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

import android.os.Bundle;
import android.text.Html;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.ListView;
import android.widget.TextView;

import androidx.appcompat.app.AlertDialog;

import net.java.sip.communicator.impl.certificate.CertificateServiceImpl;
import net.java.sip.communicator.plugin.jabberaccregwizz.JabberAccountRegistrationActivator;
import net.java.sip.communicator.service.protocol.AccountID;
import net.java.sip.communicator.service.protocol.OperationSetConnectionInfo;
import net.java.sip.communicator.service.protocol.OperationSetTLS;
import net.java.sip.communicator.service.protocol.ProtocolProviderService;
import net.java.sip.communicator.service.protocol.TransportProtocol;
import net.java.sip.communicator.util.account.AccountUtils;

import org.apache.commons.lang3.StringUtils;
import org.atalk.android.R;
import org.atalk.android.aTalkApp;
import org.atalk.service.osgi.OSGiActivity;

import java.net.InetSocketAddress;
import java.security.cert.Certificate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Hashtable;
import java.util.List;
import java.util.Map;

/**
 * Setting screen which displays protocolProvider connection info and servers SSL Certificates.
 * Unregistered accounts without any approved certificates are not shown.
 * a. Short click to display the SSL certificate for registered account.
 * b. Long Click to delete any manually approved self signed SSL certificates if any.
 *
 * @author Eng Chong Meng
 */
public class ConnectionInfo extends OSGiActivity
{
    /**
     * List of AccountId to its array of manual approved self signed certificates
     */
    private final Map<AccountID, List<String>> certificateEntry = new Hashtable<>();

    /*
     * Adapter used to display connection info and SSL certificates for all protocolProviders.
     */
    private ConnectionInfoAdapter mCIAdapter;

    private CertificateServiceImpl cvs;

    /*
     * X509 SSL Certificate view on dialog window
     */
    private X509CertificateView viewCertDialog;

    private AlertDialog deleteDialog;

    /**
     * {@inheritDoc}
     */
    @Override
    protected void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.list_layout);
        ListView providerKeysList = findViewById(R.id.list);

        cvs = (CertificateServiceImpl) JabberAccountRegistrationActivator.getCertificateService();
        List<AccountID> accountIDS = initCertificateEntry();

        this.mCIAdapter = new ConnectionInfoAdapter(accountIDS);
        providerKeysList.setAdapter(mCIAdapter);

        providerKeysList.setOnItemClickListener((parent, view, position, id)
                -> showSslCertificate(position));

        providerKeysList.setOnItemLongClickListener((parent, view, position, id) -> {
            showSslCertificateDeleteAlert(position);
            return true;
        });
    }

    /*
     * Dismissed any opened dialog to avoid window leaks on rotation
     */
    @Override
    protected void onPause()
    {
        super.onPause();
        if (viewCertDialog != null && viewCertDialog.isShowing()) {
            viewCertDialog.dismiss();
            viewCertDialog = null;
        }
        if (deleteDialog != null && deleteDialog.isShowing()) {
            deleteDialog.dismiss();
            deleteDialog = null;
        }
    }

    /**
     * Init and populate AccountIDs with all registered accounts or
     * account has manual approved self-signed certificate.
     *
     * @return a list of all accountIDs for display list
     */
    private List<AccountID> initCertificateEntry()
    {
        certificateEntry.clear();
        final List<String> certEntries = cvs.getAllServerAuthCertificates();

        // List of the accounts for display
        final List<AccountID> accountIDS = new ArrayList<>();

        // List of all local stored accounts
        Collection<AccountID> userAccounts = AccountUtils.getStoredAccounts();

        /*
         * Iterate all the local stored accounts; add to display list if there are associated user approved
         * certificates, or the account is registered for SSL certificate display.
         */
        for (AccountID accountId : userAccounts) {
            ProtocolProviderService pps = accountId.getProtocolProvider();
            String serviceName = accountId.getService();
            List<String> sslCerts = new ArrayList<>();
            for (String certEntry : certEntries) {
                if (certEntry.contains(serviceName)) {
                    sslCerts.add(certEntry);
                }
            }

            if ((sslCerts.size() != 0) || ((pps != null) && pps.isRegistered())) {
                accountIDS.add(accountId);
                certificateEntry.put(accountId, sslCerts);

                // remove any assigned certs from certEntries
                for (String cert : sslCerts) {
                    certEntries.remove(cert);
                }
            }
        }
        return accountIDS;
    }

    /**
     * Displays SSL Certificate information.
     * Invoked when user short clicks a link in the editor pane.
     *
     * @param position the position of <code>SSL Certificate</code> in adapter's list which will be displayed.
     */
    public void showSslCertificate(int position)
    {
        AccountID accountId = mCIAdapter.getItem(position);
        ProtocolProviderService pps = accountId.getProtocolProvider();
        if ((pps != null) && pps.isRegistered()) {
            OperationSetTLS opSetTLS = pps.getOperationSet(OperationSetTLS.class);
            Certificate[] chain = opSetTLS.getServerCertificates();

            if (chain != null) {
                viewCertDialog = new X509CertificateView(this, chain);
                viewCertDialog.show();
            }
            else
                aTalkApp.showToastMessage(aTalkApp.getResString(R.string.callinfo_tls_certificate_content) + ": null!");
        }
        else {
            aTalkApp.showToastMessage(R.string.certconfig_show_cert_exception, accountId);
        }
    }

    /**
     * Displays alert asking user if he wants to delete the selected SSL Certificate. (Long click)
     * Delete both the serviceName certificate and the _xmpp-client.serviceName
     *
     * @param position the position of <code>SSL Certificate</code> in adapter's list which has to be used in the alert.
     */
    private void showSslCertificateDeleteAlert(int position)
    {
        AccountID accountId = mCIAdapter.getItem(position);
        List<String> certs = certificateEntry.get(accountId);
        // Just display the SSL certificate info if none to delete
        if (certs.size() == 0) {
            showSslCertificate(position);
            return;
        }

        final String bareJid = accountId.getAccountJid();
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle(R.string.settings_ssl_certificate_remove)
                .setMessage(getString(R.string.settings_ssl_certificate_purge, bareJid))
                .setPositiveButton(R.string.yes, (dialog, which) -> {
                    for (String certEntry : certs)
                        cvs.removeCertificateEntry(certEntry);

                    // Update the adapter Account list after a deletion.
                    mCIAdapter.setAccountIDs(initCertificateEntry());
                    dialog.dismiss();
                })
                .setNegativeButton(R.string.no, (dialog, which) -> dialog.dismiss());
        deleteDialog = builder.create();
        deleteDialog.show();
    }

    /**
     * Constructs the connection info text.
     * Do not use ISAddress.getHostName(); this may make a network access for a reverse IP lookup
     * and cause NetworkOnMainThreadException
     */
    private String loadDetails(ProtocolProviderService pps)
    {
        final StringBuilder buff = new StringBuilder();
        buff.append("<html><body>");

        // Protocol name
        buff.append(getItemString(getString(R.string.protocol), pps.getProtocolName()));

        // Server address and port
        final OperationSetConnectionInfo opSetConnInfo = pps.getOperationSet(OperationSetConnectionInfo.class);
        if (opSetConnInfo != null) {
            InetSocketAddress ISAddress = opSetConnInfo.getServerAddress();
            // buff.append(getItemString(getString(R.string.address),
            //      (ISAddress == null) ? "" : ISAddress.getHostName()));
            buff.append(getItemString(getString(R.string.address),
                    (ISAddress == null) ? "" : ISAddress.getHostString()));
            buff.append(getItemString(getString(R.string.port),
                    (ISAddress == null) ? "" : String.valueOf(ISAddress.getPort())));
        }

        // Transport protocol
        TransportProtocol preferredTransport = pps.getTransportProtocol();
        if (preferredTransport != TransportProtocol.UNKNOWN)
            buff.append(getItemString(getString(R.string.callinfo_call_transport), preferredTransport.toString()));

        // TLS information
        final OperationSetTLS opSetTLS = pps.getOperationSet(OperationSetTLS.class);
        if (opSetTLS != null) {
            buff.append(getItemString(getString(R.string.callinfo_tls_protocol), opSetTLS.getProtocol()));
            buff.append(getItemString(getString(R.string.callinfo_tls_cipher_suite), opSetTLS.getCipherSuite()));

            buff.append("<b><u><font color=\"aqua\">")
                    .append(getString(R.string.callinfo_view_certificate))
                    .append("</font></u></b>");
        }
        buff.append("</body></html>");
        return buff.toString();
    }

    /**
     * Returns an HTML string corresponding to the given labelText and infoText,
     * that could be easily added to the information text pane.
     *
     * @param labelText the label text that would be shown in bold
     * @param infoText the info text that would be shown in plain text
     * @return the newly constructed HTML string
     */
    private String getItemString(String labelText, String infoText)
    {
        if (StringUtils.isNotEmpty(infoText)) {
            if (infoText.contains("TLS"))
                infoText = "<small>" + infoText + "</small>";
        }
        else
            infoText = "";

        return "&#8226; <b>" + labelText + "</b> : " + infoText + "<br/>";
    }

    /**
     * Adapter which displays Connection Info for list of <code>ProtocolProvider</code>s.
     */
    class ConnectionInfoAdapter extends BaseAdapter
    {
        /**
         * List of <code>AccountID</code> for which the connection info and certificates are being displayed.
         */
        private List<AccountID> accountIDs;

        /**
         * Creates a new instance of <code>SslCertificateListAdapter</code>.
         *
         * @param accountIDS the list of <code>AccountId</code>s for which connection info and
         * certificates will be displayed by this adapter.
         */
        ConnectionInfoAdapter(List<AccountID> accountIDS)
        {
            this.accountIDs = accountIDS;
        }

        /**
         * Call to update the new List item; notify data change after update
         *
         * @param accountIDS the list of <code>AccountId</code>s for which connection info and
         * certificates will be displayed by this adapter.
         */
        public void setAccountIDs(List<AccountID> accountIDS)
        {
            this.accountIDs = accountIDS;
            notifyDataSetChanged();
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public int getCount()
        {
            return accountIDs.size();
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public AccountID getItem(int position)
        {
            return accountIDs.get(position);
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public long getItemId(int position)
        {
            return position;
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public View getView(int position, View convertView, ViewGroup parent)
        {
            // Keeps reference to avoid future findViewById()
            CIViewHolder ciViewHolder;
            if (convertView == null) {
                convertView = getLayoutInflater().inflate(R.layout.connection_info_list_row, parent, false);
                ciViewHolder = new CIViewHolder();
                ciViewHolder.protocolService = convertView.findViewById(R.id.protocolProvider);
                ciViewHolder.connectionInfo = convertView.findViewById(R.id.connectionInfo);
                convertView.setTag(ciViewHolder);
            }
            else {
                ciViewHolder = (CIViewHolder) convertView.getTag();
            }

            AccountID accountId = getItem(position);
            String accountName = "<u>" + accountId + "</u>";
            ciViewHolder.protocolService.setText(Html.fromHtml(accountName));

            String detailInfo;
            ProtocolProviderService pps = accountId.getProtocolProvider();
            if (pps != null) {
                detailInfo = loadDetails(accountId.getProtocolProvider());
            }
            else {
                detailInfo = getString(R.string.account_unregistered, "&#8226; ");
            }

            ciViewHolder.connectionInfo.setText(Html.fromHtml(detailInfo));
            return convertView;
        }
    }

    private static class CIViewHolder
    {
        TextView protocolService;
        TextView connectionInfo;
    }
}
