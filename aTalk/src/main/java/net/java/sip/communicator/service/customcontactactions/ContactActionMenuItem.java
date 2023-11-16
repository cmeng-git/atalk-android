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
package net.java.sip.communicator.service.customcontactactions;

import net.java.sip.communicator.service.protocol.OperationFailedException;

/**
 * A custom contact action menu item, used to define an action that can be 
 * represented in the contact list entry in the user interface.
 *
 * @author Hristo Terezov
 * @author Eng Chong Meng
 */
public interface ContactActionMenuItem<T>
{
    /**
     * Invoked when an action occurs.
     *
     * @param actionSource the source of the action
     */
    void actionPerformed(T actionSource)
        throws OperationFailedException;

    /**
     * The icon used by the UI to visualize this action.
     * @return the button icon.
     */
    byte[] getIcon();

    /**
     * Returns the text of the component to create for this contact
     * action.
     * 
     * @param actionSource the action source for associated with the
     * action.
     * @return the tool tip text of the component to create for this contact
     * action
     */
    String getText(T actionSource);

    /**
     * Indicates if this action is visible for the given <code>actionSource</code>.
     *
     * @param actionSource the action source for which we're verifying the
     * action.
     * @return <code>true</code> if the action should be visible for the given
     * <code>actionSource</code>, <code>false</code> - otherwise
     */
    boolean isVisible(T actionSource);
    
    /**
     * 
     * @return
     */
    char getMnemonics();
    
    /**
     * Returns <code>true</code> if the item should be enabled and <code>false</code>
     *  - not.
     *  
     * @param actionSource the action source for which we're verifying the
     * action.
     * @return <code>true</code> if the item should be enabled and <code>false</code>
     *  - not.
     */
    boolean isEnabled(T actionSource);
    
    /**
     * Returns <code>true</code> if the item should be a check box and
     * <code>false</code> if not
     * 
     * @return <code>true</code> if the item should be a check box and
     * <code>false</code> if not
     */
    boolean isCheckBox();

    /**
     * Returns the state of the item if the item is check box.
     * 
     * @param actionSource the action source for which we're verifying the
     * action.
     * @return the state of the item.
     */
    boolean isSelected(T actionSource);
}
