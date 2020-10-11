/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.atalk.impl.androidauthwindow;

import android.os.Bundle;
import android.view.View;

import org.atalk.android.R;
import org.atalk.android.aTalkApp;
import org.atalk.android.gui.util.ViewUtil;
import org.atalk.service.osgi.OSGiActivity;

/**
 * Activity controls authentication dialog for <tt>AuthenticationWindowService</tt>.
 *
 * @author Pawel Domas
 * @author Eng Chong Meng
 */
public class AuthWindowActivity extends OSGiActivity
{
    /**
     * Request id key.
     */
    static final String REQUEST_ID_EXTRA = "request_id";

    /**
     * Authentication window instance
     */
    private AuthWindowImpl mAuthWindow;
    private View contentView;

    /**
     * Changes will be stored only if flag is set to <tt>false</tt>.
     */
    private boolean cancelled = true;

    /**
     * {@inheritDoc}
     */
    @Override
    protected void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);
        long requestId = getIntent().getLongExtra(REQUEST_ID_EXTRA, -1);
        if (requestId == -1)
            throw new IllegalArgumentException();

        // Content view
        setContentView(R.layout.auth_window);
        contentView = findViewById(android.R.id.content);

        // Server name
        mAuthWindow = AuthWindowServiceImpl.getAuthWindow(requestId);
        String server = mAuthWindow.getServer();

        // Title
        String title = mAuthWindow.getWindowTitle();
        if (title == null) {
            title = getString(R.string.service_gui_AUTHENTICATION_WINDOW_TITLE, server);
        }
        setTitle(title);

        // Message
        String message = mAuthWindow.getWindowText();
        if (message == null) {
            message = getString(R.string.service_gui_AUTHENTICATION_REQUESTED_SERVER, server);
        }
        ViewUtil.setTextViewValue(contentView, R.id.text, message);

        // Username label and field
        if (mAuthWindow.getUsernameLabel() != null)
            ViewUtil.setTextViewValue(contentView, R.id.username_label, mAuthWindow.getUsernameLabel());

        if (mAuthWindow.getUserName() != null)
            ViewUtil.setTextViewValue(contentView, R.id.username, mAuthWindow.getUserName());

        ViewUtil.ensureEnabled(contentView, R.id.username, mAuthWindow.isUserNameEditable());

        // Password filed and label
        if (mAuthWindow.getPasswordLabel() != null)
            ViewUtil.setTextViewValue(contentView, R.id.password_label, mAuthWindow.getPasswordLabel());

        ViewUtil.setCompoundChecked(contentView, R.id.store_password, mAuthWindow.isRememberPassword());
        ViewUtil.ensureVisible(contentView, R.id.store_password, mAuthWindow.isAllowSavePassword());
    }

    /**
     * Fired when the ok button is clicked.
     *
     * @param v ok button's <tt>View</tt>
     */
    public void onOkClicked(View v)
    {
        String userName = ViewUtil.getTextViewValue(contentView, R.id.username);
        String password = ViewUtil.getTextViewValue(contentView, R.id.password);
        if ((userName == null) || (password == null)) {
            aTalkApp.showToastMessage(R.string.plugin_certconfig_INCOMPLETE);
        }
        else {
            cancelled = false;
            mAuthWindow.setUsername(userName);
            mAuthWindow.setPassword(password);
            mAuthWindow.setRememberPassword(ViewUtil.isCompoundChecked(contentView, R.id.store_password));
            finish();
        }
    }

    /**
     * Fired when the cancel button is clicked.
     *
     * @param v cancel button's <tt>View</tt>
     */
    public void onCancelClicked(View v)
    {
        cancelled = true;
        finish();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void onDestroy()
    {
        mAuthWindow.setCanceled(cancelled);
        mAuthWindow.windowClosed();
        super.onDestroy();
    }
}
