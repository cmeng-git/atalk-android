/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 * 
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package net.java.sip.communicator.service.protocol.event;

import net.java.sip.communicator.service.protocol.Call;

import java.util.EventObject;

/**
 * A class representing the event of a call reception.
 *
 * @author Emil Ivov
 */
public class CallReceivedEvent extends EventObject
{
	/**
	 * Serial version UID.
	 */
	private static final long serialVersionUID = 0L;

	/**
	 * Constructor.
	 *
	 * @param call
	 *        the <code>Call</code> received
	 */
	public CallReceivedEvent(Call call)
	{
		super(call);
	}

	/**
	 * Returns the received call.
	 *
	 * @return received <code>Call</code>
	 */
	public Call getCall()
	{
		return (Call) getSource();
	}
}
