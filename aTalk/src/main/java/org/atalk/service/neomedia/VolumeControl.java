/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.atalk.service.neomedia;

import org.atalk.service.neomedia.event.VolumeChangeListener;

/**
 * Control for volume level in (neo)media service.
 *
 * @author Damian Minkov
 * @author Eng Chong Meng
 */
public interface VolumeControl
{
    /**
     * The name of the configuration property which specifies the volume level of audio input.
     */
    String CAPTURE_VOLUME_LEVEL_PROPERTY_NAME = "media.CAPTURE_VOLUME_LEVEL";

    /**
     * The name of the configuration property which specifies the volume level of audio output.
     */
    String PLAYBACK_VOLUME_LEVEL_PROPERTY_NAME = "media.PLAYBACK_VOLUME_LEVEL";

    /**
     * Adds a <code>VolumeChangeListener</code> to be informed about changes in the volume level of this instance.
     *
     * @param listener the <code>VolumeChangeListener</code> to be informed about changes in the volume level of
     * this instance
     */
    void addVolumeChangeListener(VolumeChangeListener listener);

    /**
     * Returns the maximum allowed volume value/level.
     *
     * @return the maximum allowed volume value/level
     */
    float getMaxValue();

    /**
     * Returns the minimum allowed volume value/level.
     *
     * @return the minimum allowed volume value/level
     */
    float getMinValue();

    /**
     * Get mute state of sound playback.
     *
     * @return mute state of sound playback.
     */
    boolean getMute();

    /**
     * Gets the current volume value/level.
     *
     * @return the current volume value/level
     */
    float getVolume();

    /**
     * Removes a <code>VolumeChangeListener</code> to no longer be notified about changes in the volume
     * level of this instance.
     *
     * @param listener the <code>VolumeChangeListener</code> to no longer be notified about changes in the volume
     * level of this instance
     */
    void removeVolumeChangeListener(VolumeChangeListener listener);

    /**
     * Mutes current sound playback.
     *
     * @param mute mutes/unmutes playback.
     */
    void setMute(boolean mute);

    /**
     * Sets the current volume value/level.
     *
     * @param value the volume value/level to set on this instance
     * @return the actual/current volume value/level set on this instance
     */
    float setVolume(float value);
}
