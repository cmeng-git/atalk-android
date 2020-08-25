/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package net.java.sip.communicator.impl.protocol.jabber;

import net.java.sip.communicator.service.protocol.ServerStoredDetails.*;

import org.jivesoftware.smack.XMPPConnection;
import org.jivesoftware.smack.packet.XmlEnvironment;
import org.jivesoftware.smackx.avatar.vcardavatar.VCardAvatarManager;
import org.jivesoftware.smackx.vcardtemp.packet.VCard;
import org.jxmpp.jid.BareJid;
import org.jxmpp.jid.EntityBareJid;

import java.net.MalformedURLException;
import java.net.URL;
import java.text.*;
import java.util.*;

import timber.log.Timber;

/**
 * Handles and retrieves all info of our contacts or account info
 *
 * @author Damian Minkov
 * @author Eng Chong Meng
 */
public class InfoRetriever
{
    /**
     * A callback to the Jabber provider that created us.
     */
    private ProtocolProviderServiceJabberImpl jabberProvider;

    // A linked list between contact/user and his details retrieved so far
    private final Map<BareJid, List<GenericDetail>> retrievedDetails = new Hashtable<>();

    private static final String TAG_FN_OPEN = "<FN>";
    private static final String TAG_FN_CLOSE = "</FN>";

    public static final String BDAY_FORMAT_MEDIUM = "MMM dd, yyyy";
    public static final String BDAY_FORMAT_SHORT = "yyyy-mm-dd";

    protected InfoRetriever(ProtocolProviderServiceJabberImpl jabberProvider, EntityBareJid ownerUin)
    {
        this.jabberProvider = jabberProvider;
    }

    /**
     * returns the user details from the specified class or its descendants the class is one from
     * the net.java.sip.communicator.service.protocol.ServerStoredDetails or implemented one in the
     * operation set for the user info
     *
     * @param uin String
     * @param detailClass Class
     * @return Iterator
     */
    <T extends GenericDetail> Iterator<T> getDetailsAndDescendants(BareJid uin, Class<T> detailClass)
    {
        List<GenericDetail> details = getUserDetails(uin);
        List<T> result = new LinkedList<>();

        for (GenericDetail item : details)
            if (detailClass.isInstance(item)) {
                @SuppressWarnings("unchecked")
                T t = (T) item;
                result.add(t);
            }
        return result.iterator();
    }

    /**
     * returns the user details from the specified class exactly that class not its descendants
     *
     * @param uin String
     * @param detailClass Class
     * @return Iterator
     */
    Iterator<GenericDetail> getDetails(BareJid uin, Class<? extends GenericDetail> detailClass)
    {
        List<GenericDetail> details = getUserDetails(uin);
        List<GenericDetail> result = new LinkedList<>();

        // stop further retrieve from server if details is null or empty to prevent ANR when return from Account Settings
        if ((details == null) || details.isEmpty()) {
            retrievedDetails.put(uin, result);
        }
        else {
            for (GenericDetail item : details)
                if (detailClass.equals(item.getClass()))
                    result.add(item);
        }
        return result.iterator();
    }

    /**
     * request the full info for the given bareJid waits and return this details
     *
     * @param bareJid String
     * @return Vector the details
     */
    List<GenericDetail> clearUserDetails(BareJid bareJid)
    {
        return retrievedDetails.remove(bareJid);
    }

    /**
     * request the full info for the given bareJid waits and return this details
     *
     * @param bareJid String
     * @return Vector the details
     */
    List<GenericDetail> getUserDetails(BareJid bareJid)
    {
        List<GenericDetail> result = getCachedUserDetails(bareJid);
        if (result == null) {
            return retrieveDetails(bareJid);
        }
        return result;
    }

    /**
     * Retrieve details and return them; return an empty list if none is found.
     * Note: Synchronized access to #retrieveDetails(BareJid bareJid) to prevent the retrieved
     * result is being overwritten by access from other Jid running on a separate thread
     * {@link ServerStoredContactListJabberImpl.ImageRetriever#run()}
     *
     * @param bareJid the address to search for.
     * @return the details or empty list.
     */
    protected synchronized List<GenericDetail> retrieveDetails(BareJid bareJid)
    {
        Timber.w(new Exception("Retrieve Details (debug-ignore): " + bareJid.toString()));

        List<GenericDetail> result = new LinkedList<>();
        XMPPConnection connection = jabberProvider.getConnection();
        if (connection == null || !connection.isAuthenticated())
            return null;

        // Set the timeout to wait before considering vCard has time out - too long field ANR - use default instead
        // connection.setReplyTimeout(ProtocolProviderServiceJabberImpl.SMACK_PACKET_REPLY_EXTENDED_TIMEOUT_30);

        Timber.d("Start loading VCard information for: %s", bareJid);
        VCardAvatarManager vCardAvatarManager = VCardAvatarManager.getInstanceFor(connection);
        VCard card = vCardAvatarManager.downloadVCard(bareJid);

        // Reset back to aTalk default
        // connection.setReplyTimeout(ProtocolProviderServiceJabberImpl.SMACK_PACKET_REPLY_TIMEOUT_10);

        // cmeng - vCard can be null due to smack request response timeout (2017/11/29)
        // return an empty list if VCard fetching from server failed
        if (card == null) {
            Timber.w("Failed to download Vcard from server!");
            return result;
        }

        String errMessage = "Unable to load details for contact " + bareJid + "; Exception: ";
        String tmp;
        tmp = checkForFullName(card);
        if (tmp != null)
            result.add(new DisplayNameDetail(tmp));

        tmp = card.getFirstName();
        if (tmp != null)
            result.add(new FirstNameDetail(tmp));

        tmp = card.getMiddleName();
        if (tmp != null)
            result.add(new MiddleNameDetail(tmp));

        tmp = card.getLastName();
        if (tmp != null)
            result.add(new LastNameDetail(tmp));

        tmp = card.getNickName();
        if (tmp != null)
            result.add(new NicknameDetail(tmp));

        tmp = card.getField("BDAY");
        if (tmp != null) {
            BirthDateDetail birthDateDetail;
            Date birthDate = null;
            try {
                DateFormat dateFormatMedium = new SimpleDateFormat(BDAY_FORMAT_MEDIUM, Locale.US);
                birthDate = dateFormatMedium.parse(tmp);
            } catch (ParseException ex) {
                try {
                    // take care of avatar date short format created by other clients i.e. 1992-01-03
                    DateFormat dateFormatShort = new SimpleDateFormat(BDAY_FORMAT_SHORT, Locale.US);
                    birthDate = dateFormatShort.parse(tmp);
                } catch (ParseException e) {
                    Timber.w("%s %s",errMessage, ex.getMessage());
                }
            }

            if (birthDate != null) {
                Calendar birthDateCalendar = Calendar.getInstance();
                birthDateCalendar.setTime(birthDate);
                birthDateDetail = new BirthDateDetail(birthDateCalendar);
            } else
                birthDateDetail = new BirthDateDetail(tmp);

            result.add(birthDateDetail);
        }
        // Home Details addrField one of: POSTAL, PARCEL, (DOM | INTL), PREF, POBOX, EXTADR,
        // STREET, LOCALITY, REGION, PCODE, CTRY
        tmp = card.getAddressFieldHome("STREET");
        if (tmp != null)
            result.add(new AddressDetail(tmp));

        tmp = card.getAddressFieldHome("LOCALITY");
        if (tmp != null)
            result.add(new CityDetail(tmp));

        tmp = card.getAddressFieldHome("REGION");
        if (tmp != null)
            result.add(new ProvinceDetail(tmp));

        tmp = card.getAddressFieldHome("PCODE");
        if (tmp != null)
            result.add(new PostalCodeDetail(tmp));

        tmp = card.getAddressFieldHome("CTRY");
        if (tmp != null)
            result.add(new CountryDetail(tmp));

        // phoneType one of
        // VOICE, FAX, PAGER, MSG, CELL, VIDEO, BBS, MODEM, ISDN, PCS, PREF

        tmp = card.getPhoneHome("VOICE");
        if (tmp != null)
            result.add(new PhoneNumberDetail(tmp));

        tmp = card.getPhoneHome("VIDEO");
        if (tmp != null)
            result.add(new VideoDetail(tmp));

        tmp = card.getPhoneHome("FAX");
        if (tmp != null)
            result.add(new FaxDetail(tmp));

        tmp = card.getPhoneHome("PAGER");
        if (tmp != null)
            result.add(new PagerDetail(tmp));

        tmp = card.getPhoneHome("CELL");
        if (tmp != null)
            result.add(new MobilePhoneDetail(tmp));

        tmp = card.getPhoneHome("TEXT");
        if (tmp != null)
            result.add(new MobilePhoneDetail(tmp));

        tmp = card.getEmailHome();
        if (tmp != null)
            result.add(new EmailAddressDetail(tmp));

        // Work Details addrField one of
        // POSTAL, PARCEL, (DOM | INTL), PREF, POBOX, EXTADR, STREET, LOCALITY, REGION, PCODE,
        // CTRY
        tmp = card.getAddressFieldWork("STREET");
        if (tmp != null)
            result.add(new WorkAddressDetail(tmp));

        tmp = card.getAddressFieldWork("LOCALITY");
        if (tmp != null)
            result.add(new WorkCityDetail(tmp));

        tmp = card.getAddressFieldWork("REGION");
        if (tmp != null)
            result.add(new WorkProvinceDetail(tmp));

        tmp = card.getAddressFieldWork("PCODE");
        if (tmp != null)
            result.add(new WorkPostalCodeDetail(tmp));

        // tmp = card.getAddressFieldWork("CTRY");
        // if(tmp != null)
        // 	result.add(new WorkCountryDetail(tmp);

        // phoneType one of
        // VOICE, FAX, PAGER, MSG, CELL, VIDEO, BBS, MODEM, ISDN, PCS, PREF
        tmp = card.getPhoneWork("VOICE");
        if (tmp != null)
            result.add(new WorkPhoneDetail(tmp));

        tmp = card.getPhoneWork("VIDEO");
        if (tmp != null)
            result.add(new WorkVideoDetail(tmp));

        tmp = card.getPhoneWork("FAX");
        if (tmp != null)
            result.add(new WorkFaxDetail(tmp));

        tmp = card.getPhoneWork("PAGER");
        if (tmp != null)
            result.add(new WorkPagerDetail(tmp));

        tmp = card.getPhoneWork("CELL");
        if (tmp != null)
            result.add(new WorkMobilePhoneDetail(tmp));

        tmp = card.getPhoneWork("TEXT");
        if (tmp != null)
            result.add(new WorkMobilePhoneDetail(tmp));

        tmp = card.getEmailWork();
        if (tmp != null)
            result.add(new WorkEmailAddressDetail(tmp));

        tmp = card.getOrganization();
        if (tmp != null)
            result.add(new WorkOrganizationNameDetail(tmp));

        tmp = card.getOrganizationUnit();
        if (tmp != null)
            result.add(new WorkDepartmentNameDetail(tmp));

        tmp = card.getField("TITLE");
        if (tmp != null)
            result.add(new JobTitleDetail(tmp));

        tmp = card.getField("ABOUTME");
        if (tmp != null)
            result.add(new AboutMeDetail(tmp));

        // cmeng: it is normal for packet.EmptyResultIQ when contact does not have avatar uploaded
        byte[] imageBytes = card.getAvatar();
        if (imageBytes != null && imageBytes.length > 0) {
            result.add(new ImageDetail("Image", imageBytes));
        }

        // add as string context if not a valid URL
        tmp = card.getField("URL");
        try {
            if (tmp != null)
                result.add(new URLDetail("URL", new URL(tmp)));
        } catch (MalformedURLException ex) {
            result.add(new URLDetail("URL", tmp));
            Timber.w("%s %s", errMessage, ex.getMessage());
        }

        retrievedDetails.put(bareJid, result);
        Timber.i("Added retrievedDetails for: %s; size: %s", bareJid, result.size());
        return result;
    }

    /**
     * request the full info for the given bareJid if available in cache.
     *
     * @param bareJid to search for
     * @return list of the details if any.
     */
    List<GenericDetail> getCachedUserDetails(BareJid bareJid)
    {
        return retrievedDetails.get(bareJid);
    }

    /**
     * Adds a cached contact details.
     *
     * @param bareJid the contact address
     * @param details the details to add
     */
    void addCachedUserDetails(BareJid bareJid, List<GenericDetail> details)
    {
        retrievedDetails.put(bareJid, details);
    }

    /**
     * Checks for full name tag in the <tt>card</tt>.
     *
     * @param card the card to check.
     * @return the Full name if existing, null otherwise.
     */
    String checkForFullName(VCard card)
    {
        String vcardXml = card.toXML(XmlEnvironment.EMPTY).toString();
        int indexOpen = vcardXml.indexOf(TAG_FN_OPEN);

        if (indexOpen == -1)
            return null;

        int indexClose = vcardXml.indexOf(TAG_FN_CLOSE, indexOpen);

        // something is wrong!
        if (indexClose == -1)
            return null;
        return vcardXml.substring(indexOpen + TAG_FN_OPEN.length(), indexClose);
    }

    /**
     * Work department
     */
    public static class WorkDepartmentNameDetail extends NameDetail
    {
        /**
         * Constructor.
         *
         * @param workDepartmentName name of the work department
         */
        public WorkDepartmentNameDetail(String workDepartmentName)
        {
            super("Work Department Name", workDepartmentName);
        }
    }

    /**
     * Fax at work
     */
    public static class WorkFaxDetail extends FaxDetail
    {
        /**
         * Constructor.
         *
         * @param number work fax number
         */
        public WorkFaxDetail(String number)
        {
            super(number);
            super.detailDisplayName = "WorkFax";
        }
    }

    /**
     * Pager at work
     */
    public static class WorkPagerDetail extends PhoneNumberDetail
    {
        /**
         * Constructor.
         *
         * @param number work pager number
         */
        public WorkPagerDetail(String number)
        {
            super(number);
            super.detailDisplayName = "WorkPager";
        }
    }
}
