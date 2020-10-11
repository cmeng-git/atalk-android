/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 * 
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.xmpp.extensions.jingle;

import org.xmpp.extensions.AbstractExtensionElement;

/**
 * A representation of the <tt>bandwidth</tt> element used in RTP <tt>description</tt> elements.
 *
 * @author Emil Ivov
 * @author Eng Chong Meng
 */
public class BandwidthExtension extends AbstractExtensionElement
{
	/**
	 * The name of the "bandwidth" element.
	 */
	public static final String ELEMENT = "bandwidth";

	/**
	 * The name of the type argument.
	 */
	public static final String TYPE_ATTR_NAME = "type";

	/**
	 * Creates a new {@link BandwidthExtension} instance.
	 */
	public BandwidthExtension()
	{
		super(ELEMENT, null);
	}

	/**
	 * Sets the value of the optional <tt>type</tt> argument in the <tt>bandwidth</tt> element.
	 *
	 * @param type a <tt>String</tt> value which would often be one of the <tt>bwtype</tt> values specified by SDP
	 */
	public void setType(String type)
	{
		setAttribute(TYPE_ATTR_NAME, type);
	}

	/**
	 * Returns the value of the optional <tt>type</tt> argument in the <tt>bandwidth</tt> element.
	 *
	 * @return a <tt>String</tt> value which would often be one of the <tt>bwtype</tt> values specified by SDP
	 */
	public String getType()
	{
		return getAttributeAsString(TYPE_ATTR_NAME);
	}

	/**
	 * Sets the value of this bandwidth extension.
	 *
	 * @param bw the value of this bandwidth extension.
	 */
	public void setBandwidth(String bw)
	{
		super.setText(bw);
	}

	/**
	 * Returns the value of this bandwidth extension.
	 *
	 * @return the value of this bandwidth extension.
	 */
	public String getBandwidth()
	{
		return super.getText();
	}
}
