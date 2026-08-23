package io.github.jiangjinyue2333.actiongrid.hook;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public final class VirtualGridLayoutTest {
    @Test
    public void exposesEveryCellIncludingAllColumnsOfSixthRow() {
        var cells = VirtualGridLayout.cells(4, 6);

        assertEquals(24, cells.size());
        assertEquals(new VirtualGridLayout.Cell(21, 0, 5), cells.get(20));
        assertEquals(new VirtualGridLayout.Cell(24, 3, 5), cells.get(23));
    }

    @Test
    public void usesLauncherCellGeometryForSixthRowDropCenter() {
        VirtualGridLayout.Geometry geometry = VirtualGridLayout.geometry(
                3, 5, 4, 6,
                1080, 2400,
                40, 180,
                200, 280,
                20, 40);

        assertEquals(800, geometry.centerX());
        assertEquals(1920, geometry.centerY());
        assertEquals(700, geometry.bounds().left());
        assertEquals(1780, geometry.bounds().top());
        assertEquals(1080, geometry.bounds().right());
        assertEquals(2400, geometry.bounds().bottom());
    }

    @Test
    public void occupiedCellUsesIconNameAndCoordinates() {
        assertEquals("微信，第 6 行第 3 列",
                VirtualGridLayout.description("微信", "移至第 6 行第 3 列", 2, 5));
        assertEquals("移至第 6 行第 3 列",
                VirtualGridLayout.description(null, "移至第 6 行第 3 列", 2, 5));
    }
}
