package com.zwiftjoystick;

import android.app.Activity;
import android.content.Context;

/** 所有页面的基类：按设置里的语言加载资源，语言改变后回到页面时自动重建。 */
abstract class BaseActivity extends Activity {

    private int lang;

    @Override
    protected void attachBaseContext(Context base) {
        lang = App.get().prefs.lang;
        super.attachBaseContext(L10n.wrap(base, lang));
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (App.get().prefs.lang != lang) recreate();
    }
}
