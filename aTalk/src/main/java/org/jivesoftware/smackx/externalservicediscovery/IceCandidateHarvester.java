/**
 * Copyright 2017-2022 Eng Chong Meng
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
package org.jivesoftware.smackx.externalservicediscovery;

import static net.java.sip.communicator.impl.netaddr.NetworkAddressManagerServiceImpl.STUN_SRV_NAME;
import static net.java.sip.communicator.impl.netaddr.NetworkAddressManagerServiceImpl.TURN_SRV_NAME;

import android.text.TextUtils;

import org.ice4j.Transport;
import org.ice4j.TransportAddress;
import org.ice4j.ice.harvest.StunCandidateHarvester;
import org.ice4j.ice.harvest.TurnCandidateHarvester;
import org.ice4j.security.LongTermCredential;
import org.jivesoftware.smack.XMPPConnection;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * An implementation that extracts the services found in external service discovery
 * and convert all services to a list of StunCandidateHarvester's access via IceUdpTransportManager.
 *
 * @author Eng Chong Meng
 * @see <a href="https://xmpp.org/extensions/xep-0215.html">XEP-0215: External Service Discovery</a>
 */
public class IceCandidateHarvester
{
    public static List<StunCandidateHarvester> getExtServiceHarvester(XMPPConnection connection, String protocol)
    {
        List<StunCandidateHarvester> stunServices = null;

        ExternalServiceDiscoveryManager xServiceManager = ExternalServiceDiscoveryManager.getInstanceFor(connection);
        if (xServiceManager.hasExtService()) {
            List<ServiceElement> services = xServiceManager.getTransportServices(protocol);
            if (services != null && !services.isEmpty()) {
                stunServices = new ArrayList<>();
                for (ServiceElement service : services) {
                    String host = service.getHost();
                    int port = service.getPort();
                    String transport = service.getTransport();

                    if (TextUtils.isEmpty(host) || TextUtils.isEmpty(transport) || port < 0) {
                        continue;
                    }

                    // IllegalArgumentException
                    TransportAddress transportAddress = new TransportAddress(host, port, Transport.parse(transport));
                    if (TURN_SRV_NAME.equals(service.getType())) {
                        byte[] userName = service.getUserName().getBytes(StandardCharsets.UTF_8);
                        byte[] password = service.getPassword().getBytes(StandardCharsets.UTF_8);
                        stunServices.add(new TurnCandidateHarvester(transportAddress, new LongTermCredential(userName, password)));
                    }
                    else if (STUN_SRV_NAME.equals(service.getType())) {
                        stunServices.add(new StunCandidateHarvester(transportAddress));
                    }
                }
            }
        }
        return stunServices;
    }
}
