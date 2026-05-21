/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package net.java.sip.communicator.service.protocol.event;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EventObject;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import net.java.sip.communicator.service.protocol.Call;

import org.atalk.service.neomedia.MediaDirection;
import org.atalk.util.MediaType;

/**
 * An event class representing that an incoming, or an outgoing call has been created.
 * The event id indicates the exact reason for this event.
 *
 * @author Emil Ivov
 * @author Eng Chong Meng
 */
public class CallEvent extends EventObject {
    /**
     * An event id value indicating that this event has been triggered as a result of an outgoing call.
     */
    public static final int CALL_INITIATED = 1;

    /**
     * An event id value indicating that this event has been triggered as a result of an incoming call.
     */
    public static final int CALL_RECEIVED = 2;

    /**
     * An event id value indicating that this event has been triggered as a result of a call ended (all its peers have left).
     */
    public static final int CALL_ENDED = 3;

    /**
     * An event id value indicating that this event has been triggered as a result of an incoming call via JM.
     */
    public static final int CALL_RECEIVED_JM = 10;

    /**
     * Serial version UID.
     */
    private static final long serialVersionUID = 0L;

    /**
     * Determines whether this event has been fired to indicate an incoming or an outgoing call.
     */
    private final int mEventId;

    /**
     * The media types supported by this call, if information is available.
     */
    private final Map<MediaType, MediaDirection> mMediaDirections;

    /**
     * Initializes a new <code>CallEvent</code> instance which is to represent an event fired by a
     * specific <code>Call</code> as its source.
     *
     * @param call the call that triggered this event.
     * @param eventId determines whether the new instance represents an event notifying that:
     * a. an outgoing <code>Call</code> was initiated, or
     * b. an incoming <code>Call</code> was received, or
     * c. a <code>Call</code> has ended
     * @param mediaDirections Media Direction.
     */
    public CallEvent(Call call, int eventId, Map<MediaType, MediaDirection> mediaDirections) {
        super(call);
        mEventId = eventId;

        Map<MediaType, MediaDirection> thisMediaDirections = new HashMap<>();
        if (mediaDirections != null)
            thisMediaDirections.putAll(mediaDirections);

        mMediaDirections = Collections.unmodifiableMap(thisMediaDirections);
    }

    /**
     * Returns an event ID indicates the event was triggered by an outgoing or an incoming call.
     *
     * @return one of the CALL_XXX static member ints.
     */
    public int getEventId() {
        return mEventId;
    }

    /**
     * Return the media directions map
     *
     * @return the supported media direction map of current call.
     */
    public Map<MediaType, MediaDirection> getMediaDirections() {
        return mMediaDirections;
    }

    /**
     * Return the media types supported by this call, if information is available. It can be empty
     * list if information wasn't provided for this event and call.
     *
     * @return the supported media types of current call.
     */
    public List<MediaType> getMediaTypes() {
        return new ArrayList<>(mMediaDirections.keySet());
    }

    /**
     * Returns the <code>Call</code> that triggered this event.
     *
     * @return the <code>Call</code> that triggered this event.
     */
    public Call getSourceCall() {
        return (Call) getSource();
    }

    /**
     * Returns true if the call a video call.
     *
     * @return true if the call is a video call, false otherwise
     */
    public boolean isVideoCall() {
        MediaDirection direction = mMediaDirections.get(MediaType.VIDEO);
        return (MediaDirection.SENDRECV == direction);
    }

    /**
     * Returns a String representation of this CallEvent.
     *
     * @return A a String representation of this CallEvent.
     */
    @Override
    public String toString() {
        return "CallEvent:[id=" + getEventId() + " Call=" + getSourceCall() + "]";
    }
}
