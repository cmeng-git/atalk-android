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
package net.java.sip.communicator.impl.callhistory;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

import net.java.sip.communicator.impl.history.HistoryQueryImpl;
import net.java.sip.communicator.service.callhistory.*;
import net.java.sip.communicator.service.callhistory.event.*;
import net.java.sip.communicator.service.contactlist.MetaContact;
import net.java.sip.communicator.service.history.HistoryService;
import net.java.sip.communicator.service.history.event.HistorySearchProgressListener;
import net.java.sip.communicator.service.history.event.ProgressEvent;
import net.java.sip.communicator.service.history.records.HistoryRecordStructure;
import net.java.sip.communicator.service.protocol.*;
import net.java.sip.communicator.service.protocol.event.*;

import org.atalk.android.gui.chat.ChatMessage;
import org.atalk.android.plugin.timberlog.TimberLog;
import org.atalk.persistance.DatabaseBackend;
import org.osgi.framework.*;

import java.io.*;
import java.util.*;

import timber.log.Timber;

/**
 * The Call History Service stores info about the calls made. Logs calls info for all protocol
 * providers that support basic telephony (i.e. those that implement OperationSetBasicTelephony).
 *
 * @author Damian Minkov
 * @author Lubomir Marinov
 * @author Eng Chong Meng
 */
public class CallHistoryServiceImpl implements CallHistoryService, CallListener, ServiceListener
{
    /**
     * Sort database message records by TimeStamp in ASC or DESC
     */
    private static final String ORDER_ASC = ChatMessage.TIME_STAMP + " ASC";
    private static final String ORDER_DESC = ChatMessage.TIME_STAMP + " DESC";

    private static String[] STRUCTURE_NAMES = new String[]{
            "accountUID", "callStart", "callEnd",
            "dir", "callParticipantIDs",
            "callParticipantStart", "callParticipantEnd",
            "callParticipantStates", "callEndReason",
            "callParticipantNames", "secondaryCallParticipantIDs"};

    private static HistoryRecordStructure recordStructure = new HistoryRecordStructure(STRUCTURE_NAMES);

    private static final char DELIMITER = ',';

    /**
     * The BundleContext that we got from the OSGI bus.
     */
    private BundleContext bundleContext = null;

    private HistoryService historyService = null;

    final private Object syncRoot_HistoryService = new Object();

    private final Map<CallHistorySearchProgressListener, SearchProgressWrapper> progressListeners = new Hashtable<>();

    private final List<CallRecordImpl> currentCallRecords = new Vector<>();

    private final CallChangeListener historyCallChangeListener = new HistoryCallChangeListener();

    final private List<CallHistoryPeerRecordListener> callHistoryRecordListeners = new LinkedList<>();

    private SQLiteDatabase mDB;
    private ContentValues contentValues = new ContentValues();

    /**
     * starts the service. Check the current registered protocol providers which supports
     * BasicTelephony and adds calls listener to them
     *
     * @param bc BundleContext
     */
    public void start(BundleContext bc)
    {
        Timber.d("Starting the call history implementation.");

        this.bundleContext = bc;
        mDB = DatabaseBackend.getWritableDB();

        // start listening for newly register or removed protocol providers
        bc.addServiceListener(this);

        ServiceReference[] ppsRefs = null;
        try {
            ppsRefs = bundleContext.getServiceReferences(ProtocolProviderService.class.getName(),
                    null);
        } catch (InvalidSyntaxException e) {
            e.printStackTrace();
        }

        if (ppsRefs != null && ppsRefs.length != 0) {
            for (ServiceReference<ProtocolProviderService> ppsRef : ppsRefs) {
                ProtocolProviderService pps = bundleContext.getService(ppsRef);
                handleProviderAdded(pps);
            }
        }
    }

    /**
     * stops the service.
     *
     * @param bc BundleContext
     */
    public void stop(BundleContext bc)
    {
        bc.removeServiceListener(this);
        ServiceReference[] ppsRefs = null;
        try {
            ppsRefs = bundleContext.getServiceReferences(ProtocolProviderService.class.getName(),
                    null);
        } catch (InvalidSyntaxException e) {
            e.printStackTrace();
        }

        if (ppsRefs != null && ppsRefs.length != 0) {
            for (ServiceReference<ProtocolProviderService> ppsRef : ppsRefs) {
                ProtocolProviderService pps = bundleContext.getService(ppsRef);
                handleProviderRemoved(pps);
            }
        }
    }

    /**
     * When new protocol provider is registered we check does it supports BasicTelephony and
     * if so add a listener to it
     *
     * @param serviceEvent ServiceEvent
     */
    public void serviceChanged(ServiceEvent serviceEvent)
    {
        Object sService = bundleContext.getService(serviceEvent.getServiceReference());
        Timber.log(TimberLog.FINER, "Received a service event for: " + sService.getClass().getName());

        // we don't care if the source service is not a protocol provider
        if (!(sService instanceof ProtocolProviderService)) {
            return;
        }

        Timber.d("Service is a protocol provider.");
        if (serviceEvent.getType() == ServiceEvent.REGISTERED) {
            Timber.d("Handling registration of a new Protocol Provider.");
            this.handleProviderAdded((ProtocolProviderService) sService);
        }
        else if (serviceEvent.getType() == ServiceEvent.UNREGISTERING) {
            this.handleProviderRemoved((ProtocolProviderService) sService);
        }
    }

    /**
     * Used to attach the Call History Service to existing or just registered protocol provider.
     * Checks if the provider has implementation of OperationSetBasicTelephony
     *
     * @param provider ProtocolProviderService
     */
    private void handleProviderAdded(ProtocolProviderService provider)
    {
        Timber.d("Adding protocol provider %s", provider.getProtocolName());
        // check whether the provider has a basic telephony operation set
        OperationSetBasicTelephony<?> opSetTelephony = provider.getOperationSet(OperationSetBasicTelephony.class);
        if (opSetTelephony != null) {
            opSetTelephony.addCallListener(this);
        }
        else {
            Timber.log(TimberLog.FINER, "Service did not have a basic telephony op. set.");
        }
    }

    /**
     * Removes the specified provider from the list of currently known providers and ignores all
     * the calls made by it
     *
     * @param provider the ProtocolProviderService that has been unregistered.
     */
    private void handleProviderRemoved(ProtocolProviderService provider)
    {
        OperationSetBasicTelephony<?> opSetTelephony = provider.getOperationSet(OperationSetBasicTelephony.class);
        if (opSetTelephony != null) {
            opSetTelephony.removeCallListener(this);
        }
    }

    /**
     * Set the configuration service.
     *
     * @param historyService HistoryService
     */
    public void setHistoryService(HistoryService historyService)
    {
        synchronized (this.syncRoot_HistoryService) {
            this.historyService = historyService;
            Timber.d("New history service registered.");
        }
    }

    /**
     * Remove a configuration service.
     *
     * @param hService HistoryService
     */
    public void unsetHistoryService(HistoryService hService)
    {
        synchronized (this.syncRoot_HistoryService) {
            if (this.historyService == hService) {
                this.historyService = null;
                Timber.d("History service unregistered.");
            }
        }
    }

    /**
     * Returns all the calls made by all the contacts in the supplied <tt>metaContact</tt>
     * on and after the given date.
     *
     * @param metaContact MetaContact which contacts participate in the returned calls
     * @param startDate Date the start date of the calls
     * @return the <tt>CallHistoryQuery</tt>, corresponding to this find
     */
    public Collection<CallRecord> findByStartDate(MetaContact metaContact, Date startDate)
            throws RuntimeException
    {
        TreeSet<CallRecord> result = new TreeSet<>(new CallRecordComparator());

        Iterator<Contact> contacts = metaContact.getContacts();
        while (contacts.hasNext()) {
            Contact contact = contacts.next();
            String startTimeStamp = String.valueOf(startDate.getTime());
            String[] args = {contact.toString(), startTimeStamp};

            Cursor cursor = mDB.query(CallHistoryService.TABLE_NAME, null,
                    CallHistoryService.ENTITY_JID + "=? AND "
                            + CallHistoryService.TIME_STAMP + ">=?", args, null, null, ORDER_ASC);

            while (cursor.moveToNext()) {
                result.add(convertHistoryRecordToCallRecord(cursor));
            }
        }
        return result;
    }

    /**
     * Returns all the calls made after the given date
     *
     * @param startDate Date the start date of the calls
     * @return the <tt>CallHistoryQuery</tt>, corresponding to this find
     */
    public Collection<CallRecord> findByStartDate(Date startDate)
    {
        TreeSet<CallRecord> result = new TreeSet<>(new CallRecordComparator());

        String startTimeStamp = String.valueOf(startDate.getTime());
        String[] args = {startTimeStamp};

        Cursor cursor = mDB.query(CallHistoryService.TABLE_NAME, null,
                CallHistoryService.TIME_STAMP + ">=?", args, null, null, ORDER_ASC);

        while (cursor.moveToNext()) {
            result.add(convertHistoryRecordToCallRecord(cursor));
        }
        return result;
    }

    /**
     * Returns all the calls made by all the contacts in the supplied metaContact before
     * the given date
     *
     * @param metaContact MetaContact which contacts participate in the returned calls
     * @param endDate Date the end date of the calls
     * @return Collection of CallRecords with CallPeerRecord
     */
    public Collection<CallRecord> findByEndDate(MetaContact metaContact, Date endDate)
    {
        TreeSet<CallRecord> result = new TreeSet<>(new CallRecordComparator());

        Iterator<Contact> contacts = metaContact.getContacts();
        while (contacts.hasNext()) {
            Contact contact = contacts.next();
            String endTimeStamp = String.valueOf(endDate.getTime());
            String[] args = {contact.toString(), endTimeStamp};

            Cursor cursor = mDB.query(CallHistoryService.TABLE_NAME, null,
                    CallHistoryService.ENTITY_JID + "=? AND "
                            + CallHistoryService.TIME_STAMP + "<?", args, null, null, ORDER_ASC);

            while (cursor.moveToNext()) {
                result.add(convertHistoryRecordToCallRecord(cursor));
            }
        }
        return result;
    }

    /**
     * Returns all the calls made before the given date
     *
     * @param endDate Date the end date of the calls
     * @return Collection of CallRecords with CallPeerRecord
     */
    public Collection<CallRecord> findByEndDate(Date endDate)
    {
        TreeSet<CallRecord> result = new TreeSet<>(new CallRecordComparator());

        String endTimeStamp = String.valueOf(endDate.getTime());
        String[] args = {endTimeStamp};

        Cursor cursor = mDB.query(CallHistoryService.TABLE_NAME, null,
                CallHistoryService.TIME_STAMP + "<?", args, null, null, ORDER_ASC);

        while (cursor.moveToNext()) {
            result.add(convertHistoryRecordToCallRecord(cursor));
        }
        return result;
    }

    /**
     * Returns all the calls made before the given date
     *
     * @param endDate Date the end date of the calls
     * @return Collection of CallRecords with CallPeerRecord
     */
    public Collection<CallRecord> findByEndDate(String accountUuid, Date endDate)
    {
        TreeSet<CallRecord> result = new TreeSet<>(new CallRecordComparator());

        String endTimeStamp = String.valueOf(endDate.getTime());
        String[] args = {accountUuid, endTimeStamp};

        Cursor cursor = mDB.query(CallHistoryService.TABLE_NAME, null,
                CallHistoryService.ACCOUNT_UID + "=? AND "
                        + CallHistoryService.TIME_STAMP + "<?", args, null, null, ORDER_DESC);

        while (cursor.moveToNext()) {
            result.add(convertHistoryRecordToCallRecord(cursor));
        }
        return result;
    }

    /**
     * Returns all the calls made by all the contacts in the supplied metaContact between the
     * given dates
     *
     * @param metaContact MetaContact
     * @param startDate Date the start date of the calls
     * @param endDate Date the end date of the conversations
     * @return Collection of CallRecords with CallPeerRecord
     */
    public Collection<CallRecord> findByPeriod(MetaContact metaContact, Date startDate,
            Date endDate)
    {
        TreeSet<CallRecord> result = new TreeSet<>(new CallRecordComparator());

        Iterator<Contact> contacts = metaContact.getContacts();
        while (contacts.hasNext()) {
            Contact contact = contacts.next();
            String startTimeStamp = String.valueOf(startDate.getTime());
            String endTimeStamp = String.valueOf(endDate.getTime());
            String[] args = {contact.toString(), startTimeStamp, endTimeStamp};

            Cursor cursor = mDB.query(CallHistoryService.TABLE_NAME, null,
                    CallHistoryService.ENTITY_JID + "=? AND "
                            + CallHistoryService.TIME_STAMP + ">=? AND "
                            + CallHistoryService.TIME_STAMP + "<?",
                    args, null, null, ORDER_ASC);

            while (cursor.moveToNext()) {
                result.add(convertHistoryRecordToCallRecord(cursor));
            }
        }
        return result;
    }

    /**
     * Returns all the calls made between the given dates
     *
     * @param startDate Date the start date of the calls
     * @param endDate Date the end date of the conversations
     * @return Collection of CallRecords with CallPeerRecord
     */
    public Collection<CallRecord> findByPeriod(Date startDate, Date endDate)
    {
        TreeSet<CallRecord> result = new TreeSet<>(new CallRecordComparator());

        String startTimeStamp = String.valueOf(startDate.getTime());
        String endTimeStamp = String.valueOf(endDate.getTime());
        String[] args = {startTimeStamp, endTimeStamp};

        Cursor cursor = mDB.query(CallHistoryService.TABLE_NAME, null,
                CallHistoryService.TIME_STAMP + ">=? AND "
                        + CallHistoryService.TIME_STAMP + "<?", args, null, null, ORDER_ASC);

        while (cursor.moveToNext()) {
            result.add(convertHistoryRecordToCallRecord(cursor));
        }
        return result;
    }

    /**
     * Returns the supplied number of calls by all the contacts in the supplied metaContact
     *
     * @param metaContact MetaContact which contacts participate in the returned calls
     * @param count calls count
     * @return Collection of CallRecords with CallPeerRecord
     */
    public Collection<CallRecord> findLast(MetaContact metaContact, int count)
    {
        TreeSet<CallRecord> result = new TreeSet<>(new CallRecordComparator());

        Iterator<Contact> contacts = metaContact.getContacts();
        while (contacts.hasNext()) {
            Contact contact = contacts.next();
            String[] args = {contact.toString()};

            Cursor cursor = mDB.query(CallHistoryService.TABLE_NAME, null,
                    CallHistoryService.ENTITY_JID + "=?", args, null, null, ORDER_DESC,
                    String.valueOf(count));

            while (cursor.moveToNext()) {
                result.add(convertHistoryRecordToCallRecord(cursor));
            }
        }
        return result;
    }

    /**
     * Returns the supplied number of calls made
     *
     * @param count calls count
     * @return Collection of CallRecords with CallPeerRecord
     */
    public Collection<CallRecord> findLast(int count)
    {
        TreeSet<CallRecord> result = new TreeSet<>(new CallRecordComparator());

        Cursor cursor = mDB.query(CallHistoryService.TABLE_NAME, null,
                null, null, null, null, ORDER_DESC, String.valueOf(count));

        while (cursor.moveToNext()) {
            result.add(convertHistoryRecordToCallRecord(cursor));
        }
        return result;
    }

    /**
     * Find the calls made by the supplied peer address
     *
     * @param address String the address of the peer (cmeng: may be null?)
     * @param recordCount the number of records to return
     * @return Collection of CallRecords with CallPeerRecord
     */
    public CallHistoryQuery findByPeer(String address, int recordCount)
    {
        HistoryQueryImpl hq = new HistoryQueryImpl("callParticipantIDs");
        CallHistoryQueryImpl callQuery = new CallHistoryQueryImpl(hq);
        CallRecord callRecord;

        String[] args = {address};
        Cursor cursor = mDB.query(CallHistoryService.TABLE_NAME, null,
                CallHistoryService.ENTITY_FULL_JID + " LIKE '%?%'", args,
                null, null, ORDER_DESC, String.valueOf(recordCount));

        while (cursor.moveToNext()) {
            callRecord = convertHistoryRecordToCallRecord(cursor);
            callQuery.addHistoryRecord(callRecord);
        }
        return callQuery;

        //		try {
        //			// the default ones
        //			History history = this.getHistory(null, null);
        //			InteractiveHistoryReader historyReader = history.getInteractiveReader();
        //			HistoryQuery historyQuery = historyReader.findByKeyword(address, "callParticipantIDs", recordCount);
        //
        //			callQuery = new CallHistoryQueryImpl(historyQuery);
        //		}
        //		catch (IOException ex) {
        //			Timber.e("Could not read history", ex);
        //		}
        //		return callQuery;
    }

    //	/**
    //	 * Returns the history by specified local and remote contact if one of them is null the default is used
    //	 *
    //	 * @param localContact Contact
    //	 * @param remoteContact Contact
    //	 * @return History
    //	 */
    //	private History getHistory(Contact localContact, Contact remoteContact)
    //			throws IOException
    //	{
    //		String localId = localContact == null ? "default" : localContact.getAddress();
    //		String remoteId = remoteContact == null ? "default" : remoteContact.getAddress();
    //
    //		HistoryID historyId = HistoryID.createFromRawID(new String[]{"callhistory", localId, remoteId});
    //		return this.historyService.createHistory(historyId, recordStructure);
    //	}

    /**
     * Used to convert HistoryRecord in CallRecord and CallPeerRecord which are returned  in cursor
     * by the finder methods
     *
     * @param cursor HistoryRecord in cursor
     * @return Object CallRecord
     */
    private CallRecord convertHistoryRecordToCallRecord(Cursor cursor)
    {
        Map<String, String> mProperties = new Hashtable<>();
        for (int i = 0; i < cursor.getColumnCount(); i++) {
            String value = (cursor.getString(i) == null) ? "" : cursor.getString(i);
            mProperties.put(cursor.getColumnName(i), value);
        }
        return createCallRecordFromProperties(mProperties);
    }

    /**
     * Create from the retrieved database mProperties to chatMessages
     *
     * @param mProperties CallRecord properties converted from cursor
     * @return CallRecordImpl
     */
    public static CallRecord createCallRecordFromProperties(Map<String, String> mProperties)
    {
        List<String> callPeerIDs;
        List<String> callPeerNames;
        List<String> callPeerStart;
        List<String> callPeerEnd;
        List<CallPeerState> callPeerStates;
        List<String> callPeerSecondaryIDs;

        CallRecordImpl result = new CallRecordImpl(mProperties.get(CallHistoryService.UUID),
                mProperties.get(CallHistoryService.DIRECTION),
                new Date(Long.parseLong(mProperties.get(CallHistoryService.CALL_START))),
                new Date(Long.parseLong(mProperties.get(CallHistoryService.CALL_END))));

        result.setProtocolProvider(getProtocolProvider(mProperties.get(CallHistoryService.ACCOUNT_UID)));
        result.setEndReason(Integer.parseInt(mProperties.get(CallHistoryService.CALL_END_REASON)));

        callPeerIDs = getCSVs(mProperties.get(CallHistoryService.ENTITY_FULL_JID));
        callPeerStart = getCSVs(mProperties.get(CallHistoryService.ENTITY_CALL_START));
        callPeerEnd = getCSVs(mProperties.get(CallHistoryService.ENTITY_CALL_END));
        callPeerStates = getStates(mProperties.get(CallHistoryService.ENTITY_CALL_STATE));
        callPeerNames = getCSVs(mProperties.get(CallHistoryService.ENTITY_JID));
        callPeerSecondaryIDs = getCSVs(mProperties.get(CallHistoryService.SEC_ENTITY_ID));

        final int callPeerCount = callPeerIDs == null ? 0 : callPeerIDs.size();
        for (int i = 0; i < callPeerCount; i++) {
            // As we iterate over the CallPeer IDs we could not be sure that for some reason the
            // start or end call list could result in different size lists, so we check this first.
            Date callPeerStartValue;
            Date callPeerEndValue;

            if (i < callPeerStart.size()) {
                callPeerStartValue = new Date(Long.parseLong(callPeerStart.get(i)));
            }
            else {
                callPeerStartValue = result.getStartTime();
                Timber.i("Call history start time list different from ids list.");
            }

            if (i < callPeerEnd.size()) {
                callPeerEndValue = new Date(Long.parseLong(callPeerEnd.get(i)));
            }
            else {
                callPeerEndValue = result.getEndTime();
                Timber.i("Call history end time list different from ids list.");
            }

            CallPeerRecordImpl cpr = new CallPeerRecordImpl(callPeerIDs.get(i),
                    callPeerStartValue, callPeerEndValue);

            String callPeerSecondaryID = null;
            if (callPeerSecondaryIDs != null && !callPeerSecondaryIDs.isEmpty())
                callPeerSecondaryID = callPeerSecondaryIDs.get(i);

            if (callPeerSecondaryID != null && !callPeerSecondaryID.equals("")) {
                cpr.setPeerSecondaryAddress(callPeerSecondaryID);
            }

            // if there is no record about the states (backward compatibility)
            if (callPeerStates != null && i < callPeerStates.size())
                cpr.setState(callPeerStates.get(i));
            else
                Timber.i("Call history state list different from ids list.");

            if (callPeerNames != null && i < callPeerNames.size())
                cpr.setDisplayName(callPeerNames.get(i));

            result.getPeerRecords().add(cpr);
        }
        return result;
    }

    /**
     * Returns list of String items contained in the supplied string separated by DELIMITER
     *
     * @param str String
     * @return LinkedList
     */
    private static List<String> getCSVs(String str)
    {
        List<String> result = new LinkedList<>();
        if (str == null)
            return result;

        StreamTokenizer stt = new StreamTokenizer(new StringReader(str));
        stt.resetSyntax();
        stt.wordChars('\u0000', '\uFFFF');
        stt.eolIsSignificant(false);
        stt.quoteChar('"');
        stt.whitespaceChars(DELIMITER, DELIMITER);
        try {
            while (stt.nextToken() != StreamTokenizer.TT_EOF) {
                if (stt.sval != null) {
                    result.add(stt.sval.trim());
                }
            }
        } catch (IOException e) {
            Timber.e("failed to parse %s: %s", str, e.getMessage());
        }
        return result;
    }

    /**
     * Get the delimited strings and converts them to CallPeerState
     *
     * @param str String delimited string states
     * @return LinkedList the converted values list
     */
    private static List<CallPeerState> getStates(String str)
    {
        List<CallPeerState> result = new LinkedList<>();
        Collection<String> stateStrs = getCSVs(str);

        for (String item : stateStrs) {
            result.add(convertStateStringToState(item));
        }
        return result;
    }

    /**
     * Converts the state string to state
     *
     * @param state String the string
     * @return CallPeerState the state
     */
    private static CallPeerState convertStateStringToState(String state)
    {
        switch (state) {
            case CallPeerState._CONNECTED:
                return CallPeerState.CONNECTED;
            case CallPeerState._BUSY:
                return CallPeerState.BUSY;
            case CallPeerState._FAILED:
                return CallPeerState.FAILED;
            case CallPeerState._DISCONNECTED:
                return CallPeerState.DISCONNECTED;
            case CallPeerState._ALERTING_REMOTE_SIDE:
                return CallPeerState.ALERTING_REMOTE_SIDE;
            case CallPeerState._CONNECTING:
                return CallPeerState.CONNECTING;
            case CallPeerState._ON_HOLD_LOCALLY:
                return CallPeerState.ON_HOLD_LOCALLY;
            case CallPeerState._ON_HOLD_MUTUALLY:
                return CallPeerState.ON_HOLD_MUTUALLY;
            case CallPeerState._ON_HOLD_REMOTELY:
                return CallPeerState.ON_HOLD_REMOTELY;
            case CallPeerState._INITIATING_CALL:
                return CallPeerState.INITIATING_CALL;
            case CallPeerState._INCOMING_CALL:
                return CallPeerState.INCOMING_CALL;
            default:
                return CallPeerState.UNKNOWN;
        }
    }

    /**
     * Writes the given record to the history service
     *
     * @param callRecord CallRecord
     * @param source Contact
     * @param destination Contact
     */
    private void writeCall(CallRecordImpl callRecord, Contact source, Contact destination)
    {
        StringBuilder callPeerIDs = new StringBuilder();
        StringBuilder callPeerNames = new StringBuilder();
        StringBuilder callPeerStartTime = new StringBuilder();
        StringBuilder callPeerEndTime = new StringBuilder();
        StringBuilder callPeerStates = new StringBuilder();
        StringBuilder callPeerSecondaryIDs = new StringBuilder();

        // Generate the delimited peerCallRecord item values
        for (CallPeerRecord item : callRecord.getPeerRecords()) {
            if (callPeerIDs.length() > 0) {
                callPeerIDs.append(DELIMITER);
                callPeerStartTime.append(DELIMITER);
                callPeerEndTime.append(DELIMITER);
                callPeerStates.append(DELIMITER);
                callPeerNames.append(DELIMITER);
                callPeerSecondaryIDs.append(DELIMITER);
            }

            callPeerIDs.append(item.getPeerAddress());
            callPeerStartTime.append(item.getStartTime().getTime());
            callPeerEndTime.append(item.getEndTime().getTime());
            callPeerStates.append(item.getState().getStateString());
            callPeerNames.append(item.getDisplayName());
            callPeerSecondaryIDs.append((item.getPeerSecondaryAddress() == null)
                    ? "" : item.getPeerSecondaryAddress());
        }

        Long timeStamp = new Date().getTime();
        String Uuid = callRecord.getCallUuid();
        String accountUid = callRecord.getSourceCall().getProtocolProvider().getAccountID().getAccountUniqueID();

        contentValues.clear();
        contentValues.put(CallHistoryService.UUID, Uuid);
        contentValues.put(CallHistoryService.TIME_STAMP, timeStamp);
        contentValues.put(CallHistoryService.ACCOUNT_UID, accountUid);
        contentValues.put(CallHistoryService.CALL_START, callRecord.getStartTime().getTime());
        contentValues.put(CallHistoryService.CALL_END, callRecord.getEndTime().getTime());
        contentValues.put(CallHistoryService.DIRECTION, callRecord.getDirection());
        contentValues.put(CallHistoryService.ENTITY_FULL_JID, callPeerIDs.toString());
        contentValues.put(CallHistoryService.ENTITY_CALL_START, callPeerStartTime.toString());
        contentValues.put(CallHistoryService.ENTITY_CALL_END, callPeerEndTime.toString());
        contentValues.put(CallHistoryService.ENTITY_CALL_STATE, callPeerStates.toString());
        contentValues.put(CallHistoryService.CALL_END_REASON, callRecord.getEndReason());
        contentValues.put(CallHistoryService.ENTITY_JID, callPeerNames.toString());
        contentValues.put(CallHistoryService.SEC_ENTITY_ID, callPeerSecondaryIDs.toString());

        mDB.insert(CallHistoryService.TABLE_NAME, null, contentValues);
    }

    /**
     * Adding progress listener for monitoring progress of search process
     *
     * @param listener HistorySearchProgressListener
     */
    public void addSearchProgressListener(CallHistorySearchProgressListener listener)
    {
        synchronized (progressListeners) {
            progressListeners.put(listener, new SearchProgressWrapper(listener));
        }
    }

    /**
     * Removing progress listener
     *
     * @param listener HistorySearchProgressListener
     */
    public void removeSearchProgressListener(CallHistorySearchProgressListener listener)
    {
        synchronized (progressListeners) {
            progressListeners.remove(listener);
        }
    }

    /**
     * Adding <tt>CallHistoryRecordListener</tt> listener to the list.
     *
     * @param listener CallHistoryRecordListener
     */
    public void addCallHistoryRecordListener(CallHistoryPeerRecordListener listener)
    {
        synchronized (callHistoryRecordListeners) {
            callHistoryRecordListeners.add(listener);
        }
    }

    /**
     * Removing <tt>CallHistoryRecordListener</tt> listener
     *
     * @param listener CallHistoryRecordListener
     */
    public void removeCallHistoryRecordListener(CallHistoryPeerRecordListener listener)
    {
        synchronized (callHistoryRecordListeners) {
            callHistoryRecordListeners.remove(listener);
        }
    }

    /**
     * Fires the given event to all <tt>CallHistoryRecordListener</tt> listeners
     *
     * @param event the <tt>CallHistoryRecordReceivedEvent</tt> event to be fired
     */
    private void fireCallHistoryRecordReceivedEvent(CallHistoryPeerRecordEvent event)
    {
        List<CallHistoryPeerRecordListener> tmpListeners;
        synchronized (callHistoryRecordListeners) {
            tmpListeners = new LinkedList<>(callHistoryRecordListeners);
        }

        for (CallHistoryPeerRecordListener listener : tmpListeners) {
            listener.callPeerRecordReceived(event);
        }
    }

    /**
     * CallListener implementation for incoming calls
     *
     * @param event CallEvent
     */
    public void incomingCallReceived(CallEvent event)
    {
        handleNewCall(event.getSourceCall(), CallRecord.IN);
    }

    /**
     * CallListener implementation for outgoing calls
     *
     * @param event CallEvent
     */
    public void outgoingCallCreated(CallEvent event)
    {
        handleNewCall(event.getSourceCall(), CallRecord.OUT);
    }

    /**
     * CallListener implementation for call endings
     *
     * @param event CallEvent
     */
    public void callEnded(CallEvent event)
    {
        // We store the call in the callStateChangeEvent where we
        // have more information on the previous state of the call.
    }

    /**
     * Adding a record for joining peer
     *
     * @param callPeer CallPeer
     */
    private void handlePeerAdded(CallPeer callPeer)
    {
        CallRecord callRecord = findCallRecord(callPeer.getCall());
        // no such call
        if (callRecord == null)
            return;

        callPeer.addCallPeerListener(new CallPeerAdapter()
        {
            @Override
            public void peerStateChanged(CallPeerChangeEvent evt)
            {
                if (!evt.getNewValue().equals(CallPeerState.DISCONNECTED)) {
                    CallPeerRecordImpl peerRecord = findPeerRecord(evt.getSourceCallPeer());
                    if (peerRecord == null)
                        return;

                    CallPeerState newState = (CallPeerState) evt.getNewValue();
                    if (newState.equals(CallPeerState.CONNECTED)
                            && !CallPeerState.isOnHold((CallPeerState) evt.getOldValue()))
                        peerRecord.setStartTime(new Date());

                    peerRecord.setState(newState);
                    // Disconnected / Busy
                    // Disconnected / Connecting - fail
                    // Disconnected / Connected
                }
            }
        });

        Date startDate = new Date();
        CallPeerRecordImpl newRec = new CallPeerRecordImpl(callPeer.getAddress(), startDate, startDate);
        newRec.setDisplayName(callPeer.getDisplayName());

        callRecord.getPeerRecords().add(newRec);
        fireCallHistoryRecordReceivedEvent(new CallHistoryPeerRecordEvent(callPeer.getAddress(),
                startDate, callPeer.getProtocolProvider()));
    }

    /**
     * Adding a record for removing peer from call
     *
     * @param callPeer CallPeer
     * @param srcCall Call
     */
    private void handlePeerRemoved(CallPeer callPeer, Call srcCall)
    {
        CallRecord callRecord = findCallRecord(srcCall);
        if (callRecord == null)
            return;

        String pAddress = callPeer.getAddress();
        CallPeerRecordImpl cpRecord = (CallPeerRecordImpl) callRecord.findPeerRecord(pAddress);
        // no such peer
        if (cpRecord == null)
            return;

        if (!callPeer.getState().equals(CallPeerState.DISCONNECTED))
            cpRecord.setState(callPeer.getState());

        CallPeerState cpRecordState = cpRecord.getState();

        if (cpRecordState.equals(CallPeerState.CONNECTED)
                || CallPeerState.isOnHold(cpRecordState)) {
            cpRecord.setEndTime(new Date());
        }
    }

    /**
     * Updates the secondary address field of call record.
     *
     * @param date the start date of the record which will be updated.
     * @param peerAddress the address of the peer of the record which will be updated.
     * @param address the value of the secondary address .
     */
    public void updateCallRecordPeerSecondaryAddress(final Date date, final String peerAddress, final String address)
    {
        boolean callRecordFound = false;
        synchronized (currentCallRecords) {
            for (CallRecord record : currentCallRecords) {
                for (CallPeerRecord peerRecord : record.getPeerRecords()) {
                    if (peerRecord.getPeerAddress().equals(peerAddress)
                            && peerRecord.getStartTime().equals(date)) {
                        callRecordFound = true;
                        peerRecord.setPeerSecondaryAddress(address);
                    }
                }
            }
        }
        if (callRecordFound)
            return;

        // update the record in db for found match record
        String[] columns = {CallHistoryService.UUID, CallHistoryService.ENTITY_FULL_JID,
                CallHistoryService.ENTITY_CALL_START, CallHistoryService.SEC_ENTITY_ID};
        String[] args = {peerAddress, String.valueOf(date.getTime())};

        Cursor cursor = mDB.query(CallHistoryService.TABLE_NAME, columns,
                CallHistoryService.ENTITY_FULL_JID + " LIKE '%?%' AND "
                        + CallHistoryService.ENTITY_CALL_START + " LIKE '%?%')",
                args, null, null, ORDER_ASC);

        // process only for record that have matched peerID and date and same index locations
        while (cursor.moveToNext()) {
            String uuid = cursor.getString(0);

            List<String> peerIDs = getCSVs(cursor.getString(1));
            int i = peerIDs.indexOf(peerAddress);
            if (i == -1)
                continue;

            String dateString = getCSVs(cursor.getString(2)).get(i);
            if (!String.valueOf(date.getTime()).equals(dateString))
                continue;

            List<String> secondaryID = getCSVs(cursor.getString(3));
            secondaryID.set(i, address);
            String secEntityID = "";
            int j = 0;
            for (String sid : secondaryID) {
                if (j++ != 0)
                    secEntityID += DELIMITER;
                secEntityID += sid;
            }

            args = new String[]{uuid};
            contentValues.clear();
            contentValues.put(CallHistoryService.SEC_ENTITY_ID, secEntityID);

            mDB.update(CallHistoryService.TABLE_NAME, contentValues, CallHistoryService.UUID + "=?", args);
        }
        cursor.close();
    }

    /**
     * Finding a CallRecord for the given call
     *
     * @param call Call
     * @return CallRecord
     */
    private CallRecordImpl findCallRecord(Call call)
    {
        synchronized (currentCallRecords) {
            for (CallRecordImpl item : currentCallRecords) {
                if (item.getSourceCall().equals(call))
                    return item;
            }
        }
        return null;
    }

    /**
     * Returns the peer record for the given peer
     *
     * @param callPeer CallPeer peer
     * @return CallPeerRecordImpl the corresponding record
     */
    private CallPeerRecordImpl findPeerRecord(CallPeer callPeer)
    {
        CallRecord record = findCallRecord(callPeer.getCall());
        if (record == null)
            return null;

        return (CallPeerRecordImpl) record.findPeerRecord(callPeer.getAddress());
    }

    /**
     * Adding a record for a new call
     *
     * @param sourceCall Call
     * @param direction String
     */
    private void handleNewCall(Call sourceCall, String direction)
    {
        // if call exist. its not new
        synchronized (currentCallRecords) {
            for (CallRecordImpl currentCallRecord : currentCallRecords) {
                if (currentCallRecord.getSourceCall().equals(sourceCall))
                    return;
            }
        }

        CallRecordImpl newRecord = new CallRecordImpl(null, direction, new Date(), null);
        newRecord.setSourceCall(sourceCall);
        sourceCall.addCallChangeListener(historyCallChangeListener);
        synchronized (currentCallRecords) {
            currentCallRecords.add(newRecord);
        }

        // if has already participants Dispatch them
        Iterator<? extends CallPeer> callPeers = sourceCall.getCallPeers();
        while (callPeers.hasNext()) {
            handlePeerAdded(callPeers.next());
        }
    }

    /**
     * A wrapper around HistorySearchProgressListener that fires events for CallHistorySearchProgressListener
     */
    private class SearchProgressWrapper implements HistorySearchProgressListener
    {
        private CallHistorySearchProgressListener listener;
        int contactCount = 0;
        int currentContactCount = 0;
        int currentProgress = 0;
        int lastHistoryProgress = 0;

        SearchProgressWrapper(CallHistorySearchProgressListener listener)
        {
            this.listener = listener;
        }

        public void progressChanged(ProgressEvent evt)
        {
            int progress = getProgressMapping(evt.getProgress());

            listener.progressChanged(new net.java.sip.communicator.service.callhistory.event
                    .ProgressEvent(CallHistoryServiceImpl.this, evt, progress));
        }

        /**
         * Calculates the progress according the count of the contacts we will search
         *
         * @param historyProgress int
         * @return int
         */
        private int getProgressMapping(int historyProgress)
        {
            currentProgress += (historyProgress - lastHistoryProgress) / contactCount;

            if (historyProgress == HistorySearchProgressListener.PROGRESS_MAXIMUM_VALUE) {
                currentContactCount++;
                lastHistoryProgress = 0;

                // this is the last one and the last event fire the max
                // there will be looses in currentProgress due to the division
                if (currentContactCount == contactCount)
                    currentProgress = CallHistorySearchProgressListener.PROGRESS_MAXIMUM_VALUE;
            }
            else
                lastHistoryProgress = historyProgress;
            return currentProgress;
        }

        /**
         * clear the values
         */
        void clear()
        {
            contactCount = 0;
            currentProgress = 0;
            lastHistoryProgress = 0;
            currentContactCount = 0;
        }
    }

    /**
     * Used to compare CallRecords and to be ordered in TreeSet according their timestamp
     */
    private static class CallRecordComparator implements Comparator<CallRecord>
    {
        public int compare(CallRecord o1, CallRecord o2)
        {
            return o2.getStartTime().compareTo(o1.getStartTime());
        }
    }

    /**
     * Receive events for adding or removing peers from a call
     */
    private class HistoryCallChangeListener implements CallChangeListener
    {
        /**
         * Indicates that a new call peer has joined the source call.
         *
         * @param evt the <tt>CallPeerEvent</tt> containing the source call and call peer.
         */
        public void callPeerAdded(CallPeerEvent evt)
        {
            handlePeerAdded(evt.getSourceCallPeer());
        }

        /**
         * Indicates that a call peer has left the source call.
         *
         * @param evt the <tt>CallPeerEvent</tt> containing the source call and call peer.
         */
        public void callPeerRemoved(CallPeerEvent evt)
        {
            handlePeerRemoved(evt.getSourceCallPeer(), evt.getSourceCall());
        }

        /**
         * A dummy implementation of this listener's callStateChanged() method.
         *
         * @param evt the <tt>CallChangeEvent</tt> instance containing the source calls and its old and new state.
         */
        public void callStateChanged(CallChangeEvent evt)
        {
            CallRecordImpl callRecord = findCallRecord(evt.getSourceCall());
            // no such call
            if (callRecord == null)
                return;

            if (!CallChangeEvent.CALL_STATE_CHANGE.equals(evt.getPropertyName()))
                return;

            if (CallState.CALL_ENDED.equals(evt.getNewValue())) {
                boolean writeRecord = true;
                if (CallState.CALL_INITIALIZATION.equals(evt.getOldValue())) {
                    callRecord.setEndTime(callRecord.getStartTime());

                    // if call was answered elsewhere, add its reason; so we can distinguish it from missed
                    if ((evt.getCause() != null)
                            && (evt.getCause().getReasonCode() == CallPeerChangeEvent.NORMAL_CALL_CLEARING)) {
                        callRecord.setEndReason(evt.getCause().getReasonCode());
                        if ("Call completed elsewhere".equals(evt.getCause().getReasonString())) {
                            writeRecord = false;
                        }
                    }
                }
                else
                    callRecord.setEndTime(new Date());

                if (writeRecord) {
                    writeCall(callRecord, null, null);
                }
                synchronized (currentCallRecords) {
                    currentCallRecords.remove(callRecord);
                }
            }
        }
    }

    /**
     * Returns the <tt>ProtocolProviderService</tt> corresponding to the given account identifier.
     *
     * @param accountUID the identifier of the account.
     * @return the <tt>ProtocolProviderService</tt> corresponding to the given account identifier
     */
    private static ProtocolProviderService getProtocolProvider(String accountUID)
    {
        Map<Object, ProtocolProviderFactory> ppsRefs = CallHistoryActivator.getProtocolProviderFactories();

        if (ppsRefs != null) {
            for (ProtocolProviderFactory providerFactory : ppsRefs.values()) {
                for (AccountID accountID : providerFactory.getRegisteredAccounts()) {
                    if (accountID.getAccountUniqueID().equals(accountUID)) {
                        ServiceReference<ProtocolProviderService> serRef
                                = providerFactory.getProviderForAccount(accountID);
                        return CallHistoryActivator.bundleContext.getService(serRef);
                    }
                }
            }
        }
        return null;
    }

    /**
     * Permanently removes all locally stored call history.
     */
    public void eraseLocallyStoredHistory(List<String> callUUIDs)
    {
        for (String uuid : callUUIDs) {
            String[] args = {uuid};
            mDB.delete(CallHistoryService.TABLE_NAME, CallHistoryService.UUID + "=?", args);
        }
    }
}
