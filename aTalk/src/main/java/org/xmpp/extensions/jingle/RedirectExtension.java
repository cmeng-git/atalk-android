/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.xmpp.extensions.jingle;

import org.xmpp.extensions.AbstractExtensionElement;

import javax.xml.namespace.QName;

/**
 * The redirect <tt>ExtensionElement</tt>.
 *
 * @author Sebastien Vincent
 * @author Eng Chong Meng
 */
public class RedirectExtension extends AbstractExtensionElement
{
    /**
     * The name of the "redirect" element.
     */
    public static final String ELEMENT = "redirect";

    /**
     * The namespace.
     */
    public static final String NAMESPACE = "http://www.google.com/session";

    public static final QName QNAME = new QName(NAMESPACE, ELEMENT);

    /**
     * The redirect text.
     */
    private String redir = null;

    /**
     * Creates a new {@link RedirectExtension} instance.
     */
    public RedirectExtension()
    {
        super(ELEMENT, NAMESPACE);
    }

    /**
     * Set redirection.
     *
     * @param redir redirection
     */
    public void setRedir(String redir)
    {
        this.setText(redir);
        this.redir = redir;
    }

    /**
     * Get redirection.
     *
     * @return redirection
     */
    public String getRedir()
    {
        return redir;
    }
}
