/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.atalk.impl.neomedia.codec.audio.alaw;

import javax.media.Codec;
import javax.media.Control;
import javax.media.Format;
import javax.media.ResourceUnavailableException;
import javax.media.format.AudioFormat;

import org.atalk.service.neomedia.codec.Constants;

/**
 * Implements an RTP packetizer for the A-law codec.
 *
 * @author Damian Minkov
 * @author Lyubomir Marinov
 */
public class Packetizer extends com.ibm.media.codec.audio.AudioPacketizer {
    /**
     * Initializes a new <code>Packetizer</code> instance.
     */
    public Packetizer() {
        defaultOutputFormats = new AudioFormat[]{new AudioFormat(Constants.ALAW_RTP,
                Format.NOT_SPECIFIED, 8, 1, Format.NOT_SPECIFIED, Format.NOT_SPECIFIED, 8,
                Format.NOT_SPECIFIED, Format.byteArray)};
        packetSize = 160;
        PLUGIN_NAME = "A-law Packetizer";
        supportedInputFormats = new AudioFormat[]{new AudioFormat(AudioFormat.ALAW,
                Format.NOT_SPECIFIED, 8, 1, Format.NOT_SPECIFIED, Format.NOT_SPECIFIED, 8,
                Format.NOT_SPECIFIED, Format.byteArray)};
    }

    @Override
    public Object[] getControls() {
        if (controls == null) {
            controls = new Control[]{new PacketSizeAdapter(this, packetSize, true)};
        }
        return controls;
    }

    @Override
    protected Format[] getMatchingOutputFormats(Format in) {
        AudioFormat af = (AudioFormat) in;
        double sampleRate = af.getSampleRate();

        supportedOutputFormats = new AudioFormat[]{new AudioFormat(Constants.ALAW_RTP,
                sampleRate, 8, 1, Format.NOT_SPECIFIED, Format.NOT_SPECIFIED, 8, sampleRate,
                Format.byteArray)};
        return supportedOutputFormats;
    }

    @Override
    public void open()
            throws ResourceUnavailableException {
        setPacketSize(packetSize);
        reset();
    }

    /**
     * Sets the packet size to be used by this <code>Packetizer</code>.
     *
     * @param newPacketSize the new packet size to be used by this <code>Packetizer</code>
     */
    private synchronized void setPacketSize(int newPacketSize) {
        packetSize = newPacketSize;

        sample_count = packetSize;

        if (history == null) {
            history = new byte[packetSize];
        }
        else if (packetSize > history.length) {
            byte[] newHistory = new byte[packetSize];

            System.arraycopy(history, 0, newHistory, 0, historyLength);
            history = newHistory;
        }
    }

    private static class PacketSizeAdapter extends com.sun.media.controls.PacketSizeAdapter {
        public PacketSizeAdapter(Codec owner, int packetSize, boolean settable) {
            super(owner, packetSize, settable);
        }

        @Override
        public int setPacketSize(int numBytes) {
            if (numBytes < 10)
                numBytes = 10;
            if (numBytes > 8000)
                numBytes = 8000;

            packetSize = numBytes;
            ((Packetizer) owner).setPacketSize(packetSize);
            return packetSize;
        }
    }
}
