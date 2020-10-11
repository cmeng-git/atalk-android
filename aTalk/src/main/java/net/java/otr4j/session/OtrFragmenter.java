package net.java.otr4j.session;

import net.java.otr4j.OtrEngineHost;
import net.java.otr4j.OtrPolicy;

import java.io.IOException;
import java.util.LinkedList;
import java.util.Locale;

/**
 * OTR fragmenter.
 * TODO It may be better to separate the v2 and v3 implementations into specialized classes.
 *
 * @author Danny van Heumen
 */
public class OtrFragmenter
{
    /**
     * Exception message in cases where only OTRv1 is allowed.
     */
    private static final String OTR_V1_NOT_SUPPORTED = "Fragmentation is not supported in OTRv1.";

    /**
     * The maximum number of fragments supported by the OTR (v3) protocol.
     */
    private static final int MAXIMUM_NUMBER_OF_FRAGMENTS = 65535;

    /**
     * The message format of an OTRv3 message fragment.
     */
    private static final String OTR_V3_MESSAGE_FRAGMENT_FORMAT = "?OTR|%08x|%08x,%05d,%05d,%s,";

    /**
     * The message format of an OTRv2 message fragment.
     */
    private static final String OTR_V2_MESSAGE_FRAGMENT_FORMAT = "?OTR,%d,%d,%s,";

    /**
     * Session instance.
     */
    private final Session session;

    /**
     * Instructions on how to fragment the input message.
     */
    private final OtrEngineHost host;

    /**
     * Constructor.
     *
     * @param session session instance (cannot be null)
     * @param host OTR engine host calling upon OTR session
     */
    public OtrFragmenter(final Session session, final OtrEngineHost host)
    {
        if (session == null) {
            throw new NullPointerException("session cannot be null");
        }
        this.session = session;
        if (host == null) {
            throw new NullPointerException("host cannot be null");
        }
        this.host = host;
    }

    /**
     * Get instructions for fragmentation behaviour.
     *
     * @return returns instructions
     */
    public OtrEngineHost getHost()
    {
        return this.host;
    }

    /**
     * Calculate the number of fragments that are required for the message to be sent fragmented completely.
     *
     * @param message the original message
     * @return returns the number of fragments required
     * @throws IOException throws an IOException in case fragment size is too small to store any content or when
     * the provided policy does not support fragmentation, for example if only OTRv1 is allowed.
     */
    public int numberOfFragments(final String message)
            throws IOException
    {
        final SessionID sessionID = this.session.getSessionID();
        final FragmenterInstructions requested = this.host.getFragmenterInstructions(sessionID);
        final FragmenterInstructions instructions = FragmenterInstructions.verify(requested);
        return numberOfFragments(message, instructions);
    }

    /**
     * Calculate the number of fragments that are required for the message to be sent fragmented completely.
     *
     * @param message the message to fragment
     * @param instructions the fragmentation instructions
     * @return returns number of fragments required
     * @throws IOException throws an IOException in case fragment size is too small to store any content or when
     * the provided policy does not support fragmentation, for example if only OTRv1 is allowed.
     */
    private int numberOfFragments(final String message, final FragmenterInstructions instructions)
            throws IOException
    {
        if (instructions.maxFragmentSize == FragmenterInstructions.UNLIMITED
                || instructions.maxFragmentSize >= message.length()) {
            return 1;
        }
        return computeFragmentNumber(message, instructions);
    }

    /**
     * Compute the number of fragments required.
     *
     * @param message the original message
     * @param instructions fragmentation instructions
     * @return returns number of fragments required.
     * @throws IOException throws an IOException if fragment size is too small.
     */
    private int computeFragmentNumber(final String message, final FragmenterInstructions instructions)
            throws IOException
    {
        final int overhead = computeHeaderSize();
        final int payloadSize = instructions.maxFragmentSize - overhead;
        if (payloadSize <= 0) {
            throw new IOException("Fragment size too small for storing content.");
        }
        int messages = message.length() / payloadSize;
        if (message.length() % payloadSize != 0) {
            messages++;
        }
        return messages;
    }

    /**
     * Fragment the given message into pieces as specified by the FragmenterInstructions instance.
     *
     * @param message the original message
     * @return returns an array of message fragments. The array will contain at least 1 message
     * fragment, or more if fragmentation is necessary.
     * @throws IOException throws an IOException if the fragment size is too small or if the maximum number of
     * fragments is exceeded.
     */
    public String[] fragment(final String message)
            throws IOException
    {
        final SessionID sessionID = this.session.getSessionID();
        final FragmenterInstructions requested = this.host.getFragmenterInstructions(sessionID);
        final FragmenterInstructions instructions = FragmenterInstructions.verify(requested);
        return fragment(message, instructions);
    }

    /**
     * Fragment a message according to the specified instructions.
     *
     * @param message the message
     * @param instructions the instructions
     * @return returns the fragmented message. The array will contain at least 1 message fragment,
     * or more if fragmentation is necessary.
     * @throws IOException Exception in the case when it is impossible to fragment the message according to the
     * specified instructions.
     */
    private String[] fragment(final String message, final FragmenterInstructions instructions)
            throws IOException
    {
        if (instructions.maxFragmentSize == FragmenterInstructions.UNLIMITED
                || instructions.maxFragmentSize >= message.length()) {
            return new String[]{message};
        }
        final int num = numberOfFragments(message, instructions);
        if (instructions.maxFragmentsAllowed != FragmenterInstructions.UNLIMITED
                && instructions.maxFragmentsAllowed < num) {
            throw new IOException("Need more fragments to store full message.");
        }
        if (num > MAXIMUM_NUMBER_OF_FRAGMENTS) {
            throw new IOException("Number of necessary fragments exceeds limit.");
        }
        final int payloadSize = instructions.maxFragmentSize - computeHeaderSize();
        int previous = 0;
        final LinkedList<String> fragments = new LinkedList<>();
        while (previous < message.length()) {
            // Either get new position or position of exact message end
            final int end = Math.min(previous + payloadSize, message.length());

            final String partialContent = message.substring(previous, end);
            fragments.add(createMessageFragment(fragments.size(), num, partialContent));

            previous = end;
        }
        return fragments.toArray(new String[0]);
    }

    /**
     * Create a message fragment.
     *
     * @param count the current fragment number
     * @param total the total number of fragments
     * @param partialContent the content for this fragment
     * @return returns the full message fragment
     * @throws UnsupportedOperationException in case v1 is only allowed in policy
     */
    private String createMessageFragment(final int count, final int total, final String partialContent)
    {
        if (getPolicy().getAllowV3()) {
            return createV3MessageFragment(count, total, partialContent);
        }
        else {
            return createV2MessageFragment(count, total, partialContent);
        }
    }

    /**
     * Create a message fragment according to the v3 message format.
     *
     * @param count the current fragment number
     * @param total the total number of fragments
     * @param partialContent the content for this fragment
     * @return returns the full message fragment
     */
    private String createV3MessageFragment(final int count, final int total, final String partialContent)
    {
        final String msg = String.format(Locale.US, OTR_V3_MESSAGE_FRAGMENT_FORMAT,
                getSenderInstance(), getReceiverInstance(), count + 1, total, partialContent);
        return msg;
    }

    /**
     * Create a message fragment according to the v2 message format.
     *
     * @param count the current fragment number
     * @param total the total number of fragments
     * @param partialContent the content for this fragment
     * @return returns the full message fragment
     */
    private String createV2MessageFragment(final int count, final int total,
            final String partialContent)
    {
        final String msg = String.format(Locale.US, OTR_V2_MESSAGE_FRAGMENT_FORMAT, count + 1, total, partialContent);
        return msg;
    }

    /**
     * Compute size of fragmentation header size.
     *
     * @return returns size of fragment header
     * @throws UnsupportedOperationException in case v1 is only allowed in policy
     */
    private int computeHeaderSize()
    {
        if (getPolicy().getAllowV3()) {
            return computeHeaderV3Size();
        }
        else if (getPolicy().getAllowV2()) {
            return computeHeaderV2Size();
        }
        else {
            throw new UnsupportedOperationException(OTR_V1_NOT_SUPPORTED);
        }
    }

    /**
     * Compute the overhead size for a v3 header.
     *
     * @return returns size of v3 header
     */
    static int computeHeaderV3Size()
    {
        // For a OTRv3 header this seems to be a constant number, since the
        // specs seem to suggest that smaller numbers have leading zeros.
        return 36;
    }

    /**
     * Compute the overhead size for a v2 header.
     * <p>
     * Current implementation returns an upper bound size for the size of the header. As I
     * understand it, the protocol does not require leading zeros to fill a 5-space number are so
     * in theory it is possible to gain a few extra characters per message if an exact
     * calculation of the number of required chars is used.
     * <p>
     * TODO I think this is dependent on the number of chars in a decimal representation of the
     * current and total number of fragments.
     *
     * @return returns size of v2 header
     */
    static int computeHeaderV2Size()
    {
        // currently returns an upper bound (for the case of 10000+ fragments)
        return 18;
    }

    /**
     * Get the OTR policy.
     *
     * @return returns the policy
     */
    private OtrPolicy getPolicy()
    {
        return this.session.getSessionPolicy();
    }

    /**
     * Get the sender instance.
     *
     * @return returns the sender instance
     */
    private int getSenderInstance()
    {
        return this.session.getSenderInstanceTag().getValue();
    }

    /**
     * Get the receiver instance.
     *
     * @return returns the receiver instance
     */
    private int getReceiverInstance()
    {
        return this.session.getReceiverInstanceTag().getValue();
    }
}
