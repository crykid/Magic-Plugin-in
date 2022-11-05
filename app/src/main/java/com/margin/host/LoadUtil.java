package com.margin.host;

import android.annotation.SuppressLint;
import android.annotation.TargetApi;
import android.content.Context;
import android.content.Intent;
import android.content.res.AssetManager;
import android.content.res.Resources;
import android.os.Build;
import android.os.Handler;
import android.os.Message;
import android.util.Log;

import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.List;

import dalvik.system.DexClassLoader;
import dalvik.system.PathClassLoader;

/**
 * Created by : mr.lu
 * Created at : 2020-02-11 at 15:34
 * Description:
 */
public class LoadUtil {
    private static final String TAG = "LoadUtil";

    private final static String INTENT_ORIGIN_INTENT = "intent_origin_intent";
    private final static String METHOD_START_ACTIVITY = "startActivity";
    public final static String INTENT_LOAD_PLUGIN = "intent_load_plugin";

    public final static int WHAT_LAUNCH_ACTIVITY = 100;
    //9.0版本H 处理启动activity的流程
    public final static int WHAT_EXECUTE_TRANSACTION = 159;

    /**
     * 将插件的类加载并合并到宿主中
     *
     * @param context
     */
    @TargetApi(Build.VERSION_CODES.CUPCAKE)
    public static void loadClass(Context context,String pluginAbsolutPath) {
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
            DexClassLoader dexClassLoader = new DexClassLoader(pluginAbsolutPath, context.getCacheDir().getAbsolutePath(),
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

    /**
     * Hook AMS 的startActivity，用ProxyActivity替换plugin 的Activity，以欺骗AMS的manifest校验
     */
    @TargetApi(Build.VERSION_CODES.KITKAT)
    @SuppressLint("PrivateApi")
    public static void hookAMS() {
        try {
            //- 获取singleton（它作为单例持有了IActivityManager实例）
            Field singletonFiled;
            //8.0
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
                Class<?> clazz = Class.forName("android.app.ActivityManagerNative");
                singletonFiled = clazz.getDeclaredField("gDefault");
            } else {
                final Class<?> activityManagerClass = Class.forName("android.app.ActivityManager");
                singletonFiled = activityManagerClass.getDeclaredField("IActivityManagerSingleton");
            }

            singletonFiled.setAccessible(true);
            final Object singleton = singletonFiled.get(null);//静态对象，直接反射

            //- 获取IActivityManager 对象
            final Class<?> singletonClass = Class.forName("android.util.Singleton");
            final Field mInstanceFiled = singletonClass.getDeclaredField("mInstance");
            mInstanceFiled.setAccessible(true);
            final Object mInstance = mInstanceFiled.get(singleton);

            final Class<?> iActivityManagerClass = Class.forName("android.app.IActivityManager");
            //- 代理IActivityManager
            Object proxyInstance = Proxy.newProxyInstance(Thread.currentThread().getContextClassLoader(),
                    new Class[]{iActivityManagerClass},
                    new InvocationHandler() {
                        @Override
                        public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
                            String methodName = method.getName();
                            if (methodName.equals(METHOD_START_ACTIVITY)) {
                                Intent intent = null;
                                int intentIndex = -1;
                                //找到intent
                                for (int i = 0; i < args.length; i++) {
                                    if (args[i] instanceof Intent) {
                                        intent = (Intent) args[i];
                                        intentIndex = i;
                                        break;
                                    }
                                }
                                //启动的是插件的activity才进行替换
                                if (intent != null && intent.getBooleanExtra(INTENT_LOAD_PLUGIN, false)) {
                                    final Intent proxyIntent = new Intent();
                                    proxyIntent.putExtra(INTENT_LOAD_PLUGIN, true);
                                    //使用包含ProxyActivity的intent替换原来的intent，以骗过AMS
                                    proxyIntent.setClassName("com.margin.host", "com.margin.host.ProxyActivity");
                                    //保存原来的intent
                                    proxyIntent.putExtra(INTENT_ORIGIN_INTENT, intent);
                                    //替换原来的intent
                                    args[intentIndex] = proxyIntent;
                                }
                            }
                            return method.invoke(mInstance, args);
                        }
                    });
            //替换系统的IActivityManager
            mInstanceFiled.set(singleton, proxyInstance);

        } catch (ClassNotFoundException | NoSuchFieldException | IllegalAccessException e) {
            e.printStackTrace();
        }

    }

    /**
     * Hook ActivityThread 的H-Handler，将ProxyActivity还原为 plugin的Activity
     */
    public static void hoodH() {
        final Handler.Callback callback = new Handler.Callback() {
            @Override
            public boolean handleMessage(Message msg) {
                try {
                    Intent proxyIntent;
                    Field intentField = null;
                    //startActivity 且 需要启动插件activity
                    // TODO: 2022/11/2 不同版本的 H 中 launchActivity what值可能不一样
                    if (msg.what == WHAT_LAUNCH_ACTIVITY) {
                        intentField = msg.obj.getClass().getDeclaredField("intent");
                        proxyIntent = (Intent) intentField.get(msg.obj);

                        if (proxyIntent != null
                                && proxyIntent.getBooleanExtra(INTENT_LOAD_PLUGIN, false)) {
                            intentField.setAccessible(true);
                            //替换为原来的intent
                            Intent originIntent = proxyIntent.getParcelableExtra(INTENT_ORIGIN_INTENT);
                            if (originIntent != null) {
                                intentField.set(msg.obj, originIntent);
                            }
                        }
                        //28-9.0
                    } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P
                            && msg.what == WHAT_EXECUTE_TRANSACTION) {
                        final Field mActivityCallbacksField = msg.obj.getClass().getDeclaredField("mActivityCallbacks");
                        mActivityCallbacksField.setAccessible(true);
                        final List mActivityCallbacks = (List) mActivityCallbacksField.get(msg.obj);

                        int launchItemIndex = -1;
                        for (int i = 0; i < mActivityCallbacks.size(); i++) {
                            if (mActivityCallbacks.get(i).getClass().getName().equals("android.app.servertransaction.LaunchActivityItem")) {
                                launchItemIndex = i;
                                break;
                            }
                        }
                        if (launchItemIndex > -1) {
                            final Object launchActivityItem = mActivityCallbacks.get(launchItemIndex);
                            intentField = launchActivityItem.getClass().getDeclaredField("mIntent");
                            intentField.setAccessible(true);
                            proxyIntent = (Intent) intentField.get(launchActivityItem);
                            Intent originIntent = proxyIntent.getParcelableExtra(INTENT_ORIGIN_INTENT);
                            if (originIntent != null) {
                                intentField.set(launchActivityItem, originIntent);
                            }
                        }

                    }
                } catch (NoSuchFieldException | IllegalAccessException e) {
                    e.printStackTrace();
                }
                //切记返回false不拦截，这样H 可以处理后续流程
                return false;
            }
        };
        try {
            //-1.获取ActivityThread 实例
            final Class<?> activityThreadClazz = Class.forName("android.app.ActivityThread");
            final Field sCurrentActivityThread = activityThreadClazz.getDeclaredField("sCurrentActivityThread");
            sCurrentActivityThread.setAccessible(true);
            final Object activityThread = sCurrentActivityThread.get(null);

            //-2.获取ActivityThread的mH实例
            final Field mHFiled = activityThreadClazz.getDeclaredField("mH");
            mHFiled.setAccessible(true);
            final Handler mH = (Handler) mHFiled.get(activityThread);

            //-3.获取H的mCallback变量
            final Class<?> handlerClazz = mH.getClass();
            final Field mCallbackField = handlerClazz.getDeclaredField("mCallback");
            mCallbackField.setAccessible(true);
            //-4.注入我们的Handler.Callback
            mCallbackField.set(mH, callback);
        } catch (ClassNotFoundException | NoSuchFieldException | IllegalAccessException e) {
            e.printStackTrace();
        }
    }


    /**
     * 加载插件的Resources
     *
     * @param context
     * @return
     */
    public static Resources loadResources(Context context, String pluginAbsolutePath) {
        try {
            //创建加载插件的AssetManager
            AssetManager assetManager = AssetManager.class.newInstance();
            Method addAssetPathMethod = AssetManager.class.getDeclaredMethod("addAssetPath", String.class);
            //加载插件的资源
            addAssetPathMethod.invoke(assetManager, pluginAbsolutePath);
            //获取宿主的资源
            Resources resources = context.getResources();
            return new Resources(assetManager, resources.getDisplayMetrics(), resources.getConfiguration());

        } catch (IllegalAccessException | InstantiationException | NoSuchMethodException | InvocationTargetException e) {
            Log.e(TAG, "loadResources: ", e);
        }

        return null;
    }
}
