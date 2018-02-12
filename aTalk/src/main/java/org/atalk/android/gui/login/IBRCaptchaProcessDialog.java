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
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.InputType;
import android.text.TextUtils;
import android.util.DisplayMetrics;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;

import net.java.sip.communicator.impl.protocol.jabber.JabberActivator;
import net.java.sip.communicator.impl.protocol.jabber.ProtocolProviderServiceJabberImpl;
import net.java.sip.communicator.service.protocol.AccountID;
import net.java.sip.communicator.service.protocol.globalstatus.GlobalStatusEnum;
import net.java.sip.communicator.service.protocol.globalstatus.GlobalStatusService;
import net.java.sip.communicator.util.Logger;

import org.atalk.android.R;
import org.atalk.android.aTalkApp;
import org.atalk.android.gui.AndroidGUIActivator;
import org.jivesoftware.smack.ConnectionListener;
import org.jivesoftware.smack.SmackException;
import org.jivesoftware.smack.XMPPConnection;
import org.jivesoftware.smack.XMPPException;
import org.jivesoftware.smack.packet.XMPPError;
import org.jivesoftware.smack.tcp.XMPPTCPConnection;
import org.jivesoftware.smack.util.Async;
import org.jivesoftware.smack.util.StringUtils;
import org.jivesoftware.smackx.bob.packet.BoB;
import org.jivesoftware.smackx.iqregisterx.AccountManager;
import org.jivesoftware.smackx.iqregisterx.packet.Registration;
import org.jivesoftware.smackx.xdata.FormField;
import org.jivesoftware.smackx.xdata.packet.DataForm;
import org.jxmpp.jid.impl.JidCreate;
import org.jxmpp.jid.parts.Localpart;
import org.jxmpp.util.XmppStringUtils;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;

/**
 * The dialog pops up when the user account login return with "not-authorized" i.e. not
 * registered on server, and user has confirmed the InBand Registration.
 *
 * @author Eng Chong Meng
 */
public class IBRCaptchaProcessDialog extends Dialog
{
    /**
     * Logger of this class
     */
    private static final Logger logger = Logger.getLogger(IBRCaptchaProcessDialog.class);

    private static final String FORM_TYPE = "FORM_TYPE";
    private static final String NS_CAPTCHA = "urn:xmpp:captcha";
    private static final String CHALLENGE = "challenge";
    private static final String SID = "sid";
    private static final String ANSWER = "answers";

    private static final String USER_NAME = "username";
    private static final String PASSWORD = "password";
    private static final String OCR = "ocr";

    /**
     * Listens for connection closes or errors.
     */
    private JabberConnectionListener connectionListener;

    private EditText mCaptchaText;
    private EditText mPasswordField;
    private CheckBox mServerOverrideCheckBox;
    private EditText mServerIpField;
    private EditText mServerPortField;
    private TextView mReason;

    private ImageView mImageView;
    private ImageView mShowPasswordImage;
    private CheckBox mShowPasswordCheckBox;

    private Button mAcceptButton;
    private Button mCancelButton;
    private Button mOKButton;

    private AccountID mAccountId;
    private Bitmap mCaptcha;
    private Context mContext;
    private DataForm mDataForm;
    private DataForm submitForm;
    private String mPassword;
    private String mReasonText;
    private ProtocolProviderServiceJabberImpl mPPS;
    private XMPPTCPConnection mConnection;

    /**
     * Constructor for the <tt>Captcha Request Dialog</tt> for passing the dialog parameters
     * <p>
     *
     * @param context the context to which the dialog belongs
     * @param pps the protocol provider service that offers the service
     * @param accountId the AccountID of the login user
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
        this.setContentView(R.layout.captcha_dialog);
        setTitle(mContext.getString(R.string.captcha_registration_request));

        EditText mUserNameField = this.findViewById(R.id.username);
        mUserNameField.setText(mAccountId.getUserID());
        mUserNameField.setEnabled(false);

        mPasswordField = this.findViewById(R.id.password);
        mShowPasswordCheckBox = this.findViewById(R.id.show_password);
        mShowPasswordImage = this.findViewById(R.id.pwdviewImage);
        mServerOverrideCheckBox = this.findViewById(R.id.serverOverridden);
        mServerIpField = this.findViewById(R.id.serverIpField);
        mServerPortField = this.findViewById(R.id.serverPortField);
        mImageView = this.findViewById(R.id.captcha);
        mCaptchaText = this.findViewById(R.id.input);
        mReason = this.findViewById(R.id.reason_field);

        mAcceptButton = this.findViewById(R.id.button_Accept);
        mAcceptButton.setVisibility(View.VISIBLE);
        mOKButton = this.findViewById(R.id.button_OK);
        mOKButton.setVisibility(View.GONE);
        mCancelButton = this.findViewById(R.id.button_Cancel);

        if (connectionListener == null) {
            connectionListener = new JabberConnectionListener();
            mConnection.addConnectionListener(connectionListener);
        }

        if (initIBRRegistration()) {
            UpdateDialogContent();
            initializeViewListeners();
        }
    }

    /*
     * Update dialog content with the received captcha information for form presentation.
     */
    private void UpdateDialogContent()
    {
        mPasswordField.setText(mPassword);
        Boolean isServerOverridden = mAccountId.isServerOverridden();
        mServerOverrideCheckBox.setChecked(isServerOverridden);

        mServerIpField.setText(mAccountId.getServerAddress());
        mServerPortField.setText(mAccountId.getServerPort());
        updateViewVisibility(isServerOverridden);

        // Scale the captcha to the display resolution
        DisplayMetrics metrics = mContext.getResources().getDisplayMetrics();
        Bitmap captcha = Bitmap.createScaledBitmap(mCaptcha,
                (int) (mCaptcha.getWidth() * metrics.scaledDensity),
                (int) (mCaptcha.getHeight() * metrics.scaledDensity), false);
        mImageView.setImageBitmap(captcha);
        mReason.setText(mReasonText);
        mCaptchaText.requestFocus();
    }

    /**
     * Setup all the dialog buttons. listeners for the required actions on user click
     */
    private void initializeViewListeners()
    {
        mShowPasswordCheckBox.setOnCheckedChangeListener(
                new CompoundButton.OnCheckedChangeListener()
                {
                    @Override
                    public void onCheckedChanged(CompoundButton buttonView, boolean isChecked)
                    {
                        showPassword(isChecked);
                    }
                });

        mServerOverrideCheckBox.setOnCheckedChangeListener(
                new CompoundButton.OnCheckedChangeListener()
                {
                    @Override
                    public void onCheckedChanged(CompoundButton buttonView, boolean isChecked)
                    {
                        updateViewVisibility(isChecked);
                    }
                });

        mImageView.setOnClickListener(
                new ImageView.OnClickListener()
                {
                    public void onClick(View v)
                    {
                        mCaptchaText.requestFocus();
                    }
                });

        mAcceptButton.setOnClickListener(new View.OnClickListener()
        {
            public void onClick(View v)
            {
                // server disconnect user if waited for too long
                if (mConnection.isConnected()) {
                    if (updateAccount()) {
                        onAcceptClicked();
                        showResult();
                    }
                }
            }
        });

        mOKButton.setOnClickListener(new View.OnClickListener()
        {
            // Retrigger IBR is user click OK - let login takes over
            public void onClick(View v)
            {
                closeDialog();
                GlobalStatusService globalStatusService = AndroidGUIActivator.getGlobalStatusService();
                globalStatusService.publishStatus(GlobalStatusEnum.ONLINE);
            }
        });

        mCancelButton.setOnClickListener(new View.OnClickListener()
        {
            // Just exit if user cancel
            public void onClick(View v)
            {
                String errMsg = "InBand registration cancelled by user!";
                XMPPError xmppError = XMPPError.from(XMPPError.Condition.registration_required, errMsg).build();
                mPPS.accountIBRegistered.reportFailure(new XMPPException.XMPPErrorException(null, xmppError));
                closeDialog();
            }
        });
    }

    /**
     * Handles the <tt>ActionEvent</tt> triggered when one user clicks on the Submit button.
     */
    private void onAcceptClicked()
    {
        submitForm = new DataForm(DataForm.Type.submit);
        addField(FORM_TYPE, NS_CAPTCHA);

        String cl = mDataForm.getField(CHALLENGE).getValues().get(0);
        addField(CHALLENGE, cl);

        String sid = mDataForm.getField(CHALLENGE).getValues().get(0);
        addField(SID, sid);
        addField(ANSWER, "3");

        // Only localPart is required
        String userName = XmppStringUtils.parseLocalpart(mAccountId.getUserID());
        addField(USER_NAME, userName);

        Editable pwd = mPasswordField.getText();
        if (pwd != null) {
            addField(PASSWORD, pwd.toString());
        }

        Editable rc = mCaptchaText.getText();
        if (rc != null) {
            addField(OCR, rc.toString());
        }

        if ((mConnection != null) && mConnection.isConnected()) {
            AccountManager accountManager = AccountManager.getInstance(mConnection);
            try {
                accountManager.createAccount(submitForm);
                // if not exception being thrown, then registration is successful. Clear IBR flag on success
                mAccountId.setIbRegistration(false);
                mPPS.accountIBRegistered.reportSuccess();
            } catch (SmackException.NoResponseException | XMPPException.XMPPErrorException
                    | SmackException.NotConnectedException | InterruptedException ex) {
                XMPPError xmppError;
                String errMsg = ex.getMessage();
                String errDetails = "";
                if (ex instanceof XMPPException.XMPPErrorException) {
                    xmppError = ((XMPPException.XMPPErrorException) ex).getXMPPError();
                    errDetails = xmppError.getDescriptiveText();
                }
                else {
                    xmppError = XMPPError.from(XMPPError.Condition.not_acceptable, errMsg).build();
                }
                logger.error("Exception: " + errMsg + ": " + errDetails);
                if (errMsg.contains("conflict") && errDetails.contains("exists"))
                    mAccountId.setIbRegistration(false);
                mPPS.accountIBRegistered.reportFailure(new XMPPException.XMPPErrorException(null, xmppError));
            }
        }
    }

    /**
     * Add field / value to the submit Form for registration
     *
     * @param field the submit field variable
     * @param value the field value
     */
    private void addField(String field, String value)
    {
        FormField formField = new FormField(field);
        formField.addValue(value);
        submitForm.addField(formField);
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
     * Updated AccountID with the parameters entered by user
     */
    private boolean updateAccount()
    {
        String password;
        Editable pwd = mPasswordField.getText();
        if ((pwd != null) && !StringUtils.isNullOrEmpty(password = pwd.toString())) {
            mAccountId.setPassword(password);
            if (mAccountId.isPasswordPersistent())
                JabberActivator.getProtocolProviderFactory().storePassword(mAccountId, password);
        }
        else {
            mReason.setText(R.string.captcha_registration_pwd_empty);
            return false;
        }

        // Update server override options
        String serverAddress = mServerIpField.getText().toString().replaceAll("\\s", "");
        String serverPort = mServerPortField.getText().toString().replaceAll("\\s", "");
        boolean isServerOverride = mServerOverrideCheckBox.isChecked();
        mAccountId.setServerOverridden(isServerOverride);
        if ((isServerOverride) && !TextUtils.isEmpty(serverAddress) && !TextUtils.isEmpty(serverPort)) {
            mAccountId.setServerAddress(mServerIpField.getText().toString());
            mAccountId.setServerPort(mServerPortField.getText().toString());
        }
        return true;
    }

    /**
     * Show or hide password
     *
     * @param show <tt>true</tt> set password visible to user
     */
    private void showPassword(boolean show)
    {
        int cursorPosition = mPasswordField.getSelectionStart();
        if (show) {
            mShowPasswordImage.setAlpha(1.0f);
            mPasswordField.setInputType(InputType.TYPE_TEXT_VARIATION_PASSWORD);
        }
        else {
            mShowPasswordImage.setAlpha(0.3f);
            mPasswordField.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        }
        mPasswordField.setSelection(cursorPosition);
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
     * Perform the InBand Registration for the accountId on the defined XMPP connection by pps.
     * Registration can either be:
     * - simple username and password or
     * - With captcha protection using form with embedded captcha image if available, else the
     * image is retrieved from the given url in the form.
     */
    private boolean initIBRRegistration()
    {
        try {
            // Reconnect if the connection is closed by the earlier exception
            if (!mConnection.isConnected())
                mConnection.connect();

            AccountManager accountManager = AccountManager.getInstance(mConnection);
            if (accountManager.isSupported()) {
                Registration info = accountManager.getRegistrationInfo();
                if (info != null) {
                    DataForm dataForm = info.getDataForm();

                    Bitmap captcha = null;
                    BoB bob = info.getBoB();
                    if (bob != null) {
                        byte[] bytData = bob.getContent();
                        InputStream stream = new ByteArrayInputStream(bytData);
                        captcha = BitmapFactory.decodeStream(stream);
                    }
                    else {
                        FormField urlField = dataForm.getField("url");
                        if (urlField != null) {
                            String urlString = urlField.getValues().get(0);
                            URL uri = new URL(urlString);
                            captcha = BitmapFactory.decodeStream(uri.openConnection().getInputStream());
                        }
                    }
                    if ((captcha != null) && (dataForm != null)) {
                        mDataForm = dataForm;
                        mCaptcha = captcha;
                        return true;
                    }
                }
                else {
                    String userName = mAccountId.getUserID();
                    Localpart username = JidCreate.bareFrom(userName).getLocalpartOrNull();
                    accountManager.sensitiveOperationOverInsecureConnection(false);
                    accountManager.createAccount(username, mPassword);
                }
            }
        } catch (IOException | InterruptedException | XMPPException | SmackException e) {
            String errMsg = e.getMessage();
            XMPPError xmppError = XMPPError.from(XMPPError.Condition.not_authorized, errMsg).build();
            mPPS.accountIBRegistered.reportFailure(new XMPPException.XMPPErrorException(null, xmppError));
            showResult();
        }
        return false;
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
                    String errDetails = ((XMPPException.XMPPErrorException) ex).getXMPPError().getDescriptiveText();
                    if (!StringUtils.isEmpty(errDetails))
                        errMsg += "\n" + errDetails;
                }
            }
        } catch (SmackException.NoResponseException | InterruptedException e) {
            errMsg = e.getMessage();
        }
        if (!StringUtils.isNullOrEmpty(errMsg)) {
            mReasonText = mContext.getString(R.string.captcha_registration_fail, errMsg);
        }
        // close connection on error, else throws connectionClosedOnError on timeout
        Async.go(new Runnable()
        {
            @Override
            public void run()
            {
                if (mConnection.isConnected())
                    mConnection.disconnect();
            }
        });

        mReason.setText(mReasonText);
        mAcceptButton.setVisibility(View.GONE);
        mOKButton.setVisibility(View.VISIBLE);
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
            logger.error("Captcha-Exception: " + errMsg);

            XMPPError xmppError = XMPPError.from(XMPPError.Condition.remote_server_timeout, errMsg).build();
            mPPS.accountIBRegistered.reportFailure(new XMPPException.XMPPErrorException(null, xmppError));

            new Handler(Looper.getMainLooper()).post(new Runnable()
            {
                @Override
                public void run()
                {
                    showResult();
                }
            });
        }

        /**
         * Implements <tt>reconnectingIn</tt> from <tt>ConnectionListener</tt>
         *
         * @param i delay in seconds for reconnection.
         */
        public void reconnectingIn(int i)
        {
        }

        /**
         * Implements <tt>reconnectingIn</tt> from <tt>ConnectionListener</tt>
         */
        public void reconnectionSuccessful()
        {
        }

        /**
         * Implements <tt>reconnectionFailed</tt> from <tt>ConnectionListener</tt>.
         *
         * @param exception description of the failure
         */
        public void reconnectionFailed(Exception exception)
        {
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
