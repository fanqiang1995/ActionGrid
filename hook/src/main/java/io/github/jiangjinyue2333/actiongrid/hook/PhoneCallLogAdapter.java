package io.github.jiangjinyue2333.actiongrid.hook;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.View;
import android.view.accessibility.AccessibilityNodeInfo;

import java.lang.reflect.Method;
import java.util.List;

import io.github.libxposed.api.XposedInterface;
import io.github.libxposed.api.XposedModule;

/**
 * 电话/短信 App：给列表行补「默认操作」。
 *
 * 动作顺序：行本身的默认动作 → 行内按钮 → 官方长按菜单的各项。
 * 菜单项不缓存，每次让这一行自己构建菜单，再在窗口添加前拦下并直接执行，
 * 用户体感是双击即执行。
 */
final class PhoneCallLogAdapter {
    private static final String TAG = "ActionGrid-Phone";
    private static final String CONTACTS = "com.android.contacts";
    private static final String MMS = "com.android.mms";

    /** 通话记录行：点击=拨打，行内按钮=通话记录详情，其余来自官方菜单。 */
    private static final Spec CALL_LOG = new Spec(CONTACTS,
            CONTACTS + ":id/content_layout", null, 0x6B000001,
            new Entry(0, "拨打电话", Kind.ROW_CLICK, null),
            new Entry(1, "通话记录详情", Kind.CHILD_CLICK, "secondary_action_view"),
            new Entry(2, "发送短信", Kind.MENU, null),
            new Entry(3, "拨打IP电话", Kind.MENU, null),
            new Entry(4, "呼叫前编辑", Kind.MENU, null),
            new Entry(5, "复制号码", Kind.MENU, null),
            new Entry(6, "标记", Kind.MENU, null),
            new Entry(7, "删除", Kind.MENU, null),
            new Entry(8, "多选", Kind.MENU, null));

    /** 联系人行：点击=查看详情，其余来自官方菜单。 */
    private static final Spec CONTACT = new Spec(CONTACTS,
            CONTACTS + ":id/contact_list_item_view", null, 0x6B000101,
            new Entry(0, "查看", Kind.ROW_CLICK, null),
            new Entry(1, "编辑", Kind.MENU, null),
            new Entry(2, "添加收藏", Kind.MENU, null, "添加收藏", "取消收藏"),
            new Entry(3, "发送到桌面", Kind.MENU, null),
            new Entry(4, "删除", Kind.MENU, null),
            new Entry(5, "多选", Kind.MENU, null));

    /** 已存联系人的号码块。 */
    private static final Spec NUMBER_ACTIONS = new Spec(CONTACTS,
            CONTACTS + ":id/content_actions_view_container", null, 0x6B000201,
            new Entry(0, "拨打电话", Kind.CHILD_CLICK, "first_action_button"),
            new Entry(1, "发送短信", Kind.CHILD_CLICK, "secondary_action_button"),
            new Entry(2, "复制到剪贴板", Kind.MENU, null),
            new Entry(3, "拨打IP电话", Kind.MENU, null));

    /** 未存联系人的号码详情行。 */
    private static final Spec DETAIL_NUMBER = new Spec(CONTACTS,
            CONTACTS + ":id/detail_list_item", null, 0x6B000301,
            new Entry(0, "拨打电话", Kind.CHILD_CLICK, "first_action_button"),
            new Entry(1, "发送短信", Kind.CHILD_CLICK, "secondary_action_button"),
            new Entry(2, "复制到剪贴板", Kind.MENU, null),
            new Entry(3, "拨打IP电话", Kind.MENU, null));

    /** 短信会话行：这一版没有 resource-id，只能按类名匹配。 */
    private static final Spec SMS_CONVERSATION = new Spec(MMS,
            null, null, "HyperCellLayout", 0x6B000401,
            new Entry(0, "打开会话", Kind.ROW_CLICK, null),
            new Entry(1, "标为已读", Kind.MENU, null, "标为已读", "标为未读"),
            new Entry(2, "置顶", Kind.MENU, null, "置顶", "取消置顶"),
            new Entry(3, "删除", Kind.MENU, null),
            new Entry(4, "多选", Kind.MENU, null));

    /** 短信气泡：收藏/复制/转发/详情 等来自官方菜单。 */
    private static final Spec SMS_MESSAGE = new Spec(MMS,
            MMS + ":id/message_list_item", null, 0x6B000501,
            new Entry(0, "收藏", Kind.MENU, null, "收藏", "取消收藏"),
            new Entry(1, "复制", Kind.MENU, null),
            new Entry(2, "转发", Kind.MENU, null),
            new Entry(3, "详情", Kind.MENU, null),
            new Entry(4, "删除", Kind.MENU, null),
            new Entry(5, "反馈", Kind.MENU, null),
            new Entry(6, "多选", Kind.MENU, null));

    private static final java.util.Set<String> LOGGED_TYPES = new java.util.HashSet<>();
    private static final java.util.Map<String, String[]> DISCOVERED_MENUS =
            new java.util.concurrent.ConcurrentHashMap<>();
    private static final java.util.Set<String> DISCOVERING =
            java.util.concurrent.ConcurrentHashMap.newKeySet();
    private static final int MENU_DYNAMIC_OFFSET = 100;
    private static final int MENU_DYNAMIC_LIMIT = 32;

    /** 验证码类会话行：自定义 View，类名与普通会话不同。 */
    private static final Spec SMS_VERIFY_CODE = new Spec(MMS,
            null, null, "VerificationCodeListItem", 0x6B000421,
            new Entry(0, "打开会话", Kind.ROW_CLICK, null),
            new Entry(1, "标为已读", Kind.MENU, null, "标为已读", "标为未读"),
            new Entry(2, "置顶", Kind.MENU, null, "置顶", "取消置顶"),
            new Entry(3, "删除", Kind.MENU, null),
            new Entry(4, "多选", Kind.MENU, null));

    private static final Spec[] SPECS = {
            CALL_LOG, CONTACT, NUMBER_ACTIONS, DETAIL_NUMBER,
            SMS_CONVERSATION, SMS_VERIFY_CODE, SMS_MESSAGE};

    private static XposedModule host;
    private static Method nodeIsSealed;
    private static Method nodeSetSealed;
    private static Method findViewByAccessibilityId;
    private static Method nodeSourceId;
    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    private static volatile boolean installed;

    private PhoneCallLogAdapter() {
    }

    static synchronized boolean install(XposedModule module, ClassLoader appClassLoader) {
        if (installed) {
            return true;
        }
        try {
            Class<?> controllerClass =
                    Class.forName("android.view.AccessibilityInteractionController");
            Class<?> callbackClass = Class.forName(
                    "android.view.accessibility.IAccessibilityInteractionConnectionCallback");

            Method returnSingleResult = accessible(controllerClass.getDeclaredMethod(
                    "returnFindNodeResult", AccessibilityNodeInfo.class, callbackClass, int.class));
            Method returnListResult = accessible(controllerClass.getDeclaredMethod(
                    "returnFindNodesResult", List.class, callbackClass, int.class));
            Method performActionClientThread = accessible(controllerClass.getDeclaredMethod(
                    "performAccessibilityActionClientThread", long.class, int.class,
                    Bundle.class, int.class, callbackClass, int.class, int.class, long.class));
            findViewByAccessibilityId = accessible(controllerClass.getDeclaredMethod(
                    "findViewByAccessibilityId", int.class));
            nodeSourceId = accessible(
                    AccessibilityNodeInfo.class.getDeclaredMethod("getSourceNodeId"));
            nodeIsSealed = accessible(
                    AccessibilityNodeInfo.class.getDeclaredMethod("isSealed"));
            nodeSetSealed = accessible(
                    AccessibilityNodeInfo.class.getDeclaredMethod("setSealed", boolean.class));
            host = module;

            module.hook(returnSingleResult)
                    .setId("actiongrid.phone.node.single.v1")
                    .intercept(chain -> {
                        if (chain.getArg(0) instanceof AccessibilityNodeInfo node) {
                            decorate(node, chain.getThisObject());
                        }
                        return chain.proceed();
                    });
            module.hook(returnListResult)
                    .setId("actiongrid.phone.node.list.v1")
                    .intercept(chain -> {
                        if (chain.getArg(0) instanceof List<?> nodes) {
                            Object controller = chain.getThisObject();
                            for (Object item : nodes) {
                                if (item instanceof AccessibilityNodeInfo node) {
                                    decorate(node, controller);
                                }
                            }
                        }
                        return chain.proceed();
                    });
            module.hook(performActionClientThread)
                    .setId("actiongrid.phone.action.v1")
                    .intercept(chain -> handleAction(chain));

            PhoneMenuBridge.install(module);

            installed = true;
            log("app adapter installed");
            return true;
        } catch (Throwable throwable) {
            log("install failed " + throwable.getClass().getSimpleName()
                    + ": " + throwable.getMessage());
            return false;
        }
    }

    private static void decorate(AccessibilityNodeInfo node, Object controller) {
        try {
            String viewId = node.getViewIdResourceName();
            String className = String.valueOf(node.getClassName());
            String packageName = String.valueOf(node.getPackageName());
            View rowView = null;
            String viewClassName = null;
            boolean resolved = false;
            for (Spec spec : SPECS) {
                if (!spec.packageName.equals(packageName)) {
                    continue;
                }
                boolean idMatch = spec.rowViewId != null && spec.rowViewId.equals(viewId);
                boolean classMatch = spec.classSuffix != null
                        && className.endsWith(spec.classSuffix);
                if (!idMatch && !classMatch) {
                    if (spec.viewClassSuffix == null) {
                        continue;
                    }
                    if (!resolved) {
                        resolved = true;
                        rowView = resolveRowView(node, controller);
                        viewClassName = rowView == null ? null : rowView.getClass().getName();
                    }
                    if (viewClassName == null
                            || !viewClassName.endsWith(spec.viewClassSuffix)) {
                        continue;
                    }
                }
                decorateRow(node, spec, rowView, controller);
                return;
            }
        } catch (Throwable ignored) {
            // 节点读取失败不影响原行为
        }
    }

    private static void decorateRow(AccessibilityNodeInfo node, Spec spec,
                                    View rowView, Object controller) {
        String key = spec.key();
        String[] menu = DISCOVERED_MENUS.get(key);
        if (menu == null) {
            menu = spec.presetMenuLabels();
            scheduleDiscovery(key, spec, node, controller, rowView);
        }
        for (Entry entry : spec.entries) {
            if (entry.kind != Kind.MENU) {
                addAction(node, entry.actionId(spec), entry.label);
            }
        }
        for (int index = 0; index < menu.length && index < MENU_DYNAMIC_LIMIT; index++) {
            addAction(node, spec.base + MENU_DYNAMIC_OFFSET + index, menu[index]);
        }
    }

    /** 第一次遇到这类行：后台静默长按一次，把官方菜单项全量读下来。 */
    private static void scheduleDiscovery(String key, Spec spec, AccessibilityNodeInfo node,
                                          Object controller, View rowView) {
        if (!DISCOVERING.add(key)) {
            return;
        }
        MAIN.post(() -> {
            View target = rowView != null ? rowView : resolveRowView(node, controller);
            if (target == null) {
                DISCOVERING.remove(key);
                return;
            }
            PhoneMenuBridge.discoverMenu(target, labels -> {
                try {
                    if (labels != null && labels.length > 0) {
                        DISCOVERED_MENUS.put(key, labels);
                        log("discovered " + key + " -> " + String.join("/", labels));
                    }
                } finally {
                    DISCOVERING.remove(key);
                }
            });
        });
    }

    /** 无障碍节点没有 id 时，用节点反查真实 View 的类名。 */
    private static View resolveRowView(AccessibilityNodeInfo node, Object controller) {
        try {
            if (!node.isClickable() || controller == null) {
                return null;
            }
            long nodeId = ((Number) nodeSourceId.invoke(node)).longValue();
            View view = (View) findViewByAccessibilityId.invoke(controller, (int) nodeId);
            if (view != null) {
                String key = "view:" + view.getClass().getName();
                synchronized (LOGGED_TYPES) {
                    if (LOGGED_TYPES.size() < 30 && LOGGED_TYPES.add(key)) {
                        log("resolved view class " + view.getClass().getName());
                    }
                }
            }
            return view;
        } catch (Throwable throwable) {
            return null;
        }
    }

    private static void addAction(AccessibilityNodeInfo node, int id, String label) {
        try {
            boolean sealed = (boolean) nodeIsSealed.invoke(node);
            if (sealed) {
                nodeSetSealed.invoke(node, false);
            }
            node.addAction(new AccessibilityNodeInfo.AccessibilityAction(id, label));
            if (sealed) {
                nodeSetSealed.invoke(node, true);
            }
        } catch (Throwable throwable) {
            log("addAction failed " + throwable.getClass().getSimpleName());
        }
    }

    private static Object handleAction(XposedInterface.Chain chain) throws Throwable {
        long nodeId = (long) chain.getArg(0);
        int action = (int) chain.getArg(1);
        Match match = resolve(action);
        if (match == null) {
            return chain.proceed();
        }
        Object controller = chain.getThisObject();
        Object callback = chain.getArg(4);
        int interactionId = (int) chain.getArg(3);
        View row = rowView(controller, nodeId);
        if (row == null) {
            deliver(callback, interactionId, false);
            return null;
        }
        MAIN.post(() -> {
            Entry entry = match.entry;
            String dynamicLabel = match.dynamicLabel;
            boolean handled = false;
            try {
                if (dynamicLabel != null) {
                    handled = PhoneMenuBridge.runMenuAction(row, dynamicLabel);
                } else if (entry != null) {
                    switch (entry.kind) {
                        case ROW_CLICK -> handled = row.performClick();
                        case CHILD_CLICK -> handled = clickChild(row, match.spec.packageName,
                                entry.childViewId);
                        case MENU -> handled = PhoneMenuBridge.runMenuAction(row, entry.menuLabels);
                        default -> { }
                    }
                }
            } catch (Throwable throwable) {
                log("action failed " + throwable.getClass().getSimpleName());
            }
            log((dynamicLabel != null ? dynamicLabel : entry == null ? "?" : entry.label)
                    + " handled=" + handled);
            deliver(callback, interactionId, handled);
        });
        return null;
    }

    private static Match resolve(int action) {
        for (Spec spec : SPECS) {
            for (Entry entry : spec.entries) {
                if (entry.kind != Kind.MENU && entry.actionId(spec) == action) {
                    return new Match(spec, entry, null);
                }
            }
            int index = action - (spec.base + MENU_DYNAMIC_OFFSET);
            if (index >= 0 && index < MENU_DYNAMIC_LIMIT) {
                String[] menu = DISCOVERED_MENUS.get(spec.key());
                String label = null;
                if (menu != null && index < menu.length) {
                    label = menu[index];
                } else {
                    String[] preset = spec.presetMenuLabels();
                    if (index < preset.length) {
                        label = preset[index];
                    }
                }
                if (label != null) {
                    return new Match(spec, null, label);
                }
            }
        }
        return null;
    }

    private static boolean clickChild(View row, String packageName, String childViewId) {
        if (childViewId == null) {
            return false;
        }
        int id = row.getResources().getIdentifier(childViewId, "id", packageName);
        if (id == 0) {
            return false;
        }
        View target = row.findViewById(id);
        return target != null && target.performClick();
    }

    private static View rowView(Object controller, long nodeId) {
        try {
            return (View) findViewByAccessibilityId.invoke(controller, (int) nodeId);
        } catch (Throwable throwable) {
            return null;
        }
    }

    private static void deliver(Object callback, int interactionId, boolean handled) {
        try {
            Method setResult = callback.getClass().getMethod(
                    "setPerformAccessibilityActionResult", boolean.class, int.class);
            setResult.invoke(callback, handled, interactionId);
        } catch (Throwable throwable) {
            log("deliver failed " + throwable.getClass().getSimpleName());
        }
    }

    private static Method accessible(Method method) {
        method.setAccessible(true);
        return method;
    }

    private static void log(String message) {
        if (host == null) {
            return;
        }
        try {
            host.log(Log.INFO, TAG, message);
        } catch (RuntimeException ignored) {
            // 日志失败不影响功能
        }
    }

    private enum Kind {
        ROW_CLICK, CHILD_CLICK, MENU
    }

    private static final class Entry {
        final int offset;
        final String label;
        final Kind kind;
        final String childViewId;
        final String[] menuLabels;

        Entry(int offset, String label, Kind kind, String childViewId, String... menuAliases) {
            this.offset = offset;
            this.label = label;
            this.kind = kind;
            this.childViewId = childViewId;
            this.menuLabels = menuAliases == null || menuAliases.length == 0
                    ? new String[]{label} : menuAliases;
        }

        int actionId(Spec spec) {
            return spec.base + offset;
        }
    }

    private static final class Spec {
        final String packageName;
        final String rowViewId;
        final String classSuffix;
        final String viewClassSuffix;
        final int base;
        final List<Entry> entries;

        String key() {
            return packageName + "|" + rowViewId + "|" + classSuffix + "|" + viewClassSuffix;
        }

        String[] presetMenuLabels() {
            java.util.List<String> labels = new java.util.ArrayList<>();
            for (Entry entry : entries) {
                if (entry.kind == Kind.MENU) {
                    labels.add(entry.label);
                }
            }
            return labels.toArray(new String[0]);
        }

        Spec(String packageName, String rowViewId, String classSuffix, int base,
             Entry... entries) {
            this(packageName, rowViewId, classSuffix, null, base, entries);
        }

        Spec(String packageName, String rowViewId, String classSuffix,
             String viewClassSuffix, int base, Entry... entries) {
            this.packageName = packageName;
            this.rowViewId = rowViewId;
            this.classSuffix = classSuffix;
            this.viewClassSuffix = viewClassSuffix;
            this.base = base;
            this.entries = List.of(entries);
        }
    }

    private static final class Match {
        final Spec spec;
        final Entry entry;
        final String dynamicLabel;

        Match(Spec spec, Entry entry, String dynamicLabel) {
            this.spec = spec;
            this.entry = entry;
            this.dynamicLabel = dynamicLabel;
        }
    }
}
