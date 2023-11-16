/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package net.java.sip.communicator.impl.protocol.jabber;

import net.java.sip.communicator.service.protocol.AuthorizationRequest;
import net.java.sip.communicator.service.protocol.Contact;
import net.java.sip.communicator.service.protocol.OperationFailedException;
import net.java.sip.communicator.service.protocol.OperationSetExtendedAuthorizations;

import org.jivesoftware.smack.SmackException.NotConnectedException;
import org.jivesoftware.smack.XMPPConnection;
import org.jivesoftware.smack.packet.Presence;
import org.jivesoftware.smack.roster.RosterEntry;
import org.jivesoftware.smack.roster.packet.RosterPacket;

/**
 * Extended authorization implementation for jabber provider.
 *
 * @author Damian Minkov
 * @author Eng Chong Meng
 */
public class OperationSetExtendedAuthorizationsJabberImpl implements OperationSetExtendedAuthorizations
{
    /**
     * A reference to the persistent presence operation set that we use to match incoming messages
     * to <code>Contact</code>s and vice versa.
     */
    private OperationSetPersistentPresenceJabberImpl opSetPersPresence = null;

    /**
     * The parent provider.
     */
    private ProtocolProviderServiceJabberImpl parentProvider;

    /**
     * Creates OperationSetExtendedAuthorizations.
     *
     * @param opSetPersPresence the presence opset.
     * @param provider the parent provider
     */
    OperationSetExtendedAuthorizationsJabberImpl(ProtocolProviderServiceJabberImpl provider,
            OperationSetPersistentPresenceJabberImpl opSetPersPresence)
    {
        this.opSetPersPresence = opSetPersPresence;
        this.parentProvider = provider;
    }

    /**
     * Send a positive authorization to <code>contact</code> thus allowing them to add us to their
     * contact list without needing to first request an authorization.
     *
     * @param contact the <code>Contact</code> whom we're granting authorization prior to receiving a request.
     * @throws OperationFailedException if we fail sending the authorization.
     */
    public void explicitAuthorize(Contact contact)
            throws OperationFailedException
    {
        opSetPersPresence.assertConnected();

        if (!(contact instanceof ContactJabberImpl))
            throw new IllegalArgumentException("The specified contact is not an jabber contact." + contact);

        XMPPConnection connection = parentProvider.getConnection();
        Presence responsePacket = connection.getStanzaFactory().buildPresenceStanza()
                .ofType(Presence.Type.subscribed).build();
        responsePacket.setTo(contact.getJid());
        try {
            connection.sendStanza(responsePacket);
        } catch (NotConnectedException | InterruptedException e) {
            throw new OperationFailedException("Could not send authorize",
                OperationFailedException.NETWORK_FAILURE, e);
        }
    }

    /**
     * Send an authorization request, requesting <code>contact</code> to add them to our contact list?
     *
     * @param request the <code>AuthorizationRequest</code> that we'd like the protocol provider to send to <code>contact</code>.
     * @param contact the <code>Contact</code> who we'd be asking for an authorization.
     * @throws OperationFailedException if we fail sending the authorization request.
     */
    public void reRequestAuthorization(AuthorizationRequest request, Contact contact)
            throws OperationFailedException
    {
        opSetPersPresence.assertConnected();
        if (!(contact instanceof ContactJabberImpl))
            throw new IllegalArgumentException("The specified contact is not an jabber contact: " + contact);

        XMPPConnection connection = parentProvider.getConnection();
        Presence responsePacket = connection.getStanzaFactory().buildPresenceStanza()
                .ofType(Presence.Type.subscribed).build();
        responsePacket.setTo(contact.getJid());
        try {
            connection.sendStanza(responsePacket);
        } catch (NotConnectedException | InterruptedException e) {
            throw new OperationFailedException("Could not send subscribe packet",
                OperationFailedException.NETWORK_FAILURE, e);
        }
    }

    /**
     * Returns the subscription status for the <code>contact</code> or if not available returns null.
     *
     * @param contact the contact to query for subscription status.
     * @return the subscription status for the <code>contact</code> or if not available returns null.
     */
    public SubscriptionStatus getSubscriptionStatus(Contact contact)
    {
        if (!(contact instanceof ContactJabberImpl))
            throw new IllegalArgumentException("The specified contact is not an jabber contact." + contact);

        RosterEntry entry = ((ContactJabberImpl) contact).getSourceEntry();
        if (entry != null) {
            if (((entry.getType() == RosterPacket.ItemType.none) || (entry.getType() == RosterPacket.ItemType.from))
                    && entry.isSubscriptionPending()) {
                return SubscriptionStatus.SubscriptionPending;
            }
            else if (entry.getType() == RosterPacket.ItemType.to || entry.getType() == RosterPacket.ItemType.both)
                return SubscriptionStatus.Subscribed;
            else
                return SubscriptionStatus.NotSubscribed;
        }
        return null;
    }
}
