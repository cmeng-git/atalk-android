/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.jivesoftware.smackx.jingle;

/**
 * Contains an enumeration of all possible <code>session-info</code> element.
 *
 * @author Emil Ivov
 */
public enum SessionInfoType
{
    /**
     * The <code>active</code> payload indicates that the principal or device is again actively
     * participating in the session after having been on mute or having put the other party on hold.
     * The <code>active</code> element applies to all aspects of the session, and thus does not possess
     * a 'name' attribute.
     */
    active,

    /**
     * The <code>hold</code> payload indicates that the principal is temporarily not listening for media
     * from the other party
     */
    hold,

    /**
     * The <code>mute</code> payload indicates that the principal is temporarily not sending media to
     * the other party but continuing to accept media from the other party.
     */
    mute,

    /**
     * The <code>ringing</code> payload indicates that the device is ringing but the principal has not
     * yet interacted with it to answer (this maps to the SIP 180 response code).
     */
    ringing,

    /**
     * Ends a <code>hold</code> state.
     */
    unhold,

    /**
     * Ends a <code>mute</code> state.
     */
    unmute;
}
