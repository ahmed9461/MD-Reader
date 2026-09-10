package app.mdreader.mobile;

import android.content.Context;
import android.text.Layout;
import android.view.MotionEvent;
import android.view.VelocityTracker;
import android.view.ViewConfiguration;
import android.widget.EditText;
import android.widget.OverScroller;

/**
 * EditText with document-style kinetic vertical scrolling.
 *
 * A normal tap/long-press is still delegated to EditText so cursor placement,
 * selection handles and editing behave normally. A quick vertical drag switches
 * to scrolling; releasing the finger starts an OverScroller fling, and touching
 * the editor again stops the fling immediately.
 */
final class FlingEditText extends EditText {
    private final OverScroller flingScroller;
    private final int touchSlop;
    private final int minFlingVelocity;
    private final int maxFlingVelocity;

    private VelocityTracker velocityTracker;
    private float downX;
    private float downY;
    private float lastY;
    private boolean dragScrolling;
    private int flingSpeedPercent = 100;

    FlingEditText(Context context) {
        super(context);
        ViewConfiguration config = ViewConfiguration.get(context);
        touchSlop = config.getScaledTouchSlop();
        minFlingVelocity = config.getScaledMinimumFlingVelocity();
        maxFlingVelocity = config.getScaledMaximumFlingVelocity();
        flingScroller = new OverScroller(context);
    }

    void setFlingSpeedPercent(int percent) {
        flingSpeedPercent = Math.max(10, Math.min(500, percent));
    }

    int getFlingSpeedPercent() {
        return flingSpeedPercent;
    }

    void stopFling() {
        if (!flingScroller.isFinished()) flingScroller.abortAnimation();
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        final int action = event.getActionMasked();

        if (action == MotionEvent.ACTION_DOWN) {
            stopFling();
            recycleVelocityTracker();
            velocityTracker = VelocityTracker.obtain();
            velocityTracker.addMovement(event);
            downX = event.getX();
            downY = event.getY();
            lastY = downY;
            dragScrolling = false;
            return super.onTouchEvent(event);
        }

        if (velocityTracker != null) velocityTracker.addMovement(event);

        if (action == MotionEvent.ACTION_MOVE) {
            float y = event.getY();
            float dy = lastY - y;
            float totalY = downY - y;
            float totalX = event.getX() - downX;

            // Do not steal a gesture after Android has had time to enter
            // long-press text selection. Quick vertical swipes become scrolling.
            boolean beforeLongPress = event.getEventTime() - event.getDownTime()
                    < ViewConfiguration.getLongPressTimeout();
            if (!dragScrolling
                    && beforeLongPress
                    && Math.abs(totalY) > touchSlop
                    && Math.abs(totalY) > Math.abs(totalX)) {
                dragScrolling = true;
                cancelLongPress();
                MotionEvent cancel = MotionEvent.obtain(event);
                cancel.setAction(MotionEvent.ACTION_CANCEL);
                super.onTouchEvent(cancel);
                cancel.recycle();
            }

            lastY = y;
            if (dragScrolling) {
                scrollByClamped(Math.round(dy));
                return true;
            }
            return super.onTouchEvent(event);
        }

        if (action == MotionEvent.ACTION_UP) {
            if (dragScrolling) {
                if (velocityTracker != null) {
                    velocityTracker.computeCurrentVelocity(1000, maxFlingVelocity);
                    float fingerVelocity = velocityTracker.getYVelocity(event.getPointerId(0));
                    int scrollVelocity = Math.round(-fingerVelocity);
                    if (Math.abs(scrollVelocity) >= minFlingVelocity) {
                        startFling(scaleVelocity(scrollVelocity));
                    }
                }
                dragScrolling = false;
                recycleVelocityTracker();
                return true;
            }
            recycleVelocityTracker();
            return super.onTouchEvent(event);
        }

        if (action == MotionEvent.ACTION_CANCEL) {
            boolean wasScrolling = dragScrolling;
            dragScrolling = false;
            recycleVelocityTracker();
            return wasScrolling || super.onTouchEvent(event);
        }

        return super.onTouchEvent(event);
    }

    @Override
    public void computeScroll() {
        super.computeScroll();
        if (flingScroller.computeScrollOffset()) {
            int y = clamp(flingScroller.getCurrY(), 0, maxScrollY());
            scrollTo(0, y);
            awakenScrollBars();
            if (!flingScroller.isFinished()) postInvalidateOnAnimation();
        }
    }

    @Override
    protected void onDetachedFromWindow() {
        stopFling();
        recycleVelocityTracker();
        super.onDetachedFromWindow();
    }

    private void startFling(int velocityY) {
        int max = maxScrollY();
        if (max <= 0 || velocityY == 0) return;
        flingScroller.fling(0, getScrollY(), 0, velocityY, 0, 0, 0, max);
        postInvalidateOnAnimation();
    }

    private void scrollByClamped(int dy) {
        if (dy == 0) return;
        int target = clamp(getScrollY() + dy, 0, maxScrollY());
        scrollTo(0, target);
        awakenScrollBars();
    }

    private int maxScrollY() {
        Layout layout = getLayout();
        if (layout == null) return 0;
        int viewport = Math.max(1,
                getHeight() - getTotalPaddingTop() - getTotalPaddingBottom());
        return Math.max(0, layout.getHeight() - viewport);
    }

    private int scaleVelocity(int velocity) {
        long scaled = (long) velocity * (long) flingSpeedPercent / 100L;
        if (scaled > Integer.MAX_VALUE) return Integer.MAX_VALUE;
        if (scaled < Integer.MIN_VALUE) return Integer.MIN_VALUE;
        return (int) scaled;
    }

    private void recycleVelocityTracker() {
        if (velocityTracker != null) {
            velocityTracker.recycle();
            velocityTracker = null;
        }
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }
}
