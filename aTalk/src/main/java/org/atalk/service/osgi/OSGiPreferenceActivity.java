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
package org.atalk.service.osgi;

import androidx.appcompat.app.ActionBar;

import org.atalk.android.aTalkApp;
import org.atalk.android.gui.actionbar.ActionBarUtil;

/**
 * Copy of <code>OSGiActivity</code> that extends <code>PreferenceActivity</code>.
 *
 * @author Eng Chong Meng
 */
public class OSGiPreferenceActivity extends OSGiActivity {
    @Override
    protected void configureToolBar() {
        ActionBar actionBar = getSupportActionBar();
        if (actionBar != null) {
            // Disable up arrow on home activity
            Class<?> homeActivity = aTalkApp.getHomeScreenActivityClass();
            if (this.getClass().equals(homeActivity)) {
                actionBar.setDisplayHomeAsUpEnabled(false);
                actionBar.setHomeButtonEnabled(false);
            }
            ActionBarUtil.setTitle(this, getTitle());
        }
    }
}
