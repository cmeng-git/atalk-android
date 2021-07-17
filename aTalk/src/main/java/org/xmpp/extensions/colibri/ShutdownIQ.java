/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */
package org.xmpp.extensions.colibri;

import org.jivesoftware.smack.packet.IQ;

/**
 * The IQ used to trigger the graceful shutdown mode of the videobridge or force shutdown the one
 * which receives the stanza(given that source JID is authorized to do so).
 *
 * @author Pawel Domas
 * @author Eng Chong Meng
 */
public class ShutdownIQ extends IQ
{
    /**
     * Force shutdown IQ element name.
     */
    final static public String FORCE_ELEMENT = "force-shutdown";

    /**
     * Graceful shutdown IQ element name.
     */
    final static public String GRACEFUL_ELEMENT = "graceful-shutdown";

    /**
     * XML namespace name for shutdown IQs.
     */
    final static public String NAMESPACE = ColibriConferenceIQ.NAMESPACE;

    /**
     * The element name of this IQ. Either {@link #FORCE_ELEMENT} or
     * {@link #GRACEFUL_ELEMENT}. Default = GRACEFUL_ELEMENT
     */
    private static String elementName = GRACEFUL_ELEMENT;

    public ShutdownIQ()
    {
        super(elementName, NAMESPACE);
    }

    /**
     * Checks if given element is a valid one for <tt>ShutdownIQ</tt>.
     *
     * @param elementName the name if XML element name inside of the IQ.
     * @return <tt>true</tt> if given <tt>elementName</tt> is correct for <tt>ShutdownIQ</tt>.
     */
    public static boolean isValidElementName(String elementName)
    {
        return GRACEFUL_ELEMENT.equals(elementName) || FORCE_ELEMENT.equals(elementName);
    }

    /**
     * Creates shutdown IQ for given element name.
     *
     * @param elementName can be {@link #FORCE_ELEMENT} or {@link #GRACEFUL_ELEMENT}
     * @return new <tt>ShutdownIQ</tt> instance for given element name.
     * @throws IllegalArgumentException if given element name is neither {@link #FORCE_ELEMENT} nor
     * {@link #GRACEFUL_ELEMENT}.
     */
    public static ShutdownIQ createShutdownIQ(String elementName)
    {
        if (!isValidElementName(elementName)) {
            throw new IllegalArgumentException("Invalid element name: " + elementName);
        }

        if (GRACEFUL_ELEMENT.equals(elementName)) {
            return createGracefulShutdownIQ();
        }
        else {
            return createForceShutdownIQ();
        }
    }

    /**
     * Creates and returns new instance of graceful shutdown IQ.
     */
    public static ShutdownIQ createGracefulShutdownIQ()
    {
        elementName = GRACEFUL_ELEMENT;
        return new ShutdownIQ();
    }

    /**
     * Creates and returns new instance of force shutdown IQ.
     */
    public static ShutdownIQ createForceShutdownIQ()
    {
        elementName = FORCE_ELEMENT;
        return new ShutdownIQ();
    }

    /**
     * Returns <tt>true</tt> if this IQ instance is a "graceful shutdown" one. Otherwise it is a
     * force shutdown IQ.
     */
    public boolean isGracefulShutdown()
    {
        return elementName.equals(GRACEFUL_ELEMENT);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected IQChildElementXmlStringBuilder getIQChildElementBuilder(IQChildElementXmlStringBuilder xml)
    {
        xml.setEmptyElement();
        return xml;
    }
}
