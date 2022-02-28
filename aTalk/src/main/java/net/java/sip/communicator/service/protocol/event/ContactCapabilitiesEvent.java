/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */
package net.java.sip.communicator.service.protocol.event;

import net.java.sip.communicator.service.protocol.Contact;
import net.java.sip.communicator.service.protocol.OperationSet;

import org.jxmpp.jid.Jid;

import java.util.EventObject;
import java.util.Map;

/**
 * Represents an event/<code>EventObject</code> fired by <code>OperationSetClientCapabilities</code> in order
 * to notify about changes in the list of the <code>OperationSet</code> capabilities of a <code>Contact</code>.
 *
 * @author Lubomir Marinov
 * @author Yana Stamcheva
 * @author Eng Chong Meng
 */
public class ContactCapabilitiesEvent extends EventObject
{
    /**
     * Serial version UID.
     */
    private static final long serialVersionUID = 0L;

    /**
     * The full jid of the contact of which its resource is used for actual contact identification
     */
    private final Jid contactJid;

    /**
     * The new set of supported <code>OperationSet</code>s.
     */
    private final Map<String, ? extends OperationSet> opSets;

    /**
     * Initializes a new <code>ContactCapabilitiesEvent</code> instance which is to notify about a
     * specific change in the list of <code>OperationSet</code> capabilities of a specific <code>Contact</code>.
     *
     * @param sourceContact the <code>Contact</code> which is to be considered the source/cause of the new event
     * @param jid the full Jid of contact
     * @param opSets the new set of operation sets this event is about
     */
    public ContactCapabilitiesEvent(Contact sourceContact, Jid jid, Map<String, ? extends OperationSet> opSets)
    {
        super(sourceContact);

        this.contactJid = jid;
        this.opSets = opSets;
    }

    /**
     * Gets the contact Jid which indicates the specifics of the change in the list of <code>OperationSet</code>
     * capabilities of the associated <code>sourceContact</code> and the details it carries.
     *
     * @return the the fullJid of the contact
     */
    public Jid getJid()
    {
        return contactJid;
    }

    /**
     * Gets the <code>Contact</code> which is the source/cause of this event i.e. which has changed its
     * list of <code>OperationSet</code> capabilities.
     *
     * @return the <code>Contact</code> which is the source/cause of this event
     */
    public Contact getSourceContact()
    {
        return (Contact) getSource();
    }

    /**
     * Returns the new set of <code>OperationSet</code>-s this event is about
     *
     * @return the new set of <code>OperationSet</code>-s
     */
    public Map<String, ? extends OperationSet> getOperationSets()
    {
        return opSets;
    }
}
