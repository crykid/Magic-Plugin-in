package com.margin.host;

import android.app.Application;

/**
 * Created by : mr.lu
 * Created at : 2020-02-11 at 15:33
 * Description:
 */
public class HostApplication extends Application {
    @Override
    public void onCreate() {
        super.onCreate();
        LoadUtil.loadClass(this);
    }
}
