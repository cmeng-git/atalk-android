/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.atalk.impl.neomedia.codec;

import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaCodecList;
import android.media.MediaFormat;

import org.atalk.impl.neomedia.codec.video.AVFrame;
import org.atalk.impl.neomedia.codec.video.AVFrameFormat;
import org.atalk.impl.neomedia.codec.video.ByteBuffer;
import org.atalk.impl.neomedia.jmfext.media.protocol.ByteBufferPool;
import org.atalk.service.neomedia.codec.Constants;

import java.awt.Dimension;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;

import javax.media.Buffer;
import javax.media.Format;
import javax.media.ResourceUnavailableException;
import javax.media.format.VideoFormat;

import timber.log.Timber;

/**
 * Implements an FMJ <code>Codec</code> using Android's {@link MediaCodec}.
 *
 * @author Lyubomir Marinov
 * @author Eng Chong Meng
 */
public class AndroidMediaCodec extends AbstractCodec2 {
    /**
     * The interval of time in microseconds to wait for {@link MediaCodec#dequeueInputBuffer(long)}
     * to dequeue an input buffer.
     */
    private static final long DEQUEUE_INPUT_BUFFER_TIMEOUT = /* second */ 1000000 / 30;
    /* frames per second * /

    /**
     * The map of FMJ <code>Format</code> encodings to <code>MediaCodec</code> mime types which allows
     * converting between the two.
     */
    private static final String[] FMJ_ENCODINGS_TO_MEDIA_CODEC_TYPES;

    /**
     * The mime type of H.264-encoded media data as defined by Android's <code>MediaCodec</code> class.
     */
    private static final String H264_MEDIA_CODEC_TYPE = "video/avc";

    /**
     * The constant defined by OpenMAX IL to signify that a <code>colorFormat</code> value defined in
     * the terms of Android's <code>MediaCodec</code> class is unknown.
     */
    private static final int OMX_COLOR_FormatUnused = 0;

    /**
     * The map of <code>FFmpeg</code> pixel formats to <code>MediaCodec</code> <code>colorFormat</code>s which
     * allows converting between the two.
     */
    private static final int[] PIX_FMTS_TO_MEDIA_CODEC_COLOR_FORMATS;

    /**
     * The list of <code>Format</code>s of media data supported as input by <code>AndroidMediaCodec</code> instances.
     */
    private static final Format[] SUPPORTED_INPUT_FORMATS;

    /**
     * The list of <code>Format</code>s of media data supported as output by <code>AndroidMediaCodec</code> instances.
     */
    private static final Format[] SUPPORTED_OUTPUT_FORMATS;

    /**
     * The mime type of VP8-encoded media data as defined by Android's <code>MediaCodec</code> class.
     */
    private static final String VP8_MEDIA_CODEC_TYPE = "video/x-vnd.on2.vp8";

    /**
     * The mime type of VP9-encoded media data as defined by Android's <code>MediaCodec</code> class.
     */
    private static final String VP9_MEDIA_CODEC_TYPE = "video/x-vnd.on2.vp9";

    static {
        /*
         * AndroidMediaCodec is an FMJ Codec and, consequently, defines the various formats of media
         * (data) in FMJ's terms. MediaCodec is defined in its own (Android) terms. Make it possible
         * to translate between the two domains of terms.
         */
        FMJ_ENCODINGS_TO_MEDIA_CODEC_TYPES = new String[]{
                Constants.H264, H264_MEDIA_CODEC_TYPE,
                Constants.VP9, VP9_MEDIA_CODEC_TYPE,
                Constants.VP8, VP8_MEDIA_CODEC_TYPE};
        PIX_FMTS_TO_MEDIA_CODEC_COLOR_FORMATS = new int[]{
                FFmpeg.PIX_FMT_NV12, MediaCodecInfo.CodecCapabilities.COLOR_TI_FormatYUV420PackedSemiPlanar};

        /*
         * The Formats supported by AndroidMediaCodec as input and output are the mime types and
         * colorFormats (in the cases of video) supported by the MediaCodecs available on the Android system.
         */

        /*
         * We'll keep the list of FMJ VideoFormats equivalent to MediaCodecInfo.CodecCapabilities
         * colorFormats out of the loop bellow in order to minimize the production of garbage.
         */
        List<Format> bSupportedFormats = null;
        List<Format> supportedInputFormats = new ArrayList<>();
        List<Format> supportedOutputFormats = new ArrayList<>();

        MediaCodecInfo[] mCodecInfos = new MediaCodecList(MediaCodecList.REGULAR_CODECS).getCodecInfos();
        for (MediaCodecInfo codecInfo : mCodecInfos) {

            String[] supportedTypes = codecInfo.getSupportedTypes();
            for (String supportedType : supportedTypes) {
                /*
                 * Represent Android's MediaCodec mime type in the terms of FMJ (i.e. as an FMJ
                 * Format) because AndroidMediaCodec implements an FMJ Codec.
                 */
                Format aSupportedFormat = getFmjFormatFromMediaCodecType(supportedType);

                if (aSupportedFormat == null)
                    continue;

                /*
                 * Android's mime type will determine either the supported input Format or the
                 * supported output Format of AndroidMediaCodec. The colorFormats will determine the
                 * other half of the information related to the supported Formats. Of course, that
                 * means that we will not utilize Android's MediaCodec for audio just yet.
                 */
                MediaCodecInfo.CodecCapabilities capabilities = getCapabilitiesForType(codecInfo, supportedType);

                if (capabilities == null)
                    continue;

                int[] colorFormats = capabilities.colorFormats;

                if ((colorFormats == null) || (colorFormats.length == 0))
                    continue;

                if (bSupportedFormats != null)
                    bSupportedFormats.clear();
                for (int colorFormat : colorFormats) {
                    Format bSupportedFormat = getFmjFormatFromMediaCodecColorFormat(colorFormat);

                    if (bSupportedFormat == null)
                        continue;

                    if (bSupportedFormats == null) {
                        bSupportedFormats = new ArrayList<>(colorFormats.length);
                    }
                    bSupportedFormats.add(bSupportedFormat);
                }
                if ((bSupportedFormats == null) || bSupportedFormats.isEmpty())
                    continue;

                /*
                 * Finally, we know the FMJ Formats supported by Android's MediaCodec as input and output.
                 */
                List<Format> a, b;

                if (codecInfo.isEncoder()) {
                    /*
                     * Android's supportedType i.e. aSupportedFormat specifies the output Format of
                     * the MediaCodec. Respectively, Android's colorFormats i.e. bSupportedFormats
                     * define the input Formats supported by the MediaCodec.
                     */
                    a = supportedOutputFormats;
                    b = supportedInputFormats;
                }
                else {
                    /*
                     * Android's supportedType i.e. aSupportedFormat specifies the input Format of
                     * the MediaCodec. Respectively, Android's colorFormats i.e. bSupportedFormats
                     * define the output Formats supported by the MediaCodec.
                     */
                    a = supportedInputFormats;
                    b = supportedOutputFormats;
                }
                if (!a.contains(aSupportedFormat))
                    a.add(aSupportedFormat);
                for (Format bSupportedFormat : bSupportedFormats) {
                    if (!b.contains(bSupportedFormat))
                        b.add(bSupportedFormat);
                }

                StringBuilder s = new StringBuilder();

                s.append("Supported MediaCodec:");
                s.append(" name= ").append(codecInfo.getName()).append(';');
                s.append(" mime= ").append(supportedType).append(';');
                s.append(" colorFormats= ").append(Arrays.toString(colorFormats)).append(';');

                MediaCodecInfo.CodecProfileLevel[] profileLevels = capabilities.profileLevels;

                if ((profileLevels != null) && (profileLevels.length != 0)) {
                    s.append(" profileLevels= [");
                    for (int i = 0; i < profileLevels.length; i++) {
                        if (i != 0)
                            s.append("; ");

                        MediaCodecInfo.CodecProfileLevel profileLevel = profileLevels[i];

                        s.append("profile= ").append(profileLevel.profile).append(", level= ")
                                .append(profileLevel.level);
                    }
                    s.append("];");
                }
                Timber.d("%s", s);
            }
        }

        SUPPORTED_INPUT_FORMATS = supportedInputFormats.toArray(EMPTY_FORMATS);
        SUPPORTED_OUTPUT_FORMATS = supportedOutputFormats.toArray(EMPTY_FORMATS);
    }

    /**
     * Invokes {@link MediaCodecInfo#getCapabilitiesForType(String)} on a specific
     * <code>MediaCodecInfo</code> instance with a specific supported/mime type and logs and swallows
     * any <code>IllegalArgumentException</code>. Such an exception has been seen thrown on at least one
     * device with no known reason.
     *
     * @param codecInfo the <code>MediaCodecInfo</code> to invoke the method on
     * @param type the supported/mime type to pass as an argument to the method to be invoked
     *
     * @return the result of the invocation of the method on the specified <code>codecInfo</code>
     */
    private static MediaCodecInfo.CodecCapabilities getCapabilitiesForType(
            MediaCodecInfo codecInfo, String type) {
        MediaCodecInfo.CodecCapabilities capabilities;

        try {
            capabilities = codecInfo.getCapabilitiesForType(type);
        } catch (IllegalArgumentException iae) {
            capabilities = null;
            Timber.w(iae, "Invocation failed for supported/mime type: %s", type);
        }
        return capabilities;
    }

    /**
     * Gets an FMJ <code>VideoFormat</code> instance which represents the same information about media
     * data as a specific <code>colorFormat</code> defined in the terms of Android's <code>MediaCodec</code> class.
     *
     * @param colorFormat the <code>colorFormat</code> value in the terms of Android's <code>MediaCodec</code> class to
     * get an FMJ <code>VideoFormat</code> equivalent of
     *
     * @return an FMJ <code>VideoFormat</code> instance which represents the same information about
     * media data as (i.e. is equivalent to) the specified <code>colorFormat</code>
     */
    private static VideoFormat getFmjFormatFromMediaCodecColorFormat(int colorFormat) {
        int pixfmt = FFmpeg.PIX_FMT_NONE;

        for (int i = 0; i < PIX_FMTS_TO_MEDIA_CODEC_COLOR_FORMATS.length; i += 2) {
            if (PIX_FMTS_TO_MEDIA_CODEC_COLOR_FORMATS[i + 1] == colorFormat) {
                pixfmt = PIX_FMTS_TO_MEDIA_CODEC_COLOR_FORMATS[i];
                break;
            }
        }

        return (pixfmt == FFmpeg.PIX_FMT_NONE) ? null : new AVFrameFormat(pixfmt);
    }

    /**
     * Gets an FMJ <code>Format</code> instance which represents the same information about media data
     * as a specific mime type defined in the terms of Android's <code>MediaCodec</code> class.
     *
     * @param type the mime type in the terms of Android's <code>MediaCodec</code> class to get an FMJ
     * <code>Format</code> equivalent of
     *
     * @return an FMJ <code>Format</code> instance which represents the same information about media
     * data as (i.e. is equivalent to) the specified <code>type</code>
     */
    private static Format getFmjFormatFromMediaCodecType(String type) {
        String encoding = null;

        for (int i = 0; i < FMJ_ENCODINGS_TO_MEDIA_CODEC_TYPES.length; i += 2) {
            if (FMJ_ENCODINGS_TO_MEDIA_CODEC_TYPES[i + 1].equals(type)) {
                encoding = FMJ_ENCODINGS_TO_MEDIA_CODEC_TYPES[i];
                break;
            }
        }

        return (encoding == null) ? null : new VideoFormat(encoding);
    }

    /**
     * Gets a <code>colorFormat</code> value defined in the terms of Android's <code>MediaCodec</code> class
     * which is equivalent to a specific FMJ <code>Format</code>.
     *
     * @param format the FMJ <code>Format</code> to get the equivalent to
     *
     * @return a <code>colorFormat</code> value defined in the terms of Android's <code>MediaCodec</code>
     * class which is equivalent to the specified <code>format</code> or
     * {@link #OMX_COLOR_FormatUnused} if no equivalent is known to <code>AndroidMediaCodec</code>
     */
    private static int getMediaCodecColorFormatFromFmjFormat(Format format) {
        if (format instanceof AVFrameFormat) {
            AVFrameFormat avFrameFormat = (AVFrameFormat) format;
            int pixfmt = avFrameFormat.getPixFmt();

            for (int i = 0; i < PIX_FMTS_TO_MEDIA_CODEC_COLOR_FORMATS.length; i += 2) {
                if (PIX_FMTS_TO_MEDIA_CODEC_COLOR_FORMATS[i] == pixfmt)
                    return PIX_FMTS_TO_MEDIA_CODEC_COLOR_FORMATS[i + 1];
            }
        }
        return OMX_COLOR_FormatUnused;
    }

    /**
     * Gets a mime type defined in the terms of Android's <code>MediaCodec</code> class which is
     * equivalent to a specific FMJ <code>Format</code>.
     *
     * @param format the FMJ <code>Format</code> to get the equivalent to
     *
     * @return a mime type defined in the terms of Android's <code>MediaCodec</code> class which is
     * equivalent to the specified <code>format</code> or <code>null</code> if no equivalent is
     * known to <code>AndroidMediaCodec</code>
     */
    private static String getMediaCodecTypeFromFmjFormat(Format format) {
        if (format != null) {
            String encoding = format.getEncoding();

            for (int i = 0; i < FMJ_ENCODINGS_TO_MEDIA_CODEC_TYPES.length; i += 2) {
                if (FMJ_ENCODINGS_TO_MEDIA_CODEC_TYPES[i].equals(encoding))
                    return FMJ_ENCODINGS_TO_MEDIA_CODEC_TYPES[i + 1];
            }
        }
        return null;
    }

    /**
     * Determines whether a specific FMJ <code>Format</code> matches (i.e. is equivalent to) a specific
     * <code>colorFormat</code> defined in the terms of Android's <code>MediaCodec</code> class.
     *
     * @param format the FMJ <code>Format</code> to be compared to the specified <code>colorFormat</code>
     * @param colorFormat the <code>colorFormat</code> defined in the terms of Android's <code>MediaCodec</code> class
     * to be compared to the specified <code>format</code>
     *
     * @return <code>true</code> if the specified <code>format</code> matches (i.e. is equivalent to) the
     * specified <code>colorFormat</code>; otherwise, <code>false</code>
     */
    private static boolean matchesMediaCodecColorFormat(Format format, int colorFormat) {
        int formatColorFormat = getMediaCodecColorFormatFromFmjFormat(format);

        return (formatColorFormat != OMX_COLOR_FormatUnused) && (formatColorFormat == colorFormat);
    }

    /**
     * Determines whether a specific FMJ <code>Format</code> matches (i.e. is equivalent to) a specific
     * mime type defined in the terms of Android's <code>MediaCodec</code> class.
     *
     * @param format the FMJ <code>Format</code> to be compared to the specified <code>type</code>
     * @param type the media type defined in the terms of Android's <code>MediaCodec</code> class to be
     * compared to the specified <code>format</code>
     *
     * @return <code>true</code> if the specified <code>format</code> matches (i.e. is equal to) the
     * specified <code>type</code>; otherwise, <code>false</code>
     */
    private static boolean matchesMediaCodecType(Format format, String type) {
        String formatType = getMediaCodecTypeFromFmjFormat(format);
        return (formatType != null) && formatType.equals(type);
    }

    /**
     * The <code>AVFrame</code> instance into which this <code>Codec</code> outputs media (data) if the
     * <code>outputFormat</code> is an <code>AVFrameFormat</code> instance.
     */
    private AVFrame avFrame;

    /**
     * A <code>byte</code> in the form of an array which is used to copy the bytes of a
     * <code>java.nio.ByteBuffer</code> into native memory (because the <code>memcpy</code> implementation
     * requires an array. Allocated once to reduce garbage collection.
     */
    private final byte[] b = new byte[1];

    private final ByteBufferPool byteBufferPool = new ByteBufferPool();

    /**
     * The <code>colorFormat</code> value defined in the terms of Android's <code>MediaCodec</code> class
     * with which {@link #mediaCodec} is configured.
     */
    private int colorFormat = OMX_COLOR_FormatUnused;

    /**
     * The indicator which determines whether {@link #mediaCodec} is configured to encode (or
     * decode) media (data).
     */
    private boolean encoder;

    /**
     * The <code>MediaCodec.BufferInfo</code> instance which is populated by {@link #mediaCodec} to
     * describe the offset and length/size of the <code>java.nio.ByteBuffer</code>s it utilizes.
     * Allocated once to reduce garbage collection at runtime.
     */
    private final MediaCodec.BufferInfo info = new MediaCodec.BufferInfo();

    private java.nio.ByteBuffer[] inputBuffers;

    /**
     * Android's <code>MediaCodec</code> instance which is wrapped by this instance and which performs
     * the very media processing (during the execution of {@link #doProcess(Buffer, Buffer)}).
     */
    private MediaCodec mediaCodec;

    private java.nio.ByteBuffer[] outputBuffers;

    /**
     * The mime type defined in the terms of Android's <code>MediaCodec</code> class with which
     * {@link #mediaCodec} is configured.
     */
    private String type;

    /**
     * Initializes a new <code>AndroidMediaCodec</code> instance.
     */
    public AndroidMediaCodec() {
        super("MediaCodec", Format.class, SUPPORTED_OUTPUT_FORMATS);
        inputFormats = SUPPORTED_INPUT_FORMATS;
    }

    /**
     * {@inheritDoc}
     *
     * Stops and releases {@link #mediaCodec} i.e. prepares it to be garbage collected.
     */
    protected void doClose() {
        if (mediaCodec != null) {
            try {
                try {
                    mediaCodec.stop();
                } finally {
                    mediaCodec.release();
                }
            } finally {
                mediaCodec = null;

                /*
                 * The following are properties of mediaCodec which are not exposed by the
                 * MediaCodec class. The encoder property is of type boolean and either of its
                 * domain values is significant so clearing it is impossible.
                 */
                colorFormat = OMX_COLOR_FormatUnused;
                inputBuffers = null;
                outputBuffers = null;
                type = null;
            }
        }

        if (avFrame != null) {
            avFrame.free();
            avFrame = null;
        }
        byteBufferPool.drain();
    }

    /**
     * {@inheritDoc}
     */
    protected void doOpen()
            throws ResourceUnavailableException {
        /*
         * If the inputFormat and outputFormat properties of this Codec have already been assigned
         * suitable values, initialize a MediaCodec instance, configure it and start it. Otherwise,
         * the procedure will be performed later on when the properties in question do get assigned
         * suitable values.
         */
        try {
            maybeConfigureAndStart();
        } catch (Throwable t) {
            /*
             * PlugIn#open() (and, consequently, AbstractCodecExt#doOpen()) is supposed to throw
             * ResourceUnavailableException but maybeConfigureAndStart() does not throw such an
             * exception for the sake of ease of use.
             */
            if (t instanceof ThreadDeath)
                throw (ThreadDeath) t;
            else {
                if (t instanceof InterruptedException)
                    Thread.currentThread().interrupt();

                Timber.e(t, "Failed to open %s", getName());

                ResourceUnavailableException rue = new ResourceUnavailableException();

                rue.initCause(t);
                throw rue;
            }
        }
    }

    /**
     * {@inheritDoc}
     */
    protected int doProcess(Buffer inputBuffer, Buffer outputBuffer) {
        Format inputFormat = inputBuffer.getFormat();

        if ((inputFormat != null)
                && (inputFormat != this.inputFormat)
                && !inputFormat.equals(inputFormat)
                && (null == setInputFormat(inputFormat))) {
            return BUFFER_PROCESSED_FAILED;
        }
        inputFormat = this.inputFormat;

        /*
         * FIXME The implementation of AndroidMediaCodec is incomplete by relying on inputFormat
         * having Format.byteArray dataType.
         */
        if ((inputFormat == null) || !Format.byteArray.equals(inputFormat.getDataType())) {
            return BUFFER_PROCESSED_FAILED;
        }

        Format outputFormat = this.outputFormat;

        /*
         * FIXME The implementation of AndroidMediaCodec is incomplete by relying on outputFormat
         * being an AVFrameFormat instance.
         */
        if (!(outputFormat instanceof AVFrameFormat))
            return BUFFER_PROCESSED_FAILED;

        int mediaCodecOutputIndex = mediaCodec.dequeueOutputBuffer(info, 0);
        /*
         * We will first exhaust the output of mediaCodec and then we will feed input into it.
         */
        int processed = INPUT_BUFFER_NOT_CONSUMED | OUTPUT_BUFFER_NOT_FILLED;

        if (MediaCodec.INFO_OUTPUT_BUFFERS_CHANGED == mediaCodecOutputIndex) {
            outputBuffers = mediaCodec.getOutputBuffers();
        }
        else if (0 <= mediaCodecOutputIndex) {
            int outputLength = 0;
            java.nio.ByteBuffer byteBuffer = null;

            try {
                if ((outputLength = info.size) > 0) {
                    byteBuffer = outputBuffers[mediaCodecOutputIndex];

                    ByteBuffer avFrameData = avFrame.getData();
                    AVFrameFormat avFrameFormat = (AVFrameFormat) outputFormat;

                    if ((avFrameData == null) || (avFrameData.getCapacity() < outputLength)) {
                        avFrameData = byteBufferPool.getBuffer(outputLength);
                        if ((avFrameData == null)
                                || (avFrame.avpicture_fill(avFrameData, avFrameFormat) < 0)) {
                            processed = BUFFER_PROCESSED_FAILED;
                        }
                    }
                    if (processed != BUFFER_PROCESSED_FAILED) {
                        long ptr = avFrameData.getPtr();

                        for (int i = info.offset, end = i + outputLength; i < end; i++) {
                            b[0] = byteBuffer.get(i);
                            FFmpeg.memcpy(ptr, b, 0, b.length);
                            ptr++;
                        }

                        outputBuffer.setData(avFrame);
                        outputBuffer.setFormat(outputFormat);
                        outputBuffer.setLength(outputLength);
                        outputBuffer.setOffset(0);

                        processed &= ~OUTPUT_BUFFER_NOT_FILLED;
                    }
                }
            } finally {
                // Well, it was recommended by the Internet.
                if (byteBuffer != null)
                    byteBuffer.clear();

                mediaCodec.releaseOutputBuffer(mediaCodecOutputIndex,
                        /* render */false);
            }
            /*
             * We will first exhaust the output of mediaCodec and then we will feed input into it.
             */
            if ((processed == BUFFER_PROCESSED_FAILED) || (outputLength > 0))
                return processed;
        }

        int mediaCodecInputIndex = MediaCodec.INFO_TRY_AGAIN_LATER;// mediaCodec.dequeueInputBuffer(DEQUEUE_INPUT_BUFFER_TIMEOUT);

        if (0 <= mediaCodecInputIndex) {
            int mediaCodecInputOffset = 0;
            int mediaCodecInputLength = 0;

            try {
                java.nio.ByteBuffer byteBuffer = inputBuffers[mediaCodecInputIndex];
                int fmjLength = inputBuffer.getLength();

                mediaCodecInputLength = Math.min(byteBuffer.capacity(), fmjLength);

                byte[] bytes = (byte[]) inputBuffer.getData();
                int fmjOffset = inputBuffer.getOffset();

                for (int dst = 0, src = fmjOffset; dst < mediaCodecInputLength; dst++, src++) {
                    byteBuffer.put(dst, bytes[src]);
                }

                if (mediaCodecInputLength == fmjLength)
                    processed &= ~INPUT_BUFFER_NOT_CONSUMED;
                else {
                    inputBuffer.setLength(fmjLength - mediaCodecInputLength);
                    inputBuffer.setOffset(fmjOffset + mediaCodecInputLength);
                }
            } finally {
                mediaCodec.queueInputBuffer(mediaCodecInputIndex, mediaCodecInputOffset,
                        mediaCodecInputLength, 0, 0);
            }
        }
        return processed;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected Format[] getMatchingOutputFormats(Format inputFormat) {
        /*
         * An input Format may be supported by multiple MediaCodecs and, consequently, the output
         * Formats supported by this AndroidMediaCodec is the set of the output formats supported by
         * the multiple MediaCodecs in question.
         */

        List<Format> outputFormats = new LinkedList<>();

        for (int codecIndex = 0, codecCount = MediaCodecList.getCodecCount(); codecIndex < codecCount; codecIndex++) {
            MediaCodecInfo codecInfo = MediaCodecList.getCodecInfoAt(codecIndex);
            String[] supportedTypes = codecInfo.getSupportedTypes();

            if (codecInfo.isEncoder()) {
                /* The supported input Formats are the colorFormats. */
                for (String supportedType : supportedTypes) {
                    MediaCodecInfo.CodecCapabilities capabilities = getCapabilitiesForType(codecInfo, supportedType);
                    if (capabilities != null) {
                        int[] colorFormats = capabilities.colorFormats;

                        if ((colorFormats != null) && (colorFormats.length != 0)) {
                            boolean matches = false;

                            for (int colorFormat : colorFormats) {
                                if (matchesMediaCodecColorFormat(inputFormat, colorFormat)) {
                                    matches = true;
                                    break;
                                }
                            }
                            if (matches) {
                                /*
                                 * The supported input Formats are the supportedTypes.
                                 */
                                Format outputFormat = getFmjFormatFromMediaCodecType(supportedType);

                                if ((outputFormat != null) && !outputFormats.contains(outputFormat)) {
                                    outputFormats.add(outputFormat);
                                }
                            }
                        }
                    }
                }
            }
            else {
                /* The supported input Formats are the supportedTypes. */
                for (String supportedType : supportedTypes) {
                    if (matchesMediaCodecType(inputFormat, supportedType)) {
                        /* The supported output Formats are the colorFormats. */
                        MediaCodecInfo.CodecCapabilities capabilities
                                = getCapabilitiesForType(codecInfo, supportedType);

                        if (capabilities != null) {
                            int[] colorFormats = capabilities.colorFormats;

                            if ((colorFormats != null) && (colorFormats.length != 0)) {
                                for (int colorFormat : colorFormats) {
                                    Format outputFormat = getFmjFormatFromMediaCodecColorFormat(colorFormat);

                                    if ((outputFormat != null)
                                            && !outputFormats.contains(outputFormat)) {
                                        outputFormats.add(outputFormat);
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        return outputFormats.toArray(EMPTY_FORMATS);
    }

    /**
     * Configures and starts {@link #mediaCodec} if the <code>inputFormat</code> and
     * <code>outputFormat</code> properties of this <code>Codec</code> have already been assigned suitable
     * values.
     */
    private void maybeConfigureAndStart() {
        /*
         * We can discover an appropriate MediaCodec to be wrapped by this instance only if the
         * inputFormat and outputFormat are suitably set.
         */
        if ((inputFormat == null) || (outputFormat == null))
            return;

        /*
         * If the inputFormat and outputFormat are supported by the current MediaCodec, we will not
         * have to bring it in accord with them (because it is already in accord with them).
         */
        if (mediaCodec != null) {
            Format typeFormat, colorFormatFormat;

            if (encoder) {
                typeFormat = outputFormat;
                colorFormatFormat = inputFormat;
            }
            else {
                typeFormat = inputFormat;
                colorFormatFormat = outputFormat;
            }
            if (!matchesMediaCodecType(typeFormat, type)
                    || !matchesMediaCodecColorFormat(colorFormatFormat, colorFormat)) {
                doClose();
            }
        }
        if (mediaCodec != null)
            return;

        /*
         * Find a MediaCodecInfo which supports the specified inputFormat and outputFormat of this
         * instance, initialize a MediaCodec from it to be wrapped by this instance, configure it
         * and start it.
         */
        MediaCodecInfo codecInfo = null;
        for (int codecIndex = 0, codecCount = MediaCodecList.getCodecCount(); codecIndex < codecCount; codecIndex++) {
            codecInfo = MediaCodecList.getCodecInfoAt(codecIndex);

            Format typeFormat, colorFormatFormat;
            if (codecInfo.isEncoder()) {
                typeFormat = outputFormat;
                colorFormatFormat = inputFormat;
            }
            else {
                typeFormat = inputFormat;
                colorFormatFormat = outputFormat;
            }

            String[] supportedTypes = codecInfo.getSupportedTypes();
            for (String supportedType : supportedTypes) {
                if (!matchesMediaCodecType(typeFormat, supportedType))
                    continue;

                MediaCodecInfo.CodecCapabilities capabilities = getCapabilitiesForType(codecInfo,
                        supportedType);
                if (capabilities == null)
                    continue;

                int[] colorFormats = capabilities.colorFormats;
                if ((colorFormats == null) || (colorFormats.length == 0))
                    continue;

                for (int colorFormat : colorFormats) {
                    if (matchesMediaCodecColorFormat(colorFormatFormat, colorFormat)) {
                        // We have found a MediaCodecInfo which supports
                        // inputFormat and outputFormat.
                        this.colorFormat = colorFormat;
                        this.type = supportedType;
                        break;
                    }
                }

                // Have we found a MediaCodecInfo which supports inputFormat and
                // outputFormat yet?
                if ((this.colorFormat != OMX_COLOR_FormatUnused) && (this.type != null)) {
                    break;
                }
            }

            // Have we found a MediaCodecInfo which supports inputFormat and
            // outputFormat yet?
            if ((this.colorFormat != OMX_COLOR_FormatUnused) && (this.type != null)) {
                break;
            }
        }

        // Have we found a MediaCodecInfo which supports inputFormat and
        // outputFormat yet?
        if ((this.colorFormat != OMX_COLOR_FormatUnused) && (this.type != null)) {
            MediaCodec mediaCodec = null;
            try {
                mediaCodec = MediaCodec.createByCodecName(codecInfo.getName());
            } catch (IOException e) {
                // TODO Auto-generated catch block
                e.printStackTrace();
            }

            if (mediaCodec != null) {
                MediaFormat format = new MediaFormat();
                int flags = 0;

                format.setString(MediaFormat.KEY_MIME, this.type);
                if (codecInfo.isEncoder()) {
                    encoder = true;
                    flags |= MediaCodec.CONFIGURE_FLAG_ENCODE;
                    format.setInteger(MediaFormat.KEY_COLOR_FORMAT, this.colorFormat);
                }
                else {
                    encoder = false;
                }

                // Well, this Codec is either an encoder or a decoder so it
                // seems like only its inputFormat may specify the size/Dimension.
                if (inputFormat instanceof VideoFormat) {
                    Dimension size = ((VideoFormat) inputFormat).getSize();
                    if (size == null)
                        size = new Dimension(640, 480);

                    format.setInteger(MediaFormat.KEY_HEIGHT, size.height);
                    format.setInteger(MediaFormat.KEY_WIDTH, size.width);
                }

                mediaCodec.configure(format, null, null, flags);
                mediaCodec.start();

                this.mediaCodec = mediaCodec;
                inputBuffers = mediaCodec.getInputBuffers();
                outputBuffers = mediaCodec.getOutputBuffers();

                if (avFrame == null)
                    avFrame = new AVFrame();
            }
        }

        // At this point, mediaCodec should have successfully been initialized,
        // configured and started.
        if (this.mediaCodec == null)
            throw new IllegalStateException("mediaCodec");
    }

    /**
     * {@inheritDoc}
     *
     * Makes sure that {@link #mediaCodec} is in accord in terms of properties with the
     * <code>inputFormat</code> and <code>outputFormat</code> set on this <code>Codec</code>.
     */
    @Override
    public Format setInputFormat(Format format) {
        Format oldValue = inputFormat;
        Format setInputFormat = super.setInputFormat(format);
        Format newValue = inputFormat;

        if ((oldValue != newValue) && opened)
            maybeConfigureAndStart();
        return setInputFormat;
    }

    /**
     * {@inheritDoc}
     *
     * Makes sure that {@link #mediaCodec} is in accord in terms of properties with the
     * <code>inputFormat</code> and <code>outputFormat</code> set on this <code>Codec</code>.
     */
    @Override
    public Format setOutputFormat(Format format) {
        if (format instanceof AVFrameFormat) {
            AVFrameFormat avFrameFormat = (AVFrameFormat) format;

            if (avFrameFormat.getSize() == null) {
                format = new AVFrameFormat(new Dimension(640, 480), avFrameFormat.getFrameRate(),
                        avFrameFormat.getPixFmt(), avFrameFormat.getDeviceSystemPixFmt());
            }
        }
        Format oldValue = outputFormat;
        Format setOutputFormat = super.setOutputFormat(format);
        Format newValue = outputFormat;

        if ((oldValue != newValue) && opened)
            maybeConfigureAndStart();
        return setOutputFormat;
    }
}
