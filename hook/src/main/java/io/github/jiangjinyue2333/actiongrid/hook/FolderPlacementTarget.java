package io.github.jiangjinyue2333.actiongrid.hook;

final class FolderPlacementTarget {
    private FolderPlacementTarget() {
    }

    static int anchorIndex(
            PlacementAction action, int targetIndex, int itemCount, int sourceIndex) {
        if (action == PlacementAction.MOVE_BEFORE) {
            return targetIndex;
        }
        for (int index = targetIndex + 1; index < itemCount; index++) {
            if (index != sourceIndex) {
                return index;
            }
        }
        return itemCount;
    }
}
