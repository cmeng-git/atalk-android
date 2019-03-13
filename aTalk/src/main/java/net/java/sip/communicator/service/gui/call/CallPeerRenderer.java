/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license.
 * See terms of license at gnu.org.
 */
package net.java.sip.communicator.service.gui.call;

import net.java.sip.communicator.service.protocol.CallPeerState;
import net.java.sip.communicator.service.protocol.event.*;

/**
 * The <tt>CallPeerRenderer</tt> interface is meant to be implemented by
 * different renderers of <tt>CallPeer</tt>s. Through this interface they would
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
     * <tt>CallPeerRenderer</tt> throughout its lifetime and prepares it for garbage collection.
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
     * @return <tt>true</tt> if the local video component is currently visible, <tt>false</tt> - otherwise
     */
    boolean isLocalVideoVisible();

    /**
     * Prints the given DTMG character through this <tt>CallPeerRenderer</tt>.
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
     * @param visible <tt>true</tt> to show the local video or <tt>false</tt> to hide it
     */
    void setLocalVideoVisible(boolean visible);

    /**
     * Sets the mute property value.
     *
     * @param mute <tt>true</tt> to mute the <tt>CallPeer</tt> depicted by this
     * instance; <tt>false</tt>, otherwise
     */
    void setMute(boolean mute);

    /**
     * Sets the "on hold" property value.
     *
     * @param onHold <tt>true</tt> to put the <tt>CallPeer</tt> depicted by this
     * instance on hold; <tt>false</tt>, otherwise
     */
    void setOnHold(boolean onHold);

    /**
     * Sets the <tt>image</tt> of the peer.
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
     * @param visible <tt>true</tt> to show the security panel or <tt>false</tt> to hide it
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
