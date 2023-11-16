/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license.
 * See terms of license at gnu.org.
 */
package net.java.sip.communicator.service.gui.call;

import net.java.sip.communicator.service.protocol.CallPeerState;
import net.java.sip.communicator.service.protocol.event.CallPeerSecurityNegotiationStartedEvent;
import net.java.sip.communicator.service.protocol.event.CallPeerSecurityOffEvent;
import net.java.sip.communicator.service.protocol.event.CallPeerSecurityOnEvent;
import net.java.sip.communicator.service.protocol.event.CallPeerSecurityTimeoutEvent;

/**
 * The <code>CallPeerRenderer</code> interface is meant to be implemented by
 * different renderers of <code>CallPeer</code>s. Through this interface they would
 * could be updated in order to reflect the current state of the CallPeer.
 *
 * @author Yana Stamcheva
 * @author Lyubomir Marinov
 * @author Eng Chong Meng
 */
public interface CallPeerRenderer
{
    /**
     * Releases the resources (which require explicit disposal) acquired by this
     * <code>CallPeerRenderer</code> throughout its lifetime and prepares it for garbage collection.
     */
    void dispose();

    /**
     * Returns the parent call renderer.
     *
     * @return the parent call renderer
     */
    CallRenderer getCallRenderer();

    /**
     * Indicates if the local video component is currently visible.
     *
     * @return <code>true</code> if the local video component is currently visible, <code>false</code> - otherwise
     */
    boolean isLocalVideoVisible();

    /**
     * Prints the given DTMG character through this <code>CallPeerRenderer</code>.
     *
     * @param dtmfChar the DTMF char to print
     */
    void printDTMFTone(char dtmfChar);

    /**
     * The handler for the security event received. The security event
     * for starting establish a secure connection.
     *
     * @param securityStartedEvent the security started event received
     */
    void securityNegotiationStarted(CallPeerSecurityNegotiationStartedEvent securityStartedEvent);

    /**
     * Indicates that the security is turned off.
     *
     * @param evt Details about the event that caused this message.
     */
    void securityOff(CallPeerSecurityOffEvent evt);

    /**
     * Indicates that the security is turned on.
     *
     * @param evt Details about the event that caused this message.
     */
    void securityOn(CallPeerSecurityOnEvent evt);

    /**
     * Indicates that the security status is pending confirmation.
     */
    void securityPending();

    /**
     * Indicates that the security is timeouted, is not supported by the other end.
     *
     * @param evt Details about the event that caused this message.
     */
    void securityTimeout(CallPeerSecurityTimeoutEvent evt);

    /**
     * Sets the reason of a call failure if one occurs. The renderer should
     * display this reason to the user.
     *
     * @param reason the reason of the error to set
     */
    void setErrorReason(String reason);

    /**
     * Shows/hides the local video component.
     *
     * @param visible <code>true</code> to show the local video or <code>false</code> to hide it
     */
    void setLocalVideoVisible(boolean visible);

    /**
     * Sets the mute property value.
     *
     * @param mute <code>true</code> to mute the <code>CallPeer</code> depicted by this
     * instance; <code>false</code>, otherwise
     */
    void setMute(boolean mute);

    /**
     * Sets the "on hold" property value.
     *
     * @param onHold <code>true</code> to put the <code>CallPeer</code> depicted by this
     * instance on hold; <code>false</code>, otherwise
     */
    void setOnHold(boolean onHold);

    /**
     * Sets the <code>image</code> of the peer.
     *
     * @param image the image to set
     */
    void setPeerImage(byte[] image);

    /**
     * Sets the name of the peer.
     *
     * @param name the name of the peer
     */
    void setPeerName(String name);

    /**
     * Sets the state of the contained call peer by specifying the state name.
     *
     * @param oldState the previous state of the peer
     * @param newState the new state of the peer
     * @param stateString the state of the contained call peer
     */
    void setPeerState(CallPeerState oldState, CallPeerState newState, String stateString);

    /**
     * Shows/hides the security panel.
     *
     * @param visible <code>true</code> to show the security panel or <code>false</code> to hide it
     */
    void setSecurityPanelVisible(boolean visible);
    /**
     * Enable or disable DTMF tone handle
     * @param enabled - if true DTMF tone is enabled and disabled if false
     */
    void setDtmfToneEnabled(boolean enabled);

    /**
     * @return true if DTMF handling enabled
     */
    boolean isDtmfToneEnabled();
}
