package org.atalk.android.plugin.timberlog;

import android.util.Log;

import org.jetbrains.annotations.Nullable;

import timber.log.Timber;

/**
 * Debug tree log everything;.
 * Log everything i.e priority == (Log.VERBOSE || Log.DEBUG || Log.INFO || Log.WARN || Log.ERROR)
 * Log priority == TimberLevel.FINE only if enabled
 */
public class DebugTreeExt extends Timber.DebugTree
{
    @Override
    protected boolean isLoggable(@Nullable String tag, int priority)
    {
        return ((priority != TimberLog.FINER) || TimberLog.isTraceEnable);

        // For testing release version logcat messages in debug mode
        // return (priority == Log.WARN || priority == Log.ERROR || priority == Log.ASSERT
        //        || (priority == Log.INFO && TimberLog.isInfoEnabled()));
    }

    /**
     * Must override log to print TimberLog.FINE properly by changing priority to Log.DEBUG
     * Log.println(priority, tag, message) would not print priority == TimberLog.FINE
     */
    @Override
    protected void log(int priority, String tag, String message, Throwable t)
    {
        if ((priority == TimberLog.FINER) || (priority == TimberLog.FINEST)) {
            println_native(0, priority, tag, message);
        }
        else {
            super.log(priority, tag, message, t);
        }
    }

    static int println_native(int bufID, int priority, String tag, String msgs)
    {
        String prefix = priorityChar(priority) + "/" + tag + ": ";
        for (String msg : msgs.split("\n")) {
            System.out.println(prefix + msg);
        }
        return 0;
    }

    // to replicate prefix visible when using 'adb logcat'
    private static char priorityChar(int priority)
    {
        switch (priority) {
            case Log.VERBOSE:
                return 'V';
            case Log.DEBUG:
                return 'D';
            case Log.INFO:
                return 'I';
            case Log.WARN:
                return 'W';
            case Log.ERROR:
                return 'E';
            case Log.ASSERT:
                return 'A';
            case TimberLog.FINER:
                return 'T';
            case TimberLog.FINEST:
                return 'S';
            default:
                return '?';
        }
    }
}