/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
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
package net.java.sip.communicator.service.callhistory;

import net.java.sip.communicator.service.protocol.CallPeerState;

import org.jxmpp.jid.BareJid;
import org.jxmpp.jid.Jid;
import org.jxmpp.jid.impl.JidCreate;
import org.jxmpp.stringprep.XmppStringprepException;

import java.util.Date;

/**
 * Structure used for encapsulating data when writing or reading Call History Data. Also These
 * records are used for returning data from the Call History Service
 *
 * @author Damian Minkov
 * @author Hristo Terezov
 * @author Eng Chong Meng
 */
public class CallPeerRecord
{
    /**
     * The peer address - entityFullJid or callParticipantID.
     */
    protected String peerAddress = null;

    /**
     * The display name - entityJid or callParticipantNames.
     */
    protected String displayName = null;

    /**
     * The start time of the record.
     */
    protected Date startTime = null;

    /**
     * The end time of the record.
     */
    protected Date endTime = null;

    /**
     * The secondary address of the peer - secondaryCallParticipantID
     */
    protected String secondaryPeerAddress = null;

    /**
     * The state of <tt>CallPeer</tt>.
     */
    protected CallPeerState state = CallPeerState.UNKNOWN;

    /**
     * Creates CallPeerRecord
     *
     * @param peerAddress String
     * @param startTime Date
     * @param endTime Date
     */
    public CallPeerRecord(String peerAddress, Date startTime, Date endTime)
    {
        this.peerAddress = peerAddress;
        this.startTime = startTime;
        this.endTime = endTime;
    }

    /**
     * When peer disconnected from the call
     *
     * @return Date
     */
    public Date getEndTime()
    {
        return endTime;
    }

    /**
     * The peer address - entityFullJid or callParticipantIDs
     *
     * @return String
     */
    public String getPeerAddress()
    {
        return peerAddress;
    }

    /**
     * Returns the display name of the call peer in this record - entityJid.
     *
     * @return the call peer display name
     */
    public String getDisplayName()
    {
        return displayName;
    }

    public BareJid getPeerJid()
    {
        String peer = peerAddress.split("/")[0];
        try {
            return JidCreate.bareFrom(peer);
        } catch (XmppStringprepException e) {
            e.printStackTrace();
        }
        return null;
    }

    /**
     * When peer connected to the call
     *
     * @return Date
     */
    public Date getStartTime()
    {
        return startTime;
    }

    /**
     * Returns the actual state of the peer
     *
     * @return CallPeerState
     */
    public CallPeerState getState()
    {
        return state;
    }

    /**
     * Sets secondary address to the <tt>CallPeerRecord</tt>
     *
     * @param address the address to be set.
     */
    public void setPeerSecondaryAddress(String address)
    {
        secondaryPeerAddress = address;
    }

    /**
     * Returns the secondary address to the <tt>CallPeerRecord</tt>
     *
     * @return the secondary address to the <tt>CallPeerRecord</tt>
     */
    public String getPeerSecondaryAddress()
    {
        return secondaryPeerAddress;
    }
}
