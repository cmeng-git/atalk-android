/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
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
package org.jivesoftware.smackx.jibri;

import javax.xml.namespace.QName;

import org.apache.commons.lang3.StringUtils;
import org.jivesoftware.smack.provider.ProviderManager;
import org.jivesoftware.smackx.AbstractExtensionElement;
import org.jivesoftware.smackx.DefaultExtensionElementProvider;

/**
 * Status extension included in MUC presence by Jibri to indicate it's status.
 * One of:
 * <li>idle</li> - the instance is idle and can be used for recording
 * <li>busy</li> - the instance is currently recording or doing something very
 * important and should not be disturbed
 */
public class JibriBusyStatusExtension extends AbstractExtensionElement {
    /**
     * The namespace of this packet extension.
     */
    public static final String NAMESPACE = JibriIq.NAMESPACE;

    /**
     * XML element name of this packet extension.
     */
    public static final String ELEMENT = "busy-status";

    public static final QName QNAME = new QName(NAMESPACE, ELEMENT);

    private static final String STATUS_ATTRIBUTE = "status";

    /**
     * Creates new instance of <code>VideoMutedExtensionElement</code>.
     */
    public JibriBusyStatusExtension() {
        super(ELEMENT, NAMESPACE);
    }

    static public void registerExtensionProvider() {
        ProviderManager.addExtensionProvider(ELEMENT, NAMESPACE,
                new DefaultExtensionElementProvider<>(JibriBusyStatusExtension.class));
    }

    public BusyStatus getStatus() {
        return BusyStatus.parse(getAttributeAsString(STATUS_ATTRIBUTE));
    }

    public void setStatus(BusyStatus status) {
        setAttribute(STATUS_ATTRIBUTE, String.valueOf(status));
    }

    public enum BusyStatus {
        IDLE("idle"),
        BUSY("busy"),
        UNDEFINED("undefined");

        private String name;

        BusyStatus(String name) {
            this.name = name;
        }

        @Override
        public String toString() {
            return name;
        }

        /**
         * Parses <code>Status</code> from given string.
         *
         * @param status the string representation of <code>Status</code>.
         *
         * @return <code>Status</code> value for given string or
         * {@link #UNDEFINED} if given string does not reflect any of valid values.
         */
        public static BusyStatus parse(String status) {
            if (StringUtils.isEmpty(status))
                return UNDEFINED;

            try {
                return BusyStatus.valueOf(status.toUpperCase());
            } catch (IllegalArgumentException e) {
                return UNDEFINED;
            }
        }
    }
}
