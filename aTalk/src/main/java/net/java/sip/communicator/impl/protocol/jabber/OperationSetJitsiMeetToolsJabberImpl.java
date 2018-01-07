/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 * 
 * Distributable under LGPL license. See terms of license at gnu.org.
 */

package net.java.sip.communicator.impl.protocol.jabber;

import net.java.sip.communicator.service.protocol.ChatRoom;
import net.java.sip.communicator.service.protocol.OperationSetJitsiMeetTools;

import org.jivesoftware.smack.packet.ExtensionElement;

/**
 * Jabber protocol provider implementation of {@link OperationSetJitsiMeetTools}
 *
 * @author Pawel Domas
 * @author Eng Chong Meng
*/
public class OperationSetJitsiMeetToolsJabberImpl implements OperationSetJitsiMeetTools
{
	private final ProtocolProviderServiceJabberImpl parentProvider;

	/**
	 * Creates new instance of <tt>OperationSetJitsiMeetToolsJabberImpl</tt>.
	 *
	 * @param parentProvider
	 *        parent Jabber protocol provider service instance.
	 */
	public OperationSetJitsiMeetToolsJabberImpl(ProtocolProviderServiceJabberImpl parentProvider)
	{
		this.parentProvider = parentProvider;
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public void addSupportedFeature(String featureName)
	{
		parentProvider.getDiscoveryManager().addFeature(featureName);
	}

	/**
	 * {@inheritDoc}
	 */
	public void removeSupportedFeature(String featureName)
	{
		parentProvider.getDiscoveryManager().removeFeature(featureName);
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public void sendPresenceExtension(ChatRoom chatRoom, ExtensionElement extension)
	{
		((ChatRoomJabberImpl) chatRoom).sendPresenceExtension(extension);
	}

	/**
	 * {@inheritDoc}
	 */
    @Override
    public void removePresenceExtension(ChatRoom chatRoom, ExtensionElement extension)
    {
        ((ChatRoomJabberImpl)chatRoom).removePresenceExtension(extension);
    }

    /**
     * {@inheritDoc}
     */
	@Override
	public void setPresenceStatus(ChatRoom chatRoom, String statusMessage)
	{
		((ChatRoomJabberImpl) chatRoom).publishPresenceStatus(statusMessage);
	}

	@Override
	public void addRequestListener(JitsiMeetRequestListener requestHandler)
	{
		// Not used
	}

	@Override
	public void removeRequestListener(JitsiMeetRequestListener requestHandler)
	{
		// Not used
	}
}
