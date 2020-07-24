/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 * 
pHideExtendedAwayStatus * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 * 
 * http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */
package org.xmpp.extensions.rayo;

import org.jivesoftware.smack.XMPPConnection;
import org.xmpp.extensions.AbstractExtensionElement;

import org.jivesoftware.smack.packet.Presence;
import org.jxmpp.jid.Jid;

/**
 * 'End' Rayo packet extension used to notify the client about call ended event.
 *
 * @author Pawel Domas
 * @author Eng Chong Meng
 */
public class EndExtension extends AbstractExtensionElement
{
    /**
     * XML element name of this extension.
     */
    public static final String ELEMENT = "end";

    /**
     * End reason.
     */
    private ReasonExtension reason;

    /**
     * Creates new instance.
     */
    protected EndExtension()
    {
        super(ELEMENT, RayoIqProvider.NAMESPACE);
    }

    /**
     * Checks if given <tt>elementName</tt> is valid end reason element.
     *
     * @param elementName the XML element name to check.
     * @return <tt>true</tt> if given <tt>elementName</tt> is valid end reason element.
     */
    public static boolean isValidReason(String elementName)
    {
        return ReasonExtension.BUSY.equals(elementName)
                || ReasonExtension.ERROR.equals(elementName)
                || ReasonExtension.HANGUP.equals(elementName)
                || ReasonExtension.HANGUP_COMMND.equals(elementName)
                || ReasonExtension.REJECTED.equals(elementName)
                || ReasonExtension.TIMEOUT.equals(elementName);
    }

    /**
     * Returns {@link ReasonExtension} associated with this instance.
     *
     * @return {@link ReasonExtension} associated with this instance.
     */
    public ReasonExtension getReason()
    {
        return reason;
    }

    /**
     * Sets new {@link ReasonExtension} for this <tt>EndExtensionElement</tt> instance.
     *
     * @param newReason the new {@link ReasonExtension} to set.
     */
    public void setReason(ReasonExtension newReason)
    {
        if (this.reason != null) {
            getChildExtensions().remove(this.reason);
        }

        this.reason = newReason;
        addChildExtension(newReason);
    }

    /**
     * Creates 'Presence' packet containing call ended Rayo notification that contains specified end
     * <tt>reason</tt>.
     *
     * @param connection XMPP Connection
     * @param from source JID of this event.
     * @param to destination JID.
     * @param reason call end reason string. One of {@link ReasonExtension} static constants.
     * @return 'Presence' packet containing call ended Rayo notification.
     */
    public static Presence createEnd(XMPPConnection connection, Jid from, Jid to, String reason)
    {
        Presence presence = connection.getStanzaFactory().buildPresenceStanza()
                .ofType(Presence.Type.unavailable)
                .build();
        presence.setFrom(from);
        presence.setTo(to);

        EndExtension end = new EndExtension();
        end.setReason(new ReasonExtension(reason));
        presence.addExtension(end);
        return presence;
    }
}
