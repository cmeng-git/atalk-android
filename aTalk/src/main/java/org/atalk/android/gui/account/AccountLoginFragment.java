/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.atalk.android.gui.account;

import android.accounts.Account;
import android.accounts.*;
import android.content.Context;
import android.os.Bundle;
import android.view.*;
import android.widget.*;

import net.java.sip.communicator.service.certificate.CertificateConfigEntry;
import net.java.sip.communicator.service.certificate.CertificateService;
import net.java.sip.communicator.service.protocol.ProtocolProviderFactory;
import net.java.sip.communicator.service.protocol.ProtocolProviderService;

import org.apache.commons.lang3.StringUtils;
import org.atalk.android.R;
import org.atalk.android.gui.util.ViewUtil;
import org.atalk.android.plugin.certconfig.CertConfigActivator;
import org.atalk.service.osgi.OSGiFragment;

import java.util.*;

/**
 * The <tt>AccountLoginFragment</tt> is used for creating new account, but can be also used to obtain
 * user credentials. In order to do that parent <tt>Activity</tt> must implement {@link AccountLoginListener}.
 *
 * @author Yana Stamcheva
 * @author Pawel Domas
 * @author Eng Chong Meng
 */
public class AccountLoginFragment extends OSGiFragment implements AdapterView.OnItemSelectedListener
{
    /**
     * The username property name.
     */
    public static final String ARG_USERNAME = "Username";

    /**
     * The password property name.
     */
    public static final String ARG_PASSWORD = "Password";

    /**
     * The password property name.
     */
    public static final String ARG_CLIENT_CERT = "ClientCert";

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
    private CheckBox mClientCertCheckBox;
    private CheckBox mServerOverrideCheckBox;
    private CheckBox mIBRegistrationCheckBox;

    private Spinner spinnerNwk;
    private Spinner spinnerDM;

    private Spinner spinnerCert;
    private CertificateConfigEntry mCertEntry = null;

    /**
     * A map of <row, CertificateConfigEntry>
     */
    private Map<Integer, CertificateConfigEntry> mCertEntryList = new LinkedHashMap<>();

    private Context mContext;

    /**
     * Creates new <tt>AccountLoginFragment</tt> with optionally filled login and password fields.
     *
     * @param login optional login text that will be filled on the form.
     * @param password optional password text that will be filled on the form.
     * @return new instance of parametrized <tt>AccountLoginFragment</tt>.
     */
    public static AccountLoginFragment createInstance(String login, String password)
    {
        AccountLoginFragment fragment = new AccountLoginFragment();

        Bundle args = new Bundle();
        args.putString(ARG_USERNAME, login);
        args.putString(ARG_PASSWORD, password);

        return fragment;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void onAttach(Context context)
    {
        super.onAttach(context);
        mContext = context;
        if (context instanceof AccountLoginListener) {
            this.loginListener = (AccountLoginListener) context;
        }
        else {
            throw new RuntimeException("Account login listener unspecified");
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void onDetach()
    {
        super.onDetach();
        loginListener = null;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState)
    {
        View content = inflater.inflate(R.layout.account_create_new, container, false);
        spinnerNwk = content.findViewById(R.id.networkSpinner);
        ArrayAdapter<CharSequence> adapterNwk = ArrayAdapter.createFromResource(mContext,
                R.array.networks_array, R.layout.simple_spinner_item);
        adapterNwk.setDropDownViewResource(R.layout.simple_spinner_dropdown_item);
        spinnerNwk.setAdapter(adapterNwk);

        spinnerDM = content.findViewById(R.id.dnssecModeSpinner);
        ArrayAdapter<CharSequence> adapterDM = ArrayAdapter.createFromResource(mContext,
                R.array.dnssec_Mode_name, R.layout.simple_spinner_item);
        adapterDM.setDropDownViewResource(R.layout.simple_spinner_dropdown_item);
        spinnerDM.setAdapter(adapterDM);

        mPasswordField = content.findViewById(R.id.passwordField);
        mShowPasswordCheckBox = content.findViewById(R.id.show_password);
        mSavePasswordCheckBox = content.findViewById(R.id.store_password);
        mIBRegistrationCheckBox = content.findViewById(R.id.ibRegistration);

        mClientCertCheckBox = content.findViewById(R.id.clientCertEnable);
        spinnerCert = content.findViewById(R.id.clientCertEntry);
        initCertList();

        mServerOverrideCheckBox = content.findViewById(R.id.serverOverridden);
        mServerIpField = content.findViewById(R.id.serverIpField);
        mServerPortField = content.findViewById(R.id.serverPortField);

        // Hide ip and port fields on first create
        updateCertEntryViewVisibility(false);
        updateViewVisibility(false);
        initializeViewListeners();
        initButton(content);

        Bundle extras = getArguments();
        if (extras != null) {
            String username = extras.getString(ARG_USERNAME);
            if (StringUtils.isNotEmpty(username)) {
                ViewUtil.setTextViewValue(container, R.id.usernameField, username);
            }

            String password = extras.getString(ARG_PASSWORD);
            if (StringUtils.isNotEmpty(password)) {
                ViewUtil.setTextViewValue(content, R.id.passwordField, password);
            }
        }
        return content;
    }

    /**
     * Certificate spinner list for selection
     */
    private void initCertList()
    {
        List<String> certList = new ArrayList<>();

        List<CertificateConfigEntry> certEntries = new ArrayList<>();
        CertificateService cvs = CertConfigActivator.getCertService();
        if (cvs != null) // NPE from field
            certEntries = cvs.getClientAuthCertificateConfigs();
        certEntries.add(0, CertificateConfigEntry.CERT_NONE);

        for (int idx = 0; idx < certEntries.size(); idx++) {
            CertificateConfigEntry entry = certEntries.get(idx);
            certList.add(entry.toString());
            mCertEntryList.put(idx, entry);
        }

        ArrayAdapter<String> certAdapter = new ArrayAdapter<>(mContext, R.layout.simple_spinner_item, certList);
        certAdapter.setDropDownViewResource(R.layout.simple_spinner_dropdown_item);
        spinnerCert.setAdapter(certAdapter);
        spinnerCert.setOnItemSelectedListener(this);
    }

    private void initializeViewListeners()
    {
        mShowPasswordCheckBox.setOnCheckedChangeListener((buttonView, isChecked)
                -> ViewUtil.showPassword(mPasswordField, isChecked));
        mClientCertCheckBox.setOnCheckedChangeListener((buttonView, isChecked)
                -> updateCertEntryViewVisibility(isChecked));
        mServerOverrideCheckBox.setOnCheckedChangeListener((buttonView, isChecked)
                -> updateViewVisibility(isChecked));
    }

    /**
     * Initializes the sign in button.
     */
    private void initButton(final View content)
    {
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

            // cmeng - must trim all leading and ending whitespace character entered
            // get included by android from auto correction checker
            String userName = ViewUtil.toString(content.findViewById(R.id.usernameField));
            String password = ViewUtil.toString(mPasswordField);

            if (mClientCertCheckBox.isChecked() && (!CertificateConfigEntry.CERT_NONE.equals(mCertEntry))) {
                accountProperties.put(ProtocolProviderFactory.CLIENT_TLS_CERTIFICATE, mCertEntry.toString());
            }
            else {
                accountProperties.put(ProtocolProviderFactory.CLIENT_TLS_CERTIFICATE, CertificateConfigEntry.CERT_NONE.toString());
            }

            String serverAddress = ViewUtil.toString(mServerIpField);
            String serverPort = ViewUtil.toString(mServerPortField);

            String savePassword = Boolean.toString(mSavePasswordCheckBox.isChecked());
            accountProperties.put(ProtocolProviderFactory.PASSWORD_PERSISTENT, savePassword);

            String ibRegistration = Boolean.toString(mIBRegistrationCheckBox.isChecked());
            accountProperties.put(ProtocolProviderFactory.IBR_REGISTRATION, ibRegistration);

            // Update server override options
            if (mServerOverrideCheckBox.isChecked() && (serverAddress != null) && (serverPort != null)) {
                accountProperties.put(ProtocolProviderFactory.IS_SERVER_OVERRIDDEN, "true");
                accountProperties.put(ProtocolProviderFactory.SERVER_ADDRESS, serverAddress);
                accountProperties.put(ProtocolProviderFactory.SERVER_PORT, serverPort);
            }
            else {
                accountProperties.put(ProtocolProviderFactory.IS_SERVER_OVERRIDDEN, "false");
            }
            loginListener.onLoginPerformed(userName, password, selectedNetwork, accountProperties);
        });

        final Button cancelButton = content.findViewById(R.id.buttonCancel);
        cancelButton.setOnClickListener(v -> getActivity().finish());
    }

    private void updateCertEntryViewVisibility(boolean isEnabled)
    {
        if (isEnabled) {
            spinnerCert.setVisibility(View.VISIBLE);
        }
        else {
            spinnerCert.setVisibility(View.GONE);
        }
    }

    private void updateViewVisibility(boolean IsServerOverridden)
    {
        if (IsServerOverridden) {
            mServerIpField.setVisibility(View.VISIBLE);
            mServerPortField.setVisibility(View.VISIBLE);
        }
        else {
            mServerIpField.setVisibility(View.GONE);
            mServerPortField.setVisibility(View.GONE);
        }
    }

    /**
     * Stores the given <tt>protocolProvider</tt> data in the android system accounts.
     *
     * @param protocolProvider the <tt>ProtocolProviderService</tt>, corresponding to the account to store
     */
    private void storeAndroidAccount(ProtocolProviderService protocolProvider)
    {
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

    @Override
    public void onItemSelected(AdapterView<?> adapter, View view, int pos, long id)
    {
        if (adapter.getId() == R.id.clientCertEntry) {
            mCertEntry = mCertEntryList.get(pos);
        }
    }

    @Override
    public void onNothingSelected(AdapterView<?> parent)
    {
    }

    /**
     * The interface is used to notify listener when user click the sign-in button.
     */
    public interface AccountLoginListener
    {
        /**
         * Method is called when user click the sign in button.
         *
         * @param userName the login account entered by the user.
         * @param password the password entered by the user.
         * @param network the network name selected by the user.
         */
        void onLoginPerformed(String userName, String password, String network, Map<String, String> accountProperties);
    }
}
