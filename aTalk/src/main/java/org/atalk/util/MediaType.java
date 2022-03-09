/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.atalk.util;

/**
 * The <code>MediaType</code> enumeration contains a list of media types.
 *
 * @see <a href="http://www.iana.org/assignments/sdp-parameters/sdp-parameters.xhtml#sdp-parameters-1">
 *     Session Description Protocol (SDP) Parameters, media</a>
 *
 * @author Emil Ivov
 * @author Eng Chong Meng
 */
public enum MediaType
{
    /**
     * Represents an AUDIO media type.
     */
    AUDIO("audio"),

    /**
     * Represents a VIDEO media type.
     */
    VIDEO("video"),

    /**
     * Represents a TEXT media type. See RFC4103.
     */
    TEXT("text"),

    /**
     * Represents an APPLICATION media type.
     */
    APPLICATION("application"),

    /**
     * Represents a (chat-) MESSAGE media type.
     */
    MESSAGE("message"),

    /**
     * Represents an IMAGE media type. See RFC6466.
     */
    IMAGE("image"),

    /**
     * Represents a DATA media type.
     *
     * @deprecated In RFC4566. Still defined to avoid parsing errors.
     */
    @Deprecated
    CONTROL("control"),

    /**
     * Represents a DATA media type.
     */
    DATA("data");

    /**
     * The name of this <code>MediaType</code>.
     */
    private final String mediaTypeName;

    /**
     * Creates a <code>MediaType</code> instance with the specified name.
     *
     * @param mediaTypeName the name of the <code>MediaType</code> we'd like to create.
     */
    private MediaType(String mediaTypeName)
    {
        this.mediaTypeName = mediaTypeName;
    }

    /**
     * Returns the name of this MediaType (e.g. "audio" or "video"). The name returned by this
     * method is meant for use by session description mechanisms such as SIP/SDP or XMPP/Jingle.
     *
     * @return the name of this MediaType (e.g. "audio" or "video").
     */
    @Override
    public String toString()
    {
        return mediaTypeName;
    }

    /**
     * Returns a <code>MediaType</code> value corresponding to the specified <code>mediaTypeName</code>
     * or in other words <code>AUDIO</code>, <code>MESSAGE</code> or <code>VIDEO</code>.
     *
     * @param mediaTypeName the name that we'd like to parse.
     * @return a <code>MediaType</code> value corresponding to the specified <code>mediaTypeName</code>.
     * @throws IllegalArgumentException in case <code>mediaTypeName</code> is not a valid or currently supported media type.
     */
    public static MediaType parseString(String mediaTypeName)
            throws IllegalArgumentException
    {
        if (AUDIO.toString().equalsIgnoreCase(mediaTypeName))
            return AUDIO;

        if (VIDEO.toString().equalsIgnoreCase(mediaTypeName))
            return VIDEO;

        if (TEXT.toString().equalsIgnoreCase(mediaTypeName))
            return TEXT;

        if (APPLICATION.toString().equalsIgnoreCase(mediaTypeName))
            return APPLICATION;

        if (MESSAGE.toString().equalsIgnoreCase(mediaTypeName))
            return MESSAGE;

        if (IMAGE.toString().equalsIgnoreCase(mediaTypeName))
            return IMAGE;

        if (CONTROL.toString().equalsIgnoreCase(mediaTypeName))
            return CONTROL;

        if (DATA.toString().equalsIgnoreCase(mediaTypeName))
            return DATA;

        throw new IllegalArgumentException(mediaTypeName + " is not a currently supported MediaType");
    }
}
