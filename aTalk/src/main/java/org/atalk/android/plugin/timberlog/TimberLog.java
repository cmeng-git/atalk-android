package org.atalk.android.plugin.timberlog;

public class TimberLog
{
    /**
     * Priority constant for the println method; use Timber.log; mainly for fine tracing debug messages
     */
    public static final int FINER = 10;
    public static final int FINEST = 11;

    /*
     * Flag to indicate if Timber.finest is being enabled for finest tracing of the debug message
     * Set this to false if detail finest tracing is not required.
     */
    public static boolean isFinestEnable = false;

    /*
     * Flag to indicate if Timber.fine is being enabled for finer tracing of the debug message
     * Set this to false if detail tracing is not required.
     */
    public static boolean isTraceEnable = false;

    /**
     * To specify if the info logging is enabled for released version
     */
    public static boolean isInfoEnable = true;
}
