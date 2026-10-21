package com.zwiftjoystick.ui;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.RadialGradient;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.os.Build;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.View;

/**
 * Zwift Click 风格按键：
 * STYLE_BALL —— 深色圆座里的彩色磨砂平面圆键 + 白色粗体字母（Y/Z/A/B）；按下时变暗、略缩。
 * STYLE_CHIN —— 机身下巴上的 −/＋ 键：打孔点阵（带内阴影）+ 白色符号；按下时变暗、符号发光。
 */
public class PadButton extends View {

    public interface OnPress {
        void onPress(boolean pressed);
    }

    public static final int STYLE_BALL = 0, STYLE_CHIN = 1;

    private final Paint socket = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint ball = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint spec = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint text = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint dots = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint sign = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint dim = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint edge = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint grain = Grain.paint(55);
    private final RectF rect = new RectF();
    private final String label;
    private final int color;
    private int style = STYLE_BALL;
    private int glow = Color.CYAN;
    private boolean down;
    private OnPress listener;

    public PadButton(Context ctx, String label, int color) {
        super(ctx);
        this.label = label;
        this.color = color;
        text.setTextAlign(Paint.Align.CENTER);
        text.setTypeface(Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD));
        text.setColor(Color.WHITE);
        sign.setStyle(Paint.Style.STROKE);
        edge.setStyle(Paint.Style.STROKE);
        sign.setStrokeCap(Paint.Cap.ROUND);
        if (Build.VERSION.SDK_INT < 28) setLayerType(LAYER_TYPE_SOFTWARE, null);
    }

    public PadButton setStyle(int s) {
        style = s;
        return this;
    }

    /** STYLE_CHIN 按下时符号的发光色。 */
    public PadButton setGlow(int c) {
        glow = c;
        return this;
    }

    public void setOnPress(OnPress l) {
        listener = l;
    }

    @Override
    protected void onDraw(Canvas c) {
        if (style == STYLE_CHIN) drawChin(c);
        else drawBall(c);
    }

    private void drawBall(Canvas c) {
        float w = getWidth(), h = getHeight(), cx = w / 2f, cy = h / 2f;
        float R = Math.min(w, h) / 2f * 0.96f;

        // 圆座：深色凹坑，上缘有一道内阴影
        socket.setShader(null);
        socket.setColor(Color.rgb(12, 12, 13));
        c.drawCircle(cx, cy, R, socket);
        socket.setColor(Color.argb(140, 0, 0, 0));
        c.drawCircle(cx, cy - R * 0.06f, R * 0.94f, socket);

        // 键面：纯色平涂（无高光、无渐变，看起来是平的）+ 磨砂颗粒 + 细暗边
        float r = R * (down ? 0.80f : 0.83f);
        ball.setShader(null);
        ball.clearShadowLayer();
        ball.setColor(down ? darken(color, 0.22f) : color);
        c.drawCircle(cx, cy, r, ball);
        grain.setAlpha(down ? 40 : 55);
        c.drawCircle(cx, cy, r, grain);
        edge.setStrokeWidth(Math.max(1f, R * 0.035f));
        edge.setColor(darken(color, 0.35f));
        c.drawCircle(cx, cy, r - edge.getStrokeWidth() / 2f, edge);

        text.setTextSize(r * 0.95f);
        text.clearShadowLayer();
        text.setColor(down ? Color.argb(200, 255, 255, 255) : Color.WHITE);
        c.drawText(label, cx, cy - (text.descent() + text.ascent()) / 2f, text);
    }

    private void drawChin(Canvas c) {
        float w = getWidth(), h = getHeight(), cx = w / 2f, cy = h / 2f;
        float m = Math.min(w, h);
        rect.set(0, 0, w, h);
        if (down) {
            dim.setColor(Color.argb(90, 0, 0, 0));
            c.drawRoundRect(rect, m * 0.35f, m * 0.35f, dim);
        }
        // 打孔点阵（避开中间符号）：每个点都有内阴影和下缘反光
        float step = m * 0.12f, dr = m * 0.028f;
        for (float y = step * 0.7f; y < h - step * 0.4f; y += step) {
            for (float x = step * 0.6f; x < w - step * 0.4f; x += step) {
                if (Math.abs(x - cx) < m * 0.30f && Math.abs(y - cy) < m * 0.24f) continue;
                Grain.hole(c, x, y, dr, down ? 0.7f : 1f, dots);
            }
        }
        // − / ＋
        float a = m * 0.17f;
        sign.setStrokeWidth(m * 0.075f);
        sign.setColor(down ? glow : Color.argb(235, 255, 255, 255));
        sign.setShadowLayer(down ? m * 0.12f : 0, 0, 0, glow);
        c.drawLine(cx - a, cy, cx + a, cy, sign);
        if ("+".equals(label)) c.drawLine(cx, cy - a, cx, cy + a, sign);
    }

    @Override
    public boolean onTouchEvent(MotionEvent e) {
        switch (e.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                set(true);
                performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
                return true;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                set(false);
                return true;
            default:
                return true;
        }
    }

    private void set(boolean d) {
        if (down == d) return;
        down = d;
        invalidate();
        if (listener != null) listener.onPress(d);
    }

    static int withAlpha(int c, int a) {
        return Color.argb(a, Color.red(c), Color.green(c), Color.blue(c));
    }

    static int lighten(int c, float f) {
        return Color.rgb(Color.red(c) + (int) ((255 - Color.red(c)) * f),
                Color.green(c) + (int) ((255 - Color.green(c)) * f),
                Color.blue(c) + (int) ((255 - Color.blue(c)) * f));
    }

    static int darken(int c, float f) {
        return Color.rgb((int) (Color.red(c) * (1 - f)), (int) (Color.green(c) * (1 - f)), (int) (Color.blue(c) * (1 - f)));
    }
}
