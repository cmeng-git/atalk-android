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
package org.jivesoftware.smackx.jinglemessage;

import org.jivesoftware.smack.XMPPConnection;
import org.jivesoftware.smack.packet.Message;
import org.jivesoftware.smackx.jinglemessage.packet.JingleMessage;

/**
 * Interface for listening to jingle Message events.
 * XEP-0353: Jingle Message Initiation 0.3 (2017-09-11)
 *
 * @author Eng Chong Meng
 */
public interface JingleMessageListener {
    /**
     * Caller propose an media call
     */
    void onJingleMessagePropose(XMPPConnection connection, JingleMessage jingleMessage, Message message);

    /**
     * Caller has retract the call.
     */
    void onJingleMessageRetract(XMPPConnection connection, JingleMessage jingleMessage, Message message);

    /**
     * Call has accepted the call; broadcast message by server to other callee resources
     */
    void onJingleMessageAccept(XMPPConnection connection, JingleMessage jingleMessage, Message message);

    /**
     * Called accepted and request to proceed.
     */
    void onJingleMessageProceed(XMPPConnection connection, JingleMessage jingleMessage, Message message);

    /**
     * callee has rejected the call.
     */
    void onJingleMessageReject(XMPPConnection connection, JingleMessage jingleMessage, Message message);
}
