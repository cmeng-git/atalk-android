/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 * 
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.xmpp.extensions.jitsimeet;

import org.xmpp.extensions.AbstractExtensionElement;

/**
 * Jitsi Meet specifics bundle packet extension.
 *
 * @author Pawel Domas
 * @author Eng Chong Meng
 */
public class BundleExtension extends AbstractExtensionElement
{
	/**
	 * The XML element name of {@link BundleExtension}.
	 */
	public static final String ELEMENT = "bundle";

	/**
	 * The XML element namespace of {@link BundleExtension}.
	 */
	public static final String NAMESPACE = "http://estos.de/ns/bundle";

	/**
	 * Creates an {@link BundleExtension} instance for the specified <tt>namespace</tt> and <tt>elementName</tt>.
	 *
	 */
	public BundleExtension()
	{
		super(ELEMENT, NAMESPACE);
	}
}
