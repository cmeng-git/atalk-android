/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 * 
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.atalk.service.version;

/**
 * The version service keeps track of the SIP Communicator version that we are currently running.
 * Other modules (such as a Help->About dialog) query and use this service in order to show the
 * current application version.
 *
 * @author Emil Ivov
 * @author Eng Chong Meng
 */
public interface VersionService
{
	/**
	 * Returns a <code>Version</code> object containing version details of the SIP Communicator
	 * version that we're currently running.
	 *
	 * @return a <code>Version</code> object containing version details of the SIP Communicator
	 * version that we're currently running.
	 */
	Version getCurrentVersion();

	long getCurrentVersionCode();

	String getCurrentVersionName();

	/**
	 * Returns a Version instance corresponding to the <code>version</code> string.
	 *
	 * @param version
	 * 		a version String that we have obtained by calling a <code>Version.toString()</code> method.
	 * @return the <code>Version</code> object corresponding to the <code>version</code> string.
	 */
	Version parseVersionString(String version);
}
