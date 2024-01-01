/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package net.java.sip.communicator.util;

import net.java.sip.communicator.service.gui.AlertUIService;
import net.java.sip.communicator.service.gui.UIService;
import net.java.sip.communicator.service.protocol.AccountManager;
import net.java.sip.communicator.service.protocol.ProtocolProviderFactory;
import net.java.sip.communicator.service.resources.ResourceManagementServiceUtils;

import org.atalk.impl.timberlog.TimberLog;
import org.atalk.service.configuration.ConfigurationService;
import org.atalk.service.fileaccess.FileAccessService;
import org.atalk.service.neomedia.MediaConfigurationService;
import org.atalk.service.neomedia.MediaService;
import org.atalk.service.resources.ResourceManagementService;
import org.atalk.util.OSUtils;
import org.osgi.framework.BundleActivator;
import org.osgi.framework.BundleContext;
import org.osgi.framework.InvalidSyntaxException;
import org.osgi.framework.ServiceReference;

import java.util.Collection;
import java.util.Hashtable;
import java.util.Map;

import timber.log.Timber;

/**
 * The only reason d'etre for this Activator is so that it would set a global exception handler.
 * It doesn't export any services and neither it runs any initialization - all it does is call
 * <code>Thread.setUncaughtExceptionHandler()</code>
 *
 * @author Emil Ivov
 * @author Eng Chong Meng
 */
public class UtilActivator implements BundleActivator, Thread.UncaughtExceptionHandler
{
    private static ConfigurationService configurationService;
    private static ResourceManagementService resourceService;
    private static UIService uiService;
    private static FileAccessService fileAccessService;
    private static MediaService mediaService;
    public static BundleContext bundleContext;
    private static AccountManager accountManager;
    private static AlertUIService alertUIService;

    /**
     * Calls <code>Thread.setUncaughtExceptionHandler()</code>
     *
     * @param context The execution context of the bundle being started (unused).
     * @throws Exception If this method throws an exception, this bundle is marked as stopped and the Framework
     * will remove this bundle's listeners, unregister all services registered by this
     * bundle, and release all services used by this bundle.
     */
    public void start(BundleContext context)
            throws Exception
    {
        bundleContext = context;
        if (OSUtils.IS_ANDROID)
            loadLoggingConfig();

        Timber.log(TimberLog.FINER, "Setting default uncaught exception handler.");
        Thread.setDefaultUncaughtExceptionHandler(this);
    }

    /**
     * Loads logging config if any. Need to be loaded in order to activate logging and need to be
     * activated after bundle context is initialized.
     */
    private void loadLoggingConfig()
    {
        try {
            Class.forName("net.java.sip.communicator.util.JavaUtilLoggingConfig").newInstance();
        } catch (Throwable t) {
        }
    }

    /**
     * Method invoked when a thread would terminate due to the given uncaught exception. All we do
     * here is simply log the exception using the system logger.
     * <p/>
     * <p/>
     * Any exception thrown by this method will be ignored by the Java Virtual Machine and thus
     * won't screw our application.
     *
     * @param thread the thread
     * @param exc the exception
     */
    public void uncaughtException(Thread thread, Throwable exc)
    {
        Timber.e(exc, "An uncaught exception occurred in thread = %s and message was: %s", thread, exc.getMessage());
    }

    /**
     * Doesn't do anything.
     *
     * @param context The execution context of the bundle being stopped.
     * @throws Exception If this method throws an exception, the bundle is still marked as stopped, and the
     * Framework will remove the bundle's listeners, unregister all services registered by
     * the bundle, and release all services used by the bundle.
     */
    public void stop(BundleContext context)
            throws Exception
    {
    }

    /**
     * Returns the <code>ConfigurationService</code> currently registered.
     *
     * @return the <code>ConfigurationService</code>
     */
    public static ConfigurationService getConfigurationService()
    {
        if (configurationService == null) {
            configurationService = ServiceUtils.getService(bundleContext, ConfigurationService.class);
        }
        return configurationService;
    }

    /**
     * Returns the service giving access to all application resources.
     *
     * @return the service giving access to all application resources.
     */
    public static ResourceManagementService getResources()
    {
        if (resourceService == null) {
            resourceService = ResourceManagementServiceUtils.getService(bundleContext);
        }
        return resourceService;
    }

    /**
     * Gets the <code>UIService</code> instance registered in the <code>BundleContext</code> of the
     * <code>UtilActivator</code>.
     *
     * @return the <code>UIService</code> instance registered in the <code>BundleContext</code> of the
     * <code>UtilActivator</code>
     */
    public static UIService getUIService()
    {
        if (uiService == null)
            uiService = ServiceUtils.getService(bundleContext, UIService.class);
        return uiService;
    }

    /**
     * Returns the <code>FileAccessService</code> obtained from the bundle context.
     *
     * @return the <code>FileAccessService</code> obtained from the bundle context
     */
    public static FileAccessService getFileAccessService()
    {
        if (fileAccessService == null) {
            fileAccessService = ServiceUtils.getService(bundleContext, FileAccessService.class);
        }
        return fileAccessService;
    }

    /**
     * Returns an instance of the <code>MediaService</code> obtained from the bundle context.
     *
     * @return an instance of the <code>MediaService</code> obtained from the bundle context
     */
    public static MediaService getMediaService()
    {
        if (mediaService == null) {
            mediaService = ServiceUtils.getService(bundleContext, MediaService.class);
        }
        return mediaService;
    }

    /**
     * Returns the {@link MediaConfigurationService} instance registered in the <code>BundleContext</code> of the
     * <code>UtilActivator</code>.
     *
     * @return the <code>UIService</code> instance registered in the <code>BundleContext</code> of the
     * <code>UtilActivator</code>
     */
    public static MediaConfigurationService getMediaConfiguration()
    {
        return ServiceUtils.getService(bundleContext, MediaConfigurationService.class);
    }

    /**
     * Returns all <code>ProtocolProviderFactory</code>s obtained from the bundle context.
     *
     * @return all <code>ProtocolProviderFactory</code>s obtained from the bundle context
     */
    public static Map<Object, ProtocolProviderFactory> getProtocolProviderFactories()
    {
        Collection<ServiceReference<ProtocolProviderFactory>> serRefs = null;
        Map<Object, ProtocolProviderFactory> providerFactoriesMap = new Hashtable<>();

        // get all registered provider factories
        try {
            if (bundleContext != null)
                serRefs = bundleContext.getServiceReferences(ProtocolProviderFactory.class, null);
        } catch (InvalidSyntaxException ex) {
            serRefs = null;
            Timber.e("LoginManager : %s", ex.getMessage());
        }

        if ((serRefs != null) && !serRefs.isEmpty()) {
            for (ServiceReference<ProtocolProviderFactory> serRef : serRefs) {
                ProtocolProviderFactory providerFactory = bundleContext.getService(serRef);
                providerFactoriesMap.put(serRef.getProperty(ProtocolProviderFactory.PROTOCOL), providerFactory);
            }
        }
        return providerFactoriesMap;
    }

    /**
     * Returns the <code>AccountManager</code> obtained from the bundle context.
     *
     * @return the <code>AccountManager</code> obtained from the bundle context
     */
    public static AccountManager getAccountManager()
    {
        if (accountManager == null) {
            accountManager = ServiceUtils.getService(bundleContext, AccountManager.class);
        }
        return accountManager;
    }

    /**
     * Returns the <code>AlertUIService</code> obtained from the bundle context.
     *
     * @return the <code>AlertUIService</code> obtained from the bundle context
     */
    public static AlertUIService getAlertUIService()
    {
        if (alertUIService == null) {
            alertUIService = ServiceUtils.getService(bundleContext, AlertUIService.class);
        }
        return alertUIService;
    }
}
