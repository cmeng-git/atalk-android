/*
 * aTalk, android VoIP and Instant Messaging client
 * Copyright 2014 Eng Chong Meng
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.atalk.crypto.omemo;

import net.java.sip.communicator.impl.protocol.jabber.*;
import net.java.sip.communicator.service.protocol.*;
import net.java.sip.communicator.util.Logger;

import org.jivesoftware.smack.packet.ExtensionElement;
import org.jivesoftware.smack.tcp.XMPPTCPConnection;
import org.jivesoftware.smackx.omemo.*;
import org.jivesoftware.smackx.omemo.element.OmemoDeviceListVAxolotlElement;
import org.jivesoftware.smackx.omemo.exceptions.*;
import org.jivesoftware.smackx.omemo.internal.OmemoDevice;
import org.jivesoftware.smackx.pep.*;
import org.jivesoftware.smackx.pubsub.*;
import org.jxmpp.jid.*;

import java.lang.reflect.*;
import java.util.Set;

/**
 * Omemo service.
 *
 * @author Eng Chong Meng
 */
public class AndroidOmemoService
{
	/**
	 * The <tt>Logger</tt> used by the <tt>AndroidOmemoService</tt> class and its
	 * instances for logging output.
	 */
	private static final Logger logger = Logger.getLogger(AndroidOmemoService.class);

	private OmemoManager mOmemoManager;
	private OmemoStore mOmemoStore;

	private OperationSetBasicInstantMessagingJabberImpl imOpSet = null;
	private OperationSetMultiUserChatJabberImpl mucOpSet = null;

	public AndroidOmemoService(ProtocolProviderService pps)
	{
		mOmemoManager = initOmemoManager(pps);

		logger.info("### Registered omemo messageListener for: " + pps.getAccountID().getUserID());
		imOpSet = (OperationSetBasicInstantMessagingJabberImpl)
				pps.getOperationSet(OperationSetBasicInstantMessaging.class);
		imOpSet.registerOmemoListener(mOmemoManager);

		mucOpSet = (OperationSetMultiUserChatJabberImpl)
				pps.getOperationSet(OperationSetMultiUserChat.class);
		mucOpSet.registerOmemoMucListener(mOmemoManager);
	}

	/**
	 * Initialize store for the specific protocolProvider and Initialize the OMEMO Manager
	 *
	 * @param pps
	 * 		protocolProvider for the current user
	 * @return instance of OMEMO Manager
	 */
	private OmemoManager initOmemoManager(ProtocolProviderService pps)
	{
		BareJid user;
		mOmemoStore = OmemoService.getInstance().getOmemoStoreBackend();

		XMPPTCPConnection connection = pps.getConnection();
		if (connection.getUser() != null) {
			user = connection.getUser().asBareJid();
		}
		else {
			user = pps.getAccountID().getFullJid().asBareJid();
		}

		int defaultDeviceId = mOmemoStore.getDefaultDeviceId(user);
		if (defaultDeviceId < 1) {
			defaultDeviceId = OmemoManager.randomDeviceId();
			mOmemoStore.setDefaultDeviceId(user, defaultDeviceId);
		}
		OmemoManager omemoManager = OmemoManager.getInstanceFor(connection, defaultDeviceId);
		PEPManager.getInstanceFor(connection).addPEPListener(buddyDeviceListUpdateListener);

		// patches for smack 4.2.1.-beta2-SNAPSHOT
		addOmemoListener(omemoManager);
		// subscribeToDeviceLists(omemoManager); not required for 4.2.2-SNAPSHOT (10/14/2017)

		return omemoManager;
	}

	private void addOmemoListener(OmemoManager omemoManager)
	{
		OmemoService omemoService = OmemoService.getInstance();
		try {
			Method addMsgListener = OmemoService.class.getDeclaredMethod
					("registerOmemoMessageStanzaListeners", OmemoManager.class);
			addMsgListener.setAccessible(true);
			addMsgListener.invoke(omemoService, omemoManager);
		}
		catch (NoSuchMethodException | IllegalAccessException | InvocationTargetException e) {
			logger.warn("Exception in addOmemoListener");
		}
	}

//  Not required for 4.2.2-SNAPSHOT (10/14/2017)
//	private void subscribeToDeviceLists(OmemoManager omemoManager)
//	{
//		OmemoService omemoService = OmemoService.getInstance();
//		try {
//			Method addPEPListener = OmemoService.class.getDeclaredMethod
//					("subscribeToDeviceLists", OmemoManager.class);
//			addPEPListener.setAccessible(true);
//			addPEPListener.invoke(omemoService, omemoManager);
//		}
//		catch (NoSuchMethodException | IllegalAccessException | InvocationTargetException e) {
//			logger.warn("Exception in subscribeToDeviceLists");
//		}
//	}

	/**
	 * PEPListener to add new buddy omemoDevice to identities table
	 */
	private PEPListener buddyDeviceListUpdateListener = new PEPListener()
	{
		@Override
		public void eventReceived(EntityBareJid from, EventElement event,
				org.jivesoftware.smack.packet.Message message)
		{
			// Our deviceList, so nothing more to do as it is handled in OmemoManager
			if ((from == null) || from.equals(mOmemoManager.getOwnJid())) {
				return;
			}

			for (ExtensionElement items : event.getExtensions()) {
				if (!(items instanceof ItemsExtension)) {
					continue;
				}

				for (ExtensionElement item : ((ItemsExtension) items).getItems()) {
					if (!(item instanceof PayloadItem<?>)) {
						continue;
					}

					PayloadItem<?> payloadItem = (PayloadItem<?>) item;
					if (!(payloadItem.getPayload() instanceof OmemoDeviceListVAxolotlElement)) {
						continue;
					}

					// Get the Device List <list>
					OmemoDeviceListVAxolotlElement omemoDeviceListElement
							= (OmemoDeviceListVAxolotlElement) payloadItem.getPayload();
					Set<Integer> deviceList = omemoDeviceListElement.getDeviceIds();
					for (int deviceID : deviceList) {
						OmemoDevice omemoDevice = new OmemoDevice(from.asBareJid(), deviceID);
						try {
							mOmemoStore.loadOmemoIdentityKey(mOmemoManager, omemoDevice);
						}
						catch (CorruptedOmemoKeyException e) {
							// Get the preKeys for missing buddy deviceID
							try {
								OmemoService.getInstance().buildSessionFromOmemoBundle(
										mOmemoManager, omemoDevice, false);
							}
							catch (CannotEstablishOmemoSessionException
									| CorruptedOmemoKeyException e1) {
								e1.printStackTrace();
							}
						}
					}
				}
			}
		}
	};
}