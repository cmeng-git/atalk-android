/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license.
 * See terms of license at gnu.org.
 */
package net.java.sip.communicator.service.gui;

/**
 * Creates and show authentication window, normally to fill in username and password.
 * @author Damian Minkov
 * @author Eng Chong Meng
 */
public interface AuthenticationWindowService
{
    /**
     * Creates an instance of the <code>AuthenticationWindow</code> implementation.
     *
     * @param server the server name
     * @param isUserNameEditable indicates if the user name is editable
     * @param icon the icon to display on the left of the authentication window
     * @param windowTitle customized window title
     * @param windowText customized window text
     * @param usernameLabelText customized username field label text
     * @param passwordLabelText customized password field label text
     * @param errorMessage an error message if this dialog is shown to indicate
     * the user that something went wrong
     * @param signupLink an URL that allows the user to sign up
     */
    AuthenticationWindow create(String userName,
                                    char[] password,
                                    String server,
                                    boolean isUserNameEditable,
                                    boolean isRememberPassword,
                                    Object icon,
                                    String windowTitle,
                                    String windowText,
                                    String usernameLabelText,
                                    String passwordLabelText,
                                    String errorMessage,
                                    String signupLink);

    /**
     * The window interface used by implementers.
     */
    interface AuthenticationWindow
    {
        /**
         * Shows window implementation.
         *
         * @param isVisible specifies whether we should be showing or hiding the window.
         */
        void setVisible(final boolean isVisible);

        /**
         * Indicates if this window has been canceled.
         *
         * @return <code>true</code> if this window has been canceled, <code>false</code> - otherwise.
         */
        boolean isCanceled();

        /**
         * Returns the user name entered by the user or previously set if the user name is not editable.
         *
         * @return the user name.
         */
        String getUserName();

        /**
         * Returns the password entered by the user.
         *
         * @return the password.
         */
        char[] getPassword();

        /**
         * Indicates if the password should be remembered.
         *
         * @return <code>true</code> if the password should be remembered, <code>false</code> - otherwise.
         */
        boolean isRememberPassword();

        /**
         * Shows or hides the "save password" checkbox.
         * @param allow the checkbox is shown when allow is <code>true</code>
         */
        void setAllowSavePassword(boolean allow);
    }
}
