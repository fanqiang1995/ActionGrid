package io.github.jiangjinyue2333.actiongrid.hook;

public record StableItemRef(
        long id,
        long container,
        long screenId,
        int cellX,
        int cellY,
        int itemType,
        int spanX,
        int spanY
) implements Comparable<StableItemRef> {
    public static final long CONTAINER_DESKTOP = -100;
    public static final long CONTAINER_HOTSEAT = -101;

    public boolean isSupportedShortcut() {
        return (itemType == 0 || itemType == 1 || itemType == 14)
                && spanX == 1 && spanY == 1
                && (container == CONTAINER_DESKTOP
                || container == CONTAINER_HOTSEAT
                || container > 0);
    }

    public boolean isSupportedFolder() {
        return itemType == 2 && spanX == 1 && spanY == 1
                && (container == CONTAINER_DESKTOP || container == CONTAINER_HOTSEAT);
    }

    public boolean isSupportedLiftSource() {
        return isSupportedShortcut() || isSupportedFolder();
    }

    @Override
    public int compareTo(StableItemRef other) {
        int byScreen = Long.compare(screenId, other.screenId);
        if (byScreen != 0) {
            return byScreen;
        }
        int byRow = Integer.compare(cellY, other.cellY);
        return byRow != 0 ? byRow : Integer.compare(cellX, other.cellX);
    }
}
