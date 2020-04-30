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
package org.atalk.impl.androidcertdialog;

import android.annotation.SuppressLint;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.os.Bundle;

import net.java.sip.communicator.impl.certificate.CertificateServiceImpl;
import net.java.sip.communicator.plugin.jabberaccregwizz.JabberAccountRegistrationActivator;

import org.atalk.android.R;
import org.atalk.service.osgi.OSGiActivity;

import java.util.*;

/**
 * Allow user to remove any manually approved certificate
 *
 * @author Eng Chong Meng
 */
public class CertificateDeleteDialog extends OSGiActivity implements DialogInterface.OnDismissListener
{
    private boolean[] checkedItems;
    private final Map<String, String> certificateMap = new Hashtable<>();
    private final List<String> certificates = new ArrayList<>();
    private CertificateServiceImpl cvs;

    /**
     * {@inheritDoc}
     */
    @Override
    protected void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);
        cvs = (CertificateServiceImpl) JabberAccountRegistrationActivator.getCertificateService();

        if (cvs != null) {
            List<String> certEntries = cvs.getAllServerAuthCertificates();
            for (String certEntry : certEntries) {
                int index = (CertificateServiceImpl.PNAME_CERT_TRUST_PREFIX + CertificateServiceImpl.CERT_TRUST_PARAM_SUBFIX).length();
                certificateMap.put(certEntry.substring(index), certEntry);
                certificates.add(certEntry.substring(index));
            }
        }

        checkedItems = new boolean[certificateMap.size()];
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle(R.string.service_gui_settings_SSL_CERTIFICATE_DIALOG_TITLE);
        builder.setMultiChoiceItems(certificates.toArray(new CharSequence[0]),
                checkedItems, (dialog, which, isChecked) -> {
                    checkedItems[which] = isChecked;
                    final AlertDialog multiChoiceDialog = (AlertDialog) dialog;
                    for (boolean item : checkedItems) {
                        if (item) {
                            multiChoiceDialog.getButton(DialogInterface.BUTTON_POSITIVE).setEnabled(true);
                            return;
                        }
                    }
                    multiChoiceDialog.getButton(DialogInterface.BUTTON_POSITIVE).setEnabled(false);
                });

        builder.setNegativeButton(R.string.service_gui_CANCEL, (dialog, which) -> finish());

        builder.setPositiveButton(R.string.crypto_dialog_button_DELETE, (dialog, which) -> {
            // proceed to delete only first selected item. Remainders handle by onDismiss() of dialog
            for (int i = 0; i < checkedItems.length; ++i) {
                if (checkedItems[i]) {
                    showCertificateDeleteAlert(certificates.get(i));
                    checkedItems[i] = false;
                    break;
                }
            }
        });
        AlertDialog dialog = builder.create();
        dialog.setCancelable(false);
        dialog.setCanceledOnTouchOutside(false);
        dialog.show();
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(false);
    }

    /**
     * Proceed to delete the next selected items in the remainder list.
     * Exit only when all selected items has been processed
     *
     * @param dialog dismissed dialog
     */
    @Override
    public void onDismiss(DialogInterface dialog)
    {
        for (int i = 0; i < checkedItems.length; ++i) {
            if (checkedItems[i]) {
                showCertificateDeleteAlert(certificates.get(i));
                checkedItems[i] = false;
                return;
            }
        }
        finish();
    }

    /**
     * Displays alert asking user if he wants to delete the selected SSL Certificate.
     * setOnDismissListener => API-17. Old android OS will only delete the first selected item
     *
     * @param trustFor the ID of <tt>SSL Certificate</tt> to be deleted.
     */
    @SuppressLint("NewApi")
    private void showCertificateDeleteAlert(final String trustFor)
    {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle(R.string.service_gui_settings_SSL_CERTIFICATE_DIALOG_TITLE)
                .setMessage(getString(R.string.service_gui_settings_SSL_CERTIFICATE_DELETE, trustFor))
                .setPositiveButton(R.string.service_gui_PROCEED, (dialog, which) -> {
                    cvs.removeCertificateEntry(certificateMap.get(trustFor));
                    dialog.dismiss();
                })
                .setNegativeButton(R.string.service_gui_CANCEL, (dialog, which) -> dialog.dismiss())
                .setOnDismissListener(this);

        AlertDialog confirmDialog = builder.create();
        confirmDialog.show();
    }
}
