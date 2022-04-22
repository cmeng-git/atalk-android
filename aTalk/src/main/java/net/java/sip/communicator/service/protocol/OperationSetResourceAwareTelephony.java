/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package net.java.sip.communicator.service.protocol;

/**
 * The <code>OperationSetResourceAwareTelephony</code> defines methods for creating a call toward a
 * specific resource, from which a callee is connected.
 *
 * @author Yana Stamcheva
 * @author Eng Chong Meng
 */
public interface OperationSetResourceAwareTelephony extends OperationSet
{
    /**
     * Creates a new <code>Call</code> and invites a specific <code>CallPeer</code> given by her
     * <code>Contact</code> on a specific <code>ContactResource</code> to it.
     *
     * @param callee the address of the callee who we should invite to a new call
     * @param calleeResource the specific resource to which the invite should be sent
     * @return a newly created <code>Call</code>. The specified <code>callee</code> is available in the
     * <code>Call</code> as a <code>CallPeer</code>
     * @throws OperationFailedException with the corresponding code if we fail to create the call
     */
    Call createCall(Contact callee, ContactResource calleeResource)
            throws OperationFailedException;

    /**
     * Creates a new <code>Call</code> and invites a specific <code>CallPeer</code> given by her
     * <code>Contact</code> on a specific <code>ContactResource</code> to it.
     *
     * @param callee the address of the callee who we should invite to a new call
     * @param calleeResource the specific resource to which the invite should be sent
     * @return a newly created <code>Call</code>. The specified <code>callee</code> is available in the
     * <code>Call</code> as a <code>CallPeer</code>
     * @throws OperationFailedException with the corresponding code if we fail to create the call
     */
    Call createCall(String callee, String calleeResource)
            throws OperationFailedException;

    /**
     * Creates a new <code>Call</code> and invites a specific <code>CallPeer</code> to it given by her
     * <code>String</code> URI.
     *
     * @param uri the address of the callee who we should invite to a new <code>Call</code>
     * @param calleeResource the specific resource to which the invite should be sent
     * @param conference the <code>CallConference</code> in which the newly-created <code>Call</code> is to participate
     * @return a newly created <code>Call</code>. The specified <code>callee</code> is available in the
     * <code>Call</code> as a <code>CallPeer</code>
     * @throws OperationFailedException with the corresponding code if we fail to create the call
     */
    Call createCall(String uri, String calleeResource, CallConference conference)
            throws OperationFailedException;

    /**
     * Creates a new <code>Call</code> and invites a specific <code>CallPeer</code> given by her
     * <code>Contact</code> to it.
     *
     * @param callee the address of the callee who we should invite to a new call
     * @param calleeResource the specific resource to which the invite should be sent
     * @param conference the <code>CallConference</code> in which the newly-created <code>Call</code> is to participate
     * @return a newly created <code>Call</code>. The specified <code>callee</code> is available in the
     * <code>Call</code> as a <code>CallPeer</code>
     * @throws OperationFailedException with the corresponding code if we fail to create the call
     */
    Call createCall(Contact callee, ContactResource calleeResource, CallConference conference)
            throws OperationFailedException;
}
