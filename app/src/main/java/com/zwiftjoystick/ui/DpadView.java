package com.zwiftjoystick.ui;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Shader;
import android.os.Build;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.View;

/**
 * Zwift Click 风格的一体式方向摇板：倒圆角菱形 + 细白 V 形箭头 + 两侧点阵纹理。
 * 按手指相对中心的方向判定 上/左/右/下；按下时摇板朝该方向微倾，对应箭头变青色。
 */
public class DpadView extends View {

    public interface OnDir {
        void onDir(int dir, boolean pressed);
    }

    public static final int UP = 0, LEFT = 1, RIGHT = 2, DOWN = 3;
    private static final float[][] VEC = {{0, -1}, {-1, 0}, {1, 0}, {0, 1}};

    private final Paint pad = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint rim = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint shade = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint chev = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint dots = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path diamond = new Path();
    private final Path tmp = new Path();
    private final Matrix m = new Matrix();
    private final int accent;
    private int pressed = -1;
    private int pointerId = -1;
    private OnDir listener;

    public DpadView(Context ctx, int accent) {
        super(ctx);
        this.accent = accent;
        rim.setStyle(Paint.Style.STROKE);
        chev.setStyle(Paint.Style.STROKE);
        chev.setStrokeCap(Paint.Cap.ROUND);
        chev.setStrokeJoin(Paint.Join.ROUND);
        if (Build.VERSION.SDK_INT < 28) setLayerType(LAYER_TYPE_SOFTWARE, null);
    }

    public void setOnDir(OnDir l) {
        listener = l;
    }

    private float radius() {
        return Math.min(getWidth(), getHeight()) / 2f * 0.92f;
    }

    @Override
    protected void onDraw(Canvas c) {
        float cx = getWidth() / 2f, cy = getHeight() / 2f, R = radius();
        float tx = 0, ty = 0;
        if (pressed >= 0) {
            tx = VEC[pressed][0] * R * 0.03f;
            ty = VEC[pressed][1] * R * 0.03f;
        }
        // 菱形 = 旋转 45° 的圆角正方形（半对角线 = R）
        float s = R * 1.4142f;
        diamond.reset();
        diamond.addRoundRect(new RectF(-s / 2, -s / 2, s / 2, s / 2), s * 0.2f, s * 0.2f, Path.Direction.CW);
        m.reset();
        m.setRotate(45);
        m.postTranslate(cx + tx, cy + ty);
        diamond.transform(m);

        // 抬起的摇板：投影 + 上亮下暗
        pad.setShader(new LinearGradient(0, cy - R, 0, cy + R,
                Color.rgb(48, 50, 55), Color.rgb(19, 20, 22), Shader.TileMode.CLAMP));
        pad.setShadowLayer(R * 0.08f, 0, R * 0.04f, Color.argb(180, 0, 0, 0));
        c.drawPath(diamond, pad);
        rim.setStrokeWidth(R * 0.025f);
        rim.setShader(new LinearGradient(0, cy - R, 0, cy + R,
                Color.argb(60, 255, 255, 255), Color.argb(0, 255, 255, 255), Shader.TileMode.CLAMP));
        c.drawPath(diamond, rim);

        c.save();
        c.clipPath(diamond);
        // 中间一条稍亮的横向带（参考图中摇板表面的反光）
        shade.setShader(new LinearGradient(0, cy - R * 0.25f, 0, cy + R * 0.25f,
                new int[]{Color.argb(0, 255, 255, 255), Color.argb(14, 255, 255, 255), Color.argb(0, 255, 255, 255)},
                null, Shader.TileMode.CLAMP));
        c.drawRect(cx - R, cy - R * 0.25f, cx + R, cy + R * 0.25f, shade);
        // 按下的那一侧加一层高光
        if (pressed >= 0) {
            tmp.reset();
            tmp.moveTo(cx + tx, cy + ty);
            float[] v = VEC[pressed];
            float px = -v[1], py = v[0];
            tmp.lineTo(cx + tx + (v[0] + px) * R, cy + ty + (v[1] + py) * R);
            tmp.lineTo(cx + tx + (v[0] - px) * R, cy + ty + (v[1] - py) * R);
            tmp.close();
            shade.setShader(null);
            shade.setColor(Color.argb(22, 255, 255, 255));
            c.drawPath(tmp, shade);
        }
        c.restore();

        // 左右两侧的打孔点阵
        float step = R * 0.05f, dr = R * 0.014f;
        for (int side = -1; side <= 1; side += 2) {
            float ox = cx + tx + side * R * 0.62f, oy = cy + ty;
            for (int i = -4; i <= 4; i++) {
                for (int j = -4; j <= 4; j++) {
                    float d = (float) Math.hypot(i, j) * step;
                    if (d < R * 0.10f || d > R * 0.2f) continue;
                    Grain.hole(c, ox + i * step, oy + j * step, dr, 0.9f, dots);
                }
            }
        }

        // 四个 V 形箭头
        float a = R * 0.11f;
        chev.setStrokeWidth(R * 0.038f);
        for (int d = 0; d < 4; d++) {
            float[] v = VEC[d];
            float px = -v[1], py = v[0];
            float ox = cx + tx + v[0] * R * 0.64f, oy = cy + ty + v[1] * R * 0.64f;
            chev.setColor(d == pressed ? accent : Color.argb(225, 255, 255, 255));
            chev.setShadowLayer(d == pressed ? R * 0.06f : 0, 0, 0, accent);
            tmp.reset();
            tmp.moveTo(ox - v[0] * a * 0.5f + px * a, oy - v[1] * a * 0.5f + py * a);
            tmp.lineTo(ox + v[0] * a * 0.5f, oy + v[1] * a * 0.5f);
            tmp.lineTo(ox - v[0] * a * 0.5f - px * a, oy - v[1] * a * 0.5f - py * a);
            c.drawPath(tmp, chev);
        }
    }

    @Override
    public boolean onTouchEvent(MotionEvent e) {
        switch (e.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                pointerId = e.getPointerId(0);
                track(e.getX(0), e.getY(0));
                return true;
            case MotionEvent.ACTION_MOVE: {
                int i = e.findPointerIndex(pointerId);
                if (i >= 0) track(e.getX(i), e.getY(i));
                return true;
            }
            case MotionEvent.ACTION_POINTER_UP:
                if (e.getPointerId(e.getActionIndex()) == pointerId) release();
                return true;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                release();
                return true;
            default:
                return true;
        }
    }

    private void track(float x, float y) {
        float dx = x - getWidth() / 2f, dy = y - getHeight() / 2f;
        int d;
        if (Math.hypot(dx, dy) < radius() * 0.15f) d = -1;   // 中心死区
        else if (Math.abs(dx) > Math.abs(dy)) d = dx < 0 ? LEFT : RIGHT;
        else d = dy < 0 ? UP : DOWN;
        if (d == pressed) return;
        if (pressed >= 0 && listener != null) listener.onDir(pressed, false);
        pressed = d;
        if (d >= 0) {
            performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
            if (listener != null) listener.onDir(d, true);
        }
        invalidate();
    }

    private void release() {
        if (pressed >= 0 && listener != null) listener.onDir(pressed, false);
        pressed = -1;
        pointerId = -1;
        invalidate();
    }

    /** 说明层锚点：各方向箭头在本 View 内的相对位置。 */
    public static float[] anchor(int dir) {
        float k = 0.46f * 0.64f;   // 0.46 ≈ radius() / min(w,h)
        return new float[]{0.5f + VEC[dir][0] * k, 0.5f + VEC[dir][1] * k};
    }
}
