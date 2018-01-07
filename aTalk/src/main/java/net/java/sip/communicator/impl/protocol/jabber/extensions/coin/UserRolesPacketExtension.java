/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 * 
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package net.java.sip.communicator.impl.protocol.jabber.extensions.coin;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import net.java.sip.communicator.impl.protocol.jabber.extensions.AbstractPacketExtension;

import org.jivesoftware.smack.packet.ExtensionElement;
import org.jivesoftware.smack.util.XmlStringBuilder;

/**
 * User roles packet extension.
 *
 * @author Sebastien Vincent
 * @author Eng Chong Meng
 */
public class UserRolesPacketExtension extends AbstractPacketExtension
{
	/**
	 * The name of the element that contains the user roles data.
	 */
	public static final String ELEMENT_NAME = "roles";

	/**
	 * The namespace that user roles belongs to.
	 */
	// cmeng - temporary fix non-null
	public static final String NAMESPACE = "roles";

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
	public UserRolesPacketExtension()
	{
		super(ELEMENT_NAME, NAMESPACE);
	}

	/**
	 * Add roles.
	 *
	 * @param role
	 *        role to add
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
	public XmlStringBuilder toXML()
	{
		XmlStringBuilder xml = new XmlStringBuilder();
		xml.prelude(getElementName(), getNamespace());

		// add the rest of the attributes if any
		for (Map.Entry<String, Object> entry : attributes.entrySet()) {
			xml.optAttribute(entry.getKey(), entry.getValue().toString());
		}
		xml.append(">");

		for (String role : roles) {
			xml.optElement(ELEMENT_ROLE, role);
		}

		for (ExtensionElement ext : getChildExtensions()) {
			xml.append(ext.toXML());
		}

		xml.closeElement(getElementName());
		return xml;
	}
}
