/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.atalk.crypto.otr;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;

import net.java.sip.communicator.plugin.otr.*;
import net.java.sip.communicator.plugin.otr.OtrContactManager.OtrContact;
import net.java.sip.communicator.service.protocol.Contact;
import net.java.sip.communicator.service.protocol.IMessage;

import org.atalk.android.R;
import org.atalk.android.aTalkApp;
import org.atalk.android.gui.chat.ChatMessage;
import org.atalk.android.gui.util.ViewUtil;
import org.atalk.service.osgi.OSGiActivity;
import org.atalk.util.CryptoHelper;

import java.security.PublicKey;
import java.util.Date;
import java.util.UUID;

/**
 * OTR buddy authenticate dialog. Takes OTR session's UUID as an extra.
 *
 * @author Pawel Domas
 * @author Eng Chong Meng
 */
public class OtrAuthenticateDialog extends OSGiActivity
{
    /**
     * Key name for OTR session's UUID.
     */
    private final static String EXTRA_SESSION_UUID = "uuid";

    /**
     * The <tt>Contact</tt> that belongs to OTR session handled by this instance.
     */
    private OtrContact otrContact;
    private String remoteFingerprint;
    private ScOtrKeyManager keyManager = OtrActivator.scOtrKeyManager;

    /**
     * {@inheritDoc}
     */
    @Override
    protected void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.otr_authenticate_dialog);
        setTitle(R.string.plugin_otr_authbuddydialog_TITLE);

        UUID guid = (UUID) getIntent().getSerializableExtra(EXTRA_SESSION_UUID);
        ScSessionID sessionID = ScOtrEngineImpl.getScSessionForGuid(guid);
        this.otrContact = ScOtrEngineImpl.getOtrContact(sessionID.getSessionID());
        Contact contact = otrContact.contact;

        // Local fingerprint.
        String account = contact.getProtocolProvider().getAccountID().getDisplayName();
        String localFingerprint = keyManager.getLocalFingerprint(contact.getProtocolProvider().getAccountID());

        View content = findViewById(android.R.id.content);
        ViewUtil.setTextViewValue(content, R.id.localFingerprintLbl,
                getString(R.string.plugin_otr_authbuddydialog_LOCAL_FINGERPRINT, account,
                        CryptoHelper.prettifyFingerprint(localFingerprint)));

        // Remote fingerprint.
        String user = contact.getDisplayName();
        PublicKey pubKey = OtrActivator.scOtrEngine.getRemotePublicKey(otrContact);
        remoteFingerprint = keyManager.getFingerprintFromPublicKey(pubKey);

        ViewUtil.setTextViewValue(content, R.id.remoteFingerprintLbl,
                getString(R.string.plugin_otr_authbuddydialog_REMOTE_FINGERPRINT, user,
                        CryptoHelper.prettifyFingerprint(remoteFingerprint)));
        // Action
        ViewUtil.setTextViewValue(content, R.id.actionTextView,
                getString(R.string.plugin_otr_authbuddydialog_VERIFY_ACTION, user));

        // Verify button
        ViewUtil.setCompoundChecked(getContentView(), R.id.verifyButton,
                keyManager.isVerified(contact, remoteFingerprint));
    }

    /**
     * Method fired when the ok button is clicked.
     *
     * @param v ok button's <tt>View</tt>.
     */
    public void onOkClicked(View v)
    {
        if (ViewUtil.isCompoundChecked(getContentView(), R.id.verifyButton)) {
            keyManager.verify(otrContact, remoteFingerprint);

            Contact contact = otrContact.contact;
            String resourceName = (otrContact.resource != null) ? "/" + otrContact.resource.getResourceName() : "";
            String sender = contact.getDisplayName();
            String message = getString(R.string.plugin_otr_activator_sessionstared, sender + resourceName);
            OtrActivator.uiService.getChat(contact).addMessage(sender, new Date(), ChatMessage.MESSAGE_SYSTEM,
                    IMessage.ENCODE_HTML, message);
        }
        else {
            keyManager.unverify(otrContact, remoteFingerprint);
        }
        finish();
    }

    /**
     * Method fired when the cancel button is clicked.
     *
     * @param v the cancel button's <tt>View</tt>
     */
    public void onCancelClicked(View v)
    {
        finish();
    }

    /**
     * Creates parametrized <tt>Intent</tt> of buddy authenticate dialog.
     *
     * @param uuid the UUID of OTR session.
     * @return buddy authenticate dialog parametrized with given OTR session's UUID.
     */
    public static Intent createIntent(UUID uuid)
    {
        Intent intent = new Intent(aTalkApp.getGlobalContext(), OtrAuthenticateDialog.class);
        intent.putExtra(EXTRA_SESSION_UUID, uuid);

        // Started not from Activity
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        return intent;
    }
}
