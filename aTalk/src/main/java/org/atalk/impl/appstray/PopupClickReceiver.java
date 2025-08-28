/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.atalk.impl.appstray;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;

import androidx.core.content.ContextCompat;

import org.atalk.android.aTalkApp;

import timber.log.Timber;

/**
 * This <code>BroadcastReceiver</code> listens for <code>PendingIntent</code> coming from popup messages notifications. There
 * are two actions handled:<br/>
 * - <code>POPUP_CLICK_ACTION</code> fired when notification is clicked<br/>
 * - <code>POPUP_CLEAR_ACTION</code> fired when notification is cleared<br/>
 * Those events are passed to <code>NotificationPopupHandler</code> to take appropriate decisions.
 *
 * @author Pawel Domas
 * @author Eng Chong Meng
 */
public class PopupClickReceiver extends BroadcastReceiver {
    /**
     * Popup clicked action name used for <code>Intent</code> handling by this <code>BroadcastReceiver</code>.
     */
    public static final String ACTION_POPUP_CLICK = "org.atalk.ui.popup_click";

    /**
     * Popup cleared action name used for <code>Intent</code> handling by this <code>BroadcastReceiver</code>
     */
    public static final String ACTION_POPUP_CLEAR = "org.atalk.ui.popup_discard";

    /**
     * Android Notification Actions
     */
    public static final String ACTION_MARK_AS_READ = "mark_as_read";
    public static final String ACTION_SNOOZE = "snooze";

    private static final String ACTION_REPLY_TO = "reply_to";
    // private static final String ACTION_CLEAR_NOTIFICATION = "clear_notification";
    // private static final String ACTION_DISMISS_ERROR_NOTIFICATIONS = "dismiss_error";

    public static final String ACTION_CALL_DISMISS = "call_dismiss";
    public static final String ACTION_CALL_ANSWER = "call_answer";

    /**
     * <code>Intent</code> extra key that provides the notification id.
     */
    private static final String EXTRA_NOTIFICATION_ID = "notification_id";

    /**
     * <code>NotificationPopupHandler</code> that manages the popups.
     */
    private final NotificationPopupHandler notificationHandler;

    /**
     * Creates new instance of <code>PopupClickReceiver</code> bound to given <code>notificationHandler</code>.
     *
     * @param notificationHandler the <code>NotificationPopupHandler</code> that manages the popups.
     */
    public PopupClickReceiver(NotificationPopupHandler notificationHandler) {
        this.notificationHandler = notificationHandler;
    }

    /**
     * Registers this <code>BroadcastReceiver</code>.
     */
    void registerReceiver() {
        IntentFilter filter = new IntentFilter();
        filter.addAction(ACTION_POPUP_CLICK);
        filter.addAction(ACTION_POPUP_CLEAR);
        filter.addAction(ACTION_REPLY_TO);
        filter.addAction(ACTION_MARK_AS_READ);
        filter.addAction(ACTION_SNOOZE);
        filter.addAction(ACTION_CALL_ANSWER);
        filter.addAction(ACTION_CALL_DISMISS);
        ContextCompat.registerReceiver(aTalkApp.getInstance(), this, filter, ContextCompat.RECEIVER_NOT_EXPORTED);
    }

    /**
     * Unregisters this <code>BroadcastReceiver</code>.
     */
    void unregisterReceiver() {
        aTalkApp.getInstance().unregisterReceiver(this);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void onReceive(Context context, Intent intent) {
        int notificationId = intent.getIntExtra(EXTRA_NOTIFICATION_ID, -1);
        if (notificationId == -1) {
            Timber.w("Invalid notification id = -1");
            return;
        }

        String action = intent.getAction();
        Timber.d("Popup action: %s", action);
        if (action == null)
            return;

        switch (action) {
            case ACTION_POPUP_CLICK:
                notificationHandler.fireNotificationClicked(notificationId);
                break;

            case ACTION_REPLY_TO:
                notificationHandler.fireNotificationClicked(notificationId, intent);
                break;

            case ACTION_POPUP_CLEAR:
            case ACTION_MARK_AS_READ:
            case ACTION_SNOOZE:
            case ACTION_CALL_ANSWER: // this will not be called here
            case ACTION_CALL_DISMISS:
                notificationHandler.fireNotificationClicked(notificationId, action);
                break;

            default:
                Timber.w("Unsupported action: %s", action);
        }
    }

    /**
     * Creates "on click" <code>Intent</code> for notification popup identified by given <code>notificationId</code>.
     *
     * @param notificationId the id of popup message notification.
     *
     * @return new "on click" <code>Intent</code> for given <code>notificationId</code>.
     */
    public static Intent createIntent(int notificationId) {
        Intent intent = new Intent();
        intent.setPackage(aTalkApp.getInstance().getPackageName());
        intent.setAction(ACTION_POPUP_CLICK);
        intent.putExtra(EXTRA_NOTIFICATION_ID, notificationId);
        return intent;
    }

    /**
     * Creates "on deleted" <code>Intent</code> for notification popup identified by given <code>notificationId</code>.
     *
     * @param notificationId the id of popup message notification.
     *
     * @return new "on deleted" <code>Intent</code> for given <code>notificationId</code>.
     */
    public static Intent createDeleteIntent(int notificationId) {
        Intent intent = new Intent();
        intent.setPackage(aTalkApp.getInstance().getPackageName());
        intent.setAction(ACTION_POPUP_CLEAR);
        intent.putExtra(EXTRA_NOTIFICATION_ID, notificationId);
        return intent;
    }

    /**
     * Creates "on deleted" <code>Intent</code> for notification popup identified by given <code>notificationId</code>.
     *
     * @param notificationId the id of popup message notification.
     *
     * @return new "on deleted" <code>Intent</code> for given <code>notificationId</code>.
     */
    public static Intent createReplyIntent(int notificationId) {
        Intent intent = new Intent();
        intent.setPackage(aTalkApp.getInstance().getPackageName());
        intent.setAction(ACTION_REPLY_TO);
        intent.putExtra(EXTRA_NOTIFICATION_ID, notificationId);
        return intent;
    }

    /**
     * Creates "on deleted" <code>Intent</code> for notification popup identified by given <code>notificationId</code>.
     *
     * @param notificationId the id of popup message notification.
     *
     * @return new "on deleted" <code>Intent</code> for given <code>notificationId</code>.
     */
    public static Intent createMarkAsReadIntent(int notificationId) {
        Intent intent = new Intent();
        intent.setPackage(aTalkApp.getInstance().getPackageName());
        intent.setAction(ACTION_MARK_AS_READ);
        intent.putExtra(EXTRA_NOTIFICATION_ID, notificationId);
        return intent;
    }

    /**
     * Creates "on deleted" <code>Intent</code> for notification popup identified by given <code>notificationId</code>.
     *
     * @param notificationId the id of popup message notification.
     *
     * @return new "on deleted" <code>Intent</code> for given <code>notificationId</code>.
     */
    public static Intent createSnoozeIntent(int notificationId) {
        Intent intent = new Intent();
        intent.setPackage(aTalkApp.getInstance().getPackageName());
        intent.setAction(ACTION_SNOOZE);
        intent.putExtra(EXTRA_NOTIFICATION_ID, notificationId);
        return intent;
    }

    /**
     * Creates call dismiss <code>Intent</code> for notification popup identified by given <code>notificationId</code>.
     *
     * @param notificationId the id of popup message notification.
     *
     * @return new dismiss <code>Intent</code> for given <code>notificationId</code>.
     */
    public static Intent createCallDismiss(int notificationId) {
        Intent intent = new Intent();
        intent.setPackage(aTalkApp.getInstance().getPackageName());
        intent.setAction(ACTION_CALL_DISMISS);
        intent.putExtra(EXTRA_NOTIFICATION_ID, notificationId);
        return intent;
    }
}
