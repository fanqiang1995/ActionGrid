package io.github.jiangjinyue2333.actiongrid.hook;

final class PlacementConfirmation {
    private PlacementConfirmation() {
    }

    static boolean matches(
            StableItemRef item, long container, long screenId, int cellX, int cellY) {
        return item != null
                && item.container() == container
                && (container == StableItemRef.CONTAINER_HOTSEAT
                || item.screenId() == screenId)
                && item.cellX() == cellX
                && item.cellY() == cellY;
    }

    static boolean matchesItemAtPosition(
            long expectedItemId, StableItemRef observed,
            long container, long screenId, int cellX, int cellY) {
        return observed != null && observed.id() == expectedItemId
                && matches(observed, container, screenId, cellX, cellY);
    }
}
