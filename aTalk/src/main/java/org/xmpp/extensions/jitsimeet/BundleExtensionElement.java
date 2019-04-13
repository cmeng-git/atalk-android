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
public class BundleExtensionElement extends AbstractExtensionElement
{
	/**
	 * The XML element name of {@link BundleExtensionElement}.
	 */
	public static final String ELEMENT_NAME = "bundle";

	/**
	 * The XML element namespace of {@link BundleExtensionElement}.
	 */
	public static final String NAMESPACE = "http://estos.de/ns/bundle";

	/**
	 * Creates an {@link BundleExtensionElement} instance for the specified <tt>namespace</tt> and <tt>elementName</tt>.
	 *
	 */
	public BundleExtensionElement()
	{
		super(ELEMENT_NAME, NAMESPACE);
	}
}
