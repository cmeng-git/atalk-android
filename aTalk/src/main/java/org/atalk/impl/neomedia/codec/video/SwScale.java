/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license.
 * See terms of license at gnu.org.
 */
package org.atalk.impl.neomedia.codec.video;

import net.sf.fmj.media.AbstractCodec;

import org.atalk.impl.timberlog.TimberLog;
import org.atalk.impl.neomedia.codec.FFmpeg;
import org.atalk.impl.neomedia.control.FrameProcessingControlImpl;

import java.awt.Dimension;

import javax.media.Buffer;
import javax.media.Format;
import javax.media.format.RGBFormat;
import javax.media.format.VideoFormat;
import javax.media.format.YUVFormat;

import timber.log.Timber;

/**
 * Implements an FMJ <code>Codec</code> which uses libswscale to scale images and
 * convert between color spaces (typically, RGB and YUV).
 *
 * @author Sebastien Vincent
 * @author Lyubomir Marinov
 * @author Eng Chong Meng
 */
public class SwScale extends AbstractCodec
{
    /**
     * The minimum height and/or width of the input and/or output to be passed
     * to <code>sws_scale</code> in order to prevent its crashing.
     */
    public static final int MIN_SWS_SCALE_HEIGHT_OR_WIDTH = 4;

    /**
     * Gets the FFmpeg <code>PixelFormat</code> equivalent of a specific FMJ <code>RGBFormat</code>.
     *
     * @param rgb the FMJ <code>RGBFormat</code> to get the equivalent FFmpeg <code>PixelFormat</code> of
     * @return the FFmpeg <code>PixelFormat</code> equivalent of the specified FMJ <code>RGBFormat</code>
     */
    private static int getFFmpegPixelFormat(RGBFormat rgb)
    {
        int pixfmt;

        switch (rgb.getBitsPerPixel()) {
            case 24:
                pixfmt = FFmpeg.PIX_FMT_RGB24;
                break;

            case 32:
                switch (rgb.getRedMask()) {
                    case 1:
                    case 0x000000ff:
                        pixfmt = FFmpeg.PIX_FMT_BGR32;
                        break;
                    case 2:
                    case 0x0000ff00:
                        pixfmt = FFmpeg.PIX_FMT_BGR32_1;
                        break;
                    case 3:
                    case 0x00ff0000:
                        pixfmt = FFmpeg.PIX_FMT_RGB32;
                        break;
                    case 4:
                    case 0xff000000:
                        pixfmt = FFmpeg.PIX_FMT_RGB32_1;
                        break;
                    default:
                        pixfmt = FFmpeg.PIX_FMT_NONE;
                        break;
                }
                break;

            default:
                pixfmt = FFmpeg.PIX_FMT_NONE;
                break;
        }

        return pixfmt;
    }

    /**
     * Gets a <code>VideoFormat</code> with a specific size i.e. width and height
     * using a specific <code>VideoFormat</code> as a template.
     *
     * @param format the <code>VideoFormat</code> which is the template for the <code>VideoFormat</code> to be returned
     * @param size the size i.e. width and height of the <code>VideoFormat</code> to be returned
     * @return a <code>VideoFormat</code> with the specified <code>size</code> and based on the specified <code>format</code>
     */
    private static VideoFormat setSize(VideoFormat format, Dimension size)
    {
        /*
         * Since the size of the Format has changed, its size-related properties
         * should change as well. Format#intersects doesn't seem to be cool
         * because it preserves them and thus the resulting Format is inconsistent.
         */
        if (format instanceof RGBFormat) {
            RGBFormat rgbFormat = (RGBFormat) format;
            Class<?> dataType = format.getDataType();
            int bitsPerPixel = rgbFormat.getBitsPerPixel();
            int pixelStride = rgbFormat.getPixelStride();

            if ((pixelStride == Format.NOT_SPECIFIED)
                    && (dataType != null)
                    && (bitsPerPixel != Format.NOT_SPECIFIED)) {
                pixelStride = dataType.equals(Format.byteArray)
                        ? (bitsPerPixel / 8) : 1;
            }
            format = new RGBFormat(
                    size,
                    /* maxDataLength */ Format.NOT_SPECIFIED,
                    dataType,
                    format.getFrameRate(),
                    bitsPerPixel,
                    rgbFormat.getRedMask(),
                    rgbFormat.getGreenMask(),
                    rgbFormat.getBlueMask(),
                    pixelStride,
                    ((pixelStride == Format.NOT_SPECIFIED) || (size == null))
                            ? Format.NOT_SPECIFIED : (pixelStride * size.width) /* lineStride */,
                    rgbFormat.getFlipped(),
                    rgbFormat.getEndian());
        }
        else if (format instanceof YUVFormat) {
            YUVFormat yuvFormat = (YUVFormat) format;

            format = new YUVFormat(
                    size,
                    /* maxDataLength */ Format.NOT_SPECIFIED,
                    format.getDataType(),
                    format.getFrameRate(),
                    yuvFormat.getYuvType(),
                    /* strideY */ Format.NOT_SPECIFIED,
                    /* strideUV */ Format.NOT_SPECIFIED,
                    0,
                    /* offsetU */ Format.NOT_SPECIFIED,
                    /* offsetV */ Format.NOT_SPECIFIED);
        }
        else if (format != null) {
            Timber.w("SwScale outputFormat of type %s is not supported for optimized scaling.",
                    format.getClass().getName());
        }
        return format;
    }

    /**
     * The indicator which determines whether this scaler will attempt to keep
     * the width and height of YUV 420 output even.
     */
    private final boolean fixOddYuv420Size;

    /**
     * The <code>FrameProcessingControl</code> of this <code>Codec</code> which allows
     * JMF to instruct it to drop frames because it's behind schedule.
     */
    private final FrameProcessingControlImpl frameProcessingControl = new FrameProcessingControlImpl();

    /**
     * The indicator which determines whether this instance is to preserve the
     * aspect ratio of the video frames provided to this instance as input to be
     * processed. If <code>true</code>, the <code>size</code> of the
     * <code>outputFormat</code> of this instance is used to device a rectangle into
     * which a scaled video frame should fit with the input aspect ratio preserved.
     */
    private final boolean preserveAspectRatio;

    private Dimension preserveAspectRatioCachedIn;

    private Dimension preserveAspectRatioCachedOut;

    private Dimension preserveAspectRatioCachedRet;

    /**
     * Supported output formats.
     */
    private VideoFormat[] supportedOutputFormats = new VideoFormat[]{
            new RGBFormat(),
            new YUVFormat(YUVFormat.YUV_420)
    };

    /**
     * The pointer to the <code>libswscale</code> context.
     */
    private long swsContext = 0;

    /**
     * Initializes a new <code>SwScale</code> instance which doesn't have an output
     * size and will use a default one when it becomes necessary unless an
     * explicit one is specified in the meantime.
     */
    public SwScale()
    {
        this(false);
    }

    /**
     * Initializes a new <code>SwScale</code> instance which can optionally attempt
     * to keep the width and height of YUV 420 output even.
     *
     * @param fixOddYuv420Size <code>true</code> to have the new instance keep the
     * width and height of YUV 420 output even; otherwise, <code>false</code>
     */
    public SwScale(boolean fixOddYuv420Size)
    {
        this(fixOddYuv420Size, false);
    }

    /**
     * Initializes a new <code>SwScale</code> instance which can optionally attempt
     * to keep the width and height of YUV 420 output even and to preserve the
     * aspect ratio of the video frames provided to the instance as input to be processed.
     *
     * @param fixOddYuv420Size <code>true</code> to have the new instance keep the
     * width and height of YUV 420 output even; otherwise, <code>false</code>
     * @param preserveAspectRatio <code>true</code> to have the new instance
     * preserve the aspect ratio of the video frames provided to it as input to
     * be processed; otherwise, <code>false</code>
     */
    public SwScale(boolean fixOddYuv420Size, boolean preserveAspectRatio)
    {
        this.fixOddYuv420Size = fixOddYuv420Size;
        this.preserveAspectRatio = preserveAspectRatio;

        inputFormats = new Format[]{
                new AVFrameFormat(),
                new RGBFormat(),
                new YUVFormat(YUVFormat.YUV_420)
        };
        addControl(frameProcessingControl);
    }

    /**
     * Close codec.
     */
    @Override
    public void close()
    {
        try {
            if (swsContext != 0) {
                FFmpeg.sws_freeContext(swsContext);
                swsContext = 0;
            }
        } finally {
            super.close();
        }
    }

    /**
     * Gets the <code>Format</code> in which this <code>Codec</code> is currently
     * configured to accept input media data.
     * <p>
     * Makes the protected super implementation public.
     * </p>
     *
     * @return the <code>Format</code> in which this <code>Codec</code> is currently configured to accept input media data
     * @see AbstractCodec#getInputFormat()
     */
    @Override
    public Format getInputFormat()
    {
        return super.getInputFormat();
    }

    /**
     * Gets the output size.
     *
     * @return the output size
     */
    public Dimension getOutputSize()
    {
        Format outputFormat = getOutputFormat();

        if (outputFormat == null) {
            // They all have one and the same size.
            outputFormat = supportedOutputFormats[0];
        }
        return ((VideoFormat) outputFormat).getSize();
    }

    /**
     * Gets the supported output formats for an input one.
     *
     * @param input input format to get supported output ones for
     * @return array of supported output formats
     */
    @Override
    public Format[] getSupportedOutputFormats(Format input)
    {
        if (input == null)
            return supportedOutputFormats;

        /*
         * if size is set for element 0 (YUVFormat), it is also set for element 1 (RGBFormat) and so on...
         */
        Dimension size = supportedOutputFormats[0].getSize();

        /*
         * no specified size set so return the same size as input in output format supported
         */
        VideoFormat videoInput = (VideoFormat) input;

        if (size == null)
            size = videoInput.getSize();

        float frameRate = videoInput.getFrameRate();

        return new Format[]{
                new RGBFormat(
                        size,
                        /* maxDataLength */ Format.NOT_SPECIFIED,
                        /* dataType */ null,
                        frameRate,
                        32,
                        /* red */ Format.NOT_SPECIFIED,
                        /* green */ Format.NOT_SPECIFIED,
                        /* blue */ Format.NOT_SPECIFIED),
                new YUVFormat(
                        size,
                        /* maxDataLength */ Format.NOT_SPECIFIED,
                        /* dataType */ null,
                        frameRate,
                        YUVFormat.YUV_420,
                        /* strideY */ Format.NOT_SPECIFIED,
                        /* strideUV */ Format.NOT_SPECIFIED,
                        /* offsetY */ Format.NOT_SPECIFIED,
                        /* offsetU */ Format.NOT_SPECIFIED,
                        /* offsetV */ Format.NOT_SPECIFIED)
        };
    }

    /**
     * Calculates an output size which has the aspect ratio of a specific input
     * size and fits into a specific output size.
     *
     * @param in the input size which defines the aspect ratio
     * @param out the output size which defines the rectangle into which the returned output size is to fit
     * @return an output size which has the aspect ratio of the specified input
     * size and fits into the specified output size
     */
    private Dimension preserveAspectRatio(Dimension in, Dimension out)
    {
        int inHeight = in.height, inWidth = in.width;
        int outHeight = out.height, outWidth = out.width;

        /*
         * Reduce the effects of allocation and garbage collection by caching
         * the arguments and the return value.
         */
        if ((preserveAspectRatioCachedIn != null)
                && (preserveAspectRatioCachedOut != null)
                && (preserveAspectRatioCachedIn.height == inHeight)
                && (preserveAspectRatioCachedIn.width == inWidth)
                && (preserveAspectRatioCachedOut.height == outHeight)
                && (preserveAspectRatioCachedOut.width == outWidth)
                && (preserveAspectRatioCachedRet != null))
            return preserveAspectRatioCachedRet;

        boolean scale = false;
        double heightRatio, widthRatio;

        if ((outHeight != inHeight) && (outHeight > 0)) {
            scale = true;
            heightRatio = inHeight / (double) outHeight;
        }
        else
            heightRatio = 1;
        if ((outWidth != inWidth) && (outWidth > 0)) {
            scale = true;
            widthRatio = inWidth / (double) outWidth;
        }
        else
            widthRatio = 1;

        Dimension ret = out;

        if (scale) {
            double ratio = Math.min(heightRatio, widthRatio);
            int retHeight, retWidth;

            retHeight = (int) (outHeight * ratio);
            retWidth = (int) (outWidth * ratio);
            /*
             * Preserve the aspect ratio only if it is going to make noticeable
             * differences in height and/or width; otherwise, play it safe.
             */
            if ((Math.abs(retHeight - outHeight) > 1)
                    || (Math.abs(retWidth - outWidth) > 1)) {
                // Make sure to not cause sws_scale to crash.
                if ((retHeight < MIN_SWS_SCALE_HEIGHT_OR_WIDTH)
                        || (retWidth < MIN_SWS_SCALE_HEIGHT_OR_WIDTH)) {
                    ret = new Dimension(retWidth, retHeight);
                    preserveAspectRatioCachedRet = ret;
                }
            }
        }

        preserveAspectRatioCachedIn = new Dimension(inWidth, inHeight);
        preserveAspectRatioCachedOut = new Dimension(outWidth, outHeight);
        if (ret == out)
            preserveAspectRatioCachedRet = preserveAspectRatioCachedOut;
        return ret;
    }

    /**
     * Processes (converts color space and/or scales) an input <code>Buffer</code> into an output <code>Buffer</code>.
     *
     * @param inBuf the input <code>Buffer</code> to process (from)
     * @param outBuf the output <code>Buffer</code> to process into
     * @return <code>BUFFER_PROCESSED_OK</code> if <code>in</code> has been successfully processed into <code>out</code>
     */
    public int process(Buffer inBuf, Buffer outBuf)
    {
        if (!checkInputBuffer(inBuf))
            return BUFFER_PROCESSED_FAILED;
        if (isEOM(inBuf)) {
            propagateEOM(outBuf);
            return BUFFER_PROCESSED_OK;
        }
        if (inBuf.isDiscard() || frameProcessingControl.isMinimalProcessing()) {
            outBuf.setDiscard(true);
            return BUFFER_PROCESSED_OK;
        }

        // Determine the input Format and size.
        VideoFormat inFormat = (VideoFormat) inBuf.getFormat();
        Format thisInFormat = getInputFormat();

        if ((inFormat != thisInFormat) && !inFormat.equals(thisInFormat))
            setInputFormat(inFormat);

        Dimension inSize = inFormat.getSize();
        if (inSize == null)
            return BUFFER_PROCESSED_FAILED;

        int inWidth = inSize.width, inHeight = inSize.height;

        if ((inWidth < MIN_SWS_SCALE_HEIGHT_OR_WIDTH)
                || (inHeight < MIN_SWS_SCALE_HEIGHT_OR_WIDTH)) {
            return OUTPUT_BUFFER_NOT_FILLED; // Otherwise, sws_scale will crash.
        }

        // Determine the output Format and size.
        VideoFormat outFormat = (VideoFormat) getOutputFormat();

        if (outFormat == null) {
            /*
             * The format of the output Buffer is not documented to be used as
             * input to the #process method. Anyway, we're trying to use it in
             * case this Codec doesn't have an outputFormat set which is unlikely to ever happen.
             */
            outFormat = (VideoFormat) outBuf.getFormat();
            if (outFormat == null)
                return BUFFER_PROCESSED_FAILED;
        }

        Dimension outSize = outFormat.getSize();
        if (outSize == null)
            outSize = inSize;
        else if (preserveAspectRatio)
            outSize = preserveAspectRatio(inSize, outSize);

        int outWidth = outSize.width, outHeight = outSize.height;
        if ((outWidth < MIN_SWS_SCALE_HEIGHT_OR_WIDTH)
                || (outHeight < MIN_SWS_SCALE_HEIGHT_OR_WIDTH)) {
            return OUTPUT_BUFFER_NOT_FILLED; // Otherwise, sws_scale will crash.
        }

        // Apply outSize to outFormat of the output Buffer.
        outFormat = setSize(outFormat, outSize);
        if (outFormat == null)
            return BUFFER_PROCESSED_FAILED;

        int dstFmt;
        int dstLength;

        if (outFormat instanceof RGBFormat) {
            dstFmt = getFFmpegPixelFormat((RGBFormat) outFormat);
            dstLength = (outWidth * outHeight * 4);
        }
        else if (outFormat instanceof YUVFormat) {
            dstFmt = FFmpeg.PIX_FMT_YUV420P;
            /* YUV420P is 12 bits per pixel. */
            dstLength = outWidth * outHeight
                    + 2 * ((outWidth + 1) / 2) * ((outHeight + 1) / 2);
        }
        else
            return BUFFER_PROCESSED_FAILED;

        Class<?> outDataType = outFormat.getDataType();
        Object dst = outBuf.getData();

        if (Format.byteArray.equals(outDataType)) {
            if ((dst == null) || (((byte[]) dst).length < dstLength))
                dst = new byte[dstLength];
        }
        else if (Format.intArray.equals(outDataType)) {
            /* Java int is always 4 bytes. */
            dstLength = dstLength / 4 + ((dstLength % 4 == 0) ? 0 : 1);
            if ((dst == null) || (((int[]) dst).length < dstLength))
                dst = new int[dstLength];
        }
        else if (Format.shortArray.equals(outDataType)) {
            /* Java short is always 2 bytes. */
            dstLength = dstLength / 2 + ((dstLength % 2 == 0) ? 0 : 1);
            if ((dst == null) || (((short[]) dst).length < dstLength))
                dst = new short[dstLength];
        }
        else {
            Timber.e("Unsupported output data type %s", outDataType);
            return BUFFER_PROCESSED_FAILED;
        }

        Object src = inBuf.getData();
        int srcFmt;
        long srcFrame;

        if (src instanceof AVFrame) {
            srcFmt = ((AVFrameFormat) inFormat).getPixFmt();
            srcFrame = ((AVFrame) src).getPtr();
        }
        else {
            srcFmt = (inFormat instanceof YUVFormat)
                    ? FFmpeg.PIX_FMT_YUV420P : getFFmpegPixelFormat((RGBFormat) inFormat);
            srcFrame = 0;
        }

        swsContext = FFmpeg.sws_getCachedContext(
                swsContext,
                inWidth, inHeight, srcFmt,
                outWidth, outHeight, dstFmt,
                FFmpeg.SWS_BICUBIC);

        if (srcFrame == 0) {
            FFmpeg.sws_scale(
                    swsContext,
                    src, srcFmt, inWidth, inHeight, 0, inHeight,
                    dst, dstFmt, outWidth, outHeight);
        }
        else {
            FFmpeg.sws_scale(
                    swsContext,
                    srcFrame, 0, inHeight,
                    dst, dstFmt, outWidth, outHeight);
        }

        outBuf.setData(dst);
        outBuf.setDuration(inBuf.getDuration());
        outBuf.setFlags(inBuf.getFlags());
        outBuf.setFormat(outFormat);
        outBuf.setLength(dstLength);
        outBuf.setOffset(0);
        outBuf.setSequenceNumber(inBuf.getSequenceNumber());
        outBuf.setTimeStamp(inBuf.getTimeStamp());

        // flags
        int inFlags = inBuf.getFlags();
        int outFlags = outBuf.getFlags();

        if ((inFlags & Buffer.FLAG_LIVE_DATA) != 0)
            outFlags |= Buffer.FLAG_LIVE_DATA;
        if ((inFlags & Buffer.FLAG_NO_WAIT) != 0)
            outFlags |= Buffer.FLAG_NO_WAIT;
        if ((inFlags & Buffer.FLAG_RELATIVE_TIME) != 0)
            outFlags |= Buffer.FLAG_RELATIVE_TIME;
        if ((inFlags & Buffer.FLAG_RTP_TIME) != 0)
            outFlags |= Buffer.FLAG_RTP_TIME;
        if ((inFlags & Buffer.FLAG_SYSTEM_TIME) != 0)
            outFlags |= Buffer.FLAG_SYSTEM_TIME;
        outBuf.setFlags(outFlags);

        return BUFFER_PROCESSED_OK;
    }

    /**
     * Sets the input format.
     *
     * @param format format to set
     * @return format
     */
    @Override
    public Format setInputFormat(Format format)
    {
        Format inputFormat = (format instanceof VideoFormat)
                ? super.setInputFormat(format)
                : null /* The input must be video, a size is not required. */;

        if (inputFormat != null) {
            Timber.log(TimberLog.FINER, "%s-%08x set to input in %s",
                    getClass().getName(), hashCode(), inputFormat);
        }
        return inputFormat;
    }

    /**
     * Sets the <code>Format</code> in which this <code>Codec</code> is to output media
     * data.
     *
     * @param format the <code>Format</code> in which this <code>Codec</code> is to output media data
     * @return the <code>Format</code> in which this <code>Codec</code> is currently
     * configured to output media data or <code>null</code> if <code>format</code> was
     * found to be incompatible with this <code>Codec</code>
     */
    @Override
    public Format setOutputFormat(Format format)
    {
        if (fixOddYuv420Size && (format instanceof YUVFormat)) {
            YUVFormat yuvFormat = (YUVFormat) format;

            if (YUVFormat.YUV_420 == yuvFormat.getYuvType()) {
                Dimension size = yuvFormat.getSize();

                if ((size != null) && (size.width > 2) && (size.height > 2)) {
                    int width = (size.width >> 1) << 1;
                    int height = (size.height >> 1) << 1;

                    if ((width != size.width) || (height != size.height)) {
                        format = new YUVFormat(
                                new Dimension(width, height),
                                /* maxDataLength */ Format.NOT_SPECIFIED,
                                yuvFormat.getDataType(),
                                yuvFormat.getFrameRate(),
                                yuvFormat.getYuvType(),
                                /* strideY */ Format.NOT_SPECIFIED,
                                /* strideUV */ Format.NOT_SPECIFIED,
                                0,
                                /* offsetU */ Format.NOT_SPECIFIED,
                                /* strideV */ Format.NOT_SPECIFIED);
                    }
                }
            }
        }

        Format outputFormat = super.setOutputFormat(format);

        if (outputFormat != null) {
            Timber.log(TimberLog.FINER, "%s %08x set to output in %s", getClass().getName(), hashCode(), outputFormat);
        }
        return outputFormat;
    }

    /**
     * Sets the size i.e. width and height of the current <code>outputFormat</code> of this <code>SwScale</code>
     *
     * @param size the size i.e. width and height to be set on the current
     * <code>outputFormat</code> of this <code>SwScale</code>
     */
    private void setOutputFormatSize(Dimension size)
    {
        VideoFormat outputFormat = (VideoFormat) getOutputFormat();

        if (outputFormat != null) {
            outputFormat = setSize(outputFormat, size);
            if (outputFormat != null)
                setOutputFormat(outputFormat);
        }
    }

    /**
     * Sets the output size.
     *
     * @param size the size to set as the output size
     */
    public void setOutputSize(Dimension size)
    {
        /*
         * If the specified output size is tiny enough to crash sws_scale, do not accept it.
         */
        if ((size != null)
                && ((size.height < MIN_SWS_SCALE_HEIGHT_OR_WIDTH)
                || (size.width < MIN_SWS_SCALE_HEIGHT_OR_WIDTH))) {
            return;
        }

        for (int i = 0; i < supportedOutputFormats.length; i++) {
            supportedOutputFormats[i] = setSize(supportedOutputFormats[i], size);
        }

        // Set the size to the outputFormat as well.
        setOutputFormatSize(size);
    }
}
