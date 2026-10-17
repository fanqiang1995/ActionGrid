param(
    [string] $PrivateLibrary = 'tmp/home/sys/libhyper_os_accessibility_private.dylib.so',
    [string] $PublicLibrary = 'tmp/home/sys/libhyper_os_accessibility_public.so',
    [string] $LauncherLibrary = 'tmp/home/libs/libapp_launcher.so',
    [string] $DartLibrary = 'tmp/home/libs/libapp.so'
)

$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $PSScriptRoot

function Find-NdkTool([string] $name) {
    $command = Get-Command $name -ErrorAction SilentlyContinue
    if ($command) { return $command.Source }
    $sdk = $env:ANDROID_HOME
    if ([string]::IsNullOrWhiteSpace($sdk)) {
        $sdk = Join-Path $env:LOCALAPPDATA 'Android\Sdk'
    }
    $candidate = @(Get-ChildItem (Join-Path $sdk 'ndk') -Recurse -Filter "$name.exe" -ErrorAction SilentlyContinue |
        Sort-Object FullName -Descending | Select-Object -First 1)
    if (-not $candidate) { throw "找不到 NDK 工具：$name" }
    return $candidate.FullName
}

function Resolve-PathFromRoot([string] $path) {
    if ([IO.Path]::IsPathRooted($path)) { return $path }
    return Join-Path $root $path
}

function Build-Id([string] $path, [string] $readelf) {
    $output = & $readelf -n $path | Out-String
    $match = [regex]::Match($output, 'Build ID:\s*([0-9a-f]+)')
    if (-not $match.Success) { throw "没有 Build ID：$path" }
    return $match.Groups[1].Value
}

function Require-Symbol([string] $symbols, [string] $symbol) {
    if ($symbols -notmatch [regex]::Escape($symbol)) {
        throw "缺少导出符号：$symbol"
    }
}

$privatePath = Resolve-PathFromRoot $PrivateLibrary
$publicPath = Resolve-PathFromRoot $PublicLibrary
$launcherPath = Resolve-PathFromRoot $LauncherLibrary
$dartPath = Resolve-PathFromRoot $DartLibrary
if (-not (Test-Path -LiteralPath $privatePath)) { throw "缺少私有 Rust 库：$PrivateLibrary" }
if (-not (Test-Path -LiteralPath $publicPath)) { throw "缺少公开 Rust 库：$PublicLibrary" }
if (-not (Test-Path -LiteralPath $launcherPath)) { throw "缺少桌面 Rust 库：$LauncherLibrary" }
if (-not (Test-Path -LiteralPath $dartPath)) { throw "缺少桌面 Dart 库：$DartLibrary" }

$readelf = Find-NdkTool 'llvm-readelf'
$nm = Find-NdkTool 'llvm-nm'
$objdump = Find-NdkTool 'llvm-objdump'
$privateId = Build-Id $privatePath $readelf
$publicId = Build-Id $publicPath $readelf
 $dartId = Build-Id $dartPath $readelf
if ($privateId -notin @('401af21eddb88554a568b61ef29fc138', 'c3f3fc36f279d12dd70d69f328596457')) {
    throw "私有 Rust 库 Build ID 不匹配：$privateId"
}
if ($publicId -ne 'e2ba6d4fbeb2a03e72cf5f016429d2e4') {
    throw "公开 Rust 库 Build ID 不匹配：$publicId"
}
if ($dartId -notin @('4f1bdaed62b77ed2de7d3ebc34e453ca', '4f1bdaedd47beb4ede7d3ebcd292504e', '4f1bdaeda5978273de7d3ebca639ae02')) {
    throw "桌面 Dart 库 Build ID 不匹配：$dartId"
}

function Verify-WorkspaceReloadPrologue([string] $path, [string] $tool, [UInt64] $offset) {
    $start = '0x{0:x}' -f $offset
    $stop = '0x{0:x}' -f ($offset + 16)
    $disassembly = & $tool -d "--start-address=$start" "--stop-address=$stop" $path | Out-String
    foreach ($word in @('d10343ff', 'a90b7bfd', 'a90c4ff4', '9102c3fd')) {
        if ($disassembly -notmatch "\b$word\b") {
            throw "workspace reload prologue 不匹配：$path offset=$start word=$word"
        }
    }
}

$workspaceReloadOffset = switch ($dartId) {
    '4f1bdaedd47beb4ede7d3ebcd292504e' { [UInt64] 0x7a1660 }
    '4f1bdaed62b77ed2de7d3ebc34e453ca' { [UInt64] 0x7a2008 }
    '4f1bdaeda5978273de7d3ebca639ae02' { [UInt64] 0x7aa050 }
    default { throw "没有 workspace reload profile：$dartId" }
}
Verify-WorkspaceReloadPrologue $launcherPath $objdump $workspaceReloadOffset

$privateSymbols = & $nm -D $privatePath | Out-String
Require-Symbol $privateSymbols 'create_accessibility_node_infoB9_'
Require-Symbol $privateSymbols 'perform_actionB9_'
Require-Symbol $privateSymbols 'AccessibilityInteractionController28perform_accessibility_action'
Require-Symbol $privateSymbols 'IAccessibilityInteractionConnection26performAccessibilityAction'
Require-Symbol $privateSymbols 'AccessibilityController32request_send_accessibility_event'

$publicSymbols = & $nm -D $publicPath | Out-String
foreach ($symbol in @(
    'AccessibilityAction_new',
    'AccessibilityAction_drop',
    'AccessibilityNodeInfo_new',
    'AccessibilityNodeInfo_add_action',
    'AccessibilityNodeInfo_drop',
    'AccessibilityNodeInfo_set_content_description',
    'AccessibilityNodeInfo_set_text',
    'AccessibilityNodeInfo_set_selected',
    'AccessibilityNodeInfo_set_checked',
    'AccessibilityEvent_obtain',
    'AccessibilityEvent_drop',
    'AccessibilityEvent_record_push_text',
    'AccessibilityEvent_record_set_source',
    'AccessibilityEvent_record_set_item_count',
    'AccessibilityEvent_record_set_from_index',
    'AccessibilityEvent_record_set_to_index',
    'AccessibilityEvent_set_event_type'
)) {
    Require-Symbol $publicSymbols $symbol
}

$launcherSymbols = & $nm -D $launcherPath | Out-String
foreach ($symbol in @(
    'frb_pde_ffi_dispatcher_primary',
    'LauncherApps_default',
    'LauncherApps_get_shortcuts',
    'LauncherApps_start_shortcut',
    'LauncherApps_start_main_activity',
    'LauncherApps_start_app_details_activity',
    'ShortcutQuery_default',
    'ShortcutQuery_set_package_name',
    'ShortcutInfo_get_id',
    'ShortcutInfo_get_title'
)) {
    Require-Symbol $launcherSymbols $symbol
}

$nativeSource = Get-Content -Raw -LiteralPath (Join-Path $root 'app/src/main/cpp/native_init.cpp')
foreach ($buildId in @(
    '401af21eddb88554a568b61ef29fc138',
    'e05f4ad255225718a2f86dfcc636b871',
    '237f244c1c392a963dce55a2d11068fd',
    'c3f3fc36f279d12dd70d69f328596457',
    '7de2dd3e204a267ee1ed57c7a54391d2',
    'a6c7eeb5fec3383a58b89d43166852b8',
    '4f1bdaed62b77ed2de7d3ebc34e453ca',
    '4f1bdaedd47beb4ede7d3ebcd292504e',
    '4f1bdaeda5978273de7d3ebca639ae02'
)) {
    if ($nativeSource -notmatch [regex]::Escape($buildId)) {
        throw "native_init.cpp 缺少已验证 profile Build ID：$buildId"
    }
}
foreach ($op in @(
    'kFrbUpdateItemDatabaseBatch = 854U',
    'kFrbStartLoad = 851U',
    'kFrbMoveItemInDatabase = 833U'
)) {
    if ($nativeSource -notmatch [regex]::Escape($op)) {
        throw "缺少 HyperOS4 FRB 内部模型操作：$op"
    }
}
foreach ($offset in @('0x7a1660', '0x7a2008', '0x7aa050')) {
    if ($nativeSource -notmatch [regex]::Escape($offset)) {
        throw "native_init.cpp 缺少 workspace reload profile 偏移：$offset"
    }
}
foreach ($word in @('d10343ff', 'a90b7bfd', 'a90c4ff4', '9102c3fd')) {
    if ($nativeSource -notmatch $word) {
        throw "native_init.cpp 缺少 workspace reload prologue：$word"
    }
}
if ($nativeSource -notmatch 'resolveWorkspaceReloadForProfile') {
    throw 'native_init.cpp 缺少 Build ID/prologue 校验的 workspace reload resolver'
}
if ($nativeSource -match 'g_trigger_workspace_reload\s*=\s*nullptr\s*;\s*if\s*\(\s*g_frb_dispatcher') {
    throw 'native_init.cpp 不得禁用已验证的 workspace reload entry point'
}
if ($nativeSource -match '0x23ca8|0x23ce4|0x27af8|0x28828') {
    throw 'native_init.cpp 不得硬编码未经运行时解析的 ABI 偏移'
}
if ($nativeSource -notmatch 'dlsym\(') {
    throw 'native_init.cpp 必须通过 dlsym 解析符号'
}

Write-Output "PASS: Rust ABI 基线有效（private=$privateId, public=$publicId, libapp=$dartId）。"
