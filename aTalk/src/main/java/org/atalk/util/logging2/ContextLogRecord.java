/*
 * Copyright @ 2018 - present 8x8, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.atalk.util.logging2;

import java.util.logging.Level;
import java.util.logging.LogRecord;

/**
 * {@link ContextLogRecord} extends {@link LogRecord} and adds
 * a 'context' String.  The reason it's done this way and the context
 * is not added to the log message itself is so that, in the log formatter,
 * we can place this context elsewhere (notably before the class and
 * method names) in the final log message.
 */
public class ContextLogRecord extends LogRecord
{
    protected final String context;
    public ContextLogRecord(Level level, String msg, String context)
    {
        super(level, msg);
        this.context = context;
    }

    public String getContext()
    {
        return context;
    }
}
