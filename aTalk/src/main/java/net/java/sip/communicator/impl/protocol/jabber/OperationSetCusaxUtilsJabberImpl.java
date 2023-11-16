/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package net.java.sip.communicator.impl.protocol.jabber;

import net.java.sip.communicator.service.protocol.Contact;
import net.java.sip.communicator.service.protocol.OperationSetCusaxUtils;
import net.java.sip.communicator.service.protocol.ProtocolProviderService;
import net.java.sip.communicator.util.call.ContactPhoneUtil;

import java.util.Iterator;
import java.util.List;

/**
 * The <code>OperationSetCusaxUtilsJabberImpl</code> provides utility methods related to the Jabber CUSAX implementation.
 *
 * @author Yana Stamcheva
 */
public class OperationSetCusaxUtilsJabberImpl implements OperationSetCusaxUtils
{
    /**
     * Checks if the given <code>detailAddress</code> exists in the given <code>contact</code> details.
     *
     * @param contact the <code>Contact</code>, which details to check
     * @param detailAddress the detail address we're looking for
     * @return <code>true</code> if the given <code>detailAdress</code> exists in the details of the given <code>contact</code>
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