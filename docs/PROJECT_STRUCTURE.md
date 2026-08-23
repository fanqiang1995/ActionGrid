# ActionGrid 项目结构

## 项目用途

ActionGrid 是小米桌面的 TalkBack 增强模块。管理应用负责模块状态、诊断和在线更新，Hook 模块负责向小米桌面图标与位置节点注入无障碍操作。

当前正式版本为 `0.3`（`versionCode=15`）。

## 目录

```text
ActionGrid/
├─ app/                         # Android 应用、关于页、诊断与更新
│  └─ src/main/
│     ├─ java/.../actiongrid/
│     ├─ res/
│     └─ resources/META-INF/xposed/
├─ hook/                        # 小米桌面 Hook 与无障碍操作
│  └─ src/main/java/.../hook/
├─ docs/                        # 项目结构与开发资料
├─ scripts/                     # 项目结构校验脚本
├─ gradle/
├─ build.gradle.kts
└─ settings.gradle.kts
```

## 模块边界

- `:app` 生成最终 APK，入口为 `MainActivity`，并依赖 `:hook`。
- `:hook` 是 Android Library，仅编译依赖 libxposed API，不依赖 `:app`。
- Xposed 入口为 `ActionGridModule`，静态作用域仅包含 `com.miui.home`。
- 应用最低支持 Android 14，目标版本为 Android 17。

## 完成任务前检查

1. 运行 `scripts/verify-project-structure.ps1`。
2. 运行 `gradlew.bat test lint assembleDebug assembleRelease`。
3. 检查 `git status`，确认构建产物和本机文件未进入版本控制。
