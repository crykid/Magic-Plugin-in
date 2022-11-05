package com.margin.host;

import android.app.Activity;
import android.content.Intent;
import android.content.res.Resources;

public abstract class BaseActivity extends Activity {

    protected void startPluginActivity(Intent intent) {
        intent.putExtra(LoadUtil.INTENT_LOAD_PLUGIN, true);
        startActivity(intent);
    }

    @Override
    public Resources getResources() {
        //使用Application中初始化的
        Resources resources = getApplicationContext().getResources();
        return resources != null ? resources : super.getResources();
    }
}
