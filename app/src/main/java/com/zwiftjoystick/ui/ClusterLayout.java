package com.zwiftjoystick.ui;

import android.content.Context;
import android.view.View;
import android.view.ViewGroup;

/**
 * 四键十字布局：子 View 依次为 上、左、右、下。
 * keyRatio：按键直径占可用正方形边长的比例；spread：按键中心到十字中心的距离（以按键直径为单位）。
 * spread 需 > 0.71，否则斜向相邻的两键会重叠。
 */
public class ClusterLayout extends ViewGroup {

    private final float keyRatio;
    private final float spread;

    public ClusterLayout(Context ctx, float keyRatio, float spread) {
        super(ctx);
        this.keyRatio = keyRatio;
        this.spread = spread;
    }

    private int keySize(int w, int h) {
        // 保证整个十字（直径 × (1 + 2·spread)）放得进可用空间
        int side = Math.min(w, h);
        return (int) Math.min(side * keyRatio, side / (1f + 2f * spread));
    }

    @Override
    protected void onMeasure(int wSpec, int hSpec) {
        int w = MeasureSpec.getSize(wSpec), h = MeasureSpec.getSize(hSpec);
        int spec = MeasureSpec.makeMeasureSpec(keySize(w, h), MeasureSpec.EXACTLY);
        for (int i = 0; i < getChildCount(); i++) getChildAt(i).measure(spec, spec);
        setMeasuredDimension(w, h);
    }

    @Override
    protected void onLayout(boolean changed, int l, int t, int r, int b) {
        int w = r - l, h = b - t;
        int key = keySize(w, h);
        int cx = w / 2, cy = h / 2, off = Math.round(key * spread);
        int[][] pos = {{cx, cy - off}, {cx - off, cy}, {cx + off, cy}, {cx, cy + off}};
        for (int i = 0; i < getChildCount() && i < 4; i++) {
            View v = getChildAt(i);
            v.layout(pos[i][0] - key / 2, pos[i][1] - key / 2, pos[i][0] + key / 2, pos[i][1] + key / 2);
        }
    }
}
