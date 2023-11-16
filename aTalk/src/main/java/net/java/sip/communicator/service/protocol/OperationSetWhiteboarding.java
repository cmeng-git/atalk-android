/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 * 
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package net.java.sip.communicator.service.protocol;

import net.java.sip.communicator.service.protocol.event.WhiteboardInvitationListener;
import net.java.sip.communicator.service.protocol.event.WhiteboardInvitationRejectionListener;
import net.java.sip.communicator.service.protocol.event.WhiteboardSessionPresenceListener;

import java.util.Hashtable;
import java.util.List;

/**
 * Provides basic functionality for white-board.
 *
 * @author Julien Waechter
 */
public interface OperationSetWhiteboarding extends OperationSet
{
	/**
	 * Returns a list of the <code>WhiteboardSession</code>s that we have joined and are currently
	 * active in.
	 *
	 * @return a <code>List</code> of the <code>WhiteboardSession</code>s where the user has joined using a
	 *         given connection.
	 */
	public List<WhiteboardSession> getCurrentlyJoinedWhiteboards();

	/**
	 * Returns a list of the <code>WhiteboardSession</code>s that <code>WhiteboardParticipant</code> has
	 * joined and is currently active in.
	 *
	 * @param participant
	 *        the participant whose current <code>WhiteboardSession</code>s we will be querying.
	 * @return a list of the <code>WhiteboardSession</code>s that <code>WhiteboardParticipant</code> has
	 *         joined and is currently active in.
	 *
	 * @throws OperationFailedException
	 *         if an error occurs while trying to discover the session.
	 * @throws OperationNotSupportedException
	 *         if the server does not support white-boarding
	 */
	public List<WhiteboardSession> getCurrentlyJoinedWhiteboards(WhiteboardParticipant participant)
		throws OperationFailedException, OperationNotSupportedException;

	/**
	 * Creates a <code>WhiteboardSession</code> with the name <code>sessionName</code> and according to the
	 * specified <code>sessionProperties</code>. When the method returns the white-board session object,
	 * the local user will not have joined it and thus will not receive messages on it until the
	 * <code>WhiteboardSession.join()</code> method is called.
	 * <p>
	 * 
	 * @param sessionName
	 *        the name of the <code>WhiteboardSession</code> to create.
	 * @param sessionProperties
	 *        properties specifying how the session should be created.
	 * @throws OperationFailedException
	 *         if the room couldn't be created for some reason (e.g. room already exists; user
	 *         already joined to an existent room or user has no permissions to create a chat room).
	 * @throws OperationNotSupportedException
	 *         if chat room creation is not supported by this server
	 *
	 * @return the newly created <code>WhiteboardSession</code> named <code>sessionName</code>.
	 */
	public WhiteboardSession createWhiteboardSession(String sessionName,
		Hashtable<Object, Object> sessionProperties)
		throws OperationFailedException, OperationNotSupportedException;

	/**
	 * Returns a reference to a <code>WhiteboardSession</code> named <code>sessionName</code> or null if no
	 * such session exists.
	 * <p>
	 * 
	 * @param sessionName
	 *        the name of the <code>WhiteboardSession</code> that we're looking for.
	 * @return the <code>WhiteboardSession</code> named <code>sessionName</code> or null if no such session
	 *         exists on the server that this provider is currently connected to.
	 *
	 * @throws OperationFailedException
	 *         if an error occurs while trying to discover the white-board session on the server.
	 * @throws OperationNotSupportedException
	 *         if the server does not support white-boarding
	 */
	public WhiteboardSession findWhiteboardSession(String sessionName)
		throws OperationFailedException, OperationNotSupportedException;

	/**
	 * Informs the sender of an invitation that we decline their invitation.
	 *
	 * @param invitation
	 *        the invitation we are rejecting.
	 * @param rejectReason
	 *        the reason to reject the invitation (optional)
	 */
	public void rejectInvitation(WhiteboardInvitation invitation, String rejectReason);

	/**
	 * Adds a listener to invitation notifications. The listener will be fired anytime an invitation
	 * is received.
	 *
	 * @param listener
	 *        an invitation listener.
	 */
	public void addInvitationListener(WhiteboardInvitationListener listener);

	/**
	 * Removes <code>listener</code> from the list of invitation listeners registered to receive
	 * invitation events.
	 *
	 * @param listener
	 *        the invitation listener to remove.
	 */
	public void removeInvitationListener(WhiteboardInvitationListener listener);

	/**
	 * Adds a listener to invitation notifications. The listener will be fired anytime an invitation
	 * is received.
	 *
	 * @param listener
	 *        an invitation listener.
	 */
	public void addInvitationRejectionListener(WhiteboardInvitationRejectionListener listener);

	/**
	 * Removes the given listener from the list of invitation listeners registered to receive events
	 * every time an invitation has been rejected.
	 *
	 * @param listener
	 *        the invitation listener to remove.
	 */
	public void removeInvitationRejectionListener(WhiteboardInvitationRejectionListener listener);

	/**
	 * Returns true if <code>contact</code> supports white-board sessions.
	 *
	 * @param contact
	 *        reference to the contact whose support for white-boards we are currently querying.
	 * @return a boolean indicating whether <code>contact</code> supports white-boards.
	 */
	public boolean isWhiteboardingSupportedByContact(Contact contact);

	/**
	 * Adds a listener that will be notified of changes in our participation in a white-board
	 * session such as us being joined, left, dropped.
	 *
	 * @param listener
	 *        a local user participation listener.
	 */
	public void addPresenceListener(WhiteboardSessionPresenceListener listener);

	/**
	 * Removes a listener that was being notified of changes in our participation in a room such as
	 * us being kicked, joined, left.
	 *
	 * @param listener
	 *        a local user participation listener.
	 */
	public void removePresenceListener(WhiteboardSessionPresenceListener listener);
}
