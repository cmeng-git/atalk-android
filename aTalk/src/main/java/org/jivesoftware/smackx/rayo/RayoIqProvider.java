/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client. Implementing: XEP-0327: Rayo
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
package org.jivesoftware.smackx.rayo;

import org.apache.commons.lang3.StringUtils;
import org.jivesoftware.smack.xml.XmlPullParser;
import org.jivesoftware.smack.xml.XmlPullParserException;
import org.jivesoftware.smackx.DefaultExtensionElementProvider;

import org.jivesoftware.smack.packet.*;
import org.jivesoftware.smack.provider.IQProvider;
import org.jivesoftware.smack.provider.ProviderManager;
import org.jxmpp.jid.Jid;

import java.io.IOException;

/**
 * Provider handles parsing of Rayo IQ stanzas and converting objects back to their XML representation.
 *
 * FIXME: implements only the minimum required to start and hang up a call
 *
 * @author Pawel Domas
 */
public class RayoIqProvider extends IQProvider<RayoIqProvider.RayoIq>
{
    /**
     * Rayo namespace.
     */
    public final static String NAMESPACE = "urn:xmpp:rayo:1";

    /**
     * Registers this IQ provider into given <code>ProviderManager</code>.
     *
     * @param providerManager the <code>ProviderManager</code> to which this instance wil be bound to.
     */
    public void registerRayoIQs(ProviderManager providerManager)
    {
        // <dial>
        ProviderManager.addIQProvider(DialIq.ELEMENT, NAMESPACE, this);
        // <ref>
        ProviderManager.addIQProvider(RefIq.ELEMENT, NAMESPACE, this);
        // <hangup>
        ProviderManager.addIQProvider(HangUp.ELEMENT, NAMESPACE, this);
        // <end> presence extension
        ProviderManager.addExtensionProvider(EndExtension.ELEMENT, NAMESPACE,
                new DefaultExtensionElementProvider<>(EndExtension.class));
        // <header> extension
        ProviderManager.addExtensionProvider(HeaderExtension.ELEMENT, "",
                new DefaultExtensionElementProvider<>(HeaderExtension.class));
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public RayoIq parse(XmlPullParser parser, int initialDepth, XmlEnvironment xmlEnvironment)
            throws IOException, XmlPullParserException
    {
        String namespace = parser.getNamespace();

        // Check the namespace
        if (!NAMESPACE.equals(namespace)) {
            return null;
        }

        String rootElement = parser.getName();
        RayoIq iq;
        DialIq dial;
        RefIq ref;
        // End end = null;

        if (DialIq.ELEMENT.equals(rootElement)) {
            iq = dial = new DialIq();
            String src = parser.getAttributeValue("", DialIq.SRC_ATTR_NAME);
            String dst = parser.getAttributeValue("", DialIq.DST_ATTR_NAME);

            // Destination is mandatory
            if (StringUtils.isEmpty(dst))
                return null;

            dial.setSource(src);
            dial.setDestination(dst);
        }
        else if (RefIq.ELEMENT.equals(rootElement)) {
            iq = ref = new RefIq();
            String uri = parser.getAttributeValue("", RefIq.URI_ATTR_NAME);
            if (StringUtils.isEmpty(uri))
                return null;
            ref.setUri(uri);
        }
        else if (HangUp.ELEMENT.equals(rootElement)) {
            iq = new HangUp();
        }
        /*
         * else if (End.ELEMENT.equals(rootElement)) { iq = end = new End(); }
         */
        else {
            return null;
        }

        boolean done = false;
        HeaderExtension header = null;
        // JingleReason reason = null;

        while (!done) {
            switch (parser.next()) {
                case END_ELEMENT: {
                    String name = parser.getName();
                    if (rootElement.equals(name)) {
                        done = true;
                    }
                    else if (HeaderExtension.ELEMENT.equals(name)) {
                        if (header != null) {
                            iq.addExtension(header);
                            header = null;
                        }
                    }
                    /*
                     * else if (End.isValidReason(name)) { if (end != null && reason != null) {
                     * end.setReason(reason); reason = null; } }
                     */
                    break;
                }

                case START_ELEMENT: {
                    String name = parser.getName();

                    if (HeaderExtension.ELEMENT.equals(name)) {
                        header = new HeaderExtension();
                        String nameAttr = parser.getAttributeValue("",
                                HeaderExtension.NAME_ATTR_NAME);
                        header.setName(nameAttr);
                        String valueAttr = parser.getAttributeValue("",
                                HeaderExtension.VALUE_ATTR_NAME);
                        header.setValue(valueAttr);
                    }
                    /*
                     * else if (End.isValidReason(name)) {
                     * 	reason = new JingleReason(name);
                     *
                     * String platformCode = parser.getAttributeValue( "",
                     * JingleReason.PLATFORM_CODE_ATTRIBUTE);
                     *
                     * if (StringUtils.isNotEmpty(platformCode)) {
                     * reason.setPlatformCode(platformCode); } }
                     */
                    break;
                }
                case TEXT_CHARACTERS: {
                    // Parse some text here
                    break;
                }
            }
        }
        return iq;
    }

    /**
     * Base class for all Ray IQs. Takes care of <header /> extension handling as well as other
     * functions shared by all IQs.
     */
    public static abstract class RayoIq extends IQ
    {
        /**
         * Creates new instance of <code>RayoIq</code>.
         *
         * @param elementName the name of XML element that will be used.
         */

        protected RayoIq(String elementName)
        {
            super(elementName, NAMESPACE);
        }

        /**
         * Creates new instance of this class as a copy from <code>original</code>.
         *
         * @param original the class to copy the data from.
         */
        protected RayoIq(RayoIq original)
        {
            super(original);
        }

        /**
         * Returns value of the header extension with given <code>name</code> (if any).
         *
         * @param name the name of header extension which value we want to retrieve.
         * @return value of header extension with given <code>name</code> if it exists or <code>null</code> otherwise.
         */
        public String getHeader(String name)
        {
            HeaderExtension header = findHeader(name);
            return header != null ? header.getValue() : null;
        }

        private HeaderExtension findHeader(String name)
        {
            for (ExtensionElement ext : getExtensions()) {
                if (ext instanceof HeaderExtension) {
                    HeaderExtension header = (HeaderExtension) ext;

                    if (name.equals(header.getName()))
                        return header;
                }
            }
            return null;
        }

        /**
         * Adds 'header' extension to this Rayo IQ with given name and value attributes.
         *
         * @param name the attribute name of the 'header' extension to be added.
         * @param value the 'value' attribute of the 'header' extension that will be added to this IQ.
         */
        public void setHeader(String name, String value)
        {
            HeaderExtension headerExt = findHeader(name);

            if (headerExt == null) {
                headerExt = new HeaderExtension();
                headerExt.setName(name);
                addExtension(headerExt);
            }
            headerExt.setValue(value);
        }
    }

    /**
     * The 'dial' IQ used to initiate new outgoing call session in Rayo protocol.
     */
    public static class DialIq extends RayoIq
    {
        /**
         * The name of XML element for this IQ.
         */
        public static final String ELEMENT = "dial";

        /**
         * The name of source URI/address attribute. Referred as "source" to avoid confusion with
         * "getFrom" and "setFrom" in {@link IQ} class.
         */
        public static final String SRC_ATTR_NAME = "from";

        /**
         * The name of destination URI/address attribute. Referred as "source" to avoid confusion
         * with "getFrom" and "setFrom" in {@link IQ} class.
         */
        public static final String DST_ATTR_NAME = "to";

        /**
         * Source URI/address.
         */
        private String source;

        /**
         * Destination URI/address.
         */
        private String destination;

        /**
         * Creates new instance of <code>DialIq</code>.
         */
        public DialIq()
        {
            super(DialIq.ELEMENT);
        }

        /**
         * Creates a new instance of this class as a copy from <code>original</code>.
         * @param original the class to copy the data from.
         */
        public DialIq(DialIq original)
        {
            // copies: id, to, from, extensions, error, type
            super(original);
            source = original.source;
            destination = original.destination;
        }

        /**
         * Creates new <code>DialIq</code> for given source and destination addresses.
         * @param to the destination address/call URI to be used.
         * @param from the source address that will be set on new <code>DialIq</code> instance.
         * @return new <code>DialIq</code> parametrized with given source and destination addresses.
         */
        public static DialIq create(String to, String from)
        {
            DialIq dialIq = new DialIq();
            dialIq.setSource(from);
            dialIq.setDestination(to);
            return dialIq;
        }

        /**
         * Return source address value set on this <code>DialIq</code>.
         *
         * @return source address value of this <code>DialIq</code>.
         */
        public String getSource()
        {
            return source;
        }

        /**
         * Sets new source address value on this <code>DialIq</code>.
         *
         * @param source the new source address value to be set.
         */
        public void setSource(String source)
        {
            this.source = source;
        }

        /**
         * Returns destination address/call URI associated with this instance.
         *
         * @return destination address/call URI associated with this instance.
         */
        public String getDestination()
        {
            return destination;
        }

        /**
         * Sets new destination address/call URI on this <code>DialIq</code>.
         *
         * @param destination the new destination address/call URI to set.
         */
        public void setDestination(String destination)
        {
            this.destination = destination;
        }

        /**
         * {@inheritDoc}
         */

        @Override
        protected IQChildElementXmlStringBuilder getIQChildElementBuilder(IQChildElementXmlStringBuilder xml)
        {
            xml.optAttribute(SRC_ATTR_NAME, source);
            xml.optAttribute(DST_ATTR_NAME, destination);
            xml.setEmptyElement();
            return xml;
        }
    }

    /**
     * Rayo 'ref' IQ sent by the server as a reply to 'dial' request. Holds created call's resource in 'uri' attribute.
     */
    public static class RefIq extends RayoIq
    {
        /**
         * XML element name of <code>RefIq</code>.
         */
        public static final String ELEMENT = "ref";

        /**
         * Name of the URI attribute that stores call resource reference.
         */
        public static final String URI_ATTR_NAME = "uri";

        /**
         * Call resource/uri reference.
         */
        private String uri;

        /**
         * Creates new <code>RefIq</code>.
         */
        protected RefIq()
        {
            super(RefIq.ELEMENT);
        }

        /**
         * Creates new <code>RefIq</code> parametrized with given call <code>uri</code>.
         *
         * @param uri the call URI to be set on newly created <code>RefIq</code>.
         * @return new <code>RefIq</code> parametrized with given call <code>uri</code>.
         */
        public static RefIq create(String uri)
        {
            RefIq refIq = new RefIq();
            refIq.setUri(uri);
            return refIq;
        }

        /**
         * Creates result <code>RefIq</code> for given <code>requestIq</code> parametrized with given call <code>uri</code>.
         *
         * @param requestIq the request IQ which 'from', 'to' and 'id' attributes will be used for
         * constructing result IQ.
         * @param uri the call URI that will be included in newly created <code>RefIq</code>.
         * @return result <code>RefIq</code> for given <code>requestIq</code> parametrized with given call <code>uri</code>.
         */
        public static RefIq createResult(IQ requestIq, String uri)
        {
            RefIq refIq = create(uri);
            refIq.setType(IQ.Type.result);
            refIq.setStanzaId(requestIq.getStanzaId());
            refIq.setFrom(requestIq.getTo());
            refIq.setTo(requestIq.getFrom());
            return refIq;
        }

        /**
         * {@inheritDoc}
         */
        @Override
        protected IQChildElementXmlStringBuilder getIQChildElementBuilder(IQChildElementXmlStringBuilder xml)
        {
            xml.optAttribute(URI_ATTR_NAME, uri);
            xml.setEmptyElement();
            return xml;
        }

        /**
         * Sets given call <code>uri</code> value on this instance.
         *
         * @param uri the call <code>uri</code> to be stored in this instance.
         */
        public void setUri(String uri)
        {
            this.uri = uri;
        }

        /**
         * Returns call URI held by this instance.
         *
         * @return the call URI held by this instance.
         */
        public String getUri()
        {
            return uri;
        }
    }

    /**
     * Rayo hangup IQ is sent by the controlling agent to tell the server that call whose resource
     * is mentioned in IQ's 'to' attribute should be terminated. Server immediately replies with
     * result IQ which means that hangup operation is now scheduled. After it is actually executed
     * presence indication with {@link EndExtension} is sent through the presence to confirm the operation.
     */
    public static class HangUp extends RayoIq
    {
        /**
         * The name of 'hangup' element.
         */
        public static final String ELEMENT = "hangup";

        /**
         * Creates new instance of <code>HangUp</code> IQ.
         */
        protected HangUp()
        {
            super(ELEMENT);
        }

        /**
         * Creates new, parametrized instance of {@link HangUp} IQ.
         *
         * @param from source JID.
         * @param to the destination address/call URI to be ended by this IQ.
         * @return new, parametrized instance of {@link HangUp} IQ.
         */
        public static HangUp create(Jid from, Jid to)
        {
            HangUp hangUp = new HangUp();
            hangUp.setFrom(from);
            hangUp.setTo(to);
            hangUp.setType(Type.set);
            return hangUp;
        }

        @Override
        protected IQChildElementXmlStringBuilder getIQChildElementBuilder(IQChildElementXmlStringBuilder xml)
        {
            xml.setEmptyElement();
            return xml;
        }
    }
}
