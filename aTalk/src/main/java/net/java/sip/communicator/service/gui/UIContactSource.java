/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license.
 * See terms of license at gnu.org.
 */
package net.java.sip.communicator.service.gui;

import net.java.sip.communicator.service.contactsource.ContactSourceService;
import net.java.sip.communicator.service.contactsource.SourceContact;

/**
 * The user interface representation of a contact source.
 *
 * @author Yana Stamcheva
 */
public interface UIContactSource
{
    /**
     * Returns the UI group for this contact source. There's only one group
     * descriptor per external source.
     *
     * @return the group descriptor
     */
    public UIGroup getUIGroup();

    /**
     * Returns the <code>UIContact</code> corresponding to the given
     * <code>sourceContact</code>.
     *
     * @param sourceContact the <code>SourceContact</code>, for which we search a
     * corresponding <code>UIContact</code>
     * @return the <code>UIContact</code> corresponding to the given
     * <code>sourceContact</code>
     */
    public UIContact createUIContact(SourceContact sourceContact);

    /**
     * Removes the <code>UIContact</code> from the given <code>sourceContact</code>.
     * @param sourceContact the <code>SourceContact</code>, which corresponding UI
     * contact we would like to remove
     */
    public void removeUIContact(SourceContact sourceContact);

    /**
     * Returns the <code>UIContact</code> corresponding to the given
     * <code>SourceContact</code>.
     * @param sourceContact the <code>SourceContact</code>, which corresponding UI
     * contact we're looking for
     * @return the <code>UIContact</code> corresponding to the given
     * <code>MetaContact</code>
     */
    public UIContact getUIContact(SourceContact sourceContact);

    /**
     * Returns the corresponding <code>ContactSourceService</code>.
     *
     * @return the corresponding <code>ContactSourceService</code>
     */
    public ContactSourceService getContactSourceService();
    
    /**
     * Sets the contact source index.
     * 
     * @param contactSourceIndex the contact source index to set
     */
    public void setContactSourceIndex(int contactSourceIndex);
}
