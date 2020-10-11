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

import java.util.*;

/**
 * Class merges consecutive <tt>ChatMessage</tt> instances.
 *
 * @author Pawel Domas
 * @author Eng Chong Meng
 */
public class MergedMessage implements ChatMessage
{
    /**
     * Root message instance.
     */
    private final ChatMessage rootMessage;

    /**
     * The list of messages consecutive to this <tt>MergedMessage</tt>.
     */
    private final List<ChatMessage> children = new ArrayList<>();

    /**
     * The message date(updated with each merge).
     */
    private Date date;

    /**
     * Variable used to cache merged message content.
     */
    private String mergedMessage;

    /**
     * Variable used to cache merged message Ids.
     */
    private String serverMsgIds;

    /**
     * Creates new instance of <tt>MergedMessage</tt> where the given message will become its
     * rootMessage on which other new messages are being merged.
     *
     * @param rootMsg the rootMessage message for this merged instance.
     */
    public MergedMessage(ChatMessage rootMsg)
    {
        this.rootMessage = rootMsg;
        date = rootMsg.getDate();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String getSender()
    {
        return rootMessage.getSender();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String getSenderName()
    {
        return rootMessage.getSenderName();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Date getDate()
    {
        return date;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int getMessageType()
    {
        return rootMessage.getMessageType();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int getMimeType()
    {
        return rootMessage.getMimeType();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int getEncryptionType()
    {
        return rootMessage.getEncryptionType();
    }

    @Override
    public int getXferStatus()
    {
        return FileRecord.STATUS_UNKNOWN;
    }

    /**
     * Returns the merged message lowest delivery receipt status
     *
     * @return the receipt status
     */
    public int getReceiptStatus()
    {
        int receiptStatus = rootMessage.getReceiptStatus();
        for (ChatMessage ch : children) {
            if (ch.getReceiptStatus() < receiptStatus)
                receiptStatus = ch.getReceiptStatus();
        }
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
        /*
         * Variable used to cache merged message Ids.
         */
        serverMsgIds = rootMessage.getServerMsgId();

        // Merge the server message Ids
        for (ChatMessage ch : children) {
            serverMsgIds = mergeText(serverMsgIds, ch.getServerMsgId());
        }
        return serverMsgIds;
    }

    /**
     * Returns the remote message Id of the message received - for tracking delivery receipt
     *
     * @return the remote message Id of the message received.
     */
    public String getRemoteMsgId()
    {
        String remoteMsgId = rootMessage.getRemoteMsgId();

        // Merge the remote server message Ids
        for (ChatMessage ch : children) {
            remoteMsgId = mergeText(remoteMsgId, ch.getRemoteMsgId());
        }
        return remoteMsgId;
    }

    /**
     * Returns the UID of this message.
     *
     * @return the UID of this message.
     */
    public String getMessageUID()
    {
        return rootMessage.getMessageUID();
    }

    /**
     * Returns a list of all UIDs of the rootMessage and its children.
     *
     * @return list of all UIDs of the rootMessage and its children.
     */
    public List<String> getMessageUIDs()
    {
        List<String> msgUuidList = new ArrayList<>();

        msgUuidList.add(rootMessage.getMessageUID());
        for (ChatMessage child : children) {
            msgUuidList.add(child.getMessageUID());
        }
        return msgUuidList;
    }

    /**
     * Returns the UID of the message that this message replaces, or <tt>null</tt> if this is a new message.
     *
     * @return the UID of the message that this message replaces, or <tt>null</tt> if this is a new message.
     */
    public String getCorrectedMessageUID()
    {
        return rootMessage.getCorrectedMessageUID();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public ChatMessage mergeMessage(ChatMessage consecutiveMessage)
    {
        ChatMessage corrected = findCorrectedMessage(consecutiveMessage);
        if (corrected == null) {
            children.add(consecutiveMessage);
            // Use the most recent date, as main date
            date = consecutiveMessage.getDate();
            // Append the text only if we have cached content, otherwise it will be lazily generated on content request
            if (mergedMessage != null) {
                mergedMessage = mergeText(mergedMessage, getMessageText(consecutiveMessage));
            }
        }
        else {
            // Merge chat message
            ChatMessage correctionResult = corrected.mergeMessage(consecutiveMessage);
            int correctedIdx = children.indexOf(corrected);
            children.set(correctedIdx, correctionResult);

            // Clear content cache
            mergedMessage = null;
        }
        return this;
    }

    /**
     * {@inheritDoc}
     * msgText = "&#x2611 &#x2713 &#x2612 &#x2716 &#x2717 &#x2718 &#x2715 " + msgText;
     */
    @Override
    public String getMessage()
    {
        if (mergedMessage == null) {
            mergedMessage = getMessageText(rootMessage);

            // Merge the child text to root Message
            for (ChatMessage chatMessage : children) {
                mergedMessage = mergeText(mergedMessage, getMessageText(chatMessage));
            }
        }
        return mergedMessage;
    }

    /**
     * Utility method used for merging message contents.
     *
     * @param chatMessage Chat message
     * @return merged message text
     */
    private static String getMessageText(ChatMessage chatMessage)
    {
        String msgText = chatMessage.getMessage();
        if (chatMessage.getMessageType() == MESSAGE_OUT) {
            msgText = getReceiptStatus(chatMessage) + msgText;
        }
        return msgText;
    }

    /**
     * Get the status display for the given message.
     *
     * @param receiptStatusView the encryption state view
     * @param deliveryStatus the encryption
     */
    private static String getReceiptStatus(ChatMessage chatMessage)
    {
        int reciptStatus = chatMessage.getReceiptStatus();
        switch (reciptStatus) {
            case ChatMessage.MESSAGE_DELIVERY_NONE:
                return "&#x2612 ";  // cross makr with square boundary
            case ChatMessage.MESSAGE_DELIVERY_RECEIPT:
                return ""; // "&#x2713 "; do not want to show anything for receipt message
            case ChatMessage.MESSAGE_DELIVERY_CLIENT_SENT:
                return "&#x2717 ";  // cross mark for message delivery to server
            case ChatMessage.MESSAGE_DELIVERY_SERVER_SENT:
                return "&#x2618 ";  // bold cross mark
        }
        return "";
    }

    /**
     * Utility method used for merging message contents.
     *
     * @param msg current message text
     * @param nextMsg next message text to merge
     * @return merged message text
     */
    private static String mergeText(String msg, String nextMsg)
    {
        return msg + " <br/>" + nextMsg;
    }

    public MergedMessage updateDeliveryStatus(String msgId, int status)
    {
        if (rootMessage.getServerMsgId().equals(msgId)) {
            ((ChatMessageImpl) rootMessage).setReceiptStatus(status);
        }
        else {
            for (int i = 0; i < children.size(); i++) {
                ChatMessage child = children.get(i);
                if (child.getServerMsgId().equals(msgId)) {
                    ((ChatMessageImpl) child).setReceiptStatus(status);
                    break;
                }
            }
        }
        // rebuild the mergeMessage
        mergedMessage = null;
        getMessage();
        return this;
    }

    /**
     * Returns the last child message if it has valid UID and content or the rootMessage message.
     *
     * @return the last child message if it has valid UID and content or the rootMessage message.
     */
    private ChatMessage getMessageForCorrection()
    {
        if (children.size() > 0) {
            ChatMessage candidate = children.get(children.size() - 1);
            if ((candidate.getUidForCorrection() != null)
                    && (candidate.getContentForCorrection() != null))
                return candidate;
        }
        return rootMessage;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String getUidForCorrection()
    {
        return getMessageForCorrection().getUidForCorrection();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String getContentForCorrection()
    {
        return getMessageForCorrection().getContentForCorrection();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String getContentForClipboard()
    {
        StringBuilder output = new StringBuilder(rootMessage.getContentForClipboard());
        for (ChatMessage c : children) {
            output.append("\n").append(c.getContentForClipboard());
        }
        return output.toString();
    }

    /**
     * Finds the message that should be corrected by given message instance.
     *
     * @param newMsg new message to check if it is a correction for any of merged messages.
     * @return message that is corrected by given <tt>newMsg</tt> or <tt>null</tt> if there isn't
     * any.
     */
    private ChatMessage findCorrectedMessage(ChatMessage newMsg)
    {
        for (ChatMessage msg : children) {
            String msgUID = msg.getMessageUID();
            if (msgUID == null) {
                continue;
            }
            if (msgUID.equals(newMsg.getCorrectedMessageUID())) {
                return msg;
            }
        }
        return null;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean isConsecutiveMessage(ChatMessage nextMsg)
    {
        return ((findCorrectedMessage(nextMsg) != null) || (rootMessage.isConsecutiveMessage(nextMsg)));
    }

    @Override
    public FileRecord getFileRecord()
    {
        return rootMessage.getFileRecord();
    }

    @Override
    public OperationSetFileTransfer getOpSet()
    {
        return rootMessage.getOpSet();
    }

    @Override
    public IncomingFileTransferRequest getFTRequest()
    {
        return rootMessage.getFTRequest();
    }

    /**
     * Returns the HttpFileDownloadJabberImpl of this message.
     *
     * @return the HttpFileDownloadJabberImpl of this message.
     */
    @Override
    public HttpFileDownloadJabberImpl getHttpFileTransfer()
    {
        return rootMessage.getHttpFileTransfer();
    }
}
