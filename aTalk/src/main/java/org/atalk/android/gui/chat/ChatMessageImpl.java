/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.atalk.android.gui.chat;

import android.text.TextUtils;

import java.io.File;
import java.util.Date;

import net.java.sip.communicator.impl.protocol.jabber.HttpFileDownloadJabberImpl;
import net.java.sip.communicator.service.contactlist.MetaContact;
import net.java.sip.communicator.service.filehistory.FileRecord;
import net.java.sip.communicator.service.muc.ChatRoomWrapper;
import net.java.sip.communicator.service.protocol.Contact;
import net.java.sip.communicator.service.protocol.IMessage;
import net.java.sip.communicator.service.protocol.IncomingFileTransferRequest;
import net.java.sip.communicator.service.protocol.OperationSetFileTransfer;
import net.java.sip.communicator.service.protocol.event.ChatRoomMessageDeliveredEvent;
import net.java.sip.communicator.service.protocol.event.ChatRoomMessageReceivedEvent;
import net.java.sip.communicator.service.protocol.event.MessageDeliveredEvent;
import net.java.sip.communicator.service.protocol.event.MessageReceivedEvent;

import org.atalk.android.gui.AppGUIActivator;

import org.apache.commons.text.StringEscapeUtils;

/**
 * The <code>ChatMessageImpl</code> class encapsulates message information in order to provide a
 * single object containing all data needed to display a chat message.
 *
 * @author Yana Stamcheva
 * @author Pawel Domas
 * @author Eng Chong Meng
 */
public class ChatMessageImpl implements ChatMessage {
    /**
     * The string Id of the message sender. The value is used in quoted messages.
     * Actual value pending message type i.e.:
     * userId: delivered chat message e.g. swordfish@atalk.org
     * contactId: received chat message e.g. leopoard@atalk.org
     * chatRoom: delivered group chat message e.g. conference@atalk.org
     * nickName: received group chat message e.g. leopard
     * <p>
     * Exception as recipient:
     * contactId: ChatMessage.MESSAGE_FILE_TRANSFER_SEND & ChatMessage.MESSAGE_STICKER_SEND:
     */
    private final String mSender;

    /**
     * The display name of the message sender. It may be the same as sender
     */
    private final String mSenderName;

    /**
     * The date and time of the message.
     */
    private final Date date;

    /**
     * The type of the message.
     */
    private int messageType;

    /**
     * The mime type of the message content.
     */
    private final int mimeType;

    /**
     * The content of the message.
     */
    private String messageBody;

    /**
     * The direction of the message.
     */
    private final String mDirection;

    /**
     * The ChatMessage#STATUS_xxx or HTTP file download file transfer status
     */
    private int mStatus;

    /**
     * The encryption type of the message content.
     */
    private int receiptStatus;

    /**
     * The encryption type of the message content.
     */
    private final int encryptionType;

    /**
     * A unique identifier for this message.
     */
    private final String messageUid;

    /**
     * The unique identifier of the last message that this message should replace,
     * or <code>null</code> if this is a new message.
     */
    private final String correctionUid;

    /**
     * The sent message stanza Id.
     */
    private final String mServerMsgId;

    /**
     * The received message stanza Id.
     */
    private final String mRemoteMsgId;

    /**
     * Field used to cache processed message body after replacements and corrections. This text is
     * used to display the message on the screen.
     */
    private String cachedOutput = null;

    /**
     * The file transfer OperationSet (event).
     */
    private final OperationSetFileTransfer opSet;

    /**
     * The Incoming file transfer request (event).
     */
    private final IncomingFileTransferRequest request;

    private HttpFileDownloadJabberImpl httpFileTransfer;

    /**
     * The file transfer history record.
     */
    private FileRecord fileRecord;

    /*
     * ChatMessageImpl with enclosed IMessage as content
     */
    public ChatMessageImpl(String sender, String senderName, Date date, int messageType, IMessage msg, String correctionUid, String direction) {
        this(sender, senderName, date, messageType, msg.getMimeType(), msg.getContent(),
                msg.getEncryptionType(), msg.getMessageUid(), correctionUid, direction,
                msg.getStatus(), msg.getReceiptStatus(), msg.getServerMsgId(), msg.getRemoteMsgId(), null, null, null);
    }

    /*
     * Default direction ot DIR_OUT, not actually being use in message except in file transfer
     */
    public ChatMessageImpl(String sender, String senderName, Date date, int messageType, int mimeType, String content, String messageUid, String direction) {
        this(sender, senderName, date, messageType, mimeType, content, IMessage.ENCRYPTION_NONE, messageUid, null, direction,
                STATUS_UNKNOWN, ChatMessage.MESSAGE_DELIVERY_NONE, "", "", null, null, null);
    }

    public ChatMessageImpl(String sender, Date date, int messageType, int mimeType, String content, String messageUid,
            String direction, OperationSetFileTransfer opSet, Object request, FileRecord fileRecord) {
        this(sender, sender, date, messageType, mimeType, content, IMessage.ENCRYPTION_NONE, messageUid, null, direction,
                STATUS_UNKNOWN, ChatMessage.MESSAGE_DELIVERY_NONE, null, null, opSet, request, fileRecord);
    }

    /**
     * Creates a <code>ChatMessageImpl</code> by specifying all parameters of the message.
     *
     * @param sender The string Id of the message sender.
     * @param senderName the sender display name
     * @param date the DateTimeStamp
     * @param messageType the type (INCOMING, OUTGOING, SYSTEM etc)
     * @param mimeType the content type of the message
     * @param content the message content
     * @param encryptionType the message original encryption type
     * @param messageUid The ID of the message.
     * @param correctionUid The ID of the message being replaced.
     * @param status The ChatMessage#STATUS_xxx or file transfer status.
     * @param receiptStatus The message delivery receipt status.
     * @param serverMsgId The sent message stanza Id.
     * @param remoteMsgId The received message stanza Id.
     * @param opSet The OperationSetFileTransfer.
     * @param request IncomingFileTransferRequest or HttpFileDownloadJabberImpl
     * @param fileRecord The history file record.
     */
    public ChatMessageImpl(String sender, String senderName, Date date, int messageType, int mimeType,
            String content, int encryptionType, String messageUid, String correctionUid,
            String direction, int status, int receiptStatus, String serverMsgId, String remoteMsgId,
            OperationSetFileTransfer opSet, Object request, FileRecord fileRecord) {
        this.mSender = sender;
        this.mSenderName = senderName;
        this.date = date;
        this.messageType = messageType;
        this.mimeType = mimeType;
        this.messageBody = content;
        this.encryptionType = encryptionType;
        this.messageUid = messageUid;
        this.correctionUid = correctionUid;
        this.mDirection = direction;

        this.mStatus = status;
        this.receiptStatus = receiptStatus;
        this.mServerMsgId = serverMsgId;
        this.mRemoteMsgId = remoteMsgId;

        this.fileRecord = fileRecord;
        this.opSet = opSet;

        if (request instanceof IncomingFileTransferRequest) {
            this.request = (IncomingFileTransferRequest) request;
            this.httpFileTransfer = null;
        }
        else {
            this.request = null;
            this.httpFileTransfer = (HttpFileDownloadJabberImpl) request;
        }
    }

    /**
     * Returns the UID of this message.
     *
     * @return the UID of this message.
     */
    @Override
    public String getMessageUid() {
        return messageUid;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String getUidForCorrection() {
        return messageUid;
    }

    /**
     * Returns the UID of the message that this message replaces, or <code>null</code> if this is a new message.
     *
     * @return the UID of the message that this message replaces, or <code>null</code> if this is a new message.
     */
    @Override
    public String getCorrectedMessageUid() {
        return correctionUid;
    }

    /**
     * Returns the server message Id of the message sent - for tracking delivery receipt
     *
     * @return the server message Id of the message sent.
     */
    @Override
    public String getServerMsgId() {
        return mServerMsgId;
    }

    /**
     * Returns the remote message Id of the message received - for tracking delivery receipt
     *
     * @return the remote message Id of the message received.
     */
    public String getRemoteMsgId() {
        return mRemoteMsgId;
    }

    /**
     * Returns the name of the entity sending the message.
     *
     * @return the name of the entity sending the message.
     */
    @Override
    public String getSender() {
        return mSender;
    }

    /**
     * Returns the display name of the entity sending the message.
     *
     * @return the display name of the entity sending the message
     */
    @Override
    public String getSenderName() {
        return mSenderName;
    }

    /**
     * Returns the date and time of the message.
     *
     * @return the date and time of the message.
     */
    @Override
    public Date getDate() {
        return date;
    }

    /**
     * Returns the type of the message.
     *
     * @return the type of the message.
     */
    @Override
    public int getMessageType() {
        return messageType;
    }

    /**
     * Set the type of the message.
     */
    public void setMessageType(int messageType) {
        this.messageType = messageType;
    }

    /**
     * Returns the mime type of the content type (e.g. "text", "text/html", etc.).
     *
     * @return the mimeType
     */
    @Override
    public int getMimeType() {
        return mimeType;
    }

    /**
     * Returns the content of the message or cached output; tagged with message status icon.
     *
     * @return the content of the message.
     */
    @Override
    public String getMessageBody() {
        if (cachedOutput != null)
            return cachedOutput;

        if (messageBody == null)
            return null;

        String output = messageBody;
        // Escape HTML content - seems not necessary for android OS (getMimeType() can be null)
        if (IMessage.ENCODE_HTML != getMimeType()) {
            output = StringEscapeUtils.escapeHtml4(messageBody);
        }
        // Process replacements (cmeng - just do a direct unicode conversion for std emojis)
        output = StringEscapeUtils.unescapeXml(output);

        // Apply the "edited at" tag for corrected message
        if (ChatMessage.STATUS_EDITED == mStatus || correctionUid != null) {
            output = "&#x270E " + output;
        }
        else if (ChatMessage.STATUS_DELETED == mStatus) {
            output = "&#x2612 " + output;
        }
        cachedOutput = output;
        return cachedOutput;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String getMessageContent() {
        return messageBody;
    }

    /**
     * Set the message content; clear cachedOutput so getMessageBody will regenerated content.
     *
     * @param content message content to be overwritten
     */
    public void setMessageContent(String content) {
        messageBody = content;
        cachedOutput = null;
    }

    /**
     * Returns the encryption type of the original received message.
     *
     * @return the encryption type
     */
    @Override
    public int getEncryptionType() {
        return encryptionType;
    }

    @Override
    public int getStatus() {
        return mStatus;
    }

    public void setStatus(int status) {
        mStatus = status;
    }

    /**
     * Returns the message delivery receipt status
     *
     * @return the receipt status
     */
    public int getReceiptStatus() {
        return receiptStatus;
    }

    public void setReceiptStatus(int status) {
        receiptStatus = status;
    }

    /**
     * Returns the message direction i.e. in/put.
     *
     * @return the direction of this message.
     */
    @Override
    public String getMessageDir() {
        return mDirection;
    }

    /**
     * Update file transfer status for the message with the specified msgUuid.
     *
     * @param msgUuid Message uuid
     * @param status Status of the fileTransfer
     */
    public void updateFTStatus(String msgUuid, int status) {
        if (messageUid.equals(msgUuid)) {
            mStatus = status;
        }
    }

    /**
     * Update file transfer status and FileRecord for the message with the specified msgUuid.
     *
     * @param descriptor The recipient
     * @param msgUuid Message uuid also use as FileRecord id
     * @param status Status of the fileTransfer
     * @param fileName FileName
     * @param encType Message encode Type
     * @param recordType ChatMessage#Type
     * @param dir File received or send
     *
     * @return True if found the a matching msgUuid for update
     */
    public boolean updateFTStatus(Object descriptor, String msgUuid, int status, String fileName, int encType, int recordType, String dir) {
        if (messageUid.equals(msgUuid)) {
            mStatus = status;
            messageType = recordType;

            // Require to create new if (fileName != null) to update both filePath and mXferStatus
            if (!TextUtils.isEmpty(fileName)) {
                Object entityJid;
                if (descriptor instanceof ChatRoomWrapper)
                    entityJid = ((ChatRoomWrapper) descriptor).getChatRoom();
                else
                    entityJid = ((MetaContact) descriptor).getDefaultContact();

                fileRecord = new FileRecord(msgUuid, entityJid, dir, date, new File(fileName), encType, mStatus);
            }
            // Timber.d("Updated ChatMessage Uid: %s (%s); status: %s => FR: %s", msgUuid, dir, status, fileRecord);
            return true;
        }
        return false;
    }

    /**
     * Returns the OperationSetFileTransfer of this message.
     *
     * @return the OperationSetFileTransfer of this message.
     */
    @Override
    public OperationSetFileTransfer getOpSet() {
        return this.opSet;
    }

    /**
     * Returns the IncomingFileTransferRequest of this message.
     *
     * @return the IncomingFileTransferRequest of this message.
     */
    @Override
    public IncomingFileTransferRequest getFTRequest() {
        return this.request;
    }

    /**
     * Returns the HttpFileDownloadJabberImpl of this message.
     *
     * @return the HttpFileDownloadJabberImpl of this message.
     */
    @Override
    public HttpFileDownloadJabberImpl getHttpFileTransfer() {
        return httpFileTransfer;
    }

    public void setHttpFileTransfer(HttpFileDownloadJabberImpl httpFileTransfer) {
        this.httpFileTransfer = httpFileTransfer;
    }

    /**
     * Returns the fileRecord of this message.
     *
     * @return the fileRecord of this message.
     */
    @Override
    public FileRecord getFileRecord() {
        return fileRecord;
    }

    static public ChatMessageImpl getMsgForEvent(MessageDeliveredEvent evt) {
        final IMessage imessage = evt.getSourceMessage();
        final String sender = evt.getContact().getProtocolProvider().getAccountID().getAccountJid();
        final String senderName = evt.getSender().isEmpty() ? sender : evt.getSender();

        return new ChatMessageImpl(sender, senderName, evt.getTimestamp(),
                ChatMessage.MESSAGE_OUT, imessage, evt.getCorrectedMessageUid(), ChatMessage.DIR_OUT);
    }

    static public ChatMessageImpl getMsgForEvent(final MessageReceivedEvent evt) {
        final IMessage imessage = evt.getSourceMessage();
        final Contact contact = evt.getSourceContact();
        final String sender = !evt.getSender().isEmpty() ? evt.getSender()
                : AppGUIActivator.getContactListService().findMetaContactByContact(contact).getDisplayName();

        return new ChatMessageImpl(contact.getAddress(), sender,
                evt.getTimestamp(), evt.getEventType(), imessage, evt.getCorrectedMessageUid(), ChatMessage.DIR_IN);
    }

    static public ChatMessageImpl getMsgForEvent(final ChatRoomMessageDeliveredEvent evt) {
        final IMessage imessage = evt.getMessage();
        String chatRoom = evt.getSourceChatRoom().getName();

        return new ChatMessageImpl(chatRoom, chatRoom, evt.getTimestamp(),
                ChatMessage.MESSAGE_MUC_OUT, imessage, null, ChatMessage.DIR_OUT);
    }

    static public ChatMessageImpl getMsgForEvent(final ChatRoomMessageReceivedEvent evt) {
        final IMessage imessage = evt.getMessage();
        String nickName = evt.getSourceChatRoomMember().getNickName();
        String contact = evt.getSourceChatRoomMember().getContactAddress();

        return new ChatMessageImpl(nickName, contact, evt.getTimestamp(),
                evt.getEventType(), imessage, null, ChatMessage.DIR_IN);
    }

    static public ChatMessageImpl getMsgForEvent(final FileRecord fileRecord) {
        return new ChatMessageImpl(fileRecord.getJidAddress(), fileRecord.getDate(),
                ChatMessage.MESSAGE_FILE_TRANSFER_HISTORY, IMessage.ENCODE_PLAIN, null,
                fileRecord.getID(), fileRecord.getDirection(), null, null, fileRecord);
    }
}

