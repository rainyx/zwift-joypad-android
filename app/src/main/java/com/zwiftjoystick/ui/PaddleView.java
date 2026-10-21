package com.zwiftjoystick.ui;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.RadialGradient;
import android.graphics.RectF;
import android.graphics.Shader;
import android.os.Build;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.View;
import android.view.animation.OvershootInterpolator;

/** 横向拨片（ZL / ZR），Zwift Click 风格：黑色磨砂轨道 + 刻度 + 强调色进度 + 深灰磨砂平面旋钮（略浅于轨道）；左右拖动输出 -100..100，松手弹回中位。 */
public class PaddleView extends View {

    public interface OnValue {
        void onValue(int value);
    }

    private final Paint groove = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint edge = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint tick = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint knob = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint knobRing = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint text = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint grain = Grain.paint(45);
    private final RectF rect = new RectF();
    private final String label;
    private final int color;
    private int value;
    private boolean dragging;
    private OnValue listener;
    private ValueAnimator back;
    private int shadowColor = Color.argb(110, 60, 20, 0);

    public PaddleView(Context ctx, String label, int color) {
        super(ctx);
        this.label = label;
        this.color = color;
        edge.setStyle(Paint.Style.STROKE);
        knobRing.setStyle(Paint.Style.STROKE);
        tick.setStrokeCap(Paint.Cap.ROUND);
        text.setColor(Color.WHITE);
        text.setFakeBoldText(true);
        text.setTextAlign(Paint.Align.CENTER);
        if (Build.VERSION.SDK_INT < 28) setLayerType(LAYER_TYPE_SOFTWARE, null);
    }

    /** 轨道上下留白（占高度比例），给放大的旋钮和投影留空间 */
    private static final float PAD = 0.22f;
    /** 旋钮半径 / 轨道半径 */
    private static final float KNOB = 1.4f;

    /** 旋钮中心可移动的半程：让旋钮到端点时边缘与轨道末端对齐 */
    private static float travel(float w, float h) {
        float pad = h * PAD, r = (h - 2 * pad) / 2f;
        return (w - 2 * pad) / 2f - r * KNOB;
    }

    /** 轨道落在背景上的投影颜色（随背景主题变化）。 */
    public void setShadowColor(int c) {
        shadowColor = c;
        invalidate();
    }

    public void setOnValue(OnValue l) {
        listener = l;
    }

    @Override
    protected void onDraw(Canvas c) {
        float w = getWidth(), h = getHeight();
        float pad = h * PAD;
        rect.set(pad, pad, w - pad, h - pad);
        float r = rect.height() / 2f;
        float cy = h / 2f, cx = w / 2f;

        // 黑色哑光轨道，投影落在橙色背景上
        groove.setShader(new LinearGradient(0, rect.top, 0, rect.bottom,
                Color.rgb(42, 43, 46), Color.rgb(18, 19, 21), Shader.TileMode.CLAMP));
        groove.setShadowLayer(h * 0.10f, 0, h * 0.05f, shadowColor);
        c.drawRoundRect(rect, r, r, groove);
        grain.setAlpha(30);
        c.drawRoundRect(rect, r, r, grain);
        grain.setAlpha(45);
        edge.setStrokeWidth(Math.max(1f, h * 0.025f));
        edge.setColor(Color.argb(28, 255, 255, 255));
        c.drawRoundRect(rect, r, r, edge);

        // 刻度：中位长、两侧短
        float half = travel(w, h);
        tick.setStrokeWidth(Math.max(1.5f, h * 0.03f));
        for (int i = -4; i <= 4; i++) {
            float x = cx + half * i / 4f;
            float len = i == 0 ? rect.height() * 0.32f : rect.height() * 0.14f;
            tick.setColor(Color.argb(i == 0 ? 90 : 45, 255, 255, 255));
            c.drawLine(x, cy - len, x, cy + len, tick);
        }

        // 主题色进度
        float kx = cx + half * value / 100f;
        if (value != 0) {
            fill.setShader(new LinearGradient(cx, 0, kx, 0, PadButton.withAlpha(color, 40),
                    PadButton.withAlpha(color, 170), Shader.TileMode.CLAMP));
            float th = rect.height() * 0.22f;
            c.drawRoundRect(new RectF(Math.min(cx, kx), cy - th, Math.max(cx, kx), cy + th), th, th, fill);
        }

        // 浅灰磨砂平面旋钮（纯色 + 颗粒，无高光）；拖动时外圈亮起强调色
        // 旋钮直径 = 轨道高度 × 140%，比轨道略浅；下方一层偏下的投影落在轨道上，显得浮在轨道之上
        float kr = r * KNOB * (dragging ? 1.03f : 1f);
        knob.setShader(null);
        knob.setColor(Color.argb(1, 0, 0, 0));
        knob.setShadowLayer(h * 0.09f, 0, h * 0.06f, Color.argb(230, 0, 0, 0));
        c.drawCircle(kx, cy, kr * 0.96f, knob);                 // 投影
        knob.clearShadowLayer();
        knob.setColor(dragging ? Color.rgb(70, 72, 77) : Color.rgb(62, 64, 69));
        c.drawCircle(kx, cy, kr, knob);
        c.drawCircle(kx, cy, kr, grain);
        knobRing.setStrokeWidth(Math.max(1.5f, h * 0.035f));
        knobRing.setColor(dragging ? color : Color.argb(70, 0, 0, 0));
        knobRing.setShadowLayer(dragging ? h * 0.1f : 0, 0, 0, color);
        c.drawCircle(kx, cy, kr, knobRing);
        text.setColor(Color.rgb(200, 203, 208));
        text.setTextSize(kr * 0.62f);
        c.drawText(label, kx, cy - (text.descent() + text.ascent()) / 2f, text);
    }

    @Override
    public boolean onTouchEvent(MotionEvent e) {
        switch (e.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                if (back != null) back.cancel();
                dragging = true;
                getParent().requestDisallowInterceptTouchEvent(true);
                performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
                // fall through
            case MotionEvent.ACTION_MOVE: {
                float half = travel(getWidth(), getHeight());
                int v = Math.round((e.getX() - getWidth() / 2f) / half * 100f);
                set(Math.max(-100, Math.min(100, v)));
                return true;
            }
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                dragging = false;
                back = ValueAnimator.ofInt(value, 0).setDuration(180);
                back.setInterpolator(new OvershootInterpolator(1.2f));
                back.addUpdateListener(a -> set((Integer) a.getAnimatedValue()));
                back.start();
                invalidate();
                return true;
            default:
                return true;
        }
    }

    private void set(int v) {
        v = Math.max(-100, Math.min(100, v));
        if (v == value) return;
        value = v;
        invalidate();
        if (listener != null) listener.onValue(v);
    }
}
