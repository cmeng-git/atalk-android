/*
 * Copyright @ 2017 Atlassian Pty Ltd
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

package org.atalk.impl.neomedia.rtcp;

import org.atalk.impl.neomedia.*;
import org.atalk.impl.neomedia.transform.*;
import org.atalk.service.neomedia.*;
import org.atalk.util.ByteArrayBuffer;

/**
 * Provide RTCP termination facilities for audio
 *
 * @author Brian Baldino
 */
public class AudioRTCPTermination implements TransformEngine
{
    RTCPTransformer rtcpTransformer = new RTCPTransformer();

    /**
     * {@inheritDoc}
     */
    @Override
    public PacketTransformer getRTPTransformer()
    {
        return null;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public PacketTransformer getRTCPTransformer()
    {
        return rtcpTransformer;
    }

    class RTCPTransformer extends SinglePacketTransformerAdapter
    {
        /**
         * Ctor.
         */
        RTCPTransformer()
        {
            super(RTCPPacketPredicate.INSTANCE);
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public RawPacket transform(RawPacket pkt)
        {
            RTCPIterator it = new RTCPIterator(pkt);
            while (it.hasNext())
            {
                ByteArrayBuffer baf = it.next();
                // We want to terminate all REMB packets
                if (RTCPREMBPacket.isREMBPacket(baf))
                {
                    it.remove();
                }
            }
            return pkt.getLength() == 0 ? null : pkt;
        }
    }
}
