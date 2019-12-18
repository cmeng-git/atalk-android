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
import net.java.sip.communicator.util.GuiUtils;

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
     * The mime type of the message content.
     */
    private int mimeType;

    /**
     * The content of the message.
     */
    private String message;

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
     * The unique identifier of the message that this message should replace, or <tt>null</tt> if
     * this is a new message.
     */
    private String correctedMessageUID;

    private String mServerMsgId;
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

    public ChatMessageImpl(String contactName, String displayName, Date date, int messageType, int mimeType, String content)
    {
        this(contactName, displayName, date, messageType, mimeType, content,
                IMessage.ENCRYPTION_NONE, null, null,
                ChatMessage.MESSAGE_DELIVERY_NONE, "", "",
                null, null, null);
    }


    public ChatMessageImpl(String contactName, String displayName, Date date, int messageType, IMessage msg,
            String correctedMessageUID)
    {
        this(contactName, displayName, date, messageType, msg.getMimeType(), msg.getContent(),
                msg.getEncryptionType(), msg.getMessageUID(), correctedMessageUID,
                msg.getReceiptStatus(), msg.getServerMsgId(), msg.getRemoteMsgId(),
                null, null, null);
    }

    public ChatMessageImpl(String contactName, Date date, int messageType, int mimeType, String content,
            String messageUID, OperationSetFileTransfer opSet, Object request, FileRecord fileRecord)
    {
        this(contactName, contactName, date, messageType, mimeType, content,
                IMessage.ENCRYPTION_NONE, messageUID, null,
                ChatMessage.MESSAGE_DELIVERY_NONE, null, null,
                opSet, request, fileRecord);
    }

    /**
     * Creates a <tt>ChatMessageImpl</tt> by specifying all parameters of the message.
     *
     * @param contactName the name of the contact
     * @param contactDisplayName the contact display name
     * @param date the DateTimeStamp
     * @param messageType the type (INCOMING, OUTGOING, SYSTEM etc)
     * @param mimeType the content type of the message
     * @param content the message content
     * @param encryptionType the message original encryption type
     * @param messageUID The ID of the message.
     * @param correctedMessageUID The ID of the message being replaced.
     * @param receiptStatus The message delivery receipt status.
     * @param serverMsgId The sent message stanza Id.
     * @param remoteMsgId The received message stanza Id.
     * @param opSet The OperationSetFileTransfer.
     * @param request IncomingFileTransferRequest or HttpFileDownloadJabberImpl
     * @param fileRecord The history file record.
     */

    public ChatMessageImpl(String contactName, String contactDisplayName, Date date, int messageType, int mimeType,
            String content, int encryptionType, String messageUID, String correctedMessageUID,
            int receiptStatus, String serverMsgId, String remoteMsgId,
            OperationSetFileTransfer opSet, Object request, FileRecord fileRecord)
    {
        this.contactName = contactName;
        this.contactDisplayName = contactDisplayName;
        this.date = date;
        this.messageType = messageType;
        this.mimeType = mimeType;
        this.message = content;
        this.encryptionType = encryptionType;
        this.messageUID = messageUID;
        this.correctedMessageUID = correctedMessageUID;

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
        // Escape HTML content -  seems not necessary for android OS (getMimeType() can be null)
        if (IMessage.ENCODE_HTML != getMimeType()) {
            output = StringEscapeUtils.escapeHtml4(message);
        }
        // Process replacements (cmeng - just do a direct unicode conversion for std emojis)
        output = StringEscapeUtils.unescapeXml(output);

        // Apply the "edited at" tag for corrected message
        if (correctedMessageUID != null) {
            String editedStr = aTalkApp.getResString(R.string.service_gui_EDITED_AT, GuiUtils.formatTime(getDate()));
            output = "<i>" + output + "  <font color=\"#989898\" >(" + editedStr + ")</font></i>";
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

    /**
     * Returns the message delivery receipt status
     *
     * @return the receipt status
     */
    public int getReceiptStatus()
    {
        return receiptStatus;
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
     * Indicates if given <tt>nextMsg</tt> is a consecutive message.
     *
     * @param nextMsg the next message to check
     * @return <tt>true</tt> if the given message is a consecutive message, <tt>false</tt> - otherwise
     */
    public boolean isConsecutiveMessage(ChatMessage nextMsg)
    {
        if (nextMsg == null)
            return false;

        String bodyText = nextMsg.getMessage();

        // New FTRequest message always treated as non-consecutiveMessage
        boolean nonFTMsg = ((messageType != MESSAGE_FILE_TRANSFER_RECEIVE)
                && (messageType != MESSAGE_FILE_TRANSFER_SEND) && (messageType != MESSAGE_STICKER_SEND));
        // New system message always treated as non-consecutiveMessage
        boolean nonHttpFTMsg = (bodyText == null)
                || !bodyText.matches("(?s)^aesgcm:.*|^http[s].*");
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
        // same message encryption type
        boolean encTypeSame = (encryptionType == nextMsg.getEncryptionType());
        // true if the new message is within a minute from the last one
        boolean inElapseTime = ((nextMsg.getDate().getTime() - getDate().getTime()) < 60000);
        boolean isMarkUpText = (bodyText != null) && bodyText.matches("(?s).*?<[A-Za-z]+>.*?</[A-Za-z]+>.*?");

        return (nonFTMsg && nonSystemMsg && nonLatLng && encTypeSame && nonHttpFTMsg && !isMarkUpText
                && (uidEqual || (mTypeJidEqual && inElapseTime)));
    }

    static public ChatMessageImpl getMsgForEvent(MessageDeliveredEvent evt)
    {
        final Contact contact = evt.getDestinationContact();
        final IMessage message = evt.getSourceMessage();

        return new ChatMessageImpl(contact.getProtocolProvider().getAccountID().getAccountJid(),
                getAccountDisplayName(contact.getProtocolProvider()), evt.getTimestamp(),
                ChatMessage.MESSAGE_OUT, message, evt.getCorrectedMessageUID());
    }

    static public ChatMessageImpl getMsgForEvent(final MessageReceivedEvent evt)
    {
        final IMessage message = evt.getSourceMessage();
        final Contact protocolContact = evt.getSourceContact();
        final MetaContact metaContact
                = AndroidGUIActivator.getContactListService().findMetaContactByContact(protocolContact);

        return new ChatMessageImpl(protocolContact.getAddress(), metaContact.getDisplayName(),
                evt.getTimestamp(), evt.getEventType(), message, evt.getCorrectedMessageUID());
    }

    static public ChatMessageImpl getMsgForEvent(final ChatRoomMessageDeliveredEvent evt)
    {
        ChatRoom chatRoom = evt.getSourceChatRoom();
        final IMessage message = evt.getMessage();

        return new ChatMessageImpl(chatRoom.toString(), chatRoom.getName(), evt.getTimestamp(),
                ChatMessage.MESSAGE_MUC_OUT, message, null);
    }

    static public ChatMessageImpl getMsgForEvent(final ChatRoomMessageReceivedEvent evt)
    {
        String nickName = evt.getSourceChatRoomMember().getNickName();
        // Extract the contact with the resource part
        String contact = evt.getSourceChatRoomMember().getContactAddress(); //.split("/")[0];

        final IMessage message = evt.getMessage();
        return new ChatMessageImpl(contact, nickName, evt.getTimestamp(), evt.getEventType(), message, null);
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

