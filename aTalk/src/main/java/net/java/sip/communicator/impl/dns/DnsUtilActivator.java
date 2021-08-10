/*
 * aTalk, android VoIP and Instant Messaging client
 * Copyright 2014 Eng Chong Meng
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package net.java.sip.communicator.impl.dns;

import org.atalk.android.aTalkApp;
import org.atalk.android.util.AndroidUsingLinkProperties;
import org.osgi.framework.BundleActivator;
import org.osgi.framework.BundleContext;

import timber.log.Timber;

/**
 * The DNS Util activator.
 *
 * @author Eng Chong Meng
 */
public class DnsUtilActivator implements BundleActivator
{
    /**
     * Calls <tt>Thread.setUncaughtExceptionHandler()</tt>
     *
     * @param context The execution context of the bundle being started (unused).
     * @throws Exception If this method throws an exception, this bundle is marked as stopped and the Framework
     * will remove this bundle's listeners, unregister all services registered by this
     * bundle, and release all services used by this bundle.
     */
    public void start(BundleContext context)
            throws Exception
    {
        // Fall back to use miniDns 0.3.2 AndroidUsingExec instead
        // Init miniDNS Resolver for android 21/23 requirements
        // DnsClient.removeDNSServerLookupMechanism(AndroidUsingExec.INSTANCE);
        // DnsClient.addDnsServerLookupMechanism(AndroidUsingExecLowPriority.INSTANCE);

        AndroidUsingLinkProperties.setup(aTalkApp.getGlobalContext());
        Timber.i("Mini DNS service ... [STARTED]");
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
}
