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
import android.os.Bundle;
import android.view.*;
import android.widget.*;

import net.java.sip.communicator.service.certificate.CertificateConfigEntry;
import net.java.sip.communicator.service.certificate.CertificateService;

import org.atalk.android.R;
import org.atalk.service.osgi.OSGiFragment;

import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.security.Security;
import java.util.*;

import androidx.fragment.app.FragmentTransaction;
import timber.log.Timber;

/**
 * Advanced configuration form to define client TLS certificate templates.
 *
 * @author Eng Chong Meng
 */
public class TLS_Configuration extends OSGiFragment
        implements View.OnClickListener, CompoundButton.OnCheckedChangeListener,
        AdapterView.OnItemSelectedListener, PropertyChangeListener, CertConfigEntryDialog.OnFinishedCallback
{
    private CertificateService cvs;

    /**
     * Certificate spinner list for selection
     */
    private final List<String> mCertList = new ArrayList<>();
    private ArrayAdapter<String> certAdapter;

    private Spinner certSpinner;
    private CertificateConfigEntry mCertEntry = null;

    /**
     * A map of <row, CertificateConfigEntry>
     */
    private final Map<Integer, CertificateConfigEntry> mCertEntryList = new LinkedHashMap<>();

    private CheckBox chkEnableOcsp;

    private Button cmdRemove;
    private Button cmdEdit;

    private Context mContext;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState)
    {
        mContext = getContext();
        cvs = CertConfigActivator.getCertService();
        CertConfigActivator.getConfigService().addPropertyChangeListener(this);

        View content = inflater.inflate(R.layout.cert_tls_config, container, false);

        CheckBox chkEnableRevocationCheck = content.findViewById(R.id.cb_crl);
        chkEnableRevocationCheck.setOnCheckedChangeListener(this);

        chkEnableOcsp = content.findViewById(R.id.cb_ocsp);
        chkEnableOcsp.setOnCheckedChangeListener(this);

        certSpinner = content.findViewById(R.id.cboCert);
        initCertSpinner();

        Button mAdd = content.findViewById(R.id.cmd_add);
        mAdd.setOnClickListener(this);

        cmdRemove = content.findViewById(R.id.cmd_remove);
        cmdRemove.setOnClickListener(this);

        cmdEdit = content.findViewById(R.id.cmd_edit);
        cmdEdit.setOnClickListener(this);

        return content;
    }

    private void initCertSpinner()
    {
        initCertList();
        certAdapter = new ArrayAdapter<>(mContext, R.layout.simple_spinner_item, mCertList);
        certAdapter.setDropDownViewResource(R.layout.simple_spinner_dropdown_item);
        certSpinner.setAdapter(certAdapter);
        certSpinner.setOnItemSelectedListener(this);
    }

    private void initCertList()
    {
        mCertList.clear();
        List<CertificateConfigEntry> certEntries = cvs.getClientAuthCertificateConfigs();
        for (int idx = 0; idx < certEntries.size(); idx++) {
            CertificateConfigEntry entry = certEntries.get(idx);
            mCertList.add(entry.toString());
            mCertEntryList.put(idx, entry);
        }
    }

    @Override
    public void onClick(View v)
    {
        CertConfigEntryDialog dialog;
        FragmentTransaction ft = getParentFragmentManager().beginTransaction();
        ft.addToBackStack(null);

        switch (v.getId()) {
            case R.id.cmd_add:
                dialog = CertConfigEntryDialog.getInstance(CertificateConfigEntry.CERT_NONE, this);
                dialog.show(ft, "CertConfigEntry");
                break;

            case R.id.cmd_remove:
                if (mCertEntry != null) {
                    Timber.d("Certificate Entry removed: %s", mCertEntry.getId());
                    CertConfigActivator.getCertService().removeClientAuthCertificateConfig(mCertEntry.getId());
                }
                break;

            case R.id.cmd_edit:
                if (mCertEntry != null) {
                    dialog = CertConfigEntryDialog.getInstance(mCertEntry, this);
                    dialog.show(ft, "CertConfigEntry");
                }
                break;
        }
    }

    @Override
    public void onCheckedChanged(CompoundButton buttonView, boolean isChecked)
    {
        String enabled = Boolean.valueOf(isChecked).toString();
        switch (buttonView.getId()) {
            case R.id.cb_crl:
                CertConfigActivator.getConfigService().setProperty(
                        CertificateService.PNAME_REVOCATION_CHECK_ENABLED, isChecked);

                System.setProperty(CertificateService.SECURITY_CRLDP_ENABLE, enabled);
                System.setProperty(CertificateService.SECURITY_SSL_CHECK_REVOCATION, enabled);
                chkEnableOcsp.setEnabled(isChecked);
                break;

            case R.id.cb_ocsp:
                CertConfigActivator.getConfigService().setProperty(
                        CertificateService.PNAME_OCSP_ENABLED, isChecked);
                Security.setProperty(CertificateService.SECURITY_OCSP_ENABLE, enabled);
                break;
        }
    }

    @Override
    public void onItemSelected(AdapterView<?> adapter, View view, int pos, long id)
    {
        if (adapter.getId() == R.id.cboCert) {
            certSpinner.setSelection(pos);

            mCertEntry = mCertEntryList.get(pos);
            cmdRemove.setEnabled(true);
            cmdEdit.setEnabled(true);
        }
    }

    @Override
    public void onNothingSelected(AdapterView<?> parent)
    {
    }

    @Override
    public void propertyChange(PropertyChangeEvent evt)
    {
        if (evt.getPropertyName().startsWith(CertificateService.PNAME_CLIENTAUTH_CERTCONFIG_BASE)) {
            initCertList();
            certAdapter.notifyDataSetChanged();
        }
    }

    @Override
    public void onCloseDialog(Boolean success, CertificateConfigEntry entry)
    {
        if (success) {
            CertConfigActivator.getCertService().setClientAuthCertificateConfig(entry);
        }
    }
}
