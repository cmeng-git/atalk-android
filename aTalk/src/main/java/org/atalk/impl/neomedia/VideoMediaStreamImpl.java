/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.atalk.impl.neomedia;

import org.atalk.impl.timberlog.TimberLog;
import org.atalk.impl.neomedia.codec.video.AndroidEncoder;
import org.atalk.impl.neomedia.control.ImgStreamingControl;
import org.atalk.impl.neomedia.device.DeviceConfiguration;
import org.atalk.impl.neomedia.device.DeviceSystem;
import org.atalk.impl.neomedia.device.MediaDeviceImpl;
import org.atalk.impl.neomedia.device.MediaDeviceSession;
import org.atalk.impl.neomedia.device.ScreenDeviceImpl;
import org.atalk.impl.neomedia.device.VideoMediaDeviceSession;
import org.atalk.impl.neomedia.rtcp.RTCPReceiverFeedbackTermination;
import org.atalk.impl.neomedia.rtp.MediaStreamTrackReceiver;
import org.atalk.impl.neomedia.rtp.RTPEncodingDesc;
import org.atalk.impl.neomedia.rtp.StreamRTPManager;
import org.atalk.impl.neomedia.rtp.VideoMediaStreamTrackReceiver;
import org.atalk.impl.neomedia.rtp.remotebitrateestimator.RemoteBitrateEstimatorWrapper;
import org.atalk.impl.neomedia.rtp.sendsidebandwidthestimation.BandwidthEstimatorImpl;
import org.atalk.impl.neomedia.transform.CachingTransformer;
import org.atalk.impl.neomedia.transform.PaddingTermination;
import org.atalk.impl.neomedia.transform.RetransmissionRequesterImpl;
import org.atalk.impl.neomedia.transform.RtxTransformer;
import org.atalk.impl.neomedia.transform.TransformEngine;
import org.atalk.impl.neomedia.transform.TransformEngineWrapper;
import org.atalk.impl.neomedia.transform.fec.FECTransformEngine;
import org.atalk.service.configuration.ConfigurationService;
import org.atalk.service.libjitsi.LibJitsi;
import org.atalk.service.neomedia.QualityControl;
import org.atalk.service.neomedia.QualityPreset;
import org.atalk.service.neomedia.SrtpControl;
import org.atalk.service.neomedia.StreamConnector;
import org.atalk.service.neomedia.VideoMediaStream;
import org.atalk.service.neomedia.control.KeyFrameControl;
import org.atalk.service.neomedia.control.KeyFrameControlAdapter;
import org.atalk.service.neomedia.device.MediaDevice;
import org.atalk.service.neomedia.device.ScreenDevice;
import org.atalk.service.neomedia.format.MediaFormat;
import org.atalk.service.neomedia.rtp.BandwidthEstimator;
import org.atalk.util.OSUtils;
import org.atalk.util.concurrent.RecurringRunnableExecutor;
import org.atalk.util.event.VideoEvent;
import org.atalk.util.event.VideoListener;
import org.atalk.util.event.VideoNotifierSupport;

import java.awt.Component;
import java.awt.Dimension;
import java.awt.Point;
import java.awt.Rectangle;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.media.Format;
import javax.media.control.BufferControl;
import javax.media.control.FormatControl;
import javax.media.format.VideoFormat;
import javax.media.format.YUVFormat;
import javax.media.protocol.DataSource;

import timber.log.Timber;

/**
 * Extends <code>MediaStreamImpl</code> in order to provide an implementation of <code>VideoMediaStream</code>.
 *
 * @author Lyubomir Marinov
 * @author Sebastien Vincent
 * @author Eng Chong Meng
 */
public class VideoMediaStreamImpl extends MediaStreamImpl implements VideoMediaStream
{
    /**
     * The indicator which determines whether RTCP feedback Picture Loss Indication messages are to be used.
     */
    private static final boolean USE_RTCP_FEEDBACK_PLI = true;

    /**
     * The <code>RecurringRunnableExecutor</code> to be utilized by the <code>MediaStreamImpl</code> class and its instances.
     */
    private static final RecurringRunnableExecutor recurringRunnableExecutor
            = new RecurringRunnableExecutor(VideoMediaStreamImpl.class.getSimpleName());

    /**
     * MediaFormat handle by this method
     */
    private static MediaFormat mFormat;


    /**
     * Extracts and returns maximum resolution can receive from the image attribute.
     *
     * @param imgattr send/recv resolution string
     * @return maximum resolution array (first element is send, second one is recv). Elements could
     * be null if image attribute is not present or if resolution is a wildcard.
     */
    public static Dimension[] parseSendRecvResolution(String imgattr)
    {
        Dimension[] res = new Dimension[2];
        String token;
        Pattern pSendSingle = Pattern.compile("send \\[x=[0-9]+,y=[0-9]+]");
        Pattern pRecvSingle = Pattern.compile("recv \\[x=[0-9]+,y=[0-9]+]");
        Pattern pSendRange = Pattern.compile("send \\[x=\\[[0-9]+([-:])[0-9]+],y=\\[[0-9]+([-:])[0-9]+]]");
        Pattern pRecvRange = Pattern.compile("recv \\[x=\\[[0-9]+([-:])[0-9]+],y=\\[[0-9]+([-:])[0-9]+]]");
        Pattern pNumeric = Pattern.compile("[0-9]+");
        Matcher mSingle;
        Matcher mRange;
        Matcher m;

        /*
         * resolution (width and height) can be on four forms
         *
         * - single value [x=1920,y=1200]
         * - range of values [x=[800:1024],y=[600:768]]
         * - fixed range of values [x=[800,1024],y=[600,768]]
         * - range of values with step [x=[800:32:1024],y=[600:32:768]]
         *
         * For the moment we only support the first two forms.
         */

        /* send part */
        mSingle = pSendSingle.matcher(imgattr);
        mRange = pSendRange.matcher(imgattr);

        if (mSingle.find()) {
            int[] val = new int[2];
            int i = 0;
            token = imgattr.substring(mSingle.start(), mSingle.end());
            m = pNumeric.matcher(token);

            while (m.find() && i < 2) {
                val[i] = Integer.parseInt(token.substring(m.start(), m.end()));
                i++;
            }
            res[0] = new Dimension(val[0], val[1]);
        }
        else if (mRange.find()) /* try with range */ {
            /* have two value for width and two for height (min-max) */
            int[] val = new int[4];
            int i = 0;
            token = imgattr.substring(mRange.start(), mRange.end());
            m = pNumeric.matcher(token);

            while (m.find() && i < 4) {
                val[i] = Integer.parseInt(token.substring(m.start(), m.end()));
                i++;
            }
            res[0] = new Dimension(val[1], val[3]);
        }

        /* recv part */
        mSingle = pRecvSingle.matcher(imgattr);
        mRange = pRecvRange.matcher(imgattr);

        if (mSingle.find()) {
            int[] val = new int[2];
            int i = 0;
            token = imgattr.substring(mSingle.start(), mSingle.end());
            m = pNumeric.matcher(token);

            while (m.find() && i < 2) {
                val[i] = Integer.parseInt(token.substring(m.start(), m.end()));
                i++;
            }
            res[1] = new Dimension(val[0], val[1]);
        }
        else if (mRange.find()) /* try with range */ {
            /* have two value for width and two for height (min-max) */
            int[] val = new int[4];
            int i = 0;
            token = imgattr.substring(mRange.start(), mRange.end());
            m = pNumeric.matcher(token);

            while (m.find() && i < 4) {
                val[i] = Integer.parseInt(token.substring(m.start(), m.end()));
                i++;
            }
            res[1] = new Dimension(val[1], val[3]);
        }
        return res;
    }

    /**
     * Selects the <code>VideoFormat</code> from the list of supported formats of a specific video <code>DataSource</code>
     * which has a size as close as possible to a specific size and sets it as the format of the specified video
     * <code>DataSource</code>. Must also check if the VideoFormat is supported by the androidEncoder;
     * VP9 encode many not be supported in all android devices.
     *
     * @param videoDS the video <code>DataSource</code> which is to have its supported formats examined and its
     * format changed to the <code>VideoFormat</code> which is as close as possible to the
     * specified <code>preferredWidth</code> and <code>preferredHeight</code>
     * @param preferredWidth the width of the <code>VideoFormat</code> to be selected
     * @param preferredHeight the height of the <code>VideoFormat</code> to be selected
     * @return the size of the <code>VideoFormat</code> from the list of supported formats of
     * <code>videoDS</code> which is as close as possible to <code>preferredWidth</code> and
     * <code>preferredHeight</code> and which has been set as the format of <code>videoDS</code>
     */
    public static Dimension selectVideoSize(DataSource videoDS, final int preferredWidth, final int preferredHeight)
    {
        if (videoDS == null)
            return null;

        FormatControl formatControl = (FormatControl) videoDS.getControl(FormatControl.class.getName());
        if (formatControl == null)
            return null;

        Format[] formats = formatControl.getSupportedFormats();
        final int count = formats.length;
        if (count < 1)
            return null;

        VideoFormat selectedFormat = null;
        if (count == 1)
            selectedFormat = (VideoFormat) formats[0];
        else {
            class FormatInfo
            {
                public final double difference;
                public final Dimension dimension;
                public final VideoFormat format;

                public FormatInfo(Dimension size)
                {
                    this.format = null;
                    this.dimension = size;
                    this.difference = getDifference(this.dimension);
                }

                public FormatInfo(VideoFormat format)
                {
                    this.format = format;
                    this.dimension = format.getSize();
                    this.difference = getDifference(this.dimension);
                    // Timber.d("format: %s; dimension: %s, difference: %s", format, dimension, difference);
                }

                private double getDifference(Dimension size)
                {
                    int width = (size == null) ? 0 : size.width;
                    double xScale;

                    if (width == 0)
                        xScale = Double.POSITIVE_INFINITY;
                    else if (width == preferredWidth)
                        xScale = 1;
                    else
                        xScale = (preferredWidth / (double) width);

                    int height = (size == null) ? 0 : size.height;
                    double yScale;

                    if (height == 0)
                        yScale = Double.POSITIVE_INFINITY;
                    else if (height == preferredHeight)
                        yScale = 1;
                    else
                        yScale = (preferredHeight / (double) height);

                    return Math.abs(1 - Math.min(xScale, yScale));
                }
            }

            // Check to see if the hardware encoder is supported
            boolean isCodecSupported = AndroidEncoder.isCodecSupported(mFormat.getEncoding());
            FormatInfo[] infos = new FormatInfo[count];
            int idx = -1;
            for (int i = 0; i < count; i++) {
                FormatInfo info = infos[i] = new FormatInfo((VideoFormat) formats[i]);
                if (info.difference == 0) {
                    if ((info.format instanceof YUVFormat) || isCodecSupported) {
                        selectedFormat = info.format;
                        idx = i;
                        break;
                    }
                }
            }
            Timber.d("Selected video format: Count: %s/%s; Dimension: [%s x %s] => %s",
                    idx, count, preferredWidth, preferredHeight, selectedFormat);

            // Select the closest is none has perfect matched in Dimension
            if (selectedFormat == null) {
                Arrays.sort(infos, (info0, info1) -> Double.compare(info0.difference, info1.difference));
                for (int i = 0; i < count; i++) {
                    if ((infos[i].format instanceof YUVFormat) || isCodecSupported) {
                        selectedFormat = infos[i].format;
                    }
                }
            }

            /*
             * If videoDS states to support any size, use the sizes that we support which is
             * closest(or smaller) to the preferred one.
             */
            if ((selectedFormat != null) && (selectedFormat.getSize() == null)) {
                VideoFormat currentFormat = (VideoFormat) formatControl.getFormat();
                Dimension currentSize = null;
                int width = preferredWidth;
                int height = preferredHeight;

                // Try to preserve the aspect ratio
                if (currentFormat != null)
                    currentSize = currentFormat.getSize();

                // sort supported resolutions by aspect
                FormatInfo[] supportedInfos = new FormatInfo[DeviceConfiguration.SUPPORTED_RESOLUTIONS.length];
                for (int i = 0; i < supportedInfos.length; i++) {
                    supportedInfos[i] = new FormatInfo(DeviceConfiguration.SUPPORTED_RESOLUTIONS[i]);
                }
                Arrays.sort(infos, (info0, info1) -> Double.compare(info0.difference, info1.difference));

                FormatInfo preferredFormat = new FormatInfo(new Dimension(preferredWidth, preferredHeight));
                Dimension closestAspect = null;
                // Let's choose the closest size to the preferred one, finding the first suitable aspect
                for (FormatInfo supported : supportedInfos) {
                    // find the first matching aspect
                    if (preferredFormat.difference > supported.difference)
                        continue;
                    else if (closestAspect == null)
                        closestAspect = supported.dimension;

                    if (supported.dimension.height <= preferredHeight
                            && supported.dimension.width <= preferredWidth) {
                        currentSize = supported.dimension;
                    }
                }

                if (currentSize == null)
                    currentSize = closestAspect;

                if ((currentSize.width > 0) && (currentSize.height > 0)) {
                    width = currentSize.width;
                    height = currentSize.height;
                }
                selectedFormat = (VideoFormat) new VideoFormat(null, new Dimension(width, height),
                        Format.NOT_SPECIFIED, null, Format.NOT_SPECIFIED).intersects(selectedFormat);
            }
        }

        Format setFormat = formatControl.setFormat(selectedFormat);
        return (setFormat instanceof VideoFormat) ? ((VideoFormat) setFormat).getSize() : null;
    }

    /**
     * The <code>VideoListener</code> which handles <code>VideoEvent</code>s from the <code>MediaDeviceSession</code> of this
     * instance and fires respective <code>VideoEvent</code>s from this <code>VideoMediaStream</code> to its <code>VideoListener</code>s.
     */
    private VideoListener deviceSessionVideoListener;

    /**
     * The <code>KeyFrameControl</code> of this <code>VideoMediaStream</code>.
     */
    private KeyFrameControl keyFrameControl;

    /**
     * Negotiated output size of the video stream. It may need to scale original capture device stream.
     */
    private Dimension outputSize;

    /**
     * The <code>QualityControl</code> of this <code>VideoMediaStream</code>.
     */
    private final QualityControlImpl qualityControl = new QualityControlImpl();

    /**
     * The instance that is aware of all of the {@link RTPEncodingDesc} of the remote endpoint.
     */
    private final MediaStreamTrackReceiver mediaStreamTrackReceiver = new VideoMediaStreamTrackReceiver(this);

    /**
     * The transformer which handles outgoing rtx (RFC-4588) packets for this {@link VideoMediaStreamImpl}.
     */
    private final RtxTransformer rtxTransformer = new RtxTransformer(this);

    /**
     * The transformer which handles incoming and outgoing fec
     */
    private final TransformEngineWrapper<FECTransformEngine> fecTransformEngineWrapper = new TransformEngineWrapper<>();

    /**
     * The instance that terminates RRs and REMBs.
     */
    private final RTCPReceiverFeedbackTermination rtcpFeedbackTermination = new RTCPReceiverFeedbackTermination(this);

    /**
     *
     */
    private final PaddingTermination paddingTermination = new PaddingTermination();

    /**
     * The <code>RemoteBitrateEstimator</code> which computes bitrate estimates for the incoming RTP streams.
     */
    private final RemoteBitrateEstimatorWrapper remoteBitrateEstimator = new RemoteBitrateEstimatorWrapper(
            VideoMediaStreamImpl.this::remoteBitrateEstimatorOnReceiveBitrateChanged, getDiagnosticContext()
    );

    /**
     * The facility which aids this instance in managing a list of <code>VideoListener</code>s and
     * firing <code>VideoEvent</code>s to them.
     * <p>
     * Since the <code>videoNotifierSupport</code> of this <code>VideoMediaStreamImpl</code> just forwards
     * the <code>VideoEvent</code>s of the associated <code>VideoMediaDeviceSession</code> at the time of
     * this writing, it does not make sense to have <code>videoNotifierSupport</code> executing
     * asynchronously because it does not know whether it has to wait for the delivery of the
     * <code>VideoEvent</code>s and thus it has to default to waiting anyway.
     * </p>
     */
    private final VideoNotifierSupport videoNotifierSupport = new VideoNotifierSupport(this, true);

    /**
     * The {@code BandwidthEstimator} which estimates the available bandwidth from this endpoint to the remote peer.
     */
    private BandwidthEstimatorImpl bandwidthEstimator;

    /**
     * The {@link CachingTransformer} which caches outgoing/incoming packets from/to this {@link VideoMediaStreamImpl}.
     */
    private CachingTransformer cachingTransformer;

    /**
     * Whether the remote end supports RTCP FIR.
     */
    private boolean supportsFir = false;

    /**
     * Whether the remote end supports RTCP PLI.
     */
    private boolean supportsPli = false;

    /**
     * Initializes a new <code>VideoMediaStreamImpl</code> instance which will use the specified <code>MediaDevice</code>
     * for both capture and playback of video exchanged via the specified <code>StreamConnector</code>.
     *
     * @param connector the <code>StreamConnector</code> the new instance is to use for sending and receiving video
     * @param device the <code>MediaDevice</code> the new instance is to use for both capture and playback of
     * video exchanged via the specified <code>StreamConnector</code>
     * @param srtpControl a control which is already created, used to control the srtp operations.
     */
    public VideoMediaStreamImpl(StreamConnector connector, MediaDevice device, SrtpControl srtpControl)
    {
        super(connector, device, srtpControl);
        recurringRunnableExecutor.registerRecurringRunnable(rtcpFeedbackTermination);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public RtxTransformer getRtxTransformer()
    {
        return rtxTransformer;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected TransformEngineWrapper<FECTransformEngine> getFecTransformEngine()
    {
        return this.fecTransformEngineWrapper;
    }

    /**
     * {@inheritDoc}
     *
     * @param fecTransformEngine
     */
    @Override
    protected void setFecTransformEngine(FECTransformEngine fecTransformEngine)
    {
        this.fecTransformEngineWrapper.setWrapped(fecTransformEngine);
    }

    /**
     * Sets the value of the flag which indicates whether the remote end supports RTCP FIR or not.
     *
     * @param supportsFir the value to set.
     */
    public void setSupportsFir(boolean supportsFir)
    {
        this.supportsFir = supportsFir;
    }

    /**
     * Sets the value of the flag which indicates whether the remote end supports RTCP PLI or not.
     *
     * @param supportsPli the value to set.
     */
    public void setSupportsPli(boolean supportsPli)
    {
        this.supportsPli = supportsPli;
    }

    /**
     * Sets the value of the flag which indicates whether the remote end supports RTCP REMB or not.
     *
     * @param supportsRemb the value to set.
     */
    public void setSupportsRemb(boolean supportsRemb)
    {
        remoteBitrateEstimator.setSupportsRemb(supportsRemb);
    }

    /**
     * @return {@code true} iff the remote end supports RTCP FIR.
     */
    public boolean supportsFir()
    {
        return supportsFir;
    }

    /**
     * @return {@code true} iff the remote end supports RTCP PLI.
     */
    public boolean supportsPli()
    {
        return supportsPli;
    }

    /**
     * Set remote SSRC.
     *
     * @param ssrc remote SSRC
     */
    @Override
    protected void addRemoteSourceID(long ssrc)
    {
        super.addRemoteSourceID(ssrc);
        MediaDeviceSession deviceSession = getDeviceSession();

        if (deviceSession instanceof VideoMediaDeviceSession)
            ((VideoMediaDeviceSession) deviceSession).setRemoteSSRC(ssrc);
    }

    /**
     * Adds a specific <code>VideoListener</code> to this <code>VideoMediaStream</code> in order to receive
     * notifications when visual/video <code>Component</code>s are being added and removed.
     * <p>
     * Adding a listener which has already been added does nothing i.e. it is not added more than
     * once and thus does not receive one and the same <code>VideoEvent</code> multiple times.
     * </p>
     *
     * @param listener the <code>VideoListener</code> to be notified when visual/video <code>Component</code>s are
     * being added or removed in this <code>VideoMediaStream</code>
     */
    public void addVideoListener(VideoListener listener)
    {
        videoNotifierSupport.addVideoListener(listener);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void close()
    {
        try {
            super.close();
        } finally {
            if (cachingTransformer != null) {
                recurringRunnableExecutor.deRegisterRecurringRunnable(cachingTransformer);
            }

            if (bandwidthEstimator != null) {
                recurringRunnableExecutor.deRegisterRecurringRunnable(bandwidthEstimator);
            }

            if (rtcpFeedbackTermination != null) {
                recurringRunnableExecutor.deRegisterRecurringRunnable(rtcpFeedbackTermination);
            }
        }
    }

    /**
     * Performs any optional configuration on a specific <code>RTPConnectorOuputStream</code> of an
     * <code>RTPManager</code> to be used by this <code>MediaStreamImpl</code>.
     *
     * @param dataOutputStream the <code>RTPConnectorOutputStream</code> to be used by an <code>RTPManager</code> of this
     * <code>MediaStreamImpl</code> and to be configured
     */
    @Override
    protected void configureDataOutputStream(RTPConnectorOutputStream dataOutputStream)
    {
        super.configureDataOutputStream(dataOutputStream);

        /*
         * XXX Android's current video CaptureDevice is based on MediaRecorder which gives no
         * control over the number and the size of the packets, frame dropping is not implemented
         * because it is hard since MediaRecorder generates encoded video.
         */
        if (!OSUtils.IS_ANDROID) {
            int maxBandwidth
                    = NeomediaServiceUtils.getMediaServiceImpl().getDeviceConfiguration().getVideoRTPPacingThreshold();

            // Ignore the case of maxBandwidth > 1000, because in this case
            // setMaxPacketsPerMillis fails. Effectively, this means that no pacing is performed
            // when the user deliberately set the setting to over 1000 (1MByte/s according to the
            // GUI). This is probably close to what the user expects, and makes more sense than
            // failing with an exception.
            // TODO: proper handling of maxBandwidth values >1000
            if (maxBandwidth <= 1000) {
                // maximum one packet for X milliseconds(the settings are for one second)
                dataOutputStream.setMaxPacketsPerMillis(1, 1000 / maxBandwidth);
            }
        }
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
        super.configureRTPManagerBufferControl(rtpManager, bufferControl);
        bufferControl.setBufferLength(BufferControl.MAX_VALUE);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public MediaStreamTrackReceiver getMediaStreamTrackReceiver()
    {
        return mediaStreamTrackReceiver;
    }

    /**
     * Notifies this <code>MediaStream</code> that the <code>MediaDevice</code> (and respectively the
     * <code>MediaDeviceSession</code> with it) which this instance uses for capture and playback of
     * media has been changed. Makes sure that the <code>VideoListener</code>s of this instance get
     * <code>VideoEvent</code>s for the new/current <code>VideoMediaDeviceSession</code> and not for the old one.
     * <p/>
     * Note: this overloaded method gets executed in the <code>MediaStreamImpl</code> constructor. As a
     * consequence we cannot assume proper initialization of the fields specific to <code>VideoMediaStreamImpl</code>.
     *
     * @param oldValue the <code>MediaDeviceSession</code> with the <code>MediaDevice</code> this instance used work with
     * @param newValue the <code>MediaDeviceSession</code> with the <code>MediaDevice</code> this instance is to work with
     * @see MediaStreamImpl#deviceSessionChanged(MediaDeviceSession, MediaDeviceSession)
     */
    @Override
    protected void deviceSessionChanged(MediaDeviceSession oldValue, MediaDeviceSession newValue)
    {
        super.deviceSessionChanged(oldValue, newValue);
        if (oldValue instanceof VideoMediaDeviceSession) {
            VideoMediaDeviceSession oldVideoMediaDeviceSession = (VideoMediaDeviceSession) oldValue;

            if (deviceSessionVideoListener != null)
                oldVideoMediaDeviceSession.removeVideoListener(deviceSessionVideoListener);

            /*
             * The oldVideoMediaDeviceSession is being disconnected from this VideoMediaStreamImpl
             * so do not let it continue using its keyFrameControl.
             */
            oldVideoMediaDeviceSession.setKeyFrameControl(null);
        }
        if (newValue instanceof VideoMediaDeviceSession) {
            VideoMediaDeviceSession newVideoMediaDeviceSession = (VideoMediaDeviceSession) newValue;

            if (deviceSessionVideoListener == null) {
                deviceSessionVideoListener = new VideoListener()
                {
                    /**
                     * {@inheritDoc}
                     *
                     * Notifies that a visual <code>Component</code> depicting video was reported added
                     * by the provider this listener is added to.
                     */
                    public void videoAdded(VideoEvent e)
                    {
                        if (fireVideoEvent(e.getType(), e.getVisualComponent(), e.getOrigin(), true))
                            e.consume();
                    }

                    /**
                     * {@inheritDoc}
                     *
                     * Notifies that a visual <code>Component</code> depicting video was reported
                     * removed by the provider this listener is added to.
                     */
                    public void videoRemoved(VideoEvent e)
                    {
                        // Process in the same way as VIDEO_ADDED.
                        videoAdded(e);
                    }

                    /**
                     * {@inheritDoc}
                     *
                     * Notifies that a visual <code>Component</code> depicting video was reported
                     * updated by the provider this listener is added to.
                     */
                    public void videoUpdate(VideoEvent e)
                    {
                        fireVideoEvent(e, true);
                    }
                };
            }
            newVideoMediaDeviceSession.addVideoListener(deviceSessionVideoListener);
            newVideoMediaDeviceSession.setOutputSize(outputSize);
            AbstractRTPConnector rtpConnector = getRTPConnector();

            if (rtpConnector != null)
                newVideoMediaDeviceSession.setConnector(rtpConnector);
            newVideoMediaDeviceSession.setRTCPFeedbackPLI(USE_RTCP_FEEDBACK_PLI);

            /*
             * The newVideoMediaDeviceSession is being connected to this VideoMediaStreamImpl so the key
             * frame-related logic will be controlled by the keyFrameControl of this VideoMediaStreamImpl.
             */
            newVideoMediaDeviceSession.setKeyFrameControl(getKeyFrameControl());
        }
    }

    /**
     * Notifies the <code>VideoListener</code>s registered with this <code>VideoMediaStream</code> about a
     * specific type of change in the availability of a specific visual <code>Component</code> depicting video.
     *
     * @param type the type of change as defined by <code>VideoEvent</code> in the availability of the
     * specified visual <code>Component</code> depicting video
     * @param visualComponent the visual <code>Component</code> depicting video which has been added or removed in this
     * <code>VideoMediaStream</code>
     * @param origin {@link VideoEvent#LOCAL} if the origin of the video is local (e.g. it is being locally
     * captured); {@link VideoEvent#REMOTE} if the origin of the video is remote (e.g. a remote peer is streaming it)
     * @param wait <code>true</code> if the call is to wait till the specified <code>VideoEvent</code> has been
     * delivered to the <code>VideoListener</code>s; otherwise, <code>false</code>
     * @return <code>true</code> if this event and, more specifically, the visual <code>Component</code> it
     * describes have been consumed and should be considered owned, referenced (which is important because
     * <code>Component</code>s belong to a single <code>Container</code> at a time); otherwise, <code>false</code>
     */
    protected boolean fireVideoEvent(int type, Component visualComponent, int origin, boolean wait)
    {
        Timber.log(TimberLog.FINER, "Firing VideoEvent with type %s and origin %s",
                VideoEvent.typeToString(type), VideoEvent.originToString(origin));

        return videoNotifierSupport.fireVideoEvent(type, visualComponent, origin, wait);
    }

    /**
     * Notifies the <code>VideoListener</code>s registered with this instance about a specific <code>VideoEvent</code>.
     *
     * @param event the <code>VideoEvent</code> to be fired to the <code>VideoListener</code>s registered with this instance
     * @param wait <code>true</code> if the call is to wait till the specified <code>VideoEvent</code> has been
     * delivered to the <code>VideoListener</code>s; otherwise, <code>false</code>
     */
    protected void fireVideoEvent(VideoEvent event, boolean wait)
    {
        videoNotifierSupport.fireVideoEvent(event, wait);
    }

    /**
     * Implements {@link VideoMediaStream#getKeyFrameControl()}.
     *
     * {@inheritDoc}
     *
     * @see VideoMediaStream#getKeyFrameControl()
     */
    public KeyFrameControl getKeyFrameControl()
    {
        if (keyFrameControl == null)
            keyFrameControl = new KeyFrameControlAdapter();
        return keyFrameControl;
    }

    /**
     * Gets the visual <code>Component</code>, if any, depicting the video streamed from the local peer to the remote peer.
     *
     * @return the visual <code>Component</code> depicting the local video if local video is actually
     * being streamed from the local peer to the remote peer; otherwise, <code>null</code>
     */
    public Component getLocalVisualComponent()
    {
        MediaDeviceSession deviceSession = getDeviceSession();
        return (deviceSession instanceof VideoMediaDeviceSession)
                ? ((VideoMediaDeviceSession) deviceSession).getLocalVisualComponent() : null;
    }

    /**
     * The priority of the video is 5, which is meant to be higher than other threads and lower than the audio one.
     *
     * @return video priority.
     */
    @Override
    protected int getPriority()
    {
        return 5;
    }

    /**
     * Gets the <code>QualityControl</code> of this <code>VideoMediaStream</code>.
     *
     * @return the <code>QualityControl</code> of this <code>VideoMediaStream</code>
     */
    public QualityControl getQualityControl()
    {
        return qualityControl;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public RemoteBitrateEstimatorWrapper getRemoteBitrateEstimator()
    {
        return remoteBitrateEstimator;
    }

    /**
     * Gets the visual <code>Component</code> where video from the remote peer is being rendered or
     * <code>null</code> if no video is currently being rendered.
     *
     * @return the visual <code>Component</code> where video from the remote peer is being rendered or
     * <code>null</code> if no video is currently being rendered
     * @see VideoMediaStream#getVisualComponent()
     */
    @Deprecated
    public Component getVisualComponent()
    {
        List<Component> visualComponents = getVisualComponents();
        return visualComponents.isEmpty() ? null : visualComponents.get(0);
    }

    /**
     * Gets the visual <code>Component</code>s rendering the <code>ReceiveStream</code> corresponding to the given ssrc.
     *
     * @param ssrc the src-id of the receive stream, which visual <code>Component</code> we're looking for
     * @return the visual <code>Component</code> rendering the <code>ReceiveStream</code> corresponding to the given ssrc
     */
    public Component getVisualComponent(long ssrc)
    {
        MediaDeviceSession deviceSession = getDeviceSession();
        return (deviceSession instanceof VideoMediaDeviceSession)
                ? ((VideoMediaDeviceSession) deviceSession).getVisualComponent(ssrc) : null;
    }

    /**
     * Gets a list of the visual <code>Component</code>s where video from the remote peer is being rendered.
     *
     * @return a list of the visual <code>Component</code>s where video from the remote peer is being rendered
     * @see VideoMediaStream#getVisualComponents()
     */
    public List<Component> getVisualComponents()
    {
        MediaDeviceSession deviceSession = getDeviceSession();
        List<Component> visualComponents;

        if (deviceSession instanceof VideoMediaDeviceSession) {
            visualComponents = ((VideoMediaDeviceSession) deviceSession).getVisualComponents();
        }
        else
            visualComponents = Collections.emptyList();
        return visualComponents;
    }

    /**
     * Handles attributes contained in <code>MediaFormat</code>.
     *
     * @param format the <code>MediaFormat</code> to handle the attributes of
     * @param attrs the attributes <code>Map</code> to handle
     */
    @Override
    protected void handleAttributes(MediaFormat format, Map<String, String> attrs)
    {
        // Keep a reference copy for use in selectVideoSize()
        mFormat = format;

        /*
         * Iterate over the specified attributes and handle those of them which we recognize.
         */
        if (attrs != null) {
            /*
             * The width and height attributes are separate but they have to be collected into a
             * Dimension in order to be handled.
             */
            String width = null;
            String height = null;
            Dimension dim;

            for (Map.Entry<String, String> attr : attrs.entrySet()) {
                String key = attr.getKey();
                String value = attr.getValue();

                switch (key) {
                    case "rtcp-fb":
                        // if (value.equals("nack pli")) USE_PLI = true;
                        break;

                    case "imageattr":
                        /*
                         * If the width and height attributes have been collected into
                         * outputSize, do not override the Dimension they have specified.
                         */
                        if ((attrs.containsKey("width") || attrs.containsKey("height")) && (outputSize != null)) {
                            continue;
                        }

                        Dimension[] res = parseSendRecvResolution(value);
                        setOutputSize(res[1]);
                        qualityControl.setRemoteSendMaxPreset(new QualityPreset(res[0]));
                        qualityControl.setRemoteReceiveResolution(outputSize);
                        ((VideoMediaDeviceSession) getDeviceSession()).setOutputSize(outputSize);
                        break;

                    case "CIF":
                        dim = new Dimension(352, 288);
                        if ((outputSize == null)
                                || ((outputSize.width < dim.width) && (outputSize.height < dim.height))) {
                            setOutputSize(dim);
                            ((VideoMediaDeviceSession) getDeviceSession()).setOutputSize(outputSize);
                        }
                        break;

                    case "QCIF":
                        dim = new Dimension(176, 144);
                        if ((outputSize == null)
                                || ((outputSize.width < dim.width) && (outputSize.height < dim.height))) {
                            setOutputSize(dim);
                            ((VideoMediaDeviceSession) getDeviceSession()).setOutputSize(outputSize);
                        }
                        break;

                    case "VGA":  // X-Lite sends it.
                        dim = new Dimension(640, 480);
                        if ((outputSize == null)
                                || ((outputSize.width < dim.width) && (outputSize.height < dim.height))) {
                            // X-Lite does not display anything if we send 640x480.
                            setOutputSize(dim);
                            ((VideoMediaDeviceSession) getDeviceSession()).setOutputSize(outputSize);
                        }
                        break;

                    case "CUSTOM":
                        String[] args = value.split(",");
                        if (args.length < 3)
                            continue;

                        try {
                            dim = new Dimension(Integer.parseInt(args[0]), Integer.parseInt(args[1]));

                            if ((outputSize == null)
                                    || ((outputSize.width < dim.width) && (outputSize.height < dim.height))) {
                                setOutputSize(dim);
                                ((VideoMediaDeviceSession) getDeviceSession()).setOutputSize(outputSize);
                            }
                        } catch (Exception e) {
                            Timber.e("Exception in handle attribute: %s", e.getMessage());
                        }
                        break;

                    case "width":
                        width = value;
                        if (height != null) {
                            setOutputSize(new Dimension(Integer.parseInt(width), Integer.parseInt(height)));
                            ((VideoMediaDeviceSession) getDeviceSession()).setOutputSize(outputSize);
                        }
                        break;

                    case "height":
                        height = value;
                        if (width != null) {
                            setOutputSize(new Dimension(Integer.parseInt(width), Integer.parseInt(height)));
                            ((VideoMediaDeviceSession) getDeviceSession()).setOutputSize(outputSize);
                        }
                        break;
                }
            }
        }
    }

    /**
     * Move origin of a partial desktop streaming <code>MediaDevice</code>.
     *
     * @param x new x coordinate origin
     * @param y new y coordinate origin
     */
    public void movePartialDesktopStreaming(int x, int y)
    {
        MediaDeviceImpl dev = (MediaDeviceImpl) getDevice();

        if (!DeviceSystem.LOCATOR_PROTOCOL_IMGSTREAMING.equals(dev.getCaptureDeviceInfoLocatorProtocol())) {
            return;
        }

        DataSource captureDevice = getDeviceSession().getCaptureDevice();
        Object imgStreamingControl = captureDevice.getControl(ImgStreamingControl.class.getName());

        if (imgStreamingControl == null)
            return;

        // Makes the screen detection with a point inside a real screen i.e.
        // x and y are both greater than or equal to 0.
        ScreenDevice screen = NeomediaServiceUtils.getMediaServiceImpl().getScreenForPoint(
                new Point(Math.max(x, 0), Math.max(y, 0)));

        if (screen != null) {
            Rectangle bounds = ((ScreenDeviceImpl) screen).getBounds();

            ((ImgStreamingControl) imgStreamingControl).setOrigin(0, screen.getIndex(),
                    x - bounds.x, y - bounds.y);
        }
    }

    /**
     * Notifies this <code>VideoMediaStreamImpl</code> that {@link #remoteBitrateEstimator} has
     * computed a new bitrate estimate for the incoming streams.
     *
     * @param ssrcs Remote source
     * @param bitrate Source bitRate
     */
    private void remoteBitrateEstimatorOnReceiveBitrateChanged(Collection<Long> ssrcs, long bitrate)
    {
        // TODO Auto-generated method stub
    }

    /**
     * Removes a specific <code>VideoListener</code> from this <code>VideoMediaStream</code> in order to have to
     * no longer receive notifications when visual/video <code>Component</code>s are being added and removed.
     *
     * @param listener the <code>VideoListener</code> to no longer be notified when visual/video
     * <code>Component</code>s are being added or removed in this <code>VideoMediaStream</code>
     */
    public void removeVideoListener(VideoListener listener)
    {
        videoNotifierSupport.removeVideoListener(listener);
    }

    /**
     * Notifies this <code>MediaStream</code> implementation that its <code>RTPConnector</code> instance
     * has changed from a specific old value to a specific new value. Allows extenders to
     * override and perform additional processing after this <code>MediaStream</code> has changed its
     * <code>RTPConnector</code> instance.
     *
     * @param oldValue the <code>RTPConnector</code> of this <code>MediaStream</code> implementation before it got
     * changed to <code>newValue</code>
     * @param newValue the current <code>RTPConnector</code> of this <code>MediaStream</code> which replaced <code>oldValue</code>
     * @see MediaStreamImpl#rtpConnectorChanged(AbstractRTPConnector, AbstractRTPConnector)
     */
    @Override
    protected void rtpConnectorChanged(AbstractRTPConnector oldValue, AbstractRTPConnector newValue)
    {
        super.rtpConnectorChanged(oldValue, newValue);
        if (newValue != null) {
            MediaDeviceSession deviceSession = getDeviceSession();

            Timber.w("rtpConnectorChanged: %s => %s", deviceSession.getClass().getSimpleName(),
                    newValue.getConnector().getDataSocket().getInetAddress());
            if (deviceSession instanceof VideoMediaDeviceSession) {
                ((VideoMediaDeviceSession) deviceSession).setConnector(newValue);
            }
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void setLocalSourceID(long localSourceID)
    {
        super.setLocalSourceID(localSourceID);
        MediaDeviceSession deviceSession = getDeviceSession();

        if (deviceSession instanceof VideoMediaDeviceSession) {
            ((VideoMediaDeviceSession) deviceSession).setLocalSSRC(localSourceID);
        }
    }

    /**
     * Sets the size/resolution of the video to be output by this instance.
     *
     * @param outputSize the size/resolution of the video to be output by this instance
     */
    private void setOutputSize(Dimension outputSize)
    {
        this.outputSize = outputSize;
    }

    /**
     * Updates the <code>QualityControl</code> of this <code>VideoMediaStream</code>.
     *
     * @param advancedParams parameters of advanced attributes that may affect quality control
     */
    public void updateQualityControl(Map<String, String> advancedParams)
    {
        for (Map.Entry<String, String> entry : advancedParams.entrySet()) {
            if (entry.getKey().equals("imageattr")) {
                Dimension[] res = parseSendRecvResolution(entry.getValue());

                qualityControl.setRemoteSendMaxPreset(new QualityPreset(res[0]));
                qualityControl.setRemoteReceiveResolution(res[1]);
                setOutputSize(res[1]);
                ((VideoMediaDeviceSession) getDeviceSession()).setOutputSize(outputSize);
            }
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected CachingTransformer createCachingTransformer()
    {
        if (cachingTransformer == null) {
            cachingTransformer = new CachingTransformer(this);
            recurringRunnableExecutor.registerRecurringRunnable(cachingTransformer);
        }
        return cachingTransformer;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected RetransmissionRequesterImpl createRetransmissionRequester()
    {
        ConfigurationService cfg = LibJitsi.getConfigurationService();
        if (cfg != null && cfg.getBoolean(REQUEST_RETRANSMISSIONS_PNAME, false)) {
            return new RetransmissionRequesterImpl(this);
        }
        return null;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected TransformEngine getRTCPTermination()
    {
        return rtcpFeedbackTermination;
    }

    /**
     * {@inheritDoc}
     */
    protected PaddingTermination getPaddingTermination()
    {
        return paddingTermination;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public BandwidthEstimator getOrCreateBandwidthEstimator()
    {
        if (bandwidthEstimator == null) {
            bandwidthEstimator = new BandwidthEstimatorImpl(this);
            recurringRunnableExecutor.registerRecurringRunnable(bandwidthEstimator);
            Timber.i("Creating a BandwidthEstimator for stream %s", this);
        }
        return bandwidthEstimator;
    }
}
