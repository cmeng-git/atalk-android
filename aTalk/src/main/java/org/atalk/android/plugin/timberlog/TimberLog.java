package org.atalk.android.plugin.timberlog;

public class TimberLog
{
    /**
     * Priority constant for the println method; use Timber.log; mainly for fine tracing debug messages
     */
    public static final int FINER = 10;

    /*
     * Flag to indicate if Timber.fine is being enabled for finer tracing of the debug message
     * Set this to false if detail tracing is not required.
     */
    private static boolean isTraceable = true;

    /**
     * To specify if the info logging is enabled for released version
     */
    private static boolean infoEnable = true;


    public static boolean isTraceEnabled() {
        return isTraceable;
    }

    public static boolean isInfoEnabled() {
        return infoEnable;
    }
}
