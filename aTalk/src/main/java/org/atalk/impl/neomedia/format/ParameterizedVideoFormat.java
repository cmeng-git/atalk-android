/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.atalk.impl.neomedia.format;

import java.awt.Dimension;
import java.util.HashMap;
import java.util.Map;

import javax.media.Format;
import javax.media.format.VideoFormat;

/**
 * Implements a <code>VideoFormat</code> with format parameters (like {@link VideoMediaFormatImpl})
 * (some of) which (could) distinguish payload types.
 *
 * @author Lyubomir Marinov
 */
public class ParameterizedVideoFormat extends VideoFormat
{
    /**
     * Serial version UID.
     */
    private static final long serialVersionUID = 0L;

    /**
     * The format parameters of this <code>ParameterizedVideoFormat</code> instance.
     */
    private Map<String, String> fmtps;

    /**
     * Constructs a new <code>ParameterizedVideoFormat</code>.
     *
     * @param encoding encoding
     * @param size video size
     * @param maxDataLength maximum data length
     * @param dataType data type
     * @param frameRate frame rate
     * @param fmtps format parameters.
     */
    public ParameterizedVideoFormat(String encoding, Dimension size, int maxDataLength,
            Class<?> dataType, float frameRate, Map<String, String> fmtps)
    {
        super(encoding, size, maxDataLength, dataType, frameRate);

        this.fmtps = ((fmtps == null) || fmtps.isEmpty())
                ? MediaFormatImpl.EMPTY_FORMAT_PARAMETERS
                : new HashMap<>(fmtps);
    }

    /**
     * Initializes a new <code>ParameterizedVideoFormat</code> with a specific , and a specific
     * set of format parameters.
     *
     * @param encoding the encoding of the new instance
     * @param fmtps the format parameters of the new instance
     */
    public ParameterizedVideoFormat(String encoding, Map<String, String> fmtps)
    {
        super(encoding);

        this.fmtps = ((fmtps == null) || fmtps.isEmpty())
                ? MediaFormatImpl.EMPTY_FORMAT_PARAMETERS
                : new HashMap<>(fmtps);
    }

    /**
     * Initializes a new <code>ParameterizedVideoFormat</code> with a specific encoding, and a specific
     * set of format parameters.
     *
     * @param encoding the encoding of the new instance
     * @param fmtps the format parameters of the new instance in the form of an array of <code>String</code>s
     * in which the key and the value of an association are expressed as consecutive elements.
     */
    public ParameterizedVideoFormat(String encoding, String... fmtps)
    {
        this(encoding, toMap(fmtps));
    }

    /**
     * Initializes a new <code>ParameterizedVideoFormat</code> instance which has the same properties as this instance.
     *
     * @return a new <code>ParameterizedVideoFormat</code> instance which has the same properties as this instance.
     */
    @Override
    public Object clone()
    {
        ParameterizedVideoFormat f = new ParameterizedVideoFormat(
                getEncoding(),
                getSize(),
                getMaxDataLength(),
                getDataType(),
                getFrameRate(),
                /*
                 * The formatParameters will be copied by ParameterizedVideoFormat#copy(Format) bellow.
                 */
                null);

        f.copy(this);
        return f;
    }

    /**
     * Copies the properties of the specified <code>Format</code> into this instance.
     *
     * @param f the <code>Format</code> the properties of which are to be copied into this instance.
     */
    @Override
    protected void copy(Format f)
    {
        super.copy(f);

        if (f instanceof ParameterizedVideoFormat) {
            ParameterizedVideoFormat pvf = (ParameterizedVideoFormat) f;
            Map<String, String> pvfFmtps = pvf.getFormatParameters();

            fmtps = ((pvfFmtps == null) || pvfFmtps.isEmpty())
                    ? MediaFormatImpl.EMPTY_FORMAT_PARAMETERS
                    : new HashMap<>(pvfFmtps);
        }
    }

    /**
     * Determines whether a specific <code>Object</code> represents a value that is equal to the value
     * represented by this instance.
     *
     * @param obj the <code>Object</code> to be determined whether it represents a value that is equal to
     * the value represented by this instance
     * @return <code>true</code> if the specified <code>obj</code> represents a value that is equal to the
     * value represented by this instance; otherwise, <code>false</code>
     */
    @Override
    public boolean equals(Object obj)
    {
        if (!super.equals(obj))
            return false;

        Map<String, String> objFmtps = null;

        if (obj instanceof ParameterizedVideoFormat)
            objFmtps = ((ParameterizedVideoFormat) obj).getFormatParameters();
        return VideoMediaFormatImpl.formatParametersAreEqual(getEncoding(), getFormatParameters(), objFmtps);
    }

    /**
     * Returns true if the format parameters matched.
     *
     * @param format format to test
     * @return true if the format parameters match.
     */
    public boolean formatParametersMatch(Format format)
    {
        Map<String, String> formatFmtps = null;

        if (format instanceof ParameterizedVideoFormat)
            formatFmtps = ((ParameterizedVideoFormat) format).getFormatParameters();
        return VideoMediaFormatImpl.formatParametersMatch(getEncoding(), getFormatParameters(), formatFmtps);
    }

    /**
     * Returns the format parameters value for the specified name.
     *
     * @param name format parameters name
     * @return value for the specified format parameters name.
     */
    public String getFormatParameter(String name)
    {
        return fmtps.get(name);
    }

    /**
     * Returns the format parameters <code>Map</code>.
     *
     * @return the format parameters <code>Map</code>.
     */
    public Map<String, String> getFormatParameters()
    {
        return new HashMap<>(fmtps);
    }

    /**
     * Finds the attributes shared by two matching <code>Format</code>s. If the specified
     * <code>Format</code> does not match this one, the result is undefined.
     *
     * @param format the matching <code>Format</code> to intersect with this one
     * @return a <code>Format</code> with its attributes set to the attributes common to this instance
     * and the specified <code>format</code>
     */
    @Override
    public Format intersects(Format format)
    {
        Format intersection = super.intersects(format);

        if (intersection == null)
            return null;

        ((ParameterizedVideoFormat) intersection).fmtps = fmtps.isEmpty()
                ? MediaFormatImpl.EMPTY_FORMAT_PARAMETERS
                : getFormatParameters();
        return intersection;
    }

    /**
     * Determines whether a specific format matches this instance i.e. whether their attributes
     * match according to the definition of "match" given by {@link Format#matches(Format)}.
     *
     * @param format the <code>Format</code> to compare to this instance
     * @return <code>true</code> if the specified <code>format</code> matches this one; otherwise, <code>false</code>
     */
    @Override
    public boolean matches(Format format)
    {
        return super.matches(format) && formatParametersMatch(format);
    }

    /**
     * Initializes a new <code>Map</code> from an array in which the key, and the value of an
     * association are expressed as consecutive elements.
     *
     * @param <T> the very type of the keys and the values to be associated in the new <code>Map</code>
     * @param entries the associations to be created in the new <code>Map</code> where the key and value of an
     * association are expressed as consecutive elements
     * @return a new <code>Map</code> with the associations specified by <code>entries</code>
     */
    public static <T> Map<T, T> toMap(T... entries)
    {
        Map<T, T> map;

        if ((entries == null) || (entries.length == 0))
            map = null;
        else {
            map = new HashMap<T, T>();
            for (int i = 0; i < entries.length; i++)
                map.put(entries[i++], entries[i]);
        }
        return map;
    }

    @Override
    public String toString()
    {
        StringBuilder s = new StringBuilder();
        s.append(super.toString());

        // fmtps
        {
            s.append(", fmtps={");
            for (Map.Entry<String, String> fmtp : fmtps.entrySet()) {
                s.append(fmtp.getKey());
                s.append('=');
                s.append(fmtp.getValue());
                s.append(',');
            }

            int lastIndex = s.length() - 1;
            if (s.charAt(lastIndex) == ',')
                s.setCharAt(lastIndex, '}');
            else
                s.append('}');
        }
        return s.toString();
    }
}
