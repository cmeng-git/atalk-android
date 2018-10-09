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
import java.util.List;

/**
 * The <tt>EntityListHelper</tt> is the class through which we make operations with the
 * <tt>MetaContact</tt> or <tt>ChatRoomWrapper</tt> in the list
 * All methods in this class are static.
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
     * @param desc the contact or chatRoom to be removed from the list.
     */
    public static void removeEntity(final Object desc, final ChatPanel chatPanel)
    {
        Context ctx = aTalkApp.getGlobalContext();
        String message;
        String title;

        if (desc instanceof MetaContact) {
            title = ctx.getString(R.string.service_gui_REMOVE_CONTACT);
            MetaContact contact = (MetaContact) desc;
            if (contact.getDefaultContact() == null) {
                aTalkApp.showToastMessage(R.string.service_gui_CONTACT_INVALID, contact.getDisplayName());
                return;
            }
            message = ctx.getString(R.string.service_gui_REMOVE_CONTACT_TEXT,
                    contact.getDefaultContact().getProtocolProvider().getAccountID().getUserID(),
                    contact.getDisplayName());
        }
        else if (desc instanceof ChatRoomWrapper) {
            ChatRoomWrapper chatRoomWrapper = (ChatRoomWrapper) desc;
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
                        if (desc instanceof MetaContact) {
                            doRemoveContact((MetaContact) desc);
                        }
                        else {
                            ChatRoomWrapper chatRoomWrapper = (ChatRoomWrapper) desc;
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
     * @param contact contact to be remove
     */
    private static void doRemoveContact(final MetaContact contact)
    {
        // Prevent NetworkOnMainThreadException
        new Thread(new Runnable()
        {
            @Override
            public void run()
            {
                Context ctx = aTalkApp.getGlobalContext();
                MetaContactListService mls = AndroidGUIActivator.getContactListService();
                try {
                    mls.removeMetaContact(contact);
                } catch (Exception ex) {
                    AndroidUtils.showAlertDialog(ctx, ctx.getString(R.string.service_gui_REMOVE_CONTACT), ex.getMessage());
                }
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

        // /int returnCode = dialog.showDialog();
        // if (returnCode == MessageDialog.OK_RETURN_CODE) {
        // 		GuiActivator.getContactListService().removeMetaContactGroup(group);
        // }
        // else if (returnCode == MessageDialog.OK_DONT_ASK_CODE) {
        // 		GuiActivator.getContactListService().removeMetaContactGroup(group);
        // 		Constants.REMOVE_CONTACT_ASK = false;
        // }
    }

    /**
     * Removes given group from the contact list. Catches any exceptions and shows error alert.
     *
     * @param group the group to remove from the contact list.
     */
    private static void doRemoveGroup(final MetaContactGroup group)
    {
        // Prevent NetworkOnMainThreadException
        new Thread(new Runnable()
        {
            @Override
            public void run()
            {
                Context ctx = aTalkApp.getGlobalContext();
                try {
                    AndroidGUIActivator.getContactListService().removeMetaContactGroup(group);
                } catch (Exception ex) {
                    AndroidUtils.showAlertDialog(ctx, ctx.getString(R.string.service_gui_REMOVE_GROUP), ex.getMessage());
                }
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

        String title = aTalkApp.getResString(R.string.service_gui_HISTORY_CONTACT, entityJid);
        String message = aTalkApp.getResString(R.string.service_gui_HISTORY_REMOVE_PER_CONTACT_WARNING, entityJid);

        DialogActivity.showConfirmDialog(aTalkApp.getGlobalContext(), title, message,
                aTalkApp.getResString(R.string.service_gui_PURGE),
                new DialogActivity.DialogListener()
                {
                    @Override
                    public boolean onConfirmClicked(DialogActivity dialog)
                    {
                        EntityListHelper mErase = new EntityListHelper();
                        mErase.new doEraseEntityChatHistory(caller, msgUUIDs, msgFiles).execute(desc);
                        return true;
                    }

                    @Override
                    public void onDialogCancelled(DialogActivity dialog)
                    {
                    }
                });
    }

    /**
     * Perform history message delete in background.
     * Purge all history messages for the descriptor if messageUUIDs is null
     */
    private class doEraseEntityChatHistory extends AsyncTask<Object, Void, Integer>
    {
        private Context mContext = null;
        private TaskCompleted mCallback;
        private final List<String> messageUUIDs;
        private final List<File> messageFiles;

        private doEraseEntityChatHistory(Context context, List<String> msgUUIDs, List<File> msgFiles)
        {
            this.mContext = context;
            this.mCallback = (TaskCompleted) mContext;
            this.messageUUIDs = msgUUIDs;
            this.messageFiles = msgFiles;
        }

        @Override
        public void onPreExecute()
        {
        }

        @Override
        protected Integer doInBackground(Object... mDescriptor)
        {
            Object desc = mDescriptor[0];
            if (desc instanceof MetaContact) {
                // purge all the voice files of the deleted messages
                if (messageFiles != null) {
                    for (File file : messageFiles) {
                        if (file.exists())
                            file.delete();
                    }
                }
                MetaContact metaContact = (MetaContact) desc;
                MessageHistoryService mhs = AndroidGUIActivator.getMessageHistoryService();
                mhs.eraseLocallyStoredHistory(metaContact, messageUUIDs);
            }
            else if (desc instanceof ChatRoomWrapper) {
                ChatRoom chatRoom = ((ChatRoomWrapper) desc).getChatRoom();
                MessageHistoryService mhs = AndroidGUIActivator.getMessageHistoryService();
                mhs.eraseLocallyStoredHistory(chatRoom, messageUUIDs);
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
            this.mContext = null;
        }

        @Override
        protected void onCancelled()
        {
        }
    }

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
                        EntityListHelper mErase = new EntityListHelper();
                        mErase.new doEraseAllContactHistory(caller).execute();
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
        private Context mContext = null;
        private TaskCompleted mCallback;

        private doEraseAllContactHistory(Context context)
        {
            this.mContext = context;
            this.mCallback = (TaskCompleted) mContext;
        }

        @Override
        public void onPreExecute()
        {
        }

        @Override
        protected Integer doInBackground(Void... none)
        {
            MessageHistoryService mhs = AndroidGUIActivator.getMessageHistoryService();
            mhs.eraseLocallyStoredHistory();
            return ALL_ENTITIES;
        }

        @Override
        protected void onPostExecute(Integer result)
        {
            // Return result to caller
            mCallback.onTaskComplete(result);
            this.mContext = null;
        }

        @Override
        protected void onCancelled()
        {
        }
    }
}
