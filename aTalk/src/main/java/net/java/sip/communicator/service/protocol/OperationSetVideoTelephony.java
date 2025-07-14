/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package net.java.sip.communicator.service.protocol;

import org.atalk.service.neomedia.QualityControl;
import org.atalk.service.neomedia.QualityPreset;
import org.atalk.util.event.VideoListener;

import java.awt.Component;
import java.beans.PropertyChangeListener;
import java.text.ParseException;
import java.util.List;

/**
 * Represents an <code>OperationSet</code> giving access to video-specific functionality in telephony such as
 * visual <code>Component</code>s displaying video and listening to dynamic availability of such <code>Component</code>s.
 *
 * @author Lyubomir Marinov
 * @author Sebastien Vincent
 * @author Eng Chong Meng
 */
public interface OperationSetVideoTelephony extends OperationSet
{
    /**
     * Adds a specific <code>VideoListener</code> to this telephony in order to receive notifications
     * when visual/video <code>Component</code>s are being added and removed for a specific <code>CallPeer</code>.
     *
     * @param peer the <code>CallPeer</code> whose video the specified listener is to be notified about
     * @param listener the <code>VideoListener</code> to be notified when visual/video <code>Component</code>s are
     * being added or removed for <code>peer</code>
     */
    void addVideoListener(CallPeer peer, VideoListener listener);

    /**
     * Gets the visual <code>Component</code> which depicts the local video being streamed to a specific <code>CallPeer</code>.
     *
     * @param peer the <code>CallPeer</code> to whom the local video which is to be depicted by the returned
     * visual <code>Component</code> is being streamed
     * @return a visual <code>Component</code> which depicts the local video being streamed to the
     * specified <code>CallPeer</code> if this telephony chooses to carry out the creation
     * synchronously; <code>null</code> if this telephony chooses to create the requested visual
     * <code>Component</code> asynchronously
     * @throws OperationFailedException if creating the component fails for whatever reason.
     */
    Component getLocalVisualComponent(CallPeer peer)
            throws OperationFailedException;

    /**
     * Gets the visual/video <code>Component</code> available in this telephony for a specific <code>CallPeer</code>.
     *
     * @param peer the <code>CallPeer</code> whose video is to be retrieved
     * @return the visual/video <code>Component</code> available in this telephony for the specified
     * <code>peer</code> if any; otherwise, <code>null</code>
     */
    @Deprecated
    Component getVisualComponent(CallPeer peer);

    /**
     * Gets the visual/video <code>Component</code>s available in this telephony for a specific <code>CallPeer</code>.
     *
     * @param peer the <code>CallPeer</code> whose videos are to be retrieved
     * @return the visual/video <code>Component</code>s available in this telephony for the specified <code>peer</code>
     */
    List<Component> getVisualComponents(CallPeer peer);

    /**
     * Removes a specific <code>VideoListener</code> from this telephony in order to no longer have it
     * receive notifications when visual/video <code>Component</code>s are being added and removed for a
     * specific <code>CallPeer</code>.
     *
     * @param peer the <code>CallPeer</code> whose video the specified listener is to no longer be notified about
     * @param listener the <code>VideoListener</code> to no longer be notified when visual/video
     * <code>Component</code>s are being added or removed for <code>peer</code>
     */
    void removeVideoListener(CallPeer peer, VideoListener listener);

    /**
     * Sets the indicator which determines whether the streaming of local video in a specific
     * <code>Call</code> is allowed. The setting does not reflect the availability of actual video
     * capture devices, it just expresses the desire of the user to have the local video streamed in
     * the case the system is actually able to do so.
     *
     * @param call the <code>Call</code> to allow/disallow the streaming of local video for
     * @param allowed <code>true</code> to allow the streaming of local video for the specified <code>Call</code>;
     * <code>false</code> to disallow it
     * @throws OperationFailedException if initializing local video fails.
     */
    void setLocalVideoAllowed(Call call, boolean allowed)
            throws OperationFailedException;

    /**
     * Gets the indicator which determines whether the streaming of local video in a specific
     * <code>Call</code> is allowed. The setting does not reflect the availability of actual video
     * capture devices, it just expresses the desire of the user to have the local video streamed in
     * the case the system is actually able to do so.
     *
     * @param call the <code>Call</code> to get the indicator of
     * @return <code>true</code> if the streaming of local video for the specified <code>Call</code> is
     * allowed; otherwise, <code>false</code>
     */
    boolean isLocalVideoAllowed(Call call);

    /**
     * The property which indicates whether a specific <code>Call</code> is currently streaming the
     * local video (to a remote destination).
     */
    static final String LOCAL_VIDEO_STREAMING = "LOCAL_VIDEO_STREAMING";

    /**
     * Gets the indicator which determines whether a specific <code>Call</code> is currently streaming
     * the local video (to a remote destination).
     *
     * @param call the <code>Call</code> to get the indicator of
     * @return <code>true</code> if the specified <code>Call</code> is currently streaming the local video
     * (to a remote destination); otherwise, <code>false</code>
     */
    boolean isLocalVideoStreaming(Call call);

    /**
     * Adds a specific <code>PropertyChangeListener</code> to the list of listeners which get notified
     * when the properties (e.g. {@link #LOCAL_VIDEO_STREAMING}) associated with a specific
     * <code>Call</code> change their values.
     *
     * @param call the <code>Call</code> to start listening to the changes of the property values of
     * @param listener the <code>PropertyChangeListener</code> to be notified when the properties associated with
     * the specified <code>Call</code> change their values
     */
    void addPropertyChangeListener(Call call, PropertyChangeListener listener);

    /**
     * Removes a specific <code>PropertyChangeListener</code> from the list of listeners which get
     * notified when the properties (e.g. {@link #LOCAL_VIDEO_STREAMING}) associated with a specific
     * <code>Call</code> change their values.
     *
     * @param call the <code>Call</code> to stop listening to the changes of the property values of
     * @param listener the <code>PropertyChangeListener</code> to no longer be notified when the properties
     * associated with the specified <code>Call</code> change their values
     */
    void removePropertyChangeListener(Call call, PropertyChangeListener listener);

    /**
     * Create a new video call and invite the specified CallPeer to it.
     *
     * @param uri the address of the callee that we should invite to a new call.
     * @return CallPeer the CallPeer that will represented by the specified uri. All following state
     * change events will be delivered through that call peer. The Call that this peer is a
     * member of could be retrieved from the CallParticipatn instance with the use of the
     * corresponding method.
     * @throws OperationFailedException with the corresponding code if we fail to create the video call.
     * @throws ParseException if <code>callee</code> is not a valid sip address string.
     */
    Call createVideoCall(String uri)
            throws OperationFailedException, ParseException;

    /**
     * Create a new video call and invite the specified CallPeer to it.
     *
     * @param callee the address of the callee that we should invite to a new call.
     * @return CallPeer the CallPeer that will represented by the specified uri. All following state
     * change events will be delivered through that call peer. The Call that this peer is a
     * member of could be retrieved from the CallParticipatn instance with the use of the
     * corresponding method.
     * @throws OperationFailedException with the corresponding code if we fail to create the video call.
     */
    Call createVideoCall(Contact callee)
            throws OperationFailedException;

    /**
     * Create a new video call and invite the specified CallPeer to it.
     *
     * @param uri the address of the callee that we should invite to a new call.
     * @param qualityPreferences the quality preset we will use establishing the video call, and we will expect from
     * the other side. When establishing call we don't have any indications whether remote
     * part supports quality presets, so this setting can be ignored.
     * @return CallPeer the CallPeer that will represented by the specified uri. All following state
     * change events will be delivered through that call peer. The Call that this peer is a
     * member of could be retrieved from the CallParticipatn instance with the use of the
     * corresponding method.
     * @throws OperationFailedException with the corresponding code if we fail to create the video call.
     * @throws ParseException if <code>callee</code> is not a valid sip address string.
     */
    Call createVideoCall(String uri, QualityPreset qualityPreferences)
            throws OperationFailedException, ParseException;

    /**
     * Create a new video call and invite the specified CallPeer to it.
     *
     * @param callee the address of the callee that we should invite to a new call.
     * @param qualityPreferences the quality preset we will use establishing the video call, and we will expect from
     * the other side. When establishing call we don't have any indications whether remote
     * part supports quality presets, so this setting can be ignored.
     * @return CallPeer the CallPeer that will represented by the specified uri. All following state
     * change events will be delivered through that call peer. The Call that this peer is a
     * member of could be retrieved from the CallParticipatn instance with the use of the
     * corresponding method.
     * @throws OperationFailedException with the corresponding code if we fail to create the video call.
     */
    Call createVideoCall(Contact callee, QualityPreset qualityPreferences)
            throws OperationFailedException;

    /**
     * Indicates a user request to answer an incoming call with video enabled from the specified CallPeer.
     *
     * @param peer the call peer that we'd like to answer.
     * @throws OperationFailedException with the corresponding code if we encounter an error while performing this operation.
     */
    void answerVideoCallPeer(CallPeer peer)
            throws OperationFailedException;

    /**
     * Returns the quality control for video calls if any. It can be null if we were able to
     * successfully determine that other party does not support it.
     *
     * @param peer the peer which this control operates on.
     * @return the implemented quality control.
     */
    QualityControl getQualityControl(CallPeer peer);

    /**
     * Determines the <code>ConferenceMember</code> which is participating in a telephony conference
     * with a specific <code>CallPeer</code> as its focus and which is sending a video content/RTP
     * stream displayed in a specific visual <code>Component</code>.
     *
     * @param peer the <code>CallPeer</code> which is the conference focus of the telephony conference to be
     * examined in order to locate the <code>ConferenceMember</code> which is sending the video
     * content/RTP stream displayed in the specified <code>visualComponent</code>
     * @param visualComponent the visual <code>Component</code> which displays the video content/RTP stream of the
     * <code>ConferenceMember</code> to be located
     * @return the <code>ConferenceMember</code>, if any, which is sending the video content/RTP stream
     * displayed in the specific <code>visualComponent</code>
     */
    ConferenceMember getConferenceMember(CallPeer peer, Component visualComponent);
}
