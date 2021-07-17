/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 * 
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package net.java.sip.communicator.util.account;

import net.java.sip.communicator.service.protocol.OperationSet;
import net.java.sip.communicator.service.protocol.OperationSetPresence;
import net.java.sip.communicator.service.protocol.PresenceStatus;
import net.java.sip.communicator.service.protocol.ProtocolProviderService;
import net.java.sip.communicator.service.protocol.globalstatus.GlobalStatusService;
import net.java.sip.communicator.util.ServiceUtils;
import net.java.sip.communicator.util.UtilActivator;

import java.util.Iterator;

/**
 * The <tt>AccountStatusUtils</tt> provides utility methods for account status management.
 *
 * @author Yana Stamcheva
 * @author Eng Chong Meng
 */
public class AccountStatusUtils {
	private static GlobalStatusService globalStatusService;

	/**
	 * If the protocol provider supports presence operation set searches the last status which
	 * was selected, otherwise returns null.
	 *
	 * @param protocolProvider the protocol provider we're interested in.
	 * @return the last protocol provider presence status, or null if this provider doesn't
	 * support presence operation set
	 */
	public static Object getProtocolProviderLastStatus(ProtocolProviderService protocolProvider) {
		if (getProtocolPresenceOpSet(protocolProvider) != null)
			return getLastPresenceStatus(protocolProvider);
		else
			return getGlobalStatusService().getLastStatusString(protocolProvider);
	}

	/**
	 * Returns the presence operation set for the given protocol provider.
	 *
	 * @param protocolProvider The protocol provider for which the presence operation set is searched.
	 * @return the presence operation set for the given protocol provider.
	 */
	public static OperationSetPresence getProtocolPresenceOpSet(ProtocolProviderService protocolProvider) {
		OperationSet opSet = protocolProvider.getOperationSet(OperationSetPresence.class);
		return (opSet != null) ? (OperationSetPresence) opSet : null;
	}

	/**
	 * Returns the last status that was stored in the configuration xml for the given protocol provider.
	 *
	 * @param protocolProvider the protocol provider
	 * @return the last status that was stored in the configuration xml for the given protocol provider
	 */
	public static PresenceStatus getLastPresenceStatus(ProtocolProviderService protocolProvider) {
		if (getGlobalStatusService() != null)
			return getGlobalStatusService().getLastPresenceStatus(protocolProvider);
		return null;
	}

	/**
	 * Returns the current status for protocol provider.
	 *
	 * @param protocolProvider the protocol provider
	 * @return the current status for protocol provider
	 */
	public static PresenceStatus getPresenceStatus(ProtocolProviderService protocolProvider) {
		PresenceStatus status = null;

		OperationSetPresence opSet = protocolProvider.getOperationSet(OperationSetPresence.class);
		if (opSet != null)
			status = opSet.getPresenceStatus();
		return status;
	}

	/**
	 * Returns the online status of provider.
	 *
	 * @param protocolProvider the protocol provider
	 * @return the online status of provider.
	 */
	public static PresenceStatus getOnlineStatus(ProtocolProviderService protocolProvider) {
		PresenceStatus onlineStatus = null;
		OperationSetPresence presence = protocolProvider.getOperationSet(OperationSetPresence.class);

		// presence can be not supported
		if (presence != null) {
            for (PresenceStatus status : presence.getSupportedStatusSet()) {
				int connectivity = status.getStatus();
				if ((onlineStatus != null && (onlineStatus.getStatus() < connectivity))
						|| ((onlineStatus == null) && (connectivity > 50 && connectivity < 80))) {
					onlineStatus = status;
				}
			}
		}
		return onlineStatus;
	}

	/**
	 * Returns the offline status of provider.
	 *
	 * @param protocolProvider the protocol provider
	 * @return the offline status of provider.
	 */
	public static PresenceStatus getOfflineStatus(ProtocolProviderService protocolProvider) {
		PresenceStatus offlineStatus = null;

		OperationSetPresence presence = protocolProvider.getOperationSet(OperationSetPresence.class);
		// presence can be not supported
		if (presence != null) {
            for (PresenceStatus status : presence.getSupportedStatusSet()) {
				int connectivity = status.getStatus();
				if (connectivity < 1) {
					offlineStatus = status;
				}
			}
		}
		return offlineStatus;
	}

	/**
	 * Returns the last contact status saved in the configuration.
	 *
	 * @param protocolProvider the protocol provider to which the status corresponds
	 * @return the last contact status saved in the configuration.
	 */
	public String getLastStatusString(ProtocolProviderService protocolProvider) {
		return getGlobalStatusService().getLastStatusString(protocolProvider);
	}

	/**
	 * Returns the <tt>GlobalStatusService</tt> obtained from the bundle context.
	 *
	 * @return the <tt>GlobalStatusService</tt> obtained from the bundle context
	 */
	public static GlobalStatusService getGlobalStatusService() {
		if (globalStatusService == null) {
			globalStatusService = ServiceUtils.getService(UtilActivator.bundleContext,
					GlobalStatusService.class);
		}
		return globalStatusService;
	}
}