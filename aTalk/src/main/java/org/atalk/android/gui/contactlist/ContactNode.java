/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on
 * an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 */
package org.atalk.android.gui.contactlist;

import net.java.sip.communicator.service.gui.ContactListNode;
import net.java.sip.communicator.service.gui.UIContact;

/**
 * The <code>ContactNode</code> is a <code>ContactListNode</code> corresponding to a given <code>UIContact</code>.
 *
 * @author Yana Stamcheva
 */
// public class ContactNode extends DefaultMutableTreeNode implements ContactListNode
public class ContactNode implements ContactListNode {
    /**
     * The <code>UIContact</code> corresponding to this contact node.
     */
    private final UIContact contact;

    /**
     * Indicates if this node is currently active. Has unread messages waiting.
     */
    private boolean isActive;

    /**
     * Creates a <code>ContactNode</code> by specifying the corresponding <code>contact</code>.
     *
     * @param contact the <code>UIContactImpl</code> corresponding to this node
     */
    public ContactNode(UIContactImpl contact) {
        // super(contact);
        this.contact = contact;
    }

    /**
     * Returns the corresponding <code>UIContactImpl</code>.
     *
     * @return the corresponding <code>UIContactImpl</code>
     */
    // public UIContactImpl getContactDescriptor()
    // {
    // return (UIContactImpl) getUserObject();
    // }

    /**
     * Returns the index of this contact node in its parent group.
     *
     * @return the index of this contact node in its parent group
     */
    public int getSourceIndex() {
        return contact.getSourceIndex();
    }

    /**
     * Returns <code>true</code> if this contact node has unread received messages waiting, otherwise returns <code>false</code>
     * .
     *
     * @return <code>true</code> if this contact node has unread received messages waiting, otherwise returns <code>false</code>
     */
    public boolean isActive() {
        return isActive;
    }

    /**
     * Sets this contact node as active, which indicates it has unread received messages waiting.
     *
     * @param isActive indicates if this contact is active
     */
    public void setActive(boolean isActive) {
        this.isActive = isActive;
    }
}
