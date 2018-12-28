/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.atalk.android.gui.account;

import android.accounts.Account;
import android.accounts.*;
import android.app.Activity;
import android.os.Bundle;
import android.text.InputType;
import android.text.TextUtils;
import android.view.*;
import android.widget.*;
import net.java.sip.communicator.service.protocol.ProtocolProviderFactory;
import net.java.sip.communicator.service.protocol.ProtocolProviderService;
import org.atalk.android.R;
import org.atalk.android.gui.util.ViewUtil;
import org.atalk.service.osgi.OSGiFragment;
import org.atalk.util.StringUtils;

import java.util.HashMap;
import java.util.Map;

/**
 * The <tt>AccountLoginFragment</tt> is used for creating new account, but can be also used to obtain
 * user credentials. In order to do that parent <tt>Activity</tt> must implement {@link AccountLoginListener}.
 *
 * @author Yana Stamcheva
 * @author Pawel Domas
 * @author Eng Chong Meng
 */
public class AccountLoginFragment extends OSGiFragment {
    /**
     * The username property name.
     */
    public static final String ARG_USERNAME = "Username";

    /**
     * The password property name.
     */
    public static final String ARG_PASSWORD = "Password";

    /**
     * Contains all implementation specific properties that define the account.
     */
    protected Map<String, String> accountProperties = new HashMap<>();

    /**
     * The listener parent Activity that will be notified when user enters login, password,
     * server overridden option and server parameters etc
     */
    private AccountLoginListener loginListener;

    private EditText mPasswordField;
    private EditText mServerIpField;
    private EditText mServerPortField;

    private CheckBox mShowPasswordCheckBox;
    private CheckBox mSavePasswordCheckBox;
    private CheckBox mServerOverrideCheckBox;
    private CheckBox mIBRegistrationCheckBox;
    private ImageView mShowPasswordImage;

    private Spinner spinnerNwk;
    private Spinner spinnerDM;

    /**
     * {@inheritDoc}
     */
    @Override
    public void onAttach(Activity activity) {
        super.onAttach(activity);
        if (activity instanceof AccountLoginListener) {
            this.loginListener = (AccountLoginListener) activity;
        } else {
            throw new RuntimeException("Account login listener unspecified");
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void onDetach() {
        super.onDetach();
        loginListener = null;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View content = inflater.inflate(R.layout.account_create_new, container, false);
        spinnerNwk = content.findViewById(R.id.networkSpinner);
        ArrayAdapter<CharSequence> adapterNwk = ArrayAdapter.createFromResource(getActivity(),
                R.array.networks_array, R.layout.simple_spinner_item);
        adapterNwk.setDropDownViewResource(R.layout.simple_spinner_dropdown_item);
        spinnerNwk.setAdapter(adapterNwk);

        spinnerDM = content.findViewById(R.id.dnssecModeSpinner);
        ArrayAdapter<CharSequence> adapterDM = ArrayAdapter.createFromResource(getActivity(),
                R.array.dnssec_Mode_name, R.layout.simple_spinner_item);
        adapterDM.setDropDownViewResource(R.layout.simple_spinner_dropdown_item);
        spinnerDM.setAdapter(adapterDM);

        mPasswordField = content.findViewById(R.id.passwordField);
        mShowPasswordImage = content.findViewById(R.id.pwdviewImage);
        mShowPasswordCheckBox = content.findViewById(R.id.show_password);
        mSavePasswordCheckBox  = content.findViewById(R.id.store_password);
        mIBRegistrationCheckBox = content.findViewById(R.id.ibRegistration);
        mServerOverrideCheckBox = content.findViewById(R.id.serverOverridden);
        mServerIpField = content.findViewById(R.id.serverIpField);
        mServerPortField = content.findViewById(R.id.serverPortField);

        // Hide ip and port fields on first create
        updateViewVisibility(false);
        initializeViewListeners();
        initButton(content);

        Bundle extras = getArguments();
        if (extras != null) {
            String username = extras.getString(ARG_USERNAME);
            if (!StringUtils.isNullOrEmpty(username)) {
                ViewUtil.setTextViewValue(container, R.id.usernameField, username);
            }

            String password = extras.getString(ARG_PASSWORD);
            if (!StringUtils.isNullOrEmpty(password)) {
                ViewUtil.setTextViewValue(content, R.id.passwordField, password);
            }
        }
        return content;
    }

    private void initializeViewListeners() {
        mShowPasswordCheckBox.setOnCheckedChangeListener(
                new CompoundButton.OnCheckedChangeListener() {
                    @Override
                    public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                        showPassword(isChecked);
                    }
                });

        mServerOverrideCheckBox.setOnCheckedChangeListener(
                new CompoundButton.OnCheckedChangeListener() {
                    @Override
                    public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                        updateViewVisibility(isChecked);
                    }
                });

    }

    /**
     * Initializes the sign in button.
     */
    private void initButton(final View content) {
        final Button signInButton = content.findViewById(R.id.buttonSignIn);
        signInButton.setEnabled(true);

        signInButton.setOnClickListener(v -> {
            // Translate network label to network value
            String[] networkValues = getResources().getStringArray(R.array.networks_array_values);
            String selectedNetwork = networkValues[spinnerNwk.getSelectedItemPosition()];

            // Translate dnssecMode label to dnssecMode value
            String[] dnssecModeValues = getResources().getStringArray(R.array.dnssec_Mode_value);
            String selectedDnssecMode = dnssecModeValues[spinnerDM.getSelectedItemPosition()];
            accountProperties.put(ProtocolProviderFactory.DNSSEC_MODE, selectedDnssecMode);

            // cmeng - must trim all inline, leading and ending whitespace character entered by
            // user accidentally or included by android from spelling checker
            final EditText userNameField = content.findViewById(R.id.usernameField);
            String userName = userNameField.getText().toString().replaceAll("\\s", "");
            String password = mPasswordField.getText().toString().trim();

            String serverAddress = mServerIpField.getText().toString().replaceAll("\\s", "");
            String serverPort = mServerPortField.getText().toString().replaceAll("\\s", "");

            String savePassword = Boolean.toString(mSavePasswordCheckBox.isChecked());
            accountProperties.put(ProtocolProviderFactory.PASSWORD_PERSISTENT, savePassword);

            String ibRegistration = Boolean.toString(mIBRegistrationCheckBox.isChecked());
            accountProperties.put(ProtocolProviderFactory.IBR_REGISTRATION, ibRegistration);

            // Update server override options
            if (mServerOverrideCheckBox.isChecked()
                    && !TextUtils.isEmpty(serverAddress) && !TextUtils.isEmpty(serverPort)) {
                accountProperties.put(ProtocolProviderFactory.IS_SERVER_OVERRIDDEN, "true");
                accountProperties.put(ProtocolProviderFactory.SERVER_ADDRESS, serverAddress);
                accountProperties.put(ProtocolProviderFactory.SERVER_PORT, serverPort);
            } else {
                accountProperties.put(ProtocolProviderFactory.IS_SERVER_OVERRIDDEN, "false");
            }
            loginListener.onLoginPerformed(userName, password, selectedNetwork, accountProperties);
        });

        final Button cancelButton = content.findViewById(R.id.buttonCancel);
        cancelButton.setOnClickListener(v -> getActivity().finish());
    }

    private void updateViewVisibility(boolean IsServerOverridden) {
        if (IsServerOverridden) {
            mServerIpField.setVisibility(View.VISIBLE);
            mServerPortField.setVisibility(View.VISIBLE);
        } else {
            mServerIpField.setVisibility(View.GONE);
            mServerPortField.setVisibility(View.GONE);
        }
    }

    private void showPassword(boolean show) {
        int cursorPosition = mPasswordField.getSelectionStart();
        if (show) {
            mShowPasswordImage.setAlpha(1.0f);
            mPasswordField.setInputType(InputType.TYPE_TEXT_VARIATION_PASSWORD);
        } else {
            mShowPasswordImage.setAlpha(0.3f);
            mPasswordField.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        }
        mPasswordField.setSelection(cursorPosition);
    }

    /**
     * Stores the given <tt>protocolProvider</tt> data in the android system accounts.
     *
     * @param protocolProvider the <tt>ProtocolProviderService</tt>, corresponding to the account to store
     */
    private void storeAndroidAccount(ProtocolProviderService protocolProvider) {
        Map<String, String> accountProps = protocolProvider.getAccountID().getAccountProperties();

        String username = accountProps.get(ProtocolProviderFactory.USER_ID);
        Account account = new Account(username, getString(R.string.ACCOUNT_TYPE));

        final Bundle extraData = new Bundle();
        for (String key : accountProps.keySet()) {
            extraData.putString(key, accountProps.get(key));
        }

        AccountManager am = AccountManager.get(getActivity());
        boolean accountCreated = am.addAccountExplicitly(account,
                accountProps.get(ProtocolProviderFactory.PASSWORD), extraData);

        Bundle extras = getArguments();
        if (extras != null) {
            if (accountCreated) { // Pass the new account back to the account manager
                AccountAuthenticatorResponse response
                        = extras.getParcelable(AccountManager.KEY_ACCOUNT_AUTHENTICATOR_RESPONSE);

                Bundle result = new Bundle();
                result.putString(AccountManager.KEY_ACCOUNT_NAME, username);
                result.putString(AccountManager.KEY_ACCOUNT_TYPE, getString(R.string.ACCOUNT_TYPE));
                result.putAll(extraData);
                response.onResult(result);
            }
            // TODO: notify about account authentication
            // finish();
        }
    }

    /**
     * Creates new <tt>AccountLoginFragment</tt> with optionally filled login and password fields.
     *
     * @param login    optional login text that will be filled on the form.
     * @param password optional password text that will be filled on the form.
     * @return new instance of parametrized <tt>AccountLoginFragment</tt>.
     */
    public static AccountLoginFragment createInstance(String login, String password) {
        AccountLoginFragment fragment = new AccountLoginFragment();

        Bundle args = new Bundle();
        args.putString(ARG_USERNAME, login);
        args.putString(ARG_PASSWORD, password);

        return fragment;
    }

    /**
     * The interface is used to notify listener when user click the sign-in button.
     */
    public interface AccountLoginListener {
        /**
         * Method is called when user click the sign in button.
         *
         * @param userName the login account entered by the user.
         * @param password the password entered by the user.
         * @param network  the network name selected by the user.
         */
        public void onLoginPerformed(String userName, String password, String network,
                                     Map<String, String> accountProperties);
    }
}
