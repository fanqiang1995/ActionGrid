package io.github.jiangjinyue2333.actiongrid.hook;

import android.content.ComponentName;
import android.os.UserHandle;

import java.util.List;

record BoundActions(
        long itemId,
        AppTarget appTarget,
        boolean canUninstall,
        boolean canRemove,
        boolean shortcutsLoaded,
        List<DynamicAction> dynamicActions
) {
    BoundActions {
        dynamicActions = List.copyOf(dynamicActions);
    }

    BoundActions withDynamicActions(List<DynamicAction> actions) {
        return new BoundActions(itemId, appTarget, canUninstall, canRemove, true, actions);
    }

    BoundActions refreshingDynamicActions(List<DynamicAction> actions) {
        return new BoundActions(itemId, appTarget, canUninstall, canRemove, false, actions);
    }
}

record AppTarget(ComponentName component, UserHandle user) {
}

record DynamicAction(String label, String packageName, String shortcutId, UserHandle user) {
}
