/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.atalk.impl.neomedia.transform;

import org.atalk.service.neomedia.RawPacket;
import org.atalk.util.ByteArrayBuffer;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import java.util.function.Predicate;

import timber.log.Timber;

/**
 * Extends the <code>PacketTransformer</code> interface with methods which allow the transformation of a
 * single packet into a single packet.
 * Eases the implementation of <code>PacketTransformer<code>-s which transform each
 * packet into a single transformed packet (as opposed to an array of possibly more than one packet).
 *
 * @author Boris Grozev
 * @author George Politis
 * @author Eng Chong Meng
 */
public abstract class SinglePacketTransformer implements PacketTransformer
{
    /**
     * The number of <code>Throwable</code>s to log with a single call to <code>logger</code>. If every
     * <code>Throwable</code> is logged in either of {@link #reverseTransform(RawPacket)} and
     * {@link #transform(RawPacket)}, the logging may be overwhelming.
     */
    private static final int EXCEPTIONS_TO_LOG = 1000;

    /**
     * The number of exceptions caught in {@link #reverseTransform(RawPacket)}.
     */
    private final AtomicInteger exceptionsInReverseTransform = new AtomicInteger();

    /**
     * The number of exceptions caught in {@link #transform(RawPacket)}.
     */
    private final AtomicInteger exceptionsInTransform = new AtomicInteger();

    /**
     * The idea is to have <code>PacketTransformer</code> implementations strictly associated with a
     * <code>Predicate</code> so that they only process packets that they're supposed to process. For
     * example, transformers that transform RTP packets should not transform RTCP packets, if, by
     * mistake, they happen to be passed RTCP packets.
     */
    private final Predicate<ByteArrayBuffer> packetPredicate;

    /*
     * A cached link to {@link #reverseTransform(RawPacket)} method to reduce calling overhead on hotpath.
     */
    private final Function<RawPacket, RawPacket> cachedReverseTransform = this::reverseTransform;

    /*
     * A cached link to {@link #transform(RawPacket)} method to reduce calling overhead on hotpath.
     */
    private final Function<RawPacket, RawPacket> cachedTransform = this::transform;

    /**
     * Ctor.
     * XXX At some point ideally we would get rid of this ctor and all the inheritors will use the
     * parametrized ctor. Also, we might want to move this check inside the <code>TransformEngineChain</code>
     * so that we only make the check once per packet: The RTCP transformer is only supposed only to (reverse)
     * transform RTCP packets and the RTP transformer is only supposed to modify RTP packets.
     */
    public SinglePacketTransformer()
    {
        this(null);
    }

    /**
     * Ctor.
     *
     * @param packetPredicate the <code>PacketPredicate</code> to use to match packets to (reverse) transform.
     */
    public SinglePacketTransformer(Predicate<ByteArrayBuffer> packetPredicate)
    {
        this.packetPredicate = packetPredicate;
    }

    /**
     * {@inheritDoc}
     *
     * The (default) implementation of {@code SinglePacketTransformer} does nothing.
     */
    @Override
    public void close()
    {
    }

    /**
     * Reverse-transforms a specific packet.
     *
     * @param pkt the transformed packet to be restored.
     * @return the reversed transformed packet.
     */
    public abstract RawPacket reverseTransform(RawPacket pkt);

    /**
     * {@inheritDoc}
     *
     * Reverse-transforms an array of packets by calling {@link #reverseTransform(RawPacket)} on each one.
     */
    @Override
    public RawPacket[] reverseTransform(RawPacket[] pkts)
    {
        return transformArray(pkts, cachedReverseTransform, exceptionsInReverseTransform, "reverseTransform");
    }

    /**
     * Transforms a specific packet.
     *
     * @param pkt the packet to be transformed.
     * @return the transformed packet.
     */
    public abstract RawPacket transform(RawPacket pkt);

    /**
     * {@inheritDoc}
     *
     * Transforms an array of packets by calling {@link #transform(RawPacket)} on each one.
     */
    @Override
    public RawPacket[] transform(RawPacket[] pkts)
    {
        return transformArray(pkts, cachedTransform, exceptionsInTransform, "transform");
    }

    /*
     * Applies a specific transformation function to an array of {@link RawPacket}s.
     *
     * @param pkts the array to transform.
     * @param transformFunction the function to apply to each (non-null) element of the array.
     * @param exceptionCounter a counter of the number of exceptions encountered.
     * @param logMessage a name of the transformation function, to be used when logging exceptions.
     * @return {@code pkts}.
     */
    private RawPacket[] transformArray(RawPacket[] pkts, Function<RawPacket, RawPacket> transformFunction,
            AtomicInteger exceptionCounter, String logMessage)
    {
        if (pkts != null) {
            for (int i = 0; i < pkts.length; i++) {
                RawPacket pkt = pkts[i];
                if (pkt != null
                        && ((packetPredicate == null) || (packetPredicate.test(pkt)))) {
                    try {
                        pkts[i] = transformFunction.apply(pkt);
                    } catch (Throwable t) {
                        exceptionCounter.incrementAndGet();
                        if ((exceptionCounter.get() % EXCEPTIONS_TO_LOG == 0)
                                || (exceptionCounter.get() == 1)) {
                            Timber.e(t, "Failed to %s RawPacket(s)!", logMessage);
                        }
                        if (t instanceof Error) {
                            throw (Error) t;
                        }
                        else {
                            throw (RuntimeException) t;
                        }
                    }
                }
            }
        }
        return pkts;
    }
}
