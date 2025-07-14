/*
 * aTalk, android VoIP and Instant Messaging client
 * Copyright 2014-2022 Eng Chong Meng
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
package org.atalk.android.gui.call;

import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.widget.AbsListView;
import android.widget.Button;
import android.widget.ExpandableListView;
import android.widget.ExpandableListView.OnChildClickListener;
import android.widget.ExpandableListView.OnGroupClickListener;

import java.util.Collection;

import net.java.sip.communicator.service.contactlist.MetaContact;
import net.java.sip.communicator.service.protocol.CallPeer;
import net.java.sip.communicator.service.protocol.Contact;
import net.java.sip.communicator.service.protocol.OperationSetAdvancedTelephony;

import org.atalk.android.R;
import org.atalk.android.gui.aTalk;
import org.atalk.android.gui.contactlist.ContactListFragment;
import org.atalk.android.gui.contactlist.model.BaseContactListAdapter;
import org.atalk.android.gui.contactlist.model.MetaContactListAdapter;
import org.atalk.android.gui.contactlist.model.MetaGroupExpandHandler;
import org.jxmpp.jid.Jid;

import timber.log.Timber;

/**
 * The CallTransferDialog is for user to select the desired contact to transfer the call to.
 *
 * @author Eng Chong Meng
 */
public class CallTransferDialog extends Dialog
        implements OnChildClickListener, OnGroupClickListener, DialogInterface.OnShowListener {
    private final CallPeer mInitialPeer;

    private CallPeer mCallPeer = null;
    private Contact mSelectedContact = null;
    private Button mTransferButton;

    /**
     * Contact list data model.
     */
    private MetaContactListAdapter contactListAdapter;

    /**
     * The contact list view.
     */
    private ExpandableListView transferListView;

    /**
     * Constructs the <code>CallTransferDialog</code>.
     * aTalk callPeers contains at most one callPeer for attended call transfer
     *
     * @param mContext android Context
     * @param initialPeer the callPeer that launches this dialog, and to which the call transfer request is sent
     * @param callPeers contains callPeer for attended call transfer, empty otherwise
     */
    public CallTransferDialog(Context mContext, CallPeer initialPeer, Collection<CallPeer> callPeers) {
        super(mContext);
        mInitialPeer = initialPeer;
        if (!callPeers.isEmpty()) {
            mCallPeer = callPeers.iterator().next();
        }
        Timber.d("Active call peers: %s", callPeers);
        setOnShowListener(this);
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setTitle((mInitialPeer.getPeerJid().asBareJid()));

        this.setContentView(R.layout.call_transfer_dialog);
        transferListView = findViewById(R.id.TransferListView);
        transferListView.setSelector(R.drawable.list_selector_state);
        transferListView.setOnChildClickListener(this);
        transferListView.setOnGroupClickListener(this);
        transferListView.setChoiceMode(AbsListView.CHOICE_MODE_SINGLE);
        initListAdapter();

        mTransferButton = findViewById(R.id.buttonTransfer);
        mTransferButton.setOnClickListener(v -> {
            transferCall();
            closeDialog();
        });
        findViewById(R.id.buttonCancel).setOnClickListener(v -> closeDialog());
        updateTransferState();
    }

    /**
     * Transfer call to the selected contact as Unattended or Attended if mCallPeer != null
     */
    private void transferCall() {
        if (mCallPeer != null) {
            Jid callContact = mSelectedContact.getJid();
            if (callContact.isParentOf(mCallPeer.getPeerJid())) {
                CallManager.transferCall(mInitialPeer, mCallPeer);
                return;
            }
        }
        CallManager.transferCall(mInitialPeer, mSelectedContact.getAddress());
    }

    /**
     * Enable the mTransfer button if mSelected != null
     */
    private void updateTransferState() {
        if (mSelectedContact == null) {
            mTransferButton.setEnabled(false);
            mTransferButton.setAlpha(.3f);
        }
        else {
            mTransferButton.setEnabled(true);
            mTransferButton.setAlpha(1.0f);
        }
    }

    private void initListAdapter() {
        transferListView.setAdapter(getContactListAdapter());

        // Attach contact groups expand memory
        MetaGroupExpandHandler listExpandHandler = new MetaGroupExpandHandler(contactListAdapter, transferListView);
        listExpandHandler.bindAndRestore();

        // setDialogMode to true to avoid contacts being filtered
        contactListAdapter.setDialogMode(true);

        // Update ExpandedList View
        contactListAdapter.invalidateViews();
    }

    private MetaContactListAdapter getContactListAdapter() {
        if (contactListAdapter == null) {
            // FFR: clf may be null; use new instance will crash dialog on select contact
            ContactListFragment clf = (ContactListFragment) aTalk.getFragment(aTalk.CL_FRAGMENT);
            contactListAdapter = new MetaContactListAdapter(clf, false);
            contactListAdapter.initModelData();
        }
        // Do not include groups with zero member in main contact list
        contactListAdapter.nonZeroContactGroupList();

        return contactListAdapter;
    }

    /**
     * Callback method to be invoked when a child in this expandable list has been clicked.
     *
     * @param parent The ExpandableListView where the click happened
     * @param v The view within the expandable list/ListView that was clicked
     * @param groupPosition The group position that contains the child that was clicked
     * @param childPosition The child position within the group
     * @param id The row id of the child that was clicked
     *
     * @return True if the click was handled
     */
    @Override
    public boolean onChildClick(ExpandableListView listView, View v, int groupPosition, int childPosition, long id) {
        BaseContactListAdapter adapter = (BaseContactListAdapter) listView.getExpandableListAdapter();
        Object clicked = adapter.getChild(groupPosition, childPosition);

        if ((clicked instanceof MetaContact)) {
            MetaContact metaContact = (MetaContact) clicked;
            if (!metaContact.getContactsForOperationSet(OperationSetAdvancedTelephony.class).isEmpty()) {
                mSelectedContact = metaContact.getDefaultContact();
                v.setSelected(true);
                updateTransferState();
                return true;
            }
        }
        return false;
    }

    /**
     * Expands/collapses the group given by <code>groupPosition</code>.
     *
     * Group collapse will clear all highlight of any selected contact; On expansion, allow time
     * for view to expand before proceed to refresh the selected contact's highlight
     *
     * @param parent the parent expandable list view
     * @param v the view
     * @param groupPosition the position of the group
     * @param id the identifier
     *
     * @return <code>true</code> if the group click action has been performed
     */
    @Override
    public boolean onGroupClick(ExpandableListView parent, View v, int groupPosition, long id) {
        if (transferListView.isGroupExpanded(groupPosition))
            transferListView.collapseGroup(groupPosition);
        else {
            transferListView.expandGroup(groupPosition, true);
            new Handler(Looper.getMainLooper()).postDelayed(()
                    -> refreshContactSelected(groupPosition), 500);
        }
        return true;
    }

    /**
     * Refresh highlight for the selected contact when:
     * a. Dialog onShow
     * b. User collapse and expand group
     *
     * @param grpPosition the contact list group position
     */
    private void refreshContactSelected(int grpPosition) {
        int lastIndex = transferListView.getCount();
        for (int index = 0; index <= lastIndex; index++) {
            long lPosition = transferListView.getExpandableListPosition(index);

            int groupPosition = ExpandableListView.getPackedPositionGroup(lPosition);
            if ((grpPosition == -1) || (groupPosition == grpPosition)) {
                int childPosition = ExpandableListView.getPackedPositionChild(lPosition);

                MetaContact mContact = ((MetaContact) contactListAdapter.getChild(groupPosition, childPosition));
                if (mContact != null) {
                    Jid mJid = mContact.getDefaultContact().getJid();
                    View mView = transferListView.getChildAt(index);

                    if (mSelectedContact != null) {
                        if (mJid.isParentOf(mSelectedContact.getJid())) {
                            mView.setSelected(true);
                            break;
                        }
                    }
                    else if (mCallPeer != null) {
                        if (mJid.isParentOf(mCallPeer.getPeerJid())) {
                            mSelectedContact = mContact.getDefaultContact();
                            mView.setSelected(true);
                            break;
                        }
                    }
                }
            }
        }
    }

    @Override
    public void onShow(DialogInterface arg0) {
        refreshContactSelected(-1);
        updateTransferState();
    }

    public void closeDialog() {
        // must clear dialogMode on exit dialog
        contactListAdapter.setDialogMode(false);
        cancel();
    }
}
