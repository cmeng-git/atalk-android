/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package net.java.sip.communicator.service.protocol;

import net.java.sip.communicator.service.protocol.event.CallPeerChangeEvent;
import net.java.sip.communicator.service.protocol.event.CallPeerConferenceEvent;
import net.java.sip.communicator.service.protocol.event.CallPeerConferenceListener;
import net.java.sip.communicator.service.protocol.event.CallPeerListener;
import net.java.sip.communicator.service.protocol.event.CallPeerSecurityListener;
import net.java.sip.communicator.service.protocol.event.CallPeerSecurityMessageEvent;
import net.java.sip.communicator.service.protocol.event.CallPeerSecurityNegotiationStartedEvent;
import net.java.sip.communicator.service.protocol.event.CallPeerSecurityOffEvent;
import net.java.sip.communicator.service.protocol.event.CallPeerSecurityOnEvent;
import net.java.sip.communicator.service.protocol.event.CallPeerSecurityStatusEvent;
import net.java.sip.communicator.service.protocol.event.CallPeerSecurityTimeoutEvent;

import org.atalk.util.event.PropertyChangeNotifier;

import java.net.URL;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;

import timber.log.Timber;

/**
 * Provides a default implementation for most of the <code>CallPeer</code> methods with the purpose of
 * only leaving custom protocol development to clients using the PhoneUI service.
 *
 * @param <T> the call extension class like for example <code>CallSipImpl</code> or <code>CallJabberImpl</code>
 * @param <U> the provider extension class like for example <code>ProtocolProviderServiceSipImpl</code> or
 * <code>ProtocolProviderServiceJabberImpl</code>
 * @author Emil Ivov
 * @author Lyubomir Marinov
 * @author Yana Stamcheva
 * @author Eng Chong Meng
 */
public abstract class AbstractCallPeer<T extends Call, U extends ProtocolProviderService>
        extends PropertyChangeNotifier implements CallPeer
{
    /**
     * The constant which describes an empty set of <code>ConferenceMember</code>s (and which can be
     * used to reduce allocations).
     */
    public static final ConferenceMember[] NO_CONFERENCE_MEMBERS = new ConferenceMember[0];

    /**
     * The time this call started at.
     */
    private long callDurationStartTime = CALL_DURATION_START_TIME_UNKNOWN;

    /**
     * The list of <code>CallPeerConferenceListener</code>s interested in and to be notified about
     * changes in conference-related information such as this peer acting or not acting as a
     * conference focus and conference membership details.
     */
    protected final List<CallPeerConferenceListener> callPeerConferenceListeners = new ArrayList<>();

    /**
     * All the CallPeer listeners registered with this CallPeer.
     */
    protected final List<CallPeerListener> callPeerListeners = new ArrayList<>();

    /**
     * All the CallPeerSecurityListener-s registered with this CallPeer.
     */
    protected final List<CallPeerSecurityListener> callPeerSecurityListeners = new ArrayList<>();

    /**
     * The indicator which determines whether this peer is acting as a conference focus and thus may
     * provide information about <code>ConferenceMember</code> such as {@link #getConferenceMembers()}
     * and {@link #getConferenceMemberCount()}.
     */
    private boolean conferenceFocus;

    /**
     * The list of <code>ConferenceMember</code>s currently known to and managed in a conference by this
     * <code>CallPeer</code>. It is implemented as a copy-on-write storage in order to optimize the
     * implementation of {@link #getConferenceMembers()} which is used more often than
     * {@link #addConferenceMember(ConferenceMember)} and
     * {@link #removeConferenceMember(ConferenceMember)}.
     */
    private List<ConferenceMember> conferenceMembers;

    /**
     * The <code>Object</code> which synchronizes the access to {@link #conferenceMembers} and
     * {@link #unmodifiableConferenceMembers}.
     */
    private final Object conferenceMembersSyncRoot = new Object();

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
     * An unmodifiable view of {@link #conferenceMembers}. The list of <code>ConferenceMember</code>s
     * participating in the conference managed by this instance is implemented as a copy-on-write
     * storage in order to optimize the implementation of {@link #getConferenceMembers()} which is
     * used more often than {@link #addConferenceMember(ConferenceMember)} and
     * {@link #removeConferenceMember(ConferenceMember)}.
     */
    private List<ConferenceMember> unmodifiableConferenceMembers;

    /**
     * Initializes a new <code>AbstractCallPeer</code> instance.
     */
    protected AbstractCallPeer()
    {
        conferenceMembers = Collections.emptyList();
        unmodifiableConferenceMembers = Collections.unmodifiableList(conferenceMembers);
    }

    /**
     * Returns an alternative IMPP address corresponding to this <code>CallPeer</code>.
     *
     * @return a string representing an alternative IMPP address corresponding to this <code>CallPeer</code>
     */
    public String getAlternativeIMPPAddress()
    {
        return alternativeIMPPAddress;
    }

    /**
     * Returns an alternative IMPP address corresponding to this <code>CallPeer</code>.
     *
     * @param address an alternative IMPP address corresponding to this <code>CallPeer</code>
     */
    public void setAlternativeIMPPAddress(String address)
    {
        alternativeIMPPAddress = address;
    }

    /**
     * Implements <code>CallPeer#addCallPeerConferenceListener(
     * CallPeerConferenceListener)</code>. In the fashion of the addition of the other listeners, does
     * not throw an exception on attempting to add a <code>null</code> listeners and just ignores the call.
     *
     * @param listener the <code>CallPeerConferenceListener</code> to add
     */
    public void addCallPeerConferenceListener(CallPeerConferenceListener listener)
    {
        if (listener != null)
            synchronized (callPeerConferenceListeners) {
                if (!callPeerConferenceListeners.contains(listener))
                    callPeerConferenceListeners.add(listener);
            }
    }

    /**
     * Registers the <code>listener</code> to the list of listeners that would be receiving CallPeerEvents.
     *
     * @param listener a listener instance to register with this peer.
     */
    public void addCallPeerListener(CallPeerListener listener)
    {
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
    public void addCallPeerSecurityListener(CallPeerSecurityListener listener)
    {
        if (listener == null)
            return;
        synchronized (callPeerSecurityListeners) {
            if (!callPeerSecurityListeners.contains(listener))
                callPeerSecurityListeners.add(listener);
        }
    }

    /**
     * Adds a specific <code>ConferenceMember</code> to the list of <code>ConferenceMember</code>s reported
     * by this peer through {@link #getConferenceMembers()} and {@link #getConferenceMemberCount()}
     * and fires <code>CallPeerConferenceEvent#CONFERENCE_MEMBER_ADDED</code> to the currently
     * registered <code>CallPeerConferenceListener</code>s.
     *
     * @param conferenceMember a <code>ConferenceMember</code> to be added to the list of <code>ConferenceMember</code>
     * reported by this peer. If the specified <code>ConferenceMember</code> is already contained
     * in the list, it is not added again and no event is fired.
     */
    public void addConferenceMember(ConferenceMember conferenceMember)
    {
        if (conferenceMember == null)
            throw new NullPointerException("conferenceMember");
        else {
            synchronized (conferenceMembersSyncRoot) {
                if (conferenceMembers.contains(conferenceMember))
                    return;
                else {
                    List<ConferenceMember> newConferenceMembers = new ArrayList<>(conferenceMembers);

                    if (newConferenceMembers.add(conferenceMember)) {
                        conferenceMembers = newConferenceMembers;
                        unmodifiableConferenceMembers = Collections.unmodifiableList(conferenceMembers);
                    }
                    else
                        return;
                }
            }
            fireCallPeerConferenceEvent(new CallPeerConferenceEvent(this,
                    CallPeerConferenceEvent.CONFERENCE_MEMBER_ADDED, conferenceMember));
        }
    }

    /**
     * Fires <code>CallPeerConferenceEvent#CONFERENCE_MEMBER_ERROR_RECEIVED</code> to the currently
     * registered <code>CallPeerConferenceListener</code>s.
     *
     * @param errorMessage error message that can be displayed.
     */
    public void fireConferenceMemberErrorEvent(String errorMessage)
    {
        if (errorMessage == null || errorMessage.length() == 0) {
            Timber.w("The error message for %  is null or empty string.", this.getDisplayName());
            return;
        }

        fireCallPeerConferenceEvent(new CallPeerConferenceEvent(this,
                CallPeerConferenceEvent.CONFERENCE_MEMBER_ERROR_RECEIVED, null, errorMessage));
    }

    /**
     * Finds the first <code>ConferenceMember</code> whose <code>audioSsrc</code> is equals to a specific
     * value. The method is meant for very frequent use so it iterates over the <code>List</code> of
     * <code>ConferenceMember</code>s without creating an <code>Iterator</code>.
     *
     * @param ssrc the SSRC identifier of the audio RTP streams transmitted by the
     * <code>ConferenceMember</code> that we are looking for.
     * @return the first <code>ConferenceMember</code> whose <code>audioSsrc</code> is equal to
     * <code>ssrc</code> or <code>null</code> if no such <code>ConferenceMember</code> was found
     */
    protected ConferenceMember findConferenceMember(long ssrc)
    {
        List<ConferenceMember> members = getConferenceMembers();
        for (int i = 0, memberCount = members.size(); i < memberCount; i++) {
            ConferenceMember member = members.get(i);
            if (member.getAudioSsrc() == ssrc)
                return member;
        }
        return null;
    }

    /**
     * Constructs a <code>CallPeerChangeEvent</code> using this call peer as source, setting it to be of
     * type <code>eventType</code> and the corresponding <code>oldValue</code> and <code>newValue</code>,
     *
     * @param eventType the type of the event to create and dispatch.
     * @param oldValue the value of the source property before it changed.
     * @param newValue the current value of the source property.
     */
    protected void fireCallPeerChangeEvent(String eventType, Object oldValue, Object newValue)
    {
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
    protected void fireCallPeerChangeEvent(String eventType, Object oldValue, Object newValue, String reason)
    {
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
    protected void fireCallPeerChangeEvent(String eventType, Object oldValue, Object newValue, String reason, int reasonCode)
    {
        CallPeerChangeEvent evt = new CallPeerChangeEvent(this, eventType, oldValue, newValue, reason, reasonCode);

        Iterator<CallPeerListener> listeners;
        synchronized (callPeerListeners) {
            listeners = new ArrayList<>(callPeerListeners).iterator();
        }

        Timber.d("Dispatching CallPeerChangeEvent (%s): %s; Events: %s", callPeerListeners.size(), callPeerListeners, evt);
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
            } catch (Throwable t) {
                Timber.e(t, "Error dispatching event to %s: %s", listener, eventType);
            }
        }
    }

    /**
     * Fires a specific <code>CallPeerConferenceEvent</code> to the <code>CallPeerConferenceListener</code>s
     * interested in changes in the conference-related information provided by this peer.
     *
     * @param conferenceEvent a <code>CallPeerConferenceEvent</code> to be fired and carrying the event data
     */
    protected void fireCallPeerConferenceEvent(CallPeerConferenceEvent conferenceEvent)
    {
        CallPeerConferenceListener[] listeners;

        synchronized (callPeerConferenceListeners) {
            listeners = callPeerConferenceListeners.toArray(new CallPeerConferenceListener[0]);
        }

        int eventID = conferenceEvent.getEventID();
        String eventIDString;
        switch (eventID) {
            case CallPeerConferenceEvent.CONFERENCE_FOCUS_CHANGED:
                eventIDString = "CONFERENCE_FOCUS_CHANGED";
                break;
            case CallPeerConferenceEvent.CONFERENCE_MEMBER_ADDED:
                eventIDString = "CONFERENCE_MEMBER_ADDED";
                break;
            case CallPeerConferenceEvent.CONFERENCE_MEMBER_REMOVED:
                eventIDString = "CONFERENCE_MEMBER_REMOVED";
                break;
            case CallPeerConferenceEvent.CONFERENCE_MEMBER_ERROR_RECEIVED:
                eventIDString = "CONFERENCE_MEMBER_ERROR_RECEIVED";
                break;
            default:
                eventIDString = "UNKNOWN";
                break;
        }
        Timber.d("Dispatching CallPeerConferenceEvent with ID %s to %s listeners", eventIDString, listeners.length);

        for (CallPeerConferenceListener listener : listeners)
            switch (eventID) {
                case CallPeerConferenceEvent.CONFERENCE_FOCUS_CHANGED:
                    listener.conferenceFocusChanged(conferenceEvent);
                    break;
                case CallPeerConferenceEvent.CONFERENCE_MEMBER_ADDED:
                    listener.conferenceMemberAdded(conferenceEvent);
                    break;
                case CallPeerConferenceEvent.CONFERENCE_MEMBER_REMOVED:
                    listener.conferenceMemberRemoved(conferenceEvent);
                    break;
                case CallPeerConferenceEvent.CONFERENCE_MEMBER_ERROR_RECEIVED:
                    listener.conferenceMemberErrorReceived(conferenceEvent);
                    break;
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
    protected void fireCallPeerSecurityMessageEvent(String messageType, String i18nMessage, int severity)
    {
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
    protected void fireCallPeerSecurityNegotiationStartedEvent(CallPeerSecurityNegotiationStartedEvent evt)
    {
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
    protected void fireCallPeerSecurityOffEvent(CallPeerSecurityOffEvent evt)
    {
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
    protected void fireCallPeerSecurityOnEvent(CallPeerSecurityOnEvent evt)
    {
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
    protected void fireCallPeerSecurityTimeoutEvent(CallPeerSecurityTimeoutEvent evt)
    {
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
    public long getCallDurationStartTime()
    {
        return callDurationStartTime;
    }

    /**
     * Returns a URL pointing ta a location with call control information for this peer or
     * <code>null</code> if no such URL is available for this call peer.
     *
     * @return a URL link to a location with call information or a call control web interface
     * related to this peer or <code>null</code> if no such URL is available.
     */
    public URL getCallInfoURL()
    {
        // if signaling protocols (such as SIP) know where to get this URL from they should override this method
        return null;
    }

    /**
     * {@inheritDoc}
     */
    public int getConferenceMemberCount()
    {
        synchronized (conferenceMembersSyncRoot) {
            return isConferenceFocus() ? getConferenceMembers().size() : 0;
        }
    }

    /**
     * {@inheritDoc}
     */
    public List<ConferenceMember> getConferenceMembers()
    {
        synchronized (conferenceMembersSyncRoot) {
            return unmodifiableConferenceMembers;
        }
    }

    /**
     * Returns the currently used security settings of this <code>CallPeer</code>.
     *
     * @return the <code>CallPeerSecurityStatusEvent</code> that contains the current security settings.
     */
    public CallPeerSecurityStatusEvent getCurrentSecuritySettings()
    {
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
    public CallPeerState getState()
    {
        return state;
    }

    /**
     * Determines whether this call peer is currently a conference focus.
     *
     * @return <code>true</code> if this peer is a conference focus and <code>false</code> otherwise.
     */
    public boolean isConferenceFocus()
    {
        return conferenceFocus;
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
    public boolean isMute()
    {
        return isMute;
    }

    /**
     * Implements <code>CallPeer#removeCallPeerConferenceListener(CallPeerConferenceListener)</code>.
     *
     * @param listener the <code>CallPeerConferenceListener</code> to remove
     */
    public void removeCallPeerConferenceListener(CallPeerConferenceListener listener)
    {
        if (listener != null)
            synchronized (callPeerConferenceListeners) {
                callPeerConferenceListeners.remove(listener);
            }
    }

    /**
     * Unregisters the specified listener.
     *
     * @param listener the listener to unregister.
     */
    public void removeCallPeerListener(CallPeerListener listener)
    {
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
    public void removeCallPeerSecurityListener(CallPeerSecurityListener listener)
    {
        if (listener == null)
            return;
        synchronized (callPeerSecurityListeners) {
            callPeerSecurityListeners.remove(listener);
        }
    }

    /**
     * Removes a specific <code>ConferenceMember</code> from the list of <code>ConferenceMember</code>s
     * reported by this peer through {@link #getConferenceMembers()} and
     * {@link #getConferenceMemberCount()} if it is contained and fires
     * <code>CallPeerConferenceEvent#CONFERENCE_MEMBER_REMOVED</code> to the currently registered
     * <code>CallPeerConferenceListener</code>s.
     *
     * @param conferenceMember a <code>ConferenceMember</code> to be removed from the list of <code>ConferenceMember</code>
     * reported by this peer. If the specified <code>ConferenceMember</code> is no contained in the list, no event is fired.
     */
    public void removeConferenceMember(ConferenceMember conferenceMember)
    {
        if (conferenceMember != null) {
            synchronized (conferenceMembersSyncRoot) {
                if (conferenceMembers.contains(conferenceMember)) {
                    List<ConferenceMember> newConferenceMembers = new ArrayList<>(conferenceMembers);

                    if (newConferenceMembers.remove(conferenceMember)) {
                        conferenceMembers = newConferenceMembers;
                        unmodifiableConferenceMembers = Collections.unmodifiableList(conferenceMembers);
                    }
                    else
                        return;
                }
                else
                    return;
            }
            fireCallPeerConferenceEvent(new CallPeerConferenceEvent(this,
                    CallPeerConferenceEvent.CONFERENCE_MEMBER_REMOVED, conferenceMember));
        }
    }

    /**
     * Specifies whether this peer is a conference focus.
     *
     * @param conferenceFocus <code>true</code> if this peer is to become a conference focus and <code>false</code> otherwise.
     */
    public void setConferenceFocus(boolean conferenceFocus)
    {
        if (this.conferenceFocus != conferenceFocus) {
            this.conferenceFocus = conferenceFocus;

            fireCallPeerConferenceEvent(new CallPeerConferenceEvent(this,
                    CallPeerConferenceEvent.CONFERENCE_FOCUS_CHANGED));
        }
    }

    /**
     * Sets the mute property for this call peer.
     *
     * @param newMuteValue the new value of the mute property for this call peer
     */
    public void setMute(boolean newMuteValue)
    {
        this.isMute = newMuteValue;
        firePropertyChange(MUTE_PROPERTY_NAME, isMute, newMuteValue);
    }

    /**
     * Causes this CallPeer to enter the specified state. The method also sets the
     * currentStateStartDate field and fires a CallPeerChangeEvent.
     *
     * @param newState the state this call peer should enter.
     */
    public void setState(CallPeerState newState)
    {
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
    public void setState(CallPeerState newState, String reason)
    {
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
    public void setState(CallPeerState newState, String reason, int reasonCode)
    {
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
    public String toString()
    {
        return getDisplayName() + " <" + getAddress() + ">; status=" + getState().getStateString();
    }
}
