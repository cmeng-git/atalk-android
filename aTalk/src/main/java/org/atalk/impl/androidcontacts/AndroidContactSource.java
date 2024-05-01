/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.atalk.impl.androidcontacts;

import net.java.sip.communicator.service.contactsource.ContactQuery;
import net.java.sip.communicator.service.contactsource.ExtendedContactSourceService;
import net.java.sip.communicator.service.contactsource.PrefixedContactSourceService;

import org.atalk.android.R;
import org.atalk.android.aTalkApp;

import java.util.regex.Pattern;

/**
 * Android contact source implementation.
 *
 * @author Pawel Domas
 * @author Eng Chong Meng
 */
public class AndroidContactSource implements ExtendedContactSourceService, PrefixedContactSourceService
{
    /**
     * Queries this search source for the given <code>searchPattern</code>.
     *
     * @param queryPattern the pattern to search for
     * @return the created query
     */
    @Override
    public ContactQuery createContactQuery(Pattern queryPattern)
    {
        return new AndroidContactQuery(this, "%" + queryPattern.toString() + "%");
    }

    /**
     * Queries this search source for the given <code>query</code>.
     *
     * @param query the string to search for
     * @return the created query
     */
    @Override
    public ContactQuery createContactQuery(String query)
    {
        return createContactQuery(Pattern.compile(query, Pattern.CASE_INSENSITIVE | Pattern.LITERAL));
    }

    /**
     * Queries this search source for the given <code>query</code>.
     *
     * @param query the string to search for
     * @param contactCount the maximum count of result contacts
     * @return the created query
     */
    @Override
    public ContactQuery createContactQuery(String query, int contactCount)
    {
        return createContactQuery(Pattern.compile(query, Pattern.CASE_INSENSITIVE | Pattern.LITERAL));
    }

    /**
     * Returns the global phone number prefix to be used when calling contacts from this contact source.
     *
     * @return the global phone number prefix
     */
    @Override
    public String getPhoneNumberPrefix()
    {
        return null;
        // return AddrBookActivator.getConfigService().getString(OUTLOOK_ADDR_BOOK_PREFIX);
    }

    /**
     * Returns the type of this contact source.
     *
     * @return the type of this contact source
     */
    @Override
    public int getType()
    {
        return SEARCH_TYPE;
    }

    /**
     * Returns a user-friendly string that identifies this contact source.
     *
     * @return the display name of this contact source
     */
    @Override
    public String getDisplayName()
    {
        return aTalkApp.getResString(R.string.phonebook);
    }

    /**
     * Returns the index of the contact source in the result list.
     *
     * @return the index of the contact source in the result list
     */
    @Override
    public int getIndex()
    {
        return -1;
    }
}
