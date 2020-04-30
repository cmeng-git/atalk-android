/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.atalk.android.gui.chat;

import android.text.TextUtils;

import net.java.sip.communicator.impl.protocol.jabber.HttpFileDownloadJabberImpl;
import net.java.sip.communicator.service.contactlist.MetaContact;
import net.java.sip.communicator.service.filehistory.FileRecord;
import net.java.sip.communicator.service.protocol.*;
import net.java.sip.communicator.service.protocol.event.*;

import org.apache.commons.text.StringEscapeUtils;
import org.atalk.android.R;
import org.atalk.android.aTalkApp;
import org.atalk.android.gui.AndroidGUIActivator;

import java.util.Date;

import timber.log.Timber;

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
     * The string Id of the message sender. The value is used in quoted messages.
     * Actual value pending message type i.e.:
     * userId: delivered chat message e.g. swordfish@atalk.org
     * contactId: received chat message e.g. leopoard@atalk.org
     * chatRoom: delivered group chat message e.g. conference@atalk.org
     * nickName: received group chat message e.g. leopard
     *
     * Exception as recipient:
     * contactId: ChatMessage.MESSAGE_FILE_TRANSFER_SEND & ChatMessage.MESSAGE_STICKER_SEND:
     */
    private String mSender;

    /**
     * The display name of the message sender. It may be the same as sender
     */
    private String mSenderName;

    /**
     * The date and time of the message.
     */
    private Date date;

    /**
     * The type of the message.
     */
    private int messageType;

    /**
     * The mime type of the message content.
     */
    private int mimeType;

    /**
     * The content of the message.
     */
    private String message;

    /**
     * The HTTP file download file transfer status
     */
    private int mXferStatus;

    /**
     * The encryption type of the message content.
     */
    private int receiptStatus;

    /**
     * The encryption type of the message content.
     */
    private int encryptionType;

    /**
     * A unique identifier for this message.
     */
    private String messageUID;

    /**
     * The unique identifier of the last message that this message should replace,
     * or <tt>null</tt> if this is a new message.
     */
    private String correctedMessageUID;

    /**
     * The sent message stanza Id.
     */
    private String mServerMsgId;

    /**
     * The received message stanza Id.
     */
    private String mRemoteMsgId;

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

    private HttpFileDownloadJabberImpl httpFileTransfer;

    /**
     * The file transfer history record.
     */
    private FileRecord fileRecord;

    public ChatMessageImpl(String sender, String senderName, Date date, int messageType, int mimeType,
            String content, String messageUID)
    {
        this(sender, senderName, date, messageType, mimeType, content, IMessage.ENCRYPTION_NONE, messageUID, null,
                FileRecord.STATUS_UNKNOWN, ChatMessage.MESSAGE_DELIVERY_NONE, "", "", null, null, null);
    }

    public ChatMessageImpl(String sender, String senderName, Date date, int messageType, IMessage msg, String correctedMessageUID)
    {
        this(sender, senderName, date, messageType, msg.getMimeType(), msg.getContent(),
                msg.getEncryptionType(), msg.getMessageUID(), correctedMessageUID, msg.getXferStatus(),
                msg.getReceiptStatus(), msg.getServerMsgId(), msg.getRemoteMsgId(), null, null, null);
    }

    public ChatMessageImpl(String sender, Date date, int messageType, int mimeType, String content,
            String messageUID, OperationSetFileTransfer opSet, Object request, FileRecord fileRecord)
    {
        this(sender, sender, date, messageType, mimeType, content, IMessage.ENCRYPTION_NONE, messageUID, null,
                FileRecord.STATUS_UNKNOWN, ChatMessage.MESSAGE_DELIVERY_NONE, null, null, opSet, request, fileRecord);
    }

    /**
     * Creates a <tt>ChatMessageImpl</tt> by specifying all parameters of the message.
     *
     * @param sender The string Id of the message sender.
     * @param senderName the sender display name
     * @param date the DateTimeStamp
     * @param messageType the type (INCOMING, OUTGOING, SYSTEM etc)
     * @param mimeType the content type of the message
     * @param content the message content
     * @param encryptionType the message original encryption type
     * @param messageUID The ID of the message.
     * @param correctedMessageUID The ID of the message being replaced.
     * @param xferStatus The file transfer status.
     * @param receiptStatus The message delivery receipt status.
     * @param serverMsgId The sent message stanza Id.
     * @param remoteMsgId The received message stanza Id.
     * @param opSet The OperationSetFileTransfer.
     * @param request IncomingFileTransferRequest or HttpFileDownloadJabberImpl
     * @param fileRecord The history file record.
     */
    public ChatMessageImpl(String sender, String senderName, Date date, int messageType, int mimeType,
            String content, int encryptionType, String messageUID, String correctedMessageUID,
            int xferStatus, int receiptStatus, String serverMsgId, String remoteMsgId,
            OperationSetFileTransfer opSet, Object request, FileRecord fileRecord)
    {
        this.mSender = sender;
        this.mSenderName = senderName;
        this.date = date;
        this.messageType = messageType;
        this.mimeType = mimeType;
        this.message = content;
        this.encryptionType = encryptionType;
        this.messageUID = messageUID;
        this.correctedMessageUID = correctedMessageUID;

        this.mXferStatus = xferStatus;
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

    public void updateCachedOutput(String msg)
    {
        this.cachedOutput = msg;
    }

    /**
     * Returns the name of the entity sending the message.
     *
     * @return the name of the entity sending the message.
     */
    @Override
    public String getSender()
    {
        return mSender;
    }

    /**
     * Returns the display name of the entity sending the message.
     *
     * @return the display name of the entity sending the message
     */
    @Override
    public String getSenderName()
    {
        return mSenderName;
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
     * Returns the mime type of the content type (e.g. "text", "text/html", etc.).
     *
     * @return the mimeType
     */
    @Override
    public int getMimeType()
    {
        return mimeType;
    }

    /**
     * Returns the content of the message or cached output if it is an correction message
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
        // Escape HTML content -  seems not necessary for android OS (getMimeType() can be null)
        if (IMessage.ENCODE_HTML != getMimeType()) {
            output = StringEscapeUtils.escapeHtml4(message);
        }
        // Process replacements (cmeng - just do a direct unicode conversion for std emojis)
        output = StringEscapeUtils.unescapeXml(output);

        // Apply the "edited at" tag for corrected message
        if (correctedMessageUID != null) {
            String editStr = aTalkApp.getResString(R.string.service_gui_EDITED);
            output = String.format("<i>%s <small><font color='#989898'>(%s)</font></small></i>", output, editStr);
        }
        cachedOutput = output;
        return cachedOutput;
    }

    /**
     * Returns the encryption type of the original received message.
     *
     * @return the encryption type
     */
    @Override
    public int getEncryptionType()
    {
        return encryptionType;
    }

    @Override
    public int getXferStatus()
    {
        return mXferStatus;
    }

    /**
     * Returns the message delivery receipt status
     *
     * @return the receipt status
     */
    public int getReceiptStatus()
    {
        return receiptStatus;
    }

    public void setReceiptStatus(int status)
    {
        receiptStatus = status;
    }

    /**
     * Returns the server message Id of the message sent - for tracking delivery receipt
     *
     * @return the server message Id of the message sent.
     */
    @Override
    public String getServerMsgId()
    {
        return mServerMsgId;
    }

    /**
     * Returns the remote message Id of the message received - for tracking delivery receipt
     *
     * @return the remote message Id of the message received.
     */
    public String getRemoteMsgId()
    {
        return mRemoteMsgId;
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
     * Returns the HttpFileDownloadJabberImpl of this message.
     *
     * @return the HttpFileDownloadJabberImpl of this message.
     */
    @Override
    public HttpFileDownloadJabberImpl getHttpFileTransfer()
    {
        return httpFileTransfer;
    }

    public void setHttpFileTransfer(HttpFileDownloadJabberImpl httpFileTransfer)
    {
        this.httpFileTransfer = httpFileTransfer;
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
     * Returns the UID of the message that this message replaces, or <tt>null</tt> if this is a new message.
     *
     * @return the UID of the message that this message replaces, or <tt>null</tt> if this is a new message.
     */
    public String getCorrectedMessageUID()
    {
        return correctedMessageUID;
    }

    /**
     * Indicates if the given <tt>nextMsg</tt> should be considered as consecutive message;
     * checking it against the previous message type and the next message.
     *
     * @param nextMsg the next message to check
     * @return <tt>true</tt> if the given message is a consecutive message, <tt>false</tt> - otherwise
     */
    public boolean isConsecutiveMessage(ChatMessage nextMsg)
    {
        if (nextMsg == null)
            return false;

        boolean isNonEmpty = !TextUtils.isEmpty(message);

        // Same UID specified i.e. corrected message
        boolean isCorrectionMessage = ((messageUID != null) && messageUID.equals(nextMsg.getCorrectedMessageUID()));

        // FTRequest message always treated as non-consecutiveMessage
        boolean isFTMsg = (messageType == MESSAGE_FILE_TRANSFER_RECEIVE)
                || (messageType == MESSAGE_FILE_TRANSFER_SEND)
                || (messageType == MESSAGE_STICKER_SEND);

        boolean isHttpFTMsg = isNonEmpty && message.matches("(?s)^aesgcm:.*|^http[s].*");

        boolean isMarkUpText = isNonEmpty && message.matches("(?s).*?<[A-Za-z]+>.*?</[A-Za-z]+>.*?");

        // New LatLng message always treated as non-consecutiveMessage
        boolean isLatLng = isNonEmpty && message.contains("LatLng:");

        // system message always treated as non-consecutiveMessage
        boolean isSystemMsg = (messageType == MESSAGE_SYSTEM) || (messageType == MESSAGE_ERROR);

        // Same message type and from the same contactName
        boolean isJidSame = ((mSender != null)
                && (messageType == nextMsg.getMessageType())
                && mSender.equals(nextMsg.getSender()));

        // same message encryption type
        boolean isEncTypeSame = (encryptionType == nextMsg.getEncryptionType());

        // true if the new message is within a minute from the last one
        boolean inElapseTime = ((nextMsg.getDate().getTime() - getDate().getTime()) < 60000);

        return isCorrectionMessage
                || (!(isNonMerge(nextMsg) || isFTMsg || isHttpFTMsg || isMarkUpText || isLatLng || isSystemMsg)
                && (isEncTypeSame && isJidSame && inElapseTime));
    }

    /**
     * Check the given ChatMessage to ascertain if it should always be treated as non-consecutiveMessage
     *
     * @param chatMessage ChatMessage to check
     * @return true if non-consecutiveMessage
     */
    private boolean isNonMerge(ChatMessage chatMessage)
    {
        int msgType = chatMessage.getMessageType();
        String bodyText = chatMessage.getMessage();
        boolean isNonEmpty = !TextUtils.isEmpty(bodyText);

        // FT  messages always treated as non-consecutiveMessage
        boolean isFTMsg = ((msgType == MESSAGE_FILE_TRANSFER_RECEIVE)
                || (msgType == MESSAGE_FILE_TRANSFER_SEND) || (msgType == MESSAGE_STICKER_SEND));

        boolean isHttpFTMsg = isNonEmpty && bodyText.matches("(?s)^aesgcm:.*|^http[s].*");

        // XHTML markup message always treated as non-consecutiveMessage
        boolean isMarkUpText = isNonEmpty && bodyText.matches("(?s).*?<[A-Za-z]+>.*?</[A-Za-z]+>.*?");

        // LatLng message always treated as non-consecutiveMessage
        boolean isLatLng = isNonEmpty && bodyText.contains("LatLng:");

        // system message always treated as non-consecutiveMessage
        boolean isSystemMsg = (msgType == MESSAGE_SYSTEM) || (msgType == MESSAGE_ERROR);

        return (isFTMsg || isHttpFTMsg || isMarkUpText || isLatLng || isSystemMsg);
    }

    static public ChatMessageImpl getMsgForEvent(MessageDeliveredEvent evt)
    {
        final String sender = evt.getDestinationContact().getProtocolProvider().getAccountID().getAccountJid();
        final IMessage message = evt.getSourceMessage();

        return new ChatMessageImpl(sender, sender, evt.getTimestamp(),
                ChatMessage.MESSAGE_OUT, message, evt.getCorrectedMessageUID());
    }

    static public ChatMessageImpl getMsgForEvent(final MessageReceivedEvent evt)
    {
        final IMessage message = evt.getSourceMessage();
        final Contact contact = evt.getSourceContact();
        final MetaContact metaContact
                = AndroidGUIActivator.getContactListService().findMetaContactByContact(contact);

        return new ChatMessageImpl(contact.getAddress(), metaContact.getDisplayName(),
                evt.getTimestamp(), evt.getEventType(), message, evt.getCorrectedMessageUID());
    }

    static public ChatMessageImpl getMsgForEvent(final ChatRoomMessageDeliveredEvent evt)
    {
        final IMessage message = evt.getMessage();
        String chatRoom = evt.getSourceChatRoom().getName();

        return new ChatMessageImpl(chatRoom, chatRoom, evt.getTimestamp(),
                ChatMessage.MESSAGE_MUC_OUT, message, null);
    }

    static public ChatMessageImpl getMsgForEvent(final ChatRoomMessageReceivedEvent evt)
    {
        final IMessage message = evt.getMessage();
        String nickName = evt.getSourceChatRoomMember().getNickName();
        // Extract the contact with the resource part
        String contact = evt.getSourceChatRoomMember().getContactAddress(); //.split("/")[0];

        return new ChatMessageImpl(nickName, nickName, evt.getTimestamp(), evt.getEventType(), message, null);
    }

    static public ChatMessageImpl getMsgForEvent(final FileRecord fileRecord)
    {
        return new ChatMessageImpl(fileRecord.getJidAddress(), fileRecord.getDate(),
                ChatMessage.MESSAGE_FILE_TRANSFER_HISTORY, IMessage.ENCODE_PLAIN, null,
                fileRecord.getID(), null, null, fileRecord);
    }

    /**
     * Returns the account user display name for the given protocol provider.
     *
     * @param protocolProvider the protocol provider corresponding to the account to add
     * @return The account user display name for the given protocol provider.
     */
    private static String getAccountDisplayName(ProtocolProviderService protocolProvider)
    {
        // Get displayName from OperationSetServerStoredAccountInfo need account to be login in
        if (((protocolProvider == null) || !protocolProvider.isRegistered())) {
            return protocolProvider.getAccountID().getDisplayName();
        }

        final OperationSetServerStoredAccountInfo accountInfoOpSet
                = protocolProvider.getOperationSet(OperationSetServerStoredAccountInfo.class);
        try {
            if (accountInfoOpSet != null) {
                String displayName = AccountInfoUtils.getDisplayName(accountInfoOpSet);
                if (displayName != null && displayName.length() > 0)
                    return displayName;
            }
        } catch (Exception e) {
            Timber.w("Cannot obtain display name through OPSet");
        }
        return protocolProvider.getAccountID().getDisplayName();
    }
}

