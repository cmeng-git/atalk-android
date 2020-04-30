/*
 * Jigasi, the JItsi GAteway to SIP.
 *
 * Copyright @ 2015 Atlassian Pty Ltd
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
 * The packet extension added to the MUC presence to broadcast current
 * transcription status to all conference participants.
 *
 * Status meaning:
 * <tt>{@link Status#OFF}</tt> - transcription stopped(available to start)
 * <tt>{@link Status#ON}</tt> - transcription in progress
 */
public class TranscriptionStatusExtension extends AbstractExtensionElement
{
    /**
     * XML element name of this packet extension.
     */
    public static final String ELEMENT = "transcription-status";

    /**
     * The namespace of this packet extension.
     */
    public static final String NAMESPACE = "jabber:client";

    public static final QName QNAME = new QName(NAMESPACE, ELEMENT);

    /**
     * The name of XML attribute which holds the transcription status.
     */
    private static final String STATUS_ATTRIBUTE = "status";

    /**
     * Constructs new TranscriptionStatusExtensionElement.
     */
    public TranscriptionStatusExtension()
    {
        super(ELEMENT, NAMESPACE);
    }

    /**
     * Sets new value for the transcription status.
     * @param status one of {@link Status}
     */
    public void setStatus(Status status)
    {
        setAttribute(STATUS_ATTRIBUTE, String.valueOf(status));
    }

    /**
     * Gets the value of the transcription status
     *
     * @return one of the {@link Status}
     */
    public Status getStatus()
    {
        return Status.valueOf(((String) getAttribute(STATUS_ATTRIBUTE)).toUpperCase());
    }

    /**
     * The enumeration of recording status values.
     */
    public enum Status
    {
        /**
         * Transcription is in progress.
         */
        ON("ON"),

        /**
         * Transcription stopped.
         */
        OFF("OFF");

        /**
         * Status name holder.
         */
        private String name;

        /**
         * Creates new {@link Status} instance.
         * @param name a string corresponding to one of {@link Status} values.
         */
        Status(String name)
        {
            this.name = name;
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public String toString()
        {
            return name;
        }

    }
}

