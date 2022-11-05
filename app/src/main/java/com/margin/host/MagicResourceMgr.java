package com.margin.host;

import android.content.Context;
import android.content.res.Resources;
import android.util.LruCache;

import java.io.File;
import java.util.HashMap;
import java.util.Map;

// TODO: 2022/11/6 后续会上插件化换肤 ，这个类需要整合修改
public enum MagicResourceMgr {
    INSTANCE;
    private Context mAppContext;
    private LruCache<String, Resources> mThemeResources = new LruCache<>(10);
    private Resources mCurrentRes;
    private final static String DEFAULT_RESOURCE = "default_resource";
    private static String PLUGIN_ROOT_PATH;
    private final static String THEME_FILE_SUFFIX = ".apk";
    private Map<String, String> mThemePath = new HashMap<>();

    public void init(Context context) {
        mAppContext = context.getApplicationContext();
        //SDCard/Android/data/你的应用包名/cache/theme/     目录
        PLUGIN_ROOT_PATH = mAppContext.getExternalCacheDir() + File.separator + "theme" + File.separator;
        mCurrentRes = mAppContext.getResources();
        mThemeResources.put(DEFAULT_RESOURCE, mCurrentRes);
    }

    public Resources getCurrentRes() {
        return mCurrentRes;
    }

    public void switchDefaultResources() {
        switchTheme(DEFAULT_RESOURCE);
    }

    public void switchTheme(String theme) {
        mCurrentRes = getResources(theme);
        applyTheme();
    }

    public Resources getResources(String theme) {
        Resources resources = mThemeResources.get(theme);
        if (resources == null) {
            mThemeResources.put(theme, resources = loadPluginResources(theme));
        }
        return resources;
    }
    public Resources getDefaultResources(){
        return getResources(DEFAULT_RESOURCE);
    }

    private String createOrGetThemePath(String theme) {
        String themeAbsolutePath = mThemePath.get(theme);
        if (themeAbsolutePath == null) {
            mThemePath.put(theme, themeAbsolutePath = PLUGIN_ROOT_PATH + theme + THEME_FILE_SUFFIX);
        }
        return themeAbsolutePath;
    }


    private void applyTheme() {
        // TODO: 2022/11/6 [插件化换肤]
    }

    private Resources loadPluginResources(String theme) {
        if (DEFAULT_RESOURCE.equals(theme)) {
            return mThemeResources.get(DEFAULT_RESOURCE);
        }
        return LoadUtil.loadResources(mAppContext, createOrGetThemePath(theme));
    }

//    private static class PluginLoader{
//
//    }
}
