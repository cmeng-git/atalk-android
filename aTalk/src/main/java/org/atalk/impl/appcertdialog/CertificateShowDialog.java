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
package org.atalk.impl.appcertdialog;

import android.app.Activity;
import android.app.Dialog;
import android.content.Context;
import android.os.Bundle;
import android.view.View;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.widget.CompoundButton;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;

import org.atalk.android.R;
import org.atalk.android.gui.dialogs.BaseDialogFragment;

import java.security.cert.Certificate;

/**
 * The dialog that displays certificate details. It allows user to mark the certificate as "always trusted".
 * The dialog details are created dynamically in html format. That's because it's certificate implementation
 * dependent. Parent <code>Activity</code> must implement <code>CertInfoDialogListener</code>.
 *
 * @author Eng Chong Meng
 */
public class CertificateShowDialog extends BaseDialogFragment {
    /**
     * Argument holding request id used to retrieve dialog model.
     */
    private static final String ARG_REQ_ID = "request_id";

    /**
     * Parent <code>Activity</code> listening for this dialog results.
     */
    private CertInfoDialogListener listener;

    /**
     * {@inheritDoc}
     */
    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        final Long request = getArguments().getLong(ARG_REQ_ID);

        VerifyCertDialog certDialog = CertificateDialogActivator.impl.retrieveDialog(request);
        if (certDialog == null)
            throw new RuntimeException("No dialog model found for: " + request);

        // Alert view and its title
        AlertDialog.Builder b = new AlertDialog.Builder(getActivity());
        b.setTitle(certDialog.getTitle());

        // Certificate content in html format
        View contentView = getActivity().getLayoutInflater().inflate(R.layout.cert_info, null);
        WebView certView = contentView.findViewById(R.id.certificateInfo);
        WebSettings settings = certView.getSettings();
        settings.setDefaultFontSize(10);
        settings.setDefaultFixedFontSize(10);
        settings.setBuiltInZoomControls(true);

        Certificate cert = certDialog.getCertificate();
        X509CertificateView certInfo = new X509CertificateView(getActivity());
        String certHtml = certInfo.toString(cert);
        certView.loadData(certHtml, "text/html", "utf-8");

        // Always trust checkbox
        CompoundButton alwaysTrustBtn = contentView.findViewById(R.id.alwaysTrust);

        // Updates always trust property of dialog model
        alwaysTrustBtn.setOnCheckedChangeListener((buttonView, isChecked) -> {
            CertificateDialogActivator.getDialog(request).setAlwaysTrust(isChecked);
        });

        // contentView.findViewById(R.id.dummyView).setVisibility(View.GONE);
        AlertDialog alertDialog = b.setView(contentView).create();
        return alertDialog;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void onAttach(@NonNull Context context) {
        super.onAttach(context);
        this.listener = (CertInfoDialogListener) context;
    }

    /**
     * Method fired when continue button is clicked.
     *
     * @param v button's <code>View</code>.
     */
    public void onContinueClicked(View v) {
        listener.onDialogResult(true);
        dismiss();
    }

    /**
     * Method fired when cancel button is clicked.
     *
     * @param v button's <code>View</code>.
     */
    public void onCancelClicked(View v) {
        listener.onDialogResult(false);
        dismiss();
    }

    /**
     * Creates new instance of <code>CertificateInfoDialog</code> parametrized with given <code>requestId</code>.
     *
     * @param requestId identifier of dialog model managed by <code>CertificateDialogServiceImpl</code>
     * @return new instance of <code>CertificateInfoDialog</code> parametrized with given <code>requestId</code>.
     */
    static public CertificateShowDialog createFragment(long requestId) {
        CertificateShowDialog dialog = new CertificateShowDialog();
        Bundle args = new Bundle();
        args.putLong(ARG_REQ_ID, requestId);
        dialog.setArguments(args);
        return dialog;
    }

    /**
     * Interface used to pass dialog result to parent <code>Activity</code>.
     */
    public interface CertInfoDialogListener {
        /**
         * Fired when dialog is dismissed. Passes the result as an argument.
         *
         * @param continueAnyway <code>true</code> if continue anyway button was pressed, <code>false</code>
         * means that the dialog was discarded or cancel button was pressed.
         */
        void onDialogResult(boolean continueAnyway);
    }
}
