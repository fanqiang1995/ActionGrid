# ActionGrid

为小米桌面补充 TalkBack 可执行的图标整理操作。

作者：江浸月

## GitHub Actions 签名构建

合并贡献到 `main` 后，`Signed APK` 工作流自动运行测试、Lint、Release 构建及签名验证。也可以在 Actions 页面手动选择 `main` 运行；成功后从该次运行的 Artifacts 下载 `ActionGrid-signed-<运行编号>`。

仓库管理员需在 Settings → Secrets and variables → Actions 配置以下 Repository secrets：

| 名称 | 内容 |
| --- | --- |
| `ACTIONGRID_RELEASE_KEYSTORE_BASE64` | 当前正式版签名文件的 Base64 内容 |
| `ACTIONGRID_RELEASE_STORE_PASSWORD` | 签名文件密码 |
| `ACTIONGRID_RELEASE_KEY_ALIAS` | 当前发布密钥别名 |
| `ACTIONGRID_RELEASE_KEY_PASSWORD` | 发布密钥密码 |

必须使用当前正式版的原签名，不能重新生成密钥。`scripts/verify-release.ps1` 会核对固定的发布证书摘要，错误证书签出的 APK 不会上传为构建产物。密钥仅在 Runner 临时目录还原，构建结束后清理，不提交源码仓库。

外部贡献者可以提交 PR；PR 和 Fork 不会使用本仓库的签名。维护者审查并合并到 `main` 后，产出的 APK 使用同一发布签名，包名保持不变即可覆盖安装。应用内在线更新还要求 `versionCode` 大于已安装版本；发布新版本时需同步应用配置、发布脚本的版本检查和项目结构文档。工作流只提供 APK 构建产物，不自动发布在线更新。

如需协作者自行合并，可给信任的人 Write 权限，再按仓库分支规则授予合并权限；无需把签名文件或密码发给协作者。拥有合并权限的人也能决定哪些代码使用正式签名构建。
