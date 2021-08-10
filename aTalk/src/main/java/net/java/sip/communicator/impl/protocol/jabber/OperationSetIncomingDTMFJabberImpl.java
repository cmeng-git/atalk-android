/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package net.java.sip.communicator.impl.protocol.jabber;

import net.java.sip.communicator.service.protocol.OperationSetIncomingDTMF;
import net.java.sip.communicator.service.protocol.event.DTMFListener;
import net.java.sip.communicator.service.protocol.event.DTMFReceivedEvent;

import java.util.HashSet;
import java.util.Set;

/**
 * Implements <tt>OperationSetIncomingDTMF</tt> for the jabber protocol.
 *
 * @author Boris Grozev
 */
public class OperationSetIncomingDTMFJabberImpl implements OperationSetIncomingDTMF, DTMFListener
{
    private final Set<DTMFListener> listeners = new HashSet<DTMFListener>();

    /**
     * {@inheritDoc}
     *
     * Implements
     * {@link net.java.sip.communicator.service.protocol.OperationSetIncomingDTMF#addDTMFListener(DTMFListener)}
     */
    @Override
    public void addDTMFListener(DTMFListener listener)
    {
        listeners.add(listener);
    }

    /**
     * {@inheritDoc}
     *
     * Implements
     * {@link net.java.sip.communicator.service.protocol.OperationSetIncomingDTMF#removeDTMFListener(DTMFListener)}
     */
    @Override
    public void removeDTMFListener(DTMFListener listener)
    {
        listeners.remove(listener);
    }

    /**
     * {@inheritDoc}
     *
     * Implements
     * {@link net.java.sip.communicator.service.protocol.event.DTMFListener#toneReceived(DTMFReceivedEvent)}
     */
    @Override
    public void toneReceived(DTMFReceivedEvent evt)
    {
        for (DTMFListener listener : listeners)
            listener.toneReceived(evt);
    }
}
