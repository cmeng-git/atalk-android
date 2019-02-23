/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package net.java.sip.communicator.impl.protocol.jabber.extensions.jingle;

import net.java.sip.communicator.impl.protocol.jabber.extensions.AbstractExtensionElement;

/**
 * The redirect <tt>ExtensionElement</tt>.
 *
 * @author Sebastien Vincent
 * @author Eng Chong Meng
 */
public class RedirectExtensionElement extends AbstractExtensionElement
{
    /**
     * The name of the "redirect" element.
     */
    public static final String ELEMENT_NAME = "redirect";

    /**
     * The namespace.
     */
    public static final String NAMESPACE = "http://www.google.com/session";

    /**
     * The redirect text.
     */
    private String redir = null;

    /**
     * Creates a new {@link RedirectExtensionElement} instance.
     */
    public RedirectExtensionElement()
    {
        super(ELEMENT_NAME, NAMESPACE);
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
