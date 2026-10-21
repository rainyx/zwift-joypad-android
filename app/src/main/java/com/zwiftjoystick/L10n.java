package com.zwiftjoystick;

import android.content.Context;
import android.content.res.Configuration;

import java.util.Locale;

/**
 * 界面语言。跟随系统时由资源目录匹配：中文系统用 values-zh，其它语言回落到 values（英文）；
 * 手动选择时用指定语言包装 Context。
 */
final class L10n {

    static final int SYSTEM = 0, EN = 1, ZH = 2;
    private static final String[] TAGS = {null, "en", "zh"};

    private L10n() {
    }

    static Context wrap(Context base, int lang) {
        if (lang <= SYSTEM || lang >= TAGS.length) return base;
        Configuration c = new Configuration(base.getResources().getConfiguration());
        c.setLocale(new Locale(TAGS[lang]));
        return base.createConfigurationContext(c);
    }
}
