/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 * 
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package net.java.sip.communicator.service.protocol;

import org.atalk.service.neomedia.MediaDirection;

import java.beans.PropertyChangeListener;

/**
 * Represents a member and its details in a telephony conference managed by a <code>CallPeer</code> in
 * its role as a conference focus.
 *
 * @author Lyubomir Marinov
 */
public interface ConferenceMember
{
	/**
	 * The name of the property of <code>ConferenceMember</code> which specifies the SSRC of the audio
	 * content/RTP stream sent by the respective <code>ConferenceMember</code> in the conference.
	 */
	public static final String AUDIO_SSRC_PROPERTY_NAME = "audioSsrc";

	/**
	 * The name of the property of <code>ConferenceMember</code> which specifies the status of the audio
	 * RTP stream from the point of view of the <code>ConferenceMember</code>.
	 */
	public static final String AUDIO_STATUS_PROPERTY_NAME = "audioStatus";

	/**
	 * The name of the property of <code>ConferenceMember</code> which specifies the user-friendly
	 * display name of the respective <code>ConferenceMember</code> in the conference.
	 */
	public static final String DISPLAY_NAME_PROPERTY_NAME = "displayName";

	/**
	 * The name of the property of <code>ConferenceMember</code> which specifies the state of the device
	 * and signaling session of the respective <code>ConferenceMember</code> in the conference.
	 */
	public static final String STATE_PROPERTY_NAME = "state";

	/**
	 * The name of the property of <code>ConferenceMember</code> which specifies the SSRC of the video
	 * content/RTP stream sent by the respective <code>ConferenceMember</code> in the conference.
	 */
	public static final String VIDEO_SSRC_PROPERTY_NAME = "videoSsrc";

	/**
	 * The name of the property of <code>ConferenceMember</code> which specifies the status of the video
	 * RTP stream from the point of view of the <code>ConferenceMember</code>.
	 */
	public static final String VIDEO_STATUS_PROPERTY_NAME = "videoStatus";

	/**
	 * Adds a specific <code>PropertyChangeListener</code> to the list of listeners interested in and
	 * notified about changes in the values of the properties of this <code>ConferenceMember</code> such
	 * as <code>#DISPLAY_NAME_PROPERTY_NAME</code> and <code>#STATE_PROPERTY_NAME</code>.
	 *
	 * @param listener
	 *        a <code>PropertyChangeListener</code> to be notified about changes in the values of the
	 *        properties of this <code>ConferenceMember</code>. If the specified listener is already in
	 *        the list of interested listeners (i.e. it has been previously added), it is not added
	 *        again.
	 */
	public void addPropertyChangeListener(PropertyChangeListener listener);

	/**
	 * Gets the SIP address of this <code>ConferenceMember</code> as specified by the conference-info
	 * XML received from its <code>conferenceFocusCallPeer</code>.
	 *
	 * @return the SIP address of this <code>ConferenceMember</code> as specified by the conference-info
	 *         XML received from its <code>conferenceFocusCallPeer</code>
	 */
	public String getAddress();

	/**
	 * Returns the SSRC of the audio content/RTP stream sent by this <code>ConferenceMember</code> in
	 * the conference or <code>-1</code> if such information is not currently available.
	 *
	 * @return the SSRC of the audio content/RTP stream sent by this <code>ConferenceMember</code> in
	 *         the conference or <code>-1</code> if such information is not currently available
	 */
	public long getAudioSsrc();

	/**
	 * Gets the status in both directions of the audio RTP stream from the point of view of this
	 * <code>ConferenceMember</code>.
	 *
	 * @return a <code>MediaDIrection</code> which represents the status in both directions of the audio
	 *         RTP stream from the point of view of this <code>ConferenceMember</code>
	 */
	public MediaDirection getAudioStatus();

	/**
	 * Gets the <code>CallPeer</code> which is the conference focus of this <code>ConferenceMember</code>.
	 *
	 * @return the <code>CallPeer</code> which is the conference focus of this <code>ConferenceMember</code>
	 */
	public CallPeer getConferenceFocusCallPeer();

	/**
	 * Gets the user-friendly display name of this <code>ConferenceMember</code> in the conference.
	 *
	 * @return the user-friendly display name of this <code>ConferenceMember</code> in the conference
	 */
	public String getDisplayName();

	/**
	 * Gets the state of the device and signaling session of this <code>ConferenceMember</code> in the
	 * conference in the form of a <code>ConferenceMemberState</code> value.
	 *
	 * @return a <code>ConferenceMemberState</code> value which represents the state of the device and
	 *         signaling session of this <code>ConferenceMember</code> in the conference
	 */
	public ConferenceMemberState getState();

	/**
	 * Returns the SSRC of the video content/RTP stream sent by this <code>ConferenceMember</code> in
	 * the conference or <code>-1</code> if such information is not currently available.
	 *
	 * @return the SSRC of the video content/RTP stream sent by this <code>ConferenceMember</code> in
	 *         the conference or <code>-1</code> if such information is not currently available
	 */
	public long getVideoSsrc();

	/**
	 * Gets the status in both directions of the video RTP stream from the point of view of this
	 * <code>ConferenceMember</code>.
	 *
	 * @return a <code>MediaDIrection</code> which represents the status in both directions of the video
	 *         RTP stream from the point of view of this <code>ConferenceMember</code>
	 */
	public MediaDirection getVideoStatus();

	/**
	 * Removes a specific <code>PropertyChangeListener</code> from the list of listeners interested in
	 * and notified about changes in the values of the properties of this <code>ConferenceMember</code>
	 * such as <code>#DISPLAY_NAME_PROPERTY_NAME</code> and <code>#STATE_PROPERTY_NAME</code>.
	 *
	 * @param listener
	 *        a <code>PropertyChangeListener</code> to no longer be notified about changes in the values
	 *        of the properties of this <code>ConferenceMember</code>
	 */
	public void removePropertyChangeListener(PropertyChangeListener listener);
}
