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
package org.atalk.android.gui.util;

import android.content.Context;
import android.os.AsyncTask;
import android.os.Bundle;
import android.widget.CheckBox;

import net.java.sip.communicator.impl.callhistory.CallHistoryActivator;
import net.java.sip.communicator.service.callhistory.CallHistoryService;
import net.java.sip.communicator.service.contactlist.*;
import net.java.sip.communicator.service.msghistory.MessageHistoryService;
import net.java.sip.communicator.service.muc.ChatRoomWrapper;
import net.java.sip.communicator.service.protocol.ChatRoom;
import net.java.sip.communicator.service.protocol.Contact;

import org.atalk.android.R;
import org.atalk.android.aTalkApp;
import org.atalk.android.gui.AndroidGUIActivator;
import org.atalk.android.gui.call.CallHistoryFragment;
import org.atalk.android.gui.chat.*;
import org.atalk.android.gui.dialogs.DialogActivity;
import org.jxmpp.jid.DomainBareJid;
import org.jxmpp.jid.Jid;
import org.jxmpp.util.XmppStringUtils;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import timber.log.Timber;

import static org.atalk.android.R.id.cb_media_delete;

/**
 * The <tt>EntityListHelper</tt> is the class through which we make operations with the
 * <tt>MetaContact</tt> or <tt>ChatRoomWrapper</tt> in the list. All methods in this class are static.
 *
 * @author Eng Chong Meng
 */
public class EntityListHelper
{
    // History erase return result ZERO_ENTITY => error
    public static final int ZERO_ENTITY = 0;
    public static final int CURRENT_ENTITY = 1;
    public static final int ALL_ENTITIES = 2;

    /**
     * Removes given <tt>metaContact</tt> from the contact list. Asks the user for confirmation before proceed.
     * a. Remove all the chat messages and chatSession records from the database.
     * b. Remove metaContact from the roster in DB.
     *
     * Note: DomainJid will not be removed.
     *
     * @param metaContact the contact to be removed from the list.
     */
    public static void removeEntity(Context context, final MetaContact metaContact, final ChatPanel chatPanel)
    {
        String message;
        String title;

        title = context.getString(R.string.service_gui_REMOVE_CONTACT);
        Contact contact = metaContact.getDefaultContact();
        Jid contactJid = contact.getJid();
        if (!(contactJid instanceof DomainBareJid)) {
            message = context.getString(R.string.service_gui_REMOVE_CONTACT_TEXT,
                    contact.getProtocolProvider().getAccountID().getUserID(), contactJid);
        }
        else {
            aTalkApp.showToastMessage(R.string.service_gui_CONTACT_INVALID, contactJid);
            return;
        }

        DialogActivity.showConfirmDialog(context, title, message,
                context.getString(R.string.service_gui_REMOVE), new DialogActivity.DialogListener()
                {
                    @Override
                    public boolean onConfirmClicked(DialogActivity dialog)
                    {
                        doRemoveContact(context, metaContact);
                        if (chatPanel != null) {
                            ChatSessionManager.removeActiveChat(chatPanel);
                        }
                        return true;
                    }

                    @Override
                    public void onDialogCancelled(DialogActivity dialog)
                    {
                    }
                });
    }

    /**
     * Routine to remove metaContact
     *
     * @param contact contact to be removed
     */
    private static void doRemoveContact(final Context ctx, final MetaContact contact)
    {
        // Prevent NetworkOnMainThreadException
        new Thread(() -> {
            MetaContactListService metaContactListService = AndroidGUIActivator.getContactListService();
            try {
                metaContactListService.removeMetaContact(contact);
            } catch (Exception ex) {
                AndroidUtils.showAlertDialog(ctx, ctx.getString(R.string.service_gui_REMOVE_CONTACT), ex.getMessage());
            }
        }).start();
    }

    /**
     * Removes the given <tt>MetaContactGroup</tt> from the list.
     *
     * @param group the <tt>MetaContactGroup</tt> to remove
     */
    public static void removeMetaContactGroup(final MetaContactGroup group)
    {
        Context ctx = aTalkApp.getGlobalContext();
        String message = ctx.getString(R.string.service_gui_REMOVE_GROUP_TEXT, group.getGroupName());

        DialogActivity.showConfirmDialog(ctx, ctx.getString(R.string.service_gui_REMOVE), message, ctx.getString(R.string.service_gui_REMOVE_GROUP),
                new DialogActivity.DialogListener()
                {
                    @Override
                    public boolean onConfirmClicked(DialogActivity dialog)
                    {
                        doRemoveGroup(group);
                        return true;
                    }

                    @Override
                    public void onDialogCancelled(DialogActivity dialog)
                    {
                    }
                });
    }

    /**
     * Removes given group from the contact list. Catches any exceptions and shows error alert.
     *
     * @param group the group to remove from the contact list.
     */
    private static void doRemoveGroup(final MetaContactGroup group)
    {
        // Prevent NetworkOnMainThreadException
        new Thread(() -> {
            Context ctx = aTalkApp.getGlobalContext();
            try {
                AndroidGUIActivator.getContactListService().removeMetaContactGroup(group);
            } catch (Exception ex) {
                AndroidUtils.showAlertDialog(ctx, ctx.getString(R.string.service_gui_REMOVE_GROUP), ex.getMessage());
            }
        }).start();
    }

    // ----------------- Erase History for metaContact or ChatRoom----------------------- //

    /**
     * Erase chat history for either MetaContact or ChatRoomWrapper
     *
     * @param caller the context
     * @param desc descriptor either MetaContact or ChatRoomWrapper
     * @param msgUUIDs list of message UID to be deleted. null to delete all for the specified desc
     */
    public static void eraseEntityChatHistory(final Context caller, final Object desc, final List<String> msgUUIDs,
            final List<File> msgFiles)
    {
        String entityJid;
        if (desc instanceof MetaContact)
            entityJid = ((MetaContact) desc).getDisplayName();
        else if (desc instanceof ChatRoomWrapper)
            entityJid = XmppStringUtils.parseLocalpart(((ChatRoomWrapper) desc).getChatRoomID());
        else
            return;

        Bundle args = new Bundle();
        args.putString(ChatMessageDeleteFragment.ARG_MESSAGE,
                aTalkApp.getResString(R.string.service_gui_HISTORY_REMOVE_PER_CONTACT_WARNING, entityJid));
        String title = aTalkApp.getResString(R.string.service_gui_HISTORY_CONTACT, entityJid);

        // Displays the history delete dialog and waits for user confirmation
        DialogActivity.showCustomDialog(aTalkApp.getGlobalContext(), title, ChatMessageDeleteFragment.class.getName(),
                args, aTalkApp.getResString(R.string.service_gui_PURGE), new DialogActivity.DialogListener()
                {
                    public boolean onConfirmClicked(DialogActivity dialog)
                    {
                        CheckBox cbMediaDelete = dialog.findViewById(cb_media_delete);
                        boolean mediaDelete = cbMediaDelete.isChecked();

                        // EntityListHelper mErase = new EntityListHelper();
                        new doEraseEntityChatHistory(caller, msgUUIDs, msgFiles, mediaDelete).execute(desc);
                        return true;
                    }

                    @Override
                    public void onDialogCancelled(DialogActivity dialog)
                    {
                    }
                }, null);
    }

    /**
     * Perform history message delete in background.
     * Purge all history messages for the descriptor if messageUUIDs is null
     */
    private static class doEraseEntityChatHistory extends AsyncTask<Object, Void, Integer>
    {
        private final TaskCompleted mCallback;
        private final boolean isPurgeMediaFile;
        private final List<String> msgUUIDs;
        private List<File> msgFiles;

        private doEraseEntityChatHistory(Context context, List<String> msgUUIDs, List<File> msgFiles, boolean purgeMedia)
        {
            this.mCallback = (TaskCompleted) context;
            this.msgUUIDs = msgUUIDs;
            this.msgFiles = msgFiles;
            this.isPurgeMediaFile = purgeMedia;
        }

        @Override
        public void onPreExecute()
        {
        }

        @Override
        protected Integer doInBackground(Object... mDescriptor)
        {
            Object desc = mDescriptor[0];
            if ((desc instanceof MetaContact) || (desc instanceof ChatRoomWrapper)) {
                MessageHistoryService mhs = AndroidGUIActivator.getMessageHistoryService();
                if (isPurgeMediaFile) {
                    // null => delete all local saved files; then construct locally
                    if (msgFiles == null) {
                        msgFiles = new ArrayList<>();
                        List<String> filePathDel = mhs.getLocallyStoredFilePath(desc);
                        for (String filePath : filePathDel) {
                            msgFiles.add(new File(filePath));
                        }
                    }

                    // purge all the files of the deleted messages
                    for (File file : msgFiles) {
                        if ((file.exists() && !file.delete()))
                            Timber.e("Failed to delete file: %s", file.getName());
                    }
                }

                if (desc instanceof MetaContact) {
                    MetaContact metaContact = (MetaContact) desc;
                    mhs.eraseLocallyStoredChatHistory(metaContact, msgUUIDs);
                }
                else {
                    ChatRoom chatRoom = ((ChatRoomWrapper) desc).getChatRoom();
                    mhs.eraseLocallyStoredChatHistory(chatRoom, msgUUIDs);
                }
            }
            else {
                return ZERO_ENTITY;
            }
            return CURRENT_ENTITY;
        }

        @Override
        protected void onPostExecute(Integer result)
        {
            // Return result to caller
            mCallback.onTaskComplete(result);
        }

        @Override
        protected void onCancelled()
        {
        }
    }

    // ----------- Erase all the local stored chat history for all the entities (not called) ------------- //

    /**
     * Erase all the local stored chat history for all the entities i.e. MetaContacts or ChatRoomWrappers.
     *
     * @param caller the callback.
     */
    public static void eraseAllEntityHistory(final Context caller)
    {
        Context ctx = aTalkApp.getGlobalContext();
        String title = ctx.getString(R.string.service_gui_HISTORY);
        String message = ctx.getString(R.string.service_gui_HISTORY_REMOVE_ALL_WARNING);

        DialogActivity.showConfirmDialog(ctx, title, message, ctx.getString(R.string.service_gui_PURGE),
                new DialogActivity.DialogListener()
                {
                    @Override
                    public boolean onConfirmClicked(DialogActivity dialog)
                    {
                        CheckBox cbMediaDelete = dialog.findViewById(cb_media_delete);
                        boolean mediaDelete = cbMediaDelete.isChecked();

                        // EntityListHelper mErase = new EntityListHelper();
                        new doEraseAllEntityHistory(caller, mediaDelete).execute();
                        return true;
                    }

                    @Override
                    public void onDialogCancelled(DialogActivity dialog)
                    {
                    }
                }
        );
    }

    private static class doEraseAllEntityHistory extends AsyncTask<Void, Void, Integer>
    {
        private final boolean isPurgeMediaFile;
        private final TaskCompleted mCallback;

        private doEraseAllEntityHistory(Context context, boolean purgeMedia)
        {
            this.mCallback = (TaskCompleted) context;
            this.isPurgeMediaFile = purgeMedia;
        }

        @Override
        public void onPreExecute()
        {
        }

        @Override
        protected Integer doInBackground(Void... none)
        {
            MessageHistoryService mhs = AndroidGUIActivator.getMessageHistoryService();
            if (isPurgeMediaFile) {
                // purge all the files of the deleted messages
                List<String> msgFiles = mhs.getLocallyStoredFilePath();
                for (String msgFile : msgFiles) {
                    File file = new File(msgFile);
                    if (file.exists() && !file.delete())
                        Timber.w("Failed to delete the file: %s", msgFile);
                }
            }
            mhs.eraseLocallyStoredChatHistory(ChatSession.MODE_SINGLE);
            mhs.eraseLocallyStoredChatHistory(ChatSession.MODE_MULTI);
            return ALL_ENTITIES;
        }

        @Override
        protected void onPostExecute(Integer result)
        {
            // Return result to caller
            mCallback.onTaskComplete(result);
        }

        @Override
        protected void onCancelled()
        {
        }
    }

    // ----------------- Erase Call History ----------------------- //

    /**
     * Erase local store call history
     *
     * @param caller the context
     * @param callUUIDs list of call record UID to be deleted. null to delete all for the specified desc
     */
    public static void eraseEntityCallHistory(final CallHistoryFragment caller, final List<String> callUUIDs)
    {
        // Displays the call history delete dialog and waits for user
        DialogActivity.showConfirmDialog(caller.getContext(), R.string.service_gui_CALL_HISTORY_GROUP_NAME,
                R.string.service_gui_CALL_HISTORY_REMOVE_WARNING, R.string.service_gui_PURGE,
                new DialogActivity.DialogListener()
                {

                    public boolean onConfirmClicked(DialogActivity dialog)
                    {
                        new doEraseEntityCallHistory(caller, callUUIDs).execute();
                        return true;
                    }

                    @Override
                    public void onDialogCancelled(DialogActivity dialog)
                    {
                    }
                }
        );
    }

    /**
     * Perform history message delete in background.
     * Purge all history messages for the descriptor if messageUUIDs is null
     */
    private static class doEraseEntityCallHistory extends AsyncTask<Void, Void, Integer>
    {
        private final EntityListHelper.TaskCompleted mCallback;
        private final List<String> callUUIDs;

        private doEraseEntityCallHistory(CallHistoryFragment caller, List<String> callUUIDs)
        {
            this.mCallback = (EntityListHelper.TaskCompleted) caller;
            this.callUUIDs = callUUIDs;
        }

        @Override
        public void onPreExecute()
        {
        }

        @Override
        protected Integer doInBackground(Void... none)
        {
            CallHistoryService CHS = CallHistoryActivator.getCallHistoryService();
            CHS.eraseLocallyStoredHistory(callUUIDs);

            return callUUIDs.size();
        }

        @Override
        protected void onPostExecute(Integer result)
        {
            // Return result to caller
            if (mCallback != null)
                mCallback.onTaskComplete(result);
        }

        @Override
        protected void onCancelled()
        {
        }
    }

    public interface TaskCompleted
    {
        // Define data you like to return from AsyncTask
        void onTaskComplete(Integer result);
    }
}
