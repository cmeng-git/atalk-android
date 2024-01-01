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
package org.atalk.impl.appupdate;

import com.codetroopers.betterpickers.BuildConfig;

import net.java.sip.communicator.service.update.UpdateService;

/**
 * Dummy UpdateServiceImpl class for aTalk release version
 *
 * @author Eng Chong Meng
 */
public class UpdateServiceImpl implements UpdateService
{
    @Override
    public void checkForUpdates()
    {
    }

    @Override
    public String getLatestVersion()
    {
        return BuildConfig.VERSION_NAME;
    }

    @Override
    public boolean isLatestVersion()
    {
        return true;
    }
}
