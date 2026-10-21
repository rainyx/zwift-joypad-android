package com.zwiftjoystick.ui;

import android.animation.ArgbEvaluator;
import android.animation.ValueAnimator;
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
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.AccelerateDecelerateInterpolator;

/**
 * Zwift Click 风格的黑色哑光机身：圆润主体 + 四角凸耳 + 下巴 + 顶部 LED + 中间菱形凹槽。
 * 子 View：[0] 凹槽内的控件（DpadView 或 YABZ 的 ClusterLayout），[1] 下巴按键（−/＋）。
 */
public class ControllerBody extends ViewGroup {

    private final Paint shadow = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint body = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint nub = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint rim = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint well = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint led = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path shape = new Path();
    private final Path recess = new Path();
    private final RectF tmp = new RectF();
    private final Matrix m = new Matrix();
    private final int ledColor;
    private int shadowColor = Color.argb(120, 60, 20, 0);

    /** LED 状态：关闭（灰）/ 广播中（黄灰呼吸）/ 已连接（青蓝常亮）。 */
    public static final int LED_OFF = 0, LED_ADVERTISING = 1, LED_CONNECTED = 2;
    private static final int LED_GREY = Color.rgb(58, 61, 64), LED_YELLOW = Color.rgb(255, 196, 46);
    private int ledState = LED_OFF;
    private float breath;                       // 0..1，呼吸相位
    private ValueAnimator breathAnim;

    // 本次布局计算出的几何
    private float S, cx, cy;

    /** 简约模式：不画机身，只摆放按键（凹槽控件放大，下巴键移到内侧） */
    private boolean minimal;
    private boolean chinRight;
    private final RectF wellRect = new RectF(), chinRect = new RectF(), ledRect = new RectF();

    public ControllerBody(Context ctx, int ledColor) {
        super(ctx);
        this.ledColor = ledColor;
        setWillNotDraw(false);
        setClipChildren(false);
        rim.setStyle(Paint.Style.STROKE);
        well.setStyle(Paint.Style.FILL);
        if (Build.VERSION.SDK_INT < 28) setLayerType(LAYER_TYPE_SOFTWARE, null);
    }

    /** 机身落在背景上的投影颜色（随背景主题变化）。 */
    public void setShadowColor(int c) {
        shadowColor = c;
        invalidate();
    }

    /** 简约模式；chinRight 为下巴键（−/＋）放在凹槽控件的右侧，否则放左侧。 */
    public void setMinimal(boolean on, boolean chinRight) {
        minimal = on;
        this.chinRight = chinRight;
        requestLayout();
        invalidate();
    }

    public void setLedState(int state) {
        if (ledState == state) return;
        ledState = state;
        if (state == LED_ADVERTISING) startBreath();
        else stopBreath();
        invalidate();
    }

    private void startBreath() {
        if (breathAnim != null) return;
        breathAnim = ValueAnimator.ofFloat(0f, 1f).setDuration(1600);
        breathAnim.setRepeatCount(ValueAnimator.INFINITE);
        breathAnim.setRepeatMode(ValueAnimator.REVERSE);
        breathAnim.setInterpolator(new AccelerateDecelerateInterpolator());
        breathAnim.addUpdateListener(a -> {
            breath = (Float) a.getAnimatedValue();
            invalidate();
        });
        breathAnim.start();
    }

    private void stopBreath() {
        if (breathAnim != null) breathAnim.cancel();
        breathAnim = null;
        breath = 0f;
    }

    @Override
    protected void onDetachedFromWindow() {
        stopBreath();
        super.onDetachedFromWindow();
    }

    private void geometry(int w, int h) {
        S = Math.min(w * 0.95f, h / 1.0f);
        cx = w / 2f;
        cy = h / 2f - S * 0.05f;
        if (minimal) {
            // 凹槽控件 D×D，旁边是 0.30D×0.56D 的下巴键，间隔 0.08D，整体居中
            float D = Math.min(h * 0.94f, w * 0.96f / 1.38f);
            float cw = D * 0.30f, ch = D * 0.56f, gap = D * 0.08f;
            float left = (w - (D + gap + cw)) / 2f, top = (h - D) / 2f;
            float wellL = chinRight ? left : left + cw + gap;
            float chinL = chinRight ? left + D + gap : left;
            wellRect.set(wellL, top, wellL + D, top + D);
            chinRect.set(chinL, h / 2f - ch / 2f, chinL + cw, h / 2f + ch / 2f);
            // LED 居中放在下巴键上方
            float lw = Math.min(S * 0.075f, cw * 0.5f), lh = lw * 0.29f, ly = chinRect.top - D * 0.08f;
            ledRect.set(chinRect.centerX() - lw / 2, ly - lh / 2, chinRect.centerX() + lw / 2, ly + lh / 2);
        } else {
            float rr = recessR(), wy = cy - S * 0.03f;
            wellRect.set(cx - rr, wy - rr, cx + rr, wy + rr);
            float cw = S * 0.32f, ch = S * 0.22f, top = cy + S * 0.27f;
            chinRect.set(cx - cw / 2, top, cx + cw / 2, top + ch);
            float lw = S * 0.075f, lh = S * 0.022f, ly = cy - S * 0.345f;
            ledRect.set(cx - lw / 2, ly - lh / 2, cx + lw / 2, ly + lh / 2);
        }
    }

    /** 凹槽半对角线 */
    private float recessR() {
        return S * 0.30f;
    }

    @Override
    protected void onMeasure(int wSpec, int hSpec) {
        int w = MeasureSpec.getSize(wSpec), h = MeasureSpec.getSize(hSpec);
        geometry(w, h);
        if (getChildCount() > 0) getChildAt(0).measure(exact((int) wellRect.width()), exact((int) wellRect.height()));
        if (getChildCount() > 1) getChildAt(1).measure(exact((int) chinRect.width()), exact((int) chinRect.height()));
        setMeasuredDimension(w, h);
    }

    private static int exact(int v) {
        return MeasureSpec.makeMeasureSpec(Math.max(0, v), MeasureSpec.EXACTLY);
    }

    @Override
    protected void onLayout(boolean changed, int l, int t, int r, int b) {
        geometry(r - l, b - t);
        if (getChildCount() > 0) layout(getChildAt(0), wellRect);
        if (getChildCount() > 1) layout(getChildAt(1), chinRect);
        buildShape();
    }

    private static void layout(View v, RectF r) {
        v.layout((int) r.left, (int) r.top, (int) r.right, (int) r.bottom);
    }

    private void buildShape() {
        shape.reset();
        // 主体
        Path main = new Path();
        main.addRoundRect(new RectF(cx - S * 0.40f, cy - S * 0.38f, cx + S * 0.40f, cy + S * 0.36f),
                S * 0.30f, S * 0.30f, Path.Direction.CW);
        // 下巴
        Path chin = new Path();
        chin.addRoundRect(new RectF(cx - S * 0.18f, cy + S * 0.10f, cx + S * 0.18f, cy + S * 0.52f),
                S * 0.11f, S * 0.11f, Path.Direction.CW);
        shape.op(main, chin, Path.Op.UNION);

        float rr = recessR(), wy = cy - S * 0.03f;
        float s = rr * 1.4142f;
        recess.reset();
        recess.addRoundRect(new RectF(-s / 2, -s / 2, s / 2, s / 2), s * 0.2f, s * 0.2f, Path.Direction.CW);
        m.reset();
        m.setRotate(45);
        m.postTranslate(cx, wy);
        recess.transform(m);
    }

    private void drawNubs(Canvas c) {
        nub.setShader(new LinearGradient(0, cy - S * 0.45f, 0, cy + S * 0.45f,
                Color.rgb(38, 39, 42), Color.rgb(16, 17, 18), Shader.TileMode.CLAMP));
        float[][] pos = {{-0.355f, -0.285f, -45}, {0.355f, -0.285f, 45}, {-0.355f, 0.265f, 45}, {0.355f, 0.265f, -45}};
        for (float[] p : pos) {
            c.save();
            c.translate(cx + p[0] * S, cy + p[1] * S);
            c.rotate(p[2]);
            tmp.set(-S * 0.115f, -S * 0.075f, S * 0.115f, S * 0.075f);
            c.drawRoundRect(tmp, S * 0.03f, S * 0.03f, nub);
            c.restore();
        }
    }

    @Override
    protected void onDraw(Canvas c) {
        if (S <= 0) return;
        if (minimal) {
            // 简约模式不画机身，只保留 LED
            drawLed(c);
            return;
        }
        // 落在橙色背景上的柔和投影
        shadow.setColor(Color.argb(1, 0, 0, 0));
        shadow.setShadowLayer(S * 0.09f, 0, S * 0.05f, shadowColor);
        c.drawPath(shape, shadow);

        drawNubs(c);

        body.setShader(new LinearGradient(0, cy - S * 0.38f, 0, cy + S * 0.52f,
                Color.rgb(44, 45, 48), Color.rgb(20, 21, 23), Shader.TileMode.CLAMP));
        c.drawPath(shape, body);
        // 顶部边缘高光
        rim.setStrokeWidth(S * 0.008f);
        rim.setShader(new LinearGradient(0, cy - S * 0.38f, 0, cy, Color.argb(50, 255, 255, 255),
                Color.argb(0, 255, 255, 255), Shader.TileMode.CLAMP));
        c.drawPath(shape, rim);

        // 菱形凹槽：深色 + 内阴影 + 外缘高光
        well.setShader(new LinearGradient(0, cy - S * 0.33f, 0, cy + S * 0.27f,
                Color.rgb(8, 8, 9), Color.rgb(26, 27, 29), Shader.TileMode.CLAMP));
        c.drawPath(recess, well);
        rim.setStrokeWidth(S * 0.012f);
        rim.setShader(new LinearGradient(0, cy - S * 0.33f, 0, cy + S * 0.27f, Color.argb(120, 0, 0, 0),
                Color.argb(40, 255, 255, 255), Shader.TileMode.CLAMP));
        c.drawPath(recess, rim);

        drawLed(c);
    }

    /** LED：关闭灰 / 广播中黄灰呼吸 / 已连接青蓝常亮 */
    private void drawLed(Canvas c) {
        float lh = ledRect.height();
        switch (ledState) {
            case LED_CONNECTED:
                led.setColor(ledColor);
                led.setShadowLayer(S * 0.03f, 0, 0, ledColor);
                break;
            case LED_ADVERTISING: {
                int col = (int) new ArgbEvaluator().evaluate(breath, LED_GREY, LED_YELLOW);
                led.setColor(col);
                if (breath > 0.05f) led.setShadowLayer(S * 0.03f * breath, 0, 0, LED_YELLOW);
                else led.clearShadowLayer();
                break;
            }
            default:
                led.setColor(LED_GREY);
                led.clearShadowLayer();
                break;
        }
        c.drawRoundRect(ledRect, lh / 2, lh / 2, led);
    }
}
