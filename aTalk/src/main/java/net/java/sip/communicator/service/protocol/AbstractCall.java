/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package net.java.sip.communicator.service.protocol;

import net.java.sip.communicator.service.protocol.event.CallPeerEvent;

import java.beans.PropertyChangeListener;
import java.beans.PropertyChangeSupport;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;

/**
 * Provides implementations for some of the methods in the <code>Call</code> abstract class to facilitate implementations.
 *
 * @param <T> the peer extension class like for example <code>CallPeerSipImpl</code> or <code>CallPeerJabberImpl</code>
 * @param <U> the provider extension class like for example <code>ProtocolProviderServiceSipImpl</code> or
 * <code>ProtocolProviderServiceJabberImpl</code>
 * @author Emil Ivov
 * @author Lyubomir Marinov
 * @author Eng Chong Meng
 */
public abstract class AbstractCall<T extends CallPeer, U extends ProtocolProviderService> extends Call
{
    /**
     * The list of <code>CallPeer</code>s of this <code>Call</code>. It is implemented as a copy-on-write
     * storage in order to optimize the implementation of {@link Call#getCallPeers()}. It represents
     * private state which is to not be exposed to outsiders. An unmodifiable view which may safely
     * be exposed to outsiders without the danger of <code>ConcurrentModificationException</code> is
     * {@link #unmodifiableCallPeers}.
     */
    private List<T> callPeers;

    /**
     * The <code>Object</code> which is used to synchronize the access to {@link #callPeers} and
     * {@link #unmodifiableCallPeers}.
     */
    private final Object callPeersSyncRoot = new Object();

    /**
     * The <code>PropertyChangeSupport</code> which helps this instance with <code>PropertyChangeListener</code>s.
     */
    private final PropertyChangeSupport propertyChangeSupport = new PropertyChangeSupport(this);

    /**
     * An unmodifiable view of {@link #callPeers}. It may safely be exposed to outsiders without the
     * danger of <code>ConcurrentModificationException</code> and thus optimizes the implementation of
     * {@link Call#getCallPeers()}.
     */
    private List<T> unmodifiableCallPeers;

    /**
     * Creates a new Call instance.
     *
     * @param sourceProvider the proto provider that created us.
     * @param sid the Jingle session-initiate id if provided.
     */
    protected AbstractCall(U sourceProvider, String sid)
    {
        super(sourceProvider, sid);

        callPeers = Collections.emptyList();
        unmodifiableCallPeers = Collections.unmodifiableList(callPeers);
    }

    /**
     * {@inheritDoc}
     *
     * Delegates to {@link #propertyChangeSupport}.
     */
    @Override
    public void addPropertyChangeListener(PropertyChangeListener listener)
    {
        propertyChangeSupport.addPropertyChangeListener(listener);
    }

    /**
     * Adds a specific <code>CallPeer</code> to the list of <code>CallPeer</code>s of this <code>Call</code> if
     * the list does not contain it; otherwise, does nothing. Does not fire
     * {@link CallPeerEvent#CALL_PEER_ADDED}.
     * <p>
     * The method is named <code>doAddCallPeer</code> and not <code>addCallPeer</code> because, at the time
     * of its introduction, multiple extenders have already defined an <code>addCallPeer</code> method
     * with the same argument but with no return value.
     * </p>
     *
     * @param callPeer the <code>CallPeer</code> to be added to the list of <code>CallPeer</code>s of this <code>Call</code>
     * @return <code>true</code> if the list of <code>CallPeer</code>s of this <code>Call</code> was modified as
     * a result of the execution of the method; otherwise, <code>false</code>
     * @throws NullPointerException if <code>callPeer</code> is <code>null</code>
     */
    protected boolean doAddCallPeer(T callPeer)
    {
        if (callPeer == null)
            throw new NullPointerException("callPeer");

        synchronized (callPeersSyncRoot) {
            if (callPeers.contains(callPeer))
                return false;
            else {
                /*
                 * The List of CallPeers of this Call is implemented as a copy-on-write storage in
                 * order to optimize the implementation of the Call.getCallPeers() method.
                 */

                List<T> newCallPeers = new ArrayList<T>(callPeers);

                if (newCallPeers.add(callPeer)) {
                    callPeers = newCallPeers;
                    unmodifiableCallPeers = Collections.unmodifiableList(callPeers);
                    return true;
                }
                else
                    return false;
            }
        }
    }

    /**
     * Removes a specific <code>CallPeer</code> from the list of <code>CallPeer</code>s of this
     * <code>Call</code> if the list does contain it; otherwise, does nothing. Does not fire
     * {@link CallPeerEvent#CALL_PEER_REMOVED}.
     * <p>
     * The method is named <code>doRemoveCallPeer</code> and not <code>removeCallPeer</code> because, at the
     * time of its introduction, multiple extenders have already defined a <code>removeCallPeer</code>
     * method with the same argument but with no return value.
     * </p>
     *
     * @param callPeer the <code>CallPeer</code> to be removed from the list of <code>CallPeer</code>s of this <code>Call</code>
     * @return <code>true</code> if the list of <code>CallPeer</code>s of this <code>Call</code> was modified as
     * a result of the execution of the method; otherwise, <code>false</code>
     */
    protected boolean doRemoveCallPeer(T callPeer)
    {
        synchronized (callPeersSyncRoot) {
            /*
             * The List of CallPeers of this Call is implemented as a copy-on-write storage in order
             * to optimize the implementation of the Call.getCallPeers() method.
             */

            List<T> newCallPeers = new ArrayList<T>(callPeers);

            if (newCallPeers.remove(callPeer)) {
                callPeers = newCallPeers;
                unmodifiableCallPeers = Collections.unmodifiableList(callPeers);
                return true;
            }
            else
                return false;
        }
    }

    /**
     * {@inheritDoc}
     *
     * Delegates to {@link #propertyChangeSupport}.
     */
    @Override
    protected void firePropertyChange(String property, Object oldValue, Object newValue)
    {
        propertyChangeSupport.firePropertyChange(property, oldValue, newValue);
    }

    /**
     * Returns the number of peers currently associated with this call.
     *
     * @return an <code>int</code> indicating the number of peers currently associated with this call.
     */
    @Override
    public int getCallPeerCount()
    {
        return getCallPeerList().size();
    }

    /**
     * Gets an unmodifiable <code>List</code> of the <code>CallPeer</code>s of this <code>Call</code>. The implementation of
     * {@link Call#getCallPeers()} returns an <code>Iterator</code> over the same <code>List</code>.
     *
     * @return an unmodifiable <code>List</code> of the <code>CallPeer</code>s of this <code>Call</code>
     */
    public List<T> getCallPeerList()
    {
        synchronized (callPeersSyncRoot) {
            return unmodifiableCallPeers;
        }
    }

    /**
     * Returns an <code>Iterator</code> over the (list of) <code>CallPeer</code>s of this <code>Call</code>. The
     * returned <code>Iterator</code> operates over the <code>List</code> returned by {@link #getCallPeerList()}.
     *
     * @return an <code>Iterator</code> over the (list of) <code>CallPeer</code>s of this <code>Call</code>
     */
    @Override
    public Iterator<T> getCallPeers()
    {
        return getCallPeerList().iterator();
    }

    /**
     * Returns a reference to the <code>ProtocolProviderService</code> instance that created this call.
     *
     * @return the <code>ProtocolProviderService</code> that created this call.
     */
    @Override
    @SuppressWarnings("unchecked")
    public U getProtocolProvider()
    {
        return (U) super.getProtocolProvider();
    }

    /**
     * {@inheritDoc}
     *
     * Delegates to {@link #propertyChangeSupport}.
     */
    @Override
    public void removePropertyChangeListener(PropertyChangeListener listener)
    {
        propertyChangeSupport.removePropertyChangeListener(listener);
    }
}
