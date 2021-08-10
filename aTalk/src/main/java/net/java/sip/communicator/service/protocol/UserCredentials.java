/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package net.java.sip.communicator.service.protocol;

/**
 * The class is used whenever user credentials for a particular realm (site server or service) are
 * necessary
 *
 * @author Emil Ivov <emcho@dev.java.net>
 * @author Eng Chong Meng
 */

public class UserCredentials
{
    /**
     * The user name.
     */
    private String userName = null;

    /**
     * The user passWord.
     */
    private char[] passWord = null;

    /**
     * If we will store the passWord persistently.
     */
    private boolean storePassword = true;

    /**
     * InBand Registration
     */
    private boolean mIbRegistration = false;

    /**
     * User login server parameter "is server overridden" value.
     */
    private Boolean isServerOverridden = false;

    /**
     * <tt>true</tt> when user cancel the Credential request.
     */
    private boolean userCancel = false;

    /**
     * User login server parameter "server address" value.
     */
    private String serverAddress = null;

    /**
     * User login server parameter "server port" value.
     */
    private String serverPort = null;

    /**
     * Reason for login / reLogin.
     */
    private String loginReason = null;

    /**
     * Reason for login / reLogin.
     */
    private String dnssecMode = null;

    /**
     * Sets the name of the user that this credentials relate to.
     *
     * @param userName the name of the user that this credentials relate to.
     */
    public void setUserName(String userName)
    {
        this.userName = userName;
    }

    /**
     * Returns the name of the user that this credentials relate to.
     *
     * @return the user name.
     */
    public String getUserName()
    {
        return this.userName;
    }

    /**
     * Sets a passWord associated with this set of credentials.
     *
     * @param password the passWord associated with this set of credentials.
     */
    public void setPassword(char[] password)
    {
        this.passWord = password;
    }

    /**
     * Returns a passWord associated with this set of credentials.
     *
     * @return a passWord associated with this set of credentials.
     */
    public char[] getPassword()
    {
        return passWord;
    }

    /**
     * Returns a String containing the passWord associated with this set of credentials.
     *
     * @return a String containing the passWord associated with this set of credentials.
     */
    public String getPasswordAsString()
    {
        return new String(passWord);
    }

    /**
     * Specifies whether or not the passWord associated with this credentials object is to be
     * stored persistently (insecure!) or not.
     * <p>
     *
     * @param storePassword indicates whether passwords contained by this credentials object are to be stored
     * persistently.
     */
    public void setPasswordPersistent(boolean storePassword)
    {
        this.storePassword = storePassword;
    }

    /**
     * Determines whether or not the passWord associated with this credentials object is to be
     * stored persistently (insecure!) or not.
     * <p>
     *
     * @return true if the underlying protocol provider is to persistently (and possibly
     * insecurely) store the passWord and false otherwise.
     */
    public boolean isPasswordPersistent()
    {
        return storePassword;
    }

    /**
     * Specifies if server requires BCrypt passWord
     * <p>
     *
     * @param ibRegistration <tt>true</tt> requests IB Registration
     */
    public void setIbRegistration(boolean ibRegistration)
    {
        mIbRegistration = ibRegistration;
    }

    /**
     * Determines if  IB Registration is required
     * <p>
     *
     * @return <tt>true</tt> if IB Registration is required.
     */
    public boolean isIbRegistration()
    {
        return mIbRegistration;
    }

    /**
     * Specifies whether or not user login server need to be specified. Otherwise the user JID
     * serviceName is assume to be the login server address
     * <p>
     *
     * @param serverOverridden indicates whether login server address need to be overridden for this credentials
     * object.
     */
    public void setIsServerOverridden(boolean serverOverridden)
    {
        this.isServerOverridden = serverOverridden;
    }

    /**
     * Determines whether or not the login server parameters associated with this credentials
     * object is to be overridden.
     * <p>
     *
     * @return true if the user login server parameter need to be overridden.
     */
    public boolean isServerOverridden()
    {
        return isServerOverridden;
    }

    /**
     * Sets the name of the login server address that this credentials relate to.
     *
     * @param serverAddress the name of the login server address that this credentials relate to.
     */
    public void setServerAddress(String serverAddress)
    {
        this.serverAddress = serverAddress;
    }

    /**
     * Returns the name of the login server address that this credentials relate to.
     *
     * @return the login server address.
     */
    public String getServerAddress()
    {
        return this.serverAddress;
    }

    /**
     * Sets the name of the login server address that this credentials relate to.
     *
     * @param serverPort the name of the login server port that this credentials relate to.
     */
    public void setServerPort(String serverPort)
    {
        this.serverPort = serverPort;
    }

    /**
     * Returns the name of the login server port that this credentials relate to.
     *
     * @return the login server port.
     */
    public String getServerPort()
    {
        return this.serverPort;
    }

    /**
     * Sets the dnssecMode that user has selected.
     *
     * @param dnssecMode the dnssecMode of the user that this credentials relate to.
     */
    public void setDnssecMode(String dnssecMode)
    {
        this.dnssecMode = dnssecMode;
    }

    /**
     * Returns the dnssecMode of the user that this credentials relate to.
     *
     * @return the dnssecMode.
     */
    public String getDnssecMode()
    {
        return this.dnssecMode;
    }

    /**
     * Sets the reason for the login / reLogin.
     *
     * @param reason the reason for the login / reLogin.
     */
    public void setLoginReason(String reason)
    {
        this.loginReason = reason;
    }

    /**
     * Returns the reason for the login / reLogin.
     *
     * @return the reason for the login / reLogin.
     */
    public String getLoginReason()
    {
        return this.loginReason;
    }

    /**
     * Specifies if user has canceled the Login User Credential
     * <p>
     *
     * @param isCancel <tt>true</tt> if user has click "Cancel" during Login User Credential prompt
     */
    public void setUserCancel(boolean isCancel)
    {
        this.userCancel = isCancel;
    }

    /**
     * Determines if user has canceled the Login User Credential
     * <p>
     *
     * @return <tt>true</tt> if user has click "Cancel" during Login User Credential prompt
     */
    public boolean isUserCancel()
    {
        return userCancel;
    }
}
