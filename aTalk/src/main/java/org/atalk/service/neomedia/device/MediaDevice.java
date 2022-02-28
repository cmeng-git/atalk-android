/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.atalk.service.neomedia.device;

import org.atalk.service.neomedia.MediaDirection;
import org.atalk.service.neomedia.QualityPreset;
import org.atalk.service.neomedia.RTPExtension;
import org.atalk.service.neomedia.codec.EncodingConfiguration;
import org.atalk.service.neomedia.format.MediaFormat;
import org.atalk.util.MediaType;

import java.util.List;

/**
 * The <code>MediaDevice</code> class represents capture and playback devices that can be used to grab
 * or render media. Sound cards, USB phones and webcams are examples of such media devices.
 *
 * @author Emil Ivov
 * @author Eng Chong Meng
 */
public interface MediaDevice
{
    /**
     * Returns the <code>MediaDirection</code> supported by this device.
     *
     * @return <code>MediaDirection.SENDONLY</code> if this is a read-only device,
     * <code>MediaDirection.RECVONLY</code> if this is a write-only device and
     * <code>MediaDirection.SENDRECV</code> if this <code>MediaDevice</code> can both capture and render media.
     */
    MediaDirection getDirection();

    /**
     * Returns the <code>MediaFormat</code> that this device is currently set to use when capturing data.
     *
     * @return the <code>MediaFormat</code> that this device is currently set to provide media in.
     */
    MediaFormat getFormat();

    /**
     * Returns the <code>MediaType</code> that this device supports.
     *
     * @return <code>MediaType.AUDIO</code> if this is an audio device or <code>MediaType.VIDEO</code> in
     * case of a video device.
     */
    MediaType getMediaType();

    /**
     * Returns the <code>List</code> of <code>RTPExtension</code>s that this device know how to handle.
     *
     * @return the <code>List</code> of <code>RTPExtension</code>s that this device know how to handle or
     * <code>null</code> if the device does not support any RTP extensions.
     */
    List<RTPExtension> getSupportedExtensions();

    /**
     * Returns a list of <code>MediaFormat</code> instances representing the media formats supported by
     * this <code>MediaDevice</code>.
     *
     * @return the list of <code>MediaFormat</code>s supported by this device.
     */
    List<MediaFormat> getSupportedFormats();

    /**
     * Returns a list of <code>MediaFormat</code> instances representing the media formats supported by
     * this <code>MediaDevice</code>.
     *
     * @param localPreset the preset used to set the send format parameters, used for video and settings.
     * @param remotePreset the preset used to set the receive format parameters, used for video and settings.
     * @return the list of <code>MediaFormat</code>s supported by this device.
     */
    List<MediaFormat> getSupportedFormats(QualityPreset localPreset, QualityPreset remotePreset);

    /**
     * Returns a list of <code>MediaFormat</code> instances representing the media formats supported by
     * this <code>MediaDevice</code> and enabled in <code>encodingConfiguration</code>.
     *
     * @param localPreset the preset used to set the send format parameters, used for video and settings.
     * @param remotePreset the preset used to set the receive format parameters, used for video and settings.
     * @param encodingConfiguration the <code>EncodingConfiguration<code> instance
     * to use.
     * @return the list of <code>MediaFormat</code>s supported by this device.
     */
    List<MediaFormat> getSupportedFormats(QualityPreset localPreset,
            QualityPreset remotePreset, EncodingConfiguration encodingConfiguration);
}
