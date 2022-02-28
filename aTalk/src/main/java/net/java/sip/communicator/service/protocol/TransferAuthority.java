/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 * 
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package net.java.sip.communicator.service.protocol;

/**
 * Interacts with user for received transfer request for unknown calls.
 *
 * @author Damian Minkov
 */
public interface TransferAuthority
{
	/**
	 * Checks with user for unknown transfer. Returns <code>true</code> if user accepts and we must
	 * process the transfer, <code>false</code> otherwise.
	 *
	 * @param fromContact
	 *        the contact initiating the transfer.
	 * @param transferTo
	 *        the address we will be transferred to.
	 * @return <code>true</code> if transfer is allowed to process, <code>false</code> otherwise.
	 */
	public boolean processTransfer(Contact fromContact, String transferTo);

	/**
	 * Checks with user for unknown transfer. Returns <code>true</code> if user accepts and we must
	 * process the transfer, <code>false</code> otherwise.
	 *
	 * @param fromAddress
	 *        the address initiating the transfer.
	 * @param transferTo
	 *        the address we will be transferred to.
	 * @return <code>true</code> if transfer is allowed to process, <code>false</code> otherwise.
	 */
	public boolean processTransfer(String fromAddress, String transferTo);
}
