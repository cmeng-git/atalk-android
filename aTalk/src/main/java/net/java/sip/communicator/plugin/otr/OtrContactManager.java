/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license.
 * See terms of license at gnu.org.
 */
package net.java.sip.communicator.plugin.otr;

import net.java.sip.communicator.service.protocol.*;

import org.osgi.framework.ServiceEvent;
import org.osgi.framework.ServiceListener;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

import timber.log.Timber;

/**
 * The OtrContactManager is used for accessing <tt>OtrContact</tt>s in a static way.
 * <p/>
 * The <tt>OtrContact</tt> class is just a wrapper of [Contact, ContactResource]
 * pairs. Its purpose is for the otr plugin to be able to create different
 * <tt>Session</tt>s for every ContactResource that a Contact has.
 * <p/>
 * Currently, only the Jabber protocol supports ContactResources.
 *
 * @author Marin Dzhigarov
 * @author Eng Chong Meng
 */
public class OtrContactManager implements ServiceListener
{
    /**
     * A map that caches OtrContacts to minimize memory usage.
     */
    private static final Map<Contact, List<OtrContact>> contactsMap = new ConcurrentHashMap<>();

    /**
     * Gets the <tt>OtrContact</tt> that represents this
     * [Contact, ContactResource] pair from the cache. If such pair does not
     * still exist it is then created and cached for further usage.
     *
     * @param contact the <tt>Contact</tt> that the returned OtrContact represents.
     * @param resource the <tt>ContactResource</tt> that the returned OtrContact represents.
     * @return The <tt>OtrContact</tt> that represents this [Contact, ContactResource] pair.
     */
    public static OtrContact getOtrContact(Contact contact, ContactResource resource)
    {
        if (contact == null)
            return null;

        List<OtrContact> otrContactsList = contactsMap.get(contact);
        if (otrContactsList != null) {
            for (OtrContact otrContact : otrContactsList) {
                if (resource != null && resource.equals(otrContact.resource))
                    return otrContact;
            }

            // Create and cache new if none found
            OtrContact otrContact = new OtrContact(contact, resource);
            synchronized (otrContactsList) {
                otrContactsList.add(otrContact);
            }
            return otrContact;
        }
        else {
            synchronized (contactsMap) {
                if (!contactsMap.containsKey(contact)) {
                    otrContactsList = new ArrayList<>();
                    contactsMap.put(contact, otrContactsList);
                }
            }
            return getOtrContact(contact, resource);
        }
    }

    /**
     * Cleans up unused cached up Contacts.
     */
    public void serviceChanged(ServiceEvent event)
    {
        Object service = OtrActivator.bundleContext.getService(event.getServiceReference());
        if (!(service instanceof ProtocolProviderService))
            return;

        if (event.getType() == ServiceEvent.UNREGISTERING) {
            Timber.d("Unregistering a ProtocolProviderService, cleaning OTR's Contact to OtrContact map");

            ProtocolProviderService provider = (ProtocolProviderService) service;
            synchronized (contactsMap) {
                Iterator<Contact> i = contactsMap.keySet().iterator();

                while (i.hasNext()) {
                    if (provider.equals(i.next().getProtocolProvider()))
                        i.remove();
                }
            }
        }
    }

    /**
     * The <tt>OtrContact</tt> class is just a wrapper of
     * [Contact, ContactResource] pairs. Its purpose is for the otr plugin to be
     * able to create different <tt>Session</tt>s for every ContactResource that
     * a Contact has.
     *
     * @author Marin Dzhigarov
     */
    public static class OtrContact
    {
        public final Contact contact;
        public final ContactResource resource;

        private OtrContact(Contact contact, ContactResource resource)
        {
            this.contact = contact;
            this.resource = resource;
        }

        public boolean equals(Object obj)
        {
            if (this == obj)
                return true;

            if (!(obj instanceof OtrContact))
                return false;

            OtrContact other = (OtrContact) obj;
            if ((this.contact != null) && this.contact.equals(other.contact)) {
                // cmeng: must only compare resourceName, other resource parameters may not be the same
                // e.g. presenceStatus - incoming otrContact can be offline?
                if ((this.resource != null) && (other.resource != null)
                        && this.resource.getResourceName().equals(other.resource.getResourceName()))
                    return true;
                return ((this.resource == null) && (other.resource == null));
            }
            return false;
        }

        public int hashCode()
        {
            int result = 17;

            result = 31 * result + (contact == null ? 0 : contact.hashCode());
            result = 31 * result + (resource == null ? 0 : resource.hashCode());
            return result;
        }
    }
}
