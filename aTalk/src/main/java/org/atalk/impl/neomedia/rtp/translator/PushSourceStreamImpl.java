/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.atalk.impl.neomedia.rtp.translator;

import org.atalk.impl.timberlog.TimberLog;
import org.atalk.impl.neomedia.RTPConnectorOutputStream;
import org.ice4j.util.QueueStatistics;

import java.io.IOException;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

import javax.media.Buffer;
import javax.media.protocol.ContentDescriptor;
import javax.media.protocol.PushBufferStream;
import javax.media.protocol.PushSourceStream;
import javax.media.protocol.SourceTransferHandler;

import timber.log.Timber;

/**
 * Implements <code>PushSourceStream</code> for an <code>RTPTranslatorImpl</code>. Reads packets from
 * endpoint <code>PushSourceStream</code>s and pushes them to an <code>RTPTranslatorImpl</code> to be
 * translated.
 *
 * @author Lyubomir Marinov
 * @author Eng Chong Meng
 */
class PushSourceStreamImpl implements PushSourceStream, Runnable, SourceTransferHandler
{
    /**
     * The indicator which determines whether {@link #close()} has been invoked on this instance.
     */
    private boolean closed = false;

    private final RTPConnectorImpl connector;

    private final boolean data;

    /**
     * The indicator which determines whether {@link #read(byte[], int, int)} read a
     * <code>SourcePacket</code> from {@link #readQ} after a <code>SourcePacket</code> was written there.
     */
    private boolean _read = false;

    /**
     * The <code>Queue</code> of <code>SourcePacket</code>s to be read out of this instance via
     * {@link #read(byte[], int, int)}.
     */
    private final Queue<SourcePacket> readQ;

    /**
     * The capacity of {@link #readQ}.
     */
    private final int readQCapacity;

    private final QueueStatistics readQStats;

    /**
     * The number of packets dropped because a packet was inserted while
     * {@link #readQ} was full.
     */
    private int numDroppedPackets = 0;

    /**
     * The pool of <code>SourcePacket</code> instances to reduce their allocations and garbage collection.
     */
    private final Queue<SourcePacket> sourcePacketPool
            = new LinkedBlockingQueue<>(RTPConnectorOutputStream.POOL_CAPACITY);

    private final List<PushSourceStreamDesc> streams = new LinkedList<>();

    /**
     * The <code>Thread</code> which invokes
     * {@link SourceTransferHandler#transferData(PushSourceStream)} on {@link #_transferHandler}.
     */
    private Thread transferDataThread;

    private SourceTransferHandler _transferHandler;

    public PushSourceStreamImpl(RTPConnectorImpl connector, boolean data)
    {
        this.connector = connector;
        this.data = data;

        readQCapacity = RTPConnectorOutputStream.PACKET_QUEUE_CAPACITY;
        readQ = new ArrayBlockingQueue<>(readQCapacity);
        if (TimberLog.isTraceEnable) {
            // readQStats = QueueStatistics.get(getClass().getSimpleName()); // ice4j 2.0
            readQStats = new QueueStatistics(getClass().getSimpleName() + "-" + hashCode());
        }
        else {
            readQStats = null;
        }

        transferDataThread = new Thread(this, getClass().getName());
        transferDataThread.setDaemon(true);
        transferDataThread.start();
    }

    public synchronized void addStream(RTPConnectorDesc connectorDesc, PushSourceStream stream)
    {
        for (PushSourceStreamDesc streamDesc : streams) {
            if ((streamDesc.connectorDesc == connectorDesc) && (streamDesc.stream == stream)) {
                return;
            }
        }
        streams.add(new PushSourceStreamDesc(connectorDesc, stream, this.data));
        stream.setTransferHandler(this);
    }

    public void close()
    {
        closed = true;
        sourcePacketPool.clear();
    }

    /**
     * Not implemented because there are currently no uses of the underlying functionality.
     */
    @Override
    public boolean endOfStream()
    {
        // TODO Auto-generated method stub
        return false;
    }

    /**
     * Not implemented because there are currently no uses of the underlying functionality.
     */
    @Override
    public ContentDescriptor getContentDescriptor()
    {
        return null;
    }

    @Override
    public long getContentLength()
    {
        return LENGTH_UNKNOWN;
    }

    /**
     * Not implemented because there are currently no uses of the underlying functionality.
     */
    @Override
    public Object getControl(String controlType)
    {
        return null;
    }

    /**
     * Not implemented because there are currently no uses of the underlying functionality.
     */
    @Override
    public Object[] getControls()
    {
        return null;
    }

    @Override
    public synchronized int getMinimumTransferSize()
    {
        int minimumTransferSize = 0;

        for (PushSourceStreamDesc streamDesc : streams) {
            int streamMinimumTransferSize = streamDesc.stream.getMinimumTransferSize();

            if (minimumTransferSize < streamMinimumTransferSize)
                minimumTransferSize = streamMinimumTransferSize;
        }
        return minimumTransferSize;
    }

    private RTPTranslatorImpl getTranslator()
    {
        return connector.translator;
    }

    @Override
    public int read(byte[] buffer, int offset, int length)
            throws IOException
    {
        if (closed)
            return -1;

        SourcePacket pkt;
        int pktLength;

        synchronized (readQ) {
            pkt = readQ.peek();
            if (pkt == null)
                return 0;

            pktLength = pkt.getLength();
            if (length < pktLength) {
                throw new IOException("Length " + length + " is insufficient. Must be at least " + pktLength + ".");
            }

            readQ.remove();
            if (readQStats != null) {
                readQStats.remove(System.currentTimeMillis());
            }
            _read = true;
            readQ.notifyAll();
        }

        System.arraycopy(pkt.getBuffer(), pkt.getOffset(), buffer, offset, pktLength);

        PushSourceStreamDesc streamDesc = pkt.streamDesc;
        int read = pktLength;
        int flags = pkt.getFlags();

        pkt.streamDesc = null;
        sourcePacketPool.offer(pkt);

        if (read > 0) {
            RTPTranslatorImpl translator = getTranslator();

            if (translator != null) {
                read = translator.didRead(streamDesc, buffer, offset, read, flags);
            }
        }

        return read;
    }

    public synchronized void removeStreams(RTPConnectorDesc connectorDesc)
    {
        Iterator<PushSourceStreamDesc> streamIter = streams.iterator();

        while (streamIter.hasNext()) {
            PushSourceStreamDesc streamDesc = streamIter.next();

            if (streamDesc.connectorDesc == connectorDesc) {
                streamDesc.stream.setTransferHandler(null);
                streamIter.remove();
            }
        }
    }

    /**
     * Runs in {@link #transferDataThread} and invokes
     * {@link SourceTransferHandler#transferData(PushSourceStream)} on {@link #_transferHandler}.
     */
    @Override
    public void run()
    {
        try {
            while (!closed) {
                SourceTransferHandler transferHandler = _transferHandler;

                synchronized (readQ) {
                    if (readQ.isEmpty() || (transferHandler == null)) {
                        try {
                            readQ.wait(100);
                        } catch (InterruptedException ie) {
                        }
                        continue;
                    }
                }

                try {
                    transferHandler.transferData(this);
                } catch (Throwable t) {
                    if (t instanceof ThreadDeath) {
                        throw (ThreadDeath) t;
                    }
                    else {
                        Timber.w(t, "An RTP packet may have not been fully handled.");
                    }
                }
            }
        } finally {
            if (Thread.currentThread().equals(transferDataThread))
                transferDataThread = null;
        }
    }

    @Override
    public synchronized void setTransferHandler(SourceTransferHandler transferHandler)
    {
        if (_transferHandler != transferHandler) {
            _transferHandler = transferHandler;
            for (PushSourceStreamDesc streamDesc : streams)
                streamDesc.stream.setTransferHandler(this);
        }
    }

    /**
     * {@inheritDoc}
     * <p>
     * Implements {@link SourceTransferHandler#transferData(PushSourceStream)}. This instance sets
     * itself as the <code>transferHandler</code> of all <code>PushSourceStream</code>s that get added to it
     * (i.e. {@link #streams}). When either one of these pushes media data, this instance pushes
     * that media data.
     */
    @Override
    public void transferData(PushSourceStream stream)
    {
        if (closed)
            return;

        PushSourceStreamDesc streamDesc = null;
        synchronized (this) {
            for (PushSourceStreamDesc aStreamDesc : streams) {
                if (aStreamDesc.stream == stream) {
                    streamDesc = aStreamDesc;
                    break;
                }
            }
        }
        if (streamDesc == null)
            return;

        int len = stream.getMinimumTransferSize();

        if (len < 1)
            len = 2 * 1024;

        SourcePacket pkt = sourcePacketPool.poll();
        byte[] buf;

        if (pkt == null || (buf = pkt.getBuffer()).length < len) {
            buf = new byte[len];
            pkt = new SourcePacket(buf, 0, 0);
        }
        else {
            len = buf.length;
            pkt.setFlags(0);
            pkt.setLength(0);
            pkt.setOffset(0);
        }

        int read = 0;
        try {
            PushBufferStream streamAsPushBufferStream = streamDesc.streamAsPushBufferStream;

            if (streamAsPushBufferStream == null) {
                read = stream.read(buf, 0, len);
            }
            else {
                streamAsPushBufferStream.read(pkt);
                if (pkt.isDiscard()) {
                    read = 0;
                }
                else {
                    read = pkt.getLength();
                    if ((read < 1) && ((pkt.getFlags() & Buffer.FLAG_EOM) == Buffer.FLAG_EOM)) {
                        read = -1;
                    }
                }
            }
        } catch (IOException ioe) {
            Timber.e(ioe, "Failed to read from an RTP stream!");
        } finally {
            if (read > 0) {
                pkt.setLength(read);
                pkt.streamDesc = streamDesc;

                boolean yield;

                synchronized (readQ) {
                    int readQSize = readQ.size();

                    if (readQSize < 1)
                        yield = false;
                    else
                        yield = (readQSize >= readQCapacity) || (!_read);

                    if (yield)
                        readQ.notifyAll();
                }
                if (yield)
                    Thread.yield();

                synchronized (readQ) {
                    long now = System.currentTimeMillis();
                    if (readQ.size() >= readQCapacity) {
                        readQ.remove();
                        if (readQStats != null) {
                            readQStats.remove(now);
                        }
                        numDroppedPackets++;
                        if (RTPConnectorOutputStream.logDroppedPacket(numDroppedPackets)) {
                            Timber.w("Dropped %s packets hashCode = %s", numDroppedPackets, hashCode());
                        }
                    }

                    if (readQ.offer(pkt)) {
                        if (readQStats != null) {
                            readQStats.add(now);
                        }
                        // TODO It appears that it is better to not yield based
                        // on whether the read method has read after the last write.
                        // this.read = false;
                    }
                    readQ.notifyAll();
                }
            }
            else {
                pkt.streamDesc = null;
                sourcePacketPool.offer(pkt);
            }
        }
    }
}
