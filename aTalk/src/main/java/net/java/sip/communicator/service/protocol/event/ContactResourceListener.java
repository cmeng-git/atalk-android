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

/**
 * The <code>ContactResourceListener</code> listens for events related to <code>ContactResource</code>-s. It
 * is notified each time a <code>ContactResource</code> has been added, removed or modified.
 *
 * @author Yana Stamcheva
 * @author Eng Chong Meng
 */
public interface ContactResourceListener
{
    /**
     * Called when a new <code>ContactResource</code> has been added to the list of available <code>Contact</code> resources.
     *
     * @param event the <code>ContactResourceEvent</code> that notified us
     */
    void contactResourceAdded(ContactResourceEvent event);

    /**
     * Called when a <code>ContactResource</code> has been removed to the list of available <code>Contact</code> resources.
     *
     * @param event the <code>ContactResourceEvent</code> that notified us
     */
    void contactResourceRemoved(ContactResourceEvent event);

    /**
     * Called when a <code>ContactResource</code> in the list of available <code>Contact</code> resources has been modified.
     *
     * @param event the <code>ContactResourceEvent</code> that notified us
     */
    void contactResourceModified(ContactResourceEvent event);
}
