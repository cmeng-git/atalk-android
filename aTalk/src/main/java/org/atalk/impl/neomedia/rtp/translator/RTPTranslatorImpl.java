/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.atalk.impl.neomedia.rtp.translator;

import net.sf.fmj.media.rtp.RTCPHeader;
import net.sf.fmj.media.rtp.RTPHeader;
import net.sf.fmj.media.rtp.SSRCCache;

import org.atalk.impl.timberlog.TimberLog;
import org.atalk.impl.neomedia.jmfext.media.rtp.RTPSessionMgr;
import org.atalk.impl.neomedia.rtp.StreamRTPManager;
import org.atalk.service.neomedia.AbstractRTPTranslator;
import org.atalk.service.neomedia.MediaStream;
import org.atalk.service.neomedia.RawPacket;
import org.atalk.service.neomedia.SSRCFactory;
import org.atalk.util.RTPUtils;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Vector;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import javax.media.Buffer;
import javax.media.Format;
import javax.media.format.UnsupportedFormatException;
import javax.media.protocol.DataSource;
import javax.media.rtp.GlobalReceptionStats;
import javax.media.rtp.GlobalTransmissionStats;
import javax.media.rtp.RTPConnector;
import javax.media.rtp.RTPManager;
import javax.media.rtp.ReceiveStream;
import javax.media.rtp.ReceiveStreamListener;
import javax.media.rtp.RemoteListener;
import javax.media.rtp.SendStream;
import javax.media.rtp.SendStreamListener;
import javax.media.rtp.SessionListener;
import javax.media.rtp.event.ReceiveStreamEvent;

import timber.log.Timber;

/**
 * Implements <code>RTPTranslator</code> which represents an RTP translator which forwards RTP and RTCP
 * traffic between multiple <code>MediaStream</code>s.
 *
 * @author Lyubomir Marinov
 * @author Eng Chong Meng
 */
public class RTPTranslatorImpl extends AbstractRTPTranslator
        implements ReceiveStreamListener
{
    /**
     * Logs information about an RTCP packet for debugging purposes.
     *
     * @param obj the object which is the source of the log request
     * @param methodName the name of the method on <code>obj</code> which is the source of the log request
     * @param buf the <code>byte</code>s which (possibly) represent an RTCP packet to be logged for debugging purposes
     * @param off the position within <code>buf</code> at which the valid data begins
     * @param len the number of bytes in <code>buf</code> which constitute the valid data
     */
    static void logRTCP(Object obj, String methodName, byte[] buf, int off, int len)
    {
        // Do the bytes in the specified buffer resemble (a header of) an RTCP packet?
        if (len >= 8 /* BYE */) {
            byte b0 = buf[off];
            int v = (b0 & 0xc0) >>> 6;

            if (v == RTCPHeader.VERSION) {
                byte b1 = buf[off + 1];
                int pt = b1 & 0xff;

                if (pt == 203 /* BYE */) {
                    // Verify the length field.
                    int rtcpLength = (RTPUtils.readUint16AsInt(buf, off + 2) + 1) * 4;

                    if (rtcpLength <= len) {
                        int sc = b0 & 0x1f;
                        int o = off + 4;

                        for (int i = 0, end = off + len; i < sc && o + 4 <= end; ++i, o += 4) {
                            int ssrc = RTPUtils.readInt(buf, o);

                            Timber.log(TimberLog.FINER, "%s.%s: RTCP BYE SSRC/CSRC %s",
                                    obj.getClass().getName(), methodName, Long.toString(ssrc & 0xffffffffL));
                        }
                    }
                }
            }
        }
    }


    /**
     * The <code>RTPConnector</code> which is used by {@link #manager} and which delegates to the
     * <code>RTPConnector</code>s of the <code>StreamRTPManager</code>s attached to this instance.
     */
    private RTPConnectorImpl connector;

    /**
     * A local SSRC for this <code>RTPTranslator</code>. This overrides the SSRC of the
     * <code>RTPManager</code> and it does not deal with SSRC collisions. TAG(cat4-local-ssrc-hurricane).
     */
    private long localSSRC = -1;

    /**
     * The <code>ReadWriteLock</code> which synchronizes the access to and/or modification of the state
     * of this instance. Replaces <code>synchronized</code> blocks in order to reduce the number of
     * exclusive locks and, therefore, the risks of superfluous waiting.
     */
    private final ReadWriteLock _lock = new ReentrantReadWriteLock();

    /**
     * The <code>RTPManager</code> which implements the actual RTP management of this instance.
     */
    private final RTPManager manager = RTPManager.newInstance();

    /**
     * An instance which can be used to send RTCP Feedback Messages, using as 'packet sender SSRC'
     * the SSRC of (the <code>RTPManager</code> of) this <code>RTPTranslator</code>.
     */
    private final RTCPFeedbackMessageSender rtcpFeedbackMessageSender = new RTCPFeedbackMessageSender(this);

    /**
     * The <code>SendStream</code>s created by the <code>RTPManager</code> and the
     * <code>StreamRTPManager</code> -specific views to them.
     */
    private final List<SendStreamDesc> sendStreams = new LinkedList<>();

    /**
     * The list of <code>StreamRTPManager</code>s i.e. <code>MediaStream</code>s which this instance
     * forwards RTP and RTCP traffic between.
     */
    private final List<StreamRTPManagerDesc> streamRTPManagers = new ArrayList<>();

    /**
     * Initializes a new <code>RTPTranslatorImpl</code> instance.
     */
    public RTPTranslatorImpl()
    {
        manager.addReceiveStreamListener(this);
    }

    /**
     * Specifies the RTP payload type (number) to be used for a specific <code>Format</code>. The
     * association between the specified <code>format</code> and the specified <code>payloadType</code> is
     * being added by a specific <code>StreamRTPManager</code> but effects the <code>RTPTranslatorImpl</code> globally.
     *
     * @param streamRTPManager the <code>StreamRTPManager</code> that is requesting the association of <code>format</code> to
     * <code>payloadType</code>
     * @param format the <code>Format</code> which is to be associated with the specified RTP payload type (number)
     * @param payloadType the RTP payload type (number) to be associated with the specified <code>format</code>
     */
    public void addFormat(StreamRTPManager streamRTPManager, Format format, int payloadType)
    {
        Lock lock = _lock.writeLock();
        StreamRTPManagerDesc desc;

        lock.lock();
        try {

            // XXX RTPManager.addFormat is NOT thread-safe. It appears we have
            // decided to provide thread-safety at least on our side. Which may be
            // insufficient in all use cases but it still sounds reasonable in our current use cases.
            manager.addFormat(format, payloadType);
            desc = getStreamRTPManagerDesc(streamRTPManager, true);

        } finally {
            lock.unlock();
        }
        // StreamRTPManager.addFormat is thread-safe.
        desc.addFormat(format, payloadType);
    }

    /**
     * Adds a <code>ReceiveStreamListener</code> to be notified about <code>ReceiveStreamEvent</code>s
     * related to a specific neomedia <code>MediaStream</code> (expressed as a <code>StreamRTPManager</code>
     * for the purposes of and in the terms of <code>RTPTranslator</code>). If the specified
     * <code>listener</code> has already been added, the method does nothing.
     *
     * @param streamRTPManager the <code>StreamRTPManager</code> which specifies the neomedia <code>MediaStream</code> with
     * which the <code>ReceiveStreamEvent</code>s delivered to the specified <code>listener</code>
     * are to be related. In other words, a <code>ReceiveStreamEvent</code> received by
     * <code>RTPTranslatorImpl</code> is first examined to determine which
     * <code>StreamRTPManager</code> it is related to and then it is delivered to the
     * <code>ReceiveStreamListener</code>s which have been added to this
     * <code>RTPTranslatorImpl</code> by that <code>StreamRTPManager</code>.
     * @param listener the <code>ReceiveStreamListener</code> to be notified about <code>ReceiveStreamEvent</code>s
     * related to the specified <code>streamRTPManager</code>
     */
    public void addReceiveStreamListener(StreamRTPManager streamRTPManager, ReceiveStreamListener listener)
    {
        getStreamRTPManagerDesc(streamRTPManager, true).addReceiveStreamListener(listener);
    }

    /**
     * Adds a <code>RemoteListener</code> to be notified about <code>RemoteEvent</code>s received by this
     * <code>RTPTranslatorImpl</code>. Though the request is being made by a specific
     * <code>StreamRTPManager</code>, the addition of the specified <code>listener</code> and the
     * deliveries
     * of the <code>RemoteEvent</code>s are performed irrespective of any <code>StreamRTPManager</code>.
     *
     * @param streamRTPManager the <code>StreamRTPManager</code> which is requesting the addition of the specified
     * <code>RemoteListener</code>
     * @param listener the <code>RemoteListener</code> to be notified about <code>RemoteEvent</code>s received by
     * this <code>RTPTranslatorImpl</code>
     */
    public void addRemoteListener(StreamRTPManager streamRTPManager, RemoteListener listener)
    {
        manager.addRemoteListener(listener);
    }

    /**
     * Not implemented because there are currently no uses of the underlying functionality.
     */
    public void addSendStreamListener(StreamRTPManager streamRTPManager, SendStreamListener listener)
    {
        // TODO Auto-generated method stub
    }

    /**
     * Not implemented because there are currently no uses of the underlying functionality.
     */
    public void addSessionListener(StreamRTPManager streamRTPManager, SessionListener listener)
    {
        // TODO Auto-generated method stub
    }

    /**
     * Closes a specific <code>SendStream</code>.
     *
     * @param sendStreamDesc a <code>SendStreamDesc</code> instance that specifies the <code>SendStream</code> to be closed
     */
    void closeSendStream(SendStreamDesc sendStreamDesc)
    {
        // XXX Here we could potentially start with a read lock and upgrade to a write lock, if
        // the sendStreamDesc is in the sendStreams collection, but does it worth it?
        Lock lock = _lock.writeLock();

        lock.lock();
        try {
            if (sendStreams.contains(sendStreamDesc)
                    && (sendStreamDesc.getSendStreamCount() < 1)) {
                SendStream sendStream = sendStreamDesc.sendStream;

                try {
                    sendStream.close();
                } catch (NullPointerException npe) {
                    // Refer to MediaStreamImpl#stopSendStreams(Iterable<SendStream>, boolean) for
                    // an explanation about the swallowing of the exception.
                    Timber.e(npe, "Failed to close send stream");
                }
                sendStreams.remove(sendStreamDesc);
            }

        } finally {
            lock.unlock();
        }
    }

    /**
     * Creates a <code>SendStream</code> from the stream of a specific <code>DataSource</code> that is at a
     * specific zero-based position within the array/list of streams of that <code>DataSource</code>.
     *
     * @param streamRTPManager the <code>StreamRTPManager</code> which is requesting the creation of a
     * <code>SendStream</code>. Since multiple <code>StreamRTPManager</code> may request the creation
     * of a <code>SendStream</code> from one and the same combination of <code>dataSource</code> and
     * <code>streamIndex</code>, the method may not create a completely new <code>SendStream</code>
     * but may return a <code>StreamRTPManager</code>-specific view of an existing
     * <code>SendStream</code>.
     * @param dataSource the <code>DataSource</code> which provides the stream from which a <code>SendStream</code> is
     * to be created
     * @param streamIndex the zero-based position within the array/list of streams of the specified
     * <code>dataSource</code> of the stream from which a <code>SendStream</code> is to be created
     * @return a <code>SendStream</code> created from the specified <code>dataSource</code> and
     * <code>streamIndex</code>. The returned <code>SendStream</code> implementation is a
     * <code>streamRTPManager</code>-dedicated view to an actual <code>SendStream</code> which may
     * have been created during a previous execution of the method
     * @throws IOException if an error occurs during the execution of
     * {@link RTPManager#createSendStream(DataSource, int)}
     * @throws UnsupportedFormatException if an error occurs during the execution of
     * <code>RTPManager.createSendStream(DataSource, int)</code>
     */
    public SendStream createSendStream(StreamRTPManager streamRTPManager, DataSource dataSource, int streamIndex)
            throws IOException, UnsupportedFormatException
    {
        // XXX Here we could potentially start with a read lock and upgrade to a write lock, if
        // the sendStreamDesc is not in sendStreams collection, but does it worth it?
        Lock lock = _lock.writeLock();
        SendStream ret;

        lock.lock();
        try {

            SendStreamDesc sendStreamDesc = null;

            for (SendStreamDesc s : sendStreams) {
                if ((s.dataSource == dataSource) && (s.streamIndex == streamIndex)) {
                    sendStreamDesc = s;
                    break;
                }
            }
            if (sendStreamDesc == null) {
                SendStream sendStream = manager.createSendStream(dataSource, streamIndex);

                if (sendStream != null) {
                    sendStreamDesc = new SendStreamDesc(this, dataSource, streamIndex, sendStream);
                    sendStreams.add(sendStreamDesc);
                }
            }
            ret = (sendStreamDesc == null)
                    ? null : sendStreamDesc.getSendStream(streamRTPManager, true);

        } finally {
            lock.unlock();
        }
        return ret;
    }

    /**
     * Notifies this instance that an RTP or RTCP packet has been received from a peer represented
     * by a specific <code>PushSourceStreamDesc</code>.
     *
     * @param streamDesc a <code>PushSourceStreamDesc</code> which identifies the peer from which an RTP or RTCP
     * packet has been received
     * @param buf the buffer which contains the bytes of the received RTP or RTCP packet
     * @param off the zero-based index in <code>buf</code> at which the bytes of the received RTP or RTCP packet begin
     * @param len the number of bytes in <code>buf</code> beginning at <code>off</code> which represent the
     * received RTP or RTCP packet
     * @param flags <code>Buffer.FLAG_XXX</code>
     * @return the number of bytes in <code>buf</code> beginning at <code>off</code> which represent the
     * received RTP or RTCP packet
     * @throws IOException if an I/O error occurs while the method processes the specified RTP or RTCP packet
     */
    int didRead(PushSourceStreamDesc streamDesc, byte[] buf, int off, int len, int flags)
            throws IOException
    {
        Lock lock = _lock.readLock();
        lock.lock();
        try {

            boolean data = streamDesc.data;
            StreamRTPManagerDesc streamRTPManager = streamDesc.connectorDesc.streamRTPManagerDesc;
            Format format = null;

            if (data) {
                // Ignore RTP packets coming from peers whose MediaStream's
                // direction does not allow receiving.
                if (!streamRTPManager.streamRTPManager.getMediaStream().getDirection().allowsReceiving()) {
                    // FIXME We are ignoring RTP packets received from peers who we
                    // do not want to receive from ONLY in the sense that we are not
                    // translating/forwarding them to the other peers. Do not we
                    // want to not receive them locally as well?
                    return len;
                }

                // We flag an RTP packet with Buffer.FLAG_SILENCE when we want to
                // ignore its payload. Because the payload may have skipped
                // decryption as a result of the flag, it is unwise to translate/forward it.
                if ((flags & Buffer.FLAG_SILENCE) == Buffer.FLAG_SILENCE)
                    return len;

                // Do the bytes in the specified buffer resemble (a header of) an
                // RTP packet?
                if ((len >= RTPHeader.SIZE)
                        && (/* v */((buf[off] & 0xc0) >>> 6) == RTPHeader.VERSION)) {
                    int ssrc = RTPUtils.readInt(buf, off + 8);

                    if (!streamRTPManager.containsReceiveSSRC(ssrc)) {
                        if (findStreamRTPManagerDescByReceiveSSRC(ssrc, streamRTPManager) == null) {
                            streamRTPManager.addReceiveSSRC(ssrc);
                        }
                        else {
                            return 0;
                        }
                    }
                    int pt = buf[off + 1] & 0x7f;
                    format = streamRTPManager.getFormat(pt);

                    // Pass the packet to the feedback message sender to give it
                    // a chance to inspect the received packet and decide whether
                    // or not it should keep asking for a key frame or stop.
                    rtcpFeedbackMessageSender.maybeStopRequesting(
                            streamRTPManager, ssrc & 0xffff_ffffL, buf, off, len);
                }
            }
            else if (TimberLog.isTraceEnable) {
                logRTCP(this, "read", buf, off, len);
            }

            OutputDataStreamImpl outputStream = data
                    ? connector.getDataOutputStream() : connector.getControlOutputStream();

            if (outputStream != null) {
                outputStream.write(buf, off, len, format, streamRTPManager);
            }

        } finally {
            lock.unlock();
        }
        return len;
    }

    /**
     * Releases the resources allocated by this instance in the course of its execution and
     * prepares it to be garbage collected.
     */
    @Override
    public void dispose()
    {
        Lock lock = _lock.writeLock();
        lock.lock();
        try {
            rtcpFeedbackMessageSender.dispose();
            manager.removeReceiveStreamListener(this);
            try {
                manager.dispose();
            } catch (Throwable t) {
                if (t instanceof ThreadDeath) {
                    throw (ThreadDeath) t;
                }
                else {
                    // RTPManager.dispose() often throws at least a NullPointerException in relation to some RTP BYE.
                    Timber.e(t, "Failed to dispose of RTPManager");
                }
            }

        } finally {
            lock.unlock();
        }
    }

    /**
     * Releases the resources allocated by this instance for the purposes of the functioning of a
     * specific <code>StreamRTPManager</code> in the course of its execution and prepares that
     * <code>StreamRTPManager</code> to be garbage collected (as far as this <code>RTPTranslatorImpl</code> is concerned).
     */
    public void dispose(StreamRTPManager streamRTPManager)
    {
        // XXX Here we could potentially start with a read lock and upgrade to
        // a write lock, if the streamRTPManager is in the streamRTPManagers
        // collection. Not sure about the up/down grading performance hit though.
        Lock lock = _lock.writeLock();

        lock.lock();
        try {
            Iterator<StreamRTPManagerDesc> streamRTPManagerIter = streamRTPManagers.iterator();
            while (streamRTPManagerIter.hasNext()) {
                StreamRTPManagerDesc streamRTPManagerDesc = streamRTPManagerIter.next();

                if (streamRTPManagerDesc.streamRTPManager == streamRTPManager) {
                    RTPConnectorDesc connectorDesc = streamRTPManagerDesc.connectorDesc;

                    if (connectorDesc != null) {
                        if (this.connector != null)
                            this.connector.removeConnector(connectorDesc);
                        connectorDesc.connector.close();
                        streamRTPManagerDesc.connectorDesc = null;
                    }

                    streamRTPManagerIter.remove();
                    break;
                }
            }

        } finally {
            lock.unlock();
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public StreamRTPManager findStreamRTPManagerByReceiveSSRC(int receiveSSRC)
    {
        StreamRTPManagerDesc desc = findStreamRTPManagerDescByReceiveSSRC(receiveSSRC, null);
        return (desc == null) ? null : desc.streamRTPManager;
    }

    /**
     * Finds the first <code>StreamRTPManager</code> which is related to a specific receive/remote SSRC.
     *
     * @param receiveSSRC the receive/remote SSRC to which the returned <code>StreamRTPManager</code> is to be related
     * @param exclusion the <code>StreamRTPManager</code>, if any, to be excluded from the search
     * @return the first <code>StreamRTPManager</code> which is related to the specified <code>receiveSSRC</code>
     */
    private StreamRTPManagerDesc findStreamRTPManagerDescByReceiveSSRC(int receiveSSRC, StreamRTPManagerDesc exclusion)
    {
        Lock lock = _lock.readLock();
        StreamRTPManagerDesc ret = null;

        lock.lock();
        try {

            for (int i = 0, count = streamRTPManagers.size(); i < count; i++) {
                StreamRTPManagerDesc s = streamRTPManagers.get(i);
                if ((s != exclusion) && s.containsReceiveSSRC(receiveSSRC)) {
                    ret = s;
                    break;
                }
            }
        } finally {
            lock.unlock();
        }
        return ret;
    }

    /**
     * Exposes {@link RTPManager#getControl(String)} on the internal/underlying <code>RTPManager</code>.
     *
     * @param streamRTPManager ignored
     * @param controlType
     * @return the return value of the invocation of <code>RTPManager.getControl(String)</code> on the
     * internal/underlying <code>RTPManager</code>
     */
    public Object getControl(StreamRTPManager streamRTPManager, String controlType)
    {
        return manager.getControl(controlType);
    }

    /**
     * Exposes {@link RTPManager#getGlobalReceptionStats()} on the internal/underlying <code>RTPManager</code>.
     *
     * @param streamRTPManager ignored
     * @return the return value of the invocation of <code>RTPManager.getGlobalReceptionStats()</code>
     * on the internal/underlying <code>RTPManager</code>
     */
    public GlobalReceptionStats getGlobalReceptionStats(StreamRTPManager streamRTPManager)
    {
        return manager.getGlobalReceptionStats();
    }

    /**
     * Exposes {@link RTPManager#getGlobalTransmissionStats()} on the internal/underlying <code>RTPManager</code>.
     *
     * @param streamRTPManager ignored
     * @return the return value of the invocation of
     * <code>RTPManager.getGlobalTransmissionStats()</code> on the internal/underlying <code>RTPManager</code>
     */
    public GlobalTransmissionStats getGlobalTransmissionStats(StreamRTPManager streamRTPManager)
    {
        return manager.getGlobalTransmissionStats();
    }

    /**
     * Exposes {@link RTPSessionMgr#getLocalSSRC()} on the internal/underlying <code>RTPSessionMgr</code>.
     *
     * @param streamRTPManager ignored
     * @return the return value of the invocation of <code>RTPSessionMgr.getLocalSSRC()</code> on the
     * internal/underlying <code>RTPSessionMgr</code>
     */
    public long getLocalSSRC(StreamRTPManager streamRTPManager)
    {
        // if (streamRTPManager == null)
        // return localSSRC;
        // return ((RTPSessionMgr) manager).getLocalSSRC();

        // XXX(gp) it makes (almost) no sense to use the FMJ SSRC because, at
        // least in the case of jitsi-videobridge, it's not announced to the
        // peers, resulting in Chrome's discarding the RTP/RTCP packets with
        // ((RTPSessionMgr) manager).getLocalSSRC(); as the media sender SSRC.
        // This makes the ((RTPSessionMgr) manager).getLocalSSRC() useless in
        // 95% of the use cases (hence the "almost" in the beginning of this comment).
        return localSSRC;
    }

    /**
     * Gets the <code>ReceiveStream</code>s associated with/related to a neomedia <code>MediaStream</code>
     * (specified in the form of a <code>StreamRTPManager</code> instance for the purposes of and in
     * the terms of <code>RTPManagerImpl</code>).
     *
     * @param streamRTPManager the <code>StreamRTPManager</code> to which the returned <code>ReceiveStream</code>s are to be related
     * @return the <code>ReceiveStream</code>s related to/associated with the specified <code>streamRTPManager</code>
     */
    public Vector<ReceiveStream> getReceiveStreams(StreamRTPManager streamRTPManager)
    {
        Lock lock = _lock.readLock();
        Vector<ReceiveStream> receiveStreams = null;

        lock.lock();
        try {

            StreamRTPManagerDesc streamRTPManagerDesc = getStreamRTPManagerDesc(streamRTPManager, false);

            if (streamRTPManagerDesc != null) {
                Vector<?> managerReceiveStreams = manager.getReceiveStreams();

                if (managerReceiveStreams != null) {
                    receiveStreams = new Vector<>(managerReceiveStreams.size());
                    for (Object s : managerReceiveStreams) {
                        ReceiveStream receiveStream = (ReceiveStream) s;
                        // FMJ stores the synchronization source (SSRC) identifiers
                        // as 32-bit signed values.
                        int receiveSSRC = (int) receiveStream.getSSRC();

                        if (streamRTPManagerDesc.containsReceiveSSRC(receiveSSRC))
                            receiveStreams.add(receiveStream);
                    }
                }
            }
        } finally {
            lock.unlock();
        }
        return receiveStreams;
    }

    /**
     * Gets the <code>RTCPFeedbackMessageSender</code> which should be used for sending RTCP Feedback
     * Messages from this <code>RTPTranslator</code>.
     *
     * @return the <code>RTCPFeedbackMessageSender</code> which should be used for sending RTCP
     * Feedback Messages from this <code>RTPTranslator</code>.
     */
    public RTCPFeedbackMessageSender getRtcpFeedbackMessageSender()
    {
        return rtcpFeedbackMessageSender;
    }

    /**
     * Gets the <code>SendStream</code>s associated with/related to a neomedia <code>MediaStream</code>
     * (specified in the form of a <code>StreamRTPManager</code> instance for the purposes of and in
     * the terms of <code>RTPManagerImpl</code>).
     *
     * @param streamRTPManager the <code>StreamRTPManager</code> to which the returned <code>SendStream</code>s are to be related
     * @return the <code>SendStream</code>s related to/associated with the specified <code>streamRTPManager</code>
     */
    public Vector<SendStream> getSendStreams(StreamRTPManager streamRTPManager)
    {
        Lock lock = _lock.readLock();
        Vector<SendStream> sendStreams = null;

        lock.lock();
        try {
            Vector<?> managerSendStreams = manager.getSendStreams();

            if (managerSendStreams != null) {
                sendStreams = new Vector<>(managerSendStreams.size());
                for (SendStreamDesc sendStreamDesc : this.sendStreams) {
                    if (managerSendStreams.contains(sendStreamDesc.sendStream)) {
                        SendStream sendStream = sendStreamDesc.getSendStream(streamRTPManager, false);
                        if (sendStream != null)
                            sendStreams.add(sendStream);
                    }
                }
            }
        } finally {
            lock.unlock();
        }
        return sendStreams;
    }

    private StreamRTPManagerDesc getStreamRTPManagerDesc(StreamRTPManager streamRTPManager, boolean create)
    {
        Lock lock = create ? _lock.writeLock() : _lock.readLock();
        StreamRTPManagerDesc ret = null;

        lock.lock();
        try {
            for (StreamRTPManagerDesc s : streamRTPManagers) {
                if (s.streamRTPManager == streamRTPManager) {
                    ret = s;
                    break;
                }
            }

            if (ret == null && create) {
                ret = new StreamRTPManagerDesc(streamRTPManager);
                streamRTPManagers.add(ret);
            }
        } finally {
            lock.unlock();
        }
        return ret;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public List<StreamRTPManager> getStreamRTPManagers()
    {
        List<StreamRTPManager> ret = new ArrayList<>(streamRTPManagers.size());
        for (StreamRTPManagerDesc streamRTPManagerDesc : streamRTPManagers) {
            ret.add(streamRTPManagerDesc.streamRTPManager);
        }
        return ret;
    }

    public void initialize(StreamRTPManager streamRTPManager, RTPConnector connector)
    {
        Lock w = _lock.writeLock();
        Lock r = _lock.readLock();
        Lock lock; // the lock which is to eventually be unlocked

        w.lock();
        lock = w;
        try {
            if (this.connector == null) {
                this.connector = new RTPConnectorImpl(this);
                manager.initialize(this.connector);
            }

            StreamRTPManagerDesc streamRTPManagerDesc = getStreamRTPManagerDesc(streamRTPManager, true);

            // We got the connector and the streamRTPManagerDesc. We can now
            // downgrade the lock on this translator.
            r.lock();
            w.unlock();
            lock = r;

            // We're managing access to the streamRTPManagerDesc.
            synchronized (streamRTPManagerDesc) {
                RTPConnectorDesc connectorDesc = streamRTPManagerDesc.connectorDesc;

                if (connectorDesc == null || connectorDesc.connector != connector) {
                    if (connectorDesc != null) {
                        // The connector is thread-safe.
                        this.connector.removeConnector(connectorDesc);
                    }

                    streamRTPManagerDesc.connectorDesc = connectorDesc = (connector == null) ? null
                            : new RTPConnectorDesc(streamRTPManagerDesc, connector);
                }
                if (connectorDesc != null) {
                    // The connector is thread-safe.
                    this.connector.addConnector(connectorDesc);
                }
            }
        } finally {
            lock.unlock();
        }
    }

    /**
     * Removes a <code>ReceiveStreamListener</code> to no longer be notified about
     * <code>ReceiveStreamEvent</code>s related to a specific neomedia <code>MediaStream</code> (expressed
     * as a <code>StreamRTPManager</code> for the purposes of and in the terms of <code>RTPTranslator</code>).
     * Since {@link #addReceiveStreamListener(StreamRTPManager, ReceiveStreamListener)} does not
     * add equal <code>ReceiveStreamListener</code>s, a single removal is enough to reverse multiple
     * additions of equal <code>ReceiveStreamListener</code>s.
     *
     * @param streamRTPManager the <code>StreamRTPManager</code> which specifies the neomedia <code>MediaStream</code> with
     * which the <code>ReceiveStreamEvent</code>s delivered to the specified <code>listener</code> are to be related
     * @param listener the <code>ReceiveStreamListener</code> to no longer be notified about
     * <code>ReceiveStreamEvent</code>s related to the specified <code>streamRTPManager</code>
     */
    public void removeReceiveStreamListener(StreamRTPManager streamRTPManager, ReceiveStreamListener listener)
    {
        StreamRTPManagerDesc desc = getStreamRTPManagerDesc(streamRTPManager, false);

        if (desc != null)
            desc.removeReceiveStreamListener(listener);
    }

    /**
     * Removes a <code>RemoteListener</code> to no longer be notified about <code>RemoteEvent</code>s
     * received by this <code>RTPTranslatorImpl</code>. Though the request is being made by a specific
     * <code>StreamRTPManager</code>, the addition of the specified <code>listener</code> and the deliveries
     * of the <code>RemoteEvent</code>s are performed irrespective of any <code>StreamRTPManager</code> so
     * the removal follows the same logic.
     *
     * @param streamRTPManager the <code>StreamRTPManager</code> which is requesting the removal of the specified
     * <code>RemoteListener</code>
     * @param listener the <code>RemoteListener</code> to no longer be notified about <code>RemoteEvent</code>s
     * received by this <code>RTPTranslatorImpl</code>
     */
    public void removeRemoteListener(StreamRTPManager streamRTPManager, RemoteListener listener)
    {
        manager.removeRemoteListener(listener);
    }

    /**
     * Not implemented because there are currently no uses of the underlying functionality.
     * (Additionally, {@link #addSendStreamListener(StreamRTPManager, SendStreamListener)} is not
     * implemented for the same reason.)
     */
    public void removeSendStreamListener(StreamRTPManager streamRTPManager, SendStreamListener listener)
    {
        // TODO Auto-generated method stub
    }

    /**
     * Not implemented because there are currently no uses of the underlying functionality.
     * (Additionally, {@link #addSessionListener(StreamRTPManager, SessionListener)} is not
     * implemented for the same reason.)
     */
    public void removeSessionListener(StreamRTPManager streamRTPManager, SessionListener listener)
    {
        // TODO Auto-generated method stub
    }

    /**
     * Sets the local SSRC for this <code>RTPTranslatorImpl</code>.
     *
     * @param localSSRC the SSRC to set.
     */
    public void setLocalSSRC(long localSSRC)
    {
        this.localSSRC = localSSRC;
    }

    /**
     * Sets the <code>SSRCFactory</code> which is to generate new synchronization source (SSRC)
     * identifiers.
     *
     * @param ssrcFactory the <code>SSRCFactory</code> which is to generate new synchronization source (SSRC)
     * identifiers or <code>null</code> if this <code>MediaStream</code> is to employ internal logic
     * to generate new synchronization source (SSRC) identifiers
     */
    public void setSSRCFactory(SSRCFactory ssrcFactory)
    {
        RTPManager manager = this.manager;
        if (manager instanceof RTPSessionMgr) {
            ((RTPSessionMgr) manager).setSSRCFactory(ssrcFactory);
        }
    }

    /**
     * Notifies this <code>ReceiveStreamListener</code> about a specific event related to a
     * <code>ReceiveStream</code>.
     *
     * @param event a <code>ReceiveStreamEvent</code> which contains the specifics of the event this
     * <code>ReceiveStreamListener</code> is being notified about
     * @see ReceiveStreamListener#update(ReceiveStreamEvent)
     */
    @Override
    public void update(ReceiveStreamEvent event)
    {
        /*
         * Because NullPointerException was seen during testing, be thorough with the null checks.
         */
        if (event != null) {
            ReceiveStream receiveStream = event.getReceiveStream();

            if (receiveStream != null) {
                /*
                 * FMJ stores the synchronization source (SSRC) identifiers as 32-bit signed
                 * values.
                 */
                int receiveSSRC = (int) receiveStream.getSSRC();
                StreamRTPManagerDesc streamRTPManagerDesc
                        = findStreamRTPManagerDescByReceiveSSRC(receiveSSRC, null);

                if (streamRTPManagerDesc != null) {
                    for (ReceiveStreamListener listener
                            : streamRTPManagerDesc.getReceiveStreamListeners()) {
                        listener.update(event);
                    }
                }
            }
        }
    }

    /**
     * Notifies this <code>RTPTranslator</code> that a <code>buffer</code> from a <code>source</code> will be
     * written into a <code>destination</code>.
     *
     * @param source the source of <code>buffer</code>
     * @param pkt the packet from <code>source</code> which is to be written into <code>destination</code>
     * @param destination the destination into which <code>buffer</code> is to be written
     * @param data <code>true</code> for data/RTP or <code>false</code> for control/RTCP
     * @return <code>true</code> if the writing is to continue or <code>false</code> if the writing is to
     * abort
     */
    boolean willWrite(StreamRTPManagerDesc source, RawPacket pkt,
            StreamRTPManagerDesc destination, boolean data)
    {
        MediaStream src = (source == null) ? null : source.streamRTPManager.getMediaStream();
        MediaStream dst = destination.streamRTPManager.getMediaStream();

        return willWrite(src, pkt, dst, data);
    }

    /**
     * Writes an <code>RTCPFeedbackMessage</code> into a destination identified by a specific
     * <code>MediaStream</code>.
     *
     * @param controlPayload
     * @param destination
     * @return <code>true</code> if the <code>controlPayload</code> was written into the
     * <code>destination</code>; otherwise, <code>false</code>
     */
    public boolean writeControlPayload(Payload controlPayload, MediaStream destination)
    {
        RTPConnectorImpl connector = this.connector;
        return (connector != null) && connector.writeControlPayload(controlPayload, destination);
    }

    /**
     * Provides access to the underlying <code>SSRCCache</code> that holds statistics information about
     * each SSRC that we receive.
     *
     * @return the underlying <code>SSRCCache</code> that holds statistics information about each SSRC
     * that we receive.
     */
    @Override
    public SSRCCache getSSRCCache()
    {
        return ((RTPSessionMgr) manager).getSSRCCache();
    }
}
