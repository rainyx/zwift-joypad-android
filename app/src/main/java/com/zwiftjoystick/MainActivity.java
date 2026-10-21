package com.zwiftjoystick;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Bundle;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.view.Gravity;
import android.view.HapticFeedbackConstants;
import android.view.Menu;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.PopupMenu;
import android.widget.TextView;

import com.zwiftjoystick.ui.ClusterLayout;
import com.zwiftjoystick.ui.ControllerBody;
import com.zwiftjoystick.ui.DpadView;
import com.zwiftjoystick.ui.HintOverlay;
import com.zwiftjoystick.ui.PadButton;
import com.zwiftjoystick.ui.PaddleView;

/** 横屏深色手柄主界面：左 D-pad + ZL/LS，右 YABZ + ZR/RS；其它功能在 ☰ 菜单。 */
public class MainActivity extends BaseActivity implements ZwiftPeripheral.Listener {

    static final int BG = Color.rgb(18, 20, 24);
    static final int ORANGE = Color.rgb(252, 103, 25);
    static final int GREY = Color.rgb(120, 128, 140);
    static final int C_Y = Color.rgb(30, 155, 192), C_A = Color.rgb(52, 168, 83),
            C_B = Color.rgb(210, 53, 150), C_Z = Color.rgb(227, 90, 43);
    /** Zwift 橙背景、LED 青色（参考 Zwift Click 官方渲染图） */
    /** 底部状态文字：暂时隐藏（代码保留，改为 true 即恢复显示） */
    static final boolean SHOW_STATUS_TEXT = false;
    static final int ORANGE_BG = Color.rgb(242, 106, 28), CYAN = Color.rgb(60, 230, 242);

    private App app;
    private HintOverlay hints;
    private TextView stateView, eventView;
    private ControllerBody leftBody, rightBody;
    // 当前背景主题下的配色（buildUi 时确定）
    private int fg, fgDim, shadowCol;
    private final Runnable clearEvent = this::fadeOutEvent;
    /** 按住空白处多久显示按键说明 */
    private static final long HINT_HOLD_MS = 1500;
    private float holdX, holdY, touchSlop;
    private final Runnable showHints = () -> {
        hints.show();
        hints.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        app = App.get();
        // 手指按住时会自然漂移，系统 touchSlop（约 8dp）太严，会悄悄取消计时
        touchSlop = 48 * getResources().getDisplayMetrics().density;
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        requestPerms();
        if (app.prefs.autoStart && !app.isRunning()) app.startAdvertising();
    }

    @Override
    protected void onResume() {
        super.onResume();
        // 每次回到前台都重建：设置页可能改了键位
        setContentView(buildUi());
        hideSystemUi();
        app.addListener(this);
        onState(app.state());
    }

    @Override
    protected void onPause() {
        app.removeListener(this);
        super.onPause();
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) hideSystemUi();
    }

    @SuppressWarnings("deprecation")
    private void hideSystemUi() {
        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                | View.SYSTEM_UI_FLAG_FULLSCREEN | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION);
    }

    private void requestPerms() {
        if (Build.VERSION.SDK_INT >= 31) {
            String[] p = {Manifest.permission.BLUETOOTH_ADVERTISE, Manifest.permission.BLUETOOTH_CONNECT};
            if (checkSelfPermission(p[0]) != PackageManager.PERMISSION_GRANTED
                    || checkSelfPermission(p[1]) != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(p, 1);
            }
        }
    }

    // ===================================================================== UI

    private View buildUi() {
        // FrameLayout：手柄布局在下，说明层盖在上面（不参与布局，不拦截触摸）
        FrameLayout frame = new FrameLayout(this);
        GradientDrawable bg = new GradientDrawable();
        bg.setGradientType(GradientDrawable.RADIAL_GRADIENT);
        int[] bgColors;
        switch (app.prefs.bgTheme) {
            case Prefs.BG_DARK:
                bgColors = new int[]{Color.rgb(40, 42, 48), Color.rgb(13, 14, 17)};
                fg = Color.WHITE;
                shadowCol = Color.argb(170, 0, 0, 0);
                break;
            case Prefs.BG_LIGHT:
                bgColors = new int[]{Color.rgb(246, 247, 249), Color.rgb(204, 207, 213)};
                fg = Color.rgb(38, 40, 45);
                shadowCol = Color.argb(110, 0, 0, 0);
                break;
            default:
                bgColors = new int[]{ORANGE_BG, Color.rgb(196, 78, 14)};
                fg = Color.WHITE;
                shadowCol = Color.argb(120, 60, 20, 0);
                break;
        }
        fgDim = Color.argb(150, Color.red(fg), Color.green(fg), Color.blue(fg));
        bg.setColors(bgColors);
        bg.setGradientRadius(getResources().getDisplayMetrics().widthPixels * 0.7f);
        frame.setBackground(bg);
        hints = new HintOverlay(this);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(16), dp(6), dp(16), dp(6));
        root.addView(topBar(), new LinearLayout.LayoutParams(-1, dp(36)));

        LinearLayout body = blank(new LinearLayout(this));
        body.setOrientation(LinearLayout.HORIZONTAL);
        body.setMotionEventSplittingEnabled(true);
        root.addView(body, new LinearLayout.LayoutParams(-1, 0, 1f));

        body.addView(leftSide(), new LinearLayout.LayoutParams(0, -1, 1f));
        body.addView(space(dp(32)));
        body.addView(rightSide(), new LinearLayout.LayoutParams(0, -1, 1f));

        frame.addView(blank(root), new FrameLayout.LayoutParams(-1, -1));

        // 状态文字：底部横向居中，半透明；不占布局、不拦截点击
        stateView = new TextView(this);
        stateView.setTextColor(fgDim);
        stateView.setTextSize(12);
        stateView.setSingleLine();
        stateView.setClickable(false);
        stateView.setVisibility(SHOW_STATUS_TEXT ? View.VISIBLE : View.GONE);
        FrameLayout.LayoutParams slp = new FrameLayout.LayoutParams(-2, -2, Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL);
        slp.bottomMargin = dp(20);
        frame.addView(stateView, slp);
        frame.addView(hints, new FrameLayout.LayoutParams(-1, -1));
        return frame;
    }

    /** 空白区域：按住 1.5 秒显示按键说明层，松开隐藏。 */
    @SuppressLint("ClickableViewAccessibility")
    private <T extends View> T blank(T v) {
        if (!app.prefs.holdGuide) return v;
        v.setOnTouchListener((x, e) -> {
            switch (e.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    holdX = e.getRawX();
                    holdY = e.getRawY();
                    x.removeCallbacks(showHints);
                    x.postDelayed(showHints, HINT_HOLD_MS);
                    return true;
                case MotionEvent.ACTION_MOVE:
                    // 说明层出现前手指滑开，视为放弃
                    if (!hints.isShowing() && Math.hypot(e.getRawX() - holdX, e.getRawY() - holdY) > touchSlop) {
                        x.removeCallbacks(showHints);
                    }
                    return true;
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    x.removeCallbacks(showHints);
                    if (hints.isShowing()) hints.hide();
                    return true;
                default:
                    return true;
            }
        });
        return v;
    }

    private View topBar() {
        LinearLayout bar = new LinearLayout(this);
        bar.setGravity(Gravity.CENTER_VERTICAL);

        // 左侧占位，让事件提示保持在顶栏中央（与右侧菜单按钮等宽）
        bar.addView(new View(this), new LinearLayout.LayoutParams(dp(48), -1));

        eventView = new TextView(this);
        eventView.setTextSize(14);
        eventView.setGravity(Gravity.CENTER);
        eventView.setSingleLine();
        if (app.prefs.holdGuide) showIdleHint();
        bar.addView(eventView, new LinearLayout.LayoutParams(0, -2, 1f));

        TextView menu = new TextView(this);
        menu.setText("☰");
        menu.setTextSize(24);
        menu.setTextColor(fg);
        menu.setGravity(Gravity.CENTER);
        menu.setOnClickListener(this::showMenu);
        bar.addView(menu, new LinearLayout.LayoutParams(dp(48), -1));
        return blank(bar);
    }

    /** 进入主界面时显示操作提示，2 秒后渐隐。 */
    private void showIdleHint() {
        eventView.animate().cancel();
        eventView.setAlpha(1f);
        eventView.setTextColor(fgDim);
        eventView.setText(R.string.hint_idle);
        eventView.removeCallbacks(clearEvent);
        eventView.postDelayed(clearEvent, 2000);
    }

    private void fadeOutEvent() {
        eventView.animate().alpha(0f).setDuration(600).withEndAction(() -> eventView.setText("")).start();
    }

    /** 左手柄：ZL 拨片；机身内方向摇板，下巴为 LS（−） */
    private View leftSide() {
        LinearLayout col = blank(new LinearLayout(this));
        col.setOrientation(LinearLayout.VERTICAL);
        col.setMotionEventSplittingEnabled(true);
        col.addView(paddleRow("ZL", true), new LinearLayout.LayoutParams(-1, dp(60)));

        leftBody = blank(new ControllerBody(this, CYAN));
        leftBody.setShadowColor(shadowCol);
        leftBody.setMotionEventSplittingEnabled(true);
        leftBody.setMinimal(simple(), true);
        DpadView dpad = new DpadView(this, CYAN);
        String[] dirs = {"UP", "LEFT", "RIGHT", "DOWN"};
        Prefs p = app.prefs;
        dpad.setOnDir((dir, down) -> app.peripheral.setButton(p.digital[p.digitalIndex(dirs[dir])], down));
        for (int d = 0; d < 4; d++) {
            float[] a = DpadView.anchor(d);
            hint(dpad, a[0], a[1], dirs[d], p.digital[p.digitalIndex(dirs[d])], CYAN);
        }
        leftBody.addView(dpad);
        leftBody.addView(chin("LS", "-"));
        col.addView(leftBody, new LinearLayout.LayoutParams(-1, 0, 1f));
        return col;
    }

    /** 右手柄：ZR 拨片；机身内 Y/Z/A/B，下巴为 RS（＋） */
    private View rightSide() {
        LinearLayout col = blank(new LinearLayout(this));
        col.setOrientation(LinearLayout.VERTICAL);
        col.setMotionEventSplittingEnabled(true);
        col.addView(paddleRow("ZR", false), new LinearLayout.LayoutParams(-1, dp(60)));

        rightBody = blank(new ControllerBody(this, CYAN));
        rightBody.setShadowColor(shadowCol);
        rightBody.setMotionEventSplittingEnabled(true);
        rightBody.setMinimal(simple(), false);
        // 简约模式下没有凹槽边框，按键可以更大、更紧凑
        ClusterLayout face = simple() ? new ClusterLayout(this, 0.36f, 0.86f) : new ClusterLayout(this, 0.30f, 0.90f);
        face.setMotionEventSplittingEnabled(true);
        face.addView(ball("Y", C_Y));
        face.addView(ball("Z", C_Z));
        face.addView(ball("A", C_A));
        face.addView(ball("B", C_B));
        rightBody.addView(face);
        rightBody.addView(chin("RS", "+"));
        col.addView(rightBody, new LinearLayout.LayoutParams(-1, 0, 1f));
        return col;
    }

    /** 按键外观为“简约”：不画机身，只保留按键并放大。 */
    private boolean simple() {
        return app.prefs.keyStyle == Prefs.KEY_SIMPLE;
    }

    /** Y/Z/A/B：彩色哑光圆键 */
    private View ball(String control, int color) {
        Prefs p = app.prefs;
        int idx = p.digitalIndex(control);
        PadButton b = new PadButton(this, control, color).setStyle(PadButton.STYLE_BALL);
        b.setOnPress(down -> app.peripheral.setButton(p.digital[idx], down));
        hint(b, 0.5f, 0.5f, control, p.digital[idx], color);
        return b;
    }

    /** 机身下巴上的 −/＋ 键（LS / RS） */
    private View chin(String control, String sign) {
        Prefs p = app.prefs;
        int idx = p.digitalIndex(control);
        PadButton b = new PadButton(this, sign, CYAN).setStyle(PadButton.STYLE_CHIN).setGlow(CYAN)
                .setPlate(simple());
        b.setOnPress(down -> app.peripheral.setButton(p.digital[idx], down));
        hint(b, 0.5f, 0.5f, control, p.digital[idx], CYAN);
        return b;
    }

    /** 拨片占所在行的 70% 宽度并居中，两侧空白仍可点击呼出说明。 */
    private View paddleRow(String control, boolean left) {
        LinearLayout row = blank(new LinearLayout(this));
        row.setGravity(Gravity.CENTER);
        row.setWeightSum(1f);
        row.addView(paddle(control, left), new LinearLayout.LayoutParams(0, -1, 0.7f));
        return row;
    }

    private View paddle(String control, boolean left) {
        Prefs p = app.prefs;
        PaddleView v = new PaddleView(this, control, CYAN);
        v.setShadowColor(shadowCol);
        v.setOnValue(val -> {
            int loc = left ? p.zlLoc : p.zrLoc;
            boolean inv = left ? p.zlInvert : p.zrInvert;
            app.peripheral.setAnalog(loc, inv ? -val : val);
        });
        hints.add(v, control, getString(left ? R.string.paddle_hint_left : R.string.paddle_hint_right), CYAN);
        return v;
    }

    /** 说明层标签：上行为设置里的键码名（如 LS1、FACE_Y；未分配时用屏幕控件名），下行为 Zwift 动作。 */
    private void hint(View target, float fx, float fy, String control, int key, int color) {
        String name = Prefs.keyName(key);
        hints.add(target, fx, fy, name != null ? name : control, Prefs.actionText(getResources(), key), color);
    }

    private View space(int w) {
        View v = new View(this);
        v.setLayoutParams(new LinearLayout.LayoutParams(w, 1));
        return v;
    }

    private void showMenu(View anchor) {
        PopupMenu pm = new PopupMenu(this, anchor);
        Menu m = pm.getMenu();
        m.add(0, 1, 0, app.isRunning() ? R.string.menu_stop : R.string.menu_start);
        m.add(0, 2, 1, R.string.menu_restart);
        m.add(0, 3, 2, R.string.menu_battery);
        m.add(0, 4, 3, R.string.menu_log);
        m.add(0, 5, 4, R.string.menu_settings);
        m.add(0, 6, 5, R.string.menu_about);
        pm.setOnMenuItemClickListener(item -> {
            switch (item.getItemId()) {
                case 1:
                    if (app.isRunning()) app.stopAdvertising();
                    else app.startAdvertising();
                    return true;
                case 2:
                    app.startAdvertising();
                    return true;
                case 3:
                    app.peripheral.sendBattery();
                    return true;
                case 4:
                    startActivity(new Intent(this, LogActivity.class));
                    return true;
                case 5:
                    startActivity(new Intent(this, SettingsActivity.class));
                    return true;
                case 6:
                    startActivity(new Intent(this, AboutActivity.class));
                    return true;
                default:
                    return false;
            }
        });
        pm.show();
    }

    private int dp(int v) {
        return (int) (v * getResources().getDisplayMetrics().density + 0.5f);
    }

    // ===================================================================== Listener

    @Override
    public void onLog(String line) {
    }

    @Override
    public void onState(String state) {
        stateView.setText(state);
        int led = app.peripheral.isConnected() ? ControllerBody.LED_CONNECTED
                : app.isRunning() ? ControllerBody.LED_ADVERTISING : ControllerBody.LED_OFF;
        if (leftBody != null) leftBody.setLedState(led);
        if (rightBody != null) rightBody.setLedState(led);
    }

    @Override
    public void onHaptic(int side, boolean loop, int waveId, byte[] custom) {
        if (app.prefs.eventToast) {
            String sideS = getString(side == 0 ? R.string.side_left : side == 1 ? R.string.side_right : R.string.side_both);
            String ev = waveId >= 0 ? ZwiftPeripheral.hapticName(waveId) : getString(R.string.custom_wave);
            eventView.animate().cancel();
            eventView.setAlpha(1f);
            eventView.setTextColor(fg);
            eventView.setText("★ " + ev + " · " + sideS);
            eventView.removeCallbacks(clearEvent);
            eventView.postDelayed(clearEvent, 4000);
        }
        if (!app.prefs.vibrate) return;
        Vibrator vib = (Vibrator) getSystemService(VIBRATOR_SERVICE);
        if (vib == null || !vib.hasVibrator()) return;
        if (custom != null && custom.length > 0) {
            // Zwift 波形值 0 = 最强、127 = 无 → 振幅 255..0，每点 40ms
            long[] t = new long[custom.length];
            int[] amp = new int[custom.length];
            for (int i = 0; i < custom.length; i++) {
                t[i] = 40;
                amp[i] = Math.max(0, 255 - (custom[i] & 0x7F) * 2);
            }
            vib.vibrate(VibrationEffect.createWaveform(t, amp, -1));
        } else {
            vib.vibrate(VibrationEffect.createOneShot(120, VibrationEffect.DEFAULT_AMPLITUDE));
        }
    }
}
