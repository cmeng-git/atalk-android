/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license.
 * See terms of license at gnu.org.
 */
package net.java.sip.communicator.service.gui.call;

import net.java.sip.communicator.service.protocol.CallPeer;

/**
 * The <code>CallRenderer</code> represents a renderer for a call. All user
 * interfaces representing a call should implement this interface.
 *
 * @author Yana Stamcheva
 * @author Eng Chong Meng
 */
public interface CallRenderer
{
    /**
     * Releases the resources acquired by this instance which require explicit
     * disposal (e.g. any listeners added to the depicted
     * <code>CallConference</code>, the participating <code>Call</code>s, and their
     * associated <code>CallPeer</code>s). Invoked by <code>CallPanel</code> when it
     * determines that this <code>CallRenderer</code> is no longer necessary.
     */
    void dispose();

    /**
     * Returns the <code>CallPeerRenderer</code> corresponding to the given <code>callPeer</code>.
     *
     * @param callPeer the <code>CallPeer</code>, for which we're looking for a renderer
     * @return the <code>CallPeerRenderer</code> corresponding to the given <code>callPeer</code>
     */
    CallPeerRenderer getCallPeerRenderer(CallPeer callPeer);

    /**
     * Starts the timer that counts call duration.
     */
    void startCallTimer();

    /**
     * Stops the timer that counts call duration.
     */
    void stopCallTimer();

    /**
     * Returns {@code true} if the call timer has been started, otherwise returns {@code false}.
     * @return {@code true} if the call timer has been started, otherwise returns {@code false}
     */
    boolean isCallTimerStarted();

    /**
     * Updates the state of the general hold button. The hold button is selected
     * only if all call peers are locally or mutually on hold at the same time.
     * In all other cases the hold button is unselected.
     */
    void updateHoldButtonState();
}
