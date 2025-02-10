/*
 * aTalk, android VoIP and Instant Messaging client
 * Copyright 2014~2024 Eng Chong Meng
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
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.widget.CheckBox;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.concurrent.Executors;

import net.java.sip.communicator.impl.callhistory.CallHistoryActivator;
import net.java.sip.communicator.impl.msghistory.MessageHistoryActivator;
import net.java.sip.communicator.impl.msghistory.MessageHistoryServiceImpl;
import net.java.sip.communicator.impl.protocol.jabber.OperationSetPersistentPresenceJabberImpl;
import net.java.sip.communicator.service.callhistory.CallHistoryService;
import net.java.sip.communicator.service.contactlist.MetaContact;
import net.java.sip.communicator.service.contactlist.MetaContactGroup;
import net.java.sip.communicator.service.contactlist.MetaContactListService;
import net.java.sip.communicator.service.msghistory.MessageHistoryService;
import net.java.sip.communicator.service.muc.ChatRoomWrapper;
import net.java.sip.communicator.service.protocol.ChatRoom;
import net.java.sip.communicator.service.protocol.Contact;

import org.atalk.android.R;
import org.atalk.android.aTalkApp;
import org.atalk.android.gui.AppGUIActivator;
import org.atalk.android.gui.chat.ChatPanel;
import org.atalk.android.gui.chat.ChatSession;
import org.atalk.android.gui.chat.ChatSessionManager;
import org.atalk.android.gui.chat.chatsession.ChatSessionRecord;
import org.atalk.android.gui.dialogs.CustomDialogCbox;
import org.atalk.android.gui.dialogs.DialogActivity;
import org.jivesoftware.smack.XMPPConnection;
import org.jivesoftware.smackx.blocking.BlockingCommandManager;
import org.jxmpp.jid.Jid;
import org.jxmpp.util.XmppStringUtils;

import timber.log.Timber;

/**
 * The <code>EntityListHelper</code> is the class through which we make operations with the
 * <code>MetaContact</code> or <code>ChatRoomWrapper</code> in the list. All methods in this class are static.
 *
 * @author Eng Chong Meng
 */
public class EntityListHelper {
    public static final int SINGLE_ENTITY = 1;
    public static final int ALL_ENTITY = 2;

    /**
     * Set the contact blocking status with option apply to all contacts on domain
     *
     * @param context Context
     * @param contact Contact
     * @param setBlock ture to block contact
     *
     * @see OperationSetPersistentPresenceJabberImpl#onJidsBlocked(List) etc
     */
    public static void setEntityBlockState(final Context context, final Contact contact, boolean setBlock) {
        Jid contactJid = contact.getJid();

        // Disable Domain Block option if user is on the same Domain
        boolean cbEnable = !contactJid.asDomainBareJid().isParentOf(contact.getProtocolProvider().getOurJid());

        String title = context.getString(setBlock ? R.string.contact_block : R.string.contact_unblock);
        String message = context.getString(setBlock ?
                R.string.contact_block_text : R.string.contact_unblock_text, contactJid);
        String cbMessage = context.getString(R.string.domain_blocking, contactJid.asDomainBareJid());
        String btnText = context.getString(setBlock ? R.string.block : R.string.unblock);

        Bundle args = new Bundle();
        args.putString(CustomDialogCbox.ARG_MESSAGE, message);
        args.putString(CustomDialogCbox.ARG_CB_MESSAGE, cbMessage);
        args.putBoolean(CustomDialogCbox.ARG_CB_CHECK, false);
        args.putBoolean(CustomDialogCbox.ARG_CB_ENABLE, cbEnable);

        // Displays the history delete dialog and waits for user confirmation
        DialogActivity.showCustomDialog(aTalkApp.getInstance(), title, CustomDialogCbox.class.getName(),
                args, btnText, new DialogActivity.DialogListener() {
                    public boolean onConfirmClicked(DialogActivity dialog) {
                        CheckBox cbDomain = dialog.findViewById(R.id.cb_option);
                        final Jid entityJid = cbEnable && cbDomain.isChecked() ?
                                contactJid.asDomainBareJid() : contactJid;

                        XMPPConnection connection = contact.getProtocolProvider().getConnection();
                        BlockingCommandManager blockManager = BlockingCommandManager.getInstanceFor(connection);
                        try {
                            if (setBlock) {
                                blockManager.blockContacts(Collections.singletonList(entityJid));
                            }
                            else {
                                blockManager.unblockContacts(Collections.singletonList(entityJid));
                            }
                        } catch (Exception e) {
                            Timber.w("Block Entity %s failed: %s", contactJid, e.getMessage());
                        }
                        return true;
                    }

                    @Override
                    public void onDialogCancelled(DialogActivity dialog) {
                    }
                }, null);
    }

    /**
     * Removes given <code>metaContact</code> from the contact list. Asks the user for confirmation before proceed.
     * a. Remove all the chat messages and chatSession records from the database.
     * b. Remove metaContact from the roster etc in DB via MclStorageManager#fireMetaContactEvent.
     * Note: DomainJid will not be removed.
     *
     * @param metaContact the contact to be removed from the list.
     */
    public static void removeEntity(TaskCompleteListener caller, final MetaContact metaContact, final ChatPanel chatPanel) {
        String message;
        String title;

        Context context = aTalkApp.getInstance();
        title = context.getString(R.string.remove_contact);
        Contact contact = metaContact.getDefaultContact();
        Jid contactJid = contact.getJid();

        // Allow both contact or DomainBareJid to be remove
        if (contactJid != null) {
            Jid userJid = contact.getProtocolProvider().getAccountID().getEntityBareJid();
            message = context.getString(R.string.remove_contact_prompt, userJid, contactJid);
        }
        else {
            aTalkApp.showToastMessage(R.string.contact_invalid, contact);
            return;
        }

        DialogActivity.showConfirmDialog(context, title, message,
                context.getString(R.string.remove), new DialogActivity.DialogListener() {
                    @Override
                    public boolean onConfirmClicked(DialogActivity dialog) {
                        doRemoveContact(caller, metaContact);
                        if (chatPanel != null) {
                            ChatSessionManager.removeActiveChat(chatPanel);
                        }
                        return true;
                    }

                    @Override
                    public void onDialogCancelled(DialogActivity dialog) {
                    }
                }
        );
    }

    /**
     * Routine to remove the specified metaContact
     *
     * @param metaContact the metaContact to be removed
     */
    private static void doRemoveContact(final TaskCompleteListener caller, final MetaContact metaContact) {
        // Prevent NetworkOnMainThreadException
        new Thread(() -> {
            MetaContactListService metaContactListService = AppGUIActivator.getContactListService();
            CallHistoryService CHS = CallHistoryActivator.getCallHistoryService();
            try {
                new doEraseEntityChatHistory(caller, null, null, true).execute(metaContact);
                CHS.eraseLocallyStoredCallHistory(metaContact);
                metaContactListService.removeMetaContact(metaContact);
            } catch (Exception ex) {
                DialogActivity.showDialog(aTalkApp.getInstance(),
                        aTalkApp.getResString(R.string.remove_contact), ex.getMessage());
            }
        }).start();
    }

    /**
     * Removes the given <code>MetaContactGroup</code> from the list.
     *
     * @param group the <code>MetaContactGroup</code> to remove
     */
    public static void removeMetaContactGroup(final MetaContactGroup group) {
        Context ctx = aTalkApp.getInstance();
        String message = ctx.getString(R.string.remove_group_prompt, group.getGroupName());

        DialogActivity.showConfirmDialog(ctx, ctx.getString(R.string.remove), message, ctx.getString(R.string.remove_group),
                new DialogActivity.DialogListener() {
                    @Override
                    public boolean onConfirmClicked(DialogActivity dialog) {
                        doRemoveGroup(group);
                        return true;
                    }

                    @Override
                    public void onDialogCancelled(DialogActivity dialog) {
                    }
                });
    }

    /**
     * Removes given group from the contact list. Catches any exceptions and shows error alert.
     *
     * @param group the group to remove from the contact list.
     */
    private static void doRemoveGroup(final MetaContactGroup group) {
        // Prevent NetworkOnMainThreadException
        new Thread(() -> {
            Context ctx = aTalkApp.getInstance();
            try {
                AppGUIActivator.getContactListService().removeMetaContactGroup(group);
            } catch (Exception ex) {
                DialogActivity.showDialog(ctx, ctx.getString(R.string.remove_group), ex.getMessage());
            }
        }).start();
    }

    // ----------------- Erase History for metaContact or ChatRoom----------------------- //

    /**
     * Erase chat history for either MetaContact, ChatRoomWrapper or ChatSessionRecord.
     *
     * @param caller the listener to callback with result.
     * @param obj descriptor either MetaContact, ChatRoomWrapper or ChatSessionRecord.
     * @param msgUUIDs list of message UID to be deleted. null to delete all for the specified desc
     */
    public static void eraseEntityChatHistory(final TaskCompleteListener caller, final Object obj,
            final List<String> msgUUIDs, final List<File> msgFiles) {
        String entityJid;
        if (obj instanceof MetaContact)
            entityJid = ((MetaContact) obj).getDisplayName();
        else if (obj instanceof ChatRoomWrapper)
            entityJid = XmppStringUtils.parseLocalpart(((ChatRoomWrapper) obj).getChatRoomID());
        else if (obj instanceof ChatSessionRecord) {
            entityJid = ((ChatSessionRecord) obj).getEntityId();
        }
        else
            return;

        String title = aTalkApp.getResString(R.string.history_contact, entityJid);
        String message = aTalkApp.getResString(R.string.history_purge_for_contact_warning, entityJid);
        String cbMessage = aTalkApp.getResString(R.string.history_purge_media);
        String btnText = aTalkApp.getResString(R.string.purge);

        Bundle args = new Bundle();
        args.putString(CustomDialogCbox.ARG_MESSAGE, message);
        args.putString(CustomDialogCbox.ARG_CB_MESSAGE, cbMessage);
        args.putBoolean(CustomDialogCbox.ARG_CB_CHECK, true);
        args.putBoolean(CustomDialogCbox.ARG_CB_ENABLE, true);

        // Displays the history delete dialog and waits for user confirmation
        DialogActivity.showCustomDialog(aTalkApp.getInstance(), title, CustomDialogCbox.class.getName(),
                args, btnText, new DialogActivity.DialogListener() {
                    public boolean onConfirmClicked(DialogActivity dialog) {
                        CheckBox cbMediaDelete = dialog.findViewById(R.id.cb_option);
                        boolean mediaDelete = cbMediaDelete.isChecked();
                        if (obj instanceof ChatSessionRecord)
                            new doEraseEntityChatHistory(caller, msgUUIDs, msgFiles, mediaDelete)
                                    .execute(((ChatSessionRecord) obj).getSessionUuid());
                        else
                            new doEraseEntityChatHistory(caller, msgUUIDs, msgFiles, mediaDelete).execute(obj);
                        return true;
                    }

                    @Override
                    public void onDialogCancelled(DialogActivity dialog) {
                    }
                }, null);
    }

    /**
     * Perform history message deletion in background.
     * Purge all history messages for the descriptor if messageUUIDs is null
     * Note: if the sender deletes the media content immediately after sending, only the tmp copy is deleted
     */
    private static class doEraseEntityChatHistory {
        private final TaskCompleteListener mCallback;
        private final boolean isPurgeMediaFile;
        private final List<String> msgUUIDs;
        private List<File> msgFiles;

        private doEraseEntityChatHistory(TaskCompleteListener caller, List<String> msgUUIDs, List<File> msgFiles, boolean purgeMedia) {
            this.mCallback = caller;
            this.msgUUIDs = msgUUIDs;
            this.msgFiles = msgFiles;
            this.isPurgeMediaFile = purgeMedia;
        }

        public void execute(Object... mDescriptor) {
            Executors.newSingleThreadExecutor().execute(() -> {
                final int msgCount = doInBackground(mDescriptor);

                new Handler(Looper.getMainLooper()).post(() -> {
                    mCallback.onTaskComplete(msgCount, msgUUIDs);
                });
            });
        }

        private Integer doInBackground(Object... mDescriptor) {
            int msgCount = 0;
            Object desc = mDescriptor[0];
            if ((desc instanceof MetaContact) || (desc instanceof ChatRoomWrapper) || desc instanceof String) {
                MessageHistoryServiceImpl mhs = MessageHistoryActivator.getMessageHistoryService();
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
                    msgCount = mhs.eraseLocallyStoredChatHistory(metaContact, msgUUIDs);
                }
                else if (desc instanceof ChatRoomWrapper) {
                    ChatRoom chatRoom = ((ChatRoomWrapper) desc).getChatRoom();
                    msgCount = mhs.eraseLocallyStoredChatHistory(chatRoom, msgUUIDs);
                }
                else {
                    String sessionUuid = (String) desc;
                    msgCount = mhs.purgeLocallyStoredHistory(Collections.singletonList(sessionUuid), true);
                }
            }
            return msgCount;
        }
    }

    // ----------- Erase all the local stored chat history for all the entities (currently this is disabled) ------------- //

    /**
     * Erase all the local stored chat history for all the entities i.e. MetaContacts or ChatRoomWrappers.
     *
     * @param caller to which to return results.
     */
    public static void eraseAllEntityHistory(final TaskCompleteListener caller) {
        Context ctx = aTalkApp.getInstance();
        String title = ctx.getString(R.string.history);
        String message = ctx.getString(R.string.history_purge_all_warning);

        DialogActivity.showConfirmDialog(ctx, title, message, ctx.getString(R.string.purge),
                new DialogActivity.DialogListener() {
                    @Override
                    public boolean onConfirmClicked(DialogActivity dialog) {
                        CheckBox cbMediaDelete = dialog.findViewById(R.id.cb_option);
                        boolean mediaDelete = cbMediaDelete.isChecked();

                        // EntityListHelper mErase = new EntityListHelper();
                        new doEraseAllEntityHistory(caller, mediaDelete).execute();
                        return true;
                    }

                    @Override
                    public void onDialogCancelled(DialogActivity dialog) {
                    }
                }
        );
    }

    private static class doEraseAllEntityHistory {
        private final boolean isPurgeMediaFile;
        private final TaskCompleteListener mCallback;

        private doEraseAllEntityHistory(TaskCompleteListener caller, boolean purgeMedia) {
            this.mCallback = caller;
            this.isPurgeMediaFile = purgeMedia;
        }

        public void execute() {
            Executors.newSingleThreadExecutor().execute(() -> {
                int msgCount = doInBackground();

                new Handler(Looper.getMainLooper()).post(() -> {
                    mCallback.onTaskComplete(msgCount, null);
                });
            });
        }

        private int doInBackground() {
            MessageHistoryService mhs = MessageHistoryActivator.getMessageHistoryService();
            if (isPurgeMediaFile) {
                // purge all the files of the deleted messages
                List<String> msgFiles = mhs.getLocallyStoredFilePath();
                for (String msgFile : msgFiles) {
                    File file = new File(msgFile);
                    if (file.exists() && !file.delete())
                        Timber.w("Failed to delete the file: %s", msgFile);
                }
            }
            int msgCount = mhs.eraseLocallyStoredChatHistory(ChatSession.MODE_SINGLE);
            msgCount += mhs.eraseLocallyStoredChatHistory(ChatSession.MODE_MULTI);
            return msgCount;
        }
    }

    // ----------------- Erase Call History ----------------------- //

    /**
     * Erase local store call history.
     *
     * @param caller to which to return the results.
     * @param callUUIDs list of call record UID to be deleted. null to delete all for the specified desc
     */
    public static void eraseEntityCallHistory(TaskCompleteListener caller, List<String> callUUIDs) {
        // Displays the call history delete dialog and waits for user
        Context context = aTalkApp.getInstance();

        DialogActivity.showConfirmDialog(context, R.string.call_history_name,
                R.string.call_history_remove_warning, R.string.purge,
                new DialogActivity.DialogListener() {

                    public boolean onConfirmClicked(DialogActivity dialog) {
                        new doEraseEntityCallHistory(caller, callUUIDs, null).execute();
                        return true;
                    }

                    @Override
                    public void onDialogCancelled(DialogActivity dialog) {
                    }
                }
        );
    }

    public static void eraseEntityCallHistory(TaskCompleteListener caller, Date endDate) {
        new doEraseEntityCallHistory(caller, null, endDate).execute();
    }

    /**
     * Perform history message delete in background.
     * Purge all history messages for the descriptor if messageUUIDs is null
     */
    private static class doEraseEntityCallHistory {
        private final TaskCompleteListener mCallback;
        private final List<String> callUUIDs;
        private final Date mEndDate;

        /**
         * To delete call history based on given parameters either callUUIDs or endDate
         *
         * @param caller i.e. CallHistoryFragment.this to which to return the results.
         * @param callUUIDs list of callUuids to be deleted OR;
         * @param endDate records on and before the given endDate toe be deleted
         */
        private doEraseEntityCallHistory(TaskCompleteListener caller, List<String> callUUIDs, Date endDate) {
            this.mCallback = caller;
            this.callUUIDs = callUUIDs;
            this.mEndDate = endDate;
        }

        public void execute() {
            Executors.newSingleThreadExecutor().execute(() -> {
                int msgCount = doInBackground();

                new Handler(Looper.getMainLooper()).post(() -> {
                    mCallback.onTaskComplete(msgCount, callUUIDs);
                });
            });
        }

        private Integer doInBackground() {
            CallHistoryService CHS = CallHistoryActivator.getCallHistoryService();

            if (mEndDate == null) {
                CHS.eraseLocallyStoredCallHistory(callUUIDs);
                return callUUIDs.size();
            }
            else {
                return CHS.eraseLocallyStoredCallHistoryBefore(mEndDate);
            }
        }
    }

    public interface TaskCompleteListener {
        /**
         * Return the deleted messages Count and deleted messageUuid list.
         *
         * @param msgCount deleted message count.
         * @param deletedUUIDs deleted message uuid.
         */
        void onTaskComplete(int msgCount, List<String> deletedUUIDs);
    }
}
