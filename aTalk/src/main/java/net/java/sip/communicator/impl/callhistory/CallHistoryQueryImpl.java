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
package net.java.sip.communicator.impl.callhistory;

import net.java.sip.communicator.service.callhistory.CallHistoryQuery;
import net.java.sip.communicator.service.callhistory.CallRecord;
import net.java.sip.communicator.service.callhistory.event.CallHistoryQueryListener;
import net.java.sip.communicator.service.callhistory.event.CallHistoryQueryStatusEvent;
import net.java.sip.communicator.service.callhistory.event.CallRecordEvent;
import net.java.sip.communicator.service.history.HistoryQuery;
import net.java.sip.communicator.service.history.event.HistoryQueryListener;
import net.java.sip.communicator.service.history.event.HistoryQueryStatusEvent;
import net.java.sip.communicator.service.history.event.HistoryRecordEvent;
import net.java.sip.communicator.service.history.records.HistoryRecord;

import java.util.Collection;
import java.util.LinkedList;
import java.util.Vector;

/**
 * @author Yana Stamcheva
 * @author Eng Chong Meng
 */
public class CallHistoryQueryImpl implements CallHistoryQuery
{
	private final Collection<CallHistoryQueryListener> queryListeners = new LinkedList<>();
	private final Collection<CallRecord> callRecords = new Vector<>();
	private final HistoryQuery historyQuery;

	/**
	 * Creates an instance of <code>CallHistoryQueryImpl</code> by specifying the underlying
	 * <code>HistoryQuery</code>.
	 *
	 * @param query
	 * 		the underlying <code>HistoryQuery</code> this query is based on
	 */
	public CallHistoryQueryImpl(HistoryQuery query)
	{
		this.historyQuery = query;
		historyQuery.addHistoryRecordsListener(new HistoryQueryListener()
		{
			public void historyRecordReceived(HistoryRecordEvent event)
			{
				CallRecord callRecord = CallHistoryServiceImpl.createCallRecordFromProperties(
						event.getHistoryRecord().getProperties());

				callRecords.add(callRecord);
				fireQueryEvent(callRecord);
			}

			public void queryStatusChanged(HistoryQueryStatusEvent event)
			{
				fireQueryStatusEvent(event.getEventType());
			}
		});

		for (HistoryRecord historyRecord : historyQuery.getHistoryRecords()) {
			CallRecord callRecord = CallHistoryServiceImpl
					.createCallRecordFromProperties(historyRecord.getProperties());
			callRecords.add(callRecord);
		}
	}

	/**
	 * Cancels this query.
	 */
	public void cancel()
	{
		historyQuery.cancel();
	}

	/**
	 * Returns a collection of the results for this query. It's up to the implementation to
	 * determine how and when to fill this list of results.
	 * <p>
	 * This method could be used in order to obtain first fast initial results and then obtain
	 * the additional results through the <code>CallHistoryQueryListener</code>, which should improve
	 * user experience when waiting for results.
	 *
	 * @return a collection of the initial results for this query
	 */
	public Collection<CallRecord> getCallRecords()
	{
		return new Vector<>(callRecords);
	}

	/**
	 * Adds the given <code>CallHistoryQueryListener</code> to the list of listeners interested in
	 * query result changes.
	 *
	 * @param l
	 * 		the <code>CallHistoryQueryListener</code> to add
	 */
	public void addQueryListener(CallHistoryQueryListener l)
	{
		synchronized (queryListeners) {
			queryListeners.add(l);
		}
	}

	/**
	 * Removes the given <code>CallHistoryQueryListener</code> from the list of listeners interested
	 * in query result changes.
	 *
	 * @param l
	 * 		the <code>CallHistoryQueryListener</code> to remove
	 */
	public void removeQueryListener(CallHistoryQueryListener l)
	{
		synchronized (queryListeners) {
			queryListeners.remove(l);
		}
	}

	/**
	 * Notifies all registered <code>HistoryQueryListener</code>s that a new record has been received.
	 *
	 * @param record
	 * 		the <code>HistoryRecord</code>
	 */
	private void fireQueryEvent(CallRecord record)
	{
		CallRecordEvent event = new CallRecordEvent(this, record);
		synchronized (queryListeners) {
			for (CallHistoryQueryListener l : queryListeners)
				l.callRecordReceived(event);
		}
	}

	/**
	 * Notifies all registered <code>HistoryQueryListener</code>s that a new status has been received.
	 *
	 * @param newStatus
	 * 		the new status
	 */
	private void fireQueryStatusEvent(int newStatus)
	{
		CallHistoryQueryStatusEvent event = new CallHistoryQueryStatusEvent(this, newStatus);
		synchronized (queryListeners) {
			for (CallHistoryQueryListener l : queryListeners)
				l.queryStatusChanged(event);
		}
	}

	/**
	 * Returns the query string, this query was created for.
	 *
	 * @return the query string, this query was created for
	 */
	public String getQueryString()
	{
		return historyQuery.getQueryString();
	}

	/**
	 * Adds the given <code>CallRecord</code> to the result list of this query and notifies all
	 * interested listeners that a new record is received.
	 *
	 * @param callRecord
	 * 		the <code>CallRecord</code> to add
	 */
	public void addHistoryRecord(CallRecord callRecord)
	{
		callRecords.add(callRecord);
		fireQueryEvent(callRecord);
	}
}
