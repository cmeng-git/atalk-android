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

import org.jivesoftware.smack.ConnectionCreationListener;
import org.jivesoftware.smack.Manager;
import org.jivesoftware.smack.SmackException;
import org.jivesoftware.smack.XMPPConnection;
import org.jivesoftware.smack.XMPPConnectionRegistry;
import org.jivesoftware.smack.packet.ExtensionElement;
import org.jivesoftware.smack.packet.Message;
import org.jivesoftware.smack.packet.MessageBuilder;
import org.jivesoftware.smack.packet.StanzaBuilder;

import org.jivesoftware.smackx.disco.ServiceDiscoveryManager;
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
 *
 * To enable / disable auto-announcing support for this feature, call
 * {@link #setEnabledByDefault(boolean)}. Auto-announcing is enabled by default.
 *
 * To retract a message, call {@link #retractMessage(Jid, String, String, List)}, recipient,
 * id of the message to be retracted, and List of extension to be included in the message
 */
public final class MessageRetractionManager extends Manager {

    private static final Map<XMPPConnection, MessageRetractionManager> INSTANCES = new WeakHashMap<>();

    private static boolean ENABLED_BY_DEFAULT = true;

    static {
        XMPPConnectionRegistry.addConnectionCreationListener(new ConnectionCreationListener() {
            @Override
            public void connectionCreated(XMPPConnection connection) {
                if (ENABLED_BY_DEFAULT) {
                    getInstanceFor(connection).announceSupport();
                }
            }
        });
    }

    private MessageRetractionManager(XMPPConnection connection) {
        super(connection);
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
     * Enable or disable auto-announcing support for Message Retraction.
     * Default is enabled.
     *
     * @param enabled enabled
     */
    public static synchronized void setEnabledByDefault(boolean enabled) {
        ENABLED_BY_DEFAULT = enabled;
    }

    /**
     * Announce support for Message Retraction to the server.
     *
     * @see <a href="https://xmpp.org/extensions/xep-0424.html#disco">XEP-0424: Message Retraction: §2. Discovering Support</a>
     */
    public void announceSupport() {
        ServiceDiscoveryManager.getInstanceFor(connection()).addFeature(RetractElement.NAMESPACE);
    }

    /**
     * Stop announcing support for Message Retraction.
     */
    public void stopAnnouncingSupport() {
        ServiceDiscoveryManager.getInstanceFor(connection()).removeFeature(RetractElement.NAMESPACE);
    }

    /**
     * Retract a message by appending a {@link RetractElement} wrapped inside a {@link FasteningElement} which contains
     * the {@link OriginIdElement Origin-ID} of the message that will be retracted to a new message and send it to the
     * server.
     *
     * @param recipient recipient of the retract message
     * @param id Original Id of the message that the user wants to retract
     * @param extExtensions List of ExtensionElement's to be included in the message
     *
     * @throws SmackException.NotConnectedException in case the connection is not connected.
     * @throws InterruptedException if the thread gets interrupted.
     */
    public void retractMessage(EntityBareJid recipient, String id, String thread, List<ExtensionElement> extExtensions)
            throws SmackException.NotConnectedException, InterruptedException {
        MessageBuilder messageBuilder = StanzaBuilder.buildMessage(id)
                .ofType(Message.Type.chat)
                .setThread(thread)
                .to(recipient);

        messageBuilder.addExtension(new RetractElement(id));
        if (extExtensions != null) {
            for (ExtensionElement xElement : extExtensions) {
                messageBuilder.addExtension(xElement);
            }
        }
        connection().sendStanza(messageBuilder.build());
    }

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
