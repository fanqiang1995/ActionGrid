package io.github.jiangjinyue2333.actiongrid.hook;

final class HyperOs4VirtualNodes {
    private static final int PREFIX = 0x6A100000;
    private static final int PREFIX_MASK = 0xFFFFE000;

    private HyperOs4VirtualNodes() {
    }

    static int id(HyperOs4Workspace.GridKind kind, int column, int row) {
        int kindBit = kind == HyperOs4Workspace.GridKind.HOTSEAT ? 0x1000 : 0;
        return PREFIX | kindBit | (row << 6) | column;
    }

    static boolean contains(int virtualId) {
        return (virtualId & PREFIX_MASK) == PREFIX;
    }

    static int accessibilityViewId(long nodeId) {
        return (int) nodeId;
    }

    static int virtualDescendantId(long nodeId) {
        return (int) (nodeId >> 32);
    }
}
