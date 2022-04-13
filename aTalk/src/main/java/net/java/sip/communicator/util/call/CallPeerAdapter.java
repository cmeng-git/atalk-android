/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license.
 * See terms of license at gnu.org.
 */
package net.java.sip.communicator.util.call;

import net.java.sip.communicator.service.gui.call.CallPeerRenderer;
import net.java.sip.communicator.service.protocol.CallPeer;
import net.java.sip.communicator.service.protocol.CallPeerState;
import net.java.sip.communicator.service.protocol.event.CallPeerChangeEvent;
import net.java.sip.communicator.service.protocol.event.CallPeerSecurityListener;
import net.java.sip.communicator.service.protocol.event.CallPeerSecurityMessageEvent;
import net.java.sip.communicator.service.protocol.event.CallPeerSecurityNegotiationStartedEvent;
import net.java.sip.communicator.service.protocol.event.CallPeerSecurityOffEvent;
import net.java.sip.communicator.service.protocol.event.CallPeerSecurityOnEvent;
import net.java.sip.communicator.service.protocol.event.CallPeerSecurityTimeoutEvent;

import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;


/**
 * <code>CallPeerAdapter</code> implements common <code>CallPeer</code> related
 * listeners in order to facilitate the task of implementing
 * <code>CallPeerRenderer</code>.
 *
 * @author Yana Stamcheva
 * @author Lyubomir Marinov
 */
public class CallPeerAdapter extends net.java.sip.communicator.service.protocol.event.CallPeerAdapter
        implements CallPeerSecurityListener, PropertyChangeListener
{
    /**
     * The <code>CallPeer</code> which is depicted by {@link #renderer}.
     */
    private final CallPeer peer;

    /**
     * The <code>CallPeerRenderer</code> which is facilitated by this instance.
     */
    private final CallPeerRenderer renderer;

    /**
     * Initializes a new <code>CallPeerAdapter</code> instance which is to listen to
     * a specific <code>CallPeer</code> on behalf of a specific
     * <code>CallPeerRenderer</code>. The new instance adds itself to the specified
     * <code>CallPeer</code> as a listener for each of the implemented listener
     * types.
     *
     * @param peer the <code>CallPeer</code> which the new instance is to listen to
     * on behalf of the specified <code>renderer</code>
     * @param renderer the <code>CallPeerRenderer</code> which is to be facilitated
     * by the new instance
     */
    public CallPeerAdapter(CallPeer peer, CallPeerRenderer renderer)
    {
        this.peer = peer;
        this.renderer = renderer;

        this.peer.addCallPeerListener(this);
        this.peer.addCallPeerSecurityListener(this);
        this.peer.addPropertyChangeListener(this);
    }

    /**
     * Removes the listeners implemented by this instance from the associated
     * <code>CallPeer</code> and prepares it for garbage collection.
     */
    public void dispose()
    {
        peer.removeCallPeerListener(this);
        peer.removeCallPeerSecurityListener(this);
        peer.removePropertyChangeListener(this);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void peerDisplayNameChanged(CallPeerChangeEvent ev)
    {
        if (peer.equals(ev.getSourceCallPeer()))
            renderer.setPeerName((String) ev.getNewValue());
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void peerImageChanged(CallPeerChangeEvent ev)
    {
        if (peer.equals(ev.getSourceCallPeer()))
            renderer.setPeerImage((byte[]) ev.getNewValue());
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void peerStateChanged(CallPeerChangeEvent ev)
    {
        CallPeer sourcePeer = ev.getSourceCallPeer();

        if (!sourcePeer.equals(peer))
            return;

        CallPeerState newState = (CallPeerState) ev.getNewValue();
        CallPeerState oldState = (CallPeerState) ev.getOldValue();

        String newStateString = sourcePeer.getState().getLocalizedStateString();

        if (newState == CallPeerState.CONNECTED) {
            if (!CallPeerState.isOnHold(oldState)) {
                if (!renderer.getCallRenderer().isCallTimerStarted())
                    renderer.getCallRenderer().startCallTimer();
            }
            else {
                renderer.setOnHold(false);
                renderer.getCallRenderer().updateHoldButtonState();
            }
        }
        else if (newState == CallPeerState.DISCONNECTED) {
            // The call peer should be already removed from the call
            // see CallPeerRemoved
        }
        else if (newState == CallPeerState.FAILED) {
            // The call peer should be already removed from the call
            // see CallPeerRemoved
        }
        else if (CallPeerState.isOnHold(newState)) {
            renderer.setOnHold(true);
            renderer.getCallRenderer().updateHoldButtonState();
        }

        renderer.setPeerState(oldState, newState, newStateString);

        String reasonString = ev.getReasonString();
        if (reasonString != null)
            renderer.setErrorReason(reasonString);
    }

    /**
     * {@inheritDoc}
     */
    public void propertyChange(PropertyChangeEvent ev)
    {
        String propertyName = ev.getPropertyName();

        if (propertyName.equals(CallPeer.MUTE_PROPERTY_NAME)) {
            boolean mute = (Boolean) ev.getNewValue();

            renderer.setMute(mute);
        }
    }

    /**
     * {@inheritDoc}
     *
     * <code>CallPeerAdapter</code> does nothing.
     */
    public void securityMessageReceived(CallPeerSecurityMessageEvent event)
    {
    }

    /**
     * {@inheritDoc}
     */
    public void securityNegotiationStarted(
            CallPeerSecurityNegotiationStartedEvent ev)
    {
        if (peer.equals(ev.getSource()))
            renderer.securityNegotiationStarted(ev);
    }

    /**
     * {@inheritDoc}
     */
    public void securityOff(CallPeerSecurityOffEvent ev)
    {
        if (peer.equals(ev.getSource()))
            renderer.securityOff(ev);
    }

    /**
     * {@inheritDoc}
     */
    public void securityOn(CallPeerSecurityOnEvent ev)
    {
        if (peer.equals(ev.getSource()))
            renderer.securityOn(ev);
    }

    /**
     * {@inheritDoc}
     */
    public void securityTimeout(CallPeerSecurityTimeoutEvent ev)
    {
        if (peer.equals(ev.getSource()))
            renderer.securityTimeout(ev);
    }
}