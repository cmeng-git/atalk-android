/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.xmpp.extensions.jingle;

/**
 * A representation of the <tt>remote-candidate</tt> ICE transport element.
 *
 * @author Emil Ivov
 */
public class RemoteCandidateExtensionElement extends CandidateExtensionElement
{
    /**
     * The name of the "candidate" element.
     */
    public static final String ELEMENT_NAME = "remote-candidate";

    /**
     * Creates a new {@link CandidateExtensionElement}
     */
    public RemoteCandidateExtensionElement()
    {
        super(ELEMENT_NAME);
    }
}
