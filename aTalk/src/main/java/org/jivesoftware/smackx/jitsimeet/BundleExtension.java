/**
 * Copyright 2017-2022 Jive Software
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
package org.jivesoftware.smackx.jitsimeet;

import org.jivesoftware.smackx.jingle_rtp.AbstractXmlElement;

import javax.xml.namespace.QName;

/**
 * Jitsi Meet specific bundle packet extension.
 *
 * @author Eng Chong Meng
 */
public class BundleExtension extends AbstractXmlElement
{
    /**
     * The XML element name of {@link BundleExtension}.
     */
    public static final String ELEMENT = "bundle";

    /**
     * The XML element namespace of {@link BundleExtension}.
     */
    public static final String NAMESPACE = "http://estos.de/ns/bundle";

    public static final QName QNAME = new QName(NAMESPACE, ELEMENT);

    public BundleExtension()
    {
        super(getBuilder());
    }

    /**
     * Creates a new <code>BundleExtension</code>; required by DefaultXmlElementProvider()
     */
    public BundleExtension(Builder builder)
    {
        super(builder);
    }

    public static Builder getBuilder()
    {
        return new Builder(ELEMENT, NAMESPACE);
    }

    /**
     * Builder for BundleExtension. Use {@link AbstractXmlElement.Builder#Builder(String, String)} to
     * obtain a new instance and {@link #build} to build the BundleExtension.
     */
    public static final class Builder extends AbstractXmlElement.Builder<Builder, BundleExtension>
    {
        protected Builder(String element, String namespace)
        {
            super(element, namespace);
        }

        @Override
        public BundleExtension build()
        {
            return new BundleExtension(this);
        }

        @Override
        public Builder getThis()
        {
            return this;
        }
    }
}
