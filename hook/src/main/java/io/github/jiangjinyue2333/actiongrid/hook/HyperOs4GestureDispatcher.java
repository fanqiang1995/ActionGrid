package io.github.jiangjinyue2333.actiongrid.hook;

import android.app.Activity;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.os.SystemClock;
import android.view.InputDevice;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;

final class HyperOs4GestureDispatcher {
    private static final long MOVE_SETTLE_MILLIS = 320L;
    private static final int VERIFY_ATTEMPTS = 20;
    private static final long VERIFY_INTERVAL_MILLIS = 100L;

    interface Completion {
        void complete(boolean success, String announcement);
    }

    private final HyperOs4Workspace workspace;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final Handler verifier;

    HyperOs4GestureDispatcher(HyperOs4Workspace workspace) {
        this.workspace = workspace;
        HandlerThread thread = new HandlerThread("ActionGrid-placement-check");
        thread.start();
        verifier = new Handler(thread.getLooper());
    }

    void place(
            Activity activity,
            HyperOs4Workspace.ResolvedNode source,
            HyperOs4Workspace.ResolvedNode occupiedTarget,
            HyperOs4Workspace.DropTarget emptyTarget,
            HyperOs4Workspace.PlacementRelation relation,
            Completion completion
    ) {
        main.post(() -> start(activity, source, occupiedTarget, emptyTarget,
                relation, completion));
    }

    private void start(
            Activity activity,
            HyperOs4Workspace.ResolvedNode source,
            HyperOs4Workspace.ResolvedNode occupiedTarget,
            HyperOs4Workspace.DropTarget emptyTarget,
            HyperOs4Workspace.PlacementRelation relation,
            Completion completion
    ) {
        View decor = activity.getWindow().getDecorView();
        if (activity.isFinishing() || activity.isDestroyed()
                || !decor.isAttachedToWindow() || !decor.isShown()) {
            completion.complete(false, "桌面当前不可操作");
            return;
        }
        int[] offset = new int[2];
        decor.getLocationOnScreen(offset);
        float sourceX = source.bounds().centerX() - offset[0];
        float sourceY = source.bounds().centerY() - offset[1];
        Point destination = destination(decor, occupiedTarget, emptyTarget, relation, offset);
        long downTime = SystemClock.uptimeMillis();
        if (!dispatch(activity, downTime, downTime, MotionEvent.ACTION_DOWN, sourceX, sourceY)) {
            completion.complete(false, "桌面未接受拖动操作");
            return;
        }
        long hold = ViewConfiguration.getLongPressTimeout() + 100L;
        main.postDelayed(() -> {
            if (!dispatch(activity, downTime, SystemClock.uptimeMillis(),
                    MotionEvent.ACTION_MOVE, sourceX + 2F, sourceY + 2F)) {
                cancel(activity, downTime, sourceX, sourceY);
                completion.complete(false, "桌面未开始拖动");
                return;
            }
            main.postDelayed(() -> {
                dispatch(activity, downTime, SystemClock.uptimeMillis(),
                        MotionEvent.ACTION_MOVE, destination.x(), destination.y());
                long targetSettle = relation == HyperOs4Workspace.PlacementRelation.CREATE_FOLDER
                        ? 650L : MOVE_SETTLE_MILLIS;
                main.postDelayed(() -> {
                    dispatch(activity, downTime, SystemClock.uptimeMillis(),
                            MotionEvent.ACTION_UP, destination.x(), destination.y());
                    verify(source, occupiedTarget, emptyTarget, relation, completion, 0);
                }, targetSettle);
            }, 32L);
        }, hold);
    }

    private void verify(
            HyperOs4Workspace.ResolvedNode source,
            HyperOs4Workspace.ResolvedNode occupiedTarget,
            HyperOs4Workspace.DropTarget emptyTarget,
            HyperOs4Workspace.PlacementRelation relation,
            Completion completion,
            int attempt
    ) {
        verifier.postDelayed(() -> {
            long targetId = occupiedTarget == null
                    ? Long.MIN_VALUE : occupiedTarget.item().id();
            if (workspace.confirmPlacement(source.item().id(), targetId,
                    emptyTarget, relation)) {
                main.post(() -> completion.complete(true, "移动完成"));
            } else if (attempt + 1 < VERIFY_ATTEMPTS) {
                verify(source, occupiedTarget, emptyTarget, relation, completion, attempt + 1);
            } else {
                main.post(() -> completion.complete(false, "没有确认到桌面位置变化"));
            }
        }, VERIFY_INTERVAL_MILLIS);
    }

    private static Point destination(
            View decor,
            HyperOs4Workspace.ResolvedNode occupied,
            HyperOs4Workspace.DropTarget empty,
            HyperOs4Workspace.PlacementRelation relation,
            int[] offset
    ) {
        if (relation == HyperOs4Workspace.PlacementRelation.EMPTY) {
            return new Point(empty.centerX() - offset[0], empty.centerY() - offset[1]);
        }
        android.graphics.Rect bounds = occupied.bounds();
        int quarter = Math.max(1, bounds.width() / 4);
        boolean rtl = decor.getLayoutDirection() == View.LAYOUT_DIRECTION_RTL;
        int x;
        if (relation == HyperOs4Workspace.PlacementRelation.CREATE_FOLDER) {
            x = bounds.centerX();
        } else if ((relation == HyperOs4Workspace.PlacementRelation.BEFORE) ^ rtl) {
            x = bounds.left + quarter;
        } else {
            x = bounds.right - quarter;
        }
        return new Point(x - offset[0], bounds.centerY() - offset[1]);
    }

    private static boolean dispatch(
            Activity activity,
            long downTime,
            long eventTime,
            int action,
            float x,
            float y
    ) {
        MotionEvent event = MotionEvent.obtain(downTime, eventTime, action, x, y, 0);
        event.setSource(InputDevice.SOURCE_TOUCHSCREEN);
        try {
            return activity.dispatchTouchEvent(event);
        } finally {
            event.recycle();
        }
    }

    private static void cancel(Activity activity, long downTime, float x, float y) {
        dispatch(activity, downTime, SystemClock.uptimeMillis(), MotionEvent.ACTION_CANCEL, x, y);
    }

    private record Point(float x, float y) {
    }
}
