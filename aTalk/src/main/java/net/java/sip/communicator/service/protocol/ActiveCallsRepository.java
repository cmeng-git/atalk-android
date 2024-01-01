/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package net.java.sip.communicator.service.protocol;

import net.java.sip.communicator.service.protocol.event.CallChangeAdapter;
import net.java.sip.communicator.service.protocol.event.CallChangeEvent;
import net.java.sip.communicator.service.protocol.event.CallEvent;

import org.atalk.impl.timberlog.TimberLog;

import java.util.ArrayList;
import java.util.Hashtable;
import java.util.Iterator;

import timber.log.Timber;

/**
 * Keeps a list of all calls currently active and maintained by this protocol provider. Offers
 * methods for finding a call by its ID, peer session and others. This class is meant for use by
 * protocol implementations and cannot be accessed from other bundles.
 *
 * @param <T> <code>Call</code>
 * @param <U> <code>OperationSetBasicTelephony</code>
 * @author Emil Ivov
 * @author Eng Chong Meng
 */
public abstract class ActiveCallsRepository<T extends Call, U extends OperationSetBasicTelephony<? extends ProtocolProviderService>>
        extends CallChangeAdapter
{
    /**
     * A table mapping call ids against call instances.
     */
    private final Hashtable<String, T> activeCalls = new Hashtable<>();

    /**
     * The operation set that created us. Instance is mainly used for firing events when necessary.
     */
    protected final U parentOperationSet;

    /**
     * Creates a new instance of this repository.
     *
     * @param opSet a reference to the <code>AbstractOperationSetBasicTelephony</code> extension that created
     * us.
     */
    public ActiveCallsRepository(U opSet)
    {
        this.parentOperationSet = opSet;
    }

    /**
     * Adds the specified call to the list of calls tracked by this repository.
     *
     * @param call CallSipImpl
     */
    public void addCall(T call)
    {
        activeCalls.put(call.getCallId(), call);
        call.addCallChangeListener(this);
    }

    /**
     * If <code>evt</code> indicates that the call has been ended we remove it from the repository.
     *
     * @param evt the <code>CallChangeEvent</code> instance containing the source calls and its old and new
     * state.
     */
    @Override
    public void callStateChanged(CallChangeEvent evt)
    {
        if (evt.getEventType().equals(CallChangeEvent.CALL_STATE_CHANGE)
                && evt.getNewValue().equals(CallState.CALL_ENDED)) {
            T sourceCall = activeCalls.remove(evt.getSourceCall().getCallId());
            Timber.log(TimberLog.FINER, "Removing call %s from the list of active calls because it entered an ENDED state", sourceCall);

            fireCallEvent(CallEvent.CALL_ENDED, sourceCall);
        }
    }

    /**
     * Returns an iterator over all currently active (non-ended) calls.
     *
     * @return an iterator over all currently active (non-ended) calls.
     */
    public Iterator<T> getActiveCalls()
    {
        synchronized (activeCalls) {
            /*
             * Given that we know the elements that will go into the new List, it is more optimal in
             * terms of memory and execution time to use ArrayList rather than LinkedList.
             */
            return new ArrayList<>(activeCalls.values()).iterator();
        }
    }

    /**
     * Returns the number of calls currently tracked by this repository.
     *
     * @return the number of calls currently tracked by this repository.
     */
    public int getActiveCallCount()
    {
        synchronized (activeCalls) {
            return activeCalls.size();
        }
    }

    /**
     * Creates and dispatches a <code>CallEvent</code> notifying registered listeners that an event with
     * id <code>eventID</code> has occurred on <code>sourceCall</code>.
     *
     * @param eventID the ID of the event to dispatch
     * @param sourceCall the call on which the event has occurred.
     */
    protected void fireCallEvent(int eventID, Call sourceCall)
    {
        fireCallEvent(eventID, sourceCall, null);
    }

    /**
     * Creates and dispatches a <code>CallEvent</code> notifying registered listeners that an event with
     * id <code>eventID</code> has occurred on <code>sourceCall</code>.
     * <p>
     * TODO The method is ugly because it can be implemented if <code>parentOperationSet</code> is an
     * <code>AbstractOperationSetBasicTelephony</code>. But after the move of the latter in the
     * <code>.service.protocol.media</code> package, it is not visible here.
     * </p>
     *
     * @param eventID the ID of the event to dispatch
     * @param sourceCall the call on which the event has occurred
     * @param cause the <code>CallChangeEvent</code>, if any, which is the cause that necessitated a new
     * <code>CallEvent</code> to be fired
     */
    protected abstract void fireCallEvent(int eventID, Call sourceCall, CallChangeEvent cause);
}
