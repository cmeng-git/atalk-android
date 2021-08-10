package org.atalk.android.plugin.timberlog;

public class TimberLog
{
    /**
     * Priority constant for the println method; use Timber.log; mainly for fine tracing debug messages
     */
    public static final int FINER = 10;
    public static final int FINEST = 11;

    /*
     * Set this to true to enable Timber.FINEST for tracing debug message.
     * It is also used to collect and format info for more detailed debug message display.
     */
    public static boolean isFinestEnable = false;

    /*
     * Set this to true to enable Timber.FINER tracing debug message
     */
    public static boolean isTraceEnable = false;

    /**
     * To specify if the info logging is enabled for released version
     */
    public static boolean isInfoEnable = true;
}
