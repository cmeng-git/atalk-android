/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 * 
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.atalk.android.gui.call;

import org.atalk.impl.neomedia.device.util.OpenGlCtxProvider;
import org.atalk.impl.neomedia.device.util.CameraUtils;

import android.os.Bundle;

/**
 * Video handler fragment for API18 and above. Provides OpenGL context for displaying local preview in direct surface
 * encoding mode.
 *
 * @author Pawel Domas
 */
public class VideoHandlerFragmentAPI18 extends VideoHandlerFragment
{

	@Override
	public void onActivityCreated(Bundle savedInstanceState)
	{
		super.onActivityCreated(savedInstanceState);
		CameraUtils.localPreviewCtxProvider = new OpenGlCtxProvider(getActivity(), localPreviewContainer);
	}

	@Override
	public void onDestroy()
	{
		super.onDestroy();

		// This provider is no longer valid
		CameraUtils.localPreviewCtxProvider = null;
	}
}
