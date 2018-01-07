package net.java.sip.communicator.service.globaldisplaydetails.event;

import java.util.EventObject;

public class GlobalAvatarChangeEvent extends EventObject
{
	private static final long serialVersionUID = 1L;
	private byte[] avatar;

	public GlobalAvatarChangeEvent(Object source, byte[] newAvatar)
	{
		super(source);

		this.avatar = newAvatar;
	}

	public byte[] getNewAvatar()
	{
		return this.avatar;
	}
}
