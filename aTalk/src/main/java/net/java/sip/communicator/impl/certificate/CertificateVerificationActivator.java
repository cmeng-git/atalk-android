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
package net.java.sip.communicator.impl.certificate;

import net.java.sip.communicator.service.certificate.CertificateService;
import net.java.sip.communicator.service.certificate.VerifyCertificateDialogService;
import net.java.sip.communicator.service.credentialsstorage.CredentialsStorageService;
import net.java.sip.communicator.service.gui.AuthenticationWindowService;
import net.java.sip.communicator.util.ServiceUtils;

import org.atalk.service.configuration.ConfigurationService;
import org.atalk.service.resources.ResourceManagementService;
import org.osgi.framework.BundleActivator;
import org.osgi.framework.BundleContext;

/**
 * The certificate verification bundle activator.
 *
 * @author Yana Stamcheva
 */
public class CertificateVerificationActivator implements BundleActivator {
    /**
     * The bundle context for this bundle.
     */
    protected static BundleContext bundleContext;

    /**
     * The configuration service.
     */
    private static ConfigurationService configService;

    /**
     * The service giving access to all resources.
     */
    private static ResourceManagementService resourcesService;

    /**
     * The service to store and access passwords.
     */
    private static CredentialsStorageService credService;

    /**
     * The service to create and show dialogs for user interaction.
     */
    private static VerifyCertificateDialogService certificateDialogService;

    /**
     * Called when this bundle is started.
     *
     * @param bc The execution context of the bundle being started.
     *
     * @throws Exception if the bundle is not correctly started
     */
    public void start(BundleContext bc)
            throws Exception {
        bundleContext = bc;
        bundleContext.registerService(CertificateService.class.getName(), new CertificateServiceImpl(), null);
    }

    /**
     * Called when this bundle is stopped so the Framework can perform the
     * bundle-specific activities necessary to stop the bundle.
     *
     * @param bc The execution context of the bundle being stopped.
     *
     * @throws Exception If this method throws an exception, the bundle is
     * still marked as stopped, and the Framework will remove the bundle's
     * listeners, unregister all services registered by the bundle, and
     * release all services used by the bundle.
     */
    public void stop(BundleContext bc)
            throws Exception {
        configService = null;
        resourcesService = null;
        credService = null;
        certificateDialogService = null;
    }

    /**
     * Returns the <code>ConfigurationService</code> obtained from the bundle context.
     *
     * @return the <code>ConfigurationService</code> obtained from the bundle context
     */
    public static ConfigurationService getConfigurationService() {
        if (configService == null) {
            configService = ServiceUtils.getService(bundleContext, ConfigurationService.class);
        }
        return configService;
    }

    /**
     * Returns the <code>ResourceManagementService</code>, through which we will access all resources.
     *
     * @return the <code>ResourceManagementService</code>, through which we will access all resources.
     */
    public static ResourceManagementService getResources() {
        if (resourcesService == null) {
            resourcesService = ServiceUtils.getService(bundleContext, ResourceManagementService.class);
        }
        return resourcesService;
    }

    /**
     * Returns the <code>CredentialsStorageService</code>, through which we will access all passwords.
     *
     * @return the <code>CredentialsStorageService</code>, through which we will access all passwords.
     */
    public static CredentialsStorageService getCredService() {
        if (credService == null) {
            credService = ServiceUtils.getService(bundleContext, CredentialsStorageService.class);
        }
        return credService;
    }

    /**
     * Returns the <code>VerifyCertificateDialogService</code>, through which we will use to create dialogs.
     *
     * @return the <code>VerifyCertificateDialogService</code>, through which we will use to create dialogs.
     */
    public static VerifyCertificateDialogService getCertificateDialogService() {
        if (certificateDialogService == null) {
            certificateDialogService = ServiceUtils.getService(bundleContext, VerifyCertificateDialogService.class);
        }
        return certificateDialogService;
    }

    /**
     * Returns service to show authentication window.
     *
     * @return return service to show authentication window.
     */
    public static AuthenticationWindowService getAuthenticationWindowService() {
        return ServiceUtils.getService(bundleContext, AuthenticationWindowService.class);
    }
}
