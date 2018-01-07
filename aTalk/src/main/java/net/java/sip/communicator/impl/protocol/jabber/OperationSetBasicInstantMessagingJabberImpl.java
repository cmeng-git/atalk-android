/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 * 
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package net.java.sip.communicator.impl.protocol.jabber;

import net.java.sip.communicator.impl.protocol.jabber.extensions.carbon.CarbonPacketExtension;
import net.java.sip.communicator.impl.protocol.jabber.extensions.carbon.ForwardedPacketExtension;
import net.java.sip.communicator.impl.protocol.jabber.extensions.mailnotification.MailThreadInfo;
import net.java.sip.communicator.impl.protocol.jabber.extensions.mailnotification.MailboxIQ;
import net.java.sip.communicator.impl.protocol.jabber.extensions.mailnotification.MailboxIQProvider;
import net.java.sip.communicator.impl.protocol.jabber.extensions.mailnotification.MailboxQueryIQ;
import net.java.sip.communicator.impl.protocol.jabber.extensions.mailnotification.NewMailNotificationIQ;
import net.java.sip.communicator.impl.protocol.jabber.extensions.mailnotification.NewMailNotificationProvider;
import net.java.sip.communicator.service.protocol.AbstractOperationSetBasicInstantMessaging;
import net.java.sip.communicator.service.protocol.ChatRoom;
import net.java.sip.communicator.service.protocol.Contact;
import net.java.sip.communicator.service.protocol.ContactResource;
import net.java.sip.communicator.service.protocol.Message;
import net.java.sip.communicator.service.protocol.OperationSetMessageCorrection;
import net.java.sip.communicator.service.protocol.OperationSetMultiUserChat;
import net.java.sip.communicator.service.protocol.OperationSetPersistentPresence;
import net.java.sip.communicator.service.protocol.ProtocolProviderFactory;
import net.java.sip.communicator.service.protocol.RegistrationState;
import net.java.sip.communicator.service.protocol.event.ChatRoomMessageDeliveryFailedEvent;
import net.java.sip.communicator.service.protocol.event.MessageDeliveredEvent;
import net.java.sip.communicator.service.protocol.event.MessageDeliveryFailedEvent;
import net.java.sip.communicator.service.protocol.event.MessageReceivedEvent;
import net.java.sip.communicator.service.protocol.event.RegistrationStateChangeEvent;
import net.java.sip.communicator.service.protocol.event.RegistrationStateChangeListener;
import net.java.sip.communicator.service.protocol.jabberconstants.JabberStatusEnum;
import net.java.sip.communicator.util.ConfigurationUtils;
import net.java.sip.communicator.util.Html2Text;
import net.java.sip.communicator.util.Logger;

import org.atalk.android.aTalkApp;
import org.atalk.android.gui.chat.ChatMessage;
import org.atalk.crypto.omemo.OmemoAuthenticateDialog;
import org.jivesoftware.smack.SmackException.NoResponseException;
import org.jivesoftware.smack.SmackException.NotConnectedException;
import org.jivesoftware.smack.StanzaListener;
import org.jivesoftware.smack.XMPPException;
import org.jivesoftware.smack.chat.Chat;
import org.jivesoftware.smack.chat.ChatManager;
import org.jivesoftware.smack.chat.ChatMessageListener;
import org.jivesoftware.smack.filter.AndFilter;
import org.jivesoftware.smack.filter.StanzaFilter;
import org.jivesoftware.smack.filter.StanzaTypeFilter;
import org.jivesoftware.smack.packet.ExtensionElement;
import org.jivesoftware.smack.packet.Stanza;
import org.jivesoftware.smack.packet.XMPPError;
import org.jivesoftware.smack.packet.XMPPError.Condition;
import org.jivesoftware.smack.provider.ProviderManager;
import org.jivesoftware.smack.tcp.XMPPTCPConnection;
import org.jivesoftware.smack.util.StringUtils;
import org.jivesoftware.smackx.carbons.CarbonManager;
import org.jivesoftware.smackx.chatstates.ChatState;
import org.jivesoftware.smackx.chatstates.packet.ChatStateExtension;
import org.jivesoftware.smackx.delay.packet.DelayInformation;
import org.jivesoftware.smackx.message_correct.element.MessageCorrectExtension;
import org.jivesoftware.smackx.omemo.OmemoManager;
import org.jivesoftware.smackx.omemo.element.OmemoElement;
import org.jivesoftware.smackx.omemo.exceptions.CannotEstablishOmemoSessionException;
import org.jivesoftware.smackx.omemo.exceptions.CryptoFailedException;
import org.jivesoftware.smackx.omemo.exceptions.UndecidedOmemoIdentityException;
import org.jivesoftware.smackx.omemo.internal.CipherAndAuthTag;
import org.jivesoftware.smackx.omemo.internal.OmemoDevice;
import org.jivesoftware.smackx.omemo.internal.OmemoMessageInformation;
import org.jivesoftware.smackx.omemo.listener.OmemoMessageListener;
import org.jivesoftware.smackx.xhtmlim.XHTMLManager;
import org.jivesoftware.smackx.xhtmlim.XHTMLText;
import org.jivesoftware.smackx.xhtmlim.packet.XHTMLExtension;
import org.jxmpp.jid.EntityFullJid;
import org.jxmpp.jid.EntityJid;
import org.jxmpp.jid.Jid;
import org.jxmpp.jid.impl.JidCreate;
import org.jxmpp.stringprep.XmppStringprepException;

import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Date;
import java.util.EventObject;
import java.util.HashSet;
import java.util.Hashtable;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

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

public class OperationSetBasicInstantMessagingJabberImpl
        extends AbstractOperationSetBasicInstantMessaging
        implements OperationSetMessageCorrection, OmemoMessageListener
{
    /**
     * Our class logger
     */
    private static final Logger logger
            = Logger.getLogger(OperationSetBasicInstantMessagingJabberImpl.class);

    /**
     * The maximum number of unread threads that we'd be notifying the user of.
     */
    private static final String PNAME_MAX_GMAIL_THREADS_PER_NOTIFICATION
            = "protocol.jabber.MAX_GMAIL_THREADS_PER_NOTIFICATION";

    /**
     * A table mapping contact addresses to message threads that can be used to target a specific
     * resource (rather than sending a message to all logged instances of a user).
     */
    private final Map<Jid, StoredThreadID> jidThreads = new Hashtable<>();

    /**
     * The most recent FullJid used for the contact address.
     */
    private Map<Jid, Jid> recentJidForContact = new Hashtable<>();

    /**
     * The smackMessageListener instance listens for incoming messages. Keep a reference of it
     * so if anything goes wrong we don't add two different instances.
     */
    private SmackMessageListener smackMessageListener = null;

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
     * Keeps track of the current increment, which is appended to the prefix to forum a unique
     * thread ID.
     */
    private static long id = 0;

    /**
     * The number of milliseconds that we preserve threads with no traffic before considering them
     * dead.
     */
    private static final long JID_INACTIVITY_TIMEOUT = 10 * 60 * 1000; // 10 min.

    /**
     * Indicates the time of the last Mailbox report that we received from Google (if this is a
     * Google server we are talking to). Should be included in all following mailbox queries
     */
    private long lastReceivedMailboxResultTime = -1;

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
     * The html namespace used as feature XHTMLManager.namespace
     */
    private final static String HTML_NAMESPACE = "http://jabber.org/protocol/xhtml-im";

    /**
     * List of filters to be used to filter which messages to handle current Operation Set.
     */
    private List<StanzaFilter> packetFilters = new ArrayList<>();

    /**
     * Whether carbon is enabled or not.
     */
    private boolean isCarbonEnabled = false;

    /**
     * Creates an instance of this operation set.
     *
     * @param provider
     *         a reference to the <tt>ProtocolProviderServiceImpl</tt> that created us and that we'll
     *         use for retrieving the underlying aim connection.
     */
    OperationSetBasicInstantMessagingJabberImpl(ProtocolProviderServiceJabberImpl provider)
    {
        this.jabberProvider = provider;

        packetFilters.add(new GroupMessagePacketFilter());
        packetFilters.add(new StanzaTypeFilter(org.jivesoftware.smack.packet.Message.class));
        provider.addRegistrationStateChangeListener(new RegistrationStateListener());
    }

    /**
     * Create a Message instance with the specified UID, content type and a default encoding. This
     * method can be useful when message correction is required. One can construct the corrected
     * message to have the same UID as the message before correction.
     *
     * @param messageText
     *         the string content of the message.
     * @param encType
     *         the encryption type for the <tt>content</tt>
     * @param messageUID
     *         the unique identifier of this message.
     * @return Message the newly created message
     */
    public Message createMessageWithUID(String messageText, int encType, String messageUID)
    {
        return new MessageJabberImpl(messageText, encType, null, messageUID);
    }

    /**
     * Create a Message instance for sending arbitrary MIME-encoding content.
     *
     * @param content
     *         content value
     * @param encType
     *         the encryption type for the <tt>content</tt>
     * @return the newly created message.
     */
    public Message createMessage(String content, int encType)
    {
        return createMessage(content, encType, null);
    }

    /**
     * Create a Message instance for sending arbitrary MIME-encoding content.
     *
     * @param content
     *         content value
     * @param encType
     *         the encryption type for the <tt>content</tt>
     * @param subject
     *         the Subject of the message that we'd like to create.
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
     * sendMessage method throw an OperationFailedException with code -
     * OFFLINE_MESSAGES_NOT_SUPPORTED.
     *
     * @return <tt>true</tt> if the protocol supports offline messages and <tt>false</tt>
     * otherwise.
     */
    public boolean isOfflineMessagingSupported()
    {
        return true;
    }

    /**
     * Determines whether the protocol supports the supplied content type
     *
     * @param encType
     *         the encryption type we want to check
     * @return <tt>true</tt> if the protocol supports it and <tt>false</tt> otherwise.
     */
    public boolean isContentTypeSupported(int encType)
    {
        return ((encType == ChatMessage.ENCODE_PLAIN)
                || (encType == ChatMessage.ENCODE_HTML));
    }

    /**
     * Determines whether the protocol supports the supplied content type for the given contact.
     *
     * @param encType
     *         the encryption type we want to check
     * @param contact
     *         contact which is checked for supported encType
     * @return <tt>true</tt> if the contact supports it and <tt>false</tt> otherwise.
     */
    @Override
    public boolean isContentTypeSupported(int encType, Contact contact)
    {
        // by default we support default mime type, for other mime types method must be overridden
        if (encType == ChatMessage.ENCODE_PLAIN) {
            return true;
        }
        else if (encType == ChatMessage.ENCODE_HTML) {
            Jid toJid = getRecentFullJidForContactIfPossible(contact);
            return jabberProvider.isFeatureListSupported(toJid, HTML_NAMESPACE);
        }
        return false;
    }

    private Chat obtainChatInstance(EntityJid jid)
    {
        XMPPTCPConnection jabberConnection = this.jabberProvider.getConnection();
        ChatManager chatManager = ChatManager.getInstanceFor(jabberConnection);

        Chat chat = chatManager.getThreadChat(jid.toString());
        if (chat != null) {
            return chat;
        }

        ChatMessageListener msgListener = new ChatMessageListener()
        {
            @Override
            public void processMessage(Chat chat, org.jivesoftware.smack.packet.Message message)
            {
            }
        };

        chat = chatManager.createChat(jid, msgListener);
        return chat;
    }

    /**
     * Remove from our <tt>jidThreads</tt> map all entries that have not seen any activity
     * (i.e. neither outgoing nor incoming messages) for more than JID_INACTIVITY_TIMEOUT.
     * Note that this method is not synchronous and that it is only meant for use by the
     * {@link #getThreadIDForAddress(Jid, boolean)} and {@link #putJidForAddress(Jid, String)}
     */
    private void purgeOldJidThreads()
    {
        long currentTime = System.currentTimeMillis();
        Iterator<Map.Entry<Jid, StoredThreadID>> entries = jidThreads.entrySet().iterator();

        while (entries.hasNext()) {
            Map.Entry<Jid, StoredThreadID> entry = entries.next();
            StoredThreadID target = entry.getValue();

            if (currentTime - target.lastUpdatedTime > JID_INACTIVITY_TIMEOUT)
                entries.remove();
        }
    }

    /**
     * When chat state enter ChatState.gone, existing thread should not be used again.
     *
     * @param address
     *         the <tt>address</tt> that we'd like to remove a threadID for.
     */
    public void purgeGoneJidThreads(Jid address)
    {
        if (jidThreads.containsKey(address)) {
            jidThreads.remove(address);
        }
        else if (jidThreads.containsKey(address.asBareJid())) {
            jidThreads.remove(address.asBareJid());
        }
    }

    /**
     * Returns the threadID that the party with the specified <tt>address</tt> contacted us from or
     * <tt>new ThreadID</tt> if <tt>null</tt> and <tt>generateNewIfNoExist</tt> is true; otherwise
     * <tt>null</tt> if we don't have a jid for the specified <tt>address</tt> yet.
     * <p>
     * The method would also purge all entries that haven't seen any activity (i.e. no one has
     * tried to get or remap it) for a delay longer than <tt>JID_INACTIVITY_TIMEOUT</tt>.
     *
     * @param address
     *         the <tt>address</tt> that we'd like to obtain a threadID for.
     * @param generateNewIfNoExist
     *         if <tt>true</tt> generates new threadID if null is found.
     * @return new or last threadID that the party with the specified <tt>address</tt> contacted
     * us from OR <tt>null</tt> if we don't have a jid for the specified <tt>address</tt> and
     * <tt>generateNewIfNoExist</tt> is false.
     */
    public String getThreadIDForAddress(Jid address, boolean generateNewIfNoExist)
    {
        synchronized (jidThreads) {
            purgeOldJidThreads();
            StoredThreadID ta = jidThreads.get(address);

            if (ta == null) {
                if (generateNewIfNoExist) {
                    ta = new StoredThreadID();
                    ta.threadID = nextThreadID();
                    putJidForAddress(address, ta.threadID);
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
     * @param threadID
     *         the threadID of conversation.
     * @param jid
     *         the jid (i.e. address/resource) that the contact with the specified <tt>address</tt>
     *         last contacted us from.
     */
    private void putJidForAddress(Jid jid, String threadID)
    {
        synchronized (jidThreads) {
            purgeOldJidThreads();
            StoredThreadID ta = jidThreads.get(jid);

            if (ta == null) {
                ta = new StoredThreadID();
                jidThreads.put(jid, ta);
            }
            recentJidForContact.put(jid.asBareJid(), jid);
            ta.lastUpdatedTime = System.currentTimeMillis();
            ta.threadID = threadID;
        }
    }

    /**
     * Helper function used to send a message to a contact, with the given extensions attached.
     *
     * @param to
     *         The contact to send the message to.
     * @param toResource
     *         The resource to send the message to or null if no resource has been specified
     * @param message
     *         The message to send.
     * @param extensions
     *         The XMPP extensions that should be attached to the message before sending.
     * @return The MessageDeliveryEvent that resulted after attempting to send this message, so the
     * calling function can modify it if needed.
     */
    private MessageDeliveredEvent sendMessage(Contact to, ContactResource toResource,
                                              Message message, ExtensionElement[] extensions)
    {
        if (!(to instanceof ContactJabberImpl))
            throw new IllegalArgumentException(
                    "The specified contact is not a Jabber contact: " + to);
        try {
            assertConnected();
        } catch (IllegalStateException ex) {
            MessageDeliveryFailedEvent msgDeliveryFailed
                    = new MessageDeliveryFailedEvent(message, to,
                    MessageDeliveryFailedEvent.PROVIDER_NOT_REGISTERED);
            fireMessageEvent(msgDeliveryFailed);
            throw ex;
        }

        org.jivesoftware.smack.packet.Message msg = new org.jivesoftware.smack.packet.Message();
        Jid toJid = null;
        if (toResource != null) {
            if (toResource.equals(ContactResource.BASE_RESOURCE)) {
                toJid = to.getJid();
            }
            else
                toJid = ((ContactResourceJabberImpl) toResource).getFullJid();
        }
        if (toJid == null) {
            toJid = getRecentFullJidForContactIfPossible(to);
        }

        mChat = obtainChatInstance((EntityJid) toJid);
        msg.setStanzaId(message.getMessageUID());
        msg.setTo(toJid);

        for (ExtensionElement ext : extensions) {
            msg.addExtension(ext);
        }

        if (logger.isTraceEnabled())
            logger.trace("Will send a message to: " + toJid + " chat.jid = " + toJid);

        MessageDeliveredEvent msgDeliveryPendingEvt
                = new MessageDeliveredEvent(message, to, toResource);
        MessageDeliveredEvent[] transformedEvents
                = messageDeliveryPendingTransform(msgDeliveryPendingEvt);

        if (transformedEvents == null || transformedEvents.length == 0)
            return null;

        for (MessageDeliveredEvent event : transformedEvents) {
            String content = event.getSourceMessage().getContent();

            if (message.getEncType() == ChatMessage.ENCODE_HTML) {
                msg.setBody(Html2Text.extractText(content));

                // Check if the other user supports XHTML messages make sure we use our discovery
                // manager as it caches calls
                if (jabberProvider.isFeatureListSupported(toJid, HTML_NAMESPACE)) {
                    // Add the XHTML text to the message
                    XHTMLText htmlText
                            = new XHTMLText("", "us").append(content).appendCloseBodyTag();
                    XHTMLManager.addBody(msg, htmlText);
                }
            }
            else {
                // this is plain text so keep it as it is.
                msg.setBody(content);
            }

            // msg.addExtension(new Version());
            if (event.isMessageEncrypted() && isCarbonEnabled) {
                msg.addExtension(new CarbonPacketExtension.PrivateExtension());
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
     * @param to
     *         the <tt>Contact</tt> to send <tt>message</tt> to
     * @param message
     *         the <tt>Message</tt> to send.
     * @throws java.lang.IllegalStateException
     *         if the underlying stack is not registered and initialized.
     * @throws java.lang.IllegalArgumentException
     *         if <tt>to</tt> is not an instance of ContactImpl.
     */
    public void sendInstantMessage(Contact to, Message message)
            throws IllegalStateException, IllegalArgumentException
    {
        sendInstantMessage(to, null, message);
    }

    /**
     * Sends the <tt>message</tt> to the destination indicated by the <tt>to</tt>. Provides a
     * default implementation of this method.
     *
     * @param to
     *         the <tt>Contact</tt> to send <tt>message</tt> to
     * @param toResource
     *         the resource to which the message should be send
     * @param message
     *         the <tt>Message</tt> to send.
     * @throws java.lang.IllegalStateException
     *         if the underlying ICQ stack is not registered and initialized.
     * @throws java.lang.IllegalArgumentException
     *         if <tt>to</tt> is not an instance belonging to the underlying implementation.
     */
    @Override
    public void sendInstantMessage(Contact to, ContactResource toResource, Message message)
            throws IllegalStateException, IllegalArgumentException
    {
        MessageDeliveredEvent msgDelivered
                = sendMessage(to, toResource, message, new ExtensionElement[0]);
        fireMessageEvent(msgDelivered);
    }

    public void sendInstantMessage(Contact to, ContactResource toResource, Message message,
                                   OmemoManager omemoManager)
            throws IllegalStateException, IllegalArgumentException
    {
        Jid toJid = to.getJid();
        String msgContent = message.getContent();
        org.jivesoftware.smack.packet.Message encryptedMessage = null;
        try {
            encryptedMessage = omemoManager.encrypt(toJid.asBareJid(), msgContent);
            mChat = obtainChatInstance((EntityJid) toJid);
            mChat.sendMessage(encryptedMessage);
        } catch (UndecidedOmemoIdentityException e) {
            // logger.warn("There are unTrusted Omemo device: " + e.getMessage());
            HashSet<OmemoDevice> omemoDevices = e.getUndecidedDevices();
            aTalkApp.getGlobalContext().startActivity(
                    OmemoAuthenticateDialog.createIntent(omemoManager, omemoDevices, null));
        } catch (CannotEstablishOmemoSessionException e) {
            // encryptedMessage = omemoManager.encryptForExistingSessions(e, msgContent);
            logger.warn("Omemo is unable to create session with a device: " + e.getMessage());
        } catch (CryptoFailedException | NoSuchAlgorithmException | InterruptedException
                | NotConnectedException | NoResponseException e) {
            e.printStackTrace();
        }
        MessageDeliveredEvent msgDelivered = new MessageDeliveredEvent(message, to);
        fireMessageEvent(msgDelivered);
    }

    /**
     * Replaces the message with ID <tt>correctedMessageUID</tt> sent to the contact <tt>to</tt>
     * with the message <tt>message</tt>
     *
     * @param to
     *         The contact to send the message to.
     * @param message
     *         The new message.
     * @param correctedMessageUID
     *         The ID of the message being replaced.
     */
    public void correctMessage(Contact to, ContactResource resource, Message message,
                               String correctedMessageUID)
    {
        ExtensionElement[] exts = new ExtensionElement[1];
        exts[0] = new MessageCorrectExtension(correctedMessageUID);
        MessageDeliveredEvent msgDelivered = sendMessage(to, resource, message, exts);
        msgDelivered.setCorrectedMessageUID(correctedMessageUID);
        fireMessageEvent(msgDelivered);
    }

    public void correctMessage(Contact to, ContactResource resource, Message message,
                               String correctedMessageUID, OmemoManager omemoManager)
    {
        Jid toJid = to.getJid();
        String msgContent = message.getContent();
        org.jivesoftware.smack.packet.Message encryptedMessage = null;
        try {
            encryptedMessage = omemoManager.encrypt(toJid.asBareJid(), msgContent);
            encryptedMessage.addExtension(new MessageCorrectExtension(correctedMessageUID));
            mChat = obtainChatInstance((EntityJid) toJid);
            mChat.sendMessage(encryptedMessage);
        } catch (UndecidedOmemoIdentityException e) {
            // logger.warn("There are unTrusted Omemo device: " + e.getMessage());
            HashSet<OmemoDevice> omemoDevices = e.getUndecidedDevices();
            aTalkApp.getGlobalContext().startActivity(
                    OmemoAuthenticateDialog.createIntent(omemoManager, omemoDevices, null));
        } catch (CannotEstablishOmemoSessionException e) {
            // encryptedMessage = omemoManager.encryptForExistingSessions(e, msgContent);
            logger.warn("Omemo is unable to create session with a device: " + e.getMessage());
        } catch (CryptoFailedException | NoSuchAlgorithmException | InterruptedException
                | NotConnectedException | NoResponseException e) {
            e.printStackTrace();
        }
        MessageDeliveredEvent msgDelivered
                = new MessageDeliveredEvent(message, to, correctedMessageUID);
        fireMessageEvent(msgDelivered);
    }

    /**
     * Utility method throwing an exception if the stack is not properly initialized.
     *
     * @throws java.lang.IllegalStateException
     *         if the underlying stack is not registered and initialized.
     */
    private void assertConnected()
            throws IllegalStateException
    {
        if (opSetPersPresence == null) {
            throw new IllegalStateException("The provider must be signed on the service before"
                    + " being able to communicate.");
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
         * @param evt
         *         ProviderStatusChangeEvent the event describing the status change.
         */
        public void registrationStateChanged(RegistrationStateChangeEvent evt)
        {
            XMPPTCPConnection xmppConnection = jabberProvider.getConnection();
            OmemoManager omemoManager;

            if (evt.getNewState() == RegistrationState.REGISTERING) {
                opSetPersPresence = (OperationSetPersistentPresenceJabberImpl) jabberProvider
                        .getOperationSet(OperationSetPersistentPresence.class);
                /*
                 * HashSet<OmemoMessageListener> omemoMessageListeners; so can just add
				 * but has problem as jid is not known to omemoManager yet
				 * See AndroidOmemoService implementation for fix
				 */
                // omemoManager = OmemoManager.getInstanceFor(xmppConnection);
                // registerOmemoMucListener(omemoManager);

                if (smackMessageListener == null) {
                    smackMessageListener = new SmackMessageListener();
                }
                else {
                    // make sure this listener is not already installed in this connection
                    xmppConnection.removeAsyncStanzaListener(smackMessageListener);
                }
                xmppConnection.addAsyncStanzaListener(smackMessageListener, new AndFilter(
                        packetFilters.toArray(new StanzaFilter[packetFilters.size()])));

            }
            else if (evt.getNewState() == RegistrationState.REGISTERED) {
                new Thread(new Runnable()
                {
                    @Override
                    public void run()
                    {
                        initAdditionalServices();
                    }
                }).start();
            }
            else if (evt.getNewState() == RegistrationState.UNREGISTERED
                    || evt.getNewState() == RegistrationState.CONNECTION_FAILED
                    || evt.getNewState() == RegistrationState.AUTHENTICATION_FAILED) {
                if (xmppConnection != null) {  // must not assume - may call after log off
                    if (xmppConnection.isAuthenticated()) {
                        omemoManager = OmemoManager.getInstanceFor(xmppConnection);
                        unRegisterOmemoListener(omemoManager);
                    }
                    if (smackMessageListener != null)
                        xmppConnection.removeAsyncStanzaListener(smackMessageListener);
                }
                smackMessageListener = null;
            }
        }
    }

    /**
     * Initialize additional services, like gmail notifications and message carbons.
     */
    private void initAdditionalServices()
    {
        // subscribe for Google (Gmail or Google Apps) notifications for new mail messages.
        boolean enableGmailNotifications = jabberProvider.getAccountID()
                .getAccountPropertyBoolean("GMAIL_NOTIFICATIONS_ENABLED", false);

        if (enableGmailNotifications)
            subscribeForGmailNotifications();

        enableDisableCarbon();
    }

    /**
     * Enable carbon feature if supported by server.
     */
    private void enableDisableCarbon()
    {
        boolean enableCarbon = false;
        EntityFullJid userJid = jabberProvider.getOurJID();
        CarbonManager carbonManager = CarbonManager.getInstanceFor(jabberProvider.getConnection());
        try {
            enableCarbon = carbonManager.isSupportedByServer()
                    && !jabberProvider.getAccountID().getAccountPropertyBoolean(
                    ProtocolProviderFactory.IS_CARBON_DISABLED, false);
            logger.info("Required new state for enableCarbon: " + enableCarbon);

            if (enableCarbon) {
                carbonManager.setCarbonsEnabled(true);
                isCarbonEnabled = true;
            }
            else {
                isCarbonEnabled = false;
            }
            logger.info("Successfully setting carbon new state for: " + userJid + " to "
                    + isCarbonEnabled);
        } catch (NoResponseException | InterruptedException | NotConnectedException
                | XMPPException.XMPPErrorException e) {
            logger.error("Failed to set carbon state for: " + userJid + " to "
                    + enableCarbon, e);
        }
    }

    /**
     * The listener that we use in order to handle incoming messages.
     */
    @SuppressWarnings("unchecked")
    private class SmackMessageListener implements StanzaListener
    {
        private SmackMessageListener()
        {
        }

        /**
         * Handles incoming messages and dispatches whatever events are necessary.
         *
         * @param stanza
         *         the packet that we need to handle (if it is a message).
         */
        public void processStanza(Stanza stanza)
        {
            if (!(stanza instanceof org.jivesoftware.smack.packet.Message))
                return;

            // Leave handling of omemo message to onOmemoMessageReceived()
            OmemoElement omemoMessage
                    = stanza.getExtension(OmemoElement.ENCRYPTED, OMEMO_NAMESPACE_V_AXOLOTL);
            if (omemoMessage != null)
                return;

            org.jivesoftware.smack.packet.Message msg
                    = (org.jivesoftware.smack.packet.Message) stanza;
            boolean isForwardedSentMessage = false;
            if (msg.getBody() == null) {
                CarbonPacketExtension carbonExt = (CarbonPacketExtension)
                        msg.getExtension(CarbonPacketExtension.NAMESPACE);
                if (carbonExt == null)
                    return;

                isForwardedSentMessage = carbonExt.getElementName()
                        .equalsIgnoreCase(CarbonPacketExtension.SENT_ELEMENT_NAME);
                List<ForwardedPacketExtension> extensions
                        = carbonExt.getChildExtensionsOfType(ForwardedPacketExtension.class);
                if (extensions.isEmpty())
                    return;

                // according to xep-0280 all carbons should come from our bare jid
                if (!msg.getFrom().equals(jabberProvider.getOurJID().asBareJid())) {
                    logger.info("Received a carbon copy with incorrrect from attribute!");
                    return;
                }
                ForwardedPacketExtension forwardedExt = extensions.get(0);
                msg = forwardedExt.getMessage();
                if (msg == null || msg.getBody() == null)
                    return;
            }

            // Return if it is not for us
            ExtensionElement multiChatExtension
                    = msg.getExtension("x", "http://jabber.org/protocol/muc#user");
            if (multiChatExtension != null)
                return;

            Jid userFullId = isForwardedSentMessage ? msg.getTo() : msg.getFrom();
            String userBareID = userFullId.asBareJid().toString();

            boolean isPrivateMessaging = false;
            ChatRoom privateContactRoom = null;
            OperationSetMultiUserChatJabberImpl mucOpSet = (OperationSetMultiUserChatJabberImpl)
                    jabberProvider.getOperationSet(OperationSetMultiUserChat.class);
            if (mucOpSet != null) {
                privateContactRoom = mucOpSet.getChatRoom(userBareID);
                if (privateContactRoom != null) {
                    isPrivateMessaging = true;
                }
            }

            if (logger.isDebugEnabled())
                logger.debug("Received from " + userBareID + " the message " + msg.toXML());

            String msgID = msg.getStanzaId();
            String correctedMessageUID = getCorrectionMessageId(msg);

            Message newMessage
                    = createMessageWithUID(msg.getBody(), ChatMessage.ENCODE_PLAIN, msgID);

            // check if the message is available in xhtml
            ExtensionElement ext = msg.getExtension("http://jabber.org/protocol/xhtml-im");
            if (ext != null) {
                XHTMLExtension xhtmlExt = (XHTMLExtension) ext;

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
                            .replaceAll("\\<[bB][oO][dD][yY].*?>", "")
                            // removes body end tag
                            .replaceAll("\\</[bB][oO][dD][yY].*?>", "");

                    // for some reason &apos; is not rendered correctly from our ui, lets use its
                    // equivalent. Other similar chars(< > & ") seem ok.
                    receivedMessage = receivedMessage.replaceAll("&apos;", "&#39;");
                    newMessage = createMessageWithUID(receivedMessage, ChatMessage.ENCODE_HTML,
                            msgID);
                }
            }

            Contact sourceContact = opSetPersPresence
                    .findContactByID((isPrivateMessaging ? userFullId.toString() : userBareID));
            if (msg.getType() == org.jivesoftware.smack.packet.Message.Type.error) {
                // error which is multi-chat and we don't know about the contact is a muc message
                // error which is missing muc extension and is coming from the room, when we try
                // to send message to room which was deleted or offline on the server
                XMPPError error = stanza.getError();
                if (isPrivateMessaging && sourceContact == null) {
                    int errorResultCode = ChatRoomMessageDeliveryFailedEvent.UNKNOWN_ERROR;
                    if ((error != null) && (Condition.forbidden == error.getCondition())) {
                        errorResultCode = ChatRoomMessageDeliveryFailedEvent.FORBIDDEN;
                    }

                    String errorReason = error.toString();
                    ChatRoomMessageDeliveryFailedEvent evt
                            = new ChatRoomMessageDeliveryFailedEvent(privateContactRoom,
                            null, errorResultCode, errorReason, new Date(), newMessage);
                    ((ChatRoomJabberImpl) privateContactRoom).fireMessageEvent(evt);
                    return;
                }

                if (logger.isInfoEnabled())
                    logger.info("Message error received from " + userBareID);

                int errorResultCode = MessageDeliveryFailedEvent.UNKNOWN_ERROR;
                if (error != null) {
                    Condition errorCondition = error.getCondition();
                    if (Condition.service_unavailable == errorCondition) {
                        if (!sourceContact.getPresenceStatus().isOnline()) {
                            errorResultCode
                                    = MessageDeliveryFailedEvent.OFFLINE_MESSAGES_NOT_SUPPORTED;
                        }
                    }
                }
                if (sourceContact == null) {
                    sourceContact = opSetPersPresence.createVolatileContact(userFullId.toString(),
                            isPrivateMessaging);
                }

                MessageDeliveryFailedEvent ev = new MessageDeliveryFailedEvent(newMessage,
                        sourceContact, correctedMessageUID, errorResultCode);
                fireMessageEvent(ev);
                return;
            }
            putJidForAddress(userFullId, msg.getThread());

            // In the second condition we filter all group chat messages, because they are managed
            // by the multi user chat operation set.
            if (sourceContact == null) {
                if (logger.isDebugEnabled())
                    logger.debug("received a message from an unknown contact: " + userBareID);
                // create the volatile contact
                sourceContact = opSetPersPresence.createVolatileContact(userFullId.toString(),
                        isPrivateMessaging);
            }

            Date timestamp = getTimeStamp(msg);
            ContactResource resource
                    = ((ContactJabberImpl) sourceContact).getResourceFromJid(userFullId);

            EventObject msgEvt = null;
            if (!isForwardedSentMessage)
                msgEvt = new MessageReceivedEvent(newMessage, sourceContact, resource, timestamp,
                        correctedMessageUID, isPrivateMessaging, privateContactRoom);
            else
                msgEvt = new MessageDeliveredEvent(newMessage, sourceContact, timestamp);

            // msgReceivedEvt = messageReceivedTransform(msgReceivedEvt);
            fireMessageEvent(msgEvt);
        }
    }

    /**
     * A filter that prevents this operation set from handling multi user chat messages.
     */
    private static class GroupMessagePacketFilter implements StanzaFilter
    {
        /**
         * Returns <tt>true</tt> if <tt>packet</tt> is a <tt>Message</tt> and false otherwise.
         *
         * @param packet
         *         the packet that we need to check.
         * @return <tt>true</tt> if <tt>packet</tt> is a <tt>Message</tt> and false otherwise.
         */
        public boolean accept(Stanza packet)
        {
            if (!(packet instanceof org.jivesoftware.smack.packet.Message))
                return false;

            org.jivesoftware.smack.packet.Message msg
                    = (org.jivesoftware.smack.packet.Message) packet;
            return (!(msg.getType() == org.jivesoftware.smack.packet.Message.Type.groupchat));
        }
    }

    /**
     * Subscribes this provider as interested in receiving notifications for new mail messages from
     * Google mail services such as Gmail or Google Apps.
     */
    private void subscribeForGmailNotifications()
    {
        // first check support for the notification service
        Jid accountIDService = null;
        try {
            accountIDService = JidCreate.from(jabberProvider.getAccountID().getService());
        } catch (XmppStringprepException e) {
            e.printStackTrace();
        }
        boolean notificationsAreSupported = jabberProvider.isFeatureSupported(accountIDService,
                NewMailNotificationIQ.NAMESPACE);

        if (!notificationsAreSupported) {
            if (logger.isDebugEnabled())
                logger.debug(accountIDService
                        + " does not seem to provide a Gmail notification service so we won't be" +
                        " trying to subscribe for it");
            return;
        }

        if (logger.isDebugEnabled())
            logger.debug(accountIDService
                    + " seems to provide a Gmail notification service so we will try to " +
                    "subscribe for it");

        // ProviderManager providerManager = ProviderManager.getInstance();
        ProviderManager.addIQProvider(MailboxIQ.ELEMENT_NAME, MailboxIQ.NAMESPACE,
                new MailboxIQProvider());
        ProviderManager.addIQProvider(NewMailNotificationIQ.ELEMENT_NAME,
                NewMailNotificationIQ.NAMESPACE, new NewMailNotificationProvider());

        XMPPTCPConnection xmppConnection = jabberProvider.getConnection();
        xmppConnection.addAsyncStanzaListener(new MailboxIQListener(), new StanzaTypeFilter(
                MailboxIQ.class));
        xmppConnection.addAsyncStanzaListener(new NewMailNotificationListener(),
                new StanzaTypeFilter(NewMailNotificationIQ.class));

        if (opSetPersPresence.getCurrentStatusMessage().equals(JabberStatusEnum.OFFLINE))
            return;

        // create a query with -1 values for newer-than-tid and newer-than-time attributes
        MailboxQueryIQ mailboxQuery = new MailboxQueryIQ();

        if (logger.isTraceEnabled())
            logger.trace("sending mailNotification for acc: "
                    + jabberProvider.getAccountID().getAccountUniqueID());
        try {
            xmppConnection.sendStanza(mailboxQuery);
        } catch (NotConnectedException | InterruptedException e) {
            e.printStackTrace();
        }
    }

    /**
     * Creates an html description of the specified mailbox.
     *
     * @param mailboxIQ
     *         the mailboxIQ that we are to describe.
     * @return an html description of <tt>mailboxIQ</tt>
     */
    private String createMailboxDescription(MailboxIQ mailboxIQ)
    {
        int threadCount = mailboxIQ.getThreadCount();

        String resourceHeaderKey = (threadCount > 1) ? "service.gui.NEW_GMAIL_MANY_HEADER"
                : "service.gui.NEW_GMAIL_HEADER";
        String resourceFooterKey = (threadCount > 1) ? "service.gui.NEW_GMAIL_MANY_FOOTER"
                : "service.gui.NEW_GMAIL_FOOTER";

        // FIXME Escape HTML!
        String newMailHeader = JabberActivator.getResources().getI18NString(resourceHeaderKey,
                new String[]{jabberProvider.getAccountID().getService(), // {0}
                        // - service name
                        mailboxIQ.getUrl(), // {1} - inbox URI
                        Integer.toString(threadCount) // {2} - thread count
                });

        StringBuilder message = new StringBuilder(newMailHeader);

        // we now start an html table for the threads.
        message.append("<table width=100% cellpadding=2 cellspacing=0 ");
        message.append("border=0 bgcolor=#e8eef7>");

        Iterator<MailThreadInfo> threads = mailboxIQ.threads();
        String maxThreadsStr = (String) JabberActivator.getConfigurationService().getProperty(
                PNAME_MAX_GMAIL_THREADS_PER_NOTIFICATION);

        int maxThreads = 5;
        try {
            if (maxThreadsStr != null)
                maxThreads = Integer.parseInt(maxThreadsStr);
        } catch (NumberFormatException e) {
            if (logger.isDebugEnabled())
                logger.debug("Failed to parse max threads count: " + maxThreads
                        + ". Going for default.");
        }

        // print a maximum of MAX_THREADS
        for (int i = 0; i < maxThreads && threads.hasNext(); i++) {
            message.append(threads.next().createHtmlDescription());
        }
        message.append("</table><br/>");

        if (threadCount > maxThreads) {
            String messageFooter = JabberActivator.getResources().getI18NString(resourceFooterKey,
                    new String[]{mailboxIQ.getUrl(),
                            // {0} - inbox URI
                            Integer.toString(threadCount - maxThreads) // {1} - thread count
                    });
            message.append(messageFooter);
        }
        return message.toString();
    }

    public Jid getRecentFullJidForContactIfPossible(Contact contact)
    {
        Jid contactJid = contact.getJid();
        Jid jid = recentJidForContact.get(contactJid.asBareJid());
        if (jid == null)
            jid = contactJid;
        return jid;
    }

    public Chat getChat(EntityJid jid)
    {
        return mChat;
    }

    /**
     * Receives incoming MailNotification Packets
     */
    private class MailboxIQListener implements StanzaListener
    {
        /**
         * Handles incoming <tt>MailboxIQ</tt> packets.
         *
         * @param packet
         *         the IQ that we need to handle in case it is a <tt>MailboxIQ</tt>.
         */
        public void processStanza(Stanza packet)
        {
            if (packet != null && !(packet instanceof MailboxIQ))
                return;

            MailboxIQ mailboxIQ = (MailboxIQ) packet;

            if (mailboxIQ.getTotalMatched() < 1)
                return;

            // Get a reference to a dummy volatile contact
            Contact sourceContact = opSetPersPresence.findContactByID(
                    jabberProvider.getAccountID().getService());

            if (sourceContact == null)
                sourceContact = opSetPersPresence.createVolatileContact(
                        jabberProvider.getAccountID().getService());

            lastReceivedMailboxResultTime = mailboxIQ.getResultTime();
            String newMail = createMailboxDescription(mailboxIQ);
            Message newMailMessage = new MessageJabberImpl(newMail, ChatMessage.ENCODE_HTML,
                    null);

            MessageReceivedEvent msgReceivedEvt = new MessageReceivedEvent(newMailMessage,
                    sourceContact, new Date(), MessageReceivedEvent.SYSTEM_MESSAGE_RECEIVED);

            fireMessageEvent(msgReceivedEvt);
        }
    }

    /**
     * Receives incoming NewMailNotification Packets.
     */
    private class NewMailNotificationListener implements StanzaListener
    {
        /**
         * Handles incoming <tt>NewMailNotificationIQ</tt> packets.
         *
         * @param packet
         *         the IQ that we need to handle in case it is a <tt>NewMailNotificationIQ</tt>.
         */
        public void processStanza(Stanza packet)
        {
            if (packet != null && !(packet instanceof NewMailNotificationIQ))
                return;

            // check whether we are still enabled.
            boolean enableGmailNotifications = jabberProvider.getAccountID()
                    .getAccountPropertyBoolean("GMAIL_NOTIFICATIONS_ENABLED", false);

            if (!enableGmailNotifications)
                return;

            if (opSetPersPresence.getCurrentStatusMessage().equals(JabberStatusEnum.OFFLINE))
                return;

            MailboxQueryIQ mailboxQueryIQ = new MailboxQueryIQ();

            if (lastReceivedMailboxResultTime != -1)
                mailboxQueryIQ.setNewerThanTime(lastReceivedMailboxResultTime);

            if (logger.isTraceEnabled())
                logger.trace("send mailNotification for acc: "
                        + jabberProvider.getAccountID().getAccountUniqueID());

            try {
                jabberProvider.getConnection().sendStanza(mailboxQueryIQ);
            } catch (NotConnectedException | InterruptedException e) {
                e.printStackTrace();
            }
        }
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
     * Adds additional filters for incoming messages. To be able to skip some messages.
     *
     * @param filter
     *         to add
     */
    public void addMessageFilters(StanzaFilter filter)
    {
        this.packetFilters.add(filter);
    }

    /**
     * Returns the next unique thread id. Each thread id made up of a short alphanumeric prefix
     * along with a unique numeric value.
     *
     * @return the next thread id.
     */
    public static synchronized String nextThreadID()
    {
        return prefix + Long.toString(id++);
    }

    /**
     * Return XEP-0203 time-stamp of the message if present or current time;
     *
     * @param msg
     *         Message
     * @return the correct message timeStamp
     */
    private Date getTimeStamp(org.jivesoftware.smack.packet.Message msg)
    {
        Date timeStamp;
        DelayInformation delayInfo
                = msg.getExtension(DelayInformation.ELEMENT, DelayInformation.NAMESPACE);
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
     * @param message
     *         Message
     * @return messageCorrectionID if presence or null
     */
    private String getCorrectionMessageId(org.jivesoftware.smack.packet.Message message)
    {
        ExtensionElement correctionExtension
                = message.getExtension(MessageCorrectExtension.NAMESPACE);
        if (correctionExtension != null) {
            return ((MessageCorrectExtension) correctionExtension).getIdInitialMessage();
        }
        return null;
    }

    // =============== OMEMO message received =============== //

    public void registerOmemoListener(OmemoManager omemoManager)
    {
        omemoManager.addOmemoMessageListener(this);
    }

    public void unRegisterOmemoListener(OmemoManager omemoManager)
    {
        omemoManager.removeOmemoMessageListener(this);
    }

    /**
     * Gets called, whenever an OmemoMessage has been received and was successfully decrypted.
     *
     * @param decryptedBody
     *         Decrypted body
     * @param encryptedMessage
     *         Encrypted Message
     * @param wrappingMessage
     *         Wrapping carbon message, in case the message was a carbon copy, else null.
     * @param omemoInformation
     *         Information about the messages encryption etc.
     */
    @Override
    public void onOmemoMessageReceived(String decryptedBody,
                                       org.jivesoftware.smack.packet.Message encryptedMessage,
                                       org.jivesoftware.smack.packet.Message wrappingMessage,
                                       OmemoMessageInformation omemoInformation)
    {
        Jid fromJid = encryptedMessage.getFrom();
        putJidForAddress(fromJid, encryptedMessage.getThread());
        Contact sourceContact = opSetPersPresence.findContactByID(fromJid.toString());
        Date timeStamp = getTimeStamp(encryptedMessage);

        String msgID = encryptedMessage.getStanzaId();
        String correctedMsgID = getCorrectionMessageId(encryptedMessage);

        Message newMessage = createMessageWithUID(decryptedBody, ChatMessage.ENCODE_PLAIN, msgID);
        MessageReceivedEvent msgReceivedEvt =
                new MessageReceivedEvent(newMessage, sourceContact, timeStamp, correctedMsgID);
        fireMessageEvent(msgReceivedEvt);
    }

    /**
     * Gets called, whenever an OmemoElement without a body (an OmemoKeyTransportElement) is
     * received.
     *
     * @param cipherAndAuthTag
     *         transported Cipher along with an optional AuthTag
     * @param message
     *         Message that contained the KeyTransport
     * @param wrappingMessage
     *         Wrapping message (eg. carbon), or null
     * @param omemoInformation
     *         Information about the messages encryption etc.
     */
    @Override
    public void onOmemoKeyTransportReceived(CipherAndAuthTag cipherAndAuthTag,
                                            org.jivesoftware.smack.packet.Message message,
                                            org.jivesoftware.smack.packet.Message wrappingMessage,
                                            OmemoMessageInformation omemoInformation)
    {
    }
}
