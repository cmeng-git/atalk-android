/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.atalk.impl.androidbrowserlauncher;

import android.content.Intent;
import android.net.Uri;

import net.java.sip.communicator.service.browserlauncher.BrowserLauncherService;

import org.atalk.android.aTalkApp;

import timber.log.Timber;

/**
 * Android implementation of <tt>BrowserLauncherService</tt>.
 *
 * @author Pawel Domas
 */
public class AndroidBrowserLauncher implements BrowserLauncherService
{
    /**
     * {@inheritDoc}
     */
    @Override
    public void openURL(String url)
    {
        try {
            Uri uri = Uri.parse(url);

            Intent launchBrowser = new Intent(Intent.ACTION_VIEW, uri);
            launchBrowser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);

            aTalkApp.getGlobalContext().startActivity(launchBrowser);
        } catch (Exception e) {
            Timber.e(e, "Error opening URL");
        }
    }
}
