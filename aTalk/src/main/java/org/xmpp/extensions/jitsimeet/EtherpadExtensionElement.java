/*
 * Copyright @ 2018 - present 8x8, Inc.
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

/**
 * A packet extension used to advertise the name for shared Etherpad document
 * in Jitsi Meet conference.
 *
 * @author Pawel Domas
 * @author Eng Chong Meng
 */
public class EtherpadExtensionElement extends AbstractExtensionElement
{
    /**
     * XML namespace of this packets extension.
     */
    public static final String NAMESPACE = "http://jitsi.org/jitmeet/etherpad";

    /**
     * XML element name of this packets extension.
     */
    public static final String ELEMENT_NAME = "etherpad";

    /**
     * Creates new instance of <tt>EtherpadExtensionElement</tt>.
     */
    public EtherpadExtensionElement()
    {
        super(ELEMENT_NAME, NAMESPACE);
    }

    /**
     * Sets the <tt>name</tt> of Etherpad document to be shared in Jitsi Meet conference.
     *
     * @param name the name of the document to set.
     */
    public void setDocumentName(String name)
    {
        setText(name);
    }

    /**
     * Returns the name of shared Etherpad document.
     */
    public String getDocumentName()
    {
        return getText();
    }

    /**
     * Return new Etherpad packet extension instance with given document <tt>name</tt>.
     *
     * @param name the name of shared Etherpad document.
     */
    public static EtherpadExtensionElement forDocumentName(String name)
    {
        EtherpadExtensionElement ext = new EtherpadExtensionElement();
        ext.setDocumentName(name);
        return ext;
    }
}
