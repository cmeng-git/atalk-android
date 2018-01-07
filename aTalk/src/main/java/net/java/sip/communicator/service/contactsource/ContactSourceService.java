/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license.
 * See terms of license at gnu.org.
 */
package net.java.sip.communicator.service.contactsource;

/**
 * The <tt>ContactSourceService</tt> interface is meant to be implemented by modules supporting
 * large lists of contacts and wanting to enable searching from other modules.
 *
 * @author Yana Stamcheva
 * @author Eng Chong Meng
 */
public interface ContactSourceService
{
    /**
     * Type of a default source.
     */
    int DEFAULT_TYPE = 0;

    /**
     * Type of a search source. Queried only when searches are performed.
     */
    int SEARCH_TYPE = 1;

    /**
     * Type of a history source. Queries only when history should be shown.
     */
    int HISTORY_TYPE = 2;
    
    /**
     * Type of a contact list source. Queries to be shown in the contact list.
     */
    int CONTACT_LIST_TYPE = 3;

    /**
     * Returns the type of this contact source.
     *
     * @return the type of this contact source
     */
    int getType();

    /**
     * Returns a user-friendly string that identifies this contact source.
     *
     * @return the display name of this contact source
     */
    String getDisplayName();

    /**
     * Creates and returns new <tt>ContactQuery</tt> instance.
     * 
     * @param queryString the string to search for
     * 
     * @return new <tt>ContactQuery</tt> instance.
     */
    ContactQuery createContactQuery(String queryString);
    
    /**
     * Creates and returns new <tt>ContactQuery</tt> instance.
     * 
     * @param queryString the string to search for
     * @param contactCount the maximum count of result contacts
     * @return new <tt>ContactQuery</tt> instance.
     */
    ContactQuery createContactQuery(String queryString, int contactCount);

    /**
     * Returns the index of the contact source in the result list.
     *
     * @return the index of the contact source in the result list
     */
    int getIndex();
}
