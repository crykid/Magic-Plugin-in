package com.margin.host;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Intent;
import android.content.res.Resources;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.TextView;

import androidx.appcompat.widget.AppCompatButton;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;

import butterknife.BindView;
import butterknife.ButterKnife;
import butterknife.OnClick;
import dalvik.system.DexClassLoader;
import dalvik.system.PathClassLoader;


public class MainActivity extends BaseActivity {
    private static final String TAG = "MainActivity";
    @BindView(R.id.btn_main_launch_class)
    AppCompatButton btnLaunchClass;
    @BindView(R.id.btn_main_start_plugin_Activity)
    AppCompatButton btnStartPluginActivity;
    @BindView(R.id.tv_content)
    TextView mTvContent;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        ButterKnife.bind(this);
        findClassLoader();

    }

    private void loadClass() {
        try {

            String dexPath = "";
            DexClassLoader dexClassLoader = new DexClassLoader(dexPath, this.getCacheDir().getAbsolutePath(),
                    null, getClassLoader());
            Class<?> clazz = dexClassLoader.loadClass("com.margin.host.ProxyActivity");

        } catch (ClassNotFoundException e) {
            e.printStackTrace();
        }
    }


    private void findClassLoader() {
        ClassLoader classLoader = getClassLoader();
        while (classLoader != null) {
            Log.d(TAG, "findClassLoader: classLoader : " + classLoader);
            classLoader = classLoader.getParent();
        }
        Log.d(TAG, "findClassLoader: classLoader : " + Activity.class.getClassLoader());
    }


    @OnClick({R.id.btn_main_launch_class, R.id.btn_main_load_plugin_res, R.id.btn_main_load_host_res,
            R.id.btn_main_start_plugin_Activity})
    public void onClick(View view) {
        switch (view.getId()) {
            case R.id.btn_main_launch_class:
                //使用插件class
//                launchDexClass();
                launchCompoundClass();
                break;
            case R.id.btn_main_start_plugin_Activity:
                //启动插件Activity
                startPluginActivity(new Intent()
                        .setComponent(new ComponentName("com.margin.plugin", "com.margin.plugin.MainActivity")));
                break;
            case R.id.btn_main_load_plugin_res:
                //使用插件资源
                MagicResourceMgr.INSTANCE.switchTheme("plugin-debug");
                mTvContent.setText(R.string.say_hi);
                break;
            case R.id.btn_main_load_host_res:
                //使用插件资源
                MagicResourceMgr.INSTANCE.switchDefaultResources();
                mTvContent.setText(R.string.say_hi);
                break;
        }
    }


    /**
     * 加载dex，为合并插件的方式
     */
    private void launchDexClass() {
        final String dexPath = "/sdcard/test.dex";
        PathClassLoader dexClassLoader = new PathClassLoader(dexPath, getClassLoader());
        try {
            Class<?> testClazz = dexClassLoader.loadClass("com.margin.plugin.Test");
            Method sayHiMethod = testClazz.getMethod("sayHi");
            sayHiMethod.invoke(null);

        } catch (ClassNotFoundException e) {
            e.printStackTrace();
        } catch (NoSuchMethodException e) {
            e.printStackTrace();
        } catch (IllegalAccessException e) {
            e.printStackTrace();
        } catch (InvocationTargetException e) {
            e.printStackTrace();
        }

    }

    /**
     * 将插件的类合并到宿主内后的加载方式
     */
    private void launchCompoundClass() {
        try {
            Class<?> testClazz = Class.forName("com.margin.plugin.Test");
            Method sayHiMethod = testClazz.getMethod("sayHi");

            sayHiMethod.invoke(null);
        } catch (ClassNotFoundException e) {
            e.printStackTrace();
        } catch (NoSuchMethodException e) {
            e.printStackTrace();
        } catch (IllegalAccessException e) {
            e.printStackTrace();
        } catch (InvocationTargetException e) {
            e.printStackTrace();
        }
    }


}
