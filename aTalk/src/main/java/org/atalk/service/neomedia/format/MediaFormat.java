/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.atalk.service.neomedia.format;

import org.atalk.util.MediaType;

import java.util.Map;

/**
 * The <code>MediaFormat</code> interface represents a generic (i.e. audio/video or other) format used
 * to represent media represent a media stream.
 * <p>
 * The interface contains utility methods for extracting common media format properties such as the
 * name of the underlying encoding, or clock rate or in order comparing to compare formats.
 * Extending interfaces representing audio or video formats are likely to add other methods.
 *
 * @author Emil Ivov
 * @author Lyubomir Marinov
 * @author Eng Chong Meng
 */
public interface MediaFormat
{
    /**
     * The constant returned by {@link #getRTPPayloadType()} when the <code>MediaFormat</code> instance
     * describes a format without an RTP payload type (number) known in RFC 3551 "RTP Profile for
     * Audio and Video Conferences with Minimal Control".
     */
    byte RTP_PAYLOAD_TYPE_UNKNOWN = -1;

    /**
     * The minimum integer that is allowed for use in dynamic payload type assignment.
     */
    int MIN_DYNAMIC_PAYLOAD_TYPE = 96;

    /**
     * The maximum integer that is allowed for use in dynamic payload type assignment.
     */
    int MAX_DYNAMIC_PAYLOAD_TYPE = 127;

    /**
     * Returns the type of this <code>MediaFormat</code> (e.g. audio or video).
     *
     * @return the <code>MediaType</code> that this format represents (e.g. audio or video).
     */
    MediaType getMediaType();

    /**
     * Returns the name of the encoding (i.e. codec) used by this <code>MediaFormat</code>.
     *
     * @return The name of the encoding that this <code>MediaFormat</code> is using.
     */
    String getEncoding();

    /**
     * Returns the clock rate associated with this <code>MediaFormat</code>.
     *
     * @return The clock rate associated with this format.
     */
    double getClockRate();

    /**
     * Returns a <code>String</code> representation of the clock rate associated with this
     * <code>MediaFormat</code> making sure that the value appears as an integer (i.e. contains no
     * decimal point) unless it is actually a non integer.
     *
     * @return a <code>String</code> representation of the clock rate associated with this <code>MediaFormat</code>.
     */
    String getClockRateString();

    /**
     * Returns a <code>String</code> representation of the real used clock rate associated with this
     * <code>MediaFormat</code> making sure that the value appears as an integer (i.e. contains no
     * decimal point) unless it is actually a non integer. This function corrects the problem of the
     * G.722 codec which advertises its clock rate to be 8 kHz while 16 kHz is really used to encode
     * the stream (that's an error noted in the respective RFC and kept for the sake of compatibility.).
     *
     * @return a <code>String</code> representation of the real used clock rate associated with this <code>MediaFormat</code>.
     */
    String getRealUsedClockRateString();

    /**
     * Determines whether this <code>MediaFormat</code> is equal to <code>mediaFormat</code> i.e. they have
     * the same encoding, clock rate, format parameters, advanced attributes, etc.
     *
     * @param mediaFormat the <code>MediaFormat</code> to compare to this instance
     * @return <code>true</code> if <code>mediaFormat</code> is equal to this format and <code>false</code> otherwise.
     */
    @Override
    boolean equals(Object mediaFormat);

    /**
     * Determines whether the format parameters of this <code>MediaFormat</code> match a specific set of format parameters.
     *
     * @param fmtps the set of format parameters to match to the format parameters of this <code>MediaFormat</code>
     * @return <code>true</code> if this <code>MediaFormat</code> considers <code>fmtps</code> matching its
     * format parameters; otherwise, <code>false</code>
     */
    boolean formatParametersMatch(Map<String, String> fmtps);

    /**
     * Returns a <code>Map</code> containing advanced parameters specific to this particular <code>MediaFormat</code>.
     * The parameters returned here are meant for use in SIP/SDP or XMPP session descriptions.
     *
     * @return a <code>Map</code> containing advanced parameters specific to this particular <code>MediaFormat</code>
     */
    Map<String, String> getAdvancedAttributes();

    /**
     * Check to see if advancedAttributes contains the specific parameter name-value pair
     *
     * @param parameterName the key of the <parameter/> name-value pair
     * @return true if the <parameter/> contains the specified key name
     */
    boolean hasParameter(String parameterName);

    /**
     * Remove the specific parameter name-value pair from advancedAttributes
     *
     * @param parameterName the key of the <parameter/> name-value pair to be removed
     * @see org.atalk.impl.neomedia.format.MediaFormatImpl.FORMAT_PARAMETER_ATTR_IMAGEATTR
     */
    void removeParameter(String parameterName);

    /**
     * Returns a <code>Map</code> containing parameters specific to this particular <code>MediaFormat</code>.
     * The parameters returned here are meant for use in SIP/SDP or XMPP session descriptions
     * where they get transported through the "fmtp:" attribute or <parameter/> tag respectively.
     *
     * @return a <code>Map</code> containing parameters specific to this particular <code>MediaFormat</code>
     * .
     */
    Map<String, String> getFormatParameters();

    /**
     * Gets the RTP payload type (number) of this <code>MediaFormat</code> as it is known in RFC 3551
     * "RTP Profile for Audio and Video Conferences with Minimal Control".
     *
     * @return the RTP payload type of this <code>MediaFormat</code> if it is known in RFC 3551 "RTP
     * Profile for Audio and Video Conferences with Minimal Control"; otherwise,
     * {@link #RTP_PAYLOAD_TYPE_UNKNOWN}
     */
    byte getRTPPayloadType();

    /**
     * Sets additional codec settings.
     *
     * @param settings additional settings represented by a map.
     */
    void setAdditionalCodecSettings(Map<String, String> settings);

    /**
     * Returns additional codec settings.
     *
     * @return additional settings represented by a map.
     */
    Map<String, String> getAdditionalCodecSettings();

    /**
     * Returns a <code>String</code> representation of this <code>MediaFormat</code> containing important
     * format attributes such as the encoding for example.
     *
     * @return a <code>String</code> representation of this <code>MediaFormat</code>.
     */
    @Override
    String toString();

    /**
     * Determines whether this <code>MediaFormat</code> matches properties of a specific
     * <code>MediaFormat</code>, such as <code>mediaType</code>, <code>encoding</code>, <code>clockRate</code> and
     * <code>channels</code> for <code>MediaFormat</code>s with <code>mediaType</code> equal to {@link MediaType#AUDIO}.
     *
     * @param format the {@link MediaFormat} whose properties we'd like to examine
     */
    boolean matches(MediaFormat format);

    /**
     * Determines whether this <code>MediaFormat</code> has specific values for its properties
     * <code>mediaType</code>, <code>encoding</code>, <code>clockRate</code> and <code>channels</code> for
     * <code>MediaFormat</code>s with <code>mediaType</code> equal to {@link MediaType#AUDIO}.
     *
     * @param mediaType the type we expect {@link MediaFormat} to have
     * @param encoding the encoding we are looking for.
     * @param clockRate the clock rate that we'd like the format to have.
     * @param channels the number of channels that expect to find in this format
     * @param formatParameters the format parameters expected to match these of the specified <code>format</code>
     * @return <code>true</code> if the specified <code>format</code> has specific values for its properties
     * <code>mediaType</code>, <code>encoding</code>, <code>clockRate</code> and <code>channels</code>; otherwise, <code>false</code>
     */
    boolean matches(MediaType mediaType, String encoding, double clockRate, int channels,
            Map<String, String> formatParameters);
}
