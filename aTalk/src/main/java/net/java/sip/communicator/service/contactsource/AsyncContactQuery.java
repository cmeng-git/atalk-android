/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
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
package net.java.sip.communicator.service.contactsource;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Pattern;

/**
 * Provides an abstract implementation of a <code>ContactQuery</code> which runs in a separate <code>Thread</code>.
 *
 * @param <T> the very type of <code>ContactSourceService</code> which performs the <code>ContactQuery</code>
 *
 * @author Lyubomir Marinov
 * @author Eng Chong Meng
 */
public abstract class AsyncContactQuery<T extends ContactSourceService>
        extends AbstractContactQuery<T> {
    /**
     * The {@link #query} in the form of a <code>String</code> telephone number if
     * such parsing, formatting and validation is possible; otherwise, <code>null</code>.
     */
    private String phoneNumberQuery;

    /**
     * The <code>Pattern</code> for which the associated <code>ContactSourceService</code> is being queried.
     */
    protected final Pattern query;

    /**
     * The indicator which determines whether there has been an attempt to
     * convert {@link #query} to {@link #phoneNumberQuery}. If the conversion has
     * been successful, <code>phoneNumberQuery</code> will be non-<code>null</code>.
     */
    private boolean queryIsConvertedToPhoneNumber;

    /**
     * The <code>SourceContact</code>s which match {@link #query}.
     */
    private Collection<SourceContact> queryResults = new LinkedList<>();

    /**
     * The <code>Thread</code> in which this <code>AsyncContactQuery</code> is performing {@link #query}.
     */
    private Thread thread;

    /**
     * Initializes a new <code>AsyncContactQuery</code> instance which is to perform
     * a specific <code>query</code> on behalf of a specific <code>contactSource</code>.
     *
     * @param contactSource the <code>ContactSourceService</code> which is to
     * perform the new <code>ContactQuery</code> instance
     * @param query the <code>Pattern</code> for which <code>contactSource</code> is being queried
     * @param isSorted indicates if the results of this query should be sorted
     */
    protected AsyncContactQuery(T contactSource, Pattern query, boolean isSorted) {
        super(contactSource);
        this.query = query;
        if (isSorted)
            queryResults = new TreeSet<>();
    }

    /**
     * Initializes a new <code>AsyncContactQuery</code> instance which is to perform
     * a specific <code>query</code> on behalf of a specific <code>contactSource</code>.
     *
     * @param contactSource the <code>ContactSourceService</code> which is to
     * perform the new <code>ContactQuery</code> instance
     * @param query the <code>Pattern</code> for which <code>contactSource</code> is being queried
     */
    protected AsyncContactQuery(T contactSource, Pattern query) {
        super(contactSource);
        this.query = query;
    }

    /**
     * Adds a specific <code>SourceContact</code> to the list of
     * <code>SourceContact</code>s to be returned by this <code>ContactQuery</code> in
     * response to {@link #getQueryResults()}.
     *
     * @param sourceContact the <code>SourceContact</code> to be added to the
     * <code>queryResults</code> of this <code>ContactQuery</code>
     * @param showMoreEnabled indicates whether show more label should be shown or not.
     *
     * @return <code>true</code> if the <code>queryResults</code> of this
     * <code>ContactQuery</code> has changed in response to the call
     */
    protected boolean addQueryResult(SourceContact sourceContact, boolean showMoreEnabled) {
        boolean changed;
        synchronized (queryResults) {
            changed = queryResults.add(sourceContact);
        }
        if (changed)
            fireContactReceived(sourceContact, showMoreEnabled);

        return changed;
    }

    /**
     * Adds a specific <code>SourceContact</code> to the list of
     * <code>SourceContact</code>s to be returned by this <code>ContactQuery</code> in response to {@link #getQueryResults()}.
     *
     * @param sourceContact the <code>SourceContact</code> to be added to the
     * <code>queryResults</code> of this <code>ContactQuery</code>
     *
     * @return <code>true</code> if the <code>queryResults</code> of this
     * <code>ContactQuery</code> has changed in response to the call
     */
    protected boolean addQueryResult(SourceContact sourceContact) {
        boolean changed;
        synchronized (queryResults) {
            changed = queryResults.add(sourceContact);
        }
        if (changed)
            fireContactReceived(sourceContact);
        return changed;
    }

    /**
     * Removes a specific <code>SourceContact</code> from the list of
     * <code>SourceContact</code>s.
     *
     * @param sourceContact the <code>SourceContact</code> to be removed from the
     * <code>queryResults</code> of this <code>ContactQuery</code>
     *
     * @return <code>true</code> if the <code>queryResults</code> of this
     * <code>ContactQuery</code> has changed in response to the call
     */
    protected boolean removeQueryResult(SourceContact sourceContact) {
        boolean changed;
        synchronized (queryResults) {
            changed = queryResults.remove(sourceContact);
        }
        if (changed)
            fireContactRemoved(sourceContact);
        return changed;
    }

    /**
     * Adds a set of <code>SourceContact</code> instances to the list of
     * <code>SourceContact</code>s to be returned by this <code>ContactQuery</code> in
     * response to {@link #getQueryResults()}.
     *
     * @param sourceContacts the set of <code>SourceContact</code> to be added to
     * the <code>queryResults</code> of this <code>ContactQuery</code>
     *
     * @return <code>true</code> if the <code>queryResults</code> of this
     * <code>ContactQuery</code> has changed in response to the call
     */
    protected boolean addQueryResults(final Set<? extends SourceContact> sourceContacts) {
        final boolean changed;

        synchronized (queryResults) {
            changed = queryResults.addAll(sourceContacts);
        }

        if (changed) {
            // TODO Need something to fire one event for multiple contacts.
            for (SourceContact contact : sourceContacts) {
                fireContactReceived(contact, false);
            }
        }
        return changed;
    }


    /**
     * Gets the {@link #query} of this <code>AsyncContactQuery</code> as a
     * <code>String</code> which represents a phone number (if possible).
     *
     * @return a <code>String</code> which represents the <code>query</code> of this
     * <code>AsyncContactQuery</code> as a phone number if such parsing, formatting
     * and validation is possible; otherwise, <code>null</code>
     */
    protected String getPhoneNumberQuery() {
        if ((phoneNumberQuery == null) && !queryIsConvertedToPhoneNumber) {
            try {
                String pattern = query.pattern();
                if (pattern != null) {
                    int patternLength = pattern.length();

                    if ((patternLength > 2)
                            && (pattern.charAt(0) == '^')
                            && (pattern.charAt(patternLength - 1) == '$')) {
                        phoneNumberQuery = pattern.substring(1, patternLength - 1);
                    }
                    else if ((patternLength > 4)
                            && (pattern.charAt(0) == '\\')
                            && (pattern.charAt(1) == 'Q')
                            && (pattern.charAt(patternLength - 2) == '\\')
                            && (pattern.charAt(patternLength - 1) == 'E')) {
                        phoneNumberQuery = pattern.substring(2, patternLength - 2);
                    }
                }
            } finally {
                queryIsConvertedToPhoneNumber = true;
            }
        }
        return phoneNumberQuery;
    }

    /**
     * Gets the number of <code>SourceContact</code>s which match this <code>ContactQuery</code>.
     *
     * @return the number of <code>SourceContact</code> which match this <code>ContactQuery</code>
     */
    public int getQueryResultCount() {
        synchronized (queryResults) {
            return queryResults.size();
        }
    }

    /**
     * Gets the <code>List</code> of <code>SourceContact</code>s which match this <code>ContactQuery</code>.
     *
     * @return the <code>List</code> of <code>SourceContact</code>s which match this <code>ContactQuery</code>
     *
     * @see ContactQuery#getQueryResults()
     */
    public List<SourceContact> getQueryResults() {
        List<SourceContact> qr;

        synchronized (queryResults) {
            qr = new ArrayList<>(queryResults.size());
            qr.addAll(queryResults);
        }
        return qr;
    }

    /**
     * Returns the query string, this query was created for.
     *
     * @return the query string, this query was created for
     */
    public String getQueryString() {
        return query.toString();
    }

    /**
     * Performs this <code>ContactQuery</code> in a background <code>Thread</code>.
     */
    protected abstract void run();

    /**
     * Starts this <code>AsyncContactQuery</code>.
     */
    public synchronized void start() {
        if (thread == null) {
            thread = new Thread() {
                @Override
                public void run() {
                    boolean completed = false;

                    try {
                        AsyncContactQuery.this.run();
                        completed = true;
                    } finally {
                        synchronized (AsyncContactQuery.this) {
                            if (thread == Thread.currentThread())
                                stopped(completed);
                        }
                    }
                }
            };
            thread.setDaemon(true);
            thread.start();
        }
        else
            throw new IllegalStateException("thread");
    }

    /**
     * Notifies this <code>AsyncContactQuery</code> that it has stopped performing
     * in the associated background <code>Thread</code>.
     *
     * @param completed <code>true</code> if this <code>ContactQuery</code> has
     * successfully completed, <code>false</code> if an error has been encountered during its execution
     */
    protected void stopped(boolean completed) {
        if (getStatus() == QUERY_IN_PROGRESS)
            setStatus(completed ? QUERY_COMPLETED : QUERY_ERROR);
    }

    /**
     * Determines whether a specific <code>String</code> phone number matches the
     * {@link #query} of this <code>AsyncContactQuery</code>.
     *
     * @param phoneNumber the <code>String</code> which represents the phone number
     * to match to the <code>query</code> of this <code>AsyncContactQuery</code>
     *
     * @return <code>true</code> if the specified <code>phoneNumber</code> matches the
     * <code>query</code> of this <code>AsyncContactQuery</code>; otherwise, <code>false</code>
     */
    protected boolean phoneNumberMatches(String phoneNumber) {
        /*
         * PhoneNumberI18nService implements functionality to aid the parsing,
         * formatting and validation of international phone numbers so attempt
         * to use it to determine whether the specified phoneNumber matches the
         * query. For example, check whether the normalized phoneNumber matches the query.
         */

        boolean phoneNumberMatches = false;

        if (query.matcher(ContactSourceActivator.getPhoneNumberI18nService().normalize(phoneNumber)).find()) {
            phoneNumberMatches = true;
        }
        else {
            /*
             * The fact that the normalized form of the phoneNumber doesn't
             * match the query doesn't mean that, for example, it doesn't
             * match the normalized form of the query. The latter, though,
             * requires the query to look like a phone number as well. In
             * order to not accidentally start matching all queries to phone
             * numbers, it seems justified to normalize the query only when
             * it is a phone number, not whenever it looks like a piece of a
             * phone number.
             */

            String phoneNumberQuery = getPhoneNumberQuery();

            if ((phoneNumberQuery != null)
                    && (phoneNumberQuery.length() != 0)) {
                try {
                    phoneNumberMatches = ContactSourceActivator.getPhoneNumberI18nService().phoneNumbersMatch(
                            phoneNumberQuery,
                            phoneNumber);
                } catch (IllegalArgumentException iaex) {
                    /*
                     * Ignore it, phoneNumberMatches will remain equal to
                     * false.
                     */
                }
            }
        }
        return phoneNumberMatches;
    }
}
