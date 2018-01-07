/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 * 
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package net.java.sip.communicator.impl.protocol.jabber;

import net.java.sip.communicator.impl.protocol.jabber.extensions.geolocation.*;
import net.java.sip.communicator.service.protocol.*;
import net.java.sip.communicator.service.protocol.event.*;
import net.java.sip.communicator.util.Logger;

import org.jivesoftware.smack.SmackException.NotConnectedException;
import org.jivesoftware.smack.StanzaListener;
import org.jivesoftware.smack.filter.StanzaExtensionFilter;
import org.jivesoftware.smack.packet.Stanza;
import org.jivesoftware.smackx.geoloc.packet.GeoLocation;

import java.util.*;

/**
 * The Jabber implementation of an OperationSetGeolocation done with the XEP-0080: User Geolocation.
 * This class broadcast our own geolocation and manage the geolocation status of our buddies.
 * <p>
 * Currently, we send geolocation message in presence. We passively listen to buddies geolocation
 * when their presence are updated.
 *
 * @author Guillaume Schreiner
 * @author Eng Chong Meng
 */
public class OperationSetGeolocationJabberImpl implements OperationSetGeolocation
{
	/**
	 * Our logger.
	 */
	private static final Logger logger = Logger.getLogger(OperationSetGeolocationJabberImpl.class);

	/**
	 * The list of Geolocation status listeners interested in receiving presence notifications of
	 * changes in geolocation of contacts in our contact list.
	 */
	private final List<GeolocationListener> geolocationContactsListeners = new Vector<>();

	/**
	 * A callback to the provider
	 */
	private final ProtocolProviderServiceJabberImpl jabberProvider;

	/**
	 * A callback to the persistent presence operation set.
	 */
	private final OperationSetPersistentPresence opsetprez;

	/**
	 * Constructor
	 *
	 * @param provider
	 * 		<tt>ProtocolProviderServiceJabberImpl</tt>
	 */
	public OperationSetGeolocationJabberImpl(ProtocolProviderServiceJabberImpl provider)
	{
		this.jabberProvider = provider;
		this.opsetprez = provider.getOperationSet(OperationSetPersistentPresence.class);
		this.jabberProvider.addRegistrationStateChangeListener(new RegistrationStateListener());
	}

	/**
	 * Broadcast our current Geolocation trough this provider using a Jabber presence message.
	 *
	 * @param geolocation
	 * 		our current Geolocation ready to be sent
	 */
	public void publishGeolocation(Map<String, String> geolocation)
	{
		GeolocationPresence myGeolocPrez = new GeolocationPresence(opsetprez);
		GeoLocation geolocExt = GeolocationJabberUtils.convertMapToExtension(geolocation);
		myGeolocPrez.setGeolocationExtention(geolocExt);
		try {
			this.jabberProvider.getConnection().sendStanza(myGeolocPrez.getGeolocPresence());
		}
		catch (NotConnectedException | InterruptedException e) {
			e.printStackTrace();
		}
	}

	/**
	 * Retrieve the geolocation of the given contact.
	 * <p>
	 * Note: Currently not implemented because we can not actively poll the server for the presence
	 * of a given contact ?
	 * <p>
	 *
	 * @param contactIdentifier
	 * 		the <tt>Contact</tt> we want to retrieve its geolocation by its identifier.
	 * @return the <tt>Geolocation</tt> of the contact.
	 */
	public Map<String, String> queryContactGeolocation(String contactIdentifier)
	{
		/** @todo implement queryContactGeolocation() */
		return null;
	}

	/**
	 * Registers a listener that would get notifications any time a contact refreshed its
	 * geolocation via Presence.
	 *
	 * @param listener
	 * 		the <tt>ContactGeolocationPresenceListener</tt> to register
	 */
	public void addGeolocationListener(GeolocationListener listener)
	{
		synchronized (geolocationContactsListeners) {
			geolocationContactsListeners.add(listener);
		}
	}

	/**
	 * Remove a listener that would get notifications any time a contact refreshed its geolocation
	 * via Presence.
	 *
	 * @param listener
	 * 		the <tt>ContactGeolocationPresenceListener</tt> to register
	 */
	public void removeGeolocationListener(GeolocationListener listener)
	{
		synchronized (geolocationContactsListeners) {
			geolocationContactsListeners.remove(listener);
		}
	}

	/**
	 * Our listener that will tell us when we're registered to server and we are ready to launch
	 * the listener for GeolocationPacketExtension packets
	 */
	private class RegistrationStateListener implements RegistrationStateChangeListener
	{
		/**
		 * The method is called by a ProtocolProvider implementation whenever a change in the
		 * registration state of the corresponding provider had occurred.
		 *
		 * @param evt
		 * 		ProviderStatusChangeEvent the event describing the status change.
		 */
		public void registrationStateChanged(RegistrationStateChangeEvent evt)
		{
			if (evt.getNewState() == RegistrationState.REGISTERED) {
				StanzaExtensionFilter filterGeoloc
						= new StanzaExtensionFilter(GeoLocation.ELEMENT, GeoLocation.NAMESPACE);

				// launch the listener
				try {
					jabberProvider.getConnection().addAsyncStanzaListener(
							new GeolocationPresencePacketListener(), filterGeoloc);
				}
				catch (Exception e) {
					logger.error(e);
				}
			}
			else if (evt.getNewState() == RegistrationState.UNREGISTERED
					|| evt.getNewState() == RegistrationState.AUTHENTICATION_FAILED
					|| evt.getNewState() == RegistrationState.CONNECTION_FAILED) {
			}
		}
	}

	/**
	 * This class listen to GeolocationExtension into Presence Packet. If GeolocationExtension is
	 * found, an event is sent.
	 *
	 * @author Guillaume Schreiner
	 */
	private class GeolocationPresencePacketListener implements StanzaListener
	{
		/**
		 * Match incoming packets with geolocation Extension tags for dispatching a new event.
		 *
		 * @param packet
		 * 		matching Geolocation Extension tags.
		 */
		public void processStanza(Stanza packet)
		{
			String from = packet.getFrom().asBareJid().toString();

			GeoLocation geolocExt = (GeoLocation) packet
					.getExtension(GeoLocation.ELEMENT, GeoLocation.NAMESPACE);

			if (geolocExt != null) {
				if (logger.isDebugEnabled())
					logger.debug("GeolocationExtension found from " + from + ":"
							+ geolocExt.toXML());

				Map<String, String> newGeolocation = GeolocationJabberUtils
						.convertExtensionToMap(geolocExt);
				this.fireGeolocationContactChangeEvent(from, newGeolocation);
			}
		}

		/**
		 * Notify registered listeners for a new incoming GeolocationExtension.
		 *
		 * @param sourceContact
		 * 		which send a new Geolocation.
		 * @param newGeolocation
		 * 		the new given Geolocation.
		 */
		public void fireGeolocationContactChangeEvent(String sourceContact,
				Map<String, String> newGeolocation)
		{
			if (logger.isDebugEnabled())
				logger.debug("Trying to dispatch geolocation contact update for " + sourceContact);

			Contact source = opsetprez.findContactByID(sourceContact);

			GeolocationEvent evt = new GeolocationEvent(source, jabberProvider, newGeolocation,
					OperationSetGeolocationJabberImpl.this);

			if (logger.isDebugEnabled())
				logger.debug("Dispatching  geolocation contact update. Listeners="
						+ geolocationContactsListeners.size() + " evt=" + evt);

			GeolocationListener[] listeners;
			synchronized (geolocationContactsListeners) {
				listeners = geolocationContactsListeners
						.toArray(new GeolocationListener[geolocationContactsListeners.size()]);
			}
			for (GeolocationListener listener : listeners)
				listener.contactGeolocationChanged(evt);
		}
	}
}
