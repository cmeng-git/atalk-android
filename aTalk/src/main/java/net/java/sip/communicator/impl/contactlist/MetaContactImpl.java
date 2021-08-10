/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 * * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package net.java.sip.communicator.impl.contactlist;

import net.java.sip.communicator.impl.protocol.jabber.ProtocolProviderServiceJabberImpl;
import net.java.sip.communicator.impl.protocol.jabber.ScServiceDiscoveryManager;
import net.java.sip.communicator.service.contactlist.MetaContact;
import net.java.sip.communicator.service.contactlist.MetaContactGroup;
import net.java.sip.communicator.service.contactlist.event.MetaContactModifiedEvent;
import net.java.sip.communicator.service.protocol.*;
import net.java.sip.communicator.util.DataObject;

import org.jivesoftware.smack.XMPPConnection;
import org.jivesoftware.smack.packet.Presence;
import org.jivesoftware.smack.roster.Roster;
import org.jivesoftware.smackx.avatar.AvatarManager;
import org.jivesoftware.smackx.disco.packet.DiscoverInfo;
import org.json.*;
import org.jxmpp.jid.BareJid;
import org.jxmpp.jid.Jid;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

import androidx.annotation.NonNull;
import timber.log.Timber;

/**
 * A default implementation of the <code>MetaContact</code> interface.
 *
 * @author Emil Ivov
 * @author Lubomir Marinov
 * @author Eng Chong Meng
 */
public class MetaContactImpl extends DataObject implements MetaContact
{
    /**
     * A vector containing all protocol specific contacts merged in this MetaContact.
     */
    private final List<Contact> protoContacts = new Vector<>();

    /**
     * The list of capabilities of the meta contact i.e. map of each OperationSet for all the contacts that support it.
     * Currently has problem as OpSet capability get updated by last contact resource presence
     */
    private final ConcurrentHashMap<String, List<Contact>> capabilities = new ConcurrentHashMap<>();

    /**
     * The list of capabilities of the meta contact FullJid i.e. all contact resources. To overcome the above problem
     */
    private final ConcurrentHashMap<String, List<Jid>> capabilityJid = new ConcurrentHashMap<>();

    /**
     * The number of contacts online in this meta contact.
     */
    private int contactsOnline = 0;

    /**
     * The number of unread messages
     */
    private int unreadCount = 0;

    /**
     * An id uniquely identifying the meta contact in this contact list.
     */
    private final String uid;

    /**
     * Returns a human readable string used by the UI to display the contact.
     */
    private String displayName = "";

    /**
     * The contact that should be chosen by default when communicating with this meta contact.
     */
    private Contact defaultContact = null;

    /**
     * A locally cached copy of an avatar that we should return for lazy calls to the
     * getAvatarMethod() in order to speed up display.
     */
    private byte[] mCachedAvatar = null;

    /**
     * A flag that tells us whether or not we have already tried to restore
     * an avatar from cache. We need this to know whether a <tt>null</tt> cached
     * avatar implies that there is no locally stored avatar or that we simply
     * haven't tried to retrieve it. This should allow us to only interrogate
     * the file system if haven't done so before.
     */
    private boolean avatarFileCacheAlreadyQueried = false;

    /**
     * A callback to the meta contact group that is currently our parent. If
     * this is an orphan meta contact that has not yet been added or has been
     * removed from a group this callback is going to be null.
     */
    private MetaContactGroupImpl parentGroup = null;

    /**
     * JSONObject containing the contact details i.e. Name -> JSONArray.
     */
    private JSONObject details;

    /**
     * Whether user has renamed this meta contact.
     */
    private boolean isDisplayNameUserDefined = false;

    /**
     * Creates new meta contact with a newly generated meta contact UID.
     */
    MetaContactImpl()
    {
        // create the uid
        this.uid = String.valueOf(System.currentTimeMillis()) + String.valueOf(hashCode());
        this.details = new JSONObject();
    }

    /**
     * Creates a new meta contact with the specified UID. This constructor MUST ONLY be used when
     * restoring contacts from persistent storage.
     *
     * @param metaUID the meta uid that this meta contact should have.
     * @param details the already stored details for the contact.
     */
    MetaContactImpl(String metaUID, JSONObject details)
    {
        this.uid = metaUID;
        this.details = details;
    }

    /**
     * Returns the number of protocol specific <tt>Contact</tt>s that this <tt>MetaContact</tt> contains.
     *
     * @return an int indicating the number of protocol specific contacts merged in this <tt>MetaContact</tt>
     */
    public int getContactCount()
    {
        return protoContacts.size();
    }

    /**
     * Returns a Contact, encapsulated by this MetaContact and coming from the specified ProtocolProviderService.
     *
     * In order to prevent problems with concurrency, the <tt>Iterator</tt>
     * returned by this method is not be over the actual list of contacts but over a copy of that list.
     *
     * @param provider a reference to the <tt>ProtocolProviderService</tt> that we'd like to get a <tt>Contact</tt> for.
     * @return a <tt>Contact</tt> encapsulated in this <tt>MetaContact</tt> nd originating from the specified provider.
     */
    public Iterator<Contact> getContactsForProvider(ProtocolProviderService provider)
    {
        LinkedList<Contact> providerContacts = new LinkedList<>();
        for (Contact contact : protoContacts) {
            if (contact.getProtocolProvider() == provider)
                providerContacts.add(contact);
        }
        return providerContacts.iterator();
    }

    /**
     * Returns all protocol specific Contacts, encapsulated by this MetaContact and supporting the
     * given <tt>opSetClass</tt>. If none of the contacts encapsulated by this MetaContact is
     * supporting the specified <tt>OperationSet</tt> class then an empty iterator is returned.
     *
     * @param opSetClass the operation for which the default contact is needed
     * @return a <tt>List</tt> over all contacts encapsulated in this <tt>MetaContact</tt> and
     * supporting the specified <tt>OperationSet</tt>
     */
    public List<Contact> getContactsForOperationSet(Class<? extends OperationSet> opSetClass)
    {
        LinkedList<Contact> opSetContacts = new LinkedList<>();
        for (Contact contact : protoContacts) {
            ProtocolProviderService contactProvider = contact.getProtocolProvider();
            // First try to ask the capabilities operation set if such is available.
            OperationSetContactCapabilities capOpSet
                    = contactProvider.getOperationSet(OperationSetContactCapabilities.class);

            if (capOpSet != null) {
                synchronized (capabilities) {
                    List<Contact> capContacts = capabilities.get(opSetClass.getName());
                    if ((capContacts != null) && capContacts.contains(contact))
                        opSetContacts.add(contact);
                }
            }
            else if (contactProvider.getOperationSet(opSetClass) != null)
                opSetContacts.add(contact);
        }
        return opSetContacts;
    }

    /**
     * Determines if the given <tt>feature</tt> is supported by this metaContact for all presence contact.
     *
     * @param feature the feature to check for
     * @return <tt>true</tt> if the required feature is supported; otherwise, <tt>false</tt>
     */
    public boolean isFeatureSupported(String feature)
    {
        Contact contact = getDefaultContact();
        ProtocolProviderServiceJabberImpl pps = (ProtocolProviderServiceJabberImpl) contact.getProtocolProvider();

        ScServiceDiscoveryManager discoveryManager = pps.getDiscoveryManager();
        if (discoveryManager == null)
            return  false;

        // Proceed only for presence with Type.available
        List<Presence> presences = Roster.getInstanceFor(pps.getConnection()).getPresences(contact.getJid().asBareJid());
        for (Presence presence : presences) {
            if (presence.isAvailable()) {
                DiscoverInfo featureInfo = discoveryManager.discoverInfoNonBlocking(presence.getFrom());
                if ((featureInfo != null) && featureInfo.containsFeature(feature)) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Returns contacts, encapsulated by this MetaContact and belonging to the specified protocol ContactGroup.
     *
     * In order to prevent problems with concurrency, the <tt>Iterator</tt> returned by
     * this method is not be over the actual list of contacts but over a copy of that list.
     *
     * @param parentProtoGroup
     * @return an Iterator over all <tt>Contact</tt>s encapsulated in this
     * <tt>MetaContact</tt> and belonging to the specified proto ContactGroup.
     */
    public Iterator<Contact> getContactsForContactGroup(ContactGroup parentProtoGroup)
    {
        List<Contact> providerContacts = new LinkedList<>();
        for (Contact contact : protoContacts) {
            if (contact.getParentContactGroup() == parentProtoGroup)
                providerContacts.add(contact);
        }
        return providerContacts.iterator();
    }

    /**
     * Returns a contact encapsulated by this meta contact, having the specified
     * contactAddress and coming from the indicated ownerProvider.
     *
     * @param contactAddress the address of the contact who we're looking for.
     * @param ownerProvider a reference to the ProtocolProviderService that the contact we're looking for belongs
     * to.
     * @return a reference to a <tt>Contact</tt>, encapsulated by this MetaContact, carrying
     * the specified address and originating from the specified ownerProvider or null if no such contact exists..
     */
    public Contact getContact(String contactAddress, ProtocolProviderService ownerProvider)
    {
        for (Contact contact : protoContacts) {
            if ((contact.getProtocolProvider() == ownerProvider)
                    && (contact.getAddress().equals(contactAddress)
                    || contact.toString().equals(contactAddress)))
                return contact;
        }
        return null;
    }

    /**
     * Returns a contact encapsulated by this meta contact, having the specified
     * contactAddress and coming from a provider with a matching
     * <tt>accountID</tt>. The method returns null if no such contact exists.
     *
     * @param contactAddress the address of the contact who we're looking for.
     * @param accountID the identifier of the provider that the contact we're looking for must belong to.
     * @return a reference to a <tt>Contact</tt>, encapsulated by this MetaContact, carrying the
     * specified address and originating from the ownerProvider carrying <tt>accountID</tt>.
     */
    public Contact getContact(String contactAddress, String accountID)
    {
        for (Contact contact : protoContacts) {
            if (contact.getProtocolProvider().getAccountID().getAccountUniqueID().equals(accountID)
                    && contact.getAddress().equals(contactAddress))
                return contact;
        }
        return null;
    }

    /**
     * Returns <tt>true</tt> if the given <tt>protocolContact</tt> is contained in
     * this <tt>MetaContact</tt>, otherwise - returns <tt>false</tt>.
     *
     * @param protocolContact the <tt>Contact</tt> we're looking for
     * @return <tt>true</tt> if the given <tt>protocolContact</tt> is contained in
     * this <tt>MetaContact</tt>, otherwise - returns <tt>false</tt>
     */
    public boolean containsContact(Contact protocolContact)
    {
        return protoContacts.contains(protocolContact);
    }

    /**
     * Returns a <tt>java.util.Iterator</tt> over all protocol specific <tt>Contacts</tt>
     * encapsulated by this <tt>MetaContact</tt>.
     *
     * In order to prevent problems with concurrency, the <tt>Iterator</tt> returned by
     * this method is not over the actual list of contacts but over a copy of that list.
     *
     * @return a <tt>java.util.Iterator</tt> over all protocol specific <tt>Contact</tt>s
     * that were registered as subContacts for this <tt>MetaContact</tt>
     */
    public Iterator<Contact> getContacts()
    {
        return new LinkedList<>(protoContacts).iterator();
    }

    /**
     * Currently simply returns the most connected protocol contact. We should add
     * the possibility to choose it also according to pre-configured preferences.
     *
     * @return the default <tt>Contact</tt> to use when communicating with this <tt>MetaContact</tt>
     */
    public Contact getDefaultContact()
    {
        if (defaultContact == null) {
            PresenceStatus currentStatus = null;
            for (Contact protoContact : protoContacts) {
                PresenceStatus contactStatus = protoContact.getPresenceStatus();

                if (currentStatus != null) {
                    if (currentStatus.getStatus() < contactStatus.getStatus()) {
                        currentStatus = contactStatus;
                        defaultContact = protoContact;
                    }
                }
                else {
                    currentStatus = contactStatus;
                    defaultContact = protoContact;
                }
            }
        }
        return defaultContact;
    }

    /**
     * Returns a default contact for a specific operation (call, file transfer, IM ...)
     * cmeng may possibly replaced by getOpSetSupportedContact()
     *
     * @param operationSet the operation for which the default contact is needed
     * @return the default contact for the specified operation.
     */
    public Contact getDefaultContact(Class<? extends OperationSet> operationSet)
    {
        Contact defaultOpSetContact = null;
        Contact defaultContact = getDefaultContact();

        // if the current default contact supports the requested operationSet we use it
        if (defaultContact != null) {
            ProtocolProviderService pps = defaultContact.getProtocolProvider();

            // First try to ask the capabilities operation set if such is available.
            OperationSetContactCapabilities capOpSet = pps.getOperationSet(OperationSetContactCapabilities.class);

            if (capOpSet != null) {
                synchronized (capabilities) {
                    List<Contact> capContacts = capabilities.get(operationSet.getName());
                    if (capContacts != null && capContacts.contains(defaultContact)) {
                        defaultOpSetContact = defaultContact;
                    }
                }
            }
            else if (pps.getOperationSet(operationSet) != null) {
                defaultOpSetContact = defaultContact;
            }
        }

        // if default not supported, then check the protoContacts for one
        if (defaultOpSetContact == null) {
            PresenceStatus currentStatus = null;

            for (Contact protoContact : protoContacts) {
                ProtocolProviderService contactProvider = protoContact.getProtocolProvider();

                // First try to ask the capabilities operation set if such is available.
                OperationSetContactCapabilities capOpSet
                        = contactProvider.getOperationSet(OperationSetContactCapabilities.class);

                // We filter to care only about contact which support the needed opset.
                if (capOpSet != null) {
                    synchronized (capabilities) {
                        List<Contact> capContacts = capabilities.get(operationSet.getName());
                        if (capContacts == null || !capContacts.contains(protoContact)) {
                            continue;
                        }
                    }
                }
                else if (contactProvider.getOperationSet(operationSet) == null)
                    continue;

                PresenceStatus contactStatus = protoContact.getPresenceStatus();
                if (currentStatus != null) {
                    if (currentStatus.getStatus() < contactStatus.getStatus()) {
                        currentStatus = contactStatus;
                        defaultOpSetContact = protoContact;
                    }
                }
                else {
                    currentStatus = contactStatus;
                    defaultOpSetContact = protoContact;
                }
            }
        }
        return defaultOpSetContact;
    }

    /**
     * Returns a contact for a specific operationSet (call, file transfer, IM ...), null if none is found
     * Note: this is currently used for showing the video/call buttons; and protoContacts.size() == 1
     *
     * @param operationSet the operation for which the contact is needed
     * @return a contact that supports the specified operation.
     */
    public Contact getOpSetSupportedContact(Class<? extends OperationSet> operationSet)
    {
        for (Contact opSetContact : protoContacts) {
            Jid jid = opSetContact.getJid();  // always a BareJid

            // First try to ask the capabilities operation set if such is available.
            ProtocolProviderService pps = opSetContact.getProtocolProvider();
            OperationSetContactCapabilities capOpSet = pps.getOperationSet(OperationSetContactCapabilities.class);

            // We filter to care only about opSetContact which support the needed opSet.
            if (capOpSet != null) {
                synchronized (capabilityJid) {
                    List<Jid> capJids = capabilityJid.get(operationSet.getName());
                    // Just return null if none supported
                    if (capJids == null)
                        return null;

                    for (Jid jidx : capJids) {
                        if (jid.isParentOf(jidx)) {
                            return opSetContact;
                        }
                    }
                }
            }
        }
        return null;
    }

    /**
     * Returns a String identifier (the actual contents is left to implementations) this
     * <tt>MetaContact</tt> in that uniquely represents the containing <tt>MetaContactList</tt>
     *
     * @return a String uniquely identifying this meta contact.
     */
    public String getMetaUID()
    {
        return uid;
    }

    /**
     * Set the unread message count for this metaContact
     *
     * @param count unread message count
     */
    public void setUnreadCount(int count)
    {
        unreadCount = count;
    }

    /**
     * Returns the unread message count for this metaContact
     *
     * @return the unread message count
     */
    public int getUnreadCount()
    {
        return unreadCount;
    }

    /**
     * Compares this meta contact with the specified object for order.  Returns
     * a negative integer, zero, or a positive integer as this meta contact is
     * less than, equal to, or greater than the specified object.
     *
     * The result of this method is calculated the following way:
     *
     * (contactsOnline - o.contactsOnline) * 1 000 000 <br>
     * + getDisplayName().compareTo(o.getDisplayName()) * 100 000
     * + getMetaUID().compareTo(o.getMetaUID())<br>
     *
     * Or in other words ordering of meta accounts would be first done by presence status,, and
     * finally (in order to avoid then display name equalities) be the fairly random meta contact metaUID.
     *
     * @param o the <code>MetaContact</code> to be compared.
     * @return a negative integer, zero, or a positive integer as this object
     * is less than, equal to, or greater than the specified object.
     * @throws ClassCastException if the specified object is not a MetaContactListImpl
     */
    public int compareTo(@NonNull MetaContact o)
    {
        MetaContactImpl target = (MetaContactImpl) o;

        int isOnline = (contactsOnline > 0) ? 1 : 0;
        int targetIsOnline = (target.contactsOnline > 0) ? 1 : 0;

        return ((10 - isOnline) - (10 - targetIsOnline)) * 100000000
                + getDisplayName().compareToIgnoreCase(target.getDisplayName()) * 10000
                + getMetaUID().compareTo(target.getMetaUID());
    }

    /**
     * Returns a string representation of this contact, containing most of its representative details.
     *
     * @return a string representation of this contact.
     */
    @Override
    public String toString()
    {
        return "MetaContact[ DisplayName=" + getDisplayName() + "]";
    }

    /**
     * Returns a characteristic display name that can be used when including
     * this <tt>MetaContact</tt> in user interface.
     *
     * @return a human readable String that represents this meta contact.
     */
    public String getDisplayName()
    {
        return displayName;
    }

    /**
     * Determines if display name was changed for this <tt>MetaContact</tt> in user interface.
     *
     * @return whether display name was changed by user.
     */
    boolean isDisplayNameUserDefined()
    {
        return isDisplayNameUserDefined;
    }

    /**
     * Changes that display name was changed for this <tt>MetaContact</tt> in user interface.
     *
     * @param value control whether display name is user defined
     */
    public void setDisplayNameUserDefined(boolean value)
    {
        this.isDisplayNameUserDefined = value;
    }

    /**
     * Queries a specific protocol <tt>Contact</tt> for its avatar. Beware that this method
     * could cause multiple network operations. Use with caution.
     *
     * @param contact the protocol <tt>Contact</tt> to query for its avatar
     * @return an array of <tt>byte</tt>s representing the avatar returned by the
     * specified <tt>Contact</tt> or <tt>null</tt> if the
     * specified <tt>Contact</tt> did not or failed to return an avatar
     */
    private byte[] queryProtoContactAvatar(Contact contact)
    {
        try {
            byte[] contactImage = contact.getImage();

            if ((contactImage != null) && (contactImage.length > 0))
                return contactImage;
        } catch (Exception ex) {
            Timber.e(ex, "Failed to get the photo of contact %s", contact);
        }
        return null;
    }

    /**
     * Returns the avatar of this contact, that can be used when including this
     * <tt>MetaContact</tt> in user interface. The isLazy parameter would tell
     * the implementation if it could return the locally stored avatar or it
     * should obtain the avatar right from the server.
     *
     * @param isLazy Indicates if this method should return the locally stored avatar or it should
     * obtain the avatar right from the server.
     * @return an avatar (e.g. user photo) of this contact.
     */
    public byte[] getAvatar(boolean isLazy)
    {
        byte[] result;
        if (!isLazy) {
            // the caller is willing to perform a lengthy operation so let's
            // query the proto contacts for their avatars.
            Iterator<Contact> protoContacts = getContacts();

            while (protoContacts.hasNext()) {
                Contact contact = protoContacts.next();
                result = queryProtoContactAvatar(contact);

                // if we got a result from the above, then let's cache and return it.
                if ((result != null) && (result.length > 0)) {
                    cacheAvatar(contact, result);
                    return result;
                }
            }
        }

        // if we get here then the caller is probably not willing to perform
        // network operations and opted for a lazy retrieve (... or the
        // queryAvatar method returned null because we are calling it too often)
        if ((mCachedAvatar != null) && (mCachedAvatar.length > 0)) {
            // we already have a cached avatar, so let's return it
            return mCachedAvatar;
        }

        // no cached avatar. let's try the file system for previously stored
        // ones. (unless we already did this)
        if (avatarFileCacheAlreadyQueried)
            return null;
        avatarFileCacheAlreadyQueried = true;

        Iterator<Contact> iter = getContacts();
        while (iter.hasNext()) {
            Contact protoContact = iter.next();
            if (protoContact == null)
                continue;

            // mCachedAvatar = AvatarCacheUtils.getCachedAvatar(protoContact);
            BareJid bareJid = protoContact.getJid().asBareJid();
            mCachedAvatar = AvatarManager.getAvatarImageByJid(bareJid);

            /*
             * Caching a zero-length avatar happens but such an avatar isn't very useful.
             */
            if ((mCachedAvatar != null) && (mCachedAvatar.length > 0))
                return mCachedAvatar;
        }
        return null;
    }

    /**
     * Returns an avatar that can be used when presenting this <tt>MetaContact</tt> in user interface.
     * The method would also make sure that we try the network for new versions of avatars.
     *
     * @return an avatar (e.g. user photo) of this contact.
     */
    public byte[] getAvatar()
    {
        return getAvatar(false);
    }

    /**
     * Sets a name that can be used when displaying this contact in user interface components.
     *
     * @param displayName a human readable String representing this <tt>MetaContact</tt>
     */
    void setDisplayName(String displayName)
    {
        synchronized (getParentGroupModLock()) {
            if (parentGroup != null)
                parentGroup.lightRemoveMetaContact(this);

            this.displayName = (displayName == null) ? "" : displayName;
            if (parentGroup != null)
                parentGroup.lightAddMetaContact(this);
        }
    }

    /**
     * Adds the specified protocol specific contact to the list of contacts merged in this
     * meta contact. The method also keeps up to date the contactsOnline field which is used
     * in the compareTo() method.
     *
     * @param contact the protocol specific Contact to add.
     */
    void addProtoContact(Contact contact)
    {
        synchronized (getParentGroupModLock()) {
            if (parentGroup != null)
                parentGroup.lightRemoveMetaContact(this);
            contactsOnline += contact.getPresenceStatus().isOnline() ? 1 : 0;

            this.protoContacts.add(contact);

            // Re-init the default contact.
            defaultContact = null;

            // if this is our first contact and we don't already have a display
            // name, use theirs.
            if (this.protoContacts.size() == 1 && (this.displayName == null
                    || this.displayName.trim().length() == 0)) {
                // be careful not to use setDisplayName() here cause this will bring us into a deadlock.
                this.displayName = contact.getDisplayName();
            }

            if (parentGroup != null)
                parentGroup.lightAddMetaContact(this);

            ProtocolProviderService contactProvider = contact.getProtocolProvider();

            // Check if the capabilities operation set is available for this
            // contact and add a listener to it in order to track capabilities'
            // changes for all contained protocol contacts.
            OperationSetContactCapabilities capOpSet
                    = contactProvider.getOperationSet(OperationSetContactCapabilities.class);

            if (capOpSet != null) {
                addCapabilities(contact, capOpSet.getSupportedOperationSets(contact));
            }
        }
    }

    /**
     * Called by MetaContactListServiceImpl after a contact has changed its status,
     * so that ordering in the parent group is updated. The method also elects the
     * most connected contact as default contact.
     *
     * @return the new index at which the contact was added.
     */
    int reevalContact()
    {
        synchronized (getParentGroupModLock()) {
            // first lightremove or otherwise we won't be able to get hold of the contact
            if (parentGroup != null) {
                parentGroup.lightRemoveMetaContact(this);
            }

            this.contactsOnline = 0;
            int maxContactStatus = 0;

            for (Contact contact : protoContacts) {
                int contactStatus = contact.getPresenceStatus().getStatus();

                if (maxContactStatus < contactStatus) {
                    maxContactStatus = contactStatus;
                    this.defaultContact = contact;
                }
                if (contact.getPresenceStatus().isOnline())
                    contactsOnline++;
            }
            // now read it and the contact would be automatically placed
            // properly by the containing group
            if (parentGroup != null) {
                return parentGroup.lightAddMetaContact(this);
            }
        }

        return -1;
    }

    /**
     * Removes the specified protocol specific contact from the contacts encapsulated in
     * this <code>MetaContact</code>. The method also updates the total status field accordingly.
     * And updates its ordered position in its parent group. If the display name of this
     * <code>MetaContact</code> was the one of the removed contact, we update it.
     *
     * @param contact the contact to remove
     */
    void removeProtoContact(Contact contact)
    {
        synchronized (getParentGroupModLock()) {
            if (parentGroup != null)
                parentGroup.lightRemoveMetaContact(this);
            contactsOnline -= contact.getPresenceStatus().isOnline() ? 1 : 0;
            this.protoContacts.remove(contact);

            if (defaultContact == contact)
                defaultContact = null;

            if ((protoContacts.size() > 0) && displayName.equals(contact.getDisplayName())) {
                displayName = getDefaultContact().getDisplayName();
            }

            if (parentGroup != null)
                parentGroup.lightAddMetaContact(this);

            ProtocolProviderService contactProvider = contact.getProtocolProvider();

            // Check if the capabilities operation set is available for this
            // contact and add a listener to it in order to track capabilities'
            // changes for all contained protocol contacts.
            OperationSetContactCapabilities capOpSet = contactProvider.getOperationSet(
                    OperationSetContactCapabilities.class);

            if (capOpSet != null) {
                removeCapabilities(contact, capOpSet.getSupportedOperationSets(contact));
            }
        }
    }

    /**
     * Removes all proto contacts that belong to the specified provider.
     *
     * @param provider the provider whose contacts we want removed.
     * @return true if this <tt>MetaContact</tt> was modified and false otherwise.
     */
    boolean removeContactsForProvider(ProtocolProviderService provider)
    {
        boolean modified = false;
        Iterator<Contact> contactsIter = protoContacts.iterator();

        while (contactsIter.hasNext()) {
            Contact contact = contactsIter.next();

            if (contact.getProtocolProvider() == provider) {
                contactsIter.remove();
                modified = true;
            }
        }
        // if the default contact has been modified, set it to null
        if (modified && !protoContacts.contains(defaultContact)) {
            defaultContact = null;
        }
        return modified;
    }

    /**
     * Removes all proto contacts that belong to the specified protocol group.
     *
     * @param protoGroup the group whose children we want removed.
     * @return true if this <tt>MetaContact</tt> was modified and false otherwise.
     */
    boolean removeContactsForGroup(ContactGroup protoGroup)
    {
        boolean modified = false;
        Iterator<Contact> contacts = protoContacts.iterator();

        while (contacts.hasNext()) {
            Contact contact = contacts.next();
            if (contact.getParentContactGroup() == protoGroup) {
                contacts.remove();
                modified = true;
            }
        }
        // if the default contact has been modified, set it to null
        if (modified && !protoContacts.contains(defaultContact)) {
            defaultContact = null;
        }
        return modified;
    }

    /**
     * Sets <tt>parentGroup</tt> as a parent of this meta contact. Do not call this method with a
     * null argument even if a group is removing this contact from itself as this could lead to
     * race conditions (imagine another group setting itself as the new parent and you
     * removing it). Use unsetParentGroup instead.
     *
     * @param parentGroup the <tt>MetaContactGroupImpl</tt> that is currently a parent of this meta contact.
     * @throws NullPointerException if <tt>parentGroup</tt> is null.
     */
    void setParentGroup(MetaContactGroupImpl parentGroup)
    {
        if (parentGroup == null)
            throw new NullPointerException("Do not call this method with a "
                    + "null argument even if a group is removing this contact "
                    + "from itself as this could lead to race conditions "
                    + "(imagine another group setting itself as the new parent"
                    + " and you  removing it). Use unsetParentGroup instead.");

        synchronized (getParentGroupModLock()) {
            this.parentGroup = parentGroup;
        }
    }

    /**
     * If <tt>parentGroup</tt> was the parent of this meta contact then it sets it to null. Call
     * this method when removing this contact from a meta contact group.
     *
     * @param parentGrp the <tt>MetaContactGroupImpl</tt> that we don't want considered as a parent of this
     * contact any more.
     */
    void unsetParentGroup(MetaContactGroupImpl parentGrp)
    {
        synchronized (getParentGroupModLock()) {
            if (parentGroup == parentGrp)
                parentGroup = null;
        }
    }

    /**
     * Returns the group that is currently holding this meta contact.
     *
     * @return the group that is currently holding this meta contact.
     */
    MetaContactGroupImpl getParentGroup()
    {
        return parentGroup;
    }

    /**
     * Returns the MetaContactGroup currently containing this meta contact
     *
     * @return a reference to the MetaContactGroup currently containing this meta contact.
     */
    public MetaContactGroup getParentMetaContactGroup()
    {
        return getParentGroup();
    }

    /**
     * Adds a custom detail to this contact.
     *
     * @param name name of the detail.
     * @param value the value of the detail.
     */
    public void addDetail(String name, String value)
    {
        try {
            JSONArray jsonArray = (JSONArray) details.get(name);
            if (jsonArray == null) {
                jsonArray = new JSONArray();
            }
            jsonArray.put(value);
            details.put(name, jsonArray);
            fireMetaContactModified(name, null, value);
        } catch (JSONException e) {
            e.printStackTrace();
        }
    }

    /**
     * Remove the given detail.
     *
     * @param name of the detail to be removed.
     * @param value value of the detail to be removed.
     */
    public void removeDetail(String name, String value)
    {
        try {
            JSONArray jsonArray = (JSONArray) details.get(name);
            if ((jsonArray != null) && (jsonArray.length() != 0)) {
                for (int i = 0; i < jsonArray.length(); i++) {
                    if (value.equals(jsonArray.getString(i))) {
                        jsonArray.remove(i);
                        fireMetaContactModified(name, value, null);
                        break;
                    }
                }
            }
        } catch (JSONException e) {
            e.printStackTrace();
        }
    }

    /**
     * Remove all details with given name.
     *
     * @param name of the details to be removed.
     */
    public void removeDetails(String name)
    {
        Object itemRemoved = details.remove(name);
        if (itemRemoved != null)
            fireMetaContactModified(name, itemRemoved, null);
    }

    /**
     * Change the detail.
     *
     * @param name of the detail to be changed.
     * @param oldValue the old value of the detail.
     * @param newValue the new value of the detail.
     */
    public void changeDetail(String name, String oldValue, String newValue)
    {
        try {
            JSONArray jsonArray = (JSONArray) details.get(name);
            if (jsonArray == null)
                return;

            for (int i = 0; i < jsonArray.length(); i++) {
                if (oldValue.equals(jsonArray.getString(i))) {
                    jsonArray.put(i, newValue);
                    fireMetaContactModified(name, oldValue, newValue);
                    break;
                }
            }
        } catch (JSONException e) {
            e.printStackTrace();
        }
    }

    /**
     * Gets the JSONObject details.
     *
     * @return the JSONObject which represent the details of the metaContactImpl
     */
    public JSONObject getDetails()
    {
        return details;
    }

    /**
     * Gets all details with a given name.
     *
     * @param name the name of the details we are searching for
     * @return a JSONArray which represent the details with the specified
     * <tt>name</tt>
     */
    public JSONArray getDetails(String name)
    {
        JSONArray jsonArray = new JSONArray();
        try {
            jsonArray = (JSONArray) details.get(name);
        } catch (JSONException e) {
            e.printStackTrace();
        }
        return jsonArray;
    }

    /**
     * Fires a new <tt>MetaContactModifiedEvent</tt> which is to notify about a modification with
     * a specific name of this <tt>MetaContact</tt> which has caused a property value change from a specific
     * <tt>oldValue</tt> to a specific <tt>newValue</tt>.
     *
     * @param modificationName the name of the modification which has caused a new <tt>MetaContactModifiedEvent</tt>
     * to be fired
     * @param oldValue the value of the property before the modification
     * @param newValue the value of the property after the modification
     */
    private void fireMetaContactModified(String modificationName, Object oldValue,
            Object newValue)
    {
        MetaContactGroupImpl parentGroup = getParentGroup();
        if (parentGroup != null)
            parentGroup.getMclServiceImpl().fireMetaContactEvent(
                    new MetaContactModifiedEvent(this, modificationName, oldValue, newValue));
    }

    /**
     * Stores avatar bytes in the given <tt>Contact</tt>.
     *
     * @param protoContact The contact in which we store the avatar.
     * @param avatarBytes The avatar image bytes.
     */
    public void cacheAvatar(Contact protoContact, byte[] avatarBytes)
    {
        this.mCachedAvatar = avatarBytes;
        this.avatarFileCacheAlreadyQueried = true;

        // AvatarCacheUtils.cacheAvatar(protoContact, avatarBytes);
        BareJid userId = protoContact.getJid().asBareJid();
        AvatarManager.addAvatarImage(userId, avatarBytes, false);
    }

    /**
     * Updates the capabilities for the given contact.
     *
     * @param contact the <tt>Contact</tt>, which capabilities have changed
     * @param opSets the new updated set of operation sets
     */
    public void updateCapabilities(Contact contact, Jid jid, Map<String, ? extends OperationSet> opSets)
    {
        OperationSetContactCapabilities capOpSet
                = contact.getProtocolProvider().getOperationSet(OperationSetContactCapabilities.class);

        // This should not happen, because this method is called explicitly for
        // events coming from the capabilities operation set.
        if (capOpSet == null)
            return;

        // Update based on contact only (not considering the contact resource)
        removeCapabilities(contact, opSets);
        addCapabilities(contact, opSets);

        // Update based on FullJid
        removeCapabilities(jid, opSets);
        addCapabilities(jid, opSets);
    }

    /**
     * Remove capabilities for the given contacts.
     *
     * @param contact the <tt>Contact</tt>, which capabilities we remove
     * @param opSets the new updated set of operation sets
     */
    private void removeCapabilities(Contact contact, Map<String, ? extends OperationSet> opSets)
    {
        synchronized (capabilities) {
            Iterator<Map.Entry<String, List<Contact>>> caps = capabilities.entrySet().iterator();
            Set<String> contactNewCaps = opSets.keySet();

            while (caps.hasNext()) {
                Map.Entry<String, List<Contact>> entry = caps.next();

                String opSetName = entry.getKey();
                List<Contact> contactsForCap = entry.getValue();

                if (contactsForCap.contains(contact) && !contactNewCaps.contains(opSetName)) {
                    contactsForCap.remove(contact);

                    if (contactsForCap.size() == 0)
                        caps.remove();
                }
            }
        }
    }

    /**
     * Adds the capabilities of the given contact.
     *
     * @param contact the <tt>Contact</tt>, which capabilities we add
     * @param opSets the map of operation sets supported by the contact
     */
    private void addCapabilities(Contact contact, Map<String, ? extends OperationSet> opSets)
    {
        synchronized (capabilities) {
            for (String newCap : opSets.keySet()) {
                List<Contact> capContacts;
                if (!capabilities.containsKey(newCap)) {
                    capContacts = new LinkedList<>();
                    capContacts.add(contact);
                    capabilities.put(newCap, capContacts);
                }
                else {
                    capContacts = capabilities.get(newCap);
                    if ((capContacts != null) && !capContacts.contains(contact)) {
                        capContacts.add(contact);
                    }
                }
            }
        }
    }

    /**
     * Remove capabilities for the given contacts based on FullJid
     *
     * @param jid the FullJid of the <tt>Contact</tt>, whom capabilities we remove. Null applies to all resources
     * @param opSets the new updated set of operation sets.
     */
    private void removeCapabilities(Jid jid, Map<String, ? extends OperationSet> opSets)
    {
        Timber.d("Opset capability removal started: %s", jid);
        synchronized (capabilityJid) {
            Iterator<Map.Entry<String, List<Jid>>> capJids = capabilityJid.entrySet().iterator();
            Set<String> contactNewCaps = opSets.keySet();

            while (capJids.hasNext()) {
                Map.Entry<String, List<Jid>> entryJid = capJids.next();
                String opSetName = entryJid.getKey();

                Iterator<Jid> jidsForCap = entryJid.getValue().iterator();
                while (jidsForCap.hasNext()) {
                    Jid jidx = jidsForCap.next();
                    if (jid.equals(jidx) && !contactNewCaps.contains(opSetName)) {
                        jidsForCap.remove();
                        if (!jidsForCap.hasNext()) {
                            capJids.remove();
                            break;
                        }
                    }
                }
            }
        }
    }

    /**
     * Adds the capabilities of the given contact based on FullJid
     *
     * @param jid the FullJid of the <tt>Contact</tt>, whom capabilities we remove. Null applies to all resources
     * @param opSets the map of operation sets supported by the contact.
     */
    private void addCapabilities(Jid jid, Map<String, ? extends OperationSet> opSets)
    {
        Timber.d("Opset capability adding started: %s", jid);
        synchronized (capabilityJid) {
            for (String newCap : opSets.keySet()) {
                List<Jid> capJids;

                if (!capabilityJid.containsKey(newCap)) {
                    capJids = new LinkedList<>();
                    capJids.add(jid);
                    capabilityJid.put(newCap, capJids);
                }
                else {
                    capJids = capabilityJid.get(newCap);
                    if ((capJids != null) && !capJids.contains(jid)) {
                        capJids.add(jid);
                    }
                }
            }
        }
    }

    /**
     * Gets the sync lock for use when modifying {@link #parentGroup}.
     *
     * @return the sync lock for use when modifying {@link #parentGroup}
     */
    private Object getParentGroupModLock()
    {
        /*
         * XXX The use of uid as parentGroupModLock is a bit unusual but a dedicated lock enlarges
         * the shallow runtime size of this instance and having hundreds of MetaContactImpl
         * instances is not unusual for a multi-protocol application. With respect to
         * parentGroupModLock being unique among the MetaContactImpl instances, uid is fine
         * because it is also supposed to be unique in the same way.
         */
        return uid;
    }
}
