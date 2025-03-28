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
package net.java.sip.communicator.impl.callhistory;

import net.java.sip.communicator.service.callhistory.CallPeerRecord;
import net.java.sip.communicator.service.callhistory.CallRecord;
import net.java.sip.communicator.service.contactsource.ContactDetail;
import net.java.sip.communicator.service.contactsource.ContactSourceService;
import net.java.sip.communicator.service.contactsource.SourceContact;
import net.java.sip.communicator.service.protocol.Contact;
import net.java.sip.communicator.service.protocol.OperationNotSupportedException;
import net.java.sip.communicator.service.protocol.OperationSet;
import net.java.sip.communicator.service.protocol.OperationSetBasicTelephony;
import net.java.sip.communicator.service.protocol.OperationSetContactCapabilities;
import net.java.sip.communicator.service.protocol.OperationSetPersistentPresence;
import net.java.sip.communicator.service.protocol.OperationSetPresence;
import net.java.sip.communicator.service.protocol.PresenceStatus;
import net.java.sip.communicator.service.protocol.ProtocolNames;
import net.java.sip.communicator.service.protocol.ProtocolProviderService;
import net.java.sip.communicator.service.protocol.event.CallPeerChangeEvent;
import net.java.sip.communicator.util.DataObject;
import net.java.sip.communicator.util.GuiUtils;

import org.atalk.android.R;
import org.atalk.android.aTalkApp;

import java.util.Date;
import java.util.Hashtable;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

/**
 * The <code>CallHistorySourceContact</code> is an implementation of the
 * <code>SourceContact</code> interface based on a <code>CallRecord</code>.
 *
 * @author Yana Stamcheva
 * @author Eng Chong Meng
 */
public class CallHistorySourceContact extends DataObject implements SourceContact
{
    /**
     * Whether we need to strip saved addresses to numbers. We strip everything
     * before '@', if it is absent nothing is changed from the saved address.
     */
    private static final String STRIP_ADDRESSES_TO_NUMBERS = "callhistory.STRIP_ADDRESSES_TO_NUMBERS";

    /**
     * The parent <code>CallHistoryContactSource</code>, where this contact is contained.
     */
    private final CallHistoryContactSource contactSource;

    /**
     * The corresponding call record.
     */
    private final CallRecord callRecord;

    /**
     * The incoming call icon.
     */
    private static final byte[] incomingIcon
            = CallHistoryActivator.getResources().getImageInBytes("gui.icons.INCOMING_CALL");

    /**
     * The outgoing call icon.
     */
    private static byte[] outgoingIcon
            = CallHistoryActivator.getResources().getImageInBytes("gui.icons.OUTGOING_CALL");

    /**
     * The missed call icon.
     */
    private static byte[] missedCallIcon
            = CallHistoryActivator.getResources().getImageInBytes("gui.icons.MISSED_CALL");

    /**
     * A list of all contact details.
     */
    private final List<ContactDetail> contactDetails = new LinkedList<>();

    /**
     * The display name of this contact.
     */
    private String displayName = "";

    /**
     * The display details of this contact.
     */
    private final String displayDetails;

    /**
     * Creates an instance of <code>CallHistorySourceContact</code>
     *
     * @param contactSource the contact source
     * @param callRecord the call record
     */
    public CallHistorySourceContact(CallHistoryContactSource contactSource, CallRecord callRecord)
    {
        this.contactSource = contactSource;
        this.callRecord = callRecord;

        this.initPeerDetails();

        this.displayDetails
                = aTalkApp.getResString(R.string.at) + ": "
                + getDateString(callRecord.getStartTime().getTime())
                + " " + aTalkApp.getResString(R.string.duration) + ": "
                + GuiUtils.formatTime(callRecord.getStartTime(), callRecord.getEndTime());
    }

    /**
     * Initializes peer details.
     */
    private void initPeerDetails()
    {
        boolean stripAddress = false;
        String stripAddressProp = CallHistoryActivator.getResources().getSettingsString(STRIP_ADDRESSES_TO_NUMBERS);

        if (stripAddressProp != null && Boolean.parseBoolean(stripAddressProp))
            stripAddress = true;

        for (CallPeerRecord peerRecord : callRecord.getPeerRecords()) {
            String peerAddress = peerRecord.getPeerAddress();
            String peerSecondaryAddress = peerRecord.getPeerSecondaryAddress();

            if (peerAddress != null) {
                if (stripAddress && !peerAddress.startsWith("@")) {
                    peerAddress = peerAddress.split("@")[0];
                }

                String peerRecordDisplayName = peerRecord.getDisplayName();
                if (peerRecordDisplayName == null || peerRecordDisplayName.length() == 0)
                    peerRecordDisplayName = peerAddress;

                ContactDetail contactDetail = new ContactDetail(peerAddress, peerRecordDisplayName);

                Map<Class<? extends OperationSet>, ProtocolProviderService> preferredProviders = null;
                Map<Class<? extends OperationSet>, String> preferredProtocols = null;

                ProtocolProviderService preferredProvider = callRecord.getProtocolProvider();

                if (preferredProvider != null) {
                    preferredProviders = new Hashtable<>();

                    OperationSetPresence opSetPres = preferredProvider.getOperationSet(OperationSetPresence.class);

                    Contact contact = null;
                    if (opSetPres != null)
                        contact = opSetPres.findContactByID(peerAddress);

                    OperationSetContactCapabilities opSetCaps
                            = preferredProvider.getOperationSet(OperationSetContactCapabilities.class);

                    if (opSetCaps != null && opSetPres != null) {
                        if (contact != null
                                && opSetCaps.getOperationSet(contact, OperationSetBasicTelephony.class) != null) {
                            preferredProviders.put(OperationSetBasicTelephony.class, preferredProvider);
                        }
                    }
                    else {
                        preferredProviders.put(OperationSetBasicTelephony.class, preferredProvider);
                    }
                    contactDetail.setPreferredProviders(preferredProviders);
                }
                // If there's no preferred provider set we just specify that
                // the SIP protocol should be used for the telephony operation
                // set. This is needed for all history records stored before
                // the protocol provider property had been introduced.
                else {
                    preferredProtocols = new Hashtable<>();

                    preferredProtocols.put(OperationSetBasicTelephony.class, ProtocolNames.SIP);

                    contactDetail.setPreferredProtocols(preferredProtocols);
                }

                LinkedList<Class<? extends OperationSet>> supportedOpSets = new LinkedList<>();

                // if the contact supports call
                if ((preferredProviders != null
                        && preferredProviders.containsKey(OperationSetBasicTelephony.class))
                        || (preferredProtocols != null)) {
                    supportedOpSets.add(OperationSetBasicTelephony.class);
                }

                // can be added as contacts
                supportedOpSets.add(OperationSetPersistentPresence.class);
                contactDetail.setSupportedOpSets(supportedOpSets);
                contactDetails.add(contactDetail);

                if (peerSecondaryAddress != null) {
                    ContactDetail secondaryContactDetail = new ContactDetail(peerSecondaryAddress);
                    secondaryContactDetail.addSupportedOpSet(OperationSetPersistentPresence.class);
                    contactDetails.add(secondaryContactDetail);
                }

                // Set the displayName.
                String name = peerRecord.getDisplayName();

                if (name == null || name.length() <= 0)
                    name = peerAddress;

                if (displayName == null || displayName.length() <= 0)
                    if (callRecord.getPeerRecords().size() > 1)
                        displayName = "Conference " + name;
                    else
                        displayName = name;

            }
        }
    }

    /**
     * Returns a list of available contact details.
     *
     * @return a list of available contact details
     */
    public List<ContactDetail> getContactDetails()
    {
        return new LinkedList<>(contactDetails);
    }

    /**
     * Returns the parent <code>ContactSourceService</code> from which this contact came from.
     *
     * @return the parent <code>ContactSourceService</code> from which this contact came from
     */
    public ContactSourceService getContactSource()
    {
        return contactSource;
    }

    /**
     * Returns the display details of this search contact. This could be any
     * important information that should be shown to the user.
     *
     * @return the display details of the search contact
     */
    public String getDisplayDetails()
    {
        return displayDetails;
    }

    /**
     * Returns the display name of this search contact. This is a user-friendly
     * name that could be shown in the user interface.
     *
     * @return the display name of this search contact
     */
    public String getDisplayName()
    {
        return displayName;
    }

    /**
     * An image (or avatar) corresponding to this search contact. If such is
     * not available this method will return null.
     *
     * @return the byte array of the image or null if no image is available
     */
    public byte[] getImage()
    {
        if (callRecord.getDirection().equals(CallRecord.IN)) {
            // if the call record has reason for normal call clearing
            // means it was answered somewhere else, then we don't mark it as missed
            if (callRecord.getStartTime().equals(callRecord.getEndTime())
                    && (callRecord.getEndReason() != CallPeerChangeEvent.NORMAL_CALL_CLEARING))
                return missedCallIcon;
            else
                return incomingIcon;
        }
        else if (callRecord.getDirection().equals(CallRecord.OUT))
            return outgoingIcon;

        return null;
    }

    /**
     * Returns a list of all <code>ContactDetail</code>s supporting the given <code>OperationSet</code> class.
     *
     * @param operationSet the <code>OperationSet</code> class we're looking for
     * @return a list of all <code>ContactDetail</code>s supporting the given <code>OperationSet</code> class.
     */
    public List<ContactDetail> getContactDetails(Class<? extends OperationSet> operationSet)
    {
        // We support only call details or persistence presence so we can add contacts.
        if (!(operationSet.equals(OperationSetBasicTelephony.class)
                || operationSet.equals(OperationSetPersistentPresence.class)))
            return null;

        return new LinkedList<>(contactDetails);
    }

    /**
     * Returns a list of all <code>ContactDetail</code>s corresponding to the given category.
     *
     * @param category the <code>OperationSet</code> class we're looking for
     * @return a list of all <code>ContactDetail</code>s corresponding to the given category
     */
    public List<ContactDetail> getContactDetails(ContactDetail.Category category)
            throws OperationNotSupportedException
    {
        // We don't support category for call history details, so we return null.
        throw new OperationNotSupportedException("Categories are not supported for call history records.");
    }

    /**
     * Returns the preferred <code>ContactDetail</code> for a given <code>OperationSet</code> class.
     *
     * @param operationSet the <code>OperationSet</code> class, for which we would
     * like to obtain a <code>ContactDetail</code>
     * @return the preferred <code>ContactDetail</code> for a given <code>OperationSet</code> class
     */
    public ContactDetail getPreferredContactDetail(Class<? extends OperationSet> operationSet)
    {
        // We support only call details
        // or persistence presence so we can add contacts.
        if (!(operationSet.equals(OperationSetBasicTelephony.class)
                || operationSet.equals(OperationSetPersistentPresence.class)))
            return null;

        return contactDetails.get(0);
    }

    /**
     * Returns the date string to show for the given date.
     *
     * @param date the date to format
     * @return the date string to show for the given date
     */
    public static String getDateString(long date)
    {
        String time = GuiUtils.formatTime(date);

        // If the current date we don't go in there and we'll return just the time.
        if (GuiUtils.compareDatesOnly(date, System.currentTimeMillis()) < 0) {
            return GuiUtils.formatDateTime(new Date(date));
        }

        return time;
    }

    /**
     * Returns the status of the source contact. And null if such information is not available.
     *
     * @return the PresenceStatus representing the state of this source contact.
     */
    public PresenceStatus getPresenceStatus()
    {
        return null;
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

    /**
     * {@inheritDoc}
     *
     * Not implemented.
     */
    @Override
    public String getContactAddress()
    {
        return null;
    }

    /**
     * {@inheritDoc}
     *
     * Not implemented.
     */
    @Override
    public void setContactAddress(String contactAddress)
    {
    }

    /**
     * Whether the current image returned by @see #getImage() is the one provided by the
     * SourceContact by default, or is a one used and obtained from external source.
     *
     * @return whether this is the default image for this SourceContact.
     */
    @Override
    public boolean isDefaultImage()
    {
        // in this SourceContact we always show a default image based
        // on the call direction (in, out or missed)
        return true;
    }
}
