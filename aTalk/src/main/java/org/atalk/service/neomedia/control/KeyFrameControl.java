/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 * 
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.atalk.service.neomedia.control;

import java.util.List;

/**
 * Represents a control over the key frame-related logic of a <code>VideoMediaStream</code>.
 *
 * @author Lyubomir Marinov
 */
public interface KeyFrameControl
{
	/**
	 * Adds a <code>KeyFrameRequestee</code> to be made available through this <code>KeyFrameControl</code>.
	 *
	 * @param index
	 *        the zero-based index at which <code>keyFrameRequestee</code> is to be added to the list of
	 *        <code>KeyFrameRequestee</code>s made available or <code>-1</code> to have this
	 *        <code>KeyFrameControl</code> choose at which index it is to be added in accord with its
	 *        internal logic through this <code>KeyFrameControl</code>
	 * @param keyFrameRequestee
	 *        the <code>KeyFrameRequestee</code> to be added to this <code>KeyFrameControl</code> so that it
	 *        is made available through it
	 */
	public void addKeyFrameRequestee(int index, KeyFrameRequestee keyFrameRequestee);

	/**
	 * Adds a <code>KeyFrameRequester</code> to be made available through this <code>KeyFrameControl</code>.
	 *
	 * @param index
	 *        the zero-based index at which <code>keyFrameRequester</code> is to be added to the list of
	 *        <code>KeyFrameRequester</code>s made available or <code>-1</code> to have this
	 *        <code>KeyFrameControl</code> choose at which index it is to be added in accord with its
	 *        internal logic through this <code>KeyFrameControl</code>
	 * @param keyFrameRequester
	 *        the <code>KeyFrameRequester</code> to be added to this <code>KeyFrameControl</code> so that it
	 *        is made available through it
	 */
	public void addKeyFrameRequester(int index, KeyFrameRequester keyFrameRequester);

	/**
	 * Gets the <code>KeyFrameRequestee</code>s made available through this <code>KeyFrameControl</code>.
	 *
	 * @return an unmodifiable list of <code>KeyFrameRequestee</code>s made available through this
	 *         <code>KeyFrameControl</code>
	 */
	public List<KeyFrameRequestee> getKeyFrameRequestees();

	/**
	 * Gets the <code>KeyFrameRequester</code>s made available through this <code>KeyFrameControl</code>.
	 *
	 * @return an unmodifiable list of <code>KeyFrameRequester</code>s made available through this
	 *         <code>KeyFrameControl</code>
	 */
	public List<KeyFrameRequester> getKeyFrameRequesters();

	/**
	 * Notifies this <code>KeyFrameControl</code> that the remote peer of the associated
	 * <code>VideoMediaStream</code> has requested a key frame from the local peer.
	 *
	 * @return <code>true</code> if the local peer has honored the request from the remote peer for a
	 *         key frame; otherwise, <code>false</code>
	 */
	public boolean keyFrameRequest();

	/**
	 * Removes a <code>KeyFrameRequestee</code> to no longer be made available through this
	 * <code>KeyFrameControl</code>.
	 *
	 * @param keyFrameRequestee
	 *        the <code>KeyFrameRequestee</code> to be removed from this <code>KeyFrameControl</code> so
	 *        that it is no longer made available through it
	 * @return <code>true</code> if <code>keyFrameRequestee</code> was found in this
	 *         <code>KeyFrameControl</code>; otherwise, <code>false</code>
	 */
	public boolean removeKeyFrameRequestee(KeyFrameRequestee keyFrameRequestee);

	/**
	 * Removes a <code>KeyFrameRequester</code> to no longer be made available through this
	 * <code>KeyFrameControl</code>.
	 *
	 * @param keyFrameRequester
	 *        the <code>KeyFrameRequester</code> to be removed from this <code>KeyFrameControl</code> so
	 *        that it is no longer made available through it
	 * @return <code>true</code> if <code>keyFrameRequester</code> was found in this
	 *         <code>KeyFrameControl</code>; otherwise, <code>false</code>
	 */
	public boolean removeKeyFrameRequester(KeyFrameRequester keyFrameRequester);

	/**
	 * Requests a key frame from the remote peer of the associated <code>VideoMediaStream</code>.
	 *
	 * @param urgent
	 *        <code>true</code> if the caller has determined that the need for a key frame is urgent and
	 *        should not obey all constraints with respect to time between two subsequent requests
	 *        for key frames
	 * @return <code>true</code> if a key frame was indeed requested from the remote peer of the
	 *         associated <code>VideoMediaStream</code> in response to the call; otherwise,
	 *         <code>false</code>
	 */
	public boolean requestKeyFrame(boolean urgent);

	/**
	 * Represents a way for the remote peer of a <code>VideoMediaStream</code> to request a key frame
	 * from its local peer.
	 *
	 * @author Lyubomir Marinov
	 */
	public interface KeyFrameRequestee
	{
		/**
		 * Notifies this <code>KeyFrameRequestee</code> that the remote peer of the associated
		 * <code>VideoMediaStream</code> requests a key frame from the local peer.
		 *
		 * @return <code>true</code> if this <code>KeyFrameRequestee</code> has honored the request for a
		 *         key frame; otherwise, <code>false</code>
		 */
		public boolean keyFrameRequest();
	}

	/**
	 * Represents a way for a <code>VideoMediaStream</code> to request a key frame from its remote peer.
	 *
	 * @author Lyubomir Marinov
	 */
	public interface KeyFrameRequester
	{
		/**
		 * The name of the <code>ConfigurationService</code> property which specifies the preferred
		 * <code>KeyFrameRequester</code> to be used.
		 */
		public static final String PREFERRED_PNAME
				= "neomedia.codec.video.h264.preferredKeyFrameRequester";

		/**
		 * The value of the {@link #PREFERRED_PNAME} <code>ConfigurationService</code> property which
		 * indicates that the RTCP <code>KeyFrameRequester</code> is preferred.
		 */
		public static final String RTCP = "rtcp";

		/**
		 * The value of the {@link #PREFERRED_PNAME} <code>ConfigurationService</code> property which
		 * indicates that the signaling/protocol <code>KeyFrameRequester</code> is preferred.
		 */
		public static final String SIGNALING = "signaling";

		/**
		 * The default value of the {@link #PREFERRED_PNAME} <code>ConfigurationService</code> property.
		 */
		public static final String DEFAULT_PREFERRED = RTCP;

		/**
		 * Requests a key frame from the remote peer of the associated <code>VideoMediaStream</code>.
		 *
		 * @return <code>true</code> if this <code>KeyFrameRequester</code> has indeed requested a key frame
		 *         from the remote peer of the associated <code>VideoMediaStream</code> in response to
		 *         the call; otherwise, <code>false</code>
		 */
		public boolean requestKeyFrame();
	}
}
