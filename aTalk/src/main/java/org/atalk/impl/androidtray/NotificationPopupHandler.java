/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.atalk.impl.androidtray;

import android.app.PendingIntent;
import android.support.v4.app.NotificationCompat;

import net.java.sip.communicator.service.systray.AbstractPopupMessageHandler;
import net.java.sip.communicator.service.systray.PopupMessage;
import net.java.sip.communicator.service.systray.event.SystrayPopupMessageEvent;

import org.atalk.android.R;
import org.atalk.android.aTalkApp;
import org.atalk.android.gui.chat.ChatPanel;
import org.atalk.android.gui.chat.ChatSessionManager;
import org.atalk.android.gui.util.AndroidUtils;
import org.atalk.service.osgi.OSGiService;

import java.util.*;

import timber.log.Timber;

/**
 * Displays popup messages as Android status bar notifications.
 *
 * @author Pawel Domas
 * @author Eng Chong Meng
 */
public class NotificationPopupHandler extends AbstractPopupMessageHandler
        implements ChatSessionManager.CurrentChatListener
{
    /**
     * Map of currently displayed <tt>AndroidPopup</tt>s. Value is removed when
     * corresponding notification is clicked or discarded.
     */
    private Map<Integer, AndroidPopup> notificationMap = new HashMap<>();

    /**
     * Creates new instance of <tt>NotificationPopupHandler</tt>. Registers as active chat listener.
     */
    public NotificationPopupHandler()
    {
        ChatSessionManager.addCurrentChatListener(this);
    }

    /**
     * {@inheritDoc}
     */
    public void showPopupMessage(PopupMessage popupMessage)
    {
        AndroidPopup newPopup = null;
        // Check for existing notifications
        for (AndroidPopup popup : notificationMap.values()) {
            AndroidPopup merge = popup.tryMerge(popupMessage);
            if (merge != null) {
                newPopup = merge;
                break;
            }
        }
        if (newPopup == null) {
            newPopup = AndroidPopup.createNew(this, popupMessage);
        }
        NotificationCompat.Builder builder = newPopup.buildNotification();
        int nId = newPopup.getId();

        // Registers click intent
        builder.setContentIntent(newPopup.constructIntent());

        // Registers delete intent
        builder.setDeleteIntent(PendingIntent.getBroadcast(aTalkApp.getGlobalContext(), nId,
                // Must be unique for each, so use notification id as request code, get pending intent
                PopupClickReceiver.createDeleteIntent(nId), PendingIntent.FLAG_UPDATE_CURRENT));

        // post the notification
        aTalkApp.getNotificationManager().notify(nId, builder.build());
        newPopup.onPost();

        // caches the notification until clicked or cleared
        notificationMap.put(nId, newPopup);
    }

    /**
     * Fires <tt>SystrayPopupMessageEvent</tt> for clicked notification.
     *
     * @param notificationId the id of clicked notification.
     */
    void fireNotificationClicked(int notificationId)
    {
        Timber.d("Notification clicked: %s", notificationId);

        AndroidPopup popup = notificationMap.get(notificationId);
        if (popup == null) {
            Timber.e("No valid notification exists for %s", notificationId);
            return;
        }

        PopupMessage msg = popup.getPopupMessage();
        if (msg == null) {
            Timber.e("No popup message found for %s", notificationId);
            return;
        }

        firePopupMessageClicked(new SystrayPopupMessageEvent(msg, msg.getTag()));
        removeNotification(notificationId);
    }

    /**
     * Removes notification from the map.
     *
     * @param notificationId the id of clicked notification.
     */
    void notificationDiscarded(int notificationId)
    {
        removeNotification(notificationId);
    }

    /**
     * Removes notification for given <tt>notificationId</tt> and performs necessary cleanup.
     *
     * @param notificationId the id of notification to remove.
     */
    private void removeNotification(int notificationId)
    {
        if (notificationId == OSGiService.getGeneralNotificationId()) {
            AndroidUtils.clearGeneralNotification(aTalkApp.getGlobalContext());
        }
        AndroidPopup popup = notificationMap.get(notificationId);
        if (popup == null) {
            Timber.w("Notification for id: %s already removed", notificationId);
            return;
        }

        Timber.d("Removing notification: %s", notificationId);
        popup.removeNotification();
        notificationMap.remove(notificationId);
    }

    /**
     * Removes all currently registered notifications from the status bar.
     */
    void dispose()
    {
        // Removes active chat listener
        ChatSessionManager.removeCurrentChatListener(this);

        for (AndroidPopup popup : notificationMap.values()) {
            popup.removeNotification();
        }
        notificationMap.clear();
    }

    /**
     * {@inheritDoc} <br/>
     * This implementations scores 3: <br/>
     * +1 detecting clicks <br/>
     * +1 being able to match a click to a message <br/>
     * +1 using a native popup mechanism <br/>
     */
    public int getPreferenceIndex()
    {
        return 3;
    }

    @Override
    public String toString()
    {
        return aTalkApp.getResString(R.string.impl_popup_status_bar);
    }

    /**
     * Method called by <tt>AndroidPopup</tt> to signal the timeout.
     *
     * @param popup <tt>AndroidPopup</tt> on which timeout event has occurred.
     */
    public void onTimeout(AndroidPopup popup)
    {
        removeNotification(popup.getId());
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void onCurrentChatChanged(String chatId)
    {
        // Clears chat notification related to currently opened chat for incomingMessage & incomingFile
        ChatPanel openChat = ChatSessionManager.getActiveChat(chatId);

        if (openChat == null)
            return;

        List<AndroidPopup> chatPopups = new ArrayList<>();
        for (AndroidPopup popup : notificationMap.values()) {
            if (popup.isChatRelated(openChat)) {
                chatPopups.add(popup);
                break;
            }
        }
        for (AndroidPopup chatPopup : chatPopups) {
            removeNotification(chatPopup.getId());
        }
    }
}
