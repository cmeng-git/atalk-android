/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package net.java.sip.communicator.plugin.notificationwiring;

import org.atalk.service.resources.ResourceManagementService;

/**
 * Manages the access to the properties file containing all sounds paths.
 *
 * @author Yana Stamcheva
 * @author Eng Chong Meng
 */
public final class SoundProperties
{
    /**
     * The incoming message sound id.
     */
    public static final String INCOMING_MESSAGE;

    /**
     * The incoming file sound id.
     */
    public static final String INCOMING_FILE;

    /**
     * The incoming file sound id.
     */
    public static final String INCOMING_INVITATION;

    /**
     * The outgoing call sound id.
     */
    public static final String OUTGOING_CALL;

    /**
     * The incoming call sound id.
     */
    public static final String INCOMING_CALL;

    /**
     * The busy sound id.
     */
    public static final String BUSY;

    /**
     * The dialing sound id.
     */
    public static final String DIALING;

    /**
     * The sound id of the sound played when call security is turned on.
     */
    public static final String CALL_SECURITY_ON;

    /**
     * The sound id of the sound played when a call security error occurs.
     */
    public static final String CALL_SECURITY_ERROR;

    /**
     * The hang up sound id.
     */
    public static final String HANG_UP;

    /*
     * Call NotificationActivator.getResources() once because
     * (1) it's not a trivial getter, it caches the reference so it always checks whether
     * the cache has already been built and
     * (2) accessing a local variable is supposed to be faster than calling a method
     * (even if the method is a trivial getter and it's inlined at runtime, it's still
     * supposed to be slower because it will be accessing a field, not a local variable).
     */
    static {
        ResourceManagementService resources = NotificationWiringActivator.getResources();

        INCOMING_FILE = resources.getSoundPath("INCOMING_FILE");
        INCOMING_INVITATION = resources.getSoundPath("INCOMING_INVITATION");
        INCOMING_MESSAGE = resources.getSoundPath("INCOMING_MESSAGE");
        INCOMING_CALL = resources.getSoundPath("INCOMING_CALL");
        OUTGOING_CALL = resources.getSoundPath("OUTGOING_CALL");
        BUSY = resources.getSoundPath("BUSY");
        DIALING = resources.getSoundPath("DIAL");
        HANG_UP = resources.getSoundPath("HANG_UP");
        CALL_SECURITY_ON = resources.getSoundPath("CALL_SECURITY_ON");
        CALL_SECURITY_ERROR = resources.getSoundPath("CALL_SECURITY_ERROR");
    }

    private SoundProperties()
    {
    }

    /**
     * Get the aTalk default sound descriptor - for ringtone user default selection
     * @param eventType sound event type
     * @return the default aTalk sound descriptor
     */
    public static String getSoundDescriptor(String eventType)
    {
        switch (eventType) {
            case NotificationManager.INCOMING_FILE:
                return INCOMING_FILE;
            case NotificationManager.INCOMING_INVITATION:
                return INCOMING_INVITATION;
            case NotificationManager.INCOMING_MESSAGE:
                return INCOMING_MESSAGE;
            case NotificationManager.INCOMING_CALL:
                return INCOMING_CALL;
            case NotificationManager.OUTGOING_CALL:
                return OUTGOING_CALL;
            case NotificationManager.BUSY_CALL:
                return BUSY;
            case NotificationManager.DIALING:
                return DIALING;
            case NotificationManager.HANG_UP:
                return HANG_UP;
            case NotificationManager.CALL_SECURITY_ON:
                return CALL_SECURITY_ON;
            case NotificationManager.CALL_SECURITY_ERROR:
                return CALL_SECURITY_ERROR;
            default:
                return null;
        }
    }
}
