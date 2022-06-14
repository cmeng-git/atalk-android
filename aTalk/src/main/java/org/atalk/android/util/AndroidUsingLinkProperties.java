package org.atalk.android.util;

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
 * <p>
 * Requires the ACCESS_NETWORK_STATE permission.
 * </p>
 */
public class AndroidUsingLinkProperties extends AbstractDnsServerLookupMechanism
{
    private final Context mContext;

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
        mContext = context;
    }

    @Override
    public boolean isAvailable()
    {
        return true; // Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP;
    }

    @Override
    public List<String> getDnsServerAddresses()
    {
        final ConnectivityManager connectivityManager = (ConnectivityManager) mContext.getSystemService(Context.CONNECTIVITY_SERVICE);
        final List<String> servers = new ArrayList<>();

        final Network[] networks = connectivityManager == null ? null : connectivityManager.getAllNetworks();
        if (networks != null) {
            // ConnectivityManager.getActiveNetwork() is API 23; null if otherwise
            final Network activeNetwork = getActiveNetwork(connectivityManager);
            int vpnOffset = 0;
            for (Network network : networks) {
                LinkProperties linkProperties = connectivityManager.getLinkProperties(network);
                if (linkProperties == null) {
                    continue;
                }

                final boolean isActiveNetwork = network.equals(activeNetwork);
                if (isActiveNetwork || activeNetwork == null) {
                    final NetworkInfo networkInfo = connectivityManager.getNetworkInfo(network);
                    final boolean isVpn = networkInfo != null && networkInfo.getType() == ConnectivityManager.TYPE_VPN;
                    final List<String> v4v6Servers = getIPv4First(linkProperties.getDnsServers());
                    // Timber.d("hasDefaultRoute: %s || isActiveNetwork: %s || activeNetwork: %s || isVpn: %s || IP: %s",
                    //        hasDefaultRoute(linkProperties), isActiveNetwork, activeNetwork, isVpn, toListOfStrings(linkProperties.getDnsServers()));

                    if (isVpn) {
                        servers.addAll(0, v4v6Servers);
                        vpnOffset += v4v6Servers.size();
                    }
                    // Prioritize the DNS servers of links which have a default route
                    else if (hasDefaultRoute(linkProperties)) {
                        servers.addAll(vpnOffset, v4v6Servers);
                    }
                    else {
                        servers.addAll(v4v6Servers);
                    }
                }
            }
        }
        // Timber.d("dns Server Addresses (linkProperty): %s", servers);
        return servers;
    }

    // @TargetApi(23)
    private static Network getActiveNetwork(ConnectivityManager cm)
    {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.M ? cm.getActiveNetwork() : null;
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
