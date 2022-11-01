package com.margin.host;

import android.app.Activity;
import android.content.Intent;

public abstract class BaseActivity extends Activity {

    protected void startPluginActivity(Intent intent) {
        intent.putExtra(LoadUtil.INTENT_LOAD_PLUGIN, true);
        startActivity(intent);
    }
}
