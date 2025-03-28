/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the
 * License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for
 * the specific language governing permissions
 * and limitations under the License.
 */
package net.java.sip.communicator.impl.contactlist;

import android.text.TextUtils;

import androidx.annotation.NonNull;

import java.util.ArrayList;
import java.util.EventObject;
import java.util.Hashtable;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Vector;

import net.java.sip.communicator.service.contactlist.MetaContact;
import net.java.sip.communicator.service.contactlist.MetaContactGroup;
import net.java.sip.communicator.service.contactlist.MetaContactListException;
import net.java.sip.communicator.service.contactlist.MetaContactListService;
import net.java.sip.communicator.service.contactlist.event.MetaContactAvatarUpdateEvent;
import net.java.sip.communicator.service.contactlist.event.MetaContactEvent;
import net.java.sip.communicator.service.contactlist.event.MetaContactGroupEvent;
import net.java.sip.communicator.service.contactlist.event.MetaContactListListener;
import net.java.sip.communicator.service.contactlist.event.MetaContactModifiedEvent;
import net.java.sip.communicator.service.contactlist.event.MetaContactMovedEvent;
import net.java.sip.communicator.service.contactlist.event.MetaContactPropertyChangeEvent;
import net.java.sip.communicator.service.contactlist.event.MetaContactRenamedEvent;
import net.java.sip.communicator.service.contactlist.event.ProtoContactEvent;
import net.java.sip.communicator.service.protocol.AccountID;
import net.java.sip.communicator.service.protocol.Contact;
import net.java.sip.communicator.service.protocol.ContactGroup;
import net.java.sip.communicator.service.protocol.OperationFailedException;
import net.java.sip.communicator.service.protocol.OperationSetContactCapabilities;
import net.java.sip.communicator.service.protocol.OperationSetMultiUserChat;
import net.java.sip.communicator.service.protocol.OperationSetPersistentPresence;
import net.java.sip.communicator.service.protocol.OperationSetPresence;
import net.java.sip.communicator.service.protocol.ProtocolProviderFactory;
import net.java.sip.communicator.service.protocol.ProtocolProviderService;
import net.java.sip.communicator.service.protocol.event.ContactCapabilitiesEvent;
import net.java.sip.communicator.service.protocol.event.ContactCapabilitiesListener;
import net.java.sip.communicator.service.protocol.event.ContactPresenceStatusChangeEvent;
import net.java.sip.communicator.service.protocol.event.ContactPresenceStatusListener;
import net.java.sip.communicator.service.protocol.event.ContactPropertyChangeEvent;
import net.java.sip.communicator.service.protocol.event.ServerStoredGroupEvent;
import net.java.sip.communicator.service.protocol.event.ServerStoredGroupListener;
import net.java.sip.communicator.service.protocol.event.SubscriptionEvent;
import net.java.sip.communicator.service.protocol.event.SubscriptionListener;
import net.java.sip.communicator.service.protocol.event.SubscriptionMovedEvent;

import org.atalk.impl.timberlog.TimberLog;
import org.jivesoftware.smackx.avatar.AvatarManager;
import org.json.JSONObject;
import org.osgi.framework.BundleContext;
import org.osgi.framework.InvalidSyntaxException;
import org.osgi.framework.ServiceEvent;
import org.osgi.framework.ServiceListener;
import org.osgi.framework.ServiceReference;

import timber.log.Timber;

/**
 * An implementation of the MetaContactListService that would connect to protocol service
 * providers and build its contact list accordingly basing itself on the contact list stored by
 * the various protocol provider services and the contact list instance saved in persistent store.
 *
 * @author Emil Ivov
 * @author Eng Chong Meng
 */
public class MetaContactListServiceImpl implements MetaContactListService, ServiceListener,
        ContactPresenceStatusListener, ContactCapabilitiesListener {
    /**
     * The BundleContext that we got from the OSGI bus.
     */
    private BundleContext mBundleContext = null;

    /**
     * The list of AccountUID to protocol providers that we're currently aware of
     */
    private final Map<String, ProtocolProviderService> mCurrentlyInstalledProviders = new Hashtable<>();

    /**
     * The root of the meta contact list.
     */
    public final MetaContactGroupImpl rootMetaGroup;

    /**
     * The event handler that will be handling our subscription events.
     */
    private final ContactListSubscriptionListener clSubscriptionEventHandler = new ContactListSubscriptionListener();

    /**
     * The event handler that will be handling group events.
     */
    private final ContactListGroupListener clGroupEventHandler = new ContactListGroupListener();

    /**
     * The number of milliseconds to wait for confirmations of account modifications before deciding to drop.
     */
    private static final int CONTACT_LIST_MODIFICATION_TIMEOUT = 10000;

    /**
     * Listeners interested in events dispatched upon modification of the meta contact list.
     */
    private final List<MetaContactListListener> mMetaContactListListeners = new Vector<>();

    /**
     * Contains (as keys) <code>MetaContactGroup</code> names that are currently being resolved
     * against a given protocol and that this class's <code>ContactGroupListener</code> should ignore
     * as corresponding events will be handled by the corresponding methods. The table maps the
     * meta contact group names against lists of protocol providers. An incoming group event would
     * therefore be ignored by the class group listener if and only if it carries a name present
     * in this table and is issued by one of the providers mapped against this groupName.
     */
    private final Hashtable<String, List<ProtocolProviderService>> mGroupEventIgnoreList = new Hashtable<>();

    /**
     * Contains (as keys) <code>Contact</code> addresses that are currently being resolved against a
     * given protocol and that this class's <code>ContactListener</code> should ignore as
     * corresponding events will be handled by the corresponding methods. The table maps the meta
     * contact addresses against lists of protocol providers. An incoming group event would
     * therefore be ignored by the class group listener if and only if it carries a name present
     * in this table and is issued by one of the providers mapped against this groupName.
     */
    private final Hashtable<String, List<ProtocolProviderService>> mContactEventIgnoreList = new Hashtable<>();

    /**
     * The instance of the storage manager which is handling the local copy of our contact list.
     */
    private final MclStorageManager storageManager = new MclStorageManager();

    /**
     * Creates an instance of this class.
     */
    public MetaContactListServiceImpl() {
        rootMetaGroup = new MetaContactGroupImpl(this, ContactGroup.ROOT_GROUP_NAME, ContactGroup.ROOT_GROUP_UID);
    }

    /**
     * Starts this implementation of the MetaContactListService. The implementation would first
     * restore a default contact list from a persistent storage. It would then connect
     * to OSGI and retrieve any existing protocol providers and if <br>
     * 1) They provide implementations of OperationSetPersistentPresence, it would synchronize
     * their contact lists with the local one (adding subscriptions for contacts that do not
     * exist in the server stored contact list and adding locally contacts that were found on
     * the server but not in the local database).
     * <p>
     * 2) The only provide non persistent implementations of OperationSetPresence, the meta contact list
     * impl would create subscriptions for all local contacts in the corresponding protocol provider.
     * <p>
     * This implementation would also start listening for any newly registered protocol provider
     * implementations and perform the same algorithm with them.
     * <p>
     *
     * @param bc the currently valid OSGI bundle context.
     */
    public void start(BundleContext bc) {
        Timber.d("Starting the meta contact list implementation.");
        mBundleContext = bc;

        // initialize the meta contact list from what has been stored locally.
        try {
            storageManager.start(mBundleContext, this);
        } catch (Exception ex) {
            Timber.e("Failed loading the stored contact list: %s", ex.getMessage());
        }

        // start listening for newly register or removed protocol providers
        bc.addServiceListener(this);
        ServiceReference[] ppsRefs = null;
        try {
            // first discover the icq service then find the protocol provider service
            ppsRefs = mBundleContext.getServiceReferences(ProtocolProviderService.class.getName(), null);
        } catch (InvalidSyntaxException e) {
            Timber.e("Exception: %s", e.getMessage());
        }
        // in case we found any, retrieve the root groups for all protocol providers and create the meta contact list
        if (ppsRefs != null) {
            for (ServiceReference<ProtocolProviderService> ppsRef : ppsRefs) {
                ProtocolProviderService pps = mBundleContext.getService(ppsRef);
                handleProviderAdded(pps);
            }
        }
    }

    /**
     * Prepares the meta contact list service for shut-down.
     *
     * @param bc the currently active bundle context.
     */
    public void stop(BundleContext bc) {
        bc.removeServiceListener(this);

        // stop listening to all currently installed providers
        for (ProtocolProviderService pps : mCurrentlyInstalledProviders.values()) {
            OperationSetPersistentPresence opSetPersPresence = pps.getOperationSet(OperationSetPersistentPresence.class);

            if (opSetPersPresence != null) {
                opSetPersPresence.removeContactPresenceStatusListener(this);
                opSetPersPresence.removeSubscriptionListener(clSubscriptionEventHandler);
                opSetPersPresence.removeServerStoredGroupChangeListener(clGroupEventHandler);
            }
            else {
                // check if a non persistent presence operation set exists.
                OperationSetPresence opSetPresence = pps.getOperationSet(OperationSetPresence.class);

                if (opSetPresence != null) {
                    opSetPresence.removeContactPresenceStatusListener(this);
                    opSetPresence.removeSubscriptionListener(clSubscriptionEventHandler);
                }
            }
        }
        mCurrentlyInstalledProviders.clear();
    }

    /**
     * Adds a listener for <code>MetaContactListChangeEvent</code>s posted after the tree changes.
     *
     * @param listener the listener to add
     */
    public void addMetaContactListListener(MetaContactListListener listener) {
        synchronized (mMetaContactListListeners) {
            if (!mMetaContactListListeners.contains(listener))
                mMetaContactListListeners.add(listener);
        }
    }

    /**
     * First makes the specified protocol provider create the contact as indicated by <code>contactID</code>,
     * and then associates it to the _existing_ <code>metaContact</code> given as an argument.
     *
     * @param provider the ProtocolProviderService that should create the contact indicated by <code>contactID</code>.
     * @param metaContact the meta contact where that the newly created contact should be associated to.
     * @param contactID the identifier of the contact that the specified provider
     *
     * @throws MetaContactListException with an appropriate code if the operation fails for some reason.
     */
    public void addNewContactToMetaContact(ProtocolProviderService provider, MetaContact metaContact, String contactID)
            throws MetaContactListException {
        addNewContactToMetaContact(provider, metaContact, contactID, true);
    }

    /**
     * First makes the specified protocol provider create the contact as indicated by <code>contactID</code>,
     * and then associates it to the _existing_ <code>metaContact</code> given as an argument.
     *
     * @param provider the ProtocolProviderService that should create the contact indicated by <code>contactID</code>.
     * @param metaContact the meta contact where that the newly created contact should be associated to.
     * @param contactID the identifier of the contact that the specified provider
     * @param fireEvent specifies whether an even is to be fire at the end of the method.Used when this
     * method is called upon creation of a new meta contact and not only a new contact.
     *
     * @throws MetaContactListException with an appropriate code if the operation fails for some reason.
     */
    public void addNewContactToMetaContact(ProtocolProviderService provider,
            MetaContact metaContact, String contactID, boolean fireEvent)
            throws MetaContactListException {
        // find the parent group in the corresponding protocol.
        MetaContactGroup parentMetaGroup = findParentMetaContactGroup(metaContact);

        if (parentMetaGroup == null) {
            throw new MetaContactListException("orphan Contact: " + metaContact, null,
                    MetaContactListException.CODE_NETWORK_ERROR);
        }
        addNewContactToMetaContact(provider, parentMetaGroup, metaContact, contactID, fireEvent);
    }

    /**
     * First makes the specified protocol provider create the contact as indicated  <code>contactID</code>,
     * and then associates it to the _existing_ <code>metaContact</code> given as an argument.
     *
     * @param provider the ProtocolProviderService that should create the contact indicated by <code>contactID</code>.
     * @param parentMetaGroup the meta contact group which is the parent group of the newly created contact
     * @param metaContact the meta contact where that the newly created contact should be associated to.
     * @param contactID the identifier of the contact that the specified provider
     * @param fireEvent specifies whether an even is to be fired at the end of the method.Used when
     * this method is called upon creation of a new meta contact and not only a new contact.
     *
     * @throws MetaContactListException with an appropriate code if the operation fails for some reason.
     */
    private void addNewContactToMetaContact(ProtocolProviderService provider, MetaContactGroup parentMetaGroup,
            MetaContact metaContact, String contactID, boolean fireEvent)
            throws MetaContactListException {
        OperationSetPersistentPresence opSetPersPresence = provider.getOperationSet(OperationSetPersistentPresence.class);
        if (opSetPersPresence == null) {
            /* @todo handle non-persistent presence operation sets as well */
            return;
        }

        if (!(metaContact instanceof MetaContactImpl)) {
            throw new IllegalArgumentException(metaContact + " is not an instance of MetaContactImpl");
        }

        ContactGroup parentProtoGroup = resolveProtoPath(provider, (MetaContactGroupImpl) parentMetaGroup);
        if (parentProtoGroup == null) {
            throw new MetaContactListException("Could not obtain proto group parent for "
                    + metaContact, null, MetaContactListException.CODE_NETWORK_ERROR);
        }
        BlockingSubscriptionEventRetriever evtRetriever = new BlockingSubscriptionEventRetriever(contactID);

        addContactToEventIgnoreList(contactID, provider);
        opSetPersPresence.addSubscriptionListener(evtRetriever);
        opSetPersPresence.addServerStoredGroupChangeListener(evtRetriever);

        try {
            // create and subscribe the contact in the group; if it is the root group just call subscribe
            if (parentMetaGroup.equals(rootMetaGroup))
                opSetPersPresence.subscribe(provider, contactID);
            else
                opSetPersPresence.subscribe(parentProtoGroup, contactID);

            // wait for a confirmation event
            evtRetriever.waitForEvent(CONTACT_LIST_MODIFICATION_TIMEOUT);
        } catch (OperationFailedException ex) {
            if (ex.getErrorCode() == OperationFailedException.SUBSCRIPTION_ALREADY_EXISTS) {
                throw new MetaContactListException("failed to create contact " + contactID, ex,
                        MetaContactListException.CODE_CONTACT_ALREADY_EXISTS_ERROR);
            }
            else if (ex.getErrorCode() == OperationFailedException.NOT_SUPPORTED_OPERATION) {
                throw new MetaContactListException("failed to create contact " + contactID, ex,
                        MetaContactListException.CODE_NOT_SUPPORTED_OPERATION);
            }
            throw new MetaContactListException("failed to create contact " + contactID, ex,
                    MetaContactListException.CODE_NETWORK_ERROR);
        } catch (Exception ex) {
            throw new MetaContactListException("failed to create contact " + contactID, ex,
                    MetaContactListException.CODE_NETWORK_ERROR);
        } finally {
            // whatever happens we need to remove the event collector end the ignore filter.
            removeContactFromEventIgnoreList(contactID, provider);
            opSetPersPresence.removeSubscriptionListener(evtRetriever);
        }
        // attach the newly created contact to a meta contact
        if (evtRetriever.mEvent == null) {
            throw new MetaContactListException("Failed to create a contact with address: "
                    + contactID, null, MetaContactListException.CODE_NETWORK_ERROR);
        }

        if (evtRetriever.mEvent instanceof SubscriptionEvent
                && ((SubscriptionEvent) evtRetriever.mEvent).getEventID() == SubscriptionEvent.SUBSCRIPTION_FAILED) {
            throw new MetaContactListException(
                    "Failed to create a contact with address: " + contactID + " "
                            + ((SubscriptionEvent) evtRetriever.mEvent).getErrorReason(), null,
                    MetaContactListException.CODE_UNKNOWN_ERROR);
        }

        // now finally - add the contact to the meta contact
        ((MetaContactImpl) metaContact).addProtoContact(evtRetriever.mSourceContact);

        // only fire an event here if the calling method wants us to. in case this is the creation
        // of a new contact and not only addition of a proto contact we should remain silent and
        // the calling method will do the eventing.
        if (fireEvent) {
            fireProtoContactEvent(evtRetriever.mSourceContact,
                    ProtoContactEvent.PROTO_CONTACT_ADDED, null, metaContact);
        }
        ((MetaContactGroupImpl) parentMetaGroup).addMetaContact((MetaContactImpl) metaContact);
    }

    /**
     * Makes sure the directories in the whole path from the root to the specified group have
     * corresponding directories in the protocol indicated by <code>protoProvider</code>.
     * The method does not return before creating all groups has completed.
     *
     * @param protoProvider a reference to the protocol provider where the groups should be created.
     * @param metaGroup a ref to the last group of the path that should be created in the specified <code>protoProvider</code>
     *
     * @return e reference to the newly created <code>ContactGroup</code>
     */
    private ContactGroup resolveProtoPath(ProtocolProviderService protoProvider, MetaContactGroupImpl metaGroup) {
        // NA for aTalk, as groups are stored in DB
        // Iterator<ContactGroup> contactGroupsForPPS = metaGroup.getContactGroupsForProvider(protoProvider);
        // if (contactGroupsForPPS.hasNext()) {
        // we already have at least one group corresponding to the metaGroup
        //     return contactGroupsForPPS.next();
        // }

        OperationSetPersistentPresence opSetPersPresence
                = protoProvider.getOperationSet(OperationSetPersistentPresence.class);

        // if persistent presence is not supported - just bail out as we should have verified this earlier anyway
        if (opSetPersPresence == null) {
            return null;
        }

        ContactGroup parentProtoGroup;
        // MetaContactGroupImpl parentMetaGroup = (MetaContactGroupImpl) findParentMetaContactGroup(metaGroup);
        MetaContactGroupImpl parentMetaGroup = (MetaContactGroupImpl) metaGroup.getParentMetaContactGroup();
        // if (parentMetaGroup == null) {
        //     Timber.d("Resolve failed at group %s", metaGroup);
        //    throw new NullPointerException("Internal Error. Orphan group.");
        // }

        // special treatment for the root group (stop the recursion and return the root contactGroup
        if (parentMetaGroup == null) {
            Timber.d("Assume RootGroup, resolve parentMetaGroup failed: %s", metaGroup);
            return opSetPersPresence.getServerStoredContactListRoot();
        }
        else {
            parentProtoGroup = resolveProtoPath(protoProvider, parentMetaGroup);

            // Return the existing contactGroup if found.
            ContactGroup contactGroup = parentProtoGroup.getGroup(metaGroup.getGroupName());
            if (contactGroup != null)
                return contactGroup;
        }

        // create the proto group
        BlockingGroupEventRetriever evtRetriever = new BlockingGroupEventRetriever(metaGroup.getGroupName());
        opSetPersPresence.addServerStoredGroupChangeListener(evtRetriever);
        addGroupToEventIgnoreList(metaGroup.getGroupName(), protoProvider);

        try {
            // create the group
            opSetPersPresence.createServerStoredContactGroup(parentProtoGroup, metaGroup.getGroupName());

            // wait for a confirmation event
            evtRetriever.waitForEvent(CONTACT_LIST_MODIFICATION_TIMEOUT);
        } catch (Exception ex) {
            throw new MetaContactListException("failed to create contact group " + metaGroup.getGroupName(),
                    ex, MetaContactListException.CODE_NETWORK_ERROR);
        } finally {
            // whatever happens we need to remove the event collector and ignore filter.
            removeGroupFromEventIgnoreList(metaGroup.getGroupName(), protoProvider);
            opSetPersPresence.removeServerStoredGroupChangeListener(evtRetriever);
        }

        // something went wrong.
        if (evtRetriever.mEvent == null) {
            throw new MetaContactListException("Failed to create a proto group named: " + metaGroup.getGroupName(),
                    null, MetaContactListException.CODE_NETWORK_ERROR);
        }

        // now add the proto group to the meta group.
        metaGroup.addProtoGroup(evtRetriever.mEvent.getSourceGroup());

        fireMetaContactGroupEvent(metaGroup, evtRetriever.mEvent.getSourceProvider(),
                evtRetriever.mEvent.getSourceGroup(), MetaContactGroupEvent.CONTACT_GROUP_ADDED_TO_META_GROUP);
        return evtRetriever.mEvent.getSourceGroup();
    }

    /**
     * Returns the meta contact group that is a direct parent of the specified <code>child</code>. If
     * no parent is found <code>null</code> is returned.
     *
     * @param child the <code>MetaContactGroup</code> whose parent group we're looking for. If no parent is
     * found <code>null</code> is returned.
     *
     * @return the <code>MetaContactGroup</code> that contains <code>child</code> or null if no parent was found.
     */
    public MetaContactGroup findParentMetaContactGroup(MetaContactGroup child) {
        return findParentMetaContactGroup(rootMetaGroup, child);
    }

    /**
     * Returns the meta contact group that is a direct parent of the specified <code>child</code>,
     * beginning the search at the specified root. If no parent is found <code>null</code> is returned.
     *
     * @param child the <code>MetaContactGroup</code> whose parent group we're looking for.
     * @param root the parent where the search should start.
     *
     * @return the <code>MetaContactGroup</code> that contains <code>child</code> or null if no parent was found.
     */
    private MetaContactGroup findParentMetaContactGroup(MetaContactGroupImpl root, MetaContactGroup child) {
        return child.getParentMetaContactGroup();
    }

    /**
     * Returns the meta contact group that is a direct parent of the specified <code>child</code>.
     *
     * @param child the <code>MetaContact</code> whose parent group we're looking for.
     *
     * @return the <code>MetaContactGroup</code>
     *
     * @throws IllegalArgumentException if <code>child</code> is not an instance of MetaContactImpl
     */
    public MetaContactGroup findParentMetaContactGroup(MetaContact child) {
        if (!(child instanceof MetaContactImpl)) {
            throw new IllegalArgumentException(child + " is not a MetaContactImpl instance.");
        }
        return ((MetaContactImpl) child).getParentGroup();
    }

    /**
     * First makes the specified protocol provider create a contact corresponding to the specified
     * <code>contactID</code>, then creates a new MetaContact which will encapsulate the newly
     * created protocol specific contact.
     *
     * @param provider a ref to <code>ProtocolProviderService</code> instance which will create the actual
     * protocol specific contact.
     * @param metaContactGroup the MetaContactGroup where the newly created meta contact should be stored.
     * @param contactID a protocol specific string identifier indicating the contact the protocol provider
     * should create.
     *
     * @return the newly created <code>MetaContact</code>
     *
     * @throws MetaContactListException with an appropriate code if the operation fails for some reason.
     */
    public MetaContact createMetaContact(ProtocolProviderService provider, MetaContactGroup metaContactGroup, String contactID)
            throws MetaContactListException {
        if (!(metaContactGroup instanceof MetaContactGroupImpl)) {
            throw new IllegalArgumentException(metaContactGroup + " is not an instance of MetaContactGroupImpl");
        }

        MetaContactImpl newMetaContact = new MetaContactImpl();
        addNewContactToMetaContact(provider, metaContactGroup, newMetaContact, contactID, false);

        // don't fire a PROTO_CONT_ADDED event we'll fire our own event here.
        fireMetaContactEvent(newMetaContact, findParentMetaContactGroup(newMetaContact), MetaContactEvent.META_CONTACT_ADDED);
        return newMetaContact;
    }

    /**
     * Creates a <code>MetaContactGroup</code> with the specified group name. The meta contact group
     * would only be created locally and resolved against the different server stored protocol
     * contact lists upon the creation of the first protocol specific child contact in the
     * respective group.
     *
     * @param parent the meta contact group inside which the new child group must be created.
     * @param groupName the name of the <code>MetaContactGroup</code> to create.
     *
     * @return the newly created <code>MetaContactGroup</code>
     *
     * @throws MetaContactListException with an appropriate code if the operation fails for some reason.
     */
    public MetaContactGroup createMetaContactGroup(MetaContactGroup parent, String groupName)
            throws MetaContactListException {
        if (!(parent instanceof MetaContactGroupImpl)) {
            throw new IllegalArgumentException(parent + " is not an instance of MetaContactGroupImpl");
        }

        // make sure that "parent" does not already contain a subgroup called "groupName"
        Iterator<MetaContactGroup> subgroups = parent.getSubgroups();
        while (subgroups.hasNext()) {
            MetaContactGroup group = subgroups.next();
            if (group.getGroupName().equals(groupName)) {
                throw new MetaContactListException("Parent " + parent.getGroupName()
                        + " already contains a group called " + groupName,
                        new CloneNotSupportedException("just testing nested exc-s"),
                        MetaContactListException.CODE_GROUP_ALREADY_EXISTS_ERROR);
            }
        }
        // we only have to create the meta contact group here. we don't care about protocol
        // specific groups.
        MetaContactGroupImpl newMetaGroup = new MetaContactGroupImpl(this, groupName);
        ((MetaContactGroupImpl) parent).addSubgroup(newMetaGroup);

        // fire the event (pps is null)
        fireMetaContactGroupEvent(newMetaGroup, null, null, MetaContactGroupEvent.META_CONTACT_GROUP_ADDED);
        return newMetaGroup;
    }

    /**
     * Renames the specified <code>MetaContactGroup</code> as indicated by the <code>newName</code> param.
     * The operation would only affect the local meta group and would not "touch" any
     * encapsulated protocol specific group.
     * <p>
     *
     * @param group the group to rename.
     * @param newGroupName the new name of the <code>MetaContactGroup</code> to rename.
     */
    public void renameMetaContactGroup(MetaContactGroup group, String newGroupName) {
        ((MetaContactGroupImpl) group).setGroupName(newGroupName);

        Iterator<ContactGroup> groups = group.getContactGroups();
        while (groups.hasNext()) {
            ContactGroup protoGroup = groups.next();

            // get a persistent presence operation set
            OperationSetPersistentPresence opSetPresence
                    = protoGroup.getProtocolProvider().getOperationSet(OperationSetPersistentPresence.class);
            if (opSetPresence != null) {
                try {
                    opSetPresence.renameServerStoredContactGroup(protoGroup, newGroupName);
                } catch (Throwable t) {
                    Timber.e(t, "Error renaming protocol group: %s", protoGroup);
                }
            }
        }
        fireMetaContactGroupEvent(group, null, null,
                MetaContactGroupEvent.META_CONTACT_GROUP_RENAMED);
    }

    /**
     * Returns the root <code>MetaContactGroup</code> in this contact list.
     *
     * @return the root <code>MetaContactGroup</code> for this contact list.
     */
    public MetaContactGroup getRoot() {
        return rootMetaGroup;
    }

    /**
     * Sets the display name for <code>metaContact</code> to be <code>newName</code>.
     *
     * @param metaContact the <code>MetaContact</code> that we are renaming
     * @param newDisplayName a <code>String</code> containing the new display name for <code>metaContact</code>.
     *
     * @throws IllegalArgumentException if <code>metaContact</code> is not an instance that belongs to the underlying
     * implementation.
     */
    public void renameMetaContact(MetaContact metaContact, String newDisplayName)
            throws IllegalArgumentException {
        renameMetaContact(metaContact, newDisplayName, true);
    }

    /**
     * Sets the display name for <code>metaContact</code> to be <code>newName</code>.
     *
     * @param metaContact the <code>MetaContact</code> that we are renaming
     * @param newDisplayName a <code>String</code> containing the new display name for <code>metaContact</code>.
     *
     * @throws IllegalArgumentException if <code>metaContact</code> is not an instance that belongs to the underlying
     * implementation.
     */
    private void renameMetaContact(MetaContact metaContact, String newDisplayName, boolean isUserDefined)
            throws IllegalArgumentException {
        if (!(metaContact instanceof MetaContactImpl)) {
            throw new IllegalArgumentException(metaContact + " is not a MetaContactImpl instance.");
        }

        MetaContactImpl metaContactImpl = (MetaContactImpl) metaContact;
        String oldDisplayName = metaContactImpl.getDisplayName();
        metaContactImpl.setDisplayName(newDisplayName);
        metaContactImpl.setDisplayNameUserDefined(isUserDefined);

        Iterator<Contact> contacts = metaContactImpl.getContacts();
        while (contacts.hasNext()) {
            Contact protoContact = contacts.next();

            // get a persistent presence operation set
            OperationSetPersistentPresence opSetPresence = protoContact.getProtocolProvider()
                    .getOperationSet(OperationSetPersistentPresence.class);

            if (opSetPresence != null) {
                try {
                    opSetPresence.setDisplayName(protoContact, newDisplayName);
                } catch (Throwable t) {
                    Timber.e(t, "Error renaming protocol contact: %s", protoContact);
                }
            }
        }
        fireMetaContactEvent(new MetaContactRenamedEvent(metaContact, oldDisplayName, newDisplayName));

        // changing the display name has surely brought a change in the order as well so let's
        // tell the others
        fireMetaContactGroupEvent(findParentMetaContactGroup(metaContact), null, null,
                MetaContactGroupEvent.CHILD_CONTACTS_REORDERED);
    }

    /**
     * Resets display name of the MetaContact to show the value from the underlying contacts.
     *
     * @param metaContact the <code>MetaContact</code> that we are operating on
     *
     * @throws IllegalArgumentException if <code>metaContact</code> is not an instance that belongs to the underlying
     * implementation.
     */
    public void clearUserDefinedDisplayName(MetaContact metaContact)
            throws IllegalArgumentException {
        if (!(metaContact instanceof MetaContactImpl)) {
            throw new IllegalArgumentException(metaContact + " is not a MetaContactImpl instance.");
        }
        // set display name
        ((MetaContactImpl) metaContact).setDisplayNameUserDefined(false);

        if (metaContact.getContactCount() == 1) {
            renameMetaContact(metaContact, metaContact.getDefaultContact().getDisplayName(), false);
        }
        else {
            // just fire event so the modification is stored
            fireMetaContactEvent(new MetaContactRenamedEvent(metaContact,
                    metaContact.getDisplayName(), metaContact.getDisplayName()));
        }
    }

    /**
     * Sets the avatar for <code>metaContact</code> to be <code>newAvatar</code>.
     *
     * @param metaContact the <code>MetaContact</code> that change avatar
     * @param oldAvatarId the <code>Contact> that change avatar
     * @param newAvatarId avatar image bytes
     *
     * @throws IllegalArgumentException if <code>metaContact</code> is not an instance that belongs to the underlying
     * implementation.
     */
    public void changeMetaContactAvatar(MetaContact metaContact, String oldAvatarId, String newAvatarId)
            throws IllegalArgumentException {
        if (!(metaContact instanceof MetaContactImpl)) {
            //cmeng: do not throw; there is no proper handler defined. Just log and return
            // throw new IllegalArgumentException(metaContact + " is not a MetaContactImpl instance.");
            Timber.e("%s is not a MetaContactImpl instance.", metaContact);
            return;
        }

        ((MetaContactImpl) metaContact).cacheAvatar(null, AvatarManager.getAvatarImageByHash(newAvatarId));
        fireMetaContactEvent(new MetaContactAvatarUpdateEvent(metaContact, oldAvatarId, newAvatarId));
    }

    /**
     * Makes the specified <code>contact</code> a child move to the <code>newParentMetaGroup</code>
     * MetaContactGroup. If <code>contact</code> was previously a child of a meta contact, it will be
     * removed from its old parent and to a newly created one even if they both are in the same
     * group. If the specified contact was the only child of its previous parent, then the meta
     * contact will also be moved.
     *
     * @param contact the <code>Contact</code> to move to the
     * @param newParentMetaGroup the MetaContactGroup where we'd like contact to be moved.
     *
     * @throws MetaContactListException with an appropriate code if the operation fails for some reason.
     */
    public void moveContact(Contact contact, MetaContactGroup newParentMetaGroup)
            throws MetaContactListException {
        if (contact.getPersistableAddress() == null) {
            Timber.i("Contact cannot be moved! This contact doesn't have persistent address.");
            return;
        }

        ProtocolProviderService provider = contact.getProtocolProvider();
        OperationSetMultiUserChat opSetMUC = provider.getOperationSet(OperationSetMultiUserChat.class);
        if (opSetMUC != null && opSetMUC.isPrivateMessagingContact(contact.getJid())) {
            MetaContactImpl metaContactImpl = new MetaContactImpl();
            MetaContactGroupImpl newParentMetaGroupImpl = (MetaContactGroupImpl) newParentMetaGroup;

            newParentMetaGroupImpl.addMetaContact(metaContactImpl);
            fireMetaContactEvent(metaContactImpl, newParentMetaGroupImpl, MetaContactEvent.META_CONTACT_ADDED);
            addNewContactToMetaContact(provider, metaContactImpl, contact.getPersistableAddress());
            return;
        }
        // get a persistent presence operation set
        OperationSetPersistentPresence opSetPresence = provider.getOperationSet(OperationSetPersistentPresence.class);
        if (opSetPresence == null) {
            /* @todo handle non persistent presence operation sets */
        }
        MetaContactImpl currentParentMetaContact = (MetaContactImpl) findMetaContactByContact(contact);
        ContactGroup parentProtoGroup = resolveProtoPath(contact.getProtocolProvider(),
                (MetaContactGroupImpl) newParentMetaGroup);

        // if the contact is not currently in the proto group corresponding to its new metaContact
        // group parent then move it
        try {
            if ((contact.getParentContactGroup() != parentProtoGroup) && (opSetPresence != null)) {
                opSetPresence.moveContactToGroup(contact, parentProtoGroup);
            }
            // remove the proto-contact only if move is successful
            currentParentMetaContact.removeProtoContact(contact);
        } catch (OperationFailedException ex) {
            throw new MetaContactListException(ex.getMessage(), MetaContactListException.CODE_MOVE_CONTACT_ERROR);
        }

        // first check if this has been already done on other place
        // (SubscriptionListener.subscriptionMoved)
        MetaContactImpl metaContactImpl = null;
        synchronized (contact) {
            MetaContact checkContact = findMetaContactByContact(contact);
            if (checkContact == null) {
                metaContactImpl = new MetaContactImpl();
                ((MetaContactGroupImpl) newParentMetaGroup).addMetaContact(metaContactImpl);
                metaContactImpl.addProtoContact(contact);
            }
        }

        if (metaContactImpl != null) {
            fireMetaContactEvent(metaContactImpl, newParentMetaGroup, MetaContactEvent.META_CONTACT_ADDED);

            // fire an event telling everyone that contact has been added to its new parent.
            fireProtoContactEvent(contact, ProtoContactEvent.PROTO_CONTACT_MOVED,
                    currentParentMetaContact, metaContactImpl);
        }

        // if this was the last contact in the meta contact - remove it. it is true that in some
        // cases the move would be followed by some kind may trigger the removal of empty meta
        // contacts. Yet in many cases particularly if parent groups were not changed in the
        // protocol contact list no event would come and the meta contact will remain empty that's
        // why we delete it here and if an event follows it would simply be ignored.
        if (currentParentMetaContact.getContactCount() == 0) {
            MetaContactGroupImpl parentMetaGroup = currentParentMetaContact.getParentGroup();
            parentMetaGroup.removeMetaContact(currentParentMetaContact);

            fireMetaContactEvent(currentParentMetaContact, parentMetaGroup, MetaContactEvent.META_CONTACT_REMOVED);
        }
    }

    /**
     * Makes the specified <code>contact</code> a child of the <code>newParent</code> MetaContact.
     *
     * @param contact the <code>Contact</code> to move to the
     * @param newParentMetaContact the MetaContact where we'd like contact to be moved.
     *
     * @throws MetaContactListException with an appropriate code if the operation fails for some reason.
     */
    public void moveContact(Contact contact, MetaContact newParentMetaContact)
            throws MetaContactListException {
        if (contact.getPersistableAddress() == null) {
            Timber.i("Contact cannot be moved! This contact doesn't have persistent address.");
            return;
        }
        ProtocolProviderService provider = contact.getProtocolProvider();

        OperationSetMultiUserChat opSetMUC = provider.getOperationSet(OperationSetMultiUserChat.class);
        if (opSetMUC != null && opSetMUC.isPrivateMessagingContact(contact.getJid())) {
            addNewContactToMetaContact(provider, newParentMetaContact, contact.getPersistableAddress());
            return;
        }

        // get a persistent presence operation set
        OperationSetPersistentPresence opSetPresence = provider.getOperationSet(OperationSetPersistentPresence.class);

        if (opSetPresence == null) {
            /* @todo handle non persistent presence operation sets */
        }

        if (!(newParentMetaContact instanceof MetaContactImpl)) {
            throw new IllegalArgumentException(newParentMetaContact + " is not a MetaContactImpl instance.");
        }

        MetaContactImpl currentParentMetaContact = (MetaContactImpl) findMetaContactByContact(contact);
        MetaContactGroup newParentGroup = findParentMetaContactGroup(newParentMetaContact);
        ContactGroup parentProtoGroup
                = resolveProtoPath(contact.getProtocolProvider(), (MetaContactGroupImpl) newParentGroup);

        // if the contact is not currently in the proto group corresponding to its new metaContact
        // group parent then move it
        try {
            if (contact.getParentContactGroup() != parentProtoGroup && opSetPresence != null) {
                opSetPresence.moveContactToGroup(contact, parentProtoGroup);
            }
            // remove the proto-contact only if move is successful
            currentParentMetaContact.removeProtoContact(contact);
        } catch (OperationFailedException ex) {
            throw new MetaContactListException(ex.getMessage(), MetaContactListException.CODE_MOVE_CONTACT_ERROR);
        }

        synchronized (contact) {
            MetaContact checkContact = findMetaContactByContact(contact);
            if (checkContact == null) {
                ((MetaContactImpl) newParentMetaContact).addProtoContact(contact);
            }
        }

        if (newParentMetaContact.containsContact(contact)) {
            // fire an event telling everyone that contact has been added to its new parent.
            fireProtoContactEvent(contact, ProtoContactEvent.PROTO_CONTACT_MOVED,
                    currentParentMetaContact, newParentMetaContact);
        }

        // if this was the last contact in the meta contact - remove it. it is true that in some
        // cases the move would be followed by some kind of protocol provider events indicating
        // the change which on its turn may trigger the removal of empty meta contacts. Yet in
        // many cases particularly if parent groups were not changed in the protocol contact list
        // no event would come and the meta contact will remain empty that's why we delete it
        // here and if an event follows it would simply be ignored.
        if (currentParentMetaContact.getContactCount() == 0) {
            MetaContactGroupImpl parentMetaGroup = currentParentMetaContact.getParentGroup();
            parentMetaGroup.removeMetaContact(currentParentMetaContact);
            fireMetaContactEvent(currentParentMetaContact, parentMetaGroup,
                    MetaContactEvent.META_CONTACT_REMOVED);
        }
    }

    /**
     * Moves the specified <code>MetaContact</code> to <code>newGroup</code>.
     *
     * @param metaContact the <code>MetaContact</code> to move.
     * @param newMetaGroup the <code>MetaContactGroup</code> that should be the new parent of <code>contact</code>.
     *
     * @throws MetaContactListException with an appropriate code if the operation fails for some reason.
     * @throws IllegalArgumentException if <code>newMetaGroup</code> or <code>metaContact</code> do not come from this implementation.
     */
    public void moveMetaContact(MetaContact metaContact, MetaContactGroup newMetaGroup)
            throws MetaContactListException, IllegalArgumentException {
        if (!(newMetaGroup instanceof MetaContactGroupImpl)) {
            throw new IllegalArgumentException(newMetaGroup + " is not a MetaContactGroupImpl instance");
        }

        if (!(metaContact instanceof MetaContactImpl)) {
            throw new IllegalArgumentException(metaContact + " is not a MetaContactImpl instance");
        }

        MetaContactGroupImpl mcGroupImpl = (MetaContactGroupImpl) newMetaGroup;
        MetaContactImpl metaContactImpl = (MetaContactImpl) metaContact;

        // first remove the meta contact from its current parent, then add to new metaGroup
        MetaContactGroupImpl currentParent = (MetaContactGroupImpl) findParentMetaContactGroup(metaContact);
        if (currentParent != null)
            currentParent.removeMetaContact(metaContactImpl);

        mcGroupImpl.addMetaContact(metaContactImpl);

        try {
            // first make sure the new meta contact group path is resolved against all
            // protocols that the MetaContact requires.
            // Then move the meta contact in there and move all proto contacts inside it.
            Iterator<Contact> contacts = metaContact.getContacts();

            while (contacts.hasNext()) {
                Contact protoContact = contacts.next();
                ContactGroup protoGroup = resolveProtoPath(protoContact.getProtocolProvider(), mcGroupImpl);

                // get a persistent or non persistent presence operation set
                OperationSetPersistentPresence opSetPresence
                        = protoContact.getProtocolProvider().getOperationSet(OperationSetPersistentPresence.class);
                if (opSetPresence == null) {
                    /* @todo handle non persistent presence operation sets */
                }
                else {
                    if (newMetaGroup.equals(getRoot()))
                        opSetPresence.moveContactToGroup(protoContact, opSetPresence.getServerStoredContactListRoot());
                    else
                        opSetPresence.moveContactToGroup(protoContact, protoGroup);
                }
            }
        } catch (Exception ex) {
            Timber.e(ex, "Cannot move contact");

            // now move the contact to previous parent
            mcGroupImpl.removeMetaContact(metaContactImpl);
            currentParent.addMetaContact(metaContactImpl);
            throw new MetaContactListException(ex.getMessage(), MetaContactListException.CODE_MOVE_CONTACT_ERROR);
        }
        // fire the move event.
        fireMetaContactEvent(new MetaContactMovedEvent(metaContact, currentParent, newMetaGroup));
    }

    /**
     * Deletes the specified contact from both the local contact list and (if applicable) the
     * server stored contact list if supported by the corresponding protocol.
     *
     * @param contact the contact to remove.
     *
     * @throws MetaContactListException with an appropriate code if the operation fails for some reason.
     */
    public void removeContact(Contact contact)
            throws MetaContactListException {
        // remove the contact from the provider and do nothing else; updating and/or removing the
        // corresponding meta contact would happen once a confirmation event is received from the
        // underlying protocol provider
        OperationSetPresence opSetPresence = contact.getProtocolProvider().getOperationSet(OperationSetPresence.class);

        // in case the provider only has a persistent operation set:
        if (opSetPresence == null) {
            opSetPresence = contact.getProtocolProvider().getOperationSet(OperationSetPersistentPresence.class);
            if (opSetPresence == null) {
                throw new IllegalStateException(
                        "Cannot remove a contact from a provider with no presence operation set.");
            }
        }

        try {
            opSetPresence.unsubscribe(contact);
        } catch (Exception ex) {
            String errorTxt = "Failed to remove " + contact + " from its protocol provider. ";
            if ((ex instanceof OperationFailedException) || (ex instanceof IllegalStateException))
                errorTxt += ex.getMessage();
            throw new MetaContactListException(errorTxt, ex, MetaContactListException.CODE_NETWORK_ERROR);
        }
    }

    /**
     * Removes a listener previously added with <code>addContactListListener</code>.
     *
     * @param listener the listener to remove
     */
    public void removeMetaContactListListener(MetaContactListListener listener) {
        synchronized (mMetaContactListListeners) {
            mMetaContactListListeners.remove(listener);
        }
    }

    /**
     * Removes the specified <code>metaContact</code> as well as all of its underlying contacts.
     * Do not fire events. that will be done by the contact listener as soon as it gets
     * confirmation events of proto contact removal the removal of the last contact would
     * also generate an even for the removal of the meta contact itself.
     *
     * @param metaContact the metaContact to remove.
     *
     * @throws MetaContactListException with an appropriate code if the operation fails for some reason.
     */
    public void removeMetaContact(MetaContact metaContact)
            throws MetaContactListException {
        Iterator<Contact> protoContactsIter = metaContact.getContacts();
        while (protoContactsIter.hasNext()) {
            removeContact(protoContactsIter.next());
        }
    }

    /**
     * Removes the specified meta contact group, all its corresponding protocol specific groups
     * and all their children.
     *
     * @param groupToRemove the <code>MetaContactGroup</code> to have removed.
     *
     * @throws MetaContactListException with an appropriate code if the operation fails for some reason.
     */
    public void removeMetaContactGroup(MetaContactGroup groupToRemove)
            throws MetaContactListException {
        if (!(groupToRemove instanceof MetaContactGroupImpl)) {
            throw new IllegalArgumentException(groupToRemove + " is not an instance of MetaContactGroupImpl");
        }

        // First remove all its protoGroups; and then remove the metaGroup itself.
        try {
            Iterator<ContactGroup> protoGroups = groupToRemove.getContactGroups();
            while (protoGroups.hasNext()) {
                ContactGroup protoGroup = protoGroups.next();

                OperationSetPersistentPresence opSetPersPresence = protoGroup.getProtocolProvider()
                        .getOperationSet(OperationSetPersistentPresence.class);
                if (opSetPersPresence == null) {
                    /* @todo handle removal of non persistent proto groups */
                    return;
                }
                opSetPersPresence.removeServerStoredContactGroup(protoGroup);
            }
        } catch (Exception ex) {
            throw new MetaContactListException(ex.getMessage(),
                    MetaContactListException.CODE_REMOVE_GROUP_ERROR);
        }

        MetaContactGroupImpl parentMetaGroup = (MetaContactGroupImpl) findParentMetaContactGroup(groupToRemove);
        parentMetaGroup.removeSubgroup(groupToRemove);
        fireMetaContactGroupEvent(groupToRemove, null, null, MetaContactGroupEvent.META_CONTACT_GROUP_REMOVED);
    }

    /**
     * Removes the protocol specific group from the specified meta contact group and removes from
     * meta contacts all proto contacts that belong to the same provider as the group which is
     * being removed.
     *
     * @param metaContainer the MetaContactGroup that we'd like to remove a contact group from.
     * @param groupToRemove the ContactGroup that we'd like removed.
     * @param sourceProvider the ProtocolProvider that the contact group belongs to.
     */
    public void removeContactGroupFromMetaContactGroup(MetaContactGroupImpl metaContainer,
            ContactGroup groupToRemove, ProtocolProviderService sourceProvider) {
        // if we failed to find the metaGroup corresponding to proto group
        if (metaContainer == null) {
            Timber.w("No meta container found, when trying to remove group: %s", groupToRemove);
            return;
        }

        /*
         * Go through all meta contacts and remove all contacts that belong to the same provider
         * and are therefore children of the group that is being removed.
         */
        locallyRemoveAllContactsForProvider(metaContainer, groupToRemove);
        fireMetaContactGroupEvent(metaContainer, sourceProvider, groupToRemove,
                MetaContactGroupEvent.CONTACT_GROUP_REMOVED_FROM_META_GROUP);
    }

    /**
     * Removes local resources storing copies of the meta contact list. This method is meant
     * primarily to aid automated testing which may depend on beginning the tests with an empty
     * local contact list.
     */
    public void purgeLocallyStoredContactListCopy() {
        Timber.log(TimberLog.FINER, "Removed meta contact list storage file.");
    }

    /**
     * Goes through the specified group and removes from all meta contacts, protocol specific
     * contacts belonging to the specified <code>groupToRemove</code>. Note that this method won't
     * undertake any calls to the protocol itself as it is used only to update the local contact
     * list as a result of a server generated event.
     *
     * @param parentMetaGroup the MetaContactGroup whose children we should go through
     * @param groupToRemove the proto group that we want removed together with its children.
     */
    // cmeng - SQLite will remove all decedent of the groupToRemove base on accountUuid etc
    // need to fireEvent for all listeners.
    private void locallyRemoveAllContactsForProvider(MetaContactGroupImpl parentMetaGroup, ContactGroup groupToRemove) {
        Iterator<MetaContact> childrenContacts = parentMetaGroup.getChildContacts();
        // first go through all direct children.
        while (childrenContacts.hasNext()) {
            MetaContactImpl child = (MetaContactImpl) childrenContacts.next();

            // Get references to all contacts that will be removed in case we need to fire an
            // event.
            Iterator<Contact> contactsToRemove = child.getContactsForContactGroup(groupToRemove);
            child.removeContactsForGroup(groupToRemove);

            // if this was the last proto contact inside this meta contact, then remove the meta
            // contact as well. Otherwise only fire an event.
            if (child.getContactCount() == 0) {
                parentMetaGroup.removeMetaContact(child);
                fireMetaContactEvent(child, parentMetaGroup, MetaContactEvent.META_CONTACT_REMOVED);
            }
            else {
                // there are other proto contacts left in the contact child meta contact so we'll
                // have to send an event for each of the removed contacts and not only a single
                // event for the whole meta contact.
                while (contactsToRemove.hasNext()) {
                    fireProtoContactEvent(contactsToRemove.next(),
                            ProtoContactEvent.PROTO_CONTACT_REMOVED, child, null);
                }
            }
        }

        // then go through all subgroups.
        Iterator<MetaContactGroup> subgroups = parentMetaGroup.getSubgroups();
        while (subgroups.hasNext()) {
            MetaContactGroupImpl subMetaGroup = (MetaContactGroupImpl) subgroups.next();
            Iterator<ContactGroup> contactGroups = subMetaGroup.getContactGroups();

            ContactGroup protoGroup = null;
            while (contactGroups.hasNext()) {
                protoGroup = contactGroups.next();
                if (groupToRemove == protoGroup.getParentContactGroup())
                    locallyRemoveAllContactsForProvider(subMetaGroup, protoGroup);
            }

            // remove the group if there are no children left.
            if ((subMetaGroup.countSubgroups() == 0)
                    && (subMetaGroup.countChildContacts() == 0)
                    && (subMetaGroup.countContactGroups() == 0)) {
                parentMetaGroup.removeSubgroup(subMetaGroup);
                fireMetaContactGroupEvent(subMetaGroup, groupToRemove.getProtocolProvider(),
                        protoGroup, MetaContactGroupEvent.META_CONTACT_GROUP_REMOVED);
            }
        }
        parentMetaGroup.removeProtoGroup(groupToRemove);
    }

    /**
     * Returns the MetaContactGroup corresponding to the specified contactGroup or null if no such
     * MetaContactGroup was found.
     *
     * @param contactGroup the protocol specific <code>contactGroup</code> that we're looking for.
     *
     * @return the MetaContactGroup corresponding to the specified contactGroup or null if no such
     * MetaContactGroup was found.
     */
    public MetaContactGroup findMetaContactGroupByContactGroup(ContactGroup contactGroup) {
        return rootMetaGroup.findMetaContactGroupByContactGroup(contactGroup);
    }

    /**
     * Returns the MetaContact containing the specified contact or null if no such MetaContact was
     * found. The method can be used when for example we need to find the MetaContact that is the
     * author of an incoming message and the corresponding ProtocolProviderService has only
     * provided a <code>Contact</code> as its author.
     *
     * @param contact the protocol specific <code>contact</code> that we're looking for.
     *
     * @return the MetaContact containing the specified contact or null if no such contact is
     * present in this contact list.
     */
    public MetaContact findMetaContactByContact(Contact contact) {
        return rootMetaGroup.findMetaContactByContact(contact);
    }

    /**
     * Returns the MetaContact containing a contact with an address equal to
     * <code>contactAddress</code> and with a source provider matching <code>accountID</code>, or null if
     * no such MetaContact was found. The method can be used when for example we need to find the
     * MetaContact that is the author of an incoming message and the corresponding
     * ProtocolProviderService has only provided a <code>Contact</code> as its author.
     *
     * @param contactAddress the address of the protocol specific <code>contact</code> that we're looking for.
     * @param accountID the ID of the account that the contact we're looking for must belong to.
     *
     * @return the MetaContact containing the specified contact or null if no such contact is
     * present in this contact list.
     */
    public MetaContact findMetaContactByContact(String contactAddress, String accountID) {
        return rootMetaGroup.findMetaContactByContact(contactAddress, accountID);
    }

    /**
     * Returns the MetaContact that corresponds to the specified metaContactID.
     *
     * @param metaContactID a String identifier of a meta contact.
     *
     * @return the MetaContact with the specified string identifier or null if no such meta
     * contact was found.
     */
    public MetaContact findMetaContactByMetaUID(String metaContactID) {
        return rootMetaGroup.findMetaContactByMetaUID(metaContactID);
    }

    /**
     * Returns the MetaContactGroup that corresponds to the specified metaGroupID.
     *
     * @param metaGroupID a String identifier of a meta contact group.
     *
     * @return the MetaContactGroup with the specified string identifier or null if no such meta
     * contact was found.
     */
    public MetaContactGroup findMetaContactGroupByMetaUID(String metaGroupID) {
        return rootMetaGroup.findMetaContactGroupByMetaUID(metaGroupID);
    }

    /**
     * Returns a list of all <code>MetaContact</code>s containing a protocol contact from the given
     * <code>ProtocolProviderService</code>.
     *
     * @param protocolProvider the <code>ProtocolProviderService</code> whose contacts we're looking for.
     *
     * @return a list of all <code>MetaContact</code>s containing a protocol contact from the given
     * <code>ProtocolProviderService</code>.
     */
    public Iterator<MetaContact> findAllMetaContactsForProvider(ProtocolProviderService protocolProvider) {
        List<MetaContact> resultList = new ArrayList<>();
        findAllMetaContactsForProvider(protocolProvider, rootMetaGroup, resultList);
        return resultList.iterator();
    }

    /**
     * Returns a list of all <code>MetaContact</code>s contained in the given group and containing a
     * protocol contact from the given <code>ProtocolProviderService</code>.
     *
     * @param protocolProvider the <code>ProtocolProviderService</code> whose contacts we're looking for.
     * @param metaContactGroup the parent group.
     *
     * @return a list of all <code>MetaContact</code>s containing a protocol contact from the given
     * <code>ProtocolProviderService</code>.
     */
    public Iterator<MetaContact> findAllMetaContactsForProvider(
            ProtocolProviderService protocolProvider, MetaContactGroup metaContactGroup) {
        List<MetaContact> resultList = new LinkedList<>();
        findAllMetaContactsForProvider(protocolProvider, metaContactGroup, resultList);
        return resultList.iterator();
    }

    /**
     * Returns a list of all <code>MetaContact</code>s containing a protocol contact corresponding to
     * the given <code>contactAddress</code> string.
     *
     * @param contactAddress the contact address for which we're looking for a parent <code>MetaContact</code>.
     *
     * @return a list of all <code>MetaContact</code>s containing a protocol contact corresponding to
     * the given <code>contactAddress</code> string.
     */
    public Iterator<MetaContact> findAllMetaContactsForAddress(String contactAddress) {
        List<MetaContact> resultList = new LinkedList<>();
        findAllMetaContactsForAddress(rootMetaGroup, contactAddress, resultList);
        return resultList.iterator();
    }

    /**
     * Returns a list of all <code>MetaContact</code>s containing a protocol contact corresponding to
     * the given <code>contactAddress</code> string.
     *
     * @param contactAddress the contact address for which we're looking for a parent <code>MetaContact</code>.
     * @param metaContactGroup the parent group.
     * @param resultList the list containing the result of the search.
     */
    private void findAllMetaContactsForAddress(MetaContactGroup metaContactGroup,
            String contactAddress, List<MetaContact> resultList) {
        Iterator<MetaContact> childContacts = metaContactGroup.getChildContacts();
        while (childContacts.hasNext()) {
            MetaContact metaContact = childContacts.next();

            Iterator<Contact> protocolContacts = metaContact.getContacts();

            while (protocolContacts.hasNext()) {
                Contact protocolContact = protocolContacts.next();

                if (protocolContact.getAddress().equals(contactAddress)
                        || protocolContact.getDisplayName().equals(contactAddress))
                    resultList.add(metaContact);
            }
        }

        Iterator<MetaContactGroup> subGroups = metaContactGroup.getSubgroups();
        while (subGroups.hasNext()) {
            MetaContactGroup subGroup = subGroups.next();

            Iterator<ContactGroup> protocolSubgroups = subGroup.getContactGroups();
            if (protocolSubgroups.hasNext()) {
                findAllMetaContactsForAddress(subGroup, contactAddress, resultList);
            }
        }
    }

    /**
     * Returns a list of all <code>MetaContact</code>s contained in the given group and containing a
     * protocol contact from the given <code>ProtocolProviderService</code>.
     *
     * @param protocolProvider the <code>ProtocolProviderService</code> whose contacts we're looking for.
     * @param metaContactGroup the parent group.
     * @param resultList the list containing the result of the search.
     */
    private void findAllMetaContactsForProvider(ProtocolProviderService protocolProvider,
            MetaContactGroup metaContactGroup, List<MetaContact> resultList) {
        Iterator<MetaContact> childContacts = metaContactGroup.getChildContacts();

        while (childContacts.hasNext()) {
            MetaContact metaContact = childContacts.next();
            Iterator<Contact> protocolContacts = metaContact.getContactsForProvider(protocolProvider);

            if (protocolContacts.hasNext()) {
                resultList.add(metaContact);
            }
        }

        Iterator<MetaContactGroup> subGroups = metaContactGroup.getSubgroups();

        while (subGroups.hasNext()) {
            MetaContactGroup subGroup = subGroups.next();

            Iterator<ContactGroup> protocolSubgroups = subGroup.getContactGroupsForProvider(protocolProvider);
            if (protocolSubgroups.hasNext()) {
                findAllMetaContactsForProvider(protocolProvider, subGroup, resultList);
            }
        }
    }

    /**
     * Goes through the server stored ContactList of the specified operation set, retrieves all
     * protocol specific contacts it contains and makes sure they are all present in the local
     * contact list.
     *
     * @param presenceOpSet the presence operation set whose contact list we'd like to synchronize with the local
     * contact list.
     */
    private void synchronizeOpSetWithLocalContactList(OperationSetPersistentPresence presenceOpSet) {
        ContactGroup rootProtoGroup = presenceOpSet.getServerStoredContactListRoot();

        if (rootProtoGroup != null) {
            Timber.log(TimberLog.FINER, "subgroups: %s; child contacts: %s", rootProtoGroup.countSubgroups(), rootProtoGroup.countContacts());
            addContactGroupToMetaGroup(rootProtoGroup, rootMetaGroup, true);
        }
        presenceOpSet.addSubscriptionListener(clSubscriptionEventHandler);
        presenceOpSet.addServerStoredGroupChangeListener(clGroupEventHandler);
    }

    /**
     * Creates meta contacts and meta contact groups for all children of the specified
     * <code>contactGroup</code> and adds them to <code>metaGroup</code>
     *
     * @param protoGroup the <code>ContactGroup</code> to add.
     * <p>
     * @param metaGroup the <code>MetaContactGroup</code> where <code>ContactGroup</code> should be added.
     * @param fireEvents indicates whether or not events are to be fired upon adding subContacts and subgroups.
     * When this method is called recursively, the parameter should will be false in order
     * to generate a minimal number of events for the whole addition and not an event per
     * every subgroup and child contact.
     */
    private void addContactGroupToMetaGroup(ContactGroup protoGroup, MetaContactGroupImpl
            metaGroup, boolean fireEvents) {
        // first register the root group
        metaGroup.addProtoGroup(protoGroup);

        // register subgroups and contacts
        Iterator<ContactGroup> subgroupsIter = protoGroup.subgroups();

        while (subgroupsIter.hasNext()) {
            ContactGroup group = subgroupsIter.next();

            // continue if we have already loaded this group from the locally stored contact list.
            if (metaGroup.findMetaContactGroupByContactGroup(group) != null)
                continue;

            // right now we simply map this group to an existing one without being cautious and
            // verify whether we already have it registered
            MetaContactGroupImpl newMetaGroup = new MetaContactGroupImpl(this, group.getGroupName());
            metaGroup.addSubgroup(newMetaGroup);
            addContactGroupToMetaGroup(group, newMetaGroup, false);

            if (fireEvents) {
                fireMetaContactGroupEvent(newMetaGroup, group.getProtocolProvider(), group,
                        MetaContactGroupEvent.META_CONTACT_GROUP_ADDED);
            }
        }

        // now add all contacts, located in this group
        Iterator<Contact> contactsIter = protoGroup.contacts();
        while (contactsIter.hasNext()) {
            Contact contact = contactsIter.next();

            // continue if we have already loaded this contact from the locally stored contact
            // list.
            if (metaGroup.findMetaContactByContact(contact) != null)
                continue;

            MetaContactImpl newMetaContact = new MetaContactImpl();
            newMetaContact.addProtoContact(contact);
            metaGroup.addMetaContact(newMetaContact);
            if (fireEvents) {
                fireMetaContactEvent(newMetaContact, metaGroup, MetaContactEvent.META_CONTACT_ADDED);
            }
        }
    }

    /**
     * Adds the specified provider to the list of currently known providers. In case the provider
     * supports persistent presence, the method would also extract all contacts and synchronize
     * them with the local contact list. Otherwise it would start a process where local
     * contacts would be added on the server.
     *
     * @param provider the ProtocolProviderService that we've just detected.
     */
    private synchronized void handleProviderAdded(ProtocolProviderService provider) {
        String accountUid = provider.getAccountID().getAccountUid();
        String accountUuid = provider.getAccountID().getAccountUuid();

        Timber.d("Adding protocol provider %s", accountUid);

        mCurrentlyInstalledProviders.put(accountUid, provider);

        // If we have a persistent presence opSet for the provider - then retrieve its contact list
        // and merge it with the local one.
        OperationSetPersistentPresence opSetPersPresence
                = provider.getOperationSet(OperationSetPersistentPresence.class);
        if (opSetPersPresence != null) {
            // load contacts, stored in the local contact list and corresponding to this provider.
            storageManager.extractContactsForAccount(accountUuid, accountUid);
            Timber.d("All contacts loaded for account %s", accountUid);
            synchronizeOpSetWithLocalContactList(opSetPersPresence);
        }
        else {
            Timber.d("Service did not have a opSetPersPresence");
        }

        /* @todo implement handling non persistent presence operation sets */

        // add a presence status listener so that we could reorder contacts upon status change.
        // NOTE that we MUST NOT add the presence listener before extracting the locally stored
        // contact list or otherwise we'll get events for all contacts that we have already extracted
        if (opSetPersPresence != null)
            opSetPersPresence.addContactPresenceStatusListener(this);

        // Check if the capabilities operation set is available for this contact and add a
        // listener to it in order to track capabilities' changes for all contained protocol
        // contacts.
        OperationSetContactCapabilities capOpSet = provider.getOperationSet(OperationSetContactCapabilities.class);
        if (capOpSet != null) {
            capOpSet.addContactCapabilitiesListener(this);
        }
    }

    /**
     * Removes the specified provider from the list of currently known providers and ignores all
     * the contacts that it has registered locally.
     *
     * @param pps the ProtocolProviderService that has been unregistered.
     */
    private void handleProviderRemoved(ProtocolProviderService pps) {
        Timber.d("Removing protocol provider %s", pps);

        AccountID accountID = pps.getAccountID();
        mCurrentlyInstalledProviders.remove(accountID.getAccountUid());

        // Check if the capabilities operation set is available for this contact and remove
        // previously added listeners.
        OperationSetContactCapabilities capOpSet = pps.getOperationSet(OperationSetContactCapabilities.class);
        if (capOpSet != null)
            capOpSet.removeContactCapabilitiesListener(this);

        // get the root group for the provider so that we could remove it.
        OperationSetPersistentPresence persPresOpSet = pps.getOperationSet(OperationSetPersistentPresence.class);

        // ignore if persistent presence is not supported.
        if (persPresOpSet != null) {
            // we don't care about subscription and presence status events here any longer.
            persPresOpSet.removeContactPresenceStatusListener(this);
            persPresOpSet.removeSubscriptionListener(clSubscriptionEventHandler);
            persPresOpSet.removeServerStoredGroupChangeListener(clGroupEventHandler);

            ContactGroup rootGroup = persPresOpSet.getServerStoredContactListRoot();
            // iterate all sub groups and remove them one by one (we don't simply remove the root
            // group because the mcl storage manager is stupid (i wrote it) and doesn't know root
            // groups exist. that's why it needs to hear an event for every single group.)
            Iterator<ContactGroup> subgroups = rootGroup.subgroups();

            while (subgroups.hasNext()) {
                ContactGroup group = subgroups.next();
                // remove the group
                removeContactGroupFromMetaContactGroup((MetaContactGroupImpl)
                        findMetaContactGroupByContactGroup(group), group, pps);
            }
            // cmeng - not allow to remove the root group
            // removeContactGroupFromMetaContactGroup(rootMetaGroup, rootGroup, pps);
        }

    }

    /**
     * Registers <code>group</code> to the event ignore list. This would make the method that is
     * normally handling events for newly created groups ignore any events for that particular
     * group and leave the responsibility to the method that added the group to the ignore list.
     *
     * @param group the name of the group that we'd like to register.
     * @param ownerProvider the protocol provider that we expect the addition to come from.
     */
    private void addGroupToEventIgnoreList(String group, ProtocolProviderService ownerProvider) {
        // first check whether registrations in the ignore list already exist for this group.
        if (isGroupInEventIgnoreList(group, ownerProvider)) {
            return;
        }

        List<ProtocolProviderService> existingProvList = mGroupEventIgnoreList.get(group);
        if (existingProvList == null) {
            existingProvList = new LinkedList<>();
        }

        existingProvList.add(ownerProvider);
        mGroupEventIgnoreList.put(group, existingProvList);
    }

    /**
     * Verifies whether the specified group is in the group event ignore list.
     *
     * @param group the group whose presence in the ignore list we'd like to verify.
     * @param ownerProvider the provider that <code>group</code> belongs to.
     *
     * @return true if the group is in the group event ignore list and false otherwise.
     */
    private boolean isGroupInEventIgnoreList(String group, ProtocolProviderService ownerProvider) {
        List<ProtocolProviderService> existingProvList = mGroupEventIgnoreList.get(group);
        return existingProvList != null && existingProvList.contains(ownerProvider);
    }

    /**
     * Removes the <code>group</code> from the group event ignore list so that events concerning this
     * group get treated.
     *
     * @param group the group whose that we'd want out of the ignore list.
     * @param ownerProvider the provider that <code>group</code> belongs to.
     */
    private void removeGroupFromEventIgnoreList(String group, ProtocolProviderService
            ownerProvider) {
        // first check whether the registration actually exists.
        if (!isGroupInEventIgnoreList(group, ownerProvider)) {
            return;
        }

        List<ProtocolProviderService> existingProvList = mGroupEventIgnoreList.get(group);
        if (existingProvList.isEmpty()) {
            mGroupEventIgnoreList.remove(group);
        }
        else {
            existingProvList.remove(ownerProvider);
        }
    }

    /**
     * Registers <code>contact</code> to the event ignore list. This would make the method that is
     * normally handling events for newly created contacts ignore any events for that particular
     * contact and leave the responsibility to the method that added the contact to the ignore
     * list.
     *
     * @param contact the address of the contact that we'd like to ignore.
     * @param ownerProvider the protocol provider that we expect the addition to come from.
     */
    private void addContactToEventIgnoreList(String contact, ProtocolProviderService ownerProvider) {
        // first check whether registration is in the ignored list already exist for this contact.
        if (TextUtils.isEmpty(contact) || isContactInEventIgnoreList(contact, ownerProvider)) {
            return;
        }

        List<ProtocolProviderService> existingProvList = mContactEventIgnoreList.get(contact);
        if (existingProvList == null) {
            existingProvList = new LinkedList<>();
        }
        existingProvList.add(ownerProvider);
        mContactEventIgnoreList.put(contact, existingProvList);
    }

    /**
     * Verifies whether the specified contact is in the contact event ignore list.
     *
     * @param contact the contact whose presence in the ignore list we'd like to verify.
     * @param ownerProvider the provider that <code>contact</code> belongs to.
     *
     * @return true if the contact is in the contact event ignore list and false otherwise.
     */
    private boolean isContactInEventIgnoreList(@NonNull String contact, ProtocolProviderService ownerProvider) {
        List<ProtocolProviderService> existingProvList = mContactEventIgnoreList.get(contact);
        return (existingProvList != null) && existingProvList.contains(ownerProvider);
    }

    /**
     * Verifies whether the specified contact is in the contact event ignore list. The reason we
     * need this method in addition to the one that takes a string contact address is necessary
     * for the following reason: In some cases the ID that we create a contact with
     * (e.g. mybuddy) could be different from the one returned by its getAddress() method
     * (e.g. mybuddy@hisnet.com). If this is the case we hope that the difference would be
     * handled gracefully in the equals method of the contact so we also compare with it.
     *
     * @param contact the contact whose presence in the ignore list we'd like to verify.
     * @param ownerProvider the provider that <code>contact</code> belongs to.
     *
     * @return true if the contact is in the contact event ignore list and false otherwise.
     */
    private boolean isContactInEventIgnoreList(Contact contact, ProtocolProviderService ownerProvider) {
        for (Map.Entry<String, List<ProtocolProviderService>> contactEventIgnoreEntry :
                mContactEventIgnoreList.entrySet()) {
            String contactAddress = contactEventIgnoreEntry.getKey();

            if (contact.getAddress().equals(contactAddress) || contact.equals(contactAddress)) {
                List<ProtocolProviderService> existingProvList = contactEventIgnoreEntry.getValue();

                return existingProvList != null && existingProvList.contains(ownerProvider);
            }
        }
        return false;
    }

    /**
     * Removes the <code>contact</code> from the group event ignore list so that events concerning this group get treated.
     *
     * @param contact the contact whose that we'd want out of the ignore list.
     * @param ownerProvider the provider that <code>group</code> belongs to.
     */
    private void removeContactFromEventIgnoreList(String contact, ProtocolProviderService ownerProvider) {
        // first check whether the registration actually exists.
        if (TextUtils.isEmpty(contact) || !isContactInEventIgnoreList(contact, ownerProvider)) {
            return;
        }

        List<ProtocolProviderService> existingProvList = mContactEventIgnoreList.get(contact);
        if (existingProvList != null) {
            if (existingProvList.isEmpty()) {
                mGroupEventIgnoreList.remove(contact);
            }
            else {
                existingProvList.remove(ownerProvider);
            }
        }
    }

    /**
     * Implements the <code>ServiceListener</code> method. Verifies whether the passed event concerns
     * a <code>ProtocolProviderService</code> and modifies the list of registered protocol providers accordingly.
     *
     * @param event The <code>ServiceEvent</code> object.
     */
    public void serviceChanged(ServiceEvent event) {
        Object sService = mBundleContext.getService(event.getServiceReference());
        Timber.log(TimberLog.FINER, "Received a service event for: %s", sService.getClass().getName());

        // we don't care if the source service is not a protocol provider
        if (!(sService instanceof ProtocolProviderService)) {
            return;
        }
        Timber.d("Service is a protocol provider.");

        ProtocolProviderService pps = (ProtocolProviderService) sService;
        AccountID accountID = pps.getAccountID();

        // first check if the event really means that the accounts is uninstalled/installed (or
        // is it just stopped ... e.g. we could be shutting down, or in the other case it could
        // be just modified) ... before that however, we'd need to get a reference to the service.
        ProtocolProviderFactory sourceFactory = null;
        ServiceReference<?>[] allBundleServices = event.getServiceReference().getBundle().getRegisteredServices();
        for (ServiceReference<?> bundleServiceRef : allBundleServices) {
            Object service = mBundleContext.getService(bundleServiceRef);
            if (service instanceof ProtocolProviderFactory) {
                sourceFactory = (ProtocolProviderFactory) service;
                break;
            }
        }

        if (event.getType() == ServiceEvent.REGISTERED) {
            Timber.d("Handling registration of a new Protocol Provider.");
            // if we have the PROVIDER_MASK property set, make sure that this provider has it and if not ignore it.
            String providerMask = System.getProperty(MetaContactListService.PROVIDER_MASK_PROPERTY);
            if (providerMask != null && !providerMask.trim().isEmpty()) {
                String servRefMask = (String) event.getServiceReference().getProperty(
                        MetaContactListService.PROVIDER_MASK_PROPERTY);

                if ((servRefMask == null) || !servRefMask.equals(providerMask)) {
                    Timber.d("Ignoring masked provider: %s", accountID);
                    return;
                }
            }

            if (sourceFactory != null && mCurrentlyInstalledProviders.containsKey(
                    pps.getAccountID().getAccountUid())) {
                Timber.d("Modifying an existing installed account: %s", accountID);
                // the account is already installed and this event is coming from a modification.
                // we don't return here as the account is removed and added again and we must
                // create its unresolved contact and give him a chance to resolve them and not
                // fire new subscription to duplicate the already existing.

                // return;
            }

            handleProviderAdded(pps);
        }
        else if (event.getType() == ServiceEvent.UNREGISTERING) {
            if (sourceFactory == null) {
                // strange ... we must be shutting down. just bail
                return;
            }

            // If the account is still registered or is just being unloaded but remains stored,
            // we remove its contacts but without storing this
            if (ContactlistActivator.getAccountManager().getStoredAccounts().contains(accountID)) {
                // the account is still installed it means we are modifying it. we remove all its
                // contacts from current contactList but remove the storage manager in order to
                // avoid losing those contacts from the storage as its modification later
                // unresolved contacts will be created which will be resolved from the already
                // modified account
                synchronized (this) {
                    removeMetaContactListListener(storageManager);
                    handleProviderRemoved(pps);
                    addMetaContactListListener(storageManager);
                }
                return;
            }
            Timber.w("Account uninstalled. Removing all its meta contacts: %s", accountID);
            handleProviderRemoved(pps);
        }
    }

    /**
     * The class would listen for events delivered to <code>SubscriptionListener</code>s.
     */
    private class ContactListSubscriptionListener implements SubscriptionListener {
        /**
         * Creates a meta contact for the source contact indicated by the specified
         * SubscriptionEvent, or updates an existing one if there is one. The method would also
         * generate the corresponding <code>MetaContactEvent</code>.
         *
         * @param evt the SubscriptionEvent that we'll be handling.
         */
        public void subscriptionCreated(SubscriptionEvent evt) {
            Timber.log(TimberLog.FINER, "Subscription created: %s", evt);

            // ignore the event if the source contact is in the ignore list
            if (isContactInEventIgnoreList(evt.getSourceContact(), evt.getSourceProvider())) {
                return;
            }

            MetaContactGroupImpl parentGroup = (MetaContactGroupImpl) findMetaContactGroupByContactGroup(evt.getParentGroup());
            if (parentGroup == null) {
                Timber.e("Received a subscription for a group that we hadn't seen before!");
                return;
            }

            MetaContactImpl newMetaContact = new MetaContactImpl();
            newMetaContact.addProtoContact(evt.getSourceContact());
            newMetaContact.setDisplayName(evt.getSourceContact().getDisplayName());
            parentGroup.addMetaContact(newMetaContact);

            // fire the meta contact event.
            fireMetaContactEvent(newMetaContact, parentGroup, MetaContactEvent.META_CONTACT_ADDED);

            // make sure we have a local copy of the avatar;
            newMetaContact.getAvatar();
        }

        /**
         * Indicates that a contact/subscription has been moved from one server stored group to
         * another. The way we handle the event depends on whether the source
         * contact/subscription is the only proto contact found in its current MetaContact
         * encapsulator or not.
         * <p>
         * If this is the case (the source contact has no siblings in its current meta contact
         * list encapsulator) then we will move the whole meta contact to the meta contact group
         * corresponding to the new parent ContactGroup of the source contact. In this case we
         * would only fire a MetaContactMovedEvent containing the old and new parents of the
         * MetaContact in question.
         * <p>
         * If, however, the MetaContact that currently encapsulates the source contact also
         * encapsulates other proto contacts, then we will create a new MetaContact instance,
         * place it in the MetaContactGroup corresponding to the new parent ContactGroup of the
         * source contact and add the source contact inside it. In this case we would first fire a
         * metaContact added event over the empty meta contact and then, once the proto contact
         * has been moved inside it, we would also fire a ProtoContactEvent with event id
         * PROTO_CONTACT_MOVED.
         * <p>
         *
         * @param evt a reference to the SubscriptionMovedEvent containing previous and new parents as
         * well as a ref to the source contact.
         */
        public void subscriptionMoved(SubscriptionMovedEvent evt) {
            Timber.d("Subscription moved: %s", evt);

            Contact sourceContact = evt.getSourceContact();

            // ignore the event if the source contact is in the ignore list
            if (isContactInEventIgnoreList(sourceContact, evt.getSourceProvider())) {
                return;
            }

            MetaContactGroupImpl oldParentGroup = (MetaContactGroupImpl)
                    findMetaContactGroupByContactGroup(evt.getOldParentGroup());
            MetaContactGroupImpl newParentGroup = (MetaContactGroupImpl)
                    findMetaContactGroupByContactGroup(evt.getNewParentGroup());

            if (newParentGroup == null || oldParentGroup == null) {
                Timber.e("Received a subscription for a group that we hadn't seen before!");
                return;
            }

            MetaContactImpl currentMetaContact = (MetaContactImpl) findMetaContactByContact(sourceContact);
            if (currentMetaContact == null) {
                Timber.w(new NullPointerException(), "Received a move event for a contact that is not in our contact list.");
                return;
            }

            // if the move was caused by us (when merging contacts) then chances are that the
            // contact is already in the right group
            MetaContactGroup currentParentGroup = currentMetaContact.getParentMetaContactGroup();
            if (currentParentGroup == newParentGroup) {
                return;
            }

            // if the meta contact does not have other children apart from the contact that we're
            // currently moving then move the whole meta contact to the new parent group.
            if (currentMetaContact.getContactCount() == 1) {
                oldParentGroup.removeMetaContact(currentMetaContact);
                newParentGroup.addMetaContact(currentMetaContact);
                fireMetaContactEvent(new MetaContactMovedEvent(currentMetaContact, oldParentGroup, newParentGroup));
            }
            // if the source contact is not the only contact encapsulated by the
            // currentMetaContact, then create a new meta contact in the new parent group and
            // move the source contact to it.
            else {

                MetaContactImpl newMetaContact = null;

                // first check whether a contact hasn't been already added to a metaContact
                synchronized (sourceContact) {
                    // move the proto contact and fire the corresponding event
                    currentMetaContact.removeProtoContact(sourceContact);

                    MetaContact checkContact = findMetaContactByContact(sourceContact);
                    if (checkContact == null) {
                        newMetaContact = new MetaContactImpl();
                        newMetaContact.setDisplayName(sourceContact.getDisplayName());

                        newParentGroup.addMetaContact(newMetaContact);
                        newMetaContact.addProtoContact(sourceContact);
                    }
                }
                // new contact was created
                if (newMetaContact != null) {
                    // fire an event notifying that a new meta contact was added.
                    fireMetaContactEvent(newMetaContact, newParentGroup, MetaContactEvent.META_CONTACT_ADDED);
                    fireProtoContactEvent(sourceContact, ProtoContactEvent.PROTO_CONTACT_MOVED,
                            currentMetaContact, newMetaContact);
                }
            }
        }

        public void subscriptionFailed(SubscriptionEvent evt) {
            Timber.log(TimberLog.FINER, "Subscription failed: %s", evt);
        }

        /**
         * Events delivered through this method are ignored as they are of no interest to this
         * implementation of the meta contact list service.
         *
         * @param evt the SubscriptionEvent containing the source contact
         */
        public void subscriptionResolved(SubscriptionEvent evt) {
            // this was a contact we already had so all we need to do is update its details
            MetaContactImpl mc = (MetaContactImpl) findMetaContactByContact(evt.getSourceContact());

            if (mc != null) {
                mc.getAvatar();

                if (mc.getContactCount() == 1 && !mc.isDisplayNameUserDefined()) {
                    String oldDisplayName = mc.getDisplayName();

                    // if we have one contact, display name of metaContact haven't been changed by
                    // user and contact display name is different from metaContact's one let's
                    // change it
                    Contact c = mc.getDefaultContact();
                    String newDisplayName = c.getDisplayName();
                    if (newDisplayName != null && !newDisplayName.equals(oldDisplayName)) {
                        mc.setDisplayName(newDisplayName);

                        fireMetaContactEvent(new MetaContactRenamedEvent(mc, oldDisplayName, newDisplayName));

                        // changing the display name has surely brought a change in the order as
                        // well so let's tell the others
                        fireMetaContactGroupEvent(findParentMetaContactGroup(mc), null, null,
                                MetaContactGroupEvent.CHILD_CONTACTS_REORDERED);
                    }
                }
            }
        }

        /**
         * In the case where the event refers to a change in the display name we compare the old
         * value with the display name of the corresponding meta contact. If they are equal this
         * means that the user has not specified their own display name for the meta contact and
         * that the display name was using this contact's display name for its own display name.
         * In this case we change the display name of the meta contact to match the new display
         * name of the proto contact.
         * <p>
         *
         * @param evt the <code>ContactPropertyChangeEvent</code> containing the source contact and the old
         * and new values of the changed property.
         */
        public void contactModified(ContactPropertyChangeEvent evt) {
            MetaContactImpl mc = (MetaContactImpl) findMetaContactByContact(evt.getSourceContact());

            if (ContactPropertyChangeEvent.PROPERTY_DISPLAY_NAME.equals(evt.getPropertyName())) {
                if (evt.getOldValue() != null && evt.getOldValue().equals(mc.getDisplayName())) {
                    renameMetaContact(mc, (String) evt.getNewValue(), false);
                }
                else {
                    // we get here if the name of a contact has changed but the meta contact list
                    // is not going to reflect any change because it is not displaying that name.
                    // in this case we simply make sure everyone (e.g. the storage manager)
                    // knows about the change.
                    fireProtoContactEvent(evt.getSourceContact(), ProtoContactEvent.PROTO_CONTACT_RENAMED, mc, mc);
                }
            }
            else if (ContactPropertyChangeEvent.PROPERTY_IMAGE.equals(evt.getPropertyName())) {
                changeMetaContactAvatar(mc, (String) evt.getOldValue(), (String) evt.getNewValue());
            }
            else if (ContactPropertyChangeEvent.PROPERTY_PERSISTENT_DATA.equals(evt.getPropertyName())
                    || ContactPropertyChangeEvent.PROPERTY_DISPLAY_DETAILS.equals(evt.getPropertyName())) {
                // if persistent data changed fire an event to store it
                fireProtoContactEvent(evt.getSourceContact(),
                        ProtoContactEvent.PROTO_CONTACT_MODIFIED, mc, mc);
            }
        }

        /**
         * Locates the <code>MetaContact</code> corresponding to the contact that has been removed and updates it.
         * If the removed proto contact was the last one in it, then the <code>MetaContact</code> is also removed.
         *
         * @param evt the <code>SubscriptionEvent</code> containing the contact that has been removed.
         */
        public void subscriptionRemoved(SubscriptionEvent evt) {
            Timber.log(TimberLog.FINER, "Subscription removed: %s", evt);

            MetaContactImpl metaContact = (MetaContactImpl) findMetaContactByContact(evt.getSourceContact());
            MetaContactGroupImpl metaContactGroup = (MetaContactGroupImpl)
                    findMetaContactGroupByContactGroup(evt.getParentGroup());
            metaContact.removeProtoContact(evt.getSourceContact());

            // if this was the last protocol contact in this meta contact then remove the meta contact as well.
            if (metaContact.getContactCount() == 0) {
                metaContactGroup.removeMetaContact(metaContact);
                fireMetaContactEvent(metaContact, metaContactGroup, MetaContactEvent.META_CONTACT_REMOVED);
            }
            else {
                // this was not the las proto contact so only generate the corresponding event.
                fireProtoContactEvent(evt.getSourceContact(),
                        ProtoContactEvent.PROTO_CONTACT_REMOVED, metaContact, null);

            }
        }
    }

    /**
     * The class would listen for events delivered to <code>ServerStoredGroupListener</code>s.
     */
    private class ContactListGroupListener implements ServerStoredGroupListener {
        /**
         * The method is called upon receiving notification that a new server stored group has been created.
         *
         * @param parent a reference to the <code>MetaContactGroupImpl</code> where <code>group</code>'s newly
         * created <code>MetaContactGroup</code> wrapper should be added as a subgroup.
         * @param group the newly added <code>ContactGroup</code>
         *
         * @return the <code>MetaContactGroup</code> that now wraps the newly created <code>ContactGroup</code>.
         */
        private MetaContactGroup handleGroupCreatedEvent(MetaContactGroupImpl parent, ContactGroup group) {
            // if parent already contains a meta group with the same name, we'll
            // reuse it as the container for the new contact group.
            MetaContactGroupImpl newMetaGroup = (MetaContactGroupImpl) parent.getMetaContactSubgroup(group.getGroupName());

            // if there was no meta group with the specified name, create a new one
            if (newMetaGroup == null) {
                newMetaGroup = new MetaContactGroupImpl(MetaContactListServiceImpl.this, group.getGroupName());
                newMetaGroup.addProtoGroup(group);
                parent.addSubgroup(newMetaGroup);
            }
            else {
                newMetaGroup.addProtoGroup(group);
            }

            // check if there were any subgroups
            Iterator<ContactGroup> subgroups = group.subgroups();
            while (subgroups.hasNext()) {
                ContactGroup subgroup = subgroups.next();
                handleGroupCreatedEvent(newMetaGroup, subgroup);
            }

            Iterator<Contact> contactsIter = group.contacts();

            while (contactsIter.hasNext()) {
                Contact contact = contactsIter.next();
                MetaContactImpl newMetaContact = new MetaContactImpl();
                newMetaContact.addProtoContact(contact);
                newMetaContact.setDisplayName(contact.getDisplayName());
                newMetaGroup.addMetaContact(newMetaContact);
            }
            return newMetaGroup;
        }

        /**
         * Adds the source group and its child contacts to the meta contact list.
         *
         * @param evt the ServerStoredGroupEvent containing the source group.
         */
        public void groupCreated(ServerStoredGroupEvent evt) {
            Timber.log(TimberLog.FINER, "ContactGroup created: %s", evt);

            // ignore the event if the source group is in the ignore list
            if (isGroupInEventIgnoreList(evt.getSourceGroup().getGroupName(), evt.getSourceProvider())) {
                return;
            }

            MetaContactGroupImpl parentMetaGroup = (MetaContactGroupImpl)
                    findMetaContactGroupByContactGroup(evt.getParentGroup());
            if (parentMetaGroup == null) {
                Timber.e("Failed to identify a parent where group %s should be placed.",
                        evt.getSourceGroup().getGroupName());
            }

            // check whether the meta group was already existing before adding proto-groups to it
            boolean isExisting = parentMetaGroup.getMetaContactSubgroup(evt.getSourceGroup().getGroupName()) != null;

            // add parent group to the ServerStoredGroupEvent
            MetaContactGroup newMetaGroup = handleGroupCreatedEvent(parentMetaGroup,
                    evt.getSourceGroup());

            // if this was the first contact group in the meta group fire an
            // ADDED event. otherwise fire a modification event.
            if (newMetaGroup.countContactGroups() > 1 || isExisting) {
                fireMetaContactGroupEvent(newMetaGroup, evt.getSourceProvider(),
                        evt.getSourceGroup(), MetaContactGroupEvent.CONTACT_GROUP_ADDED_TO_META_GROUP);
            }
            else {
                fireMetaContactGroupEvent(newMetaGroup, evt.getSourceProvider(),
                        evt.getSourceGroup(), MetaContactGroupEvent.META_CONTACT_GROUP_ADDED);
            }
        }

        /**
         * Dummy implementation.
         * <p>
         *
         * @param evt a ServerStoredGroupEvent containing the source group.
         */
        public void groupResolved(ServerStoredGroupEvent evt) {
            // we couldn't care less :)
        }

        /**
         * Updates the local contact list by removing the meta contact group corresponding to the
         * group indicated by the delivered <code>evt</code>
         *
         * @param evt the ServerStoredGroupEvent confining the group that has been removed.
         */
        public void groupRemoved(ServerStoredGroupEvent evt) {
            Timber.log(TimberLog.FINER, "ContactGroup removed: %s", evt);

            MetaContactGroupImpl metaContactGroup = (MetaContactGroupImpl)
                    findMetaContactGroupByContactGroup(evt.getSourceGroup());

            if (metaContactGroup == null) {
                Timber.e("Received a RemovedGroup event for an orphan grp: %s", evt.getSourceGroup());
                return;
            }

            removeContactGroupFromMetaContactGroup(metaContactGroup, evt.getSourceGroup(), evt.getSourceProvider());
            if (metaContactGroup.countContactGroups() == 0) {
                removeMetaContactGroup(metaContactGroup);
            }
        }

        /**
         * Nothing to do here really. Oh yes .... we should actually trigger a MetaContactGroup
         * event indicating the change for interested parties but that's all.
         *
         * @param evt the ServerStoredGroupEvent containing the source group.
         */
        public void groupNameChanged(ServerStoredGroupEvent evt) {
            Timber.log(TimberLog.FINER, "ContactGroup renamed: %s", evt);

            MetaContactGroup metaContactGroup = findMetaContactGroupByContactGroup(evt.getSourceGroup());
            if (metaContactGroup.countContactGroups() == 1) {
                // if the only group contained in this group is renamed rename it
                ((MetaContactGroupImpl) metaContactGroup).setGroupName(evt.getSourceGroup().getGroupName());
            }
            fireMetaContactGroupEvent(metaContactGroup, evt.getSourceProvider(),
                    evt.getSourceGroup(), MetaContactGroupEvent.CONTACT_GROUP_RENAMED_IN_META_GROUP);
        }
    }

    /**
     * Creates the corresponding MetaContact event and notifies all
     * <code>MetaContactListListener</code>s that a MetaContact is added or removed from the
     * MetaContactList.
     *
     * @param sourceContact the contact that this event is about.
     * @param parentGroup the group that the source contact belongs or belonged to.
     * @param eventID the id indicating the exact type of the event to fire.
     */
    private synchronized void fireMetaContactEvent(MetaContact sourceContact,
            MetaContactGroup parentGroup, int eventID) {
        MetaContactEvent evt = new MetaContactEvent(sourceContact, parentGroup, eventID);
        Timber.log(TimberLog.FINER, "Will dispatch the following mcl event: %s", evt);

        for (MetaContactListListener listener : getMetaContactListListeners()) {
            switch (evt.getEventID()) {
                case MetaContactEvent.META_CONTACT_ADDED:
                    listener.metaContactAdded(evt);
                    break;
                case MetaContactEvent.META_CONTACT_REMOVED:
                    listener.metaContactRemoved(evt);
                    break;
                default:
                    Timber.e("Unknown event type %s", evt.getEventID());
            }
        }
    }

    /**
     * Gets a copy of the list of current {@code MetaContactListListener} interested in
     * events fired by this instance.
     *
     * @return an array of {@code MetaContactListListener}s currently interested in events
     * fired by this instance. The returned array is a copy of the internal listener storage and
     * thus can be safely modified.
     */
    private MetaContactListListener[] getMetaContactListListeners() {
        MetaContactListListener[] listeners;

        synchronized (mMetaContactListListeners) {
            listeners = mMetaContactListListeners.toArray(new MetaContactListListener[0]);
        }
        return listeners;
    }

    /**
     * Creates the corresponding <code>MetaContactPropertyChangeEvent</code> instance and notifies all
     * <code>MetaContactListListener</code>s that a MetaContact has been modified. Synchronized to
     * avoid firing events when we are editing the account (there we temporally remove and
     * then add again the storage manager and don't want anybody to interrupt us).
     *
     * @param event the event to dispatch.
     */
    synchronized void fireMetaContactEvent(MetaContactPropertyChangeEvent event) {
        Timber.log(TimberLog.FINER, "Dispatch mcl property change event: %s", event);

        for (MetaContactListListener listener : getMetaContactListListeners()) {
            if (event instanceof MetaContactMovedEvent) {
                listener.metaContactMoved((MetaContactMovedEvent) event);
            }
            else if (event instanceof MetaContactRenamedEvent) {
                listener.metaContactRenamed((MetaContactRenamedEvent) event);
            }
            else if (event instanceof MetaContactModifiedEvent) {
                listener.metaContactModified((MetaContactModifiedEvent) event);
            }
            else if (event instanceof MetaContactAvatarUpdateEvent) {
                Timber.d("Dispatch mcl MetaContactAvatarUpdateEvent: %s; %s", listener, event.getSourceMetaContact().getDefaultContact());
                listener.metaContactAvatarUpdated((MetaContactAvatarUpdateEvent) event);
            }
        }
    }

    /**
     * Creates the corresponding <code>ProtoContactEvent</code> instance and notifies all
     * <code>MetaContactListListener</code>s that a protocol specific <code>Contact</code> has been added
     * moved or removed. Synchronized to avoid firing events when we are editing the account (there
     * we temporally remove and then add again the storage manager and don't want anybody to
     * interrupt us).
     *
     * @param source the contact that has caused the event.
     * @param eventName One of the ProtoContactEvent.PROTO_CONTACT_XXX fields indicating the exact type of the
     * event.
     * @param oldParent the <code>MetaContact</code> that was wrapping the source <code>Contact</code> before the
     * event occurred or <code>null</code> if the event is caused by adding a new <code>Contact</code>
     * @param newParent the <code>MetaContact</code> that is wrapping the source <code>Contact</code> after the event
     * occurred or <code>null</code> if the event is caused by removing a <code>Contact</code>
     */
    private synchronized void fireProtoContactEvent(Contact source, String eventName,
            MetaContact oldParent, MetaContact newParent) {
        ProtoContactEvent event = new ProtoContactEvent(source, eventName, oldParent, newParent);
        Timber.log(TimberLog.FINER, "Will dispatch the following mcl property change event: %s", event);

        for (MetaContactListListener listener : getMetaContactListListeners()) {
            switch (eventName) {
                case ProtoContactEvent.PROTO_CONTACT_ADDED:
                    listener.protoContactAdded(event);
                    break;
                case ProtoContactEvent.PROTO_CONTACT_MOVED:
                    listener.protoContactMoved(event);
                    break;
                case ProtoContactEvent.PROTO_CONTACT_REMOVED:
                    listener.protoContactRemoved(event);
                    break;
                case ProtoContactEvent.PROTO_CONTACT_RENAMED:
                    listener.protoContactRenamed(event);
                    break;
                case ProtoContactEvent.PROTO_CONTACT_MODIFIED:
                    listener.protoContactModified(event);
                    break;
            }
        }
    }

    /**
     * Upon each status notification this method finds the corresponding meta contact and updates
     * the ordering in its parent group.
     *
     * @param evt the ContactPresenceStatusChangeEvent describing the status change.
     */
    @Override
    public void contactPresenceStatusChanged(ContactPresenceStatusChangeEvent evt) {
        MetaContactImpl metaContactImpl = (MetaContactImpl) findMetaContactByContact(evt.getSourceContact());

        // ignore if we have no meta contact.
        if (metaContactImpl == null)
            return;

        int oldContactIndex = metaContactImpl.getParentGroup().indexOf(metaContactImpl);
        int newContactIndex = metaContactImpl.reevalContact();
        if (oldContactIndex != newContactIndex) {
            fireMetaContactGroupEvent(findParentMetaContactGroup(metaContactImpl),
                    evt.getSourceProvider(), null, MetaContactGroupEvent.CHILD_CONTACTS_REORDERED);
        }
    }

    /**
     * The method is called from the storage manager whenever a new contact group has been parsed
     * and it has to be created.
     *
     * @param parentGroup the group that contains the meta contact group we're about to load.
     * @param metaContactGroupUID the unique identifier of the meta contact group.
     * @param displayName the name of the meta contact group.
     *
     * @return the newly created meta contact group.
     */
    public MetaContactGroupImpl loadStoredMetaContactGroup(MetaContactGroupImpl parentGroup,
            String metaContactGroupUID, String displayName) {
        // first check if the group exists already.
        MetaContactGroupImpl newMetaGroup
                = (MetaContactGroupImpl) parentGroup.getMetaContactSubgroupByUID(metaContactGroupUID);

        // if the group exists then we have already loaded it for another
        // account and we should reuse the same instance.
        if (newMetaGroup != null)
            return newMetaGroup;

        newMetaGroup = new MetaContactGroupImpl(this, displayName, metaContactGroupUID);
        parentGroup.addSubgroup(newMetaGroup);

        // I don't think this method needs to produce events since it is
        // currently only called upon initialization ... but it doesn't hurt trying
        fireMetaContactGroupEvent(newMetaGroup, null, null, MetaContactGroupEvent.META_CONTACT_GROUP_ADDED);
        return newMetaGroup;
    }

    /**
     * Creates a unresolved instance of the proto specific contact group according to the
     * specified arguments and adds it to <code>containingMetaContactGroup</code>
     *
     * @param containingMetaGroup the <code>MetaContactGroupImpl</code> where the restored contact group should be added.
     * @param contactGroupUID the unique identifier of the group.
     * @param parentProtoGroup the identifier of the parent proto group.
     * @param persistentData the persistent data last returned by the contact group.
     * @param accountID the ID of the account that the proto group belongs to.
     *
     * @return a reference to the newly created (unresolved) contact group.
     */
    public ContactGroup loadStoredContactGroup(MetaContactGroupImpl containingMetaGroup,
            String contactGroupUID, ContactGroup parentProtoGroup, String persistentData, String accountID) {
        // get the presence op set
        ProtocolProviderService sourceProvider = mCurrentlyInstalledProviders.get(accountID);
        OperationSetPersistentPresence presenceOpSet = sourceProvider.getOperationSet(OperationSetPersistentPresence.class);

        ContactGroup newProtoGroup = presenceOpSet.createUnresolvedContactGroup(contactGroupUID, persistentData,
                (parentProtoGroup == null) ? presenceOpSet.getServerStoredContactListRoot() : parentProtoGroup);

        containingMetaGroup.addProtoGroup(newProtoGroup);
        return newProtoGroup;
    }

    /**
     * The method is called from the storage manager whenever a new contact has been parsed and it
     * has to be created.
     *
     * @param parentGroup the group contains the meta contact we're about to load.
     * @param metaUID the unique identifier of the meta contact.
     * @param displayName the display name of the meta contact.
     * @param details the details for the contact to create.
     * @param protoContacts a list containing descriptors of proto contacts encapsulated by the meta contact that
     * we're about to create.
     * @param accountID the identifier of the account that the contacts originate from.
     *
     * @return the loaded meta contact.
     */
    public MetaContactImpl loadStoredMetaContact(MetaContactGroupImpl parentGroup, String metaUID, String displayName,
            JSONObject details, List<MclStorageManager.StoredProtoContactDescriptor> protoContacts, String accountID) {
        // first check if the meta contact exists already.
        MetaContactImpl newMetaContact = (MetaContactImpl) findMetaContactByMetaUID(metaUID);

        if (newMetaContact == null) {
            newMetaContact = new MetaContactImpl(metaUID, details);
            newMetaContact.setDisplayName(displayName);
        }

        // create unresolved contacts for the protoContacts associated with this metaContact
        ProtocolProviderService sourceProvider = mCurrentlyInstalledProviders.get(accountID);
        OperationSetPersistentPresence presenceOpSet
                = sourceProvider.getOperationSet(OperationSetPersistentPresence.class);

        for (MclStorageManager.StoredProtoContactDescriptor contactDescriptor : protoContacts) {
            // this contact has already been registered by another metaContact, so we'll ignore it.
            // If this is the only contact in the meta contact, we'll throw an exception at
            // the end of the method and cause the mcl storage manager to remove it.
            MetaContact mc = findMetaContactByContact(contactDescriptor.contactAddress, accountID);

            if (mc != null) {
                Timber.w("Ignoring duplicate metaContact %s accountID = %s. Duplication in metaContact: %s",
                        contactDescriptor, accountID, mc);
                continue;
            }

            Contact protoContact = presenceOpSet.createUnresolvedContact(
                    contactDescriptor.contactAddress, contactDescriptor.persistentData,
                    (contactDescriptor.parentProtoGroup == null)
                            ? presenceOpSet.getServerStoredContactListRoot()
                            : contactDescriptor.parentProtoGroup);

            newMetaContact.addProtoContact(protoContact);
        }

        if (newMetaContact.getContactCount() == 0) {
            Timber.e("Found an empty metaContact. Throw exception for MciStorageManager to remove it!");
            throw new IllegalArgumentException("MetaContact[" + newMetaContact
                    + "] contains zero non-duplicating child contacts.");
        }

        parentGroup.addMetaContact(newMetaContact);
        fireMetaContactEvent(newMetaContact, parentGroup, MetaContactEvent.META_CONTACT_ADDED);

        Timber.log(TimberLog.FINER, "Created meta contact: %s", newMetaContact);

        return newMetaContact;
    }

    /**
     * Creates the corresponding MetaContactGroup event and notifies all
     * <code>MetaContactListListener</code>s that a MetaContactGroup is added or removed from the
     * MetaContactList. Synchronized to avoid firing events when we are editing the account
     * (there we temporally remove and then add again the storage manager and don't want anybody
     * to interrupt us).
     * <p>
     * cmeng - have same effect in new sql implementation with single table row entry?
     * MetaContactGroupEvent.CONTACT_GROUP_REMOVED_FROM_META_GROUP:
     * MetaContactGroupEvent.META_CONTACT_GROUP_REMOVED: ==> Failed to find ...
     *
     * @param source the MetaContactGroup instance that is added to the MetaContactList
     * @param provider the ProtocolProviderService instance where this event occurred
     * @param sourceProtoGroup the proto group associated with this event or null if the event does not concern a
     * particular source group.
     * @param eventID one of the METACONTACT_GROUP_XXX static fields indicating the nature of the event.
     */
    private synchronized void fireMetaContactGroupEvent(MetaContactGroup source,
            ProtocolProviderService provider, ContactGroup sourceProtoGroup, int eventID) {
        MetaContactGroupEvent evt = new MetaContactGroupEvent(source, provider, sourceProtoGroup, eventID);
        Timber.log(TimberLog.FINER, "Will dispatch the following mcl event: %s", evt);

        for (MetaContactListListener listener : getMetaContactListListeners()) {
            switch (eventID) {
                case MetaContactGroupEvent.META_CONTACT_GROUP_ADDED:
                    listener.metaContactGroupAdded(evt);
                    break;
                case MetaContactGroupEvent.META_CONTACT_GROUP_RENAMED:
                    listener.metaContactGroupModified(evt);
                    break;
                case MetaContactGroupEvent.META_CONTACT_GROUP_REMOVED:
                    listener.metaContactGroupRemoved(evt);
                    break;
                case MetaContactGroupEvent.CONTACT_GROUP_ADDED_TO_META_GROUP:
                case MetaContactGroupEvent.CONTACT_GROUP_RENAMED_IN_META_GROUP:
                case MetaContactGroupEvent.CONTACT_GROUP_REMOVED_FROM_META_GROUP:
                    listener.metaContactGroupModified(evt);
                    break;
                case MetaContactGroupEvent.CHILD_CONTACTS_REORDERED:
                    listener.childContactsReordered(evt);
                    break;
                default:
                    Timber.e("Unknown event type (%s) for event: %s", eventID, evt);
            }
        }
    }

    /**
     * Utility class used for blocking the current thread until an event is delivered confirming
     * the creation of a particular group.
     */
    private static class BlockingGroupEventRetriever implements ServerStoredGroupListener {
        private final String mGroupName;

        public ServerStoredGroupEvent mEvent = null;

        /**
         * Creates an instance of the retriever that will wait for events confirming the creation
         * of the group with the specified name.
         *
         * @param groupName the name of the group whose birth we're waiting for.
         */
        BlockingGroupEventRetriever(String groupName) {
            mGroupName = groupName;
        }

        /**
         * Called whoever an indication is received that a new server stored group is created.
         *
         * @param event a ServerStoredGroupChangeEvent containing a reference to the newly created group.
         */
        public synchronized void groupCreated(ServerStoredGroupEvent event) {
            if (event.getSourceGroup().getGroupName().equals(mGroupName)) {
                mEvent = event;
                notifyAll();
            }
        }

        /**
         * Evens delivered through this method are ignored
         *
         * @param event param ignored
         */
        public void groupRemoved(ServerStoredGroupEvent event) {
        }

        /**
         * Evens delivered through this method are ignored
         *
         * @param event param ignored
         */
        public void groupNameChanged(ServerStoredGroupEvent event) {
        }

        /**
         * Evens delivered through this method are ignored
         *
         * @param event param ignored
         */
        public void groupResolved(ServerStoredGroupEvent event) {
        }

        /**
         * Block the execution of the current thread until either a group created event is
         * received or milliseconds pass.
         *
         * @param millis the number of ms that we should wait before we determine failure.
         */
        public synchronized void waitForEvent(long millis) {
            // no need to wait if an event is already there.
            if (mEvent == null) {
                try {
                    wait(millis);
                } catch (InterruptedException ex) {
                    Timber.e(ex, "Interrupted while waiting for group creation");
                }
            }
        }
    }

    /**
     * Utility class used for blocking the current thread until an event is delivered confirming
     * the creation of a particular contact.
     */
    private static class BlockingSubscriptionEventRetriever implements SubscriptionListener, ServerStoredGroupListener {
        private final String mSubscriptionAddress;

        public Contact mSourceContact = null;
        public EventObject mEvent = null;

        /**
         * Events delivered through this method are ignored
         *
         * @param event param ignored
         */
        public void groupResolved(ServerStoredGroupEvent event) {
        }

        /**
         * Events delivered through this method are ignored
         *
         * @param event param ignored
         */
        public void groupRemoved(ServerStoredGroupEvent event) {
        }

        /**
         * Events delivered through this method are ignored
         *
         * @param event param ignored
         */
        public void groupNameChanged(ServerStoredGroupEvent event) {
        }

        /**
         * Creates an instance of the retriever that will wait for events confirming the creation
         * of the subscription with the specified address.
         *
         * @param subscriptionAddress the name of the group whose birth we're waiting for.
         */
        BlockingSubscriptionEventRetriever(String subscriptionAddress) {
            mSubscriptionAddress = subscriptionAddress;
        }

        /**
         * Called whenever an indication is received that a new server stored group is created.
         *
         * @param event a ServerStoredGroupEvent containing a reference to the newly created group.
         */
        public synchronized void groupCreated(ServerStoredGroupEvent event) {
            Contact contact = event.getSourceGroup().getContact(mSubscriptionAddress);
            if (contact != null) {
                mEvent = event;
                mSourceContact = contact;
                notifyAll();
            }
        }

        /**
         * Called whenever an indication is received that a subscription is created.
         *
         * @param event a <code>SubscriptionEvent</code> containing a reference to the newly created contact.
         */
        public synchronized void subscriptionCreated(SubscriptionEvent event) {
            if (event.getSourceContact().getAddress().equals(mSubscriptionAddress)
                    || event.getSourceContact().toString().equals(mSubscriptionAddress)) {
                mEvent = event;
                mSourceContact = event.getSourceContact();
                notifyAll();
            }
        }

        /**
         * Events delivered through this method are ignored
         *
         * @param event param ignored
         */
        public void subscriptionRemoved(SubscriptionEvent event) {
        }

        /**
         * Called whenever an indication is received that a subscription creation has failed.
         *
         * @param event a <code>SubscriptionEvent</code> containing a reference to the contact we are trying
         * to subscribe.
         */
        public synchronized void subscriptionFailed(SubscriptionEvent event) {
            if (event.getSourceContact().getAddress().equals(mSubscriptionAddress)) {
                mEvent = event;
                mSourceContact = event.getSourceContact();
                notifyAll();
            }
        }

        /**
         * Events delivered through this method are ignored
         *
         * @param event param ignored
         */
        public void subscriptionMoved(SubscriptionMovedEvent event) {
        }

        /**
         * Events delivered through this method are ignored
         *
         * @param event param ignored
         */
        public void subscriptionResolved(SubscriptionEvent event) {
        }

        /**
         * Events delivered through this method are ignored
         *
         * @param event param ignored
         */
        public void contactModified(ContactPropertyChangeEvent event) {
        }

        /**
         * Block the execution of the current thread until either a contact created event is
         * received or milliseconds pass.
         *
         * @param millis the number of ms to wait upon determining a failure.
         */
        public synchronized void waitForEvent(long millis) {
            // no need to wait if an event is already there.
            if (mEvent == null) {
                try {
                    wait(millis);
                } catch (InterruptedException ex) {
                    Timber.e(ex, "Interrupted while waiting for contact creation");
                }
            }
        }
    }

    /**
     * Notify the listener that the list of the <code>OperationSet</code> capabilities of a <code>Contact</code> has changed.
     * cmeng: need more work here? to handle protocol contact capability changes for
     * MciStorageManager which only taking care of persistent data.
     *
     * @param event a <code>ContactCapabilitiesEvent</code> which specifies the <code>Contact</code>
     * whose list of <code>OperationSet</code> capabilities has changed
     */
    public void supportedOperationSetsChanged(ContactCapabilitiesEvent event) {
        // If the source contact not in this meta contact, we have nothing more to do here.
        Contact contact = event.getSourceContact();
        MetaContactImpl metaContactImpl = (MetaContactImpl) findMetaContactByContact(contact);
        if (metaContactImpl != null) {
            metaContactImpl.updateCapabilities(contact, event.getJid(), event.getOperationSets());
            fireProtoContactEvent(contact, ProtoContactEvent.PROTO_CONTACT_MODIFIED, metaContactImpl, metaContactImpl);
        }
    }
}
