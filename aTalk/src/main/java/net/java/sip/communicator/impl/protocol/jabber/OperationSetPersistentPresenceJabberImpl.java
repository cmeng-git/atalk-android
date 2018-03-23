/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 * 
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package net.java.sip.communicator.impl.protocol.jabber;

import net.java.sip.communicator.service.protocol.AbstractOperationSetPersistentPresence;
import net.java.sip.communicator.service.protocol.AuthorizationHandler;
import net.java.sip.communicator.service.protocol.AuthorizationRequest;
import net.java.sip.communicator.service.protocol.AuthorizationResponse;
import net.java.sip.communicator.service.protocol.ChatRoom;
import net.java.sip.communicator.service.protocol.Contact;
import net.java.sip.communicator.service.protocol.ContactGroup;
import net.java.sip.communicator.service.protocol.ContactResource;
import net.java.sip.communicator.service.protocol.OperationFailedException;
import net.java.sip.communicator.service.protocol.OperationSetMultiUserChat;
import net.java.sip.communicator.service.protocol.OperationSetServerStoredAccountInfo;
import net.java.sip.communicator.service.protocol.PresenceStatus;
import net.java.sip.communicator.service.protocol.ProtocolProviderFactory;
import net.java.sip.communicator.service.protocol.RegistrationState;
import net.java.sip.communicator.service.protocol.ServerStoredDetails.GenericDetail;
import net.java.sip.communicator.service.protocol.ServerStoredDetails.ImageDetail;
import net.java.sip.communicator.service.protocol.event.ContactPropertyChangeEvent;
import net.java.sip.communicator.service.protocol.event.ContactResourceEvent;
import net.java.sip.communicator.service.protocol.event.RegistrationStateChangeEvent;
import net.java.sip.communicator.service.protocol.event.RegistrationStateChangeListener;
import net.java.sip.communicator.service.protocol.event.ServerStoredGroupListener;
import net.java.sip.communicator.service.protocol.jabberconstants.JabberStatusEnum;
import net.java.sip.communicator.util.Logger;

import org.atalk.util.StringUtils;
import org.jivesoftware.smack.SmackException.NoResponseException;
import org.jivesoftware.smack.SmackException.NotConnectedException;
import org.jivesoftware.smack.StanzaListener;
import org.jivesoftware.smack.XMPPConnection;
import org.jivesoftware.smack.XMPPException.XMPPErrorException;
import org.jivesoftware.smack.filter.StanzaFilter;
import org.jivesoftware.smack.filter.StanzaTypeFilter;
import org.jivesoftware.smack.packet.Presence;
import org.jivesoftware.smack.packet.Stanza;
import org.jivesoftware.smack.packet.XMPPError;
import org.jivesoftware.smack.roster.Roster;
import org.jivesoftware.smack.roster.RosterEntry;
import org.jivesoftware.smack.roster.RosterListener;
import org.jivesoftware.smack.roster.RosterLoadedListener;
import org.jivesoftware.smackx.avatar.AvatarManager;
import org.jivesoftware.smackx.avatar.useravatar.UserAvatarManager;
import org.jivesoftware.smackx.avatar.useravatar.listener.UserAvatarListener;
import org.jivesoftware.smackx.avatar.useravatar.packet.AvatarMetadata;
import org.jivesoftware.smackx.avatar.vcardavatar.VCardAvatarManager;
import org.jivesoftware.smackx.avatar.vcardavatar.listener.VCardAvatarListener;
import org.jivesoftware.smackx.nick.packet.Nick;
import org.jivesoftware.smackx.vcardtemp.packet.VCard;
import org.jxmpp.jid.BareJid;
import org.jxmpp.jid.EntityBareJid;
import org.jxmpp.jid.Jid;
import org.jxmpp.jid.impl.JidCreate;
import org.jxmpp.jid.parts.Resourcepart;
import org.jxmpp.stringprep.XmppStringprepException;
import org.jxmpp.util.XmppStringUtils;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Hashtable;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/**
 * The Jabber implementation of a Persistent Presence Operation set. This class manages our own
 * presence status as well as subscriptions for the presence status of our buddies. It also offers
 * methods for retrieving and modifying the buddy contact list and adding listeners for changes in
 * its layout.
 *
 * @author Damian Minkov
 * @author Lyubomir Marinov
 * @author Hristo Terezov
 * @author Eng Chong Meng
 */
public class OperationSetPersistentPresenceJabberImpl
        extends AbstractOperationSetPersistentPresence<ProtocolProviderServiceJabberImpl>
        implements VCardAvatarListener, UserAvatarListener
{
    /**
     * The logger.
     */
    private static final Logger logger = Logger.getLogger(OperationSetPersistentPresenceJabberImpl.class);

    /**
     * Contains our current status message. Note that this field would only be changed once the
     * server has confirmed the new status message and not immediately upon setting a new one..
     */
    private String currentStatusMessage = "";

    /**
     * The presence status that we were last notified of entering. The initial one is OFFLINE
     */
    private PresenceStatus currentStatus;

    /**
     * <tt>true</tt> update both account and contacts status. set to <tt>false</tt> when the
     * session is resumed to leave contacts' status untouched.
     */
    private boolean updateAllStatus = true;

    /**
     * A map containing bindings between aTalk's jabber presence status instances and Jabber status codes
     */
    private static Map<String, Presence.Mode> scToJabberModesMappings = new Hashtable<>();

    static {
        scToJabberModesMappings.put(JabberStatusEnum.AWAY, Presence.Mode.away);
        scToJabberModesMappings.put(JabberStatusEnum.ON_THE_PHONE, Presence.Mode.away);
        scToJabberModesMappings.put(JabberStatusEnum.IN_A_MEETING, Presence.Mode.away);
        scToJabberModesMappings.put(JabberStatusEnum.EXTENDED_AWAY, Presence.Mode.xa);
        scToJabberModesMappings.put(JabberStatusEnum.DO_NOT_DISTURB, Presence.Mode.dnd);
        scToJabberModesMappings.put(JabberStatusEnum.FREE_FOR_CHAT, Presence.Mode.chat);
        scToJabberModesMappings.put(JabberStatusEnum.AVAILABLE, Presence.Mode.available);
    }

    /**
     * A map containing bindings between aTalk's xmpp presence status instances and priorities to
     * use for statuses.
     */
    private static Map<String, Integer> statusToPriorityMappings = new Hashtable<>();

    /**
     * The server stored contact list that will be encapsulating smack's buddy list.
     */
    private final ServerStoredContactListJabberImpl ssContactList;

    /**
     * Listens for subscriptions.
     */
    private JabberSubscriptionListener subscriptionPacketListener = null;

    /**
     * Current resource priority.
     */
    private int resourcePriorityAvailable = 30;

    /**
     * Manages statuses and different user resources.
     */
    private ContactChangesListener contactChangesListener = null;

    /**
     * Manages the presence extension to advertise the SHA-1 hash of this account avatar as
     * defined in XEP-0153. It also provide persistence storage of the received avatar
     */
    private VCardAvatarManager vCardAvatarManager = null;

    /**
     * Manages the event extension to advertise the SHA-1 hash of this account avatar as
     * defined in XEP-0084.
     */
    private UserAvatarManager userAvatarManager = null;

    /**
     * Handles all the logic about mobile indicator for contacts.
     */
    private final MobileIndicator mobileIndicator;

    /**
     * The last sent presence to server, contains the status, the resource and its priority.
     */
    private Presence currentPresence = null;

    /**
     * The local contact presented by the provider.
     */
    private ContactJabberImpl localContact = null;

    /**
     * Handles and retrieves all info of our contacts or account info from the downloaded vcard.
     *
     * @see InfoRetriever#retrieveDetails(BareJid contactAddress)
     */
    private InfoRetriever mInfoRetriever;

    /**
     * Creates the OperationSet.
     *
     * @param provider the parent provider.
     * @param infoRetriever retrieve contact information.
     */
    public OperationSetPersistentPresenceJabberImpl(ProtocolProviderServiceJabberImpl provider, InfoRetriever infoRetriever)
    {
        super(provider);
        currentStatus = parentProvider.getJabberStatusEnum().getStatus(JabberStatusEnum.OFFLINE);
        initializePriorities();
        mInfoRetriever = infoRetriever;
        ssContactList = new ServerStoredContactListJabberImpl(this, provider, infoRetriever);
        parentProvider.addRegistrationStateChangeListener(new RegistrationStateListener());
        mobileIndicator = new MobileIndicator(parentProvider, ssContactList);
    }

    /**
     * Registers a listener that would receive events upon changes in server stored groups.
     *
     * @param listener a ServerStoredGroupChangeListener impl that would receive events upon group changes.
     */
    @Override
    public void addServerStoredGroupChangeListener(ServerStoredGroupListener listener)
    {
        ssContactList.addGroupListener(listener);
    }

    /**
     * Creates a group with the specified name and parent in the server stored contact list.
     *
     * @param parent the group where the new group should be created
     * @param groupName the name of the new group to create.
     * @throws OperationFailedException if such group already exists
     */
    public void createServerStoredContactGroup(ContactGroup parent, String groupName)
            throws OperationFailedException
    {
        assertConnected();

        if (!parent.canContainSubgroups())
            throw new IllegalArgumentException("The specified contact group cannot contain child groups. Group:" + parent);

        ssContactList.createGroup(groupName);
    }

    /**
     * Creates a non persistent contact for the specified address. This would also create (if
     * necessary) a group for volatile contacts that would not be added to the server stored
     * contact list. The volatile contact would remain in the list until it is really added to
     * the contact list or until the application is terminated.
     *
     * @param id the address of the contact to create.
     * @return the newly created volatile <tt>ContactImpl</tt>
     */
    public synchronized ContactJabberImpl createVolatileContact(String id)
    {
        return createVolatileContact(id, false);
    }

    /**
     * Creates a non persistent contact for the specified address. This would also create (if
     * necessary) a group for volatile contacts that would not be added to the server stored
     * contact list. The volatile contact would remain in the list until it is really added to
     * the contact list or until the application is terminated.
     *
     * @param id the address of the contact to create.
     * @param displayName the display name of the contact.
     * @return the newly created volatile <tt>ContactImpl</tt>
     */
    public synchronized ContactJabberImpl createVolatileContact(String id, String displayName)
    {
        return createVolatileContact(id, false, displayName);
    }

    /**
     * Creates a non persistent contact for the specified address. This would also create (if
     * necessary) a group for volatile contacts that would not be added to the server stored
     * contact list. The volatile contact would remain in the list until it is really added to
     * the contact list or until the application is terminated.
     *
     * @param id the address of the contact to create.
     * @param isPrivateMessagingContact indicates whether the contact should be private messaging contact or not.
     * @return the newly created volatile <tt>ContactImpl</tt>
     */
    public synchronized ContactJabberImpl createVolatileContact(String id, boolean isPrivateMessagingContact)
    {
        return createVolatileContact(id, isPrivateMessagingContact, null);
    }

    /**
     * Creates a non persistent contact for the specified address. This would also create (if
     * necessary) a group for volatile contacts that would not be added to the server stored
     * contact list. The volatile contact would remain in the list until it is really added to
     * the contact list or until the application is terminated.
     *
     * @param id the address of the contact to create.
     * @param isPrivateMessagingContact indicates whether the contact should be private messaging contact or not.
     * // @param displayName
     * the display name of the contact.
     * @return the newly created volatile <tt>ContactImpl</tt>
     */
    public synchronized ContactJabberImpl createVolatileContact(String id,
            boolean isPrivateMessagingContact, String displayName)
    {
        // first check for already created one.
        ContactGroupJabberImpl notInContactListGroup = ssContactList.getNonPersistentGroup();
        ContactJabberImpl sourceContact;
        if ((notInContactListGroup != null)
                && (sourceContact = notInContactListGroup.findContact(XmppStringUtils.parseBareJid(id))) != null)
            return sourceContact;
        else {
            sourceContact = ssContactList.createVolatileContact(id, isPrivateMessagingContact, displayName);
            if (isPrivateMessagingContact && XmppStringUtils.parseResource(id) != null) {
                updateResources(sourceContact, false);
            }
            return sourceContact;
        }
    }

    /**
     * Creates and returns a unresolved contact from the specified <tt>address</tt> and
     * <tt>persistentData</tt>.
     *
     * @param address an identifier of the contact that we'll be creating.
     * @param persistentData a String returned Contact's getPersistentData() method during a previous run and that
     * has been persistently stored locally.
     * @param parentGroup the group where the unresolved contact is supposed to belong to.
     * @return the unresolved <tt>Contact</tt> created from the specified <tt>address</tt> and
     * <tt>persistentData</tt>
     */
    public Contact createUnresolvedContact(String address, String persistentData, ContactGroup parentGroup)
    {
        if (!(parentGroup instanceof ContactGroupJabberImpl
                || parentGroup instanceof RootContactGroupJabberImpl))
            throw new IllegalArgumentException("Argument is not an jabber contact group (group = " + parentGroup + ")");

        ContactJabberImpl contact = ssContactList.createUnresolvedContact(parentGroup, address);
        contact.setPersistentData(persistentData);
        return contact;
    }

    /**
     * Creates and returns a unresolved contact from the specified <tt>address</tt> and
     * <tt>persistentData</tt>.
     *
     * @param address an identifier of the contact that we'll be creating.
     * @param persistentData a String returned Contact's getPersistentData() method during a previous run and that
     * has been persistently stored locally.
     * @return the unresolved <tt>Contact</tt> created from the specified <tt>address</tt> and
     * <tt>persistentData</tt>
     */
    public Contact createUnresolvedContact(String address, String persistentData)
    {
        return createUnresolvedContact(address, persistentData, getServerStoredContactListRoot());
    }

    /**
     * Creates and returns a unresolved contact group from the specified <tt>address</tt> and
     * <tt>persistentData</tt>.
     *
     * @param groupUID an identifier, returned by ContactGroup's getGroupUID, that the protocol provider may
     * use in order to create the group.
     * @param persistentData a String returned ContactGroups's getPersistentData() method during a previous run and
     * that has been persistently stored locally.
     * @param parentGroup the group under which the new group is to be created or null if this is group directly
     * underneath the root.
     * @return the unresolved <tt>ContactGroup</tt> created from the specified <tt>uid</tt> and
     * <tt>persistentData</tt>
     */
    public ContactGroup createUnresolvedContactGroup(String groupUID, String persistentData, ContactGroup parentGroup)
    {
        return ssContactList.createUnresolvedContactGroup(groupUID);
    }

    /**
     * Returns a reference to the contact with the specified ID in case we have a subscription for
     * it and null otherwise/
     *
     * @param contactID a String identifier of the contact which we're seeking a reference of.
     * @return a reference to the Contact with the specified <tt>contactID</tt> or null if we don't
     * have a subscription for the that identifier.
     */
    public Contact findContactByID(String contactID)
    {
        return ssContactList.findContactById(contactID);
    }

    /**
     * Returns the status message that was confirmed by the server
     *
     * @return the last status message that we have requested and the aim server has confirmed.
     */
    public String getCurrentStatusMessage()
    {
        return currentStatusMessage;
    }

    /**
     * Returns the protocol specific contact instance representing the local user.
     *
     * @return the Contact (address, phone number, or uid) that the Provider implementation is
     * communicating on behalf of.
     */
    public Contact getLocalContact()
    {
        if (localContact != null)
            return localContact;

        final Jid jid = parentProvider.getOurJID();

        localContact = new ContactJabberImpl(null, ssContactList, false, true);
        localContact.setLocal(true);
        localContact.updatePresenceStatus(currentStatus);
        localContact.setJid(jid);

        Map<Jid, ContactResourceJabberImpl> rs = localContact.getResourcesMap();
        if (currentPresence != null)
            rs.put(jid, createResource(currentPresence, parentProvider.getOurJID(), localContact));

        List<Presence> presenceIterator = ssContactList.getPresences(jid.asBareJid());
        for (Presence p : presenceIterator) {
            Jid fullJid = p.getFrom();
            rs.put(fullJid, createResource(p, p.getFrom(), localContact));
        }

        // adds xmpp listener for changes in the local contact resources
        StanzaFilter presenceFilter = new StanzaTypeFilter(Presence.class);
        parentProvider.getConnection().addAsyncStanzaListener(new StanzaListener()
        {
            @Override
            public void processStanza(Stanza stanza)
            {
                Presence presence = (Presence) stanza;
                Jid from = presence.getFrom();

                if ((from == null) || !from.isParentOf(jid))
                    return;

                // own resource update, let's process it
                updateResource(localContact, null, presence);
            }
        }, presenceFilter);

        return localContact;
    }

    /**
     * Creates ContactResource from the presence, full jid and contact.
     *
     * @param presence the presence object.
     * @param fullJid the full jid for the resource.
     * @param contact the contact.
     * @return the newly created resource.
     */
    private ContactResourceJabberImpl createResource(Presence presence, Jid fullJid, Contact contact)
    {
        String resource = fullJid.getResourceOrNull().toString();

        return new ContactResourceJabberImpl(fullJid, contact, resource,
                jabberStatusToPresenceStatus(presence, parentProvider), presence.getPriority(),
                mobileIndicator.isMobileResource(resource, fullJid));
    }

    /**
     * Clear resources used for local contact and before that update its resources in order to fire
     * the needed events.
     */
    private void clearLocalContactResources()
    {
        if (localContact != null)
            try {
                removeResource(localContact, JidCreate.fullFrom(localContact.getAddress()));
            } catch (XmppStringprepException e) {
                e.printStackTrace();
            }

        currentPresence = null;
        localContact = null;
    }

    /**
     * Returns a PresenceStatus instance representing the state this provider is currently in.
     *
     * @return the PresenceStatus last published by this provider.
     */
    public PresenceStatus getPresenceStatus()
    {
        return currentStatus;
    }

    /**
     * Returns the root group of the server stored contact list.
     *
     * @return the root ContactGroup for the ContactList stored by this service.
     */
    public ContactGroup getServerStoredContactListRoot()
    {
        return ssContactList.getRootGroup();
    }

    /**
     * Returns the set of PresenceStatus objects that a user of this service may request the
     * provider to enter.
     *
     * @return Iterator a PresenceStatus array containing "selectable" status instances.
     */
    public Iterator<PresenceStatus> getSupportedStatusSet()
    {
        return parentProvider.getJabberStatusEnum().getSupportedStatusSet();
    }

    /**
     * Checks if the contact address is associated with private messaging contact or not.
     *
     * @param contactAddress the address of the contact.
     * @return <tt>true</tt> the contact address is associated with private messaging contact and
     * <tt>false</tt> if not.
     */
    public boolean isPrivateMessagingContact(String contactAddress)
    {
        return ssContactList.isPrivateMessagingContact(contactAddress);
    }

    /**
     * Removes the specified contact from its current parent and places it under
     * <tt>newParent</tt>.
     *
     * @param contactToMove the <tt>Contact</tt> to move
     * @param newParent the <tt>ContactGroup</tt> where <tt>Contact</tt> would be placed.
     */
    public void moveContactToGroup(Contact contactToMove, ContactGroup newParent)
            throws OperationFailedException
    {
        assertConnected();
        if (!(contactToMove instanceof ContactJabberImpl))
            throw new IllegalArgumentException("The specified contact is not an jabber contact. " + contactToMove);
        if (!(newParent instanceof AbstractContactGroupJabberImpl))
            throw new IllegalArgumentException("The specified group is not an jabber contact group. " + newParent);

        ssContactList.moveContact((ContactJabberImpl) contactToMove, (AbstractContactGroupJabberImpl) newParent);
    }

    /**
     * Publish the provider has entered into a state corresponding to the specified parameters.
     *
     * @param status the PresenceStatus as returned by getSupportedStatusSet
     * @param statusMessage the message that should be set as the reason to enter that status
     * @throws IllegalArgumentException if the status requested is not a valid PresenceStatus supported by this provider.
     * @throws IllegalStateException if the provider is not currently registered.
     * @throws OperationFailedException with code NETWORK_FAILURE if publishing the status fails due to a network error.
     */
    public void publishPresenceStatus(PresenceStatus status, String statusMessage)
            throws IllegalArgumentException, IllegalStateException, OperationFailedException
    {
        assertConnected();
        JabberStatusEnum jabberStatusEnum = parentProvider.getJabberStatusEnum();
        boolean isValidStatus = false;
        for (Iterator<PresenceStatus> supportedStatus = jabberStatusEnum.getSupportedStatusSet();
             supportedStatus.hasNext(); ) {
            if (supportedStatus.next().equals(status)) {
                isValidStatus = true;
                break;
            }
        }
        if (!isValidStatus)
            throw new IllegalArgumentException(status + " is not a valid Jabber status");

        // if we got publish presence and we are still in a process of initializing the roster,
        // just save the status and we will dispatch it when we are ready with the roster as
        // sending initial presence is recommended to be done after requesting the roster, but we
        // want to also dispatch it
        synchronized (ssContactList.getRosterInitLock()) {
            if (!ssContactList.isRosterInitialized()) {
                // store it
                ssContactList.setInitialStatus(status);
                ssContactList.setInitialStatusMessage(statusMessage);
                logger.info("Smack: In roster fetching-hold <presence:available/>");
                return;
            }
        }
        if (status.equals(jabberStatusEnum.getStatus(JabberStatusEnum.OFFLINE))) {
            parentProvider.unregister();
            clearLocalContactResources();
        }
        else {
            Presence presence = new Presence(Presence.Type.available);
            currentPresence = presence;
            presence.setMode(presenceStatusToJabberMode(status));
            presence.setPriority(getPriorityForPresenceStatus(status.getStatusName()));

            // On the phone or in meeting has a special status which is different from custom
            // status message
            if (status.equals(jabberStatusEnum.getStatus(JabberStatusEnum.ON_THE_PHONE))) {
                presence.setStatus(JabberStatusEnum.ON_THE_PHONE);
            }
            else if (status.equals(jabberStatusEnum.getStatus(JabberStatusEnum.IN_A_MEETING))) {
                presence.setStatus(JabberStatusEnum.IN_A_MEETING);
            }
            else
                presence.setStatus(statusMessage);

            try {
                logger.info("Smack: sending <presence:available/>");
                parentProvider.getConnection().sendStanza(presence);
            } catch (NotConnectedException | InterruptedException e) {
                e.printStackTrace();
            }
            if (localContact != null)
                updateResource(localContact, parentProvider.getOurJID(), presence);
        }
        fireProviderStatusChangeEvent(currentStatus, status);

        // Use StringUtils.isEquals instead of String.equals to avoid a NullPointerException.
        String oldStatusMessage = getCurrentStatusMessage();
        if (!StringUtils.isEquals(oldStatusMessage, statusMessage)) {
            currentStatusMessage = statusMessage;
            fireProviderStatusMessageChangeEvent(oldStatusMessage, getCurrentStatusMessage());
        }
    }

    /**
     * Gets the <tt>PresenceStatus</tt> of a contact with a specific <tt>String</tt> identifier.
     *
     * @param contactIdentifier the identifier of the contact whose status we're interested in.
     * @return the <tt>PresenceStatus</tt> of the contact with the specified
     * <tt>contactIdentifier</tt>
     * @throws IllegalArgumentException if the specified <tt>contactIdentifier</tt> does not identify a contact known to the
     * underlying protocol provider
     * @throws IllegalStateException if the underlying protocol provider is not registered/signed on a public service
     * @throws OperationFailedException with code NETWORK_FAILURE if retrieving the status fails due to errors experienced
     * during network communication
     */
    public PresenceStatus queryContactStatus(BareJid contactIdentifier)
            throws IllegalArgumentException, IllegalStateException, OperationFailedException
    {
        /*
		 * As stated by the javadoc, IllegalStateException signals that the ProtocolProviderService
		 * is not registered.
		 */
        assertConnected();
        XMPPConnection xmppConnection = parentProvider.getConnection();
        if (xmppConnection == null) {
            throw new IllegalArgumentException("The provider/account must be signed on in order"
                    + " to query the status of a contact in its roster");
        }

        Roster roster = Roster.getInstanceFor(xmppConnection);
        Presence presence = roster.getPresence(contactIdentifier);
        if (presence != null)
            return jabberStatusToPresenceStatus(presence, parentProvider);
        else {
            return parentProvider.getJabberStatusEnum().getStatus(JabberStatusEnum.OFFLINE);
        }
    }

    /**
     * Removes the specified group from the server stored contact list.
     *
     * @param group the group to remove.
     */
    public void removeServerStoredContactGroup(ContactGroup group)
            throws OperationFailedException
    {
        assertConnected();
        if (!(group instanceof ContactGroupJabberImpl))
            throw new IllegalArgumentException("The specified group is not an jabber contact group: " + group);

        ssContactList.removeGroup(((ContactGroupJabberImpl) group));
    }

    /**
     * Removes the specified group change listener so that it won't receive any further events.
     *
     * @param listener the ServerStoredGroupChangeListener to remove
     */
    @Override
    public void removeServerStoredGroupChangeListener(ServerStoredGroupListener listener)
    {
        ssContactList.removeGroupListener(listener);
    }

    /**
     * Renames the specified group from the server stored contact list.
     *
     * @param group the group to rename.
     * @param newName the new name of the group.
     */
    public void renameServerStoredContactGroup(ContactGroup group, String newName)
    {
        assertConnected();

        if (!(group instanceof ContactGroupJabberImpl))
            throw new IllegalArgumentException("The specified group is not an jabber contact group: " + group);
        ssContactList.renameGroup((ContactGroupJabberImpl) group, newName);
    }

    /**
     * Handler for incoming authorization requests.
     *
     * @param handler an instance of an AuthorizationHandler for authorization requests coming from other
     * users requesting permission add us to their contact list.
     */
    public void setAuthorizationHandler(AuthorizationHandler handler)
    {
        // subscriptionPacketListener is null when authenticated via ReconnectionManager, just
        // ignore as handler should have been setup during normal authentication
        if (subscriptionPacketListener != null)
            subscriptionPacketListener.setHandler(handler);
    }

    /**
     * Persistently adds a subscription for the presence status of the contact corresponding to the
     * specified contactIdentifier and indicates that it should be added to the specified group of
     * the server stored contact list.
     *
     * @param parent the parent group of the server stored contact list where the contact should be added.
     * <p>
     * @param contactIdentifier the contact whose status updates we are subscribing for.
     * @throws IllegalArgumentException if <tt>contact</tt> or <tt>parent</tt> are not a contact known to the underlying
     * protocol provider.
     * @throws IllegalStateException if the underlying protocol provider is not registered/signed on a public service.
     * @throws OperationFailedException with code NETWORK_FAILURE if subscribing fails due to errors experienced during
     * network communication
     */
    public void subscribe(ContactGroup parent, String contactIdentifier)
            throws IllegalArgumentException, IllegalStateException, OperationFailedException, XmppStringprepException
    {
        assertConnected();
        if (!(parent instanceof ContactGroupJabberImpl))
            throw new IllegalArgumentException("Argument is not an jabber contact group (group = " + parent + ")");

        ssContactList.addContact(parent, contactIdentifier);
    }

    /**
     * Adds a subscription for the presence status of the contact corresponding to the specified
     * contactIdentifier.
     *
     * @param contactIdentifier the identifier of the contact whose status updates we are subscribing for.
     * <p>
     * @throws IllegalArgumentException if <tt>contact</tt> is not a contact known to the underlying protocol provider
     * @throws IllegalStateException if the underlying protocol provider is not registered/signed on a public service.
     * @throws OperationFailedException with code NETWORK_FAILURE if subscribing fails due to errors experienced during
     * network communication
     */
    public void subscribe(String contactIdentifier)
            throws IllegalArgumentException, IllegalStateException, OperationFailedException, XmppStringprepException
    {
        assertConnected();
        ssContactList.addContact(contactIdentifier);
    }

    /**
     * Removes a subscription for the presence status of the specified contact.
     *
     * @param contact the contact whose status updates we are unsubscribing from.
     * @throws IllegalArgumentException if <tt>contact</tt> is not a contact known to the underlying protocol provider
     * @throws IllegalStateException if the underlying protocol provider is not registered/signed on a public service.
     * @throws OperationFailedException with code NETWORK_FAILURE if unSubscribing fails due to errors experienced during
     * network communication
     */
    public void unsubscribe(Contact contact)
            throws IllegalArgumentException, IllegalStateException, OperationFailedException
    {
        assertConnected();
        if (!(contact instanceof ContactJabberImpl))
            throw new IllegalArgumentException("Argument is not an jabber contact (contact = " + contact + ")");

        ssContactList.removeContact((ContactJabberImpl) contact);
    }

    /**
     * Converts the specified jabber status to one of the status fields of the JabberStatusEnum
     * class.
     *
     * @param presence the Jabber Status
     * @param jabberProvider the parent provider.
     * @return a PresenceStatus instance representation of the Jabber Status parameter. The
     * returned result is one of the JabberStatusEnum fields.
     */
    public static PresenceStatus jabberStatusToPresenceStatus(Presence presence,
            ProtocolProviderServiceJabberImpl jabberProvider)
    {
        JabberStatusEnum jabberStatusEnum = jabberProvider.getJabberStatusEnum();
        if (presence.getType() == Presence.Type.unavailable)
            return jabberStatusEnum.getStatus(JabberStatusEnum.OFFLINE);

            // Check status mode when user is available
        else if (presence.isAvailable()) {
            Presence.Mode mode = presence.getMode();

            if (mode == Presence.Mode.available)
                return jabberStatusEnum.getStatus(JabberStatusEnum.AVAILABLE);
            else if (mode == Presence.Mode.away) {
                // on the phone a special status which is away with custom status message
                if (presence.getStatus() != null
                        && presence.getStatus().contains(JabberStatusEnum.ON_THE_PHONE))
                    return jabberStatusEnum.getStatus(JabberStatusEnum.ON_THE_PHONE);
                else if (presence.getStatus() != null
                        && presence.getStatus().contains(JabberStatusEnum.IN_A_MEETING))
                    return jabberStatusEnum.getStatus(JabberStatusEnum.IN_A_MEETING);
                else
                    return jabberStatusEnum.getStatus(JabberStatusEnum.AWAY);
            }
            else if (mode == Presence.Mode.chat)
                return jabberStatusEnum.getStatus(JabberStatusEnum.FREE_FOR_CHAT);
            else if (mode == Presence.Mode.dnd)
                return jabberStatusEnum.getStatus(JabberStatusEnum.DO_NOT_DISTURB);
            else if (mode == Presence.Mode.xa)
                return jabberStatusEnum.getStatus(JabberStatusEnum.EXTENDED_AWAY);
            else {
                //unknown status
                if (presence.isAway())
                    return jabberStatusEnum.getStatus(JabberStatusEnum.AWAY);
                if (presence.isAvailable())
                    return jabberStatusEnum.getStatus(JabberStatusEnum.AVAILABLE);
            }
        }
        return jabberStatusEnum.getStatus(JabberStatusEnum.OFFLINE);
    }

    /**
     * Converts the specified JabberStatusEnum member to the corresponding Jabber Mode
     *
     * @param status the jabberStatus
     * @return a PresenceStatus instance
     */
    public static Presence.Mode presenceStatusToJabberMode(PresenceStatus status)
    {
        return scToJabberModesMappings.get(status.getStatusName());
    }

    /**
     * Utility method throwing an exception if the stack is not properly initialized.
     *
     * @throws IllegalStateException if the underlying stack is not registered and initialized.
     */
    void assertConnected()
            throws IllegalStateException
    {
        if (parentProvider == null) {
            throw new IllegalStateException("The provider must be non-null and signed on the " +
                    "Jabber service before able to communicate.");
        }
        if (!parentProvider.isRegistered()) {
            // if we are not registered but the current status is online change the current status
            if ((currentStatus != null) && currentStatus.isOnline()) {
                fireProviderStatusChangeEvent(currentStatus,
                        parentProvider.getJabberStatusEnum().getStatus(JabberStatusEnum.OFFLINE));
            }
            throw new IllegalStateException("The provider must be signed on the Jabber service " +
                    "before being able to communicate.");
        }
    }

    /**
     * Fires provider status changes.
     *
     * @param oldStatus old status
     * @param newStatus new status
     */
    @Override
    public void fireProviderStatusChangeEvent(PresenceStatus oldStatus, PresenceStatus newStatus)
    {
        if (!oldStatus.equals(newStatus)) {
            currentStatus = newStatus;
            super.fireProviderStatusChangeEvent(oldStatus, newStatus);

            // Do not update contacts status if pps is in reconnecting state
            if (updateAllStatus) {
                PresenceStatus offlineStatus = parentProvider.getJabberStatusEnum().getStatus(JabberStatusEnum.OFFLINE);

                if (newStatus.equals(offlineStatus)) {
                    // send event notifications saying that all our buddies are offline. The
                    // protocol does not implement top level buddies nor subgroups for top level
                    // groups so a simple nested loop would be enough.
                    Iterator<ContactGroup> groupsIter = getServerStoredContactListRoot().subgroups();
                    while (groupsIter.hasNext()) {
                        ContactGroup group = groupsIter.next();
                        Iterator<Contact> contactsIter = group.contacts();

                        while (contactsIter.hasNext()) {
                            ContactJabberImpl contact = (ContactJabberImpl) contactsIter.next();
                            updateContactStatus(contact, offlineStatus);
                        }
                    }

                    // do the same for all contacts in the root group
                    Iterator<Contact> contactsIter = getServerStoredContactListRoot().contacts();

                    while (contactsIter.hasNext()) {
                        ContactJabberImpl contact = (ContactJabberImpl) contactsIter.next();
                        updateContactStatus(contact, offlineStatus);
                    }
                }
            }
        }
    }

    /**
     * Sets the display name for <tt>contact</tt> to be <tt>newName</tt>.
     * <p>
     *
     * @param contact the <tt>Contact</tt> that we are renaming
     * @param newName a <tt>String</tt> containing the new display name for <tt>metaContact</tt>.
     * @throws IllegalArgumentException if <tt>contact</tt> is not an instance that belongs to the underlying implementation.
     */
    @Override
    public void setDisplayName(Contact contact, String newName)
            throws IllegalArgumentException
    {
        assertConnected();
        if (!(contact instanceof ContactJabberImpl))
            throw new IllegalArgumentException("Argument is not an jabber contact (contact = " + contact + ")");

        RosterEntry entry = ((ContactJabberImpl) contact).getSourceEntry();
        if (entry != null)
            try {
                entry.setName(newName);
            } catch (NotConnectedException | NoResponseException | XMPPErrorException |
                    InterruptedException e) {
                e.printStackTrace();
            }
    }

    /**
     * The listener that will tell us when we're registered to server and is ready to
     * init and accept the rosterListener.
     * cmeng: This implementation supports Roster Versioning / RosterStore for reduced
     * bandwidth requirements. See ProtocolProviderServiceJabberImpl#initRosterStore
     */
    private class RegistrationStateListener implements RegistrationStateChangeListener
    {
        /**
         * The method is called by a ProtocolProvider implementation whenever a change in the
         * registration state of the corresponding provider had occurred.
         *
         * @param evt ProviderStatusChangeEvent the event describing the status change.
         */
        public void registrationStateChanged(RegistrationStateChangeEvent evt)
        {
            RegistrationState eventNew = evt.getNewState();
            XMPPConnection xmppConnection = parentProvider.getConnection();
            if (eventNew == RegistrationState.REGISTERING) {
				/*
				 * Add a RosterLoaded listener as this will indicate when the roster is
				 * received or loaded from RosterStore (upon authenticated). We are then ready
				 * to dispatch the contact list. Note the actual RosterListener used is added
				 * and active just after the RosterLoadedListener is triggered.
				 */

                // will be used to store presence events till roster is initialized
                contactChangesListener = new ContactChangesListener();

                // setup to init ssContactList upon receiving the rosterLoaded event
                Roster roster = Roster.getInstanceFor(xmppConnection);
                roster.addRosterLoadedListener(new ServerStoredListInit());

                // Adds subscription listener as soon as connection is created or we can miss some
                // subscription requests
                if (subscriptionPacketListener == null) {
                    subscriptionPacketListener = new JabberSubscriptionListener();
                    xmppConnection.addAsyncStanzaListener(
                            subscriptionPacketListener, new StanzaTypeFilter(Presence.class)
                    );
                }
            }
            else if (eventNew == RegistrationState.REGISTERED) {
                // Do nothing if the session is resumed
                if (evt.getReasonCode() != RegistrationStateChangeEvent.REASON_RESUMED) {

				/* Add avatar change listener to handle contacts' avatar changes via XEP-0153*/
                    vCardAvatarManager = VCardAvatarManager.getInstanceFor(xmppConnection);
                    vCardAvatarManager.addVCardAvatarChangeListener(OperationSetPersistentPresenceJabberImpl.this);

				/* Add avatar change listener to handle contacts' avatar changes via XEP-0084 */
                    userAvatarManager = UserAvatarManager.getInstanceFor(xmppConnection);
                    userAvatarManager.addAvatarListener(OperationSetPersistentPresenceJabberImpl.this);

                    /**
                     * Immediately Upon account registration, load the account VCard info and
                     * cache the retrieved info in retrievedDetails for later use (avoid
                     * duplicate vcard.load().
                     * The avatar Hash will also be updated if account photo is defined to support
                     * XEP-00135 VCard Avatar <vcard-temp:x:update/> protocol
                     */
                    OperationSetServerStoredAccountInfo accountInfoOpSet
                            = parentProvider.getOperationSet(OperationSetServerStoredAccountInfo.class);
                    if (accountInfoOpSet != null) {
                        accountInfoOpSet.getAllAvailableDetails();
                    }
                }
            }
            else if (eventNew == RegistrationState.RECONNECTING) {
                // since we are disconnected, need to change our own status. Leave the contacts'
                // status untouched as we will not be informed when we resumed.
                PresenceStatus oldStatus = currentStatus;
                PresenceStatus currentStatus = parentProvider.getJabberStatusEnum().getStatus(JabberStatusEnum.OFFLINE);
                updateAllStatus = false;
                fireProviderStatusChangeEvent(oldStatus, currentStatus);
            }
            else if (eventNew == RegistrationState.UNREGISTERED
                    || eventNew == RegistrationState.AUTHENTICATION_FAILED
                    || eventNew == RegistrationState.CONNECTION_FAILED) {
                // since we are disconnected, we won't receive any further status updates so we
                // need to change by ourselves our own status as well as set to offline all
                // contacts in our contact list that were online
                PresenceStatus oldStatus = currentStatus;
                PresenceStatus currentStatus = parentProvider.getJabberStatusEnum().getStatus(JabberStatusEnum.OFFLINE);
                clearLocalContactResources();
                updateAllStatus = true;
                fireProviderStatusChangeEvent(oldStatus, currentStatus);
                ssContactList.cleanup();
                if (xmppConnection != null) {
                    xmppConnection.removeAsyncStanzaListener(subscriptionPacketListener);

                    // the roster is guaranteed to be non-null
                    Roster.getInstanceFor(xmppConnection).removeRosterListener(contactChangesListener);
                    // vCardAvatarManager can be null for unRegistered account
                    if (vCardAvatarManager != null) {
                        vCardAvatarManager.removeVCardAvatarChangeListener(OperationSetPersistentPresenceJabberImpl.this);
                        userAvatarManager.removeAvatarListener(OperationSetPersistentPresenceJabberImpl.this);
                    }
                }
                subscriptionPacketListener = null;
                contactChangesListener = null;
            }
        }
    }

    /**
     * Updates the resources for the contact.
     *
     * @param contact the contact which resources to update.
     * @param removeUnavailable whether to remove unavailable resources.
     * @return whether resource has been updated
     */
    private boolean updateResources(ContactJabberImpl contact, boolean removeUnavailable)
    {
        if (!contact.isResolved()
                || (contact instanceof VolatileContactJabberImpl
                && ((VolatileContactJabberImpl) contact).isPrivateMessagingContact()))
            return false;

        boolean eventFired = false;
        Map<Jid, ContactResourceJabberImpl> resources = contact.getResourcesMap();

        // Do not obtain getRoster if we are not connected, or new Roster will be created, all the
        // resources that will be returned will be unavailable. As we are not connected if set
        // remove all resources

        XMPPConnection xmppConnection = parentProvider.getConnection();
        if (xmppConnection == null || !xmppConnection.isConnected()) {
            if (removeUnavailable) {
                Iterator<Map.Entry<Jid, ContactResourceJabberImpl>> iter = resources.entrySet().iterator();
                while (iter.hasNext()) {
                    Map.Entry<Jid, ContactResourceJabberImpl> entry = iter.next();
                    iter.remove();
                    contact.fireContactResourceEvent(new ContactResourceEvent(contact,
                            entry.getValue(), ContactResourceEvent.RESOURCE_REMOVED));
                    eventFired = true;
                }
            }
            return eventFired;
        }

        Roster roster = Roster.getInstanceFor(xmppConnection);
        List<Presence> it = roster.getPresences(contact.getJid().asBareJid());
        // Choose the resource which has the highest priority AND supports Jingle, if we have two
        // resources with same priority take the most available.
        for (Presence presence : it) {
            eventFired = updateResource(contact, null, presence) || eventFired;
        }

        if (!removeUnavailable)
            return eventFired;

        Set<Jid> resourceKeys = resources.keySet();
        for (Jid fullJid : resourceKeys) {

            if (!roster.getPresenceResource(fullJid.asFullJidIfPossible()).isAvailable()) {
                eventFired = removeResource(contact, fullJid) || eventFired;
            }
        }
        return eventFired;
    }

    /**
     * Update the resources for the contact for the received presence.
     *
     * @param contact the contact which resources to update.
     * @param fullJid the full jid to use, if null will use those from the presence stanza
     * @param presence the presence stanza to use to get info.
     * @return whether resource has been updated
     */
    private boolean updateResource(ContactJabberImpl contact, Jid fullJid, Presence presence)
    {
        if (fullJid == null)
            fullJid = presence.getFrom();

        Resourcepart resource = fullJid.getResourceOrNull();
        if (resource != null) {
            Map<Jid, ContactResourceJabberImpl> resources = contact.getResourcesMap();
            ContactResourceJabberImpl contactResource = resources.get(fullJid);
            PresenceStatus newPresenceStatus = OperationSetPersistentPresenceJabberImpl
                    .jabberStatusToPresenceStatus(presence, parentProvider);

            if (contactResource == null) {
                contactResource = createResource(presence, fullJid, contact);
                resources.put(fullJid, contactResource);

                contact.fireContactResourceEvent(new ContactResourceEvent(contact, contactResource,
                        ContactResourceEvent.RESOURCE_ADDED));
                return true;
            }
            else {
                boolean oldIndicator = contactResource.isMobile();
                boolean newIndicator = mobileIndicator.isMobileResource(resource.toString(), fullJid);
                int oldPriority = contactResource.getPriority();

                // update mobile indicator, as cabs maybe added after creating the resource for the
                // contact
                contactResource.setMobile(newIndicator);

                contactResource.setPriority(presence.getPriority());
                if (oldPriority != contactResource.getPriority()) {
                    // priority has been updated so update and the mobile indicator before firing an event
                    mobileIndicator.resourcesUpdated(contact);
                }

                if (contactResource.getPresenceStatus().getStatus()
                        != newPresenceStatus.getStatus()
                        || (oldIndicator != newIndicator)
                        || (oldPriority != contactResource.getPriority())) {
                    contactResource.setPresenceStatus(newPresenceStatus);

                    contact.fireContactResourceEvent(new ContactResourceEvent(contact,
                            contactResource, ContactResourceEvent.RESOURCE_MODIFIED));
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Removes the resource indicated by the fullJid from the list with resources for the contact.
     *
     * @param contact from its list of resources to remove
     * @param fullJid the full jid.
     * @return whether resource has been updated
     */
    private boolean removeResource(ContactJabberImpl contact, Jid fullJid)
    {
        Map<Jid, ContactResourceJabberImpl> resources = contact.getResourcesMap();

        if (resources.containsKey(fullJid)) {
            ContactResource removedResource = resources.remove(fullJid);

            contact.fireContactResourceEvent(new ContactResourceEvent(contact, removedResource,
                    ContactResourceEvent.RESOURCE_REMOVED));
            return true;
        }
        return false;
    }

    /**
     * Fires the status change, respecting resource priorities.
     *
     * @param presence the presence changed.
     */
    void firePresenceStatusChanged(Presence presence)
    {
        if (contactChangesListener != null)
            contactChangesListener.firePresenceStatusChanged(presence);
    }

    /**
     * Updates contact status and its resources, fires PresenceStatusChange events.
     *
     * @param contact the contact which presence to update if needed.
     * @param newStatus the new status.
     */
    private void updateContactStatus(ContactJabberImpl contact, PresenceStatus newStatus)
    {
        // When status changes this may be related to a change in the available resources.
        boolean oldMobileIndicator = contact.isMobile();
        boolean resourceUpdated = updateResources(contact, true);
        mobileIndicator.resourcesUpdated(contact);
        PresenceStatus oldStatus = contact.getPresenceStatus();

        // when old and new status are the same do nothing no change
        if (oldStatus.equals(newStatus) && oldMobileIndicator == contact.isMobile()) {
            return;
        }

        contact.updatePresenceStatus(newStatus);
        if (logger.isDebugEnabled())
            logger.debug("Will Dispatch the contact status event.");

        fireContactPresenceStatusChangeEvent(contact, contact.getParentContactGroup(),
                oldStatus, newStatus, resourceUpdated);
    }

    /**
     * Manage changes of statuses by resource.
     */
    class ContactChangesListener implements RosterListener
    {
        /**
         * Store events for later processing, used when initializing contactList.
         */
        private boolean storeEvents = false;

        /**
         * Stored presences for later processing.
         */
        private List<Presence> storedPresences = null;

        /**
         * Map containing all statuses for a userJid.
         */
        private final Map<Jid, TreeSet<Presence>> statuses = new Hashtable<>();

        /**
         * Not used here.
         *
         * @param addresses list of addresses added
         */
        public void entriesAdded(Collection<Jid> addresses)
        {
        }

        /**
         * Not used here.
         *
         * @param addresses list of addresses updated
         */
        public void entriesUpdated(Collection<Jid> addresses)
        {
        }

        /**
         * Not used here.
         *
         * @param addresses list of addresses deleted
         */
        public void entriesDeleted(Collection<Jid> addresses)
        {
        }

        /**
         * Not used here.
         */
        public void rosterError(XMPPError error, Stanza stanza)
        {
        }

        /**
         * Received on resource status change.
         *
         * @param presence presence that has changed
         */
        public void presenceChanged(Presence presence)
        {
            firePresenceStatusChanged(presence);
        }

        /**
         * Whether listener is currently storing presence events.
         *
         * @return
         */
        boolean isStoringPresenceEvents()
        {
            return storeEvents;
        }

        /**
         * Adds presence stanza to the list.
         *
         * @param presence presence stanza
         */
        void addPresenceEvent(Presence presence)
        {
            storedPresences.add(presence);
        }

        /**
         * Initialize new storedPresences<Presence> and sets store events to true.
         */
        void storeEvents()
        {
            this.storedPresences = new ArrayList<>();
            this.storeEvents = true;
        }

        /**
         * Process stored presences.
         */
        void processStoredEvents()
        {
            storeEvents = false;
            for (Presence p : storedPresences) {
                firePresenceStatusChanged(p);
            }
            storedPresences.clear();
            storedPresences = null;
        }

        /**
         * Fires the status change, respecting resource priorities.
         *
         * @param presence the presence changed.
         */
        void firePresenceStatusChanged(Presence presence)
        {
            if (storeEvents && storedPresences != null) {
                storedPresences.add(presence);
                return;
            }

            try {
                Jid userJid = presence.getFrom();
                OperationSetMultiUserChat mucOpSet = parentProvider.getOperationSet(OperationSetMultiUserChat.class);
                if (mucOpSet != null) {
                    List<ChatRoom> chatRooms = mucOpSet.getCurrentlyJoinedChatRooms();
                    for (ChatRoom chatRoom : chatRooms) {
                        if (userJid.equals(chatRoom.getName())) {
                            userJid = presence.getFrom();
                            break;
                        }
                    }
                }

                if (logger.isDebugEnabled())
                    logger.debug("Received a status update for buddy = " + userJid);

                // all contact statuses that are received from all its resources ordered by
                // priority (higher first) and those with equal priorities order with the one
                // that is most connected as first
                TreeSet<Presence> userStats = statuses.get(userJid);
                if (userStats == null) {
                    userStats = new TreeSet<>(new Comparator<Presence>()
                    {
                        public int compare(Presence o1, Presence o2)
                        {
                            int res = o2.getPriority() - o1.getPriority();

                            // if statuses are with same priorities return which one is more
                            // available counts the JabberStatusEnum order
                            if (res == 0) {
                                res = jabberStatusToPresenceStatus(o2, parentProvider).getStatus()
                                        - jabberStatusToPresenceStatus(o1, parentProvider).getStatus();
                                // We have run out of "logical" ways to order the presences inside
                                // the TreeSet. We have make sure we are consistent with equals.
                                // We do this by comparing the unique resource names. If this
                                // evaluates to 0 again, then we can safely assume this presence
                                // object represents the same resource and by that the same client.
                                if (res == 0) {
                                    res = o1.getFrom().compareTo(o2.getFrom());
                                }
                            }
                            return res;
                        }
                    });
                    statuses.put(userJid, userStats);
                }
                else {
                    // remove the status for this resource if we are online we will update its
                    // value with the new status
                    for (Iterator<Presence> iter = userStats.iterator(); iter.hasNext(); ) {
                        Presence p = iter.next();
                        if (presence.getFrom().equals(p.getFrom()))
                            iter.remove();
                    }
                }

                if (!jabberStatusToPresenceStatus(presence, parentProvider).equals(
                        parentProvider.getJabberStatusEnum().getStatus(JabberStatusEnum.OFFLINE)
                )) {
                    userStats.add(presence);
                }

                Presence currentPresence;
                if (userStats.size() == 0) {
                    currentPresence = presence;
					/*
					 * We no longer have statuses for userID so it doesn't make sense to retain
					 * (1) the TreeSet and
					 * (2) its slot in the statuses Map.
					 */
                    statuses.remove(userJid);
                }
                else
                    currentPresence = userStats.first();

                ContactJabberImpl sourceContact = ssContactList.findContactById(userJid.toString());
                if (sourceContact == null) {
                    logger.warn("No source contact found for id = " + userJid);
                    return;
                }

                // statuses may be the same and only change in status message
                sourceContact.setStatusMessage(currentPresence.getStatus());
                updateContactStatus(sourceContact, jabberStatusToPresenceStatus(currentPresence, parentProvider));
            } catch (IllegalStateException | IllegalArgumentException ex) {
                logger.error("Failed changing status", ex);
            }
        }
    }

    /**
     * Listens for subscription events coming from smack.
     */
    private class JabberSubscriptionListener implements StanzaListener
    {
        /**
         * The authorization handler.
         */
        private AuthorizationHandler handler = null;

        /**
         * List of early subscriptions.
         */
        private Map<String, String> earlySubscriptions = new HashMap<>();

        /**
         * Adds auth handler.
         *
         * @param handler
         */
        private synchronized void setHandler(AuthorizationHandler handler)
        {
            this.handler = handler;
            handleEarlySubscribeReceived();
        }

        /**
         * Process packets.
         *
         * @param stanza stanza received to be processed
         */
        public void processStanza(Stanza stanza)
        {
            Presence presence = (Presence) stanza;
            if (presence == null)
                return;

            Presence.Type presenceType = presence.getType();
            final String fromID = presence.getFrom().toString();

            if (presenceType == Presence.Type.subscribe) {
                String displayName = null;
                Nick ext = (Nick) presence.getExtension(Nick.NAMESPACE);
                if (ext != null)
                    displayName = ext.getName();

                synchronized (this) {
                    if (handler == null) {
                        earlySubscriptions.put(fromID, displayName);
                        // nothing to handle
                        return;
                    }
                }
                handleSubscribeReceived(fromID, displayName);
            }
            else if (presenceType == Presence.Type.unsubscribed) {
                if (logger.isTraceEnabled())
                    logger.trace(fromID + " does not allow your subscription");

                if (handler == null) {
                    logger.warn("No to handle unsubscribed AuthorizationHandler for " + fromID);
                    return;
                }

                ContactJabberImpl contact = ssContactList.findContactById(fromID);

                if (contact != null) {
                    AuthorizationResponse response = new AuthorizationResponse(AuthorizationResponse.REJECT, "");

                    handler.processAuthorizationResponse(response, contact);
                    try {
                        ssContactList.removeContact(contact);
                    } catch (OperationFailedException e) {
                        logger.error("Cannot remove contact that is unsubscribed.");
                    }
                }
            }
            else if (presenceType == Presence.Type.subscribed) {
                if (handler == null) {
                    logger.warn("No AuthorizationHandler to handle subscribed for " + fromID);
                    return;
                }

                ContactJabberImpl contact = ssContactList.findContactById(fromID);
                AuthorizationResponse response = new AuthorizationResponse(AuthorizationResponse.ACCEPT, "");
                handler.processAuthorizationResponse(response, contact);
            }
            else if (presenceType == Presence.Type.available && contactChangesListener != null
                    && contactChangesListener.isStoringPresenceEvents()) {
                contactChangesListener.addPresenceEvent(presence);
            }
        }

        /**
         * Handles early presence subscribe that were received.
         */
        private void handleEarlySubscribeReceived()
        {
            for (String from : earlySubscriptions.keySet()) {
                handleSubscribeReceived(from, earlySubscriptions.get(from));
            }
            earlySubscriptions.clear();
        }

        /**
         * Handles receiving a presence subscribe
         *
         * @param fromID sender in bareJid
         */
        private void handleSubscribeReceived(final String fromID, final String displayName)
        {
            // run waiting for user response in different thread as this seems to block the stanza
            // dispatch thread and we don't receive anything till we unblock it
            new Thread(new Runnable()
            {
                public void run()
                {
                    if (logger.isTraceEnabled()) {
                        logger.trace(fromID + " wants to add you to its contact list");
                    }

                    // buddy want to add you to its roster
                    ContactJabberImpl srcContact = ssContactList.findContactById(fromID);
                    Presence.Type responsePresenceType = null;

                    if (srcContact == null) {
                        srcContact = createVolatileContact(fromID, displayName);
                    }
                    else {
                        if (srcContact.isPersistent())
                            responsePresenceType = Presence.Type.subscribed;
                    }

                    if (responsePresenceType == null) {
                        AuthorizationRequest req = new AuthorizationRequest();
                        AuthorizationResponse response = handler.processAuthorisationRequest(req, srcContact);

                        if (response != null) {
                            if (response.getResponseCode().equals(AuthorizationResponse.ACCEPT)) {
                                responsePresenceType = Presence.Type.subscribed;
                                if (logger.isInfoEnabled())
                                    logger.info("Sending Accepted Subscription");
                            }
                            else if (response.getResponseCode()
                                    .equals(AuthorizationResponse.REJECT)) {
                                responsePresenceType = Presence.Type.unsubscribed;
                                if (logger.isInfoEnabled())
                                    logger.info("Sending Rejected Subscription");
                            }
                        }
                    }

                    // subscription ignored
                    if (responsePresenceType == null)
                        return;

                    Presence responsePacket = new Presence(responsePresenceType);
                    responsePacket.setTo(fromID);
                    try {
                        parentProvider.getConnection().sendStanza(responsePacket);
                    } catch (NotConnectedException | InterruptedException e) {
                        e.printStackTrace();
                    }
                }
            }).start();
        }
    }

    /**
     * Runnable that resolves local contact list against the server side roster. This thread is the
     * one which will call getRoster for the first time. The thread wait until the roaster
     * is loaded by the Smack Roster class
     */
    private class ServerStoredListInit implements Runnable, RosterLoadedListener
    {
        Roster mRoster;

        public void run()
        {
            // we are already being notified lets remove us from the rosterLoaded listener
            mRoster.removeRosterLoadedListener(this);

			/*
			 *  init the presenceChangeLister, RosterChangeLister and update contact list status
			 */
            ssContactList.init(contactChangesListener);

            // as we have dispatched the contact list and Roster is ready lets start the jingle
            // nodes discovery
            parentProvider.startJingleNodesDiscovery();
        }

        /**
         * When rosterLoaded event is received we are ready to to dispatch the
         * contact list, doing it in different thread to avoid blocking xmpp stanza receiving.
         *
         * @param roster the roster stanza
         */
        @Override
        public void onRosterLoaded(Roster roster)
        {
            mRoster = roster;
            if (!ssContactList.isRosterInitialized()) {
                new Thread(this, getClass().getName()).start();
            }
        }

        @Override
        public void onRosterLoadingFailed(Exception exception)
        {

        }
    }

//	/**
//	 * Updates the presence extension to advertise a new photo SHA-1 hash corresponding to the new
//	 * avatar given in parameter.
//	 *
//	 * @param imageBytes
//	 * 		The new avatar set for this account.
//	 */
//	public void updateAccountPhotoPresenceExtension(byte[] imageBytes)
//	{
//		try {
//			// If the image has changed, then updates the presence extension and send immediately a
//			// presence stanza to advertise the photo update.
//			if (vCardAvatarManager.updateVCardAvatarHash(imageBytes, true)) {
//				this.publishPresenceStatus(currentStatus, currentStatusMessage);
//			}
//		}
//		catch (OperationFailedException ex) {
//			logger.info("Can not send presence extension to broadcast photo update", ex);
//		}
//	}

    /**
     * Event is fired when a contact change avatar via XEP-0153: vCard-Based Avatars protocol.
     * <p>
     * onAvatarChange event is triggered if a change in the VCard Avatar is detected via
     * <present/> in its update <x xmlns='vcard-temp:x:update'/><photo/> element imageHash value.
     * A new SHA-1 avatar contained in the photo tag represents a new avatar for this contact.
     *
     * @param userID The contact of the sent <presence/> stanza.
     * @param avatarHash The new photo image Hash value contains ["" | "{avatarHash}].
     * avatarHash == "" indicates that the contact does not have avatar specified.
     * avatarHash can be used to retrieve photo image from cache if auto downloaded
     * @param vCardInfo The contact VCard info - can contain null.
     */
    @Override
    public void onAvatarChange(Jid userID, String avatarHash, VCard vCardInfo)
    {
		/*
		 * Retrieves the contact ID that aTalk currently managed concerning the peer that has
		 * send this presence stanza with avatar update.
		 */
        ContactJabberImpl sourceContact = ssContactList.findContactById(userID.toString());

		/*
		 * If this contact is not yet in our contact list, then there is no need to manage this
		 * avatar update.
		 */
        if (sourceContact == null) {
            return;
        }

        byte[] currentAvatar = sourceContact.getImage(false);

		/*
		 * If vCardInfo is not null, vCardAvatarManager has already loaded the new image;
		 * we can just retrieve from the vCardInfo if any. Otherwise try to get it from cache or
		 * persistent store before we download on our own .
		 * Note: newAvatar will have byte[0] when avatarHash == ""
		 *
		 * @see VCardAvatarManager#getAvatarImageByHash(String)
		 */
        byte[] newAvatar = new byte[0];
        if (vCardInfo != null) {
            newAvatar = vCardInfo.getAvatar();
        }
        else {
            /**
             * Try to fetch from the cache/persistent before we proceed to download on our own.
             * Download via {@link InfoRetriever#retrieveDetails(BareJid)} method as it may have
             * other updated VCard info of interest that we would like to update the contact's
             * retrieveDetails
             */
            newAvatar = VCardAvatarManager.getAvatarImageByHash(avatarHash);
            if (newAvatar == null) {
                List<GenericDetail> details = mInfoRetriever.retrieveDetails(userID.asBareJid());
                for (GenericDetail detail : details) {
                    if (detail instanceof ImageDetail) {
                        newAvatar = ((ImageDetail) detail).getBytes();
                        break;
                    }
                }
            }
        }
        if (newAvatar == null)
            newAvatar = new byte[0];

        // Sets the new avatar image for the contact.
        sourceContact.setImage(newAvatar);

        // Fires a property change event to update the contact list.
        this.fireContactPropertyChangeEvent(sourceContact,
                ContactPropertyChangeEvent.PROPERTY_IMAGE, currentAvatar, newAvatar);
    }

    /**
     * Event is fired when a contact change avatar via XEP-0084: User Avatar protocol.
     *
     * @param from the contact EntityBareJid who change his avatar
     * @param avatarId the new avatar id, may be null if the contact set no avatar
     * The new photo image Hash value contains ["" | "{avatarHash}].
     * avatarHash == "" indicates that the contact does not have avatar specified.
     * avatarHash can be used to retrieve photo image from cache if auto downloaded
     * @param avatarInfo the metadata info of the userAvatar, may be empty if the contact set no avatar
     */
    public void onAvatarChange(EntityBareJid from, String avatarId, List<AvatarMetadata.Info> avatarInfo)
    {
		/*
		 * Retrieves the contact ID that aTalk currently managed concerning the peer that has
		 * send this presence stanza with avatar update.
		 */
        ContactJabberImpl sourceContact = ssContactList.findContactById(from.toString());

		/*
		 * If this contact is not yet in our contact list, then there is no need to manage this
		 * avatar update.
		 */
        if (sourceContact == null) {
            return;
        }

        byte[] currentAvatar = sourceContact.getImage(false);

		/*
		 * Try to retrieve from the cache/persistent before we proceed to download on our own via
		 * {@link UserAvatarManager#downloadAvatar(EntityBareJid, String, AvatarMetadata.Info)}
		 */
        byte[] newAvatar = AvatarManager.getAvatarImageByHash(avatarId);
        if (newAvatar == null) {
            AvatarMetadata.Info info = userAvatarManager.selectAvatar(avatarInfo);
            if (userAvatarManager.downloadAvatar(from, avatarId, info)) {
                newAvatar = AvatarManager.getAvatarImageByHash(avatarId);
            }
        }
        if (newAvatar == null)
            newAvatar = new byte[0];

        // Sets the new avatar image for the contact.
        sourceContact.setImage(newAvatar);

        // Fires a property change event to update the contact list.
        this.fireContactPropertyChangeEvent(sourceContact,
                ContactPropertyChangeEvent.PROPERTY_IMAGE, currentAvatar, newAvatar);
    }

    /**
     * Initializes the map with priorities and statuses which we will use when changing statuses.
     */
    private void initializePriorities()
    {
        try {
            this.resourcePriorityAvailable = Integer.parseInt(parentProvider.getAccountID()
                    .getAccountPropertyString(ProtocolProviderFactory.RESOURCE_PRIORITY));
        } catch (NumberFormatException ex) {
            logger.error("Wrong value for resource priority", ex);
        }

        addDefaultValue(JabberStatusEnum.AWAY, -5);
        addDefaultValue(JabberStatusEnum.EXTENDED_AWAY, -10);
        addDefaultValue(JabberStatusEnum.ON_THE_PHONE, -15);
        addDefaultValue(JabberStatusEnum.IN_A_MEETING, -16);
        addDefaultValue(JabberStatusEnum.DO_NOT_DISTURB, -20);
        addDefaultValue(JabberStatusEnum.FREE_FOR_CHAT, +5);
    }

    /**
     * Checks for account property that can override this status. If missing use the shift value to
     * create the priority to use, make sure it is not zero or less than it.
     *
     * @param statusName the status to check/create priority
     * @param availableShift the difference from available resource value to use.
     */
    private void addDefaultValue(String statusName, int availableShift)
    {
        String resourcePriority = getAccountPriorityForStatus(statusName);
        if (resourcePriority != null) {
            try {
                addPresenceToPriorityMapping(statusName, Integer.parseInt(resourcePriority));
            } catch (NumberFormatException ex) {
                logger.error("Wrong value for resource priority for status: " + statusName, ex);
            }
        }
        else {
            // if priority is less than zero, use the available priority
            int priority = resourcePriorityAvailable + availableShift;
            if (priority <= 0)
                priority = resourcePriorityAvailable;
            addPresenceToPriorityMapping(statusName, priority);
        }
    }

    /**
     * Adds the priority mapping for the <tt>statusName</tt>. Make sure we replace ' ' with '_' and
     * use upper case as this will be and the property names used in account properties that can
     * override this values.
     *
     * @param statusName the status name to use
     * @param value and its priority
     */
    private static void addPresenceToPriorityMapping(String statusName, int value)
    {
        statusToPriorityMappings.put(statusName.replaceAll(" ", "_").toUpperCase(Locale.US), value);
    }

    /**
     * Returns the priority which will be used for <tt>statusName</tt>. Make sure we replace ' '
     * with '_' and use upper case as this will be and the property names used in account
     * properties that can override this values.
     *
     * @param statusName the status name
     * @return the priority which will be used for <tt>statusName</tt>.
     */
    private int getPriorityForPresenceStatus(String statusName)
    {
        Integer priority = statusToPriorityMappings.get(statusName.replaceAll(" ", "_")
                .toUpperCase(Locale.US));
        if (priority == null)
            return resourcePriorityAvailable;

        return priority;
    }

    /**
     * Returns the account property value for a status name, if missing return null. Make sure we
     * replace ' ' with '_' and use upper case as this will be and the property names used in
     * account properties that can override this values.
     *
     * @param statusName PresenceStatus name
     * @return the account property value for a status name, if missing return null.
     */
    private String getAccountPriorityForStatus(String statusName)
    {
        return parentProvider.getAccountID().getAccountPropertyString(
                ProtocolProviderFactory.RESOURCE_PRIORITY + "_"
                        + statusName.replaceAll(" ", "_").toUpperCase(Locale.US));
    }

    /**
     * Returns the contactList impl.
     *
     * @return the contactList impl.
     */
    public ServerStoredContactListJabberImpl getSsContactList()
    {
        return ssContactList;
    }
}
