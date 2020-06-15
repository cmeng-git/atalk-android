/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.atalk.android.gui.account;

import android.graphics.drawable.Drawable;

import net.java.sip.communicator.impl.muc.MUCActivator;
import net.java.sip.communicator.service.globaldisplaydetails.GlobalDisplayDetailsService;
import net.java.sip.communicator.service.muc.MUCService;
import net.java.sip.communicator.service.protocol.*;
import net.java.sip.communicator.service.protocol.event.*;
import net.java.sip.communicator.service.protocol.globalstatus.GlobalStatusEnum;
import net.java.sip.communicator.service.protocol.globalstatus.GlobalStatusService;
import net.java.sip.communicator.util.StatusUtil;
import net.java.sip.communicator.util.account.*;

import org.atalk.android.R;
import org.atalk.android.aTalkApp;
import org.atalk.android.gui.AndroidGUIActivator;
import org.atalk.android.gui.authorization.AuthorizationHandlerImpl;
import org.atalk.android.gui.call.AndroidCallListener;
import org.atalk.android.gui.chat.ChatSessionManager;
import org.atalk.android.gui.dialogs.DialogActivity;
import org.atalk.android.gui.util.AndroidImageUtil;
import org.atalk.android.gui.util.AndroidUtils;
import org.atalk.android.gui.util.event.EventListener;
import org.atalk.android.gui.util.event.EventListenerList;
import org.atalk.service.osgi.OSGiService;

import java.beans.PropertyChangeEvent;

/**
 * The <tt>AndroidLoginRenderer</tt> is the Android renderer for login events.
 *
 * @author Yana Stamcheva
 * @author Pawel Domas
 * @author Eng Chong Meng
 */
public class AndroidLoginRenderer implements LoginRenderer
{
    /**
     * The <tt>CallListener</tt>.
     */
    private CallListener androidCallListener;

    /**
     * The android implementation of the provider presence listener.
     */
    private final ProviderPresenceStatusListener androidPresenceListener = new UIProviderPresenceStatusListener();

    /**
     * The security authority used by this login renderer.
     */
    private final SecurityAuthority mSecurityAuthority;

    /**
     * Authorization handler instance.
     */
    private final AuthorizationHandlerImpl authorizationHandler;

    /**
     * Cached global status value
     */
    private PresenceStatus globalStatus;

    /**
     * List of global status listeners.
     */
    private EventListenerList<PresenceStatus> globalStatusListeners = new EventListenerList<>();

    /**
     * Caches avatar image to track the changes
     */
    private byte[] localAvatarRaw;

    /**
     * Local avatar drawable
     */
    private Drawable localAvatar;

    /**
     * Caches local status to track the changes
     */
    private byte[] localStatusRaw;

    /**
     * Local status drawable
     */
    private Drawable localStatusDrawable;

    /**
     * Creates an instance of <tt>AndroidLoginRenderer</tt> by specifying the current <tt>Context</tt>.
     *
     * @param defaultSecurityAuthority the security authority that will be used by this login renderer
     */
    public AndroidLoginRenderer(SecurityAuthority defaultSecurityAuthority)
    {
        androidCallListener = new AndroidCallListener();
        mSecurityAuthority = defaultSecurityAuthority;
        authorizationHandler = new AuthorizationHandlerImpl();
    }

    /**
     * Adds the user interface related to the given protocol provider.
     *
     * @param protocolProvider the protocol provider for which we add the user interface
     */
    public void addProtocolProviderUI(ProtocolProviderService protocolProvider)
    {
        OperationSetBasicTelephony<?> telOpSet = protocolProvider.getOperationSet(OperationSetBasicTelephony.class);
        if (telOpSet != null) {
            telOpSet.addCallListener(androidCallListener);
        }

        OperationSetPresence presenceOpSet = protocolProvider.getOperationSet(OperationSetPresence.class);
        if (presenceOpSet != null) {
            presenceOpSet.addProviderPresenceStatusListener(androidPresenceListener);
        }
    }

    /**
     * Removes the user interface related to the given protocol provider.
     *
     * @param protocolProvider the protocol provider to remove
     */
    public void removeProtocolProviderUI(ProtocolProviderService protocolProvider)
    {
        OperationSetBasicTelephony<?> telOpSet = protocolProvider.getOperationSet(OperationSetBasicTelephony.class);
        if (telOpSet != null) {
            telOpSet.removeCallListener(androidCallListener);
        }

        OperationSetPresence presenceOpSet = protocolProvider.getOperationSet(OperationSetPresence.class);
        if (presenceOpSet != null) {
            presenceOpSet.removeProviderPresenceStatusListener(androidPresenceListener);
        }

        // Removes all chat session for unregistered provider
        ChatSessionManager.removeAllChatsForProvider(protocolProvider);
    }

    /**
     * Starts the connecting user interface for the given protocol provider.
     *
     * @param protocolProvider the protocol provider for which we add the connecting user interface
     */
    public void startConnectingUI(ProtocolProviderService protocolProvider)
    {
    }

    /**
     * Stops the connecting user interface for the given protocol provider.
     *
     * @param protocolProvider the protocol provider for which we remove the connecting user interface
     */
    public void stopConnectingUI(ProtocolProviderService protocolProvider)
    {
    }

    /**
     * Indicates that the given protocol provider has been connected at the given time.
     *
     * @param protocolProvider the <tt>ProtocolProviderService</tt> corresponding to the connected account
     * @param date the date/time at which the account has connected
     */
    public void protocolProviderConnected(ProtocolProviderService protocolProvider, long date)
    {
        OperationSetPresence presence = AccountStatusUtils.getProtocolPresenceOpSet(protocolProvider);
        if (presence != null) {
            presence.setAuthorizationHandler(authorizationHandler);
        }

        OperationSetMultiUserChat multiUserChat = MUCService.getMultiUserChatOpSet(protocolProvider);
        MUCService mucService;
        if ((multiUserChat != null)
                && (mucService = MUCActivator.getMUCService()) != null) {
            mucService.synchronizeOpSetWithLocalContactList(protocolProvider, multiUserChat);
        }
        updateGlobalStatus();
    }

    /**
     * Indicates that a protocol provider connection has failed.
     *
     * @param protocolProvider the <tt>ProtocolProviderService</tt>, which connection failed
     * @param loginManagerCallback the <tt>LoginManager</tt> implementation, which is managing the process
     */
    public void protocolProviderConnectionFailed(final ProtocolProviderService protocolProvider,
            final LoginManager loginManagerCallback)
    {
        AccountID accountID = protocolProvider.getAccountID();
        AndroidUtils.showAlertConfirmDialog(aTalkApp.getGlobalContext(),
                aTalkApp.getResString(R.string.service_gui_ERROR),
                aTalkApp.getResString(R.string.service_gui_CONNECTION_FAILED_MSG,
                        accountID.getUserID(), accountID.getService()),
                aTalkApp.getResString(R.string.service_gui_RETRY),
                new DialogActivity.DialogListener()
                {
                    public boolean onConfirmClicked(DialogActivity dialog)
                    {
                        loginManagerCallback.login(protocolProvider);
                        return true;
                    }

                    public void onDialogCancelled(DialogActivity dialog)
                    {
                    }
                });
    }

    /**
     * Returns the <tt>SecurityAuthority</tt> implementation related to this login renderer.
     *
     * @param protocolProvider the specific <tt>ProtocolProviderService</tt>, for which we're obtaining a security
     * authority
     * @return the <tt>SecurityAuthority</tt> implementation related to this login renderer
     */
    public SecurityAuthority getSecurityAuthorityImpl(ProtocolProviderService protocolProvider)
    {
        return mSecurityAuthority;
    }

    /**
     * Updates aTalk icon notification to reflect current global status.
     */
    public void updateaTalkIconNotification()
    {
        String status;
        if (getGlobalStatus().isOnline()) {
            // At least one provider is online
            status = aTalkApp.getResString(R.string.service_gui_ONLINE);

        }
        else {
            // There are no active providers so we consider to be in the offline state
            status = aTalkApp.getResString(R.string.service_gui_OFFLINE);
        }

        int notificationID = OSGiService.getGeneralNotificationId();
        if (notificationID == -1) {
            return;
        }

        AndroidUtils.updateGeneralNotification(aTalkApp.getGlobalContext(), notificationID,
                aTalkApp.getResString(R.string.APPLICATION_NAME), status, System.currentTimeMillis());
    }

    /**
     * Adds global status listener.
     *
     * @param l the listener to be add.
     */
    public void addGlobalStatusListener(EventListener<PresenceStatus> l)
    {
        globalStatusListeners.addEventListener(l);
    }

    /**
     * Removes global status listener.
     *
     * @param l the listener to remove.
     */
    public void removeGlobalStatusListener(EventListener<PresenceStatus> l)
    {
        globalStatusListeners.removeEventListener(l);
    }

    /**
     * Returns current global status.
     *
     * @return current global status.
     */
    public PresenceStatus getGlobalStatus()
    {
        if (globalStatus == null) {
            GlobalStatusService gss = AndroidGUIActivator.getGlobalStatusService();
            globalStatus = gss != null ? gss.getGlobalPresenceStatus() : GlobalStatusEnum.OFFLINE;
        }
        return globalStatus;
    }

    /**
     * AuthorizationHandler instance used by this login renderer.
     */
    public AuthorizationHandlerImpl getAuthorizationHandler()
    {
        return authorizationHandler;
    }

    /**
     * Listens for all providerStatusChanged and providerStatusMessageChanged events in order
     * to refresh the account status panel, when a status is changed.
     */
    private class UIProviderPresenceStatusListener implements ProviderPresenceStatusListener
    {
        public void providerStatusChanged(ProviderPresenceStatusChangeEvent evt)
        {
            updateGlobalStatus();
        }

        public void providerStatusMessageChanged(PropertyChangeEvent evt)
        {
        }
    }

    /**
     * Indicates if the given <tt>protocolProvider</tt> related user interface is already rendered.
     *
     * @param protocolProvider the <tt>ProtocolProviderService</tt>, which related user interface we're looking for
     * @return <tt>true</tt> if the given <tt>protocolProvider</tt> related user interface is
     * already rendered
     */
    public boolean containsProtocolProviderUI(ProtocolProviderService protocolProvider)
    {
        return false;
    }

    /**
     * Updates the global status by picking the most connected protocol provider status.
     */
    private void updateGlobalStatus()
    {
        // Only if the GUI is active (bundle context will be null on shutdown)
        if (AndroidGUIActivator.bundleContext != null) {
            // Invalidate local status image
            localStatusRaw = null;
            // Invalidate global status
            globalStatus = null;
            globalStatusListeners.notifyEventListeners(getGlobalStatus());
        }

        updateaTalkIconNotification();
    }

    /**
     * Returns the local user avatar drawable.
     *
     * @return the local user avatar drawable.
     */
    public Drawable getLocalAvatarDrawable(ProtocolProviderService provider)
    {
        GlobalDisplayDetailsService displayDetailsService
                = AndroidGUIActivator.getGlobalDisplayDetailsService();

        byte[] avatarImage = displayDetailsService.getDisplayAvatar(provider);
        // Re-create drawable only if avatar has changed
        if (avatarImage != localAvatarRaw) {
            localAvatarRaw = avatarImage;
            localAvatar = AndroidImageUtil.roundedDrawableFromBytes(avatarImage);
        }
        return localAvatar;
    }

    /**
     * Returns the local user status drawable.
     *
     * @return the local user status drawable
     */
    synchronized public Drawable getLocalStatusDrawable()
    {
        byte[] statusImage = StatusUtil.getContactStatusIcon(getGlobalStatus());
        if (statusImage != localStatusRaw) {
            localStatusRaw = statusImage;
            localStatusDrawable = (localStatusRaw != null)
                    ? AndroidImageUtil.drawableFromBytes(statusImage) : null;
        }
        return localStatusDrawable;
    }
}
