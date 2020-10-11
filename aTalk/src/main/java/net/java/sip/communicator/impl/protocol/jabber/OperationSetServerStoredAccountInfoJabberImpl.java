/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package net.java.sip.communicator.impl.protocol.jabber;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;

import net.java.sip.communicator.service.protocol.*;
import net.java.sip.communicator.service.protocol.ServerStoredDetails.*;
import net.java.sip.communicator.service.protocol.event.ServerStoredDetailsChangeEvent;

import org.atalk.android.R;
import org.atalk.android.aTalkApp;
import org.jivesoftware.smack.SmackException.NoResponseException;
import org.jivesoftware.smack.SmackException.NotConnectedException;
import org.jivesoftware.smack.XMPPConnection;
import org.jivesoftware.smack.XMPPException;
import org.jivesoftware.smackx.avatar.useravatar.UserAvatarManager;
import org.jivesoftware.smackx.avatar.vcardavatar.VCardAvatarManager;
import org.jivesoftware.smackx.vcardtemp.packet.VCard;
import org.jxmpp.jid.BareJid;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.*;

import timber.log.Timber;

/**
 * The Account Info Operation set is a means of accessing and modifying detailed information on the
 * user/account that is currently logged in through this provider.
 *
 * @author Damian Minkov
 * @author Marin Dzhigarov
 * @author Hristo Terezov
 * @author Eng Chong Meng
 */
public class OperationSetServerStoredAccountInfoJabberImpl extends AbstractOperationSetServerStoredAccountInfo
{
    /**
     * The info retriever.
     */
    private InfoRetriever infoRetriever;

    /**
     * The jabber provider that created us.
     */
    private ProtocolProviderServiceJabberImpl jabberProvider;

    /**
     * List of all supported <tt>ServerStoredDetails</tt> for this implementation.
     */
    public static final List<Class<? extends GenericDetail>> supportedTypes = new ArrayList<>();

    static {
        supportedTypes.add(ImageDetail.class);
        supportedTypes.add(FirstNameDetail.class);
        supportedTypes.add(MiddleNameDetail.class);
        supportedTypes.add(LastNameDetail.class);
        supportedTypes.add(NicknameDetail.class);
        supportedTypes.add(AddressDetail.class);
        supportedTypes.add(CityDetail.class);
        supportedTypes.add(ProvinceDetail.class);
        supportedTypes.add(PostalCodeDetail.class);
        supportedTypes.add(CountryDetail.class);
        supportedTypes.add(EmailAddressDetail.class);
        supportedTypes.add(WorkEmailAddressDetail.class);
        supportedTypes.add(PhoneNumberDetail.class);
        supportedTypes.add(WorkPhoneDetail.class);
        supportedTypes.add(MobilePhoneDetail.class);
        supportedTypes.add(VideoDetail.class);
        supportedTypes.add(WorkVideoDetail.class);
        supportedTypes.add(WorkOrganizationNameDetail.class);
        supportedTypes.add(URLDetail.class);
        supportedTypes.add(BirthDateDetail.class);
        supportedTypes.add(JobTitleDetail.class);
        supportedTypes.add(AboutMeDetail.class);
    }

    /**
     * Our account UIN.
     */
    private BareJid uin;

    protected OperationSetServerStoredAccountInfoJabberImpl(
            ProtocolProviderServiceJabberImpl jabberProvider, InfoRetriever infoRetriever, BareJid uin)
    {
        this.infoRetriever = infoRetriever;
        this.jabberProvider = jabberProvider;
        this.uin = uin;
    }

    /**
     * Returns an iterator over all details that are instances or descendants of the specified
     * class. If for example an our account has a work address and an address detail, a call to
     * this method with AddressDetail.class would return both of them.
     * <p>
     *
     * @param detailClass one of the detail classes defined in the ServerStoredDetails class, indicating the
     * kind of details we're interested in.
     * <p>
     * @return a java.util.Iterator over all details that are instances or descendants of the
     * specified class.
     */
    public <T extends GenericDetail> Iterator<T> getDetailsAndDescendants(Class<T> detailClass)
    {
        return (assertConnected()) ? infoRetriever.getDetailsAndDescendants(uin, detailClass) : null;
    }

    /**
     * Returns an iterator over all details that are instances of exactly the same class as the
     * one specified. Not that, contrary to the getDetailsAndDescendants() method this one
     * would only return details that are instances of the specified class and not only its
     * descendants. If for example our account has both a work address and an address detail,
     * a call to this method with AddressDetail.class would return only the AddressDetail
     * instance and not the WorkAddressDetail instance.
     * <p>
     *
     * @param detailClass one of the detail classes defined in the ServerStoredDetails class, indicating the
     * kind of details we're interested in.
     * <p>
     * @return a java.util.Iterator over all details of specified class.
     */
    public Iterator<GenericDetail> getDetails(Class<? extends GenericDetail> detailClass)
    {
        return (assertConnected()) ? infoRetriever.getDetails(uin, detailClass) : null;
    }

    /**
     * Returns all details currently available and set for our account.
     * <p>
     *
     * @return a java.util.Iterator over all details currently set our account.
     */
    public Iterator<GenericDetail> getAllAvailableDetails()
    {
        return (assertConnected()) ? infoRetriever.getUserDetails(uin).iterator() : null;
    }

    /**
     * Clear all details for account when it logoff; to allow refresh on next login
     */
    public void clearDetails()
    {
        infoRetriever.clearUserDetails(uin);
    }

    /**
     * Returns all detail Class-es that the underlying implementation supports setting. Note
     * that if you call one of the modification methods (add remove or replace) with a detail not
     * contained by the iterator returned by this method, an IllegalArgumentException will be thrown.
     * <p>
     *
     * @return a java.util.Iterator over all detail classes supported by the implementation.
     */
    public Iterator<Class<? extends GenericDetail>> getSupportedDetailTypes()
    {
        return supportedTypes.iterator();
    }

    /**
     * Determines whether a detail class represents a detail supported by the underlying
     * implementation or not. Note that if you call one of the modification methods (add remove or
     * replace) with a detail that this method has determined to be unsupported (returned false)
     * this would lead to an IllegalArgumentException being thrown.
     * <p>
     *
     * @param detailClass the class the support for which we'd like to determine.
     * <p>
     * @return true if the underlying implementation supports setting details of this type and false otherwise.
     */
    public boolean isDetailClassSupported(Class<? extends GenericDetail> detailClass)
    {
        return supportedTypes.contains(detailClass);
    }

    /**
     * The method returns the number of instances supported for a particular detail type. Some
     * protocols offer storing multiple values for a particular detail type. Spoken languages are a
     * good example.
     *
     * @param detailClass the class whose max instance number we'd like to find out.
     * <p>
     * @return int the maximum number of detail instances.
     */
    public int getMaxDetailInstances(Class<? extends GenericDetail> detailClass)
    {
        return 1;
    }

    /**
     * Adds the specified detail to the list of details ready to be saved online for this account.
     * If such a detail already exists its max instance number is consulted and if it allows it - a
     * second instance is added or otherwise and illegal argument exception is thrown. An
     * IllegalArgumentException is also thrown in case the class of the specified detail is not
     * supported by the underlying implementation, i.e. its class name was not returned by the
     * getSupportedDetailTypes() method.
     * <p>
     *
     * @param detail the detail that we'd like registered on the server.
     * <p>
     * @throws IllegalArgumentException if such a detail already exists and its max instances number has been attained or if
     * the underlying implementation does not support setting details of the corresponding
     * class.
     * @throws java.lang.ArrayIndexOutOfBoundsException if the number of instances currently registered by the application is already equal
     * to the maximum number of supported instances (@see #getMaxDetailInstances())
     */
    public void addDetail(ServerStoredDetails.GenericDetail detail)
            throws IllegalArgumentException, ArrayIndexOutOfBoundsException
    {
        if (!isDetailClassSupported(detail.getClass())) {
            throw new IllegalArgumentException("implementation does not support such details " + detail.getClass());
        }

        Iterator<GenericDetail> iter = getDetails(detail.getClass());
        int currentDetailsSize = 0;
        while (iter.hasNext()) {
            currentDetailsSize++;
            iter.next();
        }

        if (currentDetailsSize > getMaxDetailInstances(detail.getClass())) {
            throw new ArrayIndexOutOfBoundsException("Max count for this detail is already reached");
        }
        infoRetriever.getCachedUserDetails(uin).add(detail);
    }

    /**
     * Removes the specified detail from the list of details ready to be saved online this account.
     * The method returns a boolean indicating if such a detail was found (and removed) or not.
     * <p>
     *
     * @param detail the detail to remove
     * @return true if the specified detail existed and was successfully removed and false otherwise.
     */
    public boolean removeDetail(ServerStoredDetails.GenericDetail detail)
    {
        return infoRetriever.getCachedUserDetails(uin).remove(detail);
    }

    /**
     * Replaces the currentDetailValue detail with newDetailValue and returns true if the operation
     * was a success or false if currentDetailValue did not previously exist (in this case an
     * additional call to addDetail is required).
     * <p>
     *
     * @param currentDetailValue the detail value we'd like to replace.
     * @param newDetailValue the value of the detail that we'd like to replace currentDetailValue with.
     * @return true if the operation was a success or false if currentDetailValue did not
     * previously exist (in this case an additional call to addDetail is required).
     * @throws ClassCastException if newDetailValue is not an instance of the same class as currentDetailValue.
     */
    public boolean replaceDetail(ServerStoredDetails.GenericDetail currentDetailValue,
            ServerStoredDetails.GenericDetail newDetailValue)
            throws ClassCastException
    {
        if (!newDetailValue.getClass().equals(currentDetailValue.getClass())) {
            throw new ClassCastException("New value to be replaced is not as the current one");
        }
        // if values are the same no change
        if (currentDetailValue.equals(newDetailValue)) {
            return true;
        }

        boolean isFound = false;
        Iterator<GenericDetail> iter = infoRetriever.getDetails(uin, currentDetailValue.getClass());
        while (iter.hasNext()) {
            GenericDetail item = iter.next();
            if (item.equals(currentDetailValue)) {
                isFound = true;
                break;
            }
        }
        // current detail value does not exist
        if (!isFound) {
            return false;
        }
        removeDetail(currentDetailValue);
        addDetail(newDetailValue);
        return true;
    }

    /**
     * Saves the list of details for this account that were ready to be stored online on the
     * server. This method performs the actual saving of details online on the server and is
     * supposed to be invoked after addDetail(), replaceDetail() and/or removeDetail().
     * <p>
     *
     * @throws OperationFailedException with code Network Failure if putting the new values back online has failed.
     */
    public void save()
            throws OperationFailedException
    {
        if (!assertConnected())
            return;

        XMPPConnection xmppConnection = jabberProvider.getConnection();
        UserAvatarManager userAvatarManager = UserAvatarManager.getInstanceFor(xmppConnection);
        VCardAvatarManager vCardAvatarMgr = VCardAvatarManager.getInstanceFor(xmppConnection);

        // modify our reply timeout because some server may send "result" IQ late (> 5 seconds).
        xmppConnection.setReplyTimeout(ProtocolProviderServiceJabberImpl.SMACK_REPLY_EXTENDED_TIMEOUT_40);

        List<GenericDetail> details = infoRetriever.getUserDetails(uin);
        VCard vCard = new VCard();
        for (GenericDetail detail : details) {
            if (detail instanceof ImageDetail) {
                // disable XEP-0153 vcard <photo/> hash; instead publish avatar via XEP-0084
                // vCard.removeAvatar(); // cmeng - need to keep to support old client (XEP-0054)
                byte[] avatar = ((ImageDetail) detail).getBytes();
                if ((avatar != null) && (avatar.length > 0)) {
                    vCard.setAvatar(avatar); // for XEP-0054
                    Bitmap bitmap = BitmapFactory.decodeByteArray(avatar, 0, avatar.length);
                    userAvatarManager.publishAvatar(bitmap);
                }
                else {
                    // vCard.removeAvatar(); cause crash in Smack 4.2.1-beta2-snapshot muc setup
                    vCard.setAvatar(new byte[0]);  // for XEP-0054
                    userAvatarManager.disableAvatarPublishing();
                }
                fireServerStoredDetailsChangeEvent(jabberProvider,
                        ServerStoredDetailsChangeEvent.DETAIL_ADDED, null, detail);
            }
            else if (detail.getClass().equals(FirstNameDetail.class)) {
                vCard.setFirstName((String) detail.getDetailValue());
            }
            else if (detail.getClass().equals(MiddleNameDetail.class)) {
                vCard.setMiddleName((String) detail.getDetailValue());
            }
            else if (detail.getClass().equals(LastNameDetail.class)) {
                vCard.setLastName((String) detail.getDetailValue());
            }
            else if (detail.getClass().equals(NicknameDetail.class))
                vCard.setNickName((String) detail.getDetailValue());
            else if (detail.getClass().equals(URLDetail.class)) {
                if (detail.getDetailValue() != null)
                    vCard.setField("URL", detail.getDetailValue().toString());
            }
            else if (detail.getClass().equals(BirthDateDetail.class)) {
                if (detail.getDetailValue() != null) {
                    String strdate;
                    Object date = ((BirthDateDetail) detail).getCalendar();
                    if (date instanceof Calendar) {
                        DateFormat dateFormat = new SimpleDateFormat(InfoRetriever.BDAY_FORMAT_MEDIUM, Locale.US);
                        strdate = dateFormat.format(((Calendar) date).getTime());
                    }
                    else {
                        strdate = (String) date;
                    }
                    vCard.setField("BDAY", strdate);
                }
            }
            else if (detail.getClass().equals(AddressDetail.class))
                vCard.setAddressFieldHome("STREET", (String) detail.getDetailValue());
            else if (detail.getClass().equals(CityDetail.class))
                vCard.setAddressFieldHome("LOCALITY", (String) detail.getDetailValue());
            else if (detail.getClass().equals(ProvinceDetail.class))
                vCard.setAddressFieldHome("REGION", (String) detail.getDetailValue());
            else if (detail.getClass().equals(PostalCodeDetail.class))
                vCard.setAddressFieldHome("PCODE", (String) detail.getDetailValue());
            else if (detail.getClass().equals(CountryDetail.class))
                vCard.setAddressFieldHome("CTRY", (String) detail.getDetailValue());
            else if (detail.getClass().equals(PhoneNumberDetail.class))
                vCard.setPhoneHome("VOICE", (String) detail.getDetailValue());
            else if (detail.getClass().equals(WorkPhoneDetail.class))
                vCard.setPhoneWork("VOICE", (String) detail.getDetailValue());
            else if (detail.getClass().equals(MobilePhoneDetail.class))
                vCard.setPhoneHome("CELL", (String) detail.getDetailValue());
            else if (detail.getClass().equals(VideoDetail.class))
                vCard.setPhoneHome("VIDEO", (String) detail.getDetailValue());
            else if (detail.getClass().equals(WorkVideoDetail.class))
                vCard.setPhoneWork("VIDEO", (String) detail.getDetailValue());
            else if (detail.getClass().equals(EmailAddressDetail.class))
                vCard.setEmailHome((String) detail.getDetailValue());
            else if (detail.getClass().equals(WorkEmailAddressDetail.class))
                vCard.setEmailWork((String) detail.getDetailValue());
            else if (detail.getClass().equals(WorkOrganizationNameDetail.class))
                vCard.setOrganization((String) detail.getDetailValue());
            else if (detail.getClass().equals(JobTitleDetail.class))
                vCard.setField("TITLE", (String) detail.getDetailValue());
            else if (detail.getClass().equals(AboutMeDetail.class))
                vCard.setField("ABOUTME", (String) detail.getDetailValue());
        }

        // Fix the display name detail
        String tmp;
        tmp = infoRetriever.checkForFullName(vCard);
        if (tmp != null) {
            DisplayNameDetail displayNameDetail = new DisplayNameDetail(tmp);
            Iterator<GenericDetail> detailIt = infoRetriever.getDetails(uin, DisplayNameDetail.class);
            while (detailIt.hasNext()) {
                infoRetriever.getCachedUserDetails(uin).remove(detailIt.next());
            }
            infoRetriever.getCachedUserDetails(uin).add(displayNameDetail);
        }

        try {
            // saveVCard() via VCardAvatarManager to support XEP-0153
            if (vCardAvatarMgr.saveVCard(vCard)) {
                // need to send new <presence/> ?
            }
        } catch (XMPPException | NoResponseException | NotConnectedException
                | InterruptedException e) {
            Timber.e(e, "Error loading/saving vcard");
            throw new OperationFailedException("Error loading/saving vcard: ", 1, e);
        } finally {
            // Reset to default
            xmppConnection.setReplyTimeout(ProtocolProviderServiceJabberImpl.SMACK_REPLY_TIMEOUT_DEFAULT);
        }
    }

    /**
     * Determines whether the underlying implementation supports edition of this detail class.
     * <p>
     *
     * @param detailClass the class whose edition we'd like to determine if it's possible
     * @return true if the underlying implementation supports edition of this type of detail and false otherwise.
     */
    public boolean isDetailClassEditable(Class<? extends GenericDetail> detailClass)
    {
        return isDetailClassSupported(detailClass);
    }

    /**
     * Utility method throwing an exception if the jabber stack is not properly initialized.
     *
     * cmeng - never throw exception else it crashes the application.
     * throw java.lang.IllegalStateException if the underlying jabber stack is not registered and
     * initialized.
     */
    private boolean assertConnected()
    {
        if (((jabberProvider == null) || !jabberProvider.isRegistered())) {
            Timber.w(aTalkApp.getResString(R.string.service_gui_NETOWRK_ASSERTION_ERROR));
            return false;
        }
        return true;
    }
}