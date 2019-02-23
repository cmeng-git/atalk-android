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
package net.java.sip.communicator.impl.credentialsstorage;

import net.java.sip.communicator.service.credentialsstorage.CredentialsStorageService;
import net.java.sip.communicator.service.credentialsstorage.MasterPasswordInputService;
import net.java.sip.communicator.util.ServiceUtils;

import org.osgi.framework.BundleActivator;
import org.osgi.framework.BundleContext;

import timber.log.Timber;

/**
 * Activator for the {@link CredentialsStorageService}.
 *
 * @author Dmitri Melnikov
 * @author Eng Chong Meng
 */
public class CredentialsStorageActivator implements BundleActivator
{
    /**
     * The {@link BundleContext}.
     */
    private static BundleContext bundleContext;

    /**
     * Returns service to show master password input dialog.
     *
     * @return return master password service to display input dialog.
     */
    public static MasterPasswordInputService getMasterPasswordInputService()
    {
        return ServiceUtils.getService(bundleContext, MasterPasswordInputService.class);
    }

    /**
     * The {@link CredentialsStorageService} implementation.
     */
    private CredentialsStorageServiceImpl impl;

    /**
     * Starts the credentials storage service
     *
     * @param bundleContext the <tt>BundleContext</tt> as provided from the OSGi framework
     * @throws Exception if anything goes wrong
     */
    public void start(BundleContext bundleContext)
            throws Exception
    {
        CredentialsStorageActivator.bundleContext = bundleContext;
        impl = new CredentialsStorageServiceImpl();
        impl.start(bundleContext);

        bundleContext.registerService(
                CredentialsStorageService.class.getName(), impl, null);

        Timber.i("Service Impl: %s [REGISTERED]", getClass().getName());
    }

    /**
     * Unregisters the credentials storage service.
     *
     * @param bundleContext BundleContext
     * @throws Exception if anything goes wrong
     */
    public void stop(BundleContext bundleContext)
            throws Exception
    {
        impl.stop();
        Timber.i("The CredentialsStorageService stop method has been called.");
    }
}
