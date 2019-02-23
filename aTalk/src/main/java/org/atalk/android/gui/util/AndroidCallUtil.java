/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.atalk.android.gui.util;

import android.app.Activity;
import android.content.Context;
import android.view.*;
import android.widget.PopupMenu;
import android.widget.Toast;

import net.java.sip.communicator.service.protocol.ProtocolProviderService;
import net.java.sip.communicator.util.account.AccountUtils;

import org.atalk.android.R;
import org.atalk.android.aTalkApp;
import org.atalk.android.gui.call.CallManager;
import org.atalk.android.gui.dialogs.DialogActivity;
import org.atalk.android.gui.dialogs.ProgressDialogFragment;
import org.jivesoftware.smack.roster.Roster;
import org.jivesoftware.smack.tcp.XMPPTCPConnection;
import org.jxmpp.jid.impl.JidCreate;
import org.jxmpp.stringprep.XmppStringprepException;

import timber.log.Timber;

/**
 * @author Yana Stamcheva
 * @author Pawel Domas
 * @author Eng Chong Meng
 */
public class AndroidCallUtil
{
    /**
     * Field used to track the thread used to create outgoing calls.
     */
    private static Thread createCallThread;

    /**
     * Creates an android call.
     *
     * @param context the android context
     * @param callButtonView the button view that generated the call
     * @param contact the contact address to call
     * @param isVideoCall true to setup video call
     */
    public static void createAndroidCall(Context context, View callButtonView, String contact,
            final boolean isVideoCall)
    {
        showCallViaMenu(context, callButtonView, contact, isVideoCall);
    }

    /**
     * Shows "call via" menu allowing user to selected from multiple providers.
     *
     * @param context the android context
     * @param v the View that will contain the popup menu.
     * @param calleeAddress the target callee name that will be used.
     * @param isVideoCall true to setup video call
     */
    private static void showCallViaMenu(final Context context, View v, final String calleeAddress,
            final boolean isVideoCall)
    {
        PopupMenu popup = new PopupMenu(context, v);
        Menu menu = popup.getMenu();
        ProtocolProviderService mProvider = null;

        // loop through all registered providers to find the calleeAddress own provider
        for (final ProtocolProviderService provider : AccountUtils.getOnlineProviders()) {
            XMPPTCPConnection connection = provider.getConnection();
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
                // Pre-assigned current provider in case the calleeAddress is not listed in roaster;
                // e.g call contact from phone book - user the first available
                if (mProvider == null)
                    mProvider = provider;

            } catch (XmppStringprepException e) {
                e.printStackTrace();
            }
        }
        if (menu.size() > 1)
            popup.show();
        else if (mProvider != null)
            createCall(context, calleeAddress, mProvider, isVideoCall);
    }

    /**
     * Creates new call to given <tt>destination</tt> using selected <tt>provider</tt>.
     *
     * @param context the android context
     * @param destination target callee name.
     * @param provider the provider that will be used to make a call.
     * @param isVideoCall true to setup video call
     */
    public static void createCall(final Context context, final String destination,
            final ProtocolProviderService provider, final boolean isVideoCall)
    {
        if (createCallThread != null) {
            // force to null assuming user is making a call seeing no call in progress,
            // otherwise cannot make call at all
            createCallThread = null;
            aTalkApp.showToastMessage("Another call is already being created. End and restart!");
        }
        else if (CallManager.getActiveCallsCount() > 0) {
            aTalkApp.showToastMessage("Another call is in progress!");
            return;
        }

        final long dialogId = ProgressDialogFragment.showProgressDialog(
                aTalkApp.getResString(R.string.service_gui_OUTGOING_CALL),
                aTalkApp.getResString(R.string.service_gui_OUTGOING_CALL_MSG, destination));

        createCallThread = new Thread("Create call thread")
        {
            public void run()
            {
                try {
                    if (isVideoCall)
                        CallManager.createVideoCall(provider, destination);
                    else
                        CallManager.createCall(provider, destination);
                } catch (Throwable t) {
                    Timber.e(t, "Error creating the call: %s", t.getMessage());
                    AndroidUtils.showAlertDialog(context, context.getString(R.string.service_gui_ERROR), t.getMessage());
                } finally {
                    if (DialogActivity.waitForDialogOpened(dialogId)) {
                        DialogActivity.closeDialog(dialogId);
                    }
                    else {
                        Timber.e("Failed to wait for the dialog: %s", dialogId);
                    }
                    createCallThread = null;
                }
            }
        };
        createCallThread.start();
    }

    /**
     * Checks if there is a call in progress. If true then shows a warning toast and finishes the activity.
     *
     * @param activity activity doing a check.
     * @return <tt>true</tt> if there is call in progress and <tt>Activity</tt> was finished.
     */
    public static boolean checkCallInProgress(Activity activity)
    {
        if (CallManager.getActiveCallsCount() > 0) {
            Timber.w("Call is in progress");
            Toast.makeText(activity, R.string.service_gui_WARN_CALL_IN_PROGRESS, Toast.LENGTH_SHORT).show();
            activity.finish();
            return true;
        }
        else {
            return false;
        }
    }
}
