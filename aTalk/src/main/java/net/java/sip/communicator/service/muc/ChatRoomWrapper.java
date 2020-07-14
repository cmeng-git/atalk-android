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
package net.java.sip.communicator.service.muc;

import net.java.sip.communicator.service.protocol.ChatRoom;
import net.java.sip.communicator.service.protocol.ProtocolProviderService;

import org.jxmpp.jid.BareJid;
import org.jxmpp.jid.EntityBareJid;
import org.jxmpp.jid.parts.Resourcepart;

import java.beans.PropertyChangeListener;

/**
 * The <tt>ChatRoomWrapper</tt> is the representation of the <tt>ChatRoom</tt> in the GUI. It
 * stores the information for the chat room even when the corresponding protocol provider is not connected.
 *
 * @author Yana Stamcheva
 * @author Damian Minkov
 * @author Eng Chong Meng
 */
public interface ChatRoomWrapper extends Comparable<ChatRoomWrapper>
{
    /**
     * Property to be fired when successfully joined to chat room.
     */
    String JOIN_SUCCESS_PROP = "Success";

    /**
     * Property to be fired when authentication failed while joining a chat room.
     */
    String JOIN_AUTHENTICATION_FAILED_PROP = "AuthenticationFailed";

    /**
     * Property to be fired when chat room captcha verification failed.
     */
    String JOIN_CAPTCHA_VERIFICATION_PROP = "CaptchaVerification";

    /**
     * Property to be fired when chat room requires registration and we failed while joining the chat room.
     */
    String JOIN_REGISTRATION_REQUIRED_PROP = "RegistrationRequired";

    /**
     * Property to be fired when provider is not registered while joining a chat room.
     */
    String JOIN_PROVIDER_NOT_REGISTERED_PROP = "ProviderNotRegistered";

    /**
     * Property to be fired when we try to join twice the same chat room.
     */
    String JOIN_SUBSCRIPTION_ALREADY_EXISTS_PROP = "SubscriptionAlreadyExists";

    /**
     * Property to be fired when we do not have enough privileges to perform certain task
     * e.g. Only moderators are allowed to change the subject in this room
     */
    String NOT_ENOUGH_PRIVILEGES = "NotEnoughPrivileges";

    /**
     * Property to be fired when unknown error occurred while joining a chat room.
     */
    String JOIN_UNKNOWN_ERROR_PROP = "UnknownError";

    /**
     * Returns the <tt>ChatRoom</tt> that this wrapper represents.
     *
     * @return the <tt>ChatRoom</tt> that this wrapper represents.
     */
    ChatRoom getChatRoom();

    /**
     * Returns the chat room EntityBareJid.
     *
     * @return the chat room EntityBareJid
     */
    EntityBareJid getEntityBareJid();

    /**
     * Sets the <tt>ChatRoom</tt> that this wrapper represents.
     *
     * @param chatRoom the chat room
     */
    void setChatRoom(ChatRoom chatRoom);

    /**
     * Returns the chat room name.
     *
     * @return the chat room name
     */
    String getChatRoomName();

    /**
     * Get the account User BareJid
     *
     * @return the account user BareJid
     */
    BareJid getUser();

    /**
     * Set the unread message count for this wrapper represent
     *
     * @param count unread message count
     */
    void setUnreadCount(int count);

    /**
     * Returns the unread message count for this chat room
     *
     * @return the unread message count
     */
    int getUnreadCount();

    /**
     * Returns the identifier of the chat room.
     *
     * @return the identifier of the chat room
     */
    String getChatRoomID();

    /**
     * Returns the parent protocol provider.
     *
     * @return the parent protocol provider
     */
    ChatRoomProviderWrapper getParentProvider();

    /**
     * Returns the protocol provider service corresponding to this  wrapper.
     *
     * @return the protocol provider service corresponding to this server wrapper.
     */
    ProtocolProviderService getProtocolProvider();

    /**
     * Returns <code>true</code> if the chat room is persistent, otherwise - returns <code>false </code>.
     *
     * @return <code>true</code> if the chat room is persistent, otherwise - returns <code>false</code>.
     */
    boolean isPersistent();

    /**
     * Change persistence of this room.
     *
     * @param value set persistent state.
     */
    void setPersistent(boolean value);

    /**
     * Returns the user nickName as ResourcePart.
     *
     * @return the user nickName ResourcePart
     */
    Resourcepart getNickResource();

    /**
     * Returns the user nickName.
     *
     * @return the user nickName
     */
    String getNickName();

    /**
     * Stores the nickName for the user.
     *
     * @param nickName the nickName to save
     */
    void setNickName(String nickName);

    /**
     * Returns the bookmark name.
     *
     * @return the bookmark name
     */
    String getBookmarkName();

    /**
     * set the bookmark name.
     *
     * @param name the bookmark name to set
     */
    void setBookmarkName(String name);

    /**
     * Stores the password for the chat room.
     *
     * @param password the password to store
     */
    void savePassword(String password);

    /**
     * Returns the password for the chat room.
     *
     * @return the password
     */
    String loadPassword();

    /**
     * Removes the saved password for the chat room.
     */
    void removePassword();

    /**
     * Is room set to auto join on start-up.
     *
     * @return is auto joining enabled.
     */
    boolean isAutoJoin();

    /**
     * Changes auto join value in configuration service.
     *
     * @param value change of auto join property.
     */
    void setAutoJoin(boolean value);

    /**
     * Is access on start-up (return bookmarked may be null).
     *
     * @return if the charRoomWrapper is bookmarked.
     */
    boolean isBookmarked();

    /**
     * Changes bookmark value in configuration service.
     *
     * @param value change of bookmark property.
     */
    void setBookmark(boolean value);

    /**
     * When access on start-up, return ttsEnable may be null. Null value in DB is considered as false
     *
     * @return true if charRoomWrapper tts is enabled.
     */
    boolean isTtsEnable();

    /**
     * Change charRoomWrapper tts enable value in configuration service.
     *
     * @param value change of tts enable property.
     */
    void setTtsEnable(boolean value);

    /**
     * When access on start-up, return value may be null. Null value in DB is considered as true
     *
     * @return true if charRoomWrapper Subject or Member Presence Status Change notification is enabled.
     */
    boolean isRoomStatusEnable();

    /**
     * Change charRoomWrapper subject or member status notification enable value in configuration service.
     *
     * @param value change value of property.
     */
    void setRoomStatusEnable(boolean value);

    /**
     * Removes the listeners.
     */
    void removeListeners();

    /**
     * Property changes for the room wrapper. Like join status changes.
     *
     * @param listener the listener to be notified.
     */
    void addPropertyChangeListener(PropertyChangeListener listener);

    /**
     * Removes property change listener.
     *
     * @param listener the listener to be notified.
     */
    void removePropertyChangeListener(PropertyChangeListener listener);
}
