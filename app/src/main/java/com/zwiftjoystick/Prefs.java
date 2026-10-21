package com.zwiftjoystick;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.Resources;

/** 所有用户设置（SharedPreferences）。 */
final class Prefs {

    // ---- 屏幕上的控件 ----
    static final String[] DIGITAL = {"UP", "LEFT", "DOWN", "RIGHT", "Y", "A", "B", "Z", "LS", "RS"};
    static final int[] DIGITAL_DEFAULT = {1, 0, 3, 2, 6, 4, 5, 7, 8, 12};

    /** 手柄键码 0..15；对应的 Zwift 默认动作见 R.array.keycode_actions。 */
    static final String[] KEYCODES = {
            "0x00 DPAD_LEFT", "0x01 DPAD_UP", "0x02 DPAD_RIGHT", "0x03 DPAD_DOWN",
            "0x04 FACE_A", "0x05 FACE_B", "0x06 FACE_Y", "0x07 FACE_Z",
            "0x08 LS1", "0x09 LS2", "0x0A LS3", "0x0B LF",
            "0x0C RS1", "0x0D RS2", "0x0E RS3", "0x0F RF"};

    static final int BG_ZWIFT = 0, BG_DARK = 1, BG_LIGHT = 2;

    static final int KEY_REAL = 0, KEY_SIMPLE = 1;

    static final String[] FORMATS = {"0x23 RideKeyPadStatus", "0x07 PlayKeyPadStatus", "0x37 ClickKeyPadStatus"};
    static final int[] FORMAT_OPCODES = {0x23, 0x07, 0x37};

    private final SharedPreferences sp;

    int preset, advType, productId, format;
    String name;
    boolean encrypted;
    final int[] digital = new int[DIGITAL.length];   // 键码，16 = 不发送
    int zlLoc, zrLoc;                                 // 0..3，4 = 不发送
    boolean zlInvert, zrInvert;
    int analogRange;
    boolean vibrate, eventToast, autoStart;
    boolean holdGuide;
    int bgTheme;
    int keyStyle;
    int lang;                                         // L10n.SYSTEM / EN / ZH

    Prefs(Context ctx) {
        sp = ctx.getSharedPreferences("cfg", Context.MODE_PRIVATE);
        load();
    }

    void load() {
        preset = sp.getInt("preset", 0);
        advType = sp.getInt("advType", 0x07);
        productId = sp.getInt("productId", 8);
        format = sp.getInt("format", 0);
        name = sp.getString("name", "Zwift Ride");
        encrypted = sp.getBoolean("encrypted", false);
        for (int i = 0; i < DIGITAL.length; i++) digital[i] = sp.getInt("map_" + DIGITAL[i], DIGITAL_DEFAULT[i]);
        zlLoc = sp.getInt("zlLoc", 0);
        zrLoc = sp.getInt("zrLoc", 1);
        zlInvert = sp.getBoolean("zlInvert", false);
        zrInvert = sp.getBoolean("zrInvert", false);
        analogRange = sp.getInt("analogRange", 100);
        vibrate = sp.getBoolean("vibrate", true);
        eventToast = sp.getBoolean("eventToast", true);
        autoStart = sp.getBoolean("autoStart", true);
        holdGuide = sp.getBoolean("holdGuide", true);
        bgTheme = sp.getInt("bgTheme", BG_ZWIFT);
        keyStyle = sp.getInt("keyStyle", KEY_REAL);
        lang = sp.getInt("lang", L10n.SYSTEM);
    }

    void save() {
        SharedPreferences.Editor e = sp.edit()
                .putInt("preset", preset).putInt("advType", advType).putInt("productId", productId)
                .putInt("format", format).putString("name", name).putBoolean("encrypted", encrypted)
                .putInt("zlLoc", zlLoc).putInt("zrLoc", zrLoc)
                .putBoolean("zlInvert", zlInvert).putBoolean("zrInvert", zrInvert)
                .putInt("analogRange", analogRange)
                .putBoolean("vibrate", vibrate).putBoolean("eventToast", eventToast).putBoolean("autoStart", autoStart)
                .putBoolean("holdGuide", holdGuide)
                .putInt("bgTheme", bgTheme).putInt("keyStyle", keyStyle)
                .putInt("lang", lang);
        for (int i = 0; i < DIGITAL.length; i++) e.putInt("map_" + DIGITAL[i], digital[i]);
        e.apply();
    }

    void reset() {
        sp.edit().clear().apply();
        load();
    }

    /** 选择预设时同步设备参数（自定义不改）。 */
    void applyPreset(int p) {
        preset = p;
        switch (p) {
            case 0:
                advType = 0x07; productId = 8; encrypted = false; format = 0; name = "Zwift Ride";
                break;
            case 1:
                advType = 0x02; productId = 14; encrypted = true; format = 1; name = "Zwift Play";
                break;
            case 2:
                advType = 0x09; productId = 11; encrypted = true; format = 2; name = "Zwift Click";
                break;
            default:
                break;
        }
    }

    int digitalIndex(String control) {
        for (int i = 0; i < DIGITAL.length; i++) if (DIGITAL[i].equals(control)) return i;
        return -1;
    }

    /** 设置页的键码选项："0x00 DPAD_LEFT · 路口左转"…，最后一项为“不发送”。 */
    static String[] keycodeNames(Resources r) {
        String[] actions = r.getStringArray(R.array.keycode_actions);
        String[] out = new String[KEYCODES.length + 1];
        for (int i = 0; i < KEYCODES.length; i++) out[i] = KEYCODES[i] + " · " + actions[i];
        out[KEYCODES.length] = r.getString(R.string.key_none);
        return out;
    }

    /** 键码名（去掉 0x 前缀），如 LS1、FACE_Y；未分配时返回 null。 */
    static String keyName(int key) {
        return key >= 0 && key < KEYCODES.length ? KEYCODES[key].substring(5) : null;
    }

    /** 键码在 Zwift 默认绑定中的动作，未分配时返回“未分配”。 */
    static String actionText(Resources r, int key) {
        return key >= 0 && key < KEYCODES.length ? r.getStringArray(R.array.keycode_actions)[key] : r.getString(R.string.unassigned);
    }

    ZwiftPeripheral.Config toConfig() {
        ZwiftPeripheral.Config c = new ZwiftPeripheral.Config();
        c.advType = advType;
        c.productId = productId;
        c.name = name;
        c.encrypted = encrypted;
        c.reportOpcode = FORMAT_OPCODES[format];
        c.analogMin = -analogRange;
        c.analogMax = analogRange;
        return c;
    }
}
