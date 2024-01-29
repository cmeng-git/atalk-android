/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.atalk.android.gui.menu;

import android.content.Intent;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;

import org.atalk.android.R;
import org.atalk.android.aTalkApp;
import org.atalk.android.gui.About;
import org.atalk.persistance.ServerPersistentStoresRefreshDialog;
import org.atalk.service.osgi.OSGiActivity;
import org.atalk.service.osgi.OSGiService;

/**
 * Extends this activity to handle exit options menu item.
 *
 * @author Pawel Domas
 */
public abstract class ExitMenuActivity extends OSGiActivity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        super.onCreateOptionsMenu(menu);
        getMenuInflater().inflate(R.menu.exit_menu, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            // Shutdown application
            case R.id.menu_exit:
                shutdownApplication();
                break;
            case R.id.online_help:
                About.atalkUrlAccess(this, getString(R.string.FAQ_Link));
                break;
            case R.id.about:
                startActivity(About.class);
                break;
            // delete database
            case R.id.del_database:
                ServerPersistentStoresRefreshDialog.deleteDB();
                break;
            default:
                return super.onOptionsItemSelected(item);
        }
        return true;
    }

    /**
     * Shutdowns the app by stopping <code>OSGiService</code> and broadcasting action {@link #ACTION_EXIT}.
     */
    private void shutdownApplication() {
        // Shutdown the OSGi service
        stopService(new Intent(this, OSGiService.class));
        // Broadcast the exit action
        Intent exitIntent = new Intent();
        exitIntent.setAction(ACTION_EXIT);
        exitIntent.setPackage(getPackageName());
        sendBroadcast(exitIntent);
    }
}
