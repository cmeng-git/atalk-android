/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package net.java.sip.communicator.service.notification;

/**
 * The <code>VibrateNotificationHandler</code> interface is meant to be implemented by the
 * notification bundle in order to provide handling of vibrate actions.
 *
 * @author Pawel Domas
 * @author Eng Chong Meng
 */
public interface VibrateNotificationHandler extends NotificationHandler
{

    /**
     * Perform vibration patter defined in given <code>vibrateAction</code>.
     *
     * @param vibrateAction the <code>VibrateNotificationAction</code> containing vibration pattern details.
     */
    void vibrate(VibrateNotificationAction vibrateAction);

    /**
     * Turn the vibrator off.
     */
    void cancel();

}
