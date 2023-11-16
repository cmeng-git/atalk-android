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

import net.java.sip.communicator.service.protocol.OperationSet;
import net.java.sip.communicator.service.protocol.PresenceStatus;
import net.java.sip.communicator.util.DataObject;

import java.util.Collections;
import java.util.LinkedList;
import java.util.List;

/**
 * Implements a generic <code>SourceContact</code> for the purposes of the support
 * for the OS-specific Address Book.
 *
 * @author Lyubomir Marinov
 */
public class GenericSourceContact extends DataObject
        implements SourceContact
{
    /**
     * The <code>ContactDetail</code>s of this <code>SourceContact</code>.
     */
    protected final List<ContactDetail> contactDetails;

    /**
     * The <code>ContactSourceService</code> which has created this <code>SourceContact</code>.
     */
    private final ContactSourceService contactSource;

    /**
     * The display name of this <code>SourceContact</code>.
     */
    private String displayName;

    /**
     * The display details of this contact.
     */
    private String displayDetails;

    /**
     * The presence status of this contact.
     */
    private PresenceStatus presenceStatus;

    /**
     * The image/avatar of this <code>SourceContact</code>
     */
    private byte[] image;

    /**
     * The address of the contact.
     */
    private String contactAddress = null;

    /**
     * Initializes a new <code>AddrBookSourceContact</code> instance.
     *
     * @param contactSource the <code>ContactSourceService</code> which is creating the new instance
     * @param displayName the display name of the new instance
     * @param contactDetails the <code>ContactDetail</code>s of the new instance
     */
    public GenericSourceContact(
            ContactSourceService contactSource,
            String displayName,
            List<ContactDetail> contactDetails)
    {
        this.contactSource = contactSource;
        this.displayName = displayName;
        this.contactDetails = contactDetails;
    }

    /**
     * Returns the address of the contact.
     *
     * @return the contact address.
     */
    public String getContactAddress()
    {
        return contactAddress;
    }

    /**
     * Gets the <code>ContactDetail</code>s of this <code>SourceContact</code>.
     *
     * @return the <code>ContactDetail</code>s of this <code>SourceContact</code>
     * @see SourceContact#getContactDetails()
     */
    public List<ContactDetail> getContactDetails()
    {
        return Collections.unmodifiableList(contactDetails);
    }

    /**
     * Gets the <code>ContactDetail</code>s of this <code>SourceContact</code> which
     * support a specific <code>OperationSet</code>.
     *
     * @param operationSet the <code>OperationSet</code> the supporting
     * <code>ContactDetail</code>s of which are to be returned
     * @return the <code>ContactDetail</code>s of this <code>SourceContact</code> which support the specified
     * <code>operationSet</code>
     * @see SourceContact#getContactDetails(Class)
     */
    public List<ContactDetail> getContactDetails(Class<? extends OperationSet> operationSet)
    {
        List<ContactDetail> contactDetails = new LinkedList<ContactDetail>();
        for (ContactDetail contactDetail : getContactDetails()) {
            List<Class<? extends OperationSet>> supportedOperationSets = contactDetail.getSupportedOperationSets();
            if ((supportedOperationSets != null)
                    && supportedOperationSets.contains(operationSet))
                contactDetails.add(contactDetail);
        }
        return contactDetails;
    }

    /**
     * Returns a list of all <code>ContactDetail</code>s corresponding to the given category.
     *
     * @param category the <code>OperationSet</code> class we're looking for
     * @return a list of all <code>ContactDetail</code>s corresponding to the given category
     */
    public List<ContactDetail> getContactDetails(ContactDetail.Category category)
    {
        List<ContactDetail> contactDetails = new LinkedList<ContactDetail>();
        for (ContactDetail contactDetail : getContactDetails()) {
            if (contactDetail != null) {
                ContactDetail.Category detailCategory = contactDetail.getCategory();
                if (detailCategory != null && detailCategory.equals(category))
                    contactDetails.add(contactDetail);
            }
        }
        return contactDetails;
    }

    /**
     * Gets the <code>ContactSourceService</code> which has created this <code>SourceContact</code>.
     *
     * @return the <code>ContactSourceService</code> which has created this <code>SourceContact</code>
     * @see SourceContact#getContactSource()
     */
    public ContactSourceService getContactSource()
    {
        return contactSource;
    }

    /**
     * Gets the display details of this <code>SourceContact</code>.
     *
     * @return the display details of this <code>SourceContact</code>
     * @see SourceContact#getDisplayDetails()
     */
    public String getDisplayDetails()
    {
        return displayDetails;
    }

    /**
     * Sets the address of the contact.
     *
     * @param contactAddress the address to set.
     */
    public void setContactAddress(String contactAddress)
    {
        this.contactAddress = contactAddress;
    }

    /**
     * Sets the display details of this <code>SourceContact</code>.
     *
     * @param displayDetails the display details of this <code>SourceContact</code>
     */
    public String setDisplayDetails(String displayDetails)
    {
        return this.displayDetails = displayDetails;
    }

    /**
     * Gets the display name of this <code>SourceContact</code>.
     *
     * @return the display name of this <code>SourceContact</code>
     * @see SourceContact#getDisplayName()
     */
    public String getDisplayName()
    {
        return displayName;
    }

    /**
     * Sets the display name of this <code>SourceContact</code>.
     *
     * @param displayName The display name of this <code>SourceContact</code>
     */
    public void setDisplayName(String displayName)
    {
        this.displayName = displayName;
    }

    /**
     * Gets the image/avatar of this <code>SourceContact</code>.
     *
     * @return the image/avatar of this <code>SourceContact</code>
     * @see SourceContact#getImage()
     */
    public byte[] getImage()
    {
        return image;
    }

    /**
     * Gets the preferred <code>ContactDetail</code> for a specific <code>OperationSet</code>.
     *
     * @param operationSet the <code>OperationSet</code> to get the preferred <code>ContactDetail</code> for
     * @return the preferred <code>ContactDetail</code> for the specified <code>operationSet</code>
     * @see SourceContact#getPreferredContactDetail(Class)
     */
    public ContactDetail getPreferredContactDetail(Class<? extends OperationSet> operationSet)
    {
        List<ContactDetail> contactDetails = getContactDetails(operationSet);
        return contactDetails.isEmpty() ? null : contactDetails.get(0);
    }

    /**
     * Sets the image/avatar of this <code>SourceContact</code>.
     *
     * @param image the image/avatar to be set on this <code>SourceContact</code>
     */
    public void setImage(byte[] image)
    {
        this.image = image;
    }

    /**
     * Whether the current image returned by @see #getImage() is the one
     * provided by the SourceContact by default, or is a one used and obtained from external source.
     *
     * @return whether this is the default image for this SourceContact.
     */
    @Override
    public boolean isDefaultImage()
    {
        // in this SourceContact we always show an externally set image or null
        return false;
    }

    /**
     * Returns the status of the source contact. And null if such information is not available.
     *
     * @return the PresenceStatus representing the state of this source contact.
     */
    public PresenceStatus getPresenceStatus()
    {
        return presenceStatus;
    }

    /**
     * Sets the status of the source contact.
     *
     * @param presenceStatus the status of this contact
     */
    public void setPresenceStatus(PresenceStatus presenceStatus)
    {
        this.presenceStatus = presenceStatus;
    }

    /**
     * Returns the index of this source contact in its parent.
     *
     * @return the index of this source contact in its parent
     */
    public int getIndex()
    {
        return -1;
    }
}
