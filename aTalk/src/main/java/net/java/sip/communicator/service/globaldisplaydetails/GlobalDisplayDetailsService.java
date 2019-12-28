/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package net.java.sip.communicator.service.globaldisplaydetails;

import net.java.sip.communicator.service.globaldisplaydetails.event.GlobalDisplayDetailsListener;
import net.java.sip.communicator.service.protocol.ProtocolProviderService;

/**
 * The <tt>GlobalDisplayNameService</tt> offers generic access to a global display name and an
 * avatar for the local user. It could be used to show or set the local user display name or avatar.
 * <p>
 * A global display name implementation could determine the information by going through all
 * different accounts' server stored information or by taking into account a provisioned display
 * name if any is available or choose any other approach.
 *
 * @author Yana Stamcheva
 * @author Hristo Terezov
 * @author Eng Chong Meng
 */
public interface GlobalDisplayDetailsService
{
    /**
     * Returns default display name for the given provider or the global display name.
     *
     * @param pps the given protocol provider service
     * @return default display name.
     */
    String getDisplayName(ProtocolProviderService pps);

    /**
     * @param provider get avatar of the specified ProtocolProviderService
     * @return the global avatar for the local user. the byte array representing the avatar to set
     */
    byte[] getDisplayAvatar(ProtocolProviderService provider);

    /**
     * Adds the given <tt>GlobalDisplayDetailsListener</tt> to listen for change events concerning
     * the global display details.
     *
     * @param l the <tt>GlobalDisplayDetailsListener</tt> to add
     */
    void addGlobalDisplayDetailsListener(GlobalDisplayDetailsListener l);

    /**
     * Removes the given <tt>GlobalDisplayDetailsListener</tt> listening for change events
     * concerning the global display details.
     *
     * @param l the <tt>GlobalDisplayDetailsListener</tt> to remove
     */
    void removeGlobalDisplayDetailsListener(GlobalDisplayDetailsListener l);
}