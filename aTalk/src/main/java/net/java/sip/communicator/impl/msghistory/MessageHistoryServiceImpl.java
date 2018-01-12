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

import net.java.sip.communicator.service.contactlist.*;
import net.java.sip.communicator.service.contactsource.ContactSourceService;
import net.java.sip.communicator.service.filehistory.FileRecord;
import net.java.sip.communicator.service.history.*;
import net.java.sip.communicator.service.history.event.*;
import net.java.sip.communicator.service.msghistory.*;
import net.java.sip.communicator.service.msghistory.event.MessageHistorySearchProgressListener;
import net.java.sip.communicator.service.protocol.*;
import net.java.sip.communicator.service.protocol.event.*;
import net.java.sip.communicator.service.protocol.globalstatus.GlobalStatusEnum;
import net.java.sip.communicator.util.*;
import net.java.sip.communicator.util.account.AccountUtils;

import org.atalk.android.gui.chat.*;
import org.atalk.persistance.DatabaseBackend;
import org.atalk.service.configuration.ConfigurationService;
import org.atalk.util.StringUtils;
import org.jxmpp.util.XmppStringUtils;
import org.osgi.framework.*;

import java.beans.*;
import java.io.*;
import java.util.*;

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
		LocalUserAdHocChatRoomPresenceListener
{
	/**
	 * The logger for this class.
	 */
	private static Logger logger = Logger.getLogger(MessageHistoryServiceImpl.class);
	/**
	 * Subtype sms to mark sms messages.
	 */
	private static final String MSG_SUBTYPE_SMS = "sms";
	/**
	 * Sort database message records by TimeStamp in ASC or DESC
	 */
	private static final String ORDER_ASC = ChatMessage.TIME_STAMP + " ASC";
	private static final String ORDER_DESC = ChatMessage.TIME_STAMP + " DESC";
	/**
	 * Indicates if history logging is enabled.
	 */
	private static boolean isHistoryLoggingEnabled;
	/**
	 * The BundleContext that we got from the OSGI bus.
	 */
	private BundleContext bundleContext = null;
	private HistoryService historyService = null;
	private final Object syncRoot_HistoryService = new Object();
	private final Hashtable<MessageHistorySearchProgressListener, HistorySearchProgressListener>
			progressListeners = new Hashtable<>();
	private ConfigurationService configService;
	private MessageHistoryPropertyChangeListener msgHistoryPropListener;

	/**
	 * The message source service, can be null if not enabled.
	 */
	private MessageSourceService messageSourceService;

	/**
	 * The message source service registration.
	 */
	private ServiceRegistration messageSourceServiceReg = null;

	private SQLiteDatabase mDB;
	private ContentValues contentValues = new ContentValues();

	/**
	 * Starts the service. Check the current registered protocol providers which supports
	 * BasicIM and adds message listener to them
	 *
	 * @param bc
	 * 		BundleContext
	 */
	public void start(BundleContext bc)
	{
		this.bundleContext = bc;
		mDB = DatabaseBackend.getWritableDB();

		ServiceReference refConfig
				= bundleContext.getServiceReference(ConfigurationService.class.getName());
		configService = (ConfigurationService) bundleContext.getService(refConfig);

		// Check if the message history is enabled in the configuration
		// service, and if not do not register the service.
		boolean isMessageHistoryEnabled = configService.getBoolean(
				MessageHistoryService.PNAME_IS_MESSAGE_HISTORY_ENABLED,
				Boolean.parseBoolean(MessageHistoryActivator.getResources().getSettingsString(
						MessageHistoryService.PNAME_IS_MESSAGE_HISTORY_ENABLED)));

		// We're adding a property change listener in order to
		// listen for modifications of the isMessageHistoryEnabled property.
		msgHistoryPropListener = new MessageHistoryPropertyChangeListener();

		// Load the "IS_MESSAGE_HISTORY_ENABLED" property.
		isHistoryLoggingEnabled = configService.getBoolean(
				MessageHistoryService.PNAME_IS_MESSAGE_HISTORY_ENABLED,
				Boolean.parseBoolean(UtilActivator.getResources().getSettingsString(
						MessageHistoryService.PNAME_IS_MESSAGE_HISTORY_ENABLED)));

		configService.addPropertyChangeListener(
				MessageHistoryService.PNAME_IS_MESSAGE_HISTORY_ENABLED,
				msgHistoryPropListener);

		if (isMessageHistoryEnabled) {
			if (logger.isDebugEnabled())
				logger.debug("Starting the msg history implementation.");
			this.loadMessageHistoryService();
		}
	}

	/**
	 * Stops the service.
	 *
	 * @param bc
	 * 		BundleContext
	 */
	public void stop(BundleContext bc)
	{
		if (configService != null)
			configService.removePropertyChangeListener(msgHistoryPropListener);

		stopMessageHistoryService();
	}

	/**
	 * When new protocol provider is registered or removed; check and add/remove the listener if
	 * has BasicIM support
	 *
	 * @param serviceEvent
	 * 		ServiceEvent received
	 */
	public void serviceChanged(ServiceEvent serviceEvent)
	{
		Object sService = bundleContext.getService(serviceEvent.getServiceReference());

		if (logger.isTraceEnabled())
			logger.trace("Received a service event for: " + sService.getClass().getName());

		// we don't care if the source service is not a protocol provider
		if (!(sService instanceof ProtocolProviderService)) {
			return;
		}

		if (serviceEvent.getType() == ServiceEvent.REGISTERED) {
			if (logger.isDebugEnabled())
				logger.debug("Handling registration of a new Protocol Provider.");
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
	 * @param provider
	 * 		ProtocolProviderService
	 */
	private void handleProviderAdded(ProtocolProviderService provider)
	{
		if (logger.isDebugEnabled())
			logger.debug("Adding protocol provider " + provider.getProtocolDisplayName());

		// check whether the provider has a basic im operation set
		OperationSetBasicInstantMessaging opSetIm
				= provider.getOperationSet(OperationSetBasicInstantMessaging.class);
		if (opSetIm != null) {
			opSetIm.addMessageListener(this);

			if (this.messageSourceService != null)
				opSetIm.addMessageListener(messageSourceService);
		}
		else {
			if (logger.isTraceEnabled())
				logger.trace("Service did not have OperationSet BasicInstantMessaging.");
		}

		OperationSetSmsMessaging opSetSMS
				= provider.getOperationSet(OperationSetSmsMessaging.class);
		if (opSetSMS != null) {
			opSetSMS.addMessageListener(this);

			if (this.messageSourceService != null)
				opSetSMS.addMessageListener(messageSourceService);
		}
		else {
			if (logger.isTraceEnabled())
				logger.trace("Service did not have OperationSet SmsMessaging.");
		}

		OperationSetMultiUserChat opSetMultiUChat
				= provider.getOperationSet(OperationSetMultiUserChat.class);
		if (opSetMultiUChat != null) {
			for (ChatRoom room : opSetMultiUChat.getCurrentlyJoinedChatRooms()) {
				room.addMessageListener(this);
			}

			opSetMultiUChat.addPresenceListener(this);
			if (messageSourceService != null)
				opSetMultiUChat.addPresenceListener(messageSourceService);
		}
		else {
			if (logger.isTraceEnabled())
				logger.trace("Service did not have OperationSet MultiUserChat.");
		}

		if (messageSourceService != null) {
			OperationSetPresence opSetPresence
					= provider.getOperationSet(OperationSetPresence.class);
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

			OperationSetContactCapabilities capOpSet
					= provider.getOperationSet(OperationSetContactCapabilities.class);
			if (capOpSet != null) {
				capOpSet.addContactCapabilitiesListener(messageSourceService);
			}
		}
	}

	/**
	 * Removes the specified provider from the list of currently known providers and ignores all
	 * the messages exchanged by it
	 *
	 * @param provider
	 * 		the ProtocolProviderService that has been unregistered.
	 */
	private void handleProviderRemoved(ProtocolProviderService provider)
	{
		OperationSetBasicInstantMessaging opSetIm
				= provider.getOperationSet(OperationSetBasicInstantMessaging.class);
		if (opSetIm != null) {
			opSetIm.removeMessageListener(this);

			if (this.messageSourceService != null)
				opSetIm.removeMessageListener(messageSourceService);
		}

		OperationSetSmsMessaging opSetSMS
				= provider.getOperationSet(OperationSetSmsMessaging.class);
		if (opSetSMS != null) {
			opSetSMS.removeMessageListener(this);

			if (this.messageSourceService != null)
				opSetSMS.removeMessageListener(messageSourceService);
		}

		OperationSetMultiUserChat opSetMultiUChat
				= provider.getOperationSet(OperationSetMultiUserChat.class);
		if (opSetMultiUChat != null) {
			for (ChatRoom room : opSetMultiUChat.getCurrentlyJoinedChatRooms()) {
				room.removeMessageListener(this);
			}
			opSetMultiUChat.removePresenceListener(this);

			if (messageSourceService != null)
				opSetMultiUChat.removePresenceListener(messageSourceService);
		}

		if (messageSourceService != null) {
			OperationSetPresence opSetPresence
					= provider.getOperationSet(OperationSetPresence.class);
			if (opSetPresence != null) {
				opSetPresence.removeContactPresenceStatusListener(messageSourceService);
				opSetPresence.removeProviderPresenceStatusListener(messageSourceService);
				opSetPresence.removeSubscriptionListener(messageSourceService);
			}

			messageSourceService.handleProviderRemoved(provider);
			OperationSetContactCapabilities capOpSet
					= provider.getOperationSet(OperationSetContactCapabilities.class);
			if (capOpSet != null) {
				capOpSet.removeContactCapabilitiesListener(messageSourceService);
			}
		}
	}

	/**
	 * Returns the history service.
	 *
	 * @return the history service
	 */
	public HistoryService getHistoryService()
	{
		return historyService;
	}

	/**
	 * Set the configuration service.
	 *
	 * @param historyService
	 * 		HistoryService
	 */
	public void setHistoryService(HistoryService historyService)
	{
		synchronized (this.syncRoot_HistoryService) {
			this.historyService = historyService;

			if (logger.isDebugEnabled())
				logger.debug("New history service registered.");
		}
	}

	/**
	 * Returns all the messages exchanged by all the contacts in the supplied metaContact
	 * on and after the given date
	 *
	 * @param metaContact
	 * 		MetaContact
	 * @param startDate
	 * 		Date the start date of the conversations
	 * @return Collection of MessageReceivedEvents or MessageDeliveredEvents
	 */
	public Collection<EventObject> findByStartDate(MetaContact metaContact, Date startDate)
	{
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
	 * @param metaContact
	 * 		MetaContact
	 * @param endDate
	 * 		Date the end date of the conversations
	 * @return Collection of MessageReceivedEvents or MessageDeliveredEvents
	 */
	public Collection<EventObject> findByEndDate(MetaContact metaContact, Date endDate)
	{
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
	 * @param metaContact
	 * 		MetaContact
	 * @param startDate
	 * 		Date the start date of the conversations
	 * @param endDate
	 * 		Date the end date of the conversations
	 * @return Collection of MessageReceivedEvents or MessageDeliveredEvents
	 */
	public Collection<EventObject> findByPeriod(MetaContact metaContact, Date startDate,
			Date endDate)
	{
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
	 * @param metaContact
	 * 		MetaContact
	 * @param startDate
	 * 		Date the start date of the conversations
	 * @param endDate
	 * 		Date the end date of the conversations
	 * @param keywords
	 * 		array of keywords
	 * @return Collection of MessageReceivedEvents or MessageDeliveredEvents
	 */
	public Collection<EventObject> findByPeriod(MetaContact metaContact, Date startDate,
			Date endDate, String[] keywords)
	{
		return findByPeriod(metaContact, startDate, endDate, keywords, false);
	}

	/**
	 * Returns all the messages exchanged by all the contacts in the supplied metaContact having
	 * the given keyword
	 *
	 * @param metaContact
	 * 		MetaContact
	 * @param keyword
	 * 		keyword
	 * @return Collection of MessageReceivedEvents or MessageDeliveredEvents
	 */
	public Collection<EventObject> findByKeyword(MetaContact metaContact, String keyword)
	{
		return findByKeyword(metaContact, keyword, false);
	}

	/**
	 * Returns all the messages exchanged by all the contacts in the supplied metaContact having
	 * the given keywords
	 *
	 * @param metaContact
	 * 		MetaContact
	 * @param keywords
	 * 		keyword
	 * @return Collection of MessageReceivedEvents or MessageDeliveredEvents
	 */
	public Collection<EventObject> findByKeywords(MetaContact metaContact, String[] keywords)
	{
		return findByKeywords(metaContact, keywords, false);
	}

	/**
	 * Returns the supplied number of recent messages exchanged by all the contacts in the
	 * supplied metaContact
	 *
	 * @param metaContact
	 * 		MetaContact
	 * @param count
	 * 		messages count
	 * @return Collection of MessageReceivedEvents or MessageDeliveredEvents
	 */
	public Collection<EventObject> findLast(MetaContact metaContact, int count)
	{
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
		Collections.sort(result, new MessageEventComparator<EventObject>());
		return result;
	}

	/**
	 * Returns the supplied number of recent messages on or after the given date exchanged by all
	 * the contacts in the supplied metaContact
	 *
	 * @param metaContact
	 * 		MetaContact
	 * @param startDate
	 * 		messages after date
	 * @param count
	 * 		messages count
	 * @return Collection of MessageReceivedEvents or MessageDeliveredEvents
	 */
	public Collection<EventObject> findFirstMessagesAfter(MetaContact metaContact, Date startDate,
			int count)
	{
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
		Collections.sort(result, new MessageEventComparator<EventObject>());
		return result;
	}

	/**
	 * Returns the supplied number of recent messages before the given date exchanged by all the
	 * contacts in the supplied metaContact
	 *
	 * @param metaContact
	 * 		MetaContact
	 * @param endDate
	 * 		messages before date
	 * @param count
	 * 		messages count
	 * @return Collection of MessageReceivedEvents or MessageDeliveredEvents
	 */
	public Collection<EventObject> findLastMessagesBefore(MetaContact metaContact, Date endDate,
			int count)
	{
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
		Collections.sort(result, new MessageEventComparator<EventObject>());
		return result;
	}

	/**
	 * Returns the messages for the recently contacted <tt>count</tt> contacts.
	 *
	 * @param count
	 * 		contacts count
	 * @param providerToFilter
	 * 		can be filtered by provider e.g. Jabber:abc123@atalk.org, or <tt>null</tt> to search
	 * 		for all  providers
	 * @param contactToFilter
	 * 		can be filtered by contact e.g. xyx123@atalk.org, or <tt>null</tt> to search for all
	 * 		contacts
	 * @return Collection of MessageReceivedEvents or MessageDeliveredEvents
	 */
	public Collection<EventObject> findRecentMessagesPerContact(int count, String providerToFilter,
			String contactToFilter, boolean isSMSEnabled)
	{
		String sessionUuid;
		String accountUuid;
		String entityJid;
		String whereCondition = "";
		String[] args = {};
		Cursor cursorMsg;
		Object descriptor;
		LinkedList<EventObject> result = new LinkedList<>();

		// logger.info("Find recent message for: " + providerToFilter + " -> " + contactToFilter);
		List<String> argList = new ArrayList<>();
		if (!StringUtils.isNullOrEmpty(providerToFilter)) {
			whereCondition = ChatSession.ACCOUNT_UID + "=?";
			argList.add(providerToFilter);
		}
		if (!StringUtils.isNullOrEmpty(contactToFilter)) {
			if (!StringUtils.isNullOrEmpty(whereCondition))
				whereCondition += " AND ";
			whereCondition += ChatSession.ENTITY_JID + "=?";
			argList.add(contactToFilter);
		}
		if (argList.size() > 0)
			args = argList.toArray(new String[argList.size()]);

		Cursor cursor = mDB.query(ChatSession.TABLE_NAME, null, whereCondition, args, null,
				null, null);

		// Iterate over all the found sessionsUuid for the given accountUuid
		while (cursor.moveToNext()) {
			if (result.size() >= count)
				break;

			sessionUuid = cursor.getString(cursor.getColumnIndex(ChatSession.SESSION_UUID));
			// skip for null sessionUuid i.e. message from non-persistent contact e.g server
			// announcement.
			if (StringUtils.isNullOrEmpty(sessionUuid))
				continue;

			accountUuid = cursor.getString(cursor.getColumnIndex(ChatSession.ACCOUNT_UUID));
			entityJid = cursor.getString(cursor.getColumnIndex(ChatSession.ENTITY_JID));
			try {
				// find contact or chatRoom for given contactJid; skip if not found contacts,
				// disabled accounts and hidden one
				descriptor = getContactOrRoomByID(accountUuid, entityJid, isSMSEnabled);
				if (descriptor == null)
					continue;

				whereCondition = ChatMessage.SESSION_UUID + "=?";
				argList.clear();
				argList.add(sessionUuid);
				if (isSMSEnabled) {
					whereCondition += " AND (" + ChatMessage.MSG_TYPE + "=? OR "
							+ ChatMessage.MSG_TYPE + "=?)";
					argList.add(String.valueOf(ChatMessage.MESSAGE_SMS_IN));
					argList.add(String.valueOf(ChatMessage.MESSAGE_SMS_OUT));
				}
				args = argList.toArray(new String[argList.size()]);

				cursorMsg = mDB.query(ChatMessage.TABLE_NAME, null, whereCondition, args,
						null, null, ORDER_DESC, String.valueOf(count));


				while (cursorMsg.moveToNext()) {
					if (descriptor instanceof Contact) {
						EventObject o = convertHistoryRecordToMessageEvent(cursorMsg,
								(Contact) descriptor);
						result.add(o);
					}
					if (descriptor instanceof ChatRoom) {
						EventObject o = convertHistoryRecordToMessageEvent(cursorMsg,
								(ChatRoom) descriptor);
						result.add(o);
					}
				}
				cursorMsg.close();
			}
			catch (IOException ex) {
				logger.error("Could not read history message for: " + entityJid + ": ", ex);
			}
		}
		cursor.close();
		Collections.sort(result, new MessageEventComparator<EventObject>());
		return result;
	}

	/**
	 * Find the Contact or ChatRoom corresponding to this contactId. First Checks the account
	 * and then searches for the contact or chatRoom. Will skip hidden and disabled accounts.
	 *
	 * @param accountUuid
	 * 		the account Uuid.
	 * @param contactId
	 * 		the entityBareJid for Contact or ChatRoom in String.
	 * @param isSMSEnabled
	 * 		get contact from SmsMessage if true
	 * @return Contact or ChatRoom object.
	 */

	private Object getContactOrRoomByID(String accountUuid, String contactId, boolean isSMSEnabled)
			throws IOException
	{
        // skip for system virtual server e.g. atalk.org without "@"
        if (StringUtils.isNullOrEmpty(contactId) || contactId.indexOf("@") <= 0)
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

		ProtocolProviderService pps = accountID.getProtocolProvider();
		if (pps == null)
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
		}
		catch (Exception e) {
			logger.error("Cannot find room for: " + contactId, e);
			return null;
		}
	}

	/**
	 * Returns the sessionUuid by specified Contact
	 * Non-Persistent Entity will use ChatSession.Mode_MULTI to generate sessionUuid
	 *
	 * @param contact
	 * 		The chat Contact
	 * @return sessionUuid - created if not exist
	 */
	public String getSessionUuidByJid(Contact contact)
	{
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
	 * @param room
	 * 		The chatRoom
	 * @return sessionUuid - created if not exist
	 */
	public String getSessionUuidByJid(ChatRoom room)
	{
		AccountID accountID = room.getParentProvider().getAccountID();
		String entityJid = room.getName();

		return getSessionUuid(accountID, entityJid, ChatSession.MODE_MULTI);
	}

	/**
	 * Returns the sessionUuid by specified AccountID and chatRoomID
	 *
	 * @param accountID
	 * 		The AccountID
	 * @param entityJid
	 * 		The chatRoomID
	 * @return sessionUuid - created if not exist
	 */
	public String getSessionUuidByJid(AccountID accountID, String entityJid)
	{
		return getSessionUuid(accountID, entityJid, ChatSession.MODE_MULTI);
	}

	/**
	 * Returns the sessionUuid by the given AdHocChatRoom
	 *
	 * @param room
	 * 		The adHocChatRoom
	 * @return sessionUuid - created if not exist
	 */
	private String getSessionUuidByJid(AdHocChatRoom room)
	{
		AccountID accountID = room.getParentProvider().getAccountID();
		String entityJid = room.getName();

		return getSessionUuid(accountID, entityJid, ChatSession.MODE_MULTI);
	}

	/**
	 * Get sessionUuid for the unique pair (accountUuid + nick) OR generate new if none found
	 *
	 * @param accountID
	 * 		AccountID
	 * @param entityJid
	 * 		Contact or ChatRoom
	 * @param mode
	 * 		indicate if it is ChatSession.MODE_SINGLE or ChatSession.MODE_MUC, dictate the method
	 * 		use to generate new sessionUid
	 * @return sessionUuid - created if not exist
	 */
	private String getSessionUuid(AccountID accountID, String entityJid, int mode)
	{
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
		if (!StringUtils.isNullOrEmpty(sessionUuid))
			return sessionUuid;

		// Create new chatSession entry if one does not exist
		String timeStamp = String.valueOf(System.currentTimeMillis());
		// Use metaContactUid if it is a metaContact chatSession
		if (mode == ChatSession.MODE_SINGLE) {
			columns = new String[]{MetaContactGroup.MC_UID};
			cursor = mDB.query(MetaContactGroup.TBL_CHILD_CONTACTS, columns,
					MetaContactGroup.ACCOUNT_UUID + "=? AND " + MetaContactGroup.CONTACT_JID +
							"=?",
					args, null, null, null);
			while (cursor.moveToNext()) {
				sessionUuid = cursor.getString(0);
			}
		}

		// generate new sessionUuid for non-persistent contact or ChatSession.MODE_MULTI
		if (StringUtils.isNullOrEmpty(sessionUuid)) {
			sessionUuid = timeStamp + Math.abs(entityJid.hashCode());
		}

		contentValues.clear();
		contentValues.put(ChatSession.SESSION_UUID, sessionUuid);
		contentValues.put(ChatSession.ACCOUNT_UUID, accountUuid);
		contentValues.put(ChatSession.ACCOUNT_UID, accountUid);
		contentValues.put(ChatSession.ENTITY_JID, entityJid);
		contentValues.put(ChatSession.CREATED, timeStamp);
		contentValues.put(ChatSession.STATUS, "0");
		contentValues.put(ChatSession.MODE, mode);

		mDB.insert(ChatSession.TABLE_NAME, null, contentValues);
		return sessionUuid;
	}

	/**
	 * Use to convert HistoryRecord to MessageDeliveredEvent or MessageReceivedEvent or
	 * FileRecord which are returned in cursor by the finder Methods
	 *
	 * @param cursor
	 * 		HistoryRecord in cursor
	 * @param contact
	 * 		Contact
	 * @return Object
	 */
	private EventObject convertHistoryRecordToMessageEvent(Cursor cursor, Contact contact)
	{
		Map<String, String> mProperties = new Hashtable<>();

		for (int i = 0; i < cursor.getColumnCount(); i++) {
			String value = (cursor.getString(i) == null) ? "" : cursor.getString(i);
			mProperties.put(cursor.getColumnName(i), value);
		}

		// Return FileRecord if it is of file transfer message type
		int msgType = Integer.parseInt(mProperties.get(ChatMessage.MSG_TYPE));
		if ((msgType == ChatMessage.MESSAGE_FILE_TRANSFER_HISTORY)
				|| (msgType == ChatMessage.MESSAGE_FILE_TRANSFER_RECEIVE)
				|| (msgType == ChatMessage.MESSAGE_FILE_TRANSFER_SEND)) {
			return createFileRecordFromProperties(mProperties, contact);
		}

		// process normal chat message
		MessageImpl msg = createMessageFromProperties(mProperties);
		Date timestamp = new Date(Long.parseLong(mProperties.get(ChatMessage.TIME_STAMP)));

		if (msg.isOutgoing) {
			MessageDeliveredEvent evt = new MessageDeliveredEvent(msg, contact, timestamp);

			if ((msg.getMsgSubType() != null)
					&& msg.getMsgSubType().equals(MSG_SUBTYPE_SMS)) {
				evt.setSmsMessage(true);
			}
			return evt;
		}
		else {
			int eventType = MessageReceivedEvent.CONVERSATION_MESSAGE_RECEIVED;
			if ((msg.getMsgSubType() != null)
					&& msg.getMsgSubType().equals(MSG_SUBTYPE_SMS)) {
				eventType = MessageReceivedEvent.SMS_MESSAGE_RECEIVED;
			}
			return new MessageReceivedEvent(msg, contact, timestamp, eventType);
		}
	}

	/**
	 * Used to convert HistoryRecord in ChatRoomMessageDeliveredEvent or
	 * ChatRoomMessageReceivedEvent which are returned in cursor by the finder methods
	 *
	 * @param cursor
	 * 		HistoryRecord in cursor
	 * @param room
	 * 		the chat room
	 * @return EventObject
	 */
	private EventObject convertHistoryRecordToMessageEvent(Cursor cursor, ChatRoom room)
	{
		Map<String, String> mProperties = new Hashtable<>();

		for (int i = 0; i < cursor.getColumnCount(); i++) {
			String value = (cursor.getString(i) == null) ? "" : cursor.getString(i);
			mProperties.put(cursor.getColumnName(i), value);
		}

		MessageImpl msg = createMessageFromProperties(mProperties);
		Date timestamp = new Date(Long.parseLong(mProperties.get(ChatMessage.TIME_STAMP)));
		String nickName = mProperties.get(ChatMessage.ENTITY_JID);  // nick for muc
		// String nickName = entityJid.split("/")[1];
		String jabberID = mProperties.get(ChatMessage.JID);

        // getUserRole must have Resourcepart != null
        ChatRoomMemberRole userRole = ChatRoomMemberRole.GUEST;
        if (room.getUserNickname() != null) {
            userRole = room.getUserRole();
        }
        ChatRoomMember from = new ChatRoomMemberImpl(room, nickName, userRole);

		if (msg.isOutgoing) {
			return new ChatRoomMessageDeliveredEvent(room, timestamp, msg,
					ChatRoomMessageDeliveredEvent.CONVERSATION_MESSAGE_DELIVERED);
		}
		else
			return new ChatRoomMessageReceivedEvent(room, from, timestamp, msg,
					ChatRoomMessageReceivedEvent.CONVERSATION_MESSAGE_RECEIVED);
	}

	/**
	 * Create from the retrieved database mProperties to chatMessages
	 *
	 * @param mProperties
	 * 		message properties converted from cursor
	 * @return MessageImpl
	 */
	private MessageImpl createMessageFromProperties(Map<String, String> mProperties)
	{
		String messageUID = mProperties.get(ChatMessage.UUID);
		Date messageReceivedDate
				= new Date(Long.parseLong(mProperties.get(ChatMessage.TIME_STAMP)));
		String entityJid = mProperties.get(ChatMessage.ENTITY_JID);
		String msgBody = mProperties.get(ChatMessage.MSG_BODY);
		int encType = Integer.parseInt(mProperties.get(ChatMessage.ENC_TYPE));
		boolean isOutgoing = ChatMessage.DIR_OUT.equals(mProperties.get(ChatMessage.DIRECTION));

		String msgSubType = null;
		int msgType = Integer.parseInt(mProperties.get(ChatMessage.MSG_TYPE));
		if ((msgType == ChatMessage.MESSAGE_SMS_OUT) || (msgType == ChatMessage.MESSAGE_SMS_IN))
			msgSubType = MSG_SUBTYPE_SMS;

		return new MessageImpl(msgBody, encType, entityJid, messageUID, isOutgoing,
				messageReceivedDate, msgSubType);
	}

	/**
	 * Create from the retrieved database mProperties to FileRecord
	 *
	 * @param mProperties
	 * 		message properties converted from cursor
	 * @param contact
	 * 		the entityJid of the history message
	 * @return FileRecord
	 */
	private FileRecord createFileRecordFromProperties(Map<String, String> mProperties,
			Contact contact)
	{
		String uuid = mProperties.get(ChatMessage.UUID);
		String dir = mProperties.get(ChatMessage.DIRECTION);
		Date date = new Date(Long.parseLong(mProperties.get(ChatMessage.TIME_STAMP)));
		String file = mProperties.get(ChatMessage.FILE_PATH);
		String status = mProperties.get(ChatMessage.MSG_BODY); // contains file transfer status
		return new FileRecord(uuid, contact, dir, date, new File(file), status);
	}

	/**
	 * Loads and registers the contact source service.
	 */
	private void loadRecentMessages()
	{
		this.messageSourceService = new MessageSourceService(this);
		messageSourceServiceReg = bundleContext.registerService(
				ContactSourceService.class.getName(), messageSourceService, null);
		MessageHistoryActivator.getContactListService()
				.addMetaContactListListener(this.messageSourceService);
	}

	/**
	 * Unloads the contact source service.
	 */
	private void stopRecentMessages()
	{
		if (messageSourceServiceReg != null) {
			MessageHistoryActivator.getContactListService()
					.removeMetaContactListListener(this.messageSourceService);

			messageSourceServiceReg.unregister();
			messageSourceServiceReg = null;

			this.messageSourceService = null;
		}
	}

	// //////////////////////////////////////////////////////////////////////////
	// ChatMessageListener implementation methods for chatMessage

	public void messageReceived(MessageReceivedEvent evt)
	{
		Contact entityJid = evt.getSourceContact();
		MetaContact metaContact = MessageHistoryActivator.getContactListService()
				.findMetaContactByContact(entityJid);
		// return if logging is switched off for this particular contact
		if (metaContact != null && !isHistoryLoggingEnabled(metaContact.getMetaUID())) {
			return;
		}

		String sessionUuid = getSessionUuidByJid(entityJid);
		writeMessage(sessionUuid, ChatMessage.DIR_IN, entityJid, evt.getSourceMessage(),
				evt.getTimestamp(), evt.getEventType());
	}

	public void messageDelivered(MessageDeliveredEvent evt)
	{
		Contact entityJid = evt.getDestinationContact();
		MetaContact metaContact = MessageHistoryActivator.getContactListService()
				.findMetaContactByContact(entityJid);
		// return if logging is switched off for this particular contact
		if (metaContact != null && !isHistoryLoggingEnabled(metaContact.getMetaUID())) {
			return;
		}

		String sessionUuid = getSessionUuidByJid(entityJid);
		writeMessage(sessionUuid, ChatMessage.DIR_OUT, entityJid, evt.getSourceMessage(),
				evt.getTimestamp(), evt.getEventType());
	}

	public void messageDeliveryFailed(MessageDeliveryFailedEvent evt)
	{
		// nothing to do for the history service when delivery failed
	}

	// //////////////////////////////////////////////////////////////////////////
	// ChatRoomMessageListener implementation methods for chatRoom

	public void messageReceived(ChatRoomMessageReceivedEvent evt)
	{
		// return if logging is switched off for this particular chat room
		if (!isHistoryLoggingEnabled(evt.getSourceChatRoom().getName())) {
			return;
		}

		// ignore non conversation messages
		if (evt.getEventType() != ChatRoomMessageReceivedEvent.CONVERSATION_MESSAGE_RECEIVED)
			return;

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
					Message m1 = cev.getMessage();
					Message m2 = evt.getMessage();

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
		writeMessage(sessionUuid, ChatMessage.DIR_IN, evt.getSourceChatRoomMember(),
				evt.getMessage(), evt.getTimestamp(), evt.getEventType());
	}

	public void messageDelivered(ChatRoomMessageDeliveredEvent evt)
	{
		// return if logging is switched off for this particular chat room
		ChatRoom room = evt.getSourceChatRoom();
		if (!isHistoryLoggingEnabled(room.getName())) {
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
					Message m1 = cev.getMessage();
					Message m2 = evt.getMessage();
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
		writeMessage(sessionUuid, ChatMessage.DIR_OUT, room, evt.getMessage(), evt.getTimestamp(),
				ChatMessage.MESSAGE_MUC_OUT);
	}

	public void messageDeliveryFailed(ChatRoomMessageDeliveryFailedEvent evt)
	{
		// nothing to do for the history service when delivery failed
	}

	// //////////////////////////////////////////////////////////////////////////
	// ChatRoomMessageListener implementation methods for AdHocChatRoom (for icq)

	public void messageReceived(AdHocChatRoomMessageReceivedEvent evt)
	{
		// return if logging is switched off for this particular chat room
		if (!isHistoryLoggingEnabled(evt.getSourceChatRoom().getIdentifier())) {
			return;
		}

		String sessionUuid = getSessionUuidByJid(evt.getSourceChatRoom());
		writeMessage(sessionUuid, ChatMessage.DIR_IN, evt.getSourceChatRoomParticipant(),
				evt.getMessage(), evt.getTimestamp(), evt.getEventType());
	}

	public void messageDelivered(AdHocChatRoomMessageDeliveredEvent evt)
	{
		// return if logging is switched off for this particular chat room
		AdHocChatRoom room = evt.getSourceChatRoom();
		if (!isHistoryLoggingEnabled(room.getIdentifier())) {
			return;
		}

		String sessionUuid = getSessionUuidByJid(evt.getSourceChatRoom());
		writeMessage(sessionUuid, ChatMessage.DIR_OUT, room, evt.getMessage(), evt.getTimestamp(),
				evt.getEventType());
	}

	public void messageDeliveryFailed(AdHocChatRoomMessageDeliveryFailedEvent evt)
	{
		// nothing to do for the history service when delivery failed
	}

	// ============== Store message to database ======================

	/**
	 * Writes message to the history (ChatRoom out, AdHocChatRoom out)
	 *
	 * @param sessionUuid
	 * 		The entry with sessionUuid to which it will store the message
	 * @param direction
	 * 		coming from
	 * @param room
	 * 		ChatRoom or AdHocChatRoom (icq implementation)
	 * @param message
	 * 		Message
	 * @param messageTimestamp
	 * 		the timestamp when was message received that came from the protocol provider
	 */
	private void writeMessage(String sessionUuid, String direction, Object room,
			Message message, Date messageTimestamp, int msgType)
	{
		String entityJid = null;
		String nick = null;
		String jid = null;
		if (room instanceof ChatRoom) { // ChatRoomJabberImpl
			ChatRoom chatRoom = (ChatRoom) room;
			AccountID accountId = chatRoom.getParentProvider().getAccountID();
			jid = accountId.getAccountJid();
			// jid = ((ChatRoomJabberImpl) chatRoom).findMemberForNickName(nick).getJabberID();
			nick = chatRoom.getUserNickname().toString();
			// entityJid = chatRoom.getName() + "/" + nick;

		}
		else if (room instanceof AdHocChatRoom) {
			AdHocChatRoom chatRoom = (AdHocChatRoom) room;
			AccountID accountId = chatRoom.getParentProvider().getAccountID();
			jid = accountId.getAccountJid();
			// nick = parentProvider().getInfoRetreiver().getNickName(accountId); // for icq
			nick = jid.split("@")[0];
			// entityJid = chatRoom.getName() + "/" + nick;
		}

		contentValues.clear();
		contentValues.put(ChatMessage.SESSION_UUID, sessionUuid);
		contentValues.put(ChatMessage.TIME_STAMP, messageTimestamp.getTime());
		contentValues.put(ChatMessage.ENTITY_JID, nick);
		contentValues.put(ChatMessage.JID, jid);
		contentValues.put(ChatMessage.MSG_TYPE, msgType);

		writeMessageToDB(message, direction);
	}

	/**
	 * Writes message to the history for ChatRoom in
	 *
	 * @param sessionUuid
	 * 		The entry with sessionUuid to which it will store the message
	 * @param direction
	 * 		the direction of the message.
	 * @param from
	 * 		coming from
	 * @param message
	 * 		Message
	 * @param messageTimestamp
	 * 		the timestamp when was message received that came from the protocol provider
	 */
	private void writeMessage(String sessionUuid, String direction, ChatRoomMember from,
			Message message, Date messageTimestamp, int msgType)
	{
		// missing from, strange messages, most probably a history coming from server and
		// probably already written
		if (from == null)
			return;

		String nick = from.getNickName();
//		String entityJid = from.getChatRoom().getName() + "/" + nick;
		String jid = from.getContactAddress();  // contact entityFullJid

		contentValues.clear();
		contentValues.put(ChatMessage.SESSION_UUID, sessionUuid);
		contentValues.put(ChatMessage.TIME_STAMP, messageTimestamp.getTime());
		contentValues.put(ChatMessage.ENTITY_JID, nick);
		contentValues.put(ChatMessage.JID, jid);
		contentValues.put(ChatMessage.MSG_TYPE, msgType);

		writeMessageToDB(message, direction);
	}

	/**
	 * Writes a message to the history for chatMessage in/out and AdHocChatRoom in.
	 *
	 * @param sessionUuid
	 * 		The entry with sessionUuid to which it will store the message
	 * @param direction
	 * 		the direction of the message.
	 * @param entity
	 * 		the communicator nick for this chat
	 * @param message
	 * 		Message
	 * @param messageTimestamp
	 * 		the timestamp when was message received that came from the protocol provider
	 */
	private void writeMessage(String sessionUuid, String direction, Contact entity,
			Message message, Date messageTimestamp, int msgType)
	{
		contentValues.clear();
		contentValues.put(ChatMessage.SESSION_UUID, sessionUuid);
		contentValues.put(ChatMessage.TIME_STAMP, messageTimestamp.getTime());
		contentValues.put(ChatMessage.ENTITY_JID, entity.getAddress());
		// JID is not stored for chatMessage or incoming message
		// contentValues.put(ChatMessage.JID, entity.getAddress());
		contentValues.put(ChatMessage.MSG_TYPE, msgType);

		writeMessageToDB(message, direction);
	}

	/**
	 * Inserts message to the history. Allows to update the already saved message.
	 *
	 * @param direction
	 * 		String direction of the message in or out.
	 * @param source
	 * 		The source Contact
	 * @param destination
	 * 		The destination Contact
	 * @param message
	 * 		Message message to be written
	 * @param messageTimestamp
	 * 		the timestamp when was message received that came from the protocol provider
	 * @param isSmsSubtype
	 * 		whether message to write is an sms
	 */
	public void insertMessage(String direction, Contact source, Contact destination,
			Message message, Date messageTimestamp, boolean isSmsSubtype)
	{
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
		contentValues.put(ChatMessage.TIME_STAMP, messageTimestamp.getTime());
		contentValues.put(ChatMessage.ENTITY_JID, source.getAddress());
		contentValues.put(ChatMessage.JID, destination.getAddress());
		contentValues.put(ChatMessage.MSG_TYPE, msgType);

		writeMessageToDB(message, direction);
	}

	/**
	 * Update the reset of the message content and write to the dataBase
	 *
	 * @param message
	 * 		Message message to be written
	 */
	private void writeMessageToDB(Message message, String direction)
	{

		contentValues.put(ChatMessage.UUID, message.getMessageUID());
		contentValues.put(ChatMessage.MSG_BODY, message.getContent());
		contentValues.put(ChatMessage.ENC_TYPE, message.getEncType());
		contentValues.put(ChatMessage.DIRECTION, direction);
		contentValues.put(ChatMessage.STATUS, ChatMessage.DIR_OUT.equals(direction) ? 0 : 1);

		mDB.insert(ChatMessage.TABLE_NAME, null, contentValues);
	}

	//============ service change events handler ================//

	/**
	 * Remove a configuration service.
	 *
	 * @param historyService
	 * 		HistoryService
	 */
	public void unsetHistoryService(HistoryService historyService)
	{
		synchronized (syncRoot_HistoryService) {
			if (this.historyService == historyService) {
				this.historyService = null;

				if (logger.isDebugEnabled())
					logger.debug("History service unregistered.");
			}
		}
	}

	/**
	 * Called to notify interested parties that a change in our presence in a chat room has
	 * occurred. Changes may include us being kicked, join, left.
	 *
	 * @param evt
	 * 		the <tt>LocalUserChatRoomPresenceChangeEvent</tt> instance containing the chat
	 * 		room and the type, and reason of the change
	 */
	public void localUserPresenceChanged(LocalUserChatRoomPresenceChangeEvent evt)
	{
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
	 * @param listener
	 * 		HistorySearchProgressListener
	 */
	public void addSearchProgressListener(MessageHistorySearchProgressListener listener)
	{
		synchronized (progressListeners) {
			HistorySearchProgressListener wrapperListener = new SearchProgressWrapper(listener);
			progressListeners.put(listener, wrapperListener);
		}
	}

	/**
	 * Removing progress listener
	 *
	 * @param listener
	 * 		HistorySearchProgressListener
	 */
	public void removeSearchProgressListener(MessageHistorySearchProgressListener listener)
	{
		synchronized (progressListeners) {
			progressListeners.remove(listener);
		}
	}

	// =========== find messages for metaContact and chatRoom with keywords ==================

	/**
	 * Returns all the messages exchanged by all the contacts in the supplied metaContact between
	 * the given dates including startDate and having the given keywords
	 *
	 * @param metaContact
	 * 		MetaContact
	 * @param startDate
	 * 		Date the start date of the conversations
	 * @param endDate
	 * 		Date the end date of the conversations
	 * @param keywords
	 * 		array of keywords
	 * @param caseSensitive
	 * 		is keywords search case sensitive
	 * @return Collection of MessageReceivedEvents or MessageDeliveredEvents
	 */
	public Collection<EventObject> findByPeriod(MetaContact metaContact, Date startDate,
			Date endDate, String[] keywords, boolean caseSensitive)
	{
		HashSet<EventObject> result = new HashSet<>();
		String startTimeStamp = String.valueOf(startDate.getTime());
		String endTimeStamp = String.valueOf(endDate.getTime());
		String filterLike = "( ";
		for (String word : keywords) {
			filterLike += ChatMessage.MSG_BODY + " LIKE '%" + word + "%' OR ";
		}
		filterLike = filterLike.substring(0, filterLike.length() - 4) + " )";

		Iterator<Contact> contacts = metaContact.getContacts();
		while (contacts.hasNext()) {
			Contact contact = contacts.next();
			String sessionUuid = getSessionUuidByJid(contact);
			String[] args = {sessionUuid, startTimeStamp, endTimeStamp};

			Cursor cursor = mDB.query(ChatMessage.TABLE_NAME, null,
					ChatMessage.SESSION_UUID + "=? AND " + ChatMessage.TIME_STAMP + ">=? AND "
							+ ChatMessage.TIME_STAMP + "<? AND " + filterLike, args, null,
					null, ORDER_ASC);

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
	 * @param metaContact
	 * 		MetaContact
	 * @param keyword
	 * 		keyword
	 * @param caseSensitive
	 * 		is keywords search case sensitive
	 * @return Collection of MessageReceivedEvents or MessageDeliveredEvents
	 */
	public Collection<EventObject> findByKeyword(MetaContact metaContact, String keyword,
			boolean caseSensitive)
	{
		HashSet<EventObject> result = new HashSet<>();
		String filterLike = "( " + ChatMessage.MSG_BODY + " LIKE '%" + keyword + "%' )";

		Iterator<Contact> contacts = metaContact.getContacts();
		while (contacts.hasNext()) {
			Contact contact = contacts.next();
			String sessionUuid = getSessionUuidByJid(contact);
			String[] args = {sessionUuid};

			Cursor cursor = mDB.query(ChatMessage.TABLE_NAME, null,
					ChatMessage.SESSION_UUID + "=? AND " + filterLike, args, null, null,
					ORDER_ASC);

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
	 * @param metaContact
	 * 		MetaContact
	 * @param keywords
	 * 		keyword
	 * @param caseSensitive
	 * 		is keywords search case sensitive
	 * @return Collection of MessageReceivedEvents or MessageDeliveredEvents
	 */
	public Collection<EventObject> findByKeywords(MetaContact metaContact,
			String[] keywords, boolean caseSensitive)
	{
		HashSet<EventObject> result = new HashSet<>();
		String filterLike = "( ";
		for (String word : keywords) {
			filterLike += ChatMessage.MSG_BODY + " LIKE '%" + word + "%' OR ";
		}
		filterLike = filterLike.substring(0, filterLike.length() - 4) + " )";

		Iterator<Contact> contacts = metaContact.getContacts();
		while (contacts.hasNext()) {
			Contact contact = contacts.next();
			String sessionUuid = getSessionUuidByJid(contact);
			String[] args = {sessionUuid};

			Cursor cursor = mDB.query(ChatMessage.TABLE_NAME, null,
					ChatMessage.SESSION_UUID + "=? AND " + filterLike, args, null, null,
					ORDER_ASC);

			while (cursor.moveToNext()) {
				result.add(convertHistoryRecordToMessageEvent(cursor, contact));
			}
		}
		return result;
	}

	/**
	 * Returns all the messages exchanged in the supplied chat room on and after the given date
	 *
	 * @param room
	 * 		The chat room
	 * @param startDate
	 * 		Date the start date of the conversations
	 * @return Collection of MessageReceivedEvents or MessageDeliveredEvents
	 */
	public Collection<EventObject> findByStartDate(ChatRoom room, Date startDate)
	{
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
	 * @param room
	 * 		The chat room
	 * @param endDate
	 * 		Date the end date of the conversations
	 * @return Collection of MessageReceivedEvents or MessageDeliveredEvents
	 */
	public Collection<EventObject> findByEndDate(ChatRoom room, Date endDate)
	{
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
	 * @param room
	 * 		The chat room
	 * @param startDate
	 * 		Date the start date of the conversations
	 * @param endDate
	 * 		Date the end date of the conversations
	 * @return Collection of MessageReceivedEvents or MessageDeliveredEvents
	 */
	public Collection<EventObject> findByPeriod(ChatRoom room, Date startDate, Date endDate)
	{
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
	 * @param room
	 * 		The chat room
	 * @param startDate
	 * 		Date the start date of the conversations
	 * @param endDate
	 * 		Date the end date of the conversations
	 * @param keywords
	 * 		array of keywords
	 * @return Collection of MessageReceivedEvents or MessageDeliveredEvents
	 */
	public Collection<EventObject> findByPeriod(ChatRoom room, Date startDate, Date endDate,
			String[] keywords)
	{
		return findByPeriod(room, startDate, endDate, keywords, false);
	}

	/**
	 * Returns all the messages exchanged in the supplied chat room between the given
	 * dates and having the given keywords
	 *
	 * @param room
	 * 		The chat room
	 * @param startDate
	 * 		Date the start date of the conversations
	 * @param endDate
	 * 		Date the end date of the conversations
	 * @param keywords
	 * 		array of keywords
	 * @param caseSensitive
	 * 		is keywords search case sensitive
	 * @return Collection of MessageReceivedEvents or MessageDeliveredEvents
	 */
	public Collection<EventObject> findByPeriod(ChatRoom room, Date startDate, Date
			endDate, String[] keywords, boolean caseSensitive)
	{
		HashSet<EventObject> result = new HashSet<>();
		String startTimeStamp = String.valueOf(startDate.getTime());
		String endTimeStamp = String.valueOf(endDate.getTime());
		String sessionUuid = getSessionUuidByJid(room);
		String[] args = {sessionUuid, startTimeStamp, endTimeStamp};
		String filterLike = "( ";
		for (String word : keywords) {
			filterLike += ChatMessage.MSG_BODY + " LIKE '%" + word + "%' OR ";
		}
		filterLike = filterLike.substring(0, filterLike.length() - 4) + " )";

		Cursor cursor = mDB.query(ChatMessage.TABLE_NAME, null,
				ChatMessage.SESSION_UUID + "=? AND " + ChatMessage.TIME_STAMP + ">=? AND "
						+ ChatMessage.TIME_STAMP + "<? AND " + filterLike, args, null, null,
				ORDER_ASC);

		while (cursor.moveToNext()) {
			result.add(convertHistoryRecordToMessageEvent(cursor, room));
		}
		return result;
	}

	/**
	 * Returns all the messages exchanged in the supplied room having the given keyword
	 *
	 * @param room
	 * 		The Chat room
	 * @param keyword
	 * 		keyword
	 * @return Collection of MessageReceivedEvents or MessageDeliveredEvents
	 */
	public Collection<EventObject> findByKeyword(ChatRoom room, String keyword)
	{
		return findByKeyword(room, keyword, false);
	}

	/**
	 * Returns all the messages exchanged in the supplied chat room having the given
	 * keyword
	 *
	 * @param room
	 * 		The chat room
	 * @param keyword
	 * 		keyword
	 * @param caseSensitive
	 * 		is keywords search case sensitive
	 * @return Collection of MessageReceivedEvents or MessageDeliveredEvents
	 */
	public Collection<EventObject> findByKeyword(ChatRoom room, String keyword,
			boolean caseSensitive)
	{
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
	 * @param room
	 * 		The chat room
	 * @param keywords
	 * 		keyword
	 * @return Collection of MessageReceivedEvents or MessageDeliveredEvents
	 */
	public Collection<EventObject> findByKeywords(ChatRoom room, String[] keywords)
	{
		return findByKeywords(room, keywords, false);
	}

	/**
	 * Returns all the messages exchanged in the supplied chat room having the given
	 * keywords
	 *
	 * @param room
	 * 		The chat room
	 * @param keywords
	 * 		keyword
	 * @param caseSensitive
	 * 		is keywords search case sensitive
	 * @return Collection of MessageReceivedEvents or MessageDeliveredEvents
	 */
	public Collection<EventObject> findByKeywords(ChatRoom room, String[] keywords,
			boolean caseSensitive)
	{
		HashSet<EventObject> result = new HashSet<>();
		String sessionUuid = getSessionUuidByJid(room);
		String[] args = {sessionUuid};
		String filterLike = "( ";
		for (String word : keywords) {
			filterLike += ChatMessage.MSG_BODY + " LIKE '%" + word + "%' OR ";
		}
		filterLike = filterLike.substring(0, filterLike.length() - 4) + " )";

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
	 * @param room
	 * 		The chat room
	 * @param count
	 * 		messages count
	 * @return Collection of MessageReceivedEvents or MessageDeliveredEvents
	 */
	public Collection<EventObject> findLast(ChatRoom room, int count)
	{
		LinkedList<EventObject> result = new LinkedList<>();
		String sessionUuid = getSessionUuidByJid(room);
		String[] args = {sessionUuid};

		Cursor cursor = mDB.query(ChatMessage.TABLE_NAME, null, ChatMessage.SESSION_UUID
				+ "=?", args, null, null, ORDER_DESC, String.valueOf(count));

		while (cursor.moveToNext()) {
			result.add(convertHistoryRecordToMessageEvent(cursor, room));
		}

		Collections.sort(result, new MessageEventComparator<EventObject>());
		return result;
	}

	/**
	 * Returns the supplied number of recent messages on and after the given startDate exchanged
	 * in the supplied chat room
	 *
	 * @param room
	 * 		The chat room
	 * @param startDate
	 * 		messages on and after date
	 * @param count
	 * 		messages count
	 * @return Collection of MessageReceivedEvents or MessageDeliveredEvents
	 */
	public Collection<EventObject> findFirstMessagesAfter(ChatRoom room, Date startDate,
			int count)
	{
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

		Collections.sort(result, new ChatRoomMessageEventComparator<EventObject>());
		return result;
	}

	/**
	 * Returns the supplied number of recent messages before the given endDate exchanged in
	 * the supplied chat room
	 *
	 * @param room
	 * 		The chat room
	 * @param endDate
	 * 		messages before date
	 * @param count
	 * 		messages count
	 * @return Collection of MessageReceivedEvents or MessageDeliveredEvents
	 */
	public Collection<EventObject> findLastMessagesBefore(ChatRoom room, Date endDate, int count)
	{
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

		Collections.sort(result, new ChatRoomMessageEventComparator<EventObject>());
		return result;
	}

	/**
	 * Loads the History and MessageHistoryService. Registers the service in the bundle context.
	 */
	private void loadMessageHistoryService()
	{
		configService.addPropertyChangeListener(
				MessageHistoryService.PNAME_IS_RECENT_MESSAGES_DISABLED,
				msgHistoryPropListener);

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
	List<ProtocolProviderService> getCurrentlyAvailableProviders()
	{
		List<ProtocolProviderService> res = new ArrayList<>();

		ServiceReference[] protocolProviderRefs;
		try {
			protocolProviderRefs = bundleContext.getServiceReferences(
					ProtocolProviderService.class.getName(), null);
		}
		catch (InvalidSyntaxException ex) {
			// this shouldn't happen since we're providing no parameter string but let's log just
			// in case.
			logger.error("Error while retrieving service refs", ex);
			return res;
		}

		// in case we found any
		if (protocolProviderRefs != null) {
			if (logger.isDebugEnabled())
				logger.debug("Found " + protocolProviderRefs.length
						+ " already installed providers.");
			for (ServiceReference protocolProviderRef : protocolProviderRefs) {
				ProtocolProviderService provider = (ProtocolProviderService)
						bundleContext.getService(protocolProviderRef);
				res.add(provider);
			}
		}
		return res;
	}

	/**
	 * Stops the MessageHistoryService.
	 */
	private void stopMessageHistoryService()
	{
		// start listening for newly register or removed protocol providers
		bundleContext.removeServiceListener(this);

		ServiceReference[] protocolProviderRefs;
		try {
			protocolProviderRefs = bundleContext
					.getServiceReferences(ProtocolProviderService.class.getName(), null);
		}
		catch (InvalidSyntaxException ex) {
			// this shouldn't happen since we're providing no parameter string but let's log just
			// in case.
			logger.error("Error while retrieving service refs", ex);
			return;
		}

		// in case we found any
		if (protocolProviderRefs != null) {
			for (ServiceReference protocolProviderRef : protocolProviderRefs) {
				ProtocolProviderService provider = (ProtocolProviderService)
						bundleContext.getService(protocolProviderRef);
				this.handleProviderRemoved(provider);
			}
		}
	}

	/**
	 * Called to notify interested parties that a change in our presence in an ad-hoc chat room
	 * has occurred. Changes may include us being join, left.
	 *
	 * @param evt
	 * 		the <tt>LocalUserAdHocChatRoomPresenceChangeEvent</tt> instance containing the ad-hoc
	 * 		chat room and the type, and reason of the change
	 */
	public void localUserAdHocPresenceChanged(LocalUserAdHocChatRoomPresenceChangeEvent evt)
	{
		if (LocalUserAdHocChatRoomPresenceChangeEvent.LOCAL_USER_JOINED
				.equals(evt.getEventType())) {
			evt.getAdHocChatRoom().addMessageListener(this);
		}
		else {
			evt.getAdHocChatRoom().removeMessageListener(this);
		}
	}

	/**
	 * Permanently removes all locally stored message history.
	 * - Remove only chatMessages for metaContacts
	 * - Remove both chatSessions and chatMessages for muc
	 */
	public void eraseLocallyStoredHistory()
	{
		String[] args = {String.valueOf(ChatSession.MODE_MULTI)};
		String[] columns = {ChatSession.SESSION_UUID};

		Cursor cursor = mDB.query(ChatSession.TABLE_NAME, columns, ChatSession.MODE + "=?",
				args, null, null, null);
		while (cursor.moveToNext()) {
			purgeLocallyStoredHistory(null, cursor.getString(0));
		}
		cursor.close();
		mDB.delete(ChatMessage.TABLE_NAME, null, null);
	}

	/**
	 * Permanently removes locally stored message history for the metaContact.
	 */
	public void eraseLocallyStoredHistory(MetaContact metaContact, List<String> msgUUIDs)
	{
		if (msgUUIDs == null) {
			Iterator<Contact> contacts = metaContact.getContacts();
			while (contacts.hasNext()) {
				Contact contact = contacts.next();
				String sessionUuid = getSessionUuidByJid(contact);
				purgeLocallyStoredHistory(contact, sessionUuid);
			}
		}
		else {
			purgeLocallyStoredHistory(msgUUIDs);
		}
	}

	/**
	 * Permanently removes locally stored message history for the chatRoom.
	 */
	public void eraseLocallyStoredHistory(ChatRoom room, List<String> msgUUIDs)
	{
		if (msgUUIDs == null) {
			String sessionUuid = getSessionUuidByJid(room);
			purgeLocallyStoredHistory(null, sessionUuid);
		}
		else {
			purgeLocallyStoredHistory(msgUUIDs);
		}
	}

	/**
	 * Permanently removes locally stored message history for the sessionUuid.
	 * - Remove only chatMessages for metaContacts
	 * - Remove both chatSessions and chatMessages for muc
	 */
	private void purgeLocallyStoredHistory(Contact contact, String sessionUuid)
	{
		String[] args = {sessionUuid};
		if (contact != null) {
			mDB.delete(ChatMessage.TABLE_NAME, ChatMessage.SESSION_UUID + "=?", args);
		}
		else {
			mDB.delete(ChatSession.TABLE_NAME, ChatSession.SESSION_UUID + "=?", args);
		}
	}

	/**
	 * Permanently removes locally stored message history as specified in msgUUIDs.
	 */
	private void purgeLocallyStoredHistory(List<String> msgUUIDs)
	{
		for (String uuid : msgUUIDs) {
			String[] args = {uuid};
			mDB.delete(ChatMessage.TABLE_NAME, ChatMessage.UUID + "=?", args);
		}
	}

	/**
	 * Returns <code>true</code> if the "IS_MESSAGE_HISTORY_ENABLED" property is true,
	 * otherwise - returns <code>false</code>. Indicates to the user interface whether the
	 * history logging is enabled.
	 *
	 * @return <code>true</code> if the "IS_MESSAGE_HISTORY_ENABLED" property is true,
	 * otherwise - returns <code>false</code>.
	 */
	public boolean isHistoryLoggingEnabled()
	{
		return isHistoryLoggingEnabled;
	}

	/**
	 * Updates the "isHistoryLoggingEnabled" property through the <tt>ConfigurationService</tt>.
	 *
	 * @param isEnabled
	 * 		indicates if the history logging is enabled.
	 */
	public void setHistoryLoggingEnabled(boolean isEnabled)
	{
		isHistoryLoggingEnabled = isEnabled;

		configService.setProperty(MessageHistoryService.PNAME_IS_MESSAGE_HISTORY_ENABLED,
				Boolean.toString(isHistoryLoggingEnabled));
	}

	/**
	 * Returns <code>true</code> if the "IS_MESSAGE_HISTORY_ENABLED" property is true for the
	 * <tt>nick</tt>, otherwise - returns <code>false</code>. Indicates to the user
	 * interface whether the history logging is enabled for the supplied nick (nick for
	 * metaContact or for chatRoom).
	 *
	 * @return <code>true</code> if the "IS_MESSAGE_HISTORY_ENABLED" property is true for the
	 * <tt>nick</tt>, otherwise - returns <code>false</code>.
	 */
	public boolean isHistoryLoggingEnabled(String entityJid)
	{
		return configService.getBoolean(
				MessageHistoryService.PNAME_IS_MESSAGE_HISTORY_PER_CONTACT_ENABLED_PREFIX
						+ "." + entityJid, true);
	}

	/**
	 * Updates the "isHistoryLoggingEnabled" property through the <tt>ConfigurationService</tt>
	 * for the contact.
	 *
	 * @param isEnabled
	 * 		indicates if the history logging is enabled for the contact.
	 */
	public void setHistoryLoggingEnabled(boolean isEnabled, String id)
	{
		configService.setProperty(
				MessageHistoryService.PNAME_IS_MESSAGE_HISTORY_PER_CONTACT_ENABLED_PREFIX
						+ "." + id, isEnabled ? null : false);
	}

	/**
	 * Simple message implementation.
	 */
	private static class MessageImpl extends AbstractMessage
	{
		private final boolean isOutgoing;
		private final Date messageReceivedDate;
		private String msgSubType;

		MessageImpl(String content, int encType, String subject, String messageUID,
				boolean isOutgoing, Date messageReceivedDate, String msgSubType)
		{
			super(content, encType, subject, messageUID);

			this.isOutgoing = isOutgoing;
			this.messageReceivedDate = messageReceivedDate;
			this.msgSubType = msgSubType;
		}

		public Date getMessageReceivedDate()
		{
			return messageReceivedDate;
		}

		public String getMsgSubType()
		{
			return msgSubType;
		}
	}

	/**
	 * Used to compare MessageDeliveredEvent or MessageReceivedEvent and to be ordered in TreeSet
	 * according their timestamp
	 */
	private static class MessageEventComparator<T> implements Comparator<T>
	{
		private final boolean reverseOrder;

		MessageEventComparator(boolean reverseOrder)
		{
			this.reverseOrder = reverseOrder;
		}

		MessageEventComparator()
		{
			this(false);
		}

		public int compare(T o1, T o2)
		{
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
	private static class ChatRoomMessageEventComparator<T> implements Comparator<T>
	{
		public int compare(T o1, T o2)
		{
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
	 * Simple ChatRoomMember implementation. Searches for contact matches, to use its display nick.
	 */
	static class ChatRoomMemberImpl implements ChatRoomMember
	{
		private final ChatRoom chatRoom;
		private final String nick;
		private ChatRoomMemberRole role;
		private Contact contact = null;
		private OperationSetPersistentPresence opSetPresence = null;

		public ChatRoomMemberImpl(ChatRoom chatRoom, String fromStr, ChatRoomMemberRole role)
		{
			this.chatRoom = chatRoom;
			this.nick = fromStr;
			this.role = role;
			// this.role = chatRoom.getUserRole();
			// contact = chatRoom.getPrivateContactByNickname(fromStr);
		}

		public ChatRoom getChatRoom()
		{
			return chatRoom;
		}

		public ProtocolProviderService getProtocolProvider()
		{
			return chatRoom.getParentProvider();
		}

		public String getContactAddress()
		{
			return (getContact() == null)
					? nick : contact.getAddress();
		}

		public String getNickName()
		{
			String name = nick;
			if (StringUtils.isNullOrEmpty(name) && (getContact() != null)
					&& (getContact().getDisplayName() != null)) {
				name = getContact().getDisplayName();
			}
			return name;
		}

		public ChatRoomMemberRole getRole()
		{
			return role;
		}

		public void setRole(ChatRoomMemberRole newRole)
		{
			role = newRole;
		}

		public byte[] getAvatar()
		{
			// return null;
			return getContact().getImage();
		}

		public Contact getContact()
		{
			if ((contact == null) && (opSetPresence == null)) {
				opSetPresence = getProtocolProvider()
						.getOperationSet(OperationSetPersistentPresence.class);

				if (opSetPresence != null) {
					contact = opSetPresence.findContactByID(nick);
				}
			}
			return contact;
		}

		@Override
		public PresenceStatus getPresenceStatus()
		{
			// FIXME is this correct response?
			return GlobalStatusEnum.ONLINE;
			// return getContact().getPresenceStatus();
		}

        /**
         * {@inheritDoc}
         */
        @Override
        public String getDisplayName()
        {
            return null;
        }
	}

	/**
	 * A wrapper around HistorySearchProgressListener that fires events for
	 * MessageHistorySearchProgressListener
	 */
	private class SearchProgressWrapper implements HistorySearchProgressListener
	{
		double currentReaderProgressRatio = 0;
		double accumulatedRatio = 0;
		double currentProgress = 0;
		double lastHistoryProgress = 0;
		// used for more precise calculations with double values
		int raiser = 1000;
		private MessageHistorySearchProgressListener listener = null;

		SearchProgressWrapper(MessageHistorySearchProgressListener listener)
		{
			this.listener = listener;
		}

		private void setCurrentValues(HistoryReader currentReader, int allRecords)
		{
			currentReaderProgressRatio
					= (double) currentReader.countRecords() / allRecords * raiser;
			accumulatedRatio += currentReaderProgressRatio;
		}

		public void progressChanged(ProgressEvent evt)
		{
			int progress = getProgressMapping(evt);
			currentProgress = progress;

			listener.progressChanged(new net.java.sip.communicator.service.msghistory.event
					.ProgressEvent(MessageHistoryServiceImpl.this, evt, progress / raiser));
		}

		/**
		 * Calculates the progress according the count of the records we will search
		 *
		 * @param evt
		 * 		the progress event
		 * @return int
		 */
		private int getProgressMapping(ProgressEvent evt)
		{
			double tmpHistoryProgress = currentReaderProgressRatio * evt.getProgress();
			currentProgress += tmpHistoryProgress - lastHistoryProgress;

			if (evt.getProgress() == HistorySearchProgressListener.PROGRESS_MAXIMUM_VALUE) {
				lastHistoryProgress = 0;

				// this is the last one and the last event fire the max there will be looses in
				// currentProgress due to the deviation
				if ((int) accumulatedRatio == raiser)
					currentProgress
							= raiser * MessageHistorySearchProgressListener.PROGRESS_MAXIMUM_VALUE;
			}
			else
				lastHistoryProgress = tmpHistoryProgress;

			return (int) currentProgress;
		}

		/**
		 * clear the values
		 */
		void clear()
		{
			currentProgress = 0;
			lastHistoryProgress = 0;
		}
	}

	/**
	 * Handles <tt>PropertyChangeEvent</tt> triggered from the modification of the
	 * isMessageHistoryEnabled property.
	 */
	private class MessageHistoryPropertyChangeListener implements PropertyChangeListener
	{
		public void propertyChange(PropertyChangeEvent evt)
		{
			if (evt.getPropertyName()
					.equals(MessageHistoryService.PNAME_IS_MESSAGE_HISTORY_ENABLED)) {
				String newPropertyValue = (String) evt.getNewValue();
				isHistoryLoggingEnabled = Boolean.valueOf(newPropertyValue);

				// If the message history is not enabled we stop here.
				if (isHistoryLoggingEnabled)
					loadMessageHistoryService();
				else
					stop(bundleContext);
			}
			else if (evt.getPropertyName()
					.equals(MessageHistoryService.PNAME_IS_RECENT_MESSAGES_DISABLED)) {
				String newPropertyValue = (String) evt.getNewValue();
				boolean isDisabled = Boolean.valueOf(newPropertyValue);

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
