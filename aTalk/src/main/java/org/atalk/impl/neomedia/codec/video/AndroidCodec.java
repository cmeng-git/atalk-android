/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.atalk.impl.neomedia.codec.video;

import android.media.*;
import android.view.Surface;

import org.atalk.android.util.java.awt.Dimension;
import org.atalk.impl.neomedia.codec.AbstractCodec2;
import org.atalk.service.neomedia.codec.Constants;

import java.io.IOException;

import javax.media.*;

import timber.log.Timber;

/**
 * Abstract codec class that uses <tt>MediaCodec</tt> for video encoding. Eventually
 * <tt>AndroidDecoder</tt> and <tt>AndroidEncoder</tt> can be merged later.
 *
 * @author Pawel Domas
 * @author Eng Chong Meng
 */
abstract class AndroidCodec extends AbstractCodec2
{
    /**
     * Copied from <tt>MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface</tt>
     */
    private final static int COLOR_FormatSurface = 0x7F000789;

    /**
     * Indicates that this instance is used for encoding(and not for decoding).
     */
    private final boolean isEncoder;

    /**
     * <tt>MediaCodec</tt> used by this instance.
     */
    private MediaCodec codec;

    /**
     * Input <tt>MediaCodec</tt> buffers.
     */
    java.nio.ByteBuffer[] codecInputs;

    /**
     * Output <tt>MediaCodec</tt> buffers.
     */
    java.nio.ByteBuffer[] codecOutputs;

    /**
     * <tt>BufferInfo</tt> object that stores codec's buffer information.
     */
    MediaCodec.BufferInfo info = new MediaCodec.BufferInfo();

    /**
     * Creates a new instance of <tt>AndroidCodec</tt>.
     *
     * @param name the <tt>PlugIn</tt> name of the new instance
     * @param formatClass the <tt>Class</tt> of input and output <tt>Format</tt>s supported by the new instance
     * @param supportedOutputFormats the list of <tt>Format</tt>s supported by the new instance as output.
     */
    protected AndroidCodec(String name, Class<? extends Format> formatClass,
            Format[] supportedOutputFormats, boolean isEncoder)
    {
        super(name, formatClass, supportedOutputFormats);
        this.isEncoder = isEncoder;
    }

    /**
     * Class should return <tt>true</tt> if surface will be used.
     *
     * @return <tt>true</tt> if surface will be used.
     */
    protected abstract boolean useSurface();

    /**
     * Returns <tt>Surface</tt> used by this instance for encoding or decoding.
     *
     * @return <tt>Surface</tt> used by this instance for encoding or decoding.
     */
    protected abstract Surface getSurface();

    /**
     * Template method used to configure <tt>MediaCodec</tt> instance. Called before starting the codec.
     *
     * @param codec <tt>MediaCodec</tt> instance to be configured.
     * @param codecType string codec media type.
     * @throws ResourceUnavailableException
     */
    protected abstract void configureMediaCodec(MediaCodec codec, String codecType)
            throws ResourceUnavailableException;

    /**
     * Selects <tt>MediaFormat</tt> color format used.
     *
     * @return used <tt>MediaFormat</tt> color format.
     */
    protected int getColorFormat()
    {
        return useSurface() ? COLOR_FormatSurface : MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Planar;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void doOpen()
            throws ResourceUnavailableException
    {
        String codecType;
        String encoding = isEncoder ? outputFormat.getEncoding() : inputFormat.getEncoding();

        switch (encoding) {
            case Constants.VP9:
                codecType = CodecInfo.MEDIA_CODEC_TYPE_VP9;
                break;
            case Constants.VP8:
                codecType = CodecInfo.MEDIA_CODEC_TYPE_VP8;
                break;
            case Constants.H264:
                codecType = CodecInfo.MEDIA_CODEC_TYPE_H264;
                break;
            default:
                throw new RuntimeException("Unsupported encoding: " + encoding);
        }

        CodecInfo codecInfo = CodecInfo.getCodecForType(codecType, isEncoder);
        if (codecInfo == null) {
            throw new ResourceUnavailableException("No " + getStrName()
                    + " found for type: " + codecType);
        }

        try {
            codec = MediaCodec.createByCodecName(codecInfo.getName());
        } catch (IOException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
        configureMediaCodec(codec, codecType);
        codec.start();

        codecInputs = codec.getInputBuffers();
        codecOutputs = codec.getOutputBuffers();
        Timber.i("Opened %s %s for name: %s use surface ? %s",
                codecType, getStrName(), codecInfo.getName(), useSurface());
    }

    private String getStrName()
    {
        return isEncoder ? "encoder" : "decoder";
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void doClose()
    {
        if (codec != null) {
            codec.stop();
            codec.release();
            codec = null;
            codecInputs = null;
            codecOutputs = null;
        }
    }

    /**
     * {@inheritDoc}
     */
    protected int doProcess(Buffer inputBuffer, Buffer outputBuffer)
    {
        try {
            return doProcessImpl(inputBuffer, outputBuffer);
        } catch (Exception e) {
            Timber.e(e, "%s", e.getMessage());
            throw new RuntimeException(e);
        }
    }

    private int doProcessImpl(Buffer inputBuffer, Buffer outputBuffer)
    {
        Format outputFormat = this.outputFormat;

        int mediaCodecOutputIndex = codec.dequeueOutputBuffer(info, 0);
        /*
         * We will first exhaust the output of mediaCodec and then we will feed input into it.
         */
        int processed = INPUT_BUFFER_NOT_CONSUMED | OUTPUT_BUFFER_NOT_FILLED;

        if (mediaCodecOutputIndex == MediaCodec.INFO_OUTPUT_BUFFERS_CHANGED) {
            codecOutputs = codec.getOutputBuffers();
        }
        else if (mediaCodecOutputIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
            MediaFormat outFormat = codec.getOutputFormat();
            Timber.i("Output format changed to: %s", outFormat);
            if (!isEncoder) {
                int pixelFormat = outFormat.getInteger(MediaFormat.KEY_COLOR_FORMAT);
                int requestedFormat = getColorFormat();
                if (!useSurface() && pixelFormat != requestedFormat) {
                    throw new RuntimeException("MediaCodec returned different color format: "
                            + pixelFormat + "(requested " + requestedFormat
                            + ", try using the Surface");
                }
            }
            // Video size should be known at this point
            onSizeDiscovered(new Dimension(outFormat.getInteger(MediaFormat.KEY_WIDTH),
                    outFormat.getInteger(MediaFormat.KEY_HEIGHT)));
        }
        else if (mediaCodecOutputIndex == MediaCodec.INFO_TRY_AGAIN_LATER) {
            Timber.d("Output not available yet...");
        }
        else if (0 <= mediaCodecOutputIndex) {
            Timber.d("Reading output: %s:%s flag: %s", info.offset, info.size, info.flags);

            int outputLength = 0;
            java.nio.ByteBuffer byteBuffer = null;

            try {
                if (!isEncoder && useSurface()) {
                    processed &= ~OUTPUT_BUFFER_NOT_FILLED;
                    outputBuffer.setFormat(outputFormat);
                }
                else if ((outputLength = info.size) > 0) {
                    byteBuffer = codecOutputs[mediaCodecOutputIndex];
                    byteBuffer.position(info.offset);
                    byteBuffer.limit(info.offset + info.size);

                    byte[] out
                            = AbstractCodec2.validateByteArraySize(outputBuffer, info.size, false);
                    byteBuffer.get(out, 0, info.size);

                    outputBuffer.setFormat(outputFormat);
                    outputBuffer.setLength(outputLength);
                    outputBuffer.setOffset(0);

                    processed &= ~OUTPUT_BUFFER_NOT_FILLED;
                }
            } finally {
                if (byteBuffer != null)
                    byteBuffer.clear();

                codec.releaseOutputBuffer(mediaCodecOutputIndex,
                        /* render */!isEncoder && useSurface());
            }
            /*
             * We will first exhaust the output of mediaCodec and then we will feed input into it.
             */
            if ((processed == BUFFER_PROCESSED_FAILED) || (outputLength > 0))
                return processed;
        }
        else {
            Timber.w("Output reports: %s", mediaCodecOutputIndex);
        }

        if (isEncoder && useSurface()) {
            inputBuffer.setData(getSurface());
            processed &= ~INPUT_BUFFER_NOT_CONSUMED;
        }
        else {
            int mediaCodecInputIndex = codec.dequeueInputBuffer(0);
            if (mediaCodecInputIndex >= 0) {
                byte[] input = (byte[]) inputBuffer.getData();
                int inputOffset = inputBuffer.getOffset();
                // TODO: is it correct: len = len-offset ?
                int inputLen = inputBuffer.getLength() - inputOffset;

                java.nio.ByteBuffer codecInput = codecInputs[mediaCodecInputIndex];

                if (codecInput.capacity() < inputLen) {
                    throw new RuntimeException("Input buffer too small: "
                            + codecInput.capacity() + " < " + inputLen);
                }

                codecInput.clear();
                codecInput.put(input, inputOffset, inputLen);
                codec.queueInputBuffer(mediaCodecInputIndex, 0, inputLen,
                        /* presentationTimeUs */inputBuffer.getTimeStamp(),
                        /* flags */0);

                processed &= ~INPUT_BUFFER_NOT_CONSUMED;
                Timber.d("Fed input with %s bytes of data.", inputLen);
            }
            else if (mediaCodecInputIndex == MediaCodec.INFO_TRY_AGAIN_LATER) {
                Timber.d("Input not available - try again later");
            }
            else {
                Timber.w("Input returned: %s", mediaCodecInputIndex);
            }
        }
        return processed;
    }

    /**
     * Method fired when <tt>MediaCodec</tt> detects video size.
     *
     * @param dimension video dimension.
     */
    protected void onSizeDiscovered(Dimension dimension)
    {

    }
}
