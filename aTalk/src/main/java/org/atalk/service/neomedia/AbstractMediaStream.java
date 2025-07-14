/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.atalk.service.neomedia;

import java.beans.PropertyChangeListener;
import java.beans.PropertyChangeSupport;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import org.atalk.impl.neomedia.codec.REDBlock;
import org.atalk.impl.neomedia.rtp.MediaStreamTrackReceiver;
import org.atalk.impl.neomedia.rtp.TransportCCEngine;
import org.atalk.impl.neomedia.transform.TransformEngineChain;
import org.atalk.service.neomedia.format.MediaFormat;
import org.atalk.util.ByteArrayBuffer;

/**
 * Abstract base implementation of <code>MediaStream</code> to ease the implementation of the interface.
 *
 * @author Lyubomir Marinov
 */
public abstract class AbstractMediaStream implements MediaStream {
    /**
     * The name of this stream, that some protocols may use for diagnostic purposes.
     */
    private String name;

    /**
     * The opaque properties of this <code>MediaStream</code>.
     */
    private final Map<String, Object> properties = Collections.synchronizedMap(new HashMap<>());

    /**
     * The delegate of this instance which implements support for property change notifications for
     * its {@link #addPropertyChangeListener(PropertyChangeListener)} and
     * {@link #removePropertyChangeListener(PropertyChangeListener)}.
     */
    private final PropertyChangeSupport propertyChangeSupport = new PropertyChangeSupport(this);

    /**
     * The <code>RTPTranslator</code>, if any, which forwards RTP and RTCP traffic between this and
     * other <code>MediaStream</code>s.
     */
    protected RTPTranslator rtpTranslator;

    /**
     * Adds a <code>PropertyChangeListener</code> to this stream which is to be notified upon property
     * changes such as a SSRC ID which becomes known.
     *
     * @param listener the <code>PropertyChangeListener</code> to register for <code>PropertyChangeEvent</code>s
     *
     * @see MediaStream#addPropertyChangeListener(PropertyChangeListener)
     */
    @Override
    public void addPropertyChangeListener(PropertyChangeListener listener) {
        propertyChangeSupport.addPropertyChangeListener(listener);
    }

    /**
     * Asserts that the state of this instance will remain consistent if a specific
     * <code>MediaDirection</code> (i.e. <code>direction</code>) and a <code>MediaDevice</code> with a specific
     * <code>MediaDirection</code> (i.e. <code>deviceDirection</code>) are both set on this instance.
     *
     * @param direction the <code>MediaDirection</code> to validate against the specified <code>deviceDirection</code>
     * @param deviceDirection the <code>MediaDirection</code> of a <code>MediaDevice</code> to validate against the
     * specified <code>direction</code>
     * @param illegalArgumentExceptionMessage the message of the <code>IllegalArgumentException</code> to be thrown if the state of this
     * instance would've been compromised if <code>direction</code> and the <code>MediaDevice</code>
     * associated with <code>deviceDirection</code> were both set on this instance
     *
     * @throws IllegalArgumentException if the state of this instance would've been compromised were both <code>direction</code>
     * and the <code>MediaDevice</code> associated with <code>deviceDirection</code> set on this instance
     */
    protected void assertDirection(MediaDirection direction, MediaDirection deviceDirection,
            String illegalArgumentExceptionMessage)
            throws IllegalArgumentException {
        if ((direction != null) && !direction.and(deviceDirection).equals(direction))
            throw new IllegalArgumentException(illegalArgumentExceptionMessage);
    }

    /**
     * Fires a new <code>PropertyChangeEvent</code> to the <code>PropertyChangeListener</code>s registered
     * with this instance in order to notify about a change in the value of a specific property
     * which had its old value modified to a specific new value.
     *
     * @param property the name of the property of this instance which had its value changed
     * @param oldValue the value of the property with the specified name before the change
     * @param newValue the value of the property with the specified name after the change
     */
    protected void firePropertyChange(String property, Object oldValue, Object newValue) {
        propertyChangeSupport.firePropertyChange(property, oldValue, newValue);
    }

    /**
     * Returns the name of this stream or <code>null</code> if no name has been set. A stream name is
     * used by some protocols, for diagnostic purposes mostly. In XMPP for example this is the name
     * of the content element that describes a stream.
     *
     * @return the name of this stream or <code>null</code> if no name has been set.
     */
    @Override
    public String getName() {
        return name;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Object getProperty(String propertyName) {
        return properties.get(propertyName);
    }

    /**
     * Handles attributes contained in <code>MediaFormat</code>.
     *
     * @param format the <code>MediaFormat</code> to handle the attributes of
     * @param attrs the attributes <code>Map</code> to handle
     */
    protected void handleAttributes(MediaFormat format, Map<String, String> attrs) {
    }

    /**
     * Sends a given RTP or RTCP packet to the remote peer/side.
     *
     * @param pkt the packet to send.
     * @param data {@code true} to send an RTP packet or {@code false} to send an RTCP packet.
     *
     * @throws TransmissionFailedException if the transmission failed.
     */
    public void injectPacket(RawPacket pkt, boolean data)
            throws TransmissionFailedException {
        injectPacket(pkt, data, /* after */null);
    }

    /**
     * Removes the specified <code>PropertyChangeListener</code> from this stream so that it won't
     * receive further property change events.
     *
     * @param listener the <code>PropertyChangeListener</code> to remove
     *
     * @see MediaStream#removePropertyChangeListener(PropertyChangeListener)
     */
    @Override
    public void removePropertyChangeListener(PropertyChangeListener listener) {
        propertyChangeSupport.removePropertyChangeListener(listener);
    }

    /**
     * Sets the name of this stream. Stream names are used by some protocols, for diagnostic purposes mostly.
     * In XMPP for example this is the name of the content element that describes a stream.
     *
     * @param name the name of this stream or <code>null</code> if no name has been set.
     */
    @Override
    public void setName(String name) {
        this.name = name;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void setProperty(String propertyName, Object value) {
        if (value == null)
            properties.remove(propertyName);
        else
            properties.put(propertyName, value);
    }


    /**
     * {@inheritDoc}
     */
    @Override
    public void setRTPTranslator(RTPTranslator rtpTranslator) {
        if (this.rtpTranslator != rtpTranslator) {
            this.rtpTranslator = rtpTranslator;
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public RTPTranslator getRTPTranslator() {
        return rtpTranslator;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public TransformEngineChain getTransformEngineChain() {
        return null;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public byte getDynamicRTPPayloadType(String codec) {
        return -1;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public MediaStreamTrackReceiver getMediaStreamTrackReceiver() {
        return null;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public MediaFormat getFormat(byte pt) {
        return null;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void setTransportCCEngine(TransportCCEngine engine) {
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public REDBlock getPrimaryREDBlock(RawPacket pkt) {
        return null;
    }
}
