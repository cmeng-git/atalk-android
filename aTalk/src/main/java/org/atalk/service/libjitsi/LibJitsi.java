/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.atalk.service.libjitsi;

import org.atalk.impl.libjitsi.LibJitsiImpl;
import org.atalk.impl.libjitsi.LibJitsiOSGiImpl;
import org.atalk.service.audionotifier.AudioNotifierService;
import org.atalk.service.configuration.ConfigurationService;
import org.atalk.service.fileaccess.FileAccessService;
import org.atalk.service.neomedia.MediaService;
import org.atalk.service.resources.ResourceManagementService;
import org.osgi.framework.BundleContext;

import timber.log.Timber;

/**
 * Represents the entry point of the <code>libjitsi</code> library.
 * <p>
 * The {@link #start()} method is to be called to initialize/start the use of
 * the library. Respectively, the {@link #stop()} method is to be called to
 * uninitialize/stop the use of the library (i.e. to release the resources
 * acquired by the library during its execution). The <code>getXXXService()</code>
 * methods may be called only after the <code>start()</code> method returns
 * successfully and before the <code>stop()</code> method is called.
 * </p>
 * <p>
 * The <code>libjitsi</code> library may be utilized both with and without OSGi. If
 * the library detects during the execution of the <code>start()</code> method that
 * (a) the <code>LibJitsi</code> class has been loaded as part of an OSGi
 * <code>Bundle</code> and (b) successfully retrieves the associated
 * <code>BundleContext</code>, it will look for the references to the
 * implementations of the supported service classes in the retrieved
 * <code>BundleContext</code>. Otherwise, the library will stand alone without
 * relying on OSGi functionality. In the case of successful detection of OSGi,
 * the library will not register the supported service class instances in the
 * retrieved <code>BundleContext</code>.
 * </p>
 *
 * @author Lyubomir Marinov
 * @author Eng Chong Meng
 */
public abstract class LibJitsi
{
    /**
     * The <code>LibJitsi</code> instance which is provides the implementation of the <code>getXXXService</code> methods.
     */
    private static LibJitsi impl;

    /**
     * Gets the <code>AudioNotifierService</code> instance. If no existing
     * <code>AudioNotifierService</code> instance is known to the library, tries to
     * initialize a new one. (Such a try to initialize a new instance is
     * performed just once while the library is initialized.)
     *
     * @return the <code>AudioNotifierService</code> instance known to the library
     * or <code>null</code> if no <code>AudioNotifierService</code> instance is known to the library
     */
    public static AudioNotifierService getAudioNotifierService()
    {
        return invokeGetServiceOnImpl(AudioNotifierService.class);
    }

    /**
     * Gets the <code>ConfigurationService</code> instance. If no existing
     * <code>ConfigurationService</code> instance is known to the library, tries to
     * initialize a new one. (Such a try to initialize a new instance is
     * performed just once while the library is initialized.)
     *
     * @return the <code>ConfigurationService</code> instance known to the library
     * or <code>null</code> if no <code>ConfigurationService</code> instance is known to the library
     */
    public static ConfigurationService getConfigurationService()
    {
        return invokeGetServiceOnImpl(ConfigurationService.class);
    }

    /**
     * Gets the <code>FileAccessService</code> instance. If no existing
     * <code>FileAccessService</code> instance is known to the library, tries to
     * initialize a new one. (Such a try to initialize a new instance is
     * performed just once while the library is initialized.)
     *
     * @return the <code>FileAccessService</code> instance known to the library or
     * <code>null</code> if no <code>FileAccessService</code> instance is known to the library
     */
    public static FileAccessService getFileAccessService()
    {
        return invokeGetServiceOnImpl(FileAccessService.class);
    }

    /**
     * Gets the <code>MediaService</code> instance. If no existing
     * <code>MediaService</code> instance is known to the library, tries to
     * initialize a new one. (Such a try to initialize a new instance is
     * performed just once while the library is initialized.)
     *
     * @return the <code>MediaService</code> instance known to the library or
     * <code>null</code> if no <code>MediaService</code> instance is known to the library
     */
    public static MediaService getMediaService()
    {
        return invokeGetServiceOnImpl(MediaService.class);
    }

    /**
     * Gets the <code>ResourceManagementService</code> instance. If no existing
     * <code>ResourceManagementService</code> instance is known to the library,
     * tries to initialize a new one. (Such a try to initialize a new instance
     * is performed just once while the library is initialized.)
     *
     * @return the <code>ResourceManagementService</code> instance known to the
     * library or <code>null</code> if no <code>ResourceManagementService</code>
     * instance is known to the library.
     */
    public static ResourceManagementService getResourceManagementService()
    {
        return invokeGetServiceOnImpl(ResourceManagementService.class);
    }

    /**
     * Invokes {@link #getService(Class)} on {@link #impl}.
     *
     * @param serviceClass the class of the service to be retrieved.
     * @return a service of the specified type if such a service is associated with the library.
     * @throws IllegalStateException if the library is not currently initialized.
     */
    private static <T> T invokeGetServiceOnImpl(Class<T> serviceClass)
    {
        LibJitsi impl = LibJitsi.impl;
        if (impl == null)
            throw new IllegalStateException("impl");
        else
            return impl.getService(serviceClass);
    }

    /**
     * Starts/initializes the use of the <code>libjitsi</code> library.
     */
    public static void start()
    {
        start(null);
    }

    /**
     * Starts/initializes the use of the <code>libjitsi</code> library.
     *
     * @param context an OSGi {@link BundleContext}.
     */
    static LibJitsi start(BundleContext context)
    {
        if (null != LibJitsi.impl) {
            Timber.d("LibJitsi already started, using as implementation: %s",
                    impl.getClass().getCanonicalName());
            return impl;
        }

        /*
         * LibJitsi implements multiple backends and tries to choose the most
         * appropriate at run time. For example, an OSGi-aware backend is used
         * if it is detected that an OSGi implementation is available.
         */
        if (context == null) {
            impl = new LibJitsiImpl();
        }
        else {
            impl = new LibJitsiOSGiImpl(context);
        }
        Timber.d("Successfully started LibJitsi using implementation: %s", impl.getClass().getCanonicalName());
        return impl;
    }

    /**
     * Stops/uninitializes the use of the <code>libjitsi</code> library.
     */
    public static void stop()
    {
        impl = null;
    }

    /**
     * Initializes a new <code>LibJitsi</code> instance.
     */
    protected LibJitsi()
    {
    }

    /**
     * Gets a service of a specific type associated with this implementation of the <code>libjitsi</code> library.
     *
     * @param serviceClass the type of the service to be retrieved
     * @return a service of the specified type if there is such an association
     * known to this implementation of the <code>libjitsi</code> library; otherwise, <code>null</code>
     */
    protected abstract <T> T getService(Class<T> serviceClass);
}
