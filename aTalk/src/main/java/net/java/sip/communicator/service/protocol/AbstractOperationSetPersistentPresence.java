/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package net.java.sip.communicator.service.protocol;

import java.beans.PropertyChangeEvent;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Vector;

import net.java.sip.communicator.service.protocol.event.ContactPresenceStatusChangeEvent;
import net.java.sip.communicator.service.protocol.event.ContactPresenceStatusListener;
import net.java.sip.communicator.service.protocol.event.ContactPropertyChangeEvent;
import net.java.sip.communicator.service.protocol.event.ProviderPresenceStatusChangeEvent;
import net.java.sip.communicator.service.protocol.event.ProviderPresenceStatusListener;
import net.java.sip.communicator.service.protocol.event.ServerStoredGroupEvent;
import net.java.sip.communicator.service.protocol.event.ServerStoredGroupListener;
import net.java.sip.communicator.service.protocol.event.SubscriptionEvent;
import net.java.sip.communicator.service.protocol.event.SubscriptionListener;
import net.java.sip.communicator.service.protocol.event.SubscriptionMovedEvent;

import org.atalk.impl.timberlog.TimberLog;
import org.jxmpp.jid.Jid;

import timber.log.Timber;

/**
 * Represents a default implementation of <code>OperationSetPersistentPresence</code> in order to make
 * it easier for implementers to provide complete solutions while focusing on implementation-specific details.
 *
 * @author Lubomir Marinov
 * @author Eng Chong Meng
 */
public abstract class AbstractOperationSetPersistentPresence<T extends ProtocolProviderService>
        implements OperationSetPersistentPresence
{
    /**
     * A list of listeners registered for <code>ContactPresenceStatusChangeEvent</code>s.
     */
    private final List<ContactPresenceStatusListener> contactPresenceStatusListeners = new Vector<>();

    /**
     * The provider that created us.
     */
    protected final T mPPS;

    /**
     * A list of listeners registered for <code>ProviderPresenceStatusChangeEvent</code>s.
     */
    private final List<ProviderPresenceStatusListener> providerPresenceStatusListeners = new Vector<>();

    /**
     * A list of listeners registered for <code>ServerStoredGroupChangeEvent</code>s.
     */
    private final List<ServerStoredGroupListener> serverStoredGroupListeners = new Vector<>();

    /**
     * The list of listeners interested in <code>SubscriptionEvent</code>s.
     */
    private final List<SubscriptionListener> subscriptionListeners = new Vector<>();

    /**
     * Initializes a new <code>AbstractOperationSetPersistentPresence</code> instance created by a
     * specific <code>ProtocolProviderService</code> .
     *
     * @param pps the <code>ProtocolProviderService</code> which created the new instance
     */
    protected AbstractOperationSetPersistentPresence(T pps)
    {
        mPPS = pps;
    }

    /**
     * Implementation of the corresponding ProtocolProviderService method.
     *
     * @param listener a presence status listener.
     */
    public void addContactPresenceStatusListener(ContactPresenceStatusListener listener)
    {
        synchronized (contactPresenceStatusListeners) {
            if (!contactPresenceStatusListeners.contains(listener))
                contactPresenceStatusListeners.add(listener);
        }
    }

    /**
     * Adds a listener that would receive events upon changes of the provider presence status.
     *
     * @param listener the listener to register for changes in our PresenceStatus.
     */
    public void addProviderPresenceStatusListener(ProviderPresenceStatusListener listener)
    {
        synchronized (providerPresenceStatusListeners) {
            if (!providerPresenceStatusListeners.contains(listener))
                providerPresenceStatusListeners.add(listener);
        }
    }

    /**
     * Registers a listener that would receive events upon changes in server stored groups.
     *
     * @param listener a ServerStoredGroupChangeListener impl that would receive events upon group changes.
     */
    public void addServerStoredGroupChangeListener(ServerStoredGroupListener listener)
    {
        synchronized (serverStoredGroupListeners) {
            if (!serverStoredGroupListeners.contains(listener))
                serverStoredGroupListeners.add(listener);
        }
    }

    public void addSubscriptionListener(SubscriptionListener listener)
    {
        synchronized (subscriptionListeners) {
            if (!subscriptionListeners.contains(listener))
                subscriptionListeners.add(listener);
        }
    }

    /**
     * Notifies all registered listeners of the new event.
     *
     * @param source the contact that has caused the event.
     * @param jid the contact FullJid (Jid for chatRoom) that has caused the event.
     * @param parentGroup the group that contains the source contact.
     * @param oldValue the status that the source contact detained before changing it.
     * @param newValue the status defined in source.getPresenceStatus().
     * @param isResourceChange true if event is for resource change.
     */
    public void fireContactPresenceStatusChangeEvent(Contact source, Jid jid, ContactGroup parentGroup,
            PresenceStatus oldValue, PresenceStatus newValue, boolean isResourceChange, boolean capsExtension)
    {
        ContactPresenceStatusChangeEvent evt = new ContactPresenceStatusChangeEvent(source, jid,
                mPPS, parentGroup, oldValue, newValue, isResourceChange, capsExtension);

        Collection<ContactPresenceStatusListener> listeners;
        synchronized (contactPresenceStatusListeners) {
            listeners = new ArrayList<>(contactPresenceStatusListeners);
        }

        // Timber.w("Dispatching contact status change for %s: %s => %s listeners\n%s", jid, newValue.getStatusName(), listeners.size(), listeners);
        for (ContactPresenceStatusListener listener : listeners) {
            listener.contactPresenceStatusChanged(evt);
        }
    }

    /**
     * Notify all subscription listeners of the corresponding contact property change event.
     *
     * @param source the ContactJabberImpl instance that this event is pertaining to.
     * @param eventID the String ID of the event to dispatch
     * @param oldValue the value that the changed property had before the change occurred.
     * @param newValue the value that the changed property currently has (after the change has occurred).
     */
    public void fireContactPropertyChangeEvent(Contact source, String eventID, Object oldValue, Object newValue)
    {
        ContactPropertyChangeEvent evt = new ContactPropertyChangeEvent(source, eventID, oldValue, newValue);

        Collection<SubscriptionListener> listeners;
        synchronized (subscriptionListeners) {
            listeners = new ArrayList<>(subscriptionListeners);
        }
        // Timber.d("Dispatching a Contact Property Change Event to %d listeners. Evt = %S", listeners.size(), evt);
        for (SubscriptionListener listener : listeners)
            listener.contactModified(evt);
    }

    /**
     * Notify all provider presence listeners of the corresponding event change
     *
     * @param oldValue the status our stack had so far
     * @param newValue the status we have from now on
     */
    protected void fireProviderStatusChangeEvent(PresenceStatus oldValue, PresenceStatus newValue)
    {
        ProviderPresenceStatusChangeEvent evt = new ProviderPresenceStatusChangeEvent(mPPS, oldValue, newValue);

        Collection<ProviderPresenceStatusListener> listeners;
        synchronized (providerPresenceStatusListeners) {
            listeners = new ArrayList<>(providerPresenceStatusListeners);
        }

        Timber.log(TimberLog.FINER, "Dispatching Provider Status Change. Listeners = %d evt = %s", listeners.size(), evt);
        for (ProviderPresenceStatusListener listener : listeners)
            listener.providerStatusChanged(evt);
    }

    /**
     * Notify all provider presence listeners that a new status message has been set.
     *
     * @param oldStatusMessage the status message our stack had so far
     * @param newStatusMessage the status message we have from now on
     */
    protected void fireProviderStatusMessageChangeEvent(String oldStatusMessage, String newStatusMessage)
    {
        PropertyChangeEvent evt = new PropertyChangeEvent(mPPS,
                ProviderPresenceStatusListener.STATUS_MESSAGE, oldStatusMessage, newStatusMessage);

        Collection<ProviderPresenceStatusListener> listeners;
        synchronized (providerPresenceStatusListeners) {
            listeners = new ArrayList<>(providerPresenceStatusListeners);
        }
        Timber.d("Dispatching  stat. msg change. Listeners = %d evt = %s", listeners.size(), evt);

        for (ProviderPresenceStatusListener listener : listeners)
            listener.providerStatusMessageChanged(evt);
    }

    /**
     * Notifies all registered listeners of the new event.
     *
     * @param source the contact that has caused the event.
     * @param eventID an identifier of the event to dispatch.
     */
    protected void fireServerStoredGroupEvent(ContactGroup source, int eventID)
    {
        ServerStoredGroupEvent evt = new ServerStoredGroupEvent(source, eventID,
                source.getParentContactGroup(), mPPS, this);

        Iterable<ServerStoredGroupListener> listeners;
        synchronized (serverStoredGroupListeners) {
            listeners = new ArrayList<>(serverStoredGroupListeners);
        }

        for (ServerStoredGroupListener listener : listeners)
            switch (eventID) {
                case ServerStoredGroupEvent.GROUP_CREATED_EVENT:
                    listener.groupCreated(evt);
                    break;
                case ServerStoredGroupEvent.GROUP_RENAMED_EVENT:
                    listener.groupNameChanged(evt);
                    break;
                case ServerStoredGroupEvent.GROUP_REMOVED_EVENT:
                    listener.groupRemoved(evt);
                    break;
            }
    }

    /**
     * Notifies all registered listeners of the new event.
     *
     * @param source the contact that has caused the event.
     * @param parentGroup the group that contains the source contact.
     * @param eventID an identifier of the event to dispatch.
     */
    public void fireSubscriptionEvent(Contact source, ContactGroup parentGroup, int eventID)
    {
        fireSubscriptionEvent(source, parentGroup, eventID, SubscriptionEvent.ERROR_UNSPECIFIED, null);
    }

    public void fireSubscriptionEvent(Contact source, ContactGroup parentGroup, int eventID,
            int errorCode, String errorReason)
    {
        SubscriptionEvent evt = new SubscriptionEvent(source, mPPS, parentGroup, eventID,
                errorCode, errorReason);

        Collection<SubscriptionListener> listeners;
        synchronized (subscriptionListeners) {
            listeners = new ArrayList<>(subscriptionListeners);
        }

        Timber.log(TimberLog.FINER, "Dispatching a Subscription Event to %d listeners. Evt = %s", listeners.size(), evt);
        for (SubscriptionListener listener : listeners)
            switch (eventID) {
                case SubscriptionEvent.SUBSCRIPTION_CREATED:
                    listener.subscriptionCreated(evt);
                    break;
                case SubscriptionEvent.SUBSCRIPTION_FAILED:
                    listener.subscriptionFailed(evt);
                    break;
                case SubscriptionEvent.SUBSCRIPTION_REMOVED:
                    listener.subscriptionRemoved(evt);
                    break;
                case SubscriptionEvent.SUBSCRIPTION_RESOLVED:
                    listener.subscriptionResolved(evt);
                    break;
            }
    }

    /**
     * Notifies all registered listeners of the new event.
     *
     * @param source the contact that has been moved..
     * @param oldParent the group where the contact was located before being moved.
     * @param newParent the group where the contact has been moved.
     */
    public void fireSubscriptionMovedEvent(Contact source, ContactGroup oldParent, ContactGroup newParent)
    {
        SubscriptionMovedEvent evt = new SubscriptionMovedEvent(source, mPPS, oldParent, newParent);

        Collection<SubscriptionListener> listeners;
        synchronized (subscriptionListeners) {
            listeners = new ArrayList<>(subscriptionListeners);
        }
        Timber.d("Dispatching a Subscription Event to %d listeners. Evt = %s", listeners.size(), evt);

        for (SubscriptionListener listener : listeners)
            listener.subscriptionMoved(evt);
    }

    /**
     * Removes the specified listener so that it won't receive any further updates on contact
     * presence status changes
     *
     * @param listener the listener to remove.
     */
    public void removeContactPresenceStatusListener(ContactPresenceStatusListener listener)
    {
        synchronized (contactPresenceStatusListeners) {
            contactPresenceStatusListeners.remove(listener);
        }
    }

    /**
     * Unregisters the specified listener so that it does not receive further events upon changes in
     * local presence status.
     *
     * @param listener ProviderPresenceStatusListener
     */
    public void removeProviderPresenceStatusListener(ProviderPresenceStatusListener listener)
    {
        synchronized (providerPresenceStatusListeners) {
            providerPresenceStatusListeners.remove(listener);
        }
    }

    /**
     * Removes the specified group change listener so that it won't receive any further events.
     *
     * @param listener the ServerStoredGroupChangeListener to remove
     */
    public void removeServerStoredGroupChangeListener(ServerStoredGroupListener listener)
    {
        synchronized (serverStoredGroupListeners) {
            serverStoredGroupListeners.remove(listener);
        }
    }

    /**
     * Removes the specified subscription listener.
     *
     * @param listener the listener to remove.
     */
    public void removeSubscriptionListener(SubscriptionListener listener)
    {
        synchronized (subscriptionListeners) {
            subscriptionListeners.remove(listener);
        }
    }

    /**
     * Sets the display name for <code>contact</code> to be <code>newName</code>.
     * <p>
     *
     * @param contact the <code>Contact</code> that we are renaming
     * @param newName a <code>String</code> containing the new display name for <code>metaContact</code>.
     * @throws IllegalArgumentException if <code>contact</code> is not an instance that belongs to the underlying implementation.
     */
    public void setDisplayName(Contact contact, String newName)
            throws IllegalArgumentException
    {
    }

    /**
     * Returns the protocol specific contact instance representing the local user or null if it is not supported.
     *
     * @return the Contact that the Provider implementation is communicating on behalf of or null if not supported.
     */
    public Contact getLocalContact()
    {
        return null;
    }
}
