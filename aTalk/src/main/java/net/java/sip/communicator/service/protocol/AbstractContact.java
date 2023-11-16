/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package net.java.sip.communicator.service.protocol;

import net.java.sip.communicator.service.protocol.event.ContactResourceEvent;
import net.java.sip.communicator.service.protocol.event.ContactResourceListener;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.Objects;

/**
 * An abstract base implementation of the {@link Contact} interface which is to aid implementers.
 *
 * @author Lyubomir Marinov
 * @author Eng Chong Meng
 */
public abstract class AbstractContact implements Contact
{
    /**
     * The list of <code>ContactResourceListener</code>-s registered in this contact.
     */
    final private Collection<ContactResourceListener> resourceListeners = new ArrayList<>();

    /**
     * A reference copy of last fetch contact activity. The value is set to -1 when contact is online
     * so a new lastActivity is fetched when the user is offline again
     */
    private long mLastSeen = -1;

    @Override
    public boolean equals(Object obj)
    {
        if (obj == null)
            return false;
        else if (obj == this)
            return true;
        else if (!obj.getClass().equals(getClass()))
            return false;
        else {
            Contact contact = (Contact) obj;
            ProtocolProviderService protocolProvider = contact.getProtocolProvider();
            ProtocolProviderService thisProtocolProvider = getProtocolProvider();

            if (Objects.equals(protocolProvider, thisProtocolProvider)) {
                String address = contact.getAddress();
                String thisAddress = getAddress();

                return Objects.equals(address, thisAddress);
            }
            else
                return false;
        }
    }

    @Override
    public int hashCode()
    {
        int hashCode = 0;

        ProtocolProviderService protocolProvider = getProtocolProvider();
        if (protocolProvider != null)
            hashCode += protocolProvider.hashCode();

        String address = getAddress();
        if (address != null)
            hashCode += address.hashCode();

        return hashCode;
    }

    /**
     * Indicates if this contact supports resources.
     * <p>
     * This default implementation indicates no support for contact resources.
     *
     * @return <code>true</code> if this contact supports resources, <code>false</code> otherwise
     */
    public boolean supportResources()
    {
        return false;
    }

    /**
     * Returns a collection of resources supported by this contact or null if it doesn't support resources.
     * <p>
     * This default implementation indicates no support for contact resources.
     *
     * @return a collection of resources supported by this contact or null if it doesn't support resources
     */
    public Collection<ContactResource> getResources()
    {
        return null;
    }

    /**
     * Adds the given <code>ContactResourceListener</code> to listen for events related to contact resources changes.
     *
     * @param l the <code>ContactResourceListener</code> to add
     */
    public void addResourceListener(ContactResourceListener l)
    {
        synchronized (resourceListeners) {
            resourceListeners.add(l);
        }
    }

    /**
     * Removes the given <code>ContactResourceListener</code> listening for events related to contact resources changes.
     *
     * @param l the <code>ContactResourceListener</code> to remove
     */
    public void removeResourceListener(ContactResourceListener l)
    {
        synchronized (resourceListeners) {
            resourceListeners.remove(l);
        }
    }

    /**
     * Notifies all registered <code>ContactResourceListener</code>s that an event has occurred.
     *
     * @param event the <code>ContactResourceEvent</code> to fire notification for
     */
    protected void fireContactResourceEvent(ContactResourceEvent event)
    {
        Collection<ContactResourceListener> listeners;
        synchronized (resourceListeners) {
            listeners = new ArrayList<>(resourceListeners);
        }

        Iterator<ContactResourceListener> listenersIter = listeners.iterator();
        while (listenersIter.hasNext()) {
            if (event.getEventType() == ContactResourceEvent.RESOURCE_ADDED)
                listenersIter.next().contactResourceAdded(event);
            else if (event.getEventType() == ContactResourceEvent.RESOURCE_REMOVED)
                listenersIter.next().contactResourceRemoved(event);
            else if (event.getEventType() == ContactResourceEvent.RESOURCE_MODIFIED)
                listenersIter.next().contactResourceModified(event);
        }
    }

    /**
     * Returns the same as <code>getAddress</code> function.
     *
     * @return the address of the contact.
     */
    public String getPersistableAddress()
    {
        return getAddress();
    }

    /**
     * Whether contact is mobile one. Logged in only from mobile device.
     *
     * @return whether contact is mobile one.
     */
    public boolean isMobile()
    {
        return false;
    }

    /**
     * Set the lastActivity for the contact
     * @param dateTime Date of the contact last seen online
     */
    public void setLastActiveTime(long dateTime)
    {
        mLastSeen = dateTime;
    }

    /**
     * Get the contact last seen activityTime
     * @return contact last seen activityTime
     */
    public long getLastActiveTime()
    {
        return mLastSeen;
    }
}
