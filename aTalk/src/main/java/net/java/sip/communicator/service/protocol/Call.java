/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package net.java.sip.communicator.service.protocol;

import android.text.TextUtils;

import java.beans.PropertyChangeListener;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Vector;

import net.java.sip.communicator.service.protocol.event.CallChangeEvent;
import net.java.sip.communicator.service.protocol.event.CallChangeListener;
import net.java.sip.communicator.service.protocol.event.CallPeerChangeEvent;
import net.java.sip.communicator.service.protocol.event.CallPeerEvent;
import net.java.sip.communicator.service.protocol.event.SoundLevelListener;
import net.java.sip.communicator.util.DataObject;

import org.jetbrains.annotations.NotNull;
import org.jivesoftware.smackx.jingle.JingleManager;

import timber.log.Timber;

/**
 * A representation of a call. <code>Call</code> instances must only be created by users (i.e. telephony
 * protocols) of the PhoneUIService such as a SIP protocol implementation. Extensions of this class
 * might have names like <code>CallSipImpl</code>, <code>CallJabberImpl</code>, or
 * <code>CallAnyOtherTelephonyProtocolImpl</code>. Call is DataObject, this way it will be able to store
 * custom data and carry it to various parts of the project.
 *
 * @author Emil Ivov
 * @author Emanuel Onica
 * @author Lyubomir Marinov
 * @author Boris Grozev
 * @author Eng Chong Meng
 */
public abstract class Call extends DataObject {
    /**
     * The name of the <code>Call</code> property which represents its telephony conference-related state.
     */
    public static final String CONFERENCE = "conference";

    /**
     * The name of the <code>Call</code> property which indicates whether the local peer/user
     * represented by the respective <code>Call</code> is acting as a conference focus.
     */
    public static final String CONFERENCE_FOCUS = "conferenceFocus";

    /**
     * An identifier uniquely representing the call; set to same as Jingle Sid if available.
     */
    private final String callId;

    /**
     * A list of all listeners currently registered for <code>CallChangeEvent</code>s
     */
    private final List<CallChangeListener> callListeners = new Vector<>();

    /**
     * A reference to the ProtocolProviderService instance that created us.
     */
    private final ProtocolProviderService protocolProvider;

    /**
     * If this flag is set to true according to the account properties related with the
     * sourceProvider the associated CallSession will start encrypted by default (where applicable)
     */
    private final boolean defaultEncryption;

    /**
     * If this flag is set to true according to the account properties related with the
     * sourceProvider the associated CallSession will set the SIP/SDP attribute (where applicable)
     */
    private final boolean sipZrtpAttribute;

    /**
     * The state that this call is currently in.
     */
    private CallState callState = CallState.CALL_INITIALIZATION;

    /**
     * The telephony conference-related state of this <code>Call</code>. Since a non-conference
     * <code>Call</code> may be converted into a conference <code>Call</code> at any time, every
     * <code>Call</code> instance maintains a <code>CallConference</code> instance regardless of whether
     * the <code>Call</code> in question is participating in a telephony conference.
     */
    private CallConference conference;

    /**
     * The flag that specifies whether incoming calls into this <code>Call</code> should be auto-answered.
     */
    private boolean isAutoAnswer = false;

    /**
     * The indicator which determines whether any telephony conference represented by this instance
     * is mixing or relaying. By default what can be mixed is mixed (audio) and rest is relayed.
     */
    protected final boolean useTranslator;

    /**
     * Creates a new Call instance.
     *
     * @param sourceProvider the proto provider that created us.
     * @param sid the Jingle session-initiate id if provided.
     */
    protected Call(ProtocolProviderService sourceProvider, String sid) {
        // create the uid
        if (TextUtils.isEmpty(sid)) {
            callId = JingleManager.randomId();
        }
        else {
            callId = sid;
        }

        this.protocolProvider = sourceProvider;
        AccountID accountID = protocolProvider.getAccountID();

        defaultEncryption = accountID.getAccountPropertyBoolean(
                ProtocolProviderFactory.DEFAULT_ENCRYPTION, true);
        sipZrtpAttribute = accountID.getAccountPropertyBoolean(
                ProtocolProviderFactory.DEFAULT_SIPZRTP_ATTRIBUTE, true);
        useTranslator = accountID.getAccountPropertyBoolean(
                ProtocolProviderFactory.USE_TRANSLATOR_IN_CONFERENCE, false);
    }

    /**
     * Returns the id of the specified Call.
     *
     * @return a String uniquely identifying the call.
     */
    public String getCallId() {
        return callId;
    }

    /**
     * Compares the specified object with this call and returns true if it the specified object is
     * an instance of a Call object and if the extending telephony protocol considers the calls
     * represented by both objects to be the same.
     *
     * @param obj the call to compare this one with.
     *
     * @return true in case both objects are pertaining to the same call and false otherwise.
     */
    @Override
    public boolean equals(Object obj) {
        if (!(obj instanceof Call))
            return false;
        return (obj == this) || ((Call) obj).getCallId().equals(getCallId());
    }

    /**
     * Returns a hash code value for this call.
     *
     * @return a hash code value for this call.
     */
    @Override
    public int hashCode() {
        return getCallId().hashCode();
    }

    /**
     * Adds a call change listener to this call so that it could receive events on new call peers,
     * theme changes and others.
     *
     * @param listener the listener to register
     */
    public void addCallChangeListener(CallChangeListener listener) {
        synchronized (callListeners) {
            if (!callListeners.contains(listener)) {
                callListeners.add(listener);
            }
        }
    }

    /**
     * Removes <code>listener</code> to this call so that it won't receive further <code>CallChangeEvent</code>s.
     *
     * @param listener the listener to register
     */
    public void removeCallChangeListener(CallChangeListener listener) {
        synchronized (callListeners) {
            callListeners.remove(listener);
        }
    }

    /**
     * Returns a reference to the <code>ProtocolProviderService</code> instance that created this call.
     *
     * @return a reference to the <code>ProtocolProviderService</code> instance that created this call.
     */
    public ProtocolProviderService getProtocolProvider() {
        return this.protocolProvider;
    }

    /**
     * Creates a <code>CallPeerEvent</code> with <code>sourceCallPeer</code> and <code>eventID</code> and
     * dispatches it on all currently registered listeners.
     *
     * @param sourceCallPeer the source <code>CallPeer</code> for the newly created event.
     * @param eventID the ID of the event to create (see constants defined in <code>CallPeerEvent</code>)
     */
    protected void fireCallPeerEvent(CallPeer sourceCallPeer, int eventID) {
        fireCallPeerEvent(sourceCallPeer, eventID, false);
    }

    /**
     * Creates a <code>CallPeerEvent</code> with <code>sourceCallPeer</code> and <code>eventID</code> and
     * dispatches it on all currently registered listeners.
     *
     * @param sourceCallPeer the source <code>CallPeer</code> for the newly created event.
     * @param eventID the ID of the event to create (see constants defined in <code>CallPeerEvent</code>)
     * @param delayed <code>true</code> if the adding/removing of the peer from the GUI should be delayed and
     * <code>false</code> if not.
     */
    protected void fireCallPeerEvent(CallPeer sourceCallPeer, int eventID, boolean delayed) {
        CallPeerEvent event = new CallPeerEvent(sourceCallPeer, this, eventID, delayed);
        // Timber.d("Dispatching CallPeer event to %s listeners. The event is: %s", callListeners.size(), event);

        Iterator<CallChangeListener> listeners;
        synchronized (callListeners) {
            listeners = new ArrayList<>(callListeners).iterator();
        }

        while (listeners.hasNext()) {
            CallChangeListener listener = listeners.next();

            if (eventID == CallPeerEvent.CALL_PEER_ADDED)
                listener.callPeerAdded(event);
            else if (eventID == CallPeerEvent.CALL_PEER_REMOVED)
                listener.callPeerRemoved(event);
        }

    }

    /**
     * Returns a string textually representing this Call.
     *
     * @return a string representation of the object.
     */
    @NotNull
    @Override
    public String toString() {
        return "Call: id=" + getCallId() + " peers=" + getCallPeerCount();
    }

    /**
     * Creates a <code>CallChangeEvent</code> with this class as <code>sourceCall</code>, and the specified
     * <code>eventID</code> and old and new values and dispatches it on all currently registered
     * listeners.
     *
     * @param type the type of the event to create (see CallChangeEvent member ints)
     * @param oldValue the value of the call property that changed, before the event had occurred.
     * @param newValue the value of the call property that changed, after the event has occurred.
     */
    protected void fireCallChangeEvent(String type, Object oldValue, Object newValue) {
        fireCallChangeEvent(type, oldValue, newValue, null);
    }

    /**
     * Creates a <code>CallChangeEvent</code> with this class as <code>sourceCall</code>, and the specified
     * <code>eventID</code> and old and new values and dispatches it on all currently registered listeners.
     *
     * @param type the type of the event to create (see CallChangeEvent member ints)
     * @param oldValue the value of the call property that changed, before the event had occurred.
     * @param newValue the value of the call property that changed, after the event has occurred.
     * @param cause the event that is the initial cause of the current one.
     */
    protected void fireCallChangeEvent(String type, Object oldValue, Object newValue, CallPeerChangeEvent cause) {
        CallChangeEvent event = new CallChangeEvent(this, type, oldValue, newValue, cause);
        Timber.d("Dispatching CallChangeEvent to (%s) listeners. %s", callListeners.size(), event);

        CallChangeListener[] listeners;
        synchronized (callListeners) {
            listeners = callListeners.toArray(new CallChangeListener[0]);
        }
        for (CallChangeListener listener : listeners)
            listener.callStateChanged(event);
    }

    /**
     * Returns the state that this call is currently in.
     *
     * @return a reference to the <code>CallState</code> instance that the call is currently in.
     */
    public CallState getCallState() {
        return callState;
    }

    /**
     * Sets the state of this call and fires a call change event notifying registered listeners for the change.
     *
     * @param newState a reference to the <code>CallState</code> instance that the call is to enter.
     */
    protected void setCallState(CallState newState) {
        setCallState(newState, null);
    }

    /**
     * Sets the state of this <code>Call</code> and fires a new <code>CallChangeEvent</code> notifying the
     * registered <code>CallChangeListener</code>s about the change of the state.
     *
     * @param newState the <code>CallState</code> into which this <code>Call</code> is to enter
     * @param cause the <code>CallPeerChangeEvent</code> which is the cause for the request to have this
     * <code>Call</code> enter the specified <code>CallState</code>
     */
    protected void setCallState(CallState newState, CallPeerChangeEvent cause) {
        CallState oldState = getCallState();
        if (oldState != newState) {
            callState = newState;

            try {
                fireCallChangeEvent(CallChangeEvent.CALL_STATE_CHANGE, oldState, callState, cause);
            } finally {
                if (CallState.CALL_ENDED.equals(getCallState()))
                    setConference(null);
            }
        }
    }

    /**
     * Returns the default call encryption flag
     *
     * @return the default call encryption flag
     */
    public boolean isDefaultEncrypted() {
        return defaultEncryption;
    }

    /**
     * Check if to include the ZRTP attribute to SIP/SDP
     *
     * @return include the ZRTP attribute to SIP/SDP
     */
    public boolean isSipZrtpAttribute() {
        return sipZrtpAttribute;
    }

    /**
     * Returns an iterator over all call peers.
     *
     * @return an Iterator over all peers currently involved in the call.
     */
    public abstract Iterator<? extends CallPeer> getCallPeers();

    /**
     * Returns the number of peers currently associated with this call.
     *
     * @return an <code>int</code> indicating the number of peers currently associated with this call.
     */
    public abstract int getCallPeerCount();

    /**
     * Gets the indicator which determines whether the local peer represented by this <code>Call</code>
     * is acting as a conference focus. In the case of SIP, for example, it determines whether the
     * local peer should send the &quot;isfocus&quot; parameter in the Contact headers of its
     * outgoing SIP signaling.
     *
     * @return <code>true</code> if the local peer represented by this <code>Call</code> is acting as a
     * conference focus; otherwise, <code>false</code>
     */
    public abstract boolean isConferenceFocus();

    /**
     * Adds a specific <code>SoundLevelListener</code> to the list of listeners interested in and
     * notified about changes in local sound level information.
     *
     * @param l the <code>SoundLevelListener</code> to add
     */
    public abstract void addLocalUserSoundLevelListener(SoundLevelListener l);

    /**
     * Removes a specific <code>SoundLevelListener</code> from the list of listeners interested in and
     * notified about changes in local sound level information.
     *
     * @param l the <code>SoundLevelListener</code> to remove
     */
    public abstract void removeLocalUserSoundLevelListener(SoundLevelListener l);

    /**
     * Creates a new <code>CallConference</code> instance which is to represent the telephony
     * conference-related state of this <code>Call</code>. Allows extenders to override and customize
     * the runtime type of the <code>CallConference</code> to used by this <code>Call</code>.
     *
     * @return a new <code>CallConference</code> instance which is to represent the telephony
     * conference-related state of this <code>Call</code>
     */
    protected CallConference createConference() {
        return new CallConference();
    }

    /**
     * Gets the telephony conference-related state of this <code>Call</code>. Since a non-conference
     * <code>Call</code> may be converted into a conference <code>Call</code> at any time, every
     * <code>Call</code> instance maintains a <code>CallConference</code> instance regardless of whether
     * the <code>Call</code> in question is participating in a telephony conference.
     *
     * @return a <code>CallConference</code> instance which represents the telephony conference-related
     * state of this <code>Call</code>.
     */
    public CallConference getConference() {
        if (conference == null) {
            CallConference newValue = createConference();

            if (newValue == null) {
                /*
                 * Call is documented to always have a telephony conference-related state because
                 * there is an expectation that a 1-to-1 Call can always be turned into a
                 * conference Call.
                 */
                throw new IllegalStateException("conference");
            }
            else {
                setConference(newValue);
            }
        }
        return conference;
    }

    /**
     * Sets the telephony conference-related state of this <code>Call</code>. If the invocation modifies
     * this instance, it adds this <code>Call</code> to the newly set <code>CallConference</code> and fires
     * a <code>PropertyChangeEvent</code> for the <code>CONFERENCE</code> property to its listeners.
     *
     * @param conference the <code>CallConference</code> instance to represent the telephony conference-related
     * state of this <code>Call</code>
     */
    public void setConference(CallConference conference) {
        if (this.conference != conference) {
            CallConference oldValue = this.conference;

            this.conference = conference;

            CallConference newValue = this.conference;

            if (oldValue != null)
                oldValue.removeCall(this);
            if (newValue != null)
                newValue.addCall(this);

            firePropertyChange(CONFERENCE, oldValue, newValue);
        }
    }

    /**
     * Adds a specific <code>PropertyChangeListener</code> to the list of listeners interested in and
     * notified about changes in the values of the properties of this <code>Call</code>.
     *
     * @param listener a <code>PropertyChangeListener</code> to be notified about changes in the values of the
     * properties of this <code>Call</code>. If the specified listener is already in the list of
     * interested listeners (i.e. it has been previously added), it is not added again.
     */
    public abstract void addPropertyChangeListener(PropertyChangeListener listener);

    /**
     * Fires a new <code>PropertyChangeEvent</code> to the <code>PropertyChangeListener</code>s registered
     * with this <code>Call</code> in order to notify about a change in the value of a specific
     * property which had its old value modified to a specific new value.
     *
     * @param property the name of the property of this <code>Call</code> which had its value changed
     * @param oldValue the value of the property with the specified name before the change
     * @param newValue the value of the property with the specified name after the change
     */
    protected abstract void firePropertyChange(String property, Object oldValue, Object newValue);

    /**
     * Removes a specific <code>PropertyChangeListener</code> from the list of listeners interested in
     * and notified about changes in the values of the properties of this <code>Call</code>.
     *
     * @param listener a <code>PropertyChangeListener</code> to no longer be notified about changes in the values
     * of the properties of this <code>Call</code>
     */
    public abstract void removePropertyChangeListener(PropertyChangeListener listener);

    /**
     * Returns <code>true</code> iff incoming calls into this <code>Call</code> should be auto-answered.
     *
     * @return <code>true</code> iff incoming calls into this <code>Call</code> should be auto-answered.
     */
    public boolean isAutoAnswer() {
        return isAutoAnswer;
    }

    /**
     * Sets the flag that specifies whether incoming calls into this <code>Call</code> should be auto-answered.
     *
     * @param autoAnswer whether incoming calls into this <code>Call</code> should be auto-answered.
     */
    public void setAutoAnswer(boolean autoAnswer) {
        isAutoAnswer = autoAnswer;
    }
}
