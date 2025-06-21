/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.atalk.impl.appversion;

import org.atalk.service.version.util.AbstractVersion;

/**
 * Android version service implementation.
 *
 * @author Pawel Domas
 * @author Eng Chong Meng
 */
public class VersionImpl extends AbstractVersion {
    /**
     * Indicates if this aTalk version corresponds to a nightly build of a repository snapshot or
     * to an official aTalk release.
     */
    public static final boolean IS_NIGHTLY_BUILD = true;

    /**
     * Creates new instance of <code>VersionImpl</code> with given major, minor and nightly build id
     * parameters.
     *
     * @param major the major version number.
     * @param minor the minor version number.
     * @param nightBuildID the nightly build id.
     */
    public VersionImpl(int major, int minor, String nightBuildID) {
        super(major, minor, nightBuildID);

    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean isNightly() {
        return IS_NIGHTLY_BUILD;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean isPreRelease() {
        return false;
    }

    /**
     * Returns the version pre-release ID of the current aTalk version and null if this version
     * is not a pre-release.
     *
     * @return a String containing the version pre-release ID.
     */
    public String getPreReleaseID() {
        return null;
    }
}
