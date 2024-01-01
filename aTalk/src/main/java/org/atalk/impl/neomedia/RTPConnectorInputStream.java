/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.atalk.impl.neomedia;

import net.sf.fmj.media.util.MediaThread;

import org.atalk.impl.timberlog.TimberLog;
import org.atalk.impl.neomedia.jmfext.media.protocol.AbstractPushBufferStream;
import org.atalk.impl.neomedia.protocol.PushBufferStreamAdapter;
import org.atalk.service.libjitsi.LibJitsi;
import org.atalk.service.neomedia.RawPacket;
import org.atalk.util.ArrayUtils;
import org.atalk.util.concurrent.MonotonicAtomicLong;
import org.ice4j.socket.DatagramPacketFilter;

import java.io.Closeable;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.SocketTimeoutException;
import java.util.Queue;
import java.util.concurrent.ArrayBlockingQueue;

import javax.media.Buffer;
import javax.media.protocol.ContentDescriptor;
import javax.media.protocol.PushBufferStream;
import javax.media.protocol.PushSourceStream;
import javax.media.protocol.SourceTransferHandler;

import timber.log.Timber;

/**
 * @author Bing SU (nova.su@gmail.com)
 * @author Lyubomir Marinov
 * @author Boris Grozev
 * @author Eng Chong Meng
 */
public abstract class RTPConnectorInputStream<T extends Closeable> implements PushSourceStream, Closeable
{
    /**
     * The value of the property <code>controls</code> of <code>RTPConnectorInputStream</code> when there
     * are no controls. Explicitly defined in order to reduce unnecessary allocations.
     */
    private static final Object[] EMPTY_CONTROLS = new Object[0];

    /**
     * The length in bytes of the buffers of <code>RTPConnectorInputStream</code> receiving packets from the network.
     */
    public static final int PACKET_RECEIVE_BUFFER_LENGTH = 4 * 1024;

    /**
     * The name of the property which controls the size of the receive buffer
     * which {@link RTPConnectorInputStream} will request for the sockets that it uses.
     */
    public static final String SO_RCVBUF_PNAME = RTPConnectorInputStream.class.getName() + ".SO_RCVBUF";

    /**
     * Sets a specific priority on a specific <code>Thread</code>.
     *
     * @param thread the <code>Thread</code> to set the specified <code>priority</code> on
     * @param priority the priority to set on the specified <code>thread</code>
     */
    public static void setThreadPriority(Thread thread, int priority)
    {
        int oldPriority = thread.getPriority();

        if (priority != oldPriority) {
            Throwable throwable = null;

            try {
                thread.setPriority(priority);
            } catch (IllegalArgumentException | SecurityException iae) {
                throwable = iae;
            }
            if (throwable != null) {
                Timber.w("Failed to use Thread priority: %s", priority);
            }
            int newPriority = thread.getPriority();
            if (priority != newPriority) {
                Timber.d("Did not change Thread priority from %s => %s; keep %s instead.",
                        oldPriority, priority, newPriority);
            }
        }
    }

    /**
     * Packet receive buffer
     */
    private final byte[] buffer = new byte[PACKET_RECEIVE_BUFFER_LENGTH];

    /**
     * Whether this stream is closed. Used to control the termination of worker thread.
     */
    private boolean closed;

    /**
     * The <code>DatagramPacketFilter</code>s which allow dropping <code>DatagramPacket</code>s before they
     * are converted into <code>RawPacket</code>s.
     */
    private DatagramPacketFilter[] datagramPacketFilters;

    /**
     * Whether this <code>RTPConnectorInputStream</code> is enabled or disabled.
     * While disabled, the stream does not accept any packets.
     */
    private boolean enabled = true;

    /**
     * Caught an IO exception during read from socket
     */
    private boolean ioError = false;

    /**
     * Number of received bytes.
     */
    private long numberOfReceivedBytes = 0;

    /**
     * The packet data to be read out of this instance through its {@link #read(byte[], int, int)} method.
     */
    private RawPacket pkt;

    /**
     * The <code>Object</code> which synchronizes the access to {@link #pkt}.
     */
    private final Object pktSyncRoot = new Object();

    /**
     * The adapter of this <code>PushSourceStream</code> to the <code>PushBufferStream</code> interface.
     */
    private final PushBufferStream pushBufferStream;

    /**
     * The pool of <code>RawPacket</code> instances to reduce their allocations and garbage collection.
     */
    private final Queue<RawPacket> rawPacketPool = new ArrayBlockingQueue<>(RTPConnectorOutputStream.POOL_CAPACITY);

    /**
     * The background/daemon <code>Thread</code> which invokes {@link #receive(DatagramPacket)}.
     */
    private Thread receiveThread;

    protected final T socket;

    /**
     * SourceTransferHandler object which is used to read packets.
     */
    private SourceTransferHandler transferHandler;

    /**
     * The time in milliseconds of the last activity related to this <code>RTPConnectorInputStream</code>.
     */
    private final MonotonicAtomicLong lastActivityTime = new MonotonicAtomicLong();

    /**
     * Initializes a new <code>RTPConnectorInputStream</code> which is to receive packet data from a specific UDP socket.
     *
     * @param socket
     */
    protected RTPConnectorInputStream(T socket)
    {
        this.socket = socket;
        if (this.socket == null) {
            closed = true;
        }
        else {
            closed = false;

            try {
                int receiveBufferSize = LibJitsi.getConfigurationService().getInt(SO_RCVBUF_PNAME, 65535);
                setReceiveBufferSize(receiveBufferSize);
            } catch (Throwable t) {
                if (t instanceof InterruptedException)
                    Thread.currentThread().interrupt();
                else if (t instanceof ThreadDeath)
                    throw (ThreadDeath) t;
            }
        }

        addDatagramPacketFilter(p -> {
            lastActivityTime.increase(System.currentTimeMillis());
            return true;
        });

        /*
         * Adapt this PushSourceStream to the PushBufferStream interface in order to make it
         * possible to read the Buffer flags of RawPacket.
         */
        pushBufferStream = new PushBufferStreamAdapter(this, null)
        {
            @Override
            protected int doRead(Buffer buffer, byte[] data, int offset, int length)
                    throws IOException
            {
                return RTPConnectorInputStream.this.read(buffer, data, offset, length);
            }
        };
        maybeStartReceiveThread();
    }

    /**
     * Gets the time in milliseconds of the last activity related to this <code>RTPConnectorInputStream</code>.
     *
     * @return the time in milliseconds of the last activity related to this <code>RTPConnectorInputStream</code>
     */
    public long getLastActivityTime()
    {
        return lastActivityTime.get();
    }

    /**
     * Determines whether all {@link #datagramPacketFilters} accept a received
     * <code>DatagramPacket</code> for pushing out of this <code>PushSourceStream</code> . In other words,
     * determines whether <code>p</code> is to be discarded/dropped/ignored.
     *
     * @param p the <code>DatagramPacket</code> to be considered for acceptance by all <code>datagramPacketFilters</code>
     * @return <code>true</code> if all <code>datagramPacketFilters</code> accept <code>p</code>; otherwise, <code>false</code>
     */
    private boolean accept(DatagramPacket p)
    {
        boolean accept;
        if (enabled) {
            DatagramPacketFilter[] filters = getDatagramPacketFilters();

            if (filters == null) {
                accept = true;
            }
            else {
                accept = true;
                for (DatagramPacketFilter filter : filters) {
                    try {
                        if (!filter.accept(p)) {
                            accept = false;
                            break;
                        }
                    } catch (Throwable t) {
                        if (t instanceof InterruptedException)
                            Thread.currentThread().interrupt();
                        else if (t instanceof ThreadDeath)
                            throw (ThreadDeath) t;
                    }
                }
            }
        }
        else {
            accept = false;
            if (!closed) {
                Timber.log(TimberLog.FINER, "Will drop received packet because this is disabled: " + p.getLength() + " bytes.");
            }
        }
        return accept;
    }

    /**
     * Adds a <code>DatagramPacketFilter</code> which allows dropping <code>DatagramPacket</code>s before
     * they are converted into <code>RawPacket</code> s.
     *
     * @param datagramPacketFilter the <code>DatagramPacketFilter</code> which allows dropping <code>DatagramPacket</code>s
     * before they are converted into <code>RawPacket</code>s
     */
    public synchronized void addDatagramPacketFilter(DatagramPacketFilter datagramPacketFilter)
    {
        datagramPacketFilters = ArrayUtils.add(datagramPacketFilters, DatagramPacketFilter.class, datagramPacketFilter);
    }

    /**
     * Close this stream, stops the worker thread.
     */
    @Override
    public synchronized void close()
    {
        closed = true;
        if (socket != null) {
            /*
             * The classes DatagramSocket and Socket implement the interface
             * Closeable since Java Runtime Environment 7.
             */
            try {
                if (socket instanceof Closeable) {
                    socket.close();
                }
            } catch (IOException ex) {
                // ignore
            }
        }
    }

    /**
     * Creates a new <code>RawPacket</code> from a specific <code>DatagramPacket</code> in order to have
     * this instance receive its packet data through its {@link #read(byte[], int, int)} method.
     * Returns an array of <code>RawPacket</code> with the created packet as its first element (and
     * <code>null</code> for the other elements).
     * <p>
     * Allows extenders to intercept the packet data and possibly filter and/or modify it.
     *
     * @param datagramPacket the <code>DatagramPacket</code> containing the packet data
     * @return an array of <code>RawPacket</code> containing the <code>RawPacket</code> which contains the
     * packet data of the specified <code>DatagramPacket</code> as its first element.
     */
    protected RawPacket[] createRawPacket(DatagramPacket datagramPacket)
    {
        RawPacket[] pkts = new RawPacket[1];

        RawPacket pkt = rawPacketPool.poll();
        if (pkt == null)
            pkt = new RawPacket();

        byte[] buffer = pkt.getBuffer();
        int length = datagramPacket.getLength();
        if (buffer == null || buffer.length < length) {
            buffer = new byte[length];
            pkt.setBuffer(buffer);
        }

        System.arraycopy(datagramPacket.getData(), datagramPacket.getOffset(), buffer, 0, length);
        pkt.setBuffer(buffer);
        pkt.setOffset(0);
        pkt.setLength(length);
        pkt.setFlags(0);

        pkts[0] = pkt;
        return pkts;
    }

    /**
     * Provides a dummy implementation to {@link RTPConnectorInputStream#endOfStream()} that always
     * returns <code>false</code>.
     *
     * @return <code>false</code>, no matter what.
     */
    public boolean endOfStream()
    {
        return false;
    }

    /**
     * Provides a dummy implementation to {@link RTPConnectorInputStream#getContentDescriptor()}
     * that always returns <code>null</code>.
     *
     * @return <code>null</code>, no matter what.
     */
    public ContentDescriptor getContentDescriptor()
    {
        return null;
    }

    /**
     * Provides a dummy implementation to {@link RTPConnectorInputStream#getContentLength()} that
     * always returns <code>LENGTH_UNKNOWN</code>.
     *
     * @return <code>LENGTH_UNKNOWN</code>, no matter what.
     */
    public long getContentLength()
    {
        return LENGTH_UNKNOWN;
    }

    /**
     * Provides a dummy implementation of {@link RTPConnectorInputStream#getControl(String)} that
     * always returns <code>null</code>.
     *
     * @param controlType ignored.
     * @return <code>null</code>, no matter what.
     */
    public Object getControl(String controlType)
    {
        if (AbstractPushBufferStream.PUSH_BUFFER_STREAM_CLASS_NAME.equals(controlType)) {
            return pushBufferStream;
        }
        else {
            return null;
        }
    }

    /**
     * Provides a dummy implementation of {@link RTPConnectorInputStream#getControls()} that always
     * returns <code>EMPTY_CONTROLS</code>.
     *
     * @return <code>EMPTY_CONTROLS</code>, no matter what.
     */
    public Object[] getControls()
    {
        return EMPTY_CONTROLS;
    }

    /**
     * Gets the <code>DatagramPacketFilter</code>s which allow dropping <code>DatagramPacket</code>s before
     * they are converted into <code>RawPacket</code>s.
     *
     * @return the <code>DatagramPacketFilter</code>s which allow dropping <code>DatagramPacket</code>s
     * before they are converted into <code>RawPacket</code>s.
     */
    protected synchronized DatagramPacketFilter[] getDatagramPacketFilters()
    {
        return datagramPacketFilters;
    }

    /**
     * Provides a dummy implementation of {@link
     * PushSourceStream#getMinimumTransferSize()} that always returns
     * <code>2 * 1024</code>.
     *
     * @return <code>2 * 1024</code>, no matter what.
     */
    @Override
    public int getMinimumTransferSize()
    {
        return 2 * 1024; // twice the MTU size, just to be safe.
    }

    /**
     * Returns the number of received bytes for the stream.
     *
     * @return the number of received bytes
     */
    public long getNumberOfReceivedBytes()
    {
        return numberOfReceivedBytes;
    }

    private synchronized void maybeStartReceiveThread()
    {
        if (receiveThread == null) {
            if ((socket != null) && !closed && (transferHandler != null)) {
                receiveThread = new Thread()
                {
                    @Override
                    public void run()
                    {
                        RTPConnectorInputStream.this.runInReceiveThread();
                    }
                };
                receiveThread.setDaemon(true);
                receiveThread.setName(RTPConnectorInputStream.class.getName() + ".receiveThread");

                setThreadPriority(receiveThread, MediaThread.getNetworkPriority());
                receiveThread.start();
            }
        }
        else {
            notifyAll();
        }
    }

    /**
     * Pools the specified <code>RawPacket</code> in order to avoid future allocations and to reduce
     * the
     * effects of garbage collection.
     *
     * @param pkt the <code>RawPacket</code> to be offered to {@link #rawPacketPool}
     */
    private void poolRawPacket(RawPacket pkt)
    {
        pkt.setFlags(0);
        pkt.setLength(0);
        pkt.setOffset(0);
        rawPacketPool.offer(pkt);
    }

    /**
     * Copies the content of the most recently received packet into <code>data</code>.
     *
     * @param buffer an optional <code>Buffer</code> instance associated with the specified <code>data</code>,
     * <code>offset</code> and <code>length</code> and provided to the method in case the
     * implementation would like to provide additional <code>Buffer</code> properties such as
     * <code>flags</code>
     * @param data the <code>byte[]</code> that we'd like to copy the content of the packet to.
     * @param offset the position where we are supposed to start writing in <code>data</code>.
     * @param length the number of <code>byte</code>s available for writing in <code>data</code>.
     * @return the number of bytes read
     * @throws IOException if <code>length</code> is less than the size of the packet.
     */
    protected int read(Buffer buffer, byte[] data, int offset, int length)
            throws IOException
    {
        if (data == null)
            throw new NullPointerException("data");

        if (ioError)
            return -1;

        RawPacket pkt;

        synchronized (pktSyncRoot) {
            pkt = this.pkt;
            this.pkt = null;
        }

        int pktLength;

        if (pkt == null) {
            pktLength = 0;
        }
        else {
            // By default, pkt will be returned to the pool after it was read.
            boolean poolPkt = true;

            try {
                pktLength = pkt.getLength();
                if (length < pktLength) {
                    /*
                     * If pkt is still the latest RawPacket made available to reading, reinstate it
                     * for the next invocation of read; otherwise, return it to the pool.
                     */
                    poolPkt = false;
                    throw new IOException("Input buffer not big enough for " + pktLength);
                }
                else {
                    byte[] pktBuffer = pkt.getBuffer();

                    if (pktBuffer == null) {
                        throw new NullPointerException("pkt.buffer null, pkt.length " + pktLength
                                + ", pkt.offset " + pkt.getOffset());
                    }
                    else {
                        System.arraycopy(pkt.getBuffer(), pkt.getOffset(), data, offset, pktLength);
                        if (buffer != null)
                            buffer.setFlags(pkt.getFlags());
                    }
                }
            } finally {
                if (!poolPkt) {
                    synchronized (pktSyncRoot) {
                        if (this.pkt == null)
                            this.pkt = pkt;
                        else
                            poolPkt = true;
                    }
                }
                if (poolPkt) {
                    // Return pkt to the pool because it was successfully read.
                    poolRawPacket(pkt);
                }
            }
        }
        return pktLength;
    }

    /**
     * Copies the content of the most recently received packet into <code>buffer</code>.
     *
     * @param buffer the <code>byte[]</code> that we'd like to copy the content of the packet to.
     * @param offset the position where we are supposed to start writing in <code>buffer</code>.
     * @param length the number of <code>byte</code>s available for writing in <code>buffer</code>.
     * @return the number of bytes read
     * @throws IOException if <code>length</code> is less than the size of the packet.
     */
    @Override
    public int read(byte[] buffer, int offset, int length)
            throws IOException
    {
        return read(null, buffer, offset, length);
    }

    /**
     * Receive packet.
     *
     * @param p packet for receiving
     * @throws IOException if something goes wrong during receiving
     */
    protected abstract void receive(DatagramPacket p)
            throws IOException;

    /**
     * Listens for incoming datagram packets, stores them for reading by the <code>read</code> method
     * and notifies the local <code>transferHandler</code> that there's data to be read.
     */
    private void runInReceiveThread()
    {
        DatagramPacket p = new DatagramPacket(buffer, 0, PACKET_RECEIVE_BUFFER_LENGTH);

        while (!closed) {
            // Reset the buffer, because the previous call to receive() might
            // have bumped the offset or even changed the byte[].
            p.setData(buffer, 0, buffer.length);

            try {
                receive(p);
            } catch (SocketTimeoutException ste) {
                // We need to handle these, because some of our implementations
                // of DatagramSocket#receive are unable to throw a SocketClosed exception.
                Timber.log(TimberLog.FINER, "Socket timeout, closed = %s", closed);
                continue;
            } catch (IOException e) {
                ioError = true;
                break;
            }

            numberOfReceivedBytes += p.getLength();
            try {
                // Do the DatagramPacketFilters accept the received DatagramPacket?
                if (accept(p)) {
                    RawPacket[] pkts = createRawPacket(p);
                    transferData(pkts);
                }
            } catch (Exception e) {
                // The receive thread should not die as a result of a failure in
                // the packetization (converting to RawPacket[] and transforming)
                // or a failure in any of the DatagramPacketFilters.
                Timber.e(e, "Failed to receive a packet: ");
            }
        }
    }

    /**
     * Enables or disables this <code>RTPConnectorInputStream</code>. While the stream is disabled, it
     * does not accept any packets.
     *
     * @param enabled <code>true</code> to enable, <code>false</code> to disable.
     */
    public void setEnabled(boolean enabled)
    {
        if (this.enabled != enabled) {
            Timber.log(TimberLog.FINER, "setEnabled: " + enabled);

            this.enabled = enabled;
        }
    }

    /**
     * Changes current thread priority.
     *
     * @param priority the new priority.
     */
    public void setPriority(int priority)
    {
        // if (receiverThread != null)
        // receiverThread.setPriority(priority);
    }

    protected abstract void setReceiveBufferSize(int receiveBufferSize)
            throws IOException;

    /**
     * Sets the <code>transferHandler</code> that this connector should be notifying when new data is
     * available for reading.
     *
     * @param transferHandler the <code>transferHandler</code> that this connector should be notifying when new data is
     * available for reading.
     */
    @Override
    public synchronized void setTransferHandler(SourceTransferHandler transferHandler)
    {
        if (this.transferHandler != transferHandler) {
            this.transferHandler = transferHandler;
            maybeStartReceiveThread();
        }
    }

    /**
     * Invokes {@link SourceTransferHandler#transferData(PushSourceStream)} on
     * {@link #transferHandler} for each of <code>pkts</code> in order to consecutively push them out
     * of/make them available outside this <code>PushSourceStream</code>.
     *
     * @param pkts the set of <code>RawPacket</code>s to push out of this <code>PushSourceStream</code>
     */
    private void transferData(RawPacket[] pkts)
    {
        for (int i = 0; i < pkts.length; i++) {
            RawPacket pkt = pkts[i];

            pkts[i] = null;

            if (pkt != null) {
                if (pkt.isInvalid()) {
                    /*
                     * Return pkt to the pool because it is invalid and, consequently, will not be
                     * made available to reading.
                     */
                    poolRawPacket(pkt);
                }
                else {
                    RawPacket oldPkt;

                    synchronized (pktSyncRoot) {
                        oldPkt = this.pkt;
                        this.pkt = pkt;
                    }
                    if (oldPkt != null) {
                        /*
                         * Return oldPkt to the pool because it was made available to reading
                         * and it
                         * was not read.
                         */
                        poolRawPacket(oldPkt);
                    }

                    if ((transferHandler != null) && !closed) {
                        try {
                            transferHandler.transferData(this);
                        } catch (Throwable t) {
                            // XXX We cannot allow transferHandler to kill us.
                            if (t instanceof InterruptedException) {
                                Thread.currentThread().interrupt();
                            }
                            else if (t instanceof ThreadDeath) {
                                throw (ThreadDeath) t;
                            }
                            else {
                                Timber.w(t, "An RTP packet may have not been fully handled.");
                            }
                        }
                    }
                }
            }
        }
    }
}
