/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package net.java.sip.communicator.impl.protocol.jabber;

import net.java.sip.communicator.service.protocol.Contact;
import net.java.sip.communicator.service.protocol.OperationSetServerStoredContactInfo;
import net.java.sip.communicator.service.protocol.ServerStoredDetails.GenericDetail;

import java.util.*;

import timber.log.Timber;

/**
 * @author Damian Minkov
 * @author Eng Chong Meng
 */
public class OperationSetServerStoredContactInfoJabberImpl implements OperationSetServerStoredContactInfo
{
    private InfoRetriever infoRetriever;

    /**
     * If we got several listeners for the same contact lets retrieve once but deliver result to all.
     */
    private final Hashtable<String, List<DetailsResponseListener>> listenersForDetails = new Hashtable<>();

    protected OperationSetServerStoredContactInfoJabberImpl(InfoRetriever infoRetriever)
    {
        this.infoRetriever = infoRetriever;
    }

    /**
     * Returns the info retriever.
     *
     * @return the info retriever.
     */
    InfoRetriever getInfoRetriever()
    {
        return infoRetriever;
    }

    /**
     * returns the user details from the specified class or its descendants the class is one from
     * the net.java.sip.communicator.service.protocol.ServerStoredDetails or implemented one in the
     * operation set for the user info
     *
     * @param contact Contact
     * @param detailClass Class
     * @return Iterator
     */
    public <T extends GenericDetail> Iterator<T> getDetailsAndDescendants(Contact contact, Class<T> detailClass)
    {
        if (isPrivateMessagingContact(contact) || !(contact instanceof ContactJabberImpl)) {
            return new LinkedList<T>().iterator();
        }

        List<GenericDetail> details = infoRetriever.getUserDetails(contact.getJid().asBareJid());
        List<T> result = new LinkedList<>();

        if (details == null)
            return result.iterator();

        for (GenericDetail item : details) {
            if (detailClass.isInstance(item)) {
                @SuppressWarnings("unchecked")
                T t = (T) item;
                result.add(t);
            }
        }
        return result.iterator();
    }

    /**
     * returns the user details from the specified class exactly that class not its descendants
     *
     * @param contact Contact
     * @param detailClass Class
     * @return Iterator
     */
    public Iterator<GenericDetail> getDetails(Contact contact, Class<? extends GenericDetail> detailClass)
    {
        if (isPrivateMessagingContact(contact) || !(contact instanceof ContactJabberImpl))
            return new LinkedList<GenericDetail>().iterator();

        List<GenericDetail> details = infoRetriever.getUserDetails(contact.getJid().asBareJid());
        List<GenericDetail> result = new LinkedList<>();

        if (details == null)
            return result.iterator();

        for (GenericDetail item : details)
            if (detailClass.equals(item.getClass()))
                result.add(item);

        return result.iterator();
    }

    /**
     * request the full info for the given uin waits and return this details
     *
     * @param contact The requester Contact
     * @return Iterator
     */
    public Iterator<GenericDetail> getAllDetailsForContact(Contact contact)
    {
        if (isPrivateMessagingContact(contact) || !(contact instanceof ContactJabberImpl))
            return Collections.emptyIterator();

        List<GenericDetail> details = infoRetriever.getUserDetails(contact.getJid().asBareJid());

        if (details == null)
            return Collections.emptyIterator();
        else
            return new LinkedList<>(details).iterator();
    }

    /**
     * Requests all details for the specified contact. Always fetch online info: do not
     * use cached info as any contact vCard changes is not event triggered; user must logout and
     * login to retrieve any new update from an online contact
     *
     * @param contact the specified contact or account
     * @return Iterator over all details existing for the specified contact.
     */
    public Iterator<GenericDetail> requestAllDetailsForContact(final Contact contact, DetailsResponseListener listener)
    {
        if (!(contact instanceof ContactJabberImpl)) {
            return null;
        }

        synchronized (listenersForDetails) {
            List<DetailsResponseListener> ls = listenersForDetails.get(contact.getAddress());

            boolean isFirst = false;
            if (ls == null) {
                ls = new ArrayList<>();
                isFirst = true;
                listenersForDetails.put(contact.getAddress(), ls);
            }

            if (!ls.contains(listener))
                ls.add(listener);

            // there is already scheduled retrieve, will deliver at listener.
            if (!isFirst)
                return null;
        }

        new Thread(() -> {
            List<GenericDetail> result = infoRetriever.retrieveDetails(contact.getJid().asBareJid());

            List<DetailsResponseListener> listeners;
            synchronized (listenersForDetails) {
                listeners = listenersForDetails.remove(contact.getAddress());
            }

            if (listeners == null || result == null)
                return;

            for (DetailsResponseListener l : listeners) {
                try {
                    l.detailsRetrieved(result.iterator());
                } catch (Throwable t) {
                    Timber.e(t, "Error delivering for retrieved details");
                }
            }
        }, getClass().getName() + ".RetrieveDetails").start();

        // return null as there is no cache and we will try to retrieve
        return null;
    }

    /**
     * Checks whether a contact is a private messaging contact for chat rooms.
     *
     * @param contact the contact to check.
     * @return <tt>true</tt> if contact is private messaging contact for chat room.
     */
    private boolean isPrivateMessagingContact(Contact contact)
    {
        if (contact instanceof VolatileContactJabberImpl)
            return ((VolatileContactJabberImpl) contact).isPrivateMessagingContact();
        return false;
    }
}
