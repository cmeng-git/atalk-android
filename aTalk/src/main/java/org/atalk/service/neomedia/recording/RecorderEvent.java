/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.atalk.service.neomedia.recording;

import org.atalk.util.MediaType;
import org.json.JSONObject;

import androidx.annotation.NonNull;

/**
 * Represents an event related to media recording, such as a new SSRC starting to be recorded.
 *
 * @author Boris Grozev
 * @author Vladimir Marinov
 * @author Eng Chong Meng
 */
public class RecorderEvent
{
    /**
     * The type of this <tt>RecorderEvent</tt>.
     */
    private Type type = Type.OTHER;

    /**
     * A timestamp for this <tt>RecorderEvent</tt>.
     */
    private long instant = -1;

    /**
     * The SSRC associated with this <tt>RecorderEvent</tt>.
     */
    private long ssrc = -1;

    /**
     * The SSRC of an audio stream associated with this <tt>RecorderEvent</tt>.
     */
    private long audioSsrc = -1;

    /**
     * An RTP timestamp for this <tt>RecorderEvent</tt>.
     */
    private long rtpTimestamp = -1;

    /**
     * An NTP timestamp (represented as a double in seconds) for this <tt>RecorderEvent</tt>.
     */
    private double ntpTime = -1.0;

    /**
     * Duration associated with this <tt>RecorderEvent</tt>.
     */
    private long duration = -1;

    /**
     * An aspect ratio associated with this <tt>RecorderEvent</tt>.
     */
    private AspectRatio aspectRatio = AspectRatio.ASPECT_RATIO_UNKNOWN;

    /**
     * A file name associated with this <tt>RecorderEvent</tt>.
     */
    private String filename;

    /**
     * The media type associated with this <tt>RecorderEvent</tt>.
     */
    private MediaType mediaType = null;

    /**
     * The name of the participant associated with this <tt>RecorderEvent</tt>.
     */
    private String participantName = null;

    /**
     * A textual description of the participant associated with this <tt>RecorderEvent</tt>. (human readable)
     */
    private String participantDescription = null;

    private String endpointId = null;

    private boolean disableOtherVideosOnTop = false;

    /**
     * Constructs a <tt>RecorderEvent</tt>.
     */
    public RecorderEvent()
    {
    }

    /**
     * Constructs a <tt>RecorderEvent</tt> and tries to parse its fields from <tt>json</tt>.
     *
     * @param json a JSON object, containing fields with which to initialize the fields of this <tt>RecorderEvent</tt>.
     */
    public RecorderEvent(JSONObject json)
    {
        type = Type.parseString(json.optString("type"));

        instant = json.optLong("instant", -1);
        ssrc = json.optLong("ssrc", -1);
        audioSsrc = json.optLong("audioSsrc", -1);
        ntpTime = json.optLong("ntpTime", -1);
        duration = json.optLong("duration", -1);

        aspectRatio = AspectRatio.parseString(json.optString("aspectRatio"));

        filename = json.optString("filename", null);
        participantName = json.optString("participantName", null);
        participantDescription = json.optString("participantDescription", null);
        endpointId = json.optString("endpointId", null);

        mediaType = MediaType.parseString(json.optString("mediaType"));
        disableOtherVideosOnTop = json.optBoolean("disableOtherVideosOnTop");
    }

    public Type getType()
    {
        return type;
    }

    public void setType(Type type)
    {
        this.type = type;
    }

    public long getInstant()
    {
        return instant;
    }

    public void setInstant(long instant)
    {
        this.instant = instant;
    }

    public long getRtpTimestamp()
    {
        return rtpTimestamp;
    }

    public void setRtpTimestamp(long rtpTimestamp)
    {
        this.rtpTimestamp = rtpTimestamp;
    }

    public long getSsrc()
    {
        return ssrc;
    }

    public void setSsrc(long ssrc)
    {
        this.ssrc = ssrc;
    }

    public long getAudioSsrc()
    {
        return audioSsrc;
    }

    public void setAudioSsrc(long audioSsrc)
    {
        this.audioSsrc = audioSsrc;
    }

    public AspectRatio getAspectRatio()
    {
        return aspectRatio;
    }

    public void setAspectRatio(AspectRatio aspectRatio)
    {
        this.aspectRatio = aspectRatio;
    }

    public String getFilename()
    {
        return filename;
    }

    public void setFilename(String filename)
    {
        this.filename = filename;
    }

    public MediaType getMediaType()
    {
        return mediaType;
    }

    public void setMediaType(MediaType mediaType)
    {
        this.mediaType = mediaType;
    }

    public long getDuration()
    {
        return duration;
    }

    public void setDuration(long duration)
    {
        this.duration = duration;
    }

    public String getParticipantName()
    {
        return participantName;
    }

    public void setParticipantName(String participantName)
    {
        this.participantName = participantName;
    }

    public String getParticipantDescription()
    {
        return participantDescription;
    }

    public void setParticipantDescription(String participantDescription)
    {
        this.participantDescription = participantDescription;
    }

    public boolean getDisableOtherVideosOnTop()
    {
        return disableOtherVideosOnTop;
    }

    public void setDisableOtherVideosOnTop(boolean disableOtherVideosOnTop)
    {
        this.disableOtherVideosOnTop = disableOtherVideosOnTop;
    }

    public double getNtpTime()
    {
        return ntpTime;
    }

    public void setNtpTime(double ntpTime)
    {
        this.ntpTime = ntpTime;
    }

    public String toString()
    {
        return "RecorderEvent: " + getType().toString() + " @" + getInstant() + "(" + getMediaType() + ")";
    }

    public void setEndpointId(String endpointId)
    {
        this.endpointId = endpointId;
    }

    public String getEndpointId()
    {
        return endpointId;
    }

    /**
     * <tt>RecorderEvent</tt> types.
     */
    public enum Type
    {
        /**
         * Indicates the start of a recording.
         */
        RECORDING_STARTED("RECORDING_STARTED"),

        /**
         * Indicates the end of a recording.
         */
        RECORDING_ENDED("RECORDING_ENDED"),

        /**
         * Indicates that the active speaker has changed. The 'audioSsrc' field indicates the SSRC
         * of the audio stream which is now considered active, and the 'ssrc' field contains the
         * SSRC of a video stream associated with the now active audio stream.
         */
        SPEAKER_CHANGED("SPEAKER_CHANGED"),

        /**
         * Indicates that a new stream was added. This is different than RECORDING_STARTED, because
         * a new stream might be saved to an existing recording (for example, a new audio stream
         * might be added to a mix)
         */
        STREAM_ADDED("STREAM_ADDED"),

        /**
         * Default value.
         */
        OTHER("OTHER");

        private String name;

        private Type(String name)
        {
            this.name = name;
        }

        @NonNull
        public String toString()
        {
            return name;
        }

        public static Type parseString(String str)
        {
            for (Type type : Type.values())
                if (type.toString().equals(str))
                    return type;
            return OTHER;
        }
    }

    /**
     * Video aspect ratio.
     */
    public enum AspectRatio
    {
        ASPECT_RATIO_16_9("16_9", 16. / 9),
        ASPECT_RATIO_4_3("4_3", 4. / 3),
        ASPECT_RATIO_UNKNOWN("UNKNOWN", 1.);

        public double scaleFactor;
        private String stringValue;

        AspectRatio(String stringValue, double scaleFactor)
        {
            this.scaleFactor = scaleFactor;
            this.stringValue = stringValue;
        }

        @NonNull
        @Override
        public String toString()
        {
            return stringValue;
        }

        public static AspectRatio parseString(String str)
        {
            for (AspectRatio aspectRatio : AspectRatio.values())
                if (aspectRatio.toString().equals(str))
                    return aspectRatio;
            return ASPECT_RATIO_UNKNOWN;
        }
    }
}
