/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license.
 * See terms of license at gnu.org.
 */
package org.atalk.impl.osgi.framework.launch;

import org.atalk.impl.osgi.framework.AsyncExecutor;
import org.osgi.framework.*;

import java.util.EventListener;
import java.util.EventObject;
import java.util.concurrent.RejectedExecutionException;

import timber.log.Timber;

/**
 * @author Lyubomir Marinov
 * @author Eng Chong Meng
 */
public class EventDispatcher
{
    private final AsyncExecutor<Command> executor = new AsyncExecutor<>();

    private final EventListenerList listeners = new EventListenerList();

    public <T extends EventListener> boolean addListener(Bundle bundle, Class<T> clazz, T listener)
    {
        return listeners.add(bundle, clazz, listener);
    }

    void fireBundleEvent(BundleEvent event)
    {
        fireEvent(BundleListener.class, event);
    }

    private <T extends EventListener> void fireEvent(Class<T> clazz, EventObject event)
    {
        T[] listeners = this.listeners.getListeners(clazz);

        if (listeners.length != 0)
            try {
                executor.execute(new Command(clazz, event));
            } catch (RejectedExecutionException ree) {
                Timber.e(ree, "Error firing event");
            }
    }

    void fireServiceEvent(ServiceEvent event)
    {
        fireEvent(ServiceListener.class, event);
    }

    public <T extends EventListener> boolean removeListener(Bundle bundle, Class<T> clazz, T listener)
    {
        return listeners.remove(bundle, clazz, listener);
    }

    public boolean removeListeners(Bundle bundle)
    {
        return listeners.removeAll(bundle);
    }

    public void stop()
    {
        executor.shutdownNow();
    }

    private class Command implements Runnable
    {
        private final Class<? extends EventListener> clazz;

        private final EventObject event;

        public <T extends EventListener> Command(Class<T> clazz, EventObject event)
        {
            this.clazz = clazz;
            this.event = event;
        }

        public void run()
        {
            // Fetches listeners before command is started to get latest version of the list
            EventListener[] listeners = EventDispatcher.this.listeners.getListeners(clazz);
            for (EventListener listener : listeners) {
                try {
                    if (BundleListener.class.equals(clazz)) {
                        ((BundleListener) listener).bundleChanged((BundleEvent) event);
                    }
                    else if (ServiceListener.class.equals(clazz)) {
                        ((ServiceListener) listener).serviceChanged((ServiceEvent) event);
                    }
                } catch (Throwable t) {
                    Timber.e(t, "Error dispatching event");
                    if (FrameworkListener.class.equals(clazz)
                            && ((FrameworkEvent) event).getType() != FrameworkEvent.ERROR) {
                        // TODO Auto-generated method stub
                    }
                }
            }
        }
    }
}
