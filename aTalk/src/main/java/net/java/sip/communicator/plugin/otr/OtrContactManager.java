/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license.
 * See terms of license at gnu.org.
 */
package net.java.sip.communicator.plugin.otr;

import net.java.sip.communicator.service.protocol.Contact;
import net.java.sip.communicator.service.protocol.ContactResource;
import net.java.sip.communicator.service.protocol.ProtocolProviderService;
import net.java.sip.communicator.util.Logger;

import org.osgi.framework.ServiceEvent;
import org.osgi.framework.ServiceListener;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

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
 */
public class OtrContactManager implements ServiceListener {
	/**
	 * A map that caches OtrContacts to minimize memory usage.
	 */
	private static final Map<Contact, List<OtrContact>> contactsMap = new ConcurrentHashMap<>();
	/**
	 * The logger
	 */
	private final Logger logger = Logger.getLogger(OtrContactManager.class);

	/**
	 * Gets the <tt>OtrContact</tt> that represents this
	 * [Contact, ContactResource] pair from the cache. If such pair does not
	 * still exist it is then created and cached for further usage.
	 *
	 * @param contact the <tt>Contact</tt> that the returned OtrContact represents.
	 * @param resource the <tt>ContactResource</tt> that the returned OtrContact represents.
	 * @return The <tt>OtrContact</tt> that represents this [Contact, ContactResource] pair.
	 */
	public static OtrContact getOtrContact(Contact contact, ContactResource resource) {
		if (contact == null)
			return null;

		List<OtrContact> otrContactsList = contactsMap.get(contact);
		if (otrContactsList != null) {
			for (OtrContact otrContact : otrContactsList) {
				if (resource != null && resource.equals(otrContact.resource))
					return otrContact;
			}

			OtrContact otrContact = new OtrContact(contact, resource);
			synchronized (otrContactsList) {
				while (!otrContactsList.contains(otrContact)) {
					otrContactsList.add(otrContact);
				}
			}
			return otrContact;
		}
		else {
			synchronized (contactsMap) {
				while (!contactsMap.containsKey(contact)) {
					otrContactsList = new ArrayList<OtrContact>();
					contactsMap.put(contact, otrContactsList);
				}
			}
			return getOtrContact(contact, resource);
		}
	}

	/**
	 * Cleans up unused cached up Contacts.
	 */
	public void serviceChanged(ServiceEvent event) {
		Object service = OtrActivator.bundleContext.getService(event.getServiceReference());

		if (!(service instanceof ProtocolProviderService))
			return;

		if (event.getType() == ServiceEvent.UNREGISTERING) {
			if (logger.isDebugEnabled()) {
				logger.debug("Unregistering a ProtocolProviderService, cleaning" + " OTR's Contact to OtrContact map");
			}

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
	public static class OtrContact {
		public final Contact contact;
		public final ContactResource resource;

		private OtrContact(Contact contact, ContactResource resource) {
			this.contact = contact;
			this.resource = resource;
		}

		public boolean equals(Object obj) {
			if (this == obj)
				return true;

			if (!(obj instanceof OtrContact))
				return false;

			OtrContact other = (OtrContact) obj;
			if (this.contact != null && this.contact.equals(other.contact)) {
				if (this.resource != null && resource.equals(other.resource))
					return true;
				if (this.resource == null && other.resource == null)
					return true;
				return false;
			}
			return false;
		}

		public int hashCode() {
			int result = 17;

			result = 31 * result + (contact == null ? 0 : contact.hashCode());
			result = 31 * result + (resource == null ? 0 : resource.hashCode());
			return result;
		}
	}
}
