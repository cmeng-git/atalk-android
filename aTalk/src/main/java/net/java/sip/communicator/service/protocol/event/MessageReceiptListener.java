/*
 * aTalk, android VoIP and Instant Messaging client
 * Copyright 2014~2024 Eng Chong Meng
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
package net.java.sip.communicator.service.protocol.event;

import org.jivesoftware.smack.packet.Stanza;
import org.jxmpp.jid.Jid;

public interface MessageReceiptListener
{
    /**
     * Callback invoked when a new receipt got received.
     *
     * @param fromJid the jid that send this receipt
     * @param toJid the jid which received this receipt
     * @param receiptId the message ID of the stanza which has been received.
     * @param receipt the receipt
     */
    void receiptReceived(Jid fromJid, Jid toJid, String receiptId, Stanza receipt);
}
