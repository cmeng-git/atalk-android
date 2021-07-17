/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package net.java.sip.communicator.service.protocol.event;

import net.java.sip.communicator.service.protocol.CallPeer;

/**
 * CallPeerChangeEvent-s are triggered whenever a change occurs in a CallPeer.
 * Dispatched events may be of one of the following types.
 *
 * a. CALL_PEER_STATUS_CHANGE - indicate a change in the status of the peer.
 * b. CALL_PEER_DISPLAY_NAME_CHANGE - mean peer displayName has changed
 * c. CALL_PEER_ADDRESS_CHANGE - mean peer address has changed.
 * d. CALL_PEER_TRANSPORT_ADDRESS_CHANGE - mean peer transport address (the one use for communicate) has changed.
 * e. CALL_PEER_IMAGE_CHANGE - peer updated photo.
 *
 * @author Emil Ivov
 * @author Eng Chong Meng
 */
public class CallPeerChangeEvent extends java.beans.PropertyChangeEvent
{
    /**
     * Serial version UID.
     */
    private static final long serialVersionUID = 0L;

    /**
     * An event type indicating that the corresponding event is caused by a change of the CallPeer status.
     */
    public static final String CALL_PEER_STATE_CHANGE = "CallPeerStatusChange";

    /**
     * An event type indicating that the corresponding event is caused by a change of the peer display name.
     */
    public static final String CALL_PEER_DISPLAY_NAME_CHANGE = "CallPeerDisplayNameChange";

    /**
     * An event type indicating that the corresponding event is caused by a change of the peer address.
     */
    public static final String CALL_PEER_ADDRESS_CHANGE = "CallPeerAddressChange";

    /**
     * An event type indicating that the corresponding event is caused by a change of the peer transport address.
     */
    public static final String CALL_PEER_TRANSPORT_ADDRESS_CHANGE = "CallPeerAddressChange";

    /**
     * An event type indicating that the corresponding event is caused by a change of the peer photo/picture.
     */
    public static final String CALL_PEER_IMAGE_CHANGE = "CallPeerImageChange";

    /**
     * A reason string further explaining the event (may be null). The string would be mostly used
     * for events issued upon a CallPeerState transition that has led to a FAILED state.
     */
    private final String reason;

    /**
     * Reason code, if any, for the peer state change.
     */
    private final int reasonCode;

    /**
     * Code indicating normal call clear.
     */
    public static final int NORMAL_CALL_CLEARING = 200;

    /**
     * Creates a CallPeerChangeEvent with the specified source, type, oldValue and newValue.
     *
     * @param source the peer that produced the event.
     * @param type the type of the event (i.e. address change, state change etc.).
     * @param oldValue of the changed property before the event occurred
     * @param newValue current value of the changed property.
     */
    public CallPeerChangeEvent(CallPeer source, String type, Object oldValue, Object newValue)
    {
        this(source, type, oldValue, newValue, null);
    }

    /**
     * Creates a CallPeerChangeEvent with the specified source, type, oldValue and newValue.
     *
     * @param source the peer that produced the event.
     * @param type the type of the event (i.e. address change, state change etc.).
     * @param oldValue the value of the changed property before the event occurred
     * @param newValue current value of the changed property.
     * @param reason a string containing a human readable reason that triggered this event (may be null).
     */
    public CallPeerChangeEvent(CallPeer source, String type, Object oldValue, Object newValue, String reason)
    {
        this(source, type, oldValue, newValue, reason, -1);
    }

    /**
     * Creates a CallPeerChangeEvent with the specified source, type, oldValue and newValue.
     *
     * @param source the peer that produced the event.
     * @param type the type of the event (i.e. address change, state change etc.).
     * @param oldValue the value of the changed property before the event occurred
     * @param newValue current value of the changed property.
     * @param reason a string containing a human readable reason that triggered this event (may be null).
     * @param reasonCode a code for the reason that triggered this event (may be -1 as not specified).
     */
    public CallPeerChangeEvent(CallPeer source, String type, Object oldValue, Object newValue, String reason, int reasonCode)
    {
        super(source, type, oldValue, newValue);
        this.reason = reason;
        this.reasonCode = reasonCode;
    }

    /**
     * Returns the type of this event.
     *
     * @return a string containing one of the following values: CALL_PEER_STATUS_CHANGE,
     * CALL_PEER_DISPLAY_NAME_CHANGE, CALL_PEER_ADDRESS_CHANGE, CALL_PEER_IMAGE_CHANGE.
     */
    public String getEventType()
    {
        return getPropertyName();
    }

    /**
     * Returns a String representation of this CallPeerChangeEvent.
     *
     * @return A a String representation of this CallPeerChangeEvent.
     */
    @Override
    public String toString()
    {
        return "CallPeerChangeEvent: type=" + getEventType() + " oldV=" + getOldValue() + " newV="
                + getNewValue() + " for peer=" + getSourceCallPeer();
    }

    /**
     * Returns the <tt>CallPeer</tt> that this event is about.
     *
     * @return a reference to the <tt>CallPeer</tt> that is the source of this event.
     */
    public CallPeer getSourceCallPeer()
    {
        return (CallPeer) getSource();
    }

    /**
     * Returns a reason string further explaining the event (may be null). The string would be
     * mostly used for events issued upon a CallPeerState transition that has led to a FAILED state.
     *
     * @return a reason string further explaining the event or null if no reason was set.
     */
    public String getReasonString()
    {
        return reason;
    }

    /**
     * Returns a reason code for the event (may be -1).
     *
     * @return a reason code for the event or -1 if no reason code was set.
     */
    public int getReasonCode()
    {
        return reasonCode;
    }
}
