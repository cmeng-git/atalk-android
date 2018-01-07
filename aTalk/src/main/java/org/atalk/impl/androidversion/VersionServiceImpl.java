/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 * 
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.atalk.impl.androidversion;

import android.content.Context;
import android.content.pm.*;

import net.java.sip.communicator.util.Logger;

import org.atalk.android.aTalkApp;
import org.atalk.service.version.Version;
import org.atalk.service.version.util.AbstractVersionService;

/**
 * Android version service implementation. Current version is parsed from android:versionName
 * attribute from PackageInfo.
 *
 * @author Pawel Domas
 * @author Eng Chong Meng
 */
public class VersionServiceImpl extends AbstractVersionService
{
	/**
	 * The logger
	 */
	private final static Logger logger = Logger.getLogger(VersionServiceImpl.class);

	/**
	 * Current version instance.
	 */
	private final VersionImpl CURRENT_VERSION;

	private final int CURRENT_VERSION_CODE;

	private final String CURRENT_VERSION_NAME;

	/**
	 * Creates new instance of <tt>VersionServiceImpl</tt> and parses current version from
	 * android:versionName attribute from PackageInfo.
	 */
	public VersionServiceImpl()
	{
		Context ctx = aTalkApp.getGlobalContext();
		PackageManager pckgMan = ctx.getPackageManager();
		try {
			PackageInfo pckgInfo = pckgMan.getPackageInfo(ctx.getPackageName(), 0);

			String versionName = pckgInfo.versionName;
			int versionCode = pckgInfo.versionCode;

			CURRENT_VERSION_NAME = versionName;
			CURRENT_VERSION_CODE = versionCode;
			// cmeng - version must all be digits, otherwise no online update
			CURRENT_VERSION = (VersionImpl) parseVersionString(versionName);
			logger.info("Device installed with aTalk-android version: " + CURRENT_VERSION
					+ ", version code: " + versionCode);
		}
		catch (PackageManager.NameNotFoundException e) {
			throw new RuntimeException(e);
		}
	}

	/**
	 * Returns a <tt>Version</tt> object containing version details of the Jitsi version that
	 * we're currently running.
	 *
	 * @return a <tt>Version</tt> object containing version details of the Jitsi version that
	 * we're currently running.
	 */
	public Version getCurrentVersion()
	{
		return CURRENT_VERSION;
	}

	public int getCurrentVersionCode()
	{
		return CURRENT_VERSION_CODE;
	}

	public String getCurrentVersionName()
	{
		return CURRENT_VERSION_NAME;
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	protected Version createVersionImpl(int majorVersion, int minorVersion, String nightlyBuildId)
	{
		return new VersionImpl(majorVersion, minorVersion, nightlyBuildId);
	}
}
