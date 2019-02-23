/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.atalk.impl.libjitsi;

import org.atalk.android.R;
import org.atalk.android.aTalkApp;
import org.atalk.android.gui.aTalk;
import org.atalk.android.gui.dialogs.DialogActivity;
import org.atalk.service.libjitsi.LibJitsi;

import java.util.HashMap;
import java.util.Map;

import timber.log.Timber;

/**
 * Represents an implementation of the <tt>libjitsi</tt> library which is stand-alone and does not utilize OSGi.
 *
 * @author Lyubomir Marinov
 * @author Eng Chong Meng
 */
public class LibJitsiImpl extends LibJitsi
{
    /**
     * The service instances associated with this implementation of the <tt>libjitsi</tt> library mapped by their
     * respective type/class names.
     */
    private final Map<String, Object> services = new HashMap<>();

    /**
     * Initializes a new <tt>LibJitsiImpl</tt> instance.
     */
    public LibJitsiImpl()
    {
        /*
         * The AudioNotifierService implementation uses a non-standard package location so work around it.
         */
        String key = "org.atalk.service.audionotifier.AudioNotifierService";
        String value = System.getProperty(key);

        if ((value == null) || (value.length() == 0)) {
            System.setProperty(key, "org.atalk.impl.neomedia.notify.AudioNotifierServiceImpl");
        }
    }

    /**
     * Gets a service of a specific type associated with this implementation of the <tt>libjitsi</tt> library.
     *
     * @param serviceClass the type of the service to be retrieved
     * @return a service of the specified type if there is such an association known to this implementation of the
     * <tt>libjitsi</tt> library; otherwise, <tt>null</tt>
     */
    @Override
    protected <T> T getService(Class<T> serviceClass)
    {
        String serviceClassName = serviceClass.getName();

        synchronized (services) {
            if (services.containsKey(serviceClassName)) {
                @SuppressWarnings("unchecked")
                T service = (T) services.get(serviceClassName);
                return service;
            }
            else {
                /*
                 * Do not allow concurrent and/or repeating requests to create an instance of the specified serviceClass.
                 */
                services.put(serviceClassName, null);
            }
        }

        /*
         * Allow the service implementation class names to be specified as System properties akin to standard
         * Java class factory names.
         */
        String serviceImplClassName = System.getProperty(serviceClassName);
        boolean suppressClassNotFoundException = false;

        if ((serviceImplClassName == null) || (serviceImplClassName.length() == 0)) {
            serviceImplClassName = serviceClassName.replace(".service.", ".impl.").concat("Impl");
            /*
             * Nobody has explicitly mentioned serviceImplClassName, we have just made it up. If it turns out that it
             * cannot be found, do not log the resulting ClassNotFountException in order to not stress the developers
             * and/or the users.
             */
            suppressClassNotFoundException = true;
        }

        Class<?> serviceImplClass = null;
        Throwable exception = null;

        try {
            serviceImplClass = Class.forName(serviceImplClassName);
        } catch (ClassNotFoundException cnfe) {
            if (!suppressClassNotFoundException)
                exception = cnfe;
        } catch (ExceptionInInitializerError eiie) {
            exception = eiie;
        } catch (LinkageError le) {
            exception = le;
        }

        T service = null;

        if ((serviceImplClass != null) && serviceClass.isAssignableFrom(serviceImplClass)) {
            try {
                @SuppressWarnings("unchecked")
                T t = (T) serviceImplClass.newInstance();

                service = t;
            } catch (Throwable t) {
                if (t instanceof ThreadDeath) {
                    throw (ThreadDeath) t;
                }
                else {
                    exception = t;
                    if (t instanceof InterruptedException)
                        Thread.currentThread().interrupt();
                }
            }
        }
        if (exception == null) {
            if (service != null) {
                synchronized (services) {
                    services.put(serviceClassName, service);
                }
            }
        }
        else {
            Timber.w(exception, "Failed to initialize service implementation %s. Will continue without it.", serviceImplClassName);
            if (serviceImplClassName.contains("MediaServiceImpl")) {
                DialogActivity.showDialog(aTalkApp.getGlobalContext(), aTalkApp.getResString(R.string.service_gui_ERROR),
                        aTalkApp.getResString(R.string.service_gui_CALL_DISABLE_ON_FAULT, serviceImplClassName, exception));
                aTalk.disableMediaServiceOnFault = true;
            }
        }
        return service;
    }
}
