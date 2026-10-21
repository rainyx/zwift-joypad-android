package com.zwiftjoystick.ui;

import android.graphics.Bitmap;
import android.graphics.BitmapShader;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Shader;

import java.util.Random;

/** 磨砂质感：平铺的细噪点纹理 + 打孔点阵。所有控件共用一张噪点图。 */
final class Grain {
    private Grain() {}

    private static Bitmap noise;

    /** 平铺噪点着色器；配合 Paint alpha 控制颗粒强度。 */
    static BitmapShader shader() {
        if (noise == null) {
            int n = 96;
            int[] px = new int[n * n];
            Random r = new Random(7);
            for (int i = 0; i < px.length; i++) {
                // 明暗颗粒各半，透明度随机，叠在任意底色上都能显出磨砂感
                int a = 30 + r.nextInt(90);
                px[i] = r.nextBoolean() ? Color.argb(a, 255, 255, 255) : Color.argb(a, 0, 0, 0);
            }
            noise = Bitmap.createBitmap(px, n, n, Bitmap.Config.ARGB_8888);
        }
        return new BitmapShader(noise, Shader.TileMode.REPEAT, Shader.TileMode.REPEAT);
    }

    /** 磨砂层画笔（alpha 0..255 控制颗粒强度）。 */
    static Paint paint(int alpha) {
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setShader(shader());
        p.setAlpha(alpha);
        return p;
    }

    /**
     * 打孔点：光从上方来，孔的下缘反光、上缘处于阴影里。
     * strength 0..1 控制整体深浅（按下时可调淡）。
     */
    static void hole(Canvas c, float x, float y, float r, float strength, Paint scratch) {
        scratch.setShader(null);
        scratch.setStyle(Paint.Style.FILL);
        // 下缘反光
        scratch.setColor(Color.argb((int) (55 * strength), 255, 255, 255));
        c.drawCircle(x, y + r * 0.45f, r, scratch);
        // 孔洞
        scratch.setColor(Color.argb((int) (235 * strength), 4, 4, 5));
        c.drawCircle(x, y, r, scratch);
        // 孔内上缘更深的内阴影
        scratch.setColor(Color.argb((int) (150 * strength), 0, 0, 0));
        c.drawCircle(x, y - r * 0.25f, r * 0.75f, scratch);
    }
}
