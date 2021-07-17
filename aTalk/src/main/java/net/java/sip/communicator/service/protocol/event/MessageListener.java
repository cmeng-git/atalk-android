/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */
package net.java.sip.communicator.service.protocol.event;

import java.util.EventListener;

/**
 * A listener that would gather events notifying of message delivery status. IMessage received
 *
 * @author Emil Ivov
 * @author Eng Chong Meng
 */
public interface MessageListener extends EventListener
{
    /**
     * Called when a new incoming <tt>IMessage</tt> has been received.
     *
     * @param evt the <tt>MessageReceivedEvent</tt> containing the newly received message, its sender
     * and other details.
     */
    void messageReceived(MessageReceivedEvent evt);

    /**
     * Called when the underlying implementation has received an indication that a message, sent
     * earlier has been successfully received by the destination.
     *
     * @param evt the MessageDeliveredEvent containing the id of the message that has caused the event.
     */
    void messageDelivered(MessageDeliveredEvent evt);

    /**
     * Called to indicated that delivery of a message sent earlier has failed. Reason code and
     * phrase are contained by the <tt>MessageFailedEvent</tt>
     *
     * @param evt the <tt>MessageFailedEvent</tt> containing the ID of the message whose delivery has failed.
     */
    void messageDeliveryFailed(MessageDeliveryFailedEvent evt);
}
