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

import android.app.AlertDialog;
import android.content.DialogInterface;
import android.os.Bundle;
import android.view.View;

import net.java.sip.communicator.service.protocol.AccountID;
import net.java.sip.communicator.service.protocol.ProtocolProviderService;
import net.java.sip.communicator.util.account.AccountUtils;

import org.atalk.android.R;
import org.atalk.service.osgi.OSGiActivity;
import org.jivesoftware.smack.SmackException;
import org.jivesoftware.smack.XMPPException;
import org.jivesoftware.smackx.omemo.OmemoFingerprint;
import org.jivesoftware.smackx.omemo.OmemoManager;
import org.jivesoftware.smackx.omemo.exceptions.CorruptedOmemoKeyException;
import org.jivesoftware.smackx.omemo.internal.OmemoDevice;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Hashtable;
import java.util.List;
import java.util.Map;

/**
 * In case your device list gets filled with old unused identities, you can clean it up. This
 * will remove all active devices from the device list and only publish the device you are using
 * right now.
 *
 * @author Eng Chong Meng
 */
public class OmemoDeviceDeleteDialog extends OSGiActivity
{
    private OmemoFingerprint remoteFingerprint = null;
    private static OmemoManager mOmemoManager;
    private static OmemoDevice mOmemoDevice;

    /**
     * {@inheritDoc}
     */
    @Override
    protected void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);
        final Map<String, ProtocolProviderService> accountMap = new Hashtable<>();
        final List<CharSequence> accounts = new ArrayList<>();

        Collection<ProtocolProviderService> providers = AccountUtils.getRegisteredProviders();
        for (ProtocolProviderService pps : providers) {
            if (pps.getConnection().isAuthenticated()) {
                AccountID accountId = pps.getAccountID();
                String userId = accountId.getUserID();
                accountMap.put(userId, pps);
                accounts.add(userId);
            }
        }

        final boolean[] checkedItems = new boolean[accountMap.size()];
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle(R.string.pref_omemo_purge_devices_title);
        builder.setMultiChoiceItems(accounts.toArray(new CharSequence[accounts.size()]),
                checkedItems, new DialogInterface.OnMultiChoiceClickListener()
                {
                    @Override
                    public void onClick(DialogInterface dialog, int which, boolean isChecked)
                    {
                        checkedItems[which] = isChecked;
                        final AlertDialog alertDialog = (AlertDialog) dialog;
                        for (boolean item : checkedItems) {
                            if (item) {
                                alertDialog.getButton(DialogInterface.BUTTON_POSITIVE).setEnabled(true);
                                return;
                            }
                        }
                        alertDialog.getButton(DialogInterface.BUTTON_POSITIVE).setEnabled(false);
                    }
                });

        builder.setNegativeButton(R.string.service_gui_CANCEL,
                new DialogInterface.OnClickListener()
                {
                    @Override
                    public void onClick(DialogInterface dialog, int which)
                    {
                        finish();
                    }
                });
        builder.setPositiveButton(R.string.crypto_dialog_button_DELETE,
                new DialogInterface.OnClickListener()
                {
                    @Override
                    public void onClick(DialogInterface dialog, int which)
                    {
                        for (int i = 0; i < checkedItems.length; ++i) {
                            if (checkedItems[i]) {
                                ProtocolProviderService pps = accountMap.get(accounts.get(i).toString());
                                if (pps != null) {
                                    OmemoManager omemoManager
                                            = OmemoManager.getInstanceFor(pps.getConnection());
                                    try {
                                        omemoManager.purgeDevices();
                                    } catch (SmackException | InterruptedException
                                            | XMPPException.XMPPErrorException
                                            | CorruptedOmemoKeyException e) {
                                        e.printStackTrace();
                                    }
                                }
                            }
                        }
                        finish();
                    }
                });
        AlertDialog dialog = builder.create();
        dialog.show();
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(false);
    }

    /**
     * Method fired when the ok button is clicked.
     *
     * @param v
     *         ok button's <tt>View</tt>.
     */
    public void onOkClicked(View v)
    {
        finish();
    }

    /**
     * Method fired when the cancel button is clicked.
     *
     * @param v
     *         the cancel button's <tt>View</tt>
     */
    public void onCancelClicked(View v)
    {
        finish();
    }
}
