/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.atalk.android.gui.dialogs;

import android.content.Context;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;

import org.atalk.android.BaseFragment;
import org.atalk.android.R;
import org.atalk.android.gui.util.ViewUtil;

/**
 * Fragment can be used to display indeterminate progress dialogs.
 *
 * @author Pawel Domas
 * @author Eng Chong Meng
 */
public class ProgressDialog extends BaseFragment {
    /**
     * Argument used to retrieve the message that will be displayed next to the progress bar.
     */
    private static final String ARG_MESSAGE = "progress_dialog_message";
    /**
     * Static map holds listeners for currently displayed dialogs.
     */
    private static final Map<Long, View> viewMap = new HashMap<>();
    private static long dialogId;

    public ProgressDialog() {
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View dialogView = inflater.inflate(R.layout.progress_dialog, container, false);
        Bundle args = getArguments();
        if (args != null) {
            ViewUtil.setTextViewValue(dialogView, R.id.messageText, args.getString(ARG_MESSAGE));
        }
        viewMap.put(dialogId, dialogView);
        return dialogView;
    }

    /**
     * Displays indeterminate progress dialog.
     *
     * @param title dialog's title
     * @param message the message to be displayed next to the progress bar.
     *
     * @return dialog id that can be used to close the dialog
     * {@link DialogActivity#closeDialog(long)}.
     */
    public static long show(Context context, String title, String message, boolean cancelable) {
        Map<String, Serializable> extras = new HashMap<>();
        extras.put(DialogActivity.EXTRA_CANCELABLE, cancelable);
        extras.put(DialogActivity.EXTRA_HIDE_BUTTONS, true);

        Bundle args = new Bundle();
        args.putString(ARG_MESSAGE, message);

        dialogId = DialogActivity.showCustomDialog(context, title,
                ProgressDialog.class.getName(), args, null, null, extras);
        return dialogId;
    }

    public static void setMessage(long dialogId, String message) {
        View dialogView = viewMap.get(dialogId);
        if (dialogView != null) {
            ViewUtil.setTextViewValue(dialogView, R.id.messageText, message);
        }
    }

    /**
     * @return Whether the dialog is currently showing.
     */
    public static boolean isShowing(long dialogId) {
        View dialogView = viewMap.get(dialogId);
        return (dialogView != null) && (dialogView.getVisibility() == View.VISIBLE);
    }

    public static void dismiss(long dialogId) {
        if (viewMap.containsKey(dialogId)) {
            DialogActivity.closeDialog(dialogId);
            viewMap.remove(dialogId);
        }
    }
}
