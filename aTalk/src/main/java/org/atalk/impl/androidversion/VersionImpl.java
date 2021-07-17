/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.atalk.impl.androidversion;

import net.java.sip.communicator.util.ServiceUtils;

import org.atalk.android.R;
import org.atalk.android.aTalkApp;
import org.atalk.service.resources.ResourceManagementService;
import org.atalk.service.version.util.AbstractVersion;

/**
 * Android version service implementation.
 *
 * @author Pawel Domas
 * @author Eng Chong Meng
 */
public class VersionImpl extends AbstractVersion
{
    /**
     * Default application name.
     */
    private static final String DEFAULT_APPLICATION_NAME = aTalkApp.getResString(R.string.APPLICATION_NAME);

    /**
     * The name of this application.
     */
    private static String applicationName = null;

    /**
     * Indicates if this aTalk version corresponds to a nightly build of a repository snapshot or
     * to an official aTalk release.
     */
    public static final boolean IS_NIGHTLY_BUILD = true;

    /**
     * Creates new instance of <tt>VersionImpl</tt> with given major, minor and nightly build id
     * parameters.
     *
     * @param major the major version number.
     * @param minor the minor version number.
     * @param nightBuildID the nightly build id.
     */
    public VersionImpl(int major, int minor, String nightBuildID)
    {
        super(major, minor, nightBuildID);

    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean isNightly()
    {
        return IS_NIGHTLY_BUILD;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean isPreRelease()
    {
        return false;
    }

    /**
     * Returns the version pre-release ID of the current aTalk version and null if this version
     * is not a pre-release.
     *
     * @return a String containing the version pre-release ID.
     */
    public String getPreReleaseID()
    {
        return null;
    }

    /**
     * Returns the name of the application that we're currently running. Default MUST be aTalk.
     *
     * @return the name of the application that we're currently running. Default MUST be aTalk.
     */
    public String getApplicationName()
    {
        if (applicationName == null) {
            try {
                /*
                 * XXX There is no need to have the ResourceManagementService instance as a static
                 * field of the VersionImpl class because it will be used once only anyway.
                 */
                ResourceManagementService resources = ServiceUtils.getService(
                        VersionActivator.bundleContext, ResourceManagementService.class);

                if (resources != null) {
                    applicationName = resources.getSettingsString("service.gui.APPLICATION_NAME");
                }
            } catch (Exception e) {
                // if resource bundle is not found or the key is missing return the default name
            } finally {
                if (applicationName == null)
                    applicationName = DEFAULT_APPLICATION_NAME;
            }
        }
        return applicationName;
    }
}
