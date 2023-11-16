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
 * <code>WhiteboardObjectModifiedEvent</code>s indicate that a WhiteboardObject has been modified
 * remotely.
 *
 * @author Julien Waechter
 * @author Emil Ivov
 */
public class WhiteboardObjectModifiedEvent extends EventObject
{
	/**
	 * Serial version UID.
	 */
	private static final long serialVersionUID = 0L;

	/**
	 * The contact that has sent this wbObject.
	 */
	private Contact from = null;

	/**
	 * A timestamp indicating the exact date when the event occurred.
	 */
	private Date timestamp = null;

	/**
	 * A reference to the whiteboard object that has been modified.
	 */
	private WhiteboardObject obj;

	/**
	 * Creates a <code>WhiteboardObjectModifiedEvent</code> representing reception of the modified
	 * <code>source</code> WhiteboardObject received from the specified <code>from</code> contact.
	 *
	 * @param source
	 *        the <code>WhiteboardSession</code>
	 * @param obj
	 *        the <code>WhiteboardObject</code> whose reception this event represents.
	 * @param from
	 *        the <code>Contact</code> that has sent this WhiteboardObject.
	 * @param timestamp
	 *        the exact date when the event ocurred.
	 */
	public WhiteboardObjectModifiedEvent(WhiteboardSession source, WhiteboardObject obj,
		Contact from, Date timestamp)
	{
		super(source);
		this.obj = obj;
		this.from = from;
		this.timestamp = timestamp;
	}

	/**
	 * Returns the source white-board session, to which the received object belongs.
	 *
	 * @return the source white-board session, to which the received object belongs
	 */
	public WhiteboardSession getSourceWhiteboardSession()
	{
		return (WhiteboardSession) getSource();
	}

	/**
	 * Returns a reference to the <code>Contact</code> that has send the <code>WhiteboardObject</code> whose
	 * reception this event represents.
	 *
	 * @return a reference to the <code>Contact</code> that has send the <code>WhiteboardObject</code> whose
	 *         reception this event represents.
	 */
	public Contact getSourceContact()
	{
		return from;
	}

	/**
	 * Returns the WhiteboardObject that triggered this event
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
