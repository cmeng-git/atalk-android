/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 * 
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.xmpp.extensions.coin;

import org.xmpp.extensions.AbstractExtensionElement;

/**
 * Call information packet extension.
 *
 * @author Sebastien Vincent
 * @author Eng Chong Meng
 */
public class CallInfoExtensionElement extends AbstractExtensionElement
{
	/**
	 * The name of the element that contains the call info.
	 */
	public static final String ELEMENT_NAME = "call-info";

	/**
	 * The namespace that call info belongs to.
	 */
    public static final String NAMESPACE = CoinIQ.NAMESPACE;

	/**
	 * Constructor.
	 */
	public CallInfoExtensionElement()
	{
		super(ELEMENT_NAME, NAMESPACE);
	}
}
