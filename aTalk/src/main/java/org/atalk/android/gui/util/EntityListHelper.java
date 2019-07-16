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

import net.java.sip.communicator.service.contactlist.*;
import net.java.sip.communicator.service.msghistory.MessageHistoryService;
import net.java.sip.communicator.service.muc.ChatRoomWrapper;
import net.java.sip.communicator.service.protocol.ChatRoom;

import org.atalk.android.R;
import org.atalk.android.aTalkApp;
import org.atalk.android.gui.AndroidGUIActivator;
import org.atalk.android.gui.chat.ChatPanel;
import org.atalk.android.gui.chat.ChatSessionManager;
import org.atalk.android.gui.dialogs.DialogActivity;
import org.jxmpp.util.XmppStringUtils;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import static org.atalk.android.R.id.cb_media_delete;

/**
 * The <tt>EntityListHelper</tt> is the class through which we make operations with the
 * <tt>MetaContact</tt> or <tt>ChatRoomWrapper</tt> in the list All methods in this class are static.
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
     * Removes given <tt>entity</tt> from the contact list or chatRoom list. Asks the user for
     * confirmation before proceed.
     *
     * @param descriptor the contact or chatRoom to be removed from the list.
     */
    public static void removeEntity(final Object descriptor, final ChatPanel chatPanel)
    {
        Context ctx = aTalkApp.getGlobalContext();
        String message;
        String title;

        if (descriptor instanceof MetaContact) {
            title = ctx.getString(R.string.service_gui_REMOVE_CONTACT);
            MetaContact contact = (MetaContact) descriptor;
            if (contact.getDefaultContact() == null) {
                aTalkApp.showToastMessage(R.string.service_gui_CONTACT_INVALID, contact.getDisplayName());
                return;
            }
            message = ctx.getString(R.string.service_gui_REMOVE_CONTACT_TEXT,
                    contact.getDefaultContact().getProtocolProvider().getAccountID().getUserID(),
                    contact.getDisplayName());
        }
        else if (descriptor instanceof ChatRoomWrapper) {
            ChatRoomWrapper chatRoomWrapper = (ChatRoomWrapper) descriptor;
            title = ctx.getString(R.string.service_gui_DESTROY_CHATROOM_TITLE);
            message = ctx.getString(R.string.service_gui_DESTROY_CHATROOM_TEXT,
                    chatRoomWrapper.getParentProvider().getProtocolProvider().getAccountID().getUserID(),
                    chatRoomWrapper.getChatRoomID());
        }
        else
            return;

        DialogActivity.showConfirmDialog(ctx, title, message,
                ctx.getString(R.string.service_gui_REMOVE), new DialogActivity.DialogListener()
                {
                    @Override
                    public boolean onConfirmClicked(DialogActivity dialog)
                    {
                        if (descriptor instanceof MetaContact) {
                            doRemoveContact((MetaContact) descriptor);
                        }
                        else {
                            ChatRoomWrapper chatRoomWrapper = (ChatRoomWrapper) descriptor;
                            ChatRoom chatRoom = chatRoomWrapper.getChatRoom();
                            AndroidGUIActivator.getMUCService().destroyChatRoom(chatRoomWrapper,
                                    "User requested", chatRoom.getIdentifier());
                        }
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
    private static void doRemoveContact(final MetaContact contact)
    {
        // Prevent NetworkOnMainThreadException
        new Thread(() -> {
            Context ctx = aTalkApp.getGlobalContext();
            MetaContactListService mls = AndroidGUIActivator.getContactListService();
            try {
                mls.removeMetaContact(contact);
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
        String message = ctx.getString(R.string.service_gui_REMOVE_GROUP_TEXT,
                group.getGroupName());

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

    public interface TaskCompleted
    {
        // Define data you like to return from AsyncTask
        void onTaskComplete(Integer result);
    }

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
        args.putString(HistoryDeleteFragment.ARG_MESSAGE,
                aTalkApp.getResString(R.string.service_gui_HISTORY_REMOVE_PER_CONTACT_WARNING, entityJid));
        String title = aTalkApp.getResString(R.string.service_gui_HISTORY_CONTACT, entityJid);

        // Displays the history delete dialog and waits for user confirmation
        DialogActivity.showCustomDialog(aTalkApp.getGlobalContext(), title, HistoryDeleteFragment.class.getName(),
                args, aTalkApp.getResString(R.string.service_gui_PURGE), new DialogActivity.DialogListener()
                {
                    public boolean onConfirmClicked(DialogActivity dialog)
                    {
                        CheckBox cbMediaDelete = dialog.findViewById(cb_media_delete);
                        boolean mediaDelete = cbMediaDelete.isChecked();

                        EntityListHelper mErase = new EntityListHelper();
                        mErase.new doEraseEntityChatHistory(caller, msgUUIDs, msgFiles, mediaDelete).execute(desc);
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
    private class doEraseEntityChatHistory extends AsyncTask<Object, Void, Integer>
    {
        private TaskCompleted mCallback;
        private boolean isPurgeMediaFile;
        private List<String> messageUUIDs;
        private List<File> msgFiles;

        private doEraseEntityChatHistory(Context context, List<String> msgUUIDs, List<File> msgFiles, boolean purgeMedia)
        {
            this.mCallback = (TaskCompleted) context;
            this.messageUUIDs = msgUUIDs;
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
                        if (file.exists())
                            file.delete();
                    }
                }

                if (desc instanceof MetaContact) {
                    MetaContact metaContact = (MetaContact) desc;
                    mhs.eraseLocallyStoredHistory(metaContact, messageUUIDs);
                }
                else {
                    ChatRoom chatRoom = ((ChatRoomWrapper) desc).getChatRoom();
                    mhs.eraseLocallyStoredHistory(chatRoom, messageUUIDs);
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

    /**
     * Erase all the local stored chat history for all MetaContact and ChatRoomWrapper
     *
     * @param caller the callback
     */
    public static void eraseAllContactHistory(final Context caller)
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

                        EntityListHelper mErase = new EntityListHelper();
                        mErase.new doEraseAllContactHistory(caller, mediaDelete).execute();
                        return true;
                    }

                    @Override
                    public void onDialogCancelled(DialogActivity dialog)
                    {
                    }
                }
        );
    }

    private class doEraseAllContactHistory extends AsyncTask<Void, Void, Integer>
    {
        private boolean isPurgeMediaFile;
        private TaskCompleted mCallback;

        private doEraseAllContactHistory(Context context, boolean purgeMedia)
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
                    if (file.exists())
                        file.delete();
                }
            }
            mhs.eraseLocallyStoredHistory();
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
}
