package net.java.sip.communicator.service.globaldisplaydetails.event;

import java.util.EventListener;

public interface GlobalDisplayDetailsListener extends EventListener
{
    void globalDisplayNameChanged(GlobalDisplayNameChangeEvent paramGlobalDisplayNameChangeEvent);

    void globalDisplayAvatarChanged(GlobalAvatarChangeEvent paramGlobalAvatarChangeEvent);
}
