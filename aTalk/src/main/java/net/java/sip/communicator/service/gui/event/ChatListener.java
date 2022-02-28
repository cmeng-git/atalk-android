/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license.
 * See terms of license at gnu.org.
 */
package net.java.sip.communicator.service.gui.event;

import net.java.sip.communicator.service.gui.Chat;

/**
 * Listens to the creation and closing of <code>Chat</code>s.
 *
 * @author Damian Johnson
 * @author Lyubomir Marinov
 * @author Eng Chong Meng
 */
public interface ChatListener
{
    /**
     * Notifies this instance that a <code>Chat</code> has been closed.
     *
     * @param chat the <code>Chat</code> which has been closed
     */
    void chatClosed(Chat chat);

    /**
     * Notifies this instance that a new <code>Chat</code> has been created.
     *
     * @param chat the new <code>Chat</code> which has been created
     */
    void chatCreated(Chat chat);
}
