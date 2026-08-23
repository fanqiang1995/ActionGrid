# ActionGrid

ActionGrid 是面向 Android 14–17 和 Modern API 102 LSP 环境的小米桌面 TalkBack 增强模块，为桌面图标补充移动、排序、创建文件夹、应用信息和快捷操作。

当前版本：`0.3`（`versionCode=15`）。安装包发布在 GitHub Releases。

## 构建

需要 JDK 21、Android SDK 37 和 PowerShell 7。

```powershell
.\scripts\verify-project-structure.ps1
.\gradlew.bat test lint assembleDebug assembleRelease
```

作者：江浸月

本项目采用 MIT License。
