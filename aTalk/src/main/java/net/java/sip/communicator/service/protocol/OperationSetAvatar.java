/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package net.java.sip.communicator.service.protocol;

import net.java.sip.communicator.service.protocol.event.AvatarListener;

/**
 * This interface is an extension of the operation set, meant to be implemented by protocols that
 * support user avatar.
 *
 * @author Damien Roth
 * @author Eng Chong Meng
 */
public interface OperationSetAvatar extends OperationSet {
    /**
     * Returns the maximum width of the avatar. This method should return 0 (zero) if there is no
     * maximum width.
     *
     * @return the maximum width of the avatar
     */
    int getMaxWidth();

    /**
     * Returns the maximum height of the avatar. This method should return 0 (zero) if there is no
     * maximum height.
     *
     * @return the maximum height of the avatar
     */
    int getMaxHeight();

    /**
     * Returns the maximum size of the avatar. This method should return 0 (zero) if there is no
     * maximum size.
     *
     * @return the maximum size of the avatar
     */
    int getMaxSize();

    /**
     * Fire avatar change to all listeners on new avatar received.
     *
     * @param avatar the new avatar
     */
    void fireAvatarChanged(byte[] avatar);

    /**
     * Defines a new avatar for this protocol
     *
     * @param avatar the new avatar
     */
    void setAvatar(byte[] avatar);

    /**
     * Returns the current avatar of this protocol. May return null if the account has no avatar
     *
     * @return avatar's bytes or null if no avatar set
     */
    byte[] getAvatar();

    /**
     * Registers a listener that would receive events upon avatar changes.
     *
     * @param listener a AvatarListener that would receive events upon avatar changes.
     */
    void addAvatarListener(AvatarListener listener);

    /**
     * Removes the specified group change listener so that it won't receive any further events.
     *
     * @param listener the VCardAvatarListener to remove
     */
    void removeAvatarListener(AvatarListener listener);
}
