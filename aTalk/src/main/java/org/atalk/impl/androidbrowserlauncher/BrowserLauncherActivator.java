/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 * 
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.atalk.impl.androidbrowserlauncher;

import net.java.sip.communicator.service.browserlauncher.BrowserLauncherService;
import net.java.sip.communicator.util.SimpleServiceActivator;

/**
 * Browser launcher bundle activator.
 *
 * @author Pawel Domas
 */
public class BrowserLauncherActivator extends SimpleServiceActivator
{
	public BrowserLauncherActivator() {
		super(BrowserLauncherService.class, "Android Browser Launcher");
	}

	@Override
	protected Object createServiceImpl()
	{
		return new AndroidBrowserLauncher();
	}
}
