/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.atalk.impl.neomedia.codec.video;

import android.media.MediaCodec;
import android.media.MediaCodecInfo.CodecCapabilities;
import android.media.MediaFormat;
import android.view.Surface;

import java.awt.Dimension;
import java.io.IOException;
import java.nio.ByteBuffer;

import javax.media.Buffer;
import javax.media.Format;
import javax.media.ResourceUnavailableException;

import org.atalk.impl.neomedia.codec.AbstractCodec2;
import org.atalk.service.neomedia.codec.Constants;

import timber.log.Timber;

/**
 * Abstract codec class uses android <code>MediaCodec</code> for video decoding/encoding.
 * Eventually <code>AndroidDecoder</code> and <code>AndroidEncoder</code> can be merged later.
 *
 * @author Pawel Domas
 * @author Eng Chong Meng
 */
abstract class AndroidCodec extends AbstractCodec2 {
    /**
     * Indicates that this instance is used for encoding(and not for decoding).
     */
    private final boolean isEncoder;

    /**
     * <code>MediaCodec</code> used by this instance.
     */
    private MediaCodec codec;

    /**
     * Input <code>MediaCodec</code> buffer.
     */
    ByteBuffer codecInputBuf;

    /**
     * Output <code>MediaCodec</code> buffer.
     */
    ByteBuffer codecOutputBuf;

    /**
     * <code>BufferInfo</code> object that stores codec buffer information.
     */
    MediaCodec.BufferInfo mBufferInfo = new MediaCodec.BufferInfo();

    /**
     * Creates a new instance of <code>AndroidCodec</code>.
     *
     * @param name the <code>PlugIn</code> name of the new instance
     * @param formatClass the <code>Class</code> of input and output <code>Format</code>s supported by the new instance
     * @param supportedOutputFormats the list of <code>Format</code>s supported by the new instance as output.
     * @param isEncoder true if codec is encoder.
     */
    protected AndroidCodec(String name, Class<? extends Format> formatClass, Format[] supportedOutputFormats, boolean isEncoder) {
        super(name, formatClass, supportedOutputFormats);
        this.isEncoder = isEncoder;
    }

    /**
     * Class should return <code>true</code> if surface will be used for the codec.
     *
     * @return <code>true</code> if surface will be used.
     */
    protected abstract boolean useSurface();

    /**
     * Returns <code>Surface</code> used by this instance for encoding or decoding.
     *
     * @return <code>Surface</code> used by this instance for encoding or decoding.
     */
    protected abstract Surface getSurface();

    /**
     * Template method used to configure <code>MediaCodec</code> instance. Called before starting the codec.
     *
     * @param codec <code>MediaCodec</code> instance to be configured.
     * @param codecType string codec media type.
     *
     * @throws ResourceUnavailableException Resource Unavailable Exception if not supported
     */
    protected abstract void configureMediaCodec(MediaCodec codec, String codecType)
            throws ResourceUnavailableException;

    /**
     * Selects <code>MediaFormat</code> color format used.
     *
     * @return used <code>MediaFormat</code> color format.
     */
    protected int getColorFormat() {
        return useSurface() ? CodecCapabilities.COLOR_FormatSurface : CodecCapabilities.COLOR_FormatYUV420Flexible;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void doClose() {
        if (codec != null) {
            try {
                // Throws IllegalStateException – if in the Released state.
                codec.stop();
                codec.release();
            } catch (IllegalStateException e) {
                Timber.w("Codec stop exception: %s", e.getMessage());
            } finally {
                codec = null;
            }
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void doOpen()
            throws ResourceUnavailableException {
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
        // Timber.d("starting %s %s for: %s; useSurface: %s", codecType, getStrName(), codecInfo.getName(), useSurface());
        configureMediaCodec(codec, codecType);
        codec.start();
    }

    private String getStrName() {
        return isEncoder ? "encoder" : "decoder";
    }

    /**
     * {@inheritDoc}
     *
     * Exception: IllegalStateException thrown by codec.dequeueOutputBuffer or codec.dequeueInputBuffer
     * Any RuntimeException will close remote view container.
     */
    protected int doProcess(Buffer inputBuffer, Buffer outputBuffer) {
        try {
            return doProcessImpl(inputBuffer, outputBuffer);
        } catch (Exception e) {
            Timber.e(e, "Do process for codec: %s; Exception: %s", codec.getName(), e.getMessage());
            throw new RuntimeException(e);
        }
    }

    /**
     * Process the video stream:
     * We will first process the output data from the mediaCodec; then we will feed input into the decoder.
     *
     * @param inputBuffer input buffer
     * @param outputBuffer output buffer
     *
     * @return process status
     */
    private int doProcessImpl(Buffer inputBuffer, Buffer outputBuffer) {
        Format outputFormat = this.outputFormat;
        int processed = INPUT_BUFFER_NOT_CONSUMED | OUTPUT_BUFFER_NOT_FILLED;

        // Process the output data from the codec
        // Returns the index of an output buffer that has been successfully decoded or one of the INFO_* constants.
        int outputBufferIdx = codec.dequeueOutputBuffer(mBufferInfo, 0);
        if (outputBufferIdx == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
            MediaFormat outFormat = codec.getOutputFormat();
            if (!isEncoder) {
                int pixelFormat = outFormat.getInteger(MediaFormat.KEY_COLOR_FORMAT);
                int requestedFormat = getColorFormat();
                if (!useSurface() && pixelFormat != requestedFormat) {
                    throw new RuntimeException("MediaCodec returned different color format: "
                            + pixelFormat + "(requested " + requestedFormat
                            + ", try using the Surface");
                }
            }
            Timber.d("Codec output format changed (encoder: %s): %s", isEncoder, outFormat);
            // Video size should be known at this point
            Dimension videoSize = new Dimension(outFormat.getInteger(MediaFormat.KEY_WIDTH), outFormat.getInteger(MediaFormat.KEY_HEIGHT));
            onSizeChanged(videoSize);
        }
        else if (outputBufferIdx >= 0) {
            // Timber.d("Reading output: %s:%s flag: %s", mBufferInfo.offset, mBufferInfo.size, mBufferInfo.flags);
            int outputLength = 0;
            codecOutputBuf = null;
            try {
                if (!isEncoder && useSurface()) {
                    processed &= ~OUTPUT_BUFFER_NOT_FILLED;
                    outputBuffer.setFormat(outputFormat);
                    // Timber.d("Codec output format: %s", outputFormat);
                }
                else if ((outputLength = mBufferInfo.size) > 0) {
                    codecOutputBuf = codec.getOutputBuffer(outputBufferIdx);
                    if (codecOutputBuf != null) {
                        codecOutputBuf.position(mBufferInfo.offset);
                        codecOutputBuf.limit(mBufferInfo.offset + mBufferInfo.size);

                        byte[] out = AbstractCodec2.validateByteArraySize(outputBuffer, mBufferInfo.size, false);
                        codecOutputBuf.get(out, 0, mBufferInfo.size);
                    }
                    outputBuffer.setFormat(outputFormat);
                    outputBuffer.setLength(outputLength);
                    outputBuffer.setOffset(0);

                    processed &= ~OUTPUT_BUFFER_NOT_FILLED;
                }
            } finally {
                if (codecOutputBuf != null)
                    codecOutputBuf.clear();
                /*
                 * releaseOutputBuffer: the output buffer data will be forwarded to SurfaceView for render if true.
                 * see https://developer.android.com/reference/android/media/MediaCodec
                 */
                codec.releaseOutputBuffer(outputBufferIdx, !isEncoder && useSurface());
            }
            /*
             * We will first exhaust the output of the mediaCodec, and then we will feed input into it.
             */
            if (outputLength > 0)
                return processed;
        }
        else if (outputBufferIdx != MediaCodec.INFO_TRY_AGAIN_LATER) {
            Timber.w("Codec output reports: %s", outputBufferIdx);
        }

        // Feed more data to the decoder.
        if (isEncoder && useSurface()) {
            inputBuffer.setData(getSurface());
            processed &= ~INPUT_BUFFER_NOT_CONSUMED;
        }
        else {
            int inputBufferIdx = codec.dequeueInputBuffer(0);
            if (inputBufferIdx >= 0) {
                byte[] buf_data = (byte[]) inputBuffer.getData();
                int buf_offset = inputBuffer.getOffset();
                int buf_size = inputBuffer.getLength();

                codecInputBuf = codec.getInputBuffer(inputBufferIdx);
                if (codecInputBuf == null || codecInputBuf.capacity() < buf_size) {
                    throw new RuntimeException("Invalid input buffer: " + codecInputBuf + " < " + buf_size);
                }

                codecInputBuf.clear();
                codecInputBuf.put(buf_data, buf_offset, buf_size);
                codec.queueInputBuffer(inputBufferIdx, 0, buf_size, inputBuffer.getTimeStamp(), 0);

                // Timber.d("Fed input with %s bytes of data; Offset: %s.", buf_size, buf_offset);
                processed &= ~INPUT_BUFFER_NOT_CONSUMED;
            }
            else if (inputBufferIdx != MediaCodec.INFO_TRY_AGAIN_LATER) {
                Timber.w("Codec input reports: %s", inputBufferIdx);
            }
        }
        return processed;
    }

    /**
     * Method fired when <code>MediaCodec</code> detects video size changed.
     *
     * @param dimension video dimension.
     *
     * @see AndroidDecoder#onSizeChanged(Dimension)
     */
    protected void onSizeChanged(Dimension dimension) {
    }
}
