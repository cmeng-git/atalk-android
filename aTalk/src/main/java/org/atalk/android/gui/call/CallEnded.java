/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.atalk.android.gui.call;

import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.v4.app.FragmentActivity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;

import org.atalk.android.R;
import org.atalk.android.aTalkApp;
import org.atalk.android.gui.util.AndroidImageUtil;
import org.atalk.android.gui.util.ViewUtil;
import org.atalk.service.osgi.OSGiFragment;
import org.jivesoftware.smackx.avatar.AvatarManager;

/**
 * Fragment displayed in <tt>VideoCallActivity</tt> when the call has ended.
 *
 * @author Pawel Domas
 */
public class CallEnded extends OSGiFragment
{
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState)
    {
        View v = inflater.inflate(R.layout.call_ended, container, false);

        // Display callPeer avatar
        byte[] avatar = AvatarManager.getAvatarImageByJid(VideoCallActivity.callState.callPeer.asBareJid());
        if (avatar != null)
            ((ImageView) v.findViewById(R.id.calleeAvatar)).setImageBitmap(AndroidImageUtil.bitmapFromBytes(avatar));

        ViewUtil.setTextViewValue(v, R.id.callTime, VideoCallActivity.callState.callDuration);
        String errorReason = VideoCallActivity.callState.errorReason;
        if (!errorReason.isEmpty()) {
            ViewUtil.setTextViewValue(v, R.id.callErrorReason, errorReason);
        }
        else {
            ViewUtil.ensureVisible(v, R.id.callErrorReason, false);
        }

        View.OnClickListener onClickAction = onActionListener();
        v.findViewById(R.id.button_call_hangup).setOnClickListener(onClickAction);
        v.findViewById(R.id.button_call_back_to_chat).setOnClickListener(onClickAction);
        return v;
    }

    /**
     * Handles buttons action events.
     * the <tt>ActionEvent</tt> that notified us
     */
    protected View.OnClickListener onActionListener()
    {
        return new View.OnClickListener()
        {
            public void onClick(View v)
            {
                switch (v.getId()) {
                    case R.id.button_call_hangup:
                    case R.id.button_call_back_to_chat:
                        FragmentActivity ctx = getActivity();
                        ctx.finish();
                        ctx.startActivity(aTalkApp.getHomeIntent());
                }
            }
        };
    }
}
