package com.zwiftjoystick;

import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

/** 协议日志（握手、GET、按键、Zwift 下发事件）。 */
public class LogActivity extends Activity implements ZwiftPeripheral.Listener {

    private App app;
    private TextView logView, stateView;
    private ScrollView scroll;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setTitle("日志");
        app = App.get();

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(MainActivity.BG);
        root.setPadding(dp(12), dp(8), dp(12), dp(8));

        LinearLayout bar = new LinearLayout(this);
        stateView = new TextView(this);
        stateView.setTextColor(Color.rgb(200, 205, 215));
        bar.addView(stateView, new LinearLayout.LayoutParams(0, -2, 1f));
        Button copy = new Button(this);
        copy.setText("复制");
        copy.setOnClickListener(v -> {
            ClipboardManager cm = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
            cm.setPrimaryClip(ClipData.newPlainText("zwift-log", app.logText()));
            Toast.makeText(this, "已复制", Toast.LENGTH_SHORT).show();
        });
        Button clear = new Button(this);
        clear.setText("清空");
        clear.setOnClickListener(v -> {
            app.clearLog();
            logView.setText("");
        });
        bar.addView(copy);
        bar.addView(clear);
        root.addView(bar);

        scroll = new ScrollView(this);
        logView = new TextView(this);
        logView.setTypeface(Typeface.MONOSPACE);
        logView.setTextSize(11);
        logView.setTextColor(Color.rgb(210, 215, 222));
        logView.setTextIsSelectable(true);
        scroll.addView(logView);
        root.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1f));
        setContentView(root);
    }

    @Override
    protected void onResume() {
        super.onResume();
        app.addListener(this);
        logView.setText(app.logText());
        stateView.setText(app.state());
        scrollToEnd();
    }

    @Override
    protected void onPause() {
        app.removeListener(this);
        super.onPause();
    }

    private void scrollToEnd() {
        scroll.post(() -> scroll.fullScroll(View.FOCUS_DOWN));
    }

    private int dp(int v) {
        return (int) (v * getResources().getDisplayMetrics().density + 0.5f);
    }

    @Override
    public void onLog(String line) {
        logView.append(line + "\n");
        scrollToEnd();
    }

    @Override
    public void onState(String state) {
        stateView.setText(state);
    }

    @Override
    public void onHaptic(int side, boolean loop, int waveId, byte[] custom) {
    }
}
