/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package net.java.sip.communicator.impl.protocol.jabber;

import java.util.Hashtable;

import net.java.sip.communicator.service.credentialsstorage.CredentialsStorageService;
import net.java.sip.communicator.service.globaldisplaydetails.GlobalDisplayDetailsService;
import net.java.sip.communicator.service.googlecontacts.GoogleContactsService;
import net.java.sip.communicator.service.gui.UIService;
import net.java.sip.communicator.service.hid.HIDService;
import net.java.sip.communicator.service.netaddr.NetworkAddressManagerService;
import net.java.sip.communicator.service.protocol.PhoneNumberI18nService;
import net.java.sip.communicator.service.protocol.ProtocolNames;
import net.java.sip.communicator.service.protocol.ProtocolProviderFactory;
import net.java.sip.communicator.service.resources.ResourceManagementServiceUtils;
import net.java.sip.communicator.util.ServiceUtils;

import org.atalk.service.configuration.ConfigurationService;
import org.atalk.service.neomedia.MediaService;
import org.atalk.service.resources.ResourceManagementService;
import org.atalk.service.version.VersionService;
import org.osgi.framework.BundleActivator;
import org.osgi.framework.BundleContext;
import org.osgi.framework.ServiceReference;
import org.osgi.framework.ServiceRegistration;

/**
 * Loads the Jabber provider factory and registers it with service in the OSGI bundle context.
 *
 * @author Damian Minkov
 * @author Symphorien Wanko
 * @author Emil Ivov
 * @author Hristo Terezov
 * @author Eng Chong Meng
 */
public class JabberActivator implements BundleActivator {
    /**
     * Service reference for the currently valid Jabber provider factory.
     */
    private ServiceRegistration<?> jabberPpFactoryServReg = null;

    /**
     * Bundle context from OSGi.
     */
    static BundleContext bundleContext = null;

    /**
     * Configuration service.
     */
    private static ConfigurationService configurationService = null;

    /**
     * Media service.
     */
    private static MediaService mediaService = null;

    /**
     * A reference to the currently valid {@link NetworkAddressManagerService}.
     */
    private static NetworkAddressManagerService networkAddressManagerService = null;

    /**
     * A reference to the currently valid {@link CredentialsStorageService}.
     */
    private static CredentialsStorageService credentialsService = null;

    /**
     * The Jabber protocol provider factory.
     */
    private static ProtocolProviderFactoryJabberImpl jabberProviderFactory = null;

    /**
     * The <code>UriHandler</code> implementation that we use to handle "xmpp:" URIs
     */
    private UriHandlerJabberImpl uriHandlerImpl = null;

    /**
     * A reference to the currently valid <code>UIService</code>.
     */
    private static UIService uiService = null;

    /**
     * A reference to the currently valid <code>ResoucreManagementService</code> instance.
     */
    private static ResourceManagementService resourcesService = null;

    /**
     * A reference to the currently valid <code>HIDService</code> instance.
     */
    private static HIDService hidService = null;

    /**
     * A reference to the currently valid <code>GoogleContactsService</code> instance.
     */
    private static GoogleContactsService googleService = null;

    /**
     * A reference to the currently valid <code>VersionService</code> instance.
     */
    private static VersionService versionService = null;

    /**
     * The registered PhoneNumberI18nService.
     */
    private static PhoneNumberI18nService phoneNumberI18nService;

    /**
     * The global display details service instance.
     */
    private static GlobalDisplayDetailsService globalDisplayDetailsService = null;

    /**
     * Called when this bundle is started so the Framework can perform the bundle-specific
     * activities necessary to start this bundle.
     *
     * @param context The execution context of the bundle being started.
     *
     * @throws Exception If this method throws an exception, this bundle is marked as stopped and the
     * Framework will remove this bundle's listeners, unregister all services registered by
     * this bundle, and release all services used by this bundle.
     */
    public void start(BundleContext context)
            throws Exception {
        JabberActivator.bundleContext = context;

        Hashtable<String, String> hashtable = new Hashtable<>();
        hashtable.put(ProtocolProviderFactory.PROTOCOL, ProtocolNames.JABBER);
        jabberProviderFactory = new ProtocolProviderFactoryJabberImpl();

        /*
         * Install the UriHandler prior to registering the factory service in order to allow it to
         * detect when the stored accounts are loaded (because they may be asynchronously loaded).
         */
        uriHandlerImpl = new UriHandlerJabberImpl(jabberProviderFactory);

        // register the jabber account man.
        jabberPpFactoryServReg
                = context.registerService(ProtocolProviderFactory.class.getName(), jabberProviderFactory, hashtable);
    }

    /**
     * Returns a reference to a ConfigurationService implementation currently registered in the
     * bundle context or null if no such implementation was found.
     *
     * @return ConfigurationService a currently valid implementation of the configuration service.
     */
    public static ConfigurationService getConfigurationService() {
        if (configurationService == null) {
            configurationService = ServiceUtils.getService(bundleContext, ConfigurationService.class);
        }
        return configurationService;
    }

    /**
     * Returns a reference to the bundle context that we were started with.
     *
     * @return a reference to the BundleContext instance that we were started witn.
     */
    public static BundleContext getBundleContext() {
        return bundleContext;
    }

    /**
     * Returns a reference to the protocol provider factory that we have registered.
     *
     * @return a reference to the <code>ProtocolProviderFactoryJabberImpl</code> instance that we have
     * registered from this package.
     */
    public static ProtocolProviderFactoryJabberImpl getProtocolProviderFactory() {
        return jabberProviderFactory;
    }

    /**
     * Called when this bundle is stopped so the Framework can perform the bundle-specific
     * activities necessary to stop the bundle.
     *
     * @param context The execution context of the bundle being stopped.
     *
     * @throws Exception If this method throws an exception, the bundle is still marked as stopped, and the
     * Framework will remove the bundle's listeners, unregister all services registered by
     * the bundle, and release all services used by the bundle.
     */
    public void stop(BundleContext context)
            throws Exception {
        jabberProviderFactory.stop();
        jabberPpFactoryServReg.unregister();

        if (uriHandlerImpl != null) {
            uriHandlerImpl.dispose();
            uriHandlerImpl = null;
        }
        configurationService = null;
        mediaService = null;
        networkAddressManagerService = null;
        credentialsService = null;
    }

    /**
     * Returns a reference to the UIService implementation currently registered in the bundle
     * context or null if no such implementation was found.
     *
     * @return a reference to a UIService implementation currently registered in the bundle context
     * or null if no such implementation was found.
     */
    public static UIService getUIService() {
        if (uiService == null) {
            ServiceReference<?> uiServiceReference = bundleContext.getServiceReference(UIService.class.getName());
            uiService = (UIService) bundleContext.getService(uiServiceReference);
        }
        return uiService;
    }

    /**
     * Returns a reference to the ResourceManagementService implementation currently registered in
     * the bundle context or <code>null</code> if no such implementation was found.
     *
     * @return a reference to the ResourceManagementService implementation currently registered in
     * the bundle context or <code>null</code> if no such implementation was found.
     */
    public static ResourceManagementService getResources() {
        if (resourcesService == null)
            resourcesService = ResourceManagementServiceUtils.getService(bundleContext);
        return resourcesService;
    }

    /**
     * Returns a reference to a {@link MediaService} implementation currently registered in the
     * bundle context or null if no such implementation was found.
     *
     * @return a reference to a {@link MediaService} implementation currently registered in the
     * bundle context or null if no such implementation was found.
     */
    public static MediaService getMediaService() {
        if (mediaService == null) {
            ServiceReference<?> mediaServiceReference = bundleContext.getServiceReference(MediaService.class.getName());
            mediaService = (MediaService) bundleContext.getService(mediaServiceReference);
        }
        return mediaService;
    }

    /**
     * Returns a reference to a NetworkAddressManagerService implementation currently registered in
     * the bundle context or null if no such implementation was found.
     *
     * @return a currently valid implementation of the NetworkAddressManagerService .
     */
    public static NetworkAddressManagerService getNetworkAddressManagerService() {
        if (networkAddressManagerService == null) {
            ServiceReference<?> confReference
                    = bundleContext.getServiceReference(NetworkAddressManagerService.class.getName());
            networkAddressManagerService = (NetworkAddressManagerService) bundleContext.getService(confReference);
        }
        return networkAddressManagerService;
    }

    /**
     * Returns a reference to a CredentialsStorageService implementation currently registered in the
     * bundle context or null if no such implementation was found.
     *
     * @return a currently valid implementation of the CredentialsStorageService
     */
    public static CredentialsStorageService getCredentialsStorageService() {
        if (credentialsService == null) {
            ServiceReference<?> confReference = bundleContext.getServiceReference(CredentialsStorageService.class.getName());
            credentialsService = (CredentialsStorageService) bundleContext.getService(confReference);
        }
        return credentialsService;
    }

    /**
     * Returns a reference to <code>HIDService</code> implementation currently registered in the bundle
     * context or null if no such implementation was found
     *
     * @return a currently valid implementation of the <code>HIDService</code>
     */
    public static HIDService getHIDService() {
        if (hidService == null) {
            ServiceReference<?> hidReference = bundleContext.getServiceReference(HIDService.class.getName());
            if (hidReference == null)
                return null;
            hidService = (HIDService) bundleContext.getService(hidReference);
        }
        return hidService;
    }

    /**
     * Returns a reference to the GoogleContactsService implementation currently registered in the
     * bundle context or null if no such implementation was found.
     *
     * @return a reference to a GoogleContactsService implementation currently registered in the
     * bundle context or null if no such implementation was found.
     */
    public static GoogleContactsService getGoogleService() {
        if (googleService == null) {
            googleService = ServiceUtils.getService(bundleContext, GoogleContactsService.class);
        }
        return googleService;
    }

    /**
     * Returns a reference to a VersionService implementation currently registered in the bundle
     * context or null if no such implementation was found.
     *
     * @return a reference to a VersionService implementation currently registered in the bundle
     * context or null if no such implementation was found.
     */
    public static VersionService getVersionService() {
        if (versionService == null) {
            ServiceReference<?> versionServiceReference = bundleContext.getServiceReference(VersionService.class.getName());
            versionService = (VersionService) bundleContext.getService(versionServiceReference);
        }
        return versionService;
    }

    /**
     * Returns the PhoneNumberI18nService.
     *
     * @return returns the PhoneNumberI18nService.
     */
    public static PhoneNumberI18nService getPhoneNumberI18nService() {
        if (phoneNumberI18nService == null) {
            phoneNumberI18nService = ServiceUtils.getService(bundleContext, PhoneNumberI18nService.class);
        }
        return phoneNumberI18nService;
    }

    /**
     * Returns the <code>GlobalDisplayDetailsService</code> obtained from the bundle context.
     *
     * @return the <code>GlobalDisplayDetailsService</code> obtained from the bundle context
     */
    public static GlobalDisplayDetailsService getGlobalDisplayDetailsService() {
        if (globalDisplayDetailsService == null) {
            globalDisplayDetailsService = ServiceUtils.getService(bundleContext, GlobalDisplayDetailsService.class);
        }
        return globalDisplayDetailsService;
    }
}
