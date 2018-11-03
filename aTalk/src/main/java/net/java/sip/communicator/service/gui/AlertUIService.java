/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license.
 * See terms of license at gnu.org.
 */
package net.java.sip.communicator.service.gui;

/**
 * The <tt>AlertUIService</tt> is a service that allows to show error messages and warnings.
 *
 * @author Yana Stamcheva
 * @author Eng Chong Meng
 */
public interface AlertUIService
{
    /**
     * Indicates that the OK button is pressed.
     */
    static final int OK_RETURN_CODE = 0;

    /**
     * Indicates that the Cancel button is pressed.
     */
    static final int CANCEL_RETURN_CODE = 1;

    /**
     * Indicates that the OK button is pressed and the Don't ask check box is checked.
     */
    static final int OK_DONT_ASK_CODE = 2;

    /**
     * The type of the alert dialog, which displays a warning instead of an error.
     */
    static final int WARNING = 1;

    /**
     * The type of alert dialog which displays a warning instead of an error.
     */
    static final int ERROR = 0;

    /**
     * Shows an alert dialog with the given title and message.
     *
     * @param title the title of the dialog
     * @param message the message to be displayed
     */
    void showAlertDialog(String title, String message);

    /**
     * Shows an alert dialog with the given title message and exception corresponding to the error.
     *
     * @param title the title of the dialog
     * @param message the message to be displayed
     * @param e the exception corresponding to the error
     */
    void showAlertDialog(String title, String message, Throwable e);

    /**
     * Shows an alert dialog with the given title, message and type of message.
     *
     * @param title the title of the error dialog
     * @param message the message to be displayed
     * @param type the dialog type (warning or error)
     */
    void showAlertDialog(String title, String message, int type);

    /**
     * Shows an notification pop-up which can be clicked. An error dialog is
     * shown when the notification is clicked.
     *
     * @param title the title of the error dialog and the notification pop-up
     * @param message the message to be displayed in the error dialog and the pop-up
     */
    void showAlertPopup(String title, String message);

    /**
     * Shows an notification pop-up which can be clicked. An error dialog is
     * shown when the notification is clicked.
     *
     * @param title the title of the error dialog and the notification pop-up
     * @param message the message to be displayed in the error dialog and the pop-up
     * @param e the exception that can be shown in the error dialog
     */
    void showAlertPopup(String title, String message,
            Throwable e);

    /**
     * Shows an notification pop-up which can be clicked. An error dialog is
     * shown when the notification is clicked.
     *
     * @param title the title of the notification pop-up
     * @param message the message of the pop-up
     * @param errorDialogTitle the title of the error dialog
     * @param errorDialogMessage the message of the error dialog
     */
    void showAlertPopup(String title, String message, String errorDialogTitle, String errorDialogMessage);

    /**
     * Shows an notification pop-up which can be clicked. An error dialog is
     * shown when the notification is clicked.
     *
     * @param title the title of the notification pop-up
     * @param message the message of the pop-up
     * @param errorDialogTitle the title of the error dialog
     * @param errorDialogMessage the message of the error dialog
     * @param e the exception that can be shown in the error dialog
     */
    void showAlertPopup(String title, String message, String errorDialogTitle, String errorDialogMessage, Throwable e);

    /**
     * Releases the resources acquired by this instance throughout its lifetime and removes the listeners.
     */
    void dispose();
}
