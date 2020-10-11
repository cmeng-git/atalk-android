/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package net.java.sip.communicator.impl.globaldisplaydetails;

import android.text.TextUtils;

import net.java.sip.communicator.service.globaldisplaydetails.GlobalDisplayDetailsService;
import net.java.sip.communicator.service.globaldisplaydetails.event.*;
import net.java.sip.communicator.service.protocol.*;
import net.java.sip.communicator.service.protocol.event.*;
import net.java.sip.communicator.util.account.AccountUtils;

import org.apache.commons.lang3.StringUtils;
import org.atalk.android.R;
import org.atalk.android.aTalkApp;
import org.jivesoftware.smackx.avatar.AvatarManager;
import org.jxmpp.jid.BareJid;

import java.util.*;

/**
 * The <tt>GlobalDisplayNameImpl</tt> offers generic access to a global display name for the local user.
 * <p>
 *
 * @author Yana Stamcheva
 * @author Hristo Terezov
 * @author Eng Chong Meng
 */
public class GlobalDisplayDetailsImpl implements GlobalDisplayDetailsService,
        RegistrationStateChangeListener, ServerStoredDetailsChangeListener, AvatarListener
{
    /**
     * Property to disable auto displayName update.
     */
    private static final String GLOBAL_DISPLAY_NAME_PROP = "gui.presence.GLOBAL_DISPLAY_NAME";

    /**
     * The display details listeners list.
     */
    private final List<GlobalDisplayDetailsListener> displayDetailsListeners = new ArrayList<>();

    /**
     * The current first name.
     */
    private String currentFirstName;

    /**
     * The current last name.
     */
    private String currentLastName;

    /**
     * The current display name.
     */
    private String currentDisplayName;

    /**
     * The provisioned display name.
     */
    private String provisionedDisplayName;

    /**
     * The global avatar.
     */
    private static byte[] globalAvatar;

    /**
     * The global display name.
     */
    private String globalDisplayName;

    /**
     * Creates an instance of <tt>GlobalDisplayDetailsImpl</tt>.
     */
    public GlobalDisplayDetailsImpl()
    {
        provisionedDisplayName
                = GlobalDisplayDetailsActivator.getConfigurationService().getString(GLOBAL_DISPLAY_NAME_PROP, null);

        for (ProtocolProviderService protocolProviderService : AccountUtils.getRegisteredProviders()) {
            protocolProviderService.addRegistrationStateChangeListener(this);
        }
    }

    /**
     * Returns default display name for the given provider or the user defined display name.
     *
     * @param pps the given protocol provider service
     * @return default display name.
     */
    public String getDisplayName(ProtocolProviderService pps)
    {
        // assume first registered provider if null;
        if (pps == null) {
            Collection<ProtocolProviderService> providers = AccountUtils.getRegisteredProviders();
            if ((providers == null) || (providers.size() == 0))
                return "";
            pps = ((List<ProtocolProviderService>) providers).get(0);
        }

        // proceed only if account has registered
        // cmeng (20200327) - vcard contains no fullName field so skip as it always returns null;
//        if (pps.isRegistered()) {
//            final OperationSetServerStoredAccountInfo accountInfoOpSet
//                    = pps.getOperationSet(OperationSetServerStoredAccountInfo.class);
//            if (accountInfoOpSet != null) {
//                String displayName = AccountInfoUtils.getDisplayName(accountInfoOpSet);
//                if (StringUtils.isNotEmpty(displayName)) {
//                    return displayName;
//                }
//            }
//        }
        return pps.getAccountID().getUserID();
    }

    /**
     * Returns the global avatar for the specified user.
     * Retrieve avatar via XEP-0084 and override vCard <photo/> content if avatarImage not null
     *
     * @return a byte array containing the global avatar for the local user
     */
    public byte[] getDisplayAvatar(ProtocolProviderService pps)
    {
        // assume first registered provider if null;
        if (pps == null) {
            Collection<ProtocolProviderService> providers = AccountUtils.getRegisteredProviders();
            if (providers.size() == 0)
                return null;
            pps = ((List<ProtocolProviderService>) providers).get(0);
        }

        BareJid userJid = pps.getAccountID().getBareJid();
        return AvatarManager.getAvatarImageByJid(userJid);
    }

    /**
     * Adds the given <tt>GlobalDisplayDetailsListener</tt> to listen for change events concerning
     * the global display details.
     *
     * @param l the <tt>GlobalDisplayDetailsListener</tt> to add
     */
    public void addGlobalDisplayDetailsListener(GlobalDisplayDetailsListener l)
    {
        synchronized (displayDetailsListeners) {
            if (!displayDetailsListeners.contains(l))
                displayDetailsListeners.add(l);
        }
    }

    /**
     * Removes the given <tt>GlobalDisplayDetailsListener</tt> listening for change events
     * concerning the global display details.
     *
     * @param l the <tt>GlobalDisplayDetailsListener</tt> to remove
     */
    public void removeGlobalDisplayDetailsListener(GlobalDisplayDetailsListener l)
    {
        synchronized (displayDetailsListeners) {
            displayDetailsListeners.remove(l);
        }
    }

    /**
     * Updates account information when a protocol provider is registered.
     *
     * @param evt the <tt>RegistrationStateChangeEvent</tt> that notified us of the change
     */
    public void registrationStateChanged(RegistrationStateChangeEvent evt)
    {
        ProtocolProviderService protocolProvider = evt.getProvider();
        if (evt.getNewState().equals(RegistrationState.REGISTERED)) {
            /*
             * Check the support for OperationSetServerStoredAccountInfo prior to starting the
             * Thread because only a couple of the protocols currently support it and thus
             * starting a Thread that is not going to do anything useful can be prevented.
             */
            OperationSetServerStoredAccountInfo accountInfoOpSet
                    = protocolProvider.getOperationSet(OperationSetServerStoredAccountInfo.class);
            if (accountInfoOpSet != null) {
                /*
                 * FIXME Starting a separate Thread for each ProtocolProviderService is
                 * uncontrollable because the application is multi-protocol and having multiple
                 * accounts is expected so one is likely to end up with a multitude of Threads.
                 * Besides, it not very  when retrieving the first and last name is to stop
                 * so one ProtocolProviderService being able to supply both the first and the
                 * last name may be overwritten by a ProtocolProviderService which is able to
                 * provide just one of them.
                 */
                new UpdateAccountInfo(protocolProvider, accountInfoOpSet, false).start();
            }

            OperationSetAvatar avatarOpSet
                    = protocolProvider.getOperationSet(OperationSetAvatar.class);
            if (avatarOpSet != null)
                avatarOpSet.addAvatarListener(this);

            OperationSetServerStoredAccountInfo serverStoredAccountInfo
                    = protocolProvider.getOperationSet(OperationSetServerStoredAccountInfo.class);
            if (serverStoredAccountInfo != null)
                serverStoredAccountInfo.addServerStoredDetailsChangeListener(this);
        }
        else if (evt.getNewState().equals(RegistrationState.UNREGISTERING)
                || evt.getNewState().equals(RegistrationState.CONNECTION_FAILED)) {
            OperationSetAvatar avatarOpSet
                    = protocolProvider.getOperationSet(OperationSetAvatar.class);
            if (avatarOpSet != null)
                avatarOpSet.removeAvatarListener(this);

            OperationSetServerStoredAccountInfo serverStoredAccountInfo
                    = protocolProvider.getOperationSet(OperationSetServerStoredAccountInfo.class);
            if (serverStoredAccountInfo != null)
                serverStoredAccountInfo.removeServerStoredDetailsChangeListener(this);
        }
    }

    /**
     * Called whenever a new avatar is defined for one of the protocols that we have subscribed
     * for.
     *
     * @param event the event containing the new image
     */
    public void avatarChanged(AvatarEvent event)
    {
        globalAvatar = event.getNewAvatar();
        // If there is no avatar image set, then displays the default one.
        if (globalAvatar == null) {
            globalAvatar = GlobalDisplayDetailsActivator.getResources()
                    .getImageInBytes(aTalkApp.getResString(R.string.service_gui_DEFAULT_USER_PHOTO));
        }

        // AvatarCacheUtils.cacheAvatar(event.getSourceProvider(), globalAvatar);
        BareJid userId = event.getSourceProvider().getAccountID().getBareJid();
        AvatarManager.addAvatarImage(userId, globalAvatar, false);
        fireGlobalAvatarEvent(globalAvatar);
    }

    /**
     * Registers a ServerStoredDetailsChangeListener with the operation sets of the providers, if
     * a provider change its name we use it in the UI.
     *
     * @param evt the <tt>ServerStoredDetailsChangeEvent</tt> the event for name change.
     */
    public void serverStoredDetailsChanged(ServerStoredDetailsChangeEvent evt)
    {
        if(StringUtils.isNotEmpty(provisionedDisplayName))
            return;

        if (((evt.getEventID() == ServerStoredDetailsChangeEvent.DETAIL_ADDED)
                || (evt.getEventID() == ServerStoredDetailsChangeEvent.DETAIL_REPLACED))
                && (evt.getNewValue() instanceof ServerStoredDetails.DisplayNameDetail
                || evt.getNewValue() instanceof ServerStoredDetails.ImageDetail)) {

            ProtocolProviderService protocolProvider = evt.getProvider();
            OperationSetServerStoredAccountInfo accountInfoOpSet
                    = protocolProvider.getOperationSet(OperationSetServerStoredAccountInfo.class);

            new UpdateAccountInfo(evt.getProvider(), accountInfoOpSet, true).start();
        }
    }

    /**
     * Queries the operations sets to obtain names and display info. Queries are done in separate thread.
     */
    private class UpdateAccountInfo extends Thread
    {
        /**
         * The protocol provider.
         */
        private ProtocolProviderService protocolProvider;

        /**
         * The account info operation set to query.
         */
        private OperationSetServerStoredAccountInfo accountInfoOpSet;

        /**
         * Indicates if the display name and avatar should be updated from this provider even if
         * they already have values.
         */
        private boolean isUpdate;

        /**
         * Constructs with provider and opSet to use.
         *
         * @param protocolProvider the provider.
         * @param accountInfoOpSet the opSet.
         * @param isUpdate indicates if the display name and avatar should be updated from this provider even
         * if they already have values.
         */
        UpdateAccountInfo(ProtocolProviderService protocolProvider,
                OperationSetServerStoredAccountInfo accountInfoOpSet, boolean isUpdate)
        {
            this.protocolProvider = protocolProvider;
            this.accountInfoOpSet = accountInfoOpSet;
            this.isUpdate = isUpdate;
        }

        @Override
        public void run()
        {
            // globalAvatar = AvatarCacheUtils.getCachedAvatar(protocolProvider);
            BareJid userId = protocolProvider.getAccountID().getBareJid();
            globalAvatar = AvatarManager.getAvatarImageByJid(userId);
            if ((isUpdate) || (globalAvatar == null)) {
                byte[] accountImage = AccountInfoUtils.getImage(accountInfoOpSet);
                if ((accountImage != null) && (accountImage.length > 0)) {
                    globalAvatar = accountImage;
                    // AvatarCacheUtils.cacheAvatar(protocolProvider, globalAvatar);
                    AvatarManager.addAvatarImage(userId, globalAvatar, false);
                }
                else {
                    globalAvatar = new byte[0];
                }
                fireGlobalAvatarEvent(globalAvatar);
            }

            if (!isUpdate || (!TextUtils.isEmpty(provisionedDisplayName)
                    && StringUtils.isNotEmpty(globalDisplayName)))
                return;

            if (currentFirstName == null) {
                String firstName = AccountInfoUtils.getFirstName(accountInfoOpSet);

                if (StringUtils.isNotEmpty(firstName)) {
                    currentFirstName = firstName;
                }
            }

            if (currentLastName == null) {
                String lastName = AccountInfoUtils.getLastName(accountInfoOpSet);

                if (StringUtils.isNotEmpty(lastName)) {
                    currentLastName = lastName;
                }
            }

            if (currentFirstName == null && currentLastName == null) {
                String displayName = AccountInfoUtils.getDisplayName(accountInfoOpSet);

                if (displayName != null)
                    currentDisplayName = displayName;
            }
            setGlobalDisplayName();
        }

        /**
         * Called on the event dispatching thread (not on the worker thread) after the
         * <code>construct</code> method has returned.
         */
        protected void setGlobalDisplayName()
        {
            String accountName = null;
            if (StringUtils.isNotEmpty(currentFirstName)) {
                accountName = currentFirstName;
            }

            if (StringUtils.isNotEmpty(currentLastName)) {
                /*
                 * If accountName is null, don't use += because it will make the accountName start
                 * with the string "null".
                 */
                if (StringUtils.isEmpty(accountName))
                    accountName = currentLastName;
                else
                    accountName += " " + currentLastName;
            }

            if (currentFirstName == null && currentLastName == null) {
                if (currentDisplayName != null)
                    accountName = currentDisplayName;
            }

            globalDisplayName = accountName;
            if (StringUtils.isNotEmpty(globalDisplayName)) {
                fireGlobalDisplayNameEvent(globalDisplayName);
            }
        }
    }

    /**
     * Notifies all interested listeners of a global display details change.
     *
     * @param displayName the new display name
     */
    private void fireGlobalDisplayNameEvent(String displayName)
    {
        List<GlobalDisplayDetailsListener> listeners;
        synchronized (displayDetailsListeners) {
            listeners = Collections.unmodifiableList(displayDetailsListeners);
        }

        Iterator<GlobalDisplayDetailsListener> listIter = listeners.iterator();
        while (listIter.hasNext()) {
            listIter.next().globalDisplayNameChanged(
                    new GlobalDisplayNameChangeEvent(this, displayName));
        }
    }

    /**
     * Notifies all interested listeners of a global display details change.
     *
     * @param avatar the new avatar
     */
    private void fireGlobalAvatarEvent(byte[] avatar)
    {
        List<GlobalDisplayDetailsListener> listeners;
        synchronized (displayDetailsListeners) {
            listeners = Collections.unmodifiableList(displayDetailsListeners);
        }

        for (GlobalDisplayDetailsListener listener : listeners) {
            listener.globalDisplayAvatarChanged(
                    new GlobalAvatarChangeEvent(this, avatar));
        }
    }
}
