/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.atalk.android.gui.chat;

import android.text.TextUtils;

import net.java.sip.communicator.service.contactlist.MetaContact;
import net.java.sip.communicator.service.filehistory.FileRecord;
import net.java.sip.communicator.service.protocol.*;
import net.java.sip.communicator.service.protocol.event.*;
import net.java.sip.communicator.util.GuiUtils;

import org.apache.commons.lang3.StringEscapeUtils;
import org.atalk.android.R;
import org.atalk.android.aTalkApp;
import org.atalk.android.gui.AndroidGUIActivator;
import org.atalk.android.gui.util.Html;

import java.util.Date;
import java.util.HashMap;

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
    public static final HashMap<String, Integer> statusMap = new HashMap<String, Integer>()
    {{
        put(FileRecord.COMPLETED, STATUS_COMPLETED);
        put(FileRecord.FAILED, STATUS_FAILED);
        put(FileRecord.CANCELED, STATUS_CANCELED);
        put(FileRecord.REFUSED, STATUS_REFUSED);
        put(FileRecord.ACTIVE, STATUS_ACTIVE);
        put(FileRecord.PREPARING, STATUS_PREPARING);
        put(FileRecord.IN_PROGRESS, STATUS_IN_PROGRESS);
    }};

    /**
     * The file transfer history record.
     */
    private FileRecord fileRecord;

    /**
     * Creates a <tt>ChatMessageImpl</tt> by specifying all parameters of the message.
     *
     * @param contactName the name of the contact sending the message
     * @param date the time at which the message is sent or received
     * @param messageType the type (INCOMING or OUTGOING)
     * @param mimeType the mime type of the message content
     * @param message the message content
     * @param encryptionType the encryption type
     */
    public ChatMessageImpl(String contactName, Date date, int messageType, int mimeType, String message, int encryptionType)
    {
        this(contactName, null, date, messageType, mimeType, message, encryptionType,
                null, null, null, null, null);
    }

    public ChatMessageImpl(String contactName, Date date, int messageType, int mimeType, String message,
            OperationSetFileTransfer opSet, IncomingFileTransferRequest request)
    {
        this(contactName, null, date, messageType, mimeType, message, ChatMessage.ENCRYPTION_NONE,
                null, null, opSet, request, null);
    }

    public ChatMessageImpl(String contactName, Date date, int messageType, int mimeType, String message,
            String messageUID, FileRecord fileRecord)
    {
        this(contactName, null, date, messageType, mimeType, message, ChatMessage.ENCRYPTION_NONE,
                messageUID, null, null, null, fileRecord);
    }

    /**
     * Creates a <tt>ChatMessageImpl</tt> by specifying all parameters of the message.
     *
     * @param contactName the name of the contact
     * @param contactDisplayName the contact display name
     * @param date the date and time
     * @param messageType the type (INCOMING or OUTGOING)
     * @param message the content
     * @param encryptionType the content type (e.g. "text", "text/html", etc.)
     */
    public ChatMessageImpl(String contactName, String contactDisplayName, Date date, int messageType, int mimeType,
            String message, int encryptionType)
    {
        this(contactName, contactDisplayName, date, messageType, mimeType, message, encryptionType,
                null, null, null, null, null);
    }

    public ChatMessageImpl(String contactName, String contactDisplayName, Date date, int messageType,
            int mimeType, String message, int encryptionType, String messageUID, String correctedMessageUID)
    {
        this(contactName, contactDisplayName, date, messageType, mimeType, message, encryptionType,
                messageUID, correctedMessageUID, null, null, null);
    }

    /**
     * Creates a <tt>ChatMessageImpl</tt> by specifying all parameters of the message.
     *
     * @param contactName the name of the contact
     * @param contactDisplayName the contact display name
     * @param date the DateTimeStamp
     * @param messageType the type (INCOMING or OUTGOING)
     * @param mimeType the content type of the message
     * @param message the message content
     * @param encryptionType the message original encryption type
     * @param messageUID The ID of the message.
     * @param correctedMessageUID The ID of the message being replaced.
     */

    public ChatMessageImpl(String contactName, String contactDisplayName, Date date, int messageType, int mimeType,
            String message, int encryptionType, String messageUID, String correctedMessageUID,
            OperationSetFileTransfer opSet, IncomingFileTransferRequest request, FileRecord fileRecord)
    {
        this.contactName = contactName;
        this.contactDisplayName = contactDisplayName;
        this.date = date;
        this.messageType = messageType;
        this.mimeType = mimeType;
        this.message = message;
        this.encryptionType = encryptionType;
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
        // Escape HTML content (getMimeType() can be null)
        if (ChatMessage.ENCODE_HTML != getMimeType()) {
            output = Html.escapeHtml(output);
        }

        // Process replacements (cmeng - just do a direct unicode conversion for std emojis)
        // output = processReplacements(output);
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
        // New FTRequest message always treated as non-consecutiveMessage
        boolean nonFTMsg = ((messageType != MESSAGE_FILE_TRANSFER_RECEIVE)
                && (messageType != MESSAGE_FILE_TRANSFER_SEND) && (messageType != MESSAGE_STICKER_SEND));
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
        // same message encryption type
        boolean encTypeSame = (encryptionType == nextMsg.getEncryptionType());
        // true if the new message is within a minute from the last one
        boolean inElapseTime = ((nextMsg.getDate().getTime() - getDate().getTime()) < 60000);

        return (nonFTMsg && nonSystemMsg && nonLatLng && encTypeSame
                && (uidEqual || (mTypeJidEqual && inElapseTime)));
    }

    /**
     * Returns the message type corresponding to the given <tt>MessageReceivedEvent</tt>.
     *
     * @param evt the <tt>MessageReceivedEvent</tt>, that gives us information of the message type
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
                evt.getTimestamp(), ChatMessage.MESSAGE_OUT, msg.getMimeType(),
                msg.getContent(), msg.getEncryptionType(), msg.getMessageUID(),
                evt.getCorrectedMessageUID(), null, null, null);
    }

    static public ChatMessageImpl getMsgForEvent(final MessageReceivedEvent evt)
    {
        final Message message = evt.getSourceMessage();
        final Contact protocolContact = evt.getSourceContact();
        final MetaContact metaContact
                = AndroidGUIActivator.getContactListService().findMetaContactByContact(protocolContact);

        return new ChatMessageImpl(protocolContact.getAddress(), metaContact.getDisplayName(),
                evt.getTimestamp(), getMessageType(evt), message.getMimeType(),
                message.getContent(), message.getEncryptionType(), message.getMessageUID(),
                evt.getCorrectedMessageUID(), null, null, null);
    }

    static public ChatMessageImpl getMsgForEvent(final ChatRoomMessageDeliveredEvent evt)
    {
        ChatRoom chatRoom = evt.getSourceChatRoom();
        final Message message = evt.getMessage();

        return new ChatMessageImpl(chatRoom.toString(), chatRoom.getName(),
                evt.getTimestamp(), ChatMessage.MESSAGE_OUT, message.getMimeType(),
                message.getContent(), message.getEncryptionType(), message.getMessageUID(),
                null, null, null, null);
    }

    static public ChatMessageImpl getMsgForEvent(final ChatRoomMessageReceivedEvent evt)
    {
        String nickName = evt.getSourceChatRoomMember().getNickName();

        final Message message = evt.getMessage();
        return new ChatMessageImpl(nickName, nickName, evt.getTimestamp(),
                ChatMessage.MESSAGE_IN, message.getMimeType(), message.getContent(), message.getMimeType(),
                message.getMessageUID(), null, null, null, null);
    }

    static public ChatMessageImpl getMsgForEvent(final FileRecord fileRecord)
    {
        Contact protocolContact = fileRecord.getContact();

        return new ChatMessageImpl(protocolContact.getAddress(), fileRecord.getDate(),
                ChatMessage.MESSAGE_FILE_TRANSFER_HISTORY, ChatMessage.ENCODE_PLAIN, null,
                fileRecord.getID(), fileRecord);
    }

    /**
     * Returns the account user display name for the given protocol provider.
     *
     * @param protocolProvider the protocol provider corresponding to the account to add
     * @return The account user display name for the given protocol provider.
     */
    public static String getAccountDisplayName(ProtocolProviderService protocolProvider)
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

