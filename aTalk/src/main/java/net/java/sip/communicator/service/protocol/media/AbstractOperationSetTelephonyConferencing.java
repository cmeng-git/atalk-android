/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package net.java.sip.communicator.service.protocol.media;

import net.java.sip.communicator.service.protocol.AbstractConferenceMember;
import net.java.sip.communicator.service.protocol.Call;
import net.java.sip.communicator.service.protocol.CallConference;
import net.java.sip.communicator.service.protocol.CallPeer;
import net.java.sip.communicator.service.protocol.CallPeerState;
import net.java.sip.communicator.service.protocol.ChatRoom;
import net.java.sip.communicator.service.protocol.ConferenceDescription;
import net.java.sip.communicator.service.protocol.ConferenceMember;
import net.java.sip.communicator.service.protocol.OperationFailedException;
import net.java.sip.communicator.service.protocol.OperationSetBasicTelephony;
import net.java.sip.communicator.service.protocol.OperationSetTelephonyConferencing;
import net.java.sip.communicator.service.protocol.ProtocolProviderService;
import net.java.sip.communicator.service.protocol.RegistrationState;
import net.java.sip.communicator.service.protocol.event.CallChangeEvent;
import net.java.sip.communicator.service.protocol.event.CallChangeListener;
import net.java.sip.communicator.service.protocol.event.CallEvent;
import net.java.sip.communicator.service.protocol.event.CallListener;
import net.java.sip.communicator.service.protocol.event.CallPeerAdapter;
import net.java.sip.communicator.service.protocol.event.CallPeerChangeEvent;
import net.java.sip.communicator.service.protocol.event.CallPeerEvent;
import net.java.sip.communicator.service.protocol.event.CallPeerListener;
import net.java.sip.communicator.service.protocol.event.RegistrationStateChangeEvent;
import net.java.sip.communicator.service.protocol.event.RegistrationStateChangeListener;

import org.atalk.service.neomedia.MediaDirection;
import org.atalk.service.neomedia.MediaStream;
import org.atalk.util.MediaType;
import org.atalk.util.xml.XMLException;
import org.jxmpp.stringprep.XmppStringprepException;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import timber.log.Timber;

/**
 * Represents a default implementation of <code>OperationSetTelephonyConferencing</code> in order to
 * make it easier for implementers to provide complete solutions while focusing on
 * implementation-specific details.
 *
 * @param <ProtocolProviderServiceT>
 * @param <OperationSetBasicTelephonyT>
 * @param <MediaAwareCallT>
 * @param <MediaAwareCallPeerT>
 * @param <CalleeAddressT>
 *
 * @author Lyubomir Marinov
 * @author Boris Grozev
 * @author Eng Chong Meng
 */
public abstract class AbstractOperationSetTelephonyConferencing<
        ProtocolProviderServiceT extends ProtocolProviderService,
        OperationSetBasicTelephonyT extends OperationSetBasicTelephony<ProtocolProviderServiceT>,
        MediaAwareCallT extends MediaAwareCall<MediaAwareCallPeerT, OperationSetBasicTelephonyT, ProtocolProviderServiceT>,
        MediaAwareCallPeerT extends MediaAwareCallPeer<MediaAwareCallT, ?, ProtocolProviderServiceT>, CalleeAddressT>
        implements OperationSetTelephonyConferencing, RegistrationStateChangeListener,
        PropertyChangeListener, CallListener, CallChangeListener {
    /**
     * The name of the conference-info XML element <code>display-text</code>.
     */
    protected static final String ELEMENT_DISPLAY_TEXT = "display-text";

    /**
     * The name of the conference-info XML element <code>endpoint</code>.
     */
    protected static final String ELEMENT_ENDPOINT = "endpoint";

    /**
     * The name of the conference-info XML element <code>media</code>.
     */
    protected static final String ELEMENT_MEDIA = "media";

    /**
     * The name of the conference-info XML element <code>src-id</code>.
     */
    protected static final String ELEMENT_SRC_ID = "src-id";

    /**
     * The name of the conference-info XML element <code>status</code>.
     */
    protected static final String ELEMENT_STATUS = "status";

    /**
     * The name of the conference-info XML element <code>type</code>.
     */
    protected static final String ELEMENT_TYPE = "type";

    /**
     * The name of the conference-info XML element <code>user</code>.
     */
    protected static final String ELEMENT_USER = "user";

    /**
     * The name of the conference-info XML element <code>users</code>.
     */
    protected static final String ELEMENT_USERS = "users";

    /**
     * The name of the account property which specifies whether we should generate and send RFC4575
     * partial notifications (as opposed to always sending 'full' documents)
     */
    private static final String PARTIAL_NOTIFICATIONS_PROP_NAME = "RFC4575_PARTIAL_NOTIFICATIONS_ENABLED";

    /**
     * The <code>OperationSetBasicTelephony</code> implementation which this instance uses to carry out
     * tasks such as establishing <code>Call</code>s.
     */
    private OperationSetBasicTelephonyT basicTelephony;

    /**
     * The <code>CallPeerListener</code> which listens to modifications in the properties/state of
     * <code>CallPeer</code> so that NOTIFY requests can be sent from a conference focus to its
     * conference members to update them with the latest information about the <code>CallPeer</code>.
     */
    private final CallPeerListener callPeerListener = new CallPeerAdapter() {
        /**
         * Indicates that a change has occurred in the status of the source <code>CallPeer</code>.
         *
         * @param evt
         *        the <code>CallPeerChangeEvent</code> instance containing the source event as well as
         *        its previous and its new status
         */
        @Override
        public void peerStateChanged(CallPeerChangeEvent evt) {
            CallPeer peer = evt.getSourceCallPeer();

            if (peer != null) {
                Call call = peer.getCall();

                if (call != null) {
                    CallPeerState state = peer.getState();

                    if ((state != null) && !state.equals(CallPeerState.DISCONNECTED)
                            && !state.equals(CallPeerState.FAILED)) {
                        AbstractOperationSetTelephonyConferencing.this.notifyAll(call);
                    }
                }
            }
        }
    };

    /**
     * The <code>ProtocolProviderService</code> implementation which created this instance and for which
     * telephony conferencing services are being provided by this instance.
     */
    protected final ProtocolProviderServiceT parentProvider;

    /**
     * Initializes a new <code>AbstractOperationSetTelephonyConferencing</code> instance which is to
     * provide telephony conferencing services for the specified <code>ProtocolProviderService</code>
     * implementation.
     *
     * @param parentProvider the <code>ProtocolProviderService</code> implementation which has requested the creation
     * of the new instance and for which the new instance is to provide telephony
     * conferencing services
     */
    protected AbstractOperationSetTelephonyConferencing(ProtocolProviderServiceT parentProvider) {
        this.parentProvider = parentProvider;
        this.parentProvider.addRegistrationStateChangeListener(this);
    }

    /**
     * Notifies this <code>OperationSetTelephonyConferencing</code> that its <code>basicTelephony</code>
     * property has changed its value from a specific <code>oldValue</code> to a specific
     * <code>newValue</code>
     *
     * @param oldValue the old value of the <code>basicTelephony</code> property
     * @param newValue the new value of the <code>basicTelephony</code> property
     */
    protected void basicTelephonyChanged(OperationSetBasicTelephonyT oldValue,
            OperationSetBasicTelephonyT newValue) {
        if (oldValue != null)
            oldValue.removeCallListener(this);
        if (newValue != null)
            newValue.addCallListener(this);
    }

    /**
     * Notifies this <code>CallListener</code> that a specific <code>Call</code> has been established.
     *
     * @param event a <code>CallEvent</code> which specified the newly-established <code>Call</code>
     */
    protected void callBegun(CallEvent event) {
        Call call = event.getSourceCall();

        call.addCallChangeListener(this);

        /*
         * If there were any CallPeers in the Call prior to our realization that it has begun,
         * pretend that they are added afterwards.
         */
        Iterator<? extends CallPeer> callPeerIter = call.getCallPeers();

        while (callPeerIter.hasNext()) {
            callPeerAdded(new CallPeerEvent(callPeerIter.next(), call,
                    CallPeerEvent.CALL_PEER_ADDED));
        }
    }

    /**
     * Notifies this <code>CallListener</code> that a specific <code>Call</code> has ended.
     *
     * @param event a <code>CallEvent</code> which specified the <code>Call</code> which has just ended
     */
    public void callEnded(CallEvent event) {
        Call call = event.getSourceCall();

        /*
         * If there are still CallPeers after our realization that it has ended, pretend that they
         * are removed before that.
         */
        Iterator<? extends CallPeer> callPeerIter = call.getCallPeers();

        while (callPeerIter.hasNext()) {
            callPeerRemoved(new CallPeerEvent(callPeerIter.next(), call,
                    CallPeerEvent.CALL_PEER_REMOVED));
        }

        call.removeCallChangeListener(this);
    }

    /**
     * Notifies this <code>CallChangeListener</code> that a specific <code>CallPeer</code> has been added to
     * a specific <code>Call</code>.
     *
     * @param event a <code>CallPeerEvent</code> which specifies the <code>CallPeer</code> which has been added to
     * a <code>Call</code>
     */
    public void callPeerAdded(CallPeerEvent event) {
        MediaAwareCallPeer<?, ?, ?> callPeer = (MediaAwareCallPeer<?, ?, ?>) event
                .getSourceCallPeer();

        callPeer.addCallPeerListener(callPeerListener);
        callPeer.getMediaHandler().addPropertyChangeListener(this);
        callPeersChanged(event);
    }

    /**
     * Notifies this <code>CallChangeListener</code> that a specific <code>CallPeer</code> has been remove
     * from a specific <code>Call</code>.
     *
     * @param event a <code>CallPeerEvent</code> which specifies the <code>CallPeer</code> which has been removed
     * from a <code>Call</code>
     */
    public void callPeerRemoved(CallPeerEvent event) {
        @SuppressWarnings("unchecked")
        MediaAwareCallPeerT callPeer = (MediaAwareCallPeerT) event.getSourceCallPeer();

        callPeer.removeCallPeerListener(callPeerListener);
        callPeer.getMediaHandler().removePropertyChangeListener(this);
        callPeersChanged(event);
    }

    /**
     * Notifies this <code>CallChangeListener</code> that the <code>CallPeer</code> list of a specific
     * <code>Call</code> has been modified by adding or removing a specific <code>CallPeer</code>.
     *
     * @param event a <code>CallPeerEvent</code> which specifies the <code>CallPeer</code> which has been added to
     * or removed from a <code>Call</code>
     */
    private void callPeersChanged(CallPeerEvent event) {
        notifyAll(event.getSourceCall());
    }

    /**
     * Notifies this <code>CallChangeListener</code> that a specific <code>Call</code> has changed its
     * state. Does nothing.
     *
     * @param event a <code>CallChangeEvent</code> which specifies the <code>Call</code> which has changed its
     * state, the very state which has been changed and the values of the state before and
     * after the change
     */
    public void callStateChanged(CallChangeEvent event) {
        if (CallChangeEvent.CALL_PARTICIPANTS_CHANGE.equals(event.getPropertyName())) {
            notifyAll(event.getSourceCall());
        }
    }

    /**
     * Creates a conference call with the specified callees as call peers.
     *
     * @param callees the list of addresses that we should call
     *
     * @return the newly created conference call containing all CallPeers
     * @throws OperationFailedException if establishing the conference call fails
     * @see OperationSetTelephonyConferencing#createConfCall(String[])
     */
    public Call createConfCall(String[] callees)
            throws OperationFailedException, XmppStringprepException {
        return createConfCall(callees, null);
    }

    /**
     * Creates a conference <code>Call</code> with the specified callees as <code>CallPeers</code>.
     *
     * @param callees the list of addresses that we should call
     * @param conference the <code>CallConference</code> which represents the state of the telephony conference
     * into which the specified callees are to be invited
     *
     * @return the newly-created conference call containing all <code>CallPeer</code>s
     * @throws OperationFailedException if establishing the conference <code>Call</code> fails
     */
    public Call createConfCall(String[] callees, CallConference conference)
            throws OperationFailedException, XmppStringprepException {
        List<CalleeAddressT> calleeAddresses = new ArrayList<>(callees.length);

        for (String callee : callees)
            calleeAddresses.add(parseAddressString(callee));

        MediaAwareCallT call = createOutgoingCall();
        if (conference == null)
            conference = call.getConference();
        else
            call.setConference(conference);
        conference.setConferenceFocus(true);

        for (CalleeAddressT calleeAddress : calleeAddresses)
            doInviteCalleeToCall(calleeAddress, call);

        return call;
    }

    /**
     * Creates a new outgoing <code>Call</code> into which conference callees are to be invited by this
     * <code>OperationSetTelephonyConferencing</code>.
     *
     * @return a new outgoing <code>Call</code> into which conference callees are to be invited by this
     * <code>OperationSetTelephonyConferencing</code>
     * @throws OperationFailedException if anything goes wrong
     */
    protected abstract MediaAwareCallT createOutgoingCall()
            throws OperationFailedException;

    /**
     * Invites a callee with a specific address to join a specific <code>Call</code> for the purposes of
     * telephony conferencing.
     *
     * @param calleeAddress the address of the callee to be invited to the specified existing <code>Call</code>
     * @param call the existing <code>Call</code> to invite the callee with the specified address to
     *
     * @return a new <code>CallPeer</code> instance which describes the signaling and the media
     * streaming of the newly-invited callee within the specified <code>Call</code>
     * @throws OperationFailedException if inviting the specified callee to the specified <code>Call</code> fails
     */
    protected abstract CallPeer doInviteCalleeToCall(CalleeAddressT calleeAddress,
            MediaAwareCallT call)
            throws OperationFailedException;

    /**
     * Gets the <code>OperationSetBasicTelephony</code> implementation which this instance uses to carry
     * out tasks such as establishing <code>Call</code>s.
     *
     * @return the <code>OperationSetBasicTelephony</code> implementation which this instance uses to
     * carry out tasks such as establishing <code>Call</code>s
     */
    public OperationSetBasicTelephonyT getBasicTelephony() {
        return basicTelephony;
    }

    private void getEndpointMediaProperties(Node endpoint, Map<String, Object> properties) {
        NodeList endpointChildList = endpoint.getChildNodes();
        int endpoingChildCount = endpointChildList.getLength();

        for (int endpointChildIndex = 0; endpointChildIndex < endpoingChildCount; endpointChildIndex++) {
            Node endpointChild = endpointChildList.item(endpointChildIndex);

            if (ELEMENT_MEDIA.equals(endpointChild.getNodeName())) {
                NodeList mediaChildList = endpointChild.getChildNodes();
                int mediaChildCount = mediaChildList.getLength();
                String srcId = null;
                String status = null;
                String type = null;

                for (int mediaChildIndex = 0; mediaChildIndex < mediaChildCount; mediaChildIndex++) {
                    Node mediaChild = mediaChildList.item(mediaChildIndex);
                    String mediaChildName = mediaChild.getNodeName();

                    if (ELEMENT_SRC_ID.equals(mediaChildName))
                        srcId = mediaChild.getTextContent();
                    else if (ELEMENT_STATUS.equals(mediaChildName))
                        status = mediaChild.getTextContent();
                    else if (ELEMENT_TYPE.equals(mediaChildName))
                        type = mediaChild.getTextContent();
                }

                if (MediaType.AUDIO.toString().equalsIgnoreCase(type)) {
                    properties.put(ConferenceMember.AUDIO_SSRC_PROPERTY_NAME, srcId);
                    properties.put(ConferenceMember.AUDIO_STATUS_PROPERTY_NAME, status);
                }
                else if (MediaType.VIDEO.toString().equalsIgnoreCase(type)) {
                    properties.put(ConferenceMember.VIDEO_SSRC_PROPERTY_NAME, srcId);
                    properties.put(ConferenceMember.VIDEO_STATUS_PROPERTY_NAME, status);
                }
            }
        }
    }

    /**
     * Reads the text content of the <code>status</code> XML element of a specific <code>endpoint</code> XML
     * element.
     *
     * @param endpoint an XML <code>Node</code> which represents the <code>endpoint</code> XML element from which to
     * get the text content of its <code>status</code> XML element
     *
     * @return the text content of the <code>status</code> XML element of the specified
     * <code>endpoint</code> XML element if any; otherwise, <code>null</code>
     */
    private String getEndpointStatus(Node endpoint) {
        NodeList childNodes = endpoint.getChildNodes();
        int childCount = childNodes.getLength();

        for (int i = 0; i < childCount; i++) {
            Node child = childNodes.item(i);

            if (ELEMENT_STATUS.equals(child.getNodeName()))
                return child.getTextContent();
        }
        return null;
    }

    /**
     * Gets the remote SSRC to be reported in the conference-info XML for a specific
     * <code>CallPeer</code>'s media of a specific <code>MediaType</code>.
     *
     * @param callPeer the <code>CallPeer</code> whose remote SSRC for the media of the specified
     * <code>mediaType</code> is to be returned
     * @param mediaType the <code>MediaType</code> of the specified <code>callPeer</code>'s media whose remote SSRC is
     * to be returned
     *
     * @return the remote SSRC to be reported in the conference-info XML for the specified
     * <code>callPeer</code>'s media of the specified <code>mediaType</code>
     */
    protected long getRemoteSourceID(MediaAwareCallPeer<?, ?, ?> callPeer, MediaType mediaType) {
        long remoteSourceID = callPeer.getMediaHandler().getRemoteSSRC(mediaType);

        if (remoteSourceID != -1) {
            /*
             * TODO Technically, we are detecting conflicts within a Call while we should be
             * detecting them within the whole CallConference.
             */
            MediaAwareCall<?, ?, ?> call = callPeer.getCall();

            if (call != null) {
                for (MediaAwareCallPeer<?, ?, ?> aCallPeer : call.getCallPeerList()) {
                    if (aCallPeer != callPeer) {
                        long aRemoteSourceID = aCallPeer.getMediaHandler().getRemoteSSRC(mediaType);

                        if (aRemoteSourceID == remoteSourceID) {
                            remoteSourceID = -1;
                            break;
                        }
                    }
                }
            }
        }
        return remoteSourceID;
    }

    /**
     * Notifies this <code>CallListener</code> that a specific incoming <code>Call</code> has been received.
     *
     * @param event a <code>CallEvent</code> which specifies the newly-received incoming <code>Call</code>
     */
    public void incomingCallReceived(CallEvent event) {
        callBegun(event);
    }

    /**
     * Invites the callee represented by the specified uri to an already existing call. The
     * difference between this method and createConfCall is that inviteCalleeToCall allows a user to
     * transform an existing 1 to 1 call into a conference call, or add new peers to an already
     * established conference.
     *
     * @param uri the callee to invite to an existing conf call.
     * @param call the call that we should invite the callee to.
     *
     * @return the CallPeer object corresponding to the callee represented by the specified uri.
     * @throws OperationFailedException if inviting the specified callee to the specified call fails
     */
    public CallPeer inviteCalleeToCall(String uri, Call call)
            throws OperationFailedException, XmppStringprepException {
        CalleeAddressT calleeAddress = parseAddressString(uri);
        @SuppressWarnings("unchecked")
        MediaAwareCallT mediaAwareCallT = (MediaAwareCallT) call;

        mediaAwareCallT.getConference().setConferenceFocus(true);
        return doInviteCalleeToCall(calleeAddress, mediaAwareCallT);
    }

    /**
     * Notifies all <code>CallPeer</code>s associated with the telephony conference in which a specific
     * <code>Call</code> is participating about changes in the telephony conference-related information.
     *
     * @param call the <code>Call</code> which specifies the telephony conference the associated
     * <code>CallPeer</code>s of which are to be notified about changes in the telephony
     * conference-related information
     */
    @SuppressWarnings("rawtypes")
    protected void notifyAll(Call call) {
        CallConference conference = call.getConference();

        if (conference == null)
            notifyCallPeers(call);
        else {
            /*
             * Make each Call notify its CallPeers through its OperationSetTelephonyConferencing
             * (i.e. its protocol).
             */
            for (Call conferenceCall : conference.getCalls()) {
                OperationSetTelephonyConferencing opSet = conferenceCall.getProtocolProvider()
                        .getOperationSet(OperationSetTelephonyConferencing.class);

                if (opSet instanceof AbstractOperationSetTelephonyConferencing) {
                    ((AbstractOperationSetTelephonyConferencing) opSet)
                            .notifyCallPeers(conferenceCall);
                }
            }
        }
    }

    /**
     * Notifies all <code>CallPeer</code>s associated with a specific <code>Call</code> about changes in the
     * telephony conference-related information. In contrast, {@link #notifyAll()} notifies all
     * <code>CallPeer</code>s associated with the telephony conference in which a specific <code>Call</code>
     * is participating.
     *
     * @param call the <code>Call</code> whose <code>CallPeer</code>s are to be notified about changes in the
     * telephony conference-related information
     */
    protected abstract void notifyCallPeers(Call call);

    /**
     * Notifies this <code>CallListener</code> that a specific outgoing <code>Call</code> has been created.
     *
     * @param event a <code>CallEvent</code> which specifies the newly-created outgoing <code>Call</code>
     */
    public void outgoingCallCreated(CallEvent event) {
        callBegun(event);
    }

    /**
     * Parses a <code>String</code> value which represents a callee address specified by the user into
     * an object which is to actually represent the callee during the invitation to a conference
     * <code>Call</code>.
     *
     * @param calleeAddressString a <code>String</code> value which represents a callee address to be parsed into an object
     * which is to actually represent the callee during the invitation to a conference
     * <code>Call</code>
     *
     * @return an object which is to actually represent the specified <code>calleeAddressString</code>
     * during the invitation to a conference <code>Call</code>
     * @throws OperationFailedException if parsing the specified <code>calleeAddressString</code> fails
     */
    protected abstract CalleeAddressT parseAddressString(String calleeAddressString)
            throws OperationFailedException, XmppStringprepException;

    /**
     * Notifies this <code>PropertyChangeListener</code> that the value of a specific property of the
     * notifier it is registered with has changed.
     *
     * @param ev a <code>PropertyChangeEvent</code> which describes the source of the event, the name of
     * the property which has changed its value and the old and new values of the property
     *
     * @see PropertyChangeListener#propertyChange(PropertyChangeEvent)
     */
    public void propertyChange(PropertyChangeEvent ev) {
        String propertyName = ev.getPropertyName();

        if (CallPeerMediaHandler.AUDIO_LOCAL_SSRC.equals(propertyName)
                || CallPeerMediaHandler.AUDIO_REMOTE_SSRC.equals(propertyName)
                || CallPeerMediaHandler.VIDEO_LOCAL_SSRC.equals(propertyName)
                || CallPeerMediaHandler.VIDEO_REMOTE_SSRC.equals(propertyName)) {
            @SuppressWarnings("unchecked")
            CallPeerMediaHandler<MediaAwareCallPeerT> mediaHandler = (CallPeerMediaHandler<MediaAwareCallPeerT>) ev
                    .getSource();
            Call call = mediaHandler.getPeer().getCall();

            if (call != null)
                notifyAll(call);
        }
    }

    /**
     * Notifies this <code>RegistrationStateChangeListener</code> that the
     * <code>ProtocolProviderService</code> has registered with change to the registration state.
     *
     * @param event a <code>RegistrationStateChangeEvent</code> which specifies the old and the new value of
     * the registration state of the <code>ProtocolProviderService</code> this
     * <code>RegistrationStateChangeListener</code> listens to
     */
    public void registrationStateChanged(RegistrationStateChangeEvent event) {
        RegistrationState newState = event.getNewState();

        if (RegistrationState.REGISTERED.equals(newState)) {
            @SuppressWarnings("unchecked")
            OperationSetBasicTelephonyT basicTelephony = (OperationSetBasicTelephonyT)
                    parentProvider.getOperationSet(OperationSetBasicTelephony.class);

            if (this.basicTelephony != basicTelephony) {
                OperationSetBasicTelephonyT oldValue = this.basicTelephony;

                this.basicTelephony = basicTelephony;
                basicTelephonyChanged(oldValue, this.basicTelephony);
            }
        }
        else if (RegistrationState.UNREGISTERED.equals(newState)) {
            if (basicTelephony != null) {
                OperationSetBasicTelephonyT oldValue = basicTelephony;

                basicTelephony = null;
                basicTelephonyChanged(oldValue, null);
            }
        }
    }

    /**
     * Updates the conference-related properties of a specific <code>CallPeer</code> such as
     * <code>conferenceFocus</code> and <code>conferenceMembers</code> with the information described in
     * <code>confInfo</code>. <code>confInfo</code> must be a document with "full" state.
     *
     * @param callPeer the <code>CallPeer</code> which is a conference focus and has sent the specified
     * conference-info XML document
     * @param confInfo the conference-info XML document to use to update the conference-related information
     * of the local peer represented by the associated <code>Call</code>. It must have a "full"
     * state.
     */
    private int setConferenceInfoDocument(MediaAwareCallPeerT callPeer,
            ConferenceInfoDocument confInfo) {
        NodeList usersList = confInfo.getDocument().getElementsByTagName(ELEMENT_USERS);
        List<ConferenceMember> conferenceMembers = callPeer.getConferenceMembers();
        ConferenceMember[] toRemove = conferenceMembers.toArray(new ConferenceMember[0]);
        int toRemoveCount = toRemove.length;
        boolean changed = false;

        if (usersList.getLength() > 0) {
            NodeList userList = usersList.item(0).getChildNodes();
            int userCount = userList.getLength();
            Map<String, Object> conferenceMemberProperties = new HashMap<>();

            for (int userIndex = 0; userIndex < userCount; userIndex++) {
                Node user = userList.item(userIndex);

                if (!ELEMENT_USER.equals(user.getNodeName()))
                    continue;

                String address = stripParametersFromAddress(((Element) user).getAttribute("entity"));

                if ((address == null) || (address.length() < 1))
                    continue;

                /*
                 * Determine the ConferenceMembers who are no longer in the list i.e. are to be
                 * removed.
                 */
                AbstractConferenceMember conferenceMember = null;

                for (int i = 0; i < toRemoveCount; i++) {
                    ConferenceMember aConferenceMember = toRemove[i];

                    if ((aConferenceMember != null)
                            && address.equalsIgnoreCase(aConferenceMember.getAddress())) {
                        toRemove[i] = null;
                        conferenceMember = (AbstractConferenceMember) aConferenceMember;
                        break;
                    }
                }

                // Create the new ones.
                boolean addConferenceMember;

                if (conferenceMember == null) {
                    conferenceMember = new AbstractConferenceMember(callPeer, address);
                    addConferenceMember = true;
                }
                else
                    addConferenceMember = false;

                // Update the existing ones.
                if (conferenceMember != null) {
                    NodeList userChildList = user.getChildNodes();
                    int userChildCount = userChildList.getLength();
                    String displayName = null;
                    String endpointStatus = null;

                    conferenceMemberProperties.put(ConferenceMember.AUDIO_SSRC_PROPERTY_NAME, null);
                    conferenceMemberProperties.put(ConferenceMember.AUDIO_STATUS_PROPERTY_NAME,
                            null);
                    conferenceMemberProperties.put(ConferenceMember.VIDEO_SSRC_PROPERTY_NAME, null);
                    conferenceMemberProperties.put(ConferenceMember.VIDEO_STATUS_PROPERTY_NAME,
                            null);
                    for (int userChildIndex = 0; userChildIndex < userChildCount; userChildIndex++) {
                        Node userChild = userChildList.item(userChildIndex);
                        String userChildName = userChild.getNodeName();

                        if (ELEMENT_DISPLAY_TEXT.equals(userChildName))
                            displayName = userChild.getTextContent();
                        else if (ELEMENT_ENDPOINT.equals(userChildName)) {
                            endpointStatus = getEndpointStatus(userChild);
                            getEndpointMediaProperties(userChild, conferenceMemberProperties);
                        }
                    }
                    conferenceMember.setDisplayName(displayName);
                    conferenceMember.setEndpointStatus(endpointStatus);

                    changed = conferenceMember.setProperties(conferenceMemberProperties);

                    if (addConferenceMember)
                        callPeer.addConferenceMember(conferenceMember);
                }
            }
        }

        /*
         * Remove the ConferenceMember instances which are no longer present in the conference-info
         * XML document.
         */
        for (ConferenceMember conferenceMemberToRemove : toRemove) {
            if (conferenceMemberToRemove != null)
                callPeer.removeConferenceMember(conferenceMemberToRemove);
        }

        if (changed)
            notifyAll(callPeer.getCall());

        callPeer.setLastConferenceInfoReceived(confInfo);
        return confInfo.getVersion();
    }

    /**
     * Updates the conference-related properties of a specific <code>CallPeer</code> such as
     * <code>conferenceFocus</code> and <code>conferenceMembers</code> with information received from it as
     * a conference focus in the form of a conference-info XML document.
     *
     * @param callPeer the <code>CallPeer</code> which is a conference focus and has sent the specified
     * conference-info XML document
     * @param conferenceInfoXML the conference-info XML document sent by <code>callPeer</code> in order to update the
     * conference-related information of the local peer represented by the associated
     * <code>Call</code>
     *
     * @return the value of the <code>version</code> attribute of the <code>conference-info</code> XML
     * element of the specified <code>conferenceInfoXML</code> if it was successfully parsed and
     * represented in the specified <code>callPeer</code>
     * @throws XMLException If <code>conferenceInfoXML</code> couldn't be parsed as a <code>ConferenceInfoDocument</code>
     */
    protected int setConferenceInfoXML(MediaAwareCallPeerT callPeer, String conferenceInfoXML)
            throws XMLException {
        ConferenceInfoDocument confInfo = new ConferenceInfoDocument(conferenceInfoXML);

        /*
         * The CallPeer sent conference-info XML so we're sure it's a conference focus.
         */
        callPeer.setConferenceFocus(true);

        /*
         * The following implements the procedure outlined in section 4.6 of RFC4575 - Constructing
         * Coherent State
         */
        int documentVersion = confInfo.getVersion();
        int ourVersion = callPeer.getLastConferenceInfoReceivedVersion();
        ConferenceInfoDocument.State documentState = confInfo.getState();

        if (ourVersion == -1) {
            if (documentState == ConferenceInfoDocument.State.FULL) {
                return setConferenceInfoDocument(callPeer, confInfo);
            }
            else {
                Timber.w("Received a conference-info document with state '%s'. Cannot apply it, because we haven't initialized a local document yet. Sending peer: %s", documentState, callPeer);
                return -1;
            }
        }
        else if (documentVersion <= ourVersion) {
            Timber.i("Received a stale conference-info document. Local version %s, document version %s. Sending peer: %s",
                    ourVersion, documentVersion, callPeer);
            return -1;
        }
        else // ourVersion != -1 && ourVersion < documentVersion
        {
            if (documentState == ConferenceInfoDocument.State.FULL)
                return setConferenceInfoDocument(callPeer, confInfo);
            else if (documentState == ConferenceInfoDocument.State.DELETED) {
                Timber.w("Received a conference-info document with state 'deleted', can't handle. Sending peer: %s",
                        callPeer);
                return -1;
            }
            else if (documentState == ConferenceInfoDocument.State.PARTIAL) {
                if (documentVersion == ourVersion + 1)
                    return updateConferenceInfoDocument(callPeer, confInfo);
                else {
                    /*
                     * According to RFC4575 we "MUST generate a subscription refresh request to
                     * trigger a full state notification".
                     */
                    Timber.w("Received a Conference Information document with state '%s' and version %s. Cannon apply it, because local version is %s. Sending peer: %s",
                            documentState, documentVersion, ourVersion, callPeer);
                    return -1;
                }
            }
            else
                return -1; // unreachable
        }
    }

    /**
     * Removes the parameters (specified after a semicolon) from a specific address <code>String</code>
     * if any are present in it.
     *
     * @param address the <code>String</code> value representing an address from which any parameters are to be
     * removed
     *
     * @return a <code>String</code> representing the specified <code>address</code> without any parameters
     */
    public static String stripParametersFromAddress(String address) {
        if (address != null) {
            int parametersBeginIndex = address.indexOf(';');

            if (parametersBeginIndex > -1)
                address = address.substring(0, parametersBeginIndex);
        }
        return address;
    }

    /**
     * Creates a <code>ConferenceInfoDocument</code> which describes the current state of the conference
     * in which <code>callPeer</code> participates. The created document contains a "full" description
     * (as opposed to a partial description, see RFC4575).
     *
     * @return a <code>ConferenceInfoDocument</code> which describes the current state of the conference
     * in which this <code>CallPeer</code> participates.
     */
    protected ConferenceInfoDocument getCurrentConferenceInfo(MediaAwareCallPeer<?, ?, ?> callPeer) {
        ConferenceInfoDocument confInfo;
        try {
            confInfo = new ConferenceInfoDocument();
        } catch (XMLException e) {
            return null;
        }
        confInfo.setState(ConferenceInfoDocument.State.FULL);
        confInfo.setEntity(getLocalEntity(callPeer));

        Call call = callPeer.getCall();
        if (call == null)
            return null;

        List<CallPeer> conferenceCallPeers = CallConference.getCallPeers(call);
        confInfo.setUserCount(1 /* the local peer/user */ + conferenceCallPeers.size());

        /* The local user */
        addPeerToConferenceInfo(confInfo, callPeer, false);

        /* Remote users */
        for (CallPeer conferenceCallPeer : conferenceCallPeers) {
            if (conferenceCallPeer instanceof MediaAwareCallPeer<?, ?, ?>) {
                addPeerToConferenceInfo(confInfo, (MediaAwareCallPeer<?, ?, ?>) conferenceCallPeer,
                        true);
            }
        }

        return confInfo;
    }

    /**
     * Adds a <code>user</code> element to <code>confInfo</code> which describes <code>callPeer</code>, or the
     * local peer if <code>remote</code> is <code>false</code>.
     *
     * @param confInfo the <code>ConferenceInformationDocument</code> to which to add a <code>user</code> element
     * @param callPeer the <code>CallPeer</code> which should be described
     * @param remote <code>true</code> to describe <code>callPeer</code>, or <code>false</code> to describe the local
     * peer.
     */
    private void addPeerToConferenceInfo(ConferenceInfoDocument confInfo,
            MediaAwareCallPeer<?, ?, ?> callPeer, boolean remote) {
        String entity = remote ? callPeer.getEntity() : getLocalEntity(callPeer);
        Timber.i("### Gettting entity (remote): %s (%s)", entity, remote);
        ConferenceInfoDocument.User user = confInfo.addNewUser(entity);

        String displayName = remote ? callPeer.getDisplayName() : getLocalDisplayName();
        user.setDisplayText(displayName);

        ConferenceInfoDocument.Endpoint endpoint = user.addNewEndpoint(entity);

        endpoint.setStatus(remote ? getEndpointStatus(callPeer)
                : ConferenceInfoDocument.EndpointStatusType.connected);

        CallPeerMediaHandler<?> mediaHandler = callPeer.getMediaHandler();

        for (MediaType mediaType : MediaType.values()) {
            MediaStream stream = mediaHandler.getStream(mediaType);
            if (stream != null || !remote) {
                long srcId = -1;
                if (remote) {
                    srcId = getRemoteSourceID(callPeer, mediaType);
                }
                else if (stream != null) {
                    srcId = stream.getLocalSourceID();
                }
                else // stream == null && !remote
                {
                    /*
                     * If we are describing the local peer, but we don't have media streams with
                     * callPeer (which is the case when we send conference-info while the other side
                     * is still ringing), we can try to obtain our local SSRC from the streams we
                     * have already set up with the other participants in the conference.
                     */
                    for (MediaAwareCallPeer<?, ?, ?> otherCallPeer : callPeer.getCall().getCallPeerList()) {
                        MediaStream otherStream = otherCallPeer.getMediaHandler().getStream(mediaType);
                        if (otherStream != null) {
                            srcId = otherStream.getLocalSourceID();
                            break;
                        }
                    }
                }

                MediaDirection direction = MediaDirection.INACTIVE;
                if (remote) {
                    direction = callPeer.getDirection(mediaType).getReverseDirection();
                }
                else {
                    if (mediaType == MediaType.AUDIO
                            && callPeer.getMediaHandler().isLocalAudioTransmissionEnabled())
                        direction = direction.or(MediaDirection.SENDONLY);
                    else if (mediaType == MediaType.VIDEO && callPeer.isLocalVideoStreaming())
                        direction = direction.or(MediaDirection.SENDONLY);

                    if (callPeer.getDirection(mediaType).allowsReceiving())
                        direction = direction.or(MediaDirection.RECVONLY);
                }

                if ((srcId != -1) || (direction != MediaDirection.INACTIVE)) {
                    ConferenceInfoDocument.Media media = endpoint.addNewMedia(mediaType.toString());

                    media.setType(mediaType.toString());
                    if (srcId != -1)
                        media.setSrcId(Long.toString(srcId));
                    media.setStatus(direction.toString());
                }
            }
        }
    }

    /**
     * Returns a string to be used for the <code>entity</code> attribute of the <code>user</code> element
     * for the local peer, in a Conference Information document to be sent to <code>callPeer</code>
     *
     * @param callPeer The <code>CallPeer</code> for which we are creating a Conference Information document.
     *
     * @return a string to be used for the <code>entity</code> attribute of the <code>user</code> element
     * for the local peer, in a Conference Information document to be sent to
     * <code>callPeer</code>
     */
    protected abstract String getLocalEntity(CallPeer callPeer);

    /**
     * Returns the display name for the local peer, which is to be used when we send Conference
     * Information.
     *
     * @return the display name for the local peer, which is to be used when we send Conference
     * Information.
     */
    protected abstract String getLocalDisplayName();

    /**
     * Gets the <code>EndpointStatusType</code> to use when describing <code>callPeer</code> in a Conference
     * Information document.
     *
     * @param callPeer the <code>CallPeer</code> which is to get its state described in a <code>status</code> XML
     * element of an <code>endpoint</code> XML element
     *
     * @return the <code>EndpointStatusType</code> to use when describing <code>callPeer</code> in a
     * Conference Information document.
     */
    private ConferenceInfoDocument.EndpointStatusType getEndpointStatus(CallPeer callPeer) {
        CallPeerState callPeerState = callPeer.getState();

        if (CallPeerState.ALERTING_REMOTE_SIDE.equals(callPeerState))
            return ConferenceInfoDocument.EndpointStatusType.alerting;
        if (CallPeerState.CONNECTING.equals(callPeerState)
                || CallPeerState.CONNECTING_WITH_EARLY_MEDIA.equals(callPeerState))
            return ConferenceInfoDocument.EndpointStatusType.pending;
        if (CallPeerState.DISCONNECTED.equals(callPeerState))
            return ConferenceInfoDocument.EndpointStatusType.disconnected;
        if (CallPeerState.INCOMING_CALL.equals(callPeerState))
            return ConferenceInfoDocument.EndpointStatusType.dialing_in;
        if (CallPeerState.INITIATING_CALL.equals(callPeerState))
            return ConferenceInfoDocument.EndpointStatusType.dialing_out;

        /*
         * RFC4575 does not list an appropriate endpoint status for "remotely on hold", e.g. the
         * endpoint is not "hearing" the conference mix, but it's media stream *is* being mixed into
         * the conference.
         *
         * We use the on-hold status anyway, because it's the one that makes the most sense.
         */
        if (CallPeerState.ON_HOLD_REMOTELY.equals(callPeerState))
            return ConferenceInfoDocument.EndpointStatusType.on_hold;

        /*
         * he/she is neither "hearing" the conference mix nor is his/her media being mixed in the
         * conference
         */
        if (CallPeerState.ON_HOLD_LOCALLY.equals(callPeerState)
                || CallPeerState.ON_HOLD_MUTUALLY.equals(callPeerState))
            return ConferenceInfoDocument.EndpointStatusType.on_hold;
        if (CallPeerState.CONNECTED.equals(callPeerState))
            return ConferenceInfoDocument.EndpointStatusType.connected;
        return null;
    }

    /**
     * @param from A document with state <code>full</code> from which to generate a "diff".
     * @param to A document with state <code>full</code> to which to generate a "diff"
     *
     * @return a <code>ConferenceInfoDocument</code>, such that when it is applied to <code>from</code>
     * using the procedure defined in section 4.6 of RFC4575, the result is <code>to</code>. May
     * return <code>null</code> if <code>from</code> and <code>to</code> are not found to be different
     * (that is, in case no document needs to be sent)
     */
    protected ConferenceInfoDocument getConferenceInfoDiff(ConferenceInfoDocument from,
            ConferenceInfoDocument to)
            throws IllegalArgumentException {
        if (from.getState() != ConferenceInfoDocument.State.FULL)
            throw new IllegalArgumentException("The 'from' document needs to have state=full");
        if (to.getState() != ConferenceInfoDocument.State.FULL)
            throw new IllegalArgumentException("The 'to' document needs to have state=full");

        if (!isPartialNotificationEnabled()) {
            return conferenceInfoDocumentsMatch(from, to) ? null : to;
        }

        ConferenceInfoDocument diff;
        try {
            diff = new ConferenceInfoDocument();
        } catch (XMLException e) {
            return conferenceInfoDocumentsMatch(from, to) ? null : to;
        }

        diff.setState(ConferenceInfoDocument.State.PARTIAL);
        diff.setUsersState(ConferenceInfoDocument.State.PARTIAL);

        // temporary, used for xmpp only
        String sid = to.getSid();
        if (sid != null && !sid.equals(""))
            diff.setSid(to.getSid());

        diff.setUserCount(to.getUserCount());
        diff.setEntity(to.getEntity());

        boolean needsPartial = false;
        boolean hasDifference = !from.getEntity().equals(to.getEntity()) || from.getUserCount() != to.getUserCount();

        // find users which have been removed in 'to'
        for (ConferenceInfoDocument.User user : from.getUsers()) {
            if (to.getUser(user.getEntity()) == null) {
                ConferenceInfoDocument.User deletedUser = diff.addNewUser(user.getEntity());
                deletedUser.setState(ConferenceInfoDocument.State.DELETED);
                hasDifference = true;
                needsPartial = true;
            }
        }

        for (ConferenceInfoDocument.User toUser : to.getUsers()) {
            ConferenceInfoDocument.User fromUser = from.getUser(toUser.getEntity());
            if (!usersMatch(toUser, fromUser)) {
                hasDifference = true;
                diff.addUser(toUser);
            }
            else {
                // if there is a "user" element which didn't change, we skip it
                // and we need to send state=partial, because otherwise it will
                // be removed by the recipient
                needsPartial = true;
            }
        }

        Timber.d("Generated partial notification. From: %s\nTo: %s\nDiff: %s(hasDifference: %s",
                from, to, diff, hasDifference);

        if (!hasDifference)
            return null;

        /*
         * In some cases (when all the user elements have changed, and none have been removed) we
         * are essentially generating a full document, but marking it 'partial'. In this case it is
         * better to send the full document, just in case the receiver lost the previous document
         * somehow.
         */
        if (!needsPartial) {
            diff.setState(ConferenceInfoDocument.State.FULL);
            diff.setUsersState(ConferenceInfoDocument.State.FULL);
        }

        return diff;
    }

    /**
     * Updates the conference-related properties of a specific <code>CallPeer</code> such as
     * <code>conferenceFocus</code> and <code>conferenceMembers</code> with information received from it as
     * a conference focus in the form of a partial conference-info XML document.
     *
     * @param callPeer the <code>CallPeer</code> which is a conference focus and has sent the specified partial
     * conference-info XML document
     * @param diff the partial conference-info XML document sent by <code>callPeer</code> in order to update
     * the conference-related information of the local peer represented by the associated
     * <code>Call</code>
     *
     * @return the value of the <code>version</code> attribute of the <code>conference-info</code> XML
     * element of the specified <code>conferenceInfoXML</code> if it was successfully parsed and
     * represented in the specified <code>callPeer</code>
     */
    private int updateConferenceInfoDocument(MediaAwareCallPeerT callPeer,
            ConferenceInfoDocument diff) {
        // "apply" diff to ourDocument, result is in newDocument
        ConferenceInfoDocument ourDocument = callPeer.getLastConferenceInfoReceived();
        ConferenceInfoDocument newDocument;

        ConferenceInfoDocument.State usersState = diff.getUsersState();
        if (usersState == ConferenceInfoDocument.State.FULL) {
            // if users is 'full', all its children must be full
            try {
                newDocument = new ConferenceInfoDocument(diff);
            } catch (XMLException e) {
                Timber.e("Could not create a new ConferenceInfoDocument");
                return -1;
            }
            newDocument.setState(ConferenceInfoDocument.State.FULL);
        }
        else if (usersState == ConferenceInfoDocument.State.DELETED) {
            try {
                newDocument = new ConferenceInfoDocument();
            } catch (XMLException e) {
                Timber.e(e, "Could not create a new ConferenceInfoDocument");
                return -1;
            }

            newDocument.setVersion(diff.getVersion());
            newDocument.setEntity(diff.getEntity());
            newDocument.setUserCount(diff.getUserCount());
        }
        else // 'partial'
        {
            try {
                newDocument = new ConferenceInfoDocument(ourDocument);
            } catch (XMLException e) {
                Timber.e(e, "Could not create a new ConferenceInfoDocument");
                return -1;
            }

            newDocument.setVersion(diff.getVersion());
            newDocument.setEntity(diff.getEntity());
            newDocument.setUserCount(diff.getUserCount());

            for (ConferenceInfoDocument.User user : diff.getUsers()) {
                ConferenceInfoDocument.State userState = user.getState();
                if (userState == ConferenceInfoDocument.State.FULL) {
                    newDocument.removeUser(user.getEntity());
                    newDocument.addUser(user);
                }
                else if (userState == ConferenceInfoDocument.State.DELETED) {
                    newDocument.removeUser(user.getEntity());
                }
                else // partial
                {
                    ConferenceInfoDocument.User ourUser = newDocument.getUser(user.getEntity());
                    ourUser.setDisplayText(user.getDisplayText());
                    for (ConferenceInfoDocument.Endpoint endpoint : user.getEndpoints()) {
                        ConferenceInfoDocument.State endpointState = endpoint.getState();
                        if (endpointState == ConferenceInfoDocument.State.FULL) {
                            ourUser.removeEndpoint(endpoint.getEntity());
                            ourUser.addEndpoint(endpoint);
                        }
                        else if (endpointState == ConferenceInfoDocument.State.DELETED) {
                            ourUser.removeEndpoint(endpoint.getEntity());
                        }
                        else // partial
                        {
                            ConferenceInfoDocument.Endpoint ourEndpoint = ourUser
                                    .getEndpoint(endpoint.getEntity());
                            for (ConferenceInfoDocument.Media media : endpoint.getMedias()) {
                                ourEndpoint.removeMedia(media.getId());
                                ourEndpoint.addMedia(media);
                            }
                        }
                    }
                }
            }
        }
        Timber.d("Applied a partial conference-info notification. Base: %s\nDiff: %s\nResult: %s",
                ourDocument, diff, newDocument);
        return setConferenceInfoDocument(callPeer, newDocument);
    }

    /**
     * @param a A document with state <code>full</code> which to compare to <code>b</code>
     * @param b A document with state <code>full</code> which to compare to <code>a</code>
     *
     * @return <code>false</code> if the two documents are found to be different, <code>true</code>
     * otherwise (that is, it can return true for non-identical documents).
     */
    private boolean conferenceInfoDocumentsMatch(ConferenceInfoDocument a, ConferenceInfoDocument b) {
        if (a.getState() != ConferenceInfoDocument.State.FULL)
            throw new IllegalArgumentException("The 'a' document needs to have state=full");
        if (b.getState() != ConferenceInfoDocument.State.FULL)
            throw new IllegalArgumentException("The 'b' document needs to have state=full");

        if (!stringsMatch(a.getEntity(), b.getEntity()))
            return false;
        else if (a.getUserCount() != b.getUserCount())
            return false;
        else if (a.getUsers().size() != b.getUsers().size())
            return false;

        for (ConferenceInfoDocument.User aUser : a.getUsers()) {
            if (!usersMatch(aUser, b.getUser(aUser.getEntity())))
                return false;
        }
        return true;
    }

    /**
     * Checks whether two <code>ConferenceInfoDocument.User</code> instances match according to the
     * needs of our implementation. Can return <code>true</code> for users which are not identical.
     *
     * @param a A <code>ConferenceInfoDocument.User</code> to compare
     * @param b A <code>ConferenceInfoDocument.User</code> to compare
     *
     * @return <code>false</code> if <code>a</code> and <code>b</code> are found to be different in a way that
     * is significant for our needs, <code>true</code> otherwise.
     */
    private boolean usersMatch(ConferenceInfoDocument.User a, ConferenceInfoDocument.User b) {
        if (a == null && b == null)
            return true;
        else if (a == null || b == null)
            return false;
        else if (!stringsMatch(a.getEntity(), b.getEntity()))
            return false;
        else if (!stringsMatch(a.getDisplayText(), b.getDisplayText()))
            return false;
        else if (a.getEndpoints().size() != b.getEndpoints().size())
            return false;

        for (ConferenceInfoDocument.Endpoint aEndpoint : a.getEndpoints()) {
            if (!endpointsMatch(aEndpoint, b.getEndpoint(aEndpoint.getEntity())))
                return false;
        }

        return true;
    }

    /**
     * Checks whether two <code>ConferenceInfoDocument.Endpoint</code> instances match according to the
     * needs of our implementation. Can return <code>true</code> for endpoints which are not identical.
     *
     * @param a A <code>ConferenceInfoDocument.Endpoint</code> to compare
     * @param b A <code>ConferenceInfoDocument.Endpoint</code> to compare
     *
     * @return <code>false</code> if <code>a</code> and <code>b</code> are found to be different in a way that
     * is significant for our needs, <code>true</code> otherwise.
     */
    private boolean endpointsMatch(ConferenceInfoDocument.Endpoint a,
            ConferenceInfoDocument.Endpoint b) {
        if (a == null && b == null)
            return true;
        else if (a == null || b == null)
            return false;
        else if (!stringsMatch(a.getEntity(), b.getEntity()))
            return false;
        else if (a.getStatus() != b.getStatus())
            return false;
        else if (a.getMedias().size() != b.getMedias().size())
            return false;

        for (ConferenceInfoDocument.Media aMedia : a.getMedias()) {
            if (!mediasMatch(aMedia, b.getMedia(aMedia.getId())))
                return false;
        }
        return true;
    }

    /**
     * Checks whether two <code>ConferenceInfoDocument.Media</code> instances match according to the
     * needs of our implementation. Can return <code>true</code> for endpoints which are not identical.
     *
     * @param a A <code>ConferenceInfoDocument.Media</code> to compare
     * @param b A <code>ConferenceInfoDocument.Media</code> to compare
     *
     * @return <code>false</code> if <code>a</code> and <code>b</code> are found to be different in a way that
     * is significant for our needs, <code>true</code> otherwise.
     */
    private boolean mediasMatch(ConferenceInfoDocument.Media a, ConferenceInfoDocument.Media b) {
        if (a == null && b == null)
            return true;
        else if (a == null || b == null)
            return false;
        else if (!stringsMatch(a.getId(), b.getId()))
            return false;
        else if (!stringsMatch(a.getSrcId(), b.getSrcId()))
            return false;
        else if (!stringsMatch(a.getType(), b.getType()))
            return false;
        else
            return stringsMatch(a.getStatus(), b.getStatus());

    }

    /**
     * @param a A <code>String</code> to compare to <code>b</code>
     * @param b A <code>String</code> to compare to <code>a</code>
     *
     * @return <code>true</code> if and only if <code>a</code> and <code>b</code> are both <code>null</code>, or
     * they are equal as <code>String</code>s
     */
    private boolean stringsMatch(String a, String b) {
        if (a == null && b == null)
            return true;
        else if (a == null || b == null)
            return false;
        return a.equals(b);
    }

    /**
     * Checks whether sending of RFC4575 partial notifications is enabled in the configuration. If
     * disabled, RFC4575 documents will always be sent with state 'full'.
     *
     * @return <code>true</code> if sending of RFC4575 partial notifications is enabled in the
     * configuration.
     */
    private boolean isPartialNotificationEnabled() {
        String s = parentProvider.getAccountID().getAccountProperties().get(PARTIAL_NOTIFICATIONS_PROP_NAME);

        return (s == null || Boolean.parseBoolean(s));
    }

    /**
     * {@inheritDoc}
     *
     * Unimplemented by default, returns <code>null</code>.
     */
    @Override
    public ConferenceDescription setupConference(ChatRoom chatRoom) {
        return null;
    }
}
