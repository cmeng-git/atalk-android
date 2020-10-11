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

import org.jivesoftware.smack.*;
import org.jivesoftware.smack.chat2.Chat;
import org.jivesoftware.smack.chat2.ChatManager;
import org.jivesoftware.smack.filter.*;
import org.jivesoftware.smack.packet.*;
import org.jivesoftware.smackx.delay.packet.DelayInformation;
import org.jivesoftware.smackx.jinglemessage.packet.JingleMessage;
import org.jxmpp.jid.*;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArraySet;

/**
 * A chat manager for 1:1 XMPP instant messaging chats.
 * <p>
 * This manager and the according {@link Chat} API implement "Resource Locking" (XEP-0296). Support for Carbon Copies
 * (XEP-0280) will be added once the XEP has progressed from experimental.
 * </p>
 *
 * @see <a href="https://xmpp.org/extensions/xep-0296.html">XEP-0296: Best Practices for Resource Locking</a>
 */
public final class JingleMessageManager extends Manager
{
    private static final Map<XMPPConnection, JingleMessageManager> INSTANCES = new WeakHashMap<>();

    public static synchronized JingleMessageManager getInstanceFor(XMPPConnection connection)
    {
        JingleMessageManager jingleMessageManager = INSTANCES.get(connection);
        if (jingleMessageManager == null) {
            jingleMessageManager = new JingleMessageManager(connection);
            INSTANCES.put(connection, jingleMessageManager);
        }
        return jingleMessageManager;
    }

    /**
     * Message filter to listen for message sent from DomianJid i.e. server with normal or
     * has extensionElement i.e. XEP-0071: XHTML-IM
     */
    private static final StanzaFilter MESSAGE_FILTER = new AndFilter(
            MessageTypeFilter.NORMAL_OR_CHAT, new StanzaExtensionFilter(JingleMessage.NAMESPACE));

    private static final StanzaFilter INCOMING_JINGLE_MESSAGE_FILTER
            = new AndFilter(MESSAGE_FILTER, FromTypeFilter.ENTITY_FULL_JID);

    private final Map<EntityBareJid, Chat> chats = new ConcurrentHashMap<>();

    private final Set<JingleMessageListener> jingleMessageListeners = new CopyOnWriteArraySet<>();

    private final AsyncButOrdered<Chat> asyncButOrdered = new AsyncButOrdered<>();

    private JingleMessageManager(final XMPPConnection connection)
    {
        super(connection);
        connection.addSyncStanzaListener(new StanzaListener()
        {
            @Override
            public void processStanza(Stanza stanza)
            {
                // ignore any delayed Jingle Messages
                if (stanza.hasExtension(DelayInformation.ELEMENT, DelayInformation.NAMESPACE))
                    return;

                final Message message = (Message) stanza;
                ExtensionElement extElement = message.getExtension(JingleMessage.NAMESPACE);

                final JingleMessage jingleMessage = new JingleMessage((StandardExtensionElement) extElement);
                final Jid from = message.getFrom();
                final EntityFullJid fullFrom = from.asEntityFullJidOrThrow();
                final EntityBareJid bareFrom = fullFrom.asEntityBareJid();
                final Chat chat = chatWith(connection, bareFrom);

                asyncButOrdered.performAsyncButOrdered(chat, new Runnable()
                {
                    @Override
                    public void run()
                    {
                        for (JingleMessageListener listener : jingleMessageListeners) {
                            switch (jingleMessage.getAction()) {
                                case JingleMessage.ACTION_PROPOSE:
                                    listener.onJingleMessagePropose(connection, jingleMessage, message);
                                    break;

                                case JingleMessage.ACTION_RETRACT:
                                    listener.onJingleMessageRetract(connection, jingleMessage, message);
                                    break;

                                case JingleMessage.ACTION_ACCEPT:
                                    listener.onJingleMessageAccept(connection, jingleMessage, message);
                                    break;

                                case JingleMessage.ACTION_PROCEED:
                                    listener.onJingleMessageProceed(connection, jingleMessage, message);
                                    break;

                                case JingleMessage.ACTION_REJECT:
                                    listener.onJingleMessageReject(connection, jingleMessage, message);
                                    break;
                            }
                        }
                    }
                });

            }
        }, INCOMING_JINGLE_MESSAGE_FILTER);
    }

    /**
     * Add a new listener for incoming chat messages.
     *
     * @param listener the listener to add.
     * @return <code>true</code> if the listener was not already added.
     */
    public boolean addIncomingListener(JingleMessageListener listener)
    {
        return jingleMessageListeners.add(listener);
    }

    /**
     * Remove an incoming chat message listener.
     *
     * @param listener the listener to remove.
     * @return <code>true</code> if the listener was active and got removed.
     */
    public boolean removeIncomingListener(JingleMessageListener listener)
    {
        return jingleMessageListeners.remove(listener);
    }

    /**
     * Start a new or retrieve the existing chat with <code>jid</code>.
     *
     * @param jid the XMPP address of the other entity to chat with.
     * @return the Chat API for the given XMPP address.
     */
    public Chat chatWith(XMPPConnection connection, EntityBareJid jid)
    {
        Chat chat = chats.get(jid);
        if (chat == null) {
            synchronized (chats) {
                // Double-checked locking.
                chat = chats.get(jid);
                if (chat != null) {
                    return chat;
                }
                chat = ChatManager.getInstanceFor(connection).chatWith(jid);
                chats.put(jid, chat);
            }
        }
        return chat;
    }
}
