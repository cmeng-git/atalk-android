/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 * 
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.atalk.impl.androidtray;

import android.content.Context;
import android.widget.Toast;

import net.java.sip.communicator.service.systray.AbstractPopupMessageHandler;
import net.java.sip.communicator.service.systray.PopupMessage;

import org.atalk.android.aTalkApp;
import org.atalk.android.gui.widgets.ClickableToastController;

/**
 * TODO: Toast popup handler stub. It should be registered by displayed Activity as we need to hold the UI thread to
 * show Toasts. Also {@link ClickableToastController} may be used to catch the clicks.
 *
 * @author Pawel Domas
 */
public class ToastPopupMessageHandler extends AbstractPopupMessageHandler {
	/**
	 * {@inheritDoc}
	 */
	public void showPopupMessage(PopupMessage popupMessage) {
		Context context = aTalkApp.getGlobalContext();
		CharSequence text = popupMessage.getMessage();
		int duration = Toast.LENGTH_SHORT;

		Toast toast = Toast.makeText(context, text, duration);
		toast.show();
	}

	/**
	 * {@inheritDoc}
	 */
	public int getPreferenceIndex() {
		// +1 for native toaster
		return 1;
	}
}
