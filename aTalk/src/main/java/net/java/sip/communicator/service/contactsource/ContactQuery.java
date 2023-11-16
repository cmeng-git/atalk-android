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

import java.util.List;

/**
 * The <code>ContactQuery</code> corresponds to a particular query made through the
 * <code>ContactSourceService</code>. Each query once started could be
 * canceled. One could also register a listener in order to be notified for
 * changes in query status and query contact results.
 *
 * @author Yana Stamcheva
 * @author Eng Chong Meng
 */
public interface ContactQuery
{
    /**
     * Indicates that this query has been completed.
     */
    public static final int QUERY_COMPLETED = 0;

    /**
     * Indicates that this query has been canceled.
     */
    public static final int QUERY_CANCELED = 1;

    /**
     * Indicates that this query has been stopped because of an error.
     */
    public static final int QUERY_ERROR = 2;

    /**
     * Indicates that this query is in progress.
     */
    public static final int QUERY_IN_PROGRESS = 3;

    /**
     * Returns the <code>ContactSourceService</code>, where this query was first  initiated.
     * @return the <code>ContactSourceService</code>, where this query was first initiated
     */
    ContactSourceService getContactSource();

    /**
     * Returns the query string, this query was created for.
     * @return the query string, this query was created for
     */
    String getQueryString();

    /**
     * Returns the list of <code>SourceContact</code>s returned by this query.
     * @return the list of <code>SourceContact</code>s returned by this query
     */
    List<SourceContact> getQueryResults();
    
    /**
     * Starts the query.
     */
    void start();

    /**
     * Cancels this query.
     */
    void cancel();

    /**
     * Returns the status of this query. One of the static constants QUERY_XXXX defined in this class.
     * @return the status of this query
     */
    int getStatus();

    /**
     * Adds the given <code>ContactQueryListener</code> to the list of registered
     * listeners. The <code>ContactQueryListener</code> would be notified each
     * time a new <code>ContactQuery</code> result has been received or if the
     * query has been completed or has been canceled by user or for any other reason.
     * @param l the <code>ContactQueryListener</code> to add
     */
    void addContactQueryListener(ContactQueryListener l);

    /**
     * Removes the given <code>ContactQueryListener</code> to the list of
     * registered listeners. The <code>ContactQueryListener</code> would be
     * notified each time a new <code>ContactQuery</code> result has been received
     * or if the query has been completed or has been canceled by user or for any other reason.
     * @param l the <code>ContactQueryListener</code> to remove
     */
    void removeContactQueryListener(ContactQueryListener l);
}
