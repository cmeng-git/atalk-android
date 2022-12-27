/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.atalk.impl.neomedia.transform.zrtp;

import org.atalk.android.R;
import org.atalk.android.aTalkApp;
import org.atalk.service.libjitsi.LibJitsi;
import org.atalk.service.neomedia.event.SrtpListener;
import org.atalk.service.resources.ResourceManagementService;
import org.atalk.util.MediaType;

import java.util.EnumSet;
import java.util.Iterator;

import gnu.java.zrtp.ZrtpCodes;
import gnu.java.zrtp.ZrtpUserCallback;
import timber.log.Timber;

/**
 * The user callback class for ZRTP4J.
 *
 * This class constructs and sends events to the ZRTP GUI implementation. The
 * <code>showMessage()<code> function implements a specific check to start
 * associated ZRTP multi-stream sessions.
 *
 * Coordinate this callback class with the associated GUI implementation class
 *
 * @author Emanuel Onica
 * @author Werner Dittmann
 * @author Yana Stamcheva
 * @author Eng Chong Meng
 * @see net.java.sip.communicator.impl.gui.main.call.ZrtpSecurityPanel
 */
public class SecurityEventManager extends ZrtpUserCallback
{
    /**
     * A warning <code>String</code> that we display to the user.
     */
    public static final String WARNING_NO_RS_MATCH
            = aTalkApp.getResString(R.string.impl_media_security_WARNING_NO_RS_MATCH);

    /**
     * A warning <code>String</code> that we display to the user.
     */
    public static final String WARNING_NO_EXPECTED_RS_MATCH
            = aTalkApp.getResString(R.string.impl_media_security_WARNING_NO_EXPECTED_RS_MATCH);

    /**
     * The zrtp control that this manager is associated with.
     */
    private final ZrtpControlImpl zrtpControl;

    /**
     * The event manager that belongs to the ZRTP master session.
     */
    private SecurityEventManager masterEventManager;

    /**
     * A callback to the instance that created us.
     */
    private SrtpListener securityListener;

    /**
     * The type of this session.
     */
    private MediaType sessionType;

    /**
     * SAS string.
     */
    private String sas;

    /**
     * Cipher.
     */
    private String cipher;

    /**
     * Indicates if the SAS has already been verified in a previous session.
     */
    private boolean isSasVerified;

    /**
     * The class constructor.
     *
     * @param zrtpControl that this manager is to be associated with.
     */
    public SecurityEventManager(ZrtpControlImpl zrtpControl)
    {
        this.zrtpControl = zrtpControl;
        this.securityListener = zrtpControl.getSrtpListener();
    }

    /**
     * Set the type of this session.
     *
     * @param sessionType the <code>MediaType</code> of this session
     */
    public void setSessionType(MediaType sessionType)
    {
        this.sessionType = sessionType;
    }

    /**
     * Sets the event manager that belongs to the ZRTP master session.
     *
     * @param master the event manager that belongs to the ZRTP master session.
     */
    void setMasterEventManager(SecurityEventManager master)
    {
        this.masterEventManager = master;
    }

    /*
     * The following methods implement the ZrtpUserCallback interface
     */

    /**
     * Reports the security algorithm that the ZRTP protocol negotiated.
     *
     * @param cipher the cipher
     */
    @Override
    public void secureOn(String cipher)
    {
        Timber.d("%s: cipher enabled: %s", sessionTypeToString(sessionType), cipher);
        this.cipher = cipher;
    }

    /**
     * ZRTP computes the SAS string after nearly all the negotiation and computations are done internally.
     *
     * @param sas The string containing the SAS.
     * @param isVerified is sas verified.
     */
    @Override
    public void showSAS(String sas, boolean isVerified)
    {
        Timber.d("%s: SAS is: %s", sessionTypeToString(sessionType), sas);
        this.sas = sas;
        this.isSasVerified = isVerified;
    }

    /**
     * Sets current SAS verification status.
     *
     * @param isVerified flag indicating whether SAS has been verified.
     */
    public void setSASVerified(boolean isVerified)
    {
        this.isSasVerified = getSecurityString() != null && isVerified;
    }

    /**
     * Show some information to user. ZRTP calls this method to display some information to the user.
     * Along with the message ZRTP provides a severity indicator that defines: Info, Warning, Error, and Alert.
     *
     * @param sev severity of the message.
     * @param subCode the message code.
     */
    @Override
    public void showMessage(ZrtpCodes.MessageSeverity sev, EnumSet<?> subCode)
    {
        int multiStreams = 0;
        Iterator<?> ii = subCode.iterator();
        Object msgCode = ii.next();

        String message = null;
        String i18nMessage = null;
        int severity = 0;

        boolean sendEvent = true;

        if (msgCode instanceof ZrtpCodes.InfoCodes) {
            ZrtpCodes.InfoCodes inf = (ZrtpCodes.InfoCodes) msgCode;

            // Use the following fields if INFORMATION type messages shall be shown to the user via
            // SecurityMessageEvent, i.e. if sendEvent is set to true
            // severity = CallPeerSecurityMessageEvent.INFORMATION;

            // Don't spam user with info messages, only internal processing or logging.
            sendEvent = false;

            // If the ZRTP Master session (DH mode) signals "security on" then start multi-stream sessions.
            // Signal SAS to GUI only if this is a DH mode session. Multi-stream session don't have own SAS data
            if (inf == ZrtpCodes.InfoCodes.InfoSecureStateOn) {
                securityListener.securityTurnedOn(sessionType, cipher, zrtpControl);
            }
        }
        else if (msgCode instanceof ZrtpCodes.WarningCodes) {
            // Warning codes usually do not affect encryption or security.
            // Only in few cases inform the user and ask to verify SAS.
            ZrtpCodes.WarningCodes warn = (ZrtpCodes.WarningCodes) msgCode;
            severity = SrtpListener.WARNING;

            if (warn == ZrtpCodes.WarningCodes.WarningNoRSMatch) {
                message = "No retained shared secret available.";
                i18nMessage = WARNING_NO_RS_MATCH;
            }
            else if (warn == ZrtpCodes.WarningCodes.WarningNoExpectedRSMatch) {
                message = "An expected retained shared secret is missing.";
                i18nMessage = WARNING_NO_EXPECTED_RS_MATCH;
            }
            else if (warn == ZrtpCodes.WarningCodes.WarningCRCmismatch) {
                message = "Internal ZRTP packet checksum mismatch.";
                i18nMessage = aTalkApp.getResString(R.string.impl_media_security_CHECKSUM_MISMATCH);
            }
            else {
                // Other warnings are internal only, no user action required
                sendEvent = false;
            }
        }
        else if (msgCode instanceof ZrtpCodes.SevereCodes) {
            ZrtpCodes.SevereCodes severe = (ZrtpCodes.SevereCodes) msgCode;
            severity = SrtpListener.SEVERE;

            if (severe == ZrtpCodes.SevereCodes.SevereCannotSend) {
                message = "Failed to send data. Internet data connection or peer is down.";
                i18nMessage = aTalkApp.getResString(R.string.impl_media_security_DATA_SEND_FAILED, msgCode.toString());
            }
            else if (severe == ZrtpCodes.SevereCodes.SevereTooMuchRetries) {
                message = "Too much retries during ZRTP negotiation.";
                i18nMessage = aTalkApp.getResString(R.string.impl_media_security_RETRY_RATE_EXCEEDED, msgCode.toString());
            }
            else if (severe == ZrtpCodes.SevereCodes.SevereProtocolError) {
                message = "Internal protocol error occurred.";
                i18nMessage = aTalkApp.getResString(R.string.impl_media_security_INTERNAL_PROTOCOL_ERROR, msgCode.toString());
            }
            else {
                message = "General error has occurred.";
                i18nMessage = aTalkApp.getResString(R.string.impl_media_security_ZRTP_GENERIC_MSG, msgCode.toString());
            }
        }
        else if (msgCode instanceof ZrtpCodes.ZrtpErrorCodes) {
            severity = SrtpListener.ERROR;

            message = "Indicates compatibility problems like for example: unsupported protocol version," +
                    " unsupported hash type, cypher type, SAS scheme, etc.";
            i18nMessage = aTalkApp.getResString(R.string.impl_media_security_ZRTP_GENERIC_MSG, msgCode.toString());
        }
        else {
            // Other warnings are internal only, no user action required
            sendEvent = false;
        }

        if (sendEvent)
            securityListener.securityMessageReceived(message, i18nMessage, severity);

        Timber.d("%s: ZRTP message (%s): code: %s; message: %s; sendEvent: %s",
                sessionTypeToString(sessionType), sev, msgCode, message, sendEvent);
    }

    /**
     * Negotiation has failed.
     *
     * @param severity of the message.
     * @param subCode the message code.
     */
    @Override
    public void zrtpNegotiationFailed(ZrtpCodes.MessageSeverity severity, EnumSet<?> subCode)
    {
        Iterator<?> ii = subCode.iterator();
        Object msgCode = ii.next();

        aTalkApp.showToastMessage(R.string.impl_media_security_ZRTP_HANDSHAKE_TIMEOUT, msgCode);
        Timber.w(new Exception(), "%s: ZRTP key negotiation failed: %s", sessionTypeToString(sessionType), msgCode);
    }

    /**
     * Inform user interface that security is not active any more.
     */
    @Override
    public void secureOff()
    {
        Timber.d("%s: Security off", sessionTypeToString(sessionType));
        securityListener.securityTurnedOff(sessionType);
    }

    /**
     * The other part does not support zrtp.
     */
    @Override
    public void zrtpNotSuppOther()
    {
        Timber.d("%s: Other party does not support ZRTP key negotiation protocol, no secure calls possible.",
                sessionTypeToString(sessionType));
        securityListener.securityTimeout(sessionType);
    }

    /**
     * Inform the user that ZRTP received "go clear" message from its peer.
     */
    @Override
    public void confirmGoClear()
    {
        Timber.d("%s: GoClear confirmation requested.", sessionTypeToString(sessionType));
    }

    /**
     * Converts the <code>sessionType</code> into a <code>String</code>.
     *
     * @param sessionType the <code>MediaType</code> to be converted into a <code>String</code> for the purposes of this
     * <code>SecurityEventManager</code>
     * @return a <code>String</code> representation of <code>sessionType</code>.
     */
    private String sessionTypeToString(MediaType sessionType)
    {
        switch (sessionType) {
            case AUDIO:
                return "AUDIO_SESSION";
            case VIDEO:
                return "VIDEO_SESSION";
            default:
                throw new IllegalArgumentException("sessionType");
        }
    }

    /**
     * Gets the localized message and replace the param. If the param is null we ignore it.
     *
     * @param key the key for the localized message.
     * @param param the param to replace.
     * @return the i18n message.
     */
    private static String getI18NString(String key, String param)
    {
        ResourceManagementService resources = LibJitsi.getResourceManagementService();
        if (resources == null)
            return null;
        else {
            String[] params = (param == null) ? null : new String[]{param};

            return resources.getI18NString(key, params);
        }
    }

    /**
     * Sets a new receiver of the security callback events.
     *
     * @param securityListener An object that receives the security events.
     */
    public void setSrtpListener(SrtpListener securityListener)
    {
        this.securityListener = securityListener;
    }

    /**
     * Gets the SAS for the current media stream.
     *
     * @return the four character ZRTP SAS.
     */
    public String getSecurityString()
    {
        if (masterEventManager != null && masterEventManager != this) {
            return masterEventManager.sas;
        }
        return sas;
    }

    /**
     * Gets the cipher information for the current media stream.
     *
     * @return the cipher information string.
     */
    public String getCipherString()
    {
        return cipher;
    }

    /**
     * Gets the status of the SAS verification.
     *
     * @return true when the SAS has been verified.
     */
    public boolean isSecurityVerified()
    {
        if (masterEventManager != null && masterEventManager != this) {
            return masterEventManager.isSasVerified;
        }
        return isSasVerified;
    }

    /**
     * Indicates that we started the process of securing the the connection.
     */
    public void securityNegotiationStarted()
    {
        // make sure we don't throw any exception
        try {
            this.securityListener.securityNegotiationStarted(sessionType, zrtpControl);
        } catch (Throwable t) {
            Timber.e("Error processing security started.");
        }
    }
}
