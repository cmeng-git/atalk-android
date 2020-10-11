/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.atalk.android.gui.chat;

import net.java.sip.communicator.impl.protocol.jabber.HttpFileDownloadJabberImpl;
import net.java.sip.communicator.service.filehistory.FileRecord;
import net.java.sip.communicator.service.protocol.IncomingFileTransferRequest;
import net.java.sip.communicator.service.protocol.OperationSetFileTransfer;

import java.util.Date;

/**
 * The <tt>ChatMessage</tt> interface is used to display a chat message.
 *
 * @author Eng Chong Meng
 */
public interface ChatMessage
{
    /* DB database column  fields */
    String TABLE_NAME = "messages";
    String UUID = "uuid";   // msg Unique identification in database (deletion Id)
    String SESSION_UUID = "chatSessionUuid"; // chatSession Uuid
    String TIME_STAMP = "timeStamp"; // TimeStamp
    String ENTITY_JID = "entityJid"; // nick (muc); contact BareJid (others)
    String JID = "Jid";              // chatRoom member: contact FullJid (msg out); user BareJid (msg in)
    String MSG_BODY = "msgBody";     // message content
    String ENC_TYPE = "encType";     // see IMessage for the ENCRYPTION_xxx & MASK definitions
    String MSG_TYPE = "msgType";     // as defined in below * message type *
    String DIRECTION = "direction";  // in or out
    String STATUS = "status";        // Use by FileTransferStatusChangeEvent and FileRecord STATUS_xxx
    String FILE_PATH = "filePath";   // filepath
    String FINGERPRINT = "OmemoFingerprint"; // rx fingerPrint
    String STEALTH_TIMER = "stealthTimer";   // stealth timer
    String CARBON = "carbon";
    String READ = "read";            // read status
    String OOB = "oob";              // 0
    String ERROR_MSG = "errorMsg";
    String SERVER_MSG_ID = "serverMsgId";    // chat msg Id - message out
    String REMOTE_MSG_ID = "remoteMsgId";    // chat msg Id - message in

    String ME_COMMAND = "/me ";

    /**
     * @see ChatMessage defined constant below
     */

    /* chat message or File transfer status - see FileRecord.STATUS_XXX */
    int STATUS_SEND = 0;
    int STATUS_RECEIVED = 1;
    int STATUS_DELETE = 99;  // to be deleted

    /* READ - message delivery status: Do not change the order, values used in MergedMessage */
    int MESSAGE_DELIVERY_NONE = 0;
    int MESSAGE_DELIVERY_CLIENT_SENT = 1;
    int MESSAGE_DELIVERY_SERVER_SENT = 2;
    int MESSAGE_DELIVERY_RECEIPT = 4;

    // Not used
    // int ENCRYPTION_DECRYPTED = 0x50;
    // int ENCRYPTION_DECRYPTION_FAILED = 0x60;

    /* Chat message stream direction */
    String DIR_OUT = "out";
    String DIR_IN = "in";

    /**
     * The message type representing outgoing messages.
     */
    int MESSAGE_OUT = 0;
    /**
     * The message type representing incoming messages.
     *
     * An event type indicating that the message being received is a standard conversation message
     * sent by another contact.
     */
    int MESSAGE_IN = 1;
    /**
     * The message type representing status messages.
     */
    int MESSAGE_STATUS = 3;
    /**
     * The message type representing system messages received.
     *
     * An event type indicting that the message being received is a system message being sent by the
     * server or a system administrator, possibly notifying us of something important such as
     * ongoing maintenance activities or server downtime.
     */
    int MESSAGE_SYSTEM = 5;
    /**
     * The message type representing action messages. These are message specific for IRC,
     * but could be used in other protocols also.
     *
     * An event type indicating that the message being received is a special message that sent by
     * either another member or the server itself, indicating that some kind of action (other than
     * the delivery of a conversation message) has occurred. Action messages are widely used in IRC
     * through the /action and /me commands
     */
    int MESSAGE_ACTION = 6;
    /**
     * The message type representing error messages.
     */
    int MESSAGE_ERROR = 9;

    /**
     * The history outgoing message type.
     */
    int MESSAGE_HISTORY_OUT = 10;
    /**
     * The history incoming message type.
     */
    int MESSAGE_HISTORY_IN = 11;

    /**
     * The Location message type.
     */
    int MESSAGE_LOCATION_OUT = 20;
    int MESSAGE_LOCATION_IN = 21;

    /**
     * The Stealth message type.
     */
    int MESSAGE_STEALTH_OUT = 30;
    int MESSAGE_STEALTH_IN = 31;

    /**
     * The message type representing sms messages.
     */
    int MESSAGE_SMS_OUT = 40;

    /**
     * an event type indicating that the message being received is an SMS message.
     */
    int MESSAGE_SMS_IN = 41;

    /**
     * The file transfer message type.
     */
    int MESSAGE_FILE_TRANSFER_SEND = 50;
    /**
     * The file transfer message type.
     */
    int MESSAGE_FILE_TRANSFER_RECEIVE = 51;

    /**
     * The Http file upload message type.
     */
    int MESSAGE_HTTP_FILE_UPLOAD = 52;

    /**
     * The Http file download message type.
     */
    int MESSAGE_HTTP_FILE_DOWNLOAD = 53;

    /**
     * The sticker message type.
     */
    int MESSAGE_STICKER_SEND = 54;

    /**
     * The file transfer history message type.
     */
    int MESSAGE_FILE_TRANSFER_HISTORY = 55;

    /* ***********************
     * All muc messages are numbered >=80 cmeng?
     * The MUC message type.
     */

    /**
     * An event type indicating that the message being received is a standard conversation message
     * sent by another member of the chat room to all current participants.
     */
    int MESSAGE_MUC_OUT = 80;

    /**
     * An event type indicating that the message being received is a standard conversation message
     * sent by another member of the chatRoom to all current participants.
     */
    int MESSAGE_MUC_IN = 81;

    /**
     * The display name of the message sender.
     *
     * Returns the string Id of the message sender.
     * Actual value is pending on message type i.e.:
     * a. userId: swordfish@atalk.org
     * b. contactId: leopard@atalk.org
     * c. chatRoom: conference@atalk.org
     * d. nickName: leopard
     *
     * Exception as recipient:
     * contactId: ChatMessage.MESSAGE_FILE_TRANSFER_SEND & ChatMessage.MESSAGE_STICKER_SEND:
     *
     * @return the string id of the message sender.
     */
    String getSender();

    /**
     * Returns the display name of the message sender.
     *
     * @return the display name of the message sender
     */
    String getSenderName();

    /**
     * Returns the date and time of the message.
     *
     * @return the date and time of the message.
     */
    Date getDate();

    /**
     * Returns the type of the message.
     *
     * @return the type of the message.
     */
    int getMessageType();

    /**
     * Returns the mime type of the content type (e.g. "text", "text/html", etc.).
     *
     * @return the mimeType
     */
    int getMimeType();

    /**
     * Returns the content of the message.
     *
     * @return the content of the message.
     */
    String getMessage();

    /**
     * Returns the encryption type of the content
     *
     * @return the encryption Type
     */
    int getEncryptionType();

    /**
     * Returns the HttpFileDownload file xfer status
     *
     * @return the HttpFileDownload file transfer status
     */
    int getXferStatus();

    /**
     * Returns the message delivery receipt status
     *
     * @return the receipt status
     */
    int getReceiptStatus();

    /**
     * Returns the server message Id of the message sent - for tracking delivery receipt
     *
     * @return the server message Id of the message sent.
     */
    String getServerMsgId();

    /**
     * Returns the remote message Id of the message received - for tracking delivery receipt
     *
     * @return the remote message Id of the message received.
     */
    String getRemoteMsgId();

    /**
     * Returns the UID of this message.
     *
     * @return the UID of this message.
     */
    String getMessageUID();

    /**
     * Returns the UID of the message that this message replaces, or <tt>null</tt> if this is a new message.
     *
     * @return the UID of the message that this message replaces, or <tt>null</tt> if this is a new message.
     */
    String getCorrectedMessageUID();

    /**
     * Indicates if given <tt>nextMsg</tt> is a consecutive message or if the <tt>nextMsg</tt>
     * is a replacement for this message.
     *
     * @param nextMsg the next message to check
     * @return <tt>true</tt> if the given message is a consecutive or replacement message, <tt>false</tt> - otherwise
     */
    boolean isConsecutiveMessage(ChatMessage nextMsg);

    /**
     * Merges given message. If given message is consecutive to this one, then their contents will be merged.
     * If given message is a replacement message for <tt>this</tt> one, then the replacement will be returned.
     *
     * @param consecutiveMessage the next message to merge with <tt>this</tt> instance
     * (it must be consecutive in terms of <tt>isConsecutiveMessage</tt> method).
     * @return merge operation result that should be used instead of this <tt>ChatMessage</tt> instance.
     */
    ChatMessage mergeMessage(ChatMessage consecutiveMessage);

    /**
     * Returns the UID that should be used for matching correction messages.
     *
     * @return the UID that should be used for matching correction messages.
     */
    String getUidForCorrection();

    /**
     * Returns original message content that should be given for the user to edit the correction.
     *
     * @return original message content that should be given for the user to edit the correction.
     */
    String getContentForCorrection();

    /**
     * Returns message content that should be used for copy and paste functionality.
     *
     * @return message content that should be used for copy and paste functionality.
     */
    String getContentForClipboard();

    /**
     * Returns the OperationSetFileTransfer of this message.
     *
     * @return the OperationSetFileTransfer of this message.
     */
    OperationSetFileTransfer getOpSet();

    /**
     * Returns IncomingFileTransferRequest]of this message.
     *
     * @return the IncomingFileTransferRequest of this message.
     */
    IncomingFileTransferRequest getFTRequest();

    /**
     * Returns HttpFileTransferImpl of this message.
     *
     * @return the IncomingFileTransferRequest of this message.
     */
    HttpFileDownloadJabberImpl getHttpFileTransfer();

    /**
     * Returns history file transfer fileRecord
     *
     * @return file history file transferRecord.
     */
    FileRecord getFileRecord();
}
