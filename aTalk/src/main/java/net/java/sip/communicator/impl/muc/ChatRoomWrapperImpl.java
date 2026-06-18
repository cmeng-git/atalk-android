/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
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

import androidx.annotation.NonNull;

import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;

import net.java.sip.communicator.service.msghistory.MessageHistoryService;
import net.java.sip.communicator.service.muc.ChatRoomListChangeEvent;
import net.java.sip.communicator.service.muc.ChatRoomProviderWrapper;
import net.java.sip.communicator.service.muc.ChatRoomWrapper;
import net.java.sip.communicator.service.muc.MUCService;
import net.java.sip.communicator.service.protocol.ChatRoom;
import net.java.sip.communicator.service.protocol.ProtocolProviderService;
import net.java.sip.communicator.util.ConfigurationUtils;

import org.atalk.android.gui.AppGUIActivator;
import org.atalk.util.event.PropertyChangeNotifier;

import org.apache.commons.lang3.StringUtils;

import org.jxmpp.jid.BareJid;
import org.jxmpp.jid.EntityBareJid;
import org.jxmpp.jid.impl.JidCreate;
import org.jxmpp.jid.parts.Resourcepart;
import org.jxmpp.stringprep.XmppStringprepException;
import org.jxmpp.util.XmppStringUtils;

import timber.log.Timber;

/**
 * The <code>ChatRoomWrapper</code> is the representation of the <code>ChatRoom</code> in the GUI. It
 * stores the information for the chat room even when the corresponding protocol provider is not connected.
 *
 * @author Yana Stamcheva
 * @author Damian Minkov
 * @author Eng Chong Meng
 */
public class ChatRoomWrapperImpl extends PropertyChangeNotifier implements ChatRoomWrapper {
    /**
     * The protocol provider to which the corresponding chat room belongs.
     */
    private final ChatRoomProviderWrapper parentProvider;

    private final ProtocolProviderService mPPS;

    /**
     * The number of unread messages
     */
    private int unreadCount = 0;

    /**
     * The room that is wrapped.
     */
    private ChatRoom chatRoom;

    /**
     * The room Name.
     */
    private final String chatRoomId;

    /**
     * The property we use to store values in configuration service.
     */
    private static final String AUTOJOIN_PROPERTY_NAME = "autoJoin";

    /**
     * The property we use to store values in configuration service.
     */
    private static final String BOOKMARK_PROPERTY_NAME = "bookmark";

    private static final String TTS_ENABLE = "tts_Enable";
    private static final String TRANSLATE_SEND_ENABLE = "translate_send_enable";
    private static final String TRANSLATE_RECEIVE_ENABLE = "translate_receive_enable";

    /**
     * ChatRoom member presence status change
     */
    private static final String ROOM_STATUS_ENABLE = "room_status_Enable";

    /**
     * The participant nickName.
     */
    private String mNickName = null;

    private String bookmarkName = null;

    private Boolean isTtsEnable = null;
    private Boolean translateSend = null;
    private Boolean translateReceive = null;

    private Boolean isRoomStatusEnable;

    /**
     * As isAutoJoin can be called from GUI many times we store its value once retrieved to
     * minimize calls to configuration service. Set to null to indicate not initialize
     */
    private Boolean isAutoJoin;

    private Boolean bookmark = null;

    /**
     * By default all chat rooms are persistent from UI point of view. But we can override this
     * and force not saving it. If not overridden we query the wrapped room.
     */
    private Boolean persistent = null;

    /**
     * The prefix needed by the credential storage service to store the password of the chatRoom.
     * cmeng: passwordPrefix = sessionUuid
     */
    private String passwordPrefix;

    /**
     * The property change listener for the message service.
     */
    private final PropertyChangeListener propertyListener = new PropertyChangeListener() {
        @Override
        public void propertyChange(PropertyChangeEvent e) {
            MessageHistoryService mhs = MUCActivator.getMessageHistoryService();
            if (!mhs.isHistoryLoggingEnabled() || !mhs.isHistoryLoggingEnabled(getChatRoomId())) {
                MUCService.setChatRoomAutoOpenOption(mPPS, getChatRoomId(), MUCService.OPEN_ON_ACTIVITY);
            }
        }
    };

    /**
     * Creates a <code>ChatRoomWrapper</code> by specifying the protocol provider and the name of the chatRoom.
     *
     * @param parentProvider the protocol provider to which the corresponding chat room belongs
     * @param chatRoomId the identifier of the corresponding chat room
     */
    public ChatRoomWrapperImpl(ChatRoomProviderWrapper parentProvider, String chatRoomId) {
        this.parentProvider = parentProvider;
        this.chatRoomId = chatRoomId;
        this.mPPS = parentProvider.getProtocolProvider();

        String val = ConfigurationUtils.getChatRoomProperty(mPPS, chatRoomId, ROOM_STATUS_ENABLE);
        isRoomStatusEnable = StringUtils.isNotEmpty(val) && Boolean.parseBoolean(val);

        val = ConfigurationUtils.getChatRoomProperty(mPPS, chatRoomId, AUTOJOIN_PROPERTY_NAME);
        isAutoJoin = StringUtils.isNotEmpty(val) && Boolean.parseBoolean(val);

        // Request for passwordPrefix only if chatRoomID is not a serviceName
        if (chatRoomId.contains("@")) {
            passwordPrefix = ConfigurationUtils.getChatRoomPrefix(mPPS, chatRoomId) + ".password";
        }
        MUCActivator.getConfigurationService().addPropertyChangeListener(
                MessageHistoryService.PNAME_IS_MESSAGE_HISTORY_ENABLED, propertyListener);
        MUCActivator.getConfigurationService().addPropertyChangeListener(
                MessageHistoryService.PNAME_IS_MESSAGE_HISTORY_PER_CONTACT_ENABLED_PREFIX
                        + "." + chatRoomId, propertyListener);
    }

    /**
     * Creates a <code>ChatRoomWrapper</code> by specifying the corresponding chat room.
     *
     * @param parentProvider the protocol provider to which the corresponding chat room belongs
     * @param chatRoom the chat room to which this wrapper corresponds.
     */
    public ChatRoomWrapperImpl(ChatRoomProviderWrapper parentProvider, ChatRoom chatRoom) {
        this(parentProvider, chatRoom.getIdentifier().toString());
        this.chatRoom = chatRoom;
    }

    /**
     * Returns the <code>ChatRoom</code> that this wrapper represents.
     *
     * @return the <code>ChatRoom</code> that this wrapper represents.
     */
    public ChatRoom getChatRoom() {
        return chatRoom;
    }

    /**
     * Sets the <code>ChatRoom</code> that this wrapper represents.
     *
     * @param chatRoom the chat room
     */
    public void setChatRoom(ChatRoom chatRoom) {
        this.chatRoom = chatRoom;
    }

    /**
     * cmeng: Get the chatRoom name - current same as chatRoomID.
     *
     * @return the chat room name
     */
    public String getChatRoomName() {
        if (chatRoom != null)
            return chatRoom.getName();

        return chatRoomId;
    }

    /**
     * Returns the identifier of the chat room.
     *
     * @return the identifier of the chat room
     */
    public String getChatRoomId() {
        return chatRoomId;
    }

    /**
     * Get the account User BareJid
     *
     * @return the account user BareJid
     */
    public BareJid getUser() {
        return mPPS.getOurJid().asBareJid();
    }

    /**
     * Set the unread message count for this wrapper represent
     *
     * @param count unread message count
     */
    public void setUnreadCount(int count) {
        unreadCount = count;
    }

    /**
     * Returns the unread message count for this chat room
     *
     * @return the unread message count
     */
    public int getUnreadCount() {
        return unreadCount;
    }

    /**
     * Returns the chat room EntityBareJid.
     *
     * @return the chat room EntityBareJid
     */
    public EntityBareJid getEntityBareJid() {
        if (chatRoom != null)
            return chatRoom.getIdentifier();
        else {
            try {
                return JidCreate.entityBareFrom(chatRoomId);
            }
            catch (XmppStringprepException e) {
                Timber.w("Failed to get Room EntityBareJid: %s", e.getMessage());
            }
        }
        return null;
    }

    /**
     * Returns the parent protocol provider.
     *
     * @return the parent protocol provider
     */
    public ChatRoomProviderWrapper getParentProvider() {
        return this.parentProvider;
    }

    /**
     * Returns the protocol provider service.
     *
     * @return the protocol provider service
     */
    public ProtocolProviderService getProtocolProvider() {
        return mPPS;
    }

    /**
     * Returns {@code true} if the chat room is persistent, otherwise - returns {@code false}.
     *
     * @return {@code true} if the chat room is persistent, otherwise - returns {@code false}.
     */
    public boolean isPersistent() {
        if (persistent == null) {
            if (chatRoom != null)
                persistent = chatRoom.isPersistent();
            else
                return true;
        }
        return persistent;
    }

    /**
     * Change persistence of this room.
     *
     * @param value set persistent state.
     */
    public void setPersistent(boolean value) {
        this.persistent = value;
    }

    /**
     * Stores the password for the chat room.
     *
     * @param password the password to store
     */
    public void savePassword(String password) {
        MUCActivator.getCredentialsStorageService().storePassword(passwordPrefix, password);
    }

    /**
     * Returns the password for the chat room.
     *
     * @return the password
     */
    public String loadPassword() {
        return MUCActivator.getCredentialsStorageService().loadPassword(passwordPrefix);
    }

    /**
     * Removes the saved password for the chat room.
     */
    public void removePassword() {
        MUCActivator.getCredentialsStorageService().removePassword(passwordPrefix);
    }

    /**
     * Returns the user nickName as ResourcePart.
     *
     * @return the user nickName ResourcePart
     */
    public Resourcepart getNickResource() {
        String nickName = getNickName();
        try {
            return Resourcepart.from(nickName);
        }
        catch (XmppStringprepException e) {
            Timber.w("Failed to get Nick resourcePart: %s", e.getMessage());
        }
        return null;
    }

    /**
     * Returns the member nickName.
     *
     * @return the member nickName
     */
    public String getNickName() {
        if (StringUtils.isEmpty(mNickName)) {
            mNickName = ConfigurationUtils.getChatRoomProperty(mPPS, chatRoomId, ChatRoom.USER_NICK_NAME);
            if (StringUtils.isEmpty(mNickName))
                mNickName = getDefaultNickname(mPPS);
        }
        return mNickName;
    }

    /**
     * Sets the default value in the nickname field based on pps.
     *
     * @param pps the ProtocolProviderService
     */
    private String getDefaultNickname(ProtocolProviderService pps) {
        String nickName = AppGUIActivator.getGlobalDisplayDetailsService().getDisplayName(pps);
        if ((nickName == null) || nickName.contains("@"))
            nickName = XmppStringUtils.parseLocalpart(pps.getAccountID().getAccountJid());

        return nickName;
    }

    /**
     * Stores the nickName for the member.
     *
     * @param nickName the nickName to store
     */
    public void setNickName(String nickName) {
        mNickName = nickName;
        if (!isPersistent()) {
            setPersistent(true);
            ConfigurationUtils.saveChatRoom(mPPS, chatRoomId, chatRoomId);
        }
        ConfigurationUtils.updateChatRoomProperty(mPPS, chatRoomId, ChatRoom.USER_NICK_NAME, nickName);
    }

    /**
     * Return the bookmark name; default to conference local part if null.
     *
     * @return the bookmark name
     */
    public String getBookmarkName() {
        return StringUtils.isEmpty(bookmarkName)
                ? chatRoomId.split("@")[0] : bookmarkName;
    }

    /**
     * set the bookmark name.
     *
     * @param value the bookmark name to set
     */
    public void setBookmarkName(String value) {
        bookmarkName = value;
        if (!isPersistent()) {
            setPersistent(true);
            ConfigurationUtils.saveChatRoom(mPPS, chatRoomId, chatRoomId);
        }
        ConfigurationUtils.updateChatRoomProperty(mPPS, chatRoomId, ChatRoom.CHATROOM_NAME, null);
    }

    /**
     * Is room set to auto join on start-up (return autoJoin may be null).
     *
     * @return is auto joining enabled.
     */
    public boolean isAutoJoin() {
        return isAutoJoin;
    }

    /**
     * Changes auto join value in configuration service.
     *
     * @param value change of auto join property.
     */
    public void setAutoJoin(boolean value) {

        isAutoJoin = value;
        // as the user wants to autoJoin this room and it maybe already created as non-persistent
        // we must set it persistent and store it
        if (!isPersistent()) {
            setPersistent(true);
            ConfigurationUtils.saveChatRoom(mPPS, chatRoomId, chatRoomId);
        }

        ConfigurationUtils.updateChatRoomProperty(mPPS, chatRoomId, AUTOJOIN_PROPERTY_NAME, Boolean.toString(isAutoJoin));
        MUCActivator.getMUCService().fireChatRoomListChangedEvent(this, ChatRoomListChangeEvent.CHAT_ROOM_CHANGED);
    }

    /**
     * Is access on start-up (return bookmarked may be null).
     *
     * @return if the charRoomWrapper is bookmarked.
     */
    public boolean isBookmarked() {
        if (bookmark == null) {
            String val = ConfigurationUtils.getChatRoomProperty(mPPS, chatRoomId, BOOKMARK_PROPERTY_NAME);
            bookmark = StringUtils.isNotEmpty(val) && Boolean.parseBoolean(val);
        }
        return bookmark;
    }

    /**
     * Changes bookmark value in configuration service.
     *
     * @param value change of bookmark property.
     */
    public void setBookmark(boolean value) {
        if (isBookmarked() == value)
            return;

        bookmark = value;
        // as the user wants to bookmark this room and it maybe already created as non persistent
        // we must set it persistent and store it
        if (!isPersistent()) {
            setPersistent(true);
            ConfigurationUtils.saveChatRoom(mPPS, chatRoomId, chatRoomId);
        }

        if (value) {
            ConfigurationUtils.updateChatRoomProperty(mPPS, chatRoomId, BOOKMARK_PROPERTY_NAME, Boolean.toString(bookmark));
        }
        else {
            ConfigurationUtils.updateChatRoomProperty(mPPS, chatRoomId, BOOKMARK_PROPERTY_NAME, null);
        }
    }

    /**
     * When access on start-up, return ttsEnable may be null.
     *
     * @return true if chatroom tts is enabled.
     */
    public boolean isTtsEnable() {
        if (isTtsEnable == null) {
            String val = ConfigurationUtils.getChatRoomProperty(mPPS, chatRoomId, TTS_ENABLE);
            isTtsEnable = StringUtils.isNotEmpty(val) && Boolean.parseBoolean(val);
        }
        return isTtsEnable;
    }

    /**
     * Change chatroom tts enable value in configuration service.
     * Null value in DB is considered as false.
     *
     * @param value change of tts enable property.
     */
    public void setTtsEnable(boolean value) {
        if (isTtsEnable != value) {
            isTtsEnable = value;
            if (value) {
                ConfigurationUtils.updateChatRoomProperty(mPPS, chatRoomId, TTS_ENABLE, Boolean.toString(true));
            }
            else {
                ConfigurationUtils.updateChatRoomProperty(mPPS, chatRoomId, TTS_ENABLE, null);
            }
        }
    }

    /**
     * Check this chatRoom Language Translate send status
     * When access on start-up, return translation_send_enable may be null.
     * Null value in DB is considered as false
     *
     * @return true of this ChatRoom Language Translation send is enable.
     */
    public boolean isTranslateSend() {
        if (translateSend == null) {
            String val = ConfigurationUtils.getChatRoomProperty(mPPS, chatRoomId, TRANSLATE_SEND_ENABLE);
            translateSend = StringUtils.isNotEmpty(val) && Boolean.parseBoolean(val);
        }
        return translateSend;
    }

    /**
     * Set chatroom Language Translate send status
     *
     * @param value Language Translation status.
     */
    public void setTranslateSend(boolean value) {
        if (translateSend != value) {
            translateSend = value;
            if (value) {
                ConfigurationUtils.updateChatRoomProperty(mPPS, chatRoomId, TRANSLATE_SEND_ENABLE, Boolean.toString(true));
            }
            else {
                ConfigurationUtils.updateChatRoomProperty(mPPS, chatRoomId, TRANSLATE_SEND_ENABLE, null);
            }
        }
    }

    /**
     * Check this chatroom Language Translate receive status
     * When access on start-up, return translation_receive_enable may be null.
     * Null value in DB is considered as false
     *
     * @return true if chatroom Language Translation receive is enable.
     */
    public boolean isTranslateReceive() {
        if (translateReceive == null) {
            String val = ConfigurationUtils.getChatRoomProperty(mPPS, chatRoomId, TRANSLATE_RECEIVE_ENABLE);
            translateReceive = StringUtils.isNotEmpty(val) && Boolean.parseBoolean(val);
        }
        return translateReceive;
    }

    /**
     * Set chatroom Language Translate receive status
     *
     * @param value Language Translation status.
     */
    public void setTranslateReceive(boolean value) {
        if (translateReceive != value) {
            translateReceive = value;
            if (value) {
                ConfigurationUtils.updateChatRoomProperty(mPPS, chatRoomId, TRANSLATE_RECEIVE_ENABLE, Boolean.toString(true));
            }
            else {
                ConfigurationUtils.updateChatRoomProperty(mPPS, chatRoomId, TRANSLATE_RECEIVE_ENABLE, null);
            }
        }
    }

    /**
     * Get the roomStatusEnable status
     *
     * @return true if chatroom tts is enabled.
     */
    public boolean isRoomStatusEnable() {
        return isRoomStatusEnable;
    }

    /**
     * Change chatroom status enable value in configuration service. Null value in DB is considered as false.
     *
     * @param value change of room status enable property.
     */
    public void setRoomStatusEnable(boolean value) {
        isRoomStatusEnable = value;
        ConfigurationUtils.updateChatRoomProperty(mPPS, chatRoomId, ROOM_STATUS_ENABLE, Boolean.toString(isRoomStatusEnable));
    }

    /**
     * Removes the listeners.
     */
    public void removeListeners() {
        MUCActivator.getConfigurationService().removePropertyChangeListener(
                MessageHistoryService.PNAME_IS_MESSAGE_HISTORY_ENABLED, propertyListener);
        MUCActivator.getConfigurationService().removePropertyChangeListener(
                MessageHistoryService.PNAME_IS_MESSAGE_HISTORY_PER_CONTACT_ENABLED_PREFIX + "."
                        + chatRoomId, propertyListener);
    }

    /**
     * Fire property change.
     *
     * @param property chatRoom property that has been changed
     */
    public void firePropertyChange(String property) {
        super.firePropertyChange(property, null, null);
    }

    @Override
    public int compareTo(@NonNull ChatRoomWrapper o) {
        ChatRoomWrapperImpl target = (ChatRoomWrapperImpl) o;

//		int isOnline = (contactsOnline > 0) ? 1 : 0;
//		int targetIsOnline = (target.contactsOnline > 0) ? 1 : 0;
//		return ((10 - isOnline) - (10 - targetIsOnline)) * 100000000
//				+ getDisplayName().compareToIgnoreCase(target.getDisplayName()) * 10000
//				+ getMetaUID().compareTo(target.getMetaUID());

        return chatRoomId.compareToIgnoreCase(target.getChatRoomId());
    }
}
