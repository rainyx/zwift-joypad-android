package com.zwiftjoystick;

import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

/** 关于：软件名、版本号、作者、GitHub 地址、功能介绍、免责声明。 */
public class AboutActivity extends BaseActivity {

    private static final String APP_NAME = "Zwift Joypad";
    private static final String AUTHOR = "@rainyx";
    private static final String GITHUB = "https://github.com/rainyx/zwift-joypad-android";
    private static final int CARD = Color.rgb(30, 34, 40);
    private static final int TEXT2 = Color.rgb(160, 168, 180);

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setTitle(R.string.title_about);

        ScrollView scroll = new ScrollView(this);
        scroll.setBackgroundColor(MainActivity.BG);
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setPadding(dp(16), dp(16), dp(16), dp(24));
        scroll.addView(col);

        TextView name = text(APP_NAME, 26, Color.WHITE);
        name.setTypeface(Typeface.DEFAULT_BOLD);
        col.addView(name);
        TextView ver = text(getString(R.string.version_fmt, versionName()), 14, TEXT2);
        ver.setPadding(0, dp(2), 0, dp(16));
        col.addView(ver);

        LinearLayout info = card(col);
        row(info, getString(R.string.author), text(AUTHOR, 15, Color.WHITE));
        TextView link = text(GITHUB, 15, MainActivity.CYAN);
        link.setPaintFlags(link.getPaintFlags() | Paint.UNDERLINE_TEXT_FLAG);
        link.setOnClickListener(v -> startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(GITHUB))));
        row(info, "GitHub", link);

        LinearLayout feat = card(col);
        TextView ft = text(getString(R.string.features), 15, Color.WHITE);
        ft.setTypeface(Typeface.DEFAULT_BOLD);
        ft.setPadding(0, 0, 0, dp(4));
        feat.addView(ft);
        for (String f : getResources().getStringArray(R.array.feature_list)) {
            TextView t = text("•  " + f, 14, Color.rgb(210, 215, 222));
            t.setPadding(0, dp(4), 0, dp(4));
            feat.addView(t);
        }

        LinearLayout disc = card(col);
        TextView dt = text(getString(R.string.disclaimer), 15, Color.WHITE);
        dt.setTypeface(Typeface.DEFAULT_BOLD);
        dt.setPadding(0, 0, 0, dp(4));
        disc.addView(dt);
        for (String d : getResources().getStringArray(R.array.disclaimer_list)) {
            TextView t = text("•  " + d, 13, TEXT2);
            t.setPadding(0, dp(4), 0, dp(4));
            disc.addView(t);
        }

        setContentView(scroll);
    }

    private String versionName() {
        try {
            return getPackageManager().getPackageInfo(getPackageName(), 0).versionName;
        } catch (PackageManager.NameNotFoundException e) {
            return "?";
        }
    }

    private LinearLayout card(LinearLayout parent) {
        LinearLayout c = new LinearLayout(this);
        c.setOrientation(LinearLayout.VERTICAL);
        c.setPadding(dp(16), dp(12), dp(16), dp(12));
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(CARD);
        bg.setCornerRadius(dp(8));
        c.setBackground(bg);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.bottomMargin = dp(12);
        parent.addView(c, lp);
        return c;
    }

    private void row(LinearLayout card, String label, View value) {
        LinearLayout r = new LinearLayout(this);
        r.setPadding(0, dp(6), 0, dp(6));
        r.addView(text(label, 14, TEXT2), new LinearLayout.LayoutParams(dp(80), -2));
        r.addView(value, new LinearLayout.LayoutParams(0, -2, 1f));
        card.addView(r);
    }

    private TextView text(String s, int sp, int color) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextSize(sp);
        t.setTextColor(color);
        return t;
    }

    private int dp(int v) {
        return (int) (v * getResources().getDisplayMetrics().density + 0.5f);
    }
}
