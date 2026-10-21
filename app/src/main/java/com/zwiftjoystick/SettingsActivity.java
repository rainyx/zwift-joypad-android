package com.zwiftjoystick;

import android.app.AlertDialog;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.Spinner;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

/**
 * 设置页：设备模拟 / Buttons / D-Pad / Shoulder / 外观 / 反馈 分卡片，
 * 每个屏幕按键选择发送的手柄键码，Zwift 再按自身的按键绑定映射成游戏动作。
 */
public class SettingsActivity extends BaseActivity {

    private static final int CARD = Color.rgb(30, 34, 40);
    private static final int TEXT2 = Color.rgb(160, 168, 180);

    private Prefs p;
    private EditText advTypeEdit, productEdit, nameEdit;
    private Spinner formatSpinner;
    private Switch encryptSwitch;
    private boolean deviceChanged;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setTitle(R.string.title_settings);
        p = App.get().prefs;
        setContentView(build());
    }

    @Override
    protected void onPause() {
        readDeviceFields();
        p.save();
        super.onPause();
    }

    @Override
    public void onBackPressed() {
        readDeviceFields();
        p.save();
        if (deviceChanged && App.get().isRunning()) {
            new AlertDialog.Builder(this)
                    .setMessage(R.string.restart_prompt)
                    .setPositiveButton(R.string.restart_adv, (d, w) -> {
                        App.get().startAdvertising();
                        finish();
                    })
                    .setNegativeButton(R.string.later, (d, w) -> finish())
                    .show();
        } else {
            super.onBackPressed();
        }
    }

    private View build() {
        ScrollView scroll = new ScrollView(this);
        scroll.setBackgroundColor(MainActivity.BG);
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setPadding(dp(16), dp(12), dp(16), dp(24));
        scroll.addView(col);

        col.addView(header(getString(R.string.settings_header), getString(R.string.settings_header_desc)));

        // ---------- 设备模拟 ----------
        LinearLayout dev = card(col, getString(R.string.card_device));
        Spinner preset = spinner(getResources().getStringArray(R.array.presets), p.preset);
        row(dev, getString(R.string.preset), getString(R.string.preset_desc), preset);
        advTypeEdit = edit(String.format("%02X", p.advType), InputType.TYPE_CLASS_TEXT);
        row(dev, getString(R.string.adv_type), getString(R.string.adv_type_desc), advTypeEdit);
        productEdit = edit(String.valueOf(p.productId), InputType.TYPE_CLASS_NUMBER);
        row(dev, getString(R.string.product_id), getString(R.string.product_id_desc), productEdit);
        nameEdit = edit(p.name, InputType.TYPE_CLASS_TEXT);
        row(dev, getString(R.string.device_name), getString(R.string.device_name_desc), nameEdit);

        encryptSwitch = new Switch(this);
        encryptSwitch.setChecked(p.encrypted);
        row(dev, getString(R.string.encryption), "P-256 ECDH + AES-256-CCM", encryptSwitch);
        formatSpinner = spinner(Prefs.FORMATS, p.format);
        row(dev, getString(R.string.report_msg), getString(R.string.report_msg_desc), formatSpinner);
        setDeviceEditable(p.preset == 3);
        preset.setOnItemSelectedListener(new SimpleSelect(pos -> {
            if (pos == p.preset) return;
            p.applyPreset(pos);
            deviceChanged = true;
            advTypeEdit.setText(String.format("%02X", p.advType));
            productEdit.setText(String.valueOf(p.productId));
            nameEdit.setText(p.name);
            encryptSwitch.setChecked(p.encrypted);
            formatSpinner.setSelection(p.format);
            setDeviceEditable(pos == 3);
        }));

        // ---------- Buttons ----------
        LinearLayout btns = card(col, getString(R.string.card_buttons));
        for (String c : new String[]{"Y", "A", "B", "Z"}) digitalRow(btns, c);

        // ---------- D-Pad ----------
        LinearLayout dpad = card(col, getString(R.string.card_dpad));
        for (String c : new String[]{"UP", "LEFT", "DOWN", "RIGHT"}) digitalRow(dpad, c);

        // ---------- Shoulder ----------
        LinearLayout sh = card(col, getString(R.string.card_shoulder));
        digitalRow(sh, "LS");
        digitalRow(sh, "RS");
        analogRow(sh, true);
        analogRow(sh, false);
        TextView range = new TextView(this);
        range.setTextColor(Color.WHITE);
        range.setText("±" + p.analogRange);
        LinearLayout rangeBox = new LinearLayout(this);
        rangeBox.setGravity(Gravity.CENTER_VERTICAL);
        SeekBar fine = new SeekBar(this);
        fine.setMax(100);
        fine.setProgress(Math.min(100, p.analogRange));
        fine.setOnSeekBarChangeListener(new SimpleSeek(v -> {
            p.analogRange = Math.max(10, v);
            range.setText("±" + p.analogRange);
            deviceChanged = true;
        }));
        rangeBox.addView(fine, new LinearLayout.LayoutParams(dp(200), -2));
        rangeBox.addView(range);
        row(sh, getString(R.string.paddle_range), getString(R.string.paddle_range_desc), rangeBox);

        // ---------- 反馈 ----------
        LinearLayout look = card(col, getString(R.string.card_look));
        Spinner langSpinner = spinner(getResources().getStringArray(R.array.lang_options), p.lang);
        langSpinner.setOnItemSelectedListener(new SimpleSelect(pos -> {
            if (pos == p.lang) return;
            p.lang = pos;
            p.save();
            App.get().languageChanged();
            recreate();
        }));
        row(look, getString(R.string.language), getString(R.string.language_desc), langSpinner);
        Spinner bgSpinner = spinner(getResources().getStringArray(R.array.bg_themes), p.bgTheme);
        bgSpinner.setOnItemSelectedListener(new SimpleSelect(pos -> p.bgTheme = pos));
        row(look, getString(R.string.bg_theme), getString(R.string.bg_theme_desc), bgSpinner);
        Spinner keySpinner = spinner(getResources().getStringArray(R.array.key_styles), p.keyStyle);
        keySpinner.setOnItemSelectedListener(new SimpleSelect(pos -> p.keyStyle = pos));
        row(look, getString(R.string.key_style), getString(R.string.key_style_desc), keySpinner);

        LinearLayout fb = card(col, getString(R.string.card_feedback));
        row(fb, getString(R.string.vibrate), getString(R.string.vibrate_desc), toggle(p.vibrate, v -> p.vibrate = v));
        row(fb, getString(R.string.event_toast), getString(R.string.event_toast_desc), toggle(p.eventToast, v -> p.eventToast = v));
        row(fb, getString(R.string.auto_start), getString(R.string.auto_start_desc), toggle(p.autoStart, v -> p.autoStart = v));
        row(fb, getString(R.string.hold_guide), getString(R.string.hold_guide_desc), toggle(p.holdGuide, v -> p.holdGuide = v));

        Button reset = new Button(this);
        reset.setText(R.string.reset_defaults);
        reset.setOnClickListener(v -> new AlertDialog.Builder(this)
                .setMessage(R.string.reset_confirm)
                .setPositiveButton(R.string.reset_ok, (d, w) -> {
                    p.reset();
                    deviceChanged = true;
                    setContentView(build());
                    Toast.makeText(this, R.string.reset_done, Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton(R.string.cancel, null).show());
        col.addView(reset);
        return scroll;
    }

    private void digitalRow(LinearLayout card, String control) {
        int idx = p.digitalIndex(control);
        Spinner s = spinner(Prefs.keycodeNames(getResources()), p.digital[idx]);
        s.setOnItemSelectedListener(new SimpleSelect(pos -> p.digital[idx] = pos));
        row(card, getResources().getStringArray(R.array.digital_labels)[idx], null, s);
    }

    private void analogRow(LinearLayout card, boolean left) {
        LinearLayout box = new LinearLayout(this);
        box.setGravity(Gravity.CENTER_VERTICAL);
        Spinner s = spinner(getResources().getStringArray(R.array.analog_names), left ? p.zlLoc : p.zrLoc);
        s.setOnItemSelectedListener(new SimpleSelect(pos -> {
            if (left) p.zlLoc = pos;
            else p.zrLoc = pos;
        }));
        CheckBox inv = new CheckBox(this);
        inv.setText(R.string.invert);
        inv.setTextColor(Color.WHITE);
        inv.setChecked(left ? p.zlInvert : p.zrInvert);
        inv.setOnCheckedChangeListener((b, c) -> {
            if (left) p.zlInvert = c;
            else p.zrInvert = c;
        });
        box.addView(s);
        box.addView(inv);
        row(card, getString(left ? R.string.zl_paddle : R.string.zr_paddle),
                getString(left ? R.string.zl_paddle_desc : R.string.zr_paddle_desc), box);
    }

    private void readDeviceFields() {
        try {
            int t = Integer.parseInt(advTypeEdit.getText().toString().trim(), 16);
            int prod = Integer.parseInt(productEdit.getText().toString().trim());
            String n = nameEdit.getText().toString().trim();
            boolean enc = encryptSwitch.isChecked();
            int f = formatSpinner.getSelectedItemPosition();
            if (t != p.advType || prod != p.productId || !n.equals(p.name) || enc != p.encrypted || f != p.format) {
                deviceChanged = true;
            }
            p.advType = t & 0xFF;
            p.productId = prod;
            p.name = n;
            p.encrypted = enc;
            p.format = f;
        } catch (NumberFormatException e) {
            Toast.makeText(this, R.string.bad_number, Toast.LENGTH_SHORT).show();
        }
    }

    private void setDeviceEditable(boolean on) {
        advTypeEdit.setEnabled(on);
        productEdit.setEnabled(on);
        encryptSwitch.setEnabled(on);
        formatSpinner.setEnabled(on);
    }

    // ===================================================================== 小控件

    private View header(String title, String desc) {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(0, 0, 0, dp(12));
        TextView t = new TextView(this);
        t.setText(title);
        t.setTextSize(26);
        t.setTextColor(Color.WHITE);
        t.setTypeface(Typeface.DEFAULT_BOLD);
        TextView d = new TextView(this);
        d.setText(desc);
        d.setTextColor(TEXT2);
        box.addView(t);
        box.addView(d);
        return box;
    }

    private LinearLayout card(LinearLayout parent, String title) {
        LinearLayout c = new LinearLayout(this);
        c.setOrientation(LinearLayout.VERTICAL);
        c.setPadding(dp(16), dp(12), dp(16), dp(8));
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(CARD);
        bg.setCornerRadius(dp(8));
        c.setBackground(bg);
        TextView t = new TextView(this);
        t.setText(title);
        t.setTextColor(Color.WHITE);
        t.setTypeface(Typeface.DEFAULT_BOLD);
        t.setTextSize(15);
        t.setPadding(0, 0, 0, dp(4));
        c.addView(t);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.bottomMargin = dp(12);
        parent.addView(c, lp);
        return c;
    }

    private void row(LinearLayout card, String label, String desc, View control) {
        LinearLayout r = new LinearLayout(this);
        r.setGravity(Gravity.CENTER_VERTICAL);
        r.setPadding(0, dp(6), 0, dp(6));
        LinearLayout text = new LinearLayout(this);
        text.setOrientation(LinearLayout.VERTICAL);
        TextView l = new TextView(this);
        l.setText(label);
        l.setTextColor(Color.WHITE);
        l.setTextSize(14);
        text.addView(l);
        if (desc != null) {
            TextView d = new TextView(this);
            d.setText(desc);
            d.setTextColor(TEXT2);
            d.setTextSize(11);
            text.addView(d);
        }
        r.addView(text, new LinearLayout.LayoutParams(0, -2, 1f));
        r.addView(control);
        card.addView(r);
    }

    private Spinner spinner(String[] items, int sel) {
        Spinner s = new Spinner(this);
        ArrayAdapter<String> a = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, items);
        a.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        s.setAdapter(a);
        s.setSelection(Math.max(0, Math.min(items.length - 1, sel)), false);
        return s;
    }

    private EditText edit(String text, int type) {
        EditText e = new EditText(this);
        e.setText(text);
        e.setInputType(type);
        e.setSingleLine();
        e.setMinEms(6);
        e.setTextColor(Color.WHITE);
        return e;
    }

    private Switch toggle(boolean on, BoolSink sink) {
        Switch s = new Switch(this);
        s.setChecked(on);
        s.setOnCheckedChangeListener((b, c) -> sink.set(c));
        return s;
    }

    private int dp(int v) {
        return (int) (v * getResources().getDisplayMetrics().density + 0.5f);
    }

    private interface BoolSink {
        void set(boolean v);
    }

    private interface IntSink {
        void set(int v);
    }

    private static final class SimpleSelect implements AdapterView.OnItemSelectedListener {
        private final IntSink sink;

        SimpleSelect(IntSink sink) {
            this.sink = sink;
        }

        @Override
        public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
            sink.set(position);
        }

        @Override
        public void onNothingSelected(AdapterView<?> parent) {
        }
    }

    private static final class SimpleSeek implements SeekBar.OnSeekBarChangeListener {
        private final IntSink sink;

        SimpleSeek(IntSink sink) {
            this.sink = sink;
        }

        @Override
        public void onProgressChanged(SeekBar s, int progress, boolean fromUser) {
            if (fromUser) sink.set(progress);
        }

        @Override
        public void onStartTrackingTouch(SeekBar s) {
        }

        @Override
        public void onStopTrackingTouch(SeekBar s) {
        }
    }
}
