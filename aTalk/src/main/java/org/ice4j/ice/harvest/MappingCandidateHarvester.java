/*
 * ice4j, the OpenSource Java Solution for NAT and Firewall Traversal.
 *
 * Copyright @ 2015 Atlassian Pty Ltd
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

import androidx.annotation.NonNull;

import org.ice4j.TransportAddress;
import org.ice4j.ice.Candidate;
import org.ice4j.ice.CandidateExtendedType;
import org.ice4j.ice.Component;
import org.ice4j.ice.HostCandidate;
import org.ice4j.ice.LocalCandidate;
import org.ice4j.ice.ServerReflexiveCandidate;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.logging.Logger;

import kotlin.jvm.internal.DefaultConstructorMarker;
import kotlin.jvm.internal.Intrinsics;

/**
 * Uses a list of addresses as a predefined static mask in order to generate
 * {@link TransportAddress}es. This harvester is meant for use in situations
 * where servers are deployed behind a NAT or in a DMZ with static port mapping.
 * <p>
 * Every time the {@link #harvest(Component)} method is called, the mapping
 * harvester will return a list of candidates that provide masked alternatives
 * for every host candidate in the component. Kind of like a STUN server.
 * <p>
 * Example: You run this on a server with address 192.168.0.1, that is behind
 * a NAT with public IP: 93.184.216.119. You allocate a host candidate
 * 192.168.0.1/UDP/5000. This harvester is going to then generate an address
 * 93.184.216.119/UDP/5000
 * <p>
 * This harvester is instant and does not introduce any harvesting latency.
 *
 * @author Emil Ivov
 * @author Eng Chong Meng
 */
public abstract class MappingCandidateHarvester extends AbstractCandidateHarvester
{
    /**
     * The <tt>Logger</tt> used by the <tt>StunCandidateHarvester</tt> class and its instances for logging output.
     */
    private static final Logger logger = Logger.getLogger(StunCandidateHarvester.class.getName());

    /**
     * Returns the local (face) address, or null.
     * The addresses that we will be masking
     */
    @Nullable
    public abstract TransportAddress getFace();

    /**
     * Returns the public (mask) address, or null.
     * The addresses that we will use as a mask
     */
    @Nullable
    public abstract TransportAddress getMask();

    private final String name;

    /**
     * Whether this harvester should match the port of the public address.
     *
     * When [matchPort] is enabled, mapping candidates will be added only when the local host candidate's address and
     * port match the public (mask) address, and the public (mask) address's port will be used.
     *
     * When [matchPort] is disabled, mapping candidates will be added whenever the local host candidate's inet address
     * matches the public (mask) address, and the host candidate port will be preserved.
     */
    private final boolean matchPort;

    public MappingCandidateHarvester(@NotNull String name, boolean matchPort)
    {
        super();
        Intrinsics.checkNotNullParameter(name, "name");
        this.name = name;
        this.matchPort = matchPort;
    }

    public MappingCandidateHarvester(String name, boolean matchPort, int var3, DefaultConstructorMarker dcMarker)
    {
        this(name, matchPort && (var3 & 2) == 0);
    }

    /**
     * Initializes a {@link MappingCandidateHarvester} instance with name but without
     * specified addresses (only useful in subclasses which override {@link #getMask()} and {@link #getFace()}).
     */
    public MappingCandidateHarvester(@NotNull String name)
    {
        this(name, false, 2, null);
    }

    /**
     * Checks whether the given [address] matches the public address of this harvester.
     * only compares the inet address (since by default the port is not matched in [harvest]),
     * but other implementations may chose to also compare the port.
     */
    public boolean publicAddressMatches(TransportAddress address)
    {
        Intrinsics.checkNotNullParameter(address, "address");
        TransportAddress mask = this.getMask();

        return (mask != null) && mask.getAddress() == address.getAddress()
                && (!matchPort || mask.getPort() == address.getPort());
    }

    /**
     * Maps all candidates to this harvester's mask and adds them to <tt>component</tt>.
     *
     * @param component the {@link Component} that we'd like to map candidates to.
     * @return the <tt>LocalCandidate</tt>s gathered by this
     * <tt>CandidateHarvester</tt> or <tt>null</tt> if no mask is specified.
     */
    @Override
    public Collection<LocalCandidate> harvest(Component component)
    {
        Intrinsics.checkNotNullParameter(component, "component");

        TransportAddress mask = getMask();
        TransportAddress face = getFace();
        if (face == null || mask == null) {
            logger.warning("Harvester not configured: face=" + face + ", mask=" + mask);
            return Collections.emptyList();
        }

        // Report the LocalCandidates gathered by this CandidateHarvester so
        // that the harvest is sure to be considered successful.
        Collection<LocalCandidate> candidates = new HashSet<>();

        for (Candidate<?> cand : component.getLocalCandidates()) {
            if (!(cand instanceof HostCandidate)
                    || !cand.getTransportAddress().getHostAddress().equals(face.getHostAddress())
                    || cand.getTransport() != face.getTransport()) {
                continue;
            }

            HostCandidate hostCandidate = (HostCandidate) cand;
            TransportAddress mappedAddress = new TransportAddress(
                    mask.getHostAddress(),
                    hostCandidate.getHostAddress().getPort(),
                    hostCandidate.getHostAddress().getTransport());

            ServerReflexiveCandidate mappedCandidate = new ServerReflexiveCandidate(
                    mappedAddress,
                    hostCandidate,
                    hostCandidate.getStunServerAddress(),
                    CandidateExtendedType.STATICALLY_MAPPED_CANDIDATE);
            if (hostCandidate.isSSL())
                mappedCandidate.setSSL(true);

            //try to add the candidate to the component and then
            //only add it to the harvest not redundant
            if (!candidates.contains(mappedCandidate)
                    && component.addLocalCandidate(mappedCandidate)) {
                candidates.add(mappedCandidate);
            }
        }

        return candidates;
    }

    @NotNull
    public final String getName()
    {
        return this.name;
    }

    public final boolean getMatchPort()
    {
        return this.matchPort;
    }

    /**
     * {@inheritDoc}
     */
    @NonNull
    @Override
    public String toString()
    {
        TransportAddress face = getFace();
        TransportAddress mask = getMask();
        return this.getClass().getName()
                + ", face=" + (face == null ? "null" : face.getAddress())
                + ", mask=" + (mask == null ? "null" : mask.getAddress());
    }
}
