/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 * 
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.atalk.android.gui.call;

import org.atalk.android.aTalkApp;
import org.atalk.android.gui.util.ViewUtil;
import org.atalk.service.osgi.OSGiFragment;
import org.atalk.android.R;

import android.content.Context;
import android.os.Bundle;
import android.support.v4.app.FragmentActivity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

/**
 * Fragment displayed in <tt>VideoCallActivity</tt> when the call has ended.
 *
 * @author Pawel Domas
 */
public class CallEnded extends OSGiFragment
{
	@Override
	public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState)
	{
		View v = inflater.inflate(R.layout.call_ended, container, false);

		ViewUtil.setTextViewValue(v, R.id.callTime, VideoCallActivity.callState.callDuration);
		String errorReason = VideoCallActivity.callState.errorReason;
		if (!errorReason.isEmpty()) {
			ViewUtil.setTextViewValue(v, R.id.callErrorReason, errorReason);
		}
		else {
			ViewUtil.ensureVisible(v, R.id.callErrorReason, false);
		}

		v.findViewById(R.id.callHangupButton).setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v)
			{
                FragmentActivity ctx = getActivity();
				ctx.finish();
				ctx.startActivity(aTalkApp.getHomeIntent());
			}
		});
		return v;
	}
}
