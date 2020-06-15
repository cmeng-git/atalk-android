/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package net.java.sip.communicator.service.protocol;

import net.java.sip.communicator.service.protocol.event.*;

import org.jxmpp.jid.Jid;

import java.beans.PropertyChangeListener;
import java.net.URL;
import java.util.List;

/**
 * The CallPeer is an interface that represents peers in a call. Users of the UIService need to
 * implement this interface (or one of its default implementations such DefaultCallPeer) in order to
 * be able to register call peer in the user interface.
 *
 * <p>
 * For SIP calls for example, it would be necessary to create a CallPeerSipImpl class that would
 * provide sip specific implementations of various methods (getAddress() for example would return
 * the peer's sip URI).
 *
 * @author Emil Ivov
 * @author Lyubomir Marinov
 * @author Yana Stamcheva
 * @author Eng Chong Meng
 */
public interface CallPeer
{
    /**
     * The constant indicating that a <tt>CallPeer</tt> has not yet transitioned into a state
     * marking the beginning of a participation in a <tt>Call</tt> or that such a transition may
     * have happened but the time of its occurrence is unknown.
     */
    public static long CALL_DURATION_START_TIME_UNKNOWN = 0;

    /**
     * The mute property name.
     */
    public static final String MUTE_PROPERTY_NAME = "Mute";

    /**
     * Adds a specific <tt>CallPeerConferenceListener</tt> to the list of listeners interested in
     * and notified about changes in conference-related information such as this peer acting or not
     * acting as a conference focus and conference membership details.
     *
     * @param listener a <tt>CallPeerConferenceListener</tt> to be notified about changes in
     * conference-related information. If the specified listener is already in the list of
     * interested listeners (i.e. it has been previously added), it is not added again.
     */
    void addCallPeerConferenceListener(CallPeerConferenceListener listener);

    /**
     * Allows the user interface to register a listener interested in changes
     *
     * @param listener a listener instance to register with this peer.
     */
    void addCallPeerListener(CallPeerListener listener);

    /**
     * Allows the user interface to register a listener interested in security status changes.
     *
     * @param listener a listener instance to register with this peer
     */
    void addCallPeerSecurityListener(CallPeerSecurityListener listener);

    /**
     * Adds a specific <tt>SoundLevelListener</tt> to the list of listeners interested in and
     * notified about changes in conference members sound level.
     *
     * @param listener the <tt>SoundLevelListener</tt> to add
     */
    void addConferenceMembersSoundLevelListener(ConferenceMembersSoundLevelListener listener);

    /**
     * Allows the user interface to register a listener interested in property changes.
     *
     * @param listener a property change listener instance to register with this peer.
     */
    void addPropertyChangeListener(PropertyChangeListener listener);

    /**
     * Adds a specific <tt>SoundLevelListener</tt> to the list of listeners interested in and
     * notified about changes in stream sound level related information.
     *
     * @param listener the <tt>SoundLevelListener</tt> to add
     */
    void addStreamSoundLevelListener(SoundLevelListener listener);

    /**
     * Returns a String locator for that peer. A locator might be a SIP URI, an IP address or a telephone number.
     *
     * @return the peer's address or phone number.
     */
    String getAddress();

    /**
     * Returns a reference to the call that this peer belongs to.
     *
     * @return a reference to the call containing this peer.
     */
    Call getCall();

    /**
     * Gets the time at which this <tt>CallPeer</tt> transitioned into a state (likely
     * {@link CallPeerState#CONNECTED}) marking the start of the duration of the participation in a <tt>Call</tt>.
     *
     * @return the time at which this <tt>CallPeer</tt> transitioned into a state marking the start
     * of the duration of the participation in a <tt>Call</tt> or
     * {@link #CALL_DURATION_START_TIME_UNKNOWN} if such a transition has not been performed
     */
    long getCallDurationStartTime();

    /**
     * Returns a URL pointing to a location with call control information or null if such an URL is
     * not available for the current call peer.
     *
     * @return a URL link to a location with call information or a call control web interface
     * related to this peer or <tt>null</tt> if no such URL is available.
     */
    URL getCallInfoURL();

    /**
     * Gets the number of <tt>ConferenceMember</tt>s currently known to this peer if it is acting as
     * a conference focus.
     *
     * @return the number of <tt>ConferenceMember</tt>s currently known to this peer if it is acting
     * as a conference focus. If this peer is not acting as a conference focus or it does
     * but there are currently no members in the conference it manages, a value of zero is
     * returned.
     */
    int getConferenceMemberCount();

    /**
     * Gets the <tt>ConferenceMember</tt>s currently known to this peer if it is acting as a
     * conference focus.
     *
     * @return a <tt>List</tt> of <tt>ConferenceMember</tt>s describing the members of a conference
     * managed by this peer if it is acting as a conference focus. If this peer is not
     * acting as a conference focus or it does but there are currently no members in the
     * conference it manages, an empty <tt>List</tt> is returned.
     */
    List<ConferenceMember> getConferenceMembers();

    /**
     * Returns the contact corresponding to this peer or null if no particular contact has been  associated.
     * <p>
     *
     * @return the <tt>Contact</tt> corresponding to this peer or null if no particular contact has
     * been associated.
     */
    Contact getContact();

    /**
     * Returns the currently used security settings of this <tt>CallPeer</tt>.
     *
     * @return the <tt>CallPeerSecurityStatusEvent</tt> that contains the current security settings.
     */
    CallPeerSecurityStatusEvent getCurrentSecuritySettings();

    /**
     * Returns a human readable name representing this peer.
     *
     * @return a String containing a name for that peer.
     */
    String getDisplayName();

    /**
     * Returns an alternative IMPP address corresponding to this <tt>CallPeer</tt>.
     *
     * @return a string representing an alternative IMPP address corresponding to this <tt>CallPeer</tt>
     */
    String getAlternativeIMPPAddress();

    /**
     * The method returns an image representation of the call peer (e.g. a photo). Generally, the
     * image representation is acquired from the underlying telephony protocol and is transferred
     * over the network during call negotiation.
     *
     * @return byte[] a byte array containing the image or null if no image is available.
     */
    byte[] getImage();

    /**
     * Returns a unique identifier representing this peer. Identifiers returned by this method
     * should remain unique across calls. In other words, if it returned the value of "A" for a
     * given peer it should not return that same value for any other peer and return a different
     * value even if the same person (address) is participating in another call. Values need not
     * remain unique after restarting the program.
     *
     * @return an identifier representing this call peer.
     */
    String getPeerID();

    Jid getPeerJid();

    /**
     * Returns the protocol provider that this peer belongs to.
     *
     * @return a reference to the ProtocolProviderService that this peer belongs to.
     */
    ProtocolProviderService getProtocolProvider();

    /**
     * Returns an object representing the current state of that peer. CallPeerState may vary among
     * CONNECTING, RINGING, CALLING, BUSY, CONNECTED, and others, and it reflects the state of the
     * connection between us and that peer.
     *
     * @return a CallPeerState instance representing the peer's state.
     */
    CallPeerState getState();

    /**
     * Returns full URI of the address. For example sip:user@domain.org or xmpp:user@domain.org.
     *
     * @return full URI of the address
     */
    String getURI();

    /**
     * Determines whether this peer is acting as a conference focus and thus may provide information
     * about <tt>ConferenceMember</tt> such as {@link #getConferenceMembers()} and {@link #getConferenceMemberCount()}.
     *
     * @return <tt>true</tt> if this peer is acting as a conference focus; <tt>false</tt>, otherwise
     */
    boolean isConferenceFocus();

    /**
     * Determines whether the audio stream (if any) being sent to this peer is mute.
     *
     * @return <tt>true</tt> if an audio stream is being sent to this peer and it is currently mute; <tt>false</tt>, otherwise
     */
    boolean isMute();

    /**
     * Removes a specific <tt>CallPeerConferenceListener</tt> from the list of listeners interested
     * in and notified about changes in conference-related information such as this peer acting or
     * not acting as a conference focus and conference membership details.
     *
     * @param listener a <tt>CallPeerConferenceListener</tt> to no longer be notified about changes in
     * conference-related information
     */
    void removeCallPeerConferenceListener(CallPeerConferenceListener listener);

    /**
     * Unregisters the specified listener.
     *
     * @param listener the listener to unregister.
     */
    void removeCallPeerListener(CallPeerListener listener);

    /**
     * Unregisters the specified listener.
     *
     * @param listener the listener to unregister
     */
    void removeCallPeerSecurityListener(CallPeerSecurityListener listener);

    /**
     * Removes a specific <tt>SoundLevelListener</tt> of the list of listeners interested in and
     * notified about changes in conference members sound level.
     *
     * @param listener the <tt>SoundLevelListener</tt> to remove
     */
    void removeConferenceMembersSoundLevelListener(ConferenceMembersSoundLevelListener listener);

    /**
     * Unregisters the specified property change listener.
     *
     * @param listener the property change listener to unregister.
     */
    void removePropertyChangeListener(PropertyChangeListener listener);

    /**
     * Removes a specific <tt>SoundLevelListener</tt> of the list of listeners interested in and
     * notified about changes in stream sound level related information.
     *
     * @param listener the <tt>SoundLevelListener</tt> to remove
     */
    void removeStreamSoundLevelListener(SoundLevelListener listener);

    /**
     * Returns a string representation of the peer in the form of <br>
     * Display Name &lt;address&gt;;status=CallPeerStatus
     *
     * @return a string representation of the peer and its state.
     */
    String toString();
}
