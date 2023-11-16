/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.atalk.impl.neomedia.rtp;

import org.atalk.impl.neomedia.jmfext.media.rtp.RTPSessionMgr;
import org.atalk.impl.neomedia.rtp.translator.RTPTranslatorImpl;
import org.atalk.service.neomedia.MediaStream;
import org.atalk.service.neomedia.RTPTranslator;
import org.atalk.service.neomedia.SSRCFactory;

import java.io.IOException;
import java.util.Vector;

import javax.media.Format;
import javax.media.format.UnsupportedFormatException;
import javax.media.protocol.DataSource;
import javax.media.rtp.GlobalReceptionStats;
import javax.media.rtp.GlobalTransmissionStats;
import javax.media.rtp.RTPConnector;
import javax.media.rtp.RTPManager;
import javax.media.rtp.ReceiveStreamListener;
import javax.media.rtp.RemoteListener;
import javax.media.rtp.SendStream;
import javax.media.rtp.SendStreamListener;
import javax.media.rtp.SessionListener;

import timber.log.Timber;

/**
 * Implements the <code>RTPManager</code> interface as used by a <code>MediaStream</code>.
 * The media steam is either handled via rtpManager or rtpTranslator (encrypted); cannot have both null
 *
 * @author Lyubomir Marinov
 * @author Eng Chong Meng
 */
public class StreamRTPManager
{
    /**
     * The <code>MediaStream</code> that uses this <code>StreamRTPManager</code>
     */
    private final MediaStream stream;

    /**
     * The <code>RTPManager</code> this instance is to delegate to when it is not attached to an <code>RTPTranslator</code>.
     */
    private final RTPManager rtpManager;

    /**
     * The <code>RTPTranslator</code> which this instance is attached to and which forwards the RTP and
     * RTCP flows of the <code>MediaStream</code> associated with this instance to other <code>MediaStream</code>s.
     */
    private final RTPTranslatorImpl rtpTranslator;

    /**
     * Initializes a new <code>StreamRTPManager</code> instance which is, optionally,
     * attached to a specific <code>RTPTranslator</code> which is to forward the RTP and
     * RTCP flows of the associated <code>MediaStream</code> to other <code>MediaStream</code>s.
     *
     * @param stream the <code>MediaStream</code> that created this <code>StreamRTPManager</code>.
     * @param translator the <code>RTPTranslator</code> to attach the new instance to or <code>null</code>
     * if the new instance is to not be attached to any <code>RTPTranslator</code>
     */
    public StreamRTPManager(MediaStream stream, RTPTranslator translator)
    {
        this.stream = stream;
        this.rtpTranslator = (RTPTranslatorImpl) translator;
        rtpManager = (this.rtpTranslator == null) ? RTPManager.newInstance() : null;
    }

    public void addFormat(Format format, int payloadType)
    {
        if (rtpTranslator == null)
            rtpManager.addFormat(format, payloadType);
        else
            rtpTranslator.addFormat(this, format, payloadType);
    }

    public void addReceiveStreamListener(ReceiveStreamListener listener)
    {
        if (rtpTranslator == null)
            rtpManager.addReceiveStreamListener(listener);
        else
            rtpTranslator.addReceiveStreamListener(this, listener);
    }

    public void addRemoteListener(RemoteListener listener)
    {
        if (rtpTranslator == null)
            rtpManager.addRemoteListener(listener);
        else
            rtpTranslator.addRemoteListener(this, listener);
    }

    public void addSendStreamListener(SendStreamListener listener)
    {
        if (rtpTranslator == null)
            rtpManager.addSendStreamListener(listener);
        else
            rtpTranslator.addSendStreamListener(this, listener);
    }

    public void addSessionListener(SessionListener listener)
    {
        if (rtpTranslator == null)
            rtpManager.addSessionListener(listener);
        else
            rtpTranslator.addSessionListener(this, listener);
    }

    public SendStream createSendStream(DataSource dataSource, int streamIndex)
            throws IOException, UnsupportedFormatException, NullPointerException
    {
        // NullPointerException: Attempt to invoke virtual method 'SessionAddress.getDataPort()' on a null object reference.
        // Happen when call is terminated in middle of setting up.
        if (rtpTranslator == null)
            return rtpManager.createSendStream(dataSource, streamIndex);
        else
            return rtpTranslator.createSendStream(this, dataSource, streamIndex);
    }

    public void dispose()
    {
        Timber.d("Stream RTP Manager disposing: x = %s; m = %s", rtpTranslator, rtpManager);

		if (rtpTranslator == null)
			rtpManager.dispose();
		else
			rtpTranslator.dispose(this);
    }

    /**
     * Gets a control of a specific type over this instance. Invokes {@link #getControl(String)}.
     *
     * @param controlType a <code>Class</code> which specifies the type of the control over this instance to get
     * @return a control of the specified <code>controlType</code> over this instance
     * if this instance supports such a control; otherwise, <code>null</code>
     */
    @SuppressWarnings("unchecked")
    public <T> T getControl(Class<T> controlType)
    {
        return (T) getControl(controlType.getName());
    }

    /**
     * Gets a control of a specific type over this instance.
     *
     * @param controlType a <code>String</code> which specifies the type (i.e. the name of the class)
     * of the control over this instance to get
     * @return a control of the specified <code>controlType</code> over this instance if this instance
     * supports such a control; otherwise, <code>null</code>
     */
    public Object getControl(String controlType)
    {
        if (rtpTranslator == null)
            return rtpManager.getControl(controlType);
        else
            return rtpTranslator.getControl(this, controlType);
    }

    public GlobalReceptionStats getGlobalReceptionStats()
    {
        if (rtpTranslator == null)
            return rtpManager.getGlobalReceptionStats();
        else
            return rtpTranslator.getGlobalReceptionStats(this);
    }

    public GlobalTransmissionStats getGlobalTransmissionStats()
    {
        if (rtpTranslator == null)
            return rtpManager.getGlobalTransmissionStats();
        else
            return rtpTranslator.getGlobalTransmissionStats(this);
    }

    public long getLocalSSRC()
    {
        if (rtpTranslator == null) {
            return ((net.sf.fmj.media.rtp.RTPSessionMgr) rtpManager).getLocalSSRC();
        }
        else
            return rtpTranslator.getLocalSSRC(this);
    }

    /**
     * Returns the <code>MediaStream</code> that uses this <code>StreamRTPManager</code>
     *
     * @return the <code>MediaStream</code> that uses this <code>StreamRTPManager</code>
     */
    public MediaStream getMediaStream()
    {
        return stream;
    }

    @SuppressWarnings("rawtypes")
    public Vector getReceiveStreams()
    {
        if (rtpTranslator == null)
            return rtpManager.getReceiveStreams();
        else
            return rtpTranslator.getReceiveStreams(this);
    }

    @SuppressWarnings("rawtypes")
    public Vector getSendStreams()
    {
        if (rtpTranslator == null)
            return rtpManager.getSendStreams();
        else
            return rtpTranslator.getSendStreams(this);
    }

    public void initialize(RTPConnector connector)
    {
        if (rtpTranslator == null)
            rtpManager.initialize(connector);
        else
            rtpTranslator.initialize(this, connector);
    }

    public void removeReceiveStreamListener(ReceiveStreamListener listener)
    {
        if (rtpTranslator == null)
            rtpManager.removeReceiveStreamListener(listener);
        else
            rtpTranslator.removeReceiveStreamListener(this, listener);
    }

    public void removeRemoteListener(RemoteListener listener)
    {
        if (rtpTranslator == null)
            rtpManager.removeRemoteListener(listener);
        else
            rtpTranslator.removeRemoteListener(this, listener);
    }

    public void removeSendStreamListener(SendStreamListener listener)
    {
        if (rtpTranslator == null)
            rtpManager.removeSendStreamListener(listener);
        else
            rtpTranslator.removeSendStreamListener(this, listener);
    }

    public void removeSessionListener(SessionListener listener)
    {
        if (rtpTranslator == null)
            rtpManager.removeSessionListener(listener);
        else
            rtpTranslator.removeSessionListener(this, listener);
    }

    /**
     * Sets the <code>SSRCFactory</code> to be utilized by this instance to generate new synchronization
     * source (SSRC) identifiers.
     *
     * @param ssrcFactory the <code>SSRCFactory</code> to be utilized by this instance to generate new
     * synchronization source (SSRC) identifiers or <code>null</code> if this instance is to
     * employ internal logic to generate new synchronization source (SSRC) identifiers
     */
    public void setSSRCFactory(SSRCFactory ssrcFactory)
    {
        if (rtpTranslator == null) {
            RTPManager m = this.rtpManager;

            if (m instanceof RTPSessionMgr) {
                RTPSessionMgr sm = (RTPSessionMgr) m;
                sm.setSSRCFactory(ssrcFactory);
            }
        }
        else {
            rtpTranslator.setSSRCFactory(ssrcFactory);
        }
    }
}
