package javax.media.format;

import java.awt.*;

import javax.media.*;

/**
 * Describes YUV image data.
 */
public class YUVFormat extends VideoFormat
{
    /** YUV Planar 4:1:1 type. */
    public static final int YUV_411 = 1;
    /** YUV Planar 4:2:0 type. */
    public static final int YUV_420 = 2;
    /** YUV Planar 4:2:2 type. */
    public static final int YUV_422 = 4;
    /** YUV Planar 1:1:1 type. */
    public static final int YUV_111 = 8;
    /**
     * YUV Planar YVU9 type. Contains a Y value for every pixel and U and V
     * values for every 4x4 block of pixels.
     */
    public static final int YUV_YVU9 = 16;
    /**
     * YUV 4:2:2 interleaved format. The components are ordered as specified by
     * the offsetY, offsetU and offsetV attributes. For example, if the ordering
     * is Y, V, Y and U, the offsets would be offsetY=0;offsetU=3;offsetV=1. The
     * position of the second Y is implied. Y pixel stride is assumed to be 2
     * and the U and V pixel strides are assumed to be 4.
     */
    public static final int YUV_YUYV = 32;
    /**
     * When added to the yuvType, specifies that the chrominance values are
     * signed.
     */
    public static final int YUV_SIGNED = 64;

    /* 512 taken for YVU9 from Indeo 3.2 decoder */

    /** The YUV format type */
    protected int yuvType = NOT_SPECIFIED;
    /** Length of a row of Y values. Would be >= width of the frame. */
    protected int strideY = NOT_SPECIFIED;
    /** Length of a row of U or V values. */
    protected int strideUV = NOT_SPECIFIED;
    /**
     * When the YUV data is in planar format, specifies the offset into the data
     * for the Y plane. This value is ignored in the interleaved formats.
     */
    protected int offsetY = NOT_SPECIFIED;
    /**
     * When the YUV data is in planar format, specifies the offset into the data
     * for the U plane. This value is ignored in the interleaved formats.
     */
    protected int offsetU = NOT_SPECIFIED;
    /**
     * When the YUV data is in planar format, specifies the offset into the data
     * for the V plane. This value is ignored in the interleaved formats.
     */
    protected int offsetV = NOT_SPECIFIED;

    // The encoding string for YUV.
    private static String ENCODING = VideoFormat.YUV;

    /**
     * Constructs a <tt>YUVFormat</tt> object that represents all YUV formats.
     */
    public YUVFormat()
    {
        super(ENCODING);
    }

    /**
     * Constructs a <tt>YUVFormat</tt> with the specified properties. Use this
     * constructor for planar YUV formats. (YUV_411, YUV_420, YUV_422, YUV_111,
     * or YUV_YVU9.)
     *
     * @param size
     *            A <tt>Dimension</tt> that specifies the frame size.
     * @param maxDataLength
     *            The maximum size of the data array.
     * @param dataType
     *            The type of the data.
     * @param frameRate
     *            The frame rate.
     * @param yuvType
     *            The YUV ordering type.
     * @param strideY
     *            The number of data elements between the first Y component in a
     *            row and the first Y component in the next row.
     * @param strideUV
     *            The number of data elements between the first U component in a
     *            row and the first U component in the next row. The same value
     *            is expected for the V component.
     * @param offsetY
     *            The offset into the data array where the Y plane begins.
     * @param offsetU
     *            The offset into the data array where the U plane begins.
     * @param offsetV
     *            The offset into the data array where the V plane begins.
     */
    public YUVFormat(Dimension size, int maxDataLength, Class<?> dataType,
            float frameRate, int yuvType, int strideY, int strideUV,
            int offsetY, int offsetU, int offsetV)
    {
        // Call VideoFormat constructor
        super(ENCODING, size, maxDataLength, dataType, frameRate);
        // Set YUV properties.
        this.yuvType = yuvType;
        this.strideY = strideY;
        this.strideUV = strideUV;
        this.offsetY = offsetY;
        this.offsetU = offsetU;
        this.offsetV = offsetV;
    }

    /**
     * Constructs a <tt>YUVFormat</tt> with the specified properties. Use this
     * constructor for interleaved YUV formats (YUV_YUYV).
     *
     * @param size
     *            A <tt>Dimension</tt> that specifies the frame size.
     * @param maxDataLength
     *            The maximum size of the data array.
     * @param dataType
     *            The type of the data.
     * @param yuvType
     *            The YUV ordering type.
     * @param strideY
     *            The number of data elements between the first Y component in a
     *            row and the first Y component in the next row.
     */
    // public YUVFormat(Dimension size, int maxDataLength,
    // Class dataType,
    // int yuvType, int strideY) {
    // // Call VideoFormat constructor
    // super(ENCODING, size, maxDataLength, dataType);
    // // Set YUV properties.
    // this.yuvType = yuvType;
    // this.strideY = strideY;
    // this.strideUV = strideY;
    // this.offsetY = 0;
    // this.offsetU = 0;
    // this.offsetV = 0;
    // }

    /**
     * Constructs a <tt>YUVFormat</tt> object for a specific <tt>yuvType</tt>.
     *
     * @param yuvType
     *            The YUV type for this <tt>YUVFormat</tt>: YUV_411, YUV_420,
     *            YUV_422, YUV_111, YUV_YVU9, or YUV_YUYV.
     */
    public YUVFormat(int yuvType)
    {
        super(ENCODING);
        this.yuvType = yuvType;
    }

    /**
     * Creates a clone of this <tt>YUVFormat</tt>.
     *
     * @return A clone of this <tt>YUVFormat</tt>.
     */
    @Override
    public Object clone()
    {
        YUVFormat f = new YUVFormat(size, maxDataLength, dataType, frameRate,
                yuvType, strideY, strideUV, offsetY, offsetU, offsetV);
        f.copy(this);
        return f;
    }

    /**
     * Copies the attributes from the specified <tt>Format</tt> into this
     * <tt>YUVFormat</tt>.
     *
     * @param f
     *            The <tt>Format</tt> to copy the attributes from.
     */
    @Override
    protected void copy(Format f)
    {
        super.copy(f);
        if (f instanceof YUVFormat)
        {
            YUVFormat other = (YUVFormat) f;
            yuvType = other.yuvType;
            strideY = other.strideY;
            strideUV = other.strideUV;
            offsetY = other.offsetY;
            offsetU = other.offsetU;
            offsetV = other.offsetV;
        }
    }

    /**
     * Compares the specified <tt>Format</tt> with this <tt>YUVFormat</tt>.
     * Returns <tt>true</tt> only if the specified <tt>Format</tt> is a
     * <tt>YUVFormat</tt> object and all of its attributes are identical to the
     * attributes in this <tt>YUVFormat</tt> .
     *
     * @param format
     *            The <tt>Format</tt> to compare.
     * @return true if the specified <tt>Format</tt> is the same as this one.
     */
    @Override
    public boolean equals(Object format)
    {
        if (format instanceof YUVFormat)
        {
            YUVFormat other = (YUVFormat) format;

            return super.equals(format) && yuvType == other.yuvType
                    && strideY == other.strideY && strideUV == other.strideUV
                    && offsetY == other.offsetY && offsetU == other.offsetU
                    && offsetV == other.offsetV;
        } else
            return false;
    }

    /**
     * Gets the U offset--the position in the data where the U values begin.
     *
     * @return An integer representing the U offset.
     */
    public int getOffsetU()
    {
        return offsetU;
    }

    /**
     * Gets the V offset--the position in the data where the V values begin.
     *
     * @return An integer representing the V offset.
     */
    public int getOffsetV()
    {
        return offsetV;
    }

    /**
     * Gets the Y offset--the position in the data where the Y values begin.
     *
     * @return An integer representing the Y offset.
     */
    public int getOffsetY()
    {
        return offsetY;
    }

    /**
     * Gets the UV stride--the length of a row of U or V values.
     *
     * @return An integer representing the UV stride.
     */
    public int getStrideUV()
    {
        return strideUV;
    }

    /**
     * Gets the Y stride--the length of a row of Y values.
     *
     * @return An integer representing the Y stride.
     */
    public int getStrideY()
    {
        return strideY;
    }

    /**
     * Gets the YUV data format.
     *
     * @return The YUV type: YUV_411, YUV_420, YUV_422, YUV_111, YUV_YVU9, or
     *         YUV_YUYV.
     */
    public int getYuvType()
    {
        return yuvType;
    }

    /**
     * Finds the attributes shared by two matching <tt>Format</tt> objects. If
     * the specified <tt>Format</tt> does not match this one, the result is
     * undefined.
     *
     * @param format The matching <tt>Format</tt> to intersect with this
     * <tt>YUVFormat</tt>.
     * @return A <tt>Format</tt> object with its attributes set to those
     * attributes common to both <tt>Format</tt> objects.
     * @see #matches
     */
    @Override
    public Format intersects(Format format)
    {
        Format fmt;
        if ((fmt = super.intersects(format)) == null)
            return null;
        if (!(format instanceof YUVFormat))
            return fmt;
        YUVFormat other = (YUVFormat) format;
        YUVFormat res = (YUVFormat) fmt;
        res.yuvType = (yuvType != NOT_SPECIFIED ? yuvType : other.yuvType);
        res.strideY = (strideY != NOT_SPECIFIED ? strideY : other.strideY);
        res.strideUV = (strideUV != NOT_SPECIFIED ? strideUV : other.strideUV);

        res.offsetY = (offsetY != NOT_SPECIFIED ? offsetY : other.offsetY);
        res.offsetU = (offsetU != NOT_SPECIFIED ? offsetU : other.offsetU);
        res.offsetV = (offsetV != NOT_SPECIFIED ? offsetV : other.offsetV);

        return res;
    }

    /**
     * Checks whether or not the specified <tt>Format</tt> <EM>matches</EM> this
     * <tt>YUVFormat</tt>. Matches only compares the attributes that are defined
     * in the specified <tt>Format</tt>, unspecified attributes are ignored.
     * <p>
     * The two <tt>Format</tt> objects do not have to be of the same class to
     * match. For example, if "A" are "B" are being compared, a match is
     * possible if "A" is derived from "B" or "B" is derived from "A". (The
     * compared attributes must still match, or <tt>matches</tt> fails.)
     *
     * @param format
     *            The <tt>Format</tt> to compare with this one.
     * @return <tt>true</tt> if the specified <tt>Format</tt> matches this one,
     *         <tt>false</tt> if it does not.
     */
    @Override
    public boolean matches(Format format)
    {
        if (!super.matches(format))
            return false;
        if (!(format instanceof YUVFormat))
            return true;

        YUVFormat other = (YUVFormat) format;

        return (yuvType == NOT_SPECIFIED || other.yuvType == NOT_SPECIFIED || yuvType == other.yuvType)
                &&

                (strideY == NOT_SPECIFIED || other.strideY == NOT_SPECIFIED || strideY == other.strideY)
                && (strideUV == NOT_SPECIFIED
                        || other.strideUV == NOT_SPECIFIED || strideUV == other.strideUV)
                &&

                (offsetY == NOT_SPECIFIED || other.offsetY == NOT_SPECIFIED || offsetY == other.offsetY)
                && (offsetU == NOT_SPECIFIED || other.offsetU == NOT_SPECIFIED || offsetU == other.offsetU)
                && (offsetV == NOT_SPECIFIED || other.offsetV == NOT_SPECIFIED || offsetV == other.offsetV);

    }

    /**
     * Generate a format that's less restrictive than this format but contains
     * the basic attributes that will make this resulting format useful for
     * format matching.
     *
     * @return A <tt>Format</tt> that's less restrictive than the this format.
     */
    @Override
    public Format relax()
    {
        YUVFormat fmt;
        if ((fmt = (YUVFormat) super.relax()) == null)
            return null;

        fmt.strideY = NOT_SPECIFIED;
        fmt.strideUV = NOT_SPECIFIED;
        fmt.offsetY = NOT_SPECIFIED;
        fmt.offsetU = NOT_SPECIFIED;
        fmt.offsetV = NOT_SPECIFIED;

        return fmt;
    }

    /**
     * Gets a <tt>String</tt> representation of the attributes of this
     * <tt>YUVFormat</tt>. For example: "YUV Video Format, 352x240, ...".
     *
     * @return A <tt>String</tt> that describes the format attributes.
     */
    @Override
    public String toString()
    {
        return "YUV Video Format: Size = " + size + " MaxDataLength = "
                + maxDataLength + " DataType = " + dataType + " yuvType = "
                + yuvType + " StrideY = " + strideY + " StrideUV = " + strideUV
                + " OffsetY = " + offsetY + " OffsetU = " + offsetU
                + " OffsetV = " + offsetV + "\n";
    }
}
