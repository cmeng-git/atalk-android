package net.java.sip.communicator.service.protocol;

import net.java.sip.communicator.service.protocol.event.ChatRoomConferencePublishedEvent;
import net.java.sip.communicator.service.protocol.event.ChatRoomConferencePublishedListener;

import org.jxmpp.jid.parts.Resourcepart;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

/**
 * An abstract class with a default implementation of some of the methods of the <code>ChatRoom</code> interface.
 *
 * @author Boris Grozev
 * @author Eng Chong Meng
 */
public abstract class AbstractChatRoom implements ChatRoom
{
    /**
     * The list of listeners to be notified when a member of the chat room publishes a <code>ConferenceDescription</code>
     */
    protected final List<ChatRoomConferencePublishedListener> conferencePublishedListeners = new ArrayList<>();

    /**
     * The list of all <code>ConferenceDescription</code> that were announced and are not yet processed.
     */
    protected final Map<Resourcepart, ConferenceDescription> cachedConferenceDescriptions = new HashMap();

    /**
     * {@inheritDoc}
     */
    public void addConferencePublishedListener(ChatRoomConferencePublishedListener listener)
    {
        synchronized (conferencePublishedListeners) {
            conferencePublishedListeners.add(listener);
        }
    }

    /**
     * {@inheritDoc}
     */
    public void removeConferencePublishedListener(ChatRoomConferencePublishedListener listener)
    {
        synchronized (conferencePublishedListeners) {
            conferencePublishedListeners.remove(listener);
        }
    }

    /**
     * Returns cached <code>ConferenceDescription</code> instances.
     * @return the cached <code>ConferenceDescription</code> instances.
     */
    public Map<String, ConferenceDescription> getCachedConferenceDescriptions()
    {
        Map<String, ConferenceDescription> tmpCachedConferenceDescriptions = new HashMap<>();
        synchronized (cachedConferenceDescriptions) {
            for (Map.Entry<Resourcepart, ConferenceDescription> entry : cachedConferenceDescriptions.entrySet()) {
                tmpCachedConferenceDescriptions.put(entry.getKey().toString(), entry.getValue());
            }
        }
        return tmpCachedConferenceDescriptions;
    }

    /**
     * Returns the number of cached <code>ConferenceDescription</code> instances.
     * @return the number of cached <code>ConferenceDescription</code> instances.
     */
    public synchronized int getCachedConferenceDescriptionSize()
    {
        return cachedConferenceDescriptions.size();
    }

    /**
     * Creates the corresponding <code>ChatRoomConferencePublishedEvent</code> and
     * notifies all <code>ChatRoomConferencePublishedListener</code>s that
     * <code>member</code> has published a conference description.
     *
     * @param member the <code>ChatRoomMember</code> that published <code>cd</code>.
     * @param cd the <code>ConferenceDescription</code> that was published.
     * @param eventType the type of the event.
     */
    protected void fireConferencePublishedEvent(ChatRoomMember member, ConferenceDescription cd, int eventType)
    {
        ChatRoomConferencePublishedEvent evt = new ChatRoomConferencePublishedEvent(eventType, this, member, cd);
        List<ChatRoomConferencePublishedListener> listeners;
        synchronized (conferencePublishedListeners) {
            listeners = new LinkedList<>(conferencePublishedListeners);
        }
        for (ChatRoomConferencePublishedListener listener : listeners) {
            listener.conferencePublished(evt);
        }
    }

    /**
     * Processes the <code>ConferenceDescription</code> instance and adds/removes it to the list of conferences.
     *
     * @param cd the <code>ConferenceDescription</code> instance to process.
     * @param participantNick the name of the participant that sent the <code>ConferenceDescription</code>.
     * @return <code>true</code> on success and <code>false</code> if fail.
     */
    protected boolean processConferenceDescription(ConferenceDescription cd, Resourcepart participantNick)
    {
        if (cd.isAvailable()) {
            if (cachedConferenceDescriptions.containsKey(participantNick)) {
                return false;
            }
            cachedConferenceDescriptions.put(participantNick, cd);
        }
        else {
            ConferenceDescription cachedDescription = cachedConferenceDescriptions.get(participantNick);
            if ((cachedDescription == null) || (!cd.compareConferenceDescription(cachedDescription))) {
                return false;
            }
            cachedConferenceDescriptions.remove(participantNick);
        }
        return true;
    }

    /**
     * Clears the list with the chat room conferences.
     */
    protected void clearCachedConferenceDescriptionList()
    {
        cachedConferenceDescriptions.clear();
    }
}
