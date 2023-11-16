/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 * 
 * http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */
package net.java.sip.communicator.service.protocol;

import net.java.sip.communicator.service.protocol.event.WhiteboardChangeListener;
import net.java.sip.communicator.service.protocol.event.WhiteboardObjectListener;
import net.java.sip.communicator.service.protocol.whiteboardobjects.WhiteboardObject;

import java.util.Iterator;
import java.util.Vector;

/**
 * A represenation of a <code>WhiteboardSession</code>.
 *
 * @author Julien Waechter
 * @author Emil Ivov
 */
public interface WhiteboardSession
{

	/**
	 * Returns the id of the specified Whiteboard.
	 *
	 * @return a String uniquely identifying the whiteboard.
	 */
	public String getWhiteboardID();

	/**
	 * Returns an iterator over all whiteboard participants.
	 *
	 * @return an Iterator over all participants currently involved in the whiteboard.
	 */
	public Iterator<WhiteboardParticipant> getWhiteboardParticipants();

	/**
	 * Returns the number of participants currently associated with this whiteboard session.
	 *
	 * @return an <code>int</code> indicating the number of participants currently associated with this
	 *         whiteboard.
	 */
	public int getWhiteboardParticipantsCount();

	/**
	 * Adds a whiteboard change listener to this whiteboard so that it could receive events on new
	 * whiteboard participants, theme changes and others.
	 *
	 * @param listener
	 *        the listener to register
	 */
	public void addWhiteboardChangeListener(WhiteboardChangeListener listener);

	/**
	 * Removes <code>listener</code> to this whiteboard so that it won't receive further
	 * <code>WhiteboardChangeEvent</code>s.
	 *
	 * @param listener
	 *        the listener to register
	 */
	public void removeWhiteboardChangeListener(WhiteboardChangeListener listener);

	/**
	 * Returns a reference to the <code>ProtocolProviderService</code> instance that created this
	 * whiteboard.
	 *
	 * @return a reference to the <code>ProtocolProviderService</code> instance that created this
	 *         whiteboard.
	 */
	public ProtocolProviderService getProtocolProvider();

	/**
	 * Joins this whiteboard with the nickname of the local user so that the user would start
	 * receiving events and WhiteboardObject for it.
	 *
	 * @throws OperationFailedException
	 *         with the corresponding code if an error occurs while joining the room.
	 */
	public abstract void join()
		throws OperationFailedException;

	/**
	 * Joins this whiteboard so that the user would start receiving events and WhiteboardObject for
	 * it. The method uses the nickname of the local user and the specified password in order to
	 * enter the whiteboard session.
	 *
	 * @param password
	 *        the password to use when authenticating on the whiteboard session.
	 * @throws OperationFailedException
	 *         with the corresponding code if an error occurs while joining the room.
	 */
	public abstract void join(byte[] password)
		throws OperationFailedException;

	/**
	 * Returns true if the local user is currently in the whiteboard session (after whiteboarding
	 * one of the {@link #join()} methods).
	 *
	 * @return true if currently we're currently in this whiteboard and false otherwise.
	 */
	public abstract boolean isJoined();

	/**
	 * Leave this whiteboard. Once this method is whiteboarded, the user won't be listed as a member
	 * of the whiteboard any more and no further whiteboard events will be delivered. Depending on
	 * the underlying protocol and implementation leave() might cause the room to be destroyed if it
	 * has been created by the local user.
	 */
	public abstract void leave();

	/**
	 * Invites another user to this room.
	 * <p>
	 * If the room is password-protected, the invitee will receive a password to use to join the
	 * room. If the room is members-only, the the invitee may be added to the member list.
	 *
	 * @param userAddress
	 *        the address of the user to invite to the room. (one may also invite users not on their
	 *        contact list).
	 */
	public abstract void invite(String userAddress);

	/**
	 * Registers <code>listener</code> so that it would receive events every time a new WhiteboardObject
	 * is received on this whiteboard.
	 *
	 *
	 * @param listener
	 *        a <code>WhiteboardObjectListener</code> that would be notified every time a new
	 *        WhiteboardObject is received on this whiteboard.
	 */
	public abstract void addWhiteboardObjectListener(WhiteboardObjectListener listener);

	/**
	 * Removes <code>listener</code> so that it won't receive any further WhiteboardObject events from
	 * this room.
	 *
	 *
	 * @param listener
	 *        the <code>WhiteboardObjectListener</code> to remove from this room
	 */
	public abstract void removeWhiteboardObjectListener(WhiteboardObjectListener listener);

	/**
	 * Create a WhiteboardObject instance with the specified type. This method only creates the
	 * object locally and it would not be visible to other session participants until it is resolved
	 * with the sendWhiteboardObject(WhiteboardObject) method.
	 *
	 * @param name
	 *        the name of the object to create (should be one of the + * WhiteboardObjectXXX.NAME
	 *        fields).
	 *
	 * @return the newly created WhiteboardObject with an id
	 */
	public abstract WhiteboardObject createWhiteboardObject(String name);

	/**
	 * Resolves <code>obj</code> with the other session participants. When called for the first time
	 * with a specific <code>WhiteboardObject</code> instance it would appear on their whiteboards. If
	 * <code>obj</code> has already been sent through this method previously, this method would result
	 * in updating the way the object looks in other instances of this session (i.e. the method
	 * should be used for both initially sending an object as well as sending changes made on an
	 * object since the method was last called).
	 *
	 * @param obj
	 *        the <code>WhiteboardObject</code> to send.
	 * @throws OperationFailedException
	 *         if sending the WhiteboardObject fails for some reason.
	 */
	public abstract void sendWhiteboardObject(WhiteboardObject obj)
		throws OperationFailedException;

	/**
	 * Sends a <code>WhiteboardObject</code> to modify and modifies the local <code>WhiteboardObject</code>
	 *
	 * @param obj
	 *        the <code>WhiteboardObject</code> to send and modify
	 * @throws OperationFailedException
	 *         if sending the WhiteboardObject fails for some reason.
	 */
	public abstract void moveWhiteboardObject(WhiteboardObject obj)
		throws OperationFailedException;

	/**
	 * Sends a <code>WhiteboardObject</code> to delete and delete the local <code>WhiteboardObject</code>
	 *
	 * @param obj
	 *        the <code>WhiteboardObject</code> to send and delete
	 * @throws OperationFailedException
	 *         if sending the WhiteboardObject fails for some reason.
	 */
	public abstract void deleteWhiteboardObject(WhiteboardObject obj)
		throws OperationFailedException;

	/**
	 * Adds <code>wbParticipant</code> to the list of participants in this whiteboard. If the wb
	 * participant is already included in the whiteboard, the method has no effect.
	 *
	 * @param wbParticipant
	 *        the new <code>WhiteboardParticipant</code>
	 */
	public abstract void addWhiteboardParticipant(WhiteboardParticipant wbParticipant);

	/**
	 * Removes <code>whiteboardParticipant</code> from the list of participants in this whiteboard. The
	 * method has no effect if there was no such participant in the whiteboard.
	 *
	 * @param wbParticipant
	 *        the <code>WhiteboardParticipant</code> leaving the whiteboard;
	 */

	public abstract void removeWhiteboardParticipant(WhiteboardParticipant wbParticipant);

	/**
	 * Returns the WhiteboardObjects in this whiteboard session.
	 * 
	 * @return an <code>Vector</code> of WhiteboardObjects associated with this whiteboard.
	 */
	public Vector<WhiteboardObject> getWhiteboardObjects();

	/**
	 * Sets the state of this whiteboard
	 *
	 * @param newState
	 *        a reference to the <code>WhiteboardState</code> instance that the whiteboard is to enter.
	 */
	public void setState(WhiteboardSessionState newState);

	/**
	 * Returns the state that this whiteboard is currently in.
	 *
	 * @return a reference to the <code>WhiteboardState</code> instance that the whiteboard is currently
	 *         in.
	 */
	public WhiteboardSessionState getState();

	/**
	 * Returns all the type of WhiteboardObject that this whiteboard support.
	 *
	 * @return all the WhiteboardObject supported by this WhiteboardSession.
	 */
	public String[] getSupportedWhiteboardObjects();
}
