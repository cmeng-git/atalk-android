/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 * 
 * http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */
package net.java.sip.communicator.service.protocol.event;

import net.java.sip.communicator.service.protocol.Contact;
import net.java.sip.communicator.service.protocol.WhiteboardSession;
import net.java.sip.communicator.service.protocol.whiteboardobjects.WhiteboardObject;

import java.util.Date;
import java.util.EventObject;

/**
 * <code>WhiteboardObjectDeliveredEvent</code>s confirms successful delivery of a WhiteboardObject.
 *
 * @author Julien Waechter
 * @author Emil Ivov
 */
public class WhiteboardObjectDeliveredEvent extends EventObject
{
	/**
	 * Serial version UID.
	 */
	private static final long serialVersionUID = 0L;

	/**
	 * The contact that has sent this wbObject.
	 */
	private Contact to = null;

	/**
	 * A timestamp indicating the exact date when the event occurred.
	 */
	private Date timestamp = null;

	/**
	 * The whiteboard object that has just been delivered.
	 */
	private WhiteboardObject obj;

	/**
	 * Creates a <code>WhiteboardObjectDeliveredEvent</code> representing delivery of the
	 * <code>source</code> whiteboardObject to the specified <code>to</code> contact.
	 *
	 * @param source
	 *        the <code>WhiteboardSession</code> whose delivery this event represents.
	 * @param obj
	 *        the <code>WhiteboardObject</code>
	 * @param to
	 *        the <code>Contact</code> that this whiteboardObject was sent to.
	 * @param timestamp
	 *        a date indicating the exact moment when the event ocurred
	 */
	public WhiteboardObjectDeliveredEvent(WhiteboardSession source, WhiteboardObject obj,
		Contact to, Date timestamp)
	{
		super(source);
		this.obj = obj;
		this.to = to;
		this.timestamp = timestamp;
	}

	/**
	 * Returns a reference to the <code>Contact</code> that the source <code>WhiteboardObject</code> was
	 * sent to.
	 *
	 * @return a reference to the <code>Contact</code> that has send the <code>WhiteboardObject</code> whose
	 *         reception this event represents.
	 */
	public Contact getDestinationContact()
	{
		return to;
	}

	/**
	 * Returns the whiteboardObject that triggered this event
	 *
	 * @return the <code>WhiteboardObject</code> that triggered this event.
	 */
	public WhiteboardObject getSourceWhiteboardObject()
	{
		return obj;
	}

	/**
	 * A timestamp indicating the exact date when the event ocurred.
	 *
	 * @return a Date indicating when the event ocurred.
	 */
	public Date getTimestamp()
	{
		return timestamp;
	}
}
