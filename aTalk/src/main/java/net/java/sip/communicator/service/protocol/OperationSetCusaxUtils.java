/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 * 
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package net.java.sip.communicator.service.protocol;

/**
 * The <code>OperationSetCusaxUtils</code> provides utility methods related to a CUSAX implementation.
 *
 * @author Yana Stamcheva
 */
public interface OperationSetCusaxUtils extends OperationSet
{
	/**
	 * Checks if the given <code>detailAddress</code> exists in the given <code>contact</code> details.
	 *
	 * @param contact
	 *        the <code>Contact</code>, which details to check
	 * @param detailAddress
	 *        the detail address we're looking for
	 * @return <code>true</code> if the given <code>detailAdress</code> exists in the details of the given
	 *         <code>contact</code>
	 */
	public boolean doesDetailBelong(Contact contact, String detailAddress);

	/**
	 * Returns the linked CUSAX provider for this protocol provider.
	 *
	 * @return the linked CUSAX provider for this protocol provider or null if such isn't specified
	 */
	public ProtocolProviderService getLinkedCusaxProvider();
}
