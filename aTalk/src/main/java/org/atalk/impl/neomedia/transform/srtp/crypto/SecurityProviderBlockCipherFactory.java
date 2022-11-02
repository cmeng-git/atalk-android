/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.atalk.impl.neomedia.transform.srtp.crypto;

import org.bouncycastle.crypto.BlockCipher;

import java.security.Provider;
import java.security.Security;

import javax.crypto.Cipher;

/**
 * Implements a <code>BlockCipherFactory</code> which initializes <code>BlockCipher</code>s that are
 * implemented by a <code>java.security.Provider</code>.
 *
 * @author Lyubomir Marinov
 * @author Eng Chong Meng
 */
public class SecurityProviderBlockCipherFactory implements BlockCipherFactory
{
    /**
     * The <code>java.security.Provider</code> which provides the implementations of the
     * <code>BlockCipher</code>s to be initialized by this instance.
     */
    private final Provider provider;

    /**
     * The name of the transformation.
     */
    private final String transformation;

    /**
     * Initializes a new <code>SecurityProvider</code> instance which is to initialize
     * <code>BlockCipher</code>s that are implemented by a specific <code>java.security.Provider</code>.
     *
     * @param transformation the name of the transformation
     * @param provider the <code>java.security.Provider</code> which provides the implementations of the
     * <code>BlockCipher</code>s to be initialized by the new instance.
     */
    public SecurityProviderBlockCipherFactory(String transformation, Provider provider)
    {
        if (transformation == null)
            throw new NullPointerException("transformation");
        if (transformation.length() == 0)
            throw new IllegalArgumentException("transformation");
        if (provider == null)
            throw new NullPointerException("provider");

        this.transformation = transformation;
        this.provider = provider;
    }

    /**
     * Initializes a new <code>SecurityProvider</code> instance which is to initialize
     * <code>BlockCipher</code>s that are implemented by a specific <code>java.security.Provider</code>.
     *
     * @param transformation the name of the transformation
     * @param providerName the name of the <code>java.security.Provider</code> which provides the implementations of
     * the <code>BlockCipher</code>s to be initialized by the new instance.
     */
    public SecurityProviderBlockCipherFactory(String transformation, String providerName)
    {
        this(transformation, Security.getProvider(providerName));
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public BlockCipher createBlockCipher(int keySize)
            throws Exception
    {
        return new BlockCipherAdapter(
                Cipher.getInstance(transformation.replaceFirst("<size>", Integer.toString(keySize * 8)), provider)
        );
    }
}
