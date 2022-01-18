/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 * 
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.jivesoftware.smackx.coin;

import org.jivesoftware.smackx.AbstractExtensionElement;

import javax.xml.namespace.QName;

/**
 * Call information packet extension.
 *
 * @author Sebastien Vincent
 * @author Eng Chong Meng
 */
public class CallInfoExtension extends AbstractExtensionElement
{
	/**
	 * The name of the element that contains the call info.
	 */
	public static final String ELEMENT = "call-info";

	/**
	 * The namespace that call info belongs to.
	 */
    public static final String NAMESPACE = CoinIQ.NAMESPACE;

    public static final QName QNAME = new QName(NAMESPACE, ELEMENT);

    /**
	 * Constructor.
	 */
	public CallInfoExtension()
	{
		super(ELEMENT, NAMESPACE);
	}
}
