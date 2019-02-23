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
package org.atalk.persistance;

import android.content.ContentValues;

import org.jivesoftware.smackx.caps.EntityCapsManager;

/**
 * Keeps track of entity capabilities.
 * <p/>
 * This work is based on Jonas Adahl's smack fork.
 *
 * @author Emil Ivov
 * @author Lyubomir Marinov
 * @author Eng Chong Meng
 */
public class EntityCapsDatabase
{
    public static final String TABLE_NAME = "discoveryCaps";
    public static final String HASH = "hash";
    public static final String VER = "ver";
    public static final String RESULT = "result";

    public ContentValues getContentValues()
    {
        String currentCapsVersion = EntityCapsManager.getNodeVersionByJid(null);
        final ContentValues values = new ContentValues();

        values.put(HASH, HASH);
        values.put(VER, currentCapsVersion);
        values.put(RESULT, this.toString());
        return values;
    }
}
