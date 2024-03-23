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
import net.java.sip.communicator.service.protocol.ContactGroup;
import net.java.sip.communicator.service.protocol.ProtocolProviderService;

/**
 * A Contact property change event is issued whenever a contact property has changed. Event codes
 * defined in this class describe properties whose changes are being announced through this event.
 *
 * @author Emil Ivov
 */
public class ContactPropertyChangeEvent extends java.beans.PropertyChangeEvent {
    /**
     * Serial version UID.
     */
    private static final long serialVersionUID = 0L;

    /**
     * Indicates that a change has occurred in the display name of the source contact.
     */
    public static final String PROPERTY_DISPLAY_NAME = "DisplayName";

    /**
     * Indicates that a change has occurred in the image of the source contact.
     */
    public static final String PROPERTY_IMAGE = "Image";

    /**
     * Indicates that a change has occurred in the data that the contact is storing in external
     * sources.
     */
    public static final String PROPERTY_PERSISTENT_DATA = "PersistentData";

    /**
     * Indicates that a change has occurred in the display details of the source contact.
     */
    public static final String PROPERTY_DISPLAY_DETAILS = "DisplayDetails";

    /**
     * Creates a ContactPropertyChangeEvent indicating that a change has occurred for property
     * <code>propertyName</code> in the <code>source</code> contact and that its value has changed from
     * <code>oldValue</code> to <code>newValue</code>.
     * <p>
     *
     * @param source the Contact whose property has changed.
     * @param propertyName the name of the property that has changed.
     * @param oldValue the value of the property before the change occurred.
     * @param newValue the value of the property after the change occurred.
     */
    public ContactPropertyChangeEvent(Contact source, String propertyName, Object oldValue,
            Object newValue) {
        super(source, propertyName, oldValue, newValue);
    }

    /**
     * Returns a reference to the <code>Contact</code> whose property has changed.
     * <p>
     *
     * @return a reference to the <code>Contact</code> whose reference has changed.
     */
    public Contact getSourceContact() {
        return (Contact) getSource();
    }

    /**
     * Returns a reference to the protocol provider where the event has originated.
     * <p>
     *
     * @return a reference to the ProtocolProviderService instance where this event originated.
     */
    public ProtocolProviderService getProtocolProvider() {
        return getSourceContact().getProtocolProvider();
    }

    /**
     * Returns a reference to the source contact parent <code>ContactGroup</code>.
     *
     * @return a reference to the <code>ContactGroup</code> instance that contains the source
     * <code>Contact</code>.
     */
    public ContactGroup getParentContactGroup() {
        return getSourceContact().getParentContactGroup();
    }
}
