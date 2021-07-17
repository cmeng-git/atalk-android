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
package org.jivesoftware.smackx.captcha.packet;

import org.jivesoftware.smack.packet.ExtensionElement;
import org.jivesoftware.smack.packet.XmlEnvironment;
import org.jivesoftware.smack.util.XmlStringBuilder;
import org.jivesoftware.smackx.xdata.packet.DataForm;

import javax.xml.namespace.QName;

/**
 * The <tt>Captcha</tt> implementing XEP-0158: CAPTCHA Forms is an extension element to
 * include data Form, information in an XMPP stanza. Its use include fighting against Spam group chat
 *
 * @author Eng Chong Meng
 */
public class CaptchaExtension implements ExtensionElement
{
    /**
     * The name of the "captcha" element.
     */
    public static final String ELEMENT = "captcha";

    /**
     * The names XMPP space that the captcha elements belong to.
     */
    public static final String NAMESPACE = "urn:xmpp:captcha";

    public static final QName QNAME = new QName(NAMESPACE, ELEMENT);

    /**
     * Captcha FormField variables
     */
    public static final String FROM = "from";
    public static final String CHALLENGE = "challenge";
    public static final String SID = "sid";
    public static final String OCR = "ocr";

    public static final String ANSWER = "answers";
    public static final String USER_NAME = "username";
    public static final String PASSWORD = "password";

    /**
     */
    private DataForm mForm;

    /**
     * Creates a <tt>Captcha</tt> form, by specifying the form
     *
     * @param form the DataForm
     */
    public CaptchaExtension(DataForm form)
    {
        this.mForm = form;
    }

    /**
     * Returns the data of the bob.
     *
     * @return the data of the bob
     */
    public DataForm getDataForm()
    {
        return mForm;
    }

    /**
     * Returns the root element name.
     *
     * @return the element name.
     */
    public String getElementName()
    {
        return ELEMENT;
    }

    /**
     * Returns the root element XML namespace.
     *
     * @return the namespace.
     */
    public String getNamespace()
    {
        return NAMESPACE;
    }

    /**
     * Returns the XML representation of the <tt>Captcha</tt>.
     *
     * <captcha xmlns='urn:xmpp:captcha'>
     * <x xmlns='jabber:x:data' type='submit'>
     * <field var='FORM_TYPE'><value>urn:xmpp:captcha</value></field>
     * <field var='from'><value>innocent@victim.com</value></field>
     * <field var='challenge'><value>F3A6292C</value></field>
     * <field var='sid'><value>spam1</value></field>
     * <field var='ocr'><value>7nHL3</value></field>
     * </x>
     * </captcha>
     */
    @Override
    public CharSequence toXML(XmlEnvironment xmlEnvironment)
    {
        XmlStringBuilder xml = new XmlStringBuilder(this);
        xml.append('>');
        if (mForm != null) {
            xml.append(mForm.toXML(XmlEnvironment.EMPTY));
        }
        xml.closeElement(this);
        return xml;
    }
}
