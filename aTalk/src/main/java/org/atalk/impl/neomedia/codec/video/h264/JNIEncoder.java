/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.atalk.impl.neomedia.codec.video.h264;

import org.atalk.impl.timberlog.TimberLog;
import org.atalk.impl.neomedia.NeomediaServiceUtils;
import org.atalk.impl.neomedia.codec.AbstractCodec2;
import org.atalk.impl.neomedia.codec.FFmpeg;
import org.atalk.impl.neomedia.device.DeviceConfiguration;
import org.atalk.impl.neomedia.format.ParameterizedVideoFormat;
import org.atalk.impl.neomedia.format.VideoMediaFormatImpl;
import org.atalk.service.configuration.ConfigurationService;
import org.atalk.service.libjitsi.LibJitsi;
import org.atalk.service.neomedia.codec.Constants;
import org.atalk.service.neomedia.control.KeyFrameControl;
import org.atalk.service.neomedia.event.RTCPFeedbackMessageEvent;
import org.atalk.service.neomedia.event.RTCPFeedbackMessageListener;

import java.awt.Dimension;
import java.util.HashMap;
import java.util.Map;

import javax.media.Buffer;
import javax.media.Format;
import javax.media.ResourceUnavailableException;
import javax.media.format.VideoFormat;
import javax.media.format.YUVFormat;

import timber.log.Timber;

/**
 * Implements an FMJ H.264 encoder using FFmpeg (and x264).
 *
 * @author Damian Minkov
 * @author Lyubomir Marinov
 * @author Sebastien Vincent
 * @author Eng Chong Meng
 */
public class JNIEncoder extends AbstractCodec2
        implements RTCPFeedbackMessageListener
{
    /**
     * The available presets we can use with the encoder.
     */
    public static final String[] AVAILABLE_PRESETS
            = {
            "ultrafast",
            "superfast",
            "veryfast",
            "faster",
            "fast",
            "medium",
            "slow",
            "slower",
            "veryslow"
    };

    /**
     * The name of the baseline H.264 (encoding) profile.
     */
    public static final String BASELINE_PROFILE = "baseline";

    /**
     * The default value of the {@link #DEFAULT_INTRA_REFRESH_PNAME} <code>ConfigurationService</code> property.
     */
    public static final boolean DEFAULT_DEFAULT_INTRA_REFRESH = true;

    /**
     * The name of the main H.264 (encoding) profile.
     */
    public static final String MAIN_PROFILE = "main";

    /**
     * The default value of the {@link #DEFAULT_PROFILE_PNAME} <code>ConfigurationService</code> property.
     */
    public static final String DEFAULT_DEFAULT_PROFILE = BASELINE_PROFILE;

    /**
     * The frame rate to be assumed by <code>JNIEncoder</code> instances in the absence of any other frame rate indication.
     */
    public static final int DEFAULT_FRAME_RATE = 15;

    /**
     * The name of the boolean <code>ConfigurationService</code> property which specifies whether Periodic
     * Intra Refresh is to be used by default. The default value is <code>true</code>.
     * The value may be overridden by {@link #setAdditionalCodecSettings(Map)}.
     */
    public static final String DEFAULT_INTRA_REFRESH_PNAME = "neomedia.codec.video.h264.defaultIntraRefresh";

    /**
     * The default maximum GOP (group of pictures) size i.e. the maximum interval between
     * keyframes. The x264 library defaults to 250.
     */
    public static final int DEFAULT_KEYINT = 150;

    /**
     * The default value of the {@link #PRESET_PNAME} <code>ConfigurationService</code> property.
     */
    public static final String DEFAULT_PRESET = AVAILABLE_PRESETS[0];

    /**
     * The name of the <code>ConfigurationService</code> property which specifies the H.264 (encoding)
     * profile to be used in the absence of negotiation. Though it seems that RFC 3984 "RTP
     * Payload Format for H.264 Video" specifies the baseline profile as the default, we have
     * till the time of this writing defaulted to the main profile and we do not currently want
     * to change from the main to the base profile unless we really have to.
     */
    public static final String DEFAULT_PROFILE_PNAME = "neomedia.codec.video.h264.defaultProfile";

    /**
     * The name of the high H.264 (encoding) profile.
     */
    public static final String HIGH_PROFILE = "high";

    /**
     * The name of the integer <code>ConfigurationService</code> property which specifies the maximum
     * GOP (group of pictures) size i.e. the maximum interval between keyframes. FFmpeg calls it
     * <code>gop_size</code>, x264 refers to it as <code>keyint</code> or <code>i_keyint_max</code>.
     */
    public static final String KEYINT_PNAME = "neomedia.codec.video.h264.keyint";

    /**
     * The minimum interval between two PLI request processing (in milliseconds).
     */
    private static final long PLI_INTERVAL = 3000;

    /**
     * The name of the <code>ConfigurationService</code> property which specifies the x264 preset to
     * be used by <code>JNIEncoder</code>. A preset is a collection of x264 options that will provide
     * a certain encoding speed to compression ratio. A slower preset will provide better
     * compression i.e. quality per size.
     */
    public static final String PRESET_PNAME = "neomedia.codec.video.h264.preset";

    /**
     * The list of <code>Formats</code> supported by <code>JNIEncoder</code> instances as output.
     */
    static final Format[] SUPPORTED_OUTPUT_FORMATS = {
            new ParameterizedVideoFormat(Constants.H264, VideoMediaFormatImpl.H264_PACKETIZATION_MODE_FMTP, "0"),
            new ParameterizedVideoFormat(Constants.H264, VideoMediaFormatImpl.H264_PACKETIZATION_MODE_FMTP, "1")};

    public static final int X264_KEYINT_MAX_INFINITE = 1 << 30;

    public static final int X264_KEYINT_MIN_AUTO = 0;

    /**
     * Checks the configuration and returns the profile to use.
     *
     * @param profile the profile setting.
     * @return the profile FFmpeg to use.
     */
    private static int getProfileForConfig(String profile)
    {
        if (BASELINE_PROFILE.equalsIgnoreCase(profile))
            return FFmpeg.FF_PROFILE_H264_BASELINE;
        else if (HIGH_PROFILE.equalsIgnoreCase(profile))
            return FFmpeg.FF_PROFILE_H264_HIGH;
        else
            return FFmpeg.FF_PROFILE_H264_MAIN;
    }

    /**
     * The additional settings of this <code>Codec</code>.
     */
    private Map<String, String> additionalCodecSettings;

    /**
     * The codec we will use.
     */
    private long avctx;

    /**
     * The encoded data is stored in avpicture.
     */
    private long avFrame;

    /**
     * The indicator which determines whether the generation of a keyframe is to be forced during
     * a subsequent execution of {@link #process(Buffer, Buffer)}. The first frame to undergo
     * encoding is naturally a keyframe and, for the sake of clarity, the initial value is <code>true</code>.
     */
    private boolean forceKeyFrame = true;

    /**
     * The <code>KeyFrameControl</code> used by this <code>JNIEncoder</code> to control its key frame-related logic.
     */
    private KeyFrameControl keyFrameControl;

    private KeyFrameControl.KeyFrameRequestee keyFrameRequestee;

    /**
     * The maximum GOP (group of pictures) size i.e. the maximum interval between keyframes (with
     * which {@link #open()} has been invoked without an intervening {@link #close()}). FFmpeg
     * calls it <code>gop_size</code>, x264 refers to it as <code>keyint</code> or <code>i_keyint_max</code>.
     */
    private int keyint;

    /**
     * The number of frames processed since the last keyframe.
     */
    private int lastKeyFrame;

    /**
     * The time in milliseconds of the last request for a key frame from the remote peer to this local peer.
     */
    private long lastKeyFrameRequestTime = System.currentTimeMillis();

    /**
     * The packetization mode to be used for the H.264 RTP payload output by this <code>JNIEncoder</code>,
     * and the associated packetizer. RFC 3984 "RTP Payload Format for H.264 Video" says that
     * "when the value of packetization-mode is equal to 0 or packetization-mode is not present,
     * the single NAL mode, as defined in section 6.2 of RFC 3984, MUST be used."
     */
    private String packetizationMode;

    /**
     * The raw frame buffer.
     */
    private long rawFrameBuffer;

    /**
     * Length of the raw frame buffer. Once the dimensions are known, this is set to 3/2 *
     * (height*width), which is the size needed for a YUV420 frame.
     */
    private int rawFrameLen;

    /**
     * The indicator which determines whether two consecutive frames at the beginning of the video
     * transmission have been encoded as keyframes. The first frame is a keyframe but it is at
     * the very beginning of the video transmission and, consequently, there is a higher risk
     * that pieces of it will be lost on their way through the network. To mitigate possible
     * issues in the case of network loss, the second frame is also a keyframe.
     */
    private boolean secondKeyFrame = true;

    @SuppressWarnings("SuspiciousNameCombination")
    private int mWidth = DeviceConfiguration.DEFAULT_VIDEO_HEIGHT;
    @SuppressWarnings("SuspiciousNameCombination")
    private int mHeight = DeviceConfiguration.DEFAULT_VIDEO_WIDTH;

    /**
     * Initializes a new <code>JNIEncoder</code> instance.
     */
    public JNIEncoder()
    {
        super("H.264 Encoder", VideoFormat.class, SUPPORTED_OUTPUT_FORMATS);

        inputFormats = new Format[]{new YUVFormat(
                null, /* size */
                Format.NOT_SPECIFIED, /* maxDataLength */
                Format.byteArray,
                Format.NOT_SPECIFIED, /* frameRate */
                YUVFormat.YUV_420,
                Format.NOT_SPECIFIED, /* strideY */
                Format.NOT_SPECIFIED, /* strideUV */
                Format.NOT_SPECIFIED, /* offsetY */
                Format.NOT_SPECIFIED, /* offsetU */
                Format.NOT_SPECIFIED) /* offsetV */
        };
    }

    /**
     * Closes this <code>Codec</code>.
     */
    @Override
    protected void doClose()
    {
        if (avctx != 0) {
            FFmpeg.avcodec_close(avctx);
            FFmpeg.av_free(avctx);
            avctx = 0;
        }

        if (avFrame != 0) {
            FFmpeg.avcodec_free_frame(avFrame);
            avFrame = 0;
        }
        if (rawFrameBuffer != 0) {
            FFmpeg.av_free(rawFrameBuffer);
            rawFrameBuffer = 0;
        }

        if (keyFrameRequestee != null) {
            if (keyFrameControl != null)
                keyFrameControl.removeKeyFrameRequestee(keyFrameRequestee);
            keyFrameRequestee = null;
        }
    }

    /**
     * Opens this <code>Codec</code>.
     */
    @Override
    protected void doOpen() throws ResourceUnavailableException
    {
        /*
         * An Encoder translates raw media data in (en)coded media data.
         * Consequently, the size of the output is equal to the size of the input.
         */
        VideoFormat ipFormat = (VideoFormat) inputFormat;
        VideoFormat opFormat = (VideoFormat) outputFormat;

        Dimension size = null;
        if (ipFormat != null)
            size = ipFormat.getSize();
        if ((size == null) && (opFormat != null))
            size = opFormat.getSize();

        // Use the default if format size is null
        if (size != null) {
            Timber.d("H264 encode video size: %s", size);
            mWidth = size.width;
            mHeight = size.height;
        }

        /*
         * XXX We do not currently negotiate the profile so, regardless of the many AVCodecContext
         * properties we have set above, force the default profile configuration.
         */
        ConfigurationService config = LibJitsi.getConfigurationService();
        boolean intraRefresh = DEFAULT_DEFAULT_INTRA_REFRESH;
        int keyint = DEFAULT_KEYINT;
        String preset = DEFAULT_PRESET;
        String profile = DEFAULT_DEFAULT_PROFILE;

        if (config != null) {
            intraRefresh = config.getBoolean(DEFAULT_INTRA_REFRESH_PNAME, intraRefresh);
            keyint = config.getInt(KEYINT_PNAME, keyint);
            preset = config.getString(PRESET_PNAME, preset);
            profile = config.getString(DEFAULT_PROFILE_PNAME, profile);
        }

        if (additionalCodecSettings != null) {
            for (Map.Entry<String, String> e : additionalCodecSettings.entrySet()) {
                String k = e.getKey();
                String v = e.getValue();

                if ("h264.intrarefresh".equals(k)) {
                    if ("false".equals(v))
                        intraRefresh = false;
                }
                else if ("h264.profile".equals(k)) {
                    if (BASELINE_PROFILE.equals(v)
                            || HIGH_PROFILE.equals(v)
                            || MAIN_PROFILE.equals(v))
                        profile = v;
                }
            }
        }

        long avcodec = FFmpeg.avcodec_find_encoder(FFmpeg.CODEC_ID_H264);
        if (avcodec == 0) {
            throw new ResourceUnavailableException("Could not find H.264 encoder.");
        }

        avctx = FFmpeg.avcodec_alloc_context3(avcodec);
        FFmpeg.avcodeccontext_set_pix_fmt(avctx, FFmpeg.PIX_FMT_YUV420P);
        FFmpeg.avcodeccontext_set_size(avctx, mWidth, mHeight);
        FFmpeg.avcodeccontext_set_qcompress(avctx, 0.6f);

        int bitRate = 1000 * NeomediaServiceUtils.getMediaServiceImpl().getDeviceConfiguration().getVideoBitrate();
        int frameRate = Format.NOT_SPECIFIED;

        // Allow the outputFormat to request a certain frameRate.
        if (opFormat != null)
            frameRate = (int) opFormat.getFrameRate();
        // Otherwise, output in the frameRate of the inputFormat.
        if ((frameRate == Format.NOT_SPECIFIED) && (ipFormat != null))
            frameRate = (int) ipFormat.getFrameRate();
        if (frameRate == Format.NOT_SPECIFIED)
            frameRate = DEFAULT_FRAME_RATE;

        // average bit rate
        FFmpeg.avcodeccontext_set_bit_rate(avctx, bitRate);
        // so to be 1 in x264
        FFmpeg.avcodeccontext_set_bit_rate_tolerance(avctx, (bitRate / frameRate));
        FFmpeg.avcodeccontext_set_rc_max_rate(avctx, bitRate);
        FFmpeg.avcodeccontext_set_sample_aspect_ratio(avctx, 0, 0);
        FFmpeg.avcodeccontext_set_thread_count(avctx, 1);

        // time_base should be 1 / frame rate
        FFmpeg.avcodeccontext_set_time_base(avctx, 1, frameRate);
        FFmpeg.avcodeccontext_set_ticks_per_frame(avctx, 2);
        FFmpeg.avcodeccontext_set_quantizer(avctx, 30, 31, 4);

        // avctx.chromaoffset = -2;
        FFmpeg.avcodeccontext_set_mb_decision(avctx, FFmpeg.FF_MB_DECISION_SIMPLE);
        FFmpeg.avcodeccontext_add_flags(avctx, FFmpeg.CODEC_FLAG_LOOP_FILTER);

        FFmpeg.avcodeccontext_set_me_subpel_quality(avctx, 2);
        FFmpeg.avcodeccontext_set_me_range(avctx, 16);
        FFmpeg.avcodeccontext_set_me_cmp(avctx, FFmpeg.FF_CMP_CHROMA);
        FFmpeg.avcodeccontext_set_scenechange_threshold(avctx, 40);
        FFmpeg.avcodeccontext_set_rc_buffer_size(avctx, 10);
        FFmpeg.avcodeccontext_set_gop_size(avctx, keyint);
        FFmpeg.avcodeccontext_set_i_quant_factor(avctx, 1f / 1.4f);

        FFmpeg.avcodeccontext_set_refs(avctx, 1);
        // FFmpeg.avcodeccontext_set_trellis(avctx, 2);

        FFmpeg.avcodeccontext_set_keyint_min(avctx, X264_KEYINT_MIN_AUTO);

        if ((null == packetizationMode) || "0".equals(packetizationMode)) {
            FFmpeg.avcodeccontext_set_rtp_payload_size(avctx, Packetizer.MAX_PAYLOAD_SIZE);
        }

        try {
            FFmpeg.avcodeccontext_set_profile(avctx, getProfileForConfig(profile));
        } catch (UnsatisfiedLinkError ule) {
            Timber.w("The FFmpeg JNI library is out-of-date.");
        }

        /*
         * XXX crf=0 means lossless coding which is not supported by the baseline and main
         * profiles. Consequently, we cannot specify it because we specify either the
         * baseline or the main profile. Otherwise, x264 will detect the inconsistency in the
         * specified parameters/options and FFmpeg will fail.
         */
        if (FFmpeg.avcodec_open2(avctx, avcodec,
                // "crf", "0",  /* constant quality mode, constant rate factor */
                "intra-refresh", intraRefresh ? "1" : "0",
                "keyint", Integer.toString(keyint),
                "partitions", "b8x8,i4x4,p8x8",
                "preset", preset,
                "thread_type", "slice",
                "tune", "zerolatency") < 0) {
            throw new ResourceUnavailableException("Could not open H.264 encoder. (size= " + mWidth + "x" + mHeight + ")");
        }

        rawFrameLen = (mWidth * mHeight * 3) / 2;
        rawFrameBuffer = FFmpeg.av_malloc(rawFrameLen);

        avFrame = FFmpeg.avcodec_alloc_frame();
        // Required to be set for ffmpeg v4.4
        FFmpeg.avframe_set_properties(avFrame, FFmpeg.PIX_FMT_YUV420P, mWidth, mHeight);

        int sizeInBytes = mWidth * mHeight;
        FFmpeg.avframe_set_data(avFrame, rawFrameBuffer, sizeInBytes, sizeInBytes / 4);
        FFmpeg.avframe_set_linesize(avFrame, mWidth, mWidth / 2, mWidth / 2);

        /*
         * In order to be sure keyint will be respected, we will implement it ourselves
         * (regardless of the fact that we have told FFmpeg and x264 about it). Otherwise, we may
         * end up not generating keyframes at all (apart from the two generated after open).
         */
        forceKeyFrame = true;
        this.keyint = keyint;
        lastKeyFrame = 0;

        /*
         * Implement the ability to have the remote peer request key frames from this local peer.
         */
        if (keyFrameRequestee == null) {
            keyFrameRequestee = JNIEncoder.this::keyFrameRequest;
        }
        if (keyFrameControl != null)
            keyFrameControl.addKeyFrameRequestee(-1, keyFrameRequestee);
    }

    /**
     * Processes/encodes a buffer.
     *
     * @param inBuffer input buffer
     * @param outBuffer output buffer
     * @return <code>BUFFER_PROCESSED_OK</code> if buffer has been successfully processed
     */
    @Override
    protected int doProcess(Buffer inBuffer, Buffer outBuffer)
    {
        YUVFormat format = (YUVFormat) inBuffer.getFormat();
        Dimension formatSize = format.getSize();
        int width = formatSize.width;
        int height = formatSize.height;

        if (width > 0 && height > 0
                && (width != mWidth || height != mHeight)) {
            Timber.d("H264 encode video size changed: [width=%s, height=%s]=>%s",  mWidth, mHeight, formatSize);

            doClose();
            try {
                doOpen();
            } catch (ResourceUnavailableException e) {
                Timber.e("Could not find H.264 encoder.");
            }
        }

        if (inBuffer.getLength() < 10) {
            outBuffer.setDiscard(true);
            reset();
            return BUFFER_PROCESSED_OK;
        }

        // Copy the data of inBuffer into avFrame.
        FFmpeg.memcpy(rawFrameBuffer, (byte[]) inBuffer.getData(), inBuffer.getOffset(), rawFrameLen);

        boolean keyFrame = isKeyFrame();
        FFmpeg.avframe_set_key_frame(avFrame, keyFrame);
        /*
         * In order to be sure that keyint will be respected, we will implement it ourselves
         * (regardless of the fact that we have told FFmpeg and x264 about it). Otherwise, we may
         * end up not generating keyframes at all (apart from the two generated after open).
         */
        if (keyFrame)
            lastKeyFrame = 0;
        else
            lastKeyFrame++;

        // Encode avFrame into the data of outBuffer.
        byte[] out = AbstractCodec2.validateByteArraySize(outBuffer, rawFrameLen, false);
        int outLength = FFmpeg.avcodec_encode_video(avctx, out, out.length, avFrame);

        outBuffer.setLength(outLength);
        outBuffer.setOffset(0);
        outBuffer.setTimeStamp(inBuffer.getTimeStamp());
        return BUFFER_PROCESSED_OK;
    }

    /**
     * Gets the matching output formats for a specific format.
     *
     * @param inputFormat input format
     * @return array for formats matching input format
     */
    protected Format[] getMatchingOutputFormats(Format inputFormat)
    {
        VideoFormat inputVideoFormat = (VideoFormat) inputFormat;

        String[] packetizationModes = (this.packetizationMode == null)
                ? new String[]{"0", "1"} : new String[]{this.packetizationMode};
        Format[] matchingOutputFormats = new Format[packetizationModes.length];
        Dimension size = inputVideoFormat.getSize();
        float frameRate = inputVideoFormat.getFrameRate();

        for (int index = packetizationModes.length - 1; index >= 0; index--) {
            matchingOutputFormats[index] = new ParameterizedVideoFormat(
                    Constants.H264, size,
                    Format.NOT_SPECIFIED, /* maxDataLength */
                    Format.byteArray,
                    frameRate,
                    ParameterizedVideoFormat.toMap(VideoMediaFormatImpl.H264_PACKETIZATION_MODE_FMTP, packetizationModes[index]));
        }
        return matchingOutputFormats;
    }

    /**
     * Determines whether the encoding of {@link #avFrame} is to produce a keyframe. The returned
     * value will be set on <code>avFrame</code> via a call to
     * {@link FFmpeg#avframe_set_key_frame(long, boolean)}.
     *
     * @return <code>true</code> if the encoding of <code>avFrame</code> is to produce a keyframe; otherwise <code>false</code>
     */
    private boolean isKeyFrame()
    {
        boolean keyFrame;

        if (forceKeyFrame) {
            keyFrame = true;

            /*
             * The first frame is a keyframe, but it is at the very beginning of the video
             * transmission and, consequently, there is a higher risk that pieces of it will be
             * lost on their way through the network. To mitigate possible issues in the case of
             * network loss, the second frame is also a keyframe.
             */
            if (secondKeyFrame) {
                secondKeyFrame = false;
                forceKeyFrame = true;
            }
            else
                forceKeyFrame = false;
        }
        else {
            /*
             * In order to be sure that keyint will be respected, we will implement it ourselves
             * (regardless of the fact that we have told FFmpeg and x264 about it). Otherwise, we
             * may end up not generating keyframes at all (apart from the two generated after
             * open).
             */
            keyFrame = (lastKeyFrame == keyint);
        }

        return keyFrame;
    }

    /**
     * Notifies this <code>JNIEncoder</code> that the remote peer has requested a key frame from this Local peer.
     *
     * @return <code>true</code> if this <code>JNIEncoder</code> has honored the request for a key frame; otherwise <code>false</code>
     */
    private boolean keyFrameRequest()
    {
        long now = System.currentTimeMillis();

        if (now > (lastKeyFrameRequestTime + PLI_INTERVAL)) {
            lastKeyFrameRequestTime = now;
            forceKeyFrame = true;
        }
        return true;
    }

    /**
     * Notifies this <code>RTCPFeedbackListener</code> that an RTCP feedback message has been received
     *
     * @param ev an <code>RTCPFeedbackMessageEvent</code> which specifies the details of the notification
     * event such as the feedback message type and the payload type
     */
    @Override
    public void rtcpFeedbackMessageReceived(RTCPFeedbackMessageEvent ev)
    {
        /*
         * If RTCP message is a Picture Loss Indication (PLI) or a Full Intra-frame Request (FIR)
         * the encoder will force the next frame to be a keyframe.
         */
        if (ev.getPayloadType() == RTCPFeedbackMessageEvent.PT_PS) {
            switch (ev.getFeedbackMessageType()) {
                case RTCPFeedbackMessageEvent.FMT_PLI:
                case RTCPFeedbackMessageEvent.FMT_FIR:
                    Timber.log(TimberLog.FINER, "Scheduling a key-frame, because we received an RTCP PLI or FIR.");
                    keyFrameRequest();
                    break;
                default:
                    break;
            }
        }
    }

    /**
     * Sets additional settings on this <code>Codec</code>.
     *
     * @param additionalCodecSettings the additional settings to be set on this <code>Codec</code>
     */
    public void setAdditionalCodecSettings(Map<String, String> additionalCodecSettings)
    {
        this.additionalCodecSettings = additionalCodecSettings;
    }

    /**
     * Sets the <code>KeyFrameControl</code> to be used by this <code>JNIEncoder</code> as a means of
     * control over its key frame-related logic.
     *
     * @param keyFrameControl the <code>KeyFrameControl</code> to be used by this <code>JNIEncoder</code> as a means of
     * control over its key frame-related logic
     */
    public void setKeyFrameControl(KeyFrameControl keyFrameControl)
    {
        if (this.keyFrameControl != keyFrameControl) {
            if ((this.keyFrameControl != null) && (keyFrameRequestee != null))
                this.keyFrameControl.removeKeyFrameRequestee(keyFrameRequestee);

            this.keyFrameControl = keyFrameControl;

            if ((this.keyFrameControl != null) && (keyFrameRequestee != null))
                this.keyFrameControl.addKeyFrameRequestee(-1, keyFrameRequestee);
        }
    }

    /**
     * Sets the <code>Format</code> in which this <code>Codec</code> is to output media data.
     *
     * @param format the <code>Format</code> in which this <code>Codec</code> is to output media data
     * @return the <code>Format</code> in which this <code>Codec</code> is currently configured to output
     * media data or <code>null</code> if <code>format</code> was found to be incompatible with this <code>Codec</code>
     */
    @Override
    public Format setOutputFormat(Format format)
    {
        //  Return null if mismatch output format
        if (!(format instanceof VideoFormat)
                || (null == AbstractCodec2.matches(format, getMatchingOutputFormats(inputFormat))))
            return null;

        VideoFormat videoFormat = (VideoFormat) format;
        /*
         * An Encoder translates raw media data in (en)coded media data. Consequently, the size of
         * the output is equal to the size of the input.
         */
        Dimension size = null;

        if (inputFormat != null)
            size = ((VideoFormat) inputFormat).getSize();
        if ((size == null) && format.matches(outputFormat))
            size = ((VideoFormat) outputFormat).getSize();

        Map<String, String> fmtps = null;

        if (format instanceof ParameterizedVideoFormat)
            fmtps = ((ParameterizedVideoFormat) format).getFormatParameters();
        if (fmtps == null)
            fmtps = new HashMap<>();
        if (packetizationMode != null) {
            fmtps.put(VideoMediaFormatImpl.H264_PACKETIZATION_MODE_FMTP, packetizationMode);
        }

        outputFormat = new ParameterizedVideoFormat(
                videoFormat.getEncoding(),
                size,
                Format.NOT_SPECIFIED, /* maxDataLength */
                Format.byteArray,
                videoFormat.getFrameRate(),
                fmtps);

        // Return the selected outputFormat
        return outputFormat;
    }

    /**
     * Sets the packetization mode to be used for the H.264 RTP payload output by this <code>JNIEncoder</code>,
     * and the associated packetizer.
     *
     * @param packetizationMode the packetization mode to be used for the H.264 RTP payload output by this
     * <code>JNIEncoder</code> and the associated packetizer
     */
    public void setPacketizationMode(String packetizationMode)
    {
        /*
         * RFC 3984 "RTP Payload Format for H.264 Video", packetization-mode:
         * This parameter signals the properties of an RTP payload type or the capabilities of a receiver implementation.
         * Only a single configuration point can be indicated; thus, when capabilities to support more than one
         * packetization-mode are declared, multiple configuration points (RTP payload types) must be used.
         *
         * The value of packetization mode MUST be an integer in the range of 0 to 2, inclusive.
         * a. When the value of packetization-mode is equal to 0 or packetization-mode is not present,
         *    the single NAL mode, as defined in section 6.2 of RFC 3984, MUST be used.
         * b. When the value of packetization-mode is equal to 1, the non- interleaved mode,
         *    as defined in section 6.3 of RFC 3984, MUST be used.
         * c. When the value of packetization-mode is equal to 2, the interleaved mode,
         *    as defined in section 6.4 of RFC 3984, MUST be used.
         */
        if ((packetizationMode == null) || "0".equals(packetizationMode))
            this.packetizationMode = "0";
        else if ("1".equals(packetizationMode))
            this.packetizationMode = "1";
        else if ("2".equals(packetizationMode))
            this.packetizationMode = "2";
        else
            throw new IllegalArgumentException("packetizationMode");
    }
}
