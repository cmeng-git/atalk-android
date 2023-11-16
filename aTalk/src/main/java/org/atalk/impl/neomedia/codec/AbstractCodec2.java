/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.atalk.impl.neomedia.codec;

import net.sf.fmj.media.AbstractCodec;
import net.sf.fmj.media.AbstractPlugIn;

import java.awt.Dimension;

import javax.media.Buffer;
import javax.media.Effect;
import javax.media.Format;
import javax.media.PlugIn;
import javax.media.ResourceUnavailableException;
import javax.media.format.YUVFormat;

/**
 * Extends FMJ's <code>AbstractCodec</code> to make it even easier to implement a <code>Codec</code>.
 *
 * @author Lyubomir Marinov
 * @author Eng Chong Meng
 */
public abstract class AbstractCodec2 extends AbstractCodec
{
    /**
     * The <code>Buffer</code> flag which indicates that the respective <code>Buffer</code> contains audio
     * data which has been decoded as a result of the operation of FEC.
     */
    public static final int BUFFER_FLAG_FEC = (1 << 24);

    /**
     * The <code>Buffer</code> flag which indicates that the respective <code>Buffer</code> contains audio
     * data which has been decoded as a result of the operation of PLC.
     */
    public static final int BUFFER_FLAG_PLC = (1 << 25);

    /**
     * An empty array of <code>Format</code> element type. Explicitly defined to reduce unnecessary allocations.
     */
    public static final Format[] EMPTY_FORMATS = new Format[0];

    /**
     * The maximum number of lost sequence numbers to conceal with packet loss mitigation
     * techniques such as Forward Error Correction (FEC) and Packet Loss Concealment (PLC)
     * when dealing with audio stream.
     */
    public static final int MAX_AUDIO_SEQUENCE_NUMBERS_TO_PLC = 3;

    /**
     * The maximum (RTP) sequence number value.
     */
    public static final int SEQUENCE_MAX = 65535;

    /**
     * The minimum (RTP) sequence number value.
     */
    public static final int SEQUENCE_MIN = 0;

    /**
     * Calculates the number of sequences which have been lost i.e. which have not been received.
     *
     * @param lastSeqNo the last received sequence number (prior to the current sequence number represented by
     * <code>seqNo</code>.) May be {@link Buffer#SEQUENCE_UNKNOWN}. May be equal to
     * <code>seqNo</code> for the purposes of Codec implementations which repeatedly process one
     * and the same input Buffer multiple times.
     * @param seqNo the current sequence number. May be equal to <code>lastSeqNo</code> for the purposes of
     * Codec implementations which repeatedly process the same input Buffer multiple times.
     * @return the number of sequences (between <code>lastSeqNo</code> and <code>seqNo</code>) which have
     * been lost i.e. which have not been received
     */
    public static int calculateLostSeqNoCount(long lastSeqNo, long seqNo)
    {
        if (lastSeqNo == Buffer.SEQUENCE_UNKNOWN)
            return 0;

        int delta = (int) (seqNo - lastSeqNo);

        /*
         * We explicitly allow the same sequence number to be received multiple times for the purposes of
         * Codec implementations which repeatedly process the one and the same input Buffer multiple times.
         */
        if (delta == 0)
            return 0;
        else if (delta > 0)
            return delta - 1; // The sequence number has not wrapped yet.
        else
            return delta + SEQUENCE_MAX; // The sequence number has wrapped.
    }

    /**
     * Increments a specific sequence number and makes sure that the result stays within the range
     * of valid RTP sequence number values.
     *
     * @param seqNo the sequence number to increment
     * @return a sequence number which represents an increment over the specified <code>seqNo</code>
     * within the range of valid RTP sequence number values.
     */
    public static long incrementSeqNo(long seqNo)
    {
        seqNo++;
        if (seqNo > SEQUENCE_MAX)
            seqNo = SEQUENCE_MIN;
        return seqNo;
    }

    /**
     * Utility to perform format matching.
     *
     * @param in input format
     * @param outs array of output formats
     * @return the first output format that is supported
     */
    public static Format matches(Format in, Format[] outs)
    {
        for (Format out : outs)
            if (in.matches(out))
                return out;
        return null;
    }

    public static YUVFormat specialize(YUVFormat yuvFormat, Class<?> dataType)
    {
        Dimension size = yuvFormat.getSize();
        int strideY = yuvFormat.getStrideY();

        if ((strideY == Format.NOT_SPECIFIED) && (size != null))
            strideY = size.width;

        int strideUV = yuvFormat.getStrideUV();

        if ((strideUV == Format.NOT_SPECIFIED) && (strideY != Format.NOT_SPECIFIED))
            strideUV = (strideY + 1) / 2;

        int offsetY = yuvFormat.getOffsetY();

        if (offsetY == Format.NOT_SPECIFIED)
            offsetY = 0;

        int offsetU = yuvFormat.getOffsetU();

        if ((offsetU == Format.NOT_SPECIFIED)
                && (strideY != Format.NOT_SPECIFIED)
                && (size != null))
            offsetU = offsetY + strideY * size.height;

        int offsetV = yuvFormat.getOffsetV();

        if ((offsetV == Format.NOT_SPECIFIED)
                && (offsetU != Format.NOT_SPECIFIED)
                && (strideUV != Format.NOT_SPECIFIED)
                && (size != null))
            offsetV = offsetU + strideUV * ((size.height + 1) / 2);

        int maxDataLength = ((strideY != Format.NOT_SPECIFIED)
                && (strideUV != Format.NOT_SPECIFIED))
                && (size != null)
                ? (strideY * size.height + 2 * strideUV * ((size.height + 1) / 2)
                + FFmpeg.FF_INPUT_BUFFER_PADDING_SIZE)
                : Format.NOT_SPECIFIED;

        return new YUVFormat(
                size,
                maxDataLength,
                (dataType == null) ? yuvFormat.getDataType() : dataType,
                yuvFormat.getFrameRate(),
                YUVFormat.YUV_420,
                strideY, strideUV,
                offsetY, offsetU, offsetV);
    }

    /**
     * Ensures that the value of the <code>data</code> property of a specific <code>Buffer</code> is an
     * array of <code>byte</code>s whose length is at least a specific number of bytes.
     *
     * @param buffer the <code>Buffer</code> whose <code>data</code> property value is to be validated
     * @param newSize the minimum length of the array of <code>byte</code> which is to be the value of the
     * <code>data</code> property of <code>buffer</code>
     * @param arraycopy <code>true</code> to copy the bytes which are in the value of the <code>data</code> property
     * of <code>buffer</code> at the time of the invocation of the method if the value of the
     * <code>data</code> property of <code>buffer</code> is an array of <code>byte</code> whose length is
     * less than <code>newSize</code>; otherwise, <code>false</code>
     * @return an array of <code>byte</code>s which is the value of the <code>data</code> property of
     * <code>buffer</code> and whose length is at least <code>newSize</code> number of bytes
     */
    public static byte[] validateByteArraySize(Buffer buffer, int newSize, boolean arraycopy)
    {
        Object data = buffer.getData();
        byte[] newBytes;

        if (data instanceof byte[]) {
            byte[] bytes = (byte[]) data;

            if (bytes.length < newSize) {
                newBytes = new byte[newSize];
                buffer.setData(newBytes);
                if (arraycopy) {
                    System.arraycopy(bytes, 0, newBytes, 0, bytes.length);
                }
                else {
                    buffer.setLength(0);
                    buffer.setOffset(0);
                }
            }
            else {
                newBytes = bytes;
            }
        }
        else {
            newBytes = new byte[newSize];
            buffer.setData(newBytes);
            buffer.setLength(0);
            buffer.setOffset(0);
        }
        return newBytes;
    }

    /**
     * The bitmap/flag mask of optional features supported by this <code>AbstractCodec2</code> such as
     * {@link #BUFFER_FLAG_FEC} and {@link #BUFFER_FLAG_PLC}.
     */
    protected int features;

    private final Class<? extends Format> formatClass;

    /**
     * The total input length processed by all invocations of {@link #process(Buffer, Buffer)}.
     * Introduced for the purposes of debugging at the time of this writing.
     */
    private long inLenProcessed;

    /**
     * The name of this <code>PlugIn</code>.
     */
    private final String name;

    /**
     * The total output length processed by all invocations of {@link #process(Buffer, Buffer)}.
     * Introduced for the purposes of debugging at the time of this writing.
     */
    private long outLenProcessed;

    private final Format[] supportedOutputFormats;

    /**
     * Initializes a new <code>AbstractCodec2</code> instance with a specific <code>PlugIn</code> name, a
     * specific <code>Class</code> of input and output <code>Format</code>s and a specific list of
     * <code>Format</code>s supported as output.
     *
     * @param name the <code>PlugIn</code> name of the new instance
     * @param formatClass the <code>Class</code> of input and output <code>Format</code>s supported by the new instance
     * @param supportedOutputFormats the list of <code>Format</code>s supported by the new instance as output
     */
    protected AbstractCodec2(String name, Class<? extends Format> formatClass, Format[] supportedOutputFormats)
    {
        this.formatClass = formatClass;
        this.name = name;
        this.supportedOutputFormats = supportedOutputFormats;

        /*
         * An Effect is a Codec that does not modify the Format of the data, it modifies the contents.
         */
        if (this instanceof Effect)
            inputFormats = this.supportedOutputFormats;
    }

    @Override
    public void close()
    {
        if (!opened)
            return;

        doClose();
        opened = false;
        super.close();
    }

    protected void discardOutputBuffer(Buffer outputBuffer)
    {
        outputBuffer.setDiscard(true);
    }

    protected abstract void doClose();

    /**
     * Opens this <code>Codec</code> and acquires the resources that it needs to operate. A call to
     * {@link PlugIn#open()} on this instance will result in a call to <code>doOpen</code> only if
     * {@link AbstractCodec#opened} is <code>false</code>. All required input and/or output formats are
     * assumed to have been set on this <code>Codec</code> before <code>doOpen</code> is called.
     *
     * @throws ResourceUnavailableException if any of the resources that this <code>Codec</code> needs to operate cannot be acquired
     */
    protected abstract void doOpen()
            throws ResourceUnavailableException;

    protected abstract int doProcess(Buffer inBuf, Buffer outBuf);

    /**
     * Gets the <code>Format</code>s which are supported by this <code>Codec</code> as output when the
     * input is in a specific <code>Format</code>.
     *
     * @param inputFormat the <code>Format</code> of the input for which the supported
     * output <code>Format</code>s are to be returned
     * @return an array of <code>Format</code>s supported by this <code>Codec</code> as output when the
     * input is in the specified <code>inputFormat</code>
     */
    protected Format[] getMatchingOutputFormats(Format inputFormat)
    {
        /*
         * An Effect is a Codec that does not modify the Format of the data, it modifies the contents.
         */
        if (this instanceof Effect)
            return new Format[]{inputFormat};

        return (supportedOutputFormats == null)
                ? EMPTY_FORMATS : supportedOutputFormats.clone();
    }

    @Override
    public String getName()
    {
        return (name == null) ? super.getName() : name;
    }

    /**
     * Implements {@link AbstractCodec#getSupportedOutputFormats(Format)}.
     *
     * @param inputFormat input format
     * @return array of supported output format
     * @see AbstractCodec#getSupportedOutputFormats(Format)
     */
    @Override
    public Format[] getSupportedOutputFormats(Format inputFormat)
    {
        if (inputFormat == null)
            return supportedOutputFormats;

        if (!formatClass.isInstance(inputFormat)
                || (matches(inputFormat, inputFormats) == null))
            return EMPTY_FORMATS;

        return getMatchingOutputFormats(inputFormat);
    }

    /**
     * Opens this <code>PlugIn</code> software or hardware component and acquires the resources that it
     * needs to operate. All required input and/or output formats have to be set on this
     * <code>PlugIn</code> before <code>open</code> is called. Buffers should not be passed into this
     * <code>PlugIn</code> without first calling <code>open</code>.
     *
     * @throws ResourceUnavailableException if any of the resources that this <code>PlugIn</code> needs to operate cannot be acquired
     * @see AbstractPlugIn#open()
     */
    @Override
    public void open()
            throws ResourceUnavailableException
    {
        if (opened)
            return;

        doOpen();
        opened = true;
        super.open();
    }

    /**
     * Implements <code>AbstractCodec#process(Buffer, Buffer)</code>.
     *
     * @param inBuf input buffer
     * @param outBuf out buffer
     * @return <code>BUFFER_PROCESSED_OK</code> if the specified <code>inBuff</code> was successfully
     * processed or <code>BUFFER_PROCESSED_FAILED</code> if the specified was not successfully processed
     * @see AbstractCodec#process(Buffer, Buffer)
     */
    @Override
    public int process(Buffer inBuf, Buffer outBuf)
    {
        if (!checkInputBuffer(inBuf))
            return BUFFER_PROCESSED_FAILED;
        if (isEOM(inBuf)) {
            propagateEOM(outBuf);
            return BUFFER_PROCESSED_OK;
        }
        if (inBuf.isDiscard()) {
            discardOutputBuffer(outBuf);
            return BUFFER_PROCESSED_OK;
        }

        // Must update the inputFormat when there is a change in inBuf format
        Format inFormat = inBuf.getFormat();
        if ((inFormat != inputFormat) && !inFormat.matches(inputFormat))
            setInputFormat(inFormat);

        int inLenProcessed = inBuf.getLength();
        int process;

        // Buffer.FLAG_SILENCE is set only when the intention is to drop the
        // specified input Buffer but to note that it has not been lost. The
        // latter is usually necessary if this AbstractCodec2 does Forward Error
        // Correction (FEC) and/or Packet Loss Concealment (PLC) and may cause
        // noticeable artifacts otherwise.
        if ((((BUFFER_FLAG_FEC | BUFFER_FLAG_PLC) & features) == 0)
                && ((Buffer.FLAG_SILENCE & inBuf.getFlags()) != 0)) {
            process = OUTPUT_BUFFER_NOT_FILLED;
        }
        else {
            process = doProcess(inBuf, outBuf);
        }

        // Keep track of additional information for the purposes of debugging.
        if ((process & INPUT_BUFFER_NOT_CONSUMED) != 0)
            inLenProcessed -= inBuf.getLength();
        if (inLenProcessed < 0)
            inLenProcessed = 0;

        int outLenProcessed;

        if (((process & BUFFER_PROCESSED_FAILED) != 0)
                || ((process & OUTPUT_BUFFER_NOT_FILLED)) != 0) {
            outLenProcessed = 0;
        }
        else {
            outLenProcessed = outBuf.getLength();
            if (outLenProcessed < 0)
                outLenProcessed = 0;
        }
        this.inLenProcessed += inLenProcessed;
        this.outLenProcessed += outLenProcessed;

        return process;
    }

    @Override
    public Format setInputFormat(Format format)
    {
        if (!formatClass.isInstance(format)
                || (matches(format, inputFormats) == null))
            return null;

        return super.setInputFormat(format);
    }

    @Override
    public Format setOutputFormat(Format format)
    {
        if (!formatClass.isInstance(format)
                || (matches(format, getMatchingOutputFormats(inputFormat)) == null))
            return null;

        return super.setOutputFormat(format);
    }

    /**
     * Updates the <code>format</code>, <code>length</code> and <code>offset</code> of a specific output
     * <code>Buffer</code> to specific values.
     *
     * @param outputBuffer the output <code>Buffer</code> to update the properties of
     * @param format the <code>Format</code> to set on <code>outputBuffer</code>
     * @param length the length to set on <code>outputBuffer</code>
     * @param offset the offset to set on <code>outputBuffer</code>
     */
    protected void updateOutput(Buffer outputBuffer, Format format, int length, int offset)
    {
        outputBuffer.setFormat(format);
        outputBuffer.setLength(length);
        outputBuffer.setOffset(offset);
    }

    protected short[] validateShortArraySize(Buffer buffer, int newSize)
    {
        Object data = buffer.getData();
        short[] newShorts;

        if (data instanceof short[]) {
            short[] shorts = (short[]) data;

            if (shorts.length >= newSize)
                return shorts;

            newShorts = new short[newSize];
            System.arraycopy(shorts, 0, newShorts, 0, shorts.length);
        }
        else {
            newShorts = new short[newSize];
            buffer.setLength(0);
            buffer.setOffset(0);
        }
        buffer.setData(newShorts);
        return newShorts;
    }

    private static final char[] HEX_ARRAY = "0123456789ABCDEF".toCharArray();
    public static String bytesToHex(byte[] bytes, int length) {
        int k = 0;
        char[] hexChars = new char[length * 2 + length/4];
        for (int j = 0; j < length; j++) {
            int v = bytes[j] & 0xFF;
            hexChars[j * 2 + k] = HEX_ARRAY[v >>> 4];
            hexChars[j * 2 + 1 + k] = HEX_ARRAY[v & 0x0F];
            if (j % 4 == 3) {
                hexChars[j * 2 + 2 + k++] = 0x20;
            }
        }
        return new String(hexChars);
    }
}
