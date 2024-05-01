/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license.
 * See terms of license at gnu.org.
 */
package net.java.sip.communicator.util.call;

import java.util.ArrayList;
import java.util.Hashtable;
import java.util.Iterator;
import java.util.List;

import net.java.sip.communicator.service.contactlist.MetaContact;
import net.java.sip.communicator.service.protocol.Contact;
import net.java.sip.communicator.service.protocol.OperationSet;
import net.java.sip.communicator.service.protocol.OperationSetBasicTelephony;
import net.java.sip.communicator.service.protocol.OperationSetContactCapabilities;
import net.java.sip.communicator.service.protocol.OperationSetDesktopSharingServer;
import net.java.sip.communicator.service.protocol.OperationSetServerStoredContactInfo.DetailsResponseListener;
import net.java.sip.communicator.service.protocol.OperationSetVideoTelephony;
import net.java.sip.communicator.service.protocol.ServerStoredDetails.GenericDetail;
import net.java.sip.communicator.service.protocol.ServerStoredDetails.MobilePhoneDetail;
import net.java.sip.communicator.service.protocol.ServerStoredDetails.VideoDetail;
import net.java.sip.communicator.service.protocol.ServerStoredDetails.WorkPhoneDetail;
import net.java.sip.communicator.util.ConfigurationUtils;
import net.java.sip.communicator.util.account.AccountUtils;

import org.atalk.android.R;
import org.atalk.android.aTalkApp;

/**
 * Utility class used to check if there is a telephony service, video calls and
 * desktop sharing enabled for a protocol specific <code>MetaContact</code>.
 *
 * @author Damian Minkov
 * @author Yana Stamcheva
 * @author Eng Chong Meng
 */
public class MetaContactPhoneUtil {
    /**
     * The metaContact we are working on.
     */
    private final MetaContact metaContact;

    /**
     * The phones that have been discovered for metaContact child contacts.
     */
    private final Hashtable<Contact, List<String>> phones = new Hashtable<>();

    /**
     * The video phones that have been discovered for metaContact child contacts.
     */
    private final Hashtable<Contact, List<String>> videoPhones = new Hashtable<>();

    /**
     * True if there is any phone found for the metaContact.
     */
    private boolean hasPhones = false;

    /**
     * True if there is any video phone found for the metaContact.
     */
    private boolean hasVideoDetail = false;

    /**
     * Is routing for video enabled for any of the contacts of the metaContact.
     */
    private boolean routingForVideoEnabled = false;

    /**
     * Is routing for desktop enabled for any of the contacts of the metaContact.
     */
    private boolean routingForDesktopEnabled = false;

    /**
     * Obtains the util for <code>metaContact</code>
     *
     * @param metaContact the metaContact.
     *
     * @return ContactPhoneUtil for the <code>metaContact</code>.
     */
    public static MetaContactPhoneUtil getPhoneUtil(MetaContact metaContact) {
        return new MetaContactPhoneUtil(metaContact);
    }

    /**
     * Creates utility instance for <code>metaContact</code>.
     *
     * @param metaContact the metaContact checked in the utility.
     */
    protected MetaContactPhoneUtil(MetaContact metaContact) {
        this.metaContact = metaContact;
    }

    /**
     * Returns the metaContact we work on.
     *
     * @return the metaContact we work on.
     */
    public MetaContact getMetaContact() {
        return metaContact;
    }

    /**
     * Returns localized addition phones list for contact, if any.
     * Return null if we have stopped searching and a listener is available and will be used to inform for results.
     *
     * @param contact the contact
     *
     * @return localized addition phones list for contact, if any.
     */
    public List<String> getPhones(Contact contact) {
        return getPhones(contact, null, true);
    }

    /**
     * Returns list of video phones for <code>contact</code>, localized.
     * Return null if we have stopped searching and a listener is available
     * and will be used to inform for results.
     *
     * @param contact the contact to check for video phones.
     * @param listener the <code>DetailsResponseListener</code> to listen for result details
     *
     * @return list of video phones for <code>contact</code>, localized.
     */
    public List<String> getVideoPhones(Contact contact, DetailsResponseListener listener) {
        if (!this.metaContact.containsContact(contact)) {
            return new ArrayList<String>();
        }

        if (videoPhones.containsKey(contact)) {
            return videoPhones.get(contact);
        }

        List<String> phonesList = ContactPhoneUtil.getContactAdditionalPhones(contact, listener, true, true);

        if (phonesList == null)
            return null;
        else if (phonesList.size() > 0)
            hasVideoDetail = true;

        videoPhones.put(contact, phonesList);

        // to check for routingForVideoEnabled prop
        isVideoCallEnabled(contact);
        // to check for routingForDesktopEnabled prop
        isDesktopSharingEnabled(contact);

        return phonesList;
    }

    /**
     * List of phones for contact, localized if <code>localized</code> is <code>true</code>, and not otherwise.
     * Return null if we have stopped searching and a listener is available
     * and will be used to inform for results.
     *
     * @param contact the contact to check for video phones.
     * @param listener the <code>DetailsResponseListener</code> to listen for result details
     * @param localized whether to localize the phones, put a description text.
     *
     * @return list of phones for contact.
     */
    public List<String> getPhones(Contact contact, DetailsResponseListener listener, boolean localized) {
        if (!this.metaContact.containsContact(contact)) {
            return new ArrayList<>();
        }

        if (phones.containsKey(contact)) {
            return phones.get(contact);
        }

        List<String> phonesList = ContactPhoneUtil.getContactAdditionalPhones(contact, listener, false, localized);
        if (phonesList == null)
            return null;
        else if (phonesList.size() > 0)
            hasPhones = true;

        phones.put(contact, phonesList);
        return phonesList;
    }

    /**
     * Is video called is enabled for metaContact. If any of the child contacts has video enabled.
     *
     * @param listener the <code>DetailsResponseListener</code> to listen for result details
     *
     * @return is video called is enabled for metaContact.
     */
    public boolean isVideoCallEnabled(DetailsResponseListener listener) {
        // make sure children are checked
        if (!checkMetaContactVideoPhones(listener))
            return false;

        return (metaContact.getOpSetSupportedContact(OperationSetVideoTelephony.class) != null)
                || routingForVideoEnabled
                || hasVideoDetail;
    }

    /**
     * Is video called is enabled for metaContact. If any of the child contacts has video enabled.
     *
     * @return is video called is enabled for metaContact.
     */
    public boolean isVideoCallEnabled() {
        return isVideoCallEnabled((DetailsResponseListener) null);
    }

    /**
     * Is video call enabled for contact.
     *
     * @param contact to check for video capabilities.
     *
     * @return is video call enabled for contact.
     */
    public boolean isVideoCallEnabled(Contact contact) {
        if (!this.metaContact.containsContact(contact))
            return false;

        // make sure we have checked everything for the contact before continue
        if (!checkContactPhones(contact))
            return false;

        routingForVideoEnabled = ConfigurationUtils.isRouteVideoAndDesktopUsingPhoneNumberEnabled()
                && phones.containsKey(contact)
                && phones.get(contact).size() > 0
                && AccountUtils.getOpSetRegisteredProviders(
                OperationSetVideoTelephony.class, null, null).size() > 0;

        return contact.getProtocolProvider().getOperationSet(OperationSetVideoTelephony.class) != null
                && hasContactCapabilities(contact, OperationSetVideoTelephony.class)
                || routingForVideoEnabled;
    }

    /**
     * Is desktop sharing enabled for metaContact. If any of the child contacts has desktop sharing enabled.
     *
     * @param listener the <code>DetailsResponseListener</code> to listen for result details
     *
     * @return is desktop share is enabled for metaContact.
     */
    public boolean isDesktopSharingEnabled(DetailsResponseListener listener) {
        // make sure children are checked
        if (!checkMetaContactVideoPhones(listener))
            return false;

        return metaContact.getDefaultContact(OperationSetDesktopSharingServer.class) != null
                || routingForDesktopEnabled
                || hasVideoDetail;
    }

    /**
     * Is desktop sharing enabled for metaContact. If any of the child contacts has desktop sharing enabled.
     *
     * @return is desktop share is enabled for metaContact.
     */
    public boolean isDesktopSharingEnabled() {
        return isDesktopSharingEnabled((DetailsResponseListener) null);
    }

    /**
     * Is desktop sharing enabled for contact.
     *
     * @param contact to check for desktop sharing capabilities.
     *
     * @return is desktop sharing enabled for contact.
     */
    public boolean isDesktopSharingEnabled(Contact contact) {
        if (!this.metaContact.containsContact(contact))
            return false;

        // make sure we have checked everything for the contact
        // before continue
        if (!checkContactPhones(contact))
            return false;

        routingForDesktopEnabled = ConfigurationUtils.isRouteVideoAndDesktopUsingPhoneNumberEnabled()
                && phones.containsKey(contact)
                && phones.get(contact).size() > 0
                && AccountUtils.getOpSetRegisteredProviders(
                OperationSetDesktopSharingServer.class, null, null).size() > 0;
        return contact.getProtocolProvider().getOperationSet(OperationSetDesktopSharingServer.class) != null
                && hasContactCapabilities(contact, OperationSetDesktopSharingServer.class)
                || routingForDesktopEnabled;
    }

    /**
     * Is call enabled for metaContact. If any of the child contacts has call enabled.
     *
     * @param listener the <code>DetailsResponseListener</code> to listen for result details
     *
     * @return is call enabled for metaContact.
     */
    public boolean isCallEnabled(DetailsResponseListener listener) {
        return isCallEnabled(listener, true);
    }

    /**
     * Is call enabled for metaContact. If any of the child contacts has call enabled.
     *
     * @param listener the <code>DetailsResponseListener</code> to listen for result details
     * @param checkForTelephonyOpSet whether we should check for registered
     * telephony operation sets that can be used to dial out, can be used
     * in plugins dialing out using methods outside the provider.
     *
     * @return is call enabled for metaContact.
     */
    public boolean isCallEnabled(DetailsResponseListener listener, boolean checkForTelephonyOpSet) {
        // make sure children are checked
        if (!checkMetaContactPhones(listener))
            return false;

        boolean hasPhoneCheck = hasPhones;
        if (checkForTelephonyOpSet)
            hasPhoneCheck = hasPhones && AccountUtils.getRegisteredProviders(OperationSetBasicTelephony.class).size() > 0;

        return metaContact.getDefaultContact(OperationSetBasicTelephony.class) != null
                || hasPhoneCheck;
    }

    /**
     * Is call enabled for metaContact. If any of the child contacts has call enabled.
     *
     * @return is call enabled for metaContact.
     */
    public boolean isCallEnabled() {
        return isCallEnabled(null, true);
    }

    /**
     * Is call enabled for metaContact. If any of the child contacts has call enabled.
     *
     * @param checkForTelephonyOpSet whether we should check for registered
     * telephony operation sets that can be used to dial out, can be used
     * in plugins dialing out using methods outside the provider.
     *
     * @return is call enabled for metaContact.
     */
    public boolean isCallEnabled(boolean checkForTelephonyOpSet) {
        return isCallEnabled(null, checkForTelephonyOpSet);
    }

    /**
     * Is call enabled for contact.
     *
     * @param contact to check for call capabilities.
     *
     * @return is call enabled for contact.
     */
    public boolean isCallEnabled(Contact contact) {
        if (!checkContactPhones(contact))
            return false;

        return contact.getProtocolProvider().getOperationSet(OperationSetBasicTelephony.class) != null
                && hasContactCapabilities(contact, OperationSetBasicTelephony.class);
    }

    /**
     * Checking all contacts for the metaContact.
     * Return <code>false</code> if there are listeners added for a contact
     * and we need to stop executions cause listener will be used to be informed for result.
     *
     * @return whether to continue or listeners present and will be informed for result.
     */
    private boolean checkMetaContactPhones() {
        return checkMetaContactPhones(null);
    }

    /**
     * Checking all contacts for the metaContact.
     * Return <code>false</code> if there are listeners added for a contact
     * and we need to stop executions cause listener will be used to be informed for result.
     *
     * @param l the <code>DetailsResponseListener</code> to listen for further details
     *
     * @return whether to continue or listeners present and will be informed for result.
     */
    private boolean checkMetaContactPhones(DetailsResponseListener l) {
        Iterator<Contact> contactIterator = metaContact.getContacts();
        while (contactIterator.hasNext()) {
            Contact contact = contactIterator.next();
            if (phones.containsKey(contact))
                continue;

            List<String> phones = getPhones(contact, l, false);
            if (phones == null)
                return false;
        }
        return true;
    }

    /**
     * Checking all contacts for the metaContact.
     * Return <code>false</code> if there are listeners added for a contact
     * and we need to stop executions cause listener will be used to be informed for result.
     *
     * @param l the <code>DetailsResponseListener</code> to listen for further details
     *
     * @return whether to continue or listeners present and will be informed for result.
     */
    private boolean checkMetaContactVideoPhones(DetailsResponseListener l) {
        Iterator<Contact> contactIterator = metaContact.getContacts();
        while (contactIterator.hasNext()) {
            Contact contact = contactIterator.next();
            if (videoPhones.containsKey(contact))
                continue;

            List<String> phones = getVideoPhones(contact, l);
            if (phones == null)
                return false;
        }
        return true;
    }

    /**
     * Checking contact for phones.
     * Return <code>false</code> if there are listeners added for the contact
     * and we need to stop executions cause listener will be used to be informed for result.
     *
     * @return whether to continue or listeners present and will be informed for result.
     */
    private boolean checkContactPhones(Contact contact) {
        if (!phones.containsKey(contact)) {
            List<String> phones = getPhones(contact);
            if (phones == null)
                return false;

            // to check for routingForVideoEnabled prop
            isVideoCallEnabled(contact);
            // to check for routingForDesktopEnabled prop
            isDesktopSharingEnabled(contact);
        }
        return true;
    }

    /**
     * Returns <code>true</code> if <code>Contact</code> supports the specified <code>OperationSet</code>, <code>false</code> otherwise.
     *
     * @param contact contact to check
     * @param opSet <code>OperationSet</code> to search for
     *
     * @return Returns <code>true</code> if <code>Contact</code> supports the specified
     * <code>OperationSet</code>, <code>false</code> otherwise.
     */
    private boolean hasContactCapabilities(Contact contact, Class<? extends OperationSet> opSet) {
        OperationSetContactCapabilities capOpSet
                = contact.getProtocolProvider().getOperationSet(OperationSetContactCapabilities.class);

        // assume contact has OpSet capabilities if null
        if (capOpSet == null) {
            return true;
        }
        else {
            return capOpSet.getOperationSet(contact, opSet) != null;
        }
    }

    /**
     * Returns localized phone number.
     *
     * @param d the detail.
     *
     * @return the localized phone number.
     */
    protected String getLocalizedPhoneNumber(GenericDetail d) {
        if (d instanceof WorkPhoneDetail) {
            return aTalkApp.getResString(R.string.work);
        }
        else if (d instanceof MobilePhoneDetail) {
            return aTalkApp.getResString(R.string.mobile);
        }
        else if (d instanceof VideoDetail) {
            return aTalkApp.getResString(R.string.video);
        }
        else {
            return aTalkApp.getResString(R.string.home);
        }
    }
}
