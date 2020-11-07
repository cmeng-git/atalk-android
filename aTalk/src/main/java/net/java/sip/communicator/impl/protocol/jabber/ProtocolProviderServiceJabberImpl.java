/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package net.java.sip.communicator.impl.protocol.jabber;

import android.content.Context;
import android.net.*;
import android.os.Build;
import android.text.TextUtils;

import net.java.sip.communicator.impl.certificate.CertificateServiceImpl;
import net.java.sip.communicator.impl.msghistory.MessageHistoryServiceImpl;
import net.java.sip.communicator.service.certificate.CertificateConfigEntry;
import net.java.sip.communicator.service.certificate.CertificateService;
import net.java.sip.communicator.service.msghistory.MessageHistoryService;
import net.java.sip.communicator.service.protocol.*;
import net.java.sip.communicator.service.protocol.event.RegistrationStateChangeEvent;
import net.java.sip.communicator.service.protocol.jabber.JabberAccountID;
import net.java.sip.communicator.service.protocol.jabberconstants.JabberStatusEnum;
import net.java.sip.communicator.util.ConfigurationUtils;
import net.java.sip.communicator.util.NetworkUtils;

import org.apache.commons.lang3.StringUtils;
import org.atalk.android.*;
import org.atalk.android.gui.AndroidGUIActivator;
import org.atalk.android.gui.aTalk;
import org.atalk.android.gui.account.settings.BoshProxyDialog;
import org.atalk.android.gui.call.JingleMessageHelper;
import org.atalk.android.gui.dialogs.DialogActivity;
import org.atalk.android.gui.login.LoginSynchronizationPoint;
import org.atalk.android.gui.util.AndroidUtils;
import org.atalk.android.plugin.timberlog.TimberLog;
import org.atalk.crypto.omemo.AndroidOmemoService;
import org.atalk.service.configuration.ConfigurationService;
import org.atalk.service.neomedia.SrtpControlType;
import org.atalk.util.OSUtils;
import org.jivesoftware.smack.*;
import org.jivesoftware.smack.ConnectionConfiguration.DnssecMode;
import org.jivesoftware.smack.ConnectionConfiguration.SecurityMode;
import org.jivesoftware.smack.SmackException.*;
import org.jivesoftware.smack.XMPPException.StreamErrorException;
import org.jivesoftware.smack.XMPPException.XMPPErrorException;
import org.jivesoftware.smack.bosh.BOSHConfiguration;
import org.jivesoftware.smack.bosh.XMPPBOSHConnection;
import org.jivesoftware.smack.packet.*;
import org.jivesoftware.smack.packet.StanzaError.Condition;
import org.jivesoftware.smack.provider.ExtensionElementProvider;
import org.jivesoftware.smack.provider.ProviderManager;
import org.jivesoftware.smack.proxy.ProxyInfo;
import org.jivesoftware.smack.roster.Roster;
import org.jivesoftware.smack.roster.rosterstore.DirectoryRosterStore;
import org.jivesoftware.smack.roster.rosterstore.RosterStore;
import org.jivesoftware.smack.tcp.XMPPTCPConnection;
import org.jivesoftware.smack.tcp.XMPPTCPConnectionConfiguration;
import org.jivesoftware.smack.util.SslContextFactory;
import org.jivesoftware.smack.util.TLSUtils;
import org.jivesoftware.smack.util.dns.minidns.MiniDnsDane;
import org.jivesoftware.smackx.avatar.AvatarManager;
import org.jivesoftware.smackx.avatar.useravatar.UserAvatarManager;
import org.jivesoftware.smackx.avatar.useravatar.packet.AvatarData;
import org.jivesoftware.smackx.avatar.useravatar.packet.AvatarMetadata;
import org.jivesoftware.smackx.avatar.useravatar.provider.AvatarDataProvider;
import org.jivesoftware.smackx.avatar.useravatar.provider.AvatarMetadataProvider;
import org.jivesoftware.smackx.avatar.vcardavatar.VCardAvatarManager;
import org.jivesoftware.smackx.avatar.vcardavatar.packet.VCardTempXUpdate;
import org.jivesoftware.smackx.avatar.vcardavatar.provider.VCardTempXUpdateProvider;
import org.jivesoftware.smackx.bob.element.BoBExt;
import org.jivesoftware.smackx.bob.provider.BoBExtensionProvider;
import org.jivesoftware.smackx.bytestreams.ibb.InBandBytestreamManager;
import org.jivesoftware.smackx.bytestreams.ibb.packet.DataPacketExtension;
import org.jivesoftware.smackx.bytestreams.socks5.Socks5BytestreamManager;
import org.jivesoftware.smackx.bytestreams.socks5.packet.Bytestream;
import org.jivesoftware.smackx.caps.EntityCapsManager;
import org.jivesoftware.smackx.caps.packet.CapsExtension;
import org.jivesoftware.smackx.caps.provider.CapsExtensionProvider;
import org.jivesoftware.smackx.captcha.packet.CaptchaExtension;
import org.jivesoftware.smackx.captcha.provider.CaptchaProvider;
import org.jivesoftware.smackx.chatstates.packet.ChatStateExtension;
import org.jivesoftware.smackx.delay.packet.DelayInformation;
import org.jivesoftware.smackx.delay.provider.DelayInformationProvider;
import org.jivesoftware.smackx.disco.ServiceDiscoveryManager;
import org.jivesoftware.smackx.disco.packet.DiscoverInfo;
import org.jivesoftware.smackx.disco.packet.DiscoverItems;
import org.jivesoftware.smackx.filetransfer.FileTransferNegotiator;
import org.jivesoftware.smackx.httpauthorizationrequest.HTTPAuthorizationRequestListener;
import org.jivesoftware.smackx.httpauthorizationrequest.HTTPAuthorizationRequestManager;
import org.jivesoftware.smackx.httpauthorizationrequest.element.ConfirmExtension;
import org.jivesoftware.smackx.httpauthorizationrequest.provider.ConfirmExtProvider;
import org.jivesoftware.smackx.httpauthorizationrequest.provider.ConfirmIQProvider;
import org.jivesoftware.smackx.iqlast.LastActivityManager;
import org.jivesoftware.smackx.iqregisterx.packet.Registration;
import org.jivesoftware.smackx.iqregisterx.provider.RegistrationProvider;
import org.jivesoftware.smackx.iqregisterx.provider.RegistrationStreamFeatureProvider;
import org.jivesoftware.smackx.iqversion.VersionManager;
import org.jivesoftware.smackx.jinglemessage.JingleMessageManager;
import org.jivesoftware.smackx.jinglemessage.packet.JingleMessage;
import org.jivesoftware.smackx.message_correct.element.MessageCorrectExtension;
import org.jivesoftware.smackx.muc.packet.MUCInitialPresence;
import org.jivesoftware.smackx.nick.packet.Nick;
import org.jivesoftware.smackx.nick.provider.NickProvider;
import org.jivesoftware.smackx.ping.PingFailedListener;
import org.jivesoftware.smackx.ping.PingManager;
import org.jivesoftware.smackx.receipts.DeliveryReceipt;
import org.jivesoftware.smackx.receipts.DeliveryReceiptManager;
import org.jivesoftware.smackx.receipts.DeliveryReceiptManager.AutoReceiptMode;
import org.jivesoftware.smackx.si.packet.StreamInitiation;
import org.jivesoftware.smackx.vcardtemp.packet.VCard;
import org.jivesoftware.smackx.xhtmlim.XHTMLManager;
import org.jivesoftware.smackx.xhtmlim.packet.XHTMLExtension;
import org.jxmpp.jid.*;
import org.jxmpp.jid.impl.JidCreate;
import org.jxmpp.jid.parts.Resourcepart;
import org.jxmpp.stringprep.XmppStringprepException;
import org.jxmpp.util.XmppStringUtils;
import org.minidns.dnsname.DnsName;
import org.minidns.dnssec.DnssecValidationFailedException;
import org.minidns.record.SRV;
import org.osgi.framework.ServiceReference;
import org.xmlpull.v1.XmlPullParserException;
import org.xmpp.extensions.DefaultExtensionElementProvider;
import org.xmpp.extensions.coin.CoinIQ;
import org.xmpp.extensions.coin.CoinIQProvider;
import org.xmpp.extensions.colibri.ColibriConferenceIQ;
import org.xmpp.extensions.colibri.ColibriIQProvider;
import org.xmpp.extensions.condesc.ConferenceDescriptionExtension;
import org.xmpp.extensions.condesc.ConferenceDescriptionExtensionProvider;
import org.xmpp.extensions.inputevt.InputEvtIQ;
import org.xmpp.extensions.inputevt.InputEvtIQProvider;
import org.xmpp.extensions.jibri.JibriIq;
import org.xmpp.extensions.jibri.JibriIqProvider;
import org.xmpp.extensions.jingle.*;
import org.xmpp.extensions.jingle.element.Jingle;
import org.xmpp.extensions.jingle.provider.JingleProvider;
import org.xmpp.extensions.jingleinfo.JingleInfoQueryIQ;
import org.xmpp.extensions.jingleinfo.JingleInfoQueryIQProvider;
import org.xmpp.extensions.jitsimeet.*;
import org.xmpp.extensions.thumbnail.Thumbnail;
import org.xmpp.extensions.thumbnail.ThumbnailStreamInitiationProvider;
import org.xmpp.jnodes.smack.*;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Field;
import java.math.BigInteger;
import java.net.*;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.*;

import javax.net.SocketFactory;
import javax.net.ssl.*;

import timber.log.Timber;

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
 * @author MilanKral
 */
public class ProtocolProviderServiceJabberImpl extends AbstractProtocolProviderService
        implements PingFailedListener, HTTPAuthorizationRequestListener, DialogActivity.DialogListener
{
    /**
     * Jingle's Discovery Info common URN.
     */
    public static final String URN_XMPP_JINGLE = Jingle.NAMESPACE;

    /**
     * Jingle's Discovery Info URN for RTP support.
     */
    public static final String URN_XMPP_JINGLE_RTP = RtpDescriptionExtension.NAMESPACE;

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
    public static final String URN_XMPP_JINGLE_RTP_ZRTP = ZrtpHashExtension.NAMESPACE;

    /**
     * Jingle's Discovery Info URN for ICE_UDP transport support.
     */
    public static final String URN_XMPP_JINGLE_RAW_UDP_0 = RawUdpTransportExtension.NAMESPACE;

    /**
     * Jingle's Discovery Info URN for ICE_UDP transport support.
     */
    public static final String URN_XMPP_JINGLE_ICE_UDP_1 = IceUdpTransportExtension.NAMESPACE;

    /**
     * Jingle's Discovery Info URN for Jingle Nodes support.
     */
    public static final String URN_XMPP_JINGLE_NODES = "http://jabber.org/protocol/jinglenodes";

    /**
     * Jingle's Discovery Info URN for "XEP-0251: Jingle Session Transfer" support.
     */
    public static final String URN_XMPP_JINGLE_TRANSFER_0 = TransferExtension.NAMESPACE;

    /**
     * Jingle's Discovery Info URN for
     * XEP-0298: Delivering Conference Information to Jingle Participants (Coin)
     */
    public static final String URN_XMPP_JINGLE_COIN = "urn:xmpp:coin";

    /**
     * Jingle's Discovery Info URN for &quot;XEP-0320: Use of DTLS-SRTP in Jingle Sessions&quot;.
     * "urn:xmpp:jingle:apps:dtls:0"
     */
    public static final String URN_XMPP_JINGLE_DTLS_SRTP = DtlsFingerprintExtension.NAMESPACE;

    /**
     * Discovery Info URN for classic RFC3264-style Offer/Answer negotiation with no support for
     * Trickle ICE and low tolerance to transport/payload separation. Defined in XEP-0176
     */
    public static final String URN_IETF_RFC_3264 = "urn:ietf:rfc:3264";

    /**
     * https://xmpp.org/extensions/xep-0092.html Software Version.
     */
    // Used in JVB
    public static final String URN_XMPP_IQ_VERSION = "jabber:iq:version";

    /**
     * URN for XEP-0077 inband registration
     */
    public static final String URN_REGISTER = "jabber:iq:register";

    /*
     * Determines the requested DNSSEC security mode.
     * <b>Note that Smack's support for DNSSEC/DANE is experimental!</b>
     *
     * The default '{@link #disabled}' means that neither DNSSEC nor DANE verification will be performed. When
     * '{@link #needsDnssec}' is used, then the connection will not be established if the resource records used
     * to connect to the XMPP service are not authenticated by DNSSEC. Additionally, if '{@link #needsDnssecAndDane}'
     * is used, then the XMPP service's TLS certificate is verified using DANE.
     */
    // Do not perform any DNSSEC authentication or DANE verification.
    private static final String DNSSEC_DISABLE = "disabled";

    // Require all DNS information to be authenticated by DNSSEC.
    private static final String DNSSEC_ONLY = "needsDnssec";

    // Require all DNS information to be authenticated by DNSSEC and require the XMPP service's TLS certificate
    // to be verified using DANE.
    private static final String DNSSEC_AND_DANE = "needsDnssecAndDane";

    /**
     * The name of the property under which the user may specify if the desktop streaming or sharing should be disabled.
     */
    private static final String IS_DESKTOP_STREAMING_DISABLED = "protocol.jabber.DESKTOP_STREAMING_DISABLED";

    /**
     * The name of the property under which the user may specify if audio/video calls should be disabled.
     */
    private static final String IS_CALLING_DISABLED = "protocol.jabber.CALLING_DISABLED";

    /**
     * Smack packet maximum reply timeout - Smack will immediately return on reply or until timeout
     * before issues exception. Need this to take care for some servers response on some packages
     * e.g. disco#info (30 seconds). Also on some slow client e.g. Samsung SII takes up to 30
     * Sec to response to sasl authentication challenge on first login
     */
    public static final int SMACK_REPLY_EXTENDED_TIMEOUT_30 = 30000;  // 30 seconds

    // vCard save takes about 29 seconds on Note 8
    public static final int SMACK_REPLY_EXTENDED_TIMEOUT_40 = 40000;  // 40 seconds

    public static final int SMACK_REPLY_OMEMO_INIT_TIMEOUT = 15000;

    /**
     * aTalk Smack packet reply default timeout - use Smack default instead of 10s (starting v2.1.8).
     * Too many FFR on ANR at smack.StanzaCollector.nextResult (StanzaCollector.java:206) when server is not responding.
     * - change the xmppConnect replyTimeout to smack default of 5 seconds under normal operation.
     */
    public static final int SMACK_REPLY_TIMEOUT_DEFAULT = SmackConfiguration.getDefaultReplyTimeout();

    public static final int DEFAULT_PORT = 5222;

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
    private AbstractXMPPConnection mConnection = null;

    /**
     * The InetSocketAddress of the XMPP server.
     */
    private InetSocketAddress mInetSocketAddress;

    /**
     * Indicates whether or not the provider is initialized and ready for use.
     */
    private boolean isInitialized = false;

    /**
     * False to disable resetting the smack reply timer on completion;
     * let AndroidOmemoService do it as the task is async
     */
    private boolean resetSmackTimer = true;

    /**
     * We use this to lock access to initialization.
     */
    private final Object initializationLock = new Object();

    /**
     * The identifier of the account that this provider represents.
     */
    private JabberAccountID mAccountID = null;

    /**
     * Used when we need to re-register
     */
    private SecurityAuthority mAuthority = null;

    /**
     * The resource we will use when connecting during this run.
     */
    private Resourcepart mResource = null;

    /**
     * The icon corresponding to the jabber protocol.
     */
    private ProtocolIconJabberImpl jabberIcon;

    private boolean isDesktopSharingEnable = false;

    /**
     * Persistent Storage for Roster Versioning support.
     */
    private File rosterStoreDirectory;

    private Roster mRoster = null;

    /**
     * A set of features supported by our Jabber implementation. In general, we add new feature(s)
     * when we add new operation sets.
     * (see xep-0030 : https://www.xmpp.org/extensions/xep-0030.html#info).
     * Example : to tell the world that we support jingle, we simply have to do :
     * supportedFeatures.add("https://www.xmpp.org/extensions/xep-0166.html#ns"); Beware there is no
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

    private ReconnectionManager reconnectionManager = null;

    private AndroidOmemoService androidOmemoService = null;

    private HTTPAuthorizationRequestManager httpAuthorizationRequestManager = null;

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
     * Listens for XMPP connection state or errors.
     */
    private XMPPConnectionListener xmppConnectionListener;

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
    public static int defaultPingInterval = 240;  // 4 minutes

    public static String defaultMinimumTLSversion = TLSUtils.PROTO_TLSV1_2;

    /**
     * Flag to indicate the network type connection before the ConnectionClosedOnError occur
     * Note: Switching between WiFi and Mobile network will also causes ConnectionClosedOnError to occur
     */
    private boolean isLastConnectionMobile = false;

    /**
     * Flag to indicate the connected mobile network has ConnectionClosedOnError due to:
     * 1. Disconnection occur while it is in connected with Mobile network
     * 2. Occur within 500mS of the ping action
     * i.e. to discard ConnectionClosedOnError due to other factors e.g. network fading etc
     */
    private boolean isMobilePingClosedOnError = false;

    /**
     * Set to success if protocol provider has successfully connected to the server.
     */
    private LoginSynchronizationPoint<XMPPException> xmppConnected;

    /**
     * Set to success if account has registered on server via inBand Registration.
     */
    public LoginSynchronizationPoint<XMPPException> accountIBRegistered;

    /**
     * Set to success if account has authenticated with the server.
     */
    private LoginSynchronizationPoint<XMPPException> accountAuthenticated;

    // load xmpp manager classes
    static {
        if (OSUtils.IS_ANDROID)
            loadJabberServiceClasses();
    }

    /**
     * An <tt>OperationSet</tt> that allows access to connection information used by the protocol provider.
     */
    private class OperationSetConnectionInfoJabberImpl implements OperationSetConnectionInfo
    {
        /**
         * @return The XMPP server hostAddress.
         */
        @Override
        public InetSocketAddress getServerAddress()
        {
            return mInetSocketAddress;
        }
    }

    /**
     * Returns the state of the account login state of this protocol provider
     * Note: RegistrationState is not inBand Registration
     *
     * @return the <tt>RegistrationState</tt> ot the provider is currently in.
     */
    public RegistrationState getRegistrationState()
    {
        if (mConnection == null) {
            if (inConnectAndLogin)
                return RegistrationState.REGISTERING;
            else
                return RegistrationState.UNREGISTERED;
        }
        else {
            if (mConnection.isAuthenticated()) {
                return RegistrationState.REGISTERED;
            }
            else {
                if (mConnection.isConnected() || ((mConnection instanceof XMPPTCPConnection)
                        && ((XMPPTCPConnection) mConnection).isDisconnectedButSmResumptionPossible())) {
                    return RegistrationState.REGISTERING;
                }
            }
        }
        return RegistrationState.UNREGISTERED;
    }

    /**
     * Return the certificate verification service impl.
     *
     * @return the CertificateVerification service.
     */
    private CertificateService getCertificateVerificationService()
    {
        if (guiVerification == null) {
            ServiceReference<?> guiVerifyReference
                    = JabberActivator.getBundleContext().getServiceReference(CertificateService.class.getName());
            if (guiVerifyReference != null) {
                guiVerification
                        = ((CertificateService) JabberActivator.getBundleContext().getService(guiVerifyReference));
            }
        }
        return guiVerification;
    }

    /**
     * Starts the registration process. Connection details such as registration server, user
     * name/number are provided through the configuration service through implementation specific properties.
     *
     * @param authority the security authority that will be used for resolving any security challenges that
     * may be returned during the registration or at any moment while we're registered.
     * @throws OperationFailedException with the corresponding code it the registration fails for some reason
     * (e.g. a networking error or an implementation problem).
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
            initializeConnectAndLogin(authority, SecurityAuthority.AUTHENTICATION_REQUIRED, loginReason, false);
        } catch (XMPPException | SmackException ex) {
            Timber.e("Error registering: %s", ex.getMessage());
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
     * @param authReasonCode indicates the reason of the re-authentication.
     */
    private void reRegister(int authReasonCode, String loginReason)
    {
        try {
            Timber.d("SMACK: Trying to re-register account!");

            // set to indicate the account has not registered during the registration process
            this.unregisterInternal(false);
            // reset states
            abortConnecting = false;

            // indicate we started connectAndLogin process
            synchronized (connectAndLoginLock) {
                inConnectAndLogin = true;
            }
            initializeConnectAndLogin(mAuthority, authReasonCode, loginReason, true);
        } catch (OperationFailedException ex) {
            Timber.e("Error reRegistering: %s", ex.getMessage());
            eventDuringLogin = null;
            disconnectAndCleanConnection();
            fireRegistrationStateChanged(getRegistrationState(),
                    RegistrationState.CONNECTION_FAILED, RegistrationStateChangeEvent.REASON_INTERNAL_ERROR, null);
        } catch (XMPPException | SmackException ex) {
            Timber.e("Error ReRegistering: %s", ex.getMessage());
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
     * @param authority SecurityAuthority
     * @param reasonCode the authentication reason code. Indicates the reason of this authentication.
     * @throws XMPPException if we cannot connect to the server - network problem
     * @throws OperationFailedException if login parameters as server port are not correct
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
            userCredentials = loginStrategy.prepareLogin(authority, reasonCode, loginReason, isShowAlways);
            if (!loginStrategy.loginPreparationSuccessful()
                    || ((userCredentials != null) && userCredentials.isUserCancel()))
                return;

            loadResource();
            loadProxy();

            String userID;
            /*
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
                connectAndLogin(userID, loginStrategy);
            } catch (XMPPException | SmackException ex) {
                // server disconnect us after such an error, do cleanup or connection denied.
                disconnectAndCleanConnection();
                throw ex; // rethrow the original exception
            } finally {
                // Reset to Smack default on login process completion
                if ((mConnection != null) && resetSmackTimer)
                    mConnection.setReplyTimeout(SMACK_REPLY_TIMEOUT_DEFAULT);
                resetSmackTimer = true;
            }
        }
    }

    /**
     * Creates the JabberLoginStrategy to use for the current account.
     */
    private JabberLoginStrategy createLoginStrategy()
    {
        ConnectionConfiguration.Builder<?, ?> ccBuilder;
        if (mAccountID.isBOSHEnable()) {
            ccBuilder = BOSHConfiguration.builder();
        }
        else {
            ccBuilder = XMPPTCPConnectionConfiguration.builder();
        }

        if (mAccountID.isAnonymousAuthUsed()) {
            return new AnonymousLoginStrategy(mAccountID.getAuthorizationName(), ccBuilder);
        }

        String clientCertId = mAccountID.getTlsClientCertificate();
        if ((clientCertId != null) && !clientCertId.equals(CertificateConfigEntry.CERT_NONE.toString())) {
            return new LoginByClientCertificateStrategy(mAccountID, ccBuilder);
        }
        else {
            return new LoginByPasswordStrategy(this, mAccountID, ccBuilder);
        }
    }

    /**
     * Initializes the Jabber Resource identifier: default or auto generated.
     */
    private void loadResource()
    {
        if (mResource != null)
            return;

        String sResource = mAccountID.getAccountPropertyString(ProtocolProviderFactory.RESOURCE, DEFAULT_RESOURCE);
        boolean autoGenRes = mAccountID.getAccountPropertyBoolean(ProtocolProviderFactory.AUTO_GENERATE_RESOURCE, true);
        if (autoGenRes) {
            SecureRandom random = new SecureRandom();
            sResource += "-" + new BigInteger(32, random).toString(32);
        }
        try {
            mResource = Resourcepart.from(sResource);
        } catch (XmppStringprepException e) {
            e.printStackTrace();
        }
    }

    /**
     * Sets the proxy information as per account proxy setting
     *
     * @throws OperationFailedException for incorrect proxy parameters being specified
     */
    private void loadProxy()
            throws OperationFailedException
    {
        if (mAccountID.isUseProxy()) {
            String proxyType = mAccountID.getAccountPropertyString(
                    ProtocolProviderFactory.PROXY_TYPE, mAccountID.getProxyType());

            if (mAccountID.isBOSHEnable()) {
                if (!mAccountID.isBoshHttpProxyEnabled()) {
                    proxy = null;
                    return;
                }
                else
                    proxyType = BoshProxyDialog.HTTP;
            }

            String proxyAddress = mAccountID.getAccountPropertyString(
                    ProtocolProviderFactory.PROXY_ADDRESS, mAccountID.getProxyAddress());

            String proxyPortStr = mAccountID.getAccountPropertyString(
                    ProtocolProviderFactory.PROXY_PORT, mAccountID.getProxyPort());
            int proxyPort;
            try {
                proxyPort = Integer.parseInt(proxyPortStr);
            } catch (NumberFormatException ex) {
                throw new OperationFailedException("Wrong proxy port, " + proxyPortStr
                        + " does not represent an integer", OperationFailedException.INVALID_ACCOUNT_PROPERTIES, ex);
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
            } catch (IllegalStateException e) {
                Timber.e(e, "Invalid Proxy Type not support by smack");
                proxy = null;
            }
        }
        else {
            proxy = null;
        }
    }

    /**
     * Connects xmpp connection and login. Returning the state whether is it final - Abort due to
     * certificate cancel or keep trying cause only current address has failed or stop trying cause we succeeded.
     *
     * @param userName the username to use
     * @param loginStrategy the login strategy to use
     * @return return the state how to continue the connect process.
     * @throws XMPPException & SmackException if we cannot connect for some reason
     */
    private ConnectState connectAndLogin(String userName, JabberLoginStrategy loginStrategy)
            throws XMPPException, SmackException
    {
        ConnectionConfiguration.Builder<?, ?> config = loginStrategy.getConnectionConfigurationBuilder();

        // Set XmppDomain to serviceName - default for no server-overridden and Bosh connection.
        DomainBareJid serviceName = mAccountID.getXmppDomain();
        config.setXmppDomain(serviceName);
        config.setResource(mResource);
        config.setProxyInfo(proxy);
        config.setCompressionEnabled(false);

        /*=== Configure connection for BOSH or TCP ===*/
        boolean isBosh = mAccountID.isBOSHEnable();
        if (isBosh) {
            String boshURL = mAccountID.getBoshUrl();
            BOSHConfiguration.Builder boshConfigurationBuilder = (BOSHConfiguration.Builder) config;
            try {
                URI boshURI = new URI(boshURL);
                boolean useHttps = boshURI.getScheme().equals("https");
                int port = boshURI.getPort();
                if (port == -1) {
                    port = useHttps ? 443 : 80;
                }

                String file = boshURI.getPath();
                // use rawQuery as getQuery() decodes the string
                String query = boshURI.getRawQuery();
                if (!TextUtils.isEmpty(query)) {
                    file += "?" + query;
                }

                boshConfigurationBuilder
                        .setUseHttps(useHttps)
                        .setFile(file)
                        .setHost(boshURI.getHost())
                        .setPort(port);
            } catch (URISyntaxException e) {
                Timber.e("Fail setting bosh URL in XMPPBOSHConnection configuration: %s", e.getMessage());
                StanzaError stanzaError = StanzaError.getBuilder(Condition.unexpected_request).build();
                throw new XMPPErrorException(null, stanzaError);
            }
        }
        else {
            /*
             * The defined settings for setHostAddress and setHost are handled by XMPPTCPConnection
             * #populateHostAddresses()-obsoleted, mechanism for the various service mode.
             */
            boolean isServerOverridden = mAccountID.getAccountPropertyBoolean(
                    ProtocolProviderFactory.IS_SERVER_OVERRIDDEN, false);

            // cmeng - value not defined currently for CUSTOM_XMPP_DOMAIN login
            String customXMPPDomain = mAccountID.getAccountPropertyString(ProtocolProviderFactory.CUSTOM_XMPP_DOMAIN);
            if (customXMPPDomain != null) {
                mInetSocketAddress = new InetSocketAddress(customXMPPDomain, DEFAULT_PORT);
                Timber.i("Connect using custom XMPP domain: %s", mInetSocketAddress);

                config.setHostAddress(mInetSocketAddress.getAddress());
                config.setPort(DEFAULT_PORT);
            }

            /*=== connect using overridden server name defined by user ===*/
            else if (isServerOverridden) {
                String host = mAccountID.getServerAddress();
                int port = mAccountID.getAccountPropertyInt(ProtocolProviderFactory.SERVER_PORT, DEFAULT_PORT);
                mInetSocketAddress = new InetSocketAddress(host, port);
                Timber.i("Connect using server override: %s", mInetSocketAddress);

                // For host given as ip address, then no DNSSEC authentication support
                if (Character.digit(host.charAt(0), 16) != -1) {
                    config.setHostAddress(mInetSocketAddress.getAddress());
                }
                // setHostAddress will take priority over setHost in smack populateHostAddresses() implementation - obsoleted
                config.setHost(host);
                config.setPort(port);
            }

            /*=== connect using SRV Resource Record for userID service name (host and hostAddress must be null) ===*/
            else {
                mInetSocketAddress = new InetSocketAddress(mAccountID.getService(), DEFAULT_PORT);
                Timber.i("Connect using service SRV Resource Record: %s", mInetSocketAddress);

                config.setHost((DnsName) null);
                config.setHostAddress(null);
            }
        }

        // if we have OperationSetPersistentPresence to take care of <presence/> sending, then
        // disable smack from sending the initial presence upon user authentication
        OpSetPP = getOperationSet(OperationSetPersistentPresence.class);
        if (OpSetPP != null)
            config.setSendPresence(false);

        if (mConnection != null && mConnection.isConnected()) {
            Timber.w("Attempt on connection that is not null and isConnected %s", mAccountID.getAccountJid());
            disconnectAndCleanConnection();
        }
        config.setSocketFactory(SocketFactory.getDefault());

        String[] supportedProtocols = {"TLSv1", "TLSv1.1", "TLSv1.2"};
        try {
            supportedProtocols = ((SSLSocket) SSLSocketFactory.getDefault().createSocket()).getSupportedProtocols();
        } catch (IOException e) {
            Timber.d("Use default supported Protocols: %s", Arrays.toString(supportedProtocols));
            // Use default list
        }
        Arrays.sort(supportedProtocols);

        // Determine enabled TLS protocol versions using getMinimumTLSversion
        ArrayList<String> enabledTLSProtocols = new ArrayList<>();
        for (int prot = supportedProtocols.length - 1; prot >= 0; prot--) {
            final String prot_version = supportedProtocols[prot];
            enabledTLSProtocols.add(prot_version);
            if (prot_version.equals(mAccountID.getMinimumTLSversion())) {
                break;
            }
        }

        String[] enabledTLSProtocolsArray = new String[enabledTLSProtocols.size()];
        enabledTLSProtocols.toArray(enabledTLSProtocolsArray);
        config.setEnabledSSLProtocols(enabledTLSProtocolsArray);

        // Cannot use a custom SSL context with DNSSEC enabled
        String dnssecMode = mAccountID.getDnssMode();
        if (DNSSEC_DISABLE.equals(dnssecMode)) {
            config.setDnssecMode(DnssecMode.disabled);

            /*
             * BOSH connection does not support TLS;
             * XEP-206 Note: The client SHOULD ignore any Transport Layer Security (TLS) feature since
             * BOSH channel encryption SHOULD be negotiated at the HTTP layer.
             */
            boolean tlsRequired;
            if (isBosh) {
                tlsRequired = false;
                config.setSecurityMode(SecurityMode.disabled);
            }
            else {
                /*
                 * user have the possibility to disable TLS but in this case, it will not be able to
                 * connect to a server which requires TLS;
                 */
                tlsRequired = loginStrategy.isTlsRequired();
                config.setSecurityMode(tlsRequired ? SecurityMode.required : SecurityMode.ifpossible);
            }

            CertificateService cvs = getCertificateVerificationService();
            if (cvs != null) {
                try {
                    X509TrustManager sslTrustManager = getTrustManager(cvs, serviceName);
                    SSLContext sslContext = loginStrategy.createSslContext(cvs, sslTrustManager);
                    SslContextFactory sslContextFactory = () -> sslContext;

                    config.setSslContextFactory(sslContextFactory);
                    config.setCustomX509TrustManager(sslTrustManager);
                    config.setAuthzid(mAccountID.getBareJid().asEntityBareJidIfPossible());

                } catch (GeneralSecurityException e) {
                    Timber.e(e, "Error creating custom trust manager");
                    // StanzaError stanzaError = StanzaError.getBuilder(Condition.service_unavailable).build();
                    throw new ATalkXmppException("Security-Exception: Creating custom TrustManager", e);
                }
            }
            else if (tlsRequired) {
                // StanzaError stanzaError = StanzaError.getBuilder(Condition.service_unavailable).build();
                // throw new XMPPErrorException(null, stanzaError);
                throw new ATalkXmppException("Security-Exception: Certificate verification service is unavailable and TLS is required");
            }
        }
        else {
            if (DNSSEC_ONLY.equals(dnssecMode)) {
                config.setDnssecMode(DnssecMode.needsDnssec);
            }
            else if (DNSSEC_AND_DANE.equals(dnssecMode)) {
                // override user SecurityMode setting for DNSSEC & DANE option
                config.setDnssecMode(DnssecMode.needsDnssecAndDane);
                config.setSecurityMode(ConnectionConfiguration.SecurityMode.required);
            }
        }

        // String userJid = userName + "@" + serviceName;
        // String password = userCredentials.getPasswordAsString();
        // config.setUsernameAndPassword(userJid, password);
        try {
            if (isBosh) {
                mConnection = new XMPPBOSHConnection((BOSHConfiguration) config.build());
            }
            else {
                mConnection = new XMPPTCPConnection((XMPPTCPConnectionConfiguration) config.build());
            }
        } catch (IllegalStateException ex) {
            // Cannot use a custom SSL context with DNSSEC enabled
            String errMsg = ex.getMessage() + "\n Please change DNSSEC security option accordingly.";
            StanzaError stanzaError = StanzaError.from(Condition.not_allowed, errMsg).build();
            throw new XMPPErrorException(null, stanzaError);
        }

        /* Start monitoring the status before connection-login. Only register listener once */
        if (xmppConnectionListener == null) {
            xmppConnectionListener = new XMPPConnectionListener();
            mConnection.addConnectionListener(xmppConnectionListener);
        }

        // Allow longer timeout during login for slow client device; clear to default in caller
        mConnection.setReplyTimeout(SMACK_REPLY_EXTENDED_TIMEOUT_30);

        // Init the connection SynchronizedPoints
        xmppConnected = new LoginSynchronizationPoint<>(this, "connection connected");

        Timber.i("Starting XMPP Connection...: %s", mInetSocketAddress);
        try {
            mConnection.connect();
        } catch (StreamErrorException ex) {
            String errMsg = ex.getMessage();
            if (StringUtils.isEmpty(errMsg))
                errMsg = ex.getStreamError().getDescriptiveText();
            Timber.e("Encounter problem during XMPPConnection: %s", errMsg);
            StanzaError stanzaError = StanzaError.from(Condition.policy_violation, errMsg).build();
            throw new XMPPErrorException(null, stanzaError);
            // } catch (DnssecValidationFailedException | IllegalArgumentException ex) {
        } catch (DnssecValidationFailedException ex) {
            String errMsg = ex.getMessage();
            StanzaError stanzaError = StanzaError.from(Condition.not_authorized, errMsg).build();
            throw new XMPPErrorException(null, stanzaError);
        } catch (SecurityRequiredByServerException ex) {
            // "SSL/TLS required by server but disabled in client"
            String errMsg = ex.getMessage();
            StanzaError stanzaError = StanzaError.from(Condition.not_allowed, errMsg).build();
            throw new XMPPErrorException(null, stanzaError);
        } catch (SecurityRequiredByClientException ex) {
            // "SSL/TLS required by client but not supported by server"
            String errMsg = ex.getMessage();
            StanzaError stanzaError = StanzaError.from(Condition.service_unavailable, errMsg).build();
            throw new XMPPErrorException(null, stanzaError);
        } catch (XMPPException | SmackException | IOException | InterruptedException | NullPointerException ex) {
//            if (ex.getCause() instanceof SSLHandshakeException) {
//                Timber.e(ex.getCause());
//            }
            String errMsg = aTalkApp.getResString(R.string.service_gui_XMPP_EXCEPTION, ex.getMessage());
            Timber.e("%s", errMsg);
            StanzaError stanzaError = StanzaError.from(Condition.remote_server_timeout, errMsg).build();
            throw new XMPPErrorException(null, stanzaError);
        }
        try {
            /*
             * Wait for connectionListener to report connection status. Exception handled in the above try/catch
             */
            xmppConnected.checkIfSuccessOrWaitOrThrow();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        // Check if user has cancelled the Trusted Certificate confirmation request
        if (abortConnecting) {
            abortConnecting = false;
            disconnectAndCleanConnection();
            return ConnectState.ABORT_CONNECTING;
        }

        if (!mConnection.isConnected()) {
            Timber.e("XMPPConnection establishment has failed!");

            // mConnection is not connected, lets set the mConnection state as failed;
            disconnectAndCleanConnection();
            eventDuringLogin = null;
            fireRegistrationStateChanged(getRegistrationState(),
                    RegistrationState.CONNECTION_FAILED, RegistrationStateChangeEvent.REASON_SERVER_NOT_FOUND, null);
            return ConnectState.ABORT_CONNECTING;
        }

        // cmeng - leave the registering state broadcast when xmpp is connected - may be better to do it here
        fireRegistrationStateChanged(RegistrationState.UNREGISTERED, RegistrationState.REGISTERING,
                RegistrationStateChangeEvent.REASON_NOT_SPECIFIED, null);

        // Init the user authentication SynchronizedPoints
        accountAuthenticated = new LoginSynchronizationPoint<>(this, "account authenticated");
        boolean success = false;
        try {
            success = loginStrategy.login(mConnection, userName, mResource);
        } catch (StreamErrorException ex) {
            String errMsg = ex.getStreamError().getDescriptiveText();
            Timber.e("Encounter problem during XMPPConnection: %s", errMsg);
            StanzaError stanzaError = StanzaError.from(Condition.policy_violation, errMsg).build();
            throw new XMPPErrorException(null, stanzaError);
        } catch (SmackException | XMPPException | InterruptedException | IOException el) {
            String errMsg = el.getMessage();
            /*
             * If account is not registered on server, send IB registration request to server if user
             * enable the option. Otherwise throw back to user and ask for InBand registration confirmation.
             */
            if (StringUtils.isNotEmpty(errMsg) && errMsg.contains("not-authorized")) {
                if (mAccountID.isIbRegistration()) {
                    try {
                        // Server sends stream disconnect on "not-authorized". So perform manual connect again before
                        // server closes the stream. Some Server does otherwise, so check before making connection.
                        if (!mConnection.isConnected())
                            mConnection.connect();
                        // stop pps connectionListener from disturbing IBR registration process
                        mConnection.removeConnectionListener(xmppConnectionListener);
                        xmppConnectionListener = null;

                        accountIBRegistered = new LoginSynchronizationPoint<>(this, "account ib registered");
                        loginStrategy.registerAccount(this, mAccountID);
                        eventDuringLogin = null;
                        return ConnectState.STOP_TRYING;
                    } catch (StreamErrorException ex) {
                        errMsg = ex.getStreamError().getDescriptiveText();
                        Timber.e("Encounter problem during XMPPConnection: %s", errMsg);
                        StanzaError stanzaError = StanzaError.from(Condition.policy_violation, errMsg).build();
                        throw new XMPPErrorException(null, stanzaError);
                    } catch (SmackException | XMPPException | InterruptedException | IOException | NullPointerException err) {
                        disconnectAndCleanConnection();
                        eventDuringLogin = null;
                        fireRegistrationStateChanged(getRegistrationState(), RegistrationState.CONNECTION_FAILED,
                                RegistrationStateChangeEvent.REASON_IB_REGISTRATION_FAILED,
                                loginStrategy.getClass().getName() + " requests abort");

                        errMsg = err.getMessage();
                        if (StringUtils.isNotEmpty(errMsg) && !errMsg.contains("registration-required")) {
                            errMsg = aTalkApp.getResString(R.string.service_gui_REGISTRATION_REQUIRED, errMsg);
                            Timber.e("%s", errMsg);
                            StanzaError stanzaError = StanzaError.from(Condition.forbidden, errMsg).build();
                            throw new XMPPErrorException(null, stanzaError);
                        }
                        else {
                            Timber.e("%s", errMsg);
                            StanzaError stanzaError = StanzaError.from(Condition.registration_required, errMsg).build();
                            throw new XMPPErrorException(null, stanzaError);
                        }
                    }
                }
                else {
                    errMsg = aTalkApp.getResString(R.string.service_gui_NOT_AUTHORIZED_HINT, errMsg);
                    StanzaError stanzaError = StanzaError.from(Condition.not_authorized, errMsg).build();
                    throw new XMPPErrorException(null, stanzaError);
                }
            }
        }

        // cmeng - sometimes exception and crash after this point during apk debug launch. android JIT problem
        try {
            // wait for connectionListener to report status. Exceptions are handled in try/catch
            accountAuthenticated.checkIfSuccessOrWait();
        } catch (InterruptedException e) {
            Timber.w("Xmpp Connection authentication exception: %s", e.getMessage());
        }

        if (!success) {
            disconnectAndCleanConnection();
            eventDuringLogin = null;
            fireRegistrationStateChanged(getRegistrationState(),
                    RegistrationState.CONNECTION_FAILED, RegistrationStateChangeEvent.REASON_AUTHENTICATION_FAILED,
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
    private class XMPPConnectionListener implements ConnectionListener
    {
        /**
         * Notification that the connection was closed normally.
         */
        public void connectionClosed()
        {
            String errMsg = "Stream closed!";
            StanzaError stanzaError = StanzaError.from(Condition.remote_server_timeout, errMsg).build();
            XMPPErrorException xmppException = new XMPPErrorException(null, stanzaError);
            xmppConnected.reportFailure(xmppException);

            if (reconnectionManager != null)
                reconnectionManager.disableAutomaticReconnection();

            // if we are in the middle of connecting process do not fire events, will do it later
            // when the method connectAndLogin finishes its work
            synchronized (connectAndLoginLock) {
                if (inConnectAndLogin) {
                    eventDuringLogin = new RegistrationStateChangeEvent(
                            ProtocolProviderServiceJabberImpl.this, getRegistrationState(),
                            RegistrationState.CONNECTION_FAILED,
                            RegistrationStateChangeEvent.REASON_USER_REQUEST, errMsg);
                    return;
                }
            }
            // Fire that connection has closed. User is responsible to log in again as the stream
            // closed can be authentication, ssl security etc that an auto retrial is of little use
            fireRegistrationStateChanged(getRegistrationState(),
                    RegistrationState.CONNECTION_FAILED,
                    RegistrationStateChangeEvent.REASON_USER_REQUEST, errMsg);
        }

        /**
         * Notification that the connection was closed due to an exception. When abruptly
         * disconnected, the ReconnectionManager will try to reconnecting to the server.
         * Note: Must reported as RegistrationState.RECONNECTING to allow resume as all
         * initial setup must be kept.
         *
         * @param exception contains information on the error.
         */
        public void connectionClosedOnError(Exception exception)
        {
            String errMsg = exception.getMessage();
            int regEvent = RegistrationStateChangeEvent.REASON_NOT_SPECIFIED;
            StanzaError.Condition seCondition = Condition.remote_server_not_found;

            Timber.e("### Connection closed on error (StreamErrorException: %s) during XMPPConnection: %s",
                    (exception instanceof StreamErrorException), errMsg);
            if (exception instanceof SSLException) {
                regEvent = RegistrationStateChangeEvent.REASON_SERVER_NOT_FOUND;
            }
            else if (exception instanceof StreamErrorException) {
                StreamError err = ((StreamErrorException) exception).getStreamError();
                StreamError.Condition condition = err.getCondition();
                errMsg = err.getDescriptiveText();

                if (condition == StreamError.Condition.conflict) {
                    seCondition = Condition.conflict;
                    regEvent = RegistrationStateChangeEvent.REASON_MULTIPLE_LOGIN;
                    if (errMsg.contains("removed")) {
                        regEvent = RegistrationStateChangeEvent.REASON_NON_EXISTING_USER_ID;
                    }
                }
                else if (condition == StreamError.Condition.policy_violation) {
                    seCondition = Condition.policy_violation;
                    regEvent = RegistrationStateChangeEvent.REASON_POLICY_VIOLATION;
                }
            }
            else if (exception instanceof XmlPullParserException) {
                seCondition = Condition.unexpected_request;
                regEvent = RegistrationStateChangeEvent.REASON_AUTHENTICATION_FAILED;
            }

            // Timber.e(exception, "Smack connection Closed OnError: %s", errMsg);
            StanzaError stanzaError = StanzaError.from(seCondition, errMsg).build();
            XMPPErrorException xmppException = new XMPPErrorException(null, stanzaError);
            xmppConnected.reportFailure(xmppException);

            // if we are in the middle of connecting process do not fire events, will do it
            // later when the method connectAndLogin finishes its work
            synchronized (connectAndLoginLock) {
                if (inConnectAndLogin) {
                    eventDuringLogin = new RegistrationStateChangeEvent(
                            ProtocolProviderServiceJabberImpl.this, getRegistrationState(),
                            RegistrationState.CONNECTION_FAILED, regEvent, errMsg);
                    return;
                }
            }

            // if ((seCondition == Condition.conflict) || (seCondition == Condition.policy_violation)) {
            if (seCondition == Condition.conflict) {
                // launch re-login prompt with reason for disconnect "replace with new connection"
                fireRegistrationStateChanged(exception);
            }
            else
                // Reconnecting state - keep all contacts' status
                fireRegistrationStateChanged(getRegistrationState(), RegistrationState.RECONNECTING, regEvent, errMsg);
        }

        /**
         * Notification that the connection has been successfully connected to the remote endpoint (e.g. the XMPP server).
         *
         * Note that the connection is likely not yet authenticated and therefore only limited operations
         * like registering an account may be possible.
         *
         * @param connection the XMPPConnection which successfully connected to its endpoint.
         */
        public void connected(XMPPConnection connection)
        {
            xmppConnected.reportSuccess();
            if (connection instanceof XMPPTCPConnection)
                setTrafficClass();

            // check and set auto tune ping interval if necessary
            tunePingInterval();

            // must initialize caps entities upon success connection to ensure it is ready for the very first <iq/> send
            initServicesAndFeatures();

            JingleMessageManager.getInstanceFor(connection);
            JingleMessageHelper.getInstanceFor(ProtocolProviderServiceJabberImpl.this);

            /*
             * Broadcast to all others after connection is connected but before actual account registration start.
             * This is required by others to init their states and get ready when the user is authenticated
             */
            // fireRegistrationStateChanged(RegistrationState.UNREGISTERED, RegistrationState.REGISTERING,
            //        RegistrationStateChangeEvent.REASON_USER_REQUEST, "TCP Connection Successful");
        }

        /**
         * Notification that the connection has been authenticated.
         *
         * @param connection the XMPPConnection which successfully authenticated.
         * @param resumed true if a previous XMPP session's stream was resumed.
         */
        public void authenticated(XMPPConnection connection, boolean resumed)
        {
            accountAuthenticated.reportSuccess();

            // Get the Roster instance for this authenticated user
            mRoster = Roster.getInstanceFor(mConnection);

            /*
             * XEP-0237:Roster Versioning - init RosterStore for each authenticated account to
             * support persistent storage
             */
            initRosterStore();

            /* Always set roster subscription mode to manual so Roster passes the control back to aTalk for processing */
            mRoster.setSubscriptionMode(Roster.SubscriptionMode.manual);

            /* Set Roster subscription mode per global defined option for this accounts - Roaster will handle accept-all*/
            //    if (ConfigurationUtils.isPresenceSubscribeAuto())
            //        mRoster.setSubscriptionMode(Roster.SubscriptionMode.accept_all);
            //    else
            //        mRoster.setSubscriptionMode(Roster.SubscriptionMode.manual);

            isResumed = resumed;
            String msg = "Smack: User Authenticated with isResumed state: " + resumed;
            if (mConnection instanceof XMPPTCPConnection)
                ((XMPPTCPConnection) mConnection).setUseStreamManagementResumption(true);

            /*
             * Enable ReconnectionManager with ReconnectionPolicy.RANDOM_INCREASING_DELAY
             * - attempt to reconnect when server disconnect unexpectedly
             * Only enable on authentication. Otherwise <not-authorized/> will also trigger reconnection.
             */
            reconnectionManager = ReconnectionManager.getInstanceFor(mConnection);
            reconnectionManager.enableAutomaticReconnection();

            if (mAccountID.isIbRegistration())
                mAccountID.setIbRegistration(false);

            // Init HTTPAuthorizationRequestManager on authenticated
            httpAuthorizationRequestManager = HTTPAuthorizationRequestManager.getInstanceFor(mConnection);
            httpAuthorizationRequestManager.addIncomingListener(ProtocolProviderServiceJabberImpl.this);

            /*
             * Must initialize omemoManager on every new connected connection, to ensure both pps and omemoManager is referred
             * to same instance of xmppConnection.  Perform only after connection is connected to ensure the user is defined
             */
            // androidOmemoService = new AndroidOmemoService(ProtocolProviderServiceJabberImpl.this);

            /*
             * Must only initialize omemoDevice after user authenticated
             * Leave the smack reply timer reset to androidOmemoService as it is running async
             */
            resetSmackTimer = false;
            androidOmemoService.initOmemoDevice();

            // Fire registration state has changed
            if (resumed) {
                fireRegistrationStateChanged(RegistrationState.REGISTERING, RegistrationState.REGISTERED,
                        RegistrationStateChangeEvent.REASON_RESUMED, msg, false);
            }
            else {
                eventDuringLogin = null;
                fireRegistrationStateChanged(RegistrationState.REGISTERING, RegistrationState.REGISTERED,
                        RegistrationStateChangeEvent.REASON_USER_REQUEST, msg, true);
            }
        }
    }

    /**
     * Called when the server ping fails.
     */
    public void pingFailed()
    {
        // Timber.w("Ping failed! isLastConnectionMobile: %s; isConnectedMobile: %s", isLastConnectionMobile,
        //        isConnectedMobile());
        isMobilePingClosedOnError = isLastConnectionMobile && isConnectedMobile();
    }

    /*
     * Perform auto tuning of the ping interval if ping and optimization are both enabled and
     * connectionClosedOnError is due to ping activity
     */
    private void tunePingInterval()
    {
        // Remember the new connection network type, use in next network ClosedOnError for reference
        isLastConnectionMobile = isConnectedMobile();

        // Perform ping auto-tune only if a mobile network connection error, ping and auto tune options are all enabled
        if (!isMobilePingClosedOnError || !mAccountID.isKeepAliveEnable() || !mAccountID.isPingAutoTuneEnable())
            return;

        isMobilePingClosedOnError = false;
        int pingInterval = Integer.parseInt(mAccountID.getPingInterval());
        /* adjust ping Interval in step according to its current value */
        if (pingInterval > 240)
            pingInterval -= 30;
        else if (pingInterval > 120)
            pingInterval -= 10;

        // kept lowest limit at 120S ping interval
        if (pingInterval >= 120) {
            mAccountID.storeAccountProperty(ProtocolProviderFactory.PING_INTERVAL, pingInterval);
            Timber.w("Auto tunning ping interval to: %s", pingInterval);
        }
        else {
            Timber.e("Connection closed on error with ping interval of 120 second!!!");
        }
    }

    // Check if there is any connectivity to a mobile network
    private boolean isConnectedMobile()
    {
        Context context = aTalkApp.getGlobalContext();
        ConnectivityManager cm = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
        if (cm != null) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                final Network network = cm.getActiveNetwork();
                if (network != null) {
                    final NetworkCapabilities nc = cm.getNetworkCapabilities(network);
                    return (nc != null) && nc.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR);
                }
            }
            else {
                NetworkInfo networkInfo = cm.getActiveNetworkInfo();
                return (networkInfo != null && networkInfo.isConnected()
                        && networkInfo.getType() == ConnectivityManager.TYPE_MOBILE);
            }
        }
        return false;
    }

    /**
     * Gets the TrustManager that should be used for the specified service
     *
     * @param serviceName the service name
     * @param cvs The CertificateVerificationService to retrieve the trust manager
     * @return the trust manager
     */

    private X509TrustManager getTrustManager(CertificateService cvs, DomainBareJid serviceName)
            throws GeneralSecurityException
    {
        return new HostTrustManager(
                cvs.getTrustManager(Arrays.asList(serviceName.toString(),
                        CertificateServiceImpl.CERT_XMPP_CLIENT_SUBFIX + serviceName)));
    }

    /**
     * Use to disconnect current connection if exists and do the necessary clean up.
     */
    public void disconnectAndCleanConnection()
    {
        if (mConnection == null)
            return;

        /*
         * Must stop any reconnection timer if any; disconnect does not kill the timer. It continuous
         * to count down and starts another reconnection which disrupts any new established connection.
         */
        if (reconnectionManager != null)
            reconnectionManager.abortPossiblyRunningReconnection();

        // Remove the listener that is added at connection setup
        if (xmppConnectionListener != null) {
            mConnection.removeConnectionListener(xmppConnectionListener);
            xmppConnectionListener = null;
        }

        mRoster = null;
        if (mConnection.isConnected()) {
            try {
                PresenceBuilder presenceBuilder = mConnection.getStanzaFactory().buildPresenceStanza()
                        .ofType(Presence.Type.unavailable);
                if ((OpSetPP != null) && StringUtils.isNotEmpty(OpSetPP.getCurrentStatusMessage())) {
                    presenceBuilder.setStatus(OpSetPP.getCurrentStatusMessage());
                }
                mConnection.disconnect(presenceBuilder.build());
            } catch (Exception ex) {
                Timber.w("Exception while disconnect and clean connection!!!");
            }
        }

        if (httpAuthorizationRequestManager != null) {
            httpAuthorizationRequestManager.removeIncomingListener(this);
        }

        try {
            /*
             * The discoveryManager is exposed as service-public by the OperationSetContactCapabilities of this
             * ProtocolProviderService. No longer expose it because it's going away.
             */
            if (opsetContactCapabilities != null)
                opsetContactCapabilities.setDiscoveryManager(null);
        } finally {
            if (discoveryManager != null) {
                discoveryManager.stop();
                discoveryManager = null;
            }
        }

        // set it null as it also holds a reference to the old connection; it will be created again on new connection setup
        mConnection = null;
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
     * @param userRequest is the unregister by user request.
     */
    public void unregister(boolean userRequest)
    {
        unregisterInternal(true, userRequest);
    }

    /**
     * Unregister and fire the event if requested
     *
     * @param fireEvent boolean
     */
    public void unregisterInternal(boolean fireEvent)
    {
        unregisterInternal(fireEvent, false);
    }

    /**
     * Unregister and fire the event if requested
     *
     * @param fireEvent boolean
     */
    public void unregisterInternal(boolean fireEvent, boolean userRequest)
    {
        synchronized (initializationLock) {
            if (fireEvent) {
                eventDuringLogin = null;
                fireRegistrationStateChanged(getRegistrationState(), RegistrationState.UNREGISTERING,
                        RegistrationStateChangeEvent.REASON_NOT_SPECIFIED, null, userRequest);
            }

            disconnectAndCleanConnection();

            if (fireEvent) {
                eventDuringLogin = null;
                fireRegistrationStateChanged(RegistrationState.UNREGISTERING, RegistrationState.UNREGISTERED,
                        RegistrationStateChangeEvent.REASON_USER_REQUEST, null, userRequest);
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
        /* Persistent Storage directory for Avatar. */
        File avatarStoreDirectory = new File(aTalkApp.getGlobalContext().getFilesDir() + "/avatarStore");

        // Store in memory cache by default, and in persistent store if not null
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
        String userID = mAccountID.getUserID();

        rosterStoreDirectory = new File(aTalkApp.getGlobalContext().getFilesDir() + "/rosterStore_" + userID);

        if (!rosterStoreDirectory.exists()) {
            if (!rosterStoreDirectory.mkdir())
                Timber.e("Roster Store directory creation error: %s", rosterStoreDirectory.getAbsolutePath());
        }
        if (rosterStoreDirectory.exists()) {
            RosterStore rosterStore = DirectoryRosterStore.open(rosterStoreDirectory);
            if (rosterStore == null) {
                rosterStore = DirectoryRosterStore.init(rosterStoreDirectory);
            }
            mRoster.setRosterStore(rosterStore);
        }
    }

    public File getRosterStoreDirectory()
    {
        return rosterStoreDirectory;
    }

    /**
     * Setup all the Smack Service Discovery and other features that can only be performed during
     * actual account registration stage (mConnection). For initial setup see:
     * {@link #initSmackDefaultSettings()} and {@link #initialize(EntityBareJid, JabberAccountID)}
     *
     * Note: For convenience, many of the OperationSets when supported will handle state and events changes on its own.
     */
    private void initServicesAndFeatures()
    {
        /*  XEP-0092: Software Version initialization */
        VersionManager versionManager = VersionManager.getInstanceFor(mConnection);

        /* XEP-0199: XMPP Ping: Each account may set his own ping interval */
        PingManager mPingManager = PingManager.getInstanceFor(mConnection);
        if (mAccountID.isKeepAliveEnable()) {
            int pingInterval = Integer.parseInt(mAccountID.getPingInterval());
            mPingManager.setPingInterval(pingInterval);
            mPingManager.registerPingFailedListener(this);
        }
        else {
            // Disable pingManager
            mPingManager.setPingInterval(0);
        }

        /* XEP-0184: Message Delivery Receipts - global option */
        DeliveryReceiptManager deliveryReceiptManager = DeliveryReceiptManager.getInstanceFor(mConnection);
        // Always enable the ReceiptReceivedListener and receipt request (indepedent of contact capability)
        MessageHistoryService mhs = AndroidGUIActivator.getMessageHistoryService();
        deliveryReceiptManager.addReceiptReceivedListener((MessageHistoryServiceImpl) mhs);
        deliveryReceiptManager.autoAddDeliveryReceiptRequests();

        if (ConfigurationUtils.isSendMessageDeliveryReceipt()) {
            deliveryReceiptManager.setAutoReceiptMode(AutoReceiptMode.ifIsSubscribed);

        }
        else {
            deliveryReceiptManager.setAutoReceiptMode(AutoReceiptMode.disabled);
        }

        // Enable Last Activity - XEP-0012
        LastActivityManager lastActivityManager = LastActivityManager.getInstanceFor(mConnection);
        lastActivityManager.enable();

        /*  Start up VCardAvatarManager / UserAvatarManager for mAccount auto-update */
        VCardAvatarManager.getInstanceFor(mConnection);
        UserAvatarManager.getInstanceFor(mConnection);

        /*
         * Must initialize omemoManager on every new connected connection, to ensure both pps and
         * omemoManager is referred to same instance of xmppConnection.  Perform only after connection
         * is connected to ensure the user is defined
         *
         * Move to authenticated stage?
         * Perform here to ensure only one androidOmemoService is created; otherwise onResume may create multiple instances
         */
        androidOmemoService = new AndroidOmemoService(this);

        /*
         * add SupportedFeatures only prior to registerServiceDiscoveryManager. Otherwise found
         * some race condition with some optional features not properly initialized
         */
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
     *
     * Note: Do not need to mention if there are already included in Smack Library and have been activated.
     */
    private void addSupportedCapsFeatures()
    {
        supportedFeatures.clear();

        boolean isCallingDisabled = JabberActivator.getConfigurationService()
                .getBoolean(IS_CALLING_DISABLED, aTalk.disableMediaServiceOnFault);

        boolean isCallingDisabledForAccount = true;
        boolean isVideoCallingDisabledForAccount = true;
        if (mAccountID != null) {
            isCallingDisabledForAccount = mAccountID.getAccountPropertyBoolean(
                    ProtocolProviderFactory.IS_CALLING_DISABLED_FOR_ACCOUNT, aTalk.disableMediaServiceOnFault);

            isVideoCallingDisabledForAccount = mAccountID.getAccountPropertyBoolean(
                    ProtocolProviderFactory.IS_VIDEO_CALLING_DISABLED_FOR_ACCOUNT, aTalk.disableMediaServiceOnFault);
        }

        if (!aTalk.disableMediaServiceOnFault && !isCallingDisabled && !isCallingDisabledForAccount) {
            /*
             * Adds Jingle related features to the supported features.
             */
            // XEP-0166: Jingle
            supportedFeatures.add(URN_XMPP_JINGLE);
            // XEP-0167: Jingle RTP Sessions
            supportedFeatures.add(URN_XMPP_JINGLE_RTP);
            // XEP-0177: Jingle Raw UDP Transport Method
            supportedFeatures.add(URN_XMPP_JINGLE_RAW_UDP_0);

            supportedFeatures.add(JingleMessage.NAMESPACE);

            /*
             * Reflect the preference of the user with respect to the use of ICE.
             */
            if (mAccountID.getAccountPropertyBoolean(ProtocolProviderFactory.IS_USE_ICE, true)) {
                // XEP-0176: Jingle ICE-UDP Transport Method
                supportedFeatures.add(URN_XMPP_JINGLE_ICE_UDP_1);
            }

            // XEP-0167: Jingle RTP Sessions
            supportedFeatures.add(URN_XMPP_JINGLE_RTP_AUDIO);
            // XEP-0262: Use of ZRTP in Jingle RTP Sessions
            supportedFeatures.add(URN_XMPP_JINGLE_RTP_ZRTP);

            if (!isVideoCallingDisabledForAccount) {
                // XEP-0180: Jingle Video via RTP
                supportedFeatures.add(URN_XMPP_JINGLE_RTP_VIDEO);

                if (isDesktopSharingEnable) {
                    // Adds extension to support remote control as a sharing server (sharer).
                    supportedFeatures.add(InputEvtIQ.NAMESPACE_SERVER);

                    // Adds extension to support remote control as a sharing client (sharer).
                    supportedFeatures.add(InputEvtIQ.NAMESPACE_CLIENT);
                }
            }

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
        }

        // ===============================================
        // TODO: add the feature if any, corresponding to persistent presence, if someone knows
        // supportedFeatures.add(_PRESENCE_);

        // XEP-0065: SOCKS5 Bytestreams
        supportedFeatures.add(Bytestream.NAMESPACE);

        // Do not advertise if user disable message delivery receipts option
        if (ConfigurationUtils.isSendMessageDeliveryReceipt()) {
            // XEP-0184: Message Delivery Receipts
            supportedFeatures.add(DeliveryReceipt.NAMESPACE);
        }
        else {
            supportedFeatures.remove(DeliveryReceipt.NAMESPACE);
        }

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
        supportedFeatures.add(RTPHdrExtExtension.NAMESPACE);

        // XEP-0308: Last Message Correction
        supportedFeatures.add(MessageCorrectExtension.NAMESPACE);

        /* This is the "main" feature to advertise when a client support muc. We have to
         * add some features for specific functionality we support in muc.
         * see https://www.xmpp.org/extensions/xep-0045.html
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
        supportedFeatures.add(BoBExt.NAMESPACE);

        // XEP-0264: File Transfer Thumbnails
        supportedFeatures.add(Thumbnail.NAMESPACE);

        // XEP-0084: User Avatar
        supportedFeatures.add(AvatarMetadata.NAMESPACE_NOTIFY);
        supportedFeatures.add(AvatarData.NAMESPACE);

        // XEP-0384: OMEMO Encryption - obsoleted?
        // supportedFeatures.add(OmemoConstants.PEP_NODE_DEVICE_LIST_NOTIFY);

        // XEP-0092: Software Version
        supportedFeatures.add(URN_XMPP_IQ_VERSION);

        // Enable the XEP-0071: XHTML-IM feature for the account
        supportedFeatures.add(XHTMLExtension.NAMESPACE);
        // XHTMLManager.setServiceEnabled(mConnection, true);
    }

    /**
     * Registers the ServiceDiscoveryManager wrapper
     *
     * we setup all supported features before packets are actually being sent during feature
     * registration. So we'd better do it here so that our first presence update would
     * contain a caps with the right features.
     */
    private void registerServiceDiscoveryManager()
    {
        // Add features aTalk supports in addition to smack.
        String[] featuresToRemove = new String[]{"http://jabber.org/protocol/commands"};
        String[] featuresToAdd = supportedFeatures.toArray(new String[0]);
        // boolean cacheNonCaps = true;

        discoveryManager = new ScServiceDiscoveryManager(this, mConnection, featuresToRemove,
                featuresToAdd, true);

        /*
         * Expose the discoveryManager as service-public through the
         * OperationSetContactCapabilities of this ProtocolProviderService.
         */
        if (opsetContactCapabilities != null)
            opsetContactCapabilities.setDiscoveryManager(discoveryManager);
    }

    /**
     * Inorder to take effect on xmppConnection setup and the very first corresponding stanza being
     * sent; all smack default settings must be initialized prior to connection & account login.
     * Note: The getInstanceFor(xmppConnection) action during account login will auto-include
     * the smack Service Discovery feature. So it is no necessary to add the feature again in
     * method {@link #initialize(EntityBareJid, JabberAccountID)}
     */
    private void initSmackDefaultSettings()
    {
        int omemoReplyTimeout = 10000; // increase smack default timeout to 10 seconds

        /*
         * 	init Avatar to support persistent storage for both XEP-0153 and XEP-0084
         */
        initAvatarStore();

        /*
         * XEP-0153: vCard-Based Avatars - We will handle download of VCard on our own when there is an avatar update
         */
        VCardAvatarManager.setAutoDownload(false);

        /*
         * XEP-0084: User Avatars - Enable auto download when there is an avatar update
         */
        UserAvatarManager.setAutoDownload(true);

        /*
         * The CapsExtension node value to advertise in <presence/>.
         */
        String entityNode = OSUtils.IS_ANDROID ? "https://android.atalk.org" : "https://atalk.org";
        EntityCapsManager.setDefaultEntityNode(entityNode);

        /* setup EntityCapsManager persistent store for XEP-0115: Entity Capabilities */
        ScServiceDiscoveryManager.initEntityPersistentStore();

        /*
         * The CapsExtension reply to be included in the caps <Identity/>
         */
        String category = "client";
        String appName = aTalkApp.getResString(R.string.APPLICATION_NAME);
        String type = "android";

        DiscoverInfo.Identity identity = new DiscoverInfo.Identity(category, appName, type);
        ServiceDiscoveryManager.setDefaultIdentity(identity);

        /*
         * XEP-0092: Software Version
         * Initialize jabber:iq:version support feature
         */
        String versionName = BuildConfig.VERSION_NAME;
        String os = OSUtils.IS_ANDROID ? "android" : "pc";

        VersionManager.setDefaultVersion(appName, versionName, os);

        /*
         * XEP-0199: XMPP Ping
         * Set the default ping interval in seconds used by PingManager. Can be omitted if you do
         * not wish to change the Smack Default Setting. The Smack default is 30 minutes.
         */
        PingManager.setDefaultPingInterval(defaultPingInterval);

        // cmeng - to take care of slow device S3=7s (N3=4.5S) and heavy loaded server.
        SmackConfiguration.setDefaultReplyTimeout(omemoReplyTimeout);

        // Enable smack debug message printing
        SmackConfiguration.DEBUG = true;

        // Disable certain ReflectionDebuggerFactory.DEFAULT_DEBUGGERS loading for Android (that are only for windows)
        SmackConfiguration.addDisabledSmackClass("org.jivesoftware.smackx.debugger.EnhancedDebugger");
        SmackConfiguration.addDisabledSmackClass("org.jivesoftware.smack.debugger.LiteDebugger");

        // Disables unused class, throwing some errors on login (disco-info)
        SmackConfiguration.addDisabledSmackClass("org.jivesoftware.smackx.httpfileupload.HttpFileUploadManager");

        // Just ignore UnparseableStanza to avoid connection abort
        SmackConfiguration.setDefaultParsingExceptionCallback(packetData -> {
                    Exception ex = packetData.getParsingException();
                    String errMsg = packetData.getContent() + ": " + ex.getMessage();
                    // StanzaErrorException xmppException = null;
                    // if (errMsg.contains("403")) {
                    //     StanzaError stanzaError = StanzaError.from(Condition.forbidden, errMsg).build();
                    //     xmppException = new StanzaErrorException(null, stanzaError);
                    // }
                    // else if (errMsg.contains("503")) {
                    //     StanzaError stanzaError = StanzaError.from(Condition.service_unavailable, errMsg).build();
                    //     xmppException = new StanzaErrorException(null, stanzaError);
                    // }
                    //
                    // if (xmppException != null)
                    //     throw xmppException;
                    // else
                    Timber.w(ex, "UnparseableStanza Error: %s", errMsg);
                }
        );

        XMPPTCPConnection.setUseStreamManagementDefault(true);

        // setup Dane provider
        MiniDnsDane.setup();

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
     * @param screenName the account id/uin/screenName of the account that we're about to create
     * @param accountID the identifier of the account that this protocol provider represents.
     * @see net.java.sip.communicator.service.protocol.AccountID
     */
    protected void initialize(EntityBareJid screenName, JabberAccountID accountID)
    {
        synchronized (initializationLock) {
            mAccountID = accountID;

            // Initialize all the smack default setting
            initSmackDefaultSettings();

            /*
             * Tell Smack what are the additional IQProviders that aTalk can support
             */
            // register our coin provider
            ProviderManager.addIQProvider(CoinIQ.ELEMENT, CoinIQ.NAMESPACE, new CoinIQProvider());

            // Jitsi Videobridge IQProvider and PacketExtensionProvider
            ProviderManager.addIQProvider(ColibriConferenceIQ.ELEMENT, ColibriConferenceIQ.NAMESPACE,
                    new ColibriIQProvider());

            ProviderManager.addIQProvider(JibriIq.ELEMENT, JibriIq.NAMESPACE, new JibriIqProvider());

            // register our input event provider
            ProviderManager.addIQProvider(InputEvtIQ.ELEMENT, InputEvtIQ.NAMESPACE, new InputEvtIQProvider());

            // register our jingle provider
            ProviderManager.addIQProvider(Jingle.ELEMENT, Jingle.NAMESPACE, new JingleProvider());

            // register our JingleInfo provider
            ProviderManager.addIQProvider(JingleInfoQueryIQ.ELEMENT, JingleInfoQueryIQ.NAMESPACE,
                    new JingleInfoQueryIQProvider());

            // replace the default StreamInitiationProvider with our
            // custom provider that handles the XEP-0264 <File/> element
            ProviderManager.addIQProvider(StreamInitiation.ELEMENT, StreamInitiation.NAMESPACE,
                    new ThumbnailStreamInitiationProvider());

            ProviderManager.addIQProvider(Registration.ELEMENT, Registration.NAMESPACE, new RegistrationProvider());

            ProviderManager.addIQProvider(ConfirmExtension.ELEMENT, ConfirmExtension.NAMESPACE, new ConfirmIQProvider());

            ProviderManager.addExtensionProvider(
                    ConferenceDescriptionExtension.ELEMENT, ConferenceDescriptionExtension.NAMESPACE,
                    new ConferenceDescriptionExtensionProvider());

            ProviderManager.addExtensionProvider(Nick.ELEMENT_NAME, Nick.NAMESPACE, new NickProvider());

            ProviderManager.addExtensionProvider(AvatarUrl.ELEMENT, AvatarUrl.NAMESPACE, new AvatarUrl.Provider());

            ProviderManager.addExtensionProvider(StatsId.ELEMENT, StatsId.NAMESPACE, new StatsId.Provider());

            ProviderManager.addExtensionProvider(IdentityExtension.ELEMENT, IdentityExtension.NAMESPACE,
                    new IdentityExtension.Provider());

            ProviderManager.addExtensionProvider(AvatarIdExtension.ELEMENT, AvatarIdExtension.NAMESPACE,
                    new DefaultExtensionElementProvider<>(AvatarIdExtension.class));

            ProviderManager.addExtensionProvider(JsonMessageExtension.ELEMENT, JsonMessageExtension.NAMESPACE,
                    new DefaultExtensionElementProvider<>(JsonMessageExtension.class));

            ProviderManager.addExtensionProvider(TranslationLanguageExtension.ELEMENT,
                    TranslationLanguageExtension.NAMESPACE,
                    new DefaultExtensionElementProvider<>(TranslationLanguageExtension.class));

            ProviderManager.addExtensionProvider(
                    TranscriptionLanguageExtension.ELEMENT, TranscriptionLanguageExtension.NAMESPACE,
                    new DefaultExtensionElementProvider<>(TranscriptionLanguageExtension.class));

            ProviderManager.addExtensionProvider(
                    TranscriptionStatusExtension.ELEMENT, TranscriptionStatusExtension.NAMESPACE,
                    new DefaultExtensionElementProvider<>(TranscriptionStatusExtension.class));

            ProviderManager.addExtensionProvider(
                    TranscriptionRequestExtension.ELEMENT, TranscriptionRequestExtension.NAMESPACE,
                    new DefaultExtensionElementProvider<>(TranscriptionRequestExtension.class));

            ProviderManager.addExtensionProvider(ConfirmExtension.ELEMENT, ConfirmExtension.NAMESPACE, new ConfirmExtProvider());

            /*
             * Tell Smack what are the additional StreamFeatureProvider and ExtensionProviders that aTalk can support
             */
            ProviderManager.addStreamFeatureProvider(Registration.Feature.ELEMENT, Registration.Feature.NAMESPACE,
                    (ExtensionElementProvider) new RegistrationStreamFeatureProvider());

            ProviderManager.addExtensionProvider(CapsExtension.ELEMENT, CapsExtension.NAMESPACE,
                    new CapsExtensionProvider());

            // XEP-0231: Bits of Binary Extension - aTalk Bob Extension Provider support
            ProviderManager.addExtensionProvider(BoBExt.ELEMENT, BoBExt.NAMESPACE, new BoBExtensionProvider());

            // XEP-0084: User Avatar (metadata) + notify
            ProviderManager.addExtensionProvider(AvatarMetadata.ELEMENT, AvatarMetadata.NAMESPACE,
                    new AvatarMetadataProvider());

            // XEP-0084: User Avatar (data)
            ProviderManager.addExtensionProvider(AvatarData.ELEMENT, AvatarData.NAMESPACE, new AvatarDataProvider());

            // XEP-0153: vCard-Based Avatars
            ProviderManager.addExtensionProvider(VCardTempXUpdate.ELEMENT, VCardTempXUpdate.NAMESPACE,
                    new VCardTempXUpdateProvider());

            // XEP-0158: CAPTCHA Forms
            ProviderManager.addExtensionProvider(CaptchaExtension.ELEMENT, CaptchaExtension.NAMESPACE, new CaptchaProvider());

            // in case of modified account, we clear list of supported features and all state
            // change listeners, otherwise we can have two OperationSet for same feature and it
            // can causes problem (i.e. two OperationSetBasicTelephony can launch two ICE
            // negotiations (with different ufrag/pwd) and peer will failed call. And by the way
            // user will see two dialog for answering/refusing the call
            this.clearRegistrationStateChangeListener();
            this.clearSupportedOperationSet();

            String protocolIconPath = accountID.getAccountPropertyString(ProtocolProviderFactory.PROTOCOL_ICON_PATH);
            if (protocolIconPath == null)
                protocolIconPath = "resources/images/protocol/jabber";

            jabberIcon = new ProtocolIconJabberImpl(protocolIconPath);
            jabberStatusEnum = JabberStatusEnum.getJabberStatusEnum(protocolIconPath);

            /*
             * Here are all the OperationSets that aTalk supported; to be queried by the
             * application and take appropriate actions
             */
            // String keepAliveStrValue = mAccountID.getAccountPropertyString(ProtocolProviderFactory.KEEP_ALIVE_METHOD);

            // initialize the presence OperationSet
            InfoRetriever infoRetriever = new InfoRetriever(this, screenName);
            OperationSetPersistentPresenceJabberImpl persistentPresence
                    = new OperationSetPersistentPresenceJabberImpl(this, infoRetriever);

            addSupportedOperationSet(OperationSetPersistentPresence.class, persistentPresence);

            // register it once again for those that simply need presence
            addSupportedOperationSet(OperationSetPresence.class, persistentPresence);

            if (accountID.getAccountPropertyString(ProtocolProviderFactory.ACCOUNT_READ_ONLY_GROUPS) != null) {
                addSupportedOperationSet(OperationSetPersistentPresencePermissions.class,
                        new OperationSetPersistentPresencePermissionsJabberImpl(this));
            }

            // initialize the IM operation set
            OperationSetBasicInstantMessagingJabberImpl basicInstantMessaging
                    = new OperationSetBasicInstantMessagingJabberImpl(this);

            addSupportedOperationSet(OperationSetBasicInstantMessaging.class, basicInstantMessaging);

            addSupportedOperationSet(OperationSetMessageCorrection.class, basicInstantMessaging);

            // The XHTMLExtension.NAMESPACE: http://jabber.org/protocol/xhtml-im feature is included already in smack.

            addSupportedOperationSet(OperationSetExtendedAuthorizations.class,
                    new OperationSetExtendedAuthorizationsJabberImpl(this, persistentPresence));

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
                    = new OperationSetServerStoredAccountInfoJabberImpl(this, infoRetriever, screenName);

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
                    .getBoolean(IS_CALLING_DISABLED, aTalk.disableMediaServiceOnFault);
            boolean isCallingDisabledForAccount = accountID.getAccountPropertyBoolean(
                    ProtocolProviderFactory.IS_CALLING_DISABLED_FOR_ACCOUNT, aTalk.disableMediaServiceOnFault);

            // Check if calling is enabled.
            if (!aTalk.disableMediaServiceOnFault && !isCallingDisabled && !isCallingDisabledForAccount) {
                OperationSetBasicTelephonyJabberImpl basicTelephony
                        = new OperationSetBasicTelephonyJabberImpl(this);

                addSupportedOperationSet(OperationSetAdvancedTelephony.class, basicTelephony);
                addSupportedOperationSet(OperationSetBasicTelephony.class, basicTelephony);
                addSupportedOperationSet(OperationSetSecureZrtpTelephony.class, basicTelephony);
                addSupportedOperationSet(OperationSetSecureSDesTelephony.class, basicTelephony);

                // initialize audio telephony OperationSet
                addSupportedOperationSet(OperationSetTelephonyConferencing.class,
                        new OperationSetTelephonyConferencingJabberImpl(this));
                addSupportedOperationSet(OperationSetBasicAutoAnswer.class,
                        new OperationSetAutoAnswerJabberImpl(this));
                addSupportedOperationSet(OperationSetResourceAwareTelephony.class,
                        new OperationSetResAwareTelephonyJabberImpl(basicTelephony));

                boolean isVideoCallingDisabledForAccount = accountID.getAccountPropertyBoolean(
                        ProtocolProviderFactory.IS_VIDEO_CALLING_DISABLED_FOR_ACCOUNT, aTalk.disableMediaServiceOnFault);

                if (!isVideoCallingDisabledForAccount) {
                    // initialize video telephony OperationSet
                    addSupportedOperationSet(OperationSetVideoTelephony.class,
                            new OperationSetVideoTelephonyJabberImpl(basicTelephony));
                }

                // Only init video bridge if enabled
                boolean isVideobridgeDisabled = JabberActivator.getConfigurationService()
                        .getBoolean(OperationSetVideoBridge.IS_VIDEO_BRIDGE_DISABLED, false);
                if (!isVideobridgeDisabled) {
                    // init video bridge
                    addSupportedOperationSet(OperationSetVideoBridge.class, new OperationSetVideoBridgeImpl(this));
                }

                // init DTMF
                OperationSetDTMFJabberImpl operationSetDTMF = new OperationSetDTMFJabberImpl(this);
                addSupportedOperationSet(OperationSetDTMF.class, operationSetDTMF);
                addSupportedOperationSet(OperationSetIncomingDTMF.class, new OperationSetIncomingDTMFJabberImpl());
            }

            // OperationSetContactCapabilities
            opsetContactCapabilities = new OperationSetContactCapabilitiesJabberImpl(this);
            if (discoveryManager != null)
                opsetContactCapabilities.setDiscoveryManager(discoveryManager);
            addSupportedOperationSet(OperationSetContactCapabilities.class, opsetContactCapabilities);

            OperationSetChangePassword opsetChangePassword = new OperationSetChangePasswordJabberImpl(this);
            addSupportedOperationSet(OperationSetChangePassword.class, opsetChangePassword);

            OperationSetCusaxUtils opsetCusaxCusaxUtils = new OperationSetCusaxUtilsJabberImpl();
            addSupportedOperationSet(OperationSetCusaxUtils.class, opsetCusaxCusaxUtils);

            boolean isUserSearchEnabled = accountID.getAccountPropertyBoolean(IS_USER_SEARCH_ENABLED_PROPERTY, false);
            if (isUserSearchEnabled) {
                addSupportedOperationSet(OperationSetUserSearch.class, new OperationSetUserSearchJabberImpl(this));
            }
            OperationSetTLS opsetTLS = new OperationSetTLSJabberImpl(this);
            addSupportedOperationSet(OperationSetTLS.class, opsetTLS);

            OperationSetConnectionInfo opsetConnectionInfo = new OperationSetConnectionInfoJabberImpl();
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
            Timber.log(TimberLog.FINER, "Killing the Jabber Protocol Provider.");

            // kill all active calls
            OperationSetBasicTelephonyJabberImpl telephony
                    = (OperationSetBasicTelephonyJabberImpl) getOperationSet(OperationSetBasicTelephony.class);
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
     * @return true if the provider is initialized and ready for use and false otherwise.
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
     * @param contactId the contact identifier to validate
     * @param result Must be supplied as an empty a list. Implementors add items:
     * <ol>
     * <li>is the error message if applicable</li>
     * <li>a suggested correction. Index 1 is optional and can only be present if there was a validation failure.</li>
     * </ol>
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
            // no suggestion for an empty id
            if (contactId.length() == 0) {
                result.add(aTalkApp.getResString(R.string.service_gui_INVALID_ADDRESS, contactId));
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
            StringBuilder suggestion = new StringBuilder();
            for (char c : user.toCharArray()) {
                if (!(c == 0x21 || (c >= 0x23 && c <= 0x25)
                        || (c >= 0x28 && c <= 0x2e) || (c >= 0x30 && c <= 0x39)
                        || c == 0x3b || c == 0x3d || c == 0x3f
                        || (c >= 0x41 && c <= 0x7e) || (c >= 0x80 && c <= 0xd7ff)
                        || (c >= 0xe000 && c <= 0xfffd))) {
                    valid = false;
                }
                else {
                    suggestion.append(c);
                }
            }
            if (!valid) {
                result.add(aTalkApp.getResString(R.string.service_gui_INVALID_ADDRESS, contactId));
                result.add(suggestion + remainder);
                return false;
            }

            return true;
        } catch (Exception ex) {
            result.add(aTalkApp.getResString(R.string.service_gui_INVALID_ADDRESS, contactId));
        }
        return false;
    }

    /**
     * Returns the <tt>XMPPConnection</tt>opened by this provider
     *
     * @return a reference to the <tt>XMPPConnection</tt> last opened by this provider.
     */
    public XMPPConnection getConnection()
    {
        return mConnection;
    }

    /**
     * Determines whether a specific <tt>XMPPException</tt> signals that attempted login has failed.
     *
     * Calling method will trigger a re-login dialog if the return <tt>failureMode</tt> is not
     * <tt>SecurityAuthority.REASON_UNKNOWN</tt> etc
     *
     * Add additional exMsg message if necessary to achieve this effect.
     *
     * @param ex the <tt>Exception</tt> which is to be determined whether it signals
     * that attempted authentication has failed
     * @return if the specified <tt>ex</tt> signals that attempted authentication is
     * known' otherwise <tt>SecurityAuthority.REASON_UNKNOWN</tt> is returned.
     * @see SecurityAuthority#REASON_UNKNOWN
     */
    private int checkLoginFailMode(Exception ex)
    {
        int failureMode = SecurityAuthority.REASON_UNKNOWN;
        String exMsg = ex.getMessage().toLowerCase(Locale.US);

        /*
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
        else if (exMsg.contains("conflict")) {
            failureMode = SecurityAuthority.CONFLICT;
        }
        else if (exMsg.contains("policy-violation")) {
            failureMode = SecurityAuthority.POLICY_VIOLATION;
        }
        else if (exMsg.contains("not-allowed")) {
            failureMode = SecurityAuthority.DNSSEC_NOT_ALLOWED;
        }
        else if (exMsg.contains("security-exception")) {
            failureMode = SecurityAuthority.SECURITY_EXCEPTION;
        }
        return failureMode;
    }

    /**
     * Tries to determine the appropriate message and status to fire, according to the exception.
     *
     * @param ex the {@link XMPPException} or {@link SmackException}  that caused the state change.
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
        else if (failMode == SecurityAuthority.CONFLICT) {
            regState = RegistrationState.CONNECTION_FAILED;
            reasonCode = RegistrationStateChangeEvent.REASON_MULTIPLE_LOGIN;
        }

        // we fired these for some reason that we have gone offline; lets clean
        // the current connection state for any future connections
        if (regState == RegistrationState.UNREGISTERED
                || regState == RegistrationState.CONNECTION_FAILED) {
            disconnectAndCleanConnection();
        }

        String reason = ex.getMessage();
        fireRegistrationStateChanged(getRegistrationState(), regState, reasonCode, reason);

        // Show error and abort further attempt for unknown or specified exceptions; others proceed to retry
        if ((failMode == SecurityAuthority.REASON_UNKNOWN)
                || (failMode == SecurityAuthority.SECURITY_EXCEPTION)
                || (failMode == SecurityAuthority.POLICY_VIOLATION)) {
            if (TextUtils.isEmpty(reason) && (ex.getCause() != null))
                reason = ex.getCause().getMessage();
            DialogActivity.showDialog(aTalkApp.getGlobalContext(),
                    aTalkApp.getResString(R.string.service_gui_ERROR), reason);
        }
        else {
            // Try re-register and ask user for new credentials giving detail reason description.
            if (ex instanceof XMPPErrorException) {
                reason = ((XMPPErrorException) ex).getStanzaError().getDescriptiveText();
            }
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
     * @param jid the jabber id for which to check;
     * Jid must be FullJid unless it is for service e.g. proxy.atalk.org, conference.atalk.org
     * @param features the list of features to check for
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

            // If one is not supported we return false and don't check the others.
            for (String feature : features) {
                if (!featureInfo.containsFeature(feature)) {
                    return false;
                }
            }
            return true;
        } catch (Exception e) {
            Timber.d(e, "Failed to retrieve discovery info.");
        }
        return false;
    }

    /**
     * Determines if the given list of <tt>features</tt> is supported by the specified jabber id.
     *
     * @param jid the jabber id that we'd like to get information about
     * @param feature the feature to check for
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
     * @param contact the contact, for which we're looking for a full jid
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
     * @param jid the contact jid (i.e. usually without resource) whose full jid we are looking for.
     * @return the jid of the specified contact or bareJid if the provider is not yet connected;
     */
    public Jid getFullJidIfPossible(Jid jid)
    {
        // when we are not connected there is no full jid
        if (mConnection != null && mConnection.isConnected()) {
            if (mRoster != null)
                jid = mRoster.getPresence(jid.asBareJid()).getFrom();
        }
        return jid;
    }

    /**
     * The trust manager which asks the client whether to trust a particular certificate,
     * when it is not android root's CA trusted.
     * Note: X509ExtendedTrustManager required API-24
     */
    private class HostTrustManager implements X509TrustManager // X509ExtendedTrustManager
    {
        /**
         * The default trust manager.
         */
        private final X509TrustManager tm;

        /**
         * Creates the custom trust manager.
         *
         * @param tm the default trust manager.
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
         * @param chain the cert chain.
         * @param authType authentication type like: RSA.
         * @throws CertificateException never
         * @throws UnsupportedOperationException always
         */
        public void checkClientTrusted(X509Certificate[] chain, String authType)
                throws CertificateException, UnsupportedOperationException
        {
            throw new UnsupportedOperationException();
        }

        // All the below 4 Overrides are for X509ExtendedTrustManager
        // @Override
        public void checkClientTrusted(X509Certificate[] chain, String authType, Socket socket)
                throws CertificateException
        {
            throw new UnsupportedOperationException();
        }

        // @Override
        public void checkServerTrusted(X509Certificate[] chain, String authType, Socket socket)
                throws CertificateException
        {
            checkServerTrusted(chain, authType);
        }

        // @Override
        public void checkClientTrusted(X509Certificate[] chain, String authType, SSLEngine engine)
                throws CertificateException
        {
            throw new UnsupportedOperationException();
        }

        // @Override
        public void checkServerTrusted(X509Certificate[] chain, String authType, SSLEngine engine)
                throws CertificateException
        {
            checkServerTrusted(chain, authType);
        }

        /**
         * Check whether a certificate is trusted, if not ask user whether he trusts it.
         *
         * @param chain the certificate chain.
         * @param authType authentication type like: RSA.
         * @throws CertificateException not trusted.
         */
        public void checkServerTrusted(X509Certificate[] chain, String authType)
                throws CertificateException
        {
            abortConnecting = true;
            // Timber.e(new Exception("TSL Certificate Invalid"));
            try {
                tm.checkServerTrusted(chain, authType);
            } catch (CertificateException e) {
                // notify in a separate thread to avoid a deadlock when a reg state listener
                // accesses a synchronized XMPPConnection method (like getRoster)
                new Thread(() -> fireRegistrationStateChanged(getRegistrationState(),
                        RegistrationState.UNREGISTERED, RegistrationStateChangeEvent.REASON_USER_REQUEST,
                        "Not trusted certificate")).start();
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
                new Thread(() -> reRegister(SecurityAuthority.CONNECTION_FAILED, null)).start();
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
     *
     * Build our own EntityJid if not connected. May not be full compliant - For explanation
     *
     * @return the Jabber EntityFullJid
     * @see AbstractXMPPConnection #user
     */
    public EntityFullJid getOurJID()
    {
        EntityFullJid fullJid;
        if (mConnection != null) {
            fullJid = mConnection.getUser();
        }
        else {
            // mResource can be null if user is not registered, so use default
            loadResource();
            fullJid = JidCreate.entityFullFrom(mAccountID.getBareJid().asEntityBareJidIfPossible(), mResource);
        }
        return fullJid;
    }

    /**
     * Returns the <tt>InetAddress</tt> that is most likely to be to be used as a next hop when
     * contacting our XMPP server. This is an utility method that is used whenever we have to
     * choose one of our local addresses (e.g. when trying to pick a best candidate for raw udp).
     * It is based on the assumption that, in absence of any more specific details, chances are
     * that we will be accessing remote destinations via the same interface that we are using to
     * access our jabber server.
     *
     * @return the <tt>InetAddress</tt> that is most likely to be to be used as a next hop when contacting our server.
     * @throws IllegalArgumentException if we don't have a valid server.
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
        } catch (UnknownHostException ex) {
            throw new IllegalArgumentException("seems we don't have a valid next hop.", ex);
        }
        Timber.d("Returning address %s as next hop.", nextHop);
        return nextHop;
    }

    /**
     * Start auto-discovery of JingleNodes tracker/relays.
     */
    public void startJingleNodesDiscovery()
    {
        // Jingle Nodes Service Initialization;
        final JabberAccountIDImpl accID = (JabberAccountIDImpl) mAccountID;

        // v2.2.2  mConnection == null on FFR ???. Call only on RegistrationState.REGISTERED state?
        final SmackServiceNode service = new SmackServiceNode(mConnection, 60000);

        // make sure SmackServiceNode will clean up when connection is closed
        mConnection.addConnectionListener(service);

        for (JingleNodeDescriptor desc : accID.getJingleNodes()) {
            TrackerEntry entry = null;
            try {
                entry = new TrackerEntry(desc.isRelaySupported()
                        ? TrackerEntry.Type.relay : TrackerEntry.Type.tracker, TrackerEntry.Policy._public,
                        JidCreate.from(desc.getJID()), JingleChannelIQ.UDP);
            } catch (XmppStringprepException e) {
                e.printStackTrace();
            }
            service.addTrackerEntry(entry);
        }
        new Thread(new JingleNodesServiceDiscovery(service, mConnection, accID, jingleNodesSyncRoot)).start();
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
     * @param message the message to be logged and then wrapped in a new <tt>OperationFailedException</tt>
     * @param errorCode the error code to be assigned to the new <tt>OperationFailedException</tt>
     * @param cause the <tt>Throwable</tt> that has caused the necessity to log an error and have a new
     * <tt>OperationFailedException</tt> thrown
     * @throws OperationFailedException the exception that we wanted this method to throw.
     */
    public static void throwOperationFailedException(String message, int errorCode, Throwable cause)
            throws OperationFailedException
    {
        Timber.e(cause, "%s", message);
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
        String domain = mAccountID.getService();
        if (mAccountID.isServerOverridden()) {
            domain = mAccountID.getAccountPropertyString(ProtocolProviderFactory.SERVER_ADDRESS, domain);
        }
        return isGmailOrGoogleAppsAccount(domain);
    }

    /**
     * Returns true if our account is a Gmail or a Google Apps ones.
     *
     * @param domain domain to check
     * @return true if our account is a Gmail or a Google Apps ones.
     */
    public static boolean isGmailOrGoogleAppsAccount(String domain)
    {
        try {
            SRV[] srvRecords = NetworkUtils.getSRVRecords("xmpp-client", "tcp", domain);
            if (srvRecords == null) {
                return false;
            }
            for (SRV srv : srvRecords) {
                if (srv.target.toString().contains("google.com")) {
                    return true;
                }
            }
        } catch (IOException e) {
            Timber.e("Failed when checking for google account: %s", e.getMessage());
        }
        return false;
    }

    /**
     * Gets the entity PRE_KEY_ID of the first Jitsi Videobridge associated with {@link #mConnection} i.e.
     * provided by the <tt>serviceName</tt> of <tt>mConnection</tt>.
     * Abort checking if last check returned with NoResponseException. Await 45s wait time
     *
     * @return the entity PRE_KEY_ID of the first Jitsi Videobridge associated with <tt>mConnection</tt>
     */
    public Jid getJitsiVideobridge()
    {
        if (mConnection != null && mConnection.isConnected()) {
            ScServiceDiscoveryManager discoveryManager = getDiscoveryManager();
            DomainBareJid serviceName = mConnection.getXMPPServiceDomain();
            DiscoverItems discoverItems = null;

            try {
                discoverItems = discoveryManager.discoverItems(serviceName);
            } catch (NoResponseException | NotConnectedException | XMPPException
                    | InterruptedException ex) {
                Timber.d(ex, "Failed to discover the items associated with Jabber entity: %s", serviceName);
            }

            if ((discoverItems != null) && !isLastVbNoResponse) {
                List<DiscoverItems.Item> discoverItemIter = discoverItems.getItems();

                for (DiscoverItems.Item discoverItem : discoverItemIter) {
                    Jid entityID = discoverItem.getEntityID();
                    DiscoverInfo discoverInfo = null;

                    try {
                        discoverInfo = discoveryManager.discoverInfo(entityID);
                    } catch (NoResponseException | NotConnectedException | XMPPException | InterruptedException ex) {
                        Timber.w(ex, "Failed to discover information about Jabber entity: %s", entityID);
                        if (ex instanceof NoResponseException) {
                            isLastVbNoResponse = true;
                        }
                    }
                    if ((discoverInfo != null) && discoverInfo.containsFeature(ColibriConferenceIQ.NAMESPACE)) {
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
        } catch (ClassNotFoundException e) {
            Timber.e(e, "Error loading classes in smack");
        }
    }

    /*
     * ============== HTTP Authorization Request received ===============
     */
    @Override
    public void onHTTPAuthorizationRequest(DomainBareJid from, ConfirmExtension confirmExt)
    {
        String instruction = httpAuthorizationRequestManager.getInstruction();
        if (StringUtils.isEmpty(instruction)) {
            instruction = aTalkApp.getResString(R.string.service_gui_HTTP_REQUEST_INSTRUCTION,
                    confirmExt.getMethod(), confirmExt.getUrl(), confirmExt.getId(), mAccountID.getAccountJid());
        }
        AndroidUtils.showAlertConfirmDialog(aTalkApp.getGlobalContext(),
                aTalkApp.getResString(R.string.service_gui_HTTP_REQUEST_TITLE), instruction,
                aTalkApp.getResString(R.string.service_gui_ACCEPT), this);
    }

    @Override
    public boolean onConfirmClicked(DialogActivity dialog)
    {
        httpAuthorizationRequestManager.accept();
        return true;
    }

    @Override
    public void onDialogCancelled(DialogActivity dialog)
    {
        httpAuthorizationRequestManager.reject();
    }

    // ==================================================

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
                } catch (Exception e) {
                    Timber.i(e, "Failed to set trafficClass");
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
                Field socket = XMPPTCPConnection.class.getDeclaredField("secureSocket");
                socket.setAccessible(true);
                secureSocket = (SSLSocket) socket.get(mConnection);
            } catch (Exception e) {
                Timber.w("Access to XMPPTCPConnection.secureSocket not found!");
            }
        }
        return secureSocket;
    }

    /**
     * Retrieve the XMPP connection socket used by the protocolProvider (by reflection)
     *
     * @return the socket which is used for this connection.
     * @see XMPPTCPConnection#socket.
     */
    public Socket getSocket()
    {
        Socket socket = null;
        if (mConnection != null && mConnection.isConnected()) {
            try {
                Field field = XMPPTCPConnection.class.getDeclaredField("socket");
                field.setAccessible(true);
                socket = (Socket) field.get(mConnection);
            } catch (Exception e) {
                Timber.w("Access to XMPPTCPConnection.socket not found!");
            }
        }
        return socket;
    }
}
