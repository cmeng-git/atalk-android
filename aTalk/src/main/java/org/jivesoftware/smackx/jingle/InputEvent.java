/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 * 
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.jivesoftware.smackx.jingle;

import org.jivesoftware.smackx.AbstractXmlElement;

/**
 * Represents the content <code>inputevt</code> element that may be find in <code>content</code> part of a Jingle media negotiation.
 *
 * @author Sebastien Vincent
 * @author Eng Chong Meng
 */
public class InputEvent extends AbstractXmlElement
{
	/**
	 * Name of the XML element representing the extension.
	 */
	public final static String ELEMENT = "inputevt";

	public final static String NAMESPACE = "http://jitsi.org/protocol/inputevt";

	public InputEvent()
	{
		super(builder());
	}

	public InputEvent(Builder builder)
	{
		super(builder);
	}

	public static Builder builder()
	{
		return new Builder(ELEMENT, NAMESPACE);
	}

	/**
	 * Builder for InputEvent. Use {@link AbstractXmlElement#builder()} to
	 * obtain a new instance and {@link #build} to build the InputEvent.
	 */
	public static final class Builder extends AbstractXmlElement.Builder<Builder, InputEvent>
	{
		protected Builder(String element, String namespace)
		{
			super(element, namespace);
		}

		@Override
		public InputEvent build()
		{
			return new InputEvent(this);
		}

		@Override
		protected Builder getThis()
		{
			return this;
		}
	}
}
