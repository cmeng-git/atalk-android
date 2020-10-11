/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.xmpp.extensions.coin;

import org.xmpp.extensions.AbstractExtensionElement;

import javax.xml.namespace.QName;

/**
 * URIs packet extension.
 *
 * @author Sebastien Vincent
 * @author Eng Chong Meng
 */
public class URIsExtension extends AbstractExtensionElement
{
    /**
     * The name of the element that contains the URIs data.
     */
    public static final String ELEMENT = "uris";

    /**
     * The namespace that URIs belongs to.
     */
    public static final String NAMESPACE = "";

    public static final QName QNAME = new QName(NAMESPACE, ELEMENT);

    /**
     * Constructor.
     */
    public URIsExtension()
    {
        super(ELEMENT, NAMESPACE);
    }
}
