/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.atalk.impl.neomedia.transform.srtp.crypto;

import org.bouncycastle.crypto.BlockCipher;

/**
 * Defines the application programming interface (API) of a factory of
 * <tt>org.bouncycastle.crypto.BlockCipher</tt> instances.
 *
 * @author Lyubomir Marinov
 */
public interface BlockCipherFactory
{
    /**
     * Initializes a new <tt>BlockCipher</tt> instance.
     *
     * @param keySize AES key size (16, 24, 32 bytes)
     * @return a new <tt>BlockCipher</tt> instance
     * @throws Exception if anything goes wrong while initializing a new <tt>BlockCipher</tt> instance.
     */
    public BlockCipher createBlockCipher(int keySize)
            throws Exception;
}
