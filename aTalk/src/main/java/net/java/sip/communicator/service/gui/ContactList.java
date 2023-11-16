/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license.
 * See terms of license at gnu.org.
 */
package net.java.sip.communicator.service.gui;

import net.java.sip.communicator.service.contactsource.ContactQuery;
import net.java.sip.communicator.service.contactsource.ContactQueryListener;
import net.java.sip.communicator.service.contactsource.ContactSourceService;
import net.java.sip.communicator.service.gui.event.ContactListListener;
import net.java.sip.communicator.service.gui.event.MetaContactQueryListener;

import java.awt.Component;
import java.util.Collection;
import java.util.List;

/**
 * The <code>ContactList</code> interface represents a contact list. All contact
 * list components that need to be available as a service could implement this interface.
 *
 * @author Yana Stamcheva
 */
public interface ContactList extends ContactQueryListener, MetaContactQueryListener
{
    /**
     * Returns the actual component corresponding to the contact list.
     *
     * @return the actual component corresponding to the contact list
     */
    Component getComponent();

    /**
     * Returns the list of registered contact sources to search in.
     *
     * @return the list of registered contact sources to search in
     */
    Collection<UIContactSource> getContactSources();

    /**
     * Returns the <code>ExternalContactSource</code> corresponding to the given <code>ContactSourceService</code>.
     *
     * @param contactSource the <code>ContactSourceService</code>, which
     * corresponding external source implementation we're looking for
     * @return the <code>ExternalContactSource</code> corresponding to the given <code>ContactSourceService</code>
     */
    UIContactSource getContactSource(ContactSourceService contactSource);

    /**
     * Adds the given contact source to the list of available contact sources.
     *
     * @param contactSource the <code>ContactSourceService</code>
     */
    void addContactSource(ContactSourceService contactSource);

    /**
     * Removes the given contact source from the list of available contact sources.
     *
     * @param contactSource
     */
    void removeContactSource(ContactSourceService contactSource);

    /**
     * Removes all stored contact sources.
     */
    void removeAllContactSources();

    /**
     * Sets the default filter to the given <code>filter</code>.
     *
     * @param filter the <code>ContactListFilter</code> to set as default
     */
    void setDefaultFilter(ContactListFilter filter);

    /**
     * Gets the default filter for this contact list.
     *
     * @return the default filter for this contact list
     */
    ContactListFilter getDefaultFilter();

    /**
     * Returns all <code>UIContactSource</code>s of the given type.
     *
     * @param type the type of sources we're looking for
     * @return a list of all <code>UIContactSource</code>s of the given type
     */
    List<UIContactSource> getContactSources(int type);

    /**
     * Adds the given group to this list.
     *
     * @param group the <code>UIGroup</code> to add
     * @param isSorted indicates if the contact should be sorted regarding to the <code>GroupNode</code> policy
     */
    void addGroup(final UIGroup group, final boolean isSorted);

    /**
     * Removes the given group and its children from the list.
     *
     * @param group the <code>UIGroup</code> to remove
     */
    void removeGroup(final UIGroup group);

    /**
     * Adds the given <code>contact</code> to this list.
     *
     * @param contact the <code>UIContact</code> to add
     * @param group the <code>UIGroup</code> to add to
     * @param isContactSorted indicates if the contact should be sorted
     * regarding to the <code>GroupNode</code> policy
     * @param isGroupSorted indicates if the group should be sorted regarding to
     * the <code>GroupNode</code> policy in case it doesn't exist and should be dded
     */
    void addContact(final UIContact contact,
            final UIGroup group,
            final boolean isContactSorted,
            final boolean isGroupSorted);

    /**
     * Adds the given <code>contact</code> to this list.
     *
     * @param query the <code>ContactQuery</code> that adds the given contact
     * @param contact the <code>UIContact</code> to add
     * @param group the <code>UIGroup</code> to add to
     * @param isSorted indicates if the contact should be sorted regarding to
     * the <code>GroupNode</code> policy
     */
    void addContact(final ContactQuery query,
            final UIContact contact,
            final UIGroup group,
            final boolean isSorted);

    /**
     * Removes the node corresponding to the given <code>MetaContact</code> from this list.
     *
     * @param contact the <code>UIContact</code> to remove
     * @param removeEmptyGroup whether we should delete the group if is empty
     */
    void removeContact(final UIContact contact,
            final boolean removeEmptyGroup);

    /**
     * Removes the node corresponding to the given <code>MetaContact</code> from
     * this list.
     *
     * @param contact the <code>UIContact</code> to remove
     */
    void removeContact(UIContact contact);

    /**
     * Removes all entries in this contact list.
     */
    void removeAll();

    /**
     * Returns a collection of all direct child <code>UIContact</code>s of the given
     * <code>UIGroup</code>.
     *
     * @param group the parent <code>UIGroup</code>
     * @return a collection of all direct child <code>UIContact</code>s of the given <code>UIGroup</code>
     */
    Collection<UIContact> getContacts(final UIGroup group);

    /**
     * Returns the currently applied filter.
     *
     * @return the currently applied filter
     */
    ContactListFilter getCurrentFilter();

    /**
     * Returns the currently applied filter.
     *
     * @return the currently applied filter
     */
    FilterQuery getCurrentFilterQuery();

    /**
     * Applies the given <code>filter</code>.
     *
     * @param filter the <code>ContactListFilter</code> to apply.
     * @return the filter query
     */
    FilterQuery applyFilter(ContactListFilter filter);

    /**
     * Applies the default filter.
     *
     * @return the filter query that keeps track of the filtering results
     */
    FilterQuery applyDefaultFilter();

    /**
     * Returns the currently selected <code>UIContact</code>. In case of a multiple
     * selection returns the first contact in the selection.
     *
     * @return the currently selected <code>UIContact</code> if there's one.
     */
    UIContact getSelectedContact();

    /**
     * Returns the list of selected contacts.
     *
     * @return the list of selected contacts
     */
    List<UIContact> getSelectedContacts();

    /**
     * Returns the currently selected <code>UIGroup</code> if there's one.
     *
     * @return the currently selected <code>UIGroup</code> if there's one.
     */
    UIGroup getSelectedGroup();

    /**
     * Selects the given <code>UIContact</code> in the contact list.
     *
     * @param uiContact the contact to select
     */
    void setSelectedContact(UIContact uiContact);

    /**
     * Selects the given <code>UIGroup</code> in the contact list.
     *
     * @param uiGroup the group to select
     */
    void setSelectedGroup(UIGroup uiGroup);

    /**
     * Selects the first found contact node from the beginning of the contact list.
     */
    void selectFirstContact();

    /**
     * Removes the current selection.
     */
    void removeSelection();

    /**
     * Adds a listener for <code>ContactListEvent</code>s.
     *
     * @param listener the listener to add
     */
    void addContactListListener(ContactListListener listener);

    /**
     * Removes a listener previously added with <code>addContactListListener</code>.
     *
     * @param listener the listener to remove
     */
    void removeContactListListener(ContactListListener listener);

    /**
     * Refreshes the given <code>UIContact</code>.
     *
     * @param uiContact the contact to refresh
     */
    void refreshContact(UIContact uiContact);

    /**
     * Indicates if this contact list is empty.
     *
     * @return <code>true</code> if this contact list contains no children, otherwise returns <code>false</code>
     */
    boolean isEmpty();

    /**
     * Shows/hides buttons shown in contact row.
     *
     * @param isVisible <code>true</code> to show contact buttons, <code>false</code> - otherwise.
     */
    void setContactButtonsVisible(boolean isVisible);

    /**
     * Shows/hides buttons shown in contact row.
     *
     * return <code>true</code> to indicate that contact buttons are shown,
     * <code>false</code> - otherwise.
     */
    boolean isContactButtonsVisible();

    /**
     * Enables/disables multiple selection.
     *
     * @param isEnabled <code>true</code> to enable multiple selection,
     * <code>false</code> - otherwise
     */
    void setMultipleSelectionEnabled(boolean isEnabled);

    /**
     * Enables/disables drag operations on this contact list.
     *
     * @param isEnabled <code>true</code> to enable drag operations, <code>false</code> otherwise
     */
    void setDragEnabled(boolean isEnabled);

    /**
     * Enables/disables the right mouse click menu.
     *
     * @param isEnabled <code>true</code> to enable right button menu, <code>false</code> otherwise.
     */
    void setRightButtonMenuEnabled(boolean isEnabled);
}
