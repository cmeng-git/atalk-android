/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license.
 * See terms of license at gnu.org.
 */
package net.java.sip.communicator.plugin.otr;

import net.java.sip.communicator.service.contactlist.MetaContactListService;
import net.java.sip.communicator.service.gui.*;
import net.java.sip.communicator.service.msghistory.MessageHistoryService;
import net.java.sip.communicator.service.protocol.*;
import net.java.sip.communicator.service.resources.ResourceManagementServiceUtils;
import net.java.sip.communicator.util.AbstractServiceDependentActivator;
import net.java.sip.communicator.util.ServiceUtils;

import org.atalk.android.plugin.timberlog.TimberLog;
import org.atalk.service.configuration.ConfigurationService;
import org.atalk.service.resources.ResourceManagementService;
import org.atalk.util.OSUtils;
import org.osgi.framework.*;

import java.util.*;

import timber.log.Timber;

/**
 * @author George Politis
 * @author Pawel Domas
 * @author Eng Chong Meng
 */
public class OtrActivator extends AbstractServiceDependentActivator implements ServiceListener
{
    public static final String AUTO_INIT_OTR_PROP = "otr.AUTO_INIT_PRIVATE_MESSAGING";
    /**
     * A property used in configuration to disable the OTR plugin.
     */
    public static final String OTR_DISABLED_PROP = "otr.DISABLED";
    /**
     * A property specifying whether private messaging should be made mandatory.
     */
    public static final String OTR_MANDATORY_PROP = "otr.PRIVATE_MESSAGING_MANDATORY";

    /**
     * Indicates if the security/chat config form should be disabled, i.e.
     * not visible to the user.
     */
    private static final String OTR_CHAT_CONFIG_DISABLED_PROP = "otr.otrchatconfig.DISABLED";
    /**
     * The {@link BundleContext} of the {@link OtrActivator}.
     */
    public static BundleContext bundleContext;
    /**
     * The {@link ConfigurationService} of the {@link OtrActivator}. Can also be
     * obtained from the {@link OtrActivator#bundleContext} on demand, but we
     * add it here for convenience.
     */
    public static ConfigurationService configService;
    /**
     * The {@link ResourceManagementService} of the {@link OtrActivator}. Can
     * also be obtained from the {@link OtrActivator#bundleContext} on demand,
     * but we add it here for convenience.
     */
    public static ResourceManagementService resourceService;

    /**
     * The {@link ScOtrEngine} of the {@link OtrActivator}.
     */
    public static ScOtrEngineImpl scOtrEngine;

    /**
     * The {@link ScOtrKeyManager} of the {@link OtrActivator}.
     */
    public static ScOtrKeyManager scOtrKeyManager = new ScOtrKeyManagerImpl();

    /**
     * The {@link UIService} of the {@link OtrActivator}. Can also be obtained from the
     * {@link OtrActivator#bundleContext} on demand, but we add it here for convenience.
     */
    public static UIService uiService;

    /**
     * The <tt>MetaContactListService</tt> reference.
     */
    private static MetaContactListService metaCListService;

    /**
     * The message history service.
     */
    private static MessageHistoryService messageHistoryService;

    /**
     * The {@link OtrContactManager} of the {@link OtrActivator}.
     */
    private static OtrContactManager otrContactManager;
    private OtrTransformLayer otrTransformLayer;

    /**
     * Gets an {@link AccountID} by its UID.
     *
     * @param uid The {@link AccountID} UID.
     * @return The {@link AccountID} with the requested UID or null.
     */
    public static AccountID getAccountIDByUID(String uid)
    {
        if ((uid == null) || (uid.length() < 1))
            return null;

        Map<Object, ProtocolProviderFactory> providerFactoriesMap = getProtocolProviderFactories();

        if (providerFactoriesMap == null)
            return null;

        for (ProtocolProviderFactory providerFactory : providerFactoriesMap.values()) {
            for (AccountID accountID : providerFactory.getRegisteredAccounts()) {
                if (accountID.getAccountUniqueID().equals(uid))
                    return accountID;
            }
        }
        return null;
    }

    /**
     * Gets all the available accounts in SIP Communicator.
     *
     * @return a {@link List} of {@link AccountID}.
     */
    public static List<AccountID> getAllAccountIDs()
    {
        Map<Object, ProtocolProviderFactory> providerFactoriesMap = getProtocolProviderFactories();

        if (providerFactoriesMap == null)
            return null;

        List<AccountID> accountIDs = new Vector<>();

        for (ProtocolProviderFactory providerFactory : providerFactoriesMap.values()) {
            accountIDs.addAll(providerFactory.getRegisteredAccounts());
        }
        return accountIDs;
    }

    private static Map<Object, ProtocolProviderFactory> getProtocolProviderFactories()
    {
        ServiceReference[] serRefs;

        try {
            serRefs = bundleContext.getServiceReferences(ProtocolProviderFactory.class.getName(), null);
        } catch (InvalidSyntaxException ex) {
            Timber.e(ex, "Error while retrieving service refs");
            return null;
        }
        Map<Object, ProtocolProviderFactory> providerFactoriesMap = new Hashtable();

        if (serRefs != null) {
            for (ServiceReference serRef : serRefs) {
                ProtocolProviderFactory providerFactory = (ProtocolProviderFactory) bundleContext.getService(serRef);
                providerFactoriesMap.put(serRef.getProperty("PROTOCOL_NAME"), providerFactory);
            }
        }
        return providerFactoriesMap;
    }

    /**
     * Returns the <tt>MetaContactListService</tt> obtained from the bundle
     * context.
     *
     * @return the <tt>MetaContactListService</tt> obtained from the bundle
     * context
     */
    public static MetaContactListService getContactListService()
    {
        if (metaCListService == null) {
            metaCListService = ServiceUtils.getService(bundleContext, MetaContactListService.class);
        }
        return metaCListService;
    }

    /**
     * Gets the service giving access to message history.
     *
     * @return the service giving access to message history.
     */
    public static MessageHistoryService getMessageHistoryService()
    {
        if (messageHistoryService == null) {
            messageHistoryService = ServiceUtils.getService(bundleContext, MessageHistoryService.class);
        }
        return messageHistoryService;
    }

    /**
     * The dependent class. We are waiting for the ui service.
     *
     * @return the ui service class.
     */
    @Override
    public Class<?> getDependentServiceClass()
    {
        return UIService.class;
    }

    private void handleProviderAdded(ProtocolProviderService provider)
    {
        OperationSetInstantMessageTransform opSetMessageTransform
                = provider.getOperationSet(OperationSetInstantMessageTransform.class);

        if (opSetMessageTransform != null)
            opSetMessageTransform.addTransformLayer(this.otrTransformLayer);
        else
            Timber.log(TimberLog.FINER, "Service did not have a transform op. set.");
    }

    private void handleProviderRemoved(ProtocolProviderService provider)
    {
        // check whether the provider has a basic im operation set
        OperationSetInstantMessageTransform opSetMessageTransform
                = provider.getOperationSet(OperationSetInstantMessageTransform.class);

        if (opSetMessageTransform != null)
            opSetMessageTransform.removeTransformLayer(this.otrTransformLayer);
    }

    /*
     * Implements ServiceListener#serviceChanged(ServiceEvent).
     */
    @Override
    public void serviceChanged(ServiceEvent serviceEvent)
    {
        Object sService = bundleContext.getService(serviceEvent.getServiceReference());

        if (TimberLog.isTraceEnabled()) {
            Timber.log(TimberLog.FINER, "Received a service event for: %s", sService.getClass().getName());
        }

        // we don't care if the source service is not a protocol provider
        if (!(sService instanceof ProtocolProviderService))
            return;

        Timber.d("Service is a protocol provider.");
        if (serviceEvent.getType() == ServiceEvent.REGISTERED) {
            Timber.d("Handling registration of a new Protocol Provider.");
            this.handleProviderAdded((ProtocolProviderService) sService);
        }
        else if (serviceEvent.getType() == ServiceEvent.UNREGISTERING) {
            this.handleProviderRemoved((ProtocolProviderService) sService);
        }
    }

    /**
     * The bundle context to use.
     *
     * @param context the context to set.
     */
    @Override
    public void setBundleContext(BundleContext context)
    {
        bundleContext = context;
    }

    /*
     * Implements AbstractServiceDependentActivator#start(UIService).
     */
    @Override
    public void start(Object dependentService)
    {
        configService = ServiceUtils.getService(bundleContext, ConfigurationService.class);
        // Check whether someone has disabled this plug-in.
        if (configService.getBoolean(OTR_DISABLED_PROP, false)) {
            configService = null;
            return;
        }

        resourceService = ResourceManagementServiceUtils.getService(bundleContext);
        if (resourceService == null) {
            configService = null;
            return;
        }

        uiService = (UIService) dependentService;

        // Init static variables, don't proceed without them.
        scOtrEngine = new ScOtrEngineImpl();
        otrContactManager = new OtrContactManager();
        otrTransformLayer = new OtrTransformLayer();

        // Register Transformation Layer
        bundleContext.addServiceListener(this);
        bundleContext.addServiceListener(scOtrEngine);
        bundleContext.addServiceListener(otrContactManager);

        ServiceReference<ProtocolProviderService>[] protocolProviderRefs
                = ServiceUtils.getServiceReferences(bundleContext, ProtocolProviderService.class);

        if ((protocolProviderRefs != null) && (protocolProviderRefs.length > 0)) {
            Timber.d("Found %d already installed providers.", protocolProviderRefs.length);
            for (ServiceReference<ProtocolProviderService> protocolProviderRef : protocolProviderRefs) {
                ProtocolProviderService provider = bundleContext.getService(protocolProviderRef);
                handleProviderAdded(provider);
            }
        }

        // If the general configuration form is disabled don't register it.
        if (!configService.getBoolean(OTR_CHAT_CONFIG_DISABLED_PROP, false)
                && !OSUtils.IS_ANDROID) {
            Dictionary<String, String> properties = new Hashtable<>();

            properties.put(ConfigurationForm.FORM_TYPE, ConfigurationForm.SECURITY_TYPE);
            // Register the configuration form.
            bundleContext.registerService(ConfigurationForm.class.getName(),
                    new LazyConfigurationForm("otr.authdialog.OtrConfigurationPanel",
                            getClass().getClassLoader(), "plugin.otr.configform.ICON",
                            "service.gui.CHAT", 1), properties);
        }
    }

    /*
     * Implements BundleActivator#stop(BundleContext).
     */
    @Override
    public void stop(BundleContext bc)
            throws Exception
    {
        // Unregister transformation layer.
        // start listening for newly register or removed protocol providers
        bundleContext.removeServiceListener(this);
        if (scOtrEngine != null)
            bundleContext.removeServiceListener(scOtrEngine);
        if (otrContactManager != null)
            bundleContext.removeServiceListener(otrContactManager);

        ServiceReference[] protocolProviderRefs;
        try {
            protocolProviderRefs = bundleContext.getServiceReferences(ProtocolProviderService.class.getName(), null);
        } catch (InvalidSyntaxException ex) {
            Timber.e(ex, "Error while retrieving service refs");
            return;
        }

        if ((protocolProviderRefs != null) && (protocolProviderRefs.length > 0)) {
            // in case we found any
            for (ServiceReference protocolProviderRef : protocolProviderRefs) {
                ProtocolProviderService provider = (ProtocolProviderService) bundleContext.getService(protocolProviderRef);
                handleProviderRemoved(provider);
            }
        }
    }
}
