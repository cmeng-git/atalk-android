/*
 * Copyright @ 2019 8x8, Inc
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
package org.xmpp.extensions.jitsimeet;

import org.xmpp.extensions.*;

import javax.xml.namespace.QName;

/**
 * A packet extension which contains the information about the current
 * bridge session.
 *
 * @author Boris Grozev
 * @author Pawel Domas
 * @author Eng Chong Meng
 */
public class BridgeSessionExtension extends AbstractExtensionElement
{
    /**
     * The name of the {@code bridge-session} element.
     */
    public static final String ELEMENT = "bridge-session";

    /**
     * The name of the attribute which carries the bridge session's ID.
     */
    public static final String ID_ATTR_NAME = "id";

    /**
     * The namespace for the {@code bridge-session} element.
     */
    public static final String NAMESPACE = ConferenceIq.NAMESPACE;

    public static final QName QNAME = new QName(NAMESPACE, ELEMENT);

    /**
     * The name of the "region" attribute.
     */
    public static final String REGION_ATTR_NAME = "region";

    /**
     * Creates new instance of {@code BridgeSessionExtensionElement}.
     */
    public BridgeSessionExtension()
    {
        super(ELEMENT, NAMESPACE);
    }

    public BridgeSessionExtension(String id, String region)
    {
        this();
        setId(id);
        setRegion(region);
    }

    /**
     * @return the region.
     */
    public String getRegion()
    {
        return getAttributeAsString(REGION_ATTR_NAME);
    }

    /**
     * Sets the region.
     * @param region the value to set.
     */
    public void setRegion(String region)
    {
        setAttribute(REGION_ATTR_NAME, region);
    }

    /**
     * The bridge session id.
     * @return the id.
     */
    public String getId()
    {
        return getAttributeAsString(ID_ATTR_NAME);
    }

    /**
     * Sets the id.
     * @param id the value to set.
     */
    public void setId(String id)
    {
        setAttribute(ID_ATTR_NAME, id);
    }
}
