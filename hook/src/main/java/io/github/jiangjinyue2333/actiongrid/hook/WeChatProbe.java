package io.github.jiangjinyue2333.actiongrid.hook;

import android.util.Log;
import android.view.View;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Collections;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * 反射读 WeChat 会话行 View 的状态。
 *
 * WeChat 的 ConversationFolderItemView 内部持有一个数据对象（通常是
 * BizRecentConversationInfo 之类），它上面有 isPinned / isUnread 之类的标志。
 * 我们不强依赖具体类型，而是：在 View 上扫描字段/方法，命中"看起来像"的状态。
 * 命中结果按 View 弱引用缓存，避免每次无障碍事件都全量反射。
 */
final class WeChatProbe {
    private static final String TAG = "ActionGrid-WeChat";

    private static final Map<View, boolean[]> CACHE =
            Collections.synchronizedMap(new WeakHashMap<>());

    private WeChatProbe() {
    }

    /** @return [isPinned, isUnread]；无法判断时返回 null。 */
    static boolean[] probe(View row) {
        if (row == null) {
            return null;
        }
        boolean[] cached = CACHE.get(row);
        if (cached != null) {
            return cached;
        }
        boolean[] state = detect(row);
        if (state != null) {
            CACHE.put(row, state);
        }
        return state;
    }

    private static boolean[] detect(View row) {
        Boolean pinned = firstBoolean(row, "isPinned", "pinned", "isTop", "isStick",
                "stickOnTop", "isOnTop");
        if (pinned == null) {
            pinned = firstBooleanMethod(row, "isPinned", "isTop", "isStick", "isOnTop");
        }
        if (pinned == null) {
            pinned = firstBooleanOnField(row, "mIsPinned", "mPinned", "mIsTop", "mIsStick",
                    "mStickOnTop", "mIsOnTop", "isPinned", "pinned", "isTop", "isStick");
        }
        Boolean unread = firstBooleanOnField(row, "mIsUnread", "mUnread", "unread", "isUnread",
                "hasUnread", "isUnreadMessage", "unreadCount");
        if (unread == null) {
            unread = firstBooleanMethod(row, "isUnread", "hasUnread", "isUnreadMessage");
        }
        if (unread == null) {
            // 兜底：unreadCount 是数字；>0 视作未读
            Integer count = firstIntOnField(row, "mUnreadCount", "unreadCount", "mUnread");
            if (count != null) {
                unread = count > 0;
            }
        }
        if (pinned == null && unread == null) {
            Log.d(TAG, "probe miss on " + row.getClass().getName());
            return null;
        }
        boolean[] out = new boolean[2];
        out[0] = pinned != null && pinned;
        out[1] = unread != null && unread;
        return out;
    }

    private static Boolean firstBoolean(View row, String... names) {
        Class<?> type = row.getClass();
        while (type != null && type != Object.class) {
            for (String name : names) {
                try {
                    Field field = type.getDeclaredField(name);
                    field.setAccessible(true);
                    Object value = field.get(row);
                    if (value instanceof Boolean bool) {
                        return bool;
                    }
                } catch (NoSuchFieldException ignored) {
                } catch (Throwable throwable) {
                    return null;
                }
            }
            type = type.getSuperclass();
        }
        return null;
    }

    private static Boolean firstBooleanMethod(View row, String... names) {
        Class<?> type = row.getClass();
        while (type != null && type != Object.class) {
            for (String name : names) {
                try {
                    Method method = type.getDeclaredMethod(name);
                    Class<?> ret = method.getReturnType();
                    if (ret == boolean.class || ret == Boolean.class) {
                        method.setAccessible(true);
                        Object value = method.invoke(row);
                        if (value instanceof Boolean bool) {
                            return bool;
                        }
                    }
                } catch (NoSuchMethodException ignored) {
                } catch (Throwable throwable) {
                    return null;
                }
            }
            type = type.getSuperclass();
        }
        return null;
    }

    /** 在 row 自身找不到时，扫它持有的非 View 字段，递归一层查 boolean。 */
    private static Boolean firstBooleanOnField(View row, String... preferredNames) {
        Class<?> type = row.getClass();
        while (type != null && type != Object.class) {
            for (Field field : type.getDeclaredFields()) {
                if (java.lang.reflect.Modifier.isStatic(field.getModifiers())) {
                    continue;
                }
                Class<?> fieldType = field.getType();
                if (fieldType == boolean.class || fieldType == Boolean.class) {
                    String name = field.getName();
                    if (!matches(name, preferredNames)) {
                        continue;
                    }
                    field.setAccessible(true);
                    try {
                        Object value = field.get(row);
                        if (value instanceof Boolean bool) {
                            return bool;
                        }
                    } catch (Throwable throwable) {
                        return null;
                    }
                }
            }
            type = type.getSuperclass();
        }
        return null;
    }

    private static Integer firstIntOnField(View row, String... preferredNames) {
        Class<?> type = row.getClass();
        while (type != null && type != Object.class) {
            for (Field field : type.getDeclaredFields()) {
                if (java.lang.reflect.Modifier.isStatic(field.getModifiers())) {
                    continue;
                }
                Class<?> fieldType = field.getType();
                if (fieldType != int.class && fieldType != Integer.class) {
                    continue;
                }
                String name = field.getName();
                if (!matches(name, preferredNames)) {
                    continue;
                }
                field.setAccessible(true);
                try {
                    Object value = field.get(row);
                    if (value instanceof Integer integer) {
                        return integer;
                    }
                } catch (Throwable throwable) {
                    return null;
                }
            }
            type = type.getSuperclass();
        }
        return null;
    }

    private static boolean matches(String name, String[] preferred) {
        for (String p : preferred) {
            if (name.equals(p)) {
                return true;
            }
        }
        // 大小写不敏感兜底
        for (String p : preferred) {
            if (name.equalsIgnoreCase(p)) {
                return true;
            }
        }
        return false;
    }

}
