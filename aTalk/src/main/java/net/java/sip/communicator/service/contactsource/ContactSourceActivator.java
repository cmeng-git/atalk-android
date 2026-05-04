/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
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
package net.java.sip.communicator.service.contactsource;

import org.osgi.framework.BundleActivator;
import org.osgi.framework.BundleContext;

/**
 * @author Damian Minkov
 */
public class ContactSourceActivator implements BundleActivator {
    /**
     * OSGi bundle context.
     */
    public static BundleContext bundleContext;

    @Override
    public void start(BundleContext bundleContext)
            throws Exception {
        ContactSourceActivator.bundleContext = bundleContext;
    }

    @Override
    public void stop(BundleContext bundleContext)
            throws Exception {
    }
}
