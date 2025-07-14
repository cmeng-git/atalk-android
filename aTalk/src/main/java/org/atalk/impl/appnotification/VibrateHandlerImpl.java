/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.atalk.impl.appnotification;

import android.content.Context;
import android.os.Vibrator;

import net.java.sip.communicator.service.notification.NotificationAction;
import net.java.sip.communicator.service.notification.VibrateNotificationAction;
import net.java.sip.communicator.service.notification.VibrateNotificationHandler;

import org.atalk.android.aTalkApp;

/**
 * Android implementation of {@link VibrateNotificationHandler}.
 *
 * @author Pawel Domas
 */
public class VibrateHandlerImpl implements VibrateNotificationHandler {
    /**
     * The <code>Vibrator</code> if present on this device.
     */
    private final Vibrator mVibrator;

    /**
     * Creates new instance of <code>VibrateHandlerImpl</code>.
     */
    public VibrateHandlerImpl() {
        mVibrator = (Vibrator) aTalkApp.getInstance().getSystemService(Context.VIBRATOR_SERVICE);
    }

    /**
     * Returns <code>true</code> if the <code>Vibrator</code> service is present on this device.
     *
     * @return <code>true</code> if the <code>Vibrator</code> service is present on this device.
     */
    private boolean hasVibrator() {
        return (mVibrator != null) && mVibrator.hasVibrator();
    }

    /**
     * {@inheritDoc}
     */
    public void vibrate(VibrateNotificationAction action) {
        if (hasVibrator())
            mVibrator.vibrate(action.getPattern(), action.getRepeat());
    }

    /**
     * {@inheritDoc}
     */
    public void cancel() {
        if (hasVibrator())
            mVibrator.cancel();
    }

    /**
     * {@inheritDoc}
     */
    public String getActionType() {
        return NotificationAction.ACTION_VIBRATE;
    }
}
