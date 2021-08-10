/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.atalk.android.gui.account;

import android.content.Context;
import android.graphics.drawable.Drawable;

import net.java.sip.communicator.service.protocol.*;
import net.java.sip.communicator.service.protocol.event.*;
import net.java.sip.communicator.service.protocol.globalstatus.GlobalStatusEnum;
import net.java.sip.communicator.util.UtilActivator;
import net.java.sip.communicator.util.account.AccountUtils;

import org.atalk.android.gui.util.AccountUtil;
import org.atalk.android.gui.util.AndroidImageUtil;
import org.atalk.android.gui.util.event.EventListener;
import org.atalk.android.gui.util.event.EventListenerList;
import org.atalk.android.plugin.timberlog.TimberLog;
import org.atalk.service.osgi.OSGiActivity;
import org.atalk.service.resources.ResourceManagementService;
import org.jxmpp.jid.Jid;
import org.jxmpp.jid.impl.JidCreate;
import org.jxmpp.stringprep.XmppStringprepException;
import org.osgi.framework.*;

import java.beans.PropertyChangeEvent;
import java.io.*;

import timber.log.Timber;

/**
 * Class exposes account information for specified {@link AccountID} in a form that can be easily
 * used for building GUI. It tracks changes of {@link PresenceStatus}, {@link RegistrationState}
 * and avatar changes and  passes them as an {@link AccountEvent} to registered
 * {@link EventListener}s.<br/>
 * It also provides default values for fields that may be currently unavailable from
 * corresponding {@link OperationSet} or {@link ProtocolProviderService}.
 *
 * @author Pawel Domas
 * @author Eng Chong Meng
 */
public class Account implements ProviderPresenceStatusListener, RegistrationStateChangeListener,
        ServiceListener, AvatarListener
{
    /**
     * Unique identifier for the account
     */
    public static final String UUID = "uuid";

    /**
     * The {@link ProtocolProviderService} if is currently available
     */
    private ProtocolProviderService protocolProvider;

    /**
     * The encapsulated {@link AccountID}
     */
    private final AccountID mAccountID;

    /**
     * The {@link BundleContext} of parent {@link OSGiActivity}
     */
    private final BundleContext bundleContext;

    /**
     * The {@link Context} of parent {@link android.app.Activity}
     */
    private final Context activityContext;

    /**
     * List of {@link EventListener}s that listen for {@link AccountEvent}s.
     */
    private EventListenerList<AccountEvent> listeners = new EventListenerList<>();

    /**
     * The {@link Drawable} representing protocol's image
     */
    private Drawable protocolIcon;

    /**
     * Current avatar image
     */
    private Drawable avatarIcon;

    /**
     * Creates new instance of {@link Account}
     *
     * @param accountID the {@link AccountID} that will be encapsulated by this class
     * @param context the {@link BundleContext} of parent {@link OSGiActivity}
     * @param activityContext the {@link Context} of parent {@link android.app.Activity}
     */
    public Account(AccountID accountID, BundleContext context, Context activityContext)
    {
        mAccountID = accountID;
        setProtocolProvider(AccountUtils.getRegisteredProviderForAccount(accountID));

        this.bundleContext = context;
        this.bundleContext.addServiceListener(this);

        this.activityContext = activityContext;
        this.protocolIcon = initProtocolIcon();
    }

    /**
     * Tries to retrieve the protocol's icon
     *
     * @return protocol's icon
     */
    private Drawable initProtocolIcon()
    {
        byte[] blob = null;

        if (protocolProvider != null)
            blob = protocolProvider.getProtocolIcon().getIcon(ProtocolIcon.ICON_SIZE_32x32);

        if (blob != null)
            return AndroidImageUtil.drawableFromBytes(blob);

        String iconPath = mAccountID.getAccountPropertyString(
                ProtocolProviderFactory.ACCOUNT_ICON_PATH);

        if (iconPath != null) {
            blob = loadIcon(iconPath);
            if (blob != null)
                return AndroidImageUtil.drawableFromBytes(blob);
        }

        return null;
    }

    /**
     * Loads an image from a given image path.
     *
     * @param imagePath The identifier of the image.
     * @return The image for the given identifier.
     */
    public static byte[] loadIcon(String imagePath)
    {
        ResourceManagementService resources = UtilActivator.getResources();
        byte[] icon = null;

        if (resources != null) {
            InputStream is = resources.getImageInputStreamForPath(imagePath);

            if (is == null)
                return null;

            try {
                ByteArrayOutputStream bout = new ByteArrayOutputStream();
                byte[] buffer = new byte[1024];
                int read;
                while (-1 != (read = is.read(buffer))) {
                    bout.write(buffer, 0, read);
                }
                icon = bout.toByteArray();
            } catch (IOException ioex) {
                Timber.e(ioex, "Failed to load protocol icon: %s", imagePath);
            }
        }
        return icon;
    }

    /**
     * Gets the {@link ProtocolProviderService} for encapsulated {@link AccountID}
     *
     * @return the {@link ProtocolProviderService} if currently registered for encapsulated
     */
    public ProtocolProviderService getProtocolProvider()
    {
        return protocolProvider;
    }

    /**
     * Tries to get the {@link OperationSetPresence} for encapsulated {@link AccountID}
     *
     * @return the {@link OperationSetPresence} if the protocol is active and supports it or
     * <tt>null</tt> otherwise
     */
    OperationSetPresence getPresenceOpSet()
    {
        if (protocolProvider == null)
            return null;

        return protocolProvider.getOperationSet(OperationSetPresence.class);
    }

    /**
     * Tries to get the {@link OperationSetAvatar} if the protocol supports it and is currently
     * active
     *
     * @return the {@link OperationSetAvatar} for encapsulated {@link AccountID} if it's supported
     * and active or
     * <tt>null</tt> otherwise
     */
    OperationSetAvatar getAvatarOpSet()
    {
        if (protocolProvider == null)
            return null;

        return protocolProvider.getOperationSet(OperationSetAvatar.class);
    }

    /**
     * Tracks the de/registration of {@link ProtocolProviderService} for encapsulated
     * {@link AccountID}
     *
     * @param event the {@link ServiceEvent}
     */
    public void serviceChanged(ServiceEvent event)
    {
        // if the event is caused by a bundle being stopped, we don't want to
        // know
        if (event.getServiceReference().getBundle().getState() == Bundle.STOPPING) {
            return;
        }
        Object sourceService = bundleContext.getService(event.getServiceReference());

        // we don't care if the source service is not a protocol provider
        if (!(sourceService instanceof ProtocolProviderService)) {
            return;
        }

        ProtocolProviderService protocolProvider = (ProtocolProviderService) sourceService;
        if (!protocolProvider.getAccountID().equals(mAccountID)) {
            // Only interested for this account
            return;
        }

        if (event.getType() == ServiceEvent.REGISTERED) {
            setProtocolProvider(protocolProvider);
        }
        else if (event.getType() == ServiceEvent.UNREGISTERING) {
            setProtocolProvider(null);
        }
    }

    /**
     * Sets the currently active {@link ProtocolProviderService} for encapsulated
     * {@link #mAccountID}.
     *
     * @param protocolProvider if not <tt>null</tt> all listeners are registered otherwise listeners are unregistered
     * from current
     * {@link #protocolProvider}
     */
    private void setProtocolProvider(ProtocolProviderService protocolProvider)
    {
        if (this.protocolProvider != null && protocolProvider != null) {
            if (this.protocolProvider == protocolProvider) {
                // It's the same
                return;
            }

            Timber.w("This account have already registered provider - will update");
            // Unregister old
            setProtocolProvider(null);
            // Register new
            setProtocolProvider(protocolProvider);
        }

        if (protocolProvider != null) {
            protocolProvider.addRegistrationStateChangeListener(this);

            OperationSetPresence presenceOpSet = protocolProvider.getOperationSet(
                    OperationSetPresence.class);
            if (presenceOpSet == null) {
                Timber.w("%s does not support presence operations", protocolProvider.getProtocolDisplayName());
            }
            else {
                presenceOpSet.addProviderPresenceStatusListener(this);
            }

            OperationSetAvatar avatarOpSet = protocolProvider.getOperationSet(
                    OperationSetAvatar.class);
            if (avatarOpSet != null) {
                avatarOpSet.addAvatarListener(this);
            }

            Timber.d("Registered listeners for %s", protocolProvider);
        }
        else if (this.protocolProvider != null) {
            // Unregister listeners
            this.protocolProvider.removeRegistrationStateChangeListener(this);

            OperationSetPresence presenceOpSet = this.protocolProvider.getOperationSet(
                    OperationSetPresence.class);
            if (presenceOpSet != null) {
                presenceOpSet.removeProviderPresenceStatusListener(this);
            }

            OperationSetAvatar avatarOpSet = this.protocolProvider.getOperationSet(
                    OperationSetAvatar.class);
            if (avatarOpSet != null) {
                avatarOpSet.removeAvatarListener(this);
            }
        }
        this.protocolProvider = protocolProvider;
    }

    /**
     * Unregisters from all services and clears {@link #listeners}
     */
    public void destroy()
    {
        setProtocolProvider(null);
        bundleContext.removeServiceListener(this);
        listeners.clear();
    }

    /**
     * Adds {@link EventListener} that will be listening for changed that occurred to this
     * {@link Account}. In particular these are the registration status, presence status and
     * avatar events.
     *
     * @param listener the {@link EventListener} that listens for changes on this {@link Account} object
     */
    public void addAccountEventListener(EventListener<AccountEvent> listener)
    {
        Timber.log(TimberLog.FINER, "Added change listener %s", listener);
        listeners.addEventListener(listener);
    }

    /**
     * Removes the given <tt>listener</tt> from observers list
     *
     * @param listener the {@link EventListener} that doesn't want to be notified about the changes to this
     * {@link Account} anymore
     */
    public void removeAccountEventListener(EventListener<AccountEvent> listener)
    {
        Timber.log(TimberLog.FINER, "Removed change listener %s", listener);
        listeners.removeEventListener(listener);
    }

    public void providerStatusChanged(ProviderPresenceStatusChangeEvent evt)
    {
        Timber.log(TimberLog.FINER, "Provider status notification");
        listeners.notifyEventListeners(new AccountEvent(this, AccountEvent.PRESENCE_STATUS_CHANGE));
    }

    public void providerStatusMessageChanged(PropertyChangeEvent evt)
    {
        Timber.log(TimberLog.FINER, "Provider status msg notification");
        listeners.notifyEventListeners(new AccountEvent(this, AccountEvent.STATUS_MSG_CHANGE));
    }

    public void registrationStateChanged(RegistrationStateChangeEvent evt)
    {
        Timber.log(TimberLog.FINER, "Provider registration notification");
        listeners.notifyEventListeners(new AccountEvent(this, AccountEvent.REGISTRATION_CHANGE));
    }

    public void avatarChanged(AvatarEvent event)
    {
        Timber.log(TimberLog.FINER, "Avatar changed notification");
        updateAvatar(event.getNewAvatar());
        listeners.notifyEventListeners(new AccountEvent(this, AccountEvent.AVATAR_CHANGE));
    }

    /**
     * Returns the display name
     *
     * @return the display name of this {@link Account}
     */
    public String getAccountName()
    {
        return mAccountID.getDisplayName();
    }

    /**
     * Returns the current presence status name of this {@link Account}
     *
     * @return current presence status name
     */
    public String getStatusName()
    {
        OperationSetPresence presence = getPresenceOpSet();
        if (presence != null) {
            return presence.getPresenceStatus().getStatusName();
        }
        return GlobalStatusEnum.OFFLINE_STATUS;
    }

    /**
     * Returns the {@link Drawable} protocol icon
     *
     * @return the protocol's icon valid for this {@link Account}
     */
    public Drawable getProtocolIcon()
    {
        return protocolIcon;
    }

    /**
     * Returns the current {@link PresenceStatus} icon
     *
     * @return the icon describing actual {@link PresenceStatus} of this {@link Account}
     */
    public Drawable getStatusIcon()
    {
        OperationSetPresence presence = getPresenceOpSet();

        if (presence != null) {
            byte[] statusBlob = presence.getPresenceStatus().getStatusIcon();

            if (statusBlob != null)
                return AndroidImageUtil.drawableFromBytes(statusBlob);
        }

        return AccountUtil.getDefaultPresenceIcon(activityContext, mAccountID.getProtocolName());
    }

    /**
     * Returns <tt>true</tt> if this {@link Account} is enabled
     *
     * @return <tt>true</tt> if this {@link Account} is enabled
     */
    boolean isEnabled()
    {
        return mAccountID.isEnabled();
    }

    /**
     * Returns encapsulated {@link AccountID}
     *
     * @return the {@link AccountID} encapsulated by this instance of {@link Account}
     */
    public AccountID getAccountID()
    {
        return mAccountID;
    }

    /**
     * Returns the user id (Jid) associated with this account e.g. abc123@example.org.
     *
     * @return A String identifying the user inside this particular service.
     */
    public String getUserID()
    {
        return mAccountID.getUserID();
    }

    public Jid getJid()
    {
        Jid jid = null;
        try {
            jid = JidCreate.from(mAccountID.getUserID());
        } catch (XmppStringprepException | IllegalArgumentException e) {
            e.printStackTrace();
        }
        return jid;
    }

    /**
     * Sets the avatar icon. If <tt>newAvatar</tt> is specified as <tt>null</tt> the default one
     * is set
     *
     * @param newAvatar an array of bytes with raw avatar image data
     */
    private void updateAvatar(byte[] newAvatar)
    {
        if (newAvatar == null) {
            avatarIcon = AccountUtil.getDefaultAvatarIcon(activityContext);
        }
        else {
            avatarIcon = AndroidImageUtil.drawableFromBytes(newAvatar);
        }
    }

    /**
     * Returns the {@link Drawable} of account avatar
     *
     * @return the {@link Drawable} of account avatar
     */
    public Drawable getAvatarIcon()
    {
        if (avatarIcon == null) {
            byte[] avatarBlob = null;

            try {
                OperationSetAvatar avatarOpSet = getAvatarOpSet();
                if (avatarOpSet != null) {
                    avatarBlob = avatarOpSet.getAvatar();
                }
            } catch (IllegalStateException exc) {
                Timber.e("Error retrieving avatar: %s", exc.getMessage());
            }
            updateAvatar(avatarBlob);
        }
        return avatarIcon;
    }
}