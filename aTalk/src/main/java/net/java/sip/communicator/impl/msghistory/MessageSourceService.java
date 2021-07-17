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

import net.java.sip.communicator.service.contactlist.MetaContact;
import net.java.sip.communicator.service.contactlist.event.MetaContactListAdapter;
import net.java.sip.communicator.service.contactlist.event.MetaContactRenamedEvent;
import net.java.sip.communicator.service.contactsource.*;
import net.java.sip.communicator.service.filehistory.FileRecord;
import net.java.sip.communicator.service.msghistory.MessageSourceContactPresenceStatus;
import net.java.sip.communicator.service.muc.ChatRoomPresenceStatus;
import net.java.sip.communicator.service.protocol.*;
import net.java.sip.communicator.service.protocol.event.*;

import org.apache.commons.lang3.StringUtils;
import org.atalk.android.R;
import org.atalk.android.aTalkApp;
import org.atalk.android.gui.chat.ChatMessage;
import org.atalk.persistance.DatabaseBackend;
import org.atalk.service.configuration.ConfigurationService;
import org.jetbrains.annotations.NotNull;

import java.beans.PropertyChangeEvent;
import java.io.IOException;
import java.util.*;

import androidx.annotation.NonNull;
import timber.log.Timber;

/**
 * The source contact service. This will show most recent messages.
 *
 * @author Damian Minkov
 * @author Eng Chong Meng
 */
public class MessageSourceService extends MetaContactListAdapter implements ContactSourceService,
        ContactPresenceStatusListener, ContactCapabilitiesListener, ProviderPresenceStatusListener,
        SubscriptionListener, LocalUserChatRoomPresenceListener, MessageListener,
        ChatRoomMessageListener, AdHocChatRoomMessageListener
{
    /* DB database column fields for call history */
    public static final String TABLE_NAME = "recentMessages";
    public static final String UUID = "uuid";    // unique identification for the recent message
    public static final String ACCOUNT_UID = "accountUid";  // account uid
    public static final String ENTITY_JID = "entityJid";    // contact Jid
    public static final String TIME_STAMP = "timeStamp";    // callEnd TimeStamp
    public static final String VERSION = "version";         // version

    /**
     * Whether to show recent messages in history or in contactList. By default we show it in contactList.
     */
    private static final String IN_HISTORY_PROPERTY = "msghistory.contactsrc.IN_HISTORY";
    /**
     * Property to control number of recent messages.
     */
    private static final String NUMBER_OF_RECENT_MSGS_PROP = "msghistory.contactsrc.MSG_NUMBER";
    /**
     * Property to control version of recent messages.
     */
    private static final String VER_OF_RECENT_MSGS_PROP = "msghistory.contactsrc.MSG_VER";
    /**
     * Property to control messages type. Can query for message sub type.
     */
    private static final String IS_MESSAGE_SUBTYPE_SMS_PROP = "msghistory.contactsrc.IS_SMS_ENABLED";

    /**
     * Sort database message records by TimeStamp in ASC
     */
    private static final String ORDER_ASC = MessageSourceService.TIME_STAMP + " ASC";

    /**
     * The maximum number of recent messages to store in the history, but will retrieve just <tt>numberOfMessages</tt>
     */
    private static final int NUMBER_OF_MSGS_IN_HISTORY = 100;

    /**
     * The current version of recent messages. When changed the recent messages are recreated.
     */
    private static String RECENT_MSGS_VER = "2";
    /**
     * List of recent messages.
     */
    private final List<ComparableEvtObj> recentMessages = new LinkedList<>();
    /**
     * The type of the source service, the place to be shown in the ui.
     */
    private int sourceServiceType = CONTACT_LIST_TYPE;
    /**
     * Number of messages to show.
     */
    private int numberOfMessages;
    /**
     * Date of the oldest shown message.
     */
    private Date oldestRecentMessage = null;
    /**
     * The last query created.
     */
    private MessageSourceContactQuery recentQuery = null;
    /**
     * The message subtype if any.
     */
    private boolean isSMSEnabled = false;
    /**
     * Message history service that has created us.
     */
    private MessageHistoryServiceImpl messageHistoryService;

    // SQLite database variables
    private SQLiteDatabase mDB;
    private ContentValues contentValues = new ContentValues();

    /**
     * Constructs MessageSourceService.
     */
    public MessageSourceService(MessageHistoryServiceImpl messageHistoryService)
    {
        this.messageHistoryService = messageHistoryService;
        mDB = DatabaseBackend.getWritableDB();

        ConfigurationService conf = MessageHistoryActivator.getConfigurationService();
        if (conf.getBoolean(IN_HISTORY_PROPERTY, false)) {
            sourceServiceType = HISTORY_TYPE;
        }

        numberOfMessages = conf.getInt(NUMBER_OF_RECENT_MSGS_PROP, numberOfMessages);
        isSMSEnabled = conf.getBoolean(IS_MESSAGE_SUBTYPE_SMS_PROP, isSMSEnabled);
        RECENT_MSGS_VER = conf.getString(VER_OF_RECENT_MSGS_PROP, RECENT_MSGS_VER);
        MessageSourceContactPresenceStatus.MSG_SRC_CONTACT_ONLINE.setStatusIcon(
                MessageHistoryActivator.getResources().getImageInBytes("sms_status_icon"));
    }

    @Override
    public void providerStatusChanged(ProviderPresenceStatusChangeEvent evt)
    {
        if (!evt.getNewStatus().isOnline() || evt.getOldStatus().isOnline())
            return;
        handleProviderAdded(evt.getProvider(), true);
    }

    @Override
    public void providerStatusMessageChanged(PropertyChangeEvent evt)
    {
    }

    /**
     * When a provider is added, do not block and start executing in new thread.
     *
     * @param provider ProtocolProviderService
     */
    public void handleProviderAdded(final ProtocolProviderService provider, final boolean isStatusChanged)
    {
        Timber.d("Handle new provider added and status changed to online: %s", provider.getAccountID().getUserID());
        new Thread(() -> handleProviderAddedInSeparateThread(provider, isStatusChanged)).start();
    }

    /**
     * When a provider is added. As searching can be slow especially when handling special type of
     * messages (with subType) this need to be run in new Thread.
     * cmeng - may not be true for SQLite database implementation
     *
     * @param provider ProtocolProviderService
     */
    private void handleProviderAddedInSeparateThread(ProtocolProviderService provider, boolean isStatusChanged)
    {
        // lets check if we have cached recent messages for this provider, and fire events if found and are newer
        synchronized (recentMessages) {
            List<ComparableEvtObj> cachedRecentMessages = getCachedRecentMessages(provider, isStatusChanged);
            if (cachedRecentMessages.isEmpty()) {
                // there is no cached history for this, let's check and load it not from cache, but do a local search
                Collection<EventObject> res = messageHistoryService.findRecentMessagesPerContact(numberOfMessages,
                        provider.getAccountID().getAccountUniqueID(), null, isSMSEnabled);

                List<ComparableEvtObj> newMsc = new ArrayList<>();
                processEventObjects(res, newMsc, isStatusChanged);
                addNewRecentMessages(newMsc);
                for (ComparableEvtObj msc : newMsc) {
                    saveRecentMessageToHistory(msc);
                }
            }
            else {
                addNewRecentMessages(cachedRecentMessages);
            }
        }
    }

    /**
     * A provider has been removed.
     *
     * @param provider the ProtocolProviderService that has been unregistered.
     */
    public void handleProviderRemoved(ProtocolProviderService provider)
    {
        // Remove the recent messages for this provider, and update with recent messages for the available providers
        synchronized (recentMessages) {
            if (provider != null) {
                List<ComparableEvtObj> removedItems = new ArrayList<>();
                for (ComparableEvtObj msc : recentMessages) {
                    if (msc.getProtocolProviderService().equals(provider))
                        removedItems.add(msc);
                }
                recentMessages.removeAll(removedItems);
                if (!recentMessages.isEmpty())
                    oldestRecentMessage = recentMessages.get(recentMessages.size() - 1).getTimestamp();
                else
                    oldestRecentMessage = null;

                if (recentQuery != null) {
                    for (ComparableEvtObj msc : removedItems) {
                        recentQuery.fireContactRemoved(msc);
                    }
                }
            }
            // handleProviderRemoved can be invoked due to stopped history service, if this is the
            // case we do not want to update messages
            if (!this.messageHistoryService.isHistoryLoggingEnabled())
                return;

            // lets do the same as we enable provider for all registered providers and finally fire events
            List<ComparableEvtObj> contactsToAdd = new ArrayList<>();
            for (ProtocolProviderService pps : messageHistoryService.getCurrentlyAvailableProviders()) {
                contactsToAdd.addAll(getCachedRecentMessages(pps, true));
            }
            addNewRecentMessages(contactsToAdd);
        }
    }

    /**
     * Updates contact source contacts with status.
     *
     * @param evt the ContactPresenceStatusChangeEvent describing the status
     */
    @Override
    public void contactPresenceStatusChanged(ContactPresenceStatusChangeEvent evt)
    {
        if (recentQuery == null)
            return;

        synchronized (recentMessages) {
            for (ComparableEvtObj msg : recentMessages) {
                if ((msg.getContact() != null)
                        && msg.getContact().equals(evt.getSourceContact())) {
                    recentQuery.updateContactStatus(msg, evt.getNewStatus());
                }
            }
        }
    }

    @Override
    public void localUserPresenceChanged(LocalUserChatRoomPresenceChangeEvent evt)
    {
        if (recentQuery == null)
            return;

        ComparableEvtObj srcContact = null;
        synchronized (recentMessages) {
            for (ComparableEvtObj msg : recentMessages) {
                if ((msg.getRoom() != null) && msg.getRoom().equals(evt.getChatRoom())) {
                    srcContact = msg;
                    break;
                }
            }
        }

        if (srcContact == null)
            return;

        String eventType = evt.getEventType();
        if (LocalUserChatRoomPresenceChangeEvent.LOCAL_USER_JOINED.equals(eventType)) {
            recentQuery.updateContactStatus(srcContact, ChatRoomPresenceStatus.CHAT_ROOM_ONLINE);
        }
        else if ((LocalUserChatRoomPresenceChangeEvent.LOCAL_USER_LEFT.equals(eventType)
                || LocalUserChatRoomPresenceChangeEvent.LOCAL_USER_KICKED.equals(eventType)
                || LocalUserChatRoomPresenceChangeEvent.LOCAL_USER_DROPPED.equals(eventType))) {
            recentQuery.updateContactStatus(srcContact, ChatRoomPresenceStatus.CHAT_ROOM_OFFLINE);
        }
    }

    /**
     * Updates the contact sources in the recent query if any. Done here in order to sync with
     * recentMessages instance, and to check for already existing instances of contact sources.
     * Normally called from the query.
     */
    public void updateRecentMessages()
    {
        if (recentQuery == null)
            return;

        synchronized (recentMessages) {
            List<SourceContact> currentContactsInQuery = recentQuery.getQueryResults();

            for (ComparableEvtObj evtObj : recentMessages) {
                // the contains will use the correct equals method of the object evtObj
                if (!currentContactsInQuery.contains(evtObj)) {
                    MessageSourceContact newSourceContact
                            = new MessageSourceContact(evtObj.getEventObject(), MessageSourceService.this);
                    newSourceContact.initDetails(evtObj.getEventObject());
                    recentQuery.addQueryResult(newSourceContact);
                }
            }
        }
    }

    /**
     * Searches for entries in cached recent messages in history.
     *
     * @param provider the provider which contact messages we will search e.g Jabber:abc123@atalk.org
     * @param isStatusChanged is the search because of status changed
     * @return entries in cached recent messages in history.
     */
    private List<ComparableEvtObj> getCachedRecentMessages(ProtocolProviderService provider, boolean isStatusChanged)
    {
        Collection<EventObject> res;
        String providerID = provider.getAccountID().getAccountUniqueID();
        List<String> recentMessagesContactIDs = getRecentContactIDs(providerID,
                (recentMessages.size() < numberOfMessages) ? null : oldestRecentMessage);

        List<ComparableEvtObj> cachedRecentMessages = new ArrayList<>();
        for (String contactID : recentMessagesContactIDs) {
            try {
                res = messageHistoryService.findRecentMessagesPerContact(numberOfMessages,
                        providerID, contactID, isSMSEnabled);
                processEventObjects(res, cachedRecentMessages, isStatusChanged);
            } catch (Exception e) { // IndexOutOfBound
                Timber.w("Get cache recent message exception for: %s => %s", contactID, e.getMessage());
            }
        }
        return cachedRecentMessages;
    }

    /**
     * Process list of event objects. Checks whether message source contact already exist for this
     * event object, if yes just update it with the new values (not sure whether we should do
     * this, as it may bring old messages) and if status of provider is changed, init its
     * details, updates its capabilities. It still adds the found messages source contact to the
     * list of the new contacts, as later we will detect this and fire update event. If nothing
     * found a new contact is created.
     *
     * @param res list of lately fetched event
     * @param cachedRecentMessages list of newly created source contacts or already existed but updated with
     * corresponding event object
     * @param isStatusChanged whether provider status changed and we are processing
     */
    private void processEventObjects(Collection<EventObject> res,
            List<ComparableEvtObj> cachedRecentMessages, boolean isStatusChanged)
    {
        for (EventObject eventObject : res) {
            // skip process any non-message FileRecord object
            if (eventObject instanceof FileRecord)
                continue;

            ComparableEvtObj oldMsg = findRecentMessage(eventObject, recentMessages);
            if (oldMsg != null) {
                oldMsg.update(eventObject); // update

                if (isStatusChanged && recentQuery != null)
                    recentQuery.updateCapabilities(oldMsg, eventObject);

                // we still add it to cachedRecentMessages later we will find it is duplicate and
                // will fire update event
                if (!cachedRecentMessages.contains(oldMsg))
                    cachedRecentMessages.add(oldMsg);
                continue;
            }

            oldMsg = findRecentMessage(eventObject, cachedRecentMessages);
            if (oldMsg == null) {
                oldMsg = new ComparableEvtObj(eventObject);

                if (isStatusChanged && recentQuery != null)
                    recentQuery.updateCapabilities(oldMsg, eventObject);
                cachedRecentMessages.add(oldMsg);
            }
        }
    }

    /**
     * Access for source contacts impl.
     *
     * @return isSMSEnabled
     */
    boolean isSMSEnabled()
    {
        return isSMSEnabled;
    }

    /**
     * Add the ComparableEvtObj, newly added will fire new, for existing fire update and when
     * trimming the list to desired length fire remove for those that were removed
     *
     * @param contactsToAdd List of contacts to add
     */
    private void addNewRecentMessages(List<ComparableEvtObj> contactsToAdd)
    {
        // now find object to fire new, and object to fire remove let us find duplicates and fire update
        List<ComparableEvtObj> duplicates = new ArrayList<>();
        for (ComparableEvtObj msgToAdd : contactsToAdd) {
            if (recentMessages.contains(msgToAdd)) {
                duplicates.add(msgToAdd);
                // save update
                updateRecentMessageToHistory(msgToAdd);
            }
        }
        recentMessages.removeAll(duplicates);
        // now contacts to add has no duplicates, add them all
        boolean changed = recentMessages.addAll(contactsToAdd);
        if (changed) {
            Collections.sort(recentMessages);
            if (recentQuery != null) {
                for (ComparableEvtObj obj : duplicates)
                    recentQuery.updateContact(obj, obj.getEventObject());
            }
        }
        if (!recentMessages.isEmpty())
            oldestRecentMessage = recentMessages.get(recentMessages.size() - 1).getTimestamp();

        // trim
        List<ComparableEvtObj> removedItems = null;
        if (recentMessages.size() > numberOfMessages) {
            removedItems = new ArrayList<>(recentMessages.subList(numberOfMessages, recentMessages.size()));
            recentMessages.removeAll(removedItems);
        }
        if (recentQuery != null) {
            // now fire, removed for all that were in the list and now are removed after trim
            if (removedItems != null) {
                for (ComparableEvtObj msc : removedItems) {
                    if (!contactsToAdd.contains(msc))
                        recentQuery.fireContactRemoved(msc);
                }
            }
            // fire new for all that were added, and not removed after trim
            for (ComparableEvtObj msc : contactsToAdd) {
                if ((removedItems == null || !removedItems.contains(msc))
                        && !duplicates.contains(msc)) {
                    MessageSourceContact newSourceContact = new MessageSourceContact(
                            msc.getEventObject(), MessageSourceService.this);
                    newSourceContact.initDetails(msc.getEventObject());
                    recentQuery.addQueryResult(newSourceContact);
                }
            }
            // if recent messages were changed, indexes have change lets fire event for the last
            // element which will reorder the whole group if needed.
            if (changed)
                recentQuery.fireContactChanged(recentMessages.get(recentMessages.size() - 1));
        }
    }

    /**
     * Searches for contact ids in history of recent messages on and after the startDate
     *
     * @param provider Account Uid
     * @param startDate start date to search; can be null if not applicable
     * @return List of found entityJid
     */
    private List<String> getRecentContactIDs(String provider, Date startDate)
    {
        List<String> contacts = new ArrayList<>();
        List<String> argList = new ArrayList<>();

        String[] columns = {ENTITY_JID};
        String whereCondition = ACCOUNT_UID + "=?";
        argList.add(provider);

        // add startDate if not null as additional search condition
        if (startDate != null) {
            whereCondition += " AND " + TIME_STAMP + ">=?";
            argList.add(String.valueOf(startDate.getTime()));
        }
        String[] args = argList.toArray(new String[0]);

        // Retrieve all the entityJid for the given provider and startDate
        Cursor cursor = mDB.query(MessageSourceService.TABLE_NAME, columns,
                whereCondition, args, null, null, null);

        while (cursor.moveToNext()) {
            contacts.add(cursor.getString(0));
        }
        cursor.close();
        return contacts;
    }

    /**
     * Adds recent message in history database;
     * Remove excess of old records (+10) each time if db exceed NUMBER_OF_MSGS_IN_HISTORY.
     */
    private void saveRecentMessageToHistory(ComparableEvtObj msc)
    {
        // Keep the record size to within the specified NUMBER_OF_MSGS_IN_HISTORY
        Cursor cursor = mDB.query(MessageSourceService.TABLE_NAME, null, null, null,
                null, null, ORDER_ASC);
        int excess = cursor.getCount() - NUMBER_OF_MSGS_IN_HISTORY;
        if (excess > 0) {
            cursor.move(excess + 12);
            String[] args = {cursor.getString(cursor.getColumnIndex(TIME_STAMP))};
            int count = mDB.delete(MessageSourceService.TABLE_NAME, TIME_STAMP + "<?", args);
            Timber.d("No of recent old messages deleted : %s", count);
        }
        cursor.close();

        Date date = new Date();
        String uuid = String.valueOf(date.getTime()) + Math.abs(date.hashCode());
        String accountUid = msc.getProtocolProviderService().getAccountID().getAccountUniqueID();

        contentValues.clear();
        contentValues.put(UUID, uuid);
        contentValues.put(ACCOUNT_UID, accountUid);
        contentValues.put(ENTITY_JID, msc.getContactAddress());
        contentValues.put(TIME_STAMP, msc.getTimestamp().getTime());
        contentValues.put(VERSION, RECENT_MSGS_VER);

        mDB.insert(MessageSourceService.TABLE_NAME, null, contentValues);
    }

    /**
     * Updates recent message in history.
     */
    private void updateRecentMessageToHistory(ComparableEvtObj msg)
    {
        contentValues.clear();
        contentValues.put(TIME_STAMP, msg.getTimestamp().getTime());
        contentValues.put(VERSION, RECENT_MSGS_VER);

        String accountUid = msg.getProtocolProviderService().getAccountID().getAccountUniqueID();
        String entityJid = msg.getContactAddress();
        String[] args = {accountUid, entityJid};

        mDB.update(MessageSourceService.TABLE_NAME, contentValues, ACCOUNT_UID + "=? AND " + ENTITY_JID + "=?", args);
    }

    // ================ Message events handlers =======================

    @Override
    public void messageReceived(MessageReceivedEvent evt)
    {
        if (isSMSEnabled && (evt.getEventType() != ChatMessage.MESSAGE_SMS_IN)) {
            return;
        }
        handle(evt, evt.getSourceContact().getProtocolProvider(), evt.getSourceContact().getAddress());
    }

    @Override
    public void messageDelivered(MessageDeliveredEvent evt)
    {
        if (isSMSEnabled && !evt.isSmsMessage())
            return;

        handle(evt, evt.getDestinationContact().getProtocolProvider(), evt.getDestinationContact().getAddress());
    }

    /**
     * @param evt the <tt>MessageFailedEvent</tt>
     */
    @Override
    public void messageDeliveryFailed(MessageDeliveryFailedEvent evt)
    {
    }

    @Override
    public void messageReceived(ChatRoomMessageReceivedEvent evt)
    {
        if (isSMSEnabled)
            return;

        // ignore non conversation messages
        if (evt.getEventType() != ChatMessage.MESSAGE_IN)
            return;

        handle(evt, evt.getSourceChatRoom().getParentProvider(), evt.getSourceChatRoom().getName());
    }

    @Override
    public void messageDelivered(ChatRoomMessageDeliveredEvent evt)
    {
        if (isSMSEnabled)
            return;

        handle(evt, evt.getSourceChatRoom().getParentProvider(), evt.getSourceChatRoom().getName());
    }

    /**
     * @param evt the <tt>ChatRoomMessageDeliveryFailedEvent</tt>
     */
    @Override
    public void messageDeliveryFailed(ChatRoomMessageDeliveryFailedEvent evt)
    {
    }

    @Override
    public void messageReceived(AdHocChatRoomMessageReceivedEvent evt)
    {
        // TODO
    }

    @Override
    public void messageDelivered(AdHocChatRoomMessageDeliveredEvent evt)
    {
        // TODO
    }

    /**
     * @param evt the <tt>AdHocChatRoomMessageDeliveryFailedEvent</tt>
     */
    @Override
    public void messageDeliveryFailed(AdHocChatRoomMessageDeliveryFailedEvent evt)
    {
    }

    @Override
    public void subscriptionCreated(SubscriptionEvent evt)
    {
    }

    @Override
    public void subscriptionFailed(SubscriptionEvent evt)
    {
    }

    @Override
    public void subscriptionRemoved(SubscriptionEvent evt)
    {
    }

    @Override
    public void subscriptionMoved(SubscriptionMovedEvent evt)
    {
    }

    @Override
    public void subscriptionResolved(SubscriptionEvent evt)
    {
    }

    /**
     * Handles new events.
     *
     * @param obj the event object
     * @param provider the provider
     * @param id the id of the source of the event
     */
    private void handle(EventObject obj, ProtocolProviderService provider, String id)
    {
        // check if provider - contact exist update message content
        synchronized (recentMessages) {
            ComparableEvtObj existingMsc = null;
            for (ComparableEvtObj msc : recentMessages) {
                if (msc.getProtocolProviderService().equals(provider) && msc.getContactAddress().equals(id)) {
                    msc.update(obj);
                    updateRecentMessageToHistory(msc);
                    existingMsc = msc;
                }
            }

            if (existingMsc != null) {
                Collections.sort(recentMessages);
                oldestRecentMessage = recentMessages.get(recentMessages.size() - 1).getTimestamp();

                if (recentQuery != null) {
                    recentQuery.updateContact(existingMsc, existingMsc.getEventObject());
                    recentQuery.fireContactChanged(existingMsc);
                }
                return;
            }

            // if missing create source contact and update recent messages, trim and sort
            MessageSourceContact newSourceContact = new MessageSourceContact(obj, MessageSourceService.this);
            newSourceContact.initDetails(obj);
            // we have already checked for duplicate
            ComparableEvtObj newMsg = new ComparableEvtObj(obj);
            recentMessages.add(newMsg);

            Collections.sort(recentMessages);
            oldestRecentMessage = recentMessages.get(recentMessages.size() - 1).getTimestamp();

            // trim
            List<ComparableEvtObj> removedItems = null;
            if (recentMessages.size() > numberOfMessages) {
                removedItems = new ArrayList<>(recentMessages.subList(numberOfMessages, recentMessages.size()));
                recentMessages.removeAll(removedItems);
            }
            // save
            saveRecentMessageToHistory(newMsg);

            // no query nothing to fire
            if (recentQuery == null)
                return;

            // now fire
            if (removedItems != null) {
                for (ComparableEvtObj msc : removedItems) {
                    recentQuery.fireContactRemoved(msc);
                }
            }
            recentQuery.addQueryResult(newSourceContact);
        }
    }

    /**
     * If a contact is renamed update the locally stored message if any.
     *
     * @param evt the <tt>ContactPropertyChangeEvent</tt> containing the source
     */
    @Override
    public void contactModified(ContactPropertyChangeEvent evt)
    {
        if (!evt.getPropertyName().equals(ContactPropertyChangeEvent.PROPERTY_DISPLAY_NAME))
            return;

        Contact contact = evt.getSourceContact();
        if (contact == null)
            return;

        for (ComparableEvtObj msc : recentMessages) {
            if (contact.equals(msc.getContact())) {
                if (recentQuery != null)
                    recentQuery.updateContactDisplayName(msc, contact.getDisplayName());
                return;
            }
        }
    }

    /**
     * Indicates that a MetaContact has been modified.
     *
     * @param evt the MetaContactListEvent containing the corresponding contact
     */
    public void metaContactRenamed(MetaContactRenamedEvent evt)
    {
        for (ComparableEvtObj msc : recentMessages) {
            if (evt.getSourceMetaContact().containsContact(msc.getContact())) {
                if (recentQuery != null)
                    recentQuery.updateContactDisplayName(msc, evt.getNewDisplayName());
            }
        }
    }

    @Override
    public void supportedOperationSetsChanged(ContactCapabilitiesEvent event)
    {
        Contact contact = event.getSourceContact();
        if (contact == null)
            return;

        for (ComparableEvtObj msc : recentMessages) {
            if (contact.equals(msc.getContact())) {
                if (recentQuery != null)
                    recentQuery.updateCapabilities(msc, contact);
                return;
            }
        }
    }


    /**
     * Tries to match the event object to already existing ComparableEvtObj in the supplied list.
     *
     * @param obj the object that we will try to match.
     * @param list the list we will search in.
     * @return the found ComparableEvtObj
     */
    private static ComparableEvtObj findRecentMessage(EventObject obj,
            List<ComparableEvtObj> list)
    {
        Contact contact = null;
        ChatRoom chatRoom = null;

        if (obj instanceof MessageDeliveredEvent) {
            contact = ((MessageDeliveredEvent) obj).getDestinationContact();
        }
        else if (obj instanceof MessageReceivedEvent) {
            contact = ((MessageReceivedEvent) obj).getSourceContact();
        }
        else if (obj instanceof ChatRoomMessageDeliveredEvent) {
            chatRoom = ((ChatRoomMessageDeliveredEvent) obj).getSourceChatRoom();
        }
        else if (obj instanceof ChatRoomMessageReceivedEvent) {
            chatRoom = ((ChatRoomMessageReceivedEvent) obj).getSourceChatRoom();
        }

        for (ComparableEvtObj evt : list) {
            if ((contact != null && contact.equals(evt.getContact()))
                    || (chatRoom != null && chatRoom.equals(evt.getRoom())))
                return evt;
        }
        return null;
    }

    /**
     * Returns the display name of this contact source.
     *
     * @return the display name of this contact source
     */
    @Override
    public String getDisplayName()
    {
        return aTalkApp.getResString(R.string.service_gui_RECENT_MESSAGES);
    }

    /**
     * Returns default type to indicate that this contact source can be queried by default filters.
     *
     * @return the type of this contact source
     */
    @Override
    public int getType()
    {
        return sourceServiceType;
    }

    /**
     * Returns the index of the contact source in the result list.
     *
     * @return the index of the contact source in the result list
     */
    @Override
    public int getIndex()
    {
        return 0;
    }

    /**
     * Returns the index of the source contact, in the list of recent messages.
     *
     * @param messageSourceContact search item
     * @return index of recentMessages containing the messageSourceContact
     */
    int getIndex(MessageSourceContact messageSourceContact)
    {
        synchronized (recentMessages) {
            for (int i = 0; i < recentMessages.size(); i++)
                if (recentMessages.get(i).getContact().equals(messageSourceContact.getContact()))
                    return i;
            return -1;
        }
    }

    /**
     * Creates query for the given <tt>searchString</tt>.
     *
     * @param queryString the string to search for
     * @return the created query
     */
    @Override
    public ContactQuery createContactQuery(String queryString)
    {
        recentQuery = (MessageSourceContactQuery) createContactQuery(queryString, numberOfMessages);
        return recentQuery;
    }

    /**
     * Creates query for the given <tt>searchString</tt>.
     *
     * @param queryString the string to search for
     * @param contactCount the maximum count of result contacts
     * @return the created query
     */
    @Override
    public ContactQuery createContactQuery(String queryString, int contactCount)
    {
        if(StringUtils.isNotEmpty(queryString))
            return null;
        recentQuery = new MessageSourceContactQuery(MessageSourceService.this);
        return recentQuery;
    }

    /**
     * Object used to cache recent messages.
     */
    private class ComparableEvtObj implements Comparable<ComparableEvtObj>
    {
        private EventObject eventObject;

        /**
         * The protocol provider.
         */
        private ProtocolProviderService ppService = null;

        /**
         * The address.
         */
        private String address = null;

        /**
         * The timestamp.
         */
        private Date timestamp = null;

        /**
         * The contact instance.
         */
        private Contact contact = null;

        /**
         * The room instance.
         */
        private ChatRoom room = null;

        /**
         * Constructs.
         *
         * @param source used to extract initial values.
         */
        ComparableEvtObj(EventObject source)
        {
            update(source);
        }

        /**
         * Extract values from <tt>EventObject</tt>.
         *
         * @param source the eventObject to retrieve information
         */
        public void update(EventObject source)
        {
            this.eventObject = source;

            if (source instanceof MessageDeliveredEvent) {
                MessageDeliveredEvent evt = (MessageDeliveredEvent) source;

                this.contact = evt.getDestinationContact();
                this.address = contact.getAddress();
                this.ppService = contact.getProtocolProvider();
                this.timestamp = evt.getTimestamp();
            }
            else if (source instanceof MessageReceivedEvent) {
                MessageReceivedEvent evt = (MessageReceivedEvent) source;

                this.contact = evt.getSourceContact();
                this.address = contact.getAddress();
                this.ppService = contact.getProtocolProvider();
                this.timestamp = evt.getTimestamp();
            }
            else if (source instanceof ChatRoomMessageDeliveredEvent) {
                ChatRoomMessageDeliveredEvent evt = (ChatRoomMessageDeliveredEvent) source;

                this.room = evt.getSourceChatRoom();
                this.address = room.getName();
                this.ppService = room.getParentProvider();
                this.timestamp = evt.getTimestamp();
            }
            else if (source instanceof ChatRoomMessageReceivedEvent) {
                ChatRoomMessageReceivedEvent evt = (ChatRoomMessageReceivedEvent) source;

                this.room = evt.getSourceChatRoom();
                this.address = room.getName();
                this.ppService = room.getParentProvider();
                this.timestamp = evt.getTimestamp();
            }
        }

        @NotNull
        @Override
        public String toString()
        {
            return "ComparableEvtObj{" + "address='" + address + '\'' + "," + " ppService=" + ppService + '}';
        }

        /**
         * The timestamp of the message.
         *
         * @return the timestamp of the message.
         */
        public Date getTimestamp()
        {
            return timestamp;
        }

        /**
         * The contact.
         *
         * @return the contact.
         */
        public Contact getContact()
        {
            return contact;
        }

        /**
         * The room.
         *
         * @return the room.
         */
        public ChatRoom getRoom()
        {
            return room;
        }

        /**
         * The protocol provider.
         *
         * @return the protocol provider.
         */
        public ProtocolProviderService getProtocolProviderService()
        {
            return ppService;
        }

        /**
         * The address.
         *
         * @return the address.
         */
        public String getContactAddress()
        {
            return this.address;
        }

        /**
         * The event object.
         *
         * @return the event object.
         */
        public EventObject getEventObject()
        {
            return eventObject;
        }

        /**
         * Compares two ComparableEvtObj.
         *
         * @param o the object to compare with
         * @return 0, less than zero, greater than zero, if equals, less or greater.
         */
        @Override
        public int compareTo(@NonNull ComparableEvtObj o)
        {
            if (o.getTimestamp() == null)
                return 1;

            return o.getTimestamp().compareTo(getTimestamp());
        }

        /**
         * Checks if equals, and if this event object is used to create a MessageSourceContact, if
         * the supplied <tt>Object</tt> is instance of MessageSourceContact.
         *
         * @param o the object to check.
         * @return <tt>true</tt> if equals.
         */
        @Override
        public boolean equals(Object o)
        {
            if (this == o)
                return true;
            if (o == null || (!(o instanceof MessageSourceContact) && getClass() != o.getClass()))
                return false;

            if (o instanceof ComparableEvtObj) {
                ComparableEvtObj that = (ComparableEvtObj) o;
                return (address.equals(that.address) && ppService.equals(that.ppService));
            }
            else if (o instanceof MessageSourceContact) {
                MessageSourceContact that = (MessageSourceContact) o;
                return (address.equals(that.getContactAddress()) && ppService.equals(that.getProtocolProviderService()));
            }
            else
                return false;
        }

        @Override
        public int hashCode()
        {
            int result = address.hashCode();
            result = 31 * result + ppService.hashCode();
            return result;
        }
    }

    /**
     * Permanently removes all locally stored message history, remove recent contacts.
     */
    public void eraseLocallyStoredHistory()
            throws IOException
    {
        List<ComparableEvtObj> toRemove;
        synchronized (recentMessages) {
            toRemove = new ArrayList<>(recentMessages);
            recentMessages.clear();
        }

        if (recentQuery != null) {
            for (ComparableEvtObj msc : toRemove) {
                recentQuery.fireContactRemoved(msc);
            }
        }
    }

    /**
     * Permanently removes locally stored message history for the metaContact, remove any recent contacts if any.
     */
    public void eraseLocallyStoredHistory(MetaContact metaContact, List<Date> mhsTimeStamp)
            throws IOException
    {
        List<ComparableEvtObj> toRemove;
        synchronized (recentMessages) {
            toRemove = new ArrayList<>();

            Iterator<Contact> contacts = metaContact.getContacts();
            while (contacts.hasNext()) {
                Contact contact = contacts.next();
                String id = contact.getAddress();
                ProtocolProviderService provider = contact.getProtocolProvider();

                if (mhsTimeStamp == null) {
                    for (ComparableEvtObj msc : recentMessages) {
                        if (msc.getProtocolProviderService().equals(provider)
                                && msc.getContactAddress().equals(id)) {
                            toRemove.add(msc);
                        }
                    }
                }
                else {
                    for (ComparableEvtObj msc : recentMessages) {
                        if (msc.getProtocolProviderService().equals(provider)
                                && msc.getContactAddress().equals(id)) {
                            toRemove.add(msc);
                        }
                    }
                }
            }
            recentMessages.removeAll(toRemove);
        }
        if (recentQuery != null) {
            for (ComparableEvtObj msc : toRemove) {
                recentQuery.fireContactRemoved(msc);
            }
        }
    }

    /**
     * Permanently removes locally stored message history for the chatRoom, remove any recent contacts if any.
     */
    public void eraseLocallyStoredHistory(ChatRoom room)
    {
        ComparableEvtObj toRemove = null;
        synchronized (recentMessages) {
            for (ComparableEvtObj msg : recentMessages) {
                if (msg.getRoom() != null && msg.getRoom().equals(room)) {
                    toRemove = msg;
                    break;
                }
            }
            if (toRemove == null)
                return;
            recentMessages.remove(toRemove);
        }
        if (recentQuery != null)
            recentQuery.fireContactRemoved(toRemove);
    }
}
