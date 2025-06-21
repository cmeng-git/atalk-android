/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package net.java.sip.communicator.service.history;

import java.util.NoSuchElementException;

/**
 * @author Alexander Pelov
 */
public interface QueryResultSet<T> extends BidirectionalIterator<T> {
    /**
     * A strongly-typed variant of <code>next()</code>.
     *
     * @return the next history record.
     *
     * @throws NoSuchElementException iteration has no more elements.
     */
    T nextRecord()
            throws NoSuchElementException;

    /**
     * A strongly-typed variant of <code>prev()</code>.
     *
     * @return the previous history record.
     *
     * @throws NoSuchElementException iteration has no more elements.
     */
    T prevRecord()
            throws NoSuchElementException;
}
