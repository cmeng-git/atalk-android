/*
 * aTalk, the OpenSource Java VoIP and Instant Messaging client.
 * 
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file
 * except in compliance with the License. You may obtain a copy of the License at
 * 
 * http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software distributed under the
 * License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
  * either express or implied. See the License for the specific language governing permissions
 * and limitations under the License.
 */
package org.atalk.android.gui.login;

import android.app.Dialog;
import android.content.Context;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.text.*;
import android.util.DisplayMetrics;
import android.view.View;
import android.widget.*;

import net.java.sip.communicator.impl.protocol.jabber.ProtocolProviderServiceJabberImpl;
import net.java.sip.communicator.service.protocol.AccountID;
import net.java.sip.communicator.service.protocol.globalstatus.*;

import org.atalk.android.R;
import org.atalk.android.gui.AndroidGUIActivator;
import org.atalk.android.gui.util.AndroidUtils;
import org.jivesoftware.smack.*;
import org.jivesoftware.smack.packet.XMPPError;
import org.jivesoftware.smack.tcp.XMPPTCPConnection;
import org.jivesoftware.smackx.iqregisterx.AccountManager;
import org.jivesoftware.smackx.xdata.FormField;
import org.jivesoftware.smackx.xdata.packet.DataForm;
import org.jxmpp.util.XmppStringUtils;

/**
 * The dialog pops up when the user account login return with "not-authorized" i.e. not
 * registered on server, and user has confirmed the InBand Registration.
 *
 * @author Eng Chong Meng
 */
public class CaptchaProcessDialog extends Dialog
{
	private static final String FORM_TYPE = "FORM_TYPE";
	private static final String NS_CAPTCHA = "urn:xmpp:captcha";
	private static final String CHALLENGE = "challenge";
	private static final String SID = "sid";
	private static final String ANSWER = "answers";

	private static final String USER_NAME = "username";
	private static final String PASSWORD = "password";
	private static final String OCR = "ocr";

	private Context mContext;
	private ProtocolProviderServiceJabberImpl mPPS;
	private XMPPTCPConnection mConnection;
	private AccountID mAccountId;
	private DataForm mDataForm;
	private DataForm submitForm;
	private Bitmap mCaptcha;

	private CheckBox mServerOverrideCheckBox;
	private EditText mServerIpField;
	private EditText mServerPortField;

	private EditText mPasswordField;
	private CheckBox mShowPasswordCheckBox;
	private CheckBox mSavePasswordCheckBox;
	private ImageView mShowPasswordImage;
	private EditText input;

	private Button mAcceptButton;
	private Button mIgnoreButton;

	/**
	 * Constructor for the <tt>Captcha Request Dialog</tt> for passing the dialog parameters
	 * <p>
	 *
	 * @param context
	 * 		the context to which the dialog belongs
	 * @param pps
	 * 		the protocol provider service that offers the service
	 * @param accountId
	 * 		the AccountID of the login user
	 * @param dataForm
	 * 		the DataForm containing the Registration Info that was returned
	 * @param captcha
	 * 		the captcha image to be display to the user
	 */
	public CaptchaProcessDialog(Context context, ProtocolProviderServiceJabberImpl pps,
			AccountID accountId, DataForm dataForm, Bitmap captcha)
	{
		super(context);
		mContext = context;
		mPPS = pps;
		mConnection = pps.getConnection();
		mAccountId = accountId;
		mDataForm = dataForm;
		mCaptcha = captcha;
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public void onCreate(Bundle savedInstanceState)
	{
		// Scale the captcha to the display resolution
		DisplayMetrics metrics = mContext.getResources().getDisplayMetrics();
		Bitmap captcha = Bitmap.createScaledBitmap(mCaptcha,
				(int) (mCaptcha.getWidth() * metrics.scaledDensity),
				(int) (mCaptcha.getHeight() * metrics.scaledDensity), false);

		this.setContentView(R.layout.captcha_dialog);
		setTitle(mContext.getString(R.string.captcha_registration_request));

		EditText mUserNameField = (EditText) this.findViewById(R.id.username);
		mUserNameField.setText(mAccountId.getUserID());
		mUserNameField.setEnabled(false);

		String pwd = mAccountId.getPassword();
		mPasswordField = (EditText) this.findViewById(R.id.password);
		mPasswordField.setText(pwd);

		mShowPasswordCheckBox = (CheckBox) this.findViewById(R.id.show_password);
		mShowPasswordImage = (ImageView) this.findViewById(R.id.pwdviewImage);
		mSavePasswordCheckBox = (CheckBox) this.findViewById(R.id.store_password);
		mSavePasswordCheckBox.setChecked(mAccountId.isPasswordPersistent());

		Boolean isServerOverridden = mAccountId.isServerOverridden();
		mServerOverrideCheckBox = (CheckBox) this.findViewById(R.id.serverOverridden);
		mServerOverrideCheckBox.setChecked(isServerOverridden);

		mServerIpField = (EditText) this.findViewById(R.id.serverIpField);
		mServerIpField.setText(mAccountId.getServerAddress());

		mServerPortField = (EditText) this.findViewById(R.id.serverPortField);
		mServerPortField.setText(mAccountId.getServerPort());
		updateViewVisibility(isServerOverridden);

		ImageView imageView = (ImageView) this.findViewById(R.id.captcha);
		input = (EditText) this.findViewById(R.id.input);
		imageView.setImageBitmap(captcha);

		mAcceptButton = (Button) this.findViewById(R.id.button_Accept);
		mIgnoreButton = (Button) this.findViewById(R.id.button_Ignore);
		initializeViewListeners();
	}

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

		mAcceptButton.setOnClickListener(new View.OnClickListener()
		{
			public void onClick(View v)
			{
                boolean isSuccess = false;
                // server disconnect user if waited for too long
                if (mConnection.isConnected()) {
                    updateAccount();
                    isSuccess = onAcceptClicked();
                }
                closeDialog();
                showMessage(isSuccess);
			}
		});
		mIgnoreButton.setOnClickListener(new View.OnClickListener()
		{
			public void onClick(View v)
			{
				String errMsg = "InBand registration cancelled by user!";
				XMPPError xmppError = XMPPError.from(XMPPError.Condition.registration_required,
						errMsg).build();
				mPPS.accountIBRegistered.reportFailure(new XMPPException.XMPPErrorException(null, xmppError));
				closeDialog();
			}
		});
	}

	/**
	 * Handles the <tt>ActionEvent</tt> triggered when one user clicks on the Submit button.
	 */
	private boolean onAcceptClicked()
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

		Editable rc = input.getText();
		if (rc != null) {
			addField(OCR, rc.toString());
		}

		if ((mConnection != null) && mConnection.isConnected()) {
			AccountManager accountManager = AccountManager.getInstance(mConnection);
			try {
				accountManager.createAccount(submitForm);
				// if not exception throw, then registration is successful
				mPPS.accountIBRegistered.reportSuccess();
				return true;
			}
			catch (SmackException.NoResponseException | XMPPException.XMPPErrorException
					| SmackException.NotConnectedException | InterruptedException ex) {
				String errMsg = ex.getMessage();
				XMPPError xmppError
						= XMPPError.from(XMPPError.Condition.not_acceptable, errMsg).build();
				mPPS.accountIBRegistered
						.reportFailure(new XMPPException.XMPPErrorException(null, xmppError));
				return false;
			}
		}
		return false;
	}

	/**
	 * Add field / value to the submit Form for registration
	 *
	 * @param field
	 * 		the submit field variable
	 * @param value
	 * 		the field value
	 */
	private void addField(String field, String value)
	{
		FormField formField = new FormField(field);
		formField.addValue(value);
		submitForm.addField(formField);
	}

	private void closeDialog()
	{   // Ignore android.os.StrictMode$AndroidBlockGuardPolicy.onNetwork(StrictMode.java:1147)
        if (mConnection.isConnected())
    		mConnection.disconnect();
		this.cancel();
	}

	/**
	 * Updated AccountID with the parameters entered by user
	 */
	private void updateAccount()
	{
		Editable pwd = mPasswordField.getText();
		if (pwd != null) {
			if (mSavePasswordCheckBox.isChecked()) {
				mAccountId.setPasswordPersistent(true);
				mAccountId.setPassword(pwd.toString());
			}
		}

		// Update server override options
		String serverAddress = mServerIpField.getText().toString().replaceAll("\\s", "");
		String serverPort = mServerPortField.getText().toString().replaceAll("\\s", "");
		boolean isServerOverride = mServerOverrideCheckBox.isChecked();
		mAccountId.setServerOverridden(isServerOverride);
		if ((isServerOverride)
				&& !TextUtils.isEmpty(serverAddress) && !TextUtils.isEmpty(serverPort)) {
			mAccountId.setServerAddress(mServerIpField.getText().toString());
			mAccountId.setServerPort(mServerPortField.getText().toString());
		}
	}

	/**
	 * Show or hide password
	 *
	 * @param show
	 * <tt>true</tt> set password visible to user
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
			mPasswordField.setInputType(InputType.TYPE_CLASS_TEXT
					| InputType.TYPE_TEXT_VARIATION_PASSWORD);
		}
		mPasswordField.setSelection(cursorPosition);
	}

	/**
	 * Show or hide server address & port
	 *
	 * @param IsServerOverridden
	 * <tt>true</tt> show server address and port field for user entry
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
	 * Shows given message pending registration result as an alert.
	 *
	 * @param isSuccess
	 * 		the message to show pending on isSuccess Status.
	 */
	private void showMessage(boolean isSuccess)
	{
		String sMessage = mContext.getString(R.string.captcha_registration_success);
		if (!isSuccess) {
			String errMsg = "";
			try {
				mPPS.accountIBRegistered.checkIfSuccessOrWait();
			}
			catch (SmackException.NoResponseException | InterruptedException e) {
				errMsg = e.getMessage();
			}
			sMessage = mContext.getString(R.string.captcha_registration_fail, errMsg);
		}
		AndroidUtils.showAlertDialog(mContext,
				mContext.getString(R.string.captcha_registration_request), sMessage);

		GlobalStatusService globalStatusService = AndroidGUIActivator.getGlobalStatusService();
		globalStatusService.publishStatus(GlobalStatusEnum.ONLINE);
	}
}
