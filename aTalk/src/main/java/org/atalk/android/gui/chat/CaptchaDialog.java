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
package org.atalk.android.gui.chat;

import android.app.Dialog;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Bundle;
import android.text.Editable;
import android.util.DisplayMetrics;
import android.view.View;
import android.widget.*;

import net.java.sip.communicator.service.protocol.ProtocolProviderService;
import net.java.sip.communicator.util.Logger;

import org.atalk.android.R;
import org.jivesoftware.smack.*;
import org.jivesoftware.smack.filter.StanzaIdFilter;
import org.jivesoftware.smack.packet.*;
import org.jivesoftware.smack.tcp.XMPPTCPConnection;
import org.jivesoftware.smackx.bob.packet.BoBExt;
import org.jivesoftware.smackx.captcha.packet.Captcha;
import org.jivesoftware.smackx.captcha.packet.CaptchaIQ;
import org.jivesoftware.smackx.xdata.FormField;
import org.jivesoftware.smackx.xdata.packet.DataForm;

import java.io.*;
import java.net.URL;

/**
 * The dialog pops up when the user joining chat room receive a normal message containing
 * captcha challenge for spam protection
 *
 * @author Eng Chong Meng
 */
public class CaptchaDialog extends Dialog
{
    /**
     * Logger of this class
     */
    private static final Logger logger = Logger.getLogger(CaptchaDialog.class);

    public static final int unknown = -1;
    public static final int validated = 0;
    public static final int awaiting = 1;
    public static final int failed = 2;
    public static final int cancel = 3;

    private EditText mCaptchaText;
    private TextView mReason;

    private ImageView mImageView;
    private Button mAcceptButton;
    private Button mCancelButton;
    private Button mOKButton;

    private Bitmap mCaptcha;
    private DataForm mDataForm;
    private DataForm formSubmit;
    private String mReasonText;

    private static XMPPTCPConnection mConnection;
    private static Message mMessage;
    private Context mContext;
    private final CaptchaDialogListener callBack;

    public interface CaptchaDialogListener
    {
        void onResult(int state);
    }

    public CaptchaDialog(Context context, ProtocolProviderService pps, Message message, CaptchaDialogListener listener)
    {
        super(context);
        mContext = context;
        mMessage = message;
        mConnection = pps.getConnection();
        callBack = listener;
    }

    @Override
    public void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);
        setTitle(R.string.captcha_challenge);
        setContentView(R.layout.captcha_challenge);

        mImageView = findViewById(R.id.captcha);
        mCaptchaText = findViewById(R.id.input);
        mReason = findViewById(R.id.reason_field);

        mAcceptButton = findViewById(R.id.button_accept);
        mAcceptButton.setVisibility(View.VISIBLE);
        mOKButton = findViewById(R.id.button_ok);
        mOKButton.setVisibility(View.GONE);
        mCancelButton = findViewById(R.id.button_cancel);

        if (initCaptchaData()) {
            showCaptchaContent();
            initializeViewListeners();
        }
    }

    private void closeDialog()
    {
        this.cancel();
    }

    /*
     * Update dialog content with the received captcha information for form presentation.
     */
    private void showCaptchaContent()
    {
        // Scale the captcha to the display resolution
        DisplayMetrics metrics = mContext.getResources().getDisplayMetrics();
        Bitmap captcha = Bitmap.createScaledBitmap(mCaptcha,
                (int) (mCaptcha.getWidth() * metrics.scaledDensity),
                (int) (mCaptcha.getHeight() * metrics.scaledDensity), false);
        mImageView.setImageBitmap(captcha);
        mReasonText = mMessage.getBody();
        mReason.setText(mReasonText);
        mCaptchaText.requestFocus();
    }

    /**
     * Setup all the dialog buttons. listeners for the required actions on user click
     */
    private void initializeViewListeners()
    {
        mImageView.setOnClickListener(
                v -> mCaptchaText.requestFocus()
        );

        mAcceptButton.setOnClickListener(v -> {
            onAcceptClicked();
            showResult();
        });

        mOKButton.setOnClickListener(v -> closeDialog());

        mCancelButton.setOnClickListener(v -> {
            callBack.onResult(cancel);
            closeDialog();
        });
    }

    /**
     * Handles the <tt>ActionEvent</tt> triggered when one user clicks on the Submit button.
     * Reply with the following Captcha IQ
     * <iq type='set' from='robot@abuser.com/zombie' to='victim.com' xml:lang='en' id='z140r0s'>
     * <captcha xmlns='urn:xmpp:captcha'>
     * <x xmlns='jabber:x:data' type='submit'>
     * <field var='FORM_TYPE'><value>urn:xmpp:captcha</value></field>
     * <field var='from'><value>innocent@victim.com</value></field>
     * * <field var='challenge'><value>F3A6292C</value></field>
     * <field var='sid'><value>spam1</value></field>
     * <field var='ocr'><value>7nHL3</value></field>
     * </x>
     * </captcha>
     * </iq>
     */
    private void onAcceptClicked()
    {
        formSubmit = new DataForm(DataForm.Type.submit);
        addField(FormField.FORM_TYPE, Captcha.NAMESPACE);

        String from = mDataForm.getField(Captcha.FROM).getFirstValue();
        addField(Captcha.FROM, from);

        String cl = mDataForm.getField(Captcha.CHALLENGE).getFirstValue();
        addField(Captcha.CHALLENGE, cl);

        String sid = mDataForm.getField(Captcha.SID).getFirstValue();
        addField(Captcha.SID, sid);

        // Only localPart is required
        String userName = mMessage.getTo().toString();
        addField(Captcha.USER_NAME, userName);

        Editable rc = mCaptchaText.getText();
        if (rc != null) {
            addField(Captcha.OCR, rc.toString());
        }

        CaptchaIQ iqCaptcha = new CaptchaIQ(formSubmit);
        iqCaptcha.setType(IQ.Type.set);
        iqCaptcha.setTo(mMessage.getFrom());
        try {
            createStanzaCollectorAndSend(iqCaptcha).nextResultOrThrow();
            mReasonText = mContext.getString(R.string.service_gui_JOIN_CHAT_ROOM_CAPTCHA_VERIFICATION_VALID);
            callBack.onResult(validated);
        } catch (SmackException.NoResponseException | XMPPException.XMPPErrorException
                | SmackException.NotConnectedException | InterruptedException ex) {
            String errMsg = ex.getMessage();
            if (ex instanceof XMPPException.XMPPErrorException) {
                StanzaError xmppError = ((XMPPException.XMPPErrorException) ex).getStanzaError();
                errMsg += "\n" + xmppError.getDescriptiveText();
            }
            logger.error("Exception: " + errMsg);
            mReasonText = errMsg;
            callBack.onResult(failed);
        }
        mCaptchaText.setEnabled(false);
        mCancelButton.setVisibility(View.GONE);
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
        formSubmit.addField(formField);
    }

    /*
     * set Captcha IQ and receive reply
     */
    private StanzaCollector createStanzaCollectorAndSend(IQ req)
            throws SmackException.NotConnectedException, InterruptedException
    {
        return mConnection.createStanzaCollectorAndSend(new StanzaIdFilter(req.getStanzaId()), req);
    }

    /**
     * Perform the InBand Registration for the accountId on the defined XMPP connection by pps.
     * Registration can either be:
     * - simple username and password or
     * - With captcha protection using form with embedded captcha image if available, else the
     * image is retrieved from the given url in the form.
     */
    private boolean initCaptchaData()
    {
        try {
            // do not proceed if dataForm is null
            Captcha captcha = mMessage.getExtension(CaptchaIQ.ELEMENT, CaptchaIQ.NAMESPACE);
            DataForm dataForm = captcha.getDataForm();
            if (dataForm == null) {
                callBack.onResult(failed);
                return false;
            }

            Bitmap bmCaptcha = null;
            BoBExt bob = mMessage.getExtension(BoBExt.ELEMENT, BoBExt.NAMESPACE);
            if (bob != null) {
                byte[] bytData = bob.getBoBData().getContent();
                InputStream stream = new ByteArrayInputStream(bytData);
                bmCaptcha = BitmapFactory.decodeStream(stream);
            }
            else {
                /*
                 * <field var='ocr' label='Enter the text you see'>
                 *   <media xmlns='urn:xmpp:media-element' height='80' width='290'>
                 *     <uri type='image/jpeg'>http://www.victim.com/challenges/ocr.jpeg?F3A6292C</uri>
                 *     <uri type='image/jpeg'>cid:sha1+f24030b8d91d233bac14777be5ab531ca3b9f102@bob.xmpp.org</uri>
                 *   </media>
                 * </field>
                 */
                // not working - smack does not support get media element embedded in ocr field data
                // FormField ocrField = dataForm.getField("ocr");
                // String mediaElement = ocrField.getDescription();
                FormField urlField = dataForm.getField("ocr");
                if (urlField != null) {
                    String urlString = urlField.getFirstValue();
                    if (urlString.contains("http://")) {
                        URL uri = new URL(urlString);
                        bmCaptcha = BitmapFactory.decodeStream(uri.openConnection().getInputStream());
                    }
                }
            }
            mDataForm = dataForm;
            mCaptcha = bmCaptcha;

            // use web link for captcha challenge to user if null
            if (bmCaptcha == null)
                callBack.onResult(failed);
            else
                callBack.onResult(awaiting);
            return true;
        } catch (IOException e) {
            mReasonText = e.getMessage();
            callBack.onResult(failed);
            showResult();
        }
        return false;
    }

    /**
     * Shows IBR registration result.
     */
    private void showResult()
    {
        mReason.setText(mReasonText);
        mAcceptButton.setVisibility(View.GONE);
        mOKButton.setVisibility(View.VISIBLE);
    }
}
