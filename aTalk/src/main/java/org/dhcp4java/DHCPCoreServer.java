/*
 *	This file is part of dhcp4java, a DHCP API for the Java language.
 *	(c) 2006 Stephan Hadinger
 *
 *	This library is free software; you can redistribute it and/or
 *	modify it under the terms of the GNU Lesser General Public
 *	License as published by the Free Software Foundation; either
 *	version 2.1 of the License, or (at your option) any later version.
 *
 *	This library is distributed in the hope that it will be useful,
 *	but WITHOUT ANY WARRANTY; without even the implied warranty of
 *	MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 *	Lesser General Public License for more details.
 *
 *	You should have received a copy of the GNU Lesser General Public
 *	License along with this library; if not, write to the Free Software
 *	Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301  USA
 */
package org.dhcp4java;

import org.atalk.android.plugin.timberlog.TimberLog;

import java.io.IOException;
import java.io.InputStream;
import java.net.*;
import java.util.Properties;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import timber.log.Timber;

/**
 * A simple generic DHCP Server.
 *
 * The DHCP Server provided is based on a multi-thread model. The main thread listens
 * at the socket, then dispatches work to a pool of threads running the servlet.
 *
 * <p>Configuration: the Server reads the following properties in "/DHCPd.properties"
 * at the root of the class path. You can however provide a properties set when
 * contructing the server. Default values are:
 *
 * <blockquote>
 * <tt>serverAddress=127.0.0.1:67</tt> <i>[address:port]</i>
 * <br>
 * <tt>serverThreads=2</tt> <i>[number of concurrent threads for servlets]</i>
 * </blockquote>
 *
 * <p>Note: this class implements <tt>Runnable</tt> allowing it to be run
 * in a dedicated thread.
 *
 * <p>Example:
 *
 * <pre>
 *     public static void main(String[] args) {
 *         try {
 *             DHCPCoreServer server = DHCPCoreServer.initServer(new DHCPStaticServlet(), null);
 *             new Thread(server).start();
 *         } catch (DHCPServerInitException e) {
 *             // die gracefully
 *         }
 *     }
 * </pre>
 *
 * @author Stephan Hadinger
 * @author Eng Chong Meng
 * @version 1.00
 */
public class DHCPCoreServer implements Runnable
{

    private static final int BOUNDED_QUEUE_SIZE = 20;

    /**
     * default MTU for ethernet
     */
    protected static final int PACKET_SIZE = 1500;

    /**
     * the servlet it must run
     */
    protected DHCPServlet servlet;
    /**
     * working threads pool.
     */
    protected ThreadPoolExecutor threadPool;
    /**
     * Consolidated parameters of the server.
     */
    protected Properties properties;
    /**
     * Reference of user-provided parameters
     */
    protected Properties userProps;
    /**
     * IP address and port for the server
     */
    private InetSocketAddress sockAddress = null;
    /**
     * The socket for receiving and sending.
     */
    private DatagramSocket serverSocket;
    /**
     * do we need to stop the server?
     */
    private boolean stopped = false;

    /**
     * Constructor
     *
     * <p>Constructor shall not be called directly. New servers are created through
     * <tt>initServer()</tt> factory.
     */
    private DHCPCoreServer(DHCPServlet servlet, Properties userProps)
    {
        this.servlet = servlet;
        this.userProps = userProps;
    }

    /**
     * Creates and initializes a new DHCP Server.
     *
     * <p>It instanciates the object, then calls <tt>init()</tt> method.
     *
     * @param servlet the <tt>DHCPServlet</tt> instance processing incoming requests,
     * must not be <tt>null</tt>.
     * @param userProps specific properties, overriding file and default properties,
     * may be <tt>null</tt>.
     * @return the new <tt>DHCPCoreServer</tt> instance (never null).
     * @throws DHCPServerInitException unable to start the server.
     */
    public static DHCPCoreServer initServer(DHCPServlet servlet, Properties userProps)
            throws DHCPServerInitException
    {
        if (servlet == null) {
            throw new IllegalArgumentException("servlet must not be null");
        }
        DHCPCoreServer server = new DHCPCoreServer(servlet, userProps);
        server.init();
        return server;
    }

    /**
     * Initialize the server context from the Properties, and open socket.
     */
    protected void init()
            throws DHCPServerInitException
    {
        if (this.serverSocket != null) {
            throw new IllegalStateException("Server already initialized");
        }

        try {
            // default built-in minimal properties
            this.properties = new Properties(DEF_PROPS);

            // try to load default configuration file
            InputStream propFileStream = this.getClass().getResourceAsStream("/DHCPd.properties");
            if (propFileStream != null) {
                this.properties.load(propFileStream);
            }
            else {
                Timber.e("Could not load DHCPd.properties");
            }

            // now integrate provided properties
            if (this.userProps != null) {
                this.properties.putAll(this.userProps);
            }

            // load socket address, this method may be overriden
            sockAddress = this.getInetSocketAddress(this.properties);
            if (sockAddress == null) {
                throw new DHCPServerInitException("Cannot find which SockAddress to open");
            }

            // open socket for listening and sending
            this.serverSocket = new DatagramSocket(null);
            this.serverSocket.setBroadcast(true);        // allow sending broadcast
            this.serverSocket.bind(sockAddress);

            // initialize Thread Pool
            int numThreads = Integer.valueOf(this.properties.getProperty(SERVER_THREADS));
            int maxThreads = Integer.valueOf(this.properties.getProperty(SERVER_THREADS_MAX));
            int keepaliveThreads = Integer.valueOf(this.properties.getProperty(SERVER_THREADS_KEEPALIVE));
            this.threadPool = new ThreadPoolExecutor(numThreads, maxThreads,
                    keepaliveThreads, TimeUnit.MILLISECONDS,
                    new ArrayBlockingQueue<>(BOUNDED_QUEUE_SIZE),
                    new ServerThreadFactory());
            this.threadPool.prestartAllCoreThreads();

            // now intialize the servlet
            this.servlet.setServer(this);
            this.servlet.init(this.properties);
        } catch (DHCPServerInitException e) {
            throw e;        // transparently re-throw
        } catch (Exception e) {
            this.serverSocket = null;
            Timber.e(e, "Cannot open socket");
            throw new DHCPServerInitException("Unable to init server", e);
        }
    }

    /*
     * (non-Javadoc)
     *
     * @see java.lang.Runnable#run()
     */
    protected void dispatch()
    {
        try {
            DatagramPacket requestDatagram = new DatagramPacket(new byte[PACKET_SIZE], PACKET_SIZE);
            Timber.log(TimberLog.FINER, "Waiting for packet");

            // receive datagram
            this.serverSocket.receive(requestDatagram);

            if (TimberLog.isTraceEnable) {
                StringBuilder sbuf = new StringBuilder("Received packet from ");
                DHCPPacket.appendHostAddress(sbuf, requestDatagram.getAddress());
                sbuf.append('(')
                        .append(requestDatagram.getPort())
                        .append(')');
                Timber.log(TimberLog.FINER, sbuf.toString());
            }

            // send work to thread pool
            DHCPServletDispatcher dispatcher = new DHCPServletDispatcher(this, servlet, requestDatagram);
            threadPool.execute(dispatcher);
        } catch (IOException e) {
            Timber.log(TimberLog.FINER, e, "IOException");
        }
    }

    /**
     * Send back response packet to client.
     *
     * <p>This is a callback method used by servlet dispatchers to send back responses.
     */
    protected void sendResponse(DatagramPacket responseDatagram)
    {
        if (responseDatagram == null) {
            return; // skipping
        }

        try {
            // sending back
            this.serverSocket.send(responseDatagram);
        } catch (IOException e) {
            Timber.log(TimberLog.FINER, e, "IOException");
        }
    }

    /**
     * Returns the <tt>InetSocketAddress</tt> for the server (client-side).
     *
     * <pre>
     *
     *  serverAddress (default 127.0.0.1)
     *  serverPort (default 67)
     *
     * </pre>
     *
     * <p>
     * This method can be overriden to specify an non default socket behaviour
     *
     * @param props Properties loaded from /DHCPd.properties
     * @return the socket address, null if there was a problem
     */
    protected InetSocketAddress getInetSocketAddress(Properties props)
    {
        if (props == null) {
            throw new IllegalArgumentException("null props not allowed");
        }
        String serverAddress = props.getProperty(SERVER_ADDRESS);
        if (serverAddress == null) {
            throw new IllegalStateException("Cannot load SERVER_ADDRESS property");
        }
        return parseSocketAddress(serverAddress);
    }

    /**
     * Parse a string of the form 'server:port' or '192.168.1.10:67'.
     *
     * @param address string to parse
     * @return InetSocketAddress newly created
     * @throws IllegalArgumentException if unable to parse string
     */
    public static InetSocketAddress parseSocketAddress(String address)
    {
        if (address == null) {
            throw new IllegalArgumentException("Null address not allowed");
        }
        int index = address.indexOf(':');
        if (index <= 0) {
            throw new IllegalArgumentException("semicolon missing for port number");
        }

        String serverStr = address.substring(0, index);
        String portStr = address.substring(index + 1);
        int port = Integer.parseInt(portStr);

        return new InetSocketAddress(serverStr, port);
    }

    /**
     * This is the main loop for accepting new request and delegating work to
     * servlets in different threads.
     */
    public void run()
    {
        if (this.serverSocket == null) {
            throw new IllegalStateException("Listening socket is not open - terminating");
        }
        while (!this.stopped) {
            try {
                this.dispatch();        // do the stuff
            } catch (Exception e) {
                Timber.w(e, "Unexpected Exception");
            }
        }
    }

    /**
     * This method stops the server and closes the socket.
     */
    public void stopServer()
    {
        this.stopped = true;
        this.serverSocket.close();        // this generates an exception when trying to receive
    }

    private static final Properties DEF_PROPS = new Properties();

    public static final String SERVER_ADDRESS = "serverAddress";
    private static final String SERVER_ADDRESS_DEFAULT = "127.0.0.1:67";
    public static final String SERVER_THREADS = "serverThreads";
    private static final String SERVER_THREADS_DEFAULT = "2";
    public static final String SERVER_THREADS_MAX = "serverThreadsMax";
    private static final String SERVER_THREADS_MAX_DEFAULT = "4";
    public static final String SERVER_THREADS_KEEPALIVE = "serverThreadsKeepalive";
    private static final String SERVER_THREADS_KEEPALIVE_DEFAULT = "10000";

    static {
        // initialize defProps
        DEF_PROPS.put(SERVER_ADDRESS, SERVER_ADDRESS_DEFAULT);
        DEF_PROPS.put(SERVER_THREADS, SERVER_THREADS_DEFAULT);
        DEF_PROPS.put(SERVER_THREADS_MAX, SERVER_THREADS_MAX_DEFAULT);
        DEF_PROPS.put(SERVER_THREADS_KEEPALIVE, SERVER_THREADS_KEEPALIVE_DEFAULT);
    }

    private static class ServerThreadFactory implements ThreadFactory
    {
        private static final AtomicInteger poolNumber = new AtomicInteger(1);

        final AtomicInteger threadNumber = new AtomicInteger(1);
        final String namePrefix;

        ServerThreadFactory()
        {
            this.namePrefix = "DHCPCoreServer-" + poolNumber.getAndIncrement() + "-thread-";
        }

        public Thread newThread(Runnable runnable)
        {
            return new Thread(runnable, this.namePrefix + this.threadNumber.getAndIncrement());
        }
    }

    /**
     * @return Returns the socket address.
     */
    public InetSocketAddress getSockAddress()
    {
        return sockAddress;
    }
}


/**
 * Servlet dispatcher
 */
class DHCPServletDispatcher implements Runnable
{
    private final DHCPCoreServer server;
    private final DHCPServlet dispatchServlet;
    private final DatagramPacket dispatchPacket;

    public DHCPServletDispatcher(DHCPCoreServer server, DHCPServlet servlet, DatagramPacket req)
    {
        this.server = server;
        this.dispatchServlet = servlet;
        this.dispatchPacket = req;
    }

    public void run()
    {
        try {
            DatagramPacket response = this.dispatchServlet.serviceDatagram(this.dispatchPacket);
            this.server.sendResponse(response);        // invoke callback method
        } catch (Exception e) {
            Timber.log(TimberLog.FINER, e, "Exception in dispatcher");
        }
    }
}
