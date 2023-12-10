/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.atalk.impl.neomedia.device;

import org.atalk.impl.neomedia.MediaUtils;
import org.atalk.impl.neomedia.conference.AudioMixer;
import org.atalk.service.neomedia.MediaDirection;
import org.atalk.service.neomedia.RTPExtension;
import org.atalk.util.MediaType;
import org.atalk.util.OSUtils;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.List;

import javax.media.CaptureDeviceInfo;
import javax.media.Format;
import javax.media.Renderer;
import javax.media.control.BufferControl;
import javax.media.control.FormatControl;
import javax.media.format.AudioFormat;
import javax.media.protocol.CaptureDevice;
import javax.media.protocol.DataSource;

import timber.log.Timber;

/**
 * Extends <code>MediaDeviceImpl</code> with audio-specific functionality.
 *
 * @author Lyubomir Marinov
 * @author Eng Chong Meng
 */
public class AudioMediaDeviceImpl extends MediaDeviceImpl
{
    /**
     * The <code>AudioMixer</code> which enables sharing an exclusive <code>CaptureDevice</code> such as
     * JavaSound between multiple <code>CaptureDevice</code> users.
     */
    private AudioMixer captureDeviceSharing;

    /**
     * The <code>List</code> of RTP extensions supported by this device (at the time of writing this
     * list is only filled for audio devices and is <code>null</code> otherwise).
     */
    private List<RTPExtension> rtpExtensions = null;

    /**
     * Initializes a new <code>AudioMediaDeviceImpl</code> instance which represents a <code>MediaDevice</code> with
     * <code>MediaType</code> <code>AUDIO</code> and a <code>MediaDirection</code> which does not allow sending.
     */
    public AudioMediaDeviceImpl()
    {
        super(MediaType.AUDIO);
    }

    /**
     * Initializes a new <code>AudioMediaDeviceImpl</code> which is to provide an implementation of
     * <code>MediaDevice</code> with <code>MediaType</code> <code>AUDIO</code> to a <code>CaptureDevice</code> with
     * a specific <code>CaptureDeviceInfo</code>.
     *
     * @param captureDeviceInfo the <code>CaptureDeviceInfo</code> of the <code>CaptureDevice</code> to which the new instance
     * is to provide an implementation of <code>MediaDevice</code>
     */
    public AudioMediaDeviceImpl(CaptureDeviceInfo captureDeviceInfo)
    {
        super(captureDeviceInfo, MediaType.AUDIO);
    }

    /**
     * Connects to a specific <code>CaptureDevice</code> given in the form of a <code>DataSource</code>.
     *
     * @param captureDevice the <code>CaptureDevice</code> to be connected to
     * @throws IOException if anything wrong happens while connecting to the specified <code>captureDevice</code>
     * @see AbstractMediaDevice#connect(DataSource)
     */
    @Override
    public void connect(DataSource captureDevice)
            throws IOException
    {
        super.connect(captureDevice);

        /*
         * 1. Changing the buffer length to 30 ms. The default buffer size (for JavaSound) is
         * 125 ms (i.e. 1/8 sec). On Mac OS X, this leads to an exception and no audio capture. A
         * value of 30 ms for the buffer length fixes the problem and is OK when using some PSTN gateways.
         *
         * 2. Changing to 60 ms. When it is 30 ms, there are some issues with Asterisk and NAT (we don't start to
         * send a/the stream and Asterisk's RTP functionality doesn't notice that we're behind NAT).
         *
         * 3. Do not set buffer length on Linux as it completely breaks audio capture.
         */
        if (!OSUtils.IS_LINUX) {
            BufferControl bufferControl = (BufferControl) captureDevice.getControl(BufferControl.class.getName());
            if (bufferControl != null)
                bufferControl.setBufferLength(60 /* millis */);
        }
    }

    /**
     * Creates the JMF <code>CaptureDevice</code> this instance represents and provides an
     * implementation of <code>MediaDevice</code> for.
     *
     * @return the JMF <code>CaptureDevice</code> this instance represents and provides an
     * implementation of <code>MediaDevice</code> for; <code>null</code> if the creation fails
     */
    @Override
    protected synchronized CaptureDevice createCaptureDevice()
    {
        CaptureDevice captureDevice = null;

        if (getDirection().allowsSending()) {
            if (captureDeviceSharing == null) {
                String protocol = getCaptureDeviceInfoLocatorProtocol();
                boolean createCaptureDeviceIfNull = true;

                if (AudioSystem.LOCATOR_PROTOCOL_JAVASOUND.equalsIgnoreCase(protocol)) {
                    captureDevice = superCreateCaptureDevice();
                    createCaptureDeviceIfNull = false;
                    if (captureDevice != null) {
                        captureDeviceSharing = createCaptureDeviceSharing(captureDevice);
                        captureDevice = captureDeviceSharing.createOutDataSource();
                    }
                }
                if ((captureDevice == null) && createCaptureDeviceIfNull)
                    captureDevice = superCreateCaptureDevice();
            }
            else
                captureDevice = captureDeviceSharing.createOutDataSource();
        }
        return captureDevice;
    }

    /**
     * Creates a new <code>AudioMixer</code> which is to enable the sharing of a specific explicit <code>CaptureDevice</code>
     *
     * @param captureDevice an exclusive <code>CaptureDevice</code> for which sharing is to be enabled
     * @return a new <code>AudioMixer</code> which enables the sharing of the specified exclusive <code>captureDevice</code>
     */
    private AudioMixer createCaptureDeviceSharing(CaptureDevice captureDevice)
    {
        return new AudioMixer(captureDevice)
        {
            @Override
            protected void connect(DataSource dataSource, DataSource inputDataSource)
                    throws IOException
            {
                /*
                 * CaptureDevice needs special connecting as defined by AbstractMediaDevice and,
                 * especially, MediaDeviceImpl.
                 */
                if (inputDataSource == captureDevice)
                    AudioMediaDeviceImpl.this.connect(dataSource);
                else
                    super.connect(dataSource, inputDataSource);
            }
        };
    }

    /**
     * {@inheritDoc}
     * <p>
     * Tries to delegate the initialization of a new <code>Renderer</code> instance to the
     * <code>AudioSystem</code> which provides the <code>CaptureDevice</code> of this instance. This way
     * both the capture and the playback are given a chance to happen within the same
     * <code>AudioSystem</code>. If the discovery of the delegate fails, the implementation of
     * <code>MediaDeviceImpl</code> is executed and it currently leaves it to FMJ to choose a
     * <code>Renderer</code> irrespective of this <code>MediaDevice</code>.
     */
    @Override
    protected Renderer createRenderer()
    {
        Renderer renderer = null;
        try {
            String locatorProtocol = getCaptureDeviceInfoLocatorProtocol();

            if (locatorProtocol != null) {
                AudioSystem audioSystem = AudioSystem.getAudioSystem(locatorProtocol);

                if (audioSystem != null)
                    renderer = audioSystem.createRenderer(true);
            }
        } finally {
            if (renderer == null)
                renderer = super.createRenderer();
        }
        return renderer;
    }

    /**
     * Returns a <code>List</code> containing extension descriptor indicating
     * <code>RECVONLY</code> support for mixer-to-client audio levels,
     * and extension descriptor indicating <code>SENDRECV</code> support for
     * client-to-mixer audio levels.
     * We add the ssrc audio levels as first element, in order when making offer
     * to be the first one (id 1) as some other systems have this hardcoded it as 1 (jicofo).
     *
     * @return a <code>List</code> containing the <code>CSRC_AUDIO_LEVEL_URN</code>
     * and  <code>SSRC_AUDIO_LEVEL_URN</code> extension descriptor.
     */
    @Override
    public List<RTPExtension> getSupportedExtensions()
    {
        if (rtpExtensions == null) {
            rtpExtensions = new ArrayList<RTPExtension>(1);

            URI ssrcAudioLevelURN;
            URI csrcAudioLevelURN;
            try {
                ssrcAudioLevelURN = new URI(RTPExtension.SSRC_AUDIO_LEVEL_URN);
                csrcAudioLevelURN = new URI(RTPExtension.CSRC_AUDIO_LEVEL_URN);
            } catch (URISyntaxException e) {
                // can't happen since CSRC_AUDIO_LEVEL_URN is a valid URI and never changes.
                Timber.i(e, "Aha! Someone messed with the source!");
                ssrcAudioLevelURN = null;
                csrcAudioLevelURN = null;
            }
            if (ssrcAudioLevelURN != null) {
                rtpExtensions.add(new RTPExtension(ssrcAudioLevelURN, MediaDirection.SENDRECV));
            }
            if (csrcAudioLevelURN != null) {
                rtpExtensions.add(new RTPExtension(csrcAudioLevelURN, MediaDirection.RECVONLY));
            }
        }
        return rtpExtensions;
    }

    private boolean isLessThanOrEqualToMaxAudioFormat(Format format)
    {
        if (format instanceof AudioFormat) {
            AudioFormat audioFormat = (AudioFormat) format;
            int channels = audioFormat.getChannels();

            if ((channels == Format.NOT_SPECIFIED)
                    || (MediaUtils.MAX_AUDIO_CHANNELS == Format.NOT_SPECIFIED)
                    || (channels <= MediaUtils.MAX_AUDIO_CHANNELS)) {
                double sampleRate = audioFormat.getSampleRate();

                if ((sampleRate == Format.NOT_SPECIFIED)
                        || (MediaUtils.MAX_AUDIO_SAMPLE_RATE == Format.NOT_SPECIFIED)
                        || (sampleRate <= MediaUtils.MAX_AUDIO_SAMPLE_RATE)) {
                    int sampleSizeInBits = audioFormat.getSampleSizeInBits();

                    return (sampleSizeInBits == Format.NOT_SPECIFIED)
                            || (MediaUtils.MAX_AUDIO_SAMPLE_SIZE_IN_BITS == Format.NOT_SPECIFIED)
                            || (sampleSizeInBits <= MediaUtils.MAX_AUDIO_SAMPLE_SIZE_IN_BITS);
                }
            }
        }
        return false;
    }

    /**
     * Invokes the super (with respect to the <code>AudioMediaDeviceImpl</code> class)
     * implementation of {@link MediaDeviceImpl#createCaptureDevice()}. Allows this instance to
     * customize the very <code>CaptureDevice</code> which is to be possibly further wrapped by this instance.
     *
     * @return the <code>CaptureDevice</code> returned by the call to the super implementation of
     * <code>MediaDeviceImpl#createCaptureDevice</code>.
     */
    protected CaptureDevice superCreateCaptureDevice()
    {
        CaptureDevice captureDevice = super.createCaptureDevice();
        if (captureDevice != null) {
            /*
             * Try to default the captureDevice to a Format which does not exceed the maximum
             * quality known to MediaUtils.
             */
            try {
                FormatControl[] formatControls = captureDevice.getFormatControls();

                if ((formatControls != null) && (formatControls.length != 0)) {
                    for (FormatControl formatControl : formatControls) {
                        Format format = formatControl.getFormat();

                        if (isLessThanOrEqualToMaxAudioFormat(format))
                            continue;

                        Format[] supportedFormats = formatControl.getSupportedFormats();
                        AudioFormat supportedFormatToSet = null;

                        if ((supportedFormats != null) && (supportedFormats.length != 0)) {
                            for (Format supportedFormat : supportedFormats) {
                                if (isLessThanOrEqualToMaxAudioFormat(supportedFormat)) {
                                    supportedFormatToSet = (AudioFormat) supportedFormat;
                                    break;
                                }
                            }
                        }

                        if ((supportedFormatToSet != null) && !supportedFormatToSet.matches(format)) {
                            int channels = supportedFormatToSet.getChannels();
                            double sampleRate = supportedFormatToSet.getSampleRate();
                            int sampleSizeInBits = supportedFormatToSet.getSampleSizeInBits();

                            if (channels == Format.NOT_SPECIFIED)
                                channels = MediaUtils.MAX_AUDIO_CHANNELS;
                            if (sampleRate == Format.NOT_SPECIFIED)
                                sampleRate = MediaUtils.MAX_AUDIO_SAMPLE_RATE;
                            if (sampleSizeInBits == Format.NOT_SPECIFIED) {
                                sampleSizeInBits = MediaUtils.MAX_AUDIO_SAMPLE_SIZE_IN_BITS;
                                /*
                                 * TODO A great deal of the neomedia-contributed audio Codecs,
                                 * CaptureDevices, DataSources and Renderers deal with 16-bit
                                 * samples.
                                 */
                                if (sampleSizeInBits == Format.NOT_SPECIFIED)
                                    sampleSizeInBits = 16;
                            }

                            if ((channels != Format.NOT_SPECIFIED)
                                    && (sampleRate != Format.NOT_SPECIFIED)
                                    && (sampleSizeInBits != Format.NOT_SPECIFIED)) {
                                AudioFormat formatToSet = new AudioFormat(
                                        supportedFormatToSet.getEncoding(), sampleRate,
                                        sampleSizeInBits, channels);

                                if (supportedFormatToSet.matches(formatToSet))
                                    formatControl.setFormat(supportedFormatToSet.intersects(formatToSet));
                            }
                        }
                    }
                }
            } catch (Throwable t) {
                if (t instanceof ThreadDeath)
                    throw (ThreadDeath) t;
                /*
                 * We tried to default the captureDevice to a Format which does not exceed the
                 * maximum quality known to MediaUtils and we failed but it does not mean that the
                 * captureDevice will not be successfully used.
                 */
            }
        }
        return captureDevice;
    }
}
