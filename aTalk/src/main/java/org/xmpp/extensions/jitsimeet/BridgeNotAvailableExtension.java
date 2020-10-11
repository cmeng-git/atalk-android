/*
 * Copyright @ 2018 - present 8x8, Inc.
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
package org.xmpp.extensions.jitsimeet;


import org.xmpp.extensions.AbstractExtensionElement;

/**
 * Packet extension sent in focus MUC presence to notify users about the fact
 * that there are currently no JVBs available either because all of them has
 * failed to allocate channels or none were ever available.
 *
 * @author Pawel Domas
 * @author Eng Chong Meng
 */
public class BridgeNotAvailableExtension extends AbstractExtensionElement
{
    public final static String ELEMENT = "bridgeNotAvailable";

    public final static String NAMESPACE = ConferenceIq.NAMESPACE;

    public BridgeNotAvailableExtension()
    {
        super(ELEMENT, NAMESPACE);
    }
}
