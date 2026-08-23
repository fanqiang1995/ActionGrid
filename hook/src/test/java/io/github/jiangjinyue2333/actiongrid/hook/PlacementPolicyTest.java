package io.github.jiangjinyue2333.actiongrid.hook;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.util.Set;

import org.junit.Test;

public final class PlacementPolicyTest {
    private static StableItemRef item(long id, long container, long screen, int x, int y) {
        return new StableItemRef(id, container, screen, x, y, 0, 1, 1);
    }

    private static StableItemRef item(
            long id, long container, long screen, int x, int y, int itemType) {
        return new StableItemRef(id, container, screen, x, y, itemType, 1, 1);
    }

    @Test
    public void desktopShortcutSupportsAllPlacements() {
        assertEquals(
                Set.of(PlacementAction.MOVE_BEFORE, PlacementAction.MOVE_AFTER,
                        PlacementAction.CREATE_FOLDER),
                PlacementPolicy.actionsFor(Set.of(1L), item(2, -100, 0, 1, 0),
                        TargetKind.SHORTCUT));
    }

    @Test
    public void hotseatShortcutCanBeLiftedAndUsedAsATarget() {
        StableItemRef hotseat = item(2, StableItemRef.CONTAINER_HOTSEAT, 0, 2, 0);

        assertTrue(hotseat.isSupportedShortcut());
        assertEquals(
                Set.of(PlacementAction.MOVE_BEFORE, PlacementAction.MOVE_AFTER,
                        PlacementAction.CREATE_FOLDER),
                PlacementPolicy.actionsFor(Set.of(1L), hotseat, TargetKind.SHORTCUT));
    }

    @Test
    public void appCreatedAndDeepShortcutsCanBeLifted() {
        assertTrue(item(2, StableItemRef.CONTAINER_DESKTOP, 0, 0, 0, 1)
                .isSupportedShortcut());
        assertTrue(item(3, StableItemRef.CONTAINER_DESKTOP, 0, 1, 0, 14)
                .isSupportedShortcut());
    }

    @Test
    public void ordinaryOneCellFolderCanBeLifted() {
        assertTrue(item(4, StableItemRef.CONTAINER_DESKTOP, 0, 2, 0, 2)
                .isSupportedFolder());
    }

    @Test
    public void liftedFolderCannotCreateNestedFolder() {
        StableItemRef target = item(5, StableItemRef.CONTAINER_DESKTOP, 0, 3, 0);

        assertTrue(PlacementPolicy.actionsFor(Set.of(4L), false, target, TargetKind.SHORTCUT)
                .contains(PlacementAction.MOVE_AFTER));
        assertTrue(!PlacementPolicy.actionsFor(Set.of(4L), false, target, TargetKind.SHORTCUT)
                .contains(PlacementAction.CREATE_FOLDER));
    }

    @Test
    public void desktopAndHotseatAreSymmetricDropTargets() {
        StableItemRef desktop = item(2, StableItemRef.CONTAINER_DESKTOP, 0, 1, 1);
        StableItemRef hotseat = item(3, StableItemRef.CONTAINER_HOTSEAT, 0, 2, 0);

        assertTrue(PlacementPolicy.actionsFor(Set.of(1L), desktop, TargetKind.SHORTCUT)
                .contains(PlacementAction.MOVE_AFTER));
        assertTrue(PlacementPolicy.actionsFor(Set.of(1L), hotseat, TargetKind.SHORTCUT)
                .contains(PlacementAction.MOVE_AFTER));
    }

    @Test
    public void folderTargetOnlySupportsDesktopReordering() {
        assertEquals(
                Set.of(PlacementAction.MOVE_BEFORE, PlacementAction.MOVE_AFTER),
                PlacementPolicy.actionsFor(Set.of(1L), item(2, -100, 0, 1, 0),
                        TargetKind.FOLDER));
        assertTrue(PlacementPolicy.actionsFor(
                Set.of(1L), item(2, 100, 0, 1, 0), TargetKind.FOLDER).isEmpty());
    }

    @Test
    public void sourceItemNeverBecomesTarget() {
        assertTrue(PlacementPolicy.actionsFor(
                Set.of(1L, 2L), item(2, -100, 0, 1, 0), TargetKind.SHORTCUT).isEmpty());
    }

    @Test
    public void itemOrderUsesScreenRowAndColumn() {
        assertTrue(item(1, -100, 0, 4, 0).compareTo(item(2, -100, 0, 0, 1)) < 0);
        assertTrue(item(1, -100, 0, 0, 1).compareTo(item(2, -100, 1, 0, 0)) < 0);
    }
}
