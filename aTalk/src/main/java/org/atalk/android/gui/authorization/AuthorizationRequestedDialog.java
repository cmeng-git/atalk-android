/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.atalk.android.gui.authorization;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.view.KeyEvent;
import android.view.View;
import android.widget.CompoundButton;
import android.widget.Spinner;

import net.java.sip.communicator.service.protocol.AuthorizationResponse;

import org.atalk.android.R;
import org.atalk.android.aTalkApp;
import org.atalk.android.gui.contactlist.MetaContactGroupAdapter;
import org.atalk.android.gui.util.ViewUtil;
import org.atalk.service.osgi.OSGiActivity;

/**
 * The dialog is displayed when someone wants to add us to his contact list and the authorization
 * is required.
 *
 * @author Pawel Domas
 * @author Eng Chong Meng
 */
public class AuthorizationRequestedDialog extends OSGiActivity
{
    /**
     * Request id managed by <tt>AuthorizationHandlerImpl</tt>.
     */
    private static final String EXTRA_REQUEST_ID = "request_id";

    /**
     * Request holder object.
     */
    AuthorizationHandlerImpl.AuthorizationRequestedHolder request;

    /**
     * Ignore request by default
     */
    AuthorizationResponse.AuthorizationResponseCode responseCode = AuthorizationResponse.IGNORE;

    /**
     * {@inheritDoc}
     */
    @Override
    protected void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.authorization_requested);

        long requestId = getIntent().getLongExtra(EXTRA_REQUEST_ID, -1);
        if (requestId == -1)
            throw new IllegalArgumentException();

        this.request = AuthorizationHandlerImpl.getRequest(requestId);
        String contactId = request.contact.getAddress();
        View content = findViewById(android.R.id.content);
        ViewUtil.setTextViewValue(content, R.id.requestInfo,
                getString(R.string.service_gui_AUTHORIZATION_REQUESTED_INFO, contactId));

        ViewUtil.setTextViewValue(content, R.id.addToContacts,
                getString(R.string.service_gui_ADD_AUTHORIZED_CONTACT, contactId));

        Spinner contactGroupSpinner = findViewById(R.id.selectGroupSpinner);
        contactGroupSpinner.setAdapter(new MetaContactGroupAdapter(this, R.id.selectGroupSpinner, true, true));

        CompoundButton addToContactsCb = findViewById(R.id.addToContacts);
        addToContactsCb.setOnCheckedChangeListener((buttonView, isChecked)
                -> updateAddToContactsStatus(isChecked));

        // Prevents from closing the dialog on outside touch
        setFinishOnTouchOutside(false);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void onResume()
    {
        super.onResume();

        // Update add to contacts status
        updateAddToContactsStatus(ViewUtil.isCompoundChecked(getContentView(), R.id.addToContacts));
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
     * Updates select group spinner status based on add to contact list checkbox state.
     *
     * @param isChecked <tt>true</tt> if "add to contacts" checkbox is checked.
     */
    private void updateAddToContactsStatus(boolean isChecked)
    {
        ViewUtil.ensureEnabled(getContentView(), R.id.selectGroupSpinner, isChecked);
    }

    /**
     * Method fired when user accept the request.
     *
     * @param v the button's <tt>View</tt>
     */
    @SuppressWarnings("unused")
    public void onAcceptClicked(View v)
    {
        responseCode = AuthorizationResponse.ACCEPT;
        finish();
    }

    /**
     * Method fired when reject button is clicked.
     *
     * @param v the button's <tt>View</tt>
     */
    @SuppressWarnings("unused")
    public void onRejectClicked(View v)
    {
        responseCode = AuthorizationResponse.REJECT;
        finish();
    }

    /**
     * Method fired when ignore button is clicked.
     *
     * @param v the button's <tt>View</tt>
     */
    @SuppressWarnings("unused")
    public void onIgnoreClicked(View v)
    {
        finish();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void onDestroy()
    {
        super.onDestroy();

        // cmeng - Handle in OperationSetPersistentPresenceJabberImpl#handleSubscribeReceived
//		if (ViewUtil.isCompoundChecked(getContentView(), R.id.addToContacts)
//				&& responseCode.equals(AuthorizationResponse.ACCEPT)) {
//			// Add to contacts
//			Spinner groupSpinner = findViewById(R.id.selectGroupSpinner);
//			ContactListUtils.addContact(request.contact.getProtocolProvider(),
//					(MetaContactGroup) groupSpinner.getSelectedItem(), request.contact.getAddress());
//		}
        request.notifyResponseReceived(responseCode);
    }

    /**
     * Shows <tt>AuthorizationRequestedDialog</tt> for the request with given <tt>id</tt>.
     *
     * @param id request identifier for which new dialog will be displayed.
     */
    public static void showDialog(Long id)
    {
        Context ctx = aTalkApp.getGlobalContext();
        Intent showIntent = new Intent(ctx, AuthorizationRequestedDialog.class);
        showIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        showIntent.putExtra(EXTRA_REQUEST_ID, id);
        ctx.startActivity(showIntent);
    }
}
