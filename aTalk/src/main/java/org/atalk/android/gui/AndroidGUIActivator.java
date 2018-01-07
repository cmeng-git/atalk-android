/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 * 
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.atalk.android.gui;

import android.content.Context;

import net.java.sip.communicator.service.contactlist.MetaContactListService;
import net.java.sip.communicator.service.contactsource.*;
import net.java.sip.communicator.service.credentialsstorage.CredentialsStorageService;
import net.java.sip.communicator.service.filehistory.FileHistoryService;
import net.java.sip.communicator.service.globaldisplaydetails.GlobalDisplayDetailsService;
import net.java.sip.communicator.service.gui.*;
import net.java.sip.communicator.service.metahistory.MetaHistoryService;
import net.java.sip.communicator.service.msghistory.MessageHistoryService;
import net.java.sip.communicator.service.muc.MUCService;
import net.java.sip.communicator.service.protocol.*;
import net.java.sip.communicator.service.protocol.globalstatus.GlobalStatusService;
import net.java.sip.communicator.service.systray.SystrayService;
import net.java.sip.communicator.util.*;
import net.java.sip.communicator.util.account.LoginManager;

import org.atalk.android.aTalkApp;
import org.atalk.android.gui.account.AndroidLoginRenderer;
import org.atalk.android.gui.chat.ChatSessionManager;
import org.atalk.android.gui.contactlist.ContactListFragment;
import org.atalk.android.gui.contactlist.model.MetaContactListAdapter;
import org.atalk.android.gui.login.AndroidSecurityAuthority;
import org.atalk.android.gui.util.AlertUIServiceImpl;
import org.atalk.crypto.CryptoFragment;
import org.atalk.service.configuration.ConfigurationService;
import org.atalk.service.neomedia.MediaService;
import org.atalk.service.resources.ResourceManagementService;
import org.osgi.framework.*;

import java.util.*;

/**
 * Creates <tt>LoginManager</tt> and registers <tt>AlertUIService</tt>. It's moved here from
 * launcher <tt>Activity</tt> because it could be created multiple times and result in multiple
 * objects/registrations for those services. It also guarantees that they wil be registered
 * each time OSGI service starts.
 *
 * @author Pawel Domas
 * @author Eng Chong Meng
 */
public class AndroidGUIActivator implements BundleActivator
{
	/**
	 * The {@link LoginManager}
	 */
	private static LoginManager loginManager;

	private static AndroidUIServiceImpl uiService = null;
	/**
	 * The OSGI bundle context.
	 */
	public static BundleContext bundleContext;

	/**
	 * The presence status handler
	 */
	private PresenceStatusHandler presenceStatusHandler;

	/**
	 * Android login renderer impl.
	 */
	private static AndroidLoginRenderer loginRenderer;

	/**
	 * Configuration service instance.
	 */
	private static ConfigurationService configService;
	private static MetaHistoryService metaHistoryService;

	/**
	 * <tt>MetaContactListService</tt> cached instance.
	 */
	private static MetaContactListService metaCListService;
	private static SystrayService systrayService;
	private static ResourceManagementService resourcesService;
	private static MediaService mediaService;
	private static GlobalStatusService globalStatusService;

	private static DemuxContactSourceService demuxContactSourceService;
	/**
	 * <tt>GlobalDisplayDetailsService</tt> instance.
	 */
	private static GlobalDisplayDetailsService globalDisplayService;
	private static AlertUIService alertUIService;
	private static CredentialsStorageService credentialsService;
	private static MUCService mucService;
	private static MessageHistoryService messageHistoryService;

	private static ContactListFragment contactListFragment;

	/**
	 * The registered PhoneNumberI18nService.
	 */
	private static PhoneNumberI18nService phoneNumberI18nService;

	/**
	 * The contact list object.
	 */

	private static MetaContactListAdapter contactListAdapter;

	/**
	 * Called when this bundle is started.
	 *
	 * @param bundleContext
	 * 		The execution context of the bundle being started.
	 * @throws Exception
	 * 		if the bundle is not correctly started
	 */

	public void start(BundleContext bundleContext)
			throws Exception
	{
		Context androidContext = aTalkApp.getGlobalContext();
		SecurityAuthority securityAuthority = new AndroidSecurityAuthority();

		loginRenderer = new AndroidLoginRenderer(securityAuthority);
		loginManager = new LoginManager(loginRenderer);

		// Register the alert service android implementation.
		alertUIService = new AlertUIServiceImpl(androidContext);
		bundleContext.registerService(AlertUIService.class.getName(), alertUIService, null);

		// Creates and registers presence status handler
		this.presenceStatusHandler = new PresenceStatusHandler();
		presenceStatusHandler.start(bundleContext);

		AccountManager accountManager
				= ServiceUtils.getService(bundleContext, AccountManager.class);
		if (accountManager.getStoredAccounts().size() > 0) {
			new Thread(new Runnable()
			{
				public void run()
				{
					loginManager.runLogin();
				}
			}).start();
		}

		ConfigurationUtils.loadGuiConfigurations();

		// Register show history settings OTR link listener
		ChatSessionManager.addChatLinkListener(new CryptoFragment.ShowHistoryLinkListener());

		AndroidGUIActivator.bundleContext = bundleContext;
		// Registers UIService stub
		uiService = new AndroidUIServiceImpl(securityAuthority);
		bundleContext.registerService(UIService.class.getName(), uiService, null);
	}

	/**
	 * Called when this bundle is stopped so the Framework can perform the bundle-specific
	 * activities necessary to stop the bundle.
	 *
	 * @param bundleContext
	 * 		The execution context of the bundle being stopped.
	 * @throws Exception
	 * 		If this method throws an exception, the bundle is still marked as stopped, and the
	 * 		Framework will remove the bundle's listeners, unregister all services registered by
	 * 		the bundle, and release all services used by the bundle.
	 */
	public void stop(BundleContext bundleContext)
			throws Exception
	{
		presenceStatusHandler.stop(bundleContext);

		// Clears chat sessions
		ChatSessionManager.dispose();
		loginRenderer = null;
		loginManager = null;
		configService = null;
		globalDisplayService = null;
		metaCListService = null;
		AndroidGUIActivator.bundleContext = null;
	}

	/**
	 * Returns the <tt>ConfigurationService</tt>.
	 *
	 * @return the <tt>ConfigurationService</tt>.
	 */
	public static ConfigurationService getConfigurationService()
	{
		if (configService == null) {
			configService = ServiceUtils.getService(bundleContext, ConfigurationService.class);
		}
		return configService;
	}

	/**
	 * Returns the <tt>MetaHistoryService</tt> obtained from the bundle context.
	 *
	 * @return the <tt>MetaHistoryService</tt> obtained from the bundle context
	 */
	public static MetaHistoryService getMetaHistoryService()
	{
		if (metaHistoryService == null) {
			metaHistoryService = ServiceUtils.getService(bundleContext, MetaHistoryService.class);
		}
		return metaHistoryService;
	}

	/**
	 * Returns <tt>MetaContactListService</tt>.
	 *
	 * @return the <tt>MetaContactListService</tt>.
	 */
	public static MetaContactListService getContactListService()
	{
		if (metaCListService == null) {
			metaCListService
					= ServiceUtils.getService(bundleContext, MetaContactListService.class);
		}
		return metaCListService;
	}

	public static FileHistoryService getFileHistoryService()
	{
		return ServiceUtils.getService(bundleContext, FileHistoryService.class);
	}

	/**
	 * Returns the <tt>GlobalStatusService</tt> obtained from the bundle context.
	 *
	 * @return the <tt>GlobalStatusService</tt> obtained from the bundle context
	 */
	public static GlobalStatusService getGlobalStatusService()
	{
		if (globalStatusService == null) {
			globalStatusService
					= ServiceUtils.getService(bundleContext, GlobalStatusService.class);
		}
		return globalStatusService;
	}

	/**
	 * Returns a reference to the UIService implementation currently registered in the bundle
	 * context or null if no such implementation was found.
	 *
	 * @return a reference to a UIService implementation currently registered in the bundle
	 * context or null if no such implementation was found.
	 */
	public static AndroidUIServiceImpl getUIService()
	{
		return uiService;
	}

	/**
	 * Returns the implementation of the <tt>AlertUIService</tt>.
	 *
	 * @return the implementation of the <tt>AlertUIService</tt>
	 */
	public static AlertUIService getAlertUIService()
	{
		return alertUIService;
	}

	/**
	 * Returns <tt>SystrayService</tt> instance.
	 *
	 * @return <tt>SystrayService</tt> instance.
	 */
	public static SystrayService getSystrayService()
	{
		if (systrayService == null) {
			systrayService = ServiceUtils.getService(bundleContext, SystrayService.class);
		}
		return systrayService;
	}

	/**
	 * Returns <tt>ResourceManagementService</tt>.
	 *
	 * @return the <tt>ResourceManagementService</tt>.
	 */
	public static ResourceManagementService getResourcesService()
	{
		return ServiceUtils.getService(bundleContext, ResourceManagementService.class);
	}

	/**
	 * Returns the <tt>ResourceManagementService</tt>, through which we will access all resources.
	 *
	 * @return the <tt>ResourceManagementService</tt>, through which we will access all resources.
	 */
	public static ResourceManagementService getResources()
	{
		if (resourcesService == null) {
			resourcesService = ServiceUtils.getService(bundleContext,
					ResourceManagementService.class);
		}
		return resourcesService;
	}

	/**
	 * Returns the <tt>LoginManager</tt> for Android application.
	 *
	 * @return the <tt>LoginManager</tt> for Android application.
	 */
	public static LoginManager getLoginManager()
	{
		return loginManager;
	}

	/**
	 * Return Android login renderer.
	 *
	 * @return Android login renderer.
	 */
	public static AndroidLoginRenderer getLoginRenderer()
	{
		return loginRenderer;
	}


	/**
	 * Returns the <tt>DemuxContactSourceService</tt> obtained from the bundle context.
	 *
	 * @return the <tt>DemuxContactSourceService</tt> obtained from the bundle context
	 */
	public static DemuxContactSourceService getDemuxContactSourceService()
	{
		if (demuxContactSourceService == null) {
			demuxContactSourceService
					= ServiceUtils.getService(
					bundleContext,
					DemuxContactSourceService.class);
		}
		return demuxContactSourceService;
	}

	/**
	 * Returns the <tt>GlobalDisplayDetailsService</tt> obtained from the bundle context.
	 *
	 * @return the <tt>GlobalDisplayDetailsService</tt> obtained from the bundle context
	 */
	public static GlobalDisplayDetailsService getGlobalDisplayDetailsService()
	{
		if (globalDisplayService == null) {
			globalDisplayService = ServiceUtils.getService(bundleContext,
					GlobalDisplayDetailsService.class);
		}
		return globalDisplayService;
	}

	/**
	 * Returns a list of all registered contact sources.
	 *
	 * @return a list of all registered contact sources
	 */
	public static List<ContactSourceService> getContactSources()
	{
		List<ContactSourceService> contactSources = new Vector<>();
		ServiceReference[] serRefs = null;
		try {
			serRefs = bundleContext.getServiceReferences(ContactSourceService.class.getName(),
					null);
		}
		catch (InvalidSyntaxException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

		if ((serRefs != null) && (serRefs.length != 0)) {
			for (ServiceReference serRef : serRefs) {
				ContactSourceService contactSource
						= (ContactSourceService) bundleContext.getService(serRef);
				contactSources.add(contactSource);
			}
		}
		return contactSources;
	}


	/**
	 * Returns an instance of the <tt>MediaService</tt> obtained from the bundle context.
	 *
	 * @return an instance of the <tt>MediaService</tt> obtained from the bundle context
	 */
	public static MediaService getMediaService()
	{
		if (mediaService == null) {
			mediaService = ServiceUtils.getService(bundleContext, MediaService.class);
		}
		return mediaService;
	}

	/**
	 * Sets the <tt>contactListAdapter</tt> component currently used to show the contact list.
	 *
	 * @param cList
	 * 		the contact list object to set
	 */
	public static void setContactListAdapter(MetaContactListAdapter cList)
	{
		contactListAdapter = cList;
	}

	/**
	 * Returns the component used to show the contact list.
	 *
	 * @return the component used to show the contact list
	 */
	public static MetaContactListAdapter getContactListAdapter()
	{
		return contactListAdapter;
	}


	/**
	 * Sets the <tt>contactListFragment</tt> component currently used to show the contact list.
	 *
	 * @param cListFragment
	 * 		the contact list fragment object to set
	 */
	public static void setContactListFragment(ContactListFragment cListFragment)
	{
		contactListFragment = cListFragment;
	}

	/**
	 * Returns the ContactListFragment that contains the contact list.
	 *
	 * @return the ContactListFragment that contains the contact lists
	 */
	public static ContactListFragment getContactListFragment()
	{
		return contactListFragment;
	}

	/**
	 * Returns the <tt>CredentialsStorageService</tt>.
	 *
	 * @return the <tt>CredentialsStorageService</tt>.
	 */
	/**
	 * Returns a reference to a CredentialsStorageService implementation currently registered in
	 * the bundle context or null if no such implementation was found.
	 *
	 * @return a currently valid implementation of the CredentialsStorageService.
	 */
	public static CredentialsStorageService getCredentialsStorageService()
	{
		if (credentialsService == null) {
			credentialsService = ServiceUtils.getService(bundleContext,
					CredentialsStorageService.class);
		}
		return credentialsService;

	}

	/**
	 * Returns a reference to a MUCService implementation currently registered in the bundle
	 * context or null if no such implementation was found.
	 *
	 * @return a currently valid implementation of the MUCService.
	 */
	public static MUCService getMUCService()
	{
		if (mucService == null) {
			mucService = ServiceUtils.getService(bundleContext, MUCService.class);
		}
		return mucService;
	}

	/**
	 * Gets the service giving access to message history.
	 *
	 * @return the service giving access to message history.
	 */
	public static MessageHistoryService getMessageHistoryService()
	{
		if (messageHistoryService == null)
			messageHistoryService = ServiceUtils.getService(bundleContext,
					MessageHistoryService.class);
		return messageHistoryService;
	}

	/**
	 * Returns the PhoneNumberI18nService.
	 *
	 * @return returns the PhoneNumberI18nService.
	 */
	public static PhoneNumberI18nService getPhoneNumberI18nService()
	{
		if (phoneNumberI18nService == null) {
			phoneNumberI18nService = ServiceUtils.getService(bundleContext,
					PhoneNumberI18nService.class);
		}
		return phoneNumberI18nService;
	}
}
