package io.github.jiangjinyue2333.actiongrid.hook;

import android.graphics.Rect;
import android.os.Bundle;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityManager;
import android.view.accessibility.AccessibilityNodeInfo;
import android.view.accessibility.AccessibilityNodeProvider;

import java.util.ArrayList;
import java.util.List;

final class EmptyPositionAccessibilityProvider extends AccessibilityNodeProvider {
    private static final int INVALID_ID = Integer.MIN_VALUE;

    private final View host;
    private final ActionController controller;
    private final List<PositionTarget> targets;
    private int accessibilityFocusedId = INVALID_ID;
    private int hoveredId = INVALID_ID;

    EmptyPositionAccessibilityProvider(View host, ActionController controller,
                                       List<PositionTarget> targets) {
        this.host = host;
        this.controller = controller;
        this.targets = List.copyOf(targets);
    }

    @Override
    public AccessibilityNodeInfo createAccessibilityNodeInfo(int virtualViewId) {
        if (virtualViewId == HOST_VIEW_ID) {
            AccessibilityNodeInfo info = AccessibilityNodeInfo.obtain(host);
            host.onInitializeAccessibilityNodeInfo(info);
            for (PositionTarget target : targets()) {
                info.addChild(host, target.virtualId());
            }
            return info;
        }
        PositionTarget target = find(virtualViewId);
        return target == null ? null : createTargetNode(target);
    }

    @Override
    public List<AccessibilityNodeInfo> findAccessibilityNodeInfosByText(
            String searched, int virtualViewId) {
        List<AccessibilityNodeInfo> matches = new ArrayList<>();
        if (searched == null) {
            return matches;
        }
        String needle = searched.toLowerCase(java.util.Locale.ROOT);
        for (PositionTarget target : targets()) {
            if (target.description().toLowerCase(java.util.Locale.ROOT).contains(needle)) {
                matches.add(createTargetNode(target));
            }
        }
        return matches;
    }

    @Override
    public AccessibilityNodeInfo findFocus(int focus) {
        if (focus != AccessibilityNodeInfo.FOCUS_ACCESSIBILITY
                || accessibilityFocusedId == INVALID_ID) {
            return null;
        }
        PositionTarget target = find(accessibilityFocusedId);
        return target == null ? null : createTargetNode(target);
    }

    @Override
    public boolean performAction(int virtualViewId, int action, Bundle arguments) {
        PositionTarget target = find(virtualViewId);
        if (target == null) {
            return false;
        }
        if (action != AccessibilityNodeInfo.ACTION_ACCESSIBILITY_FOCUS
                && action != AccessibilityNodeInfo.ACTION_CLEAR_ACCESSIBILITY_FOCUS) {
            boolean handled = controller.performPositionTarget(target, action);
            if (handled) {
                sendEvent(virtualViewId, AccessibilityEvent.TYPE_VIEW_CLICKED);
            }
            return handled;
        }

        if (action == AccessibilityNodeInfo.ACTION_ACCESSIBILITY_FOCUS) {
            if (accessibilityFocusedId == virtualViewId) {
                return false;
            }
            int previous = accessibilityFocusedId;
            accessibilityFocusedId = virtualViewId;
            if (previous != INVALID_ID) {
                sendEvent(previous, AccessibilityEvent.TYPE_VIEW_ACCESSIBILITY_FOCUS_CLEARED);
            }
            sendEvent(virtualViewId, AccessibilityEvent.TYPE_VIEW_ACCESSIBILITY_FOCUSED);
            return true;
        }
        if (action == AccessibilityNodeInfo.ACTION_CLEAR_ACCESSIBILITY_FOCUS
                && accessibilityFocusedId == virtualViewId) {
            accessibilityFocusedId = INVALID_ID;
            sendEvent(virtualViewId, AccessibilityEvent.TYPE_VIEW_ACCESSIBILITY_FOCUS_CLEARED);
            return true;
        }
        return false;
    }
    boolean dispatchHoverEvent(MotionEvent event) {
        AccessibilityManager manager = manager();
        if (manager == null || !manager.isTouchExplorationEnabled()) {
            return false;
        }
        if (event.getActionMasked() == MotionEvent.ACTION_HOVER_EXIT) {
            boolean handled = hoveredId != INVALID_ID;
            updateHoveredId(INVALID_ID);
            return handled;
        }
        if (event.getActionMasked() != MotionEvent.ACTION_HOVER_ENTER
                && event.getActionMasked() != MotionEvent.ACTION_HOVER_MOVE) {
            return false;
        }
        if (!targets.isEmpty()
                && targets.get(0).container() == StableItemRef.CONTAINER_HOTSEAT
                && isOverActionableChild(event.getX(), event.getY())) {
            updateHoveredId(INVALID_ID);
            return false;
        }
        int targetId = hitTest(event.getX(), event.getY());
        updateHoveredId(targetId);
        return targetId != INVALID_ID;
    }

    void reset() {
        if (hoveredId != INVALID_ID) {
            sendEvent(hoveredId, AccessibilityEvent.TYPE_VIEW_HOVER_EXIT);
        }
        if (accessibilityFocusedId != INVALID_ID) {
            sendEvent(accessibilityFocusedId,
                    AccessibilityEvent.TYPE_VIEW_ACCESSIBILITY_FOCUS_CLEARED);
        }
        hoveredId = INVALID_ID;
        accessibilityFocusedId = INVALID_ID;
    }

    private AccessibilityNodeInfo createTargetNode(PositionTarget target) {
        AccessibilityNodeInfo info = AccessibilityNodeInfo.obtain();
        info.setPackageName(host.getContext().getPackageName());
        info.setClassName(View.class.getName());
        info.setSource(host, target.virtualId());
        info.setParent(host);
        info.setContentDescription(target.description());
        info.setEnabled(host.isEnabled());
        info.setVisibleToUser(host.isShown());
        info.setFocusable(true);
        info.setScreenReaderFocusable(true);
        boolean clickable = controller.onInitializePositionNode(target, info);
        info.setClickable(clickable);
        info.setBoundsInParent(target.boundsInParent());
        Rect screenBounds = new Rect(target.boundsInParent());
        int[] location = new int[2];
        host.getLocationOnScreen(location);
        screenBounds.offset(location[0], location[1]);
        info.setBoundsInScreen(screenBounds);
        if (clickable) {
            info.addAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_CLICK);
        }
        if (accessibilityFocusedId == target.virtualId()) {
            info.setAccessibilityFocused(true);
            info.addAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_CLEAR_ACCESSIBILITY_FOCUS);
        } else {
            info.setAccessibilityFocused(false);
            info.addAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_ACCESSIBILITY_FOCUS);
        }
        return info;
    }

    private int hitTest(float x, float y) {
        for (PositionTarget target : targets()) {
            if (target.boundsInParent().contains((int) x, (int) y)) {
                return target.virtualId();
            }
        }
        return INVALID_ID;
    }

    private boolean isOverActionableChild(float x, float y) {
        if (!(host instanceof ViewGroup group)) {
            return false;
        }
        int[] location = new int[2];
        host.getLocationOnScreen(location);
        return containsActionableChild(group, location[0] + (int) x, location[1] + (int) y);
    }

    private static boolean containsActionableChild(ViewGroup parent, int x, int y) {
        Rect bounds = new Rect();
        for (int index = 0; index < parent.getChildCount(); index++) {
            View child = parent.getChildAt(index);
            if (!child.isShown() || !child.getGlobalVisibleRect(bounds)
                    || !bounds.contains(x, y)) {
                continue;
            }
            if (child.isClickable() || child.isLongClickable() || child.isFocusable()
                    || child.getContentDescription() != null) {
                return true;
            }
            if (child instanceof ViewGroup group && containsActionableChild(group, x, y)) {
                return true;
            }
        }
        return false;
    }

    private void updateHoveredId(int next) {
        if (hoveredId == next) {
            return;
        }
        int previous = hoveredId;
        hoveredId = next;
        if (previous != INVALID_ID) {
            sendEvent(previous, AccessibilityEvent.TYPE_VIEW_HOVER_EXIT);
        }
        if (next != INVALID_ID) {
            sendEvent(next, AccessibilityEvent.TYPE_VIEW_HOVER_ENTER);
        }
    }

    private void sendEvent(int virtualId, int eventType) {
        AccessibilityManager manager = manager();
        if (manager == null || !manager.isEnabled() || host.getParent() == null) {
            return;
        }
        PositionTarget target = find(virtualId);
        AccessibilityEvent event = AccessibilityEvent.obtain(eventType);
        event.setPackageName(host.getContext().getPackageName());
        event.setClassName(View.class.getName());
        event.setEnabled(host.isEnabled());
        event.setSource(host, virtualId);
        if (target != null) {
            event.setContentDescription(target.description());
        }
        host.getParent().requestSendAccessibilityEvent(host, event);
    }

    private AccessibilityManager manager() {
        return host.getContext().getSystemService(AccessibilityManager.class);
    }

    private PositionTarget find(int virtualId) {
        for (PositionTarget target : targets()) {
            if (target.virtualId() == virtualId) {
                return target;
            }
        }
        return null;
    }

    private List<PositionTarget> targets() {
        return targets;
    }
}
