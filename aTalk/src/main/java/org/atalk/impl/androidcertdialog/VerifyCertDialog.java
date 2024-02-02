/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.atalk.impl.androidcertdialog;

import android.content.Context;
import android.content.Intent;

import net.java.sip.communicator.service.certificate.VerifyCertificateDialogService;

import org.atalk.android.aTalkApp;

import java.security.cert.Certificate;

/**
 * Implementation of <code>VerifyCertificateDialog</code>. Serves as dialog data model for GUI components.
 *
 * @author Pawel Domas
 */
class VerifyCertDialog implements VerifyCertificateDialogService.VerifyCertificateDialog
{
    /**
     * Request id that can be used to retrieve this dialog from <code>CertificateDialogServiceImpl</code>.
     */
    private final Long requestId;

    /**
     * Lock used to hold protocol thread until user decides what to do about the certificate.
     */
    private final Object finishLock = new Object();

    /**
     * Subject certificate.
     */
    private final Certificate cert;

    /**
     * Dialog title supplied by the service.
     */
    private final String title;

    /**
     * Dialog message supplied by the service.
     */
    private final String msg;

    /**
     * Holds trusted state.
     */
    private boolean trusted = false;

    /**
     * Holds always trust state.
     */
    private boolean alwaysTrust = false;

    /**
     * Creates new instance of <code>VerifyCertDialog</code>.
     *
     * @param requestId the request identifier.
     * @param cert the certificate to be verified
     * @param title dialog title
     * @param message dialog message
     */
    VerifyCertDialog(Long requestId, Certificate cert, String title, String message)
    {
        this.requestId = requestId;
        this.cert = cert;
        this.title = title;
        this.msg = message;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void setVisible(boolean b)
    {
        if (!b) {
            // Currently, method is always called with true  it's expected to block until dialog finishes its job.
            return;
        }

        // starts the dialog and waits on the lock until finish
        Context ctx = aTalkApp.getInstance();
        Intent verifyIntent = VerifyCertificateActivity.createIntent(ctx, requestId);
        ctx.startActivity(verifyIntent);

        synchronized (finishLock) {
            try {
                finishLock.wait();
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean isTrusted()
    {
        return trusted;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean isAlwaysTrustSelected()
    {
        return alwaysTrust;
    }

    /**
     * Returns certificate to be verified.
     *
     * @return the certificate to be verified.
     */
    public Certificate getCertificate()
    {
        return cert;
    }

    /**
     * Returns dialog title.
     *
     * @return dialog title.
     */
    public String getTitle()
    {
        return title;
    }

    /**
     * Returns dialog message.
     *
     * @return dialog message.
     */
    public String getMsg()
    {
        return msg;
    }

    /**
     * Notifies thread waiting for user decision.
     */
    public void notifyFinished()
    {
        synchronized (finishLock) {
            finishLock.notifyAll();
        }
    }

    /**
     * Sets the trusted flag.
     *
     * @param trusted <code>true</code> if subject certificate is trusted by the user.
     */
    public void setTrusted(boolean trusted)
    {
        this.trusted = trusted;
    }

    /**
     * Sets always trusted flag.
     *
     * @param alwaysTrust <code>true</code> if user decided to always trust subject certificate.
     */
    public void setAlwaysTrust(boolean alwaysTrust)
    {
        this.alwaysTrust = alwaysTrust;
    }
}
