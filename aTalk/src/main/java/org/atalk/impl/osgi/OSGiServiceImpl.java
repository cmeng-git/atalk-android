/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.atalk.impl.osgi;

import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.res.AssetManager;
import android.os.IBinder;
import android.text.TextUtils;

import org.atalk.android.R;
import org.atalk.android.aTalkApp;
import org.atalk.impl.osgi.framework.AsyncExecutor;
import org.atalk.impl.osgi.framework.launch.FrameworkFactoryImpl;
import org.atalk.service.configuration.ConfigurationService;
import org.atalk.service.osgi.BundleContextHolder;
import org.atalk.service.osgi.OSGiService;
import org.atalk.util.OSUtils;
import org.osgi.framework.BundleContext;
import org.osgi.framework.BundleException;
import org.osgi.framework.Constants;
import org.osgi.framework.launch.Framework;
import org.osgi.framework.launch.FrameworkFactory;
import org.osgi.framework.startlevel.BundleStartLevel;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.StringTokenizer;
import java.util.TreeMap;
import java.util.concurrent.TimeUnit;

/**
 * Implements the actual, internal functionality of {@link OSGiService}.
 *
 * @author Lyubomir Marinov
 * @author Eng Chong Meng
 */
public class OSGiServiceImpl {
    private final OSGiServiceBundleContextHolder bundleContextHolder = new OSGiServiceBundleContextHolder();

    private final AsyncExecutor<Runnable> executor = new AsyncExecutor<>(5, TimeUnit.MINUTES);

    /**
     * The <code>org.osgi.framework.launch.Framework</code> instance which represents the OSGi
     * instance launched by this <code>OSGiServiceImpl</code>.
     */
    private Framework framework;

    /**
     * The <code>Object</code> which synchronizes the access to {@link #framework}.
     */
    private final Object frameworkSyncRoot = new Object();

    /**
     * The Android {@link Service} which uses this instance as its very implementation.
     */
    private final OSGiService service;

    /**
     * Initializes a new <code>OSGiServiceImpl</code> instance which is to be used by a specific
     * Android <code>OSGiService</code> as its very implementation.
     *
     * @param service the Android <code>OSGiService</code> which is to use the new instance as its very implementation
     */
    public OSGiServiceImpl(OSGiService service) {
        this.service = service;
    }

    /**
     * Invoked by the Android system to initialize a communication channel to {@link #service}.
     * Returns an implementation of the public API of the <code>OSGiService</code> i.e.
     * {@link BundleContextHolder} in the form of an {@link IBinder}.
     *
     * @param intent the <code>Intent</code> which was used to bind to <code>service</code>
     *
     * @return an <code>IBinder</code> through which clients may call on to the public API of <code>OSGiService</code>
     *
     * @see Service#onBind(Intent)
     */
    public IBinder onBind(Intent intent) {
        return bundleContextHolder;
    }

    /**
     * Invoked by the Android system when {@link #service} is first created. Asynchronously starts
     * the OSGi framework (implementation) represented by this instance.
     *
     * @see Service#onCreate()
     */
    public void onCreate() {
        try {
            setScHomeDir();
        } catch (Throwable t) {
            if (t instanceof ThreadDeath)
                throw (ThreadDeath) t;
        }
        try {
            setJavaUtilLoggingConfigFile();
        } catch (Throwable t) {
            if (t instanceof ThreadDeath)
                throw (ThreadDeath) t;
        }

        executor.execute(new OnCreateCommand());
    }

    /**
     * Invoked by the Android system when {@link #service} is no longer used and is being removed.
     * Asynchronously stops the OSGi framework (implementation) represented by this instance.
     *
     * @see Service#onDestroy()
     */
    public void onDestroy() {
        synchronized (executor) {
            executor.execute(new OnDestroyCommand());
            executor.shutdown();
        }
    }

    /**
     * Invoked by the Android system every time a client explicitly starts {@link #service} by
     * calling {@link Context#startService(Intent)}. Always returns {@link Service#START_STICKY}.
     *
     * @param intent the <code>Intent</code> supplied to <code>Context.startService(Intent}</code>
     * @param flags additional data about the start request
     * @param startId a unique integer which represents this specific request to start
     *
     * @return a value which indicates what semantics the Android system should use for
     * <code>service</code>'s current started state
     *
     * @see Service#onStartCommand(Intent, int, int)
     */
    public int onStartCommand(Intent intent, int flags, int startId) {
        return Service.START_STICKY;
    }

    /**
     * Sets up <code>java.util.logging.LogManager</code> by assigning values to the system properties
     * which allow more control over reading the initial configuration.
     */
    private void setJavaUtilLoggingConfigFile() {
    }

    private void setScHomeDir() {
        String name = null;

        if (System.getProperty(ConfigurationService.PNAME_SC_HOME_DIR_LOCATION) == null) {
            File filesDir = service.getFilesDir();
            String location = filesDir.getParentFile().getAbsolutePath();

            name = filesDir.getName();
            System.setProperty(ConfigurationService.PNAME_SC_HOME_DIR_LOCATION, location);
        }
        if (System.getProperty(ConfigurationService.PNAME_SC_HOME_DIR_NAME) == null) {
            if (TextUtils.isEmpty(name)) {
                ApplicationInfo info = service.getApplicationInfo();
                name = info.name;
                if (TextUtils.isEmpty(name))
                    name = aTalkApp.getResString(R.string.app_name);
            }
            System.setProperty(ConfigurationService.PNAME_SC_HOME_DIR_NAME, name);
        }

        // Set log dir location to PNAME_SC_HOME_DIR_LOCATION
        if (System.getProperty(ConfigurationService.PNAME_SC_LOG_DIR_LOCATION) == null) {
            String homeDir = System.getProperty(ConfigurationService.PNAME_SC_HOME_DIR_LOCATION, null);

            System.setProperty(ConfigurationService.PNAME_SC_LOG_DIR_LOCATION, homeDir);
        }
        // Set cache dir location to Context.getCacheDir()
        if (System.getProperty(ConfigurationService.PNAME_SC_CACHE_DIR_LOCATION) == null) {
            File cacheDir = service.getCacheDir();
            String location = cacheDir.getParentFile().getAbsolutePath();
            System.setProperty(ConfigurationService.PNAME_SC_CACHE_DIR_LOCATION, location);
        }

        /*
         * Set the System property user.home as well because it may be relied upon (e.g. FMJ).
         */
        String location = System.getProperty(ConfigurationService.PNAME_SC_HOME_DIR_LOCATION);

        if ((location != null) && (!location.isEmpty())) {
            name = System.getProperty(ConfigurationService.PNAME_SC_HOME_DIR_NAME);
            if ((name != null) && (!name.isEmpty())) {
                System.setProperty("user.home", new File(location, name).getAbsolutePath());
            }
        }
    }

    /**
     * Asynchronously starts the OSGi framework (implementation) represented by this instance.
     */
    private class OnCreateCommand implements Runnable {
        public void run() {
            FrameworkFactory frameworkFactory = new FrameworkFactoryImpl();
            Map<String, String> configuration = new HashMap<>();

            TreeMap<Integer, List<String>> BUNDLES = getBundlesConfig(service);
            configuration.put(Constants.FRAMEWORK_BEGINNING_STARTLEVEL, Integer.toString(BUNDLES.lastKey()));
            Framework framework = frameworkFactory.newFramework(configuration);

            try {
                framework.init();
                BundleContext bundleContext = framework.getBundleContext();
                bundleContext.registerService(OSGiService.class, service, null);
                bundleContext.registerService(BundleContextHolder.class, bundleContextHolder, null);

                for (Map.Entry<Integer, List<String>> startLevelEntry : BUNDLES.entrySet()) {
                    int startLevel = startLevelEntry.getKey();

                    for (String location : startLevelEntry.getValue()) {
                        org.osgi.framework.Bundle bundle = bundleContext.installBundle(location);
                        if (bundle != null) {
                            BundleStartLevel bundleStartLevel = bundle.adapt(BundleStartLevel.class);

                            if (bundleStartLevel != null)
                                bundleStartLevel.setStartLevel(startLevel);
                        }
                    }
                }
                framework.start();
            } catch (BundleException be) {
                throw new RuntimeException(be);
            }

            synchronized (frameworkSyncRoot) {
                OSGiServiceImpl.this.framework = framework;
            }

            service.onOSGiStarted();
        }

        /**
         * Loads bundles configuration from the configured or default file name location.
         *
         * @param context the context to use
         *
         * @return the locations of the OSGi bundles (or rather of the class files of their
         * <code>BundleActivator</code> implementations) comprising the Jitsi core/library and the
         * application which is currently using it. And the corresponding start levels.
         */
        private TreeMap<Integer, List<String>> getBundlesConfig(Context context) {
            String fileName = System.getProperty("osgi.config.properties");
            if (fileName == null)
                fileName = "lib/osgi.client.run.properties";

            InputStream is = null;
            Properties props = new Properties();

            try {
                if (OSUtils.IS_ANDROID) {
                    if (context != null) {
                        is = context.getAssets().open(fileName, AssetManager.ACCESS_UNKNOWN);
                    }
                }
                else {
                    is = new FileInputStream(fileName);
                }

                if (is != null)
                    props.load(is);
            } catch (IOException ioe) {
                throw new RuntimeException(ioe);
            } finally {
                try {
                    if (is != null)
                        is.close();
                } catch (IOException ignore) {
                }
            }

            TreeMap<Integer, List<String>> startLevels = new TreeMap<>();

            for (Map.Entry<Object, Object> e : props.entrySet()) {
                String prop = e.getKey().toString().trim();
                Object value;

                if (prop.contains("auto.start.") && ((value = e.getValue()) != null)) {
                    String startLevelStr = prop.substring("auto.start.".length());
                    try {
                        int startLevelInt = Integer.parseInt(startLevelStr);

                        StringTokenizer classTokens = new StringTokenizer(value.toString(), " ");
                        List<String> classNames = new ArrayList<>();

                        while (classTokens.hasMoreTokens()) {
                            String className = classTokens.nextToken().trim();

                            if (!TextUtils.isEmpty(className) && !className.startsWith("#"))
                                classNames.add(className);
                        }
                        if (!classNames.isEmpty())
                            startLevels.put(startLevelInt, classNames);
                    } catch (Throwable t) {
                        if (t instanceof ThreadDeath)
                            throw (ThreadDeath) t;
                    }
                }
            }
            return startLevels;
        }
    }

    /**
     * Asynchronously stops the OSGi framework (implementation) represented by this instance.
     */
    private class OnDestroyCommand implements Runnable {
        public void run() {
            Framework framework;
            synchronized (frameworkSyncRoot) {
                framework = OSGiServiceImpl.this.framework;
                OSGiServiceImpl.this.framework = null;
            }

            if (framework != null)
                try {
                    framework.stop();
                } catch (BundleException be) {
                    throw new RuntimeException(be);
                }
        }
    }
}
