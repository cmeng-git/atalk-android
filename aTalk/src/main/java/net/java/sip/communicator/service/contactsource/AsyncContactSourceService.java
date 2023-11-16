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

import java.util.regex.Pattern;

/**
 * Declares the interface of a <code>ContactSourceService</code> which performs
 * <code>ContactQuery</code>s in a separate <code>Thread</code>.
 *
 * @author Lyubomir Marinov
 * @author Eng Chong Meng
 */
public abstract class AsyncContactSourceService
    implements ExtendedContactSourceService
{
    /**
     * Creates query that searches for <code>SourceContact</code>s
     * which match a specific <code>query</code> <code>String</code>.
     *
     * @param query the <code>String</code> which this <code>ContactSourceService</code>
     * is being queried for
     * @return a <code>ContactQuery</code> which represents the query of this
     * <code>ContactSourceService</code> implementation for the specified
     * <code>String</code> and via which the matching <code>SourceContact</code>s (if
     * any) will be returned
     * @see ContactSourceService#createContactQuery(String)
     */
    public ContactQuery createContactQuery(String query)
    {
        return createContactQuery(
            Pattern.compile(query, Pattern.CASE_INSENSITIVE | Pattern.LITERAL));
    }

    /**
     * Creates query that searches for <code>SourceContact</code>s
     * which match a specific <code>query</code> <code>String</code>.
     *
     * @param query the <code>String</code> which this <code>ContactSourceService</code>
     * is being queried for
     * @param contactCount the maximum count of result contacts
     * @return a <code>ContactQuery</code> which represents the query of this
     * <code>ContactSourceService</code> implementation for the specified
     * <code>String</code> and via which the matching <code>SourceContact</code>s (if
     * any) will be returned
     * @see ContactSourceService#createContactQuery(String)
     */
    public ContactQuery createContactQuery(String query, int contactCount)
    {
        return createContactQuery(
            Pattern.compile(query, Pattern.CASE_INSENSITIVE | Pattern.LITERAL));
    }

    /**
     * Stops this <code>ContactSourceService</code>.
     */
    public abstract void stop();

    /**
     * Defines whether using this contact source service (Outlook or MacOSX
     * Contacts) can be used as result for the search field. This is
     * useful when an external plugin looks for result of this contact source
     * service, but want to display the search field result from its own (avoid
     * duplicate results).
     *
     * @return True if this contact source service can be used to perform search
     * for contacts. False otherwise.
     */
    public abstract boolean canBeUsedToSearchContacts();
}
