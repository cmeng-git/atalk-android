/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package net.java.sip.communicator.service.muc;

import net.java.sip.communicator.service.protocol.ChatRoom;
import net.java.sip.communicator.service.protocol.ProtocolProviderService;

import java.util.List;

/**
 * @author Yana Stamcheva
 * @author Damian Minkov
 * @author Hristo Terezov
 * @author Eng Chong Meng
 */
public interface ChatRoomProviderWrapper
{
    /**
     * Returns the name of this chat room provider.
     *
     * @return the name of this chat room provider.
     */
    String getName();

    byte[] getIcon();

    byte[] getImage();

    /**
     * Returns the system room wrapper corresponding to this server.
     *
     * @return the system room wrapper corresponding to this server.
     */
    ChatRoomWrapper getSystemRoomWrapper();

    /**
     * Sets the system room corresponding to this server.
     *
     * @param systemRoom the system room to set
     */
    void setSystemRoom(ChatRoom systemRoom);

    /**
     * Returns the protocol provider service corresponding to this  wrapper.
     *
     * @return the protocol provider service corresponding to this server wrapper.
     */
    ProtocolProviderService getProtocolProvider();

    /**
     * Adds the given chat room to this chat room provider.
     *
     * @param chatRoom the chat room to add.
     */
    void addChatRoom(ChatRoomWrapper chatRoom);

    /**
     * Removes the given chat room from this provider.
     *
     * @param chatRoom the chat room to remove.
     */
    void removeChatRoom(ChatRoomWrapper chatRoom);

    /**
     * Returns <code>true</code> if the given chat room is contained in this
     * provider, otherwise - returns <code>false</code>.
     *
     * @param chatRoom the chat room to search for.
     * @return <code>true</code> if the given chat room is contained in this
     * provider, otherwise - returns <code>false</code>.
     */
    boolean containsChatRoom(ChatRoomWrapper chatRoom);

    /**
     * Returns the chat room wrapper contained in this provider that corresponds to the given chat room.
     *
     * @param chatRoom the chat room we're looking for.
     * @return the chat room wrapper contained in this provider that corresponds to the given chat room.
     */
    ChatRoomWrapper findChatRoomWrapperForChatRoom(ChatRoom chatRoom);

    /**
     * Returns the chat room wrapper contained in this provider that corresponds to the chat room with the given id.
     *
     * @param chatRoomID the id of the chat room we're looking for.
     * @return the chat room wrapper contained in this provider that corresponds to the given chat room id.
     */
    ChatRoomWrapper findChatRoomWrapperForChatRoomID(String chatRoomID);

    List<ChatRoomWrapper> getChatRooms();

    /**
     * Returns the number of chat rooms contained in this provider.
     *
     * @return the number of chat rooms contained in this provider.
     */
    int countChatRooms();

    ChatRoomWrapper getChatRoom(int index);

    /**
     * Returns the index of the given chat room in this provider.
     *
     * @param chatRoomWrapper the chat room to search for.
     * @return the index of the given chat room in this provider.
     */
    int indexOf(ChatRoomWrapper chatRoomWrapper);

    /**
     * Goes through the locally stored chat rooms list and for each
     * {@link ChatRoomWrapper} tries to find the corresponding server stored
     * {@link ChatRoom} in the specified operation set. Joins automatically all found chat rooms.
     */
    void synchronizeProvider();

    /**
     * Gets the user data associated with this instance and a specific key.
     *
     * @param key the key of the user data associated with this instance to be retrieved
     * @return an <code>Object</code> which represents the value associated with
     * this instance and the specified <code>key</code>; <tt>null</tt>
     * if no association with the specified <code>key</code> exists in this instance
     */
    Object getData(Object key);

    /**
     * Sets a user-specific association in this instance in the form of a
     * key-value pair. If the specified <code>key</code> is already associated
     * in this instance with a value, the existing value is overwritten with the specified <code>value</code>.
     * <p>
     * The user-defined association created by this method and stored in this
     * instance is not serialized by this instance and is thus only meant for runtime use.
     * </p>
     * <p>
     * The storage of the user data is implementation-specific and is thus not
     * guaranteed to be optimized for execution time and memory use.
     * </p>
     *
     * @param key the key to associate in this instance with the specified value
     * @param value the value to be associated in this instance with the specified <code>key</code>
     */
    void setData(Object key, Object value);
}
