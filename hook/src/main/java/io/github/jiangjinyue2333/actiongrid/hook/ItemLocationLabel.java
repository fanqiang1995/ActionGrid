package io.github.jiangjinyue2333.actiongrid.hook;

final class ItemLocationLabel {
    private ItemLocationLabel() {
    }

    static String occupied(String label, long container, int cellX, int cellY) {
        if (container == StableItemRef.CONTAINER_DESKTOP) {
            return label + "，第 " + (cellY + 1) + " 行第 " + (cellX + 1) + " 列";
        }
        if (container == StableItemRef.CONTAINER_HOTSEAT) {
            return label + "，底栏第 " + (cellX + 1) + " 个位置";
        }
        return label;
    }

    static String emptyDesktop(int cellX, int cellY) {
        return "移至第 " + (cellY + 1) + " 行第 " + (cellX + 1) + " 列";
    }

    static String emptyHotseat(int position) {
        return "移至底栏第 " + (position + 1) + " 个位置";
    }
}
