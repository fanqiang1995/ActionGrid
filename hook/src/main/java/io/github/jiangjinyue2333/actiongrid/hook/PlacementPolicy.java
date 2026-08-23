package io.github.jiangjinyue2333.actiongrid.hook;

import java.util.EnumSet;
import java.util.Set;

public final class PlacementPolicy {
    private PlacementPolicy() {
    }

    public static Set<PlacementAction> actionsFor(
            Set<Long> sourceIds,
            StableItemRef target,
            TargetKind kind
    ) {
        return actionsFor(sourceIds, true, target, kind);
    }

    public static Set<PlacementAction> actionsFor(
            Set<Long> sourceIds,
            boolean canCreateFolder,
            StableItemRef target,
            TargetKind kind
    ) {
        EnumSet<PlacementAction> actions = EnumSet.noneOf(PlacementAction.class);
        if (target == null || sourceIds.contains(target.id())) {
            return actions;
        }
        if (kind == TargetKind.SHORTCUT && (target.container() == StableItemRef.CONTAINER_DESKTOP
                || target.container() == StableItemRef.CONTAINER_HOTSEAT
                || target.container() > 0)) {
            actions.add(PlacementAction.MOVE_BEFORE);
            actions.add(PlacementAction.MOVE_AFTER);
            if (canCreateFolder
                    && (target.container() == StableItemRef.CONTAINER_DESKTOP
                    || target.container() == StableItemRef.CONTAINER_HOTSEAT)) {
                actions.add(PlacementAction.CREATE_FOLDER);
            }
        } else if (kind == TargetKind.FOLDER
                && (target.container() == StableItemRef.CONTAINER_DESKTOP
                || target.container() == StableItemRef.CONTAINER_HOTSEAT)) {
            actions.add(PlacementAction.MOVE_BEFORE);
            actions.add(PlacementAction.MOVE_AFTER);
        }
        return actions;
    }
}
