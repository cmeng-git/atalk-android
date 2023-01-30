/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package net.java.sip.communicator.impl.protocol.jabber;

import static org.jivesoftware.smackx.omemo.util.OmemoConstants.OMEMO_NAMESPACE_V_AXOLOTL;

import android.content.Context;
import android.text.Html;
import android.text.TextUtils;

import androidx.fragment.app.Fragment;

import net.java.sip.communicator.plugin.notificationwiring.NotificationManager;
import net.java.sip.communicator.service.contactlist.MetaContact;
import net.java.sip.communicator.service.protocol.AbstractOperationSetBasicInstantMessaging;
import net.java.sip.communicator.service.protocol.Contact;
import net.java.sip.communicator.service.protocol.ContactResource;
import net.java.sip.communicator.service.protocol.IMessage;
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
import net.java.sip.communicator.util.ConfigurationUtils;

import org.apache.commons.lang3.RandomStringUtils;
import org.atalk.android.R;
import org.atalk.android.aTalkApp;
import org.atalk.android.gui.AndroidGUIActivator;
import org.atalk.android.gui.aTalk;
import org.atalk.android.gui.chat.ChatMessage;
import org.atalk.android.gui.contactlist.ContactListFragment;
import org.atalk.android.gui.util.XhtmlUtil;
import org.atalk.android.plugin.timberlog.TimberLog;
import org.atalk.crypto.omemo.OmemoAuthenticateDialog;
import org.jivesoftware.smack.SmackException;
import org.jivesoftware.smack.SmackException.NoResponseException;
import org.jivesoftware.smack.SmackException.NotConnectedException;
import org.jivesoftware.smack.StanzaListener;
import org.jivesoftware.smack.XMPPConnection;
import org.jivesoftware.smack.XMPPException;
import org.jivesoftware.smack.chat2.Chat;
import org.jivesoftware.smack.chat2.ChatManager;
import org.jivesoftware.smack.chat2.IncomingChatMessageListener;
import org.jivesoftware.smack.filter.AndFilter;
import org.jivesoftware.smack.filter.FromTypeFilter;
import org.jivesoftware.smack.filter.MessageTypeFilter;
import org.jivesoftware.smack.filter.MessageWithBodiesFilter;
import org.jivesoftware.smack.filter.OrFilter;
import org.jivesoftware.smack.filter.StanzaExtensionFilter;
import org.jivesoftware.smack.filter.StanzaFilter;
import org.jivesoftware.smack.packet.ExtensionElement;
import org.jivesoftware.smack.packet.Message;
import org.jivesoftware.smack.packet.MessageBuilder;
import org.jivesoftware.smack.packet.Stanza;
import org.jivesoftware.smack.packet.StanzaBuilder;
import org.jivesoftware.smack.packet.StanzaError;
import org.jivesoftware.smack.packet.StanzaError.Condition;
import org.jivesoftware.smack.parsing.SmackParsingException;
import org.jivesoftware.smack.util.PacketParserUtils;
import org.jivesoftware.smack.xml.XmlPullParser;
import org.jivesoftware.smack.xml.XmlPullParserException;
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
import org.jivesoftware.smackx.omemo.exceptions.CorruptedOmemoKeyException;
import org.jivesoftware.smackx.omemo.exceptions.CryptoFailedException;
import org.jivesoftware.smackx.omemo.exceptions.NoRawSessionException;
import org.jivesoftware.smackx.omemo.exceptions.UndecidedOmemoIdentityException;
import org.jivesoftware.smackx.omemo.internal.OmemoDevice;
import org.jivesoftware.smackx.omemo.listener.OmemoMessageListener;
import org.jivesoftware.smackx.omemo.provider.OmemoVAxolotlProvider;
import org.jivesoftware.smackx.xhtmlim.XHTMLManager;
import org.jivesoftware.smackx.xhtmlim.XHTMLText;
import org.jivesoftware.smackx.xhtmlim.packet.XHTMLExtension;
import org.jxmpp.jid.BareJid;
import org.jxmpp.jid.EntityBareJid;
import org.jxmpp.jid.EntityFullJid;
import org.jxmpp.jid.Jid;

import java.io.IOException;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.EventObject;
import java.util.Hashtable;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;

import timber.log.Timber;

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
        implements RegistrationStateChangeListener, OperationSetMessageCorrection, IncomingChatMessageListener,
        StanzaListener, CarbonCopyReceivedListener, OmemoMessageListener {
    /**
     * A table mapping contact addresses to message threads that can be used to target a specific
     * resource (rather than sending a message to all logged instances of a user).
     */
    private final Map<BareJid, StoredThreadID> jidThreads = new Hashtable<>();

    /**
     * The most recent FullJid used for the contact address.
     */
    private final Map<BareJid, Jid> recentJidForContact = new Hashtable<>();

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
    public static class StoredThreadID {
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
    private static final String prefix = RandomStringUtils.random(5);

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
    private final ProtocolProviderServiceJabberImpl mPPS;

    private String mUserJid;

    private OmemoManager mOmemoManager;

    private final OmemoVAxolotlProvider omemoVAxolotlProvider = new OmemoVAxolotlProvider();

    /**
     * A reference to the persistent presence operation set that we use to match incoming messages
     * to <code>Contact</code>s and vice versa.
     */
    private OperationSetPersistentPresenceJabberImpl opSetPersPresence = null;

    /**
     * Whether carbon is enabled or not.
     */
    private boolean isCarbonEnabled = false;

    // Indicate if the received message is carbon copy
    private boolean isCarbon = false;

    /**
     * Message filter to listen for message sent from DomianJid i.e. server with normal or
     * has extensionElement i.e. XEP-0071: XHTML-IM
     */
    private static final StanzaFilter MESSAGE_FILTER = new AndFilter(
            MessageTypeFilter.NORMAL_OR_CHAT, new OrFilter(MessageWithBodiesFilter.INSTANCE,
            new StanzaExtensionFilter(XHTMLExtension.ELEMENT, XHTMLExtension.NAMESPACE))
    );
    private static final StanzaFilter INCOMING_SVR_MESSAGE_FILTER
            = new AndFilter(MESSAGE_FILTER, FromTypeFilter.DOMAIN_BARE_JID);

    /**
     * Creates an instance of this operation set.
     *
     * @param provider a reference to the <code>ProtocolProviderServiceImpl</code> that created us and that we'll
     * use for retrieving the underlying aim connection.
     */
    OperationSetBasicInstantMessagingJabberImpl(ProtocolProviderServiceJabberImpl provider) {
        mPPS = provider;
        mPPS.addRegistrationStateChangeListener(this);
    }

    /**
     * Create a IMessage instance with the specified UID, content type and a default encoding. This
     * method can be useful when message correction is required. One can construct the corrected
     * message to have the same UID as the message before correction.
     *
     * @param messageText the string content of the message.
     * @param encType the encryption type for the <code>content</code>
     * @param messageUID the unique identifier of this message.
     *
     * @return IMessage the newly created message
     */
    public IMessage createMessageWithUID(String messageText, int encType, String messageUID) {
        return new MessageJabberImpl(messageText, encType, null, messageUID);
    }

    /**
     * Create a IMessage instance for sending arbitrary MIME-encoding content.
     *
     * @param content content value
     * @param encType the encryption type for the <code>content</code>
     *
     * @return the newly created message.
     */
    public IMessage createMessage(String content, int encType) {
        return createMessage(content, encType, null);
    }

    /**
     * Create a IMessage instance for sending arbitrary MIME-encoding content.
     *
     * @param content content value
     * @param encType the encryption type for the <code>content</code>
     * @param subject the Subject of the message that we'd like to create.
     *
     * @return the newly created message.
     */
    @Override
    public IMessage createMessage(String content, int encType, String subject) {
        return new MessageJabberImpl(content, encType, subject, null);
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
     * @return <code>true</code> if the protocol supports offline messages and <code>false</code> otherwise.
     */
    public boolean isOfflineMessagingSupported() {
        return true;
    }

    /**
     * Determines whether the protocol supports the supplied content type
     *
     * @param mimeType the encryption type we want to check
     *
     * @return <code>true</code> if the protocol supports it and <code>false</code> otherwise.
     */
    public boolean isContentTypeSupported(int mimeType) {
        return ((IMessage.ENCODE_PLAIN == mimeType) || (IMessage.ENCODE_HTML == mimeType));
    }

    /**
     * Determines whether the protocol supports the supplied content type for the given contact.
     *
     * @param mimeType the encryption type we want to check
     * @param contact contact which is checked for supported encType
     *
     * @return <code>true</code> if the contact supports it and <code>false</code> otherwise.
     */
    @Override
    public boolean isContentTypeSupported(int mimeType, Contact contact) {
        // by default we support default mime type, for other mime types method must be overridden
        if (IMessage.ENCODE_PLAIN == mimeType) {
            return true;
        } else if (IMessage.ENCODE_HTML == mimeType) {
            Jid toJid = getRecentFullJidForContactIfPossible(contact);
            return mPPS.isFeatureListSupported(toJid, XHTMLExtension.NAMESPACE);
        }
        return false;
    }

    /**
     * Remove from our <code>jidThreads</code> map all entries that have not seen any activity
     * (i.e. neither outgoing nor incoming messages) for more than JID_INACTIVITY_TIMEOUT.
     * Note that this method is not synchronous and that it is only meant for use by the
     * {@link #getThreadIDForAddress(BareJid, boolean)} and {@link #putJidForAddress(Jid, String)}
     */
    private void purgeOldJidThreads() {
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
     * @param bareJid the <code>address</code> that we'd like to remove a threadID for.
     */
    public void purgeGoneJidThreads(BareJid bareJid) {
        jidThreads.remove(bareJid);
    }

    /**
     * Returns the threadID that the party with the specified <code>address</code> contacted us from or
     * <code>new ThreadID</code> if <code>null</code> and <code>generateNewIfNoExist</code> is true; otherwise
     * <code>null</code> if we don't have a jid for the specified <code>address</code> yet.
     *
     * The method would also purge all entries that haven't seen any activity (i.e. no one has
     * tried to get or remap it) for a delay longer than <code>JID_INACTIVITY_TIMEOUT</code>.
     *
     * @param bareJid the <code>Jid</code> that we'd like to obtain a threadID for.
     * @param generateNewIfNoExist if <code>true</code> generates new threadID if null is found.
     *
     * @return new or last threadID that the party with the specified <code>address</code> contacted
     * us from OR <code>null</code> if we don't have a jid for the specified <code>address</code> and
     * <code>generateNewIfNoExist</code> is false.
     */
    public String getThreadIDForAddress(BareJid bareJid, boolean generateNewIfNoExist) {
        synchronized (jidThreads) {
            purgeOldJidThreads();
            StoredThreadID ta = jidThreads.get(bareJid);

            // https://xmpp.org/extensions/xep-0201.html message thread Id is only recommended. buddy may sent without it
            if ((ta == null) || (ta.threadID == null)) {
                if (generateNewIfNoExist) {
                    ta = new StoredThreadID();
                    ta.threadID = nextThreadID();
                    putJidForAddress(bareJid, ta.threadID);
                } else {
                    return null;
                }
            }

            ta.lastUpdatedTime = System.currentTimeMillis();
            return ta.threadID;
        }
    }

    /**
     * Maps the specified <code>address</code> to <code>jid</code>. The point of this method is to allow us
     * to send all messages destined to the contact with the specified <code>address</code> to the
     * <code>jid</code> that they last contacted us from.
     *
     * @param threadID the threadID of conversation.
     * @param jid the jid (i.e. address/resource) that the contact with the specified <code>address</code>
     * last contacted us from.
     */
    private void putJidForAddress(Jid jid, String threadID) {
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
     * @param extElements The XMPP extensions that should be attached to the message before sending.
     *
     * @return The MessageDeliveryEvent that resulted after attempting to send this message, so the
     * calling function can modify it if needed.
     */
    private MessageDeliveredEvent sendMessage(Contact to, ContactResource toResource,
            IMessage message, Collection<ExtensionElement> extElements) {
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

        EntityBareJid toJid = to.getJid().asEntityBareJidIfPossible();
        mChat = mChatManager.chatWith(toJid);
        String threadID = getThreadIDForAddress(toJid, true);
        Date timeStamp = new Date();

        MessageBuilder messageBuilder = StanzaBuilder.buildMessage(message.getMessageUID())
                .ofType(Message.Type.chat)
                .to(toJid)
                .from(mPPS.getConnection().getUser())
                .setThread(threadID)
                .addExtensions(extElements);

        Timber.log(TimberLog.FINER, "MessageDeliveredEvent - Sending a message to: %s", toJid);

        message.setServerMsgId(messageBuilder.getStanzaId());
        MessageDeliveredEvent msgDeliveryPendingEvt = new MessageDeliveredEvent(message, to, toResource, mUserJid, timeStamp);
        MessageDeliveredEvent[] transformedEvents = messageDeliveryPendingTransform(msgDeliveryPendingEvt);

        if (transformedEvents == null || transformedEvents.length == 0) {
            MessageDeliveryFailedEvent msgDeliveryFailed
                    = new MessageDeliveryFailedEvent(message, to, MessageDeliveryFailedEvent.UNSUPPORTED_OPERATION);
            fireMessageEvent(msgDeliveryFailed);
            return null;
        }

        message.setReceiptStatus(ChatMessage.MESSAGE_DELIVERY_CLIENT_SENT);
        for (MessageDeliveredEvent event : transformedEvents) {
            String content = event.getSourceMessage().getContent();

            if (IMessage.ENCODE_HTML == message.getMimeType()) {
                messageBuilder.addBody(null, Html.fromHtml(content).toString());

                // Just add XHTML element as it will be ignored by buddy without XEP-0071: XHTML-IM support
                // Also carbon messages may send to buddy on difference clients with different capabilities
                // Note isFeatureListSupported must use FullJid unless it is for service e.g. conference.atalk.org

                // Check if the buddy supports XHTML messages make sure we use our discovery manager as it caches calls
                // if (jabberProvider.isFeatureListSupported(toJid, XHTMLExtension.NAMESPACE)) {
                // Add the XHTML text to the message
                XHTMLText htmlText = new XHTMLText("", "us")
                        .append(content)
                        .appendCloseBodyTag();

                XHTMLExtension xhtmlExtension = new XHTMLExtension();
                xhtmlExtension.addBody(htmlText.toXML());
                messageBuilder.addExtension(xhtmlExtension);
            } else {
                // this is plain text so keep it as it is.
                messageBuilder.addBody(null, content);
            }

            // msg.addExtension(new Version());
            // Disable carbon for OTR message
            if (event.isMessageEncrypted() && isCarbonEnabled) {
                CarbonExtension.Private.addTo(messageBuilder);
            }

            // Add ChatState.active extension to message send if option is enabled
            if (ConfigurationUtils.isSendChatStateNotifications()) {
                ChatStateExtension extActive = new ChatStateExtension(ChatState.active);
                messageBuilder.addExtension(extActive);
            }

            try {
                mChat.send(messageBuilder.build());
            } catch (NotConnectedException | InterruptedException e) {
                e.printStackTrace();
            }
            putJidForAddress(toJid, threadID);
        }
        return new MessageDeliveredEvent(message, to, toResource, mUserJid, timeStamp);
    }

    /**
     * Sends the <code>message</code> to the destination indicated by the <code>to</code> contact.
     *
     * @param to the <code>Contact</code> to send <code>message</code> to
     * @param message the <code>IMessage</code> to send.
     *
     * @throws java.lang.IllegalStateException    if the underlying stack is not registered and initialized.
     * @throws java.lang.IllegalArgumentException if <code>to</code> is not an instance of ContactImpl.
     */
    public void sendInstantMessage(Contact to, IMessage message) {
        sendInstantMessage(to, null, message);
    }

    /**
     * Sends the <code>message</code> to the destination indicated by the <code>to</code>. Provides a
     * default implementation of this method.
     *
     * @param to the <code>Contact</code> to send <code>message</code> to
     * @param resource the resource to which the message should be send
     * @param message the <code>IMessage</code> to send.
     *
     * @throws java.lang.IllegalStateException    if the underlying ICQ stack is not registered and initialized.
     * @throws java.lang.IllegalArgumentException if <code>to</code> is not an instance belonging to the underlying implementation.
     */
    @Override
    public void sendInstantMessage(Contact to, ContactResource resource, IMessage message) {
        MessageDeliveredEvent msgDelivered = sendMessage(to, resource, message, Collections.emptyList());
        if (msgDelivered != null) {
            fireMessageEvent(msgDelivered);
        }
    }

    /**
     * Replaces the message with ID <code>correctedMessageUID</code> sent to the contact <code>to</code>
     * with the message <code>message</code>
     *
     * @param to The contact to send the message to.
     * @param message The new message.
     * @param correctedMessageUID The ID of the message being replaced.
     */
    public void correctMessage(Contact to, ContactResource resource, IMessage message, String correctedMessageUID) {
        Collection<ExtensionElement> extElements
                = Collections.singletonList(new MessageCorrectExtension(correctedMessageUID));

        MessageDeliveredEvent msgDelivered = sendMessage(to, resource, message, extElements);
        if (msgDelivered != null) {
            msgDelivered.setCorrectedMessageUID(correctedMessageUID);
            fireMessageEvent(msgDelivered);
        }
    }

    public void sendInstantMessage(Contact to, ContactResource resource, IMessage message, String correctedMessageUID,
            final OmemoManager omemoManager) {
        BareJid bareJid = to.getJid().asBareJid();
        String msgContent = message.getContent();
        String errMessage = null;

        try {
            OmemoMessage.Sent encryptedMessage = omemoManager.encrypt(bareJid, msgContent);

            MessageBuilder messageBuilder = StanzaBuilder.buildMessage(message.getMessageUID());
            if (correctedMessageUID != null)
                messageBuilder.addExtension(new MessageCorrectExtension(correctedMessageUID));
            Message sendMessage = encryptedMessage.buildMessage(messageBuilder, bareJid);

            if (IMessage.ENCODE_HTML == message.getMimeType()) {
                // Make this into encrypted xhtmlText for inclusion
                String xhtmlEncrypted = encryptedMessage.getElement().toXML().toString();
                XHTMLText xhtmlText = new XHTMLText("", "us")
                        .append(xhtmlEncrypted)
                        .appendCloseBodyTag();

                // OMEMO body message content will strip off any xhtml tags info
                msgContent = Html.fromHtml(msgContent).toString();
                encryptedMessage = omemoManager.encrypt(bareJid, msgContent);

                messageBuilder = StanzaBuilder.buildMessage(message.getMessageUID());
                if (correctedMessageUID != null)
                    messageBuilder.addExtension(new MessageCorrectExtension(correctedMessageUID));

                // Add the XHTML text to the message builder
                XHTMLManager.addBody(messageBuilder, xhtmlText);
                sendMessage = encryptedMessage.buildMessage(messageBuilder, bareJid);
            }

            // proceed to send the message if there is no exception.
            mChat = mChatManager.chatWith(bareJid.asEntityBareJidIfPossible());
            mChat.send(sendMessage);

            message.setServerMsgId(sendMessage.getStanzaId());
            message.setReceiptStatus(ChatMessage.MESSAGE_DELIVERY_CLIENT_SENT);
            MessageDeliveredEvent msgDelivered;
            msgDelivered = new MessageDeliveredEvent(message, to, resource, mUserJid, correctedMessageUID);

            fireMessageEvent(msgDelivered);
        } catch (UndecidedOmemoIdentityException e) {
            OmemoAuthenticateListener omemoAuthListener
                    = new OmemoAuthenticateListener(to, resource, message, correctedMessageUID, omemoManager);
            Context ctx = aTalkApp.getGlobalContext();
            ctx.startActivity(OmemoAuthenticateDialog.createIntent(ctx, omemoManager, e.getUndecidedDevices(), omemoAuthListener));
            return;
        } catch (CryptoFailedException | InterruptedException | NotConnectedException | NoResponseException | IOException e) {
            errMessage = aTalkApp.getResString(R.string.crypto_msg_OMEMO_SESSION_SETUP_FAILED, e.getMessage());
        } catch (SmackException.NotLoggedInException e) {
            errMessage = aTalkApp.getResString(R.string.service_gui_MSG_SEND_CONNECTION_PROBLEM);
        }

        if (!TextUtils.isEmpty(errMessage)) {
            Timber.w("%s", errMessage);
            MessageDeliveryFailedEvent failedEvent = new MessageDeliveryFailedEvent(message, to,
                    MessageDeliveryFailedEvent.OMEMO_SEND_ERROR, System.currentTimeMillis(), errMessage);
            fireMessageEvent(failedEvent);
        }
    }

    /**
     * Omemo listener callback on user authentication for undecided omemoDevices
     */
    private class OmemoAuthenticateListener implements OmemoAuthenticateDialog.AuthenticateListener {
        Contact to;
        ContactResource resource;
        IMessage message;
        String correctedMessageUID;
        OmemoManager omemoManager;

        OmemoAuthenticateListener(Contact to, ContactResource resource, IMessage message, String correctedMessageUID,
                OmemoManager omemoManager) {
            this.to = to;
            this.resource = resource;
            this.message = message;
            this.correctedMessageUID = correctedMessageUID;
            this.omemoManager = omemoManager;
        }

        @Override
        public void onAuthenticate(boolean allTrusted, Set<OmemoDevice> omemoDevices) {
            if (allTrusted) {
                sendInstantMessage(to, resource, message, correctedMessageUID, omemoManager);
            } else {
                String errMessage = aTalkApp.getResString(R.string.omemo_send_error,
                        "Undecided Omemo Identity: " + omemoDevices.toString());
                Timber.w("%s", errMessage);
                MessageDeliveryFailedEvent failedEvent = new MessageDeliveryFailedEvent(message, to,
                        MessageDeliveryFailedEvent.OMEMO_SEND_ERROR, System.currentTimeMillis(), errMessage);
                fireMessageEvent(failedEvent);
            }
        }
    }

    /**
     * Utility method throwing an exception if the stack is not properly initialized.
     *
     * @throws java.lang.IllegalStateException if the underlying stack is not registered and initialized.
     */
    private void assertConnected()
            throws IllegalStateException {
        if (opSetPersPresence == null) {
            throw new IllegalStateException("The provider must be sign in before able to communicate.");
        } else
            opSetPersPresence.assertConnected();
    }

    /**
     * The method is called by a ProtocolProvider implementation whenever a change in the
     * registration state of the corresponding provider had occurred.
     *
     * @param evt ProviderStatusChangeEvent the event describing the status change.
     */
    public void registrationStateChanged(RegistrationStateChangeEvent evt) {
        XMPPConnection connection = mPPS.getConnection();

        if (evt.getNewState() == RegistrationState.REGISTERING) {
            opSetPersPresence = (OperationSetPersistentPresenceJabberImpl)
                    mPPS.getOperationSet(OperationSetPersistentPresence.class);
            /*
             * HashSet<OmemoMessageListener> omemoMessageListeners;
             * Cannot just add here, has a problem as jid is not known to omemoManager yet
             * See AndroidOmemoService implementation for fix
             */
            // OmemoManager omemoManager = OmemoManager.getInstanceFor(xmppConnection);
            // registerOmemoMucListener(omemoManager);

            // make sure this listener is not already installed in this connection - ChatManager has taken care <set>
            mChatManager = ChatManager.getInstanceFor(connection);
            mChatManager.addIncomingListener(this);

        } else if (evt.getNewState() == RegistrationState.REGISTERED) {
            EntityFullJid userJid = connection.getUser();
            mUserJid = userJid.toString();

            // make sure this listener is not already registered in this connection
            connection.removeAsyncStanzaListener(OperationSetBasicInstantMessagingJabberImpl.this);
            connection.addAsyncStanzaListener(this, INCOMING_SVR_MESSAGE_FILTER);
            enableDisableCarbon(userJid);
        } else if (evt.getNewState() == RegistrationState.UNREGISTERED
                || evt.getNewState() == RegistrationState.CONNECTION_FAILED
                || evt.getNewState() == RegistrationState.AUTHENTICATION_FAILED) {
            if (connection != null) {  // must not assume - may call after log off
                connection.removeAsyncStanzaListener(this);
                if (connection.isAuthenticated()) {
                    unRegisterOmemoListener(mOmemoManager);
                }
            }

            if (mChatManager != null) {
                mChatManager.removeIncomingListener(this);
                mChatManager = null;
            }

            if (mCarbonManager != null) {
                isCarbonEnabled = false;
                mCarbonManager.removeCarbonCopyReceivedListener(this);
                mCarbonManager = null;
            }
        }
    }

    /**
     * The listener that we use in order to handle incoming server messages currently not supported by smack
     *
     * @param stanza the packet that we need to handle (if it is a message).
     *
     * @see #INCOMING_SVR_MESSAGE_FILTER filter settings
     *
     * Handles incoming messages and dispatches whatever events that are necessary.
     */
    @Override
    public void processStanza(Stanza stanza)
            throws NotConnectedException, InterruptedException, SmackException.NotLoggedInException {
        final Message message = (Message) stanza;
        if (message.getBodies().isEmpty())
            return;

        int encType = IMessage.ENCRYPTION_NONE | IMessage.ENCODE_PLAIN;
        String content = message.getBody();
        String subject = message.getSubject();
        if (!TextUtils.isEmpty(subject)) {
            content = subject + ": " + content;
        }

        IMessage newMessage = createMessageWithUID(content, encType, message.getStanzaId());
        newMessage.setRemoteMsgId(message.getStanzaId());

        // createVolatileContact will check before create
        Contact sourceContact = opSetPersPresence.createVolatileContact(message.getFrom());
        String sender = message.getFrom().toString();
        MessageReceivedEvent msgEvt = new MessageReceivedEvent(newMessage, sourceContact,
                null, sender, getTimeStamp(message), null);
        fireMessageEvent(msgEvt);
    }

    /**
     * Enable carbon feature if supported by server.
     */
    private void enableDisableCarbon(EntityFullJid userJid) {
        boolean enableCarbon = false;
        mCarbonManager = CarbonManager.getInstanceFor(mPPS.getConnection());
        try {
            enableCarbon = mCarbonManager.isSupportedByServer()
                    && !mPPS.getAccountID().getAccountPropertyBoolean(
                    ProtocolProviderFactory.IS_CARBON_DISABLED, false);

            if (enableCarbon) {
                mCarbonManager.setCarbonsEnabled(true);
                mCarbonManager.addCarbonCopyReceivedListener(this);
                isCarbonEnabled = true;
            } else {
                isCarbonEnabled = false;
                mCarbonManager = null;
            }
            Timber.i("Successfully setting carbon new state for: %s to %s", userJid, isCarbonEnabled);
        } catch (NoResponseException | InterruptedException | NotConnectedException
                | XMPPException.XMPPErrorException e) {
            Timber.e("Failed to set carbon state for: %s to %S\n%s", userJid, enableCarbon, e.getMessage());
        }
    }

    /**
     * The listener that we use in order to handle incoming messages and carbon messages.
     */
    private boolean isForwardedSentMessage = false;

    @Override
    public void onCarbonCopyReceived(CarbonExtension.Direction direction,
            Message carbonCopy, Message wrappingMessage) {
        isForwardedSentMessage = CarbonExtension.Direction.sent.equals(direction);
        Jid userJId = isForwardedSentMessage ? carbonCopy.getTo() : carbonCopy.getFrom();
        isCarbon = wrappingMessage.hasExtension(CarbonExtension.NAMESPACE);
        newIncomingMessage(userJId.asEntityBareJidIfPossible(), carbonCopy, null);
        isForwardedSentMessage = false;
    }

    /**
     * Handles incoming messages and dispatches whatever events are necessary.
     *
     * @param message the message that we need to handle.
     */
    public void newIncomingMessage(EntityBareJid from, Message message, Chat chat) {
        // Leave handling of omemo messages to onOmemoMessageReceived()
        if ((message == null) || message.hasExtension(OmemoElement.NAME_ENCRYPTED, OMEMO_NAMESPACE_V_AXOLOTL))
            return;

        // Return if it is for group chat
        if (message.hasExtension("x", "http://jabber.org/protocol/muc#user"))
            return;

        String msgBody = message.getBody();
        if (msgBody == null)
            return;

        Jid userFullJId = isForwardedSentMessage ? message.getTo() : message.getFrom();
        BareJid userBareID = userFullJId.asBareJid();

        boolean isPrivateMessaging = false;
        ChatRoomJabberImpl privateContactRoom = null;
        OperationSetMultiUserChatJabberImpl mucOpSet
                = (OperationSetMultiUserChatJabberImpl) mPPS.getOperationSet(OperationSetMultiUserChat.class);
        if (mucOpSet != null) {
            privateContactRoom = mucOpSet.getChatRoom(userBareID);
            if (privateContactRoom != null) {
                isPrivateMessaging = true;
            }
        }

        // Timber.d("Received from %s the message %s", userBareID, message.toString());
        String msgID = message.getStanzaId();
        String correctedMessageUID = getCorrectionMessageId(message);

        // Get the message type i.e. OTR or NONE; for chat message encryption indication
        int encryption = msgBody.startsWith("?OTR") ? IMessage.ENCRYPTION_OTR : IMessage.ENCRYPTION_NONE;
        int encType;

        // set up default in case XHTMLExtension contains no message
        // if msgBody contains markup text then set as ENCODE_HTML mode
        if (msgBody.matches(ChatMessage.HTML_MARKUP)) {
            encType = encryption | IMessage.ENCODE_HTML;
        } else {
            encType = encryption | IMessage.ENCODE_PLAIN;
        }

        // isCarbon only valid if from onCarbonCopyReceived i.e. chat == null;
        if (chat == null && isCarbon) {
            encType |= IMessage.FLAG_IS_CARBON;
        }
        IMessage newMessage = createMessageWithUID(msgBody, encType, msgID);

        // check if the message is available in xhtml
        String xhtmString = XhtmlUtil.getXhtmlExtension(message);
        if (xhtmString != null) {
            encType = encryption | IMessage.ENCODE_HTML;
            newMessage = createMessageWithUID(xhtmString, encType, msgID);
        }
        newMessage.setRemoteMsgId(message.getStanzaId());

        // cmeng: do not really matter if it is fullJid or bareJid. bareJid is used always.
        Contact sourceContact = opSetPersPresence.findContactByJid(isPrivateMessaging ? userFullJId : userBareID);
        if (message.getType() == Message.Type.error) {
            // error which is multi-chat and we don't know about the contact is a muc message
            // error which is missing muc extension and is coming from the room, when we try
            // to send message to room which was deleted or offline on the server
            StanzaError error = message.getError();
            int errorResultCode = MessageDeliveryFailedEvent.UNKNOWN_ERROR;

            if (isPrivateMessaging && sourceContact == null) {
                if ((error != null) && (Condition.forbidden == error.getCondition())) {
                    errorResultCode = MessageDeliveryFailedEvent.FORBIDDEN;
                }

                String errorReason = (error != null) ? error.toString() : "";
                ChatRoomMessageDeliveryFailedEvent msgDeliveryFailed = new ChatRoomMessageDeliveryFailedEvent(privateContactRoom,
                        null, errorResultCode, System.currentTimeMillis(), errorReason, newMessage);
                privateContactRoom.fireMessageEvent(msgDeliveryFailed);
                return;
            }

            Timber.i("Message error received from %s", userBareID);
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
                    = new MessageDeliveryFailedEvent(newMessage, sourceContact, errorResultCode, correctedMessageUID);
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
        ContactResource resource = ((ContactJabberImpl) sourceContact).getResourceFromJid(userFullJId.asFullJidIfPossible());

        EventObject msgEvt;
        String sender = message.getFrom().toString();
        if (isForwardedSentMessage) {
            msgEvt = new MessageDeliveredEvent(newMessage, sourceContact, null, sender, timestamp);
            // Update message unread count for carbon message for the actual recipient.
            NotificationManager.updateMessageCount(sourceContact);
        }
        else {
            msgEvt = new MessageReceivedEvent(newMessage, sourceContact, resource, sender, timestamp,
                    correctedMessageUID, isPrivateMessaging, privateContactRoom);
        }

        fireMessageEvent(msgEvt);
    }

    /**
     * A filter that prevents this operation set from handling multi user chat messages.
     */
    private static class GroupMessagePacketFilter implements StanzaFilter {
        /**
         * Returns <code>true</code> if <code>packet</code> is a <code>Message</code> and false otherwise.
         *
         * @param packet the packet that we need to check.
         *
         * @return <code>true</code> if <code>packet</code> is a <code>Message</code> and false otherwise.
         */
        @Override
        public boolean accept(Stanza packet) {
            if (!(packet instanceof Message))
                return false;

            Message msg = (Message) packet;
            return (!(msg.getType() == Message.Type.groupchat));
        }
    }

    public Jid getRecentFullJidForContactIfPossible(Contact contact) {
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
    public long getInactivityTimeout() {
        return JID_INACTIVITY_TIMEOUT;
    }

    /**
     * Returns the next unique thread id. Each thread id made up of a short alphanumeric prefix
     * along with a unique numeric value.
     *
     * @return the next thread id.
     */
    private static synchronized String nextThreadID() {
        return prefix + id++;
    }

    /**
     * Return XEP-0203 time-stamp of the message if present or current time;
     *
     * @param msg Message
     *
     * @return the correct message timeStamp
     */
    private Date getTimeStamp(Message msg) {
        Date timeStamp;
        DelayInformation delayInfo = msg.getExtension(DelayInformation.class);
        if (delayInfo != null) {
            timeStamp = delayInfo.getStamp();
        } else {
            timeStamp = new Date();
        }
        return timeStamp;
    }

    /**
     * Get messageCorrectionID if presence
     *
     * @param message Message
     *
     * @return messageCorrectionID if presence or null
     */
    private String getCorrectionMessageId(Message message) {
        MessageCorrectExtension correctionExtension = MessageCorrectExtension.from(message);
        if (correctionExtension != null) {
            return correctionExtension.getIdInitialMessage();
        }
        return null;
    }

    // =============== OMEMO message received =============== //

    public void registerOmemoListener(OmemoManager omemoManager) {
        mOmemoManager = omemoManager;
        omemoManager.addOmemoMessageListener(this);
    }

    private void unRegisterOmemoListener(OmemoManager omemoManager) {
        omemoManager.removeOmemoMessageListener(this);
        mOmemoManager = null;
    }

    private boolean isForwardedSentOmemoMessage = false;

    /**
     * Gets called, whenever an OmemoMessage has been received and was successfully decrypted.
     *
     * @param stanza Received (encrypted) stanza.
     * @param decryptedMessage decrypted OmemoMessage.
     */
    @Override
    public void onOmemoMessageReceived(Stanza stanza, OmemoMessage.Received decryptedMessage) {
        // Do not process if decryptedMessage isKeyTransportMessage i.e. msgBody == null
        if (decryptedMessage.isKeyTransportMessage())
            return;

        Message message = (Message) stanza;
        Date timeStamp = getTimeStamp(message);

        Jid userFullJid = isForwardedSentOmemoMessage ? message.getTo() : message.getFrom();
        BareJid userBareJid = userFullJid.asBareJid();
        putJidForAddress(userBareJid, message.getThread());
        Contact contact = opSetPersPresence.findContactByJid(userBareJid);
        if (contact == null) {
            // create new volatile contact
            contact = opSetPersPresence.createVolatileContact(userBareJid);
        }

        String msgID = message.getStanzaId();
        String correctedMsgID = getCorrectionMessageId(message);
        int encType = IMessage.ENCRYPTION_OMEMO;
        String msgBody = decryptedMessage.getBody();

        // Send by remote when doing Jet-OMEMO jingle file transfer
        // org.jivesoftware.smackx.jingle_filetransfer.component.JingleOutgoingFileOffer@ecd901a
        // if (msgBody != null && msgBody.contains("JingleOutgoingFileOffer"))
        //    return;

        // aTalk OMEMO msgBody may contains markup text then set as ENCODE_HTML mode
        if (msgBody.matches(ChatMessage.HTML_MARKUP)) {
            encType |= IMessage.ENCODE_HTML;
        } else {
            encType |= IMessage.ENCODE_PLAIN;
        }
        IMessage newMessage = createMessageWithUID(msgBody, encType, msgID);

        // check if the message is available in xhtml
        String xhtmString = XhtmlUtil.getXhtmlExtension(message);
        if (xhtmString != null) {
            try {
                XmlPullParser xpp = PacketParserUtils.getParserFor(xhtmString);
                OmemoElement omemoElement = omemoVAxolotlProvider.parse(xpp);

                OmemoMessage.Received xhtmlMessage = mOmemoManager.decrypt(userBareJid, omemoElement);
                encType |= IMessage.ENCODE_HTML;
                newMessage = createMessageWithUID(xhtmlMessage.getBody(), encType, msgID);
            } catch (SmackException.NotLoggedInException | IOException | CorruptedOmemoKeyException
                    | NoRawSessionException | CryptoFailedException | XmlPullParserException | SmackParsingException e) {
                Timber.e("Error decrypting xhtmlExtension message %s:", e.getMessage());
            }
        }
        newMessage.setRemoteMsgId(msgID);

        EventObject msgEvt;
        String sender = message.getFrom().toString();
        if (isForwardedSentOmemoMessage) {
            msgEvt = new MessageDeliveredEvent(newMessage, contact, null, sender, timeStamp);
            // Update message unread count for carbon message for the actual recipient.
            NotificationManager.updateMessageCount(contact);
        } else {
            msgEvt = new MessageReceivedEvent(newMessage, contact, null, sender, timeStamp, correctedMsgID);
        }

        fireMessageEvent(msgEvt);
    }

    public void onOmemoCarbonCopyReceived(CarbonExtension.Direction direction,
            Message carbonCopy, Message wrappingMessage, OmemoMessage.Received decryptedCarbonCopy) {
        isForwardedSentOmemoMessage = CarbonExtension.Direction.sent.equals(direction);
        onOmemoMessageReceived(carbonCopy, decryptedCarbonCopy);

        // Need to reset isForwardedSentOmemoMessage to receive normal Omemo message
        isForwardedSentOmemoMessage = false;
    }
}
