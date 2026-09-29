param(
    [Parameter(Mandatory)]
    [string] $ApkPath
)

$ErrorActionPreference = 'Stop'

$apk = (Resolve-Path -LiteralPath $ApkPath).Path
$sdk = if ($env:ANDROID_SDK_ROOT) { $env:ANDROID_SDK_ROOT } else { $env:ANDROID_HOME }
if (-not $sdk) {
    throw 'ANDROID_SDK_ROOT 或 ANDROID_HOME 未配置'
}

$buildTools = Join-Path $sdk 'build-tools\37.0.0'
$aapt = Join-Path $buildTools 'aapt.exe'
$apksigner = Join-Path $buildTools 'apksigner.bat'
$apkanalyzer = Join-Path $sdk 'cmdline-tools\latest\bin\apkanalyzer.bat'
foreach ($tool in @($aapt, $apksigner, $apkanalyzer)) {
    if (-not (Test-Path -LiteralPath $tool)) {
        throw "缺少发布验证工具：$tool"
    }
}

$badging = (& $aapt dump badging $apk) -join "`n"
if ($LASTEXITCODE -ne 0) {
    throw 'aapt 无法读取 APK'
}
foreach ($expected in @(
    "package: name='io.github.jiangjinyue2333.actiongrid' versionCode='15' versionName='0.3'",
    "sdkVersion:'34'",
    "targetSdkVersion:'37'",
    "compileSdkVersion='37'"
)) {
    if (-not $badging.Contains($expected)) {
        throw "APK 元数据不正确：缺少 $expected"
    }
}

& $apksigner verify --verbose $apk | Out-Null
if ($LASTEXITCODE -ne 0) {
    throw 'APK 签名验证失败'
}

$certificateOutput = (& $apksigner verify --verbose --print-certs $apk) -join "`n"
if ($LASTEXITCODE -ne 0) {
    throw '无法读取 APK 签名证书'
}
$certificateMatch = [regex]::Match(
    $certificateOutput,
    'certificate SHA-256 digest: ([0-9a-fA-F]{64})')
if (-not $certificateMatch.Success) {
    throw 'APK 缺少可识别的签名证书摘要'
}
$certificateSha256 = $certificateMatch.Groups[1].Value.ToLowerInvariant()
$expectedCertificateSha256 = 'e000d876a95db3c3ea565e8650d19b81d75a674f1926e9889d4cf5a54aeb1f05'
if ($certificateSha256 -ne $expectedCertificateSha256) {
    throw 'APK 使用了非当前 ActionGrid 发布证书；禁止生成无法覆盖升级的发布包'
}

Add-Type -AssemblyName System.IO.Compression.FileSystem
$archive = [IO.Compression.ZipFile]::OpenRead($apk)
try {
    $expectedEntries = @{
        'META-INF/xposed/java_init.list' = 'io.github.jiangjinyue2333.actiongrid.hook.ActionGridModule'
        'META-INF/xposed/module.prop' = "minApiVersion=102`ntargetApiVersion=102`nstaticScope=true"
        'META-INF/xposed/scope.list' = 'com.miui.home'
    }
    foreach ($pair in $expectedEntries.GetEnumerator()) {
        $entry = $archive.GetEntry($pair.Key)
        if ($null -eq $entry) {
            throw "APK 缺少 $($pair.Key)"
        }
        $reader = [IO.StreamReader]::new($entry.Open())
        try {
            $actual = $reader.ReadToEnd().Replace("`r`n", "`n").Trim()
        } finally {
            $reader.Dispose()
        }
        if ($actual -ne $pair.Value) {
            throw "APK 中 $($pair.Key) 内容不正确"
        }
    }
} finally {
    $archive.Dispose()
}

$defined = (& $apkanalyzer dex packages --defined-only $apk) -join "`n"
if ($LASTEXITCODE -ne 0) {
    throw 'apkanalyzer 无法读取 DEX'
}
if ($defined -notmatch '(?m)^C\s+\S+\s+\d+\s+\d+\s+\d+\s+io\.github\.jiangjinyue2333\.actiongrid\.hook\.ActionGridModule$') {
    throw 'Release DEX 中缺少 ActionGridModule'
}
if ($defined -match '(?m)^C\s+.*(?:de\.robv\.android\.xposed|io\.github\.libxposed\.api)\.') {
    throw 'APK 错误打包了 Xposed API 实现类'
}

$hash = (Get-FileHash -LiteralPath $apk -Algorithm SHA256).Hash.ToLowerInvariant()
Write-Output "发布 APK 验证通过：Android 14–17、Modern API 102、静态作用域、DEX 入口与签名均正确。"
Write-Output "SHA-256: $hash"
