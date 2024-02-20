/*
 * aTalk, android VoIP and Instant Messaging client
 * Copyright 2014~2024 Eng Chong Meng
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package net.java.sip.communicator.service.protocol.event;


import net.java.sip.communicator.service.protocol.Contact;

public interface ContactBlockingStatusListener
{
    /**
     * Callback whenever a change occurs in the contact blocking status
     *
     * @param contact the associated contact for the blocking status change.
     * @param blockState true if contact has been blocked.
     */
    void contactBlockingStatusChanged(Contact contact, boolean blockState);
}
