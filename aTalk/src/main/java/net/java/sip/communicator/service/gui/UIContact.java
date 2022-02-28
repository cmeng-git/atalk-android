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
package net.java.sip.communicator.service.gui;

import java.util.*;

import java.awt.Component;
import javax.swing.JButton;
import javax.swing.JMenuItem;

import net.java.sip.communicator.service.protocol.*;

/**
 * The <code>UIContact</code> represents the user interface contact contained in the contact list component.
 *
 * @author Yana Stamcheva
 */
public abstract class UIContact
{
    /**
     * Returns the descriptor of this contact.
     *
     * @return the descriptor of this contact
     */
    public abstract Object getDescriptor();

    /**
     * Returns the display name of this contact.
     *
     * @return the display name of this contact
     */
    public abstract String getDisplayName();

    /**
     * Returns the display details of this contact. These would be shown whenever the contact is selected.
     *
     * @return the display details of this contact
     */
    public abstract String getDisplayDetails();

    /**
     * Returns the index of this contact in its source.
     *
     * @return the source index
     */
    public abstract int getSourceIndex();

    /**
     * Creates a tool tip for this contact. If such tooltip is
     * provided it would be shown on mouse over over this <code>UIContact</code>.
     *
     * @return the tool tip for this contact descriptor
     */
    // public abstract ExtendedTooltip getToolTip();

    /**
     * Returns the right button menu component.
     *
     * @return the right button menu component
     */
    public abstract Component getRightButtonMenu();

    /**
     * Returns the parent group.
     *
     * @return the parent group
     */
    public abstract UIGroup getParentGroup();

    /**
     * Sets the given <code>UIGroup</code> to be the parent group of this <code>UIContact</code>.
     *
     * @param parentGroup the parent <code>UIGroup</code> of this contact
     */
    public abstract void setParentGroup(UIGroup parentGroup);

    /**
     * Returns an <code>Iterator</code> over a list of the search strings of this contact.
     *
     * @return an <code>Iterator</code> over a list of the search strings of this contact
     */
    public abstract Iterator<String> getSearchStrings();

    /**
     * Returns the default <code>ContactDetail</code> to use for any operations
     * depending to the given <code>OperationSet</code> class.
     *
     * @param opSetClass the <code>OperationSet</code> class we're interested in
     * @return the default <code>ContactDetail</code> to use for any operations
     * depending to the given <code>OperationSet</code> class
     */
    public abstract UIContactDetail getDefaultContactDetail(
        Class<? extends OperationSet> opSetClass);

    /**
     * Returns a list of all <code>UIContactDetail</code>s corresponding to the
     * given <code>OperationSet</code> class.
     *
     * @param opSetClass the <code>OperationSet</code> class we're looking for
     * @return a list of all <code>UIContactDetail</code>s corresponding to the
     * given <code>OperationSet</code> class
     */
    public abstract List<UIContactDetail> getContactDetailsForOperationSet(
        Class<? extends OperationSet> opSetClass);

    /**
     * Returns a list of all <code>UIContactDetail</code>s within this <code>UIContact</code>.
     *
     * @return a list of all <code>UIContactDetail</code>s within this <code>UIContact</code>
     */
    public abstract List<UIContactDetail> getContactDetails();

    /**
     * Returns all custom action buttons for this notification contact.
     *
     * @return a list of all custom action buttons for this notification contact
     */
    public abstract Collection<? extends JButton> getContactCustomActionButtons();

    /**
     * Returns the preferred height of this group in the contact list.
     *
     * @return the preferred height of this group in the contact list
     */
    public int getPreferredHeight()
    {
        return -1;
    }

    /**
     * Returns all custom action menu items for this contact.
     * 
     * @param initActions if <code>true</code> the actions will be reloaded.
     * @return a list of all custom action menu items for this contact.
     */
    public Collection<JMenuItem> getContactCustomActionMenuItems(
        boolean initActions)
    {
        return null;
    }
}
