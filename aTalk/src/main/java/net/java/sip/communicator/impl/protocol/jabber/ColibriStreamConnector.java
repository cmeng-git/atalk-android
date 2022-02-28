/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package net.java.sip.communicator.impl.protocol.jabber;

import org.atalk.service.neomedia.StreamConnector;
import org.atalk.service.neomedia.StreamConnectorDelegate;

/**
 * Implements a <code>StreamConnector</code> which allows sharing a specific <code>StreamConnector</code>
 * instance among multiple <code>TransportManager</code>s for the purposes of the Jitsi Videobridge.
 *
 * @author Lyubomir Marinov
 */
public class ColibriStreamConnector extends StreamConnectorDelegate<StreamConnector>
{
    /**
     * Initializes a new <code>ColibriStreamConnector</code> instance which is to share a specific
     * <code>StreamConnector</code> instance among multiple <code>TransportManager</code>s for the purposes
     * of the Jitsi Videobridge.
     *
     * @param streamConnector the <code>StreamConnector</code> instance to be shared by the new instance among multiple
     * <code>TransportManager</code>s for the purposes of the Jitsi Videobridge
     */
    public ColibriStreamConnector(StreamConnector streamConnector)
    {
        super(streamConnector);
    }

    /**
     * {@inheritDoc}
     *
     * Overrides {@link StreamConnectorDelegate#close()} in order to prevent the closing of the
     * <code>StreamConnector</code> wrapped by this instance because the latter is shared and it is not
     * clear whether no <code>TransportManager</code> is using it.
     */
    @Override
    public void close()
    {
        /*
         * Do not close the shared StreamConnector because it is not clear whether no
         * TransportManager is using it.
         */
    }

    /**
     * {@inheritDoc}
     *
     * Invokes {@link #close()} on this instance when it is clear that no <code>TransportManager</code>
     * is using it in order to release the resources allocated by this instance throughout its life
     * time (that need explicit disposal).
     */
    @Override
    protected void finalize()
            throws Throwable
    {
        try {
            /*
             * Close the shared StreamConnector because it is clear that no TrasportManager is using it.
             */
            super.close();
        } finally {
            super.finalize();
        }
    }
}
