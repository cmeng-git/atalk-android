/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.atalk.impl.neomedia;

import org.atalk.impl.timberlog.TimberLog;
import org.atalk.impl.neomedia.device.AudioMediaDeviceSession;
import org.atalk.impl.neomedia.device.MediaDeviceSession;
import org.atalk.impl.neomedia.rtcp.AudioRTCPTermination;
import org.atalk.impl.neomedia.rtp.MediaStreamTrackReceiver;
import org.atalk.impl.neomedia.rtp.StreamRTPManager;
import org.atalk.impl.neomedia.transform.DiscardTransformEngine;
import org.atalk.impl.neomedia.transform.TransformEngine;
import org.atalk.impl.neomedia.transform.csrc.CsrcTransformEngine;
import org.atalk.impl.neomedia.transform.csrc.SsrcTransformEngine;
import org.atalk.impl.neomedia.transform.dtmf.DtmfTransformEngine;
import org.atalk.service.configuration.ConfigurationService;
import org.atalk.service.libjitsi.LibJitsi;
import org.atalk.service.neomedia.AudioMediaStream;
import org.atalk.service.neomedia.DTMFInbandTone;
import org.atalk.service.neomedia.DTMFMethod;
import org.atalk.service.neomedia.DTMFRtpTone;
import org.atalk.service.neomedia.DTMFTone;
import org.atalk.service.neomedia.MediaDirection;
import org.atalk.service.neomedia.MediaService;
import org.atalk.service.neomedia.MediaStream;
import org.atalk.service.neomedia.RTPExtension;
import org.atalk.service.neomedia.SrtpControl;
import org.atalk.service.neomedia.StreamConnector;
import org.atalk.service.neomedia.VolumeControl;
import org.atalk.service.neomedia.codec.Constants;
import org.atalk.service.neomedia.device.MediaDevice;
import org.atalk.service.neomedia.event.CsrcAudioLevelListener;
import org.atalk.service.neomedia.event.DTMFListener;
import org.atalk.service.neomedia.event.DTMFToneEvent;
import org.atalk.service.neomedia.event.SimpleAudioLevelListener;
import org.atalk.util.event.PropertyChangeNotifier;

import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import javax.media.Format;
import javax.media.control.BufferControl;
import javax.media.format.AudioFormat;

import timber.log.Timber;

/**
 * Extends <code>MediaStreamImpl</code> in order to provide an implementation of <code>AudioMediaStream</code>.
 *
 * @author Lyubomir Marinov
 * @author Emil Ivov
 * @author Eng Chong Meng
 */
public class AudioMediaStreamImpl extends MediaStreamImpl implements AudioMediaStream, PropertyChangeListener
{
    /**
     * List of RTP format strings which are supported by SIP Communicator in addition to the JMF
     * standard formats.
     *
     * @see #registerCustomCodecFormats(StreamRTPManager)
     */
    private static final AudioFormat[] CUSTOM_CODEC_FORMATS = new AudioFormat[]{
            /*
             * these formats are specific, since RTP uses format numbers with no parameters.
             */
            new AudioFormat(
                    Constants.ALAW_RTP,
                    8000,
                    8,
                    1,
                    Format.NOT_SPECIFIED,
                    AudioFormat.SIGNED),
            new AudioFormat(
                    Constants.G722_RTP,
                    8000,
                    Format.NOT_SPECIFIED /* sampleSizeInBits */,
                    1)
    };

    /**
     * A <code>PropertyChangeNotifier<code> which will inform this <code>AudioStream</code> if a selected
     * audio device (capture, playback or notification device) has changed. We want to listen to
     * these events, especially for those generated after the <code>AudioSystem</code> has changed.
     */
    private final PropertyChangeNotifier audioSystemChangeNotifier;

    /**
     * The listener that gets notified of changes in the audio level of remote conference participants.
     */
    private CsrcAudioLevelListener csrcAudioLevelListener;

    /**
     * The list of DTMF listeners.
     */
    private final List<DTMFListener> dtmfListeners = new ArrayList<>();

    /**
     * The transformer that we use for sending and receiving DTMF packets.
     */
    private DtmfTransformEngine dtmfTransformEngine;

    /**
     * The listener which has been set on this instance to get notified of changes in the levels of
     * the audio that the local peer/user is sending to the remote peer(s).
     */
    private SimpleAudioLevelListener localUserAudioLevelListener;

    /**
     * The <code>VolumeControl</code> implementation which is to control the volume (level) of the
     * audio received in/by this <code>AudioMediaStream</code> and played back.
     */
    private VolumeControl outputVolumeControl;

    /**
     * The instance that terminates REMBs.
     */
    private final AudioRTCPTermination rtcpTermination = new AudioRTCPTermination();

    /**
     * The listener which has been set on this instance to get notified of changes in the levels of
     * the audios that the local peer/user is receiving from the remote peer(s).
     */
    private SimpleAudioLevelListener streamAudioLevelListener;

    private SsrcTransformEngine ssrcTransformEngine;

    /**
     * The instance that is aware of all of the {@link org.atalk.impl.neomedia.rtp.RTPEncodingDesc} of the remote endpoint.
     */
    private final MediaStreamTrackReceiver mediaStreamTrackReceiver = new MediaStreamTrackReceiver(this);

    /**
     * Initializes a new <code>AudioMediaStreamImpl</code> instance which will use the specified
     * <code>MediaDevice</code> for both capture and playback of audio exchanged via the specified <code>StreamConnector</code>.
     *
     * @param connector the <code>StreamConnector</code> the new instance is to use for sending and receiving audio
     * @param device the <code>MediaDevice</code> the new instance is to use for both capture and playback of
     * audio exchanged via the specified <code>StreamConnector</code>
     * @param srtpControl a control which is already created, used to control the srtp operations.
     */
    public AudioMediaStreamImpl(StreamConnector connector, MediaDevice device, SrtpControl srtpControl)
    {
        super(connector, device, srtpControl);
        MediaService mediaService = LibJitsi.getMediaService();

        if (mediaService instanceof PropertyChangeNotifier) {
            audioSystemChangeNotifier = (PropertyChangeNotifier) mediaService;
            audioSystemChangeNotifier.addPropertyChangeListener(this);
        }
        else
            audioSystemChangeNotifier = null;
    }

    /**
     * Gets the time in milliseconds of the last input activity related to this
     * <code>AudioMediaStream</code>. We detect either RTP or RTCP activity.
     *
     * @return the time in milliseconds of the last input activity related to this <code>AudioMediaStream</code>
     * @throws IOException only in case we create input stream and it fails,
     * as we always pass false to skip creating, should never be thrown.
     */
    public long getLastInputActivityTime()
            throws IOException
    {
        RTPConnectorInputStream inData = getRTPConnector().getDataInputStream(false);
        long inDataActivity = -1;
        if (inData != null) {
            inDataActivity = inData.getLastActivityTime();
        }

        RTPConnectorInputStream inControl = getRTPConnector().getControlInputStream(false);
        long inControlActivity = -1;
        if (inControl != null) {
            inControlActivity = inControl.getLastActivityTime();
        }
        return Math.max(inControlActivity, inDataActivity);
    }

    /**
     * Adds a <code>DTMFListener</code> to this <code>AudioMediaStream</code> which is to receive
     * notifications when the remote party starts sending DTMF tones to us.
     *
     * @param listener the <code>DTMFListener</code> to register for notifications about the remote party
     * starting sending of DTM tones to this <code>AudioMediaStream</code>
     * @see AudioMediaStream#addDTMFListener(DTMFListener)
     */
    public void addDTMFListener(DTMFListener listener)
    {
        if ((listener != null) && !dtmfListeners.contains(listener))
            dtmfListeners.add(listener);
    }

    /**
     * In addition to calling {@link MediaStreamImpl#addRTPExtension(byte, RTPExtension)} this
     * method enables sending of CSRC audio levels. The reason we are doing this here rather
     * than in the super class is that CSRC levels only make sense for audio streams so we don't
     * want them enabled in any other type.
     *
     * @param extensionID the ID assigned to <code>rtpExtension</code> for the lifetime of this stream.
     * @param rtpExtension the RTPExtension that is being added to this stream.
     */
    @Override
    public void addRTPExtension(byte extensionID, RTPExtension rtpExtension)
    {
        if (rtpExtension != null)
            super.addRTPExtension(extensionID, rtpExtension);

        // Do go on even if the extension is null, to make sure that the currently active
        // extensions are configured.

        // The method invocation may add, remove, or replace the value associated with
        // extensionID. Consequently, we have to update csrcEngine with whatever is in
        // activeRTPExtensions eventually.
        CsrcTransformEngine csrcEngine = getCsrcEngine();
        SsrcTransformEngine ssrcEngine = this.ssrcTransformEngine;

        if ((csrcEngine != null) || (ssrcEngine != null)) {
            Map<Byte, RTPExtension> activeRTPExtensions = getActiveRTPExtensions();
            Byte csrcExtID = null;
            MediaDirection csrcDir = MediaDirection.INACTIVE;
            Byte ssrcExtID = null;
            MediaDirection ssrcDir = MediaDirection.INACTIVE;

            if ((activeRTPExtensions != null) && !activeRTPExtensions.isEmpty()) {
                for (Map.Entry<Byte, RTPExtension> e : activeRTPExtensions.entrySet()) {
                    RTPExtension ext = e.getValue();
                    String uri = ext.getURI().toString();

                    if (RTPExtension.CSRC_AUDIO_LEVEL_URN.equals(uri)) {
                        csrcExtID = e.getKey();
                        csrcDir = ext.getDirection();
                    }
                    else if (RTPExtension.SSRC_AUDIO_LEVEL_URN.equals(uri)) {
                        ssrcExtID = e.getKey();
                        ssrcDir = ext.getDirection();

                        // jicofo is always setting this extension as one
                        // if we negotiate it to be something different let's at least print it
                        if (ssrcExtID != 1) {
                            Timber.w("SSRC_AUDIO_LEVEL_URN extension id needs rewriting!");
                        }
                    }
                }
            }

            if (csrcEngine != null) {
                csrcEngine.setCsrcAudioLevelExtensionID((csrcExtID == null) ? -1 : csrcExtID, csrcDir);
            }
            if (ssrcEngine != null) {
                ssrcEngine.setSsrcAudioLevelExtensionID((ssrcExtID == null) ? -1 : ssrcExtID, ssrcDir);

                if (ssrcDir.allowsSending()) {
                    AudioMediaDeviceSession deviceSession = getDeviceSession();
                    if (deviceSession != null) {
                        deviceSession.enableOutputSSRCAudioLevels(true, ssrcExtID == null ? -1 : ssrcExtID);
                    }
                }
            }
        }
    }

    /**
     * Delivers the <code>audioLevels</code> map to whoever is interested. This method is meant for use
     * primarily by the transform engine handling incoming RTP packets (currently <code>CsrcTransformEngine</code>).
     *
     * @param audioLevels an array mapping CSRC IDs to audio levels in consecutive elements.
     */
    public void audioLevelsReceived(long[] audioLevels)
    {
        CsrcAudioLevelListener csrcAudioLevelListener = this.csrcAudioLevelListener;
        if (csrcAudioLevelListener != null)
            csrcAudioLevelListener.audioLevelsReceived(audioLevels);
    }

    /**
     * Releases the resources allocated by this instance in the course of its execution and
     * prepares it to be garbage collected.
     *
     * @see MediaStream#close()
     */
    @Override
    public void close()
    {
        super.close();

        if (dtmfTransformEngine != null) {
            dtmfTransformEngine.close();
            dtmfTransformEngine = null;
        }
        if (ssrcTransformEngine != null) {
            ssrcTransformEngine.close();
            ssrcTransformEngine = null;
        }

        if (audioSystemChangeNotifier != null)
            audioSystemChangeNotifier.removePropertyChangeListener(this);
    }

    /**
     * Performs any optional configuration on the <code>BufferControl</code> of the specified
     * <code>RTPManager</code> which is to be used as the <code>RTPManager</code> of this <code>MediaStreamImpl</code>.
     *
     * @param rtpManager the <code>RTPManager</code> which is to be used by this <code>MediaStreamImpl</code>
     * @param bufferControl the <code>BufferControl</code> of <code>rtpManager</code> on which any optional configuration
     * is to be performed
     */
    @Override
    protected void configureRTPManagerBufferControl(StreamRTPManager rtpManager, BufferControl bufferControl)
    {
        /*
         * It appears that, if we don't do the following, the RTPManager won't play.
         */
        ConfigurationService cfg = LibJitsi.getConfigurationService();
        /*
         * There isn't a particular reason why we'd choose 100 or 120. It may be that 120 is
         * divided by 30 (which is used by iLBC, for example) and 100 isn't. Anyway, what matters
         * most is that it's proportional to the latency of the playback.
         */
        long bufferLength = 120;
        if (cfg != null) {
            String bufferLengthStr = cfg.getString(PROPERTY_NAME_RECEIVE_BUFFER_LENGTH);

            try {
                if ((bufferLengthStr != null) && (bufferLengthStr.length() > 0))
                    bufferLength = Long.parseLong(bufferLengthStr);
            } catch (NumberFormatException nfe) {
                Timber.w(nfe, "%s is not a valid receive buffer length/long value", bufferLengthStr);
            }
        }

        bufferLength = bufferControl.setBufferLength(bufferLength);
        Timber.log(TimberLog.FINER, "Set receiver buffer length to %s", bufferLength);

        /*
         * The threshold should better be half of the bufferLength rather than equal to it (as it
         * used to be before). Whatever it is, FMJ/JMF doesn't take it into account anyway.
         */
        long minimumThreshold = bufferLength / 2;

        bufferControl.setEnabledThreshold(minimumThreshold > 0);
        bufferControl.setMinimumThreshold(minimumThreshold);
    }

    /**
     * A stub that allows audio oriented streams to create and keep a reference to a <code>DtmfTransformEngine</code>.
     *
     * @return a <code>DtmfTransformEngine</code> if this is an audio oriented stream and <code>null</code> otherwise.
     */
    @Override
    protected DtmfTransformEngine createDtmfTransformEngine()
    {
        if (dtmfTransformEngine == null) {
            ConfigurationService cfg = LibJitsi.getConfigurationService();
            if (cfg == null
                    || !cfg.getBoolean(AudioMediaStream.DISABLE_DTMF_HANDLING_PNAME, false)) {
                dtmfTransformEngine = new DtmfTransformEngine(this);
            }
        }
        return dtmfTransformEngine;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected SsrcTransformEngine createSsrcTransformEngine()
    {
        if (ssrcTransformEngine == null)
            ssrcTransformEngine = new SsrcTransformEngine(this);
        return ssrcTransformEngine;
    }

    /**
     * {@inheritDoc}
     * <p>
     * Makes sure that {@link #localUserAudioLevelListener} and {@link #streamAudioLevelListener}
     * which have been set on this <code>AudioMediaStream</code> will be automatically updated when a
     * new <code>MediaDevice</code> is set on this instance.
     */
    @Override
    protected void deviceSessionChanged(MediaDeviceSession oldValue, MediaDeviceSession newValue)
    {
        try {
            if (oldValue != null) {
                AudioMediaDeviceSession deviceSession = (AudioMediaDeviceSession) oldValue;

                if (localUserAudioLevelListener != null)
                    deviceSession.setLocalUserAudioLevelListener(null);
                if (streamAudioLevelListener != null)
                    deviceSession.setStreamAudioLevelListener(null);
            }
            if (newValue != null) {
                AudioMediaDeviceSession deviceSession = (AudioMediaDeviceSession) newValue;

                if (localUserAudioLevelListener != null) {
                    deviceSession.setLocalUserAudioLevelListener(localUserAudioLevelListener);
                }
                if (streamAudioLevelListener != null) {
                    deviceSession.setStreamAudioLevelListener(streamAudioLevelListener);
                }

                /*
                 * The output volume (level) of the newValue will begin to be controlled by the
                 * outputVolumeControl of this instance (of course). The output volume (level) of
                 * the oldValue will continue to be controlled by the outputVolumeControl of this
                 * instance (as well). The latter behaviour should not present a problem and keeps
                 * the design and implementation as simple as possible.
                 */
                if (outputVolumeControl != null)
                    deviceSession.setOutputVolumeControl(outputVolumeControl);
            }
        } finally {
            super.deviceSessionChanged(oldValue, newValue);
        }
    }

    /**
     * Delivers the <code>DTMF</code> tones. The method is meant for use primarily by the transform
     * engine handling incoming RTP packets (currently <code>DtmfTransformEngine</code>).
     *
     * @param tone the new tone
     * @param end <code>true</code> if the tone is to be ended or <code>false</code> to be started
     */
    public void fireDTMFEvent(DTMFRtpTone tone, boolean end)
    {
        DTMFToneEvent ev = new DTMFToneEvent(this, tone);

        for (DTMFListener listener : dtmfListeners) {
            if (end)
                listener.dtmfToneReceptionEnded(ev);
            else
                listener.dtmfToneReceptionStarted(ev);
        }
    }

    /**
     * Returns the <code>MediaDeviceSession</code> associated with this stream after first casting it to
     * <code>AudioMediaDeviceSession</code> since this is, after all, an <code>AudioMediaStreamImpl</code>.
     *
     * @return the <code>AudioMediaDeviceSession</code> associated with this stream.
     */
    @Override
    public AudioMediaDeviceSession getDeviceSession()
    {
        return (AudioMediaDeviceSession) super.getDeviceSession();
    }

    /**
     * Returns the last audio level that was measured by the underlying device session for the specified
     * <code>ssrc</code> (where <code>ssrc</code> could also correspond to our local sync source identifier).
     *
     * @param ssrc the SSRC ID whose last measured audio level we'd like to retrieve.
     * @return the audio level that was last measured for the specified <code>ssrc</code> or
     * <code>-1</code> if no level has been cached for that ID.
     */
    public int getLastMeasuredAudioLevel(long ssrc)
    {
        AudioMediaDeviceSession devSession = getDeviceSession();
        if (devSession == null)
            return -1;
        else if (ssrc == getLocalSourceID())
            return devSession.getLastMeasuredLocalUserAudioLevel();
        else
            return devSession.getLastMeasuredAudioLevel(ssrc);
    }

    /**
     * The priority of the audio is 3, which is meant to be higher than other threads and higher than the video one.
     *
     * @return audio priority.
     */
    @Override
    protected int getPriority()
    {
        return 3;
    }

    /**
     * Receives and reacts to property change events: if the selected device (for capture, playback
     * or notifications) has changed, then create or recreate the streams in order to use it. We
     * want to listen to these events, especially for those generated after the audio system has changed.
     *
     * @param ev The event which may contain a audio system change event.
     */
    public void propertyChange(PropertyChangeEvent ev)
    {
        /*
         * FIXME It is very wrong to do the following upon every PropertyChangeEvent fired by
         * MediaServiceImpl. Moreover, it does not seem right that we'd want to start this
         * MediaStream upon a PropertyChangeEvent (regardless of its specifics).
         */
        if (sendStreamsAreCreated)
            recreateSendStreams();
        else
            start();
    }

    /**
     * Registers {@link #CUSTOM_CODEC_FORMATS} with a specific <code>RTPManager</code>.
     *
     * @param rtpManager the <code>RTPManager</code> to register {@link #CUSTOM_CODEC_FORMATS} with
     * @see MediaStreamImpl#registerCustomCodecFormats(StreamRTPManager)
     */
    @Override
    protected void registerCustomCodecFormats(StreamRTPManager rtpManager)
    {
        super.registerCustomCodecFormats(rtpManager);
        for (AudioFormat format : CUSTOM_CODEC_FORMATS) {
            Timber.d("registering format %s with RTPManager", format);
            /*
             * NOTE (mkoch@rowa.de): com.sun.media.rtp.RtpSessionMgr.addFormat leaks memory, since
             * it stores the Format in a static Vector. AFAIK there is no easy way around it, but
             * the memory impact should not be too bad.
             */
            rtpManager.addFormat(format, MediaUtils.getRTPPayloadType(format.getEncoding(), format.getSampleRate()));
        }
    }

    /**
     * Removes <code>listener</code> from the list of <code>DTMFListener</code>s registered with this
     * <code>AudioMediaStream</code> to receive notifications about incoming DTMF tones.
     *
     * @param listener the <code>DTMFListener</code> to no longer be notified by this <code>AudioMediaStream</code>
     * about incoming DTMF tones
     * @see AudioMediaStream#removeDTMFListener(DTMFListener)
     */
    public void removeDTMFListener(DTMFListener listener)
    {
        dtmfListeners.remove(listener);
    }

    /**
     * Registers <code>listener</code> as the <code>CsrcAudioLevelListener</code> that will receive
     * notifications for changes in the levels of conference participants that the remote party
     * could be mixing.
     *
     * @param listener the <code>CsrcAudioLevelListener</code> that we'd like to register or <code>null</code> if
     * we'd like to stop receiving notifications.
     */
    public void setCsrcAudioLevelListener(CsrcAudioLevelListener listener)
    {
        csrcAudioLevelListener = listener;
    }

    /**
     * Sets <code>listener</code> as the <code>SimpleAudioLevelListener</code> registered to receive
     * notifications from our device session for changes in the levels of the audio that this
     * stream is sending out.
     *
     * @param listener the <code>SimpleAudioLevelListener</code> that we'd like to register or <code>null</code> if
     * we want to stop local audio level measurements.
     */
    public void setLocalUserAudioLevelListener(SimpleAudioLevelListener listener)
    {
        if (localUserAudioLevelListener != listener) {
            localUserAudioLevelListener = listener;

            AudioMediaDeviceSession deviceSession = getDeviceSession();
            if (deviceSession != null) {
                deviceSession.setLocalUserAudioLevelListener(localUserAudioLevelListener);
            }
        }
    }

    /**
     * {@inheritDoc}
     */
    public void setOutputVolumeControl(VolumeControl outputVolumeControl)
    {
        if (this.outputVolumeControl != outputVolumeControl) {
            this.outputVolumeControl = outputVolumeControl;

            AudioMediaDeviceSession deviceSession = getDeviceSession();
            if (deviceSession != null)
                deviceSession.setOutputVolumeControl(this.outputVolumeControl);
        }
    }

    /**
     * Sets <code>listener</code> as the <code>SimpleAudioLevelListener</code> registered to receive
     * notifications from our device session for changes in the levels of the party that's at the
     * other end of this stream.
     *
     * @param listener the <code>SimpleAudioLevelListener</code> that we'd like to register or <code>null</code> if
     * we want to stop stream audio level measurements.
     */
    public void setStreamAudioLevelListener(SimpleAudioLevelListener listener)
    {
        if (streamAudioLevelListener != listener) {
            streamAudioLevelListener = listener;

            AudioMediaDeviceSession deviceSession = getDeviceSession();
            if (deviceSession != null) {
                deviceSession.setStreamAudioLevelListener(streamAudioLevelListener);
            }
        }
    }

    /**
     * Starts sending the specified <code>DTMFTone</code> until the <code>stopSendingDTMF()</code>
     * method is called (Excepts for INBAND DTMF, which stops by itself this is why where there
     * is no need to call the stopSendingDTMF). Callers should keep in mind the fact that calling
     * this method would most likely interrupt all audio transmission until the corresponding
     * stop method is called. Also, calling this method successively without invoking the
     * corresponding stop method between the calls will simply replace the <code>DTMFTone</code> from
     * the first call with that from the second.
     *
     * @param tone the <code>DTMFTone</code> to start sending.
     * @param dtmfMethod The kind of DTMF used (RTP, SIP-INOF or INBAND).
     * @param minimalToneDuration The minimal DTMF tone duration.
     * @param maximalToneDuration The maximal DTMF tone duration.
     * @param volume The DTMF tone volume.
     * @throws IllegalArgumentException if <code>dtmfMethod</code> is not one of {@link DTMFMethod#INBAND_DTMF},
     * {@link DTMFMethod#RTP_DTMF}, and {@link DTMFMethod#SIP_INFO_DTMF}
     * @see AudioMediaStream#startSendingDTMF(DTMFTone, DTMFMethod, int, int, int)
     */
    public void startSendingDTMF(DTMFTone tone, DTMFMethod dtmfMethod, int minimalToneDuration,
            int maximalToneDuration, int volume)
    {
        switch (dtmfMethod) {
            case INBAND_DTMF:
                MediaDeviceSession deviceSession = getDeviceSession();
                if (deviceSession != null)
                    deviceSession.addDTMF(DTMFInbandTone.mapTone(tone));
                break;

            case RTP_DTMF:
                if (dtmfTransformEngine != null) {
                    DTMFRtpTone t = DTMFRtpTone.mapTone(tone);
                    if (t != null)
                        dtmfTransformEngine.startSending(t, minimalToneDuration, maximalToneDuration, volume);
                }
                break;

            case SIP_INFO_DTMF:
                // This kind of DTMF is not managed directly by the OperationSetDTMFSipImpl.
                break;

            default:
                throw new IllegalArgumentException("dtmfMethod");
        }
    }

    /**
     * Interrupts transmission of a <code>DTMFTone</code> started with the <code>startSendingDTMF()</code>
     * method. Has no effect if no tone is currently being sent.
     *
     * @param dtmfMethod The kind of DTMF used (RTP, SIP-INOF or INBAND).
     * @throws IllegalArgumentException if <code>dtmfMethod</code> is not one of {@link DTMFMethod#INBAND_DTMF},
     * {@link DTMFMethod#RTP_DTMF}, and {@link DTMFMethod#SIP_INFO_DTMF}
     * @see AudioMediaStream#stopSendingDTMF(DTMFMethod)
     */
    public void stopSendingDTMF(DTMFMethod dtmfMethod)
    {
        switch (dtmfMethod) {
            case INBAND_DTMF:
                // The INBAND DTMF is sent by impulse of constant duration and does not need to be stopped explicitly.
                break;

            case RTP_DTMF:
                if (dtmfTransformEngine != null)
                    dtmfTransformEngine.stopSendingDTMF();
                break;

            case SIP_INFO_DTMF:
                // The SIP-INFO DTMF is managed directly by the OperationSetDTMFSipImpl.
                break;

            default:
                throw new IllegalArgumentException("dtmfMethod");
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected DiscardTransformEngine createDiscardEngine()
    {
        return new DiscardTransformEngine(this);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected TransformEngine getRTCPTermination()
    {
        return rtcpTermination;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public MediaStreamTrackReceiver getMediaStreamTrackReceiver()
    {
        return mediaStreamTrackReceiver;
    }
}
