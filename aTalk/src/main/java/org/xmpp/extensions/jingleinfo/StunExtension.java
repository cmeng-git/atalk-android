/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 * 
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.xmpp.extensions.jingleinfo;

import org.xmpp.extensions.AbstractExtensionElement;

import javax.xml.namespace.QName;

/**
 * Stun packet extension.
 *
 * @author Sebastien Vincent
 * @author Eng Chong Meng
 */
public class StunExtension extends AbstractExtensionElement
{
	/**
	 * The namespace.
	 */
    public static final String NAMESPACE = "google:jingleinfo";

	/**
	 * The element name.
	 */
	public static final String ELEMENT = "stun";

    public static final QName QNAME = new QName(NAMESPACE, ELEMENT);

    /**
	 * Constructor.
	 */
	public StunExtension()
	{
		super(ELEMENT, NAMESPACE);
	}
}
