/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file
 * except in compliance with the License. You may obtain a copy of the License at
 * 
 * http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software distributed under the
 * License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
 * either express or implied. See the License for the specific language governing permissions and
  * limitations under the License.
 */
package org.atalk.android.gui.contactlist;

import android.graphics.drawable.Drawable;

import net.java.sip.communicator.service.gui.UIContact;

import org.atalk.android.gui.AndroidGUIActivator;

/**
 * The <code>UIContactImpl</code> class extends the <code>UIContact</code> in order to add some more
 * methods specific the UI implementation.
 *
 * @author Yana Stamcheva
 */
public abstract class UIContactImpl extends UIContact
{
	/**
	 * Whether we should filter all addresses shown in tooltips and to remove the domain part.
	 */
	private static final String FILTER_DOMAIN_IN_TIP_ADDRESSES = "gui.contactlist.FILTER_DOMAIN_IN_TIP_ADDRESSES";

	/**
	 * Returns the corresponding <code>ContactNode</code>. The <code>ContactNode</code> is the real node
	 * that is stored in the contact list component data model.
	 *
	 * @return the corresponding <code>ContactNode</code>
	 */
	public abstract ContactNode getContactNode();

	/**
	 * Sets the given <code>contactNode</code>. The <code>ContactNode</code> is the real node that is
	 * stored in the contact list component data model.
	 *
	 * @param contactNode
	 * 		the <code>ContactNode</code> that corresponds to this <code>UIGroup</code>
	 */
	public abstract void setContactNode(ContactNode contactNode);

	/**
	 * Returns the general status icon of the given UIContact.
	 *
	 * @return PresenceStatus the most "available" status from all sub-contact statuses.
	 */
	public abstract byte[] getStatusIcon();

	/**
	 * Gets the avatar of a specific <code>UIContact</code> in the form of an <code>ImageIcon</code> value.
	 *
	 * @param isSelected
	 * 		indicates if the contact is selected
	 * @param width
	 * 		the desired icon width
	 * @param height
	 * 		the desired icon height
	 * @return an <code>ImageIcon</code> which represents the avatar of the specified
	 * <code>MetaContact</code>
	 */
	public abstract Drawable getScaledAvatar(boolean isSelected, int width, int height);

	/**
	 * Gets the avatar of a specific <code>UIContact</code> in the form of an <code>ImageIcon</code> value.
	 *
	 * @return a byte array representing the avatar of this <code>UIContact</code>
	 */
	public byte[] getAvatar()
	{
		return null;
	}

	/**
	 * Returns the display name of this <code>UIContact</code>.
	 *
	 * @return the display name of this <code>UIContact</code>
	 */
	@Override
	public abstract String getDisplayName();

	/**
	 * Filter address display if enabled will remove domain part of the addresses to show.
	 *
	 * @param addressToDisplay
	 * 		the address to change
	 * @return if enabled the address with removed domain part
	 */
	protected String filterAddressDisplay(String addressToDisplay)
	{
		if (!AndroidGUIActivator.getConfigurationService().getBoolean(FILTER_DOMAIN_IN_TIP_ADDRESSES, false))
			return addressToDisplay;

		int ix = addressToDisplay.indexOf("@");
		int typeIx = addressToDisplay.indexOf("(");

		if (ix != -1) {
			if (typeIx != -1)
				addressToDisplay = addressToDisplay.substring(0, ix) + " "
						+ addressToDisplay.substring(typeIx, addressToDisplay.length());
			else
				addressToDisplay = addressToDisplay.substring(0, ix);
		}
		return addressToDisplay;
	}
}
