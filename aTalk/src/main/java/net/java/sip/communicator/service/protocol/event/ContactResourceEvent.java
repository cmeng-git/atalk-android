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
import net.java.sip.communicator.service.protocol.ContactResource;

import java.util.EventObject;

/**
 * The <code>ContactResourceEvent</code> is the event that notifies for any changes in the
 * <code>ContactResource</code>-s for a certain <code>Contact</code>.
 *
 * @author Yana Stamcheva
 */
public class ContactResourceEvent extends EventObject
{
    /**
     * The <code>ContactResource</code> that is concerned by the change.
     */
    private final ContactResource contactResource;

    /**
     * One of the event types defined in this class: RESOURCE_ADDED, RESOURCE_REMOVED, RESOURCE_MODIFIED.
     */
    private final int eventType;

    /**
     * Indicates that the <code>ContactResourceEvent</code> instance was triggered by the add of a <code>ContactResource</code>.
     */
    public static final int RESOURCE_ADDED = 0;

    /**
     * Indicates that the <code>ContactResourceEvent</code> instance was triggered by the removal of a <code>ContactResource</code>.
     */
    public static final int RESOURCE_REMOVED = 1;

    /**
     * Indicates that the <code>ContactResourceEvent</code> instance was triggered by the modification
     * of a <code>ContactResource</code>.
     */
    public static final int RESOURCE_MODIFIED = 2;

    /**
     * Creates an instance of <code>ContactResourceEvent</code> by specifying the source, where this
     * event occurred and the concerned <code>ContactSource</code>.
     *
     * @param source the source where this event occurred
     * @param contactResource the <code>ContactResource</code> that is concerned by the change
     * @param eventType an integer representing the type of this event. One of the types defined in this
     * class: RESOURCE_ADDED, RESOURCE_REMOVED, RESOURCE_MODIFIED.
     */
    public ContactResourceEvent(Contact source, ContactResource contactResource, int eventType)
    {
        super(source);

        this.contactResource = contactResource;
        this.eventType = eventType;
    }

    /**
     * Returns the <code>Contact</code>, which is the source of this event.
     *
     * @return the <code>Contact</code>, which is the source of this event
     */
    public Contact getContact()
    {
        return (Contact) getSource();
    }

    /**
     * Returns the <code>ContactResource</code> that is concerned by the change.
     *
     * @return the <code>ContactResource</code> that is concerned by the change
     */
    public ContactResource getContactResource()
    {
        return contactResource;
    }

    /**
     * Returns the type of the event.
     * <p>
     * One of the event types defined in this class: RESOURCE_ADDED, RESOURCE_REMOVED,
     * RESOURCE_MODIFIED.
     *
     * @return an int representing the type of the event
     */
    public int getEventType()
    {
        return eventType;
    }
}
