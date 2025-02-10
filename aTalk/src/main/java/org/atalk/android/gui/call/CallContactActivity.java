/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.atalk.android.gui.call;

import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.telephony.PhoneNumberUtils;

import androidx.annotation.NonNull;

import org.atalk.android.BaseActivity;
import org.atalk.android.gui.aTalk;

/**
 * Tha <code>CallContactActivity</code> can be used to call contact. The phone number can be filled
 * from <code>Intent</code> data.
 *
 * @author Yana Stamcheva
 * @author Pawel Domas
 */
public class CallContactActivity extends BaseActivity {
    protected CallContactFragment ccFragment;

    /**
     * Called when the activity is starting. Initializes the corresponding call interface.
     *
     * @param savedInstanceState If the activity is being re-initialized after previously being shut down
     * then this Bundle contains the data it most recently supplied in onSaveInstanceState(Bundle).
     * Note: Otherwise it is null.
     */
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // There's no need to create fragment if the Activity is being restored.
        if (savedInstanceState == null) {
            // Create new call contact fragment
            String phoneNumber = null;
            Intent intent = getIntent();
            if (intent.getDataString() != null)
                phoneNumber = PhoneNumberUtils.getNumberFromIntent(intent, this);
            ccFragment = CallContactFragment.newInstance(phoneNumber);
            getSupportFragmentManager().beginTransaction().add(android.R.id.content, ccFragment).commit();
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        // If request is canceled, the result arrays are empty.
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == aTalk.PRC_GET_CONTACTS) {
            if ((grantResults.length > 0)
                    && (grantResults[0] == PackageManager.PERMISSION_GRANTED)) {
                // permission granted, so proceed
                ccFragment.initAndroidAccounts();
            }
        }
    }
}