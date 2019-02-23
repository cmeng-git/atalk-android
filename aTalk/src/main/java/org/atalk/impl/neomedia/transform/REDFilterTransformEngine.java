/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.atalk.impl.neomedia.transform;

import org.atalk.impl.neomedia.RTPPacketPredicate;
import org.atalk.impl.neomedia.codec.REDBlock;
import org.atalk.impl.neomedia.codec.REDBlockIterator;
import org.atalk.service.neomedia.RawPacket;

import timber.log.Timber;

/**
 * Removes the RED encapsulation (RFC2198) from outgoing packets, dropping non-primary (redundancy)
 * packets.
 *
 * @author George Politis
 * @author Eng Chong Meng
 */
public class REDFilterTransformEngine extends SinglePacketTransformerAdapter
        implements TransformEngine
{
    /**
     * A boolean flag determining whether or not this transformer should strip RED from outgoing
     * packets.
     */
    private boolean enabled = false;

    /**
     * The RED payload type. This, of course, should be dynamic but in the context of this
     * transformer this doesn't matter.
     */
    private final byte redPayloadType;

    /**
     * Initializes a new <tt>REDFilterTransformEngine</tt> with the given payload type number for
     * RED.
     *
     * @param redPayloadType the RED payload type number.
     */
    public REDFilterTransformEngine(byte redPayloadType)
    {
        super(RTPPacketPredicate.INSTANCE);
        this.redPayloadType = redPayloadType;
    }

    /**
     * Enables or disables this transformer.
     *
     * @param enabled
     */
    public void setEnabled(boolean enabled)
    {
        this.enabled = enabled;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public PacketTransformer getRTPTransformer()
    {
        return this;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public PacketTransformer getRTCPTransformer()
    {
        return null;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public RawPacket transform(RawPacket pkt)
    {
        // XXX: this method is heavily inspired by the
        // {@link REDTransformEngine#reverseTransformSingle} method only
        // here we do the transformation in the opposite direction, i.e.
        // we transform outgoing packets instead of incoming ones.
        //
        // This transform engine has bee added to enable support for FF
        // that, at the time of this writing, lacks support for
        // ulpfec/red. Thus its purpose is to strip ulpfec/red from the
        // outgoing packets that target FF.
        //
        // We could theoretically strip ulpfec/red from all the incoming
        // packets (in a reverseTransform method) and selectively re-add
        // it later-on (using the RED and FEC transform engines) only to
        // those outgoing streams that have announced ulpfec/red support
        // (currently those are the streams that target Chrome).
        //
        // But, given that the best supported client is Chrome and thus
        // assuming that most participants will use Chrome to connect to
        // a JVB-based service, it seems a waste of resources to do
        // that. It's more efficient to strip ulpfec/red only from the
        // outgoing streams that target FF.

        if (!enabled || redPayloadType == -1 || pkt == null
                || pkt.getPayloadType() != redPayloadType) {
            return pkt;
        }

        REDBlock pb = REDBlockIterator.getPrimaryBlock(pkt.getBuffer(), pkt.getPayloadOffset(),
                pkt.getPayloadLength());

        if (pb == null) {
            Timber.w("Ignoring RED packet with no primary block.");
            return pkt;
        }

        byte[] buf = pkt.getBuffer();
        int hdrLen = pkt.getHeaderLength();
        int off = pkt.getOffset();
        int len = pkt.getLength();

        // Shift the RTP header right.
        pkt.setPayloadType(pb.getPayloadType());
        System.arraycopy(buf, off, buf, pb.getOffset() - hdrLen, hdrLen);
        pkt.setOffset(pb.getOffset() - hdrLen);
        pkt.setLength(len - (pb.getOffset() - hdrLen - off));

        return pkt;
    }
}
