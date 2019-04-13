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

import org.xmpp.extensions.AbstractExtensionElement;

import org.jivesoftware.smack.packet.Presence;
import org.jxmpp.jid.Jid;

/**
 * 'End' Rayo packet extension used to notify the client about call ended event.
 *
 * @author Pawel Domas
 */
public class EndExtensionElement extends AbstractExtensionElement
{
    /**
     * XML element name of this extension.
     */
    public static final String ELEMENT_NAME = "end";

    /**
     * End reason.
     */
    private ReasonExtensionElement reason;

    /**
     * Creates new instance.
     */
    protected EndExtensionElement()
    {
        super(ELEMENT_NAME, RayoIqProvider.NAMESPACE);
    }

    /**
     * Checks if given <tt>elementName</tt> is valid end reason element.
     *
     * @param elementName the XML element name to check.
     * @return <tt>true</tt> if given <tt>elementName</tt> is valid end reason element.
     */
    public static boolean isValidReason(String elementName)
    {
        return ReasonExtensionElement.BUSY.equals(elementName)
                || ReasonExtensionElement.ERROR.equals(elementName)
                || ReasonExtensionElement.HANGUP.equals(elementName)
                || ReasonExtensionElement.HANGUP_COMMND.equals(elementName)
                || ReasonExtensionElement.REJECTED.equals(elementName)
                || ReasonExtensionElement.TIMEOUT.equals(elementName);
    }

    /**
     * Returns {@link ReasonExtensionElement} associated with this instance.
     *
     * @return {@link ReasonExtensionElement} associated with this instance.
     */
    public ReasonExtensionElement getReason()
    {
        return reason;
    }

    /**
     * Sets new {@link ReasonExtensionElement} for this <tt>EndExtensionElement</tt> instance.
     *
     * @param newReason the new {@link ReasonExtensionElement} to set.
     */
    public void setReason(ReasonExtensionElement newReason)
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
     * @param from source JID of this event.
     * @param to destination JID.
     * @param reason call end reason string. One of {@link ReasonExtensionElement} static constants.
     * @return 'Presence' packet containing call ended Rayo notification.
     */
    public static Presence createEnd(Jid from, Jid to, String reason)
    {
        Presence presence = new Presence(Presence.Type.unavailable);
        presence.setFrom(from);
        presence.setTo(to);

        EndExtensionElement end = new EndExtensionElement();
        end.setReason(new ReasonExtensionElement(reason));
        presence.addExtension(end);
        return presence;
    }
}
