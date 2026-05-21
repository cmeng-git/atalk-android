/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.atalk.android.gui.call;

import android.content.Context;
import android.content.Intent;
import android.text.TextUtils;

import java.text.ParseException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.WeakHashMap;

import net.java.sip.communicator.service.protocol.Call;
import net.java.sip.communicator.service.protocol.CallPeer;
import net.java.sip.communicator.service.protocol.CallPeerState;
import net.java.sip.communicator.service.protocol.CallState;
import net.java.sip.communicator.service.protocol.Contact;
import net.java.sip.communicator.service.protocol.ContactResource;
import net.java.sip.communicator.service.protocol.OperationFailedException;
import net.java.sip.communicator.service.protocol.OperationSetAdvancedTelephony;
import net.java.sip.communicator.service.protocol.OperationSetBasicTelephony;
import net.java.sip.communicator.service.protocol.OperationSetResourceAwareTelephony;
import net.java.sip.communicator.service.protocol.OperationSetVideoTelephony;
import net.java.sip.communicator.service.protocol.ProtocolProviderFactory;
import net.java.sip.communicator.service.protocol.ProtocolProviderService;
import net.java.sip.communicator.service.protocol.media.MediaAwareCall;
import net.java.sip.communicator.service.protocol.media.ProtocolMediaActivator;

import org.atalk.android.R;
import org.atalk.android.aTalkApp;
import org.atalk.android.gui.AppGUIActivator;
import org.atalk.android.gui.contactlist.UIContactImpl;
import org.atalk.android.gui.dialogs.DialogActivity;
import org.atalk.service.neomedia.MediaService;
import org.atalk.service.neomedia.MediaUseCase;
import org.atalk.service.neomedia.codec.Constants;
import org.atalk.service.neomedia.codec.EncodingConfiguration;
import org.atalk.service.neomedia.device.MediaDevice;
import org.atalk.service.neomedia.format.MediaFormat;
import org.atalk.util.MediaType;

import org.apache.commons.lang3.StringUtils;

import org.jivesoftware.smackx.avatar.AvatarManager;
import org.jivesoftware.smackx.jingle.JingleManager;

import org.jxmpp.jid.BareJid;

import timber.log.Timber;

/**
 * @author Yana Stamcheva
 * @author Eng Chong Meng
 */
public class CallManager {
    // Jingle Message id / Jingle session-initiate sid
    public static final String CALL_SID = "call_sid";
    public static final String CALL_EVENT = "call_event";

    // True to indicate the jingleMessage <accept/> is auto-send onReceive the <propose/> stanza
    public static final String AUTO_ACCEPT = "auto_accept";

    // android call parameters
    public static final String CALL_TRANSFER = "CallTransfer";

    /**
     * A table mapping protocol <code>Call</code> objects to the GUI dialogs that are currently used to display them.
     * The string ID is an instance of the time when the call is first activated
     */
    private static final Map<String, Call> activeCalls = new HashMap<>();
    private static final Map<Call, VideoCallActivity> videoCalls = new WeakHashMap<>();

    /**
     * A map of active outgoing calls per <code>UIContactImpl</code>.
     */
    private static Map<Call, UIContactImpl> uiContactCalls;

    public synchronized static String addActiveCall(Call call) {
        String key = call.getCallId();
        if (TextUtils.isEmpty(key)) {
            key = JingleManager.randomUuid();
            Timber.e("CallId is not initialized with jingle sid: %s", key);
        }
        synchronized (activeCalls) {
            activeCalls.put(key, call);
        }
        return key;
    }

    public synchronized static void removeActiveCall(String callKey) {
        synchronized (activeCalls) {
            activeCalls.remove(callKey);
        }
    }

    public synchronized static void removeActiveCall(Call call) {
        synchronized (activeCalls) {
            if (!activeCalls.containsValue(call))
                return;

            Iterator<String> activeCallsIter = activeCalls.keySet().iterator();
            ArrayList<String> toRemove = new ArrayList<>();
            while (activeCallsIter.hasNext()) {
                String key = activeCallsIter.next();
                if (Objects.equals(activeCalls.get(key), call))
                    toRemove.add(key);
            }
            for (String removeKey : toRemove) {
                removeActiveCall(removeKey);
            }
            removeVideoCall(call);
        }
    }

    /**
     * @param callKey an instance of the time when the call is first activated,
     * it is used for later identification of the call.
     *
     * @return the active call
     */
    public synchronized static Call getActiveCall(String callKey) {
        synchronized (activeCalls) {
            return activeCalls.get(callKey);
        }
    }

    /**
     * Returns currently active calls.
     *
     * @return collection of currently active calls.
     */
    public static Collection<Call> getActiveCalls() {
        synchronized (activeCalls) {
            return activeCalls.values();
        }
    }

    /**
     * Returns the number of currently active calls.
     *
     * @return the number of currently active calls.
     */
    public synchronized static int getActiveCallsCount() {
        synchronized (activeCalls) {
            return activeCalls.size();
        }
    }

    public synchronized static void addVideoCall(Call call, VideoCallActivity callActivity) {
        synchronized (videoCalls) {
            videoCalls.put(call, callActivity);
        }
    }

    private synchronized static void removeVideoCall(Call call) {
        synchronized (videoCalls) {
            videoCalls.remove(call);
        }
    }

    /**
     * Switch the UI to the request call. Finish() old activity and re-start new.
     * When switching between two calls from notification using pendingIntent, a new instance of the VideoCall
     * is created on each call switch. Need user to hangup each newly created activity. Solution not working:
     * a. ActivityManager.moveTaskToFront()
     * b. FLAG_ACTIVITY_REORDER_TO_FRONT.
     *
     * @param call The call to switch to.
     */
    public static void callSwitchOrTransfer(Call call, boolean transfer) {
        if (call != null) {
            VideoCallActivity vCall = videoCalls.get(call);
            if (vCall != null) {
                videoCalls.remove(call);
                vCall.finish(); // finish existing before start new.

                Context ctx = aTalkApp.getInstance();
                Intent videoCall = VideoCallActivity.createVideoCallIntent(ctx, call.getCallId());
                videoCall.putExtra(CallManager.CALL_TRANSFER, transfer);
                ctx.startActivity(videoCall);
            }
        }
    }

    /**
     * Answers the given call with the required media i.e. audio/video.
     *
     * @param call the call to answer
     * @param isVideoCall the incoming call type (audio/video?)
     */
    public static void answerCall(Call call, boolean isVideoCall) {
        new AnswerCallThread(call, isVideoCall).start();
    }

    /**
     * Hang ups the given call.
     *
     * @param call the call to hang up
     */
    public static void hangupCall(Call call) {
        new HangupCallThread(call).start();
    }

    /**
     * Creates a call to the contact represented by the given string.
     *
     * @param protocolProvider the protocol provider to which this call belongs.
     * @param contact the contact to call to
     * @param isVideoCall true to setup video call
     */
    public static void createCall(ProtocolProviderService protocolProvider, String contact, boolean isVideoCall) {
        new CreateCallThread(protocolProvider, contact, isVideoCall).start();
    }

    /**
     * Creates a call to the contact represented by the given string.
     *
     * @param pps the protocol provider to which this call belongs.
     * @param contact the contact to call to
     * @param uiContact the meta contact we're calling
     * @param isVideoCall true to setup video call
     */
    public static void createCall(ProtocolProviderService pps, String contact, UIContactImpl uiContact, boolean isVideoCall) {
        new CreateCallThread(pps, null, null, uiContact, contact, isVideoCall).start();
    }

    /**
     * Enables/disables local video for a specific <code>Call</code>.
     *
     * @param call the <code>Call</code> to enable/disable to local video for
     * @param enable <code>true</code> to enable the local video; otherwise, <code>false</code>
     */
    public static void enableLocalVideo(Call call, boolean enable) {
        new EnableLocalVideoThread(call, enable).start();
    }

    /**
     * Indicates if the local video is currently enabled for the given <code>call</code>.
     *
     * @param call the <code>Call</code>, for which we would to check if the local video streaming is currently enabled
     *
     * @return <code>true</code> if the local video streaming is currently enabled for the given
     * <code>call</code>, <code>false</code> otherwise
     */
    public static boolean isLocalVideoEnabled(Call call) {
        OperationSetVideoTelephony telephony = call.getProtocolProvider().getOperationSet(OperationSetVideoTelephony.class);
        return (telephony != null) && telephony.isLocalVideoAllowed(call);
    }

    /**
     * Puts on or off hold the given <code>callPeer</code>.
     *
     * @param callPeer the peer to put on/off hold
     * @param isOnHold indicates the action (on hold or off hold)
     */
    public static void putOnHold(CallPeer callPeer, boolean isOnHold) {
        new PutOnHoldCallPeerThread(callPeer, isOnHold).start();
    }

    /**
     * Puts on or off hold the given <code>call</code>. (cmeng-android)
     *
     * @param call the peer to put on/off hold
     * @param isOnHold indicates the action (on hold or off hold)
     */
    public static void putOnHold(Call call, boolean isOnHold) {
        Iterator<? extends CallPeer> peers = call.getCallPeers();
        while (peers.hasNext()) {
            putOnHold(peers.next(), isOnHold);
        }
    }

    /**
     * Transfers the given <code>peer</code> to the given <code>target</code>.
     *
     * @param peer the <code>CallPeer</code> to transfer
     * @param target the <code>CallPeer</code> target to transfer to
     */
    public static void transferCall(CallPeer peer, CallPeer target) {
        OperationSetAdvancedTelephony<?> telephony
                = peer.getCall().getProtocolProvider().getOperationSet(OperationSetAdvancedTelephony.class);

        if (telephony != null) {
            try {
                telephony.transfer(peer, target);
            }
            catch (OperationFailedException ex) {
                String error = aTalkApp.getResString(R.string.call_transfer_failed,
                        peer.getAddress(), target.getAddress(), ex.getMessage());
                Timber.w("%s", error);
                DialogActivity.showDialog(aTalkApp.getInstance(),
                        aTalkApp.getResString(R.string.call_transfer_call), error);
            }
        }
    }

    /**
     * Transfers the given <code>peer</code> to the given <code>target</code>.
     *
     * @param peer the <code>CallPeer</code> to transfer
     * @param target the target of the transfer
     */
    public static void transferCall(CallPeer peer, String target) {
        OperationSetAdvancedTelephony<?> telephony
                = peer.getCall().getProtocolProvider().getOperationSet(OperationSetAdvancedTelephony.class);

        if (telephony != null) {
            try {
                telephony.transfer(peer, target);
            }
            catch (OperationFailedException ex) {
                String error = aTalkApp.getResString(R.string.call_transfer_failed,
                        peer.getAddress(), target, ex.getMessage());
                Timber.w("%s", error);
                DialogActivity.showDialog(aTalkApp.getInstance(),
                        aTalkApp.getResString(R.string.call_transfer_call), error);
            }
        }
    }

    /**
     * Returns a collection of all currently in progress calls. A call is active if it is in
     * progress so the method merely delegates to
     *
     * @return a collection of all currently in progress calls.
     */
    public static Collection<Call> getInProgressCalls() {
        return getActiveCalls();
    }

    /**
     * Returns the image corresponding to the given <code>peer</code>.
     *
     * @param peer the call peer, for which we're returning an image
     *
     * @return the peer image
     */
    public static byte[] getPeerImage(CallPeer peer) {
        byte[] image = null;
        BareJid peerJid = peer.getPeerJid().asBareJid();
        // We search for a contact corresponding to this call peer and try to get its image.
        if (peer.getPeerJid() != null) {
            image = AvatarManager.getAvatarImageByJid(peerJid);
        }
        return image;
    }

    /**
     * Indicates if the given call is currently muted.
     *
     * @param call the call to check
     *
     * @return <code>true</code> if the given call is currently muted, <code>false</code> - otherwise
     */
    public static boolean isMute(Call call) {
        if (call instanceof MediaAwareCall<?, ?, ?>) {
            return ((MediaAwareCall<?, ?, ?>) call).isMute();
        }
        else {
            return false;
        }
    }

    /**
     * Mutes/unmutes the given call.
     *
     * @param call the call to mute/unmute
     * @param isMute <code>true</code> to mute the call, <code>false</code> to unmute it
     */
    public static void setMute(Call call, boolean isMute) {
        Timber.d("Set mute to %s", isMute);
        new MuteThread(call, isMute).start();
    }

    /**
     * Creates the mute call thread.
     */
    private static class MuteThread extends Thread {
        private final Call call;
        private final boolean isMute;

        public MuteThread(Call call, boolean isMute) {
            this.call = call;
            this.isMute = isMute;
        }

        public void run() {
            if (call != null) {
                OperationSetBasicTelephony<?> telephony
                        = call.getProtocolProvider().getOperationSet(OperationSetBasicTelephony.class);
                telephony.setMute(call, isMute);
            }
        }
    }

    /**
     * Checks if the call has been put on hold by local user.
     *
     * @param call the <code>Call</code> that will be checked.
     *
     * @return <code>true</code> if given <code>Call</code> is locally on hold.
     */
    public static boolean isLocallyOnHold(Call call) {
        boolean onHold = false;
        Iterator<? extends CallPeer> peers = call.getCallPeers();
        if (peers.hasNext()) {
            CallPeerState peerState = call.getCallPeers().next().getState();
            onHold = CallPeerState.ON_HOLD_LOCALLY.equals(peerState) || CallPeerState.ON_HOLD_MUTUALLY.equals(peerState);
        }
        else {
            Timber.w("No peer belongs to call: %s", call.toString());
        }
        return onHold;
    }

    /**
     * Returns of supported/enabled list of audio formats for a provider.
     *
     * @param device the <code>MediaDevice</code>, which audio formats we're looking for
     * @param protocolProvider the provider to check.
     *
     * @return list of supported/enabled audio formats or empty list otherwise.
     */
    private static List<MediaFormat> getAudioFormats(MediaDevice device, ProtocolProviderService protocolProvider) {
        List<MediaFormat> res = new ArrayList<>();

        Map<String, String> accountProperties = protocolProvider.getAccountID().getAccountProperties();
        String overrideEncodings = accountProperties.get(ProtocolProviderFactory.OVERRIDE_ENCODINGS);

        List<MediaFormat> formats;
        if (Boolean.parseBoolean(overrideEncodings)) {
            /*
             * The account properties associated with account override the global
             * EncodingConfiguration.
             */
            EncodingConfiguration encodingConfiguration
                    = ProtocolMediaActivator.getMediaService().createEmptyEncodingConfiguration();

            encodingConfiguration.loadProperties(accountProperties, ProtocolProviderFactory.ENCODING_PROP_PREFIX);
            formats = device.getSupportedFormats(null, null, encodingConfiguration);
        }
        else /* The global EncodingConfiguration is in effect. */ {
            formats = device.getSupportedFormats();
        }

        // skip the special telephony event
        for (MediaFormat format : formats) {
            if (!format.getEncoding().equals(Constants.TELEPHONE_EVENT))
                res.add(format);
        }
        return res;
    }

    /**
     * Creates a new (audio-only or video) <code>Call</code> to a contact specified as a
     * <code>Contact</code> instance or a <code>String</code> contact address/identifier.
     */
    private static class CreateCallThread extends Thread {
        /**
         * The contact to call.
         */
        private final Contact contact;

        /**
         * The specific contact resource to call.
         */
        private final ContactResource contactResource;

        /**
         * The <code>UIContactImpl</code> we're calling.
         */
        private final UIContactImpl uiContact;

        /**
         * The protocol provider through which the call goes.
         */
        private final ProtocolProviderService protocolProvider;

        /**
         * The string to call.
         */
        private final String stringContact;

        /**
         * The indicator which determines whether this instance is to create a new video (as
         * opposed to audio-only) <code>Call</code>.
         */
        private final boolean video;

        /**
         * Creates an instance of <code>CreateCallThread</code>.
         *
         * @param protocolProvider the protocol provider through which the call is going.
         * @param contact the contact to call
         * @param contactResource the specific <code>ContactResource</code> we're calling
         * @param video indicates if this is a video call
         */
        public CreateCallThread(ProtocolProviderService protocolProvider, Contact contact,
                ContactResource contactResource, boolean video) {
            this(protocolProvider, contact, contactResource, null, null, video);
        }

        /**
         * Creates an instance of <code>CreateCallThread</code>.
         *
         * @param protocolProvider the protocol provider through which the call is going.
         * @param contact the contact to call
         * @param video indicates if this is a video call
         */
        public CreateCallThread(ProtocolProviderService protocolProvider, String contact, boolean video) {
            this(protocolProvider, null, null, null, contact, video);
        }

        /**
         * Initializes a new <code>CreateCallThread</code> instance which is to create a new
         * <code>Call</code> to a contact specified either as a <code>Contact</code> instance or as a
         * <code>String</code> contact address/identifier.
         *
         * The constructor is private because it relies on its arguments being validated prior to its invocation.
         *
         * @param protocolProvider the ProtocolProviderService which is to perform the establishment of the new Call
         * @param contact the contact to call
         * @param contactResource the specific contact resource to call
         * @param uiContact the ui contact we're calling
         * @param stringContact the string callee to call
         * @param video <code>true</code> if this instance is to create a new video (as opposed to audio-only) <code>Call</code>
         */
        public CreateCallThread(ProtocolProviderService protocolProvider, Contact contact,
                ContactResource contactResource, UIContactImpl uiContact, String stringContact, boolean video) {
            this.protocolProvider = protocolProvider;
            this.contact = contact;
            this.contactResource = contactResource;
            this.uiContact = uiContact;
            this.stringContact = stringContact;
            this.video = video;
        }

        @Override
        public void run() {
            if (!video) {
                // if it is not video let's check for available audio codec and available audio devices
                MediaService mediaService = AppGUIActivator.getMediaService();
                MediaDevice dev = mediaService.getDefaultDevice(MediaType.AUDIO, MediaUseCase.CALL);

                List<MediaFormat> formats = getAudioFormats(dev, protocolProvider);
                String errMsg = null;

                if (!dev.getDirection().allowsSending())
                    errMsg = aTalkApp.getResString(R.string.call_no_audio_device);
                else if (formats.isEmpty()) {
                    errMsg = aTalkApp.getResString(R.string.call_no_audio_codec);
                }
                if (errMsg != null) {
                    DialogActivity.showDialog(aTalkApp.getInstance(),
                            R.string.call_audio, R.string.call_no_device_codec_H, errMsg);
                    return;
                }
            }

            Contact contact = this.contact;
            String stringContact = this.stringContact;
            if (contact != null) {
                stringContact = contact.getAddress();
                contact = null;
            }

            try {
                if (video) {
                    internalCallVideo(protocolProvider, contact, uiContact, stringContact);
                }
                else {
                    internalCall(protocolProvider, contact, stringContact, contactResource, uiContact);
                }
            }
            catch (Throwable t) {
                if (t instanceof ThreadDeath)
                    throw (ThreadDeath) t;

                Timber.e(t, "The call could not be created: ");
                String message = aTalkApp.getResString(R.string.create_call_failed);

                if (t.getMessage() != null)
                    message += "\n" + t.getMessage();
                DialogActivity.showDialog(aTalkApp.getInstance(), aTalkApp.getResString(R.string.error), message);
            }
        }
    }

    /**
     * Creates a video call through the given <code>protocolProvider</code>.
     *
     * @param protocolProvider the <code>ProtocolProviderService</code> through which to make the call
     * @param contact the <code>Contact</code> to call
     * @param uiContact the <code>UIContactImpl</code> we're calling
     * @param stringContact the contact string to call
     *
     * @throws OperationFailedException thrown if the call operation fails
     * @throws ParseException thrown if the contact string is malformatted
     */
    private static void internalCallVideo(ProtocolProviderService protocolProvider,
            Contact contact, UIContactImpl uiContact, String stringContact)
            throws OperationFailedException, ParseException {
        OperationSetVideoTelephony telephony = protocolProvider.getOperationSet(OperationSetVideoTelephony.class);
        Call createdCall = null;
        if (telephony != null) {
            if (contact != null) {
                createdCall = telephony.createVideoCall(contact);
            }
            else if (stringContact != null)
                createdCall = telephony.createVideoCall(stringContact);
        }

        if (uiContact != null && createdCall != null)
            addUIContactCall(uiContact, createdCall);
        // if (createdCall != null)
        // addActiveCall(createdCall);
    }

    /**
     * Creates a call through the given <code>protocolProvider</code>.
     *
     * @param protocolProvider the <code>ProtocolProviderService</code> through which to make the call
     * @param contact the <code>Contact</code> to call
     * @param stringContact the contact string to call
     * @param contactResource the specific <code>ContactResource</code> to call
     * @param uiContact the <code>UIContactImpl</code> we're calling
     *
     * @throws OperationFailedException thrown if the call operation fails
     * @throws ParseException thrown if the contact string is malformatted
     */
    private static void internalCall(ProtocolProviderService protocolProvider, Contact contact,
            String stringContact, ContactResource contactResource, UIContactImpl uiContact)
            throws OperationFailedException, ParseException {
        OperationSetBasicTelephony<?> telephony = protocolProvider.getOperationSet(OperationSetBasicTelephony.class);
        OperationSetResourceAwareTelephony resourceTelephony
                = protocolProvider.getOperationSet(OperationSetResourceAwareTelephony.class);

        Call createdCall = null;
        if (resourceTelephony != null && contactResource != null) {
            if (contact != null)
                createdCall = resourceTelephony.createCall(contact, contactResource);
            else if (StringUtils.isNotEmpty(stringContact))
                createdCall = resourceTelephony.createCall(stringContact, contactResource.getResourceName());
        }
        else if (telephony != null) {
            if (contact != null) {
                createdCall = telephony.createCall(contact);
            }
            else if (StringUtils.isNotEmpty(stringContact))
                createdCall = telephony.createCall(stringContact);
        }

        if (uiContact != null && createdCall != null)
            addUIContactCall(uiContact, createdCall);
    }

    /**
     * Returns the <code>MetaContact</code>, to which the given <code>Call</code> was initially created.
     *
     * @param call the <code>Call</code>, which corresponding <code>MetaContact</code> we're looking for
     *
     * @return the <code>UIContactImpl</code>, to which the given <code>Call</code> was initially created
     */
    public static UIContactImpl getCallUIContact(Call call) {
        if (uiContactCalls != null)
            return uiContactCalls.get(call);
        return null;
    }

    /**
     * Adds a call for a <code>metaContact</code>.
     *
     * @param uiContact the <code>UIContact</code> corresponding to the call
     * @param call the <code>Call</code> corresponding to the <code>MetaContact</code>
     */
    private static void addUIContactCall(UIContactImpl uiContact, Call call) {
        if (uiContactCalls == null)
            uiContactCalls = new WeakHashMap<>();

        uiContactCalls.put(call, uiContact);
    }

    /**
     * Answers to all <code>CallPeer</code>s associated with a specific <code>Call</code> and, optionally,
     * does that in a telephony conference with an existing <code>Call</code>.
     */
    private static class AnswerCallThread extends Thread {
        /**
         * The <code>Call</code> which is to be answered.
         */
        private final Call call;

        /**
         * The indicator which determines whether this instance is to answer {@link #call} with
         * video.
         */
        private final boolean isVideoCall;

        public AnswerCallThread(Call call, boolean isVideoCall) {
            this.call = call;
            this.isVideoCall = isVideoCall;
        }

        @Override
        public void run() {
            ProtocolProviderService pps = call.getProtocolProvider();
            Iterator<? extends CallPeer> peers = call.getCallPeers();

            while (peers.hasNext()) {
                CallPeer peer = peers.next();

                if (isVideoCall) {
                    OperationSetVideoTelephony telephony = pps.getOperationSet(OperationSetVideoTelephony.class);
                    try {
                        telephony.answerVideoCallPeer(peer);
                    }
                    catch (OperationFailedException ofe) {
                        Timber.e("Could not answer %s with video because of the following exception: %s",
                                peer, ofe.getMessage());
                    }
                }
                else {
                    OperationSetBasicTelephony<?> telephony = pps.getOperationSet(OperationSetBasicTelephony.class);
                    try {
                        telephony.answerCallPeer(peer);
                    }
                    catch (OperationFailedException ofe) {
                        Timber.e("Could not answer %s because of the following exception: %s", peer, ofe.getMessage());
                    }
                }
            }
        }
    }

    /**
     * Hangs up a specific <code>Call</code> (i.e. all <code>CallPeer</code>s associated with a
     * <code>Call</code>), <code>CallConference</code> (i.e. all <code>Call</code>s participating in a
     * <code>CallConference</code>), or <code>CallPeer</code>.
     */
    private static class HangupCallThread extends Thread {
        private final Call call;
        private final CallPeer peer;

        /**
         * Initializes a new <code>HangupCallThread</code> instance which is to hang up a specific
         * <code>Call</code> i.e. all <code>CallPeer</code>s associated with the <code>Call</code>.
         *
         * @param call the <code>Call</code> whose associated <code>CallPeer</code>s are to be hanged up
         */
        public HangupCallThread(Call call) {
            // this.call = call;
            this(call, null);
        }

        /**
         * Initializes a new <code>HangupCallThread</code> instance which is to hang up a specific
         * <code>CallPeer</code>.
         *
         * @param peer the <code>CallPeer</code> to hang up
         */
        public HangupCallThread(CallPeer peer) {
            this(null, peer);
        }

        /**
         * Initializes a new <code>HangupCallThread</code> instance which is to hang up a specific
         * <code>Call</code>, <code>CallConference</code>, or <code>CallPeer</code>.
         *
         * @param call the <code>Call</code> whose associated <code>CallPeer</code>s are to be hanged up
         * @param peer the <code>CallPeer</code> to hang up
         */
        private HangupCallThread(Call call, CallPeer peer) {
            this.call = call;
            this.peer = peer;
        }

        @Override
        public void run() {
            /*
             * There is only an OperationSet which hangs up a CallPeer at a time so prepare a list
             * of all CallPeers to be hanged up.
             */
            Set<CallPeer> peers = new HashSet<>();

            if (call != null) {
                Iterator<? extends CallPeer> peerIter = call.getCallPeers();
                while (peerIter.hasNext()) {
                    peers.add(peerIter.next());
                }
            }

            if (peer != null) {
                peers.add(peer);
            }

            for (CallPeer peer : peers) {
                OperationSetBasicTelephony<?> basicTelephony
                        = peer.getProtocolProvider().getOperationSet(OperationSetBasicTelephony.class);
                try {
                    // Must send JingleMessage retract to close the loop if Jingle RTP has yet to start
                    if (CallState.CALL_INITIALIZATION.equals(peer.getCall().getCallState())) {
                        JingleMessageSessionImpl.sendJingleMessageRetract(peer.getPeerJid().asBareJid(), peer.getCall().getCallId());
                        String reasonText = aTalkApp.getResString(R.string.call_retracted, "Caller");
                        basicTelephony.hangupCallPeer(peer, OperationSetBasicTelephony.HANGUP_REASON_CALL_RETRACT, reasonText);
                    } else {
                        basicTelephony.hangupCallPeer(peer);
                    }
                }
                catch (OperationFailedException ofe) {
                    Timber.e(ofe, "Could not hang up: %s", peer);
                }
            }
            removeActiveCall(call);
        }
    }

    /**
     * Creates the EnableLocalVideoThread.
     */
    private static class EnableLocalVideoThread extends Thread {
        private final Call call;
        private final boolean enable;

        /**
         * Creates the EnableLocalVideoThread.
         *
         * @param call the call, for which to enable/disable
         * @param enable allow to have LocalVideo streaming if true
         */
        public EnableLocalVideoThread(Call call, boolean enable) {
            this.call = call;
            this.enable = enable;
        }

        @Override
        public void run() {
            OperationSetVideoTelephony videoTelephony
                    = call.getProtocolProvider().getOperationSet(OperationSetVideoTelephony.class);
            if (videoTelephony != null) {
                try {
                    videoTelephony.setLocalVideoAllowed(call, enable);
                }
                catch (OperationFailedException ex) {
                    Timber.e("Failed to toggle the streaming of local video. %s", ex.getMessage());
                }
            }
        }
    }

    /**
     * Puts on hold the given <code>CallPeer</code>.
     */
    private static class PutOnHoldCallPeerThread extends Thread {
        private final CallPeer callPeer;
        private final boolean isOnHold;

        public PutOnHoldCallPeerThread(CallPeer callPeer, boolean isOnHold) {
            this.callPeer = callPeer;
            this.isOnHold = isOnHold;
        }

        @Override
        public void run() {
            OperationSetBasicTelephony<?> telephony
                    = callPeer.getProtocolProvider().getOperationSet(OperationSetBasicTelephony.class);
            try {
                if (isOnHold)
                    telephony.putOnHold(callPeer);
                else
                    telephony.putOffHold(callPeer);
            }
            catch (OperationFailedException ex) {
                Timber.e(ex, "Failed to put %s %s", callPeer.getAddress(), (isOnHold ? " on hold." : " off hold. "));
            }
        }
    }
}
