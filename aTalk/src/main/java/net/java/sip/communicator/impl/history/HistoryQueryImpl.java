/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package net.java.sip.communicator.impl.history;

import java.util.Collection;
import java.util.LinkedList;
import java.util.Vector;

import net.java.sip.communicator.service.history.HistoryQuery;
import net.java.sip.communicator.service.history.event.HistoryQueryListener;
import net.java.sip.communicator.service.history.event.HistoryQueryStatusEvent;
import net.java.sip.communicator.service.history.event.HistoryRecordEvent;
import net.java.sip.communicator.service.history.records.HistoryRecord;

/**
 * The <code>HistoryQueryImpl</code> is an implementation of the <code>HistoryQuery</code> interface. It
 * corresponds to a query made through the <code>InteractiveHistoryReader</code>. It allows to be
 * canceled, to listen for changes in the results and to obtain initial results if available.
 *
 * @author Yana Stamcheva
 */
public class HistoryQueryImpl implements HistoryQuery {
    /**
     * The list of query listeners registered in this query.
     */
    private final Collection<HistoryQueryListener> queryListeners = new LinkedList<>();

    /**
     * The list of history records, which is the result of this query.
     */
    private final Collection<HistoryRecord> historyRecords = new Vector<>();

    /**
     * Indicates if this query has been canceled.
     */
    private boolean isCanceled = false;

    /**
     * The query string we're looking for in this query.
     */
    private final String queryString;

    /**
     * Creates an instance of <code>HistoryQueryImpl</code> by specifying the query string it was
     * created for.
     *
     * @param queryString the query string we're looking for in this query
     */
    public HistoryQueryImpl(String queryString) {
        this.queryString = queryString;
    }

    /**
     * Cancels this query.
     */
    public void cancel() {
        isCanceled = true;
    }

    /**
     * Indicates if this query has been canceled.
     *
     * @return <code>true</code> if this query has been canceled, otherwise returns <code>false</code>
     */
    boolean isCanceled() {
        return isCanceled;
    }

    /**
     * Returns a collection of the results for this query. It's up to the implementation to
     * determine how and when to ill this list of results.
     * <p>
     * This method could be used in order to obtain first fast initial results and then obtain the
     * additional results through the <code>HistoryQueryListener</code>, which should improve user
     * experience when waiting for results.
     *
     * @return a collection of the initial results for this query
     */
    public Collection<HistoryRecord> getHistoryRecords() {
        return new Vector<>(historyRecords);
    }

    /**
     * Adds the given <code>HistoryQueryListener</code> to the list of listeners interested in query
     * result changes.
     *
     * @param l the <code>HistoryQueryListener</code> to add
     */
    public void addHistoryRecordsListener(HistoryQueryListener l) {
        synchronized (queryListeners) {
            queryListeners.add(l);
        }
    }

    /**
     * Removes the given <code>HistoryQueryListener</code> from the list of listeners interested in
     * query result changes.
     *
     * @param l the <code>HistoryQueryListener</code> to remove
     */
    public void removeHistoryRecordsListener(HistoryQueryListener l) {
        synchronized (queryListeners) {
            queryListeners.remove(l);
        }
    }

    /**
     * Adds the given <code>HistoryRecord</code> to the result list of this query and notifies all
     * interested listeners that a new record is received.
     *
     * @param record the <code>HistoryRecord</code> to add
     */
    void addHistoryRecord(HistoryRecord record) {
        historyRecords.add(record);
        fireQueryEvent(record);
    }

    /**
     * Sets this query status to the given <code>queryStatus</code> and notifies all interested
     * listeners of the change.
     *
     * @param queryStatus the new query status to set
     */
    void setStatus(int queryStatus) {
        fireQueryStatusEvent(queryStatus);
    }

    /**
     * Notifies all registered <code>HistoryQueryListener</code>s that a new record has been received.
     *
     * @param record the <code>HistoryRecord</code>
     */
    private void fireQueryEvent(HistoryRecord record) {
        HistoryRecordEvent event = new HistoryRecordEvent(this, record);

        synchronized (queryListeners) {
            for (HistoryQueryListener l : queryListeners) {
                l.historyRecordReceived(event);
            }
        }
    }

    /**
     * Notifies all registered <code>HistoryQueryListener</code>s that a new status has been received.
     *
     * @param newStatus the new status
     */
    private void fireQueryStatusEvent(int newStatus) {
        HistoryQueryStatusEvent event = new HistoryQueryStatusEvent(this, newStatus);

        synchronized (queryListeners) {
            for (HistoryQueryListener l : queryListeners) {
                l.queryStatusChanged(event);
            }
        }
    }

    /**
     * Returns the query string, this query was created for.
     *
     * @return the query string, this query was created for
     */
    public String getQueryString() {
        return queryString;
    }
}
