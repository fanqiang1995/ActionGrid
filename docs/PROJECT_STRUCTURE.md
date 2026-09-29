# ActionGrid 项目结构

## 项目用途

ActionGrid 是小米桌面的 TalkBack 增强模块。管理应用负责模块状态、诊断和在线更新，Hook 模块负责向小米桌面图标与位置节点注入无障碍操作。

当前正式版本为 `0.3`（`versionCode=15`）。

## 目录

```text
ActionGrid/
├─ .github/workflows/           # main 分支的 GitHub Actions 正式签名构建
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
- 应用最低支持 Android 14，目标版本为 Android 17。
- 关于页显示作者，并通过明确按钮打开 GitHub 源码仓库。

## 签名构建

- `.github/workflows/signed-apk.yml` 在推送到 `main` 或手动选择 `main` 时运行，使用 Windows Runner、JDK 21、Android API 37 和 Build Tools 37.0.0。
- 工作流先运行结构验证，再复用 `scripts/build-release.ps1` 完成测试、Lint、Release 构建、对齐和签名；`scripts/verify-release.ps1` 校验包名、版本、Xposed 元数据、DEX 入口与固定发布证书。
- 签名文件和密码由 README 所列四个 Repository secrets 注入，临时签名文件在结束时清理。只上传验证通过的 APK，构建产物保留 14 天。
- 外部 PR 不执行正式签名；贡献经审查合并到 `main` 后才使用原签名出包。保持包名和发布签名连续，并为在线更新递增 `versionCode`。
- 工作流不创建 GitHub Release，也不修改在线更新清单。

## 完成任务前检查

1. 运行 `scripts/verify-project-structure.ps1`。
2. 运行 `gradlew.bat test lint assembleDebug assembleRelease`。
3. 对发布 APK 运行 `scripts/verify-release.ps1 -ApkPath <apk>`。
4. 检查 `git status`，确认构建产物和本机文件未进入版本控制。
5. 修改工作流后验证 YAML 与 Actions 配置，确认正式签名仅在 `main` 执行、缺少 Secrets 时失败、错误证书不能上传 APK、临时密钥始终清理。首次部署后检查 GitHub Actions 运行与下载产物。
