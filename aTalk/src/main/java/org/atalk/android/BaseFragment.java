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
package org.atalk.android;

import android.content.Context;
import android.os.Looper;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;

/**
 * Class can be used to build {@link Fragment}s that require OSGI services access.
 *
 * @author Eng Chong Meng
 */
public class BaseFragment extends Fragment {
    protected Context mContext;
    protected FragmentActivity mFragmentActivity;

    /**
     * {@inheritDoc}
     */
    @Override
    public void onAttach(@NonNull Context context) {
        super.onAttach(context);
        mContext = context;
        mFragmentActivity = getActivity();
    }

    /**
     * Convenience method for running code on UI thread looper(instead of getActivity().runOnUIThread()).
     * It is never guaranteed that <code>getActivity()</code> will return not <code>null</code> value,
     * hence it must be checked in the <code>action</code>.
     *
     * @param action <code>Runnable</code> action to execute on UI thread.
     */
    public void runOnUiThread(Runnable action) {
        if (Looper.getMainLooper().isCurrentThread())
            action.run();
        else
            // Post action to the ui mainLooper
            BaseActivity.uiHandler.post(action);
    }
}
