# ActionGrid 项目结构

## 项目用途

ActionGrid 是小米桌面的 TalkBack 增强模块。管理应用负责模块状态、诊断和在线更新，Hook 模块负责向小米桌面图标与位置节点注入无障碍操作。

正式版本以 `app/build.gradle.kts` 中的 `versionName` 和 `versionCode` 为准。

## 目录

```text
ActionGrid/
├─ .github/workflows/           # main/PR 检查与 v<version> 签名发布
│  └─ signed-apk.yml
├─ app/                         # Android 应用、关于页、诊断与更新
│  └─ src/main/
│     ├─ java/.../actiongrid/
│     ├─ res/
│     └─ resources/META-INF/xposed/
├─ hook/                        # 小米桌面 Hook 与无障碍操作
│  └─ src/main/java/.../hook/
├─ docs/                        # 项目结构与开发资料
├─ scripts/                     # 结构、构建和 APK 校验脚本
├─ gradle/
├─ build.gradle.kts
└─ settings.gradle.kts
```

## 模块边界

- `:app` 生成最终 APK，入口为 `MainActivity`，并依赖 `:hook`。
- `:hook` 是 Android Library，仅编译依赖 libxposed API，不依赖 `:app`。
- Xposed 入口为 `ActionGridModule`，静态作用域仅包含 `com.miui.home`。
- 7.x Java 小米桌面继续使用 View 适配器；澎湃 4 的 `8.01.02.7653`、`8.01.02.7722`、`8.01.02.8565` 统一进入 `HyperOs4NodeAdapter` 应用操作路径，放置由 `app/src/main/cpp/native_init.cpp` 按 Build ID 选择 Rust/FRB profile 执行。
- `app/src/main/cpp/native_init.cpp` 负责澎湃 4 Rust 库的 Build ID、系统 ABI 配对、延迟加载复核、节点 action 透传和内部模型事务；离线基线检查见 `scripts/verify-native-abi.ps1`，运行时验收见 `docs/HYPEROS4_NATIVE_FINDINGS.md`。
- 应用最低支持 Android 14，目标版本为 Android 17。
- 关于页显示作者，并通过明确按钮打开 GitHub 源码仓库。

## 签名构建

- 普通 push/PR 只运行结构检查、测试、Lint 和 unsigned build，不读取签名 Secrets。
- 只有推送形如 `v<version>` 的 tag 才进入正式签名 job；工作流从 APK 实际元数据生成 `ActionGrid-<version>.apk` 并创建 GitHub Release。
- 发布 tag 的版本必须与 `app/build.gradle.kts` 的 `versionName` 完全一致，`versionCode` 只能递增。
- `scripts/verify-release.ps1` 校验包名、版本、Xposed 元数据、固定发布证书、DEX 入口和 arm64 native ABI。
- 签名文件和密码由 README 所列四个 Repository secrets 注入，临时签名文件在结束时清理。外部 PR 不执行正式签名；工作流不自动修改在线更新清单。

## 完成任务前检查

1. 运行 `scripts/verify-project-structure.ps1`。
2. 运行 `gradlew.bat clean test lint assembleDebug assembleRelease`。
3. 对签名包运行 `scripts/verify-release.ps1 -ApkPath <apk>`。
4. 检查 `git status`，确认构建产物和本机文件未进入版本控制。
