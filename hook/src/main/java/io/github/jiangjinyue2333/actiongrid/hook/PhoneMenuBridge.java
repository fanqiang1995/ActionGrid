package io.github.jiangjinyue2333.actiongrid.hook;

import android.os.SystemClock;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Adapter;
import android.widget.ListView;
import android.widget.PopupWindow;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

import io.github.libxposed.api.XposedModule;

/**
 * 电话 App 的官方菜单桥。
 *
 * MIUI 的上下文菜单最终会把 DecorView 加进 WindowManager，所以在 addView 处拦截：
 * 命中等待中的菜单项 → 直接调用该项自己的动作 → 拒绝添加窗口（菜单不显示、不朗读）。
 * 菜单对象每次都现建，保证绑定的是当前这一行的数据，不做缓存复用。
 */
final class PhoneMenuBridge {
    private static final String TAG = "ActionGrid-Phone";
    private static final long PENDING_MENU_MILLIS = 2_000L;

    private static XposedModule host;
    private static volatile String pendingLabel;
    private static volatile String[] pendingLabels;
    private static volatile boolean discovering;
    private static volatile MenuListener discoveryListener;
    private static volatile long pendingAt;
    private static volatile boolean installed;

    private PhoneMenuBridge() {
    }

    static synchronized void install(XposedModule module) {
        if (installed) {
            return;
        }
        host = module;
        try {
            Class<?> impl = Class.forName("android.view.WindowManagerImpl");
            Method addView = impl.getDeclaredMethod(
                    "addView", View.class, ViewGroup.LayoutParams.class);
            addView.setAccessible(true);
            module.hook(addView)
                    .setId("actiongrid.phone.window.addview.v1")
                    .intercept(chain -> {
                        Object added = chain.getArg(0);
                        if (discovering && added instanceof View discovered) {
                            String[] labels = menuLabels(discovered);
                            MenuListener listener = discoveryListener;
                            discovering = false;
                            discoveryListener = null;
                            if (listener != null) {
                                listener.onMenuLabels(labels);
                            }
                            log("menu discovered items="
                                    + (labels == null ? -1 : labels.length));
                            return null;
                        }
                        String label = pendingLabel;
                        if (label == null || SystemClock.elapsedRealtime() - pendingAt
                                > PENDING_MENU_MILLIS || !(added instanceof View view)) {
                            return chain.proceed();
                        }
                        // 第一个窗口就消费掉挂起状态，避免误拦后续无关弹窗
                        pendingLabel = null;
                        boolean handled = false;
                        try {
                            handled = captureAndInvoke(view, pendingLabels);
                        } catch (Throwable throwable) {
                            log("menu capture failed " + throwable.getClass().getSimpleName());
                        }
                        log("menu " + label + " " + (handled ? "invoked" : "not found")
                                + " window=" + view.getClass().getSimpleName());
                        if (!handled) {
                            return chain.proceed();
                        }
                        return null;
                    });
            // 兜底：部分版本走 PopupWindow.show*
            installPopupHooks(module);
            installed = true;
            log("menu bridge installed");
        } catch (Throwable throwable) {
            log("menu bridge unavailable " + throwable.getClass().getSimpleName()
                    + ": " + throwable.getMessage());
        }
    }

    private static void installPopupHooks(XposedModule module) {
        String[][] targets = {
                {"showAsDropDown", "3"}, {"showAsDropDown", "1"}, {"showAtLocation", "4"}};
        for (String[] target : targets) {
            try {
                Method method = "showAsDropDown".equals(target[0])
                        ? ("3".equals(target[1])
                        ? PopupWindow.class.getDeclaredMethod("showAsDropDown",
                        View.class, int.class, int.class)
                        : PopupWindow.class.getDeclaredMethod("showAsDropDown", View.class))
                        : PopupWindow.class.getDeclaredMethod("showAtLocation",
                        View.class, int.class, int.class, int.class);
                method.setAccessible(true);
                module.hook(method)
                        .setId("actiongrid.phone.popup." + target[0] + target[1])
                        .intercept(chain -> {
                            String label = pendingLabel;
                            if (label == null || SystemClock.elapsedRealtime() - pendingAt
                                    > PENDING_MENU_MILLIS) {
                                return chain.proceed();
                            }
                            pendingLabel = null;
                            boolean handled = false;
                            try {
                                handled = captureAndInvoke(
                                        (PopupWindow) chain.getThisObject(), pendingLabels);
                            } catch (Throwable throwable) {
                                log("popup capture failed "
                                        + throwable.getClass().getSimpleName());
                            }
                            return handled ? null : chain.proceed();
                        });
            } catch (Throwable throwable) {
                log("popup hook " + target[0] + " unavailable");
            }
        }
    }

    interface MenuListener {
        void onMenuLabels(String[] labels);
    }

    /** 静默长按一次，只为读出官方菜单项；菜单不会显示。 */
    static boolean discoverMenu(View row, MenuListener listener) {
        discoveryListener = listener;
        discovering = true;
        pendingLabel = null;
        boolean clicked = row.performLongClick();
        if (discovering) {
            discovering = false;
            discoveryListener = null;
        }
        return clicked;
    }

    private static String[] menuLabels(View window) {
        ListView list = findListView(window);
        if (list == null) {
            return null;
        }
        Adapter adapter = list.getAdapter();
        if (adapter == null) {
            return null;
        }
        java.util.List<String> labels = new java.util.ArrayList<>();
        for (int position = 0; position < adapter.getCount(); position++) {
            String title = title(adapter.getItem(position));
            if (title != null && !title.trim().isEmpty()) {
                labels.add(title);
            }
        }
        return labels.toArray(new String[0]);
    }

    /** 触发这一行的官方长按菜单，并把 label 对应的那一项直接执行掉。 */
    static boolean runMenuAction(View row, String... labels) {
        pendingLabel = labels == null || labels.length == 0 ? "" : labels[0];
        pendingLabels = labels;
        pendingAt = SystemClock.elapsedRealtime();
        boolean clicked = row.performLongClick();
        boolean handled = pendingLabel == null;
        log("long press for " + pendingLabel + " " + clicked + " handled=" + handled);
        return handled;
    }

    private static boolean captureAndInvoke(Object window, String[] labels) {
        View content = window instanceof PopupWindow popup
                ? contentView(popup) : (View) window;
        if (content == null) {
            return false;
        }
        ListView list = findListView(content);
        if (list == null) {
            return false;
        }
        Adapter adapter = list.getAdapter();
        if (adapter == null) {
            return false;
        }
        for (int position = 0; position < adapter.getCount(); position++) {
            Object item = adapter.getItem(position);
            String title = normalize(title(item));
            if (title.isEmpty()) {
                continue;
            }
            for (String candidate : labels) {
                if (title.equals(normalize(candidate))) {
                    return activate(list, adapter, position, item);
                }
            }
        }
        return false;
    }

    private static boolean activate(ListView list, Adapter adapter, int position, Object item) {
        try {
            Method invoke = item.getClass().getMethod("invoke");
            invoke.setAccessible(true);
            Object result = invoke.invoke(item);
            if (!(result instanceof Boolean) || (Boolean) result) {
                return true;
            }
        } catch (Throwable ignored) {
            // 退回到 ListView 派发
        }
        try {
            return list.performItemClick(null, position, adapter.getItemId(position));
        } catch (Throwable throwable) {
            log("item click failed " + throwable.getClass().getSimpleName());
            return false;
        }
    }

    private static View contentView(PopupWindow window) {
        try {
            Field field = PopupWindow.class.getDeclaredField("mContentView");
            field.setAccessible(true);
            return (View) field.get(window);
        } catch (Throwable throwable) {
            return null;
        }
    }

    static ListView findListView(View view) {
        if (view instanceof ListView list) {
            return list;
        }
        if (view instanceof ViewGroup group) {
            for (int index = 0; index < group.getChildCount(); index++) {
                ListView found = findListView(group.getChildAt(index));
                if (found != null) {
                    return found;
                }
            }
        }
        return null;
    }

    private static String title(Object item) {
        if (item instanceof android.view.MenuItem menuItem) {
            CharSequence value = menuItem.getTitle();
            return value == null ? null : value.toString();
        }
        return item == null ? null : item.toString();
    }

    private static String normalize(String value) {
        return value == null ? "" : value.replace(" ", "").replace("\u00A0", "");
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
}
