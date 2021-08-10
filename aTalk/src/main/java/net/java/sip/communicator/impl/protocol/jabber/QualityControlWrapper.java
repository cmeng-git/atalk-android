/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package net.java.sip.communicator.impl.protocol.jabber;

import net.java.sip.communicator.service.protocol.media.AbstractQualityControlWrapper;

import org.atalk.service.neomedia.QualityControl;
import org.atalk.service.neomedia.QualityPreset;
import org.jivesoftware.smack.SmackException;

import timber.log.Timber;

/**
 * A wrapper of media quality control.
 *
 * @author Damian Minkov
 * @author Sebastien Vincent
 * @author Eng Chong Meng
 */
public class QualityControlWrapper extends AbstractQualityControlWrapper<CallPeerJabberImpl>
{
    /**
     * Creates quality control for peer.
     *
     * @param peer peer
     */
    QualityControlWrapper(CallPeerJabberImpl peer)
    {
        super(peer);
    }

    /**
     * Changes the current video settings for the peer with the desired quality settings and inform
     * the peer to stream the video with those settings.
     *
     * @param preset the desired video settings
     */
    @Override
    public void setPreferredRemoteSendMaxPreset(QualityPreset preset)
    {
        QualityControl qControls = getMediaQualityControl();

        if (qControls != null) {
            qControls.setRemoteSendMaxPreset(preset);

            // re-invites the peer with the new settings
            try {
                peer.sendModifyVideoResolutionContent();
            } catch (SmackException.NotConnectedException | InterruptedException e) {
                Timber.e(e, "Could not send modify video resolution of peer");
            }
        }
    }
}
