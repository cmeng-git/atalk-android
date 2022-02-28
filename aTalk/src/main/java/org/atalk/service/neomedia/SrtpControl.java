/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.atalk.service.neomedia;

import org.atalk.impl.neomedia.AbstractRTPConnector;
import org.atalk.service.neomedia.event.SrtpListener;
import org.atalk.util.MediaType;

/**
 * Controls SRTP encryption in the MediaStream.
 *
 * @author Damian Minkov
 */
public interface SrtpControl
{
    public static final String RTP_SAVP = "RTP/SAVP";
    public static final String RTP_SAVPF = "RTP/SAVPF";

    /**
     * Adds a <code>cleanup()</code> method to
     * <code>TransformEngine</code> which is to go in hand with the
     * <code>cleanup()</code> method of <code>SrtpControl</code>.
     *
     * @author Lyubomir Marinov
     */
    interface TransformEngine extends org.atalk.impl.neomedia.transform.TransformEngine
    {
        /**
         * Cleans up this <code>TransformEngine</code> and prepares it for garbage collection.
         */
        void cleanup();
    }

    /**
     * Cleans up this <code>SrtpControl</code> and its <code>TransformEngine</code>.
     *
     * @param user the instance which requests the clean up.
     */
    void cleanup(Object user);

    /**
     * Gets the default secure/insecure communication status for the supported call sessions.
     *
     * @return default secure communication status for the supported call sessions.
     */
    boolean getSecureCommunicationStatus();

    /**
     * Gets the <code>SrtpControlType</code> of this instance.
     *
     * @return the <code>SrtpControlType</code> of this instance
     */
    SrtpControlType getSrtpControlType();

    /**
     * Returns the <code>SrtpListener</code> which listens for security events.
     *
     * @return the <code>SrtpListener</code> which listens for security events
     */
    SrtpListener getSrtpListener();

    /**
     * Returns the transform engine currently used by this stream.
     *
     * @return the RTP stream transformation engine
     */
    TransformEngine getTransformEngine();

    /**
     * Indicates if the key exchange method is dependent on secure transport of the signaling channel.
     *
     * @return <code>true</code> when secure signaling is required to make the encryption secure; <code>false</code>, otherwise.
     */
    boolean requiresSecureSignalingTransport();

    /**
     * Sets the <code>RTPConnector</code> which is to use or uses this SRTP engine.
     *
     * @param connector the <code>RTPConnector</code> which is to use or uses this SRTP engine
     */
    void setConnector(AbstractRTPConnector connector);

    /**
     * When in multistream mode, enables the master session.
     *
     * @param masterSession whether current control, controls the master session.
     */
    void setMasterSession(boolean masterSession);

    /**
     * Sets the multistream data, which means that the master stream has successfully started and
     * this will start all other streams in this session.
     *
     * @param master The security control of the master stream.
     */
    void setMultistream(SrtpControl master);

    /**
     * Sets a <code>SrtpListener</code> that will listen for security events.
     *
     * @param srtpListener the <code>SrtpListener</code> that will receive the events
     */
    void setSrtpListener(SrtpListener srtpListener);

    /**
     * Starts and enables zrtp in the stream holding this control.
     *
     * @param mediaType the media type of the stream this control controls.
     */
    void start(MediaType mediaType);

    /**
     * Registers <code>user</code> as an instance which is currently using this <code>SrtpControl</code>.
     *
     * @param user
     */
    void registerUser(Object user);
}
