/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package net.java.sip.communicator.impl.protocol.jabber;

import net.java.sip.communicator.service.protocol.AbstractOperationSetAvatar;
import net.java.sip.communicator.service.protocol.OperationSetServerStoredAccountInfo;

/**
 * A simple implementation of the <code>OperationSetAvatar</code> interface for the jabber protocol.
 * Actually there isn't any maximum size for the jabber protocol but GoogleTalk fix it a 96x96.
 *
 * @author Damien Roth
 */
public class OperationSetAvatarJabberImpl extends
        AbstractOperationSetAvatar<ProtocolProviderServiceJabberImpl> {

    /**
     * Creates a new instances of <code>OperationSetAvatarJabberImpl</code>.
     *
     * @param parentProvider a reference to the <code>ProtocolProviderServiceJabberImpl</code> instance that created us.
     * @param accountInfoOpSet a reference to the <code>OperationSetServerStoredAccountInfo</code>.
     */
    public OperationSetAvatarJabberImpl(ProtocolProviderServiceJabberImpl parentProvider,
            OperationSetServerStoredAccountInfo accountInfoOpSet) {
        super(parentProvider, accountInfoOpSet, 96, 96, 0);
    }
}
