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
package net.java.sip.communicator.service.contactsource;

import net.java.sip.communicator.service.protocol.OperationSet;
import net.java.sip.communicator.service.protocol.ProtocolProviderService;

/**
 * The <code>ProtocolAwareContactSourceService</code> extends the basic
 * <code>ContactSourceService</code> interface to provide a protocol aware contact
 * source. In other words a preferred <code>ProtocolProviderService</code> can be
 * set for a given <code>OperationSet</code> class that would affect the query
 * result by excluding source contacts that has a preferred provider different
 * from the one specified as a preferred provider.
 *
 * @author Yana Stamcheva
 */
public interface ProtocolAwareContactSourceService
    extends ContactSourceService
{
    /**
     * Sets the preferred protocol provider for this contact source. The
     * preferred <code>ProtocolProviderService</code> set for a given
     * <code>OperationSet</code> class would affect the query result by excluding
     * source contacts that has a preferred provider different from the one
     * specified here.
     *
     * @param opSetClass the <code>OperationSet</code> class, for which the
     * preferred provider is set
     * @param protocolProvider the <code>ProtocolProviderService</code> to set
     */
    public void setPreferredProtocolProvider(
                                    Class<? extends OperationSet> opSetClass,
                                    ProtocolProviderService protocolProvider);
}
