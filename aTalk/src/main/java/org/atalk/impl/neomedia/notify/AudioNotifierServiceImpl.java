/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.atalk.impl.neomedia.notify;

import org.atalk.impl.neomedia.NeomediaServiceUtils;
import org.atalk.impl.neomedia.device.AudioSystem;
import org.atalk.impl.neomedia.device.DeviceConfiguration;
import org.atalk.impl.neomedia.device.NoneAudioSystem;
import org.atalk.service.audionotifier.AudioNotifierService;
import org.atalk.service.audionotifier.SCAudioClip;

import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Callable;

import javax.media.CaptureDeviceInfo;

/**
 * The implementation of <code>AudioNotifierService</code>.
 *
 * @author Yana Stamcheva
 * @author Lyubomir Marinov
 */
public class AudioNotifierServiceImpl implements AudioNotifierService, PropertyChangeListener
{
    /**
     * The cache of <code>SCAudioClip</code> instances which we may reuse. The reuse is complex because
     * a <code>SCAudioClip</code> may be used by a single user at a time.
     */
    private Map<AudioKey, SCAudioClip> audioClips;

    /**
     * The <code>Object</code> which synchronizes the access to {@link #audioClips}.
     */
    private final Object audiosSyncRoot = new Object();

    /**
     * The <code>DeviceConfiguration</code> which provides information about the notify and playback
     * devices on which this instance plays <code>SCAudioClip</code>s.
     */
    private final DeviceConfiguration deviceConfiguration;

    /**
     * The indicator which determined whether <code>SCAudioClip</code>s are to be played by this
     * instance.
     */
    private boolean mute;

    /**
     * Initializes a new <code>AudioNotifierServiceImpl</code> instance.
     */
    public AudioNotifierServiceImpl()
    {
        this.deviceConfiguration = NeomediaServiceUtils.getMediaServiceImpl().getDeviceConfiguration();
        this.deviceConfiguration.addPropertyChangeListener(this);
    }

    /**
     * Checks whether the playback and notification configuration share the same device.
     *
     * @return are audio out and notifications using the same device.
     */
    public boolean audioOutAndNotificationsShareSameDevice()
    {
        AudioSystem audioSystem = getDeviceConfiguration().getAudioSystem();
        CaptureDeviceInfo notify = audioSystem.getSelectedDevice(AudioSystem.DataFlow.NOTIFY);
        CaptureDeviceInfo playback = audioSystem.getSelectedDevice(AudioSystem.DataFlow.PLAYBACK);

        if (notify == null)
            return (playback == null);
        else {
            if (playback == null)
                return false;
            else
                return notify.getLocator().equals(playback.getLocator());
        }
    }

    /**
     * Creates an SCAudioClip from the given URI and adds it to the list of available audio-s.
     * Uses notification device if any.
     *
     * @param uri the path where the audio file could be found
     * @return a newly created <code>SCAudioClip</code> from <code>uri</code>
     */
    public SCAudioClip createAudio(String uri)
    {
        return createAudio(uri, false);
    }

    /**
     * Creates an SCAudioClip from the given URI and adds it to the list of available audio-s.
     *
     * @param uri the path where the audio file could be found
     * @param playback use or not the playback device.
     * @return a newly created <code>SCAudioClip</code> from <code>uri</code>
     */
    public SCAudioClip createAudio(String uri, boolean playback)
    {
        SCAudioClip audio;
        synchronized (audiosSyncRoot) {
            final AudioKey key = new AudioKey(uri, playback);

            /*
             * While we want to reuse the SCAudioClip instances, they may be used by a single user
             * at a time. That's why we'll forget about them while they are in use, and we'll
             * reclaim them when they are no longer in use.
             */
            audio = (audioClips == null) ? null : audioClips.remove(key);
            if (audio == null) {
                try {
                    AudioSystem audioSystem = getDeviceConfiguration().getAudioSystem();
                    if (audioSystem == null) {
                        audio = new JavaSoundClipImpl(uri, this);
                    }
                    else if (NoneAudioSystem.LOCATOR_PROTOCOL.equalsIgnoreCase(audioSystem.getLocatorProtocol())) {
                        audio = null;
                    }
                    else {
                        audio = new AudioSystemClipImpl(uri, this, audioSystem, playback);
                    }
                } catch (Throwable t) {
                    if (t instanceof ThreadDeath)
                        throw (ThreadDeath) t;
                    else {
                        /*
                         * Could not initialize a new SCAudioClip instance to be played.
                         */
                        return null;
                    }
                }
            }

            /*
             * Make sure the SCAudioClip will be reclaimed for reuse when it is no longer in use.
             */
            if (audio != null) {
                if (audioClips == null)
                    audioClips = new HashMap<>();
                /*
                 * We have to return in the Map which was active at the time the SCAudioClip was
                 * initialized because it may have become invalid if the playback or notify audio
                 * device changed.
                 */
                final Map<AudioKey, SCAudioClip> finalAudios = audioClips;
                final SCAudioClip finalAudio = audio;

                audio = new SCAudioClip()
                {
                    /**
                     * Evaluates a specific <code>loopCondition</code> as defined by
                     * {@link SCAudioClip#play(int, Callable)}.
                     *
                     * @param loopCondition the <code>Callable&lt;Boolean&gt;</code> which represents the
                     * <code>loopCondition</code> to be evaluated
                     * @return {@link Boolean#FALSE} if <code>loopCondition</code> is <code>null</code>; otherwise,
                     * the value returned by invoking {@link Callable#call()} on the specified <code>loopCondition</code>
                     * @throws Exception if the specified <code>loopCondition</code> throws an <code>Exception</code>
                     */
                    private Boolean evaluateLoopCondition(Callable<Boolean> loopCondition)
                            throws Exception
                    {
                        /*
                         * SCAudioClip.play(int,Callable<Boolean>) is documented to play the
                         * SCAudioClip once only if the loopCondition is null. The same will be
                         * accomplished by returning Boolean.FALSE.
                         */
                        return (loopCondition == null) ? Boolean.FALSE : loopCondition.call();
                    }

                    /**
                     * {@inheritDoc}
                     *
                     * Returns the wrapped <code>SCAudioClip</code> into the cache from it has earlier
                     * been retrieved in order to allow its reuse.
                     */
                    @Override
                    protected void finalize()
                            throws Throwable
                    {
                        try {
                            synchronized (audioClips) {
                                finalAudios.put(key, finalAudio);
                            }
                        } finally {
                            super.finalize();
                        }
                    }

                    public void play()
                    {
                        /*
                         * SCAudioClip.play() is documented to behave as if loopInterval is
                         * negative and/or loopCondition is null. We have to take care that this
                         * instance does not get garbage collected until the finalAudio finishes
                         * playing so we will delegate to this instance's implementation of
                         * SCAudioClip.play(int,Callable<Boolean>) instead of to the finalAudio's.
                         */
                        play(-1, null);
                    }

                    public void play(int loopInterval, final Callable<Boolean> finalLoopCondition)
                    {
                        /*
                         * We have to make sure that this instance does not get garbage collected
                         * before the finalAudio finishes playing. The argument loopCondition of
                         * the method SCAudioClip.play(int,Callable<Boolean>) will live/be
                         * referenced during that time so we will use it to hold on to this
                         * instance.
                         */
                        Callable<Boolean> loopCondition = () -> evaluateLoopCondition(finalLoopCondition);
                        finalAudio.play(loopInterval, loopCondition);
                    }

                    public void stop()
                    {
                        finalAudio.stop();
                    }

                    /**
                     * Determines whether this audio is started i.e. a <code>play</code> method was
                     * invoked and no subsequent <code>stop</code> has been invoked yet.
                     *
                     * @return <code>true</code> if this audio is started; otherwise, <code>false</code>
                     */
                    public boolean isStarted()
                    {
                        return finalAudio.isStarted();
                    }
                };
            }
        }
        return audio;
    }

    /**
     * The device configuration.
     *
     * @return the deviceConfiguration
     */
    public DeviceConfiguration getDeviceConfiguration()
    {
        return deviceConfiguration;
    }

    /**
     * Returns <code>true</code> if the sound is currently disabled; <code>false</code>, otherwise.
     *
     * @return <code>true</code> if the sound is currently disabled; <code>false</code>, otherwise
     */
    public boolean isMute()
    {
        return mute;
    }

    /**
     * Listens for changes in notify device.
     *
     * @param ev the event that notify device has changed.
     */
    public void propertyChange(PropertyChangeEvent ev)
    {
        String propertyName = ev.getPropertyName();

        if (DeviceConfiguration.AUDIO_NOTIFY_DEVICE.equals(propertyName)
                || DeviceConfiguration.AUDIO_PLAYBACK_DEVICE.equals(propertyName)) {
            synchronized (audiosSyncRoot) {
                /*
                 * Make sure that the currently referenced SCAudioClips will not be reclaimed.
                 */
                audioClips = null;
            }
        }
    }

    /**
     * Enables or disables the sound in the application. If <code>false</code>, we try to restore all
     * looping sounds if any.
     *
     * @param mute when <code>true</code> disables the sound; otherwise, enables the sound.
     */
    public void setMute(boolean mute)
    {
        // TODO Auto-generated method stub
        this.mute = mute;
    }

    /**
     * Implements the key of {@link AudioNotifierServiceImpl#audioClips}. Combines the <code>uri</code> of
     * the <code>SCAudioClip</code> with the indicator which determines whether the
     * <code>SCAudioClip</code> in question uses the playback or the notify audio device.
     */
    private static class AudioKey
    {
        /**
         * Is it playback?
         */
        private final boolean playback;

        /**
         * The uri.
         */
        final String uri;

        /**
         * Initializes a new <code>AudioKey</code> instance.
         *
         * @param uri
         * @param playback
         */
        private AudioKey(String uri, boolean playback)
        {
            this.uri = uri;
            this.playback = playback;
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public boolean equals(Object o)
        {
            if (o == this)
                return true;
            if (!(o instanceof AudioKey))
                return false;

            AudioKey that = (AudioKey) o;
            return (playback == that.playback)
                    && ((uri == null) ? (that.uri == null) : uri.equals(that.uri));
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public int hashCode()
        {
            return ((uri == null) ? 0 : uri.hashCode()) + (playback ? 1 : 0);
        }
    }
}
