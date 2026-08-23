$ErrorActionPreference = 'Stop'

$root = Split-Path -Parent $PSScriptRoot

function Require-Text {
    param(
        [Parameter(Mandatory)] [string] $Path,
        [Parameter(Mandatory)] [string] $Pattern,
        [Parameter(Mandatory)] [string] $Description
    )

    $absolutePath = Join-Path $root $Path
    if (-not (Test-Path -LiteralPath $absolutePath)) {
        throw "缺少文件：$Path"
    }
    if (-not (Select-String -LiteralPath $absolutePath -Pattern $Pattern -Quiet)) {
        throw "结构验证失败：$Description"
    }
}

Require-Text 'settings.gradle.kts' 'include\(":app", ":hook"\)' '必须包含 app 和 hook 模块'
Require-Text 'app/build.gradle.kts' 'implementation\(project\(":hook"\)\)' 'app 必须依赖 hook'
Require-Text 'hook/build.gradle.kts' 'compileOnly\(libs\.libxposed\.api\)' 'hook 必须仅编译依赖 API 102'
Require-Text 'app/build.gradle.kts' 'minSdk = 34' '最低版本必须是 Android 14'
Require-Text 'app/build.gradle.kts' 'targetSdk = 37' '目标版本必须是 Android 17'
Require-Text 'app/build.gradle.kts' 'versionCode = 15' '0.3 的 versionCode 必须为 15'
Require-Text 'app/build.gradle.kts' 'versionName = "0.3"' '正式版本名必须为 0.3'
Require-Text 'app/src/main/res/values/strings.xml' '<string name="author">作者：江浸月</string>' '关于页必须显示作者'
Require-Text 'app/src/main/resources/META-INF/xposed/java_init.list' '^io\.github\.jiangjinyue2333\.actiongrid\.hook\.ActionGridModule$' 'Hook 入口不正确'
Require-Text 'app/src/main/resources/META-INF/xposed/module.prop' '^minApiVersion=102$' 'Modern API 最低版本不正确'
Require-Text 'app/src/main/resources/META-INF/xposed/module.prop' '^targetApiVersion=102$' 'Modern API 目标版本不正确'
Require-Text 'app/src/main/resources/META-INF/xposed/scope.list' '^com\.miui\.home$' '静态作用域必须只有小米桌面'
Require-Text 'hook/src/main/java/io/github/jiangjinyue2333/actiongrid/hook/StableItemRef.java' 'CONTAINER_HOTSEAT = -101' '必须识别小米底栏容器'
Require-Text 'hook/src/main/java/io/github/jiangjinyue2333/actiongrid/hook/ActionGridModule.java' 'createAccessibilityNodeInfo' '图标操作必须在节点生命周期稳定注入'
Require-Text 'hook/src/main/java/io/github/jiangjinyue2333/actiongrid/hook/EmptyPositionAccessibilityProvider.java' 'extends AccessibilityNodeProvider' '空位置必须使用虚拟无障碍节点'
Require-Text 'hook/src/main/java/io/github/jiangjinyue2333/actiongrid/hook/VirtualGridLayout.java' 'List<Cell> cells' '虚拟桌面必须提供完整行列遍历模型'
Require-Text 'hook/src/main/java/io/github/jiangjinyue2333/actiongrid/hook/PlacementConfirmation.java' 'matchesItemAtPosition' '放置结果必须按目标位置实际项目确认'
Require-Text 'hook/src/main/java/io/github/jiangjinyue2333/actiongrid/hook/FolderPlacementTarget.java' 'static int anchorIndex' '文件夹前后插入必须使用独立锚点模型'
Require-Text 'hook/src/main/java/io/github/jiangjinyue2333/actiongrid/hook/MiuiHomeBridge.java' 'findOptionalMethod' '可选放置能力不得阻断核心操作 Hook'
Require-Text 'hook/src/main/java/io/github/jiangjinyue2333/actiongrid/hook/MiuiHomeBridge.java' 'com\.miui\.home\.folder\.FolderInfo' '7.x 必须使用真实 FolderInfo 类'
Require-Text 'hook/src/main/java/io/github/jiangjinyue2333/actiongrid/hook/MiuiHomeBridge.java' 'isAllDropedSuccess' '放置确认必须读取原生整组拖动结果'
Require-Text 'hook/src/main/java/io/github/jiangjinyue2333/actiongrid/hook/MiuiHomeBridge.java' '"autoDrop"' '7.x 占用目标必须支持原生定向投递'
Require-Text 'hook/src/main/java/io/github/jiangjinyue2333/actiongrid/hook/MiuiHomeBridge.java' '"mAutoDraged"' '7.x 原生 autoDrop 必须补齐控制器状态'
Require-Text 'hook/src/main/java/io/github/jiangjinyue2333/actiongrid/hook/MiuiHomeBridge.java' '"autoDrag"' '7.x 打开文件夹后必须直接建立桌面源拖动'
Require-Text 'hook/src/main/java/io/github/jiangjinyue2333/actiongrid/hook/MiuiHomeBridge.java' 'MotionEvent.ACTION_CANCEL' '7.x 原生落下后必须结束合成触摸流'
Require-Text 'hook/src/main/java/io/github/jiangjinyue2333/actiongrid/hook/MiuiHomeBridge.java' '"reorderItems"' '7.x 文件夹定向投递必须使用原生重排'
Require-Text 'hook/src/main/java/io/github/jiangjinyue2333/actiongrid/hook/MiuiHomeBridge.java' 'mergeShortcutResults' 'Shortcut 必须合并 Activity 与包级查询'
Require-Text 'hook/src/main/java/io/github/jiangjinyue2333/actiongrid/hook/MiuiHomeBridge.java' 'showFolderSheet' '文件夹编辑必须进入小米原生界面'
Require-Text 'hook/src/main/java/io/github/jiangjinyue2333/actiongrid/hook/ActionController.java' 'ActionIds\.EDIT_FOLDER' '文件夹节点必须提供编辑动作'
Require-Text 'hook/src/main/java/io/github/jiangjinyue2333/actiongrid/hook/ActionController.java' 'if \(cached\.canRemove\(\)\)' '快捷方式移除动作必须独立显示'
Require-Text 'hook/src/main/java/io/github/jiangjinyue2333/actiongrid/hook/ItemLocationLabel.java' '底栏第 ' '底栏必须使用官方位置语义'
Require-Text 'hook/src/main/java/io/github/jiangjinyue2333/actiongrid/hook/HomeRestartProtocol.java' 'REQUEST_NONCE' '无 Root 桌面重启必须使用共享协议'
Require-Text 'hook/src/main/java/io/github/jiangjinyue2333/actiongrid/hook/ActionGridModule.java' 'killProcess' '桌面进程必须自行响应重启请求'
Require-Text 'app/src/main/java/io/github/jiangjinyue2333/actiongrid/HomeRestarter.java' 'getRemotePreferences' '管理应用必须通过 LSP 远程配置请求重启'
Require-Text 'app/src/main/java/io/github/jiangjinyue2333/actiongrid/MainActivity.java' 'ClipData.newRawUri' '跨进程日志 URI 必须传播读授权'
Require-Text 'app/src/main/java/io/github/jiangjinyue2333/actiongrid/update/UpdateInstallActivity.java' 'ClipData.newRawUri' '安装包 URI 必须传播读授权'
Require-Text 'app/src/main/java/io/github/jiangjinyue2333/actiongrid/update/UpdateInstallActivity.java' 'openFileDescriptor\(updateUri, "r"\)' '拉起安装器前必须确认更新 URI 可读'
Require-Text 'app/src/main/java/io/github/jiangjinyue2333/actiongrid/update/UpdateNotification.java' 'UpdateInstallActivity.intent' '后台更新通知必须进入应用自有安装入口'
Require-Text 'app/src/main/java/io/github/jiangjinyue2333/actiongrid/update/UpdateDownloader.java' 'setDestinationInExternalFilesDir' '更新必须下载到应用专属外部目录'
Require-Text 'app/src/main/AndroidManifest.xml' 'android:readPermission="io\.github\.jiangjinyue2333\.actiongrid\.permission\.READ_DIAGNOSTICS"' '日志文件必须由平台 URI grant 控制读取'
Require-Text 'app/src/main/AndroidManifest.xml' 'android:permission="android\.permission\.SEND_DOWNLOAD_COMPLETED_INTENTS"' '更新完成广播必须只接受系统下载进程'
Require-Text 'docs/PROJECT_STRUCTURE.md' '当前正式版本为 `0\.3`（`versionCode=15`）' '项目结构文档必须同步正式版本'
Require-Text 'README.md' '作者：江浸月' '公开说明必须包含作者'
Require-Text 'LICENSE' '江浸月' '开源许可必须包含作者'

$scopeLines = @(Get-Content -LiteralPath (Join-Path $root 'app/src/main/resources/META-INF/xposed/scope.list') |
    Where-Object { -not [string]::IsNullOrWhiteSpace($_) })
if ($scopeLines.Count -ne 1) {
    throw '结构验证失败：scope.list 必须只有一项'
}

$hookBuild = Get-Content -Raw -LiteralPath (Join-Path $root 'hook/build.gradle.kts')
if ($hookBuild -match 'project\(":app"\)') {
    throw '结构验证失败：hook 不得依赖 app'
}

$bridge = Get-Content -Raw -LiteralPath (Join-Path $root 'hook/src/main/java/io/github/jiangjinyue2333/actiongrid/hook/MiuiHomeBridge.java')
if ($bridge -match 'com\.miui\.home\.model\.api\.FolderInfo') {
    throw '结构验证失败：7.x 不存在 model.api.FolderInfo'
}

$downloader = Get-Content -Raw -LiteralPath (Join-Path $root 'app/src/main/java/io/github/jiangjinyue2333/actiongrid/update/UpdateDownloader.java')
if ($downloader -match 'VISIBILITY_VISIBLE_NOTIFY_COMPLETED|setDestinationUri') {
    throw '结构验证失败：不得把 DownloadManager 完成通知作为安装入口'
}

Write-Output '项目结构验证通过：双模块、依赖方向、Android 14–17 和 Xposed API 102 元数据均正确。'
