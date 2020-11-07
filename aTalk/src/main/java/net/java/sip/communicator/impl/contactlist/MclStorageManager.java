/*
 * aTalk, android VoIP and Instant Messaging client
 * Copyright 2014 Eng Chong Meng
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package net.java.sip.communicator.impl.contactlist;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

import net.java.sip.communicator.service.contactlist.MetaContact;
import net.java.sip.communicator.service.contactlist.MetaContactGroup;
import net.java.sip.communicator.service.contactlist.event.*;
import net.java.sip.communicator.service.protocol.Contact;
import net.java.sip.communicator.service.protocol.ContactGroup;

import org.apache.commons.lang3.StringUtils;
import org.atalk.persistance.DatabaseBackend;
import org.json.*;
import org.osgi.framework.BundleContext;

import java.util.*;

import androidx.annotation.NonNull;
import timber.log.Timber;

import static net.java.sip.communicator.service.contactlist.MetaContactGroup.TBL_CHILD_CONTACTS;

/**
 * The class handles read / write operations over a  persistent copy of the meta contacts and
 * groups stored in SQLite tables i.e. metaContactGroup and childContacts.
 *
 * The load / resolve strategy that we use when storing contact lists is roughly the following:
 *
 * 1) The MetaContactListService is started. <br>
 * 2) We receive an OSGI event telling us that a new ProtocolProviderService is registered or we
 * simply retrieve one that was already in the bundle <br>
 * 3) We look through the database and load groups and contacts belonging to this new provider.
 * Unresolved proto groups and contacts will be created for every one of them.
 *
 * @author Eng Chong Meng
 */
public class MclStorageManager implements MetaContactListListener
{
    /**
     * The property to enable multi tenant mode. When changing profiles/accounts the table
     * can be filled with groups and contacts from protocol provider we do not know about. This
     * mode will prevent loading empty and groups we do not know about.
     */
    private static final String MULTI_TENANT_MODE_PROP = "contactlist.MULTI_TENANT_MODE";

    /**
     * A reference to the MetaContactListServiceImpl that created and started us.
     */
    private MetaContactListServiceImpl mclServiceImpl = null;

    private static SQLiteDatabase mDB;
    private final ContentValues mcValues = new ContentValues();
    private final ContentValues ccValues = new ContentValues();

    /**
     * Initializes the storage manager to perform the initial loading and parsing of the
     * contacts and groups in the database
     *
     * @param bc a reference to the currently valid OSGI <tt>BundleContext</tt>
     * @param mclServiceImpl a reference to the currently valid instance of the <tt>MetaContactListServiceImpl</tt>
     * that we could use to pass parsed contacts and contact groups.
     */
    void start(BundleContext bc, MetaContactListServiceImpl mclServiceImpl)
    {
        this.mclServiceImpl = mclServiceImpl;
        mDB = DatabaseBackend.getWritableDB();
        mclServiceImpl.addMetaContactListListener(this);
    }

    /**
     * Parses the contacts in childContacts table and calls corresponding "add" methods belonging
     * to <tt>mclServiceImpl</tt> for every metaContact and metaContactGroup stored in the
     * tables that correspond to a provider caring the specified <tt>accountID</tt>.
     *
     * @param accountUuid the identifier of the account whose contacts we're interested in.
     */
    void extractContactsForAccount(String accountUuid, String accountUid)
    {
        // we don't want to receive meta contact events triggered by ourselves, so we stop
        // listening. It is possible but very unlikely that other events, not triggered by us are
        // received while we're off the channel.
        mclServiceImpl.removeMetaContactListListener(this);

        // Extract all its child groups and contacts
        processGroupContact(accountUuid, accountUid);

        // now we're done updating the contact list we can start listening again
        this.mclServiceImpl.addMetaContactListListener(this);
    }

    // #TODO: Rename of ROOT_PROTO_GROUP_UID to "Contacts" in v2.4.0 (20200817); need to remove on later version
    public static void mcg_patch()
    {
        // Remove table row: ContactGroup.ROOT_GROUP_UID in Table metaContactGroup
        String[] args = new String[]{ContactGroup.ROOT_GROUP_UID};
        mDB.delete(MetaContactGroup.TABLE_NAME, MetaContactGroup.MC_GROUP_UID + "=?", args);

        // Rename all "ContactListRoot" to "Contacts" in Table metaContactGroup
        args = new String[]{"ContactListRoot"};
        ContentValues values = new ContentValues();
        values.put(MetaContactGroup.PARENT_PROTO_GROUP_UID, ContactGroup.ROOT_PROTO_GROUP_UID);
        mDB.update(MetaContactGroup.TABLE_NAME, values,
                MetaContactGroup.PARENT_PROTO_GROUP_UID + "=?", args);

        // Rename all "ContactListRoot" to "Contacts" in Table childContacts
        values.clear();
        values.put(MetaContactGroup.PROTO_GROUP_UID, ContactGroup.ROOT_PROTO_GROUP_UID);
        mDB.update(MetaContactGroup.TBL_CHILD_CONTACTS, values,
                MetaContactGroup.PROTO_GROUP_UID + "=?", args);
    }

    // For data base garbage clean-up during testing
    private void mcg_clean()
    {
        String[] Ids = new String[]{"83"};
        for (String Id : Ids) {
            String[] args = new String[]{Id};
            mDB.delete(MetaContactGroup.TABLE_NAME, MetaContactGroup.ID + "=?", args);
            // mDB.delete(MetaContactGroup.TBL_CHILD_CONTACTS, MetaContactGroup.ID + "=?", args);
        }
    }

    /**
     * Parses <tt>RootMetaContactGroup</tt> and all of its proto-groups, subgroups, and
     * child-contacts creating corresponding instances through <tt>mclServiceImpl</tt> as
     * children of each <tt>parentGroup</tt>
     *
     * RootMetaContactGroup: starting point where all the group we're parsing.
     * parentGroup:　the <tt>MetaContactGroupImpl</tt> where we should be creating children.
     * parentProtoGroups a Map containing all proto groups that could be parents of any groups
     * parsed from the specified groupNode. The map binds UIDs to group references and may be
     * null for top level groups.
     *
     * @param accountUuid a String identifier i.e. prefix with "acc" of the account whose contacts we're interested in.
     */
    private void processGroupContact(String accountUuid, String accountUid)
    {
        // This map stores all proto groups that we find in the meta group table
        Map<String, ContactGroup> protoGroupsMap = new Hashtable<>();
        Map<String, MetaContactGroupImpl> metaGroupMap = new Hashtable<>();

        // Contact details attribute = value.
        List<StoredProtoContactDescriptor> protoContacts = new LinkedList<>();
        ContactGroup parentProtoGroup;

        // mcg_clean();
        // #TODO: Rename of ROOT_PROTO_GROUP_UID to "Contacts" in v2.4.0 (20200817); need to remove on later version
        MclStorageManager.mcg_patch();

        /*
         * Initialize and create the Root MetalContact Group.
         * This eliminates the need to store the first entry in metaContactGroup table in old DB design;
         * was the first entry in metaContactGroup table where the ContactGroup.ROOT_PROTO_GROUP_UID
         * is the root of the contact tree.
         */
        MetaContactGroupImpl metaContactGroup = mclServiceImpl.rootMetaGroup;
        String protoGroupUID = ContactGroup.ROOT_PROTO_GROUP_UID;

        metaGroupMap.put(protoGroupUID, metaContactGroup);
        ContactGroup newProtoGroup = mclServiceImpl.loadStoredContactGroup(metaContactGroup, protoGroupUID,
                null, null, accountUid);
        protoGroupsMap.put(protoGroupUID, newProtoGroup);

        String[] args = new String[]{accountUuid};
        Cursor cursor = mDB.query(MetaContactGroup.TABLE_NAME, null,
                MetaContactGroup.ACCOUNT_UUID + "=?", args, null, null, MetaContactGroup.ID);
        while (cursor.moveToNext()) {
            String parentProtoGroupUID = cursor.getString(cursor.getColumnIndex(MetaContactGroup.PARENT_PROTO_GROUP_UID));
            String groupUID = cursor.getString(cursor.getColumnIndex(MetaContactGroup.MC_GROUP_UID));
            String groupName = cursor.getString(cursor.getColumnIndex(MetaContactGroup.MC_GROUP_NAME));
            protoGroupUID = cursor.getString(cursor.getColumnIndex(MetaContactGroup.PROTO_GROUP_UID));
            String persistentData = cursor.getString(cursor.getColumnIndex(MetaContactGroup.PERSISTENT_DATA));

            Timber.d("### Fetching contact group: %s: %s for %s", parentProtoGroupUID, protoGroupUID, accountUuid);
            MetaContactGroupImpl metaGroup = metaGroupMap.get(parentProtoGroupUID);
            if (metaGroup != null) {
                metaContactGroup = mclServiceImpl.loadStoredMetaContactGroup(metaGroup, groupUID, groupName);
                metaGroupMap.put(protoGroupUID, metaContactGroup);

                parentProtoGroup = protoGroupsMap.get(protoGroupUID);
                newProtoGroup = mclServiceImpl.loadStoredContactGroup(metaContactGroup, protoGroupUID,
                        parentProtoGroup, persistentData, accountUid);
                protoGroupsMap.put(protoGroupUID, newProtoGroup);
            }
        }

        args = new String[]{accountUuid};
        String innerJoin = " INNER JOIN " + Contact.TABLE_NAME + " ON "
                + MetaContactGroup.TBL_CHILD_CONTACTS + "." + MetaContactGroup.CONTACT_JID + "="
                + Contact.TABLE_NAME + "." + Contact.CONTACT_JID;

        cursor = mDB.query(MetaContactGroup.TBL_CHILD_CONTACTS + innerJoin, null,
                MetaContactGroup.ACCOUNT_UUID + "=?", args, null, null, null);

        while (cursor.moveToNext()) {
            String metaUID = cursor.getString(cursor.getColumnIndex(MetaContactGroup.MC_UID));
            protoGroupUID = cursor.getString(cursor.getColumnIndex(MetaContactGroup.PROTO_GROUP_UID));
            String contactAddress = cursor.getString(cursor.getColumnIndex(MetaContactGroup.CONTACT_JID));
            String displayName = cursor.getString(cursor.getColumnIndex(MetaContactGroup.MC_DISPLAY_NAME));
            boolean isDisplayNameUserDefined = Boolean.parseBoolean(
                    cursor.getString(cursor.getColumnIndex(MetaContactGroup.MC_USER_DEFINED)));
            String persistentData = cursor.getString(cursor.getColumnIndex(MetaContactGroup.PERSISTENT_DATA));
            JSONObject details = new JSONObject();
            try {
                details = new JSONObject(cursor.getString(cursor.getColumnIndex(MetaContactGroup.MC_DETAILS)));
            } catch (JSONException e) {
                e.printStackTrace();
            }

            // Proceed if only there is a pre-loaded protoGroup for the contact
            if (protoGroupsMap.containsKey(protoGroupUID)) {
                protoContacts.clear();
                protoContacts.add(new StoredProtoContactDescriptor(contactAddress, persistentData,
                        protoGroupsMap.get(protoGroupUID)));

                // pass the parsed proto contacts to the mcl service
                MetaContactImpl metaContactImpl = mclServiceImpl.loadStoredMetaContact(
                        metaGroupMap.get(protoGroupUID), metaUID, displayName, details, protoContacts, accountUid);
                metaContactImpl.setDisplayNameUserDefined(isDisplayNameUserDefined);
            }
        }
        cursor.close();
    }

    /**
     * Creates a <tt>MetaContactGroup</tt> and its decedents
     *
     * A metaGroup may contain:
     * a. proto-groups
     * b. subGroups (can repeat a, b and c etc)
     * c. child-contacts
     *
     * Except the rootGroup, all decedents are linked to its parent with "parent-proto-group-uid"
     * Except for rootGroup, all decedents must be owned by a specific account uuid.
     * Note: the rootGroup is created when a virgin database is first generated.
     *
     * @param mcGroup the MetaContactGroup that the new entry is to be created
     */
    private void createMetaContactGroupEntry(MetaContactGroup mcGroup)
    {
        Iterator<ContactGroup> protoGroups = mcGroup.getContactGroups();
        while (protoGroups.hasNext()) {
            ContactGroup protoGroup = protoGroups.next();
            createProtoContactGroupEntry(protoGroup, mcGroup);
        }

        // create the sub-groups entry
        Iterator<MetaContactGroup> subgroups = mcGroup.getSubgroups();
        while (subgroups.hasNext()) {
            MetaContactGroup subgroup = subgroups.next();
            createMetaContactGroupEntry(subgroup);
        }

        // create the child-contacts entry
        Iterator<MetaContact> childContacts = mcGroup.getChildContacts();
        while (childContacts.hasNext()) {
            MetaContact metaContact = childContacts.next();
            createMetaContactEntry(metaContact);
        }
    }

    /**
     * Creates a new <tt>protoGroup</tt> entry in the table
     *
     * @param protoGroup the {@code ContactGroup} which is to be created for
     * @param metaGroup the parent of the protoGroup
     */
    private void createProtoContactGroupEntry(ContactGroup protoGroup, MetaContactGroup metaGroup)
    {
        // Do not create root group i.e. "Contacts", check of groupName == "Contacts"
        if (ContactGroup.ROOT_GROUP_NAME.equals(metaGroup.getGroupName())) {
            Timber.w("Not allowed! Root group creation: %s", metaGroup.getGroupName());
            return;
        }

        // Ignore if the group was created as an encapsulator of a non persistent proto group
        if ((protoGroup != null) && protoGroup.isPersistent()) {
            ContentValues mcgContent = new ContentValues();

            String accountUuid = protoGroup.getProtocolProvider().getAccountID().getAccountUuid();
            mcgContent.put(MetaContactGroup.ACCOUNT_UUID, accountUuid);

            String mcGroupName = metaGroup.getGroupName();
            mcgContent.put(MetaContactGroup.MC_GROUP_NAME, mcGroupName);

            String mcGroupUid = metaGroup.getMetaUID();
            mcgContent.put(MetaContactGroup.MC_GROUP_UID, mcGroupUid);

            // Use default ContactGroup.ROOT_PROTO_GROUP_UID for all protoGroup entry
            String parentGroupUid = ContactGroup.ROOT_PROTO_GROUP_UID;
            mcgContent.put(MetaContactGroup.PARENT_PROTO_GROUP_UID, parentGroupUid);

            String protoGroupUid = protoGroup.getUID();
            mcgContent.put(MetaContactGroup.PROTO_GROUP_UID, protoGroupUid);

            // add persistent data
            String persistentData = protoGroup.getPersistentData();
            if (StringUtils.isEmpty(persistentData))
                persistentData = "";
            mcgContent.put(MetaContactGroup.PERSISTENT_DATA, persistentData);

            mDB.insert(MetaContactGroup.TABLE_NAME, null, mcgContent);
        }
    }

    /**
     * Creates a <tt>metaContact</tt> entry in the table
     *
     * @param metaContact the MetaContact that the new entry is to be created for
     */
    private void createMetaContactEntry(MetaContact metaContact)
    {
        ContentValues contentValues = new ContentValues();
        mcValues.clear();

        String mcUid = metaContact.getMetaUID();
        mcValues.put(MetaContactGroup.MC_UID, mcUid);

        String displayName = metaContact.getDisplayName();
        mcValues.put(MetaContactGroup.MC_DISPLAY_NAME, displayName);

        boolean isUserDefined = ((MetaContactImpl) metaContact).isDisplayNameUserDefined();
        mcValues.put(MetaContactGroup.MC_USER_DEFINED, Boolean.toString(isUserDefined));

        JSONObject mcDetails = metaContact.getDetails();
        mcValues.put(MetaContactGroup.MC_DETAILS, mcDetails.toString());

        Iterator<Contact> contacts = metaContact.getContacts();
        while (contacts.hasNext()) {
            Contact contact = contacts.next();
            if (!contact.isPersistent())
                continue;

            String accountUuid = contact.getProtocolProvider().getAccountID().getAccountUuid();
            mcValues.put(MetaContactGroup.ACCOUNT_UUID, accountUuid);

            String protoGroupId = contact.getParentContactGroup().getUID();
            mcValues.put(MetaContactGroup.PROTO_GROUP_UID, protoGroupId);

            String contactJid = contact.getAddress();
            mcValues.put(MetaContactGroup.CONTACT_JID, contactJid);

            String persistentData = contact.getPersistentData();
            mcValues.put(MetaContactGroup.PERSISTENT_DATA, persistentData);

            mDB.insert(TBL_CHILD_CONTACTS, null, mcValues);

            // Create the contact entry only if not found in contacts table
            if (findContactEntry("Jabber", contactJid) == null) {
                contentValues.clear();
                contentValues.put(Contact.CONTACT_UUID, mcUid);
                contentValues.put(Contact.PROTOCOL_PROVIDER, "Jabber");
                contentValues.put(Contact.CONTACT_JID, contactJid);

                String svrDisplayName = (isUserDefined) ? contactJid : displayName;
                contentValues.put(Contact.SVR_DISPLAY_NAME, svrDisplayName);

                mDB.insert(Contact.TABLE_NAME, null, contentValues);
            }
        }
    }

    // ============= Event triggered handlers for MetaContactListService Implementation ===========

    /**
     * Creates a table entry for the source metaContact group, its child metaContacts and
     * associated proto-groups.
     *
     * @param evt the MetaContactListEvent containing the corresponding contact
     */
    public void metaContactGroupAdded(MetaContactGroupEvent evt)
    {
        // create metaContactGroup entry only if it is not null and has a parent associated with it
        MetaContactGroup mcGroup = evt.getSourceMetaContactGroup();
        if ((mcGroup == null) || (mcGroup.getParentMetaContactGroup() == null)) {
            String mcGroupName = "mcGroupNull";
            if (mcGroup != null)
                mcGroupName = mcGroup.getGroupName();
            Timber.d("Abort metaContactGroup creation without a parent for: %s", mcGroupName);
            return;
        }
        createMetaContactGroupEntry(mcGroup);
    }

    /**
     * Determines the exact type of the change and acts accordingly either updating group name
     *
     * @param evt the MetaContactListEvent containing the corresponding contact
     */
    public void metaContactGroupModified(MetaContactGroupEvent evt)
    {
        // ignore modification of non-persistent metaContactGroup
        MetaContactGroup mcGroup = evt.getSourceMetaContactGroup();
        if (!mcGroup.isPersistent())
            return;

        /*
         * CONTACT_GROUP_ADDED_TO_META_GROUP not required metaContactGroup to exist - recreate
         * all new. Just logged in an internal err if metaContactGroup for modification not found
         */
        String mcGroupUid = mcGroup.getMetaUID();
        String mcGroupName = findMetaContactGroupEntry(mcGroupUid);
        if ((MetaContactGroupEvent.CONTACT_GROUP_ADDED_TO_META_GROUP != evt.getEventID()) && (mcGroupName == null)) {
            Timber.d("Debug ref only: Failed to find modifying metaContactGroup: %s", mcGroup.getGroupName());
            return;
        }

        switch (evt.getEventID()) {
            case MetaContactGroupEvent.CONTACT_GROUP_ADDED_TO_META_GROUP:
                contactGroupAddedToMetaGroup(evt);
                break;

            case MetaContactGroupEvent.CONTACT_GROUP_RENAMED_IN_META_GROUP:
                contactGroupRenamedInMetaGroup(evt);
                break;

            case MetaContactGroupEvent.CONTACT_GROUP_REMOVED_FROM_META_GROUP:
                contactGroupRemovedFromMetaGroup(evt);
                break;

            case MetaContactGroupEvent.META_CONTACT_GROUP_RENAMED:
                mcValues.clear();
                String[] args = {mcGroupUid};

                mcValues.put(MetaContactGroup.MC_GROUP_NAME, mcGroup.getGroupName());
                mDB.update(MetaContactGroup.TABLE_NAME, mcValues, MetaContactGroup.MC_GROUP_UID + "=?", args);
                break;
        }
    }

    /**
     * Removes the corresponding metaContactGroup from the metaContactGroup table.
     *
     * @param evt the MetaContactGroupEvent containing the corresponding contact
     */
    public void metaContactGroupRemoved(MetaContactGroupEvent evt)
    {
        // ignore removal of non-persistent metaContactGroup
        MetaContactGroupImpl mcGroup = (MetaContactGroupImpl) evt.getSourceMetaContactGroup();
        if (!mcGroup.isPersistent())
            return;

        // Just logged in an internal error if metaContactGroup not found; can happen when a
        // contact is removed - already triggered and removed in contactGroupRemovedFromMetaGroup()
        String mcGroupUid = mcGroup.getMetaUID();
        if (findMetaContactGroupEntry(mcGroupUid) == null) {
            Timber.d("Failed to find metaContactGroup for removal (may have been removed): %s", mcGroup.getGroupName());
            return;
        }

        // proceed to remove metaContactGroup
        String[] args = new String[]{mcGroupUid};
        mDB.delete(MetaContactGroup.TABLE_NAME, MetaContactGroup.MC_GROUP_UID + "=?", args);
    }

    /**
     * Creates table entries for the new Contact group in the mcGroup
     *
     * @param evt the MetaContactListEvent containing the corresponding contact
     */
    private void contactGroupAddedToMetaGroup(MetaContactGroupEvent evt)
    {
        ContactGroup protoGroup = evt.getSourceProtoGroup();
        MetaContactGroupImpl mcGroupImpl = (MetaContactGroupImpl) evt.getSourceMetaContactGroup();
        createProtoContactGroupEntry(protoGroup, mcGroupImpl);
    }

    /**
     * Renamed of a protocol specific ContactGroup in the source MetaContactGroup. Note that
     * this does not in any way mean that the name of the MetaContactGroup itself has changed.
     * Change of the protoContactGroup name/UID is allowed if it is the only defined protoGroup;
     * If permitted, change its child contacts to the new ContactGroup Name are also required.
     *
     * <tt>MetaContactGroup</tt>s contain multiple protocol groups and their name cannot change
     * each time one of them is renamed.
     *
     * @param evt the MetaContactListEvent containing the corresponding contactGroup and other info.
     * @see MetaContactListServiceImpl#locallyRemoveAllContactsForProvider(MetaContactGroupImpl, ContactGroup)
     */
    private void contactGroupRenamedInMetaGroup(MetaContactGroupEvent evt)
    {
        MetaContactGroup mcGroup = evt.getSourceMetaContactGroup();
        String mcGroupUid = mcGroup.getMetaUID();
        String newProtoGroupUid = evt.getSourceProtoGroup().getUID();

        String[] columns = {MetaContactGroup.PROTO_GROUP_UID};
        String[] args = {mcGroupUid};
        Cursor cursor = mDB.query(MetaContactGroup.TABLE_NAME, columns,
                MetaContactGroup.MC_GROUP_UID + "=?", args, null, null, null);

        if (cursor.getCount() != 1) {
            Timber.d("Ignore debug ref: Rename of the protoGroup is not allowed with multiple owners: %s", newProtoGroupUid);
        }
        else {
            cursor.moveToNext();
            String oldProtoGroupUid = cursor.getString(0);
            args = new String[]{mcGroupUid, oldProtoGroupUid};

            mcValues.clear();
            mcValues.put(MetaContactGroup.PROTO_GROUP_UID, newProtoGroupUid);
            mDB.update(MetaContactGroup.TABLE_NAME, mcValues, MetaContactGroup.MC_GROUP_UID
                    + "=? AND " + MetaContactGroup.PROTO_GROUP_UID + "=?", args);

            // update childContacts to new protoGroupUid
            String accountUuid = evt.getSourceProvider().getAccountID().getAccountUuid();
            args = new String[]{accountUuid, oldProtoGroupUid};
            mDB.update(TBL_CHILD_CONTACTS, mcValues, MetaContactGroup.ACCOUNT_UUID
                    + "=? AND " + MetaContactGroup.PROTO_GROUP_UID + "=?", args);
        }
        cursor.close();
    }

    /**
     * Removal of a protocol specific ContactGroup in the source MetaContactGroup;
     *
     * Removal of its child contacts were already performed by mclServiceImpl prior to
     * call for contactGroup removal.
     *
     * @param evt the MetaContactListEvent containing the corresponding contactGroup and other info.
     * @see MetaContactListServiceImpl#locallyRemoveAllContactsForProvider
     * (MetaContactGroupImpl, ContactGroup)
     */
    private void contactGroupRemovedFromMetaGroup(MetaContactGroupEvent evt)
    {
        // Ignore if the group was created as an encapsulator of a non persistent proto group
        ContactGroup protoGroup = evt.getSourceProtoGroup();
        if ((protoGroup == null) || !protoGroup.isPersistent())
            return;

        String accountUuid = evt.getSourceProvider().getAccountID().getAccountUuid();
        String mcGroupUid = evt.getSourceMetaContactGroup().getMetaUID();
        String protoGroupUid = protoGroup.getUID();
        Timber.d("Removing contact ProtoGroup: %s: %s", protoGroupUid, accountUuid);

        // Do not allow removal of root group i.e. "Contacts" or VOLATILE_GROUP or non-empty group
        if (ContactGroup.ROOT_GROUP_UID.equals(mcGroupUid)
                || ContactGroup.VOLATILE_GROUP.equals(protoGroupUid)
                || protoGroup.countContacts() > 0) {
            Timber.w("Not allowed! Group deletion for: %s (%s)", protoGroupUid, protoGroup.countContacts());
            return;
        }

        String[] args = {accountUuid, mcGroupUid, protoGroupUid};
        mDB.delete(MetaContactGroup.TABLE_NAME, MetaContactGroup.ACCOUNT_UUID + "=? AND "
                + MetaContactGroup.MC_GROUP_UID + "=? AND " + MetaContactGroup.PROTO_GROUP_UID + "=?", args);

        // Remove all the protoGroup orphan childContacts entry - in case not clean properly
        args = new String[]{protoGroupUid};
        Cursor cursor = mDB.query(MetaContactGroup.TABLE_NAME, null,
                MetaContactGroup.PROTO_GROUP_UID + "=?", args, null, null, null);
        if (cursor.getCount() == 0) {  // found no parent
            Timber.d("Removing old protoGroup childContacts if any: %s: %s", protoGroupUid, accountUuid);
            args = new String[]{accountUuid, protoGroupUid};
            mDB.delete(TBL_CHILD_CONTACTS, MetaContactGroup.ACCOUNT_UUID
                    + "=? AND " + MetaContactGroup.PROTO_GROUP_UID + "=?", args);
        }
        cursor.close();
    }

    /**
     * Creates new table entry for the source metaContact, its contacts with the associated
     * protoGroups in childContacts table.
     *
     * @param evt the MetaContactListEvent containing the corresponding contact
     */
    public void metaContactAdded(MetaContactEvent evt)
    {
        // if the parent group is not persistent, do not do anything
        if (!evt.getParentGroup().isPersistent())
            return;

        MetaContact metaContact = evt.getSourceMetaContact();
        createMetaContactEntry(metaContact);
    }

    /**
     * Changes the display name attribute of the specified meta contact node.
     *
     * @param evt the MetaContactListEvent containing the corresponding contact
     */
    public void metaContactRenamed(MetaContactRenamedEvent evt)
    {
        MetaContactImpl metaContactImpl = (MetaContactImpl) evt.getSourceMetaContact();
        String metaContactUid = metaContactImpl.getMetaUID();
        String contactJid = findMetaContactEntry(metaContactUid);

        // Just logged in an internal err if rename contact not found (non-persistent)
        if (contactJid == null) {
            Timber.d("MetaContact not found for rename: %s", metaContactImpl.getDisplayName());
            return;
        }
        String oldDisplayName = evt.getOldDisplayName();
        String newDisplayName = evt.getNewDisplayName();
        if (StringUtils.isNotEmpty(newDisplayName) && !newDisplayName.equals(oldDisplayName)) {
            mcValues.clear();
            mcValues.put(MetaContactGroup.MC_DISPLAY_NAME, newDisplayName);
            boolean isUserDefined = metaContactImpl.isDisplayNameUserDefined();
            mcValues.put(MetaContactGroup.MC_USER_DEFINED, Boolean.toString(isUserDefined));

            Iterator<Contact> contacts = metaContactImpl.getContacts();
            while (contacts.hasNext()) {
                Contact contact = contacts.next();
                contactJid = contact.getAddress();
                String[] args = {metaContactUid, contactJid};

                String persistentData = contact.getPersistentData();
                mcValues.put(MetaContactGroup.PERSISTENT_DATA, persistentData);

                mDB.update(TBL_CHILD_CONTACTS, mcValues, MetaContactGroup.MC_UID
                        + "=? AND " + MetaContactGroup.CONTACT_JID + "=?", args);

                // Also update the contacts table entry if update is from server
                if (!isUserDefined) {
                    ccValues.clear();
                    ccValues.put(Contact.SVR_DISPLAY_NAME, newDisplayName);
                    mDB.update(Contact.TABLE_NAME, ccValues, Contact.CONTACT_JID + "=?",
                            new String[]{contactJid});
                }
            }
        }
    }

    /**
     * Indicates that a MetaContact is to be modified.
     *
     * @param evt the MetaContactModifiedEvent containing the corresponding contact
     */
    public void metaContactModified(MetaContactModifiedEvent evt)
    {
        String metaContactUid = evt.getSourceMetaContact().getMetaUID();
        String contactJid = findMetaContactEntry(metaContactUid);

        // Just logged in an internal err if rename contact not found (non-persistent)
        if (contactJid == null) {
            Timber.d("Ignore debug ref: MetaContact not found for modification: %s", evt.getSourceMetaContact());
            return;
        }

        JSONObject details;
        JSONArray jsonArray;
        String[] columns = {MetaContactGroup.MC_DETAILS};
        String[] args = {metaContactUid};
        Cursor cursor = mDB.query(TBL_CHILD_CONTACTS, columns,
                MetaContactGroup.MC_UID + "=?", args, null, null, null);

        String name = evt.getModificationName();
        try {
            details = new JSONObject(cursor.getString(0));
            jsonArray = details.getJSONArray(name);
        } catch (JSONException e) {
            e.printStackTrace();
            cursor.close();
            return;
        }
        cursor.close();

        Object oldValue = evt.getOldValue();
        Object newValue = evt.getNewValue();
        int jaSize = jsonArray.length();
        boolean isChanged = false;

        try {
            // indicates add new item
            if ((oldValue == null) && (newValue != null)) {
                jsonArray.put(newValue);
            }
            // indicates remove and old value must be string or JSONArray
            else if ((oldValue != null) && (newValue == null) && (jaSize > 0)) {
                // indicates removing multiple items at one time
                if (oldValue instanceof JSONArray) {
                    jsonArray = null;
                    isChanged = true;
                }
                // removing one item only
                else {
                    for (int i = 0; i < jaSize; i++) {
                        if (oldValue.equals(jsonArray.get(i))) {
                            jsonArray.remove(i);
                            isChanged = true;
                            break;
                        }
                    }
                }
            }
            // indicates change item value
            else if ((oldValue != null) && (newValue != null) && (jaSize > 0)) {
                for (int i = 0; i < jaSize; i++) {
                    if (oldValue.equals(jsonArray.get(i))) {
                        jsonArray.put(i, newValue);
                        isChanged = true;
                        break;
                    }
                }
            }
            details.put(name, jsonArray);
        } catch (JSONException e) {
            e.printStackTrace();
            return;
        }
        if (isChanged) {
            mcValues.clear();
            mcValues.put(MetaContactGroup.MC_DETAILS, details.toString());
            mDB.update(TBL_CHILD_CONTACTS, mcValues, MetaContactGroup.MC_UID + "=?", args);
        }
    }

    /**
     * Moves the corresponding metaContact from its old parent to the new parent metaContactGroup.
     * Create the new metaContactGroup if no exist. Leave the removal of old group with empty
     * child to user (or mclServiceImpl?)
     *
     * @param evt the MetaContactMovedEvent containing the reference move information
     */
    public void metaContactMoved(MetaContactMovedEvent evt)
    {
        MetaContact metaContact = evt.getSourceMetaContact();
        String metaContactUid = metaContact.getMetaUID();

        //		// null => case of moving from non persistent group to a persistent one.
        //		if (metaContactUid == null) {
        //			// create new metaContact Entry
        //			createMetaContactEntry(evt.getSourceMetaContact());
        //		}
        if (findMetaContactEntry(metaContactUid) == null) {
            Timber.d("MetaContact Uid cannot be null: %s", metaContact.getDisplayName());
            return;
        }

        MetaContactGroup newMCGroup = evt.getNewParent();
        String newGroupName = newMCGroup.getGroupName();
        String newGroupUid = newMCGroup.getMetaUID();

        // check if new metaContactGroup exist (give warning if none found); "Contacts" is not stored in DB
        if (!ContactGroup.ROOT_GROUP_NAME.equals(newGroupName)
                && findMetaContactGroupEntry(newGroupUid) == null) {
            Timber.w("Destination mcGroup for metaContact move not found: %s", newGroupName);
        }

        mcValues.clear();
        String[] args = {metaContactUid};
        mcValues.put(MetaContactGroup.PROTO_GROUP_UID, newGroupName);
        mDB.update(TBL_CHILD_CONTACTS, mcValues, MetaContactGroup.MC_UID + "=?", args);
    }

    /**
     * Remove the corresponding metaContact from the childContacts table entry
     *
     * @param evt the MetaContactEvent containing the corresponding metaContact
     */
    public void metaContactRemoved(MetaContactEvent evt)
    {
        // ignore removal of metaContact of non-persistent parentGroup
        if (!evt.getParentGroup().isPersistent())
            return;

        String metaContactUid = evt.getSourceMetaContact().getMetaUID();
        String contactJid = findMetaContactEntry(metaContactUid);

        // Just logged in an internal err if none is found
        if (contactJid == null) {
            Timber.d("Ignore debug ref: MetaContact not found for removal: %s", evt.getSourceMetaContact());
            return;
        }

        // remove the meta contact entry.
        String[] args = {metaContactUid};
        mDB.delete(TBL_CHILD_CONTACTS, MetaContactGroup.MC_UID + "=?", args);

        // cmeng - need to remove from contacts table if not found in childContacts table
        if (findProtoContactEntry(null, contactJid) == 0) {
            String protocolProvider = "Jabber";
            args = new String[]{protocolProvider, contactJid};
            mDB.delete(Contact.TABLE_NAME, Contact.PROTOCOL_PROVIDER + "=? AND "
                    + Contact.CONTACT_JID + "=?", args);
        }
    }

    /**
     * Indicates that a protocol specific <tt>Contact</tt> instance has been added to the list of
     * protocol specific buddies in this <tt>MetaContact</tt>
     *
     * Creates a table entry corresponding to <tt>Contact</tt>.
     *
     * @param evt a reference to the corresponding <tt>ProtoContactEvent</tt>
     */
    public void protoContactAdded(ProtoContactEvent evt)
    {
        MetaContact metaContact = evt.getParent();
        String mcUid = metaContact.getMetaUID();
        Contact contact = evt.getProtoContact();
        String contactJid = contact.getAddress();

        // Just logged in an internal err if the new contactJid already exist
        if (findProtoContactEntry(mcUid, contactJid) == 0) {
            Timber.d("Abort create new to an existing protoContact: %s", contactJid);
            return;
        }

        // Abort if contact does not have a parent group defined.
        ContactGroup parentGroup = contact.getParentContactGroup();
        if (parentGroup == null) {
            Timber.d("Abort entry creation, contact does not have a parent: %s", contact);
            return;
        }

        mcValues.clear();
        mcValues.put(MetaContactGroup.MC_UID, mcUid);

        String accountUuid = contact.getProtocolProvider().getAccountID().getAccountUuid();
        mcValues.put(MetaContactGroup.ACCOUNT_UUID, accountUuid);

        String protoGroupId = parentGroup.getUID();
        mcValues.put(MetaContactGroup.PROTO_GROUP_UID, protoGroupId);

        mcValues.put(MetaContactGroup.CONTACT_JID, contactJid);

        String displayName = metaContact.getDisplayName();
        mcValues.put(MetaContactGroup.MC_DISPLAY_NAME, displayName);

        JSONObject mcDetails = metaContact.getDetails();
        mcValues.put(MetaContactGroup.MC_DETAILS, mcDetails.toString());

        String persistentData = contact.getPersistentData();
        mcValues.put(MetaContactGroup.PERSISTENT_DATA, persistentData);

        mDB.insert(TBL_CHILD_CONTACTS, null, mcValues);

        // Create the contact entry only if not found in contacts table
        if (findContactEntry("Jabber", contactJid) == null) {
            mcValues.clear();
            mcValues.put(Contact.CONTACT_UUID, mcUid);
            mcValues.put(Contact.PROTOCOL_PROVIDER, "Jabber");
            mcValues.put(Contact.CONTACT_JID, contactJid);
            mcValues.put(Contact.SVR_DISPLAY_NAME, contact.getDisplayName());
            mDB.insert(Contact.TABLE_NAME, null, mcValues);
        }
    }

    /**
     * Updates the displayName for the contact that caused this event.
     *
     * @param evt the ProtoContactEvent containing the corresponding contact
     */
    public void protoContactRenamed(ProtoContactEvent evt)
    {
        // Just logged in an internal err if rename contact not found
        Contact contact = evt.getProtoContact();
        if ((contact == null) || (findContactEntry("Jabber", contact.getAddress()) == null)) {
            MetaContact metaContact = evt.getParent();
            String metaContactUid = metaContact.getMetaUID();

            Timber.d("Ignore debug info: ProtoContact not found for modification: %s for: %s",
                    evt.getParent(), metaContactUid);
            return;
        }

        // update the svrDisplayName for the specific contact
        String svrDisplayName = contact.getDisplayName();
        if (StringUtils.isNotEmpty(svrDisplayName)) {
            String[] args = {"Jabber", contact.getAddress()};
            mcValues.clear();
            mcValues.put(Contact.SVR_DISPLAY_NAME, svrDisplayName);

            mDB.update(Contact.TABLE_NAME, mcValues, Contact.PROTOCOL_PROVIDER
                    + "=? AND " + Contact.CONTACT_JID + "=?", args);
        }
    }

    /**
     * Updates the data stored for the contact that caused this event. The changes can either be
     * persistent data change etc
     *
     * @param evt the ProtoContactEvent containing the corresponding contact
     */
    public void protoContactModified(ProtoContactEvent evt)
    {
        MetaContact metaContact = evt.getParent();
        String metaContactUid = metaContact.getMetaUID();
        String contactJid = findMetaContactEntry(metaContactUid);

        // Just logged in an internal err if rename contact not found
        if (contactJid == null) {
            Timber.d("Ignore debug ref: ProtoContact not found for modification: %s for: %s", evt.getParent(), metaContactUid);
            return;
        }

        // update the persistent data for the the specific contact
        mcValues.clear();
        Contact contact = evt.getProtoContact();
        String persistentData = contact.getPersistentData();
        if (StringUtils.isNotEmpty(persistentData)) {
            String contactAddress = contact.getAddress();
            String[] args = {metaContactUid, contactAddress};

            mcValues.put(MetaContactGroup.PERSISTENT_DATA, persistentData);
            mDB.update(TBL_CHILD_CONTACTS, mcValues, MetaContactGroup.MC_UID
                    + "=? AND " + MetaContactGroup.CONTACT_JID + "=?", args);
        }
    }

    /**
     * Indicates that a protocol specific <tt>Contact</tt> instance has been moved from within one
     * <tt>MetaContact</tt> to another.
     *
     * @param evt a reference to the <tt>ProtoContactMovedEvent</tt> instance.
     */
    public void protoContactMoved(ProtoContactEvent evt)
    {
        String oldMcUid = evt.getOldParent().getMetaUID();
        String contactJid = evt.getProtoContact().getAddress();

        // Just logged in an internal err if the contactJid of the oldMcUid entry not found
        if (findProtoContactEntry(oldMcUid, contactJid) == 0) {
            Timber.d("Failed to find the metaContact for moving: %s", contactJid);
            return;
        }

        String newMcUid = evt.getNewParent().getMetaUID();
        String groupName = findMetaContactEntry(newMcUid);
        if (groupName == null) {
            Timber.d("Failed to find new destination metaContactGroup for: %s", newMcUid);
            return;
        }

        // Just modified the groupName of old contact
        String[] args = new String[]{oldMcUid, contactJid};
        mcValues.clear();
        mcValues.put(MetaContactGroup.MC_GROUP_NAME, groupName);
        mDB.update(TBL_CHILD_CONTACTS, mcValues, MetaContactGroup.MC_UID
                + "=? AND " + MetaContactGroup.CONTACT_JID + "=?", args);
    }

    /**
     * Remove the contact in the metaContact entry from the childContacts table;
     * also the contact entry in contacts table if none found in childContacts after removal.
     *
     * Note: Both the contact chatSession and its associated chat messages are left in the DB
     * User may remove this in ChatSessionFragment when an invalid entity is selected.
     *
     * @param evt a reference to the corresponding <tt>ProtoContactEvent</tt>
     */
    public void protoContactRemoved(ProtoContactEvent evt)
    {
        String mcUid = evt.getParent().getMetaUID();
        String contactJid = evt.getProtoContact().getAddress();

        // Just logged in an internal err if the contactJid of the mcUid entry not found
        if (findProtoContactEntry(mcUid, contactJid) == 0) {
            Timber.d("Failed to find the protoContact for removal: %s", contactJid);
            return;
        }

        String[] args = {mcUid, contactJid};
        mDB.delete(TBL_CHILD_CONTACTS, MetaContactGroup.MC_UID + "=? AND "
                + MetaContactGroup.CONTACT_JID + "=?", args);

        // cmeng - need to remove from contacts if none found in contactList
        if (findProtoContactEntry(null, contactJid) == 0) {
            String protocolProvider = "Jabber";
            args = new String[]{protocolProvider, contactJid};
            mDB.delete(Contact.TABLE_NAME, Contact.PROTOCOL_PROVIDER + "=? AND "
                    + Contact.CONTACT_JID + "=?", args);
        }
    }

    /**
     * Return the unique contact UUID corresponding to the contact with the specified protocol
     * provider or null if no such entry was found in the contacts table.
     *
     * @param protocolProvider the protocol provider of contact whose UUID we are looking for.
     * @param jid the jid String of the contact whose UUID we are looking for.
     * @return the contact UUID corresponding to the contact with the specified protocol provider
     * and Jid or null if no such contact was found in the contact table.
     */
    private String findContactEntry(String protocolProvider, String jid)
    {
        String[] columns = {Contact.CONTACT_UUID};
        String[] args = {protocolProvider, jid};
        Cursor cursor = mDB.query(Contact.TABLE_NAME, columns, Contact.PROTOCOL_PROVIDER
                + "=? AND " + Contact.CONTACT_JID + "=?", args, null, null, null);

        String contactUuid = null;
        while (cursor.moveToNext()) {
            contactUuid = cursor.getString(0);
        }
        cursor.close();
        return contactUuid;
    }

    /**
     * Return the number of entries in the childContacts table with the given contactJid AND
     * metaContact Uuid if given i.e. non-null)
     *
     * @param contactJid the contact Jid we are looking for.
     * @return the number of entry with the given contactJid.
     */
    private int findProtoContactEntry(String mcUid, String contactJid)
    {
        Cursor cursor;
        String[] args = {contactJid};

        if (StringUtils.isEmpty(mcUid)) {
            cursor = mDB.query(TBL_CHILD_CONTACTS, null,
                    MetaContactGroup.CONTACT_JID + "=?", args, null, null, null);
        }
        else {
            args = new String[]{mcUid, contactJid};
            cursor = mDB.query(TBL_CHILD_CONTACTS, null,
                    MetaContactGroup.MC_UID + "=? AND " + MetaContactGroup.CONTACT_JID + "=?",
                    args, null, null, null);
        }
        int count = cursor.getCount();
        cursor.close();
        return count;
    }

    /**
     * Return the contactJid corresponding to the specified metaContact uid or null if none is found.
     * Note: each metaContactUid may contain more than one contactJid entry in the table.
     * However current aTalk implementation is that metaContact has only single contactJid entry.
     *
     * @param metaContactUID the UID String of the metaContact whose contactJid we are looking for.
     * @return the contactJid corresponding to the metaContact with the specified UID or null if no
     * such metaContactUid entry was found in the metaContactGroup table.
     */
    private String findMetaContactEntry(String metaContactUID)
    {
        String[] columns = {MetaContactGroup.CONTACT_JID};
        String[] args = {metaContactUID};
        Cursor cursor = mDB.query(TBL_CHILD_CONTACTS, columns,
                MetaContactGroup.MC_UID + "=?", args, null, null, null);

        String contactJid = null;
        while (cursor.moveToNext()) {
            contactJid = cursor.getString(0);
        }
        cursor.close();
        return contactJid;
    }

    /**
     * Return the metaContact GroupName corresponding to the metaContactGroup with the specified
     * uid or null if no found.
     *
     * @param metaContactGroupUID the metaContactGroup UID whose groupName we are looking for.
     * @return the metaContact GroupName corresponding to the metaContactGroup with the
     * specified UID or null if no such group was found in the metaContactGroup table.
     */
    private String findMetaContactGroupEntry(String metaContactGroupUID)
    {
        String[] columns = {MetaContactGroup.MC_GROUP_NAME};
        String[] args = {metaContactGroupUID};
        Cursor cursor = mDB.query(MetaContactGroup.TABLE_NAME, columns,
                MetaContactGroup.MC_GROUP_UID + "=?", args, null, null, null);

        String mcGroupName = null;
        while (cursor.moveToNext()) {
            mcGroupName = cursor.getString(0);
        }
        cursor.close();
        return mcGroupName;
    }

    /**
     * Contains details parsed out of the database, necessary for creating unresolved contacts.
     */
    static class StoredProtoContactDescriptor
    {
        String contactAddress;
        String persistentData;
        ContactGroup parentProtoGroup;

        StoredProtoContactDescriptor(String contactAddress, String persistentData, ContactGroup parentProtoGroup)
        {
            this.contactAddress = contactAddress;
            this.persistentData = persistentData;
            this.parentProtoGroup = parentProtoGroup;
        }

        /**
         * Returns a string representation of the descriptor.
         *
         * @return a string representation of the descriptor.
         */
        @NonNull
        @Override
        public String toString()
        {
            return "StoredProtocolContactDescriptor[ " + " contactAddress=" + contactAddress
                    + " persistentData=" + persistentData + " parentProtoGroup="
                    + ((parentProtoGroup == null) ? "" : parentProtoGroup.getGroupName()) + "]";
        }

        /**
         * Utility method that allows us to verify whether a ContactDescriptor corresponding to a
         * particular contact is already in a descriptor list and thus eliminate duplicates.
         *
         * @param contactAddress the address of the contact whose descriptor we are looking for.
         * @param list the <tt>List</tt> of <tt>StoredProtoContactDescriptor</tt> that we are supposed to
         * search for <tt>contactAddress</tt>
         * @return a <tt>StoredProtoContactDescriptor</tt> corresponding to
         * <tt>contactAddress</tt> or <tt>null</tt> if no such descriptor exists.
         */
        private static StoredProtoContactDescriptor findContactInList(String contactAddress,
                List<StoredProtoContactDescriptor> list)
        {
            if (list != null && list.size() > 0) {
                for (StoredProtoContactDescriptor desc : list) {
                    if (desc.contactAddress.equals(contactAddress))
                        return desc;
                }
            }
            return null;
        }
    }

    /**
     * We simply ignore - we're not interested in this kind of events.
     *
     * @param evt the <tt>MetaContactGroupEvent</tt> containing details of this event.
     */
    public void childContactsReordered(MetaContactGroupEvent evt)
    {
        // ignore - not interested in such kind of events
    }

    /**
     * Indicates that a new avatar is available for a <tt>MetaContact</tt>.
     *
     * @param evt the <tt>MetaContactAvatarUpdateEvent</tt> containing details of this event
     */
    public void metaContactAvatarUpdated(MetaContactAvatarUpdateEvent evt)
    {
        // TODO Store MetaContact avatar.
    }
}
