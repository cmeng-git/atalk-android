/**
 * Copyright 2017-2022 Eng Chong Meng
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
package org.jivesoftware.smackx.coin;

import org.jivesoftware.smackx.jingle_rtp.AbstractXmlElement;

import javax.xml.namespace.QName;

/**
 * Represents the conference information.
 * XEP-0298: Delivering Conference Information to Jingle Participants (Coin) 0.2 (2015-07-02)
 * https://xmpp.org/extensions/xep-0298.html
 *
 * @author Sebastien Vincent
 * @author Eng Chong Meng
 */
public class CoinExtension extends AbstractXmlElement
{
    /**
     * Name of the XML element representing the extension.
     */
    public final static String ELEMENT = "conference-info";

    /**
     * Namespace.
     */
    public final static String NAMESPACE = "urn:xmpp:coin:1";

    public static final QName QNAME = new QName(NAMESPACE, ELEMENT);

    /**
     * IsFocus attribute name.
     */
    public final static String ATTR_ISFOCUS = "isfocus";

    /**
     * <code>CoinExtension</code> default constructor; use in DefaultXmlElementProvider, and newInstance() etc
     */
    public CoinExtension()
    {
        super(getBuilder());
    }

    /**
     * Initializes a new <code>CoinExtension</code> instance.; required by DefaultXmlElementProvider()
     *
     * @param builder Builder instance
     */
    public CoinExtension(Builder builder)
    {
        super(builder);
    }

    public boolean isFocus()
    {
        String inFocus = getAttributeValue(ATTR_ISFOCUS);
        return Boolean.parseBoolean(inFocus);
    }


    public static Builder getBuilder()
    {
        return new Builder(ELEMENT, NAMESPACE);
    }

    /**
     * Builder for CoinExtension. Use {@link AbstractXmlElement.Builder#Builder(String, String)} to
     * obtain a new instance and {@link #build} to build the CoinExtension.
     */
    public static class Builder extends AbstractXmlElement.Builder<Builder, CoinExtension>
    {
        protected Builder(String element, String namespace)
        {
            super(element, namespace);
        }

        /**
         * <code>coin</code> isfocus attribute.
         *
         * @param isFocus <code>true</code> if the peer is a conference focus; otherwise, <code>false</code>
         */
        public Builder setFocus(boolean isFocus)
        {
            addAttribute(ATTR_ISFOCUS, Boolean.toString(isFocus));
            return this;
        }

        /**
         * Sets the value of this bandwidth extension.
         *
         * @param bw the value of this bandwidth extension.
         */
        public Builder setBandwidth(String bw)
        {
            setText(bw);
            return this;
        }

        @Override
        public CoinExtension build()
        {
            return new CoinExtension(this);
        }

        @Override
        public Builder getThis()
        {
            return this;
        }
    }
}
