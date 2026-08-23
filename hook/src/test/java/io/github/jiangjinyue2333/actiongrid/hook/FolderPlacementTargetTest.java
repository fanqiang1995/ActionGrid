package io.github.jiangjinyue2333.actiongrid.hook;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public final class FolderPlacementTargetTest {
    @Test
    public void afterUsesTheNextFolderItemAsTheNativeInsertAnchor() {
        assertEquals(2, FolderPlacementTarget.anchorIndex(
                PlacementAction.MOVE_AFTER, 1, 4, -1));
    }

    @Test
    public void afterTheLastFolderItemUsesTheTemporaryAppendSlot() {
        assertEquals(4, FolderPlacementTarget.anchorIndex(
                PlacementAction.MOVE_AFTER, 3, 4, -1));
    }

    @Test
    public void sourceItemIsSkippedWhenItOccupiesTheNextSlot() {
        assertEquals(3, FolderPlacementTarget.anchorIndex(
                PlacementAction.MOVE_AFTER, 1, 4, 2));
    }

    @Test
    public void beforeAlwaysUsesTheTargetItemItself() {
        assertEquals(1, FolderPlacementTarget.anchorIndex(
                PlacementAction.MOVE_BEFORE, 1, 4, -1));
    }
}
