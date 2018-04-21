/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.atalk.android.gui.chat;

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
    String UUID = "uuid";
    String SESSION_UUID = "chatSessionUuid"; // chatSession Uuid
    String TIME_STAMP = "timeStamp"; // TimeStamp
    String ENTITY_JID = "entityJid"; // contactJid or chatRoomJid
    String JID = "Jid";                // chatRoom Jid
    String MSG_BODY = "msgBody";    // message content
    String ENC_TYPE = "encType";    // Mime / Encryption of the message
    String MSG_TYPE = "msgType";    // as defined in below * message type *
    String DIRECTION = "direction"; // in or out
    String STATUS = "status";        // see STATUS_
    String FILE_PATH = "filePath";  // filepath
    String FINGERPRINT = "OmemoFingerprint";    // rx fingerPrint
    String STEALTH_TIMER = "stealthTimer";        // stealth timer
    String CARBON = "carbon";
    String READ = "read";        // read status
    String OOB = "oob";            // 0
    String ERROR_MSG = "errorMsg";
    String SERVER_MSG_ID = "serverMsgId";  // muc msg id???
    String REMOTE_MSG_ID = "remoteMsgId";  // thread?

    String ME_COMMAND = "/me ";

    /**
     * @see ChatMessage defined constant below
     */

    /* chat message status */
    int STATUS_SEND = 0;
    int STATUS_RECEIVED = 1;
    int STATUS_SEND_RECEIVED = 2;
    int STATUS_SEND_DISPLAYED = 3;
    int STATUS_SEND_FAILED = 4;

    int STATUS_WAITING = 5;
    int STATUS_OFFERED = 6;
    int STATUS_UNSEND = 7;

    /* File transfer status */
    int STATUS_COMPLETED = 10;  // completed
    int STATUS_FAILED = 11;        // failed
    int STATUS_CANCELED = 12;    // canceled
    int STATUS_REFUSED = 13;    // refused
    int STATUS_ACTIVE = 14;        // active

    int STATUS_DELETE = 99;  // to be deleted

    /*
     * ENC_TYPE type defined in DB
     * Lower nibble for body mimeType
     * Upper nibble for body encryption Type
     */
    int MIME_MASK = 0x0F;
    int ENCRYPTION_MASK = 0xF0;

    int ENCODE_PLAIN = 0x00; // text/plain (UTF-8)
    int ENCODE_HTML = 0x01;    // text/html

    int ENCRYPTION_NONE = 0x00;
    int ENCRYPTION_OMEMO = 0x10;
    int ENCRYPTION_OTR = 0x20;

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
     */
    int MESSAGE_IN = 1;
    /**
     * The message type representing status messages.
     */
    int MESSAGE_STATUS = 3;
    /**
     * The message type representing system messages received.
     */
    int MESSAGE_SYSTEM = 5;
    /**
     * The message type representing action messages. These are message specific for IRC,
     * but could be used in other protocols also.
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
     * The file transfer history message type.
     */
    int MESSAGE_FILE_TRANSFER_HISTORY = 55;

    // All muc messages are numbered >=80
    /**
     * The MUC message type.
     */
    int MESSAGE_MUC_OUT = 80;
    int MESSAGE_MUC_IN = 81;

    /**
     * Returns the name of the contact sending the message.
     *
     * @return the name of the contact sending the message.
     */
    String getContactName();

    /**
     * Returns the display name of the contact sending the message.
     *
     * @return the display name of the contact sending the message
     */
    String getContactDisplayName();

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
     * Indicates if given <tt>nextMsg</tt> is a consecutive message or if the <tt>nextMsg</tt> is a replacement for this
     * message.
     *
     * @param nextMsg the next message to check
     * @return <tt>true</tt> if the given message is a consecutive or replacement message, <tt>false</tt> - otherwise
     */
    boolean isConsecutiveMessage(ChatMessage nextMsg);

    /**
     * Merges given message. If given message is consecutive to this one, then their contents will be merged. If given
     * message is a replacement message for <tt>this</tt> one, then the replacement will be returned.
     *
     * @param consecutiveMessage the next message to merge with <tt>this</tt> instance(it must be consecutive in terms of
     * <tt>isConsecutiveMessage</tt> method).
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
     * Returns the IncomingFileTransferRequest of this message.
     *
     * @return the IncomingFileTransferRequest of this message.
     */
    IncomingFileTransferRequest getFTRequest();

    /**
     * Returns history file transfer fileRecord
     *
     * @return file history file transferRecord.
     */
    FileRecord getFileRecord();
}
