/**
 *
 * Copyright 2003-2007 Jive Software.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.jivesoftware.smackx.iqregisterx.packet;

import org.jivesoftware.smack.packet.*;
import org.jivesoftware.smackx.bob.packet.BoB;
import org.jivesoftware.smackx.xdata.packet.DataForm;

import java.util.Map;

/**
 * XEP-0077: In-Band Registration Implementation with fields elements and DataForm
 * Represents registration packets. An empty GET query will cause the server to return information
 * about its registration support and requirements. SET queries can be used to create accounts or
 * update existing account information. XMPP servers will require a number of parameters to be
 * set; presented in the form of attributes fields and/or DataForm. The entity should only
 * return either one of them but not both.
 * when creating a new account. The standard account parameters either given in field or
 * DataForm are as follows:
 * <ul>
 *      <li>username -- Account name associated with the user (Required).
 *      <li>password -- Password or secret for the user (Required).
 *      <li>nick     -- Familiar name of the user.
 *      <li>name     -- Full name of the user.
 *      <li>first    -- Given name of the user.
 *      <li>last     -- Family name of the user.
 *      <li>email    -- Email address of the user.
 *      <li>address  -- Street portion of a physical or mailing address.
 *      <li>city     -- Locality portion of a physical or mailing address.
 *      <li>state    -- Region portion of a physical or mailing address.
 *      <li>zip      -- Postal code portion of a physical or mailing address.
 *      <li>phone    -- Telephone number of the user.
 *      <li>url      -- URL to web page describing the user.
 *      <li>date     -- Some date (e.g., birth date, hire date, sign-up date).
 *      <li>remove   -- empty flag to remove account.
 * </ul>
 *
 * The Registration can supported via DataForm with Captcha protection
 *
 * @author Matt Tucker
 * @author Eng Chong Meng
 */
public class Registration extends IQ {

    public static final String ELEMENT = QUERY_ELEMENT;
    public static final String NAMESPACE = "jabber:iq:register";
    public static final String ELE_REGISTERED = "registered";

    private final Map<String, String> attributes;
    private final DataForm mDataForm;

    private boolean hasRegistered = false;
    private String instructions;
    private BoB mBoB = null;

    public Registration() {
        this(null, null);
    }

    public Registration(Map<String, String> attributes) {
        this(attributes, null);
    }

    public Registration(DataForm dataForm) {
        this(null, dataForm);
    }

    public Registration(Map<String, String> attributes, DataForm dataForm) {
        super(ELEMENT, NAMESPACE);
        this.attributes = attributes;
        this.mDataForm = dataForm;
    }

    /**
     * Returns the registration instructions, or <tt>null</tt> if no instructions
     * have been set. If present, instructions should be displayed to the end-user
     * that will complete the registration process.
     *
     * @return the registration instructions, or <tt>null</tt> if there are none.
     */
    public String getInstructions() {
        return instructions;
    }

    public void setInstructions(String msg) {
        instructions = msg;
    }

    /**
     * Returns the map of String key/value pairs of account attributes.
     *
     * @return the account attributes.
     */
    public Map<String, String> getAttributes() {
        return attributes;
    }

    /**
     * return the account registration status.
     *
     * @return the account registration status.
     * <tt>true</tt> account has already registered
     */
    public boolean isRegistered() {
        return hasRegistered;
    }

    public void setRegistrationStatus(boolean isRegistered) {
         hasRegistered = isRegistered;
    }

    /**
     * Returns the DataForm in the registration.
     *
     * @return the DataForm in the registration.
     */
    public DataForm getDataForm() {
        return mDataForm;
    }

    /**
     * Returns the BoB in the registration.
     *
     * @return the BoB in the registration.
     */
    public BoB getBoB() {
        return mBoB;
    }

    public void setBoB(BoB bob) {
        mBoB = bob;
    }

    @Override
    protected IQChildElementXmlStringBuilder getIQChildElementBuilder(IQChildElementXmlStringBuilder xml) {
        xml.rightAngleBracket();
        // attributes and mDataForm are mutually exclusive in account registration
        if (attributes != null && attributes.size() > 0) {
            for (String name : attributes.keySet()) {
                String value = attributes.get(name);
                xml.element(name, value);
            }
        }
        else if (mDataForm != null) {
            xml.append(mDataForm.toXML());
        }
        return xml;
    }

    public static final class Feature implements ExtensionElement {
        public static final String ELEMENT = "register";
        public static final String NAMESPACE = "http://jabber.org/features/iq-register";
        public static final Feature INSTANCE = new Registration.Feature();

        private Feature() {
        }

        @Override
        public String getElementName() {
            return ELEMENT;
        }

        @Override
        public CharSequence toXML() {
            return '<' + ELEMENT + " xmlns='" + NAMESPACE + "'/>";
        }

        @Override
        public String getNamespace() {
            return NAMESPACE;
        }
    }
}
