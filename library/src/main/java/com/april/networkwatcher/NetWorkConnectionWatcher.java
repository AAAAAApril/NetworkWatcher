package com.april.networkwatcher;

import android.app.Activity;
import android.app.Application;
import android.content.Context;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.NetworkInfo;
import android.net.NetworkRequest;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentActivity;
import android.support.v4.app.FragmentManager;
import android.view.View;

import java.util.ArrayList;
import java.util.List;

/**
 * @Time: 2018-11-26
 * @Created by  April
 * <p>
 * <p>
 * 网络监听管理类
 * 利用 Activity 生命周期管理
 * 如果是需要根据网络动态改变页面，那么就需要实现监听，
 * 而如果只是想获取网络连接状态，则只需要调用方法就行了。
 */
public final class NetWorkConnectionWatcher implements Application.ActivityLifecycleCallbacks {

    /**
     * 想要在 Activity 或者 Fragment 中监听网络变化，可以直接实现此接口
     * <p>
     * 想要在其他类中监听，则可以调用 {@link #getInstance()}
     * 然后调用 {@link #addNetWatcher(INetWatcher)}
     * 不要忘记调用 {@link #removeNetWatcher(INetWatcher)}
     */
    public interface INetWatcher {
        /**
         * @param network         网络信息
         * @param mobileConnected 移动网络是否可用
         * @param wifiConnected   WiFi 网络是否可用
         */
        void onNetConnectionChanged(Network network, boolean mobileConnected, boolean wifiConnected);
    }

    //==============================================================================================

    private static NetWorkConnectionWatcher watcher;

    /**
     * 初始化
     */
    public static void init(Application application) {
        watcher = new NetWorkConnectionWatcher(application);
    }

    public static NetWorkConnectionWatcher getInstance() {
        return watcher;
    }

    //移动网络连接是否可用
    public static boolean mobileAvailable = false;
    //wifi 连接是否可用
    public static boolean wifiAvailable = false;
    //可以添加一些其他链接方式的判定值……

    /**
     * 释放资源
     */
    @Deprecated
    public static void release() {
        if (watcher != null) {
            watcher.destroy();
        }
        watcher = null;
    }

    /**
     * 初始化资源
     */
    private void init() {
        listenerList = new ArrayList<>();
        //获取网络管理器
        manager = (ConnectivityManager)
                application.getSystemService(Context.CONNECTIVITY_SERVICE);
        //初始化监听器
        callback = new NetWorkListener();
        //监听网络变化
        if (manager != null) {
            manager.requestNetwork(new NetworkRequest.Builder().build(), callback);
        }
        mobileAvailable = checkMobileAvailable();
        wifiAvailable = checkWifiAvailable();
        //注册生命周期回调
        application.registerActivityLifecycleCallbacks(this);
        lifecycle = new FragmentLifecycle();
    }

    /**
     * 释放资源
     */
    private void destroy() {
        application.unregisterActivityLifecycleCallbacks(this);
        lifecycle = null;
        if (manager != null) {
            manager.unregisterNetworkCallback(callback);
        }
        callback = null;
        manager = null;
        if (listenerList != null) {
            listenerList.clear();
        }
        listenerList = null;
    }

    /**
     * 网络连接变化
     */
    private void onNetWorkChanged(final Network network) {
        mobileAvailable = checkMobileAvailable();
        wifiAvailable = checkWifiConnected(network);
        handler.post(new Runnable() {
            @Override
            public void run() {
                for (INetWatcher listener : listenerList) {
                    if (listener != null) {
                        listener.onNetConnectionChanged(
                                network, mobileAvailable, wifiAvailable
                        );
                    }
                }
            }
        });
    }

    //==============================================================================================

    // TODO: 2019/3/14  差一个通过 Network 检测移动网络是否可用的函数

    /**
     * @return wifi 网络是否已连接
     */
    private boolean checkWifiConnected(Network network) {
        if (manager != null) {
            NetworkCapabilities capabilities = manager.getNetworkCapabilities(network);
            if (capabilities != null) {
                capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI);
            }
        }
        return false;
    }

    /**
     * @return 检查网络是否可用
     */
    public boolean checkMobileAvailable() {
        if (manager != null) {
            NetworkInfo info = manager.getNetworkInfo(ConnectivityManager.TYPE_MOBILE);
            if (info != null) {
                return info.isConnected();
            }
        }
        return false;
    }

    /**
     * @return 检查 wifi 是否可用
     */
    public boolean checkWifiAvailable() {
        if (manager != null) {
            NetworkInfo info = manager.getNetworkInfo(ConnectivityManager.TYPE_WIFI);
            if (info != null) {
                return info.isConnected();
            }
        }
        return false;
    }

    /**
     * @return 网络是否连接
     */
    public boolean checkNetworkConnected() {
        if (manager != null) {
            NetworkInfo info = manager.getActiveNetworkInfo();
            if (info != null) {
                return info.isConnected();
            }
        }
        return false;
    }

    /**
     * @param netWatcher 添加一个监听
     */
    public void addNetWatcher(INetWatcher netWatcher) {
        if (netWatcher == null || listenerList == null) {
            return;
        }
        if (!listenerList.contains(netWatcher)) {
            listenerList.add(netWatcher);
        }
    }

    /**
     * @param netWatcher 移除一个监听
     */
    public void removeNetWatcher(INetWatcher netWatcher) {
        if (listenerList == null) {
            return;
        }
        if (listenerList.contains(netWatcher)) {
            listenerList.remove(netWatcher);
        }
    }

    /**
     * 注意：SSID 值是自带双引号的，
     * 比如，正常我们认为一个字符串是指："name"，name 为 WiFi 名称，则这个 SSID 返回的值是：""name""
     *
     * @return 正在连接的 wifi 名称
     */
    @Nullable
    public String getConnectedWifiName() {
        WifiManager wifiManager = (WifiManager) application.getSystemService(Context.WIFI_SERVICE);
        if (wifiManager != null) {
            WifiInfo wifiInfo = wifiManager.getConnectionInfo();
            if (wifiInfo != null) {
                return wifiInfo.getSSID();
            }
        }
        return null;
    }

    //==============================================================================================

    //app
    private Application application;
    private Handler handler;

    /**
     * 构造函数
     */
    private NetWorkConnectionWatcher(Application application) {
        this.application = application;
        handler = new Handler(Looper.getMainLooper());
        init();
    }

    //连接管理器
    private ConnectivityManager manager;
    //网络回调
    private ConnectivityManager.NetworkCallback callback;
    //网络连接监听
    private List<INetWatcher> listenerList;
    private FragmentLifecycle lifecycle;

    //==============================================================================================

    @Override
    public void onActivityCreated(Activity activity, Bundle savedInstanceState) {
        if (activity instanceof FragmentActivity) {
            ((FragmentActivity) activity)
                    .getSupportFragmentManager()
                    .registerFragmentLifecycleCallbacks(lifecycle, true);
        }
    }

    @Override
    public void onActivityStarted(Activity activity) {
        if (activity instanceof INetWatcher) {
            addNetWatcher((INetWatcher) activity);
        }
    }

    @Override
    public void onActivityResumed(Activity activity) {

    }

    @Override
    public void onActivityPaused(Activity activity) {

    }

    @Override
    public void onActivityStopped(Activity activity) {
        if (activity instanceof INetWatcher) {
            removeNetWatcher((INetWatcher) activity);
        }
    }

    @Override
    public void onActivitySaveInstanceState(Activity activity, Bundle outState) {

    }

    @Override
    public void onActivityDestroyed(Activity activity) {

    }

    //==============================================================================================

    private class FragmentLifecycle extends FragmentManager.FragmentLifecycleCallbacks {
        @Override
        public void onFragmentViewCreated(@NonNull FragmentManager fm, @NonNull Fragment f, @NonNull View v, @Nullable Bundle savedInstanceState) {
            super.onFragmentViewCreated(fm, f, v, savedInstanceState);
        }

        @Override
        public void onFragmentStarted(@NonNull FragmentManager fm, @NonNull Fragment f) {
            super.onFragmentStarted(fm, f);
            if (f instanceof INetWatcher) {
                addNetWatcher((INetWatcher) f);
            }
        }

        @Override
        public void onFragmentStopped(@NonNull FragmentManager fm, @NonNull Fragment f) {
            super.onFragmentStopped(fm, f);
            if (f instanceof INetWatcher) {
                removeNetWatcher((INetWatcher) f);
            }
        }
    }

    //==============================================================================================

    /**
     * 网络监听类
     * 注意：这些回调似乎并不在主线程
     * <p>
     * {@link #onAvailable(Network)} 以及 {@link #onLost(Network)}
     * 是成对出现的，可以通过它们来判断网络是否可用
     */
    private class NetWorkListener extends ConnectivityManager.NetworkCallback {
        @Override
        public void onAvailable(Network network) {
            super.onAvailable(network);
            // 网络可用，会回调此方法
            onNetWorkChanged(network);
        }

        @Override
        public void onLost(Network network) {
            super.onLost(network);
            //网络不可用，会回调到此方法
            onNetWorkChanged(network);
        }
    }

}
