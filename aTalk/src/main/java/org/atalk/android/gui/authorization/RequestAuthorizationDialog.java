/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.atalk.android.gui.authorization;

import android.content.Intent;
import android.os.Bundle;
import android.view.KeyEvent;
import android.view.View;

import org.atalk.android.R;
import org.atalk.android.aTalkApp;
import org.atalk.android.gui.util.ViewUtil;
import org.atalk.service.osgi.OSGiActivity;

/**
 * This dialog is displayed in order to prepare the authorization request that has to be sent to
 * the user we want to include in our contact list.
 *
 * @author Pawel Domas
 * @author Eng Chong Meng
 */
public class RequestAuthorizationDialog extends OSGiActivity
{
    /**
     * Request identifier extra key.
     */
    private static final String EXTRA_REQUEST_ID = "request_id";

    /**
     * The request holder.
     */
    private AuthorizationHandlerImpl.AuthorizationRequestedHolder request;

    /**
     * Flag stores the discard state.
     */
    private boolean discard;

    /**
     * {@inheritDoc}
     */
    @Override
    protected void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.request_authorization);
        long requestId = getIntent().getLongExtra(EXTRA_REQUEST_ID, -1);
        if (requestId == -1)
            throw new IllegalArgumentException();

        this.request = AuthorizationHandlerImpl.getRequest(requestId);
        String userID = request.contact.getProtocolProvider().getAccountID().getUserID();
        String contactId = request.contact.getAddress();

        ViewUtil.setTextViewValue(getContentView(), R.id.requestInfo,
                getString(R.string.service_gui_REQUEST_AUTHORIZATION_MSG, userID, contactId));

        // Prevents from closing the dialog on outside touch
        setFinishOnTouchOutside(false);
    }

    @Override
    public boolean onKeyUp(int keyCode, KeyEvent event)
    {
        // Prevent Back Key from closing the dialog
        if (keyCode == KeyEvent.KEYCODE_BACK) {
            return true;
        }
        return super.onKeyUp(keyCode, event);
    }

    /**
     * Method fired when the request button is clicked.
     *
     * @param v the button's <tt>View</tt>
     */
    public void onRequestClicked(View v)
    {
        String requestText = ViewUtil.getTextViewValue(getContentView(), R.id.requestText);
        request.submit(requestText);
        discard = false;
        finish();
    }

    /**
     * Method fired when the cancel button is clicked.
     *
     * @param v the button's <tt>View</tt>
     */
    public void onCancelClicked(View v)
    {
        discard = true;
        finish();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void onDestroy()
    {
        if (discard)
            request.discard();
        super.onDestroy();
    }

    /**
     * Creates the <tt>Intent</tt> to start <tt>RequestAuthorizationDialog</tt> parametrized with
     * given <tt>requestId</tt>.
     *
     * @param requestId the id of authentication request.
     * @return <tt>Intent</tt> that start <tt>RequestAuthorizationDialog</tt> parametrized with given request id.
     */
    public static Intent getRequestAuthDialogIntent(long requestId)
    {
        Intent intent = new Intent(aTalkApp.getGlobalContext(), RequestAuthorizationDialog.class);
        intent.putExtra(EXTRA_REQUEST_ID, requestId);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        return intent;
    }
}
