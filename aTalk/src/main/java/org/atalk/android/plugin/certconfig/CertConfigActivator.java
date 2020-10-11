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
package org.atalk.android.plugin.certconfig;

import net.java.sip.communicator.service.certificate.CertificateService;
import net.java.sip.communicator.service.credentialsstorage.CredentialsStorageService;
import net.java.sip.communicator.service.gui.UIService;
import net.java.sip.communicator.util.ServiceUtils;

import org.atalk.service.configuration.ConfigurationService;
import org.osgi.framework.BundleActivator;
import org.osgi.framework.BundleContext;

/**
 * OSGi Activator for the Certificate Configuration Advanced Form.
 *
 * @author Ingo Bauersachs
 * @author Eng Chong Meng
 */
public class CertConfigActivator implements BundleActivator
{
    private static BundleContext bundleContext;

    public void start(BundleContext bc)
            throws Exception
    {
        bundleContext = bc;
    }

    public void stop(BundleContext arg0)
            throws Exception
    {
    }

    static BundleContext getBundleContext()
    {
        return bundleContext;
    }

    /**
     * Returns a reference to a ConfigurationService implementation currently
     * registered in the bundle context or null if no such implementation was found.
     *
     * @return a currently valid implementation of the ConfigurationService.
     */
    public static ConfigurationService getConfigService()
    {
        return ServiceUtils.getService(bundleContext, ConfigurationService.class);
    }

    /**
     * Returns a reference to a CertificateService implementation currently
     * registered in the bundle context or null if no such implementation was found.
     *
     * @return a currently valid implementation of the CertificateService.
     */
    public static CertificateService getCertService()
    {
        return ServiceUtils.getService(bundleContext, CertificateService.class);
    }

    /**
     * Returns a reference to a UIService implementation currently
     * registered in the bundle context or null if no such implementation was found.
     *
     * @return a currently valid implementation of the UIService.
     */
    public static UIService getUIService()
    {
        return ServiceUtils.getService(bundleContext, UIService.class);
    }

    /**
     * Returns a reference to a CredentialsStorageService implementation currently
     * registered in the bundle context or null if no such implementation was found.
     *
     * @return a currently valid implementation of the CredentialsStorageService.
     */
    public static CredentialsStorageService getCredService()
    {
        return ServiceUtils.getService(bundleContext, CredentialsStorageService.class);
    }
}
