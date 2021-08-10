package org.atalk.android.util;

import android.app.*;
import android.os.Bundle;

import org.atalk.android.aTalkApp;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import timber.log.Timber;

/**
 * based on https://gist.github.com/steveliles/11116937
 * Usage:
 *
 * 1. Get the Foreground Singleton, passing a Context or Application object unless you
 * are sure that the Singleton has definitely already been initialised elsewhere.
 *
 * 2.a) Perform a direct, synchronous check: Foreground.isForeground() / .isBackground()
 * 2.b) Register to be notified (useful in Service or other non-UI components):
 *
 * BackgroundManager.Listener myListener = new Foreground.Listener(){
 * public void onAppForeground(){
 * // ... whatever you want to do
 * }
 * public void onAppBackground(){
 * // ... whatever you want to do
 * }
 * }
 *
 * public void onCreate(){
 * super.onCreate();
 * BackgroundManager.get(this).addListener(listener);
 * }
 *
 * public void onDestroy(){
 * super.onCreate();
 * BackgroundManager.get(this).removeListener(listener);
 * }
 */
public class BackgroundManager implements Application.ActivityLifecycleCallbacks
{
    private static BackgroundManager sInstance;
    private boolean appInForeground = false;

    public interface Listener
    {
        void onAppForeground();

        void onAppBackground();
    }

    private boolean mInBackground = true;
    private final List<Listener> listeners = new CopyOnWriteArrayList<>();

    public static BackgroundManager getInstance()
    {
        if (sInstance == null) {
            sInstance = new BackgroundManager(aTalkApp.getInstance());
        }
        return sInstance;
    }

    private BackgroundManager(Application application)
    {
        application.registerActivityLifecycleCallbacks(this);
    }

    public void registerListener(Listener listener)
    {
        listeners.add(listener);
    }

    public void unregisterListener(Listener listener)
    {
        listeners.remove(listener);
    }

    /*
     * Activity state changes has transition, also is too late to be used by
     * AndroidCameraSystem and MediaRecorderSystem; Use isAppInBackground instead
     * pre android-o will also return false
     */
    public boolean isAppInBackground()
    {
        ActivityManager.RunningAppProcessInfo myProcess = new ActivityManager.RunningAppProcessInfo();
        try {
            ActivityManager.getMyMemoryState(myProcess);
        } catch (NoSuchMethodError ignored) {
            // from android 4.0.3
        }
        return myProcess.importance != ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND;
    }

    @Override
    public void onActivityResumed(Activity activity)
    {
        if (!appInForeground && !isAppInBackground()) {
            appInForeground = true;
            Timber.i("Application returns to foreground");
            notifyOnAppForeground();
        }
    }

    private void notifyOnAppForeground()
    {
        for (Listener listener : listeners) {
            try {
                listener.onAppForeground();
            } catch (Exception e) {
                Timber.e(e, "Listener threw exception!");
            }
        }
    }

    @Override
    public void onActivityPaused(Activity activity)
    {
        if (isAppInBackground()) {
            appInForeground = false;
            Timber.i("Application returns to background");
            notifyOnAppBackground();
        }
    }

    private void notifyOnAppBackground()
    {
        for (Listener listener : listeners) {
            try {
                listener.onAppBackground();
            } catch (Exception e) {
                Timber.e(e, "Listener threw exception!");
            }
        }
    }

    @Override
    public void onActivityStopped(Activity activity)
    {
    }

    @Override
    public void onActivityCreated(Activity activity, Bundle savedInstanceState)
    {
    }

    @Override
    public void onActivityStarted(Activity activity)
    {
    }

    @Override
    public void onActivitySaveInstanceState(Activity activity, Bundle outState)
    {
    }

    @Override
    public void onActivityDestroyed(Activity activity)
    {
    }
}
