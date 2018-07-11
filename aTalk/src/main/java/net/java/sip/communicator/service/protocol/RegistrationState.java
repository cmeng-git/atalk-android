/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 * 
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package net.java.sip.communicator.service.protocol;

import org.jivesoftware.smack.ReconnectionManager;

/**
 * The class represents a set of states that a protocol provider may take while registering, logging
 * in, or signing on to a public service server, such as a SIP registrar, the ICQ/AIM login and
 * registration servers or a Jabber server. States are generally supposed to be appearing in the
 * following order:
 * <p>
 * Note that the order strongly depends on the particular protocol, which may also influence the
 * exact states that might actually be entered.
 * </p>
 * <p>
 * For more information on the particular states, please check the documentation for each of them.
 * </p>
 *
 * @author Emil Ivov
 * @author Eng Chong Meng
 */
public class RegistrationState
{
	/**
	 * The initial state of a protocol provider, assigned to it upon creation and before any
	 * registration action has been undertaken by the user.
	 */
	public static final RegistrationState INIT = new RegistrationState("Initial");

	/**
	 * A transition state indicating that registration has been undertaken but has not yet been confirmed by
     * the registrar server/service (after xmppConnection connected). The state generally occurs after the client
	 * hs undertaken action to completing the registration and the server is about to respond.
	 */
	public static final RegistrationState REGISTERING = new RegistrationState("Registering");

	/**
	 * The registrar service requires authentication and we are about to send one.
	 */
	public static final RegistrationState CHALLENGED_FOR_AUTHENTICATION
			= new RegistrationState("Challenged for authentication");

	/**
	 * In the process of authenticating. The state is entered when a protocol provider sends
	 * authentication info and waits for a confirmation
	 */
	public static final RegistrationState AUTHENTICATING = new RegistrationState("Authenticating");

	/**
	 * Representing any transition state after authentication is completed and before it has been
	 * completed. This state wouldn't make sense for many services, and would only be used with
	 * others such as ICQ/AIM for example.
	 */
	public static final RegistrationState FINALIZING_REGISTRATION
			= new RegistrationState("Finalizing Registration");

	/**
	 * Registration has completed successfully and we are currently signed on the registration service.
	 */
	public static final RegistrationState REGISTERED = new RegistrationState("Registered");

	/**
	 * Registration has failed for a technical reason, such as connection disruption for example.
	 * The state is use only when it is signed in via normal LoginManager to help cleanup states.
	 *
	 * Note: This state must never be reported during reconnection attempted by ReconnectionManager
	 * as all previously states setup will be destroyed.
	 */
	public static final RegistrationState CONNECTION_FAILED = new RegistrationState("Connection Failed");

	/**
	 * Connection has failed unexpectedly; ReconnectionManager is taking control to attempt sign
	 * in and resume with the server. All previous device state setup during normal sign in must
	 * not be destroyed as they will be reuse. RegistrationState#REGISTERED will only be reported
	 * when reconnection is completed.
	 *
	 * @see ReconnectionManager
	 */
	public static final RegistrationState RECONNECTING = new RegistrationState("Reconnecting");

	/**
	 * Registration has failed because of a problem with the authentication.
	 */
	public static final RegistrationState AUTHENTICATION_FAILED = new RegistrationState("Authentication Failed");

	/**
	 * Indicates that a protocol provider is currently updating its registration.
	 */
	public static final RegistrationState UPDATING_REGISTRATION = new RegistrationState("Updating Registration");

	/**
	 * The registration has expired.
	 */
	public static final RegistrationState EXPIRED = new RegistrationState("Expired");

	/**
	 * The Protocol Provider is being unregistered. Most probably due to a user request.
	 */
	public static final RegistrationState UNREGISTERING = new RegistrationState("Unregistering");

	/**
	 * The Protocol Provider is not registered. Most probably due to a unregistration.
	 */
	public static final RegistrationState UNREGISTERED = new RegistrationState("Unregistered");

	private final String statusString;

	private RegistrationState(String statusString)
	{
		this.statusString = statusString;
	}

	/**
	 * Returns a String representation of the provider state.
	 *
	 * @return a String representation of the state.
	 */
	public String getStateName()
	{
		return statusString;
	}

	/**
	 * Returns a String representation of the provider state.
	 *
	 * @return a String representation of the state.
	 */
	@Override
	public String toString()
	{
		return "RegistrationState = " + getStateName();
	}

	/**
	 * Returns true if the specified object is equal to this provider state.
	 *
	 * @param obj
	 * 		the object to compare this provider state with.
	 * @return true if the specified object represents the same state as this one.
	 */
	@Override
	public boolean equals(Object obj)
	{
		return (obj != null) && (obj instanceof RegistrationState)
				&& statusString.equals(((RegistrationState) obj).statusString);
	}

    @Override
    public int hashCode()
    {
        return statusString.hashCode();
    }
}
