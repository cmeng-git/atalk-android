/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.atalk.impl.neomedia.device;

import org.atalk.impl.neomedia.jmfext.media.protocol.audiosilence.DataSource;

import java.util.ArrayList;
import java.util.List;

import javax.media.MediaLocator;

/**
 * Audio system used by server side technologies that do not capture any sound. The device is
 * producing silence.
 *
 * @author Pawel Domas
 */
public class AudioSilenceSystem extends AudioSystem
{
    private static final String LOCATOR_PROTOCOL = LOCATOR_PROTOCOL_AUDIOSILENCE;

    protected AudioSilenceSystem()
            throws Exception
    {
        super(LOCATOR_PROTOCOL_AUDIOSILENCE);
    }

    @Override
    protected void doInitialize()
            throws Exception
    {
        List<CaptureDeviceInfo2> captureDevices = new ArrayList<>(2);

        captureDevices.add(new CaptureDeviceInfo2(
                "AudioSilenceCaptureDevice",
                new MediaLocator(LOCATOR_PROTOCOL + ":"),
                DataSource.SUPPORTED_FORMATS,
                null, null, null));

        // The following is a dummy audio capture device which does not even
        // produce silence. It is suitable for scenarios in which an audio
        // capture device is required but no audio samples from it are necessary
        // such as negotiating signalling for audio but actually RTP translating
        // other participants/peers' audio.
        captureDevices.add(new CaptureDeviceInfo2(
                "AudioSilenceCaptureDevice:" + DataSource.NO_TRANSFER_DATA,
                new MediaLocator(LOCATOR_PROTOCOL + ":" + DataSource.NO_TRANSFER_DATA),
                DataSource.SUPPORTED_FORMATS,
                null, null, null));

        setCaptureDevices(captureDevices);
    }
}
