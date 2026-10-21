package com.zwiftjoystick;

import android.content.Context;
import android.content.SharedPreferences;

/** 所有用户设置（SharedPreferences）。 */
final class Prefs {

    // ---- 屏幕上的控件 ----
    static final String[] DIGITAL = {"UP", "LEFT", "DOWN", "RIGHT", "Y", "A", "B", "Z", "LS", "RS"};
    static final String[] DIGITAL_LABEL = {"上 ∧", "左 <", "下 ∨", "右 >", "Y", "A", "B", "Z", "LS", "RS"};
    static final int[] DIGITAL_DEFAULT = {1, 0, 3, 2, 6, 4, 5, 7, 8, 12};

    /** 手柄键码 0..15 及其在 Zwift 默认绑定中的功能。 */
    static final String[] KEYCODE_NAMES = {
            "0x00 DPAD_LEFT · 路口左转",
            "0x01 DPAD_UP · 动作栏",
            "0x02 DPAD_RIGHT · 路口右转",
            "0x03 DPAD_DOWN · 掉头(长按)",
            "0x04 FACE_A · 选择",
            "0x05 FACE_B · 返回/菜单",
            "0x06 FACE_Y · 使用道具",
            "0x07 FACE_Z · RideOn(长按炸弹)",
            "0x08 LS1 · 减档 / 降强度",
            "0x09 LS2 · 减档",
            "0x0A LS3 · drops 绑定",
            "0x0B LF · 左电源键",
            "0x0C RS1 · 加档 / 升强度",
            "0x0D RS2 · 加档",
            "0x0E RS3 · drops 绑定",
            "0x0F RF · 右电源键",
            "不发送"};
    static final String[] ANALOG_NAMES = {"LH 左横向 (0x10)", "RH 右横向 (0x11)", "LV 左纵向 (0x12)", "RV 右纵向 (0x13)", "不发送"};

    static final String[] BG_THEMES = {"Zwift 橙", "深色", "浅色"};
    static final int BG_ZWIFT = 0, BG_DARK = 1, BG_LIGHT = 2;

    static final String[] KEY_STYLES = {"仿真", "简约"};
    static final int KEY_REAL = 0, KEY_SIMPLE = 1;

    static final String[] PRESETS = {"Zwift Ride（明文）", "Zwift Play（加密）", "Zwift Click（加密）", "自定义"};
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
    int bgTheme;
    int keyStyle;

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
        bgTheme = sp.getInt("bgTheme", BG_ZWIFT);
        keyStyle = sp.getInt("keyStyle", KEY_REAL);
    }

    void save() {
        SharedPreferences.Editor e = sp.edit()
                .putInt("preset", preset).putInt("advType", advType).putInt("productId", productId)
                .putInt("format", format).putString("name", name).putBoolean("encrypted", encrypted)
                .putInt("zlLoc", zlLoc).putInt("zrLoc", zrLoc)
                .putBoolean("zlInvert", zlInvert).putBoolean("zrInvert", zrInvert)
                .putInt("analogRange", analogRange)
                .putBoolean("vibrate", vibrate).putBoolean("eventToast", eventToast).putBoolean("autoStart", autoStart)
                .putInt("bgTheme", bgTheme).putInt("keyStyle", keyStyle);
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
