/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package net.java.sip.communicator.impl.history;

import static net.java.sip.communicator.service.history.HistoryService.DATE_FORMAT;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.Vector;
import java.util.regex.Pattern;

import net.java.sip.communicator.service.history.HistoryReader;
import net.java.sip.communicator.service.history.QueryResultSet;
import net.java.sip.communicator.service.history.event.HistorySearchProgressListener;
import net.java.sip.communicator.service.history.event.ProgressEvent;
import net.java.sip.communicator.service.history.records.HistoryRecord;

import org.apache.commons.text.StringEscapeUtils;
import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

/**
 * @author Alexander Pelov
 * @author Damian Minkov
 * @author Yana Stamcheva
 */
public class HistoryReaderImpl implements HistoryReader {
    private final HistoryImpl historyImpl;
    private final Vector<HistorySearchProgressListener> progressListeners = new Vector<>();

    // regexp used for index of case(in)sensitive impl
    private static String REGEXP_END = ".*$";
    private static String REGEXP_SENSITIVE_START = "(?s)^.*";
    private static String REGEXP_INSENSITIVE_START = "(?si)^.*";

    /**
     * Creates an instance of <code>HistoryReaderImpl</code>.
     *
     * @param historyImpl the parent History implementation
     */
    protected HistoryReaderImpl(HistoryImpl historyImpl) {
        this.historyImpl = historyImpl;
    }

    /**
     * Searches the history for all records with timestamp after <code>startDate</code>.
     *
     * @param startDate the date after all records will be returned
     *
     * @return the found records
     *
     * @throws RuntimeException Thrown if an exception occurs during the execution of the query, such as internal IO error.
     */
    public synchronized QueryResultSet<HistoryRecord> findByStartDate(Date startDate)
            throws RuntimeException {
        return find(startDate, null, null, null, false);
    }

    /**
     * Searches the history for all records with timestamp before <code>endDate</code>.
     *
     * @param endDate the date before which all records will be returned
     *
     * @return the found records
     *
     * @throws RuntimeException Thrown if an exception occurs during the execution of the query, such as internal IO error.
     */
    public synchronized QueryResultSet<HistoryRecord> findByEndDate(Date endDate)
            throws RuntimeException {
        return find(null, endDate, null, null, false);
    }

    /**
     * Searches the history for all records with timestamp between <code>startDate</code> and <code>endDate</code>.
     *
     * @param startDate start of the interval in which we search
     * @param endDate end of the interval in which we search
     *
     * @return the found records
     *
     * @throws RuntimeException Thrown if an exception occurs during the execution of the query, such as internal IO error.
     */
    public synchronized QueryResultSet<HistoryRecord> findByPeriod(Date startDate, Date endDate)
            throws RuntimeException {
        return find(startDate, endDate, null, null, false);
    }

    /**
     * Searches the history for all records containing the <code>keyword</code>.
     *
     * @param keyword the keyword to search for
     * @param field the field where to look for the keyword
     *
     * @return the found records
     *
     * @throws RuntimeException Thrown if an exception occurs during the execution of the query, such as internal IO error.
     */
    public synchronized QueryResultSet<HistoryRecord> findByKeyword(String keyword, String field)
            throws RuntimeException {
        return findByKeywords(new String[]{keyword}, field);
    }

    /**
     * Searches the history for all records containing all <code>keywords</code>.
     *
     * @param keywords array of keywords we search for
     * @param field the field where to look for the keyword
     *
     * @return the found records
     *
     * @throws RuntimeException Thrown if an exception occurs during the execution of the query, such as internal IO error.
     */
    public synchronized QueryResultSet<HistoryRecord> findByKeywords(String[] keywords, String field)
            throws RuntimeException {
        return find(null, null, keywords, field, false);
    }

    /**
     * Searches for all history records containing all <code>keywords</code>, with timestamp between <code>startDate</code> and
     * <code>endDate</code>.
     *
     * @param startDate start of the interval in which we search
     * @param endDate end of the interval in which we search
     * @param keywords array of keywords we search for
     * @param field the field where to look for the keyword
     *
     * @return the found records
     *
     * @throws UnsupportedOperationException Thrown if an exception occurs during the execution of the query, such as internal IO error.
     */
    public synchronized QueryResultSet<HistoryRecord> findByPeriod(Date startDate, Date endDate, String[] keywords, String field)
            throws UnsupportedOperationException {
        return find(startDate, endDate, keywords, field, false);
    }

    /**
     * Returns the last <code>count</code> messages. No progress firing as this method is supposed to be used in message
     * windows and is supposed to be as quick as it can.
     *
     * @param count int
     *
     * @return QueryResultSet
     *
     * @throws RuntimeException
     */
    public synchronized QueryResultSet<HistoryRecord> findLast(int count)
            throws RuntimeException {
        return findLast(count, null, null, false);
    }

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
    public synchronized QueryResultSet<HistoryRecord> findLast(int count, String[] keywords,
            String field, boolean caseSensitive)
            throws RuntimeException {
        // the files are supposed to be ordered from oldest to newest
        Vector<String> filelist = filterFilesByDate(this.historyImpl.getFileList(), null, null);

        TreeSet<HistoryRecord> result = new TreeSet<>(new HistoryRecordComparator());
        int leftCount = count;
        int currentFile = filelist.size() - 1;

        SimpleDateFormat sdf = new SimpleDateFormat(DATE_FORMAT, Locale.US);
        while ((leftCount > 0) && (currentFile >= 0)) {
            Document doc = this.historyImpl.getDocumentForFile(filelist.get(currentFile));

            if (doc == null) {
                currentFile--;
                continue;
            }

            // will get nodes and construct a List of nodes so we can easily get sublist of it
            List<Node> nodes = new ArrayList<>();
            NodeList nodesList = doc.getElementsByTagName("record");
            for (int i = 0; i < nodesList.getLength(); i++) {
                nodes.add(nodesList.item(i));
            }

            List<Node> lNodes = null;

            if (nodes.size() > leftCount) {
                lNodes = nodes.subList(nodes.size() - leftCount, nodes.size());
                leftCount = 0;
            }
            else {
                lNodes = nodes;
                leftCount -= nodes.size();
            }

            Iterator<Node> i = lNodes.iterator();
            while (i.hasNext()) {
                Node node = i.next();

                NodeList propertyNodes = node.getChildNodes();

                Date timestamp;
                String ts = node.getAttributes().getNamedItem("timestamp").getNodeValue();
                try {
                    timestamp = sdf.parse(ts);
                } catch (ParseException e) {
                    timestamp = new Date(Long.parseLong(ts));
                }

                HistoryRecord record = filterByKeyword(propertyNodes, timestamp, keywords, field, caseSensitive);

                if (record != null) {
                    result.add(record);
                }
            }
            currentFile--;
        }
        return new OrderedQueryResultSet<>(result);
    }

    /**
     * Searches the history for all records containing the <code>keyword</code>.
     *
     * @param keyword the keyword to search for
     * @param field the field where to look for the keyword
     * @param caseSensitive is keywords search case sensitive
     *
     * @return the found records
     *
     * @throws RuntimeException Thrown if an exception occurs during the execution of the query, such as internal IO error.
     */
    public synchronized QueryResultSet<HistoryRecord> findByKeyword(String keyword, String field, boolean caseSensitive)
            throws RuntimeException {
        return findByKeywords(new String[]{keyword}, field, caseSensitive);
    }

    /**
     * Searches the history for all records containing all <code>keywords</code>.
     *
     * @param keywords array of keywords we search for
     * @param field the field where to look for the keyword
     * @param caseSensitive is keywords search case sensitive
     *
     * @return the found records
     *
     * @throws RuntimeException Thrown if an exception occurs during the execution of the query, such as internal IO error.
     */
    public synchronized QueryResultSet<HistoryRecord> findByKeywords(String[] keywords, String field, boolean caseSensitive)
            throws RuntimeException {
        return find(null, null, keywords, field, caseSensitive);
    }

    /**
     * Searches for all history records containing all <code>keywords</code>, with timestamp between <code>startDate</code> and
     * <code>endDate</code>.
     *
     * @param startDate start of the interval in which we search
     * @param endDate end of the interval in which we search
     * @param keywords array of keywords we search for
     * @param field the field where to look for the keyword
     * @param caseSensitive is keywords search case sensitive
     *
     * @return the found records
     *
     * @throws UnsupportedOperationException Thrown if an exception occurs during the execution of the query, such as internal IO error.
     */
    public synchronized QueryResultSet<HistoryRecord> findByPeriod(Date startDate, Date endDate, String[] keywords, String field, boolean caseSensitive)
            throws UnsupportedOperationException {
        return find(startDate, endDate, keywords, field, caseSensitive);
    }

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
    public QueryResultSet<HistoryRecord> findFirstRecordsAfter(Date date, int count)
            throws RuntimeException {
        TreeSet<HistoryRecord> result = new TreeSet<>(new HistoryRecordComparator());

        Vector<String> filelist = filterFilesByDate(this.historyImpl.getFileList(), date, null);

        int leftCount = count;
        int currentFile = 0;

        SimpleDateFormat sdf = new SimpleDateFormat(DATE_FORMAT, Locale.US);
        while (leftCount > 0 && currentFile < filelist.size()) {
            Document doc = this.historyImpl.getDocumentForFile(filelist.get(currentFile));

            if (doc == null) {
                currentFile++;
                continue;
            }

            NodeList nodes = doc.getElementsByTagName("record");

            Node node;
            for (int i = 0; i < nodes.getLength() && leftCount > 0; i++) {
                node = nodes.item(i);

                NodeList propertyNodes = node.getChildNodes();

                Date timestamp;
                String ts = node.getAttributes().getNamedItem("timestamp").getNodeValue();
                try {
                    timestamp = sdf.parse(ts);
                } catch (ParseException e) {
                    timestamp = new Date(Long.parseLong(ts));
                }

                if (!isInPeriod(timestamp, date, null))
                    continue;

                ArrayList<String> nameVals = new ArrayList<>();

                boolean isRecordOK = true;
                int len = propertyNodes.getLength();
                for (int j = 0; j < len; j++) {
                    Node propertyNode = propertyNodes.item(j);
                    if (propertyNode.getNodeType() == Node.ELEMENT_NODE) {
                        // Get nested TEXT node's value
                        Node nodeValue = propertyNode.getFirstChild();

                        if (nodeValue != null) {
                            nameVals.add(propertyNode.getNodeName());
                            nameVals.add(nodeValue.getNodeValue());
                        }
                        else
                            isRecordOK = false;
                    }
                }

                // if we found a broken record - just skip it
                if (!isRecordOK)
                    continue;

                String[] propertyNames = new String[nameVals.size() / 2];
                String[] propertyValues = new String[propertyNames.length];
                for (int j = 0; j < propertyNames.length; j++) {
                    propertyNames[j] = nameVals.get(j * 2);
                    propertyValues[j] = nameVals.get(j * 2 + 1);
                }

                HistoryRecord record = new HistoryRecord(propertyNames, propertyValues, timestamp);

                result.add(record);
                leftCount--;
            }

            currentFile++;
        }

        return new OrderedQueryResultSet<>(result);
    }

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
    public QueryResultSet<HistoryRecord> findLastRecordsBefore(Date date, int count)
            throws RuntimeException {
        // the files are supposed to be ordered from oldest to newest
        Vector<String> filelist = filterFilesByDate(this.historyImpl.getFileList(), null, date);

        TreeSet<HistoryRecord> result = new TreeSet<>(new HistoryRecordComparator());
        int leftCount = count;

        int currentFile = filelist.size() - 1;

        SimpleDateFormat sdf = new SimpleDateFormat(DATE_FORMAT, Locale.US);
        while (leftCount > 0 && currentFile >= 0) {
            Document doc = this.historyImpl.getDocumentForFile(filelist.get(currentFile));

            if (doc == null) {
                currentFile--;
            }
            else {

                NodeList nodes = doc.getElementsByTagName("record");

                Node node;
                for (int i = nodes.getLength() - 1; i >= 0 && leftCount > 0; i--) {
                    node = nodes.item(i);
                    NodeList propertyNodes = node.getChildNodes();

                    Date timestamp;
                    String ts = node.getAttributes().getNamedItem("timestamp").getNodeValue();
                    try {
                        timestamp = sdf.parse(ts);
                    } catch (ParseException e) {
                        timestamp = new Date(Long.parseLong(ts));
                    }

                    if (isInPeriod(timestamp, null, date)) {

                        ArrayList<String> nameVals = new ArrayList<>();

                        boolean isRecordOK = true;
                        int len = propertyNodes.getLength();
                        for (int j = 0; j < len; j++) {
                            Node propertyNode = propertyNodes.item(j);
                            if (propertyNode.getNodeType() == Node.ELEMENT_NODE) {
                                // Get nested TEXT node's value
                                Node nodeValue = propertyNode.getFirstChild();

                                if (nodeValue != null) {
                                    nameVals.add(propertyNode.getNodeName());
                                    nameVals.add(nodeValue.getNodeValue());
                                }
                                else {
                                    isRecordOK = false;
                                }
                            }
                        }

                        // if we found a broken record - just skip it
                        if (isRecordOK) {
                            String[] propertyNames = new String[nameVals.size() / 2];
                            String[] propertyValues = new String[propertyNames.length];
                            for (int j = 0; j < propertyNames.length; j++) {
                                propertyNames[j] = nameVals.get(j * 2);
                                propertyValues[j] = nameVals.get(j * 2 + 1);
                            }

                            HistoryRecord record = new HistoryRecord(propertyNames, propertyValues, timestamp);
                            result.add(record);
                            leftCount--;
                        }
                    }
                }
                currentFile--;
            }
        }
        return new OrderedQueryResultSet<>(result);
    }

    private QueryResultSet<HistoryRecord> find(Date startDate, Date endDate, String[] keywords, String field, boolean caseSensitive) {
        TreeSet<HistoryRecord> result = new TreeSet<>(new HistoryRecordComparator());

        Vector<String> filelist = filterFilesByDate(this.historyImpl.getFileList(), startDate, endDate);

        double currentProgress = HistorySearchProgressListener.PROGRESS_MINIMUM_VALUE;
        double fileProgressStep = HistorySearchProgressListener.PROGRESS_MAXIMUM_VALUE;

        if (filelist.size() != 0)
            fileProgressStep = HistorySearchProgressListener.PROGRESS_MAXIMUM_VALUE / filelist.size();

        // start progress - minimum value
        fireProgressStateChanged(startDate, endDate, keywords, HistorySearchProgressListener.PROGRESS_MINIMUM_VALUE);

        SimpleDateFormat sdf = new SimpleDateFormat(DATE_FORMAT, Locale.US);
        for (String filename : filelist) {
            Document doc = this.historyImpl.getDocumentForFile(filename);
            if (doc == null)
                continue;

            NodeList nodes = doc.getElementsByTagName("record");
            double nodesProgressStep = fileProgressStep;

            if (nodes.getLength() != 0)
                nodesProgressStep = fileProgressStep / nodes.getLength();

            Node node;
            for (int i = 0; i < nodes.getLength(); i++) {
                node = nodes.item(i);
                Date timestamp;
                String ts = node.getAttributes().getNamedItem("timestamp").getNodeValue();
                try {
                    timestamp = sdf.parse(ts);
                } catch (ParseException e) {
                    timestamp = new Date(Long.parseLong(ts));
                }

                if (isInPeriod(timestamp, startDate, endDate)) {
                    NodeList propertyNodes = node.getChildNodes();

                    HistoryRecord record = filterByKeyword(propertyNodes, timestamp, keywords, field, caseSensitive);
                    if (record != null) {
                        result.add(record);
                    }
                }
                currentProgress += nodesProgressStep;
                fireProgressStateChanged(startDate, endDate, keywords, (int) currentProgress);
            }
        }

        // if maximum value is not reached fire an event
        if ((int) currentProgress < HistorySearchProgressListener.PROGRESS_MAXIMUM_VALUE) {
            fireProgressStateChanged(startDate, endDate, keywords, HistorySearchProgressListener.PROGRESS_MAXIMUM_VALUE);
        }
        return new OrderedQueryResultSet<>(result);
    }

    /**
     * Evaluetes does <code>timestamp</code> is in the given time period.
     *
     * @param timestamp Date
     * @param startDate Date the start of the period
     * @param endDate Date the end of the period
     *
     * @return boolean
     */
    static boolean isInPeriod(Date timestamp, Date startDate, Date endDate) {
        Long startLong;
        Long endLong;
        Long tsLong = timestamp.getTime();

        if (startDate == null)
            startLong = Long.MIN_VALUE;
        else
            startLong = startDate.getTime();

        if (endDate == null)
            endLong = Long.MAX_VALUE;
        else
            endLong = endDate.getTime();

        return startLong <= tsLong && tsLong < endLong;
    }

    /**
     * If there is keyword restriction and doesn't match the conditions return null. Otherwise return the HistoryRecord
     * corresponding the given nodes.
     *
     * @param propertyNodes NodeList
     * @param timestamp Date
     * @param keywords String[]
     * @param field String
     * @param caseSensitive boolean
     *
     * @return HistoryRecord
     */
    static HistoryRecord filterByKeyword(NodeList propertyNodes, Date timestamp, String[] keywords, String field, boolean caseSensitive) {
        ArrayList<String> nameVals = new ArrayList<>();
        int len = propertyNodes.getLength();
        boolean targetNodeFound = false;
        for (int j = 0; j < len; j++) {
            Node propertyNode = propertyNodes.item(j);
            if (propertyNode.getNodeType() == Node.ELEMENT_NODE) {
                String nodeName = propertyNode.getNodeName();
                Node nestedNode = propertyNode.getFirstChild();
                if (nestedNode != null) {

                    // Get nested TEXT node's value
                    String nodeValue = nestedNode.getNodeValue();

                    // unescape xml chars, we have escaped when writing values
                    nodeValue = StringEscapeUtils.unescapeXml(nodeValue);

                    if (field != null && field.equals(nodeName)) {
                        targetNodeFound = true;
                        if (!matchKeyword(nodeValue, keywords, caseSensitive))
                            // doesn't match the given keyword(s) so return nothing
                            return null;
                    }

                    nameVals.add(nodeName);
                    // Get nested TEXT node's value
                    nameVals.add(nodeValue);
                }
            }
        }

        // if we need to find a particular record but the target node is not present skip this record
        if (keywords != null && keywords.length > 0 && !targetNodeFound) {
            return null;
        }

        String[] propertyNames = new String[nameVals.size() / 2];
        String[] propertyValues = new String[propertyNames.length];
        for (int j = 0; j < propertyNames.length; j++) {
            propertyNames[j] = nameVals.get(j * 2);
            propertyValues[j] = nameVals.get(j * 2 + 1);
        }

        return new HistoryRecord(propertyNames, propertyValues, timestamp);
    }

    /**
     * Check if a value is in the given keyword(s) If no keyword(s) given must return true
     *
     * @param value String
     * @param keywords String[]
     * @param caseSensitive boolean
     *
     * @return boolean
     */
    static boolean matchKeyword(String value, String[] keywords, boolean caseSensitive) {
        if (keywords != null) {
            String regexpStart = null;
            if (caseSensitive)
                regexpStart = REGEXP_SENSITIVE_START;
            else
                regexpStart = REGEXP_INSENSITIVE_START;

            for (int i = 0; i < keywords.length; i++) {
                if (!value.matches(regexpStart + Pattern.quote(keywords[i]) + REGEXP_END))
                    return false;
            }

            // all keywords match return true
            return true;
        }

        // if no keyword or keywords given we must not filter this record so will return true
        return true;
    }

    /**
     * Used to limit the files if any starting or ending date exist So only few files to be searched.
     *
     * @param filelist Iterator
     * @param startDate Date
     * @param endDate Date
     *
     * @return Iterator
     */
    static Vector<String> filterFilesByDate(Iterator<String> filelist, Date startDate, Date endDate) {
        return filterFilesByDate(filelist, startDate, endDate, false);
    }

    /**
     * Used to limit the files if any starting or ending date exist So only few files to be searched.
     *
     * @param filelist Iterator
     * @param startDate Date
     * @param endDate Date
     * @param reverseOrder reverse order of files
     *
     * @return Vector
     */
    static Vector<String> filterFilesByDate(Iterator<String> filelist, Date startDate, Date endDate, final boolean reverseOrder) {
        if (startDate == null && endDate == null) {
            // no filtering needed then just return the same list
            Vector<String> result = new Vector<>();
            while (filelist.hasNext()) {
                result.add(filelist.next());
            }

            Collections.sort(result, (o1, o2) -> {
                if (reverseOrder)
                    return o2.compareTo(o1);
                else
                    return o1.compareTo(o2);
            });

            return result;
        }
        // first convert all files to long
        TreeSet<Long> files = new TreeSet<>();
        while (filelist.hasNext()) {
            String filename = filelist.next();

            files.add(Long.parseLong(filename.substring(0, filename.length() - 4)));
        }

        TreeSet<Long> resultAsLong = new TreeSet<>();

        // Temporary fix of a NoSuchElementException
        if (files.isEmpty()) {
            return new Vector<>();
        }

        Long startLong;
        long endLong;

        if (startDate == null)
            startLong = Long.MIN_VALUE;
        else
            startLong = startDate.getTime();

        if (endDate == null)
            endLong = Long.MAX_VALUE;
        else
            endLong = endDate.getTime();

        // get all records inclusive the one before the startDate
        for (Long f : files) {
            if (startLong <= f && f <= endLong) {
                resultAsLong.add(f);
            }
        }

        // get the subset before the start date, to get its last element
        // if exists
        if (!files.isEmpty() && files.first() <= startLong) {
            SortedSet<Long> setBeforeTheInterval = files.subSet(files.first(), true, startLong, true);
            if (!setBeforeTheInterval.isEmpty())
                resultAsLong.add(setBeforeTheInterval.last());
        }

        Vector<String> result = new Vector<>();

        Iterator<Long> iter = resultAsLong.iterator();
        while (iter.hasNext()) {
            Long item = iter.next();
            result.add(item.toString() + ".xml");
        }

        Collections.sort(result, (o1, o2) -> {
            if (reverseOrder)
                return o2.compareTo(o1);
            else
                return o1.compareTo(o2);
        });

        return result;
    }

    private void fireProgressStateChanged(Date startDate, Date endDate, String[] keywords, int progress) {
        ProgressEvent event = new ProgressEvent(this, startDate, endDate, keywords, progress);

        synchronized (progressListeners) {
            Iterator<HistorySearchProgressListener> iter = progressListeners.iterator();
            while (iter.hasNext()) {
                HistorySearchProgressListener item = iter.next();
                item.progressChanged(event);
            }
        }
    }

    /**
     * Adding progress listener for monitoring progress of search process
     *
     * @param listener HistorySearchProgressListener
     */
    public void addSearchProgressListener(HistorySearchProgressListener listener) {
        synchronized (progressListeners) {
            progressListeners.add(listener);
        }
    }

    /**
     * Removing progress listener
     *
     * @param listener HistorySearchProgressListener
     */
    public void removeSearchProgressListener(HistorySearchProgressListener listener) {
        synchronized (progressListeners) {
            progressListeners.remove(listener);
        }
    }

    /**
     * Count the number of messages that a search will return Actually only the last file is parsed and its nodes are
     * counted. We accept that the other files are full with max records, this way we escape parsing all files which
     * will significantly slow the process and for one search will parse the files twice.
     *
     * @return the number of searched messages
     *
     * @throws UnsupportedOperationException Thrown if an exception occurs during the execution of the query, such as internal IO error.
     */
    public int countRecords()
            throws UnsupportedOperationException {
        int result = 0;
        String lastFile = null;
        Iterator<String> filelistIter = this.historyImpl.getFileList();
        while (filelistIter.hasNext()) {
            lastFile = filelistIter.next();
            result += HistoryWriterImpl.MAX_RECORDS_PER_FILE;
        }

        if (lastFile == null)
            return result;

        Document doc = this.historyImpl.getDocumentForFile(lastFile);

        if (doc == null)
            return result;

        NodeList nodes = doc.getElementsByTagName("record");

        result += nodes.getLength();

        return result;
    }

    /**
     * Used to compare HistoryRecords ant to be ordered in TreeSet
     */
    private static class HistoryRecordComparator implements Comparator<HistoryRecord> {
        public int compare(HistoryRecord h1, HistoryRecord h2) {
            return h1.getTimestamp().compareTo(h2.getTimestamp());
        }
    }
}
