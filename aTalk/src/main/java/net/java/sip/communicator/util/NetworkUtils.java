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

package net.java.sip.communicator.util;

import android.text.TextUtils;

import org.atalk.android.plugin.timberlog.TimberLog;
import org.minidns.dnsqueryresult.DnsQueryResult;
import org.minidns.hla.ResolverApi;
import org.minidns.hla.ResolverResult;
import org.minidns.record.*;
import org.minidns.record.Record.TYPE;
import org.minidns.util.InetAddressUtil;

import java.io.IOException;
import java.net.*;
import java.util.*;

import timber.log.Timber;

/**
 * Utility methods and fields to use when working with network addresses.
 *
 * @author Eng Chong Meng
 */
public class NetworkUtils
{
    /**
     * A string containing the "any" local address for IPv6.
     */
    public static final String IN6_ADDR_ANY = "::0";

    /**
     * A string containing the "any" local address for IPv4.
     */
    public static final String IN4_ADDR_ANY = "0.0.0.0";

    /**
     * A string containing the "any" local address.
     */
    public static final String IN_ADDR_ANY = determineAnyAddress();

    /**
     * The length of IPv6 addresses.
     */
    private final static int IN6_ADDR_SIZE = 16;

    /**
     * The size of the tokens in a <tt>String</tt> representation of IPv6
     * addresses.
     */
    private final static int IN6_ADDR_TOKEN_SIZE = 2;

    /**
     * The length of IPv4 addresses.
     */
    private final static int IN4_ADDR_SIZE = 4;

    /**
     * The maximum int value that could correspond to a port number.
     */
    public static final int MAX_PORT_NUMBER = 65535;

    /**
     * The minimum int value that could correspond to a port number bindable
     * by the SIP Communicator.
     */
    public static final int MIN_PORT_NUMBER = 1024;

    /**
     * The random port number generator that we use in getRandomPortNumer()
     */
    private static Random portNumberGenerator = new Random();

    /**
     * The name of the boolean property that defines whether all domain names
     * looked up from Jitsi should be treated as absolute.
     */
    public static final String PNAME_DNS_ALWAYS_ABSOLUTE = "dns.DNSSEC_ALWAYS_ABSOLUTE";

    /**
     * Default value of {@link #PNAME_DNS_ALWAYS_ABSOLUTE}.
     */
    public static final boolean PDEFAULT_DNS_ALWAYS_ABSOLUTE = false;

    /**
     * A random number generator.
     */
    private static final Random random = new Random();

    static {
        String prefer6 = System.getProperty("java.net.preferIPv6Addresses");
        String prefer4 = System.getProperty("java.net.preferIPv4Stack");
        Timber.i("java.net.preferIPv6Addresses=%s; java.net.preferIPv4Stack=%s", prefer6, prefer4);
    }

    /**
     * Determines whether the address is the result of windows auto configuration.
     * (i.e. One that is in the 169.254.0.0 network)
     *
     * @param add the address to inspect
     * @return true if the address is auto-configured by windows, false otherwise.
     */
    public static boolean isWindowsAutoConfiguredIPv4Address(InetAddress add)
    {
        return ((add.getAddress()[0] & 0xFF) == 169 && (add.getAddress()[1] & 0xFF) == 254);
    }

    /**
     * Returns a random local port number that user applications could bind to. (i.e. above 1024).
     *
     * @return a random int located between 1024 and 65 535.
     */
    public static int getRandomPortNumber()
    {
        return getRandomPortNumber(MIN_PORT_NUMBER, MAX_PORT_NUMBER);
    }

    /**
     * Returns a random local port number in the interval [min, max].
     *
     * @param min the minimum allowed value for the returned port number.
     * @param max the maximum allowed value for the returned port number.
     * @return a random int in the interval [min, max].
     */
    public static int getRandomPortNumber(int min, int max)
    {
        return portNumberGenerator.nextInt(max - min + 1) + min;
    }

    /**
     * Returns array of hosts from the SRV record of the specified domain.
     * The records are ordered against the SRV record priority
     *
     * @param domain the name of the domain we'd like to resolve (_proto._tcp included).
     * @return an array of SRV containing records returned by the DNS server - address and port .
     * @throws IOException if an IO error occurs.
     */
    public static SRV[] getSRVRecords(String domain)
            throws IOException
    {
        try {
            ResolverResult<SRV> result = ResolverApi.INSTANCE.resolve(domain, SRV.class);
            Set<SRV> records = result.getAnswersOrEmptySet();
            if (!records.isEmpty()) {
                SRV[] srvRecords = records.toArray(new SRV[0]);
                // Sort the SRV RRs by priority (lower is preferred) and weight.
                sortSrvRecord(srvRecords);
                return srvRecords;
            }
            else {
                return null;
            }
        } catch (IOException e) {
            Timber.e("No SRV record found for %s: %s", domain, e.getMessage());
            throw new IOException(e);
        }
    }

    /**
     * Returns array of SRV Record for the specified (service, proto and domain).
     * or <tt>null</tt> if the specified domain is of unknown host or there are no SRV records for <tt>domain</tt>.
     *
     * @param service the service that we are trying to get a record for e.g. xmpp.
     * @param proto the protocol that we'd like <tt>service</tt> on i.e. tcp or udp.
     * @param domain the name of the domain we'd like to resolve i.e. example.org.
     * @return an array of SRV containing records returned by the DNS server - address and port .
     * @throws IOException if an IO error occurs.
     */
    public static SRV[] getSRVRecords(String service, String proto, String domain)
            throws IOException
    {
        // verify the domain is knownHost and reachable before proceed
        try {
            InetAddress inetAddress = InetAddress.getByName(domain);
        } catch (UnknownHostException e) {
            new Exception("_" + service + "._" + proto + "." + domain).printStackTrace();
            return null;
        }
        return getSRVRecords("_" + service + "._" + proto + "." + domain);
    }

    /**
     * Not use : not implemented by miniDNS
     * Makes a NAPTR query and returns the result. The returned records are an array of [Order, Service(Transport)
     * and Replacement (the srv to query for servers and ports)] this all for supplied <tt>domain</tt>.
     *
     * @param domain the name of the domain we'd like to resolve.
     * @return an array with the values or null if no records found.
     */
    public static String[][] getNAPTRRecords(String domain)
    {
        List<Record<? extends Data>> records;
        try {
            DnsQueryResult dnsQueryResult = ResolverApi.INSTANCE.getClient().query(domain, TYPE.NAPTR);
            records = dnsQueryResult.query.answerSection;
        } catch (IOException tpe) {
            Timber.log(TimberLog.FINER, "No A record found for " + domain);
            // throw new ParseException(tpe.getMessage(), 0);
            return null;
        }

        if (records != null) {
            List<String[]> recVals = new ArrayList<>(records.size());
            for (int i = 0; i < records.size(); i++) {
                String[] recVal = new String[4];
//                NAPTR r = (NAPTR) records.get(i).getPayload();
//
//                // todo - check here for broken records as missing transport
//                recVal[0] = "" + r.getOrder();
//                recVal[1] = getProtocolFromNAPTRRecords(r.getService());
//                // we don't understand this NAPTR, maybe it's not for SIP?
//                if (recVal[1] == null) {
//                    continue;
//                }
//
//                String replacement = r.getReplacement().toString();
//                if (replacement.endsWith(".")) {
//                    recVal[2] = replacement.substring(0, replacement.length() - 1);
//                }
//                else {
//                    recVal[2] = replacement;
//                }
//                recVal[3] = "" + r.getPreference();
//                recVals.add(recVal);
            }

            // sort the SRV RRs by RR value (lower is preferred)
            Collections.sort(recVals, new Comparator<String[]>()
            {
                // Sorts NAPTR records by ORDER (low number first), PREFERENCE (low number first) and
                // PROTOCOL (0-TLS, 1-TCP, 2-UDP).
                public int compare(String[] array1, String[] array2)
                {
                    // First tries to define the priority with the NAPTR order.
                    int order = Integer.parseInt(array1[0]) - Integer.parseInt(array2[0]);
                    if (order != 0) {
                        return order;
                    }
                    // Second tries to define the priority with the NAPTR preference.
                    int preference = Integer.parseInt(array1[3]) - Integer.parseInt(array2[3]);
                    if (preference != 0) {
                        return preference;
                    }
                    // Finally defines the priority with the NAPTR protocol.
                    int protocol = getProtocolPriority(array1[1]) - getProtocolPriority(array2[1]);
                    return protocol;
                }
            });

            String[][] arrayResult = new String[recVals.size()][4];
            arrayResult = recVals.toArray(arrayResult);
            Timber.log(TimberLog.FINER, "NAPTRs for " + domain + " = " + Arrays.toString(arrayResult));
            return arrayResult;
        }
        return null;
    }

    /**
     * Returns the mapping from rfc3263 between service and the protocols.
     *
     * @param service the service from NAPTR record.
     * @return the protocol TCP, UDP or TLS.
     */
    private static String getProtocolFromNAPTRRecords(String service)
    {
        if (service.equalsIgnoreCase("SIP+D2U"))
            return "UDP";
        else if (service.equalsIgnoreCase("SIP+D2T"))
            return "TCP";
        else if (service.equalsIgnoreCase("SIPS+D2T"))
            return "TLS";
        else
            return null;
    }

    /**
     * Returns the priority of a protocol. The lowest priority is the highest: 0-TLS, 1-TCP, 2-UDP.
     *
     * @param protocol The protocol name: "TLS", "TCP" or "UDP".
     * @return The priority of a protocol. The lowest priority is the highest: 0-TLS, 1-TCP, 2-UDP.
     */
    private static int getProtocolPriority(String protocol)
    {
        if (protocol.equals("TLS"))
            return 0;
        else if (protocol.equals("TCP"))
            return 1;
        return 2; // "UDP".
    }

    /**
     * Creates an InetAddress from the specified <tt>hostAddress</tt>. The point of using the method rather than
     * creating the address by yourself is that it would first check whether the specified <tt>hostAddress</tt>
     * is indeed a valid ip address. It this is the case, the method would create the <tt>InetAddress</tt> using
     * the <tt>InetAddress.getByAddress()</tt> method so that no DNS resolution is attempted by the JRE. Otherwise
     * it would simply use <tt>InetAddress.getByName()</tt> so that we would an <tt>InetAddress</tt> instance
     * even at the cost of a potential DNS resolution.
     *
     * @param hostAddress the <tt>String</tt> representation of the address
     * that we would like to create an <tt>InetAddress</tt> instance for.
     * @return an <tt>InetAddress</tt> instance corresponding to the specified <tt>hostAddress</tt>.
     * @throws UnknownHostException if any of the <tt>InetAddress</tt> methods we are using throw an exception.
     * @throws IllegalArgumentException if the given hostAddress is not an ip4 or ip6 address.
     */
    public static InetAddress getInetAddress(String hostAddress)
            throws UnknownHostException, IllegalArgumentException
    {
        if (TextUtils.isEmpty(hostAddress)) {
            throw new UnknownHostException(hostAddress + " is not a valid host address");
        }

        // transform IPv6 literals into normal addresses
        if (hostAddress.charAt(0) == '[') {
            // This is supposed to be an IPv6 literal
            if (hostAddress.length() > 2 && hostAddress.charAt(hostAddress.length() - 1) == ']') {
                hostAddress = hostAddress.substring(1, hostAddress.length() - 1);
            }
            else {
                // This was supposed to be a IPv6 address, but it's not!
                throw new UnknownHostException(hostAddress);
            }
        }

        // if not IPv6, then parse as IPv4 address else throws
        InetAddress inetAddress;
        try {
            inetAddress = InetAddressUtil.ipv6From(hostAddress);
        } catch (IllegalArgumentException e) {
            inetAddress = InetAddressUtil.ipv4From(hostAddress);
        }
        return InetAddress.getByAddress(hostAddress, inetAddress.getAddress());
    }

    /**
     * Returns array of hosts from the A and AAAA records of the specified domain. The records are
     * ordered against the IPv4/IPv6 protocol priority
     *
     * @param domain the name of the domain we'd like to resolve.
     * @param port the port number of the returned <tt>InetSocketAddress</tt>
     * @return an array of InetSocketAddress containing records returned by the DNS server - address and port .
     * @throws UnknownHostException if IP address is of illegal length
     * @throws IOException if an IO error occurs.
     */
    public static List<InetSocketAddress> getAandAAAARecords(String domain, int port)
            throws IOException
    {
        byte[] address;
        List<InetSocketAddress> inetSocketAddresses = new ArrayList<>();
        try {
            address = InetAddressUtil.ipv4From(domain).getAddress();
        } catch (IllegalArgumentException e) {
            address = InetAddressUtil.ipv6From(domain).getAddress();
        }
        if (address != null) {
            inetSocketAddresses.add(new InetSocketAddress(InetAddress.getByAddress(domain, address), port));
            return inetSocketAddresses;
        }
        else
            Timber.i("Unable to create InetAddress for <%s>; Try using A/AAAA RR.", domain);

        ResolverResult<A> resultA;
        ResolverResult<AAAA> resultAAAA;
        boolean v6lookup = Boolean.getBoolean("java.net.preferIPv6Addresses");

        for (int i = 0; i < 2; i++) {
            if (v6lookup) {
                resultAAAA = ResolverApi.INSTANCE.resolve(domain, AAAA.class);
                if (!resultAAAA.wasSuccessful()) {
                    continue;
                }
                Set<AAAA> answers = resultAAAA.getAnswers();
                for (AAAA aaaa : answers) {
                    InetAddress inetAddress = aaaa.getInetAddress();
                    inetSocketAddresses.add(new InetSocketAddress(inetAddress, port));
                }
            }
            else {
                resultA = ResolverApi.INSTANCE.resolve(domain, A.class);
                if (!resultA.wasSuccessful()) {
                    continue;
                }
                Set<A> answers = resultA.getAnswers();
                for (A a : answers) {
                    InetAddress inetAddress = a.getInetAddress();
                    inetSocketAddresses.add(new InetSocketAddress(inetAddress, port));
                }
            }
            v6lookup = !v6lookup;
        }
        return inetSocketAddresses;
    }

    /**
     * Returns array of hosts from the A record of the specified domain.
     * The records are ordered against the A record priority
     *
     * @param domain the name of the domain we'd like to resolve.
     * @param port the port number of the returned <tt>InetSocketAddress</tt>
     * @return an array of InetSocketAddress containing records returned by the DNS server - address and port .
     */
    public static List<InetSocketAddress> getARecords(String domain, int port)
            throws IOException
    {
        List<InetSocketAddress> inetSocketAddresses = new ArrayList<>();
        try {
            byte[] address = InetAddressUtil.ipv4From(domain).getAddress();
            inetSocketAddresses.add(new InetSocketAddress(InetAddress.getByAddress(domain, address), port));
            return inetSocketAddresses;
        } catch (IllegalArgumentException e) {
            Timber.e(e, "Unable to create InetAddress for <%s>", domain);
        }

        ResolverResult<A> result = ResolverApi.INSTANCE.resolve(domain, A.class);
        if (!result.wasSuccessful()) {
            return null;
        }

        Set<A> answers = result.getAnswers();
        for (A a : answers) {
            InetAddress inetAddress = a.getInetAddress();
            inetSocketAddresses.add(new InetSocketAddress(inetAddress, port));
        }
        return inetSocketAddresses;
    }

    /**
     * Returns array of hosts from the AAAA record of the specified domain.
     * The records are ordered against the AAAA record priority
     *
     * @param domain the name of the domain we'd like to resolve.
     * @param port the port number of the returned <tt>InetSocketAddress</tt>
     * @return an array of InetSocketAddress containing records returned by the DNS server - address and port .
     * @throws IOException if an IO error occurs.
     */
    public static List<InetSocketAddress> getAAAARecords(String domain, int port)
            throws IOException
    {
        List<InetSocketAddress> inetSocketAddresses = new ArrayList<>();
        try {
            byte[] address = InetAddressUtil.ipv6From(domain).getAddress();
            inetSocketAddresses.add(new InetSocketAddress(InetAddress.getByAddress(domain, address), port));
            return inetSocketAddresses;
        } catch (IllegalArgumentException e) {
            Timber.e(e, "Unable to create InetAddress for <%s>", domain);
        }

        ResolverResult<AAAA> result = ResolverApi.INSTANCE.resolve(domain, AAAA.class);
        if (!result.wasSuccessful()) {
            return null;
        }

        Set<AAAA> answers = result.getAnswers();
        for (AAAA aaaa : answers) {
            InetAddress inetAddress = aaaa.getInetAddress();
            inetSocketAddresses.add(new InetSocketAddress(inetAddress, port));
        }
        return inetSocketAddresses;
    }

    /**
     * Tries to determine if this host supports IPv6 addresses (i.e. has at
     * least one IPv6 address) and returns IN6_ADDR_ANY or IN4_ADDR_ANY
     * accordingly. This method is only used to initialize IN_ADDR_ANY so that
     * it could be used when binding sockets. The reason we need it is because
     * on mac (contrary to lin or win) binding a socket on 0.0.0.0 would make
     * it deaf to IPv6 traffic. Binding on ::0 does the trick but that would
     * fail on hosts that have no IPv6 support. Using the result of this method
     * provides an easy way to bind sockets in cases where we simply want any
     * IP packets coming on the port we are listening on (regardless of IP version).
     *
     * @return IN6_ADDR_ANY or IN4_ADDR_ANY if this host supports or not IPv6.
     */
    private static String determineAnyAddress()
    {
        Enumeration<NetworkInterface> ifaces;
        try {
            ifaces = NetworkInterface.getNetworkInterfaces();
        } catch (SocketException e) {
            Timber.d(e, "Couldn't retrieve local interfaces.");
            return IN4_ADDR_ANY;
        }

        while (ifaces.hasMoreElements()) {
            Enumeration<InetAddress> addrs = ifaces.nextElement().getInetAddresses();
            while (addrs.hasMoreElements()) {
                if (addrs.nextElement() instanceof Inet6Address)
                    return IN6_ADDR_ANY;
            }
        }
        return IN4_ADDR_ANY;
    }

    /**
     * Checks whether <tt>address</tt> is a valid IP address string.
     *
     * @param address the address that we'd like to check
     * @return true if address is an IPv4 or IPv6 address and false otherwise.
     */
    public static boolean isValidIPAddress(String address)
    {
        if (TextUtils.isEmpty(address)) {
            return false;
        }
        // look for IPv6 brackets and remove brackets for parsing
        if (address.charAt(0) == '[') {
            // This is supposed to be an IPv6 literal
            if ((address.length() > 2) && (address.charAt(address.length() - 1) == ']')) {
                // remove brackets from IPv6
                address = address.substring(1, address.length() - 1);
            }
            else {
                return false;
            }
        }

        // look for IP addresses valid pattern i.e. start with digit or ":"
        if (Character.digit(address.charAt(0), 16) != -1 || (address.charAt(0) == ':')) {
            // see if it is IPv4 address; if not, see if it is IPv6 address
            InetAddress inetAddress;
            try {
                // if IPv6is found as expected
                inetAddress = InetAddressUtil.ipv6From(address);
                return (inetAddress != null);
            } catch (IllegalArgumentException e6) {
                try {
                    inetAddress = InetAddressUtil.ipv4From(address);
                    return (inetAddress != null);
                } catch (IllegalArgumentException e4) {
                    Timber.w("The given IP address is an unkownHost: %s", address);
                    return false;
                }
            }
        }
        return false;
    }

    /**
     * Determines whether <tt>port</tt> is a valid port number bindable by an
     * application (i.e. an integer between 1024 and 65535).
     *
     * @param port the port number that we'd like verified.
     * @return <tt>true</tt> if port is a valid and bindable port number and <tt>alse</tt> otherwise.
     */
    public static boolean isValidPortNumber(int port)
    {
        return MIN_PORT_NUMBER <= port && port <= MAX_PORT_NUMBER;
    }

    /**
     * Returns an IPv4 address matching the one mapped in the IPv6
     * <tt>addr</tt>. Both input and returned value are in network order.
     *
     * @param addr a String representing an IPv4-Mapped address in textual format
     * @return a byte array numerically representing the IPv4 address
     */
    public static byte[] mappedIPv4ToRealIPv4(byte[] addr)
    {
        if (isMappedIPv4Addr(addr)) {
            byte[] newAddr = new byte[IN4_ADDR_SIZE];
            System.arraycopy(addr, 12, newAddr, 0, IN6_ADDR_SIZE);
            return newAddr;
        }
        return null;
    }

    /**
     * Utility method to check if the specified <tt>address</tt> is an IPv4 mapped IPv6 address.
     *
     * @param address the address that we'd like to determine as an IPv4 mapped one or not.
     * @return <tt>true</tt> if address is an IPv4 mapped IPv6 address and <tt>false</tt> otherwise.
     */
    private static boolean isMappedIPv4Addr(byte[] address)
    {
        if (address.length < IN6_ADDR_SIZE) {
            return false;
        }

        if ((address[0] == 0x00) && (address[1] == 0x00)
                && (address[2] == 0x00) && (address[3] == 0x00)
                && (address[4] == 0x00) && (address[5] == 0x00)
                && (address[6] == 0x00) && (address[7] == 0x00)
                && (address[8] == 0x00) && (address[9] == 0x00)
                && (address[10] == (byte) 0xff)
                && (address[11] == (byte) 0xff)) {
            return true;
        }
        return false;
    }

    /**
     * Sorts the SRV record list by priority and weight.
     *
     * @param srvRecords The list of SRV records.
     */
    private static void sortSrvRecord(SRV[] srvRecords)
    {
        // Sort the SRV RRs by priority (lower is preferred).
        Arrays.sort(srvRecords, (obj1, obj2) -> (obj1.priority - obj2.priority));

        // Sort the SRV RRs by weight (larger weight has a proportionately higher probability of being selected).
        sortSrvRecordByWeight(srvRecords);
    }

    /**
     * Sorts each priority of the SRV record list. Each priority is sorted with
     * the probability given by the weight attribute.
     *
     * @param srvRecords The list of SRV records already sorted by priority.
     */
    private static void sortSrvRecordByWeight(SRV[] srvRecords)
    {
        int currentPriority = srvRecords[0].priority;
        int startIndex = 0;

        for (int i = 0; i < srvRecords.length; ++i) {
            if (currentPriority != srvRecords[i].priority) {
                // Sort the current priority.
                sortSrvRecordPriorityByWeight(srvRecords, startIndex, i);
                // Reinit variables for the next priority.
                startIndex = i;
                currentPriority = srvRecords[i].priority;
            }
        }
    }

    /**
     * Sorts SRV record list for a given priority: this priority is sorted with
     * the probability given by the weight attribute.
     *
     * @param srvRecords The list of SRV records already sorted by priority.
     * @param startIndex The first index (included) for the current priority.
     * @param endIndex The last index (excluded) for the current priority.
     */
    private static void sortSrvRecordPriorityByWeight(SRV[] srvRecords, int startIndex, int endIndex)
    {
        int randomWeight;

        // Loops over the items of the current priority.
        while (startIndex < endIndex) {
            // Compute a random number in [0...totalPriorityWeight].
            randomWeight = getRandomWeight(srvRecords, startIndex, endIndex);

            // Move the selected item on top of the unsorted items for this priority.
            moveSelectedSRVRecord(srvRecords, startIndex, endIndex, randomWeight);

            // Move to next index.
            ++startIndex;
        }
    }

    /**
     * Compute a random number in [0...totalPriorityWeight] with
     * totalPriorityWeight the sum of all weight for the current priority.
     *
     * @param srvRecords The list of SRV records already sorted by priority.
     * @param startIndex The first index (included) for the current priority.
     * @param endIndex The last index (excluded) for the current priority.
     * @return A random number in [0...totalPriorityWeight] with
     * totalPriorityWeight the sum of all weight for the current priority.
     */
    private static int getRandomWeight(SRV[] srvRecords, int startIndex, int endIndex)
    {
        int totalPriorityWeight = 0;

        // Compute the max born.
        for (int i = startIndex; i < endIndex; ++i) {
            totalPriorityWeight += srvRecords[i].weight;
        }
        // Compute a random number in [0...totalPriorityWeight].
        return random.nextInt(totalPriorityWeight + 1);
    }

    /**
     * Moves the selected SRV record in top of the unsorted items for this priority.
     *
     * @param srvRecords The list of SRV records already sorted by priority.
     * @param startIndex The first unsorted index (included) for the current priority.
     * @param endIndex The last unsorted index (excluded) for the current priority.
     * @param selectedWeight The selected weight used to design the selected item to move.
     */
    private static void moveSelectedSRVRecord(SRV[] srvRecords, int startIndex, int endIndex, int selectedWeight)
    {
        SRV tmpSrvRecord;
        int totalPriorityWeight = 0;

        for (int i = startIndex; i < endIndex; ++i) {
            totalPriorityWeight += srvRecords[i].weight;

            // If we found the selecting record.
            if (totalPriorityWeight >= selectedWeight) {
                // Switch between startIndex and j.
                tmpSrvRecord = srvRecords[startIndex];
                srvRecords[startIndex] = srvRecords[i];
                srvRecords[i] = tmpSrvRecord;
                // Break the loop;
                return;
            }
        }
    }
}
