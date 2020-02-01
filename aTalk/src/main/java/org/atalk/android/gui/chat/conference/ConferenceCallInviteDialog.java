/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */
package org.atalk.android.gui.chat.conference;

import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.ExpandableListView;
import android.widget.ExpandableListView.OnChildClickListener;

import net.java.sip.communicator.service.contactlist.MetaContact;
import net.java.sip.communicator.service.gui.ContactList;
import net.java.sip.communicator.service.protocol.*;
import net.java.sip.communicator.util.ConfigurationUtils;

import org.atalk.android.R;
import org.atalk.android.gui.call.CallManager;
import org.atalk.android.gui.contactlist.ContactListFragment;
import org.atalk.android.gui.contactlist.model.*;

import java.util.*;

/**
 * The invite dialog is the one shown when the user clicks on the conference button in the chat toolbar.
 *
 * @author Eng Chong Meng
 */
public class ConferenceCallInviteDialog extends Dialog implements OnChildClickListener, DialogInterface.OnShowListener
{
    private static boolean MUC_OFFLINE_ALLOW = true;

    private Button mInviteButton;
    private Button mCancelButton;

    /**
     * Contact list data model.
     */
    protected MetaContactListAdapter contactListAdapter;

    /**
     * Meta contact groups expand memory.
     */
    private MetaGroupExpandHandler listExpandHandler;

    /**
     * The contact list view.
     */
    protected ExpandableListView contactListView;

    /**
     * Stores last clicked <tt>MetaContact</tt>.
     */
    public MetaContact clickedContact;

    /**
     * The source contact list.
     */
    protected ContactList srcContactList;

    /**
     * The destination contact list.
     */
    protected ContactList destContactList;

    /**
     * A map of all active chats i.e. metaContactChat, MUC etc.
     */
    private static final Map<String, MetaContact> mucContactList = new LinkedHashMap<>();

    /**
     * The telephony conference into which this instance is to invite participants.
     */
    private final CallConference conference;

    /**
     * The previously selected protocol provider, with which this dialog has been instantiated.
     */
    private ProtocolProviderService preselectedProtocolProvider;

    /**
     * Indicates whether this conference invite dialog is associated with a Jitsi Videobridge invite.
     */
    private final boolean isJitsiVideobridge;

    /**
     * Initializes a new <tt>ConferenceCallInviteDialog</tt> instance which is to invite
     * contacts/participants in a specific telephony conference.
     *
     * @param conference the telephony conference in which the new instance is to invite contacts/participants
     * @param preselectedProvider the preselected protocol provider
     * @param protocolProviders the protocol providers list
     * @param isJitsiVideobridge <tt>true</tt> if this dialog should create a conference through a Jitsi Videobridge;
     * otherwise, <tt>false</tt>
     */
    public ConferenceCallInviteDialog(Context mContext, CallConference conference, ProtocolProviderService preselectedProvider,
            List<ProtocolProviderService> protocolProviders, final boolean isJitsiVideobridge)
    {
        super(mContext);

        this.conference = conference;
        this.preselectedProtocolProvider = preselectedProvider;
        this.isJitsiVideobridge = isJitsiVideobridge;

        if (preselectedProtocolProvider == null)
            initAccountSelectorPanel(protocolProviders);
        setOnShowListener(this);
    }

    /**
     * Constructs the <tt>ConferenceCallInviteDialog</tt>.
     */
    public ConferenceCallInviteDialog(Context mContext)
    {
        this(mContext, null, null, null, false);
    }

    /**
     * Creates an instance of <tt>ConferenceCallInviteDialog</tt> by specifying an already created
     * conference. To use when inviting contacts to an existing conference is needed.
     *
     * @param conference the existing <tt>CallConference</tt>
     */
    public ConferenceCallInviteDialog(Context mContext, CallConference conference)
    {
        this(mContext, conference, null, null, false);
    }

    /**
     * Creates an instance of <tt>ConferenceCallInviteDialog</tt> by specifying an already created
     * conference. To use when inviting contacts to an existing conference is needed.
     *
     * @param conference the existing <tt>CallConference</tt>
     */
    public ConferenceCallInviteDialog(Context mContext, CallConference conference,
            ProtocolProviderService preselectedProtocolProvider, boolean isJitsiVideobridge)
    {
        this(mContext, conference, preselectedProtocolProvider, null, isJitsiVideobridge);
    }

    /**
     * Creates an instance of <tt>ConferenceCallInviteDialog</tt> by specifying a preselected protocol
     * provider to be used and if this is an invite for a video bridge conference.
     *
     * @param protocolProviders the protocol providers list
     * @param isJitsiVideobridge <tt>true</tt> if this dialog should create a conference through a Jitsi Videobridge;
     * otherwise, <tt>false</tt>
     */
    public ConferenceCallInviteDialog(Context mContext,
            List<ProtocolProviderService> protocolProviders, boolean isJitsiVideobridge)
    {
        this(mContext, null, null, protocolProviders, isJitsiVideobridge);
    }

    /**
     * Creates an instance of <tt>ConferenceCallInviteDialog</tt> by specifying a preselected protocol
     * provider to be used and if this is an invite for a video bridge conference.
     *
     * @param selectedConfProvider the preselected protocol provider
     * @param isJitsiVideobridge <tt>true</tt> if this dialog should create a conference through a Jitsi Videobridge;
     * otherwise, <tt>false</tt>
     */
    public ConferenceCallInviteDialog(Context mContext, ProtocolProviderService selectedConfProvider,
            boolean isJitsiVideobridge)
    {
        this(mContext, null, selectedConfProvider, null, isJitsiVideobridge);
    }

    @Override
    public void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);
        setTitle(R.string.service_gui_INVITE_CONTACT_TO_VIDEO_BRIDGE);

        this.setContentView(R.layout.videobridge_invite_dialog);
        contactListView = this.findViewById(R.id.ContactListView);
        contactListView.setSelector(R.drawable.list_selector_state);
        contactListView.setOnChildClickListener(this);

        // Adds context menu for contact list items
        registerForContextMenu(contactListView);
        initListAdapter();

        mInviteButton = this.findViewById(R.id.button_invite);
        if (mucContactList.isEmpty()) {
            mInviteButton.setEnabled(false);
            mInviteButton.setAlpha(.3f);
        }
        else {
            mInviteButton.setEnabled(true);
            mInviteButton.setAlpha(1.0f);
        }

        mInviteButton.setOnClickListener(v -> {
            List<MetaContact> mContacts = new LinkedList<>(mucContactList.values());
            if (mContacts.size() != 0) {
                if (isJitsiVideobridge)
                    inviteJitsiVideobridgeContacts(preselectedProtocolProvider, mContacts);
                else
                    inviteContacts(mContacts);

                // Store the last used account in order to pre-select it next time.
                ConfigurationUtils.setLastCallConferenceProvider(preselectedProtocolProvider);
                closeDialog();
            }
        });

        mCancelButton = this.findViewById(R.id.buttonCancel);
        mCancelButton.setOnClickListener(v -> closeDialog());
        // this.initContactListData();
    }

    private void initListAdapter()
    {
        contactListView.setAdapter(getContactListAdapter());

        // Attach contact groups expand memory
        listExpandHandler = new MetaGroupExpandHandler(contactListAdapter, contactListView);
        listExpandHandler.bindAndRestore();

        // setDialogMode to true to avoid contacts being filtered
        contactListAdapter.setDialogMode(true);

        // Update ExpandedList View
        contactListAdapter.invalidateViews();
    }

    public void chkSelectedContact()
    {
        List<MetaContact> mContacts = new LinkedList<>(mucContactList.values());
        int indexes = contactListAdapter.getGroupCount();
        for (MetaContact mContact : mContacts) {
            int childIdx;
            for (int gIdx = 0; gIdx < indexes; gIdx++) {
                childIdx = contactListAdapter.getChildIndex(gIdx, mContact);
                if (childIdx != -1) {
                    contactListView.setItemChecked(childIdx, true);
                    break;
                }
            }
        }
    }

    public void closeDialog()
    {
        // must clear dialogMode on exit dialog
        contactListAdapter.setDialogMode(false);
        this.cancel();
    }

    private MetaContactListAdapter getContactListAdapter()
    {
        if (contactListAdapter == null) {
            ContactListFragment clf = new ContactListFragment(); //(ContactListFragment) aTalk.getFragment(aTalk.CL_FRAGMENT);
            // Disable call button options
            contactListAdapter = new MetaContactListAdapter(clf, false);
            contactListAdapter.initModelData();
        }
        // Do not include groups with zero member in main contact list
        contactListAdapter.nonZeroContactGroupList();
        return contactListAdapter;
    }

    /**
     *
     */
    @Override
    public boolean onChildClick(ExpandableListView listView, View v, int groupPosition,
            int childPosition, long id)
    {
        BaseContactListAdapter adapter = (BaseContactListAdapter) listView.getExpandableListAdapter();
        int position = adapter.getListIndex(groupPosition, childPosition);
        contactListView.setSelection(position);
        // adapter.invalidateViews();

        // Get v index for multiple selection highlight
        int index = listView.getFlatListPosition(ExpandableListView.getPackedPositionForChild(
                groupPosition, childPosition));

        Object clicked = adapter.getChild(groupPosition, childPosition);
        if ((clicked instanceof MetaContact)) {
            MetaContact metaContact = (MetaContact) clicked;
            if (MUC_OFFLINE_ALLOW
                    || !metaContact.getContactsForOperationSet(OperationSetMultiUserChat.class).isEmpty()) {
                // Toggle muc Contact Selection
                String key = metaContact.getMetaUID();
                if (mucContactList.containsKey(key)) {
                    mucContactList.remove(key);
                    listView.setItemChecked(index, false);
                    // v.setSelected(false);
                }
                else {
                    mucContactList.put(key, metaContact);
                    listView.setItemChecked(index, true);
                    // v.setSelected(true); for single item selection only
                }

                if (mucContactList.isEmpty()) {
                    mInviteButton.setEnabled(false);
                    mInviteButton.setAlpha(.3f);
                }
                else {
                    mInviteButton.setEnabled(true);
                    mInviteButton.setAlpha(1.0f);
                }
                return true;
            }
            return false;
        }
        return false;
    }

    /**
     * Initializes the account selector panel.
     *
     * @param protocolProviders the list of protocol providers we'd like to show in the account selector box
     */
    private void initAccountSelectorPanel(List<ProtocolProviderService> protocolProviders)
    {
        // Initialize the account selector box.
        if (protocolProviders != null && protocolProviders.size() > 0)
            this.initAccountListData(protocolProviders);
        else
            this.initAccountListData();
    }

    /**
     * Initializes the account selector box with the given list of <tt>ProtocolProviderService</tt>
     * -s.
     *
     * @param protocolProviders the list of <tt>ProtocolProviderService</tt>-s we'd like to show in the account
     * selector box
     */
    private void initAccountListData(List<ProtocolProviderService> protocolProviders)
    {
        for (ProtocolProviderService protocolProvider : protocolProviders) {
            // accountSelectorBox.addItem(protocolProvider);
        }

        // if (accountSelectorBox.getItemCount() > 0)
        // accountSelectorBox.setSelectedIndex(0);
    }

    /**
     * Initializes the account list.
     */
    private void initAccountListData()
    {
        List<ProtocolProviderService> protocolProviders = ProtocolProviderActivator.getProtocolProviders();
        for (ProtocolProviderService protocolProvider : protocolProviders) {
            OperationSet opSet = protocolProvider.getOperationSet(OperationSetTelephonyConferencing.class);

            if ((opSet != null) && protocolProvider.isRegistered()) {
                // accountSelectorBox.addItem(protocolProvider);
            }
        }

        // Try to select the last used account if available.
        ProtocolProviderService pps = ConfigurationUtils.getLastCallConferenceProvider();

        if (pps == null && conference != null) {
            /*
             * Pick up the first account from the ones participating in the associated telephony
             * conference which supports OperationSetTelephonyConferencing.
             */
            for (Call call : conference.getCalls()) {
                ProtocolProviderService callPps = call.getProtocolProvider();

                if (callPps.getOperationSet(OperationSetTelephonyConferencing.class) != null) {
                    pps = callPps;
                    break;
                }
            }
        }
        // if (pps != null)
        // accountSelectorBox.setSelectedItem(pps);
        // else if (accountSelectorBox.getItemCount() > 0)
        // accountSelectorBox.setSelectedIndex(0);
    }

    /**
     * Invites the contacts to the chat conference.
     */
    private void inviteContacts(List<MetaContact> mContacts)
    {
        Map<ProtocolProviderService, List<String>> selectedProviderCallees = new HashMap<>();
        List<String> callees = new ArrayList<>();

        // Collection<String> selectedContactAddresses = new ArrayList<String>();

        for (MetaContact mContact : mContacts) {
            String mAddress = mContact.getDefaultContact().getAddress();
            callees.add(mAddress);
        }

        // Invite all selected.
        if (callees.size() > 0) {
            selectedProviderCallees.put(preselectedProtocolProvider, callees);

            if (conference != null) {
                CallManager.inviteToConferenceCall(selectedProviderCallees, conference);
            }
            else {
                CallManager.createConferenceCall(selectedProviderCallees);
            }
        }
    }

    /**
     * Invites the contacts to the chat conference.
     *
     * @param contacts
     *        the list of contacts to invite
     */
//	private void inviteContacts(Collection<UIContact> contacts)
//	{
//		ProtocolProviderService selectedProvider;
//		Map<ProtocolProviderService, List<String>> selectedProviderCallees = new HashMap<ProtocolProviderService, List<String>>();
//		List<String> callees;
//
//		Iterator<UIContact> contactsIter = contacts.iterator();
//
//		while (contactsIter.hasNext()) {
//			UIContact uiContact = contactsIter.next();
//
//			Iterator<UIContactDetail> contactDetailsIter = uiContact
//				.getContactDetailsForOperationSet(OperationSetBasicTelephony.class).iterator();
//
//			// We invite the first protocol contact that corresponds to the invite provider.
//			if (contactDetailsIter.hasNext()) {
//				UIContactDetail inviteDetail = contactDetailsIter.next();
//				selectedProvider = inviteDetail
//					.getPreferredProtocolProvider(OperationSetBasicTelephony.class);
//
//				if (selectedProvider == null) {
//					// selectedProvider = (ProtocolProviderService)
//					// accountSelectorBox.getSelectedItem();
//				}
//
//				if (selectedProvider != null
//					&& selectedProviderCallees.get(selectedProvider) != null) {
//					callees = selectedProviderCallees.get(selectedProvider);
//				}
//				else {
//					callees = new ArrayList<String>();
//				}
//
//				callees.add(inviteDetail.getAddress());
//				selectedProviderCallees.put(selectedProvider, callees);
//			}
//		}
//
//		if (conference != null) {
//			CallManager.inviteToConferenceCall(selectedProviderCallees, conference);
//		}
//		else {
//			CallManager.createConferenceCall(selectedProviderCallees);
//		}
//	}

    /**
     * Invites the contacts to the chat conference.
     *
     * @param mContacts the list of contacts to invite
     */
    private void inviteJitsiVideobridgeContacts(ProtocolProviderService preselectedProvider,
            List<MetaContact> mContacts)
    {
        List<String> callees = new ArrayList<>();

        for (MetaContact mContact : mContacts) {
            String mAddress = mContact.getDefaultContact().getAddress();
            callees.add(mAddress);
        }

        // Invite all selected.
        if (callees.size() > 0) {
            if (conference != null) {
                CallManager.inviteToJitsiVideobridgeConfCall(
                        callees.toArray(new String[0]), conference.getCalls().get(0));
            }
            else {
                CallManager.createJitsiVideobridgeConfCall(preselectedProvider,
                        callees.toArray(new String[0]));
            }
        }
    }

//	/**
//	 * Invites the contacts to the chat conference.
//	 *
//	 * @param mContacts
//	 *        the list of contacts to invite
//	 */
//	private void inviteJitsiVideobridgeContacts(ProtocolProviderService preselectedProvider,
//		Collection<UIContact> mcontacts)
//	{
//		List<String> callees = new ArrayList<String>();
//		Iterator<UIContact> contactsIter = contacts.iterator();
//
//		while (contactsIter.hasNext()) {
//			UIContact uiContact = contactsIter.next();
//
//			Iterator<UIContactDetail> contactDetailsIter = uiContact
//				.getContactDetailsForOperationSet(OperationSetBasicTelephony.class).iterator();
//
//			// We invite the first protocol contact that corresponds to the invite provider.
//			if (contactDetailsIter.hasNext()) {
//				UIContactDetail inviteDetail = contactDetailsIter.next();
//				callees.add(inviteDetail.getAddress());
//			}
//		}
//
//		if (conference != null) {
//			CallManager.inviteToJitsiVideobridgeConfCall(
//				callees.toArray(new String[callees.size()]), conference.getCalls().get(0));
//		}
//		else {
//			CallManager.createJitsiVideobridgeConfCall(preselectedProvider,
//				callees.toArray(new String[callees.size()]));
//		}
//	}

    @Override
    public void onShow(DialogInterface arg0)
    {
        List<MetaContact> mContacts = new LinkedList<>(mucContactList.values());
        int indexes = contactListAdapter.getGroupCount();
        for (MetaContact mContact : mContacts) {
            int childIdx;
            for (int gIdx = 0; gIdx < indexes; gIdx++) {
                childIdx = contactListAdapter.getChildIndex(gIdx, mContact);
                if (childIdx != -1) {
                    childIdx += gIdx + 1;
                    contactListView.setItemChecked(childIdx, true);
                    break;
                }
            }
        }
    }
}
