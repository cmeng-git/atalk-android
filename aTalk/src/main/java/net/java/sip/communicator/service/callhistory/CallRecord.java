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
package net.java.sip.communicator.service.callhistory;

import android.icu.text.MeasureFormat;
import android.icu.util.Measure;
import android.icu.util.MeasureUnit;
import android.os.Build;
import android.text.format.DateUtils;

import androidx.annotation.RequiresApi;

import net.java.sip.communicator.service.protocol.ProtocolProviderService;

import org.jetbrains.annotations.NotNull;

import java.text.DateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Vector;

/**
 * Structure used for encapsulating data when writing or reading Call History Data. Also these
 * records are used for returning data from the Call History Service.
 *
 * @author Damian Minkov
 * @author Yana Stamcheva
 * @author Eng Chong Meng
 */
public class CallRecord
{
    /**
     * The outgoing call direction.
     */
    public final static String OUT = "out";

    /**
     * The incoming call direction.
     */
    public final static String IN = "in";

    /**
     * A list of all peer records corresponding to this call record.
     */
    protected final List<CallPeerRecord> peerRecords = new Vector<>();

    /**
     * The id that uniquely identifies the call record.
     */
    protected String uuid;

    /**
     * Indicates the direction of the call - IN or OUT.
     */
    protected String direction;

    /**
     * The start call date.
     */
    protected Date startTime;

    /**
     * The end call date.
     */
    protected Date endTime;

    /**
     * The protocol provider (accountUid) through which the call was made.
     */
    protected ProtocolProviderService protocolProvider;

    /**
     * This is the end reason of the call if any. -1 default value for no reason specified.
     */
    protected int endReason = -1;

    /**
     * Creates Call Record
     *
     * @param uuid String
     * @param direction String
     * @param startTime Date
     * @param endTime Date
     */
    public CallRecord(String uuid, String direction, Date startTime, Date endTime)
    {
        if (uuid == null) {
            Date date = new Date();
            uuid = String.valueOf(date.getTime()) + Math.abs(date.hashCode());
        }

        this.uuid = uuid;
        this.direction = direction;
        this.startTime = startTime;
        this.endTime = endTime;
    }

    /**
     * Finds a CallPeer with the supplied address
     *
     * @param address EntityFullJid or callParticipantIDs
     * @return CallPeerRecord
     */
    public CallPeerRecord findPeerRecord(String address)
    {
        for (CallPeerRecord item : peerRecords) {
            if (item.getPeerAddress().equals(address))
                return item;
        }
        return null;
    }

    /**
     * Returns the direction of the call IN or OUT
     *
     * @return String
     */
    public String getCallUuid()
    {
        return uuid;
    }

    /**
     * Returns the direction of the call IN or OUT
     *
     * @return String
     */
    public String getDirection()
    {
        return direction;
    }

    /**
     * Returns the time when the call has finished
     *
     * @return Date
     */
    public Date getEndTime()
    {
        return endTime;
    }

    /**
     * Return Vector of CallPeerRecords
     *
     * @return Vector
     */
    public List<CallPeerRecord> getPeerRecords()
    {
        return peerRecords;
    }

    /**
     * The time when the call has began
     *
     * @return Date
     */
    public Date getStartTime()
    {
        return startTime;
    }

    /**
     * Returns the protocol provider used for the call. Could be null if the record has not saved the provider.
     *
     * @return the protocol provider used for the call
     */
    public ProtocolProviderService getProtocolProvider()
    {
        return protocolProvider;
    }

    /**
     * This is the end reason of the call if any. -1 the default value for no reason specified.
     *
     * @return end reason code if any.
     */
    public int getEndReason()
    {
        return endReason;
    }

    @NotNull
    public String toString()
    {
        String callStart;

        long start = startTime.getTime();
        if (DateUtils.isToday(start)) {
            DateFormat df = DateFormat.getTimeInstance(DateFormat.MEDIUM);
            callStart = df.format(startTime);
        }
        else {
            DateFormat df = DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT);
            callStart = df.format(startTime);
        }

        long callTime = (endTime.getTime() - start);
        CharSequence callDuration;
        callDuration = formatDuration(callTime);

        StringBuilder callInfo = new StringBuilder()
                .append(callStart)
                .append(" (")
                .append(callDuration)
                .append(")");

        return callInfo.toString();
    }

    public static CharSequence formatDuration(long millis)
    {
        final MeasureFormat.FormatWidth width;
        width = MeasureFormat.FormatWidth.SHORT;

        final MeasureFormat formatter = MeasureFormat.getInstance(Locale.getDefault(), width);
        if (millis >= DateUtils.HOUR_IN_MILLIS) {
            final int hours = (int) ((millis + 1800000) / DateUtils.HOUR_IN_MILLIS);
            return formatter.format(new Measure(hours, MeasureUnit.HOUR));
        }
        else if (millis >= DateUtils.MINUTE_IN_MILLIS) {
            final int minutes = (int) ((millis + 30000) / DateUtils.MINUTE_IN_MILLIS);
            return formatter.format(new Measure(minutes, MeasureUnit.MINUTE));
        }
        else {
            final int seconds = (int) ((millis + 500) / DateUtils.SECOND_IN_MILLIS);
            return formatter.format(new Measure(seconds, MeasureUnit.SECOND));
        }
    }
}
