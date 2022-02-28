/*
 * Copyright @ 2015 Atlassian Pty Ltd
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
package org.atalk.util.event;

import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.util.ArrayList;
import java.util.List;

import timber.log.Timber;

/**
 * Represents a source of <code>PropertyChangeEvent</code>s which notifies
 * <code>PropertyChangeListener</code>s about changes in the values of properties.
 *
 * @author Lyubomir Marinov
 * @author Eng Chong Meng
 */
public class PropertyChangeNotifier
{
    /**
     * The list of <code>PropertyChangeListener</code>s interested in and notified about changes in
     * the values of the properties of this <code>PropertyChangeNotifier</code>.
     */
    private final List<PropertyChangeListener> listeners = new ArrayList<>();

    /**
     * Initializes a new <code>PropertyChangeNotifier</code> instance.
     */
    public PropertyChangeNotifier()
    {
    }

    /**
     * Adds a specific <code>PropertyChangeListener</code> to the list of listeners
     * interested in and notified about changes in the values of the properties
     * of this <code>PropertyChangeNotifier</code>.
     *
     * @param listener a <code>PropertyChangeListener</code> to be notified about
     * changes in the values of the properties of this
     * <code>PropertyChangeNotifier</code>. If the specified listener is already in the list of
     * interested listeners (i.e. it has been previously added), it is not added again.
     */
    public void addPropertyChangeListener(PropertyChangeListener listener)
    {
        if (listener == null) {
            Timber.d("The specified argument listener is nul and that does not make sense.");
        }
        else {
            synchronized (listeners) {
                if (!listeners.contains(listener))
                    listeners.add(listener);
            }
        }
    }

    /**
     * Fires a new <code>PropertyChangeEvent</code> to the
     * <code>PropertyChangeListener</code>s registered with this
     * <code>PropertyChangeNotifier</code> in order to notify about a change in the
     * value of a specific property which had its old value modified to a
     * specific new value. <code>PropertyChangeNotifier</code> does not check
     * whether the specified <code>oldValue</code> and <code>newValue</code> are indeed different.
     *
     * @param property the name of the property of this
     * <code>PropertyChangeNotifier</code> which had its value changed
     * @param oldValue the value of the property with the specified name before the change
     * @param newValue the value of the property with the specified name after the change
     */
    protected void firePropertyChange(String property, Object oldValue, Object newValue)
    {
        PropertyChangeListener[] ls;
        synchronized (listeners) {
            ls = listeners.toArray(new PropertyChangeListener[0]);
        }

        if (ls.length != 0) {
            PropertyChangeEvent ev = new PropertyChangeEvent(
                    getPropertyChangeSource(property, oldValue, newValue),
                    property, oldValue, newValue);

            for (PropertyChangeListener l : ls) {
                try {
                    l.propertyChange(ev);
                } catch (Throwable t) {
                    if (t instanceof InterruptedException) {
                        Thread.currentThread().interrupt();
                    }
                    else if (t instanceof ThreadDeath) {
                        throw (ThreadDeath) t;
                    }
                    else {
                        Timber.w(t, "A PropertyChangeListener threw an exception while handling a PropertyChangeEvent.");
                    }
                }
            }
        }
    }

    /**
     * Gets the <code>Object</code> to be reported as the source of a new
     * <code>PropertyChangeEvent</code> which is to notify the <code>PropertyChangeListener</code>s
     * registered with this <code>PropertyChangeNotifier</code> about the change in the value of a
     * property with a  specific name from a specific old value to a specific new value.
     *
     * @param property the name of the property which had its value changed from
     * the specified old value to the specified new value
     * @param oldValue the value of the property with the specified name before the change
     * @param newValue the value of the property with the specified name after the change
     * @return the <code>Object</code> to be reported as the source of the new
     * <code>PropertyChangeEvent</code> which is to notify the
     * <code>PropertyChangeListener</code>s registered with this
     * <code>PropertyChangeNotifier</code> about the change in the value of the
     * property with the specified name from the specified old value to the specified new value
     */
    protected Object getPropertyChangeSource(String property, Object oldValue, Object newValue)
    {
        return this;
    }

    /**
     * Removes a specific <code>PropertyChangeListener</code> from the list of
     * listeners interested in and notified about changes in the values of the
     * properties of this <code>PropertyChangeNotifer</code>.
     *
     * @param listener a <code>PropertyChangeListener</code> to no longer be
     * notified about changes in the values of the properties of this
     * <code>PropertyChangeNotifier</code>
     */
    public void removePropertyChangeListener(PropertyChangeListener listener)
    {
        if (listener != null) {
            synchronized (listeners) {
                listeners.remove(listener);
            }
        }
    }
}
