package org.atalk.bccontrib.prng;

import org.bouncycastle.crypto.BlockCipher;
import org.bouncycastle.crypto.Digest;
import org.bouncycastle.crypto.digests.SHA256Digest;
import org.bouncycastle.crypto.engines.AESFastEngine;
import org.bouncycastle.crypto.params.KeyParameter;
import org.bouncycastle.crypto.prng.RandomGenerator;

import java.util.Arrays;

/**
 * The Fortuna random number generator.
 * <p>
 * This class is a modified variant of the original Fortuna generator in GNU
 * Classpath (see note below). The class now uses the bouncycastle hash and
 * cipher classes and provides a bouncycastle compliant interface.
 * <p>
 * Fortuna is a continuously-seeded pseudo-random number generator (PRNG)
 * and is composed of two major pieces: the entropy accumulator and the
 * generator function. The former takes in random bits and incorporates them
 * into the generator's state. The latter takes this base entropy and generates
 * pseudo-random bits from it.
 * <p>
 * Here an example how to use the FortunaGenerator (pseudo code):
 * 
 * <pre>
 * ...
 *     new Random().nextBytes(firstSeed)  // get some random data
 *     FortunaGenerator fg = new FortunaGenerator(firstSeed);
 *     ...
 *     
 *     fg.nextBytes(randomData)
 *     ...
 *     fg.addSeedMaterial(entropyData)
 * 
 * </pre>
 * 
 * After some time the application has done its work and exits. To enable 
 * a fast restart of the FortunaGenerator you may store the seed status 
 * and initialize the FortunaGenerator with this seed the next time. For
 * example:
 * 
 * <pre>
 * ... // use FortunaGenerator
 *     seedStatus = fg.getSeedStatus()
 *     
 *     // save seed status somewhere
 *     // exit application
 * ...
 *     // restart application
 *     if (saved seed status available) 
 *         read seed status data
 *         FortunaGenerator fg = new FortunaGenerator()
 *         fg.setSeedStatus(seed status data)
 *     // alternatively you can do 
 *         FortunaGenerator = new FortunaGenerator(seed status data)
 *     // use FortunaGenerator
 * 
 * </pre>
 * 
 * There are some things users of this class <em>must</em> be aware of:
 * <dl>
 * <dt>Adding Random Data</dt>
 * <dd>This class does not do any polling of random sources, but rather provides
 * an interface for adding entropy data (additional seed). Applications that use
 * this code <em>must</em> provide this mechanism. We use this design because an
 * application writer who knows the system he is targeting is in a better
 * position to judge what random data is available.</dd>
 * <dt>Storing the Seed</dt>
 * <dd>This class implements functions to read and restore the seed in such a
 * way that it returns a 64 byte seed byte array to the application, and sets it
 * back again when the application sets the seed status again. This is the
 * extent of seed file management, however, and those using this class are
 * encouraged to think deeply about when, how often, and where to store the
 * seed.</dd>
 * </dl>
 * <p>
 * <b>References:</b>
 * <ul>
 * <li>Niels Ferguson and Bruce Schneier, <i>Practical Cryptography</i>, pp.
 * 155--184. Wiley Publishing, Indianapolis. (2003 Niels Ferguson and Bruce
 * Schneier). ISBN 0-471-22357-3.</li>
 * </ul>
 * 
 * I did some small enhancements of the re-seed loop that, otherwise the
 * algorithms were not touched.
 * <p>
 * License: the bouncycastle license applies to this file. Also notice the GNU
 * Classpath license exception (see below).
 * <p>
 * Copyright (C) 2010 Werner Dittmann (Werner.Dittmann@t-online.de) <br>
 * Copyright (C) 2004, 2006 Free Software Foundation, Inc.
 * 
 */

/* 
 * Copyright note of the original Fortuna source. Not that THIS file is not
 * longer part of GNU Classpath.
 * 
 * Fortuna.java -- The Fortuna PRNG.
Copyright (C) 2004, 2006 Free Software Foundation, Inc.

This file is a part of GNU Classpath.

GNU Classpath is free software; you can redistribute it and/or modify
it under the terms of the GNU General Public License as published by
the Free Software Foundation; either version 2 of the License, or (at
your option) any later version.

GNU Classpath is distributed in the hope that it will be useful, but
WITHOUT ANY WARRANTY; without even the implied warranty of
MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
General Public License for more details.

You should have received a copy of the GNU General Public License
along with GNU Classpath; if not, write to the Free Software
Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301
USA

Linking this library statically or dynamically with other modules is
making a combined work based on this library.  Thus, the terms and
conditions of the GNU General Public License cover the whole
combination.

As a special exception, the copyright holders of this library give you
permission to link this library with independent modules to produce an
executable, regardless of the license terms of these independent
modules, and to copy and distribute the resulting executable under
terms of your choice, provided that you also meet, for each linked
independent module, the terms and conditions of the license of that
module.  An independent module is a module which is not derived from
or based on this library.  If you modify this library, you may extend
this exception to your version of the library, but you are not
obligated to do so.  If you do not wish to do so, delete this
exception statement from your version.  */

public class FortunaGenerator implements RandomGenerator {

    private static final int SEED_FILE_SIZE = 64;
    private static final int NUM_POOLS = 32;
    private static final int MIN_POOL_SIZE = 64;
    private final Generator generator;
    private final Digest[] pools;
    private long lastReseed = 0;
    private int pool = 0;
    private int pool0Count = 0;
    private int reseedCount = 0;
    private boolean initialized = false;
    
    /** A temporary buffer to serve random bytes. */
    private byte[] buffer;

    /** The index into buffer of where the next byte will come from. */
    protected int ndx = 0;

    public FortunaGenerator() {
    	this(null);
    }
    
    public FortunaGenerator(byte[] seed) {
        generator = new Generator(new AESFastEngine(), new SHA256Digest());
        pools = new Digest[NUM_POOLS];
        for (int i = 0; i < NUM_POOLS; i++)
            pools[i] = new SHA256Digest();
        buffer = new byte[256];	
    	if (seed != null) {
    		generator.init(seed);
    		fillBlock();
    		initialized = true;
    	}
    }

    private void fillBlock()  {
        if (pool0Count >= MIN_POOL_SIZE
                && System.currentTimeMillis() - lastReseed > 100)
        {
            long powerOfTwo = 1;
            reseedCount++;
            byte[] randomBytes = new byte[pools[0].getDigestSize()];
            for (int i = 0; i < NUM_POOLS; i++) {
                if (i == 0 || reseedCount % powerOfTwo == 0) {
                    pools[i].doFinal(randomBytes, 0);
                    generator.addRandomBytes(randomBytes, 0, randomBytes.length);
                }
                else
                    break;

                powerOfTwo <<= 1;
            }
            lastReseed = System.currentTimeMillis();
            pool0Count = 0;
        }
        generator.nextBytes(buffer, 0, buffer.length);
    }

    /**
     * Get new random data.
     * <p>
     * This functions fills a byte buffer with new random data. 
     * 
     * @param out the buffer that receives the random data
     */
    public void nextBytes(byte[] out) {
        nextBytes(out, 0, out.length);
    }

    /**
     * Get new random data.
     * <p>
     * This functions returns new random data. 
     * 
     * @param out the buffer that receives the random data
     * @param offset offset into the buffer
     * @param length number of random bytes
     */
    public void nextBytes(byte[] out, int offset, int length) {
        if (!initialized)
            throw new IllegalStateException(" Fortuna generator not initialized/seeded");

        if (length == 0)
            return;

        if (offset < 0 || length < 0 || offset + length > out.length)
            throw new ArrayIndexOutOfBoundsException("offset=" + offset + " length="
                    + length + " limit="
                    + out.length);
        
        if (ndx >= buffer.length) {
            fillBlock();
            ndx = 0;
        }
        int count = 0;
        while (count < length) {
            int amount = Math.min(buffer.length - ndx, length - count);
            System.arraycopy(buffer, ndx, out, offset + count, amount);
            count += amount;
            ndx += amount;
            if (ndx >= buffer.length) {
                fillBlock();
                ndx = 0;
            }
        }
    }

    /**
     * Adds new random data (entropy) to an entropy pool.
     * <p>
     * This functions adds entropy data to the current pool. Fortuna uses
     * 32 pools to gather entropy. After the function added the entropy to
     * the pool it increments the current pool number modulo 32.
     * <p>
     * Only if pool 0 (zero) got enough entropy (min. 64 bytes) then Fortuna
     * uses the pools to perform a real re-seed. If an application uses this
     * function to add entropy it shall take this behaviour into consideration.
     * 
     * @param b  a long with new entropy data. If the current pool is 0 then
     *            the function adds the length of a long to the overall
     *            entropy count that controls re-seed.
     */
    public void addSeedMaterial(long b) {
        pools[pool].update((byte)(b & 0xff));
        pools[pool].update((byte)((b >>  8) & 0xff));
        pools[pool].update((byte)((b >> 16) & 0xff));
        pools[pool].update((byte)((b >> 24) & 0xff));
        pools[pool].update((byte)((b >> 32) & 0xff));
        pools[pool].update((byte)((b >> 40) & 0xff));
        pools[pool].update((byte)((b >> 48) & 0xff));
        pools[pool].update((byte)((b >> 56) & 0xff));
        if (pool == 0)
            pool0Count += 8;
        pool = (pool + 1) % NUM_POOLS;
    }

    /**
     * Adds new random data (entropy) to an entropy pool.
     * <p>
     * This functions adds entropy data to the current pool. Fortuna uses
     * 32 pools to gather entropy. After the function added the entropy to
     * the pool it increments the current pool number modulo 32.
     * <p>
     * Only if pool 0 (zero) got enough entropy (min. 64 bytes) then Fortuna
     * uses the pools to perform a real re-seed. If an application uses this
     * function to add entropy it shall take this behaviour into consideration.
     * 
     * @param buf buffer with new entropy data. If the current pool is 0 then
     *            the function adds the length of the buffer to the overall
     *            entropy count that controls re-seed.
     */
    public void addSeedMaterial(byte[] buf) {
        addSeedMaterial(buf, 0, buf.length);
    }

    /**
     * Adds new random data (entropy) to an entropy pool.
     * <p>
     * This functions adds entropy data to the current pool. Fortuna uses
     * 32 pools to gather entropy. After the function added the entropy to
     * the pool it increments the current pool number modulo 32.
     * <p>
     * Only if pool 0 (zero) got enough entropy (min. 64 bytes) then Fortuna
     * uses the pools to perform a real re-seed. If an application uses this
     * function to add entropy it shall take this behaviour into consideration.
     * 
     * @param buf buffer with new entropy data. 
     * @param offset offset into the buffer
     * @param length number of bytes to add to the current pool's entropy.
     *            If the current pool is 0 then the function adds the length 
     *            of the buffer to the overall entropy count that controls 
     *            re-seed.
     */
    public void addSeedMaterial(byte[] buf, int offset, int length) {
        pools[pool].update(buf, offset, length);
        if (pool == 0)
            pool0Count += buf.length;
        pool = (pool + 1) % NUM_POOLS;
    }

    /**
     * Adds new random data (entropy) to the specified entropy pool.
     * <p>
     * This functions adds entropy data to the the specified pool. Fortuna 
     * uses32 pools to gather entropy.
     * <p>
     * Only if pool 0 (zero) got enough entropy (min. 64 bytes) then Fortuna
     * uses the pools to perform a real re-seed. If an application uses this
     * function to add entropy it shall take this behaviour into consideration.
     * 
     * @param poolNumber specifies which pool receives the entropy data
     * @param data buffer with new entropy data. 
     * @param offset offset into the buffer
     * @param length number of bytes to add to the specified pool's entropy.
     *            If the specified pool is 0 then the function adds the length 
     *            of the data to the overall entropy count that controls 
     *            re-seed.
     */
    public void addSeedMaterial(int poolNumber, byte[] data, int offset, int length) {
      if (poolNumber < 0 || poolNumber >= pools.length)
        throw new IllegalArgumentException("pool number out of range: "
                                           + poolNumber);
      pools[poolNumber].update((byte)length);
      pools[poolNumber].update(data, offset, length);
      if (poolNumber == 0)
        pool0Count += length;
    }

    
    /**
     * Return the generator's seed status.
     * <p>
     * An application may get the seed status, store it in a safe place
     * and retrieve it to seed a new Fortuna PRNG instance.
     * 
     * @return The seed status.
     */
    public byte[] getSeedStatus() {
        byte[] seed = new byte[SEED_FILE_SIZE];
        generator.nextBytes(seed, 0, seed.length);
        return seed;
    }

    /**
     * Seed the generator with a previously saved seed.
     * 
     * @param seedStatus the generator's seed.
     */
    public void setSeedStatus(byte[] seedStatus) {
        generator.init(seedStatus);
        fillBlock();
        initialized = true;
    }

    /**
     * The Fortuna generator function. The generator is a PRNG in its own right;
     * Fortuna itself is basically a wrapper around this generator that manages
     * re-seeding in a secure way.
     */
    private static class Generator  {
        private static final int LIMIT = 1 << 20;
        private final BlockCipher cipher;
        private final Digest hash;
        private final byte[] counter;
        private final byte[] key;

        /** A temporary buffer to serve random bytes. */
        private byte[] buffer;

        /** The index into buffer of where the next byte will come from. */
        protected int ndx = 0;

        private Generator(final BlockCipher cipher, final Digest hash)
        {
            this.cipher = cipher;
            this.hash = hash;
            counter = new byte[cipher.getBlockSize()];
            buffer = new byte[cipher.getBlockSize()];
            key = new byte[32];
        }

        private void nextBytes(byte[] out, int offset, int length) {

        	int count = 0;
            do {
                int amount = Math.min(LIMIT, length - count);
                nextBytesInternal(out, offset + count, amount);
                count += amount;
                for (int i = 0; i < key.length; i += counter.length) {
                    fillBlock();
                    int l = Math.min(key.length - i, cipher.getBlockSize());
                    System.arraycopy(buffer, 0, key, i, l);
                }
                resetKey();
            } while (count < length);

            fillBlock();
            ndx = 0;
        }

        private void addRandomBytes(byte[] seed, int offset, int length) {
            hash.update(key, 0, key.length);
            hash.update(seed, offset, length);
            byte[] newkey = new byte[hash.getDigestSize()];
            hash.doFinal(newkey, 0);
            System.arraycopy(newkey, 0, key, 0, Math.min(key.length, newkey.length));
            resetKey();
            incrementCounter();
        }

        private void fillBlock() {
            cipher.processBlock(counter, 0, buffer, 0);
            incrementCounter();
        }

        private void init(byte[] seed) {
            Arrays.fill(key, (byte) 0);
            Arrays.fill(counter, (byte) 0);
            if (seed != null)
                addRandomBytes(seed, 0, seed.length);
            fillBlock();
        }

        private void nextBytesInternal(byte[] out, int offset, int length) {

            if (length == 0)
                return;

            if (offset < 0 || length < 0 || offset + length > out.length)
                throw new ArrayIndexOutOfBoundsException("offset=" + offset + " length="
                        + length + " limit="
                        + out.length);
            if (ndx >= buffer.length) {
                fillBlock();
                ndx = 0;
            }
            int count = 0;
            while (count < length) {
                int amount = Math.min(buffer.length - ndx, length - count);
                System.arraycopy(buffer, ndx, out, offset + count, amount);
                count += amount;
                ndx += amount;
                if (ndx >= buffer.length) {
                    fillBlock();
                    ndx = 0;
                }
            }
        }

        /**
         * Resets the cipher's key. This is done after every reseed, which combines
         * the old key and the seed, and processes that through the hash function.
         */
        private void resetKey() {
            cipher.reset();
            cipher.init(true, new KeyParameter(key));
        }

        /**
         * Increment `counter' as a sixteen-byte little-endian unsigned integer by
         * one.
         */
        private void incrementCounter() {
            for (int i = 0; i < counter.length; i++) {
                counter[i]++;
                if (counter[i] != 0)
                    break;
            }
        }
    }
}

