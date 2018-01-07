/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 * 
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.atalk.android.gui.chat;

import android.content.ContentValues;
import android.database.Cursor;
import android.text.*;

import net.java.sip.communicator.service.contactlist.MetaContact;
import net.java.sip.communicator.service.filehistory.FileRecord;
import net.java.sip.communicator.service.protocol.*;
import net.java.sip.communicator.service.protocol.event.*;
import net.java.sip.communicator.util.*;

import org.apache.commons.lang3.StringEscapeUtils;
import org.atalk.Config;
import org.atalk.android.*;
import org.atalk.android.gui.AndroidGUIActivator;
import org.atalk.android.gui.util.Html;
import org.atalk.entities.Transferable;
import org.atalk.util.CryptoHelper;
import org.jxmpp.jid.Jid;
import org.jxmpp.jid.impl.JidCreate;
import org.jxmpp.stringprep.XmppStringprepException;

import java.net.*;
import java.sql.Timestamp;
import java.util.*;

/**
 * The <tt>ChatMessageImpl</tt> class encapsulates message information in order to provide a
 * single object containing all data needed to display a chat message.
 *
 * @author Yana Stamcheva
 * @author Pawel Domas
 * @author Eng Chong Meng
 */
public class ChatMessageImpl implements ChatMessage
{
	/**
	 * The logger
	 */
	private static final Logger logger = Logger.getLogger(ChatMessageImpl.class);

	/**
	 * The name of the contact sending the message.
	 */
	private String contactName;

	/**
	 * The display name of the contact sending the message.
	 */
	private String contactDisplayName;

	/**
	 * The date and time of the message.
	 */
	private Date date;

	/**
	 * The type of the message.
	 */
	private int messageType;

	/**
	 * The content of the message.
	 */
	private String message;

	/**
	 * The encryption type of the message content.
	 */
	private int encType;

	/**
	 * A unique identifier for this message.
	 */
	private String messageUID;

	/**
	 * The unique identifier of the message that this message should replace, or <tt>null</tt> if
	 * this is a new message.
	 */
	private String correctedMessageUID;

	/**
	 * Field used to cache processed message body after replacements and corrections. This text is
	 * used to display the message on the screen.
	 */
	private String cachedOutput = null;

	/**
	 * The file transfer OperationSet (event).
	 */
	private OperationSetFileTransfer opSet;

	/**
	 * The Incoming file transfer request (event).
	 */
	private IncomingFileTransferRequest request;

	/**
	 * A map between FileRecord status to statusCode saved in DB
	 */
	public static final HashMap<String, Integer> statusMap = new HashMap<String, Integer>() {{
		put(FileRecord.COMPLETED, STATUS_COMPLETED);
		put(FileRecord.FAILED, STATUS_FAILED);
		put(FileRecord.CANCELED, STATUS_CANCELED);
		put(FileRecord.REFUSED, STATUS_REFUSED);
		put(FileRecord.ACTIVE, STATUS_ACTIVE);
	}};

	/**
	 * The file transfer history record.
	 */
	private FileRecord fileRecord;

	/**
	 * Creates a <tt>ChatMessageImpl</tt> by specifying all parameters of the message.
	 *
	 * @param contactName
	 * 		the name of the contact sending the message
	 * @param date
	 * 		the time at which the message is sent or received
	 * @param messageType
	 * 		the type (INCOMING or OUTGOING)
	 * @param message
	 * 		the message content
	 * @param encType
	 * 		the content type (e.g. "text", "text/html", etc.)
	 */
	public ChatMessageImpl(String contactName, Date date, int messageType, String message,
			int encType)
	{
		this(contactName, null, date, messageType, null, message, encType, null, null,
				null, null, null);
	}

	public ChatMessageImpl(String contactName, Date date, int messageType, String message,
			int encType, OperationSetFileTransfer opSet, IncomingFileTransferRequest request)
	{
		this(contactName, null, date, messageType, null, message, encType, null, null,
				opSet, request, null);
	}

	public ChatMessageImpl(String contactName, Date date, int messageType, String message,
			int encType, String messageUID, FileRecord fileRecord)
	{
		this(contactName, null, date, messageType, null, message, encType, messageUID, null,
				null, null, fileRecord);
	}

	/**
	 * Creates a <tt>ChatMessageImpl</tt> by specifying all parameters of the message.
	 *
	 * @param contactName
	 * 		the name of the contact
	 * @param date
	 * 		the date and time
	 * @param messageType
	 * 		the type (INCOMING or OUTGOING)
	 * @param messageTitle
	 * 		the title of the message
	 * @param message
	 * 		the content
	 * @param encType
	 * 		the content type (e.g. "text", "text/html", etc.)
	 */
	public ChatMessageImpl(String contactName, Date date, int messageType, String messageTitle,
			String message, int encType)
	{
		this(contactName, null, date, messageType, messageTitle, message, encType, null, null,
				null, null, null);
	}

	/**
	 * Creates a <tt>ChatMessageImpl</tt> by specifying all parameters of the message.
	 *
	 * @param contactName
	 * 		the name of the contact
	 * @param contactDisplayName
	 * 		the contact display name
	 * @param date
	 * 		the date and time
	 * @param messageType
	 * 		the type (INCOMING or OUTGOING)
	 * @param message
	 * 		the content
	 * @param encType
	 * 		the content type (e.g. "text", "text/html", etc.)
	 */
	public ChatMessageImpl(String contactName, String contactDisplayName, Date date,
			int messageType, String message, int encType)
	{
		this(contactName, contactDisplayName, date, messageType, null, message, encType, null,
				null, null, null, null);
	}

	public ChatMessageImpl(String contactName, String contactDisplayName, Date date,
			int messageType, String messageTitle, String message, int encType,
			String messageUID, String correctedMessageUID)
	{
		this(contactName, contactDisplayName, date, messageType, null, message, encType,
				messageUID, correctedMessageUID, null, null, null);
	}

	/**
	 * Creates a <tt>ChatMessageImpl</tt> by specifying all parameters of the message.
	 *
	 * @param contactName
	 * 		the name of the contact
	 * @param contactDisplayName
	 * 		the contact display name
	 * @param date
	 * 		the date and time
	 * @param messageType
	 * 		the type (INCOMING or OUTGOING)
	 * @param messageTitle
	 * 		the title of the message
	 * @param message
	 * 		the content
	 * @param encType
	 * 		the content type (e.g. "text", "text/html", etc.)
	 * @param messageUID
	 * 		The ID of the message.
	 * @param correctedMessageUID
	 * 		The ID of the message being replaced.
	 */

	public ChatMessageImpl(String contactName, String contactDisplayName, Date date,
			int messageType, String messageTitle, String message, int encType,
			String messageUID, String correctedMessageUID, OperationSetFileTransfer opSet,
			IncomingFileTransferRequest request, FileRecord fileRecord)
	{
		this.contactName = contactName;
		this.contactDisplayName = contactDisplayName;
		this.date = date;
		this.messageType = messageType;
		this.message = message;
		this.encType = encType;
		this.messageUID = messageUID;
		this.correctedMessageUID = correctedMessageUID;
		this.fileRecord = fileRecord;
		this.opSet = opSet;
		this.request = request;
	}

	public void updateCachedOutput(String msg)
	{
		this.cachedOutput = msg;
	}

	/**
	 * Returns the name of the contact sending the message.
	 *
	 * @return the name of the contact sending the message.
	 */
	@Override
	public String getContactName()
	{
		return contactName;
	}

	/**
	 * Returns the display name of the contact sending the message.
	 *
	 * @return the display name of the contact sending the message
	 */
	@Override
	public String getContactDisplayName()
	{
		return contactDisplayName;
	}

	/**
	 * Returns the date and time of the message.
	 *
	 * @return the date and time of the message.
	 */
	@Override
	public Date getDate()
	{
		return date;
	}

	/**
	 * Returns the type of the message.
	 *
	 * @return the type of the message.
	 */
	@Override
	public int getMessageType()
	{
		return messageType;
	}

	/**
	 * Set the type of the message.
	 */
	public void setMessageType(int messageType)
	{
		this.messageType = messageType;
	}

	/**
	 * Returns the content of the message.
	 *
	 * @return the content of the message.
	 */
	@Override
	public String getMessage()
	{
		if (cachedOutput != null)
			return cachedOutput;

		if (message == null)
			return null;

		String output = message;
		// Escape HTML content (getEncType() can be null)
		if (ChatMessage.ENCODE_HTML != getEncType()) {
			output = Html.escapeHtml(output);
		}

		// Process replacements (cmeng - just do a direct unicode conversion for std emojis)
		// output = processReplacements(output);
		output = StringEscapeUtils.unescapeXml(output);

		// Apply the "edited at" tag for corrected message
		if (correctedMessageUID != null) {
			String editedStr = aTalkApp.getResString(R.string.service_gui_EDITED_AT,
					GuiUtils.formatTime(getDate()));
			output = "<i>" + output + "  <font color=\"#989898\" >(" + editedStr + ")</font></i>";
		}
		cachedOutput = output;
		return cachedOutput;
	}

	/**
	 * Returns the content type (e.g. "text", "text/html", etc.).
	 *
	 * @return the content type
	 */
	@Override
	public int getEncType()
	{
		return encType;
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public ChatMessage mergeMessage(ChatMessage consecutiveMessage)
	{
		if (messageUID != null && messageUID.equals(consecutiveMessage.getCorrectedMessageUID())) {
			return consecutiveMessage;
		}
		return new MergedMessage(this).mergeMessage(consecutiveMessage);
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public String getUidForCorrection()
	{
		return messageUID;
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public String getContentForCorrection()
	{
		return message;
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public String getContentForClipboard()
	{
		return message;
	}

	/**
	 * Returns the OperationSetFileTransfer of this message.
	 *
	 * @return the OperationSetFileTransfer of this message.
	 */
	@Override
	public OperationSetFileTransfer getOpSet()
	{
		return this.opSet;
	}

	/**
	 * Returns the IncomingFileTransferRequest of this message.
	 *
	 * @return the IncomingFileTransferRequest of this message.
	 */
	@Override
	public IncomingFileTransferRequest getFTRequest()
	{
		return this.request;
	}

	/**
	 * Returns the fileRecord of this message.
	 *
	 * @return the fileRecord of this message.
	 */
	@Override
	public FileRecord getFileRecord()
	{
		return fileRecord;
	}

	/**
	 * Returns the UID of this message.
	 *
	 * @return the UID of this message.
	 */
	public String getMessageUID()
	{
		return messageUID;
	}

	/**
	 * Returns the UID of the message that this message replaces, or <tt>null</tt> if this is a
	 * new message.
	 *
	 * @return the UID of the message that this message replaces, or <tt>null</tt> if this is a
	 * new message.
	 */
	public String getCorrectedMessageUID()
	{
		return correctedMessageUID;
	}

	/**
	 * Indicates if given <tt>nextMsg</tt> is a consecutive message.
	 *
	 * @param nextMsg
	 * 		the next message to check
	 * @return <tt>true</tt> if the given message is a consecutive message, <tt>false</tt> -
	 * otherwise
	 */
	public boolean isConsecutiveMessage(ChatMessage nextMsg)
	{
		// New FTRequest message always treated as non-consecutiveMessage
		boolean nonFTMsg = ((messageType != MESSAGE_FILE_TRANSFER_RECEIVE)
				&& (messageType != MESSAGE_FILE_TRANSFER_SEND));
		// New system message always treated as non-consecutiveMessage
		boolean nonSystemMsg = (messageType != MESSAGE_SYSTEM);
		// New LatLng message always treated as non-consecutiveMessage
		boolean nonLatLng = (!TextUtils.isEmpty(message) && !message.contains("LatLng:"));
		// Same UID specified
		boolean uidEqual = ((messageUID != null)
				&& messageUID.equals(nextMsg.getCorrectedMessageUID()));
		// Same message type and from the same contactName
		boolean mTypeJidEqual = ((contactName != null)
				&& (messageType == nextMsg.getMessageType())
				&& contactName.equals(nextMsg.getContactName()));
		// true if the new message is within a minute from the last one
		boolean inElapseTime = ((nextMsg.getDate().getTime() - getDate().getTime()) < 60000);

		return (nonFTMsg && nonSystemMsg && nonLatLng
				&& (uidEqual || (mTypeJidEqual &&  inElapseTime)));
	}

	/**
	 * Returns the message type corresponding to the given <tt>MessageReceivedEvent</tt>.
	 *
	 * @param evt
	 * 		the <tt>MessageReceivedEvent</tt>, that gives us information of the message type
	 * @return the message type corresponding to the given <tt>MessageReceivedEvent</tt>
	 */
	public static int getMessageType(MessageReceivedEvent evt)
	{
		int eventType = evt.getEventType();

		// Distinguish the message type, depending on the type of event that we have received.
		int messageType = -1;

		if (eventType == MessageReceivedEvent.CONVERSATION_MESSAGE_RECEIVED) {
			messageType = MESSAGE_IN;
		}
		else if (eventType == MessageReceivedEvent.SYSTEM_MESSAGE_RECEIVED) {
			messageType = MESSAGE_SYSTEM;
		}
		else if (eventType == MessageReceivedEvent.SMS_MESSAGE_RECEIVED) {
			messageType = MESSAGE_SMS_IN;
		}
		return messageType;
	}

	static public ChatMessageImpl getMsgForEvent(MessageDeliveredEvent evt)
	{
		final Contact contact = evt.getDestinationContact();
		final Message msg = evt.getSourceMessage();

		return new ChatMessageImpl(contact.getProtocolProvider().getAccountID().getAccountJid(),
				getAccountDisplayName(contact.getProtocolProvider()),
				evt.getTimestamp(), ChatMessage.MESSAGE_OUT, null,
				msg.getContent(), msg.getEncType(), msg.getMessageUID(),
				evt.getCorrectedMessageUID(), null, null, null);
	}

	static public ChatMessageImpl getMsgForEvent(final MessageReceivedEvent evt)
	{
		final Message message = evt.getSourceMessage();
		final Contact protocolContact = evt.getSourceContact();
		final MetaContact metaContact = AndroidGUIActivator.getContactListService()
				.findMetaContactByContact(protocolContact);

		return new ChatMessageImpl(protocolContact.getAddress(), metaContact.getDisplayName(),
				evt.getTimestamp(), getMessageType(evt), null,
				message.getContent(), message.getEncType(), message.getMessageUID(),
				evt.getCorrectedMessageUID(), null, null, null);
	}

	static public ChatMessageImpl getMsgForEvent(final ChatRoomMessageDeliveredEvent evt)
	{
		ChatRoom chatRoom = evt.getSourceChatRoom();
		final Message message = evt.getMessage();

		return new ChatMessageImpl(chatRoom.toString(), chatRoom.getName(),
				evt.getTimestamp(), ChatMessage.MESSAGE_OUT, null,
				message.getContent(), message.getEncType(), message.getMessageUID(),
				null, null, null, null);
	}

	static public ChatMessageImpl getMsgForEvent(final ChatRoomMessageReceivedEvent evt)
	{
		String nickName = evt.getSourceChatRoomMember().getNickName();

		final Message message = evt.getMessage();
		return new ChatMessageImpl(nickName, nickName, evt.getTimestamp(),
				ChatMessage.MESSAGE_IN, null, message.getContent(), message.getEncType(),
				message.getMessageUID(), null, null, null, null);
	}

	static public ChatMessageImpl getMsgForEvent(final FileRecord fileRecord)
	{
		Contact protocolContact = fileRecord.getContact();

		return new ChatMessageImpl(protocolContact.getAddress(), fileRecord.getDate(),
				ChatMessage.MESSAGE_FILE_TRANSFER_HISTORY, null, ChatMessage.ENCODE_PLAIN,
				fileRecord.getID(), fileRecord);
	}

	/**
	 * Returns the account user display name for the given protocol provider.
	 *
	 * @param protocolProvider
	 * 		the protocol provider corresponding to the account to add
	 * @return The account user display name for the given protocol provider.
	 */
	public static String getAccountDisplayName(ProtocolProviderService protocolProvider)
	{
		final OperationSetServerStoredAccountInfo accountInfoOpSet
				= protocolProvider.getOperationSet(OperationSetServerStoredAccountInfo.class);

		try {
			if (accountInfoOpSet != null) {
				String displayName = AccountInfoUtils.getDisplayName(accountInfoOpSet);
				if (displayName != null && displayName.length() > 0)
					return displayName;
			}
		}
		catch (Exception e) {
			logger.warn("Cannot obtain display name through OPSet");
		}
		return protocolProvider.getAccountID().getDisplayName();
	}

	// ================================================

	/* Chat Type */
	public static final int TYPE_CHAT = 0;
	public static final int TYPE_FILE = 1;
	public static final int TYPE_MUC = 3;
	public static final int TYPE_PRIVATE = 4;
	public static final int TYPE_STATUS = 5;

	protected String uuid;
	public boolean markable = false;
	protected String chatSessionUuid;
	protected Jid entityFullJid;
	protected Jid entityJid;
	protected String body;
	protected String encryptedBody;
	protected long timeStamp;
	protected int encryption;
	protected int status;
	protected int type;
	protected boolean carbon = false;
	protected boolean oob = false;
	protected String edited = null;
	protected String filePath;
	protected boolean read = true;
	protected String remoteMsgId = null;
	protected String serverMsgId = null;
	protected ChatSession chatSession = null;
	protected Transferable transferable = null;
	private ChatMessageImpl mNextMessage = null;
	private ChatMessageImpl mPreviousMessage = null;
	private String omemoFingerprint = null;
	private String errorMessage = null;

	private ChatMessageImpl()
	{
	}

	public ChatMessageImpl(ChatSession conversation, String body, int encryption, Date date,
			String contact)
	{
		this(conversation, body, encryption, STATUS_UNSEND, date, contact);
	}

	public ChatMessageImpl(ChatSession conversation, String body, int encryption, int status,
			Date date, String contact)
	{
		this(java.util.UUID.randomUUID().toString(),
				conversation.getSessionUuid(),
				conversation.getJid() == null ? null : conversation.getJid().asBareJid(),
				null,
				body,
				System.currentTimeMillis(),
				encryption,
				status,
				TYPE_CHAT,
				false,
				null,
				null,
				null,
				null,
				true,
				false,
				null,
				date,
				contact);
		this.chatSession = conversation;
	}

	public ChatMessageImpl(final String uuid, final String chatSessionUuid, final Jid entityFullJid,
			final Jid entityJid, final String body, final long timeSent,
			final int encryption, final int status, final int type, final boolean carbon,
			final String remoteMsgId, final String filePath,
			final String serverMsgId, final String fingerprint, final boolean read,
			final boolean oob, final String errorMessage,
			Date date, String contact)
	{
		this.uuid = uuid;
		this.chatSessionUuid = chatSessionUuid;
		this.entityFullJid = entityFullJid;
		this.entityJid = entityJid;
		this.body = body == null ? "" : body;
		this.timeStamp = timeSent;
		this.encryption = encryption;
		this.status = status;
		this.type = type;
		this.carbon = carbon;
		this.remoteMsgId = remoteMsgId;
		this.filePath = filePath;
		this.serverMsgId = serverMsgId;
		this.omemoFingerprint = fingerprint;
		this.read = read;
		this.oob = oob;
		this.errorMessage = errorMessage;

		this.date = date;
		this.contactName = contact;
	}

	public static ChatMessageImpl fromCursor(Cursor cursor)
	{
		Jid jid;
		String value = cursor.getString(cursor.getColumnIndex(JID));
		try {
			if (value != null) {
				jid = JidCreate.from(value);
			}
			else {
				jid = null;
			}
		}
		catch (XmppStringprepException e) {
			jid = null;
		}
		Jid trueCounterpart;
		value = cursor.getString(cursor.getColumnIndex(ENTITY_JID));
		String contact = value;
		try {
			if (value != null) {
				trueCounterpart = JidCreate.from(value);
			}
			else {
				trueCounterpart = null;
			}
		}
		catch (XmppStringprepException e) {
			trueCounterpart = null;
		}

		Long timeValue = cursor.getLong(cursor.getColumnIndex(TIME_STAMP));
		Timestamp timeStamp = new Timestamp(timeValue);
		Date date = new Date(timeStamp.getTime());

		return new ChatMessageImpl(cursor.getString(cursor.getColumnIndex(UUID)),
				cursor.getString(cursor.getColumnIndex(SESSION_UUID)),
				jid,
				trueCounterpart,
				cursor.getString(cursor.getColumnIndex(MSG_BODY)),
				cursor.getLong(cursor.getColumnIndex(TIME_STAMP)),
				cursor.getInt(cursor.getColumnIndex(ENC_TYPE)),
				cursor.getInt(cursor.getColumnIndex(STATUS)),
				cursor.getInt(cursor.getColumnIndex(MSG_TYPE)),
				cursor.getInt(cursor.getColumnIndex(CARBON)) > 0,
				cursor.getString(cursor.getColumnIndex(REMOTE_MSG_ID)),
				cursor.getString(cursor.getColumnIndex(FILE_PATH)),
				cursor.getString(cursor.getColumnIndex(SERVER_MSG_ID)),
				cursor.getString(cursor.getColumnIndex(FINGERPRINT)),
				cursor.getInt(cursor.getColumnIndex(READ)) > 0,
				cursor.getInt(cursor.getColumnIndex(OOB)) > 0,
				cursor.getString(cursor.getColumnIndex(ERROR_MSG)),
				date,
				contact);
	}

	public static ChatMessageImpl createStatusMessage(ChatSession chatSession, String body)
	{
		final ChatMessageImpl message = new ChatMessageImpl();
		message.setType(TYPE_STATUS);
		message.setChatSession(chatSession);
		message.setBody(body);
		return message;
	}

	public static ChatMessageImpl createLoadMoreMessage(ChatSession chatSession)
	{
		final ChatMessageImpl message = new ChatMessageImpl();
		message.setType(TYPE_STATUS);
		message.setChatSession(chatSession);
		message.setBody("LOAD_MORE");
		return message;
	}

	// @Override
	public ContentValues getContentValues()
	{
		ContentValues values = new ContentValues();
		values.put(UUID, uuid);
		values.put(SESSION_UUID, chatSessionUuid);
		if (entityFullJid == null) {
			values.putNull(JID);
		}
		else {
			values.put(JID, entityFullJid.toString());
		}
		if (entityJid == null) {
			values.putNull(ENTITY_JID);
		}
		else {
			values.put(ENTITY_JID, entityJid.toString());
		}
		values.put(MSG_BODY, body);
		values.put(TIME_STAMP, timeStamp);
		values.put(ENC_TYPE, encryption);
		values.put(STATUS, status);
		values.put(CARBON, carbon ? 1 : 0);
		values.put(REMOTE_MSG_ID, remoteMsgId);
		values.put(FILE_PATH, filePath);
		values.put(SERVER_MSG_ID, serverMsgId);
		values.put(FINGERPRINT, omemoFingerprint);
		values.put(READ, read ? 1 : 0);
		values.put(OOB, oob ? 1 : 0);
		values.put(ERROR_MSG, errorMessage);
		return values;
	}

	public String getUuid() {
		return this.uuid;
	}

	public String getChatSessionUuid()
	{
		return chatSessionUuid;
	}

	public ChatSession getChatSession()
	{
		return this.chatSession;
	}

	public void setChatSession(ChatSession chatSession)
	{
		this.chatSession = chatSession;
	}

	public Jid getEntityFullJid()
	{
		return entityFullJid;
	}

	public void setEntityFullJid(final Jid entityFullJid)
	{
		this.entityFullJid = entityFullJid;
	}

	public Contact getContact()
	{
		if (this.chatSession.getMode() == ChatSession.MODE_SINGLE) {
			return this.chatSession.getContact();
		}
		else {
			if (this.entityJid == null) {
				return null;
			}
			else {
				return null;
				// this.chatSession.getAccount().getRoster()
				// .getContactFromRoster(this.entityJid);
			}
		}
	}

	public String getBody()
	{
		return body;
	}

	public void setBody(String body)
	{
		if (body == null) {
			throw new Error("You should not set the message body to null");
		}
		this.body = body;
	}

	public String getErrorMessage()
	{
		return errorMessage;
	}

	public boolean setErrorMessage(String message)
	{
		boolean changed = (message != null && !message.equals(errorMessage))
				|| (message == null && errorMessage != null);
		this.errorMessage = message;
		return changed;
	}

	public long getTimeStamp()
	{
		return timeStamp;
	}

	public int getEncryption()
	{
		return encryption;
	}

	public void setEncryption(int encryption)
	{
		this.encryption = encryption;
	}

	public int getStatus()
	{
		return status;
	}

	public void setStatus(int status)
	{
		this.status = status;
	}

	public String getFilePath()
	{
		return this.filePath;
	}

	public void setFilePath(String path)
	{
		this.filePath = path;
	}

	public String getRemoteMsgId()
	{
		return this.remoteMsgId;
	}

	public void setRemoteMsgId(String id)
	{
		this.remoteMsgId = id;
	}

	public String getServerMsgId()
	{
		return this.serverMsgId;
	}

	public void setServerMsgId(String id)
	{
		this.serverMsgId = id;
	}

	public boolean isRead()
	{
		return this.read;
	}

	public void markRead()
	{
		this.read = true;
	}

	public void markUnread()
	{
		this.read = false;
	}

	public void setTime(long time)
	{
		this.timeStamp = time;
	}

	public String getEncryptedBody()
	{
		return this.encryptedBody;
	}

	public void setEncryptedBody(String body)
	{
		this.encryptedBody = body;
	}

	public int getType()
	{
		return this.type;
	}

	public void setType(int type)
	{
		this.type = type;
	}

	public boolean isCarbon()
	{
		return carbon;
	}

	public void setCarbon(boolean carbon)
	{
		this.carbon = carbon;
	}

	public void setEdited(String edited)
	{
		this.edited = edited;
	}

	public boolean edited()
	{
		return this.edited != null;
	}

	public void setEntityJid(Jid entityJid)
	{
		this.entityJid = entityJid;
	}

	public Jid getEntityJid()
	{
		return this.entityJid;
	}

	public Transferable getTransferable()
	{
		return this.transferable;
	}

	public void setTransferable(Transferable transferable)
	{
		this.transferable = transferable;
	}

	public boolean similar(ChatMessageImpl message)
	{
		if (type != TYPE_PRIVATE && this.serverMsgId != null && message.getServerMsgId() != null) {
			return this.serverMsgId.equals(message.getServerMsgId());
		}
		else if (this.body == null || this.entityFullJid == null) {
			return false;
		}
		else {
			String body, otherBody;
			if (this.hasFileOnRemoteHost()) {
				body = getFileParams().url.toString();
				otherBody = message.body == null ? null : message.body.trim();
			}
			else {
				body = this.body;
				otherBody = message.body;
			}
			if (message.getRemoteMsgId() != null) {
				return (message.getRemoteMsgId().equals(
						this.remoteMsgId) || message.getRemoteMsgId().equals(this.uuid))
						&& this.entityFullJid.equals(message.getEntityFullJid())
						&& (body.equals(otherBody)
						|| (message.getEncryption() == ENCRYPTION_PGP
						&& CryptoHelper.UUID_PATTERN.matcher(message.getRemoteMsgId()).matches()));
			}
			else {
				return this.remoteMsgId == null
						&& this.entityFullJid.equals(message.getEntityFullJid())
						&& body.equals(otherBody)
						&& Math.abs(
						this.getTimeStamp() - message.getTimeStamp()) < Config.MESSAGE_MERGE_WINDOW
						* 1000;
			}
		}
	}

	public ChatMessageImpl next()
	{
		synchronized (this.chatSession.messages) {
			if (this.mNextMessage == null) {
				int index = this.chatSession.messages.indexOf(this);
				if (index < 0 || index >= this.chatSession.messages.size() - 1) {
					this.mNextMessage = null;
				}
				else {
					this.mNextMessage = this.chatSession.messages.get(index + 1);
				}
			}
			return this.mNextMessage;
		}
	}

	public ChatMessageImpl prev()
	{
		synchronized (this.chatSession.messages) {
			if (this.mPreviousMessage == null) {
				int index = this.chatSession.messages.indexOf(this);
				if (index <= 0 || index > this.chatSession.messages.size()) {
					this.mPreviousMessage = null;
				}
				else {
					this.mPreviousMessage = this.chatSession.messages.get(index - 1);
				}
			}
			return this.mPreviousMessage;
		}
	}

	public boolean isLastCorrectableMessage()
	{
		ChatMessageImpl next = next();
		while (next != null) {
			if (next.isCorrectable()) {
				return false;
			}
			next = next.next();
		}
		return isCorrectable();
	}

	private boolean isCorrectable()
	{
		return getStatus() != STATUS_RECEIVED && !isCarbon();
	}

	public boolean mergeable(final ChatMessageImpl message)
	{
		return (message != null)
				&& (message.getType() == TYPE_CHAT
				&& this.getTransferable() == null
				&& message.getTransferable() == null
				&& message.getEncryption() != ENCRYPTION_PGP
				&& message.getEncryption() != ENCRYPTION_DECRYPTION_FAILED
				&& this.getType() == message.getType()
				// && this.getStatus() == message.getStatus()
				&& isStatusMergeable(this.getStatus(), message.getStatus())
				&& this.getEncryption() == message.getEncryption()
				&& this.getEntityFullJid() != null
				&& this.getEntityFullJid().equals(message.getEntityFullJid())
				&& this.edited() == message.edited()
				&& (message.getTimeStamp() - this.getTimeStamp())
				<= (Config.MESSAGE_MERGE_WINDOW * 1000)
				&& this.getBody().length() + message.getBody().length()
				<= Config.MAX_DISPLAY_MESSAGE_CHARS
//				&& !GeoHelper.isGeoUri(message.getBody())
//				&& !GeoHelper.isGeoUri(this.body) &&
				&& message.treatAsDownloadable() == ChatMessageImpl.Decision.NEVER
				&& this.treatAsDownloadable() == ChatMessageImpl.Decision.NEVER
				&& !message.getBody().startsWith(ME_COMMAND)
				&& !this.getBody().startsWith(ME_COMMAND)
				&& !this.bodyIsHeart()
				&& !message.bodyIsHeart()
				&& ((this.omemoFingerprint == null && message.omemoFingerprint == null)
				|| this.omemoFingerprint.equals(message.getFingerprint()))
				);
	}

	private static boolean isStatusMergeable(int a, int b)
	{
		return a == b || (
				(a == STATUS_SEND_RECEIVED && b == STATUS_UNSEND)
						|| (a == STATUS_SEND_RECEIVED && b == STATUS_SEND)
						|| (a == STATUS_UNSEND && b == STATUS_SEND)
						|| (a == STATUS_UNSEND && b == STATUS_SEND_RECEIVED)
						|| (a == STATUS_SEND && b == STATUS_UNSEND)
						|| (a == STATUS_SEND && b == STATUS_SEND_RECEIVED)
		);
	}

	public static class MergeSeparator
	{
	}

	public SpannableStringBuilder getMergedBody()
	{
		SpannableStringBuilder body = new SpannableStringBuilder(this.body.trim());
		ChatMessageImpl current = this;
		while (current.mergeable(current.next())) {
			current = current.next();
			if (current == null) {
				break;
			}
			body.append("\n\n");
			body.setSpan(new ChatMessageImpl.MergeSeparator(), body.length() - 2, body.length(),
					SpannableStringBuilder.SPAN_EXCLUSIVE_EXCLUSIVE);
			body.append(current.getBody().trim());
		}
		return body;
	}

	public boolean hasMeCommand()
	{
		return this.body.trim().startsWith(ME_COMMAND);
	}

	public int getMergedStatus()
	{
		int status = this.status;
		ChatMessageImpl current = this;
		while (current.mergeable(current.next())) {
			current = current.next();
			if (current == null) {
				break;
			}
			status = current.status;
		}
		return status;
	}

	public long getMergedTimeSent()
	{
		long time = this.timeStamp;
		ChatMessageImpl current = this;
		while (current.mergeable(current.next())) {
			current = current.next();
			if (current == null) {
				break;
			}
			time = current.timeStamp;
		}
		return time;
	}

	public boolean wasMergedIntoPrevious()
	{
		ChatMessageImpl prev = this.prev();
		return prev != null && prev.mergeable(this);
	}

	public boolean trusted()
	{
		Contact contact = this.getContact();
		return (status > STATUS_RECEIVED || (contact != null));
		// && contact.mutualPresenceSubscription()));
	}

	public boolean fixCounterpart()
	{
//		Presences presences = chatSession.getContact().getPresences();
//		if (entityFullJid != null && presences.has(entityFullJid.getResourcepart())) {
//			return true;
//		}
//		else if (presences.size() >= 1) {
//			try {
//				entityFullJid = Jid.fromParts(chatSession.getJid().getLocalpart(),
//						chatSession.getJid().getDomainpart(),
//						presences.toResourceArray()[0]);
//				return true;
//			}
//			catch (InvalidJidException e) {
//				entityFullJid = null;
//				return false;
//			}
//		}
//		else {
//			entityFullJid = null;
		return false;
//		}
	}

	public void setUuid(String uuid)
	{
		this.uuid = uuid;
	}

	public String getEditedId()
	{
		return edited;
	}

	public void setOob(boolean isOob)
	{
		this.oob = isOob;
	}

	public enum Decision
	{
		MUST,
		SHOULD,
		NEVER,
	}

	private static String extractRelevantExtension(URL url)
	{
		String path = url.getPath();
		return extractRelevantExtension(path);
	}

	private static String extractRelevantExtension(String path)
	{
		if (path == null || path.isEmpty()) {
			return null;
		}

		String filename = path.substring(path.lastIndexOf('/') + 1).toLowerCase();
		int dotPosition = filename.lastIndexOf(".");

		if (dotPosition != -1) {
			String extension = filename.substring(dotPosition + 1);
			// we want the real file extension, not the crypto one
			if (Transferable.VALID_CRYPTO_EXTENSIONS.contains(extension)) {
				return extractRelevantExtension(filename.substring(0, dotPosition));
			}
			else {
				return extension;
			}
		}
		return null;
	}

//	public String getMimeType()
//	{
//		if (filePath != null) {
//			int start = filePath.lastIndexOf('.') + 1;
//			if (start < filePath.length()) {
//				return MimeUtils.guessMimeTypeFromExtension(filePath.substring(start));
//			}
//			else {
//				return null;
//			}
//		}
//		else {
//			try {
//				return MimeUtils.guessMimeTypeFromExtension(
//						extractRelevantExtension(new URL(body.trim())));
//			}
//			catch (MalformedURLException e) {
//				return null;
//			}
//		}
//	}

	public ChatMessageImpl.Decision treatAsDownloadable()
	{
		if (body.trim().contains(" ")) {
			return ChatMessageImpl.Decision.NEVER;
		}
		try {
			URL url = new URL(body);
			if (!url.getProtocol().equalsIgnoreCase("http") && !url.getProtocol().equalsIgnoreCase(
					"https")) {
				return ChatMessageImpl.Decision.NEVER;
			}
			else if (oob) {
				return ChatMessageImpl.Decision.MUST;
			}
			String extension = extractRelevantExtension(url);
			if (extension == null) {
				return ChatMessageImpl.Decision.NEVER;
			}
			String ref = url.getRef();
			boolean encrypted = ref != null && ref.matches("([A-Fa-f0-9]{2}){48}");

			if (encrypted) {
				return ChatMessageImpl.Decision.MUST;
			}
			else if (Transferable.VALID_IMAGE_EXTENSIONS.contains(extension)
					|| Transferable.WELL_KNOWN_EXTENSIONS.contains(extension)) {
				return ChatMessageImpl.Decision.SHOULD;
			}
			else {
				return ChatMessageImpl.Decision.NEVER;
			}

		}
		catch (MalformedURLException e) {
			return ChatMessageImpl.Decision.NEVER;
		}
	}

	public boolean bodyIsHeart()
	{
		return (body != null); // && UIHelper.HEARTS.contains(body.trim());
	}

	public ChatMessageImpl.FileParams getFileParams()
	{
		ChatMessageImpl.FileParams params = getLegacyFileParams();
		if (params != null) {
			return params;
		}
		params = new ChatMessageImpl.FileParams();
		if (this.transferable != null) {
			params.size = this.transferable.getFileSize();
		}
		if (body == null) {
			return params;
		}
		String parts[] = body.split("\\|");
		switch (parts.length) {
			case 1:
				try {
					params.size = Long.parseLong(parts[0]);
				}
				catch (NumberFormatException e) {
					try {
						params.url = new URL(parts[0]);
					}
					catch (MalformedURLException e1) {
						params.url = null;
					}
				}
				break;
			case 2:
			case 4:
				try {
					params.url = new URL(parts[0]);
				}
				catch (MalformedURLException e1) {
					params.url = null;
				}
				try {
					params.size = Long.parseLong(parts[1]);
				}
				catch (NumberFormatException e) {
					params.size = 0;
				}
				try {
					params.width = Integer.parseInt(parts[2]);
				}
				catch (NumberFormatException | ArrayIndexOutOfBoundsException e) {
					params.width = 0;
				}
				try {
					params.height = Integer.parseInt(parts[3]);
				}
				catch (NumberFormatException | ArrayIndexOutOfBoundsException e) {
					params.height = 0;
				}
				break;
			case 3:
				try {
					params.size = Long.parseLong(parts[0]);
				}
				catch (NumberFormatException e) {
					params.size = 0;
				}
				try {
					params.width = Integer.parseInt(parts[1]);
				}
				catch (NumberFormatException e) {
					params.width = 0;
				}
				try {
					params.height = Integer.parseInt(parts[2]);
				}
				catch (NumberFormatException e) {
					params.height = 0;
				}
				break;
		}
		return params;
	}

	public ChatMessageImpl.FileParams getLegacyFileParams()
	{
		ChatMessageImpl.FileParams params = new ChatMessageImpl.FileParams();
		if (body == null) {
			return params;
		}
		String parts[] = body.split(",");
		if (parts.length == 3) {
			try {
				params.size = Long.parseLong(parts[0]);
			}
			catch (NumberFormatException e) {
				return null;
			}
			try {
				params.width = Integer.parseInt(parts[1]);
			}
			catch (NumberFormatException e) {
				return null;
			}
			try {
				params.height = Integer.parseInt(parts[2]);
			}
			catch (NumberFormatException e) {
				return null;
			}
			return params;
		}
		else {
			return null;
		}
	}

	public void untie()
	{
		this.mNextMessage = null;
		this.mPreviousMessage = null;
	}

	public boolean isFile()
	{
		return type == TYPE_FILE;
	}

	public boolean hasFileOnRemoteHost()
	{
		return isFile() && getFileParams().url != null;
	}

	public boolean needsUploading()
	{
		return isFile() && getFileParams().url == null;
	}

	public class FileParams
	{
		public URL url;
		public long size = 0;
		public int width = 0;
		public int height = 0;
	}

	public void setFingerprint(String fingerprint)
	{
		this.omemoFingerprint = fingerprint;
	}

	public String getFingerprint()
	{
		return omemoFingerprint;
	}

//	public boolean isTrusted()
//	{
//		FingerprintStatus s = chatSession.getAccountID().getAxolotlService()
//				.getFingerprintTrust(omemoFingerprint);
//		return s != null && s.isTrusted();
//	}

	private int getPreviousEncryption()
	{
		for (ChatMessageImpl iterator = this.prev(); iterator != null;
			 iterator = iterator.prev()) {
			if (iterator.isCarbon() || iterator.getStatus() == STATUS_RECEIVED) {
				continue;
			}
			return iterator.getEncryption();
		}
		return ENCRYPTION_NONE;
	}

	private int getNextEncryption()
	{
		for (ChatMessageImpl iterator = this.next(); iterator != null;
			 iterator = iterator.next()) {
			if (iterator.isCarbon() || iterator.getStatus() == STATUS_RECEIVED) {
				continue;
			}
			return iterator.getEncryption();
		}
		return chatSession.getNextEncryption();
	}

	public boolean isValidInSession()
	{
		int pastEncryption = getCleanedEncryption(this.getPreviousEncryption());
		int futureEncryption = getCleanedEncryption(this.getNextEncryption());

		boolean inUnencryptedSession = pastEncryption == ENCRYPTION_NONE
				|| futureEncryption == ENCRYPTION_NONE
				|| pastEncryption != futureEncryption;

		return inUnencryptedSession || getCleanedEncryption(this.getEncryption()) == pastEncryption;
	}

	private static int getCleanedEncryption(int encryption)
	{
		if (encryption == ENCRYPTION_DECRYPTED || encryption == ENCRYPTION_DECRYPTION_FAILED) {
			return ENCRYPTION_PGP;
		}
		return encryption;
	}

	public ChatSession getConversation()
	{
		return this.chatSession;
	}
}
