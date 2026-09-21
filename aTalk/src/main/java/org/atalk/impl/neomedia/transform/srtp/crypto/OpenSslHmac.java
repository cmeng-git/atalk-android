/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.atalk.impl.neomedia.transform.srtp.crypto;

import org.bouncycastle.crypto.CipherParameters;
import org.bouncycastle.crypto.DataLengthException;
import org.bouncycastle.crypto.Mac;
import org.bouncycastle.crypto.params.KeyParameter;

/**
 * Implements the interface <code>org.bouncycastle.crypto.Mac</code> using the OpenSSL Crypto library.
 *
 * @author Lyubomir Marinov
 * @author Eng Chong Meng
 */
public class OpenSslHmac implements Mac {
    private static native int EVP_MD_size(long md);

    private static native long EVP_sha1();

    private static native long EVP_MAC_CTX_new();

    private static native void EVP_MAC_CTX_free(long ctx);

    private static native boolean EVP_MAC_init(long ctx, byte[] key, int keyLen, long md);

    private static native boolean EVP_MAC_update(long ctx, byte[] data, int off, int len);

    private static native int EVP_MAC_final(long ctx, byte[] md, int mdOff, long mdLen);

    /**
     * The name of the algorithm implemented by this instance.
     */
    private static final String algorithmName = "SHA-1/HMAC";

    /**
     * The context of the OpenSSL (Crypto) library through which the actual
     * algorithm implementation is invoked by this instance.
     */
    private long ctx;

    /**
     * The key provided in the form of a {@link KeyParameter} in the last invocation of
     * {@link #init(CipherParameters)}.
     */
    private byte[] key;

    /**
     * The block size in bytes for this MAC.
     */
    private final int macSize;

    /**
     * The OpenSSL Crypto type of the message digest implemented by this instance.
     */
    private final long md;

    /**
     * The algorithm of the SHA-1 cryptographic hash function/digest.
     */
    public static final int SHA1 = 1;

    /**
     * Initializes a new <code>OpenSslHmac</code> instance with a specific digest algorithm.
     *
     * @param digestAlgorithm the algorithm of the digest to initialize the new instance with
     *
     * @see OpenSslHmac#SHA1
     */
    public OpenSslHmac(int digestAlgorithm) {
        if (!OpenSslWrapperLoader.isLoaded())
            throw new RuntimeException("OpenSSL wrapper not loaded");

        if (digestAlgorithm != OpenSslHmac.SHA1)
            throw new IllegalArgumentException("digestAlgorithm " + digestAlgorithm);

        md = EVP_sha1();
        if (md == 0)
            throw new IllegalStateException("EVP_sha1 == 0");

        macSize = EVP_MD_size(md);
        if (macSize == 0)
            throw new IllegalStateException("EVP_MD_size == 0");

        ctx = EVP_MAC_CTX_new();
        if (ctx == 0)
            throw new RuntimeException("EVP_MAC_CTX_new == 0");
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int doFinal(byte[] out, int outOff)
            throws DataLengthException, IllegalStateException {
        if (out == null)
            throw new NullPointerException("out");
        if ((outOff < 0) || (out.length <= outOff))
            throw new ArrayIndexOutOfBoundsException(outOff);

        int outLen = out.length - outOff;
        int macSize = getMacSize();

        if (outLen < macSize) {
            throw new DataLengthException("Space in out must be at least " + macSize
                    + "bytes but is " + outLen + " bytes!");
        }

        long ctx = this.ctx;
        if (ctx == 0) {
            throw new IllegalStateException("ctx");
        }
        else {
            outLen = EVP_MAC_final(ctx, out, outOff, outLen);
            if (outLen < 0) {
                throw new RuntimeException("EVP_MAC_final");
            }
            else {
                // As the Javadoc on interface method specifies,
                // the doFinal call leaves this Digest reset.
                reset();
                return outLen;
            }
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void finalize()
            throws Throwable {
        try {
            // Well, the destroying in the finalizer should exist as a backup anyway. There is no way
            // to explicitly invoke the destroying at the time of this writing, but it is a start.
            long ctx = this.ctx;

            if (ctx != 0) {
                this.ctx = 0;
                EVP_MAC_CTX_free(ctx);
            }
        }
        finally {
            super.finalize();
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String getAlgorithmName() {
        return algorithmName;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int getMacSize() {
        return macSize;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void init(CipherParameters params)
            throws IllegalArgumentException {
        key = (params instanceof KeyParameter)
                ? ((KeyParameter) params).getKey() : null;

        if (key == null)
            throw new IllegalStateException("key == null");
        if (ctx == 0)
            throw new IllegalStateException("ctx == 0");

        if (!EVP_MAC_init(ctx, key, key.length, md))
            throw new RuntimeException("EVP_MAC_init() init failed");
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void reset() {
        if (key == null)
            throw new IllegalStateException("key == null");
        if (ctx == 0)
            throw new IllegalStateException("ctx == 0");

        // RESET CTX KEEPING KEY & MD
        // Passing NULL as key and NULL (or empty) as params tells OpenSSL to
        // clear data states but reuse the previously loaded key and configuration.
        if (!EVP_MAC_init(ctx, null, 0, 0))
            throw new RuntimeException("EVP_MAC_init() reset failed");
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void update(byte in)
            throws IllegalStateException {
        // TODO Auto-generated method stub
    }

    /**
     * {@inheritDoc}
     * Buffer to hold the final MAC output (SHA1 output is 20 bytes)
     */
    @Override
    public void update(byte[] in, int off, int len)
            throws DataLengthException, IllegalStateException {
        if (len != 0) {
            if (in == null)
                throw new NullPointerException("in");
            if ((off < 0) || (in.length <= off))
                throw new ArrayIndexOutOfBoundsException(off);
            if ((len < 0) || (in.length < off + len))
                throw new IllegalArgumentException("len " + len);

            long ctx = this.ctx;

            if (ctx == 0)
                throw new IllegalStateException("ctx");
            else if (!EVP_MAC_update(ctx, in, off, len))
                throw new RuntimeException("EVP_MAC_update");
        }
    }
}
