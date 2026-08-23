package io.github.jiangjinyue2333.actiongrid.hook;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public final class ItemLocationLabelTest {
    @Test
    public void occupiedDesktopIconUsesOneBasedRowAndColumn() {
        assertEquals("QQ，第 6 行第 3 列",
                ItemLocationLabel.occupied("QQ", StableItemRef.CONTAINER_DESKTOP, 2, 5));
    }

    @Test
    public void occupiedHotseatIconUsesOfficialPositionTerm() {
        assertEquals("QQ，底栏第 3 个位置",
                ItemLocationLabel.occupied("QQ", StableItemRef.CONTAINER_HOTSEAT, 2, 0));
    }

    @Test
    public void emptyDesktopTargetUsesOfficialMovePhrase() {
        assertEquals("移至第 6 行第 3 列", ItemLocationLabel.emptyDesktop(2, 5));
    }

    @Test
    public void emptyHotseatTargetUsesOfficialMovePhrase() {
        assertEquals("移至底栏第 3 个位置", ItemLocationLabel.emptyHotseat(2));
    }
}
