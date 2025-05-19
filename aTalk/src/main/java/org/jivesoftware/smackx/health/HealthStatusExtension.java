/*
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
package org.jivesoftware.smackx.health;

import org.apache.commons.lang3.StringUtils;
import org.jivesoftware.smack.provider.ProviderManager;
import org.jivesoftware.smackx.AbstractExtensionElement;
import org.jivesoftware.smackx.DefaultExtensionElementProvider;

/**
 * A generic extension for a component to represent its current health.
 * NOTE: 'component' here does not refer to XMPP component, but any logical component of a system.
 * One of:
 * <li>healthy</li> - the component is healthy and can be considered as usable (other,
 * component-specific factors should of course also be considered.  A component may be
 * healthy but perhaps busy/unavailable to handle further requests at the moment).
 * <li>unhealthy</li> - the component is not in a healthy state and should not be considered
 * usable (despite what any other component-specific statuses may say).
 *
 * @author bbaldino
 * @author Eng Chong Meng
 */
public class HealthStatusExtension extends AbstractExtensionElement {
    /**
     * XML namespace name for health check IQs.
     */
    final static public String NAMESPACE = "http://jitsi.org/protocol/health";

    public static final String ELEMENT = "health-status";

    private static final String HEALTH_ATTRIBUTE = "status";

    public HealthStatusExtension() {
        super(ELEMENT, NAMESPACE);
    }

    static public void registerExtensionProvider() {
        ProviderManager.addExtensionProvider(
                ELEMENT,
                NAMESPACE,
                new DefaultExtensionElementProvider<>(HealthStatusExtension.class)
        );
    }

    public Health getStatus() {
        return Health.parse(getAttributeAsString(HEALTH_ATTRIBUTE));
    }

    public void setStatus(Health health) {
        setAttribute(HEALTH_ATTRIBUTE, String.valueOf(health));
    }

    public enum Health {
        HEALTHY("healthy"),
        UNHEALTHY("unhealthy"),
        UNDEFINED("undefined");

        private String health;

        Health(String health) {
            this.health = health;
        }

        @Override
        public String toString() {
            return health;
        }

        /**
         * Parses <code>Health</code> from given string.
         *
         * @param health the string representation of <code>Health</code>.
         *
         * @return <code>Health</code> value for given string or
         * {@link #UNDEFINED} if given string does not reflect any of valid values.
         */
        public static Health parse(String health) {
            if (StringUtils.isEmpty(health)) {
                return UNDEFINED;
            }
            try {
                return Health.valueOf(health.toUpperCase());
            } catch (IllegalArgumentException e) {
                return UNDEFINED;
            }
        }
    }
}
