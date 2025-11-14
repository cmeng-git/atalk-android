/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.atalk.android.gui.contactlist;

import android.content.DialogInterface;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.TextView;

import net.java.sip.communicator.service.contactlist.MetaContact;
import net.java.sip.communicator.service.contactlist.MetaContactGroup;
import net.java.sip.communicator.service.contactlist.MetaContactListException;

import org.atalk.android.R;
import org.atalk.android.aTalkApp;
import org.atalk.android.gui.AppGUIActivator;
import org.atalk.android.gui.dialogs.BaseDialogFragment;
import org.atalk.android.gui.dialogs.DialogActivity;

import timber.log.Timber;

/**
 * Dialog that allows user to move the contact to selected group.
 *
 * @author Pawel Domas
 * @author Eng Chong Meng
 */
public class MoveToGroupDialog extends BaseDialogFragment implements DialogInterface.OnClickListener {
    /**
     * Meta UID arg key.
     */
    private static final String META_CONTACT_UID = "meta_uid";

    /**
     * Meta account UserID.
     */
    private static final String USER_ID = "userId";

    /**
     * The meta contact that will be moved.
     */
    private MetaContact metaContact;

    /**
     * Creates a new instance of <code>MoveToGroupDialog</code>.
     *
     * @param metaContact the contact that will be moved.
     *
     * @return parametrized instance of <code>MoveToGroupDialog</code>.
     */
    public static MoveToGroupDialog getInstance(MetaContact metaContact) {
        MoveToGroupDialog dialog = new MoveToGroupDialog();

        Bundle args = new Bundle();
        String userName = metaContact.getDefaultContact().getProtocolProvider().getAccountID().getUserID();
        args.putString(USER_ID, userName);
        args.putString(META_CONTACT_UID, metaContact.getMetaUID());

        dialog.setArguments(args);
        return dialog;
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View contentView = inflater.inflate(R.layout.move_to_group, container, false);

        getDialog().setTitle(R.string.move_contact);
        this.metaContact = AppGUIActivator.getContactListService()
                .findMetaContactByMetaUID(getArguments().getString(META_CONTACT_UID));

        String UserId = getArguments().getString(USER_ID);
        TextView accountOwner = contentView.findViewById(R.id.accountOwner);
        accountOwner.setText(getString(R.string.contact_owner, UserId));

        final AdapterView<MetaContactGroupAdapter> groupListView = contentView.findViewById(R.id.selectGroupSpinner);
        MetaContactGroupAdapter contactGroupAdapter
                = new MetaContactGroupAdapter(getActivity(), groupListView, true, true);
        groupListView.setAdapter(contactGroupAdapter);

        contentView.findViewById(R.id.move).setOnClickListener(v -> {
            MetaContactGroup newGroup = (MetaContactGroup) groupListView.getSelectedItem();
            if (!(newGroup.equals(metaContact.getParentMetaContactGroup()))) {
                moveContact(newGroup);
            }
            dismiss();
        });

        contentView.findViewById(R.id.cancel).setOnClickListener(v -> dismiss());
        return contentView;
    }

    private void moveContact(final MetaContactGroup selectedItem) {
        new Thread() {
            @Override
            public void run() {
                try {
                    AppGUIActivator.getContactListService().moveMetaContact(metaContact, selectedItem);
                } catch (MetaContactListException e) {
                    Timber.e(e, "%s", e.getMessage());
                    DialogActivity.showDialog(aTalkApp.getInstance(),
                            aTalkApp.getResString(R.string.error), e.getMessage());
                }
            }
        }.start();
    }

    @Override
    public void onClick(DialogInterface dialog, int which) {
    }
}
