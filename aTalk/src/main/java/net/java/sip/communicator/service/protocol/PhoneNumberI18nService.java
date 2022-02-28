/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package net.java.sip.communicator.service.protocol;

/**
 * Implements <code>PhoneNumberI18nService</code> which aids the parsing, formatting
 * and validating of international phone numbers.
 *
 * @author Lyubomir Marinov
 * @author Vincent Lucas
 * @author Damian Minkov
 * @author Eng Chong Meng
 */
public interface PhoneNumberI18nService
{
    /**
     * Normalizes a <code>String</code> which may be a phone number or a identifier by removing useless
     * characters and, if necessary, replacing the alpahe characters in corresponding dial pad
     * numbers.
     *
     * @param possibleNumber a <code>String</code> which may represents a phone number or an identifier to normalize.
     * @return a <code>String</code> which is a normalized form of the specified <code>possibleNumber</code>
     * .
     */
    String normalize(String possibleNumber);

    /**
     * Tries to format the passed phone number into the international format. If
     * parsing fails or the string is not recognized as a valid phone number,
     * the input is returned as is.
     *
     * @param phoneNumber The phone number to format.
     * @return the formatted phone number in the international format.
     */
    String formatForDisplay(String phoneNumber);

    /**
     * Determines whether two <code>String</code> phone numbers match.
     *
     * @param aPhoneNumber a <code>String</code> which represents a phone number to match to <code>bPhoneNumber</code>
     * @param bPhoneNumber a <code>String</code> which represents a phone number to match to <code>aPhoneNumber</code>
     * @return <code>true</code> if the specified <code>String</code>s match as phone numbers; otherwise,
     * <code>false</code>
     */
    public boolean phoneNumbersMatch(String aPhoneNumber, String bPhoneNumber);

    /**
     * Indicates if the given string is possibly a phone number.
     *
     * @param possibleNumber the string to be verified
     * @return <code>true</code> if the possibleNumber is a phone number, <code>false</code> - otherwise
     */
    boolean isPhoneNumber(String possibleNumber);
}
