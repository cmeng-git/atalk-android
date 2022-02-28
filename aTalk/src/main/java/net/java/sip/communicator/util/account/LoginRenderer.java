/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 * 
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package net.java.sip.communicator.util.account;

import net.java.sip.communicator.service.protocol.ProtocolProviderService;
import net.java.sip.communicator.service.protocol.SecurityAuthority;

/**
 * The <code>LoginRenderer</code> is the renderer of all login related operations.
 *
 * @author Yana Stamcheva
 */
public interface LoginRenderer {
	/**
	 * Adds the user interface related to the given protocol provider.
	 *
	 * @param protocolProvider the protocol provider for which we add the user interface
	 */
	public void addProtocolProviderUI(ProtocolProviderService protocolProvider);

	/**
	 * Removes the user interface related to the given protocol provider.
	 *
	 * @param protocolProvider the protocol provider to remove
	 */
	public void removeProtocolProviderUI(ProtocolProviderService protocolProvider);

	/**
	 * Starts the connecting user interface for the given protocol provider.
	 *
	 * @param protocolProvider the protocol provider for which we add the connecting user interface
	 */
	public void startConnectingUI(ProtocolProviderService protocolProvider);

	/**
	 * Stops the connecting user interface for the given protocol provider.
	 *
	 * @param protocolProvider the protocol provider for which we remove the connecting user interface
	 */
	public void stopConnectingUI(ProtocolProviderService protocolProvider);

	/**
	 * Indicates that the given protocol provider is now connected.
	 *
	 * @param protocolProvider the <code>ProtocolProviderService</code> that is connected
	 * @param date the date on which the event occured
	 */
	public void protocolProviderConnected(ProtocolProviderService protocolProvider, long date);

	/**
	 * Indicates that a protocol provider connection has failed.
	 *
	 * @param protocolProvider the <code>ProtocolProviderService</code>, which connection failed
	 * @param loginManagerCallback the <code>LoginManager</code> implementation, which is
	 * managing the process
	 */
	public void protocolProviderConnectionFailed(ProtocolProviderService protocolProvider,
			LoginManager loginManagerCallback);

	/**
	 * Returns the <code>SecurityAuthority</code> implementation related to this login renderer.
	 *
	 * @param protocolProvider the specific <code>ProtocolProviderService</code>, for which we're
	 * obtaining a security authority
	 * @return the <code>SecurityAuthority</code> implementation related to this login renderer
	 */
	public SecurityAuthority getSecurityAuthorityImpl(ProtocolProviderService protocolProvider);

	/**
	 * Indicates if the given <code>protocolProvider</code> related user interface is already rendered.
	 *
	 * @param protocolProvider the <code>ProtocolProviderService</code>, which related user interface
	 * we're looking for
	 * @return <code>true</code> if the given <code>protocolProvider</code> related user interface is
	 * already rendered
	 */
	public boolean containsProtocolProviderUI(ProtocolProviderService protocolProvider);
}