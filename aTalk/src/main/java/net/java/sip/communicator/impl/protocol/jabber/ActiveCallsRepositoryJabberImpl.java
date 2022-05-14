/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
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
package net.java.sip.communicator.impl.protocol.jabber;

import net.java.sip.communicator.service.protocol.ActiveCallsRepository;
import net.java.sip.communicator.service.protocol.Call;
import net.java.sip.communicator.service.protocol.event.CallChangeEvent;

import java.util.Iterator;

/**
 * Keeps a list of all calls currently active and maintained by this protocol
 * provider. Offers methods for finding a call by its ID, peer session and others.
 *
 * @author Emil Ivov
 * @author Symphorien Wanko
 * @author Vincent Lucas
 */
public class ActiveCallsRepositoryJabberImpl extends ActiveCallsRepository<CallJabberImpl, OperationSetBasicTelephonyJabberImpl>
{
    /**
     * It's where we store all active calls
     *
     * @param opSet the <code>OperationSetBasicTelphony</code> instance which has
     * been used to create calls in this repository
     */
    public ActiveCallsRepositoryJabberImpl(OperationSetBasicTelephonyJabberImpl opSet)
    {
        super(opSet);
    }

    /**
     * Returns the {@link CallJabberImpl} containing a {@link
     * CallPeerJabberImpl} whose corresponding jingle session has the specified jingle <code>sid</code>.
     *
     * @param sid the jingle <code>sid</code> we're looking for.
     * @return the {@link CallJabberImpl} containing the peer with the
     * specified <code>sid</code> or <code>null</code> if we couldn't find one matching it.
     */
    public CallJabberImpl findBySid(String sid)
    {
        Iterator<CallJabberImpl> calls = getActiveCalls();
        while (calls.hasNext()) {
            CallJabberImpl call = calls.next();
            if (call.containsSid(sid))
                return call;
        }
        return null;
    }

    /**
     * Returns the <code>Call</code> with ID equal to <code>callid</code>.
     *
     * @param callid the ID to search for
     * @return the <code>Call</code> with ID equal to <code>callid</code>.
     */
    public CallJabberImpl findByCallId(String callid)
    {
        Iterator<CallJabberImpl> calls = getActiveCalls();
        while (calls.hasNext()) {
            CallJabberImpl call = calls.next();
            if (call.getCallId().equals(callid))
                return call;
        }
        return null;
    }

    /**
     * Returns the {@link CallPeerJabberImpl} whose jingle session has the specified jingle <code>sid</code>.
     *
     * @param sid the jingle <code>sid</code> we're looking for.
     * @return the {@link CallPeerJabberImpl} with the specified <code>sid</code>
     * or <code>null</code> if we couldn't find one matching it.
     */
    public CallPeerJabberImpl findCallPeerBySid(String sid)
    {
        Iterator<CallJabberImpl> calls = getActiveCalls();
        while (calls.hasNext()) {
            CallJabberImpl call = calls.next();
            CallPeerJabberImpl peer = call.getPeerBySid(sid);
            if (peer != null)
                return peer;
        }
        return null;
    }

    /**
     * Returns the {@link CallPeerJabberImpl} whose session-initiate's stanzaId has the specified IQ <code>id</code>.
     *
     * @param stanzaId the IQ <code>id</code> we're looking for.
     * @return the {@link CallPeerJabberImpl} with the specified
     * <code>stanzaId</code> or <code>null</code> if we couldn't find one matching it.
     */
    public CallPeerJabberImpl findCallPeerByJingleIQStanzaId(String stanzaId)
    {
        Iterator<CallJabberImpl> calls = getActiveCalls();
        while (calls.hasNext()) {
            CallJabberImpl call = calls.next();
            CallPeerJabberImpl peer = call.getPeerByJingleIQStanzaId(stanzaId);
            if (peer != null)
                return peer;
        }
        return null;
    }

    /**
     * Creates and dispatches a <code>CallEvent</code> notifying registered
     * listeners that an event with id <code>eventID</code> has occurred on <code>sourceCall</code>.
     *
     * @param eventID the ID of the event to dispatch
     * @param sourceCall the call on which the event has occurred
     * @param cause the <code>CallChangeEvent</code>, if any, which is the cause
     * that necessitated a new <code>CallEvent</code> to be fired
     * @see ActiveCallsRepository#fireCallEvent(int, Call, CallChangeEvent)
     */
    @Override
    protected void fireCallEvent(int eventID, Call sourceCall, CallChangeEvent cause)
    {
        parentOperationSet.fireCallEvent(eventID, sourceCall);
    }
}
