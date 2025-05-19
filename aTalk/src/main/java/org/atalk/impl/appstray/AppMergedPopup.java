/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.atalk.impl.appstray;

import androidx.core.app.NotificationCompat;

import java.util.ArrayList;
import java.util.List;

import net.java.sip.communicator.service.systray.PopupMessage;

/**
 * Popup notification that consists of few merged previous popups.
 *
 * @author Pawel Domas
 * @author Eng Chong Meng
 */
public class AppMergedPopup extends AppPopup {
    /**
     * List of merged popups.
     */
    private final List<AppPopup> mergedPopups = new ArrayList<>();

    /**
     * Creates new instance of <code>AppMergedPopup</code> with given <code>AppPopup</code> as root.
     *
     * @param rootPopup root <code>AppPopup</code>.
     */
    AppMergedPopup(AppPopup rootPopup) {
        super(rootPopup.handler, rootPopup.popupMessage);
        this.nId = rootPopup.nId;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected AppPopup mergePopup(PopupMessage popupMessage) {
        // Timing out notifications are replaced - not valid in android
//        AppPopup replace = null;
//        if (mergedPopups.size() > 0) {
//            replace = mergedPopups.get(mergedPopups.size() - 1);
//            if (replace.timeoutHandler != null) {
//                replace.cancelTimeout();
//            }
//        }
//        if (replace != null) {
//            mergedPopups.set(mergedPopups.indexOf(replace), new AppPopup(handler, popupMessage));
//        }
//        else {
//            mergedPopups.add(new AppPopup(handler, popupMessage));
//        }
        mergedPopups.add(new AppPopup(handler, popupMessage));
        return this;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected String getMessage() {
        StringBuilder msg = new StringBuilder(super.getMessage());
        for (AppPopup popup : mergedPopups) {
            msg.append("\n").append(popup.getMessage());
        }
        return msg.toString();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    NotificationCompat.Builder buildNotification(int nId) {
        NotificationCompat.Builder builder = super.buildNotification(nId);
        // Set number of events
        builder.setNumber(mergedPopups.size() + 1);
        return builder;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void onBuildInboxStyle(NotificationCompat.InboxStyle inboxStyle) {
        super.onBuildInboxStyle(inboxStyle);
        for (AppPopup popup : mergedPopups) {
            inboxStyle.addLine(popup.getMessage());
        }
    }

    protected boolean displaySnoozeAction() {
        return mergedPopups.size() > 2;
    }
}
