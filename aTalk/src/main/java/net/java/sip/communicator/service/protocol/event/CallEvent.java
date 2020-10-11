/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package net.java.sip.communicator.service.protocol.event;

import net.java.sip.communicator.service.protocol.Call;
import net.java.sip.communicator.service.protocol.CallConference;

import org.atalk.service.neomedia.MediaDirection;
import org.atalk.util.MediaType;

import java.util.*;

/**
 * An event class representing that an incoming, or an outgoing call has been created.
 * The event id indicates the exact reason for this event.
 *
 * @author Emil Ivov
 * @author Eng Chong Meng
 */
public class CallEvent extends EventObject
{
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
     * Serial version UID.
     */
    private static final long serialVersionUID = 0L;

    /**
     * Determines whether this event has been fired to indicate an incoming or an outgoing call.
     */
    private final int eventID;

    /**
     * The media types supported by this call, if information is available.
     */
    private final Map<MediaType, MediaDirection> mediaDirections;

    /**
     * The conference of the call for this event. Must be set when creating this event. This is because when
     * a call ends, the call conference may be released just after creating this event, but its
     * reference will still be necessary in the future for the UI (i.e to release the call panel),
     */
    private final CallConference conference;

    /**
     * Indicate whether the call is recognized to be video call and desktop streaming call.
     */
    private boolean isDesktopStreaming = false;

    /**
     * Initializes a new <tt>CallEvent</tt> instance which is to represent an event fired by a
     * specific <tt>Call</tt> as its source.
     *
     * @param call the call that triggered this event.
     * @param eventID determines whether the new instance represents an event notifying that:
     * a. an outgoing <tt>Call</tt> was initiated, or
     * b. an incoming <tt>Call</tt> was received, or
     * c. a <tt>Call</tt> has ended
     * @param mediaDirections Media Direction.
     */
    public CallEvent(Call call, int eventID, Map<MediaType, MediaDirection> mediaDirections)
    {
        super(call);
        this.eventID = eventID;

        Map<MediaType, MediaDirection> thisMediaDirections = new HashMap<>();
        if (mediaDirections != null)
            thisMediaDirections.putAll(mediaDirections);

        this.mediaDirections = Collections.unmodifiableMap(thisMediaDirections);
        this.conference = call.getConference();
    }

    /**
     * Returns an event ID indicates the event was triggered by an outgoing or an incoming call.
     *
     * @return one of the CALL_XXX static member ints.
     */
    public int getEventID()
    {
        return this.eventID;
    }

    /**
     * Return the media directions map
     *
     * @return the supported media direction map of current call.
     */
    public Map<MediaType, MediaDirection> getMediaDirections()
    {
        return mediaDirections;
    }

    /**
     * Return the media types supported by this call, if information is available. It can be empty
     * list if information wasn't provided for this event and call.
     *
     * @return the supported media types of current call.
     */
    public List<MediaType> getMediaTypes()
    {
        return new ArrayList<>(mediaDirections.keySet());
    }

    /**
     * Returns the <tt>Call</tt> that triggered this event.
     *
     * @return the <tt>Call</tt> that triggered this event.
     */
    public Call getSourceCall()
    {
        return (Call) getSource();
    }

    /**
     * Returns the <tt>CallConference</tt> that triggered this event.
     *
     * @return the <tt>CallConference</tt> that triggered this event.
     */
    public CallConference getCallConference()
    {
        return this.conference;
    }

    /**
     * Returns true if the call a video call.
     *
     * @return true if the call is a video call, false otherwise
     */
    public boolean isVideoCall()
    {
        MediaDirection direction = mediaDirections.get(MediaType.VIDEO);
        return (MediaDirection.SENDRECV == direction);
    }

    /**
     * Returns whether the current event is for video call and desktop streaming one.
     *
     * @return true if this is video call and desktop streaming one.
     */
    public boolean isDesktopStreaming()
    {
        return isDesktopStreaming;
    }

    /**
     * Change the desktop streaming indication for this event.
     *
     * @param value the new value.
     */
    public void setDesktopStreaming(boolean value)
    {
        this.isDesktopStreaming = value;
    }

    /**
     * Returns a String representation of this CallEvent.
     *
     * @return A a String representation of this CallEvent.
     */
    @Override
    public String toString()
    {
        return "CallEvent:[id=" + getEventID() + " Call=" + getSourceCall() + "]";
    }
}
