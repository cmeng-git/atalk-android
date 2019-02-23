/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license.
 * See terms of license at gnu.org.
 */
package net.java.sip.communicator.util.call;

import net.java.sip.communicator.service.protocol.Contact;
import net.java.sip.communicator.service.protocol.OperationSetServerStoredContactInfo;
import net.java.sip.communicator.service.protocol.OperationSetServerStoredContactInfo.DetailsResponseListener;
import net.java.sip.communicator.service.protocol.ServerStoredDetails.*;

import org.atalk.android.R;
import org.atalk.android.aTalkApp;

import java.util.*;

import timber.log.Timber;

/**
 * Utility class used to check if there is a telephony service, video calls and
 * desktop sharing enabled for a protocol specific <tt>Contact</tt>.
 *
 * @author Yana Stamcheva
 * @author Eng Chong Meng
 */
public class ContactPhoneUtil
{
    /**
     * Searches for phones for the contact.
     * Return null if we have stopped searching and a listener is available
     * and will be used to inform for results.
     *
     * @param contact the contact to check.
     * @param listener the <tt>DetailsResponseListener</tt> if we're interested
     * in obtaining results that came later
     * @param onlyVideo whether to include only video phones.
     * @param localized whether to localize phones.
     * @return list of phones, or null if we will use the listeners for the result.
     */
    public static List<String> getContactAdditionalPhones(Contact contact, DetailsResponseListener listener,
            boolean onlyVideo, boolean localized)
    {
        OperationSetServerStoredContactInfo infoOpSet
                = contact.getProtocolProvider().getOperationSet(OperationSetServerStoredContactInfo.class);
        Iterator<GenericDetail> details;
        ArrayList<String> phonesList = new ArrayList<String>();

        if (infoOpSet != null) {
            try {
                if (listener != null) {
                    details = infoOpSet.requestAllDetailsForContact(contact, listener);
                    if (details == null)
                        return null;
                }
                else {
                    details = infoOpSet.getAllDetailsForContact(contact);
                }

                ArrayList<String> phoneNumbers = new ArrayList<String>();
                while (details.hasNext()) {
                    GenericDetail d = details.next();

                    if (d instanceof PhoneNumberDetail &&
                            !(d instanceof PagerDetail) &&
                            !(d instanceof FaxDetail)) {
                        PhoneNumberDetail pnd = (PhoneNumberDetail) d;
                        String number = pnd.getNumber();
                        if (number != null &&
                                number.length() > 0) {
                            if (!(d instanceof VideoDetail) && onlyVideo)
                                continue;

                            // skip duplicate numbers
                            if (phoneNumbers.contains(number))
                                continue;

                            phoneNumbers.add(number);

                            if (!localized) {
                                phonesList.add(number);
                                continue;
                            }

                            phonesList.add(number + " (" + getLocalizedPhoneNumber(d) + ")");
                        }
                    }
                }
            } catch (Throwable t) {
                Timber.e("Error obtaining server stored contact info");
            }
        }

        return phonesList;
    }

    /**
     * Returns localized phone number.
     *
     * @param d the detail.
     * @return the localized phone number.
     */
    protected static String getLocalizedPhoneNumber(GenericDetail d)
    {
        if (d instanceof WorkPhoneDetail) {
            return aTalkApp.getResString(R.string.service_gui_WORK_PHONE);
        }
        else if (d instanceof MobilePhoneDetail) {
            return aTalkApp.getResString(R.string.service_gui_MOBILE_PHONE);
        }
        else if (d instanceof VideoDetail) {
            return aTalkApp.getResString(R.string.service_gui_VIDEO_PHONE);
        }
        else {
            return aTalkApp.getResString(R.string.service_gui_HOME);
        }
    }
}
