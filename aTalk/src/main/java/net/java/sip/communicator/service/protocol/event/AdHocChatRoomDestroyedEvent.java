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

import net.java.sip.communicator.service.protocol.AdHocChatRoom;
import net.java.sip.communicator.service.protocol.Contact;

/**
 * The event that occurs when an ad-hoc chat room has been created.
 *
 * @author Valentin Martinet
 */
public class AdHocChatRoomDestroyedEvent {
    /**
     * The ad-hoc room that has been created.
     */
    private final AdHocChatRoom adHocChatRoom;

    /**
     * The <code>Contact</code> who created the ad-hoc room.
     */
    private final Contact by;

    /**
     * Initializes an <code>AdHocChatRoomDestroyedEvent</code> with the creator (<code> by</code>) and the
     * ad-hoc room <code>adHocChatRoom</code>.
     *
     * @param adHocChatRoom the <code>AdHocChatRoom</code>
     * @param by the <code>Contact</code> who created this ad-hoc room
     */
    public AdHocChatRoomDestroyedEvent(AdHocChatRoom adHocChatRoom, Contact by) {
        this.adHocChatRoom = adHocChatRoom;
        this.by = by;
    }

    /**
     * Returns the <code>Contact</code> who created the room.
     *
     * @return <code>Contact</code>
     */
    public Contact getBy() {
        return this.by;
    }

    /**
     * Returns the ad-hoc room concerned by this event.
     *
     * @return <code>AdHocChatRoom</code>
     */
    public AdHocChatRoom getAdHocDestroyedRoom() {
        return this.adHocChatRoom;
    }
}
