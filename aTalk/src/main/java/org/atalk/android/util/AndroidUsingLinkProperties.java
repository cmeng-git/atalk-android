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
package org.atalk.android.util;

import android.annotation.SuppressLint;
import android.annotation.TargetApi;
import android.content.Context;
import android.net.ConnectivityManager;
import android.net.LinkProperties;
import android.net.Network;
import android.net.NetworkInfo;
import android.net.RouteInfo;
import android.os.Build;

import org.minidns.DnsClient;
import org.minidns.dnsserverlookup.AbstractDnsServerLookupMechanism;
import org.minidns.dnsserverlookup.AndroidUsingExec;

import java.net.Inet4Address;
import java.net.InetAddress;
import java.util.ArrayList;
import java.util.List;

import timber.log.Timber;

/**
 * A DNS server lookup mechanism using Android's Link Properties method available on Android API 21 or higher.
 * Use {@link #setup(Context)} to setup this mechanism.
 *
 * Requires the ACCESS_NETWORK_STATE permission.
 *
 */
public class AndroidUsingLinkProperties extends AbstractDnsServerLookupMechanism
{
    private final ConnectivityManager connectivityManager;

    /**
     * Setup this DNS server lookup mechanism. You need to invoke this method only once,
     * ideally before you do your first DNS lookup.
     *
     * @param context a Context instance.
     * @return the instance of the newly setup mechanism
     */
    public static AndroidUsingLinkProperties setup(Context context)
    {
        AndroidUsingLinkProperties androidUsingLinkProperties = new AndroidUsingLinkProperties(context);
        DnsClient.addDnsServerLookupMechanism(androidUsingLinkProperties);
        return androidUsingLinkProperties;
    }

    public AndroidUsingLinkProperties(Context context)
    {
        super(AndroidUsingLinkProperties.class.getSimpleName(), AndroidUsingExec.PRIORITY - 1);
        connectivityManager = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
    }

    @SuppressLint("ObsoleteSdkInt")
    @Override
    public boolean isAvailable()
    {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP;
    }

    private Network getActiveNetwork()
    {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
            Network[] networks = connectivityManager.getAllNetworks();
            for (Network network : networks) {
                if (connectivityManager.getNetworkInfo(network).isConnected())
                    return network;
            }
            return null;
        }

        // ConnectivityManager.getActiveNetwork() is API 23; null if otherwise
        return connectivityManager.getActiveNetwork();
    }

    /**
     * Get DnsServerAddresses; null if unavailable so DnsClient#findDNS() will proceed with next available mechanism .
     *
     * @return servers list or null
     */
    @Override
    public List<String> getDnsServerAddresses()
    {
        final List<String> servers = new ArrayList<>();
        final Network network = getActiveNetwork();
        if (network == null) {
            return null;
        }

        LinkProperties linkProperties = connectivityManager.getLinkProperties(network);
        if (linkProperties == null) {
            return null;
        }

        int vpnOffset = 0;
        final NetworkInfo networkInfo = connectivityManager.getNetworkInfo(network);
        final boolean isVpn = networkInfo != null && networkInfo.getType() == ConnectivityManager.TYPE_VPN;
        final List<String> v4v6Servers = getIPv4First(linkProperties.getDnsServers());
        // Timber.d("hasDefaultRoute: %s activeNetwork: %s || isVpn: %s || IP: %s",
        //        hasDefaultRoute(linkProperties), network, isVpn, toListOfStrings(linkProperties.getDnsServers()));

        if (isVpn) {
            servers.addAll(0, v4v6Servers);
            // vpnOffset += v4v6Servers.size();
        }
        // Prioritize the DNS servers of links which have a default route
        else if (hasDefaultRoute(linkProperties)) {
            servers.addAll(vpnOffset, v4v6Servers);
        }
        else {
            servers.addAll(v4v6Servers);
        }

        // Timber.d("dns Server Addresses (linkProperty): %s", servers);
        return servers;
    }

    /**
     * Sort and return the list of given InetAddress in IPv4-IPv6 order, and keeping original in order
     *
     * @param in list of unsorted InetAddress
     * @return sorted vp4 vp6 IP addresses
     */
    private static List<String> getIPv4First(List<InetAddress> in)
    {
        List<String> out = new ArrayList<>();
        int i = 0;
        for (InetAddress addr : in) {
            if (addr instanceof Inet4Address) {
                out.add(i++, addr.getHostAddress());
            }
            else {
                out.add(addr.getHostAddress());
            }
        }
        return out;
    }

    @SuppressLint("ObsoleteSdkInt")
    @TargetApi(Build.VERSION_CODES.LOLLIPOP)
    private static boolean hasDefaultRoute(LinkProperties linkProperties)
    {
        for (RouteInfo route : linkProperties.getRoutes()) {
            if (route.isDefaultRoute()) {
                return true;
            }
        }
        return false;
    }
}
