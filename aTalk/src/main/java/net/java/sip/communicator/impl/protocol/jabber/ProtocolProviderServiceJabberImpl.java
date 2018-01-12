/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 * 
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package net.java.sip.communicator.impl.protocol.jabber;

import android.text.TextUtils;

import net.java.sip.communicator.impl.protocol.jabber.extensions
		.ConferenceDescriptionPacketExtension;
import net.java.sip.communicator.impl.protocol.jabber.extensions.carbon.CarbonPacketExtension;
import net.java.sip.communicator.impl.protocol.jabber.extensions.coin.*;
import net.java.sip.communicator.impl.protocol.jabber.extensions.colibri.*;
import net.java.sip.communicator.impl.protocol.jabber.extensions.inputevt.*;
import net.java.sip.communicator.impl.protocol.jabber.extensions.jibri.*;
import net.java.sip.communicator.impl.protocol.jabber.extensions.jingle.*;
import net.java.sip.communicator.impl.protocol.jabber.extensions.jingleinfo.*;
import net.java.sip.communicator.impl.protocol.jabber.extensions.thumbnail.ThumbnailElement;
import net.java.sip.communicator.impl.protocol.jabber.extensions.whiteboard.*;
import net.java.sip.communicator.service.certificate.CertificateService;
import net.java.sip.communicator.service.dns.DnssecException;
import net.java.sip.communicator.service.protocol.*;
import net.java.sip.communicator.service.protocol.event.RegistrationStateChangeEvent;
import net.java.sip.communicator.service.protocol.jabberconstants.JabberStatusEnum;
import net.java.sip.communicator.util.*;
import net.java.sip.communicator.util.Logger;

import org.atalk.android.*;
import org.atalk.android.gui.login.LoginSynchronizationPoint;
import org.atalk.crypto.omemo.AndroidOmemoService;
import org.atalk.persistance.DatabaseBackend;
import org.atalk.service.configuration.ConfigurationService;
import org.atalk.service.neomedia.SrtpControlType;
import org.atalk.util.*;
import org.jivesoftware.smack.*;
import org.jivesoftware.smack.SmackException.*;
import org.jivesoftware.smack.XMPPException.*;
import org.jivesoftware.smack.packet.*;
import org.jivesoftware.smack.packet.XMPPError.Condition;
import org.jivesoftware.smack.provider.*;
import org.jivesoftware.smack.proxy.ProxyInfo;
import org.jivesoftware.smack.roster.Roster;
import org.jivesoftware.smack.roster.rosterstore.*;
import org.jivesoftware.smack.tcp.*;
import org.jivesoftware.smack.util.TLSUtils;
import org.jivesoftware.smackx.avatar.AvatarManager;
import org.jivesoftware.smackx.avatar.useravatar.UserAvatarManager;
import org.jivesoftware.smackx.avatar.useravatar.packet.*;
import org.jivesoftware.smackx.avatar.useravatar.provider.*;
import org.jivesoftware.smackx.avatar.vcardavatar.VCardAvatarManager;
import org.jivesoftware.smackx.avatar.vcardavatar.packet.VCardTempXUpdate;
import org.jivesoftware.smackx.avatar.vcardavatar.provider.VCardTempXUpdateProvider;
import org.jivesoftware.smackx.bob.packet.BoB;
import org.jivesoftware.smackx.bob.provider.BoBProvider;
import org.jivesoftware.smackx.bytestreams.ibb.InBandBytestreamManager;
import org.jivesoftware.smackx.bytestreams.ibb.packet.DataPacketExtension;
import org.jivesoftware.smackx.bytestreams.socks5.Socks5BytestreamManager;
import org.jivesoftware.smackx.bytestreams.socks5.packet.Bytestream;
import org.jivesoftware.smackx.caps.EntityCapsManager;
import org.jivesoftware.smackx.caps.packet.CapsExtension;
import org.jivesoftware.smackx.caps.provider.CapsExtensionProvider;
import org.jivesoftware.smackx.chatstates.packet.ChatStateExtension;
import org.jivesoftware.smackx.delay.packet.DelayInformation;
import org.jivesoftware.smackx.delay.provider.DelayInformationProvider;
import org.jivesoftware.smackx.disco.ServiceDiscoveryManager;
import org.jivesoftware.smackx.disco.packet.*;
import org.jivesoftware.smackx.filetransfer.FileTransferNegotiator;
import org.jivesoftware.smackx.iqregisterx.packet.Registration;
import org.jivesoftware.smackx.iqregisterx.provider.*;
import org.jivesoftware.smackx.iqversion.VersionManager;
import org.jivesoftware.smackx.message_correct.element.MessageCorrectExtension;
import org.jivesoftware.smackx.muc.packet.MUCInitialPresence;
import org.jivesoftware.smackx.nick.packet.Nick;
import org.jivesoftware.smackx.omemo.util.OmemoConstants;
import org.jivesoftware.smackx.ping.PingManager;
import org.jivesoftware.smackx.si.packet.StreamInitiation;
import org.jivesoftware.smackx.vcardtemp.packet.VCard;
import org.jivesoftware.smackx.xhtmlim.XHTMLManager;
import org.jxmpp.jid.*;
import org.jxmpp.jid.impl.JidCreate;
import org.jxmpp.stringprep.XmppStringprepException;
import org.jxmpp.util.XmppStringUtils;
import org.osgi.framework.ServiceReference;
import org.xmlpull.v1.XmlPullParserException;
import org.xmpp.jnodes.smack.*;

import java.io.*;
import java.lang.reflect.Field;
import java.math.BigInteger;
import java.net.*;
import java.security.*;
import java.security.cert.*;
import java.text.ParseException;
import java.util.*;

import javax.net.SocketFactory;
import javax.net.ssl.*;

/**
 * An implementation of the protocol provider service over the Jabber protocol
 *
 * @author Damian Minkov
 * @author Symphorien Wanko
 * @author Lyubomir Marinov
 * @author Yana Stamcheva
 * @author Emil Ivov
 * @author Hristo Terezov
 * @author Eng Chong Meng
 */
public class ProtocolProviderServiceJabberImpl extends AbstractProtocolProviderService
{
	/**
	 * Logger of this class
	 */
	private static final Logger logger = Logger.getLogger(ProtocolProviderServiceJabberImpl.class);

	/**
	 * Jingle's Discovery Info common URN.
	 */
	public static final String URN_XMPP_JINGLE = JingleIQ.NAMESPACE;

	/**
	 * Jingle's Discovery Info URN for RTP support.
	 */
	public static final String URN_XMPP_JINGLE_RTP = RtpDescriptionPacketExtension.NAMESPACE;

	/**
	 * Jingle's Discovery Info URN for RTP support with audio.
	 */
	public static final String URN_XMPP_JINGLE_RTP_AUDIO = "urn:xmpp:jingle:apps:rtp:audio";

	/**
	 * Jingle's Discovery Info URN for RTP support with video.
	 */
	public static final String URN_XMPP_JINGLE_RTP_VIDEO = "urn:xmpp:jingle:apps:rtp:video";

	/**
	 * Jingle's Discovery Info URN for ZRTP support with RTP.
	 */
	public static final String URN_XMPP_JINGLE_RTP_ZRTP = ZrtpHashPacketExtension.NAMESPACE;

	/**
	 * Jingle's Discovery Info URN for ICE_UDP transport support.
	 */
	public static final String URN_XMPP_JINGLE_RAW_UDP_0
			= RawUdpTransportPacketExtension.NAMESPACE;

	/**
	 * Jingle's Discovery Info URN for ICE_UDP transport support.
	 */
	public static final String URN_XMPP_JINGLE_ICE_UDP_1
			= IceUdpTransportPacketExtension.NAMESPACE;

	/**
	 * Jingle's Discovery Info URN for Jingle Nodes support.
	 */
	public static final String URN_XMPP_JINGLE_NODES = "http://jabber.org/protocol/jinglenodes";

	/**
	 * Jingle's Discovery Info URN for "XEP-0251: Jingle Session Transfer" support.
	 */
	public static final String URN_XMPP_JINGLE_TRANSFER_0 = TransferPacketExtension.NAMESPACE;

	/**
	 * Jingle's Discovery Info URN for
	 * XEP-0298: Delivering Conference Information to Jingle Participants (Coin)
	 */
	public static final String URN_XMPP_JINGLE_COIN = "urn:xmpp:coin";

	/**
	 * Jingle's Discovery Info URN for &quot;XEP-0320: Use of DTLS-SRTP in Jingle Sessions&quot;.
	 */
	public static final String URN_XMPP_JINGLE_DTLS_SRTP = "urn:xmpp:jingle:apps:dtls:0";

	/**
	 * Discovery Info URN for classic RFC3264-style Offer/Answer negotiation with no support for
	 * Trickle ICE and low tolerance to transport/payload separation. Defined in XEP-0176
	 */
	public static final String URN_IETF_RFC_3264 = "urn:ietf:rfc:3264";

	/**
	 * http://xmpp.org/extensions/xep-0092.html Software Version.
	 */
	// Used in JVB
	@SuppressWarnings("unused")
	public static final String URN_XMPP_IQ_VERSION = "jabber:iq:version";

	/**
	 * Jingle's Discovery Info URN for "XEP-0294: Jingle RTP Header Extensions Negotiation"
	 * support.
	 */
	public static final String URN_XMPP_JINGLE_RTP_HDREXT
			= "urn:xmpp:jingle:apps:rtp:rtp-hdrext:0";

	/**
	 * URN for XEP-0077 inband registration
	 */
	public static final String URN_REGISTER = "jabber:iq:register";

	/**
	 * The name of the property under which the user may specify if the desktop streaming or
	 * sharing should be disabled.
	 */
	private static final String IS_DESKTOP_STREAMING_DISABLED = "protocol.jabber.DESKTOP_STREAMING_DISABLED";

	/**
	 * The name of the property under which the user may specify if audio/video calls should be
	 * disabled.
	 */
	private static final String IS_CALLING_DISABLED = "protocol.jabber.CALLING_DISABLED";

	/**
	 * Smack packet maximum reply timeout - Smack will immediately return on reply or until timeout
	 * before issues exception. Need this to take care for some servers response on some packages
	 * e.g. disco#info (30 seconds). Also on some slow client e.g. Samsung S3 takes up to 30
	 * Sec to response to sasl authentication challenge on first login
	 */
	public static final int SMACK_PACKET_REPLY_TIMEOUT = 50000;  // 50 seconds

	/**
	 * Smack packet reply default timeout.
	 */
	public static final int SMACK_PACKET_REPLY_DEFAULT_TIMEOUT = 5000;

	/**
	 * Property for vcard reply timeout. Time to wait before we think vcard retrieving has
	 * timeout, default value of smack is 5000 (5 sec.).
	 */
	public static final String VCARD_REPLY_TIMEOUT_PROPERTY
			= "protocol.jabber.VCARD_REPLY_TIMEOUT";

	/**
	 * XMPP signaling DSCP configuration property name.
	 */
	private static final String XMPP_DSCP_PROPERTY = "protocol.jabber.XMPP_DSCP";

	/**
	 * Indicates if user search is disabled.
	 */
	private static final String IS_USER_SEARCH_ENABLED_PROPERTY = "USER_SEARCH_ENABLED";

	private static final String DEFAULT_RESOURCE = "atalk";

	/**
	 * Used to connect to a XMPP server.
	 */
	private XMPPTCPConnection mConnection = null;

	/**
	 * The socket address of the XMPP server.
	 */
	private InetSocketAddress address;

	/**
	 * Indicates whether or not the provider is initialized and ready for use.
	 */
	private boolean isInitialized = false;

	/**
	 * We use this to lock access to initialization.
	 */
	private final Object initializationLock = new Object();

	/**
	 * The identifier of the account that this provider represents.
	 */
	private AccountID mAccountID = null;

	/**
	 * Used when we need to re-register
	 */
	private SecurityAuthority mAuthority = null;

	/**
	 * The resource we will use when connecting during this run.
	 */
	private String resource = null;

	/**
	 * The icon corresponding to the jabber protocol.
	 */
	private ProtocolIconJabberImpl jabberIcon;

	private boolean isDesktopSharingEnable = false;

	/**
	 * Persistent Storage for Roster Versioning support.
	 */
	private File rosterStoreDirectory;

	/**
	 * Persistent Storage directory for Avatar.
	 */
	private static File avatarStoreDirectory;

	/**
	 * A set of features supported by our Jabber implementation. In general, we add new feature(s)
	 * when we add new operation sets.
	 * (see xep-0030 : http://www.xmpp.org/extensions/xep-0030.html#info).
	 * Example : to tell the world that we support jingle, we simply have to do :
	 * supportedFeatures.add("http://www.xmpp.org/extensions/xep-0166.html#ns"); Beware there is no
	 * canonical mapping between op set and jabber features (op set is a SC "concept"). This means
	 * that one op set in SC can correspond to many jabber features. It is also possible that there
	 * is no jabber feature corresponding to a SC op set or again, we can currently support some
	 * features which do not have a specific op set in SC (the mandatory feature :
	 * http://jabber.org/protocol/disco#info is one example). We can find features corresponding to
	 * op set in the xep(s) related to implemented functionality.
	 */
	private final List<String> supportedFeatures = new ArrayList<>();

	/**
	 * The <tt>ServiceDiscoveryManager</tt> is responsible for advertising
	 * <tt>supportedFeatures</tt> when asked by a remote client. It can also be used to query
	 * remote clients for supported features.
	 */
	private ScServiceDiscoveryManager discoveryManager = null;

	private AndroidOmemoService androidOmemoService = null;

	/**
	 * The <tt>OperationSetContactCapabilities</tt> of this <tt>ProtocolProviderService</tt> which
	 * is the service-public counterpart of {@link #discoveryManager}.
	 */
	private OperationSetContactCapabilitiesJabberImpl opsetContactCapabilities;

	/**
	 * The statuses.
	 */
	private JabberStatusEnum jabberStatusEnum;

	/**
	 * The service we use to interact with user.
	 */
	private CertificateService guiVerification;

	/**
	 * Used with tls connecting when certificates are not trusted and we ask the user to confirm.
	 * When some timeout expires connect method returns, and we use abortConnecting to
	 * abort further execution cause after user chooses we make further processing from there.
	 */
	private boolean abortConnecting = false;

	/**
	 * Flag indicating are we currently executing connectAndLogin method.
	 */
	private boolean inConnectAndLogin = false;

	/**
	 * Flag indicates that the last getJitsiVideobridge returns NoResponseException
	 *
	 * @see #getJitsiVideobridge()
	 */
	private boolean isLastVbNoResponse = false;

	/**
	 * Instant of OperationSetPersistentPresent
	 */
	private OperationSetPersistentPresence OpSetPP = null;

	/**
	 * Object used to synchronize the flag inConnectAndLogin.
	 */
	private final Object connectAndLoginLock = new Object();

	/**
	 * If an event occurs during login we fire it at the end of the login process (at the end of
	 * connectAndLogin method).
	 */
	private RegistrationStateChangeEvent eventDuringLogin;

	/**
	 * Listens for connection closes or errors.
	 */
	private JabberConnectionListener connectionListener;

	/**
	 * The details of the proxy we are using to connect to the server (if any)
	 */
	private ProxyInfo proxy;

	/**
	 * State for connect and login state.
	 */
	private enum ConnectState
	{
		/**
		 * Abort any further connecting.
		 */
		ABORT_CONNECTING,
		/**
		 * Continue trying with next address.
		 */
		CONTINUE_TRYING,
		/**
		 * Stop trying we succeeded or just have a final state for the whole connecting procedure.
		 */
		STOP_TRYING
	}

	/**
	 * Jingle Nodes service.
	 */
	private SmackServiceNode jingleNodesServiceNode = null;

	/**
	 * Synchronization object to monitor jingle nodes auto discovery.
	 */
	private final Object jingleNodesSyncRoot = new Object();

	/**
	 * Stores user credentials for local use if user hasn't stored its password.
	 */
	private UserCredentials userCredentials = null;

	/**
	 * XEP-0199: XMPP Ping
	 * The default ping interval in seconds used by PingManager. The Smack default is 30 minutes.
	 * See {@link #initSmackDefaultSettings()}
	 */
	private static int defaultPingInterval = 60 * 30;  // 30 minutes

	public DatabaseBackend databaseBackend;

	/**
	 * Set to success if protocol provider has successfully connected to the server.
	 */
	protected LoginSynchronizationPoint<XMPPException> xmppConnected;

	/**
	 * Set to success if account has registered on server via inBand Registration.
	 */
	public LoginSynchronizationPoint<XMPPException> accountIBRegistered;

	/**
	 * Set to success if account has authenticated with the server.
	 */
	protected LoginSynchronizationPoint<XMPPException> accountAuthenticated;

	// load xmpp manager classes
	static {
		if (OSUtils.IS_ANDROID)
			loadJabberServiceClasses();
	}

	/**
	 * An <tt>OperationSet</tt> that allows access to connection information used by the protocol
	 * provider.
	 */
	private class OperationSetConnectionInfoJabberImpl implements OperationSetConnectionInfo
	{
		/**
		 * @return The XMPP server address.
		 */
		@Override
		public InetSocketAddress getServerAddress()
		{
			return address;
		}
	}

	/**
	 * Returns the state of the account login state of this protocol provider
	 * Note: RegistrationState is not inBand Registration
	 *
	 * @return the <tt>RegistrationState</tt> that this provider is currently in or null in case it
	 * is in a unknown state.
	 */
	public RegistrationState getRegistrationState()
	{
		if (mConnection == null)
			return RegistrationState.UNREGISTERED;
		else if (mConnection.isConnected()
				&& mConnection.isAuthenticated()
				&& !mConnection.isDisconnectedButSmResumptionPossible())
			return RegistrationState.REGISTERED;
		else
			return RegistrationState.UNREGISTERED;
	}

	/**
	 * Return the certificate verification service impl.
	 *
	 * @return the CertificateVerification service.
	 */
	CertificateService getCertificateVerificationService()
	{
		if (guiVerification == null) {
			ServiceReference guiVerifyReference = JabberActivator.getBundleContext()
					.getServiceReference(CertificateService.class.getName());
			if (guiVerifyReference != null) {
				guiVerification = ((CertificateService) JabberActivator.getBundleContext()
						.getService(guiVerifyReference));
			}
		}
		return guiVerification;
	}

	/**
	 * Starts the registration process. Connection details such as registration server, user
	 * name/number are provided through the configuration service through implementation specific
	 * properties.
	 *
	 * @param authority
	 * 		the security authority that will be used for resolving any security challenges that
	 * 		may be returned during the registration or at any moment while we're registered.
	 * @throws OperationFailedException
	 * 		with the corresponding code it the registration fails for some reason
	 * 		(e.g. a networking error or an implementation problem).
	 */
	public void register(final SecurityAuthority authority)
			throws OperationFailedException
	{
		if (authority == null)
			throw new IllegalArgumentException("The register method needs a valid non-null"
					+ " authority impl in order to be able to retrieve passwords.");

		mAuthority = authority;
		try {
			// reset states
			abortConnecting = false;

			// indicate we have started connectAndLogin process
			synchronized (connectAndLoginLock) {
				inConnectAndLogin = true;
			}
			String loginReason = "User Authentication Required!";
			initializeConnectAndLogin(authority, SecurityAuthority.AUTHENTICATION_REQUIRED,
					loginReason, false);
		}
		catch (XMPPException | SmackException ex) {
			logger.error("Error registering: ", ex);
			eventDuringLogin = null;
			fireRegistrationStateChanged(ex);
		} finally {
			synchronized (connectAndLoginLock) {
				// If an error has occurred during login, only fire it here in order to avoid a
				// deadlock which occurs in reconnect plugin. The deadlock is because we fired an
				// event during login process and have locked initializationLock; and we cannot
				// unregister from reconnect, because unregister method also needs this lock.
				if (eventDuringLogin != null) {
					RegistrationState newState = eventDuringLogin.getNewState();
					if (newState.equals(RegistrationState.CONNECTION_FAILED)
							|| newState.equals(RegistrationState.UNREGISTERED)) {
						disconnectAndCleanConnection();
					}
					fireRegistrationStateChanged(eventDuringLogin.getOldState(), newState,
							eventDuringLogin.getReasonCode(), eventDuringLogin.getReason());
					eventDuringLogin = null;
				}
				inConnectAndLogin = false;
			}
		}
	}

	/**
	 * Connect and login again to the server.
	 *
	 * @param authReasonCode
	 * 		indicates the reason of the re-authentication.
	 */
	private void reRegister(int authReasonCode, String loginReason)
	{
		try {
			logger.info("SMACK: Trying to re-register account!");

			// set to indicate the account has not registered during the registration process
			this.unregisterInternal(false);
			// reset states
			abortConnecting = false;

			// indicate we started connectAndLogin process
			synchronized (connectAndLoginLock) {
				inConnectAndLogin = true;
			}
			initializeConnectAndLogin(mAuthority, authReasonCode, loginReason, true);
		}
		catch (OperationFailedException ex) {
			logger.error("Error reRegistering: ", ex);
			eventDuringLogin = null;
			disconnectAndCleanConnection();
			fireRegistrationStateChanged(getRegistrationState(),
					RegistrationState.CONNECTION_FAILED,
					RegistrationStateChangeEvent.REASON_INTERNAL_ERROR, null);
		}
		catch (XMPPException | SmackException ex) {
			logger.error("Error ReRegistering: ", ex);
			eventDuringLogin = null;
			fireRegistrationStateChanged(ex);
		} finally {
			synchronized (connectAndLoginLock) {
				// If an error has occurred during login, only fire it here in order to avoid a
				// deadlock which occurs in reconnect plugin. The deadlock is because we fired an
				// event during login process and have locked initializationLock; and we cannot
				// unregister from reconnect, because unregister method also needs this lock.
				if (eventDuringLogin != null) {
					RegistrationState newState = eventDuringLogin.getNewState();
					if (newState.equals(RegistrationState.CONNECTION_FAILED)
							|| newState.equals(RegistrationState.UNREGISTERED))
						disconnectAndCleanConnection();

					fireRegistrationStateChanged(eventDuringLogin.getOldState(), newState,
							eventDuringLogin.getReasonCode(), eventDuringLogin.getReason());
					eventDuringLogin = null;
				}
				inConnectAndLogin = false;
			}
		}
	}

	/**
	 * Indicates if the XMPP transport channel is using a TLS secured socket.
	 *
	 * @return True when TLS is used, false otherwise.
	 */
	public boolean isSignalingTransportSecure()
	{
		return (mConnection != null) && mConnection.isSecureConnection();
	}

	/**
	 * Returns the "transport" protocol of this instance used to carry the control channel for the
	 * current protocol service.
	 *
	 * @return The "transport" protocol of this instance: TCP, TLS or UNKNOWN.
	 */
	public TransportProtocol getTransportProtocol()
	{
		// Without a connection, there is no transport available.
		if (mConnection != null && mConnection.isConnected()) {
			// Transport using a secure connection.
			if (mConnection.isSecureConnection()) {
				return TransportProtocol.TLS;
			}
			// Transport using a unsecured connection.
			return TransportProtocol.TCP;
		}
		return TransportProtocol.UNKNOWN;
	}

	/**
	 * Connect and login to the server
	 *
	 * @param authority
	 * 		SecurityAuthority
	 * @param reasonCode
	 * 		the authentication reason code. Indicates the reason of this authentication.
	 * @throws XMPPException
	 * 		if we cannot connect to the server - network problem
	 * @throws OperationFailedException
	 * 		if login parameters as server port are not correct
	 */
	private void initializeConnectAndLogin(SecurityAuthority authority, int reasonCode,
			String loginReason, Boolean isShowAlways)
			throws XMPPException, SmackException, OperationFailedException
	{
		synchronized (initializationLock) {
			// if a thread is waiting for initializationLock and enters, lets check whether one
			// has already tried login and have succeeded. Avoid duplicate connections"
			if (isRegistered())
				return;

			JabberLoginStrategy loginStrategy = createLoginStrategy();
			userCredentials
					= loginStrategy.prepareLogin(authority, reasonCode, loginReason, isShowAlways);
			if (!loginStrategy.loginPreparationSuccessful()
					|| ((userCredentials != null) && userCredentials.isUserCancel()))
				return;

			String serviceName = mAccountID.getService();
			loadResource();
			loadProxy();

			ConnectState state;
			boolean[] hadDnsSecException = new boolean[]{false};

			// try connecting with auto-detection if enabled
			boolean isServerOverridden = mAccountID.getAccountPropertyBoolean(
					ProtocolProviderFactory.IS_SERVER_OVERRIDDEN, false);

			if (!isServerOverridden) {
				state = connectUsingSRVRecords(serviceName, serviceName, hadDnsSecException,
						loginStrategy);
				if (hadDnsSecException[0]) {
					setDnssecLoginFailure();
					return;
				}
				if ((state == ConnectState.ABORT_CONNECTING)
						|| (state == ConnectState.STOP_TRYING))
					return;
			}

			// check for custom xmpp domain which will check for SRV records for server addresses
			// cmeng - value not defined currently for CUSTOM_XMPP_DOMAIN server login
			String customXMPPDomain = mAccountID.getAccountPropertyString(
					ProtocolProviderFactory.CUSTOM_XMPP_DOMAIN);

			if (customXMPPDomain != null && !hadDnsSecException[0]) {
				logger.info("Connect using custom xmpp domain: " + customXMPPDomain);
				state = connectUsingSRVRecords(customXMPPDomain, serviceName, hadDnsSecException,
						loginStrategy);

				logger.info("state for connectUsingSRVRecords: " + state);
				if (hadDnsSecException[0]) {
					setDnssecLoginFailure();
					return;
				}
				if ((state == ConnectState.ABORT_CONNECTING)
						|| (state == ConnectState.STOP_TRYING))
					return;
			}

			// connect with the given xmpp server name in the preference settings
			String serverAddress
					= mAccountID.getAccountPropertyString(ProtocolProviderFactory.SERVER_ADDRESS,
					mAccountID.getService());
			int serverPort
					= mAccountID.getAccountPropertyInt(ProtocolProviderFactory.SERVER_PORT, 5222);

			InetSocketAddress[] isAddresses = null;
			try {
				isAddresses = NetworkUtils.getAandAAAARecords(serverAddress, serverPort);
			}
			catch (ParseException e) {
				logger.error("Unable to resolve Domain Name: ", e);
			}
			catch (DnssecException e) {
				logger.error("DNSSEC failure for overridden server", e);
				setDnssecLoginFailure();
				return;
			}

			if (isAddresses == null || isAddresses.length == 0) {
				String noServerFound = "Remote server not found - unable to resolve " +
						"InetSocketAddress!";
				logger.error(noServerFound);
				eventDuringLogin = null;

				fireRegistrationStateChanged(getRegistrationState(),
						RegistrationState.CONNECTION_FAILED,
						RegistrationStateChangeEvent.REASON_SERVER_NOT_FOUND, noServerFound);

				// Throw XMPPError to show re-login dialog; and do not just send notification
				XMPPError xmppError
						= XMPPError.from(Condition.remote_server_not_found, noServerFound).build();
				throw new XMPPException.XMPPErrorException(null, xmppError);
			}
			else {
				for (InetSocketAddress isa : isAddresses) {
					try {
						state = connectAndLogin(isa, serviceName, loginStrategy);
						if ((state == ConnectState.ABORT_CONNECTING)
								|| (state == ConnectState.STOP_TRYING))
							return;
					}
					catch (XMPPException | SmackException ex) {
						disconnectAndCleanConnection();
						if (checkLoginFailMode(ex) != SecurityAuthority.REASON_UNKNOWN)
							throw ex;
					}
				}
			}
		}
	}

	/**
	 * Creates the JabberLoginStrategy to use for the current account.
	 */
	private JabberLoginStrategy createLoginStrategy()
	{
		if (((JabberAccountIDImpl) mAccountID).isAnonymousAuthUsed()) {
			return new AnonymousLoginStrategy(mAccountID.getAuthorizationName());
		}

		String clientCertId = mAccountID
				.getAccountPropertyString(ProtocolProviderFactory.CLIENT_TLS_CERTIFICATE);
		if (clientCertId != null) {
			return new LoginByClientCertificateStrategy(mAccountID);
		}
		else {
			return new LoginByPasswordStrategy(this, mAccountID);
		}
	}

	private void setDnssecLoginFailure()
	{
		eventDuringLogin = new RegistrationStateChangeEvent(this, getRegistrationState(),
				RegistrationState.UNREGISTERED, RegistrationStateChangeEvent.REASON_USER_REQUEST,
				"No usable host found due to DNSSEC failures");
	}

	/**
	 * Connects using the domain specified and its SRV records.
	 *
	 * @param domain
	 * 		the domain to use
	 * @param serviceName
	 * 		the domain name of the user's login
	 * @param dnssecState
	 * 		state of possible received DNSSEC exceptions
	 * @param loginStrategy
	 * 		the login strategy to use
	 * @return whether to continue trying or stop.
	 */
	private ConnectState connectUsingSRVRecords(String domain, String serviceName,
			boolean[] dnssecState, JabberLoginStrategy loginStrategy)
			throws XMPPException, SmackException
	{
		// check to see is there are SRV records for this server domain
		SRVRecord srvRecords[] = null;
		try {
			srvRecords = NetworkUtils.getSRVRecords("xmpp-client", "tcp", domain);
		}
		catch (ParseException e) {
			logger.error("SRV record not resolved", e);
		}
		catch (DnssecException e) {
			logger.error("DNSSEC failure for SRV lookup", e);
			dnssecState[0] = true;
		}

		if (srvRecords != null) {
			InetSocketAddress[] isAddresses = null;
			String mSvr = null;

			for (final SRVRecord srv : srvRecords) {
				try {
					isAddresses = NetworkUtils.getAandAAAARecords(srv.getTarget(), srv.getPort());
					mSvr = srv.getTarget();
				}
				catch (ParseException e) {
					logger.error("Invalid SRV record target", e);
				}
				catch (DnssecException e) {
					logger.error("DNSSEC failure for A/AAAA lookup of SRV", e);
					dnssecState[0] = true;
				}

				if (isAddresses == null || isAddresses.length == 0) {
					logger.error("No A/AAAA addresses found for " + srv.getTarget());
				}
				else {
					for (InetSocketAddress isa : isAddresses) {
						try {
							// if fail-over mechanism is enabled, use it, default is not enabled.
							if (JabberActivator.getConfigurationService().getBoolean(
									FailoverConnectionMonitor.REVERSE_FAILOVER_ENABLED_PROP,
									false)) {
								FailoverConnectionMonitor.getInstance(this)
										.setCurrent(domain, mSvr);
							}
							return connectAndLogin(isa, serviceName, loginStrategy);
						}
						catch (XMPPException | SmackException ex) {
							logger.error("Error connecting to " + isa + " for domain:" + domain
									+ " serviceName:" + serviceName, ex);
							disconnectAndCleanConnection();
							if (checkLoginFailMode(ex) != SecurityAuthority.REASON_UNKNOWN)
								throw ex;
						}
					}
				}
			}
		}
		else
			logger.error("No SRV addresses found for _xmpp-client._tcp." + domain);
		return ConnectState.CONTINUE_TRYING;
	}

	/**
	 * Tries to login to the XMPP server with the supplied user PRE_KEY_ID. If the protocol is
	 * Google Talk,
	 * the user PRE_KEY_ID including the service name is used. For other protocols, if the login
	 * with the
	 * user PRE_KEY_ID without the service name fails, a second attempt including the service name
	 * is made
	 * when the return exception is "not-authorized".
	 *
	 * @param currentAddress
	 * 		the IP address to connect to
	 * @param serviceName
	 * 		the domain name of the user's login
	 * @param loginStrategy
	 * 		the login strategy to use
	 * @throws XMPPException
	 * 		when a failure occurs
	 */
	private ConnectState connectAndLogin(InetSocketAddress currentAddress, String serviceName,
			JabberLoginStrategy loginStrategy)
			throws XMPPException, SmackException
	{
		String userID;

		/**
		 * with a google account (either gmail or google apps related), the userID MUST be the
		 * @see EntityBareJid i.e. user@serviceName
		 */
		if (mAccountID.getProtocolDisplayName().equals("Google Talk")) {
			userID = mAccountID.getUserID();
		}
		else {
			userID = XmppStringUtils.parseLocalpart(mAccountID.getUserID());
		}

		try {
			return connectAndLogin(currentAddress, serviceName, userID, resource, loginStrategy);
		}
		catch (XMPPErrorException | SmackException ex) {
			// server disconnect us after such an error, do cleanup or connection denied.
			disconnectAndCleanConnection();
			throw ex; // rethrow the original exception
		}
	}

	/**
	 * Initializes the Jabber Resource identifier: default or auto generated.
	 */
	private void loadResource()
	{
		if (resource == null) {
			String autoGenerateResource = mAccountID.getAccountPropertyString(
					ProtocolProviderFactory.AUTO_GENERATE_RESOURCE);
			if (autoGenerateResource == null || Boolean.parseBoolean(autoGenerateResource)) {
				SecureRandom random = new SecureRandom();
				resource = DEFAULT_RESOURCE + "-" + new BigInteger(32, random).toString(32);
			}
			else {
				resource = mAccountID.getAccountPropertyString(ProtocolProviderFactory.RESOURCE);
				if (TextUtils.isEmpty(resource))
					resource = DEFAULT_RESOURCE;
			}
		}
	}

	/**
	 * Sets the proxy information as per account proxy setting
	 *
	 * @throws OperationFailedException
	 */
	private void loadProxy()
			throws OperationFailedException
	{
		boolean isUseProxy = mAccountID.getAccountPropertyBoolean(
				ProtocolProviderFactory.IS_USE_PROXY, false);

		if (isUseProxy) {
			String proxyType = mAccountID.getAccountPropertyString(
					ProtocolProviderFactory.PROXY_TYPE, mAccountID.getProxyType());
			String proxyAddress = mAccountID.getAccountPropertyString(
					ProtocolProviderFactory.PROXY_ADDRESS, mAccountID.getProxyAddress());

			String proxyPortStr = mAccountID.getAccountPropertyString(
					ProtocolProviderFactory.PROXY_PORT, mAccountID.getProxyPort());
			int proxyPort;
			try {
				proxyPort = Integer.parseInt(proxyPortStr);
			}
			catch (NumberFormatException ex) {
				throw new OperationFailedException("Wrong proxy port, " + proxyPortStr
						+ " does not represent an integer",
						OperationFailedException.INVALID_ACCOUNT_PROPERTIES, ex);
			}

			String proxyUsername = mAccountID.getAccountPropertyString(
					ProtocolProviderFactory.PROXY_USERNAME, mAccountID.getProxyUserName());
			String proxyPassword = mAccountID.getAccountPropertyString(
					ProtocolProviderFactory.PROXY_PASSWORD, mAccountID.getProxyPassword());

			if (proxyAddress == null || proxyAddress.length() <= 0) {
				throw new OperationFailedException("Missing Proxy Address",
						OperationFailedException.INVALID_ACCOUNT_PROPERTIES);
			}
			try {
				proxy = new ProxyInfo(ProxyInfo.ProxyType.valueOf(proxyType),
						proxyAddress, proxyPort, proxyUsername, proxyPassword);
			}
			catch (IllegalStateException e) {
				logger.error("Invalid Proxy Type not support by smack: ", e);
				proxy = null;
			}
		}
		else {
			proxy = null;
		}
	}

	/**
	 * Connects xmpp connection and login. Returning the state whether is it final - Abort due to
	 * certificate cancel or keep trying cause only current address has failed or stop trying cause
	 * we succeeded.
	 *
	 * @param address
	 * 		the address to connect to
	 * @param serviceName
	 * 		the service name to use
	 * @param userName
	 * 		the username to use
	 * @param resource
	 * 		and the resource.
	 * @param loginStrategy
	 * 		the login strategy to use
	 * @return return the state how to continue the connect process.
	 * @throws XMPPException
	 * 		if we cannot connect for some reason
	 */
	private ConnectState connectAndLogin(InetSocketAddress address, String serviceName,
			String userName, String resource, JabberLoginStrategy loginStrategy)
			throws XMPPException, SmackException
	{
		XMPPTCPConnectionConfiguration.Builder config = XMPPTCPConnectionConfiguration.builder();
		// Enable smack debug message printing
		config.setDebuggerEnabled(true);

		try {
			config.setXmppDomain(serviceName);
			config.setResource(resource);
		}
		catch (XmppStringprepException e) {
			e.printStackTrace();
		}

		// config.setHost(address.getAddress().getHostAddress());
		config.setHost(address.getHostName());
		config.setPort(address.getPort());
		config.setProxyInfo(proxy);
		config.setCompressionEnabled(false);

		// if we have OperationSetPersistentPresence to take care of <presence/> sending, then
		// disable Smack from sending the initial presence upon user authentication
		OpSetPP = getOperationSet(OperationSetPersistentPresence.class);
		if (OpSetPP != null)
			config.setSendPresence(false);

		// user have the possibility to disable TLS but in this case, it will not be able to
		// connect to a server which requires TLS
		boolean tlsRequired = loginStrategy.isTlsRequired();
		config.setSecurityMode(tlsRequired
				? ConnectionConfiguration.SecurityMode.required
				: ConnectionConfiguration.SecurityMode.ifpossible);
		TLSUtils.setSSLv3AndTLSOnly(config);

		if (mConnection != null) {
			logger.warn("Attempt on connection that is not null and isConnected? "
							+ mConnection.isConnected(),
					new Exception("Trace possible duplicate connections: "
							+ mAccountID.getAccountJid()));
			disconnectAndCleanConnection();
		}

		config.setSocketFactory(SocketFactory.getDefault());
		this.address = address;
		CertificateService cvs = getCertificateVerificationService();
		if (cvs != null) {
			SSLContext sslContext;
			try {
				X509TrustManager sslTrustManager = getTrustManager(cvs, serviceName);
				sslContext = loginStrategy.createSslContext(cvs, sslTrustManager);
				config.setCustomSSLContext(sslContext);
			}
			catch (GeneralSecurityException e) {
				logger.error("Error creating custom trust manager", e);
				XMPPError xmppError = XMPPError.getBuilder(Condition.service_unavailable).build();
				throw new XMPPException.XMPPErrorException(null, xmppError);
			}
		}
		else if (tlsRequired) {
			XMPPError xmppError = XMPPError.getBuilder(Condition.service_unavailable).build();
			throw new XMPPException.XMPPErrorException(null, xmppError);
		}

//		String userJid = userName + "@" + serviceName;
//		String password = userCredentials.getPasswordAsString();
//		config.setUsernameAndPassword(userJid, password);

		/* Start monitoring the status before connection-login. Only register listener once */
		mConnection = new XMPPTCPConnection(config.build());
		if (connectionListener == null) {
			connectionListener = new JabberConnectionListener();
			mConnection.addConnectionListener(connectionListener);
		}

		// Allow longer timeout during login for slow client
		mConnection.setReplyTimeout(SMACK_PACKET_REPLY_TIMEOUT);

		// Init the connection SynchronizedPoints
		xmppConnected = new LoginSynchronizationPoint<>(this, "connection connected");

		logger.info("Starting XMPP Connection...: " + address.toString());
		try {
			mConnection.connect();
		}
		catch (StreamErrorException ex) {
			String errMsg = ex.getStreamError().getDescriptiveText();
			logger.error("Encounter problem during XMPPConnection: " + errMsg);
			XMPPError xmppError = XMPPError.from(Condition.policy_violation, errMsg).build();
			throw new XMPPException.XMPPErrorException(null, xmppError);
		}
		catch (SecurityRequiredByServerException ex) {
			// "SSL/TLS required by server but disabled in client"
			String errMsg = ex.getMessage();
			XMPPError xmppError = XMPPError.from(Condition.not_allowed, errMsg).build();
			throw new XMPPException.XMPPErrorException(null, xmppError);
		}
		catch (SecurityRequiredByClientException ex) {
			// "SSL/TLS required by client but not supported by server"
			String errMsg = ex.getMessage();
			XMPPError xmppError = XMPPError.from(Condition.service_unavailable, errMsg).build();
			throw new XMPPException.XMPPErrorException(null, xmppError);
		}
		catch (XMPPException | SmackException | IOException | InterruptedException ex) {
			String errMsg = "Encounter problem during XMPPConnection: " + ex.getMessage();
			logger.error(errMsg);
			XMPPError xmppError = XMPPError.from(Condition.remote_server_timeout, errMsg).build();
			throw new XMPPException.XMPPErrorException(null, xmppError);
		}

		try {
			// Wait for connectionListener to report connection status. Exception handled in
			// the above try/catch
			xmppConnected.checkIfSuccessOrWaitOrThrow();
		}
		catch (InterruptedException e) {
			e.printStackTrace();
		}

		// Check if user has cancelled the Trusted Certificate confirmation request
		if (abortConnecting) {
			abortConnecting = false;
			disconnectAndCleanConnection();
			return ConnectState.ABORT_CONNECTING;
		}

		if (!mConnection.isConnected()) {
			logger.error("XMPPConnection establishment has failed!");

			// mConnection is not connected, lets set the mConnection state as failed;
			disconnectAndCleanConnection();
			eventDuringLogin = null;
			fireRegistrationStateChanged(getRegistrationState(),
					RegistrationState.CONNECTION_FAILED,
					RegistrationStateChangeEvent.REASON_SERVER_NOT_FOUND, null);
			return ConnectState.ABORT_CONNECTING;
		}

		fireRegistrationStateChanged(getRegistrationState(), RegistrationState.REGISTERING,
				RegistrationStateChangeEvent.REASON_NOT_SPECIFIED, null);

		// Init the user authentication SynchronizedPoints
		accountAuthenticated = new LoginSynchronizationPoint<>(this, "account authenticated");
		Boolean success = false;
		try {
			success = loginStrategy.login(mConnection, userName, resource);
		}
		catch (StreamErrorException ex) {
			String errMsg = ex.getStreamError().getDescriptiveText();
			logger.error("Encounter problem during XMPPConnection: " + errMsg);
			XMPPError xmppError = XMPPError.from(Condition.policy_violation, errMsg).build();
			throw new XMPPException.XMPPErrorException(null, xmppError);
		}
		catch (SmackException | XMPPException el) {
			String errMsg = el.getMessage();
			/*
			 * If account is not registered on server, send registration to server if user
			 * requested. Otherwise throw back to user and ask for InBand registration
			 * confirmation.
			 */
			if (errMsg.contains("not-authorized")) {
				if (mAccountID.isIbRegistration()) {
					try {
						// Server sends stream disconnect on "not-authorized". So perform manual
						// disconnect and connect again before server closes the stream
						mConnection.disconnect();
						mConnection.connect();
						accountIBRegistered
								= new LoginSynchronizationPoint<>(this, "account ib registered");
						loginStrategy.registerAccount(this, mAccountID);
						eventDuringLogin = null;
						return ConnectState.STOP_TRYING;
					}
					catch (StreamErrorException ex) {
						errMsg = ex.getStreamError().getDescriptiveText();
						logger.error("Encounter problem during XMPPConnection: " + errMsg);
						XMPPError xmppError = XMPPError.from(Condition.policy_violation,
								errMsg).build();
						throw new XMPPException.XMPPErrorException(null, xmppError);
					}
					catch (SmackException | XMPPException | InterruptedException
							| IOException err) {
						disconnectAndCleanConnection();
						eventDuringLogin = null;
						fireRegistrationStateChanged(getRegistrationState(),
								RegistrationState.CONNECTION_FAILED,
								RegistrationStateChangeEvent.REASON_IB_REGISTRATION_FAILED,
								loginStrategy.getClass().getName() + " requests abort");

						errMsg = err.getMessage();
						if (!errMsg.contains("registration-required")) {
							errMsg = "Error in account registration on server: " + errMsg;
							logger.error(errMsg);
							XMPPError xmppError = XMPPError.from(Condition.forbidden,
									errMsg).build();
							throw new XMPPException.XMPPErrorException(null, xmppError);
						}
						else {
							logger.error(errMsg);
							XMPPError xmppError = XMPPError.from(
									Condition.registration_required, errMsg).build();
							throw new XMPPException.XMPPErrorException(null, xmppError);
						}
					}
				}
				else {
					errMsg = "You are not authorized to access the server. Check account settings"
                            + " or enable IB Registration and try again: " + errMsg;
					XMPPError xmppError = XMPPError.from(Condition.not_authorized,
							errMsg).build();
					throw new XMPPException.XMPPErrorException(null, xmppError);
				}
			}
		}

		// cmeng-sometimes exception and crash after this point during apk debug launch. Stack
		// overflow or library incompatibility (chatRoomListFragment)?????
		try {
			// wait for connectionListener to report status. Exceptions are handled in try/catch
			accountAuthenticated.checkIfSuccessOrWait();

		}
		catch (InterruptedException e) {
			e.printStackTrace();
		}

		// Reset back to Smack default - may not reach here if exception
		mConnection.setReplyTimeout(SMACK_PACKET_REPLY_DEFAULT_TIMEOUT);

		if (!success) {
			disconnectAndCleanConnection();
			eventDuringLogin = null;
			fireRegistrationStateChanged(getRegistrationState(),
					RegistrationState.CONNECTION_FAILED,
					RegistrationStateChangeEvent.REASON_AUTHENTICATION_FAILED,
					loginStrategy.getClass().getName() + " requests abort");
			return ConnectState.ABORT_CONNECTING;
		}

		if (mConnection.isAuthenticated()) {
			return ConnectState.STOP_TRYING;
		}
		else {
			disconnectAndCleanConnection();
			eventDuringLogin = null;
			fireRegistrationStateChanged(getRegistrationState(), RegistrationState.UNREGISTERED,
					RegistrationStateChangeEvent.REASON_NOT_SPECIFIED, null);
			return ConnectState.CONTINUE_TRYING;
		}
	}

	/**
	 * Listener for jabber connection events
	 */
	private class JabberConnectionListener implements ConnectionListener
	{
		/**
		 * Notification that the connection was closed normally.
		 */
		public void connectionClosed()
		{
			String errMsg = "Stream closed!";
			XMPPError xmppError
					= XMPPError.from(Condition.remote_server_timeout, errMsg).build();
			XMPPErrorException xmppException = new XMPPErrorException(null, xmppError);
			xmppConnected.reportFailure(xmppException);

			// if we are in the middle of connecting process do not fire events, will do it later
			// when the method connectAndLogin finishes its work
			synchronized (connectAndLoginLock) {
				if (inConnectAndLogin) {
					eventDuringLogin = new RegistrationStateChangeEvent(
							ProtocolProviderServiceJabberImpl.this, getRegistrationState(),
							RegistrationState.CONNECTION_FAILED,
							RegistrationStateChangeEvent.REASON_NOT_SPECIFIED, errMsg);
					return;
				}
			}
			// Fire that connection has closed. User is responsible to log in again as the stream
			// closed can be authentication, ssl security etc that an auto retrial is of little use
			fireRegistrationStateChanged(getRegistrationState(),
					RegistrationState.CONNECTION_FAILED,
					RegistrationStateChangeEvent.REASON_NOT_SPECIFIED, errMsg);

//			if (androidOmemoService != null) {
//				androidOmemoService.handleProviderRemoved();
//			}
		}

		/**
		 * Notification that the connection was closed due to an exception. When abruptly
		 * disconnected, the ReconnectionManager will try to reconnecting to the server.
		 * Note: Must reported as RegistrationState.RECONNECTING to allow resume as all
		 * initial setup must be kept.
		 *
		 * @param exception
		 * 		contains information on the error.
		 */
		public void connectionClosedOnError(Exception exception)
		{
			String errMsg = exception.getMessage();
			int regEvent = RegistrationStateChangeEvent.REASON_NOT_SPECIFIED;

			if (exception instanceof SSLException) {
				regEvent = RegistrationStateChangeEvent.REASON_SERVER_NOT_FOUND;
			}
			else if (exception instanceof StreamErrorException) {
				StreamError err = ((StreamErrorException) exception).getStreamError();
				StreamError.Condition condition = err.getCondition();
				errMsg = err.getDescriptiveText();

				if ((condition == StreamError.Condition.conflict)
						|| (condition == StreamError.Condition.policy_violation)) {
					regEvent = RegistrationStateChangeEvent.REASON_MULTIPLE_LOGIN;
					if (errMsg.contains("removed"))
						regEvent = RegistrationStateChangeEvent.REASON_NON_EXISTING_USER_ID;
					else if (condition == StreamError.Condition.policy_violation)
						regEvent = RegistrationStateChangeEvent.REASON_POLICY_VIOLATION;
				}
			}
			else if (exception instanceof XmlPullParserException) {
				regEvent = RegistrationStateChangeEvent.REASON_AUTHENTICATION_FAILED;
			}

			// logger.error("Smack connection Closed OnError: " + errMsg, exception);
			XMPPError xmppError
					= XMPPError.from(Condition.remote_server_not_found, errMsg).build();
			XMPPErrorException xmppException = new XMPPErrorException(null, xmppError);
			xmppConnected.reportFailure(xmppException);

			// if we are in the middle of connecting process do not fire events, will do it
			// later when the method connectAndLogin finishes its work
			synchronized (connectAndLoginLock) {
				if (inConnectAndLogin) {
					eventDuringLogin = new RegistrationStateChangeEvent(
							ProtocolProviderServiceJabberImpl.this,
							getRegistrationState(),
							RegistrationState.CONNECTION_FAILED,
							regEvent, errMsg);
					return;
				}
			}
			// Reconnecting state - keep all contacts' status
			fireRegistrationStateChanged(getRegistrationState(),
					RegistrationState.RECONNECTING, regEvent, errMsg);
		}

		/**
		 * Implements <tt>reconnectingIn</tt> from <tt>ConnectionListener</tt>
		 *
		 * @param i
		 * 		delay in seconds for reconnection.
		 */
		public void reconnectingIn(int i)
		{
			if ((i <= 0) && logger.isInfoEnabled())
				logger.info("ReconnectionManager starting connection attempt...");
		}

		/**
		 * Implements <tt>reconnectingIn</tt> from <tt>ConnectionListener</tt>
		 */
		public void reconnectionSuccessful()
		{
			xmppConnected.reportSuccess();
			if (logger.isInfoEnabled())
				logger.info("Reconnection Successful");
		}

		/**
		 * Implements <tt>reconnectionFailed</tt> from <tt>ConnectionListener</tt>.
		 *
		 * @param exception
		 * 		description of the failure
		 */
		public void reconnectionFailed(Exception exception)
		{
			String errMsg = exception.getMessage();
			XMPPError xmppError
					= XMPPError.from(Condition.remote_server_not_found, errMsg).build();
			XMPPErrorException xmppException = new XMPPErrorException(null, xmppError);
			xmppConnected.reportFailure(xmppException);

			if (logger.isInfoEnabled())
				logger.info("Reconnection Failed: ", exception);
		}

		@Override
		public void connected(XMPPConnection connection)
		{
			xmppConnected.reportSuccess();
//			if (logger.isInfoEnabled())
//				logger.info("Smack: CP Connection Successful");
			setTrafficClass();
			// must initialize caps entities upon success connection to ensure it is ready for
			// the very first <iq/> send
			initServiceDiscoveryFeature();
			fireRegistrationStateChanged(getRegistrationState(),
					RegistrationState.CONNECTION_CONNECTED,
					RegistrationStateChangeEvent.REASON_NOT_SPECIFIED,
					"TCP Connection Successful");
		}

		@Override
		public void authenticated(XMPPConnection connection, boolean resumed)
		{
			accountAuthenticated.reportSuccess();

			/*
			 * XEP-0237:Roster Versioning - init RosterStore for each authenticated account to
			 * support persistent storage
		 	 */
			initRosterStore();

			isResumed = resumed;
			String msg = "Smack: User Authenticated with isResumed state: " + resumed;
			mConnection.setUseStreamManagementResumption(true);

			if (mAccountID.isIbRegistration())
				mAccountID.setIbRegistration(false);

			// Fire registration state has changed
			if (resumed) {
				fireRegistrationStateChanged(getRegistrationState(), RegistrationState.REGISTERED,
						RegistrationStateChangeEvent.REASON_RESUMED, msg, false);
			}
			else {
				eventDuringLogin = null;
				fireRegistrationStateChanged(getRegistrationState(), RegistrationState.REGISTERED,
						RegistrationStateChangeEvent.REASON_USER_REQUEST, msg, true);
			}
		}
	}

	/**
	 * Gets the TrustManager that should be used for the specified service
	 *
	 * @param serviceName
	 * 		the service name
	 * @param cvs
	 * 		The CertificateVerificationService to retrieve the trust manager
	 * @return the trust manager
	 */

	private X509TrustManager getTrustManager(CertificateService cvs, String serviceName)
			throws GeneralSecurityException
	{
		return new HostTrustManager(cvs.getTrustManager(
				Arrays.asList(new String[]{serviceName, "_xmpp-client." + serviceName})));
	}

	/**
	 * Used to disconnect current connection and clean it.
	 */
	public void disconnectAndCleanConnection()
	{
		if (mConnection != null) {
			mConnection.setReplyTimeout(SMACK_PACKET_REPLY_DEFAULT_TIMEOUT);

			// disconnect anyway because it will clear any listeners that maybe added even if
			// it is not connected
			mConnection.removeConnectionListener(connectionListener);
			connectionListener = null;

			try {
				Presence unavailablePresence = new Presence(Presence.Type.unavailable);
				if ((OpSetPP != null)
						&& !StringUtils.isNullOrEmpty(OpSetPP.getCurrentStatusMessage())) {
					unavailablePresence.setStatus(OpSetPP.getCurrentStatusMessage());
				}
				mConnection.disconnect(unavailablePresence);
			}
			catch (Exception ex) {
				logger.warn("Exception while disconnect and clean connection!!!");
			}

			// make it null as it also holds a reference to the old connection; it will be created
			// again on new connection
			mConnection = null;
			try {
				/*
				 * The discoveryManager is exposed as service-public by the
				 * OperationSetContactCapabilities of this ProtocolProviderService.
				 * No longer expose it because it's going away.
				 */
				if (opsetContactCapabilities != null)
					opsetContactCapabilities.setDiscoveryManager(null);
			} finally {
				if (discoveryManager != null) {
					discoveryManager.stop();
					discoveryManager = null;
				}
			}
		}
	}

	/**
	 * Ends the registration of this protocol provider with the service.
	 */
	public void unregister()
	{
		unregisterInternal(true);
	}

	/**
	 * Ends the registration of this protocol provider with the service.
	 *
	 * @param userRequest
	 * 		is the unregister by user request.
	 */
	public void unregister(boolean userRequest)
	{
		unregisterInternal(true, userRequest);
	}

	/**
	 * Unregister and fire the event if requested
	 *
	 * @param fireEvent
	 * 		boolean
	 */
	public void unregisterInternal(boolean fireEvent)
	{
		unregisterInternal(fireEvent, false);
	}

	/**
	 * Unregister and fire the event if requested
	 *
	 * @param fireEvent
	 * 		boolean
	 */
	public void unregisterInternal(boolean fireEvent, boolean userRequest)
	{
		synchronized (initializationLock) {
			if (fireEvent) {
				eventDuringLogin = null;
				fireRegistrationStateChanged(getRegistrationState(),
						RegistrationState.UNREGISTERING,
						RegistrationStateChangeEvent.REASON_NOT_SPECIFIED, null,
						userRequest);
			}

			disconnectAndCleanConnection();
			RegistrationState currRegState = getRegistrationState();

			if (fireEvent) {
				eventDuringLogin = null;
				fireRegistrationStateChanged(currRegState, RegistrationState.UNREGISTERED,
						RegistrationStateChangeEvent.REASON_USER_REQUEST, null,
						userRequest);
			}
		}
	}

	/**
	 * Returns the short name of the protocol that the implementation of this provider is based
	 * upon (like SIP, Jabber, ICQ/AIM, or others for example).
	 *
	 * @return a String containing the short name of the protocol this service is taking care of.
	 */
	public String getProtocolName()
	{
		return ProtocolNames.JABBER;
	}

	/**
	 * Setup a common avatarStoreDirectory store for all accounts during Smack initialization
	 * state to support server avatar info persistent storage
	 */
	public static void initAvatarStore()
	{
		avatarStoreDirectory
				= new File(aTalkApp.getGlobalContext().getFilesDir() + "/avatarStore");

		// Store in memory cache by default and also persistent store if not null
		VCardAvatarManager.setPersistentCache(avatarStoreDirectory);
		UserAvatarManager.setPersistentCache(avatarStoreDirectory);
	}

	/**
	 * Setup the rosterStoreDirectory store for each mAccountID during login process i.e.
	 * tag the rosterStore with userID, to support server rosterVersioning if available
	 * Note: roster.isRosterVersioningSupported() is not used as its actual status is only know
	 * after account is authenticated (too late?).
	 */
	public void initRosterStore()
	{
		Roster roster = Roster.getInstanceFor(mConnection);
		String userID = mAccountID.getUserID();

		rosterStoreDirectory
				= new File(aTalkApp.getGlobalContext().getFilesDir() + "/rosterStore_" + userID);

		if (!rosterStoreDirectory.exists()) {
			if (!rosterStoreDirectory.mkdir())
				logger.error("Roster Store directory creation error: "
						+ rosterStoreDirectory.getAbsolutePath());
		}
		if (rosterStoreDirectory.exists()) {
			RosterStore rosterStore = DirectoryRosterStore.open(rosterStoreDirectory);
			if (rosterStore == null) {
				rosterStore = DirectoryRosterStore.init(rosterStoreDirectory);
			}
			roster.setRosterStore(rosterStore);
		}
	}

	public File getRosterStoreDirectory()
	{
		return rosterStoreDirectory;
	}

	/**
	 * Registers the ServiceDiscoveryManager wrapper
	 * <p>
	 * we setup all supported features before packets are actually being sent during feature
	 * registration. So we'd better do it here so that our first presence update would
	 * contain a caps with the right features.
	 */
	private void registerServiceDiscoveryManager()
	{
		// Add features aTalk supports in addition to smack.
		String[] featuresToRemove = new String[]{"http://jabber.org/protocol/commands"};
		String[] featuresToAdd = supportedFeatures.toArray(new String[supportedFeatures.size()]);
		// boolean cacheNonCaps = true;

		discoveryManager = new ScServiceDiscoveryManager(this, mConnection,
				featuresToRemove, featuresToAdd, true);

		boolean isCallingDisabled = JabberActivator.getConfigurationService().getBoolean(
				"protocol.jabber.CALLING_DISABLED", false);

		boolean isCallingDisabledForAccount = false;
		if ((mAccountID != null)
				&& (mAccountID.getAccountPropertyBoolean("CALLING_DISABLED", false))) {
			isCallingDisabled = true;
		}

		/*
		 * Expose the discoveryManager as service-public through the
		 * OperationSetContactCapabilities of this ProtocolProviderService.
		 */
		if (opsetContactCapabilities != null)
			opsetContactCapabilities.setDiscoveryManager(discoveryManager);
	}

	/**
	 * Setup all the Smack Service Discovery features that can only be performed during
	 * actual account registration stage (mConnection). For initial setup see:
	 * {@link #initSmackDefaultSettings()} and {@link #initialize(String, AccountID)}
	 * <p>
	 * Note: For convenience, many of the OperationSets when supported will handle state and events
	 * changes on its own.
	 */
	private void initServiceDiscoveryFeature()
	{
		/*  XEP-0092: Software Version initialization */
		VersionManager versionManager = VersionManager.getInstanceFor(mConnection);

		/* XEP-0199: XMPP Ping: Each account may set his own ping interval */
		PingManager pingManager = PingManager.getInstanceFor(mConnection);

		boolean isKeepAliveEnable = mAccountID.getAccountPropertyBoolean(
				ProtocolProviderFactory.IS_KEEP_ALIVE_ENABLE, false);
		if (isKeepAliveEnable) {
			int pingInterval = mAccountID.getAccountPropertyInt(
					ProtocolProviderFactory.PING_INTERVAL, defaultPingInterval);
			pingManager.setPingInterval(pingInterval);
		}
		else {
			// Disable pingManager
			pingManager.setPingInterval(0);
		}

		/*  Start up VCardAvatarManager / UserAvatarManager for mAccount auto-update */
		VCardAvatarManager.getInstanceFor(mConnection);
		UserAvatarManager.getInstanceFor(mConnection);

		/*  Must initialize omemoManager only on xmppConnection */
		androidOmemoService = new AndroidOmemoService(this);

		/*
		 * Enable ReconnectionManager with ReconnectionPolicy.RANDOM_INCREASING_DELAY
		 * - attempt to reconnect when server disconnect unexpectedly
		 */
		ReconnectionManager reconnectionManager = ReconnectionManager.getInstanceFor(mConnection);
		reconnectionManager.enableAutomaticReconnection();

		// add SupportedFeatures only prior to registerServiceDiscoveryManager. Otherwise found
		// some race condition with some optional features not properly initialized/
		addSupportedCapsFeatures();

		/*
		 * XEP-0030:Service Discovery: Leave it to the last step so all features are included in
		 * caps ver calculation
		 */
		registerServiceDiscoveryManager();
	}

	/**
	 * Defined all the entity capabilities for the EntityCapsManager to advertise in
	 * disco#info query from other entities. Some features support are user selectable
	 * <p>
	 * Note: Do not need to mention if there are already included in Smack Library and have been
	 * activated.
	 */
	private void addSupportedCapsFeatures()
	{
		String NOTIFY = "+notify";
		supportedFeatures.clear();

		/*
		 * Adds Jingle related features to the supported features.
		 */
		// XEP-0166: Jingle
		supportedFeatures.add(URN_XMPP_JINGLE);
		// XEP-0167: Jingle RTP Sessions
		supportedFeatures.add(URN_XMPP_JINGLE_RTP);
		// XEP-0177: Jingle Raw UDP Transport Method
		supportedFeatures.add(URN_XMPP_JINGLE_RAW_UDP_0);

		/*
		 * Reflect the preference of the user with respect to the use of ICE.
		 */
		if (mAccountID.getAccountPropertyBoolean(ProtocolProviderFactory.IS_USE_ICE, true)) {
			// XEP-0176: Jingle ICE-UDP Transport Method
			supportedFeatures.add(URN_XMPP_JINGLE_ICE_UDP_1);
		}

		// XEP-0167: Jingle RTP Sessions
		supportedFeatures.add(URN_XMPP_JINGLE_RTP_AUDIO);
		// XEP-0180: Jingle Video via RTP
		supportedFeatures.add(URN_XMPP_JINGLE_RTP_VIDEO);
		// XEP-0262: Use of ZRTP in Jingle RTP Sessions
		supportedFeatures.add(URN_XMPP_JINGLE_RTP_ZRTP);

		/*
		 * Reflect the preference of the user with respect to the use of Jingle Nodes.
		 */
		if (mAccountID.getAccountPropertyBoolean(
				ProtocolProviderFactoryJabberImpl.IS_USE_JINGLE_NODES, true)) {
			// XEP-0278: Jingle Relay Nodes
			supportedFeatures.add(URN_XMPP_JINGLE_NODES);
		}

		// XEP-0251: Jingle Session Transfer
		supportedFeatures.add(URN_XMPP_JINGLE_TRANSFER_0);

		if (mAccountID.getAccountPropertyBoolean(ProtocolProviderFactory.DEFAULT_ENCRYPTION, true)
				&& mAccountID.isEncryptionProtocolEnabled(SrtpControlType.DTLS_SRTP)) {
			// XEP-0320: Use of DTLS-SRTP in Jingle Sessions
			supportedFeatures.add(URN_XMPP_JINGLE_DTLS_SRTP);
		}

		if (isDesktopSharingEnable) {
			// Adds extension to support remote control as a sharing server (sharer).
			supportedFeatures.add(InputEvtIQ.NAMESPACE_SERVER);

			// Adds extension to support remote control as a sharing client (sharer).
			supportedFeatures.add(InputEvtIQ.NAMESPACE_CLIENT);
		}

		// ===============================================
		// TODO: add the feature if any, corresponding to persistent presence, if someone knows
		// supportedFeatures.add(_PRESENCE_);

		// XEP-0065: SOCKS5 Bytestreams
		supportedFeatures.add(Bytestream.NAMESPACE);

		// Do not advertise if user disable chat stat notifications option
		if (ConfigurationUtils.isSendChatStateNotifications()) {
			// XEP-0085: Chat State Notifications
			supportedFeatures.add(ChatStateExtension.NAMESPACE);
		}

		// XEP-0298: Delivering Conference Information to Jingle Participants (Coin)
		supportedFeatures.add(URN_XMPP_JINGLE_COIN);

		// this feature is mandatory to be compliant with Service Discovery - added by default
		// supportedFeatures.add("http://jabber.org/protocol/disco#info");

		// XEP-0047: In-Band Bytestreams
		supportedFeatures.add(DataPacketExtension.NAMESPACE);

		// XEP-0294: Jingle RTP Header Extensions Negotiation
		supportedFeatures.add(URN_XMPP_JINGLE_RTP_HDREXT);

		// XEP-0308: Last Message Correction
		supportedFeatures.add(MessageCorrectExtension.NAMESPACE);

		/* This is the "main" feature to advertise when a client support muc. We have to
		 * add some features for specific functionality we support in muc.
		 * see http://www.xmpp.org/extensions/xep-0045.html
		 * The http://jabber.org/protocol/muc feature is already included in smack.
		 */
		// XEP-0045: Multi-User Chat
		supportedFeatures.add(MUCInitialPresence.NAMESPACE + "#rooms");
		supportedFeatures.add(MUCInitialPresence.NAMESPACE + "#traffic");

		// XEP-0054: vcard-temp
		supportedFeatures.add(VCard.NAMESPACE);

		// XEP-0095: Stream Initiation
		supportedFeatures.add(StreamInitiation.NAMESPACE);

		// XEP-0096: SI File Transfer
		supportedFeatures.add(FileTransferNegotiator.SI_PROFILE_FILE_TRANSFER_NAMESPACE);

		// XEP-0231: Bits of Binary
		supportedFeatures.add("urn:xmpp:bob");

		// XEP-0264: File Transfer Thumbnails
		supportedFeatures.add(ThumbnailElement.NAMESPACE);

		// XEP-0084: User Avatar
		supportedFeatures.add(AvatarMetadata.NAMESPACE_NOTIFY);
		supportedFeatures.add(AvatarData.NAMESPACE);

		// XEP-0384: OMEMO Encryption
		supportedFeatures.add(OmemoConstants.PEP_NODE_DEVICE_LIST_NOTIFY);

		// XEP-0092: Software Version
		supportedFeatures.add(URN_XMPP_IQ_VERSION);
	}

	/**
	 * Inorder to take effect on xmppConnection setup and the very first corresponding stanza being
	 * sent; all smack default settings must be initialized prior to connection & account login.
	 * Note: The getInstanceFor(xmppConnection) action during account login will auto-include
	 * the smack Service Discovery feature. So it is no necessary to add the feature again in
	 * method {@link #initialize(String, AccountID)}
	 */
	private void initSmackDefaultSettings()
	{
		int omemoReplyTimeout = 10000; // increase smack default timeout to 10 seconds

		/**
		 * 	init Avatar to support persistent storage for both XEP-0153 and XEP-0084
		 */
		initAvatarStore();

		/** XEP-0153: vCard-Based Avatars - We will handle download of VCard on our own when
		 * there is an avatar update
		 */
		VCardAvatarManager.setAutoDownload(false);

		/** XEP-0084: User Avatars - Enable auto download when there is an avatar update
		 */
		UserAvatarManager.setAutoDownload(true);

		/**
		 * The CapsExtension node value to advertise in <presence/>.
		 */
		String entityNode = OSUtils.IS_ANDROID ? "http://android.atalk.org" : "http://atalk.org";
		EntityCapsManager.setDefaultEntityNode(entityNode);

		/* setup EntityCapsManager persistent store for XEP-0115: Entity Capabilities */
		ScServiceDiscoveryManager.initEntityPersistentStore();

		/**
		 * The CapsExtension reply to be included in the caps <Identity/>
		 */
		String category = "client";
		String appName = aTalkApp.getGlobalContext().getResources().getString(R.string.app_name);
		String type = "android";

		DiscoverInfo.Identity identity = new DiscoverInfo.Identity(category, appName, type);
		ServiceDiscoveryManager.setDefaultIdentity(identity);

		/**
		 * XEP-0092: Software Version
		 * Initialize jabber:iq:version support feature
		 */
		String versionName = BuildConfig.VERSION_NAME;
		String os = OSUtils.IS_ANDROID ? "android" : "pc";

		VersionManager.setDefaultVersion(appName, versionName, os);

		/**
		 * XEP-0199: XMPP Ping
		 * Set the default ping interval in seconds used by PingManager. Can be omitted if you do
		 * not wish to change the Smack Default Setting. The Smack default is 30 minutes.
		 */
		PingManager.setDefaultPingInterval(defaultPingInterval);

		/* Set Roster subscription to manual for all accounts */
		Roster.setDefaultSubscriptionMode(Roster.SubscriptionMode.manual);

		// cmeng - to take care of slow device S3=7s (N3=4.5S) and heavy loaded server.
		SmackConfiguration.setDefaultReplyTimeout(omemoReplyTimeout);

		// Need to disable certain ReflectionDebuggerFactory.DEFAULT_DEBUGGERS loading for
		// Android (that are only for windows)
		SmackConfiguration.addDisabledSmackClass(
				"org.jivesoftware.smackx.debugger.EnhancedDebugger");
		SmackConfiguration.addDisabledSmackClass("org.jivesoftware.smack.debugger.LiteDebugger");

		XMPPTCPConnection.setUseStreamManagementDefault(true);

		// Enable XEP-0054 User Avatar mode to disable avatar photo update via <presence/><photo/>
		AvatarManager.setUserAvatar(true);

		// uncomment if XMPP Server cannot support supports SCRAMSHA1Mechanism
		// SASLAuthentication.unregisterSASLMechanism(SCRAMSHA1Mechanism.class.getName());
	}

	/**
	 * Initialized the service implementation, and puts it in a state where it could inter-operate
	 * with other services. It is strongly recommended that properties in this Map be mapped to
	 * property names as specified by <tt>AccountProperties</tt>.
	 *
	 * @param screenName
	 * 		the account id/uin/screenName of the account that we're about to create
	 * @param accountID
	 * 		the identifier of the account that this protocol provider represents.
	 * @see net.java.sip.communicator.service.protocol.AccountID
	 */
	protected void initialize(String screenName, AccountID accountID)
	{
		synchronized (initializationLock) {
			mAccountID = accountID;

			// Initialize all the smack default setting
			initSmackDefaultSettings();

			/**
			 * Tell Smack what are the additional IQProviders that aTalk can support
			 */
			// register our coin provider
			ProviderManager.addIQProvider(CoinIQ.ELEMENT_NAME, CoinIQ.NAMESPACE,
					new CoinIQProvider());

			// Jitsi Videobridge IQProvider and PacketExtensionProvider
			ProviderManager.addIQProvider(ColibriConferenceIQ.ELEMENT_NAME,
					ColibriConferenceIQ.NAMESPACE, new ColibriIQProvider());

			// register our input event provider
			ProviderManager.addIQProvider(InputEvtIQ.ELEMENT_NAME, InputEvtIQ.NAMESPACE,
					new InputEvtIQProvider());

			ProviderManager.addIQProvider(JibriIq.ELEMENT_NAME, JibriIq.NAMESPACE,
					new JibriIqProvider());

			// register our JingleInfo provider
			ProviderManager.addIQProvider(JingleInfoQueryIQ.ELEMENT_NAME,
					JingleInfoQueryIQ.NAMESPACE, new JingleInfoQueryIQProvider());

			// register our jingle provider
			ProviderManager.addIQProvider(JingleIQ.ELEMENT_NAME, JingleIQ.NAMESPACE,
					new JingleIQProvider());

			ProviderManager.addIQProvider(Registration.ELEMENT, Registration.NAMESPACE,
					new RegistrationProvider());

			/**
			 * Tell Smack what are the additional StreamFeatureProvider and ExtensionProviders that
			 * aTalk can support
			 */
			ProviderManager.addStreamFeatureProvider(Registration.Feature.ELEMENT,
					Registration.Feature.NAMESPACE,
					(ExtensionElementProvider) new RegistrationStreamFeatureProvider());

			ProviderManager.addExtensionProvider(CarbonPacketExtension.RECEIVED_ELEMENT_NAME,
					CarbonPacketExtension.NAMESPACE, new CarbonPacketExtension.Provider(
							CarbonPacketExtension.RECEIVED_ELEMENT_NAME));

			ProviderManager.addExtensionProvider(CarbonPacketExtension.SENT_ELEMENT_NAME,
					CarbonPacketExtension.NAMESPACE, new CarbonPacketExtension.Provider(
							CarbonPacketExtension.SENT_ELEMENT_NAME));

			ProviderManager.addExtensionProvider(CapsExtension.ELEMENT, CapsExtension.NAMESPACE,
					new CapsExtensionProvider());

			ProviderManager.addExtensionProvider(ConferenceDescriptionPacketExtension.ELEMENT_NAME,
					ConferenceDescriptionPacketExtension.NAMESPACE,
					new ConferenceDescriptionPacketExtension.Provider());

			// Add the custom GeolocationExtension to the Smack library - need cleanup
//			ProviderManager.addExtensionProvider(GeolocationPacketExtensionProvider.ELEMENT,
//					GeolocationPacketExtensionProvider.NAMESPACE,
//					new GeolocationPacketExtensionProvider());

			ProviderManager.addExtensionProvider(Nick.ELEMENT_NAME, Nick.NAMESPACE,
					new Nick.Provider());

			// XEP-0231: Bits of Binary
			ProviderManager.addExtensionProvider(BoB.ELEMENT, BoB.NAMESPACE, new BoBProvider());

			// XEP-0084: User Avatar (metadata) + notify
			ProviderManager.addExtensionProvider(AvatarMetadata.ELEMENT,
					AvatarMetadata.NAMESPACE, new AvatarMetadataProvider());

			// XEP-0084: User Avatar (data)
			ProviderManager.addExtensionProvider(AvatarData.ELEMENT, AvatarData.NAMESPACE,
					new AvatarDataProvider());

			// XEP-0153: vCard-Based Avatars
			ProviderManager.addExtensionProvider(VCardTempXUpdate.ELEMENT,
					VCardTempXUpdate.NAMESPACE, new VCardTempXUpdateProvider());

			// Add the custom WhiteboardObjectJabberProvider to the Smack library
			ProviderManager.addExtensionProvider(WhiteboardObjectPacketExtension.ELEMENT_NAME,
					WhiteboardObjectPacketExtension.NAMESPACE,
					new WhiteboardObjectJabberProvider());

			ProviderManager.addExtensionProvider(WhiteboardSessionPacketExtension.ELEMENT_NAME,
					WhiteboardSessionPacketExtension.NAMESPACE,
					new WhiteboardObjectJabberProvider());

			// in case of modified account, we clear list of supported features and all state
			// change listeners, otherwise we can have two OperationSet for same feature and it
			// can causes problem (i.e. two OperationSetBasicTelephony can launch two ICE
			// negotiations (with different ufrag/pwd) and peer will failed call. And by the way
			// user will see two dialog for answering/refusing the call
			this.clearRegistrationStateChangeListener();
			this.clearSupportedOperationSet();

			String protocolIconPath = accountID.getAccountPropertyString(
					ProtocolProviderFactory.PROTOCOL_ICON_PATH);
			if (protocolIconPath == null)
				protocolIconPath = "resources/images/protocol/jabber";

			jabberIcon = new ProtocolIconJabberImpl(protocolIconPath);
			jabberStatusEnum = JabberStatusEnum.getJabberStatusEnum(protocolIconPath);

			/**
			 * Here are all the OperationSets that aTalk supported; to be queried by the
			 * application and take appropriate actions
			 */
//			String keepAliveStrValue = mAccountID.getAccountPropertyString(
//					ProtocolProviderFactory.KEEP_ALIVE_METHOD);

			// initialize the presence OperationSet
			InfoRetriever infoRetriever = new InfoRetriever(this, screenName);
			OperationSetPersistentPresenceJabberImpl persistentPresence
					= new OperationSetPersistentPresenceJabberImpl(this, infoRetriever);

			addSupportedOperationSet(OperationSetPersistentPresence.class, persistentPresence);

			// register it once again for those that simply need presence
			addSupportedOperationSet(OperationSetPresence.class, persistentPresence);

			if (accountID.getAccountPropertyString(
					ProtocolProviderFactory.ACCOUNT_READ_ONLY_GROUPS) != null) {
				addSupportedOperationSet(OperationSetPersistentPresencePermissions.class,
						new OperationSetPersistentPresencePermissionsJabberImpl(this));
			}

			// initialize the IM operation set
			OperationSetBasicInstantMessagingJabberImpl basicInstantMessaging
					= new OperationSetBasicInstantMessagingJabberImpl(this);

			addSupportedOperationSet(OperationSetBasicInstantMessaging.class,
					basicInstantMessaging);

			addSupportedOperationSet(OperationSetMessageCorrection.class, basicInstantMessaging);

			// The http://jabber.org/protocol/xhtml-im feature is included already in smack.
			addSupportedOperationSet(OperationSetExtendedAuthorizations.class,
					new OperationSetExtendedAuthorizationsJabberImpl(this, persistentPresence));

			// initialize the Whiteboard operation set
			addSupportedOperationSet(OperationSetWhiteboarding.class,
					new OperationSetWhiteboardingJabberImpl(this));

			// initialize the chat state notifications operation set
			addSupportedOperationSet(OperationSetChatStateNotifications.class,
					new OperationSetChatStateNotificationsJabberImpl(this));

			// Initialize the multi-user chat operation set
			addSupportedOperationSet(OperationSetMultiUserChat.class,
					new OperationSetMultiUserChatJabberImpl(this));

			addSupportedOperationSet(OperationSetJitsiMeetTools.class,
					new OperationSetJitsiMeetToolsJabberImpl(this));

			addSupportedOperationSet(OperationSetServerStoredContactInfo.class,
					new OperationSetServerStoredContactInfoJabberImpl(infoRetriever));

			OperationSetServerStoredAccountInfo accountInfo
					= new OperationSetServerStoredAccountInfoJabberImpl(
					this, infoRetriever, screenName);

			addSupportedOperationSet(OperationSetServerStoredAccountInfo.class, accountInfo);

			// Initialize avatar operation set
			addSupportedOperationSet(OperationSetAvatar.class,
					new OperationSetAvatarJabberImpl(this, accountInfo));

			// initialize the file transfer operation set
			addSupportedOperationSet(OperationSetFileTransfer.class,
					new OperationSetFileTransferJabberImpl(this));

			addSupportedOperationSet(OperationSetInstantMessageTransform.class,
					new OperationSetInstantMessageTransformImpl());

			// initialize the thumbNailed file factory operation set
			addSupportedOperationSet(OperationSetThumbnailedFileFactory.class,
					new OperationSetThumbnailedFileFactoryImpl());

			// initialize the telephony operation set
			boolean isCallingDisabled = JabberActivator.getConfigurationService()
					.getBoolean(IS_CALLING_DISABLED, false);
			boolean isCallingDisabledForAccount = accountID.getAccountPropertyBoolean(
					ProtocolProviderFactory.IS_CALLING_DISABLED_FOR_ACCOUNT, false);

			// Check if calling is enabled.
			if (!isCallingDisabled && !isCallingDisabledForAccount) {
				OperationSetBasicTelephonyJabberImpl basicTelephony
						= new OperationSetBasicTelephonyJabberImpl(this);
				addSupportedOperationSet(OperationSetAdvancedTelephony.class, basicTelephony);
				addSupportedOperationSet(OperationSetBasicTelephony.class, basicTelephony);
				addSupportedOperationSet(OperationSetSecureZrtpTelephony.class, basicTelephony);
				addSupportedOperationSet(OperationSetSecureSDesTelephony.class, basicTelephony);

				// initialize video telephony OperationSet
				addSupportedOperationSet(OperationSetVideoTelephony.class,
						new OperationSetVideoTelephonyJabberImpl(basicTelephony));
				addSupportedOperationSet(OperationSetTelephonyConferencing.class,
						new OperationSetTelephonyConferencingJabberImpl(this));
				addSupportedOperationSet(OperationSetBasicAutoAnswer.class,
						new OperationSetAutoAnswerJabberImpl(this));
				addSupportedOperationSet(OperationSetResourceAwareTelephony.class,
						new OperationSetResAwareTelephonyJabberImpl(basicTelephony));

				// Only init video bridge if enabled
				boolean isVideobridgeDisabled = JabberActivator.getConfigurationService()
						.getBoolean(OperationSetVideoBridge.IS_VIDEO_BRIDGE_DISABLED, false);

				if (!isVideobridgeDisabled) {
					// init video bridge
					addSupportedOperationSet(OperationSetVideoBridge.class,
							new OperationSetVideoBridgeImpl(this));
				}

				// init DTMF
				OperationSetDTMFJabberImpl operationSetDTMF = new OperationSetDTMFJabberImpl(this);
				addSupportedOperationSet(OperationSetDTMF.class, operationSetDTMF);

				addSupportedOperationSet(OperationSetIncomingDTMF.class,
						new OperationSetIncomingDTMFJabberImpl());

				// Check if desktop streaming is enabled.
				boolean isDesktopStreamingDisabled = JabberActivator.getConfigurationService()
						.getBoolean(IS_DESKTOP_STREAMING_DISABLED, false);
				boolean isAccountDesktopStreamingDisabled = accountID.getAccountPropertyBoolean(
						ProtocolProviderFactory.IS_DESKTOP_STREAMING_DISABLED, false);

				if (!isDesktopStreamingDisabled && !isAccountDesktopStreamingDisabled) {
					isDesktopSharingEnable = true;

					// initialize desktop streaming OperationSet
					addSupportedOperationSet(OperationSetDesktopStreaming.class,
							new OperationSetDesktopStreamingJabberImpl(basicTelephony));

					if (!accountID.getAccountPropertyBoolean(
							ProtocolProviderFactory.IS_DESKTOP_REMOTE_CONTROL_DISABLED, false)) {
						// initialize desktop sharing OperationSets
						addSupportedOperationSet(OperationSetDesktopSharingServer.class,
								new OperationSetDesktopSharingServerJabberImpl(basicTelephony));

						addSupportedOperationSet(OperationSetDesktopSharingClient.class,
								new OperationSetDesktopSharingClientJabberImpl(this));
					}
				}
			}

			// OperationSetContactCapabilities
			opsetContactCapabilities = new OperationSetContactCapabilitiesJabberImpl(this);
			if (discoveryManager != null)
				opsetContactCapabilities.setDiscoveryManager(discoveryManager);
			addSupportedOperationSet(OperationSetContactCapabilities.class,
					opsetContactCapabilities);

			addSupportedOperationSet(OperationSetGenericNotifications.class,
					new OperationSetGenericNotificationsJabberImpl(this));

			OperationSetChangePassword opsetChangePassword
					= new OperationSetChangePasswordJabberImpl(this);
			addSupportedOperationSet(OperationSetChangePassword.class, opsetChangePassword);

			OperationSetCusaxUtils opsetCusaxCusaxUtils
					= new OperationSetCusaxUtilsJabberImpl(this);
			addSupportedOperationSet(OperationSetCusaxUtils.class, opsetCusaxCusaxUtils);

			boolean isUserSearchEnabled = accountID.getAccountPropertyBoolean(
					IS_USER_SEARCH_ENABLED_PROPERTY, false);
			if (isUserSearchEnabled) {
				addSupportedOperationSet(OperationSetUserSearch.class,
						new OperationSetUserSearchJabberImpl(this));
			}
			OperationSetTLS opsetTLS = new OperationSetTLSJabberImpl(this);
			addSupportedOperationSet(OperationSetTLS.class, opsetTLS);

			OperationSetConnectionInfo opsetConnectionInfo
					= new OperationSetConnectionInfoJabberImpl();
			addSupportedOperationSet(OperationSetConnectionInfo.class, opsetConnectionInfo);
			isInitialized = true;
		}
	}

	/**
	 * Makes the service implementation close all open sockets and release any resources that it
	 * might have taken and prepare for shutdown/garbage collection.
	 */
	public void shutdown()
	{
		synchronized (initializationLock) {
			if (logger.isTraceEnabled())
				logger.trace("Killing the Jabber Protocol Provider.");

			// kill all active calls
			OperationSetBasicTelephonyJabberImpl telephony = (OperationSetBasicTelephonyJabberImpl)
					getOperationSet(OperationSetBasicTelephony.class);
			if (telephony != null) {
				telephony.shutdown();
			}
			disconnectAndCleanConnection();
			isInitialized = false;
		}
	}

	/**
	 * Returns true if the provider service implementation is initialized and ready for use by
	 * other services, and false otherwise.
	 *
	 * @return true if the provider is initialized and ready for use and false otherwise
	 */
	public boolean isInitialized()
	{
		return isInitialized;
	}

	/**
	 * Returns the AccountID that uniquely identifies the account represented by this instance of
	 * the ProtocolProviderService.
	 *
	 * @return the id of the account represented by this provider.
	 */
	public AccountID getAccountID()
	{
		return mAccountID;
	}

	/**
	 * Validates the node part of a JID and returns an error message if applicable and a
	 * suggested correction.
	 *
	 * @param contactId
	 * 		the contact identifier to validate
	 * @param result
	 * 		Must be supplied as an empty a list. Implementors add
	 * 		items:
	 * 		<ol>
	 * 		<li>is the error message if applicable</li>
	 * 		<li>a suggested correction. Index 1 is optional and can only be present if there was
	 * 		a validation failure.</li>
	 * 		</ol>
	 * @return true if the contact id is valid, false otherwise
	 */
	@Override
	public boolean validateContactAddress(String contactId, List<String> result)
	{
		if (result == null) {
			throw new IllegalArgumentException("result must be an empty list");
		}

		result.clear();
		try {
			contactId = contactId.trim();
			if (contactId.length() == 0) {
				result.add(JabberActivator.getResources().getI18NString(
						"impl.protocol.jabber.INVALID_ADDRESS", new String[]{contactId}));
				// no suggestion for an empty id
				return false;
			}

			String user = contactId;
			String remainder = "";
			int at = contactId.indexOf('@');
			if (at > -1) {
				user = contactId.substring(0, at);
				remainder = contactId.substring(at);
			}

			// <conforming-char> ::= #x21 | [#x23-#x25] | [#x28-#x2E] |
			// [#x30-#x39] | #x3B | #x3D | #x3F |
			// [#x41-#x7E] | [#x80-#xD7FF] |
			// [#xE000-#xFFFD] | [#x10000-#x10FFFF]
			boolean valid = true;
			String suggestion = "";
			for (char c : user.toCharArray()) {
				if (!(c == 0x21 || (c >= 0x23 && c <= 0x25)
						|| (c >= 0x28 && c <= 0x2e) || (c >= 0x30 && c <= 0x39)
						|| c == 0x3b || c == 0x3d || c == 0x3f
						|| (c >= 0x41 && c <= 0x7e) || (c >= 0x80 && c <= 0xd7ff)
						|| (c >= 0xe000 && c <= 0xfffd))) {
					valid = false;
				}
				else {
					suggestion += c;
				}
			}
			if (!valid) {
				result.add(JabberActivator.getResources().getI18NString(
						"impl.protocol.jabber.INVALID_ADDRESS", new String[]{contactId}));
				result.add(suggestion + remainder);
				return false;
			}

			return true;
		}
		catch (Exception ex) {
			result.add(JabberActivator.getResources().getI18NString(
					"impl.protocol.jabber.INVALID_ADDRESS", new String[]{contactId}));
		}
		return false;
	}

	/**
	 * Returns the <tt>XMPPConnection</tt>opened by this provider
	 *
	 * @return a reference to the <tt>XMPPConnection</tt> last opened by this provider.
	 */
	public XMPPTCPConnection getConnection()
	{
		return mConnection;
	}

	/**
	 * Determines whether a specific <tt>XMPPException</tt> signals that attempted login
	 * has failed.
	 * <p>
	 * Calling method will trigger a re-login dialog if the return <tt>failureMode</tt> is not
	 * <tt>SecurityAuthority.REASON_UNKNOWN</tt> etc
	 * <p>
	 * Add additional exMsg message if necessary to achieve this effect.
	 *
	 * @param ex
	 * 		the <tt>XMPPException</tt> which is to be determined whether it signals that attempted
	 * 		authentication has failed
	 * @return <tt>>>-1</tt> if the specified <tt>ex</tt> signals that attempted authentication is
	 * known' otherwise <tt>SecurityAuthority.REASON_UNKNOWN</tt> is returned.
	 * @see SecurityAuthority#REASON_UNKNOWN
	 */
	private int checkLoginFailMode(Exception ex)
	{
		int failureMode = SecurityAuthority.REASON_UNKNOWN;
		String exMsg = ex.getMessage().toLowerCase(Locale.US);

		/**
		 * As there are no defined type or reason specified for XMPPException, we try to
		 * determine the reason according to the received exception messages that are relevant
		 * and found in smack 4.2.0
		 */
		if (exMsg.contains("sasl") && (exMsg.contains("invalid")
				|| exMsg.contains("error") || exMsg.contains("failed"))) {
			failureMode = SecurityAuthority.INVALID_AUTHORIZATION;
		}
		else if (exMsg.contains("forbidden")) {
			failureMode = SecurityAuthority.AUTHENTICATION_FORBIDDEN;
		}
		else if (exMsg.contains("not-authorized")) {
			failureMode = SecurityAuthority.NOT_AUTHORIZED;
		}
		else if (exMsg.contains("unable to determine password")) {
			failureMode = SecurityAuthority.WRONG_PASSWORD;
		}
		else if (exMsg.contains("service-unavailable")
				|| exMsg.contains("tls is required")) {
			failureMode = SecurityAuthority.AUTHENTICATION_FAILED;
		}
		else if (exMsg.contains("remote-server-timeout")
				|| exMsg.contains("no response received within reply timeout")
				|| exMsg.contains("connection failed")) {
			failureMode = SecurityAuthority.CONNECTION_FAILED;
		}
		else if (exMsg.contains("remote-server-not-found")
				|| exMsg.contains("internal-server-error")) {
			failureMode = SecurityAuthority.NO_SERVER_FOUND;
		}
		else if (exMsg.contains("policy-violation")) {
			failureMode = SecurityAuthority.POLICY_VIOLATION;
		}
		return failureMode;
	}

	/**
	 * Tries to determine the appropriate message and status to fire, according to the exception.
	 *
	 * @param ex
	 * 		the {@link XMPPException} or {@link SmackException}  that caused the state change.
	 */
	private void fireRegistrationStateChanged(Exception ex)
	{
		RegistrationState regState = RegistrationState.UNREGISTERED;
		int reasonCode = RegistrationStateChangeEvent.REASON_NOT_SPECIFIED;

		int failMode = checkLoginFailMode(ex);
		if (failMode == SecurityAuthority.INVALID_AUTHORIZATION) {
			// JabberActivator.getProtocolProviderFactory().storePassword(mAccountID, null);
			regState = RegistrationState.AUTHENTICATION_FAILED;
			reasonCode = RegistrationStateChangeEvent.REASON_AUTHENTICATION_FAILED;
		}
		else if (failMode == SecurityAuthority.AUTHENTICATION_FORBIDDEN) {
			regState = RegistrationState.CONNECTION_FAILED;
			reasonCode = RegistrationStateChangeEvent.REASON_IB_REGISTRATION_FAILED;
		}
		else if (failMode == SecurityAuthority.NO_SERVER_FOUND) {
			regState = RegistrationState.CONNECTION_FAILED;
			reasonCode = RegistrationStateChangeEvent.REASON_SERVER_NOT_FOUND;
		}
		else if (failMode == SecurityAuthority.CONNECTION_FAILED) {
			regState = RegistrationState.CONNECTION_FAILED;
			reasonCode = RegistrationStateChangeEvent.REASON_NOT_SPECIFIED;
		}
		else if (failMode == SecurityAuthority.AUTHENTICATION_FAILED) {
			regState = RegistrationState.AUTHENTICATION_FAILED;
			reasonCode = RegistrationStateChangeEvent.REASON_TLS_REQUIRED;
		}

		if (regState == RegistrationState.UNREGISTERED
				|| regState == RegistrationState.CONNECTION_FAILED) {
			// we fired that for some reason we are going offline lets clean the connection state
			// for any future connections
			disconnectAndCleanConnection();
		}

		String reason = ex.getMessage();
		fireRegistrationStateChanged(getRegistrationState(), regState, reasonCode, reason);

		// Try re-register and ask user for new credentials giving detail reason description.
		if (ex instanceof XMPPErrorException) {
			reason = ((XMPPErrorException) ex).getXMPPError().getDescriptiveText();
		}
		if (failMode != SecurityAuthority.REASON_UNKNOWN) {
			reRegister(failMode, reason);
		}
	}

	/**
	 * Returns the jabber protocol icon.
	 *
	 * @return the jabber protocol icon
	 */
	public ProtocolIcon getProtocolIcon()
	{
		return jabberIcon;
	}

	/**
	 * Returns the current instance of <tt>JabberStatusEnum</tt>.
	 *
	 * @return the current instance of <tt>JabberStatusEnum</tt>.
	 */
	JabberStatusEnum getJabberStatusEnum()
	{
		return jabberStatusEnum;
	}

	/**
	 * Determines if the given list of <tt>features</tt> is supported by the specified jabber id.
	 *
	 * @param jid
	 * 		the jabber id for which to check
	 * @param features
	 * 		the list of features to check for
	 * @return <tt>true</tt> if the list of features is supported; otherwise, <tt>false</tt>
	 */
	public boolean isFeatureListSupported(Jid jid, String... features)
	{
		try {
			if (discoveryManager == null)
				return false;

			DiscoverInfo featureInfo = discoveryManager.discoverInfoNonBlocking(jid);

			if (featureInfo == null)
				return false;

			for (String feature : features) {
				if (!featureInfo.containsFeature(feature)) {
					// If one is not supported we return false and don't check the others.
					return false;
				}
			}
			return true;
		}
		catch (XMPPException e) {
			if (logger.isDebugEnabled())
				logger.debug("Failed to retrieve discovery info.", e);
		}
		return false;
	}

	/**
	 * Determines if the given list of <tt>features</tt> is supported by the specified jabber id.
	 *
	 * @param jid
	 * 		the jabber id that we'd like to get information about
	 * @param feature
	 * 		the feature to check for
	 * @return <tt>true</tt> if the list of features is supported, otherwise returns <tt>false</tt>
	 */
	public boolean isFeatureSupported(Jid jid, String feature)
	{
		return isFeatureListSupported(jid, feature);
	}

	/**
	 * Returns the full jabber id (jid) corresponding to the given contact. If the provider is not
	 * connected then just returns the given jid (BareJid).
	 *
	 * @param contact
	 * 		the contact, for which we're looking for a full jid
	 * @return the jid of the specified contact or bareJid if the provider is not yet connected;
	 */
	public Jid getFullJidIfPossible(Contact contact)
	{
		return getFullJidIfPossible(contact.getJid());
	}

	/**
	 * Returns the full jabber id (jid) for the given jid if possible. If the provider is not
	 * connected then just returns the given jid (BareJid).
	 *
	 * @param jid
	 * 		the contact jid (i.e. usually without resource) whose full jid we are looking for.
	 * @return the jid of the specified contact or bareJid if the provider is not yet connected;
	 */
	public Jid getFullJidIfPossible(Jid jid)
	{
		// when we are not connected there is no full jid
		if (mConnection != null && mConnection.isConnected()) {
			Roster roster = Roster.getInstanceFor(mConnection);
			if (roster != null)
				jid = roster.getPresence(jid.asBareJid()).getFrom();
		}
		return jid;
	}

	/**
	 * The trust manager which asks the client whether to trust particular certificate which is not
	 * globally trusted.
	 */
	private class HostTrustManager implements X509TrustManager
	{
		/**
		 * The default trust manager.
		 */
		private final X509TrustManager tm;

		/**
		 * Creates the custom trust manager.
		 *
		 * @param tm
		 * 		the default trust manager.
		 */
		HostTrustManager(X509TrustManager tm)
		{
			this.tm = tm;
		}

		/**
		 * Not used.
		 *
		 * @return nothing.
		 */
		public X509Certificate[] getAcceptedIssuers()
		{
			return new X509Certificate[0];
		}

		/**
		 * Not used.
		 *
		 * @param chain
		 * 		the cert chain.
		 * @param authType
		 * 		authentication type like: RSA.
		 * @throws CertificateException
		 * 		never
		 * @throws UnsupportedOperationException
		 * 		always
		 */
		public void checkClientTrusted(X509Certificate[] chain, String authType)
				throws CertificateException, UnsupportedOperationException
		{
			throw new UnsupportedOperationException();
		}

		/**
		 * Check whether a certificate is trusted, if not ask user whether he trust it.
		 *
		 * @param chain
		 * 		the certificate chain.
		 * @param authType
		 * 		authentication type like: RSA.
		 * @throws CertificateException
		 * 		not trusted.
		 */
		public void checkServerTrusted(X509Certificate[] chain, String authType)
				throws CertificateException
		{
			abortConnecting = true;
			try {
				tm.checkServerTrusted(chain, authType);
			}
			catch (CertificateException e) {
				// notify in a separate thread to avoid a deadlock when a reg state listener
				// accesses a synchronized XMPPConnection method (like getRoster)
				new Thread(new Runnable()
				{
					public void run()
					{
						fireRegistrationStateChanged(getRegistrationState(),
								RegistrationState.UNREGISTERED,
								RegistrationStateChangeEvent.REASON_USER_REQUEST,
								"Not trusted certificate");
					}
				}).start();
				throw e;
			}

			if (abortConnecting) {
				// connect hasn't finished we will continue normally
				abortConnecting = false;
			}
			else {
				// in this situation connect method has finished and it was disconnected so we
				// wont to connect. register.connect in new thread so we can release the current
				// connecting thread, otherwise this blocks jabber
				new Thread(new Runnable()
				{
					public void run()
					{
						reRegister(SecurityAuthority.CONNECTION_FAILED, null);
					}
				}).start();
			}
		}

	}

	/**
	 * Returns the currently valid {@link ScServiceDiscoveryManager}.
	 *
	 * @return the currently valid {@link ScServiceDiscoveryManager}.
	 */
	public ScServiceDiscoveryManager getDiscoveryManager()
	{
		return discoveryManager;
	}

	/**
	 * Return the EntityFullJid associate with this protocol provider.
	 * <p>
	 * Build our own EntityJid if not connected. May not be full compliant - For explanation
	 *
	 * @return the Jabber EntityFullJid
	 * @see AbstractXMPPConnection#user
	 */
	public EntityFullJid getOurJID()
	{
		EntityFullJid jid = null;
		if (mConnection != null) {
			jid = mConnection.getUser();
		}
		else {
			// get Jid from accountID if connection is not established.
			try {
				jid = JidCreate.entityFullFrom(mAccountID.getAccountJid());
			}
			catch (XmppStringprepException e) {
				e.printStackTrace();
			}
		}
		return jid;
	}

	/**
	 * Returns the <tt>InetAddress</tt> that is most likely to be to be used as a next hop when
	 * contacting our XMPP server. This is an utility method that is used whenever we have to
	 * choose one of our local addresses (e.g. when trying to pick a best candidate for raw udp).
	 * It is based on the assumption that, in absence of any more specific details, chances are
	 * that we will be accessing remote destinations via the same interface that we are using to
	 * access our jabber server.
	 *
	 * @return the <tt>InetAddress</tt> that is most likely to be to be used as a next hop when
	 * contacting our server.
	 * @throws IllegalArgumentException
	 * 		if we don't have a valid server.
	 */
	public InetAddress getNextHop()
			throws IllegalArgumentException
	{
		InetAddress nextHop;
		String nextHopStr;

		if (proxy != null) {
			nextHopStr = proxy.getProxyAddress();
		}
		else {
			nextHopStr = getConnection().getHost();
		}

		try {
			nextHop = NetworkUtils.getInetAddress(nextHopStr);
		}
		catch (UnknownHostException ex) {
			throw new IllegalArgumentException("seems we don't have a valid next hop.", ex);
		}

		if (logger.isDebugEnabled())
			logger.debug("Returning address " + nextHop + " as next hop.");
		return nextHop;
	}

	/**
	 * Start auto-discovery of JingleNodes tracker/relays.
	 */
	public void startJingleNodesDiscovery()
	{
		// Jingle Nodes Service Initialization
		final JabberAccountIDImpl accID = (JabberAccountIDImpl) mAccountID;
		final SmackServiceNode service = new SmackServiceNode(mConnection, 60000);
		// make sure SmackServiceNode will clean up when connection is closed
		mConnection.addConnectionListener(service);

		for (JingleNodeDescriptor desc : accID.getJingleNodes()) {
			TrackerEntry entry = null;
			try {
				entry = new TrackerEntry(desc.isRelaySupported()
						? TrackerEntry.Type.relay : TrackerEntry.Type.tracker,
						TrackerEntry.Policy._public,
						JidCreate.from(desc.getJID()), JingleChannelIQ.UDP);
			}
			catch (XmppStringprepException e) {
				e.printStackTrace();
			}
			service.addTrackerEntry(entry);
		}
		new Thread(new JingleNodesServiceDiscovery(service, mConnection, accID,
				jingleNodesSyncRoot)).start();
		jingleNodesServiceNode = service;
	}

	/**
	 * Get the Jingle Nodes service. Note that this method will block until Jingle Nodes auto
	 * discovery (if enabled) finished.
	 *
	 * @return Jingle Nodes service
	 */
	public SmackServiceNode getJingleNodesServiceNode()
	{
		synchronized (jingleNodesSyncRoot) {
			return jingleNodesServiceNode;
		}
	}

	/**
	 * Logs a specific message and associated <tt>Throwable</tt> cause as an error using the
	 * current <tt>Logger</tt> and then throws a new <tt>OperationFailedException</tt> with the
	 * message, a specific error code and the cause.
	 *
	 * @param message
	 * 		the message to be logged and then wrapped in a new <tt>OperationFailedException</tt>
	 * @param errorCode
	 * 		the error code to be assigned to the new <tt>OperationFailedException</tt>
	 * @param cause
	 * 		the <tt>Throwable</tt> that has caused the necessity to log an error and have a new
	 * 		<tt>OperationFailedException</tt> thrown
	 * @param logger
	 * 		the logger that we'd like to log the error <tt>message</tt> and <tt>cause</tt>.
	 * @throws OperationFailedException
	 * 		the exception that we wanted this method to throw.
	 */
	public static void throwOperationFailedException(String message, int errorCode,
			Throwable cause, Logger logger)
			throws OperationFailedException
	{
		logger.error(message, cause);
		if (cause == null)
			throw new OperationFailedException(message, errorCode);
		else
			throw new OperationFailedException(message, errorCode, cause);
	}

	/**
	 * Used when we need to re-register or someone needs to obtain credentials.
	 *
	 * @return the SecurityAuthority.
	 */
	public SecurityAuthority getAuthority()
	{
		return mAuthority;
	}

	public UserCredentials getUserCredentials()
	{
		return userCredentials;
	}

	/**
	 * Returns true if our account is a Gmail or a Google Apps ones.
	 *
	 * @return true if our account is a Gmail or a Google Apps ones.
	 */
	public boolean isGmailOrGoogleAppsAccount()
	{
		String domain = XmppStringUtils.parseDomain(mAccountID.getUserID());
		return isGmailOrGoogleAppsAccount(domain);
	}

	/**
	 * Returns true if our account is a Gmail or a Google Apps ones.
	 *
	 * @param domain
	 * 		domain to check
	 * @return true if our account is a Gmail or a Google Apps ones.
	 */
	public static boolean isGmailOrGoogleAppsAccount(String domain)
	{
		SRVRecord srvRecords[];

		try {
			srvRecords = NetworkUtils.getSRVRecords("xmpp-client", "tcp", domain);
		}
		catch (ParseException e) {
			logger.info("Failed to get SRV records for XMPP domain");
			return false;
		}
		catch (DnssecException e) {
			logger.error("DNSSEC failure while checking for google domains", e);
			return false;
		}

		if (srvRecords == null) {
			return false;
		}

		for (SRVRecord srv : srvRecords) {
			if (srv.getTarget().endsWith("google.com")
					|| srv.getTarget().endsWith("google.com.")) {
				return true;
			}
		}
		return false;
	}

	/**
	 * Gets the entity PRE_KEY_ID of the first Jitsi Videobridge associated with
	 * {@link #mConnection} i.e.
	 * provided by the <tt>serviceName</tt> of <tt>mConnection</tt>.
	 * Abort checking if last check returned with NoResponseException. Await 45s wait time
	 *
	 * @return the entity PRE_KEY_ID of the first Jitsi Videobridge associated with
	 * <tt>mConnection</tt>
	 */
	public Jid getJitsiVideobridge()
	{
		if (mConnection != null && mConnection.isConnected()) {
			ScServiceDiscoveryManager discoveryManager = getDiscoveryManager();
			DomainBareJid serviceName = mConnection.getServiceName();
			DiscoverItems discoverItems = null;

			try {
				discoverItems = discoveryManager.discoverItems(serviceName);
			}
			catch (NoResponseException | NotConnectedException | XMPPException
					| InterruptedException ex) {
				if (logger.isDebugEnabled()) {
					logger.debug("Failed to discover the items associated with Jabber entity: "
							+ serviceName, ex);
				}
			}

			if ((discoverItems != null) && !isLastVbNoResponse) {
				List<DiscoverItems.Item> discoverItemIter = discoverItems.getItems();

				for (DiscoverItems.Item discoverItem : discoverItemIter) {
					Jid entityID = discoverItem.getEntityID();
					DiscoverInfo discoverInfo = null;

					try {
						discoverInfo = discoveryManager.discoverInfo(entityID);
					}
					catch (NoResponseException | NotConnectedException | XMPPException
							| InterruptedException ex) {
						logger.warn("Failed to discover information about Jabber entity: "
								+ entityID, ex);
						if (ex instanceof NoResponseException) {
							isLastVbNoResponse = true;
						}
					}
					if ((discoverInfo != null)
							&& discoverInfo.containsFeature(ColibriConferenceIQ.NAMESPACE)) {
						return entityID;
					}
				}
			}
		}
		return null;
	}

	/**
	 * Load jabber service class, their static context will register what is needed. Used in
	 * android as when using the other jars these services are loaded from the jar manifest.
	 */
	private static void loadJabberServiceClasses()
	{
		try {
			// pre-configure smack in android just to load class to init their static blocks
			SmackConfiguration.getVersion();
			Class.forName(ServiceDiscoveryManager.class.getName());
			Class.forName(DelayInformation.class.getName());
			Class.forName(DelayInformationProvider.class.getName());
			Class.forName(Socks5BytestreamManager.class.getName());
			Class.forName(XHTMLManager.class.getName());
			Class.forName(InBandBytestreamManager.class.getName());
		}
		catch (ClassNotFoundException e) {
			logger.error("Error loading classes in smack", e);
		}
	}

	/**
	 * Sets the traffic class for the XMPP signalling socket.
	 */
	private void setTrafficClass()
	{
		Socket s = getSocket();
		if (s != null) {
			ConfigurationService configService = JabberActivator.getConfigurationService();
			String dscp = configService.getString(XMPP_DSCP_PROPERTY);

			if (dscp != null) {
				try {
					int dscpInt = Integer.parseInt(dscp) << 2;
					if (dscpInt > 0)
						s.setTrafficClass(dscpInt);
				}
				catch (Exception e) {
					logger.info("Failed to set trafficClass", e);
				}
			}
		}
	}

	/**
	 * Return no null if the SSL socket (if TLS used).
	 * Retrieve the XMPP connection secureSocket use in protocolProvider (by reflection)
	 *
	 * @return The SSL socket or null if not used
	 */
	public SSLSocket getSSLSocket()
	{
		SSLSocket secureSocket = null;
		if (mConnection != null && mConnection.isConnected()) {
			try {
				Field field = XMPPTCPConnection.class.getDeclaredField("secureSocket");
				field.setAccessible(true);
				secureSocket = (SSLSocket) field.get(mConnection);
			}
			catch (NoSuchFieldException | IllegalAccessException e) {
				logger.warn("Access to XMPPTCPConnection.secureSocket not found!");
			}
		}
		return secureSocket;
	}

	/**
	 * Retrieve the XMPP connection socket used by the protocolProvider (by reflection)
	 *
	 * @return the socket which is used for this connection.
	 * @see XMPPTCPConnection#socket
	 */
	public Socket getSocket()
	{
		Socket socket = null;
		if (mConnection != null && mConnection.isConnected()) {
			try {
				Field field = XMPPTCPConnection.class.getDeclaredField("socket");
				field.setAccessible(true);
				socket = (Socket) field.get(mConnection);
			}
			catch (NoSuchFieldException | IllegalAccessException e) {
				logger.warn("Access to XMPPTCPConnection.socket not found!");
			}
		}
		return socket;
	}
}
