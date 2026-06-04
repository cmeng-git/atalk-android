/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package net.java.sip.communicator.service.protocol;

import java.net.URL;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;

import net.java.sip.communicator.service.protocol.event.CallPeerChangeEvent;
import net.java.sip.communicator.service.protocol.event.CallPeerListener;
import net.java.sip.communicator.service.protocol.event.CallPeerSecurityListener;
import net.java.sip.communicator.service.protocol.event.CallPeerSecurityMessageEvent;
import net.java.sip.communicator.service.protocol.event.CallPeerSecurityNegotiationStartedEvent;
import net.java.sip.communicator.service.protocol.event.CallPeerSecurityOffEvent;
import net.java.sip.communicator.service.protocol.event.CallPeerSecurityOnEvent;
import net.java.sip.communicator.service.protocol.event.CallPeerSecurityStatusEvent;
import net.java.sip.communicator.service.protocol.event.CallPeerSecurityTimeoutEvent;

import org.atalk.util.event.PropertyChangeNotifier;

import timber.log.Timber;

/**
 * Provides a default implementation for most of the <code>CallPeer</code> methods with the purpose of
 * only leaving custom protocol development to clients using the PhoneUI service.
 *
 * @param <T> the call extension class like <code>CallJabberImpl</code>
 * @param <U> the provider extension class like for <code>ProtocolProviderServiceJabberImpl</code>
 *
 * @author Emil Ivov
 * @author Lyubomir Marinov
 * @author Yana Stamcheva
 * @author Eng Chong Meng
 */
public abstract class AbstractCallPeer<T extends Call, U extends ProtocolProviderService>
        extends PropertyChangeNotifier implements CallPeer {
    /**
     * The time this call started at.
     */
    private long callDurationStartTime = CALL_DURATION_START_TIME_UNKNOWN;

    /**
     * All the CallPeer listeners registered with this CallPeer.
     */
    protected final List<CallPeerListener> callPeerListeners = new ArrayList<>();

    /**
     * All the CallPeerSecurityListener-s registered with this CallPeer.
     */
    protected final List<CallPeerSecurityListener> callPeerSecurityListeners = new ArrayList<>();

    /**
     * The flag that determines whether our audio stream to this call peer is currently muted.
     */
    private boolean isMute = false;

    /**
     * The last fired security event.
     */
    private CallPeerSecurityStatusEvent lastSecurityEvent;

    /**
     * The state of the call peer.
     */
    private CallPeerState state = CallPeerState.UNKNOWN;

    /**
     *
     */
    private String alternativeIMPPAddress;

    /**
     * Returns an alternative IMPP address corresponding to this <code>CallPeer</code>.
     *
     * @return a string representing an alternative IMPP address corresponding to this <code>CallPeer</code>
     */
    public String getAlternativeIMPPAddress() {
        return alternativeIMPPAddress;
    }

    /**
     * Returns an alternative IMPP address corresponding to this <code>CallPeer</code>.
     *
     * @param address an alternative IMPP address corresponding to this <code>CallPeer</code>
     */
    public void setAlternativeIMPPAddress(String address) {
        alternativeIMPPAddress = address;
    }

    /**
     * Registers the <code>listener</code> to the list of listeners that would be receiving CallPeerEvents.
     *
     * @param listener a listener instance to register with this peer.
     */
    public void addCallPeerListener(CallPeerListener listener) {
        if (listener == null)
            return;
        synchronized (callPeerListeners) {
            if (!callPeerListeners.contains(listener))
                callPeerListeners.add(listener);
        }
    }

    /**
     * Registers the <code>listener</code> to the list of listeners that would be receiving CallPeerSecurityEvents.
     *
     * @param listener a listener instance to register with this peer.
     */
    public void addCallPeerSecurityListener(CallPeerSecurityListener listener) {
        if (listener == null)
            return;
        synchronized (callPeerSecurityListeners) {
            if (!callPeerSecurityListeners.contains(listener))
                callPeerSecurityListeners.add(listener);
        }
    }

    /**
     * Constructs a <code>CallPeerChangeEvent</code> using this call peer as source, setting it to be of
     * type <code>eventType</code> and the corresponding <code>oldValue</code> and <code>newValue</code>,
     *
     * @param eventType the type of the event to create and dispatch.
     * @param oldValue the value of the source property before it changed.
     * @param newValue the current value of the source property.
     */
    protected void fireCallPeerChangeEvent(String eventType, Object oldValue, Object newValue) {
        this.fireCallPeerChangeEvent(eventType, oldValue, newValue, null);
    }

    /**
     * Constructs a <code>CallPeerChangeEvent</code> using this call peer as source, setting it to be of
     * type <code>eventType</code> and the corresponding <code>oldValue</code> and <code>newValue</code>.
     *
     * @param eventType the type of the event to create and dispatch.
     * @param oldValue the value of the source property before it changed.
     * @param newValue the current value of the source property.
     * @param reason a string that could be set to contain a human readable explanation for the transition
     * (particularly handy when moving into a FAILED state).
     */
    protected void fireCallPeerChangeEvent(String eventType, Object oldValue, Object newValue, String reason) {
        this.fireCallPeerChangeEvent(eventType, oldValue, newValue, reason, -1);
    }

    /**
     * Constructs a <code>CallPeerChangeEvent</code> using this call peer as source, setting it to be of
     * type <code>eventType</code> and the corresponding <code>oldValue</code> and <code>newValue</code>.
     *
     * @param eventType the type of the event to create and dispatch.
     * @param oldValue the value of the source property before it changed.
     * @param newValue the current value of the source property.
     * @param reason a string that could be set to contain a human readable explanation for the transition
     * (particularly handy when moving into a FAILED state).
     * @param reasonCode the reason code for the reason of this event.
     */
    protected void fireCallPeerChangeEvent(String eventType, Object oldValue, Object newValue, String reason, int reasonCode) {
        CallPeerChangeEvent evt = new CallPeerChangeEvent(this, eventType, oldValue, newValue, reason, reasonCode);

        Iterator<CallPeerListener> listeners;
        synchronized (callPeerListeners) {
            listeners = new ArrayList<>(callPeerListeners).iterator();
        }

        Timber.d("Dispatching CallPeerChangeEvent; %s;\n%s Listeners: %s;", evt, callPeerListeners.size(), callPeerListeners);
        while (listeners.hasNext()) {
            CallPeerListener listener = listeners.next();
            // catch any possible errors, so we are sure we dispatch events to all listeners
            try {
                switch (eventType) {
                case CallPeerChangeEvent.CALL_PEER_ADDRESS_CHANGE:
                    listener.peerAddressChanged(evt);
                    break;
                case CallPeerChangeEvent.CALL_PEER_DISPLAY_NAME_CHANGE:
                    listener.peerDisplayNameChanged(evt);
                    break;
                case CallPeerChangeEvent.CALL_PEER_IMAGE_CHANGE:
                    listener.peerImageChanged(evt);
                    break;
                case CallPeerChangeEvent.CALL_PEER_STATE_CHANGE:
                    // Timber.d("Dispatching CallPeerChangeEvent CALL_PEER_STATE_CHANGE to: %s", listener);
                    listener.peerStateChanged(evt);
                    break;
                }
            }
            catch (Throwable t) {
                Timber.e(t, "Error dispatching event to %s: %s", listener, eventType);
            }
        }
    }

    /**
     * Constructs a <code>CallPeerSecurityStatusEvent</code> using this call peer as source, setting it
     * to be of type <code>eventType</code> and the corresponding <code>oldValue</code> and <code>newValue</code>.
     *
     * @param messageType the type of the message
     * @param i18nMessage message
     * @param severity severity level
     */
    protected void fireCallPeerSecurityMessageEvent(String messageType, String i18nMessage, int severity) {
        CallPeerSecurityMessageEvent evt = new CallPeerSecurityMessageEvent(this, messageType, i18nMessage, severity);

        Timber.d("Dispatching CallPeer Security Message Events to %s listeners:\n%s",
                callPeerSecurityListeners.size(), evt.toString());

        Iterator<CallPeerSecurityListener> listeners;
        synchronized (callPeerSecurityListeners) {
            listeners = new ArrayList<>(callPeerSecurityListeners).iterator();
        }

        while (listeners.hasNext()) {
            CallPeerSecurityListener listener = listeners.next();
            listener.securityMessageReceived(evt);
        }
    }

    /**
     * Constructs a <code>CallPeerSecurityStatusEvent</code> using this call peer as source, setting it
     * to be of type <code>eventType</code> and the corresponding <code>oldValue</code> and <code>newValue</code>.
     *
     * @param evt the event object with details to pass on to the consumers
     */
    protected void fireCallPeerSecurityNegotiationStartedEvent(CallPeerSecurityNegotiationStartedEvent evt) {
        lastSecurityEvent = evt;

        Timber.d("Dispatching CallPeerSecurityStatusEvent Started to %s listeners: %s",
                callPeerSecurityListeners.size(), evt.toString());

        List<CallPeerSecurityListener> listeners;
        synchronized (callPeerSecurityListeners) {
            listeners = new ArrayList<>(callPeerSecurityListeners);
        }
        for (CallPeerSecurityListener listener : listeners) {
            listener.securityNegotiationStarted(evt);
        }
    }

    /**
     * Constructs a <code>CallPeerSecurityStatusEvent</code> using this call peer as source, setting it
     * to be of type <code>eventType</code> and the corresponding <code>oldValue</code> and <code>newValue</code>.
     *
     * @param evt the event object with details to pass on to the consumers
     */
    protected void fireCallPeerSecurityOffEvent(CallPeerSecurityOffEvent evt) {
        lastSecurityEvent = evt;
        Timber.d("Dispatching CallPeerSecurityAuthenticationEvent OFF to %s listeners: %s",
                callPeerSecurityListeners.size(), evt.toString());

        List<CallPeerSecurityListener> listeners;
        synchronized (callPeerSecurityListeners) {
            listeners = new ArrayList<>(callPeerSecurityListeners);
        }

        for (CallPeerSecurityListener listener : listeners) {
            listener.securityOff(evt);
        }
    }

    /**
     * Constructs a <code>CallPeerSecurityStatusEvent</code> using this call peer as source, setting it
     * to be of type <code>eventType</code> and the corresponding <code>oldValue</code> and <code>newValue</code>.
     *
     * @param evt the event object with details to pass on to the consumers
     */
    protected void fireCallPeerSecurityOnEvent(CallPeerSecurityOnEvent evt) {
        lastSecurityEvent = evt;
        Timber.d("Dispatching CallPeerSecurityStatusEvent ON to %s listeners: %s",
                callPeerSecurityListeners.size(), evt.toString());

        List<CallPeerSecurityListener> listeners;
        synchronized (callPeerSecurityListeners) {
            listeners = new ArrayList<>(callPeerSecurityListeners);
        }

        for (CallPeerSecurityListener listener : listeners) {
            listener.securityOn(evt);
        }
    }

    /**
     * Constructs a <code>CallPeerSecurityStatusEvent</code> using this call peer as source, setting it
     * to be of type <code>eventType</code> and the corresponding <code>oldValue</code> and
     * <code>newValue</code>.
     *
     * @param evt the event object with details to pass on to the consumers
     */
    protected void fireCallPeerSecurityTimeoutEvent(CallPeerSecurityTimeoutEvent evt) {
        lastSecurityEvent = evt;

        Timber.d("Dispatching CallPeerSecurityStatusEvent Timeout to %s listeners: %s",
                callPeerSecurityListeners.size(), evt.toString());

        List<CallPeerSecurityListener> listeners;
        synchronized (callPeerSecurityListeners) {
            listeners = new ArrayList<>(callPeerSecurityListeners);
        }

        for (CallPeerSecurityListener listener : listeners) {
            listener.securityTimeout(evt);
        }
    }

    /**
     * Returns a reference to the call that this peer belongs to.
     *
     * @return a reference to the call containing this peer.
     */
    public abstract T getCall();

    /**
     * Gets the time at which this <code>CallPeer</code> transitioned into a state (likely
     * {@link CallPeerState#CONNECTED}) marking the start of the duration of the participation in a <code>Call</code>.
     *
     * @return the time at which this <code>CallPeer</code> transitioned into a state marking the start
     * of the duration of the participation in a <code>Call</code> or
     * {@link CallPeer#CALL_DURATION_START_TIME_UNKNOWN} if such a transition has not been performed
     */
    public long getCallDurationStartTime() {
        return callDurationStartTime;
    }

    /**
     * Returns a URL pointing ta a location with call control information for this peer or
     * <code>null</code> if no such URL is available for this call peer.
     *
     * @return a URL link to a location with call information or a call control web interface
     * related to this peer or <code>null</code> if no such URL is available.
     */
    public URL getCallInfoURL() {
        // if signaling protocols (such as SIP) know where to get this URL from they should override this method
        return null;
    }

    /**
     * Returns the currently used security settings of this <code>CallPeer</code>.
     *
     * @return the <code>CallPeerSecurityStatusEvent</code> that contains the current security settings.
     */
    public CallPeerSecurityStatusEvent getCurrentSecuritySettings() {
        return lastSecurityEvent;
    }

    /**
     * Returns the protocol provider that this peer belongs to.
     *
     * @return a reference to the ProtocolProviderService that this peer belongs to.
     */
    public abstract U getProtocolProvider();

    /**
     * Returns an object representing the current state of that peer.
     *
     * @return a CallPeerState instance representing the peer's state.
     */
    public CallPeerState getState() {
        return state;
    }

    /**
     * Determines whether the audio stream (if any) being sent to this peer is mute.
     * <p>
     * The default implementation returns <code>false</code>.
     * </p>
     *
     * @return <code>true</code> if an audio stream is being sent to this peer and it is currently mute;
     * <code>false</code>, otherwise
     */
    public boolean isMute() {
        return isMute;
    }

    /**
     * Unregisters the specified listener.
     *
     * @param listener the listener to unregister.
     */
    public void removeCallPeerListener(CallPeerListener listener) {
        if (listener == null)
            return;
        synchronized (callPeerListeners) {
            callPeerListeners.remove(listener);
        }
    }

    /**
     * Unregisters the specified listener.
     *
     * @param listener the listener to unregister.
     */
    public void removeCallPeerSecurityListener(CallPeerSecurityListener listener) {
        if (listener == null)
            return;
        synchronized (callPeerSecurityListeners) {
            callPeerSecurityListeners.remove(listener);
        }
    }

    /**
     * Sets the mute property for this call peer.
     *
     * @param newMuteValue the new value of the mute property for this call peer
     */
    public void setMute(boolean newMuteValue) {
        this.isMute = newMuteValue;
        firePropertyChange(MUTE_PROPERTY_NAME, isMute, newMuteValue);
    }

    /**
     * Causes this CallPeer to enter the specified state. The method also sets the
     * currentStateStartDate field and fires a CallPeerChangeEvent.
     *
     * @param newState the state this call peer should enter.
     */
    public void setState(CallPeerState newState) {
        setState(newState, null);
    }

    /**
     * Causes this CallPeer to enter the specified state. The method also sets the
     * currentStateStartDate field and fires a CallPeerChangeEvent.
     *
     * @param newState the state this call peer should enter.
     * @param reason a string that could be set to contain a human readable explanation for the transition
     * (particularly handy when moving into a FAILED state).
     */
    public void setState(CallPeerState newState, String reason) {
        setState(newState, reason, -1);
    }

    /**
     * Causes this CallPeer to enter the specified state. The method also sets the
     * currentStateStartDate field and fires a CallPeerChangeEvent.
     *
     * @param newState the state this call peer should enter.
     * @param reason a string that could be set to contain a human readable explanation for the transition
     * (particularly handy when moving into a FAILED state).
     * @param reasonCode the code for the reason of the state change.
     */
    public void setState(CallPeerState newState, String reason, int reasonCode) {
        CallPeerState oldState = getState();
        if (oldState == newState)
            return;

        this.state = newState;
        if (CallPeerState.CONNECTED.equals(newState) && !CallPeerState.isOnHold(oldState)) {
            callDurationStartTime = System.currentTimeMillis();
        }
        fireCallPeerChangeEvent(CallPeerChangeEvent.CALL_PEER_STATE_CHANGE, oldState, newState, reason, reasonCode);
    }

    /**
     * Returns a string representation of the peer in the form of <br/>
     * Display Name &lt;address&gt;;status=CallPeerStatus
     *
     * @return a string representation of the peer and its state.
     */
    @Override
    public String toString() {
        return getDisplayName() + " <" + getAddress() + ">; status=" + getState().getStateString();
    }
}
