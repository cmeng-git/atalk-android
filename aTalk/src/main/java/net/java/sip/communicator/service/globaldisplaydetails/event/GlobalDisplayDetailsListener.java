package net.java.sip.communicator.service.globaldisplaydetails.event;

import java.util.EventListener;

public abstract interface GlobalDisplayDetailsListener extends EventListener
{
	public abstract void globalDisplayNameChanged(GlobalDisplayNameChangeEvent paramGlobalDisplayNameChangeEvent);

	public abstract void globalDisplayAvatarChanged(GlobalAvatarChangeEvent paramGlobalAvatarChangeEvent);
}
