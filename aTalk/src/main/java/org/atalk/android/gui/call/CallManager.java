/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.atalk.android.gui.call;

import android.text.TextUtils;

import net.java.sip.communicator.impl.phonenumbers.PhoneNumberI18nServiceImpl;
import net.java.sip.communicator.service.protocol.*;
import net.java.sip.communicator.service.protocol.media.*;
import net.java.sip.communicator.util.ConfigurationUtils;
import net.java.sip.communicator.util.NetworkUtils;
import net.java.sip.communicator.util.account.AccountUtils;

import org.apache.commons.lang3.StringUtils;
import org.atalk.android.R;
import org.atalk.android.aTalkApp;
import org.atalk.android.gui.AndroidGUIActivator;
import org.atalk.android.gui.contactlist.UIContactImpl;
import org.atalk.android.gui.util.AndroidUtils;
import org.atalk.android.util.java.awt.Component;
import org.atalk.android.util.javax.swing.JComponent;
import org.atalk.service.neomedia.*;
import org.atalk.service.neomedia.codec.Constants;
import org.atalk.service.neomedia.codec.EncodingConfiguration;
import org.atalk.service.neomedia.device.MediaDevice;
import org.atalk.service.neomedia.format.MediaFormat;
import org.atalk.util.MediaType;
import org.jivesoftware.smackx.avatar.AvatarManager;
import org.jxmpp.jid.BareJid;

import java.text.ParseException;
import java.util.*;

import timber.log.Timber;

/**
 * @author Yana Stamcheva
 * @author Eng Chong Meng
 */
public class CallManager
{
    // Jingle Message id / Jingle session-initiate sid
    public static final String CALL_SID = "call_sid";
    public static final String CALL_EVENT = "call_event";

    // android call parameter
    public static final String CALL_IDENTIFIER = "CallIdentifier";

    /**
     * A table mapping protocol <tt>Call</tt> objects to the GUI dialogs that are currently used to display them.
     * The string ID is an instance of the time when the call is first activated
     */
    private static final Map<String, Call> activeCalls = new HashMap<>();

    /**
     * A map of active outgoing calls per <tt>UIContactImpl</tt>.
     */
    private static Map<Call, UIContactImpl> uiContactCalls;

    public synchronized static String addActiveCall(Call call)
    {
        String key = call.getCallId();
        if (TextUtils.isEmpty(key)) {
            key = String.valueOf(System.currentTimeMillis());
            Timber.e("CallId is not initialized with jingle sid: %s", key);
        }

        synchronized (activeCalls) {
            activeCalls.put(key, call);
        }
        return key;
    }

    public synchronized static void removeActiveCall(String callKey)
    {
        synchronized (activeCalls) {
            activeCalls.remove(callKey);
        }
    }

    public synchronized static void removeActiveCall(Call call)
    {
        synchronized (activeCalls) {
            if (!activeCalls.containsValue(call))
                return;

            Iterator<String> activeCallsIter = activeCalls.keySet().iterator();
            ArrayList<String> toRemove = new ArrayList<>();
            while (activeCallsIter.hasNext()) {
                String key = activeCallsIter.next();
                if (activeCalls.get(key).equals(call))
                    toRemove.add(key);
            }
            for (String removeKey : toRemove) {
                removeActiveCall(removeKey);
            }
        }
    }

    /**
     * @param callKey an instance of the time when the call is first activated,
     * it is used for later identification of the call.
     * @return the active call
     */
    public synchronized static Call getActiveCall(String callKey)
    {
        synchronized (activeCalls) {
            return activeCalls.get(callKey);
        }
    }

    /**
     * Returns currently active calls.
     *
     * @return collection of currently active calls.
     */
    public static Collection<Call> getActiveCalls()
    {
        synchronized (activeCalls) {
            return activeCalls.values();
        }
    }

    /**
     * Returns the number of currently active calls.
     *
     * @return the number of currently active calls.
     */
    public synchronized static int getActiveCallsCount()
    {
        synchronized (activeCalls) {
            return activeCalls.size();
        }
    }

    /**
     * Answers the given call with the required media.
     *
     * @param call the call to answer
     * @param isVideoCall the incoming call type (audio/video?)
     */
    public static void answerCall(Call call, boolean isVideoCall)
    {
        answerCall(call, null, isVideoCall);
    }

    /**
     * Answers a specific <tt>Call</tt> with or without video and, optionally, does that in a
     * telephony conference with an existing <tt>Call</tt>.
     *
     * @param call the call to answer
     * @param existingCall current call in progress
     * @param isVideoCall the incoming call type (audio/video?)
     */
    private static void answerCall(Call call, Call existingCall, boolean isVideoCall)
    {
        // if (existingCall == null)
        // openCallContainerIfNecessary(call);
        new AnswerCallThread(call, existingCall, isVideoCall).start();
    }

    /**
     * Answers the given call in an existing call. It will end up with a conference call.
     *
     * @param call the call to answer
     */
    public static void answerCallInFirstExistingCall(Call call)
    {
        // Find the first existing call.
        Iterator<Call> existingCallIter = getInProgressCalls().iterator();
        Call existingCall = existingCallIter.hasNext() ? existingCallIter.next() : null;
        answerCall(call, existingCall, false /* without video */);
    }

    /**
     * Merges specific existing <tt>Call</tt>s into a specific telephony conference.
     *
     * @param conference the conference
     * @param calls list of calls
     */
    public static void mergeExistingCalls(CallConference conference, Collection<Call> calls)
    {
        new MergeExistingCalls(conference, calls).start();
    }

    /**
     * Hang ups the given call.
     *
     * @param call the call to hang up
     */
    public static void hangupCall(Call call)
    {
        new HangupCallThread(call).start();
    }

    /**
     * Hang ups the given <tt>callPeer</tt>.
     *
     * @param peer the <tt>CallPeer</tt> to hang up
     */
    public static void hangupCallPeer(CallPeer peer)
    {
        new HangupCallThread(peer).start();
    }

    /**
     * Asynchronously hangs up the <tt>Call</tt>s participating in a specific <tt>CallConference</tt>.
     *
     * @param conference the <tt>CallConference</tt> whose participating <tt>Call</tt>s are to be hanged up
     */
    public static void hangupCalls(CallConference conference)
    {
        new HangupCallThread(conference).start();
    }

    /**
     * Creates a call to the contact represented by the given string.
     *
     * @param protocolProvider the protocol provider to which this call belongs.
     * @param contact the contact to call to
     * @param isVideoCall true to setup video call
     */
    public static void createCall(ProtocolProviderService protocolProvider, String contact, boolean isVideoCall)
    {
        new CreateCallThread(protocolProvider, contact, isVideoCall).start();
    }

    /**
     * Creates a call to the contact represented by the given string.
     *
     * @param protocolProvider the protocol provider to which this call belongs.
     * @param contact the contact to call to
     * @param uiContact the meta contact we're calling
     * @param isVideoCall true to setup video call
     */
    public static void createCall(ProtocolProviderService protocolProvider, String contact, UIContactImpl uiContact,
            boolean isVideoCall)
    {
        new CreateCallThread(protocolProvider, null, null, uiContact, contact, null, null, isVideoCall).start();
    }

    /**
     * Enables/disables local video for a specific <tt>Call</tt>.
     *
     * @param call the <tt>Call</tt> to enable/disable to local video for
     * @param enable <tt>true</tt> to enable the local video; otherwise, <tt>false</tt>
     */
    public static void enableLocalVideo(Call call, boolean enable)
    {
        new EnableLocalVideoThread(call, enable).start();
    }

    /**
     * Indicates if the desktop sharing is currently enabled for the given <tt>call</tt>.
     *
     * @param call the <tt>Call</tt>, for which we would to check if the desktop sharing is currently enabled
     * @return <tt>true</tt> if the desktop sharing is currently enabled for the given
     * <tt>call</tt>, <tt>false</tt> otherwise
     */
    public static boolean isLocalVideoEnabled(Call call)
    {
        OperationSetVideoTelephony telephony = call.getProtocolProvider().getOperationSet(OperationSetVideoTelephony.class);
        return (telephony != null) && telephony.isLocalVideoAllowed(call);
    }

    /**
     * Creates a call to the given call string. The given component indicates where should be
     * shown the "call via" menu if needed.
     *
     * @param callString the string to call
     * @param c the component, which indicates where should be shown the "call via" menu if needed
     */
    public static void createCall(String callString, JComponent c)
    {
        createCall(callString, c, null);
    }

    /**
     * Creates a call to the given call string. The given component indicates where should be
     * shown the "call via" menu if needed.
     *
     * @param callString the string to call
     * @param c the component, which indicates where should be shown the "call via" menu if needed
     * @param l listener that is notified when the call interface has been started after call was created
     */
    public static void createCall(String callString, JComponent c, CallInterfaceListener l)
    {
        callString = callString.trim();

        // Removes special characters from phone numbers.
        if (ConfigurationUtils.isNormalizePhoneNumber() && !NetworkUtils.isValidIPAddress(callString)) {
            callString = AndroidGUIActivator.getPhoneNumberI18nService().normalize(callString);
        }

        List<ProtocolProviderService> telephonyProviders = CallManager.getTelephonyProviders();
        if (telephonyProviders.size() == 1) {
            CallManager.createCall(telephonyProviders.get(0), callString, false);

            if (l != null)
                l.callInterfaceStarted();
        }
        else if (telephonyProviders.size() > 1) {
            /*
             * Allow plugins which do not have a (Jitsi) UI to create calls by automatically
             * picking up a telephony provider.
             */
            if (c == null) {
                ProtocolProviderService preferredTelephonyProvider = null;

                for (ProtocolProviderService telephonyProvider : telephonyProviders) {
                    try {
                        OperationSetPresence presenceOpSet
                                = telephonyProvider.getOperationSet(OperationSetPresence.class);

                        if ((presenceOpSet != null) && (presenceOpSet.findContactByID(callString) != null)) {
                            preferredTelephonyProvider = telephonyProvider;
                            break;
                        }
                    } catch (Throwable t) {
                        if (t instanceof ThreadDeath)
                            throw (ThreadDeath) t;
                    }
                }
                if (preferredTelephonyProvider == null)
                    preferredTelephonyProvider = telephonyProviders.get(0);

                CallManager.createCall(preferredTelephonyProvider, callString, false);
                if (l != null)
                    l.callInterfaceStarted();
            }
            else {
                ChooseCallAccountPopupMenu chooseAccountDialog
                        = new ChooseCallAccountPopupMenu(c, callString, telephonyProviders, l);

                // chooseAccountDialog.setLocation(c.getLocation());
                chooseAccountDialog.showPopupMenu();
            }
        }
        else {
            AndroidUtils.showAlertDialog(aTalkApp.getGlobalContext(),
                    R.string.service_gui_WARNING, R.string.service_gui_NO_ONLINE_TELEPHONY_ACCOUNT);
        }
    }

    /**
     * Creates a call to the given list of contacts.
     *
     * @param protocolProvider the protocol provider to which this call belongs.
     * @param callees the list of contacts to call to
     */
    public static void createConferenceCall(String[] callees, ProtocolProviderService protocolProvider)
    {
        Map<ProtocolProviderService, List<String>> crossProtocolCallees = new HashMap<>();
        crossProtocolCallees.put(protocolProvider, Arrays.asList(callees));
        createConferenceCall(crossProtocolCallees);
    }

    /**
     * Invites the given list of <tt>callees</tt> to the given conference <tt>call</tt>.
     *
     * @param callees the list of contacts to invite
     * @param call the protocol provider to which this call belongs
     */
    public static void inviteToConferenceCall(String[] callees, Call call)
    {
        Map<ProtocolProviderService, List<String>> crossProtocolCallees = new HashMap<>();
        crossProtocolCallees.put(call.getProtocolProvider(), Arrays.asList(callees));
        inviteToConferenceCall(crossProtocolCallees, call);
    }

    /**
     * Invites the given list of <tt>callees</tt> to the given conference <tt>call</tt>.
     *
     * @param callees the list of contacts to invite
     * @param call existing call
     */
    public static void inviteToConferenceCall(Map<ProtocolProviderService, List<String>> callees, Call call)
    {
        new InviteToConferenceCallThread(callees, call).start();
    }

    /**
     * Invites specific <tt>callees</tt> to a specific telephony conference.
     *
     * @param callees the list of contacts to invite
     * @param conference the telephony conference to invite the specified <tt>callees</tt> into
     */
    public static void inviteToConferenceCall(Map<ProtocolProviderService, List<String>> callees,
            CallConference conference)
    {
        /*
         * InviteToConferenceCallThread takes a specific Call but actually invites to the
         * telephony conference associated with the specified Call (if any). In order to not
         * change the signature of its constructor at this time, just pick up a Call
         * participating in the specified telephony conference (if any).
         */
        Call call = null;
        if (conference != null) {
            List<Call> calls = conference.getCalls();

            if (!calls.isEmpty())
                call = calls.get(0);
        }
        new InviteToConferenceCallThread(callees, call).start();
    }

    /**
     * Asynchronously creates a new conference <tt>Call</tt> with a specific list of
     * participants/callees.
     *
     * @param callees the list of participants/callees to invite to a newly-created conference <tt>Call</tt>
     */
    public static void createConferenceCall(Map<ProtocolProviderService, List<String>> callees)
    {
        new InviteToConferenceCallThread(callees, null).start();
    }

    /**
     * Asynchronously creates a new video bridge conference <tt>Call</tt> with a specific list of
     * participants/callees.
     *
     * @param callProvider the <tt>ProtocolProviderService</tt> to use for creating the call
     * @param callees the list of participants/callees to invite to the newly-created video bridge
     * conference <tt>Call</tt>
     */
    public static void createJitsiVideobridgeConfCall(ProtocolProviderService callProvider, String[] callees)
    {
        new InviteToConferenceBridgeThread(callProvider, callees, null).start();
    }

    /**
     * Invites the given list of <tt>callees</tt> to the given conference <tt>call</tt>.
     *
     * @param callees the list of contacts to invite
     * @param call the protocol provider to which this call belongs
     */
    public static void inviteToJitsiVideobridgeConfCall(String[] callees, Call call)
    {
        new InviteToConferenceBridgeThread(call.getProtocolProvider(), callees, call).start();
    }

    /**
     * Puts on or off hold the given <tt>callPeer</tt>.
     *
     * @param callPeer the peer to put on/off hold
     * @param isOnHold indicates the action (on hold or off hold)
     */
    public static void putOnHold(CallPeer callPeer, boolean isOnHold)
    {
        new PutOnHoldCallPeerThread(callPeer, isOnHold).start();
    }

    /**
     * Puts on or off hold the given <tt>call</tt>. (cmeng-android)
     *
     * @param call the peer to put on/off hold
     * @param isOnHold indicates the action (on hold or off hold)
     */
    public static void putOnHold(Call call, boolean isOnHold)
    {
        Iterator<? extends CallPeer> peers = call.getCallPeers();
        while (peers.hasNext()) {
            putOnHold(peers.next(), isOnHold);
        }
    }

    /**
     * Transfers the given <tt>peer</tt> to the given <tt>target</tt>.
     *
     * @param peer the <tt>CallPeer</tt> to transfer
     * @param target the <tt>CallPeer</tt> target to transfer to
     */
    public static void transferCall(CallPeer peer, CallPeer target)
    {
        OperationSetAdvancedTelephony<?> telephony
                = peer.getCall().getProtocolProvider().getOperationSet(OperationSetAdvancedTelephony.class);

        if (telephony != null) {
            try {
                telephony.transfer(peer, target);
            } catch (OperationFailedException ex) {
                Timber.e(ex, "Failed to transfer %s to %s", peer.getAddress(), target);
            }
        }
    }

    /**
     * Transfers the given <tt>peer</tt> to the given <tt>target</tt>.
     *
     * @param peer the <tt>CallPeer</tt> to transfer
     * @param target the target of the transfer
     */
    public static void transferCall(CallPeer peer, String target)
    {
        OperationSetAdvancedTelephony<?> telephony
                = peer.getCall().getProtocolProvider().getOperationSet(OperationSetAdvancedTelephony.class);

        if (telephony != null) {
            try {
                telephony.transfer(peer, target);
            } catch (OperationFailedException ex) {
                Timber.e(ex, "Failed to transfer %s to %s", peer.getAddress(), target);
            }
        }
    }

    /**
     * Returns a list of all currently registered telephony providers.
     *
     * @return a list of all currently registered telephony providers
     */
    public static List<ProtocolProviderService> getTelephonyProviders()
    {
        return AccountUtils.getRegisteredProviders(OperationSetBasicTelephony.class);
    }

    /**
     * Returns a list of all currently registered telephony providers supporting conferencing.
     *
     * @return a list of all currently registered telephony providers supporting conferencing
     */
    public static List<ProtocolProviderService> getTelephonyConferencingProviders()
    {
        return AccountUtils.getRegisteredProviders(OperationSetTelephonyConferencing.class);
    }

    /**
     * Returns a list of all currently active calls.
     *
     * @return a list of all currently active calls
     */
    // private static List<Call> getActiveCalls()
    // {
    // CallConference[] conferences;
    //
    // synchronized (callPanels)
    // {
    // Set<CallConference> keySet = callPanels.keySet();
    // conferences = keySet.toArray(new CallConference[keySet.size()]);
    // }
    //
    // List<Call> calls = new ArrayList<>();
    //
    // for (CallConference conference : conferences)
    // {
    // for (Call call : conference.getCalls())
    // {
    // if (call.getCallState() == CallState.CALL_IN_PROGRESS)
    // calls.add(call);
    // }
    // }
    // return calls;
    // }

    /**
     * Returns a collection of all currently in progress calls. A call is active if it is in
     * progress so the method merely delegates to
     *
     * @return a collection of all currently in progress calls.
     */
    public static Collection<Call> getInProgressCalls()
    {
        return getActiveCalls();
    }

    /**
     * Returns the image corresponding to the given <tt>peer</tt>.
     *
     * @param peer the call peer, for which we're returning an image
     * @return the peer image
     */
    public static byte[] getPeerImage(CallPeer peer)
    {
        byte[] image = null;
        BareJid peerJid = peer.getPeerJid().asBareJid();
        // We search for a contact corresponding to this call peer and try to get its image.
        if (peer.getPeerJid() != null) {
            image = AvatarManager.getAvatarImageByJid(peerJid);
        }
        return image;
    }

    /**
     * Indicates if we have video streams to show in this interface.
     *
     * @param call the call to check for video streaming
     * @return <tt>true</tt> if we have video streams to show in this interface; otherwise, <tt>false</tt>
     */
    public static boolean isVideoStreaming(Call call)
    {
        return isVideoStreaming(call.getConference());
    }

    /**
     * Indicates if we have video streams to show in this interface.
     *
     * @param conference the conference we check for video streaming
     * @return <tt>true</tt> if we have video streams to show in this interface; otherwise, <tt>false</tt>
     */
    public static boolean isVideoStreaming(CallConference conference)
    {
        for (Call call : conference.getCalls()) {
            OperationSetVideoTelephony videoTelephony
                    = call.getProtocolProvider().getOperationSet(OperationSetVideoTelephony.class);

            if (videoTelephony == null)
                continue;

            if (videoTelephony.isLocalVideoStreaming(call))
                return true;

            Iterator<? extends CallPeer> callPeers = call.getCallPeers();

            while (callPeers.hasNext()) {
                List<Component> remoteVideos = videoTelephony.getVisualComponents(callPeers.next());
                if ((remoteVideos != null) && (remoteVideos.size() > 0))
                    return true;
            }
        }
        return false;
    }

    /**
     * Indicates if the given call is currently muted.
     *
     * @param call the call to check
     * @return <tt>true</tt> if the given call is currently muted, <tt>false</tt> - otherwise
     */
    public static boolean isMute(Call call)
    {
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
     * @param isMute <tt>true</tt> to mute the call, <tt>false</tt> to unmute it
     */
    public static void setMute(Call call, boolean isMute)
    {
        Timber.d("Set mute to %s", isMute);
        new MuteThread(call, isMute).start();
    }

    /**
     * Creates the mute call thread.
     */
    private static class MuteThread extends Thread
    {
        private final Call call;
        private final boolean isMute;

        public MuteThread(Call call, boolean isMute)
        {
            this.call = call;
            this.isMute = isMute;
        }

        public void run()
        {
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
     * @param call the <tt>Call</tt> that will be checked.
     * @return <tt>true</tt> if given <tt>Call</tt> is locally on hold.
     */
    public static boolean isLocallyOnHold(Call call)
    {
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
     * @param device the <tt>MediaDevice</tt>, which audio formats we're looking for
     * @param protocolProvider the provider to check.
     * @return list of supported/enabled audio formats or empty list otherwise.
     */
    private static List<MediaFormat> getAudioFormats(MediaDevice device, ProtocolProviderService protocolProvider)
    {
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
     * Creates a new (audio-only or video) <tt>Call</tt> to a contact specified as a
     * <tt>Contact</tt> instance or a <tt>String</tt> contact address/identifier.
     */
    private static class CreateCallThread extends Thread
    {
        /**
         * The contact to call.
         */
        private final Contact contact;

        /**
         * The specific contact resource to call.
         */
        private final ContactResource contactResource;

        /**
         * The <tt>UIContactImpl</tt> we're calling.
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
         * The description of a conference to call, if any.
         */
        private final ConferenceDescription conferenceDescription;

        /**
         * The indicator which determines whether this instance is to create a new video (as
         * opposed to audio-only) <tt>Call</tt>.
         */
        private final boolean video;

        /**
         * The chat room associated with the call.
         */
        private final ChatRoom chatRoom;

        /**
         * Creates an instance of <tt>CreateCallThread</tt>.
         *
         * @param protocolProvider the protocol provider through which the call is going.
         * @param contact the contact to call
         * @param contactResource the specific <tt>ContactResource</tt> we're calling
         * @param video indicates if this is a video call
         */
        public CreateCallThread(ProtocolProviderService protocolProvider, Contact contact,
                ContactResource contactResource, boolean video)
        {
            this(protocolProvider, contact, contactResource, null, null, null, null, video);
        }

        /**
         * Creates an instance of <tt>CreateCallThread</tt>.
         *
         * @param protocolProvider the protocol provider through which the call is going.
         * @param contact the contact to call
         * @param video indicates if this is a video call
         */
        public CreateCallThread(ProtocolProviderService protocolProvider, String contact, boolean video)
        {
            this(protocolProvider, null, null, null, contact, null, null, video);
        }

        /**
         * Initializes a new <tt>CreateCallThread</tt> instance which is to create a new
         * <tt>Call</tt> to a conference specified via a <tt>ConferenceDescription</tt>.
         *
         * @param protocolProvider the <tt>ProtocolProviderService</tt> which is to perform the establishment of the
         * new <tt>Call</tt>.
         * @param conferenceDescription the description of the conference to call.
         * @param chatRoom the chat room associated with the call.
         */
        public CreateCallThread(ProtocolProviderService protocolProvider, ConferenceDescription conferenceDescription,
                ChatRoom chatRoom)
        {
            this(protocolProvider, null, null, null, null, conferenceDescription, chatRoom,
                    false /* audio */);
        }

        /**
         * Initializes a new <tt>CreateCallThread</tt> instance which is to create a new
         * <tt>Call</tt> to a contact specified either as a <tt>Contact</tt> instance or as a
         * <tt>String</tt> contact address/identifier.
         *
         * The constructor is private because it relies on its arguments being validated prior to its invocation.
         *
         * @param protocolProvider the ProtocolProviderService which is to perform the establishment of the new Call
         * @param contact the contact to call
         * @param contactResource the specific contact resource to call
         * @param uiContact the ui contact we're calling
         * @param stringContact the string callee to call
         * @param conferenceDescription the description of a conference to call
         * @param chatRoom the chat room associated with the call.
         * @param video <tt>true</tt> if this instance is to create a new video (as opposed to audio-only) <tt>Call</tt>
         */
        public CreateCallThread(ProtocolProviderService protocolProvider, Contact contact,
                ContactResource contactResource, UIContactImpl uiContact, String stringContact,
                ConferenceDescription conferenceDescription, ChatRoom chatRoom, boolean video)
        {
            this.protocolProvider = protocolProvider;
            this.contact = contact;
            this.contactResource = contactResource;
            this.uiContact = uiContact;
            this.stringContact = stringContact;
            this.video = video;
            this.conferenceDescription = conferenceDescription;
            this.chatRoom = chatRoom;
        }

        @Override
        public void run()
        {
            if (!video) {
                // if it is not video let's check for available audio codec and available audio devices
                MediaService mediaService = AndroidGUIActivator.getMediaService();
                MediaDevice dev = mediaService.getDefaultDevice(MediaType.AUDIO, MediaUseCase.CALL);

                List<MediaFormat> formats = getAudioFormats(dev, protocolProvider);
                String errMsg = null;

                if (!dev.getDirection().allowsSending())
                    errMsg = aTalkApp.getResString(R.string.service_gui_CALL_NO_AUDIO_DEVICE);
                else if (formats.isEmpty()) {
                    errMsg = aTalkApp.getResString(R.string.service_gui_CALL_NO_AUDIO_CODEC);
                }
                if (errMsg != null) {
                    AndroidUtils.showAlertDialog(aTalkApp.getGlobalContext(),
                            aTalkApp.getResString(R.string.service_gui_CALL), errMsg + ":  "
                                    + aTalkApp.getResString(R.string.service_gui_CALL_NO_DEVICE_CODEC_H, "")
                    );
                    return;
                }
            }

            Contact contact = this.contact;
            String stringContact = this.stringContact;
            if (ConfigurationUtils.isNormalizePhoneNumber()
                    && !NetworkUtils.isValidIPAddress(stringContact)) {
                if (contact != null) {
                    stringContact = contact.getAddress();
                    contact = null;
                }
                if (stringContact != null) {
                    stringContact = new PhoneNumberI18nServiceImpl().normalize(stringContact);
                }
            }

            try {
                if (conferenceDescription != null) {
                    internalCall(protocolProvider, conferenceDescription, chatRoom);
                }
                else {
                    if (video) {
                        internalCallVideo(protocolProvider, contact, uiContact, stringContact);
                    }
                    else {
                        internalCall(protocolProvider, contact, stringContact, contactResource, uiContact);
                    }
                }
            } catch (Throwable t) {
                if (t instanceof ThreadDeath)
                    throw (ThreadDeath) t;

                Timber.e(t, "The call could not be created: ");
                String message = aTalkApp.getResString(R.string.service_gui_CREATE_CALL_FAILED);

                if (t.getMessage() != null)
                    message += "\n" + t.getMessage();
                AndroidUtils.showAlertDialog(aTalkApp.getGlobalContext(),
                        aTalkApp.getResString(R.string.service_gui_ERROR), message);
            }
        }
    }

    /**
     * Creates a video call through the given <tt>protocolProvider</tt>.
     *
     * @param protocolProvider the <tt>ProtocolProviderService</tt> through which to make the call
     * @param contact the <tt>Contact</tt> to call
     * @param uiContact the <tt>UIContactImpl</tt> we're calling
     * @param stringContact the contact string to call
     * @throws OperationFailedException thrown if the call operation fails
     * @throws ParseException thrown if the contact string is malformatted
     */
    private static void internalCallVideo(ProtocolProviderService protocolProvider,
            Contact contact, UIContactImpl uiContact, String stringContact)
            throws OperationFailedException, ParseException
    {
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
     * Creates a call through the given <tt>protocolProvider</tt>.
     *
     * @param protocolProvider the <tt>ProtocolProviderService</tt> through which to make the call
     * @param contact the <tt>Contact</tt> to call
     * @param stringContact the contact string to call
     * @param contactResource the specific <tt>ContactResource</tt> to call
     * @param uiContact the <tt>UIContactImpl</tt> we're calling
     * @throws OperationFailedException thrown if the call operation fails
     * @throws ParseException thrown if the contact string is malformatted
     */
    private static void internalCall(ProtocolProviderService protocolProvider, Contact contact,
            String stringContact, ContactResource contactResource, UIContactImpl uiContact)
            throws OperationFailedException, ParseException
    {
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
     * Creates a call through the given <tt>protocolProvider</tt>.
     *
     * @param protocolProvider the <tt>ProtocolProviderService</tt> through which to make the call
     * @param conferenceDescription the description of the conference to call
     * @param chatRoom the chat room associated with the call.
     */
    private static void internalCall(ProtocolProviderService protocolProvider,
            ConferenceDescription conferenceDescription, ChatRoom chatRoom)
            throws OperationFailedException
    {
        OperationSetBasicTelephony<?> telephony = protocolProvider.getOperationSet(OperationSetBasicTelephony.class);
        if (telephony != null) {
            telephony.createCall(conferenceDescription, chatRoom);
        }
    }

    /**
     * Returns the <tt>MetaContact</tt>, to which the given <tt>Call</tt> was initially created.
     *
     * @param call the <tt>Call</tt>, which corresponding <tt>MetaContact</tt> we're looking for
     * @return the <tt>UIContactImpl</tt>, to which the given <tt>Call</tt> was initially created
     */
    public static UIContactImpl getCallUIContact(Call call)
    {
        if (uiContactCalls != null)
            return uiContactCalls.get(call);
        return null;
    }

    /**
     * Adds a call for a <tt>metaContact</tt>.
     *
     * @param uiContact the <tt>UIContact</tt> corresponding to the call
     * @param call the <tt>Call</tt> corresponding to the <tt>MetaContact</tt>
     */
    private static void addUIContactCall(UIContactImpl uiContact, Call call)
    {
        if (uiContactCalls == null)
            uiContactCalls = new WeakHashMap<Call, UIContactImpl>();

        uiContactCalls.put(call, uiContact);
    }

    /**
     * Creates a desktop sharing session with the given Contact or a given String.
     */
    private static class CreateDesktopSharingThread extends Thread
    {
        /**
         * The string contact to share the desktop with.
         */
        private final String stringContact;

        /**
         * The protocol provider through which we share our desktop.
         */
        private final ProtocolProviderService protocolProvider;

        /**
         * The media device corresponding to the screen we would like to share.
         */
        private final MediaDevice mediaDevice;

        /**
         * The <tt>UIContactImpl</tt> we're calling.
         */
        private final UIContactImpl uiContact;

        /**
         * Whether user has selected sharing full screen or region.
         */
        private boolean fullscreen;

        /**
         * Creates a desktop sharing session thread.
         *
         * @param protocolProvider protocol provider through which we share our desktop
         * @param contact the contact to share the desktop with
         * @param uiContact the <tt>UIContact</tt>, which initiated the desktop sharing session
         * @param mediaDevice the media device corresponding to the screen we would like to share
         */
        public CreateDesktopSharingThread(ProtocolProviderService protocolProvider, String contact,
                UIContactImpl uiContact, MediaDevice mediaDevice, boolean fullscreen)
        {
            this.protocolProvider = protocolProvider;
            this.stringContact = contact;
            this.uiContact = uiContact;
            this.mediaDevice = mediaDevice;
            this.fullscreen = fullscreen;
        }

        @Override
        public void run()
        {
            OperationSetDesktopStreaming desktopSharingOpSet
                    = protocolProvider.getOperationSet(OperationSetDesktopStreaming.class);

            /*
             * XXX If we are here and we just discover that OperationSetDesktopStreaming is not supported, then we're
             * already in trouble - we've already started a whole new thread just to check that a reference is null.
             */
            if (desktopSharingOpSet == null)
                return;

            Throwable exception = null;

            Call createdCall = null;
            try {
                if (mediaDevice != null) {
                    createdCall = desktopSharingOpSet.createVideoCall(stringContact, mediaDevice);
                }
                else
                    createdCall = desktopSharingOpSet.createVideoCall(stringContact);
            } catch (OperationFailedException | ParseException e) {
                exception = e;
            }
            if (exception != null) {
                Timber.e("The call could not be created: %s", exception.getMessage());
                AndroidUtils.showAlertDialog(aTalkApp.getGlobalContext(),
                        aTalkApp.getResString(R.string.service_gui_ERROR), exception.getMessage());
            }

            if (uiContact != null && createdCall != null)
                addUIContactCall(uiContact, createdCall);

            if (createdCall != null && fullscreen) {
                // new FullScreenShareIndicator(createdCall);
            }
        }
    }

    /**
     * Answers to all <tt>CallPeer</tt>s associated with a specific <tt>Call</tt> and, optionally,
     * does that in a telephony conference with an existing <tt>Call</tt>.
     */
    private static class AnswerCallThread extends Thread
    {
        /**
         * The <tt>Call</tt> which is to be answered.
         */
        private final Call call;

        /**
         * The existing <tt>Call</tt>, if any, which represents a telephony conference in which
         * {@link #call} is to be answered.
         */
        private final Call existingCall;

        /**
         * The indicator which determines whether this instance is to answer {@link #call} with
         * video.
         */
        private final boolean isVideoCall;

        public AnswerCallThread(Call call, Call existingCall, boolean isVideoCall)
        {
            this.call = call;
            this.existingCall = existingCall;
            this.isVideoCall = isVideoCall;
        }

        @Override
        public void run()
        {
            if (existingCall != null)
                call.setConference(existingCall.getConference());

            ProtocolProviderService pps = call.getProtocolProvider();
            Iterator<? extends CallPeer> peers = call.getCallPeers();

            while (peers.hasNext()) {
                CallPeer peer = peers.next();

                if (isVideoCall) {
                    OperationSetVideoTelephony telephony = pps.getOperationSet(OperationSetVideoTelephony.class);

                    try {
                        telephony.answerVideoCallPeer(peer);
                    } catch (OperationFailedException ofe) {
                        Timber.e("Could not answer %s with video because of the following exception: %s",
                                peer, ofe.getMessage());
                    }
                }
                else {
                    OperationSetBasicTelephony<?> telephony = pps.getOperationSet(OperationSetBasicTelephony.class);
                    try {
                        telephony.answerCallPeer(peer);
                    } catch (OperationFailedException ofe) {
                        Timber.e("Could not answer %s because of the following exception: %s", peer, ofe.getMessage());
                    }
                }
            }
        }
    }

    /**
     * Invites a list of callees to a conference <tt>Call</tt>. If the specified <tt>Call</tt> is
     * <tt>null</tt>, creates a brand new telephony conference.
     */
    private static class InviteToConferenceCallThread extends Thread
    {
        /**
         * The addresses of the callees to be invited into the telephony conference to be
         * organized by this instance. For further details, refer to the documentation on the
         * <tt>callees</tt> parameter of the respective <tt>InviteToConferenceCallThread</tt>
         * constructor.
         */
        private final Map<ProtocolProviderService, List<String>> callees;

        /**
         * The <tt>Call</tt>, if any, into the telephony conference of which {@link #callees} are
         * to be invited. If non-<tt>null</tt>, its <tt>CallConference</tt> state will be shared
         * with all <tt>Call</tt>s established by this instance for the purposes of having the
         * <tt>callees</tt> into the same telephony conference.
         */
        private final Call call;

        /**
         * Initializes a new <tt>InviteToConferenceCallThread</tt> instance which is to invite a
         * list of callees to a conference <tt>Call</tt>. If the specified <tt>call</tt> is
         * <tt>null</tt>, creates a brand new telephony conference.
         *
         * @param callees the addresses of the callees to be invited into a telephony conference. The
         * addresses are provided in multiple <tt>List&lt;String&gt;</tt>s. Each such list
         * of addresses is mapped by the <tt>ProtocolProviderService</tt> through which they
         * are to be invited into the telephony conference. If there are multiple
         * <tt>ProtocolProviderService</tt>s in the specified <tt>Map</tt>, the resulting
         * telephony conference is known by the name &quot;cross-protocol&quot;. It is also
         * allowed to have a list of addresses mapped to <tt>null</tt> which means that the
         * new instance will automatically choose
         * a <tt>ProtocolProviderService</tt> to invite the respective callees into the
         * telephony conference.
         * @param call the <tt>Call</tt> to invite the specified <tt>callees</tt> into. If <tt>null</tt>,
         * this instance will create a brand new telephony conference. Technically, a
         * <tt>Call</tt> instance is protocol/account-specific and it is possible to have
         * cross-protocol/account telephony conferences. That's why the specified
         * <tt>callees</tt> are invited into one and the same <tt>CallConference</tt>:
         * the one in which the specified <tt>call</tt> is participating or a new one if
         * <tt>call</tt> is <tt>null</tt>. Of course, an attempt is made to have all callees
         * from one and the same protocol/account into one <tt>Call</tt> instance.
         */
        public InviteToConferenceCallThread(Map<ProtocolProviderService, List<String>> callees, Call call)
        {
            this.callees = callees;
            this.call = call;
        }

        /**
         * Invites {@link #callees} into a telephony conference which is optionally specified by
         * {@link #call}.
         */
        @Override
        public void run()
        {
            CallConference conference = (call == null) ? null : call.getConference();
            for (Map.Entry<ProtocolProviderService, List<String>> entry : callees.entrySet()) {
                ProtocolProviderService pps = entry.getKey();

                /*
                 * We'd like to allow specifying callees without specifying an associated
                 * ProtocolProviderService.
                 */
                if (pps != null) {
                    OperationSetBasicTelephony<?> basicTelephony = pps.getOperationSet(OperationSetBasicTelephony.class);

                    if (basicTelephony == null)
                        continue;
                }

                List<String> contactList = entry.getValue();
                String[] contactArray = contactList.toArray(new String[contactList.size()]);

                if (ConfigurationUtils.isNormalizePhoneNumber())
                    normalizePhoneNumbers(contactArray);

                /* Try to have a single Call per ProtocolProviderService. */
                Call ppsCall;

                if ((call != null) && call.getProtocolProvider().equals(pps))
                    ppsCall = call;
                else {
                    ppsCall = null;
                    if (conference != null) {
                        List<Call> conferenceCalls = conference.getCalls();

                        if (pps == null) {
                            /*
                             * We'd like to allow specifying callees without specifying an
                             * associated ProtocolProviderService. The simplest approach is to
                             * just choose the first ProtocolProviderService involved in the
                             * telephony conference.
                             */
                            if (call == null) {
                                if (!conferenceCalls.isEmpty()) {
                                    ppsCall = conferenceCalls.get(0);
                                    pps = ppsCall.getProtocolProvider();
                                }
                            }
                            else {
                                ppsCall = call;
                                pps = ppsCall.getProtocolProvider();
                            }
                        }
                        else {
                            for (Call conferenceCall : conferenceCalls) {
                                if (pps.equals(conferenceCall.getProtocolProvider())) {
                                    ppsCall = conferenceCall;
                                    break;
                                }
                            }
                        }
                    }
                }

                OperationSetTelephonyConferencing telephonyConferencing
                        = pps.getOperationSet(OperationSetTelephonyConferencing.class);

                try {
                    if (ppsCall == null) {
                        ppsCall = telephonyConferencing.createConfCall(contactArray, conference);
                        if (conference == null)
                            conference = ppsCall.getConference();
                    }
                    else {
                        for (String contact : contactArray) {
                            telephonyConferencing.inviteCalleeToCall(contact, ppsCall);
                        }
                    }
                } catch (Exception e) {
                    Timber.e(e, "Failed to invite callees: %s", Arrays.toString(contactArray));
                    AndroidUtils.showAlertDialog(aTalkApp.getGlobalContext(),
                            aTalkApp.getResString(R.string.service_gui_ERROR), e.getMessage());
                }
            }
        }
    }

    /**
     * Invites a list of callees to a specific conference <tt>Call</tt>. If the specified
     * <tt>Call</tt> is <tt>null</tt>, creates a brand new telephony conference.
     */
    private static class InviteToConferenceBridgeThread extends Thread
    {
        private final ProtocolProviderService callProvider;
        private final String[] callees;
        private final Call call;

        public InviteToConferenceBridgeThread(ProtocolProviderService callProvider, String[] callees, Call call)
        {
            this.callProvider = callProvider;
            this.callees = callees;
            this.call = call;
        }

        @Override
        public void run()
        {
            OperationSetVideoBridge opSetVideoBridge = callProvider.getOperationSet(OperationSetVideoBridge.class);

            // Normally if this method is called then this should not happen
            // but we check in order to be sure to be able to proceed.
            if (opSetVideoBridge == null || !opSetVideoBridge.isActive())
                return;

            if (ConfigurationUtils.isNormalizePhoneNumber())
                normalizePhoneNumbers(callees);

            try {
                if (call == null) {
                    opSetVideoBridge.createConfCall(callees);
                }
                else {
                    for (String contact : callees)
                        opSetVideoBridge.inviteCalleeToCall(contact, call);
                }
            } catch (Exception e) {
                Timber.e(e, "Failed to invite callees: %s", Arrays.toString(callees));
                AndroidUtils.showAlertDialog(aTalkApp.getGlobalContext(),
                        aTalkApp.getResString(R.string.service_gui_ERROR), e.getMessage());
            }
        }
    }

    /**
     * Hangs up a specific <tt>Call</tt> (i.e. all <tt>CallPeer</tt>s associated with a
     * <tt>Call</tt>), <tt>CallConference</tt> (i.e. all <tt>Call</tt>s participating in a
     * <tt>CallConference</tt>), or <tt>CallPeer</tt>.
     */
    private static class HangupCallThread extends Thread
    {
        private final Call call;
        private final CallConference conference;
        private final CallPeer peer;

        /**
         * Initializes a new <tt>HangupCallThread</tt> instance which is to hang up a specific
         * <tt>Call</tt> i.e. all <tt>CallPeer</tt>s associated with the <tt>Call</tt>.
         *
         * @param call the <tt>Call</tt> whose associated <tt>CallPeer</tt>s are to be hanged up
         */
        public HangupCallThread(Call call)
        {
            // this.call = call;
            this(call, null, null);
        }

        /**
         * Initializes a new <tt>HangupCallThread</tt> instance which is to hang up a specific
         * <tt>CallConference</tt> i.e. all <tt>Call</tt>s participating in the
         * <tt>CallConference</tt>.
         *
         * @param conference the <tt>CallConference</tt> whose participating <tt>Call</tt>s re to be hanged up
         */
        public HangupCallThread(CallConference conference)
        {
            this(null, conference, null);
        }

        /**
         * Initializes a new <tt>HangupCallThread</tt> instance which is to hang up a specific
         * <tt>CallPeer</tt>.
         *
         * @param peer the <tt>CallPeer</tt> to hang up
         */
        public HangupCallThread(CallPeer peer)
        {
            this(null, null, peer);
        }

        /**
         * Initializes a new <tt>HangupCallThread</tt> instance which is to hang up a specific
         * <tt>Call</tt>, <tt>CallConference</tt>, or <tt>CallPeer</tt>.
         *
         * @param call the <tt>Call</tt> whose associated <tt>CallPeer</tt>s are to be hanged up
         * @param conference the <tt>CallConference</tt> whose participating <tt>Call</tt>s re to be hanged up
         * @param peer the <tt>CallPeer</tt> to hang up
         */
        private HangupCallThread(Call call, CallConference conference, CallPeer peer)
        {
            this.call = call;
            this.conference = conference;
            this.peer = peer;
        }

        @Override
        public void run()
        {
            /*
             * There is only an OperationSet which hangs up a CallPeer at a time so prepare a list
             * of all CallPeers to be hanged up.
             */
            Set<CallPeer> peers = new HashSet<>();

            if (call != null) {
                Iterator<? extends CallPeer> peerIter = call.getCallPeers();

                // CallPeer peer = peerIter.next();
                while (peerIter.hasNext()) {
                    peers.add(peerIter.next());
                }
            }

            if (conference != null)
                peers.addAll(conference.getCallPeers());

            if (peer != null)
                peers.add(peer);

            for (CallPeer peer : peers) {
                OperationSetBasicTelephony<?> basicTelephony
                        = peer.getProtocolProvider().getOperationSet(OperationSetBasicTelephony.class);

                try {
                    basicTelephony.hangupCallPeer(peer);
                } catch (OperationFailedException ofe) {
                    Timber.e(ofe, "Could not hang up: %s", peer);
                }
            }
            removeActiveCall(call);
        }
    }

    /**
     * Creates the enable local video call thread.
     */
    private static class EnableLocalVideoThread extends Thread
    {
        private final Call call;
        private final boolean enable;

        /**
         * Creates the enable local video call thread.
         *
         * @param call the call, for which to enable/disable
         * @param enable allow to have LocalVideo streaming if true
         */
        public EnableLocalVideoThread(Call call, boolean enable)
        {
            this.call = call;
            this.enable = enable;
        }

        @Override
        public void run()
        {
            OperationSetVideoTelephony videoTelephony
                    = call.getProtocolProvider().getOperationSet(OperationSetVideoTelephony.class);
            if (videoTelephony != null) {
                try {
                    videoTelephony.setLocalVideoAllowed(call, enable);
                } catch (OperationFailedException ex) {
                    Timber.e("Failed to toggle the streaming of local video. %s", ex.getMessage());
                }
            }
        }
    }

    /**
     * Puts on hold the given <tt>CallPeer</tt>.
     */
    private static class PutOnHoldCallPeerThread extends Thread
    {
        private final CallPeer callPeer;
        private final boolean isOnHold;

        public PutOnHoldCallPeerThread(CallPeer callPeer, boolean isOnHold)
        {
            this.callPeer = callPeer;
            this.isOnHold = isOnHold;
        }

        @Override
        public void run()
        {
            OperationSetBasicTelephony<?> telephony
                    = callPeer.getProtocolProvider().getOperationSet(OperationSetBasicTelephony.class);
            try {
                if (isOnHold)
                    telephony.putOnHold(callPeer);
                else
                    telephony.putOffHold(callPeer);
            } catch (OperationFailedException ex) {
                Timber.e(ex, "Failed to put %s %s", callPeer.getAddress(), (isOnHold ? " on hold." : " off hold. "));
            }
        }
    }

    /**
     * Merges specific existing <tt>Call</tt>s into a specific telephony conference.
     */
    private static class MergeExistingCalls extends Thread
    {
        /**
         * The telephony conference in which {@link #calls} are to be merged.
         */
        private final CallConference conference;

        /**
         * Second call.
         */
        private final Collection<Call> calls;

        /**
         * Initializes a new <tt>MergeExistingCalls</tt> instance which is to merge specific
         * existing <tt>Call</tt>s into a specific telephony conference.
         *
         * @param conference the telephony conference in which the specified <tt>Call</tt>s are to be merged
         * @param calls the <tt>Call</tt>s to be merged into the specified telephony conference
         */
        public MergeExistingCalls(CallConference conference, Collection<Call> calls)
        {
            this.conference = conference;
            this.calls = calls;
        }

        /**
         * Puts off hold the <tt>CallPeer</tt>s of a specific <tt>Call</tt> which are locally on
         * hold.
         *
         * @param call the <tt>Call</tt> which is to have its <tt>CallPeer</tt>s put off hold
         */
        private void putOffHold(Call call)
        {
            Iterator<? extends CallPeer> peers = call.getCallPeers();
            OperationSetBasicTelephony<?> telephony
                    = call.getProtocolProvider().getOperationSet(OperationSetBasicTelephony.class);

            while (peers.hasNext()) {
                CallPeer callPeer = peers.next();
                boolean putOffHold = true;

                if (callPeer instanceof MediaAwareCallPeer) {
                    putOffHold = ((MediaAwareCallPeer<?, ?, ?>) callPeer).getMediaHandler().isLocallyOnHold();
                }
                if (putOffHold) {
                    try {
                        telephony.putOffHold(callPeer);
                        Thread.sleep(400);
                    } catch (Exception ofe) {
                        Timber.e("Failed to put off hold. %s", ofe.getMessage());
                    }
                }
            }
        }

        @Override
        public void run()
        {
            // conference
            for (Call call : conference.getCalls())
                putOffHold(call);

            // calls
            if (!calls.isEmpty()) {
                for (Call call : calls) {
                    if (conference.containsCall(call))
                        continue;

                    putOffHold(call);

                    /*
                     * Dispose of the CallPanel associated with the Call which is to be merged.
                     */
                    // cmeng - closeCallContainerIfNotNecessary(conference, false);
                    call.setConference(conference);
                }
            }
        }
    }

    /**
     * Normalizes the phone numbers (if any) in a list of <tt>String</tt> contact addresses or
     * phone numbers.
     *
     * @param callees the list of contact addresses or phone numbers to be normalized
     */
    private static void normalizePhoneNumbers(String callees[])
    {
        for (int i = 0; i < callees.length; i++)
            callees[i] = AndroidGUIActivator.getPhoneNumberI18nService().normalize(callees[i]);
    }
}
