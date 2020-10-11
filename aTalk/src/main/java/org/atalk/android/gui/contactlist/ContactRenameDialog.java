/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.atalk.android.gui.contactlist;

import android.content.Context;
import android.content.DialogInterface;
import android.os.Bundle;
import android.view.*;
import android.widget.EditText;
import android.widget.TextView;

import net.java.sip.communicator.service.contactlist.MetaContact;
import net.java.sip.communicator.service.contactlist.MetaContactListException;

import org.apache.commons.lang3.StringUtils;
import org.atalk.android.R;
import org.atalk.android.aTalkApp;
import org.atalk.android.gui.AndroidGUIActivator;
import org.atalk.android.gui.util.AndroidUtils;
import org.atalk.android.gui.util.ViewUtil;
import org.atalk.service.osgi.OSGiDialogFragment;

import timber.log.Timber;

/**
 * Dialog that allows user to move the contact to selected group.
 *
 * @author Pawel Domas
 * @author Eng Chong Meng
 */
public class ContactRenameDialog extends OSGiDialogFragment
        implements DialogInterface.OnClickListener
{
    /**
     * Meta UID arg key.
     */
    private static final String META_CONTACT_UID = "meta_uid";

    /**
     * Meta account UserID.
     */
    private static final String USER_ID = "userId";

    /**
     * Meta account UserID.
     */
    private static final String CONTACT_NICK = "contactNick";

    EditText mEditName;

    /**
     * The meta contact that will be moved.
     */
    private MetaContact metaContact;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState)
    {
        getDialog().setTitle(R.string.service_gui_CONTACT_RENAME_TITLE);
        this.metaContact = AndroidGUIActivator.getContactListService()
                .findMetaContactByMetaUID(getArguments().getString(META_CONTACT_UID));

        View contentView = inflater.inflate(R.layout.contact_rename, container, false);
        String userId = getArguments().getString(USER_ID);
        TextView accountOwner = contentView.findViewById(R.id.accountOwner);
        accountOwner.setText(getString(R.string.service_gui_CONTACT_OWNER, userId));

        mEditName = contentView.findViewById(R.id.editName);
        String contactNick = getArguments().getString(CONTACT_NICK);
        if (StringUtils.isNotEmpty(contactNick))
            mEditName.setText(contactNick);

        contentView.findViewById(R.id.rename).setOnClickListener(v -> {
            String displayName = ViewUtil.toString(mEditName);
            if (displayName == null) {
                showErrorMessage(getString(R.string.service_gui_CONTACT_NAME_EMPTY));
            }
            else
                renameContact(displayName);
            dismiss();
        });

        contentView.findViewById(R.id.cancel).setOnClickListener(v -> dismiss());
        return contentView;
    }

    private void renameContact(final String newDisplayName)
    {
        new Thread()
        {
            @Override
            public void run()
            {
                try {
                    AndroidGUIActivator.getContactListService().renameMetaContact(metaContact, newDisplayName);
                } catch (MetaContactListException e) {
                    Timber.e(e, "%s", e.getMessage());
                    AndroidUtils.showAlertDialog(aTalkApp.getGlobalContext(), "Error", e.getMessage());
                }
            }
        }.start();
    }

    @Override
    public void onClick(DialogInterface dialog, int which)
    {
    }

    /**
     * Creates new instance of <tt>MoveToGroupDialog</tt>.
     *
     * @param metaContact the contact that will be moved.
     * @return parametrized instance of <tt>MoveToGroupDialog</tt>.
     */
    public static ContactRenameDialog getInstance(MetaContact metaContact)
    {
        Bundle args = new Bundle();
        String userId = metaContact.getDefaultContact().getProtocolProvider().getAccountID().getUserID();
        args.putString(USER_ID, userId);
        args.putString(META_CONTACT_UID, metaContact.getMetaUID());
        args.putString(CONTACT_NICK, metaContact.getDisplayName());
        ContactRenameDialog dialog = new ContactRenameDialog();
        dialog.setArguments(args);

        return dialog;
    }

    /**
     * Shows given error message as an alert.
     *
     * @param errMessage the error message to show.
     */
    private void showErrorMessage(String errMessage)
    {
        Context ctx = aTalkApp.getGlobalContext();
        AndroidUtils.showAlertDialog(ctx, ctx.getString(R.string.service_gui_ERROR), errMessage);
    }
}
