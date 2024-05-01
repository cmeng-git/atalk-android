/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.atalk.android.gui.account;

import android.content.Intent;
import android.os.Bundle;

import net.java.sip.communicator.service.gui.AccountRegistrationWizard;
import net.java.sip.communicator.service.protocol.OperationFailedException;
import net.java.sip.communicator.service.protocol.ProtocolProviderService;

import org.atalk.android.R;
import org.atalk.android.gui.aTalk;
import org.atalk.android.gui.dialogs.DialogActivity;
import org.atalk.android.gui.menu.ExitMenuActivity;
import org.osgi.framework.BundleContext;
import org.osgi.framework.InvalidSyntaxException;
import org.osgi.framework.ServiceReference;

import java.util.Map;

import timber.log.Timber;

/**
 * The <code>AccountLoginActivity</code> is the activity responsible for creating or
 * registration a new account on the server.
 *
 * @author Yana Stamcheva
 * @author Pawel Domas
 * @author Eng Chong Meng
 */
public class AccountLoginActivity extends ExitMenuActivity
        implements AccountLoginFragment.AccountLoginListener
{
    /**
     * The username property name.
     */
    public static final String USERNAME = "Username";

    /**
     * The password property name.
     */
    public static final String PASSWORD = "Password";

    /**
     * Called when the activity is starting. Initializes the corresponding call interface.
     *
     * @param savedInstanceState If the activity is being re-initialized after previously being shut down then this
     * Bundle contains the data it most recently supplied in onSaveInstanceState(Bundle).
     * Note: Otherwise it is null.
     */
    @Override
    protected void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);

        // If we have instance state it means the fragment is already created
        if (savedInstanceState == null) {
            // Create AccountLoginFragment fragment
            String login = getIntent().getStringExtra(USERNAME);
            String password = getIntent().getStringExtra(PASSWORD);
            AccountLoginFragment accountLogin = AccountLoginFragment.createInstance(login, password);
            getSupportFragmentManager().beginTransaction().add(android.R.id.content, accountLogin).commit();
        }
    }

    /**
     * Create an new account database with the given <code>userName</code>, <code>password</code>
     * and <code>protocolName</code>.
     *
     * @param userName the username of the account
     * @param password the password of the account
     * @param protocolName the name of the protocol
     * @return the <code>ProtocolProviderService</code> corresponding to the newly signed in account
     */
    private ProtocolProviderService createAccount(String userName, String password,
            String protocolName, Map<String, String> accountProperties)
    {
        BundleContext bundleContext = getBundleContext();
        // Find all the available AccountRegistrationWizard that the system has implemented
        ServiceReference<?>[] accountWizardRefs = null;
        try {
            accountWizardRefs = bundleContext.getServiceReferences(AccountRegistrationWizard.class.getName(), null);
        } catch (InvalidSyntaxException ex) {
            // this shouldn't happen since we have provided all parameter string
            Timber.e(ex, "Error while retrieving service refs");
        }

        // in case we found none, then exit.
        if (accountWizardRefs == null) {
            Timber.e("No registered account registration wizards found");
            return null;
        }

        Timber.d("Found %s already installed providers.", accountWizardRefs.length);

        // Get the user selected AccountRegistrationWizard for account registration
        AccountRegistrationWizard selectedWizard = null;
        for (ServiceReference<?> accountWizardRef : accountWizardRefs) {
            AccountRegistrationWizard accReg = (AccountRegistrationWizard) bundleContext.getService(accountWizardRef);
            if (accReg.getProtocolName().equals(protocolName)) {
                selectedWizard = accReg;
                break;
            }
        }
        if (selectedWizard == null) {
            Timber.w("No account registration wizard found for protocol name: %s", protocolName);
            return null;
        }
        try {
            selectedWizard.setModification(false);
            return selectedWizard.signin(userName, password, accountProperties);
        } catch (OperationFailedException e) {
            Timber.e(e, "Account creation operation failed.");

            switch (e.getErrorCode()) {
                case OperationFailedException.ILLEGAL_ARGUMENT:
                    DialogActivity.showDialog(this, R.string.login_failed,
                            R.string.username_password_null);
                    break;
                case OperationFailedException.IDENTIFICATION_CONFLICT:
                    DialogActivity.showDialog(this, R.string.login_failed,
                            R.string.user_exist_error);
                    break;
                case OperationFailedException.SERVER_NOT_SPECIFIED:
                    DialogActivity.showDialog(this, R.string.login_failed,
                            R.string.server_info_not_complete);
                    break;
                default:
                    DialogActivity.showDialog(this, R.string.login_failed,
                            R.string.account_create_failed, e.getMessage());
            }
        } catch (Exception e) {
            Timber.e(e, "Exception while adding account: %s", e.getMessage());
            DialogActivity.showDialog(this, R.string.error,
                    R.string.account_create_failed, e.getMessage());
        }
        return null;
    }

    /**
     * See {@link AccountLoginFragment.AccountLoginListener#onLoginPerformed}
     * Not used causing problem in API-34 release, So disable it.
     */
    @Override
    public void onLoginPerformed(String userName, String password, String network, Map<String, String> accountProperties)
    {
        ProtocolProviderService pps = createAccount(userName, password, network, accountProperties);
        if (pps != null) {
            startActivity(new Intent(this, aTalk.class));
            finish();
        }
    }
}