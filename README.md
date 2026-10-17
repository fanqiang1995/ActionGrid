# ActionGrid

为小米桌面补充 TalkBack 可执行的图标整理操作。

作者：江浸月

## 使用前提

- Android 14–17，LSPosed Modern API 102。
- 作用域只勾选 `com.miui.home`，不要勾选 `android`。
- 启用模块并重启小米桌面后，在管理页确认作用域、桌面进程和版本状态。
- TalkBack 操作依赖小米桌面原有无障碍节点；未知 ABI 会主动关闭原生动作，不会冒险套用偏移。

## 桌面版本

澎湃 4 原生路径使用一个 APK，在运行时按桌面 Build ID 选择 profile：

| 桌面版本 | 原生路径 |
| --- | --- |
| `8.01.02.8565` (`801028565`) | 当前 FRB profile，空位单项移动走 op 833 |
| `8.01.02.7722` (`801027722`) | 旧 FRB profile，批量移动走 op 854 |
| `8.01.02.7653` (`801027653`) | 旧 FRB profile，批量移动走 op 854 |

7.x 继续使用旧版 Java 桥接路径。其他 8.x 版本显示为未验证并关闭桌面动作；需要对应旧版功能时，请使用旧版 Release，不要把 7.x 逻辑强行套到澎湃 4。

TalkBack 只能操作当前可见的桌面节点；跨页、文件夹和占用位置由桌面状态确认，确认失败时会保留原位置并播报失败。快捷入口中的微信扫码、付款和我的二维码可被第三方显式调用；卸载入口只接受小米桌面 native 调用。

## GitHub Actions 签名构建

普通 push/PR 只运行结构检查、测试、Lint 和 unsigned build，不读取签名 Secrets。只有推送形如 `v<version>` 的 tag 才会读取签名 Secrets，生成并验证对应的 `ActionGrid-<version>.apk`，然后自动创建 GitHub Release。

发布新版本时只需：

1. 在 `app/build.gradle.kts` 中修改 `versionName`，并把 `versionCode` 递增为大于已发布版本的正整数。
2. 运行项目结构检查、测试、Lint 和构建。
3. 创建与 `versionName` 完全一致的 tag，例如 `versionName = "1.2.0"` 时推送 `v1.2.0`。

工作流会从 APK 的实际元数据读取版本，并拒绝 tag 与 `versionName` 不一致的发布。正式签名必须永久复用当前发布 keystore；更换证书会导致旧安装无法覆盖升级。

仓库管理员需在 Settings → Secrets and variables → Actions 配置以下 Repository secrets：

| 名称 | 内容 |
| --- | --- |
| `ACTIONGRID_RELEASE_KEYSTORE_BASE64` | 当前正式版签名文件的 Base64 内容 |
| `ACTIONGRID_RELEASE_STORE_PASSWORD` | 签名文件密码 |
| `ACTIONGRID_RELEASE_KEY_ALIAS` | 当前发布密钥别名 |
| `ACTIONGRID_RELEASE_KEY_PASSWORD` | 发布密钥密码 |

`scripts/verify-release.ps1` 会核对固定证书摘要、包名、版本、Xposed 元数据、native ABI 和 DEX 入口；错误证书签出的 APK 不会发布。密钥只在 Runner 临时目录还原，构建结束后清理，不提交源码仓库。

应用内在线更新仍要求 `versionCode` 大于已安装版本。工作流只发布 GitHub Release，不自动修改 `latest.json` 或 GitCode 清单，在线更新清单仍需按实际发布地址手动同步。
