/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license.
 * See terms of license at gnu.org.
 */
package net.java.sip.communicator.service.googlecontacts;

/**
 * Defines the interface for a callback function which is called by the
 * <code>GoogleContactsService</code> when a new <code>GoogleContactsEntry</code> has
 * been found during a search.
 */
public interface GoogleEntryCallback {
	/**
	 * Notifies this <code>GoogleEntryCallback</code> when a new
	 * <code>GoogleContactsEntry</code> has been found.
	 *
	 * @param entry the <code>GoogleContactsEntry</code> found
	 */
	void callback(GoogleContactsEntry entry);
}
