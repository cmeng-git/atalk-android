/*
 * Copyright @ 2015 Atlassian Pty Ltd
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */

package org.atalk.impl.neomedia.jmfext.media.protocol.rtpdumpfile;

import org.atalk.impl.neomedia.device.AudioMediaDeviceImpl;
import org.atalk.impl.neomedia.device.AudioMediaDeviceSession;
import org.atalk.impl.neomedia.device.MediaDeviceImpl;
import org.atalk.impl.neomedia.device.MediaDeviceSession;
import org.atalk.service.neomedia.device.MediaDevice;
import org.atalk.service.neomedia.format.MediaFormat;
import org.atalk.util.MediaType;

import javax.media.CaptureDeviceInfo;
import javax.media.Format;
import javax.media.MediaLocator;
import javax.media.Processor;
import javax.media.format.AudioFormat;
import javax.media.format.VideoFormat;

/**
 * This class contains the method <code>createRtpdumpMediaDevice</code> that can create
 * <code>MediaDevice</code>s that will read the rtpdump file given. This static method is here for
 * convenience.
 *
 * @author Thomas Kuntz
 */
public class RtpdumpMediaDevice
{
    /**
     * Create a new video <code>MediaDevice</code> instance which will read the rtpdump file located at
     * <code>filePath</code>, and which will have the encoding format <code>encodingConstant</code>.
     *
     * @param filePath the location of the rtpdump file
     * @param rtpEncodingConstant the format this <code>MediaDevice</code> will have. You can find the
     * list of possible format in the class <code>Constants</code> of libjitsi (ex : Constants.VP8_RTP).
     * @param format the <code>MediaFormat</code> of the data contained in the payload of the recorded rtp
     * packet in the rtpdump file.
     * @return a <code>MediaDevice</code> that will read the rtpdump file given.
     */
    public static MediaDevice createRtpdumpVideoMediaDevice(String filePath,
            String rtpEncodingConstant, MediaFormat format)
    {
        /*
         * NOTE: The RtpdumpStream instance needs to know the RTP clock rate, to correctly
         * interpret the RTP timestamps. We use the frameRate field of VideoFormat, to piggyback
         * the RTP  clock rate. See RtpdumpStream#RtpdumpStream().
         * TODO: Avoid this hack...
         */
        return new MediaDeviceImpl(new CaptureDeviceInfo("Video rtpdump file",
                new MediaLocator("rtpdumpfile:" + filePath),
                new Format[]{new VideoFormat(
                        rtpEncodingConstant, /* Encoding */
                        null, /* Dimension */
                        Format.NOT_SPECIFIED, /* maxDataLength */
                        Format.byteArray, /* dataType */
                        (float) format.getClockRate()) /* frameRate */
                }),
                MediaType.VIDEO);
    }

    /**
     * Create a new audio <code>MediaDevice</code> instance which will read the rtpdump file located at
     * <code>filePath</code>, and which will have the encoding format <code>format</code>.
     * <p>
     * Note: for proper function, <code>format</code> has to implement correctly the
     * <code>computeDuration(long)</code> method, because FMJ insists on using this to compute its own RTP timestamps.
     * <p>
     * Note: The RtpdumpStream instance needs to know the RTP clock rate to correctly interpret the
     * RTP timestamps. We use the sampleRate field of AudioFormat, or the frameRate field of
     * VideoFormat, to piggyback the RTP clock rate. See
     * {@link RtpdumpStream#RtpdumpStream(DataSource, javax.media.control.FormatControl)} TODO: Avoid this hack...
     *
     * @param filePath the location of the rtpdump file
     * @param format the <code>AudioFormat</code> of the data contained in the payload of the recorded rtp
     * packet in the rtpdump file.
     * @return a <code>MediaDevice</code> that will read the rtpdump file given.
     */
    public static MediaDevice createRtpdumpAudioMediaDevice(String filePath, AudioFormat format)
    {
        return new MyAudioMediaDeviceImpl(new CaptureDeviceInfo("Audio rtpdump file",
                new MediaLocator("rtpdumpfile:" + filePath), new Format[]{format}));
    }

    /**
     * An implementation of <code>AudioMediaDevice</code>.
     */
    private static class MyAudioMediaDeviceImpl extends AudioMediaDeviceImpl
    {
        /**
         * Initializes a new <code>MyAudioMediaDeviceImpl</code>.
         *
         * @param captureDeviceInfo
         */
        private MyAudioMediaDeviceImpl(CaptureDeviceInfo captureDeviceInfo)
        {
            super(captureDeviceInfo);
        }

        /**
         * {@inheritDoc}
         * <p>
         * Makes sure that the <code>MediaDeviceSession</code> created by this <code>AudioMediaDevice</code>
         * does not try to register an <code>AudioLevelEffect</code>, because this causes media to be
         * re-encoded (as <code>AudioLevelEffect</code> only works with raw audio formats).
         */
        @Override
        public MediaDeviceSession createSession()
        {
            return new AudioMediaDeviceSession(MyAudioMediaDeviceImpl.this)
            {
                @Override
                protected void registerLocalUserAudioLevelEffect(Processor processor)
                {
                }
            };
        }
    }
}
