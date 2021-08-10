package org.atalk.android.plugin.timberlog;

import android.util.Log;

import org.jetbrains.annotations.Nullable;

import timber.log.Timber;

/**
 * Release tree to log only WARN, ERROR and WTF.
 * Do not log if (priority == Log.VERBOSE || priority == Log.DEBUG || priority == TimberLevel.FINE);
 * Log Log.INFO only if enabled for released apk
 */
public class ReleaseTree extends Timber.DebugTree
{
    @Override
    protected boolean isLoggable(@Nullable String tag, int priority)
    {
        // return (priority < TimberLog.FINE && priority > Log.DEBUG) && (priority != Log.INFO || TimberLog.isInfoEnabled());
        return (priority == Log.WARN || priority == Log.ERROR || priority == Log.ASSERT
                || (priority == Log.INFO && TimberLog.isInfoEnable));
    }

//    @Override
//    protected void log(int priority, String tag, String message, Throwable throwable)
//    {
//        super.log(priority, tag, message, throwable);
//
//        if (priority >= Log.ERROR) {
//            Crashlytics.log(priority, tag, message);
//
//            if (throwable != null) {
//                Crashlytics.logException(throwable);
//            }
//        }
//    }
}
