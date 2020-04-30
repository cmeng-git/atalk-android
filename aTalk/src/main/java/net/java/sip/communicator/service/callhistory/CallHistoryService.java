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

import net.java.sip.communicator.service.callhistory.event.CallHistoryPeerRecordListener;
import net.java.sip.communicator.service.callhistory.event.CallHistorySearchProgressListener;
import net.java.sip.communicator.service.contactlist.MetaContact;

import java.util.*;

/**
 * The Call History Service stores info about calls made from various protocols
 *
 * @author Alexander Pelov
 * @author Damian Minkov
 * @author Hristo Terezov
 * @author Eng Chong Meng
 */
public interface CallHistoryService
{
    /* DB database column fields for call history */
    String TABLE_NAME = "callHistory";
    String UUID = "uuid";
    String TIME_STAMP = "timeStamp";        // TimeStamp
    String ACCOUNT_UID = "accountUid";    // account uid
    String CALL_START = "callStart";        // callStart TimeStamp
    String CALL_END = "callEnd";            // callEnd TimeStamp
    String DIRECTION = "direction";         // dir

    String ENTITY_JID = "entityJid";        // callParticipantName
    String ENTITY_CALL_START = "entityCallStart";   // callParticipantStart
    String ENTITY_CALL_END = "entityCallEnd";       // callParticipantEnd
    String ENTITY_CALL_STATE = "entityCallState";   // callParticipantStates
    String CALL_END_REASON = "callEndReason";
    String ENTITY_FULL_JID = "entityFullJid";       // callParticipantIDs
    String SEC_ENTITY_ID = "secEntityID";   //secondaryCallParticipantIDs

    /**
     * Returns all the calls made by all the contacts in the supplied <tt>contact</tt> on and after
     * the given date.
     *
     * @param contact MetaContact which contacts participate in the returned calls
     * @param startDate Date the start date of the calls
     * @return Collection of CallRecords with CallPeerRecord
     */
    Collection<CallRecord> findByStartDate(MetaContact contact, Date startDate);

    /**
     * Returns all the calls made by all the contacts in the supplied <tt>contact</tt> before the given date.
     *
     * @param contact MetaContact which contacts participate in the returned calls
     * @param endDate Date the end date of the calls
     * @return Collection of CallRecords with CallPeerRecord
     */
    Collection<CallRecord> findByEndDate(MetaContact contact, Date endDate);

    /**
     * Returns all the calls made by all the contacts in the supplied <tt>contact</tt> between
     * the given dates inclusive of startDate.
     *
     * @param contact MetaContact which contacts participate in the returned calls
     * @param startDate Date the start date of the calls
     * @param endDate Date the end date of the calls
     * @return Collection of CallRecords with CallPeerRecord
     */
    Collection<CallRecord> findByPeriod(MetaContact contact, Date startDate, Date endDate);

    /**
     * Returns all the calls made after the given date.
     *
     * @param startDate Date the start date of the calls
     * @return Collection of CallRecords with CallPeerRecord
     */
    Collection<CallRecord> findByStartDate(Date startDate);

    /**
     * Returns all the calls made before the given date.
     *
     * @param endDate Date the end date of the calls
     * @return Collection of CallRecords with CallPeerRecord
     */
    Collection<CallRecord> findByEndDate(Date endDate);

    Collection<CallRecord> findByEndDate(String accountUuid, Date endDate);

    /**
     * Returns all the calls made between the given dates.
     *
     * @param startDate Date the start date of the calls
     * @param endDate Date the end date of the calls
     * @return Collection of CallRecords with CallPeerRecord
     */
    Collection<CallRecord> findByPeriod(Date startDate, Date endDate);

    /**
     * Returns the supplied number of recent calls made by all the contacts in the supplied <tt>contact</tt>.
     *
     * @param contact MetaContact which contacts participate in the returned calls
     * @param count calls count
     * @return Collection of CallRecords with CallPeerRecord
     */
    Collection<CallRecord> findLast(MetaContact contact, int count);

    /**
     * Returns the supplied number of recent calls.
     *
     * @param count calls count
     * @return Collection of CallRecords with CallPeerRecord
     */
    Collection<CallRecord> findLast(int count);

    /**
     * Find the calls made by the supplied peer address
     *
     * @param address String the address of the peer
     * @param recordCount the number of records to return
     * @return Collection of CallRecords with CallPeerRecord
     */
    CallHistoryQuery findByPeer(String address, int recordCount);

    /**
     * Adding progress listener for monitoring progress of search process
     *
     * @param listener HistorySearchProgressListener
     */
    void addSearchProgressListener(CallHistorySearchProgressListener listener);

    /**
     * Removing progress listener
     *
     * @param listener HistorySearchProgressListener
     */
    void removeSearchProgressListener(CallHistorySearchProgressListener listener);

    /**
     * Updates the secondary address field of call record.
     *
     * @param date the start date of the record which will be updated.
     * @param peerAddress the peer of the record which will be updated.
     * @param address the value of the secondary address .
     */
    void updateCallRecordPeerSecondaryAddress(final Date date, final String peerAddress,
            final String address);

    /**
     * Adding <tt>CallHistoryRecordListener</tt> listener to the list.
     *
     * @param listener CallHistoryRecordListener
     */
    void addCallHistoryRecordListener(CallHistoryPeerRecordListener listener);

    /**
     * Removing <tt>CallHistoryRecordListener</tt> listener
     *
     * @param listener CallHistoryRecordListener
     */
    void removeCallHistoryRecordListener(CallHistoryPeerRecordListener listener);

    /**
     * Permanently removes all locally stored call history.
     */
    void eraseLocallyStoredHistory(List<String> callUUIDs);
}
