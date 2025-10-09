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

import androidx.activity.OnBackPressedCallback;

import org.atalk.android.BaseActivity;
import org.atalk.android.R;
import org.atalk.android.aTalkApp;
import org.atalk.android.gui.util.ViewUtil;

/**
 * This dialog is displayed in order to prepare the authorization request that has to be sent to
 * the user we want to include in our contact list.
 *
 * @author Pawel Domas
 * @author Eng Chong Meng
 */
public class RequestAuthorizationDialog extends BaseActivity {
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
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.request_authorization);
        long requestId = getIntent().getLongExtra(EXTRA_REQUEST_ID, -1);
        if (requestId == -1)
            throw new IllegalArgumentException();

        this.request = AuthorizationHandlerImpl.getRequest(requestId);
        String userID = request.contact.getProtocolProvider().getAccountID().getUserID();
        String contactId = request.contact.getAddress();

        ViewUtil.setTextViewValue(getContentView(), R.id.requestInfo,
                getString(R.string.request_authorization_prompt, userID, contactId));

        // Prevents from closing the dialog on outside touch
        setFinishOnTouchOutside(false);
        getOnBackPressedDispatcher().addCallback(backPressedCallback);
    }

    /*
     * Do not allow backKey to terminate dialog.
     */
    OnBackPressedCallback backPressedCallback = new OnBackPressedCallback(true) {
        @Override
        public void handleOnBackPressed() {
            // Block the back key action to end dialog.
        }
    };

    /**
     * Method fired when the request button is clicked.
     *
     * @param v the button's <code>View</code>
     */
    public void onRequestClicked(View v) {
        String requestText = ViewUtil.getTextViewValue(getContentView(), R.id.requestText);
        request.submit(requestText);
        discard = false;
        finish();
    }

    /**
     * Method fired when the cancel button is clicked.
     *
     * @param v the button's <code>View</code>
     */
    public void onCancelClicked(View v) {
        discard = true;
        finish();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void onDestroy() {
        if (discard)
            request.discard();
        super.onDestroy();
    }

    /**
     * Creates the <code>Intent</code> to start <code>RequestAuthorizationDialog</code> parametrized with
     * given <code>requestId</code>.
     *
     * @param requestId the id of authentication request.
     *
     * @return <code>Intent</code> that start <code>RequestAuthorizationDialog</code> parametrized with given request id.
     */
    public static Intent getRequestAuthDialogIntent(long requestId) {
        Intent intent = new Intent(aTalkApp.getInstance(), RequestAuthorizationDialog.class);
        intent.putExtra(EXTRA_REQUEST_ID, requestId);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        return intent;
    }
}
