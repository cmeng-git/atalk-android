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

import java.util.LinkedList;
import java.util.List;

/**
 * Provides an abstract implementation of the basic functionality of <code>ContactQuery</code> and allows
 * extenders to focus on the specifics of their implementation.
 *
 * @param <T> the very type of <code>ContactSourceService</code> which performs the <code>ContactQuery</code>
 * @author Lyubomir Marinov
 * @author Eng Chong Meng
 */
public abstract class AbstractContactQuery<T extends ContactSourceService>
        implements ContactQuery
{
    /**
     * The <code>ContactSourceService</code> which is performing this <code>ContactQuery</code>.
     */
    private final T contactSource;

    /**
     * The <code>List</code> of <code>ContactQueryListener</code>s which are to be
     * notified by this <code>ContactQuery</code> about changes in its status, the
     * receipt of new <code>ContactSource</code>s via this <code>ContactQuery</code>, etc.
     */
    private final List<ContactQueryListener> listeners = new LinkedList<>();

    /**
     * The status of this <code>ContactQuery</code> which is one of the
     * <code>QUERY_XXX</code> constants defined by the <code>ContactQuery</code> class.
     */
    private int status = QUERY_IN_PROGRESS;

    /**
     * Initializes a new <code>AbstractContactQuery</code> which is to be performed
     * by a specific <code>ContactSourceService</code>. The status of the new
     * instance is {@link ContactQuery#QUERY_IN_PROGRESS}.
     *
     * @param contactSource the <code>ContactSourceService</code> which is to perform the new <code>AbstractContactQuery</code>
     */
    protected AbstractContactQuery(T contactSource)
    {
        this.contactSource = contactSource;
    }

    /**
     * Adds a <code>ContactQueryListener</code> to the list of listeners interested
     * in notifications about this <code>ContactQuery</code> changing its status,
     * the receipt of new <code>SourceContact</code>s via this <code>ContactQuery</code>, etc.
     *
     * @param l the <code>ContactQueryListener</code> to be added to the list of
     * listeners interested in the notifications raised by this <code>ContactQuery</code>
     * @see ContactQuery#addContactQueryListener(ContactQueryListener)
     */
    public void addContactQueryListener(ContactQueryListener l)
    {
        if (l == null)
            throw new NullPointerException("l");
        else {
            synchronized (listeners) {
                if (!listeners.contains(l))
                    listeners.add(l);
            }
        }
    }

    /**
     * Cancels this <code>ContactQuery</code>.
     *
     * @see ContactQuery#cancel()
     */
    public void cancel()
    {
        if (getStatus() == QUERY_IN_PROGRESS)
            setStatus(QUERY_CANCELED);
    }

    /**
     * Notifies the <code>ContactQueryListener</code>s registered with this
     * <code>ContactQuery</code> that a new <code>SourceContact</code> has been received.
     *
     * @param contact the <code>SourceContact</code> which has been received and
     * which the registered <code>ContactQueryListener</code>s are to be notified about
     * @param showMoreEnabled indicates whether show more label should be shown or not.
     */
    protected void fireContactReceived(SourceContact contact, boolean showMoreEnabled)
    {
        ContactQueryListener[] ls;
        synchronized (listeners) {
            ls = listeners.toArray(new ContactQueryListener[0]);
        }
        ContactReceivedEvent ev = new ContactReceivedEvent(this, contact, showMoreEnabled);
        for (ContactQueryListener l : ls) {
            l.contactReceived(ev);
        }
    }

    /**
     * Notifies the <code>ContactQueryListener</code>s registered with this
     * <code>ContactQuery</code> that a new <code>SourceContact</code> has been received.
     *
     * @param contact the <code>SourceContact</code> which has been received and
     * which the registered <code>ContactQueryListener</code>s are to be notified about
     */
    protected void fireContactReceived(SourceContact contact)
    {
        fireContactReceived(contact, true);
    }

    /**
     * Notifies the <code>ContactQueryListener</code>s registered with this
     * <code>ContactQuery</code> that a <code>SourceContact</code> has been removed.
     *
     * @param contact the <code>SourceContact</code> which has been removed and
     * which the registered <code>ContactQueryListener</code>s are to be notified about
     */
    protected void fireContactRemoved(SourceContact contact)
    {
        ContactQueryListener[] ls;
        synchronized (listeners) {
            ls = listeners.toArray(new ContactQueryListener[0]);
        }

        ContactRemovedEvent ev = new ContactRemovedEvent(this, contact);
        for (ContactQueryListener l : ls)
            l.contactRemoved(ev);
    }

    /**
     * Notifies the <code>ContactQueryListener</code>s registered with this
     * <code>ContactQuery</code> that a <code>SourceContact</code> has been changed.
     *
     * @param contact the <code>SourceContact</code> which has been changed and
     * which the registered <code>ContactQueryListener</code>s are to be notified about
     */
    protected void fireContactChanged(SourceContact contact)
    {
        ContactQueryListener[] ls;
        synchronized (listeners) {
            ls = listeners.toArray(new ContactQueryListener[0]);
        }

        ContactChangedEvent ev = new ContactChangedEvent(this, contact);
        for (ContactQueryListener l : ls)
            l.contactChanged(ev);
    }

    /**
     * Notifies the <code>ContactQueryListener</code>s registered with this <code>ContactQuery</code> that its state has changed.
     *
     * @param eventType the type of the <code>ContactQueryStatusEvent</code> to be
     * fired which can be one of the <code>QUERY_XXX</code> constants defined by <code>ContactQueryStatusEvent</code>
     */
    protected void fireQueryStatusChanged(int eventType)
    {
        ContactQueryListener[] ls;
        synchronized (listeners) {
            ls = listeners.toArray(new ContactQueryListener[0]);
        }

        ContactQueryStatusEvent ev = new ContactQueryStatusEvent(this, eventType);
        for (ContactQueryListener l : ls)
            l.queryStatusChanged(ev);
    }

    /**
     * Gets the <code>ContactSourceService</code> which is performing this <code>ContactQuery</code>.
     *
     * @return the <code>ContactSourceService</code> which is performing this <code>ContactQuery</code>
     * @see ContactQuery#getContactSource()
     */
    public T getContactSource()
    {
        return contactSource;
    }

    /**
     * Gets the status of this <code>ContactQuery</code> which can be one of the
     * <code>QUERY_XXX</code> constants defined by <code>ContactQuery</code>.
     *
     * @return the status of this <code>ContactQuery</code> which can be one of the
     * <code>QUERY_XXX</code> constants defined by <code>ContactQuery</code>
     * @see ContactQuery#getStatus()
     */
    @Override
    public int getStatus()
    {
        return status;
    }

    /**
     * Removes a <code>ContactQueryListener</code> from the list of listeners
     * interested in notifications about this <code>ContactQuery</code> changing its
     * status, the receipt of new <code>SourceContact</code>s via this <code>ContactQuery</code>, etc.
     *
     * @param l the <code>ContactQueryListener</code> to be removed from the list of
     * listeners interested in notifications raised by this <code>ContactQuery</code>
     * @see ContactQuery#removeContactQueryListener(ContactQueryListener)
     */
    public void removeContactQueryListener(ContactQueryListener l)
    {
        if (l != null) {
            synchronized (listeners) {
                listeners.remove(l);
            }
        }
    }

    /**
     * Sets the status of this <code>ContactQuery</code>.
     *
     * @param status {@link ContactQuery#QUERY_CANCELED},
     * {@link ContactQuery#QUERY_COMPLETED}, or
     * {@link ContactQuery#QUERY_ERROR}
     */
    public void setStatus(int status)
    {
        if (this.status != status) {
            int eventType;

            switch (status) {
                case QUERY_CANCELED:
                    eventType = ContactQueryStatusEvent.QUERY_CANCELED;
                    break;
                case QUERY_COMPLETED:
                    eventType = ContactQueryStatusEvent.QUERY_COMPLETED;
                    break;
                case QUERY_ERROR:
                    eventType = ContactQueryStatusEvent.QUERY_ERROR;
                    break;
                case QUERY_IN_PROGRESS:
                default:
                    throw new IllegalArgumentException("status");
            }
            this.status = status;
            fireQueryStatusChanged(eventType);
        }
    }
}
