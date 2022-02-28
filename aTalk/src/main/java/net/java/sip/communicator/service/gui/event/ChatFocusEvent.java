/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license.
 * See terms of license at gnu.org.
 */
package net.java.sip.communicator.service.gui.event;

import net.java.sip.communicator.service.gui.Chat;

import java.util.EventObject;

/**
 * The <code>ChatFocusEvent</code> indicates that a <code>Chat</code> has gained or lost the current focus.
 *
 * @author Yana Stamcheva
 * @author Eng Chong Meng
 */
public class ChatFocusEvent extends EventObject
{
    /**
     * Serial version UID.
     */
    private static final long serialVersionUID = 0L;

    /**
     * ID of the event.
     */
    private int eventID;

    /**
     * Indicates that the ChatFocusEvent instance was triggered by <code>Chat</code> gaining the focus.
     */
    public static final int FOCUS_GAINED = 1;

    /**
     * Indicates that the ChatFocusEvent instance was triggered by <code>Chat</code> losing the focus.
     */
    public static final int FOCUS_LOST = 2;

    /**
     * Creates a new <code>ChatFocusEvent</code> according to the specified parameters.
     *
     * @param source The <code>Chat</code> that triggers the event.
     * @param eventID one of the FOCUS_XXX static fields indicating the nature of the event.
     */
    public ChatFocusEvent(Object source, int eventID)
    {
        super(source);
        this.eventID = eventID;
    }

    /**
     * Returns an event id specifying what is the type of this event (FOCUS_GAINED or FOCUS_LOST)
     *
     * @return one of the REGISTRATION_XXX int fields of this class.
     */
    public int getEventID()
    {
        return eventID;
    }

    /**
     * Returns the <code>Chat</code> object that corresponds to this event.
     *
     * @return the <code>Chat</code> object that corresponds to this event
     */
    public Chat getChat()
    {
        return (Chat) source;
    }
}
