/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package net.java.sip.communicator.service.contactsource;

import net.java.sip.communicator.service.protocol.OperationNotSupportedException;
import net.java.sip.communicator.service.protocol.OperationSet;
import net.java.sip.communicator.service.protocol.PresenceStatus;

import java.util.List;

/**
 * The <code>SourceContact</code> is the result contact of a search in the source. It should be
 * identifier by a display name, an image if available and a telephony string, which would allow
 * to call this contact through the preferred telephony provider defined in the <code>ContactSourceService</code>.
 *
 * @author Yana Stamcheva
 * @author Eng Chong Meng
 */
public interface SourceContact {
    /**
     * Returns the display name of this search contact. This is a user-friendly name that could
     * be shown in the user interface.
     *
     * @return the display name of this search contact
     */
    String getDisplayName();

    /**
     * The key that can be used to store <code>SourceContact</code> ids where need it.
     */
    String DATA_ID = SourceContact.class.getName() + ".id";


    /**
     * Returns the address of the contact.
     *
     * @return the contact address.
     */
    String getContactAddress();

    /**
     * Returns the parent <code>ContactSourceService</code> from which this contact came from.
     *
     * @return the parent <code>ContactSourceService</code> from which this contact came from
     */
    ContactSourceService getContactSource();

    /**
     * Returns the display details of this search contact. This could be any important
     * information that should be shown to the user.
     *
     * @return the display details of the search contact
     */
    String getDisplayDetails();

    /**
     * Returns a list of available contact details.
     *
     * @return a list of available contact details
     */
    List<ContactDetail> getContactDetails();

    /**
     * Returns a list of all <code>ContactDetail</code>s supporting the given <code>OperationSet</code> class.
     *
     * @param operationSet the <code>OperationSet</code> class we're looking for
     *
     * @return a list of all <code>ContactDetail</code>s supporting the given <code>OperationSet</code> class
     */
    List<ContactDetail> getContactDetails(Class<? extends OperationSet> operationSet);

    /**
     * Returns a list of all <code>ContactDetail</code>s corresponding to the given category.
     *
     * @param category the <code>OperationSet</code> class we're looking for
     *
     * @return a list of all <code>ContactDetail</code>s corresponding to the given category
     * @throws OperationNotSupportedException if categories aren't supported for call history records
     */
    List<ContactDetail> getContactDetails(ContactDetail.Category category)
            throws OperationNotSupportedException;

    /**
     * Returns the preferred <code>ContactDetail</code> for a given <code>OperationSet</code> class.
     *
     * @param operationSet the <code>OperationSet</code> class, for which we would like to obtain a <code>ContactDetail</code>
     *
     * @return the preferred <code>ContactDetail</code> for a given <code>OperationSet</code> class
     */
    ContactDetail getPreferredContactDetail(Class<? extends OperationSet> operationSet);

    /**
     * An image (or avatar) corresponding to this search contact. If such is not available this
     * method will return null.
     *
     * @return the byte array of the image or null if no image is available
     */
    byte[] getImage();

    /**
     * Whether the current image returned by @see #getImage() is the one provided by the
     * SourceContact by default, or is a one used and obtained from external source.
     *
     * @return whether this is the default image for this SourceContact.
     */
    boolean isDefaultImage();

    /**
     * Gets the user data associated with this instance and a specific key.
     *
     * @param key the key of the user data associated with this instance to be retrieved
     *
     * @return an <code>Object</code> which represents the value associated with this instance and
     * the specified <code>key</code>; <code>null</code> if no association with the specified
     * <code>key</code> exists in this instance
     */
    Object getData(Object key);

    /**
     * Sets the address of the contact.
     *
     * @param contactAddress the address to set.
     */
    void setContactAddress(String contactAddress);

    /**
     * Sets a user-specific association in this instance in the form of a key-value pair. If the
     * specified <code>key</code> is already associated in this instance with a value, the existing
     * value is overwritten with the specified <code>value</code>.
     * <p>
     * The user-defined association created by this method and stored in this instance is not
     * serialized by this instance and is thus only meant for runtime use.
     * </p>
     * <p>
     * The storage of the user data is implementation-specific and is thus not guaranteed to be
     * optimized for execution time and memory use.
     * </p>
     *
     * @param key the key to associate in this instance with the specified value
     * @param value the value to be associated in this instance with the specified <code>key</code>
     */
    void setData(Object key, Object value);

    /**
     * Returns the status of the source contact. And null if such information is not available.
     *
     * @return the PresenceStatus representing the state of this source contact.
     */
    PresenceStatus getPresenceStatus();

    /**
     * Returns the index of this source contact in its parent.
     *
     * @return the index of this source contact in its parent
     */
    int getIndex();
}
