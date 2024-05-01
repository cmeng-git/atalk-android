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
package org.atalk.android.plugin.certconfig;

import android.content.Context;
import android.graphics.Rect;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.Spinner;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;

import net.java.sip.communicator.impl.certificate.CertificateVerificationActivator;
import net.java.sip.communicator.service.certificate.CertificateConfigEntry;
import net.java.sip.communicator.service.certificate.CertificateService;
import net.java.sip.communicator.service.certificate.KeyStoreType;
import net.java.sip.communicator.service.gui.AuthenticationWindowService;

import org.atalk.android.R;
import org.atalk.android.aTalkApp;
import org.atalk.android.gui.util.ViewUtil;
import org.atalk.impl.androidcertdialog.X509CertificateView;
import org.atalk.persistance.FilePathHelper;
import org.atalk.service.osgi.OSGiDialogFragment;
import org.jetbrains.annotations.NotNull;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Constructor;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.Provider;
import java.security.Security;
import java.security.UnrecoverableEntryException;
import java.security.cert.Certificate;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;

import javax.security.auth.callback.Callback;
import javax.security.auth.callback.PasswordCallback;
import javax.security.auth.callback.UnsupportedCallbackException;

import timber.log.Timber;

/**
 * Dialog window to add/edit client certificate configuration entries.
 *
 * @author Eng Chong Meng
 */
public class CertConfigEntryDialog extends OSGiDialogFragment
        implements View.OnClickListener, CompoundButton.OnCheckedChangeListener, AdapterView.OnItemSelectedListener
{
    // ------------------------------------------------------------------------
    // Fields and services
    // ------------------------------------------------------------------------
    private static final KeyStoreType KS_NONE =
            new KeyStoreType(aTalkApp.getResString(R.string.none), new String[]{""}, false);

    private static final String PKCS11 = "PKCS11";

    private EditText txtDisplayName;
    private EditText txtKeyStore;
    private EditText txtKeyStorePassword;

    private CheckBox chkSavePassword;
    private ImageButton cmdShowCert;

    private Spinner cboKeyStoreType;
    private Spinner cboAlias;

    private KeyStore mKeyStore;
    private final List<KeyStoreType> keyStoreTypes = new ArrayList<>();

    private ArrayAdapter<String> aliasAdapter;
    private final List<String> mAliasList = new ArrayList<>();

    private Context mContext;
    private CertificateService cs;

    // Use Static scope to prevent crash on screen rotation
    private static CertificateConfigEntry mEntry;

    /**
     * callback to caller with status and entry value
     */
    private static OnFinishedCallback finishedCallback = null;

    // Stop cboKeyStoreType from triggering on first entry
    private boolean newInstall = false;

    public static CertConfigEntryDialog getInstance(CertificateConfigEntry entry, OnFinishedCallback callback)
    {
        CertConfigEntryDialog dialog = new CertConfigEntryDialog();
        mEntry = entry;
        finishedCallback = callback;
        return dialog;
    }

    @Override
    public View onCreateView(@NotNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState)
    {
        cs = CertConfigActivator.getCertService();
        mContext = getContext();
        View contentView = inflater.inflate(R.layout.cert_tls_entry_config, container, false);

        if (getDialog() != null) {
            getDialog().setTitle(R.string.certconfig_cert_entry_title);

            Window window = getDialog().getWindow();
            if (window != null) {
                Rect displayRectangle = new Rect();
                window.getDecorView().getWindowVisibleDisplayFrame(displayRectangle);
                contentView.setMinimumWidth(displayRectangle.width());
                contentView.setMinimumHeight(displayRectangle.height());
            }
        }

        txtDisplayName = contentView.findViewById(R.id.certDisplayName);
        txtKeyStore = contentView.findViewById(R.id.certFileName);

        ActivityResultLauncher<String> mGetContent = browseKeyStore();
        contentView.findViewById(R.id.browse).setOnClickListener(view -> mGetContent.launch("*/*"));

        // Init the keyStore Type Spinner
        cboKeyStoreType = contentView.findViewById(R.id.cboKeyStoreType);
        keyStoreTypes.add(KS_NONE);
        keyStoreTypes.addAll(cs.getSupportedKeyStoreTypes());
        ArrayAdapter<KeyStoreType> keyStoreAdapter = new ArrayAdapter<>(mContext, R.layout.simple_spinner_item, keyStoreTypes);
        keyStoreAdapter.setDropDownViewResource(R.layout.simple_spinner_dropdown_item);
        cboKeyStoreType.setAdapter(keyStoreAdapter);
        cboKeyStoreType.setOnItemSelectedListener(this);
        cboKeyStoreType.setEnabled(false);

        txtKeyStorePassword = contentView.findViewById(R.id.keyStorePassword);
        txtKeyStorePassword.setEnabled(false);

        CheckBox chkShowPassword = contentView.findViewById(R.id.show_password);
        chkShowPassword.setOnCheckedChangeListener(this);

        chkSavePassword = contentView.findViewById(R.id.chkSavePassword);
        chkSavePassword.setOnCheckedChangeListener(this);

        cmdShowCert = contentView.findViewById(R.id.showCert);
        cmdShowCert.setOnClickListener(this);
        cmdShowCert.setEnabled(false);

        cboAlias = contentView.findViewById(R.id.cboAlias);
        aliasAdapter = new ArrayAdapter<>(mContext, R.layout.simple_spinner_item, mAliasList);
        aliasAdapter.setDropDownViewResource(R.layout.simple_spinner_dropdown_item);
        cboAlias.setAdapter(aliasAdapter);
        cboAlias.setOnItemSelectedListener(this);
        cboAlias.setEnabled(false);

        Button cmdOk = contentView.findViewById(R.id.button_OK);
        cmdOk.setOnClickListener(this);

        Button cmdCancel = contentView.findViewById(R.id.button_Cancel);
        cmdCancel.setOnClickListener(this);

        CertConfigEntryInit();
        setCancelable(false);
        return contentView;
    }

    /**
     * Initialization the edited certificate or add new certificate
     */
    public void CertConfigEntryInit()
    {
        String displayName = mEntry.getDisplayName();
        txtDisplayName.setText(displayName);
        txtKeyStore.setText(mEntry.getKeyStore());

        // Init edited certificate parameters
        if (mEntry.getKeyStore() != null) {
            txtKeyStorePassword.setText(mEntry.getKeyStorePassword());
            chkSavePassword.setChecked(mEntry.isSavePassword());
            cboKeyStoreType.setEnabled(true);
            cboKeyStoreType.setSelection(getIndexForType(mEntry.getKeyStoreType()));

            initKeyStoreAlias();
            cboAlias.setSelection(getIndexForAlias(mEntry.getAlias()));
            cboAlias.setEnabled(true);
        }
    }

    /**
     * Initialize KeyStore on Edit or with new SSL client certificate installation.
     * a. loadKeyStore() must not run in UI Thread
     * b. loadAliases() must execute after loadKeyStore()
     * c. loadAliases() needs to be in UI thread as it access to UI components
     */
    private void initKeyStoreAlias()
    {
        new Thread(() -> {
            try {
                mKeyStore = loadKeyStore();
                runOnUiThread(this::loadAliases);
            } catch (KeyStoreException | UnrecoverableEntryException ex) {
                Timber.e(ex, "Load KeyStore Exception");
                aTalkApp.showGenericError(R.string.certconfig_invalid_keystore_type, ex.getMessage());
            }
        }).start();
    }

    /**
     * Open the keystore selected by the user. If the type is set as PKCS#11,
     * the file is loaded as a provider. If the store is protected by a
     * password, the user is being asked by an authentication dialog.
     *
     * @return The loaded keystore
     * @throws KeyStoreException when something goes wrong
     * @throws UnrecoverableEntryException Happen in android Note-5 (not working)
     */
    private KeyStore loadKeyStore()
            throws KeyStoreException, UnrecoverableEntryException
    {
        String keyStore = ViewUtil.toString(txtKeyStore);
        if (keyStore == null)
            return null;

        final File f = new File(keyStore);
        final String keyStoreType = ((KeyStoreType) cboKeyStoreType.getSelectedItem()).getName();

        if (PKCS11.equals(keyStoreType)) {
            String config = "name=" + f.getName() + "\nlibrary=" + f.getAbsoluteFile();
            try {
                Class<?> pkcs11c = Class.forName("sun.security.pkcs11.SunPKCS11");
                Constructor<?> c = pkcs11c.getConstructor(InputStream.class);
                Provider p = (Provider) c.newInstance(new ByteArrayInputStream(config.getBytes()));
                Security.insertProviderAt(p, 0);
            } catch (Exception e) {
                Timber.e("Tried to access the PKCS11 provider on an unsupported platform or the load : %s", e.getMessage());
            }
        }

        KeyStore.Builder ksBuilder = KeyStore.Builder.newInstance(keyStoreType, null, f,
                new KeyStore.CallbackHandlerProtection(callbacks -> {
                    for (Callback cb : callbacks) {
                        if (!(cb instanceof PasswordCallback)) {
                            throw new UnsupportedCallbackException(cb);
                        }
                        PasswordCallback pwcb = (PasswordCallback) cb;
                        char[] ksPassword = ViewUtil.toCharArray(txtKeyStorePassword);
                        if ((ksPassword != null) || chkSavePassword.isChecked()) {
                            pwcb.setPassword(ksPassword);
                        }
                        else {
                            AuthenticationWindowService authenticationWindowService
                                    = CertificateVerificationActivator.getAuthenticationWindowService();

                            AuthenticationWindowService.AuthenticationWindow aw = authenticationWindowService.create(f.getName(), null, keyStoreType, false,
                                    false, null, null, null, null, null, null, null);

                            aw.setAllowSavePassword(!PKCS11.equals(keyStoreType));
                            aw.setVisible(true);
                            if (!aw.isCanceled()) {
                                pwcb.setPassword(aw.getPassword());
                                runOnUiThread(() -> {
                                    // if (!PKCS11.equals(keyStoreType) && aw.isRememberPassword()) {
                                    if (!PKCS11.equals(keyStoreType)) {
                                        txtKeyStorePassword.setText(new String(aw.getPassword()));
                                    }
                                    chkSavePassword.setChecked(aw.isRememberPassword());
                                });
                            }
                            else {
                                throw new IOException("User cancel");
                            }
                        }
                    }
                })
        );
        return ksBuilder.getKeyStore();
    }

    /**
     * Load the certificate entry aliases from the chosen keystore.
     */
    private void loadAliases()
    {
        if (mKeyStore == null)
            return;

        mAliasList.clear();
        try {
            Enumeration<String> e = mKeyStore.aliases();
            while (e.hasMoreElements()) {
                mAliasList.add(e.nextElement());
            }
            aliasAdapter.notifyDataSetChanged();
        } catch (KeyStoreException e) {
            aTalkApp.showGenericError(R.string.certconfig_alias_load_exception, e.getMessage());
        }
    }

    /**
     * Opens a FileChooserDialog to let the user pick a keystore and tries to
     * auto-detect the keystore type using the file extension
     */
    private ActivityResultLauncher<String> browseKeyStore()
    {
        return registerForActivityResult(new ActivityResultContracts.GetContent(), fileUri -> {
            if (fileUri != null) {
                File inFile = new File(FilePathHelper.getFilePath(mContext, fileUri));
                if (inFile.exists()) {
                    newInstall = true;
                    cboKeyStoreType.setEnabled(true);
                    cboKeyStoreType.setSelection(0);
                    cboAlias.setEnabled(true);

                    txtDisplayName.setText(inFile.getName());
                    txtKeyStore.setText(inFile.getAbsolutePath());
                    boolean resolved = false;
                    for (KeyStoreType kt : cs.getSupportedKeyStoreTypes()) {
                        for (String ext : kt.getFileExtensions()) {
                            if (inFile.getName().endsWith(ext)) {
                                cboKeyStoreType.setSelection(getIndexForType(kt));
                                resolved = true;
                                break;
                            }
                        }
                        if (resolved) {
                            break;
                        }
                    }
                }
                else
                    aTalkApp.showToastMessage(R.string.file_does_not_exist);
            }
        });
    }

    @Override
    public void onClick(View v)
    {
        switch (v.getId()) {
            case R.id.showCert:
                showSelectedCertificate();
                break;

            case R.id.button_OK:
                if ((cboAlias.getSelectedItem() == null)
                        || (ViewUtil.toString(txtDisplayName) == null)
                        || (ViewUtil.toString(txtKeyStore) == null)) {
                    aTalkApp.showGenericError(R.string.certconfig_incomplete);
                    return;
                }
                mEntry.setDisplayName(ViewUtil.toString(txtDisplayName));
                mEntry.setKeyStore(ViewUtil.toString(txtKeyStore));
                mEntry.setKeyStoreType((KeyStoreType) cboKeyStoreType.getSelectedItem());
                mEntry.setAlias(cboAlias.getSelectedItem().toString());

                if (chkSavePassword.isChecked()) {
                    mEntry.setSavePassword(true);
                    mEntry.setKeyStorePassword(ViewUtil.toString(txtKeyStorePassword));
                }
                else {
                    mEntry.setSavePassword(false);
                    mEntry.setKeyStorePassword(null);
                }

                closeDialog(true);
                break;
            case R.id.button_Cancel:
                closeDialog(false);
                break;
        }
    }

    @Override
    public void onCheckedChanged(CompoundButton buttonView, boolean isChecked)
    {
        switch (buttonView.getId()) {
            case R.id.show_password:
                ViewUtil.showPassword(txtKeyStorePassword, isChecked);
                break;

            case R.id.chkSavePassword:
                txtKeyStorePassword.setEnabled(chkSavePassword.isChecked()
                        && ((KeyStoreType) cboKeyStoreType.getSelectedItem()).hasKeyStorePassword()
                );
                break;
        }
    }

    @Override
    public void onItemSelected(AdapterView<?> adapter, View view, int position, long id)
    {
        switch (adapter.getId()) {
            case R.id.cboKeyStoreType:
                // Proceed if new install or != NONE. First item always get selected onEntry
                KeyStoreType kt = (KeyStoreType) cboKeyStoreType.getSelectedItem();
                if ((!newInstall) || KS_NONE.equals(kt)) {
                    return;
                }
                if (!PKCS11.equals(kt.getName()))
                    chkSavePassword.setEnabled(true);
                txtKeyStorePassword.setEnabled(kt.hasKeyStorePassword() && chkSavePassword.isChecked());
                initKeyStoreAlias();
                break;
            case R.id.cboAlias:
                cmdShowCert.setEnabled(cboAlias.getSelectedItem() != null);
        }
    }

    @Override
    public void onNothingSelected(AdapterView<?> parent)
    {
    }

    private void showSelectedCertificate()
    {
        try {
            Certificate[] chain = mKeyStore.getCertificateChain(cboAlias.getSelectedItem().toString());
            // must use getActivity: otherwise -> token null is not valid; is your activity running?
            X509CertificateView viewCertDialog = new X509CertificateView(getActivity(), chain);
            viewCertDialog.show();
        } catch (KeyStoreException e1) {
            aTalkApp.showGenericError(R.string.certconfig_show_cert_exception, e1.getMessage());
        }
    }

    private int getIndexForType(KeyStoreType type)
    {
        for (int i = 0; i < keyStoreTypes.size(); i++) {
            if (keyStoreTypes.get(i).equals(type)) {
                return i;
            }
        }
        return -1;
    }

    private int getIndexForAlias(String alias)
    {
        if (alias != null) {
            for (int i = 0; i < aliasAdapter.getCount(); i++) {
                if (alias.equals(aliasAdapter.getItem(i)))
                    return i;
            }
        }
        return -1;
    }

    private void closeDialog(Boolean success)
    {
        if (finishedCallback != null)
            finishedCallback.onCloseDialog(success, mEntry);
        dismiss();
    }

    public interface OnFinishedCallback
    {
        void onCloseDialog(Boolean success, CertificateConfigEntry entry);
    }
}
