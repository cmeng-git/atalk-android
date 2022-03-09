/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package net.java.sip.communicator.service.protocol;

import net.java.sip.communicator.service.protocol.event.CallChangeEvent;
import net.java.sip.communicator.service.protocol.event.CallChangeListener;
import net.java.sip.communicator.service.protocol.event.CallPeerConferenceAdapter;
import net.java.sip.communicator.service.protocol.event.CallPeerConferenceEvent;
import net.java.sip.communicator.service.protocol.event.CallPeerConferenceListener;
import net.java.sip.communicator.service.protocol.event.CallPeerEvent;

import org.atalk.util.event.PropertyChangeNotifier;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;

/**
 * Represents the telephony conference-related state of a <code>Call</code>. Multiple <code>Call</code>
 * instances share a single <code>CallConference</code> instance when the former are into a telephony
 * conference i.e. the local peer/user is the conference focus. <code>CallConference</code> is
 * protocol-agnostic and thus enables cross-protocol conferences. Since a non-conference
 * <code>Call</code> may be converted into a conference <code>Call</code> at any time, every <code>Call</code>
 * instance maintains a <code>CallConference</code> instance regardless of whether the <code>Call</code> in
 * question is participating in a telephony conference.
 *
 * @author Lyubomir Marinov
 * @author Eng Chong Meng
 */
public class CallConference extends PropertyChangeNotifier
{
    /**
     * The name of the <code>CallConference</code> property which specifies the list of <code>Call</code>s
     * participating in a telephony conference. A change in the value of the property is delivered
     * in the form of a <code>PropertyChangeEvent</code> which has its <code>oldValue</code> or
     * <code>newValue</code> set to the <code>Call</code> which has been removed or added to the list of
     * <code>Call</code>s participating in the telephony conference.
     */
    public static final String CALLS = "calls";

    /**
     * Gets the number of <code>CallPeer</code>s associated with the <code>Call</code>s participating in the
     * telephony conference-related state of a specific <code>Call</code>.
     *
     * @param call the <code>Call</code> for which the number of <code>CallPeer</code>s associated with the
     * <code>Call</code>s participating in its associated telephony conference-related state
     * @return the number of <code>CallPeer</code>s associated with the <code>Call</code>s participating in
     * the telephony conference-related state of the specified <code>Call</code>
     */
    public static int getCallPeerCount(Call call)
    {
        CallConference conference = call.getConference();

        /*
         * A Call instance is supposed to always maintain a CallConference instance. Anyway, if it
         * turns out that it is not the case, we will consider the Call as a representation of a
         * telephony conference.
         */
        return (conference == null) ? call.getCallPeerCount() : conference.getCallPeerCount();
    }

    /**
     * Gets a list of the <code>CallPeer</code>s associated with the <code>Call</code>s participating in the
     * telephony conference in which a specific <code>Call</code> is participating.
     *
     * @param call the <code>Call</code> which specifies the telephony conference the <code>CallPeer</code>s of
     * which are to be retrieved
     * @return a list of the <code>CallPeer</code>s associated with the <code>Call</code>s participating in
     * the telephony conference in which the specified <code>call</code> is participating
     */
    public static List<CallPeer> getCallPeers(Call call)
    {
        CallConference conference = call.getConference();
        List<CallPeer> callPeers = new ArrayList<>();

        if (conference == null) {
            Iterator<? extends CallPeer> callPeerIt = call.getCallPeers();

            while (callPeerIt.hasNext())
                callPeers.add(callPeerIt.next());
        }
        else
            conference.getCallPeers(callPeers);
        return callPeers;
    }

    /**
     * Gets the list of <code>Call</code>s participating in the telephony conference in which a specific
     * <code>Call</code> is participating.
     *
     * @param call the <code>Call</code> which participates in the telephony conference the list of
     * participating <code>Call</code>s of which is to be returned
     * @return the list of <code>Call</code>s participating in the telephony conference in which the
     * specified <code>call</code> is participating
     */
    public static List<Call> getCalls(Call call)
    {
        CallConference conference = call.getConference();
        List<Call> calls;

        if (conference == null)
            calls = Collections.emptyList();
        else
            calls = conference.getCalls();
        return calls;
    }

    /**
     * Determines whether a <code>CallConference</code> is to report the local peer/user as a conference
     * focus judging by a specific list of <code>Call</code>s.
     *
     * @param calls the list of <code>Call</code> which are to be judged whether the local peer/user that they
     * represent is to be considered as a conference focus
     * @return <code>true</code> if the local peer/user represented by the specified <code>calls</code> is
     * judged to be a conference focus; otherwise, <code>false</code>
     */
    private static boolean isConferenceFocus(List<Call> calls)
    {
        int callCount = calls.size();
        boolean conferenceFocus;

        if (callCount < 1)
            conferenceFocus = false;
        else if (callCount > 1)
            conferenceFocus = true;
        else
            conferenceFocus = (calls.get(0).getCallPeerCount() > 1);
        return conferenceFocus;
    }

    /**
     * The <code>CallChangeListener</code> which listens to changes in the <code>Call</code>s participating
     * in this telephony conference.
     */
    private final CallChangeListener callChangeListener = new CallChangeListener()
    {
        @Override
        public void callPeerAdded(CallPeerEvent ev)
        {
            CallConference.this.onCallPeerEvent(ev);
        }

        @Override
        public void callPeerRemoved(CallPeerEvent ev)
        {
            CallConference.this.onCallPeerEvent(ev);
        }

        @Override
        public void callStateChanged(CallChangeEvent ev)
        {
            CallConference.this.callStateChanged(ev);
        }
    };

    /**
     * The list of <code>CallChangeListener</code>s added to the <code>Call</code>s participating in this
     * telephony conference via {@link #addCallChangeListener(CallChangeListener)}.
     */
    private final List<CallChangeListener> callChangeListeners = new LinkedList<>();

    /**
     * The <code>CallPeerConferenceListener</code> which listens to the <code>CallPeer</code>s associated
     * with the <code>Call</code> s participating in this telephony conference.
     */
    private final CallPeerConferenceListener callPeerConferenceListener = new CallPeerConferenceAdapter()
    {
        /**
         * {@inheritDoc}
         *
         * Invokes {@link CallConference#onCallPeerConferenceEvent(CallPeerConferenceEvent)}.
         */
        @Override
        protected void onCallPeerConferenceEvent(CallPeerConferenceEvent ev)
        {
            CallConference.this.onCallPeerConferenceEvent(ev);
        }

        /**
         * {@inheritDoc}
         *
         * Invokes {@link CallConference#onCallPeerConferenceEvent(CallPeerConferenceEvent)}.
         */
        @Override
        public void conferenceMemberErrorReceived(CallPeerConferenceEvent ev)
        {
            CallConference.this.onCallPeerConferenceEvent(ev);
        }
    };

    /**
     * The list of <code>CallPeerConferenceListener</code>s added to the <code>CallPeer</code>s associated
     * with the <code>CallPeer</code>s participating in this telephony conference via
     * {@link #addCallPeerConferenceListener}.
     */
    private final List<CallPeerConferenceListener> callPeerConferenceListeners = new LinkedList<>();

    /**
     * The synchronization root/<code>Object</code> which protects the access to {@link #immutableCalls}
     * and {@link #mutableCalls}.
     */
    private final Object callsSyncRoot = new Object();

    /**
     * The indicator which determines whether the local peer represented by this instance and the
     * <code>Call</code>s participating in it is acting as a conference focus. The SIP protocol, for
     * example, will add the &quot;isfocus&quot; parameter to the Contact headers of its outgoing
     * signaling if <code>true</code>.
     */
    private boolean conferenceFocus = false;

    /**
     * The list of <code>Call</code>s participating in this telephony conference as an immutable
     * <code>List</code> which can be exposed out of this instance without the need to make a copy. In
     * other words, it is an unmodifiable view of {@link #mutableCalls}.
     */
    private List<Call> immutableCalls;

    /**
     * The indicator which determines whether the telephony conference represented by this instance
     * is utilizing the Jitsi Videobridge server-side telephony conferencing technology.
     */
    private final boolean jitsiVideobridge;

    /**
     * The list of <code>Call</code>s participating in this telephony conference as a mutable
     * <code>List</code> which should not be exposed out of this instance.
     */
    private List<Call> mutableCalls;

    /**
     * Initializes a new <code>CallConference</code> instance.
     */
    public CallConference()
    {
        this(false);
    }

    /**
     * Initializes a new <code>CallConference</code> instance which is to optionally utilize the Jitsi
     * Videobridge server-side telephony conferencing technology.
     *
     * @param jitsiVideobridge <code>true</code> if the telephony conference represented by the new instance is to
     * utilize the Jitsi Videobridge server-side telephony conferencing technology;
     * otherwise, <code>false</code>
     */
    public CallConference(boolean jitsiVideobridge)
    {
        this.jitsiVideobridge = jitsiVideobridge;

        mutableCalls = new ArrayList<>();
        immutableCalls = Collections.unmodifiableList(mutableCalls);
    }

    /**
     * Adds a specific <code>Call</code> to the list of <code>Call</code>s participating in this telephony
     * conference.
     *
     * @param call the <code>Call</code> to add to the list of <code>Call</code>s participating in this telephony
     * conference
     * @return <code>true</code> if the list of <code>Call</code>s participating in this telephony
     * conference changed as a result of the method call; otherwise, <code>false</code>
     * @throws NullPointerException if <code>call</code> is <code>null</code>
     */
    boolean addCall(Call call)
    {
        if (call == null)
            throw new NullPointerException("call");

        synchronized (callsSyncRoot) {
            if (mutableCalls.contains(call))
                return false;

            /*
             * Implement the List of Calls participating in this telephony conference as a
             * copy-on-write storage in order to optimize the getCalls method which is likely to be
             * executed much more often than the addCall and removeCall methods.
             */
            List<Call> newMutableCalls = new ArrayList<>(mutableCalls);

            if (newMutableCalls.add(call)) {
                mutableCalls = newMutableCalls;
                immutableCalls = Collections.unmodifiableList(mutableCalls);
            }
            else
                return false;
        }

        callAdded(call);
        return true;
    }

    /**
     * Adds a <code>CallChangeListener</code> to the <code>Call</code>s participating in this telephony
     * conference. The method is a convenience that takes on the responsibility of tracking the
     * <code>Call</code>s that get added/removed to/from this telephony conference.
     *
     * @param listener the <code>CallChangeListner</code> to be added to the <code>Call</code>s participating in this
     * telephony conference
     * @throws NullPointerException if <code>listener</code> is <code>null</code>
     */
    public void addCallChangeListener(CallChangeListener listener)
    {
        if (listener == null)
            throw new NullPointerException("listener");
        else {
            synchronized (callChangeListeners) {
                if (!callChangeListeners.contains(listener))
                    callChangeListeners.add(listener);
            }
        }
    }

    /**
     * Adds {@link #callPeerConferenceListener} to the <code>CallPeer</code>s associated with a specific
     * <code>Call</code>.
     *
     * @param call the <code>Call</code> to whose associated <code>CallPeer</code>s
     * <code>callPeerConferenceListener</code> is to be added
     */
    private void addCallPeerConferenceListener(Call call)
    {
        Iterator<? extends CallPeer> callPeerIter = call.getCallPeers();

        while (callPeerIter.hasNext()) {
            callPeerIter.next().addCallPeerConferenceListener(callPeerConferenceListener);
        }
    }

    /**
     * Adds a <code>CallPeerConferenceListener</code> to the <code>CallPeer</code>s associated with the
     * <code>Call</code>s participating in this telephony conference. The method is a convenience that
     * takes on the responsibility of tracking the <code>Call</code>s that get added/removed to/from
     * this telephony conference and the <code>CallPeer</code> that get added/removed to/from these
     * <code>Call</code>s.
     *
     * @param listener the <code>CallPeerConferenceListener</code> to be added to the <code>CallPeer</code>s
     * associated with the <code>Call</code>s participating in this telephony conference
     * @throws NullPointerException if <code>listener</code> is <code>null</code>
     */
    public void addCallPeerConferenceListener(CallPeerConferenceListener listener)
    {
        if (listener == null)
            throw new NullPointerException("listener");
        else {
            synchronized (callPeerConferenceListeners) {
                if (!callPeerConferenceListeners.contains(listener))
                    callPeerConferenceListeners.add(listener);
            }
        }
    }

    /**
     * Notifies this <code>CallConference</code> that a specific <code>Call</code> has been added to the
     * list of <code>Call</code>s participating in this telephony conference.
     *
     * @param call the <code>Call</code> which has been added to the list of <code>Call</code>s participating in
     * this telephony conference
     */
    protected void callAdded(Call call)
    {
        call.addCallChangeListener(callChangeListener);
        addCallPeerConferenceListener(call);

        /*
         * Update the conferenceFocus state. Because the public setConferenceFocus method allows
         * forcing a specific value on the state in question and because it does not sound right to
         * have the adding of a Call set conferenceFocus to false, only update it if the new
         * conferenceFocus value is true,
         */
        boolean conferenceFocus = isConferenceFocus(getCalls());

        if (conferenceFocus)
            setConferenceFocus(true);

        firePropertyChange(CALLS, null, call);
    }

    /**
     * Notifies this <code>CallConference</code> that a specific <code>Call</code> has been removed from the
     * list of <code>Call</code>s participating in this telephony conference.
     *
     * @param call the <code>Call</code> which has been removed from the list of <code>Call</code>s participating
     * in this telephony conference
     */
    protected void callRemoved(Call call)
    {
        call.removeCallChangeListener(callChangeListener);
        removeCallPeerConferenceListener(call);

        /*
         * Update the conferenceFocus state. Following the line of thinking expressed in the
         * callAdded method, only update it if the new conferenceFocus value is false.
         */
        boolean conferenceFocus = isConferenceFocus(getCalls());

        if (!conferenceFocus)
            setConferenceFocus(false);

        firePropertyChange(CALLS, call, null);
    }

    /**
     * Notifies this telephony conference that the <code>CallState</code> of a <code>Call</code> has
     * changed.
     *
     * @param ev a <code>CallChangeEvent</code> which specifies the <code>Call</code> which had its
     * <code>CallState</code> changed and the old and new <code>CallState</code>s of that
     * <code>Call</code>
     */
    private void callStateChanged(CallChangeEvent ev)
    {
        Call call = ev.getSourceCall();

        if (containsCall(call)) {
            try {
                // Forward the CallChangeEvent to the callChangeListeners.
                for (CallChangeListener l : getCallChangeListeners())
                    l.callStateChanged(ev);
            } finally {
                if (CallChangeEvent.CALL_STATE_CHANGE.equals(ev.getPropertyName())
                        && CallState.CALL_ENDED.equals(ev.getNewValue())) {
                    /*
                     * Should not be vital because Call will remove itself. Anyway, do it for the
                     * sake of completeness.
                     */
                    removeCall(call);
                }
            }
        }
    }

    /**
     * Notifies this <code>CallConference</code> that the value of its <code>conferenceFocus</code> property
     * has changed from a specific old value to a specific new value.
     *
     * @param oldValue the value of the <code>conferenceFocus</code> property of this instance before the change
     * @param newValue the value of the <code>conferenceFocus</code> property of this instance after the change
     */
    protected void conferenceFocusChanged(boolean oldValue, boolean newValue)
    {
        firePropertyChange(Call.CONFERENCE_FOCUS, oldValue, newValue);
    }

    /**
     * Determines whether a specific <code>Call</code> is participating in this telephony conference.
     *
     * @param call the <code>Call</code> which is to be checked whether it is participating in this telephony
     * conference
     * @return <code>true</code> if the specified <code>call</code> is participating in this telephony
     * conference
     */
    public boolean containsCall(Call call)
    {
        synchronized (callsSyncRoot) {
            return mutableCalls.contains(call);
        }
    }

    /**
     * Gets the list of <code>CallChangeListener</code>s added to the <code>Call</code>s participating in
     * this telephony conference via {@link #addCallChangeListener(CallChangeListener)}.
     *
     * @return the list of <code>CallChangeListener</code>s added to the <code>Call</code>s participating in
     * this telephony conference via {@link #addCallChangeListener(CallChangeListener)}
     */
    private CallChangeListener[] getCallChangeListeners()
    {
        synchronized (callChangeListeners) {
            return callChangeListeners.toArray(new CallChangeListener[0]);
        }
    }

    /**
     * Gets the number of <code>Call</code>s that are participating in this telephony conference.
     *
     * @return the number of <code>Call</code>s that are participating in this telephony conference
     */
    public int getCallCount()
    {
        synchronized (callsSyncRoot) {
            return mutableCalls.size();
        }
    }

    /**
     * Gets the list of <code>CallPeerConferenceListener</code>s added to the <code>CallPeer</code>s
     * associated with the <code>Call</code>s participating in this telephony conference via
     * {@link #addCallPeerConferenceListener(CallPeerConferenceListener)}.
     *
     * @return the list of <code>CallPeerConferenceListener</code>s added to the <code>CallPeer</code>s
     * associated with the <code>Call</code>s participating in this telephony conference via
     * {@link #addCallPeerConferenceListener(CallPeerConferenceListener)}
     */
    private CallPeerConferenceListener[] getCallPeerConferenceListeners()
    {
        synchronized (callPeerConferenceListeners) {
            return callPeerConferenceListeners.toArray(new CallPeerConferenceListener[0]);
        }
    }

    /**
     * Gets the number of <code>CallPeer</code>s associated with the <code>Call</code>s participating in
     * this telephony conference.
     *
     * @return the number of <code>CallPeer</code>s associated with the <code>Call</code>s participating in
     * this telephony conference
     */
    public int getCallPeerCount()
    {
        int callPeerCount = 0;

        for (Call call : getCalls())
            callPeerCount += call.getCallPeerCount();
        return callPeerCount;
    }

    /**
     * Gets a list of the <code>CallPeer</code>s associated with the <code>Call</code>s participating in
     * this telephony conference.
     *
     * @return a list of the <code>CallPeer</code>s associated with the <code>Call</code>s participating in
     * this telephony conference
     */
    public List<CallPeer> getCallPeers()
    {
        List<CallPeer> callPeers = new ArrayList<>();

        getCallPeers(callPeers);
        return callPeers;
    }

    /**
     * Adds the <code>CallPeer</code>s associated with the <code>Call</code>s participating in this
     * telephony conference into a specific <code>List</code>.
     *
     * @param callPeers a <code>List</code> into which the <code>CallPeer</code>s associated with the <code>Call</code>s
     * participating in this telephony conference are to be added
     */
    protected void getCallPeers(List<CallPeer> callPeers)
    {
        for (Call call : getCalls()) {
            Iterator<? extends CallPeer> callPeerIt = call.getCallPeers();

            while (callPeerIt.hasNext())
                callPeers.add(callPeerIt.next());
        }
    }

    /**
     * Gets the list of <code>Call</code> participating in this telephony conference.
     *
     * @return the list of <code>Call</code>s participating in this telephony conference. An empty array
     * of <code>Call</code> element type is returned if there are no <code>Call</code>s in this
     * telephony conference-related state.
     */
    public List<Call> getCalls()
    {
        synchronized (callsSyncRoot) {
            return immutableCalls;
        }
    }

    /**
     * Determines whether the local peer/user associated with this instance and represented by the
     * <code>Call</code>s participating into it is acting as a conference focus.
     *
     * @return <code>true</code> if the local peer/user associated by this instance is acting as a
     * conference focus; otherwise, <code>false</code>
     */
    public boolean isConferenceFocus()
    {
        return conferenceFocus;
    }

    /**
     * Determines whether the current state of this instance suggests that the telephony conference
     * it represents has ended. Iterates over the <code>Call</code>s participating in this telephony
     * conference and looks for a <code>Call</code> which is not in the {@link CallState#CALL_ENDED}
     * state.
     *
     * @return <code>true</code> if the current state of this instance suggests that the telephony
     * conference it represents has ended; otherwise, <code>false</code>
     */
    public boolean isEnded()
    {
        for (Call call : getCalls()) {
            if (!CallState.CALL_ENDED.equals(call.getCallState()))
                return false;
        }
        return true;
    }

    /**
     * Determines whether the telephony conference represented by this instance is utilizing the
     * Jitsi Videobridge server-side telephony conferencing technology.
     *
     * @return <code>true</code> if the telephony conference represented by this instance is utilizing
     * the Jitsi Videobridge server-side telephony conferencing technology
     */
    public boolean isJitsiVideobridge()
    {
        return jitsiVideobridge;
    }

    /**
     * Notifies this telephony conference that a <code>CallPeerConferenceEvent</code> was fired by a
     * <code>CallPeer</code> associated with a <code>Call</code> participating in this telephony conference.
     * Forwards the specified <code>CallPeerConferenceEvent</code> to
     * {@link #callPeerConferenceListeners}.
     *
     * @param ev the <code>CallPeerConferenceEvent</code> which was fired
     */
    private void onCallPeerConferenceEvent(CallPeerConferenceEvent ev)
    {
        int eventID = ev.getEventID();

        for (CallPeerConferenceListener l : getCallPeerConferenceListeners()) {
            switch (eventID) {
                case CallPeerConferenceEvent.CONFERENCE_FOCUS_CHANGED:
                    l.conferenceFocusChanged(ev);
                    break;
                case CallPeerConferenceEvent.CONFERENCE_MEMBER_ADDED:
                    l.conferenceMemberAdded(ev);
                    break;
                case CallPeerConferenceEvent.CONFERENCE_MEMBER_REMOVED:
                    l.conferenceMemberRemoved(ev);
                    break;
                case CallPeerConferenceEvent.CONFERENCE_MEMBER_ERROR_RECEIVED:
                    l.conferenceMemberErrorReceived(ev);
                    break;
                default:
                    throw new UnsupportedOperationException(
                            "Unsupported CallPeerConferenceEvent eventID.");
            }
        }
    }

    /**
     * Notifies this telephony conference about a specific <code>CallPeerEvent</code> i.e. that a
     * <code>CallPeer</code> was either added to or removed from a <code>Call</code>.
     *
     * @param ev a <code>CallPeerEvent</code> which specifies the <code>CallPeer</code> which was added or
     * removed and the <code>Call</code> to which it was added or from which is was removed
     */
    private void onCallPeerEvent(CallPeerEvent ev)
    {
        Call call = ev.getSourceCall();

        if (containsCall(call)) {
            /*
             * Update the conferenceFocus state. Following the line of thinking expressed in the
             * callAdded and callRemoved methods, only update it if the new conferenceFocus value is
             * in accord with the expectations.
             */
            int eventID = ev.getEventID();
            boolean conferenceFocus = isConferenceFocus(getCalls());

            switch (eventID) {
                case CallPeerEvent.CALL_PEER_ADDED:
                    if (conferenceFocus)
                        setConferenceFocus(true);
                    break;
                case CallPeerEvent.CALL_PEER_REMOVED:
                    if (!conferenceFocus)
                        setConferenceFocus(false);
                    break;
                default:
                    /*
                     * We're interested in the adding and removing of CallPeers only.
                     */
                    break;
            }

            try {
                // Forward the CallPeerEvent to the callChangeListeners.
                for (CallChangeListener l : getCallChangeListeners()) {
                    switch (eventID) {
                        case CallPeerEvent.CALL_PEER_ADDED:
                            l.callPeerAdded(ev);
                            break;
                        case CallPeerEvent.CALL_PEER_REMOVED:
                            l.callPeerRemoved(ev);
                            break;
                        default:
                            break;
                    }
                }
            } finally {
                /*
                 * Add/remove the callPeerConferenceListener to/from the source CallPeer (for the
                 * purposes of the addCallPeerConferenceListener method of this CallConference).
                 */
                CallPeer callPeer = ev.getSourceCallPeer();

                switch (eventID) {
                    case CallPeerEvent.CALL_PEER_ADDED:
                        callPeer.addCallPeerConferenceListener(callPeerConferenceListener);
                        break;
                    case CallPeerEvent.CALL_PEER_REMOVED:
                        callPeer.removeCallPeerConferenceListener(callPeerConferenceListener);
                        break;
                    default:
                        break;
                }
            }
        }
    }

    /**
     * Removes a specific <code>Call</code> from the list of <code>Call</code>s participating in this
     * telephony conference.
     *
     * @param call the <code>Call</code> to remove from the list of <code>Call</code>s participating in this
     * telephony conference
     * @return <code>true</code> if the list of <code>Call</code>s participating in this telephony
     * conference changed as a result of the method call; otherwise, <code>false</code>
     */
    boolean removeCall(Call call)
    {
        if (call == null)
            return false;

        synchronized (callsSyncRoot) {
            if (!mutableCalls.contains(call))
                return false;

            /*
             * Implement the List of Calls participating in this telephony conference as a
             * copy-on-write storage in order to optimize the getCalls method which is likely to be
             * executed much more often than the addCall and removeCall methods.
             */
            List<Call> newMutableCalls = new ArrayList<>(mutableCalls);

            if (newMutableCalls.remove(call)) {
                mutableCalls = newMutableCalls;
                immutableCalls = Collections.unmodifiableList(mutableCalls);
            }
            else
                return false;
        }

        callRemoved(call);
        return true;
    }

    /**
     * Removes a <code>CallChangeListener</code> from the <code>Call</code>s participating in this telephony
     * conference.
     *
     * @param listener the <code>CallChangeListener</code> to be removed from the <code>Call</code>s participating in
     * this telephony conference
     * @see #addCallChangeListener(CallChangeListener)
     */
    public void removeCallChangeListener(CallChangeListener listener)
    {
        if (listener != null) {
            synchronized (callChangeListeners) {
                callChangeListeners.remove(listener);
            }
        }
    }

    /**
     * Removes {@link #callPeerConferenceListener} from the <code>CallPeer</code>s associated with a
     * specific <code>Call</code>.
     *
     * @param call the <code>Call</code> from whose associated <code>CallPeer</code>s
     * <code>callPeerConferenceListener</code> is to be removed
     */
    private void removeCallPeerConferenceListener(Call call)
    {
        Iterator<? extends CallPeer> callPeerIter = call.getCallPeers();

        while (callPeerIter.hasNext()) {
            callPeerIter.next().removeCallPeerConferenceListener(callPeerConferenceListener);
        }
    }

    /**
     * Removes a <code>CallPeerConferenceListener</code> from the <code>CallPeer</code>s associated with the
     * <code>Call</code>s participating in this telephony conference.
     *
     * @param listener the <code>CallPeerConferenceListener</code> to be removed from the <code>CallPeer</code>s
     * associated with the <code>Call</code>s participating in this telephony conference
     * @see #addCallPeerConferenceListener(CallPeerConferenceListener)
     */
    public void removeCallPeerConferenceListener(CallPeerConferenceListener listener)
    {
        if (listener != null) {
            synchronized (callPeerConferenceListeners) {
                callPeerConferenceListeners.remove(listener);
            }
        }
    }

    /**
     * Sets the indicator which determines whether the local peer represented by this instance and
     * the <code>Call</code>s participating in it is acting as a conference focus (and thus may, for
     * example, need to send the corresponding parameters in its outgoing signaling).
     *
     * @param conferenceFocus <code>true</code> if the local peer represented by this instance and the <code>Call</code>s
     * participating in it is to act as a conference focus; otherwise, <code>false</code>
     */
    public void setConferenceFocus(boolean conferenceFocus)
    {
        if (this.conferenceFocus != conferenceFocus) {
            boolean oldValue = isConferenceFocus();

            this.conferenceFocus = conferenceFocus;
            boolean newValue = isConferenceFocus();

            if (oldValue != newValue)
                conferenceFocusChanged(oldValue, newValue);
        }
    }
}
