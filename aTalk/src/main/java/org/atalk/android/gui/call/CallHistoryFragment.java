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
package org.atalk.android.gui.call;

import android.app.DatePickerDialog;
import android.app.TimePickerDialog;
import android.content.Context;
import android.graphics.drawable.BitmapDrawable;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Handler;
import android.util.SparseBooleanArray;
import android.view.ActionMode;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AbsListView;
import android.widget.BaseAdapter;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.fragment.app.FragmentActivity;

import net.java.sip.communicator.impl.callhistory.CallHistoryActivator;
import net.java.sip.communicator.service.callhistory.CallHistoryService;
import net.java.sip.communicator.service.callhistory.CallPeerRecord;
import net.java.sip.communicator.service.callhistory.CallRecord;
import net.java.sip.communicator.service.contactlist.MetaContact;
import net.java.sip.communicator.service.contactlist.MetaContactGroup;
import net.java.sip.communicator.service.contactlist.MetaContactListService;
import net.java.sip.communicator.service.protocol.AccountID;
import net.java.sip.communicator.service.protocol.CallPeerState;
import net.java.sip.communicator.service.protocol.Contact;
import net.java.sip.communicator.service.protocol.OperationSet;
import net.java.sip.communicator.service.protocol.OperationSetBasicTelephony;
import net.java.sip.communicator.service.protocol.OperationSetPresence;
import net.java.sip.communicator.service.protocol.OperationSetVideoTelephony;
import net.java.sip.communicator.service.protocol.ProtocolProviderService;
import net.java.sip.communicator.service.protocol.event.ContactPresenceStatusChangeEvent;
import net.java.sip.communicator.service.protocol.event.ContactPresenceStatusListener;
import net.java.sip.communicator.util.account.AccountUtils;

import org.atalk.android.R;
import org.atalk.android.aTalkApp;
import org.atalk.android.gui.AndroidGUIActivator;
import org.atalk.android.gui.call.telephony.TelephonyFragment;
import org.atalk.android.gui.chat.ChatFragment;
import org.atalk.android.gui.contactlist.model.MetaContactRenderer;
import org.atalk.android.gui.util.EntityListHelper;
import org.atalk.service.osgi.OSGiActivity;
import org.atalk.service.osgi.OSGiFragment;
import org.jetbrains.annotations.NotNull;
import org.jxmpp.jid.DomainBareJid;
import org.jxmpp.jid.Jid;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collection;
import java.util.Date;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import timber.log.Timber;

/**
 * The user interface that allows user to view the call record history.
 *
 * @author Eng Chong Meng
 */
public class CallHistoryFragment extends OSGiFragment
        implements View.OnClickListener, ContactPresenceStatusListener, EntityListHelper.TaskCompleted {
    /**
     * A map of <contact, MetaContact>
     */
    private final Map<String, MetaContact> mMetaContacts = new LinkedHashMap<>();

    /**
     * The list of call records
     */
    private final List<CallRecord> callRecords = new ArrayList<>();

    /**
     * The Call record list view adapter for user selection
     */
    private CallHistoryAdapter callHistoryAdapter;

    /**
     * The call history list view representing the chat.
     */
    private ListView callListView;

    /**
     * UI thread handler used to call all operations that access data model. This guarantees that
     * it's accessed from the main thread.
     */
    protected final Handler uiHandler = OSGiActivity.uiHandler;

    /**
     * View for room configuration title description from the room configuration form
     */
    private TextView mTitle;
    private Context mContext = null;

    /**
     * {@inheritDoc}
     */
    @Override
    public void onAttach(@NonNull Context context) {
        super.onAttach(context);
        mContext = context;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public View onCreateView(@NotNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View contentView = inflater.inflate(R.layout.call_history, container, false);
        mTitle = contentView.findViewById(R.id.call_history);

        callListView = contentView.findViewById(R.id.callListView);
        callHistoryAdapter = new CallHistoryAdapter(inflater);
        callListView.setAdapter(callHistoryAdapter);

        // Using the contextual action mode with multi-selection
        callListView.setChoiceMode(ListView.CHOICE_MODE_MULTIPLE_MODAL);
        callListView.setMultiChoiceModeListener(mMultiChoiceListener);

        return contentView;
    }

    /**
     * Adapter displaying all the available call history records for user selection.
     */
    private class CallHistoryAdapter extends BaseAdapter {
        private final LayoutInflater mInflater;
        public int CALL_RECORD = 1;

        private CallHistoryAdapter(LayoutInflater inflater) {
            mInflater = inflater;
            new getCallRecords(new Date()).execute();
        }

        @Override
        public int getCount() {
            return callRecords.size();
        }

        @Override
        public Object getItem(int position) {
            return callRecords.get(position);
        }

        @Override
        public long getItemId(int position) {
            return position;
        }

        @Override
        public int getItemViewType(int position) {
            return CALL_RECORD;
        }

        @Override
        public int getViewTypeCount() {
            return 1;
        }

        @Override
        public boolean isEmpty() {
            return getCount() == 0;
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            CallRecordViewHolder callRecordViewHolder;
            CallRecord callRecord = callRecords.get(position);

            if (convertView == null) {
                convertView = mInflater.inflate(R.layout.call_history_row, parent, false);

                callRecordViewHolder = new CallRecordViewHolder();
                callRecordViewHolder.avatar = convertView.findViewById(R.id.avatar);
                callRecordViewHolder.callType = convertView.findViewById(R.id.callType);

                callRecordViewHolder.callButton = convertView.findViewById(R.id.callButton);
                callRecordViewHolder.callButton.setOnClickListener(CallHistoryFragment.this);
                callRecordViewHolder.callButton.setTag(callRecordViewHolder);

                callRecordViewHolder.callVideoButton = convertView.findViewById(R.id.callVideoButton);
                callRecordViewHolder.callVideoButton.setOnClickListener(CallHistoryFragment.this);
                callRecordViewHolder.callVideoButton.setTag(callRecordViewHolder);

                callRecordViewHolder.callType = convertView.findViewById(R.id.callType);
                callRecordViewHolder.contactId = convertView.findViewById(R.id.contactId);
                callRecordViewHolder.callInfo = convertView.findViewById(R.id.callInfo);

                convertView.setTag(callRecordViewHolder);
            } else {
                callRecordViewHolder = (CallRecordViewHolder) convertView.getTag();
            }

            callRecordViewHolder.childPosition = position;

            // Must init child Tag here as reused convertView may not necessary contains the correct crWrapper
            // View callInfoView = convertView.findViewById(R.id.callInfoView);
            // callInfoView.setOnClickListener(CallHistoryFragment.this);
            // callInfoView.setOnLongClickListener(CallHistoryFragment.this);

            CallPeerRecord peerRecord = callRecord.getPeerRecords().get(0);
            String peer = peerRecord.getPeerAddress();
            MetaContact metaContact = mMetaContacts.get(peer.split("/")[0]);
            callRecordViewHolder.metaContact = metaContact;

            if (metaContact != null) {
                BitmapDrawable avatar = MetaContactRenderer.getAvatarDrawable(metaContact);
                ChatFragment.setAvatar(callRecordViewHolder.avatar, avatar);
            }
            setCallState(callRecordViewHolder.callType, callRecord);

            callRecordViewHolder.callButton.setVisibility(isShowCallBtn(metaContact) ? View.VISIBLE : View.GONE);
            callRecordViewHolder.callVideoButton.setVisibility(isShowVideoCallBtn(metaContact) ? View.VISIBLE : View.GONE);

            callRecordViewHolder.contactId.setText(peerRecord.getPeerAddress());
            callRecordViewHolder.callInfo.setText(callRecord.toString());

            return convertView;
        }

        /**
         * Retrieve the call history records from locally stored database
         * Populate the fragment with the call record for use in getView()
         */
        private class getCallRecords extends AsyncTask<Void, Void, Void> {
            final Date mEndDate;

            public getCallRecords(Date date) {
                mEndDate = date;
                callRecords.clear();
                mMetaContacts.clear();
                callListView.clearChoices();
            }

            @Override
            protected Void doInBackground(Void... params) {
                initMetaContactList();
                Collection<CallRecord> callRecordPPS;
                CallHistoryService CHS = CallHistoryActivator.getCallHistoryService();

                Collection<ProtocolProviderService> providers = AccountUtils.getRegisteredProviders();
                for (ProtocolProviderService pps : providers) {
                    if ((pps.getConnection() != null) && pps.getConnection().isAuthenticated()) {
                        addContactStatusListener(pps);
                        AccountID accountId = pps.getAccountID();
                        String userUuId = accountId.getAccountUniqueID();

                        callRecordPPS = CHS.findByEndDate(userUuId, mEndDate);
                        if (callRecordPPS.size() != 0)
                            callRecords.addAll(callRecordPPS);
                    }
                }
                return null;
            }

            @Override
            protected void onPostExecute(Void result) {
                if (callRecords.size() > 0) {
                    callHistoryAdapter.notifyDataSetChanged();
                }
                setTitle();
            }
        }
    }

    /**
     * Adds the given <code>addContactPresenceStatusListener</code> to listen for contact presence status change.
     *
     * @param pps the <code>ProtocolProviderService</code> for which we add the listener.
     */
    private void addContactStatusListener(ProtocolProviderService pps) {
        OperationSetPresence presenceOpSet = pps.getOperationSet(OperationSetPresence.class);
        if (presenceOpSet != null) {
            presenceOpSet.removeContactPresenceStatusListener(this);
            presenceOpSet.addContactPresenceStatusListener(this);
        }
    }

    /**
     * Sets the call state.
     *
     * @param callStateView the call state image view
     * @param callRecord    the call record.
     */
    private void setCallState(ImageView callStateView, CallRecord callRecord) {
        CallPeerRecord peerRecord = callRecord.getPeerRecords().get(0);
        CallPeerState callState = peerRecord.getState();
        int resId;

        if (CallRecord.IN.equals(callRecord.getDirection())) {
            if (callState == CallPeerState.CONNECTED)
                resId = R.drawable.call_incoming;
            else
                resId = R.drawable.call_incoming_missed;
        } else {
            resId = R.drawable.call_outgoing;
        }
        callStateView.setImageResource(resId);
    }

    private void setTitle() {
        String title = aTalkApp.getResString(R.string.service_gui_CALL_HISTORY_GROUP_NAME)
                + " (" + callRecords.size() + ")";
        mTitle.setText(title);
    }

    // Handle only if contactImpl instanceof MetaContact;
    private boolean isShowCallBtn(Object contactImpl) {
        if (contactImpl instanceof MetaContact) {
            MetaContact metaContact = (MetaContact) contactImpl;

            boolean isDomainJid = false;
            if (metaContact.getDefaultContact() != null)
                isDomainJid = metaContact.getDefaultContact().getJid() instanceof DomainBareJid;

            return isDomainJid || isShowButton(metaContact, OperationSetBasicTelephony.class);
        }
        return false;
    }

    public boolean isShowVideoCallBtn(Object contactImpl) {
        return (contactImpl instanceof MetaContact)
                && isShowButton((MetaContact) contactImpl, OperationSetVideoTelephony.class);
    }

    private boolean isShowButton(MetaContact metaContact, Class<? extends OperationSet> opSetClass) {
        return ((metaContact != null) && metaContact.getOpSetSupportedContact(opSetClass) != null);
    }

    /**
     * Initializes the adapter data.
     */
    public void initMetaContactList() {
        MetaContactListService contactListService = AndroidGUIActivator.getContactListService();
        if (contactListService != null) {
            addContacts(contactListService.getRoot());
        }
    }

    /**
     * Adds all child contacts for the given <code>group</code>. Omit metaGroup of zero child.
     *
     * @param group the group, which child contacts to add
     */
    private void addContacts(MetaContactGroup group) {
        if (group.countChildContacts() > 0) {

            // Use Iterator to avoid ConcurrentModificationException on addContact()
            Iterator<MetaContact> childContacts = group.getChildContacts();
            while (childContacts.hasNext()) {
                MetaContact metaContact = childContacts.next();
                String contactId = metaContact.getDefaultContact().getAddress();
                mMetaContacts.put(contactId, metaContact);
            }
        }

        Iterator<MetaContactGroup> subGroups = group.getSubgroups();
        while (subGroups.hasNext()) {
            addContacts(subGroups.next());
        }
    }

    @Override
    public void contactPresenceStatusChanged(ContactPresenceStatusChangeEvent evt) {
        uiHandler.post(() -> callHistoryAdapter.notifyDataSetChanged());
    }

    @Override
    public void onTaskComplete(Integer result, List<String> deletedUUIDs) {
        aTalkApp.showToastMessage(R.string.service_gui_CALL_HISTORY_REMOVE_COUNT, result);
        if (result > 0) {
            callHistoryAdapter.new getCallRecords(new Date()).execute();
        }
    }

    @Override
    public void onClick(View view) {
        CallRecordViewHolder viewHolder = null;

        Object object = view.getTag();
        if (object instanceof CallRecordViewHolder) {
            viewHolder = (CallRecordViewHolder) view.getTag();
            // int childPos = viewHolder.childPosition;
            object = viewHolder.metaContact;
        }

        if (object instanceof MetaContact) {
            MetaContact metaContact = (MetaContact) object;
            Contact contact = metaContact.getDefaultContact();

            if (contact != null) {
                Jid jid = contact.getJid();

                switch (view.getId()) {
                    case R.id.callButton:
                        if (jid instanceof DomainBareJid) {
                            TelephonyFragment extPhone = TelephonyFragment.newInstance(contact.getAddress());
                            ((FragmentActivity) mContext).getSupportFragmentManager().beginTransaction()
                                    .replace(android.R.id.content, extPhone).commit();
                            break;
                        }

                    case R.id.callVideoButton:
                        if (viewHolder != null) {
                            boolean isVideoCall = viewHolder.callVideoButton.isPressed();
                            AndroidCallUtil.createAndroidCall(aTalkApp.getGlobalContext(), jid,
                                    viewHolder.callVideoButton, isVideoCall);
                        }
                        break;

                    default:
                        break;
                }
            }
        } else {
            Timber.w("Clicked item is not a valid MetaContact");
        }
    }

    /**
     * ActionMode with multi-selection implementation for chatListView
     */
    private final AbsListView.MultiChoiceModeListener mMultiChoiceListener = new AbsListView.MultiChoiceModeListener() {
        int cPos;
        int headerCount;
        int checkListSize;
        private int mYear, mMonth, mDay, mHour, mMinute;

        SparseBooleanArray checkedList = new SparseBooleanArray();
        ;

        @Override
        public void onItemCheckedStateChanged(ActionMode mode, int position, long id, boolean checked) {
            // Here you can do something when items are selected/de-selected
            checkedList = callListView.getCheckedItemPositions();
            checkListSize = checkedList.size();
            int checkedItemCount = callListView.getCheckedItemCount();

            // Position must be aligned to the number of header views included
            cPos = position - headerCount;

            mode.invalidate();
            callListView.setSelection(position);
            mode.setTitle(String.valueOf(checkedItemCount));
        }

        // Called when the user selects a menu item. On action picked, close the CAB i.e. mode.finish();
        @Override
        public boolean onActionItemClicked(ActionMode mode, MenuItem item) {
            int cType;
            CallRecord callRecord;

            switch (item.getItemId()) {
                case R.id.cr_delete_older:
                    if (checkedList.size() > 0 && checkedList.valueAt(0)) {
                        cPos = checkedList.keyAt(0) - headerCount;
                        cType = callHistoryAdapter.getItemViewType(cPos);
                        if (cType == callHistoryAdapter.CALL_RECORD) {
                            callRecord = (CallRecord) callHistoryAdapter.getItem(cPos);
                            if (callRecord != null) {
                                Date sDate = callRecord.getStartTime();
                                final Calendar c = Calendar.getInstance();
                                c.setTime(sDate);
                                mYear = c.get(Calendar.YEAR);
                                mMonth = c.get(Calendar.MONTH);
                                mDay = c.get(Calendar.DAY_OF_MONTH);
                                mHour = c.get(Calendar.HOUR_OF_DAY);
                                mMinute = c.get(Calendar.MINUTE);

                                // Show DateTime picker for user to change the endDate
                                DatePickerDialog datePickerDialog = new DatePickerDialog(mContext,
                                        (view, year, monthOfYear, dayOfMonth) -> {
                                            mYear = year;
                                            mMonth = monthOfYear;
                                            mDay = dayOfMonth;

                                            // Launch Time Picker Dialog
                                            TimePickerDialog timePickerDialog = new TimePickerDialog(mContext,
                                                    (view1, hourOfDay, minute) -> {
                                                        c.set(mYear, mMonth, mDay, hourOfDay, minute);
                                                        EntityListHelper.eraseEntityCallHistory(CallHistoryFragment.this, c.getTime());
                                                        mode.finish();
                                                    }, mHour, mMinute, false);
                                            timePickerDialog.show();
                                        }, mYear, mMonth, mDay);
                                datePickerDialog.show();
                            }
                        }
                    }
                    return true;

                case R.id.cr_select_all:
                    int size = callHistoryAdapter.getCount();
                    if (size < 2)
                        return true;

                    for (int i = 0; i < size; i++) {
                        cPos = i + headerCount;
                        checkedList.put(cPos, true);
                        callListView.setSelection(cPos);
                    }
                    checkListSize = size;
                    mode.invalidate();
                    mode.setTitle(String.valueOf(size));
                    return true;

                case R.id.cr_delete:
                    if (checkedList.size() == 0) {
                        aTalkApp.showToastMessage(R.string.service_gui_CALL_HISTORY_REMOVE_NONE);
                        return true;
                    }

                    List<String> callUuidDel = new ArrayList<>();
                    for (int i = 0; i < checkListSize; i++) {
                        if (checkedList.valueAt(i)) {
                            cPos = checkedList.keyAt(i) - headerCount;
                            cType = callHistoryAdapter.getItemViewType(cPos);
                            if (cType == callHistoryAdapter.CALL_RECORD) {
                                callRecord = (CallRecord) callHistoryAdapter.getItem(cPos);
                                if (callRecord != null) {
                                    callUuidDel.add(callRecord.getCallUuid());
                                }
                            }
                        }
                    }
                    EntityListHelper.eraseEntityCallHistory(CallHistoryFragment.this, callUuidDel);
                    mode.finish();
                    return true;

                default:
                    return false;
            }
        }

        // Called when the action ActionMode is created; startActionMode() was called
        @Override
        public boolean onCreateActionMode(ActionMode mode, Menu menu) {
            // Inflate the menu for the CAB
            MenuInflater inflater = mode.getMenuInflater();
            inflater.inflate(R.menu.call_history_menu, menu);
            headerCount = callListView.getHeaderViewsCount();
            return true;
        }

        // Called each time the action ActionMode is shown. Always called after onCreateActionMode,
        // but may be called multiple times if the ActionMode is invalidated.
        @Override
        public boolean onPrepareActionMode(ActionMode mode, Menu menu) {
            // Here you can perform updates to the CAB due to an invalidate() request
            // Return false if nothing is done
            return false;
        }

        // Called when the user exits the action ActionMode
        @Override
        public void onDestroyActionMode(ActionMode mode) {
            // Here you can make any necessary updates to the activity when
            // the CAB is removed. By default, selected items are deselected/unchecked.
            callHistoryAdapter.new getCallRecords(new Date()).execute();
        }
    };

    private static class CallRecordViewHolder {
        ImageView avatar;
        ImageView callType;
        ImageView callButton;
        ImageView callVideoButton;

        TextView contactId;
        TextView callInfo;
        MetaContact metaContact;

        int childPosition;
    }
}
