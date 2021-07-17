/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with the License. You may
 * obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the specific language governing permissions
 * and limitations under the License.
 */
package net.java.sip.communicator.impl.callhistory;

import net.java.sip.communicator.service.callhistory.CallHistoryService;
import net.java.sip.communicator.service.contactsource.ContactSourceService;
import net.java.sip.communicator.service.history.HistoryService;
import net.java.sip.communicator.service.protocol.ProtocolProviderFactory;
import net.java.sip.communicator.service.protocol.ProtocolProviderService;
import net.java.sip.communicator.util.ServiceUtils;

import org.atalk.service.resources.ResourceManagementService;
import org.osgi.framework.*;

import java.util.Hashtable;
import java.util.Map;

import timber.log.Timber;

/**
 * Activates the <tt>CallHistoryService</tt>.
 *
 * @author Damian Minkov
 * @author Yana Stamcheva
 * @author Eng Chong Meng
 */
public class CallHistoryActivator implements BundleActivator
{
    /**
     * The bundle context.
     */
    public static BundleContext bundleContext;

    /**
     * The <tt>CallHistoryServiceImpl</tt> instantiated in the start method of this bundle.
     */
    private static CallHistoryServiceImpl callHistoryService = null;

    /**
     * The service responsible for resources.
     */
    private static ResourceManagementService resourcesService;

    /**
     * The map containing all registered
     */
    private static final Map<Object, ProtocolProviderFactory> providerFactoriesMap = new Hashtable<Object, ProtocolProviderFactory>();

    /**
     * Initialize and start call history
     *
     * @param bc the <tt>BundleContext</tt>
     * @throws Exception if initializing and starting call history fails
     */
    public void start(BundleContext bc)
            throws Exception
    {
        bundleContext = bc;

        HistoryService historyService = ServiceUtils.getService(bundleContext, HistoryService.class);

        // Create and start the call history service.
        callHistoryService = new CallHistoryServiceImpl();
        // set the configuration and history service
        callHistoryService.setHistoryService(historyService);

        callHistoryService.start(bundleContext);
        bundleContext.registerService(CallHistoryService.class.getName(), callHistoryService, null);
        bundleContext.registerService(ContactSourceService.class.getName(), new CallHistoryContactSource(), null);
        Timber.d("Call History Service ...[REGISTERED]");
    }

    /**
     * Stops this bundle.
     *
     * @param bundleContext the <tt>BundleContext</tt>
     * @throws Exception if the stop operation goes wrong
     */
    public void stop(BundleContext bundleContext)
            throws Exception
    {
        if (callHistoryService != null)
            callHistoryService.stop(bundleContext);
    }

    /**
     * Returns the instance of <tt>CallHistoryService</tt> created in this activator.
     *
     * @return the instance of <tt>CallHistoryService</tt> created in this activator
     */
    public static CallHistoryService getCallHistoryService()
    {
        return callHistoryService;
    }

    /**
     * Returns the <tt>ResourceManagementService</tt>, through which we will access all resources.
     *
     * @return the <tt>ResourceManagementService</tt>, through which we will access all resources.
     */
    public static ResourceManagementService getResources()
    {
        if (resourcesService == null) {
            resourcesService = ServiceUtils.getService(bundleContext, ResourceManagementService.class);
        }
        return resourcesService;
    }

    /**
     * Returns all <tt>ProtocolProviderFactory</tt>s obtained from the bundle context.
     *
     * @return all <tt>ProtocolProviderFactory</tt>s obtained from the bundle context
     */
    public static Map<Object, ProtocolProviderFactory> getProtocolProviderFactories()
    {
        ServiceReference[] serRefs = null;

        try {
            serRefs = bundleContext.getServiceReferences(ProtocolProviderFactory.class.getName(), null);
        } catch (InvalidSyntaxException e) {
            Timber.e(e, "Error while retrieving service refs");
            return null;
        }

        Map<Object, ProtocolProviderFactory> providerFactoriesMap = new Hashtable();
        if (serRefs.length != 0) {
            for (ServiceReference<ProtocolProviderService> serRef : serRefs) {
                ProtocolProviderFactory providerFactory = (ProtocolProviderFactory) bundleContext.getService(serRef);
                providerFactoriesMap.put(serRef.getProperty(ProtocolProviderFactory.PROTOCOL), providerFactory);
            }
        }
        return providerFactoriesMap;
    }
}
