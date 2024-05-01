/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.atalk.impl.neomedia.transform.zrtp;

import net.java.sip.communicator.service.protocol.AccountID;
import net.java.sip.communicator.service.protocol.ProtocolProviderFactory;

import org.atalk.android.R;
import org.atalk.android.aTalkApp;
import org.atalk.impl.neomedia.AbstractRTPConnector;
import org.atalk.service.neomedia.AbstractSrtpControl;
import org.atalk.service.neomedia.SrtpControl;
import org.atalk.service.neomedia.SrtpControlType;
import org.atalk.service.neomedia.ZrtpControl;
import org.atalk.util.MediaType;
import org.jxmpp.jid.BareJid;

import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.EnumSet;

import gnu.java.zrtp.ZrtpCodes;
import gnu.java.zrtp.utils.ZrtpUtils;

/**
 * Controls zrtp in the MediaStream.
 *
 * @author Damian Minkov
 * @author Eng Chong Meng
 * @author MilanKral
 */
public class ZrtpControlImpl extends AbstractSrtpControl<ZRTPTransformEngine> implements ZrtpControl
{
    /**
     * Additional info codes for and data to support ZRTP4J. These could be added to the library.
     * However they are specific for this implementation, needing them for various GUI changes.
     */
    public static enum ZRTPCustomInfoCodes
    {
        ZRTPDisabledByCallEnd,
        ZRTPEnabledByDefault,
        ZRTPEngineInitFailure,
        ZRTPNotEnabledByUser
    }

    /**
     * Whether current is master session.
     */
    private boolean masterSession = false;

    /**
     * This is the connector, required to send ZRTP packets via the DatagramSocket.
     */
    private AbstractRTPConnector zrtpConnector = null;

    byte[] myZid;

    /**
     * Creates the control.
     */
    public ZrtpControlImpl(final byte[] myZid)
    {
        super(SrtpControlType.ZRTP);
        this.myZid = myZid;
    }

    /**
     * Cleans up the current zrtp control and its engine.
     */
    @Override
    public void doCleanup()
    {
        super.doCleanup();
        zrtpConnector = null;
    }

    /*
     * (non-Javadoc)
     *
     * @see net.java.sip.communicator.service.neomedia.ZrtpControl#getCiperString()
     */
    public String getCipherString()
    {
        return getTransformEngine().getUserCallback().getCipherString();
    }

    /**
     * Get negotiated ZRTP protocol version.
     *
     * @return the integer representation of the negotiated ZRTP protocol version.
     */
    public int getCurrentProtocolVersion()
    {
        ZRTPTransformEngine zrtpEngine = getTransformEngine();
        return (zrtpEngine != null) ? zrtpEngine.getCurrentProtocolVersion() : 0;
    }

    /**
     * Return the zrtp hello hash String.
     *
     * @param index Hello hash of the Hello packet identified by index. Index must be
     * 0 <= index < SUPPORTED_ZRTP_VERSIONS.
     * @return String the zrtp hello hash.
     */
    public String getHelloHash(int index)
    {
        return getTransformEngine().getHelloHash(index);
    }

    /**
     * Get the ZRTP Hello Hash data - separate strings.
     *
     * @param index Hello hash of the Hello packet identified by index.
     * Index must be 0 <= index < SUPPORTED_ZRTP_VERSIONS.
     * @return String array containing the version string at offset 0, the Hello hash value as
     * hex-digits at offset 1. Hello hash is available immediately after class
     * instantiation. Returns {@code null} if ZRTP is not available.
     */
    public String[] getHelloHashSep(int index)
    {
        return getTransformEngine().getHelloHashSep(index);
    }

    /**
     * Get number of supported ZRTP protocol versions.
     *
     * @return the number of supported ZRTP protocol versions.
     */
    public int getNumberSupportedVersions()
    {
        ZRTPTransformEngine zrtpEngine = getTransformEngine();
        return (zrtpEngine != null) ? zrtpEngine.getNumberSupportedVersions() : 0;
    }

    /**
     * Get the peer's Hello Hash data.
     *
     * Use this method to get the peer's Hello Hash data. The method returns the data as a string.
     *
     * @return a String containing the Hello hash value as hex-digits. Peer Hello hash is available
     * after we received a Hello packet from our peer. If peer's hello hash is not available return null.
     */
    public String getPeerHelloHash()
    {
        ZRTPTransformEngine zrtpEngine = getTransformEngine();
        return (zrtpEngine != null) ? zrtpEngine.getPeerHelloHash() : "";
    }

    /*
     * (non-Javadoc)
     *
     * @see net.java.sip.communicator.service.neomedia.ZrtpControl#getPeerZid ()
     */
    public byte[] getPeerZid()
    {
        return getTransformEngine().getPeerZid();
    }

    /*
     * (non-Javadoc)
     *
     * @see net.java.sip.communicator.service.neomedia.ZrtpControl#getPeerZidString()
     */
    public String getPeerZidString()
    {
        byte[] zid = getPeerZid();
        return new String(ZrtpUtils.bytesToHexString(zid, zid.length));
    }

    /**
     * Method for getting the default secure status value for communication
     *
     * @return the default enabled/disabled status value for secure communication
     */
    public boolean getSecureCommunicationStatus()
    {
        ZRTPTransformEngine zrtpEngine = getTransformEngine();
        return (zrtpEngine != null) && zrtpEngine.getSecureCommunicationStatus();
    }

    /*
     * (non-Javadoc)
     *
     * @see net.java.sip.communicator.service.neomedia.ZrtpControl#getSecurityString ()
     */
    public String getSecurityString()
    {
        return getTransformEngine().getUserCallback().getSecurityString();
    }

    /**
     * Returns the timeout value that will we will wait and fire timeout secure event if call is
     * not secured. The value is in milliseconds.
     *
     * @return the timeout value that will we will wait and fire timeout secure event if call is not secured.
     */
    public long getTimeoutValue()
    {
        // this is the default value as mentioned in rfc6189
        // we will later grab this setting from zrtp
        return 3750;
    }

    /**
     * Initializes a new <code>ZRTPTransformEngine</code> instance to be associated with and used by
     * this <code>ZrtpControlImpl</code> instance.
     *
     * @return a new <code>ZRTPTransformEngine</code> instance to be associated with and used by this
     * <code>ZrtpControlImpl</code> instance
     */
    protected ZRTPTransformEngine createTransformEngine()
    {
        ZRTPTransformEngine transformEngine = new ZRTPTransformEngine();

        // NOTE: set paranoid mode before initializing
        // zrtpEngine.setParanoidMode(paranoidMode);

        final String zidFilename = "GNUZRTP4J_" + new BigInteger(myZid).toString(32) + ".zid";

        transformEngine.initialize(zidFilename, false, ZrtpConfigureUtils.getZrtpConfiguration(), myZid);
        transformEngine.setUserCallback(new SecurityEventManager(this));
        return transformEngine;
    }

    /*
     * (non-Javadoc)
     *
     * @see net.java.sip.communicator.service.neomedia.ZrtpControl#isSecurityVerified ()
     */
    public boolean isSecurityVerified()
    {
        return getTransformEngine().getUserCallback().isSecurityVerified();
    }

    /**
     * Returns false, ZRTP exchanges is keys over the media path.
     *
     * @return false
     */
    public boolean requiresSecureSignalingTransport()
    {
        return false;
    }

    /**
     * Sets the <code>RTPConnector</code> which is to use or uses this ZRTP engine.
     *
     * @param connector the <code>RTPConnector</code> which is to use or uses this ZRTP engine
     */
    public void setConnector(AbstractRTPConnector connector)
    {
        zrtpConnector = connector;
    }

    /**
     * When in multistream mode, enables the master session.
     *
     * @param masterSession whether current control, controls the master session
     */
    @Override
    public void setMasterSession(boolean masterSession)
    {
        // by default its not master, change only if set to be master
        // sometimes (jingle) streams are re-initing and
        // we must reuse old value (true) event that false is submitted
        if (masterSession)
            this.masterSession = masterSession;
    }

    /**
     * Start multi-stream ZRTP sessions. After the ZRTP Master (DH) session reached secure state
     * the SCCallback calls this method to start the multi-stream ZRTP sessions. Enable
     * auto-start mode (auto-sensing) to the engine.
     *
     * @param master master SRTP data
     */
    @Override
    public void setMultistream(SrtpControl master)
    {
        if (master == null || master == this)
            return;

        if (!(master instanceof ZrtpControlImpl))
            throw new IllegalArgumentException("master is no ZRTP control");

        ZrtpControlImpl zm = (ZrtpControlImpl) master;
        ZRTPTransformEngine engine = getTransformEngine();

        engine.setMultiStrParams(zm.getTransformEngine().getMultiStrParams());
        engine.setEnableZrtp(true);
        engine.getUserCallback().setMasterEventManager(zm.getTransformEngine().getUserCallback());
    }

    /**
     * Sets the SAS verification
     *
     * @param verified the new SAS verification status
     */
    public void setSASVerification(boolean verified)
    {
        ZRTPTransformEngine engine = getTransformEngine();

        if (verified)
            engine.SASVerified();
        else
            engine.resetSASVerified();
    }

    /**
     * Starts and enables zrtp in the stream holding this control.
     *
     * @param mediaType the media type of the stream this control controls.
     */
    public void start(MediaType mediaType)
    {
        boolean zrtpAutoStart;

        // ZRTP engine initialization
        ZRTPTransformEngine engine = getTransformEngine();

        // Create security user callback for each peer.
        SecurityEventManager securityEventManager = engine.getUserCallback();

        // Decide if this will become the ZRTP Master session:
        // - Statement: audio media session will be started before video media session
        // - if no other audio session was started before then this will become ZRTP Master session
        // - only the ZRTP master sessions start in "auto-sensing" mode to immediately catch ZRTP communication from other client
        // - after the master session has completed its key negotiation it will start other media sessions (see SCCallback)
        if (masterSession) {
            zrtpAutoStart = true;

            // we know that audio is considered as master for zrtp
            securityEventManager.setSessionType(mediaType);
        }
        else {
            // check whether video was not already started
            // it may happen when using multistreams, audio has initiated and started video
            // initially engine has value enableZrtp = false
            zrtpAutoStart = engine.isEnableZrtp();
            securityEventManager.setSessionType(mediaType);
        }
        engine.setConnector(zrtpConnector);
        securityEventManager.setSrtpListener(getSrtpListener());

        // tells the engine whether to autostart(enable)
        // zrtp communication, if false it just passes packets without transformation
        engine.setEnableZrtp(zrtpAutoStart);
        engine.sendInfo(ZrtpCodes.MessageSeverity.Info, EnumSet.of(ZRTPCustomInfoCodes.ZRTPEnabledByDefault));
    }

    /*
     * (non-Javadoc)
     *
     * @see net.java.sip.communicator.service.neomedia.ZrtpControl#setReceivedSignaledZRTPVersion(String)
     */
    public void setReceivedSignaledZRTPVersion(final String version)
    {
        getTransformEngine().setReceivedSignaledZRTPVersion(version);
    }

    /*
     * (non-Javadoc)
     *
     * @see net.java.sip.communicator.service.neomedia.ZrtpControl#isSecurityVerified(String)
     */
    public void setReceivedSignaledZRTPHashValue(final String value)
    {
        getTransformEngine().setReceivedSignaledZRTPHashValue(value);
    }

    /**
     * Compute own ZID from salt value stored in accountID and peer JID.
     *
     * @param accountID Use the ZID salt value for this account
     * @param peerJid peer JID. Muss be a base JID, without resources part, because the resource can change too often.
     * @return computed ZID
     */
    public static byte[] generateMyZid(final AccountID accountID, final BareJid peerJid)
    {
        final String ZIDSalt = getAccountZIDSalt(accountID);
        final byte[] zid = new byte[12];
        try {
            final MessageDigest md = MessageDigest.getInstance("SHA-256");
            md.update(new BigInteger(ZIDSalt, 32).toByteArray());

            md.update(peerJid.toString().getBytes(StandardCharsets.UTF_8));
            final byte[] result = md.digest();
            System.arraycopy(result, 0, zid, 0, 12);

        } catch (NumberFormatException e) {
            aTalkApp.showToastMessage(R.string.zid_reset_summary);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalArgumentException("generateMyZid");
        }

        return zid;
    }

    /**
     * Generate a new ZID salt if none is defined for the accountId (happen in testing.
     *
     * @param accountID Use the ZID salt value for this account
     * @return the found or new ZIDSalt
     */
    public static String getAccountZIDSalt(AccountID accountID)
    {
        String ZIDSalt = accountID.getAccountPropertyString(ProtocolProviderFactory.ZID_SALT);
        if (ZIDSalt == null) {
            ZIDSalt = new BigInteger(256, new SecureRandom()).toString(32);
            accountID.storeAccountProperty(ProtocolProviderFactory.ZID_SALT, ZIDSalt);
        }
        return ZIDSalt;
    }
}
