/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package net.java.sip.communicator.service.protocol;

import net.java.sip.communicator.service.protocol.ServerStoredDetails.*;

import java.util.Iterator;

/**
 * Utility class that would give to interested parties an easy access to some of most popular
 * account details, like : first name, last name, birth date, image, etc.
 *
 * @author Yana Stamcheva
 * @author Eng Chong Meng
 */
public class AccountInfoUtils
{
    /**
     * Returns the first name of the account, to which the given accountInfoOpSet belongs.
     *
     * @param accountInfoOpSet The account info operation set corresponding to the searched account.
     * @return the first name of the account, to which the given accountInfoOpSet belongs.
     */
    public static String getFirstName(OperationSetServerStoredAccountInfo accountInfoOpSet)
    {
        FirstNameDetail firstName = null;
        Iterator<GenericDetail> firstNameDetails = accountInfoOpSet.getDetails(FirstNameDetail.class);

        if ((firstNameDetails != null) && firstNameDetails.hasNext())
            firstName = (FirstNameDetail) firstNameDetails.next();

        return (firstName != null) ? firstName.toString() : null;
    }

    /**
     * Returns the last name of the account, to which the given accountInfoOpSet belongs.
     *
     * @param accountInfoOpSet The account info operation set corresponding to the searched account.
     * @return the last name of the account, to which the given accountInfoOpSet belongs.
     */
    public static String getLastName(OperationSetServerStoredAccountInfo accountInfoOpSet)
    {
        LastNameDetail lastName = null;
        Iterator<GenericDetail> lastNameDetails = accountInfoOpSet.getDetails(LastNameDetail.class);

        if ((lastNameDetails != null) && lastNameDetails.hasNext())
            lastName = (LastNameDetail) lastNameDetails.next();

        return (lastName != null) ? lastName.getString() : null;
    }

    /**
     * Returns the display name of the account, to which the given accountInfoOpSet belongs.
     *
     * @param accountInfoOpSet The account info operation set corresponding to the searched account.
     * @return the display name of the account, to which the given accountInfoOpSet belongs.
     */
    public static String getDisplayName(OperationSetServerStoredAccountInfo accountInfoOpSet)
    {
        DisplayNameDetail displayName = null;
        Iterator<GenericDetail> displayNameDetails = accountInfoOpSet.getDetails(DisplayNameDetail.class);

        if ((displayNameDetails != null) && displayNameDetails.hasNext())
            displayName = (DisplayNameDetail) displayNameDetails.next();

        return (displayName != null) ? displayName.getString() : null;
    }

    /**
     * Returns the image of the account, to which the given accountInfoOpSet belongs.
     *
     * @param accountInfoOpSet The account info operation set corresponding to the searched account.
     * @return the image of the account, to which the given accountInfoOpSet belongs.
     */
    public static byte[] getImage(OperationSetServerStoredAccountInfo accountInfoOpSet)
    {
        ImageDetail image = null;
        Iterator<GenericDetail> imageDetails = accountInfoOpSet.getDetails(ImageDetail.class);

        if ((imageDetails != null) && imageDetails.hasNext())
            image = (ImageDetail) imageDetails.next();
        return (image != null) ? image.getBytes() : null;
    }

    /**
     * Returns the birth date of the account, to which the given accountInfoOpSet belongs.
     *
     * @param accountInfoOpSet The account info operation set corresponding to the searched account.
     * @return the birth date of the account, to which the given accountInfoOpSet belongs.
     */
    public static Object getBirthDate(OperationSetServerStoredAccountInfo accountInfoOpSet)
    {
        BirthDateDetail date = null;
        Iterator<GenericDetail> dateDetails = accountInfoOpSet.getDetails(BirthDateDetail.class);
        if ((dateDetails != null) && dateDetails.hasNext())
            date = (BirthDateDetail) dateDetails.next();

        return (date != null) ? date.getCalendar() : null;
    }

    /**
     * Returns the gender of the account, to which the given accountInfoOpSet belongs.
     *
     * @param accountInfoOpSet The account info operation set corresponding to the searched account.
     * @return the gender of the account, to which the given accountInfoOpSet belongs.
     */
    public static String getGender(OperationSetServerStoredAccountInfo accountInfoOpSet)
    {
        GenderDetail gender = null;
        Iterator<GenericDetail> genderDetails = accountInfoOpSet.getDetails(GenderDetail.class);

        if (genderDetails != null && genderDetails.hasNext()) {
            gender = (GenderDetail) genderDetails.next();
        }
        return (gender != null) ? gender.getGender() : null;
    }

    /**
     * Returns the address of the account, to which the given accountInfoOpSet belongs.
     *
     * @param accountInfoOpSet The account info operation set corresponding to the searched account.
     * @return the address of the account, to which the given accountInfoOpSet belongs.
     */
    public static String getAddress(OperationSetServerStoredAccountInfo accountInfoOpSet)
    {
        AddressDetail address = null;
        Iterator<GenericDetail> addressDetails = accountInfoOpSet.getDetails(AddressDetail.class);

        if ((addressDetails != null) && addressDetails.hasNext())
            address = (AddressDetail) addressDetails.next();

        return (address != null) ? address.getAddress() : null;
    }

    /**
     * Returns the work address of the account, to which the given accountInfoOpSet belongs.
     *
     * @param accountInfoOpSet The account info operation set corresponding to the searched account.
     * @return the work address of the account, to which the given accountInfoOpSet belongs.
     */
    public static String getWorkAddress(OperationSetServerStoredAccountInfo accountInfoOpSet)
    {
        WorkAddressDetail address = null;
        Iterator<GenericDetail> addressDetails
                = accountInfoOpSet.getDetails(WorkAddressDetail.class);

        if ((addressDetails != null) && addressDetails.hasNext())
            address = (WorkAddressDetail) addressDetails.next();

        return (address != null) ? address.getAddress() : null;
    }

    /**
     * Returns the email address of the account, to which the given accountInfoOpSet belongs.
     *
     * @param accountInfoOpSet The account info operation set corresponding to the searched account.
     * @return the email address of the account, to which the given accountInfoOpSet belongs.
     */
    public static String getEmailAddress(OperationSetServerStoredAccountInfo accountInfoOpSet)
    {
        EmailAddressDetail address = null;
        Iterator<GenericDetail> addressDetails
                = accountInfoOpSet.getDetails(EmailAddressDetail.class);

        if ((addressDetails != null) && addressDetails.hasNext())
            address = (EmailAddressDetail) addressDetails.next();

        return (address != null) ? address.getEMailAddress() : null;
    }
}
