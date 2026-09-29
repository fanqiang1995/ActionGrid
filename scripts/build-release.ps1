param(
    [string] $OutputDirectory = 'artifacts'
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

$root = Split-Path -Parent $PSScriptRoot
$output = if ([IO.Path]::IsPathRooted($OutputDirectory)) {
    [IO.Path]::GetFullPath($OutputDirectory)
} else {
    [IO.Path]::GetFullPath((Join-Path $root $OutputDirectory))
}
$sdk = $env:ANDROID_SDK_ROOT
$zipalign = Join-Path $sdk 'build-tools\37.0.0\zipalign.exe'
$apksigner = Join-Path $sdk 'build-tools\37.0.0\apksigner.bat'

& (Join-Path $root 'gradlew.bat') clean test lint assembleRelease
if ($LASTEXITCODE -ne 0) {
    throw 'Release 构建失败'
}

$unsigned = Join-Path $root 'app\build\outputs\apk\release\app-release-unsigned.apk'
if (-not (Test-Path -LiteralPath $unsigned)) {
    throw 'Release 构建未产生 unsigned APK'
}

[IO.Directory]::CreateDirectory($output) | Out-Null
$aligned = Join-Path $output 'ActionGrid-0.3-aligned.apk'
$signed = Join-Path $output 'ActionGrid-0.3.apk'
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
Remove-Item -LiteralPath $aligned

& (Join-Path $PSScriptRoot 'verify-release.ps1') -ApkPath $signed
if ($LASTEXITCODE -ne 0) {
    throw '发布包复核失败'
}
Write-Output "正式包：$signed"
