/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on
 * an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 */
package org.atalk.android.gui.contactlist;

import java.util.Iterator;
import java.util.List;

import net.java.sip.communicator.service.contactlist.MetaContact;
import net.java.sip.communicator.service.contactlist.MetaContactGroup;
import net.java.sip.communicator.service.contactsource.ContactSourceService;
import net.java.sip.communicator.service.contactsource.SourceContact;
import net.java.sip.communicator.service.gui.ContactListFilter;
import net.java.sip.communicator.service.gui.FilterQuery;
import net.java.sip.communicator.service.gui.UIContact;
import net.java.sip.communicator.service.gui.UIGroup;
import net.java.sip.communicator.service.gui.event.MetaContactQuery;
import net.java.sip.communicator.service.gui.event.MetaContactQueryStatusEvent;
import net.java.sip.communicator.service.protocol.Contact;
import net.java.sip.communicator.service.protocol.PresenceStatus;
import net.java.sip.communicator.util.ConfigurationUtils;

import org.atalk.android.gui.AndroidGUIActivator;

/**
 * The <code>PresenceFilter</code> is used to filter offline contacts from the contact list.
 *
 * @author Yana Stamcheva
 * @author Eng Chong Meng
 */
public class PresenceFilter implements ContactListFilter {
    /**
     * Indicates if this presence filter shows or hides the offline contacts.
     */
    private boolean isShowOffline;

    /**
     * The initial result count below which we insert all filter results directly to the contact list
     * without firing events.
     */
    private static final int INITIAL_CONTACT_COUNT = 30;

    /**
     * Creates an instance of <code>PresenceFilter</code>.
     */
    public PresenceFilter() {
        isShowOffline = ConfigurationUtils.isShowOffline();
    }

    /**
     * Applies this filter. This filter is applied over the <code>MetaContactListService</code>.
     *
     * @param filterQuery the query which keeps track of the filtering results
     */
    public void applyFilter(FilterQuery filterQuery) {
        // Create the query that will track filtering.
        MetaContactQuery query = new MetaContactQuery();

        // Add this query to the filterQuery.
        filterQuery.addContactQuery(query);
        List<ContactSourceService> filterSources = AndroidGUIActivator.getContactSources();

        int maxIndex = 0;
        for (ContactSourceService filterSource : filterSources) {
            int currIx = filterSource.getIndex();
            if (maxIndex < currIx)
                maxIndex = currIx;
        }

        //		contactListAdapter.getMetaContactListSource().setIndex(maxIndex + 1);
        //		for (ContactSourceService filterSource : filterSources) {
        //			filterSource.setContactSourceIndex(filterSource.getIndex());
        //			ContactSourceService sourceService = filterSource.getContactSourceService();
        //
        //			ContactQuery contactQuery = sourceService.createContactQuery(null);
        //			if (contactQuery == null)
        //				continue;
        //
        //			// Add this query to the filterQuery.
        //			filterQuery.addContactQuery(contactQuery);
        //			// contactQuery.addContactQueryListener(contactList);
        //			contactQuery.start();
        //		}

        // Closes this filter to indicate that we finished adding queries to it.
        filterQuery.close();

        // query.addContactQueryListener(AndroidGUIActivator.getContactList());
        int resultCount = 0;

        addMatching(AndroidGUIActivator.getContactListService().getRoot(), query, resultCount);

        query.fireQueryEvent(query.isCanceled()
                ? MetaContactQueryStatusEvent.QUERY_CANCELED
                : MetaContactQueryStatusEvent.QUERY_COMPLETED);
    }

    /**
     * Indicates if the given <code>uiContact</code> is matching this filter.
     *
     * @param uiContact the <code>UIContact</code> to check
     *
     * @return <code>true</code> if the given <code>uiContact</code> is matching this filter, otherwise returns <code>false</code>
     */
    public boolean isMatching(UIContact uiContact) {
        Object descriptor = uiContact.getDescriptor();

        if (descriptor instanceof MetaContact)
            return isMatching((MetaContact) descriptor);
        else if (descriptor instanceof SourceContact)
            return isMatching((SourceContact) descriptor);
        else
            return false;
    }

    /**
     * Indicates if the given <code>uiGroup</code> is matching this filter.
     *
     * @param uiGroup the <code>UIGroup</code> to check
     *
     * @return <code>true</code> if the given <code>uiGroup</code> is matching this filter, otherwise returns <code>false</code>
     */
    public boolean isMatching(UIGroup uiGroup) {
        Object descriptor = uiGroup.getDescriptor();

        if (descriptor instanceof MetaContactGroup)
            return isMatching((MetaContactGroup) descriptor);
        else
            return false;
    }

    /**
     * Sets the show offline property.
     *
     * @param isShowOffline indicates if offline contacts are shown
     */
    public void setShowOffline(boolean isShowOffline) {
        this.isShowOffline = isShowOffline;
        ConfigurationUtils.setShowOffline(isShowOffline);
    }

    /**
     * Returns <code>true</code> if offline contacts are shown, otherwise returns <code>false</code>.
     *
     * @return <code>true</code> if offline contacts are shown, otherwise returns <code>false</code>
     */
    public boolean isShowOffline() {
        return isShowOffline;
    }

    /**
     * Returns <code>true</code> if offline contacts are shown or if the given <code>MetaContact</code> is online,
     * otherwise returns false.
     *
     * @param metaContact the <code>MetaContact</code> to check
     *
     * @return <code>true</code> if the given <code>MetaContact</code> is matching this filter
     */
    public boolean isMatching(MetaContact metaContact) {
        return (isShowOffline || isContactOnline(metaContact));
    }

    /**
     * Returns <code>true</code> if offline contacts are shown or if the given <code>MetaContact</code> is online,
     * otherwise returns false.
     *
     * @param contact the <code>MetaContact</code> to check
     *
     * @return <code>true</code> if the given <code>MetaContact</code> is matching this filter
     */
    public boolean isMatching(SourceContact contact) {
        // make sure we always show chat rooms and recent messages
        return (isShowOffline
                || contact.getPresenceStatus().isOnline()
                || (contact.getContactSource().getType() == ContactSourceService.CONTACT_LIST_TYPE));
    }

    /**
     * Returns <code>true</code> if offline contacts are shown or if the given <code>MetaContactGroup</code>
     * contains online contacts.
     *
     * @param metaGroup the <code>MetaContactGroup</code> to check
     *
     * @return <code>true</code> if the given <code>MetaContactGroup</code> is matching this filter
     */
    public boolean isMatching(MetaContactGroup metaGroup) {
        return ((metaGroup.countChildContacts() > 0)
                && (isShowOffline || (metaGroup.countOnlineChildContacts() > 0)));
    }

    /**
     * Returns <code>true</code> if the given meta contact is online, <code>false</code> otherwise.
     *
     * @param contact the meta contact
     *
     * @return <code>true</code> if the given meta contact is online, <code>false</code> otherwise
     */
    private boolean isContactOnline(MetaContact contact) {
        // If for some reason the default contact is null we return false.
        Contact defaultContact = contact.getDefaultContact();
        if (defaultContact == null)
            return false;

        // Lays on the fact that the default contact is the most connected.
        return defaultContact.getPresenceStatus().getStatus() >= PresenceStatus.ONLINE_THRESHOLD;
    }

    /**
     * Adds all contacts contained in the given <code>MetaContactGroup</code> matching the current filter
     * and not contained in the contact list.
     *
     * @param metaGroup the <code>MetaContactGroup</code>, which matching contacts to add
     * @param query the <code>MetaContactQuery</code> that notifies interested listeners of the results of this matching
     * @param resultCount the initial result count we would insert directly to the contact list without firing events
     */
    private void addMatching(MetaContactGroup metaGroup, MetaContactQuery query, int resultCount) {
        Iterator<MetaContact> childContacts = metaGroup.getChildContacts();

        //		while (childContacts.hasNext() && !query.isCanceled()) {
        //			MetaContact metaContact = childContacts.next();

        //			if (isMatching(metaContact)) {
        //				resultCount++;
        //				if (resultCount <= INITIAL_CONTACT_COUNT) {
        //					UIGroup uiGroup = null;

        //					if (!MetaContactListSource.isRootGroup(metaGroup)) {
        //						synchronized (metaGroup) {
        //							uiGroup = MetaContactListSource.getUIGroup(metaGroup);
        //							if (uiGroup == null)
        //								uiGroup = MetaContactListSource.createUIGroup(metaGroup);
        //						}
        //					}
        //
        //						Timber.d("Presence filter contact added: " + metaContact.getDisplayName());
        //
        //					UIContactImpl newUIContact;
        //					synchronized (metaContact) {
        //						newUIContact = MetaContactListSource.getUIContact(metaContact);
        //						if (newUIContact == null) {
        //							newUIContact = MetaContactListSource.createUIContact(metaContact);
        //						}
        //					}

        // AndroidGUIActivator.getContactList().addContact(newUIContact, uiGroup, true, true);
        //					query.setInitialResultCount(resultCount);
        //				}
        //				else {
        //					query.fireQueryEvent(metaContact);
        //				}
        //			}
        //		}

        // If in the meantime the filtering has been stopped we return here.
        if (query.isCanceled())
            return;

        Iterator<MetaContactGroup> subgroups = metaGroup.getSubgroups();
        while (subgroups.hasNext() && !query.isCanceled()) {
            MetaContactGroup subgroup = subgroups.next();

            //			if (isMatching(subgroup)) {
            //				UIGroup uiGroup;
            //				synchronized (subgroup) {
            //					uiGroup = MetaContactListSource.getUIGroup(subgroup);
            //					if (uiGroup == null)
            //						uiGroup = MetaContactListSource.createUIGroup(subgroup);
            //				}
            //
            //				AndroidGUIActivator.getContactList().addGroup(uiGroup, true);
            //				addMatching(subgroup, query, resultCount);
            //			}
        }
    }
}
