package app.mdreader.mobile;

import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.drawable.Drawable;

/** Consistent 24-unit line icons. No emoji/font-dependent navigation glyphs. */
final class UiIcon extends Drawable {
    enum Kind { HOME, FOLDER, FILE, SAVE, SEARCH, MORE, CLOSE, UP, DOWN, REPLACE, REPLACE_ALL,
        OUTLINE, BOOKMARK, STAR, SETTINGS, PLUS, EDIT, EYE, SHARE, DOWNLOAD, MOON, SUN,
        CODE, TEXT, SPEAKER, BACK, LINK, IMAGE, CHECK, BOX, LIST, TABLE, ERASE, INFO, KEYBOARD }
    private final Kind kind;
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final int size;

    UiIcon(Kind kind, int color, int size) {
        this.kind = kind; this.size = size;
        paint.setColor(color); paint.setStrokeWidth(1.8f);
        paint.setStyle(Paint.Style.STROKE); paint.setStrokeCap(Paint.Cap.ROUND);
        paint.setStrokeJoin(Paint.Join.ROUND);
        setBounds(0, 0, size, size);
    }
    @Override public int getIntrinsicWidth() { return size; }
    @Override public int getIntrinsicHeight() { return size; }
    @Override public void setAlpha(int alpha) { paint.setAlpha(alpha); invalidateSelf(); }
    @Override public void setColorFilter(ColorFilter filter) { paint.setColorFilter(filter); invalidateSelf(); }
    @Override public int getOpacity() { return PixelFormat.TRANSLUCENT; }
    @Override public void setTint(int color) { paint.setColor(color); invalidateSelf(); }

    private void line(Canvas c, float... points) {
        Path p = new Path(); p.moveTo(points[0], points[1]);
        for (int i=2;i<points.length;i+=2) p.lineTo(points[i],points[i+1]);
        c.drawPath(p, paint);
    }
    private void dot(Canvas c, float x, float y) {
        paint.setStyle(Paint.Style.FILL); c.drawCircle(x,y,1.5f,paint); paint.setStyle(Paint.Style.STROKE);
    }
    @Override public void draw(Canvas c) {
        c.save(); c.translate(getBounds().left,getBounds().top);
        c.scale(getBounds().width()/24f,getBounds().height()/24f);
        switch (kind) {
            case HOME: line(c,3,10,12,3,21,10); line(c,5,9,5,21,10,21,10,14,14,14,14,21,19,21,19,9); break;
            case FOLDER: line(c,3,7,3,5,9,5,11,7,21,7,21,20,3,20,3,7); break;
            case FILE: line(c,6,3,15,3,20,8,20,21,6,21,6,3); line(c,15,3,15,8,20,8); line(c,9,12,16,12); line(c,9,16,16,16); break;
            case SAVE: line(c,4,3,17,3,21,7,21,21,3,21,3,3,4,3); line(c,7,3,7,9,16,9,16,3); line(c,7,21,7,14,17,14,17,21); break;
            case SEARCH: c.drawCircle(10.5f,10.5f,6.5f,paint); line(c,15.5f,15.5f,21,21); break;
            case MORE: dot(c,12,5); dot(c,12,12); dot(c,12,19); break;
            case CLOSE: line(c,6,6,18,18); line(c,6,18,18,6); break;
            case UP: line(c,5,14,12,7,19,14); break;
            case DOWN: line(c,5,10,12,17,19,10); break;
            case BACK: line(c,9,5,16,12,9,19); break;
            case REPLACE: line(c,4,7,20,7,16,3); line(c,20,17,4,17,8,21); break;
            case REPLACE_ALL: line(c,3,6,19,6,15,2); line(c,21,18,5,18,9,22); line(c,7,10,7,14); line(c,12,10,12,14); line(c,17,10,17,14); break;
            case OUTLINE: line(c,8,5,21,5); line(c,8,12,18,12); line(c,8,19,15,19); dot(c,3,5); dot(c,3,12); dot(c,3,19); break;
            case BOOKMARK: line(c,6,3,18,3,18,21,12,17,6,21,6,3); break;
            case STAR: line(c,12,2,15,8.5f,22,9.5f,17,14.5f,18,22,12,18.5f,6,22,7,14.5f,2,9.5f,9,8.5f,12,2); break;
            case SETTINGS: c.drawCircle(12,12,3.3f,paint); c.drawCircle(12,12,8,paint); for(int i=0;i<8;i++){c.save();c.rotate(i*45,12,12);line(c,12,2,12,4);c.restore();} break;
            case PLUS: line(c,12,5,12,19); line(c,5,12,19,12); break;
            case EDIT: line(c,4,20,5,14,16,3,21,8,10,19,4,20); line(c,13,6,18,11); break;
            case EYE: { Path p=new Path();p.moveTo(2,12);p.cubicTo(7,3,17,3,22,12);p.cubicTo(17,21,7,21,2,12);c.drawPath(p,paint);c.drawCircle(12,12,3,paint);break; }
            case SHARE: c.drawCircle(18,4,3,paint);c.drawCircle(5,12,3,paint);c.drawCircle(18,20,3,paint);line(c,8,10.5f,15,6);line(c,8,13.5f,15,18);break;
            case DOWNLOAD: line(c,12,3,12,15);line(c,7,10,12,15,17,10);line(c,4,16,4,21,20,21,20,16);break;
            case MOON: { Path p=new Path();p.moveTo(19,15);p.cubicTo(8,19,5,8,10,3);p.cubicTo(-2,6,1,22,14,21);p.cubicTo(17,20,19,18,19,15);c.drawPath(p,paint);break; }
            case SUN: c.drawCircle(12,12,4,paint);for(int i=0;i<8;i++){c.save();c.rotate(i*45,12,12);line(c,12,2,12,4);c.restore();}break;
            case CODE: line(c,7,6,2,12,7,18);line(c,17,6,22,12,17,18);line(c,14,3,10,21);break;
            case TEXT: line(c,3,5,17,5);line(c,10,5,10,21);line(c,13,11,23,11);line(c,18,11,18,21);break;
            case SPEAKER: line(c,3,9,7,9,12,4,12,20,7,15,3,15,3,9);c.drawArc(9,6,21,18,-60,120,false,paint);c.drawArc(5,2,25,22,-55,110,false,paint);break;
            case LINK: c.save();c.rotate(-40,12,12);c.drawRoundRect(2,8,14,16,4,4,paint);c.drawRoundRect(10,8,22,16,4,4,paint);c.restore();break;
            case IMAGE: c.drawRoundRect(3,3,21,21,2,2,paint);c.drawCircle(8,8,1.5f,paint);line(c,3,18,10,11,14,15,17,12,21,16);break;
            case CHECK: c.drawRoundRect(3,3,21,21,4,4,paint);line(c,7,12,10,15,17,8);break;
            case BOX: c.drawRoundRect(3,3,21,21,4,4,paint);break;
            case LIST: line(c,8,5,21,5);line(c,8,12,21,12);line(c,8,19,21,19);dot(c,3,5);dot(c,3,12);dot(c,3,19);break;
            case TABLE: c.drawRoundRect(3,3,21,21,2,2,paint);line(c,3,9,21,9);line(c,3,15,21,15);line(c,10,3,10,21);break;
            case ERASE: line(c,3,15,13,3,22,11,14,21,9,21,3,15);line(c,7,10,17,18);line(c,13,21,22,21);break;
            case INFO: c.drawCircle(12,12,9,paint);dot(c,12,7);line(c,12,11,12,17);break;
            case KEYBOARD: c.drawRoundRect(2,3,22,16,2,2,paint);line(c,7,8,7,9);line(c,12,8,12,9);line(c,17,8,17,9);line(c,8,13,16,13);line(c,9,20,12,23,15,20);break;
        }
        c.restore();
    }
}
