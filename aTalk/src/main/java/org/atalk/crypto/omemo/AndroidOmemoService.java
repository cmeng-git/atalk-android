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

import org.atalk.android.R;
import org.atalk.android.aTalkApp;
import org.atalk.android.gui.dialogs.DialogActivity;
import org.jivesoftware.smack.XMPPConnection;
import org.jivesoftware.smackx.omemo.*;
import org.jxmpp.jid.BareJid;

import java.util.SortedSet;

import timber.log.Timber;

/**
 * Android Omemo service.
 *
 * @author Eng Chong Meng
 */
public class AndroidOmemoService implements OmemoManager.InitializationFinishedCallback
{
    private final OmemoManager mOmemoManager;
    private final XMPPConnection mConnection;

    public static boolean isOmemoInitSuccessful = false;

    public AndroidOmemoService(ProtocolProviderService pps)
    {
        mConnection = pps.getConnection();
        mOmemoManager = initOmemoManager(pps);

        Timber.i("### Registered omemo messageListener for: %s", pps.getAccountID().getUserID());
        OperationSetBasicInstantMessaging imOpSet =  pps.getOperationSet(OperationSetBasicInstantMessaging.class);
        ((OperationSetBasicInstantMessagingJabberImpl) imOpSet).registerOmemoListener(mOmemoManager);

        OperationSetMultiUserChat mucOpSet = pps.getOperationSet(OperationSetMultiUserChat.class);
        ((OperationSetMultiUserChatJabberImpl) mucOpSet).registerOmemoMucListener(mOmemoManager);
    }

    /**
     * Initialize store for the specific protocolProvider and Initialize the OMEMO Manager
     *
     * @param pps protocolProvider for the current user
     * @return instance of OMEMO Manager
     */
    private OmemoManager initOmemoManager(ProtocolProviderService pps)
    {
        OmemoStore mOmemoStore = OmemoService.getInstance().getOmemoStoreBackend();

        BareJid userJid;
        if (mConnection.getUser() != null) {
            userJid = mConnection.getUser().asBareJid();
        }
        else {
            userJid = pps.getAccountID().getBareJid();
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
            Timber.w("SetTrustCallBack Exception: %s", e.getMessage());
        }
        return omemoManager;
    }

    /**
     * The method should only be called upon user authentication
     * Init smack reply timeout for omemo prekey publish whose reply takes 7(normal) to 11s(bosh)
     * on Note3 & Note10 with remote server; but takes only 2s on aTalk server
     */
    public void initOmemoDevice()
    {
        isOmemoInitSuccessful = false;
        mConnection.setReplyTimeout(ProtocolProviderServiceJabberImpl.SMACK_REPLY_OMEMO_INIT_TIMEOUT);
        mOmemoManager.initializeAsync(this);
    }

    @Override
    public void initializationFinished(OmemoManager manager)
    {
        isOmemoInitSuccessful = true;
        mConnection.setReplyTimeout(ProtocolProviderServiceJabberImpl.SMACK_REPLY_TIMEOUT_DEFAULT);
        Timber.d("Initialize OmemoManager successful for %s", mConnection.getUser());
    }

    @Override
    public void initializationFailed(Exception cause)
    {
        isOmemoInitSuccessful = false;
        mConnection.setReplyTimeout(ProtocolProviderServiceJabberImpl.SMACK_REPLY_TIMEOUT_DEFAULT);

        String title = aTalkApp.getResString(R.string.omemo_init_failed_title);
        String errMsg = cause.getMessage();
        Timber.w("%s: %s", title, errMsg);
        if (errMsg != null) {
            if (errMsg.contains("CorruptedOmemoKeyException")) {
                String msg = aTalkApp.getResString(R.string.omemo_init_failed_CorruptedOmemoKeyException,
                        mOmemoManager.getOwnDevice(), errMsg);
                DialogActivity.showDialog(aTalkApp.getGlobalContext(), title, msg);
            }
            else {
                aTalkApp.showToastMessage(R.string.omemo_init_failed_noresponse, mOmemoManager.getOwnDevice());
            }
        }
    }
}