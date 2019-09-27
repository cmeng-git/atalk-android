/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package net.java.sip.communicator.service.protocol;

import net.java.sip.communicator.service.protocol.event.RegistrationStateChangeListener;

import org.jivesoftware.smack.XMPPConnection;

import java.util.*;

/**
 * The ProtocolProvider interface should be implemented by bundles that wrap Instant Messaging and
 * telephony protocol stacks. It gives the user interface a way to plug into these stacks and
 * receive notifications on status change and incoming calls, as well as deliver user requests for
 * establishing or ending calls, putting peers on hold and etc.
 * <p/>
 * An instance of a ProtocolProviderService corresponds to a particular user account and all
 * operations performed through a provider (sending messages, modifying contact lists, receiving
 * calls)would pertain to this particular user account.
 * <p/>
 * ProtocolProviderService instances are created through the provider factory. Each protocol
 * provider is assigned a unique AccountID instance that uniquely identifies it. Account id's for
 * different accounts are guaranteed to be different and in the same time the ID of a particular
 * account against a given service over any protocol will always be the same (so that we detect
 * attempts for creating the same account twice.)
 *
 * @author Emil Ivov
 * @author Eng Chong Meng
 */
public interface ProtocolProviderService
{
    /**
     * The name of the property containing the number of binds that a Protocol Provider Service
     * Implementation should execute in case a port is already bound to (each retry would be on a
     * new random port).
     */
    String BIND_RETRIES_PROPERTY_NAME = "protocol.BIND_RETRIES";

    /**
     * The default number of binds that a Protocol Provider Service Implementation should execute
     * in case a port is already bound to (each retry would be on a new random port).
     */
    int BIND_RETRIES_DEFAULT_VALUE = 50;

    /**
     * Starts the registration process. Connection details such as registration server, user
     * name/number are provided through the configuration service through implementation specific
     * properties.
     *
     * @param authority the security authority that will be used for resolving any security
     * challenges that may be returned during the registration or at any moment while we're
     * registered.
     * @throws OperationFailedException with the corresponding code it the registration fails
     * for some reason (e.g. a networking error or an implementation problem).
     */
    void register(SecurityAuthority authority)
            throws OperationFailedException;

    /**
     * Ends the registration of this protocol provider with the current registration service.
     *
     * @throws OperationFailedException with the corresponding code it the registration fails
     * for some reason (e.g. a networking error or an implementation problem).
     */
    void unregister()
            throws OperationFailedException;

    /**
     * Ends the registration of this protocol provider with the current registration service.
     *
     * @param userRequest is the unregister by user request.
     * @throws OperationFailedException with the corresponding code it the registration fails
     * for some reason (e.g. a networking error or an implementation problem).
     */
    void unregister(boolean userRequest)
            throws OperationFailedException;

    /**
     * Indicates whether or not this provider is registered
     *
     * @return true if the provider is currently registered and false otherwise.
     */
    boolean isRegistered();

    /**
     * Returns the <tt>XMPPConnection</tt>opened by this provider
     *
     * @return a reference to the <tt>XMPPConnection</tt> last opened by this provider.
     */
    public XMPPConnection getConnection();

    /**
     * Indicates whether or not this provider must registered when placing outgoing calls.
     *
     * @return true if the provider must be registered when placing a call.
     */
    boolean isRegistrationRequiredForCalling();

    /**
     * Returns the state of the registration of this protocol provider with the corresponding
     * registration service.
     *
     * @return ProviderRegistrationState
     */
    RegistrationState getRegistrationState();

    /**
     * Returns the short name of the protocol that the implementation of this provider is based
     * upon (like SIP, Jabber, ICQ/AIM, or others for example). If the name of the protocol has
     * been enumerated in ProtocolNames then the value returned by this method must be the same
     * as the one in ProtocolNames.
     *
     * @return a String containing the short name of the protocol this service is implementing
     * (most often that would be a name in ProtocolNames).
     */
    String getProtocolName();

    /**
     * Returns the protocol display name. This is the name that would be used by the GUI to display
     * the protocol name.
     *
     * @return a String containing the display name of the protocol this service is implementing
     */
    String getProtocolDisplayName();

    /**
     * Returns the protocol logo icon.
     *
     * @return the protocol logo icon
     */
    ProtocolIcon getProtocolIcon();

    /**
     * Registers the specified listener with this provider so that it would receive notifications
     * on changes of its state or other properties such as its local address and display name.
     *
     * @param listener the listener to register.
     */
    void addRegistrationStateChangeListener(RegistrationStateChangeListener listener);

    /**
     * Removes the specified listener.
     *
     * @param listener the listener to remove.
     */
    void removeRegistrationStateChangeListener(RegistrationStateChangeListener listener);

    /**
     * Returns an array containing all operation sets supported by the current implementation. When
     * querying this method users must be prepared to receive any subset of the OperationSet-s
     * defined by this service. They MUST ignore any OperationSet-s that they are not aware of and
     * that may be defined by future versions of this service. Such "unknown" OperationSet-s though
     * not encouraged, may also be defined by service implementors.
     *
     * @return a {@link Map} containing instances of all supported operation sets mapped against
     * their class names (e.g. <tt>OperationSetPresence.class.getName()</tt> associated with
     * a <tt>OperationSetPresence</tt> instance).
     */
    Map<String, OperationSet> getSupportedOperationSets();

    /**
     * Returns a collection containing all operation sets classes supported by the current
     * implementation. When querying this method users must be prepared to receive any subset of the
     * OperationSet-s defined by this service. They MUST ignore any OperationSet-s that they are not
     * aware of and that may be defined by future versions of this service. Such "unknown"
     * OperationSet-s though not encouraged, may also be defined by service implementors.
     *
     * @return a {@link Collection} containing instances of all supported operation set classes
     * (e.g. <tt>OperationSetPresence.class</tt>.
     */
    Collection<Class<? extends OperationSet>> getSupportedOperationSetClasses();

    /**
     * Returns the operation set corresponding to the specified class or <tt>null</tt> if this
     * operation set is not supported by the provider implementation.
     *
     * @param <T> the type which extends <tt>OperationSet</tt> and which is to be retrieved
     * @param opsetClass the <tt>Class</tt> of the operation set that we're looking for.
     * @return returns an OperationSet of the specified <tt>Class</tt> if the underlying
     * implementation supports it or null otherwise.
     */
    <T extends OperationSet> T getOperationSet(Class<T> opsetClass);

    /**
     * Makes the service implementation close all open sockets and release any resources that it
     * might have taken and prepare for shutdown/garbage collection.
     */
    void shutdown();

    /**
     * Returns the AccountID that uniquely identifies the account represented by this instance of
     * the ProtocolProviderService.
     *
     * @return the id of the account represented by this provider.
     */
    AccountID getAccountID();

    /**
     * Validates the given protocol specific contact identifier and returns an
     * error message if applicable and a suggested correction.
     *
     * @param contactId the contact identifier to validate
     * @param result Must be supplied as an empty a list. Implementors add
     * items:
     * <ol>
     * <li>is the error message if applicable
     * <li>a suggested correction. Index 1 is optional and can only
     * be present if there was a validation failure.
     * </ol>
     * @return true if the contact id is valid, false otherwise
     */
    boolean validateContactAddress(String contactId, List<String> result);

    /**
     * Indicate if the signaling transport of this protocol instance uses a secure (e.g. via TLS) connection.
     *
     * @return True when the connection is secured, false otherwise.
     */
    boolean isSignalingTransportSecure();

    /**
     * Returns the "transport" protocol of this instance used to carry the control channel for the
     * current protocol service.
     *
     * @return The "transport" protocol of this instance: UDP, TCP, TLS or UNKNOWN.
     */
    TransportProtocol getTransportProtocol();
}
