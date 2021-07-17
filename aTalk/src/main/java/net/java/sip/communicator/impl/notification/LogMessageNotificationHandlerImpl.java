/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package net.java.sip.communicator.impl.notification;

import net.java.sip.communicator.service.notification.*;

import org.atalk.android.plugin.timberlog.TimberLog;

import timber.log.Timber;

import static net.java.sip.communicator.service.notification.LogMessageNotificationAction.ERROR_LOG_TYPE;
import static net.java.sip.communicator.service.notification.LogMessageNotificationAction.INFO_LOG_TYPE;
import static net.java.sip.communicator.service.notification.LogMessageNotificationAction.TRACE_LOG_TYPE;

/**
 * An implementation of the <tt>LogMessageNotificationHandler</tt> interface.
 *
 * @author Yana Stamcheva
 * @author Eng Chong Meng
 */
public class LogMessageNotificationHandlerImpl implements LogMessageNotificationHandler
{
    /**
     * {@inheritDoc}
     */
    public String getActionType()
    {
        return NotificationAction.ACTION_LOG_MESSAGE;
    }

    /**
     * Logs a message through the sip communicator Logger.
     *
     * @param action the action to act upon
     * @param message the message coming from the event
     */
    public void logMessage(LogMessageNotificationAction action, String message)
    {
        if (action.getLogType().equals(ERROR_LOG_TYPE))
            Timber.e("%s", message);
        else if (action.getLogType().equals(INFO_LOG_TYPE))
            Timber.i("%s", message);
        else if (action.getLogType().equals(TRACE_LOG_TYPE))
            Timber.log(TimberLog.FINER, "%s", message);
    }
}
