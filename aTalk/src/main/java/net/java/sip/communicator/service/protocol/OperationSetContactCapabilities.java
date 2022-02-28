/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package net.java.sip.communicator.service.protocol;

import net.java.sip.communicator.service.protocol.event.ContactCapabilitiesListener;

import java.util.Map;

/**
 * Represents an <code>OperationSet</code> to query the <code>OperationSet</code>s supported for a specific
 * <code>Contact</code>. The <code>OperationSet</code>s reported as supported for a specific
 * <code>Contact</code> are considered by the associated protocol provider to be capabilities possessed
 * by the <code>Contact</code> in question.
 *
 * @author Lyubomir Marinov
 * @author Eng Chong Meng
 */
public interface OperationSetContactCapabilities extends OperationSet
{

    /**
     * Registers a specific <code>ContactCapabilitiesListener</code> to be notified about changes in the
     * list of <code>OperationSet</code> capabilities of <code>Contact</code>s. If the specified
     * <code>listener</code> has already been registered, adding it again has no effect.
     *
     * @param listener the <code>ContactCapabilitiesListener</code> which is to be notified about changes in the
     * list of <code>OperationSet</code> capabilities of <code>Contact</code>s
     */
    void addContactCapabilitiesListener(ContactCapabilitiesListener listener);

    /**
     * Gets the <code>OperationSet</code> corresponding to the specified <code>Class</code> and supported by
     * the specified <code>Contact</code>. If the returned value is non-<code>null</code>, it indicates that
     * the <code>Contact</code> is considered by the associated protocol provider to possess the
     * <code>opsetClass</code> capability. Otherwise, the associated protocol provider considers
     * <code>contact</code> to not have the <code>opsetClass</code> capability.
     *
     * @param <T> the type extending <code>OperationSet</code> for which the specified <code>contact</code> is
     * to be checked whether it possesses it as a capability
     * @param contact the <code>Contact</code> for which the <code>opsetClass</code> capability is to be queried
     * @param opsetClass the <code>OperationSet</code> <code>Class</code> for which the specified <code>contact</code> is
     * to be checked whether it possesses it as a capability
     * @return the <code>OperationSet</code> corresponding to the specified <code>opsetClass</code> which is
     * considered by the associated protocol provider to be possessed as a capability by the
     * specified <code>contact</code>; otherwise, <code>null</code>
     */
    <T extends OperationSet> T getOperationSet(Contact contact, Class<T> opsetClass);

    /**
     * Gets the <code>OperationSet</code>s supported by a specific <code>Contact</code>. The returned
     * <code>OperationSet</code>s are considered by the associated protocol provider to capabilities
     * possessed by the specified <code>contact</code>.
     *
     * @param contact the <code>Contact</code> for which the supported <code>OperationSet</code> capabilities are to
     * be retrieved
     * @return a <code>Map</code> listing the <code>OperationSet</code>s considered by the associated
     * protocol provider to be supported by the specified <code>contact</code> (i.e. to be
     * possessed as capabilities). Each supported <code>OperationSet</code> capability is
     * represented by a <code>Map.Entry</code> with key equal to the <code>OperationSet</code> class
     * name and value equal to the respective <code>OperationSet</code> instance
     */
    Map<String, OperationSet> getSupportedOperationSets(Contact contact);

    /**
     * Unregisters a specific <code>ContactCapabilitiesListener</code> to no longer be notified about
     * changes in the list of <code>OperationSet</code> capabilities of <code>Contact</code>s. If the
     * specified <code>listener</code> has already been unregistered or has never been registered,
     * removing it has no effect.
     *
     * @param listener the <code>ContactCapabilitiesListener</code> which is to no longer be notified about
     * changes in the list of <code>OperationSet</code> capabilities of <code>Contact</code>s
     */
    void removeContactCapabilitiesListener(ContactCapabilitiesListener listener);
}
