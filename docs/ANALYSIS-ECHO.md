# ActionGrid 代码分析（Echo，2026-10-18）

> 目标仓库：https://github.com/jiangjinyue2333/ActionGrid
> 分析对象：main @ 62c8fa0（v0.4，96 文件，约 1.1 万行有效代码）
> 用途：明天参与开发前的技术摸底

---

## 一句话定位

给**小米桌面（com.miui.home）+ TalkBack** 补上“整理图标”的能力：选图标 → 托起 → 选位置/空位 → 放置。
核心难点不在 Android 层，而在**澎湃 4 桌面无 DEX、纯 Rust/Flutter 编译产物**，所以只能走 native hook + 复用桌面自己的 FRB（Flutter Rust Bridge）事务。

---

## 一、技术栈与构建

| 项 | 值 |
|---|---|
| 语言 | Java 21（无 Kotlin）+ C++（NDK，ndkBuild） |
| 构建 | AGP **9.2.1**，compileSdk/targetSdk **37**（Android 17），buildTools 37.0.0，minSdk 34 |
| Xposed | libxposed **Modern API 102**（`io.github.libxposed:api 102.0.0`），`staticScope=true` |
| ABI | 仅 `arm64-v8a` |
| 版本 | versionCode 16 / versionName 0.4 |
| 签名 | GitHub Actions 只在 `v*` tag 读 Secrets 签名，PR 只跑 unsigned build |

启动入口：`META-INF/xposed/{scope.list,module.prop,java_init.list,native_init.list}`
- scope 只有 `com.miui.home`（README 明确：**不要勾 android**）
- java 入口 `ActionGridModule`，native 入口 `libactiongrid_native.so`

---

## 二、模块职责

| 模块 | 职责 |
|---|---|
| `:app` | 管理页（MainActivity 26KB）、诊断（DiagnosticsProvider/LogStore）、在线更新（update/* 8 个类）、微信快捷入口 |
| `:hook` | 全部 hook 逻辑，只编译依赖 libxposed API（不反向依赖 :app，边界干净） |
| `app/src/main/cpp/native_init.cpp` | **4912 行**，ELF 指纹识别、Rust 符号解析、无障碍节点 hook、FRB 事务、虚拟空位节点 |

---

## 三、两条技术路线（按桌面版本分流）

### A. 7.x 旧桌面（有 DEX）→ Java 反射桥
`MiuiHomeBridge`(68KB) + `ActionController`(22KB)
- hook `View.setTag / onAttachedToWindow / createAccessibilityNodeInfo / performAccessibilityAction / getAccessibilityNodeProvider / dispatchHoverEvent`
- 用反射拿桌面内部对象做 native drag（`startNativeDrag`、`finishPlacement`），**模拟的是桌面自己的拖动实现，不是注入触摸**

### B. 澎湃 4（8.01.02.7653 / 7722 / 8565，无 DEX）→ native 节点注入
`HyperOs4NodeAdapter`(50KB) + `native_init.cpp`
1. 在桌面进程解析 Rust mangled symbol（`AccessibilityNodeProvider_TO::create_accessibility_node_info` / `perform_action` / `find_focus` 等，见 native_init.cpp:170+）
2. hook `AccessibilityNodeInfo.add_action`，给**图标节点直接注入高位自定义 action**（“托起”）
3. hook `AccessibilityNodeProvider_TO::perform_action` 接住这些 action
4. 托起后 hook `create_accessibility_node_info`，把**每个空位作为虚拟子节点**返回（带 bounds、点击动作）→ 读屏可以直接“点”空位
5. 放置走桌面自己的 FRB dispatcher（`frb_pde_ffi_dispatcher_primary`）：
   - op **854** = `update_item_database_batch`（7653/7722 批量）
   - op **833** = `move_item_in_database`（8565 单个 ItemInfo）
   - op **851** = `start_load`（8565 不能复用旧 payload，已放弃）
6. 事务提交后刷新 Flutter 模型（`workspace reload`，prologue 校验后才调）

> 关键设计：**不模拟手指拖动**，走桌面自己的模型事务，所以重启后布局是持久的。

---

## 四、安全设计（这个项目最值得学的部分）

1. **能力位（capability bitmask）**：`CAP_MOVE_PROFILE` 与其它能力**分离**。未知桌面版本 → 只关移动/重排，托起/取消照常可用（native_init.cpp:50-60）
2. **ELF 结构指纹**代替 Build ID：排除 `.note*`/`.comment` 后取两段哈希，**只刷 Build ID 的系统更新自动兼容**（native_init.cpp:88-130）
3. **白名单已知指纹**：private / AIDL / UI / public / libapp Dart / launcher 各自独立指纹，运行时成对照校验
4. **prologue 校验**：调 `maybe_trigger_workspace_reload` 前比对 4 个立即数（`kWorkspaceReloadPrologue`）
5. **失败即降级**：指纹/符号/prologue/ABI 任一不对 → 关闭危险能力，不猜操作号
6. 卸载入口只接受桌面 native 调用；微信快捷入口可被第三方显式调用

---

## 五、工程纪律（超出一般个人项目）

- **契约测试 40+**：`ReleaseRegressionContractTest`（app 32KB + hook 18KB）+ 各策略单测，断言的是“这个约束还在不在”（作用域只有 launcher、native 不用裸偏移、未知 profile 隐藏入口、文件夹插入锚点…）
- **三套离线校验脚本**：`verify-project-structure.ps1` / `verify-native-abi.ps1` / `verify-release.ps1`（校验证书摘要、包名、Xposed 元数据、DEX 入口、arm64 ABI）
- **CI 分级**：push/PR 不读签名 Secrets，只有 tag 才签名发布
- 文档：`docs/PROJECT_STRUCTURE.md` + `docs/HYPEROS4_NATIVE_FINDINGS.md`（含实测 ABI 哈希、偏移记录、失败案例复盘）

---

## 六、我判定的风险与隐患

| 级别 | 问题 | 说明 |
|---|---|---|
| 高 | **C++/Rust FFI 边界没有异常隔离** | native 侧调 Rust 函数无 `catch_unwind` 等值保护；Rust panic 跨 FFI 是 UB，最坏表现是桌面进程崩/重启循环 |
| 高 | **版本白名单不可持续** | 每版桌面都要手工采指纹 + 补 op 号；作者已遇过 8565 换代（op 851→833）。这是项目最大的长期成本 |
| 中 | **native 逻辑零单测** | 4912 行 C++ 全靠真机验证；ELF 解析、ABI 配对、payload 组包都可以抽成 host 可测单元 |
| 中 | **在线更新清单手工同步** | README 明说 Actions 不更新 `latest.json`，容易忘 → 版本停滞 |
| 中 | **真机验收靠人** | 文档里“验收”步骤（托起→选空位→放置→重启验证）可脚本化到 adb |
| 低 | 只 arm64、minSdk 34 | 符合目标用户，不算问题 |
| 低 | 无 CHANGELOG | 用户看不懂每版改了什么 |

---

## 七、可贡献方向（按投入产出排序）

1. **CI 自动生成 `latest.json`**（半天）：release job 里生成清单并提交到指定分支/GitCode，消除最容易被遗忘的手工步骤
2. **native 逻辑抽单元测试**（1-2 天）：ELF 指纹解析、ABI 配对、FRB payload 组包抽成纯函数 + host 端 CTest
3. **真机回归脚本**（1-2 天）：`verify-native-probe.ps1` 基础上加端到端用例（托起→空位→放置→重启校验），减少人工验收
4. **文件夹动作补齐**（较大）：文档明确“文件夹创建/放入已有文件夹暂不暴露”，需要补 folder 内部接口
5. **6.x / 其他 8.x 适配**（较大）：取决于目标用户机型分布
6. **中文用户手册 + 无障碍操作说明**：面向盲人用户的“怎么用”比技术文档更缺，这块我们最有发言权
7. **诊断信息可视化**：LogStore 已有数据，管理页可加“一键导出诊断包”

---

## 八、本地开发环境（要和作者对齐）

构建这个项目需要比常规 Android 项目更激进的环境：
- **JDK 21**
- **Android SDK Platform 37 + Build-Tools 37.0.0**（容器里现在是 33，要装）
- **NDK**（ndkBuild 路径，`app/src/main/cpp/Android.mk`）
- **AGP 9.2.1**（Gradle 版本要配套）
- Windows 侧脚本是 PowerShell（作者在 Windows 开发）

真机条件：
- 小米设备 + 澎湃 4 桌面 `8.01.02.8565`（或 7722/7653）
- **LSPosed Modern API 102**
- 作用域只勾 `com.miui.home`
- 出问题靠 LSPosed 里关掉模块恢复桌面（**测试机最好是副机**）

---

## 九、明天怎么切入（建议）

1. 先问清作者当前**主攻方向**：是继续跟桌版本适配（native），还是补功能（文件夹/多选），还是做工程化（CI/测试）
2. 我们的优势在**Flutter/Dart + Kotlin + 无障碍真实用户视角 + 中文文档**
3. 建议先领**低风险高确定性**的活：CI 自动 latest.json、诊断导出、用户手册
4. **不要一上来碰 native_init.cpp 的 hook 部分**——那是作者一个人的战场，改动需要真机验证闭环
5. 任何改动前后都跑：`verify-project-structure.ps1` + `gradlew test lint assembleDebug`

---

# 问题清单与功能建议（2026-10-18 夜，明天开工用）

## A. 代码层面（技术债）
1. **C++→Rust 无异常隔离**：native 调 Rust 无 catch 保护，panic 跨 FFI 是 UB → 建议在 FFI 调用点做防御（Rust 侧 catch_unwind / C++ 侧信号兜底）
2. **版本白名单维护成本**：每版桌面手工采指纹 + 补 op 号（8565 已换 op）。建议做"指纹采集工具"降低适配成本
3. **native 零单测**：4912 行 C++ 只能真机验证；ELF 指纹、ABI 配对、payload 组包可抽成 host 端可测单元
4. **在线更新清单手工同步**：CI 不更新 latest.json，易漏
5. **无 CHANGELOG / 无英文资源**：strings.xml 仅中文，无 values-en

## B. 功能缺口（文档已承认或代码未启用）
1. **文件夹**：创建新文件夹、放入已有文件夹均未暴露动作（`PlacementPolicy` 有 CREATE_FOLDER，native 路径未启用）
2. **6.x 桌面不支持**；8.x 非白名单版本降级为安全模式
3. **多选依赖桌面自身选中状态**：若桌面没有多选入口，读屏用户无法多选托起
4. **无撤销**：放错位置只能重新托起再移回
5. **诊断导出**：LogStore 有数据，但无"一键导出诊断包"

## C. 用户视角的问题（盲人 TalkBack 体验，我们最有发言权）
1. **位置播报只有坐标**（"第 2 行第 3 列"），盲人记不住网格。建议加**邻居参照**："微信的右边 / QQ 的下方"
2. **空位播报同样只有坐标**，没有"这块附近有什么"
3. **发现性差**：模块装好后，用户不知道图标多了"托起"动作 → 需要首次引导（一次性播报/教程）
4. **多选无实时反馈**：建立会话时播一次"已托起 N 个"，之后选中数量变化不播
5. **桌面升级后功能静默降级**：只播"当前桌面版本不支持此操作"，用户不知道怎么办 → 应引导去更新 APK
6. **无操作历史/撤销**，误操作成本高

## D. 功能建议（按性价比排序）
| 优先级 | 建议 | 难度 | 价值 |
|---|---|---|---|
| P0 | 位置播报加邻居参照（右/左/上/下是哪个应用） | 低 | 盲人直接受益 |
| P0 | 首次使用引导 + 动作发现提示 | 低 | 解决"装了不会用" |
| P0 | CI 自动生成 latest.json | 低 | 消除手工漏发 |
| P1 | 撤销上一步（记住上一次放置的源/目标） | 中 | 误操作可恢复 |
| P1 | 诊断一键导出 + 隐私脱敏说明 | 中 | 支持成本大降 |
| P1 | 真机端到端回归脚本（托起→空位→放置→重启） | 中 | 释放人工验收 |
| P2 | 文件夹内整理 / 新建文件夹 | 高 | 补齐最大功能缺口 |
| P2 | 多选数量实时播报 + 全选 | 中 | 批量整理效率 |
| P2 | 桌面版本不支持时的补救引导（跳转更新） | 低 | 降低流失 |
| P3 | 英文资源 / 国际用户 | 中 | 扩大用户群 |

## E. 建议向作者确认的问题
1. 下一步主攻方向：版本适配 / 文件夹功能 / 工程化？
2. 桌面版本白名单怎么采集的（有没有工具），能否开源采集脚本？
3. 多选在目标机型上有多选入口吗？
4. 诊断日志里会不会记录桌面应用列表（隐私）？
5. 是否需要 Windows 之外的构建环境文档（Linux/macOS 贡献者）？

---

# 专项：TalkBack 转子（Rotor）里的动作清单（2026-10-18 夜）

## 事实梳理

1. 全仓库 grep `rotor` / `转子` → **零命中**。作者没有针对转子做过专门设计，只是往 `AccessibilityNodeInfo` 里 `addAction`，由 TalkBack 自己放进“操作”类别。
2. 当前注入的动作与 ID（`ActionIds.java`）：

| ID | 标签 | 出现时机 |
|---|---|---|
| `0x6A000001` | 托起 | 空闲态，图标可托起 |
| `0x6A000002` | 取消托起 | 托起会话中，指向源图标 |
| `0x6A000003` | 移到 X 前面 | 托起会话中，指向目标 |
| `0x6A000004` | 移到 X 后面 | 托起会话中，指向目标 |
| `0x6A000005` | 和 X 创建文件夹 | 托起会话中，目标支持 |
| `0x6A000006` | 应用信息 | 空闲态，快捷方式 |
| `0x6A000007` | 卸载 | 空闲态，可卸载 |
| `0x6A000008` | 从桌面移除 | 仅 Java 7.x 路径（`ActionController:250`） |
| `0x6A000009` | 编辑（文件夹） | 仅 Java 7.x 路径（`ActionController:221`） |
| `0x6A000100 + i` | 动态快捷方式（最多 24） | 空闲态 |

3. **顺序机制**：系统按 action id 升序返回 `getActionList()`，TalkBack 转子按此顺序渲染 → **ID 递增顺序 = 转子里的显示顺序**。所以现在实际顺序是：托起 → 取消 → 前 → 后 → 创建文件夹 → 应用信息 → 卸载 → 动态快捷方式。
4. **动态快捷方式最多 24 个**（`DYNAMIC_COUNT = 24`），`HyperOs4NodeAdapter:436` 与 `ActionController:233` 都是直接循环注入 → 微信一类 App 的快捷方式多时会**淹没转子**，用户要滑很多下才能到下一个图标。
5. native 路径（澎湃 4）**没有实现 REMOVE / EDIT_FOLDER**，只有 Java 7.x 路径有 → 澎湃 4 用户少了“从桌面移除”和“编辑文件夹”。

## 改进方案（按性价比）

### 1. 重排 ID 分段，让转子顺序符合“整理”心智
建议顺序（也即 ID 递增顺序）：
```
托起 / 取消托起 → 移到前面 / 移到后面 → 创建文件夹 → 从桌面移除 → 应用信息 → 卸载 → 动态快捷方式
```
理由：整理场景里“移除”比“应用信息”常用得多；卸载和查看信息属于低频，往后放。
⚠️ 但改 ID 会破坏向后兼容（旧版本 APK 的用户升级后动作会错位）→ 需要版本化处理，或只在 0.x 阶段做。

### 2. 动态快捷方式限量（最高优先级，改动最小）
- 默认只注入 **前 4-6 个**，其余收到管理页开关或“更多操作”二级入口
- 依据：转子菜单过长是盲人操作里最直接的痛点，一次滑动越过 10+ 项成本极高

### 3. 澎湃 4 补齐 REMOVE（从桌面移除）
- 现在只有 7.x 能用，澎湃 4 用户最缺这个
- 复用现有的 favorites 事务即可，不需要新 op（待和作者确认）

### 4. 转子动作开关（管理页）
- 让用户勾选“转子里显示哪些动作”，默认精简集
- 保存到 SharedPreferences，在 `addNodeActions` 前过滤

### 5. 文案优化
- “托起”比“长按拖动”好（已用）
- “和微信创建文件夹” → 建议“放入新文件夹（与微信）”，但更长，需权衡转子朗读长度
- 空位节点建议播报“空位，第 2 行第 3 列，微信右侧”（加邻居参照）

### 6. 状态播报衔接
- 托起后应主动播报：“已托起微信，请滑到目标位置选择移到前面/后面”
- 现在只播“已托起 X”（`HyperOs4NodeAdapter:495`），没有下一步引导

## 需要向作者确认

1. 转子动作顺序是否有人反馈过太长/顺序不对？
2. 动态快捷方式 24 个上限是有意为之还是随手上限？
3. 澎湃 4 为什么没实现 REMOVE（技术限制还是没做）？
4. “默认操作”你指的是转子菜单里的**顺序**，还是双击的**默认动作**？

---

# 补正（2026-10-19 00:07）：强哥说的「默认操作」= TalkBack 上下滑切换动作

## 更正我前面的判断
- 我把它理解成「转子（Rotor）」了，**不对**。
- 强哥指的是 TalkBack 的**单指上/下滑手势循环切换自定义动作**（滑一下换一个动作，双击执行）——这就是所谓「默认操作」。
- 代码里 `swipe` 全部是**页面翻页手势跟踪**（`handlePageSwipeMotion`、`CAP_PAGE_SCROLL_EVENT`），不是动作切换。

## 机制
- 上下滑循环的**数据源就是 `AccessibilityNodeInfo.getActionList()`**，TalkBack 自动遍历，**顺序 = actionId 升序**。
- 所以 ActionGrid 只要 `addAction`，动作就自动进入上下滑循环，**不需要额外代码**——这也是为什么仓库里找不到相关实现。

## 现状（上下滑顺序）
**空闲态图标**（`HyperOs4NodeAdapter.addNodeActions:396`）：
```
1. 托起            (0x6A000001)
2. 应用信息        (0x6A000006)
3. 卸载            (0x6A000007, 可卸载时)
4. 动态快捷方式 × 最多 24 个  (0x6A000100+)
```
**托起后指向目标图标**：
```
1. 移到 X 前面     (0x6A000003)
2. 移到 X 后面     (0x6A000004)
3. 和 X 创建文件夹  (0x6A000005, 可创建时)
```
**托起后指向源图标**：只剩「取消托起」(0x6A000002)。

## 这个机制下的真问题
1. **数量是灾难**：动态快捷方式 24 个 → 上下滑要滑 25 次才回到「托起」。盲人用户基本放弃。**最高优先级**
2. **澎湃 4 没有「从桌面移除」**（只有 Java 7.x 有，`ActionController:250`），整理桌面最常用的动作缺失
3. **顺序**：空闲态里「卸载」在动态快捷方式之前，但「移除」根本不存在；整理场景应把高频动作靠前
4. **托起后没有引导**：只播「已托起 X」(`HyperOs4NodeAdapter:495`)，没告诉用户「上下滑选择目标位置」
5. **命名偏长**：「移到 微信 前面」每次滑动都朗读，可考虑「移到微信前」/「插入前方」

## 建议的改动清单（可直接落地）
| 改动 | 位置 | 成本 |
|---|---|---|
| `DYNAMIC_COUNT` 24 → 4 | `ActionIds.java:14` | 一行 |
| 动态快捷方式改为管理页开关（默认关/限量） | `ActionIds` + `HyperOs4NodeAdapter:436` + `MainActivity` | 小 |
| 澎湃 4 补 REMOVE | `HyperOs4NodeAdapter.handleNodeAction` + 桌面 favorites 事务 | 中 |
| 托起后追加引导播报 | `HyperOs4NodeAdapter:495` / `ActionController:345` | 一行 |
| 动作命名压缩 | `addNodeActions` 文案 | 一行 |
| ID 重排（移除提前） | `ActionIds.java` | 小（注意旧版兼容） |

---

# ⭐ 最终对齐结论（2026-10-19 00:19，明天照着干）

## 机制（已确认，不再纠偏）
- 桌面图标上**一根手指上下滑动 = 在自定义动作列表里循环，双击执行当前项**
- 数据源 = `AccessibilityNodeInfo.getActionList()`，TalkBack 自动消费
- **顺序 = actionId 升序** → 想调顺序就是调 ID
- 与舒经合项目（yifutong）的 `CustomSemanticsAction`（`lib/pages/charge_page.dart:904`）是同一套机制

## 作者的原始设计意图
`native_init.cpp:4819` 注释：`// Keep this order in sync with HyperOS 4's long-press menu.`
→ **动作列表要照长按菜单来**。但这句话写在「最近任务卡片」那段，桌面图标那段没做完。

## 动作常量全表（native_init.cpp:323-352）
**桌面图标**
```
0x6A000001 托起            ✅ 已注入
0x6A000002 取消托起        ✅
0x6A000003 移到 X 前面     ✅
0x6A000004 移到 X 后面     ✅
0x6A000005 和 X 创建文件夹  ⚠️ 常量有，injectIconActions 未注入（澎湃4 用不了）
0x6A000006 应用信息        ✅
0x6A000007 卸载            ✅
0x6A000008 从桌面移除      ❌ 只有 Java 7.x 路径（ActionController:250），澎湃4 无
0x6A000009 编辑（文件夹）   ❌ 同上，仅 7.x
0x6A000100+ 动态快捷方式    ⚠️ kDynamicActionCount=24，上限过大
```
**最近任务卡片（这段做完整了，可作参照）**
```
0x6A000300 应用流转   0x6A000301 锁定/解除锁定
0x6A000302 支持分屏   0x6A000303 支持小窗
0x6A000304 应用详情   0x6A000305 关闭
```
**遗留**：`kAppMenuLabel = "应用操作"`（:323）定义了但全文件未引用 → 像是没做完的聚合入口

## 明天要补的四件事
1. **加「点击」动作**（自定义 ID 排最前，如 0x6A000000，label"点击"或"点击微信"）→ 处理时转发 `ACTION_CLICK`
2. **澎湃 4 补「从桌面移除」**（复用 favorites 事务；7.x 已有实现可参考 `ActionController:250` / `MiRuiHomeBridge.remove`）
3. **澎湃 4 补「新建文件夹」**（`kCreateFolderAction` 已定义，只需在 `injectIconActions` 里按可放置条件注入）
4. **动态快捷方式限流**：24 → 4（`kDynamicActionCount` + Java 侧 `ActionIds.DYNAMIC_COUNT` 同步）

## 环境与验证（已完成）
- JDK `/opt/jdk-21`、SDK `/opt/android-sdk`（android-37.0 + build-tools 37.0.0）、Gradle 9.5.1、阿里云镜像 `~/.gradle/init.gradle`
- `./gradlew assembleDebug` ✅ 2m07s；`./gradlew test` ✅ 全绿
- 产物 `app/build/outputs/apk/debug/app-debug.apk`（3.4 MB，含 arm64 native so 678 KB）
- 构建命令见 TOOLS.md「🧩 ActionGrid 构建环境」

## 明天开工顺序建议
1. 先装作者发的 v0.4 到强哥手机，实地感受现有上下滑顺序（确认我的推断）
2. 再改「点击」+「受限的快捷方式」这两条（改动小、可验证）
3. 然后动「移除」「新建文件夹」（涉及桌面事务，需真机验证）
