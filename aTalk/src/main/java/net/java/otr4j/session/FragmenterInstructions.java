package net.java.otr4j.session;

/**
 * Instructions for the fragmenter explaining how to fragment a payload.
 *
 * @author Danny van Heumen
 */
public class FragmenterInstructions
{
    /**
     * Constant for indicating an unlimited amount.
     */
    public static final int UNLIMITED = -1;

    /**
     * Maximum number of fragments.
     */
    public final int maxFragmentsAllowed;

    /**
     * Maximum size for fragments.
     */
    public final int maxFragmentSize;

    /**
     * Constructor.
     *
     * @param maxFragmentsAllowed Maximum fragments allowed.
     * @param maxFragmentSize Maximum fragment size allowed.
     */
    public FragmenterInstructions(int maxFragmentsAllowed, int maxFragmentSize)
    {
        this.maxFragmentsAllowed = maxFragmentsAllowed;
        this.maxFragmentSize = maxFragmentSize;
    }

    /**
     * Verify instructions for safe usage. It will also create a default instructions instance in case null is provided.
     *
     * If an invalid number is specified, it will be replaced with UNLIMITED.
     *
     * @param instructions the instructions or null for defaults
     * @return returns instructions.
     */
    static FragmenterInstructions verify(FragmenterInstructions instructions)
    {
        if (instructions == null) {
            return new FragmenterInstructions(UNLIMITED, UNLIMITED);
        }
        if (instructions.maxFragmentsAllowed != UNLIMITED
                && instructions.maxFragmentsAllowed < 0) {
            throw new IllegalArgumentException("Invalid fragmenter instructions: bad number of fragments.");
        }
        if (instructions.maxFragmentSize != UNLIMITED
                && instructions.maxFragmentSize < 0) {
            throw new IllegalArgumentException("Invalid fragmenter instructions: bad fragment size.");
        }
        return instructions;
    }
}
