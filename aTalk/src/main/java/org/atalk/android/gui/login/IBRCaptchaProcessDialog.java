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

import android.app.Dialog;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.*;
import android.text.Editable;
import android.text.TextUtils;
import android.util.DisplayMetrics;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.*;

import net.java.sip.communicator.impl.protocol.jabber.JabberActivator;
import net.java.sip.communicator.impl.protocol.jabber.ProtocolProviderServiceJabberImpl;
import net.java.sip.communicator.service.protocol.AccountID;
import net.java.sip.communicator.service.protocol.globalstatus.GlobalStatusEnum;
import net.java.sip.communicator.service.protocol.globalstatus.GlobalStatusService;

import org.apache.commons.lang3.StringUtils;
import org.atalk.android.R;
import org.atalk.android.aTalkApp;
import org.atalk.android.gui.AndroidGUIActivator;
import org.atalk.android.gui.util.ViewUtil;
import org.jivesoftware.smack.*;
import org.jivesoftware.smack.packet.StanzaError;
import org.jivesoftware.smack.packet.StanzaError.Condition;
import org.jivesoftware.smack.util.Async;
import org.jivesoftware.smackx.bob.element.BoBExt;
import org.jivesoftware.smackx.captcha.packet.CaptchaExtension;
import org.jivesoftware.smackx.iqregisterx.AccountManager;
import org.jivesoftware.smackx.iqregisterx.packet.Registration;
import org.jivesoftware.smackx.xdata.FormField;
import org.jivesoftware.smackx.xdata.FormField.Type;
import org.jivesoftware.smackx.xdata.TextSingleFormField;
import org.jivesoftware.smackx.xdata.packet.DataForm;
import org.jxmpp.jid.parts.Localpart;
import org.jxmpp.util.XmppStringUtils;

import java.io.*;
import java.net.URL;
import java.util.*;

import timber.log.Timber;

/**
 * The dialog pops up when the user account login return with "not-authorized" i.e. not
 * registered on server, and user has select the InBand Registration option.
 *
 * The IBRegistration supports Form submission with optional captcha challenge,
 * and the bare attributes format method
 *
 * @author Eng Chong Meng
 */
public class IBRCaptchaProcessDialog extends Dialog
{
    /**
     * Listens for connection closes or errors.
     */
    private JabberConnectionListener connectionListener;

    private ProtocolProviderServiceJabberImpl mPPS;
    private XMPPConnection mConnection;

    // Map contains extra form field label and variable not in static layout
    private Map<String, String> varMap = new HashMap<>();

    // The layout container to add the extra form fields
    private LinearLayout entryFields;

    private EditText mCaptchaText;
    private EditText mPasswordField;
    private CheckBox mServerOverrideCheckBox;
    private EditText mServerIpField;
    private EditText mServerPortField;
    private TextView mReason;

    private ImageView mImageView;
    private CheckBox mShowPasswordCheckBox;

    private Button mSubmitButton;
    private Button mCancelButton;
    private Button mOKButton;

    private AccountID mAccountId;
    private Bitmap mCaptcha;
    private Context mContext;
    private DataForm mDataForm;
    private DataForm.Builder formBuilder;
    private String mPassword;
    private String mReasonText;

    /**
     * Constructor for the <tt>Captcha Request Dialog</tt> for passing the dialog parameters
     *
     * @param context the context to which the dialog belongs
     * @param pps the protocol provider service that offers the service
     * @param accountId the AccountID of the login user request for IBRegistration
     */
    public IBRCaptchaProcessDialog(Context context, ProtocolProviderServiceJabberImpl pps, AccountID accountId, String pwd)
    {
        super(context);
        mContext = context;
        mPPS = pps;
        mConnection = pps.getConnection();
        mAccountId = accountId;
        mPassword = pwd;
        mReasonText = aTalkApp.getResString(R.string.captcha_registration_reason);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void onCreate(Bundle savedInstanceState)
    {
        this.setContentView(R.layout.ibr_captcha);
        setTitle(mContext.getString(R.string.captcha_registration_request));

        EditText mUserNameField = this.findViewById(R.id.username);
        mUserNameField.setText(mAccountId.getUserID());
        mUserNameField.setEnabled(false);

        mPasswordField = this.findViewById(R.id.password);
        mShowPasswordCheckBox = this.findViewById(R.id.show_password);
        mServerOverrideCheckBox = this.findViewById(R.id.serverOverridden);
        mServerIpField = this.findViewById(R.id.serverIpField);
        mServerPortField = this.findViewById(R.id.serverPortField);
        mImageView = this.findViewById(R.id.captcha);
        mCaptchaText = this.findViewById(R.id.input);
        mReason = this.findViewById(R.id.reason_field);

        mSubmitButton = this.findViewById(R.id.button_Submit);
        mSubmitButton.setVisibility(View.VISIBLE);
        mOKButton = this.findViewById(R.id.button_OK);
        mOKButton.setVisibility(View.GONE);
        mCancelButton = this.findViewById(R.id.button_Cancel);

        if (connectionListener == null) {
            connectionListener = new JabberConnectionListener();
            mConnection.addConnectionListener(connectionListener);
        }

        // Prevents from closing the dialog on outside touch or Back Key
        setCanceledOnTouchOutside(false);
        setCancelable(false);

        UpdateDialogContent();
        if (initIBRRegistration()) {
            mReason.setText(mReasonText);
            updateEntryFields();
            showCaptchaContent();
            initializeViewListeners();
        }
        // unable to start IBR registration on server
        else {
            onIBRServerFailure();
        }
    }

    /*
     * Update IBRegistration dialog content with the initial user supplied information.
     */
    private void UpdateDialogContent()
    {
        mPasswordField.setText(mPassword);
        boolean isServerOverridden = mAccountId.isServerOverridden();
        mServerOverrideCheckBox.setChecked(isServerOverridden);

        mServerIpField.setText(mAccountId.getServerAddress());
        mServerPortField.setText(mAccountId.getServerPort());
        updateViewVisibility(isServerOverridden);
    }

    /**
     * Start the InBand Registration for the accountId on the defined XMPP connection by pps.
     * Registration can either be:
     * - simple username and password or
     * - Form With captcha protection with embedded captcha image if available, else the
     * image is retrieved from the given url in the form.
     *
     * Return <tt>true</tt> if IBRegistration is supported and info is available
     */
    private boolean initIBRRegistration()
    {
        // NetworkOnMainThreadException if attempt to reconnect in UI thread; so return if no connection, else deadlock.
        if (!mConnection.isConnected())
            return false;

        try {
            // Check and proceed only if IBRegistration is supported by the server
            AccountManager accountManager = AccountManager.getInstance(mConnection);
            if (accountManager.isSupported()) {
                Registration info = accountManager.getRegistrationInfo();
                if (info != null) {
                    // do not proceed if dataForm is null
                    DataForm dataForm = info.getDataForm();
                    if (dataForm == null)
                        return false;

                    mDataForm = dataForm;
                    BoBExt bob = info.getBoB();
                    if (bob != null) {
                        byte[] bytData = bob.getBoBData().getContent();
                        InputStream stream = new ByteArrayInputStream(bytData);
                        mCaptcha = BitmapFactory.decodeStream(stream);
                    }
                    // Get the captcha image from the url link if bob is not available
                    else {
                        FormField urlField = dataForm.getField("url");
                        if (urlField != null) {
                            String urlString = urlField.getFirstValue();
                            getCaptcha(urlString);
                        }
                    }
                }
                // Not user Form, so setup to use plain attributes login method instead.
                else {
                    mDataForm = null;
                    mCaptcha = null;
                }
                return true;
            }
        } catch (InterruptedException | XMPPException | SmackException e) {
            String errMsg = e.getMessage();
            StanzaError xmppError = StanzaError.from(Condition.not_authorized, errMsg).build();
            mPPS.accountIBRegistered.reportFailure(new XMPPException.XMPPErrorException(null, xmppError));
            showResult();
        }
        return false;
    }

    /**
     * Add extra Form fields if there are not in the static layout
     */
    private void updateEntryFields()
    {
        entryFields = findViewById(R.id.entry_fields);
        LayoutInflater inflater = LayoutInflater.from(mContext);

        if (mDataForm != null) {
            List<FormField> formFields = mDataForm.getFields();
            for (FormField formField : formFields) {
                Type type = formField.getType();
                String var = formField.getVariable();

                if ((type == Type.hidden) || (type == Type.fixed))
                    continue;

                String label = formField.getLabel();
                String value = formField.getFirstValue();

                if (var.equals("url")) {
                    ((TextView) findViewById(R.id.url_label)).setText(label);
                    TextView url_link = findViewById(R.id.url_link);
                    url_link.setText(value);
                    url_link.setOnClickListener(v -> {
                        getCaptcha(value);
                    });
                }
                else {
                    if (var.equals(CaptchaExtension.USER_NAME) || var.equals(CaptchaExtension.PASSWORD) || var.equals(CaptchaExtension.OCR))
                        continue;

                    LinearLayout fieldEntry = (LinearLayout) inflater.inflate(R.layout.ibr_field_entry_row, null);
                    TextView viewLabel = fieldEntry.findViewById(R.id.field_label);
                    ImageView viewRequired = fieldEntry.findViewById(R.id.star);

                    Timber.w("New entry field: %s = %s", label, var);
                    // Keep copy of the variable field for later extracting the user entered value
                    varMap.put(label, var);

                    viewLabel.setText(label);
                    viewRequired.setVisibility(formField.isRequired() ? View.VISIBLE : View.INVISIBLE);
                    entryFields.addView(fieldEntry);
                }
            }
        }
    }

    /*
     * Update dialog content with the received captcha information for form presentation.
     */
    private void showCaptchaContent()
    {
        new Handler(Looper.getMainLooper()).post(() -> {
            if (mCaptcha != null) {
                findViewById(R.id.captcha_container).setVisibility(View.VISIBLE);
                // Scale the captcha to the display resolution
                DisplayMetrics metrics = mContext.getResources().getDisplayMetrics();
                Bitmap captcha = Bitmap.createScaledBitmap(mCaptcha,
                        (int) (mCaptcha.getWidth() * metrics.scaledDensity),
                        (int) (mCaptcha.getHeight() * metrics.scaledDensity), false);
                mImageView.setImageBitmap(captcha);
                mCaptchaText.setHint(R.string.captcha_hint);
                mCaptchaText.requestFocus();
            }
            else {
                findViewById(R.id.captcha_container).setVisibility(View.GONE);
            }
        });
    }

    /**
     * Fetch the captcha bitmap from the given url link on new thread
     *
     * @param urlString Url link to fetch the captcha
     */
    private void getCaptcha(String urlString)
    {
        new Thread(() -> {
            try {
                if (!TextUtils.isEmpty(urlString)) {
                    URL uri = new URL(urlString);
                    mCaptcha = BitmapFactory.decodeStream(uri.openConnection().getInputStream());
                    showCaptchaContent();
                }
            } catch (IOException e) {
                Timber.e(e, "%s", e.getMessage());
            }
        }).start();
    }

    /**
     * Setup all the dialog buttons' listeners for the required actions on user click
     */
    private void initializeViewListeners()
    {
        mShowPasswordCheckBox.setOnCheckedChangeListener((buttonView, isChecked)
                -> ViewUtil.showPassword(mPasswordField, isChecked));
        mServerOverrideCheckBox.setOnCheckedChangeListener((buttonView, isChecked)
                -> updateViewVisibility(isChecked));
        mImageView.setOnClickListener(v -> mCaptchaText.requestFocus());

        mSubmitButton.setOnClickListener(v -> {
            // server disconnect user if waited for too long
            if (mConnection.isConnected()) {
                if (updateAccount()) {
                    onSubmitClicked();
                    showResult();
                }
            }
        });

        // Re-trigger IBR if user click OK - let login takes over
        mOKButton.setOnClickListener(v -> {
            closeDialog();
            GlobalStatusService globalStatusService = AndroidGUIActivator.getGlobalStatusService();
            globalStatusService.publishStatus(GlobalStatusEnum.ONLINE);
        });

        // Set IBR to false on user cancel. Otherwise may loop in IBR if server returns error
        mCancelButton.setOnClickListener(v -> {
            mAccountId.setIbRegistration(false);
            String errMsg = "InBand registration cancelled by user!";
            StanzaError xmppError = StanzaError.from(Condition.registration_required, errMsg).build();
            mPPS.accountIBRegistered.reportFailure(new XMPPException.XMPPErrorException(null, xmppError));
            closeDialog();
        });
    }

    /**
     * Updated AccountID with the parameters entered by user
     */
    private boolean updateAccount()
    {
        String password;
        Editable pwd = mPasswordField.getText();
        if ((pwd != null) && StringUtils.isNotEmpty(password = pwd.toString())) {
            mAccountId.setPassword(password);
            if (mAccountId.isPasswordPersistent())
                JabberActivator.getProtocolProviderFactory().storePassword(mAccountId, password);
        }
        else {
            mReason.setText(R.string.captcha_registration_pwd_empty);
            return false;
        }

        // Update server override options
        String serverAddress = ViewUtil.toString(mServerIpField);
        String serverPort = ViewUtil.toString(mServerPortField);

        boolean isServerOverride = mServerOverrideCheckBox.isChecked();
        mAccountId.setServerOverridden(isServerOverride);
        if ((isServerOverride) && (serverAddress != null) && (serverPort != null)) {
            mAccountId.setServerAddress(serverAddress);
            mAccountId.setServerPort(serverPort);
        }
        return true;
    }

    /**
     * Handles the <tt>ActionEvent</tt> triggered when one user clicks on the Submit button.
     */
    private void onSubmitClicked()
    {
        // Server will end connection on wait timeout due to user no response
        if ((mConnection != null) && mConnection.isConnected()) {
            AccountManager accountManager = AccountManager.getInstance(mConnection);

            // Only localPart is required
            String userName = XmppStringUtils.parseLocalpart(mAccountId.getUserID());
            Editable pwd = mPasswordField.getText();

            try {
                if (mDataForm != null) {
                    formBuilder = DataForm.builder(DataForm.Type.submit);

                    addFormField(CaptchaExtension.USER_NAME, userName);
                    if (pwd != null) {
                        addFormField(CaptchaExtension.PASSWORD, pwd.toString());
                    }

                    // Add an extra field if any and its value is not empty
                    int varCount = entryFields.getChildCount();
                    for (int i = 0; i < varCount; i++) {
                        final View row = entryFields.getChildAt(i);
                        String label = ViewUtil.toString(row.findViewById(R.id.field_label));
                        if (varMap.containsKey(label)) {
                            String data = ViewUtil.toString(row.findViewById(R.id.field_value));
                            if (data != null)
                                addFormField(varMap.get(label), data);
                        }
                    }

                    // set captcha challenge required info
                    if (mCaptcha != null) {
                        addFormField(FormField.FORM_TYPE, CaptchaExtension.NAMESPACE);

                        formBuilder.addField(mDataForm.getField(CaptchaExtension.CHALLENGE));
                        formBuilder.addField(mDataForm.getField(CaptchaExtension.SID));

                        addFormField(CaptchaExtension.ANSWER, "3");

                        Editable rc = mCaptchaText.getText();
                        if (rc != null) {
                            addFormField(CaptchaExtension.OCR, rc.toString());
                        }
                    }
                    accountManager.createAccount(formBuilder.build());
                }
                else {
                    Localpart username = Localpart.formUnescapedOrNull(userName);
                    accountManager.sensitiveOperationOverInsecureConnection(false);
                    if (pwd != null) {
                        accountManager.createAccount(username, pwd.toString());
                    }
                }

                // if not exception being thrown, then registration is successful. Clear IBR flag on success
                mAccountId.setIbRegistration(false);
                mPPS.accountIBRegistered.reportSuccess();
            } catch (SmackException.NoResponseException | XMPPException.XMPPErrorException
                    | SmackException.NotConnectedException | InterruptedException ex) {
                StanzaError xmppError;
                String errMsg = ex.getMessage();
                String errDetails = "";
                if (ex instanceof XMPPException.XMPPErrorException) {
                    xmppError = ((XMPPException.XMPPErrorException) ex).getStanzaError();
                    errDetails = xmppError.getDescriptiveText();
                }
                else {
                    xmppError = StanzaError.from(Condition.not_acceptable, errMsg).build();
                }
                Timber.e("Exception: %s; %s", errMsg, errDetails);
                if (errMsg.contains("conflict") && errDetails.contains("exists"))
                    mAccountId.setIbRegistration(false);
                mPPS.accountIBRegistered.reportFailure(new XMPPException.XMPPErrorException(null, xmppError));
            }
        }
    }

    /**
     * Add field / value to formBuilder for registration
     *
     * @param name the FormField variable
     * @param value the FormField value
     */
    private void addFormField(String name, String value)
    {
        TextSingleFormField.Builder field = FormField.builder(name);
        field.setValue(value);
        formBuilder.addField(field.build());
    }

    private void closeDialog()
    {
        if (connectionListener != null) {
            mConnection.removeConnectionListener(connectionListener);
            connectionListener = null;
        }
        mConnection = null;
        this.cancel();
    }

    /**
     * Show or hide server address & port
     *
     * @param IsServerOverridden <tt>true</tt> show server address and port field for user entry
     */
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
     * Shows IBR registration result.
     */
    private void showResult()
    {
        String errMsg = null;
        mReasonText = mContext.getString(R.string.captcha_registration_success);
        try {
            XMPPException ex = mPPS.accountIBRegistered.checkIfSuccessOrWait();
            if (ex != null) {
                errMsg = ex.getMessage();
                if (ex instanceof XMPPException.XMPPErrorException) {
                    String errDetails = ((XMPPException.XMPPErrorException) ex).getStanzaError().getDescriptiveText();
                    if (!StringUtils.isEmpty(errDetails))
                        errMsg += "\n" + errDetails;
                }
            }
        } catch (SmackException.NoResponseException | InterruptedException e) {
            errMsg = e.getMessage();
        }
        if (StringUtils.isNotEmpty(errMsg)) {
            mReasonText = mContext.getString(R.string.captcha_registration_fail, errMsg);
        }
        // close connection on error, else throws connectionClosedOnError on timeout
        Async.go(() -> {
            if (mConnection.isConnected())
                ((AbstractXMPPConnection) mConnection).disconnect();
        });

        mReason.setText(mReasonText);
        mSubmitButton.setVisibility(View.GONE);
        mOKButton.setVisibility(View.VISIBLE);
        mCaptchaText.setHint(R.string.captcha_retry);
        mCaptchaText.setEnabled(false);
    }

    // Server failure with start of IBR registration
    private void onIBRServerFailure()
    {
        mReasonText = "InBand registration - Server Error!";
        mImageView.setVisibility(View.GONE);
        mReason.setText(mReasonText);
        mPasswordField.setEnabled(false);
        mCaptchaText.setVisibility(View.GONE);
        mSubmitButton.setEnabled(false);
        mSubmitButton.setAlpha(0.5f);
        mOKButton.setEnabled(false);
        mOKButton.setAlpha(0.5f);
        initializeViewListeners();
    }

    /**
     * Listener for jabber connection events
     */
    private class JabberConnectionListener implements ConnectionListener
    {
        /**
         * Notification that the connection was closed normally.
         */
        public void connectionClosed()
        {
        }

        /**
         * Notification that the connection was closed due to an exception. When abruptly disconnected.
         * Note: ReconnectionManager was not enabled otherwise it will try to reconnecting to the server.
         * Any update of the view must be on UiThread
         *
         * @param exception contains information of the error.
         */
        public void connectionClosedOnError(Exception exception)
        {
            String errMsg = exception.getMessage();
            Timber.e("Captcha-Exception: %s", errMsg);

            StanzaError xmppError = StanzaError.from(Condition.remote_server_timeout, errMsg).build();
            mPPS.accountIBRegistered.reportFailure(new XMPPException.XMPPErrorException(null, xmppError));

            new Handler(Looper.getMainLooper()).post(IBRCaptchaProcessDialog.this::showResult);
        }

        @Override
        public void connected(XMPPConnection connection)
        {
        }

        @Override
        public void authenticated(XMPPConnection connection, boolean resumed)
        {
        }
    }
}
