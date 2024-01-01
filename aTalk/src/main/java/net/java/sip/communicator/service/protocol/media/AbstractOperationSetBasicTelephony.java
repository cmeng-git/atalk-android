/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package net.java.sip.communicator.service.protocol.media;

import net.java.sip.communicator.service.protocol.Call;
import net.java.sip.communicator.service.protocol.CallConference;
import net.java.sip.communicator.service.protocol.ChatRoom;
import net.java.sip.communicator.service.protocol.ConferenceDescription;
import net.java.sip.communicator.service.protocol.Contact;
import net.java.sip.communicator.service.protocol.OperationFailedException;
import net.java.sip.communicator.service.protocol.OperationSetBasicTelephony;
import net.java.sip.communicator.service.protocol.ProtocolProviderService;
import net.java.sip.communicator.service.protocol.event.CallEvent;
import net.java.sip.communicator.service.protocol.event.CallListener;

import org.atalk.impl.timberlog.TimberLog;
import org.atalk.service.neomedia.MediaDirection;
import org.atalk.service.neomedia.recording.Recorder;
import org.atalk.util.MediaType;

import java.text.ParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import timber.log.Timber;

/**
 * Represents a default implementation of <code>OperationSetBasicTelephony</code> in order to make it
 * easier for implementers to provide complete solutions while focusing on implementation-specific
 * details.
 *
 * @param <T> the implementation specific provider class like for example <code>ProtocolProviderServiceSipImpl</code>.
 * @author Lyubomir Marinov
 * @author Emil Ivov
 * @author Dmitri Melnikov
 * @author Eng Chong Meng
 */
public abstract class AbstractOperationSetBasicTelephony<T extends ProtocolProviderService>
        implements OperationSetBasicTelephony<T>
{
    /**
     * A list of listeners registered for call events.
     */
    private final List<CallListener> callListeners = new ArrayList<>();

    /**
     * {@inheritDoc}
     *
     * Forwards to {@link OperationSetBasicTelephony#createCall(Contact, CallConference)} with
     * <code>null</code> as the <code>CallConference</code> argument.
     */
    public Call createCall(Contact callee)
            throws OperationFailedException
    {
        return createCall(callee, null);
    }

    /**
     * {@inheritDoc}
     *
     * Forwards to {@link OperationSetBasicTelephony#createCall(String, CallConference)} with
     * {@link Contact#getAddress()} as the <code>String</code> argument.
     */
    public Call createCall(Contact callee, CallConference conference)
            throws OperationFailedException
    {
        try {
            return createCall(callee.getAddress(), conference);
        } catch (ParseException pe) {
            throw new OperationFailedException(pe.getMessage(), OperationFailedException.ILLEGAL_ARGUMENT, pe);
        }
    }

    /**
     * {@inheritDoc}
     *
     * Forwards to {@link OperationSetBasicTelephony#createCall(String, CallConference)} with
     * <code>null</code> as the <code>CallConference</code> argument.
     */
    public Call createCall(String uri)
            throws OperationFailedException, ParseException
    {
        return createCall(uri, null);
    }

    /**
     * {@inheritDoc}
     *
     * Always throws an exception.
     */
    @Override
    public Call createCall(ConferenceDescription cd, ChatRoom chatRoom)
            throws OperationFailedException
    {
        throw new OperationFailedException("Creating a call with a ConferenceDescription is not implemented in "
                + getClass(), OperationFailedException.INTERNAL_ERROR);
    }

    /**
     * Creates and dispatches a <code>CallEvent</code> notifying registered listeners that an event with
     * id <code>eventID</code> has occurred on <code>sourceCall</code>.
     *
     * @param eventID the ID of the event to dispatch
     * @param sourceCall the call on which the event has occurred.
     */
    public void fireCallEvent(int eventID, Call sourceCall)
    {
        fireCallEvent(eventID, sourceCall, null);
    }

    /**
     * Creates and dispatches a <code>CallEvent</code> notifying registered listeners that an event with
     * id <code>eventID</code> has occurred on <code>sourceCall</code>.
     *
     * @param eventID the ID of the event to dispatch
     * @param sourceCall the call on which the event has occurred.
     * @param mediaDirections direction map for media types
     */
    public void fireCallEvent(int eventID, Call sourceCall, Map<MediaType, MediaDirection> mediaDirections)
    {
        fireCallEvent(new CallEvent(sourceCall, eventID, mediaDirections));
    }

    /**
     * Creates and dispatches a <code>CallEvent</code> notifying registered listeners that an event with
     * id <code>eventID</code> has occurred on <code>sourceCall</code>.
     *
     * @param event the event to dispatch
     */
    public void fireCallEvent(CallEvent event)
    {
        List<CallListener> listeners;
        synchronized (callListeners) {
            listeners = new ArrayList<>(callListeners);
        }
        Timber.log(TimberLog.FINER, "Dispatching a CallEvent to %s listeners. The event is: %s", listeners.size(), event);

        for (CallListener listener : listeners) {
            Timber.log(TimberLog.FINER, "Dispatching a CallEvent to %s. The event is: %s", listener.getClass(), event);

            switch (event.getEventID()) {
                case CallEvent.CALL_INITIATED:
                    listener.outgoingCallCreated(event);
                    break;
                case CallEvent.CALL_RECEIVED:
                    listener.incomingCallReceived(event);
                    break;
                case CallEvent.CALL_ENDED:
                    listener.callEnded(event);
                    break;
            }
        }
    }

    /**
     * Registers <code>listener</code> with this provider, so that it could be notified when incoming calls are received.
     *
     * @param listener the listener to register with this provider.
     */
    public void addCallListener(CallListener listener)
    {
        synchronized (callListeners) {
            if (!callListeners.contains(listener))
                callListeners.add(listener);
        }
    }

    /**
     * Removes the <code>listener</code> from the list of call listeners.
     *
     * @param listener the listener to unregister.
     */
    public void removeCallListener(CallListener listener)
    {
        synchronized (callListeners) {
            callListeners.remove(listener);
        }
    }

    /**
     * Sets the mute state of the <code>Call</code>.
     *
     * Muting audio streams sent from the call is implementation specific and one of the possible
     * approaches to it is sending silence.
     *
     * @param call the <code>Call</code> whose mute state is to be set
     * @param mute <code>true</code> to mute the call streams being sent to <code>peers</code>; otherwise, <code>false</code>
     */
    public void setMute(Call call, boolean mute)
    {
        if (call instanceof MediaAwareCall)
            ((MediaAwareCall<?, ?, ?>) call).setMute(mute);
        else {
            /*
             * While throwing UnsupportedOperationException may be a possible approach,
             * putOnHold/putOffHold just do nothing when not supported so this implementation takes
             * inspiration from them.
             */
        }
    }

    /**
     * Creates a new <code>Recorder</code> which is to record the specified <code>Call</code> (into a file
     * which is to be specified when starting the returned <code>Recorder</code>).
     *
     * <code>AbstractOperationSetBasicTelephony</code> implements the described functionality for
     * <code>MediaAwareCall</code> only; otherwise, does nothing and just returns <code>null</code>.
     * </p>
     *
     * @param call the <code>Call</code> which is to be recorded by the returned <code>Recorder</code> when the
     * latter is started
     * @return a new <code>Recorder</code> which is to record the specified <code>call</code> (into a file
     * which is to be specified when starting the returned <code>Recorder</code>)
     * @throws OperationFailedException if anything goes wrong while creating the new <code>Recorder</code> for the specified
     * <code>call</code>
     * @see OperationSetBasicTelephony#createRecorder(Call)
     */
    public Recorder createRecorder(Call call)
            throws OperationFailedException
    {
        return (call instanceof MediaAwareCall<?, ?, ?>) ? ((MediaAwareCall<?, ?, ?>) call).createRecorder() : null;
    }
}
