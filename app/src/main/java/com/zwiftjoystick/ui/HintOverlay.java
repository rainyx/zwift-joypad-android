package com.zwiftjoystick.ui;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.view.View;

import java.util.ArrayList;
import java.util.List;

/**
 * 盖在手柄上方的说明层：在每个控件正中叠一个标签（"路口左转"、"转向 · 刹车"…）。
 * 控件之间互不重叠，所以标签也不会互相遮挡。
 * 自身不可点击，触摸直接落到下面的按键上，不改变原布局。
 */
public class HintOverlay extends View {

    private static final class Hint {
        final View target;
        final String text;
        final int color;
        final float fx, fy;   // 锚点在目标 View 内的相对位置

        Hint(View target, float fx, float fy, String text, int color) {
            this.target = target;
            this.fx = fx;
            this.fy = fy;
            this.text = text;
            this.color = color;
        }
    }

    private final List<Hint> hints = new ArrayList<>();
    private final Paint dim = new Paint();
    private final Paint pill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint border = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint text = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final int[] me = new int[2], it = new int[2];
    private final Runnable autoHide = this::hide;
    private final float density;

    public HintOverlay(Context ctx) {
        super(ctx);
        density = getResources().getDisplayMetrics().density;
        setClickable(false);
        setFocusable(false);
        setAlpha(0f);
        setVisibility(GONE);
        dim.setColor(Color.argb(110, 0, 0, 0));
        pill.setColor(Color.argb(235, 24, 26, 31));
        border.setStyle(Paint.Style.STROKE);
        border.setStrokeWidth(1.2f * density);
        text.setColor(Color.rgb(236, 239, 244));
        text.setTextSize(12.5f * getResources().getDisplayMetrics().scaledDensity);
        text.setTextAlign(Paint.Align.CENTER);
    }

    public void clear() {
        hints.clear();
    }

    public void add(View target, String label, int color) {
        add(target, 0.5f, 0.5f, label, color);
    }

    /** fx / fy：标签中心在目标 View 内的相对位置（0..1），用于一个 View 内有多个按键（如方向摇板）。 */
    public void add(View target, float fx, float fy, String label, int color) {
        hints.add(new Hint(target, fx, fy, label, color));
    }

    public boolean isShowing() {
        return getVisibility() == VISIBLE && getAlpha() > 0f;
    }

    public void toggle() {
        if (isShowing()) hide();
        else show();
    }

    public void show() {
        removeCallbacks(autoHide);
        setVisibility(VISIBLE);
        invalidate();
        animate().alpha(1f).setDuration(160).withEndAction(null).start();
        postDelayed(autoHide, 5000);
    }

    public void hide() {
        removeCallbacks(autoHide);
        animate().alpha(0f).setDuration(200).withEndAction(() -> setVisibility(GONE)).start();
    }

    @Override
    protected void onDraw(Canvas c) {
        c.drawRect(0, 0, getWidth(), getHeight(), dim);
        getLocationInWindow(me);
        float padX = 10 * density, padY = 6 * density, margin = 4 * density;
        Paint.FontMetrics fm = text.getFontMetrics();
        float th = fm.descent - fm.ascent;
        for (Hint h : hints) {
            if (h.target.getVisibility() != VISIBLE || h.target.getWidth() == 0) continue;
            h.target.getLocationInWindow(it);
            float cx = it[0] - me[0] + h.target.getWidth() * h.fx;
            float cy = it[1] - me[1] + h.target.getHeight() * h.fy;
            float pw = text.measureText(h.text) + padX * 2, ph = th + padY * 2;
            float x = Math.max(margin, Math.min(getWidth() - margin - pw, cx - pw / 2f));
            RectF r = new RectF(x, cy - ph / 2f, x + pw, cy + ph / 2f);
            border.setColor(PadButton.withAlpha(h.color, 220));
            c.drawRoundRect(r, ph / 2, ph / 2, pill);
            c.drawRoundRect(r, ph / 2, ph / 2, border);
            c.drawText(h.text, r.centerX(), r.top + padY - fm.ascent, text);
        }
    }
}
