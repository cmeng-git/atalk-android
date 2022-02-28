/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license.
 * See terms of license at gnu.org.
 */
package net.java.sip.communicator.service.googlecontacts;

import java.util.List;

/**
 * Google Contacts service.
 *
 * @author Sebastien Vincent
 */
public interface GoogleContactsService {
	/**
	 * Perform a search for a contact using regular expression.
	 *
	 * @param cnx <code>GoogleContactsConnection</code> to perform the query
	 * @param query Google query
	 * @param count maximum number of matched contacts
	 * @param callback object that will be notified for each new
	 * <code>GoogleContactsEntry</code> found
	 * @return list of <code>GoogleContactsEntry</code>
	 */
	public List<GoogleContactsEntry> searchContact(GoogleContactsConnection cnx,
			GoogleQuery query, int count, GoogleEntryCallback callback);

	/**
	 * Get a <code>GoogleContactsConnection</code>.
	 *
	 * @param login login to connect to the service
	 * @param password password to connect to the service
	 * @return <code>GoogleContactsConnection</code>.
	 */
	public GoogleContactsConnection getConnection(String login,
			String password);

	/**
	 * Get the full contacts list.
	 *
	 * @return list of <code>GoogleContactsEntry</code>
	 */
	public List<GoogleContactsEntry> getContacts();

	/**
	 * Add a contact source service with the specified
	 * <code>GoogleContactsConnection</code>.
	 *
	 * @param login login
	 * @param password password
	 */
	public void addContactSource(String login, String password);

	/**
	 * Add a contact source service with the specified
	 * <code>GoogleContactsConnection</code>.
	 *
	 * @param cnx <code>GoogleContactsConnection</code>
	 * @param googleTalk if the contact source has been created as GoogleTalk
	 * account or via external Google Contacts
	 */
	public void addContactSource(GoogleContactsConnection cnx,
			boolean googleTalk);

	/**
	 * Remove a contact source service with the specified
	 * <code>GoogleContactsConnection</code>.
	 *
	 * @param cnx <code>GoogleContactsConnection</code>.
	 */
	public void removeContactSource(GoogleContactsConnection cnx);

	/**
	 * Remove a contact source service with the specified
	 * <code>GoogleContactsConnection</code>.
	 *
	 * @param login login
	 */
	public void removeContactSource(String login);
}
