/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.atalk.android.gui.call.notification;

import android.content.*;
import android.media.AudioManager;

import net.java.sip.communicator.service.protocol.Call;

import org.atalk.android.aTalkApp;
import org.atalk.android.gui.call.CallManager;
import org.atalk.android.plugin.timberlog.TimberLog;

import timber.log.Timber;

/**
 * <tt>BroadcastReceiver</tt> that listens for {@link #CALL_CTRL_ACTION} action and performs
 * few basic operations(mute, hangup...) on the call.<br/>
 * Target call must be specified by ID passed as extra argument under {@link #EXTRA_CALL_ID} key.
 * The IDs are managed by {@link CallManager}.<br/>
 * Specific operation must be passed under {@link #EXTRA_ACTION} key. Currently supported operations:<br/>
 * {@link #ACTION_TOGGLE_SPEAKER} - toggles between speaker on / off. <br/>
 * {@link #ACTION_TOGGLE_MUTE} - toggles between muted and not muted call state. <br/>
 * {@link #ACTION_TOGGLE_ON_HOLD} - toggles the on hold call state.
 * {@link #ACTION_HANGUP} - ends the call. <br/>
 *
 * @author Pawel Domas
 * @author Eng Chong Meng
 */
public class CallControl extends BroadcastReceiver
{
    /**
     * Call control action name
     */
    public static final String CALL_CTRL_ACTION = "org.atalk.call.control";

    /**
     * Extra key for call id managed by {@link CallManager}.
     */
    public static final String EXTRA_CALL_ID = "call_id";

    /**
     * Extra key that identifies call action.
     */
    public static final String EXTRA_ACTION = "action";

    /**
     * Toggle speakerphone action value.
     */
    private static final int ACTION_TOGGLE_SPEAKER = 1;

    /**
     * The toggle mute action value. Toggles between muted/not muted call state.
     */
    public static final int ACTION_TOGGLE_MUTE = 2;

    /**
     * The toggle on hold status action value.
     */
    public static final int ACTION_TOGGLE_ON_HOLD = 3;

    /**
     * The hangup action value. Ends the call.
     */
    public static final int ACTION_HANGUP = 4;

    /**
     * {@inheritDoc}
     */
    @Override
    public void onReceive(Context context, Intent intent)
    {
        String callID = intent.getStringExtra(EXTRA_CALL_ID);
        if (callID == null) {
            Timber.e("Extra call ID is null");
            return;
        }

        Call call = CallManager.getActiveCall(callID);
        if (call == null) {
            Timber.e("Call with id: %s does not exists", callID );
            return;
        }

        int action = intent.getIntExtra(EXTRA_ACTION, -1);
        if (action == -1) {
            Timber.e("No action supplied");
            return;
        }

        switch (action) {
            case ACTION_TOGGLE_SPEAKER:
                Timber.log(TimberLog.FINER, "Action TOGGLE SPEAKER");
                AudioManager audio = (AudioManager) aTalkApp.getGlobalContext().getSystemService(Context.AUDIO_SERVICE);
                audio.setSpeakerphoneOn(!audio.isSpeakerphoneOn());
                break;

            case ACTION_TOGGLE_MUTE:
                Timber.log(TimberLog.FINER, "Action TOGGLE MUTE");
                boolean isMute = CallManager.isMute(call);
                CallManager.setMute(call, !isMute);
                break;

            case ACTION_TOGGLE_ON_HOLD:
                Timber.log(TimberLog.FINER, "Action TOGGLE ON HOLD");
                boolean isOnHold = CallManager.isLocallyOnHold(call);
                CallManager.putOnHold(call, !isOnHold);
                break;

            case ACTION_HANGUP:
                Timber.log(TimberLog.FINER, "Action HANGUP");
                CallManager.hangupCall(call);
                break;

            default:
                Timber.w("No valid action supplied");
        }
    }

    /**
     * Creates the <tt>Intent</tt> for {@link #ACTION_HANGUP}.
     *
     * @param callID the ID of target call.
     * @return the <tt>Intent</tt> for {@link #ACTION_HANGUP}.
     */
    public static Intent getHangupIntent(String callID)
    {
        return createIntent(callID, ACTION_HANGUP);
    }

    /**
     * Creates the <tt>Intent</tt> for {@link #ACTION_TOGGLE_MUTE}.
     *
     * @param callID the ID of target call.
     * @return the <tt>Intent</tt> for {@link #ACTION_TOGGLE_MUTE}.
     */
    public static Intent getToggleMuteIntent(String callID)
    {
        return createIntent(callID, ACTION_TOGGLE_MUTE);
    }

    /**
     * Creates the <tt>Intent</tt> for {@link #ACTION_TOGGLE_ON_HOLD}.
     *
     * @param callID the ID of target call.
     * @return the <tt>Intent</tt> for {@link #ACTION_TOGGLE_ON_HOLD}.
     */
    public static Intent getToggleOnHoldIntent(String callID)
    {
        return createIntent(callID, ACTION_TOGGLE_ON_HOLD);
    }

    /**
     * Creates the <tt>Intent</tt> for {@link #ACTION_TOGGLE_ON_HOLD}.
     *
     * @param callID the ID of target call.
     * @return the <tt>Intent</tt> for {@link #ACTION_TOGGLE_ON_HOLD}.
     */
    public static Intent getToggleSpeakerIntent(String callID)
    {
        return createIntent(callID, ACTION_TOGGLE_SPEAKER);
    }

    /**
     * Creates new <tt>Intent</tt> for given call <tt>action</tt> value that will be performed on the
     * call identified by <tt>callID</tt>.
     *
     * @param callID target call ID managed by {@link CallManager}.
     * @param action the action value that will be used.
     * @return new <tt>Intent</tt> for given call <tt>action</tt> value that will be performed on the
     * call identified by <tt>callID</tt>.
     */
    private static Intent createIntent(String callID, int action)
    {
        Intent intent = new Intent();
        intent.setAction(CallControl.CALL_CTRL_ACTION);
        intent.putExtra(CallControl.EXTRA_CALL_ID, callID);
        intent.putExtra(CallControl.EXTRA_ACTION, action);
        return intent;
    }
}
