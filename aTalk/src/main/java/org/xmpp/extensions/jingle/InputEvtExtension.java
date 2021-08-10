/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 * 
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.xmpp.extensions.jingle;

import org.xmpp.extensions.AbstractExtensionElement;

/**
 * Represents the content <tt>inputevt</tt> element that may be find in <tt>content</tt> part of a
 * Jingle media negociation.
 *
 * @author Sebastien Vincent
 * @author Eng Chong Meng
 */
public class InputEvtExtension extends AbstractExtensionElement
{
	/**
	 * Name of the XML element representing the extension.
	 */
	public final static String ELEMENT = "inputevt";

	/**
	 * Namespace..
	 */
	public final static String NAMESPACE = "http://jitsi.org/protocol/inputevt";

	/**
	 * Constructs a new <tt>inputevt</tt> extension.
	 */
	public InputEvtExtension()
	{
		super(ELEMENT, NAMESPACE);
	}
}
