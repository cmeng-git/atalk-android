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

import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;

import net.java.sip.communicator.service.callhistory.CallHistoryQuery;
import net.java.sip.communicator.service.callhistory.CallRecord;
import net.java.sip.communicator.service.callhistory.event.CallHistoryQueryListener;
import net.java.sip.communicator.service.callhistory.event.CallHistoryQueryStatusEvent;
import net.java.sip.communicator.service.callhistory.event.CallRecordEvent;
import net.java.sip.communicator.service.contactsource.ContactQuery;
import net.java.sip.communicator.service.contactsource.ContactQueryListener;
import net.java.sip.communicator.service.contactsource.ContactQueryStatusEvent;
import net.java.sip.communicator.service.contactsource.ContactReceivedEvent;
import net.java.sip.communicator.service.contactsource.ContactSourceService;
import net.java.sip.communicator.service.contactsource.SourceContact;

import org.atalk.android.R;
import org.atalk.android.aTalkApp;

/**
 * The <code>CallHistoryContactSource</code> is the contact source for the call history.
 *
 * @author Yana Stamcheva
 * @author Hristo Terezov
 * @author Eng Chong Meng
 */
public class CallHistoryContactSource implements ContactSourceService {
    /**
     * Returns the display name of this contact source.
     *
     * @return the display name of this contact source
     */
    public String getDisplayName() {
        return aTalkApp.getResString(R.string.call_history_name);
    }

    /**
     * Creates query for the given <code>searchString</code>.
     *
     * @param queryString the string to search for
     *
     * @return the created query
     */
    public ContactQuery createContactQuery(String queryString) {
        return createContactQuery(queryString, 50);
    }

    /**
     * Creates query for the given <code>searchString</code>.
     *
     * @param queryString the string to search for
     * @param contactCount the maximum count of result contacts
     *
     * @return the created query
     */
    public ContactQuery createContactQuery(String queryString, int contactCount) {
        if (queryString != null && !queryString.isEmpty()) {
            return new CallHistoryContactQuery(CallHistoryActivator.getCallHistoryService()
                    .findByPeer(queryString, contactCount));
        }
        else {
            return new CallHistoryContactQuery(
                    CallHistoryActivator.getCallHistoryService().findLast(contactCount));
        }
    }

    /**
     * The <code>CallHistoryContactQuery</code> contains information about a current
     * query to the contact source.
     */
    private class CallHistoryContactQuery implements ContactQuery {
        /**
         * A list of all registered query listeners.
         */
        private final List<ContactQueryListener> queryListeners = new LinkedList<>();

        /**
         * A list of all source contact results.
         */
        private final List<SourceContact> sourceContacts = new LinkedList<>();

        /**
         * The underlying <code>CallHistoryQuery</code>, on which this
         * <code>ContactQuery</code> is based.
         */
        private CallHistoryQuery callHistoryQuery;

        /**
         * Indicates the status of this query. When created this query is in rogress.
         */
        private int status = QUERY_IN_PROGRESS;

        /**
         * Iterator for the queried contacts.
         */
        Iterator<CallRecord> recordsIter = null;

        /**
         * Indicates whether show more label should be displayed or not.
         */
        private boolean showMoreLabelAllowed = true;

        /**
         * Creates an instance of <code>CallHistoryContactQuery</code> by specifying the list of call records results.
         *
         * @param callRecords the list of call records, which are the result of this query
         */
        public CallHistoryContactQuery(Collection<CallRecord> callRecords) {
            recordsIter = callRecords.iterator();
            Iterator<CallRecord> recordsIter = callRecords.iterator();

            while (recordsIter.hasNext() && status != QUERY_CANCELED) {
                sourceContacts.add(
                        new CallHistorySourceContact(CallHistoryContactSource.this, recordsIter.next()));
            }
            showMoreLabelAllowed = false;
        }

        @Override
        public void start() {
            if (callHistoryQuery != null) {
                callHistoryQuery.addQueryListener(new CallHistoryQueryListener() {
                    public void callRecordReceived(CallRecordEvent event) {
                        if (getStatus() == ContactQuery.QUERY_CANCELED)
                            return;

                        SourceContact contact = new CallHistorySourceContact(
                                CallHistoryContactSource.this, event.getCallRecord());
                        sourceContacts.add(contact);
                        fireQueryEvent(contact);
                    }

                    public void queryStatusChanged(CallHistoryQueryStatusEvent event) {
                        status = event.getEventType();
                        fireQueryStatusEvent(status);
                    }
                });
                recordsIter = callHistoryQuery.getCallRecords().iterator();
            }

            while (recordsIter.hasNext()) {
                SourceContact contact = new CallHistorySourceContact(
                        CallHistoryContactSource.this, recordsIter.next());
                sourceContacts.add(contact);
                fireQueryEvent(contact);
            }
            if (status != QUERY_CANCELED) {
                status = QUERY_COMPLETED;
                if (callHistoryQuery == null)
                    fireQueryStatusEvent(status);
            }
        }

        /**
         * Creates an instance of <code>CallHistoryContactQuery</code> based on the
         * given <code>callHistoryQuery</code>.
         *
         * @param callHistoryQuery the query used to track the call history
         */
        public CallHistoryContactQuery(CallHistoryQuery callHistoryQuery) {
            this.callHistoryQuery = callHistoryQuery;
        }

        /**
         * Adds the given <code>ContactQueryListener</code> to the list of query
         * listeners.
         *
         * @param l the <code>ContactQueryListener</code> to add
         */
        public void addContactQueryListener(ContactQueryListener l) {
            synchronized (queryListeners) {
                queryListeners.add(l);
            }
        }

        /**
         * This query could not be canceled.
         */
        public void cancel() {
            status = QUERY_CANCELED;

            if (callHistoryQuery != null)
                callHistoryQuery.cancel();
        }

        /**
         * Returns the status of this query. One of the static constants defined
         * in this class.
         *
         * @return the status of this query
         */
        @Override
        public int getStatus() {
            return status;
        }

        /**
         * Removes the given <code>ContactQueryListener</code> from the list of
         * query listeners.
         *
         * @param l the <code>ContactQueryListener</code> to remove
         */
        public void removeContactQueryListener(ContactQueryListener l) {
            synchronized (queryListeners) {
                queryListeners.remove(l);
            }
        }

        /**
         * Returns a list containing the results of this query.
         *
         * @return a list containing the results of this query
         */
        public List<SourceContact> getQueryResults() {
            return sourceContacts;
        }

        /**
         * Returns the <code>ContactSourceService</code>, where this query was first initiated.
         *
         * @return the <code>ContactSourceService</code>, where this query was first initiated
         */
        public ContactSourceService getContactSource() {
            return CallHistoryContactSource.this;
        }

        /**
         * Notifies all registered <code>ContactQueryListener</code>s that a new
         * contact has been received.
         *
         * @param contact the <code>SourceContact</code> this event is about
         */
        private void fireQueryEvent(SourceContact contact) {
            ContactReceivedEvent event = new ContactReceivedEvent(this, contact, showMoreLabelAllowed);

            Collection<ContactQueryListener> listeners;
            synchronized (queryListeners) {
                listeners = new ArrayList<>(queryListeners);
            }

            for (ContactQueryListener l : listeners)
                l.contactReceived(event);
        }

        /**
         * Notifies all registered <code>ContactQueryListener</code>s that a new
         * record has been received.
         *
         * @param newStatus the new status
         */
        private void fireQueryStatusEvent(int newStatus) {
            Collection<ContactQueryListener> listeners;
            ContactQueryStatusEvent event = new ContactQueryStatusEvent(this, newStatus);

            synchronized (queryListeners) {
                listeners = new ArrayList<>(queryListeners);
            }

            for (ContactQueryListener l : listeners)
                l.queryStatusChanged(event);
        }

        public String getQueryString() {
            return callHistoryQuery.getQueryString();
        }
    }

    /**
     * Returns default type to indicate that this contact source can be queried
     * by default filters.
     *
     * @return the type of this contact source
     */
    public int getType() {
        return HISTORY_TYPE;
    }

    /**
     * Returns the index of the contact source in the result list.
     *
     * @return the index of the contact source in the result list
     */
    public int getIndex() {
        return -1;
    }
}
