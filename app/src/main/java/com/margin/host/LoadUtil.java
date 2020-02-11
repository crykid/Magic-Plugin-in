package com.margin.host;

import android.content.Context;
import android.util.Log;

import java.lang.reflect.Array;
import java.lang.reflect.Field;

import dalvik.system.DexClassLoader;
import dalvik.system.PathClassLoader;

/**
 * Created by : mr.lu
 * Created at : 2020-02-11 at 15:34
 * Description:
 */
public class LoadUtil {
    private static final String TAG = "LoadUtil";
    private final static String apkPath = "/sdcard/plugin-debug.apk";

    /**
     * 将插件的类加载并合并到宿主中
     *
     * @param context
     */
    public static void loadClass(Context context) {
        //Element[] dexElements 是DexPathList的一个变量；
        //DexPathList pathList 是BaseDexClassesLoader的一个变量；
        try {

            //反射获取BaseDexClassLoader
            Class<?> baseDexClassLoaderClazz = Class.forName("dalvik.system.BaseDexClassLoader");

            //1.获取公共的 pathList Field
            Field pathListField = baseDexClassLoaderClazz.getDeclaredField("pathList");
            pathListField.setAccessible(true);

            //2.获取公共的 Elementp[] elements Field
            Class<?> dexPathListClazz = Class.forName("dalvik.system.DexPathList");
            Field dexElementField = dexPathListClazz.getDeclaredField("dexElements");
            dexElementField.setAccessible(true);

            //3.创建新插件的类加载器,反射获取插件 类加载器的elements
            DexClassLoader dexClassLoader = new DexClassLoader(apkPath, context.getCacheDir().getAbsolutePath(),
                    null, context.getClassLoader());
            Object pluginPathList = pathListField.get(dexClassLoader);
            Object[] pluginElements = (Object[]) dexElementField.get(pluginPathList);

            //4.获取宿主baseDexClassLoader的elements
            PathClassLoader hostClassLoader = (PathClassLoader) context.getClassLoader();
            Object hostPathList = pathListField.get(hostClassLoader);
            Object[] hostDexElements = (Object[]) dexElementField.get(hostPathList);

            //5.将插件的elements合并到宿主elements中并反射重新设置值
            Object[] compoundElements = (Object[]) Array.newInstance(hostDexElements.getClass().getComponentType(),
                    hostDexElements.length + pluginElements.length);
            System.arraycopy(hostDexElements, 0, compoundElements, 0, hostDexElements.length);
            System.arraycopy(pluginElements, 0, compoundElements, hostDexElements.length, pluginElements.length);

            dexElementField.set(hostPathList, compoundElements);


        } catch (Exception e) {
            e.printStackTrace();
            Log.e(TAG, "loadClass: ", e);
        }
    }
}
