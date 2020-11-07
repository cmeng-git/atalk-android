/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package net.java.sip.communicator.impl.protocol.jabber;

import android.app.Activity;
import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.text.Html;
import android.text.TextUtils;

import net.java.sip.communicator.impl.muc.MUCActivator;
import net.java.sip.communicator.service.gui.Chat;
import net.java.sip.communicator.service.protocol.*;
import net.java.sip.communicator.service.protocol.event.*;
import net.java.sip.communicator.service.protocol.jabberconstants.JabberStatusEnum;
import net.java.sip.communicator.util.ConfigurationUtils;

import org.apache.commons.lang3.StringUtils;
import org.atalk.android.R;
import org.atalk.android.aTalkApp;
import org.atalk.android.gui.AndroidGUIActivator;
import org.atalk.android.gui.chat.ChatMessage;
import org.atalk.android.gui.chat.conference.CaptchaDialog;
import org.atalk.android.gui.chat.conference.ConferenceChatManager;
import org.atalk.android.gui.util.AndroidUtils;
import org.atalk.android.gui.util.XhtmlUtil;
import org.atalk.android.plugin.timberlog.TimberLog;
import org.atalk.crypto.omemo.OmemoAuthenticateDialog;
import org.jivesoftware.smack.MessageListener;
import org.jivesoftware.smack.*;
import org.jivesoftware.smack.SmackException.*;
import org.jivesoftware.smack.XMPPException.XMPPErrorException;
import org.jivesoftware.smack.packet.*;
import org.jivesoftware.smack.packet.StanzaError.Condition;
import org.jivesoftware.smackx.address.packet.MultipleAddresses;
import org.jivesoftware.smackx.delay.packet.DelayInformation;
import org.jivesoftware.smackx.muc.*;
import org.jivesoftware.smackx.muc.filter.MUCUserStatusCodeFilter;
import org.jivesoftware.smackx.muc.packet.Destroy;
import org.jivesoftware.smackx.muc.packet.MUCUser;
import org.jivesoftware.smackx.nick.packet.Nick;
import org.jivesoftware.smackx.omemo.OmemoManager;
import org.jivesoftware.smackx.omemo.OmemoMessage;
import org.jivesoftware.smackx.omemo.element.OmemoElement;
import org.jivesoftware.smackx.omemo.exceptions.*;
import org.jivesoftware.smackx.omemo.internal.OmemoDevice;
import org.jivesoftware.smackx.omemo.util.OmemoConstants;
import org.jivesoftware.smackx.xdata.FormField;
import org.jivesoftware.smackx.xdata.TextSingleFormField;
import org.jivesoftware.smackx.xdata.form.FillableForm;
import org.jivesoftware.smackx.xdata.form.Form;
import org.jivesoftware.smackx.xdata.packet.DataForm;
import org.jivesoftware.smackx.xhtmlim.XHTMLManager;
import org.jivesoftware.smackx.xhtmlim.XHTMLText;
import org.jivesoftware.smackx.xhtmlim.packet.XHTMLExtension;
import org.jxmpp.jid.*;
import org.jxmpp.jid.impl.JidCreate;
import org.jxmpp.jid.parts.Resourcepart;
import org.jxmpp.stringprep.XmppStringprepException;
import org.jxmpp.util.XmppStringUtils;
import org.xmpp.extensions.condesc.ConferenceDescriptionExtension;
import org.xmpp.extensions.condesc.TransportExtension;
import org.xmpp.extensions.jitsimeet.*;

import java.beans.PropertyChangeEvent;
import java.io.IOException;
import java.util.*;

import timber.log.Timber;

/**
 * Implements chat rooms for jabber. The class encapsulates instances of the jive software <tt>MultiUserChat</tt>.
 *
 * @author Emil Ivov
 * @author Yana Stamcheva
 * @author Valentin Martinet
 * @author Boris Grozev
 * @author Hristo Terezov
 * @author Eng Chong Meng
 */

public class ChatRoomJabberImpl extends AbstractChatRoom implements CaptchaDialog.CaptchaDialogListener
{
    /**
     * The multi user chat smack object that we encapsulate in this room.
     */
    private MultiUserChat mMultiUserChat;

    /**
     * Listeners that will be notified of changes in member status in the room such as member
     * joined, left or being kicked or dropped.
     */
    private final Vector<ChatRoomMemberPresenceListener> memberListeners = new Vector<>();

    /**
     * Listeners that will be notified of changes in member mRole in the room such as member being
     * granted admin permissions, or revoked admin permissions.
     */
    private final Vector<ChatRoomMemberRoleListener> memberRoleListeners = new Vector<>();

    /**
     * Listeners that will be notified of changes in local user mRole in the room such as member
     * being granted admin permissions, or revoked admin permissions.
     */
    private final Vector<ChatRoomLocalUserRoleListener> localUserRoleListeners = new Vector<>();

    /**
     * Listeners that will be notified every time a new message is received on this chat room.
     */
    private final Vector<ChatRoomMessageListener> messageListeners = new Vector<>();

    /**
     * Listeners that will be notified every time a chat room property has been changed.
     */
    private final Vector<ChatRoomPropertyChangeListener> propertyChangeListeners = new Vector<>();

    /**
     * Listeners that will be notified every time a chat room member property has been changed.
     */
    private final Vector<ChatRoomMemberPropertyChangeListener> memberPropChangeListeners = new Vector<>();

    /**
     * The protocol mProvider that created us
     */
    private final ProtocolProviderServiceJabberImpl mPPS;

    /**
     * The operation set that created us.
     */
    private final OperationSetMultiUserChatJabberImpl opSetMuc;

    /**
     * The list of members of this chat room EntityFullJid
     */
    private final Hashtable<Resourcepart, ChatRoomMemberJabberImpl> members = new Hashtable<>();

    /**
     * The list of banned members of this chat room EntityFullJid.
     */
    private final Hashtable<Resourcepart, ChatRoomMember> banList = new Hashtable<>();

    /**
     * The Resource Part of this chat room local user participant i.e. NickName.
     */
    private Resourcepart mNickName;

    /**
     * The password use during join room
     */
    private byte[] mPassword;

    /**
     * The subject of this chat room. Keeps track of the subject changes.
     */
    private String oldSubject;

    // The last send Message encType; for reinsert into relayed delivered message from server
    private int mEncType;

    /**
     * The mRole of this chat room local user participant.
     */
    private ChatRoomMemberRole mUserRole = null;

    /**
     * The conference which we have announced in the room in our last sent <tt>Presence</tt> update.
     */
    private ConferenceDescription publishedConference = null;

    /**
     * The <tt>ConferenceAnnouncementPacketExtension</tt> corresponding to <tt>publishedConference</tt> which we
     * add to all our presence updates. This MUST be kept in sync with <tt>publishedConference</tt>
     */
    private ConferenceDescriptionExtension publishedConferenceExt = null;

    /**
     * The last <tt>Presence</tt> packet we sent to the MUC.
     */
    private Presence lastPresenceSent = null;

    /**
     * All <presence/>'s reason will default to REASON_USER_LIST until user own <tt>Presence</tt> has been received.
     *
     * @see ChatRoomMemberPresenceChangeEvent#REASON_USER_LIST
     */
    private boolean mucOwnPresenceReceived = false;

    private final List<CallJabberImpl> chatRoomConferenceCalls = new ArrayList<>();

    /**
     * Packet listener waits for rejection of invitations to join room.
     */
    private InvitationRejectionListeners invitationRejectionListeners;

    /**
     * Presence stanza interceptor listener.
     */
    private PresenceInterceptor presenceInterceptor;

    /**
     * Local status change listener
     */
    private LocalUserStatusListener userStatusListener;

    /**
     * Presence listener for joining participants.
     */
    private ParticipantListener participantListener;

    private int mCaptchaState = CaptchaDialog.unknown;
    private final MucMessageListener messageListener;

    private Message captchaMessage;

    /**
     * Creates an instance of a chat room and initialize all the necessary listeners
     * for group chat status monitoring
     *
     * @param multiUserChat MultiUserChat
     * @param provider a reference to the currently valid jabber protocol mProvider.
     */
    public ChatRoomJabberImpl(MultiUserChat multiUserChat, ProtocolProviderServiceJabberImpl provider)
    {
        mMultiUserChat = multiUserChat;
        mPPS = provider;

        this.opSetMuc = (OperationSetMultiUserChatJabberImpl) provider.getOperationSet(OperationSetMultiUserChat.class);
        this.oldSubject = multiUserChat.getSubject();

        multiUserChat.addSubjectUpdatedListener(new MucSubjectUpdatedListener());
        multiUserChat.addParticipantStatusListener(new MemberListener());

        messageListener = new MucMessageListener();
        multiUserChat.addMessageListener(messageListener);

        userStatusListener = new LocalUserStatusListener();
        multiUserChat.addUserStatusListener(userStatusListener);

        presenceInterceptor = new PresenceInterceptor();
        multiUserChat.addPresenceInterceptor(presenceInterceptor);

        participantListener = new ParticipantListener();
        multiUserChat.addParticipantListener(participantListener);

        invitationRejectionListeners = new InvitationRejectionListeners();
        multiUserChat.addInvitationRejectionListener(invitationRejectionListeners);

        ConferenceChatManager conferenceChatManager = AndroidGUIActivator.getUIService().getConferenceChatManager();
        addMessageListener(conferenceChatManager);
    }

    /**
     * Show captcha challenge for group chat if requested
     *
     * @param message Stanza Message containing captcha challenge info
     */
    public void initCaptchaProcess(final Message message)
    {
        // Set flag to ignore reply timeout
        captchaMessage = message;
        mCaptchaState = CaptchaDialog.awaiting;

        // Do not proceed to launch CaptchaDialog if app is in background - system crash
        if (!aTalkApp.isForeground) {
            return;
        }

        aTalkApp.waitForFocus();
        new Handler(Looper.getMainLooper()).post(() -> {
            // Must use activity (may be null at time); Otherwise token null is not valid; is your activity running?
            Activity activity = aTalkApp.getCurrentActivity();
            if (activity != null) {
                CaptchaDialog captchaDialog = new CaptchaDialog(activity,
                        mMultiUserChat, message, ChatRoomJabberImpl.this);
                captchaDialog.show();
            }
        });
    }

    /**
     * Captcha dialog callback on user response to the challenge
     *
     * @param state Captcha dialog response state from server/user
     * @see CaptchaDialog state
     */
    @Override
    public void onResult(int state)
    {
        mCaptchaState = state;
        switch (mCaptchaState) {
            case CaptchaDialog.validated:
                if (mMultiUserChat.isJoined())
                    onJoinSuccess();
                else {
                    try {
                        Timber.d("Rejoined chat room after captcha challenge");
                        // must re-joined immediately, otherwise smack has problem handling room delayed messages
                        joinAs(mNickName.toString(), mPassword);
                    } catch (OperationFailedException e) {
                        Timber.w("Rejoined error: %s", e.getMessage());
                    }
                }
                break;

            case CaptchaDialog.failed:
                // CaptchaDialog will display the error message, try to rejoin
                try {
                    joinAs(mNickName.toString(), mPassword);
                } catch (OperationFailedException e) {
                    Timber.w("Rejoined error: %s", e.getMessage());
                }
                break;

            case CaptchaDialog.cancel:
                // Show in chat instead of launching an alert dialog
                String errMsg = aTalkApp.getResString(R.string.service_gui_CHATROOM_JOIN_FAILED, mNickName, getName());
                addMessage(errMsg, ChatMessage.MESSAGE_ERROR);
                Timber.d("User cancel: %s", errMsg);
                // MUCActivator.getAlertUIService().showAlertDialog(aTalkApp.getResString(R.string.service_gui_ERROR), errMsg);
                break;
            default:
                break;
        }
    }

    /**
     * Captcha dialog callback on server response to user input
     *
     * @param message message string
     * @param msgType messageType
     */
    @Override
    public void addMessage(String message, int msgType)
    {
        Chat chatPanel = MUCActivator.getUIService().getChat(ChatRoomJabberImpl.this);
        chatPanel.addMessage(mMultiUserChat.getRoom().toString(), new Date(), msgType, IMessage.ENCODE_PLAIN, message);
    }

    /**
     * Provide access to the smack processMessage() by external class
     *
     * @param message chatRoom message
     */
    public void processMessage(Message message)
    {
        messageListener.processMessage(message);
    }

    /**
     * Adds <tt>listener</tt> to the list of listeners registered to receive events upon
     * modification of chat room properties such as its subject for example.
     *
     * @param listener the <tt>ChatRoomChangeListener</tt> that is to be registered for
     * <tt>ChatRoomChangeEvent</tt>-s.
     */
    public void addPropertyChangeListener(ChatRoomPropertyChangeListener listener)
    {
        synchronized (propertyChangeListeners) {
            if (!propertyChangeListeners.contains(listener))
                propertyChangeListeners.add(listener);
        }
    }

    /**
     * Removes <tt>listener</tt> from the list of listeners current registered for chat room modification events.
     *
     * @param listener the <tt>ChatRoomChangeListener</tt> to remove.
     */
    public void removePropertyChangeListener(ChatRoomPropertyChangeListener listener)
    {
        synchronized (propertyChangeListeners) {
            propertyChangeListeners.remove(listener);
        }
    }

    /**
     * Adds the given <tt>listener</tt> to the list of listeners registered to receive events upon
     * modification of chat room member properties such as its mNickname being changed for example.
     *
     * @param listener the <tt>ChatRoomMemberPropertyChangeListener</tt> that is to be registered for
     * <tt>ChatRoomMemberPropertyChangeEvent</tt>s.
     */
    public void addMemberPropertyChangeListener(ChatRoomMemberPropertyChangeListener listener)
    {
        synchronized (memberPropChangeListeners) {
            if (!memberPropChangeListeners.contains(listener))
                memberPropChangeListeners.add(listener);
        }
    }

    /**
     * Removes the given <tt>listener</tt> from the list of listeners currently registered for chat
     * room member property change events.
     *
     * @param listener the <tt>ChatRoomMemberPropertyChangeListener</tt> to remove.
     */
    public void removeMemberPropertyChangeListener(ChatRoomMemberPropertyChangeListener listener)
    {
        synchronized (memberPropChangeListeners) {
            memberPropChangeListeners.remove(listener);
        }
    }

    /**
     * Registers <tt>listener</tt> so that it would receive events every time a new message is
     * received on this chat room.
     *
     * @param listener a <tt>MessageListener</tt> that would be notified every time a new message is received
     * on this chat room.
     */
    public void addMessageListener(ChatRoomMessageListener listener)
    {
        synchronized (messageListeners) {
            if (!messageListeners.contains(listener))
                messageListeners.add(listener);
        }
    }

    /**
     * Removes <tt>listener</tt> so that it won't receive any further message events from this room.
     *
     * @param listener the <tt>MessageListener</tt> to remove from this room
     */
    public void removeMessageListener(ChatRoomMessageListener listener)
    {
        synchronized (messageListeners) {
            messageListeners.remove(listener);
        }
    }

    /**
     * Adds a listener that will be notified of changes in our status in the room such as us being
     * kicked, banned, or granted admin permissions.
     *
     * @param listener a participant status listener.
     */
    public void addMemberPresenceListener(ChatRoomMemberPresenceListener listener)
    {
        synchronized (memberListeners) {
            if (!memberListeners.contains(listener))
                memberListeners.add(listener);
        }
    }

    /**
     * Removes a listener that was being notified of changes in the status of other chat room
     * participants such as users being kicked, banned, or granted admin permissions.
     *
     * @param listener a participant status listener.
     */
    public void removeMemberPresenceListener(ChatRoomMemberPresenceListener listener)
    {
        synchronized (memberListeners) {
            memberListeners.remove(listener);
        }
    }

    /**
     * Adds a <tt>CallJabberImpl</tt> instance to the list of conference calls associated with the room.
     *
     * @param call the call to add
     */
    public synchronized void addConferenceCall(CallJabberImpl call)
    {
        if (!chatRoomConferenceCalls.contains(call))
            chatRoomConferenceCalls.add(call);
    }

    /**
     * Removes a <tt>CallJabberImpl</tt> instance from the list of conference calls associated with the room.
     *
     * @param call the call to remove.
     */
    public synchronized void removeConferenceCall(CallJabberImpl call)
    {
        chatRoomConferenceCalls.remove(call);
    }

    /**
     * Create a Message instance for sending arbitrary MIME-encoding content.
     *
     * @param content message content value
     * @param encType See IMessage for definition of encType e.g. Encryption, encode & remoteOnly
     * @param subject a <tt>String</tt> subject or <tt>null</tt> for now subject.
     * @return the newly created message.
     */
    public IMessage createMessage(String content, int encType, String subject)
    {
        return new MessageJabberImpl(content, encType, subject, null);
    }

    /**
     * Create a Message instance for sending a simple text messages with default (text/plain)
     * content type and encoding.
     *
     * @param messageText the string content of the message.
     * @return IMessage the newly created message
     */
    public IMessage createMessage(String messageText)
    {
        return new MessageJabberImpl(messageText, IMessage.ENCODE_PLAIN, "", null);
    }

    /**
     * Returns a <tt>List</tt> of <tt>Member</tt>s corresponding to all members currently participating in this room.
     *
     * @return a <tt>List</tt> of <tt>Member</tt> corresponding to all room members.
     */
    public List<ChatRoomMember> getMembers()
    {
        synchronized (members) {
            return new LinkedList<>(members.values());
        }
    }

    /**
     * Returns the number of participants that are currently in this chat room.
     *
     * @return int the number of <tt>Contact</tt>s, currently participating in this room.
     */
    public int getMembersCount()
    {
        return mMultiUserChat.getOccupantsCount();
    }

    /**
     * Returns the name of this <tt>ChatRoom</tt>.
     *
     * @return a <tt>String</tt> containing the name of this <tt>ChatRoom</tt>.
     */
    public String getName()
    {
        return mMultiUserChat.getRoom().toString();
    }

    /**
     * Returns the EntityBareJid of this <tt>ChatRoom</tt>.
     *
     * @return a <tt>EntityBareJid</tt> containing the identifier of this <tt>ChatRoom</tt>.
     */
    public EntityBareJid getIdentifier()
    {
        return mMultiUserChat.getRoom();
    }

    /**
     * Returns the local user's nickname in the context of this chat room or <tt>null</tt> if not currently joined.
     *
     * @return the nickname currently being used by the local user in the context of the local chat room.
     */
    public Resourcepart getUserNickname()
    {
        return mMultiUserChat.getNickname();
    }

    private String getAccountId(ChatRoom chatRoom)
    {
        AccountID accountId = chatRoom.getParentProvider().getAccountID();
        return accountId.getAccountJid();
    }

    /**
     * Finds private messaging contact by nickname. If the contact doesn't exists a new volatile contact is created.
     *
     * @param nickname the nickname of the contact.
     * @return the contact instance.
     */
    @Override
    public Contact getPrivateContactByNickname(String nickname)
    {
        OperationSetPersistentPresenceJabberImpl opSetPersPresence = (OperationSetPersistentPresenceJabberImpl)
                mPPS.getOperationSet(OperationSetPersistentPresence.class);
        Jid jid;
        try {
            jid = JidCreate.fullFrom(getIdentifier(), Resourcepart.from(nickname));
        } catch (XmppStringprepException e) {
            throw new IllegalArgumentException("Invalid XMPP nickname");
        }

        Contact sourceContact = opSetPersPresence.findContactByJid(jid);
        if (sourceContact == null) {
            sourceContact = opSetPersPresence.createVolatileContact(jid, true);
        }
        return sourceContact;
    }

    /**
     * Returns the last known room subject/theme or <tt>null</tt> if the user hasn't joined the
     * room or the room does not have a subject yet.
     *
     * @return the room subject or <tt>null</tt> if the user hasn't joined the room or the room
     * does not have a subject yet.
     */
    public String getSubject()
    {
        return mMultiUserChat.getSubject();
    }

    /**
     * Invites another user to this room. Block any domainJid from joining as it does not support IM
     *
     * @param userJid jid of the user to invite to the room.(one may also invite users not on their contact list).
     * @param reason a reason, subject, or welcome message that would tell the the user why they are being invited.
     */
    public void invite(EntityBareJid userJid, String reason)
            throws NotConnectedException, InterruptedException
    {
        if (TextUtils.isEmpty(XmppStringUtils.parseLocalpart(userJid.toString()))) {
            aTalkApp.showToastMessage(R.string.service_gui_SEND_MESSAGE_NOT_SUPPORTED, userJid);
        }
        else {
            mMultiUserChat.invite(userJid, reason);
        }
    }

    /**
     * Returns true if the local user is currently in the multi user chat (after calling one of the
     * {@link #join()} methods).
     *
     * @return true if currently we're currently in this chat room and false otherwise.
     */
    public boolean isJoined()
    {
        return mMultiUserChat.isJoined();
    }

    /**
     * Joins this chat room with the nickName of the local user so that the user would start
     * receiving events and messages for it.
     *
     * @throws OperationFailedException with the corresponding code if an error occurs while joining the room.
     */
    public boolean join()
            throws OperationFailedException
    {
        return joinAs(JabberActivator.getGlobalDisplayDetailsService().getDisplayName(mPPS));
    }

    /**
     * Joins this chat room so that the user would start receiving events and messages for it.
     *
     * @param password the password to use when authenticating on the chatRoom.
     * @throws OperationFailedException with the corresponding code if an error occurs while joining the room.
     */
    public boolean join(byte[] password)
            throws OperationFailedException
    {
        return joinAs(JabberActivator.getGlobalDisplayDetailsService().getDisplayName(mPPS), password);
    }

    /**
     * Joins this chat room with the specified nickname as anonymous so that the user would
     * start receiving events and messages for it.
     *
     * @param nickname the nickname can be jid or just nick.
     * @throws OperationFailedException with the corresponding code if an error occurs while joining the room.
     */
    public boolean joinAs(String nickname)
            throws OperationFailedException
    {
        return joinAs(nickname, null);
    }

    /**
     * Joins this chat room with the specified nickName and password so that the user would start
     * receiving events and messages for it.
     *
     * @param nickname the nickname can be jid or just nick.
     * @param password a password necessary to authenticate when joining the room.
     * @throws OperationFailedException with the corresponding code if an error occurs while joining the room.
     */
    public boolean joinAs(final String nickname, final byte[] password)
            throws OperationFailedException
    {
        assertConnected();
        boolean retry = true;
        String errorMessage = aTalkApp.getResString(R.string.service_gui_CHATROOM_JOIN_FAILED, nickname, getName());

        mPassword = password;
        // parseLocalPart or take nickname as it to join chatRoom
        String sNickname = nickname.split("@")[0];
        try {
            mNickName = Resourcepart.from(sNickname);
            if (mMultiUserChat.isJoined()) {
                if (!mMultiUserChat.getNickname().equals(mNickName))
                    mMultiUserChat.changeNickname(mNickName);
            }
            else {
                if (password == null)
                    mMultiUserChat.join(mNickName);
                else
                    mMultiUserChat.join(mNickName, new String(password));
            }
            onJoinSuccess();
            retry = false;
        } catch (XMPPErrorException ex) {
            StanzaError xmppError = ex.getStanzaError();
            errorMessage += "\n" + ex.getMessage();
            if (xmppError == null) {
                Timber.e(ex, "%s", errorMessage);
                throw new OperationFailedException(errorMessage, OperationFailedException.GENERAL_ERROR, ex);
            }
            else if (xmppError.getCondition().equals(Condition.not_authorized)) {
                Timber.e("Join room exception: %s (state)\n%s", mCaptchaState, errorMessage);
                if (mCaptchaState == CaptchaDialog.unknown) {
                    errorMessage += "\n" + aTalkApp.getResString(R.string.service_gui_CHATROOM_JOIN_FAILED_PASSWORD);
                    throw new OperationFailedException(errorMessage, OperationFailedException.AUTHENTICATION_FAILED, ex);
                }
                else if (mCaptchaState == CaptchaDialog.failed) {
                    // Allow user retry, do not throw Exception back to caller.
                    // errorMessage += aTalkApp.getResString(R.string.service_gui_CHATROOM_JOIN_CAPTCHA_VERIFICATION_FAILED);
                    // throw new OperationFailedException(errorMessage, OperationFailedException.CAPTCHA_CHALLENGE, ex);
                    aTalkApp.showToastMessage(errorMessage);
                    retry = true;
                }
                else if (mCaptchaState == CaptchaDialog.cancel) {
                    // To abort retry by caller on user cancel captcha challenge request
                    retry = false;
                }
            }
            else if (xmppError.getCondition().equals(Condition.registration_required)) {
                String errText = xmppError.getDescriptiveText();
                if (TextUtils.isEmpty(errText))
                    errorMessage += "\n" + aTalkApp.getResString(R.string.service_gui_CHATROOM_JOIN_FAILED_REGISTRATION);
                else
                    errorMessage += "\n" + errText;

                Timber.e(ex, "%s", errorMessage);
                throw new OperationFailedException(errorMessage, OperationFailedException.REGISTRATION_REQUIRED, ex);
            }
            else {
                Timber.e(ex, "%s", errorMessage);
                throw new OperationFailedException(errorMessage, OperationFailedException.GENERAL_ERROR, ex);
            }
        } catch (Throwable ex) {
            Timber.e("%s: %s", errorMessage, ex.getMessage());
            // Ignore server response timeout if received captcha challenge or user canceled captcha challenge request
            // - likely not get call as user cancel is implemented by sending empty reply to cancel smack delay
            if ((ex instanceof SmackException.NoResponseException)
                    && ((mCaptchaState == CaptchaDialog.cancel) || (mCaptchaState == CaptchaDialog.awaiting))) {
                Timber.d("Join room (Ignore NoResponseException): Received captcha challenge or user canceled");
                retry = false;
            }
            else if (mCaptchaState == CaptchaDialog.unknown) {
                errorMessage = ex.getMessage();
                throw new OperationFailedException(errorMessage, OperationFailedException.GENERAL_ERROR, ex);
            }
            else if (mCaptchaState != CaptchaDialog.awaiting) {
                errorMessage += "\n" + aTalkApp.getResString(R.string.service_gui_CHATROOM_JOIN_CAPTCHA_VERIFICATION_FAILED);
                throw new OperationFailedException(errorMessage, OperationFailedException.CAPTCHA_CHALLENGE, ex);
            }
            else {
                errorMessage = aTalkApp.getResString(R.string.service_gui_CHATROOM_JOIN_CAPTCHA_AWAITING, getName());
                throw new OperationFailedException(errorMessage, OperationFailedException.CAPTCHA_CHALLENGE, ex);
            }
        }
        // Abort retry by caller if false
        return retry;
    }

    /**
     * Process to perform upon a successful join room request
     */
    private void onJoinSuccess()
    {
        // update members list only on successful joining chatRoom
        ChatRoomMemberJabberImpl member = new ChatRoomMemberJabberImpl(this, mNickName, mPPS.getOurJID());
        synchronized (members) {
            members.put(mNickName, member);
        }

        // unblock all conference event UI display on received own <presence/> stanza e.g. participants' <presence/> etc
        mucOwnPresenceReceived = true;
        opSetMuc.fireLocalUserPresenceEvent(this, LocalUserChatRoomPresenceChangeEvent.LOCAL_USER_JOINED, null);
    }

    //    /**
    //     * Send chatRoom registration request
    //     * <iq to='chatroom-8eev@conference.atalk.org' id='VAgx3-116' type='get'><query xmlns='jabber:iq:register'></query></iq>
    //     *
    //     * <iq xml:lang='en' to='leopard@atalk.org/atalk' from='chatroom-8eev@conference.atalk.org' type='error' id='VAgx3-116'>
    //     * <query xmlns='jabber:iq:register'/><error code='503' type='cancel'><service-unavailable xmlns='urn:ietf:params:xml:ns:xmpp-stanzas'/>
    //     * <text xml:lang='en' xmlns='urn:ietf:params:xml:ns:xmpp-stanzas'>The feature requested is not supported by the conference</text></error></iq>
    //     */
    //    private void initRegistrationRequest()
    //    {
    //        Registration req = new Registration(null);
    //        EntityBareJid chatRoom = mMultiUserChat.getRoom();
    //        DomainFullJid fromJid = JidCreate.domainFullFrom(chatRoom.asDomainBareJid(), mNickName);
    //        req.setTo(chatRoom);
    //        req.setType(IQ.Type.get);
    //        req.setFrom(fromJid);
    //        req.setStanzaId();
    //        try {
    //            StanzaCollector stanzaCollector
    //                    = mProvider.getConnection().createStanzaCollectorAndSend(new StanzaIdFilter(req.getStanzaId()), req);
    //        } catch (NotConnectedException | InterruptedException e) {
    //            e.printStackTrace();
    //        }
    //    }

    /**
     * Returns that <tt>ChatRoomJabberRole</tt> instance corresponding to the <tt>smackRole</tt> string.
     *
     * @param mucRole the smack mRole as returned by <tt>Occupant.getRole()</tt>.
     * @return ChatRoomMemberRole
     */
    public static ChatRoomMemberRole smackRoleToScRole(MUCRole mucRole, MUCAffiliation affiliation)
    {
        if (affiliation != null) {
            if (affiliation == MUCAffiliation.admin) {
                return ChatRoomMemberRole.ADMINISTRATOR;
            }
            else if (affiliation == MUCAffiliation.owner) {
                return ChatRoomMemberRole.OWNER;
            }
        }

        if (mucRole != null) {
            if (mucRole == MUCRole.moderator) {
                return ChatRoomMemberRole.MODERATOR;
            }
            else if (mucRole == MUCRole.participant) {
                return ChatRoomMemberRole.MEMBER;
            }
        }
        return ChatRoomMemberRole.GUEST;
    }

    /**
     * Returns the <tt>ChatRoomMember</tt> corresponding to the given smack participant.
     *
     * @param participant the EntityFullJid participant (e.g. sc-testroom@conference.voipgw.fr/userNick)
     * @return the <tt>ChatRoomMember</tt> corresponding to the given smack participant
     */
    public ChatRoomMemberJabberImpl findMemberFromParticipant(Jid participant)
    {
        if (participant == null) {
            return null;
        }

        Resourcepart participantNick = participant.getResourceOrThrow();
        synchronized (members) {
            for (ChatRoomMemberJabberImpl member : members.values()) {
                if (participantNick.equals(member.getNickAsResourcepart())
                        || participant.equals(member.getJabberID()))
                    return member;
            }
        }
        return members.get(participantNick);
    }

    /**
     * Destroys the chat room.
     *
     * @param reason the reason for destroying.
     * @param roomName the chat Room Name (e.g. sc-testroom@conference.voipgw.fr)
     * @return <tt>true</tt> if the room is destroyed.
     */
    public boolean destroy(String reason, EntityBareJid roomName)
            throws XMPPException
    {
        try {
            mMultiUserChat.destroy(reason, roomName);
        } catch (NoResponseException | NotConnectedException | InterruptedException e) {
            AndroidUtils.showAlertDialog(aTalkApp.getGlobalContext(), aTalkApp.getResString(R.string.service_gui_ERROR),
                    aTalkApp.getResString(R.string.service_gui_CHATROOM_DESTROY_EXCEPTION, e.getMessage()));
            return false;
        }
        return true;
    }

    /**
     * Leave this chat room with no alternative room address to join.
     */
    public void leave()
    {
        this.leave(null, aTalkApp.getResString(R.string.service_gui_LEAVE_ROOM));
    }

    /**
     * Leave this chat room
     *
     * @param reason reason for leaving the room
     * @param alternateAddress alternate chatRoom to join
     * @param hasLeft true when then room is destroyed (smack patch)
     */
    private void leave(EntityBareJid alternateAddress, String reason)
    {
        OperationSetBasicTelephonyJabberImpl basicTelephony
                = (OperationSetBasicTelephonyJabberImpl) mPPS.getOperationSet(OperationSetBasicTelephony.class);
        if (basicTelephony != null && publishedConference != null) {
            ActiveCallsRepositoryJabberImpl activeRepository = basicTelephony.getActiveCallsRepository();

            String callId = publishedConference.getCallId();
            if (callId != null) {
                CallJabberImpl call = activeRepository.findCallId(callId);
                for (CallPeerJabberImpl peer : call.getCallPeerList()) {
                    try {
                        peer.hangup(false, null, null);
                    } catch (NotConnectedException | InterruptedException e) {
                        Timber.e(e, "Could not hangup peer %s", peer.getAddress());
                    }
                }
            }
        }

        List<CallJabberImpl> tmpConferenceCalls;
        synchronized (chatRoomConferenceCalls) {
            tmpConferenceCalls = new ArrayList<>(chatRoomConferenceCalls);
            chatRoomConferenceCalls.clear();
        }

        for (CallJabberImpl call : tmpConferenceCalls) {
            for (CallPeerJabberImpl peer : call.getCallPeerList())
                try {
                    peer.hangup(false, null, null);
                } catch (NotConnectedException | InterruptedException e) {
                    Timber.e(e, "Could not hangup peer %s", peer.getAddress());
                }
        }
        clearCachedConferenceDescriptionList();

        XMPPConnection connection = mPPS.getConnection();
        try {
            // if we are already disconnected; leave may be called from gui when closing chat window
            // Patch smack-4.4.3-alpha3, ensure mMultiUserChat.isJoined() is false when room is destroyed
            if ((connection != null) && mMultiUserChat.isJoined()) {
                mMultiUserChat.leave();
            }
        } catch (Throwable e) {
            Timber.w(e, "Error leaving room (has been destroyed or disconnected): %s", e.getMessage());
            // must proceed to clean up the rest even if exception
        }

        // cmeng: removed as chatPanel will closed ?
        synchronized (members) {
            for (ChatRoomMember member : members.values()) {
                fireMemberPresenceEvent(member, ChatRoomMemberPresenceChangeEvent.MEMBER_LEFT,
                        "Local user has left the chat room.");
            }
            members.clear();
        }

        /*
         * Remove all the callback listeners for the chatRoom
         * connection can be null if we are leaving due to connection failed
         */
        if ((connection != null) && (mMultiUserChat != null)) {
            if (presenceInterceptor != null) {
                mMultiUserChat.removePresenceInterceptor(presenceInterceptor);
                presenceInterceptor = null;
            }

            mMultiUserChat.removeUserStatusListener(userStatusListener);
            mMultiUserChat.removeParticipantListener(participantListener);
            mMultiUserChat.removeInvitationRejectionListener(invitationRejectionListeners);
        }

        opSetMuc.fireLocalUserPresenceEvent(this, LocalUserChatRoomPresenceChangeEvent.LOCAL_USER_LEFT,
                reason, (alternateAddress != null) ? alternateAddress.toString() : null);
    }

    /**
     * Construct the <tt>message</tt> for the required ENCODE mode for sending.
     *
     * @param message the <tt>IMessage</tt> to send.
     * @throws OperationFailedException if sending the message fails for some reason.
     */
    public void sendMessage(IMessage message)
            throws OperationFailedException
    {
        mEncType = message.getEncType();
        String content = message.getContent();
        MessageBuilder messageBuilder = StanzaBuilder.buildMessage();

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
        }
        else {
            // this is plain text so keep it as it is.
            messageBuilder.addBody(null, content);
        }
        sendMessage(messageBuilder);
    }

    /**
     * Sends the <tt>message</tt> with the json-message extension to the destination indicated by the <tt>to</tt> contact.
     *
     * @param json the json message to be sent.
     * @throws OperationFailedException if sending the message fails for some reason.
     */
    public void sendJsonMessage(String json)
            throws OperationFailedException
    {
        MessageBuilder messageBuilder = StanzaBuilder.buildMessage()
                .addExtension(new JsonMessageExtension(json));
        sendMessage(messageBuilder);
    }

    /**
     * Sends the <tt>message</tt> to the destination <tt>multiUserChat</tt> chatRoom.
     *
     * @param messageBuilder the {@link Message} to be sent.
     * @throws OperationFailedException if sending the message fails for some reason.
     */
    private void sendMessage(MessageBuilder messageBuilder)
            throws OperationFailedException
    {
        try {
            assertConnected();
            mMultiUserChat.sendMessage(messageBuilder);
        } catch (NotConnectedException | InterruptedException e) {
            Timber.e("Failed to send message: %s", e.getMessage());
            throw new OperationFailedException(aTalkApp.getResString(R.string.service_gui_SEND_MESSAGE_FAIL, messageBuilder.build()),
                    OperationFailedException.GENERAL_ERROR, e);
        }
    }

    public void sendMessage(IMessage message, final OmemoManager omemoManager)
    {
        EntityBareJid entityBareJid = mMultiUserChat.getRoom();
        String msgContent = message.getContent();
        String errMessage = null;

        try {
            OmemoMessage.Sent encryptedMessage = omemoManager.encrypt(mMultiUserChat, msgContent);

            MessageBuilder messageBuilder = StanzaBuilder.buildMessage();
            Message sendMessage = encryptedMessage.buildMessage(messageBuilder, entityBareJid);

            if (IMessage.ENCODE_HTML == message.getMimeType()) {
                String xhtmlBody = encryptedMessage.getElement().toXML().toString();
                XHTMLText xhtmlText = new XHTMLText("", "us")
                        .append(xhtmlBody)
                        .appendCloseBodyTag();

                // OMEMO normal body message content will strip off any html tags info
                msgContent = Html.fromHtml(msgContent).toString();
                encryptedMessage = omemoManager.encrypt(mMultiUserChat, msgContent);

                messageBuilder = StanzaBuilder.buildMessage();
                // Add the XHTML text to the message
                XHTMLManager.addBody(messageBuilder, xhtmlText);
                sendMessage = encryptedMessage.buildMessage(messageBuilder, entityBareJid);
            }

            // proceed to send message if no exceptions.
            // sendMessage.setStanzaId(message.getMessageUID());
            mMultiUserChat.sendMessage(sendMessage.asBuilder());

            // Delivered message for own outgoing message view display
            message.setServerMsgId(sendMessage.getStanzaId());
            message.setReceiptStatus(ChatMessage.MESSAGE_DELIVERY_CLIENT_SENT);
            ChatRoomMessageDeliveredEvent msgDeliveredEvt = new ChatRoomMessageDeliveredEvent(ChatRoomJabberImpl.this,
                    new Date(), message, ChatMessage.MESSAGE_MUC_OUT);
            fireMessageEvent(msgDeliveredEvt);
        } catch (UndecidedOmemoIdentityException e) {
            OmemoAuthenticateListener omemoAuthListener = new OmemoAuthenticateListener(message, omemoManager);
            Context ctx = aTalkApp.getGlobalContext();
            ctx.startActivity(OmemoAuthenticateDialog.createIntent(ctx, omemoManager, e.getUndecidedDevices(), omemoAuthListener));
            return;
        } catch (NoOmemoSupportException e) {
            errMessage = aTalkApp.getResString(R.string.crypto_msg_OMEMO_SESSION_SETUP_FAILED, "NoOmemoSupportException");
        } catch (CryptoFailedException | InterruptedException | NotConnectedException | NoResponseException
                | XMPPErrorException | IOException e) {
            errMessage = aTalkApp.getResString(R.string.crypto_msg_OMEMO_SESSION_SETUP_FAILED, e.getMessage());
        } catch (NotLoggedInException e) {
            errMessage = aTalkApp.getResString(R.string.service_gui_MSG_SEND_CONNECTION_PROBLEM);
        }

        if (!TextUtils.isEmpty(errMessage)) {
            Timber.w("%s", errMessage);
            ChatRoomMessageDeliveryFailedEvent failedEvent = new ChatRoomMessageDeliveryFailedEvent(this,
                    null, MessageDeliveryFailedEvent.OMEMO_SEND_ERROR, System.currentTimeMillis(), errMessage, message);
            fireMessageEvent(failedEvent);
        }
    }

    /**
     * Omemo listener callback on user authentication for undecided omemoDevices
     */
    private class OmemoAuthenticateListener implements OmemoAuthenticateDialog.AuthenticateListener
    {
        IMessage message;
        OmemoManager omemoManager;

        OmemoAuthenticateListener(IMessage message, OmemoManager omemoManager)
        {
            this.message = message;
            this.omemoManager = omemoManager;
        }

        @Override
        public void onAuthenticate(boolean allTrusted, Set<OmemoDevice> omemoDevices)
        {
            if (allTrusted) {
                sendMessage(message, omemoManager);
            }
            else {
                String errMessage = aTalkApp.getResString(R.string.omemo_send_error,
                        "Undecided Omemo Identity: " + omemoDevices.toString());
                Timber.w("%s", errMessage);
                ChatRoomMessageDeliveryFailedEvent failedEvent = new ChatRoomMessageDeliveryFailedEvent(ChatRoomJabberImpl.this,
                        null, MessageDeliveryFailedEvent.OMEMO_SEND_ERROR, System.currentTimeMillis(), errMessage, message);
                fireMessageEvent(failedEvent);
            }
        }
    }

    /**
     * Sets the subject of this chat room.
     *
     * @param subject the new subject that we'd like this room to have
     * @throws OperationFailedException throws Operation Failed Exception
     */
    public void setSubject(String subject)
            throws OperationFailedException
    {
        try {
            mMultiUserChat.changeSubject(subject);
        } catch (XMPPException | NoResponseException | NotConnectedException | InterruptedException ex) {
            String errMsg = "Failed to change subject for chat room" + getName();
            Timber.e(ex, "%s: %s", errMsg, ex.getMessage());
            throw new OperationFailedException(errMsg, OperationFailedException.NOT_ENOUGH_PRIVILEGES, ex);
        }
    }

    /**
     * Returns a reference to the mProvider that created this room.
     *
     * @return a reference to the <tt>ProtocolProviderService</tt> instance that created this room.
     */
    public ProtocolProviderService getParentProvider()
    {
        return mPPS;
    }

    /**
     * Returns the local user's role in the context of this chat room if currently joined.
     * Else retrieve from the value in DB that was previously saved, or <tt>null</tt> if none
     *
     * Always save a copy of the userRole retrieved online in DB for later use
     *
     * @return ChatRoomMemberRole
     */
    public ChatRoomMemberRole getUserRole()
    {
        if (mUserRole == null) {
            // return role as GUEST if participant has not joined the chatRoom i.e. nickName == null
            Resourcepart nick = mMultiUserChat.getNickname();
            if (nick == null) {
                String role = ConfigurationUtils.getChatRoomProperty(mPPS, getName(), ChatRoom.USER_ROLE);
                mUserRole = (StringUtils.isEmpty(role)) ? null : ChatRoomMemberRole.fromString(role);
            }
            else {
                EntityFullJid participant = JidCreate.entityFullFrom(getIdentifier(), nick);
                Occupant o = mMultiUserChat.getOccupant(participant);
                if (o != null) {
                    mUserRole = smackRoleToScRole(o.getRole(), o.getAffiliation());
                    ConfigurationUtils.updateChatRoomProperty(mPPS, getName(), ChatRoom.USER_ROLE, mUserRole.getRoleName());
                }
            }
        }
        // Timber.d("ChatRoom user role: %s", mUserRole);
        return mUserRole;
    }

    /**
     * Sets the new mRole for the local user in the context of this chatRoom.
     *
     * @param role the new mRole to be set for the local user
     */
    public void setLocalUserRole(ChatRoomMemberRole role)
    {
        setLocalUserRole(role, false);
    }

    /**
     * Sets the new mRole for the local user in the context of this chatRoom.
     *
     * @param role the new mRole to be set for the local user
     * @param isInitial if <tt>true</tt> this is initial mRole set.
     */
    private void setLocalUserRole(ChatRoomMemberRole role, boolean isInitial)
    {
        fireLocalUserRoleEvent(getUserRole(), role, isInitial);

        // update only after getUserRole() to local variable and save to database
        mUserRole = role;
        if (!isInitial)
            ConfigurationUtils.updateChatRoomProperty(mPPS, getName(), ChatRoom.USER_ROLE, role.getRoleName());
    }

    /**
     * Instances of this class should be registered as <tt>ParticipantStatusListener</tt> in smack
     * and translates events .
     */
    private class MemberListener implements ParticipantStatusListener
    {
        /**
         * Called when an administrator or owner banned a participant from the room. This means
         * that banned participant will no longer be able to join the room unless the ban has been
         * removed.
         *
         * @param participant the participant that was banned from the room (e.g.
         * room@conference.jabber.org/nick).
         * @param actor the administrator that banned the occupant (e.g. user@host.org).
         * @param reason the reason provided by the administrator to ban the occupant.
         */
        @Override
        public void banned(EntityFullJid participant, Jid actor, String reason)
        {
            Timber.i("%s has been banned from chat room: %s", participant, getName());

            ChatRoomMemberJabberImpl member = findMemberFromParticipant(participant);
            if (member != null) {
                Resourcepart nick = participant.getResourceOrThrow();
                synchronized (members) {
                    members.remove(nick);
                }
                banList.put(nick, member);
                fireMemberRoleEvent(member, member.getCurrentRole(), ChatRoomMemberRole.OUTCAST);
            }
        }

        /**
         * Called when an owner grants administrator privileges to a user. This means that the user
         * will be able to perform administrative functions such as banning users and edit
         * moderator list.
         *
         * @param participant the participant that was granted administrator privileges (e.g.
         * room@conference.jabber.org/nick).
         */
        @Override
        public void adminGranted(EntityFullJid participant)
        {
            ChatRoomMemberJabberImpl member = findMemberFromParticipant(participant);
            if (member != null)
                fireMemberRoleEvent(member, member.getCurrentRole(), ChatRoomMemberRole.ADMINISTRATOR);
        }

        /**
         * Called when an owner revokes administrator privileges from a user. This means that the
         * user will no longer be able to perform administrative functions such as banning users
         * and edit moderator list.
         *
         * @param participant the participant that was revoked administrator privileges (e.g.
         * room@conference.jabber.org/nick).
         */
        @Override
        public void adminRevoked(EntityFullJid participant)
        {
            ChatRoomMemberJabberImpl member = findMemberFromParticipant(participant);
            if (member != null)
                fireMemberRoleEvent(member, member.getCurrentRole(), ChatRoomMemberRole.MEMBER);
        }

        /**
         * Called when a new room occupant has joined the room. Note: Take in consideration that
         * when you join a room you will receive the list of current occupants in the room. This
         * message will be sent for each occupant.
         *
         * @param participant the participant that has just joined the room (e.g.
         * room@conference.jabber.org/nick).
         */
        @Override
        public void joined(EntityFullJid participant)
        {
            Timber.i("%s has joined chatRoom: %s", participant, getName());

            // We try to get the nickname of the participantName in case it's in the form john@servicename.com,
            // because the nickname we keep in the nickname property is just the user name like "john".
            Resourcepart participantNick = participant.getResourceOrThrow();

            // when somebody changes its nickname we first receive event for its nickname changed
            // and after that that has joined we check if this already joined and if so we skip it
            // Note: mNickName may be null so order of equals is important
            if (!participantNick.equals(mNickName) && !members.containsKey(participantNick)) {
                String reason = mucOwnPresenceReceived ? ChatRoomMemberPresenceChangeEvent.MEMBER_JOINED
                        : ChatRoomMemberPresenceChangeEvent.REASON_USER_LIST;

                // smack returns fully qualified occupant names.
                Occupant occupant = mMultiUserChat.getOccupant(participant);
                ChatRoomMemberJabberImpl member
                        = new ChatRoomMemberJabberImpl(ChatRoomJabberImpl.this, occupant.getNick(), occupant.getJid());

                members.put(participantNick, member);
                // REASON_USER_LIST reason will not show participant 'has joined' in chat window
                fireMemberPresenceEvent(member, ChatRoomMemberPresenceChangeEvent.MEMBER_JOINED, reason);
            }
        }

        /**
         * Called when a room occupant has left the room on its own. This means that the occupant
         * was neither kicked nor banned from the room.
         *
         * @param participant the participant that has left the room on its own. (e.g.
         * room@conference.jabber.org/nick).
         */
        @Override
        public void left(EntityFullJid participant)
        {
            Timber.i("%s has left the chat room: %s", participant, getName());

            ChatRoomMember member = findMemberFromParticipant(participant);
            if (member != null) {
                synchronized (members) {
                    members.remove(participant.getResourceOrThrow());
                }
                fireMemberPresenceEvent(member, ChatRoomMemberPresenceChangeEvent.MEMBER_LEFT, null);
            }
        }

        /**
         * Called when a participant changed his/her nickname in the room. The new participant's
         * nickname will be informed with the next available presence.
         *
         * @param participant the participant that has changed his nickname
         * @param newNickname the new nickname that the participant decided to use.
         */
        @Override
        public void nicknameChanged(EntityFullJid participant, Resourcepart newNickname)
        {
            ChatRoomMemberJabberImpl member = findMemberFromParticipant(participant);
            if (member == null)
                return;

            // update local mNickName if from own NickName change
            if (mNickName.equals(member.getNickAsResourcepart()))
                mNickName = newNickname;

            member.setNick(newNickname);
            synchronized (members) {
                // change the member key
                ChatRoomMemberJabberImpl mem = members.remove(participant.getResourceOrThrow());
                members.put(newNickname, mem);
            }

            ChatRoomMemberPropertyChangeEvent evt = new ChatRoomMemberPropertyChangeEvent(member,
                    ChatRoomJabberImpl.this, ChatRoomMemberPropertyChangeEvent.MEMBER_NICKNAME,
                    participant.getResourceOrThrow(), newNickname);
            fireMemberPropertyChangeEvent(evt);
        }

        /**
         * Called when an owner revokes a user ownership on the room. This means that the user will
         * no longer be able to change defining room features as well as perform all administrative functions.
         *
         * @param participant the participant that was revoked ownership on the room (e.g.
         * room@conference.jabber.org/nick).
         */
        public void ownershipRevoked(EntityFullJid participant)
        {
            ChatRoomMemberJabberImpl member = findMemberFromParticipant(participant);
            if (member != null)
                fireMemberRoleEvent(member, member.getCurrentRole(), ChatRoomMemberRole.MEMBER);
        }

        /**
         * Called when a room participant has been kicked from the room. This means that the kicked
         * participant is no longer participating in the room.
         *
         * @param participant the participant that was kicked from the room (e.g. room@conference.jabber.org/nick).
         * @param actor the moderator that kicked the occupant from the room (e.g. user@host.org).
         * @param reason the reason provided by the actor to kick the occupant from the room.
         */
        @Override
        public void kicked(EntityFullJid participant, Jid actor, String reason)
        {
            ChatRoomMember member = findMemberFromParticipant(participant);
            if (member != null) {
                synchronized (members) {
                    members.remove(participant.getResourceOrThrow());
                }
                fireMemberPresenceEvent(member, actor, ChatRoomMemberPresenceChangeEvent.MEMBER_KICKED, reason);
            }
        }

        /**
         * Called when an administrator grants moderator privileges to a user. This means that the
         * user will be able to kick users, grant and revoke voice, invite other users, modify
         * room's subject plus all the participants privileges.
         *
         * @param participant the participant that was granted moderator privileges in the room (e.g.
         * room@conference.jabber.org/nick).
         */
        @Override
        public void moderatorGranted(EntityFullJid participant)
        {
            ChatRoomMemberJabberImpl member = findMemberFromParticipant(participant);
            if (member != null)
                fireMemberRoleEvent(member, member.getCurrentRole(), ChatRoomMemberRole.MODERATOR);
        }

        /**
         * Called when a moderator revokes voice from a participant. This means that the
         * participant in the room was able to speak and now is a visitor that can't send
         * messages to the room occupants.
         *
         * @param participant the participant that was revoked voice from the room e.g. room@conference.jabber.org/nick
         */
        @Override
        public void voiceRevoked(EntityFullJid participant)
        {
            ChatRoomMemberJabberImpl member = findMemberFromParticipant(participant);
            if (member != null)
                fireMemberRoleEvent(member, member.getCurrentRole(), ChatRoomMemberRole.SILENT_MEMBER);
        }

        /**
         * Called when an administrator grants a user membership to the room. This means that the
         * user will be able to join the members-only room.
         *
         * @param participant the participant that was granted membership in the room e.g. room@conference.jabber.org/nick
         */
        @Override
        public void membershipGranted(EntityFullJid participant)
        {
            ChatRoomMemberJabberImpl member = findMemberFromParticipant(participant);
            if (member != null)
                fireMemberRoleEvent(member, member.getCurrentRole(), ChatRoomMemberRole.MEMBER);
        }

        /**
         * Called when an administrator revokes moderator privileges from a user. This means that
         * the user will no longer be able to kick users, grant and revoke voice, invite other
         * users, modify room's subject plus all the participants privileges.
         *
         * @param participant the participant that was revoked moderator privileges in the room (e.g.
         * room@conference.jabber.org/nick).
         */
        @Override
        public void moderatorRevoked(EntityFullJid participant)
        {
            ChatRoomMemberJabberImpl member = findMemberFromParticipant(participant);
            if (member != null)
                fireMemberRoleEvent(member, member.getCurrentRole(), ChatRoomMemberRole.MEMBER);
        }

        /**
         * Called when a moderator grants voice to a visitor. This means that the visitor can now
         * participate in the moderated room sending messages to all occupants.
         *
         * @param participant the participant that was granted voice in the room (e.g.
         * room@conference.jabber.org/nick).
         */
        @Override
        public void voiceGranted(EntityFullJid participant)
        {
            ChatRoomMemberJabberImpl member = findMemberFromParticipant(participant);
            if (member != null)
                fireMemberRoleEvent(member, member.getCurrentRole(), ChatRoomMemberRole.MEMBER);
        }

        /**
         * Called when an administrator revokes a user membership to the room. This means that the
         * user will not be able to join the members-only room.
         *
         * @param participant the participant that was revoked membership from the room (e.g.
         * room@conference.jabber.org/nick).
         */
        @Override
        public void membershipRevoked(EntityFullJid participant)
        {
            ChatRoomMemberJabberImpl member = findMemberFromParticipant(participant);
            if (member != null)
                fireMemberRoleEvent(member, member.getCurrentRole(), ChatRoomMemberRole.GUEST);
        }

        /**
         * Called when an owner grants a user ownership on the room. This means that the user will
         * be able to change defining room features as well as perform all administrative functions.
         *
         * @param participant the participant that was granted ownership on the room (e.g.
         * room@conference.jabber.org/nick).
         */
        @Override
        public void ownershipGranted(EntityFullJid participant)
        {
            ChatRoomMemberJabberImpl member = findMemberFromParticipant(participant);
            if (member != null)
                fireMemberRoleEvent(member, member.getCurrentRole(), ChatRoomMemberRole.OWNER);
        }
    }

    /**
     * Adds a listener that will be notified of changes in our mRole in the room such as us being
     * granted operator.
     *
     * @param listener a local user mRole listener.
     */
    @Override
    public void addLocalUserRoleListener(ChatRoomLocalUserRoleListener listener)
    {
        synchronized (localUserRoleListeners) {
            if (!localUserRoleListeners.contains(listener))
                localUserRoleListeners.add(listener);
        }
    }

    /**
     * Removes a listener that was being notified of changes in our mRole in this chat room such as
     * us being granted operator.
     *
     * @param listener a local user mRole listener.
     */
    @Override
    public void removeLocalUserRoleListener(ChatRoomLocalUserRoleListener listener)
    {
        synchronized (localUserRoleListeners) {
            localUserRoleListeners.remove(listener);
        }
    }

    /**
     * Adds a listener that will be notified of changes of a member mRole in the room such as being granted operator.
     *
     * @param listener a member mRole listener.
     */
    @Override
    public void addMemberRoleListener(ChatRoomMemberRoleListener listener)
    {
        synchronized (memberRoleListeners) {
            if (!memberRoleListeners.contains(listener))
                memberRoleListeners.add(listener);
        }
    }

    /**
     * Removes a listener that was being notified of changes of a member mRole in this chat room
     * such as us being granted operator.
     *
     * @param listener a member mRole listener.
     */
    @Override
    public void removeMemberRoleListener(ChatRoomMemberRoleListener listener)
    {
        synchronized (memberRoleListeners) {
            memberRoleListeners.remove(listener);
        }
    }

    /**
     * Returns the list of banned users.
     *
     * @return a list of all banned participants
     */
    @Override
    public Iterator<ChatRoomMember> getBanList()
    {
        return banList.values().iterator();
    }

    /**
     * Changes the local user nickname. If the new nickname already exist in the chat room
     * throws an OperationFailedException.
     *
     * @param nickname the new nickname within the room.
     * @throws OperationFailedException if the new nickname already exist in this room
     */
    @Override
    public void setUserNickname(String nickname)
            throws OperationFailedException
    {
        // parseLocalPart or take nickname as it
        String sNickname = nickname.split("@")[0];

        try {
            mNickName = Resourcepart.from(sNickname);
            mMultiUserChat.changeNickname(mNickName);
        } catch (XMPPException | NoResponseException | NotConnectedException | XmppStringprepException
                | MultiUserChatException.MucNotJoinedException | InterruptedException e) {

            String msg = "Failed to change nickname for chat room: " + getName() + " => " + e.getMessage();
            Timber.e("%s", msg);
            throw new OperationFailedException(msg, OperationFailedException.IDENTIFICATION_CONFLICT);
        }
    }

    /**
     * Bans a user from the room. An admin or owner of the room can ban users from a room.
     *
     * @param member the <tt>ChatRoomMember</tt> to be banned.
     * @param reason the reason why the user was banned.
     * @throws OperationFailedException if an error occurs while banning a user. In particular, an error can occur if a
     * moderator or a user with an affiliation of "owner" or "admin" was tried to be banned
     * or if the user that is banning have not enough permissions to ban.
     */
    @Override
    public void banParticipant(ChatRoomMember member, String reason)
            throws OperationFailedException
    {
        try {
            Jid jid = ((ChatRoomMemberJabberImpl) member).getJabberID();
            mMultiUserChat.banUser(jid, reason);
        } catch (XMPPErrorException e) {
            Timber.e(e, "Failed to ban participant.");

            // If a moderator or a user with an affiliation of "owner" or "admin" was intended to be kicked.
            if (e.getStanzaError().getCondition().equals(Condition.not_allowed)) {
                throw new OperationFailedException("Kicking an admin user or a chat room owner is a forbidden operation.",
                        OperationFailedException.FORBIDDEN);
            }
            else {
                throw new OperationFailedException("An error occurred while trying to kick the participant.",
                        OperationFailedException.GENERAL_ERROR);
            }
        } catch (NoResponseException | NotConnectedException | InterruptedException e) {
            throw new OperationFailedException("An error occurred while trying to kick the participant.",
                    OperationFailedException.GENERAL_ERROR);
        }
    }

    /**
     * Kicks a participant from the room.
     *
     * @param member the <tt>ChatRoomMember</tt> to kick from the room
     * @param reason the reason why the participant is being kicked from the room
     * @throws OperationFailedException if an error occurs while kicking the participant. In particular, an error can occur
     * if a moderator or a user with an affiliation of "owner" or "admin" was intended to be kicked;
     * or if the participant that intended to kick another participant does not have kicking privileges;
     */
    @Override
    public void kickParticipant(ChatRoomMember member, String reason)
            throws OperationFailedException
    {
        try {
            Resourcepart nick = ((ChatRoomMemberJabberImpl) member).getNickAsResourcepart();
            mMultiUserChat.kickParticipant(nick, reason);
        } catch (XMPPErrorException e) {
            Timber.e(e, "Failed to kick participant.");

            // If a moderator or a user with an affiliation of "owner" or "admin" was intended to be kicked.
            if (e.getStanzaError().getCondition().equals(Condition.not_allowed)) {
                throw new OperationFailedException(
                        "Kicking an admin user or a chat room owner is a forbidden operation.",
                        OperationFailedException.FORBIDDEN);
            }
            // If a participant that intended to kick another participant does not have kicking privileges.
            else if (e.getStanzaError().getCondition().equals(Condition.forbidden)) {
                throw new OperationFailedException(
                        "The user that intended to kick another participant does not have enough privileges to do that.",
                        OperationFailedException.NOT_ENOUGH_PRIVILEGES);
            }
            else {
                throw new OperationFailedException("An error occurred while trying to kick the participant.",
                        OperationFailedException.GENERAL_ERROR);
            }
        } catch (NoResponseException | NotConnectedException | InterruptedException e) {
            throw new OperationFailedException("An error occurred while trying to kick the participant.",
                    OperationFailedException.GENERAL_ERROR);
        }
    }

    /**
     * Creates the corresponding ChatRoomMemberPresenceChangeEvent and notifies all
     * <tt>ChatRoomMemberPresenceListener</tt>s that a ChatRoomMember has joined or left this
     * <tt>ChatRoom</tt>.
     *
     * @param member the <tt>ChatRoomMember</tt> that this
     * @param eventID the identifier of the event
     * @param eventReason the reason of the event
     */
    private void fireMemberPresenceEvent(ChatRoomMember member, String eventID, String eventReason)
    {
        ChatRoomMemberPresenceChangeEvent evt
                = new ChatRoomMemberPresenceChangeEvent(this, member, eventID, eventReason);

        Timber.log(TimberLog.FINER, "Will dispatch the following ChatRoom event: %s", evt);

        Iterable<ChatRoomMemberPresenceListener> listeners;
        synchronized (memberListeners) {
            listeners = new ArrayList<>(memberListeners);
        }
        for (ChatRoomMemberPresenceListener listener : listeners) {
            listener.memberPresenceChanged(evt);
        }
    }

    /**
     * Creates the corresponding ChatRoomMemberPresenceChangeEvent and notifies all
     * <tt>ChatRoomMemberPresenceListener</tt>s that a ChatRoomMember has joined or left this <tt>ChatRoom</tt>.
     *
     * @param member the <tt>ChatRoomMember</tt> that changed its presence status
     * @param actor the <tt>ChatRoomMember</tt> that participated as an actor in this event
     * @param eventID the identifier of the event
     * @param eventReason the reason of this event
     */
    private void fireMemberPresenceEvent(ChatRoomMember member, Jid actor, String eventID, String eventReason)
    {
        ChatRoomMemberPresenceChangeEvent evt = new ChatRoomMemberPresenceChangeEvent(this, member,
                actor, eventID, eventReason);

        Timber.log(TimberLog.FINER, "Will dispatch the following ChatRoom event: %s", evt);

        Iterable<ChatRoomMemberPresenceListener> listeners;
        synchronized (memberListeners) {
            listeners = new ArrayList<>(memberListeners);
        }
        for (ChatRoomMemberPresenceListener listener : listeners)
            listener.memberPresenceChanged(evt);
    }

    /**
     * Creates the corresponding ChatRoomMemberRoleChangeEvent and notifies all
     * <tt>ChatRoomMemberRoleListener</tt>s that a ChatRoomMember has changed its mRole in this <tt>ChatRoom</tt>.
     *
     * @param member the <tt>ChatRoomMember</tt> that has changed its mRole
     * @param previousRole the previous mRole that member had
     * @param newRole the new mRole the member get
     */
    private void fireMemberRoleEvent(ChatRoomMember member, ChatRoomMemberRole previousRole, ChatRoomMemberRole newRole)
    {
        member.setRole(newRole);
        ChatRoomMemberRoleChangeEvent evt = new ChatRoomMemberRoleChangeEvent(this, member, previousRole, newRole);

        Timber.log(TimberLog.FINER, "Will dispatch the following ChatRoom event: %s", evt);

        Iterable<ChatRoomMemberRoleListener> listeners;
        synchronized (memberRoleListeners) {
            listeners = new ArrayList<>(memberRoleListeners);
        }
        for (ChatRoomMemberRoleListener listener : listeners)
            listener.memberRoleChanged(evt);
    }

    /**
     * Delivers the specified event to all registered message listeners.
     *
     * @param evt the <tt>EventObject</tt> that we'd like delivered to all registered message listeners.
     */
    void fireMessageEvent(EventObject evt)
    {
        Iterable<ChatRoomMessageListener> listeners;
        synchronized (messageListeners) {
            listeners = new ArrayList<>(messageListeners);
        }

        for (ChatRoomMessageListener listener : listeners) {
            try {
                if (evt instanceof ChatRoomMessageDeliveredEvent) {
                    listener.messageDelivered((ChatRoomMessageDeliveredEvent) evt);
                }
                else if (evt instanceof ChatRoomMessageReceivedEvent) {
                    listener.messageReceived((ChatRoomMessageReceivedEvent) evt);
                }
                else if (evt instanceof ChatRoomMessageDeliveryFailedEvent) {
                    listener.messageDeliveryFailed((ChatRoomMessageDeliveryFailedEvent) evt);
                }
            } catch (Throwable e) {
                Timber.e(e, "Error delivering multi chat message for %s", listener);
            }
        }
    }

    /**
     * Publishes a conference to the room by sending a <tt>Presence</tt> IQ which contains a
     * <tt>ConferenceDescriptionExtensionElement</tt>
     *
     * @param cd the description of the conference to announce
     * @param name the name of the conference
     * @return the <tt>ConferenceDescription</tt> that was announced (e.g. <tt>cd</tt> on
     * success or <tt>null</tt> on failure)
     */
    @Override
    public ConferenceDescription publishConference(ConferenceDescription cd, String name)
    {
        if (publishedConference != null) {
            cd = publishedConference;
            cd.setAvailable(false);
        }
        else {
            String displayName;
            if (TextUtils.isEmpty(name)) {
                displayName = aTalkApp.getResString(R.string.service_gui_CHAT_CONFERENCE_ITEM_LABEL, mNickName.toString());
            }
            else {
                displayName = name;
            }
            cd.setDisplayName(displayName);
        }

        ConferenceDescriptionExtension ext
                = new ConferenceDescriptionExtension(cd.getUri(), cd.getUri(), cd.getPassword());
        if (lastPresenceSent != null) {
            setPacketExtension(lastPresenceSent, ext, ConferenceDescriptionExtension.NAMESPACE);
            try {
                mPPS.getConnection().sendStanza(lastPresenceSent);
            } catch (NotConnectedException | InterruptedException e) {
                Timber.w(e, "Could not publish conference");
            }
        }
        else {
            Timber.w("Could not publish conference, lastPresenceSent is null.");
            publishedConference = null;
            publishedConferenceExt = null;
            return null;
        }
        /*
         * Save the extensions to set to other outgoing Presence packets
         */
        publishedConference = (!cd.isAvailable()) ? null : cd;
        publishedConferenceExt = (publishedConference == null) ? null : ext;


        fireConferencePublishedEvent(members.get(mNickName), cd,
                ChatRoomConferencePublishedEvent.CONFERENCE_DESCRIPTION_SENT);
        return cd;
    }

    /**
     * Sets <tt>ext</tt> as the only <tt>ExtensionElement</tt> that belongs to given <tt>namespace</tt> of the <tt>packet</tt>.
     *
     * @param packet the <tt>Packet<tt> to be modified.
     * @param extension the <tt>ConferenceDescriptionPacketExtension<tt> to set, or <tt>null</tt> to not set one.
     * @param namespace the namespace of <tt>ExtensionElement</tt>.
     * @param matchElementName if {@code true} only extensions matching both the element name and namespace will be matched
     * and removed. Otherwise, only the namespace will be matched.
     */
    private static void setPacketExtension(Stanza packet, ExtensionElement extension, String namespace, boolean matchElementName)
    {
        if (StringUtils.isEmpty(namespace)) {
            return;
        }

        // clear previous announcements
        ExtensionElement pe;
        if (matchElementName && extension != null) {
            String element = extension.getElementName();
            while (null != (pe = packet.getExtensionElement(element, namespace))) {
                packet.removeExtension(pe);
            }
        }
        else {
            while (null != (pe = packet.getExtension(namespace))) {
                packet.removeExtension(pe);
            }
        }
        if (extension != null) {
            packet.addExtension(extension);
        }
    }


    /**
     * Sets <tt>ext</tt> as the only <tt>ExtensionElement</tt> that belongs to given <tt>namespace</tt>
     * of the <tt>packet</tt>.
     *
     * @param packet the <tt>Packet<tt> to be modified.
     * @param extension the <tt>ConferenceDescriptionPacketExtension<tt> to set, or <tt>null</tt> to not set one.
     * @param namespace the namespace of <tt>ExtensionElement</tt>.
     */
    private static void setPacketExtension(Stanza packet, ExtensionElement extension, String namespace)
    {
        setPacketExtension(packet, extension, namespace, false);
    }

    /**
     * Publishes new status message in chat room presence.
     *
     * @param newStatus the new status message to be published in the MUC.
     */
    public void publishPresenceStatus(String newStatus)
    {
        if (lastPresenceSent != null) {
            lastPresenceSent.setStatus(newStatus);
            try {
                mPPS.getConnection().sendStanza(lastPresenceSent);
            } catch (NotConnectedException | InterruptedException e) {
                Timber.e(e, "Could not publish presence");
            }
        }
    }

    /**
     * Adds given <tt>ExtensionElement</tt> to the MUC presence and publishes it immediately.
     *
     * @param extension the <tt>ExtensionElement</tt> to be included in MUC presence.
     */
    public void sendPresenceExtension(ExtensionElement extension)
    {
        if (lastPresenceSent != null) {
            setPacketExtension(lastPresenceSent, extension, extension.getNamespace(), true);
            try {
                mPPS.getConnection().sendStanza(lastPresenceSent);
            } catch (NotConnectedException | InterruptedException e) {
                Timber.e(e, "Could not send presence");
            }
        }
    }

    /**
     * Removes given <tt>PacketExtension</tt> from the MUC presence and publishes it immediately.
     *
     * @param extension the <tt>PacketExtension</tt> to be removed from the MUC presence.
     */
    public void removePresenceExtension(ExtensionElement extension)
    {
        if (lastPresenceSent != null) {
            setPacketExtension(lastPresenceSent, null, extension.getNamespace());
            try {
                mPPS.getConnection().sendStanza(lastPresenceSent);
            } catch (NotConnectedException | InterruptedException e) {
                Timber.e(e, "Could not remove presence");
            }
        }
    }

    /**
     * Returns the ids of the users that has the member mRole in the room. When the room is member
     * only, this are the users allowed to join.
     *
     * @return the ids of the users that has the member mRole in the room.
     */
    public List<Jid> getMembersWhiteList()
    {
        List<Jid> res = new ArrayList<>();
        try {
            for (Affiliate a : mMultiUserChat.getMembers()) {
                res.add(a.getJid());
            }
        } catch (XMPPException | NoResponseException | NotConnectedException | InterruptedException e) {
            Timber.e(e, "Cannot obtain members list");
        }
        return res;
    }

    /**
     * Changes the list of users that has mRole member for this room. When the room is member only,
     * this are the users allowed to join.
     *
     * @param members the ids of user to have member mRole.
     */
    public void setMembersWhiteList(List<Jid> members)
    {
        try {
            List<Jid> membersToRemove = getMembersWhiteList();
            membersToRemove.removeAll(members);

            if (membersToRemove.size() > 0)
                mMultiUserChat.revokeMembership(membersToRemove);

            if (members.size() > 0)
                mMultiUserChat.grantMembership(members);
        } catch (XMPPException | NoResponseException | NotConnectedException
                | InterruptedException e) {
            Timber.e(e, "Cannot modify members list");
        }
    }

    /**
     * A listener that listens for packets of type Message and fires an event to notifier
     * interesting parties that a message was received.
     */
    private class MucMessageListener implements MessageListener
    {
        /**
         * The timestamp of the last history message sent to the UI. Do not send earlier or
         * messages with the same timestamp.
         */
        private Date lsdMessageTime = null;

        /**
         * The property to store the timestamp.
         */
        private static final String LAST_SEEN_DELAYED_MESSAGE_PROP = "lastSeenDelayedMessage";

        /**
         * Process a Message stanza.
         *
         * @param message Smack Message to process.
         */
        @Override
        public void processMessage(Message message)
        {
            // Leave handling of omemo messages to onOmemoMessageReceived()
            if ((message == null) || message.hasExtension(OmemoElement.NAME_ENCRYPTED, OmemoConstants.OMEMO_NAMESPACE_V_AXOLOTL))
                return;

            // Captcha challenge body is in body extension
            String msgBody = null;
            Set<Message.Body> msgBodies = message.getBodies();
            if (!msgBodies.isEmpty()) {
                for (Message.Body body : msgBodies) {
                    if (body != null) {
                        msgBody = body.getMessage();
                        break;
                    }
                }
            }
            // Check if the message is of Type.error if none in Message Body
            if ((msgBody == null) && (message.getType() == Message.Type.error)) {
                msgBody = "";
            }

            if (msgBody == null)
                return;

            Date timeStamp;
            DelayInformation delayInfo = message.getExtension(DelayInformation.class);
            if (delayInfo != null) {
                timeStamp = delayInfo.getStamp();

                // This is a delayed chat room message, a history message for the room coming from
                // server. Lets check have we already shown this message and if this is the case
                // skip it otherwise save it as last seen delayed message
                if (lsdMessageTime == null) {
                    // initialise this from configuration
                    String sTimestamp = ConfigurationUtils.getChatRoomProperty(mPPS, getName(),
                            LAST_SEEN_DELAYED_MESSAGE_PROP);

                    try {
                        if (!TextUtils.isEmpty(sTimestamp))
                            lsdMessageTime = new Date(Long.parseLong(sTimestamp));
                    } catch (Throwable ex) {
                        Timber.w("TimeStamp property is null! %s", timeStamp);
                    }
                }

                if (lsdMessageTime != null && !timeStamp.after(lsdMessageTime))
                    return;

                // save it in configuration
                ConfigurationUtils.updateChatRoomProperty(mPPS, getName(),
                        LAST_SEEN_DELAYED_MESSAGE_PROP, String.valueOf(timeStamp.getTime()));
                lsdMessageTime = timeStamp;
            }
            else {
                timeStamp = new Date();
            }

            // for delay message only
            Jid jabberID = message.getFrom();
            MultipleAddresses mAddress = message.getExtension(MultipleAddresses.class);
            if (mAddress != null) {
                List<MultipleAddresses.Address> addresses = mAddress.getAddressesOfType(MultipleAddresses.Type.ofrom);
                jabberID = addresses.get(0).getJid().asBareJid();
            }

            ChatRoomMember member;
            int messageReceivedEventType = ChatMessage.MESSAGE_MUC_IN;
            Jid entityJid = message.getFrom();  // chatRoom entityJid
            Resourcepart fromNick = entityJid.getResourceOrNull();

            // when the message comes from the room itself, it is a system message
            if (entityJid.equals(getName())) {
                messageReceivedEventType = ChatMessage.MESSAGE_SYSTEM;
                member = new ChatRoomMemberJabberImpl(ChatRoomJabberImpl.this, Resourcepart.EMPTY, getIdentifier());
            }
            else {
                member = members.get(entityJid.getResourceOrThrow());
            }

            // sometimes when connecting to rooms they send history when the member is no longer
            // available we create a fake one so the messages to be displayed.
            if (member == null) {
                member = new ChatRoomMemberJabberImpl(ChatRoomJabberImpl.this, fromNick, jabberID);
            }

            // set up default in case XHTMLExtension contains no message
            // if msgBody contains markup text then set as ENCODE_HTML mode
            int encType = IMessage.ENCODE_PLAIN;
            if (msgBody.matches("(?s).*?<[A-Za-z]+>.*?</[A-Za-z]+>.*?")) {
                encType = IMessage.ENCODE_HTML;
            }
            IMessage newMessage = createMessage(msgBody, encType, null);

            // check if the message is available in xhtml
            String xhtmString = XhtmlUtil.getXhtmlExtension(message);
            if (xhtmString != null) {
                newMessage = createMessage(xhtmString, IMessage.ENCODE_HTML, null);
            }
            newMessage.setRemoteMsgId(message.getStanzaId());

            if (message.getType() == Message.Type.error) {
                Timber.d("Message error received from: %s", jabberID);

                StanzaError error = message.getError();
                String errorReason = error.getConditionText();
                if (TextUtils.isEmpty(errorReason)) {
                    // errorReason = error.getDescriptiveText();
                    errorReason = error.toString();
                }

                // Failed Event error
                int errorResultCode = (ChatMessage.MESSAGE_SYSTEM == messageReceivedEventType) ?
                        MessageDeliveryFailedEvent.SYSTEM_ERROR_MESSAGE : MessageDeliveryFailedEvent.UNKNOWN_ERROR;

                Condition errorCondition = error.getCondition();
                if (Condition.service_unavailable == errorCondition) {
                    if (!member.getPresenceStatus().isOnline()) {
                        errorResultCode = MessageDeliveryFailedEvent.OFFLINE_MESSAGES_NOT_SUPPORTED;
                    }
                }
                else if (Condition.not_acceptable == errorCondition) {
                    errorResultCode = MessageDeliveryFailedEvent.NOT_ACCEPTABLE;
                }

                ChatRoomMessageDeliveryFailedEvent failedEvent
                        = new ChatRoomMessageDeliveryFailedEvent(ChatRoomJabberImpl.this,
                        member, errorResultCode, System.currentTimeMillis(), errorReason, newMessage);
                fireMessageEvent(failedEvent);
                return;
            }

            // Check received message for sent message: either a delivery report or a message coming from the
            // chaRoom server. Checking using nick OR jid in case user join with a different nick.
            Timber.d("Received room message %s", message.toString());
            if (((getUserNickname() != null) && getUserNickname().equals(fromNick))
                    || ((jabberID != null) && jabberID.equals(getAccountId(member.getChatRoom())))) {

                // MUC received message may be relayed from server on message sent hence reCreate the message if required
                if (IMessage.FLAG_REMOTE_ONLY == (mEncType & IMessage.FLAG_MODE_MASK)) {
                    newMessage = createMessage(msgBody, mEncType, "");
                    newMessage.setRemoteMsgId(message.getStanzaId());
                }

                // message delivered for own outgoing message view display
                newMessage.setServerMsgId(message.getStanzaId());
                newMessage.setReceiptStatus(ChatMessage.MESSAGE_DELIVERY_CLIENT_SENT);
                ChatRoomMessageDeliveredEvent msgDeliveredEvt = new ChatRoomMessageDeliveredEvent(
                        ChatRoomJabberImpl.this, timeStamp, newMessage, ChatMessage.MESSAGE_MUC_OUT);

                msgDeliveredEvt.setHistoryMessage(true);
                fireMessageEvent(msgDeliveredEvt);
            }
            else {
                // CONVERSATION_MESSAGE_RECEIVED or SYSTEM_MESSAGE_RECEIVED
                ChatRoomMessageReceivedEvent msgReceivedEvt = new ChatRoomMessageReceivedEvent(
                        ChatRoomJabberImpl.this, member, timeStamp, newMessage, messageReceivedEventType);

                if (messageReceivedEventType == ChatMessage.MESSAGE_MUC_IN
                        && newMessage.getContent().contains(getUserNickname() + ":")) {
                    msgReceivedEvt.setImportantMessage(true);
                }

                msgReceivedEvt.setHistoryMessage(delayInfo != null);
                fireMessageEvent(msgReceivedEvt);
            }
        }
    }

    /**
     * A listener that is fired anytime a MUC room changes its subject.
     */
    private class MucSubjectUpdatedListener implements SubjectUpdatedListener
    {
        /**
         * Notification that subject has changed
         *
         * @param subject the new subject
         * @param from the sender from room participants
         */
        @Override
        public void subjectUpdated(String subject, EntityFullJid from)
        {
            // only fire event if subject has really changed, not for new one
            if ((subject != null) && !subject.equals(oldSubject)) {
                Timber.d("ChatRoom subject updated to '%s'", subject);
                ChatRoomPropertyChangeEvent evt = new ChatRoomPropertyChangeEvent(
                        ChatRoomJabberImpl.this, ChatRoomPropertyChangeEvent.CHAT_ROOM_SUBJECT, oldSubject, subject);
                firePropertyChangeEvent(evt);
            }
            // Keeps track of the subject.
            oldSubject = subject;
        }
    }

    /**
     * A listener that is fired anytime self status in a room is changed, such as the
     * user being kicked, banned, or granted admin permissions.
     */
    private class LocalUserStatusListener implements UserStatusListener
    {
        /**
         * Called when a moderator kicked your user from the room. This means that you are no
         * longer participating in the room.
         *
         * @param actor the moderator that kicked your user from the room (e.g. user@host.org).
         * @param reason the reason provided by the actor to kick you from the room.
         */
        @Override
        public void kicked(Jid actor, String reason)
        {
            opSetMuc.fireLocalUserPresenceEvent(ChatRoomJabberImpl.this,
                    LocalUserChatRoomPresenceChangeEvent.LOCAL_USER_KICKED, reason);
            leave();
        }

        /**
         * Called when a moderator grants voice to your user. This means that you were a visitor in the
         * moderated room before and now you can participate in the room by sending messages to all occupants.
         */
        @Override
        public void voiceGranted()
        {
            setLocalUserRole(ChatRoomMemberRole.MEMBER);
        }

        /**
         * Called when a moderator revokes voice from your user. This means that you were a participant in the
         * room able to speak and now you are a visitor that can't send messages to the room occupants.
         */
        @Override
        public void voiceRevoked()
        {
            setLocalUserRole(ChatRoomMemberRole.SILENT_MEMBER);
        }

        /**
         * Called when an administrator or owner banned your user from the room. This means that
         * you will no longer be able to join the room unless the ban has been removed.
         *
         * @param actor the administrator that banned your user (e.g. user@host.org).
         * @param reason the reason provided by the administrator to banned you.
         */
        @Override
        public void banned(Jid actor, String reason)
        {
            opSetMuc.fireLocalUserPresenceEvent(ChatRoomJabberImpl.this,
                    LocalUserChatRoomPresenceChangeEvent.LOCAL_USER_DROPPED, reason);
            leave();
        }

        /**
         * Called when an administrator grants your user membership to the room. This means that
         * you will be able to join the members-only room.
         */
        @Override
        public void membershipGranted()
        {
            setLocalUserRole(ChatRoomMemberRole.MEMBER);
        }

        /**
         * Called when an administrator revokes your user membership to the room. This means that
         * you will not be able to join the members-only room.
         */
        @Override
        public void membershipRevoked()
        {
            setLocalUserRole(ChatRoomMemberRole.GUEST);
        }

        /**
         * Called when an administrator grants moderator privileges to your user. This means that
         * you will be able to kick users, grant and revoke voice, invite other users, modify
         * room's subject plus all the participants privileges.
         */
        @Override
        public void moderatorGranted()
        {
            setLocalUserRole(ChatRoomMemberRole.MODERATOR);
        }

        /**
         * Called when an administrator revokes moderator privileges from your user. This means
         * that you will no longer be able to kick users, grant and revoke voice, invite other
         * users, modify room's subject plus all the participants privileges.
         */
        @Override
        public void moderatorRevoked()
        {
            setLocalUserRole(ChatRoomMemberRole.MEMBER);
        }

        /**
         * Called when an owner grants to your user ownership on the room. This means that you will
         * be able to change defining room features as well as perform all administrative
         * functions.
         */
        @Override
        public void ownershipGranted()
        {
            setLocalUserRole(ChatRoomMemberRole.OWNER);
        }

        /**
         * Called when an owner revokes from your user ownership on the room. This means that you
         * will no longer be able to change defining room features as well as perform all
         * administrative functions.
         */
        @Override
        public void ownershipRevoked()
        {
            setLocalUserRole(ChatRoomMemberRole.MEMBER);
        }

        /**
         * Called when an owner grants administrator privileges to your user. This means that you
         * will be able to perform administrative functions such as banning users and edit
         * moderator list.
         */
        @Override
        public void adminGranted()
        {
            setLocalUserRole(ChatRoomMemberRole.ADMINISTRATOR);
        }

        /**
         * Called when an owner revokes administrator privileges from your user. This means that
         * you will no longer be able to perform administrative functions such as banning users and
         * edit moderator list.
         */
        @Override
        public void adminRevoked()
        {
            setLocalUserRole(ChatRoomMemberRole.MEMBER);
        }

        /**
         * Called when the room is destroyed.
         *
         * @param alternateMUC an alternate MultiUserChat, may be null.
         * @param reason the reason why the room was destroyed, may be null.
         */
        @Override
        public void roomDestroyed(MultiUserChat alternateMUC, String reason)
        {
            Timber.d("CharRoom destroyed, alternate MUC: %s. Reason: %s", alternateMUC, reason);
        }
    }

    /**
     * Creates the corresponding ChatRoomLocalUserRoleChangeEvent and notifies all
     * <tt>ChatRoomLocalUserRoleListener</tt>s that local user's mRole has been changed in this
     * <tt>ChatRoom</tt>.
     *
     * @param previousRole the previous mRole that local user had
     * @param newRole the new mRole the local user gets
     * @param isInitial if <tt>true</tt> this is initial mRole set.
     */
    private void fireLocalUserRoleEvent(ChatRoomMemberRole previousRole, ChatRoomMemberRole newRole, boolean isInitial)
    {
        ChatRoomLocalUserRoleChangeEvent evt
                = new ChatRoomLocalUserRoleChangeEvent(this, previousRole, newRole, isInitial);

        Timber.log(TimberLog.FINER, "Will dispatch the following ChatRoom event: %s", evt);

        Iterable<ChatRoomLocalUserRoleListener> listeners;
        synchronized (localUserRoleListeners) {
            listeners = new ArrayList<>(localUserRoleListeners);
        }
        for (ChatRoomLocalUserRoleListener listener : listeners)
            listener.localUserRoleChanged(evt);
    }

    /**
     * Delivers the specified event to all registered property change listeners.
     *
     * @param evt the <tt>PropertyChangeEvent</tt> that we'd like delivered to all registered property
     * change listeners.
     */
    private void firePropertyChangeEvent(PropertyChangeEvent evt)
    {
        Iterable<ChatRoomPropertyChangeListener> listeners;
        synchronized (propertyChangeListeners) {
            listeners = new ArrayList<>(propertyChangeListeners);
        }

        for (ChatRoomPropertyChangeListener listener : listeners) {
            if (evt instanceof ChatRoomPropertyChangeEvent) {
                listener.chatRoomPropertyChanged((ChatRoomPropertyChangeEvent) evt);
            }
            else if (evt instanceof ChatRoomPropertyChangeFailedEvent) {
                listener.chatRoomPropertyChangeFailed((ChatRoomPropertyChangeFailedEvent) evt);
            }
        }
    }

    /**
     * Delivers the specified event to all registered property change listeners.
     *
     * @param evt the <tt>ChatRoomMemberPropertyChangeEvent</tt> that we'd like deliver to all
     * registered member property change listeners.
     */
    private void fireMemberPropertyChangeEvent(ChatRoomMemberPropertyChangeEvent evt)
    {
        Iterable<ChatRoomMemberPropertyChangeListener> listeners;
        synchronized (memberPropChangeListeners) {
            listeners = new ArrayList<>(memberPropChangeListeners);
        }
        for (ChatRoomMemberPropertyChangeListener listener : listeners)
            listener.chatRoomPropertyChanged(evt);
    }

    /**
     * Utility method throwing an exception if the stack is not properly initialized.
     *
     * @throws java.lang.IllegalStateException if the underlying stack is not registered and initialized.
     */
    private void assertConnected()
            throws IllegalStateException
    {
        if (mPPS == null)
            throw new IllegalStateException("The mProvider must be non-null and signed on the "
                    + "service before being able to communicate.");
        if (!mPPS.isRegistered())
            throw new IllegalStateException("The mProvider must be signed on the service before "
                    + "being able to communicate.");
    }

    /**
     * Returns the <tt>ChatRoomConfigurationForm</tt> containing all configuration properties for
     * this chat room. If the user doesn't have permissions to see and change chat room
     * configuration an <tt>OperationFailedException</tt> is thrown.
     *
     * @return the <tt>ChatRoomConfigurationForm</tt> containing all configuration properties for this chat room
     * @throws OperationFailedException if the user doesn't have permissions to see and change chat room configuration
     */
    public ChatRoomConfigurationForm getConfigurationForm()
            throws OperationFailedException, InterruptedException
    {
        // The corresponding configuration form.
        ChatRoomConfigurationFormJabberImpl configForm;
        Form smackConfigForm;
        try {
            smackConfigForm = mMultiUserChat.getConfigurationForm();
            configForm = new ChatRoomConfigurationFormJabberImpl(mMultiUserChat, smackConfigForm);
        } catch (XMPPErrorException e) {
            if (e.getStanzaError().getCondition().equals(Condition.forbidden))
                throw new OperationFailedException(
                        "Failed to obtain smack multi user chat config form. User doesn't have enough privileges to see the form.",
                        OperationFailedException.NOT_ENOUGH_PRIVILEGES, e);
            else
                throw new OperationFailedException(
                        "Failed to obtain smack multi user chat config form.", OperationFailedException.GENERAL_ERROR, e);
        } catch (NoResponseException | NotConnectedException e) {
            throw new OperationFailedException(
                    "Failed to obtain smack multi user chat config form.", OperationFailedException.GENERAL_ERROR, e);
        }
        return configForm;
    }

    /**
     * The Jabber multi user chat implementation doesn't support system rooms.
     *
     * @return false to indicate that the Jabber protocol implementation doesn't support system rooms.
     */
    public boolean isSystem()
    {
        return false;
    }

    /**
     * Determines whether this chat room should be stored in the configuration file or not. If the
     * chat room is persistent it still will be shown after a restart in the chat room list. A
     * non-persistent chat room will only be in the chat room list until the the program is running.
     *
     * @return true if this chat room is persistent, false otherwise
     */
    public boolean isPersistent()
    {
        boolean persistent = false;
        EntityBareJid room = mMultiUserChat.getRoom();
        try {
            // Do not use getRoomInfo, as it has bug and throws NPE
//            DiscoverInfo info = ServiceDiscoveryManager.getInstanceFor(mProvider.getConnection()).discoverInfo(room);
//            if (info != null)
//                persistent = info.containsFeature("muc_persistent");

            RoomInfo roomInfo = MultiUserChatManager.getInstanceFor(mPPS.getConnection()).getRoomInfo(room);
            persistent = roomInfo.isPersistent();

        } catch (Exception ex) {
            Timber.w("could not get persistent state for room '%s':%s", room, ex.getMessage());
        }
        return persistent;
    }

    /**
     * Finds the member of this chat room corresponding to the given nick name.
     *
     * @param nickName the nick name to search for.
     * @return the member of this chat room corresponding to the given nick name.
     */
    public ChatRoomMemberJabberImpl findMemberForNickName(Resourcepart nickName)
    {
        synchronized (members) {
            return members.get(nickName);
        }
    }

    /**
     * Grants administrator privileges to another user. Room owners may grant administrator
     * privileges to a member or un-affiliated user. An administrator is allowed to perform
     * administrative functions such as banning users and edit moderator list.
     *
     * @param jid the bare XMPP user ID of the user to grant administrator privileges (e.g. "user@host.org").
     */
    public void grantAdmin(Jid jid)
    {
        try {
            mMultiUserChat.grantAdmin(jid);
        } catch (XMPPException | NoResponseException | NotConnectedException | InterruptedException ex) {
            Timber.e(ex, "An error occurs granting administrator privileges to a user.");
        }
    }

    /**
     * Grants membership to a user. Only administrators are able to grant membership. A user that
     * becomes a room member will be able to enter a room of type Members-Only (i.e. a room that a
     * user cannot enter without being on the member list).
     *
     * @param jid the bare XMPP user ID of the user to grant membership privileges (e.g. "user@host.org").
     */
    public void grantMembership(Jid jid)
    {
        try {
            mMultiUserChat.grantMembership(jid);
        } catch (XMPPException | NoResponseException | NotConnectedException | InterruptedException ex) {
            Timber.e(ex, "An error occurs granting membership to a user");
        }
    }

    /**
     * Grants moderator privileges to a participant or visitor. Room administrators may grant
     * moderator privileges. A moderator is allowed to kick users, grant and revoke voice, invite
     * other users, modify room's subject plus all the participants privileges.
     *
     * @param nickname the nickname of the occupant to grant moderator privileges.
     */
    public void grantModerator(String nickname)
    {
        try {
            mMultiUserChat.grantModerator(Resourcepart.from(nickname));
        } catch (XMPPException | NoResponseException | NotConnectedException | InterruptedException
                | XmppStringprepException ex) {
            Timber.e(ex, "An error occurs granting moderator privileges to a user");
        }
    }

    /**
     * Grants ownership privileges to another user. Room owners may grant ownership privileges.
     * Some room implementations will not allow to grant ownership privileges to other users. An
     * owner is allowed to change defining room features as well as perform all administrative functions.
     *
     * @param jid the bare XMPP user ID of the user to grant ownership privileges (e.g. "user@host.org").
     */
    public void grantOwnership(String jid)
    {
        try {
            mMultiUserChat.grantOwnership(JidCreate.from(jid));
        } catch (XMPPException | NoResponseException | NotConnectedException | InterruptedException
                | XmppStringprepException | IllegalArgumentException ex) {
            Timber.e(ex, "An error occurs granting ownership privileges to a user");
        }
    }

    /**
     * Grants voice to a visitor in the room. In a moderated room, a moderator may want to manage
     * who does and does not have "voice" in the room. To have voice means that a room occupant is
     * able to send messages to the room occupants.
     *
     * @param nickname the nickname of the visitor to grant voice in the room (e.g. "john").
     *
     * XMPPException if an error occurs granting voice to a visitor. In particular, a 403
     * error can occur if the occupant that intended to grant voice is not a moderator in
     * this room (i.e. Forbidden error); or a 400 error can occur if the provided nickname is
     * not present in the room.
     */
    public void grantVoice(String nickname)
    {
        try {
            mMultiUserChat.grantVoice(Resourcepart.from(nickname));
        } catch (XMPPException | NoResponseException | NotConnectedException
                | InterruptedException | XmppStringprepException ex) {
            Timber.e(ex, "An error occurs granting voice to a visitor");
        }
    }

    /**
     * Revokes administrator privileges from a user. The occupant that loses administrator
     * privileges will become a member. Room owners may revoke administrator privileges from a
     * member or unaffiliated user.
     *
     * @param jid the bare XMPP user ID of the user to grant administrator privileges (e.g. "user@host.org").
     */
    public void revokeAdmin(String jid)
    {
        try {
            mMultiUserChat.revokeAdmin((EntityJid) JidCreate.from(jid));
        } catch (XMPPException | NoResponseException | NotConnectedException
                | XmppStringprepException | IllegalArgumentException | InterruptedException ex) {
            Timber.e(ex, "n error occurs revoking administrator privileges to a user");
        }
    }

    /**
     * Revokes a user's membership. Only administrators are able to revoke membership. A user that
     * becomes a room member will be able to enter a room of type Members-Only (i.e. a room that a
     * user cannot enter without being on the member list). If the user is in the room and the room
     * is of type members-only then the user will be removed from the room.
     *
     * @param jid the bare XMPP user ID of the user to revoke membership (e.g. "user@host.org").
     */
    public void revokeMembership(String jid)
    {
        try {
            mMultiUserChat.revokeMembership(JidCreate.from(jid));
        } catch (XMPPException | NoResponseException | NotConnectedException
                | InterruptedException | IllegalArgumentException | XmppStringprepException ex) {
            Timber.e(ex, "An error occurs revoking membership to a user");
        }
    }

    /**
     * Revokes moderator privileges from another user. The occupant that loses moderator privileges
     * will become a participant. Room administrators may revoke moderator privileges only to
     * occupants whose affiliation is member or none. This means that an administrator is not
     * allowed to revoke moderator privileges from other room administrators or owners.
     *
     * @param nickname the nickname of the occupant to revoke moderator privileges.
     */
    public void revokeModerator(String nickname)
    {
        try {
            mMultiUserChat.revokeModerator(Resourcepart.from(nickname));
        } catch (XMPPException | NoResponseException | NotConnectedException
                | InterruptedException | XmppStringprepException ex) {
            Timber.e(ex, "An error occurs revoking moderator privileges from a user");
        }
    }

    /**
     * Revokes ownership privileges from another user. The occupant that loses ownership privileges
     * will become an administrator. Room owners may revoke ownership privileges. Some room
     * implementations will not allow to grant ownership privileges to other users.
     *
     * @param jid the bare XMPP user ID of the user to revoke ownership (e.g. "user@host.org").
     */
    public void revokeOwnership(String jid)
    {
        try {
            mMultiUserChat.revokeOwnership(JidCreate.from(jid));
        } catch (XMPPException | NoResponseException | NotConnectedException
                | InterruptedException | XmppStringprepException | IllegalArgumentException ex) {
            Timber.e(ex, "An error occurs revoking ownership privileges from a user");
        }
    }

    /**
     * Revokes voice from a participant in the room. In a moderated room, a moderator may want to
     * revoke an occupant's privileges to speak. To have voice means that a room occupant is
     * able to send messages to the room occupants.
     *
     * @param nickname the nickname of the participant to revoke voice (e.g. "john").
     *
     * XMPPException if an error occurs revoking voice from a participant. In particular, a
     * 405 error can occur if a moderator or a user with an affiliation of "owner" or "admin"
     * was tried to revoke his voice (i.e. Not Allowed error); or a 400 error can occur if
     * the provided nickname is not present in the room.
     */
    public void revokeVoice(String nickname)
    {
        try {
            mMultiUserChat.revokeVoice(Resourcepart.from(nickname));
        } catch (XMPPException | NoResponseException | NotConnectedException
                | InterruptedException | XmppStringprepException ex) {
            Timber.i(ex, "An error occurs revoking voice from a participant");
        }
    }

    /**
     * Returns the internal stack used chat room instance.
     *
     * @return the MultiUserChat instance used in the protocol stack.
     */
    public MultiUserChat getMultiUserChat()
    {
        return mMultiUserChat;
    }

    /**
     * The <tt>StanzaInterceptor</tt> we use to make sure that our outgoing <tt>Presence</tt>
     * packets contain the correct <tt>ConferenceAnnouncementPacketExtension</tt>.
     */
    private class PresenceInterceptor implements PresenceListener
    {
        /**
         * {@inheritDoc}
         *
         * Adds <tt>this.publishedConferenceExt</tt> as the only
         * <tt>ConferenceAnnouncementPacketExtension</tt> of <tt>packet</tt>.
         */
        @Override
        public void processPresence(Presence presence)
        {
            if (presence != null) {
                setPacketExtension(presence, publishedConferenceExt, ConferenceDescriptionExtension.NAMESPACE);
                lastPresenceSent = presence;
            }
        }
    }

    /**
     * Class implementing MultiUserChat#PresenceListener
     */
    private class ParticipantListener implements PresenceListener
    {
        /**
         * Processes an incoming presence packet from participantListener.
         *
         * @param presence the presence packet.
         */
        public void processPresence(Presence presence)
        {
            if (MUCUserStatusCodeFilter.STATUS_110_PRESENCE_TO_SELF.accept(presence))
                processOwnPresence(presence);
            else
                processOtherPresence(presence);
        }

        /**
         * Processes a <tt>Presence</tt> packet addressed to our own occupant JID.
         * with either MUCUser extension or MUCInitialPresence extension (error)
         *
         * @param presence the packet to process.
         */
        private void processOwnPresence(Presence presence)
        {
            MUCUser mucUser = presence.getExtension(MUCUser.class);
            if (mucUser != null) {
                MUCAffiliation affiliation = mucUser.getItem().getAffiliation();
                MUCRole role = mucUser.getItem().getRole();

                // if status 201 is available means that room is created and locked till we send the configuration
                if ((mucUser.getStatus() != null)
                        && mucUser.getStatus().contains(MUCUser.Status.ROOM_CREATED_201)) {
                    try {
                        TextSingleFormField formField
                                = FormField.buildHiddenFormType("http://jabber.org/protocol/muc#roomconfig");
                        DataForm dataForm = DataForm.builder(DataForm.Type.form)
                                .addField(formField).build();
                        mMultiUserChat.sendConfigurationForm(new FillableForm(dataForm));

                        // Sending null also picked up the options OperationSetMultiUserChatJabberImpl#createChatRoom and sent
                        // mMultiUserChat.sendConfigurationForm(null);
                    } catch (XMPPException | NoResponseException | NotConnectedException | InterruptedException e) {
                        Timber.e(e, "Failed to send config form.");
                    }

                    opSetMuc.addSmackInvitationRejectionListener(mMultiUserChat, ChatRoomJabberImpl.this);
                    if (affiliation == MUCAffiliation.owner) {
                        setLocalUserRole(ChatRoomMemberRole.OWNER, true);
                    }
                    else
                        setLocalUserRole(ChatRoomMemberRole.MODERATOR, true);
                }
                else {
                    // this is the presence for our own initial mRole and affiliation, as
                    // smack do not fire any initial events lets check it and fire events
                    if (role == MUCRole.moderator
                            || affiliation == MUCAffiliation.owner
                            || affiliation == MUCAffiliation.admin) {
                        ChatRoomMemberRole scRole = ChatRoomJabberImpl.smackRoleToScRole(role, affiliation);
                        setLocalUserRole(scRole, true);
                    }

                    if (!presence.isAvailable() && role == MUCRole.none
                            && affiliation == MUCAffiliation.none) {
                        Destroy destroy = mucUser.getDestroy();

                        // the room is unavailable to us, there is no message we will just leave
                        if (destroy == null) {
                            leave();
                        }
                        else {
                            leave(destroy.getJid(), aTalkApp.getResString(R.string.service_gui_CHATROOM_DESTROY_MESSAGE,
                                    destroy.getReason()));
                        }
                    }
                }
            }
            // Check for presence error (which use MUCInitialPresence extension)
            else if (Presence.Type.error == presence.getType()) {
                String errMessage = presence.getError().toString();
                addMessage(errMessage, ChatMessage.MESSAGE_ERROR);
            }
        }

        /**
         * Process a <tt>Presence</tt> packet sent by one of the other room occupants.
         */
        private void processOtherPresence(Presence presence)
        {
            Jid from = presence.getFrom();
            Resourcepart participantNick = null;
            if (from != null) {
                participantNick = from.getResourceOrNull();
            }
            ChatRoomMemberJabberImpl member = (participantNick == null) ? null : members.get(participantNick);

            ConferenceDescriptionExtension cdExt
                    = presence.getExtension(ConferenceDescriptionExtension.class);
            if (presence.isAvailable() && cdExt != null) {
                ConferenceDescription cd = new ConferenceDescription(
                        cdExt.getUri(),
                        cdExt.getCallId(),
                        cdExt.getPassword());
                cd.setAvailable(cdExt.isAvailable());
                cd.setDisplayName(getName());
                for (TransportExtension t
                        : cdExt.getChildExtensionsOfType(TransportExtension.class)) {
                    cd.addTransport(t.getNamespace());
                }
                if (!processConferenceDescription(cd, participantNick))
                    return;

                if (member != null) {
                    Timber.d("Received %s from %s in %s", cd, participantNick, mMultiUserChat.getRoom());
                    fireConferencePublishedEvent(member, cd, ChatRoomConferencePublishedEvent.CONFERENCE_DESCRIPTION_RECEIVED);
                }
                else {
                    Timber.w("Received a ConferenceDescription from an unknown member (%s) in %s",
                            participantNick, mMultiUserChat.getRoom());
                }
            }

            // if member wasn't just created, we should potentially modify some elements
            if (member == null) {
                return;
            }

            // For 4.4.3-master (20200416): presence.getExtension(Nick.class); => IllegalArgumentException
            Nick nickExt = (Nick) presence.getExtensionElement(Nick.ELEMENT_NAME, Nick.NAMESPACE);
            if (nickExt != null) {
                member.setDisplayName(nickExt.getName());
            }

            Email emailExtension = presence.getExtension(Email.class);
            if (emailExtension != null) {
                member.setEmail(emailExtension.getAddress());
            }

            AvatarUrl avatarUrl = presence.getExtension(AvatarUrl.class);
            if (avatarUrl != null) {
                member.setAvatarUrl(avatarUrl.getAvatarUrl());
            }

            StatsId statsId = presence.getExtension(StatsId.class);
            if (statsId != null) {
                member.setStatisticsID(statsId.getStatsId());
            }

            // tell listeners the member was updated (and new information about it is available)
            member.setLastPresence(presence);
            fireMemberPresenceEvent(member, ChatRoomMemberPresenceChangeEvent.MEMBER_UPDATED, null);
        }
    }

    /**
     * Listens for rejection message and delivers system message when received.
     */
    private class InvitationRejectionListeners implements InvitationRejectionListener
    {
        /**
         * Listens for rejection message and delivers system message when received.
         * Called when the invitee declines the invitation.
         *
         * @param invitee the invitee that declined the invitation. (e.g. hecate@shakespeare.lit).
         * @param reason the reason why the invitee declined the invitation.
         * @param message the message used to decline the invitation.
         * @param rejection the raw decline found in the message.
         */

        public void invitationDeclined(EntityBareJid invitee, String reason,
                Message message, MUCUser.Decline rejection)
        {
            // MUCUser mucUser = packet.getExtension(MUCUser.class);
            MUCUser mucUser = MUCUser.from(message);

            // Check if the MUCUser informs that the invitee has declined the invitation
            if ((mucUser != null) && (rejection != null)
                    && (message.getType() != Message.Type.error)) {

                Resourcepart nick;
                try {
                    nick = Resourcepart.from(invitee.getLocalpart().toString());
                } catch (XmppStringprepException e) {
                    nick = Resourcepart.EMPTY;
                }
                ChatRoomMemberJabberImpl member = new ChatRoomMemberJabberImpl(ChatRoomJabberImpl.this, nick, invitee);

//                OperationSetPersistentPresenceJabberImpl presenceOpSet = (OperationSetPersistentPresenceJabberImpl)
//                        mPPS.getOperationSet(OperationSetPersistentPresence.class);
//                if (presenceOpSet != null) {
//                    Contact contact = presenceOpSet.findContactByJid(invitee);
//                    if (contact != null) {
//                        if (invitee.isParentOf(contact.getJid())){
//                            // if (!from.contains(contact.getDisplayName())) {
//                            String from = contact.getDisplayName() + " (" + invitee + ")";
//                        }
//                    }
//                }

                String msgBody = aTalkApp.getResString(R.string.service_gui_INVITATION_REJECTED, invitee, reason);
                ChatRoomMessageReceivedEvent msgReceivedEvt = new ChatRoomMessageReceivedEvent(
                        ChatRoomJabberImpl.this, member, new Date(), createMessage(msgBody), ChatMessage.MESSAGE_SYSTEM);
                fireMessageEvent(msgReceivedEvt);
            }
        }

    }

    /**
     * Updates the presence status of private messaging contact.
     * ChatRoom member always has e.g. conference@conference.atalk.org/swan,
     *
     * @param chatRoomMember the chatRoomMember of the contact.
     */
    public void updatePrivateContactPresenceStatus(ChatRoomMember chatRoomMember)
    {
        OperationSetPersistentPresenceJabberImpl presenceOpSet = (OperationSetPersistentPresenceJabberImpl)
                mPPS.getOperationSet(OperationSetPersistentPresence.class);
        ContactJabberImpl contact
                = (ContactJabberImpl) presenceOpSet.findContactByID(getName() + "/" + chatRoomMember.getNickName());
        updatePrivateContactPresenceStatus(contact);
    }

    /**
     * Updates the presence status of private messaging contact.
     *
     * @param contact the contact.
     */
    public void updatePrivateContactPresenceStatus(Contact contact)
    {
        if (contact == null)
            return;

        OperationSetPersistentPresenceJabberImpl presenceOpSet
                = (OperationSetPersistentPresenceJabberImpl) mPPS.getOperationSet(OperationSetPersistentPresence.class);

        PresenceStatus oldContactStatus = contact.getPresenceStatus();
        Resourcepart nickname;
        try {
            nickname = JidCreate.from(contact.getAddress()).getResourceOrEmpty();
        } catch (XmppStringprepException | IllegalArgumentException e) {
            Timber.e("Invalid contact address: %s", contact.getAddress());
            return;
        }

        boolean isOffline = !members.containsKey(nickname);
        PresenceStatus offlineStatus = mPPS.getJabberStatusEnum().getStatus(
                isOffline ? JabberStatusEnum.OFFLINE : JabberStatusEnum.AVAILABLE);

        // When status changes this may be related to a change in the available resources.
        ((ContactJabberImpl) contact).updatePresenceStatus(offlineStatus);
        presenceOpSet.fireContactPresenceStatusChangeEvent(contact, contact.getJid(), contact.getParentContactGroup(),
                oldContactStatus, offlineStatus);
    }
}
