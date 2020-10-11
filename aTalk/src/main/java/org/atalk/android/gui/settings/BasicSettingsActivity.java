/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 * 
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.atalk.android.gui.settings;

import android.content.pm.*;
import android.os.Bundle;

import org.atalk.service.osgi.OSGiPreferenceActivity;

/**
 * Base class for settings screens which only adds preferences from XML resource.
 * By default preference resource id is obtained from <tt>Activity</tt> meta-data,
 * resource key: "androidx.preference".
 *
 * @author Pawel Domas
 * @author Eng Chong Meng
 */
public class BasicSettingsActivity extends OSGiPreferenceActivity
{
	/**
	 * Returns preference XML resource ID.
	 *
	 * @return preference XML resource ID.
	 */
	protected int getPreferencesXmlId()
	{
		// Cant' find custom preference classes using:
		// addPreferencesFromIntent(getActivity().getIntent());
		try {
			ActivityInfo app = getPackageManager().getActivityInfo(getComponentName(), PackageManager.GET_META_DATA);
			return app.metaData.getInt("android.preference");
		}
		catch (PackageManager.NameNotFoundException e) {
			throw new RuntimeException(e);
		}
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
    protected void onCreate(Bundle savedInstanceState)
	{
		super.onCreate(savedInstanceState);
		addPreferencesFromResource(getPreferencesXmlId());
	}
}
