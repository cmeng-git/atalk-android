package net.java.sip.communicator.service.globaldisplaydetails.event;

import java.util.EventObject;

public class GlobalDisplayNameChangeEvent extends EventObject
{
	private static final long serialVersionUID = 1L;
	private String displayName;

	public GlobalDisplayNameChangeEvent(Object source, String newDisplayName)
	{
		super(source);

		this.displayName = newDisplayName;
	}

	public String getNewDisplayName()
	{
		return this.displayName;
	}
}
