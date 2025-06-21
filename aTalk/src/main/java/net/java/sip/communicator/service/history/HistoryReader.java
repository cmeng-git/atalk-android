/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package net.java.sip.communicator.service.history;

import java.util.Date;

import net.java.sip.communicator.service.history.event.HistorySearchProgressListener;
import net.java.sip.communicator.service.history.records.HistoryRecord;

/**
 * Used to search over the history records
 *
 * @author Alexander Pelov
 * @author Damian Minkov
 */
public interface HistoryReader {
    /**
     * Searches the history for all records with timestamp after <code>startDate</code>.
     *
     * @param startDate the date after all records will be returned
     *
     * @return the found records
     *
     * @throws RuntimeException Thrown if an exception occurs during the execution of the query,
     * such as internal IO error.
     */
    QueryResultSet<HistoryRecord> findByStartDate(Date startDate)
            throws RuntimeException;

    /**
     * Searches the history for all records with timestamp before <code>endDate</code>.
     *
     * @param endDate the date before which all records will be returned
     *
     * @return the found records
     *
     * @throws RuntimeException Thrown if an exception occurs during the execution of the query,
     * such as internal IO error.
     */
    QueryResultSet<HistoryRecord> findByEndDate(Date endDate)
            throws RuntimeException;

    /**
     * Searches the history for all records with timestamp between <code>startDate</code> and
     * <code>endDate</code>.
     *
     * @param startDate start of the interval in which we search
     * @param endDate end of the interval in which we search
     *
     * @return the found records
     *
     * @throws RuntimeException Thrown if an exception occurs during the execution of the query,
     * such as internal IO error.
     */
    QueryResultSet<HistoryRecord> findByPeriod(Date startDate, Date endDate)
            throws RuntimeException;

    /**
     * Searches the history for all records containing the <code>keyword</code>.
     *
     * @param keyword the keyword to search for
     * @param field the field where to look for the keyword
     *
     * @return the found records
     *
     * @throws RuntimeException Thrown if an exception occurs during the execution of the query,
     * such as internal IO error.
     */
    QueryResultSet<HistoryRecord> findByKeyword(String keyword, String field)
            throws RuntimeException;

    /**
     * Searches the history for all records containing the <code>keyword</code>.
     *
     * @param keyword the keyword to search for
     * @param field the field where to look for the keyword
     * @param caseSensitive is keywords search case sensitive
     *
     * @return the found records
     *
     * @throws RuntimeException Thrown if an exception occurs during the execution of the query,
     * such as internal IO error.
     */
    QueryResultSet<HistoryRecord> findByKeyword(String keyword, String field, boolean
            caseSensitive)
            throws RuntimeException;

    /**
     * Searches the history for all records containing all <code>keywords</code>.
     *
     * @param keywords array of keywords we search for
     * @param field the field where to look for the keyword
     *
     * @return the found records
     *
     * @throws RuntimeException Thrown if an exception occurs during the execution of the query,
     * such as internal IO error.
     */
    QueryResultSet<HistoryRecord> findByKeywords(String[] keywords, String field)
            throws RuntimeException;

    /**
     * Searches the history for all records containing all <code>keywords</code>.
     *
     * @param keywords array of keywords we search for
     * @param field the field where to look for the keyword
     * @param caseSensitive is keywords search case sensitive
     *
     * @return the found records
     *
     * @throws RuntimeException Thrown if an exception occurs during the execution of the query,
     * such as internal IO error.
     */
    QueryResultSet<HistoryRecord> findByKeywords(String[] keywords, String field, boolean
            caseSensitive)
            throws RuntimeException;

    /**
     * Searches for all history records containing all <code>keywords</code>, with timestamp between
     * <code>startDate</code> and <code>endDate</code>.
     *
     * @param startDate start of the interval in which we search
     * @param endDate end of the interval in which we search
     * @param keywords array of keywords we search for
     * @param field the field where to look for the keyword
     *
     * @return the found records
     *
     * @throws UnsupportedOperationException Thrown if an exception occurs during the execution of the query,
     * such as internal IO error.
     */
    QueryResultSet<HistoryRecord> findByPeriod(Date startDate, Date endDate, String[]
            keywords, String field)
            throws UnsupportedOperationException;

    /**
     * Searches for all history records containing all <code>keywords</code>, with timestamp between
     * <code>startDate</code> and <code>endDate</code>.
     *
     * @param startDate start of the interval in which we search
     * @param endDate end of the interval in which we search
     * @param keywords array of keywords we search for
     * @param field the field where to look for the keyword
     * @param caseSensitive is keywords search case sensitive
     *
     * @return the found records
     *
     * @throws UnsupportedOperationException Thrown if an exception occurs during the execution of the query,
     * such as internal IO error.
     */
    QueryResultSet<HistoryRecord> findByPeriod(Date startDate, Date endDate, String[]
            keywords, String field, boolean caseSensitive)
            throws UnsupportedOperationException;

    /**
     * Returns the supplied number of recent messages
     *
     * @param count messages count
     *
     * @return the found records
     *
     * @throws RuntimeException
     */
    QueryResultSet<HistoryRecord> findLast(int count)
            throws RuntimeException;

    /**
     * Returns the supplied number of recent messages containing all <code>keywords</code>.
     *
     * @param count messages count
     * @param keywords array of keywords we search for
     * @param field the field where to look for the keyword
     * @param caseSensitive is keywords search case sensitive
     *
     * @return the found records
     *
     * @throws RuntimeException
     */
    QueryResultSet<HistoryRecord> findLast(int count, String[] keywords, String field, boolean
            caseSensitive)
            throws RuntimeException;

    /**
     * Returns the supplied number of recent messages after the given date
     *
     * @param date messages after date
     * @param count messages count
     *
     * @return QueryResultSet the found records
     *
     * @throws RuntimeException
     */
    QueryResultSet<HistoryRecord> findFirstRecordsAfter(Date date, int count)
            throws RuntimeException;

    /**
     * Returns the supplied number of recent messages before the given date
     *
     * @param date messages before date
     * @param count messages count
     *
     * @return QueryResultSet the found records
     *
     * @throws RuntimeException
     */
    QueryResultSet<HistoryRecord> findLastRecordsBefore(Date date, int count)
            throws RuntimeException;

    /**
     * Adding progress listener for monitoring progress of search process
     *
     * @param listener HistorySearchProgressListener
     */
    void addSearchProgressListener(HistorySearchProgressListener listener);

    /**
     * Removing progress listener
     *
     * @param listener HistorySearchProgressListener
     */
    void removeSearchProgressListener(HistorySearchProgressListener listener);

    /**
     * Total count of records that current history reader will read through
     *
     * @return the number of searched messages
     *
     * @throws UnsupportedOperationException Thrown if an exception occurs during the execution of the query,
     * such as internal IO error.
     */
    int countRecords()
            throws UnsupportedOperationException;
}
