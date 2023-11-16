/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license.
 * See terms of license at gnu.org.
 */
package net.java.sip.communicator.service.systray;

import net.java.sip.communicator.service.systray.event.SystrayPopupMessageEvent;
import net.java.sip.communicator.service.systray.event.SystrayPopupMessageListener;

import java.util.ArrayList;
import java.util.List;
import java.util.Vector;

/**
 * Abstract base implementation of <code>PopupMessageHandler</code> which
 * facilitates the full implementation of the interface.
 *
 * @author Lubomir Marinov
 */
public abstract class AbstractPopupMessageHandler implements PopupMessageHandler
{

    /**
     * The list of <code>SystrayPopupMessageListener</code>s registered with this
     * instance.
     */
    private final List<SystrayPopupMessageListener> popupMessageListeners = new Vector<>();

    /**
     * Adds a <code>SystrayPopupMessageListener</code> to this instance so that it
     * receives <code>SystrayPopupMessageEvent</code>s.
     *
     * @param listener the <code>SystrayPopupMessageListener</code> to be added to this instance
     * @see PopupMessageHandler#addPopupMessageListener(SystrayPopupMessageListener)
     */
    public void addPopupMessageListener(SystrayPopupMessageListener listener)
    {
        synchronized (popupMessageListeners) {
            if (!popupMessageListeners.contains(listener))
                popupMessageListeners.add(listener);
        }
    }

    /**
     * Notifies the <code>SystrayPopupMessageListener</code>s registered with this
     * instance that a <code>SystrayPopupMessageEvent</code> has occurred.
     *
     * @param evt the <code>SystrayPopupMessageEvent</code> to be fired to the
     * <code>SystrayPopupMessageListener</code>s registered with this instance
     */
    protected void firePopupMessageClicked(SystrayPopupMessageEvent evt)
    {
        List<SystrayPopupMessageListener> listeners;
        synchronized (popupMessageListeners) {
            listeners = new ArrayList<>(popupMessageListeners);
        }
        for (SystrayPopupMessageListener listener : listeners)
            listener.popupMessageClicked(evt);
    }

    /**
     * Removes a <code>SystrayPopupMessageListener</code> from this instance so that
     * it no longer receives <code>SystrayPopupMessageEvent</code>s.
     *
     * @param listener the <code>SystrayPopupMessageListener</code> to be removed from this instance
     * @see PopupMessageHandler#removePopupMessageListener(SystrayPopupMessageListener)
     */
    public void removePopupMessageListener(SystrayPopupMessageListener listener)
    {
        synchronized (popupMessageListeners) {
            popupMessageListeners.remove(listener);
        }
    }
}
