/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 * 
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.atalk.impl.androidcontacts;

import net.java.sip.communicator.service.contactsource.ContactSourceService;
import net.java.sip.communicator.util.SimpleServiceActivator;

/**
 * Activator of <tt>AndroidContactSource</tt> service.
 *
 * @author Pawel Domas
 */
public class AndroidContactsActivator extends SimpleServiceActivator<AndroidContactSource>
{

	public AndroidContactsActivator() {
		super(ContactSourceService.class, "Android contacts");
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	protected AndroidContactSource createServiceImpl()
	{
		return new AndroidContactSource();
	}
}
