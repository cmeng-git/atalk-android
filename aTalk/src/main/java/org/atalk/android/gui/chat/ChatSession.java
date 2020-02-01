/*
 * aTalk, android VoIP and Instant Messaging client
 * Copyright 2014 Eng Chong Meng
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.atalk.android.gui.chat;

import net.java.sip.communicator.service.msghistory.MessageHistoryService;
import net.java.sip.communicator.service.protocol.AccountID;
import net.java.sip.communicator.service.protocol.OperationSet;

import org.jivesoftware.smackx.muc.MultiUserChat;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;

/**
 * @author Yana Stamcheva
 * @author Lubomir Marinov
 * @author Eng Chong Meng
 */
public abstract class ChatSession
{
    public static final String TABLE_NAME = "chatSessions"; // chat session
    public static final String SESSION_UUID = "sessionUuid";
    public static final String ACCOUNT_UUID = "accountUuid";
    public static final String ACCOUNT_UID = "accountUid";  // AccountUID
    public static final String ENTITY_JID = "entityJid";    // entityJid for contact or chatRoom
    public static final String CREATED = "created";         // time stamp
    public static final String STATUS = "status";           // see ChatFragment#chatType (persistent)
    public static final String MODE = "mode";               // muc = 1
    public static final String ATTRIBUTES = "attributes";   // see below ATTR_*

    public static final String ATTR_NEXT_ENCRYPTION = "next_encryption";
    public static final String ATTR_MUC_PASSWORD = "muc_password";
    public static final String ATTR_MUTED_TILL = "muted_till";
    public static final String ATTR_ALWAYS_NOTIFY = "always_notify";
    public static final String ATTR_CRYPTO_TARGETS = "crypto_targets";
    public static final String ATTR_LAST_CLEAR_HISTORY = "last_clear_history";

    public static final String ATTR_AUTO_JOIN = "autoJoin";
    public static final String ATTR_AUTO_OPEN = "autoOpen";  // on-activity
    // public static final String ATTR_STATUS = "lastStatus";

    public static final int STATUS_AVAILABLE = 0;
    public static final int STATUS_ONLINE = 1;
    public static final int STATUS_ARCHIVED = 2;
    public static final int STATUS_DELETED = 3;

    public static final int MODE_SINGLE = 0;
    public static final int MODE_MULTI = 1;
    public static final int MODE_NPE = 2;    // non-persistent entity

    private String sessionUuid;
    private JSONObject attributes = new JSONObject();

    private static ChatSession chatSession;
    public final ArrayList<ChatMessageImpl> messages = new ArrayList<>();
    private AccountID accountId = null;
    private String nextMessage;
    private transient MultiUserChat mucOptions = null;

    /**
     * The persistable address of the contact from the session.
     */
    protected String persistableAddress = null;

    /**
     * The chat history filter for retrieving history messages.
     * MessageHistoryService in aTalk includes both the message and file history
     * Note: FileHistoryService.class is now handle within the MessageHistoryService.class
     */
    public static final String[] chatHistoryFilter = {MessageHistoryService.class.getName()};

    /**
     * The list of <tt>ChatContact</tt>s contained in this chat session.
     */
    protected final List<ChatContact<?>> chatParticipants = new ArrayList<>();

    /**
     * The list of <tt>ChatTransport</tt>s available in this session.
     */
    protected final List<ChatTransport> chatTransports = new LinkedList<>();

    /**
     * The list of all <tt>ChatSessionChangeListener</tt> registered to listen for transport modifications.
     */
    private final List<ChatSessionChangeListener> chatTransportChangeListeners = new ArrayList<>();

    /**
     * Returns the descriptor of this chat session.
     *
     * @return the descriptor of this chat session i.e. MetaContact or ChatRoomWrapper
     */
    public abstract Object getDescriptor();

    /**
     * Returns the chat identifier i.e. sessionUuid.
     *
     * @return the chat identifier
     */
    public String getChatId()
    {
        return sessionUuid;
    }

    /**
     * Returns the sessionUuid, uniquely identify this chat session. The sessionUuid is also use
     * as a link to retrieve all the chatMessages of this chatSession in the database
     *
     * @return the sessionUuid of the chat
     */
    public String getSessionUuid()
    {
        return this.sessionUuid;
    }

    /**
     * Returns the persistable address of the contact from the session.
     *
     * @return the persistable address.
     */
    public String getPersistableAddress()
    {
        return persistableAddress;
    }

    /**
     * Returns {@code true} if this chat session descriptor is persistent, otherwise returns {@code false}.
     *
     * @return {@code true} if this chat session descriptor is persistent, otherwise returns {@code false}.
     */
    public abstract boolean isDescriptorPersistent();

    /**
     * Returns an iterator to the list of all participants contained in this chat session.
     *
     * @return an iterator to the list of all participants contained in this chat session.
     */
    public Iterator<ChatContact<?>> getParticipants()
    {
        return chatParticipants.iterator();
    }

    /**
     * Returns all available chat transports for this chat session. Each chat transport is
     * corresponding to a protocol provider.
     *
     * @return all available chat transports for this chat session.
     */
    public Iterator<ChatTransport> getChatTransports()
    {
        return chatTransports.iterator();
    }

    /**
     * Returns the currently used transport for all operation within this chat session.
     *
     * @return the currently used transport for all operation within this chat session.
     */
    public abstract ChatTransport getCurrentChatTransport();

    /**
     * Returns a list of all <tt>ChatTransport</tt>s contained in this session supporting the given <tt>opSetClass</tt>.
     *
     * @param opSetClass the <tt>OperationSet</tt> class we're looking for
     * @return a list of all <tt>ChatTransport</tt>s contained in this session supporting the given <tt>opSetClass</tt>
     */
    public List<ChatTransport> getTransportsForOperationSet(
            Class<? extends OperationSet> opSetClass)
    {
        LinkedList<ChatTransport> opSetTransports = new LinkedList<>();

        for (ChatTransport transport : chatTransports) {
            if (transport.getProtocolProvider().getOperationSet(opSetClass) != null)
                opSetTransports.add(transport);
        }
        return opSetTransports;
    }

    /**
     * Returns the <tt>ChatPanel</tt> that provides the connection between this chat session and its UI.
     *
     * @return The <tt>ChatSessionRenderer</tt>.
     */
    public abstract ChatPanel getChatSessionRenderer();

    /**
     * Sets the transport that will be used for all operations within this chat session.
     *
     * @param chatTransport The transport to set as a default transport for this session.
     */
    public abstract void setCurrentChatTransport(ChatTransport chatTransport);

    /**
     * Returns the entityBareJid of the chat. If this chat panel corresponds to a single
     * chat it will return the entityBareJid of the <tt>MetaContact</tt>, otherwise it
     * will return the entityBareJid of the chat room.
     *
     * @return the entityBareJid of the chat
     */
    public abstract String getChatEntity();

    /**
     * Returns a collection of the last N number of history messages given by count.
     *
     * @param count The number of messages from history to return.
     * @return a collection of the last N number of messages given by count.
     */
    public abstract Collection<Object> getHistory(int count);

    /**
     * Returns a collection of the last N number of history messages given by count before the given date.
     *
     * @param date The date up to which we're looking for messages.
     * @param count The number of messages from history to return.
     * @return a collection of the last N number of messages given by count.
     */
    public abstract Collection<Object> getHistoryBeforeDate(Date date, int count);

    /**
     * Returns a collection of the last N number of history messages given by count after the given date.
     *
     * @param date The date from which we're looking for messages.
     * @param count The number of messages from history to return.
     * @return a collection of the last N number of messages given by count.
     */
    public abstract Collection<Object> getHistoryAfterDate(Date date, int count);

    /**
     * Returns the start date of the history of this chat session.
     *
     * @return the start date of the history of this chat session.
     */
    public abstract Date getHistoryStartDate();

    /**
     * Returns the end date of the history of this chat session.
     *
     * @return the end date of the history of this chat session.
     */
    public abstract Date getHistoryEndDate();

    /**
     * Returns the default mobile number used to send sms-es in this session.
     *
     * @return the default mobile number used to send sms-es in this session.
     */
    public abstract String getDefaultSmsNumber();

    /**
     * Sets the default mobile number used to send sms-es in this session.
     *
     * @param smsPhoneNumber The default mobile number used to send sms-es in this session.
     */
    public abstract void setDefaultSmsNumber(String smsPhoneNumber);

    /**
     * Disposes this chat session.
     */
    public abstract void dispose();

    /**
     * Returns the ChatTransport corresponding to the given descriptor.
     *
     * @param descriptor The descriptor of the chat transport we're looking for.
     * @param resourceName The entityBareJid of the resource if any, null otherwise
     * @return The ChatTransport corresponding to the given descriptor.
     */
    public ChatTransport findChatTransportForDescriptor(Object descriptor, String resourceName)
    {
        for (ChatTransport chatTransport : chatTransports) {
            String transportResName = chatTransport.getResourceName();

            if (chatTransport.getDescriptor().equals(descriptor)
                    && ((resourceName == null)
                    || ((transportResName != null)
                    && (transportResName.equals(resourceName)))))
                return chatTransport;
        }
        return null;
    }

    /**
     * Returns the status icon of this chat session.
     *
     * @return the status icon of this chat session.
     */
    public abstract byte[] getChatStatusIcon();

    /**
     * Returns the avatar icon of this chat session.
     *
     * @return the avatar icon of this chat session.
     */
    public abstract byte[] getChatAvatar();

    /**
     * Gets the indicator which determines whether a contact list of (multiple) participants is
     * supported by this {@code ChatSession}. For example, UI implementations may use the
     * indicator to determine whether UI elements should be created for the user to represent the
     * contact list of the participants in this {@code ChatSession}.
     *
     * @return <tt>true</tt> if this {@code ChatSession} supports a contact list of
     * (multiple) participants; otherwise, <tt>false</tt>
     */
    public abstract boolean isContactListSupported();

    /**
     * Adds the given {@link ChatSessionChangeListener} to this <tt>ChatSession</tt>.
     *
     * @param l the <tt>ChatSessionChangeListener</tt> to add
     */
    public void addChatTransportChangeListener(ChatSessionChangeListener l)
    {
        synchronized (chatTransportChangeListeners) {
            if (!chatTransportChangeListeners.contains(l))
                chatTransportChangeListeners.add(l);
        }
    }

    /**
     * Removes the given {@link ChatSessionChangeListener} to this <tt>ChatSession</tt>.
     *
     * @param l the <tt>ChatSessionChangeListener</tt> to add
     */
    public void removeChatTransportChangeListener(ChatSessionChangeListener l)
    {
        synchronized (chatTransportChangeListeners) {
            chatTransportChangeListeners.remove(l);
        }
    }

    /**
     * Fires a event that current ChatTransport has changed.
     */
    public void fireCurrentChatTransportChange()
    {
        List<ChatSessionChangeListener> listeners;
        synchronized (chatTransportChangeListeners) {
            listeners = new ArrayList<>(chatTransportChangeListeners);
        }

        for (ChatSessionChangeListener l : listeners)
            l.currentChatTransportChanged(this);
    }

    /**
     * Fires a event that current ChatTransport has been updated.
     */
    public void fireCurrentChatTransportUpdated(int eventID)
    {
        List<ChatSessionChangeListener> listeners;
        synchronized (chatTransportChangeListeners) {
            listeners = new ArrayList<>(chatTransportChangeListeners);
        }

        for (ChatSessionChangeListener l : listeners)
            l.currentChatTransportUpdated(eventID);
    }
}