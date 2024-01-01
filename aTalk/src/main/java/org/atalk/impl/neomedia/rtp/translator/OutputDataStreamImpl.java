/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.atalk.impl.neomedia.rtp.translator;

import net.sf.fmj.media.rtp.RTCPHeader;
import net.sf.fmj.media.rtp.RTPHeader;

import org.atalk.impl.timberlog.TimberLog;
import org.atalk.impl.neomedia.RTPConnectorOutputStream;
import org.atalk.service.libjitsi.LibJitsi;
import org.atalk.service.neomedia.MediaStream;
import org.atalk.service.neomedia.RawPacket;
import org.atalk.util.ConfigUtils;
import org.atalk.util.RTPUtils;
import org.ice4j.util.QueueStatistics;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;

import javax.media.Format;
import javax.media.rtp.OutputDataStream;

import timber.log.Timber;

/**
 * Implements <code>OutputDataStream</code> for an <code>RTPTranslatorImpl</code>. The packets written into
 * <code>OutputDataStreamImpl</code> are copied into multiple endpoint <code>OutputDataStream</code>s.
 *
 * @author Lyubomir Marinov
 * @author Maryam Daneshi
 * @author George Politis
 * @author Boris Grozev
 * @author Eng Chong Meng
 */
class OutputDataStreamImpl implements OutputDataStream, Runnable
{
    /**
     * The name of the <code>boolean</code> <code>ConfigurationService</code> property which indicates
     * whether the RTP header extension(s) are to be removed from received RTP packets prior to
     * relaying them. The default value is <code>false</code>.
     */
    private static final String REMOVE_RTP_HEADER_EXTENSIONS_PNAME
            = RTPTranslatorImpl.class.getName() + ".removeRTPHeaderExtensions";

    private static final int WRITE_Q_CAPACITY = RTPConnectorOutputStream.PACKET_QUEUE_CAPACITY;

    private boolean closed;

    private final RTPConnectorImpl connector;

    /**
     * The indicator which determines whether RTP data ({@code true}) is written into this
     * {@code OutputDataStreamImpl} or RTP control i.e. RTCP ({@code false}).
     */
    private final boolean _data;

    /**
     * The indicator which determines whether the RTP header extension(s) are to be removed from
     * received RTP packets prior to relaying them. The default value is <code>false</code>.
     */
    private final boolean _removeRTPHeaderExtensions;

    /**
     * The {@code List} of {@code OutputDataStream}s into which this {@code OutputDataStream}
     * copies written data/packets. Implemented as a copy-on-write storage in order to reduce
     * synchronized blocks and deadlock risks. I do NOT want to use {@code CopyOnWriteArrayList}
     * because I want to
     * (1) avoid {@code Iterator}s and
     * (2) reduce synchronization. The access to
     * {@link #_streams} is synchronized by {@link #_streamsSyncRoot}.
     */
    private List<OutputDataStreamDesc> _streams = Collections.emptyList();

    /**
     * The {@code Object} which synchronizes the access to {@link #_streams}.
     */
    private final Object _streamsSyncRoot = new Object();

    private final RTPTranslatorBuffer[] writeQ = new RTPTranslatorBuffer[WRITE_Q_CAPACITY];

    private int writeQHead;

    private int writeQLength;

    private final QueueStatistics writeQStats;

    /**
     * The number of packets dropped because a packet was inserted while {@link #writeQ} was full.
     */
    private int numDroppedPackets = 0;

    private Thread writeThread;

    public OutputDataStreamImpl(RTPConnectorImpl connector, boolean data)
    {
        this.connector = connector;
        _data = data;

        _removeRTPHeaderExtensions = ConfigUtils.getBoolean(
                LibJitsi.getConfigurationService(),
                REMOVE_RTP_HEADER_EXTENSIONS_PNAME,
                false);

        if (TimberLog.isTraceEnable) {
            // writeQStats = QueueStatistics.get(getClass().getSimpleName()); // ice4j 2.0
            writeQStats = new QueueStatistics(getClass().getSimpleName() + "-" + hashCode());
        }
        else {
            writeQStats = null;
        }
    }

    /**
     * Adds a new {@code OutputDataStream} to the list of {@code OutputDataStream}s into which this
     * {@code OutputDataStream} copies written data/packets. If this instance contains the
     * specified {@code stream} already, does nothing.
     *
     * @param connectorDesc the endpoint {@code RTPConnector} which owns {@code stream}
     * @param stream the {@code OutputDataStream} to add to this instance
     */
    public void addStream(RTPConnectorDesc connectorDesc, OutputDataStream stream)
    {
        synchronized (_streamsSyncRoot) {
            // Prevent repetitions.
            for (OutputDataStreamDesc streamDesc : _streams) {
                if (streamDesc.connectorDesc == connectorDesc && streamDesc.stream == stream) {
                    return;
                }
            }

            // Add. Copy on write.
            List<OutputDataStreamDesc> newStreams = new ArrayList<>(_streams.size() * 3 / 2 + 1);
            newStreams.addAll(_streams);
            newStreams.add(new OutputDataStreamDesc(connectorDesc, stream));
            _streams = newStreams;
        }
    }

    public synchronized void close()
    {
        closed = true;
        writeThread = null;
        notify();
    }

    private synchronized void createWriteThread()
    {
        writeThread = new Thread(this, getClass().getName());
        writeThread.setDaemon(true);
        writeThread.start();
    }

    private int doWrite(byte[] buf, int off, int len, Format format, StreamRTPManagerDesc exclusion)
    {
        RTPTranslatorImpl translator = getTranslator();
        if (translator == null)
            return 0;

        // XXX The field _streams is explicitly implemented as a copy-on-write
        // storage in order to avoid synchronization and, especially, here where
        // I'm to invoke writes on multiple other OutputDataStreams.
        List<OutputDataStreamDesc> streams = _streams;
        boolean removeRTPHeaderExtensions = _removeRTPHeaderExtensions;
        int written = 0;

        // XXX I do NOT want to use an Iterator.
        for (int i = 0, end = streams.size(); i < end; ++i) {
            OutputDataStreamDesc s = streams.get(i);
            StreamRTPManagerDesc streamRTPManager = s.connectorDesc.streamRTPManagerDesc;

            if (streamRTPManager == exclusion)
                continue;

            boolean write;

            if (_data) {
                // TODO The removal of the RTP header extensions is an
                // experiment inspired by https://code.google.com/p/webrtc/issues/detail?id=1095
                // "Chrom WebRTC VP8 RTP packet retransmission does not follow RFC 4588"
                if (removeRTPHeaderExtensions) {
                    removeRTPHeaderExtensions = false;
                    len = removeRTPHeaderExtensions(buf, off, len);
                }
                write = willWriteData(streamRTPManager, buf, off, len, format, exclusion);
            }
            else {
                write = willWriteControl(streamRTPManager, buf, off, len, format, exclusion);
            }

            if (write) {
                // Allow the RTPTranslatorImpl a final chance to filter out the
                // packet on a source-destination basis.
                write = translator.willWrite(exclusion, new RawPacket(buf, off, len), streamRTPManager, _data);
            }

            if (write) {
                int w = s.stream.write(buf, off, len);

                if (written < w)
                    written = w;
            }
        }
        return written;
    }

    private RTPTranslatorImpl getTranslator()
    {
        return connector.translator;
    }

    /**
     * Removes the RTP header extension(s) from an RTP packet.
     *
     * @param buf the <code>byte</code>s of a datagram packet which may contain an RTP packet
     * @param off the offset in <code>buf</code> at which the actual data in <code>buf</code> starts
     * @param len the number of <code>byte</code>s in <code>buf</code> starting at <code>off</code> comprising the actual data
     * @return the number of <code>byte</code>s in <code>buf</code> starting at <code>off</code> comprising the
     * actual data after the possible removal of the RTP header extension(s)
     */
    private static int removeRTPHeaderExtensions(byte[] buf, int off, int len)
    {
        // Do the bytes in the specified buffer resemble (the header of) an RTP packet?
        if (len >= RTPHeader.SIZE) {
            byte b0 = buf[off];
            int v = (b0 & 0xC0) >>> 6; /* version */

            if (v == RTPHeader.VERSION) {
                boolean x = (b0 & 0x10) == 0x10; /* extension */

                if (x) {
                    int cc = b0 & 0x0F; /* CSRC count */
                    int xBegin = off + RTPHeader.SIZE + 4 * cc;
                    int xLen = 2 /* defined by profile */ + 2 /* length */;
                    int end = off + len;

                    if (xBegin + xLen < end) {
                        xLen += RTPUtils.readUint16AsInt(buf, xBegin + 2 /* defined by profile */) * 4;

                        int xEnd = xBegin + xLen;
                        if (xEnd <= end) {
                            // Remove the RTP header extension bytes.
                            for (int src = xEnd, dst = xBegin; src < end; )
                                buf[dst++] = buf[src++];
                            len -= xLen;
                            // Switch off the extension bit.
                            buf[off] = (byte) (b0 & 0xEF);
                        }
                    }
                }
            }
        }
        return len;
    }

    /**
     * Removes the {@code OutputDataStream}s owned by a specific {@code RTPConnector} from the list
     * of {@code OutputDataStream}s into which this {@code OutputDataStream} copies written data/packets.
     *
     * @param connectorDesc the {@code RTPConnector} that is the owner of the {@code OutputDataStream}s to remove
     * from this instance.
     */
    public void removeStreams(RTPConnectorDesc connectorDesc)
    {
        synchronized (_streamsSyncRoot) {
            // Copy on write. Well, we aren't sure yet whether a write is going
            // to happen but it's the caller's fault if they ask this instance
            // to remove an RTPConnector which this instance doesn't contain.
            List<OutputDataStreamDesc> newStreams = new ArrayList<>(_streams);

            for (Iterator<OutputDataStreamDesc> i = newStreams.iterator(); i.hasNext(); ) {
                if (i.next().connectorDesc == connectorDesc)
                    i.remove();
            }
            _streams = newStreams;
        }
    }

    @Override
    public void run()
    {
        try {
            do {
                int writeIndex;
                byte[] buffer;
                StreamRTPManagerDesc exclusion;
                Format format;
                int length;

                synchronized (this) {
                    if (closed || !Thread.currentThread().equals(writeThread))
                        break;
                    if (writeQLength < 1) {
                        boolean interrupted = false;

                        try {
                            wait();
                        } catch (InterruptedException ie) {
                            interrupted = true;
                        }
                        if (interrupted)
                            Thread.currentThread().interrupt();
                        continue;
                    }

                    writeIndex = writeQHead;
                    RTPTranslatorBuffer write = writeQ[writeIndex];

                    buffer = write.data;
                    write.data = null;
                    exclusion = write.exclusion;
                    write.exclusion = null;
                    format = write.format;
                    write.format = null;
                    length = write.length;
                    write.length = 0;

                    writeQHead++;
                    if (writeQHead >= writeQ.length)
                        writeQHead = 0;
                    writeQLength--;
                    if (writeQStats != null) {
                        writeQStats.remove(System.currentTimeMillis());
                    }
                }

                try {
                    doWrite(buffer, 0, length, format, exclusion);
                } finally {
                    synchronized (this) {
                        RTPTranslatorBuffer write = writeQ[writeIndex];

                        if (write != null && write.data == null)
                            write.data = buffer;
                    }
                }
            } while (true);
        } catch (Throwable t) {
            Timber.e(t, "Failed to translate RTP packet");
            if (t instanceof ThreadDeath)
                throw (ThreadDeath) t;
        } finally {
            synchronized (this) {
                if (Thread.currentThread().equals(writeThread))
                    writeThread = null;
                if (!closed && writeThread == null && writeQLength > 0)
                    createWriteThread();
            }
        }
    }

    /**
     * Notifies this instance that a specific <code>byte</code> buffer will be written into the control
     * <code>OutputDataStream</code> of a specific <code>StreamRTPManagerDesc</code>.
     *
     * @param destination the <code>StreamRTPManagerDesc</code> which is the destination of the write
     * @param buffer the data to be written into <code>destination</code>
     * @param offset the offset in <code>buffer</code> at which the data to be written into <code>destination</code> starts
     * @param length the number of <code>byte</code>s in <code>buffer</code> beginning at <code>offset</code> which
     * constitute the data to the written into <code>destination</code>
     * @param format the FMJ <code>Format</code> of the data to be written into <code>destination</code>
     * @param exclusion the <code>StreamRTPManagerDesc</code> which is exclude from the write batch, possibly
     * because it is the cause of the write batch in the first place
     * @return <code>true</code> to write the specified data into the specified <code>destination</code> or
     * <code>false</code> to not write the specified data into the specified <code>destination</code>
     */
    private boolean willWriteControl(StreamRTPManagerDesc destination, byte[] buffer, int offset,
            int length, Format format, StreamRTPManagerDesc exclusion)
    {
        boolean write = true;

        // Do the bytes in the specified buffer resemble (the header of) an RTCP packet?
        if (length >= 12 /* FB */) {
            byte b0 = buffer[offset];
            int v = (b0 & 0xc0) >>> 6; /* version */

            if (v == RTCPHeader.VERSION) {
                byte b1 = buffer[offset + 1];
                int pt = b1 & 0xff; /* payload type */
                int fmt = b0 & 0x1f; /* feedback message type */

                if ((pt == 205 /* RTPFB */) || (pt == 206 /* PSFB */)) {
                    // Verify the length field.
                    int rtcpLength = (RTPUtils.readUint16AsInt(buffer, offset + 2) + 1) * 4;

                    if (rtcpLength <= length) {
                        int ssrcOfMediaSource = 0;
                        if (pt == 206 && fmt == 4) // FIR
                        {
                            if (rtcpLength < 20) {
                                // FIR messages are at least 20 bytes long
                                write = false;
                            }
                            else {
                                // FIR messages don't have a valid 'media source' field, use the
                                // SSRC from the first FCI entry instead
                                ssrcOfMediaSource = RTPUtils.readInt(buffer, offset + 12);
                            }
                        }
                        else {
                            ssrcOfMediaSource = RTPUtils.readInt(buffer, offset + 8);
                        }

                        if (destination.containsReceiveSSRC(ssrcOfMediaSource)) {
                            if (TimberLog.isTraceEnable) {
                                int ssrcOfPacketSender = RTPUtils.readInt(buffer, offset + 4);
                                String message = getClass().getName() + ".willWriteControl: FMT "
                                        + fmt + ", PT " + pt + ", SSRC of packet sender "
                                        + (ssrcOfPacketSender & 0xffffffffL)
                                        + ", SSRC of media source "
                                        + (ssrcOfMediaSource & 0xffffffffL);

                                Timber.log(TimberLog.FINER, "%s", message);
                            }
                        }
                        else {
                            write = false;
                        }
                    }
                }
            }
        }

        if (write && TimberLog.isTraceEnable)
            RTPTranslatorImpl.logRTCP(this, "doWrite", buffer, offset, length);
        return write;
    }

    /**
     * Notifies this instance that a specific <code>byte</code> buffer will be written into the data
     * <code>OutputDataStream</code> of a specific <code>StreamRTPManagerDesc</code>.
     *
     * @param destination the <code>StreamRTPManagerDesc</code> which is the destination of the write
     * @param buf the data to be written into <code>destination</code>
     * @param off the offset in <code>buf</code> at which the data to be written into <code>destination</code> starts
     * @param len the number of <code>byte</code>s in <code>buf</code> beginning at <code>off</code> which
     * constitute the data to the written into <code>destination</code>
     * @param format the FMJ <code>Format</code> of the data to be written into <code>destination</code>
     * @param exclusion the <code>StreamRTPManagerDesc</code> which is exclude from the write batch, possibly
     * because it is the cause of the write batch in the first place
     * @return <code>true</code> to write the specified data into the specified <code>destination</code> or
     * <code>false</code> to not write the specified data into the specified <code>destination</code>
     */
    private boolean willWriteData(StreamRTPManagerDesc destination, byte[] buf, int off, int len,
            Format format, StreamRTPManagerDesc exclusion)
    {
        // Only write data packets to OutputDataStreams for which the associated MediaStream allows sending.
        if (!destination.streamRTPManager.getMediaStream().getDirection().allowsSending()) {
            return false;
        }

        if (format != null && len > 0) {
            Integer pt = destination.getPayloadType(format);

            if (pt == null && exclusion != null) {
                pt = exclusion.getPayloadType(format);
            }
            if (pt != null) {
                int ptByteIndex = off + 1;

                buf[ptByteIndex] = (byte) ((buf[ptByteIndex] & 0x80) | (pt & 0x7f));
            }
        }
        return true;
    }

    @Override
    public int write(byte[] buf, int off, int len)
    {
        // FIXME It's unclear at the time of this writing why the method doWrite
        // is being invoked here and not the overloaded method write.
        return doWrite(buf, off, len, /* format */null, /* exclusion */null);
    }

    public synchronized void write(byte[] buf, int off, int len, Format format,
            StreamRTPManagerDesc exclusion)
    {
        if (closed)
            return;

        int writeIndex;

        if (writeQLength < writeQ.length) {
            writeIndex = (writeQHead + writeQLength) % writeQ.length;
        }
        else {
            writeIndex = writeQHead;
            writeQHead++;
            if (writeQHead >= writeQ.length)
                writeQHead = 0;
            writeQLength--;
            if (writeQStats != null) {
                writeQStats.remove(System.currentTimeMillis());
            }

            numDroppedPackets++;
            if (RTPConnectorOutputStream.logDroppedPacket(numDroppedPackets)) {
                Timber.w("Dropped %s packets hashCode = %s", numDroppedPackets, hashCode());
            }
        }

        RTPTranslatorBuffer write = writeQ[writeIndex];
        if (write == null)
            writeQ[writeIndex] = write = new RTPTranslatorBuffer();

        byte[] data = write.data;

        if (data == null || data.length < len)
            write.data = data = new byte[len];
        System.arraycopy(buf, off, data, 0, len);

        write.exclusion = exclusion;
        write.format = format;
        write.length = len;

        writeQLength++;
        if (writeQStats != null) {
            writeQStats.add(System.currentTimeMillis());
        }

        if (writeThread == null)
            createWriteThread();
        else
            notify();
    }

    /**
     * Writes an <code>RTCPFeedbackMessage</code> into a destination identified by specific <code>MediaStream</code>.
     *
     * @param controlPayload
     * @param destination
     * @return <code>true</code> if the <code>controlPayload</code> was written
     * into the <code>destination</code>; otherwise, <code>false</code>
     */
    boolean writeControlPayload(Payload controlPayload, MediaStream destination)
    {
        // XXX The field _streams is explicitly implemented as a copy-on-write
        // storage in order to avoid synchronization.
        List<OutputDataStreamDesc> streams = _streams;

        // XXX I do NOT want to use an Iterator.
        for (int i = 0, end = streams.size(); i < end; ++i) {
            OutputDataStreamDesc s = streams.get(i);

            if (destination == s.connectorDesc.streamRTPManagerDesc.streamRTPManager.getMediaStream()) {
                controlPayload.writeTo(s.stream);
                return true;
            }
        }
        return false;
    }
}
