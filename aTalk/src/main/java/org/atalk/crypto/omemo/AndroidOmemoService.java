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

import net.java.sip.communicator.impl.protocol.jabber.OperationSetBasicInstantMessagingJabberImpl;
import net.java.sip.communicator.impl.protocol.jabber.OperationSetMultiUserChatJabberImpl;
import net.java.sip.communicator.service.protocol.OperationSetBasicInstantMessaging;
import net.java.sip.communicator.service.protocol.OperationSetMultiUserChat;
import net.java.sip.communicator.service.protocol.ProtocolProviderService;
import net.java.sip.communicator.util.Logger;

import org.atalk.android.R;
import org.atalk.android.aTalkApp;
import org.atalk.android.gui.dialogs.DialogActivity;
import org.jivesoftware.smack.XMPPConnection;
import org.jivesoftware.smackx.omemo.OmemoManager;
import org.jivesoftware.smackx.omemo.OmemoService;
import org.jivesoftware.smackx.omemo.OmemoStore;
import org.jxmpp.jid.BareJid;

import java.util.SortedSet;

/**
 * Android Omemo service.
 *
 * @author Eng Chong Meng
 */
public class AndroidOmemoService implements OmemoManager.InitializationFinishedCallback
{
    /**
     * The <tt>Logger</tt> used by the <tt>AndroidOmemoService</tt> class and its
     * instances for logging output.
     */
    private static final Logger logger = Logger.getLogger(AndroidOmemoService.class);

    private OmemoManager mOmemoManager;
    private OmemoStore mOmemoStore;
    private XMPPConnection mConnection;

    private OperationSetBasicInstantMessagingJabberImpl imOpSet = null;
    private OperationSetMultiUserChatJabberImpl mucOpSet = null;

    public AndroidOmemoService(ProtocolProviderService pps)
    {
        mConnection = pps.getConnection();
        mOmemoManager = initOmemoManager(pps);

        logger.info("### Registered omemo messageListener for: " + pps.getAccountID().getUserID());
        imOpSet = (OperationSetBasicInstantMessagingJabberImpl) pps.getOperationSet(OperationSetBasicInstantMessaging.class);
        imOpSet.registerOmemoListener(mOmemoManager);

        mucOpSet = (OperationSetMultiUserChatJabberImpl) pps.getOperationSet(OperationSetMultiUserChat.class);
        mucOpSet.registerOmemoMucListener(mOmemoManager);
    }

    /**
     * Initialize store for the specific protocolProvider and Initialize the OMEMO Manager
     *
     * @param pps protocolProvider for the current user
     * @return instance of OMEMO Manager
     */
    private OmemoManager initOmemoManager(ProtocolProviderService pps)
    {
        mOmemoStore = OmemoService.getInstance().getOmemoStoreBackend();

        BareJid userJid;
        if (mConnection.getUser() != null) {
            userJid = mConnection.getUser().asBareJid();
        }
        else {
            userJid = pps.getAccountID().getFullJid().asBareJid();
        }

        int defaultDeviceId;
        SortedSet<Integer> deviceIds = mOmemoStore.localDeviceIdsOf(userJid);
        if (deviceIds.isEmpty()) {
            defaultDeviceId = OmemoManager.randomDeviceId();
            ((SQLiteOmemoStore) mOmemoStore).setDefaultDeviceId(userJid, defaultDeviceId);
        }
        else {
            defaultDeviceId = deviceIds.first();
        }

        // OmemoManager omemoManager = OmemoManager.getInstanceFor(mConnection); - not working for aTalk
        OmemoManager omemoManager = OmemoManager.getInstanceFor(mConnection, defaultDeviceId);
        try {
            omemoManager.setTrustCallback(((SQLiteOmemoStore) mOmemoStore).getTrustCallBack());
        } catch (Exception e) {
            logger.warn("SetTrustCallBack Exception: " + e.getMessage());
        }
        return omemoManager;
    }

    /**
     * The method should only be called upon user authentication
     */
    public void initOmemoDevice()
    {
        // omemoManager.initialize(); or
        mOmemoManager.initializeAsync(this);
    }

    @Override
    public void initializationFinished(OmemoManager manager)
    {
        logger.info("Initialize OmemoManager successful for " + mConnection.getUser());
    }

    @Override
    public void initializationFailed(Exception cause)
    {
        String title = aTalkApp.getResString(R.string.omemo_init_failed_title);
        logger.error(title, cause);
        if (cause.getMessage().contains("CorruptedOmemoKeyException")) {
            String msg = aTalkApp.getResString(R.string.omemo_init_failed_CorruptedOmemoKeyException,
                    mOmemoManager.getOwnDevice(), cause.getMessage());
            DialogActivity.showDialog(aTalkApp.getGlobalContext(), title, msg);
        } else {
            aTalkApp.showToastMessage(R.string.omemo_init_failed_noresponse);

        }
    }
}