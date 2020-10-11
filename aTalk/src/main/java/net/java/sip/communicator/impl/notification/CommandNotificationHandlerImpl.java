/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package net.java.sip.communicator.impl.notification;

import net.java.sip.communicator.service.notification.*;

import org.apache.commons.lang3.StringUtils;

import java.io.IOException;
import java.util.Map;

import timber.log.Timber;

/**
 * An implementation of the <tt>CommandNotificationHandler</tt> interface.
 *
 * @author Yana Stamcheva
 * @author Eng Chong Meng
 */
public class CommandNotificationHandlerImpl implements CommandNotificationHandler
{
    /**
     * {@inheritDoc}
     */
    public String getActionType()
    {
        return NotificationAction.ACTION_COMMAND;
    }

    /**
     * Executes the command, given by the <tt>descriptor</tt> of a specific <tt>CommandNotificationAction</tt>.
     *
     * @param action the action to act upon.
     * @param cmdargs command-line arguments.
     */
    public void execute(CommandNotificationAction action, Map<String, String> cmdargs)
    {
        String actionDescriptor = action.getDescriptor();

        if (StringUtils.isBlank(actionDescriptor))
            return;

        if (cmdargs != null) {
            for (Map.Entry<String, String> cmdarg : cmdargs.entrySet()) {
                actionDescriptor = actionDescriptor.replace("${" + cmdarg.getKey() + "}", cmdarg.getValue());
            }
        }

        try {
            Runtime.getRuntime().exec(actionDescriptor);
        } catch (IOException ioe) {
            Timber.e(ioe, "Failed to execute the following command: %s", action.getDescriptor());
        }
    }
}
