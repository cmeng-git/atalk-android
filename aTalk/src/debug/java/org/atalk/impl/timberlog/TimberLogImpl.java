package org.atalk.impl.timberlog;
import androidx.annotation.NonNull;

import java.util.Locale;

import timber.log.Timber;

public class TimberLogImpl
{
    public static void init()
    {
        Timber.plant(new DebugTreeExt()
        {
            @Override
            protected String createStackElementTag(@NonNull StackTraceElement element)
            {
                return String.format(Locale.US, "(%s:%s)#%s",
                        element.getFileName(),
                        element.getLineNumber(),
                        element.getMethodName());
            }
        });
    }
}
