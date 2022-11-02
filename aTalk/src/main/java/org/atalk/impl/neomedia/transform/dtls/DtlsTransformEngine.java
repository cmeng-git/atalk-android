/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.atalk.impl.neomedia.transform.dtls;

import org.atalk.impl.neomedia.transform.PacketTransformer;
import org.atalk.impl.neomedia.transform.TransformEngine;
import org.atalk.service.neomedia.SrtpControl;

/**
 * Implements {@link SrtpControl.TransformEngine} (and, respectively, {@link TransformEngine}) for DTLS-SRTP.
 *
 * @author Lyubomir Marinov
 */
public class DtlsTransformEngine implements SrtpControl.TransformEngine
{
    /**
     * The index of the RTP component.
     */
    static final int COMPONENT_RTP = 0;

    /**
     * The index of the RTCP component.
     */
    static final int COMPONENT_RTCP = 1;

    /**
     * The indicator which determines whether {@link SrtpControl.TransformEngine#cleanup()} has been
     * invoked on this instance to prepare it for garbage collection.
     */
    private boolean disposed = false;

    /**
     * The <code>DtlsControl</code> which has initialized this instance.
     */
    private final DtlsControlImpl dtlsControl;

    /**
     * The <code>PacketTransformer</code>s of this <code>TransformEngine</code> for data/RTP and control/RTCP packets.
     */
    private final DtlsPacketTransformer[] packetTransformers = new DtlsPacketTransformer[2];

    /**
     * Initializes a new <code>DtlsTransformEngine</code> instance.
     */
    public DtlsTransformEngine(DtlsControlImpl dtlsControl)
    {
        this.dtlsControl = dtlsControl;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void cleanup()
    {
        disposed = true;

        for (int i = 0; i < packetTransformers.length; i++) {
            DtlsPacketTransformer packetTransformer = packetTransformers[i];

            if (packetTransformer != null) {
                packetTransformer.close();
                packetTransformers[i] = null;
            }
        }
    }

    /**
     * Initializes a new <code>DtlsPacketTransformer</code> instance which is to work on control/RTCP or data/RTP packets.
     *
     * @param componentID the ID of the component for which the new instance is to work
     * @return a new <code>DtlsPacketTransformer</code> instance which is to work on control/RTCP or
     * data/RTP packets (in accord with <code>data</code>)
     */
    protected DtlsPacketTransformer createPacketTransformer(int componentID)
    {
        return new DtlsPacketTransformer(this, componentID);
    }

    /**
     * Gets the <code>DtlsControl</code> which has initialized this instance.
     *
     * @return the <code>DtlsControl</code> which has initialized this instance
     */
    DtlsControlImpl getDtlsControl()
    {
        return dtlsControl;
    }

    /**
     * Gets the <code>PacketTransformer</code> of this <code>TransformEngine</code> which is to work or
     * works for the component with a specific ID.
     *
     * @param componentID the ID of the component for which the returned <code>PacketTransformer</code> is to work or works
     * @return the <code>PacketTransformer</code>, if any, which is to work or works for the component
     * with the specified <code>componentID</code>
     */
    private DtlsPacketTransformer getPacketTransformer(int componentID)
    {
        DtlsPacketTransformer packetTransformer = packetTransformers[componentID];

        if ((packetTransformer == null) && !disposed) {
            packetTransformer = createPacketTransformer(componentID);
            if (packetTransformer != null)
                packetTransformers[componentID] = packetTransformer;
        }
        return packetTransformer;
    }

    /**
     * Gets the properties of {@code DtlsControlImpl} and their values which
     * {@link #dtlsControl} shares with this instance and {@link DtlsPacketTransformer}.
     *
     * @return the properties of {@code DtlsControlImpl} and their values which
     * {@code dtlsControl} shares with this instance and {@code DtlsPacketTransformer}
     */
    Properties getProperties()
    {
        return getDtlsControl().getProperties();
    }

    /**
     * {@inheritDoc}
     */
    public PacketTransformer getRTCPTransformer()
    {
        return getPacketTransformer(COMPONENT_RTCP);
    }

    /**
     * {@inheritDoc}
     */
    public PacketTransformer getRTPTransformer()
    {
        return getPacketTransformer(COMPONENT_RTP);
    }
}
