/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.atalk.android.gui.chat.conference;

import net.java.sip.communicator.service.protocol.*;
import net.java.sip.communicator.service.protocol.event.FileTransferStatusChangeEvent;
import net.java.sip.communicator.service.protocol.event.MessageListener;

import org.atalk.android.R;
import org.atalk.android.aTalkApp;
import org.atalk.android.gui.chat.*;
import org.atalk.android.gui.chat.filetransfer.FileSendConversation;
import org.jivesoftware.smack.SmackException;
import org.jivesoftware.smack.XMPPException;
import org.jivesoftware.smackx.chatstates.ChatState;
import org.jivesoftware.smackx.httpfileupload.HttpFileUploadManager;
import org.jivesoftware.smackx.omemo.OmemoManager;
import org.jxmpp.jid.EntityBareJid;

import java.io.File;
import java.io.IOException;
import java.net.URL;

/**
 * The conference implementation of the <tt>ChatTransport</tt> interface that provides
 * abstraction to access to protocol providers.
 *
 * @author Valentin Martinet
 * @author Eng Chong Meng
 */
public class AdHocConferenceChatTransport implements ChatTransport
{
    private final ChatSession chatSession;

    private final AdHocChatRoom adHocChatRoom;
    private final ProtocolProviderService mPPS;
    private HttpFileUploadManager httpFileUploadManager;

    private static URL mURL = null;

    /**
     * Creates an instance of <tt>ConferenceChatTransport</tt> by specifying the parent chat
     * session and the ad-hoc chat room associated with this transport.
     *
     * @param chatSession the parent chat session.
     * @param chatRoom the ad-hoc chat room associated with this conference transport.
     */
    public AdHocConferenceChatTransport(ChatSession chatSession, AdHocChatRoom chatRoom)
    {
        this.chatSession = chatSession;
        this.adHocChatRoom = chatRoom;
        mPPS = adHocChatRoom.getParentProvider();
        httpFileUploadManager = HttpFileUploadManager.getInstanceFor(mPPS.getConnection());
    }

    /**
     * Returns the contact address corresponding to this chat transport.
     *
     * @return The contact address corresponding to this chat transport.
     */
    public String getName()
    {
        return adHocChatRoom.getName();
    }

    /**
     * Returns the display name corresponding to this chat transport.
     *
     * @return The display name corresponding to this chat transport.
     */
    public String getDisplayName()
    {
        return adHocChatRoom.getName();
    }

    /**
     * Returns the resource name of this chat transport. This is for example the name of the user
     * agent from which the contact is logged.
     *
     * @return The display name of this chat transport resource.
     */
    public String getResourceName()
    {
        return null;
    }

    /**
     * Indicates if the display name should only show the resource.
     *
     * @return <tt>true</tt> if the display name shows only the resource, <tt>false</tt> - otherwise
     */
    public boolean isDisplayResourceOnly()
    {
        return false;
    }

    /**
     * Returns the presence status of this transport.
     *
     * @return the presence status of this transport.
     */
    public PresenceStatus getStatus()
    {
        return null;
    }

    /**
     * Returns the <tt>ProtocolProviderService</tt>, corresponding to this chat transport.
     *
     * @return the <tt>ProtocolProviderService</tt>, corresponding to this chat transport.
     */
    public ProtocolProviderService getProtocolProvider()
    {
        return mPPS;
    }

    /**
     * Returns {@code true} if this chat transport supports instant messaging,
     * otherwise returns {@code false}.
     *
     * @return {@code true} if this chat transport supports instant messaging,
     * otherwise returns {@code false}.
     */
    public boolean allowsInstantMessage()
    {
        return true;
    }

    /**
     * Returns {@code true} if this chat transport supports sms messaging, otherwise returns {@code false}.
     *
     * @return {@code true} if this chat transport supports sms messaging, otherwise returns {@code false}.
     */
    public boolean allowsSmsMessage()
    {
        return false;
    }

    /**
     * Returns {@code true} if this chat transport supports message delivery receipts,
     * otherwise returns {@code false}.
     *
     * @return {@code true} if this chat transport supports message delivery receipts,
     * otherwise returns {@code false}
     */
    public boolean allowsMessageDeliveryReceipt()
    {
        return false;
    }

    /**
     * Returns {@code true} if this chat transport supports chat state notifications, otherwise returns {@code false}.
     *
     * @return {@code true} if this chat transport supports chat state notifications, otherwise returns {@code false}.
     */
    public boolean allowsChatStateNotifications()
    {
        Object tnOpSet = mPPS.getOperationSet(OperationSetChatStateNotifications.class);
        // isJoined as one of the condition???
        return tnOpSet != null;

    }

    /**
     * Sends the given instant message trough this chat transport, by specifying the mime type (html or plain text).
     *
     * @param messageText The message to send.
     * @param encType See IMessage for definition of encType e.g. Encryption, encode & remoteOnly
     */
    public void sendInstantMessage(String messageText, int encType)
    {
        // If this chat transport does not support instant messaging we do nothing here.
        if (!allowsInstantMessage()) {
            aTalkApp.showToastMessage(R.string.service_gui_SEND_MESSAGE_NOT_SUPPORTED, getName());
            return;
        }

        IMessage message = adHocChatRoom.createMessage(messageText, encType, null);
        if (IMessage.ENCRYPTION_OMEMO == (encType & IMessage.ENCRYPTION_MASK)) {
            OmemoManager omemoManager = OmemoManager.getInstanceFor(mPPS.getConnection());
            adHocChatRoom.sendMessage(message, omemoManager);
        }
        else {
            adHocChatRoom.sendMessage(message);
        }
    }

    /**
     * Sends <tt>message</tt> as a message correction through this transport, specifying the
     * mime type (html or plain text) and the id of the message to replace.
     *
     * @param message The message to send.
     * @param encType See IMessage for definition of encType e.g. Encryption, encode & remoteOnly
     * @param correctedMessageUID The ID of the message being corrected by this message.
     * @see ChatMessage Encryption Type
     */
    public void sendInstantMessage(String message, int encType, String correctedMessageUID)
    {
    }

    /**
     * Determines whether this chat transport supports the supplied content type
     *
     * @param mimeType the mime type we want to check
     * @return <tt>true</tt> if the chat transport supports it and <tt>false</tt> otherwise.
     */
    public boolean isContentTypeSupported(int mimeType)
    {
        // we only support plain text for chat rooms for now
        return (IMessage.ENCODE_PLAIN == mimeType);
    }

    /**
     * Sending sms messages is not supported by this chat transport implementation.
     */
    public void sendSmsMessage(String phoneNumber, String message)
    {
    }

    /**
     * Sending sms messages is not supported by this chat transport implementation.
     */
    public void sendSmsMessage(String message)
            throws Exception
    {
    }

    /**
     * Sending file in sms messages is not supported by this chat transport implementation.
     */
    public FileTransfer sendMultimediaFile(File file)
    {
        return null;
    }

    /**
     * Sends the given sticker through this chat transport file will always use http file upload
     *
     * @param file the file to send
     * @param chatType ChatFragment.MSGTYPE_OMEMO or MSGTYPE_NORMAL
     * @param xferCon and instance of #FileSendConversation
     * @return the HTTPFileUpload object charged to transfer the given <tt>file</tt>.
     * @throws Exception if anything goes wrong
     */
    public Object sendSticker(File file, int chatType, FileSendConversation xferCon)
            throws Exception
    {
        return sendFile(file, chatType, xferCon);
    }

    /**
     * Not used.
     *
     * @return status
     */
    public boolean askForSMSNumber()
    {
        return false;
    }

    /**
     * Sending chat state notifications is not supported by this chat transport implementation.
     */
    public void sendChatStateNotification(ChatState chatState)
    {
    }

    /**
     * Sending files through a chat room will always use http file upload
     *
     * @param file the file to send
     * @param chatType ChatFragment.MSGTYPE_OMEMO or MSGTYPE_NORMAL
     * @param xferCon and instance of #FileSendConversation
     * @return the HTTPFileUpload object charged to transfer the given <tt>file</tt>.
     * @throws Exception if anything goes wrong
     */
    public Object sendFile(File file, int chatType, FileSendConversation xferCon)
            throws Exception
    {
        // If this chat transport does not support file transfer we do nothing and just return.
        if (!allowsFileTransfer())
            return null;

        return httpFileUpload(file, chatType, xferCon);
    }

    /**
     * Http file upload if supported by the server
     */
    private Object httpFileUpload(File file, int chatType, FileSendConversation xferCon)
            throws Exception
    {
        // check to see if server supports httpFileUpload service if contact is off line or legacy file transfer failed
        if (httpFileUploadManager.isUploadServiceDiscovered()) {
            int encType = IMessage.ENCRYPTION_NONE;
            Object url;
            try {
                if (ChatFragment.MSGTYPE_OMEMO == chatType) {
                    encType = IMessage.ENCRYPTION_OMEMO;
                    url = httpFileUploadManager.uploadFileEncrypted(file, xferCon);
                }
                else {
                    url = httpFileUploadManager.uploadFile(file, xferCon);
                }
                xferCon.setStatus(FileTransferStatusChangeEvent.IN_PROGRESS, adHocChatRoom, encType);
                return url;
            } catch (InterruptedException | XMPPException.XMPPErrorException | SmackException | IOException e) {
                throw new OperationNotSupportedException(e.getMessage());
            }
        }
        else
            throw new OperationNotSupportedException(aTalkApp.getResString(R.string.service_gui_FILE_TRANSFER_NOT_SUPPORTED));
    }

    /**
     * Returns {@code true} if this chat transport supports file transfer, otherwise returns {@code false}.
     *
     * @return {@code true} if this chat transport supports file transfer, otherwise returns {@code false}.
     */
    public boolean allowsFileTransfer()
    {
        return httpFileUploadManager.isUploadServiceDiscovered();
    }

    /**
     * Returns the maximum file length supported by the protocol in bytes.
     *
     * @return the file length that is supported.
     */
    public long getMaximumFileLength()
    {
        return httpFileUploadManager.getDefaultUploadService().getMaxFileSize();
    }

    /**
     * Invites the given contact in this chat conference.
     *
     * @param contactAddress the address of the contact to invite
     * @param reason the reason for the invitation
     */
    public void inviteChatContact(EntityBareJid contactAddress, String reason)
    {
        if (adHocChatRoom != null)
            adHocChatRoom.invite(contactAddress, reason);
    }

    /**
     * Returns the parent session of this chat transport. A <tt>ChatSession</tt> could contain
     * more than one transports.
     *
     * @return the parent session of this chat transport
     */
    public ChatSession getParentChatSession()
    {
        return chatSession;
    }

    /**
     * Adds an sms message listener to this chat transport.
     *
     * @param l The message listener to add.
     */
    public void addSmsMessageListener(MessageListener l)
    {
        // If this chat transport does not support sms messaging we do nothing here.
        if (!allowsSmsMessage())
            return;

        OperationSetSmsMessaging smsOpSet = mPPS.getOperationSet(OperationSetSmsMessaging.class);
        smsOpSet.addMessageListener(l);
    }

    /**
     * Adds an instant message listener to this chat transport.
     *
     * @param l The message listener to add.
     */
    public void addInstantMessageListener(MessageListener l)
    {
        // If this chat transport does not support instant messaging we do nothing here.
        if (!allowsInstantMessage())
            return;

        OperationSetBasicInstantMessaging imOpSet = mPPS.getOperationSet(OperationSetBasicInstantMessaging.class);
        imOpSet.addMessageListener(l);
    }

    /**
     * Removes the given sms message listener from this chat transport.
     *
     * @param l The message listener to remove.
     */
    public void removeSmsMessageListener(MessageListener l)
    {
        // If this chat transport does not support sms messaging we do nothing here.
        if (!allowsSmsMessage())
            return;

        OperationSetSmsMessaging smsOpSet = mPPS.getOperationSet(OperationSetSmsMessaging.class);
        smsOpSet.removeMessageListener(l);
    }

    /**
     * Removes the instant message listener from this chat transport.
     *
     * @param l The message listener to remove.
     */
    public void removeInstantMessageListener(MessageListener l)
    {
        // If this chat transport does not support instant messaging we do nothing here.
        if (!allowsInstantMessage())
            return;

        OperationSetBasicInstantMessaging imOpSet = mPPS.getOperationSet(OperationSetBasicInstantMessaging.class);
        imOpSet.removeMessageListener(l);
    }

    public void dispose()
    {
    }

    /**
     * Returns the descriptor of this chat transport.
     *
     * @return the descriptor of this chat transport
     */
    public Object getDescriptor()
    {
        return adHocChatRoom;
    }

    /**
     * Returns <tt>true</tt> if this chat transport supports message corrections and false otherwise.
     *
     * @return <tt>true</tt> if this chat transport supports message corrections and false otherwise.
     */
    public boolean allowsMessageCorrections()
    {
        return false;
    }
}
