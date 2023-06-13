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
package net.java.sip.communicator.impl.msghistory;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.text.TextUtils;

import net.java.sip.communicator.impl.protocol.jabber.ChatRoomJabberImpl;
import net.java.sip.communicator.impl.protocol.jabber.ChatRoomMemberJabberImpl;
import net.java.sip.communicator.impl.protocol.jabber.MessageJabberImpl;
import net.java.sip.communicator.impl.protocol.jabber.OperationSetPersistentPresenceJabberImpl;
import net.java.sip.communicator.service.contactlist.MetaContact;
import net.java.sip.communicator.service.contactlist.MetaContactGroup;
import net.java.sip.communicator.service.contactsource.ContactSourceService;
import net.java.sip.communicator.service.filehistory.FileRecord;
import net.java.sip.communicator.service.history.HistoryReader;
import net.java.sip.communicator.service.history.HistoryService;
import net.java.sip.communicator.service.history.event.HistorySearchProgressListener;
import net.java.sip.communicator.service.history.event.ProgressEvent;
import net.java.sip.communicator.service.msghistory.MessageHistoryAdvancedService;
import net.java.sip.communicator.service.msghistory.MessageHistoryService;
import net.java.sip.communicator.service.msghistory.event.MessageHistorySearchProgressListener;
import net.java.sip.communicator.service.muc.ChatRoomWrapper;
import net.java.sip.communicator.service.protocol.AbstractMessage;
import net.java.sip.communicator.service.protocol.AccountID;
import net.java.sip.communicator.service.protocol.AdHocChatRoom;
import net.java.sip.communicator.service.protocol.ChatRoom;
import net.java.sip.communicator.service.protocol.ChatRoomMember;
import net.java.sip.communicator.service.protocol.Contact;
import net.java.sip.communicator.service.protocol.IMessage;
import net.java.sip.communicator.service.protocol.OperationSetBasicInstantMessaging;
import net.java.sip.communicator.service.protocol.OperationSetContactCapabilities;
import net.java.sip.communicator.service.protocol.OperationSetMultiUserChat;
import net.java.sip.communicator.service.protocol.OperationSetPersistentPresence;
import net.java.sip.communicator.service.protocol.OperationSetPresence;
import net.java.sip.communicator.service.protocol.OperationSetSmsMessaging;
import net.java.sip.communicator.service.protocol.ProtocolProviderService;
import net.java.sip.communicator.service.protocol.event.AdHocChatRoomMessageDeliveredEvent;
import net.java.sip.communicator.service.protocol.event.AdHocChatRoomMessageDeliveryFailedEvent;
import net.java.sip.communicator.service.protocol.event.AdHocChatRoomMessageListener;
import net.java.sip.communicator.service.protocol.event.AdHocChatRoomMessageReceivedEvent;
import net.java.sip.communicator.service.protocol.event.ChatRoomMessageDeliveredEvent;
import net.java.sip.communicator.service.protocol.event.ChatRoomMessageDeliveryFailedEvent;
import net.java.sip.communicator.service.protocol.event.ChatRoomMessageListener;
import net.java.sip.communicator.service.protocol.event.ChatRoomMessageReceivedEvent;
import net.java.sip.communicator.service.protocol.event.LocalUserAdHocChatRoomPresenceChangeEvent;
import net.java.sip.communicator.service.protocol.event.LocalUserAdHocChatRoomPresenceListener;
import net.java.sip.communicator.service.protocol.event.LocalUserChatRoomPresenceChangeEvent;
import net.java.sip.communicator.service.protocol.event.LocalUserChatRoomPresenceListener;
import net.java.sip.communicator.service.protocol.event.MessageDeliveredEvent;
import net.java.sip.communicator.service.protocol.event.MessageDeliveryFailedEvent;
import net.java.sip.communicator.service.protocol.event.MessageListener;
import net.java.sip.communicator.service.protocol.event.MessageReceivedEvent;
import net.java.sip.communicator.util.UtilActivator;
import net.java.sip.communicator.util.account.AccountUtils;

import org.apache.commons.lang3.StringUtils;
import org.atalk.android.R;
import org.atalk.android.aTalkApp;
import org.atalk.android.gui.chat.ChatFragment;
import org.atalk.android.gui.chat.ChatMessage;
import org.atalk.android.gui.chat.ChatMessageImpl;
import org.atalk.android.gui.chat.ChatPanel;
import org.atalk.android.gui.chat.ChatSession;
import org.atalk.android.gui.chat.chatsession.ChatSessionFragment;
import org.atalk.android.gui.chat.chatsession.ChatSessionRecord;
import org.atalk.android.plugin.timberlog.TimberLog;
import org.atalk.persistance.DatabaseBackend;
import org.atalk.service.configuration.ConfigurationService;
import org.jivesoftware.smack.SmackException;
import org.jivesoftware.smack.packet.ExtensionElement;
import org.jivesoftware.smack.packet.Message;
import org.jivesoftware.smack.packet.Stanza;
import org.jivesoftware.smackx.forward.packet.Forwarded;
import org.jivesoftware.smackx.omemo.OmemoManager;
import org.jivesoftware.smackx.omemo.OmemoMessage;
import org.jivesoftware.smackx.omemo.element.OmemoElement;
import org.jivesoftware.smackx.omemo.exceptions.CorruptedOmemoKeyException;
import org.jivesoftware.smackx.omemo.exceptions.CryptoFailedException;
import org.jivesoftware.smackx.omemo.exceptions.NoRawSessionException;
import org.jivesoftware.smackx.omemo.util.OmemoConstants;
import org.jivesoftware.smackx.receipts.ReceiptReceivedListener;
import org.jivesoftware.smackx.sid.element.StanzaIdElement;
import org.jxmpp.jid.EntityBareJid;
import org.jxmpp.jid.EntityFullJid;
import org.jxmpp.jid.Jid;
import org.jxmpp.jid.impl.JidCreate;
import org.jxmpp.jid.parts.Resourcepart;
import org.jxmpp.stringprep.XmppStringprepException;
import org.jxmpp.util.XmppStringUtils;
import org.osgi.framework.BundleContext;
import org.osgi.framework.InvalidSyntaxException;
import org.osgi.framework.ServiceEvent;
import org.osgi.framework.ServiceListener;
import org.osgi.framework.ServiceReference;
import org.osgi.framework.ServiceRegistration;

import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.EventObject;
import java.util.HashSet;
import java.util.Hashtable;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import timber.log.Timber;

/**
 * The Message History Service stores messages exchanged through the various protocols Logs
 * messages for all protocol providers that support basic instant messaging (i.e. those that
 * implement OperationSetBasicInstantMessaging).
 *
 * @author Alexander Pelov
 * @author Damian Minkov
 * @author Lubomir Marinov
 * @author Valentin Martinet
 * @author Eng Chong Meng
 */
public class MessageHistoryServiceImpl implements MessageHistoryService,
        MessageHistoryAdvancedService, MessageListener, ChatRoomMessageListener,
        AdHocChatRoomMessageListener, ServiceListener, LocalUserChatRoomPresenceListener,
        LocalUserAdHocChatRoomPresenceListener, ReceiptReceivedListener {
    /**
     * Sort database message records by TimeStamp in ASC or DESC
     */
    private static final String ORDER_ASC = ChatMessage.TIME_STAMP + " ASC";
    private static final String ORDER_DESC = ChatMessage.TIME_STAMP + " DESC";
    /**
     * Indicates if history logging is enabled.
     */
    private static boolean isHistoryLoggingEnabled = true;
    /**
     * The BundleContext that we got from the OSGI bus.
     */
    private BundleContext bundleContext = null;
    private HistoryService historyService = null;
    private final Object syncRoot_HistoryService = new Object();
    private final Hashtable<MessageHistorySearchProgressListener, HistorySearchProgressListener> progressListeners
            = new Hashtable<>();
    private ConfigurationService configService;
    private MessageHistoryPropertyChangeListener msgHistoryPropListener;

    /**
     * The message source service, can be null if not enabled.
     */
    private MessageSourceService messageSourceService;

    /**
     * The message source service registration.
     */
    private ServiceRegistration<?> messageSourceServiceReg = null;

    private SQLiteDatabase mDB;
    private final ContentValues contentValues = new ContentValues();

    /**
     * Starts the service. Check the current registered protocol providers which supports
     * BasicIM and adds message listener to them
     *
     * @param bc BundleContext
     */
    public void start(BundleContext bc) {
        this.bundleContext = bc;
        mDB = DatabaseBackend.getWritableDB();

        ServiceReference<?> refConfig = bundleContext.getServiceReference(ConfigurationService.class.getName());
        configService = (ConfigurationService) bundleContext.getService(refConfig);

        // Check if the message history is enabled in the configuration service;
        // if not, then do not register the service.
        boolean isMessageHistoryEnabled = configService.getBoolean(
                MessageHistoryService.PNAME_IS_MESSAGE_HISTORY_ENABLED,
                Boolean.parseBoolean(MessageHistoryActivator.getResources().getSettingsString(
                        MessageHistoryService.PNAME_IS_MESSAGE_HISTORY_ENABLED)));

        // Load the "IS_MESSAGE_HISTORY_ENABLED" property.
        isHistoryLoggingEnabled = configService.getBoolean(
                MessageHistoryService.PNAME_IS_MESSAGE_HISTORY_ENABLED,
                Boolean.parseBoolean(UtilActivator.getResources().getSettingsString(
                        MessageHistoryService.PNAME_IS_MESSAGE_HISTORY_ENABLED)));

        // We are adding a property change listener in order to
        // listen for modifications of the isMessageHistoryEnabled property.
        msgHistoryPropListener = new MessageHistoryPropertyChangeListener();
        configService.addPropertyChangeListener(
                MessageHistoryService.PNAME_IS_MESSAGE_HISTORY_ENABLED, msgHistoryPropListener);

        if (isMessageHistoryEnabled) {
            Timber.d("Starting the msg history implementation.");
            this.loadMessageHistoryService();
        }
    }

    /**
     * Stops the service.
     *
     * @param bc BundleContext
     */
    public void stop(BundleContext bc) {
        if (configService != null)
            configService.removePropertyChangeListener(msgHistoryPropListener);

        stopMessageHistoryService();
    }

    /**
     * When new protocol provider is registered or removed; check and add/remove the listener if
     * has BasicIM support
     *
     * @param serviceEvent ServiceEvent received
     */
    public void serviceChanged(ServiceEvent serviceEvent) {
        Object sService = bundleContext.getService(serviceEvent.getServiceReference());
        Timber.log(TimberLog.FINER, "Received a service event for: %s", sService.getClass().getName());

        // we don't care if the source service is not a protocol provider
        if (!(sService instanceof ProtocolProviderService)) {
            return;
        }

        if (serviceEvent.getType() == ServiceEvent.REGISTERED) {
            Timber.d("Handling registration of a new Protocol Provider.");
            this.handleProviderAdded((ProtocolProviderService) sService);
        }
        else if (serviceEvent.getType() == ServiceEvent.UNREGISTERING) {
            this.handleProviderRemoved((ProtocolProviderService) sService);
        }
    }

    /**
     * Used to attach the Message History Service to existing or just registered protocol
     * provider. Checks if the provider has implementation of OperationSetBasicInstantMessaging
     *
     * @param provider ProtocolProviderService
     */
    private void handleProviderAdded(ProtocolProviderService provider) {
        Timber.d("Adding protocol provider %s", provider.getProtocolDisplayName());

        // check whether the provider has a basic im operation set
        OperationSetBasicInstantMessaging opSetIm = provider.getOperationSet(OperationSetBasicInstantMessaging.class);
        if (opSetIm != null) {
            opSetIm.addMessageListener(this);

            if (this.messageSourceService != null)
                opSetIm.addMessageListener(messageSourceService);
        }
        else {
            Timber.log(TimberLog.FINER, "Service did not have OperationSet BasicInstantMessaging.");
        }

        OperationSetSmsMessaging opSetSMS = provider.getOperationSet(OperationSetSmsMessaging.class);
        if (opSetSMS != null) {
            opSetSMS.addMessageListener(this);

            if (this.messageSourceService != null)
                opSetSMS.addMessageListener(messageSourceService);
        }
        else {
            Timber.log(TimberLog.FINER, "Service did not have OperationSet SmsMessaging.");
        }

        OperationSetMultiUserChat opSetMultiUChat = provider.getOperationSet(OperationSetMultiUserChat.class);
        if (opSetMultiUChat != null) {
            for (ChatRoom room : opSetMultiUChat.getCurrentlyJoinedChatRooms()) {
                room.addMessageListener(this);
            }
            opSetMultiUChat.addPresenceListener(this);
            if (messageSourceService != null)
                opSetMultiUChat.addPresenceListener(messageSourceService);
        }
        else {
            Timber.log(TimberLog.FINER, "Service did not have OperationSet MultiUserChat.");
        }

        if (messageSourceService != null) {
            OperationSetPresence opSetPresence = provider.getOperationSet(OperationSetPresence.class);
            if (opSetPresence != null) {
                opSetPresence.addContactPresenceStatusListener(messageSourceService);
                opSetPresence.addProviderPresenceStatusListener(messageSourceService);
                opSetPresence.addSubscriptionListener(messageSourceService);
            }

            /* cmeng - too earlier to trigger and not ready??? messageSourceService has its own
             * ProviderPresenceStatusListener#providerStatusChanged listener to take care.
             * Need to be registered and connected for retrieving muc recent messages
             */
            // messageSourceService.handleProviderAdded(provider, false);

            OperationSetContactCapabilities capOpSet = provider.getOperationSet(OperationSetContactCapabilities.class);
            if (capOpSet != null) {
                capOpSet.addContactCapabilitiesListener(messageSourceService);
            }
        }
    }

    /**
     * Removes the specified provider from the list of currently known providers and ignores all
     * the messages exchanged by it
     *
     * @param provider the ProtocolProviderService that has been unregistered.
     */
    private void handleProviderRemoved(ProtocolProviderService provider) {
        OperationSetBasicInstantMessaging opSetIm = provider.getOperationSet(OperationSetBasicInstantMessaging.class);
        if (opSetIm != null) {
            opSetIm.removeMessageListener(this);

            if (this.messageSourceService != null)
                opSetIm.removeMessageListener(messageSourceService);
        }

        OperationSetSmsMessaging opSetSMS = provider.getOperationSet(OperationSetSmsMessaging.class);
        if (opSetSMS != null) {
            opSetSMS.removeMessageListener(this);

            if (this.messageSourceService != null)
                opSetSMS.removeMessageListener(messageSourceService);
        }

        OperationSetMultiUserChat opSetMultiUChat = provider.getOperationSet(OperationSetMultiUserChat.class);
        if (opSetMultiUChat != null) {
            for (ChatRoom room : opSetMultiUChat.getCurrentlyJoinedChatRooms()) {
                room.removeMessageListener(this);
            }
            opSetMultiUChat.removePresenceListener(this);

            if (messageSourceService != null)
                opSetMultiUChat.removePresenceListener(messageSourceService);
        }

        if (messageSourceService != null) {
            OperationSetPresence opSetPresence = provider.getOperationSet(OperationSetPresence.class);
            if (opSetPresence != null) {
                opSetPresence.removeContactPresenceStatusListener(messageSourceService);
                opSetPresence.removeProviderPresenceStatusListener(messageSourceService);
                opSetPresence.removeSubscriptionListener(messageSourceService);
            }

            messageSourceService.handleProviderRemoved(provider);
            OperationSetContactCapabilities capOpSet = provider.getOperationSet(OperationSetContactCapabilities.class);
            if (capOpSet != null) {
                capOpSet.removeContactCapabilitiesListener(messageSourceService);
            }
        }
    }

    /**
     * Set the configuration service.
     *
     * @param historyService HistoryService
     */
    public void setHistoryService(HistoryService historyService) {
        synchronized (this.syncRoot_HistoryService) {
            this.historyService = historyService;
            Timber.d("New history service registered.");
        }
    }

    /**
     * Returns all the messages exchanged by all the contacts in the supplied metaContact
     * on and after the given date
     *
     * @param metaContact MetaContact
     * @param startDate Date the start date of the conversations
     *
     * @return Collection of MessageReceivedEvents or MessageDeliveredEvents
     */
    public Collection<EventObject> findByStartDate(MetaContact metaContact, Date startDate) {
        HashSet<EventObject> result = new HashSet<>();
        String startTimeStamp = String.valueOf(startDate.getTime());

        Contact contact = metaContact.getDefaultContact();
        String sessionUuid = getSessionUuidByJid(contact);
        Cursor cursor;
        String[] args = {sessionUuid, startTimeStamp};
        cursor = mDB.query(ChatMessage.TABLE_NAME, null,
                ChatMessage.SESSION_UUID + "=? AND " + ChatMessage.TIME_STAMP + ">=?",
                args, null, null, ORDER_ASC);

        while (cursor.moveToNext()) {
            result.add(convertHistoryRecordToMessageEvent(cursor, contact));
        }
        return result;
    }

    /**
     * Returns all the messages exchanged by all the contacts in the supplied metaContact before
     * the given date
     *
     * @param metaContact MetaContact
     * @param endDate Date the end date of the conversations
     *
     * @return Collection of MessageReceivedEvents or MessageDeliveredEvents
     */
    public Collection<EventObject> findByEndDate(MetaContact metaContact, Date endDate) {
        HashSet<EventObject> result = new HashSet<>();
        String endTimeStamp = String.valueOf(endDate.getTime());

        Contact contact = metaContact.getDefaultContact();
        String sessionUuid = getSessionUuidByJid(contact);
        Cursor cursor;
        String[] args = {sessionUuid, endTimeStamp};
        cursor = mDB.query(ChatMessage.TABLE_NAME, null,
                ChatMessage.SESSION_UUID + "=? AND " + ChatMessage.TIME_STAMP + "<?",
                args, null, null, ORDER_ASC);

        while (cursor.moveToNext()) {
            result.add(convertHistoryRecordToMessageEvent(cursor, contact));
        }
        return result;
    }

    /**
     * Returns all the messages exchanged by all the contacts in the supplied metaContact between
     * the given dates inclusive of startDate
     *
     * @param metaContact MetaContact
     * @param startDate Date the start date of the conversations
     * @param endDate Date the end date of the conversations
     *
     * @return Collection of MessageReceivedEvents or MessageDeliveredEvents
     */
    public Collection<EventObject> findByPeriod(MetaContact metaContact, Date startDate, Date endDate) {
        HashSet<EventObject> result = new HashSet<>();
        String startTimeStamp = String.valueOf(startDate.getTime());
        String endTimeStamp = String.valueOf(endDate.getTime());

        Contact contact = metaContact.getDefaultContact();
        String sessionUuid = getSessionUuidByJid(contact);
        Cursor cursor;
        String[] args = {sessionUuid, startTimeStamp, endTimeStamp};
        cursor = mDB.query(ChatMessage.TABLE_NAME, null,
                ChatMessage.SESSION_UUID + "=? AND " + ChatMessage.TIME_STAMP + ">=? AND "
                        + ChatMessage.TIME_STAMP + "<?", args, null, null, ORDER_ASC);

        while (cursor.moveToNext()) {
            result.add(convertHistoryRecordToMessageEvent(cursor, contact));
        }
        return result;
    }

    /**
     * Returns all the messages exchanged by all the contacts in the supplied metaContact between
     * the given dates inclusive of startDate and having the given keywords
     *
     * @param metaContact MetaContact
     * @param startDate Date the start date of the conversations
     * @param endDate Date the end date of the conversations
     * @param keywords array of keywords
     *
     * @return Collection of MessageReceivedEvents or MessageDeliveredEvents
     */
    public Collection<EventObject> findByPeriod(MetaContact metaContact, Date startDate, Date endDate, String[] keywords) {
        return findByPeriod(metaContact, startDate, endDate, keywords, false);
    }

    /**
     * Returns all the messages exchanged by all the contacts in the supplied metaContact having
     * the given keyword
     *
     * @param metaContact MetaContact
     * @param keyword keyword
     *
     * @return Collection of MessageReceivedEvents or MessageDeliveredEvents
     */
    public Collection<EventObject> findByKeyword(MetaContact metaContact, String keyword) {
        return findByKeyword(metaContact, keyword, false);
    }

    /**
     * Returns all the messages exchanged by all the contacts in the supplied metaContact having
     * the given keywords
     *
     * @param metaContact MetaContact
     * @param keywords keyword
     *
     * @return Collection of MessageReceivedEvents or MessageDeliveredEvents
     */
    public Collection<EventObject> findByKeywords(MetaContact metaContact, String[] keywords) {
        return findByKeywords(metaContact, keywords, false);
    }

    /**
     * Returns the supplied number of recent messages exchanged by all the contacts in the
     * supplied metaContact
     *
     * @param metaContact MetaContact
     * @param count messages count
     *
     * @return Collection of MessageReceivedEvents or MessageDeliveredEvents
     */
    public Collection<EventObject> findLast(MetaContact metaContact, int count) {
        LinkedList<EventObject> result = new LinkedList<>();
        Iterator<Contact> contacts = metaContact.getContacts();
        while (contacts.hasNext()) {
            Contact contact = contacts.next();
            String sessionUuid = getSessionUuidByJid(contact);
            Cursor cursor;
            String[] args = {sessionUuid};
            cursor = mDB.query(ChatMessage.TABLE_NAME, null, ChatMessage.SESSION_UUID
                    + "=?", args, null, null, ORDER_DESC, String.valueOf(count));

            while (cursor.moveToNext()) {
                result.add(convertHistoryRecordToMessageEvent(cursor, contact));
            }
        }
        Collections.sort(result, new MessageEventComparator<>());
        return result;
    }

    /**
     * Returns the supplied number of recent messages on or after the given date exchanged by all
     * the contacts in the supplied metaContact
     *
     * @param metaContact MetaContact
     * @param startDate messages after date
     * @param count messages count
     *
     * @return Collection of MessageReceivedEvents or MessageDeliveredEvents
     */
    public Collection<EventObject> findFirstMessagesAfter(MetaContact metaContact, Date startDate, int count) {
        LinkedList<EventObject> result = new LinkedList<>();
        String startTimeStamp = String.valueOf(startDate.getTime());

        Iterator<Contact> contacts = metaContact.getContacts();
        while (contacts.hasNext()) {
            Contact contact = contacts.next();
            String sessionUuid = getSessionUuidByJid(contact);
            String[] args = {sessionUuid, startTimeStamp};
            Cursor cursor;
            cursor = mDB.query(ChatMessage.TABLE_NAME, null,
                    ChatMessage.SESSION_UUID + "=? AND " + ChatMessage.TIME_STAMP + ">=?",
                    args, null, null, ORDER_ASC, String.valueOf(count));

            while (cursor.moveToNext()) {
                result.add(convertHistoryRecordToMessageEvent(cursor, contact));
            }
        }
        Collections.sort(result, new MessageEventComparator<>());
        return result;
    }

    /**
     * Returns the supplied number of recent messages before the given date exchanged by all the
     * contacts in the supplied metaContact
     *
     * @param metaContact MetaContact
     * @param endDate messages before date
     * @param count messages count
     *
     * @return Collection of MessageReceivedEvents or MessageDeliveredEvents
     */
    public Collection<EventObject> findLastMessagesBefore(MetaContact metaContact, Date endDate, int count) {
        LinkedList<EventObject> result = new LinkedList<>();
        String endTimeStamp = String.valueOf(endDate.getTime());

        // cmeng - metaUid is also the sessionUid for metaChatSession
        // String sessionUuid = metaContact.getMetaUID();
        Iterator<Contact> contacts = metaContact.getContacts();
        while (contacts.hasNext()) {
            Contact contact = contacts.next();
            String sessionUuid = getSessionUuidByJid(contact);
            Cursor cursor;
            String[] args = {sessionUuid, endTimeStamp};
            cursor = mDB.query(ChatMessage.TABLE_NAME, null,
                    ChatMessage.SESSION_UUID + "=? AND " + ChatMessage.TIME_STAMP + "<?",
                    args, null, null, ORDER_DESC, String.valueOf(count));

            while (cursor.moveToNext()) {
                result.add(convertHistoryRecordToMessageEvent(cursor, contact));
            }
        }
        Collections.sort(result, new MessageEventComparator<>());
        return result;
    }

    // ============== ChatSessionFragment utilities ======================

    /**
     * Returns all the chat session record created by the supplied accountUid before the given date
     *
     * @param accountUid Account Uid
     * @param endDate end date for the session creation
     *
     * @return Collection of ChatSessionRecord
     */
    public Collection<ChatSessionRecord> findSessionByEndDate(String accountUid, Date endDate) {
        List<ChatSessionRecord> result = new ArrayList<>();
        String endTimeStamp = String.valueOf(endDate.getTime());

        String[] args = {accountUid, endTimeStamp};
        Cursor cursor = mDB.query(ChatSession.TABLE_NAME, null,
                ChatSession.ACCOUNT_UID + "=? AND " + ChatSession.CREATED + "<?",
                args, null, null, ChatSession.MODE + ", " + ChatSession.ENTITY_JID + " ASC");

        while (cursor.moveToNext()) {
            ChatSessionRecord csRecord = convertToSessionRecord(cursor);
            if (csRecord != null) {
                result.add(csRecord);
            }
        }
        return result;
    }

    /**
     * convert ChatSession Table rows to ChatSessionRecord for UI display
     * bit-7 of ChatSession.STATUS if set, then remove the record from UI; i.e. just return null
     *
     * @param cursor HistoryRecord in cursor
     *
     * @return Object ChatSessionRecord
     */
    private ChatSessionRecord convertToSessionRecord(Cursor cursor) {
        Map<String, String> mProperties = new Hashtable<>();
        for (int i = 0; i < cursor.getColumnCount(); i++) {
            String value = (cursor.getString(i) == null) ? "" : cursor.getString(i);
            mProperties.put(cursor.getColumnName(i), value);
        }

        try {
            EntityBareJid entityJBareid = JidCreate.entityBareFrom(mProperties.get(ChatSession.ENTITY_JID));
            int chatType = Integer.parseInt(Objects.requireNonNull(mProperties.get(ChatSession.STATUS)));
            if ((chatType & ChatSessionFragment.SESSION_HIDDEN) != 0)
                return null;

            int chatMode = Integer.parseInt(Objects.requireNonNull(mProperties.get(ChatSession.MODE)));
            Date date = new Date(Long.parseLong(Objects.requireNonNull(mProperties.get(ChatSession.CREATED))));
            Date mamDate = new Date(Long.parseLong(Objects.requireNonNull(mProperties.get(ChatSession.MAM_DATE))));
            return new ChatSessionRecord(
                    mProperties.get(ChatSession.SESSION_UUID),
                    mProperties.get(ChatSession.ACCOUNT_UID),
                    entityJBareid, chatMode, chatType, date, mamDate);
        } catch (XmppStringprepException e) {
            return null;
        }
    }

    /**
     * Get and return the last message for the specified sessionUuid
     *
     * @param sessionUuid the chatSessionUuid in ChatMessage.TABLE_NAME
     *
     * @return last message for the specified sessionUuid
     */
    public String getLastMessageForSessionUuid(String sessionUuid) {
        String msgBody = "";
        String endTimeStamp = String.valueOf(new Date().getTime());

        if (!TextUtils.isEmpty(sessionUuid)) {
            String[] columns = {ChatMessage.MSG_BODY};
            String[] args = {sessionUuid, endTimeStamp};
            Cursor cursor = mDB.query(ChatMessage.TABLE_NAME, columns, ChatMessage.SESSION_UUID + "=? AND "
                    + ChatMessage.TIME_STAMP + "<?", args, null, null, ORDER_DESC, "1");

            while (cursor.moveToNext()) {
                msgBody = cursor.getString(0);
            }
            cursor.close();
        }
        return msgBody;
    }

    /**
     * Get and return the last message Timestamp for the specified sessionUuid
     *
     * @param sessionUuid the chatSessionUuid in ChatMessage.TABLE_NAME
     *
     * @return last message Date (TimeStamp) for the specified sessionUuid
     */
    public Date getLastMessageDateForSessionUuid(String sessionUuid) {
        String endTimeStamp = String.valueOf(new Date().getTime());

        String[] columns = {ChatMessage.TIME_STAMP};
        String[] args = {sessionUuid, endTimeStamp};
        Cursor cursor = mDB.query(ChatMessage.TABLE_NAME, columns, ChatMessage.SESSION_UUID + "=? AND "
                + ChatMessage.TIME_STAMP + "<?", args, null, null, ORDER_DESC, "1");

        String mamDate = "-1";
        while (cursor.moveToNext()) {
            mamDate = cursor.getString(0);
        }
        cursor.close();
        return mamDate.equals("-1") ? null : new Date(Long.parseLong(mamDate));
    }

    /**
     * Set the chatSession chatType mainly used to set session hidden bit
     *
     * @param sessionUuid the chatSession Uuid
     *
     * @return the chatSession chatType
     */
    public int setSessionChatType(String sessionUuid, int chatType) {
        if (StringUtils.isEmpty(sessionUuid))
            return 0;

        String[] args = {sessionUuid};
        contentValues.clear();
        contentValues.put(ChatSession.STATUS, chatType);

        // From field crash on java.lang.IllegalArgumentException? HWKSA-M, Android 9
        try {
            return mDB.update(ChatSession.TABLE_NAME, contentValues, ChatSession.SESSION_UUID + "=?", args);
        } catch (IllegalArgumentException e) {
            Timber.w("Exception in setting Session ChatType for: %s; %s", sessionUuid, e.getMessage());
            return -1;
        }
    }
    // ============== End ChatSessionFragment utilities ======================

    // ============== Start mam Message utilities ======================

    /**
     * Get the last server mam record access date
     *
     * @param sessionUuid Chat session Uuid
     *
     * @return the last mamDate updated
     */
    public Date getMamDate(String sessionUuid) {
        String[] columns = {ChatSession.MAM_DATE};
        String[] args = {sessionUuid};
        Cursor cursor = mDB.query(ChatSession.TABLE_NAME, columns,
                ChatSession.SESSION_UUID + "=?", args, null, null, null, null);

        Date mamDate = null;
        while (cursor.moveToNext()) {
            mamDate = new Date(Long.parseLong(cursor.getString(0)));
        }
        cursor.close();
        return mamDate;
    }

    /**
     * Update the last mam retrieval message timeStamp for the specified sessionUuid.
     * Always advance timeStamp by 10ms to avoid last message being included in future mam fetching.
     *
     * @param sessionUuid the chat sessions record id to which to save the timestamp
     * @param date last mam message timestamp
     */
    public void setMamDate(String sessionUuid, Date date) {
        contentValues.clear();
        contentValues.put(ChatSession.MAM_DATE, Long.toString(date.getTime() + 10));
        String[] args = {sessionUuid};

        mDB.update(ChatSession.TABLE_NAME, contentValues, ChatSession.SESSION_UUID + "=?", args);
    }

    public void saveMamIfNotExit(OmemoManager omemoManager, ChatPanel chatPanel,
            List<Forwarded<Message>> forwardedList) {

        EntityFullJid userJid = chatPanel.getProtocolProvider().getConnection().getUser();
        String chatId = chatPanel.getChatSession().getChatId();

        Date timeStamp = new Date();
        for (Forwarded<Message> forwarded : forwardedList) {
            Message msg = forwarded.getForwardedStanza();

            // Skip all messages that are being sent by own self
            Jid sender = msg.getFrom();
            // Timber.d("userJid = %s; sender = %s", userJid, sender);
            if (userJid.equals(sender)) {
                continue;
            }

            // Some received/DomainBareJid message does not have msgId. So use stanzaId from StanzaIdElement if found.
            String msgId = msg.getStanzaId();
            if (TextUtils.isEmpty(msgId)) {
                ExtensionElement stanzaIdElement = msg.getExtension(StanzaIdElement.QNAME);
                if (stanzaIdElement instanceof StanzaIdElement) {
                    msgId = ((StanzaIdElement) stanzaIdElement).getId();
                }
            }
            if (TextUtils.isEmpty(msgId)) {
                continue;
            }
            timeStamp = forwarded.getDelayInformation().getStamp();

            String[] args = {msgId, chatId};
            Cursor cursor = mDB.query(ChatMessage.TABLE_NAME, null, ChatMessage.UUID
                    + "=? AND " + ChatMessage.SESSION_UUID + "=?", args, null, null, null);
            int msgCount = cursor.getCount();
            cursor.close();

            if (msgCount == 0) {
                IMessage iMessage = null;

                if (msg.hasExtension(OmemoElement.NAME_ENCRYPTED, OmemoConstants.OMEMO_NAMESPACE_V_AXOLOTL)) {
                    OmemoElement omemoElement =
                            (OmemoElement) msg.getExtensionElement(OmemoElement.NAME_ENCRYPTED, OmemoConstants.OMEMO_NAMESPACE_V_AXOLOTL);
                    try {
                        OmemoMessage.Received oReceive = omemoManager.decrypt(sender.asBareJid(), omemoElement);
                        iMessage = new MessageJabberImpl(oReceive.getBody(), iMessage.ENCRYPTION_OMEMO, null, msgId);
                    } catch (SmackException.NotLoggedInException | CorruptedOmemoKeyException | NoRawSessionException
                            | CryptoFailedException | IOException e) {
                        Timber.e("Omemo decrypt message (%s): %s", msgId, e.getMessage());
                    }
                }
                else {
                    iMessage = new MessageJabberImpl(msg.getBody(), iMessage.ENCRYPTION_NONE, null, msgId);
                }

                if (iMessage != null) {
                    String direction = userJid.asBareJid().isParentOf(sender) ? ChatMessage.DIR_OUT : ChatMessage.DIR_IN;
                    int msgType = (Message.Type.groupchat == msg.getType()) ? ChatMessage.MESSAGE_ACTION : ChatMessage.MESSAGE_IN;
                    if (isHistoryLoggingEnabled()) {
                        writeMessage(chatId, direction, sender, iMessage, timeStamp, msgType);
                    }
                    else {
                        String fromJid = sender.toString();
                        chatPanel.cacheNextMsg(new ChatMessageImpl(fromJid, fromJid, timeStamp,
                                msgType, iMessage, null, direction));
                    }
                    // Timber.d("Message body# %s: (%s) %s => %s", sender, msgId, timeStamp, iMessage.getContent());
                }
            }
        }
        // Save the last mam retrieval timeStamp
        setMamDate(chatId, timeStamp);
    }

    // ============== End mam Message utilities ======================

    /**
     * Return the messages for the recently contacted <code>count</code> contacts.
     *
     * @param count contacts count
     * @param providerToFilter can be filtered by provider e.g. Jabber:abc123@atalk.org,
     * or <code>null</code> to search for all providers
     * @param contactToFilter can be filtered by contact e.g. xyx123@atalk.org, or <code>null</code>
     * to search for all contacts
     *
     * @return Collection of MessageReceivedEvents or MessageDeliveredEvents
     */
    public Collection<EventObject> findRecentMessagesPerContact(int count, String providerToFilter,
            String contactToFilter, boolean isSMSEnabled) {
        String sessionUuid;
        String accountUuid;
        String entityJid;
        String whereCondition = "";
        String[] args = {};
        Cursor cursorMsg;
        Object descriptor;
        LinkedList<EventObject> result = new LinkedList<>();

        // Timber.i("Find recent message for: " + providerToFilter + " -> " + contactToFilter);
        List<String> argList = new ArrayList<>();
        if (StringUtils.isNotEmpty(providerToFilter)) {
            whereCondition = ChatSession.ACCOUNT_UID + "=?";
            argList.add(providerToFilter);
        }
        if (StringUtils.isNotEmpty(contactToFilter)) {
            if (StringUtils.isNotEmpty(whereCondition))
                whereCondition += " AND ";
            whereCondition += ChatSession.ENTITY_JID + "=?";
            argList.add(contactToFilter);
        }
        if (argList.size() > 0)
            args = argList.toArray(new String[0]);

        Cursor cursor = mDB.query(ChatSession.TABLE_NAME, null, whereCondition, args, null,
                null, null);

        // Iterate over all the found sessionsUuid for the given accountUuid
        while (cursor.moveToNext()) {
            if (result.size() >= count)
                break;

            sessionUuid = cursor.getString(cursor.getColumnIndexOrThrow(ChatSession.SESSION_UUID));
            // skip for null sessionUuid i.e. message from non-persistent contact e.g server announcement.
            if (StringUtils.isEmpty(sessionUuid))
                continue;

            accountUuid = cursor.getString(cursor.getColumnIndexOrThrow(ChatSession.ACCOUNT_UUID));
            entityJid = cursor.getString(cursor.getColumnIndexOrThrow(ChatSession.ENTITY_JID));

            // find contact or chatRoom for given contactJid; skip if not found contacts,
            // disabled accounts and hidden one
            descriptor = getContactOrRoomByID(accountUuid, entityJid, isSMSEnabled);
            if (descriptor == null)
                continue;

            whereCondition = ChatMessage.SESSION_UUID + "=?";
            argList.clear();
            argList.add(sessionUuid);
            if (isSMSEnabled) {
                whereCondition += " AND (" + ChatMessage.MSG_TYPE + "=? OR " + ChatMessage.MSG_TYPE + "=?)";
                argList.add(String.valueOf(ChatMessage.MESSAGE_SMS_IN));
                argList.add(String.valueOf(ChatMessage.MESSAGE_SMS_OUT));
            }
            args = argList.toArray(new String[0]);

            cursorMsg = mDB.query(ChatMessage.TABLE_NAME, null, whereCondition, args,
                    null, null, ORDER_DESC, String.valueOf(count));


            while (cursorMsg.moveToNext()) {
                if (descriptor instanceof Contact) {
                    EventObject o = convertHistoryRecordToMessageEvent(cursorMsg, (Contact) descriptor);
                    result.add(o);
                }
                if (descriptor instanceof ChatRoom) {
                    EventObject o = convertHistoryRecordToMessageEvent(cursorMsg, (ChatRoom) descriptor);
                    result.add(o);
                }
            }
            cursorMsg.close();
        }

        cursor.close();
        Collections.sort(result, new MessageEventComparator<>());
        return result;
    }

    /**
     * Get and return the count of messages for the specified accountUuid
     *
     * @param editedAccUID the current edited account
     *
     * @return count of messages for the specified accountUuid
     */
    public int getMessageCountForAccountUuid(String editedAccUID) {
        int msgCount = 0;
        String sessionUuid;
        List<String> sessionUuids = new ArrayList<>();
        String[] columns = {ChatSession.SESSION_UUID};
        String[] args = {editedAccUID};

        Cursor cursor = mDB.query(ChatSession.TABLE_NAME, columns, ChatSession.ACCOUNT_UID + "=?", args,
                null, null, null);
        while (cursor.moveToNext()) {
            sessionUuid = cursor.getString(0);
            if (!TextUtils.isEmpty(sessionUuid))
                sessionUuids.add(sessionUuid);
        }
        cursor.close();

        if (!sessionUuids.isEmpty()) {
            for (String sessionId : sessionUuids) {
                msgCount += getMessageCountForSessionUuid(sessionId);
            }
        }
        return msgCount;
    }

    /**
     * Get and return the count of messages for the specified sessionUuid
     *
     * @param sessionUuid the chatSessionUuid in ChatMessage.TABLE_NAME
     *
     * @return count of messages for the specified sessionUuid
     */
    public int getMessageCountForSessionUuid(String sessionUuid) {
        int msgCount = 0;
        if (!TextUtils.isEmpty(sessionUuid)) {
            String[] args = {sessionUuid};
            Cursor cursor = mDB.query(ChatMessage.TABLE_NAME, null, ChatMessage.SESSION_UUID + "=?", args,
                    null, null, null);
            msgCount = cursor.getCount();
            cursor.close();
        }
        return msgCount;
    }

    /**
     * Find the Contact or ChatRoom corresponding to this contactId. First Checks the account
     * and then searches for the contact or chatRoom. Will skip hidden and disabled accounts.
     *
     * @param accountUuid the account Uuid.
     * @param contactId the entityBareJid for Contact or ChatRoom in String.
     * @param isSMSEnabled get contact from SmsMessage if true
     *
     * @return Contact or ChatRoom object.
     */
    private Object getContactOrRoomByID(String accountUuid, String contactId, boolean isSMSEnabled) {
        // skip for system virtual server e.g. atalk.org without "@"
        if (StringUtils.isEmpty(contactId) || contactId.indexOf("@") <= 0)
            return null;

        AccountID accountID = null;
        for (AccountID acc : AccountUtils.getStoredAccounts()) {
            if (!acc.isHidden() && acc.isEnabled()
                    && accountUuid.equals(acc.getAccountUuid())) {
                accountID = acc;
                break;
            }
        }
        if (accountID == null)
            return null;

        /*
         * Check to ensure account is online (required to fetch room) before proceed. This is to take care in case
         * user has exited while muc chatRoom messages fetching is still on going
         */
        ProtocolProviderService pps = accountID.getProtocolProvider();
        if (pps == null || !pps.isRegistered())
            return null;

        OperationSetPersistentPresence opSetPresence = pps.getOperationSet(OperationSetPersistentPresence.class);

        if (opSetPresence == null)
            return null;

        Contact contact = opSetPresence.findContactByID(contactId);
        if (contact != null)
            return contact;

        if (isSMSEnabled) {
            // we will check only for sms contacts
            OperationSetSmsMessaging opSetSMS = pps.getOperationSet(OperationSetSmsMessaging.class);
            return (opSetSMS == null) ? null : opSetSMS.getContact(contactId);
        }

        OperationSetMultiUserChat opSetMuc = pps.getOperationSet(OperationSetMultiUserChat.class);
        if (opSetMuc == null)
            return null;

        try {
            // will remove the server part - cmeng: not required in new implementation
            // id = id.substring(0, id.lastIndexOf('@'));
            return opSetMuc.findRoom(contactId);
        } catch (Exception e) {
            Timber.e(e, "Cannot find room for: %s", contactId);
            return null;
        }
    }

    /**
     * Returns the sessionUuid by specified Contact
     * Non-Persistent Entity will use ChatSession.Mode_MULTI to generate sessionUuid
     *
     * @param contact The chat Contact
     *
     * @return sessionUuid - created if not exist
     */
    public String getSessionUuidByJid(Contact contact) {
        AccountID accountID = contact.getProtocolProvider().getAccountID();
        String entityJid = contact.getAddress();

        if (contact.isPersistent())
            return getSessionUuid(accountID, entityJid, ChatSession.MODE_SINGLE);
        else
            return getSessionUuid(accountID, entityJid, ChatSession.MODE_NPE);
    }

    /**
     * Returns the sessionUuid by specified ChatRoom
     *
     * @param room The chatRoom
     *
     * @return sessionUuid - created if not exist
     */
    public String getSessionUuidByJid(ChatRoom room) {
        AccountID accountID = room.getParentProvider().getAccountID();
        String entityJid = room.getName();

        return getSessionUuid(accountID, entityJid, ChatSession.MODE_MULTI);
    }

    /**
     * Returns the sessionUuid by specified AccountID and chatRoomID
     *
     * @param accountID The AccountID
     * @param entityJid The chatRoomID
     *
     * @return sessionUuid - created if not exist
     */
    public String getSessionUuidByJid(AccountID accountID, String entityJid) {
        return getSessionUuid(accountID, entityJid, ChatSession.MODE_MULTI);
    }

    /**
     * Returns the sessionUuid by the given AdHocChatRoom
     *
     * @param room The adHocChatRoom
     *
     * @return sessionUuid - created if not exist
     */
    private String getSessionUuidByJid(AdHocChatRoom room) {
        AccountID accountID = room.getParentProvider().getAccountID();
        String entityJid = room.getName();

        return getSessionUuid(accountID, entityJid, ChatSession.MODE_MULTI);
    }

    /**
     * Get sessionUuid for the unique pair (accountUuid + nick) OR generate new if none found
     *
     * @param accountID AccountID
     * @param entityJid Contact or ChatRoom
     * @param mode indicate if it is ChatSession.MODE_SINGLE or ChatSession.MODE_MUC, dictate the method
     * use to generate new sessionUid
     *
     * @return sessionUuid - created if not exist
     */
    private String getSessionUuid(AccountID accountID, String entityJid, int mode) {
        String accountUuid = accountID.getAccountUuid();
        String accountUid = accountID.getAccountUniqueID();
        String[] columns = {ChatSession.SESSION_UUID};
        String[] args = {accountUuid, entityJid};

        Cursor cursor = mDB.query(ChatSession.TABLE_NAME, columns, ChatSession.ACCOUNT_UUID
                + "=? AND " + ChatSession.ENTITY_JID + "=?", args, null, null, null);

        String sessionUuid = null;
        while (cursor.moveToNext()) {
            sessionUuid = cursor.getString(0);
        }
        cursor.close();
        if (StringUtils.isNotEmpty(sessionUuid))
            return sessionUuid;

        // Create new chatSession entry if one does not exist
        String timeStamp = String.valueOf(System.currentTimeMillis());
        // Use metaContactUid if it is a metaContact chatSession
        if (mode == ChatSession.MODE_SINGLE) {
            columns = new String[]{MetaContactGroup.MC_UID};
            cursor = mDB.query(MetaContactGroup.TBL_CHILD_CONTACTS, columns,
                    MetaContactGroup.ACCOUNT_UUID + "=? AND " + MetaContactGroup.CONTACT_JID + "=?",
                    args, null, null, null);
            while (cursor.moveToNext()) {
                sessionUuid = cursor.getString(0);
            }
            cursor.close();
        }

        // generate new sessionUuid for non-persistent contact or ChatSession.MODE_MULTI
        if (StringUtils.isEmpty(sessionUuid)) {
            sessionUuid = timeStamp + Math.abs(entityJid.hashCode());
        }

        contentValues.clear();
        contentValues.put(ChatSession.SESSION_UUID, sessionUuid);
        contentValues.put(ChatSession.ACCOUNT_UUID, accountUuid);
        contentValues.put(ChatSession.ACCOUNT_UID, accountUid);
        contentValues.put(ChatSession.ENTITY_JID, entityJid);
        contentValues.put(ChatSession.CREATED, timeStamp);
        contentValues.put(ChatSession.STATUS, ChatFragment.MSGTYPE_OMEMO);
        contentValues.put(ChatSession.MODE, mode);

        mDB.insert(ChatSession.TABLE_NAME, null, contentValues);
        return sessionUuid;
    }

    /**
     * Get the chatSession chatType
     *
     * @param chatSession the chatSession for single or multi-chat
     *
     * @return the chatSession chatType
     */
    public int getSessionChatType(ChatSession chatSession) {
        int chatType = ChatFragment.MSGTYPE_OMEMO;
        String entityJid = chatSession.getChatEntity();
        AccountID accountUid = chatSession.getCurrentChatTransport().getProtocolProvider().getAccountID();

        if (StringUtils.isEmpty(entityJid) || (accountUid == null))
            return chatType;

        String accountUuid = accountUid.getAccountUuid();
        String[] columns = {ChatSession.STATUS};
        String[] args = {accountUuid, entityJid};

        Cursor cursor = mDB.query(ChatSession.TABLE_NAME, columns, ChatSession.ACCOUNT_UUID
                + "=? AND " + ChatSession.ENTITY_JID + "=?", args, null, null, null);

        while (cursor.moveToNext()) {
            chatType = cursor.getInt(0);
        }
        cursor.close();

        // clear the session record hidden bit if set; to make visible to ChatSessionFragment UI
        if ((chatType & ChatFragment.MSGTYPE_MASK) != 0) {
            chatType &= ChatFragment.MSGTYPE_MASK;
            setSessionChatType(chatSession, chatType);
        }

        if (ChatFragment.MSGTYPE_UNKNOWN == chatType)
            chatType = ChatFragment.MSGTYPE_OMEMO;
        return chatType;
    }

    /**
     * Set the chatSession chatType
     *
     * @param chatSession the chatSession for single or multi-chat
     *
     * @return the chatSession chatType
     */
    public int setSessionChatType(ChatSession chatSession, int chatType) {
        String entityJid = chatSession.getChatEntity();
        AccountID accountUid = chatSession.getCurrentChatTransport().getProtocolProvider().getAccountID();

        if (StringUtils.isEmpty(entityJid) || entityJid.equals(aTalkApp.getResString(R.string.service_gui_UNKNOWN))
                || (accountUid == null))
            return 0;

        String accountUuid = accountUid.getAccountUuid();
        String[] args = {accountUuid, entityJid};

        contentValues.clear();
        contentValues.put(ChatSession.STATUS, chatType);

        // From field crash on java.lang.IllegalArgumentException? HWKSA-M, Android 9
        try {
            return mDB.update(ChatSession.TABLE_NAME, contentValues, ChatSession.ACCOUNT_UUID
                    + "=? AND " + ChatSession.ENTITY_JID + "=?", args);
        } catch (IllegalArgumentException e) {
            Timber.w("Exception setSessionChatType for EntityJid: %s and AccountUid: %s; %s",
                    entityJid, accountUid, e.getMessage());
            return -1;
        }
    }

    /**
     * Use to convert HistoryRecord to MessageDeliveredEvent or MessageReceivedEvent or
     * FileRecord which are returned in cursor by the finder Methods
     *
     * @param cursor HistoryRecord in cursor
     * @param contact always the metaContact.getDefaultContact().
     *
     * @return Object
     */
    private EventObject convertHistoryRecordToMessageEvent(Cursor cursor, Contact contact) {
        Map<String, String> mProperties = new Hashtable<>();

        for (int i = 0; i < cursor.getColumnCount(); i++) {
            String value = (cursor.getString(i) == null) ? "" : cursor.getString(i);
            mProperties.put(cursor.getColumnName(i), value);
        }

        // Return FileRecord if it is of file transfer message type, but excluding MESSAGE_HTTP_FILE_LINK
        int msgType = Integer.parseInt(Objects.requireNonNull(mProperties.get(ChatMessage.MSG_TYPE)));
        if ((msgType == ChatMessage.MESSAGE_FILE_TRANSFER_HISTORY)
                || (msgType == ChatMessage.MESSAGE_FILE_TRANSFER_RECEIVE)
                || (msgType == ChatMessage.MESSAGE_FILE_TRANSFER_SEND)
                || (msgType == ChatMessage.MESSAGE_STICKER_SEND)) {
            return createFileRecordFromProperties(mProperties, contact);
        }

        // else proceed to process normal chat message
        MessageImpl msg = createMessageFromProperties(mProperties);
        Date timestamp = new Date(Long.parseLong(Objects.requireNonNull(mProperties.get(ChatMessage.TIME_STAMP))));
        String sender = mProperties.get(ChatMessage.JID);

        if (msg.isOutgoing) {
            MessageDeliveredEvent evt = new MessageDeliveredEvent(msg, contact, null, sender, timestamp);
            if (ChatMessage.MESSAGE_SMS_OUT == msg.getMsgSubType()) {
                evt.setSmsMessage(true);
            }
            return evt;
        }
        else {
            // ContactResource has no meaning for the given contact, so set it to null
            return new MessageReceivedEvent(msg, contact, null, sender, timestamp, null);
        }
    }

    /**
     * Use to convert HistoryRecord in ChatRoomMessageDeliveredEvent or
     * ChatRoomMessageReceivedEvent which are returned in cursor by the finder methods
     *
     * @param cursor HistoryRecord in cursor
     * @param chatRoom the chat room
     *
     * @return EventObject
     */
    private EventObject convertHistoryRecordToMessageEvent(Cursor cursor, ChatRoom chatRoom) {
        Map<String, String> mProperties = new Hashtable<>();
        for (int i = 0; i < cursor.getColumnCount(); i++) {
            String value = (cursor.getString(i) == null) ? "" : cursor.getString(i);
            mProperties.put(cursor.getColumnName(i), value);
        }

        // jabberID should contain user bareJid if muc msg in; else contact fullJid if muc msg out
        // EntityBareJid if from chatRoom itself (should not have stored in DB)
        String jabberID = XmppStringUtils.parseBareJid(Objects.requireNonNull(mProperties.get(ChatMessage.JID)));

        ProtocolProviderService pps = chatRoom.getParentProvider();
        OperationSetPersistentPresenceJabberImpl presenceOpSet = (OperationSetPersistentPresenceJabberImpl)
                pps.getOperationSet(OperationSetPersistentPresence.class);
        Contact contact = presenceOpSet.findContactByID(jabberID);

        // Do not include ChatMessage.MESSAGE_HTTP_FILE_LINK
        int msgType = Integer.parseInt(Objects.requireNonNull(mProperties.get(ChatMessage.MSG_TYPE)));
        if ((msgType == ChatMessage.MESSAGE_FILE_TRANSFER_HISTORY)
                || (msgType == ChatMessage.MESSAGE_FILE_TRANSFER_SEND)
                || (msgType == ChatMessage.MESSAGE_STICKER_SEND)) {

            if (contact != null) {
                return createFileRecordFromProperties(mProperties, contact);
            }
            else // send from me
                return createFileRecordFromProperties(mProperties, chatRoom);
        }

        MessageImpl msg = createMessageFromProperties(mProperties);
        Date timestamp = new Date(Long.parseLong(Objects.requireNonNull(mProperties.get(ChatMessage.TIME_STAMP))));

        if (msg.isOutgoing) {
            return new ChatRoomMessageDeliveredEvent(chatRoom, timestamp, msg, ChatMessage.MESSAGE_MUC_OUT);
        }
        else {
            // muc incoming message can be MESSAGE_HTTP_FILE_LINK
            // Incoming muc message contact should not be null unless the sender is not one of user's contacts
            Jid userJid = (contact == null) ? null : contact.getJid();

            // Incoming muc message Entity_Jid is the nick name of the sender; null if from chatRoom
            String nickName = mProperties.get(ChatMessage.ENTITY_JID);
            Resourcepart nick = null;
            try {
                nick = Resourcepart.from(Objects.requireNonNull(nickName));
            } catch (XmppStringprepException e) {
                Timber.w("History record to message conversion with null nick");
            }

            ChatRoomMember from = new ChatRoomMemberJabberImpl((ChatRoomJabberImpl) chatRoom, nick, userJid);
            return new ChatRoomMessageReceivedEvent(chatRoom, from, timestamp, msg, msgType);
        }
    }

    /**
     * Create from the retrieved database mProperties to chatMessages
     *
     * @param mProperties message properties converted from cursor
     *
     * @return MessageImpl
     */
    private MessageImpl createMessageFromProperties(Map<String, String> mProperties) {
        String messageUID = mProperties.get(ChatMessage.UUID);
        Date messageReceivedDate = new Date(Long.parseLong(Objects.requireNonNull(mProperties.get(ChatMessage.TIME_STAMP))));

        String msgBody = mProperties.get(ChatMessage.MSG_BODY);
        int encType = Integer.parseInt(Objects.requireNonNull(mProperties.get(ChatMessage.ENC_TYPE)));
        int xferStatus = Integer.parseInt(Objects.requireNonNull(mProperties.get(ChatMessage.STATUS)));
        int receiptStatus = Integer.parseInt(Objects.requireNonNull(mProperties.get(ChatMessage.READ)));
        String serverMsgId = mProperties.get(ChatMessage.SERVER_MSG_ID);
        String remoteMsgId = mProperties.get(ChatMessage.REMOTE_MSG_ID);
        boolean isOutgoing = ChatMessage.DIR_OUT.equals(mProperties.get(ChatMessage.DIRECTION));

        int msgSubType = -1;
        int msgType = Integer.parseInt(Objects.requireNonNull(mProperties.get(ChatMessage.MSG_TYPE)));
        if ((msgType == ChatMessage.MESSAGE_SMS_OUT) || (msgType == ChatMessage.MESSAGE_SMS_IN))
            msgSubType = msgType;

        return new MessageImpl(msgBody, encType, "", messageUID, xferStatus, receiptStatus,
                serverMsgId, remoteMsgId, isOutgoing, messageReceivedDate, msgSubType);
    }

    /**
     * Create from the retrieved database mProperties to FileRecord
     *
     * @param mProperties message properties converted from cursor
     * @param entityJid an instance of Contact or ChatRoom of the history message
     *
     * @return FileRecord
     */
    private FileRecord createFileRecordFromProperties(Map<String, String> mProperties, Object entityJid) {
        String uuid = mProperties.get(ChatMessage.UUID);
        String dir = mProperties.get(ChatMessage.DIRECTION);
        Date date = new Date(Long.parseLong(Objects.requireNonNull(mProperties.get(ChatMessage.TIME_STAMP))));
        String file = Objects.requireNonNull(mProperties.get(ChatMessage.FILE_PATH));
        int encType = Integer.parseInt(Objects.requireNonNull(mProperties.get(ChatMessage.ENC_TYPE)));
        int status = Integer.parseInt(Objects.requireNonNull(mProperties.get(ChatMessage.STATUS)));
        return new FileRecord(uuid, entityJid, dir, date, new File(file), encType, status);
    }

    /**
     * Loads and registers the contact source service.
     */
    private void loadRecentMessages() {
        this.messageSourceService = new MessageSourceService(this);
        messageSourceServiceReg = bundleContext.registerService(
                ContactSourceService.class.getName(), messageSourceService, null);
        MessageHistoryActivator.getContactListService().addMetaContactListListener(this.messageSourceService);
    }

    /**
     * Unloads the contact source service.
     */
    private void stopRecentMessages() {
        if (messageSourceServiceReg != null) {
            MessageHistoryActivator.getContactListService().removeMetaContactListListener(this.messageSourceService);

            messageSourceServiceReg.unregister();
            messageSourceServiceReg = null;

            this.messageSourceService = null;
        }
    }

    // //////////////////////////////////////////////////////////////////////////
    // ChatMessageListener implementation methods for chatMessage
    // cmeng (20210816): Actually when historyLog is disabled, MessageListener is not registered with
    // OperationSetBasicInstantMessaging; hence HttpFileDownload messages are not saved in DB.
    // @see start#isMessageHistoryEnabled

    public void messageReceived(MessageReceivedEvent evt) {
        Contact contact = evt.getSourceContact();
        MetaContact metaContact = MessageHistoryActivator.getContactListService().findMetaContactByContact(contact);

        // return if logging is switched off for this particular contact OR not a Http File Transfer message
        int msgType = evt.getEventType();
        if ((metaContact != null) && !isHistoryLoggingEnabled(metaContact.getMetaUID())
                && (ChatMessage.MESSAGE_HTTP_FILE_DOWNLOAD != msgType)) {
            return;
        }

        // Replace last message in DB with the new received correction message content only - no new message record
        IMessage message = evt.getSourceMessage();
        String msgCorrectionId = evt.getCorrectedMessageUID();
        if (!TextUtils.isEmpty(msgCorrectionId))
            message.setMessageUID(msgCorrectionId);

        String sessionUuid = getSessionUuidByJid(contact);
        writeMessage(sessionUuid, ChatMessage.DIR_IN, contact, evt.getSender(), message, evt.getTimestamp(), msgType);
    }

    public void messageDelivered(MessageDeliveredEvent evt) {
        IMessage message = evt.getSourceMessage();
        Contact contact = evt.getContact();
        MetaContact metaContact = MessageHistoryActivator.getContactListService().findMetaContactByContact(contact);

        // return if logging is switched off for this particular contact
        // and do store if message is for remote only e.g HTTP file upload message
        if ((metaContact != null) && !isHistoryLoggingEnabled(metaContact.getMetaUID())
                || message.isRemoteOnly()) {
            return;
        }

        // Replace last message in DB with the new delivered correction message content only - no new message record
        String msgCorrectionId = evt.getCorrectedMessageUID();
        if (!TextUtils.isEmpty(msgCorrectionId))
            message.setMessageUID(msgCorrectionId);

        String sessionUuid = getSessionUuidByJid(contact);
        writeMessage(sessionUuid, ChatMessage.DIR_OUT, contact, evt.getSender(), message, evt.getTimestamp(), evt.getEventType());
    }

    public void messageDeliveryFailed(MessageDeliveryFailedEvent evt) {
        // nothing to do for the history service when delivery failed
    }

    @Override
    public void onReceiptReceived(Jid fromJid, Jid toJid, String receiptId, Stanza receipt) {
        String[] args = {receiptId};
        contentValues.clear();
        contentValues.put(ChatMessage.READ, ChatMessage.MESSAGE_DELIVERY_RECEIPT);
        mDB.update(ChatMessage.TABLE_NAME, contentValues, ChatMessage.SERVER_MSG_ID + "=?", args);
    }

    // //////////////////////////////////////////////////////////////////////////
    // ChatRoomMessageListener implementation methods for chatRoom

    public void messageReceived(ChatRoomMessageReceivedEvent evt) {
        int msgType = evt.getEventType();

        // return if logging is switched off for this particular chatRoom or not a Http File Transfer message
        if (!isHistoryLoggingEnabled(evt.getSourceChatRoom().getName())
                && (ChatMessage.MESSAGE_HTTP_FILE_DOWNLOAD != msgType)) {
            return;
        }

        // if this is chat room message history on every room enter, we can receive the same
        // latest history messages and this will just fill the history on every join
        if (evt.isHistoryMessage()) {
            Collection<EventObject> c = findFirstMessagesAfter(evt.getSourceChatRoom(),
                    new Date(evt.getTimestamp().getTime() - 10000), 20);

            boolean hasMatch = false;
            for (EventObject e : c) {
                if (e instanceof ChatRoomMessageReceivedEvent) {
                    ChatRoomMessageReceivedEvent cev = (ChatRoomMessageReceivedEvent) e;
                    String entityJid = evt.getSourceChatRoomMember().getContactAddress();

                    if ((entityJid != null)
                            && entityJid.equals(cev.getSourceChatRoomMember().getContactAddress())
                            && (evt.getTimestamp() != null)
                            && evt.getTimestamp().equals(cev.getTimestamp())) {
                        hasMatch = true;
                        break;
                    }
                    // also check and message content
                    IMessage m1 = cev.getMessage();
                    IMessage m2 = evt.getMessage();

                    if ((m1 != null) && (m2 != null)
                            && m1.getContent().equals(m2.getContent())) {
                        hasMatch = true;
                        break;
                    }
                }
            }
            // ignore if message is already saved
            if (hasMatch)
                return;
        }

        String sessionUuid = getSessionUuidByJid(evt.getSourceChatRoom());
        writeMessage(sessionUuid, ChatMessage.DIR_IN, evt.getSourceChatRoomMember(), evt.getMessage(),
                evt.getTimestamp(), msgType);
    }

    public void messageDelivered(ChatRoomMessageDeliveredEvent evt) {
        // return if logging is switched off for this particular chat room
        ChatRoom room = evt.getSourceChatRoom();
        IMessage message = evt.getMessage();

        if (!isHistoryLoggingEnabled(room.getName()) || message.isRemoteOnly()) {
            return;
        }

        // if this is chat room message history on every room enter, we can receive the same
        // latest history messages and this will just fill the history on every join
        if (evt.isHistoryMessage()) {
            Collection<EventObject> c = findFirstMessagesAfter(room,
                    new Date(evt.getTimestamp().getTime() - 10000), 20);

            boolean hasMatch = false;
            for (EventObject e : c)
                if (e instanceof ChatRoomMessageDeliveredEvent) {
                    ChatRoomMessageDeliveredEvent cev = (ChatRoomMessageDeliveredEvent) e;

                    if ((evt.getTimestamp() != null)
                            && evt.getTimestamp().equals(cev.getTimestamp())) {
                        hasMatch = true;
                        break;
                    }

                    // also check and message content
                    IMessage m1 = cev.getMessage();
                    IMessage m2 = evt.getMessage();
                    if ((m1 != null) && (m2 != null)
                            && m1.getContent().equals(m2.getContent())) {
                        hasMatch = true;
                        break;
                    }
                }
            // ignore if message is already saved
            if (hasMatch)
                return;
        }

        String sessionUuid = getSessionUuidByJid(room);
        writeMessage(sessionUuid, ChatMessage.DIR_OUT, room, message, evt.getTimestamp(), ChatMessage.MESSAGE_MUC_OUT);
    }

    public void messageDeliveryFailed(ChatRoomMessageDeliveryFailedEvent evt) {
        // nothing to do for the history service when delivery failed
    }

    // //////////////////////////////////////////////////////////////////////////
    // ChatRoomMessageListener implementation methods for AdHocChatRoom (for icq)

    public void messageReceived(AdHocChatRoomMessageReceivedEvent evt) {
        int msgType = evt.getEventType();

        // return if logging is switched off for this particular chatRoom or not a Http File Transfer message
        // Must save Http File Transfer message record for transfer status
        AdHocChatRoom room = evt.getSourceChatRoom();
        if (!isHistoryLoggingEnabled(room.getName())
                && (ChatMessage.MESSAGE_HTTP_FILE_DOWNLOAD != msgType)) {
            return;
        }

        String sessionUuid = getSessionUuidByJid(evt.getSourceChatRoom());
        writeMessage(sessionUuid, ChatMessage.DIR_IN, room, evt.getMessage(), evt.getTimestamp(), msgType);
    }

    public void messageDelivered(AdHocChatRoomMessageDeliveredEvent evt) {
        // return if logging is switched off for this particular chat room
        AdHocChatRoom room = evt.getSourceChatRoom();
        IMessage message = evt.getMessage();

        if (!isHistoryLoggingEnabled(room.getName()) || message.isRemoteOnly()) {
            return;
        }

        String sessionUuid = getSessionUuidByJid(evt.getSourceChatRoom());
        writeMessage(sessionUuid, ChatMessage.DIR_OUT, room, evt.getMessage(), evt.getTimestamp(), evt.getEventType());
    }

    public void messageDeliveryFailed(AdHocChatRoomMessageDeliveryFailedEvent evt) {
        // nothing to do for the history service when delivery failed
    }

    // ============== Store message to database ======================

    /**
     * Writes message to the history for MAM, ChatRoom out and AdHocChatRoom in/out
     *
     * @param chatId The entry with sessionUuid to which it will store the message
     * @param direction ChatMessage.DIR_IN or DIR_OUT
     * @param sender can either be EntityBareJid, ChatRoom or AdHocChatRoom (icq implementation)
     * @param message IMessage
     * @param msgTimestamp the timestamp when was message received that came from the protocol provider
     * @param msgType ChatMessage#Type
     */
    private void writeMessage(String chatId, String direction, Object sender,
            IMessage message, Date msgTimestamp, int msgType) {
        String jid = "";
        String entityJid;
        if (sender instanceof ChatRoom) { // ChatRoomJabberImpl
            ChatRoom chatRoom = (ChatRoom) sender;
            AccountID accountId = chatRoom.getParentProvider().getAccountID();
            jid = accountId.getAccountJid();
        }
        else if (sender instanceof AdHocChatRoom) {
            AdHocChatRoom chatRoom = (AdHocChatRoom) sender;
            AccountID accountId = chatRoom.getParentProvider().getAccountID();
            jid = accountId.getAccountJid();
        }
        else if (sender instanceof Jid) {
            jid = sender.toString();
        }
        // Strip off the resourcePart
        entityJid = jid.replaceAll("(\\w+)/.*", "$1");

        contentValues.clear();
        contentValues.put(ChatMessage.SESSION_UUID, chatId);
        contentValues.put(ChatMessage.TIME_STAMP, msgTimestamp.getTime());
        contentValues.put(ChatMessage.ENTITY_JID, entityJid);
        contentValues.put(ChatMessage.JID, jid);

        writeMessageToDB(message, direction, msgType);
    }

    /**
     * Writes the message to the history for ChatRoom incoming message
     *
     * @param chatId The entry with chatSessionUuid to which it will store the message
     * @param direction the direction of the message.
     * @param from coming from
     * @param message IMessage
     * @param msgTimestamp the timestamp when was message received that came from the protocol provider
     * @param msgType ChatMessage#Type
     */
    private void writeMessage(String chatId, String direction, ChatRoomMember from,
            IMessage message, Date msgTimestamp, int msgType) {
        // missing from, strange messages, most probably a history coming from server and
        // probably already written
        if (from == null)
            return;

        // String entityJid = from.getChatRoom().getName() + "/" + nick;
        String jid = from.getContactAddress();  // contact entityFullJid
        String entityJid = jid.replaceAll("(\\w+)/.*", "$1");

        contentValues.clear();
        contentValues.put(ChatMessage.SESSION_UUID, chatId);
        contentValues.put(ChatMessage.TIME_STAMP, msgTimestamp.getTime());
        contentValues.put(ChatMessage.ENTITY_JID, entityJid);
        contentValues.put(ChatMessage.JID, jid);

        writeMessageToDB(message, direction, msgType);
    }

    /**
     * Writes a message to the history for chatMessage in/out.
     *
     * @param chatId The entry with chatSessionUuid to which it will store the message
     * @param direction the direction of the message.
     * @param contact the communicator contact for this chat
     * @param sender message sender
     * @param message IMessage
     * @param msgTimestamp the timestamp when was message received that came from the protocol provider
     * @param msgType ChatMessage#Type
     */
    private void writeMessage(String chatId, String direction, Contact contact, String sender,
            IMessage message, Date msgTimestamp, int msgType) {
        contentValues.clear();
        contentValues.put(ChatMessage.SESSION_UUID, chatId);
        contentValues.put(ChatMessage.TIME_STAMP, msgTimestamp.getTime());
        contentValues.put(ChatMessage.ENTITY_JID, contact.getAddress());
        // JID is not stored for chatMessage or incoming message
        contentValues.put(ChatMessage.JID, sender);

        writeMessageToDB(message, direction, msgType);
    }

    /**
     * Inserts message to the history. Allows to update the already saved message.
     *
     * @param direction String direction of the message in or out.
     * @param source The source Contact
     * @param destination The destination Contact
     * @param message IMessage message to be written
     * @param msgTimestamp the timestamp when was message received that came from the protocol provider
     * @param isSmsSubtype whether message to write is an sms
     */
    public void insertMessage(String direction, Contact source, Contact destination,
            IMessage message, Date msgTimestamp, boolean isSmsSubtype) {
        // return if logging is switched off for this particular contact
        MetaContact metaContact = MessageHistoryActivator.getContactListService()
                .findMetaContactByContact(destination);
        if (metaContact != null && !isHistoryLoggingEnabled(metaContact.getMetaUID())) {
            return;
        }

        String sessionUuid = getSessionUuidByJid(destination);
        int msgType = isSmsSubtype ? ChatMessage.MESSAGE_SMS_OUT : ChatMessage.MESSAGE_OUT;

        contentValues.clear();
        contentValues.put(ChatMessage.SESSION_UUID, sessionUuid);
        contentValues.put(ChatMessage.TIME_STAMP, msgTimestamp.getTime());
        contentValues.put(ChatMessage.ENTITY_JID, source.getAddress());
        contentValues.put(ChatMessage.JID, destination.getAddress());

        writeMessageToDB(message, direction, msgType);
    }

    /**
     * Update the reset of the message content and write to the dataBase
     *
     * @param message IMessage message to be written
     * @param direction ChatMessage.DIR_IN or DIR_OUT
     * @param msgType ChatMessage#Type
     */
    private void writeMessageToDB(IMessage message, String direction, int msgType) {
        contentValues.put(ChatMessage.UUID, message.getMessageUID());
        contentValues.put(ChatMessage.MSG_BODY, message.getContent());
        contentValues.put(ChatMessage.ENC_TYPE, message.getEncType());
        contentValues.put(ChatMessage.CARBON, message.isCarbon() ? 1 : 0);
        contentValues.put(ChatMessage.DIRECTION, direction);
        contentValues.put(ChatMessage.MSG_TYPE, msgType);

        if (ChatMessage.DIR_OUT.equals(direction)) {
            contentValues.put(ChatMessage.STATUS, ChatMessage.MESSAGE_OUT);
            contentValues.put(ChatMessage.SERVER_MSG_ID, message.getServerMsgId());
            contentValues.put(ChatMessage.REMOTE_MSG_ID, message.getRemoteMsgId());
            contentValues.put(ChatMessage.READ, ChatMessage.MESSAGE_DELIVERY_CLIENT_SENT);
        }
        else {
            contentValues.put(ChatMessage.STATUS, (ChatMessage.MESSAGE_HTTP_FILE_DOWNLOAD == msgType)
                    ? FileRecord.STATUS_UNKNOWN : ChatMessage.MESSAGE_IN);
            contentValues.put(ChatMessage.REMOTE_MSG_ID, message.getMessageUID());
        }
        mDB.insert(ChatMessage.TABLE_NAME, null, contentValues);
    }

    //============ service change events handler ================//

    /**
     * Remove a configuration service.
     *
     * @param historyService HistoryService
     */
    public void unsetHistoryService(HistoryService historyService) {
        synchronized (syncRoot_HistoryService) {
            if (this.historyService == historyService) {
                this.historyService = null;
                Timber.d("History service unregistered.");
            }
        }
    }

    /**
     * Called to notify interested parties that a change in our presence in a chat room has
     * occurred. Changes may include us being kicked, join, left.
     *
     * @param evt the <code>LocalUserChatRoomPresenceChangeEvent</code> instance containing the chat
     * room and the type, and reason of the change
     */
    public void localUserPresenceChanged(LocalUserChatRoomPresenceChangeEvent evt) {
        if (LocalUserChatRoomPresenceChangeEvent.LOCAL_USER_JOINED.equals(evt.getEventType())) {
            if (!evt.getChatRoom().isSystem()) {
                evt.getChatRoom().addMessageListener(this);

                if (this.messageSourceService != null)
                    evt.getChatRoom().addMessageListener(messageSourceService);
            }
        }
        else {
            evt.getChatRoom().removeMessageListener(this);
            if (this.messageSourceService != null)
                evt.getChatRoom().removeMessageListener(messageSourceService);
        }
    }

    /**
     * Adding progress listener for monitoring progress of search process
     *
     * @param listener HistorySearchProgressListener
     */
    public void addSearchProgressListener(MessageHistorySearchProgressListener listener) {
        synchronized (progressListeners) {
            HistorySearchProgressListener wrapperListener = new SearchProgressWrapper(listener);
            progressListeners.put(listener, wrapperListener);
        }
    }

    /**
     * Removing progress listener
     *
     * @param listener HistorySearchProgressListener
     */
    public void removeSearchProgressListener(MessageHistorySearchProgressListener listener) {
        synchronized (progressListeners) {
            progressListeners.remove(listener);
        }
    }

    // =========== find messages for metaContact and chatRoom with keywords ==================

    /**
     * Returns all the messages exchanged by all the contacts in the supplied metaContact between
     * the given dates including startDate and having the given keywords
     *
     * @param metaContact MetaContact
     * @param startDate Date the start date of the conversations
     * @param endDate Date the end date of the conversations
     * @param keywords array of keywords
     * @param caseSensitive is keywords search case sensitive
     *
     * @return Collection of MessageReceivedEvents or MessageDeliveredEvents
     */
    public Collection<EventObject> findByPeriod(MetaContact metaContact, Date startDate,
            Date endDate, String[] keywords, boolean caseSensitive) {
        HashSet<EventObject> result = new HashSet<>();
        String startTimeStamp = String.valueOf(startDate.getTime());
        String endTimeStamp = String.valueOf(endDate.getTime());
        StringBuilder filterLike = new StringBuilder("( ");
        for (String word : keywords) {
            filterLike.append(ChatMessage.MSG_BODY + " LIKE '%").append(word).append("%' OR ");
        }
        filterLike = new StringBuilder(filterLike.substring(0, filterLike.length() - 4) + " )");

        Iterator<Contact> contacts = metaContact.getContacts();
        while (contacts.hasNext()) {
            Contact contact = contacts.next();
            String sessionUuid = getSessionUuidByJid(contact);
            String[] args = {sessionUuid, startTimeStamp, endTimeStamp};

            Cursor cursor = mDB.query(ChatMessage.TABLE_NAME, null,
                    ChatMessage.SESSION_UUID + "=? AND " + ChatMessage.TIME_STAMP + ">=? AND "
                            + ChatMessage.TIME_STAMP + "<? AND " + filterLike, args, null, null, ORDER_ASC);

            while (cursor.moveToNext()) {
                result.add(convertHistoryRecordToMessageEvent(cursor, contact));
            }
        }
        return result;
    }

    /**
     * Returns all the messages exchanged by all the contacts in the supplied metaContact
     * having the given keyword
     *
     * @param metaContact MetaContact
     * @param keyword keyword
     * @param caseSensitive is keywords search case sensitive
     *
     * @return Collection of MessageReceivedEvents or MessageDeliveredEvents
     */
    public Collection<EventObject> findByKeyword(MetaContact metaContact, String keyword,
            boolean caseSensitive) {
        HashSet<EventObject> result = new HashSet<>();
        String filterLike = "( " + ChatMessage.MSG_BODY + " LIKE '%" + keyword + "%' )";

        Iterator<Contact> contacts = metaContact.getContacts();
        while (contacts.hasNext()) {
            Contact contact = contacts.next();
            String sessionUuid = getSessionUuidByJid(contact);
            String[] args = {sessionUuid};

            Cursor cursor = mDB.query(ChatMessage.TABLE_NAME, null,
                    ChatMessage.SESSION_UUID + "=? AND " + filterLike, args, null, null, ORDER_ASC);

            while (cursor.moveToNext()) {
                result.add(convertHistoryRecordToMessageEvent(cursor, contact));
            }
        }
        return result;
    }

    /**
     * Returns all the messages exchanged by all the contacts in the supplied metaContact
     * having the given keywords
     *
     * @param metaContact MetaContact
     * @param keywords keyword
     * @param caseSensitive is keywords search case sensitive
     *
     * @return Collection of MessageReceivedEvents or MessageDeliveredEvents
     */
    public Collection<EventObject> findByKeywords(MetaContact metaContact,
            String[] keywords, boolean caseSensitive) {
        HashSet<EventObject> result = new HashSet<>();
        StringBuilder filterLike = new StringBuilder("( ");
        for (String word : keywords) {
            filterLike.append(ChatMessage.MSG_BODY + " LIKE '%").append(word).append("%' OR ");
        }
        filterLike = new StringBuilder(filterLike.substring(0, filterLike.length() - 4) + " )");

        Iterator<Contact> contacts = metaContact.getContacts();
        while (contacts.hasNext()) {
            Contact contact = contacts.next();
            String sessionUuid = getSessionUuidByJid(contact);
            String[] args = {sessionUuid};

            Cursor cursor = mDB.query(ChatMessage.TABLE_NAME, null,
                    ChatMessage.SESSION_UUID + "=? AND " + filterLike, args, null, null, ORDER_ASC);

            while (cursor.moveToNext()) {
                result.add(convertHistoryRecordToMessageEvent(cursor, contact));
            }
        }
        return result;
    }

    /**
     * Returns all the messages exchanged in the supplied chat room on and after the given date
     *
     * @param room The chat room
     * @param startDate Date the start date of the conversations
     *
     * @return Collection of MessageReceivedEvents or MessageDeliveredEvents
     */
    public Collection<EventObject> findByStartDate(ChatRoom room, Date startDate) {
        HashSet<EventObject> result = new HashSet<>();
        String startTimeStamp = String.valueOf(startDate.getTime());
        String sessionUuid = getSessionUuidByJid(room);
        String[] args = {sessionUuid, startTimeStamp};

        Cursor cursor = mDB.query(ChatMessage.TABLE_NAME, null,
                ChatMessage.SESSION_UUID + "=? AND " + ChatMessage.TIME_STAMP + ">=?",
                args, null, null, ORDER_ASC);

        while (cursor.moveToNext()) {
            result.add(convertHistoryRecordToMessageEvent(cursor, room));
        }
        return result;
    }

    /**
     * Returns all the messages exchanged in the supplied chat room before the given date
     *
     * @param room The chat room
     * @param endDate Date the end date of the conversations
     *
     * @return Collection of MessageReceivedEvents or MessageDeliveredEvents
     */
    public Collection<EventObject> findByEndDate(ChatRoom room, Date endDate) {
        HashSet<EventObject> result = new HashSet<>();
        String endTimeStamp = String.valueOf(endDate.getTime());
        String sessionUuid = getSessionUuidByJid(room);
        String[] args = {sessionUuid, endTimeStamp};

        Cursor cursor = mDB.query(ChatMessage.TABLE_NAME, null,
                ChatMessage.SESSION_UUID + "=? AND " + ChatMessage.TIME_STAMP + "<?",
                args, null, null, ORDER_ASC);

        while (cursor.moveToNext()) {
            result.add(convertHistoryRecordToMessageEvent(cursor, room));
        }
        return result;
    }

    /**
     * Returns all the messages exchanged in the supplied chat room between the given dates
     *
     * @param room The chat room
     * @param startDate Date the start date of the conversations
     * @param endDate Date the end date of the conversations
     *
     * @return Collection of MessageReceivedEvents or MessageDeliveredEvents
     */
    public Collection<EventObject> findByPeriod(ChatRoom room, Date startDate, Date endDate) {
        HashSet<EventObject> result = new HashSet<>();
        String startTimeStamp = String.valueOf(startDate.getTime());
        String endTimeStamp = String.valueOf(endDate.getTime());
        String sessionUuid = getSessionUuidByJid(room);
        String[] args = {sessionUuid, startTimeStamp, endTimeStamp};

        Cursor cursor = mDB.query(ChatMessage.TABLE_NAME, null,
                ChatMessage.SESSION_UUID + "=? AND " + ChatMessage.TIME_STAMP + ">=? AND "
                        + ChatMessage.TIME_STAMP + "<?", args, null, null, ORDER_ASC);

        while (cursor.moveToNext()) {
            result.add(convertHistoryRecordToMessageEvent(cursor, room));
        }
        return result;
    }

    /**
     * Returns all the messages exchanged in the supplied chat room between the given
     * dates and having the given keywords
     *
     * @param room The chat room
     * @param startDate Date the start date of the conversations
     * @param endDate Date the end date of the conversations
     * @param keywords array of keywords
     *
     * @return Collection of MessageReceivedEvents or MessageDeliveredEvents
     */
    public Collection<EventObject> findByPeriod(ChatRoom room, Date startDate, Date endDate,
            String[] keywords) {
        return findByPeriod(room, startDate, endDate, keywords, false);
    }

    /**
     * Returns all the messages exchanged in the supplied chat room between the given
     * dates and having the given keywords
     *
     * @param room The chat room
     * @param startDate Date the start date of the conversations
     * @param endDate Date the end date of the conversations
     * @param keywords array of keywords
     * @param caseSensitive is keywords search case sensitive
     *
     * @return Collection of MessageReceivedEvents or MessageDeliveredEvents
     */
    public Collection<EventObject> findByPeriod(ChatRoom room, Date startDate, Date
            endDate, String[] keywords, boolean caseSensitive) {
        HashSet<EventObject> result = new HashSet<>();
        String startTimeStamp = String.valueOf(startDate.getTime());
        String endTimeStamp = String.valueOf(endDate.getTime());
        String sessionUuid = getSessionUuidByJid(room);
        String[] args = {sessionUuid, startTimeStamp, endTimeStamp};
        StringBuilder filterLike = new StringBuilder("( ");
        for (String word : keywords) {
            filterLike.append(ChatMessage.MSG_BODY + " LIKE '%").append(word).append("%' OR ");
        }
        filterLike = new StringBuilder(filterLike.substring(0, filterLike.length() - 4) + " )");

        Cursor cursor = mDB.query(ChatMessage.TABLE_NAME, null,
                ChatMessage.SESSION_UUID + "=? AND " + ChatMessage.TIME_STAMP + ">=? AND "
                        + ChatMessage.TIME_STAMP + "<? AND " + filterLike, args, null, null, ORDER_ASC);

        while (cursor.moveToNext()) {
            result.add(convertHistoryRecordToMessageEvent(cursor, room));
        }
        return result;
    }

    /**
     * Returns all the messages exchanged in the supplied room having the given keyword
     *
     * @param room The Chat room
     * @param keyword keyword
     *
     * @return Collection of MessageReceivedEvents or MessageDeliveredEvents
     */
    public Collection<EventObject> findByKeyword(ChatRoom room, String keyword) {
        return findByKeyword(room, keyword, false);
    }

    /**
     * Returns all the messages exchanged in the supplied chat room having the given
     * keyword
     *
     * @param room The chat room
     * @param keyword keyword
     * @param caseSensitive is keywords search case sensitive
     *
     * @return Collection of MessageReceivedEvents or MessageDeliveredEvents
     */
    public Collection<EventObject> findByKeyword(ChatRoom room, String keyword,
            boolean caseSensitive) {
        HashSet<EventObject> result = new HashSet<>();
        String sessionUuid = getSessionUuidByJid(room);
        String[] args = {sessionUuid};
        String filterLike = "( " + ChatMessage.MSG_BODY + " LIKE '%" + keyword + "%' )";

        Cursor cursor = mDB.query(ChatMessage.TABLE_NAME, null,
                ChatMessage.SESSION_UUID + "=? AND " + filterLike, args, null, null, ORDER_ASC);

        while (cursor.moveToNext()) {
            result.add(convertHistoryRecordToMessageEvent(cursor, room));
        }
        return result;
    }

    /**
     * Returns all the messages exchanged in the supplied chat room having the given
     * keywords
     *
     * @param room The chat room
     * @param keywords keyword
     *
     * @return Collection of MessageReceivedEvents or MessageDeliveredEvents
     */
    public Collection<EventObject> findByKeywords(ChatRoom room, String[] keywords) {
        return findByKeywords(room, keywords, false);
    }

    /**
     * Returns all the messages exchanged in the supplied chat room having the given
     * keywords
     *
     * @param room The chat room
     * @param keywords keyword
     * @param caseSensitive is keywords search case sensitive
     *
     * @return Collection of MessageReceivedEvents or MessageDeliveredEvents
     */
    public Collection<EventObject> findByKeywords(ChatRoom room, String[] keywords,
            boolean caseSensitive) {
        HashSet<EventObject> result = new HashSet<>();
        String sessionUuid = getSessionUuidByJid(room);
        String[] args = {sessionUuid};
        StringBuilder filterLike = new StringBuilder("( ");
        for (String word : keywords) {
            filterLike.append(ChatMessage.MSG_BODY + " LIKE '%").append(word).append("%' OR ");
        }
        filterLike = new StringBuilder(filterLike.substring(0, filterLike.length() - 4) + " )");

        Cursor cursor = mDB.query(ChatMessage.TABLE_NAME, null,
                ChatMessage.SESSION_UUID + "=? AND " + filterLike, args, null, null, ORDER_ASC);

        while (cursor.moveToNext()) {
            result.add(convertHistoryRecordToMessageEvent(cursor, room));
        }
        return result;
    }

    /**
     * Returns the supplied number of recent messages exchanged in the supplied chat room
     *
     * @param room The chat room
     * @param count messages count
     *
     * @return Collection of MessageReceivedEvents or MessageDeliveredEvents
     */
    public Collection<EventObject> findLast(ChatRoom room, int count) {
        LinkedList<EventObject> result = new LinkedList<>();
        String sessionUuid = getSessionUuidByJid(room);
        String[] args = {sessionUuid};

        Cursor cursor = mDB.query(ChatMessage.TABLE_NAME, null, ChatMessage.SESSION_UUID
                + "=?", args, null, null, ORDER_DESC, String.valueOf(count));

        while (cursor.moveToNext()) {
            result.add(convertHistoryRecordToMessageEvent(cursor, room));
        }

        Collections.sort(result, new MessageEventComparator<>());
        return result;
    }

    /**
     * Returns the supplied number of recent messages on and after the given startDate exchanged
     * in the supplied chat room
     *
     * @param room The chat room
     * @param startDate messages on and after date
     * @param count messages count
     *
     * @return Collection of MessageReceivedEvents or MessageDeliveredEvents
     */
    public Collection<EventObject> findFirstMessagesAfter(ChatRoom room, Date startDate,
            int count) {
        LinkedList<EventObject> result = new LinkedList<>();
        String startTimeStamp = String.valueOf(startDate.getTime());
        String sessionUuid = getSessionUuidByJid(room);
        String[] args = {sessionUuid, startTimeStamp};

        Cursor cursor = mDB.query(ChatMessage.TABLE_NAME, null,
                ChatMessage.SESSION_UUID + "=? AND " + ChatMessage.TIME_STAMP + ">=?",
                args, null, null, ORDER_DESC, String.valueOf(count));

        while (cursor.moveToNext()) {
            result.add(convertHistoryRecordToMessageEvent(cursor, room));
        }

        Collections.sort(result, new ChatRoomMessageEventComparator<>());
        return result;
    }

    /**
     * Returns the supplied number of recent messages before the given endDate exchanged in
     * the supplied chat room
     *
     * @param room The chat room
     * @param endDate messages before date
     * @param count messages count
     *
     * @return Collection of MessageReceivedEvents or MessageDeliveredEvents
     */
    public Collection<EventObject> findLastMessagesBefore(ChatRoom room, Date endDate, int count) {
        LinkedList<EventObject> result = new LinkedList<>();
        String endTimeStamp = String.valueOf(endDate.getTime());
        String sessionUuid = getSessionUuidByJid(room);
        String[] args = {sessionUuid, endTimeStamp};

        Cursor cursor = mDB.query(ChatMessage.TABLE_NAME, null,
                ChatMessage.SESSION_UUID + "=? AND " + ChatMessage.TIME_STAMP + "<?",
                args, null, null, ORDER_DESC, String.valueOf(count));

        while (cursor.moveToNext()) {
            result.add(convertHistoryRecordToMessageEvent(cursor, room));
        }

        Collections.sort(result, new ChatRoomMessageEventComparator<>());
        return result;
    }

    /**
     * Loads the History and MessageHistoryService. Registers the service in the bundle context.
     */
    private void loadMessageHistoryService() {
        configService.addPropertyChangeListener(
                MessageHistoryService.PNAME_IS_RECENT_MESSAGES_DISABLED, msgHistoryPropListener);

        boolean isRecentMessagesDisabled = configService.getBoolean(
                MessageHistoryService.PNAME_IS_RECENT_MESSAGES_DISABLED, false);

        if (!isRecentMessagesDisabled)
            loadRecentMessages();

        // start listening for newly register or removed protocol providers
        bundleContext.addServiceListener(this);

        for (ProtocolProviderService pps : getCurrentlyAvailableProviders()) {
            this.handleProviderAdded(pps);
        }
    }

    /**
     * Returns currently registered in osgi ProtocolProviderServices.
     *
     * @return currently registered in osgi ProtocolProviderServices.
     */
    List<ProtocolProviderService> getCurrentlyAvailableProviders() {
        List<ProtocolProviderService> res = new ArrayList<>();

        ServiceReference<?>[] protocolProviderRefs;
        try {
            protocolProviderRefs = bundleContext.getServiceReferences(ProtocolProviderService.class.getName(), null);
        } catch (InvalidSyntaxException ex) {
            // this shouldn't happen since we're providing no parameter string but let's log just
            // in case.
            Timber.e(ex, "Error while retrieving service refs");
            return res;
        }

        // in case we found any
        if (protocolProviderRefs != null) {
            Timber.d("Found %s already installed providers.", protocolProviderRefs.length);
            for (ServiceReference<?> protocolProviderRef : protocolProviderRefs) {
                ProtocolProviderService provider = (ProtocolProviderService) bundleContext.getService(protocolProviderRef);
                res.add(provider);
            }
        }
        return res;
    }

    /**
     * Stops the MessageHistoryService.
     */
    private void stopMessageHistoryService() {
        // start listening for newly register or removed protocol providers
        bundleContext.removeServiceListener(this);

        ServiceReference<?>[] protocolProviderRefs;
        try {
            protocolProviderRefs = bundleContext.getServiceReferences(ProtocolProviderService.class.getName(), null);
        } catch (InvalidSyntaxException ex) {
            // this shouldn't happen since we're providing no parameter string but let's log just
            // in case.
            Timber.e(ex, "Error while retrieving service refs");
            return;
        }

        // in case we found any
        if (protocolProviderRefs != null) {
            for (ServiceReference<?> protocolProviderRef : protocolProviderRefs) {
                ProtocolProviderService provider = (ProtocolProviderService) bundleContext.getService(protocolProviderRef);
                this.handleProviderRemoved(provider);
            }
        }
    }

    /**
     * Called to notify interested parties that a change in our presence in an ad-hoc chat room
     * has occurred. Changes may include us being join, left.
     *
     * @param evt the <code>LocalUserAdHocChatRoomPresenceChangeEvent</code> instance containing the ad-hoc
     * chat room and the type, and reason of the change
     */
    public void localUserAdHocPresenceChanged(LocalUserAdHocChatRoomPresenceChangeEvent evt) {
        if (LocalUserAdHocChatRoomPresenceChangeEvent.LOCAL_USER_JOINED.equals(evt.getEventType())) {
            evt.getAdHocChatRoom().addMessageListener(this);
        }
        else {
            evt.getAdHocChatRoom().removeMessageListener(this);
        }
    }

    // =============== Erase chat history for entities for given message Uuids =====================

    /**
     * Permanently removes all locally stored message history.
     * - Remove only chatMessages for metaContacts i.e. ChatSession.MODE_SINGLE
     * - Remove both chatMessage and chatSession info (no currently) for muc i.e. ChatSession.MODE_MULTI
     */
    public void eraseLocallyStoredChatHistory(int chatMode) {
        String[] args = {String.valueOf(chatMode)};
        String[] columns = {ChatSession.SESSION_UUID};
        List<String> sessionUuids = new ArrayList<>();

        Cursor cursor = mDB.query(ChatSession.TABLE_NAME, columns, ChatSession.MODE + "=?",
                args, null, null, null);
        while (cursor.moveToNext()) {
            sessionUuids.add(cursor.getString(0));
        }
        cursor.close();
        purgeLocallyStoredHistory(sessionUuids, (ChatSession.MODE_SINGLE != chatMode));
    }

    /**
     * Permanently removes locally stored message history as listed in msgUUIDs;
     * Or all the chat message for the metaContact if msgUUIDs is null.
     *
     * @param metaContact metaContact
     * @param msgUUIDs Purge all the chat messages listed in the msgUUIDs.
     */
    public void eraseLocallyStoredChatHistory(MetaContact metaContact, List<String> msgUUIDs) {
        if (msgUUIDs == null) {
            Iterator<Contact> contacts = metaContact.getContacts();
            while (contacts.hasNext()) {
                Contact contact = contacts.next();
                purgeLocallyStoredHistory(Collections.singletonList(getSessionUuidByJid(contact)), false);
            }
        }
        else {
            purgeLocallyStoredHistory(msgUUIDs);
        }
    }

    /**
     * Permanently removes locally stored message history as listed in msgUUIDs;
     * Or all the chat message for the specified room if msgUUIDs is null.
     *
     * @param room ChatRoom
     * @param msgUUIDs Purge all the chat messages listed in the msgUUIDs.
     */
    public void eraseLocallyStoredChatHistory(ChatRoom room, List<String> msgUUIDs) {
        if (msgUUIDs == null) {
            purgeLocallyStoredHistory(Collections.singletonList(getSessionUuidByJid(room)), true);
        }
        else {
            purgeLocallyStoredHistory(msgUUIDs);
        }
    }

    /**
     * Permanently removes locally stored message history as specified in msgUUIDs.
     *
     * @param msgUUIDs list of message Uuid to be erase
     */
    private void purgeLocallyStoredHistory(List<String> msgUUIDs) {
        for (String uuid : msgUUIDs) {
            String[] args = {uuid};
            mDB.delete(ChatMessage.TABLE_NAME, ChatMessage.UUID + "=?", args);
        }
    }

    /**
     * Permanently removes locally stored message history for each sessionUuid listed in sessionUuids.
     * - Remove only chatMessages for metaContacts
     * - Remove both chatSessions and chatMessages for muc
     *
     * @param sessionUuids list of sessionUuids to be erased.
     * @param eraseSid erase also the item in ChatSession Table if true.
     */
    public void purgeLocallyStoredHistory(List<String> sessionUuids, boolean eraseSid) {
        for (String uuid : sessionUuids) {
            String[] args = {uuid};
            // purged all messages with the same sessionUuid
            mDB.delete(ChatMessage.TABLE_NAME, ChatMessage.SESSION_UUID + "=?", args);

            // Purge the sessionUuid in the ChatSession if true
            if (eraseSid) {
                mDB.delete(ChatSession.TABLE_NAME, ChatSession.SESSION_UUID + "=?", args);
            }
        }
    }

    // =============== End Erase chat history for entities for given message Uuids =====================

    /**
     * Retrieve all locally stored media file paths for the specified descriptor
     *
     * @param descriptor MetaContact or ChatRoomWrapper
     *
     * @return List of media file Paths
     */
    public List<String> getLocallyStoredFilePath(Object descriptor) {
        List<String> msgFilePathDel = new ArrayList<>();
        String filePath;
        String sessionUuid = null;

        if (descriptor instanceof MetaContact) {
            Iterator<Contact> contacts = ((MetaContact) descriptor).getContacts();
            Contact contact = contacts.next();
            if (contact != null)
                sessionUuid = getSessionUuidByJid(contact);
        }
        else {
            ChatRoom chatRoom = ((ChatRoomWrapper) descriptor).getChatRoom();
            if (chatRoom != null)
                sessionUuid = getSessionUuidByJid(chatRoom);
        }

        if (sessionUuid != null) {
            String[] args = {sessionUuid};
            String[] columns = {ChatMessage.FILE_PATH};

            Cursor cursor = mDB.query(ChatMessage.TABLE_NAME, columns, ChatMessage.SESSION_UUID + "=?",
                    args, null, null, null);
            while (cursor.moveToNext()) {
                filePath = cursor.getString(0);
                if (!TextUtils.isEmpty(filePath)) {
                    msgFilePathDel.add(filePath);
                }
            }
            cursor.close();
        }
        return msgFilePathDel;
    }

    /**
     * Retrieve all locally stored media file paths for all the received messages
     *
     * @return List of media file Paths
     */
    public List<String> getLocallyStoredFilePath() {
        List<String> msgFilePathDel = new ArrayList<>();
        String filePath;
        String[] columns = {ChatMessage.FILE_PATH};

        Cursor cursor = mDB.query(ChatMessage.TABLE_NAME, columns, ChatMessage.FILE_PATH + " IS NOT NULL",
                null, null, null, null);
        while (cursor.moveToNext()) {
            filePath = cursor.getString(0);
            if (!TextUtils.isEmpty(filePath)) {
                msgFilePathDel.add(filePath);
            }
        }
        cursor.close();
        return msgFilePathDel;
    }

    /**
     * Returns {@code true} if the "PNAME_IS_MESSAGE_HISTORY_ENABLED" property is true,
     * otherwise - returns {@code false}. Indicates to the user interface whether the
     * history logging is enabled.
     *
     * @return {@code true} if the "IS_MESSAGE_HISTORY_ENABLED" property is true,
     * otherwise - returns {@code false}.
     */
    public boolean isHistoryLoggingEnabled() {
        return isHistoryLoggingEnabled;
    }

    /**
     * Updates the "isHistoryLoggingEnabled" property through the <code>ConfigurationService</code>.
     *
     * @param isEnabled indicates if the history logging is enabled.
     */
    public void setHistoryLoggingEnabled(boolean isEnabled) {
        isHistoryLoggingEnabled = isEnabled;
        configService.setProperty(MessageHistoryService.PNAME_IS_MESSAGE_HISTORY_ENABLED,
                Boolean.toString(isHistoryLoggingEnabled));
    }

    /**
     * Returns <code>true</code> if the "PNAME_IS_MESSAGE_HISTORY_PER_CONTACT_ENABLED_PREFIX.id"
     * property is true for the <code>id</code>, otherwise - returns <code>false</code>.
     * The Global isHistoryLoggingEnabled must be enabled for this to take effect.
     * Indicates to the user interface whether the history logging is enabled for the
     * supplied id (id for metaContact or for chat room).
     *
     * @return <code>true</code> if the "PNAME_IS_MESSAGE_HISTORY_PER_CONTACT_ENABLED_PREFIX"
     * property for the <code>id</code> AND isHistoryLoggingEnabled are true,
     * otherwise - returns <code>false</code>.
     */
    public boolean isHistoryLoggingEnabled(String id) {
        return isHistoryLoggingEnabled && configService.getBoolean(
                MessageHistoryService.PNAME_IS_MESSAGE_HISTORY_PER_CONTACT_ENABLED_PREFIX
                        + "." + id, true);
    }

    /**
     * Updates the "isHistoryLoggingEnabled" property through the <code>ConfigurationService</code>
     * for the contact.
     *
     * @param isEnabled indicates if the history logging is enabled for the contact.
     */
    public void setHistoryLoggingEnabled(String id, boolean isEnabled) {
        configService.setProperty(
                MessageHistoryService.PNAME_IS_MESSAGE_HISTORY_PER_CONTACT_ENABLED_PREFIX
                        + "." + id, isEnabled ? null : false);
    }

    /**
     * Simple message implementation.
     */
    private static class MessageImpl extends AbstractMessage {
        private final boolean isOutgoing;
        private final Date messageReceivedDate;
        private final int msgSubType;

        MessageImpl(String content, int encType, String subject, String messageUID, int xferStatus, int receiptStatus,
                String serverMsgId, String remoteMsgId, boolean isOutgoing, Date messageReceivedDate, int msgSubType) {
            super(content, encType, subject, messageUID, xferStatus, receiptStatus, serverMsgId, remoteMsgId);
            this.isOutgoing = isOutgoing;
            this.messageReceivedDate = messageReceivedDate;
            this.msgSubType = msgSubType;
        }

        public Date getMessageReceivedDate() {
            return messageReceivedDate;
        }

        public int getMsgSubType() {
            return msgSubType;
        }
    }

    /**
     * Used to compare MessageDeliveredEvent or MessageReceivedEvent and to be ordered in TreeSet
     * according their timestamp
     */
    private static class MessageEventComparator<T> implements Comparator<T> {
        private final boolean reverseOrder;

        MessageEventComparator(boolean reverseOrder) {
            this.reverseOrder = reverseOrder;
        }

        MessageEventComparator() {
            this(false);
        }

        public int compare(T o1, T o2) {
            Date date1;
            Date date2;

            if (o1 instanceof MessageDeliveredEvent)
                date1 = ((MessageDeliveredEvent) o1).getTimestamp();
            else if (o1 instanceof MessageReceivedEvent)
                date1 = ((MessageReceivedEvent) o1).getTimestamp();
            else if (o1 instanceof ChatRoomMessageDeliveredEvent)
                date1 = ((ChatRoomMessageDeliveredEvent) o1).getTimestamp();
            else if (o1 instanceof ChatRoomMessageReceivedEvent)
                date1 = ((ChatRoomMessageReceivedEvent) o1).getTimestamp();
            else
                return 0;

            if (o2 instanceof MessageDeliveredEvent)
                date2 = ((MessageDeliveredEvent) o2).getTimestamp();
            else if (o2 instanceof MessageReceivedEvent)
                date2 = ((MessageReceivedEvent) o2).getTimestamp();
            else if (o2 instanceof ChatRoomMessageDeliveredEvent)
                date2 = ((ChatRoomMessageDeliveredEvent) o2).getTimestamp();
            else if (o2 instanceof ChatRoomMessageReceivedEvent)
                date2 = ((ChatRoomMessageReceivedEvent) o2).getTimestamp();
            else
                return 0;

            if (reverseOrder)
                return date2.compareTo(date1);
            else
                return date1.compareTo(date2);
        }
    }

    /**
     * Used to compare ChatRoomMessageDeliveredEvent or ChatRoomMessageReceivedEvent and to be
     * ordered in TreeSet according their timestamp
     */
    private static class ChatRoomMessageEventComparator<T> implements Comparator<T> {
        public int compare(T o1, T o2) {
            Date date1;
            Date date2;

            if (o1 instanceof ChatRoomMessageDeliveredEvent)
                date1 = ((ChatRoomMessageDeliveredEvent) o1).getTimestamp();
            else if (o1 instanceof ChatRoomMessageReceivedEvent)
                date1 = ((ChatRoomMessageReceivedEvent) o1).getTimestamp();
            else
                return 0;

            if (o2 instanceof ChatRoomMessageDeliveredEvent)
                date2 = ((ChatRoomMessageDeliveredEvent) o2).getTimestamp();
            else if (o2 instanceof ChatRoomMessageReceivedEvent)
                date2 = ((ChatRoomMessageReceivedEvent) o2).getTimestamp();
            else
                return 0;

            return date1.compareTo(date2);
        }
    }

    /**
     * A wrapper around HistorySearchProgressListener that fires events for
     * MessageHistorySearchProgressListener
     */
    private class SearchProgressWrapper implements HistorySearchProgressListener {
        double currentReaderProgressRatio = 0;
        double accumulatedRatio = 0;
        double currentProgress = 0;
        double lastHistoryProgress = 0;
        // used for more precise calculations with double values
        int raiser = 1000;
        private final MessageHistorySearchProgressListener listener;

        SearchProgressWrapper(MessageHistorySearchProgressListener listener) {
            this.listener = listener;
        }

        private void setCurrentValues(HistoryReader currentReader, int allRecords) {
            currentReaderProgressRatio = (double) currentReader.countRecords() / allRecords * raiser;
            accumulatedRatio += currentReaderProgressRatio;
        }

        public void progressChanged(ProgressEvent evt) {
            int progress = getProgressMapping(evt);
            currentProgress = progress;

            listener.progressChanged(new net.java.sip.communicator.service.msghistory.event
                    .ProgressEvent(MessageHistoryServiceImpl.this, evt, progress / raiser));
        }

        /**
         * Calculates the progress according the count of the records we will search
         *
         * @param evt the progress event
         *
         * @return int
         */
        private int getProgressMapping(ProgressEvent evt) {
            double tmpHistoryProgress = currentReaderProgressRatio * evt.getProgress();
            currentProgress += tmpHistoryProgress - lastHistoryProgress;

            if (evt.getProgress() == HistorySearchProgressListener.PROGRESS_MAXIMUM_VALUE) {
                lastHistoryProgress = 0;

                // this is the last one and the last event fire the max there will be looses in
                // currentProgress due to the deviation
                if ((int) accumulatedRatio == raiser)
                    currentProgress = raiser * MessageHistorySearchProgressListener.PROGRESS_MAXIMUM_VALUE;
            }
            else
                lastHistoryProgress = tmpHistoryProgress;

            return (int) currentProgress;
        }

        /**
         * clear the values
         */
        void clear() {
            currentProgress = 0;
            lastHistoryProgress = 0;
        }
    }

    /**
     * Handles <code>PropertyChangeEvent</code> triggered from the modification of the isMessageHistoryEnabled property.
     */
    private class MessageHistoryPropertyChangeListener implements PropertyChangeListener {
        public void propertyChange(PropertyChangeEvent evt) {
            if (evt.getPropertyName().equals(MessageHistoryService.PNAME_IS_MESSAGE_HISTORY_ENABLED)) {
                String newPropertyValue = (String) evt.getNewValue();
                isHistoryLoggingEnabled = Boolean.parseBoolean(newPropertyValue);

                // If the message history is not enabled we stop here.
                if (isHistoryLoggingEnabled)
                    loadMessageHistoryService();
                else
                    stop(bundleContext);
            }
            else if (evt.getPropertyName().equals(MessageHistoryService.PNAME_IS_RECENT_MESSAGES_DISABLED)) {
                String newPropertyValue = (String) evt.getNewValue();
                boolean isDisabled = Boolean.parseBoolean(newPropertyValue);

                if (isDisabled) {
                    stopRecentMessages();
                }
                else if (isHistoryLoggingEnabled) {
                    loadRecentMessages();
                }
            }
        }
    }
}
