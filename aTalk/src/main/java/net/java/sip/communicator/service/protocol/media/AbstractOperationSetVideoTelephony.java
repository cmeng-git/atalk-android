/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package net.java.sip.communicator.service.protocol.media;

import net.java.sip.communicator.service.protocol.Call;
import net.java.sip.communicator.service.protocol.CallPeer;
import net.java.sip.communicator.service.protocol.ConferenceMember;
import net.java.sip.communicator.service.protocol.Contact;
import net.java.sip.communicator.service.protocol.OperationFailedException;
import net.java.sip.communicator.service.protocol.OperationSetBasicTelephony;
import net.java.sip.communicator.service.protocol.OperationSetVideoTelephony;
import net.java.sip.communicator.service.protocol.ProtocolProviderService;

import org.atalk.service.neomedia.MediaUseCase;
import org.atalk.service.neomedia.QualityControl;
import org.atalk.service.neomedia.QualityPreset;
import org.atalk.service.neomedia.VideoMediaStream;
import org.atalk.util.MediaType;
import org.atalk.util.event.VideoListener;

import java.awt.Component;
import java.beans.PropertyChangeListener;
import java.text.ParseException;
import java.util.List;

/**
 * Represents a default implementation of <code>OperationSetVideoTelephony</code> in order to make it
 * easier for implementers to provide complete solutions while focusing on implementation-specific details.
 *
 * @param <T> the implementation specific telephony operation set class like for example
 * <code>OperationSetBasicTelephonySipImpl</code>.
 * @param <U> the implementation specific provider class like for example <code>ProtocolProviderServiceSipImpl</code>.
 * @param <V> the <code>MediaAwareCall</code> implementation like <code>CallSipImpl</code> or <code>CallJabberImpl</code>.
 * @param <W> the <code>MediaAwarePeerCall</code> implementation like <code>CallPeerSipImpl</code> or <code>CallPeerJabberImpl</code>.
 * @author Emil Ivov
 * @author Sebastien Vincent
 */
public abstract class AbstractOperationSetVideoTelephony<
        T extends OperationSetBasicTelephony<U>,
        U extends ProtocolProviderService,
        V extends MediaAwareCall<W, T, U>,
        W extends MediaAwareCallPeer<V, ?, U>>
        implements OperationSetVideoTelephony
{
    /**
     * The SIP <code>ProtocolProviderService</code> implementation which created this instance and for
     * which telephony conferencing services are being provided by this instance.
     */
    protected final U parentProvider;

    /**
     * The telephony-related functionality this extension builds upon.
     */
    protected final T basicTelephony;

    /**
     * Initializes a new <code>AbstractOperationSetVideoTelephony</code> instance which builds upon the
     * telephony-related functionality of a specific <code>OperationSetBasicTelephony</code> implementation.
     *
     * @param basicTelephony the <code>OperationSetBasicTelephony</code> the new extension should build upon
     */
    public AbstractOperationSetVideoTelephony(T basicTelephony)
    {
        this.basicTelephony = basicTelephony;
        this.parentProvider = basicTelephony.getProtocolProvider();
    }

    /**
     * Delegates to the <code>CallPeerMediaHandler</code> of the specified <code>CallPeer</code> because the
     * video is provided by it. Because other <code>OperationSetVideoTelephony</code> implementations
     * may not provide their video through the <code>CallPeerMediaHandler</code>, this implementation
     * promotes itself as the provider of the video by replacing the <code>CallPeerMediaHandler</code>
     * in the <code>VideoEvents</code> it fires.
     *
     * @param peer the <code>CallPeer</code> that we will be registering <code>listener</code> with.
     * @param listener the <code>VideoListener</code> that we'd like to register.
     */
    @SuppressWarnings("unchecked")
    // work with MediaAware* in media package
    public void addVideoListener(CallPeer peer, VideoListener listener)
    {
        if (listener == null)
            throw new NullPointerException("listener");

        ((W) peer).getMediaHandler().addVideoListener(listener);
    }

    /**
     * Implements {@link OperationSetVideoTelephony#getLocalVisualComponent(CallPeer)}.
     *
     * @param peer the <code>CallPeer</code> that we are sending our local video to.
     * @return the <code>Component</code> containing the local video.
     * @throws OperationFailedException if we fail extracting the local video.
     */
    @SuppressWarnings("unchecked")
    // work with MediaAware* in media package
    public Component getLocalVisualComponent(CallPeer peer)
            throws OperationFailedException
    {
        return ((W) peer).getMediaHandler().getLocalVisualComponent();
    }

    /**
     * Gets the visual/video <code>Component</code> available in this telephony for a specific <code>CallPeer</code>.
     *
     * @param peer the <code>CallPeer</code> whose video is to be retrieved
     * @return the visual/video <code>Component</code> available in this telephony for the specified
     * <code>peer</code> if any; otherwise, <code>null</code>
     */
    @Deprecated
    public Component getVisualComponent(CallPeer peer)
    {
        List<Component> visualComponents = getVisualComponents(peer);
        return visualComponents.isEmpty() ? null : visualComponents.get(0);
    }

    /**
     * Gets the visual/video <code>Component</code>s available in this telephony for a specific <code>CallPeer</code>.
     *
     * @param peer the <code>CallPeer</code> whose videos are to be retrieved
     * @return the visual/video <code>Component</code>s available in this telephony for the specified <code>peer</code>
     */
    @SuppressWarnings("unchecked")
    // work with MediaAware* in media package
    public List<Component> getVisualComponents(CallPeer peer)
    {
        return ((W) peer).getMediaHandler().getVisualComponents();
    }

    /**
     * Returns the <code>ConferenceMember</code> corresponding to the given <code>visualComponent</code>.
     *
     * @param peer the parent <code>CallPeer</code>
     * @param visualComponent the visual <code>Component</code>, which corresponding <code>ConferenceMember</code> we're
     * looking for
     * @return the <code>ConferenceMember</code> corresponding to the given <code>visualComponent</code>.
     */
    public ConferenceMember getConferenceMember(CallPeer peer, Component visualComponent)
    {
        @SuppressWarnings("unchecked")
        W w = (W) peer;
        VideoMediaStream videoStream = (VideoMediaStream) w.getMediaHandler().getStream(MediaType.VIDEO);

        if (videoStream != null) {
            for (ConferenceMember member : peer.getConferenceMembers()) {
                Component memberComponent = videoStream.getVisualComponent(member.getVideoSsrc());

                if (visualComponent.equals(memberComponent))
                    return member;
            }
        }
        return null;
    }

    /**
     * Delegates to the <code>CallPeerMediaHandler</code> of the specified <code>CallPeer</code> because the
     * video is provided by it.
     *
     * @param peer the <code>CallPeer</code> that we'd like to unregister our <code>VideoListener</code> from.
     * @param listener the <code>VideoListener</code> that we'd like to unregister.
     */
    @SuppressWarnings("unchecked")
    // work with MediaAware* in media package
    public void removeVideoListener(CallPeer peer, VideoListener listener)
    {
        if (listener != null)
            ((W) peer).getMediaHandler().removeVideoListener(listener);
    }

    /**
     * Implements OperationSetVideoTelephony#setLocalVideoAllowed(Call, boolean). Modifies the local
     * media setup to reflect the requested setting for the streaming of the local video and then
     * re-invites all CallPeers to re-negotiate the modified media setup.
     *
     * @param call the call where we'd like to allow sending local video.
     * @param allowed <code>true</code> if local video transmission is allowed and <code>false</code> otherwise.
     * @throws OperationFailedException if video initialization fails.
     */
    public void setLocalVideoAllowed(Call call, boolean allowed)
            throws OperationFailedException
    {
        MediaAwareCall<?, ?, ?> mediaAwareCall = (MediaAwareCall<?, ?, ?>) call;
        MediaUseCase useCase = MediaUseCase.CALL;

        mediaAwareCall.setLocalVideoAllowed(allowed, useCase);
    }

    /**
     * Determines whether the streaming of local video in a specific <code>Call</code> is currently
     * allowed. The setting does not reflect the availability of actual video capture devices, it
     * just expresses the desire of the user to have the local video streamed in the case the system
     * is actually able to do so.
     *
     * @param call the <code>Call</code> whose video transmission properties we are interested in.
     * @return <code>true</code> if the streaming of local video for the specified <code>Call</code> is allowed;
     * otherwise, <code>false</code>
     */
    @SuppressWarnings("unchecked")
    // work with MediaAware* in media package
    public boolean isLocalVideoAllowed(Call call)
    {
        return ((V) call).isLocalVideoAllowed(MediaUseCase.CALL);
    }

    /**
     * Determines whether a specific <code>Call</code> is currently streaming the local video (to a
     * remote destination).
     *
     * @param call the <code>Call</code> whose video transmission we are interested in.
     * @return <code>true</code> if the specified <code>Call</code> is currently streaming the local video
     * (to a remote destination); otherwise, <code>false</code>
     */
    @SuppressWarnings("unchecked")
    // work with MediaAware* in media package
    public boolean isLocalVideoStreaming(Call call)
    {
        return ((V) call).isLocalVideoStreaming();
    }

    /**
     * Adds a specific <code>PropertyChangeListener</code> to the list of listeners which get notified
     * when the properties (e.g. {@link #LOCAL_VIDEO_STREAMING}) associated with a specific
     * <code>Call</code> change their values.
     *
     * @param call the <code>Call</code> to start listening to the changes of the property values of
     * @param listener the <code>PropertyChangeListener</code> to be notified when the properties associated with
     * the specified <code>Call</code> change their values
     */
    @SuppressWarnings("unchecked")
    // work with MediaAware* in media package
    public void addPropertyChangeListener(Call call, PropertyChangeListener listener)
    {
        ((V) call).addVideoPropertyChangeListener(listener);
    }

    /**
     * Removes a specific <code>PropertyChangeListener</code> from the list of listeners which get
     * notified when the properties (e.g. {@link #LOCAL_VIDEO_STREAMING}) associated with a specific
     * <code>Call</code> change their values.
     *
     * @param call the <code>Call</code> to stop listening to the changes of the property values of
     * @param listener the <code>PropertyChangeListener</code> to no longer be notified when the properties
     * associated with the specified <code>Call</code> change their values
     */
    @SuppressWarnings("unchecked")
    // work with MediaAware* in media package
    public void removePropertyChangeListener(Call call, PropertyChangeListener listener)
    {
        ((V) call).removeVideoPropertyChangeListener(listener);
    }

    /**
     * Get the <code>MediaUseCase</code> of a video telephony operation set.
     *
     * @return <code>MediaUseCase.CALL</code>
     */
    public MediaUseCase getMediaUseCase()
    {
        return MediaUseCase.CALL;
    }

    /**
     * Returns the quality control for video calls if any. Return null so protocols who supports it to override it.
     *
     * @param peer the peer which this control operates on.
     * @return the implemented quality control.
     */
    public QualityControl getQualityControl(CallPeer peer)
    {
        return null;
    }

    /**
     * Create a new video call and invite the specified CallPeer to it with initial video setting.
     *
     * @param uri the address of the callee that we should invite to a new call.
     * @param qualityPreferences the quality preset we will use establishing the video call, and we will expect from
     * the other side. When establishing call we don't have any indications whether remote
     * part supports quality presets, so this setting can be ignored.
     * @return CallPeer the CallPeer that will represented by the specified uri. All following state
     * change events will be delivered through that call peer. The Call that this peer is a member of
     * could be retrieved from the CallParticipatn instance with the use of the corresponding method.
     * @throws OperationFailedException with the corresponding code if we fail to create the video call.
     * @throws java.text.ParseException if <code>callee</code> is not a valid sip address string.
     */
    public Call createVideoCall(String uri, QualityPreset qualityPreferences)
            throws OperationFailedException, ParseException
    {
        return createVideoCall(uri);
    }

    /**
     * Create a new video call and invite the specified CallPeer to it with initial video setting.
     *
     * @param callee the address of the callee that we should invite to a new call.
     * @param qualityPreferences the quality preset we will use establishing the video call, and we will expect from
     * the other side. When establishing call we don't have any indications whether remote
     * part supports quality presets, so this setting can be ignored.
     * @return CallPeer the CallPeer that will represented by the specified uri. All following state
     * change events will be delivered through that call peer. The Call that this peer is a member of
     * could be retrieved from the CallParticipatn instance with the use of the corresponding method.
     * @throws OperationFailedException with the corresponding code if we fail to create the video call.
     */
    public Call createVideoCall(Contact callee, QualityPreset qualityPreferences)
            throws OperationFailedException
    {
        return createVideoCall(callee);
    }
}
