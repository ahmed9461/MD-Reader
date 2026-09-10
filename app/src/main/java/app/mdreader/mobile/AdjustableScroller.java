package app.mdreader.mobile;

import android.content.Context;
import android.widget.Scroller;

/**
 * Keeps TextView/EditText's native scrolling implementation while allowing the
 * user to scale fling velocity. Direct finger dragging and text selection remain
 * untouched, which avoids fighting Android's cursor/selection gesture handling.
 */
final class AdjustableScroller extends Scroller {
    private volatile int speedPercent;

    AdjustableScroller(Context context, int speedPercent) {
        super(context);
        setSpeedPercent(speedPercent);
    }

    void setSpeedPercent(int percent) {
        speedPercent = Math.max(10, Math.min(500, percent));
    }

    int getSpeedPercent() {
        return speedPercent;
    }

    private int scaleVelocity(int velocity) {
        long scaled = (long) velocity * (long) speedPercent / 100L;
        if (scaled > Integer.MAX_VALUE) return Integer.MAX_VALUE;
        if (scaled < Integer.MIN_VALUE) return Integer.MIN_VALUE;
        return (int) scaled;
    }

    @Override
    public void fling(int startX, int startY, int velocityX, int velocityY,
                      int minX, int maxX, int minY, int maxY) {
        super.fling(startX, startY, scaleVelocity(velocityX), scaleVelocity(velocityY),
                minX, maxX, minY, maxY);
    }

    @Override
    public void fling(int startX, int startY, int velocityX, int velocityY,
                      int minX, int maxX, int minY, int maxY, int overX, int overY) {
        super.fling(startX, startY, scaleVelocity(velocityX), scaleVelocity(velocityY),
                minX, maxX, minY, maxY, overX, overY);
    }
}
