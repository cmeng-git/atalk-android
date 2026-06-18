/*
 *
 * Copyright 2020 Paul Schaub
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
package org.jivesoftware.smackx.message_retraction;

import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

import org.jivesoftware.smack.Manager;
import org.jivesoftware.smack.SmackException;
import org.jivesoftware.smack.XMPPConnection;
import org.jivesoftware.smack.packet.MessageBuilder;
import org.jivesoftware.smack.packet.Presence;
import org.jivesoftware.smack.roster.Roster;

import org.jivesoftware.smackx.caps.EntityCapsManager;
import org.jivesoftware.smackx.disco.ServiceDiscoveryManager;
import org.jivesoftware.smackx.disco.packet.DiscoverInfo;
import org.jivesoftware.smackx.fallback_indication.element.FallbackIndicationElement;
import org.jivesoftware.smackx.hints.element.StoreHint;
import org.jivesoftware.smackx.message_fastening.element.FasteningElement;
import org.jivesoftware.smackx.message_retraction.element.RetractElement;
import org.jivesoftware.smackx.receipts.DeliveryReceiptRequest;
import org.jivesoftware.smackx.sid.element.OriginIdElement;

import org.jxmpp.jid.EntityBareJid;
import org.jxmpp.jid.Jid;

/**
 * API for XEP-0424: Message Retraction 0.4.2 (2025-01-18).
 * <p>
 * To retract a message, call {@link #retractMessage(EntityBareJid, String, MessageBuilder)}  retractMessage(Jid, String, String, List)}, recipient,
 * id of the message to be retracted, and List of extension to be included in the message
 */
public final class MessageRetractionManager extends Manager {

    private static final Map<XMPPConnection, MessageRetractionManager> INSTANCES = new WeakHashMap<>();

    private MessageRetractionManager(XMPPConnection connection) {
        super(connection);
        ServiceDiscoveryManager.getInstanceFor(connection()).addFeature(RetractElement.NAMESPACE);
    }

    public static synchronized MessageRetractionManager getInstanceFor(XMPPConnection connection) {
        MessageRetractionManager manager = INSTANCES.get(connection);
        if (manager == null) {
            manager = new MessageRetractionManager(connection);
            INSTANCES.put(connection, manager);
        }
        return manager;
    }

    /**
     * Check the the specified jid supports Message Retraction for all online presence.
     * Note: Offline will always return false.
     *
     * @param jid – an XMPP ID, e.g. jdoe@example.com.
     *
     * @return true if the specified buddy jid supports Message Retraction.
     */
    public boolean contactSupportsMessageRetraction(Jid jid) {
        Roster roster = Roster.getInstanceFor(connection());
        List<Presence> presences = roster.getAvailablePresences(jid.asBareJid());
        for (Presence presence : presences) {
            DiscoverInfo featureInfo = EntityCapsManager.getDiscoverInfoByUser(presence.getFrom());
            if (featureInfo != null && featureInfo.containsFeature(RetractElement.NAMESPACE)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Retract a message by appending a {@link RetractElement} wrapped inside a {@link FasteningElement} which contains
     * the {@link OriginIdElement Origin-ID} of the message that will be retracted to a new message and send it to the
     * server.
     *
     * @param recipient recipient of the retract message
     * @param id Original Id of the message that the user wants to retract
     * @param messageBuilder Message build to add more xmlExtensions
     *
     * @throws SmackException.NotConnectedException in case the connection is not connected.
     * @throws InterruptedException if the thread gets interrupted.
     */
    public void retractMessage(EntityBareJid recipient, String id, MessageBuilder messageBuilder)
            throws SmackException.NotConnectedException, InterruptedException {

        FallbackIndicationElement fbElement = new FallbackIndicationElement(RetractElement.NAMESPACE);
        fbElement.setBody(RetractElement.retractHint);

        messageBuilder
                .to(recipient)
                .addExtension(new RetractElement(id))
                .addExtension(fbElement)
                .addExtension(StoreHint.INSTANCE)
                .addExtension(new DeliveryReceiptRequest());

        connection().sendStanza(messageBuilder.build());
    }
}
