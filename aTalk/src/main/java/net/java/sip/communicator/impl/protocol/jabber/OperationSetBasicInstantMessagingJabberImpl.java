/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package net.java.sip.communicator.impl.protocol.jabber;

import android.os.Build;
import android.text.Html;

import net.java.sip.communicator.service.protocol.Message;
import net.java.sip.communicator.service.protocol.*;
import net.java.sip.communicator.service.protocol.event.*;
import net.java.sip.communicator.util.ConfigurationUtils;

import org.atalk.android.aTalkApp;
import org.atalk.android.plugin.timberlog.TimberLog;
import org.atalk.crypto.omemo.OmemoAuthenticateDialog;
import org.jivesoftware.smack.SmackException;
import org.jivesoftware.smack.SmackException.NoResponseException;
import org.jivesoftware.smack.SmackException.NotConnectedException;
import org.jivesoftware.smack.XMPPException;
import org.jivesoftware.smack.chat2.*;
import org.jivesoftware.smack.filter.StanzaFilter;
import org.jivesoftware.smack.packet.*;
import org.jivesoftware.smack.packet.StanzaError.Condition;
import org.jivesoftware.smack.tcp.XMPPTCPConnection;
import org.jivesoftware.smack.util.StringUtils;
import org.jivesoftware.smackx.carbons.CarbonCopyReceivedListener;
import org.jivesoftware.smackx.carbons.CarbonManager;
import org.jivesoftware.smackx.carbons.packet.CarbonExtension;
import org.jivesoftware.smackx.chatstates.ChatState;
import org.jivesoftware.smackx.chatstates.packet.ChatStateExtension;
import org.jivesoftware.smackx.delay.packet.DelayInformation;
import org.jivesoftware.smackx.message_correct.element.MessageCorrectExtension;
import org.jivesoftware.smackx.omemo.OmemoManager;
import org.jivesoftware.smackx.omemo.OmemoMessage;
import org.jivesoftware.smackx.omemo.element.OmemoElement;
import org.jivesoftware.smackx.omemo.exceptions.CryptoFailedException;
import org.jivesoftware.smackx.omemo.exceptions.UndecidedOmemoIdentityException;
import org.jivesoftware.smackx.omemo.internal.OmemoDevice;
import org.jivesoftware.smackx.omemo.listener.OmemoMessageListener;
import org.jivesoftware.smackx.xhtmlim.XHTMLManager;
import org.jivesoftware.smackx.xhtmlim.XHTMLText;
import org.jivesoftware.smackx.xhtmlim.packet.XHTMLExtension;
import org.jxmpp.jid.*;

import java.util.*;

import timber.log.Timber;

import static org.jivesoftware.smackx.omemo.util.OmemoConstants.OMEMO_NAMESPACE_V_AXOLOTL;

/**
 * A straightforward implementation of the basic instant messaging operation set.
 *
 * @author Damian Minkov
 * @author Matthieu Helleringer
 * @author Alain Knaebel
 * @author Emil Ivov
 * @author Hristo Terezov
 * @author Eng Chong Meng
 */

public class OperationSetBasicInstantMessagingJabberImpl extends AbstractOperationSetBasicInstantMessaging
        implements OperationSetMessageCorrection, IncomingChatMessageListener, CarbonCopyReceivedListener,
        OmemoMessageListener
{
    /**
     * A table mapping contact addresses to message threads that can be used to target a specific
     * resource (rather than sending a message to all logged instances of a user).
     */
    private final Map<BareJid, StoredThreadID> jidThreads = new Hashtable<>();

    /**
     * The most recent FullJid used for the contact address.
     */
    private Map<BareJid, Jid> recentJidForContact = new Hashtable<>();

    /**
     * CarbonManager and ChatManager instances used by OperationSetBasicInstantMessagingJabberImpl
     */
    private CarbonManager mCarbonManager = null;
    private ChatManager mChatManager = null;

    /**
     * Current active chat
     */
    private Chat mChat = null;

    /**
     * Contains the complete jid of a specific user and the time that it was last used so that we
     * could remove it after a certain point.
     */
    public static class StoredThreadID
    {
        /**
         * The time that we last sent or received a message from this jid
         */
        long lastUpdatedTime;

        /**
         * The last chat used, this way we will reuse the thread-id
         */
        String threadID;
    }

    /**
     * A prefix helps to make sure that thread ID's are unique across multiple instances.
     */
    private static String prefix = StringUtils.randomString(5);

    /**
     * Keeps track of the current increment, which is appended to the prefix to forum a unique thread ID.
     */
    private static long id = 0;

    /**
     * The number of milliseconds that we preserve threads with no traffic before considering them dead.
     */
    private static final long JID_INACTIVITY_TIMEOUT = 10 * 60 * 1000; // 10 min.

    /**
     * The provider that created us.
     */
    private final ProtocolProviderServiceJabberImpl jabberProvider;

    /**
     * A reference to the persistent presence operation set that we use to match incoming messages
     * to <tt>Contact</tt>s and vice versa.
     */
    private OperationSetPersistentPresenceJabberImpl opSetPersPresence = null;

    /**
     * Whether carbon is enabled or not.
     */
    private boolean isCarbonEnabled = false;

    /**
     * Creates an instance of this operation set.
     *
     * @param provider a reference to the <tt>ProtocolProviderServiceImpl</tt> that created us and that we'll
     * use for retrieving the underlying aim connection.
     */
    OperationSetBasicInstantMessagingJabberImpl(ProtocolProviderServiceJabberImpl provider)
    {
        this.jabberProvider = provider;
        provider.addRegistrationStateChangeListener(new RegistrationStateListener());
    }

    /**
     * Create a Message instance with the specified UID, content type and a default encoding. This
     * method can be useful when message correction is required. One can construct the corrected
     * message to have the same UID as the message before correction.
     *
     * @param messageText the string content of the message.
     * @param encType the encryption type for the <tt>content</tt>
     * @param messageUID the unique identifier of this message.
     * @return Message the newly created message
     */
    public Message createMessageWithUID(String messageText, int encType, String messageUID)
    {
        return new MessageJabberImpl(messageText, encType, null, messageUID);
    }

    /**
     * Create a Message instance for sending arbitrary MIME-encoding content.
     *
     * @param content content value
     * @param encType the encryption type for the <tt>content</tt>
     * @return the newly created message.
     */
    public Message createMessage(String content, int encType)
    {
        return createMessage(content, encType, null);
    }

    /**
     * Create a Message instance for sending arbitrary MIME-encoding content.
     *
     * @param content content value
     * @param encType the encryption type for the <tt>content</tt>
     * @param subject the Subject of the message that we'd like to create.
     * @return the newly created message.
     */
    @Override
    public Message createMessage(String content, int encType, String subject)
    {
        return new MessageJabberImpl(content, encType, subject);
    }

    /**
     * Determines whether the protocol provider (or the protocol itself) support sending and
     * receiving offline messages. Most often this method would return true for protocols that
     * support offline messages and false for those that don't. It is however possible for a
     * protocol to support these messages and yet have a particular account that does not
     * (i.e. feature not enabled on the protocol server). In cases like this it is possible for
     * this method to return true even when offline messaging is not supported, and then have the
     * sendMessage method throw an OperationFailedException with code - OFFLINE_MESSAGES_NOT_SUPPORTED.
     *
     * @return <tt>true</tt> if the protocol supports offline messages and <tt>false</tt> otherwise.
     */
    public boolean isOfflineMessagingSupported()
    {
        return true;
    }

    /**
     * Determines whether the protocol supports the supplied content type
     *
     * @param mimeType the encryption type we want to check
     * @return <tt>true</tt> if the protocol supports it and <tt>false</tt> otherwise.
     */
    public boolean isContentTypeSupported(int mimeType)
    {
        return ((Message.ENCODE_PLAIN == mimeType) || (Message.ENCODE_HTML == mimeType));
    }

    /**
     * Determines whether the protocol supports the supplied content type for the given contact.
     *
     * @param mimeType the encryption type we want to check
     * @param contact contact which is checked for supported encType
     * @return <tt>true</tt> if the contact supports it and <tt>false</tt> otherwise.
     */
    @Override
    public boolean isContentTypeSupported(int mimeType, Contact contact)
    {
        // by default we support default mime type, for other mime types method must be overridden
        if (Message.ENCODE_PLAIN == mimeType) {
            return true;
        }
        else if (Message.ENCODE_HTML == mimeType) {
            Jid toJid = getRecentFullJidForContactIfPossible(contact);
            return jabberProvider.isFeatureListSupported(toJid, XHTMLExtension.NAMESPACE);
        }
        return false;
    }

    /**
     * Remove from our <tt>jidThreads</tt> map all entries that have not seen any activity
     * (i.e. neither outgoing nor incoming messages) for more than JID_INACTIVITY_TIMEOUT.
     * Note that this method is not synchronous and that it is only meant for use by the
     * {@link #getThreadIDForAddress(BareJid, boolean)} and {@link #putJidForAddress(Jid, String)}
     */
    private void purgeOldJidThreads()
    {
        long currentTime = System.currentTimeMillis();
        Iterator<Map.Entry<BareJid, StoredThreadID>> entries = jidThreads.entrySet().iterator();

        while (entries.hasNext()) {
            Map.Entry<BareJid, StoredThreadID> entry = entries.next();
            StoredThreadID target = entry.getValue();

            if (currentTime - target.lastUpdatedTime > JID_INACTIVITY_TIMEOUT)
                entries.remove();
        }
    }

    /**
     * When chat state enter ChatState.gone, existing thread should not be used again.
     *
     * @param bareJid the <tt>address</tt> that we'd like to remove a threadID for.
     */
    public void purgeGoneJidThreads(BareJid bareJid)
    {
        jidThreads.remove(bareJid);
    }

    /**
     * Returns the threadID that the party with the specified <tt>address</tt> contacted us from or
     * <tt>new ThreadID</tt> if <tt>null</tt> and <tt>generateNewIfNoExist</tt> is true; otherwise
     * <tt>null</tt> if we don't have a jid for the specified <tt>address</tt> yet.
     *
     * The method would also purge all entries that haven't seen any activity (i.e. no one has
     * tried to get or remap it) for a delay longer than <tt>JID_INACTIVITY_TIMEOUT</tt>.
     *
     * @param bareJid the <tt>Jid</tt> that we'd like to obtain a threadID for.
     * @param generateNewIfNoExist if <tt>true</tt> generates new threadID if null is found.
     * @return new or last threadID that the party with the specified <tt>address</tt> contacted
     * us from OR <tt>null</tt> if we don't have a jid for the specified <tt>address</tt> and
     * <tt>generateNewIfNoExist</tt> is false.
     */
    public String getThreadIDForAddress(BareJid bareJid, boolean generateNewIfNoExist)
    {
        synchronized (jidThreads) {
            purgeOldJidThreads();
            StoredThreadID ta = jidThreads.get(bareJid);

            if (ta == null) {
                if (generateNewIfNoExist) {
                    ta = new StoredThreadID();
                    ta.threadID = nextThreadID();
                    putJidForAddress(bareJid, ta.threadID);
                }
                else
                    return null;
            }
            ta.lastUpdatedTime = System.currentTimeMillis();
            return ta.threadID;
        }
    }

    /**
     * Maps the specified <tt>address</tt> to <tt>jid</tt>. The point of this method is to allow us
     * to send all messages destined to the contact with the specified <tt>address</tt> to the
     * <tt>jid</tt> that they last contacted us from.
     *
     * @param threadID the threadID of conversation.
     * @param jid the jid (i.e. address/resource) that the contact with the specified <tt>address</tt>
     * last contacted us from.
     */
    private void putJidForAddress(Jid jid, String threadID)
    {
        synchronized (jidThreads) {
            purgeOldJidThreads();
            StoredThreadID ta = jidThreads.get(jid.asBareJid());

            if (ta == null) {
                ta = new StoredThreadID();
                jidThreads.put(jid.asBareJid(), ta);
            }
            recentJidForContact.put(jid.asBareJid(), jid);
            ta.lastUpdatedTime = System.currentTimeMillis();
            ta.threadID = threadID;
        }
    }

    /**
     * Helper function used to send a message to a contact, with the given extensions attached.
     *
     * @param to The contact to send the message to.
     * @param toResource The resource to send the message to or null if no resource has been specified
     * @param message The message to send.
     * @param extensions The XMPP extensions that should be attached to the message before sending.
     * @return The MessageDeliveryEvent that resulted after attempting to send this message, so the
     * calling function can modify it if needed.
     */
    private MessageDeliveredEvent sendMessage(Contact to, ContactResource toResource,
            Message message, ExtensionElement[] extensions)
    {
        if (!(to instanceof ContactJabberImpl))
            throw new IllegalArgumentException("The specified contact is not a Jabber contact: " + to);
        try {
            assertConnected();
        } catch (IllegalStateException ex) {
            MessageDeliveryFailedEvent msgDeliveryFailed
                    = new MessageDeliveryFailedEvent(message, to, MessageDeliveryFailedEvent.PROVIDER_NOT_REGISTERED);
            fireMessageEvent(msgDeliveryFailed);
            // throw ex; Do not throw to cause system to crash, return null instead
            return null;
        }

        org.jivesoftware.smack.packet.Message msg = new org.jivesoftware.smack.packet.Message();
        EntityBareJid toJid = to.getJid().asEntityBareJidIfPossible();
        mChat = mChatManager.chatWith(toJid);
        msg.setStanzaId(message.getMessageUID());
        msg.setTo(toJid);

        for (ExtensionElement ext : extensions) {
            msg.addExtension(ext);
        }
        Timber.log(TimberLog.FINER, "Will send a message to: %s chat.jid = %s", toJid, toJid);

        MessageDeliveredEvent msgDeliveryPendingEvt = new MessageDeliveredEvent(message, to, toResource);
        MessageDeliveredEvent[] transformedEvents = messageDeliveryPendingTransform(msgDeliveryPendingEvt);

        if (transformedEvents == null || transformedEvents.length == 0) {
            MessageDeliveryFailedEvent msgDeliveryFailed
                    = new MessageDeliveryFailedEvent(message, to, MessageDeliveryFailedEvent.UNSUPPORTED_OPERATION);
            fireMessageEvent(msgDeliveryFailed);
            return null;
        }

        for (MessageDeliveredEvent event : transformedEvents) {
            String content = event.getSourceMessage().getContent();

            if (Message.ENCODE_HTML == message.getMimeType()) {
                // msg.setBody(Html2Text.extractText(content));
                if (Build.VERSION.SDK_INT > Build.VERSION_CODES.M)
                    msg.setBody(Html.fromHtml(content, Html.FROM_HTML_MODE_LEGACY));
                else
                    msg.setBody(Html.fromHtml(content));

                // cmeng: Just add XHTML element as it will be ignored by buddy without XEP-0071: XHTML-IM support
                // Also carbon messages may send to buddy on difference clients with different capabalities
                // Note isFeatureListSupported must use FullJid unless it is for service e.g. conference.atalk.org

                // Check if the buddy supports XHTML messages make sure we use our discovery manager as it caches calls
                // if (jabberProvider.isFeatureListSupported(toJid, XHTMLExtension.NAMESPACE)) {
                // Add the XHTML text to the message
                XHTMLText htmlText = new XHTMLText("", "us").append(content).appendCloseBodyTag();
                XHTMLManager.addBody(msg, htmlText);
                //}
            }
            else {
                // this is plain text so keep it as it is.
                msg.setBody(content);
            }

            // msg.addExtension(new Version());
            // Disable carbon for OTR message
            if (event.isMessageEncrypted() && isCarbonEnabled) {
                CarbonExtension.Private.addTo(msg);
            }

            // Add ChatState.active extension to message send if option is enabled
            if (ConfigurationUtils.isSendChatStateNotifications()) {
                ChatStateExtension extActive = new ChatStateExtension(ChatState.active);
                msg.addExtension(extActive);
            }

            String threadID = getThreadIDForAddress(toJid, true);
            msg.setThread(threadID);
            msg.setType(org.jivesoftware.smack.packet.Message.Type.chat);
            msg.setFrom(jabberProvider.getConnection().getUser());

            try {
                jabberProvider.getConnection().sendStanza(msg);
            } catch (NotConnectedException | InterruptedException e) {
                e.printStackTrace();
            }
            putJidForAddress(toJid, threadID);
        }
        return new MessageDeliveredEvent(message, to, toResource);
    }

    /**
     * Sends the <tt>message</tt> to the destination indicated by the <tt>to</tt> contact.
     *
     * @param to the <tt>Contact</tt> to send <tt>message</tt> to
     * @param message the <tt>Message</tt> to send.
     * @throws java.lang.IllegalStateException if the underlying stack is not registered and initialized.
     * @throws java.lang.IllegalArgumentException if <tt>to</tt> is not an instance of ContactImpl.
     */
    public void sendInstantMessage(Contact to, Message message)
    {
        sendInstantMessage(to, null, message);
    }

    /**
     * Sends the <tt>message</tt> to the destination indicated by the <tt>to</tt>. Provides a
     * default implementation of this method.
     *
     * @param to the <tt>Contact</tt> to send <tt>message</tt> to
     * @param resource the resource to which the message should be send
     * @param message the <tt>Message</tt> to send.
     * @throws java.lang.IllegalStateException if the underlying ICQ stack is not registered and initialized.
     * @throws java.lang.IllegalArgumentException if <tt>to</tt> is not an instance belonging to the underlying implementation.
     */
    @Override
    public void sendInstantMessage(Contact to, ContactResource resource, Message message)
    {
        MessageDeliveredEvent msgDelivered = sendMessage(to, resource, message, new ExtensionElement[0]);
        if (msgDelivered != null) {
            fireMessageEvent(msgDelivered);
        }
    }

    /**
     * Replaces the message with ID <tt>correctedMessageUID</tt> sent to the contact <tt>to</tt>
     * with the message <tt>message</tt>
     *
     * @param to The contact to send the message to.
     * @param message The new message.
     * @param correctedMessageUID The ID of the message being replaced.
     */
    public void sendInstantMessage(Contact to, ContactResource resource, Message message, String correctedMessageUID)
    {
        ExtensionElement[] exts = new ExtensionElement[1];
        exts[0] = new MessageCorrectExtension(correctedMessageUID);
        MessageDeliveredEvent msgDelivered = sendMessage(to, resource, message, exts);
        if (msgDelivered != null) {
            msgDelivered.setCorrectedMessageUID(correctedMessageUID);
            fireMessageEvent(msgDelivered);
        }
    }

    public void sendInstantMessage(Contact to, ContactResource resource, Message message, String correctedMessageUID,
            OmemoManager omemoManager)
    {
        Jid toJid = to.getJid();
        String content = message.getContent();
        String msgContent;
        OmemoMessage.Sent encryptedMessage;
        String msg = null;

        // OMEMO message content will strip off any html tags info for now => #TODO
        if (Message.ENCODE_HTML == message.getMimeType()) {
            if (Build.VERSION.SDK_INT > Build.VERSION_CODES.M)
                msgContent = Html.fromHtml(content, Html.FROM_HTML_MODE_LEGACY).toString();
            else
                msgContent = Html.fromHtml(content).toString();
        }
        else {
            msgContent = content;
        }

        try {
            encryptedMessage = omemoManager.encrypt(toJid.asBareJid(), msgContent);
            org.jivesoftware.smack.packet.Message sendMessage = encryptedMessage.asMessage(toJid);
            if (correctedMessageUID != null)
                sendMessage.addExtension(new MessageCorrectExtension(correctedMessageUID));
            sendMessage.setStanzaId(message.getMessageUID());
            mChat = mChatManager.chatWith(toJid.asEntityBareJidIfPossible());
            mChat.send(sendMessage);
        } catch (UndecidedOmemoIdentityException e) {
            Set<OmemoDevice> omemoDevices = e.getUndecidedDevices();
            aTalkApp.getGlobalContext().startActivity(
                    OmemoAuthenticateDialog.createIntent(omemoManager, omemoDevices, null));
        } catch (CryptoFailedException | InterruptedException | NotConnectedException | NoResponseException e) {
            msg = "Omemo is unable to create session with device: " + e.getMessage();
        } catch (SmackException.NotLoggedInException e) {
            msg = "User must login to send omemo message: " + e.getMessage();
        }
        if (!StringUtils.isNullOrEmpty(msg)) {
            Timber.w("%s", msg);
            aTalkApp.showToastMessage(msg);
        }

        MessageDeliveredEvent msgDelivered;
        if (correctedMessageUID == null)
            msgDelivered = new MessageDeliveredEvent(message, to, resource);
        else
            msgDelivered = new MessageDeliveredEvent(message, to, correctedMessageUID);

        fireMessageEvent(msgDelivered);
    }

    /**
     * Utility method throwing an exception if the stack is not properly initialized.
     *
     * @throws java.lang.IllegalStateException if the underlying stack is not registered and initialized.
     */
    private void assertConnected()
            throws IllegalStateException
    {
        if (opSetPersPresence == null) {
            throw new IllegalStateException("The provider must be signin before able to communicate.");
        }
        else
            opSetPersPresence.assertConnected();
    }

    /**
     * Our listener that will tell us when we're registered to
     */
    private class RegistrationStateListener implements RegistrationStateChangeListener
    {
        /**
         * The method is called by a ProtocolProvider implementation whenever a change in the
         * registration state of the corresponding provider had occurred.
         *
         * @param evt ProviderStatusChangeEvent the event describing the status change.
         */
        public void registrationStateChanged(RegistrationStateChangeEvent evt)
        {
            XMPPTCPConnection xmppConnection = jabberProvider.getConnection();
            OmemoManager omemoManager;

            if (evt.getNewState() == RegistrationState.REGISTERING) {
                opSetPersPresence = (OperationSetPersistentPresenceJabberImpl)
                        jabberProvider.getOperationSet(OperationSetPersistentPresence.class);
                /*
                 * HashSet<OmemoMessageListener> omemoMessageListeners;
                 * Cannot just add here, has problem as jid is not known to omemoManager yet
                 * See AndroidOmemoService implementation for fix
                 */
                // omemoManager = OmemoManager.getInstanceFor(xmppConnection);
                // registerOmemoMucListener(omemoManager);

                XMPPTCPConnection jabberConnection = jabberProvider.getConnection();
                mChatManager = ChatManager.getInstanceFor(jabberConnection);

                // make sure this listener is not already installed in this connection - ChatManager has taken care
                mChatManager.addIncomingListener(OperationSetBasicInstantMessagingJabberImpl.this);
            }
            else if (evt.getNewState() == RegistrationState.REGISTERED) {
                enableDisableCarbon();
            }
            else if (evt.getNewState() == RegistrationState.UNREGISTERED
                    || evt.getNewState() == RegistrationState.CONNECTION_FAILED
                    || evt.getNewState() == RegistrationState.AUTHENTICATION_FAILED) {
                if (xmppConnection != null) {  // must not assume - may call after log off
                    if (xmppConnection.isAuthenticated()) {
                        omemoManager = OmemoManager.getInstanceFor(xmppConnection);
                        unRegisterOmemoListener(omemoManager);
                    }
                }
                if (mChatManager != null) {
                    mChatManager.removeIncomingListener(OperationSetBasicInstantMessagingJabberImpl.this);
                    mChatManager = null;
                }

                if (mCarbonManager != null) {
                    isCarbonEnabled = false;
                    mCarbonManager.removeCarbonCopyReceivedListener(OperationSetBasicInstantMessagingJabberImpl.this);
                    mCarbonManager = null;
                }
            }
        }
    }

    /**
     * Enable carbon feature if supported by server.
     */
    private void enableDisableCarbon()
    {
        boolean enableCarbon = false;
        EntityFullJid userJid = jabberProvider.getOurJID();
        mCarbonManager = CarbonManager.getInstanceFor(jabberProvider.getConnection());
        try {
            enableCarbon = mCarbonManager.isSupportedByServer()
                    && !jabberProvider.getAccountID().getAccountPropertyBoolean(
                    ProtocolProviderFactory.IS_CARBON_DISABLED, false);

            if (enableCarbon) {
                mCarbonManager.setCarbonsEnabled(true);
                mCarbonManager.addCarbonCopyReceivedListener(this);
                isCarbonEnabled = true;
            }
            else {
                isCarbonEnabled = false;
                mCarbonManager = null;
            }
            Timber.i("Successfully setting carbon new state for: %s to %s", userJid, isCarbonEnabled);
        } catch (NoResponseException | InterruptedException | NotConnectedException
                | XMPPException.XMPPErrorException e) {
            Timber.e(e, "Failed to set carbon state for: %s to %S", userJid, enableCarbon);
        }
    }

    /**
     * The listener that we use in order to handle incoming messages and carbon messages.
     */
    private boolean isForwardedSentMessage = false;

    @Override
    public void onCarbonCopyReceived(CarbonExtension.Direction direction,
            org.jivesoftware.smack.packet.Message carbonCopy, org.jivesoftware.smack.packet.Message wrappingMessage)
    {
        isForwardedSentMessage = CarbonExtension.Direction.sent.equals(direction);
        Jid userJId = isForwardedSentMessage ? carbonCopy.getTo() : carbonCopy.getFrom();
        newIncomingMessage(userJId.asEntityBareJidIfPossible(), carbonCopy, null);
        isForwardedSentMessage = false;
    }

    /**
     * Handles incoming messages and dispatches whatever events are necessary.
     *
     * @param message the message that we need to handle.
     */
    public void newIncomingMessage(EntityBareJid from, org.jivesoftware.smack.packet.Message message, Chat chat)
    {
        // Leave handling of omemo message to onOmemoMessageReceived()
        OmemoElement omemoMessage = message.getExtension(OmemoElement.NAME_ENCRYPTED, OMEMO_NAMESPACE_V_AXOLOTL);
        if (omemoMessage != null)
            return;

        if (message.getBody() == null) {
            return;
        }
        // Return if it is not for us
        ExtensionElement multiChatExtension = message.getExtension("x", "http://jabber.org/protocol/muc#user");
        if (multiChatExtension != null)
            return;

        Jid userFullJId = isForwardedSentMessage ? message.getTo() : message.getFrom();
        BareJid userBareID = userFullJId.asBareJid();

        boolean isPrivateMessaging = false;
        ChatRoomJabberImpl privateContactRoom = null;
        OperationSetMultiUserChatJabberImpl mucOpSet
                = (OperationSetMultiUserChatJabberImpl) jabberProvider.getOperationSet(OperationSetMultiUserChat.class);
        if (mucOpSet != null) {
            privateContactRoom = mucOpSet.getChatRoom(userBareID);
            if (privateContactRoom != null) {
                isPrivateMessaging = true;
            }
        }
        Timber.d("Received from %s the message %s", userBareID, message.toString());

        String msgID = message.getStanzaId();
        String correctedMessageUID = getCorrectionMessageId(message);

        // Get the message type i.e. OTR or NONE for incoming message encryption state display
        String msgBody = message.getBody();
        int encryption = msgBody.startsWith("?OTR") ? Message.ENCRYPTION_OTR : Message.ENCRYPTION_NONE;
        int encType = encryption | Message.ENCODE_PLAIN;
        Message newMessage = createMessageWithUID(message.getBody(), encType, msgID);

        // check if the message is available in xhtml
        if (XHTMLManager.isXHTMLMessage(message)) {
            XHTMLExtension xhtmlExt = (XHTMLExtension) message.getExtension(XHTMLExtension.NAMESPACE);

            // parse all bodies
            List<CharSequence> bodies = xhtmlExt.getBodies();
            StringBuilder messageBuff = new StringBuilder();
            for (CharSequence body : bodies) {
                messageBuff.append(body);
            }

            if (messageBuff.length() > 0) {
                // we remove body tags around message cause their end body tag is breaking the
                // visualization as html in the UI
                String receivedMessage = messageBuff.toString()
                        // removes body start tag
                        .replaceAll("<[bB][oO][dD][yY].*?>", "")
                        // removes body end tag
                        .replaceAll("</[bB][oO][dD][yY].*?>", "");

                // for some reason &apos; is not rendered correctly from our ui, lets use its
                // equivalent. Other similar chars(< > & ") seem ok.
                receivedMessage = receivedMessage.replaceAll("&apos;", "&#39;");
                encType = encryption | Message.ENCODE_HTML;
                newMessage = createMessageWithUID(receivedMessage, encType, msgID);
            }
        }

        Contact sourceContact = opSetPersPresence.findContactByID(isPrivateMessaging ? userFullJId : userBareID);
        if (message.getType() == org.jivesoftware.smack.packet.Message.Type.error) {
            // error which is multi-chat and we don't know about the contact is a muc message
            // error which is missing muc extension and is coming from the room, when we try
            // to send message to room which was deleted or offline on the server
            StanzaError error = message.getError();
            if (isPrivateMessaging && sourceContact == null) {
                int errorResultCode = ChatRoomMessageDeliveryFailedEvent.UNKNOWN_ERROR;
                if ((error != null) && (Condition.forbidden == error.getCondition())) {
                    errorResultCode = ChatRoomMessageDeliveryFailedEvent.FORBIDDEN;
                }

                String errorReason = (error != null) ? error.toString() : "";
                ChatRoomMessageDeliveryFailedEvent msgDeliveryFailed = new ChatRoomMessageDeliveryFailedEvent(privateContactRoom,
                        null, errorResultCode, errorReason, new Date(), newMessage);
                privateContactRoom.fireMessageEvent(msgDeliveryFailed);
                return;
            }

            Timber.i("Message error received from %s", userBareID);

            int errorResultCode = MessageDeliveryFailedEvent.UNKNOWN_ERROR;
            if (error != null) {
                Condition errorCondition = error.getCondition();
                if (Condition.service_unavailable == errorCondition) {
                    if (!sourceContact.getPresenceStatus().isOnline()) {
                        errorResultCode = MessageDeliveryFailedEvent.OFFLINE_MESSAGES_NOT_SUPPORTED;
                    }
                }
            }
            if (sourceContact == null) {
                sourceContact = opSetPersPresence.createVolatileContact(userFullJId, isPrivateMessaging);
            }

            MessageDeliveryFailedEvent msgDeliveryFailed
                    = new MessageDeliveryFailedEvent(newMessage, sourceContact, correctedMessageUID, errorResultCode);
            fireMessageEvent(msgDeliveryFailed);
            return;
        }
        putJidForAddress(userFullJId, message.getThread());

        // In the second condition we filter all group chat messages, because they are managed
        // by the multi user chat operation set.
        if (sourceContact == null) {
            Timber.d("received a message from an unknown contact: %s", userBareID);
            // create the volatile contact
            sourceContact = opSetPersPresence.createVolatileContact(userFullJId, isPrivateMessaging);
        }

        Date timestamp = getTimeStamp(message);
        ContactResource resource = ((ContactJabberImpl) sourceContact).getResourceFromJid(userFullJId);

        EventObject msgEvt;
        if (isForwardedSentMessage)
            msgEvt = new MessageDeliveredEvent(newMessage, sourceContact, timestamp);
        else
            msgEvt = new MessageReceivedEvent(newMessage, sourceContact, resource, timestamp,
                    correctedMessageUID, isPrivateMessaging, privateContactRoom);

        fireMessageEvent(msgEvt);
    }

    /**
     * A filter that prevents this operation set from handling multi user chat messages.
     */
    private static class GroupMessagePacketFilter implements StanzaFilter
    {
        /**
         * Returns <tt>true</tt> if <tt>packet</tt> is a <tt>Message</tt> and false otherwise.
         *
         * @param packet the packet that we need to check.
         * @return <tt>true</tt> if <tt>packet</tt> is a <tt>Message</tt> and false otherwise.
         */
        @Override
        public boolean accept(Stanza packet)
        {
            if (!(packet instanceof org.jivesoftware.smack.packet.Message))
                return false;

            org.jivesoftware.smack.packet.Message msg = (org.jivesoftware.smack.packet.Message) packet;
            return (!(msg.getType() == org.jivesoftware.smack.packet.Message.Type.groupchat));
        }
    }

    public Jid getRecentFullJidForContactIfPossible(Contact contact)
    {
        Jid contactJid = contact.getJid();
        Jid jid = recentJidForContact.get(contactJid.asBareJid());
        if (jid == null)
            jid = contactJid;
        return jid;
    }

    /**
     * Returns the inactivity timeout in milliseconds.
     *
     * @return The inactivity timeout in milliseconds. Or -1 if undefined
     */
    public long getInactivityTimeout()
    {
        return JID_INACTIVITY_TIMEOUT;
    }

    /**
     * Returns the next unique thread id. Each thread id made up of a short alphanumeric prefix
     * along with a unique numeric value.
     *
     * @return the next thread id.
     */
    private static synchronized String nextThreadID()
    {
        return prefix + id++;
    }

    /**
     * Return XEP-0203 time-stamp of the message if present or current time;
     *
     * @param msg Message
     * @return the correct message timeStamp
     */
    private Date getTimeStamp(org.jivesoftware.smack.packet.Message msg)
    {
        Date timeStamp;
        DelayInformation delayInfo = msg.getExtension(DelayInformation.ELEMENT, DelayInformation.NAMESPACE);
        if (delayInfo != null) {
            timeStamp = delayInfo.getStamp();
        }
        else {
            timeStamp = new Date();
        }
        return timeStamp;
    }

    /**
     * Get messageCorrectionID if presence
     *
     * @param message Message
     * @return messageCorrectionID if presence or null
     */
    private String getCorrectionMessageId(org.jivesoftware.smack.packet.Message message)
    {
        MessageCorrectExtension correctionExtension = MessageCorrectExtension.from(message);
        if (correctionExtension != null) {
            return correctionExtension.getIdInitialMessage();
        }
        return null;
    }

    // =============== OMEMO message received =============== //

    public void registerOmemoListener(OmemoManager omemoManager)
    {
        omemoManager.addOmemoMessageListener(this);
    }

    private void unRegisterOmemoListener(OmemoManager omemoManager)
    {
        omemoManager.removeOmemoMessageListener(this);
    }

    private boolean isForwardedSentOmemoMessage = false;

    /**
     * Gets called, whenever an OmemoMessage has been received and was successfully decrypted.
     *
     * @param stanza Received (encrypted) stanza.
     * @param decryptedMessage decrypted OmemoMessage.
     */
    @Override
    public void onOmemoMessageReceived(Stanza stanza, OmemoMessage.Received decryptedMessage)
    {
        // ignore KeyTransportMessages - causes problem: exit even it is false????
//        if (decryptedMessage.isKeyTransportMessage())
//            return;
        /*
         want to check to warn user ?
         OmemoManager.isTrustedOmemoIdentity(decryptedMessage.getSenderDevice(), decryptedMessage.getSendersFingerprint())
        */

        org.jivesoftware.smack.packet.Message encryptedMessage = (org.jivesoftware.smack.packet.Message) stanza;
        Date timeStamp = getTimeStamp(encryptedMessage);

        Jid userFullJid = isForwardedSentOmemoMessage ? encryptedMessage.getTo() : encryptedMessage.getFrom();
        BareJid userBareJid = userFullJid.asBareJid();
        putJidForAddress(userBareJid, encryptedMessage.getThread());
        Contact sourceContact = opSetPersPresence.findContactByID(userBareJid.toString());
        if (sourceContact == null) {
            // create new volatile contact
            sourceContact = opSetPersPresence.createVolatileContact(userBareJid);
        }

        String msgID = encryptedMessage.getStanzaId();
        String correctedMsgID = getCorrectionMessageId(encryptedMessage);
        int encType = Message.ENCRYPTION_OMEMO | Message.ENCODE_PLAIN;

        String msgBody = decryptedMessage.getBody();
        Message newMessage = createMessageWithUID(msgBody, encType, msgID);

        EventObject msgEvt;
        if (isForwardedSentOmemoMessage)
            msgEvt = new MessageDeliveredEvent(newMessage, sourceContact, timeStamp);
        else
            msgEvt = new MessageReceivedEvent(newMessage, sourceContact, timeStamp, correctedMsgID);

        fireMessageEvent(msgEvt);
    }

    public void onOmemoCarbonCopyReceived(CarbonExtension.Direction direction,
            org.jivesoftware.smack.packet.Message carbonCopy,
            org.jivesoftware.smack.packet.Message wrappingMessage,
            OmemoMessage.Received decryptedCarbonCopy)
    {
        isForwardedSentOmemoMessage = CarbonExtension.Direction.sent.equals(direction);
        onOmemoMessageReceived(carbonCopy, decryptedCarbonCopy);
        isForwardedSentOmemoMessage = false;
    }
}
