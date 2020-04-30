/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.xmpp.extensions.coin;

import org.xmpp.extensions.AbstractExtensionElement;

import org.jivesoftware.smack.packet.ExtensionElement;
import org.jivesoftware.smack.packet.XmlEnvironment;
import org.jivesoftware.smack.util.XmlStringBuilder;

import java.util.Map;

import javax.xml.namespace.QName;

/**
 * Host Information packet extension.
 *
 * @author Sebastien Vincent
 * @author Eng Chong Meng
 */
public class HostInfoExtension extends AbstractExtensionElement
{
    /**
     * The name of the element that contains the media data.
     */
    public static final String ELEMENT = "host-info";

    /**
     * The namespace that media belongs to.
     */
    public static final String NAMESPACE = null;

    public static final QName QNAME = new QName(NAMESPACE, ELEMENT);

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
    public HostInfoExtension()
    {
        super(ELEMENT, NAMESPACE);
    }

    /**
     * Set display text.
     *
     * @param displayText display text
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
     * @param webPage web page
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
    public XmlStringBuilder toXML(XmlEnvironment xmlEnvironment)
    {
        XmlStringBuilder xml = new XmlStringBuilder(this);

        // add the rest of the attributes if any
        for (Map.Entry<String, Object> entry : attributes.entrySet()) {
            xml.optAttribute(entry.getKey(), entry.getValue().toString());
        }
        xml.rightAngleBracket();

        xml.optElement(ELEMENT_DISPLAY_TEXT, displayText);
        xml.optElement(ELEMENT_WEB_PAGE, webPage);

        for (ExtensionElement ext : getChildExtensions()) {
            xml.append(ext.toXML(XmlEnvironment.EMPTY));
        }
        xml.closeElement(ELEMENT);
        return xml;
    }
}
