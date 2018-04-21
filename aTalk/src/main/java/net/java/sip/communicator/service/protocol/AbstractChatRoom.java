package net.java.sip.communicator.service.protocol;

import net.java.sip.communicator.service.protocol.event.ChatRoomConferencePublishedEvent;
import net.java.sip.communicator.service.protocol.event.ChatRoomConferencePublishedListener;
import net.java.sip.communicator.util.Logger;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

public abstract class AbstractChatRoom implements ChatRoom
{
    private static final Logger logger = Logger.getLogger(AbstractChatRoom.class);
    protected final List<ChatRoomConferencePublishedListener> conferencePublishedListeners = new ArrayList<>();
    protected Map<String, ConferenceDescription> cachedConferenceDescriptions = new HashMap();

    public void addConferencePublishedListener(ChatRoomConferencePublishedListener listener)
    {
        synchronized (this.conferencePublishedListeners) {
            this.conferencePublishedListeners.add(listener);
        }
    }

    public void removeConferencePublishedListener(ChatRoomConferencePublishedListener listener)
    {
        synchronized (this.conferencePublishedListeners) {
            this.conferencePublishedListeners.remove(listener);
        }
    }

    public Map<String, ConferenceDescription> getCachedConferenceDescriptions()
    {
        Map<String, ConferenceDescription> tmpCachedConferenceDescriptions;
        synchronized (this.cachedConferenceDescriptions) {
            tmpCachedConferenceDescriptions = new HashMap(this.cachedConferenceDescriptions);
        }
        return tmpCachedConferenceDescriptions;
    }

    public synchronized int getCachedConferenceDescriptionSize()
    {
        return this.cachedConferenceDescriptions.size();
    }

    protected void fireConferencePublishedEvent(ChatRoomMember member, ConferenceDescription cd, int eventType)
    {
        ChatRoomConferencePublishedEvent evt = new ChatRoomConferencePublishedEvent(eventType, this, member, cd);
        List<ChatRoomConferencePublishedListener> listeners;
        synchronized (this.conferencePublishedListeners) {
            listeners = new LinkedList(this.conferencePublishedListeners);
        }
        for (ChatRoomConferencePublishedListener listener : listeners) {
            listener.conferencePublished(evt);
        }
    }

    protected boolean processConferenceDescription(ConferenceDescription cd, String participantName)
    {
        if (cd.isAvailable()) {
            if (this.cachedConferenceDescriptions.containsKey(participantName)) {
                return false;
            }
            this.cachedConferenceDescriptions.put(participantName, cd);
        }
        else {
            ConferenceDescription cachedDescription = this.cachedConferenceDescriptions.get(participantName);
            if ((cachedDescription == null) || (!cd.compareConferenceDescription(cachedDescription))) {
                return false;
            }
            this.cachedConferenceDescriptions.remove(participantName);
        }
        return true;
    }

    protected void clearCachedConferenceDescriptionList()
    {
        this.cachedConferenceDescriptions.clear();
    }
}
