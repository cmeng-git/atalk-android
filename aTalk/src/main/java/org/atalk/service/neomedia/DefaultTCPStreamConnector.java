/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.atalk.service.neomedia;

import java.io.IOException;
import java.net.DatagramSocket;
import java.net.Socket;

import timber.log.Timber;

/**
 * Represents a default implementation of <code>StreamConnector</code> which is initialized with a
 * specific pair of control and data <code>Socket</code>s and which closes them (if they exist) when its
 * {@link #close()} is invoked.
 *
 * @author Lubomir Marinov
 * @author Sebastien Vincent
 */
public class DefaultTCPStreamConnector implements StreamConnector
{
    /**
     * The <code>Socket</code> that a stream should use for control data (e.g. RTCP) traffic.
     */
    protected Socket controlSocket;

    /**
     * The <code>Socket</code> that a stream should use for data (e.g. RTP) traffic.
     */
    protected Socket dataSocket;

    /**
     * Whether this <code>DefaultStreamConnector</code> uses rtcp-mux.
     */
    protected boolean rtcpmux;

    /**
     * Initializes a new <code>DefaultTCPStreamConnector</code> instance with no control and data
     * <code>Socket</code>s.
     * <p>
     * Suitable for extenders willing to delay the creation of the control and data sockets. For
     * example, they could override {@link #getControlSocket()} and/or {@link #getDataSocket()} and
     * create them on demand.
     */
    public DefaultTCPStreamConnector()
    {
        this(null, null);
    }

    /**
     * Initializes a new <code>DefaultTCPStreamConnector</code> instance which is to represent a
     * specific pair of control and data <code>Socket</code>s.
     *
     * @param dataSocket the <code>Socket</code> to be used for data (e.g. RTP) traffic
     * @param controlSocket the <code>Socket</code> to be used for control data (e.g. RTCP) traffic
     */
    public DefaultTCPStreamConnector(Socket dataSocket, Socket controlSocket)
    {
        this(dataSocket, controlSocket, false);
    }

    /**
     * Initializes a new <code>DefaultTCPStreamConnector</code> instance which is to represent a
     * specific pair of control and data <code>Socket</code>s.
     *
     * @param dataSocket the <code>Socket</code> to be used for data (e.g. RTP) traffic
     * @param controlSocket the <code>Socket</code> to be used for control data (e.g. RTCP) traffic
     * @param rtcpmux whether rtcpmux is used.
     */
    public DefaultTCPStreamConnector(Socket dataSocket, Socket controlSocket, boolean rtcpmux)
    {
        this.controlSocket = controlSocket;
        this.dataSocket = dataSocket;
        this.rtcpmux = rtcpmux;
    }

    /**
     * Releases the resources allocated by this instance in the course of its execution and prepares
     * it to be garbage collected.
     *
     * @see StreamConnector#close()
     */
    @Override
    public void close()
    {
        try {
            if (controlSocket != null)
                controlSocket.close();
            if (dataSocket != null)
                dataSocket.close();
        } catch (IOException ioe) {
            Timber.d(ioe, "Failed to close TCP socket");
        }
    }

    /**
     * Returns a reference to the <code>DatagramSocket</code> that a stream should use for control data
     * (e.g. RTCP) traffic.
     *
     * @return a reference to the <code>DatagramSocket</code> that a stream should use for control data
     * (e.g. RTCP) traffic
     * @see StreamConnector#getControlSocket()
     */
    @Override
    public DatagramSocket getControlSocket()
    {
        return null;
    }

    /**
     * Returns a reference to the <code>DatagramSocket</code> that a stream should use for data (e.g.
     * RTP) traffic.
     *
     * @return a reference to the <code>DatagramSocket</code> that a stream should use for data (e.g.
     * RTP) traffic
     * @see StreamConnector#getDataSocket()
     */
    @Override
    public DatagramSocket getDataSocket()
    {
        return null;
    }

    /**
     * Returns a reference to the <code>Socket</code> that a stream should use for data (e.g. RTP)
     * traffic.
     *
     * @return a reference to the <code>Socket</code> that a stream should use for data (e.g. RTP)
     * traffic.
     */
    @Override
    public Socket getDataTCPSocket()
    {
        return dataSocket;
    }

    /**
     * Returns a reference to the <code>Socket</code> that a stream should use for control data (e.g.
     * RTCP).
     *
     * @return a reference to the <code>Socket</code> that a stream should use for control data (e.g.
     * RTCP).
     */
    @Override
    public Socket getControlTCPSocket()
    {
        return controlSocket;
    }

    /**
     * Returns the protocol of this <code>StreamConnector</code>.
     *
     * @return the protocol of this <code>StreamConnector</code>
     */
    @Override
    public Protocol getProtocol()
    {
        return Protocol.TCP;
    }

    /**
     * Notifies this instance that utilization of its <code>Socket</code>s for data and/or control
     * traffic has started.
     *
     * @see StreamConnector#started()
     */
    @Override
    public void started()
    {
    }

    /**
     * Notifies this instance that utilization of its <code>Socket</code>s for data and/or control
     * traffic has temporarily stopped. This instance should be prepared to be started at a later
     * time again though.
     *
     * @see StreamConnector#stopped()
     */
    @Override
    public void stopped()
    {
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean isRtcpmux()
    {
        return rtcpmux;
    }
}