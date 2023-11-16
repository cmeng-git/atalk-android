/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.atalk.service.neomedia;

import org.atalk.service.neomedia.control.KeyFrameControl;
import org.atalk.service.neomedia.rtp.BandwidthEstimator;
import org.atalk.util.event.VideoListener;

import java.awt.Component;
import java.util.List;
import java.util.Map;

/**
 * Extends the <code>MediaStream</code> interface and adds methods specific to video streaming.
 *
 * @author Emil Ivov
 * @author Lyubomir Marinov
 */
public interface VideoMediaStream extends MediaStream
{
    /**
     * The name of the property used to control whether {@link VideoMediaStream} should request
     * retransmissions for lost RTP packets using RTCP NACK.
     */
    String REQUEST_RETRANSMISSIONS_PNAME = VideoMediaStream.class.getName() + ".REQUEST_RETRANSMISSIONS";

    /**
     * Adds a specific <code>VideoListener</code> to this <code>VideoMediaStream</code> in order to receive
     * notifications when visual/video <code>Component</code>s are being added and removed.
     * <p>
     * Adding a listener which has already been added does nothing i.e. it is not added more than
     * once and thus does not receive one and the same <code>VideoEvent</code> multiple times
     * </p>
     *
     * @param listener the <code>VideoListener</code> to be notified when visual/video <code>Component</code>s are
     * being added or removed in this <code>VideoMediaStream</code>
     */
    void addVideoListener(VideoListener listener);

    /**
     * Gets the <code>KeyFrameControl</code> of this <code>VideoMediaStream</code>.
     *
     * @return the <code>KeyFrameControl</code> of this <code>VideoMediaStream</code>
     */
    KeyFrameControl getKeyFrameControl();

    /**
     * Gets the visual <code>Component</code>, if any, depicting the video streamed from the local peer
     * to the remote peer.
     *
     * @return the visual <code>Component</code> depicting the local video if local video is actually
     * being streamed from the local peer to the remote peer; otherwise, <code>null</code>
     */
    Component getLocalVisualComponent();

    /**
     * Gets the <code>QualityControl</code> of this <code>VideoMediaStream</code>.
     *
     * @return the <code>QualityControl</code> of this <code>VideoMediaStream</code>
     */
    QualityControl getQualityControl();

    /**
     * Gets the visual <code>Component</code> where video from the remote peer is being rendered or
     * <code>null</code> if no video is currently being rendered.
     *
     * @return the visual <code>Component</code> where video from the remote peer is being rendered or
     * <code>null</code> if no video is currently being rendered
     * @deprecated Since multiple videos may be received from the remote peer and rendered, it is
     * not clear which one of them is to be singled out as the return value. Thus
     * {@link #getVisualComponent(long)} and {@link #getVisualComponents()} are to be used instead.
     */
    @Deprecated
    Component getVisualComponent();

    /**
     * Gets the visual <code>Component</code> rendering the <code>ReceiveStream</code> with a specific SSRC.
     *
     * @param ssrc the SSRC of the <code>ReceiveStream</code> to get the associated rendering visual <code>Component</code> of
     * @return the visual <code>Component</code> rendering the <code>ReceiveStream</code> with the specified
     * <code>ssrc</code> if any; otherwise, <code>null</code>
     */
    Component getVisualComponent(long ssrc);

    /**
     * Gets a list of the visual <code>Component</code>s where video from the remote peer is being rendered.
     *
     * @return a list of the visual <code>Component</code>s where video from the remote peer is being rendered
     */
    List<Component> getVisualComponents();

    /**
     * Move origin of a partial desktop streaming <code>MediaDevice</code>.
     *
     * @param x new x coordinate origin
     * @param y new y coordinate origin
     */
    void movePartialDesktopStreaming(int x, int y);

    /**
     * Removes a specific <code>VideoListener</code> from this <code>VideoMediaStream</code> in order to have to
	 * no longer receive notifications when visual/video <code>Component</code>s are being added and removed.
     *
     * @param listener the <code>VideoListener</code> to no longer be notified when visual/video
     * <code>Component</code>s are being added or removed in this <code>VideoMediaStream</code>
     */
    void removeVideoListener(VideoListener listener);

    /**
     * Updates the <code>QualityControl</code> of this <code>VideoMediaStream</code>.
     *
     * @param advancedParams parameters of advanced attributes that may affect quality control
     */
    void updateQualityControl(Map<String, String> advancedParams);

    /**
     * Creates an instance of {@link BandwidthEstimator} for this {@link MediaStream} if one doesn't
     * already exist. Returns the instance.
     */
    BandwidthEstimator getOrCreateBandwidthEstimator();
}
