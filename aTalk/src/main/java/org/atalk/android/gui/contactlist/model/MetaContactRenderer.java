/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.atalk.android.gui.contactlist.model;

import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;

import net.java.sip.communicator.service.contactlist.MetaContact;
import net.java.sip.communicator.service.protocol.Contact;
import net.java.sip.communicator.service.protocol.OperationSet;
import net.java.sip.communicator.service.protocol.OperationSetBasicTelephony;
import net.java.sip.communicator.service.protocol.OperationSetExtendedAuthorizations;
import net.java.sip.communicator.service.protocol.OperationSetExtendedAuthorizations.SubscriptionStatus;
import net.java.sip.communicator.service.protocol.OperationSetFileTransfer;
import net.java.sip.communicator.service.protocol.OperationSetVideoTelephony;
import net.java.sip.communicator.service.protocol.PresenceStatus;
import net.java.sip.communicator.util.StatusUtil;

import org.apache.commons.lang3.StringUtils;
import org.atalk.android.R;
import org.atalk.android.aTalkApp;
import org.atalk.android.gui.chat.ChatSessionManager;
import org.atalk.android.gui.util.AndroidImageUtil;
import org.atalk.android.gui.util.DrawableCache;
import org.atalk.impl.neomedia.device.util.AndroidCamera;
import org.jxmpp.jid.DomainBareJid;

import java.util.Iterator;

/**
 * Class used to obtain UI specific data for <code>MetaContact</code> instances.
 *
 * @author Pawel Domas
 * @author Eng Chong Meng
 */
public class MetaContactRenderer implements UIContactRenderer
{
    @Override
    public boolean isSelected(Object contactImpl)
    {
        return MetaContactListAdapter.isContactSelected((MetaContact) contactImpl);
    }

    @Override
    public String getDisplayName(Object contactImpl)
    {
        return ((MetaContact) contactImpl).getDisplayName();
    }

    @Override
    public String getStatusMessage(Object contactImpl)
    {
        MetaContact metaContact = (MetaContact) contactImpl;
        String displayDetails = getDisplayDetails(metaContact);
        return displayDetails != null ? displayDetails : "";
    }

    @Override
    public boolean isDisplayBold(Object contactImpl)
    {
        return ChatSessionManager.getActiveChat((MetaContact) contactImpl) != null;
    }

    @Override
    public Drawable getAvatarImage(Object contactImpl)
    {
        return getAvatarDrawable((MetaContact) contactImpl);
    }

    @Override
    public Drawable getStatusImage(Object contactImpl)
    {
        return getStatusDrawable((MetaContact) contactImpl);
    }

    @Override
    public boolean isShowVideoCallBtn(Object contactImpl)
    {
        // Disable video call option if there is no camera support on the device
        if (contactImpl instanceof MetaContact) {
            MetaContact metaContact = (MetaContact) contactImpl;
            Contact contact = metaContact.getDefaultContact();
            boolean isBlocked = (contact != null) && contact.isContactBlock();
            return !isBlocked
                    && isShowButton(metaContact, OperationSetVideoTelephony.class)
                    && (AndroidCamera.getCameras().length != 0);
        }
        return false;
    }

    @Override
    public boolean isShowCallBtn(Object contactImpl)
    {
        // Handle only if contactImpl instanceof MetaContact; DomainJid always show call button option
        if (contactImpl instanceof MetaContact) {
            MetaContact metaContact = (MetaContact) contactImpl;

            boolean isDomainJid = false;
            Contact contact = metaContact.getDefaultContact();
            boolean isBlocked = false;
            if (contact != null) {
                isDomainJid = contact.getJid() instanceof DomainBareJid;
                isBlocked = contact.isContactBlock();
            }
            return !isBlocked && (isDomainJid || isShowButton(metaContact, OperationSetBasicTelephony.class) );
        }
        return false;
    }

    @Override
    public boolean isShowFileSendBtn(Object contactImpl)
    {
        return isShowButton((MetaContact) contactImpl, OperationSetFileTransfer.class);
    }

    private boolean isShowButton(MetaContact metaContact, Class<? extends OperationSet> opSetClass)
    {
        return (metaContact.getOpSetSupportedContact(opSetClass) != null);
    }

    @Override
    public String getDefaultAddress(Object contactImpl)
    {
        return ((MetaContact) contactImpl).getDefaultContact().getAddress();
    }

    /**
     * Returns the display details for the underlying <code>MetaContact</code>.
     *
     * @param metaContact the <code>MetaContact</code>, which details we're looking for
     * @return the display details for the underlying <code>MetaContact</code>
     */
    private static String getDisplayDetails(MetaContact metaContact)
    {
        boolean subscribed = false;
        String displayDetails = null;
        String subscriptionDetails = null;

        Iterator<Contact> protoContacts = metaContact.getContacts();
        while (protoContacts.hasNext()) {
            Contact protoContact = protoContacts.next();
            OperationSetExtendedAuthorizations authOpSet
                    = protoContact.getProtocolProvider().getOperationSet(OperationSetExtendedAuthorizations.class);

            SubscriptionStatus status = authOpSet.getSubscriptionStatus(protoContact);
            if (!SubscriptionStatus.Subscribed.equals(status)) {
                if (SubscriptionStatus.SubscriptionPending.equals(status))
                    subscriptionDetails = aTalkApp.getResString(R.string.service_gui_WAITING_AUTHORIZATION);
                else if (SubscriptionStatus.NotSubscribed.equals(status))
                    subscriptionDetails = aTalkApp.getResString(R.string.service_gui_NOT_AUTHORIZED);
            }
            else if (StringUtils.isNotEmpty(protoContact.getStatusMessage())) {
                displayDetails = protoContact.getStatusMessage();
                subscribed = true;
                break;
            }
            else {
                subscribed = true;
            }
        }

        if (StringUtils.isEmpty(displayDetails) && !subscribed
                && StringUtils.isNotEmpty(subscriptionDetails))
            displayDetails = subscriptionDetails;

        return displayDetails;
    }

    /**
     * Returns the avatar <code>Drawable</code> for the given <code>MetaContact</code>.
     *
     * @param metaContact the <code>MetaContact</code>, which status drawable we're looking for
     * @return a <code>BitmapDrawable</code> object representing the status of the given <code>MetaContact</code>
     */
    public static BitmapDrawable getAvatarDrawable(MetaContact metaContact)
    {
        return getCachedAvatarFromBytes(metaContact.getAvatar());
    }

    /**
     * Returns avatar <code>BitmapDrawable</code> with rounded corners. Bitmap will be cached in app global drawable cache.
     *
     * @param avatar raw avatar image data.
     * @return avatar <code>BitmapDrawable</code> with rounded corners
     */
    public static BitmapDrawable getCachedAvatarFromBytes(byte[] avatar)
    {
        if (avatar == null)
            return null;

        String bmpKey = String.valueOf(avatar.hashCode());
        DrawableCache cache = aTalkApp.getImageCache();

        BitmapDrawable avatarImage = cache.getBitmapFromMemCache(bmpKey);
        if (avatarImage == null) {
            BitmapDrawable roundedAvatar = AndroidImageUtil.roundedDrawableFromBytes(avatar);
            if (roundedAvatar != null) {
                avatarImage = roundedAvatar;
                cache.cacheImage(bmpKey, avatarImage);
            }
        }
        return avatarImage;
    }

    /**
     * Returns the status <code>Drawable</code> for the given <code>MetaContact</code>.
     *
     * @param metaContact the <code>MetaContact</code>, which status drawable we're looking for
     * @return a <code>Drawable</code> object representing the status of the given <code>MetaContact</code>
     */
    public static Drawable getStatusDrawable(MetaContact metaContact)
    {
        byte[] statusImage = getStatusImage(metaContact);

        if ((statusImage != null) && (statusImage.length > 0))
            return AndroidImageUtil.drawableFromBytes(statusImage);

        return null;
    }

    /**
     * Returns the array of bytes representing the status image of the given <code>MetaContact</code>.
     *
     * @return the array of bytes representing the status image of the given <code>MetaContact</code>
     */
    private static byte[] getStatusImage(MetaContact metaContact)
    {
        PresenceStatus status = null;
        Iterator<Contact> contactsIter = metaContact.getContacts();
        while (contactsIter.hasNext()) {
            Contact protoContact = contactsIter.next();
            PresenceStatus contactStatus = protoContact.getPresenceStatus();
            if (status == null)
                status = contactStatus;
            else
                status = (contactStatus.compareTo(status) > 0) ? contactStatus : status;
        }
        return StatusUtil.getContactStatusIcon(status);
    }
}
