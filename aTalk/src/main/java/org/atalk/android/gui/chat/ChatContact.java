/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.atalk.android.gui.chat;

import android.graphics.drawable.Drawable;

import org.atalk.android.gui.util.AndroidImageUtil;

/**
 * The <code>ChatContact</code> is a wrapping class for the <code>Contact</code> and <code>ChatRoomMember</code> interface.
 *
 * @param <T> the type of the descriptor
 * @author Yana Stamcheva
 * @author Lubomir Marinov
 */
public abstract class ChatContact<T>
{
    /**
     * The height of the avatar icon.
     */
    public static final int AVATAR_ICON_HEIGHT = 25;

    /**
     * The width of the avatar icon.
     */
    public static final int AVATAR_ICON_WIDTH = 25;

    /**
     * The avatar image corresponding to the source contact in the form of an {@code ImageIcon}.
     */
    private Drawable avatar;

    /**
     * The avatar image corresponding to the source contact in the form of an array of bytes.
     */
    private byte[] avatarBytes;

    /**
     * The descriptor being adapted by this instance.
     */
    protected final T descriptor;

    /**
     * If this instance is selected.
     */
    private boolean selected;

    /**
     * Initializes a new <code>ChatContact</code> instance with a specific descriptor.
     *
     * @param descriptor the descriptor to be adapted by the new instance
     */
    protected ChatContact(T descriptor)
    {
        this.descriptor = descriptor;
    }

    /**
     * Determines whether a specific <code>Object</code> represents the same value as this
     * <code>ChatContact</code>.
     *
     * @param obj the <code>Object</code> to be checked for value equality with this <code>ChatContact</code>
     * @return <code>true</code> if <code>obj</code> represents the same value as this
     * <code>ChatContact</code>; otherwise, <code>false</code>.
     */
    @Override
    public boolean equals(Object obj)
    {
        if (this == obj)
            return true;

        /*
         * ChatContact is an adapter so two ChatContacts of the same runtime type with equal descriptors are equal.
         */
        if (!getClass().isInstance(obj))
            return false;

        @SuppressWarnings("unchecked")
        ChatContact<T> chatContact = (ChatContact<T>) obj;

        return getDescriptor().equals(chatContact.getDescriptor());
    }

    /**
     * Returns the avatar image corresponding to the source contact. In the case of multi user
     * chat contact returns null.
     *
     * @return the avatar image corresponding to the source contact. In the case of multi user
     * chat contact returns null
     */
    public Drawable getAvatar()
    {
        byte[] avatarBytes = getAvatarBytes();

        if (this.avatarBytes != avatarBytes) {
            this.avatarBytes = avatarBytes;
            this.avatar = null;
        }
        if ((this.avatar == null) && (this.avatarBytes != null) && (this.avatarBytes.length > 0))
            this.avatar = AndroidImageUtil.getScaledRoundedIcon(this.avatarBytes,
                    AVATAR_ICON_WIDTH, AVATAR_ICON_HEIGHT);
        return this.avatar;
    }

    /**
     * Gets the avatar image corresponding to the source contact in the form of an array of bytes.
     *
     * @return an array of bytes which represents the avatar image corresponding to the source contact
     */
    protected abstract byte[] getAvatarBytes();

    /**
     * Returns the descriptor object corresponding to this chat contact. In the case of single chat this could
     * be the <code>MetaContact</code> and in the case of conference chat this could be the <code>ChatRoomMember</code>.
     *
     * @return the descriptor object corresponding to this chat contact.
     */
    public T getDescriptor()
    {
        return descriptor;
    }

    /**
     * Returns the contact name.
     *
     * @return the contact name
     */
    public abstract String getName();

    /**
     * Gets the implementation-specific identifier which uniquely specifies this contact.
     *
     * @return an identifier which uniquely specifies this contact
     */
    public abstract String getUID();

    /**
     * Gets a hash code value for this object for the benefit of hashTables.
     *
     * @return a hash code value for this object
     */
    @Override
    public int hashCode()
    {
        /*
         * ChatContact is an adapter so two ChatContacts of the same runtime type with equal descriptors are equal.
         */
        return getDescriptor().hashCode();
    }

    /**
     * Returns {@code true} if this is the currently selected contact in the list of contacts
     * for the chat, otherwise returns {@code false}.
     *
     * @return {@code true} if this is the currently selected contact in the list of contacts
     * for the chat, otherwise returns {@code false}.
     */
    public boolean isSelected()
    {
        return selected;
    }

    /**
     * Sets this isSelected property of this chat contact.
     *
     * @param selected {@code true} to indicate that this contact would be the selected contact in the
     * list of chat window contacts; otherwise, {@code false}
     */
    public void setSelected(boolean selected)
    {
        this.selected = selected;
    }
}
