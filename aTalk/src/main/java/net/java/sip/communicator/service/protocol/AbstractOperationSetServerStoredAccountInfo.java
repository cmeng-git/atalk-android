package net.java.sip.communicator.service.protocol;

import net.java.sip.communicator.service.protocol.event.ServerStoredDetailsChangeEvent;
import net.java.sip.communicator.service.protocol.event.ServerStoredDetailsChangeListener;

import java.util.*;

import timber.log.Timber;

/**
 * Represents a default implementation of {@link OperationSetServerStoredAccountInfo} in order to
 * make it easier for implementers to provide complete solutions while focusing on
 * implementation-specific details.
 *
 * @author Damian Minkov
 * @author Eng Chong Meng
 */
public abstract class AbstractOperationSetServerStoredAccountInfo implements
        OperationSetServerStoredAccountInfo
{
    /**
     * A list of listeners registered for <tt>ServerStoredDetailsChangeListener</tt>s.
     */
    private final List<ServerStoredDetailsChangeListener> serverStoredDetailsListeners = new ArrayList<ServerStoredDetailsChangeListener>();

    /**
     * Registers a ServerStoredDetailsChangeListener with this operation set so that it gets
     * notifications of details change.
     *
     * @param listener the <tt>ServerStoredDetailsChangeListener</tt> to register.
     */
    public void addServerStoredDetailsChangeListener(ServerStoredDetailsChangeListener listener)
    {
        synchronized (serverStoredDetailsListeners) {
            if (!serverStoredDetailsListeners.contains(listener))
                serverStoredDetailsListeners.add(listener);
        }
    }

    /**
     * Unregisters <tt>listener</tt> so that it won't receive any further notifications upon details
     * change.
     *
     * @param listener the <tt>ServerStoredDetailsChangeListener</tt> to unregister.
     */
    public void removeServerStoredDetailsChangeListener(ServerStoredDetailsChangeListener listener)
    {
        synchronized (serverStoredDetailsListeners) {
            serverStoredDetailsListeners.remove(listener);
        }
    }

    /**
     * Notify all listeners of the corresponding account detail change event.
     *
     * @param source the protocol provider service source
     * @param eventID the int ID of the event to dispatch
     * @param oldValue the value that the changed property had before the change occurred.
     * @param newValue the value that the changed property currently has (after the change has occurred).
     */
    public void fireServerStoredDetailsChangeEvent(ProtocolProviderService source, int eventID,
            Object oldValue, Object newValue)
    {
        ServerStoredDetailsChangeEvent evt = new ServerStoredDetailsChangeEvent(source, eventID,
                oldValue, newValue);

        Collection<ServerStoredDetailsChangeListener> listeners;
        synchronized (serverStoredDetailsListeners) {
            listeners = new ArrayList<ServerStoredDetailsChangeListener>(
                    serverStoredDetailsListeners);
        }

        Timber.d("Dispatching a Contact Property Change Event to %s listeners. Evt = %s",
                listeners.size(), evt);

        for (ServerStoredDetailsChangeListener listener : listeners)
            listener.serverStoredDetailsChanged(evt);
    }
}
