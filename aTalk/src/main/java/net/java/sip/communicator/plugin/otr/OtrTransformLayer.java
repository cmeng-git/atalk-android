/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license.
 * See terms of license at gnu.org.
 */
package net.java.sip.communicator.plugin.otr;

import net.java.otr4j.io.SerializationConstants;
import net.java.sip.communicator.impl.protocol.jabber.MessageJabberImpl;
import net.java.sip.communicator.plugin.otr.OtrContactManager.OtrContact;
import net.java.sip.communicator.service.protocol.Contact;
import net.java.sip.communicator.service.protocol.ContactResource;
import net.java.sip.communicator.service.protocol.IMessage;
import net.java.sip.communicator.service.protocol.OperationSetBasicInstantMessaging;
import net.java.sip.communicator.service.protocol.TransformLayer;
import net.java.sip.communicator.service.protocol.event.MessageDeliveredEvent;
import net.java.sip.communicator.service.protocol.event.MessageDeliveryFailedEvent;
import net.java.sip.communicator.service.protocol.event.MessageReceivedEvent;

import org.jxmpp.jid.Jid;

import java.util.Date;

/**
 * The Off-the-Record {@link TransformLayer} implementation.
 *
 * @author George Politis
 */
public class OtrTransformLayer implements TransformLayer {
	/*
	 * Implements TransformLayer#messageDelivered(MessageDeliveredEvent).
	 */
	public MessageDeliveredEvent messageDelivered(MessageDeliveredEvent evt) {
		if (OtrActivator.scOtrEngine.isMessageUIDInjected(evt.getSourceMessage().getMessageUID()))
			// If this is a message otr4j injected earlier, don't display it,
			// this may have to change when we add support for fragmentation..
			return null;
		else
			return evt;
	}

	/*
	 * Implements
	 * TransformLayer#messageDeliveryFailed(MessageDeliveryFailedEvent).
	 */
	public MessageDeliveryFailedEvent messageDeliveryFailed(MessageDeliveryFailedEvent evt) {
		return evt;
	}

	/*
	 * Implements TransformLayer#messageDeliveryPending(MessageDeliveredEvent).
	 */
	public MessageDeliveredEvent[] messageDeliveryPending(MessageDeliveredEvent evt) {
		Contact contact = evt.getContact();
		OtrContact otrContact = OtrContactManager.getOtrContact(contact, evt.getContactResource());

		// If this is a message otr4j injected earlier, return the event as is.
		if (OtrActivator.scOtrEngine.isMessageUIDInjected(evt.getSourceMessage().getMessageUID()))
			return new MessageDeliveredEvent[]{evt};

		// Process the outgoing message.
		String msgContent = evt.getSourceMessage().getContent();
		String[] processedMessageContent = OtrActivator.scOtrEngine.transformSending(otrContact, msgContent);

		if (processedMessageContent == null
				|| processedMessageContent.length <= 0
				|| processedMessageContent[0].length() < 1)
			return new MessageDeliveredEvent[0];

		if (processedMessageContent.length == 1
				&& processedMessageContent[0].equals(msgContent))
			return new MessageDeliveredEvent[]{evt};

		final MessageDeliveredEvent[] processedEvents = new MessageDeliveredEvent[processedMessageContent.length];
		OperationSetBasicInstantMessaging imOpSet
				= contact.getProtocolProvider().getOperationSet(OperationSetBasicInstantMessaging.class);
		int encType = evt.getSourceMessage().getEncType();
		String subject = evt.getSourceMessage().getSubject();
		ContactResource contactResource = evt.getContactResource();
		String sender = evt.getSender();
		Date timeStamp = evt.getTimestamp();

		for (int i = 0; i < processedMessageContent.length; i++) {
			final String fragmentContent = processedMessageContent[i];
			// Forge a new message based on the new contents.
			IMessage processedMessage = imOpSet.createMessage(fragmentContent, encType, subject);
			// Create a new processedEvent.
			final MessageDeliveredEvent processedEvent = new MessageDeliveredEvent(processedMessage, contact, contactResource, sender, timeStamp);

			if (processedMessage.getContent().contains(SerializationConstants.HEAD)) {
				processedEvent.setMessageEncrypted(true);
			}
			processedEvents[i] = processedEvent;
		}
		return processedEvents;
	}

	/*
	 * Implements TransformLayer#messageReceived(MessageReceivedEvent).
	 */
	public MessageReceivedEvent messageReceived(MessageReceivedEvent evt) {
		Contact contact = evt.getSourceContact();
		OtrContact otrContact = OtrContactManager.getOtrContact(contact, evt.getContactResource());

		// Process the incoming message.
		String msgContent = evt.getSourceMessage().getContent();
		String processedMessageContent = OtrActivator.scOtrEngine.transformReceiving(otrContact, msgContent);

		if (processedMessageContent == null || processedMessageContent.length() < 1)
			return null;

		if (processedMessageContent.equals(msgContent))
			return evt;

		// Forge a new message based on the new contents.
		OperationSetBasicInstantMessaging imOpSet
                = contact.getProtocolProvider().getOperationSet(OperationSetBasicInstantMessaging.class);
		IMessage processedMessage = imOpSet.createMessageWithUID(processedMessageContent,
                evt.getSourceMessage().getEncType(), evt.getSourceMessage().getMessageUID());

		// Create a new event and return.
		MessageReceivedEvent processedEvent = new MessageReceivedEvent(processedMessage, contact,
				evt.getContactResource(), evt.getSender(), evt.getTimestamp(), evt.getCorrectedMessageUID());
		return processedEvent;
	}
}
