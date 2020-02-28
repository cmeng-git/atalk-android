/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.atalk.impl.androidtray;

import net.java.sip.communicator.service.systray.PopupMessage;

import java.util.ArrayList;
import java.util.List;

import androidx.core.app.NotificationCompat;

/**
 * Popup notification that consists of few merged previous popups.
 *
 * @author Pawel Domas
 * @author Eng Chong Meng
 */
public class AndroidMergedPopup extends AndroidPopup
{
    /**
     * List of merged popups.
     */
    private List<AndroidPopup> mergedPopups = new ArrayList<>();

    /**
     * Creates new instance of <tt>AndroidMergedPopup</tt> with given <tt>AndroidPopup</tt> as root.
     *
     * @param rootPopup root <tt>AndroidPopup</tt>.
     */
    AndroidMergedPopup(AndroidPopup rootPopup)
    {
        super(rootPopup.handler, rootPopup.popupMessage);
        this.id = rootPopup.id;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected AndroidPopup mergePopup(PopupMessage popupMessage)
    {
        // Timing out notifications are replaced - not valid in android
//        AndroidPopup replace = null;
//        if (mergedPopups.size() > 0) {
//            replace = mergedPopups.get(mergedPopups.size() - 1);
//            if (replace.timeoutHandler != null) {
//                replace.cancelTimeout();
//            }
//        }
//        if (replace != null) {
//            mergedPopups.set(mergedPopups.indexOf(replace), new AndroidPopup(handler, popupMessage));
//        }
//        else {
//            mergedPopups.add(new AndroidPopup(handler, popupMessage));
//        }
        mergedPopups.add(new AndroidPopup(handler, popupMessage));
        return this;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected String getMessage()
    {
        StringBuilder msg = new StringBuilder(super.getMessage());
        for (AndroidPopup popup : mergedPopups) {
            msg.append("\n").append(popup.getMessage());
        }
        return msg.toString();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    NotificationCompat.Builder buildNotification(int nId)
    {
        NotificationCompat.Builder builder = super.buildNotification(nId);
        // Set number of events
        builder.setNumber(mergedPopups.size() + 1);
        return builder;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void onBuildInboxStyle(NotificationCompat.InboxStyle inboxStyle)
    {
        super.onBuildInboxStyle(inboxStyle);
        for (AndroidPopup popup : mergedPopups) {
            inboxStyle.addLine(popup.getMessage());
        }
    }

    protected boolean displaySnoozeAction()
    {
        return mergedPopups.size() > 2;
    }
}
