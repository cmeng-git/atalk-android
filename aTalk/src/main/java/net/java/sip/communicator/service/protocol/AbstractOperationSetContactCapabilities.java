/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package net.java.sip.communicator.service.protocol;

import net.java.sip.communicator.service.protocol.event.ContactCapabilitiesEvent;
import net.java.sip.communicator.service.protocol.event.ContactCapabilitiesListener;

import org.jxmpp.jid.Jid;

import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import timber.log.Timber;

/**
 * Represents a default implementation of <code>OperationSetContactCapabilities</code> which attempts to
 * make it easier for implementers to provide complete solutions while focusing on
 * implementation-specific functionality.
 *
 * @param <T> the type of the <code>ProtocolProviderService</code> implementation providing the
 * <code>AbstractOperationSetContactCapabilities</code> implementation
 *
 * @author Lyubomir Marinov
 * @author Eng Chong Meng
 */
public abstract class AbstractOperationSetContactCapabilities<T extends ProtocolProviderService>
        implements OperationSetContactCapabilities
{
    /**
     * The list of <code>ContactCapabilitiesListener</code>s registered to be notified about changes in
     * the list of <code>OperationSet</code> capabilities of <code>Contact</code>s.
     */
    private final List<ContactCapabilitiesListener> contactCapabilitiesListeners = new LinkedList<>();

    /**
     * The <code>ProtocolProviderService</code> which provides this <code>OperationSetContactCapabilities</code>.
     */
    protected final T parentProvider;

    /**
     * Initializes a new <code>AbstractOperationSetContactCapabilities</code> instance which is to be
     * provided by a specific <code>ProtocolProviderService</code> implementation.
     *
     * @param parentProvider the <code>ProtocolProviderService</code> implementation which will provide the new instance
     */
    protected AbstractOperationSetContactCapabilities(T parentProvider)
    {
        if (parentProvider == null)
            throw new NullPointerException("parentProvider");

        this.parentProvider = parentProvider;
    }

    /**
     * Registers a specific <code>ContactCapabilitiesListener</code> to be notified about changes in
     * the list of <code>OperationSet</code> capabilities of <code>Contact</code>s. If the specified
     * <code>listener</code> has already been registered, adding it again has no effect.
     *
     * @param listener the <code>ContactCapabilitiesListener</code> which is to be notified about changes in the
     * list of <code>OperationSet</code> capabilities of <code>Contact</code>s
     * @see OperationSetContactCapabilities#addContactCapabilitiesListener (ContactCapabilitiesListener)
     */
    public void addContactCapabilitiesListener(ContactCapabilitiesListener listener)
    {
        if (listener == null)
            throw new NullPointerException("listener");

        synchronized (contactCapabilitiesListeners) {
            if (!contactCapabilitiesListeners.contains(listener))
                contactCapabilitiesListeners.add(listener);
        }
    }

    /**
     * Fires a new <code>ContactCapabilitiesEvent</code> to notify the registered <code>ContactCapabilitiesListener</code>s
     * that a specific <code>Contact</code> has changed its list of <code>OperationSet</code> capabilities.
     *
     * @param sourceContact the <code>Contact</code> which is the source/cause of the event to be fired
     * @param jid the contact fullJid
     * @param opSets the new operation sets for the given source contact
     */
    protected void fireContactCapabilitiesEvent(Contact sourceContact, Jid jid, Map<String, ? extends OperationSet> opSets)
    {
        ContactCapabilitiesListener[] listeners;
        synchronized (contactCapabilitiesListeners) {
            listeners = contactCapabilitiesListeners.toArray(new ContactCapabilitiesListener[0]);
        }
        if (listeners.length != 0) {
            ContactCapabilitiesEvent event = new ContactCapabilitiesEvent(sourceContact, jid, opSets);

            for (ContactCapabilitiesListener listener : listeners) {
                if (jid != null) {
                    listener.supportedOperationSetsChanged(event);
                }
                else {
                    Timber.w(new IllegalArgumentException("Cannot fire ContactCapabilitiesEvent with Jid: " + jid));
                }
            }
        }
    }

    /**
     * Gets the <code>OperationSet</code> corresponding to the specified <code>Class</code> and
     * supported by the specified <code>Contact</code>. If the returned value is non-<code>null</code>,
     * it indicates  that the <code>Contact</code> is considered by the associated protocol provider
     * to possess the <code>opsetClass</code> capability. Otherwise, the associated protocol provider
     * considers <code>contact</code> to not have the <code>opsetClass</code> capability.
     * <code>AbstractOperationSetContactCapabilities</code> looks for the name of the specified
     * <code>opsetClass</code> in the <code>Map</code> returned by
     * {@link #getSupportedOperationSets(Contact)} and returns the associated <code>OperationSet</code>.
     * Since the implementation is suboptimal due to the temporary <code>Map</code> allocations and
     * lookups, extenders are advised to override
     * {@link #getOperationSet(Contact, Class, boolean)}.
     *
     * @param <U> the type extending <code>OperationSet</code> for which the specified <code>contact</code> is
     * to be checked whether it possesses it as a capability
     * @param contact the <code>Contact</code> for which the <code>opsetClass</code> capability is to be queried
     * @param opsetClass the <code>OperationSet</code> <code>Class</code> for which the specified <code>contact</code> is
     * to be checked whether it possesses it as a capability
     * @return the <code>OperationSet</code> corresponding to the specified <code>opsetClass</code>
     * which is considered by the associated protocol provider to be possessed as a capability by
     * the specified <code>contact</code>; otherwise, <code>null</code>
     * @see OperationSetContactCapabilities#getOperationSet(Contact, Class)
     */
    public <U extends OperationSet> U getOperationSet(Contact contact, Class<U> opsetClass)
    {
        return getOperationSet(contact, opsetClass, isOnline(contact));
    }

    /**
     * Gets the <code>OperationSet</code> corresponding to the specified <code>Class</code> and
     * supported by the specified <code>Contact</code>. If the returned value is non-<code>null</code>,
     * it indicates that the <code>Contact</code> is considered by the associated protocol provider
     * to possess the <code>opsetClass</code> capability. Otherwise, the associated protocol provider
     * considers <code>contact</code> to not have the <code>opsetClass</code> capability.
     * <code>AbstractOperationSetContactCapabilities</code> looks for the name of the specified
     * <code>opsetClass</code> in the <code>Map</code> returned by
     * {@link #getSupportedOperationSets(Contact)} and returns the associated <code>OperationSet</code>.
     * Since the implementation is suboptimal due to the temporary <code>Map</code> allocations and
     * lookups, extenders are advised to override.
     *
     * @param <U> the type extending <code>OperationSet</code> for which the specified <code>contact</code> is
     * to be checked whether it possesses it as a capability
     * @param contact the <code>Contact</code> for which the <code>opsetClass</code> capability is to be queried
     * @param opsetClass the <code>OperationSet</code> <code>Class</code> for which the specified <code>contact</code> is
     * to be checked whether it possesses it as a capability
     * @param online <code>true</code> if <code>contact</code> is online; otherwise, <code>false</code>
     * @return the <code>OperationSet</code> corresponding to the specified <code>opsetClass</code>
     * which is considered by the associated protocol provider to be possessed as a capability by
     * the specified <code>contact</code>; otherwise, <code>null</code>
     * @see OperationSetContactCapabilities#getOperationSet(Contact, Class)
     */
    @SuppressWarnings("unchecked")
    protected <U extends OperationSet> U getOperationSet(Contact contact, Class<U> opsetClass, boolean online)
    {
        Map<String, OperationSet> supportedOperationSets = getSupportedOperationSets(contact, online);

        if (supportedOperationSets != null) {
            OperationSet opset = supportedOperationSets.get(opsetClass.getName());

            if (opsetClass.isInstance(opset))
                return (U) opset;
        }
        return null;
    }

    /**
     * Gets the <code>OperationSet</code>s supported by a specific <code>Contact</code>. The returned
     * <code>OperationSet</code>s are considered by the associated protocol provider to capabilities
     * possessed by the specified <code>contact</code>. The default implementation returns the
     * result of calling {@link ProtocolProviderService#getSupportedOperationSets()} on the
     * associated <code>ProtocolProviderService</code> implementation. Extenders have to override the
     * default implementation of {@link #getSupportedOperationSets(Contact, boolean)} in order to
     * provide actual capability detection for the specified <code>contact</code>.
     *
     * @param contact the <code>Contact</code> for which the supported <code>OperationSet</code> capabilities are to be retrieved
     * @return a <code>Map</code> listing the <code>OperationSet</code>s considered by the associated
     * protocol provider to be supported by the specified <code>contact</code> (i.e. to be
     * possessed as capabilities). Each supported <code>OperationSet</code> capability is
     * represented by a <code>Map.Entry</code> with key equal to the <code>OperationSet</code> class
     * name and value equal to the respective <code>OperationSet</code> instance
     * @see OperationSetContactCapabilities#getSupportedOperationSets(Contact)
     */
    public Map<String, OperationSet> getSupportedOperationSets(Contact contact)
    {
        return getSupportedOperationSets(contact, isOnline(contact));
    }

    /**
     * Gets the <code>OperationSet</code>s supported by a specific <code>Contact</code>. The returned
     * <code>OperationSet</code>s are considered by the associated protocol provider to capabilities
     * possessed by the specified <code>contact</code>. The default implementation returns the
     * result of calling {@link ProtocolProviderService#getSupportedOperationSets()} on the
     * associated <code>ProtocolProviderService</code> implementation. Extenders have to override the
     * default implementation in order to provide actual capability detection for the specified <code>contact</code>.
     *
     * @param contact the <code>Contact</code> for which the supported <code>OperationSet</code> capabilities are to
     * be retrieved
     * @param online <code>true</code> if <code>contact</code> is online; otherwise, <code>false</code>
     * @return a <code>Map</code> listing the <code>OperationSet</code>s considered by the associated
     * protocol provider to be supported by the specified <code>contact</code> (i.e. to be
     * possessed as capabilities). Each supported <code>OperationSet</code> capability is
     * represented by a <code>Map.Entry</code> with key equal to the <code>OperationSet</code> class
     * name and value equal to the respective <code>OperationSet</code> instance
     * @see OperationSetContactCapabilities#getSupportedOperationSets(Contact)
     */
    protected Map<String, OperationSet> getSupportedOperationSets(Contact contact, boolean online)
    {
        return parentProvider.getSupportedOperationSets();
    }

    /**
     * Determines whether a specific <code>Contact</code> is online (in contrast to offline).
     *
     * @param contact the <code>Contact</code> which is to be determines whether it is online
     * @return <code>true</code> if the specified <code>contact</code> is online; otherwise, <code>false</code>
     */
    protected boolean isOnline(Contact contact)
    {
        OperationSetPresence opsetPresence = parentProvider.getOperationSet(OperationSetPresence.class);

        if (opsetPresence == null) {
            /*
             * Presence is not implemented so we cannot really know and thus we'll give it the
             * benefit of the doubt and declare it online.
             */
            return true;
        }
        else {
            PresenceStatus presenceStatus = null;
            Throwable exception = null;

            try {
                presenceStatus = opsetPresence.queryContactStatus(contact.getJid().asBareJid());
            } catch (IllegalArgumentException | IllegalStateException | OperationFailedException iaex) {
                exception = iaex;
            }

            if (presenceStatus == null)
                presenceStatus = contact.getPresenceStatus();

            if (presenceStatus == null) {
                if ((exception != null)) {
                    Timber.d(exception, "Failed to query PresenceStatus of Contact %s", contact);
                }
                /*
                 * For whatever reason the PresenceStatus wasn't retrieved, it's a fact that
                 * presence was advertised and the contacts wasn't reported online.
                 */
                return false;
            }
            else
                return presenceStatus.isOnline();
        }
    }

    /**
     * Unregisters a specific <code>ContactCapabilitiesListener</code> to no longer be notified about
     * changes in the list of <code>OperationSet</code> capabilities of <code>Contact</code>s. If the specified
     * <code>listener</code> has already been unregistered or has never been registered, removing it has no effect.
     *
     * @param listener the <code>ContactCapabilitiesListener</code> which is to no longer be notified about
     * changes in the list of <code>OperationSet</code> capabilities of <code>Contact</code>s
     * @see OperationSetContactCapabilities#removeContactCapabilitiesListener (ContactCapabilitiesListener)
     */
    public void removeContactCapabilitiesListener(ContactCapabilitiesListener listener)
    {
        if (listener != null) {
            synchronized (contactCapabilitiesListeners) {
                contactCapabilitiesListeners.remove(listener);
            }
        }
    }
}
