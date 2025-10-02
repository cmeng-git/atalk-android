/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.atalk.android.gui.contactlist.model;

import android.text.TextUtils;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;
import java.util.TreeSet;
import java.util.regex.Pattern;

import net.java.sip.communicator.service.contactlist.MetaContact;
import net.java.sip.communicator.service.contactlist.MetaContactGroup;
import net.java.sip.communicator.service.contactlist.MetaContactListService;
import net.java.sip.communicator.service.contactlist.event.MetaContactAvatarUpdateEvent;
import net.java.sip.communicator.service.contactlist.event.MetaContactEvent;
import net.java.sip.communicator.service.contactlist.event.MetaContactGroupEvent;
import net.java.sip.communicator.service.contactlist.event.MetaContactListListener;
import net.java.sip.communicator.service.contactlist.event.MetaContactModifiedEvent;
import net.java.sip.communicator.service.contactlist.event.MetaContactMovedEvent;
import net.java.sip.communicator.service.contactlist.event.MetaContactRenamedEvent;
import net.java.sip.communicator.service.contactlist.event.ProtoContactEvent;
import net.java.sip.communicator.service.protocol.AbstractOperationSetPersistentPresence;
import net.java.sip.communicator.service.protocol.Contact;
import net.java.sip.communicator.service.protocol.ContactGroup;
import net.java.sip.communicator.service.protocol.OperationSetPresence;
import net.java.sip.communicator.service.protocol.ProtocolProviderService;
import net.java.sip.communicator.service.protocol.event.ContactBlockingStatusListener;
import net.java.sip.communicator.service.protocol.event.ContactPresenceStatusChangeEvent;
import net.java.sip.communicator.service.protocol.event.ContactPresenceStatusListener;
import net.java.sip.communicator.util.account.AccountUtils;

import org.atalk.android.BaseActivity;
import org.atalk.android.gui.AppGUIActivator;
import org.atalk.android.gui.chat.ChatSessionManager;
import org.atalk.android.gui.contactlist.ContactListFragment;
import org.atalk.android.gui.contactlist.PresenceFilter;
import org.atalk.impl.timberlog.TimberLog;
import org.jivesoftware.smack.XMPPConnection;

import timber.log.Timber;

/**
 * Contact list model is responsible for caching current contact list obtained from contact
 * sources.(It will apply contact source filters which result in different output model).
 * <p>
 * Note: All contactList view update (from events) must be performed on UI thread using UI thread handler;
 * IllegalStateException: The content of the adapter has changed but ListView did not receive a notification.
 * Make sure the content of your adapter is not modified from a background thread, but only from the UI thread.
 * Make sure your adapter calls notifyDataSetChanged() when its content changes.
 *
 * @author Yana Stamcheva
 * @author Pawel Domas
 * @author Eng Chong Meng
 */
public class MetaContactListAdapter extends BaseContactListAdapter
        implements MetaContactListListener, ContactPresenceStatusListener, ContactBlockingStatusListener, UIGroupRenderer {
    /**
     * The list of contact list in original groups before filtered
     */
    private final LinkedList<MetaContactGroup> originalGroups;

    /**
     * The list of contact list groups for view display
     */
    private final LinkedList<MetaContactGroup> groups;

    /**
     * The list of original contacts before filtered.
     */
    private final LinkedList<TreeSet<MetaContact>> originalContacts;

    /**
     * The list of contacts for view display.
     */
    private final LinkedList<TreeSet<MetaContact>> contacts;

    /**
     * The <code>MetaContactListService</code>, which is the back end of this contact list adapter.
     */
    private MetaContactListService contactListService;

    /**
     * <code>MetaContactRenderer</code> instance used by this adapter.
     */
    private MetaContactRenderer contactRenderer;

    /**
     * The presence filter.
     */
    public static final PresenceFilter presenceFilter = new PresenceFilter();

    private final List<OperationSetPresence> opSetPresence = new ArrayList<>();

    /**
     * The currently used filter query.
     */
    private String currentFilterQuery;

    /**
     * if mDialogMode, update only contact status changes, not to filter data with isShownOnline
     */
    private boolean mDialogMode = false;

    public MetaContactListAdapter(ContactListFragment contactListFragment, boolean mainContactList) {
        super(contactListFragment, mainContactList);

        this.originalContacts = new LinkedList<>();
        this.contacts = new LinkedList<>();
        this.originalGroups = new LinkedList<>();
        this.groups = new LinkedList<>();
        addContactStatusListener();
    }

    /**
     * Initializes the adapter data.
     */
    public void initModelData() {
        contactListService = AppGUIActivator.getContactListService();
        if (contactListService != null) {
            addContacts(contactListService.getRoot(), true);
            contactListService.addMetaContactListListener(this);
        }
    }

    /**
     * Releases all resources used by this instance.
     */
    public void dispose() {
        if (contactListService != null) {
            contactListService.removeMetaContactListListener(this);
            removeContacts(contactListService.getRoot());
        }
        removeContactStatusListener();
    }

    /**
     * Locally implemented UIGroupRenderer
     * {@inheritDoc}
     */
    @Override
    public UIGroupRenderer getGroupRenderer(int groupPosition) {
        return this;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public UIContactRenderer getContactRenderer(int groupPosition) {
        if (contactRenderer == null) {
            contactRenderer = new MetaContactRenderer();
        }
        return contactRenderer;
    }

    /**
     * Returns the group at the given <code>groupPosition</code>.
     *
     * @param groupPosition the index of the group
     */
    @Override
    public Object getGroup(int groupPosition) {
        if (groupPosition >= 0 && groupPosition < groups.size())
            return groups.get(groupPosition);
        else {
            return null;
        }
    }

    /**
     * Returns the count of all groups contained in this adapter.
     */
    @Override
    public int getGroupCount() {
        return groups.size();
    }

    /**
     * Finds group index for given <code>MetaContactGroup</code>.
     *
     * @param group the group for which we need the index.
     *
     * @return index of given <code>MetaContactGroup</code> or -1 if not found
     */
    public int getGroupIndex(MetaContactGroup group) {
        return groups.indexOf(group);
    }

    /**
     * Finds <code>MetaContact</code> index in <code>MetaContactGroup</code> identified by given <code>groupIndex</code>.
     *
     * @param groupIndex index of group we want to search.
     * @param contact the <code>MetaContact</code> to find inside the group.
     *
     * @return index of <code>MetaContact</code> inside group identified by given group index.
     */
    public int getChildIndex(int groupIndex, MetaContact contact) {
        return getChildIndex(getContactList(groupIndex), contact);
    }

    /**
     * Returns the count of children contained in the group given by the <code>groupPosition</code>.
     *
     * @param groupPosition the index of the group, which children we would like to count
     */
    @Override
    public int getChildrenCount(int groupPosition) {
        TreeSet<MetaContact> contactList = getContactList(groupPosition);
        if (contactList != null)
            return contactList.size();
        else
            return 0;
    }

    /**
     * Get group contact list from filtered contact list.
     *
     * @param groupIndex contact group index.
     *
     * @return group contact list from filtered contact list.
     */
    private TreeSet<MetaContact> getContactList(int groupIndex) {
        if (groupIndex >= 0 && groupIndex < contacts.size()) {
            return contacts.get(groupIndex);
        }
        else {
            return null;
        }
    }

    /**
     * Get group contact list from original contact list.
     *
     * @param groupIndex contact group index.
     *
     * @return group contact list from original contact list.
     */
    private TreeSet<MetaContact> getOriginalCList(int groupIndex) {
        if (groupIndex >= 0 && groupIndex < originalContacts.size()) {
            return originalContacts.get(groupIndex);
        }
        else {
            return null;
        }
    }

    /**
     * Adds all child contacts for the given <code>group</code>. Omit metaGroup of zero child
     *
     * @param group the group, which child contacts to add
     */
    private void addContacts(MetaContactGroup group, boolean filtered) {
        if (!filtered || (group.countChildContacts() > 0)) {
            // Add the new metaGroup
            addGroup(group, filtered);

            // Use Iterator to avoid ConcurrentModificationException on addContact()
            Iterator<MetaContact> childContacts = group.getChildContacts();
            while (childContacts.hasNext()) {
                addContact(group, childContacts.next());
            }
        }

        Iterator<MetaContactGroup> subGroups = group.getSubgroups();
        while (subGroups.hasNext()) {
            addContacts(subGroups.next(), filtered);
        }
    }

    /**
     * Adds the given <code>group</code> to both the originalGroups and Groups with
     * zero contact if no existing group is found
     *
     * @param metaGroup the <code>MetaContactGroup</code> to add
     * @param filtered false will also create group if not found
     */
    private void addGroup(MetaContactGroup metaGroup, boolean filtered) {
        if (!originalGroups.contains(metaGroup)) {
            originalGroups.add(metaGroup);
            originalContacts.add(new TreeSet<>());
        }

        // cmeng: invalidateView causes childContact to be null, contact list not properly updated
        // add new group will have no contact; hence cannot remove the check for isMatching
        if ((!filtered || isMatching(metaGroup, currentFilterQuery)) && !groups.contains(metaGroup)) {
            groups.add(metaGroup);
            contacts.add(new TreeSet<>());
        }
    }

    /**
     * Remove an existing <code>group</code> from both the originalGroups and Groups
     *
     * @param metaGroup the <code>MetaContactGroup</code> to be removed
     */
    private void removeGroup(MetaContactGroup metaGroup) {
        int origGroupIndex = originalGroups.indexOf(metaGroup);
        if (origGroupIndex != -1) {
            originalContacts.remove(origGroupIndex);
            originalGroups.remove(metaGroup);
        }

        int groupIndex = groups.indexOf(metaGroup);
        if (groupIndex != -1) {
            contacts.remove(groupIndex);
            groups.remove(metaGroup);
        }
    }

    /**
     * Adds all child contacts for the given <code>group</code>.
     *
     * @param metaGroup the parent group of the child contact to add
     * @param metaContact the <code>MetaContact</code> to add
     */
    private void addContact(MetaContactGroup metaGroup, MetaContact metaContact) {
        int origGroupIndex = originalGroups.indexOf(metaGroup);
        int groupIndex = groups.indexOf(metaGroup);
        boolean isMatchingQuery = isMatching(metaContact, currentFilterQuery);

        // Add new group element and update both the Indexes (may be difference)
        if ((origGroupIndex < 0) || (isMatchingQuery && (groupIndex < 0))) {
            addGroup(metaGroup, true);
            origGroupIndex = originalGroups.indexOf(metaGroup);
            groupIndex = groups.indexOf(metaGroup);
        }

        TreeSet<MetaContact> origContactList = getOriginalCList(origGroupIndex);
        if (origContactList != null && getChildIndex(origContactList, metaContact) < 0) {
            origContactList.add(metaContact);
        }

        // cmeng: new group & contact are added only if isMatchingQuery is true
        if (isMatchingQuery) {
            TreeSet<MetaContact> contactList = getContactList(groupIndex);
            if ((contactList != null) && (getChildIndex(contactList, metaContact) < 0)) {
                // do no allow duplication with multiple accounts registration on same server
                //	if (!contactList.contains(metaContact)) ??? not correct test
                contactList.add(metaContact);
            }
        }
    }

    /**
     * Removes the contacts contained in the given group.
     *
     * @param group the <code>MetaContactGroup</code>, which contacts we'd like to remove
     */
    private void removeContacts(MetaContactGroup group) {
        removeGroup(group);
        Iterator<MetaContact> childContacts = group.getChildContacts();
        while (childContacts.hasNext()) {
            removeContact(group, childContacts.next());
        }

        Iterator<MetaContactGroup> subGroups = group.getSubgroups();
        while (subGroups.hasNext()) {
            removeContacts(subGroups.next());
        }
    }

    /**
     * Removes the given <code>metaContact</code> from both the original and the filtered list of this adapter.
     *
     * @param metaGroup the parent <code>MetaContactGroup</code> of the contact to remove
     * @param metaContact the <code>MetaContact</code> to remove
     */
    private void removeContact(MetaContactGroup metaGroup, MetaContact metaContact) {
        // Remove the contact from the original list and its group if empty.
        int origGroupIndex = originalGroups.indexOf(metaGroup);
        if (origGroupIndex != -1) {
            TreeSet<MetaContact> origContactList = getOriginalCList(origGroupIndex);
            if (origContactList != null) {
                origContactList.remove(metaContact);

                if (origContactList.isEmpty())
                    removeGroup(metaGroup);
            }
        }

        // Remove the contact from the filtered list and its group if empty
        int groupIndex = groups.indexOf(metaGroup);
        if (groupIndex != -1) {
            TreeSet<MetaContact> contactList = getContactList(groupIndex);
            if (contactList != null) {
                contactList.remove(metaContact);

                if (contactList.isEmpty())
                    removeGroup(metaGroup);
            }
        }
    }

    /**
     * Updates the display name of the given <code>metaContact</code>.
     *
     * @param metaContact the <code>MetaContact</code>, which display name to update
     */
    private void updateDisplayName(MetaContact metaContact) {
        int groupIndex = groups.indexOf(metaContact.getParentMetaContactGroup());
        if (groupIndex >= 0) {
            int contactIndex = getChildIndex(getContactList(groupIndex), metaContact);
            if (contactIndex >= 0)
                updateDisplayName(groupIndex, contactIndex);
        }
    }

    /**
     * Updates the avatar of the given <code>metaContact</code>.
     *
     * @param metaContact the <code>MetaContact</code>, which avatar to update
     */
    private void updateAvatar(MetaContact metaContact) {
        int groupIndex = groups.indexOf(metaContact.getParentMetaContactGroup());
        if (groupIndex >= 0) {
            int contactIndex = getChildIndex(getContactList(groupIndex), metaContact);
            if (contactIndex >= 0)
                updateAvatar(groupIndex, contactIndex, metaContact);
        }
    }

    /**
     * Updates the status of the given <code>metaContact</code>.
     *
     * @param metaContact the <code>MetaContact</code>, which status to update
     */
    private void updateStatus(MetaContact metaContact) {
        int groupIndex = groups.indexOf(metaContact.getParentMetaContactGroup());
        if (groupIndex >= 0) {
            int contactIndex = getChildIndex(getContactList(groupIndex), metaContact);
            if (contactIndex >= 0) {
                updateStatus(groupIndex, contactIndex, metaContact);
            }
        }
    }

    @Override
    public void contactBlockingStatusChanged(Contact contact, boolean blockState) {
        MetaContact metaContact = contactListService.findMetaContactByContact(contact);
        int groupIndex = groups.indexOf(metaContact.getParentMetaContactGroup());
        if (groupIndex >= 0) {
            int contactIndex = getChildIndex(getContactList(groupIndex), metaContact);
            if (contactIndex >= 0) {
                BaseActivity.uiHandler.post(() -> updateBlockStatus(groupIndex, contactIndex, contact));
            }
        }
    }

    /**
     * Indicates that a <code>MetaContact</code> has been added to the list.
     *
     * @param evt the <code>MetaContactEvent</code> that notified us
     */
    public void metaContactAdded(MetaContactEvent evt) {
        Timber.d("CONTACT ADDED: %s", evt.getSourceMetaContact());
        BaseActivity.uiHandler.post(() -> {
            addContact(evt.getParentGroup(), evt.getSourceMetaContact());
            notifyDataSetChanged();
        });
    }

    /**
     * Indicates that a <code>MetaContact</code> has been modified.
     *
     * @param evt the <code>MetaContactEvent</code> that notified us
     */
    public void metaContactModified(MetaContactModifiedEvent evt) {
        Timber.d("META CONTACT MODIFIED: %s", evt.getSourceMetaContact());
        invalidateViews();
    }

    /**
     * Indicates that a <code>MetaContact</code> has been removed from the list.
     *
     * @param evt the <code>MetaContactEvent</code> that notified us
     */
    public void metaContactRemoved(MetaContactEvent evt) {
        Timber.d("CONTACT REMOVED: %s", evt.getSourceMetaContact());
        BaseActivity.uiHandler.post(() -> {
            removeContact(evt.getParentGroup(), evt.getSourceMetaContact());
            notifyDataSetChanged();
        });
    }

    /**
     * Indicates that a <code>MetaContact</code> has been moved.
     *
     * @param evt the <code>MetaContactEvent</code> that notified us
     */
    public void metaContactMoved(MetaContactMovedEvent evt) {
        final MetaContactGroup oldParent = evt.getOldParent();
        final MetaContactGroup newParent = evt.getNewParent();

        final MetaContact metaContact = evt.getSourceMetaContact();
        final String destGroup = newParent.getGroupName();
        Timber.d("CONTACT MOVED (%s): %s to %s", metaContact, oldParent, destGroup);

        // Happen when a contact is moved to RootGroup i.e. "Contacts"; RootGroup is not ContactGroupJabberImpl
        if (!groups.contains(newParent)) {
            Timber.w("Add missing move-to group: %s (%s)", destGroup, newParent.getMetaUID());
            addGroup(newParent, false);
        }

        // Modify original group
        int oldGroupIdx = originalGroups.indexOf(oldParent);
        int newGroupIdx = originalGroups.indexOf(newParent);
        if (oldGroupIdx < 0 || newGroupIdx < 0) {
            Timber.e("Move group error for originalGroups, srcGroupIdx: %s, dstGroupIdx: %s (%s)",
                    oldGroupIdx, newGroupIdx, destGroup);
        }
        else {
            TreeSet<MetaContact> srcGroup = getOriginalCList(oldGroupIdx);
            if (srcGroup != null) {
                srcGroup.remove(metaContact);
            }
            TreeSet<MetaContact> dstGroup = getOriginalCList(newGroupIdx);
            if (dstGroup != null) {
                dstGroup.add(metaContact);
            }
        }

        // Move results group
        oldGroupIdx = groups.indexOf(oldParent);
        newGroupIdx = groups.indexOf(newParent);
        if (oldGroupIdx < 0 || newGroupIdx < 0) {
            Timber.e("Move group error for groups, srcGroupIdx: %s. dstGroupIdx: %s (%s)",
                    oldGroupIdx, newGroupIdx, destGroup);
        }
        else {
            TreeSet<MetaContact> srcGroup = getContactList(oldGroupIdx);
            if (srcGroup != null) {
                srcGroup.remove(metaContact);
            }
            TreeSet<MetaContact> dstGroup = getContactList(newGroupIdx);
            if (dstGroup != null) {
                dstGroup.add(metaContact);
            }

            // Hide oldParent if zero-contacts - not to do this to allow user delete empty new group
            // if (oldParent.countChildContacts() == 0) {
            //    groups.remove(oldParent);
            // }
        }

        // Note: use refreshModelData - create other problems with contacts = null
        BaseActivity.uiHandler.post(this::notifyDataSetChanged);
    }

    /**
     * Indicates that a <code>MetaContact</code> has been removed from the list.
     *
     * @param evt the <code>MetaContactEvent</code> that notified us
     */
    public void metaContactRenamed(final MetaContactRenamedEvent evt) {
        Timber.d("CONTACT RENAMED: %s", evt.getSourceMetaContact());
        BaseActivity.uiHandler.post(() -> updateDisplayName(evt.getSourceMetaContact()));
    }

    /**
     * Indicates that a protocol specific <code>Contact</code> has been added to the list.
     *
     * @param evt the <code>MetaContactEvent</code> that notified us
     */
    public void protoContactAdded(final ProtoContactEvent evt) {
        Timber.d("PROTO CONTACT ADDED: %s", evt.getNewParent());
        BaseActivity.uiHandler.post(() -> updateStatus(evt.getNewParent()));
    }

    /**
     * Indicates that a protocol specific <code>Contact</code> has been renamed.
     *
     * @param evt the <code>MetaContactEvent</code> that notified us
     */
    public void protoContactRenamed(ProtoContactEvent evt) {
        Timber.d("PROTO CONTACT RENAMED: %s", evt.getProtoContact().getAddress());
        invalidateViews();
    }

    /**
     * Indicates that a protocol specific <code>Contact</code> has been modified.
     *
     * @param evt the <code>MetaContactEvent</code> that notified us
     */
    public void protoContactModified(ProtoContactEvent evt) {
        Timber.d("PROTO CONTACT MODIFIED: %s", evt.getProtoContact().getAddress());
        invalidateViews();
    }

    /**
     * Indicates that a protocol specific <code>Contact</code> has been removed from the list.
     *
     * @param evt the <code>MetaContactEvent</code> that notified us
     */
    public void protoContactRemoved(final ProtoContactEvent evt) {
        Timber.d("PROTO CONTACT REMOVED: %s", evt.getProtoContact().getAddress());
        BaseActivity.uiHandler.post(() -> updateStatus(evt.getOldParent()));
    }

    /**
     * Indicates that a protocol specific <code>Contact</code> has been moved.
     *
     * @param evt the <code>MetaContactEvent</code> that notified us
     */
    public void protoContactMoved(final ProtoContactEvent evt) {
        Timber.d("PROTO CONTACT MOVED: %s", evt.getProtoContact().getAddress());
        BaseActivity.uiHandler.post(() -> {
            updateStatus(evt.getOldParent());
            updateStatus(evt.getNewParent());
        });
    }

    /**
     * Indicates that a <code>MetaContactGroup</code> has been added to the list.
     * Need to do it asap, as this method is called as sub-dialog of the addContact and MoveContact
     * Otherwise has problem in i.e. both the originalGroups and Groups do not contain the new metaGroup
     *
     * @param evt the <code>MetaContactEvent</code> that notified us
     *
     * @see #metaContactMoved(MetaContactMovedEvent)
     */
    public void metaContactGroupAdded(MetaContactGroupEvent evt) {
        MetaContactGroup metaGroup = evt.getSourceMetaContactGroup();
        Timber.d("META CONTACT GROUP ADDED: %s", metaGroup);
        // filtered = false; to add new group to both originalGroups and Groups even with zero contact
        addContacts(metaGroup, false);

        BaseActivity.uiHandler.post(this::notifyDataSetChanged);
    }

    /**
     * Indicates that a <code>MetaContactGroup</code> has been modified.
     *
     * @param evt the <code>MetaContactEvent</code> that notified us
     */
    public void metaContactGroupModified(MetaContactGroupEvent evt) {
        Timber.d("META CONTACT GROUP MODIFIED: %s", evt.getSourceMetaContactGroup());
        invalidateViews();
    }

    /**
     * Indicates that a <code>MetaContactGroup</code> has been removed from the list.
     *
     * @param evt the <code>MetaContactEvent</code> that notified us
     */
    public void metaContactGroupRemoved(MetaContactGroupEvent evt) {
        Timber.d("META CONTACT GROUP REMOVED: %s", evt.getSourceMetaContactGroup());
        BaseActivity.uiHandler.post(() -> {
            removeGroup(evt.getSourceMetaContactGroup());
            notifyDataSetChanged();
        });
    }

    /**
     * Indicates that the child contacts of a given <code>MetaContactGroup</code> has been reordered.
     * Note:
     * 1. add (insert) new before remove old data to avoid indexOutOfBound
     * 2. synchronized LinkList access to avoid ConcurrentModificationException
     *
     * @param evt the <code>MetaContactEvent</code> that notified us
     */
    public void childContactsReordered(MetaContactGroupEvent evt) {
        // Timber.d("Child contacts reordered");
        BaseActivity.uiHandler.post(() -> {
            MetaContactGroup group = evt.getSourceMetaContactGroup();
            int origGroupIndex = originalGroups.indexOf(group);
            int groupIndex = groups.indexOf(group);

            if (origGroupIndex >= 0) {
                TreeSet<MetaContact> contactList = getOriginalCList(origGroupIndex);

                if (contactList != null) {
                    // Timber.w("Modify originalGroups: " + origGroupIndex + " / " + originalGroups.size());
                    synchronized (originalContacts) {
                        originalContacts.add(origGroupIndex, new TreeSet<>(contactList));
                        originalContacts.remove(origGroupIndex + 1);
                    }
                }
            }

            if (groupIndex >= 0) {
                TreeSet<MetaContact> contactList = getContactList(groupIndex);

                if (contactList != null) {
                    // Timber.w("Modify groups: " + groupIndex + " / " + groups.size());
                    synchronized (contacts) {
                        contacts.add(groupIndex, new TreeSet<>(contactList));
                        contacts.remove(groupIndex + 1);
                    }
                }
            }
            notifyDataSetChanged();
        });
    }

    /**
     * Indicates that a <code>MetaContact</code> avatar has changed and needs to be updated.
     *
     * @param evt the <code>MetaContactEvent</code> that notified us
     */
    public void metaContactAvatarUpdated(final MetaContactAvatarUpdateEvent evt) {
        Timber.log(TimberLog.FINER, "metaContact avatar updated: %s", evt.getSourceMetaContact());
        BaseActivity.uiHandler.post(() -> updateAvatar(evt.getSourceMetaContact()));
    }

    /**
     * Returns the contained object on the given <code>groupPosition</code> and <code>childPosition</code>.
     * Note that this method must be called on UI thread.
     *
     * @param groupPosition the index of the group
     * @param childPosition the index of the child
     *
     * @return the contained object on the given <code>groupPosition</code> and <code>childPosition</code>
     */
    @Override
    public Object getChild(int groupPosition, int childPosition) {
        if (!contacts.isEmpty()) {
            TreeSet<MetaContact> contactList = getContactList(groupPosition);
            if (contactList != null) {
                int i = 0;
                for (MetaContact metaContact : contactList) {
                    if (i == childPosition) {
                        return metaContact;
                    }
                    i++;
                }
            }
        }
        return null;
    }

    /**
     * Return metaContact index in the contactList
     **/
    private int getChildIndex(TreeSet<MetaContact> contactList, MetaContact metaContact) {
        if ((contactList == null) || contactList.isEmpty())
            return -1;

        int i = 0;
        for (MetaContact mContact : contactList) {
            if (metaContact.equals(mContact))
                return i;
            i++;
        }
        return -1;
    }

    /**
     * Filters list data to match the given <code>query</code>.
     *
     * @param query the query we'd like to match
     */
    public void filterData(String query) {
        BaseActivity.uiHandler.post(() -> {
            currentFilterQuery = query.toLowerCase(Locale.US);
            groups.clear();
            contacts.clear();

            if (presenceFilter.isShowOffline() && TextUtils.isEmpty(query)) {
                // hide group contains zero contact
                for (MetaContactGroup metaGroup : originalGroups) {
                    if (metaGroup.countChildContacts() > 0) {
                        int groupIndex = originalGroups.indexOf(metaGroup);
                        groups.add(metaGroup);
                        contacts.add(getOriginalCList(groupIndex));
                    }
                }
            }
            else {
                for (MetaContactGroup metaGroup : originalGroups) {
                    if (metaGroup.countChildContacts() > 0) {
                        int groupIndex = originalGroups.indexOf(metaGroup);
                        TreeSet<MetaContact> contactList = getOriginalCList(groupIndex);

                        if (contactList != null) {
                            TreeSet<MetaContact> filteredList = new TreeSet<>();
                            for (MetaContact metaContact : contactList) {
                                if (isMatching(metaContact, query))
                                    filteredList.add(metaContact);
                            }

                            if (!filteredList.isEmpty()) {
                                groups.add(metaGroup);
                                contacts.add(filteredList);
                            }
                        }
                    }
                }
            }
            notifyDataSetChanged();
            expandAllGroups();
        });
    }


    /**
     * Create group/contacts TreeView with non-zero groups
     */
    public void nonZeroContactGroupList() {
        groups.clear();
        contacts.clear();

        // hide group contains zero contact
        for (MetaContactGroup metaGroup : originalGroups) {
            if (metaGroup.countChildContacts() > 0) {
                int groupIndex = originalGroups.indexOf(metaGroup);
                groups.add(metaGroup);
                contacts.add(getOriginalCList(groupIndex));
            }
        }
    }

    /**
     * Checks if the given <code>metaContact</code> is matching the given <code>query</code>.
     * A <code>MetaContact</code> would be matching the filter if one of the following is true:<br>
     * - it is online or user chooses show offline contacts
     * - its display name contains the filter string
     * - at least one of its child protocol contacts has a display name or
     * - an address that contains the filter string.
     *
     * @param metaContact the <code>MetaContact</code> to check
     * @param query the query string to check for matches
     *
     * @return <code>true</code> to indicate that the given <code>metaContact</code> is matching the
     * current filter, otherwise returns <code>false</code>
     */
    private boolean isMatching(MetaContact metaContact, String query) {
        if (presenceFilter.isMatching(metaContact)) {
            if (TextUtils.isEmpty(query))
                return true;

            Pattern queryPattern = Pattern.compile(query, Pattern.CASE_INSENSITIVE | Pattern.LITERAL);
            if (queryPattern.matcher(metaContact.getDisplayName()).find())
                return true;
            else {
                Iterator<Contact> contacts = metaContact.getContacts();
                while (contacts.hasNext()) {
                    Contact contact = contacts.next();

                    if (queryPattern.matcher(contact.getDisplayName()).find()
                            || queryPattern.matcher(contact.getAddress()).find()) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    /**
     * Checks if the given <code>metaGroup</code> is matching the current filter. A group is matching
     * the current filter only if it contains at least one child <code>MetaContact</code>, which is
     * matching the current filter.<br/>
     * Note that this method must be called on UI thread.
     *
     * @param metaGroup the <code>MetaContactGroup</code> to check
     * @param query the query string to check for matches
     *
     * @return <code>true</code> to indicate that the given <code>metaGroup</code> is matching the current
     * filter, otherwise returns <code>false</code>
     */
    private boolean isMatching(MetaContactGroup metaGroup, String query) {
        if (presenceFilter.isMatching(metaGroup)) {
            if (TextUtils.isEmpty(query))
                return true;

            Iterator<MetaContact> contacts = metaGroup.getChildContacts();
            while (contacts.hasNext()) {
                MetaContact metaContact = contacts.next();

                if (isMatching(metaContact, query))
                    return true;
            }
        }
        return false;
    }

    public void setDialogMode(boolean isDialogMode) {
        mDialogMode = isDialogMode;
    }

    /**
     * Indicates that a contact Presence Status Change has been received.
     * mDialogMode true indicates the contact list is shown for user multiple selection e.g. invite;
     * in this case do not refreshModelData() to sort, as items selected is tracked by their position
     *
     * @param event the <code>ContactPresenceStatusChangeEvent</code> that notified us
     *
     * @see AbstractOperationSetPersistentPresence #fireContactPresenceStatusChangeEvent(Contact, Jid, ContactGroup, PresenceStatus)
     */
    @Override
    public void contactPresenceStatusChanged(final ContactPresenceStatusChangeEvent event) {
        BaseActivity.uiHandler.post(() -> {
            // Timber.d("Contact status change on UI: %s => %s", mDialogMode, event.getSourceContact());
            //  mDialogMode: just update the status icon without sorting
            if (mDialogMode) {
                Contact sourceContact = event.getSourceContact();

                MetaContact metaContact = contactListService.findMetaContactByContact(sourceContact);
                // metaContact is already existing, just update it
                if (metaContact != null) {
                    updateStatus(metaContact);
                }
            }
            else {
                refreshModelData();
            }
        });
    }

    /**
     * Refresh the contact list from contactListService, with contact presence status sorted esp originalContacts
     * Then perform filterData if showOffline contacts is disabled, other newly online contacts are not included
     */
    private void refreshModelData() {
        originalGroups.clear();
        originalContacts.clear();
        groups.clear();
        contacts.clear();
        addContacts(contactListService.getRoot(), true);

        if (!presenceFilter.isShowOffline()) {
            filterData("");
        }
        notifyDataSetChanged();
    }

    /**
     * Adds <code>ContactPresenceStatusListener</code> and <code>ContactBlockingStatusListener</code>
     * for each ProtocolServiceProvider to listen for contact presence/blocking status change.
     */
    public void addContactStatusListener() {
        // Get all the registered protocolProviders
        Collection<ProtocolProviderService> providers = AccountUtils.getRegisteredProviders();
        for (ProtocolProviderService pps : providers) {
            XMPPConnection connection = pps.getConnection();
            if (connection == null)
                continue;

            // Timber.e("addContactStatusListener: %s", pps);
            OperationSetPresence opSetPresence = pps.getOperationSet(OperationSetPresence.class);
            if (opSetPresence != null) {
                this.opSetPresence.add(opSetPresence);
                opSetPresence.addContactPresenceStatusListener(this);
                opSetPresence.addContactBlockStatusListener(this);
            }
        }
    }

    /**
     * Remove code>ContactPresenceStatusListener</code> and <code>ContactBlockingStatusListener</code>
     * for each ProtocolServiceProvider to listen for contact presence/blocking status change.
     */
    public void removeContactStatusListener() {
        if (opSetPresence != null) {
            for (OperationSetPresence ops : opSetPresence) {
                ops.removeContactPresenceStatusListener(this);
                ops.removeContactBlockStatusListener(this);
            }
        }
    }

    /**
     * Checks if given <code>metaContact</code> is considered to be selected. That is if the chat
     * session with given <code>metaContact</code> is the one currently visible.
     *
     * @param metaContact the <code>MetaContact</code> to check.
     *
     * @return <code>true</code> if given <code>metaContact</code> is considered to be selected.
     */
    public static boolean isContactSelected(MetaContact metaContact) {
        return ChatSessionManager.getCurrentChatId() != null
                && ChatSessionManager.getActiveChat(metaContact) != null
                && ChatSessionManager.getCurrentChatId().equals(
                ChatSessionManager.getActiveChat(metaContact).getChatSession().getChatId());
    }

    /**
     * Implements {@link UIGroupRenderer}. {@inheritDoc}
     */
    @Override
    public String getDisplayName(Object groupImpl) {
        MetaContactGroup metaGroup = (MetaContactGroup) groupImpl;
        if (metaGroup.equals(contactListService.getRoot()))
            return ContactGroup.ROOT_GROUP_NAME;
        else
            return metaGroup.getGroupName();
    }

    //	/**
    //	 * Initializes the list of available contact sources for this contact list.
    //	 */
    //	private void initContactSources() {
    //		List<ContactSourceService> contactSources = AppGUIActivator.getContactSources();
    //		for (ContactSourceService contactSource : contactSources) {
    //			if (!(contactSource instanceof AsyncContactSourceService)
    //					|| ((AsyncContactSourceService) contactSource).canBeUsedToSearchContacts()) {
    //
    //				// ExternalContactSource extContactSource = new ExternalContactSource(contactSource, this);
    //				int sourceIndex = contactSource.getIndex();
    ////				if (sourceIndex >= 0 && mContactSources.size() >= sourceIndex)
    ////					mContactSources.add(sourceIndex, extContactSource);
    ////				else
    ////					mContactSources.add(extContactSource);
    //			}
    //		}
    ////		AppGUIActivator.bundleContext.addServiceListener(new ContactSourceServiceListener());
    //	}
    //
    //	/**
    //	 * Returns the list of registered contact sources to search in.
    //	 *
    //	 * @return the list of registered contact sources to search in
    //	 */
    //	public List<ContactSourceService> getContactSources() {
    //		return mContactSources;
    //	}
    //
    //
    //	/**
    //	 * Adds the given contact source to the list of available contact sources.
    //	 *
    //	 * @param contactSource
    //	 * 		the <code>ContactSourceService</code>
    //	 */
    //	public void addContactSource(ContactSourceService contactSource) {
    ////		if (!(contactSource instanceof AsyncContactSourceService)
    ////				|| ((AsyncContactSourceService) contactSource).canBeUsedToSearchContacts()) {
    ////			mContactSources.add(new ExternalContactSource(contactSource, this));
    ////		}
    //	}
    //
    //	/**
    //	 * Removes the given contact source from the list of available contact
    //	 * sources.
    //	 *
    //	 * @param contactSource
    //	 */
    //	public void removeContactSource(ContactSourceService contactSource) {
    ////		for (ContactSourceService extSource : mContactSources) {
    ////			if (extSource.getContactSourceService().equals(contactSource)) {
    ////				mContactSources.remove(extSource);
    ////				break;
    ////			}
    ////		}
    //	}
    //
    //	/**
    //	 * Removes all stored contact sources.
    //	 */
    //	public void removeAllContactSources() {
    //		mContactSources.clear();
    //	}
    //
    //	/**
    //	 * Returns the notification contact source.
    //	 *
    //	 * @return the notification contact source
    //	 */
    ////	public static NotificationContactSource getNotificationContactSource()
    ////	{
    ////		if (notificationSource == null)
    ////			notificationSource = new NotificationContactSource();
    ////		return notificationSource;
    ////	}
    //
    //	/**
    //	 * Returns the <code>ExternalContactSource</code> corresponding to the given
    //	 * <code>ContactSourceService</code>.
    //	 *
    //	 * @param contactSource the <code>ContactSourceService</code>, which
    //	 * 		corresponding external source implementation we're looking for
    //	 * @return the <code>ExternalContactSource</code> corresponding to the given <code>ContactSourceService</code>
    //	 */
    //	public ContactSourceService getContactSource(ContactSourceService contactSource) {
    ////		for (ContactSourceService extSource : mContactSources) {
    ////			if (extSource.getContactSourceService().equals(contactSource)) {
    ////				return extSource;
    ////			}
    ////		}
    //		return null;
    //	}
    //
    //	/**
    //	 * Returns all <code>UIContactSource</code>s of the given type.
    //	 *
    //	 * @param type the type of sources we're looking for
    //	 * @return a list of all <code>UIContactSource</code>s of the given type
    //	 */
    //	public List<ContactSourceService> getContactSources(int type) {
    ////		List<ContactSourceService> sources = new ArrayList<>();
    //
    ////		for (ContactSourceService extSource : mContactSources) {
    ////			if (extSource.getContactSourceService().getType() == type)
    ////				sources.add(extSource);
    ////		}
    //		return null; // sources;
    //	}
}
