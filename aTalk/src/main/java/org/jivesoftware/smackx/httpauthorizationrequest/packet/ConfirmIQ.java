/*
 * aTalk, android VoIP and Instant Messaging client
 * Copyright 2014 Eng Chong Meng
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.jivesoftware.smackx.httpauthorizationrequest.packet;

import org.jivesoftware.smackx.httpauthorizationrequest.element.ConfirmExtension;
import org.jivesoftware.smack.packet.*;
import org.jivesoftware.smack.util.XmlStringBuilder;
import org.jxmpp.jid.Jid;

/**
 * An HTTP Requests IQ implementation for retrieving information about an HTTP Authorization request via IQ.
 * XEP-0070: Verifying HTTP Requests via XMPP
 *
 * @author Eng Chong Meng
 */
public class ConfirmIQ extends IQ
{

    public static final String ELEMENT = ConfirmExtension.ELEMENT;
    public static final String NAMESPACE = ConfirmExtension.NAMESPACE;

    private ConfirmExtension confirmExtension = null;
    private StanzaError stanzaError = null;

    public ConfirmIQ()
    {
        super(ELEMENT, NAMESPACE);
        setType(IQ.Type.get);
    }

    public ConfirmIQ(ConfirmIQ iq)
    {
        super(iq);
        this.confirmExtension = iq.getConfirmExtension();
    }

    public ConfirmIQ(ConfirmExtension confirmExtension)
    {
        this();
        this.confirmExtension = confirmExtension;
    }

    public ConfirmExtension getConfirmExtension()
    {
        return confirmExtension;
    }

    public void initializeAsError(StanzaError stanzaError)
    {
        this.stanzaError = stanzaError;

        Jid from = this.getFrom();
        setFrom(this.getTo());
        setTo(from);
        setType(Type.error);
    }

    @Override
    protected IQChildElementXmlStringBuilder getIQChildElementBuilder(IQChildElementXmlStringBuilder xml)
    {
        if (confirmExtension != null) {
            xml.setEmptyElement();

            xml.attribute(ConfirmExtension.ATTR_ID, confirmExtension.getId());
            xml.attribute(ConfirmExtension.ATTR_METHOD, confirmExtension.getMethod());
            xml.attribute(ConfirmExtension.ATTR_URL, confirmExtension.getUrl());
        }
        else {
            return null;
        }
        return xml;
    }

    /**
     * Append an XMPPError is this stanza has one set.
     *
     * @param xml the XmlStringBuilder to append the error to.
     */
    @Override
    protected void appendErrorIfExists(XmlStringBuilder xml, XmlEnvironment enclosingXmlEnvironment)
    {
        IQChildElementXmlStringBuilder iqChildElement
                = getIQChildElementBuilder(new IQChildElementXmlStringBuilder(confirmExtension));

        if (iqChildElement != null) {
            xml.append(iqChildElement);
            xml.closeEmptyElement();
        }

        if (stanzaError != null) {
            xml.append(stanzaError.toXML(enclosingXmlEnvironment));
        }
    }
}
