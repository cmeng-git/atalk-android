/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.atalk.service.audionotifier;

import java.util.concurrent.Callable;

/**
 * Represents an audio clip which could be played (optionally, in a loop) and stopped..
 *
 * @author Yana Stamcheva
 * @author Lyubomir Marinov
 * @author Eng Chong Meng
 */
public interface SCAudioClip
{
    /**
     * Starts playing this audio once only. The method behaves as if {@link #play(int, Callable)}
     * was invoked with a negative <code>loopInterval</code> and/or <code>null</code> <code>loopCondition</code>.
     */
    void play();

    /**
     * Starts playing this audio. Optionally, the playback is looped.
     *
     * @param loopInterval the interval of time in milliseconds between consecutive plays of this audio. If
     * negative, this audio is played once only and <code>loopCondition</code> is ignored.
     * @param loopCondition a <code>Callable</code> which is called at the beginning of each iteration of looped
     * playback of this audio except the first one to determine whether to continue the loop.
     * If <code>loopInterval</code> is negative or <code>loopCondition</code> is <code>null</code>,
     * this audio is played once only.
     */
    void play(int loopInterval, Callable<Boolean> loopCondition);

    /**
     * Stops playing this audio.
     */
    void stop();

    /**
     * Determines whether this audio is started i.e. a <code>play</code> method was invoked and no
     * subsequent <code>stop</code> has been invoked yet.
     *
     * @return <code>true</code> if this audio is started; otherwise, <code>false</code>
     */
    boolean isStarted();
}
