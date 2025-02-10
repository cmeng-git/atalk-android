/*
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
package org.atalk.service.libjitsi;

import org.osgi.framework.BundleActivator;
import org.osgi.framework.BundleContext;
import org.osgi.framework.ServiceRegistration;

/**
 * Activates libjitsi in an OSGi environment.
 */
public class LibJitsiActivator implements BundleActivator {
    private ServiceRegistration<LibJitsi> service;

    public void start(BundleContext bundleContext)
            throws Exception {
        LibJitsi impl = LibJitsi.start(bundleContext);
        service = bundleContext.registerService(LibJitsi.class, impl, null);
    }

    public void stop(BundleContext bundleContext)
            throws Exception {
        service.unregister();
        LibJitsi.stop();
    }
}
