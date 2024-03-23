/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package net.java.sip.communicator.impl.globaldisplaydetails;

import net.java.sip.communicator.service.globaldisplaydetails.GlobalDisplayDetailsService;
import net.java.sip.communicator.service.gui.AlertUIService;
import net.java.sip.communicator.service.gui.UIService;
import net.java.sip.communicator.service.protocol.ProtocolProviderService;
import net.java.sip.communicator.service.protocol.globalstatus.GlobalStatusService;
import net.java.sip.communicator.util.ServiceUtils;
import net.java.sip.communicator.util.UtilActivator;

import org.atalk.service.configuration.ConfigurationService;
import org.atalk.service.resources.ResourceManagementService;
import org.osgi.framework.Bundle;
import org.osgi.framework.BundleActivator;
import org.osgi.framework.BundleContext;
import org.osgi.framework.InvalidSyntaxException;
import org.osgi.framework.ServiceEvent;
import org.osgi.framework.ServiceListener;
import org.osgi.framework.ServiceReference;

/**
 * @author Yana Stamcheva
 */
public class GlobalDisplayDetailsActivator implements BundleActivator, ServiceListener {
    /**
     * The bundle context.
     */
    private static BundleContext bundleContext;

    /**
     * The service giving access to image and string application resources.
     */
    private static ResourceManagementService resourcesService;

    /**
     * The service giving access to the configuration resources.
     */
    private static ConfigurationService configService;

    /**
     * The alert UI service.
     */
    private static AlertUIService alertUIService;

    /**
     * The UI service.
     */
    private static UIService uiService;

    /**
     * The display details implementation.
     */
    static GlobalDisplayDetailsImpl displayDetailsImpl;

    static GlobalStatusServiceImpl globalStatusService;

    /**
     * Initialize and start file service
     *
     * @param bc the <code>BundleContext</code>
     *
     * @throws Exception if initializing and starting file service fails
     */
    public void start(BundleContext bc)
            throws Exception {
        bundleContext = bc;

        displayDetailsImpl = new GlobalDisplayDetailsImpl();
        globalStatusService = new GlobalStatusServiceImpl();
        bundleContext.addServiceListener(this);
        handleAlreadyRegisteredProviders();

        bundleContext.registerService(GlobalDisplayDetailsService.class.getName(), displayDetailsImpl, null);
        bundleContext.registerService(GlobalStatusService.class.getName(), globalStatusService, null);
    }

    /**
     * Searches and processes already registered providers.
     */
    private void handleAlreadyRegisteredProviders() {
        bundleContext.addServiceListener((org.osgi.framework.ServiceListener) this);

        ServiceReference[] ppsRefs = null;
        try {
            ppsRefs = bundleContext.getServiceReferences(ProtocolProviderService.class.getName(), null);
        } catch (InvalidSyntaxException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }

        if (ppsRefs.length != 0) {
            for (ServiceReference<ProtocolProviderService> ppsRef : ppsRefs) {
                ProtocolProviderService pps = bundleContext.getService(ppsRef);
                handleProviderAdded(pps);
            }
        }
    }

    /**
     * Used to attach the listeners to existing or just registered protocol provider.
     *
     * @param pps ProtocolProviderService
     */
    private void handleProviderAdded(ProtocolProviderService pps) {
        pps.addRegistrationStateChangeListener(displayDetailsImpl);
        globalStatusService.handleProviderAdded(pps);
    }

    /**
     * Stops this bundle.
     *
     * @param bundleContext the <code>BundleContext</code>
     *
     * @throws Exception if the stop operation goes wrong
     */
    public void stop(BundleContext bundleContext)
            throws Exception {
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
     * Returns the <code>AlertUIService</code> obtained from the bundle context.
     *
     * @return the <code>AlertUIService</code> obtained from the bundle context
     */
    public static AlertUIService getAlertUIService() {
        if (alertUIService == null) {
            alertUIService = ServiceUtils.getService(bundleContext, AlertUIService.class);
        }
        return alertUIService;
    }

    /**
     * Returns the <code>UIService</code> obtained from the bundle context.
     *
     * @return the <code>UIService</code> obtained from the bundle context
     */
    public static UIService getUIService() {
        if (uiService == null) {
            uiService = ServiceUtils.getService(bundleContext, UIService.class);
        }
        return uiService;
    }

    /**
     * Implements the <code>ServiceListener</code> method. Verifies whether the passed event concerns a <code>ProtocolProviderService</code> and
     * adds or removes a registration listener.
     *
     * @param event The <code>ServiceEvent</code> object.
     */
    public void serviceChanged(ServiceEvent event) {
        ServiceReference<?> serviceRef = event.getServiceReference();

        // if the event is caused by a bundle being stopped, we don't want to know
        if (serviceRef.getBundle().getState() == Bundle.STOPPING) {
            return;
        }

        Object service = UtilActivator.bundleContext.getService(serviceRef);

        // we don't care if the source service is not a protocol provider
        if (!(service instanceof ProtocolProviderService)) {
            return;
        }

        ProtocolProviderService pps = (ProtocolProviderService) service;

        switch (event.getType()) {
            case ServiceEvent.REGISTERED:
                this.handleProviderAdded(pps);
                break;
            case ServiceEvent.UNREGISTERING:
                pps.removeRegistrationStateChangeListener(displayDetailsImpl);
                globalStatusService.handleProviderRemoved(pps);
                break;
        }
    }
}
