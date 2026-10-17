param(
    [string] $OutputDirectory = 'artifacts',
    [string] $ReleaseTag
)

$ErrorActionPreference = 'Stop'

$requiredEnvironment = @(
    'ACTIONGRID_RELEASE_STORE_FILE',
    'ACTIONGRID_RELEASE_STORE_PASSWORD',
    'ACTIONGRID_RELEASE_KEY_ALIAS',
    'ACTIONGRID_RELEASE_KEY_PASSWORD',
    'JAVA_HOME',
    'ANDROID_SDK_ROOT',
    'GRADLE_USER_HOME'
)
foreach ($name in $requiredEnvironment) {
    if ([string]::IsNullOrWhiteSpace([Environment]::GetEnvironmentVariable($name))) {
        throw "缺少发布环境变量：$name"
    }
}
if (-not (Test-Path -LiteralPath $env:ACTIONGRID_RELEASE_STORE_FILE)) {
    throw "找不到发布 keystore：$env:ACTIONGRID_RELEASE_STORE_FILE"
}

$root = Split-Path -Parent $PSScriptRoot
$output = if ([IO.Path]::IsPathRooted($OutputDirectory)) {
    [IO.Path]::GetFullPath($OutputDirectory)
} else {
    [IO.Path]::GetFullPath((Join-Path $root $OutputDirectory))
}
$buildTools = Join-Path $env:ANDROID_SDK_ROOT 'build-tools\37.0.0'
$aapt = Join-Path $buildTools 'aapt.exe'
$zipalign = Join-Path $buildTools 'zipalign.exe'
$apksigner = Join-Path $buildTools 'apksigner.bat'
foreach ($tool in @($aapt, $zipalign, $apksigner)) {
    if (-not (Test-Path -LiteralPath $tool)) {
        throw "缺少发布工具：$tool"
    }
}

$releaseTag = if ([string]::IsNullOrWhiteSpace($ReleaseTag)) {
    if ($env:GITHUB_REF_TYPE -eq 'tag') { $env:GITHUB_REF_NAME } else { $null }
} else {
    $ReleaseTag
}
$expectedVersionName = $null
if ($releaseTag) {
    $tagMatch = [regex]::Match($releaseTag, '^v(?<version>[0-9][0-9A-Za-z.+-]*)$')
    if (-not $tagMatch.Success) {
        throw "发布 tag 必须使用 v<version> 格式：$releaseTag"
    }
    $expectedVersionName = $tagMatch.Groups['version'].Value
}

& (Join-Path $root 'gradlew.bat') clean test lint assembleRelease
if ($LASTEXITCODE -ne 0) {
    throw 'Release 构建失败'
}

$unsigned = Join-Path $root 'app\build\outputs\apk\release\app-release-unsigned.apk'
if (-not (Test-Path -LiteralPath $unsigned)) {
    throw 'Release 构建未产生 unsigned APK'
}

$badging = (& $aapt dump badging $unsigned) -join "`n"
if ($LASTEXITCODE -ne 0) {
    throw 'aapt 无法读取 unsigned APK'
}
$metadataMatch = [regex]::Match(
    $badging,
    "package:\s+name='[^']+'\s+versionCode='(?<versionCode>\d+)'\s+versionName='(?<versionName>[^']+)'")
if (-not $metadataMatch.Success) {
    throw '无法从 APK 读取 versionName/versionCode'
}
$versionCode = [int]$metadataMatch.Groups['versionCode'].Value
$versionName = $metadataMatch.Groups['versionName'].Value
if ($versionCode -le 0 -or $versionName -notmatch '^[0-9][0-9A-Za-z.+-]*$') {
    throw "APK 版本元数据不可发布：versionName=$versionName versionCode=$versionCode"
}
if ($expectedVersionName -and $versionName -ne $expectedVersionName) {
    throw "tag $releaseTag 与 APK versionName $versionName 不一致"
}

[IO.Directory]::CreateDirectory($output) | Out-Null
$aligned = Join-Path $output ("ActionGrid-{0}-aligned.apk" -f $versionName)
$signed = Join-Path $output ("ActionGrid-{0}.apk" -f $versionName)
try {
    & $zipalign -f -p 4 $unsigned $aligned
    if ($LASTEXITCODE -ne 0) {
        throw 'zipalign 失败'
    }
    & $apksigner sign `
        --ks $env:ACTIONGRID_RELEASE_STORE_FILE `
        --ks-key-alias $env:ACTIONGRID_RELEASE_KEY_ALIAS `
        --ks-pass env:ACTIONGRID_RELEASE_STORE_PASSWORD `
        --key-pass env:ACTIONGRID_RELEASE_KEY_PASSWORD `
        --out $signed `
        $aligned
    if ($LASTEXITCODE -ne 0) {
        throw 'APK 签名失败'
    }
} finally {
    Remove-Item -LiteralPath $aligned -Force -ErrorAction SilentlyContinue
}

$verifyArgs = @{ ApkPath = $signed }
if ($releaseTag) {
    $verifyArgs.ExpectedTag = $releaseTag
}
& (Join-Path $PSScriptRoot 'verify-release.ps1') @verifyArgs
if ($LASTEXITCODE -ne 0) {
    throw '发布包复核失败'
}
Write-Output "正式包：$signed (versionName=$versionName, versionCode=$versionCode)"
