/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package net.java.sip.communicator.impl.history;

import net.java.sip.communicator.service.history.*;
import net.java.sip.communicator.service.history.records.HistoryRecordStructure;

import org.atalk.android.plugin.timberlog.TimberLog;
import org.atalk.util.xml.XMLUtils;
import org.w3c.dom.Document;

import java.io.*;
import java.security.InvalidParameterException;
import java.util.*;

import timber.log.Timber;

/**
 * @author Alexander Pelov
 * @author Yana Stamcheva
 * @author Eng Chong Meng
 */
public class HistoryImpl implements History
{
    /**
     * The supported filetype.
     */
    public static final String SUPPORTED_FILETYPE = "xml";

    private HistoryID id;

    private HistoryRecordStructure historyRecordStructure;

    private HistoryServiceImpl historyServiceImpl;

    private File directory;

    private HistoryReader reader;

    /**
     * The <tt>InteractiveHistoryReader</tt>.
     */
    private InteractiveHistoryReader interactiveReader;

    private HistoryWriter writer;

    private SortedMap<String, Object> historyDocuments = new TreeMap<>();

    /**
     * Creates an instance of <tt>HistoryImpl</tt> by specifying the history identifier, the directory, the
     * <tt>HistoryRecordStructure</tt> to use and the parent <tt>HistoryServiceImpl</tt>.
     *
     * @param id the identifier
     * @param directory the directory
     * @param historyRecordStructure the structure
     * @param historyServiceImpl the parent history service
     */
    protected HistoryImpl(HistoryID id, File directory, HistoryRecordStructure historyRecordStructure, HistoryServiceImpl historyServiceImpl)
    {
        // TODO: Assert: Assert.assertNonNull(historyServiceImpl, "The
        // historyServiceImpl should be non-null.");
        // TODO: Assert: Assert.assertNonNull(id, "The ID should be
        // non-null.");
        // TODO: Assert: Assert.assertNonNull(historyRecordStructure, "The
        // structure should be non-null.");

        this.id = id;
        this.directory = directory;
        this.historyServiceImpl = historyServiceImpl;
        this.historyRecordStructure = historyRecordStructure;
        this.reader = null;
        this.writer = null;

        this.reloadDocumentList();
    }

    /**
     * Returns the identifier of this history.
     *
     * @return the identifier of this history
     */
    public HistoryID getID()
    {
        return this.id;
    }

    /**
     * Returns the current <tt>HistoryRecordStructure</tt>.
     *
     * @return the current <tt>HistoryRecordStructure</tt>
     */
    public HistoryRecordStructure getHistoryRecordsStructure()
    {
        return this.historyRecordStructure;
    }

    /**
     * Sets the given <tt>structure</tt> to be the new history records structure used in this history implementation.
     *
     * @param structure the new <tt>HistoryRecordStructure</tt> to use
     */
    public void setHistoryRecordsStructure(HistoryRecordStructure structure)
    {
        this.historyRecordStructure = structure;

        try {
            File dbDatFile = new File(directory, HistoryServiceImpl.DATA_FILE);
            DBStructSerializer dbss = new DBStructSerializer(historyServiceImpl);
            dbss.writeHistory(dbDatFile, this);
        } catch (IOException e) {
            Timber.d("Could not create new history structure");
        }
    }

    public HistoryReader getReader()
    {
        if (this.reader == null) {
            this.reader = new HistoryReaderImpl(this);
        }
        return this.reader;
    }

    /**
     * Returns an object that can be used to read and query this history. The <tt>InteractiveHistoryReader</tt> differs
     * from the <tt>HistoryReader</tt> in the way it manages query results. It allows to cancel a search at any time and
     * to track history results through a <tt>HistoryQueryListener</tt>.
     *
     * @return an object that can be used to read and query this history
     */
    public InteractiveHistoryReader getInteractiveReader()
    {
        if (interactiveReader == null)
            interactiveReader = new InteractiveHistoryReaderImpl(this);
        return interactiveReader;
    }

    public HistoryWriter getWriter()
    {
        if (writer == null)
            writer = new HistoryWriterImpl(this);
        return writer;
    }

    protected HistoryServiceImpl getHistoryServiceImpl()
    {
        return this.historyServiceImpl;
    }

    void reloadDocumentList()
    {
        synchronized (this.historyDocuments) {
            this.historyDocuments.clear();

            File[] files = this.directory.listFiles();
            // TODO: Assert: Assert.assertNonNull(files, "The list of files
            // should be non-null.");

            for (File file : files) {
                if (!file.isDirectory()) {
                    String filename = file.getName();

                    if (filename.endsWith(SUPPORTED_FILETYPE)) {
                        this.historyDocuments.put(filename, file);
                    }
                }
            }
        }
    }

    protected Document createDocument(String filename)
    {
        Document retVal = null;

        synchronized (this.historyDocuments) {
            if (this.historyDocuments.containsKey(filename)) {
                retVal = getDocumentForFile(filename);
            }
            else {
                retVal = this.historyServiceImpl.getDocumentBuilder().newDocument();
                retVal.appendChild(retVal.createElement("history"));

                this.historyDocuments.put(filename, retVal);
            }
        }

        return retVal;
    }

    protected void writeFile(String filename)
            throws InvalidParameterException, IOException
    {
        File file = new File(this.directory, filename);

        synchronized (this.historyDocuments) {
            if (!this.historyDocuments.containsKey(filename)) {
                throw new InvalidParameterException("The requested filename does not exist in the document list.");
            }

            Object obj = this.historyDocuments.get(filename);

            if (obj instanceof Document) {
                Document doc = (Document) obj;

                synchronized (doc) {
                    XMLUtils.writeXML(doc, file);
                }
            }
        }
    }

    protected void writeFile(String filename, Document doc)
            throws InvalidParameterException, IOException
    {
        File file = new File(this.directory, filename);

        synchronized (this.historyDocuments) {
            if (!this.historyDocuments.containsKey(filename)) {
                throw new InvalidParameterException("The requested filename does not exist in the document list.");
            }

            synchronized (doc) {
                XMLUtils.writeXML(doc, file);
            }
        }
    }

    protected Iterator<String> getFileList()
    {
        return this.historyDocuments.keySet().iterator();
    }

    protected Document getDocumentForFile(String filename)
            throws InvalidParameterException, RuntimeException
    {
        Document retVal = null;

        synchronized (this.historyDocuments) {
            if (!this.historyDocuments.containsKey(filename)) {
                throw new InvalidParameterException("The requested filename does not exist in the document list.");
            }

            Object obj = this.historyDocuments.get(filename);
            if (obj instanceof Document) {
                // Document already loaded. Use it directly
                retVal = (Document) obj;
            }
            else if (obj instanceof File) {
                File file = (File) obj;

                try {
                    retVal = this.historyServiceImpl.parse(file);
                } catch (Exception e) {
                    // throw new RuntimeException("Error occurred while "
                    // + "parsing XML document.", e);
                    // log.error("Error occurred while parsing XML document.", e);
                    Timber.e(e, "Error occurred while parsing XML document.");

                    // will try to fix the xml file
                    retVal = getFixedDocument(file);

                    // if is not fixed return
                    if (retVal == null)
                        return null;
                }

                // Cache the loaded document for reuse if configured
                if (historyServiceImpl.isCacheEnabled())
                    this.historyDocuments.put(filename, retVal);
            }
            else {
                // TODO: Assert: Assert.fail("Internal error - the data type " +
                // "should be either Document or File.");
            }
        }

        return retVal;
    }

    /**
     * Methods trying to fix histry xml files if corrupted
     * Returns the fixed document as xml Document if file cannot be fixed return null
     *
     * @param file File the file trying to fix
     * @return Document the fixed doc
     */
    public Document getFixedDocument(File file)
    {
        Timber.i("Will try to fix file : %s", file);
        StringBuilder resultDocStr = new StringBuilder("<history>");

        try {
            BufferedReader inReader = new BufferedReader(new FileReader(file));
            String line;
            while ((line = inReader.readLine()) != null) {
                // find the next start of record node
                if (!line.contains("<record")) {
                    continue;
                }

                String record = getRecordNodeString(line, inReader).toString();
                if (isValidXML(record)) {
                    resultDocStr.append(record);
                }
            }
        } catch (Exception ex1) {
            Timber.e("File cannot be fixed. Erro reading! %s", ex1.getLocalizedMessage());
        }

        resultDocStr.append("</history>");

        try {
            Document result = this.historyServiceImpl.parse(new ByteArrayInputStream(resultDocStr.toString().getBytes("UTF-8")));

            // parsing is ok . lets overwrite with correct values
            Timber.log(TimberLog.FINER, "File fixed will write to disk!");
            XMLUtils.writeXML(result, file);

            return result;
        } catch (Exception ex) {
            System.out.println("again cannot parse " + ex.getMessage());
            return null;
        }
    }

    /**
     * Returns the string containing the record node from the xml - the supplied Reader
     *
     * @param startingLine String
     * @param inReader BufferedReader
     * @return StringBuffer
     */
    private StringBuffer getRecordNodeString(String startingLine, BufferedReader inReader)
    {
        try {
            StringBuffer result = new StringBuffer(startingLine);

            String line;
            while ((line = inReader.readLine()) != null) {
                // find the next start of record node
                if (line.contains("</record>")) {
                    result.append(line);
                    break;
                }
                result.append(line);
            }
            return result;
        } catch (IOException ex) {
            Timber.w("Error reading record %s", ex.getLocalizedMessage());
            return null;
        }
    }

    /**
     * Checks whether the given xml is valid
     *
     * @param str String
     * @return boolean
     */
    private boolean isValidXML(String str)
    {
        try {
            this.historyServiceImpl.parse(new ByteArrayInputStream(str.getBytes("UTF-8")));
        } catch (Exception ex) {
            Timber.e("not valid xml %s: %s",  str, ex.getMessage());
            return false;
        }

        return true;
    }
}
