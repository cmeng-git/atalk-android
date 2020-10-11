/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license.
 * See terms of license at gnu.org.
 */
package org.atalk.impl.osgi.framework.startlevel;

import org.atalk.impl.osgi.framework.AsyncExecutor;
import org.atalk.impl.osgi.framework.BundleImpl;
import org.atalk.impl.osgi.framework.launch.FrameworkImpl;
import org.osgi.framework.FrameworkListener;
import org.osgi.framework.startlevel.FrameworkStartLevel;

import java.util.concurrent.TimeUnit;

/**
 * @author Lyubomir Marinov
 */
public class FrameworkStartLevelImpl implements FrameworkStartLevel
{
    private final BundleImpl bundle;

    private final AsyncExecutor<Command> executor = new AsyncExecutor<>(5, TimeUnit.MINUTES);

    private int initialBundleStartLevel = 0;

    private int startLevel;

    public FrameworkStartLevelImpl(BundleImpl bundle)
    {
        this.bundle = bundle;
    }

    public BundleImpl getBundle()
    {
        return bundle;
    }

    private FrameworkImpl getFramework()
    {
        return getBundle().getFramework();
    }

    public int getInitialBundleStartLevel()
    {
        int initialBundleStartLevel = this.initialBundleStartLevel;

        if (initialBundleStartLevel == 0)
            initialBundleStartLevel = 1;
        return initialBundleStartLevel;
    }

    public synchronized int getStartLevel()
    {
        return startLevel;
    }

    public void internalSetStartLevel(
            int startLevel,
            FrameworkListener... listeners)
    {
        if (startLevel < 0)
            throw new IllegalArgumentException("startLevel");

        executor.execute(new Command(startLevel, listeners));
    }

    public void setInitialBundleStartLevel(int initialBundleStartLevel)
    {
        if (initialBundleStartLevel <= 0)
            throw new IllegalArgumentException("initialBundleStartLevel");

        this.initialBundleStartLevel = initialBundleStartLevel;
    }

    public void setStartLevel(int startLevel, FrameworkListener... listeners)
    {
        if (startLevel == 0)
            throw new IllegalArgumentException("startLevel");

        internalSetStartLevel(startLevel, listeners);
    }

    public void stop()
    {
        executor.shutdownNow();
    }

    private class Command implements Runnable
    {
        private final FrameworkListener[] listeners;

        private final int startLevel;

        public Command(int startLevel, FrameworkListener... listeners)
        {
            this.startLevel = startLevel;
            this.listeners = listeners;
        }

        public void run()
        {
            int startLevel = getStartLevel();
            FrameworkImpl framework = getFramework();

            if (startLevel < this.startLevel) {
                for (int intermediateStartLevel = startLevel + 1;
                     intermediateStartLevel <= this.startLevel;
                     intermediateStartLevel++) {
                    int oldStartLevel = getStartLevel();
                    int newStartLevel = intermediateStartLevel;

                    framework.startLevelChanging(oldStartLevel, newStartLevel, listeners);
                    synchronized (FrameworkStartLevelImpl.this) {
                        FrameworkStartLevelImpl.this.startLevel = newStartLevel;
                    }
                    framework.startLevelChanged(oldStartLevel, newStartLevel, listeners);
                }
            }
            else if (this.startLevel < startLevel) {
                for (int intermediateStartLevel = startLevel;
                     intermediateStartLevel > this.startLevel;
                     intermediateStartLevel--) {
                    int oldStartLevel = getStartLevel();
                    int newStartLevel = intermediateStartLevel - 1;

                    framework.startLevelChanging(oldStartLevel, newStartLevel, listeners);
                    synchronized (FrameworkStartLevelImpl.this) {
                        FrameworkStartLevelImpl.this.startLevel = newStartLevel;
                    }
                    framework.startLevelChanged(oldStartLevel, newStartLevel, listeners);
                }
            }
            else {
                framework.startLevelChanging(startLevel, startLevel, listeners);
                framework.startLevelChanged(startLevel, startLevel, listeners);
            }
        }
    }
}
