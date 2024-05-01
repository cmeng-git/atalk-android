/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.atalk.service.version.util;

import org.atalk.service.version.Version;

/**
 * Base class for <code>Version</code> implementation that uses major, minor and nightly build id for
 * versioning purposes.
 *
 * @author Emil Ivov
 * @author Pawel Domas
 */
public abstract class AbstractVersion implements Version {
    /**
     * The version major field.
     */
    private final int versionMajor;

    /**
     * The version minor field.
     */
    private final int versionMinor;

    /**
     * The nightly build id field.
     */
    private final String nightlyBuildID;

    /**
     * Creates version object with custom major, minor and nightly build id.
     *
     * @param majorVersion the major version to use.
     * @param minorVersion the minor version to use.
     * @param nightlyBuildID the nightly build id value for new version object.
     */
    protected AbstractVersion(int majorVersion, int minorVersion, String nightlyBuildID) {
        this.versionMajor = majorVersion;
        this.versionMinor = minorVersion;
        this.nightlyBuildID = nightlyBuildID;
    }

    /**
     * Returns the version major of the current Jitsi version. In an example 2.3.1 version string
     * 2 is the version major. The version major number changes when a relatively extensive set
     * of new features and possibly re-architecture have been applied to the Jitsi.
     *
     * @return the version major String.
     */
    public int getVersionMajor() {
        return versionMajor;
    }

    /**
     * Returns the version minor of the current Jitsi version. In an example 2.3.1 version string
     * 3 is the version minor. The version minor number changes after adding enhancements and
     * possibly new features to a given aTalk version.
     *
     * @return the version minor integer.
     */
    public int getVersionMinor() {
        return versionMinor;
    }

    /**
     * If this is a nightly build, returns the build identifies (e.g. nightly-2007.12.07-06.45.17).
     * If this is not a nightly build Jitsi version, the method returns null.
     *
     * @return a String containing a nightly build identifier or null if this is a release version
     * and therefore not a nightly build
     */
    public String getNightlyBuildID() {
        if (!isNightly())
            return null;

        return nightlyBuildID;
    }

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
    public int compareTo(Version version) {
        if (version == null)
            return -1;

        if (getVersionMajor() != version.getVersionMajor())
            return getVersionMajor() - version.getVersionMajor();

        if (getVersionMinor() != version.getVersionMinor())
            return getVersionMinor() - version.getVersionMinor();

        try {
            return compareNightlyBuildIDByComponents(getNightlyBuildID(), version.getNightlyBuildID());
        } catch (Throwable th) {
            // if parsing fails will continue with lexicographically compare
        }

        return getNightlyBuildID().compareTo(version.getNightlyBuildID());
    }

    /**
     * As normally nightly.build.id is in the form of <build-num> or <build-num>.<revision> we
     * will first try to compare them by splitting the id in components and compare them one by
     * one as numbers
     *
     * @param v1 the first version to compare
     * @param v2 the second version to compare
     *
     * @return a negative integer, zero, or a positive integer as the first parameter <code>v1</code>
     * represents a version that is earlier, same, or more recent than the one referenced by the
     * <code>v2</code> parameter.
     */
    private static int compareNightlyBuildIDByComponents(String v1, String v2) {
        String[] s1 = v1.split("\\.");
        String[] s2 = v2.split("\\.");

        int len = Math.max(s1.length, s2.length);
        for (int i = 0; i < len; i++) {
            int n1 = 0;
            int n2 = 0;

            if (i < s1.length)
                n1 = Integer.parseInt(s1[i]);
            if (i < s2.length)
                n2 = Integer.parseInt(s2[i]);

            if (n1 != n2)
                return n1 - n2;
        }

        // will happen if both versions have identical numbers in
        // their components (even if one of them is longer, has more components)
        return 0;
    }

    /**
     * Compares the <code>version</code> parameter to this version and returns true if and only if
     * both reference the same Jitsi version and false otherwise.
     *
     * @param version the version instance that we'd like to compare with this one.
     *
     * @return true if and only the version param references the same Jitsi version as this
     * Version instance and false otherwise.
     */
    @Override
    public boolean equals(Object version) {
        // simply compare the version strings
        return toString().equals((version == null) ? "null" : version.toString());
    }

    /**
     * @see java.lang.Object#hashCode()
     */
    @Override
    public int hashCode() {
        return toString().hashCode();
    }

    /**
     * Returns a String representation of this Version instance in the generic form of major
     * .minor[.nightly.build.id]. If you'd just like to obtain the version of Jitsi so that you
     * could display it (e.g. in a Help->About dialog) then all you need is calling this method.
     *
     * @return a major.minor[.build] String containing the complete Jitsi version.
     */
    @Override
    public String toString() {
        StringBuffer versionStringBuff = new StringBuffer();

        versionStringBuff.append(Integer.toString(getVersionMajor()));
        versionStringBuff.append(".");
        versionStringBuff.append(Integer.toString(getVersionMinor()));

        if (isPreRelease()) {
            versionStringBuff.append("-");
            versionStringBuff.append(getPreReleaseID());
        }

        if (isNightly()) {
            versionStringBuff.append(".");
            versionStringBuff.append(getNightlyBuildID());
        }
        return versionStringBuff.toString();
    }
}
