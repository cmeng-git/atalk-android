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
package net.java.sip.communicator.service.protocol.event;

import net.java.sip.communicator.service.protocol.ChatRoom;
import net.java.sip.communicator.service.protocol.ChatRoomMember;
import net.java.sip.communicator.service.protocol.ConferenceDescription;

import java.util.EventObject;

/**
 * Dispatched to notify interested parties that a <code>ChatRoomMember</code> has published a conference
 * description.
 *
 * @author Boris Grozev
 */
public class ChatRoomConferencePublishedEvent extends EventObject
{
	/**
	 * The <code>ChatRoom</code> which is the source of this event.
	 */
	private final ChatRoom chatRoom;

	/**
	 * The <code>ChatRoomMember</code> who published a <code>ConferenceDescription</code>
	 */
	private final ChatRoomMember member;

	/**
	 * The <code>ConferenceDescription</code> that was published.
	 */
	private final ConferenceDescription conferenceDescription;

	/**
	 * The type of the event. It can be <code>CONFERENCE_DESCRIPTION_SENT</code> or
	 * <code>CONFERENCE_DESCRIPTION_RECEIVED</code>.
	 */
	private final int eventType;

	/**
	 * Event type that indicates sending of conference description by the local user.
	 */
	public final static int CONFERENCE_DESCRIPTION_SENT = 0;

	/**
	 * Event type that indicates receiving conference description.
	 */
	public final static int CONFERENCE_DESCRIPTION_RECEIVED = 1;

	/**
	 * Creates a new instance.
	 * 
	 * @param chatRoom
	 *        The <code>ChatRoom</code> which is the source of this event.
	 * @param member
	 *        The <code>ChatRoomMember</code> who published a <code>ConferenceDescription</code>
	 * @param conferenceDescription
	 *        The <code>ConferenceDescription</code> that was published.
	 */
	public ChatRoomConferencePublishedEvent(int eventType, ChatRoom chatRoom,
		ChatRoomMember member, ConferenceDescription conferenceDescription)
	{
		super(chatRoom);

		this.eventType = eventType;
		this.chatRoom = chatRoom;
		this.member = member;
		this.conferenceDescription = conferenceDescription;
	}

	/**
	 * Returns the <code>ChatRoom</code> which is the source of this event.
	 * 
	 * @return the <code>ChatRoom</code> which is the source of this event.
	 */
	public ChatRoom getChatRoom()
	{
		return chatRoom;
	}

	/**
	 * Returns the <code>ChatRoomMember</code> who published a <code>ConferenceDescription</code>
	 * 
	 * @return the <code>ChatRoomMember</code> who published a <code>ConferenceDescription</code>
	 */
	public ChatRoomMember getMember()
	{
		return member;
	}

	/**
	 * Returns the <code>ConferenceDescription</code> that was published.
	 * 
	 * @return the <code>ConferenceDescription</code> that was published.
	 */
	public ConferenceDescription getConferenceDescription()
	{
		return conferenceDescription;
	}

	/**
	 * Returns the event type.
	 * 
	 * @return the event type.
	 */
	public int getType()
	{
		return eventType;
	}
}
