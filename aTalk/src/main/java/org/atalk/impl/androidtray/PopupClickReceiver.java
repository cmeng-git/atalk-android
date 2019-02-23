/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.atalk.impl.androidtray;

import android.content.*;

import org.atalk.android.aTalkApp;

import timber.log.Timber;

/**
 * This <tt>BroadcastReceiver</tt> listens for <tt>PendingIntent</tt> coming from popup messages notifications. There
 * are two actions handled:<br/>
 * - <tt>POPUP_CLICK_ACTION</tt> fired when notification is clicked<br/>
 * - <tt>POPUP_CLEAR_ACTION</tt> fired when notification is cleared<br/>
 * Those events are passed to <tt>NotificationPopupHandler</tt> to take appropriate decisions.
 *
 * @author Pawel Domas
 * @author Eng Chong Meng
 */
public class PopupClickReceiver extends BroadcastReceiver
{
    /**
     * Popup clicked action name used for <tt>Intent</tt> handling by this <tt>BroadcastReceiver</tt>.
     */
    private static final String POPUP_CLICK_ACTION = "org.atalk.ui.popup_click";

    /**
     * Popup cleared action name used for <tt>Intent</tt> handling by this <tt>BroadcastReceiver</tt>
     */
    private static final String POPUP_CLEAR_ACTION = "org.atalk.ui.popup_discard";

    /**
     * <tt>Intent</tt> extra key that provides the notification id.
     */
    private static final String EXTRA_NOTIFICATION_ID = "notification_id";

    /**
     * <tt>NotificationPopupHandler</tt> that manages the popups.
     */
    private final NotificationPopupHandler notificationHandler;

    /**
     * Creates new instance of <tt>PopupClickReceiver</tt> bound to given <tt>notifcationHandler</tt>.
     *
     * @param notifcationHandler the <tt>NotificationPopupHandler</tt> that manages the popups.
     */
    public PopupClickReceiver(NotificationPopupHandler notifcationHandler)
    {
        this.notificationHandler = notifcationHandler;
    }

    /**
     * Registers this <tt>BroadcastReceiver</tt>.
     */
    void registerReceiver()
    {
        IntentFilter filter = new IntentFilter();
        filter.addAction(POPUP_CLICK_ACTION);
        filter.addAction(POPUP_CLEAR_ACTION);

        aTalkApp.getGlobalContext().registerReceiver(this, filter);
    }

    /**
     * Unregisters this <tt>BroadcastReceiver</tt>.
     */
    void unregisterReceiver()
    {
        aTalkApp.getGlobalContext().unregisterReceiver(this);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void onReceive(Context context, Intent intent)
    {
        int notificationId = intent.getIntExtra(EXTRA_NOTIFICATION_ID, -1);
        if (notificationId == -1) {
            Timber.w("Invalid notification id = -1");
            return;
        }

        String action = intent.getAction();
        if (action.equals(POPUP_CLICK_ACTION)) {
            notificationHandler.fireNotificationClicked(notificationId);
        }
        else if (action.equals(POPUP_CLEAR_ACTION)) {
            notificationHandler.notificationDiscarded(notificationId);
        }
        else {
            Timber.w("Unsupported action: %s", action);
        }
    }

    /**
     * Creates "on click" <tt>Intent</tt> for notification popup identified by given <tt>notificationId</tt>.
     *
     * @param notificationId the id of popup message notification.
     * @return new "on click" <tt>Intent</tt> for given <tt>notificationId</tt>.
     */
    public static Intent createIntent(int notificationId)
    {
        Intent intent = new Intent();
        intent.setAction(POPUP_CLICK_ACTION);
        intent.putExtra(EXTRA_NOTIFICATION_ID, notificationId);
        return intent;
    }

    /**
     * Creates "on deleted" <tt>Intent</tt> for notification popup identified by given <tt>notificationId</tt>.
     *
     * @param notificationId the id of popup message notification.
     * @return new "on deleted" <tt>Intent</tt> for given <tt>notificationId</tt>.
     */
    public static Intent createDeleteIntent(int notificationId)
    {
        Intent intent = new Intent();
        intent.setAction(POPUP_CLEAR_ACTION);
        intent.putExtra(EXTRA_NOTIFICATION_ID, notificationId);
        return intent;
    }
}
