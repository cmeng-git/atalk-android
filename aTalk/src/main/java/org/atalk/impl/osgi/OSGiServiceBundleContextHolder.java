/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.atalk.impl.osgi;

import android.os.Binder;

import java.util.ArrayList;
import java.util.List;

import org.atalk.service.osgi.BundleContextHolder;
import org.osgi.framework.BundleActivator;
import org.osgi.framework.BundleContext;

import timber.log.Timber;

/**
 * @author Lyubomir Marinov
 */
public class OSGiServiceBundleContextHolder extends Binder implements BundleActivator, BundleContextHolder {
    private final List<BundleActivator> bundleActivators = new ArrayList<>();

    private BundleContext bundleContext;

    public void addBundleActivator(BundleActivator bundleActivator) {
        if (bundleActivator == null)
            throw new NullPointerException("bundleActivator");
        else {
            synchronized (bundleActivators) {
                if (!bundleActivators.contains(bundleActivator) && bundleActivators.add(bundleActivator) && (bundleContext != null)) {
                    try {
                        bundleActivator.start(bundleContext);
                    } catch (Throwable t) {
                        Timber.e(t, "Error starting bundle: %s", bundleActivator);

                        if (t instanceof ThreadDeath)
                            throw (ThreadDeath) t;
                    }
                }
            }
        }
    }

    public BundleContext getBundleContext() {
        synchronized (bundleActivators) {
            return bundleContext;
        }
    }

    public void removeBundleActivator(BundleActivator bundleActivator) {
        if (bundleActivator != null) {
            synchronized (bundleActivators) {
                bundleActivators.remove(bundleActivator);
            }
        }
    }

    public void start(BundleContext bundleContext)
            throws Exception {
        synchronized (bundleActivators) {
            this.bundleContext = bundleContext;

            for (BundleActivator bundleActivator : bundleActivators) {
                try {
                    bundleActivator.start(bundleContext);
                } catch (Throwable t) {
                    Timber.e(t, "Error starting bundle: %s", bundleActivator);

                    if (t instanceof ThreadDeath)
                        throw (ThreadDeath) t;
                }
            }
        }
    }

    public void stop(BundleContext bundleContext)
            throws Exception {
        synchronized (bundleActivators) {
            try {

                for (BundleActivator bundleActivator : bundleActivators) {
                    try {
                        bundleActivator.stop(bundleContext);
                    } catch (Throwable t) {
                        if (t instanceof ThreadDeath)
                            throw (ThreadDeath) t;
                    }
                }
            } finally {
                this.bundleContext = null;
            }
        }
    }
}
