/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package net.java.sip.communicator.service.protocol;

import org.atalk.android.R;
import org.atalk.android.aTalkApp;

/**
 * The CallPeerState class reflects the current state of a call peer. In other words when you start
 * calling your grand mother she will be in a INITIATING_CALL state, when her phone rings her state
 * will change to ALERTING_REMOTE_SIDE, and when she replies she will enter a CONNECTED state.
 *
 * Though not mandatory CallPeerState would generally have one of the following life cycles
 * In the case with your grand mother that we just described we have:
 * INITIATING_CALL -> CONNECTING -> ALERTING_REMOTE_USER -> CONNECTED -> DISCONNECTED
 *
 * If your granny was already on the phone we have:
 * INITIATING_CALL -> CONNECTING -> BUSY -> DISCONNECTED
 *
 * Whenever someone tries to reach you:
 * INCOMING_CALL -> CONNECTED -> DISCONNECTED
 *
 * A FAILED state is prone to appear at any place in the above diagram and is generally followed by
 * a disconnected state.
 *
 * Information on call peer state is shown in the phone user interface until they enter the DISCONNECTED state.
 * At that point call peer information is automatically removed from the user interface,
 * and the call is considered terminated.
 *
 * @author Emil Ivov
 * @author Lubomir Marinov
 * @author Yana Stamcheva
 * @author Eng Chong Meng
 */
public class CallPeerState
{
    /**
     * This constant value indicates a String representation of the UNKNOWN call state.
     */
    public static final String _UNKNOWN = "Unknown";

    /**
     * This constant value indicates that the state of the call peer is UNKNOWN - which means
     * that there is no information on the state for the time being (this constant should be used as
     * a default value for newly created call peer that don't yet have an attributed call state.
     */
    public static final CallPeerState UNKNOWN
            = new CallPeerState(_UNKNOWN, aTalkApp.getResString(R.string.unknown_state));

    /**
     * This constant value indicates a String representation of the INITIATING_CALL state.
     */
    public static final String _INITIATING_CALL = "Initiating Call";

    /**
     * This constant value indicates that the state of the call peer is INITIATING_CALL - which
     * means that we're currently trying to open a socket and send our request. In the case of SIP
     * for example we will leave this state the moment we receive a "100 Trying" request from a
     * proxy or the remote side.
     */
    public static final CallPeerState INITIATING_CALL = new CallPeerState(_INITIATING_CALL,
            aTalkApp.getResString(R.string.call_initiating));

    /**
     * This constant value indicates a String representation of the CONNECTING call state.
     */
    public static final String _CONNECTING = "Connecting";

    /**
     * This constant value indicates that the state of the call peer is CONNECTING - which means
     * that a network connection to that peer is currently being established.
     */
    public static final CallPeerState CONNECTING = new CallPeerState(_CONNECTING,
            aTalkApp.getResString(R.string.connecting));

    /**
     * This constant value indicates a String representation of the CONNECTING call state
     * but in cases where early media is being exchanged.
     */
    public static final String _CONNECTING_WITH_EARLY_MEDIA = "Connecting*";

    /**
     * This constant value indicates that the state of the call peer is CONNECTING - which means
     * that a network connection to that peer is currently being established.
     */
    public static final CallPeerState CONNECTING_WITH_EARLY_MEDIA = new CallPeerState(
            _CONNECTING_WITH_EARLY_MEDIA, aTalkApp.getResString(R.string.connecting_status));

    /**
     * This constant value indicates that the state of the incoming call peer is CONNECTING - which
     * means that a network connection to that peer is currently being established.
     */
    public static final CallPeerState CONNECTING_INCOMING_CALL = new CallPeerState(
            _CONNECTING_WITH_EARLY_MEDIA, aTalkApp.getResString(R.string.connecting));

    /**
     * This constant value indicates that the state of the incoming call peer is CONNECTING - which
     * means that a network connection to that peer is currently being established and during the
     * process before hearing the other peer we can still can hear media coming from the server for example.
     */
    public static final CallPeerState CONNECTING_INCOMING_CALL_WITH_MEDIA = new CallPeerState(
            _CONNECTING_WITH_EARLY_MEDIA, aTalkApp.getResString(R.string.connecting_status));

    /**
     * This constant value indicates a String representation of the ALERTING_REMOTE_SIDE call state.
     */
    public static final String _ALERTING_REMOTE_SIDE = "Alerting Remote User (Ringing)";

    /**
     * This constant value indicates that the state of the call peer is ALERTING_REMOTE_SIDE -
     * which means that a network connection to that peer has been established and peer's phone is
     * currently alerting the remote user of the current call.
     */
    public static final CallPeerState ALERTING_REMOTE_SIDE = new CallPeerState(
            _ALERTING_REMOTE_SIDE, aTalkApp.getResString(R.string.ringing));

    /**
     * This constant value indicates a String representation of the INCOMING_CALL state.
     */
    public static final String _INCOMING_CALL = "Incoming Call";

    /**
     * This constant value indicates that the state of the call peer is INCOMING_CALL - which
     * means that the peer is willing to start a call with us. At that point local side should be
     * playing a sound or a graphical alert (the phone is ringing).
     */
    public static final CallPeerState INCOMING_CALL = new CallPeerState(_INCOMING_CALL,
            aTalkApp.getResString(R.string.call_incoming_status));

    /**
     * This constant value indicates a String representation of the CONNECTED call state.
     */
    public static final String _CONNECTED = "Connected";

    /**
     * This constant value indicates that the state of the call peer is CONNECTED - which means
     * that there is an ongoing call with that peer.
     */
    public static final CallPeerState CONNECTED = new CallPeerState(_CONNECTED,
            aTalkApp.getResString(R.string.connected));

    /**
     * This constant value indicates a String representation of the DISCONNECTED call state.
     */
    public static final String _DISCONNECTED = "Disconnected";

    /**
     * This constant value indicates that the state of the call peer is DISCONNECTED - which
     * means that this peer is not participating :) in the call anymore.
     */
    public static final CallPeerState DISCONNECTED = new CallPeerState(_DISCONNECTED,
            aTalkApp.getResString(R.string.disconnected));

    /**
     * This constant value indicates a String representation of the REFERRED call state.
     */
    public static final String _REFERRED = "Referred";

    /**
     * This constant value indicates that the state of the call peer is REFERRED - which means
     * that this peer has transferred us to another peer.
     */
    public static final CallPeerState REFERRED = new CallPeerState(_REFERRED,
            aTalkApp.getResString(R.string.referred));

    /**
     * This constant value indicates a String representation of the BUSY call state.
     */
    public static final String _BUSY = "Busy";

    /**
     * This constant value indicates that the state of the call peer is BUSY - which means that
     * an attempt to establish a call with that peer has been made and that it has been turned down
     * by them (e.g. because they were already in a call).
     */
    public static final CallPeerState BUSY = new CallPeerState(_BUSY,
            aTalkApp.getResString(R.string.busy));

    /**
     * This constant value indicates a String representation of the FAILED call state.
     */
    public static final String _FAILED = "Failed";
    /**
     * This constant value indicates that the state of the call peer is ON_HOLD - which means
     * that an attempt to establish a call with that peer has failed for an unexpected reason.
     */
    public static final CallPeerState FAILED = new CallPeerState(_FAILED,
            aTalkApp.getResString(R.string.call_failed));

    /**
     * The constant value being a String representation of the ON_HOLD_LOCALLY call peer state.
     */
    public static final String _ON_HOLD_LOCALLY = "Locally On Hold";
    /**
     * The constant value indicating that the state of a call peer is locally put on hold.
     */
    public static final CallPeerState ON_HOLD_LOCALLY = new CallPeerState(_ON_HOLD_LOCALLY,
            aTalkApp.getResString(R.string.locally_on_hold));

    /**
     * The constant value being a String representation of the ON_HOLD_MUTUALLY call peer state.
     */
    public static final String _ON_HOLD_MUTUALLY = "Mutually On Hold";
    /**
     * The constant value indicating that the state of a call peer is mutually - locally and
     * remotely - put on hold.
     */
    public static final CallPeerState ON_HOLD_MUTUALLY = new CallPeerState(_ON_HOLD_MUTUALLY,
            aTalkApp.getResString(R.string.mutually_on_hole));

    /**
     * The constant value being a String representation of the ON_HOLD_REMOTELY call peer state.
     */
    public static final String _ON_HOLD_REMOTELY = "Remotely On Hold";

    /**
     * The constant value indicating that the state of a call peer is remotely put on hold.
     */
    public static final CallPeerState ON_HOLD_REMOTELY = new CallPeerState(_ON_HOLD_REMOTELY,
            aTalkApp.getResString(R.string.remotely_on_hold));

    /**
     * Determines whether a specific <code>CallPeerState</code> value signal a call hold regardless of
     * the issuer (which may be local and/or remote).
     *
     * @param state the <code>CallPeerState</code> value to be checked whether it signals a call hold
     * @return <code>true</code> if the specified <code>state</code> signals a call hold; <code>false</code>, otherwise
     */
    public static boolean isOnHold(CallPeerState state)
    {
        return CallPeerState.ON_HOLD_LOCALLY.equals(state)
                || CallPeerState.ON_HOLD_MUTUALLY.equals(state)
                || CallPeerState.ON_HOLD_REMOTELY.equals(state);
    }

    /**
     * A string representation of this peer's Call State. Could be _CONNECTED, _FAILED, _CALLING and etc.
     */
    private String callStateStr;

    /**
     * A localized string representation of this peer's Call State.
     */
    private String callStateLocalizedStr;

    /**
     * Create a peer call state object with a value corresponding to the specified string.
     *
     * @param callPeerState a string representation of the state.
     * @param callStateLocalizedStr the localized string representing this state
     */
    private CallPeerState(String callPeerState, String callStateLocalizedStr)
    {
        this.callStateStr = callPeerState;
        this.callStateLocalizedStr = callStateLocalizedStr;
    }

    /**
     * Returns a String representation of the CallPeerState.
     *
     * @return A string value (one of the _BUSY, _CALLING, _CONNECTED, _CONNECTING, _DISCONNECTED,
     * _FAILED, _RINGING constants) representing this call peer state).
     */
    public String getStateString()
    {
        return callStateStr;
    }

    /**
     * Returns a localized String representation of the CallPeerState.
     *
     * @return a localized String representation of the CallPeerState
     */
    public String getLocalizedStateString()
    {
        return callStateLocalizedStr;
    }

    /**
     * Returns a string representation of this call state. Strings returned by this method have the
     * following format: CallPeerState:<STATE_STRING> and are meant to be used for logging/debugging
     * purposes.
     *
     * @return a string representation of this object.
     */
    @Override
    public String toString()
    {
        return getClass().getName() + ":" + getStateString();
    }
}
