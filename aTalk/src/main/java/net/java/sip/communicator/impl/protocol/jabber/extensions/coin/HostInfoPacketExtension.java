/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 * 
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package net.java.sip.communicator.impl.protocol.jabber.extensions.coin;

import java.util.Map;

import net.java.sip.communicator.impl.protocol.jabber.extensions.AbstractPacketExtension;

import org.jivesoftware.smack.packet.ExtensionElement;
import org.jivesoftware.smack.util.XmlStringBuilder;

/**
 * Host Information packet extension.
 *
 * @author Sebastien Vincent
 * @author Eng Chong Meng
 */
public class HostInfoPacketExtension extends AbstractPacketExtension
{
	/**
	 * The name of the element that contains the media data.
	 */
	public static final String ELEMENT_NAME = "host-info";

	/**
	 * The namespace that media belongs to.
	 */
	public static final String NAMESPACE = null;

	/**
	 * Display text element name.
	 */
	public static final String ELEMENT_DISPLAY_TEXT = "display-text";

	/**
	 * Web page element name.
	 */
	public static final String ELEMENT_WEB_PAGE = "web-page";

	/**
	 * Display text.
	 */
	private String displayText = null;

	/**
	 * Web page.
	 */
	private String webPage = null;

	/**
	 * Constructor.
	 */
	public HostInfoPacketExtension()
	{
		super(ELEMENT_NAME, NAMESPACE);
	}

	/**
	 * Set display text.
	 * 
	 * @param displayText
	 *        display text
	 */
	public void setDisplayText(String displayText)
	{
		this.displayText = displayText;
	}

	/**
	 * Get display text.
	 *
	 * @return display text
	 */
	public String getDisplayText()
	{
		return displayText;
	}

	/**
	 * Set web page.
	 * 
	 * @param webPage
	 *        web page
	 */
	public void setWebPage(String webPage)
	{
		this.webPage = webPage;
	}

	/**
	 * Get web page.
	 *
	 * @return web page
	 */
	public String getWebPage()
	{
		return webPage;
	}

	/**
	 * Get an XML string representation.
	 *
	 * @return XML string representation
	 */
	@Override
	public XmlStringBuilder toXML()
	{
		XmlStringBuilder xml = new XmlStringBuilder();
		xml.prelude(getElementName(), getNamespace());

		// add the rest of the attributes if any
		for (Map.Entry<String, Object> entry : attributes.entrySet()) {
			xml.optAttribute(entry.getKey(), entry.getValue().toString());
		}
		xml.append(">");

		xml.optElement(ELEMENT_DISPLAY_TEXT, displayText);
		xml.optElement(ELEMENT_WEB_PAGE, webPage);

		for (ExtensionElement ext : getChildExtensions()) {
			xml.append(ext.toXML());
		}

		xml.closeElement(ELEMENT_NAME);
		return xml;
	}
}
