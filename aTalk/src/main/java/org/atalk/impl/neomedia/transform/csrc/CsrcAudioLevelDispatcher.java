/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.atalk.impl.neomedia.transform.csrc;

import org.atalk.impl.neomedia.AudioMediaStreamImpl;
import org.atalk.util.concurrent.ExecutorUtils;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

/**
 * A simple dispatcher that handles new audio levels reported from incoming
 * RTP packets and then asynchronously delivers them to associated
 * <code>AudioMediaStreamImpl</code>. The asynchronous processing is necessary
 * due to time sensitive nature of incoming RTP packets.
 *
 * @author Emil Ivov
 * @author Lyubomir Marinov
 * @author Yura Yaroshevich
 */
public class CsrcAudioLevelDispatcher
{
    /**
     * The executor service to asynchronously execute method which delivers
     * audio level updates to <code>AudioMediaStreamImpl</code>
     */
    private static final ExecutorService threadPool
            = ExecutorUtils.newCachedThreadPool(true, CsrcAudioLevelDispatcher.class.getName() + "-");

    /**
     * The levels added to this instance (by the <code>reverseTransform</code>
     * method of a <code>PacketTransformer</code> implementation) last.
     */
    private final AtomicReference<long[]> levels = new AtomicReference<>();

    /**
     * The <code>AudioMediaStreamImpl</code> which listens to this event dispatcher.
     * If <code>null</code>, this event dispatcher is stopped. If non-<code>null</code>,
     * this event dispatcher is started.
     */
    private final AudioMediaStreamImpl mediaStream;

    /**
     * Indicates that {@link CsrcAudioLevelDispatcher} should continue delivery
     * of audio levels updates to {@link #mediaStream} on separate thread.
     */
    private final AtomicBoolean running = new AtomicBoolean(true);

    /**
     * A cached instance of {@link #deliverAudioLevelsToMediaStream()} runnable
     * to reduce allocations.
     */
    private final Runnable deliverRunnable = this::deliverAudioLevelsToMediaStream;

    /**
     * Initializes a new <code>CsrcAudioLevelDispatcher</code> to dispatch events
     * to a specific <code>AudioMediaStreamImpl</code>.
     *
     * @param mediaStream the <code>AudioMediaStreamImpl</code> to which the new instance is to dispatch events
     */
    public CsrcAudioLevelDispatcher(AudioMediaStreamImpl mediaStream)
    {
        if (mediaStream == null) {
            throw new IllegalArgumentException("mediaStream is null");
        }
        this.mediaStream = mediaStream;
    }

    /**
     * A level matrix that we should deliver to our media stream and its listeners in a separate thread.
     *
     * @param levels the levels that we'd like to queue for processing.
     * @param rtpTime the timestamp carried by the RTP packet which carries the specified <code>levels</code>
     */
    public void addLevels(long[] levels, long rtpTime)
    {
        if (!running.get()) {
            return;
        }

        this.levels.set(levels);

        // submit asynchronous delivery of audio levels update
        threadPool.execute(deliverRunnable);
    }

    /**
     * Closes current {@link CsrcAudioLevelDispatcher} to prevent further
     * audio level updates delivery to associated media stream.
     */
    public void close()
    {
        running.set(false);
        levels.set(null);
    }

    /**
     * Delivers last reported audio levels to associated {@link #mediaStream}
     */
    private void deliverAudioLevelsToMediaStream()
    {
        if (!running.get()) {
            return;
        }

        // read and reset latest audio levels
        final long[] latestAudioLevels = levels.getAndSet(null);

        if (latestAudioLevels != null) {
            mediaStream.audioLevelsReceived(latestAudioLevels);
        }
    }
}
