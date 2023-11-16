/*
 * Copyright @ 2015 Atlassian Pty Ltd
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
package org.atalk.util.event;

import java.awt.Component;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

/**
 * Represents a mechanism to easily add to a specific <code>Object</code> by means
 * of composition support for firing <code>VideoEvent</code>s to <code>VideoListener</code>s.
 *
 * @author Lyubomir Marinov
 * @author Eng Chong Meng
 */
public class VideoNotifierSupport
{
    private static final long THREAD_TIMEOUT = 5000;

    /**
     * The list of <code>VideoEvent</code>s which are to be delivered to the
     * {@link #listeners} registered with this instance when
     * {@link #synchronous} is equal to <code>false</code>.
     */
    private final List<VideoEvent> events;

    /**
     * The list of <code>VideoListener</code>s interested in changes in the
     * availability of visual <code>Component</code>s depicting video.
     */
    private final List<VideoListener> listeners = new ArrayList<>();

    /**
     * The <code>Object</code> which is to be reported as the source of the <code>VideoEvent</code>s fired by this instance.
     */
    private final Object source;

    /**
     * The indicator which determines whether this instance delivers the
     * <code>VideoEvent</code>s to the {@link #listeners} synchronously.
     */
    private final boolean synchronous;

    /**
     * The <code>Thread</code> in which {@link #events} are delivered to the
     * {@link #listeners} when {@link #synchronous} is equal to <code>false</code>.
     */
    private Thread thread;

    /**
     * Initializes a new <code>VideoNotifierSupport</code> instance which is to facilitate the management of
     * <code>VideoListener</code>s and firing <code>VideoEvent</code>s to them for a specific <code>Object</code>.
     *
     * @param source the <code>Object</code> which is to be reported as the source
     * of the <code>VideoEvent</code>s fired by the new instance
     */
    public VideoNotifierSupport(Object source)
    {
        this(source, true);
    }

    /**
     * Initializes a new <code>VideoNotifierSupport</code> instance which is to facilitate the management of
     * <code>VideoListener</code>s and firing <code>VideoEvent</code>s to them for a specific <code>Object</code>.
     *
     * @param source the <code>Object</code> which is to be reported as the source
     * of the <code>VideoEvent</code>s fired by the new instance
     * @param synchronous <code>true</code> if the new instance is to deliver the
     * <code>VideoEvent</code>s synchronously; otherwise, <code>false</code>
     */
    public VideoNotifierSupport(Object source, boolean synchronous)
    {
        this.source = source;
        this.synchronous = synchronous;

        events = this.synchronous ? null : new LinkedList<>();
    }

    /**
     * Adds a specific <code>VideoListener</code> to this <code>VideoNotifierSupport</code> in order to receive
     * notifications when visual/video <code>Component</code>s are being added and removed.
     *
     * Adding a listener which has already been added does nothing i.e. it is not added more than once
     * and thus does not receive one and the same <code>VideoEvent</code> multiple times.
     *
     * @param listener the <code>VideoListener</code> to be notified when visual/video <code>Component</code>s
     * are being added or removed in this <code>VideoNotifierSupport</code>
     */
    public void addVideoListener(VideoListener listener)
    {
        if (listener == null)
            throw new NullPointerException("listener");

        synchronized (listeners) {
            if (!listeners.contains(listener))
                listeners.add(listener);
        }
    }

    protected void doFireVideoEvent(VideoEvent event)
    {
        VideoListener[] listeners;

        synchronized (this.listeners) {
            listeners = this.listeners.toArray(new VideoListener[0]);
        }

        for (VideoListener listener : listeners)
            switch (event.getType()) {
                case VideoEvent.VIDEO_ADDED:
                    listener.videoAdded(event);
                    break;
                case VideoEvent.VIDEO_REMOVED:
                    listener.videoRemoved(event);
                    break;
                default:
                    listener.videoUpdate(event);
                    break;
            }
    }

    /**
     * Notifies the <code>VideoListener</code>s registered with this <code>VideoMediaStream</code> about a specific
     * type of change in the availability of a specific visual <code>Component</code> depicting video.
     *
     * @param type the type of change as defined by <code>VideoEvent</code> in the
     * availability of the specified visual <code>Component</code> depicting video
     * @param visualComponent the visual <code>Component</code> depicting video which has been added or removed
     * @param origin {@link VideoEvent#LOCAL} if the origin of the video is local (e.g. it is being locally captured);
     * {@link VideoEvent#REMOTE} if the origin of the video is remote (e.g. a remote peer is streaming it)
     * @param wait <code>true</code> if the call is to wait till the specified
     * <code>VideoEvent</code> has been delivered to the <code>VideoListener</code>s; otherwise, <code>false</code>
     * @return <code>true</code> if this event and, more specifically, the visual <code>Component</code> it describes
     * have been consumed and should be considered owned, referenced (which is important because
     * <code>Component</code>s belong to a single <code>Container</code> at a time); otherwise, <code>false</code>
     */
    public boolean fireVideoEvent(int type, Component visualComponent, int origin, boolean wait)
    {
        VideoEvent event = new VideoEvent(source, type, visualComponent, origin);

        fireVideoEvent(event, wait);
        return event.isConsumed();
    }

    /**
     * Notifies the <code>VideoListener</code>s registered with this instance about a specific <code>VideoEvent</code>.
     *
     * @param event the <code>VideoEvent</code> to be fired to the <code>VideoListener</code>s registered with this instance
     * @param wait <code>true</code> if the call is to wait till the specified
     * <code>VideoEvent</code> has been delivered to the <code>VideoListener</code>s; otherwise, <code>false</code>
     */
    public void fireVideoEvent(VideoEvent event, boolean wait)
    {
        if (synchronous)
            doFireVideoEvent(event);
        else {
            synchronized (events) {
                events.add(event);
                // if (VideoEvent.VIDEO_REMOVED == event.getType()) {
                //     Timber.e(new Exception("Event VIDEO_REMOVED added (for testing only)?"));
                // }

                if (thread == null)
                    startThread();
                else
                    events.notify();

                if (wait) {
                    boolean interrupted = false;
                    while (events.contains(event) && (thread != null)) {
                        try {
                            events.wait();
                        } catch (InterruptedException ie) {
                            interrupted = true;
                        }
                    }
                    if (interrupted)
                        Thread.currentThread().interrupt();
                }
            }
        }
    }

    /**
     * Removes a specific <code>VideoListener</code> from this <code>VideoNotifierSupport</code> to stop
     * receiving notifications when visual/video <code>Component</code>s are being added and removed.
     *
     * @param listener the <code>VideoListener</code> to be removed that no longer be notified when
     * visual/video <code>Component</code>s are being added or removed
     */
    public void removeVideoListener(VideoListener listener)
    {
        synchronized (listeners) {
            listeners.remove(listener);
        }
    }

    private void runInThread()
    {
        while (true) {
            VideoEvent event;

            synchronized (events) {
                long emptyTime = -1;
                boolean interrupted = false;

                while (events.isEmpty()) {
                    if (emptyTime == -1)
                        emptyTime = System.currentTimeMillis();
                    else {
                        long newEmptyTime = System.currentTimeMillis();
                        if ((newEmptyTime - emptyTime) >= THREAD_TIMEOUT) {
                            events.notify();
                            return;
                        }
                    }

                    try {
                        events.wait(THREAD_TIMEOUT);
                    } catch (InterruptedException ie) {
                        interrupted = true;
                    }
                }
                if (interrupted)
                    Thread.currentThread().interrupt();
                event = events.remove(0);
            }

            if (event != null) {
                try {
                    doFireVideoEvent(event);
                } catch (Throwable t) {
                    if (t instanceof ThreadDeath)
                        throw (ThreadDeath) t;
                }
                synchronized (events) {
                    events.notify();
                }
            }
        }
    }

    private void startThread()
    {
        thread = new Thread("VideoNotifierSupportThread")
        {
            @Override
            public void run()
            {
                try {
                    runInThread();
                } finally {
                    synchronized (events) {
                        if (Thread.currentThread().equals(thread)) {
                            thread = null;
                            if (events.isEmpty())
                                events.notify();
                            else
                                startThread();
                        }
                    }
                }
            }
        };
        thread.setDaemon(true);
        thread.start();
    }
}
