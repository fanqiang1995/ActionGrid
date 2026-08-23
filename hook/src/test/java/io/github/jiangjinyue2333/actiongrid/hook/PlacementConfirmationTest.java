package io.github.jiangjinyue2333.actiongrid.hook;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public final class PlacementConfirmationTest {
    @Test
    public void confirmsFromItemObservedAtTargetInsteadOfStaleSourceSnapshot() {
        StableItemRef staleSource = item(7, StableItemRef.CONTAINER_DESKTOP, 0, 0, 0, 0);
        StableItemRef observedAtTarget = item(7, StableItemRef.CONTAINER_DESKTOP, 1, 3, 5, 0);

        assertFalse(PlacementConfirmation.matches(
                staleSource, StableItemRef.CONTAINER_DESKTOP, 1, 3, 5));
        assertTrue(PlacementConfirmation.matches(
                observedAtTarget, StableItemRef.CONTAINER_DESKTOP, 1, 3, 5));
    }

    @Test
    public void rejectsDifferentItemAtRequestedPosition() {
        StableItemRef other = item(8, StableItemRef.CONTAINER_DESKTOP, 1, 3, 5, 0);

        assertFalse(PlacementConfirmation.matchesItemAtPosition(
                7, other, StableItemRef.CONTAINER_DESKTOP, 1, 3, 5));
    }

    private static StableItemRef item(
            long id, long container, long screen, int x, int y, int itemType) {
        return new StableItemRef(id, container, screen, x, y, itemType, 1, 1);
    }
}
