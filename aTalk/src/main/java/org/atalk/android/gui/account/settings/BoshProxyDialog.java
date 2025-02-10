/*
 * aTalk, android VoIP and Instant Messaging client
 * Copyright 2014 Eng Chong Meng
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.atalk.android.gui.account.settings;

import android.app.Dialog;
import android.content.Context;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.view.View;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemSelectedListener;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.Spinner;

import net.java.sip.communicator.plugin.jabberaccregwizz.JabberAccountRegistrationActivator;
import net.java.sip.communicator.service.protocol.AccountManager;
import net.java.sip.communicator.service.protocol.ProtocolProviderActivator;
import net.java.sip.communicator.service.protocol.ProtocolProviderFactory;
import net.java.sip.communicator.service.protocol.jabber.JabberAccountRegistration;

import org.atalk.android.R;
import org.atalk.android.aTalkApp;
import org.atalk.android.gui.dialogs.DialogActivity;
import org.atalk.android.gui.util.ViewUtil;
import org.atalk.service.configuration.ConfigurationService;

/**
 * The Bosh-Proxy dialog is the one shown when the user clicks to set Bosh-Proxy preference in Account Settings...
 *
 * @author Eng Chong Meng
 */
public class BoshProxyDialog extends Dialog implements OnItemSelectedListener, TextWatcher, DialogActivity.DialogListener {
    public final static String NONE = "NONE";
    public final static String BOSH = "BOSH";
    public final static String HTTP = "HTTP";

    private final static String SOCKS4 = "SOCKS4";
    private final static String SOCKS5 = "SOCKS5";

    private final Context mContext;
    private final JabberAccountRegistration jbrReg;
    private final String mAccountUuid;

    /**
     * The bosh proxy list view.
     */
    private Spinner spinnerType;
    private View boshUrlSetting;

    private CheckBox cbHttpProxy;
    private EditText boshURL;

    private EditText proxyHost;
    private EditText proxyPort;

    private EditText proxyUserName;
    private EditText proxyPassword;

    private Button mApplyButton;

    /**
     * Flag indicating if there are uncommitted changes - need static to avoid clear by android OS
     */
    private boolean hasChanges;

    // Init to default selected so avoid false trigger on entry.
    private int mIndex = 0;

    /**
     * Constructs the <code>Bosh-Proxy Dialog</code>.
     *
     * @param context the Context
     * @param jbrReg the JabberAccountRegistration
     */
    public BoshProxyDialog(Context context, JabberAccountRegistration jbrReg) {
        super(context);
        mContext = context;

        this.jbrReg = jbrReg;
        String editedAccUID = jbrReg.getAccountUid();
        AccountManager accManager = ProtocolProviderActivator.getAccountManager();
        ProtocolProviderFactory factory = JabberAccountRegistrationActivator.getJabberProtocolProviderFactory();
        mAccountUuid = accManager.getStoredAccountUUID(factory, editedAccUID);
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setTitle(R.string.settings_bosh_proxy);
        this.setContentView(R.layout.bosh_proxy_dialog);

        spinnerType = findViewById(R.id.boshProxyType);
        boshUrlSetting = findViewById(R.id.boshURL_setting);
        boshURL = findViewById(R.id.boshURL);
        boshURL.addTextChangedListener(this);

        cbHttpProxy = findViewById(R.id.cbHttpProxy);
        cbHttpProxy.setOnCheckedChangeListener((buttonView, isChecked) -> hasChanges = true);

        proxyHost = findViewById(R.id.proxyHost);
        proxyHost.addTextChangedListener(this);
        proxyPort = findViewById(R.id.proxyPort);
        proxyPort.addTextChangedListener(this);

        proxyUserName = findViewById(R.id.proxyUsername);
        proxyUserName.addTextChangedListener(this);
        proxyPassword = findViewById(R.id.proxyPassword);
        proxyPassword.addTextChangedListener(this);

        initBoshProxyDialog();

        CheckBox showPassword = findViewById(R.id.show_password);
        showPassword.setOnCheckedChangeListener((buttonView, isChecked)
                -> ViewUtil.showPassword(proxyPassword, isChecked));

        mApplyButton = findViewById(R.id.button_Apply);
        mApplyButton.setOnClickListener(v -> {
            if (hasChanges) {
                if (saveBoshProxySettings())
                    cancel();
            }
        });

        Button cancelButton = findViewById(R.id.button_Cancel);
        cancelButton.setOnClickListener(v -> checkUnsavedChanges());
        setCancelable(false);
        setCanceledOnTouchOutside(false);
        hasChanges = false;
    }

    /**
     * initialize the Bosh-proxy dialog with the db stored values
     */
    private void initBoshProxyDialog() {
        ArrayAdapter<CharSequence> adapterType = ArrayAdapter.createFromResource(mContext,
                R.array.bosh_proxy_type, R.layout.simple_spinner_item);
        adapterType.setDropDownViewResource(R.layout.simple_spinner_dropdown_item);
        spinnerType.setAdapter(adapterType);
        spinnerType.setOnItemSelectedListener(this);

        String type = jbrReg.getProxyType();
        if (!TextUtils.isEmpty(type)) {
            for (mIndex = 0; mIndex < spinnerType.getCount(); mIndex++) {
                if (spinnerType.getItemAtPosition(mIndex).equals(type)) {
                    spinnerType.setSelection(mIndex);
                    onItemSelected(spinnerType, spinnerType.getSelectedView(), mIndex, spinnerType.getSelectedItemId());
                    break;
                }
            }
        }
        boshURL.setText(jbrReg.getBoshUrl());
        cbHttpProxy.setChecked(jbrReg.isBoshHttpProxyEnabled());

        proxyHost.setText(jbrReg.getProxyAddress());
        proxyPort.setText(jbrReg.getProxyPort());

        proxyUserName.setText(jbrReg.getProxyUserName());
        proxyPassword.setText(jbrReg.getProxyPassword());
    }

    @Override
    public void onItemSelected(AdapterView<?> adapter, View view, int pos, long id) {
        String type = (String) adapter.getItemAtPosition(pos);
        if (BOSH.equals(type)) {
            boshUrlSetting.setVisibility(View.VISIBLE);
        }
        else {
            boshUrlSetting.setVisibility(View.GONE);
        }

        if (mIndex != pos) {
            mIndex = pos;
            hasChanges = true;
        }
    }

    @Override
    public void onNothingSelected(AdapterView<?> parent) {
        // Another interface callback
    }

    /**
     * Save user entered Bosh-Proxy settings.
     */
    private boolean saveBoshProxySettings() {
        Object sType = spinnerType.getSelectedItem();
        String type = (sType == null) ? NONE : sType.toString();

        String boshUrl = ViewUtil.toString(boshURL);
        String host = ViewUtil.toString(proxyHost);
        String port = ViewUtil.toString(proxyPort);
        String userName = ViewUtil.toString(proxyUserName);
        String password = ViewUtil.toString(proxyPassword);

        String accPrefix = mAccountUuid + ".";
        ConfigurationService configSrvc = ProtocolProviderActivator.getConfigurationService();
        configSrvc.setProperty(accPrefix + ProtocolProviderFactory.PROXY_TYPE, type);
        jbrReg.setProxyType(type);

        switch (type) {
            case BOSH:
                if (boshUrl == null) {
                    aTalkApp.showToastMessage(R.string.bosh_proxy_url_null);
                    return false;
                }
                configSrvc.setProperty(accPrefix + ProtocolProviderFactory.BOSH_URL, boshUrl);
                jbrReg.setBoshUrl(boshUrl);

                boolean isHttpProxy = cbHttpProxy.isChecked();
                configSrvc.setProperty(accPrefix + ProtocolProviderFactory.BOSH_PROXY_HTTP_ENABLED, isHttpProxy);
                jbrReg.setBoshHttpProxyEnabled(isHttpProxy);

                // Continue with proxy settings checking if BOSH HTTP Proxy is enabled
                if (!isHttpProxy)
                    break;
            case HTTP:
            case SOCKS4:
            case SOCKS5:
                if ((host == null) || (port == null)) {
                    aTalkApp.showToastMessage(R.string.bosh_proxy_host_port_null);
                    return false;
                }
                break;
            case NONE:
            default:
                break;
        }

        // value if null will remove the parameter from DB
        configSrvc.setProperty(accPrefix + ProtocolProviderFactory.PROXY_ADDRESS, host);
        jbrReg.setProxyAddress(host);

        configSrvc.setProperty(accPrefix + ProtocolProviderFactory.PROXY_PORT, port);
        jbrReg.setProxyPort(port);

        configSrvc.setProperty(accPrefix + ProtocolProviderFactory.PROXY_USERNAME, userName);
        jbrReg.setProxyUserName(userName);

        configSrvc.setProperty(accPrefix + ProtocolProviderFactory.PROXY_PASSWORD, password);
        jbrReg.setProxyPassword(password);

        // remove obsolete setting from DB - to be remove on later version (>2.0.4)
        configSrvc.setProperty(accPrefix + ProtocolProviderFactory.IS_USE_PROXY, null);

        AccountPreferenceFragment.setUncommittedChanges();
        return true;
    }

    /**
     * check for any unsaved changes and alert user
     */
    private void checkUnsavedChanges() {
        if (hasChanges) {
            DialogActivity.showConfirmDialog(mContext,
                    R.string.unsaved_changes_title,
                    R.string.unsaved_changes,
                    R.string.save, this);
        }
        else {
            cancel();
        }
    }

    /**
     * Fired when user clicks the dialog's confirm button.
     *
     * @param dialog source <code>DialogActivity</code>.
     */
    public boolean onConfirmClicked(DialogActivity dialog) {
        return mApplyButton.performClick();
    }

    /**
     * Fired when user dismisses the dialog.
     *
     * @param dialog source <code>DialogActivity</code>
     */
    public void onDialogCancelled(DialogActivity dialog) {
        cancel();
    }

    @Override
    public void beforeTextChanged(CharSequence s, int start, int count, int after) {
    }

    @Override
    public void onTextChanged(CharSequence s, int start, int before, int count) {
    }

    @Override
    public void afterTextChanged(Editable s) {
        hasChanges = true;
    }
}

