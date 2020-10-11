/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.atalk.android.gui.call;

import android.app.Activity;
import android.os.Bundle;
import android.view.*;
import android.widget.Toast;

import net.java.sip.communicator.service.protocol.*;
import net.java.sip.communicator.service.protocol.event.*;
import net.java.sip.communicator.service.protocol.media.MediaAwareCallPeer;

import org.atalk.android.R;
import org.atalk.android.gui.util.ViewUtil;
import org.atalk.android.util.java.awt.Component;
import org.atalk.service.neomedia.*;
import org.atalk.service.osgi.OSGiDialogFragment;
import org.atalk.util.MediaType;
import org.atalk.util.event.VideoEvent;
import org.atalk.util.event.VideoListener;

import java.util.Iterator;
import java.util.List;

import timber.log.Timber;

/**
 * The dialog shows security information for ZRTP protocol. Allows user to verify/clear security authentication string.
 * It will be shown only if the call is secured (i.e. there is security control available).
 * Parent <tt>Activity</tt> should implement {@link SasVerificationListener} in order to receive SAS
 * verification status updates performed by this dialog.
 *
 * @author Pawel Domas
 * @author Eng Chong Meng
 */
public class ZrtpInfoDialog extends OSGiDialogFragment implements CallPeerSecurityListener, VideoListener
{
    /**
     * The extra key for call ID managed by {@link CallManager}.
     */
    private static final String EXTRA_CALL_KEY = "org.atalk.android.call_id";

    /**
     * The listener object that will be notified on SAS string verification status change.
     */
    private SasVerificationListener verificationListener;

    /**
     * The {@link MediaAwareCallPeer} used by this dialog.
     */
    private MediaAwareCallPeer<?, ?, ?> mediaAwarePeer;

    /**
     * The {@link ZrtpControl} used as a master security controller. Retrieved from AUDIO stream.
     */
    private ZrtpControl masterControl;

    /**
     * Dialog view container for ZRTP info display
     */
    private View viewContainer;

    /**
     * {@inheritDoc}
     */
    @Override
    public void onAttach(Activity activity)
    {
        if (activity instanceof SasVerificationListener) {
            verificationListener = (SasVerificationListener) activity;
        }
        super.onAttach(activity);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void onDetach()
    {
        verificationListener = null;
        super.onDetach();
    }

    /**
     * Notifies the listener(if any) about the SAS verification update.
     *
     * @param isVerified <tt>true</tt> if the SAS string has been verified by the user.
     */
    private void notifySasVerified(boolean isVerified)
    {
        if (verificationListener != null)
            verificationListener.onSasVerificationChanged(isVerified);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState)
    {
        // Retrieves the call from manager.
        String callKey = getArguments().getString(EXTRA_CALL_KEY);
        Call call = CallManager.getActiveCall(callKey);

        if (call != null) {
            // Gets first media aware call peer
            Iterator<? extends CallPeer> callPeers = call.getCallPeers();
            if (callPeers.hasNext()) {
                CallPeer callPeer = callPeers.next();
                if (callPeer instanceof MediaAwareCallPeer<?, ?, ?>) {
                    this.mediaAwarePeer = (MediaAwareCallPeer<?, ?, ?>) callPeer;
                }
            }
        }
        // Retrieves security control for master stream(AUDIO)
        if (mediaAwarePeer != null) {
            SrtpControl srtpCtrl = mediaAwarePeer.getMediaHandler().getEncryptionMethod(MediaType.AUDIO);
            if (srtpCtrl instanceof ZrtpControl) {
                this.masterControl = (ZrtpControl) srtpCtrl;
            }
        }

        viewContainer = inflater.inflate(R.layout.zrtp_info_dialog, container, false);
        View cancelBtn = viewContainer.findViewById(R.id.zrtp_ok);
        cancelBtn.setOnClickListener(view -> dismiss());

        View confirmBtn = viewContainer.findViewById(R.id.security_confirm);
        confirmBtn.setOnClickListener(view -> {
            if (mediaAwarePeer.getCall() == null)
                return;

            // Confirms / clears SAS confirmation status
            masterControl.setSASVerification(!masterControl.isSecurityVerified());
            updateVerificationStatus();
            notifySasVerified(masterControl.isSecurityVerified());
        });

        if (getDialog() != null)
            getDialog().setTitle(R.string.service_gui_SECURITY_INFO);
        return viewContainer;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void onStart()
    {
        super.onStart();
        if (mediaAwarePeer == null) {
            showToast("This call does not contain media information");
            dismiss();
            return;
        }
        if (masterControl == null) {
            showToast("This call does not contain security information");
            dismiss();
            return;
        }

        mediaAwarePeer.addCallPeerSecurityListener(this);
        mediaAwarePeer.getMediaHandler().addVideoListener(this);

        ViewUtil.setTextViewValue(viewContainer, R.id.security_auth_str, getSecurityString());

        ViewUtil.setTextViewValue(viewContainer, R.id.security_cipher,
                getString(R.string.service_gui_security_CIPHER, masterControl.getCipherString()));

        updateVerificationStatus();
        boolean isAudioSecure = masterControl != null && masterControl.getSecureCommunicationStatus();
        updateAudioSecureStatus(isAudioSecure);

        MediaStream videoStream = mediaAwarePeer.getMediaHandler().getStream(MediaType.VIDEO);
        updateVideoSecureStatus(videoStream != null && videoStream.getSrtpControl().getSecureCommunicationStatus());
    }

    /**
     * Updates SAS verification status display.
     */
    private void updateVerificationStatus()
    {
        boolean verified = masterControl.isSecurityVerified();
        Timber.d("Is sas verified? %s", verified);

        String txt = verified ? getString(R.string.service_gui_security_STRING_COMPARED) : getString(R.string.service_gui_security_COMPARE_WITH_PARTNER_SHORT);
        ViewUtil.setTextViewValue(viewContainer, R.id.security_compare, txt);

        String confirmTxt = verified ? getString(R.string.service_gui_security_CLEAR) : getString(R.string.service_gui_security_CONFIRM);
        ViewUtil.setTextViewValue(viewContainer, R.id.security_confirm, confirmTxt);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void onStop()
    {
        if (mediaAwarePeer != null) {
            mediaAwarePeer.removeCallPeerSecurityListener(this);
            mediaAwarePeer.getMediaHandler().removeVideoListener(this);
        }
        super.onStop();
    }

    /**
     * Shows the toast on the screen with given <tt>text</tt>.
     *
     * @param text the message text that will be used.
     */
    private void showToast(String text)
    {
        Toast toast = Toast.makeText(getActivity(), text, Toast.LENGTH_LONG);
        toast.show();
    }

    /**
     * Formats the security string.
     *
     * @return Returns formatted security authentication string.
     */
    private String getSecurityString()
    {
        String securityString = masterControl.getSecurityString();
        if (securityString != null) {
            final String sb = String.valueOf(
                    securityString.charAt(0)) + ' ' +
                    securityString.charAt(1) + ' ' +
                    securityString.charAt(2) + ' ' +
                    securityString.charAt(3);
            return sb;
        }
        else {
            return "";
        }
    }

    /**
     * Updates audio security displays according to given status flag.
     *
     * @param isSecure <tt>true</tt> if the audio is secure.
     */
    private void updateAudioSecureStatus(boolean isSecure)
    {
        String audioStr = isSecure ? getString(R.string.service_gui_security_SECURE_AUDIO) : getString(R.string.service_gui_security_AUDIO_NOT_SECURED);

        ViewUtil.setTextViewValue(viewContainer, R.id.secure_audio_text, audioStr);
        int iconId = isSecure ? R.drawable.secure_audio_on_light : R.drawable.secure_audio_off_light;
        ViewUtil.setImageViewIcon(viewContainer, R.id.secure_audio_icon, iconId);
    }

    /**
     * Checks video stream security status.
     *
     * @return <tt>true</tt> if the video is secure.
     */
    private boolean isVideoSecure()
    {
        MediaStream videoStream = mediaAwarePeer.getMediaHandler().getStream(MediaType.VIDEO);
        return videoStream != null && videoStream.getSrtpControl().getSecureCommunicationStatus();
    }

    /**
     * Updates video security displays.
     *
     * @param isSecure <tt>true</tt> if video stream is secured.
     */
    private void updateVideoSecureStatus(boolean isSecure)
    {
        boolean isVideo = false;

        OperationSetVideoTelephony videoTelephony = mediaAwarePeer.getProtocolProvider().getOperationSet(OperationSetVideoTelephony.class);
        if (videoTelephony != null) {
            /*
             * The invocation of MediaAwareCallPeer.isLocalVideoStreaming() is cheaper than the invocation of
             * OperationSetVideoTelephony.getVisualComponents(CallPeer).
             */
            isVideo = mediaAwarePeer.isLocalVideoStreaming();
            if (!isVideo) {
                List<Component> videos = videoTelephony.getVisualComponents(mediaAwarePeer);
                isVideo = ((videos != null) && (videos.size() != 0));
            }
        }

        ViewUtil.ensureVisible(viewContainer, R.id.secure_video_text, isVideo);
        ViewUtil.ensureVisible(viewContainer, R.id.secure_video_icon, isVideo);

        /*
         * If there's no video skip this part, as controls will be hidden.
         */
        if (!isVideo)
            return;

        String videoText = isSecure ? getString(R.string.service_gui_security_SECURE_VIDEO) : getString(R.string.service_gui_security_VIDEO_NOT_SECURED);
        ViewUtil.setTextViewValue(viewContainer, R.id.secure_video_text, videoText);

        ViewUtil.setImageViewIcon(viewContainer, R.id.secure_video_icon, isSecure
                ? R.drawable.secure_video_on_light : R.drawable.secure_video_off_light);
    }

    /**
     * The handler for the security event received. The security event represents an indication of change in the
     * security status.
     *
     * @param securityEvent the security event received
     */
    public void securityOn(CallPeerSecurityOnEvent securityEvent)
    {
        int sessionType = securityEvent.getSessionType();
        if (sessionType == CallPeerSecurityStatusEvent.AUDIO_SESSION) {
            // Audio security on
            updateAudioSecureStatus(true);
        }
        else if (sessionType == CallPeerSecurityStatusEvent.VIDEO_SESSION) {
            // Video security on
            updateVideoSecureStatus(true);
        }
    }

    /**
     * The handler for the security event received. The security event represents an indication of change in the
     * security status.
     *
     * @param securityEvent the security event received
     */
    public void securityOff(CallPeerSecurityOffEvent securityEvent)
    {
        int sessionType = securityEvent.getSessionType();
        if (sessionType == CallPeerSecurityStatusEvent.AUDIO_SESSION) {
            // Audio security off
            updateAudioSecureStatus(false);
        }
        else if (sessionType == CallPeerSecurityStatusEvent.VIDEO_SESSION) {
            // Video security off
            updateVideoSecureStatus(false);
        }
    }

    /**
     * {@inheritDoc}
     */
    public void securityTimeout(CallPeerSecurityTimeoutEvent securityTimeoutEvent)
    {
    }

    /**
     * {@inheritDoc}
     */
    public void securityMessageReceived(CallPeerSecurityMessageEvent event)
    {
        Timber.i("### ZRTP security Message Received: %s", event.getMessage());
    }

    /**
     * {@inheritDoc}
     */
    public void securityNegotiationStarted(CallPeerSecurityNegotiationStartedEvent securityStartedEvent)
    {
    }

    /**
     * Refreshes video security displays on GUI thread.
     */
    private void refreshVideoOnUIThread()
    {
        runOnUiThread(() -> updateVideoSecureStatus(isVideoSecure()));
    }

    /**
     * {@inheritDoc}
     */
    public void videoAdded(VideoEvent videoEvent)
    {
        refreshVideoOnUIThread();
    }

    /**
     * {@inheritDoc}
     */
    public void videoRemoved(VideoEvent videoEvent)
    {
        refreshVideoOnUIThread();
    }

    /**
     * {@inheritDoc}
     */
    public void videoUpdate(VideoEvent videoEvent)
    {
        refreshVideoOnUIThread();
    }

    /**
     * The security authentication string verification status listener.
     */
    public interface SasVerificationListener
    {
        /**
         * Called when SAS verification status is updated.
         *
         * @param isVerified <tt>true</tt> if SAS is verified by the user.
         */
        void onSasVerificationChanged(boolean isVerified);
    }

    /**
     * Creates new parametrized instance of {@link ZrtpInfoDialog}.
     *
     * @param callKey the call key managed by {@link CallManager}.
     * @return parametrized instance of <tt>ZrtpInfoDialog</tt>.
     */
    public static ZrtpInfoDialog newInstance(String callKey)
    {
        ZrtpInfoDialog infoDialog = new ZrtpInfoDialog();

        Bundle arguments = new Bundle();
        arguments.putString(EXTRA_CALL_KEY, callKey);
        infoDialog.setArguments(arguments);

        return infoDialog;
    }
}
