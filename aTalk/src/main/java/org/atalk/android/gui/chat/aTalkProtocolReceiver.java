package org.atalk.android.gui.chat;

import android.content.Intent;
import android.os.Bundle;

import net.java.sip.communicator.util.Logger;

import org.atalk.service.osgi.OSGiActivity;

import java.net.URI;
import java.net.URISyntaxException;

/**
 * @author Pawel Domas
 */
public class aTalkProtocolReceiver extends OSGiActivity
{
	/**
	 * The logger
	 */
	private final static Logger logger = Logger.getLogger(aTalkProtocolReceiver.class);

	@Override
	protected void onCreate(Bundle savedInstanceState)
	{
		super.onCreate(savedInstanceState);

		Intent intent = getIntent();
		logger.info("Jitsi protocol intent received " + intent);

		String urlStr = intent.getDataString();
		if (urlStr != null) {
			try {
				URI url = new URI(urlStr);
				ChatSessionManager.notifyChatLinkClicked(url);
			}
			catch (URISyntaxException e) {
				logger.error("Error parsing clicked URL", e);
			}
		}
		else {
			logger.warn("No URL supplied in Jitsi link");
		}
		finish();
	}
}
