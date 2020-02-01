/*
 * aTalk, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file
 * except in compliance with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the
 * License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
 * either express or implied. See the License for the specific language governing permissions
 * and limitations under the License.
 */
package org.atalk.android.gui.chat.conference;

import android.app.Dialog;
import android.content.Context;
import android.os.Bundle;
import android.text.TextUtils;
import android.widget.*;

import net.java.sip.communicator.impl.muc.MUCActivator;
import net.java.sip.communicator.service.protocol.*;

import org.atalk.android.R;
import org.atalk.android.aTalkApp;
import org.atalk.android.gui.util.AndroidUtils;
import org.atalk.android.gui.util.ViewUtil;
import org.jxmpp.jid.EntityJid;

/**
 * The dialog that pops up when a chat room invitation is received.
 *
 * @author Eng Chong Meng
 */
public class InvitationReceivedDialog extends Dialog
{
    /**
     * The <tt>MultiUserChatManager</tt> is the one that deals with invitation events.
     */
    private ConferenceChatManager mMultiUserChatManager;

    /**
     * The operation set that would handle the rejection if the user choose to reject the
     * invitation.
     */
    private OperationSetMultiUserChat mMultiUserChatOpSet = null;

    /**
     * The operation set that would handle the rejection if the user choose to reject the
     * invitation, in case of an <tt>AdHocChatRoom</tt>.
     */
    private OperationSetAdHocMultiUserChat mMultiUserChatAdHocOpSet = null;

    /**
     * The <tt>ChatRoomInvitation</tt> for which this dialog is.
     */
    private ChatRoomInvitation mInvitation = null;

    /**
     * The <tt>AdHocChatRoomInvitation</tt> for which this dialog is, in case of an
     * <tt>AdHocChatRoom</tt>.
     */
    private AdHocChatRoomInvitation mInvitationAdHoc = null;

    private Context mContext;
    private EditText reasonTextArea;
    private EntityJid mInviter;
    private String mChatRoomName;
    private String mReason;

    /**
     * Constructs the <tt>ChatInviteDialog</tt>.
     *
     * // @param multiUserChatManager
     * the <tt>MultiUserChatManager</tt> is the one that deals with invitation events
     *
     * @param multiUserChatOpSet the operation set that would handle the rejection if the user choose to reject the
     * invitation
     * @param invitation the invitation that this dialog represents
     */
    public InvitationReceivedDialog(Context context, ConferenceChatManager multiUserChatManager,
            OperationSetMultiUserChat multiUserChatOpSet, ChatRoomInvitation invitation)
    {
        super(context);
        mContext = context;
        mMultiUserChatManager = multiUserChatManager;
        mMultiUserChatOpSet = multiUserChatOpSet;
        mInvitation = invitation;
        mInviter = invitation.getInviter();
        mChatRoomName = invitation.getTargetChatRoom().getName();
        mReason = mInvitation.getReason();
    }

    /**
     * Constructs the <tt>ChatInviteDialog</tt>, in case of an <tt>AdHocChatRoom</tt>.
     *
     * // @param multiUserChatManager
     * the <tt>MultiUserChatManager</tt> is the one that deals with invitation events
     *
     * @param multiUserChatAdHocOpSet the operation set that would handle the rejection if the user choose to reject the
     * invitation
     * @param invitationAdHoc the invitation that this dialog represents
     */
    public InvitationReceivedDialog(Context mContext, ConferenceChatManager multiUserChatManager,
            OperationSetAdHocMultiUserChat multiUserChatAdHocOpSet, AdHocChatRoomInvitation invitationAdHoc)
    {
        super(mContext);
        mMultiUserChatManager = multiUserChatManager;
        mMultiUserChatAdHocOpSet = multiUserChatAdHocOpSet;
        mInvitationAdHoc = invitationAdHoc;
        mInviter = invitationAdHoc.getInviter();
        mChatRoomName = invitationAdHoc.getTargetAdHocChatRoom().getName();
        mReason = invitationAdHoc.getReason();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void onCreate(Bundle savedInstanceState)
    {
        setContentView(R.layout.muc_invitation_received_dialog);
        setTitle(mContext.getString(R.string.service_gui_INVITATION_RECEIVED));

        TextView infoTextArea = this.findViewById(R.id.textMsgView);
        infoTextArea.setText(mContext.getString(R.string.service_gui_INVITATION_RECEIVED_MSG,
                mInviter, mChatRoomName));

        EditText textInvitation = this.findViewById(R.id.textInvitation);
        if (!TextUtils.isEmpty(mReason)) {
            textInvitation.setText(mReason);
        }
        else {
            textInvitation.setText("");
        }
        reasonTextArea = this.findViewById(R.id.rejectReasonTextArea);

        Button mAcceptButton = this.findViewById(R.id.button_Accept);
        mAcceptButton.setOnClickListener(v -> {
            dismiss();
            onAcceptClicked();
        });

        Button mRejectButton = this.findViewById(R.id.button_Reject);
        mRejectButton.setOnClickListener(v -> {
            dismiss();
            onRejectClicked();
        });

        Button mIgnoreButton = this.findViewById(R.id.button_Ignore);
        mIgnoreButton.setOnClickListener(v -> dismiss());
    }

    /**
     * Handles the <tt>ActionEvent</tt> triggered when one user clicks on one of the buttons.
     */
    private boolean onAcceptClicked()
    {
        if (mInvitationAdHoc == null) {
            MUCActivator.getMUCService().acceptInvitation(mInvitation);
        }
        else {
            try {
                mMultiUserChatManager.acceptInvitation(mInvitationAdHoc, mMultiUserChatAdHocOpSet);
            } catch (OperationFailedException e1) {
                e1.printStackTrace();
            }
        }
        return true;
    }

    private void onRejectClicked()
    {
        String reasonField = ViewUtil.toString(reasonTextArea);
        if (mMultiUserChatAdHocOpSet == null && mInvitationAdHoc == null) {
            try {
                MUCActivator.getMUCService().rejectInvitation(mMultiUserChatOpSet, mInvitation, reasonField);
            } catch (OperationFailedException e) {
                e.printStackTrace();
            }
        }
        if (mMultiUserChatAdHocOpSet != null)
            mMultiUserChatManager.rejectInvitation(mMultiUserChatAdHocOpSet, mInvitationAdHoc, reasonField);
    }

    /**
     * Shows given error message as an alert.
     *
     * @param errorMessage the error message to show.
     */
    private void showErrorMessage(String errorMessage)
    {
        Context ctx = aTalkApp.getGlobalContext();
        AndroidUtils.showAlertDialog(ctx, ctx.getString(R.string.service_gui_ERROR), errorMessage);
    }
}
