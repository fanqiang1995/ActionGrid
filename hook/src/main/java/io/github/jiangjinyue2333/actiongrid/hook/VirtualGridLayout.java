package io.github.jiangjinyue2333.actiongrid.hook;

import java.util.ArrayList;
import java.util.List;

final class VirtualGridLayout {
    private VirtualGridLayout() {
    }

    static List<Cell> cells(int columns, int rows) {
        List<Cell> cells = new ArrayList<>(Math.max(0, columns * rows));
        for (int row = 0; row < rows; row++) {
            for (int column = 0; column < columns; column++) {
                cells.add(new Cell(1 + row * columns + column, column, row));
            }
        }
        return List.copyOf(cells);
    }

    static Geometry geometry(
            int column, int row, int columns, int rows,
            int hostWidth, int hostHeight,
            int paddingLeft, int paddingTop,
            int cellWidth, int cellHeight,
            int widthGap, int heightGap) {
        int strideX = cellWidth + widthGap;
        int strideY = cellHeight + heightGap;
        int left = column == 0 ? 0 : paddingLeft + column * strideX;
        int top = row == 0 ? 0 : paddingTop + row * strideY;
        int right = column == columns - 1
                ? hostWidth : paddingLeft + (column + 1) * strideX;
        int bottom = row == rows - 1
                ? hostHeight : paddingTop + (row + 1) * strideY;
        int centerX = paddingLeft + column * strideX + cellWidth / 2;
        int centerY = paddingTop + row * strideY + cellHeight / 2;
        return new Geometry(new Bounds(left, top, right, bottom), centerX, centerY);
    }

    static String description(
            String occupiedLabel, String emptyDescription, int column, int row) {
        return occupiedLabel == null || occupiedLabel.isBlank()
                ? emptyDescription : ItemLocationLabel.occupied(
                occupiedLabel, StableItemRef.CONTAINER_DESKTOP, column, row);
    }

    record Cell(int virtualId, int column, int row) {
    }

    record Bounds(int left, int top, int right, int bottom) {
    }

    record Geometry(Bounds bounds, int centerX, int centerY) {
    }
}
