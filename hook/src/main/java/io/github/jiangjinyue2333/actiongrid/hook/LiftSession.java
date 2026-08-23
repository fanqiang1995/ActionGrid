package io.github.jiangjinyue2333.actiongrid.hook;

import android.view.View;

import java.lang.ref.WeakReference;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

final class LiftSession {
    private final List<Object> sourceItems;
    private final List<StableItemRef> sourceRefs;
    private final Set<Long> sourceIds;
    private final WeakReference<View> anchor;
    private final boolean multiSelect;

    LiftSession(List<Object> sourceItems, List<StableItemRef> sourceRefs, View anchor,
                boolean multiSelect) {
        this.sourceItems = List.copyOf(sourceItems);
        this.sourceRefs = List.copyOf(sourceRefs);
        this.sourceIds = sourceRefs.stream().map(StableItemRef::id).collect(Collectors.toUnmodifiableSet());
        this.anchor = new WeakReference<>(anchor);
        this.multiSelect = multiSelect;
    }

    List<Object> sourceItems() {
        return sourceItems;
    }

    List<StableItemRef> sourceRefs() {
        return sourceRefs;
    }

    Set<Long> sourceIds() {
        return sourceIds;
    }

    View anchor() {
        return anchor.get();
    }

    boolean multiSelect() {
        return multiSelect;
    }
}
