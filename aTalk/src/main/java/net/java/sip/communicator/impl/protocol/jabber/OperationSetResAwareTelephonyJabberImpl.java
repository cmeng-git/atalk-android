/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package net.java.sip.communicator.impl.protocol.jabber;

import net.java.sip.communicator.service.protocol.Call;
import net.java.sip.communicator.service.protocol.CallPeer;
import net.java.sip.communicator.service.protocol.Contact;
import net.java.sip.communicator.service.protocol.ContactResource;
import net.java.sip.communicator.service.protocol.OperationFailedException;
import net.java.sip.communicator.service.protocol.OperationSetResourceAwareTelephony;

import org.apache.commons.lang3.StringUtils;

import org.jivesoftware.smackx.jingle.JingleManager;

import org.jxmpp.jid.FullJid;
import org.jxmpp.jid.impl.JidCreate;
import org.jxmpp.stringprep.XmppStringprepException;

import timber.log.Timber;

/**
 * @author Yana Stamcheva
 * @author Eng Chong Meng
 */
public class OperationSetResAwareTelephonyJabberImpl implements OperationSetResourceAwareTelephony {
    /**
     * The <code>OperationSetBasicTelephonyJabberImpl</code> supported by the parent Jabber protocol provider
     */
    private final OperationSetBasicTelephonyJabberImpl jabberTelephony;

    /**
     * Creates an instance of <code>OperationSetResourceAwareTelephonyImpl</code> by specifying the
     * basic telephony operation set.
     *
     * @param basicTelephony the <code>OperationSetBasicTelephonyJabberImpl</code>
     * supported by the parent Jabber protocol provider
     */
    public OperationSetResAwareTelephonyJabberImpl(OperationSetBasicTelephonyJabberImpl basicTelephony) {
        this.jabberTelephony = basicTelephony;
    }

    /**
     * Creates a new <code>Call</code> and invites a specific <code>CallPeer</code> given by her
     * <code>Contact</code> on a specific <code>ContactResource</code> to it.
     *
     * @param callee the address of the callee who we should invite to a new call
     * @param calleeResource the specific resource to which the invite should be sent
     *
     * @return a newly created <code>Call</code>. The specified <code>callee</code> is available in the
     * <code>Call</code> as a <code>CallPeer</code>
     *
     * @throws OperationFailedException with the corresponding code if we fail to create the call
     */
    public Call createCall(Contact callee, ContactResource calleeResource)
            throws OperationFailedException {
        return createCall(callee.getAddress(), calleeResource.getResourceName());
    }

    /**
     * Creates a new <code>Call</code> and invites a specific <code>CallPeer</code>
     * to it given by her <code>String</code> URI.
     *
     * @param callee the address of the callee who we should invite to a new call
     * @param calleeResource the specific resource to which the invite should be sent
     *
     * @return a newly created <code>Call</code>. The specified <code>callee</code>
     * is available in the <code>Call</code> as a <code>CallPeer</code>
     *
     * @throws OperationFailedException with the corresponding code if we fail to create the call
     *
     * @throws OperationFailedException with the corresponding code if we fail to create the call

     */
    public Call createCall(String callee, String calleeResource)
            throws OperationFailedException {
        CallJabberImpl call = new CallJabberImpl(jabberTelephony, JingleManager.randomUuid());

        FullJid fullCalleeUri = null;
        try {
            fullCalleeUri = JidCreate.fullFrom(StringUtils.isEmpty(calleeResource)
                    ? callee : callee + "/" + calleeResource);
        }
        catch (XmppStringprepException e) {
            Timber.w("createCall: %s", e.getMessage());
        }

        CallPeer callPeer = jabberTelephony.createOutgoingCall(call, callee, fullCalleeUri, null);
        if (callPeer == null) {
            throw new OperationFailedException("Failed to create outgoing call because no peer was created",
                    OperationFailedException.INTERNAL_ERROR);
        }
        return callPeer.getCall();
    }
}
