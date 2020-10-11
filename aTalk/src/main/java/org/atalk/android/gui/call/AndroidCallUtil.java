/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.atalk.android.gui.call;

import android.app.Activity;
import android.content.Context;
import android.view.*;
import android.widget.PopupMenu;
import android.widget.Toast;

import net.java.sip.communicator.service.contactlist.MetaContact;
import net.java.sip.communicator.service.protocol.ProtocolProviderService;
import net.java.sip.communicator.util.account.AccountUtils;

import org.atalk.android.R;
import org.atalk.android.aTalkApp;
import org.atalk.android.gui.dialogs.DialogActivity;
import org.atalk.android.gui.dialogs.ProgressDialogFragment;
import org.atalk.android.gui.util.AndroidUtils;
import org.jivesoftware.smack.XMPPConnection;
import org.jivesoftware.smack.roster.Roster;
import org.jivesoftware.smackx.jinglemessage.packet.JingleMessage;
import org.jxmpp.jid.Jid;

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
     * @param contact the contact address to call
     * @param callButtonView the button view that generated the call
     * @param isVideoCall true to setup video call
     */
    public static void createAndroidCall(Context context, Jid contact, View callButtonView, boolean isVideoCall)
    {
        showCallViaMenu(context, contact, callButtonView, isVideoCall);
    }

    /**
     * Shows "call via" menu allowing user to selected from multiple providers.
     *
     * @param context the android context
     * @param calleeJid the target callee name that will be used.
     * @param v the View that will contain the popup menu.
     * @param isVideoCall true for video call setup
     */
    private static void showCallViaMenu(final Context context, final Jid calleeJid, View v, final boolean isVideoCall)
    {
        PopupMenu popup = new PopupMenu(context, v);
        Menu menu = popup.getMenu();
        ProtocolProviderService mProvider = null;

        // loop through all registered providers to find the callee own provider
        for (final ProtocolProviderService provider : AccountUtils.getOnlineProviders()) {
            XMPPConnection connection = provider.getConnection();
            if (Roster.getInstanceFor(connection).contains(calleeJid.asBareJid())) {
                String accountAddress = provider.getAccountID().getAccountJid();
                MenuItem menuItem = menu.add(Menu.NONE, Menu.NONE, Menu.NONE, accountAddress);
                menuItem.setOnMenuItemClickListener(item -> {
                    createCall(context, provider, calleeJid, isVideoCall);
                    return false;
                });
                mProvider = provider;
            }
            // Pre-assigned current provider in case the calleeAddress is not listed in roaster;
            // e.g call contact from phone book - user the first available
            if (mProvider == null)
                mProvider = provider;
        }

        // Show contact selection menu if more than one choice
        if (menu.size() > 1) {
            popup.show();
        }
        else if (mProvider != null)
            createCall(context, mProvider, calleeJid, isVideoCall);
    }

    /**
     * Creates new call to given <tt>destination</tt> using selected <tt>provider</tt>.
     *
     * @param context the android context
     * @param metaContact target callee metaContact.
     * @param isVideoCall true to setup video call
     * @param callButtonView not null if call via contact list fragment.
     */
    public static void createCall(Context context, MetaContact metaContact, boolean isVideoCall, View callButtonView)
    {
        Jid callee = metaContact.getDefaultContact().getJid();
        ProtocolProviderService pps = metaContact.getDefaultContact().getProtocolProvider();

        boolean isJmSupported = metaContact.isFeatureSupported(JingleMessage.NAMESPACE);
        if (isJmSupported) {
            JingleMessageHelper.createAndSendJingleMessagePropose(pps, callee, isVideoCall);
        }
        else if (callButtonView != null) {
            showCallViaMenu(context, callee, callButtonView, isVideoCall);
        }
        else {
            createCall(context, pps, callee, isVideoCall);
        }
    }

    /**
     * Creates new call to given <tt>destination</tt> using selected <tt>provider</tt>.
     *
     * @param context the android context
     * @param provider the provider that will be used to make a call.
     * @param callee target callee Jid.
     * @param id the Jingle Message call id (must use this to send session-initiate if not null)
     * @param isVideoCall true for video call setup
     */
    public static void createCall(final Context context, final ProtocolProviderService provider,
            final Jid callee, final boolean isVideoCall)
    {
        // Force to null assuming user is making a call seeing no call in progress, otherwise cannot make call at all
        if (createCallThread != null) {
            createCallThread = null;
            aTalkApp.showToastMessage("Another call is already being created. restarting call thread!");
        }
        else if (CallManager.getActiveCallsCount() > 0) {
            aTalkApp.showToastMessage("Another call is already in progress!");
            return;
        }

        final long dialogId = ProgressDialogFragment.showProgressDialog(
                aTalkApp.getResString(R.string.service_gui_CALL_OUTGOING),
                aTalkApp.getResString(R.string.service_gui_CALL_OUTGOING_MSG, callee));

        createCallThread = new Thread("Create call thread")
        {
            public void run()
            {
                try {
                    CallManager.createCall(provider, callee.toString(), isVideoCall);
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
