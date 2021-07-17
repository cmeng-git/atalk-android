/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package net.java.sip.communicator.impl.protocol.jabber;

import net.java.sip.communicator.service.protocol.Contact;

import java.util.Collections;
import java.util.Iterator;

/**
 * The Jabber implementation of the Volatile ContactGroup interface.
 *
 * @author Damian Minkov
 * @author Eng Chong Meng
 */
public class VolatileContactGroupJabberImpl extends ContactGroupJabberImpl
{
    /**
     * This contact group name
     */
    private final String contactGroupName;

    /**
     * Creates an Jabber group using the specified group name
     *
     * @param groupName String groupName
     * @param ssclCallback a callback to the server stored contact list we're creating.
     */
    VolatileContactGroupJabberImpl(String groupName, ServerStoredContactListJabberImpl ssclCallback)
    {
        super(null, Collections.emptyIterator(), ssclCallback, false);
        this.contactGroupName = groupName;
    }

    /**
     * Returns the name of this group.
     *
     * @return a String containing the name of this group.
     */
    @Override
    public String getGroupName()
    {
        return contactGroupName;
    }

    /**
     * Returns a string representation of this group, in the form
     * JabberGroup.GroupName[size]{buddy1.toString(), buddy2.toString(), ...}.
     *
     * @return a String representation of the object.
     */
    @Override
    public String toString()
    {
        StringBuilder buff = new StringBuilder("VolatileJabberGroup.");
        buff.append(getGroupName());
        buff.append(", childContacts=" + countContacts() + ":[");

        Iterator<Contact> contacts = contacts();
        while (contacts.hasNext()) {
            Contact contact = contacts.next();
            buff.append(contact.toString());
            if (contacts.hasNext())
                buff.append(", ");
        }
        return buff.append("]").toString();
    }

    /**
     * Determines whether or not this contact group is to be stored in local DB. Non persistent
     * contact groups exist for the sole purpose of containing non persistent contacts.
     *
     * @return true if the contact group is persistent and false otherwise.
     */
    @Override
    public boolean isPersistent()
    {
        return true;
    }
}
