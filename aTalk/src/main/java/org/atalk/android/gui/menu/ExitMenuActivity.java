/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 * 
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.atalk.android.gui.menu;

import android.os.Bundle;
import android.view.*;

import org.atalk.android.*;
import org.atalk.persistance.ServerPersistentStoresRefreshDialog;
import org.atalk.service.osgi.OSGiActivity;

/**
 * Extends this activity to handle exit options menu item.
 *
 * @author Pawel Domas
 */
public abstract class ExitMenuActivity extends OSGiActivity
{
    @Override
	protected void onCreate(Bundle savedInstanceState)
	{
		super.onCreate(savedInstanceState);
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu)
	{
        super.onCreateOptionsMenu(menu);
		getMenuInflater().inflate(R.menu.exit_menu, menu);

		return true;
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item)
	{
		switch (item.getItemId()) {
			// Shutdown application
			case R.id.menu_exit:
				aTalkApp.shutdownApplication();
				return true;

			// delete database
			case R.id.del_database:
				ServerPersistentStoresRefreshDialog.deleteDB();
				return true;

			default:
				return super.onOptionsItemSelected(item);
		}
	}
}
