package net.java.sip.communicator.impl.history;

import java.util.LinkedList;
import java.util.NoSuchElementException;
import java.util.Set;

import net.java.sip.communicator.service.history.QueryResultSet;

/**
 * This implementation is the same as DefaultQueryResultSet but the container holding the records is LinkedList - so
 * guarantees that values are ordered
 *
 * @param <T> element type of query
 *
 * @author Damian Minkov
 */
public class OrderedQueryResultSet<T> implements QueryResultSet<T> {
    private LinkedList<T> records = null;

    private int currentPos = -1;

    /**
     * Constructor.
     *
     * @param records the <code>Set</code> of records
     */
    public OrderedQueryResultSet(Set<T> records) {
        this.records = new LinkedList<T>(records);
    }

    /**
     * Returns <code>true</code> if the iteration has more elements.
     *
     * @return <code>true</code> if the iterator has more elements.
     */
    public boolean hasNext() {
        return this.currentPos + 1 < this.records.size();
    }

    /**
     * Returns true if the iteration has elements preceeding the current one.
     *
     * @return true if the iterator has preceeding elements.
     */
    public boolean hasPrev() {
        return this.currentPos - 1 >= 0;
    }

    /**
     * Returns the next element in the iteration.
     *
     * @return the next element in the iteration.
     */
    public T next() {
        this.currentPos++;

        if (this.currentPos >= this.records.size()) {
            throw new NoSuchElementException();
        }

        return records.get(this.currentPos);
    }

    /**
     * A strongly-typed variant of <code>next()</code>.
     *
     * @return the next history record.
     *
     * @throws NoSuchElementException iteration has no more elements.
     */
    public T nextRecord()
            throws NoSuchElementException {
        return this.next();
    }

    /**
     * Returns the previous element in the iteration.
     *
     * @return the previous element in the iteration.
     *
     * @throws NoSuchElementException iteration has no more elements.
     */
    public T prev()
            throws NoSuchElementException {
        this.currentPos--;

        if (this.currentPos < 0) {
            throw new NoSuchElementException();
        }

        return records.get(this.currentPos);
    }

    /**
     * A strongly-typed variant of <code>prev()</code>.
     *
     * @return the previous history record.
     *
     * @throws NoSuchElementException iteration has no more elements.
     */
    public T prevRecord()
            throws NoSuchElementException {
        return this.prev();
    }

    /**
     * Removes from the underlying collection the last element returned by the iterator (optional operation).
     */
    public void remove() {
        throw new UnsupportedOperationException("Cannot remove elements from underlaying collection.");
    }
}
