/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.atalk.impl.androidauthwindow;

import android.content.Context;
import android.content.Intent;
import android.os.Looper;

import net.java.sip.communicator.service.gui.AuthenticationWindowService;

import org.atalk.android.aTalkApp;

import timber.log.Timber;

/**
 * Android <code>AuthenticationWindow</code> impl. Serves as a static data model for <code>AuthWindowActivity</code>. Is
 * identified by the request id passed as an intent extra. All requests are mapped in <code>AuthWindowServiceImpl</code>.
 *
 * @author Pawel Domas
 * @author Eng Chong Meng
 */
public class AuthWindowImpl implements AuthenticationWindowService.AuthenticationWindow
{
    /**
     * Lock object used to stop the thread until credentials are obtained.
     */
    private final Object notifyLock = new Object();

    private String userName;

    private char[] password;

    private final String server;

    private final boolean userNameEditable;

    private boolean rememberPassword;

    private final String windowTitle;

    private final String windowText;

    private final String userNameLabel;

    private final String passwordLabel;

    private final long requestId;

    private boolean allowSavePassword = true;

    private boolean isCanceled;

    /**
     * Creates new instance of <code>AuthWindowImpl</code>
     *
     * @param requestId request identifier managed by <code>AuthWindowServiceImpl</code>
     * @param userName pre entered username
     * @param password pre entered password
     * @param server name of the server that requested authentication
     * @param rememberPassword indicates if store password filed should be checked by default
     * @param windowTitle the title for authentication window
     * @param windowText the message text for authentication window
     * @param usernameLabel label for login field
     * @param passwordLabel label for password field
     */
    public AuthWindowImpl(long requestId, String userName, char[] password, String server, boolean userNameEditable,
            boolean rememberPassword, String windowTitle, String windowText, String usernameLabel, String passwordLabel)
    {
        this.requestId = requestId;
        this.userName = userName;
        this.password = password;
        this.server = server;
        this.userNameEditable = userNameEditable;
        this.rememberPassword = rememberPassword;
        this.windowTitle = windowTitle;
        this.windowText = windowText;
        this.userNameLabel = usernameLabel;
        this.passwordLabel = passwordLabel;
    }

    /**
     * Shows AuthWindow password request dialog.
     *
     * This function MUST NOT be called from main thread. Otherwise
     * synchronized (notifyLock){} will cause whole UI to freeze.
     *
     * @param isVisible specifies whether we should be showing or hiding the window.
     */
    public void setVisible(final boolean isVisible)
    {
        if (!isVisible)
            return;

        if (Looper.myLooper() == Looper.getMainLooper()) {
            Timber.e("AuthWindow cannot be called from main thread!");
            return;
        }

        Context ctx = aTalkApp.getInstance();
        Intent authWindowIntent = new Intent(ctx, AuthWindowActivity.class);
        authWindowIntent.putExtra(AuthWindowActivity.REQUEST_ID_EXTRA, requestId);
        authWindowIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        ctx.startActivity(authWindowIntent);

        // This will freeze UI if allow to execute from main thread
        synchronized (notifyLock) {
            try {
                notifyLock.wait();
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
        }
    }

    /**
     * Should be called when authentication window is closed. Releases thread that waits for credentials.
     */
    void windowClosed()
    {
        synchronized (notifyLock) {
            notifyLock.notifyAll();
            AuthWindowServiceImpl.clearRequest(requestId);
        }
    }

    /**
     * Indicates if this window has been canceled.
     *
     * @return <code>true</code> if this window has been canceled, <code>false</code> - otherwise.
     */
    public boolean isCanceled()
    {
        return this.isCanceled;
    }

    /**
     * Sets dialog canceled flag.
     *
     * @param canceled the canceled status to set.
     */
    void setCanceled(boolean canceled)
    {
        this.isCanceled = canceled;
    }

    /**
     * Returns the user name entered by the user or previously set if the user name is not editable.
     *
     * @return the user name.
     */
    public String getUserName()
    {
        return userName;
    }

    /**
     * Returns the password entered by the user.
     *
     * @return the password.
     */
    public char[] getPassword()
    {
        return password;
    }

    /**
     * Indicates if the password should be remembered.
     *
     * @return <code>true</code> if the password should be remembered, <code>false</code> - otherwise.
     */
    public boolean isRememberPassword()
    {
        return rememberPassword;
    }

    /**
     * Sets the store password flag.
     *
     * @param storePassword <code>true</code> if the password should be stored.
     */
    void setRememberPassword(boolean storePassword)
    {
        this.rememberPassword = storePassword;
    }

    /**
     * Returns <code>true</code> if username filed is editable.
     *
     * @return <code>true</code> if username filed is editable.
     */
    boolean isUserNameEditable()
    {
        return userNameEditable;
    }

    /**
     * Shows or hides the "save password" checkbox.
     *
     * @param allow the checkbox is shown when allow is <code>true</code>
     */
    public void setAllowSavePassword(boolean allow)
    {
        this.allowSavePassword = allow;
    }

    /**
     * Returns <code>true</code> if it's allowed to save the password.
     *
     * @return <code>true</code> if it's allowed to save the password.
     */
    boolean isAllowSavePassword()
    {
        return allowSavePassword;
    }

    /**
     * Returns authentication window message text.
     *
     * @return authentication window message text.
     */
    String getWindowText()
    {
        return windowText;
    }

    /**
     * Returns username description text.
     *
     * @return username description text.
     */
    String getUsernameLabel()
    {
        return userNameLabel;
    }

    /**
     * Returns the password label.
     *
     * @return the password label.
     */
    String getPasswordLabel()
    {
        return passwordLabel;
    }

    /**
     * Sets the username entered by the user.
     *
     * @param username the user name entered by the user.
     */
    void setUsername(String username)
    {
        this.userName = username;
    }

    /**
     * Sets the password entered by the user.
     *
     * @param password the password entered by the user.
     */
    void setPassword(String password)
    {
        this.password = password.toCharArray();
    }

    /**
     * Returns the window title that should be used by authentication dialog.
     *
     * @return the window title that should be used by authentication dialog.
     */
    String getWindowTitle()
    {
        return windowTitle;
    }

    /**
     * Returns name of the server that requested authentication.
     *
     * @return name of the server that requested authentication.
     */
    String getServer()
    {
        return server;
    }
}
