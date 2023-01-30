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

import timber.log.Timber;

/**
 * The conference implementation of the <code>ChatTransport</code> interface that provides
 * abstraction to access to protocol providers.
 *
 * @author Yana Stamcheva
 * @author Eng Chong Meng
 */
public class ConferenceChatTransport implements ChatTransport {
    private final ChatSession chatSession;
    private final ChatRoom chatRoom;

    /**
     * <code>true</code> when a contact sends a message with XEP-0085 chat state notifications;
     * override contact disco#info no XEP-0085 feature advertised.
     */
    private static boolean isChatStateSupported = false;

    private final ProtocolProviderService mPPS;
    private HttpFileUploadManager httpFileUploadManager;

    /**
     * Creates an instance of <code>ConferenceChatTransport</code> by specifying the parent chat
     * session and the chat room associated with this transport.
     *
     * @param chatSession the parent chat session.
     * @param chatRoom the chat room associated with this conference transport.
     */
    public ConferenceChatTransport(ChatSession chatSession, ChatRoom chatRoom) {
        this.chatSession = chatSession;
        this.chatRoom = chatRoom;
        mPPS = chatRoom.getParentProvider();

        // mPPS.getConnection() == null from field FER
        if ((mPPS != null) && (mPPS.getConnection() != null)) {
            isChatStateSupported = (mPPS.getOperationSet(OperationSetChatStateNotifications.class) != null);
            httpFileUploadManager = HttpFileUploadManager.getInstanceFor(mPPS.getConnection());
        }
    }

    /**
     * Returns the contact address corresponding to this chat transport.
     *
     * @return The contact address corresponding to this chat transport.
     */
    public String getName() {
        return chatRoom.getName();
    }

    /**
     * Returns the display name corresponding to this chat transport.
     *
     * @return The display name corresponding to this chat transport.
     */
    public String getDisplayName() {
        return chatRoom.getName();
    }

    /**
     * Returns the resource name of this chat transport. This is for example the name of the user
     * agent from which the contact is logged.
     *
     * @return The display name of this chat transport resource.
     */
    public String getResourceName() {
        return null;
    }

    /**
     * Indicates if the display name should only show the resource.
     *
     * @return <code>true</code> if the display name shows only the resource, <code>false</code> - otherwise
     */
    public boolean isDisplayResourceOnly() {
        return false;
    }

    /**
     * Returns the presence status of this transport.
     *
     * @return the presence status of this transport.
     */
    public PresenceStatus getStatus() {
        return null;
    }

    /**
     * Returns the <code>ProtocolProviderService</code>, corresponding to this chat transport.
     *
     * @return the <code>ProtocolProviderService</code>, corresponding to this chat transport.
     */
    public ProtocolProviderService getProtocolProvider() {
        return mPPS;
    }

    /**
     * Returns {@code true} if this chat transport supports instant messaging,
     * otherwise returns {@code false}.
     *
     * @return {@code true} if this chat transport supports instant messaging,
     * otherwise returns {@code false}.
     */
    public boolean allowsInstantMessage() {
        return chatRoom.isJoined();
    }

    /**
     * Returns {@code true} if this chat transport supports sms messaging,
     * otherwise returns {@code false}.
     *
     * @return {@code true} if this chat transport supports sms messaging,
     * otherwise returns {@code false}.
     */
    public boolean allowsSmsMessage() {
        return false;
    }

    /**
     * Returns {@code true} if this chat transport supports message delivery receipts,
     * otherwise returns {@code false}.
     *
     * @return {@code true} if this chat transport supports message delivery receipts,
     * otherwise returns {@code false}
     */
    public boolean allowsMessageDeliveryReceipt() {
        return false;
    }

    /**
     * Returns {@code true} if this chat transport supports chat state notifications,
     * otherwise returns {@code false}.
     *
     * @return {@code true} if this chat transport supports chat state notifications,
     * otherwise returns {@code false}.
     */
    public boolean allowsChatStateNotifications() {
        // Object tnOpSet = mPPS.getOperationSet(OperationSetChatStateNotifications.class);
        // return ((tnOpSet != null) && isChatStateSupported);
        return isChatStateSupported;
    }

    public static void setChatStateSupport(boolean isEnable) {
        isChatStateSupported = isEnable;
    }

    /**
     * Sends the given instant message trough this chat transport, by specifying the mime type
     * (html or plain text).
     *
     * @param messageText The message to send.
     * @param encType See IMessage for definition of encType e.g. Encryption, encode & remoteOnly
     */
    public void sendInstantMessage(String messageText, int encType)
            throws Exception {
        // If this chat transport does not support instant messaging we do nothing here.
        if (!allowsInstantMessage()) {
            aTalkApp.showToastMessage(R.string.service_gui_CHATROOM_NOT_JOINED);
            return;
        }

        IMessage message = chatRoom.createMessage(messageText, encType, null);
        if (IMessage.ENCRYPTION_OMEMO == (encType & IMessage.ENCRYPTION_OMEMO)) {
            OmemoManager omemoManager = OmemoManager.getInstanceFor(mPPS.getConnection());
            chatRoom.sendMessage(message, omemoManager);
        } else {
            chatRoom.sendMessage(message);
        }
    }

    /**
     * Sends <code>message</code> as a message correction through this transport, specifying the
     * mime type (html or plain text) and the id of the message to replace.
     *
     * @param message The message to send.
     * @param encType See IMessage for definition of encType e.g. Encryption, encode & remoteOnly
     * @param correctedMessageUID The ID of the message being corrected by this message.
     *
     * @see ChatMessage Encryption Type
     */
    public void sendInstantMessage(String message, int encType, String correctedMessageUID) {
    }

    /**
     * Determines whether this chat transport supports the supplied content type
     *
     * @param mimeType the mime type we want to check
     *
     * @return <code>true</code> if the chat transport supports it and <code>false</code> otherwise.
     */
    public boolean isContentTypeSupported(int mimeType) {
        // we only support plain text for chat rooms for now
        return (IMessage.ENCODE_PLAIN == mimeType);
    }

    /**
     * Sending sms messages is not supported by this chat transport implementation.
     */
    public void sendSmsMessage(String phoneNumber, String message)
            throws Exception {
    }

    /**
     * Sending sms messages is not supported by this chat transport implementation.
     */
    public void sendSmsMessage(String message)
            throws Exception {
    }

    /**
     * Sending file in sms messages is not supported by this chat transport implementation.
     */
    public FileTransfer sendMultimediaFile(File file)
            throws Exception {
        return null;
    }

    /**
     * Sends the given sticker through this chat transport file will always use http file upload
     *
     * @param file the file to send
     * @param chatType ChatFragment.MSGTYPE_OMEMO or MSGTYPE_NORMAL
     * @param xferCon and instance of #FileSendConversation
     *
     * @return the HTTPFileUpload object charged to transfer the given <code>file</code>.
     * @throws Exception if anything goes wrong
     */
    public Object sendSticker(File file, int chatType, FileSendConversation xferCon)
            throws Exception {
        return sendFile(file, chatType, xferCon);
    }

    /**
     * Not used.
     *
     * @return status
     */
    public boolean askForSMSNumber() {
        return false;
    }

    /**
     * Sending chat state notifications for this chat transport.
     */
    public void sendChatStateNotification(ChatState chatState) {
        // Proceed only if this chat transport allows chat state notification
        if (mPPS.isRegistered() && allowsChatStateNotifications() && allowsInstantMessage()) {

            OperationSetChatStateNotifications tnOperationSet
                    = mPPS.getOperationSet(OperationSetChatStateNotifications.class);
            try {
                tnOperationSet.sendChatStateNotification(chatRoom, chatState);
            } catch (Exception ex) {
                Timber.e("Failed to send chat state notifications for %s: %s", chatRoom, ex.getMessage());
            }
        }
    }

    /**
     * Sending files through a chat room will always use http file upload
     *
     * @param file the file to send
     * @param chatType ChatFragment.MSGTYPE_OMEMO or MSGTYPE_NORMAL
     * @param xferCon and instance of #FileSendConversation
     *
     * @return the HTTPFileUpload object charged to transfer the given <code>file</code>.
     * @throws Exception if anything goes wrong
     */
    public Object sendFile(File file, int chatType, FileSendConversation xferCon)
            throws Exception {
        // If this chat transport does not support file transfer we do nothing and just return.
        if (!allowsFileTransfer())
            return null;

        return httpFileUpload(file, chatType, xferCon);
        // return MetaContactChatTransport.httpFileUpload(chatRoom, file, chatType, xferCon, httpFileUploadManager);
    }

    /**
     * Http file upload if supported by the server
     *
     * @param file the file to send
     * @param chatType ChatFragment.MSGTYPE_OMEMO or MSGTYPE_NORMAL
     * @param xferCon an instance of FileSendConversation
     *
     * @return the <code>FileTransfer</code> or HTTPFileUpload object charged to transfer the given <code>file</code>.
     * @throws Exception if anything goes wrong
     */
    private Object httpFileUpload(File file, int chatType, FileSendConversation xferCon)
            throws Exception {
        // check to see if server supports httpFileUpload service if contact is off line or legacy file transfer failed
        if (httpFileUploadManager.isUploadServiceDiscovered()) {
            int encType = IMessage.ENCRYPTION_NONE;
            Object url;
            try {
                if (ChatFragment.MSGTYPE_OMEMO == chatType) {
                    encType = IMessage.ENCRYPTION_OMEMO;
                    url = httpFileUploadManager.uploadFileEncrypted(file, xferCon);
                } else {
                    url = httpFileUploadManager.uploadFile(file, xferCon);
                }
                xferCon.setStatus(FileTransferStatusChangeEvent.IN_PROGRESS, chatRoom, encType, "HTTP File Upload");
                return url;
            } catch (InterruptedException | XMPPException.XMPPErrorException | SmackException | IOException e) {
                throw new OperationNotSupportedException(e.getMessage());
            }
        } else
            throw new OperationNotSupportedException(aTalkApp.getResString(R.string.service_gui_FILE_TRANSFER_NOT_SUPPORTED));
    }

    /**
     * Returns {@code true} if this chat transport supports file transfer, otherwise returns {@code false}.
     *
     * @return {@code true} if this chat transport supports file transfer, otherwise returns {@code false}.
     */
    private boolean allowsFileTransfer() {
        return (httpFileUploadManager != null) && httpFileUploadManager.isUploadServiceDiscovered();
    }

    /**
     * Returns the maximum file length supported by the protocol in bytes.
     *
     * @return the file length that is supported.
     */
    public long getMaximumFileLength() {
        return (httpFileUploadManager == null)
                ? 0 : httpFileUploadManager.getDefaultUploadService().getMaxFileSize();
    }

    /**
     * Invites the given contact in this chat conference.
     *
     * @param contactAddress the address of the contact to invite
     * @param reason the reason for the invitation
     */
    public void inviteChatContact(EntityBareJid contactAddress, String reason) {
        if (chatRoom != null)
            try {
                chatRoom.invite(contactAddress, reason);
            } catch (SmackException.NotConnectedException | InterruptedException e) {
                Timber.w("Invite chat contact exception: %s", e.getMessage());
            }
    }

    /**
     * Returns the parent session of this chat transport. A <code>ChatSession</code> could contain
     * more than one transports.
     *
     * @return the parent session of this chat transport
     */
    public ChatSession getParentChatSession() {
        return chatSession;
    }

    /**
     * Adds an sms message listener to this chat transport.
     *
     * @param l The message listener to add.
     */
    public void addSmsMessageListener(MessageListener l) {
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
    public void addInstantMessageListener(MessageListener l) {
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
    public void removeSmsMessageListener(MessageListener l) {
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
    public void removeInstantMessageListener(MessageListener l) {
        // If this chat transport does not support instant messaging we do nothing here.
        if (!allowsInstantMessage())
            return;

        OperationSetBasicInstantMessaging imOpSet = mPPS.getOperationSet(OperationSetBasicInstantMessaging.class);
        imOpSet.removeMessageListener(l);
    }

    public void dispose() {
    }

    /**
     * Returns the descriptor of this chat transport.
     *
     * @return the descriptor of this chat transport
     */
    public Object getDescriptor() {
        return chatRoom;
    }

    /**
     * Returns <code>true</code> if this chat transport supports message corrections and false otherwise.
     *
     * @return <code>true</code> if this chat transport supports message corrections and false otherwise.
     */
    public boolean allowsMessageCorrections() {
        return false;
    }
}
