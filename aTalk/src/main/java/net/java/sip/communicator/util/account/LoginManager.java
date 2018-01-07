/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 * 
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package net.java.sip.communicator.util.account;

import net.java.sip.communicator.service.protocol.*;
import net.java.sip.communicator.service.protocol.event.*;
import net.java.sip.communicator.service.protocol.globalstatus.GlobalStatusEnum;
import net.java.sip.communicator.util.*;

import org.osgi.framework.*;

/**
 * The <tt>LoginManager</tt> manages the login operation. Here we obtain the
 * <tt>ProtocolProviderFactory</tt>, we make the account installation and we handle all events
 * related to the registration state.
 * <p/>
 * The <tt>LoginManager</tt> is the one that opens one or more <tt>LoginWindow</tt>s for each
 * <tt>ProtocolProviderFactory</tt>. The <tt>LoginWindow</tt> is where user could enter an
 * identifier and password.
 * <p/>
 * Note that the behavior of this class will be changed when the Configuration Service is ready.
 *
 * @author Yana Stamcheva
 * @author Eng Chong Meng
 */
public class LoginManager implements ServiceListener, RegistrationStateChangeListener
		//,AccountManagerListener
{
	private static final Logger logger = Logger.getLogger(LoginManager.class);
	private boolean manuallyDisconnected = false;
	private final LoginRenderer loginRenderer;

	/**
	 * Creates an instance of the <tt>LoginManager</tt>, by specifying the main application window.
	 *
	 * @param loginRenderer
	 * 		the main application window
	 */
	public LoginManager(LoginRenderer loginRenderer)
	{
		this.loginRenderer = loginRenderer;
		UtilActivator.bundleContext.addServiceListener(this);
	}

	/**
	 * Unregisters the given protocol provider.
	 *
	 * @param protocolProvider
	 * 		the ProtocolProviderService to unregister
	 */
	public static void logoff(ProtocolProviderService protocolProvider)
	{
		new UnregisterProvider(protocolProvider).start();
	}

	/**
	 * Registers the given protocol provider.
	 *
	 * @param protocolProvider
	 * 		the ProtocolProviderService to register.
	 */
	public void login(ProtocolProviderService protocolProvider)
	{
		// logger.warn("SMACK stack access: " + Log.getStackTraceString(new Exception()));
		loginRenderer.startConnectingUI(protocolProvider);
		new RegisterProvider(protocolProvider,
				loginRenderer.getSecurityAuthorityImpl(protocolProvider)).start();
	}

	/**
	 * Shows login window for each registered account.
	 */
	public void runLogin()
	{
		for (ProtocolProviderFactory providerFactory
				: UtilActivator.getProtocolProviderFactories().values()) {
			addAccountsForProtocolProviderFactory(providerFactory);
		}
	}

	/**
	 * Handles stored accounts for a protocol provider factory and add them to the UI and register
	 * them if needed.
	 *
	 * @param providerFactory
	 * 		the factory to handle.
	 */
	private void addAccountsForProtocolProviderFactory(ProtocolProviderFactory providerFactory)
	{
		for (AccountID accountID : providerFactory.getRegisteredAccounts()) {
			ServiceReference<ProtocolProviderService> serRef
					= providerFactory.getProviderForAccount(accountID);
			ProtocolProviderService protocolProvider
					= UtilActivator.bundleContext.getService(serRef);
			handleProviderAdded(protocolProvider);
		}
	}

	/**
	 * The method is called by a ProtocolProvider implementation whenever a change in the
	 * registration state of the corresponding provider has occurred.
	 *
	 * @param evt
	 * 		ProviderStatusChangeEvent the event describing the status change.
	 */
	public void registrationStateChanged(RegistrationStateChangeEvent evt)
	{
		RegistrationState newState = evt.getNewState();
		ProtocolProviderService protocolProvider = evt.getProvider();
		AccountID accountID = protocolProvider.getAccountID();

		if (logger.isTraceEnabled())
			logger.trace("Protocol provider: " + protocolProvider
					+ " changes state to: " + evt.getNewState().getStateName()
					+ " Reason: " + evt.getReason());

		if (newState.equals(RegistrationState.REGISTERED)
				|| newState.equals(RegistrationState.UNREGISTERED)
				|| newState.equals(RegistrationState.EXPIRED)
				|| newState.equals(RegistrationState.AUTHENTICATION_FAILED)
				|| newState.equals(RegistrationState.CONNECTION_FAILED)
				|| newState.equals(RegistrationState.CHALLENGED_FOR_AUTHENTICATION)) {
			loginRenderer.stopConnectingUI(protocolProvider);
		}

		if (newState.equals(RegistrationState.REGISTERED)) {
			loginRenderer.protocolProviderConnected(protocolProvider, System.currentTimeMillis());
		}
//		else {
//			ResourceManagementService mRMS = UtilActivator.getResources();
//			String msgText = null;
//
//			if (newState.equals(RegistrationState.AUTHENTICATION_FAILED)) {
//				switch (evt.getReasonCode()) {
//					case RegistrationStateChangeEvent.REASON_RECONNECTION_RATE_LIMIT_EXCEEDED:
//						msgText = mRMS.getI18NString("service.gui.RECONNECTION_LIMIT_EXCEEDED",
//								new String[]{accountID.getUserID(), accountID.getService()});
//						break;
//
//					case RegistrationStateChangeEvent.REASON_NON_EXISTING_USER_ID:
//						msgText = mRMS.getI18NString("service.gui.NON_EXISTING_USER_ID",
//								new String[]{protocolProvider.getProtocolDisplayName()});
//						break;
//					case RegistrationStateChangeEvent.REASON_TLS_REQUIRED:
//						msgText = mRMS.getI18NString("service.gui.NON_SECURE_CONNECTION",
//								new String[]{accountID.getAccountJid()});
//						break;
//					default:
//						break;
//				}
//
//				if (logger.isTraceEnabled())
//					logger.trace(evt.getReason());
//			}
//			// CONNECTION_FAILED events are now dispatched in reconnect plugin
////			else if (newState.equals(RegistrationState.CONNECTION_FAILED)) {
////				loginRenderer.protocolProviderConnectionFailed(
////						protocolProvider, this);
////				logger.trace(evt.getReason());
////			}
//			else if (newState.equals(RegistrationState.EXPIRED)) {
//				msgText = mRMS.getI18NString("service.gui.CONNECTION_EXPIRED_MSG",
//						new String[]{protocolProvider.getProtocolDisplayName()});
//				logger.error(evt.getReason());
//			}
//			else if (newState.equals(RegistrationState.UNREGISTERED)) {
//				if (!manuallyDisconnected) {
//					switch (evt.getReasonCode()) {
//						case RegistrationStateChangeEvent.REASON_MULTIPLE_LOGIN:
//							msgText = mRMS.getI18NString("service.gui.MULTIPLE_LOGIN",
//									new String[]{accountID.getUserID(), accountID.getService()});
//							break;
//						case RegistrationStateChangeEvent.REASON_CLIENT_LIMIT_REACHED_FOR_IP:
//							msgText = mRMS.getI18NString("service.gui.LIMIT_REACHED_FOR_IP",
//									new String[]{protocolProvider.getProtocolDisplayName()});
//							break;
//						case RegistrationStateChangeEvent.REASON_USER_REQUEST:
//							// do nothing
//							break;
//						default:
//							msgText = mRMS.getI18NString("service.gui.UNREGISTERED_MESSAGE",
//									new String[]{accountID.getUserID(),
//											accountID.getServerAddress()});
//					}
//					if (logger.isTraceEnabled())
//						logger.trace(evt.getReason());
//				}
//			}
//			if (msgText != null)
//				UtilActivator.getAlertUIService()
//						.showAlertDialog(mRMS.getI18NString("service.gui.ERROR"), msgText);
//		}
	}

	/**
	 * Implements the <tt>ServiceListener</tt> method. Verifies whether the passed event
	 * concerns a <tt>ProtocolProviderService</tt> and adds the corresponding UI controls.
	 *
	 * @param event
	 * 		The <tt>ServiceEvent</tt> object.
	 */

	public void serviceChanged(ServiceEvent event)
	{
		ServiceReference<?> serviceRef = event.getServiceReference();

		// if the event is caused by a bundle being stopped, we don't want to know
		if (serviceRef.getBundle().getState() == Bundle.STOPPING)
			return;

		Object service = UtilActivator.bundleContext.getService(serviceRef);

		// we don't care if the source service is not a protocol provider
		if (!(service instanceof ProtocolProviderService))
			return;

		switch (event.getType()) {
			case ServiceEvent.REGISTERED:
				handleProviderAdded((ProtocolProviderService) service);
				break;
			case ServiceEvent.UNREGISTERING:
				handleProviderRemoved((ProtocolProviderService) service);
				break;
		}
	}

	/**
	 * Adds all UI components (status selector box, etc) related to the given protocol provider.
	 *
	 * @param protocolProvider
	 * 		the <tt>ProtocolProviderService</tt>
	 */
	private void handleProviderAdded(ProtocolProviderService protocolProvider)
	{
		if (logger.isTraceEnabled())
			logger.trace("The following protocol provider was just added: "
					+ protocolProvider.getAccountID().getAccountJid());

		synchronized (loginRenderer) {
			if (!loginRenderer.containsProtocolProviderUI(protocolProvider)) {
				protocolProvider.addRegistrationStateChangeListener(this);
				loginRenderer.addProtocolProviderUI(protocolProvider);
			}
			// we have already added this provider and scheduled a login if needed we've done our
			// work, if it fails or something else reconnect or other plugins will take care
			else
				return;
		}

		Object status = AccountStatusUtils.getProtocolProviderLastStatus(protocolProvider);
		if ((status == null)
				|| status.equals(GlobalStatusEnum.ONLINE_STATUS)
				|| ((status instanceof PresenceStatus)
				&& (((PresenceStatus) status).getStatus() >= PresenceStatus.ONLINE_THRESHOLD))) {
			login(protocolProvider);
		}
	}

	/**
	 * Removes all UI components related to the given protocol provider.
	 *
	 * @param protocolProvider
	 * 		the <tt>ProtocolProviderService</tt>
	 */
	private void handleProviderRemoved(ProtocolProviderService protocolProvider)
	{
		loginRenderer.removeProtocolProviderUI(protocolProvider);
	}

	/**
	 * Returns <tt>true</tt> to indicate the atalk has been manually disconnected,
	 * <tt>false</tt> - otherwise.
	 *
	 * @return <tt>true</tt> to indicate the atalk has been manually disconnected,
	 * <tt>false</tt> - otherwise
	 */
	public boolean isManuallyDisconnected()
	{
		return manuallyDisconnected;
	}

	/**
	 * Sets the manually disconnected property.
	 *
	 * @param manuallyDisconnected
	 * 		<tt>true</tt> to indicate the atalk has been manually
	 * 		disconnected, <tt>false</tt> - otherwise
	 */
	public void setManuallyDisconnected(boolean manuallyDisconnected)
	{
		this.manuallyDisconnected = manuallyDisconnected;
	}

	/**
	 * Registers a protocol provider in a separate thread.
	 */
	private class RegisterProvider extends Thread
	{
		private final ProtocolProviderService protocolProvider;
		private final SecurityAuthority secAuth;

		RegisterProvider(ProtocolProviderService protocolProvider, SecurityAuthority secAuth)
		{
			this.protocolProvider = protocolProvider;
			this.secAuth = secAuth;

//			if (logger.isTraceEnabled())
//				logger.trace("Registering provider: "
//								+ protocolProvider.getAccountID().getAccountJid(),
//						new Exception("Not an error! Just tracing for provider registering."));
		}

		/**
		 * Registers the contained protocol provider.
		 * # Process all possible errors that may occur during the registration process.
		 * # This is now handled with pps registration process
		 */
		@Override
		public void run()
		{
			try {
				protocolProvider.register(secAuth);
			}
			catch (OperationFailedException ex) {
				handleOperationFailedException(ex);
			}
			catch (Throwable ex) {
				// cmeng: all exceptions will be handled within pps
				logger.error("Failed to register protocol provider. ", ex);
			}
		}

		private void handleOperationFailedException(OperationFailedException ex)
		{
			logger.error("Provider failed to register with: ", ex);
			if (OperationFailedException.NETWORK_FAILURE == ex.getErrorCode()) {
				loginRenderer.protocolProviderConnectionFailed(protocolProvider,
						LoginManager.this);
			}
		}
	}

	/**
	 * Unregisters a protocol provider in a separate thread.
	 */
	private static class UnregisterProvider extends Thread
	{
		ProtocolProviderService protocolProvider;

		UnregisterProvider(ProtocolProviderService protocolProvider)
		{
			this.protocolProvider = protocolProvider;
		}

		/**
		 * Unregisters the contained protocol provider and process all possible errors that may
		 * occur during the un-registration process.
		 */
		@Override
		public void run()
		{
			try {
				protocolProvider.unregister(true);
			}
			catch (OperationFailedException ex) {
				logger.error("Provider failed unRegistration with error: " + ex);

//				String alertMsg = "Provider could not be unregistered due to ";
//				int errorCode = ex.getErrorCode();
//				if (errorCode == OperationFailedException.GENERAL_ERROR) {
//					logger.error(alertMsg + "general error: " + ex);
//				}
//				else if (errorCode == OperationFailedException.INTERNAL_ERROR) {
//					logger.error(alertMsg + "internal error: " + ex);
//				}
//				else if (errorCode == OperationFailedException.NETWORK_FAILURE) {
//					logger.error(alertMsg + "network failure: " + ex);
//				}
				UtilActivator.getAlertUIService().showAlertDialog(
						UtilActivator.getResources().getI18NString("service.gui.ERROR"),
						UtilActivator.getResources().getI18NString(
								"service.gui.LOGOFF_NOT_SUCCEEDED",
								new String[]{protocolProvider.getAccountID().getUserID(),
										protocolProvider.getAccountID().getService()}));
			}
		}
	}
}
