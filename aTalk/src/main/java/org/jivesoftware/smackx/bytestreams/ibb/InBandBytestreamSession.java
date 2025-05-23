/**
 *
 * Copyright the original author or authors
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
package org.jivesoftware.smackx.bytestreams.ibb;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.SocketTimeoutException;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.jivesoftware.smack.SmackException.NotConnectedException;
import org.jivesoftware.smack.SmackException.NotLoggedInException;
import org.jivesoftware.smack.StanzaListener;
import org.jivesoftware.smack.XMPPConnection;
import org.jivesoftware.smack.datatypes.UInt16;
import org.jivesoftware.smack.filter.AndFilter;
import org.jivesoftware.smack.filter.StanzaFilter;
import org.jivesoftware.smack.filter.StanzaTypeFilter;
import org.jivesoftware.smack.packet.IQ;
import org.jivesoftware.smack.packet.Message;
import org.jivesoftware.smack.packet.Stanza;
import org.jivesoftware.smack.packet.StanzaBuilder;
import org.jivesoftware.smack.packet.StanzaError;
import org.jivesoftware.smack.util.stringencoder.Base64;

import org.jivesoftware.smackx.bytestreams.BytestreamSession;
import org.jivesoftware.smackx.bytestreams.ibb.packet.Close;
import org.jivesoftware.smackx.bytestreams.ibb.packet.Data;
import org.jivesoftware.smackx.bytestreams.ibb.packet.DataPacketExtension;
import org.jivesoftware.smackx.bytestreams.ibb.packet.Open;

import org.jxmpp.jid.Jid;

/**
 * InBandBytestreamSession class represents an In-Band Bytestream session.
 * <p>
 * In-band bytestreams are bidirectional and this session encapsulates the streams for both
 * directions.
 * <p>
 * Note that closing the In-Band Bytestream session will close both streams. If both streams are
 * closed individually the session will be closed automatically once the second stream is closed.
 * Use the {@link #setCloseBothStreamsEnabled(boolean)} method if both streams should be closed
 * automatically if one of them is closed.
 *
 * @author Henning Staib
 * @author Eng Chong Meng
 */
public class InBandBytestreamSession implements BytestreamSession {

    private static final Logger LOGGER = Logger.getLogger(InBandBytestreamSession.class.getName());

    static final String UNEXPECTED_IBB_SEQUENCE = "Unexpected IBB sequence";

    /* XMPP connection */
    private final XMPPConnection connection;

    /* the In-Band Bytestream open request for this session */
    private final Open byteStreamRequest;

    /*
     * the input stream for this session (either IQIBBInputStream or MessageIBBInputStream)
     */
    private IBBInputStream inputStream;

    /*
     * the output stream for this session (either IQIBBOutputStream or MessageIBBOutputStream)
     */
    private IBBOutputStream outputStream;

    /* JID of the remote peer */
    private final Jid remoteJID;

    /* flag to close both streams if one of them is closed */
    private boolean closeBothStreamsEnabled = false;

    /* flag to indicate if session is closed */
    private boolean isClosed = false;

    /* flag to indicate if session is already closed by peer */
    private volatile boolean closedByPeer = false;

    /**
     * Constructor.
     *
     * @param connection the XMPP connection
     * @param byteStreamRequest the In-Band Bytestream open request for this session
     * @param remoteJID JID of the remote peer
     */
    protected InBandBytestreamSession(XMPPConnection connection, Open byteStreamRequest, Jid remoteJID) {
        this.connection = connection;
        this.byteStreamRequest = byteStreamRequest;
        this.remoteJID = remoteJID;

        // initialize streams dependent to the uses stanza type
        switch (byteStreamRequest.getStanza()) {
        case IQ:
            this.inputStream = new IQIBBInputStream();
            this.outputStream = new IQIBBOutputStream();
            break;
        case MESSAGE:
            this.inputStream = new MessageIBBInputStream();
            this.outputStream = new MessageIBBOutputStream();
            break;
        }
    }

    @Override
    public InputStream getInputStream() {
        return this.inputStream;
    }

    @Override
    public OutputStream getOutputStream() {
        return this.outputStream;
    }

    @Override
    public int getReadTimeout() {
        return this.inputStream.readTimeout;
    }

    @Override
    public void setReadTimeout(int timeout) {
        if (timeout < 0) {
            throw new IllegalArgumentException("Timeout must be >= 0");
        }
        this.inputStream.readTimeout = timeout;
    }

    /**
     * Returns whether both streams should be closed automatically if one of the streams is closed.
     * Default is <code>false</code>.
     *
     * @return <code>true</code> if both streams will be closed if one of the streams is closed,
     *         <code>false</code> if both streams can be closed independently.
     */
    public boolean isCloseBothStreamsEnabled() {
        return closeBothStreamsEnabled;
    }

    /**
     * Sets whether both streams should be closed automatically if one of the streams is closed.
     * Default is <code>false</code>.
     *
     * @param closeBothStreamsEnabled <code>true</code> if both streams should be closed if one of
     *        the streams is closed, <code>false</code> if both streams should be closed independently
     */
    public void setCloseBothStreamsEnabled(boolean closeBothStreamsEnabled) {
        this.closeBothStreamsEnabled = closeBothStreamsEnabled;
    }

    @Override
    public void close() throws IOException {
        closeByLocal(true); // close input stream
        closeByLocal(false); // close output stream
    }

    /**
     * This method is invoked if a request to close the In-Band Bytestream has been received.
     *
     * @param closeRequest the close request from the remote peer
     * @throws NotConnectedException if the XMPP connection is not connected.
     * @throws InterruptedException if the calling thread was interrupted.
     */
    protected void closeByPeer(Close closeRequest) throws NotConnectedException, InterruptedException {

        /*
         * close streams without flushing them, because stream is already considered closed on the
         * remote peers side
         */
        this.inputStream.closeInternal();
        this.inputStream.cleanup();
        this.outputStream.closeInternal(false);
        this.closedByPeer = true;

        // acknowledge close request
        IQ confirmClose = IQ.createResultIQ(closeRequest);
        this.connection.sendStanza(confirmClose);

    }

    /**
     * This method is invoked if one of the streams has been closed locally, if an error occurred
     * locally or if the whole session should be closed.
     *
     * @param in do we want to close the Input- or OutputStream?
     * @throws IOException if an error occurs while sending the close request
     */
    protected synchronized void closeByLocal(boolean in) throws IOException {
        if (this.isClosed) {
            return;
        }

        if (this.closeBothStreamsEnabled) {
            this.inputStream.closeInternal();
            this.outputStream.closeInternal(true);
        }
        else {
            if (in) {
                this.inputStream.closeInternal();
            }
            else {
                // close stream but try to send any data left
                this.outputStream.closeInternal(true);
            }
        }

        if (this.inputStream.isClosed && this.outputStream.isClosed) {
            this.isClosed = true;

            // send close stream request if not already closed by peer
            if (!closedByPeer) {
                Close close = new Close(this.byteStreamRequest.getSessionID());
                close.setTo(this.remoteJID);
                try {
                    connection.createStanzaCollectorAndSend(close).nextResultOrThrow();
                } catch (Exception e) {
                    // Sadly we are unable to use the IOException(Throwable) constructor because this
                    // constructor is only supported from Android API 9 on.
                    IOException ioException = new IOException();
                    ioException.initCause(e);
                    throw ioException;
                }
            }

            this.inputStream.cleanup();

            // remove session from manager
            // Thanks Google Error Prone for finding the bug where remove() was called with 'this' as argument. Changed
            // now to remove(byteStreamRequest.getSessionID).
            InBandBytestreamManager.getByteStreamManager(this.connection).getSessions().remove(byteStreamRequest.getSessionID());
        }

    }

    /**
     * IBBInputStream class is the base implementation of an In-Band Bytestream input stream.
     * Subclasses of this input stream must provide a stanza listener along with a stanza filter to
     * collect the In-Band Bytestream data packets.
     */
    private abstract class IBBInputStream extends InputStream {

        /* the data packet listener to fill the data queue */
        private final StanzaListener dataPacketListener;

        /* queue containing received In-Band Bytestream data packets */
        protected final BlockingQueue<DataPacketExtension> dataQueue = new LinkedBlockingQueue<DataPacketExtension>();

        /* buffer containing the data from one data packet */
        private byte[] buffer;

        /* pointer to the next byte to read from buffer */
        private int bufferPointer = -1;

        /* data packet sequence (range from 0 to 65535) */
        private UInt16 expectedSeq = UInt16.MIN_VALUE;

        /* flag to indicate if input stream is closed */
        private boolean isClosed = false;

        /* flag to indicate if close method was invoked */
        private boolean closeInvoked = false;

        /* timeout for read operations */
        private int readTimeout = 0;

        /**
         * Constructor.
         */
        protected IBBInputStream() {
            // add data packet listener to connection
            this.dataPacketListener = getDataPacketListener();
            connection.addSyncStanzaListener(this.dataPacketListener, getDataPacketFilter());
        }

        /**
         * Returns the stanza listener that processes In-Band Bytestream data packets.
         *
         * @return the data stanza listener
         */
        protected abstract StanzaListener getDataPacketListener();

        /**
         * Returns the stanza filter that accepts In-Band Bytestream data packets.
         *
         * @return the data stanza filter
         */
        protected abstract StanzaFilter getDataPacketFilter();

        @Override
        public synchronized int read() throws IOException {
            checkClosed();

            // if nothing read yet or whole buffer has been read fill buffer
            if (bufferPointer == -1 || bufferPointer >= buffer.length) {
                // if no data available and stream was closed return -1
                if (!loadBuffer()) {
                    return -1;
                }
            }

            // return byte and increment buffer pointer
            return buffer[bufferPointer++] & 0xff;
        }

        @Override
        public synchronized int read(byte[] b, int off, int len) throws IOException {
            if (b == null) {
                throw new NullPointerException();
            }
            else if ((off < 0) || (off > b.length) || (len < 0) || ((off + len) > b.length)
                            || ((off + len) < 0)) {
                throw new IndexOutOfBoundsException();
            }
            else if (len == 0) {
                return 0;
            }

            checkClosed();

            // if nothing read yet or whole buffer has been read fill buffer
            if (bufferPointer == -1 || bufferPointer >= buffer.length) {
                // if no data available and stream was closed return -1
                if (!loadBuffer()) {
                    return -1;
                }
            }

            // if more bytes wanted than available return all available
            int bytesAvailable = buffer.length - bufferPointer;
            if (len > bytesAvailable) {
                len = bytesAvailable;
            }

            System.arraycopy(buffer, bufferPointer, b, off, len);
            bufferPointer += len;
            return len;
        }

        @Override
        public synchronized int read(byte[] b) throws IOException {
            return read(b, 0, b.length);
        }

        /**
         * This method blocks until a data stanza is received, the stream is closed or the current
         * thread is interrupted.
         *
         * @return <code>true</code> if data was received, otherwise <code>false</code>
         * @throws IOException if data packets are out of sequence
         */
        private synchronized boolean loadBuffer() throws IOException {

            // wait until data is available or stream is closed
            DataPacketExtension data = null;
            try {
                if (this.readTimeout == 0) {
                    while (data == null) {
                        if (isClosed && this.dataQueue.isEmpty()) {
                            return false;
                        }
                        data = this.dataQueue.poll(1000, TimeUnit.MILLISECONDS);
                    }
                }
                else {
                    data = this.dataQueue.poll(this.readTimeout, TimeUnit.MILLISECONDS);
                    if (data == null) {
                        throw new SocketTimeoutException();
                    }
                }
            }
            catch (InterruptedException e) {
                // Restore the interrupted status
                Thread.currentThread().interrupt();
                return false;
            }

            final UInt16 dataSeq = data.getSeq();
            // check if data packets sequence is successor of last seen sequence
            if (!expectedSeq.equals(dataSeq)) {
                // packets out of order; close stream/session
                InBandBytestreamSession.this.close();
                String message = UNEXPECTED_IBB_SEQUENCE + " " + dataSeq + " received, expected "
                                + expectedSeq;
                throw new IOException(message);
            }
            expectedSeq = dataSeq.incrementedByOne();

            // set buffer to decoded data
            buffer = data.getDecodedData();
            bufferPointer = 0;
            return true;
        }

        /**
         * Checks if this stream is closed and throws an IOException if necessary
         *
         * @throws IOException if stream is closed and no data should be read anymore
         */
        private void checkClosed() throws IOException {
            // Throw an exception if, and only if, this stream has been already
            // closed by the user using the close() method
            if (closeInvoked) {
                // clear data queue in case additional data was received after stream was closed
                this.dataQueue.clear();
                throw new IOException("Stream is closed");
            }
        }

        @Override
        public boolean markSupported() {
            return false;
        }

        @Override
        public void close() throws IOException {
            if (closeInvoked) {
                return;
            }

            this.closeInvoked = true;

            InBandBytestreamSession.this.closeByLocal(true);
        }

        /**
         * This method sets the close flag and removes the data stanza listener.
         */
        private void closeInternal() {
            if (isClosed) {
                return;
            }
            isClosed = true;
        }

        /**
         * Invoked if the session is closed.
         */
        private void cleanup() {
            connection.removeSyncStanzaListener(this.dataPacketListener);
        }
    }

    /**
     * IQIBBInputStream class implements IBBInputStream to be used with IQ stanzas encapsulating the
     * data packets.
     */
    private class IQIBBInputStream extends IBBInputStream {

        @Override
        protected StanzaListener getDataPacketListener() {
            return new StanzaListener() {

                private UInt16 expectedSequence = UInt16.MIN_VALUE;;

                @Override
                public void processStanza(Stanza packet) throws NotConnectedException, InterruptedException {
                    final Data dataIq = (Data) packet;
                    // get data packet extension
                    DataPacketExtension data = dataIq.getDataPacketExtension();

                    final UInt16 seq = data.getSeq();
                    /*
                     * check if sequence was not used already (see XEP-0047 Section 2.2)
                     */
                    if (!expectedSequence.equals(seq)) {
                        String descriptiveEnTest = UNEXPECTED_IBB_SEQUENCE + " " + seq + " received, expected "
                                        + expectedSequence;
                        StanzaError stanzaError = StanzaError.getBuilder()
                                        .setCondition(StanzaError.Condition.unexpected_request)
                                        .setDescriptiveEnText(descriptiveEnTest)
                                        .build();
                        IQ unexpectedRequest = IQ.createErrorResponse(dataIq, stanzaError);
                        connection.sendStanza(unexpectedRequest);

                        try {
                            // TODO: It would be great if close would take a "close error reason" argument. Also there
                            // is the question if this is really a reason to close the stream. We could have some more
                            // tolerance regarding out-of-sequence stanzas arriving: Even though XMPP has the in-order
                            // guarantee, I could imagine that there are cases where stanzas are, for example,
                            // duplicated because of stream resumption.
                            close();
                        } catch (IOException e) {
                            LOGGER.log(Level.FINER, "Could not close session, because of IOException. Close reason: "
                                            + descriptiveEnTest);
                        }
                        return;
                    }

                    // check if encoded data is valid (see XEP-0047 Section 2.2)
                    if (data.getDecodedData() == null) {
                        // data is invalid; respond with bad-request error
                        IQ badRequest = IQ.createErrorResponse((IQ) packet,
                                        StanzaError.Condition.bad_request);
                        connection.sendStanza(badRequest);
                        return;
                    }

                    expectedSequence = seq.incrementedByOne();

                    // data is valid; add to data queue
                    dataQueue.offer(data);

                    // confirm IQ
                    IQ confirmData = IQ.createResultIQ((IQ) packet);
                    connection.sendStanza(confirmData);
                }

            };
        }

        @Override
        protected StanzaFilter getDataPacketFilter() {
            /*
             * filter all IQ stanzas having type 'SET' (represented by Data class), containing a
             * data stanza extension, matching session ID and recipient
             */
            return new AndFilter(new StanzaTypeFilter(Data.class), new IBBDataPacketFilter());
        }
    }

    /**
     * MessageIBBInputStream class implements IBBInputStream to be used with message stanzas
     * encapsulating the data packets.
     */
    private class MessageIBBInputStream extends IBBInputStream {

        @Override
        protected StanzaListener getDataPacketListener() {
            return new StanzaListener() {

                @Override
                public void processStanza(Stanza packet) {
                    // get data packet extension
                    DataPacketExtension data = packet.getExtension(
                                    DataPacketExtension.class);

                    // check if encoded data is valid
                    if (data.getDecodedData() == null) {
                        /*
                         * TODO once a majority of XMPP server implementation support XEP-0079
                         * Advanced Message Processing the invalid message could be answered with an
                         * appropriate error. For now we just ignore the packet. Subsequent packets
                         * with an increased sequence will cause the input stream to close the
                         * stream/session.
                         */
                        return;
                    }

                    // data is valid; add to data queue
                    dataQueue.offer(data);

                    // TODO confirm packet once XMPP servers support XEP-0079
                }

            };
        }

        @Override
        protected StanzaFilter getDataPacketFilter() {
            /*
             * filter all message stanzas containing a data stanza extension, matching session ID
             * and recipient
             */
            return new AndFilter(new StanzaTypeFilter(Message.class), new IBBDataPacketFilter());
        }
    }

    /**
     * IBBDataPacketFilter class filters all packets from the remote peer of this session,
     * containing an In-Band Bytestream data stanza extension whose session ID matches this sessions
     * ID.
     */
    private class IBBDataPacketFilter implements StanzaFilter {

        @Override
        public boolean accept(Stanza packet) {
            // sender equals remote peer
            if (!packet.getFrom().equals(remoteJID)) {
                return false;
            }

            DataPacketExtension data;
            if (packet instanceof Data) {
                data = ((Data) packet).getDataPacketExtension();
            } else {
                // stanza contains data packet extension
                data = packet.getExtension(
                        DataPacketExtension.class);
                if (data == null) {
                    return false;
                }
            }

            // session ID equals this session ID
            if (!data.getSessionID().equals(byteStreamRequest.getSessionID())) {
                return false;
            }

            return true;
        }
    }

    /**
     * IBBOutputStream class is the base implementation of an In-Band Bytestream output stream.
     * Subclasses of this output stream must provide a method to send data over XMPP stream.
     */
    private abstract class IBBOutputStream extends OutputStream {

        /* buffer with the size of this sessions block size */
        protected final byte[] buffer;

        /* pointer to next byte to write to buffer */
        protected int bufferPointer = 0;

        /* data packet sequence (range from 0 to 65535) */
        protected UInt16 seq = UInt16.from(0);

        /* flag to indicate if output stream is closed */
        protected boolean isClosed = false;

        /**
         * Constructor.
         */
        private IBBOutputStream() {
            this.buffer = new byte[byteStreamRequest.getBlockSize()];
        }

        /**
         * Writes the given data stanza to the XMPP stream.
         *
         * @param data the data packet
         * @throws IOException if an I/O error occurred while sending or if the stream is closed
         * @throws NotConnectedException if the XMPP connection is not connected.
         * @throws InterruptedException if the calling thread was interrupted.
         */
        protected abstract void writeToXML(DataPacketExtension data) throws IOException, NotConnectedException, InterruptedException;

        @Override
        public synchronized void write(int b) throws IOException {
            if (this.isClosed) {
                throw new IOException("Stream is closed");
            }

            // if buffer is full flush buffer
            if (bufferPointer >= buffer.length) {
                flushBuffer();
            }

            buffer[bufferPointer++] = (byte) b;
        }

        @Override
        public synchronized void write(byte[] b, int off, int len) throws IOException {
            if (b == null) {
                throw new NullPointerException();
            }
            else if ((off < 0) || (off > b.length) || (len < 0) || ((off + len) > b.length)
                            || ((off + len) < 0)) {
                throw new IndexOutOfBoundsException();
            }
            else if (len == 0) {
                return;
            }

            if (this.isClosed) {
                throw new IOException("Stream is closed");
            }

            // is data to send greater than buffer size
            if (len >= buffer.length) {

                // "byte" off the first chunk to write out
                writeOut(b, off, buffer.length);

                // recursively call this method with the lesser amount
                write(b, off + buffer.length, len - buffer.length);
            }
            else {
                writeOut(b, off, len);
            }
        }

        @Override
        public synchronized void write(byte[] b) throws IOException {
            write(b, 0, b.length);
        }

        /**
         * Fills the buffer with the given data and sends it over the XMPP stream if the buffers
         * capacity has been reached. This method is only called from this class so it is assured
         * that the amount of data to send is <= buffer capacity
         *
         * @param b the data
         * @param off the data
         * @param len the number of bytes to write
         * @throws IOException if an I/O error occurred while sending or if the stream is closed
         */
        private synchronized void writeOut(byte[] b, int off, int len) throws IOException {
            if (this.isClosed) {
                throw new IOException("Stream is closed");
            }

            // set to 0 in case the next 'if' block is not executed
            int available = 0;

            // is data to send greater that buffer space left
            if (len > buffer.length - bufferPointer) {
                // fill buffer to capacity and send it
                available = buffer.length - bufferPointer;
                System.arraycopy(b, off, buffer, bufferPointer, available);
                bufferPointer += available;
                flushBuffer();
            }

            // copy the data left to buffer
            System.arraycopy(b, off + available, buffer, bufferPointer, len - available);
            bufferPointer += len - available;
        }

        @Override
        public synchronized void flush() throws IOException {
            if (this.isClosed) {
                throw new IOException("Stream is closed");
            }
            flushBuffer();
        }

        private synchronized void flushBuffer() throws IOException {

            // do nothing if no data to send available
            if (bufferPointer == 0) {
                return;
            }

            // create data packet
            String enc = Base64.encodeToString(buffer, 0, bufferPointer);
            DataPacketExtension data = new DataPacketExtension(byteStreamRequest.getSessionID(),
                            this.seq, enc);

            // write to XMPP stream
            try {
                writeToXML(data);
            }
            catch (InterruptedException | NotConnectedException e) {
                IOException ioException = new IOException();
                ioException.initCause(e);
                throw ioException;
            }

            // reset buffer pointer
            bufferPointer = 0;

            // increment sequence, considering sequence overflow
            seq = seq.incrementedByOne();

        }

        @Override
        public void close() throws IOException {
            if (isClosed) {
                return;
            }
            InBandBytestreamSession.this.closeByLocal(false);
        }

        /**
         * Sets the close flag and optionally flushes the stream.
         *
         * @param flush if <code>true</code> flushes the stream
         */
        protected void closeInternal(boolean flush) {
            if (this.isClosed) {
                return;
            }
            this.isClosed = true;

            try {
                if (flush) {
                    flushBuffer();
                }
            }
            catch (IOException e) {
                /*
                 * ignore, because writeToXML() will not throw an exception if stream is already
                 * closed
                 */
            }
        }
    }

    /**
     * IQIBBOutputStream class implements IBBOutputStream to be used with IQ stanzas encapsulating
     * the data packets.
     */
    private class IQIBBOutputStream extends IBBOutputStream {

        @Override
        protected synchronized void writeToXML(DataPacketExtension data) throws IOException {
            // create IQ stanza containing data packet
            IQ iq = new Data(data);
            iq.setTo(remoteJID);

            try {
                connection.createStanzaCollectorAndSend(iq).nextResultOrThrow();
            }
            catch (Exception e) {
                // close session unless it is already closed
                if (!this.isClosed) {
                    InBandBytestreamSession.this.close();
                    // Sadly we are unable to use the IOException(Throwable) constructor because this
                    // constructor is only supported from Android API 9 on.
                    IOException ioException = new IOException();
                    ioException.initCause(e);
                    throw ioException;
                }
            }

        }
    }

    /**
     * MessageIBBOutputStream class implements IBBOutputStream to be used with message stanzas
     * encapsulating the data packets.
     */
    private class MessageIBBOutputStream extends IBBOutputStream {

        @Override
        protected synchronized void writeToXML(DataPacketExtension data) throws NotConnectedException, InterruptedException {
            // create message stanza containing data packet
            Message message = StanzaBuilder.buildMessage().to(remoteJID)
                    .addExtension(data)
                    .build();

            connection.sendStanza(message);

        }

    }

    /**
     * Process IQ stanza.
     * @param data TODO javadoc me please
     * @throws NotConnectedException if the XMPP connection is not connected.
     * @throws InterruptedException if the calling thread was interrupted.
     * @throws NotLoggedInException if the XMPP connection is not authenticated.
     */
    public void processIQPacket(Data data) throws NotConnectedException, InterruptedException, NotLoggedInException {
        inputStream.dataPacketListener.processStanza(data);
    }
}
