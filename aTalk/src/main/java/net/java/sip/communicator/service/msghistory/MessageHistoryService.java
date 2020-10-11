/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license.
 * See terms of license at gnu.org.
 */
package net.java.sip.communicator.service.msghistory;

import net.java.sip.communicator.service.contactlist.MetaContact;
import net.java.sip.communicator.service.msghistory.event.MessageHistorySearchProgressListener;
import net.java.sip.communicator.service.protocol.*;

import org.atalk.android.gui.chat.ChatSession;
import org.atalk.android.gui.chat.chatsession.ChatSessionRecord;

import java.util.*;

/**
 * The Message History Service stores messages exchanged through the various protocols
 *
 * @author Alexander Pelov
 * @author Damian Minkov
 * @author Eng Chong Meng
 */
public interface MessageHistoryService
{
    /**
     * Name of the property that indicates whether the logging of messages is enabled.
     */
    public static final String PNAME_IS_MESSAGE_HISTORY_ENABLED = "msghistory.IS_MESSAGE_HISTORY_ENABLED";

    /**
     * Name of the property that indicates whether the recent messages is enabled.
     */
    public static final String PNAME_IS_RECENT_MESSAGES_DISABLED = "msghistory.IS_RECENT_MESSAGES_DISABLED";

    /**
     * Name of the property that indicates whether the logging of messages is enabled.
     */
    public static final String PNAME_IS_MESSAGE_HISTORY_PER_CONTACT_ENABLED_PREFIX = "msghistory.contact";

    /**
     * Returns all the messages exchanged by all the contacts in the supplied metaContact after the given date
     *
     * @param contact MetaContact
     * @param startDate Date the start date of the conversations
     * @return Collection of MessageReceivedEvents or MessageDeliveredEvents
     */
    Collection<EventObject> findByStartDate(MetaContact contact, Date startDate);

    /**
     * Returns all the messages exchanged by all the contacts in the supplied metaContact before the given date
     *
     * @param contact MetaContact
     * @param endDate Date the end date of the conversations
     * @return Collection of MessageReceivedEvents or MessageDeliveredEvents
     */
    Collection<EventObject> findByEndDate(MetaContact contact, Date endDate);

    /**
     * Returns all the messages exchanged by all the contacts in the supplied metaContact between the given dates
     *
     * @param contact MetaContact
     * @param startDate Date the start date of the conversations
     * @param endDate Date the end date of the conversations
     * @return Collection of MessageReceivedEvents or MessageDeliveredEvents
     */
    Collection<EventObject> findByPeriod(MetaContact contact, Date startDate, Date endDate);

    /**
     * Returns all the messages exchanged by all the contacts in the supplied metaContact between
     * the given dates and having the given keywords
     *
     * @param contact MetaContact
     * @param startDate Date the start date of the conversations
     * @param endDate Date the end date of the conversations
     * @param keywords array of keywords
     * @return Collection of MessageReceivedEvents or MessageDeliveredEvents
     */
    Collection<EventObject> findByPeriod(MetaContact contact, Date startDate, Date endDate, String[] keywords);

    /**
     * Returns all the messages exchanged by all the contacts in the supplied metaContact between
     * the given dates and having the given keywords
     *
     * @param contact MetaContact
     * @param startDate Date the start date of the conversations
     * @param endDate Date the end date of the conversations
     * @param keywords array of keywords
     * @param caseSensitive is keywords search case sensitive
     * @return Collection of MessageReceivedEvents or MessageDeliveredEvents
     */
    Collection<EventObject> findByPeriod(MetaContact contact, Date startDate, Date endDate,
            String[] keywords, boolean caseSensitive);

    /**
     * Returns all the messages exchanged by all the contacts in the supplied metaContact having the given keyword
     *
     * @param contact MetaContact
     * @param keyword keyword
     * @return Collection of MessageReceivedEvents or MessageDeliveredEvents
     */
    Collection<EventObject> findByKeyword(MetaContact contact, String keyword);

    /**
     * Returns all the messages exchanged by all the contacts in the supplied metaContact having the given keyword
     *
     * @param contact MetaContact
     * @param keyword keyword
     * @param caseSensitive is keywords search case sensitive
     * @return Collection of MessageReceivedEvents or MessageDeliveredEvents
     */
    Collection<EventObject> findByKeyword(MetaContact contact, String keyword, boolean caseSensitive);

    /**
     * Returns all the messages exchanged by all the contacts in the supplied metaContact having the given keywords
     *
     * @param contact MetaContact
     * @param keywords keyword
     * @return Collection of MessageReceivedEvents or MessageDeliveredEvents
     */
    Collection<EventObject> findByKeywords(MetaContact contact, String[] keywords);

    /**
     * Returns all the messages exchanged by all the contacts in the supplied metaContact having the given keywords
     *
     * @param contact MetaContact
     * @param keywords keyword
     * @param caseSensitive is keywords search case sensitive
     * @return Collection of MessageReceivedEvents or MessageDeliveredEvents
     */
    Collection<EventObject> findByKeywords(MetaContact contact, String[] keywords, boolean caseSensitive);

    /**
     * Returns the supplied number of recent messages exchanged by all the contacts in the supplied metaContact
     *
     * @param contact MetaContact
     * @param count messages count
     * @return Collection of MessageReceivedEvents or MessageDeliveredEvents
     */
    Collection<EventObject> findLast(MetaContact contact, int count);

    /**
     * Returns the supplied number of recent messages after the given date exchanged by all the
     * contacts in the supplied metaContact
     *
     * @param contact MetaContact
     * @param date messages after date
     * @param count messages count
     * @return Collection of MessageReceivedEvents or MessageDeliveredEvents
     */
    Collection<EventObject> findFirstMessagesAfter(MetaContact contact, Date date, int count);

    /**
     * Returns the supplied number of recent messages before the given date exchanged by all the
     * contacts in the supplied metaContact
     *
     * @param contact MetaContact
     * @param date messages before date
     * @param count messages count
     * @return Collection of MessageReceivedEvents or MessageDeliveredEvents
     */
    Collection<EventObject> findLastMessagesBefore(MetaContact contact, Date date, int count);

    /**
     * Returns all the chat session record created by the supplied accountUid before the given date
     *
     * @param accountUid Account Uid
     * @param endDate end date for the session creation
     * @return Collection of ChatSessionRecord
     */
    Collection<ChatSessionRecord> findSessionByEndDate(String accountUid, Date endDate);

    /**
     * Returns the messages for the recently contacted <tt>count</tt> contacts.
     *
     * @param count contacts count
     * @param providerToFilter can be filtered by provider e.g. Jabber:abc123@atalk.org,
     * or <tt>null</tt> to search for all  providers
     * @param contactToFilter can be filtered by contact e.g. xyx123@atalk.org,
     * or <tt>null</tt> to search for all contacts
     * @return Collection of MessageReceivedEvents or MessageDeliveredEvents
     */
    Collection<EventObject> findRecentMessagesPerContact(int count, String providerToFilter,
            String contactToFilter, boolean isSMSEnabled);

    /**
     * Adding progress listener for monitoring progress of search process
     *
     * @param listener HistorySearchProgressListener
     */
    void addSearchProgressListener(MessageHistorySearchProgressListener listener);

    /**
     * Removing progress listener
     *
     * @param listener HistorySearchProgressListener
     */
    void removeSearchProgressListener(MessageHistorySearchProgressListener listener);

    /**
     * Returns all the messages exchanged in the supplied chat room after the given date
     *
     * @param room The chat room
     * @param startDate Date the start date of the conversations
     * @return Collection of MessageReceivedEvents or MessageDeliveredEvents
     */
    Collection<EventObject> findByStartDate(ChatRoom room, Date startDate);

    /**
     * Returns all the messages exchanged in the supplied chat room before the given date
     *
     * @param room The chat room
     * @param endDate Date the end date of the conversations
     * @return Collection of MessageReceivedEvents or MessageDeliveredEvents
     */
    Collection<EventObject> findByEndDate(ChatRoom room, Date endDate);

    /**
     * Returns all the messages exchanged in the supplied chat room between the given dates
     *
     * @param room The chat room
     * @param startDate Date the start date of the conversations
     * @param endDate Date the end date of the conversations
     * @return Collection of MessageReceivedEvents or MessageDeliveredEvents
     */
    Collection<EventObject> findByPeriod(ChatRoom room, Date startDate, Date endDate);

    /**
     * Returns all the messages exchanged in the supplied chat room between the given dates and
     * having the given keywords
     *
     * @param room The chat room
     * @param startDate Date the start date of the conversations
     * @param endDate Date the end date of the conversations
     * @param keywords array of keywords
     * @return Collection of MessageReceivedEvents or MessageDeliveredEvents
     */
    Collection<EventObject> findByPeriod(ChatRoom room, Date startDate, Date endDate, String[] keywords);

    /**
     * Returns all the messages exchanged in the supplied chat room between the given dates and
     * having the given keywords
     *
     * @param room The chat room
     * @param startDate Date the start date of the conversations
     * @param endDate Date the end date of the conversations
     * @param keywords array of keywords
     * @param caseSensitive is keywords search case sensitive
     * @return Collection of MessageReceivedEvents or MessageDeliveredEvents
     */
    Collection<EventObject> findByPeriod(ChatRoom room, Date startDate, Date endDate,
            String[] keywords, boolean caseSensitive);

    /**
     * Returns all the messages exchanged in the supplied room having the given keyword
     *
     * @param room The Chat room
     * @param keyword keyword
     * @return Collection of MessageReceivedEvents or MessageDeliveredEvents
     */
    Collection<EventObject> findByKeyword(ChatRoom room, String keyword);

    /**
     * Returns all the messages exchanged in the supplied chat room having the given keyword
     *
     * @param room The chat room
     * @param keyword keyword
     * @param caseSensitive is keywords search case sensitive
     * @return Collection of MessageReceivedEvents or MessageDeliveredEvents
     */
    Collection<EventObject> findByKeyword(ChatRoom room, String keyword, boolean caseSensitive);

    /**
     * Returns all the messages exchanged in the supplied chat room having the given keywords
     *
     * @param room The chat room
     * @param keywords keyword
     * @return Collection of MessageReceivedEvents or MessageDeliveredEvents
     */
    Collection<EventObject> findByKeywords(ChatRoom room, String[] keywords);

    /**
     * Returns all the messages exchanged in the supplied chat room having the given keywords
     *
     * @param room The chat room
     * @param keywords keyword
     * @param caseSensitive is keywords search case sensitive
     * @return Collection of MessageReceivedEvents or MessageDeliveredEvents
     */
    Collection<EventObject> findByKeywords(ChatRoom room, String[] keywords, boolean caseSensitive);

    /**
     * Returns the supplied number of recent messages exchanged in the supplied chat room
     *
     * @param room The chat room
     * @param count messages count
     * @return Collection of MessageReceivedEvents or MessageDeliveredEvents
     */
    Collection<EventObject> findLast(ChatRoom room, int count);

    /**
     * Returns the supplied number of recent messages after the given date exchanged in the supplied chat room
     *
     * @param room The chat room
     * @param date messages after date
     * @param count messages count
     * @return Collection of MessageReceivedEvents or MessageDeliveredEvents
     */
    Collection<EventObject> findFirstMessagesAfter(ChatRoom room, Date date, int count);

    /**
     * Returns the supplied number of recent messages before the given date exchanged in the supplied chat room
     *
     * @param room The chat room
     * @param date messages before date
     * @param count messages count
     * @return Collection of MessageReceivedEvents or MessageDeliveredEvents
     */
    Collection<EventObject> findLastMessagesBefore(ChatRoom room, Date date, int count);

    /**
     * Permanently removes all locally stored message history for the specified chatMode.
     * @param chatMode i.e. ChatSession.MODE_SINGLE or ChatSession.MODE_MULTI
     */
    void eraseLocallyStoredChatHistory(int chatMode);

    /**
     * Permanently removes locally stored message history for the metaContact.
     */
    void eraseLocallyStoredChatHistory(MetaContact metaContact, List<String> messageUUIDs);

    /**
     * Permanently removes locally stored message history for the chatRoom.
     */
    void eraseLocallyStoredChatHistory(ChatRoom room, List<String> messageUUIDs);

    /**
     * Fetch the attached file paths for all the messages of the specified descriptor.
     */
    List<String> getLocallyStoredFilePath(Object descriptor);

    /**
     * Fetch the attached file paths for all locally saved messages.
     */
    List<String> getLocallyStoredFilePath();

    /**
     * Returns <code>true</code> if the "IS_MESSAGE_HISTORY_ENABLED" property is true, otherwise -
     * returns <code>false</code>. Indicates to the user interface whether the history logging is enabled.
     *
     * @return <code>true</code> if the "IS_MESSAGE_HISTORY_ENABLED" property is true, otherwise -
     * returns <code>false</code>.
     */
    boolean isHistoryLoggingEnabled();

    /**
     * Updates the "isHistoryLoggingEnabled" property through the <tt>ConfigurationService</tt>.
     *
     * @param isEnabled indicates if the history logging is enabled.
     */
    void setHistoryLoggingEnabled(boolean isEnabled);

    /**
     * Returns <code>true</code> if the "IS_MESSAGE_HISTORY_ENABLED" property is true for the
     * <tt>id</tt>, otherwise - returns <code>false</code>. Indicates to the user interface
     * whether the history logging is enabled for the supplied id (id for metaContact or for chat room).
     *
     * @return <code>true</code> if the "IS_MESSAGE_HISTORY_ENABLED" property is true for the
     * <tt>id</tt>, otherwise - returns <code>false</code>.
     */
    boolean isHistoryLoggingEnabled(String id);

    /**
     * Updates the "isHistoryLoggingEnabled" property through the <tt>ConfigurationService</tt> for the contact.
     *
     * @param isEnabled indicates if the history logging is enabled for the contact.
     */
    void setHistoryLoggingEnabled(boolean isEnabled, String id);

    /**
     * Returns the sessionUuid by specified Object
     *
     * @param contact The chat Contact
     * @return sessionUuid - created if not exist
     */
    String getSessionUuidByJid(Contact contact);

    String getSessionUuidByJid(ChatRoom chatRoom);

    String getSessionUuidByJid(AccountID accountID, String entityJid);

    /**
     * Get the chatSession persistent chatType
     *
     * @param chatSession the chat session for which to fetch from
     * @return the chatType
     */
    int getSessionChatType(ChatSession chatSession);

    /**
     * Store the chatSession to user selected chatType
     *
     * @param chatSession the chat session for which to apply to
     * @param chatType the chatType to store
     * @return number of columns affected
     */
    int setSessionChatType(ChatSession chatSession, int chatType);
}

