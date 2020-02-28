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

	public static ZrtpConfigure getZrtpConfiguration()
	{
		ZrtpConfigure active = new ZrtpConfigure();

		active.addAlgo(ZrtpConstants.SupportedHashes.S384);
		active.addAlgo(ZrtpConstants.SupportedSymCiphers.AES3);
		active.addAlgo(ZrtpConstants.SupportedSymCiphers.TWO3);
		active.addAlgo(ZrtpConstants.SupportedPubKeys.E255);
		active.addAlgo(ZrtpConstants.SupportedPubKeys.MULT);
		active.addAlgo(ZrtpConstants.SupportedAuthLengths.HS80);
		active.addAlgo(ZrtpConstants.SupportedAuthLengths.SK64);

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
		String savedAlgos[] = savedConf.split(";");

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
