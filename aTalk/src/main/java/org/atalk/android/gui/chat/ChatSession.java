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

import android.content.ContentValues;
import android.database.Cursor;

import net.java.sip.communicator.service.msghistory.MessageHistoryService;
import net.java.sip.communicator.service.protocol.*;

import org.atalk.entities.Bookmark;
import org.jivesoftware.smackx.muc.MultiUserChat;
import org.json.*;
import org.jxmpp.jid.*;
import org.jxmpp.jid.impl.JidCreate;
import org.jxmpp.stringprep.XmppStringprepException;
import org.jxmpp.util.XmppStringUtils;

import java.util.*;

/**
 * @author Yana Stamcheva
 * @author Lubomir Marinov
 * @author Eng Chong Meng
 */
public abstract class ChatSession // extends AbstractEntity
{
	public static final String TABLE_NAME = "chatSessions";  // chat session
	public static final String SESSION_UUID = "sessionUuid";
	public static final String ACCOUNT_UUID = "accountUuid";
	public static final String ACCOUNT_UID = "accountUid";	// AccountUID
	public static final String ENTITY_JID = "entityJid"; 	// entityJid for contact or chatRoom
	public static final String CREATED = "created";			// time stamp
	public static final String STATUS = "status";
	public static final String MODE = "mode";                // muc = 1
	public static final String ATTRIBUTES = "attributes";    // see below ATTR_*

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
	public static final int MODE_NPE = 2;	// non-persistent entity

	private String sessionUuid;
	private String accountUuid;
	private String entityBareJid;
	private String entityJid;
	private Jid contactJid;
	private int status;
	private long created;
	private int mode;
	private JSONObject attributes = new JSONObject();

	private static ChatSession chatSession;
	public final ArrayList<ChatMessageImpl> messages = new ArrayList<>();
	private AccountID accountId = null;
	private String nextMessage;
	private transient MultiUserChat mucOptions = null;
	private Bookmark bookmark;

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
	 * The list of all <tt>ChatSessionChangeListener</tt> registered to listen for transport
	 * modifications.
	 */
	private final List<ChatSessionChangeListener> chatTransportChangeListeners = new ArrayList<>();

	/**
	 * Returns the descriptor of this chat session.
	 *
	 * @return the descriptor of this chat session.
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
	 * Returns <code>true</code> if this chat session descriptor is persistent, otherwise returns
	 * <code>false</code>.
	 *
	 * @return <code>true</code> if this chat session descriptor is persistent, otherwise returns
	 * <code>false</code>.
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
	 * Returns a list of all <tt>ChatTransport</tt>s contained in this session supporting the
	 * given <tt>opSetClass</tt>.
	 *
	 * @param opSetClass
	 * 		the <tt>OperationSet</tt> class we're looking for
	 * @return a list of all <tt>ChatTransport</tt>s contained in this session supporting the
	 * given <tt>opSetClass</tt>
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
	 * Returns the <tt>ChatPanel</tt> that provides the connection between this chat session and
	 * its UI.
	 *
	 * @return The <tt>ChatSessionRenderer</tt>.
	 */
	public abstract ChatPanel getChatSessionRenderer();

	/**
	 * Sets the transport that will be used for all operations within this chat session.
	 *
	 * @param chatTransport
	 * 		The transport to set as a default transport for this session.
	 */
	public abstract void setCurrentChatTransport(ChatTransport chatTransport);

	/**
	 * Returns the entityBareJid of the chat. If this chat panel corresponds to a single
	 * chat it will return the entityBareJid of the <tt>MetaContact</tt>, otherwise it
	 * will return the entityBareJid of the chat room.
	 *
	 * @return the entityBareJid of the chat
	 */
	public abstract String getChatName();

	/**
	 * Returns a collection of the last N number of history messages given by count.
	 *
	 * @param count
	 * 		The number of messages from history to return.
	 * @return a collection of the last N number of messages given by count.
	 */
	public abstract Collection<Object> getHistory(int count);

	/**
	 * Returns a collection of the last N number of history messages given by count before the
	 * given date.
	 *
	 * @param date
	 * 		The date up to which we're looking for messages.
	 * @param count
	 * 		The number of messages from history to return.
	 * @return a collection of the last N number of messages given by count.
	 */
	public abstract Collection<Object> getHistoryBeforeDate(Date date, int count);

	/**
	 * Returns a collection of the last N number of history messages given by count after the
	 * given date.
	 *
	 * @param date
	 * 		The date from which we're looking for messages.
	 * @param count
	 * 		The number of messages from history to return.
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
	 * @param smsPhoneNumber
	 * 		The default mobile number used to send sms-es in this session.
	 */
	public abstract void setDefaultSmsNumber(String smsPhoneNumber);

	/**
	 * Disposes this chat session.
	 */
	public abstract void dispose();

	/**
	 * Returns the ChatTransport corresponding to the given descriptor.
	 *
	 * @param descriptor
	 * 		The descriptor of the chat transport we're looking for.
	 * @param resourceName
	 * 		The entityBareJid of the resource if any, null otherwise
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
	 * supported by this <code>ChatSession</code>. For example, UI implementations may use the
	 * indicator to determine whether UI elements should be created for the user to represent the
	 * contact list of the participants in this <code>ChatSession</code>.
	 *
	 * @return <tt>true</tt> if this <code>ChatSession</code> supports a contact list of
	 * (multiple) participants; otherwise, <tt>false</tt>
	 */
	public abstract boolean isContactListSupported();

	/**
	 * Adds the given {@link ChatSessionChangeListener} to this <tt>ChatSession</tt>.
	 *
	 * @param l
	 * 		the <tt>ChatSessionChangeListener</tt> to add
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
	 * @param l
	 * 		the <tt>ChatSessionChangeListener</tt> to add
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

//=====================================
	public ChatSession()
	{
	}

	public ChatSession(AccountID accountId, String bareJid, Jid contactJid, int mode)
	{
		this(java.util.UUID.randomUUID().toString(), accountId.getAccountUuid(), bareJid,
				accountId.getUserID(), System.currentTimeMillis(), STATUS_AVAILABLE, mode, "");
		this.accountId = accountId;
	}

	public ChatSession(String uuid, String accountUuid, String bareJid, String contactJid,
			long created, int status, int mode, String attributes)
	{
		this.sessionUuid = uuid;
		this.accountUuid = accountUuid;
		this.entityBareJid = bareJid;
		this.entityJid = contactJid;
		this.created = created;
		this.status = status;
		this.mode = mode;
		try {
			this.attributes = new JSONObject(attributes == null ? "" : attributes);
		}
		catch (JSONException e) {
			this.attributes = new JSONObject();
		}
	}

	public ContentValues getContentValues()
	{
		ContentValues values = new ContentValues();
		values.put(SESSION_UUID, sessionUuid);
		values.put(ACCOUNT_UUID, accountUuid);
		values.put(ENTITY_JID, entityBareJid);
		values.put(ACCOUNT_UID, contactJid.toString());
		values.put(CREATED, created);
		values.put(STATUS, status);
		values.put(MODE, mode);
		values.put(ATTRIBUTES, attributes.toString());
		return values;
	}

	public static ChatSession fromCursor(Cursor cursor)
	{
		Jid jid;
		try {
			jid = JidCreate.from(cursor.getString(cursor.getColumnIndex(ACCOUNT_UID)));
		}
		catch (XmppStringprepException e) {
			jid = null;
		}
		return chatSession;
		// cmeng - cannot instantiate abstract class
//		return new ChatSession(cursor.getString(cursor.getColumnIndex(SESSION_UUID)),
//				cursor.getString(cursor.getColumnIndex(CONTACT_JID)),
//				cursor.getString(cursor.getColumnIndex(ACCOUNT_UID)),
//				cursor.getString(cursor.getColumnIndex(CONTACT_JID)),
//				cursor.getString(cursor.getColumnIndex(ACCOUNT_UID)),
//				cursor.getLong(cursor.getColumnIndex(CREATED)),
//				cursor.getInt(cursor.getColumnIndex(STATUS)),
//				cursor.getInt(cursor.getColumnIndex(MODE)),
//				cursor.getString(cursor.getColumnIndex(ATTRIBUTES)));
	}

	public String getName()
	{
		if (getMode() == MODE_MULTI) {
			if (getMucOptions().getSubject() != null) {
				return getMucOptions().getSubject();
			}
			else if (bookmark != null
					&& bookmark.getBookmarkName() != null
					&& !bookmark.getBookmarkName().trim().isEmpty()) {
				return bookmark.getBookmarkName().trim();
			}
			else {
				String generatedName = createNameFromParticipants();
				if (generatedName != null) {
					return generatedName;
				}
				else {
					return XmppStringUtils.parseLocalpart(getJid().toString());
				}
			}
		}
		else {
			return this.getContact().getDisplayName();
		}
	}

	public List<Jid> getAcceptedCryptoTargets()
	{
		if (mode == MODE_SINGLE) {
			return Arrays.asList((Jid) getJid().asBareJid());
		}
		else {
			return getJidListAttribute(ATTR_CRYPTO_TARGETS);
		}
	}

	public AccountID getAccountID()
	{
		return this.accountId;
	}

	public Contact getContact()
	{
		// return this.account.getRoster().getContact(this.contactJid);
		return null;
	}

	public Jid getJid()
	{
		return this.contactJid;
	}

	public int getMode()
	{
		return this.mode;
	}

	public synchronized MultiUserChat getMucOptions()
	{
		if (this.mucOptions == null) {
			// this.mucOptions = new MultiUserChat(this);
		}
		return this.mucOptions;
	}

	private int getMostRecentlyUsedIncomingEncryption()
	{
		synchronized (this.messages) {
			for (int i = this.messages.size() - 1; i >= 0; --i) {
				final ChatMessageImpl m = this.messages.get(i);
				if (m.getStatus() == ChatMessageImpl.STATUS_RECEIVED) {
					final int e = m.getEncryption();
					if (e == ChatMessageImpl.ENCRYPTION_DECRYPTED
							|| e == ChatMessageImpl.ENCRYPTION_DECRYPTION_FAILED) {
						return ChatMessageImpl.ENCRYPTION_PGP;
					}
					else {
						return e;
					}
				}
			}
		}
		return ChatMessageImpl.ENCRYPTION_NONE;
	}

	public int getNextEncryption()
	{
		// final AxolotlService axolotlService = getAccountID().getAxolotlService();
		int next = this.getIntAttribute(ATTR_NEXT_ENCRYPTION, -1);
//		if (next == -1) {
//			if (Config.supportOmemo()
//					&& axolotlService != null
//					&& mode == MODE_SINGLE
//					&& axolotlService.isConversationAxolotlCapable(this)) {
//				return ChatMessageImpl.ENCRYPTION_AXOLOTL;
//			}
//			else {
//				next = this.getMostRecentlyUsedIncomingEncryption();
//			}
//		}
		return next;
	}

	public void deregisterWithBookmark()
	{
		if (this.bookmark != null) {
			this.bookmark.setConversation(null);
		}
	}

	public String getAttribute(String key)
	{
		synchronized (this.attributes) {
			try {
				return this.attributes.getString(key);
			}
			catch (JSONException e) {
				return null;
			}
		}
	}

	public List<Jid> getJidListAttribute(String key)
	{
		ArrayList<Jid> list = new ArrayList<>();
		synchronized (this.attributes) {
			try {
				JSONArray array = this.attributes.getJSONArray(key);
				for (int i = 0; i < array.length(); ++i) {
					try {
						list.add(JidCreate.from(array.getString(i)));
					}
					catch (XmppStringprepException e) {
						e.printStackTrace();
					}
				}
			}
			catch (JSONException e) {
				//ignored
			}
		}
		return list;
	}

	public int getIntAttribute(String key, int defaultValue)
	{
		String value = this.getAttribute(key);
		if (value == null) {
			return defaultValue;
		}
		else {
			try {
				return Integer.parseInt(value);
			}
			catch (NumberFormatException e) {
				return defaultValue;
			}
		}
	}

	private String createNameFromParticipants()
	{
		if (mucOptions.getOccupantsCount() >= 2) {
			List<EntityFullJid> names = new ArrayList<>();
			for (EntityFullJid user : mucOptions.getOccupants()) {
				names.add(user);
			}
			StringBuilder builder = new StringBuilder();
			for (int i = 0; i < names.size(); ++i) {
				builder.append(names.get(i));
				if (i != names.size() - 1) {
					builder.append(", ");
				}
			}
			return builder.toString();
		}
		else {
			return null;
		}
	}
}
