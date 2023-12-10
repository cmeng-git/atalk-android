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
package org.atalk.android.gui.account;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;

import org.atalk.android.R;
import org.atalk.android.gui.util.ViewUtil;
import org.atalk.service.osgi.OSGiFragment;

/**
 * Fragment for history message delete with media delete option
 *
 * @author Eng Chong Meng
 */
public class AccountDeleteFragment extends OSGiFragment
{
    public static final String ARG_MESSAGE = "dialog_message";

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState)
    {
        View viewAccountDelete = inflater.inflate(R.layout.account_delete, container, false);
        ViewUtil.setTextViewValue(viewAccountDelete, R.id.textView, getArguments().getString(ARG_MESSAGE));
        return viewAccountDelete;
    }
}
