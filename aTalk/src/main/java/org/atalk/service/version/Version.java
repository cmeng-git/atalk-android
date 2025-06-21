/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.atalk.service.version;

import androidx.annotation.NonNull;

/**
 * Contains version information of the aTalk instance that we're currently running.
 *
 * @author Emil Ivov
 * @author Lyubomir Marinov
 * @author Eng Chong Meng
 */
public interface Version extends Comparable<Version> {
    int getVersionMajor();

    /**
     * Returns the version minor of the current Jitsi version. In an example 2.3.1 version string
     * 3 is the version minor. The version minor number changes after adding enhancements and
     * possibly new features to a given Jitsi version.
     *
     * @return the version minor integer.
     */
    int getVersionMinor();

    /**
     * Indicates if this Jitsi version corresponds to a nightly build of a repository snapshot or
     * to an official Jitsi release.
     *
     * @return true if this is a build of a nightly repository snapshot and false if this is an
     * official Jitsi release.
     */
    boolean isNightly();

    /**
     * If this is a nightly build, returns the build identifies (e.g. nightly-2007.12.07-06.45.17)
     * . If this is not a nightly build Jitsi version, the method returns null.
     *
     * @return a String containing a nightly build identifier or null if
     */
    String getNightlyBuildID();

    /**
     * Indicates whether this version represents a pre-release (i.e. a non-complete release like an
     * alpha, beta or release candidate version).
     *
     * @return true if this version represents a pre-release and false otherwise.
     */
    boolean isPreRelease();

    /**
     * Returns the version pre-release ID of the current Jitsi version and null if this version is
     * not a pre-release. Version pre-release id-s exist only for pre-release versions and are
     * <code>null<tt/> otherwise. Note that pre-release versions are not used by Jitsi's current
     * versioning convention
     *
     * @return a String containing the version pre-release ID.
     */
    String getPreReleaseID();

    /**
     * Compares another <code>Version</code> object to this one and returns a negative, zero or a
     * positive integer if this version instance represents respectively an earlier, same, or
     * later version as the one indicated by the <code>version</code> parameter.
     *
     * @param version the <code>Version</code> instance that we'd like to compare to this one.
     *
     * @return a negative integer, zero, or a positive integer as this object represents a version
     * that is earlier, same, or more recent than the one referenced by the <code>version</code>
     * parameter.
     */
    int compareTo(Version version);

    /**
     * Compares the <code>version</code> parameter to this version and returns true if and only if
     * both reference the same Jitsi version and false otherwise.
     *
     * @param version the version instance that we'd like to compare with this one.
     *
     * @return true if and only the version param references the same Jitsi version as this
     * Version instance and false otherwise.
     */
    boolean equals(Object version);

    /**
     * Returns a String representation of this Version instance. If you'd just like to obtain the
     * version of Jitsi so that you could display it (e.g. in a Help->About dialog) then all you
     * need is calling this method.
     *
     * @return a major.minor[.build] String containing the complete Jitsi version.
     */
    @NonNull
    String toString();
}
