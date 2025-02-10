/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.atalk.android.gui.contactlist;

import android.app.Activity;
import android.content.Context;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import net.java.sip.communicator.service.contactlist.MetaContactGroup;
import net.java.sip.communicator.service.contactlist.MetaContactListException;
import net.java.sip.communicator.service.contactlist.MetaContactListService;

import org.atalk.android.BaseFragment;
import org.atalk.android.R;
import org.atalk.android.aTalkApp;
import org.atalk.android.gui.AppGUIActivator;
import org.atalk.android.gui.dialogs.DialogActivity;
import org.atalk.android.gui.util.ViewUtil;
import org.atalk.android.gui.util.event.EventListener;

import timber.log.Timber;

/**
 * Dialog allowing user to create new contact group.
 *
 * @author Pawel Domas
 * @author Eng Chong Meng
 */
public class AddGroupDialog extends BaseFragment {
    /**
     * {@inheritDoc}
     */
    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        return inflater.inflate(R.layout.create_group, container, false);
    }

    /**
     * Displays create contact group dialog. If the source wants to be notified about the result
     * should pass the listener here or <code>null</code> otherwise.
     *
     * @param parent the parent <code>Activity</code>
     * @param createListener listener for contact group created event that will receive newly created instance of
     * the contact group or <code>null</code> in case user cancels the dialog.
     */
    public static void showCreateGroupDialog(Activity parent, EventListener<MetaContactGroup> createListener) {
        DialogActivity.showCustomDialog(parent,
                parent.getString(R.string.create_group),
                AddGroupDialog.class.getName(), null,
                parent.getString(R.string.create),
                new DialogListenerImpl(createListener), null);
    }

    /**
     * Implements <code>DialogActivity.DialogListener</code> interface and handles contact group creation process.
     */
    static class DialogListenerImpl implements DialogActivity.DialogListener {
        /**
         * Contact created event listener.
         */
        private final EventListener<MetaContactGroup> listener;

        /**
         * Newly created contact group.
         */
        private MetaContactGroup newMetaGroup;

        /**
         * Thread that runs create group process.
         */
        private Thread createThread;

        /**
         * Creates new instance of <code>DialogListenerImpl</code>.
         *
         * @param createListener create group listener if any.
         */
        public DialogListenerImpl(EventListener<MetaContactGroup> createListener) {
            this.listener = createListener;
        }

        // private ProgressDialog progressDialog;

        @Override
        public boolean onConfirmClicked(DialogActivity dialog) {
            if (createThread != null)
                return false;

            View view = dialog.getContentFragment().getView();
            String groupName = (view == null) ? null : ViewUtil.toString(view.findViewById(R.id.editText));
            if (groupName == null) {
                showErrorMessage(dialog.getString(R.string.add_group_error_empty_name));
                return false;
            }
            else {
                // TODO: in progress dialog removed for simplicity
                // Add it here if operation will be taking too much time (seems to finish fast for now)
                // displayOperationInProgressDialog(dialog);

                this.createThread = new CreateGroup(AppGUIActivator.getContactListService(), groupName);
                createThread.start();

                try {
                    // Wait for create group thread to finish
                    createThread.join();
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                }
                if (listener != null)
                    listener.onChangeEvent(newMetaGroup);

                return true;
            }
        }

        /**
         * Shows given error message as an alert.
         *
         * @param errorMessage the error message to show.
         */
        private void showErrorMessage(String errorMessage) {
            Context ctx = aTalkApp.getInstance();
            DialogActivity.showDialog(ctx, ctx.getString(R.string.error), errorMessage);
        }

        @Override
        public void onDialogCancelled(DialogActivity dialog) {
        }

        /**
         * Creates a new meta contact group in a separate thread.
         */
        private class CreateGroup extends Thread {
            /**
             * Contact list instance.
             */
            MetaContactListService mcl;

            /**
             * Name of the contact group to create.
             */
            String groupName;

            /**
             * Creates new instance of <code>AddGroupDialog</code>.
             *
             * @param mcl contact list service instance.
             * @param groupName name of the contact group to create.
             */
            CreateGroup(MetaContactListService mcl, String groupName) {
                this.mcl = mcl;
                this.groupName = groupName;
            }

            @Override
            public void run() {
                try {
                    newMetaGroup = mcl.createMetaContactGroup(mcl.getRoot(), groupName);
                } catch (MetaContactListException ex) {
                    Timber.e(ex);
                    Context ctx = aTalkApp.getInstance();

                    int errorCode = ex.getErrorCode();

                    if (errorCode == MetaContactListException.CODE_GROUP_ALREADY_EXISTS_ERROR) {
                        showErrorMessage(ctx.getString(R.string.add_group_error_exist,
                                groupName));
                    }
                    else if (errorCode == MetaContactListException.CODE_LOCAL_IO_ERROR) {
                        showErrorMessage(ctx.getString(R.string.add_group_error_local,
                                groupName));
                    }
                    else if (errorCode == MetaContactListException.CODE_NETWORK_ERROR) {
                        showErrorMessage(ctx.getString(R.string.add_group_error_network,
                                groupName));
                    }
                    else {
                        showErrorMessage(ctx.getString(R.string.add_group_failed,
                                groupName));
                    }
                }
                /*
                 * finally { hideOperationInProgressDialog(); }
                 */
            }
        }
    }
}
