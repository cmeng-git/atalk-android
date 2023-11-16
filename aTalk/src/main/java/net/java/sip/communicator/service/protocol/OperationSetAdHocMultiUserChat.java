/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 * 
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package net.java.sip.communicator.service.protocol;

import net.java.sip.communicator.service.protocol.event.AdHocChatRoomInvitationListener;
import net.java.sip.communicator.service.protocol.event.AdHocChatRoomInvitationRejectionListener;
import net.java.sip.communicator.service.protocol.event.LocalUserAdHocChatRoomPresenceListener;

import java.util.List;
import java.util.Map;

/**
 * Allows creating, configuring, joining and administering of individual text-based ad-hoc
 * conference rooms.
 *
 * @author Valentin Martinet
 */
public interface OperationSetAdHocMultiUserChat extends OperationSet
{
	/**
	 * Creates an ad-hoc room with the named <code>adHocRoomName</code> and according to the specified
	 * <code>adHocRoomProperties</code>. When the method returns the ad-hoc room the local user will
	 * have joined it.
	 * <p>
	 *
	 * @param adHocRoomName
	 *        the name of the <code>AdHocChatRoom</code> to create.
	 * @param adHocRoomProperties
	 *        properties specifying how the ad-hoc room should be created; <code>null</code> for no
	 *        properties just like an empty <code>Map</code>
	 * @throws OperationFailedException
	 *         if the ad-hoc room couldn't be created for some reason.
	 * @throws OperationNotSupportedException
	 *         if chat room creation is not supported by this server
	 *
	 * @return the newly created <code>AdHocChatRoom</code> named <code>roomName</code>.
	 */
	public AdHocChatRoom createAdHocChatRoom(String adHocRoomName,
		Map<String, Object> adHocRoomProperties)
		throws OperationFailedException, OperationNotSupportedException;

	/**
	 * Creates an ad-hoc room with the named <code>adHocRoomName</code> and in including to the
	 * specified <code>contacts</code> for the given <code>reason
	 * </code>. When the method returns the ad-hoc room the local user will have joined it.
	 * <p>
	 *
	 * @param adHocRoomName
	 *        the name of the <code>AdHocChatRoom</code> to create.
	 * @param contacts
	 *        the contacts (ID) who are added to the room when it's created; <code>null</code> for no
	 *        contacts
	 * @param reason
	 *        the reason for this invitation
	 * @throws OperationFailedException
	 *         if the ad-hoc room couldn't be created for some reason.
	 * @throws OperationNotSupportedException
	 *         if chat room creation is not supported by this server
	 *
	 * @return the newly created <code>AdHocChatRoom</code> named <code>roomName</code>.
	 */
	public AdHocChatRoom createAdHocChatRoom(String adHocRoomName, List<String> contacts,
		String reason)
		throws OperationFailedException, OperationNotSupportedException;

	/**
	 * Returns a list of all currently joined <code>AdHocChatRoom</code>-s.
	 *
	 * @return a list of all currently joined <code>AdHocChatRoom</code>-s
	 */
	public List<AdHocChatRoom> getAdHocChatRooms();

	/**
	 * Adds a listener that will be notified of changes in our participation in an ad-hoc chat room
	 * such as us being joined, left.
	 *
	 * @param listener
	 *        a local user participation listener.
	 */
	public void addPresenceListener(LocalUserAdHocChatRoomPresenceListener listener);

	/**
	 * Removes a listener that was being notified of changes in our participation in an ad-hoc room
	 * such as us being joined, left.
	 *
	 * @param listener
	 *        a local user participation listener.
	 */
	public void removePresenceListener(LocalUserAdHocChatRoomPresenceListener listener);

	/**
	 * Adds the given <code>listener</code> to the list of <code>AdHocChatRoomInvitationListener</code>-s
	 * that would be notified when an add-hoc chat room invitation has been received.
	 *
	 * @param listener
	 *        the <code>AdHocChatRoomInvitationListener</code> to add
	 */
	public void addInvitationListener(AdHocChatRoomInvitationListener listener);

	/**
	 * Removes <code>listener</code> from the list of invitation listeners registered to receive
	 * invitation events.
	 *
	 * @param listener
	 *        the invitation listener to remove.
	 */
	public void removeInvitationListener(AdHocChatRoomInvitationListener listener);

	/**
	 * Adds the given <code>listener</code> to the list of
	 * <code>AdHocChatRoomInvitationRejectionListener</code>-s that would be notified when an add-hoc
	 * chat room invitation has been rejected.
	 *
	 * @param listener
	 *        the <code>AdHocChatRoomInvitationListener</code> to add
	 */
	public void addInvitationRejectionListener(AdHocChatRoomInvitationRejectionListener listener);

	/**
	 * Removes the given listener from the list of invitation listeners registered to receive events
	 * every time an invitation has been rejected.
	 *
	 * @param listener
	 *        the invitation listener to remove.
	 */
	public void removeInvitationRejectionListener(AdHocChatRoomInvitationRejectionListener listener);

	/**
	 * Informs the sender of an invitation that we decline their invitation.
	 *
	 * @param invitation
	 *        the invitation we are rejecting.
	 * @param rejectReason
	 *        the reason to reject the invitation (optional)
	 */
	public void rejectInvitation(AdHocChatRoomInvitation invitation, String rejectReason);
}
