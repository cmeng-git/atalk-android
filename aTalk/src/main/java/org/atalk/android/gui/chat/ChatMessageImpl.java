/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 * 
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.atalk.android.gui.chat;

import android.text.*;

import net.java.sip.communicator.service.contactlist.MetaContact;
import net.java.sip.communicator.service.filehistory.FileRecord;
import net.java.sip.communicator.service.protocol.*;
import net.java.sip.communicator.service.protocol.event.*;
import net.java.sip.communicator.util.*;

import org.apache.commons.lang3.StringEscapeUtils;
import org.atalk.android.*;
import org.atalk.android.gui.AndroidGUIActivator;
import org.atalk.android.gui.util.Html;

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
    public static final HashMap<String, Integer> statusMap = new HashMap<String, Integer>()
    {{
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
     * @param contactName the name of the contact sending the message
     * @param date the time at which the message is sent or received
     * @param messageType the type (INCOMING or OUTGOING)
     * @param message the message content
     * @param encType the content type (e.g. "text", "text/html", etc.)
     */
    public ChatMessageImpl(String contactName, Date date, int messageType, String message, int encType)
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
     * @param contactName the name of the contact
     * @param date the date and time
     * @param messageType the type (INCOMING or OUTGOING)
     * @param messageTitle the title of the message
     * @param message the content
     * @param encType the content type (e.g. "text", "text/html", etc.)
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
     * @param contactName the name of the contact
     * @param contactDisplayName the contact display name
     * @param date the date and time
     * @param messageType the type (INCOMING or OUTGOING)
     * @param message the content
     * @param encType the content type (e.g. "text", "text/html", etc.)
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
     * @param contactName the name of the contact
     * @param contactDisplayName the contact display name
     * @param date the date and time
     * @param messageType the type (INCOMING or OUTGOING)
     * @param messageTitle the title of the message
     * @param message the content
     * @param encType the content type (e.g. "text", "text/html", etc.)
     * @param messageUID The ID of the message.
     * @param correctedMessageUID The ID of the message being replaced.
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
            String editedStr = aTalkApp.getResString(R.string.service_gui_EDITED_AT, GuiUtils.formatTime(getDate()));
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
     * @param nextMsg the next message to check
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
                evt.getTimestamp(), ChatMessage.MESSAGE_OUT, null,
                msg.getContent(), msg.getEncType(), msg.getMessageUID(),
                evt.getCorrectedMessageUID(), null, null, null);
    }

    static public ChatMessageImpl getMsgForEvent(final MessageReceivedEvent evt)
    {
        final Message message = evt.getSourceMessage();
        final Contact protocolContact = evt.getSourceContact();
        final MetaContact metaContact
                = AndroidGUIActivator.getContactListService().findMetaContactByContact(protocolContact);

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
            logger.warn("Cannot obtain display name through OPSet");
        }
        return protocolProvider.getAccountID().getDisplayName();
    }
}

