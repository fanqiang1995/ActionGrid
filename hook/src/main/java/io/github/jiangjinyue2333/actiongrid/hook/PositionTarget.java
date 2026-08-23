package io.github.jiangjinyue2333.actiongrid.hook;

import android.graphics.Rect;
import android.view.View;

record PositionTarget(
        int virtualId,
        View host,
        long container,
        long screenId,
        int cellX,
        int cellY,
        Rect boundsInParent,
        int dropXInParent,
        int dropYInParent,
        View occupiedView,
        String description
) {
    PositionTarget {
        boundsInParent = new Rect(boundsInParent);
    }
}
