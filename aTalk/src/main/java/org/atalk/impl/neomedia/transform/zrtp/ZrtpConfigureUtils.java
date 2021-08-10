/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 * 
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.atalk.impl.neomedia.transform.zrtp;

import gnu.java.zrtp.*;

import org.atalk.service.configuration.ConfigurationService;
import org.atalk.service.libjitsi.LibJitsi;

public class ZrtpConfigureUtils
{
	public static <T extends Enum<T>> String getPropertyID(T algo)
	{
		Class<T> clazz = algo.getDeclaringClass();
		return "net.java.sip.communicator." + clazz.getName().replace('$', '_');
	}

    /**
     * Improvement made by: MilanKrai on 20200228
     *
     * Upgrade crypto algorithms used in ZRTP to stronger versions:
     * Enable use of SHA-2 384
     *
     * Prefer use of 256 bit ciphers AES-256 and TWOFISH-256
     * For technical details see paper:
     * Daniel J. Bernstein. "Understanding brute force."
     * ECRYPT STVL Workshop on Symmetric Key Encryption.
     * {@see} https://cr.yp.to/snuffle/bruteforce-20050425.pdf
     *

     *
     * Enable elliptic curve crypto using Curve 25519.
     * See the recommendations in paper
     * "Imperfect Forward Secrecy: How Diffie-Hellman Fails in Practice"
     * https://weakdh.org/imperfect-forward-secrecy-ccs15.pdf
     * https://cr.yp.to/newelliptic/nistecc-20160106.pdf
     *
     * cmeng (20200626)
     * {@see} ZRTP: Media Path Key Agreement for Unicast Secure RTP
     * https://tools.ietf.org/html/rfc6189
     * 5.1.3.  Cipher Type Block
     *   All ZRTP endpoints MUST support AES-128 (AES1) and MAY support AES-
     *   192 (AES2), AES-256 (AES3), or other Cipher Types. The Advanced
     *   Encryption Standard is defined in [FIPS-197].
     *
     * 5.1.4.  Auth Tag Type Block
     *    All ZRTP endpoints MUST support HMAC-SHA1 authentication tags for
     *    SRTP, with both 32-bit and 80-bit length tags as defined in [RFC3711].
     *
     * 5.1.5.  Key Agreement Type Block
     *    All ZRTP endpoints MUST support DH3k, SHOULD support Preshared, and
     *    MAY support EC25, EC38, and DH2k.
     *
     * 5.1.6.  SAS Type Block
     *    All ZRTP endpoints MUST support the base32 and MAY support the
     *    base256 rendering schemes for the Short Authentication String, and
     *    other SAS rendering schemes.  See Section 4.5.2 for how the sasvalue
     *    is computed and Section 7 for how the SAS is used.
     *
     * Use the longer HS80 and SK64 MACs.
     * @return ZrtpConfigure
     *
     */
	public static ZrtpConfigure getZrtpConfiguration()
	{
		ZrtpConfigure active = new ZrtpConfigure();
        
//        setupConfigure(ZrtpConstants.SupportedPubKeys.DH2K, active);
//        setupConfigure(ZrtpConstants.SupportedHashes.S256, active);
//        setupConfigure(ZrtpConstants.SupportedSymCiphers.AES1, active);
//        setupConfigure(ZrtpConstants.SupportedSASTypes.B32, active);
//        setupConfigure(ZrtpConstants.SupportedAuthLengths.HS32, active);

		active.addAlgo(ZrtpConstants.SupportedHashes.S384);

		active.addAlgo(ZrtpConstants.SupportedSymCiphers.AES3);
		active.addAlgo(ZrtpConstants.SupportedSymCiphers.TWO3);
        active.addAlgo(ZrtpConstants.SupportedSymCiphers.AES1); // mandatory v1.10

		active.addAlgo(ZrtpConstants.SupportedPubKeys.E255);
		active.addAlgo(ZrtpConstants.SupportedPubKeys.MULT);
        active.addAlgo(ZrtpConstants.SupportedPubKeys.DH3K); // mandatory v1.10

		active.addAlgo(ZrtpConstants.SupportedAuthLengths.HS80);
		active.addAlgo(ZrtpConstants.SupportedAuthLengths.SK64);
        active.addAlgo(ZrtpConstants.SupportedAuthLengths.HS32);  // mandatory v1.10

        active.addAlgo(ZrtpConstants.SupportedSASTypes.B32);  // mandatory v1.10

		return active;
	}

	private static <T extends Enum<T>> void setupConfigure(T algo, ZrtpConfigure active)
	{
		ConfigurationService cfg = LibJitsi.getConfigurationService();
		String savedConf = null;

		if (cfg != null) {
			String id = ZrtpConfigureUtils.getPropertyID(algo);
			savedConf = cfg.getString(id);
		}
		if (savedConf == null)
			savedConf = "";

		Class<T> clazz = algo.getDeclaringClass();
		String[] savedAlgos = savedConf.split(";");

		// Configure saved algorithms as active
		for (String str : savedAlgos) {
			try {
				T algoEnum = Enum.valueOf(clazz, str);

				if (algoEnum != null)
					active.addAlgo(algoEnum);
			}
			catch (IllegalArgumentException iae) {
				// Ignore it and continue the loop.
			}
		}
	}
}
