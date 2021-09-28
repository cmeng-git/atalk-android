/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package net.java.sip.communicator.service.protocol;

/**
 * Implemented by the user interface, this interface allows a protocol provider to asynchronously
 * demand passwords necessary for authentication against various realms.
 * <p>
 * Or in other (simpler words) this is a callback or a hook that the UI would give a protocol
 * provider so that the protocol provider could requestCredentials() when necessary (when a password
 * is not available for a server, or once it has changed, or re-demand one after a faulty authentication)
 *
 * @author Emil Ivov
 * @author Yana Stamcheva
 */
public interface SecurityAuthority
{
    /**
     * Indicates that reason is unknown for authentication failure.
     */
    int REASON_UNKNOWN = -1;

    /**
     * Indicates that the reason for obtaining credentials is that an authentication is required.
     */
    int AUTHENTICATION_REQUIRED = 0;

    /**
     * Indicates that the reason for obtaining credentials is that the last time a wrong password
     * has been provided.
     */
    int WRONG_PASSWORD = 1;

    /**
     * Indicates that the reason for obtaining credentials is that the last time a wrong user name
     * has been provided.
     */
    int WRONG_USERNAME = 2;

    /**
     * Indicates that the reason for obtaining credentials is that the last login has an invalid authorization
     */
    int INVALID_AUTHORIZATION = 3;

    /**
     * Indicates that the reason for obtaining credentials is that the last connection has failed
     */
    int CONNECTION_FAILED = 4;

    /**
     * Indicates that the reason for obtaining credentials is that the last server name provided
     * cannot be resolved to any server ip
     */
    int NO_SERVER_FOUND = 5;

    /**
     * Indicates that the requested service is not available: sasl, incompatible authentication mechanism etc
     */
    int AUTHENTICATION_FAILED = 6;

    /**
     * Indicates that the reason for obtaining credentials is that the last login has failed server account registration
     */
    int AUTHENTICATION_FORBIDDEN = 7;

    /**
     * Indicates that the reason for obtaining credentials is that the last login is not authorized
     * - IBR registration required.
     */
    int NOT_AUTHORIZED = 8;

    /**
     * SASLError using EXTERNAL: not-authorized: Failed to get peer certificate
     */
    int SASL_ERROR_EXTERNAL = 9;

    /**
     * Indicates that reason for login failure is due to a policy violation.
     */
    int POLICY_VIOLATION = 10;

    /**
     * Indicates that reason for login failure is due to a policy violation.
     */
    int DNSSEC_NOT_ALLOWED = 11;

    /**
     * Indicates that reason for login failure is due to conflict e.g. multiple login instances
     */
    int CONFLICT = 12;

    /**
     * Indicates that reason for login failure is due to security exception: ssl connection failed
     */
    int SECURITY_EXCEPTION = 13;

    /**
     * Returns a UserCredentials object associated with the specified realm, by specifying the
     * reason of this operation.
     * <p>
     *
     * @param accountID The realm that the credentials are needed for.
     * @param defaultValues the values to propose the user by default
     * @param reasonCode indicates the reason for which we're obtaining the credentials.
     * @param isShowServerOption true is to request user for server option
     * @return The credentials associated with the specified realm or null if none could be obtained.
     */
    UserCredentials obtainCredentials(AccountID accountID, UserCredentials defaultValues,
            int reasonCode, Boolean isShowServerOption);

    /**
     * Returns a UserCredentials object associated with the specified realm, by specifying the
     * reason of this operation.
     * <p>
     *
     * @param accountID The realm that the credentials are needed for.
     * @param defaultValues the values to propose the user by default
     * @param isShowServerOption true is to request user for server option
     * @return The credentials associated with the specified realm or null if none could be obtained.
     */
    UserCredentials obtainCredentials(AccountID accountID, UserCredentials defaultValues, Boolean isShowServerOption);

    /**
     * Sets the userNameEditable property, which should indicate to the implementations of this
     * interface if the user name could be changed by user or not.
     *
     * @param isUserNameEditable indicates if the user name could be changed by user in the implementation of this
     * interface.
     */
    void setUserNameEditable(boolean isUserNameEditable);

    /**
     * Indicates if the user name is currently editable, i.e. could be changed by user or not.
     *
     * @return <code>true</code> if the user name could be changed, <code>false</code> - otherwise.
     */
    boolean isUserNameEditable();
}
