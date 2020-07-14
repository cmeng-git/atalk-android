/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.atalk.android.gui.call;

import android.content.res.Resources;
import android.os.Bundle;
import android.view.*;

import net.java.sip.communicator.service.protocol.*;
import net.java.sip.communicator.service.protocol.media.CallPeerMediaHandler;
import net.java.sip.communicator.service.protocol.media.MediaAwareCallPeer;
import net.java.sip.communicator.util.GuiUtils;

import org.atalk.android.R;
import org.atalk.android.gui.util.ViewUtil;
import org.atalk.android.util.java.awt.Dimension;
import org.atalk.service.neomedia.*;
import org.atalk.service.osgi.OSGiDialogFragment;
import org.atalk.util.MediaType;

import java.net.InetSocketAddress;
import java.util.*;

// Disambiguation

/**
 * Dialog fragment displaying technical call information. To create dialog instance factory method
 * {@link #newInstance(String)} should be used. As an argument it takes the call key that identifies a call in
 * {@link CallManager}.
 *
 * @author Pawel Domas
 * @author Eng Chong Meng
 */
public class CallInfoDialogFragment extends OSGiDialogFragment
{
    /**
     * The extra key pointing to the "call key" that will be used to retrieve active call from {@link CallManager}.
     */
    private static final String CALL_KEY_EXTRA = "CALL_KEY";

    /**
     * Unicode constant for up arrow.
     */
    private static final String UP_ARROW = "\u2191";

    /**
     * Unicode constant for down arrow.
     */
    private static final String DOWN_ARROW = "\u2193";

    /**
     * The call handled by this dialog.
     */
    private Call call;

    /**
     * Reference to the thread that calculates media statistics and updates the view.
     */
    private InfoUpdateThread pollingThread;

    /**
     * Dialog view container for call info display
     */
    private View viewContainer;

    /**
     * Factory method that creates new dialog fragment and injects the <tt>callKey</tt> into the dialog arguments
     * bundle.
     *
     * @param callKey the key string that identifies active call in {@link CallManager}.
     * @return new, parametrized instance of {@link CallInfoDialogFragment}.
     */
    public static CallInfoDialogFragment newInstance(String callKey)
    {
        CallInfoDialogFragment f = new CallInfoDialogFragment();

        Bundle args = new Bundle();
        args.putString(CALL_KEY_EXTRA, callKey);
        f.setArguments(args);
        return f;
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState)
    {
        // Retrieves the call from manager.
        String callKey = getArguments().getString(CALL_KEY_EXTRA);
        this.call = CallManager.getActiveCall(callKey);
        // Inflates the view.
        viewContainer = inflater.inflate(R.layout.call_info, container, true);

        View cancelBtn = viewContainer.findViewById(R.id.call_info_ok);
        cancelBtn.setOnClickListener(view -> dismiss());

        // Sets the title.
        if (getDialog() != null)
            getDialog().setTitle(R.string.service_gui_callinfo_TECHNICAL_CALL_INFO);
        return viewContainer;
    }

    /**
     * Triggers the view update on UI thread.
     */
    private void updateView()
    {
        runOnUiThread(() -> {
            if (getView() != null)
                doUpdateView();
        });
    }

    /**
     * Sets given <tt>text</tt> on the <tt>TextView</tt> identified by the <tt>id</tt>. The <tt>TextView</tt> must be
     * inside the view hierarchy.
     *
     * @param id the id of <tt>TextView</tt> we want to edit.
     * @param text string value that will be set on the <tt>TextView</tt>.
     */
    private void setTextViewValue(int id, String text)
    {
        ViewUtil.setTextViewValue(viewContainer, id, text);
    }

    /**
     * Sets given <tt>text</tt> on the <tt>TextView</tt> identified by the <tt>id</tt>. The <tt>TextView</tt> must be
     * inside <tt>container</tt> view hierarchy.
     *
     * @param container the <tt>View</tt> that contains the <tt>TextView</tt>.
     * @param id the id of <tt>TextView</tt> we want to edit.
     * @param text string value that will be set on the <tt>TextView</tt>.
     */
    private void setTextViewValue(View container, int id, String text)
    {
        ViewUtil.setTextViewValue(container, id, text);
    }

    /**
     * Ensures that the <tt>View</tt> is currently in visible or hidden state which depends on <tt>isVisible</tt> flag.
     *
     * @param container parent <tt>View</tt> that contains displayed <tt>View</tt>.
     * @param viewId the id of <tt>View</tt> that will be shown/hidden.
     * @param isVisible flag telling whether the <tt>View</tt> has to be shown or hidden.
     */
    private void ensureVisible(View container, int viewId, boolean isVisible)
    {
        ViewUtil.ensureVisible(container, viewId, isVisible);
    }

    /**
     * Updates the view to display actual call information.
     */
    private void doUpdateView()
    {
        CallConference conference = call.getConference();
        List<Call> calls = conference.getCalls();
        if (calls.size() == 0)
            return;

        Call aCall = calls.get(0);
        // Identity.
        setTextViewValue(R.id.identity, aCall.getProtocolProvider().getAccountID().getDisplayName());
        // Peer count.
        setTextViewValue(R.id.peerCount, String.valueOf(conference.getCallPeerCount()));
        // Conference focus.
        setTextViewValue(R.id.conferenceFocus, String.valueOf(conference.isConferenceFocus()));
        // Preferred transport.
        TransportProtocol preferredTransport = aCall.getProtocolProvider().getTransportProtocol();
        setTextViewValue(R.id.transport, preferredTransport.toString());

        List<CallPeer> callPeers = conference.getCallPeers();
        if (callPeers.size() == 0)
            return;
        constructPeerInfo(callPeers.get(0));
    }

    /**
     * Constructs peer info.
     *
     * @param callPeer the <tt>CallPeer</tt>, for which we'll construct the info.
     */
    private void constructPeerInfo(CallPeer callPeer)
    {
        // Peer name.
        setTextViewValue(R.id.callPeer, callPeer.getAddress());

        // Call duration.
        Date startTime = new Date(callPeer.getCallDurationStartTime());
        String durationStr = GuiUtils.formatTime(startTime.getTime(), System.currentTimeMillis());
        setTextViewValue(R.id.callDuration, durationStr);

        CallPeerMediaHandler<?> callPeerMediaHandler;
        if (callPeer instanceof MediaAwareCallPeer) {
            callPeerMediaHandler = ((MediaAwareCallPeer<?, ?, ?>) callPeer).getMediaHandler();
            // Audio stream info.
            updateAudioVideoInfo(callPeerMediaHandler, MediaType.AUDIO);
            // Video stream info.
            updateAudioVideoInfo(callPeerMediaHandler, MediaType.VIDEO);
            // ICE info.
            updateIceSection(callPeerMediaHandler);
        }
    }

    /**
     * Updates section displaying ICE information for given <tt>callPeerMediaHandler</tt>.
     *
     * @param callPeerMediaHandler the call peer for which ICE information will be displayed.
     */
    private void updateIceSection(CallPeerMediaHandler<?> callPeerMediaHandler)
    {
        // ICE state.
        String iceState = null;
        if (callPeerMediaHandler != null) {
            iceState = callPeerMediaHandler.getICEState();
        }

        boolean iceStateVisible = iceState != null && !iceState.equals("Terminated");
        ensureVisible(viewContainer, R.id.iceState, iceStateVisible);
        ensureVisible(viewContainer, R.id.iceStateLabel, iceStateVisible);

        if (iceStateVisible) {
            Resources resources = getResources();
            int strId = resources.getIdentifier("service_gui_callinfo_ICE_STATE_" + iceState.toUpperCase(Locale.US),
                    "string", getActivity().getPackageName());
            setTextViewValue(R.id.iceState, resources.getString(strId));
        }

        // Total harvesting time.
        long harvestingTime = 0;
        if (callPeerMediaHandler != null) {
            harvestingTime = callPeerMediaHandler.getTotalHarvestingTime();
        }
        boolean isTotalHarvestTime = harvestingTime != 0;
        ensureVisible(viewContainer, R.id.totalHarvestTime, isTotalHarvestTime);
        ensureVisible(viewContainer, R.id.totalHarvestLabel, isTotalHarvestTime);

        if (isTotalHarvestTime) {
            int harvestCount = callPeerMediaHandler.getNbHarvesting();
            setTextViewValue(viewContainer, R.id.totalHarvestTime,
                    getString(R.string.service_gui_callinfo_HARVESTING_DATA, harvestingTime, harvestCount));
        }

        // Current harvester time if ICE agent is harvesting.
        String[] harvesterNames = {
                "GoogleTurnCandidateHarvester",
                "GoogleTurnSSLCandidateHarvester",
                "HostCandidateHarvester",
                "JingleNodesHarvester",
                "StunCandidateHarvester",
                "TurnCandidateHarvester",
                "UPNPHarvester"
        };
        int[] harvesterLabels = {
                R.id.googleTurnLabel,
                R.id.googleTurnSSlLabel,
                R.id.hostHarvesterLabel,
                R.id.jingleNodesLabel,
                R.id.stunHarvesterLabel,
                R.id.turnHarvesterLabel,
                R.id.upnpHarvesterLabel
        };
        int[] harvesterValues = {
                R.id.googleTurnTime,
                R.id.googleTurnSSlTime,
                R.id.hostHarvesterTime,
                R.id.jingleNodesTime,
                R.id.stunHarvesterTime,
                R.id.turnHarvesterTime,
                R.id.upnpHarvesterTime};
        for (int i = 0; i < harvesterLabels.length; ++i) {
            harvestingTime = 0;

            if (callPeerMediaHandler != null) {
                harvestingTime = callPeerMediaHandler.getHarvestingTime(harvesterNames[i]);
            }

            boolean visible = harvestingTime != 0;
            ensureVisible(viewContainer, harvesterLabels[i], visible);
            ensureVisible(viewContainer, harvesterValues[i], visible);
            if (visible) {
                setTextViewValue(viewContainer, harvesterValues[i],
                        getString(R.string.service_gui_callinfo_HARVESTING_DATA, harvestingTime,
                                callPeerMediaHandler.getNbHarvesting()));
            }
        }
    }

    /**
     * Creates the string for the stream encryption method (null, MIKEY, SDES, ZRTP) used for a given media stream (type
     * AUDIO or VIDEO).
     *
     * @param callPeerMediaHandler The media handler containing the different media streams.
     * @param mediaStream the <tt>MediaStream</tt> that gives us access to audio/video info.
     * @param mediaType The media type used to determine which stream of the media handler must returns it encryption method.
     */
    private String getStreamEncryptionMethod(CallPeerMediaHandler<?> callPeerMediaHandler, MediaStream mediaStream, MediaType mediaType)
    {
        Resources resources = getResources();

        String transportProtocolString = "";
        StreamConnector.Protocol transportProtocol = mediaStream.getTransportProtocol();
        if (transportProtocol != null) {
            transportProtocolString = transportProtocol.toString();
        }

        String rtpType;
        SrtpControl srtpControl = callPeerMediaHandler.getEncryptionMethod(mediaType);
        // If the stream is secured.
        if (srtpControl != null) {
            String info;
            if (srtpControl instanceof ZrtpControl) {
                info = "ZRTP " + ((ZrtpControl) srtpControl).getCipherString();
            }
            else {
                info = "SDES";
            }
            rtpType = resources.getString(R.string.service_gui_callinfo_MEDIA_STREAM_SRTP) + " ("
                    + resources.getString(R.string.service_gui_callinfo_KEY_EXCHANGE_PROTOCOL) + ": " + info + ")";
        }
        // If the stream is not secured.
        else {
            rtpType = resources.getString(R.string.service_gui_callinfo_MEDIA_STREAM_RTP);
        }
        return transportProtocolString + " / " + rtpType;
    }

    /**
     * Updates audio video peer info.
     *
     * @param callPeerMediaHandler The <tt>CallPeerMadiaHandler</tt> containing the AUDIO/VIDEO stream.
     * @param mediaType The media type used to determine which stream of the media handler will be used.
     */
    private void updateAudioVideoInfo(CallPeerMediaHandler<?> callPeerMediaHandler, MediaType mediaType)
    {
        View container = mediaType == MediaType.AUDIO ? viewContainer.findViewById(R.id.audioInfo) : viewContainer.findViewById(R.id.videoInfo);

        MediaStream mediaStream = callPeerMediaHandler.getStream(mediaType);
        MediaStreamStats mediaStreamStats = null;
        if (mediaStream != null) {
            mediaStreamStats = mediaStream.getMediaStreamStats();
        }

        // Hides the whole section if stats are not available.
        ensureVisible(viewContainer, container.getId(), mediaStreamStats != null);
        if (mediaStreamStats == null) {
            return;
        }

        // Sets the encryption status String.
        setTextViewValue(container, R.id.mediaTransport, getStreamEncryptionMethod(callPeerMediaHandler, mediaStream, mediaType));
        // Set the title label to Video info if it's a video stream.
        if (mediaType == MediaType.VIDEO) {
            setTextViewValue(container, R.id.audioVideoLabel, getString(R.string.service_gui_callinfo_VIDEO_INFO));
        }

        boolean hasVideoSize = false;
        if (mediaType == MediaType.VIDEO) {
            Dimension downloadVideoSize = mediaStreamStats.getDownloadVideoSize();
            Dimension uploadVideoSize = mediaStreamStats.getUploadVideoSize();
            // Checks that at least one video stream is active.
            if (downloadVideoSize != null || uploadVideoSize != null) {
                hasVideoSize = true;
                setTextViewValue(container, R.id.videoSize,
                        DOWN_ARROW + " " + this.videoSizeToString(downloadVideoSize) + " "
                                + UP_ARROW + " " + this.videoSizeToString(uploadVideoSize));
            }
        }

        // Shows video size if it's available(always false for AUDIO)
        ensureVisible(container, R.id.videoSize, hasVideoSize);
        ensureVisible(container, R.id.videoSizeLabel, hasVideoSize);

        // Codec.
        setTextViewValue(container, R.id.codec, mediaStreamStats.getEncoding() + " / " + mediaStreamStats.getEncodingClockRate() + " Hz");
        boolean displayedIpPort = false;

        // ICE candidate type.
        String iceCandidateExtendedType = callPeerMediaHandler.getICECandidateExtendedType(mediaType.toString());

        boolean iceCandidateExtVisible = iceCandidateExtendedType != null;
        ensureVisible(container, R.id.iceExtType, iceCandidateExtVisible);
        ensureVisible(container, R.id.iceExtTypeLabel, iceCandidateExtVisible);

        if (iceCandidateExtVisible) {
            setTextViewValue(container, R.id.iceExtType, iceCandidateExtendedType);
            displayedIpPort = true;
        }

        // Local host address.
        InetSocketAddress iceLocalHostAddress = callPeerMediaHandler.getICELocalHostAddress(mediaType.toString());
        boolean iceLocalHostVisible = iceLocalHostAddress != null;
        ensureVisible(container, R.id.iceLocalHost, iceLocalHostVisible);
        ensureVisible(container, R.id.localHostLabel, iceLocalHostVisible);

        if (iceLocalHostVisible) {
            setTextViewValue(container, R.id.iceLocalHost, iceLocalHostAddress.getAddress().getHostAddress()
                    + "/" + iceLocalHostAddress.getPort());
            displayedIpPort = true;
        }

        // Local reflexive address.
        InetSocketAddress iceLocalReflexiveAddress = callPeerMediaHandler.getICELocalReflexiveAddress(mediaType.toString());

        boolean iceLocalReflexiveVisible = iceLocalReflexiveAddress != null;
        ensureVisible(container, R.id.iceLocalReflx, iceLocalReflexiveVisible);
        ensureVisible(container, R.id.iceLocalReflxLabel, iceLocalReflexiveVisible);

        if (iceLocalReflexiveVisible) {
            setTextViewValue(container, R.id.iceLocalReflx, iceLocalReflexiveAddress.getAddress().getHostAddress()
                    + "/" + iceLocalReflexiveAddress.getPort());
            displayedIpPort = true;
        }

        // Local relayed address.
        InetSocketAddress iceLocalRelayedAddress = callPeerMediaHandler.getICELocalRelayedAddress(mediaType.toString());
        boolean iceLocalRelayedVisible = iceLocalRelayedAddress != null;

        ensureVisible(container, R.id.iceLocalRelayed, iceLocalRelayedVisible);
        ensureVisible(container, R.id.iceLocalRelayedLabel, iceLocalRelayedVisible);
        if (iceLocalRelayedAddress != null) {
            setTextViewValue(container, R.id.iceLocalRelayed, iceLocalRelayedAddress.getAddress().getHostAddress()
                    + "/" + iceLocalRelayedAddress.getPort());
            displayedIpPort = true;
        }

        // Remote relayed address.
        InetSocketAddress iceRemoteRelayedAddress = callPeerMediaHandler.getICERemoteRelayedAddress(mediaType.toString());
        boolean isIceRemoteRelayed = iceRemoteRelayedAddress != null;
        ensureVisible(container, R.id.iceRemoteRelayed, isIceRemoteRelayed);
        ensureVisible(container, R.id.iceRemoteRelayedLabel, isIceRemoteRelayed);

        if (isIceRemoteRelayed) {
            setTextViewValue(container, R.id.iceRemoteRelayed, iceRemoteRelayedAddress.getAddress().getHostAddress()
                    + "/" + iceRemoteRelayedAddress.getPort());
            displayedIpPort = true;
        }

        // Remote reflexive address.
        InetSocketAddress iceRemoteReflexiveAddress = callPeerMediaHandler.getICERemoteReflexiveAddress(mediaType.toString());
        boolean isIceRemoteReflexive = iceRemoteReflexiveAddress != null;
        ensureVisible(container, R.id.iceRemoteReflexive, isIceRemoteReflexive);
        ensureVisible(container, R.id.iceRemoteReflxLabel, isIceRemoteReflexive);

        if (isIceRemoteReflexive) {
            setTextViewValue(container, R.id.iceRemoteReflexive,
                    iceRemoteReflexiveAddress.getAddress().getHostAddress() + "/" + iceRemoteReflexiveAddress.getPort());
            displayedIpPort = true;
        }

        // Remote host address.
        InetSocketAddress iceRemoteHostAddress = callPeerMediaHandler.getICERemoteHostAddress(mediaType.toString());

        boolean isIceRemoteHost = iceRemoteHostAddress != null;
        ensureVisible(container, R.id.iceRemoteHostLabel, isIceRemoteHost);
        ensureVisible(container, R.id.iceRemoteHost, isIceRemoteHost);
        if (isIceRemoteHost) {
            setTextViewValue(container, R.id.iceRemoteHost, iceRemoteHostAddress.getAddress().getHostAddress()
                    + "/" + iceRemoteHostAddress.getPort());
            displayedIpPort = true;
        }

        // If the stream does not use ICE, then show the transport IP/port.
        ensureVisible(container, R.id.localIp, !displayedIpPort);
        ensureVisible(container, R.id.localIpLabel, !displayedIpPort);
        ensureVisible(container, R.id.remoteIp, !displayedIpPort);
        ensureVisible(container, R.id.remoteIpLabel, !displayedIpPort);
        if (!displayedIpPort) {
            setTextViewValue(container, R.id.localIp, mediaStreamStats.getLocalIPAddress()
                    + " / " + mediaStreamStats.getLocalPort());
            setTextViewValue(container, R.id.remoteIp, mediaStreamStats.getRemoteIPAddress()
                    + " / " + mediaStreamStats.getRemotePort());
        }

        // Bandwidth.
        String bandwidthStr = DOWN_ARROW + " " + (int) mediaStreamStats.getDownloadRateKiloBitPerSec() + " Kbps " + " " + UP_ARROW + " "
                + (int) mediaStreamStats.getUploadRateKiloBitPerSec() + " Kbps";
        setTextViewValue(container, R.id.bandwidth, bandwidthStr);

        // Loss rate.
        String lossRateStr = DOWN_ARROW + " " + (int) mediaStreamStats.getDownloadPercentLoss() + "% " + UP_ARROW + " "
                + (int) mediaStreamStats.getUploadPercentLoss() + "%";
        setTextViewValue(container, R.id.lossRate, lossRateStr);

        // Decoded with FEC.
        setTextViewValue(container, R.id.decodedWithFEC, String.valueOf(mediaStreamStats.getNbFec()));

        // Discarded percent.
        setTextViewValue(container, R.id.discardedPercent, (int) mediaStreamStats.getPercentDiscarded() + "%");

        // Discarded total.
        String discardedTotalStr = mediaStreamStats.getNbDiscarded() + " (" + mediaStreamStats.getNbDiscardedLate() + " late, "
                + mediaStreamStats.getNbDiscardedFull() + " full, " + mediaStreamStats.getNbDiscardedShrink() + " shrink, "
                + mediaStreamStats.getNbDiscardedReset() + " reset)";
        setTextViewValue(container, R.id.discardedTotal, discardedTotalStr);

        // Adaptive jitter buffer.
        setTextViewValue(container, R.id.adaptiveJitterBuffer, mediaStreamStats.isAdaptiveBufferEnabled() ? "enabled" : "disabled");

        // Jitter buffer delay.
        String jitterDelayStr = "~" + mediaStreamStats.getJitterBufferDelayMs() + "ms; currently in queue: "
                + mediaStreamStats.getPacketQueueCountPackets() + "/" + mediaStreamStats.getPacketQueueSize() + " packets";
        setTextViewValue(container, R.id.jitterBuffer, jitterDelayStr);

        // RTT
        String naStr = getString(R.string.service_gui_callinfo_NA);
        long rttMs = mediaStreamStats.getRttMs();
        String rttStr = rttMs != -1 ? rttMs + " ms" : naStr;
        setTextViewValue(container, R.id.RTT, rttStr);

        // Jitter.
        setTextViewValue(container, R.id.jitter,
                DOWN_ARROW + " " + (int) mediaStreamStats.getDownloadJitterMs() + " ms " + UP_ARROW + (int) mediaStreamStats.getUploadJitterMs() + " ms");
    }

    /**
     * Converts a video size Dimension into its String representation.
     *
     * @param videoSize The video size Dimension, containing the width and the height of the video.
     * @return The String representation of the video width and height, or a String with "Not Available (N.A.)" if the
     * videoSize is null.
     */
    private String videoSizeToString(Dimension videoSize)
    {
        if (videoSize == null) {
            return getString(R.string.service_gui_callinfo_NA);
        }
        return ((int) videoSize.getWidth()) + " x " + ((int) videoSize.getHeight());
    }

    @Override
    public void onStart()
    {
        super.onStart();
        if (call == null) {
            dismiss();
            return;
        }
        startUpdateThread();
    }

    @Override
    public void onStop()
    {
        stopUpdateThread();
        super.onStop();
    }

    /**
     * Starts the update thread.
     */
    private void startUpdateThread()
    {
        this.pollingThread = new InfoUpdateThread();
        pollingThread.start();
    }

    /**
     * Stops the update thread ensuring that it has finished it's job.
     */
    private void stopUpdateThread()
    {
        if (pollingThread != null) {
            pollingThread.ensureFinished();
            pollingThread = null;
        }
    }

    /**
     * Calculates media statistics for all peers. This must be executed on non UI thread or the network on UI thread
     * exception will occur.
     */
    private void updateMediaStats()
    {
        CallConference conference = call.getConference();

        for (CallPeer callPeer : conference.getCallPeers()) {
            if (!(callPeer instanceof MediaAwareCallPeer)) {
                continue;
            }

            CallPeerMediaHandler<?> callPeerMediaHandler = ((MediaAwareCallPeer<?, ?, ?>) callPeer).getMediaHandler();
            if (callPeerMediaHandler == null) {
                continue;
            }
            calcStreamMediaStats(callPeerMediaHandler.getStream(MediaType.AUDIO));
            calcStreamMediaStats(callPeerMediaHandler.getStream(MediaType.VIDEO));
        }
    }

    /**
     * Calculates media stream statistics.
     *
     * @param mediaStream the media stream that will have it's statistics recalculated.
     */
    private void calcStreamMediaStats(MediaStream mediaStream)
    {
        if (mediaStream == null)
            return;

        MediaStreamStats mediaStats = mediaStream.getMediaStreamStats();
        if (mediaStats != null) {
            mediaStats.updateStats();
        }
    }

    /**
     * The thread that periodically recalculates media stream statistics and triggers view updates.
     */
    class InfoUpdateThread extends Thread
    {
        /**
         * The polling loop flag.
         */
        private boolean run = true;

        /**
         * Stops and joins the thread.
         */
        public void ensureFinished()
        {
            try {
                // Immediately stop any further update attempt
                run = false;
                synchronized (this) {
                    this.notify();
                }
                this.join();
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
        }

        @Override
        public void run()
        {
            synchronized (this) {
                while (run) {
                    try {
                        // Recalculate statistics and refresh view.
                        updateMediaStats();
                        updateView();

                        // place loop in wait for next update and release lock
                        this.wait(1000);
                    } catch (InterruptedException e) {
                        throw new RuntimeException(e);
                    }
                }
            }
        }
    }
}
