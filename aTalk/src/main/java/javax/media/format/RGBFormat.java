package javax.media.format;

import java.awt.*;

import javax.media.*;

/**
 * Describes uncompressed RGB data. The data is in interleaved form. RGB
 * components can be packed into a short or an int. If the pixel components are
 * packed, then the <tt>redMask</tt>, <tt>greenMask</tt> and <tt>blueMask</tt>
 * fields specify the bit masks, otherwise they specify the order of arrangement
 * of the components. For example:
 * <UL>
 * <LI>32-bit packed RGB stored as 32-bit integers would have the following
 * masks: redMask = 0x00FF0000, greenMask = 0x0000FF00, blueMask = 0x000000FF.</LI>
 * <LI>24-bit RGB stored as bytes would have the following masks: redMask = 1,
 * greenMask = 2, blueMask = 3 and pixelStride = 3.</LI>
 * </UL>
 */
public class RGBFormat extends VideoFormat
{
    /** Mask value for the Red component. */
    protected int redMask = NOT_SPECIFIED;

    /** Mask value for the Green component. */
    protected int greenMask = NOT_SPECIFIED;

    /** Mask value for the Blue component. */
    protected int blueMask = NOT_SPECIFIED;

    /**
     * The number of bits required to represent a pixel, including all three
     * color components.
     */
    protected int bitsPerPixel = NOT_SPECIFIED;

    /** Increment value of the array index from one pixel to the next. */
    protected int pixelStride = NOT_SPECIFIED;

    /**
     * Increment value of the array index from the first pixel on line n to the
     * first pixel on line n+1.
     */
    protected int lineStride = NOT_SPECIFIED;

    /**
     * Indicates whether or not the lines in the video frame are flipped
     * vertically (upside down).
     */
    protected int flipped = NOT_SPECIFIED;

    /** Endian ordering of the data where applicable */
    protected int endian = NOT_SPECIFIED;

    public static final int BIG_ENDIAN = 0;
    public static final int LITTLE_ENDIAN = 1;

    private static String ENCODING = VideoFormat.RGB;

    /**
     * Constructs a default <tt>RGBFormat</tt>.
     */
    public RGBFormat()
    {
        super(ENCODING);
        dataType = null;
    }

    /**
     * Constructs an <tt>RGBFormat</tt> object with the specified properties.
     * The pixel stride is initialized to the default for the specified data
     * type and bits per pixel. The line stride is initialized using the default
     * pixel stride and specified frame width. The image is not flipped and the
     * endian is LITTLE_ENDIAN.
     *
     * @param size
     *            A <tt>Dimension</tt> that specifies the frame size.
     * @param maxDataLength
     *            The maximum length of a data chunk.
     * @param dataType
     *            The type of the data. For example, byte array.
     * @param frameRate
     *            The frame rate.
     * @param bitsPerPixel
     *            The number of bits representing a pixel.
     * @param red
     *            The mask for the red color component.
     * @param green
     *            The mask for the green color component.
     * @param blue
     *            The mask for the blue color component.
     */
    public RGBFormat(Dimension size, int maxDataLength, Class<?> dataType,
            float frameRate, int bitsPerPixel, int red, int green, int blue)
    {
        super(ENCODING, size, maxDataLength, dataType, frameRate);
        this.bitsPerPixel = bitsPerPixel;
        this.redMask = red;
        this.greenMask = green;
        this.blueMask = blue;
        if (bitsPerPixel != NOT_SPECIFIED && dataType != null)
        {
            pixelStride = bitsPerPixel / 8;
            if (dataType != byteArray)
                pixelStride = 1;
        } else
        {
            pixelStride = NOT_SPECIFIED;
        }
        if (size != null && pixelStride != NOT_SPECIFIED)
            lineStride = pixelStride * size.width;
        else
            lineStride = NOT_SPECIFIED;
        flipped = FALSE;
        if (bitsPerPixel == 16 && dataType == byteArray)
            endian = LITTLE_ENDIAN;
        else
            endian = NOT_SPECIFIED;
    }

    /**
     * Constructs an <tt>RGBFormat</tt> object with the specified properties.
     *
     * @param size
     *            A <tt>Dimension</tt> that specifies the frame size.
     * @param maxDataLength
     *            The maximum length of a data chunk.
     * @param dataType
     *            The type of the data. For example, byte array.
     * @param frameRate
     *            The frame rate.
     * @param bitsPerPixel
     *            The number of bits representing a pixel.
     * @param red
     *            The mask for the red color component.
     * @param green
     *            The mask for the green color component.
     * @param blue
     *            The mask for the blue color component.
     * @param pixelStride
     *            The number of array elements between adjacent pixels.
     * @param flipped
     *            Indicates whether or not the lines in the video frame are
     *            flipped vertically (upside down). <tt>Format.TRUE</tt>
     *            indicates the image is flipped, <tt>Format.FALSE</tt>
     *            indicates that it is not.
     * @param endian
     *            The byte ordering used for this <tt>RGBFormat</tt>--
     *            <tt>BIG_ENDIAN</tt> or <tt>LITTLE_ENDIAN</tt>.
     */
    public RGBFormat(Dimension size, int maxDataLength, Class<?> dataType,
            float frameRate, int bitsPerPixel, int red, int green, int blue,
            int pixelStride, int lineStride, int flipped, int endian)
    {
        super(ENCODING, size, maxDataLength, dataType, frameRate);
        this.bitsPerPixel = bitsPerPixel;
        this.redMask = red;
        this.greenMask = green;
        this.blueMask = blue;
        this.pixelStride = pixelStride;
        this.lineStride = lineStride;
        this.flipped = flipped;
        this.endian = endian;
    }

    /**
     * Creates a clone of this <tt>RGBFormat</tt>.
     *
     * @return A clone of this <tt>RGBFormat</tt>.
     */
    @Override
    public Object clone()
    {
        RGBFormat f = new RGBFormat(size, maxDataLength, dataType, frameRate,
                bitsPerPixel, redMask, greenMask, blueMask, pixelStride,
                lineStride, flipped, endian);
        f.copy(this);
        return f;
    }

    /**
     * Copies the attributes from the specified <tt>Format</tt> into this
     * <tt>RGBFormat</tt>.
     *
     * @param f
     *            The <tt>Format</tt> to copy the attributes from.
     */
    @Override
    protected void copy(Format f)
    {
        super.copy(f);
        if (f instanceof RGBFormat)
        {
            RGBFormat other = (RGBFormat) f;
            bitsPerPixel = other.bitsPerPixel;
            redMask = other.redMask;
            greenMask = other.greenMask;
            blueMask = other.blueMask;
            pixelStride = other.pixelStride;
            lineStride = other.lineStride;
            flipped = other.flipped;
            endian = other.endian;
        }
    }

    /**
     * Compares the specified <tt>Format</tt> with this <tt>RGBFormat</tt>.
     * Returns <tt>true</tt> only if the specified <tt>Format</tt> is a
     * <tt>RGBFormat</tt> object and all of its attributes are identical to the
     * attributes in this <tt>RGBFormat</tt> .
     *
     * @param format
     *            The <tt>Format</tt> to compare.
     * @return true if the specified <tt>Format</tt> is the same as this one.
     */
    @Override
    public boolean equals(Object format)
    {
        if (format instanceof RGBFormat)
        {
            RGBFormat other = (RGBFormat) format;

            return super.equals(format) && bitsPerPixel == other.bitsPerPixel
                    && redMask == other.redMask && greenMask == other.greenMask
                    && blueMask == other.blueMask
                    && pixelStride == other.pixelStride
                    && lineStride == other.lineStride && endian == other.endian
                    && flipped == other.flipped;
        } else
            return false;
    }

    /**
     * Gets the number of bits required per pixel of data.
     *
     * @return An integer representing the number of bits per pixel.
     */
    public int getBitsPerPixel()
    {
        return bitsPerPixel;
    }

    /**
     * Gets the mask for the blue component.
     *
     * @return The blue mask.
     */
    public int getBlueMask()
    {
        return blueMask;
    }

    /**
     * Gets the endian ordering of the data for unpacked 16-bit data.
     *
     * @return An integer representing the endian ordering: BIG_ENDIAN,
     *         LITTLE_ENDIAN or NOT_SPECIFIED
     */
    public int getEndian()
    {
        return endian;
    }

    /**
     * Checks whether or not the video image is vertically flipped.
     *
     * @return <tt>Format.TRUE</tt> if the video is flipped,
     *         <tt>Format.FALSE</tt> if it is not.
     */
    public int getFlipped()
    {
        return flipped;
    }

    /**
     * Gets the mask for the green component.
     *
     * @return The green mask.
     */
    public int getGreenMask()
    {
        return greenMask;
    }

    /**
     * Gets the line stride--the number of array elements between adjacent rows
     * of pixels.
     *
     * @return An integer representing the line stride.
     */
    public int getLineStride()
    {
        return lineStride;
    }

    /**
     * Gets the pixel stride--the number of array elements between adjacent
     * pixels.
     *
     * @return An integer representing the pixel stride.
     */
    public int getPixelStride()
    {
        return pixelStride;
    }

    /**
     * Gets the mask for the red component.
     *
     * @return The red mask.
     */
    public int getRedMask()
    {
        return redMask;
    }

    /**
     * Finds the attributes shared by two matching <tt>Format</tt> objects. If
     * the specified <tt>Format</tt> does not match this one, the result is
     * undefined.
     *
     * @param format The matching <tt>Format</tt> to intersect with this
     * <tt>RGBFormat</tt>.
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
        if (!(format instanceof RGBFormat))
            return fmt;
        RGBFormat other = (RGBFormat) format;
        RGBFormat res = (RGBFormat) fmt;
        res.bitsPerPixel = (bitsPerPixel != NOT_SPECIFIED ? bitsPerPixel
                : other.bitsPerPixel);
        res.pixelStride = (pixelStride != NOT_SPECIFIED ? pixelStride
                : other.pixelStride);
        res.lineStride = (lineStride != NOT_SPECIFIED ? lineStride
                : other.lineStride);
        res.redMask = (redMask != NOT_SPECIFIED ? redMask : other.redMask);
        res.greenMask = (greenMask != NOT_SPECIFIED ? greenMask
                : other.greenMask);
        res.blueMask = (blueMask != NOT_SPECIFIED ? blueMask : other.blueMask);
        res.flipped = (flipped != NOT_SPECIFIED ? flipped : other.flipped);
        res.endian = (endian != NOT_SPECIFIED ? endian : other.endian);

        return res;
    }

    /**
     * Checks whether or not the specified <tt>Format</tt> <EM>matches</EM> this
     * <tt>RGBFormat</tt>. Matches only compares the attributes that are defined
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
        if (!(format instanceof RGBFormat))
            return true;

        RGBFormat other = (RGBFormat) format;

        boolean returnVal = (bitsPerPixel == NOT_SPECIFIED
                || other.bitsPerPixel == NOT_SPECIFIED || bitsPerPixel == other.bitsPerPixel)
                &&

                (redMask == NOT_SPECIFIED || other.redMask == NOT_SPECIFIED || redMask == other.redMask)
                && (greenMask == NOT_SPECIFIED
                        || other.greenMask == NOT_SPECIFIED || greenMask == other.greenMask)
                && (blueMask == NOT_SPECIFIED
                        || other.blueMask == NOT_SPECIFIED || blueMask == other.blueMask)
                &&

                (pixelStride == NOT_SPECIFIED
                        || other.pixelStride == NOT_SPECIFIED || pixelStride == other.pixelStride)
                &&

                (endian == NOT_SPECIFIED || other.endian == NOT_SPECIFIED || endian == other.endian)
                &&

                (flipped == NOT_SPECIFIED || other.flipped == NOT_SPECIFIED || flipped == other.flipped);

        return returnVal;
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
        RGBFormat fmt;
        if ((fmt = (RGBFormat) super.relax()) == null)
            return null;

        fmt.lineStride = NOT_SPECIFIED;
        fmt.pixelStride = NOT_SPECIFIED;

        return fmt;
    }

    /**
     * Gets a <tt>String</tt> representation of the attributes of this
     * <tt>RGBFormat</tt>. For example: "RGB, 352x240, ...".
     *
     * @return A <tt>String</tt> that describes the format attributes.
     */
    @Override
    public String toString()
    {
        String s = getEncoding().toUpperCase();
        if (size != null)
            s += ", " + size.width + "x" + size.height;
        if (frameRate != NOT_SPECIFIED)
            s += ", FrameRate=" + ((int) (frameRate * 10) / 10f);
        if (maxDataLength != NOT_SPECIFIED)
            s += ", Length=" + maxDataLength;
        s += ", " + bitsPerPixel + "-bit";
        s += ", Masks=" + redMask + ":" + greenMask + ":" + blueMask;
        if (pixelStride != 1)
            s += ", PixelStride=" + pixelStride;
        s += ", LineStride=" + lineStride;
        if (flipped != NOT_SPECIFIED)
            s += (flipped == Format.TRUE ? ", Flipped" : "");
        if (dataType == byteArray && bitsPerPixel == 16
                && endian != NOT_SPECIFIED)
            s += (endian == BIG_ENDIAN ? ", BigEndian" : ", LittleEndian");
        if (dataType != null && dataType != Format.byteArray)
            s += ", " + dataType;
        return s;
    }
}
