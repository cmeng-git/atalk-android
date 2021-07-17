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
package net.java.sip.communicator.plugin.loggingutils;

import net.java.sip.communicator.service.notification.NotificationService;
import net.java.sip.communicator.util.ServiceUtils;

import org.atalk.service.configuration.ConfigurationService;
import org.atalk.service.fileaccess.FileAccessService;
import org.atalk.service.log.LogUploadService;
import org.atalk.service.resources.ResourceManagementService;
import org.osgi.framework.BundleActivator;
import org.osgi.framework.BundleContext;
import org.osgi.framework.ServiceReference;
import org.osgi.framework.ServiceRegistration;

/**
 * Creates and registers logging config form.
 *
 * @author Damian Minkov
 * @author Eng Chong Meng
 */
public class LoggingUtilsActivator implements BundleActivator
{
    /**
     * The OSGI bundle context.
     */
    private static BundleContext bundleContext = null;

    /**
     * The resource service.
     */
    private static ResourceManagementService resourceService;

    /**
     * The configuration service.
     */
    private static ConfigurationService configurationService = null;

    /**
     * The service giving access to files.
     */
    private static FileAccessService fileAccessService;

    /**
     * Notification service.
     */
    private static NotificationService notificationService;

    /**
     * The Log Upload service registration.
     */
    private ServiceRegistration logUploadServReg = null;

    /**
     * <tt>LogUploadService</tt> impl instance for android.
     */
    private LogUploadServiceImpl logUploadImpl;

    /**
     * Indicates if the logging configuration form should be disabled, i.e. not visible to the user.
     */
    private static final String DISABLED_PROP = "loggingconfig.DISABLED";

    /**
     * Creates and register logging configuration.
     *
     * @param bundleContext OSGI bundle context
     * @throws Exception if error creating configuration.
     */
    public void start(BundleContext bundleContext)
            throws Exception
    {
        LoggingUtilsActivator.bundleContext = bundleContext;
        getConfigurationService().setProperty(DISABLED_PROP, "true");

        logUploadImpl = new LogUploadServiceImpl();
        logUploadServReg = bundleContext.registerService(LogUploadService.class.getName(), logUploadImpl, null);
    }

    /**
     * Stops the Logging utils bundle
     *
     * @param bundleContext the OSGI bundle context
     */
    public void stop(BundleContext bundleContext)
            throws Exception
    {
        logUploadServReg.unregister();
        logUploadImpl.dispose();
    }

    /**
     * Returns the <tt>ResourceManagementService</tt> obtained from the bundle context.
     *
     * @return the <tt>ResourceManagementService</tt> obtained from the bundle context
     */
    public static ResourceManagementService getResourceService()
    {
        if (resourceService == null) {
            ServiceReference resourceReference
                    = bundleContext.getServiceReference(ResourceManagementService.class.getName());
            resourceService = (ResourceManagementService) bundleContext.getService(resourceReference);
        }
        return resourceService;
    }

    /**
     * Returns a reference to a ConfigurationService implementation currently
     * registered in the bundle context or null if no such implementation was found.
     *
     * @return a currently valid implementation of the ConfigurationService.
     */
    public static ConfigurationService getConfigurationService()
    {
        if (configurationService == null) {
            ServiceReference serviceReference = bundleContext.getServiceReference(ConfigurationService.class.getName());
            configurationService = (ConfigurationService) bundleContext.getService(serviceReference);
        }
        return configurationService;
    }

    /*
     * (cmeng: for android)
     * Returns a reference to a FileAccessService implementation currently registered in the bundle context
      * or null if no such implementation was found.
     *
     * @return a currently valid implementation of the FileAccessService .
     *
     * Returns the <tt>FileAccessService</tt> obtained from the bundle context.
     *
     * @return the <tt>FileAccessService</tt> obtained from the bundle context
     */
    public static FileAccessService getFileAccessService()
    {
        if (fileAccessService == null) {
            fileAccessService = ServiceUtils.getService(bundleContext, FileAccessService.class);
        }
        return fileAccessService;
    }

    /**
     * Returns the <tt>NotificationService</tt> obtained from the bundle context.
     *
     * @return the <tt>NotificationService</tt> obtained from the bundle context
     */
    public static NotificationService getNotificationService()
    {
        if (notificationService == null) {
            notificationService = ServiceUtils.getService(bundleContext, NotificationService.class);
        }
        return notificationService;
    }
}
