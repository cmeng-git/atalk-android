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
package net.java.sip.communicator.impl.provdisc.mdns;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;

import javax.jmdns.JmDNS;
import javax.jmdns.ServiceInfo;

import net.java.sip.communicator.service.provdisc.event.DiscoveryEvent;
import net.java.sip.communicator.service.provdisc.event.DiscoveryListener;

import timber.log.Timber;

/**
 * Class that will perform mDNS provisioning discovery.
 *
 * @author Sebastien Vincent
 * @author Eng Chong Meng
 */
public class MDNSProvisioningDiscover implements Runnable {
    /**
     * MDNS timeout (in milliseconds).
     */
    private static final int MDNS_TIMEOUT = 2000;

    /**
     * List of <code>ProvisioningListener</code> that will be notified when a provisioning URL is retrieved.
     */
    private final List<DiscoveryListener> listeners = new ArrayList<>();

    /**
     * Reference to JmDNS singleton.
     */
    private JmDNS jmdns = null;

    /**
     * Constructor.
     */
    public MDNSProvisioningDiscover() {
    }

    /**
     * Thread entry point. It runs <code>discoverProvisioningURL</code> in a separate thread.
     */
    public void run() {
        String url = discoverProvisioningURL();

        if (url != null) {
            /* as we run in an asynchronous manner, notify the listener */
            DiscoveryEvent evt = new DiscoveryEvent(this, url);

            for (DiscoveryListener listener : listeners) {
                listener.notifyProvisioningURL(evt);
            }
        }
    }

    /**
     * It sends a mDNS to retrieve provisioning URL and wait for a response.
     * Thread stops after first successful answer that contains the provisioning URL.
     *
     * @return provisioning URL or null if no provisioning URL was discovered
     */
    public String discoverProvisioningURL() {
        StringBuilder url = new StringBuilder();
        try {
            jmdns = JmDNS.create();
        } catch (IOException e) {
            Timber.i("Failed to create JmDNS: %s", e.getMessage());
            return null;
        }

        ServiceInfo info = jmdns.getServiceInfo("_https._tcp.local", "Provisioning URL", MDNS_TIMEOUT);
        if (info == null) {
            /* try HTTP */
            info = jmdns.getServiceInfo("_http._tcp.local", "Provisioning URL", MDNS_TIMEOUT);
        }

        if (info != null && info.getName().equals("Provisioning URL")) {
            String protocol = info.getApplication();
            String[] urls = info.getURLs(protocol);
            String sUrl = urls.length > 0 ? urls[0] : protocol + "://null:" + info.getPort();
            url.append(sUrl);
            Enumeration<String> en = info.getPropertyNames();
            if (en.hasMoreElements()) {
                url.append("?");
            }

            /* add the parameters */
            while (en.hasMoreElements()) {
                String tmp = en.nextElement();
                /* take all other parameters except "path" */
                if (tmp.equals("path")) {
                    continue;
                }
                url.append(tmp);
                url.append("=");
                url.append(info.getPropertyString(tmp));
                if (en.hasMoreElements()) {
                    url.append("&");
                }
            }
        }
        /* close jmdns */
        try {
            jmdns.close();
            jmdns = null;
        } catch (Exception e) {
            Timber.w("Failed to close JmDNS: %s", e.getMessage());
        }
        return (url.length() > 0) ? url.toString() : null;
    }

    /**
     * Add a listener that will be notified when the <code>discoverProvisioningURL</code> has finished.
     *
     * @param listener <code>ProvisioningListener</code> to add
     */
    public void addDiscoveryListener(DiscoveryListener listener) {
        if (!listeners.contains(listener)) {
            listeners.add(listener);
        }
    }

    /**
     * Add a listener that will be notified when the <code>discoverProvisioningURL</code> has finished.
     *
     * @param listener <code>ProvisioningListener</code> to add
     */
    public void removeDiscoveryListener(DiscoveryListener listener) {
        listeners.remove(listener);
    }
}
