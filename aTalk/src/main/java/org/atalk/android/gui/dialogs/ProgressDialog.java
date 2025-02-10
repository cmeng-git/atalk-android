/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.atalk.android.gui.dialogs;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;

import org.atalk.android.BaseFragment;
import org.atalk.android.R;
import org.atalk.android.aTalkApp;
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

    public ProgressDialog() {
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View progressView = inflater.inflate(R.layout.progress_dialog, container, false);
        ViewUtil.setTextViewValue(progressView, R.id.messageText, getArguments().getString(ARG_MESSAGE));
        return progressView;
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
    public static long showProgressDialog(String title, String message) {
        Map<String, Serializable> extras = new HashMap<>();
        extras.put(DialogActivity.EXTRA_CANCELABLE, false);
        extras.put(DialogActivity.EXTRA_REMOVE_BUTTONS, true);

        Bundle args = new Bundle();
        args.putString(ARG_MESSAGE, message);

        return DialogActivity.showCustomDialog(aTalkApp.getInstance(), title,
                ProgressDialog.class.getName(), args, null, null, extras);
    }
}
