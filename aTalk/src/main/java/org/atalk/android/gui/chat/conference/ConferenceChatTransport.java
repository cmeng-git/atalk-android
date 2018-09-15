/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */

package org.atalk.android.gui.chat.conference;

import net.java.sip.communicator.service.protocol.ChatRoom;
import net.java.sip.communicator.service.protocol.FileTransfer;
import net.java.sip.communicator.service.protocol.Message;
import net.java.sip.communicator.service.protocol.OperationSetBasicInstantMessaging;
import net.java.sip.communicator.service.protocol.OperationSetChatStateNotifications;
import net.java.sip.communicator.service.protocol.OperationSetSmsMessaging;
import net.java.sip.communicator.service.protocol.PresenceStatus;
import net.java.sip.communicator.service.protocol.ProtocolProviderService;
import net.java.sip.communicator.service.protocol.event.MessageListener;

import org.atalk.android.R;
import org.atalk.android.aTalkApp;
import org.atalk.android.gui.chat.ChatMessage;
import org.atalk.android.gui.chat.ChatSession;
import org.atalk.android.gui.chat.ChatTransport;
import org.jivesoftware.smack.SmackException;
import org.jivesoftware.smackx.chatstates.ChatState;
import org.jivesoftware.smackx.omemo.OmemoManager;
import org.jxmpp.jid.EntityBareJid;

import java.io.File;

/**
 * The conference implementation of the <tt>ChatTransport</tt> interface that provides
 * abstraction to access to protocol providers.
 *
 * @author Yana Stamcheva
 * @author Eng Chong Meng
 */
public class ConferenceChatTransport implements ChatTransport
{
    private final ChatSession chatSession;
    private final ChatRoom chatRoom;
    private final ProtocolProviderService mPPS;

    /**
     * Creates an instance of <tt>ConferenceChatTransport</tt> by specifying the parent chat
     * session and the chat room associated with this
     * transport.
     *
     * @param chatSession the parent chat session.
     * @param chatRoom the chat room associated with this conference transport.
     */
    public ConferenceChatTransport(ChatSession chatSession, ChatRoom chatRoom)
    {
        this.chatSession = chatSession;
        this.chatRoom = chatRoom;
        mPPS = chatRoom.getParentProvider();
    }

    /**
     * Returns the contact address corresponding to this chat transport.
     *
     * @return The contact address corresponding to this chat transport.
     */
    public String getName()
    {
        return chatRoom.getName();
    }

    /**
     * Returns the display name corresponding to this chat transport.
     *
     * @return The display name corresponding to this chat transport.
     */
    public String getDisplayName()
    {
        return chatRoom.getName();
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
     * @return <tt>true</tt> if the display name shows only the resource, <tt>false</tt> -
     * otherwise
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
     * Returns <code>true</code> if this chat transport supports instant messaging, otherwise
     * returns <code>false</code>.
     *
     * @return <code>true</code> if this chat transport supports instant messaging, otherwise
     * returns <code>false</code>.
     */
    public boolean allowsInstantMessage()
    {
        return chatRoom.isJoined();
    }

    /**
     * Returns <code>true</code> if this chat transport supports sms messaging, otherwise returns
     * <code>false</code>.
     *
     * @return <code>true</code> if this chat transport supports sms messaging, otherwise returns
     * <code>false</code>.
     */
    public boolean allowsSmsMessage()
    {
        return false;
    }

    /**
     * Returns <code>true</code> if this chat transport supports chat state notifications,
     * otherwise returns <code>false</code>.
     *
     * @return <code>true</code> if this chat transport supports chat state notifications,
     * otherwise returns <code>false</code>.
     */
    public boolean allowsChatStateNotifications()
    {
        Object tnOpSet = mPPS.getOperationSet(OperationSetChatStateNotifications.class);
        return tnOpSet != null;
    }

    /**
     * Sends the given instant message trough this chat transport, by specifying the mime type
     * (html or plain text).
     *
     * @param messageText The message to send.
     * @param encryptionType The encryptionType of the message to send: @see ChatMessage Encryption Type
     * @param mimeType The mime type of the message to send: 1=text/html or 0=text/plain.
     */
    public void sendInstantMessage(String messageText, int encryptionType, int mimeType)
			throws Exception
    {
        // If this chat transport does not support instant messaging we do nothing here.
        if (!allowsInstantMessage()) {
            aTalkApp.showToastMessage(R.string.service_gui_CHAT_ROOM_NOT_JOINED);
            return;
        }

        Message message = chatRoom.createMessage(messageText, encryptionType, mimeType, null);
        if (ChatMessage.ENCRYPTION_OMEMO == encryptionType) {
            OmemoManager omemoManager = OmemoManager.getInstanceFor(mPPS.getConnection());
            chatRoom.sendMessage(message, omemoManager);
        }
        else {
            chatRoom.sendMessage(message);
        }
    }

    /**
     * Sends <tt>message</tt> as a message correction through this transport, specifying the mime
     * type (html or plain text) and the id of the message to replace.
     *
     * @param message The message to send.
     * @param encryptionType The encryptionType of the message to send: @see ChatMessage Encryption Type
     * @param mimeType The mime type of the message to send: 1=text/html or 0=text/plain.
     * @see ChatMessage Encryption Type
     * * @param correctedMessageUID
     * The ID of the message being corrected by this message.
     */
    public void sendInstantMessage(String message, int encryptionType,  int mimeType, String correctedMessageUID)
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
        return (ChatMessage.ENCODE_PLAIN == mimeType);
    }

    /**
     * Sending sms messages is not supported by this chat transport implementation.
     */
    public void sendSmsMessage(String phoneNumber, String message)
            throws Exception
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
            throws Exception
    {
        return null;
    }

    /**
     * Sending sticker messages is not supported by this chat transport implementation.
     */
    public FileTransfer sendSticker(File file)
    {
        return null;
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
     * Sending files through a chat room is not yet supported by this chat transport
     * implementation.
     */
    public FileTransfer sendFile(File file)
            throws Exception
    {
        return null;
    }

    /**
     * Returns the maximum file length supported by the protocol in bytes.
     *
     * @return the file length that is supported.
     */
    public long getMaximumFileLength()
    {
        return -1;
    }

    /**
     * Invites the given contact in this chat conference.
     *
     * @param contactAddress the address of the contact to invite
     * @param reason the reason for the invitation
     */
    public void inviteChatContact(EntityBareJid contactAddress, String reason)
    {
        if (chatRoom != null)
            try {
                chatRoom.invite(contactAddress, reason);
            } catch (SmackException.NotConnectedException | InterruptedException e) {
                e.printStackTrace();
            }
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
        return chatRoom;
    }

    /**
     * Returns <code>true</code> if this chat transport supports message corrections and false
     * otherwise.
     *
     * @return <code>true</code> if this chat transport supports message corrections and false
     * otherwise.
     */
    public boolean allowsMessageCorrections()
    {
        return false;
    }
}
