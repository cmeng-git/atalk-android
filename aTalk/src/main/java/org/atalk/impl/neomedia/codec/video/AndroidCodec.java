/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.atalk.impl.neomedia.codec.video;

import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaFormat;
import android.view.Surface;

import org.atalk.android.util.java.awt.Dimension;
import org.atalk.impl.neomedia.codec.AbstractCodec2;
import org.atalk.service.neomedia.codec.Constants;

import java.io.IOException;

import javax.media.Buffer;
import javax.media.Format;
import javax.media.ResourceUnavailableException;

import timber.log.Timber;

/**
 * Abstract codec class uses android <tt>MediaCodec</tt> for video encoding.
 * Eventually <tt>AndroidDecoder</tt> and <tt>AndroidEncoder</tt> can be merged later.
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
     * Input <tt>MediaCodec</tt> buffer.
     */
    java.nio.ByteBuffer codecInput;

    /**
     * Output <tt>MediaCodec</tt> buffer.
     */
    java.nio.ByteBuffer codecOutput;

    /**
     * <tt>BufferInfo</tt> object that stores codec buffer information.
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
     * @throws ResourceUnavailableException Resource Unavailable Exception if not supported
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
            throw new ResourceUnavailableException("No " + getStrName() + " found for type: " + codecType);
        }

        try {
            codec = MediaCodec.createByCodecName(codecInfo.getName());
        } catch (IOException e) {
            Timber.e("Exception in create codec name: %s", e.getMessage());
        }

        configureMediaCodec(codec, codecType);
        codec.start();

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

        /*
         * We will first exhaust the output of the mediaCodec, and then we will feed input into it.
         */
        int processed = INPUT_BUFFER_NOT_CONSUMED | OUTPUT_BUFFER_NOT_FILLED;

        int outputBufferId = codec.dequeueOutputBuffer(info, 0);
        if (outputBufferId == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
            MediaFormat outFormat = codec.getOutputFormat();
            Timber.i("Codec output format changed to: %s", outFormat);
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
        else if (outputBufferId == MediaCodec.INFO_TRY_AGAIN_LATER) {
            Timber.d("Codec output not available yet...");
        }
        else if (outputBufferId >= 0) {
            Timber.d("Reading output: %s:%s flag: %s", info.offset, info.size, info.flags);

            int outputLength = 0;
            codecOutput = null;
            try {
                if (!isEncoder && useSurface()) {
                    processed &= ~OUTPUT_BUFFER_NOT_FILLED;
                    outputBuffer.setFormat(outputFormat);
                }
                else if ((outputLength = info.size) > 0) {
                    codecOutput = codec.getOutputBuffer(outputBufferId);
                    codecOutput.position(info.offset);
                    codecOutput.limit(info.offset + info.size);

                    byte[] out
                            = AbstractCodec2.validateByteArraySize(outputBuffer, info.size, false);
                    codecOutput.get(out, 0, info.size);

                    outputBuffer.setFormat(outputFormat);
                    outputBuffer.setLength(outputLength);
                    outputBuffer.setOffset(0);

                    processed &= ~OUTPUT_BUFFER_NOT_FILLED;
                }
            } finally {
                if (codecOutput != null)
                    codecOutput.clear();
                codec.releaseOutputBuffer(outputBufferId, isEncoder && useSurface());
            }
            /*
             * We will first exhaust the output of the mediaCodec, and then we will feed input into it.
             */
            if (outputLength > 0)
                return processed;
        }
        else {
            Timber.w("Codec output reports: %s", outputBufferId);
        }

        if (isEncoder && useSurface()) {
            inputBuffer.setData(getSurface());
            processed &= ~INPUT_BUFFER_NOT_CONSUMED;
        }
        else {
            int inputBufferId = codec.dequeueInputBuffer(0);
            if (inputBufferId >= 0) {
                byte[] input = (byte[]) inputBuffer.getData();
                int inputOffset = inputBuffer.getOffset();
                // TODO: is it correct: len = len-offset ?
                int inputLen = inputBuffer.getLength() - inputOffset;

                codecInput = codec.getInputBuffer(inputBufferId);
                if (codecInput.capacity() < inputLen) {
                    throw new RuntimeException("Input buffer too small: "
                            + codecInput.capacity() + " < " + inputLen);
                }

                codecInput.clear();
                codecInput.put(input, inputOffset, inputLen);
                codec.queueInputBuffer(inputBufferId, 0, inputLen,
                        /* presentationTimeUs */inputBuffer.getTimeStamp(), 0);

                processed &= ~INPUT_BUFFER_NOT_CONSUMED;
                Timber.d("Fed input with %s bytes of data.", inputLen);
            }
            else if (inputBufferId == MediaCodec.INFO_TRY_AGAIN_LATER) {
                Timber.d("Input not available - try again later");
            }
            else {
                Timber.w("Input returned: %s", inputBufferId);
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
