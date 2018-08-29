package org.atalk.android.util;

import android.annotation.TargetApi;
import android.content.Context;
import android.net.*;
import android.os.Build;

import org.minidns.DnsClient;
import org.minidns.dnsserverlookup.AbstractDnsServerLookupMechanism;
import org.minidns.dnsserverlookup.AndroidUsingExec;

import java.net.Inet4Address;
import java.net.InetAddress;
import java.util.ArrayList;
import java.util.List;

/**
 * A DNS server lookup mechanism using Android's Link Properties method available on Android API 21 or higher. Use
 * {@link #setup(Context)} to setup this mechanism.
 * <p>
 * Requires the ACCESS_NETWORK_STATE permission.
 * </p>
 */
public class AndroidUsingLinkProperties extends AbstractDnsServerLookupMechanism
{
    private final ConnectivityManager connectivityManager;

    /**
     * Setup this DNS server lookup mechanism. You need to invoke this method only once, ideally before you do your
     * first DNS lookup.
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

    @Override
    public boolean isAvailable()
    {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP;
    }

    @Override
    @TargetApi(21)
    public List<String> getDnsServerAddresses()
    {
        List<String> servers = new ArrayList<>();

        Network[] networks = connectivityManager == null ? null : connectivityManager.getAllNetworks();
        if (networks != null) {
            final Network activeNetwork = getActiveNetwork(connectivityManager);
            int vpnOffset = 0;
            for (Network network : networks) {
                LinkProperties linkProperties = connectivityManager.getLinkProperties(network);
                if (linkProperties == null) {
                    continue;
                }

                // Prioritize the DNS servers of links which have a default route
                NetworkInfo networkInfo = connectivityManager.getNetworkInfo(network);
                final boolean isActiveNetwork = network.equals(activeNetwork);
                if (networkInfo != null && isActiveNetwork && networkInfo.getType() == ConnectivityManager.TYPE_VPN) {
                    final List<String> tmp = getIPv4First(linkProperties.getDnsServers());
                    servers.addAll(0, tmp);
                    vpnOffset += tmp.size();
                }
                else if (hasDefaultRoute(linkProperties) || isActiveNetwork) {
                    servers.addAll(vpnOffset, getIPv4First(linkProperties.getDnsServers()));
                }
                else {
                    servers.addAll(getIPv4First(linkProperties.getDnsServers()));
                }
            }
        }
        return servers;
    }

    @TargetApi(23)
    private static Network getActiveNetwork(ConnectivityManager cm)
    {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.M ? cm.getActiveNetwork() : null;
    }

    private static List<String> getIPv4First(List<InetAddress> in)
    {
        List<String> out = new ArrayList<>();
        for (InetAddress addr : in) {
            if (addr instanceof Inet4Address) {
                out.add(0, addr.getHostAddress());
            }
            else {
                out.add(addr.getHostAddress());
            }
        }
        return out;
    }

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
