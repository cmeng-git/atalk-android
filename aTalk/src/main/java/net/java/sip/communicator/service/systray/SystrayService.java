/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license.
 * See terms of license at gnu.org.
 */
package net.java.sip.communicator.service.systray;

import net.java.sip.communicator.service.systray.event.*;

/**
 * The <tt>SystrayService</tt> manages the system tray icon, menu and messages.
 * It is meant to be used by all bundles that want to show a system tray message.
 *
 * @author Yana Stamcheva
 * @author Eng Chong Meng
 */
public interface SystrayService
{
    /**
     * Message type corresponding to an error message.
     */
    int ERROR_MESSAGE_TYPE = 0;

    /**
     * Message type corresponding to an information message.
     */
    int INFORMATION_MESSAGE_TYPE = 1;

    /**
     * Message type corresponding to a warning message.
     */
    int WARNING_MESSAGE_TYPE = 2;

    /**
     * Message type corresponding to a missed call message.
     */
    int MISSED_CALL_MESSAGE_TYPE = 3;

    /**
     * Message type corresponding to a Jingle <session-initiate/> call message.
     */
    int JINGLE_INCOMING_CALL = 4;

    /**
     * Message type corresponding to a JingleMessage <propose/> call message.
     */
    int JINGLE_MESSAGE_PROPOSE = 5;

    /**
     * Message type is not accessible.
     */
    int NONE_MESSAGE_TYPE = -1;

    /**
     * Image type corresponding to the jitsi icon
     */
    int SC_IMG_TYPE = 0;

    /**
     * Image type corresponding to the jitsi offline icon
     */
    int SC_IMG_OFFLINE_TYPE = 2;

    /**
     * Image type corresponding to the jitsi away icon
     */
    int SC_IMG_AWAY_TYPE = 3;

    /**
     * Image type corresponding to the jitsi free for chat icon
     */
    int SC_IMG_FFC_TYPE = 4;

    /**
     * Image type corresponding to the jitsi do not disturb icon
     */
    int SC_IMG_DND_TYPE = 5;

    /**
     * Image type corresponding to the jitsi away icon
     */
    int SC_IMG_EXTENDED_AWAY_TYPE = 6;

    /**
     * Image type corresponding to the envelope icon
     */
    int ENVELOPE_IMG_TYPE = 1;

    /**
     * Shows the given <tt>PopupMessage</tt>
     *
     * @param popupMessage the message to show
     */
    void showPopupMessage(PopupMessage popupMessage);

    /**
     * Adds a listener for <tt>SystrayPopupMessageEvent</tt>s posted when user
     * clicks on the system tray popup message.
     *
     * @param listener the listener to add
     */
    void addPopupMessageListener(SystrayPopupMessageListener listener);

    /**
     * Removes a listener previously added with <tt>addPopupMessageListener</tt>.
     *
     * @param listener the listener to remove
     */
    void removePopupMessageListener(SystrayPopupMessageListener listener);

    /**
     * Set the handler which will be used for popup message
     * @param popupHandler the handler to use
     * @return the previously used popup handler
     */
    PopupMessageHandler setActivePopupMessageHandler(PopupMessageHandler popupHandler);

    /**
     * Get the handler currently used by the systray service for popup message
     * @return the handler used by the systray service
     */
    PopupMessageHandler getActivePopupMessageHandler();

    /**
     * Sets a new icon to the systray.
     *
     * @param imageType the type of the image to set
     */
    void setSystrayIcon(int imageType);

    /**
     * Selects the best available popup message handler
     */
    void selectBestPopupMessageHandler();
}
