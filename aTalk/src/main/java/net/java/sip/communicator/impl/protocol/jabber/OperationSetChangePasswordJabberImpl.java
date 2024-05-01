/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package net.java.sip.communicator.impl.protocol.jabber;

import net.java.sip.communicator.service.protocol.OperationFailedException;
import net.java.sip.communicator.service.protocol.OperationSetChangePassword;

import org.jivesoftware.smack.SmackException.NoResponseException;
import org.jivesoftware.smack.SmackException.NotConnectedException;
import org.jivesoftware.smack.XMPPException;
import org.jivesoftware.smackx.disco.packet.DiscoverInfo;
import org.jivesoftware.smackx.iqregister.AccountManager;
import org.jxmpp.jid.Jid;
import org.jxmpp.jid.impl.JidCreate;
import org.jxmpp.stringprep.XmppStringprepException;

import timber.log.Timber;

/**
 * A jabber implementation of the password change operation set.
 *
 * @author Boris Grozev
 * @author Eng Chong Meng
 */
public class OperationSetChangePasswordJabberImpl implements OperationSetChangePassword {
    /**
     * The <code>ProtocolProviderService</code> whose password we'll change.
     */
    private final ProtocolProviderServiceJabberImpl mPPS;

    /**
     * Sets the object protocolProvider to the one given.
     *
     * @param protocolProvider the protocolProvider to use.
     */
    OperationSetChangePasswordJabberImpl(ProtocolProviderServiceJabberImpl protocolProvider) {
        mPPS = protocolProvider;
    }

    /**
     * Changes the jabber account password of protocolProvider to newPass.
     *
     * @param newPass the new password.
     *
     * @throws IllegalStateException if the account is not registered.
     * @throws OperationFailedException if the server does not support password changes.
     */
    public void changePassword(String newPass)
            throws IllegalStateException, OperationFailedException {
        AccountManager accountManager = AccountManager.getInstance(mPPS.getConnection());
        try {
            try {
                accountManager.changePassword(newPass);
            } catch (NoResponseException | NotConnectedException | InterruptedException e) {
                e.printStackTrace();
            }
        } catch (XMPPException e) {
            Timber.i(e, "Tried to change jabber password, but the server does not support inband password changes");
            throw new OperationFailedException("In-band password changes not supported",
                    OperationFailedException.NOT_SUPPORTED_OPERATION, e);
        }
    }

    /**
     * Returns true if the server supports password changes. Checks for XEP-0077 (inband registrations) support via discoInfo.
     * It makes sense to return true if something goes wrong i.e. discoInfo == null, because failing later on is not fatal,
     * and registrations are very likely to be supported.
     *
     * @return True if the server supports password changes, false otherwise.
     */
    public boolean supportsPasswordChange() {
        try {
            Jid entityJid = JidCreate.from(mPPS.getAccountID().getService());
            DiscoverInfo discoverInfo = mPPS.getScHelper().discoverInfo(entityJid);
            return (discoverInfo == null) || discoverInfo.containsFeature(ProtocolProviderServiceJabberImpl.URN_REGISTER);
        } catch (XmppStringprepException e) {
            return false;
        }
    }
}
