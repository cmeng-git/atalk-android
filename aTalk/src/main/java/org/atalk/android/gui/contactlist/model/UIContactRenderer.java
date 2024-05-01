/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.atalk.android.gui.contactlist.model;

import android.graphics.drawable.Drawable;

/**
 * Interface used to obtain data required to display contacts. Implementing classes can expect to receive their
 * implementation specific objects in calls to any method of this interface.
 *
 * @author Pawel Domas
 * @author Eng Chong Meng
 */
public interface UIContactRenderer {
    /**
     * Return <code>true</code> if given contact is considered to be currently selected.
     *
     * @param contactImpl contact instance.
     *
     * @return <code>true</code> if given contact is considered to be currently selected.
     */
    boolean isSelected(Object contactImpl);

    /**
     * Returns contact display name.
     *
     * @param contactImpl contact instance.
     *
     * @return contact display name.
     */
    String getDisplayName(Object contactImpl);

    /**
     * Returns contact status message.
     *
     * @param contactImpl contact instance.
     *
     * @return contact status message.
     */
    String getStatusMessage(Object contactImpl);

    /**
     * Returns <code>true</code> if given contact name should be displayed in bold.
     *
     * @param contactImpl contact instance.
     *
     * @return <code>true</code> if given contact name should be displayed in bold.
     */
    boolean isDisplayBold(Object contactImpl);

    /**
     * Returns contact avatar image.
     *
     * @param contactImpl contact instance.
     *
     * @return contact avatar image.
     */
    Drawable getAvatarImage(Object contactImpl);

    /**
     * Returns contact status image.
     *
     * @param contactImpl contact instance.
     *
     * @return contact status image.
     */
    Drawable getStatusImage(Object contactImpl);

    /**
     * Returns <code>true</code> if video call button should be displayed for given contact. That is if contact has valid
     * default address that can be used to make video calls.
     *
     * @param contactImpl contact instance.
     *
     * @return <code>true</code> if video call button should be displayed for given contact.
     */
    boolean isShowVideoCallBtn(Object contactImpl);

    /**
     * Returns <code>true</code> if call button should be displayed next to the contact. That means that it will returns
     * valid default address that can be used to make audio calls.
     *
     * @param contactImpl contact instance.
     *
     * @return <code>true</code> if call button should be displayed next to the contact.
     */
    boolean isShowCallBtn(Object contactImpl);

    boolean isShowFileSendBtn(Object contactImpl);

    /**
     * Returns default contact address that can be used to establish an outgoing connection.
     *
     * @param contactImpl contact instance.
     *
     * @return default contact address that can be used to establish an outgoing connection.
     */
    String getDefaultAddress(Object contactImpl);
}
