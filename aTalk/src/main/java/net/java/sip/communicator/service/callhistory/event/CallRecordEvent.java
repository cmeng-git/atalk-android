/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package net.java.sip.communicator.service.callhistory.event;

import net.java.sip.communicator.service.callhistory.CallHistoryQuery;
import net.java.sip.communicator.service.callhistory.CallRecord;

import java.util.EventObject;

/**
 * The <code>CallRecordEvent</code> indicates that a <code>CallRecord</code> has been
 * received as a result of a <code>CallHistoryQuery</code>.
 *
 * @author Yana Stamcheva
 */
public class CallRecordEvent extends EventObject
{
    /**
     * Serial version UID.
     */
    private static final long serialVersionUID = 0L;

    /**
     * The <code>CallRecord</code> this event is about.
     */
    private final CallRecord callRecord;

    /**
     * Creates a <code>CallRecordEvent</code> by specifying the parent <code>query</code>
     * and the <code>callRecord</code> this event is about.
     *
     * @param query the source that triggered this event
     * @param callRecord the <code>CallRecord</code> this event is about
     */
    public CallRecordEvent(CallHistoryQuery query,
            CallRecord callRecord)
    {
        super(query);

        this.callRecord = callRecord;
    }

    /**
     * Returns the <code>ContactQuery</code> that triggered this event.
     *
     * @return the <code>ContactQuery</code> that triggered this event
     */
    public CallHistoryQuery getQuerySource()
    {
        return (CallHistoryQuery) source;
    }

    /**
     * Returns the <code>CallRecord</code>s this event is about.
     *
     * @return the <code>CallRecord</code>s this event is about
     */
    public CallRecord getCallRecord()
    {
        return callRecord;
    }
}
