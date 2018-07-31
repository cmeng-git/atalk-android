/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package net.java.sip.communicator.impl.protocol.jabber;

import net.java.sip.communicator.service.protocol.*;
import net.java.sip.communicator.service.protocol.ServerStoredDetails.GenericDetail;
import net.java.sip.communicator.service.protocol.ServerStoredDetails.ImageDetail;
import net.java.sip.communicator.service.protocol.event.*;
import net.java.sip.communicator.service.protocol.jabberconstants.JabberStatusEnum;
import net.java.sip.communicator.util.ConfigurationUtils;
import net.java.sip.communicator.util.Logger;

import org.atalk.util.StringUtils;
import org.jivesoftware.smack.SmackException;
import org.jivesoftware.smack.SmackException.NoResponseException;
import org.jivesoftware.smack.SmackException.NotConnectedException;
import org.jivesoftware.smack.XMPPConnection;
import org.jivesoftware.smack.XMPPException.XMPPErrorException;
import org.jivesoftware.smack.packet.Presence;
import org.jivesoftware.smack.roster.*;
import org.jivesoftware.smackx.avatar.AvatarManager;
import org.jivesoftware.smackx.avatar.useravatar.UserAvatarManager;
import org.jivesoftware.smackx.avatar.useravatar.listener.UserAvatarListener;
import org.jivesoftware.smackx.avatar.useravatar.packet.AvatarMetadata;
import org.jivesoftware.smackx.avatar.vcardavatar.VCardAvatarManager;
import org.jivesoftware.smackx.avatar.vcardavatar.listener.VCardAvatarListener;
import org.jivesoftware.smackx.nick.packet.Nick;
import org.jivesoftware.smackx.vcardtemp.packet.VCard;
import org.jxmpp.jid.*;
import org.jxmpp.jid.impl.JidCreate;
import org.jxmpp.jid.parts.Resourcepart;
import org.jxmpp.stringprep.XmppStringprepException;

import java.util.*;

/**
 * The Jabber implementation of a Persistent Presence Operation set. This class manages our own
 * presence status as well as subscriptions for the presence status of our buddies. It also offers methods
 * for retrieving and modifying the buddy contact list and adding listeners for changes in its layout.
 *
 * @author Damian Minkov
 * @author Lyubomir Marinov
 * @author Hristo Terezov
 * @author Eng Chong Meng
 */
public class OperationSetPersistentPresenceJabberImpl
        extends AbstractOperationSetPersistentPresence<ProtocolProviderServiceJabberImpl>
        implements VCardAvatarListener, UserAvatarListener, SubscribeListener, PresenceEventListener
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
     * A map containing bindings between aTalk's xmpp presence status instances and priorities to use for statuses.
     */
    private static Map<String, Integer> statusToPriorityMappings = new Hashtable<>();

    /**
     * The server stored contact list that will be encapsulating smack's buddy list.
     */
    private final ServerStoredContactListJabberImpl ssContactList;

    /**
     * Handle subscriptions event is ready uf true.
     */
    private boolean handleSubsribeEvent = false;

    private Roster mRoster = null;

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
     * Manages the event extension to advertise the SHA-1 hash of this account avatar as defined in XEP-0084.
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
            throw new IllegalArgumentException("The specified parent group cannot contain child groups: " + parent);

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
    public synchronized ContactJabberImpl createVolatileContact(Jid id)
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
    public synchronized ContactJabberImpl createVolatileContact(Jid id, String displayName)
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
    public synchronized ContactJabberImpl createVolatileContact(Jid id, boolean isPrivateMessagingContact)
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
     * @param displayName the display name of the contact.
     * @return the newly created volatile <tt>ContactImpl</tt>
     */
    public synchronized ContactJabberImpl createVolatileContact(Jid id, boolean isPrivateMessagingContact,
            String displayName)
    {
        // first check for already created one.
        ContactGroupJabberImpl notInContactListGroup = ssContactList.getNonPersistentGroup();
        ContactJabberImpl sourceContact = null;
        if (notInContactListGroup != null) {
            sourceContact = notInContactListGroup.findContact(id.asBareJid());
        }
        if (sourceContact != null) {
            return sourceContact;
        }
        else {
            sourceContact = ssContactList.createVolatileContact(id, isPrivateMessagingContact, displayName);
            if (isPrivateMessagingContact && id.hasResource()) {
                updateResources(sourceContact, false);
            }
            return sourceContact;
        }
    }

    /**
     * Creates and returns a unresolved contact from the specified <tt>address</tt> and <tt>persistentData</tt>.
     *
     * @param address an identifier of the contact that we'll be creating.
     * @param persistentData a String returned Contact's getPersistentData() method during a previous run and that
     * has been persistently stored locally.
     * @param parentGroup the group where the unresolved contact is supposed to belong to.
     * @return the unresolved <tt>Contact</tt> created from the specified <tt>address</tt> and <tt>persistentData</tt>
     */
    public Contact createUnresolvedContact(String address, String persistentData, ContactGroup parentGroup)
    {
        if (!(parentGroup instanceof ContactGroupJabberImpl
                || parentGroup instanceof RootContactGroupJabberImpl)) {
            throw new IllegalArgumentException("Argument is not an jabber contact group (" + parentGroup + ")");
        }
        try {
            return ssContactList.createUnresolvedContact(parentGroup, JidCreate.from(address));
        } catch (XmppStringprepException e) {
            throw new IllegalArgumentException("Invalid JID", e);
        }
    }

    /**
     * Creates and returns a unresolved contact from the specified <tt>address</tt> and <tt>persistentData</tt>.
     *
     * @param address an identifier of the contact that we'll be creating.
     * @param persistentData a String returned Contact's getPersistentData() method during a previous run and that
     * has been persistently stored locally.
     * @return the unresolved <tt>Contact</tt> created from the specified <tt>address</tt> and <tt>persistentData</tt>
     */
    public Contact createUnresolvedContact(String address, String persistentData)
    {
        return createUnresolvedContact(address, persistentData, getServerStoredContactListRoot());
    }

    /**
     * Creates and returns a unresolved contact group from the specified <tt>address</tt> and <tt>persistentData</tt>.
     *
     * @param groupUID an identifier, returned by ContactGroup's getGroupUID, that the protocol provider may
     * use in order to create the group.
     * @param persistentData a String returned ContactGroups's getPersistentData() method during a previous run and
     * that has been persistently stored locally.
     * @param parentGroup the group under which the new group is to be created or null if this is group directly
     * underneath the root.
     * @return the unresolved <tt>ContactGroup</tt> created from the specified <tt>uid</tt> and <tt>persistentData</tt>
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
        try {
            return ssContactList.findContactById(JidCreate.from(contactID));
        } catch (XmppStringprepException e) {
            logger.error("Could not parse contact into Jid: " + contactID, e);
            return null;
        }
    }

    public Contact findContactByID(Jid contactId)
    {
        return ssContactList.findContactById(contactId);
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
     * @return the Contact (address, phone number, or uid) that the Provider implementation is communicating on behalf of.
     */
    public Contact getLocalContact()
    {
        if (localContact != null)
            return localContact;

        final FullJid ourJID = parentProvider.getOurJID();
        localContact = new ContactJabberImpl(null, ssContactList, false, true);
        localContact.setLocal(true);
        localContact.updatePresenceStatus(currentStatus);
        localContact.setJid(ourJID);

        Map<FullJid, ContactResourceJabberImpl> rs = localContact.getResourcesMap();
        if (currentPresence != null)
            rs.put(ourJID, createResource(currentPresence, ourJID, localContact));

        List<Presence> presenceList = ssContactList.getPresences(ourJID.asBareJid());
        for (Presence p : presenceList) {
            FullJid fullJid = p.getFrom().asFullJidIfPossible();
            rs.put(fullJid, createResource(p, fullJid, localContact));
        }
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
    private ContactResourceJabberImpl createResource(Presence presence, FullJid fullJid, Contact contact)
    {
        return new ContactResourceJabberImpl(fullJid, contact, jabberStatusToPresenceStatus(presence, parentProvider),
                presence.getPriority(), mobileIndicator.isMobileResource(fullJid));
    }

    /**
     * Clear resources used for local contact and before that update its resources in order to fire the needed events.
     */
    private void clearLocalContactResources()
    {
        if (localContact != null) {
            removeResource(localContact, localContact.getJid().asFullJidIfPossible());
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
     *
     * @param status the JabberStatusEnum
     * @return  JabberPresenceStatus#getStatus(String statusName)
     */
    public PresenceStatus getPresenceStatus(String status)
    {
        return parentProvider.getJabberStatusEnum().getStatus(status);
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
     * Returns the list of PresenceStatus objects that a user of this service may request the provider to enter.
     *
     * @return PresenceStatus ListArray containing "selectable" status instances.
     */
    public List<PresenceStatus> getSupportedStatusSet()
    {
        return parentProvider.getJabberStatusEnum().getSupportedStatusSet();
    }

    /**
     * Checks if the contact address is associated with private messaging contact or not.
     *
     * @param contactJid the address of the contact.
     * @return <tt>true</tt> the contact address is associated with private messaging contact and <tt>false</tt> if not.
     */
    public boolean isPrivateMessagingContact(Jid contactJid)
    {
        return ssContactList.isPrivateMessagingContact(contactJid);
    }

    /**
     * Removes the specified contact from its current parent and places it under <tt>newParent</tt>.
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
     */
    public void publishPresenceStatus(PresenceStatus status, String statusMessage)
            throws IllegalArgumentException, IllegalStateException
    {
        assertConnected();
        JabberStatusEnum jabberStatusEnum = parentProvider.getJabberStatusEnum();
        List <PresenceStatus> supportedStatuses = jabberStatusEnum.getSupportedStatusSet();
        boolean isValidStatus = supportedStatuses.contains(status);

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

            // On the phone or in meeting has a special status which is different from custom status message
            if (status.equals(jabberStatusEnum.getStatus(JabberStatusEnum.ON_THE_PHONE))) {
                presence.setStatus(JabberStatusEnum.ON_THE_PHONE);
            }
            else if (status.equals(jabberStatusEnum.getStatus(JabberStatusEnum.IN_A_MEETING))) {
                presence.setStatus(JabberStatusEnum.IN_A_MEETING);
            }
            else
                presence.setStatus(statusMessage);

            try {
                parentProvider.getConnection().sendStanza(presence);
            } catch (NotConnectedException | InterruptedException e) {
                logger.error("Could not send new presense status", e);
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
     * @param contactJid the jid of the contact whose status we're interested in.
     * @return the <tt>PresenceStatus</tt> of the contact with the specified <tt>contactIdentifier</tt>
     * @throws IllegalArgumentException if the specified <tt>contactIdentifier</tt> does not identify a contact
     * known to the underlying protocol provider
     * @throws IllegalStateException if the underlying protocol provider is not registered/signed on a public service
     */
    public PresenceStatus queryContactStatus(BareJid contactJid)
            throws IllegalArgumentException, IllegalStateException
    {
        /*
         * As stated by the javadoc, IllegalStateException signals that the ProtocolProviderService is not registered.
         */
        assertConnected();
        XMPPConnection xmppConnection = parentProvider.getConnection();
        if (xmppConnection == null) {
            throw new IllegalArgumentException("The provider/account must be signed on in order"
                    + " to query the status of a contact in its roster");
        }

        Roster roster = Roster.getInstanceFor(xmppConnection);
        Presence presence = roster.getPresence(contactJid);
        return jabberStatusToPresenceStatus(presence, parentProvider);
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
        if (handleSubsribeEvent)
            setHandler(handler);
    }

    /**
     * Persistently adds a subscription for the presence status of the contact corresponding to the
     * specified contactIdentifier and indicates that it should be added to the specified group of
     * the server stored contact list.
     *
     * @param parent the parent group of the server stored contact list where the contact should be added.
     * @param contactIdentifier the contact whose status updates we are subscribing for.
     * @throws IllegalArgumentException if <tt>contact</tt> or <tt>parent</tt> are not a contact known to the
     * underlying protocol provider.
     * @throws IllegalStateException if the underlying protocol provider is not registered/signed on a public service.
     * @throws OperationFailedException with code NETWORK_FAILURE if subscribing fails due to errors experienced
     * during network communication
     */
    public void subscribe(ContactGroup parent, String contactIdentifier)
            throws IllegalArgumentException, IllegalStateException, OperationFailedException
    {
        assertConnected();
        if (!(parent instanceof ContactGroupJabberImpl))
            throw new IllegalArgumentException("Argument is not an jabber contact group (group = " + parent + ")");

        ssContactList.addContact(parent, contactIdentifier);
    }

    /**
     * Adds a subscription for the presence status of the contact corresponding to the specified contactIdentifier.
     *
     * @param contactIdentifier the identifier of the contact whose status updates we are subscribing for.
     * @param pps the owner of the contact to be added to RootGroup.
     * @throws IllegalArgumentException if <tt>contact</tt> is not a contact known to the underlying protocol provider
     * @throws IllegalStateException if the underlying protocol provider is not registered/signed on a public service.
     * @throws OperationFailedException with code NETWORK_FAILURE if subscribing fails due to errors experienced
     * during network communication
     */
    public void subscribe(ProtocolProviderService pps, String contactIdentifier)
            throws IllegalArgumentException, IllegalStateException, OperationFailedException
    {
        assertConnected();
        ssContactList.addContact(pps, contactIdentifier);
    }

    /**
     * Removes a subscription for the presence status of the specified contact.
     *
     * @param contact the contact whose status updates we are unsubscribing from.
     * @throws IllegalArgumentException if <tt>contact</tt> is not a contact known to the underlying protocol provider
     * @throws IllegalStateException if the underlying protocol provider is not registered/signed on a public service.
     * @throws OperationFailedException with code NETWORK_FAILURE if unSubscribing fails due to errors experienced
     * during network communication
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
     * Converts the specified jabber status to one of the status fields of the JabberStatusEnum class.
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
        if (!presence.isAvailable()) {
            return jabberStatusEnum.getStatus(JabberStatusEnum.OFFLINE);
        }

        // Check status mode when user is available
        Presence.Mode mode = presence.getMode();
        switch (mode) {
            case available:
                return jabberStatusEnum.getStatus(JabberStatusEnum.AVAILABLE);
            case away:
                // on the phone a special status which is away with custom status message
                if (presence.getStatus() != null && presence.getStatus().contains(JabberStatusEnum.ON_THE_PHONE))
                    return jabberStatusEnum.getStatus(JabberStatusEnum.ON_THE_PHONE);
                else if (presence.getStatus() != null && presence.getStatus().contains(JabberStatusEnum.IN_A_MEETING))
                    return jabberStatusEnum.getStatus(JabberStatusEnum.IN_A_MEETING);
                else
                    return jabberStatusEnum.getStatus(JabberStatusEnum.AWAY);
            case chat:
                return jabberStatusEnum.getStatus(JabberStatusEnum.FREE_FOR_CHAT);
            case dnd:
                return jabberStatusEnum.getStatus(JabberStatusEnum.DO_NOT_DISTURB);
            case xa:
                return jabberStatusEnum.getStatus(JabberStatusEnum.EXTENDED_AWAY);
            default:
                //unknown status
                if (presence.isAway())
                    return jabberStatusEnum.getStatus(JabberStatusEnum.AWAY);
                if (presence.isAvailable())
                    return jabberStatusEnum.getStatus(JabberStatusEnum.AVAILABLE);
                break;
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
            } catch (NotConnectedException | NoResponseException | XMPPErrorException | InterruptedException e) {
                throw new IllegalArgumentException("Could not update name", e);
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
                // contactChangesListener will be used to store presence events till roster is initialized
                contactChangesListener = new ContactChangesListener();
                contactChangesListener.storeEvents();
            }
            else if (eventNew == RegistrationState.REGISTERED) {
                /*
                 * Add a RosterLoaded listener as this will indicate when the roster is
                 * received or loaded from RosterStore (upon authenticated). We are then ready
                 * to dispatch the contact list. Note the actual RosterListener used is added
                 * and active just after the RosterLoadedListener is triggered.
                 *
                 * setup to init ssContactList upon receiving the rosterLoaded event
                 */
                mRoster = Roster.getInstanceFor(xmppConnection);
                mRoster.addRosterLoadedListener(new ServerStoredListInit());

                // Adds subscription listeners only when user is authenticated
                if (!handleSubsribeEvent) {
                    mRoster = Roster.getInstanceFor(xmppConnection);
                    mRoster.addSubscribeListener(OperationSetPersistentPresenceJabberImpl.this);
                    mRoster.addPresenceEventListener(OperationSetPersistentPresenceJabberImpl.this);
                    handleSubsribeEvent = true;
                    logger.info("SubscribeListener and PresenceEventListener added");
                }

                if (vCardAvatarManager == null) {
                    /* Add avatar change listener to handle contacts' avatar changes via XEP-0153*/
                    vCardAvatarManager = VCardAvatarManager.getInstanceFor(xmppConnection);
                    vCardAvatarManager.addVCardAvatarChangeListener(OperationSetPersistentPresenceJabberImpl.this);
                }

                if (userAvatarManager == null) {
                    /* Add avatar change listener to handle contacts' avatar changes via XEP-0084 */
                    userAvatarManager = UserAvatarManager.getInstanceFor(xmppConnection);
                    userAvatarManager.addAvatarListener(OperationSetPersistentPresenceJabberImpl.this);
                }

                // Do the following if no from resumed (do once only)
                if (evt.getReasonCode() != RegistrationStateChangeEvent.REASON_RESUMED) {
                    /*
                     * Immediately Upon account registration, load the account VCard info and cache the
                     * retrieved info in retrievedDetails for later use (avoid duplicate vcard.load().
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
                // since we are disconnected, we won't receive any further status updates so we need to change by
                // ourselves our own status as well as set to offline all contacts in our contact list that were online
                PresenceStatus oldStatus = currentStatus;
                PresenceStatus currentStatus = parentProvider.getJabberStatusEnum().getStatus(JabberStatusEnum.OFFLINE);
                clearLocalContactResources();

                OperationSetServerStoredAccountInfo accountInfoOpSet
                        = parentProvider.getOperationSet(OperationSetServerStoredAccountInfo.class);
                if (accountInfoOpSet != null) {
                    accountInfoOpSet.clearDetails();
                }

                updateAllStatus = true;
                fireProviderStatusChangeEvent(oldStatus, currentStatus);
                ssContactList.cleanup();

                if (xmppConnection != null) {
                    // Remove all subscription listeners upon de-registration
                    if (mRoster != null) {
                        mRoster.removeSubscribeListener(OperationSetPersistentPresenceJabberImpl.this);
                        mRoster.removePresenceEventListener(OperationSetPersistentPresenceJabberImpl.this);
                        mRoster.removeRosterListener(contactChangesListener);
                        mRoster = null;
                        logger.info("SubscribeListener and PresenceEventListener removed");
                    }

                    // vCardAvatarManager can be null for unRegistered account
                    if (vCardAvatarManager != null) {
                        vCardAvatarManager.removeVCardAvatarChangeListener(OperationSetPersistentPresenceJabberImpl.this);
                        userAvatarManager.removeAvatarListener(OperationSetPersistentPresenceJabberImpl.this);
                    }
                }
                handleSubsribeEvent = false;
                contactChangesListener = null;
                vCardAvatarManager = null;
                userAvatarManager = null;
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
        if (!contact.isResolved() || (contact instanceof VolatileContactJabberImpl
                && ((VolatileContactJabberImpl) contact).isPrivateMessagingContact()))
            return false;

        boolean eventFired = false;
        Map<FullJid, ContactResourceJabberImpl> resources = contact.getResourcesMap();

        // Do not obtain getRoster if we are not connected, or new Roster will be created, all the resources
        // that will be returned will be unavailable. As we are not connected if set remove all resources

        XMPPConnection xmppConnection = parentProvider.getConnection();
        if (xmppConnection == null || !xmppConnection.isConnected()) {
            if (removeUnavailable) {
                Iterator<Map.Entry<FullJid, ContactResourceJabberImpl>> iter = resources.entrySet().iterator();
                while (iter.hasNext()) {
                    Map.Entry<FullJid, ContactResourceJabberImpl> entry = iter.next();
                    iter.remove();
                    contact.fireContactResourceEvent(new ContactResourceEvent(contact,
                            entry.getValue(), ContactResourceEvent.RESOURCE_REMOVED));
                    eventFired = true;
                }
            }
            return eventFired;
        }

        List<Presence> it = mRoster.getPresences(contact.getJid().asBareJid());
        // Choose the resource which has the highest priority AND supports Jingle, if we have two
        // resources with same priority take the most available.
        for (Presence presence : it) {
            eventFired = updateResource(contact, null, presence) || eventFired;
        }

        if (!removeUnavailable)
            return eventFired;

        Set<FullJid> resourceKeys = resources.keySet();
        for (FullJid fullJid : resourceKeys) {
            if (!mRoster.getPresenceResource(fullJid).isAvailable()) {
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
    private boolean updateResource(ContactJabberImpl contact, FullJid fullJid, Presence presence)
    {
        if (fullJid == null)
            fullJid = presence.getFrom().asFullJidIfPossible();

        if (fullJid == null)
            return false;

        Resourcepart resource = fullJid.getResourceOrNull();
        if (resource != null && resource.length() > 0) {
            Map<FullJid, ContactResourceJabberImpl> resources = contact.getResourcesMap();
            ContactResourceJabberImpl contactResource = resources.get(fullJid);
            PresenceStatus newPresenceStatus
                    = OperationSetPersistentPresenceJabberImpl.jabberStatusToPresenceStatus(presence, parentProvider);

            if (contactResource == null) {
                contactResource = createResource(presence, fullJid, contact);
                resources.put(fullJid, contactResource);

                contact.fireContactResourceEvent(new ContactResourceEvent(contact, contactResource,
                        ContactResourceEvent.RESOURCE_ADDED));
                return true;
            }
            else {
                boolean oldIndicator = contactResource.isMobile();
                boolean newIndicator = mobileIndicator.isMobileResource(fullJid);
                int oldPriority = contactResource.getPriority();

                // update mobile indicator, as cabs maybe added after creating the resource for the contact
                contactResource.setMobile(newIndicator);

                contactResource.setPriority(presence.getPriority());
                if (oldPriority != contactResource.getPriority()) {
                    // priority has been updated so update and the mobile indicator before firing an event
                    mobileIndicator.resourcesUpdated(contact);
                }

                if (contactResource.getPresenceStatus().getStatus() != newPresenceStatus.getStatus()
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
    private boolean removeResource(ContactJabberImpl contact, FullJid fullJid)
    {
        Map<FullJid, ContactResourceJabberImpl> resources = contact.getResourcesMap();
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
        if (logger.isDebugEnabled()) {
            logger.debug("Dispatching contact status update event for " + contact.getJid()
                    + ": " + newStatus.getStatusName());
        }
        fireContactPresenceStatusChangeEvent(contact, contact.getParentContactGroup(),
                oldStatus, newStatus, resourceUpdated);
    }

    /**
     * Manage changes of statuses by resource.
     */
    class ContactChangesListener extends AbstractRosterListener
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
         * Received on resource status change.
         *
         * @param presence presence that has changed
         */
        @Override
        public void presenceChanged(Presence presence)
        {
            firePresenceStatusChanged(presence);
        }

        /**
         * Whether listener is currently storing presence events.
         */
        boolean isStoringPresenceEvents()
        {
            return storeEvents;
        }

        /*
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
        void firePresenceStatusChanged(final Presence presence)
        {
            /*
             * Smack block sending of presence update while roster loading is in progress.
             *
             * cmeng - just ignore and return to see if there is any side effect while process roster is in progress
             * seem to keep double copies and all unavailable triggered from roster - need to process??
             */
            if (storeEvents && storedPresences != null) {
                storedPresences.add(presence);
                return;
            }

            // Update resource from own presence instance and localContact if it is not null
            // Execute in new thread to avoid loading on Smack
            new Thread(new Runnable()
            {
                public void run()
                {
                    try {
                        Jid userJid = presence.getFrom();
                        OperationSetMultiUserChat mucOpSet = parentProvider.getOperationSet(OperationSetMultiUserChat.class);
                        if ((userJid != null) && (mucOpSet != null)) {
                            List<ChatRoom> chatRooms = mucOpSet.getCurrentlyJoinedChatRooms();
                            for (ChatRoom chatRoom : chatRooms) {
                                if (userJid.equals(chatRoom.getIdentifier())) {
                                    userJid = presence.getFrom();
                                    break;
                                }
                            }
                        }
                        logger.info("Smack presence update for: " + userJid + " - " + presence.getType());

                        // all contact statuses that are received from all its resources ordered by priority (higher first)
                        // and those with equal priorities order with the one that is most connected as first
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
                            // remove the status for this resource if we are online we will update its value with the new status
                            Resourcepart resource = presence.getFrom().getResourceOrEmpty();
                            for (Iterator<Presence> iter = userStats.iterator(); iter.hasNext(); ) {
                                Presence p = iter.next();
                                if (resource.equals(p.getFrom().getResourceOrEmpty()))
                                    iter.remove();
                            }
                        }
                        if (!jabberStatusToPresenceStatus(presence, parentProvider)
                                .equals(parentProvider.getJabberStatusEnum().getStatus(JabberStatusEnum.OFFLINE))) {
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

                        ContactJabberImpl sourceContact = ssContactList.findContactById(userJid);
                        if (sourceContact == null) {
                            logger.warn("Ignore own or no source contact found for id = " + userJid);
                            return;
                        }

                        // statuses may be the same and only change in status message
                        sourceContact.setStatusMessage(currentPresence.getStatus());
                        updateContactStatus(sourceContact, jabberStatusToPresenceStatus(currentPresence, parentProvider));
                    } catch (IllegalStateException | IllegalArgumentException ex) {
                        logger.error("Failed changing status", ex);
                    }
                }
            }).start();
        }
    }

    //================= Presence Subscription Handlers =========================
    /**
     * The authorization handler.
     */
    private AuthorizationHandler handler = null;

    /**
     * List of early subscriptions.
     */
    private Map<Jid, String> earlySubscriptions = new HashMap<>();

    /**
     * Adds auth handler.
     *
     * @param handler AUthorization handler with UI dialog
     */
    private synchronized void setHandler(AuthorizationHandler handler)
    {
        this.handler = handler;
        handleEarlySubscribeReceived();
    }

    /**
     * Handles early presence subscribe that were received.
     */
    private void handleEarlySubscribeReceived()
    {
        for (Jid from : earlySubscriptions.keySet()) {
            handleSubscribeReceived(from, earlySubscriptions.get(from));
        }
        earlySubscriptions.clear();
    }

    /**
     * Handles the received presence subscribe: run waiting for user response in different thread as this seems
     * to block the stanza dispatch thread and we don't receive anything till we unblock it
     *
     * @param fromJid sender in bareJid
     * @param displayName sender nickName for display in contact list
     */
    private void handleSubscribeReceived(final Jid fromJid, final String displayName)
    {
        new Thread(new Runnable()
        {
            public void run()
            {
                logger.info(fromJid + " wants to add you to its contact list");

                // buddy wants to add you to its roster contact
                ContactJabberImpl srcContact = ssContactList.findContactById(fromJid);
                Presence.Type responsePresenceType = null;

                if (srcContact == null) {
                    srcContact = createVolatileContact(fromJid, displayName);
                }
                else {
                    if (srcContact.isPersistent()) {
                        responsePresenceType = Presence.Type.subscribed;
                        logger.info("Auto accept for persistent contact: " + fromJid);
                    }
                }

                if (responsePresenceType == null) {
                    AuthorizationRequest req = new AuthorizationRequest();
                    AuthorizationResponse response = handler.processAuthorisationRequest(req, srcContact);

                    if (response != null) {
                        if (response.getResponseCode().equals(AuthorizationResponse.ACCEPT)) {
                            responsePresenceType = Presence.Type.subscribed;
                            // return request for presence subscription
                            try {
                                RosterUtil.askForSubscriptionIfRequired(mRoster, fromJid.asBareJid());
                            } catch (NotConnectedException | InterruptedException e) {
                                logger.error("Return presence subscription request failed: ", e);
                            } catch (SmackException.NotLoggedInException e) {
                                e.printStackTrace();
                            }
                            if (logger.isInfoEnabled())
                                logger.info("Sending Accepted Subscription");
                        }
                        else if (response.getResponseCode().equals(AuthorizationResponse.REJECT)) {
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
                responsePacket.setTo(fromJid);
                try {
                    parentProvider.getConnection().sendStanza(responsePacket);
                } catch (NotConnectedException | InterruptedException e) {
                    logger.error("Sending presence subscription response failed: ", e);
                }
            }
        }).start();
    }

    /**
     * Handle incoming presence subscription request; run on a different thread if manual approval to avoid blocking smack.
     *
     * @param from the JID requesting the subscription.
     * @param subscribeRequest the presence stanza used for the request.
     * @return an answer to the request for smack process, or <code>null</code>
     */
    @Override
    public SubscribeAnswer processSubscribe(Jid from, Presence subscribeRequest)
    {
        Jid fromJid = subscribeRequest.getFrom();
        /*
         * Approved presence subscription request if auto accept-all option is selected OR
         * if the contact is already persistent i.e. exist in DB
         */
        ContactJabberImpl srcContact = ssContactList.findContactById(fromJid);
        if (ConfigurationUtils.isPresenceSubscribeAuto()
                || ((srcContact != null) && srcContact.isPersistent())) {
            logger.info("Approve and return request if required for contact: " + fromJid);
            return SubscribeAnswer.ApproveAndAlsoRequestIfRequired;
        }

        String displayName = null;
        Nick ext = (Nick) subscribeRequest.getExtension(Nick.NAMESPACE);
        if (ext != null)
            displayName = ext.getName();

        logger.info("Subscription authorization request from: " + fromJid);
        synchronized (this) {
            // keep the request for later process when handler becomes ready
            if (handler == null) {
                earlySubscriptions.put(fromJid, displayName);
            }
            else {
                handleSubscribeReceived(fromJid, displayName);
            }
        }
        // Request smack roster to leave handling of the presence subscription request to user for manual approval
        return null;
    }

    /**
     * Handler own presence available events return from smack.
     *
     * @param address FullJid of own or the buddy subscribe to (sender)
     * @param availablePresence presence with available state
     */
    @Override
    public void presenceAvailable(final FullJid address, final Presence availablePresence)
    {
        // Keep a copy in storedPresences for later processing if isStoringPresenceEvents()
        if ((contactChangesListener != null) && contactChangesListener.isStoringPresenceEvents()) {
            contactChangesListener.addPresenceEvent(availablePresence);
        }

        // Update resource if receive from own presence and localContact is not null
        FullJid ownJid = parentProvider.getOurJID();
        if ((localContact != null) && (address != null) && address.isParentOf(ownJid)) {
            logger.info("Smack presence update own: = " + address);
            updateResource(localContact, address, availablePresence);
        }
    }

    @Override
    public void presenceUnavailable(FullJid address, Presence presence)
    {
    }

    @Override
    public void presenceError(Jid address, Presence errorPresence)
    {
    }

    /**
     * Buddy has approved the presence subscription request
     *
     * @param address FullJid of the the buddy subscribe to
     * @param subscribedPresence presence with subscribed state i.e. approved
     */
    @Override
    public void presenceSubscribed(BareJid address, Presence subscribedPresence)
    {
        Jid fromID = subscribedPresence.getFrom();
        if (handler == null) {
            logger.warn("No AuthorizationHandler to handle subscribed for " + fromID);
            return;
        }

        logger.info("Smack presence subscription accepted by: " + address);
        ContactJabberImpl contact = ssContactList.findContactById(fromID);
        AuthorizationResponse response = new AuthorizationResponse(AuthorizationResponse.ACCEPT, "");
        handler.processAuthorizationResponse(response, contact);
    }

    /**
     * Buddy acknowledge the presence unsubscribed reply
     *
     * @param address FullJid of the the buddy whom was subscribed to
     * @param unsubscribedPresence presence with unsubscribed state i.e. removed
     */
    @Override
    public void presenceUnsubscribed(BareJid address, Presence unsubscribedPresence)
    {
        Jid fromID = unsubscribedPresence.getFrom();
        logger.info("Smack presence subscription rejected by: " + address);

        if (handler == null) {
            logger.warn("No unsubscribed Authorization Handler for " + address);
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
    //================= End of Presence Subscription Handlers =========================

    /**
     * Runnable that resolves local contact list against the server side roster. This thread is the
     * one which will call getRoster for the first time. The thread wait until the roster
     * is loaded by the Smack Roster class
     */
    private class ServerStoredListInit implements Runnable, RosterLoadedListener
    {
        public void run()
        {
            // we are already being notified lets remove us from the rosterLoaded listener
            mRoster.removeRosterLoadedListener(this);

            // init the presenceChangeLister, RosterChangeLister and update contact list status
            mRoster.addRosterListener(contactChangesListener);
            ssContactList.init(contactChangesListener);

            // as we have dispatched the contact list and Roster is ready lets start the jingle nodes discovery
            parentProvider.startJingleNodesDiscovery();
        }

        /**
         * When rosterLoaded event is received we are ready to dispatch the contact list,
         * doing it in different thread to avoid blocking xmpp stanza receiving.
         *
         * @param roster the roster stanza
         */
        @Override
        public void onRosterLoaded(Roster roster)
        {
            mRoster = roster;
            logger.info("Roster loaded completed at startup!");
            if (!ssContactList.isRosterInitialized()) {
                new Thread(this, getClass().getName()).start();
            }
        }

        @Override
        public void onRosterLoadingFailed(Exception exception)
        {
            logger.warn("Roster loading failed at startup!");
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
     *
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
        ContactJabberImpl sourceContact = ssContactList.findContactById(userID);

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
        byte[] newAvatar;
        if (vCardInfo != null) {
            newAvatar = vCardInfo.getAvatar();
        }
        else {
            /*
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

    /*
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
        ContactJabberImpl sourceContact = ssContactList.findContactById(from);

        /*
         * If this contact is not yet in our contact list, then there is no need to manage this avatar update.
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
        return parentProvider.getAccountID().getAccountPropertyString(ProtocolProviderFactory.RESOURCE_PRIORITY
                + "_" + statusName.replaceAll(" ", "_").toUpperCase(Locale.US));
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
