/*
 * Copyright @ 2015-2016 Atlassian Pty Ltd
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
package org.ice4j.ice.harvest;

import org.atalk.util.concurrent.ExecutorFactory;
import org.ice4j.StackProperties;
import org.ice4j.Transport;
import org.ice4j.TransportAddress;
import org.jetbrains.annotations.NotNull;

import java.net.Inet6Address;
import java.net.InetAddress;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.logging.Logger;

/**
 * Manages a static list of {@link MappingCandidateHarvester} instances, created
 * according to configuration provided as system properties.
 *
 * The instances in the set are safe to use by any {@code Agent}s.
 *
 * @author Damian Minkov
 * @author Boris Grozev
 * @author Eng Chong Meng
 */
public class MappingCandidateHarvesters
{
    /**
     * The {@link Logger} used by the {@link MappingCandidateHarvesters}
     * class for logging output.
     */
    private static final Logger logger = Logger.getLogger(MappingCandidateHarvesters.class.getName());

    /**
     * The name of the property that specifies the local address, if any, for the pre-configured NAT harvester.
     */
    public static final String NAT_HARVESTER_LOCAL_ADDRESS_PNAME = "org.ice4j.ice.harvest.NAT_HARVESTER_LOCAL_ADDRESS";

    /**
     * The name of the property that specifies the public address, if any, for the pre-configured NAT harvester.
     */
    public static final String NAT_HARVESTER_PUBLIC_ADDRESS_PNAME = "org.ice4j.ice.harvest.NAT_HARVESTER_PUBLIC_ADDRESS";

    /**
     * The name of the property used to disable the AWS harvester.
     */
    public static final String DISABLE_AWS_HARVESTER_PNAME = "org.ice4j.ice.harvest.DISABLE_AWS_HARVESTER";

    /**
     * The name of the property which forces the use of the AWS harvester.
     */
    public static final String FORCE_AWS_HARVESTER_PNAME = "org.ice4j.ice.harvest.FORCE_AWS_HARVESTER";

    /**
     * The name of the property which contains the addresses of the STUN servers
     * to use for the STUN mapping harvester. The property should contain a
     * comma-separated list of addresses (pairs of IP address and port, separated by a colon). Example:
     * {@code stun1.example.com:12345,stun2.example.com:23456}
     */
    public static final String STUN_MAPPING_HARVESTER_ADDRESSES_PNAME
            = "org.ice4j.ice.harvest.STUN_MAPPING_HARVESTER_ADDRESSES";

    /**
     * Whether {@link #harvesters} has been initialized.
     */
    private static boolean initialized = false;

    /**
     * The list of already configured harvesters.
     */
    private static MappingCandidateHarvester[] harvesters = new MappingCandidateHarvester[0];

    /**
     * Whether the discovery of a public address via STUN has failed.
     * It is considered failed if the configuration included at least one STUN
     * server, but we failed to receive at least one valid response.
     * Note that this defaults to false and is only raised after we are certain
     * we failed (i.e. after our STUN transactions timeout).
     */
    public static boolean stunDiscoveryFailed = false;

    /**
     * @return the list of configured harvesters.
     */
    public static MappingCandidateHarvester[] getHarvesters()
    {
        initialize();
        return harvesters;
    }

    /**
     * @return the (first) mapping harvester which matches a given public address, or {@code null} if none match it.
     */
    public static MappingCandidateHarvester findHarvesterForAddress(TransportAddress publicAddress)
    {
        for (MappingCandidateHarvester harvester : harvesters) {
            if (harvester.publicAddressMatches(publicAddress)) {
                return harvester;
            }
        }
        return null;
    }

    /**
     * Initializes {@link #harvesters}.
     * First it reads the configuration and instantiates harvesters accordingly,
     * waiting for their initialization (which may include network communication
     * and thus take a long time). Then it removes harvesters which failed to
     * initialize properly and remove any harvesters with duplicate addresses.
     *
     * Three types of mapping harvesters are supported: NAT (with pre-configured addresses), AWS and STUN.
     */
    public static synchronized void initialize()
    {
        if (initialized)
            return;
        initialized = true;

        long start = System.currentTimeMillis();
        List<MappingCandidateHarvester> harvesterList = new LinkedList<>();

        // Pre-configured NAT harvester.
        String localAddressStr = StackProperties.getString(NAT_HARVESTER_LOCAL_ADDRESS_PNAME);
        String publicAddressStr = StackProperties.getString(NAT_HARVESTER_PUBLIC_ADDRESS_PNAME);

        if (localAddressStr != null && publicAddressStr != null) {
            // the port number is unused, 9 is for "discard"
            TransportAddress localAddress = new TransportAddress(localAddressStr, 9, Transport.UDP);
            TransportAddress publicAddress = new TransportAddress(publicAddressStr, 9, Transport.UDP);

            harvesterList.add(new StaticMappingCandidateHarvester(publicAddress, localAddress));
        }

        // AWS harvester
        boolean enableAwsHarvester = !StackProperties.getBoolean(DISABLE_AWS_HARVESTER_PNAME, false);
        boolean forceAwsHarvester = StackProperties.getBoolean(FORCE_AWS_HARVESTER_PNAME, false);
        logger.info("AWS configuration enable = " + enableAwsHarvester + "; force = " + forceAwsHarvester);

        if (enableAwsHarvester && (forceAwsHarvester || AwsCandidateHarvester.smellsLikeAnEC2())) {
            logger.info("Using AwsCandidateHarvester.");
            harvesterList.add(new AwsCandidateHarvester());
        }

        // STUN harvesters
        String stunServer = StackProperties.getString(STUN_MAPPING_HARVESTER_ADDRESSES_PNAME);
        // stunServer = "stun1.l.google.com:19302,stun2.l.google.com:19302,stun3.l.google.com:19302";
        if (stunServer != null && !stunServer.isEmpty()) {
            // Create STUN harvesters (and wait for all of their discovery to finish).
            List<String> stunServers = Arrays.asList(stunServer.split(","));
            List<StunMappingCandidateHarvester> stunHarvesters = createStunHarvesters(stunServers);

            // We have STUN servers configured, so flag failure if none of them were able to discover an address.
            stunDiscoveryFailed = stunHarvesters.isEmpty();

            harvesterList.addAll(stunHarvesters);
        }

        harvesterList = prune(harvesterList);
        harvesters = harvesterList.toArray(new MappingCandidateHarvester[0]);

        for (MappingCandidateHarvester harvester : harvesters) {
            logger.info("Using " + harvester);
        }
        logger.info("Initialized mapping harvesters (delay=" + (System.currentTimeMillis() - start) + "ms). "
                + " Harvesters size=" + harvesters.length
                + " stunDiscoveryFailed=" + stunDiscoveryFailed);
    }

    /**
     * Prunes a list of mapping harvesters, removing the ones without valid
     * addresses and those with duplicate addresses.
     *
     * @param harvesters the list of harvesters.
     * @return the pruned list.
     */
    private static List<MappingCandidateHarvester> prune(
            List<MappingCandidateHarvester> harvesters)
    {
        List<MappingCandidateHarvester> pruned = new LinkedList<>();
        for (MappingCandidateHarvester harvester : harvesters) {
            maybeAdd(harvester, pruned);
        }
        return pruned;
    }

    /**
     * Adds {@code harvester} to {@code harvesters}, if it has valid addresses
     * and {@code harvesters} doesn't already contain a harvester with the same
     * addresses.
     *
     * @param harvester the harvester to add.
     * @param harvesters the list to add to.
     */
    private static void maybeAdd(
            MappingCandidateHarvester harvester,
            List<MappingCandidateHarvester> harvesters)
    {
        TransportAddress face = harvester.getFace();
        TransportAddress mask = harvester.getMask();
        if (face == null || mask == null || face.equals(mask)) {
            logger.info("Discarding a mapping harvester: " + harvester);
            return;
        }

        for (MappingCandidateHarvester h : harvesters) {
            if (face.getAddress().equals(h.getFace().getAddress())
                    && mask.getAddress().equals(h.getMask().getAddress())) {
                logger.info("Discarding a mapping harvester with duplicate addresses: " + harvester + ". Kept: " + h);
                return;
            }
        }
        harvesters.add(harvester);
    }

    /**
     * Creates STUN mapping harvesters for each of the given STUN servers, and
     * waits for address discovery to finish for all of them.
     *
     * @param stunServers an array of STUN server addresses (ip_address:port pairs).
     * @return the list of those who were successful in discovering an address.
     */
    private static List<StunMappingCandidateHarvester> createStunHarvesters(@NotNull List<String> stunServers)
    {
        List<StunMappingCandidateHarvester> stunHarvesters = new LinkedList<>();

        List<Callable<StunMappingCandidateHarvester>> tasks = new LinkedList<>();

        // Create a StunMappingCandidateHarvester for each local:remote address
        // pair.
        List<InetAddress> localAddresses
                = HostCandidateHarvester.getAllAllowedAddresses();
        for (String stunServer : stunServers) {
            String[] addressAndPort = stunServer.split(":");
            if (addressAndPort.length < 2) {
                logger.severe("Failed to parse STUN server address: "
                        + stunServer);
                continue;
            }
            int port;
            try {
                port = Integer.parseInt(addressAndPort[1]);
            } catch (NumberFormatException nfe) {
                logger.severe("Invalid STUN server port: " + addressAndPort[1]);
                continue;
            }

            TransportAddress remoteAddress
                    = new TransportAddress(
                    addressAndPort[0],
                    port,
                    Transport.UDP);

            for (InetAddress localInetAddress : localAddresses) {
                if (localInetAddress instanceof Inet6Address) {
                    // This is disabled, because it is broken for an unknown
                    // reason and it is not currently needed.
                    continue;
                }

                TransportAddress localAddress
                        = new TransportAddress(localInetAddress, 0, Transport.UDP);

                logger.info("Using " + remoteAddress + " for StunMappingCandidateHarvester (localAddress="
                        + localAddress + ").");
                final StunMappingCandidateHarvester stunHarvester
                        = new StunMappingCandidateHarvester(
                        localAddress,
                        remoteAddress);

                Callable<StunMappingCandidateHarvester> task = () ->
                {
                    stunHarvester.discover();
                    return stunHarvester;
                };
                tasks.add(task);
            }
        }

        // Now run discover() on all created harvesters in parallel and pick
        // the ones which succeeded.
        ExecutorService es = ExecutorFactory.createFixedThreadPool(tasks.size(), "ice4j.Harvester-executor-");

        try {
            List<Future<StunMappingCandidateHarvester>> futures;
            try {
                futures = es.invokeAll(tasks);
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
                return stunHarvesters;
            }

            for (Future<StunMappingCandidateHarvester> future : futures) {
                try {
                    StunMappingCandidateHarvester harvester = future.get();

                    // The STUN server replied successfully.
                    if (harvester.getMask() != null) {
                        stunHarvesters.add(harvester);
                    }
                } catch (ExecutionException ee) {
                    // The harvester failed for some reason, discard it.
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    throw new RuntimeException(ie);
                }
            }
        } finally {
            es.shutdown();
        }
        return stunHarvesters;
    }

    /**
     * Prevent instance creation.
     */
    private MappingCandidateHarvesters()
    {
    }
}
