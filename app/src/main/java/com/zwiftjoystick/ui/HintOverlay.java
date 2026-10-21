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
        final String title;   // 按钮名（设置里的键码名，如 LS1、ZL），可为 null
        final String text;
        final int color;
        final float fx, fy;   // 锚点在目标 View 内的相对位置

        Hint(View target, float fx, float fy, String title, String text, int color) {
            this.target = target;
            this.title = title;
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
    private final Paint titleP = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final int[] me = new int[2], it = new int[2];
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
        titleP.setTextSize(10.5f * getResources().getDisplayMetrics().scaledDensity);
        titleP.setTextAlign(Paint.Align.CENTER);
        titleP.setFakeBoldText(true);
    }

    public void clear() {
        hints.clear();
    }

    /** title 显示在功能说明上方（按钮名），label 为功能说明。 */
    public void add(View target, String title, String label, int color) {
        add(target, 0.5f, 0.5f, title, label, color);
    }

    /** fx / fy：标签中心在目标 View 内的相对位置（0..1），用于一个 View 内有多个按键（如方向摇板）。 */
    public void add(View target, float fx, float fy, String title, String label, int color) {
        hints.add(new Hint(target, fx, fy, title, label, color));
    }

    public boolean isShowing() {
        return getVisibility() == VISIBLE && getAlpha() > 0f;
    }

    public void show() {
        setVisibility(VISIBLE);
        invalidate();
        animate().alpha(1f).setDuration(160).withEndAction(null).start();
    }

    public void hide() {
        animate().alpha(0f).setDuration(200).withEndAction(() -> setVisibility(GONE)).start();
    }

    /** 按空格折行，每行不超过 maxW（单个词超长时独占一行）；中文说明较短，通常不折行。 */
    private List<String> wrap(String s, float maxW) {
        List<String> out = new ArrayList<>();
        StringBuilder line = new StringBuilder();
        for (String w : s.split(" ")) {
            String next = line.length() == 0 ? w : line + " " + w;
            if (line.length() > 0 && text.measureText(next) > maxW) {
                out.add(line.toString());
                line.setLength(0);
                line.append(w);
            } else {
                line.setLength(0);
                line.append(next);
            }
        }
        if (line.length() > 0) out.add(line.toString());
        return out;
    }

    @Override
    protected void onDraw(Canvas c) {
        c.drawRect(0, 0, getWidth(), getHeight(), dim);
        getLocationInWindow(me);
        float padX = 10 * density, padY = 6 * density, margin = 4 * density;
        Paint.FontMetrics fm = text.getFontMetrics(), tfm = titleP.getFontMetrics();
        float th = fm.descent - fm.ascent, tth = tfm.descent - tfm.ascent;
        float maxLine = 120 * density;

        // 先算出所有标签的位置，再把互相重叠的推开，最后绘制
        List<Hint> shown = new ArrayList<>();
        List<List<String>> texts = new ArrayList<>();
        List<RectF> rects = new ArrayList<>();
        for (Hint h : hints) {
            if (h.target.getVisibility() != VISIBLE || h.target.getWidth() == 0) continue;
            h.target.getLocationInWindow(it);
            float cx = it[0] - me[0] + h.target.getWidth() * h.fx;
            float cy = it[1] - me[1] + h.target.getHeight() * h.fy;
            boolean hasTitle = h.title != null && !h.title.isEmpty();
            List<String> lines = wrap(h.text, maxLine);
            float tw = hasTitle ? titleP.measureText(h.title) : 0;
            for (String l : lines) tw = Math.max(tw, text.measureText(l));
            float pw = tw + padX * 2;
            float ph = th * lines.size() + padY * 2 + (hasTitle ? tth : 0);
            shown.add(h);
            texts.add(lines);
            rects.add(new RectF(cx - pw / 2f, cy - ph / 2f, cx + pw / 2f, cy + ph / 2f));
        }
        separate(rects, 3 * density, margin);

        for (int i = 0; i < shown.size(); i++) {
            Hint h = shown.get(i);
            RectF r = rects.get(i);
            float rad = Math.min(r.height() / 2, 14 * density);
            border.setColor(PadButton.withAlpha(h.color, 220));
            c.drawRoundRect(r, rad, rad, pill);
            c.drawRoundRect(r, rad, rad, border);
            float y = r.top + padY;
            if (h.title != null && !h.title.isEmpty()) {
                titleP.setColor(PadButton.lighten(h.color, 0.25f));
                c.drawText(h.title, r.centerX(), y - tfm.ascent, titleP);
                y += tth;
            }
            for (String l : texts.get(i)) {
                c.drawText(l, r.centerX(), y - fm.ascent, text);
                y += th;
            }
        }
    }

    /** 迭代推开重叠的标签：每对沿穿透较浅的方向各退一半，并限制在屏幕内。 */
    private void separate(List<RectF> rs, float gap, float margin) {
        for (int iter = 0; iter < 40; iter++) {
            boolean moved = false;
            for (int i = 0; i < rs.size(); i++) {
                for (int j = i + 1; j < rs.size(); j++) {
                    RectF a = rs.get(i), b = rs.get(j);
                    float ox = Math.min(a.right, b.right) - Math.max(a.left, b.left) + gap;
                    float oy = Math.min(a.bottom, b.bottom) - Math.max(a.top, b.top) + gap;
                    if (ox <= 0 || oy <= 0) continue;
                    moved = true;
                    if (ox < oy) {
                        float d = ox / 2 * (a.centerX() <= b.centerX() ? 1 : -1);
                        a.offset(-d, 0);
                        b.offset(d, 0);
                    } else {
                        float d = oy / 2 * (a.centerY() <= b.centerY() ? 1 : -1);
                        a.offset(0, -d);
                        b.offset(0, d);
                    }
                }
            }
            for (RectF r : rs) {
                r.offset(Math.max(0, margin - r.left) - Math.max(0, r.right - (getWidth() - margin)),
                        Math.max(0, margin - r.top) - Math.max(0, r.bottom - (getHeight() - margin)));
            }
            if (!moved) break;
        }
    }
}
