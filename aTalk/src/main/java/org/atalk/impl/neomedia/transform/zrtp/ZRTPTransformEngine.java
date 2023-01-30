/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.atalk.impl.neomedia.transform.zrtp;

import org.atalk.impl.neomedia.AbstractRTPConnector;
import org.atalk.impl.neomedia.RTPConnectorOutputStream;
import org.atalk.impl.neomedia.transform.PacketTransformer;
import org.atalk.impl.neomedia.transform.SinglePacketTransformer;
import org.atalk.impl.neomedia.transform.TransformEngine;
import org.atalk.impl.neomedia.transform.srtp.SRTCPTransformer;
import org.atalk.impl.neomedia.transform.srtp.SRTPTransformer;
import org.atalk.impl.neomedia.transform.srtp.SrtpContextFactory;
import org.atalk.impl.neomedia.transform.srtp.SrtpPolicy;
import org.atalk.service.fileaccess.FileAccessService;
import org.atalk.service.fileaccess.FileCategory;
import org.atalk.service.libjitsi.LibJitsi;
import org.atalk.service.neomedia.RawPacket;
import org.atalk.service.neomedia.SrtpControl;

import java.io.File;
import java.io.IOException;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.Timer;
import java.util.TimerTask;

import gnu.java.zrtp.ZRtp;
import gnu.java.zrtp.ZrtpCallback;
import gnu.java.zrtp.ZrtpCodes;
import gnu.java.zrtp.ZrtpConfigure;
import gnu.java.zrtp.ZrtpConstants;
import gnu.java.zrtp.ZrtpSrtpSecrets;
import gnu.java.zrtp.ZrtpStateClass;
import gnu.java.zrtp.zidfile.ZidFile;
import timber.log.Timber;

/**
 * JMF extension/connector to support GNU ZRTP4J.
 *
 * ZRTP was developed by Phil Zimmermann and provides functions to negotiate keys and other
 * necessary data (crypto data) to set-up the Secure RTP (SRTP) crypto context. Refer to Phil's ZRTP
 * specification at his <a href="http://zfoneproject.com/">Zfone project</a> site to get more
 * detailed information about the capabilities of ZRTP.
 *
 * <h3>Short overview of the ZRTP4J implementation</h3>
 *
 * ZRTP is a specific protocol to negotiate encryption algorithms and the required key material.
 * ZRTP uses a RTP session to exchange its protocol messages.
 *
 * A complete GNU ZRTP4J implementation consists of two parts, the GNU ZRTP4J core and specific code
 * that binds the GNU ZRTP core to the underlying RTP/SRTP stack and the operating system:
 * <ul>
 * <li>The GNU ZRTP core is independent of a specific RTP/SRTP stack and the operating system and
 * consists of the ZRTP protocol state engine, the ZRTP protocol messages, and the GNU ZRTP4J
 * engine. The GNU ZRTP4J engine provides methods to setup ZRTP message and to analyze received ZRTP
 * messages, to compute the crypto data required for SRTP, and to maintain the required hashes and HMAC.</li>
 * <li>The second part of an implementation is specific <em>glue</em> code the binds the GNU ZRTP
 * core to the actual RTP/SRTP implementation and other operating system specific services such as timers.</li>
 * </ul>
 *
 * The GNU ZRTP4J core uses a callback interface class (refer to ZrtpCallback) to access RTP/SRTP or
 * operating specific methods, for example to send data via the RTP/SRTP stack, to access timers,
 * provide mutex handling, and to report events to the application.
 *
 * <h3>The ZRTPTransformEngine</h3>
 *
 * ZRTPTransformEngine implements code that is specific to the JMF implementation.
 *
 * To perform its tasks ZRTPTransformEngine
 * <ul>
 * <li>extends specific classes to hook into the JMF RTP methods and the RTP/SRTP send and receive queues</li>
 * <li>implements the ZrtpCallback interface to provide to enable data send and receive other
 * specific services (timer to GNU ZRTP4J</li>
 * <li>provides ZRTP specific methods that applications may use to control and setup GNU ZRTP</li>
 * <li>can register and use an application specific callback class (refer to ZrtpUserCallback)</li>
 * </ul>
 *
 * After instantiating a GNU ZRTP4J session (see below for a short example) applications may use the
 * ZRTP specific methods of ZRTPTransformEngine to control and setup GNU ZRTP, for example enable or
 * disable ZRTP processing or getting ZRTP status information.
 *
 * GNU ZRTP4J provides a ZrtpUserCallback class that an application may extend and register with
 * ZRTPTransformEngine. GNU ZRTP4J and ZRTPTransformEngine use the ZrtpUserCallback methods to
 * report ZRTP events to the application. The application may display this information to the user
 * or act otherwise.
 *
 * The following figure depicts the relationships between ZRTPTransformEngine, JMF implementation,
 * the GNU ZRTP4J core, and an application that provides an ZrtpUserCallback class.
 *
 * <pre>
 *
 *                  +---------------------------+
 *                  |  ZrtpTransformConnector   |
 *                  | extends TransformConnector|
 *                  | implements RTPConnector   |
 *                  +---------------------------+
 *                                |
 *                                | uses
 *                                |
 *  +----------------+      +-----+---------------+
 *  |  Application   |      |                     |      +----------------+
 *  |  instantiates  | uses | ZRTPTransformEngine | uses |                |
 *  | a ZRTP Session +------+    implements       +------+   GNU ZRTP4J   |
 *  |  and provides  |      |   ZrtpCallback      |      |      core      |
 *  |ZrtpUserCallback|      |                     |      | implementation |
 *  +----------------+      +---------------------+      |  (ZRtp et al)  |
 *                                                       |                |
 *                                                       +----------------+
 * </pre>
 *
 * The following short code snippets show how an application could instantiate a
 * ZrtpTransformConnector, get the ZRTP4J engine and initialize it. Then the code get a RTP manager
 * instance and initializes it with the ZRTPTransformConnector. Please note: setting the target must
 * be done with the connector, not with the RTP manager.
 *
 * <pre>
 * ...
 *   transConnector = (ZrtpTransformConnector)TransformManager.createZRTPConnector(sa);
 *   zrtpEngine = transConnector.getEngine();
 *   zrtpEngine.setUserCallback(new MyCallback());
 *   if (!zrtpEngine.initialize(&quot;test_t.zid&quot;))
 *       System.out.println(&quot;initialize failed&quot;);
 *
 *   // initialize the RTPManager using the ZRTP connector
 *
 *   mgr = RTPManager.newInstance();
 *   mgr.initialize(transConnector);
 *
 *   mgr.addSessionListener(this);
 *   mgr.addReceiveStreamListener(this);
 *
 *   transConnector.addTarget(target);
 *   zrtpEngine.startZrtp();
 *
 *   ...
 * </pre>
 *
 * The <em>demo</em> folder contains a small example that shows how to use GNU ZRTP4J.
 *
 * This ZRTPTransformEngine documentation shows the ZRTP specific extensions and describes
 * overloaded methods and a possible different behaviour.
 *
 * @author Werner Dittmann &lt;Werner.Dittmann@t-online.de>
 * @author Eng Chong Meng
 * @author MilanKral
 */
public class ZRTPTransformEngine extends SinglePacketTransformer implements SrtpControl.TransformEngine, ZrtpCallback
{
    /**
     * Very simple Timeout provider class.
     *
     * This very simple timeout provider can handle one timeout request at one time only. A second
     * request would overwrite the first one and would lead to unexpected results.
     *
     * @author Werner Dittmann <Werner.Dittmann@t-online.de>
     */
    private class TimeoutProvider extends Thread
    {
        /**
         * Constructs Timeout provider.
         *
         * @param name the name of the provider.
         */
        public TimeoutProvider(String name)
        {
            super(name);
        }

        /**
         * The delay to wait before timeout.
         */
        private long nextDelay = 0;

        /**
         * Whether to execute the timeout if delay expires.
         */
        private boolean newTask = false;

        /**
         * Whether thread is stopped.
         */
        private boolean stop = false;

        /**
         * synchronizes delays and stop.
         */
        private final Object sync = new Object();

        /**
         * Request timeout after the specified delay.
         *
         * @param delay the delay.
         */
        public synchronized void requestTimeout(long delay)
        {
            synchronized (sync) {
                nextDelay = delay;
                newTask = true;
                sync.notifyAll();
            }
        }

        /**
         * Stops the thread.
         */
        public void stopRun()
        {
            synchronized (sync) {
                stop = true;
                sync.notifyAll();
            }
        }

        /**
         * Cancels the last request.
         */
        public void cancelRequest()
        {
            synchronized (sync) {
                newTask = false;
                sync.notifyAll();
            }
        }

        /**
         * The running part of the thread.
         */
        @Override
        public void run()
        {
            while (!stop) {
                synchronized (sync) {
                    while (!newTask && !stop) {
                        try {
                            sync.wait();
                        } catch (InterruptedException e) {
                            // e.printStackTrace();
                        }
                    }
                }
                long currentTime = System.currentTimeMillis();
                long endTime = currentTime + nextDelay;
                synchronized (sync) {
                    while ((currentTime < endTime) && newTask && !stop) {
                        try {
                            sync.wait(endTime - currentTime);
                        } catch (InterruptedException e) {
                            // e.printStackTrace();
                        }
                        currentTime = System.currentTimeMillis();
                    }
                }
                if (newTask && !stop) {
                    newTask = false;
                    ZRTPTransformEngine.this.handleTimeout();
                }
            }
        }
    }

    /**
     * Each ZRTP packet has a fixed header of 12 bytes.
     */
    protected static final int ZRTP_PACKET_HEADER = 12;

    /**
     * This is the connector, required to send ZRTP packets via the DatagramSocket.
     */
    private AbstractRTPConnector zrtpConnector = null;

    /**
     * We need Out SRTPTransformer to transform RTP to SRTP.
     */
    private SRTPTransformer srtpOutTransformer = null;

    /**
     * We need In SRTPTransformer to transform SRTP to RTP.
     */
    private SRTPTransformer srtpInTransformer = null;

    /**
     * User callback class.
     */
    private SecurityEventManager securityEventManager = null;

    /**
     * The ZRTP engine.
     */
    private ZRtp zrtpEngine = null;

    /**
     * ZRTP engine enable flag (used for auto-enable at initialization)
     */
    private boolean enableZrtp = false;

    /**
     * Client ID string initialized with the name of the ZRTP4j library
     */
    private String clientIdString = ZrtpConstants.clientId;

    /**
     * SSRC identifier for the ZRTP packets
     */
    private int ownSSRC = 0;

    /**
     * ZRTP packet sequence number
     */
    private short senderZrtpSeqNo;

    /**
     * The timeout provider instance This is used for handling the ZRTP timers
     */
    private TimeoutProvider timeoutProvider = null;

    /**
     * The current condition of the ZRTP engine
     */
    private boolean started = false;

    /**
     * Sometimes we need to start muted so we will discard any packets during some time after the
     * start of the transformer. This is needed when for this time we can receive encrypted packets
     * but we hadn't established a secure communication. This happens when a secure stream is recreated.
     */
    private boolean muted = false;

    private boolean mitmMode = false;

    /**
     * Enable or disable paranoid mode.
     *
     * The Paranoid mode controls the behaviour and handling of the SAS verify flag. If Paranoid
     * mode is set to false then ZRtp applies the normal handling. If Paranoid mode is set to
     * true then the handling is:
     *
     * <ul>
     * <li>Force the SAS verify flag to be false at srtpSecretsOn() callback. This gives the user
     * interface (UI) the indication to handle the SAS as <b>not verified</b>. See implementation
     * note below.</li>
     * <li>Don't set the SAS verify flag in the {@code Confirm} packets, thus the other also
     * must report the SAS as <b>not verified</b>.</li>
     * <li>ignore the {@code SASVerified()} function, thus do not set the SAS to verified in
     * the ZRTP cache.</li>
     * <li>Disable the <b>Trusted PBX MitM</b> feature. Just send the {@code SASRelay} packet
     * but do not process the relayed data. This protects the user from a malicious "trusted PBX"
     * .</li>
     * </ul>
     * ZRtp performs all other steps during the ZRTP negotiations as usual, in particular it
     * computes, compares, uses, and stores the retained secrets. This avoids unnecessary warning
     * messages. The user may enable or disable the Paranoid mode on a call-by-call basis without
     * breaking the key continuity data.
     *
     * <b>Implementation note:</b></br> An application shall always display the SAS code if the SAS
     * verify flag is {@code false}. The application shall also use mechanisms to remind the
     * user to compare the SAS code, for example using larger fonts, different colours and other
     * display features.
     */
    private boolean enableParanoidMode = false;

    private ZRTCPTransformer zrtcpTransformer = null;

    /**
     * Count successfully decrypted SRTP packets. Need to decide if RS2 will become valid.
     *
     * @see #stopZrtp
     */
    private long zrtpUnprotect;

    /**
     * The indicator which determines whether {@link SrtpControl.TransformEngine#cleanup()} has
     * been invoked on this instance to prepare it for garbage collection. Disallows
     * {@link #getRTCPTransformer()} to initialize a new <code>ZRTCPTransformer</code> instance which
     * cannot possibly be correctly used after the disposal of this instance anyway.
     */
    private boolean disposed = false;

    /**
     * This is the peer ZRTP version received from the signaling layer.
     */
    private String receivedSignaledZRTPVersion = null;

    /**
     * This is the peer ZRTP hash value received from the signaling layer.
     */
    private String receivedSignaledZRTPHashValue = null;

    /**
     * Construct a ZRTPTransformEngine.
     */
    public ZRTPTransformEngine()
    {
        SecureRandom secRand = new SecureRandom();
        byte[] random = new byte[2];
        secRand.nextBytes(random);

        senderZrtpSeqNo = random[0];
        senderZrtpSeqNo |= (random[1] << 8);
        senderZrtpSeqNo &= 0x7fff; // to avoid early roll-over
    }

    /**
     * Returns an instance of <code>ZRTPCTransformer</code>.
     *
     * @see TransformEngine#getRTCPTransformer()
     */
    public ZRTCPTransformer getRTCPTransformer()
    {
        if ((zrtcpTransformer == null) && !disposed)
            zrtcpTransformer = new ZRTCPTransformer();
        return zrtcpTransformer;
    }

    /**
     * Returns this RTPTransformer.
     *
     * @see TransformEngine#getRTPTransformer()
     */
    public PacketTransformer getRTPTransformer()
    {
        return this;
    }

//    /**
//     * Engine initialization method. Calling this for engine initialization and start it with
//     * auto-sensing and a given configuration setting.
//     *
//     * @param zidFilename The ZID file name
//     * @param config The configuration data
//     * @return true if initialization fails, false if succeeds
//     */
//    public boolean initialize(String zidFilename, ZrtpConfigure config)
//    {
//        return initialize(zidFilename, true, config);
//    }
//
//    /**
//     * Engine initialization method. Calling this for engine initialization and start it with
//     * defined auto-sensing and a default configuration setting.
//     *
//     * @param zidFilename The ZID file name
//     * @param autoEnable If true start with auto-sensing mode.
//     * @return true if initialization fails, false if succeeds
//     */
//    public boolean initialize(String zidFilename, boolean autoEnable)
//    {
//        return initialize(zidFilename, autoEnable, null);
//    }
//
//    /**
//     * Default engine initialization method.
//     *
//     * Calling this for engine initialization and start it with auto-sensing and default
//     * configuration setting.
//     *
//     * @param zidFilename The ZID file name
//     * @return true if initialization fails, false if succeeds
//     */
//    public boolean initialize(String zidFilename)
//    {
//        return initialize(zidFilename, true, null);
//    }

    /**
     * Custom engine initialization method.
     * This allows to explicit specify if the engine starts with auto-sensing or not.
     *
     * @param zidFilename The ZID file name
     * @param autoEnable Set this true to start with auto-sensing and false to disable it.
     * @param config the zrtp config to use
     * @return true if initialization fails, false if succeeds
     */
    public synchronized boolean initialize(String zidFilename, boolean autoEnable, ZrtpConfigure config, final byte[] myZid)
    {
        // Try to get the ZidFile path through the FileAccessService.
        File file = null;
        FileAccessService faService = LibJitsi.getFileAccessService();
        if (faService != null) {
            try {
                // Create the zid file
                file = faService.getPrivatePersistentFile(zidFilename, FileCategory.PROFILE);
            } catch (Exception e) {
                Timber.w("Failed to create the zid file.");
            }
        }

        // The ZidFile path should be absolute.
        String zidFilePath = null;
        try {
            if (file != null)
                zidFilePath = file.getAbsolutePath();
        } catch (SecurityException e) {
            Timber.d(e, "Failed to obtain the absolute path of the zid file.");
        }

        ZidFile zf = ZidFile.getInstance();
        if (zf.isOpen()) {
            if (!Arrays.equals(zf.getZid(), myZid)) {
                zf.close();
            }
        }

        if (!zf.isOpen()) {
            if (zf.open(zidFilePath) < 0)
                return false;
        }

        if (config == null) {
            config = new ZrtpConfigure();
            config.setStandardConfig();
        }
        if (enableParanoidMode)
            config.setParanoidMode(enableParanoidMode);

        zrtpEngine = new ZRtp(myZid, this, "", config, mitmMode);

        if (timeoutProvider == null) {
            timeoutProvider = new TimeoutProvider("ZRTP");
            // XXX Daemon only if timeoutProvider is a global singleton.
            // timeoutProvider.setDaemon(true);
            timeoutProvider.start();
        }
        enableZrtp = autoEnable;
        return true;
    }

    /**
     * @param startMuted whether to be started as muted if no secure communication is established
     */
    public void setStartMuted(boolean startMuted)
    {
        muted = startMuted;
        if (startMuted) {
            // make sure we don't mute for long time as secure communication may fail.
            new Timer().schedule(new TimerTask()
            {
                @Override
                public void run()
                {
                    ZRTPTransformEngine.this.muted = false;
                }
            }, 1500);
        }
    }

    /**
     * Method for getting the default secure status value for communication
     *
     * @return the default enabled/disabled status value for secure communication
     */
    public boolean getSecureCommunicationStatus()
    {
        return (srtpInTransformer != null) || (srtpOutTransformer != null);
    }

    /**
     * Start the ZRTP stack immediately, not auto-sensing mode.
     */
    public void startZrtp()
    {
        if (zrtpEngine != null) {
            zrtpEngine.startZrtpEngine();
            started = true;

            securityEventManager.securityNegotiationStarted();
        }
    }

    /**
     * Close the transformer and underlying transform engine.
     *
     * The close functions closes all stored crypto contexts. This deletes key data and forces a
     * cleanup of the crypto contexts.
     */
    public void close()
    {
        stopZrtp();
    }

    /**
     * Stop ZRTP engine.
     *
     * The ZRTP stack stores RS2 without the valid flag. If we close the ZRTP stream then check if
     * we need to set RS2 to valid. This is the case if we received less than 10 good SRTP packets.
     * In this case we enable RS2 to make sure the ZRTP self-synchronization is active.
     *
     * This handling is needed to comply to an upcoming newer ZRTP RFC.
     */
    public void stopZrtp()
    {
        if (zrtpEngine != null) {
            if (zrtpUnprotect < 10)
                zrtpEngine.setRs2Valid();
            zrtpEngine.stopZrtp();
            zrtpEngine = null;
            started = false;
        }
        // The SRTP transformer are usually already closed during security-off
        // processing. Check here again just in case ...
        if (srtpOutTransformer != null) {
            srtpOutTransformer.close();
            srtpOutTransformer = null;
        }
        if (srtpInTransformer != null) {
            srtpInTransformer.close();
            srtpInTransformer = null;
        }
        if (zrtcpTransformer != null) {
            zrtcpTransformer.close();
            zrtcpTransformer = null;
        }
    }

    /**
     * Cleanup function for any remaining timers
     */
    @Override
    public void cleanup()
    {
        disposed = true;
        stopZrtp();

        if (timeoutProvider != null) {
            timeoutProvider.stopRun();
            timeoutProvider = null;
        }
    }

    /**
     * Set the SSRC of the RTP transmitter stream.
     *
     * ZRTP fills the SSRC in the ZRTP messages.
     *
     * @param ssrc SSRC to set
     */
    public void setOwnSSRC(long ssrc)
    {
        ownSSRC = (int) ssrc;
    }

    /**
     * The data output stream calls this method to transform outgoing packets.
     *
     * @see PacketTransformer#transform(RawPacket[])
     */
    @Override
    public RawPacket transform(RawPacket pkt)
    {
        // Never transform outgoing ZRTP (invalid RTP) packets.
        if (!ZrtpRawPacket.isZrtpData(pkt)) {
            // ZRTP needs the SSRC of the sending stream.
            if (enableZrtp && (ownSSRC == 0))
                ownSSRC = pkt.getSSRC();
            // If SRTP is active then srtpTransformer is set, use it.
            if (srtpOutTransformer != null)
                pkt = srtpOutTransformer.transform(pkt);
        }
        return pkt;
    }

    /**
     * The input data stream calls this method to transform incoming packets.
     *
     * @see PacketTransformer#reverseTransform(RawPacket[])
     */
    @Override
    public RawPacket reverseTransform(RawPacket pkt)
    {
        // Check if we need to start ZRTP
        if (!started && enableZrtp && ownSSRC != 0)
            startZrtp();

        /*
         * If the incoming packet is not a ZRTP packet, treat it as a normal RTP packet and handle it accordingly.
         */
        if (!ZrtpRawPacket.isZrtpData(pkt)) {
            if (srtpInTransformer == null)
                return muted ? null : pkt;

            RawPacket pkt2 = srtpInTransformer.reverseTransform(pkt);
            // if packet was valid (i.e. not null) and ZRTP engine started and in Wait for
            // Confirm2 Ack then emulate a Conf2Ack packet. See ZRTP specification chap. 5.6
            if ((pkt2 != null) && started
                    && zrtpEngine.inState(ZrtpStateClass.ZrtpStates.WaitConfAck)) {
                zrtpEngine.conf2AckSecure();
            }
            if (pkt2 != null)
                zrtpUnprotect++;

            return pkt2;
        }

        /*
         * Process if ZRTP is enabled. In any case return null because ZRTP packets must never reach the application.
         */
        if (enableZrtp && started) {
            ZrtpRawPacket zPkt = new ZrtpRawPacket(pkt);
            if (!zPkt.checkCrc()) {
                securityEventManager.showMessage(ZrtpCodes.MessageSeverity.Warning,
                        EnumSet.of(ZrtpCodes.WarningCodes.WarningCRCmismatch));
            }
            // Check if it is really a ZRTP packet, if not don't process it
            else if (zPkt.hasMagic()) {
                int extHeaderOffset = zPkt.getHeaderLength() - zPkt.getExtensionLength() - RawPacket.EXT_HEADER_SIZE;
                // zrtp engine need a "pointer" to the extension header, so we
                // give him the extension header and the payload data
                byte[] extHeader = zPkt.readRegion(extHeaderOffset, RawPacket.EXT_HEADER_SIZE
                        + zPkt.getExtensionLength() + zPkt.getPayloadLength());
                zrtpEngine.processZrtpMessage(extHeader, zPkt.getSSRC());
            }
        }
        return null;
    }

    /**
     * The callback method required by the ZRTP implementation. First allocate space to hold the
     * complete ZRTP packet, copy the message part in its place, the initialize the header, counter, SSRC and crc.
     *
     * @param data The ZRTP packet data
     * @return true if sending succeeded, false if it failed.
     */
    public boolean sendDataZRTP(byte[] data)
    {
        int totalLength = ZRTP_PACKET_HEADER + data.length;
        byte[] tmp = new byte[totalLength];
        System.arraycopy(data, 0, tmp, ZRTP_PACKET_HEADER, data.length);
        ZrtpRawPacket packet = new ZrtpRawPacket(tmp, 0, tmp.length);

        packet.setSSRC(ownSSRC);
        packet.setSeqNum(senderZrtpSeqNo++);
        packet.setCrc();

        try {
            RTPConnectorOutputStream outputStream = zrtpConnector.getDataOutputStream();
            if (outputStream != null) {
                outputStream.write(packet.getBuffer(), packet.getOffset(), packet.getLength());
            }
        } catch (IOException e) {
            Timber.w("Failed to send ZRTP data: %s", e.getMessage());
            return false;
        }
        return true;
    }

    /**
     * Switch on the security for the defined part.
     *
     * @param secrets The secret keys and salt negotiated by ZRTP.
     * @param part An enum that defines sender, receiver, or both.
     * @return always return true.
     */
    public boolean srtpSecretsReady(ZrtpSrtpSecrets secrets, EnableSecurity part)
    {
        SrtpPolicy srtpPolicy;
        int cipher = 0, authn = 0, authKeyLen = 0;

        if (secrets.getAuthAlgorithm() == ZrtpConstants.SupportedAuthAlgos.HS) {
            authn = SrtpPolicy.HMACSHA1_AUTHENTICATION;
            authKeyLen = 20;
        }
        else if (secrets.getAuthAlgorithm() == ZrtpConstants.SupportedAuthAlgos.SK) {
            authn = SrtpPolicy.SKEIN_AUTHENTICATION;
            authKeyLen = 32;
        }

        if (secrets.getSymEncAlgorithm() == ZrtpConstants.SupportedSymAlgos.AES) {
            cipher = SrtpPolicy.AESCM_ENCRYPTION;
        }
        else if (secrets.getSymEncAlgorithm() == ZrtpConstants.SupportedSymAlgos.TwoFish) {
            cipher = SrtpPolicy.TWOFISH_ENCRYPTION;
        }

        if (part == EnableSecurity.ForSender) {
            // To encrypt packets: initiator uses initiator keys, responder uses responder keys
            // Create a "half baked" crypto context first and store it. This is
            // the main crypto context for the sending part of the connection.
            if (secrets.getRole() == Role.Initiator) {
                srtpPolicy = new SrtpPolicy(cipher,
                        secrets.getInitKeyLen() / 8,
                        authn, authKeyLen,
                        secrets.getSrtpAuthTagLen() / 8,
                        secrets.getInitSaltLen() / 8
                );

                SrtpContextFactory engine = new SrtpContextFactory(
                        true,
                        secrets.getKeyInitiator(),
                        secrets.getSaltInitiator(),
                        srtpPolicy, srtpPolicy);

                srtpOutTransformer = new SRTPTransformer(engine);
                getRTCPTransformer().setSrtcpOut(new SRTCPTransformer(engine));
            }
            else {
                srtpPolicy = new SrtpPolicy(
                        cipher,
                        secrets.getRespKeyLen() / 8,
                        authn, authKeyLen,
                        secrets.getSrtpAuthTagLen() / 8,
                        secrets.getRespSaltLen() / 8
                );

                SrtpContextFactory engine = new SrtpContextFactory(
                        true,
                        secrets.getKeyResponder(),
                        secrets.getSaltResponder(),
                        srtpPolicy, srtpPolicy);

                srtpOutTransformer = new SRTPTransformer(engine);
                getRTCPTransformer().setSrtcpOut(new SRTCPTransformer(engine));
            }
        }
        else if (part == EnableSecurity.ForReceiver) {
            // To decrypt packets: initiator uses responder keys, responder initiator keys
            // See comment above.
            if (secrets.getRole() == Role.Initiator) {
                srtpPolicy = new SrtpPolicy(
                        cipher,
                        secrets.getRespKeyLen() / 8,
                        authn, authKeyLen,
                        secrets.getSrtpAuthTagLen() / 8,
                        secrets.getRespSaltLen() / 8
                );

                SrtpContextFactory engine = new SrtpContextFactory(
                        false /* receiver */,
                        secrets.getKeyResponder(),
                        secrets.getSaltResponder(),
                        srtpPolicy, srtpPolicy);

                srtpInTransformer = new SRTPTransformer(engine);
                getRTCPTransformer().setSrtcpIn(new SRTCPTransformer(engine));
                this.muted = false;
            }
            else {
                srtpPolicy = new SrtpPolicy(
                        cipher,
                        secrets.getInitKeyLen() / 8,
                        authn, authKeyLen, // auth key length
                        secrets.getSrtpAuthTagLen() / 8,
                        secrets.getInitSaltLen() / 8
                );

                SrtpContextFactory engine = new SrtpContextFactory(
                        false /* receiver */,
                        secrets.getKeyInitiator(),
                        secrets.getSaltInitiator(),
                        srtpPolicy, srtpPolicy);

                srtpInTransformer = new SRTPTransformer(engine);
                getRTCPTransformer().setSrtcpIn(new SRTCPTransformer(engine));
                this.muted = false;
            }
        }
        return true;
    }

    /**
     * Switch on the security.
     *
     * ZRTP calls this method after it has computed the SAS and check if it is verified or not.
     *
     * @param c The name of the used cipher algorithm and mode, or NULL.
     * @param s The SAS string.
     * @param verified if {@code verified} is true then SAS was verified by both parties during a previous call.
     * @see gnu.java.zrtp.ZrtpCallback#srtpSecretsOn(java.lang.String, java.lang.String, boolean)
     */
    public void srtpSecretsOn(String c, String s, boolean verified)
    {
        if (securityEventManager != null) {
            securityEventManager.secureOn(c);
            if ((s != null) || !verified)
                securityEventManager.showSAS(s, verified);
        }
    }

    /**
     * This method shall clear the ZRTP secrets.
     *
     * @param part Defines for which part (sender or receiver) to switch on security
     */
    public void srtpSecretsOff(EnableSecurity part)
    {
        if (part == EnableSecurity.ForSender) {
            if (srtpOutTransformer != null) {
                srtpOutTransformer.close();
                srtpOutTransformer = null;
            }
        }
        else if (part == EnableSecurity.ForReceiver) {
            if (srtpInTransformer != null) {
                srtpInTransformer.close();
                srtpInTransformer = null;
            }
        }

        if (securityEventManager != null)
            securityEventManager.secureOff();
    }

    /**
     * Activate timer.
     *
     * @param time The time in ms for the timer.
     * @return always return 1.
     */
    public int activateTimer(int time)
    {
        if (timeoutProvider != null)
            timeoutProvider.requestTimeout(time);
        return 1;
    }

    /**
     * Cancel the active timer.
     *
     * @return always return 1.
     */
    public int cancelTimer()
    {
        if (timeoutProvider != null)
            timeoutProvider.cancelRequest();
        return 1;
    }

    /**
     * Timeout handling function. Delegates the handling to the ZRTP engine.
     */
    public void handleTimeout()
    {
        // processTimeout will wait for ~10 below events before failing
        // sendInfo(ZrtpCodes.MessageSeverity.Severe, EnumSet.of(ZrtpCodes.SevereCodes.SevereTooMuchRetries));
        if (zrtpEngine != null)
            zrtpEngine.processTimeout();
    }

    /**
     * Send information messages to the hosting environment.
     *
     * @param severity This defines the message's severity
     * @param subCode The message code.
     */
    public void sendInfo(ZrtpCodes.MessageSeverity severity, EnumSet<?> subCode)
    {
        final String[] version_and_hash = zrtpEngine.getPeerHelloHashSep();

        if (version_and_hash != null
                && receivedSignaledZRTPVersion != null
                && receivedSignaledZRTPHashValue != null) {
            final String peerHelloVersion = version_and_hash[0];
            final String peerHelloHash = version_and_hash[1];

            if (!peerHelloVersion.equals(receivedSignaledZRTPVersion)
                    || !peerHelloHash.equals(receivedSignaledZRTPHashValue)) {
                if (securityEventManager != null) {
                    securityEventManager.showMessage(ZrtpCodes.MessageSeverity.Severe, EnumSet.of(ZrtpCodes.SevereCodes.SevereSecurityException));
                }
                close();
            }
        }

        if (securityEventManager != null)
            securityEventManager.showMessage(severity, subCode);

        if (ZrtpCodes.MessageSeverity.ZrtpError.equals(severity)) {
            Timber.w(new Exception("ZRTP Error: " + subCode.toString()));
        }
    }

    /**
     * Comes a message that zrtp negotiation has failed.
     *
     * @param severity This defines the message's severity
     * @param subCode The message code.
     */
    public void zrtpNegotiationFailed(ZrtpCodes.MessageSeverity severity, EnumSet<?> subCode)
    {
        if (securityEventManager != null)
            securityEventManager.zrtpNegotiationFailed(severity, subCode);
    }

    /**
     * The other part doesn't support zrtp.
     */
    public void zrtpNotSuppOther()
    {
        if (securityEventManager != null)
            securityEventManager.zrtpNotSuppOther();
    }

    /**
     * Zrtp ask for Enrollment.
     *
     * @param info supplied info.
     */
    public void zrtpAskEnrollment(ZrtpCodes.InfoEnrollment info)
    {
        if (securityEventManager != null)
            securityEventManager.zrtpAskEnrollment(info);
    }

    /**
     * @param info information to the user about the result of an enrollment.
     * @see gnu.java.zrtp.ZrtpCallback#zrtpInformEnrollment(gnu.java.zrtp.ZrtpCodes.InfoEnrollment)
     */
    public void zrtpInformEnrollment(ZrtpCodes.InfoEnrollment info)
    {
        if (securityEventManager != null)
            securityEventManager.zrtpInformEnrollment(info);
    }

    /**
     * @param sasHash Hash value of Short Authentication String.
     * @see gnu.java.zrtp.ZrtpCallback#signSAS(byte[])
     */
    public void signSAS(byte[] sasHash)
    {
        if (securityEventManager != null)
            securityEventManager.signSAS(sasHash);
    }

    /**
     * @param sasHash Hash value of Short Authentication String.
     * @return false if signature check fails, true otherwise
     * @see gnu.java.zrtp.ZrtpCallback#checkSASSignature(byte[])
     */
    public boolean checkSASSignature(byte[] sasHash)
    {
        return (securityEventManager != null)
                && securityEventManager.checkSASSignature(sasHash);
    }

    /**
     * Sets the enableZrtp flag.
     *
     * @param onOff The value for the enableZrtp flag.
     */
    public void setEnableZrtp(boolean onOff)
    {
        enableZrtp = onOff;
    }

    /**
     * Returns the enableZrtp flag.
     *
     * @return the enableZrtp flag.
     */
    public boolean isEnableZrtp()
    {
        return enableZrtp;
    }

    /**
     * Set the SAS as verified internally if the user confirms it
     */
    public void SASVerified()
    {
        if (zrtpEngine != null)
            zrtpEngine.SASVerified();
        if (securityEventManager != null)
            securityEventManager.setSASVerified(true);
    }

    /**
     * Resets the internal engine SAS verified flag
     */
    public void resetSASVerified()
    {
        if (zrtpEngine != null)
            zrtpEngine.resetSASVerified();
        if (securityEventManager != null)
            securityEventManager.setSASVerified(false);
    }

    /**
     * Method called when the user requests through GUI to switch a secured call to unsecured mode.
     * Just forwards the request to the Zrtp class.
     */
    public void requestGoClear()
    {
        // if (zrtpEngine != null)
        // zrtpEngine.requestGoClear();
    }

    /**
     * Method called when the user requests through GUI to switch a previously unsecured call back
     * to secure mode. Just forwards the request to the Zrtp class.
     */
    public void requestGoSecure()
    {
        // if (zrtpEngine != null)
        // zrtpEngine.requestGoSecure();
    }

    /**
     * Sets the auxiliary secret data
     *
     * @param data The auxiliary secret data
     */
    public void setAuxSecret(byte[] data)
    {
        if (zrtpEngine != null)
            zrtpEngine.setAuxSecret(data);
    }

    /**
     * Sets the client ID
     *
     * @param id The client ID
     */
    public void setClientId(String id)
    {
        clientIdString = id;
    }

    /**
     * Gets the Hello packet Hash
     *
     * @param index Hello hash of the Hello packet identified by index.
     * Index must be 0 <= index < SUPPORTED_ZRTP_VERSIONS.
     * @return the Hello packet hash
     */
    public String getHelloHash(int index)
    {
        return (zrtpEngine != null) ? zrtpEngine.getHelloHash(index) : "";
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
        return (zrtpEngine != null) ? zrtpEngine.getHelloHashSep(index) : null;
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
        return (zrtpEngine != null) ? zrtpEngine.getPeerHelloHash() : "";
    }

    /**
     * Gets the multistream params
     *
     * @return the multistream params
     */
    public byte[] getMultiStrParams()
    {
        return (zrtpEngine != null) ? zrtpEngine.getMultiStrParams() : new byte[0];
    }

    /**
     * Sets the multistream params (The multistream part needs further development)
     *
     * @param parameters the multistream params
     */
    public void setMultiStrParams(byte[] parameters)
    {
        if (zrtpEngine != null)
            zrtpEngine.setMultiStrParams(parameters);
    }

    /**
     * Gets the multistream flag (The multistream part needs further development)
     *
     * @return the multistream flag
     */
    public boolean isMultiStream()
    {
        return (zrtpEngine != null) && zrtpEngine.isMultiStream();
    }

    /**
     * Used to accept a PBX enrollment request (The PBX part needs further development)
     *
     * @param accepted The boolean value indicating if the request is accepted
     */
    public void acceptEnrollment(boolean accepted)
    {
        if (zrtpEngine != null)
            zrtpEngine.acceptEnrollment(accepted);
    }

    /**
     * Get the committed SAS rendering algorithm for this ZRTP session.
     *
     * @return the committed SAS rendering algorithm
     */
    public ZrtpConstants.SupportedSASTypes getSasType()
    {
        return (zrtpEngine != null) ? zrtpEngine.getSasType() : null;
    }

    /**
     * Get the computed SAS hash for this ZRTP session.
     *
     * @return a reference to the byte array that contains the full SAS hash.
     */
    public byte[] getSasHash()
    {
        return (zrtpEngine != null) ? zrtpEngine.getSasHash() : null;
    }

    /**
     * Send the SAS relay packet.
     *
     * The method creates and sends a SAS relay packet according to the ZRTP specifications.
     * Usually only a MitM capable user agent (PBX) uses this function.
     *
     * @param sh the full SAS hash value
     * @param render the SAS rendering algorithm
     * @return true if the SASReplay packet has been correctly sent, false otherwise
     */
    public boolean sendSASRelayPacket(byte[] sh, ZrtpConstants.SupportedSASTypes render)
    {
        return (zrtpEngine != null)
                && zrtpEngine.sendSASRelayPacket(sh, render);
    }

    /**
     * Check the state of the MitM mode flag.
     *
     * If true then this ZRTP session acts as MitM, usually enabled by a PBX based client (useragent)
     *
     * @return state of mitmMode
     */
    public boolean isMitmMode()
    {
        return mitmMode;
    }

    /**
     * Set the state of the MitM mode flag.
     *
     * If MitM mode is set to true this ZRTP session acts as MitM, usually enabled by a PBX based client (user agent).
     *
     * @param mitmMode defines the new state of the mitmMode flag
     */
    public void setMitmMode(boolean mitmMode)
    {
        this.mitmMode = mitmMode;
    }

    /**
     * Enables or disables paranoid mode.
     *
     * For further explanation of paranoid mode refer to the documentation of ZRtp class.
     *
     * @param yesNo If set to true then paranoid mode is enabled.
     */
    public void setParanoidMode(boolean yesNo)
    {
        enableParanoidMode = yesNo;
    }

    /**
     * Check status of paranoid mode.
     *
     * @return Returns true if paranoid mode is enabled.
     */
    public boolean isParanoidMode()
    {
        return enableParanoidMode;
    }

    /**
     * Check the state of the enrollment mode.
     *
     * If true then we will set the enrollment flag (E) in the confirm packets and performs the
     * enrollment actions. A MitM (PBX) enrollment service sets this flag.
     *
     * @return status of the enrollmentMode flag.
     */
    public boolean isEnrollmentMode()
    {
        return (zrtpEngine != null)
                && zrtpEngine.isEnrollmentMode();
    }

    /**
     * Set the state of the enrollment mode.
     *
     * If true then we will set the enrollment flag (E) in the confirm packets and perform the
     * enrollment actions. A MitM (PBX) enrollment service must set this mode to true.
     *
     * Can be set to true only if mitmMode is also true.
     *
     * @param enrollmentMode defines the new state of the enrollmentMode flag
     */
    public void setEnrollmentMode(boolean enrollmentMode)
    {
        if (zrtpEngine != null)
            zrtpEngine.setEnrollmentMode(enrollmentMode);
    }

    /**
     * Sets signature data for the Confirm packets
     *
     * @param data the signature data
     * @return true if signature data was successfully set
     */
    public boolean setSignatureData(byte[] data)
    {
        return (zrtpEngine != null) && zrtpEngine.setSignatureData(data);
    }

    /**
     * Gets signature data
     *
     * @return the signature data
     */
    public byte[] getSignatureData()
    {
        return (zrtpEngine != null)
                ? zrtpEngine.getSignatureData() : new byte[0];
    }

    /**
     * Gets signature length
     *
     * @return the signature length
     */
    public int getSignatureLength()
    {
        return (zrtpEngine != null) ? zrtpEngine.getSignatureLength() : 0;
    }

    /**
     * Method called by the Zrtp class as result of a GoClear request from the other peer. An
     * explicit user confirmation is needed before switching to unsecured mode. This is asked
     * through the user callback.
     */
    public void handleGoClear()
    {
        securityEventManager.confirmGoClear();
    }

    /**
     * Sets the RTP connector using this ZRTP engine
     *
     * @param connector the connector to set
     */
    public void setConnector(AbstractRTPConnector connector)
    {
        zrtpConnector = connector;
    }

    /**
     * Sets the user callback class used to maintain the GUI ZRTP part
     *
     * @param ub The user callback class
     */
    public void setUserCallback(SecurityEventManager ub)
    {
        securityEventManager = ub;
    }

    /**
     * Returns the current status of the ZRTP engine
     *
     * @return the current status of the ZRTP engine
     */
    public boolean isStarted()
    {
        return started;
    }

    /**
     * Gets the user callback used to manage the GUI part of ZRTP
     *
     * @return the user callback
     */
    public SecurityEventManager getUserCallback()
    {
        return securityEventManager;
    }

    /**
     * Get other party's ZID (ZRTP Identifier) data
     *
     * This functions returns the other party's ZID that was received during ZRTP processing.
     *
     * The ZID data can be retrieved after ZRTP receive the first Hello packet from the other
     * party. The application may call this method for example during SAS processing in showSAS
     * (...) user callback method.
     *
     * @return the ZID data as byte array.
     */
    public byte[] getPeerZid()
    {
        return (zrtpEngine != null) ? zrtpEngine.getPeerZid() : null;
    }

    /**
     * Get number of supported ZRTP protocol versions.
     *
     * @return the number of supported ZRTP protocol versions.
     */
    public int getNumberSupportedVersions()
    {
        return (zrtpEngine != null) ? zrtpEngine.getNumberSupportedVersions() : 0;
    }

    /**
     * Get negotiated ZRTP protocol version.
     *
     * @return the integer representation of the negotiated ZRTP protocol version.
     */
    public int getCurrentProtocolVersion()
    {
        return (zrtpEngine != null) ? zrtpEngine.getCurrentProtocolVersion() : 0;
    }

    /**
     * Set ZRTP version received from the signaling layer.
     *
     * @param version received version
     */
    public void setReceivedSignaledZRTPVersion(final String version)
    {
        this.receivedSignaledZRTPVersion = version;
    }

    /**
     * Set ZRTP hash value received from the signaling layer.
     *
     * @param value hash value
     */
    public void setReceivedSignaledZRTPHashValue(final String value)
    {
        this.receivedSignaledZRTPHashValue = value;
    }
}
