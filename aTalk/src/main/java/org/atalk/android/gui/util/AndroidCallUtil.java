/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 * 
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.atalk.android.gui.util;

import android.app.Activity;
import android.content.Context;
import android.view.*;
import android.widget.*;

import net.java.sip.communicator.impl.protocol.jabber.ProtocolProviderServiceJabberImpl;
import net.java.sip.communicator.service.protocol.ProtocolProviderService;
import net.java.sip.communicator.util.Logger;
import net.java.sip.communicator.util.account.AccountUtils;

import org.atalk.android.*;
import org.atalk.android.gui.ProgressDialogFragment;
import org.atalk.android.gui.call.CallManager;
import org.atalk.android.gui.dialogs.DialogActivity;
import org.jivesoftware.smack.roster.Roster;
import org.jivesoftware.smack.tcp.XMPPTCPConnection;
import org.jxmpp.jid.impl.JidCreate;
import org.jxmpp.stringprep.XmppStringprepException;

/**
 * @author Yana Stamcheva
 * @author Pawel Domas
 * @author Eng Chong Meng
 */
public class AndroidCallUtil
{
	/**
	 * The logger for this class.
	 */
	private final static Logger logger = Logger.getLogger(AndroidCallUtil.class);

	/**
	 * Field used to track the thread used to create outgoing calls.
	 */
	private static Thread createCallThread;

	/**
	 * Creates an android call.
	 *
	 * @param context
	 * 		the android context
	 * @param callButtonView
	 * 		the button view that generated the call
	 * @param contact
	 * 		the contact address to call
	 * @param isVideoCall
	 * 		true to setup video call
	 */
	public static void createAndroidCall(Context context, View callButtonView, String contact,
			final boolean isVideoCall)
	{
		showCallViaMenu(context, callButtonView, contact, isVideoCall);
	}

	/**
	 * Shows "call via" menu allowing user to selected from multiple providers.
	 *
	 * @param context
	 * 		the android context
	 * @param v
	 * 		the View that will contain the popup menu.
	 * @param calleeAddress
	 * 		the target callee name that will be used.
	 * @param isVideoCall
	 * 		true to setup video call
	 */
	private static void showCallViaMenu(final Context context, View v, final String calleeAddress,
			final boolean isVideoCall)
	{
		PopupMenu popup = new PopupMenu(context, v);
		Menu menu = popup.getMenu();
		ProtocolProviderService mProvider = null;

		for (final ProtocolProviderService provider : AccountUtils.getOnlineProviders()) {
			XMPPTCPConnection connection
					= ((ProtocolProviderServiceJabberImpl) provider).getConnection();

			try {
				if (Roster.getInstanceFor(connection).contains(JidCreate.bareFrom(calleeAddress))) {
					String accountAddress = provider.getAccountID().getAccountJid();
					MenuItem menuItem = menu.add(Menu.NONE, Menu.NONE, Menu.NONE, accountAddress);
					menuItem.setOnMenuItemClickListener(new MenuItem.OnMenuItemClickListener()
					{
						public boolean onMenuItemClick(MenuItem item)
						{
							createCall(context, calleeAddress, provider, isVideoCall);
							return false;
						}
					});
					mProvider = provider;
				}
			}
			catch (XmppStringprepException e) {
				e.printStackTrace();
			}
		}
		if (menu.size() > 1)
			popup.show();
		else
			createCall(context, calleeAddress, mProvider, isVideoCall);
	}

	/**
	 * Creates new call to given <tt>destination</tt> using selected <tt>provider</tt>.
	 *
	 * @param context
	 * 		the android context
	 * @param destination
	 * 		target callee name.
	 * @param provider
	 * 		the provider that will be used to make a call.
	 * @param isVideoCall
	 * 		true to setup video call
	 */
	public static void createCall(final Context context, final String destination,
			final ProtocolProviderService provider, final boolean isVideoCall)
	{
		if (createCallThread != null) {
			logger.warn("Another call is already being created");
			return;
		}
		else if (CallManager.getActiveCallsCount() > 0) {
			logger.warn("Another call is in progress");
			return;
		}

		final long dialogId = ProgressDialogFragment.showProgressDialog(
				aTalkApp.getResString(R.string.service_gui_OUTGOING_CALL),
				aTalkApp.getResString(R.string.service_gui_OUTGOING_CALL_MSG,
						destination));

		createCallThread = new Thread("Create call thread")
		{
			public void run()
			{
				try {
					if (isVideoCall)
						CallManager.createVideoCall(provider, destination);
					else
						CallManager.createCall(provider, destination);
				}
				catch (Throwable t) {
					logger.error("Error creating the call: " + t.getMessage(), t);
					AndroidUtils.showAlertDialog(context,
							context.getString(R.string.service_gui_ERROR), t.getMessage());
				} finally {
					if (DialogActivity.waitForDialogOpened(dialogId)) {
						DialogActivity.closeDialog(aTalkApp.getGlobalContext(), dialogId);
					}
					else {
						logger.error("Failed to wait for the dialog: " + dialogId);
					}
					createCallThread = null;
				}
			}
		};
		createCallThread.start();
	}

	/**
	 * Checks if there is a call in progress. If true then shows a warning toast and finishes the
	 * activity.
	 *
	 * @param activity
	 * 		activity doing a check.
	 * @return <tt>true</tt> if there is call in progress and <tt>Activity</tt> was finished.
	 */
	public static boolean checkCallInProgress(Activity activity)
	{
		if (CallManager.getActiveCallsCount() > 0) {
			logger.warn("Call is in progress");

			Toast t = Toast.makeText(activity, R.string.service_gui_WARN_CALL_IN_PROGRESS,
					Toast.LENGTH_SHORT);
			t.show();

			activity.finish();
			return true;
		}
		else {
			return false;
		}
	}
}
