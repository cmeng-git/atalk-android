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
package org.atalk.android.gui.login;

import android.os.Bundle;
import android.text.*;
import android.view.*;
import android.widget.*;

import net.java.sip.communicator.service.certificate.CertificateConfigEntry;

import org.atalk.android.R;
import org.atalk.android.gui.util.ViewUtil;

import java.util.Arrays;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;

/**
 * The credentials fragment can be used to retrieve username, password, the "store password" option status, login
 * server overridden option and the server ip:port. Use the arguments to fill the fragment with default values.
 * Supported arguments are:
 * - {@link #ARG_LOGIN} login default text value; editable only if new user creation
 * - {@link #ARG_LOGIN_EDITABLE} <tt>boolean</tt> flag indicating if the login field is editable
 * - {@link #ARG_PASSWORD} password default text value
 * - {@link #ARG_IB_REGISTRATION} "store password" default <tt>boolean</tt> value
 * - {@link #ARG_IB_REGISTRATION} "ibr_registration" default <tt>boolean</tt> value
 * - {@link #ARG_STORE_PASSWORD} "store password" default <tt>boolean</tt> value
 * - {@link #ARG_IS_SERVER_OVERRIDDEN} "Server Overridden" default <tt>boolean</tt> value
 * - {@link #ARG_SERVER_ADDRESS} Server address default text value
 * - {@link #ARG_SERVER_PORT} Server port default text value
 * - {@link #ARG_LOGIN_REASON} login in reason, present last server return exception if any
 *
 * @author Eng Chong Meng
 */
public class CredentialsFragment extends Fragment
{
    /**
     * Pre-entered login argument.
     */
    public static final String ARG_LOGIN = "login";

    /**
     * Pre-entered password argument.
     */
    public static final String ARG_PASSWORD = "password";

    /**
     * Pre-entered dnssecMode argument.
     */
    public static final String ARG_DNSSEC_MODE = "dnssec_mode";

    /**
     * Argument indicating whether the login can be edited.
     */
    public static final String ARG_LOGIN_EDITABLE = "login_editable";

    /**
     * Pre-entered "store password" <tt>boolean</tt> value.
     */
    public static final String ARG_STORE_PASSWORD = "store_pass";

    /**
     * Pre-entered "store password" <tt>boolean</tt> value.
     */
    public static final String ARG_IB_REGISTRATION = "ib_registration";

    /**
     * Show server option for user entry if true " <tt>boolean</tt> value.
     */
    public static final String ARG_IS_SHOWN_SERVER_OPTION = "is_shown_server_option";

    /**
     * Pre-entered "is server overridden" <tt>boolean</tt> value.
     */
    public static final String ARG_IS_SERVER_OVERRIDDEN = "is_server_overridden";

    /**
     * Pre-entered "store server address".
     */
    public static final String ARG_SERVER_ADDRESS = "server_address";

    /**
     * Pre-entered "store server port".
     */
    public static final String ARG_SERVER_PORT = "server_port";

    /**
     * Reason for the login / reLogin.
     */
    public static final String ARG_LOGIN_REASON = "login_reason";

    public static final String ARG_CERT_ID = "cert_id";

    private CheckBox mServerOverrideCheckBox;
    private EditText mServerIpField;
    private EditText mServerPortField;

    private EditText mPasswordField;
    private CheckBox mShowPasswordCheckBox;

    /**
     * {@inheritDoc}
     */
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState)
    {
        Bundle args = getArguments();
        View content = inflater.inflate(R.layout.account_credentials, container, false);

        Spinner spinnerDM = content.findViewById(R.id.dnssecModeSpinner);
        ArrayAdapter<CharSequence> adapterDM = ArrayAdapter.createFromResource(getActivity(),
                R.array.dnssec_Mode_name, R.layout.simple_spinner_item);
        adapterDM.setDropDownViewResource(R.layout.simple_spinner_dropdown_item);
        spinnerDM.setAdapter(adapterDM);

        String dnssecMode = args.getString(ARG_DNSSEC_MODE);
        String[] dnssecModeValues = getResources().getStringArray(R.array.dnssec_Mode_value);
        int sPos = Arrays.asList(dnssecModeValues).indexOf(dnssecMode);
        spinnerDM.setSelection(sPos);

        EditText mUserNameEdit = content.findViewById(R.id.username);
        mUserNameEdit.setText(args.getString(ARG_LOGIN));
        mUserNameEdit.setEnabled(args.getBoolean(ARG_LOGIN_EDITABLE, true));

        mShowPasswordCheckBox = content.findViewById(R.id.show_password);

        mPasswordField = content.findViewById(R.id.password);
        mPasswordField.setText(args.getString(ARG_PASSWORD));
        // ViewUtil.setTextViewValue(content, R.id.password, args.getString(ARG_PASSWORD));

        ViewUtil.setCompoundChecked(content, R.id.store_password, args.getBoolean(ARG_STORE_PASSWORD, true));
        ViewUtil.setCompoundChecked(content, R.id.ib_registration, args.getBoolean(ARG_IB_REGISTRATION, false));

        ImageView showCert = content.findViewById(R.id.showCert);
        String clientCertId = args.getString(ARG_CERT_ID);
        if ((clientCertId == null) || clientCertId.equals(CertificateConfigEntry.CERT_NONE.toString())) {
            showCert.setVisibility(View.GONE);
        }

        mServerOverrideCheckBox = content.findViewById(R.id.serverOverridden);
        mServerIpField = content.findViewById(R.id.serverIpField);
        mServerPortField = content.findViewById(R.id.serverPortField);

        boolean isShownServerOption = args.getBoolean(ARG_IS_SHOWN_SERVER_OPTION, false);
        if (isShownServerOption) {
            boolean isServerOverridden = args.getBoolean(ARG_IS_SERVER_OVERRIDDEN, false);
            ViewUtil.setCompoundChecked(content, R.id.serverOverridden, isServerOverridden);
            mServerIpField.setText(args.getString(ARG_SERVER_ADDRESS));
            mServerPortField.setText(args.getString(ARG_SERVER_PORT));
            updateViewVisibility(isServerOverridden);
        }
        else {
            mServerIpField.setVisibility(View.GONE);
            mServerPortField.setVisibility(View.GONE);
        }

        // make xml text more human readable and link clickable
        TextView reasonField = content.findViewById(R.id.reason_field);
        String xmlText = args.getString(ARG_LOGIN_REASON);
        if (!TextUtils.isEmpty(xmlText)) {
            Spanned loginReason = Html.fromHtml(xmlText.replace("\n", "<br/>"));
            reasonField.setText(loginReason);
        }

        initializeViewListeners();
        return content;
    }

    private void initializeViewListeners()
    {
        mShowPasswordCheckBox.setOnCheckedChangeListener((buttonView, isChecked)
                -> ViewUtil.showPassword(mPasswordField, isChecked));

        mServerOverrideCheckBox.setOnCheckedChangeListener(
                (buttonView, isChecked) -> updateViewVisibility(isChecked));
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
}
