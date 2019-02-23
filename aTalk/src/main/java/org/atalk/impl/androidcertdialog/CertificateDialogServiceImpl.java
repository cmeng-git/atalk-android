/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.atalk.impl.androidcertdialog;

import net.java.sip.communicator.service.certificate.VerifyCertificateDialogService;

import org.atalk.android.R;
import org.atalk.android.aTalkApp;

import java.security.cert.Certificate;
import java.util.HashMap;
import java.util.Map;

import timber.log.Timber;

/**
 * Android implementation of <tt>VerifyCertificateDialogService</tt>.
 *
 * @author Pawel Domas
 * @author Eng Chong Meng
 */
class CertificateDialogServiceImpl implements VerifyCertificateDialogService
{
    /**
     * Maps request ids to <tt>VerifyCertDialog</tt> so that they can be retrieved by Android
     * <tt>Activity</tt> or <tt>Fragments</tt>.
     */
    private Map<Long, VerifyCertDialog> requestMap = new HashMap<>();

    /**
     * {@inheritDoc}
     */
    @Override
    public VerifyCertificateDialog createDialog(Certificate[] certs, String title, String message)
    {
        if (title == null)
            title = aTalkApp.getResString(R.string.service_gui_CERT_DIALOG_TITLE);

        Long requestId = System.currentTimeMillis();
        VerifyCertDialog verifyCertDialog = new VerifyCertDialog(requestId, certs[0], title, message);

        requestMap.put(requestId, verifyCertDialog);
        Timber.d("%d creating dialog: %s", hashCode(), requestId);
        // Prevents from closing the dialog on outside touch

        return verifyCertDialog;
    }

    /**
     * Retrieves the dialog for given <tt>requestId</tt>.
     *
     * @param requestId dialog's request identifier assigned during dialog creation.
     * @return the dialog for given <tt>requestId</tt>.
     */
    public VerifyCertDialog retrieveDialog(Long requestId)
    {
        Timber.d("%d getting dialog: %d", hashCode(), requestId);
        return requestMap.get(requestId);
    }
}
