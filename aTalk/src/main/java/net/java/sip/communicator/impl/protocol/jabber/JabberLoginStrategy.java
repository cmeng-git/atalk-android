/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 * 
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package net.java.sip.communicator.impl.protocol.jabber;

import net.java.sip.communicator.service.certificate.CertificateService;
import net.java.sip.communicator.service.protocol.*;

import org.jivesoftware.smack.*;
import org.jivesoftware.smack.tcp.XMPPTCPConnection;

import java.security.GeneralSecurityException;

import javax.net.ssl.*;

/**
 * Is responsible to configure the login mechanism for smack and later login to the XMPP server.
 *
 * @author Stefan Sieber
 */

interface JabberLoginStrategy
{
	/**
	 * Prepare the login by e.g. asking the user for his password.
	 *
	 * @param authority
	 * 		SecurityAuthority to obtain the password
	 * @param reasonCode
	 * 		reason why we're preparing for login
	 * @param reason
	 * 		the reason descriptive text why we're preparing for login
	 * @param isShowAlways
	 * 		<tt>true</tt> always show the credential prompt for user entry
	 * @return
	 * 		UserCredentials in case they need to be cached for this session
	 * 		(i.e. password is not persistent)
	 * @see SecurityAuthority
	 */
	UserCredentials prepareLogin(SecurityAuthority authority, int reasonCode, String reason,
			Boolean isShowAlways);

	/**
	 * Determines whether the login preparation was successful and the strategy is ready to start
	 * connecting.
	 *
	 * @return true if prepareLogin was successful.
	 */
	boolean loginPreparationSuccessful();

	/**
	 * Performs the login for the specified connection.
	 *
	 * @param connection
	 * 		Connection to login
	 * @param userName
	 * 		userName to be used for the login.
	 * @param resource
	 * 		the XMPP resource
	 * @return true to continue connecting, false to abort
	 */
	boolean login(XMPPTCPConnection connection, String userName, String resource)
			throws XMPPException, SmackException;

	boolean registerAccount(ProtocolProviderServiceJabberImpl pps, AccountID accountId)
			throws XMPPException, SmackException;

	/**
	 * Is TLS required for this login strategy / account?
	 *
	 * @return true if TLS is required
	 */
	boolean isTlsRequired();

	/**
	 * Creates an SSLContext to use for the login strategy.
	 *
	 * @param certificateService
	 * 		certificate service to retrieve the ssl context
	 * @param trustManager
	 * 		Trust manager to use for the context
	 * @return the SSLContext
	 */
	SSLContext createSslContext(CertificateService certificateService,
			X509TrustManager trustManager)
			throws GeneralSecurityException;
}
