/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 * 
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package net.java.sip.communicator.service.protocol.event;

/**
 * An abstract adapter class for receiving call peer (change) events. This class exists only as a
 * convenience for creating listener objects.
 * <p>
 * Extend this class to create a <code>CallPeerChangeEvent</code> listener and override the methods for
 * the events of interest. (If you implement the <code>CallPeerListener</code> interface, you have to
 * define all of the methods in it. This abstract class defines null methods for them all, so you
 * only have to define methods for events you care about.)
 * </p>
 *
 * @see CallPeerChangeEvent
 * @see CallPeerListener
 *
 * @author Lubomir Marinov
 */
public abstract class CallPeerAdapter implements CallPeerListener
{

	/**
	 * Indicates that a change has occurred in the address of the source <code>CallPeer</code>.
	 *
	 * @param evt
	 *        the <code>CallPeerChangeEvent</code> instance containing the source event as well as its
	 *        previous and its new address
	 */
	public void peerAddressChanged(CallPeerChangeEvent evt)
	{
	}

	/**
	 * Indicates that a change has occurred in the display name of the source <code>CallPeer</code>.
	 *
	 * @param evt
	 *        the <code>CallPeerChangeEvent</code> instance containing the source event as well as its
	 *        previous and its new display names
	 */
	public void peerDisplayNameChanged(CallPeerChangeEvent evt)
	{
	}

	/**
	 * Indicates that a change has occurred in the image of the source <code>CallPeer</code>.
	 *
	 * @param evt
	 *        the <code>CallPeerChangeEvent</code> instance containing the source event as well as its
	 *        previous and its new image
	 */
	public void peerImageChanged(CallPeerChangeEvent evt)
	{
	}

	/**
	 * Indicates that a change has occurred in the status of the source <code>CallPeer</code>.
	 *
	 * @param evt
	 *        the <code>CallPeerChangeEvent</code> instance containing the source event as well as its
	 *        previous and its new status
	 */
	public void peerStateChanged(CallPeerChangeEvent evt)
	{
	}

	/**
	 * Indicates that a change has occurred in the transport address that we use to communicate with
	 * the source <code>CallPeer</code>.
	 *
	 * @param evt
	 *        the <code>CallPeerChangeEvent</code> instance containing the source event as well as its
	 *        previous and its new transport address
	 */
	public void peerTransportAddressChanged(CallPeerChangeEvent evt)
	{
	}
}
