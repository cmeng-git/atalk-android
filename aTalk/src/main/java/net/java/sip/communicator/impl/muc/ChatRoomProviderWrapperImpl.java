/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file
 * except in compliance with the License. You may
 * obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the
 * License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for
 * the specific language governing permissions
 * and limitations under the License.
 */
package net.java.sip.communicator.impl.muc;

import android.text.TextUtils;

import net.java.sip.communicator.service.muc.ChatRoomProviderWrapper;
import net.java.sip.communicator.service.muc.ChatRoomWrapper;
import net.java.sip.communicator.service.protocol.*;

import java.util.*;

/**
 * @author Yana Stamcheva
 * @author Damian Minkov
 * @author Hristo Terezov
 * @author Eng Chong Meng
 */
public class ChatRoomProviderWrapperImpl implements ChatRoomProviderWrapper
{
    private final ProtocolProviderService protocolProvider;
    private final ChatRoomWrapper systemRoomWrapper;
    private final List<ChatRoomWrapper> chatRoomsOrderedCopy = new LinkedList<>();

    /**
     * The user-specific key-value associations stored in this instance.
     * <p>
     * Like the Widget implementation of Eclipse SWT, the storage type takes into account that
     * there are likely to be many {@code MetaContactGroupImpl} instances and
     * {@code Map}s are thus likely to impose increased memory use. While an array may
     * very well perform worse than a {@code Map} with respect to search, the mechanism of
     * user-defined key-value associations explicitly states that it is not guaranteed to be
     * optimized for any particular use and only covers the most basic cases and
     * performance-savvy code will likely implement a more optimized solution anyway.
     * </p>
     */
    private Object[] data;

    /**
     * Creates an instance of <tt>ChatRoomProviderWrapper</tt> by specifying the protocol
     * provider, corresponding to the multi user chat account.
     *
     * @param protocolProvider protocol provider, corresponding to the multi user chat account.
     */
    public ChatRoomProviderWrapperImpl(ProtocolProviderService protocolProvider)
    {
        this.protocolProvider = protocolProvider;
        String accountIdService = protocolProvider.getAccountID().getService();
        this.systemRoomWrapper = new ChatRoomWrapperImpl(this, accountIdService);
    }

    /**
     * Returns the name of this chat room provider.
     *
     * @return the name of this chat room provider.
     */
    public String getName()
    {
        return protocolProvider.getProtocolDisplayName();
    }

    public byte[] getIcon()
    {
        return protocolProvider.getProtocolIcon().getIcon(ProtocolIcon.ICON_SIZE_64x64);
    }

    public byte[] getImage()
    {
        byte[] logoImage = null;
        ProtocolIcon protocolIcon = protocolProvider.getProtocolIcon();

        if (protocolIcon.isSizeSupported(ProtocolIcon.ICON_SIZE_64x64))
            logoImage = protocolIcon.getIcon(ProtocolIcon.ICON_SIZE_64x64);
        else if (protocolIcon.isSizeSupported(ProtocolIcon.ICON_SIZE_48x48))
            logoImage = protocolIcon.getIcon(ProtocolIcon.ICON_SIZE_48x48);

        return logoImage;
    }

    /**
     * Returns the system room wrapper corresponding to this server.
     *
     * @return the system room wrapper corresponding to this server.
     */
    public ChatRoomWrapper getSystemRoomWrapper()
    {
        return systemRoomWrapper;
    }

    /**
     * Sets the system room corresponding to this server.
     *
     * @param systemRoom the system room to set
     */
    public void setSystemRoom(ChatRoom systemRoom)
    {
        systemRoomWrapper.setChatRoom(systemRoom);
    }

    /**
     * Returns the protocol provider service corresponding to this server wrapper.
     *
     * @return the protocol provider service corresponding to this server wrapper.
     */
    public ProtocolProviderService getProtocolProvider()
    {
        return protocolProvider;
    }

    /**
     * Adds the given chat room to this chat room provider.
     *
     * @param chatRoom the chat room to add.
     */
    public void addChatRoom(ChatRoomWrapper chatRoom)
    {
        this.chatRoomsOrderedCopy.add(chatRoom);
    }

    /**
     * Removes the given chat room from this provider.
     *
     * @param chatRoom the chat room to remove.
     */
    public void removeChatRoom(ChatRoomWrapper chatRoom)
    {
        this.chatRoomsOrderedCopy.remove(chatRoom);
    }

    /**
     * Returns {@code true} if the given chat room is contained in this provider, otherwise - returns {@code false}.
     *
     * @param chatRoom the chat room to search for.
     * @return {@code true} if the given chat room is contained in this provider, otherwise - returns {@code false}.
     */
    public boolean containsChatRoom(ChatRoomWrapper chatRoom)
    {
        synchronized (chatRoomsOrderedCopy) {
            return chatRoomsOrderedCopy.contains(chatRoom);
        }
    }

    /**
     * Returns the chat room wrapper contained in this provider that corresponds to the given chat room.
     *
     * @param chatRoom the chat room we're looking for.
     * @return the chat room wrapper contained in this provider that corresponds to the given chat room.
     */
    public ChatRoomWrapper findChatRoomWrapperForChatRoom(ChatRoom chatRoom)
    {
        return findChatRoomWrapperForChatRoomID(chatRoom.getName());
    }

    /**
     * Returns the chat room wrapper contained in this provider that corresponds to the chat room with the given id.
     *
     * @param chatRoomID the id of the chat room we're looking for.
     * @return the chat room wrapper contained in this provider that corresponds to the given chat room id.
     */
    public ChatRoomWrapper findChatRoomWrapperForChatRoomID(String chatRoomID)
    {
        // Compare ids, cause saved chatRooms don't have ChatRoom object but Id's are the same.
        for (ChatRoomWrapper chatRoomWrapper : chatRoomsOrderedCopy) {
            if (chatRoomWrapper.getChatRoomID().equals(chatRoomID)) {
                return chatRoomWrapper;
            }
        }
        return null;
    }

    public List<ChatRoomWrapper> getChatRooms()
    {
        return chatRoomsOrderedCopy;
    }

    /**
     * Returns the number of chat rooms contained in this provider.
     *
     * @return the number of chat rooms contained in this provider.
     */
    public int countChatRooms()
    {
        return chatRoomsOrderedCopy.size();
    }

    public ChatRoomWrapper getChatRoom(int index)
    {
        return chatRoomsOrderedCopy.get(index);
    }

    /**
     * Returns the index of the given chat room in this provider.
     *
     * @param chatRoomWrapper the chat room to search for.
     * @return the index of the given chat room in this provider.
     */
    public int indexOf(ChatRoomWrapper chatRoomWrapper)
    {
        return chatRoomsOrderedCopy.indexOf(chatRoomWrapper);
    }

    /**
     * Implements {@link ChatRoomProviderWrapper#getData(Object)}.
     *
     * @return the data value corresponding to the given key
     */
    public Object getData(Object key)
    {
        if (key == null)
            throw new NullPointerException("key");

        int index = dataIndexOf(key);
        return (index == -1) ? null : data[index + 1];
    }

    /**
     * Implements {@link ChatRoomProviderWrapper#setData(Object, Object)}.
     *
     * @param key the of the data
     * @param value the value of the data
     */
    public void setData(Object key, Object value)
    {
        if (key == null)
            throw new NullPointerException("key");

        int index = dataIndexOf(key);

        if (index == -1) {
            /*
             * If value is null, remove the association with key (or just don't add it).
             */
            if (data == null) {
                if (value != null)
                    data = new Object[]{key, value};
            }
            else if (value == null) {
                int length = data.length - 2;

                if (length > 0) {
                    Object[] newData = new Object[length];

                    System.arraycopy(data, 0, newData, 0, index);
                    System.arraycopy(data, index + 2, newData, index, length - index);
                    data = newData;
                }
                else
                    data = null;
            }
            else {
                int length = data.length;
                Object[] newData = new Object[length + 2];

                System.arraycopy(data, 0, newData, 0, length);
                data = newData;
                data[length++] = key;
                data[length] = value;
            }
        }
        else
            data[index + 1] = value;
    }

    /**
     * Determines the index in {@code #data} of a specific key.
     *
     * @param key the key to retrieve the index in {@code #data} of
     * @return the index in {@code #data} of the specified {@code key} if it is contained;
     * <tt>-1</tt> if {@code key} is not contained in {@code #data}
     */
    private int dataIndexOf(Object key)
    {
        if (data != null)
            for (int index = 0; index < data.length; index += 2)
                if (key.equals(data[index]))
                    return index;
        return -1;
    }

    /**
     * Goes through the locally stored chat rooms list and for each {@link ChatRoomWrapper}
     * tries to find the corresponding server stored {@link ChatRoom} in the specified operation set.
     * Joins automatically if enabled for all found chat rooms.
     */
    public void synchronizeProvider()
    {
        final OperationSetMultiUserChat groupChatOpSet = protocolProvider.getOperationSet(OperationSetMultiUserChat.class);

        for (ChatRoomWrapper chatRoomWrapper : chatRoomsOrderedCopy) {
            ChatRoom chatRoom = groupChatOpSet.findRoom(chatRoomWrapper.getEntityBareJid());
            if (chatRoom != null) {
                chatRoomWrapper.setChatRoom(chatRoom);
            }

            if (chatRoomWrapper.isAutoJoin()) {
                // For non-existent chat room, we must create it before joining
                if (chatRoom == null) {
                    chatRoomWrapper = MUCActivator.getMUCService().createChatRoom(chatRoomWrapper,
                            "auto joined", false, false, true);
                }

                String nickName = chatRoomWrapper.getNickName();
                String pwd = chatRoomWrapper.loadPassword();
                byte[] password = TextUtils.isEmpty(pwd) ? null : pwd.getBytes();

                MUCActivator.getMUCService().joinChatRoom(chatRoomWrapper, nickName, password);
            }
        }
    }
}
