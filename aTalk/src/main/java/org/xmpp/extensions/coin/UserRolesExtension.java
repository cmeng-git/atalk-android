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

import java.util.*;

import javax.xml.namespace.QName;

/**
 * User roles packet extension.
 *
 * @author Sebastien Vincent
 * @author Eng Chong Meng
 */
public class UserRolesExtension extends AbstractExtensionElement
{
    /**
     * The name of the element that contains the user roles data.
     */
    public static final String ELEMENT = "roles";

    /**
     * The namespace that user roles belongs to.
     */
    public static final String NAMESPACE = CoinIQ.NAMESPACE;

    public static final QName QNAME = new QName(NAMESPACE, ELEMENT);

    /**
     * Subject element name.
     */
    public static final String ELEMENT_ROLE = "entry";

    /**
     * List of roles.
     */
    private List<String> roles = new ArrayList<String>();

    /**
     * Constructor.
     */
    public UserRolesExtension()
    {
        super(ELEMENT, NAMESPACE);
    }

    /**
     * Add roles.
     *
     * @param role role to add
     */
    public void addRoles(String role)
    {
        roles.add(role);
    }

    /**
     * Get list of roles.
     *
     * @return list of roles
     */
    public List<String> getRoles()
    {
        return roles;
    }

    /**
     * Returns an XML representation of this extension.
     *
     * @return an XML representation of this extension.
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

        for (String role : roles) {
            xml.optElement(ELEMENT_ROLE, role);
        }

        for (ExtensionElement ext : getChildExtensions()) {
            xml.append(ext.toXML(XmlEnvironment.EMPTY));
        }
        xml.closeElement(getElementName());
        return xml;
    }
}
