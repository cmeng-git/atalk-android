/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */

package net.java.sip.communicator.impl.muc;

import net.java.sip.communicator.service.contactsource.ContactQuery;
import net.java.sip.communicator.service.contactsource.ContactSourceService;

import org.atalk.android.R;
import org.atalk.android.aTalkApp;

/**
 * Contact source service for chat rooms.
 *
 * @author Hristo Terezov
 * @author Eng Chong Meng
 */
public class ChatRoomContactSourceService implements ContactSourceService
{
    /**
     * Returns the type of this contact source.
     *
     * @return the type of this contact source
     */
    public int getType()
    {
        return CONTACT_LIST_TYPE;
    }

    /**
     * Returns a user-friendly string that identifies this contact source.
     *
     * @return the display name of this contact source
     */
    public String getDisplayName()
    {
        return aTalkApp.getResString(R.string.chatroom);
    }

    /**
     * Creates query for the given <code>queryString</code>.
     *
     * @param queryString the string to search for
     * @return the created query
     */
    @Override
    public ContactQuery createContactQuery(String queryString)
    {
        return createContactQuery(queryString, -1);
    }

    /**
     * Creates query for the given <code>queryString</code>.
     *
     * @param queryString the string to search for
     * @param contactCount the maximum count of result contacts
     * @return the created query
     */
    @Override
    public ContactQuery createContactQuery(String queryString, int contactCount)
    {
        if (queryString == null)
            queryString = "";

        ChatRoomQuery contactQuery = new ChatRoomQuery(queryString, this);
        return contactQuery;
    }

    /**
     * Returns the index of the contact source in the result list.
     *
     * @return the index of the contact source in the result list
     */
    @Override
    public int getIndex()
    {
        return 1;
    }

}
