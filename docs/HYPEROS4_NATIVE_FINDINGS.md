# 澎湃 4 原生桌面实测与适配方案

## 结论（2026-10-01）

当前设备保留澎湃 4 和现有 LSPosed，在 `com.miui.home` 进程内通过 native hook 给桌面图标直接加入“托起”动作。动作回调建立临时托起会话，并把空位作为同一棵无障碍节点树的虚拟子节点返回；不再让读屏先进入桌面长按菜单再手动寻找节点。

## 实测环境

- USB ADB 设备 `25102RKBEC`，Android 17 / SDK 37。
- 系统 `OS4.0.0.33.XPMCNXM`，构建 `CP2A.260605.016`。
- 桌面 `RELEASE-8.01.02.7722-260904-09221533-R`，版本号 `801027722`。
- 桌面 APK 无 DEX，进程由 `hyos_spawner` 派生，先前检查的进程映射无 ART。
- 已有读屏服务正常运行；该问题发生在 ActionGrid 后端，不需要切换无线调试或重复启用读屏。

## 已验证和未验证

| 环节 | 结果 |
| --- | --- |
| LSPosed 加载 APK 中的 `libactiongrid_native.so` | 已验证 `native_init version=2` |
| 桌面加载回调 | 已验证 `libapp_launcher.so` |
| 构建无障碍节点 | 按公开库 Build ID 安装 `AccessibilityNodeInfo_add_action` hook，并给图标注入高位“托起”动作；设备已验证 |
| 动作回调 | hook `AccessibilityNodeProvider_TO::perform_action`；设备已验证 |
| 托起后的虚拟空位 | hook `create_accessibility_node_info`，按当前桌面网格返回带边界和点击动作的空位节点；设备已验证 |

当前 native 后端按 Build ID 解析 Rust 导出符号，不使用硬编码函数偏移。HyperOS 4 放置路径调用 `libapp_launcher.so` 导出的 `frb_pde_ffi_dispatcher_primary`：`8.01.02.7653` 和 `8.01.02.7722` 使用已验证的 `op 854` 批量事务；`8.01.02.8565` 对单个空位移动使用已验证的 `op 833` `ItemInfo` 事务，占用位置重排使用现有 favorites 事务。未知 Build ID 或系统 ABI 配对失败时关闭 FRB 能力，不会套用其他 profile。Java 适配器保留给有 DEX 的 7.x 旧桌面，澎湃 4 无 DEX 桌面走 native 应用动作路径。单指上下滑动由读屏服务自己解释，应用把动作直接暴露在图标的操作菜单里。

打包入口为 `META-INF/xposed/native_init.list`。已检查成功加载的 APK 不含 `assets/native_init`；此前把成功归因于旧入口声明的判断不成立。`native_init` 为导出符号。参考 [LSPosed Native Hook 文档](https://github.com/LSPosed/LSPosed/wiki/Native-Hook)。

## 下一步实现边界

1. 保留 Java 后端服务旧桌面；原生后端仅在小米桌面进程内工作。
2. 按系统库 Build ID 和桌面版本校验，不匹配时关闭动作注入；不能硬编码未经校验的函数地址。
3. 通过桌面原有 FRB/Rust 模型事务完成移动；事务提交后再刷新 Flutter 模型，不模拟手指拖动。
4. 管理器继续区分 native 加载、节点接入和动作可用。

### ABI 排查记录

旧版 7653/7722 的系统 ABI 为：private `401af21eddb88554a568b61ef29fc138`、AIDL `e05f4ad255225718a2f86dfcc636b871`、UI `237f244c1c392a963dce55a2d11068fd`。8565 的系统 ABI 为：private `c3f3fc36f279d12dd70d69f328596457`、AIDL `7de2dd3e204a267ee1ed57c7a54391d2`、UI `a6c7eeb5fec3383a58b89d43166852b8`；两套都使用 public `e2ba6d4fbeb2a03e72cf5f016429d2e4`。

- `AccessibilityNodeProvider_TO::create_accessibility_node_info` 导出偏移 `0x23ce4`。
- 调用点 `0x28c24` 返回后检查 `w0` 低位，并从 `x1` 取节点指针。先前仅按 `bool` 返回值声明会丢失 `x1`，造成节点损坏和桌面崩溃；该实验已撤除并恢复桌面。
- `AccessibilityNodeProvider_TO::perform_action` 导出偏移 `0x23ca8`。
- `AccessibilityInteractionController::perform_accessibility_action` 导出偏移 `0x27af8`。
- `native_init` 时相关系统库尚未出现；应在实际加载回调中解析依赖，不能把过早解析失败当成没有接口。
- 上述偏移仅供分析，当前探针未使用这些地址。尚未验证完整 Rust 返回值布局，不能仅凭这些记录投产。

## 验收

离线 ABI 基线检查：`scripts/verify-native-abi.ps1`；设备加载及稳定性检查：`scripts/verify-native-probe.ps1 -Serial <USB设备序列号>`。两者都不等同于整理功能已通过端到端验收。

真正的功能验收必须在现有读屏服务下完成：选择图标 → 执行“托起” → 选择空位或已有应用入口 → 放置 → 重启桌面后布局仍正确；另验多选、取消、空位、翻页和未知版本关闭适配。放置过程应保持桌面普通态，不出现“完成”，不调用输入注入接口。文件夹创建和放入已有文件夹暂不暴露动作，需单独补齐文件夹内部接口后再启用。

## 离线 APK 基线（系统桌面目录）

`系统桌面/系统桌面_RELEASE-8.01.02.7653-260904-09092213-R.apk.1` 是有效 APK，版本号为 `801027653`。该版本与 7722 共用旧 FRB batch profile；原生 ABI 仍必须通过运行时 Build ID 校验后才会启用。

它的 `libapp.so` Build ID 为 `4f1bdaedd47beb4ede7d3ebcd292504e`，AOT 符号包含 `MultiSelectMonitor.getCheckedShortcutInfos`、`DraggableWrapperState._startDrag`、`FolderGridViewGetxController.onDrop`、`FolderIconGetxController.onDrop`、`HotseatDragHandler.onDrop` 以及跨屏 push 方法。这证明桌面自身具备多选和各类放置路径；这些 Dart 内部函数没有稳定 C ABI，ActionGrid 不直接调用它们，而是读取无障碍节点的选中/勾选状态，再复用桌面原有长按拖动。

## 更新后的设备 ABI

当前设备运行时 `libapp.so` Build ID 为 `4f1bdaeda5978273de7d3ebca639ae02`（版本 `801028565`）。`start_load`（操作号 851）不能按旧 profile 发送 payload，因此不复用旧批量 envelope；单个空位移动使用 `op 833`，占用位置由 favorites 事务完成。当前 private/public 无障碍库 Build ID 分别为 `c3f3fc36f279d12dd70d69f328596457` 和 `e2ba6d4fbeb2a03e72cf5f016429d2e4`；TalkBack 和触摸 ABI 继续启用，未知或未验证的 FRB wire 格式保持关闭。
