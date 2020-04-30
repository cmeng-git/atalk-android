/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Copyright @ 2018 Atlassian Pty Ltd
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

import org.xmpp.extensions.AbstractExtensionElement;

import javax.xml.namespace.QName;

/**
 * An extension of the presence stanza for sending target translation language
 * to Jigasi.
 * The extension looks like follows:
 *
 * <pre>
 *  {@code <jitsi_participant_translation_language> target_language_code
 *  </jitsi_participant_translation_language>}
 *  </pre>
 *
 * @author Praveen Kumar Gupta
 * @author Eng Chong Meng
 */
public class TranslationLanguageExtension extends AbstractExtensionElement
{
    /**
     * The namespace of this packet extension.
     */
    public static final String NAMESPACE = "jabber:client";

    /**
     * XML element name of this packet extension.
     */
    public static final String ELEMENT = "jitsi_participant_translation_language";

    public static final QName QNAME = new QName(NAMESPACE, ELEMENT);

    /**
     * Creates a {@link TranslationLanguageExtension} instance.
     */
    public TranslationLanguageExtension()
    {
        super(ELEMENT, NAMESPACE);
    }

    /**
     * Returns the contents of this presence extension.
     *
     * @return target language translation code.
     */
    public String getTranslationLanguage()
    {
        return getText();
    }
}
