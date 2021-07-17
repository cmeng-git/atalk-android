package org.atalk.android.plugin.timberlog;

import timber.log.Timber;

public class TimberLogImpl
{
    public static void init()
    {
        Timber.plant(new DebugTreeExt()
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
