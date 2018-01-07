/**
 * aTalk (jitsi-android fork), the OpenSource Java VoIP and Instant Messaging client.
 * <p>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package net.java.sip.communicator.impl.protocol.jabber;

import net.java.sip.communicator.service.protocol.*;
import net.java.sip.communicator.service.protocol.event.*;
import net.java.sip.communicator.service.protocol.jabberconstants.JabberStatusEnum;
import net.java.sip.communicator.util.Logger;

import org.atalk.android.gui.chat.MetaContactChatTransport;
import org.jivesoftware.smack.SmackException.NotConnectedException;
import org.jivesoftware.smack.StanzaListener;
import org.jivesoftware.smack.filter.StanzaTypeFilter;
import org.jivesoftware.smack.packet.Message;
import org.jivesoftware.smack.packet.*;
import org.jivesoftware.smack.tcp.XMPPTCPConnection;
import org.jivesoftware.smackx.chatstates.*;
import org.jivesoftware.smackx.chatstates.packet.ChatStateExtension;
import org.jivesoftware.smackx.xevent.*;
import org.jxmpp.jid.Jid;

import java.util.List;

import static org.jxmpp.util.XmppStringUtils.parseBareJid;

/**
 * OperationSet that handle chat state notifications
 *
 * @author Damian Minkov
 * @author Emil Ivov
 * @author Hristo Terezov
 * @author Eng Chong Meng
 */
public class OperationSetChatStateNotificationsJabberImpl extends
		AbstractOperationSetChatStateNotifications<ProtocolProviderServiceJabberImpl>
{
	/**
	 * The logger.
	 */
	private static final Logger logger
			= Logger.getLogger(OperationSetChatStateNotificationsJabberImpl.class);

	/**
	 * An active instance of the opSetPeersPresence operation set. We're using it to map incoming
	 * events to contacts in our contact list.
	 */
	private OperationSetPersistentPresenceJabberImpl opSetPeersPresence = null;

	/**
	 * An active instance of the opSetBasicIM operation set.
	 */
	private OperationSetBasicInstantMessagingJabberImpl opSetBasicIM = null;

	/**
	 * An active instance of the ChatStateManager.
	 */
	private ChatStateManager chatStateManager = null;

	/**
	 * XMPPTCPConnection connection for chat session.
	 */
	XMPPTCPConnection mConnection;

	/**
	 * We use this listener to cease the moment when the protocol provider has been successfully
	 * registered.
	 */
	private ProviderRegListener providerRegListener = new ProviderRegListener();

	/**
	 * The manger which send us the chat state info and through which we send inf
	 */
	private MessageEventManager messageEventManager = null;

	/**
	 * The listener instance that we use to track chat states according to XEP-0085;
	 */
	private SmackChatStateListener smackChatStateListener = null;

	/**
	 * @param provider
	 * 		a ref to the <tt>ProtocolProviderServiceImpl</tt> that created us and that we'll use
	 * 		for retrieving the underlying aim connection.
	 */
	OperationSetChatStateNotificationsJabberImpl(ProtocolProviderServiceJabberImpl provider)
	{
		super(provider);
		provider.addRegistrationStateChangeListener(providerRegListener);
	}

	/**
	 * Sends a chat state notification to <tt>contact</tt> that chat state we are in
	 *
	 * @param contact
	 * 		the <tt>Contact</tt> to notify
	 * @param chatState
	 * 		the chat state that we have entered.
	 * @throws java.lang.IllegalStateException
	 * 		if the underlying stack is not registered and initialized.
	 * @throws java.lang.IllegalArgumentException
	 * 		if <tt>notifiedContact</tt> is not an instance belonging to the underlying
	 * 		implementation.
	 */
	public void sendChatStateNotification(Contact contact, ChatState chatState)
			throws IllegalStateException, IllegalArgumentException, NotConnectedException,
			InterruptedException
	{
		assertConnected();
		if (!(contact instanceof ContactJabberImpl))
			throw new IllegalArgumentException("The specified contact is not a Jabber contact."
					+ contact);

		// now handle XEP-0085 chat state sending
//		Chat chat = opSetBasicIM.getChat((EntityJid) contact.getJid());
//		if (opSetBasicIM != null && mConnection != null && chat != null) {
//			logger.info("Sending Chat State for : " + chatState.toString());
//			chatStateManager.setCurrentState(chatState, chat);
//		}

		if (opSetBasicIM != null && mConnection != null) {
			Jid toJid = opSetBasicIM.getRecentFullJidForContactIfPossible(contact);

			/*
			 * find the currently contacted jid to send chat state info or if we do not have a
			 * jid and we have already sent message to the bare jid we will also send chat state
			 * info there
			 */

			// if we haven't sent a message yet, do not send chat state notifications
			if (toJid == null)
				return;

			if (logger.isTraceEnabled())
				logger.trace("Sending XEP-0085 chat state=" + chatState + " to " + toJid);

			setCurrentState(chatState, toJid);
		}
	}

	/**
	 * Creates and sends a packet for the new chat state.
	 *
	 * @param chatState
	 * 		the new chat state.
	 * @param jid
	 * 		the JID of the receiver.
	 */
	private void setCurrentState(ChatState chatState, Jid jid)
			throws NotConnectedException, InterruptedException
	{
		String threadID = opSetBasicIM.getThreadIDForAddress(jid, true);

		Message message = new Message();
		ChatStateExtension extension = new ChatStateExtension(chatState);
		message.addExtension(extension);

		message.setTo(jid);
		message.setType(Message.Type.chat);
		message.setThread(threadID);
		message.setFrom(mConnection.getUser());
		mConnection.sendStanza(message);

		if (chatState == ChatState.gone)
			opSetBasicIM.purgeGoneJidThreads(jid);
	}

	/**
	 * Utility method throwing an exception if the stack is not properly initialized.
	 *
	 * @throws java.lang.IllegalStateException
	 * 		if the underlying stack is not registered and initialized.
	 */
	@Override
	protected void assertConnected()
			throws IllegalStateException
	{
		if (parentProvider != null && !parentProvider.isRegistered()
				&& opSetPeersPresence.getPresenceStatus().isOnline()) {
			// if we are not registered but the current status is online
			// change the current status
			opSetPeersPresence.fireProviderStatusChangeEvent(opSetPeersPresence.getPresenceStatus(),
					parentProvider.getJabberStatusEnum().getStatus(JabberStatusEnum.OFFLINE));
		}
		super.assertConnected();
	}

	/**
	 * Our listener that will tell us when we're registered and ready to accept us as a listener.
	 */
	private class ProviderRegListener implements RegistrationStateChangeListener
	{
		/**
		 * The method is called by a ProtocolProvider implementation whenever a change in the
		 * registration state of the corresponding provider had occurred.
		 *
		 * @param evt
		 * 		ProviderStatusChangeEvent the event describing the status change.
		 */
		public void registrationStateChanged(RegistrationStateChangeEvent evt)
		{
			if (evt.getNewState() == RegistrationState.REGISTERED) {
				mConnection = parentProvider.getConnection();
				// chatStateManager = ChatStateManager.getInstance(mConnection);

				opSetPeersPresence = (OperationSetPersistentPresenceJabberImpl) parentProvider
						.getOperationSet(OperationSetPersistentPresence.class);
				opSetBasicIM = (OperationSetBasicInstantMessagingJabberImpl) parentProvider
						.getOperationSet(OperationSetBasicInstantMessaging.class);

				messageEventManager
						= MessageEventManager.getInstanceFor(parentProvider.getConnection());
				messageEventManager
						.addMessageEventRequestListener(new JabberMessageEventRequestListener());
				messageEventManager
						.addMessageEventNotificationListener(new IncomingMessageEventsListener());

				if (smackChatStateListener == null)
					smackChatStateListener = new SmackChatStateListener();

				parentProvider.getConnection().addAsyncStanzaListener(smackChatStateListener,
						new StanzaTypeFilter(Message.class));

			}
			else if (evt.getNewState() == RegistrationState.UNREGISTERED
					|| evt.getNewState() == RegistrationState.AUTHENTICATION_FAILED
					|| evt.getNewState() == RegistrationState.CONNECTION_FAILED) {
				if (parentProvider.getConnection() != null) {
					parentProvider.getConnection()
							.removeAsyncStanzaListener(smackChatStateListener);
				}
				smackChatStateListener = null;
				if (messageEventManager != null) {
					messageEventManager = null;
				}
			}
		}
	}

	/**
	 * Listens for incoming request for chat state info
	 */
	private class JabberMessageEventRequestListener implements MessageEventRequestListener
	{
		public void deliveredNotificationRequested(Jid from, String packetID,
				MessageEventManager messageEventManager)
		{
			try {
				messageEventManager.sendDeliveredNotification(from, packetID);
			}
			catch (NotConnectedException | InterruptedException e) {
				e.printStackTrace();
			}
		}

		public void displayedNotificationRequested(Jid from, String packetID,
				MessageEventManager messageEventManager)
		{
			try {
				messageEventManager.sendDisplayedNotification(from, packetID);
			}
			catch (NotConnectedException | InterruptedException e) {
				e.printStackTrace();
			}
		}

		public void composingNotificationRequested(Jid from, String packetID,
				MessageEventManager messageEventManager)
		{
			try {
				messageEventManager.sendComposingNotification(from, packetID);
			}
			catch (NotConnectedException | InterruptedException e) {
				e.printStackTrace();
			}
		}

		public void offlineNotificationRequested(Jid from, String packetID,
				MessageEventManager messageEventManager)
		{
			try {
				messageEventManager.sendCancelledNotification(from, packetID);
			}
			catch (NotConnectedException | InterruptedException e) {
				e.printStackTrace();
			}
		}
	}

	/**
	 * Receives incoming typing info
	 */
	private class IncomingMessageEventsListener implements MessageEventNotificationListener
	{
		public void deliveredNotification(Jid from, String packetID)
		{
		}

		public void displayedNotification(Jid from, String packetID)
		{
		}

		public void composingNotification(Jid from, String packetID)
		{
			String fromID = from.asBareJid().toString();

			Contact sourceContact = opSetPeersPresence.findContactByID(fromID);

			if (sourceContact == null) {
				// create the volatile contact
				sourceContact = opSetPeersPresence.createVolatileContact(from.toString());
			}
			fireChatStateNotificationsEvent(sourceContact, ChatState.composing, null);
		}

		public void offlineNotification(Jid from, String packetID)
		{
		}

		public void cancelledNotification(Jid from, String packetID)
		{
			String fromID = from.asBareJid().toString();
			Contact sourceContact = opSetPeersPresence.findContactByID(fromID);

			if (sourceContact == null) {
				// create the volatile contact
				sourceContact = opSetPeersPresence.createVolatileContact(from.toString());
			}
			fireChatStateNotificationsEvent(sourceContact, ChatState.inactive, null);
		}
	}

	/**
	 * The listener that we use to track chat state notifications according to XEP-0085.
	 */
	private class SmackChatStateListener implements StanzaListener
	{
		/**
		 * Called by smack when the state of a chat changes.
		 *
		 * @param chatState
		 * 		the new state of the chat.
		 * @param message
		 * 		the message containing the new chat state
		 */
		public void stateChanged(ChatState chatState, Message message)
		{
			String fromJID = message.getFrom().toString();
			if (logger.isTraceEnabled())
				logger.trace(fromJID + " entered the " + chatState.name() + " state.");

			String fromID = parseBareJid(fromJID);
			boolean isPrivateMessagingAddress = false;
			OperationSetMultiUserChat mucOpSet
					= parentProvider.getOperationSet(OperationSetMultiUserChat.class);
			if (mucOpSet != null) {
				List<ChatRoom> chatRooms = mucOpSet.getCurrentlyJoinedChatRooms();
				for (ChatRoom chatRoom : chatRooms) {
					if (chatRoom.getName().equals(fromID)) {
						isPrivateMessagingAddress = true;
						break;
					}
				}
			}

			Contact sourceContact = opSetPeersPresence.findContactByID((isPrivateMessagingAddress
					? fromJID : fromID));
			if (sourceContact == null) {
				// in private messaging we can receive some errors when we left room and we try
				// to send some message (isPrivateMessagingAddress == false)
				if (message.getError() != null)
					sourceContact = opSetPeersPresence.findContactByID(fromJID);

				if (sourceContact == null) {
					// create the volatile contact
					sourceContact = opSetPeersPresence.createVolatileContact(fromJID,
							isPrivateMessagingAddress);
				}
			}
			if (message.getError() != null)
				fireChatStateNotificationsDeliveryFailedEvent(sourceContact, chatState);
			else {
				// Invalid the last thread associated with the contact when he is gone
				if (chatState == ChatState.gone)
					opSetBasicIM.purgeGoneJidThreads(message.getFrom());
				else if (chatState == ChatState.active)
					MetaContactChatTransport.setChatStateSupport(true);

				fireChatStateNotificationsEvent(sourceContact, chatState, message);
			}
		}

		@Override
		public void processStanza(Stanza packet)
		{
			Message msg = (Message) packet;
			ChatStateExtension ext
					= (ChatStateExtension) msg.getExtension(ChatStateManager.NAMESPACE);
			if (ext != null) {
				stateChanged(ext.getChatState(), msg);
			}
			else {
				MetaContactChatTransport.setChatStateSupport(false);
			}
		}
	}
}
