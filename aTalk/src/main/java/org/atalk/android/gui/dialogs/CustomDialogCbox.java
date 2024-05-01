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
package org.atalk.android.gui.dialogs;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;

import org.atalk.android.R;
import org.atalk.android.gui.util.ViewUtil;
import org.atalk.service.osgi.OSGiFragment;

public class CustomDialogCbox extends OSGiFragment {
    public static final String ARG_MESSAGE = "dialog_message";
    public static final String ARG_CB_MESSAGE = "cb_message";
    public static final String ARG_CB_CHECK = "cb_check";
    public static final String ARG_CB_ENABLE = "cb_enable";

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View vcBlocking = inflater.inflate(R.layout.custom_dialog_cb, container, false);
        Bundle arg = getArguments();
        if (arg != null) {
            ViewUtil.setTextViewValue(vcBlocking, R.id.messageText, arg.getString(ARG_MESSAGE));
            ViewUtil.setTextViewValue(vcBlocking, R.id.cb_option, arg.getString(ARG_CB_MESSAGE));
            ViewUtil.setCompoundChecked(vcBlocking, R.id.cb_option, arg.getBoolean(ARG_CB_CHECK));
            ViewUtil.ensureEnabled(vcBlocking, R.id.cb_option, arg.getBoolean(ARG_CB_ENABLE));
        }
        return vcBlocking;
    }
}
