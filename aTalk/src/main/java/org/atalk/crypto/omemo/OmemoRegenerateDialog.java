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

import net.java.sip.communicator.service.protocol.AccountID;
import net.java.sip.communicator.service.protocol.ProtocolProviderService;
import net.java.sip.communicator.util.account.AccountUtils;

import org.atalk.android.R;
import org.atalk.service.osgi.OSGiActivity;
import org.jivesoftware.smackx.omemo.OmemoService;
import org.jivesoftware.smackx.omemo.OmemoStore;

import java.util.*;

import timber.log.Timber;

/**
 * OMEMO identities regeneration user interface.
 *
 * @author Eng Chong Meng
 */
public class OmemoRegenerateDialog extends OSGiActivity
{
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
            if ((pps.getConnection() != null) && pps.getConnection().isAuthenticated()) {
                AccountID accountId = pps.getAccountID();
                String userId = accountId.getUserID();
                accountMap.put(userId, pps);
                accounts.add(userId);
            }
        }

        final boolean[] checkedItems = new boolean[accountMap.size()];
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle(R.string.pref_omemo_regenerate_identities_title);
        builder.setMultiChoiceItems(accounts.toArray(new CharSequence[0]), checkedItems, (dialog, which, isChecked) -> {
            checkedItems[which] = isChecked;
            final AlertDialog multiChoiceDialog = (AlertDialog) dialog;
            for (boolean item : checkedItems) {
                if (item) {
                    multiChoiceDialog.getButton(DialogInterface.BUTTON_POSITIVE).setEnabled(true);
                    return;
                }
            }
            multiChoiceDialog.getButton(DialogInterface.BUTTON_POSITIVE).setEnabled(false);
        });

        builder.setNegativeButton(R.string.service_gui_CANCEL, (dialog, which) -> finish());
        builder.setPositiveButton(R.string.crypto_dialog_button_OMEMO_REGENERATE, (dialog, which) -> {
            final OmemoStore omemoStore = OmemoService.getInstance().getOmemoStoreBackend();
            new Thread()
            {
                @Override
                public void run()
                {
                    for (int i = 0; i < checkedItems.length; ++i) {
                        if (checkedItems[i]) {
                            ProtocolProviderService pps = accountMap.get(accounts.get(i).toString());
                            if (pps != null) {
                                AccountID accountID = pps.getAccountID();
                                Timber.d("Regenerate Omemo for: %s", accountID.getAccountJid());
                                ((SQLiteOmemoStore) omemoStore).regenerate(accountID);
                                // ((SQLiteOmemoStore) omemoStore).cleanServerOmemoData(accountID); // for test only
                            }
                        }
                    }
                }
            }.start();
//            for (int i = 0; i < checkedItems.length; ++i) {
//                if (checkedItems[i]) {
//                    ProtocolProviderService pps = accountMap.get(accounts.get(i).toString());
//                    if (pps != null) {
//                        AccountID accountID = pps.getAccountID();
//                        ((SQLiteOmemoStore) omemoStore).regenerate(accountID);
//                    }
//                }
//            }
            finish();
        });

        AlertDialog dialog = builder.create();
        dialog.setCancelable(false);
        dialog.setCanceledOnTouchOutside(false);
        dialog.show();
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(false);
    }
}
