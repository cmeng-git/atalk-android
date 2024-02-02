/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.atalk.service.audionotifier;

import android.content.Context;
import android.media.Ringtone;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Build;

import org.atalk.android.aTalkApp;
import org.atalk.impl.androidresources.AndroidResourceServiceImpl;

import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import timber.log.Timber;

/**
 * An abstract base implementation of {@link SCAudioClip} which is provided in order to aid
 * implementers by allowing them to extend <code>AbstractSCAudioClip</code> and focus on the task of
 * playing actual audio once.
 *
 * @author Damian Minkov
 * @author Lyubomir Marinov
 * @author Eng Chong Meng
 */
public abstract class AbstractSCAudioClip implements SCAudioClip
{
    /**
     * The thread pool used by the <code>AbstractSCAudioClip</code> instances in order to reduce the
     * impact of thread creation/initialization.
     */
    private static ExecutorService executorService;

    /**
     * The <code>AudioNotifierService</code> which has initialized this instance.
     * <code>AbstractSCAudioClip</code> monitors its <code>mute</code> property/state in order to silence
     * the played audio as appropriate/necessary.
     */
    private final AudioNotifierService audioNotifier;

    private Runnable command;

    /**
     * The indicator which determines whether this instance was marked invalid.
     */
    private boolean invalid;

    /**
     * The indicator which determines whether this instance plays the audio it represents in a loop.
     */
    private boolean looping;

    /**
     * The interval of time in milliseconds between consecutive plays of this audio in a loop. If
     * negative, this audio is played once only. If non-negative, this audio may still be played
     * once only if the <code>loopCondition</code> specified to {@link #play(int, Callable)} is
     * <code>null</code> or its invocation fails.
     */
    private int loopInterval;

    /**
     * The indicator which determines whether the playback of this audio is started.
     */
    private boolean started;

    /**
     * The <code>Object</code> used for internal synchronization purposes which arise because this
     * instance does the actual playback of audio in a separate thread.
     * <p>
     * The synchronization root is exposed to extenders in case they would like to, for example,
     * get notified as soon as possible when this instance gets stopped.
     */
    protected final Object sync = new Object();

    /**
     * The <code>String</code> uri of the audio to be played by this instance.
     * <code>AbstractSCAudioClip</code> does not use it and just remembers it in order to make it available to extenders.
     */
    protected final String uri;

    /**
     * An instance for playback of android OS ringTone
     */
    private Ringtone ringtone;

    // private int currentVolume;

    protected AbstractSCAudioClip(String uri, AudioNotifierService audioNotifier)
    {
        this.uri = uri;
        this.audioNotifier = audioNotifier;
        // Timber.e(new Exception("AbstractSCAudioClip Init: " + uri));
    }

    /**
     * Notifies this instance that its execution in its background/separate thread dedicated to
     * the playback of this audio is about to start playing this audio for the first time.
     * Regardless of whether this instance is to be played once or multiple times in a loop, the
     * method is called once in order to allow extenders/implementers to perform one-time
     * initialization before this audio starts playing. The <code>AbstractSCAudioClip</code>
     * implementation does nothing.
     */
    protected void enterRunInPlayThread()
    {
    }

    /**
     * Notifies this instance that its execution in its background/separate thread dedicated to
     * the playback of this audio is about to stop playing this audio once. Regardless of whether
     * this instance is to be played once or multiple times in a loop, the method is called once
     * in order to allow extenders/implementers to perform one-time cleanup after this audio
     * stops playing. The <code>AbstractSCAudioClip</code> implementation does nothing.
     */
    protected void exitRunInPlayThread()
    {
    }

    /**
     * Notifies this instance that its execution in its background/separate thread dedicated to
     * the playback of this audio is about the start playing this audio once. If this audio is to
     * be played in a loop, the method is invoked at the beginning of each iteration of the loop.
     * Allows extenders/implementers to perform per-loop iteration initialization. The
     * <code>AbstractSCAudioClip</code> implementation does nothing.
     */
    private void enterRunOnceInPlayThread()
    {
    }

    /**
     * Notifies this instance that its execution in its background/separate thread dedicated to
     * the playback of this audio is about to stop playing this audio. If this audio is to be
     * played in a loop, the method is called at the end of each iteration of the loop. Allows
     * extenders/implementers to perform per-loop iteration cleanup. The
     * <code>AbstractSCAudioClip</code> implementation does nothing.
     */
    protected void exitRunOnceInPlayThread()
    {
    }

    /**
     * Plays this audio once.
     *
     * @return <code>true</code> if subsequent plays of this audio and, respectively, the method are
     * to be invoked if this audio is to be played in a loop; otherwise, <code>false</code>. The
     * value reflects an implementation-specific loop condition, is not dependent on
     * <code>loopInterval</code> and <code>loopCondition</code> and is combined with the latter in order
     * to determine whether there will be a subsequent iteration of the playback loop.
     */
    protected abstract boolean runOnceInPlayThread();

    /**
     * {@inheritDoc}
     * <p>
     * Delegates to {@link #play(int, Callable)} with <code>loopInterval</code> <code>-1</code> and
     * <code>loopCondition</code> <code>null</code> in order to conform with the contract for the
     * behavior of this method specified by the interface <code>SCAudioClip</code>.
     */
    public void play()
    {
        play(-1, null);
    }

    /**
     * {@inheritDoc}
     */
    public void play(int loopInterval, final Callable<Boolean> loopCondition)
    {
        // Timber.w(new Exception("Ring tone playing start"));
        if ((loopInterval >= 0) && (loopCondition == null))
            loopInterval = -1;

        synchronized (sync) {
            if (command != null)
                return;

            setLoopInterval(loopInterval);
            setLooping(loopInterval >= 0);

            /*
             * We use a thread pool shared among all AbstractSCAudioClip instances in order to
             * reduce the impact of thread creation/initialization.
             */
            ExecutorService executorService;

            synchronized (AbstractSCAudioClip.class) {
                if (AbstractSCAudioClip.executorService == null) {
                    AbstractSCAudioClip.executorService = Executors.newCachedThreadPool();
                }
                executorService = AbstractSCAudioClip.executorService;
            }

            try {
                started = false;
                command = new Runnable()
                {
                    public void run()
                    {
                        try {
                            synchronized (sync) {
                                /*
                                 * We have to wait for play(int,Callable<Boolean>) to let go of sync
                                 * i.e. be ready with setting up the whole AbstractSCAudioClip state;
                                 * otherwise, this Runnable will most likely prematurely seize to exist.
                                 */
                                if (!equals(command))
                                    return;
                            }
                            if (uri.startsWith(AndroidResourceServiceImpl.PROTOCOL)) {
                                // setNotificationVolume();
                                runInPlayThread(loopCondition);
                            }
                            // use runInPlayRingtoneThread if it is for android RingTone playing
                            else {
                                runInPlayRingtoneThread(loopCondition);
                            }
                        } finally {
                            synchronized (sync) {
                                if (equals(command)) {
                                    command = null;
                                    started = false;
                                    sync.notifyAll();
                                }
                            }
                        }
                    }
                };
                executorService.execute(command);
                started = true;
            } finally {
                if (!started)
                    command = null;
                sync.notifyAll();
            }
        }
    }

    /**
     * Determines whether this audio is started i.e. a <code>play</code> method was invoked and no
     * subsequent <code>stop</code> has been invoked yet.
     *
     * @return <code>true</code> if this audio is started; otherwise, <code>false</code>
     */
    public boolean isStarted()
    {
        synchronized (sync) {
            return started;
        }
    }

    /**
     * Runs in a background/separate thread dedicated to the actual playback of this audio and
     * plays this audio once or in a loop.
     *
     * @param loopCondition a <code>Callback&lt;Boolean&gt;</code> which represents the condition on which this
     * audio will play more than once. If <code>null</code>, this audio will play once only. If an invocation of
     * <code>loopCondition</code> throws a <code>Throwable</code>, this audio will discontinue playing.
     */
    private void runInPlayThread(Callable<Boolean> loopCondition)
    {
        enterRunInPlayThread();
        try {
            boolean interrupted = false;

            while (isStarted()) {
                if (audioNotifier.isMute()) {
                    /*
                     * If the AudioNotifierService has muted the sounds, we will have to really
                     * wait a bit in order to not fall into a busy wait.
                     */
                    synchronized (sync) {
                        try {
                            sync.wait(500);
                        } catch (InterruptedException ie) {
                            interrupted = true;
                        }
                    }
                }
                else {
                    enterRunOnceInPlayThread();
                    try {
                        if (!runOnceInPlayThread())
                            break;
                    } finally {
                        exitRunOnceInPlayThread();
                    }
                }

                if (!isLooping())
                    break;

                synchronized (sync) {
                    /*
                     * We may have waited to acquire sync. Before beginning the wait for
                     * loopInterval, make sure we should continue.
                     */
                    if (!isStarted())
                        break;

                    try {
                        /*
                         * XXX The value 0 means that this instance should loop playing without
                         * waiting, but it means infinity to Object.wait(long).
                         */
                        int loopInterval = getLoopInterval();
                        if (loopInterval > 0)
                            sync.wait(loopInterval);

                    } catch (InterruptedException ie) {
                        interrupted = true;
                    }
                }

                /*
                 * After this audio has been played once, loopCondition should be consulted to
                 * approve each subsequent iteration of the loop. Before invoking loopCondition
                 * which may take noticeable time to execute, make sure that this instance has
                 * not been stopped while it waited for loopInterval.
                 */
                if (!isStarted())
                    break;

                if (loopCondition == null) {
                    /*
                     * The interface contract is that this audio plays once only if the loopCondition is null.
                     */
                    break;
                }

                /*
                 * The contract of the SCAudioClip interface with respect to loopCondition is that
                 * the loop will continue only if loopCondition successfully and explicitly evaluates to true.
                 */
                boolean loop = false;
                try {
                    loop = loopCondition.call();
                } catch (Throwable t) {
                    if (t instanceof ThreadDeath)
                        throw (ThreadDeath) t;
                    /*
                     * If loopCondition fails to successfully and explicitly evaluate to true,
                     * this audio should seize to play in a loop. Otherwise, there is a risk that
                     * whoever requested this audio to be played in a loop and provided the
                     * loopCondition will continue to play it forever.
                     */
                }

                if (!loop) {
                    /*
                     * The loopCondition failed to successfully and explicitly evaluate to true so
                     * the loop will not continue.
                     */
                    break;
                }
            }
            if (interrupted)
                Thread.currentThread().interrupt();
        } finally {
            exitRunInPlayThread();
        }
    }

    // The notification volume for aTalk - no good to implement as it affect all notifications
    //    private void setNotificationVolume() {
    //        AudioManager audioManager = (AudioManager)  aTalkApp.getInstance().getSystemService(Context.AUDIO_SERVICE);
    //        currentVolume = audioManager.getStreamVolume(AudioManager.STREAM_NOTIFICATION);
    //        Timber.d("Current volume: %s", currentVolume);
    //        audioManager.setStreamVolume(AudioManager.STREAM_NOTIFICATION, audioManager.getStreamVolume(AudioManager.STREAM_MUSIC), 0);
    //    }
    //
    //    private void restoreNotificationVolume() {
    //        AudioManager audioManager = (AudioManager)  aTalkApp.getInstance().getSystemService(Context.AUDIO_SERVICE);
    //        Timber.d("Current volume restore: %s", currentVolume);
    //        audioManager.setStreamVolume(AudioManager.STREAM_NOTIFICATION, currentVolume, 0);
    //    }

    /**
     * Runs in a background/separate thread dedicated to the actual playback of the android ringtone
     * Plays this audio once or in a loop is controlled by RingTone player. So setup to check external
     * condition changes to stop playing if triggered
     * There is always a wait of 3 seconds for non-loop tone to complete playing at least once
     *
     * @param loopCondition a <code>Callback&lt;Boolean&gt;</code> which represents the condition on which this
     * audio will play more than once. If <code>null</code>, this audio will play once only. If an invocation of
     * <code>loopCondition</code> throws a <code>Throwable</code>, this audio will discontinue playing.
     */
    private void runInPlayRingtoneThread(Callable<Boolean> loopCondition)
    {
        try {
            boolean interrupted = false;

            while (isStarted()) {
                if (audioNotifier.isMute()) {
                    /*
                     * If the AudioNotifierService has muted the sounds, we will have to really
                     * wait a bit in order to not fall into a busy wait.
                     */
                    synchronized (sync) {
                        try {
                            sync.wait(500);
                        } catch (InterruptedException ie) {
                            interrupted = true;
                        }
                    }
                }
                else {
                    if ((ringtone == null) || !ringtone.isPlaying()) {
                        try {
                            if (!ringTonePlayBack(loopCondition))
                                break;
                        } catch (Exception ex) {
                            break;
                        }
                    }
                }

                // Do nothing for ringtone playback assuming it is already setup to play only once
                // if (!isLooping()) break;

                synchronized (sync) {
                    /*
                     * We may have waited to acquire sync. Before beginning the wait for loopInterval (3000ms);
                     * to monitor the progress of ringtone playback after playing back for 3s.
                     */
                    if (!isStarted())
                        break;

                    /*
                     * Playback by ringTone is auto-looping; but need to wait for some time before proceed;
                     * This is to allow notification non-loop alert to play for at least 3 seconds
                     */
                    try {
                        sync.wait(3000);
                    } catch (InterruptedException ie) {
                        interrupted = true;
                    }
                }

                /*
                 * After this audio has started playing, loopCondition should be consulted at regular interval to
                 * approve continue ringtone playing. Before invoking loopCondition which may take noticeable time
                 * to execute, make sure that this instance has not been stopped while it waited for loopInterval.
                 */
                if (!isStarted()) {
                    break;
                }

                /*
                 * The interface contract is that this audio plays once only if the loopCondition is null.
                 */
                if (loopCondition == null) {
                    break;
                }

                /*
                 * The contract of the SCAudioClip interface with respect to loopCondition is that
                 * the loop will continue only if loopCondition successfully and explicitly evaluates to true.
                 */
                try {
                    if (!loopCondition.call())
                        break;
                } catch (Exception t) {
                    /*
                     * If loopCondition fails to successfully and explicitly evaluate to true,
                     * this audio should cease to play in a loop. Otherwise, there is a risk that
                     * whoever requested this audio to be played in a loop and provided the
                     * loopCondition will continue to play it forever.
                     */
                    break;
                }
            }
            if (interrupted)
                Thread.currentThread().interrupt();
        } finally {
            ringToneStop();
        }
    }

    /**
     * Use RingTone to play android OS ringtone; AudioSystemClipImpl support only Wav media
     * Warn: looping and setVolume support only for android-P and above
     *
     * @param loopCondition check for loop
     */
    private boolean ringTonePlayBack(Callable<Boolean> loopCondition)
            throws Exception
    {
        // stop previously play ringTone if any and create new ringTone
        if (ringtone != null) {
            try {
                ringtone.stop();
                ringtone = null;
            } catch (IllegalStateException ex) {
                // just ignore any ringtone stop exception
                Timber.w("End existing ringtone error: %s", ex.getMessage());
            }
        }

        Context ctx = aTalkApp.getInstance();
        ringtone = RingtoneManager.getRingtone(ctx, Uri.parse(uri));
        if (ringtone == null)
            return false;

        boolean loop = (loopCondition != null) && loopCondition.call() && (getLoopInterval() > 0);

        // cmeng: seem android ring tone already follow the system ring tone setting or mute state.
        // AudioManager am = aTalkApp.getAudioManager();
        // int currentVolume = am.getStreamVolume(AudioManager.STREAM_RING);
        // am.setStreamVolume(AudioManager.STREAM_RING, currentVolume, AudioManager.FLAG_SHOW_UI);
        // Timber.d(new Exception(), "RingTone playing loop = %s; volume = %s; %s",
        //        loop, currentVolume, ringtone.getTitle(ctx));

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            ringtone.setLooping(loop);  // may not be necessary as this is taken care by RingTone playing
            /*
             * Set the ringTone playback volume according to system RingTone setting
             * Seem above am.setStreamVolume() is also working for all android devices
             */
            // int maxVolume = am.getStreamMaxVolume(AudioManager.STREAM_RING);
            // float ringVolume = (float) (1.0 - Math.log(maxVolume - currentVolume) / Math.log(maxVolume));
            // Timber.d("RingTone playing volume %s/%s, %s", currentVolume, maxVolume, ringVolume);
            // ringtone.setVolume(ringVolume);
        }
        ringtone.play();
        return true;
    }

    private void ringToneStop()
    {
        if (!uri.startsWith(AndroidResourceServiceImpl.PROTOCOL)) {
            if (ringtone != null) {
                // Timber.d("Ring tone playback stopping: %s = %s", ringtone.getTitle(aTalkApp.getInstance()), uri);
                try {
                    ringtone.stop();
                    ringtone = null;
                } catch (IllegalStateException ex) {
                    Timber.w("Ringtone stopping exception %s", ex.getMessage());
                }
            }
        }
//        else {
//            restoreNotificationVolume();
//        }
    }

    /**
     * Determines whether this instance is invalid. <code>AbstractSCAudioClip</code> does not use the
     * <code>invalid</code> property/state of this instance and merely remembers the value which was
     * set on it by {@link #setInvalid(boolean)}. The default value is <code>false</code> i.e. this
     * instance is valid by default.
     *
     * @return <code>true</code> if this instance is invalid; otherwise, <code>false</code>
     */
    public boolean isInvalid()
    {
        return invalid;
    }

    /**
     * Sets the indicator which determines whether this instance is invalid.
     * <code>AbstractSCAudioClip</code> does not use the <code>invalid</code> property/state of this
     * instance and merely remembers the value which was set on it so that it can be retrieved by
     * {@link #isInvalid()}. The default value is <code>false</code> i.e. this
     * instance is valid by default.
     *
     * @param invalid <code>true</code> to mark this instance invalid or <code>false</code> to mark it valid
     */
    public void setInvalid(boolean invalid)
    {
        this.invalid = invalid;
    }

    /**
     * Determines whether this instance plays the audio it represents in a loop.
     *
     * @return <code>true</code> if this instance plays the audio it represents in a loop; <code>false</code>, otherwise
     */
    public boolean isLooping()
    {
        return looping;
    }

    /**
     * Sets the indicator which determines whether this audio is to play in a loop. Generally,
     * public invocation of the method is not necessary because the looping is controlled by the
     * <code>loopInterval</code> property of this instance and the <code>loopInterval</code> and
     * <code>loopCondition</code> parameters of {@link #play(int, Callable)} anyway.
     *
     * @param looping <code>true</code> to mark this instance that it should play the audio it represents in a
     * loop; otherwise, <code>false</code>
     */
    public void setLooping(boolean looping)
    {
        synchronized (sync) {
            if (this.looping != looping) {
                this.looping = looping;
                sync.notifyAll();
            }
        }
    }

    /**
     * Gets the interval of time in milliseconds between consecutive plays of this audio.
     *
     * @return the interval of time in milliseconds between consecutive plays of this audio. If
     * negative, this audio will not be played in a loop and will be played once only.
     */
    private int getLoopInterval()
    {
        return loopInterval;
    }

    /**
     * Sets the interval of time in milliseconds between consecutive plays of this audio in a loop
     * . If negative, this audio is played once only. If non-negative, this audio may still be
     * played once only if the <code>loopCondition</code> specified to {@link #play(int, Callable)}
     * is <code>null</code> or its invocation fails.
     *
     * @param loopInterval the interval of time in milliseconds between consecutive plays of this audio in a loop
     * to be set on this instance
     */
    private void setLoopInterval(int loopInterval)
    {
        synchronized (sync) {
            if (this.loopInterval != loopInterval) {
                this.loopInterval = loopInterval;
                sync.notifyAll();
            }
        }
    }

    /**
     * {@inheritDoc}
     */
    public void stop()
    {
        ringToneStop();
        internalStop();
        setLooping(false);
    }

    /**
     * Stops this audio without setting the isLooping property in the case of a looping audio. The
     * AudioNotifier uses this method to stop the audio when setMute(true) is invoked. This
     * allows us to restore all looping audios when the sound is restored by calling setMute(false).
     */
    protected void internalStop()
    {
        boolean interrupted = false;

        synchronized (sync) {
            started = false;
            sync.notifyAll();

            while (command != null) {
                try {
                    /*
                     * Technically, we do not need a timeout. If a notifyAll() is not called to wake us up,
                     * then we will likely already be in trouble. Anyway, use a timeout just in case.
                     */
                    sync.wait(500);
                } catch (InterruptedException ie) {
                    interrupted = true;
                }
            }
        }
        if (interrupted)
            Thread.currentThread().interrupt();
    }
}
