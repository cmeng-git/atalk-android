/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.atalk.util.concurrent;

import org.jetbrains.annotations.NotNull;

import java.util.concurrent.*;

/**
 * Implements utility functions to facilitate work with <tt>Executor</tt>s and <tt>ExecutorService</tt>.
 *
 * @author Lyubomir Marinov
 */
public class ExecutorUtils
{
    /**
     * Creates a thread pool that creates new threads as needed, but will reuse previously
     * constructed threads when they are available. Optionally, the new threads are created as
     * daemon threads and their names are based on a specific (prefix) string.
     *
     * @param daemon <tt>true</tt> to create the new threads as daemon threads
     * or <tt>false</tt> to create the new threads as user threads
     * @param baseName the base/prefix to use for the names of the new threads
     * or <tt>null</tt> to leave them with their default names
     * @return the newly created thread pool
     */
    public static ExecutorService newCachedThreadPool(final boolean daemon, final String baseName)
    {
        return Executors.newCachedThreadPool(newThreadFactory(daemon, baseName));
    }

    /**
     * A thread factory creating threads, which are created as daemon threads(optionally)
     * and their names are based on a specific (prefix) string.
     *
     * @param daemon <tt>true</tt> to create the new threads as daemon threads
     * or <tt>false</tt> to create the new threads as user threads
     * @param baseName the base/prefix to use for the names of the new threads
     * or <tt>null</tt> to leave them with their default names
     * @return the newly created thread factory
     */
    private static ThreadFactory newThreadFactory(final boolean daemon, final String baseName)
    {
        return new ThreadFactory()
        {
            /**
             * The default <tt>ThreadFactory</tt> implementation which is augmented by this
             * instance to create daemon <tt>Thread</tt>s.
             */
            private final ThreadFactory defaultThreadFactory = Executors.defaultThreadFactory();

            @Override
            public Thread newThread(@NotNull Runnable r)
            {
                Thread t = defaultThreadFactory.newThread(r);
                if (t != null) {
                    t.setDaemon(daemon);

                    /*
                     * Additionally, make it known through the name of the Thread that it is
                     * associated with the specified class for debugging/informational purposes.
                     */
                    if ((baseName != null) && (baseName.length() != 0)) {
                        t.setName(baseName + "-" + t.getName());
                    }
                }
                return t;
            }
        };
    }

    /**
     * Creates a scheduled thread pool, Optionally, the new threads are created
     * as daemon threads and their names are based on a specific (prefix) string.
     *
     * @param corePoolSize the number of threads to keep in the pool,
     * even if they are idle.
     * @param daemon <tt>true</tt> to create the new threads as daemon threads
     * or <tt>false</tt> to create the new threads as user threads
     * @param baseName the base/prefix to use for the names of the new threads
     * or <tt>null</tt> to leave them with their default names
     * @return the newly created thread pool
     */
    public static ScheduledExecutorService newScheduledThreadPool(int corePoolSize, boolean daemon, String baseName)
    {
        return Executors.newScheduledThreadPool(corePoolSize, newThreadFactory(daemon, baseName));
    }
}
