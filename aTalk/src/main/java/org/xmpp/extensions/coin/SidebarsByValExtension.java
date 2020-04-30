/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.xmpp.extensions.coin;

import org.xmpp.extensions.AbstractExtensionElement;

import javax.xml.namespace.QName;

/**
 * Sidebars by val packet extension.
 *
 * @author Sebastien Vincent
 * @author Eng Chong Meng
 */
public class SidebarsByValExtension extends AbstractExtensionElement
{
    /**
     * The name of the element that contains the sidebars by val.
     */
    public static final String ELEMENT = "sidebars-by-val";

    /**
     * The namespace that sidebars by val belongs to.
     */
    public static final String NAMESPACE = "";

    public static final QName QNAME = new QName(NAMESPACE, ELEMENT);

    /**
     * Constructor.
     */
    public SidebarsByValExtension()
    {
        super(ELEMENT, NAMESPACE);
    }
}
