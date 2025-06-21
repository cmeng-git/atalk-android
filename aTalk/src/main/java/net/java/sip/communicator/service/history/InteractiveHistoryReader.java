/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package net.java.sip.communicator.service.history;

/**
 * The <code>InteractiveHistoryReader</code> allows to search in the history in an interactive way, i.e. be able to cancel
 * the search at any time and track the results through a <code>HistoryQueryListener</code>.
 *
 * @author Yana Stamcheva
 */
public interface InteractiveHistoryReader {
    /**
     * Searches the history for all records containing all <code>keywords</code>.
     *
     * @param keywords array of keywords we search for
     * @param field the field where to look for the keyword
     * @param recordCount limits the result to this record count
     *
     * @return a <code>HistoryQuery</code> object allowing to track this query
     *
     * @throws RuntimeException Thrown if an exception occurs during the execution of the query, such as internal IO error.
     */
    HistoryQuery findByKeywords(String[] keywords, String field, int recordCount);

    /**
     * Searches the history for all records containing the <code>keyword</code>.
     *
     * @param keyword the keyword to search for
     * @param field the field where to look for the keyword
     * @param recordCount limits the result to this record count
     *
     * @return a <code>HistoryQuery</code> object allowing to track this query
     *
     * @throws RuntimeException Thrown if an exception occurs during the execution of the query, such as internal IO error.
     */
    HistoryQuery findByKeyword(String keyword, String field, int recordCount);
}
