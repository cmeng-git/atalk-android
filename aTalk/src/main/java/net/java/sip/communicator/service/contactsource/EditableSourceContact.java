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
package net.java.sip.communicator.service.contactsource;

/**
 * The <code>EditableSourceContact</code> is an extension to the
 * <code>SourceContact</code> interface that allows editing.
 *
 * @see SourceContact
 *
 * @author Yana Stamcheva
 */
public interface EditableSourceContact
    extends SourceContact
{
    /**
     * Adds a contact detail to the list of contact details.
     *
     * @param detail the <code>ContactDetail</code> to add
     */
    public void addContactDetail(ContactDetail detail);

    /**
     * Removes the given <code>ContactDetail</code> from the list of details for
     * this <code>SourceContact</code>.
     *
     * @param detail the <code>ContactDetail</code> to remove
     */
    public void removeContactDetail(ContactDetail detail);

    /**
     * Locks this object before adding or removing several contact details.
     */
    public void lock();

    /**
     * Unlocks this object before after or removing several contact details.
     */
    public void unlock();

}
