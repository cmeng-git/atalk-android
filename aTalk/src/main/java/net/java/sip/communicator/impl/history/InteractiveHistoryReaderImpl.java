/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package net.java.sip.communicator.impl.history;

import static net.java.sip.communicator.service.history.HistoryService.DATE_FORMAT;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Iterator;
import java.util.Locale;
import java.util.Vector;

import net.java.sip.communicator.service.history.HistoryQuery;
import net.java.sip.communicator.service.history.InteractiveHistoryReader;
import net.java.sip.communicator.service.history.event.HistoryQueryStatusEvent;
import net.java.sip.communicator.service.history.records.HistoryRecord;

import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

/**
 * The <code>InteractiveHistoryReaderImpl</code> is an implementation of the
 * <code>InteractiveHistoryReader</code> interface. It allows to search in the history in an
 * interactive way, i.e. be able to cancel the search at any time and track the results through a
 * <code>HistoryQueryListener</code>.
 *
 * @author Yana Stamcheva
 * @author Eng Chong Meng
 */
public class InteractiveHistoryReaderImpl implements InteractiveHistoryReader {
    /**
     * The <code>HistoryImpl</code> where this reader is registered.
     */
    private final HistoryImpl history;

    /**
     * Creates an instance of <code>InteractiveHistoryReaderImpl</code> by specifying the
     * corresponding <code>history</code> implementation.
     *
     * @param history the corresponding <code>HistoryImpl</code> to read from
     */
    public InteractiveHistoryReaderImpl(HistoryImpl history) {
        this.history = history;
    }

    /**
     * Searches the history for all records containing the <code>keyword</code>.
     *
     * @param keyword the keyword to search for
     * @param field the field where to look for the keyword
     * @param recordCount limits the result to this record count
     *
     * @return the found records
     */
    public HistoryQuery findByKeyword(String keyword, String field, int recordCount) {
        return findByKeywords(new String[]{keyword}, field, recordCount);
    }

    /**
     * Searches the history for all records containing all <code>keywords</code>.
     *
     * @param keywords array of keywords we search for
     * @param field the field where to look for the keyword
     * @param recordCount limits the result to this record count
     *
     * @return the found records
     */
    public HistoryQuery findByKeywords(String[] keywords, String field, int recordCount) {
        return find(null, null, keywords, field, false, recordCount);
    }

    /**
     * Finds the history results corresponding to the given criteria.
     *
     * @param startDate the start date
     * @param endDate the end date
     * @param keywords an array of keywords to search for
     * @param field the field, where to search the keywords
     * @param caseSensitive indicates if the search should be case sensitive
     * @param resultCount the desired number of results
     *
     * @return the <code>HistoryQuery</code> that could be used to track the results or to cancel the
     * search
     */
    private HistoryQuery find(final Date startDate, final Date endDate, final String[] keywords,
            final String field, final boolean caseSensitive, final int resultCount) {
        StringBuilder queryString = new StringBuilder();
        for (String s : keywords) {
            queryString.append(' ');
            queryString.append(s);
        }

        final HistoryQueryImpl query = new HistoryQueryImpl(queryString.toString());
        new Thread() {
            @Override
            public void run() {
                find(startDate, endDate, keywords, field, caseSensitive, resultCount, query);
            }
        }.start();

        return query;
    }

    /**
     * Finds the history results corresponding to the given criteria.
     *
     * @param startDate the start date
     * @param endDate the end date
     * @param keywords an array of keywords to search for
     * @param field the field, where to search the keywords
     * @param caseSensitive indicates if the search should be case sensitive
     * @param resultCount the desired number of results
     * @param query the query tracking the results
     */
    private void find(Date startDate, Date endDate, String[] keywords, String field,
            boolean caseSensitive, int resultCount, HistoryQueryImpl query) {
        Vector<String> fileList = HistoryReaderImpl.filterFilesByDate(history.getFileList(),
                startDate, endDate, true);
        Iterator<String> fileIterator = fileList.iterator();

        SimpleDateFormat sdf = new SimpleDateFormat(DATE_FORMAT, Locale.US);
        while (fileIterator.hasNext() && resultCount > 0 && !query.isCanceled()) {
            String filename = fileIterator.next();
            Document doc = history.getDocumentForFile(filename);

            if (doc == null)
                continue;

            NodeList nodes = doc.getElementsByTagName("record");

            for (int i = nodes.getLength() - 1; i >= 0 && !query.isCanceled(); i--) {
                Node node = nodes.item(i);
                Date timestamp;
                String ts = node.getAttributes().getNamedItem("timestamp").getNodeValue();
                try {
                    timestamp = sdf.parse(ts);
                } catch (ParseException e) {
                    timestamp = new Date(Long.parseLong(ts));
                }

                if (HistoryReaderImpl.isInPeriod(timestamp, startDate, endDate)) {
                    NodeList propertyNodes = node.getChildNodes();

                    HistoryRecord record = HistoryReaderImpl.filterByKeyword(propertyNodes,
                            timestamp, keywords, field, caseSensitive);

                    if (record != null) {
                        query.addHistoryRecord(record);
                        resultCount--;
                    }
                }
            }
        }

        if (query.isCanceled())
            query.setStatus(HistoryQueryStatusEvent.QUERY_CANCELED);
        else
            query.setStatus(HistoryQueryStatusEvent.QUERY_COMPLETED);
    }
}
