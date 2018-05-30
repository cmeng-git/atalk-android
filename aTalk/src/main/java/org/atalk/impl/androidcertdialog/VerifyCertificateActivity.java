/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 * 
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.atalk.impl.androidcertdialog;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.text.Html;
import android.view.View;
import android.widget.TextView;

import net.java.sip.communicator.util.Logger;

import org.atalk.android.R;
import org.atalk.service.osgi.OSGiActivity;

/**
 * Activity displays the certificate to the user and asks him whether to trust the certificate or
 * not. It also uses <tt>CertInfoDialog</tt> to display detailed information about the certificate.
 *
 * @author Pawel Domas
 * @author Eng Chong Meng
 */
public class VerifyCertificateActivity extends OSGiActivity
		implements CertInfoDialog.CertInfoDialogListener
{
	/**
	 * The logger.
	 */
	private final static Logger logger = Logger.getLogger(VerifyCertificateActivity.class);

	/**
	 * Request identifier extra key.
	 */
	private static String REQ_ID = "request_id";

	/**
	 * Request identifier used to retrieve dialog model.
	 */
	private long requestId;

	/**
	 * Dialog model.
	 */
	private VerifyCertDialog certDialog;

	/**
	 * {@inheritDoc}
	 */
	@Override
	protected void onCreate(Bundle savedInstanceState)
	{
		super.onCreate(savedInstanceState);
		this.requestId = getIntent().getLongExtra(REQ_ID, -1);

		if (requestId == -1) {
		    return;  // not serious enough to throw exception
            // throw new RuntimeException("No request id supplied");
        }

		this.certDialog = CertificateDialogActivator.getDialog(requestId);
		if (certDialog == null) {
			logger.error("No dialog instance found for " + requestId);
			finish();
			return;
		}

		setContentView(R.layout.verify_certificate);
		TextView msgView = findViewById(R.id.message);
		msgView.setText(Html.fromHtml(certDialog.getMsg()));
		setTitle(certDialog.getTitle());
	}

	/**
	 * Method fired when "show certificate info" button is clicked.
	 *
	 * @param v
	 * 		button's <tt>View</tt>
	 */
	public void onShowCertClicked(View v)
	{
		CertInfoDialog.createFragment(requestId).show(getSupportFragmentManager(), "cert_info");
	}

	/**
	 * Method fired when continue button is clicked.
	 *
	 * @param v
	 * 		button's <tt>View</tt>
	 */
	public void onContinueClicked(View v)
	{
		certDialog.setTrusted(true);
		finish();
	}

	/**
	 * Method fired when cancel button is clicked.
	 *
	 * @param v
	 * 		button's <tt>View</tt>
	 */
	public void onCancelClicked(View v)
	{
		certDialog.setTrusted(false);
		finish();
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	protected void onDestroy()
	{
		super.onDestroy();
		if (certDialog != null)
			certDialog.notifyFinished();
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public void onDialogResult(boolean continueAnyway)
	{
		if (continueAnyway) {
			onContinueClicked(null);
		}
		else {
			onCancelClicked(null);
		}
	}

	/**
	 * Creates new parametrized <tt>Intent</tt> for <tt>VerifyCertificateActivity</tt>.
	 *
	 * @param ctx
	 * 		Android context.
	 * @param requestId
	 * 		request identifier of dialog model.
	 * @return new parametrized <tt>Intent</tt> for <tt>VerifyCertificateActivity</tt>.
	 */
	public static Intent createIntent(Context ctx, Long requestId)
	{
		Intent intent = new Intent(ctx, VerifyCertificateActivity.class);
		intent.putExtra(REQ_ID, requestId);
		intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
		return intent;
	}
}
