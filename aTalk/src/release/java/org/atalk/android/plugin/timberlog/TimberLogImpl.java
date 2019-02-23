package org.atalk.android.plugin.timberlog;

import timber.log.Timber;

public class TimberLogImpl
{
    public static void init()
    {
        // Init the crash reporting lib
        // Crashlytics.start();

        Timber.plant(new ReleaseTree()
        {
            @Override
            protected String createStackElementTag(StackTraceElement element)
            {
                return String.format("(%s:%s)#%s",
                        element.getFileName(),
                        element.getLineNumber(),
                        element.getMethodName());
            }
        });
    }
}
