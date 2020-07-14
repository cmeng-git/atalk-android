/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package net.java.sip.communicator.impl.protocol.jabber;

import net.java.sip.communicator.service.protocol.*;
import net.java.sip.communicator.util.call.ContactPhoneUtil;

import java.util.Iterator;
import java.util.List;

/**
 * The <tt>OperationSetCusaxUtilsJabberImpl</tt> provides utility methods related to the Jabber CUSAX implementation.
 *
 * @author Yana Stamcheva
 */
public class OperationSetCusaxUtilsJabberImpl implements OperationSetCusaxUtils
{
    /**
     * Checks if the given <tt>detailAddress</tt> exists in the given <tt>contact</tt> details.
     *
     * @param contact the <tt>Contact</tt>, which details to check
     * @param detailAddress the detail address we're looking for
     * @return <tt>true</tt> if the given <tt>detailAdress</tt> exists in the details of the given <tt>contact</tt>
     */
    public boolean doesDetailBelong(Contact contact, String detailAddress)
    {
        List<String> contactPhones = ContactPhoneUtil.getContactAdditionalPhones(contact, null, false, false);

        if (contactPhones == null || contactPhones.size() <= 0)
            return false;

        Iterator<String> phonesIter = contactPhones.iterator();

        while (phonesIter.hasNext()) {
            String phone = phonesIter.next();
            String normalizedPhone = JabberActivator.getPhoneNumberI18nService().normalize(phone);

            if (phone.equals(detailAddress) || normalizedPhone.equals(detailAddress)
                    || detailAddress.contains(phone) || detailAddress.contains(normalizedPhone))
                return true;
        }

        return false;
    }

    /**
     * Returns the linked CUSAX provider for this protocol provider.
     *
     * @return the linked CUSAX provider for this protocol provider or null if such isn't specified
     */
    public ProtocolProviderService getLinkedCusaxProvider()
    {
        return null;
    }
}