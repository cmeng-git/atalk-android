/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.atalk.android.gui.call;

import android.os.Bundle;
import android.view.*;
import android.widget.ImageView;

import org.atalk.android.R;
import org.atalk.android.aTalkApp;
import org.atalk.android.gui.util.AndroidImageUtil;
import org.atalk.android.gui.util.ViewUtil;
import org.atalk.service.osgi.OSGiFragment;
import org.jivesoftware.smackx.avatar.AvatarManager;
import org.jxmpp.jid.BareJid;

import androidx.annotation.NonNull;
import androidx.fragment.app.FragmentActivity;
import timber.log.Timber;

/**
 * Fragment displayed in <tt>VideoCallActivity</tt> when the call has ended.
 *
 * @author Pawel Domas
 */
public class CallEnded extends OSGiFragment implements View.OnClickListener
{
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState)
    {
        View v = inflater.inflate(R.layout.call_ended, container, false);

        // Display callPeer avatar; take care NPE from field
        byte[] avatar = null;
        try {
            BareJid bareJid = VideoCallActivity.callState.callPeer.asBareJid();
            avatar = AvatarManager.getAvatarImageByJid(bareJid);
        } catch (Exception e) {
            Timber.w("Failed to find callPeer Jid");
        }
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

        v.findViewById(R.id.button_call_hangup).setOnClickListener(this);
        v.findViewById(R.id.button_call_back_to_chat).setOnClickListener(this);
        return v;
    }

    /**
     * Handles buttons action events. the <tt>ActionEvent</tt> that notified us
     */
    @Override
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
}
