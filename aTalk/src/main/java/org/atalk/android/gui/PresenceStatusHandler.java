/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.atalk.android.gui;

import net.java.sip.communicator.service.protocol.*;
import net.java.sip.communicator.service.protocol.event.RegistrationStateChangeEvent;
import net.java.sip.communicator.service.protocol.event.RegistrationStateChangeListener;
import net.java.sip.communicator.service.protocol.globalstatus.GlobalStatusService;
import net.java.sip.communicator.util.ServiceUtils;
import net.java.sip.communicator.util.UtilActivator;
import net.java.sip.communicator.util.account.AccountStatusUtils;

import org.osgi.framework.*;

/**
 * Class takes care of setting/restoring proper presence statuses. When protocol registers for
 * the first time makes sure to set online state. When protocol provider reconnects uses
 * GlobalStatusService to restore last status set by the user.
 *
 * @author Pawel Domas
 * @author Eng Chong Meng
 */
public class PresenceStatusHandler implements ServiceListener, RegistrationStateChangeListener
{
    /**
     * Start the handler with given OSGI context.
     *
     * @param bundleContext OSGI context to be used.
     */
    public void start(BundleContext bundleContext)
    {
        bundleContext.addServiceListener(this);

        ServiceReference<ProtocolProviderService>[] pps
                = ServiceUtils.getServiceReferences(bundleContext, ProtocolProviderService.class);

        for (ServiceReference<ProtocolProviderService> sRef : pps) {
            ProtocolProviderService provider = bundleContext.getService(sRef);
            updateStatus(provider);
            provider.addRegistrationStateChangeListener(this);
        }
    }

    /**
     * Stops the handler.
     *
     * @param bundleContext OSGI context to be used by this instance.
     */
    public void stop(BundleContext bundleContext)
    {
        bundleContext.removeServiceListener(this);
    }

    @Override
    public void registrationStateChanged(RegistrationStateChangeEvent evt)
    {
        // There is nothing we can do when account is registering...
        if (evt.getNewState().equals(RegistrationState.REGISTERING)) {
            // startConnecting(protocolProvider);
        }
        else {
            updateStatus(evt.getProvider());
        }
    }

    @Override
    public void serviceChanged(ServiceEvent event)
    {
        ServiceReference<?> serviceRef = event.getServiceReference();

        // if the event is caused by a bundle being stopped, we don't want to know
        if (serviceRef.getBundle().getState() == Bundle.STOPPING) {
            return;
        }

        Object service = UtilActivator.bundleContext.getService(serviceRef);

        // we don't care if the source service is not a protocol provider
        if (!(service instanceof ProtocolProviderService)) {
            return;
        }

        switch (event.getType()) {
            case ServiceEvent.REGISTERED:
                ((ProtocolProviderService) service).addRegistrationStateChangeListener(this);
                break;
            case ServiceEvent.UNREGISTERING:
                ((ProtocolProviderService) service).removeRegistrationStateChangeListener(this);
                break;
        }
    }

    /**
     * Updates presence status on given <tt>protocolProvider</tt> depending on its state.
     *
     * @param protocolProvider the protocol provider for which new status will be adjusted.
     */
    private void updateStatus(ProtocolProviderService protocolProvider)
    {
        OperationSetPresence presence = AccountStatusUtils.getProtocolPresenceOpSet(protocolProvider);

        PresenceStatus offlineStatus = null;
        PresenceStatus onlineStatus = null;

        for (PresenceStatus status : presence.getSupportedStatusSet()) {
            int connectivity = status.getStatus();
            if (connectivity < 1) {
                offlineStatus = status;
            }
            else if ((onlineStatus != null && (onlineStatus.getStatus() < connectivity))
                    || (onlineStatus == null && (connectivity > 50 && connectivity < 80))) {
                onlineStatus = status;
            }
        }

        PresenceStatus presenceStatus = null;
        if (!protocolProvider.isRegistered())
            presenceStatus = offlineStatus;
        else {
            presenceStatus = AccountStatusUtils.getLastPresenceStatus(protocolProvider);
            if (presenceStatus == null)
                presenceStatus = onlineStatus;
        }

        GlobalStatusService gbsService = AndroidGUIActivator.getGlobalStatusService();
        if (protocolProvider.isRegistered() && (gbsService != null)
                && !presence.getPresenceStatus().equals(presenceStatus)) {
            gbsService.publishStatus(protocolProvider, presenceStatus, false);
        }
    }
}
