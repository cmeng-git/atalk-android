/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 * 
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package net.java.sip.communicator.service.protocol.event;

/**
 * An abstract implementation of <code>CallPeerConferenceListener</code> which exists only as a
 * convenience to extenders. Additionally, provides a means to receive the
 * <code>CallPeerConferenceEvent</code>s passed to the various <code>CallPeerConferenceListener</code>
 * methods into a single method because their specifics can be determined based on their
 * <code>eventID</code>.
 *
 * @author Lyubomir Marinov
 */
public class CallPeerConferenceAdapter implements CallPeerConferenceListener
{
	/**
	 * {@inheritDoc}
	 *
	 * Calls {@link #onCallPeerConferenceEvent(CallPeerConferenceEvent)}.
	 */
	public void conferenceFocusChanged(CallPeerConferenceEvent ev)
	{
		onCallPeerConferenceEvent(ev);
	}

	/**
	 * {@inheritDoc}
	 *
	 * Calls {@link #onCallPeerConferenceEvent(CallPeerConferenceEvent)}.
	 */
	public void conferenceMemberAdded(CallPeerConferenceEvent ev)
	{
		onCallPeerConferenceEvent(ev);
	}

	/**
	 * {@inheritDoc}
	 *
	 * Dummy implementation of {@link #conferenceMemberErrorReceived(CallPeerConferenceEvent)}.
	 */
	public void conferenceMemberErrorReceived(CallPeerConferenceEvent ev)
	{
	}

	/**
	 * {@inheritDoc}
	 *
	 * Calls {@link #onCallPeerConferenceEvent(CallPeerConferenceEvent)}.
	 */
	public void conferenceMemberRemoved(CallPeerConferenceEvent ev)
	{
		onCallPeerConferenceEvent(ev);
	}

	/**
	 * Notifies this listener about a specific <code>CallPeerConferenceEvent</code> provided to one of
	 * the <code>CallPeerConferenceListener</code> methods. The <code>CallPeerConferenceListener</code>
	 * method which was originally invoked on this listener can be determined based on the
	 * <code>eventID</code> of the specified <code>CallPeerConferenceEvent</code>. The implementation of
	 * <code>CallPeerConferenceAdapter</code> does nothing.
	 *
	 * @param ev
	 *        the <code>CallPeerConferenceEvent</code> this listener is being notified about
	 */
	protected void onCallPeerConferenceEvent(CallPeerConferenceEvent ev)
	{
	}
}
