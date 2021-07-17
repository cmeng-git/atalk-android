package net.java.sip.communicator.service.protocol;

import net.java.sip.communicator.service.protocol.event.ChatRoomConferencePublishedEvent;
import net.java.sip.communicator.service.protocol.event.ChatRoomConferencePublishedListener;

import org.jxmpp.jid.parts.Resourcepart;

import java.util.*;

/**
 * An abstract class with a default implementation of some of the methods of the <tt>ChatRoom</tt> interface.
 *
 * @author Boris Grozev
 * @author Eng Chong Meng
 */
public abstract class AbstractChatRoom implements ChatRoom
{
    /**
     * The list of listeners to be notified when a member of the chat room publishes a <tt>ConferenceDescription</tt>
     */
    protected final List<ChatRoomConferencePublishedListener> conferencePublishedListeners = new ArrayList<>();

    /**
     * The list of all <tt>ConferenceDescription</tt> that were announced and are not yet processed.
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
     * Returns cached <tt>ConferenceDescription</tt> instances.
     * @return the cached <tt>ConferenceDescription</tt> instances.
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
     * Returns the number of cached <tt>ConferenceDescription</tt> instances.
     * @return the number of cached <tt>ConferenceDescription</tt> instances.
     */
    public synchronized int getCachedConferenceDescriptionSize()
    {
        return cachedConferenceDescriptions.size();
    }

    /**
     * Creates the corresponding <tt>ChatRoomConferencePublishedEvent</tt> and
     * notifies all <tt>ChatRoomConferencePublishedListener</tt>s that
     * <tt>member</tt> has published a conference description.
     *
     * @param member the <tt>ChatRoomMember</tt> that published <tt>cd</tt>.
     * @param cd the <tt>ConferenceDescription</tt> that was published.
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
     * Processes the <tt>ConferenceDescription</tt> instance and adds/removes it to the list of conferences.
     *
     * @param cd the <tt>ConferenceDescription</tt> instance to process.
     * @param participantNick the name of the participant that sent the <tt>ConferenceDescription</tt>.
     * @return <tt>true</tt> on success and <tt>false</tt> if fail.
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
