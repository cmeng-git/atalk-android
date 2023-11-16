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

/**
 * A <code>ChatRoomPropertyChangeEvent</code> is issued whenever a chat room property has changed. Event
 * codes defined in this class describe properties whose changes are being announced through this
 * event.
 *
 * @author Emil Ivov
 * @author Stephane Remy
 */
public class ChatRoomPropertyChangeEvent extends java.beans.PropertyChangeEvent
{
	/**
	 * Serial version UID.
	 */
	private static final long serialVersionUID = 0L;

	/**
	 * The name of the <code>ChatRoom</code> subject property.
	 */
	public static final String CHAT_ROOM_SUBJECT = "ChatRoomSubject";

	/**
	 * The name of the <code>ChatRoom</code> subject property.
	 */
	public static final String CHAT_ROOM_USER_NICKNAME = "ChatRoomUserNickname";

	/**
	 * Creates a <code>ChatRoomPropertyChangeEvent</code> indicating that a change has occurred for
	 * property <code>propertyName</code> in the <code>source</code> chat room and that its value has
	 * changed from <code>oldValue</code> to <code>newValue</code>.
	 * <p>
	 * 
	 * @param source
	 *        the <code>ChatRoom</code> whose property has changed.
	 * @param propertyName
	 *        the name of the property that has changed.
	 * @param oldValue
	 *        the value of the property before the change occurred.
	 * @param newValue
	 *        the value of the property after the change.
	 */
	public ChatRoomPropertyChangeEvent(ChatRoom source, String propertyName, Object oldValue,
		Object newValue)
	{
		super(source, propertyName, oldValue, newValue);
	}

	/**
	 * Returns the source chat room for this event.
	 *
	 * @return the <code>ChatRoom</code> associated with this event.
	 */
	public ChatRoom getSourceChatRoom()
	{
		return (ChatRoom) getSource();
	}

	/**
	 * Returns a String representation of this event.
	 *
	 * @return String representation of this event
	 */
	@Override
	public String toString()
	{
		return "ChatRoomPropertyChangeEvent[type=" + this.getPropertyName() + " sourceRoom="
			+ this.getSource() + "oldValue=" + this.getOldValue() + "newValue="
			+ this.getNewValue() + "]";
	}
}
