/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.atalk.impl.neomedia.device;

import org.atalk.impl.neomedia.audiolevel.AudioLevelEffect;
import org.atalk.impl.neomedia.audiolevel.AudioLevelEffect2;
import org.atalk.impl.neomedia.jmfext.media.renderer.audio.AbstractAudioRenderer;
import org.atalk.service.neomedia.VolumeControl;
import org.atalk.service.neomedia.event.SimpleAudioLevelListener;

import javax.media.Codec;
import javax.media.ConfigureCompleteEvent;
import javax.media.ControllerEvent;
import javax.media.Player;
import javax.media.Processor;
import javax.media.Renderer;
import javax.media.UnsupportedPlugInException;
import javax.media.control.TrackControl;
import javax.media.format.AudioFormat;

import timber.log.Timber;

/**
 * Extends <code>MediaDeviceSession</code> to add audio-specific functionality.
 *
 * @author Emil Ivov
 * @author Damian Minkov
 * @author Lyubomir Marinov
 * @author Boris Grozev
 * @author Eng Chong Meng
 */
public class AudioMediaDeviceSession extends MediaDeviceSession
{
    /**
     * The <code>Effect</code> that we will register with our <code>DataSource</code> in order to measure
     * the audio levels of the local user.
     */
    private final AudioLevelEffect localUserAudioLevelEffect = new AudioLevelEffect();

    /**
     * The <code>Effect</code> that we will register with our output data source in order to measure the
     * outgoing audio levels.
     */
    private AudioLevelEffect2 outputAudioLevelEffect = null;

    /**
     * The <code>VolumeControl</code> which is to control the volume (level) of the audio (to be) played
     * back by this instance.
     */
    private VolumeControl outputVolumeControl;

    /**
     * The effect that we will register with our stream in order to measure audio levels of the remote user audio.
     */
    private final AudioLevelEffect streamAudioLevelEffect = new AudioLevelEffect();

    /**
     * Initializes a new <code>MediaDeviceSession</code> instance which is to represent the use of a
     * specific <code>MediaDevice</code> by a <code>MediaStream</code>.
     *
     * @param device the <code>MediaDevice</code> the use of which by a <code>MediaStream</code> is to be
     * represented by the new instance
     */
    protected AudioMediaDeviceSession(AbstractMediaDevice device)
    {
        super(device);
    }

    /**
     * Copies the playback part of a specific <code>MediaDeviceSession</code> into this instance.
     *
     * @param deviceSession the <code>MediaDeviceSession</code> to copy the playback part of into this instance
     */
    @Override
    public void copyPlayback(MediaDeviceSession deviceSession)
    {
        AudioMediaDeviceSession amds = (AudioMediaDeviceSession) deviceSession;
        setStreamAudioLevelListener(amds.streamAudioLevelEffect.getAudioLevelListener());
        setLocalUserAudioLevelListener(amds.localUserAudioLevelEffect.getAudioLevelListener());
    }

    /**
     * {@inheritDoc}
     *
     * Overrides the super implementation in order to configure the <code>VolumeControl</code> of the
     * returned <code>Renderer</code> for the purposes of having call/telephony conference-specific volume (levels).
     */
    @Override
    protected Renderer createRenderer(Player player, TrackControl trackControl)
    {
        Renderer renderer = super.createRenderer(player, trackControl);
        if (renderer != null)
            setVolumeControl(renderer, outputVolumeControl);
        return renderer;
    }

    /**
     * Returns the last audio level that was measured by this device session for the specified <code>ssrc</code>.
     *
     * @param ssrc the SSRC ID whose last measured audio level we'd like to retrieve.
     * @return the audio level that was last measured for the specified <code>ssrc</code> or <code>-1</code>
     * if no level has been cached for that ID.
     */
    public int getLastMeasuredAudioLevel(long ssrc)
    {
        return -1;
    }

    /**
     * Returns the last audio level that was measured by the underlying mixer for local user.
     *
     * @return the audio level that was last measured for the local user.
     */
    public int getLastMeasuredLocalUserAudioLevel()
    {
        return -1;
    }

    /**
     * Called by {@link MediaDeviceSession#playerControllerUpdate(ControllerEvent event)} when the
     * player associated with this session's <code>ReceiveStream</code> moves enters the
     * <code>Configured</code> state, so we use the occasion to add our audio level effect.
     *
     * @param player the <code>Player</code> which is the source of a <code>ConfigureCompleteEvent</code>
     * @see MediaDeviceSession#playerConfigureComplete(Processor)
     */
    @Override
    protected void playerConfigureComplete(Processor player)
    {
        super.playerConfigureComplete(player);
        TrackControl[] tcs = player.getTrackControls();
        if (tcs != null) {
            for (TrackControl tc : tcs) {
                if (tc.getFormat() instanceof AudioFormat) {
                    // Assume there is only one audio track.
                    try {
                        registerStreamAudioLevelJMFEffect(tc);
                    } catch (UnsupportedPlugInException upie) {
                        Timber.e(upie, "Failed to register stream audio level Effect");
                    }
                    break;
                }
            }
        }
    }

    /**
     * Gets notified about <code>ControllerEvent</code>s generated by the processor reading our capture
     * data source, calls the corresponding method from the parent class so that it would initialize
     * the processor and then adds the level effect for the local user audio levels.
     *
     * @param event the <code>ControllerEvent</code> specifying the <code>Controller</code> which is the source of
     * the event and the very type of the event
     */
    @Override
    protected void processorControllerUpdate(ControllerEvent event)
    {
        super.processorControllerUpdate(event);

        // when using translator we do not want any audio level effect
        if (useTranslator) {
            return;
        }

        if (event instanceof ConfigureCompleteEvent) {
            Processor processor = (Processor) event.getSourceController();
            if (processor != null)
                registerLocalUserAudioLevelEffect(processor);
        }
    }

    /**
     * Creates an audio level effect and add its to the codec chain of the <code>TrackControl</code>
     * assuming that it only contains a single track.
     *
     * @param processor the processor on which track control we need to register a level effect with.
     */
    protected void registerLocalUserAudioLevelEffect(Processor processor)
    {
        // we register the effect regardless of whether or not we have any listeners at this point because we won't get
        // a second chance. however the effect would do next to nothing unless we register a first listener with it.
        //
        // XXX: i am assuming that a single effect could be reused multiple times
        // if that turns out not to be the case we need to create a new instance here.

        // here we add sound level indicator for captured mediafrom the microphone if there are interested listeners
        try {
            TrackControl tcs[] = processor.getTrackControls();
            if (tcs != null) {
                for (TrackControl tc : tcs) {
                    if (tc.getFormat() instanceof AudioFormat) {
                        // we assume a single track
                        tc.setCodecChain(new Codec[]{localUserAudioLevelEffect});
                        break;
                    }
                }
            }
        } catch (UnsupportedPlugInException ex) {
            Timber.e(ex, "Effects are not supported by the datasource.");
        }
    }

    /**
     * Adds an audio level effect to the tracks of the specified <code>trackControl</code> and so that
     * we would notify interested listeners of audio level changes.
     *
     * @param trackControl the <code>TrackControl</code> where we need to register a level effect that would measure
     * the audio levels of the <code>ReceiveStream</code> associated with this class.
     * @throws UnsupportedPlugInException if we fail to add our sound level effect to the track control of
     * <code>mediaStream</code>'s processor.
     */
    private void registerStreamAudioLevelJMFEffect(TrackControl trackControl)
            throws UnsupportedPlugInException
    {
        // we register the effect regardless of whether or not we have any
        // listeners at this point because we won't get a second chance.
        // however the effect would do next to nothing unless we register a
        // first listener with it. Assume there is only one audio track
        trackControl.setCodecChain(new Codec[]{streamAudioLevelEffect});
    }

    /**
     * Sets the <code>SimpleAudioLevelListener</code> that this session should be notifying about
     * changes in local audio level related information. This class only supports a single listener
     * for audio changes per source (i.e. stream or data source). Audio changes are generally quite
     * time intensive (~ 50 per second) so we are doing this in order to reduce the number of
     * objects associated with the process (such as event instances listener list iterators and sync copies).
     *
     * @param listener the <code>SimpleAudioLevelListener</code> to add
     */
    public void setLocalUserAudioLevelListener(SimpleAudioLevelListener listener)
    {
        if (useTranslator) {
            return;
        }

        localUserAudioLevelEffect.setAudioLevelListener(listener);
    }

    /**
     * Sets the <code>VolumeControl</code> which is to control the volume (level) of the audio (to be)
     * played back by this instance.
     *
     * @param outputVolumeControl the <code>VolumeControl</code> which is to be control the volume (level) of the audio (to
     * be) played back by this instance
     */
    public void setOutputVolumeControl(VolumeControl outputVolumeControl)
    {
        this.outputVolumeControl = outputVolumeControl;
    }

    /**
     * Sets <code>listener</code> as the <code>SimpleAudioLevelListener</code> that we are going to notify
     * every time a change occurs in the audio level of the media that this device session is
     * receiving from the remote party. This class only supports a single listener for audio changes
     * per source (i.e. stream or data source). Audio changes are generally quite time intensive (~
     * 50 per second) so we are doing this in order to reduce the number of objects associated with
     * the process (such as event instances listener list iterators and sync copies).
     *
     * @param listener the <code>SimpleAudioLevelListener</code> that we want notified for audio level changes in
     * the remote participant's media.
     */
    public void setStreamAudioLevelListener(SimpleAudioLevelListener listener)
    {
        if (useTranslator) {
            return;
        }
        streamAudioLevelEffect.setAudioLevelListener(listener);
    }

    /**
     * Implements a utility which facilitates setting a specific <code>VolumeControl</code> on a
     * specific <code>Renderer</code> for the purposes of control over the volume (level) of the audio
     * (to be) played back by the specified <code>Renderer</code>.
     *
     * @param renderer the <code>Renderer</code> on which the specified <code>volumeControl</code> is to be set
     * @param volumeControl the <code>VolumeControl</code> to be set on the specified <code>renderer</code>
     */
    public static void setVolumeControl(Renderer renderer, VolumeControl volumeControl)
    {
        if (renderer instanceof AbstractAudioRenderer) {
            AbstractAudioRenderer<?> abstractAudioRenderer = (AbstractAudioRenderer<?>) renderer;
            abstractAudioRenderer.setVolumeControl(volumeControl);
        }
    }

    /**
     * Performs additional configuration on the <code>Processor</code>, after it is <code>configure</code>d,
     * but before it is <code>realize</code>d. Adds the <code>AudioLevelEffect2</code> instance to the codec
     * chain, if necessary, in order to enabled audio level measurements.
     *
     * {@inheritDoc}
     */
    @Override
    protected Processor createProcessor()
    {
        Processor processor = super.createProcessor();

        // when using translator we do not want any audio level effect
        if (useTranslator) {
            return processor;
        }

        if (processor != null) {
            if (outputAudioLevelEffect != null) {
                for (TrackControl track : processor.getTrackControls()) {
                    try {
                        track.setCodecChain(new Codec[]{outputAudioLevelEffect});
                    } catch (UnsupportedPlugInException upie) {
                        Timber.w(upie, "Failed to insert the audio level Effect. Output levels will not be included.");
                    }
                }
            }
        }
        return processor;
    }

    /**
     * Enables or disables measuring audio levels for the output <code>DataSource</code> of this
     * <code>AudioMediaDeviceSession</code>.
     *
     * Note that if audio levels are to be enabled, this method needs to be called (with
     * <code>enabled</code> set to <code>true</code>) before the output <code>DataSource</code>, or the
     * <code>Processor</code> are accessed (via {@link #getOutputDataSource()} and
     * {@link #getProcessor()}). This limitation allows to not insert an <code>Effect</code> in the
     * codec chain when measuring audio levels is not required (since we can only do this before the
     * <code>Processor</code> is realized).
     *
     * @param enabled whether to enable or disable output audio levels.
     */
    public void enableOutputSSRCAudioLevels(boolean enabled, byte extensionID)
    {
        if (enabled && outputAudioLevelEffect == null) {
            outputAudioLevelEffect = new AudioLevelEffect2();
        }

        if (outputAudioLevelEffect != null) {
            outputAudioLevelEffect.setEnabled(enabled);
            outputAudioLevelEffect.setRtpHeaderExtensionId(extensionID);
        }
    }
}
