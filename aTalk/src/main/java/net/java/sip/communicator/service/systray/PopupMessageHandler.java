/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license.
 * See terms of license at gnu.org.
 */
package net.java.sip.communicator.service.systray;

import net.java.sip.communicator.service.systray.event.SystrayPopupMessageListener;

/**
 * The <code>PopupMessageHandler</code> role is to give different methods to display
 * <code>PopupMessage</code> and listen for events (user click) coming from that popup.
 *
 * @author Symphorien Wanko
 * @author Eng Chong Meng
 */
public interface PopupMessageHandler
{
    /**
     * Registers a listener to be informed of systray popup events.
     *
     * @param listener the listened which will be informed of systray popup events
     */
    void addPopupMessageListener(SystrayPopupMessageListener listener);

    /**
     * Removes a listener previously added with <code>addPopupMessageListener</code>.
     *
     * @param listener the listener to remove
     */
    void removePopupMessageListener(SystrayPopupMessageListener listener);

    /**
     * Shows the given <code>PopupMessage</code>. Any given <code>PopupMessage</code>
     * will provide a minimum of two values: a message title and a message body.
     * These two values are respectively available via
     * <code>PopupMessage#getMessageTitle()</code> and <code>PopupMessage#getMessage()</code>
     *
     * @param popupMessage the message to show
     */
    void showPopupMessage(PopupMessage popupMessage);

    /**
     * Returns a preference index, which indicates how many features the handler
     * implements. Implementer should calculate preference index by adding "1"
     * for each of the following features that the implementation supports:
     *
     * 1) showing images
     * 2) detecting clicks
     * 3) being able to match a click to a message
     * 4) using a native popup mechanism
     *
     * @return an integer representing preference index of this popup handler
     */
    int getPreferenceIndex();

    /**
     * Returns a readable localized description of this popup handler.
     *
     * @return a string describing this popup handler
     */
    @Override
    String toString();
}
