/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 * 
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package net.java.sip.communicator.impl.protocol.jabber;

import android.text.TextUtils;

import net.java.sip.communicator.impl.protocol.jabber.extensions.*;
import net.java.sip.communicator.service.protocol.*;
import net.java.sip.communicator.service.protocol.Message;
import net.java.sip.communicator.service.protocol.event.*;
import net.java.sip.communicator.service.protocol.jabberconstants.JabberStatusEnum;
import net.java.sip.communicator.util.*;

import org.atalk.android.aTalkApp;
import org.atalk.android.gui.chat.ChatMessage;
import org.atalk.crypto.omemo.OmemoAuthenticateDialog;
import org.atalk.util.StringUtils;
import org.jivesoftware.smack.MessageListener;
import org.jivesoftware.smack.*;
import org.jivesoftware.smack.SmackException.*;
import org.jivesoftware.smack.XMPPException.XMPPErrorException;
import org.jivesoftware.smack.packet.*;
import org.jivesoftware.smack.packet.XMPPError.Condition;
import org.jivesoftware.smackx.address.packet.MultipleAddresses;
import org.jivesoftware.smackx.delay.packet.DelayInformation;
import org.jivesoftware.smackx.disco.ServiceDiscoveryManager;
import org.jivesoftware.smackx.disco.packet.DiscoverInfo;
import org.jivesoftware.smackx.muc.*;
import org.jivesoftware.smackx.muc.packet.*;
import org.jivesoftware.smackx.nick.packet.Nick;
import org.jivesoftware.smackx.omemo.OmemoManager;
import org.jivesoftware.smackx.omemo.exceptions.*;
import org.jivesoftware.smackx.omemo.internal.OmemoDevice;
import org.jivesoftware.smackx.xdata.Form;
import org.jivesoftware.smackx.xdata.packet.DataForm;
import org.jxmpp.jid.*;
import org.jxmpp.jid.impl.JidCreate;
import org.jxmpp.jid.parts.Resourcepart;
import org.jxmpp.stringprep.XmppStringprepException;
import org.jxmpp.util.XmppStringUtils;

import java.beans.PropertyChangeEvent;
import java.security.NoSuchAlgorithmException;
import java.util.*;

/**
 * Implements chat rooms for jabber. The class encapsulates instances of the jive software
 * <tt>MultiUserChat</tt>.
 *
 * @author Emil Ivov
 * @author Yana Stamcheva
 * @author Valentin Martinet
 * @author Boris Grozev
 * @author Hristo Terezov
 * @author Eng Chong Meng
 */

public class ChatRoomJabberImpl extends AbstractChatRoom
{
	/**
	 * The logger of this class.
	 */
	private static final Logger logger = Logger.getLogger(ChatRoomJabberImpl.class);

	/**
	 * The multi user chat smack object that we encapsulate in this room.
	 */
	private MultiUserChat mMultiUserChat = null;

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
	private final Vector<ChatRoomMemberPropertyChangeListener> memberPropChangeListeners
			= new Vector<>();

	/**
	 * The protocol mProvider that created us
	 */
	private final ProtocolProviderServiceJabberImpl mProvider;

	/**
	 * The operation set that created us.
	 */
	private final OperationSetMultiUserChatJabberImpl opSetMuc;

	/**
	 * The list of members of this chat room.
	 */
	private final Hashtable<EntityFullJid, ChatRoomMemberJabberImpl> members = new Hashtable<>();

	/**
	 * The list of banned members of this chat room.
	 */
	private final Hashtable<EntityFullJid, ChatRoomMember> banList = new Hashtable<>();

	/**
	 * The nickname of this chat room local user participant.
	 */
	private String mNickname;

	private Resourcepart mNickResource;

	/**
	 * The subject of this chat room. Keeps track of the subject changes.
	 */
	private String oldSubject;

	/**
	 * The mRole of this chat room local user participant.
	 */
	private ChatRoomMemberRole mRole = null;

	/**
	 * The corresponding configuration form.
	 */
	private ChatRoomConfigurationFormJabberImpl configForm;

	/**
	 * The conference which we have announced in the room in our last sent <tt>Presence</tt>
	 * update.
	 */
	private ConferenceDescription publishedConference = null;

	/**
	 * The <tt>ConferenceAnnouncementPacketExtension</tt> corresponding to
	 * <tt>publishedConference</tt> which we add to all our presence updates. This MUST be
	 * kept in sync with <tt>publishedConference</tt>
	 */
	private ConferenceDescriptionPacketExtension publishedConferenceExt = null;

	/**
	 * The last <tt>Presence</tt> packet we sent to the MUC.
	 */
	private Presence lastPresenceSent = null;

	/**
	 *
	 */
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
	 * Presence listener for joining participants.
	 */
	private ParticipantListener participantListener;

	/**
	 * Creates an instance of a chat room that has been.
	 *
	 * @param multiUserChat
	 * 		MultiUserChat
	 * @param provider
	 * 		a reference to the currently valid jabber protocol mProvider.
	 */
	public ChatRoomJabberImpl(MultiUserChat multiUserChat,
			ProtocolProviderServiceJabberImpl provider)
	{
		mMultiUserChat = multiUserChat;
		mProvider = provider;

		this.opSetMuc = (OperationSetMultiUserChatJabberImpl)
				provider.getOperationSet(OperationSetMultiUserChat.class);
		this.oldSubject = multiUserChat.getSubject();
		multiUserChat.addSubjectUpdatedListener(new MucSubjectUpdatedListener());
		multiUserChat.addParticipantStatusListener(new MemberListener());
		multiUserChat.addUserStatusListener(new UserListener());
		multiUserChat.addMessageListener(new MucMessageListener());

		presenceInterceptor = new PresenceInterceptor();
		multiUserChat.addPresenceInterceptor(presenceInterceptor);
		participantListener = new ParticipantListener();
		multiUserChat.addParticipantListener(participantListener);
		invitationRejectionListeners = new InvitationRejectionListeners();
		multiUserChat.addInvitationRejectionListener(invitationRejectionListeners);
	}

	/**
	 * Adds <tt>listener</tt> to the list of listeners registered to receive events upon
	 * modification of chat room properties such as its subject for example.
	 *
	 * @param listener
	 * 		the <tt>ChatRoomChangeListener</tt> that is to be registered for
	 * 		<tt>ChatRoomChangeEvent</tt>-s.
	 */
	public void addPropertyChangeListener(ChatRoomPropertyChangeListener listener)
	{
		synchronized (propertyChangeListeners) {
			if (!propertyChangeListeners.contains(listener))
				propertyChangeListeners.add(listener);
		}
	}

	/**
	 * Removes <tt>listener</tt> from the list of listeners current registered for chat room
	 * modification events.
	 *
	 * @param listener
	 * 		the <tt>ChatRoomChangeListener</tt> to remove.
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
	 * @param listener
	 * 		the <tt>ChatRoomMemberPropertyChangeListener</tt> that is to be registered for
	 * 		<tt>ChatRoomMemberPropertyChangeEvent</tt>s.
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
	 * @param listener
	 * 		the <tt>ChatRoomMemberPropertyChangeListener</tt> to remove.
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
	 * @param listener
	 * 		a <tt>MessageListener</tt> that would be notified every time a new message is received
	 * 		on this chat room.
	 */
	public void addMessageListener(ChatRoomMessageListener listener)
	{
		synchronized (messageListeners) {
			if (!messageListeners.contains(listener))
				messageListeners.add(listener);
		}
	}

	/**
	 * Removes <tt>listener</tt> so that it won't receive any further message events from this
	 * room.
	 *
	 * @param listener
	 * 		the <tt>MessageListener</tt> to remove from this room
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
	 * @param listener
	 * 		a participant status listener.
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
	 * @param listener
	 * 		a participant status listener.
	 */
	public void removeMemberPresenceListener(ChatRoomMemberPresenceListener listener)
	{
		synchronized (memberListeners) {
			memberListeners.remove(listener);
		}
	}

	/**
	 * Adds a <tt>CallJabberImpl</tt> instance to the list of conference calls associated with the
	 * room.
	 *
	 * @param call
	 * 		the call to add
	 */
	public synchronized void addConferenceCall(CallJabberImpl call)
	{
		if (!chatRoomConferenceCalls.contains(call))
			chatRoomConferenceCalls.add(call);
	}

	/**
	 * Removes a <tt>CallJabberImpl</tt> instance from the list of conference calls associated with
	 * the room.
	 *
	 * @param call
	 * 		the call to remove.
	 */
	public synchronized void removeConferenceCall(CallJabberImpl call)
	{
		if (chatRoomConferenceCalls.contains(call))
			chatRoomConferenceCalls.remove(call);
	}

	/**
	 * Create a Message instance for sending arbitrary MIME-encoding content.
	 *
	 * @param content
	 * 		content value
	 * @param encType
	 * 		the MIME-type for <tt>content</tt>
	 * @param contentEncoding
	 * 		encoding used for <tt>content</tt>
	 * @param subject
	 * 		a <tt>String</tt> subject or <tt>null</tt> for now subject.
	 * @return the newly created message.
	 */
	public Message createMessage(byte[] content, int encType, String contentEncoding,
			String subject)
	{
		return new MessageJabberImpl(new String(content), encType, contentEncoding, subject);
	}

	/**
	 * Create a Message instance for sending a simple text messages with default (text/plain)
	 * content type and encoding.
	 *
	 * @param messageText
	 * 		the string content of the message.
	 * @return Message the newly created message
	 */
	public Message createMessage(String messageText)
	{
		return new MessageJabberImpl(messageText, ChatMessage.ENCODE_PLAIN, null);
	}

	/**
	 * Returns a <tt>List</tt> of <tt>Member</tt>s corresponding to all members currently
	 * participating in this room.
	 *
	 * @return a <tt>List</tt> of <tt>Member</tt> corresponding to all room members.
	 */
	public List<ChatRoomMember> getMembers()
	{
		synchronized (members) {
			return new LinkedList<ChatRoomMember>(members.values());
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
	 * Returns the local user's nickname in the context of this chat room or <tt>null</tt> if not
	 * currently joined.
	 *
	 * @return the nickname currently being used by the local user in the context of the local
	 * chat room.
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
	 * Finds private messaging contact by nickname. If the contact doesn't exists a new volatile
	 * contact is created.
	 *
	 * @param nickname
	 * 		the nickname of the contact.
	 * @return the contact instance.
	 */
	@Override
	public Contact getPrivateContactByNickname(String nickname)
	{
		OperationSetPersistentPresenceJabberImpl opSetPersPresence
				= (OperationSetPersistentPresenceJabberImpl)
				mProvider.getOperationSet(OperationSetPersistentPresence.class);
		String jid = getName() + "/" + nickname;
		Contact sourceContact = opSetPersPresence.findContactByID(jid);
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
	 * Invites another user to this room.
	 *
	 * @param userAddress
	 * 		the address of the user to invite to the room.(one may also invite users not on their
	 * 		contact list).
	 * @param reason
	 * 		a reason, subject, or welcome message that would tell the the user why they are being
	 * 		invited.
	 */
	public void invite(EntityBareJid userAddress, String reason)
			throws NotConnectedException, InterruptedException
	{
		mMultiUserChat.invite(userAddress, reason);
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
	 * Joins this chat room so that the user would start receiving events and messages for it.
	 *
	 * @param password
	 * 		the password to use when authenticating on the chatRoom.
	 * @throws OperationFailedException
	 * 		with the corresponding code if an error occurs while joining the room.
	 */
	public void join(byte[] password)
			throws OperationFailedException
	{
		joinAs(JabberActivator.getGlobalDisplayDetailsService()
				.getDisplayName(getParentProvider()), password);
	}

	/**
	 * Joins this chat room with the nickName of the local user so that the user would start
	 * receiving events and messages for it.
	 *
	 * @throws OperationFailedException
	 * 		with the corresponding code if an error occurs while joining the room.
	 */
	public void join()
			throws OperationFailedException
	{
		joinAs(JabberActivator.getGlobalDisplayDetailsService()
				.getDisplayName(getParentProvider()));
	}

	/**
	 * Joins this chat room with the specified nickName and password so that the user would start
	 * receiving events and messages for it.
	 *
	 * @param nickname
	 * 		the nickname can be jid or just nick.
	 * @param password
	 * 		a password necessary to authenticate when joining the room.
	 * @throws OperationFailedException
	 * 		with the corresponding code if an error occurs while joining the room.
	 */
	public void joinAs(String nickname, byte[] password)
			throws OperationFailedException
	{
		assertConnected();
		// parseLocalPart or take nickname as it
		mNickname = XmppStringUtils.parseLocalpart(nickname);
		if (StringUtils.isNullOrEmpty(mNickname))
			mNickname = nickname;

		String errorMessage = "Failed to join room " + getName() + " with nickname: " + nickname;
		try {
			mNickResource = Resourcepart.from(mNickname);

			if (mMultiUserChat.isJoined()) {
				if (!mMultiUserChat.getNickname().toString().equals(mNickname))
					mMultiUserChat.changeNickname(mNickResource);
			}
			else {
				if (password == null)
					mMultiUserChat.join(mNickResource);
				else
					mMultiUserChat.join(mNickResource, new String(password));
			}

			ChatRoomMemberJabberImpl member = new ChatRoomMemberJabberImpl(this, mNickname,
					mProvider.getAccountID().getAccountJid());
			synchronized (members) {
				final EntityFullJid entityFullJid
						= JidCreate.fullFrom(mMultiUserChat.getRoom(), mNickResource);
				members.put(entityFullJid, member);
			}
			// We don't specify a reason.
			opSetMuc.fireLocalUserPresenceEvent(this,
					LocalUserChatRoomPresenceChangeEvent.LOCAL_USER_JOINED, null);
		}
		catch (XMPPErrorException ex) {
			if (ex.getXMPPError() == null) {
				logger.error(errorMessage, ex);
				throw new OperationFailedException(errorMessage,
						OperationFailedException.GENERAL_ERROR, ex);
			}
			else if (ex.getXMPPError().getCondition().equals(XMPPError.Condition.not_authorized)) {
				errorMessage += ". The chat room requests a password.";
				logger.error(errorMessage, ex);
				throw new OperationFailedException(errorMessage,
						OperationFailedException.AUTHENTICATION_FAILED, ex);
			}
			else if (ex.getXMPPError().getCondition()
					.equals(XMPPError.Condition.registration_required)) {
				errorMessage += ". The chat room requires registration.";
				logger.error(errorMessage, ex);
				throw new OperationFailedException(errorMessage,
						OperationFailedException.REGISTRATION_REQUIRED, ex);
			}
			else {
				logger.error(errorMessage, ex);
				throw new OperationFailedException(errorMessage,
						OperationFailedException.GENERAL_ERROR, ex);
			}
		}
		catch (Throwable ex) {
			logger.error(errorMessage, ex);
			throw new OperationFailedException(errorMessage,
					OperationFailedException.GENERAL_ERROR, ex);
		}
	}

	/**
	 * Joins this chat room with the specified nickname as anonymous so that the user would
	 * start receiving events and messages for it.
	 *
	 * @param nickname
	 * 		the nickname can be jid or just nick.
	 * @throws OperationFailedException
	 * 		with the corresponding code if an error occurs while joining the room.
	 */
	public void joinAs(String nickname)
			throws OperationFailedException
	{
		this.joinAs(nickname, null);
	}

	/**
	 * Returns that <tt>ChatRoomJabberRole</tt> instance corresponding to the <tt>smackRole</tt>
	 * string.
	 *
	 * @param mucRole
	 * 		the smack mRole as returned by <tt>Occupant.getRole()</tt>.
	 * @return ChatRoomMemberRole
	 */
	public static ChatRoomMemberRole smackRoleToScRole(MUCRole mucRole,
			MUCAffiliation affiliation)
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
	 * @param participant
	 * 		the EntityFullJid participant (e.g. sc-testroom@conference.voipgw.fr/userNick)
	 * @return the <tt>ChatRoomMember</tt> corresponding to the given smack participant
	 */
	public ChatRoomMemberJabberImpl findMemberFromParticipant(EntityFullJid participant)
	{
		synchronized (members) {
			return members.get(participant);
		}
	}

	/**
	 * Destroys the chat room.
	 *
	 * @param reason
	 * 		the reason for destroying.
	 * @param roomName
	 * 		the chat Room Name (e.g. sc-testroom@conference.voipgw.fr)
	 * @return <tt>true</tt> if the room is destroyed.
	 */
	public boolean destroy(String reason, EntityBareJid roomName)
	{
		try {
			mMultiUserChat.destroy(reason, roomName);
		}
		catch (XMPPException | NoResponseException | NotConnectedException
				| InterruptedException e) {
			logger.warn("Error occurred while destroying chat room: " + roomName.toString(), e);
			return false;
		}
		return true;
	}

	/**
	 * Leave this chat room.
	 */
	public void leave()
	{
		String reason = "Closing ChatRoom ...";
		this.leave(reason, mMultiUserChat.getRoom());
	}

	/**
	 * Leave this chat room.
	 */
	private void leave(String reason, EntityBareJid roomName)
	{
		OperationSetBasicTelephonyJabberImpl basicTelephony =
				(OperationSetBasicTelephonyJabberImpl) mProvider
						.getOperationSet(OperationSetBasicTelephony.class);
		if (basicTelephony != null && this.publishedConference != null) {
			ActiveCallsRepositoryJabberGTalkImpl<CallJabberImpl, CallPeerJabberImpl>
					activeRepository = basicTelephony.getActiveCallsRepository();

			String callId = publishedConference.getCallId();
			if (callId != null) {
				CallJabberImpl call = activeRepository.findCallId(callId);
				for (CallPeerJabberImpl peer : call.getCallPeerList()) {
					peer.hangup(false, null, null);
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
				peer.hangup(false, null, null);
		}

		clearCachedConferenceDescriptionList();
		XMPPConnection connection = mProvider.getConnection();
		try {
			// if we are already disconnected leave may be called from gui when closing chat window
			if (connection != null)
				mMultiUserChat.leave();
		}
		catch (Throwable e) {
			logger.warn("Error occurred while leaving, maybe just disconnected before leaving", e);
		}

		// cmeng: removed as chatPanel will closed ?
		synchronized (members) {
			for (ChatRoomMember member : members.values()) {
				fireMemberPresenceEvent(member, ChatRoomMemberPresenceChangeEvent.MEMBER_LEFT,
						"Local user has left the chat room.");
			}
			members.clear();
		}

		// connection can be null if we are leaving due to connection failed
		if ((connection != null) && (mMultiUserChat != null)) {
			// mMultiUserChat.removePresenceInterceptor(presenceInterceptor);
			mMultiUserChat.removeParticipantListener(participantListener);
			mMultiUserChat.removeInvitationRejectionListener(invitationRejectionListeners);
		}

		opSetMuc.fireLocalUserPresenceEvent(this,
				LocalUserChatRoomPresenceChangeEvent.LOCAL_USER_LEFT, reason, roomName.toString());
	}

	/**
	 * Sends the <tt>message</tt> to the destination indicated by the <tt>to</tt> contact.
	 *
	 * @param message
	 * 		the <tt>Message</tt> to send.
	 * @throws OperationFailedException
	 * 		if sending the message fails for some reason.
	 */
	public void sendMessage(Message message)
			throws OperationFailedException
	{
		try {
			assertConnected();
			org.jivesoftware.smack.packet.Message msg
					= new org.jivesoftware.smack.packet.Message();
			msg.setBody(message.getContent());

			// XEP-0022 is obsoleted
			// MessageEventManager.addNotificationsRequests(msg, true, false, false, true);
			mMultiUserChat.sendMessage(msg);
		}
		catch (NotConnectedException | InterruptedException e) {
			logger.error("Failed to send message " + message, e);
			throw new OperationFailedException("Failed to send message " + message,
					OperationFailedException.GENERAL_ERROR, e);
		}
	}

	public void sendMessage(Message message, OmemoManager omemoManager)
			throws OperationFailedException
	{
		try {
			assertConnected();
			String msgContent = message.getContent();
			org.jivesoftware.smack.packet.Message encryptedMucMessage
					= new org.jivesoftware.smack.packet.Message();
			try {
				encryptedMucMessage = omemoManager.encrypt(mMultiUserChat, msgContent);
			}
			catch (UndecidedOmemoIdentityException e) {
				// logger.warn("There are unTrusted Omemo devices: " + e.getUndecidedDevices());
				HashSet<OmemoDevice> omemoDevices = e.getUndecidedDevices();
				aTalkApp.getGlobalContext().startActivity(
						OmemoAuthenticateDialog.createIntent(omemoManager, omemoDevices, null));
			}
			catch (CannotEstablishOmemoSessionException e) {
				// encryptedMessage = omemoManager.encryptForExistingSessions(e, msgContent);
				logger.warn("Omemo is unable to create session with a device: " + e.getMessage());
			}
			catch (CryptoFailedException | NoSuchAlgorithmException | InterruptedException
					| NotConnectedException | NoResponseException | XMPPErrorException
					| NoOmemoSupportException e) {
				e.printStackTrace();
			}
			mMultiUserChat.sendMessage(encryptedMucMessage);

			// message delivered for own display
			Message newMessage = createMessage(msgContent);
			Date timeStamp = new Date();
			ChatRoomMessageDeliveredEvent msgDeliveredEvt = new ChatRoomMessageDeliveredEvent(
					ChatRoomJabberImpl.this, timeStamp, newMessage,
					ChatRoomMessageDeliveredEvent.CONVERSATION_MESSAGE_DELIVERED);
			fireMessageEvent(msgDeliveredEvt);
		}
		catch (NotConnectedException | InterruptedException e) {
			logger.error("Failed to send message " + message, e);
			throw new OperationFailedException("Failed to send message " + message,
					OperationFailedException.GENERAL_ERROR, e);
		}
	}

	/**
	 * Sets the subject of this chat room.
	 *
	 * @param subject
	 * 		the new subject that we'd like this room to have
	 * @throws OperationFailedException
	 * 		throws Operation Failed Exception
	 */
	public void setSubject(String subject)
			throws OperationFailedException
	{
		try {
			mMultiUserChat.changeSubject(subject);
		}
		catch (XMPPException | NoResponseException | NotConnectedException
				| InterruptedException ex) {
			String errMsg = "Failed to change subject for chat room" + getName();
			logger.error(errMsg, ex);
			throw new OperationFailedException(errMsg, OperationFailedException.FORBIDDEN, ex);
		}
	}

	/**
	 * Returns a reference to the mProvider that created this room.
	 *
	 * @return a reference to the <tt>ProtocolProviderService</tt> instance that created this room.
	 */
	public ProtocolProviderService getParentProvider()
	{
		return mProvider;
	}

	/**
	 * Returns local user mRole in the context of this chatRoom.
	 *
	 * @return ChatRoomMemberRole
	 */
	public ChatRoomMemberRole getUserRole()
	{
		if (mRole == null) {
			EntityFullJid participant = JidCreate.entityFullFrom(mMultiUserChat.getRoom(),
					mMultiUserChat.getNickname());

			Occupant o = mMultiUserChat.getOccupant(participant);
			if (o == null)
				return ChatRoomMemberRole.GUEST;
			else
				mRole = smackRoleToScRole(o.getRole(), o.getAffiliation());
		}
		return mRole;
	}

	/**
	 * Sets the new mRole for the local user in the context of this chatRoom.
	 *
	 * @param role
	 * 		the new mRole to be set for the local user
	 */
	public void setLocalUserRole(ChatRoomMemberRole role)
	{
		setLocalUserRole(role, false);
	}

	/**
	 * Sets the new mRole for the local user in the context of this chatRoom.
	 *
	 * @param role
	 * 		the new mRole to be set for the local user
	 * @param isInitial
	 * 		if <tt>true</tt> this is initial mRole set.
	 */
	public void setLocalUserRole(ChatRoomMemberRole role, boolean isInitial)
	{
		fireLocalUserRoleEvent(getUserRole(), role, isInitial);
		mRole = role;
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
		 * @param participant
		 * 		the participant that was banned from the room (e.g.
		 * 		room@conference.jabber.org/nick).
		 * @param actor
		 * 		the administrator that banned the occupant (e.g. user@host.org).
		 * @param reason
		 * 		the reason provided by the administrator to ban the occupant.
		 */
		public void banned(EntityFullJid participant, Jid actor, String reason)
		{
			if (logger.isInfoEnabled())
				logger.info(participant.toString() + " has been banned from chat room: "
						+ getName());

			ChatRoomMemberJabberImpl member = members.get(participant);
			if (member != null) {
				banList.put(participant, member);

				synchronized (members) {
					members.remove(participant);
				}
				fireMemberRoleEvent(member, member.getCurrentRole(), ChatRoomMemberRole.OUTCAST);
			}
		}

		/**
		 * Called when an owner grants administrator privileges to a user. This means that the user
		 * will be able to perform administrative functions such as banning users and edit
		 * moderator list.
		 *
		 * @param participant
		 * 		the participant that was granted administrator privileges (e.g.
		 * 		room@conference.jabber.org/nick).
		 */
		public void adminGranted(EntityFullJid participant)
		{
			ChatRoomMemberJabberImpl member = members.get(participant);
			if (member != null)
				fireMemberRoleEvent(member, member.getCurrentRole(),
						ChatRoomMemberRole.ADMINISTRATOR);
		}

		/**
		 * Called when an owner revokes administrator privileges from a user. This means that the
		 * user will no longer be able to perform administrative functions such as banning users
		 * and edit moderator list.
		 *
		 * @param participant
		 * 		the participant that was revoked administrator privileges (e.g.
		 * 		room@conference.jabber.org/nick).
		 */
		public void adminRevoked(EntityFullJid participant)
		{
			ChatRoomMemberJabberImpl member = members.get(participant);
			if (member != null)
				fireMemberRoleEvent(member, member.getCurrentRole(), ChatRoomMemberRole.MEMBER);
		}

		/**
		 * Called when a new room occupant has joined the room. Note: Take in consideration that
		 * when you join a room you will receive the list of current occupants in the room. This
		 * message will be sent for each occupant.
		 *
		 * @param participant
		 * 		the participant that has just joined the room (e.g.
		 * 		room@conference.jabber.org/nick).
		 */
		public void joined(EntityFullJid participant)
		{
			if (logger.isInfoEnabled())
				logger.info(participant + " has joined chatRoom: " + getName());

			// when somebody changes its nickname we first receive event for its nickname changed
			// and after that that has joined we check is this already joined and if so we skip it
			String nickName = participant.getResourcepart().toString();
			if (!mNickname.equals(nickName) && !members.containsKey(participant)) {

				// smack returns fully qualified occupant names.
				Occupant occupant = mMultiUserChat.getOccupant(participant);
				ChatRoomMemberJabberImpl member
						= new ChatRoomMemberJabberImpl(ChatRoomJabberImpl.this,
						occupant.getNick().toString(), occupant.getJid().toString());

				members.put(participant, member);
				// we don't specify a reason
				fireMemberPresenceEvent(member, ChatRoomMemberPresenceChangeEvent.MEMBER_JOINED,
						null);
			}
		}

		/**
		 * Called when a room occupant has left the room on its own. This means that the occupant
		 * was neither kicked nor banned from the room.
		 *
		 * @param participant
		 * 		the participant that has left the room on its own. (e.g.
		 * 		room@conference.jabber.org/nick).
		 */
		public void left(EntityFullJid participant)
		{
			if (logger.isInfoEnabled())
				logger.info(participant.toString() + " has left the chat room: " + getName());

			ChatRoomMember member = members.get(participant);
			if (member != null) {
				synchronized (members) {
					members.remove(participant);
				}
				fireMemberPresenceEvent(member, ChatRoomMemberPresenceChangeEvent.MEMBER_LEFT,
						null);
			}
		}

		/**
		 * Called when a participant changed his/her nickname in the room. The new participant's
		 * nickname will be informed with the next available presence.
		 *
		 * @param participant
		 * 		the participant that has changed his nickname
		 * @param newNickname
		 * 		the new nickname that the participant decided to use.
		 */
		public void nicknameChanged(EntityFullJid participant, Resourcepart newNickname)
		{
			ChatRoomMemberJabberImpl member = members.get(participant);
			if (member == null)
				return;

			String nickName = newNickname.toString();
			if (mNickname.equals(getNickName(member.getNickName())))
				mNickname = nickName;

			member.setNickName(nickName);
			synchronized (members) {
				// change the member key
				members.put(participant, member);
			}

			ChatRoomMemberPropertyChangeEvent evt = new ChatRoomMemberPropertyChangeEvent(member,
					ChatRoomJabberImpl.this, ChatRoomMemberPropertyChangeEvent.MEMBER_NICKNAME,
					participant.getResourcepart(), newNickname);
			fireMemberPropertyChangeEvent(evt);
		}

		/**
		 * Called when an owner revokes a user ownership on the room. This means that the user will
		 * no longer be able to change defining room features as well as perform all administrative
		 * functions.
		 *
		 * @param participant
		 * 		the participant that was revoked ownership on the room (e.g.
		 * 		room@conference.jabber.org/nick).
		 */
		public void ownershipRevoked(EntityFullJid participant)
		{
			ChatRoomMemberJabberImpl member = members.get(participant);
			if (member != null)
				fireMemberRoleEvent(member, member.getCurrentRole(), ChatRoomMemberRole.MEMBER);
		}

		/**
		 * Called when a room participant has been kicked from the room. This means that the kicked
		 * participant is no longer participating in the room.
		 *
		 * @param participant
		 * 		the participant that was kicked from the room (e.g.
		 * 		room@conference.jabber.org/nick).
		 * @param actor
		 * 		the moderator that kicked the occupant from the room (e.g. user@host.org).
		 * @param reason
		 * 		the reason provided by the actor to kick the occupant from the room.
		 */
		public void kicked(EntityFullJid participant, Jid actor, String reason)
		{
			ChatRoomMember member = members.get(participant);
			ChatRoomMember actorMember = members.get(actor.asEntityFullJidIfPossible());

			if (member != null) {
				synchronized (members) {
					members.remove(participant);
				}
				fireMemberPresenceEvent(member, actorMember,
						ChatRoomMemberPresenceChangeEvent.MEMBER_KICKED, reason);
			}
		}

		/**
		 * Called when an administrator grants moderator privileges to a user. This means that the
		 * user will be able to kick users, grant and revoke voice, invite other users, modify
		 * room's subject plus all the participants privileges.
		 *
		 * @param participant
		 * 		the participant that was granted moderator privileges in the room (e.g.
		 * 		room@conference.jabber.org/nick).
		 */
		public void moderatorGranted(EntityFullJid participant)
		{
			ChatRoomMemberJabberImpl member = members.get(participant);
			if (member != null)
				fireMemberRoleEvent(member, member.getCurrentRole(), ChatRoomMemberRole.MODERATOR);
		}

		/**
		 * Called when a moderator revokes voice from a participant. This means that the
		 * participant in the room was able to speak and now is a visitor that can't send
		 * messages to the room occupants.
		 *
		 * @param participant
		 * 		the participant that was revoked voice from the room (e.g.
		 * 		room@conference.jabber.org/nick).
		 */
		public void voiceRevoked(EntityFullJid participant)
		{
			ChatRoomMemberJabberImpl member = members.get(participant);
			if (member != null)
				fireMemberRoleEvent(member, member.getCurrentRole(),
						ChatRoomMemberRole.SILENT_MEMBER);
		}

		/**
		 * Called when an administrator grants a user membership to the room. This means that the
		 * user will be able to join the members-only room.
		 *
		 * @param participant
		 * 		the participant that was granted membership in the room (e.g.
		 * 		room@conference.jabber.org/nick).
		 */
		public void membershipGranted(EntityFullJid participant)
		{
			ChatRoomMemberJabberImpl member = members.get(participant);
			if (member != null)
				fireMemberRoleEvent(member, member.getCurrentRole(), ChatRoomMemberRole.MEMBER);
		}

		/**
		 * Called when an administrator revokes moderator privileges from a user. This means that
		 * the user will no longer be able to kick users, grant and revoke voice, invite other
		 * users, modify room's subject plus all the participants privileges.
		 *
		 * @param participant
		 * 		the participant that was revoked moderator privileges in the room (e.g.
		 * 		room@conference.jabber.org/nick).
		 */
		public void moderatorRevoked(EntityFullJid participant)
		{
			ChatRoomMemberJabberImpl member = members.get(participant);
			if (member != null)
				fireMemberRoleEvent(member, member.getCurrentRole(), ChatRoomMemberRole.MEMBER);
		}

		/**
		 * Called when a moderator grants voice to a visitor. This means that the visitor can now
		 * participate in the moderated room sending messages to all occupants.
		 *
		 * @param participant
		 * 		the participant that was granted voice in the room (e.g.
		 * 		room@conference.jabber.org/nick).
		 */
		public void voiceGranted(EntityFullJid participant)
		{
			ChatRoomMemberJabberImpl member = members.get(participant);
			if (member != null)
				fireMemberRoleEvent(member, member.getCurrentRole(), ChatRoomMemberRole.MEMBER);
		}

		/**
		 * Called when an administrator revokes a user membership to the room. This means that the
		 * user will not be able to join the members-only room.
		 *
		 * @param participant
		 * 		the participant that was revoked membership from the room (e.g.
		 * 		room@conference.jabber.org/nick).
		 */
		public void membershipRevoked(EntityFullJid participant)
		{
			ChatRoomMemberJabberImpl member = members.get(participant);
			if (member != null)
				fireMemberRoleEvent(member, member.getCurrentRole(), ChatRoomMemberRole.GUEST);
		}

		/**
		 * Called when an owner grants a user ownership on the room. This means that the user will
		 * be able to change defining room features as well as perform all administrative
		 * functions.
		 *
		 * @param participant
		 * 		the participant that was granted ownership on the room (e.g.
		 * 		room@conference.jabber.org/nick).
		 */
		public void ownershipGranted(EntityFullJid participant)
		{
			ChatRoomMemberJabberImpl member = members.get(participant);
			if (member != null)
				fireMemberRoleEvent(member, member.getCurrentRole(), ChatRoomMemberRole.OWNER);
		}
	}

	/**
	 * Adds a listener that will be notified of changes in our mRole in the room such as us being
	 * granted operator.
	 *
	 * @param listener
	 * 		a local user mRole listener.
	 */
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
	 * @param listener
	 * 		a local user mRole listener.
	 */
	public void removeLocalUserRoleListener(ChatRoomLocalUserRoleListener listener)
	{
		synchronized (localUserRoleListeners) {
			localUserRoleListeners.remove(listener);
		}
	}

	/**
	 * Adds a listener that will be notified of changes of a member mRole in the room such as being
	 * granted operator.
	 *
	 * @param listener
	 * 		a member mRole listener.
	 */
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
	 * @param listener
	 * 		a member mRole listener.
	 */
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
	 * @throws OperationFailedException
	 * 		if we could not obtain the ban list
	 */
	public Iterator<ChatRoomMember> getBanList()
			throws OperationFailedException
	{
		return banList.values().iterator();
	}

	/**
	 * Changes the local user nickname. If the new nickname already exist in the chat room
	 * throws an OperationFailedException.
	 *
	 * @param nickname
	 * 		the new nickname within the room.
	 * @throws OperationFailedException
	 * 		if the new nickname already exist in this room
	 */
	public void setUserNickname(String nickname)
			throws OperationFailedException
	{
		// parseLocalPart or take nickname as it
   		mNickname = nickname.split("@")[0];

        try {
            mMultiUserChat.changeNickname(Resourcepart.from(mNickname));
        } catch (XMPPException | NoResponseException | NotConnectedException
                | XmppStringprepException
                | MultiUserChatException.MucNotJoinedException | InterruptedException e) {

            String msg = "Failed to change nickname for chat room: " + getName() + " => " + e.getMessage();
            logger.error(msg);

            throw new OperationFailedException(msg, OperationFailedException.IDENTIFICATION_CONFLICT);
        }
	}

	/**
	 * Bans a user from the room. An admin or owner of the room can ban users from a room.
	 *
	 * @param member
	 * 		the <tt>ChatRoomMember</tt> to be banned.
	 * @param reason
	 * 		the reason why the user was banned.
	 * @throws OperationFailedException
	 * 		if an error occurs while banning a user. In particular, an error can occur if a
	 * 		moderator or a user with an affiliation of "owner" or "admin" was tried to be banned
	 * 		or if the user that is banning have not enough permissions to ban.
	 */
	public void banParticipant(ChatRoomMember member, String reason)
			throws OperationFailedException
	{
		try {
			EntityFullJid participant = JidCreate.entityFullFrom(
					((ChatRoomMemberJabberImpl) member).getJabberID());
			mMultiUserChat.banUser(participant, reason);
		}
		catch (XMPPErrorException e) {
			logger.error("Failed to ban participant.", e);

			// If a moderator or a user with an affiliation of "owner" or "admin" was intended
			// to be kicked.
			if (e.getXMPPError().getCondition().equals(XMPPError.Condition.not_allowed)) {
				throw new OperationFailedException(
						"Kicking an admin user or a chat room owner is a forbidden operation.",
						OperationFailedException.FORBIDDEN);
			}
			else {
				throw new OperationFailedException(
						"An error occurred while trying to kick the participant.",
						OperationFailedException.GENERAL_ERROR);
			}
		}
		catch (NoResponseException | NotConnectedException | XmppStringprepException
				| InterruptedException e) {
			throw new OperationFailedException(
					"An error occurred while trying to kick the participant.",
					OperationFailedException.GENERAL_ERROR);
		}
	}

	/**
	 * Kicks a participant from the room.
	 *
	 * @param member
	 * 		the <tt>ChatRoomMember</tt> to kick from the room
	 * @param reason
	 * 		the reason why the participant is being kicked from the room
	 * @throws OperationFailedException
	 * 		if an error occurs while kicking the participant. In particular, an error can occur
	 * 		if a moderator or a user with an affiliation of "owner" or "admin" was intended to be
	 * 		kicked; or if the participant that intended to kick another participant does not have
	 * 		kicking privileges;
	 */
	public void kickParticipant(ChatRoomMember member, String reason)
			throws OperationFailedException
	{
		try {
			Resourcepart nickName = Resourcepart.from(member.getNickName());
			mMultiUserChat.kickParticipant(nickName, reason);
		}
		catch (XMPPErrorException e) {
			logger.error("Failed to kick participant.", e);

			// If a moderator or a user with an affiliation of "owner" or "admin" was intended
			// to be kicked.
			if (e.getXMPPError().getCondition().equals(XMPPError.Condition.not_allowed)) {
				throw new OperationFailedException(
						"Kicking an admin user or a chat room owner is a forbidden " +
								"operation.",
						OperationFailedException.FORBIDDEN);
			}
			// If a participant that intended to kick another participant does not have kicking
			// privileges.
			else if (e.getXMPPError().getCondition().equals(XMPPError.Condition.forbidden)) {
				throw new OperationFailedException(
						"The user that intended to kick another participant does"
								+ " not have enough privileges to do that.",
						OperationFailedException.NOT_ENOUGH_PRIVILEGES);
			}
			else {
				throw new OperationFailedException(
						"An error occurred while trying to kick the participant.",
						OperationFailedException.GENERAL_ERROR);
			}
		}
		catch (NoResponseException | NotConnectedException | InterruptedException
				| XmppStringprepException e) {
			throw new OperationFailedException(
					"An error occurred while trying to kick the participant.",
					OperationFailedException.GENERAL_ERROR);
		}
	}

	/**
	 * Creates the corresponding ChatRoomMemberPresenceChangeEvent and notifies all
	 * <tt>ChatRoomMemberPresenceListener</tt>s that a ChatRoomMember has joined or left this
	 * <tt>ChatRoom</tt>.
	 *
	 * @param member
	 * 		the <tt>ChatRoomMember</tt> that this
	 * @param eventID
	 * 		the identifier of the event
	 * @param eventReason
	 * 		the reason of the event
	 */
	private void fireMemberPresenceEvent(ChatRoomMember member, String eventID, String eventReason)
	{
		ChatRoomMemberPresenceChangeEvent evt = new ChatRoomMemberPresenceChangeEvent(this, member,
				eventID, eventReason);

		if (logger.isTraceEnabled())
			logger.trace("Will dispatch the following ChatRoom event: " + evt);

		Iterator<ChatRoomMemberPresenceListener> listeners;
		synchronized (memberListeners) {
			listeners = new ArrayList<>(memberListeners).iterator();
		}

		while (listeners.hasNext()) {
			ChatRoomMemberPresenceListener listener = listeners.next();
			listener.memberPresenceChanged(evt);
		}
	}

	/**
	 * Creates the corresponding ChatRoomMemberPresenceChangeEvent and notifies all
	 * <tt>ChatRoomMemberPresenceListener</tt>s that a ChatRoomMember has joined or left this
	 * <tt>ChatRoom</tt>.
	 *
	 * @param member
	 * 		the <tt>ChatRoomMember</tt> that changed its presence status
	 * @param actor
	 * 		the <tt>ChatRoomMember</tt> that participated as an actor in this event
	 * @param eventID
	 * 		the identifier of the event
	 * @param eventReason
	 * 		the reason of this event
	 */
	private void fireMemberPresenceEvent(ChatRoomMember member, ChatRoomMember actor,
			String eventID, String eventReason)
	{
		ChatRoomMemberPresenceChangeEvent evt = new ChatRoomMemberPresenceChangeEvent(this, member,
				actor, eventID, eventReason);

		if (logger.isTraceEnabled())
			logger.trace("Will dispatch the following ChatRoom event: " + evt);

		Iterable<ChatRoomMemberPresenceListener> listeners;
		synchronized (memberListeners) {
			listeners = new ArrayList<>(memberListeners);
		}
		for (ChatRoomMemberPresenceListener listener : listeners)
			listener.memberPresenceChanged(evt);
	}

	/**
	 * Creates the corresponding ChatRoomMemberRoleChangeEvent and notifies all
	 * <tt>ChatRoomMemberRoleListener</tt>s that a ChatRoomMember has changed its mRole in this
	 * <tt>ChatRoom</tt>.
	 *
	 * @param member
	 * 		the <tt>ChatRoomMember</tt> that has changed its mRole
	 * @param previousRole
	 * 		the previous mRole that member had
	 * @param newRole
	 * 		the new mRole the member get
	 */
	private void fireMemberRoleEvent(ChatRoomMember member, ChatRoomMemberRole previousRole,
			ChatRoomMemberRole newRole)
	{
		member.setRole(newRole);
		ChatRoomMemberRoleChangeEvent evt = new ChatRoomMemberRoleChangeEvent(this, member,
				previousRole, newRole);

		if (logger.isTraceEnabled())
			logger.trace("Will dispatch the following ChatRoom event: " + evt);

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
	 * @param evt
	 * 		the <tt>EventObject</tt> that we'd like delivered to all registered message listeners.
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
			}
			catch (Throwable e) {
				logger.error("Error delivering multi chat message for " + listener, e);
			}
		}
	}

	/**
	 * Publishes a conference to the room by sending a <tt>Presence</tt> IQ which contains a
	 * <tt>ConferenceDescriptionPacketExtension</tt>
	 *
	 * @param cd
	 * 		the description of the conference to announce
	 * @param name
	 * 		the name of the conference
	 * @return the <tt>ConferenceDescription</tt> that was announced (e.g. <tt>cd</tt> on
	 * success or <tt>null</tt> on failure)
	 */
	public ConferenceDescription publishConference(ConferenceDescription cd, String name)
	{
		if (publishedConference != null) {
			cd = publishedConference;
			cd.setAvailable(false);
		}
		else {
			String displayName;
			if (TextUtils.isEmpty(name)) {
				displayName = JabberActivator.getResources().getI18NString(
						"service.gui.CHAT_CONFERENCE_ITEM_LABEL", new String[]{mNickname});
			}
			else {
				displayName = name;
			}
			cd.setDisplayName(displayName);
		}

		ConferenceDescriptionPacketExtension ext = new ConferenceDescriptionPacketExtension(cd);
		if (lastPresenceSent != null) {
			setPacketExtension(lastPresenceSent, ext,
					ConferenceDescriptionPacketExtension.NAMESPACE);
			try {
				mProvider.getConnection().sendStanza(lastPresenceSent);
			}
			catch (NotConnectedException | InterruptedException e) {
				e.printStackTrace();
			}
		}
		else {
			logger.warn("Could not publish conference, lastPresenceSent is null.");
			publishedConference = null;
			publishedConferenceExt = null;
			return null;
		}
		/*
		 * Save the extensions to set to other outgoing Presence packets
		 */
		publishedConference = !cd.isAvailable() ? null : cd;
		publishedConferenceExt = (publishedConference == null) ? null : ext;

		EntityFullJid participant
				= JidCreate.entityFullFrom(mMultiUserChat.getRoom(), mMultiUserChat.getNickname());

		fireConferencePublishedEvent(members.get(participant), cd,
				ChatRoomConferencePublishedEvent.CONFERENCE_DESCRIPTION_SENT);
		return cd;
	}

	/**
	 * Sets <tt>ext</tt> as the only <tt>ExtensionElement</tt> that belongs to given
	 * <tt>namespace</tt> of the <tt>packet</tt>.
	 *
	 * @param packet
	 * 		the <tt>Packet<tt> to be modified.
	 * @param extension
	 * 		the <tt>ConferenceDescriptionPacketExtension<tt> to set, or <tt>null</tt> to not set
	 * 		one.
	 * @param namespace
	 * 		the namespace of <tt>ExtensionElement</tt>.
	 */
	private static void setPacketExtension(Stanza packet, ExtensionElement extension,
			String namespace)
	{
		if (StringUtils.isNullOrEmpty(namespace)) {
			return;
		}

		// clear previous announcements
		ExtensionElement pe;
		while (null != (pe = packet.getExtension(namespace))) {
			packet.removeExtension(pe);
		}
		if (extension != null) {
			packet.addExtension(extension);
		}
	}

	/**
	 * Publishes new status message in chat room presence.
	 *
	 * @param newStatus
	 * 		the new status message to be published in the MUC.
	 */
	public void publishPresenceStatus(String newStatus)
	{
		if (lastPresenceSent != null) {
			lastPresenceSent.setStatus(newStatus);
			try {
				mProvider.getConnection().sendStanza(lastPresenceSent);
			}
			catch (NotConnectedException | InterruptedException e) {
				e.printStackTrace();
			}
		}
	}

	/**
	 * Adds given <tt>ExtensionElement</tt> to the MUC presence and publishes it immediately.
	 *
	 * @param extension
	 * 		the <tt>ExtensionElement</tt> to be included in MUC presence.
	 */
	public void sendPresenceExtension(ExtensionElement extension)
	{
		if (lastPresenceSent != null) {
			setPacketExtension(lastPresenceSent, extension, extension.getNamespace());
			try {
				mProvider.getConnection().sendStanza(lastPresenceSent);
			}
			catch (NotConnectedException | InterruptedException e) {
				e.printStackTrace();
			}
		}
	}

	/**
	 * Removes given <tt>PacketExtension</tt> from the MUC presence and publishes it immediately.
	 *
	 * @param extension
	 * 		the <tt>PacketExtension</tt> to be removed from the MUC
	 * 		presence.
	 */
	public void removePresenceExtension(ExtensionElement extension)
	{
		if (lastPresenceSent != null) {
			setPacketExtension(lastPresenceSent, null, extension.getNamespace());
			try {
				mProvider.getConnection().sendStanza(lastPresenceSent);
			}
			catch (NotConnectedException | InterruptedException e) {
				e.printStackTrace();
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
		}
		catch (XMPPException | NoResponseException | NotConnectedException
				| InterruptedException e) {
			logger.error("Cannot obtain members list", e);
		}
		return res;
	}

	/**
	 * Changes the list of users that has mRole member for this room. When the room is member only,
	 * this are the users allowed to join.
	 *
	 * @param members
	 * 		the ids of user to have member mRole.
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
		}
		catch (XMPPException | NoResponseException | NotConnectedException
				| InterruptedException e) {
			logger.error("Cannot modify members list", e);
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
		private Date lastSeenDelayedMessage = null;

		/**
		 * The property to store the timestamp.
		 */
		private static final String LAST_SEEN_DELAYED_MESSAGE_PROP = "lastSeenDelayedMessage";

		/**
		 * Process a Message packet.
		 *
		 * @param msg
		 * 		Smack Message to process.
		 */
		@Override
		public void processMessage(org.jivesoftware.smack.packet.Message msg)
		{
			// Do not process Omemo message
			if ((msg == null) || OmemoManager.stanzaContainsOmemoElement(msg))
				return;

			Date timeStamp;
			DelayInformation delayInfo
					= msg.getExtension(DelayInformation.ELEMENT, DelayInformation.NAMESPACE);
			if (delayInfo != null) {
				timeStamp = delayInfo.getStamp();

				// This is a delayed chat room message, a history message for the room coming from
				// server. Lets check have we already shown this message and if this is the case
				// skip it otherwise save it as last seen delayed message
				if (lastSeenDelayedMessage == null) {
					// initialise this from configuration
					String timestamp = ConfigurationUtils.getChatRoomProperty(mProvider,
							getName(), LAST_SEEN_DELAYED_MESSAGE_PROP);

					try {
						lastSeenDelayedMessage = new Date(Long.parseLong(timestamp));
					}
					catch (Throwable ex) {
						ex.printStackTrace();
					}
				}

				if (lastSeenDelayedMessage != null && !timeStamp.after(lastSeenDelayedMessage))
					return;

				// save it in configuration
				ConfigurationUtils.updateChatRoomProperty(mProvider, getName(),
						LAST_SEEN_DELAYED_MESSAGE_PROP, String.valueOf(timeStamp.getTime()));
				lastSeenDelayedMessage = timeStamp;
			}
			else {
				timeStamp = new Date();
			}

			// for delay message only
			Jid jabberID = null;
			MultipleAddresses mAddress
					= msg.getExtension(MultipleAddresses.ELEMENT, MultipleAddresses.NAMESPACE);
			if (mAddress != null) {
				List<MultipleAddresses.Address> addresses
						= mAddress.getAddressesOfType(MultipleAddresses.Type.ofrom);
				jabberID = addresses.get(0).getJid().asBareJid();
			}

			String msgBody = msg.getBody();
			if (msgBody == null)
				return;

			ChatRoomMember member;
			int messageReceivedEventType
					= ChatRoomMessageReceivedEvent.CONVERSATION_MESSAGE_RECEIVED;
			Jid entityJid = msg.getFrom();  // chatRoom entityJid
			Resourcepart fromNick = entityJid.getResourceOrNull();

			// when the message comes from the room itself, it is a system message
			if (entityJid.equals(getName())) {
				messageReceivedEventType = ChatRoomMessageReceivedEvent.SYSTEM_MESSAGE_RECEIVED;
				member = new ChatRoomMemberJabberImpl(ChatRoomJabberImpl.this, getName(),
						getName());
			}
			else {
				member = members.get(entityJid.asEntityFullJidIfPossible());
			}

			// sometimes when connecting to rooms they send history when the member is no longer
			// available we create a fake one so the messages to be displayed.
			if (member == null) {
				member = new ChatRoomMemberJabberImpl(ChatRoomJabberImpl.this, fromNick.toString(),
						jabberID.toString());
			}

			if (logger.isDebugEnabled()) {
				if (logger.isDebugEnabled())
					logger.debug("Received from " + fromNick + " the message " + msg.toXML());
			}

			Message newMessage = createMessage(msgBody);
			if (msg.getType() == org.jivesoftware.smack.packet.Message.Type.error) {
				if (logger.isInfoEnabled())
					logger.info("Message error received from: " + fromNick);

				XMPPError error = msg.getError();
				Condition errorCode = error.getCondition();
				int errorResultCode = ChatRoomMessageDeliveryFailedEvent.UNKNOWN_ERROR;
				String errorReason = error.getConditionText();

				if (errorCode == XMPPError.Condition.service_unavailable) {
					Condition errorCondition = error.getCondition();
					if (Condition.service_unavailable == errorCondition) {
						if (!member.getPresenceStatus().isOnline()) {
							errorResultCode = ChatRoomMessageDeliveryFailedEvent
									.OFFLINE_MESSAGES_NOT_SUPPORTED;
						}
					}
				}
				ChatRoomMessageDeliveryFailedEvent evt
						= new ChatRoomMessageDeliveryFailedEvent(ChatRoomJabberImpl.this,
						member, errorResultCode, errorReason, new Date(), newMessage);
				fireMessageEvent(evt);
				return;
			}

			// Check received message for sent message: either a delivery report or a message
			// coming from the chaRoom server. Checking using nick OR jid in case user join
			// with a different nick.
			if (((getUserNickname() != null) && getUserNickname().equals(fromNick))
					|| ((jabberID != null)
					&& jabberID.equals(getAccountId(member.getChatRoom())))) {
				// message delivered
				ChatRoomMessageDeliveredEvent msgDeliveredEvt = new ChatRoomMessageDeliveredEvent(
						ChatRoomJabberImpl.this, timeStamp, newMessage,
						ChatRoomMessageDeliveredEvent.CONVERSATION_MESSAGE_DELIVERED);

				msgDeliveredEvt.setHistoryMessage(true);
				fireMessageEvent(msgDeliveredEvt);
			}
			else {
				// CONVERSATION_MESSAGE_RECEIVED or SYSTEM_MESSAGE_RECEIVED
				ChatRoomMessageReceivedEvent msgReceivedEvt = new ChatRoomMessageReceivedEvent(
						ChatRoomJabberImpl.this, member, timeStamp, newMessage,
						messageReceivedEventType);

				if (messageReceivedEventType
						== ChatRoomMessageReceivedEvent.CONVERSATION_MESSAGE_RECEIVED
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
		 * @param subject
		 * 		the new subject
		 * @param from
		 * 		the sender from room participants
		 */
		public void subjectUpdated(String subject, EntityFullJid from)
		{
			if (logger.isInfoEnabled())
				logger.info("ChatRoom subject updated to '" + subject + "'");

			// only fire event if subject has really changed, not for new one
			if (subject != null && !subject.equals(oldSubject)) {
				ChatRoomPropertyChangeEvent evt = new ChatRoomPropertyChangeEvent(
						ChatRoomJabberImpl.this, ChatRoomPropertyChangeEvent.CHAT_ROOM_SUBJECT,
						oldSubject, subject);

				firePropertyChangeEvent(evt);
			}

			// Keeps track of the subject.
			oldSubject = subject;
		}
	}

	/**
	 * A listener that is fired anytime your participant's status in a room is changed, such as the
	 * user being kicked, banned, or granted admin permissions.
	 */
	private class UserListener implements UserStatusListener
	{
		/**
		 * Called when a moderator kicked your user from the room. This means that you are no
		 * longer participating in the room.
		 *
		 * @param actor
		 * 		the moderator that kicked your user from the room (e.g. user@host.org).
		 * @param reason
		 * 		the reason provided by the actor to kick you from the room.
		 */
		@Override
		public void kicked(Jid actor, String reason)
		{
			opSetMuc.fireLocalUserPresenceEvent(ChatRoomJabberImpl.this,
					LocalUserChatRoomPresenceChangeEvent.LOCAL_USER_KICKED, reason);
			leave();
		}

		/**
		 * Called when a moderator grants voice to your user. This means that you were a visitor in
		 * the moderated room before and now you can participate in the room by sending messages to
		 * all occupants.
		 */
		@Override
		public void voiceGranted()
		{
			setLocalUserRole(ChatRoomMemberRole.MEMBER);
		}

		/**
		 * Called when a moderator revokes voice from your user. This means that you were a
		 * participant in the room able to speak and now you are a visitor that can't send messages
		 * to the room occupants.
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
		 * @param actor
		 * 		the administrator that banned your user (e.g. user@host.org).
		 * @param reason
		 * 		the reason provided by the administrator to banned you.
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
		 * @param alternateMUC
		 * 		an alternate MultiUserChat, may be null.
		 * @param reason
		 * 		the reason why the room was closed, may be null.
		 */
		@Override
		public void roomDestroyed(MultiUserChat alternateMUC, String reason)
		{

		}
	}

	/**
	 * Creates the corresponding ChatRoomLocalUserRoleChangeEvent and notifies all
	 * <tt>ChatRoomLocalUserRoleListener</tt>s that local user's mRole has been changed in this
	 * <tt>ChatRoom</tt>.
	 *
	 * @param previousRole
	 * 		the previous mRole that local user had
	 * @param newRole
	 * 		the new mRole the local user gets
	 * @param isInitial
	 * 		if <tt>true</tt> this is initial mRole set.
	 */
	private void fireLocalUserRoleEvent(ChatRoomMemberRole previousRole,
			ChatRoomMemberRole newRole, boolean isInitial)
	{
		ChatRoomLocalUserRoleChangeEvent evt
				= new ChatRoomLocalUserRoleChangeEvent(this, previousRole, newRole, isInitial);

		if (logger.isTraceEnabled())
			logger.trace("Will dispatch the following ChatRoom event: " + evt);

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
	 * @param evt
	 * 		the <tt>PropertyChangeEvent</tt> that we'd like delivered to all registered property
	 * 		change listeners.
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
	 * @param evt
	 * 		the <tt>ChatRoomMemberPropertyChangeEvent</tt> that we'd like deliver to all
	 * 		registered member property change listeners.
	 */
	public void fireMemberPropertyChangeEvent(ChatRoomMemberPropertyChangeEvent evt)
	{
		Iterable<ChatRoomMemberPropertyChangeListener> listeners;
		synchronized (memberPropChangeListeners) {
			listeners = new ArrayList<>(
					memberPropChangeListeners);
		}
		for (ChatRoomMemberPropertyChangeListener listener : listeners)
			listener.chatRoomPropertyChanged(evt);
	}

	/**
	 * Utility method throwing an exception if the stack is not properly initialized.
	 *
	 * @throws java.lang.IllegalStateException
	 * 		if the underlying stack is not registered and initialized.
	 */
	private void assertConnected()
			throws IllegalStateException
	{
		if (mProvider == null)
			throw new IllegalStateException("The mProvider must be non-null and signed on the "
					+ "service before being able to communicate.");
		if (!mProvider.isRegistered())
			throw new IllegalStateException("The mProvider must be signed on the service before "
					+ "being able to communicate.");
	}

	/**
	 * Returns the <tt>ChatRoomConfigurationForm</tt> containing all configuration properties for
	 * this chat room. If the user doesn't have permissions to see and change chat room
	 * configuration an <tt>OperationFailedException</tt> is thrown.
	 *
	 * @return the <tt>ChatRoomConfigurationForm</tt> containing all configuration properties for
	 * this chat room
	 * @throws OperationFailedException
	 * 		if the user doesn't have permissions to see and change chat room configuration
	 */
	public ChatRoomConfigurationForm getConfigurationForm()
			throws OperationFailedException, InterruptedException
	{
		Form smackConfigForm;
		try {
			smackConfigForm = mMultiUserChat.getConfigurationForm();
			this.configForm = new ChatRoomConfigurationFormJabberImpl(mMultiUserChat,
					smackConfigForm);
		}
		catch (XMPPErrorException e) {
			if (e.getXMPPError().getCondition().equals(XMPPError.Condition.forbidden))
				throw new OperationFailedException(
						"Failed to obtain smack multi user chat config form."
								+ " User doesn't have enough privileges to see the form.",
						OperationFailedException.NOT_ENOUGH_PRIVILEGES, e);
			else
				throw new OperationFailedException(
						"Failed to obtain smack multi user chat config form.",
						OperationFailedException.GENERAL_ERROR, e);
		}
		catch (NoResponseException | NotConnectedException e) {
			e.printStackTrace();
		}
		return configForm;
	}

	/**
	 * The Jabber multi user chat implementation doesn't support system rooms.
	 *
	 * @return false to indicate that the Jabber protocol implementation doesn't support system
	 * rooms.
	 */
	public boolean isSystem()
	{
		return false;
	}

	/**
	 * Determines whether this chat room should be stored in the configuration file or not. If the
	 * chat room is persistent it still will be shown after a restart in the chat room list. A
	 * non-persistent chat room will be only in the chat room list until the the program is
	 * running.
	 *
	 * @return true if this chat room is persistent, false otherwise
	 */
	public boolean isPersistent()
	{
		boolean persistent = false;
		EntityBareJid roomName = mMultiUserChat.getRoom();
		try {
			// Do not use getRoomInfo, as it has bug and throws NPE
			DiscoverInfo info = ServiceDiscoveryManager.getInstanceFor(
					mProvider.getConnection()).discoverInfo(roomName);

			if (info != null)
				persistent = info.containsFeature("muc_persistent");
		}
		catch (Exception ex) {
			logger.warn("could not get persistent state for room :" + roomName + "\n", ex);
		}
		return persistent;
	}

	/**
	 * Finds the member of this chat room corresponding to the given nick name.
	 *
	 * @param nickName
	 * 		the nick name to search for.
	 * @return the member of this chat room corresponding to the given nick name.
	 */
	public ChatRoomMemberJabberImpl findMemberForNickName(String nickName)
	{
		EntityFullJid participant = null;
		try {
			participant = JidCreate.entityFullFrom(getName() + "/" + nickName);
		}
		catch (XmppStringprepException e) {
			e.printStackTrace();
		}

		synchronized (members) {
			return members.get(participant);
		}
	}

	/**
	 * Grants administrator privileges to another user. Room owners may grant administrator
	 * privileges to a member or un-affiliated user. An administrator is allowed to perform
	 * administrative functions such as banning users and edit moderator list.
	 *
	 * @param jid
	 * 		the bare XMPP user ID of the user to grant administrator privileges (e.g.
	 * 		"user@host.org").
	 */
	public void grantAdmin(Jid jid)
	{
		try {
			mMultiUserChat.grantAdmin(jid);
		}
		catch (XMPPException ex) {
			logger.error("An error occurs granting administrator privileges to a user.", ex);
		}
		catch (NoResponseException | NotConnectedException | InterruptedException e) {
			e.printStackTrace();
		}
	}

	/**
	 * Grants membership to a user. Only administrators are able to grant membership. A user that
	 * becomes a room member will be able to enter a room of type Members-Only (i.e. a room that a
	 * user cannot enter without being on the member list).
	 *
	 * @param jid
	 * 		the bare XMPP user ID of the user to grant membership privileges (e.g.
	 * 		"user@host.org").
	 */
	public void grantMembership(Jid jid)
	{
		try {
			mMultiUserChat.grantMembership(jid);
		}
		catch (XMPPException ex) {
			logger.error("An error occurs granting membership to a user", ex);
		}
		catch (NoResponseException | NotConnectedException | InterruptedException e) {
			e.printStackTrace();
		}
	}

	/**
	 * Grants moderator privileges to a participant or visitor. Room administrators may grant
	 * moderator privileges. A moderator is allowed to kick users, grant and revoke voice, invite
	 * other users, modify room's subject plus all the participants privileges.
	 *
	 * @param nickname
	 * 		the nickname of the occupant to grant moderator privileges.
	 */
	public void grantModerator(String nickname)
	{
		try {
			mMultiUserChat.grantModerator(Resourcepart.from(nickname));
		}
		catch (XMPPException | NoResponseException | NotConnectedException | InterruptedException
				| XmppStringprepException ex) {
			logger.error("An error occurs granting moderator " + "privileges to a user", ex);
		}
	}

	/**
	 * Grants ownership privileges to another user. Room owners may grant ownership privileges.
	 * Some room implementations will not allow to grant ownership privileges to other users. An
	 * owner is allowed to change defining room features as well as perform all administrative
	 * functions.
	 *
	 * @param jid
	 * 		the bare XMPP user ID of the user to grant ownership privileges (e.g.
	 * 		"user@host.org").
	 */
	public void grantOwnership(String jid)
	{
		try {
			mMultiUserChat.grantOwnership(JidCreate.from(jid));
		}
		catch (XMPPException | NoResponseException | NotConnectedException | InterruptedException
				| XmppStringprepException ex) {
			logger.error("An error occurs granting ownership privileges to a user", ex);
		}
	}

	/**
	 * Grants voice to a visitor in the room. In a moderated room, a moderator may want to manage
	 * who does and does not have "voice" in the room. To have voice means that a room occupant is
	 * able to send messages to the room occupants.
	 *
	 * @param nickname
	 * 		the nickname of the visitor to grant voice in the room (e.g. "john").
	 * 		<p/>
	 * 		XMPPException if an error occurs granting voice to a visitor. In particular, a 403
	 * 		error can occur if the occupant that intended to grant voice is not a moderator in
	 * 		this room (i.e. Forbidden error); or a 400 error can occur if the provided nickname is
	 * 		not present in the room.
	 */
	public void grantVoice(String nickname)
	{
		try {
			mMultiUserChat.grantVoice(Resourcepart.from(nickname));
		}
		catch (XMPPException | NoResponseException | NotConnectedException
				| InterruptedException | XmppStringprepException ex) {
			logger.error("An error occurs granting voice to a visitor", ex);
		}
	}

	/**
	 * Revokes administrator privileges from a user. The occupant that loses administrator
	 * privileges will become a member. Room owners may revoke administrator privileges from a
	 * member or unaffiliated user.
	 *
	 * @param jid
	 * 		the bare XMPP user ID of the user to grant administrator privileges (e.g.
	 * 		"user@host.org").
	 */
	public void revokeAdmin(String jid)
	{
		try {
			mMultiUserChat.revokeAdmin((EntityJid) JidCreate.from(jid));
		}
		catch (XMPPException | NoResponseException | NotConnectedException
				| XmppStringprepException | InterruptedException ex) {
			logger.error("n error occurs revoking administrator " + "privileges to a user", ex);
		}
	}

	/**
	 * Revokes a user's membership. Only administrators are able to revoke membership. A user that
	 * becomes a room member will be able to enter a room of type Members-Only (i.e. a room that a
	 * user cannot enter without being on the member list). If the user is in the room and the room
	 * is of type members-only then the user will be removed from the room.
	 *
	 * @param jid
	 * 		the bare XMPP user ID of the user to revoke membership (e.g. "user@host.org").
	 */
	public void revokeMembership(String jid)
	{
		try {
			mMultiUserChat.revokeMembership(JidCreate.from(jid));
		}
		catch (XMPPException | NoResponseException | NotConnectedException
				| InterruptedException | XmppStringprepException ex) {
			logger.error("An error occurs revoking membership to a user", ex);
		}
	}

	/**
	 * Revokes moderator privileges from another user. The occupant that loses moderator privileges
	 * will become a participant. Room administrators may revoke moderator privileges only to
	 * occupants whose affiliation is member or none. This means that an administrator is not
	 * allowed to revoke moderator privileges from other room administrators or owners.
	 *
	 * @param nickname
	 * 		the nickname of the occupant to revoke moderator privileges.
	 */
	public void revokeModerator(String nickname)
	{
		try {
			mMultiUserChat.revokeModerator(Resourcepart.from(nickname));
		}
		catch (XMPPException | NoResponseException | NotConnectedException
				| InterruptedException | XmppStringprepException ex) {
			logger.error("n error occurs revoking moderator privileges from a user", ex);
		}
	}

	/**
	 * Revokes ownership privileges from another user. The occupant that loses ownership privileges
	 * will become an administrator. Room owners may revoke ownership privileges. Some room
	 * implementations will not allow to grant ownership privileges to other users.
	 *
	 * @param jid
	 * 		the bare XMPP user ID of the user to revoke ownership (e.g. "user@host.org").
	 */
	public void revokeOwnership(String jid)
	{
		try {
			mMultiUserChat.revokeOwnership(JidCreate.from(jid));
		}
		catch (XMPPException | NoResponseException | NotConnectedException
				| InterruptedException | XmppStringprepException ex) {
			logger.error("An error occurs revoking ownership privileges from a user", ex);
		}
	}

	/**
	 * Revokes voice from a participant in the room. In a moderated room, a moderator may want to
	 * revoke an occupant's privileges to speak. To have voice means that a room occupant is
	 * able to send messages to the room occupants.
	 *
	 * @param nickname
	 * 		the nickname of the participant to revoke voice (e.g. "john").
	 * 		<p/>
	 * 		XMPPException if an error occurs revoking voice from a participant. In particular, a
	 * 		405 error can occur if a moderator or a user with an affiliation of "owner" or "admin"
	 * 		was tried to revoke his voice (i.e. Not Allowed error); or a 400 error can occur if
	 * 		the provided nickname is not present in the room.
	 */
	public void revokeVoice(String nickname)
	{
		try {
			mMultiUserChat.revokeVoice(Resourcepart.from(nickname));
		}
		catch (XMPPException | NoResponseException | NotConnectedException
				| InterruptedException | XmppStringprepException ex) {
			logger.info("An error occurs revoking voice from a participant", ex);
		}
	}

	/**
	 * Returns the nickname of the given participant name. For example, for the address
	 * "john@xmppservice.com", "john" would be returned. If no @ is found in the address we return
	 * the given name.
	 *
	 * @param participantAddress
	 * 		the address of the participant
	 * @return the nickname part of the given participant address
	 */
	private static String getNickName(String participantAddress)
	{
		if (participantAddress == null)
			return null;

		int atIndex = participantAddress.lastIndexOf("@");
		if (atIndex == -1)
			return participantAddress;
		else
			return participantAddress.substring(0, atIndex);
	}

	/**
	 * Returns the internal stack used chat room instance.
	 *
	 * @return the chat room used in the protocol stack.
	 */
	MultiUserChat getMultiUserChat()
	{
		return mMultiUserChat;
	}

	/**
	 * The <tt>PacketInterceptor</tt> we use to make sure that our outgoing <tt>Presence</tt>
	 * packets contain the correct <tt>ConferenceAnnouncementPacketExtension</tt>.
	 */
	private class PresenceInterceptor implements PresenceListener
	{
		/**
		 * {@inheritDoc}
		 * <p/>
		 * Adds <tt>this.publishedConferenceExt</tt> as the only
		 * <tt>ConferenceAnnouncementPacketExtension</tt> of <tt>packet</tt>.
		 */
		@Override
		public void processPresence(Presence presence)
		{
			if (presence != null) {
				setPacketExtension(presence, publishedConferenceExt,
						ConferenceDescriptionPacketExtension.NAMESPACE);
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
		 * @param presence
		 * 		the presence packet.
		 */
		public void processPresence(Presence presence)
		{
			String myRoomJID = mMultiUserChat.getRoom() + "/" + mNickResource;
			if (presence.getFrom().equals(myRoomJID))
				processOwnPresence(presence);
			else
				processOtherPresence(presence);
		}

		/**
		 * Processes a <tt>Presence</tt> packet addressed to our own occupant JID.
		 *
		 * @param presence
		 * 		the packet to process.
		 */
		private void processOwnPresence(Presence presence)
		{
			MUCUser mucUser = presence.getExtension(MUCUser.ELEMENT, MUCUser.NAMESPACE);
			if (mucUser != null) {
				MUCAffiliation affiliation = mucUser.getItem().getAffiliation();
				MUCRole role = mucUser.getItem().getRole();

				// if status 201 is available means that room is created and locked till we send
				// the configuration
				if ((mucUser.getStatus() != null)
						&& mucUser.getStatus().contains(MUCUser.Status.ROOM_CREATED_201)) {
					try {
						mMultiUserChat.sendConfigurationForm(new Form(DataForm.Type.submit));
					}
					catch (XMPPException | NoResponseException | NotConnectedException
							| InterruptedException e) {
						logger.error("Failed to send config form.", e);
					}

					opSetMuc.addSmackInvitationRejectionListener(mMultiUserChat,
							ChatRoomJabberImpl.this);
					if (affiliation == MUCAffiliation.owner) {
						setLocalUserRole(ChatRoomMemberRole.OWNER, true);
					}
					else
						setLocalUserRole(ChatRoomMemberRole.MODERATOR, true);
				}
				else {
					// this is the presence for our member initial mRole and affiliation, as
					// smack do not fire any initial events lets check it and fire events
					ChatRoomMemberRole jitsiRole
							= ChatRoomJabberImpl.smackRoleToScRole(role, affiliation);
					if (jitsiRole == ChatRoomMemberRole.MODERATOR
							|| jitsiRole == ChatRoomMemberRole.OWNER
							|| jitsiRole == ChatRoomMemberRole.ADMINISTRATOR) {
						setLocalUserRole(jitsiRole, true);
					}
					if (!presence.isAvailable()
							&& (affiliation.toString().equals("none")
							&& role.toString().equals("none"))) {
						Destroy destroy = mucUser.getDestroy();
						if (destroy == null) {
							// the room is unavailable to us, there is no message we will just
							// leave
							leave();
						}
						else {
							leave(destroy.getReason(), destroy.getJid());
						}
					}
				}
			}
		}

		/**
		 * Process a <tt>Presence</tt> packet sent by one of the other room occupants.
		 */
		private void processOtherPresence(Presence presence)
		{
			Jid from = presence.getFrom();
			ChatRoomMemberJabberImpl member = members.get(from.asEntityFullJidIfPossible());

			ExtensionElement ext
					= presence.getExtension(ConferenceDescriptionPacketExtension.NAMESPACE);
			if (presence.isAvailable() && ext != null) {
				ConferenceDescriptionPacketExtension cdExt
						= (ConferenceDescriptionPacketExtension) ext;
				ConferenceDescription cd = cdExt.toConferenceDescription();

				if (!processConferenceDescription(cd, from.toString()))
					return;

				if (member != null) {
					if (logger.isDebugEnabled())
						logger.debug("Received " + cd + " from " + from.toString() + " in "
								+ mMultiUserChat.getRoom());
					fireConferencePublishedEvent(member, cd,
							ChatRoomConferencePublishedEvent.CONFERENCE_DESCRIPTION_RECEIVED);
				}
				else {
					logger.warn("Received a ConferenceDescription from an unknown member ("
							+ from.toString() + ") in " + mMultiUserChat.getRoom());
				}
			}

			Nick nickExtension
					= (Nick) presence.getExtension(Nick.ELEMENT_NAME, Nick.NAMESPACE);
			if (member != null && nickExtension != null) {
				member.setDisplayName(nickExtension.getName());
			}

			Email emailExtension
					= (Email) presence.getExtension(Email.ELEMENT_NAME, Email.NAMESPACE);
			if (member != null && emailExtension != null) {
				member.setEmail(emailExtension.getAddress());
			}

			AvatarUrl avatarUrl = (AvatarUrl) presence.getExtension(
					AvatarUrl.ELEMENT_NAME,
					AvatarUrl.NAMESPACE);
			if (member != null && avatarUrl != null) {
				member.setAvatarUrl(avatarUrl.getAvatarUrl());
			}
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
		 * @param invitee
		 * 		the invitee that declined the invitation. (e.g. hecate@shakespeare.lit).
		 * @param reason
		 * 		the reason why the invitee declined the invitation.
		 * @param message
		 * 		the message used to decline the invitation.
		 * @param rejection
		 * 		the raw decline found in the message.
		 */

		public void invitationDeclined(EntityBareJid invitee, String reason,
				org.jivesoftware.smack.packet.Message message, MUCUser.Decline rejection)
		{
			// MUCUser mucUser = packet.getExtension(MUCUser.ELEMENT, MUCUser.NAMESPACE);
			MUCUser mucUser = MUCUser.from(message);

			// Check if the MUCUser informs that the invitee has declined the invitation
			if ((mucUser != null) && (rejection != null)
					&& (message.getType() != org.jivesoftware.smack.packet.Message.Type.error)) {
				int messageReceivedEventType = ChatRoomMessageReceivedEvent
						.SYSTEM_MESSAGE_RECEIVED;
				ChatRoomMemberJabberImpl member
						= new ChatRoomMemberJabberImpl(ChatRoomJabberImpl.this, getName(),
						getName());
				EntityBareJid from = rejection.getFrom();
				String fromStr = from.toString();

				OperationSetPersistentPresenceJabberImpl presenceOpSet
						= (OperationSetPersistentPresenceJabberImpl) mProvider
						.getOperationSet(OperationSetPersistentPresence.class);
				if (presenceOpSet != null) {
					Contact c = presenceOpSet.findContactByID(from.toString());
					if (c != null) {
						if (!fromStr.contains(c.getDisplayName())) {
							fromStr = c.getDisplayName() + " (" + from + ")";
						}
					}
				}

				String msgBody = JabberActivator.getResources().getI18NString(
						"service.gui.INVITATION_REJECTED",
						new String[]{fromStr, mucUser.getDecline().getReason()});

				ChatRoomMessageReceivedEvent msgReceivedEvt = new ChatRoomMessageReceivedEvent(
						ChatRoomJabberImpl.this, member, new Date(), createMessage(msgBody),
						messageReceivedEventType);
				fireMessageEvent(msgReceivedEvt);
			}
		}
	}

	/**
	 * Updates the presence status of private messaging contact.
	 *
	 * @param nickname
	 * 		the nickname of the contact.
	 */
	public void updatePrivateContactPresenceStatus(String nickname)
	{
		OperationSetPersistentPresenceJabberImpl presenceOpSet
				= (OperationSetPersistentPresenceJabberImpl) mProvider
				.getOperationSet(OperationSetPersistentPresence.class);
		ContactJabberImpl sourceContact = (ContactJabberImpl) presenceOpSet
				.findContactByID(getName() + "/" + nickname);

		updatePrivateContactPresenceStatus(sourceContact);
	}

	/**
	 * Updates the presence status of private messaging contact.
	 *
	 * @param contact
	 * 		the contact.
	 */
	public void updatePrivateContactPresenceStatus(Contact contact)
	{
		OperationSetPersistentPresenceJabberImpl presenceOpSet
				= (OperationSetPersistentPresenceJabberImpl) mProvider
				.getOperationSet(OperationSetPersistentPresence.class);

		if (contact == null)
			return;

		PresenceStatus oldContactStatus = contact.getPresenceStatus();
		EntityFullJid participant = null;
		try {
			participant = JidCreate.entityFullFrom(contact.getAddress());
		}
		catch (XmppStringprepException e) {
			e.printStackTrace();
		}
		boolean isOffline = !members.containsKey(participant);

		PresenceStatus offlineStatus = mProvider.getJabberStatusEnum().getStatus(
				isOffline ? JabberStatusEnum.OFFLINE : JabberStatusEnum.AVAILABLE);

		// When status changes this may be related to a change in the available resources.
		((ContactJabberImpl) contact).updatePresenceStatus(offlineStatus);
		presenceOpSet.fireContactPresenceStatusChangeEvent(contact,
				contact.getParentContactGroup(), oldContactStatus, offlineStatus);
	}
}
