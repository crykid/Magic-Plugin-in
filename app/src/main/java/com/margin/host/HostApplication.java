package com.margin.host;

import android.app.Application;
import android.content.res.Resources;

/**
 * Created by : mr.lu
 * Created at : 2020-02-11 at 15:33
 * Description:
 */
public class HostApplication extends Application {

    @Override
    public void onCreate() {
        super.onCreate();
        // TODO: 2022/11/6 [插件化换肤] 后续会更新插件化换肤，插件化管理和插件化换肤需要另外处理
        final String pluginApkPath = "/sdcard/plugin-debug.apk";
        LoadUtil.loadClass(this, pluginApkPath);
        MagicResourceMgr.INSTANCE.init(this);
    }

    @Override
    public Resources getResources() {
        //第一次启动默认加载app自带资源
        Resources currentRes = MagicResourceMgr.INSTANCE.getCurrentRes();
        return currentRes == null ? super.getResources() : currentRes;
    }
}
