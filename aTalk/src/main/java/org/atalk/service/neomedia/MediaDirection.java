/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.atalk.service.neomedia;

/**
 * The <code>MediaDirections</code> enumeration contains a list of media directions that indicate
 * read/write capabilities of different entities in this <code>MediaService</code> such as for example devices.
 *
 * @author Emil Ivov
 */
public enum MediaDirection
{
    /**
     * Indicates that the related entity does not support neither input nor output (i.e. neither
     * send nor receive) operations.
     */
    INACTIVE("inactive"),

    /**
     * Represents a direction from the entity that this direction pertains to to the outside. When
     * applied to a <code>MediaDevice</code> the direction indicates that the device is a read-only one.
     * In the case of a stream a <code>SENDONLY</code> direction indicates that the stream is only
     * sending data to the remote party without receiving.
     */
    SENDONLY("sendonly"),

    /**
     * Represents a direction pointing to the entity that this object pertains to and from the
     * outside. When applied to a <code>MediaDevice</code> the direction indicates that the device is a
     * write-only one. In the case of a <code>MediaStream</code> a <code>RECVONLY</code> direction indicates
     * that the stream is only receiving data from the remote party without sending any.
     */
    RECVONLY("recvonly"),

    /**
     * Indicates that the related entity supports both input and output (send and receive)
     * operations.
     */
    SENDRECV("sendrecv");

    /**
     * The name of this direction.
     */
    private final String directionName;

    /**
     * Creates a <code>MediaDirection</code> instance with the specified name.
     *
     * @param directionName the name of the <code>MediaDirections</code> we'd like to create.
     */
    private MediaDirection(String directionName)
    {
        this.directionName = directionName;
    }

    /**
     * Returns the name of this <code>MediaDirection</code> (e.g. "sendonly" or "sendrecv"). The name
     * returned by this method is meant for use by session description mechanisms such as SIP/SDP or
     * XMPP/Jingle.
     *
     * @return the name of this <code>MediaDirection</code> (e.g. "sendonly", "recvonly", "sendrecv").
     */
    @Override
    public String toString()
    {
        return directionName;
    }

    /**
     * Applies an extra direction constraint to this <code>MediaDirection</code> or in other words
     * performs an <code>and</code> operation. This method is primarily meant for use by the
     * <code>getReverseMediaDirection(MediaDirection)</code> method while working on Offer/Answer media
     * negotiation..
     *
     * @param direction that direction constraint that we'd like to apply to this <code>MediaDirection</code>
     * @return the new <code>MediaDirection</code> obtained after applying the <code>direction</code>
     * constraint to this <code>MediaDirection</code>.
     */
    public MediaDirection and(MediaDirection direction)
    {
        if (this == SENDRECV) {
            return direction;
        }
        else if (this == SENDONLY) {
            if (direction == SENDONLY || direction == SENDRECV)
                return SENDONLY;
            else
                return INACTIVE;
        }
        else if (this == RECVONLY) {
            if (direction == RECVONLY || direction == SENDRECV)
                return RECVONLY;
            else
                return INACTIVE;
        }
        else
            return INACTIVE;
    }

    /**
     * Reverses a direction constraint on this <code>MediaDirection</code> or in other words performs an
     * <code>or</code> operation. This method is meant for use in cases like putting a stream off hold
     * or in other words reversing the <code>SENDONLY</code> constraint.
     *
     * @param direction the direction that we'd like to enable (i.e. add) to this <code>MediaDirection</code>
     * @return the new <code>MediaDirection</code> obtained after adding the specified
     * <code>direction</code> this <code>MediaDirection</code>.
     */
    public MediaDirection or(MediaDirection direction)
    {
        if (this == SENDRECV) {
            return this;
        }
        else if (this == SENDONLY) {
            if (direction.allowsReceiving())
                return SENDRECV;
            else
                return this;
        }
        else if (this == RECVONLY) {
            if (direction.allowsSending())
                return SENDRECV;
            else
                return this;
        }
        else
            // INACTIVE
            return direction;
    }

    /**
     * Returns the <code>MediaDirection</code> value corresponding to a remote party's perspective of
     * this <code>MediaDirection</code>. In other words, if I say I'll be sending only, for you this
     * means that you'll be receiving only. If however, I say I'll be both sending and receiving
     * (i.e. <code>SENDRECV</code>) then it means you'll be doing the same (i.e. again <code>SENDRECV</code>
     * ).
     *
     * @return the <code>MediaDirection</code> value corresponding to a remote party's perspective of
     * this <code>MediaDirection</code>.
     */
    public MediaDirection getReverseDirection()
    {
        switch (this) {
            case SENDRECV:
                return SENDRECV;
            case SENDONLY:
                return RECVONLY;
            case RECVONLY:
                return SENDONLY;
            default:
                return INACTIVE;
        }
    }

    /**
     * Returns the <code>MediaDirection</code> value corresponding to a remote party's perspective of
     * this <code>MediaDirection</code> applying a remote party constraint. In other words, if I say
     * I'll only be sending media (i.e. <code>SENDONLY</code>) and you know that you can both send and
     * receive (i.e. <code>SENDRECV</code>) then to you this means that you'll be only receiving media
     * (i.e. <code>RECVONLY</code>). If however I say that I can only receive a particular media type
     * (i.e. <code>RECVONLY</code>) and you are in the same situation then this means that neither of us
     * would be sending nor receiving and the stream would appear <code>INACTIVE</code> to you (and me
     * for that matter). The method is meant for use during Offer/Answer SDP negotiation.
     *
     * @param remotePartyDir the remote party <code>MediaDirection</code> constraint that we'd have to consider when
     * trying to obtain a <code>MediaDirection</code> corresponding to remoteParty's constraint.
     * @return the <code>MediaDirection</code> value corresponding to a remote party's perspective of
     * this <code>MediaDirection</code> applying a remote party constraint.
     */
    public MediaDirection getDirectionForAnswer(MediaDirection remotePartyDir)
    {
        return this.and(remotePartyDir.getReverseDirection());
    }

    /**
     * Determines whether the directions specified by this <code>MediaDirection</code> instance allow
     * for outgoing (i.e. sending) streams or in other words whether this is a <code>SENDONLY</code> or
     * a <code>SENDRECV</code> instance
     *
     * @return <code>true</code> if this <code>MediaDirection</code> instance includes the possibility of
     * sending and <code>false</code> otherwise.
     */
    public boolean allowsSending()
    {
        return this == SENDONLY || this == SENDRECV;
    }

    /**
     * Determines whether the directions specified by this <code>MediaDirection</code> instance allow
     * for incoming (i.e. receiving) streams or in other words whether this is a <code>RECVONLY</code>
     * or a <code>SENDRECV</code> instance
     *
     * @return <code>true</code> if this <code>MediaDirection</code> instance includes the possibility of
     * receiving and <code>false</code> otherwise.
     */
    public boolean allowsReceiving()
    {
        return this == RECVONLY || this == SENDRECV;
    }

    /**
     * Returns a <code>MediaDirection</code> value corresponding to the specified
     * <code>mediaDirectionStr</code> or in other words <code>SENDONLY</code> for "sendonly",
     * <code>RECVONLY</code> for "recvonly", <code>SENDRECV</code> for "sendrecv", and <code>INACTIVE</code> for "inactive".
     *
     * @param mediaDirectionStr the direction <code>String</code> that we'd like to parse.
     * @return a <code>MediaDirection</code> value corresponding to the specified
     * <code>mediaDirectionStr</code>.
     * @throws IllegalArgumentException in case <code>mediaDirectionStr</code> is not a valid media direction.
     */
    public static MediaDirection fromString(String mediaDirectionStr)
            throws IllegalArgumentException
    {
        for (MediaDirection value : values())
            if (value.toString().equals(mediaDirectionStr))
                return value;

        throw new IllegalArgumentException(mediaDirectionStr + " is not a valid media direction");
    }
}
