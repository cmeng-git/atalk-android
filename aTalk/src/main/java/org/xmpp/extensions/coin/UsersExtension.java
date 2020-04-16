/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.xmpp.extensions.coin;

import org.xmpp.extensions.AbstractExtensionElement;

/**
 * Users packet extension.
 *
 * @author Sebastien Vincent
 * @author Eng Chong Meng
 */
public class UsersExtension extends AbstractExtensionElement
{
    /**
     * The name of the element that contains the users data.
     */
    public static final String ELEMENT = "users";

    /**
     * The namespace that users belongs to.
     */
    public static final String NAMESPACE = null;

    /**
     * Entity attribute name.
     */
    public static final String STATE_ATTR_NAME = "state";

    /**
     * Constructor.
     */
    public UsersExtension()
    {
        super(ELEMENT, NAMESPACE);
    }
}
