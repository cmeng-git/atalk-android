/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package net.java.sip.communicator.impl.globaldisplaydetails;

import net.java.sip.communicator.service.gui.UIService;
import net.java.sip.communicator.service.protocol.*;
import net.java.sip.communicator.service.protocol.event.RegistrationStateChangeEvent;
import net.java.sip.communicator.service.protocol.event.RegistrationStateChangeListener;
import net.java.sip.communicator.service.protocol.globalstatus.GlobalStatusEnum;
import net.java.sip.communicator.service.protocol.globalstatus.GlobalStatusService;
import net.java.sip.communicator.util.account.*;

import org.apache.commons.lang3.StringUtils;
import org.atalk.android.R;
import org.atalk.android.aTalkApp;
import org.atalk.service.configuration.ConfigurationService;

import java.util.Collection;

import timber.log.Timber;

import static net.java.sip.communicator.util.account.AccountUtils.getRegisteredProviders;

/**
 * Global statuses service impl - The ActionBar status indicator acts both as global presence
 * status for all the registered accounts; as well as the menu input giving access to the outside
 * to change the status of all registered accounts.
 * <p>
 * (Not implemented in android) When implemented global status menu with list of all account
 * statuses, then change to individual protocol provider status is allowed.
 *
 * @author Damian Minkov
 * @author Eng Chong Meng
 */
public class GlobalStatusServiceImpl implements GlobalStatusService, RegistrationStateChangeListener
{
    /**
     * Handles newly added providers.
     *
     * @param pps the protocolProviderService
     */
    void handleProviderAdded(ProtocolProviderService pps)
    {
        pps.addRegistrationStateChangeListener(this);
        if (pps.isRegistered()) {
            handleProviderRegistered(pps, false);
        }
    }

    /**
     * Handles removed providers.
     *
     * @param pps the Protocol Service Provider.
     */
    void handleProviderRemoved(ProtocolProviderService pps)
    {
        pps.removeRegistrationStateChangeListener(this);
    }

    /**
     * Returns the global presence status.
     *
     * @return the current global presence status
     */
    public PresenceStatus getGlobalPresenceStatus()
    {
        int status = PresenceStatus.OFFLINE;
        Collection<ProtocolProviderService> pProviders = getRegisteredProviders();
        // If we don't have registered providers we return offline status.
        if (pProviders.isEmpty())
            return getPresenceStatus(status);

        boolean hasAvailableProvider = false;
        for (ProtocolProviderService protocolProvider : pProviders) {
            // We do not show hidden protocols in our status bar, so we do not care about their status here.
            if (!protocolProvider.getAccountID().isHidden() && protocolProvider.isRegistered()) {
                OperationSetPresence presence = protocolProvider.getOperationSet(OperationSetPresence.class);
                if (presence == null) {
                    hasAvailableProvider = true;
                }
                else {
                    int presenceStatus = presence.getPresenceStatus().getStatus();
                    // Assign presenceStatus with new valid if > last status
                    if (presenceStatus > status) {
                        status = presenceStatus;
                    }
                }
            }
        }
        // if we have at least one online provider and without OperationSetPresence feature
        if ((status == PresenceStatus.OFFLINE) && hasAvailableProvider)
            status = PresenceStatus.AVAILABLE_THRESHOLD;

        return getPresenceStatus(status);
    }

    /**
     * Returns the <tt>GlobalStatusEnum</tt> corresponding to the given status. For the
     * status constants we use here are the values defined in the <tt>PresenceStatus</tt>,
     * but this is only for convenience.
     *
     * @param status the status to which the item should correspond
     * @return the <tt>GlobalStatusEnum</tt> corresponding to the given status
     */
    private PresenceStatus getPresenceStatus(int status)
    {
        if (status < PresenceStatus.ONLINE_THRESHOLD) {
            return GlobalStatusEnum.OFFLINE;
        }
        else if (status < PresenceStatus.EXTENDED_AWAY_THRESHOLD) {
            return GlobalStatusEnum.DO_NOT_DISTURB;
        }
        else if (status < PresenceStatus.AWAY_THRESHOLD) {
            return GlobalStatusEnum.EXTENDED_AWAY;
        }
        else if (status < PresenceStatus.AVAILABLE_THRESHOLD) {
            return GlobalStatusEnum.AWAY;
        }
        else if (status < PresenceStatus.EAGER_TO_COMMUNICATE_THRESHOLD) {
            return GlobalStatusEnum.ONLINE;
        }
        else if (status < PresenceStatus.MAX_STATUS_VALUE) {
            return GlobalStatusEnum.FREE_FOR_CHAT;
        }
        else {
            return GlobalStatusEnum.OFFLINE;
        }
    }

    /**
     * Returns the last status that was stored in the configuration for the given protocol
     * provider.
     *
     * @param protocolProvider the protocol provider
     * @return the last status that was stored in the configuration for the given protocol provider
     */
    public PresenceStatus getLastPresenceStatus(ProtocolProviderService protocolProvider)
    {
        String lastStatus = getLastStatusString(protocolProvider);
        PresenceStatus status = null;

        if (lastStatus != null) {
            OperationSetPresence presence = protocolProvider.getOperationSet(OperationSetPresence.class);
            if (presence == null)
                return null;

            // Check if there's such status in the supported presence status set.
            for (PresenceStatus presenceStatus : presence.getSupportedStatusSet()) {
                if (presenceStatus.getStatusName().equals(lastStatus)) {
                    status = presenceStatus;
                    break;
                }
            }

            // If we haven't found the last status in the protocol provider supported status set,
            // we'll have a look for a corresponding global status and its protocol representation.
            if (status == null) {
                switch (lastStatus) {
                    case GlobalStatusEnum.ONLINE_STATUS:
                        status = getPresenceStatus(protocolProvider, PresenceStatus.AVAILABLE_THRESHOLD,
                                PresenceStatus.EAGER_TO_COMMUNICATE_THRESHOLD);
                        break;
                    case GlobalStatusEnum.AWAY_STATUS:
                        status = getPresenceStatus(protocolProvider, PresenceStatus.AWAY_THRESHOLD,
                                PresenceStatus.AVAILABLE_THRESHOLD);
                        break;
                    case GlobalStatusEnum.EXTENDED_AWAY_STATUS:
                        status = getPresenceStatus(protocolProvider, PresenceStatus.EXTENDED_AWAY_THRESHOLD,
                                PresenceStatus.AWAY_THRESHOLD);
                        break;
                    case GlobalStatusEnum.DO_NOT_DISTURB_STATUS:
                        status = getPresenceStatus(protocolProvider, PresenceStatus.ONLINE_THRESHOLD,
                                PresenceStatus.EXTENDED_AWAY_THRESHOLD);
                        break;
                    case GlobalStatusEnum.FREE_FOR_CHAT_STATUS:
                        status = getPresenceStatus(protocolProvider, PresenceStatus.AVAILABLE_THRESHOLD,
                                PresenceStatus.MAX_STATUS_VALUE);
                        break;
                    case GlobalStatusEnum.OFFLINE_STATUS:
                        status = getPresenceStatus(protocolProvider, 0, GlobalStatusEnum.ONLINE_THRESHOLD);
                        break;
                }
            }
        }
        return status;
    }

    /**
     * Returns the last contact status saved in the configuration.
     *
     * @param protocolProvider the protocol provider to which the status corresponds
     * @return the last contact status saved in the configuration.
     */
    public String getLastStatusString(ProtocolProviderService protocolProvider)
    {
        // find the last contact status saved in the configuration.
        String lastStatus = null;

        String accountUuid = protocolProvider.getAccountID().getAccountUuid();
        if (StringUtils.isNotEmpty(accountUuid)) {
            ConfigurationService configService = GlobalDisplayDetailsActivator.getConfigurationService();
            lastStatus = configService.getString(accountUuid + ".lastAccountStatus");
        }
        return lastStatus;
    }

    /**
     * Publish present status for the given protocolProvider
     *
     * @param protocolProvider the protocol provider to which we change the status.
     * @param status the status to publish.
     */
    public void publishStatus(ProtocolProviderService protocolProvider, PresenceStatus status)
    {
        publishStatusInternal(protocolProvider, status, false);
    }

    /**
     * Publish present status for the given protocolProvider
     *
     * @param protocolProvider the protocol provider to which we change the status.
     * @param status the status to publish.
     * @param state whether the publish status is invoked after registrationStateChanged for a provider,
     * where the provider is expected to be REGISTERED, if not we do nothing
     * (means it has connection failed soon after firing registered).
     */
    @Override
    public void publishStatus(ProtocolProviderService protocolProvider, PresenceStatus status, boolean state)
    {
        publishStatusInternal(protocolProvider, status, state);
    }

    /**
     * Publish <present/> status to the server; it takes appropriate action including login
     * and logout to change state if the actual pps status is not per the requested status.
     * #TODO cmeng: may be this should not be this class responsibility to do this.
     *
     * @param protocolProvider the protocol provider to which we change the status.
     * @param status the status to publish.
     * @param dueToRegistrationStateChanged whether the publish status is invoked after registrationStateChanged
     * for a provider, where the provider is expected to be REGISTERED, if not we do nothing
     * (means it has connection failed soon after firing registered).
     */
    private void publishStatusInternal(ProtocolProviderService protocolProvider, PresenceStatus status,
            boolean dueToRegistrationStateChanged)
    {
        OperationSetPresence presence = protocolProvider.getOperationSet(OperationSetPresence.class);
        LoginManager loginManager = null;
        UIService uiService = GlobalDisplayDetailsActivator.getUIService();
        if (uiService != null) {
            loginManager = uiService.getLoginManager();
        }

        RegistrationState registrationState = protocolProvider.getRegistrationState();
        if ((registrationState == RegistrationState.REGISTERED)
                && (presence != null) && !presence.getPresenceStatus().equals(status)) {
            if (status.isOnline()) {
                new PublishPresenceStatusThread(protocolProvider, presence, status).start();
            }
            else {
                if (loginManager != null)
                    loginManager.setManuallyDisconnected(true);
                LoginManager.logoff(protocolProvider);
            }
        }
        else if ((registrationState != RegistrationState.REGISTERED)
                && (registrationState != RegistrationState.REGISTERING)
                && (registrationState != RegistrationState.AUTHENTICATING)
                && status.isOnline()) {
            if (dueToRegistrationStateChanged) {
                // If provider fires registered, and while dispatching the registered event a fatal
                // error rise in the connection and the provider goes in connection_failed we can
                // end up here calling login and going over the same cycle over and over again
                Timber.w("Called publish status for provider in wrong state provider: %s " + protocolProvider
                        + " registrationState: %s status: %s", protocolProvider, registrationState, status);
                return;
            }
            else {
                loginManager.login(protocolProvider);
            }
        }
        else if (!status.isOnline() && !(registrationState == RegistrationState.UNREGISTERING)) {
            if (loginManager != null)
                loginManager.setManuallyDisconnected(true);
            LoginManager.logoff(protocolProvider);
        }
        saveStatusInformation(protocolProvider, status.getStatusName());
    }

    /**
     * Publish present status. We search for the highest value in the given interval.
     * <p/>
     * change the status.
     *
     * @param globalStatus account status indicator on action bar
     */
    public void publishStatus(GlobalStatusEnum globalStatus)
    {
        String itemName = globalStatus.getStatusName();
        LoginManager loginManager = GlobalDisplayDetailsActivator.getUIService().getLoginManager();

        Collection<ProtocolProviderService> protocolProviders = AccountUtils.getRegisteredProviders();
        for (ProtocolProviderService protocolProvider : protocolProviders) {
            switch (itemName) {
                case GlobalStatusEnum.ONLINE_STATUS:
                    if (!protocolProvider.isRegistered()) {
                        saveStatusInformation(protocolProvider, itemName);
                        loginManager.login(protocolProvider);
                    }
                    else {
                        OperationSetPresence presence = protocolProvider.getOperationSet(OperationSetPresence.class);
                        if (presence == null) {
                            saveStatusInformation(protocolProvider, itemName);
                        }
                        else {
                            for (PresenceStatus status : presence.getSupportedStatusSet()) {
                                if ((status.getStatus() < PresenceStatus.EAGER_TO_COMMUNICATE_THRESHOLD)
                                        && (status.getStatus() >= PresenceStatus.AVAILABLE_THRESHOLD)) {
                                    new PublishPresenceStatusThread(protocolProvider, presence, status).start();
                                    this.saveStatusInformation(protocolProvider, status.getStatusName());
                                    break;
                                }
                            }
                        }
                    }
                    break;

                case GlobalStatusEnum.OFFLINE_STATUS:
                    if (!protocolProvider.getRegistrationState().equals(RegistrationState.UNREGISTERED)
                            && !protocolProvider.getRegistrationState().equals(RegistrationState.UNREGISTERING)) {
                        OperationSetPresence presence = protocolProvider.getOperationSet(OperationSetPresence.class);

                        if (presence == null) {
                            saveStatusInformation(protocolProvider, itemName);
                            LoginManager.logoff(protocolProvider);
                        }
                        else {
                            for (PresenceStatus status : presence.getSupportedStatusSet()) {
                                if (status.getStatus() < PresenceStatus.ONLINE_THRESHOLD) {
                                    this.saveStatusInformation(protocolProvider, status.getStatusName());
                                    break;
                                }
                            }
                            // Must use separate thread for account unRegistration. Otherwise
                            // StrictMode Exception from android-protocolProvider.unregister(true);
                            LoginManager.logoff(protocolProvider);
                        }
                    }
                    break;

                case GlobalStatusEnum.FREE_FOR_CHAT_STATUS:
                    if (!protocolProvider.isRegistered()) {
                        saveStatusInformation(protocolProvider, itemName);
                        loginManager.login(protocolProvider);
                    }
                    else
                        // we search for highest available status here
                        publishStatus(protocolProvider, PresenceStatus.AVAILABLE_THRESHOLD, PresenceStatus.MAX_STATUS_VALUE);
                    break;

                case GlobalStatusEnum.DO_NOT_DISTURB_STATUS:
                    if (!protocolProvider.isRegistered()) {
                        saveStatusInformation(protocolProvider, itemName);
                        loginManager.login(protocolProvider);
                    }
                    else {
                        // status between online and away is DND
                        publishStatus(protocolProvider, PresenceStatus.ONLINE_THRESHOLD, PresenceStatus.EXTENDED_AWAY_THRESHOLD);
                    }
                    break;

                case GlobalStatusEnum.AWAY_STATUS:
                    if (!protocolProvider.isRegistered()) {
                        saveStatusInformation(protocolProvider, itemName);
                        loginManager.login(protocolProvider);
                    }
                    else {
                        // a status in the away interval
                        publishStatus(protocolProvider, PresenceStatus.AWAY_THRESHOLD, PresenceStatus.AVAILABLE_THRESHOLD);
                    }
                    break;

                case GlobalStatusEnum.EXTENDED_AWAY_STATUS:
                    if (!protocolProvider.isRegistered()) {
                        saveStatusInformation(protocolProvider, itemName);
                        loginManager.login(protocolProvider);
                    }
                    else {
                        // a status in the away interval
                        publishStatus(protocolProvider, PresenceStatus.EXTENDED_AWAY_THRESHOLD, PresenceStatus.AWAY_THRESHOLD);
                    }
                    break;
            }
        }
    }

    /**
     * Publish present status. We search for the highest value in the given interval.
     *
     * @param protocolProvider the protocol provider to which we change the status.
     * @param floorStatusValue the min status value.
     * @param ceilStatusValue the max status value.
     */
    private void publishStatus(ProtocolProviderService protocolProvider, int floorStatusValue,
            int ceilStatusValue)
    {
        if (protocolProvider.isRegistered()) {
            PresenceStatus status = getPresenceStatus(protocolProvider, floorStatusValue, ceilStatusValue);
            if (status != null) {
                OperationSetPresence presence = protocolProvider.getOperationSet(OperationSetPresence.class);
                new PublishPresenceStatusThread(protocolProvider, presence, status).start();
                this.saveStatusInformation(protocolProvider, status.getStatusName());
            }
        }
    }

    private PresenceStatus getPresenceStatus(ProtocolProviderService protocolProvider, int floorStatusValue,
            int ceilStatusValue)
    {
        OperationSetPresence presence = protocolProvider.getOperationSet(OperationSetPresence.class);
        if (presence == null)
            return null;

        PresenceStatus status = null;
        for (PresenceStatus currentStatus : presence.getSupportedStatusSet()) {
            if ((status == null)
                    && (currentStatus.getStatus() < ceilStatusValue)
                    && (currentStatus.getStatus() >= floorStatusValue)) {
                status = currentStatus;
            }

            if (status != null) {
                if ((currentStatus.getStatus() < ceilStatusValue)
                        && (currentStatus.getStatus() >= floorStatusValue)
                        && (currentStatus.getStatus() > status.getStatus())) {
                    status = currentStatus;
                }
            }
        }
        return status;
    }

    /**
     * Saves the last status for all accounts. This information is used on logging. Each time user
     * logs in he's logged with the same status as he was the last time before closing the
     * application.
     *
     * @param protocolProvider the protocol provider to save status information for
     * @param statusName the name of the status to save
     */
    private void saveStatusInformation(ProtocolProviderService protocolProvider, String statusName)
    {
        ConfigurationService configService = GlobalDisplayDetailsActivator.getConfigurationService();

        String accountUuid = protocolProvider.getAccountID().getAccountUuid();
        if (StringUtils.isNotEmpty(accountUuid)) {
            configService.setProperty(accountUuid + ".lastAccountStatus", statusName);
        }
    }

    /**
     * Waits for providers to register and then checks for its last status saved if any and
     * used it to restore its status.
     *
     * @param evt a <tt>RegistrationStateChangeEvent</tt> which describes the
     */
    @Override
    public void registrationStateChanged(RegistrationStateChangeEvent evt)
    {
        if (evt.getNewState().equals(RegistrationState.REGISTERED))
            handleProviderRegistered(evt.getProvider(), true);
    }

    /**
     * Handles registered providers. If provider has a stored last status publish that status,
     * otherwise we just publish that they are Online/Available/
     *
     * @param pps the provider
     */
    private void handleProviderRegistered(ProtocolProviderService pps, boolean dueToRegistrationStateChanged)
    {
        PresenceStatus status = getLastPresenceStatus(pps);
        if (status == null) {
            // lets publish just online
            status = AccountStatusUtils.getOnlineStatus(pps);
        }

        if (status != null && status.getStatus() >= PresenceStatus.ONLINE_THRESHOLD) {
            publishStatusInternal(pps, status, dueToRegistrationStateChanged);
        }
    }

    /**
     * Publishes the given status to the given presence operation set.
     */
    private class PublishPresenceStatusThread extends Thread
    {
        private ProtocolProviderService protocolProvider;
        private PresenceStatus status;
        private OperationSetPresence presence;

        /**
         * Publishes the given <tt>status</tt> through the given <tt>presence</tt> operation set.
         *
         * @param presence the operation set through which we publish the status
         * @param status the status to publish
         */
        public PublishPresenceStatusThread(ProtocolProviderService protocolProvider,
                OperationSetPresence presence, PresenceStatus status)
        {
            this.protocolProvider = protocolProvider;
            this.presence = presence;
            this.status = status;
        }

        @Override
        public void run()
        {
            try {
                presence.publishPresenceStatus(status, "");
            } catch (IllegalArgumentException | IllegalStateException e1) {
                Timber.e(e1, "Error - changing status");
            } catch (OperationFailedException e1) {
                if (e1.getErrorCode() == OperationFailedException.GENERAL_ERROR) {
                    String msgText = aTalkApp.getResString(R.string.service_gui_STATUS_CHANGE_GENERAL_ERROR,
                            protocolProvider.getAccountID().getUserID(), protocolProvider.getAccountID().getService());

                    GlobalDisplayDetailsActivator.getAlertUIService().showAlertDialog(
                            aTalkApp.getResString(R.string.service_gui_GENERAL_ERROR), msgText, e1);
                }
                else if (e1.getErrorCode() == OperationFailedException.NETWORK_FAILURE) {
                    String msgText = aTalkApp.getResString(R.string.service_gui_STATUS_CHANGE_NETWORK_FAILURE,
                            protocolProvider.getAccountID().getUserID(), protocolProvider.getAccountID().getService());

                    GlobalDisplayDetailsActivator.getAlertUIService().showAlertDialog(msgText,
                            aTalkApp.getResString(R.string.service_gui_NETWORK_FAILURE), e1);
                }
                else if (e1.getErrorCode() == OperationFailedException.PROVIDER_NOT_REGISTERED) {
                    String msgText = aTalkApp.getResString(R.string.service_gui_STATUS_CHANGE_NETWORK_FAILURE,
                            protocolProvider.getAccountID().getUserID(), protocolProvider.getAccountID().getService());

                    GlobalDisplayDetailsActivator.getAlertUIService().showAlertDialog(
                            aTalkApp.getResString(R.string.service_gui_NETWORK_FAILURE), msgText, e1);
                }
                Timber.e(e1, "Error - changing status");
            }
        }
    }
}
