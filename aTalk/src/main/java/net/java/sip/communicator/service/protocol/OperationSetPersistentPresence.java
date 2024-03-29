/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package net.java.sip.communicator.service.protocol;

import net.java.sip.communicator.service.protocol.event.ServerStoredGroupListener;

import org.jxmpp.stringprep.XmppStringprepException;

/**
 * This interface is an extension of the presence operation set, meant to be implemented by
 * protocols that support server stored contact lists (like icq for example).
 * <b>Note:</b> Also register the persistent presence implementation for OperationSetPresence
 * support, since OperationSetPersistentPresence extends OperationSetPresence.
 * <p>
 * A server stored contact list is stored somewhere across the network and this interface allows GUI
 * and other plugins to use it in a way similar to the way they'd use a javax.swing.tree.TreeModel,
 * i.e. it would contain an initial number of members/children that is likely to change, dispatching
 * a series of events delivered through the <code>SubscriptionListener</code> and
 * <code>ServerStoredGroupChangeListener</code> interfaces.
 * <p>
 * The interfaces defines extended subscription methods that include an extra <code>parentGroup</code>
 * parameter. Simple subscribe and usubscribe operations defined by the parent
 * <code>OperationSetPresence</code> operation set, will still work, adding contacts to a default, root
 * group. to be used by GUI and other plugins the same way that they would use a
 *
 * @author Emil Ivov
 * @author Eng Chong Meng
 */
public interface OperationSetPersistentPresence extends OperationSetPresence
{
    /**
     * Persistently adds a subscription for the presence status of the contact corresponding to the
     * specified contactIdentifier to the top level group. Note that this method, unlike the
     * subscribe method in OperationSetPresence, is going the subscribe the specified contact in a
     * persistent manner or in other words, it will add it to a server stored contact list and thus
     * making the subscription for its presence status last along multiple registrations/login/signon.
     * <p>
     * Apart from an exception in the case of an immediate failure, the method won't return any
     * indication of success or failure. That would happen later on through a SubscriptionEvent
     * generated by one of the methods of the SubscriptionListener.
     *
     * @param contactIdentifier the contact whose status updates we are subscribing for.
     * @param pps the owner of the contact to be added to RootGroup.
     * @throws OperationFailedException with code NETWORK_FAILURE if subscribing fails due to errors experienced during
     * network communication
     * @throws IllegalArgumentException if <code>contact</code> is not a contact known to the underlying protocol provider
     * @throws IllegalStateException if the underlying protocol provider is not registered/signed on a public service.
     */
    void subscribe(ProtocolProviderService pps, String contactIdentifier)
            throws IllegalArgumentException, IllegalStateException, OperationFailedException, XmppStringprepException;

    /**
     * Persistently adds a subscription for the presence status of the contact corresponding to the
     * specified contactIdentifier and indicates that it should be added to the specified group of
     * the server stored contact list. Note that apart from an exception in the case of an immediate
     * failure, the method won't return any indication of success or failure. That would happen
     * later on through a SubscriptionEvent generated by one of the methods of the
     * SubscriptionListener.
     *
     * @param contactIdentifier the contact whose status updates we are subscribing for.
     * @param parent the parent group of the server stored contact list where the contact should be added.
     *
     * @throws OperationFailedException with code NETWORK_FAILURE if subscribing fails due to errors experienced during
     * network communication
     * @throws IllegalArgumentException if <code>contact</code> or <code>parent</code> are not a contact known to the underlying
     * protocol provider.
     * @throws IllegalStateException if the underlying protocol provider is not registered/signed on a service.
     */
    void subscribe(ContactGroup parent, String contactIdentifier)
            throws IllegalArgumentException, IllegalStateException, OperationFailedException, XmppStringprepException;

    /**
     * Persistently removes a subscription for the presence status of the specified contact. This
     * method has a persistent effect and the specified contact is completely removed from any
     * server stored contact lists.
     *
     * @param contact the contact whose status updates we are unsubscribing from.
     * @throws OperationFailedException with code NETWORK_FAILURE if unsubscribing fails due to errors experienced during
     * network communication
     * @throws IllegalArgumentException if <code>contact</code> is not a contact known to the underlying protocol provider
     * @throws IllegalStateException if the underlying protocol provider is not registered/signed on a service.
     */
    void unsubscribe(Contact contact)
            throws IllegalArgumentException, IllegalStateException, OperationFailedException;

    /**
     * Creates a group with the specified name and parent in the server stored contact list.
     *
     * @param groupName the name of the new group to create.
     * @param parent the group where the new group should be created
     * @throws OperationFailedException with code NETWORK_FAILURE if creating the group fails because of a network error.
     * @throws IllegalArgumentException if <code>parent</code> is not a contact known to the underlying protocol provider
     * @throws IllegalStateException if the underlying protocol provider is not registered/signed on a service.
     */
    void createServerStoredContactGroup(ContactGroup parent, String groupName)
            throws OperationFailedException;

    /**
     * Removes the specified group from the server stored contact list.
     *
     * @param group the group to remove.
     * @throws OperationFailedException with code NETWORK_FAILURE if deleting the group fails because of a network error.
     * @throws IllegalArgumentException if <code>parent</code> is not a contact known to the underlying protocol provider.
     * @throws IllegalStateException if the underlying protocol provider is not registered/signed on a service.
     */
    void removeServerStoredContactGroup(ContactGroup group)
            throws OperationFailedException;

    /**
     * Renames the specified group from the server stored contact list. This method would return
     * before the group has actually been renamed. A <code>ServerStoredGroupEvent</code> would be
     * dispatched once new name has been acknowledged by the server.
     *
     * @param group the group to rename.
     * @param newName the new name of the group.
     */
    void renameServerStoredContactGroup(ContactGroup group, String newName);

    /**
     * Removes the specified contact from its current parent and places it under <code>newParent</code>.
     *
     * @param contactToMove the <code>Contact</code> to move
     * @param newParent the <code>ContactGroup</code> where <code>Contact</code> would be placed.
     * @throws OperationFailedException when the operation didn't finished successfully.
     */
    void moveContactToGroup(Contact contactToMove, ContactGroup newParent)
            throws OperationFailedException;

    /**
     * Returns the root group of the server stored contact list. Most often this would be a dummy
     * group that user interface implementations may better not show.
     *
     * @return the root ContactGroup for the ContactList stored by this service.
     */
    ContactGroup getServerStoredContactListRoot();

    /**
     * Registers a listener that would receive events upon changes in server stored groups.
     *
     * @param listener a ServerStoredGroupChangeListener that would receive events upon group changes.
     */
    void addServerStoredGroupChangeListener(ServerStoredGroupListener listener);

    /**
     * Removes the specified group change listener so that it won't receive any further events.
     *
     * @param listener the ServerStoredGroupChangeListener to remove
     */
    void removeServerStoredGroupChangeListener(ServerStoredGroupListener listener);

    /**
     * Creates and returns a unresolved contact from the specified <code>address</code> and
     * <code>persistentData</code>. The method will not try to establish a network connection and
     * resolve the newly created Contact against the server. The protocol provider may will later
     * try and resolve the contact. When this happens the corresponding event would notify
     * interested subscription listeners.
     *
     * @param address an identifier of the contact that we'll be creating.
     * @param persistentData a String returned Contact's getPersistentData() method during a previous run and that
     * has been persistently stored locally.
     * @param parentGroup the group where the unresolved contact is supposed to belong to.
     * @return the unresolved <code>Contact</code> created from the specified <code>address</code> and
     * <code>persistentData</code>
     */
    Contact createUnresolvedContact(String address, String persistentData, ContactGroup parentGroup);

    /**
     * Creates and returns a unresolved contact group from the specified <code>address</code> and
     * <code>persistentData</code>. The method will not try to establish a network connection and
     * resolve the newly created <code>ContactGroup</code> against the server or the contact itself. The
     * protocol provider will later resolve the contact group. When this happens the corresponding
     * event would notify interested subscription listeners.
     *
     * @param groupUID an identifier, returned by ContactGroup's getGroupUID, that the protocol provider may
     * use in order to create the group.
     * @param persistentData a String returned ContactGroups's getPersistentData() method during a previous run and
     * that has been persistently stored locally.
     * @param parentGroup the group under which the new group is to be created or null if this is group directly
     * underneath the root.
     * @return the unresolved <code>ContactGroup</code> created from the specified <code>uid</code> and
     * <code>persistentData</code>
     */
    ContactGroup createUnresolvedContactGroup(String groupUID, String persistentData, ContactGroup parentGroup);

    /**
     * Sets the display name for <code>contact</code> to be <code>newName</code>.
     *
     * @param contact the <code>Contact</code> that we are renaming
     * @param newName a <code>String</code> containing the new display name for <code>metaContact</code>.
     * @throws IllegalArgumentException if <code>contact</code> is not an instance that belongs
     * to the underlying implementation.
     */
    void setDisplayName(Contact contact, String newName)
            throws IllegalArgumentException;

}
