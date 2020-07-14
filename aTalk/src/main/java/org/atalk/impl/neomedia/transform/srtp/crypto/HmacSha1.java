/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.atalk.impl.neomedia.transform.srtp.crypto;

import org.bouncycastle.crypto.Mac;
import org.bouncycastle.crypto.digests.SHA1Digest;
import org.bouncycastle.crypto.macs.HMac;

/**
 * Implements a factory for an HMAC-SHA1 <tt>org.bouncycastle.crypto.Mac</tt>.
 *
 * @author Lyubomir Marinov
 */
public class HmacSha1
{
    /**
     * Initializes a new <tt>org.bouncycastle.crypto.Mac</tt> instance which
     * implements a keyed-hash message authentication code (HMAC) with SHA-1.
     *
     * @return a new <tt>org.bouncycastle.crypto.Mac</tt> instance which
     * implements a keyed-hash message authentication code (HMAC) with SHA-1
     */
    public static Mac createMac()
    {
        if (OpenSslWrapperLoader.isLoaded()) {
            return new OpenSslHmac(OpenSslHmac.SHA1);
        }
        else {
            // Fallback to BouncyCastle.
            return new HMac(new SHA1Digest());
        }
    }
}
