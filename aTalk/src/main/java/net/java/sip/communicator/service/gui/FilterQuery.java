/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license.
 * See terms of license at gnu.org.
 */
package net.java.sip.communicator.service.gui;

import net.java.sip.communicator.service.contactsource.ContactQuery;
import net.java.sip.communicator.service.gui.event.FilterQueryListener;

/**
 * The <code>FilterQuery</code> gives information about a current filtering.
 *
 * @author Yana Stamcheva
 */
public abstract class FilterQuery
{
    /**
     * The maximum result count for each contact source.
     */
    private int maxResultCount = 10;

    /**
     * A listener, which is notified when this query finishes.
     */
    private FilterQueryListener filterQueryListener;

    /**
     * Adds the given <code>contactQuery</code> to the list of filterQueries.
     * @param contactQuery the <code>ContactQuery</code> to add
     */
    public abstract void addContactQuery(Object contactQuery);

    /**
     * Sets the <code>isSucceeded</code> property.
     * @param isSucceeded indicates if this query has succeeded
     */
    public abstract void setSucceeded(boolean isSucceeded);

    /**
     * Indicates if this query has succeeded.
     * @return <code>true</code> if this query has succeeded, <code>false</code> -
     * otherwise
     */
    public abstract boolean isSucceeded();

    /**
     * Indicates if this query is canceled.
     * @return <code>true</code> if this query is canceled, <code>false</code> otherwise
     */
    public abstract boolean isCanceled();

    /**
     * Indicates if this query is canceled.
     *
     * @return <code>true</code> if this query is canceled, <code>false</code> otherwise
     */
    public abstract boolean isRunning();

    /**
     * Cancels this filter query.
     */
    public abstract void cancel();

    /**
     * Closes this query to indicate that no more contact sub-queries would be
     * added to it.
     */
    public abstract void close();

    /**
     * Sets the given <code>FilterQueryListener</code>.
     * @param l the <code>FilterQueryListener</code> to set
     */
    public void setQueryListener(FilterQueryListener l)
    {
        filterQueryListener = l;
    }

    /**
     * Removes the given query from this filter query, updates the related data
     * and notifies interested parties if this was the last query to process.
     * @param query the <code>ContactQuery</code> to remove.
     */
    public abstract void removeQuery(ContactQuery query);

    /**
     * Verifies if the given query is contained in this filter query.
     *
     * @param query the query we're looking for
     * @return <code>true</code> if the given <code>query</code> is contained in this
     * filter query, <code>false</code> - otherwise
     */
    public abstract boolean containsQuery(Object query);


    /**
     * Sets the maximum result count shown.
     *
     * @param resultCount the maximum result count shown
     */
    public void setMaxResultShown(int resultCount)
    {
        this.maxResultCount = resultCount;
    }

    /**
     * Gets the maximum result count shown.
     *
     * @return the maximum result count shown
     */
    public int getMaxResultShown()
    {
        return maxResultCount;
    }
}
