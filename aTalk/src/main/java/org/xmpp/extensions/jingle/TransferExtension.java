/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.xmpp.extensions.jingle;

import org.xmpp.extensions.AbstractExtensionElement;

import org.jxmpp.jid.Jid;
import org.jxmpp.jid.impl.JidCreate;
import org.jxmpp.stringprep.XmppStringprepException;

import javax.xml.namespace.QName;

/**
 * Implements <tt>AbstractExtensionElement</tt> for the "transfer" element defined by XEP-0251:
 * Jingle Session Transfer.
 *
 * @author Lyubomir Marinov
 * @author Eng Chong Meng
 */
public class TransferExtension extends AbstractExtensionElement
{
    /**
     * The name of the "transfer" element.
     */
    public static final String ELEMENT = "transfer";

    /**
     * The namespace of the "transfer" element.
     */
    public static final String NAMESPACE = "urn:xmpp:jingle:transfer:0";

    public static final QName QNAME = new QName(NAMESPACE, ELEMENT);

    /**
     * The name of the "from" attribute of the "transfer" element.
     */
    public static final String FROM_ATTR_NAME = "from";

    /**
     * The name of the "sid" attribute of the "transfer" element.
     */
    public static final String SID_ATTR_NAME = "sid";

    /**
     * The name of the "to" attribute of the "transfer" element.
     */
    public static final String TO_ATTR_NAME = "to";

    /**
     * Initializes a new <tt>TransferExtensionElement</tt> instance.
     */
    public TransferExtension()
    {
        super(ELEMENT, NAMESPACE);
    }

    /**
     * Gets the value of the "from" attribute of this "transfer" element.
     *
     * @return the value of the "from" attribute of this "transfer" element
     */
    public Jid getFrom()
    {
        try {
            return JidCreate.from(getAttributeAsString(FROM_ATTR_NAME));
        } catch (XmppStringprepException | IllegalArgumentException e) {
            return null;
        }
    }

    /**
     * Sets the value of the "from" attribute of this "transfer" element.
     *
     * @param from the value of the "from" attribute of this "transfer" element
     */
    public void setFrom(Jid from)
    {
        setAttribute(FROM_ATTR_NAME, from);
    }

    /**
     * Gets the value of the "sid" attribute of this "transfer" element.
     *
     * @return the value of the "sid" attribute of this "transfer" element
     */
    public String getSid()
    {
        return getAttributeAsString(SID_ATTR_NAME);
    }

    /**
     * Sets the value of the "sid" attribute of this "transfer" element.
     *
     * @param sid the value of the "sid" attribute of this "transfer" element
     */
    public void setSID(String sid)
    {
        setAttribute(SID_ATTR_NAME, sid);
    }

    /**
     * Gets the value of the "to" attribute of this "transfer" element.
     *
     * @return the value of the "to" attribute of this "transfer" element
     */
    public Jid getTo()
    {
        try {
            return JidCreate.from(getAttributeAsString(TO_ATTR_NAME));
        } catch (XmppStringprepException e) {
            return null;
        }
    }

    /**
     * Sets the value of the "to" attribute of this "transfer" element.
     *
     * @param to the value of the "to" attribute of this "transfer" element
     */
    public void setTo(Jid to)
    {
        setAttribute(TO_ATTR_NAME, to);
    }
}
