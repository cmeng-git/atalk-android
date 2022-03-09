/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.atalk.impl.neomedia.transform.csrc;

import net.sf.fmj.media.rtp.RTPHeader;

import org.atalk.impl.neomedia.AudioMediaStreamImpl;
import org.atalk.impl.neomedia.MediaStreamImpl;
import org.atalk.impl.neomedia.transform.PacketTransformer;
import org.atalk.impl.neomedia.transform.SinglePacketTransformerAdapter;
import org.atalk.impl.neomedia.transform.TransformEngine;
import org.atalk.service.configuration.ConfigurationService;
import org.atalk.service.libjitsi.LibJitsi;
import org.atalk.service.neomedia.MediaDirection;
import org.atalk.service.neomedia.RTPExtension;
import org.atalk.service.neomedia.RawPacket;

import java.util.Map;

import javax.media.Buffer;

/**
 * Implements read-only support for &quot;A Real-Time Transport Protocol (RTP) Header Extension for
 * Client-to-Mixer Audio Level Indication&quot;. Optionally, drops RTP packets indicated to be
 * generated from a muted audio source in order to avoid wasting processing power such as
 * decrypting, decoding and audio mixing.
 *
 * @author Emil Ivov
 * @author Lyubomir Marinov
 * @author Eng Chong Meng
 */
public class SsrcTransformEngine extends SinglePacketTransformerAdapter implements TransformEngine
{
    /**
     * The name of the <code>ConfigurationService</code> property which specifies whether
     * <code>SsrcTransformEngine</code> is to drop RTP packets indicated as generated from a muted
     * audio source in {@link #reverseTransform(RawPacket)}.
     */
    public static final String DROP_MUTED_AUDIO_SOURCE_IN_REVERSE_TRANSFORM
            = SsrcTransformEngine.class.getName() + ".dropMutedAudioSourceInReverseTransform";

    /**
     * The indicator which determines whether <code>SsrcTransformEngine</code> is to drop RTP packets
     * indicated as generated from a muted audio source in {@link #reverseTransform(RawPacket)}.
     */
    private static boolean dropMutedAudioSourceInReverseTransform = false;

    /**
     * The indicator which determines whether the method
     * {@link #readConfigurationServicePropertiesOnce()} is to read the values of certain
     * <code>ConfigurationService</code> properties of concern to <code>SsrcTransformEngine</code> once
     * during the initialization of the first instance.
     */
    private static boolean readConfigurationServicePropertiesOnce = true;

    /**
     * The dispatcher that is delivering audio levels to the media steam.
     */
    private final CsrcAudioLevelDispatcher csrcAudioLevelDispatcher;

    /**
     * The <code>MediaDirection</code> in which this RTP header extension is active.
     */
    private MediaDirection ssrcAudioLevelDirection = MediaDirection.INACTIVE;

    /**
     * The negotiated ID of this RTP header extension.
     */
    private byte ssrcAudioLevelExtID = -1;

    /**
     * Initializes a new <code>SsrcTransformEngine</code> to be utilized by a specific <code>MediaStreamImpl</code>.
     *
     * @param mediaStream the <code>MediaStreamImpl</code> to utilize the new instance
     */
    public SsrcTransformEngine(MediaStreamImpl mediaStream)
    {
        /*
         * Take into account that RTPExtension.SSRC_AUDIO_LEVEL_URN may have already been activated.
         */
        Map<Byte, RTPExtension> activeRTPExtensions = mediaStream.getActiveRTPExtensions();

        if ((activeRTPExtensions != null) && !activeRTPExtensions.isEmpty()) {
            for (Map.Entry<Byte, RTPExtension> e : activeRTPExtensions.entrySet()) {
                RTPExtension rtpExtension = e.getValue();
                String uri = rtpExtension.getURI().toString();

                if (RTPExtension.SSRC_AUDIO_LEVEL_URN.equals(uri)) {
                    Byte extID = e.getKey();
                    setSsrcAudioLevelExtensionID((extID == null) ? -1 : extID, rtpExtension.getDirection());
                }
            }
        }

        readConfigurationServicePropertiesOnce();

        // Audio levels are received in RTP audio streams only.
        if (mediaStream instanceof AudioMediaStreamImpl) {
            csrcAudioLevelDispatcher = new CsrcAudioLevelDispatcher((AudioMediaStreamImpl) mediaStream);
        }
        else {
            csrcAudioLevelDispatcher = null;
        }
    }

    /**
     * Closes this <code>PacketTransformer</code> i.e. releases the resources allocated by it and
     * prepares it for garbage collection.
     */
    @Override
    public void close()
    {
        if (csrcAudioLevelDispatcher != null)
            csrcAudioLevelDispatcher.close();
    }

    /**
     * Always returns <code>null</code> since this engine does not require any RTCP transformations.
     *
     * @return <code>null</code> since this engine does not require any RTCP transformations.
     */
    @Override
    public PacketTransformer getRTCPTransformer()
    {
        return null;
    }

    /**
     * Returns a reference to this class since it is performing RTP transformations in here.
     *
     * @return a reference to <code>this</code> instance of the <code>SsrcTransformEngine</code>.
     */
    @Override
    public PacketTransformer getRTPTransformer()
    {
        return this;
    }

    /**
     * Reads the values of certain <code>ConfigurationService</code> properties of concern to
     * <code>SsrcTransformEngine</code> once during the initialization of the first instance.
     */
    private static synchronized void readConfigurationServicePropertiesOnce()
    {
        if (readConfigurationServicePropertiesOnce)
            readConfigurationServicePropertiesOnce = false;
        else
            return;

        ConfigurationService cfg = LibJitsi.getConfigurationService();

        if (cfg != null) {
            dropMutedAudioSourceInReverseTransform = cfg.getBoolean(
                    SsrcTransformEngine.DROP_MUTED_AUDIO_SOURCE_IN_REVERSE_TRANSFORM,
                    dropMutedAudioSourceInReverseTransform);
        }
    }

    /**
     * Extracts the list of CSRC identifiers and passes it to the <code>MediaStream</code> associated
     * with this engine. Other than that the method does not do any transformations since CSRC
     * lists are part of RFC 3550 and they shouldn't be disrupting the rest of the application.
     *
     * @param pkt the RTP <code>RawPacket</code> that we are to extract a SSRC list from.
     * @return the same <code>RawPacket</code> that was received as a parameter since we don't need to
     * worry about hiding the SSRC list from the rest of the RTP stack.
     */
    @Override
    public RawPacket reverseTransform(RawPacket pkt)
    {
        boolean dropPkt = false;

        if ((ssrcAudioLevelExtID > 0) && ssrcAudioLevelDirection.allowsReceiving()
                && !pkt.isInvalid() && RTPHeader.VERSION == pkt.getVersion()) {
            byte level = pkt.extractSsrcAudioLevel(ssrcAudioLevelExtID);

            if (level == 127 /* a muted audio source */) {
                if (dropMutedAudioSourceInReverseTransform) {
                    dropPkt = true;
                }
                else {
                    pkt.setFlags(Buffer.FLAG_SILENCE | pkt.getFlags());
                }
            }

            /*
             * Notify the AudioMediaStream associated with this instance about the received audio level.
             */
            if (!dropPkt && (csrcAudioLevelDispatcher != null) && (level >= 0)) {
                long[] levels = new long[2];

                levels[0] = pkt.getSSRCAsLong();
                levels[1] = 127 - level;
                csrcAudioLevelDispatcher.addLevels(levels, pkt.getTimestamp());
            }
        }
        if (dropPkt) {
            pkt.setFlags(Buffer.FLAG_DISCARD | pkt.getFlags());
        }
        return pkt;
    }

    public void setSsrcAudioLevelExtensionID(byte extID, MediaDirection dir)
    {
        this.ssrcAudioLevelExtID = extID;
        this.ssrcAudioLevelDirection = dir;
    }
}
