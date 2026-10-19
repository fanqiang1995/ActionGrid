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
 * 电话 App：给「通话记录行」和「联系人行」补默认操作。
 *
 * 动作顺序：行本身的默认动作 → 行内按钮（如通话记录详情） → 官方长按菜单的各项。
 * 菜单项不缓存，每次让这一行自己构建菜单，再在窗口添加前拦下并直接执行，
 * 用户体感是双击即执行。
 */
final class PhoneCallLogAdapter {
    private static final String TAG = "ActionGrid-Phone";
    private static final String PACKAGE = "com.android.contacts";

    private static final int CALL_LOG_BASE = 0x6B000001;
    private static final int CONTACT_BASE = 0x6B000101;

    /** 通话记录行：点击=拨打，行内按钮=通话记录详情，其余来自官方菜单。 */
    private static final Spec CALL_LOG = new Spec(
            PACKAGE + ":id/content_layout",
            CALL_LOG_BASE,
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
    private static final Spec CONTACT = new Spec(
            PACKAGE + ":id/contact_list_item_view",
            CONTACT_BASE,
            new Entry(0, "查看", Kind.ROW_CLICK, null),
            new Entry(1, "编辑", Kind.MENU, null),
            new Entry(2, "添加收藏", Kind.MENU, null),
            new Entry(3, "发送到桌面", Kind.MENU, null),
            new Entry(4, "删除", Kind.MENU, null),
            new Entry(5, "多选", Kind.MENU, null));

    /** 详情页号码块：两个行内按钮 + 长按号码菜单。 */
    private static final Spec NUMBER_ACTIONS = new Spec(
            PACKAGE + ":id/content_actions_view_container",
            0x6B000201,
            new Entry(0, "拨打电话", Kind.CHILD_CLICK, "first_action_button"),
            new Entry(1, "发送短信", Kind.CHILD_CLICK, "secondary_action_button"),
            new Entry(2, "复制到剪贴板", Kind.MENU, null),
            new Entry(3, "拨打IP电话", Kind.MENU, null));

    /** 未存联系人的号码详情：行 id 与已存联系人不同。 */
    private static final Spec DETAIL_NUMBER = new Spec(
            PACKAGE + ":id/detail_list_item",
            0x6B000301,
            new Entry(0, "拨打电话", Kind.CHILD_CLICK, "first_action_button"),
            new Entry(1, "发送短信", Kind.CHILD_CLICK, "secondary_action_button"),
            new Entry(2, "复制到剪贴板", Kind.MENU, null),
            new Entry(3, "拨打IP电话", Kind.MENU, null));

    private static final Spec[] SPECS = {CALL_LOG, CONTACT, NUMBER_ACTIONS, DETAIL_NUMBER};

    private static XposedModule host;
    private static Method nodeIsSealed;
    private static Method nodeSetSealed;
    private static Method findViewByAccessibilityId;
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
            nodeIsSealed = accessible(
                    AccessibilityNodeInfo.class.getDeclaredMethod("isSealed"));
            nodeSetSealed = accessible(
                    AccessibilityNodeInfo.class.getDeclaredMethod("setSealed", boolean.class));
            host = module;

            module.hook(returnSingleResult)
                    .setId("actiongrid.phone.node.single.v1")
                    .intercept(chain -> {
                        if (chain.getArg(0) instanceof AccessibilityNodeInfo node) {
                            decorate(node);
                        }
                        return chain.proceed();
                    });
            module.hook(returnListResult)
                    .setId("actiongrid.phone.node.list.v1")
                    .intercept(chain -> {
                        if (chain.getArg(0) instanceof List<?> nodes) {
                            for (Object item : nodes) {
                                if (item instanceof AccessibilityNodeInfo node) {
                                    decorate(node);
                                }
                            }
                        }
                        return chain.proceed();
                    });
            module.hook(performActionClientThread)
                    .setId("actiongrid.phone.action.v1")
                    .intercept(chain -> handleAction(chain, callbackClass));

            PhoneMenuBridge.install(module);

            installed = true;
            log("phone adapter installed");
            return true;
        } catch (Throwable throwable) {
            log("install failed " + throwable.getClass().getSimpleName()
                    + ": " + throwable.getMessage());
            return false;
        }
    }

    private static void decorate(AccessibilityNodeInfo node) {
        try {
            CharSequence viewId = node.getViewIdResourceName();
            if (viewId == null) {
                return;
            }
            for (Spec spec : SPECS) {
                if (!spec.rowViewId.contentEquals(viewId)) {
                    continue;
                }
                for (Entry entry : spec.entries) {
                    addAction(node, entry.actionId(spec), entry.label);
                }
                return;
            }
        } catch (Throwable ignored) {
            // 节点读取失败不影响原行为
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

    private static Object handleAction(XposedInterface.Chain chain, Class<?> callbackClass)
            throws Throwable {
        long nodeId = (long) chain.getArg(0);
        int action = (int) chain.getArg(1);
        Entry entry = resolve(action);
        if (entry == null) {
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
            boolean handled = false;
            try {
                switch (entry.kind) {
                    case ROW_CLICK -> handled = row.performClick();
                    case CHILD_CLICK -> handled = clickChild(row, entry.childViewId);
                    case MENU -> handled = PhoneMenuBridge.runMenuAction(row, entry.label);
                    default -> { }
                }
            } catch (Throwable throwable) {
                log("action failed " + throwable.getClass().getSimpleName());
            }
            log(entry.label + " handled=" + handled);
            deliver(callback, interactionId, handled);
        });
        return null;
    }

    private static Entry resolve(int action) {
        for (Spec spec : SPECS) {
            for (Entry entry : spec.entries) {
                if (entry.actionId(spec) == action) {
                    return entry;
                }
            }
        }
        return null;
    }

    private static boolean clickChild(View row, String childViewId) {
        if (childViewId == null) {
            return false;
        }
        int id = row.getResources().getIdentifier(childViewId, "id", PACKAGE);
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

        Entry(int offset, String label, Kind kind, String childViewId) {
            this.offset = offset;
            this.label = label;
            this.kind = kind;
            this.childViewId = childViewId;
        }

        int actionId(Spec spec) {
            return spec.base + offset;
        }
    }

    private static final class Spec {
        final String rowViewId;
        final int base;
        final List<Entry> entries;

        Spec(String rowViewId, int base, Entry... entries) {
            this.rowViewId = rowViewId;
            this.base = base;
            this.entries = List.of(entries);
        }
    }
}
