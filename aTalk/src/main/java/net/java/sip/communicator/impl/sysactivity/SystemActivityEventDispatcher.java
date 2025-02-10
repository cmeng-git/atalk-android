/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package net.java.sip.communicator.impl.sysactivity;

import net.java.sip.communicator.service.sysactivity.SystemActivityChangeListener;
import net.java.sip.communicator.service.sysactivity.event.SystemActivityEvent;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import timber.log.Timber;

/**
 * The class implements a dispatch event thread. The thread will
 * fire event every time it is added through the <code>fireSystemActivityEvent()</code>
 * method and would then deliver it to a registered listener if any.
 * If the event has time set we used it as a delay before dispatching the event.
 * <p>
 *
 * @author Damian Minkov
 * @author Eng Chong Meng
 */
public class SystemActivityEventDispatcher implements Runnable {
    /**
     * A list of listeners registered for system activity events.
     */
    private final List<SystemActivityChangeListener> listeners = new LinkedList<>();

    /**
     * start/stop indicator.
     */
    private boolean stopped = true;

    /**
     * The thread that runs this dispatcher.
     */
    private Thread dispatcherThread = null;

    /**
     * The events to dispatch.
     */
    private final Map<SystemActivityEvent, Integer> eventsToDispatch = new LinkedHashMap<>();

    /**
     * Registers a listener that would be notified of changes that have occurred
     * in the underlying system.
     *
     * @param listener the listener that we'd like to register for changes in
     * the underlying system.
     */
    public void addSystemActivityChangeListener(
            SystemActivityChangeListener listener) {
        synchronized (listeners) {
            if (!listeners.contains(listener)) {
                listeners.add(listener);
                if (dispatcherThread == null) {
                    dispatcherThread = new Thread(this);
                    dispatcherThread.start();
                }
            }
        }
    }

    /**
     * Remove the specified listener so that it won't receive further
     * notifications of changes that occur in the underlying system
     *
     * @param listener the listener to remove.
     */
    public void removeSystemActivityChangeListener(SystemActivityChangeListener listener) {
        synchronized (listeners) {
            listeners.remove(listener);
        }
    }

    /**
     * Interrupts this dispatcher so that it would no longer disptach events.
     */
    public void stop() {
        synchronized (eventsToDispatch) {
            stopped = true;
            eventsToDispatch.notifyAll();

            dispatcherThread = null;
        }
    }

    /**
     * Delivers the specified event to all registered listeners.
     *
     * @param evt the <code>SystemActivityEvent</code> that we'd like delivered to all registered message listeners.
     */
    protected void fireSystemActivityEvent(SystemActivityEvent evt) {
        fireSystemActivityEvent(evt, 0);
    }

    /**
     * Delivers the specified event to all registered listeners. Without
     * using the thread, but delivering them in the calling thread.
     *
     * @param evt the <code>SystemActivityEvent</code> that we'd like delivered to
     * all registered message listeners.
     */
    protected void fireSystemActivityEventCurrentThread(SystemActivityEvent evt) {
        List<SystemActivityChangeListener> listenersCopy = new ArrayList<>(listeners);
        for (int i = 0; i < listenersCopy.size(); i++) {
            fireSystemActivityEvent(
                    evt,
                    listenersCopy.get(i));
        }
    }

    /**
     * Delivers the specified event to all registered listeners.
     *
     * @param evt the <code>SystemActivityEvent</code> that we'd like delivered to
     * all registered message listeners.
     * @param wait time in ms. to wait before firing the event.
     */
    protected void fireSystemActivityEvent(SystemActivityEvent evt, int wait) {
        synchronized (eventsToDispatch) {
            eventsToDispatch.put(evt, wait);

            eventsToDispatch.notifyAll();

            if (dispatcherThread == null && !listeners.isEmpty()) {
                dispatcherThread = new Thread(this);
                dispatcherThread.start();
            }
        }
    }

    /**
     * Delivers the specified event to the <code>listener</code>.
     *
     * @param evt the <code>SystemActivityEvent</code> that we'd like delivered to
     * the listener.
     * @param listener that will receive the event.
     */
    private void fireSystemActivityEvent(SystemActivityEvent evt, SystemActivityChangeListener listener) {
        Timber.d("Dispatching SystemActivityEvent Listeners=" + listeners.size() + " evt=" + evt);

        if ((evt.getEventID() == SystemActivityEvent.EVENT_NETWORK_CHANGE
                || evt.getEventID() == SystemActivityEvent.EVENT_DNS_CHANGE)) {
            Timber.i("Dispatching SystemActivityEvent Listeners = %s evt = %s", listeners.size(), evt);
        }

        try {
            listener.activityChanged(evt);
        } catch (Throwable e) {
            Timber.e(e, "Error delivering event");
        }
    }

    /**
     * Runs the waiting thread.
     */
    public void run() {
        try {
            stopped = false;

            while (!stopped) {
                Map.Entry<SystemActivityEvent, Integer> eventToProcess = null;
                List<SystemActivityChangeListener> listenersCopy;

                synchronized (eventsToDispatch) {
                    if (eventsToDispatch.isEmpty()) {
                        try {
                            eventsToDispatch.wait();
                        } catch (InterruptedException ignore) {
                        }
                    }

                    //no point in dispatching if there's no one
                    //listening
                    if (listeners.isEmpty())
                        continue;

                    //store the ref of the listener in case someone resets
                    //it before we've had a chance to notify it.
                    listenersCopy = new ArrayList<>(listeners);

                    Iterator<Map.Entry<SystemActivityEvent, Integer>> iter = eventsToDispatch.entrySet().iterator();
                    if (iter.hasNext()) {
                        eventToProcess = iter.next();
                        iter.remove();
                    }
                }

                if (eventToProcess != null) {
                    if (eventToProcess.getValue() > 0)
                        synchronized (this) {
                            try {
                                wait(eventToProcess.getValue());
                            } catch (Throwable ignore) {
                            }
                        }

                    for (int i = 0; i < listenersCopy.size(); i++) {
                        fireSystemActivityEvent(eventToProcess.getKey(), listenersCopy.get(i));
                    }
                }
            }
        } catch (Throwable t) {
            Timber.e(t, "Error dispatching thread ended unexpectedly");
        }
    }
}
