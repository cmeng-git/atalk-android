/*
Copyright (c) 2010 Alberto Fajardo

Permission is hereby granted, free of charge, to any person
obtaining a copy of this software and associated documentation
files (the "Software"), to deal in the Software without
restriction, including without limitation the rights to use,
copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the
Software is furnished to do so, subject to the following
conditions:

The above copyright notice and this permission notice shall be
included in all copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND,
EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES
OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND
NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT
HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY,
WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING
FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR
OTHER DEALINGS IN THE SOFTWARE.

The tweaks and modifications for Java:
Copyright (c) 2010, Werner Dittmann. 

The same permissions granted.
 */
package org.atalk.bccontrib.digests;

import org.atalk.bccontrib.engines.ThreefishCipher;
import org.atalk.bccontrib.util.ByteLong;
import org.bouncycastle.crypto.ExtendedDigest;

public class Skein implements ExtendedDigest {

    public static final int NORMAL = 0;

    public static final int ZEROED_STATE = 1;

    public static final int CHAINED_STATE = 2;

    public static final int CHAINED_CONFIG = 3;

    private final byte[] schema = { 83, 72, 65, 51 }; // "SHA3"

    private ThreefishCipher cipher;

    private int cipherStateBits;

    private int cipherStateBytes;

    private int cipherStateWords;

    private int outputBytes;

    private byte[] inputBuffer;

    private int bytesFilled;

    private long[] cipherInput;

    private long[] state;

    private int hashSize;

    SkeinConfig configuration;

    public UbiTweak ubiParameters;

    public int getStateSize() {
        return cipherStateBits;
    }

    /**
     * Initializes the Skein hash instance.
     *
     * @param stateSize
     *     The internal state size of the hash in bits. Supported values 
     *     are 256, 512, and 1024
     * @param outputSize
     *     The output size of the hash in bits. Output size must greater 
     *     than zero.
     * @throws IllegalArgumentException
     */

    public Skein(int stateSize, int outputSize) throws IllegalArgumentException {

        setup(stateSize, outputSize);

        // Generate the configuration string
        configuration = new SkeinConfig(this);
        configuration.setSchema(schema); // "SHA3"
        configuration.setVersion(1);
        configuration.generateConfiguration();
        initialize();
    }

    /**
     * Initializes the Skein hash instance for use with a key (Skein MAC)
     * 
     * @param stateSize
     *     The internal state size of the hash in bits. Supported values 
     *     are 256, 512, and 1024
     * @param outputSize
     *     The output size of the hash in bits. Output size must greater 
     *     than zero.
     * @param treeInfo
     *     Not yet supported.
     * @param key
     *     The key for a message authenication code (MAC)
     * @throws IllegalArgumentException
     */
    public Skein(int stateSize, int outputSize, long treeInfo, byte[] key)
            throws IllegalArgumentException {

        setup(stateSize, outputSize);

        /* compute the initial chaining state values, based on key */
        if (key.length > 0) { /* is there a key? */
            outputBytes = cipherStateBytes;
            ubiParameters.startNewBlockType(UbiTweak.Key);
            update(key, 0, key.length); /* hash the key */
            byte[] preHash = finalPad();

            /* copy over into state variables */
            for (int i = 0; i < cipherStateWords; i++)
                state[i] = ByteLong.GetUInt64(preHash, i * 8);
        }
        /*
         * build/process the config block, type == CONFIG (could be precomputed
         * for each key)
         */
        outputBytes = (outputSize + 7) / 8;

        configuration = new SkeinConfig(this);
        configuration.setSchema(schema); // "SHA3"
        configuration.setVersion(1);

        initialize(CHAINED_CONFIG);
    }

    /*
     * Initialize the internal variables
     */
    private void setup(int stateSize, int outputSize)
            throws IllegalArgumentException {
        // Make sure the output bit size > 0
        if (outputSize <= 0)
            throw new IllegalArgumentException(
                    "Skein: Output bit size must be greater than zero.");

        cipherStateBits = stateSize;
        cipherStateBytes = stateSize / 8;
        cipherStateWords = stateSize / 64;

        hashSize = outputSize;
        outputBytes = (outputSize + 7) / 8;

        // Figure out which cipher we need based on
        // the state size
        cipher = ThreefishCipher.createCipher(stateSize);
        if (cipher == null)
            throw new IllegalArgumentException("Skein: Unsupported state size.");

        // Allocate buffers
        inputBuffer = new byte[cipherStateBytes];
        cipherInput = new long[cipherStateWords];
        state = new long[cipherStateWords];

        // Allocate tweak
        ubiParameters = new UbiTweak();
    }

    /*
     * Process (encrypt) one block with Threefish and update internal
     * context variables. 
     */
    void ProcessBlock(int bytes) {
        // Set the key to the current state
        cipher.setKey(state);

        // Update tweak
        ubiParameters.addBytesProcessed(bytes);

        cipher.setTweak(ubiParameters.getTweak());

        // Encrypt block
        cipher.encrypt(cipherInput, state);

        // Feed-forward input with state
        for (int i = 0; i < cipherInput.length; i++)
            state[i] ^= cipherInput[i];
    }

    /**
     * Update the hash with a message bit string.
     * 
     * Skein can handle data not only as bytes but also as bit strings of
     * arbitrary length (up to its maximum design size).
     * 
     * @param array
     *     The byte array that holds the bit string. The array must be big
     *     enough to hold all bits.
     * @param start
     *     Offset into byte array where the data starts, must be a byte number
     *     (not a bit number).
     * @param length
     *     Number of bits to hash.
     */
    public void updateBits(byte[] array, int start, int length) 
        throws IllegalStateException {
        
        if (ubiParameters.isBitPad())         {
            throw new IllegalStateException("Skein: partial byte only on last data block");
        }
        // if number of bits is a multiple of bytes - that's easy
        if ((length & 0x7) == 0) {
            update(array, start, length >>> 3);
            return;
        }
        // Fill in bytes in buffer, add one for partial byte
        update(array, start, (length>>>3)+1);

        // Mask partial byte and set BitPad flag before doFinal()
        byte mask = (byte)(1 << (7 - (length & 7)));        // partial byte bit mask
        inputBuffer[bytesFilled-1] = (byte)((inputBuffer[bytesFilled-1] & (0-mask)) | mask);
        ubiParameters.setBitPad(true);
    }

    public void update(byte[] array, int start, int length) {
        int bytesDone = 0;

        // Fill input buffer
        while (bytesDone < length) {
            // Do a transform if the input buffer is filled
            if (bytesFilled == cipherStateBytes) {
                // Copy input buffer to cipher input buffer
                InputBufferToCipherInput();

                // Process the block
                ProcessBlock(cipherStateBytes);

                // Clear first flag, which will be set
                // by Initialize() if this is the first transform
                ubiParameters.setFirstBlock(false);

                // Reset buffer fill count
                bytesFilled = 0;
            }
            inputBuffer[bytesFilled++] = array[start++];
            bytesDone++;
        }
    }

    public byte[] doFinal() {
        int i;

        // Pad left over space in input buffer with zeros
        // and copy to cipher input buffer
        for (i = bytesFilled; i < inputBuffer.length; i++)
            inputBuffer[i] = 0;

        InputBufferToCipherInput();

        // Do final message block
        ubiParameters.setFinalBlock(true);
        ProcessBlock(bytesFilled);

        // Clear cipher input
        for (i = 0; i < cipherInput.length; i++)
            cipherInput[i] = 0;

        // Do output block counter mode output
        int j;

        byte[] hash = new byte[outputBytes];
        long[] oldState = new long[cipherStateWords];

        // Save old state
        for (j = 0; j < state.length; j++)
            oldState[j] = state[j];

        for (i = 0; i < outputBytes; i += cipherStateBytes) {
            ubiParameters.startNewBlockType(UbiTweak.Out);
            ubiParameters.setFinalBlock(true);
            ProcessBlock(8);

            // Output a chunk of the hash
            int outputSize = outputBytes - i;
            if (outputSize > cipherStateBytes)
                outputSize = cipherStateBytes;

            ByteLong.PutBytes(state, hash, i, outputSize);

            // Restore old state
            for (j = 0; j < state.length; j++)
                state[j] = oldState[j];

            // Increment counter
            cipherInput[0]++;
        }
        reset();
        return hash;
    }

    /*
     * Internal function that performs a final block processing
     * and returns the resulting data. Used during set-up of
     * MAC key hash.
     */
    private byte[] finalPad() {
        int i;

        // Pad left over space in input buffer with zeros
        // and copy to cipher input buffer
        for (i = bytesFilled; i < inputBuffer.length; i++)
            inputBuffer[i] = 0;

        InputBufferToCipherInput();

        // Do final message block
        ubiParameters.setFinalBlock(true);
        ProcessBlock(bytesFilled);

        byte[] data = new byte[outputBytes];

        for (i = 0; i < outputBytes; i += cipherStateBytes) {
            // Output a chunk of the hash
            int outputSize = outputBytes - i;
            if (outputSize > cipherStateBytes)
                outputSize = cipherStateBytes;

            ByteLong.PutBytes(state, data, i, outputSize);
        }
        return data;
    }

    /*
     * Internal initialization function that sets up the state variables
     * in several ways. Used during set-up of MAC key hash for example.
     */
    private void initialize(int initializationType) {
        switch (initializationType) {
        case NORMAL:
            // Normal initialization
            initialize();
            return;

        case ZEROED_STATE:
            // Start with a all zero state
            for (int i = 0; i < state.length; i++)
                state[i] = 0;
            break;

        case CHAINED_STATE:
            // Keep the state as it is and do nothing
            break;

        case CHAINED_CONFIG:
            // Generate a chained configuration
            configuration.generateConfiguration(state);
            // Continue initialization
            initialize();
            return;
        }

        // Reset bytes filled
        bytesFilled = 0;
    }

    /*
     * Standard internal initialize function.
     */
    private final void initialize() {
        // Copy the configuration value to the state
        for (int i = 0; i < state.length; i++)
            state[i] = configuration.ConfigValue[i];

        // Set up tweak for message block
        ubiParameters.startNewBlockType(UbiTweak.Message);

        // Reset bytes filled
        bytesFilled = 0;
    }

    /**
     * Initialize with state variables provided by application.
     * 
     * Applications may use this method if they provide thier own Skein
     * state before starting the Skein processing. The number of long (words)
     * of the external state must conform the to number of state variables
     * this Skein instance requires (state size bits / 64).
     * 
     * After copying the external state to Skein the functions enables
     * hash processing, thus an application can call {@code update}. The
     * Skein MAC implementation uses this function to restore the state for
     * a given state size, key, and output size combination.
     * 
     * @param externalState
     *     The state to use.
     */
    public final void initialize(long[] externalState) {
        // Copy an external saved state value to internal state
        for (int i = 0; i < state.length; i++)
            state[i] = externalState[i];

        // Set up tweak for message block
        ubiParameters.startNewBlockType(UbiTweak.Message);

        // Reset bytes filled
        bytesFilled = 0;
    }

    // Moves the byte input buffer to the long cipher input
    void InputBufferToCipherInput() {
        for (int i = 0; i < cipherStateWords; i++)
            cipherInput[i] = ByteLong.GetUInt64(inputBuffer, i * 8);
    }

    /**
     * The state size of this Skein instance.
     * 
     * @return the cipherStateBits
     */
    public int getcipherStateBits() {
        return cipherStateBits;
    }

    /**
     * The output hash size in bits of this Skein instance
     * @return the hashSize int bits
     */
    public int getHashSize() {
        return hashSize;
    }

    public String getAlgorithmName() {
        return "Skein" + cipherStateBits;
    }

    public int getDigestSize() {
        return outputBytes;
    }

    public void update(byte in) {
        byte[] tmp = new byte[1];
        update(tmp, 0, 1);
    }

    public int doFinal(byte[] out, int outOff) {
        byte[] hash = doFinal();
        System.arraycopy(hash, 0, out, outOff, hash.length);
        return hash.length;
    }

    public void reset() {
        initialize();
    }

    public int getByteLength() {
        return cipherStateBytes;
    }
    
    /**
     * Get the current internal state of this Skein instance.
     * 
     * An application can get the internal state, for example after some
     * key or state-chaining processing, and reuse this state.
     * 
     * @return
     *     The current internal state.
     *
     * @see initialize(long[] externalState)
     */
    public long[] getState() {
        long[] s = new long[state.length];
        // Copy state values to external state
        for (int i = 0; i < state.length; i++)
            s[i] = state[i];
        return s;
    }
    
    class SkeinConfig {
        private final int stateSize;

        long[] ConfigValue;

        // Set the state size for the configuration
        long [] ConfigString;

        SkeinConfig(Skein sourceHash)
        {
            stateSize = sourceHash.getcipherStateBits();

            // Allocate config value
            ConfigValue = new long[stateSize / 64];

            // Set the state size for the configuration
            ConfigString = new long[ConfigValue.length];
            ConfigString[1] = sourceHash.getHashSize();
        }

        void generateConfiguration()
        {
            ThreefishCipher cipher = ThreefishCipher.createCipher(stateSize);
            UbiTweak tweak = new UbiTweak();

            // Initialize the tweak value
            tweak.startNewBlockType(UbiTweak.Config);
            tweak.setFinalBlock(true);
            tweak.setBitsProcessed(32);

            cipher.setTweak(tweak.getTweak());
            cipher.encrypt(ConfigString, ConfigValue);

            ConfigValue[0] ^= ConfigString[0]; 
            ConfigValue[1] ^= ConfigString[1];
            ConfigValue[2] ^= ConfigString[2];
        }

        void generateConfiguration(long[] initialState)
        {
            ThreefishCipher cipher = ThreefishCipher.createCipher(stateSize);
            UbiTweak tweak = new UbiTweak();

            // Initialize the tweak value
            tweak.startNewBlockType(UbiTweak.Config);
            tweak.setFinalBlock(true);
            tweak.setBitsProcessed(32);

            cipher.setKey(initialState);
            cipher.setTweak(tweak.getTweak());
            cipher.encrypt(ConfigString, ConfigValue);

            ConfigValue[0] ^= ConfigString[0];
            ConfigValue[1] ^= ConfigString[1];
            ConfigValue[2] ^= ConfigString[2];
        }

        void setSchema(byte[] schema) throws IllegalArgumentException
        {
            if (schema.length != 4) 
                throw new IllegalArgumentException("Skein configuration: Schema must be 4 bytes.");

            long n = ConfigString[0];

            // Clear the schema bytes
            n &= ~0xffffffffL;
            // Set schema bytes
            n |= (long) schema[3] << 24;
            n |= (long) schema[2] << 16;
            n |= (long) schema[1] << 8;
            n |= schema[0];

            ConfigString[0] = n;
        }

        void setVersion(int version) throws IllegalArgumentException
        {
            if (version < 0 || version > 3)
                throw new IllegalArgumentException("Skein configuration: Version must be between 0 and 3, inclusive.");

            ConfigString[0] &= ~((long)0x03 << 32);
            ConfigString[0] |= (long)version << 32;
        }

        void setTreeLeafSize(byte size)
        {
            ConfigString[2] &= ~(long)0xff;
            ConfigString[2] |= size;
        }

        void setTreeFanOutSize(byte size)
        {
            ConfigString[2] &= ~((long)0xff << 8);
            ConfigString[2] |= (long)size << 8;
        }

        void setMaxTreeHeight(byte height) throws IllegalArgumentException
        {
            if (height == 1)
                throw new IllegalArgumentException("Skein configuration: Tree height must be zero or greater than 1.");

            ConfigString[2] &= ~((long)0xff << 16);
            ConfigString[2] |= (long)height << 16;
        }
    }

    class UbiTweak {

        static final long Key = 0, Config = 4, Personalization = 8,
                PublicKey = 12, KeyIdentifier = 16, Nonce = 20, Message = 48,
                Out = 63;

        private static final long T1FlagFinal = ((long) 1 << 63);

        private static final long T1FlagFirst = ((long) 1 << 62);

        private static final long T1FlagBitPad = ((long) 1 << 55);

        private long[] tweak = new long[2];

        UbiTweak() {
        }

        /**
         * Get status of the first block flag.
         */
        boolean isFirstBlock() {
            return (tweak[1] & T1FlagFirst) != 0;
        }

        /**
         * Sets status of the first block flag.
         */
        void setFirstBlock(boolean value) {
            if (value)
                tweak[1] |= T1FlagFirst;
            else
                tweak[1] &= ~T1FlagFirst;
        }

        /**
         * Gets status of the final block flag.
         */
        boolean isFinalBlock() {
            return (tweak[1] & T1FlagFinal) != 0;
        }

        /**
         * Sets status of the final block flag.
         */
        void setFinalBlock(boolean value) {
            if (value)
                tweak[1] |= T1FlagFinal;
            else
                tweak[1] &= ~T1FlagFinal;
        }

        /**
         * Gets status of the final block flag.
         */
        boolean isBitPad() {
            return (tweak[1] & T1FlagBitPad) != 0;
        }

        /**
         * Sets status of the final block flag.
         */
        void setBitPad(boolean value) {
            if (value)
                tweak[1] |= T1FlagBitPad;
            else
                tweak[1] &= ~T1FlagBitPad;
        }
        
        /**
         * Gets  the current tree level.
         */
        byte getTreeLevel() {
            return (byte) ((tweak[1] >> 48) & 0x7f);
        }

        /**
         * Set the current tree level.
         * 
         * @param value
         *          the tree level
         */
        void setTreeLevel(int value) throws Exception {
            if (value > 63)
                throw new Exception(
                        "Tree level must be between 0 and 63, inclusive.");

            tweak[1] &= ~((long) 0x7f << 48);
            tweak[1] |= (long) value << 48;
        }

        /**
         * Gets the number of bytes processed so far, inclusive.
         * 
         * @return
         *      Number of processed bytes.
         */
        long[] getBitsProcessed() {
            long[] retval = new long[2];
            retval[0] = tweak[0];
            retval[1] = tweak[1] & 0xffffffffL;
            return retval;
        }

        /**
         * Set the number of bytes processed so far.
         * 
         * @param value
         *        The number of bits to set.
         */
        void setBitsProcessed(long value) {
            tweak[0] = value;
            tweak[1] &= 0xffffffff00000000L;
        }

        /**
         * Add number of processed bytes.
         * 
         * Adds the integere value to the 96-bit field of processed
         * bytes.
         *  
         * @param value
         *        Number of processed bytes.
         */
        void addBytesProcessed(int value) {
            final int len = 3;
            long carry = value;
            
            long words[] = new long[len];
            words[0] = tweak[0] & 0xffffffffL;
            words[1] = ((tweak[0] >>> 32) & 0xffffffffL);
            words[2] = (tweak[1] & 0xffffffffL);

            for (int i = 0; i < len; i++) {
                carry += words[i];
                words[i] = carry;
                carry >>= 32;
            }        
            tweak[0] = words[0] & 0xffffffffL;
            tweak[0] |= (words[1] & 0xffffffffL) << 32;
            tweak[1] |= words[2] & 0xffffffffL;
        }

        /**
         * Get the current UBI block type.
         */
        long getBlockType() {
            return ((tweak[1] >> 56) & 0x3f);
        }

        /**
         * Set the current UBI block type.
         * 
         * @param value
         *        Block type 
         */
        void setBlockType(long value) {
            tweak[1] = value << 56;
        }

        /**
         * Starts a new UBI block type by setting BitsProcessed to zero, setting
         * the first flag, and setting the block type.
         *
         * @param type
         *     The UBI block type of the new block
         */
        void startNewBlockType(long type) {
            setBitsProcessed(0);
            setBlockType(type);
            setFirstBlock(true);
        }

        /**
         * @return the tweak
         */
        long[] getTweak() {
            return tweak;
        }

        /**
         * @param tweak
         *            the tweak to set
         */
        void setTweak(long[] tweak) {
            this.tweak = tweak;
        }

    }

}
