/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.atalk.android.gui.contactlist.model;

import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.os.Handler;
import android.text.TextUtils;
import android.view.*;
import android.widget.*;

import net.java.sip.communicator.impl.protocol.jabber.ContactJabberImpl;
import net.java.sip.communicator.service.contactlist.MetaContact;
import net.java.sip.communicator.service.contactlist.MetaContactGroup;
import net.java.sip.communicator.service.protocol.Contact;
import net.java.sip.communicator.service.protocol.ContactGroup;

import org.atalk.android.R;
import org.atalk.android.aTalkApp;
import org.atalk.android.gui.call.AndroidCallUtil;
import org.atalk.android.gui.call.telephony.TelephonyFragment;
import org.atalk.android.gui.contactlist.ContactListFragment;
import org.atalk.android.gui.util.AndroidUtils;
import org.atalk.android.gui.util.ViewUtil;
import org.atalk.android.gui.widgets.UnreadCountCustomView;
import org.atalk.service.osgi.OSGiActivity;
import org.jxmpp.jid.DomainBareJid;
import org.jxmpp.jid.Jid;

import java.util.HashMap;
import java.util.Map;

import timber.log.Timber;

/**
 * Base class for contact list adapter implementations.
 *
 * @author Yana Stamcheva
 * @author Pawel Domas
 * @author Eng Chong Meng
 */
public abstract class BaseContactListAdapter extends BaseExpandableListAdapter
        implements View.OnClickListener, View.OnLongClickListener
{
    /**
     * UI thread handler used to call all operations that access data model. This guarantees that
     * it's accessed from the main thread.
     */
    protected final Handler uiHandler = OSGiActivity.uiHandler;

    /**
     * The contact list view.
     */
    private final ContactListFragment contactListFragment;

    /**
     * The list view.
     */
    private final ExpandableListView contactListView;

    /**
     * A map reference of MetaContact to ContactViewHolder for the unread message count update
     */
    private Map<MetaContact, ContactViewHolder> mContactViewHolder = new HashMap<>();

    /**
     * Flag set to true to indicate the view is the main contact list and all available options etc are enabled
     * Otherwise the view is meant for group chat invite, all the following options take effects:
     * a. Hide all media call buttons
     * b. Disabled Context menu (popup menus) i.e. onClick and onLongClick
     * c. Multiple contact selection are allowed
     */
    private final boolean isMainContactList;

    private LayoutInflater mInflater;

    /**
     * Creates the contact list adapter.
     *
     * @param clFragment the parent <tt>ContactListFragment</tt>
     * @param mainContactList call buttons and other options are only enable when it is the main Contact List view
     */
    public BaseContactListAdapter(ContactListFragment clFragment, boolean mainContactList)
    {
        // cmeng - must use this mInflater as clFragment may not always attached to FragmentManager e.g. muc invite dialog
        mInflater = LayoutInflater.from(aTalkApp.getGlobalContext());
        contactListFragment = clFragment;
        isMainContactList = mainContactList;
        contactListView = contactListFragment.getContactListView();
    }

    /**
     * Initializes model data. Is called before adapter is used for the first time.
     */
    public abstract void initModelData();

    /**
     * Filter the contact list with given <tt>queryString</tt>
     *
     * @param queryString the query string we want to match.
     */
    public abstract void filterData(String queryString);

    /**
     * Returns the <tt>UIContactRenderer</tt> for contacts of group at given <tt>groupIndex</tt>.
     *
     * @param groupIndex index of the contact group.
     * @return the <tt>UIContactRenderer</tt> for contact of group at given <tt>groupIndex</tt>.
     */
    protected abstract UIContactRenderer getContactRenderer(int groupIndex);

    /**
     * Returns the <tt>UIGroupRenderer</tt> for group at given <tt>groupPosition</tt>.
     *
     * @param groupPosition index of the contact group.
     * @return the <tt>UIContactRenderer</tt> for group at given <tt>groupPosition</tt>.
     */
    protected abstract UIGroupRenderer getGroupRenderer(int groupPosition);

    /**
     * Releases all resources used by this instance.
     */
    public void dispose()
    {
        notifyDataSetInvalidated();
    }

    /**
     * Expands all contained groups.
     */
    public void expandAllGroups()
    {
        // Expand group view only when contactListView is in focus (UI mode)
        // cmeng - do not use isFocused() - may not in sync with actual
        uiHandler.post(() -> {
            int count = getGroupCount();
            for (int position = 0; position < count; position++) {
                if (contactListView != null)
                    contactListView.expandGroup(position);
            }
        });
    }

    /**
     * Refreshes the view with expands group and invalid view.
     */
    public void invalidateViews()
    {
        if (contactListView != null) {
            contactListFragment.runOnUiThread(contactListView::invalidateViews);
        }
    }

    /**
     * Updates the contact display name.
     *
     * @param groupIndex the index of the group to update
     * @param contactIndex the index of the contact to update
     */
    protected void updateDisplayName(final int groupIndex, final int contactIndex)
    {
        int firstIndex = contactListView.getFirstVisiblePosition();
        View contactView = contactListView.getChildAt(getListIndex(groupIndex, contactIndex) - firstIndex);

        if (contactView != null) {
            MetaContact metaContact = (MetaContact) getChild(groupIndex, contactIndex);
            ViewUtil.setTextViewValue(contactView, R.id.displayName, metaContact.getDisplayName());
        }
    }

    /**
     * Updates the contact avatar.
     *
     * @param groupIndex the index of the group to update
     * @param contactIndex the index of the contact to update
     * @param contactImpl contact implementation object instance
     */
    protected void updateAvatar(final int groupIndex, final int contactIndex, final Object contactImpl)
    {
        int firstIndex = contactListView.getFirstVisiblePosition();
        View contactView = contactListView.getChildAt(getListIndex(groupIndex, contactIndex) - firstIndex);

        if (contactView != null) {
            ImageView avatarView = contactView.findViewById(R.id.avatarIcon);

            if (avatarView != null)
                setAvatar(avatarView, getContactRenderer(groupIndex).getAvatarImage(contactImpl));
        }
    }

    /**
     * Updates the contact status indicator.
     *
     * @param groupIndex the index of the group to update
     * @param contactIndex the index of the contact to update
     * @param contactImpl contact implementation object instance
     */
    protected void updateStatus(final int groupIndex, final int contactIndex, Object contactImpl)
    {
        int firstIndex = contactListView.getFirstVisiblePosition();
        View contactView = contactListView.getChildAt(getListIndex(groupIndex, contactIndex) - firstIndex);

        if (contactView != null) {
            ImageView statusView = contactView.findViewById(R.id.contactStatusIcon);

            if (statusView == null) {
                Timber.w("No status view found for %s", contactImpl);
                return;
            }
            statusView.setImageDrawable(getContactRenderer(groupIndex).getStatusImage(contactImpl));
        }
    }

    /**
     * Updates the contact message unread count. Hide the unread message badge if the count is zero
     *
     * @param metaContact MetaContact object
     * @param count unread message count
     */
    public void updateUnreadCount(final MetaContact metaContact, final int count)
    {
        ContactViewHolder contactViewHolder = mContactViewHolder.get(metaContact);
        if (contactViewHolder == null)
            return;

        if (count == 0) {
            contactViewHolder.unreadCount.setVisibility(View.GONE);
        }
        else {
            contactViewHolder.unreadCount.setVisibility(View.VISIBLE);
            contactViewHolder.unreadCount.setUnreadCount(count);
        }
    }

    /**
     * Returns the flat list index for the given <tt>groupIndex</tt> and <tt>contactIndex</tt>.
     *
     * @param groupIndex the index of the group
     * @param contactIndex the index of the contact
     * @return an int representing the flat list index for the given <tt>groupIndex</tt> and <tt>contactIndex</tt>
     */
    public int getListIndex(int groupIndex, int contactIndex)
    {
        int lastIndex = contactListView.getLastVisiblePosition();

        for (int i = 0; i <= lastIndex; i++) {
            long lPosition = contactListView.getExpandableListPosition(i);

            int groupPosition = ExpandableListView.getPackedPositionGroup(lPosition);
            int childPosition = ExpandableListView.getPackedPositionChild(lPosition);

            if ((groupIndex == groupPosition) && (contactIndex == childPosition)) {
                return i;
            }
        }
        return -1;
    }

    /**
     * Returns the identifier of the child contained on the given <tt>groupPosition</tt> and <tt>childPosition</tt>.
     *
     * @param groupPosition the index of the group
     * @param childPosition the index of the child
     * @return the identifier of the child contained on the given <tt>groupPosition</tt> and <tt>childPosition</tt>
     */
    @Override
    public long getChildId(int groupPosition, int childPosition)
    {
        return childPosition;
    }

    /**
     * Returns the child view for the given <tt>groupPosition</tt>, <tt>childPosition</tt>.
     *
     * @param groupPosition the group position of the desired view
     * @param childPosition the child position of the desired view
     * @param isLastChild indicates if this is the last child
     * @param convertView the view to fill with data
     * @param parent the parent view group
     */
    @Override
    public View getChildView(int groupPosition, int childPosition, boolean isLastChild,
            View convertView, ViewGroup parent)
    {
        // Keeps reference to avoid future findViewById()
        ContactViewHolder contactViewHolder;
        Object child = getChild(groupPosition, childPosition);
        // Timber.w("getChildView: %s:%s = %s", groupPosition, childPosition, child);

        if ((convertView == null) || !(convertView.getTag() instanceof ContactViewHolder)) {
            convertView = mInflater.inflate(R.layout.contact_list_row, parent, false);

            contactViewHolder = new ContactViewHolder();
            contactViewHolder.displayName = convertView.findViewById(R.id.displayName);
            contactViewHolder.statusMessage = convertView.findViewById(R.id.statusMessage);

            contactViewHolder.avatarView = convertView.findViewById(R.id.avatarIcon);
            contactViewHolder.avatarView.setOnClickListener(this);
            contactViewHolder.avatarView.setOnLongClickListener(this);
            contactViewHolder.statusView = convertView.findViewById(R.id.contactStatusIcon);

            contactViewHolder.unreadCount = convertView.findViewById(R.id.unread_count);
            contactViewHolder.unreadCount.setTag(contactViewHolder);

            // Create call button listener and add bind holder tag
            contactViewHolder.callButtonLayout = convertView.findViewById(R.id.callButtonLayout);
            contactViewHolder.callButton = convertView.findViewById(R.id.contactCallButton);
            contactViewHolder.callButton.setOnClickListener(this);
            contactViewHolder.callButton.setTag(contactViewHolder);

            contactViewHolder.callVideoButton = convertView.findViewById(R.id.contactCallVideoButton);
            contactViewHolder.callVideoButton.setOnClickListener(this);
            contactViewHolder.callVideoButton.setTag(contactViewHolder);

            contactViewHolder.buttonSeparatorView = convertView.findViewById(R.id.buttonSeparatorView);
        }
        else {
            contactViewHolder = (ContactViewHolder) convertView.getTag();
        }
        contactViewHolder.groupPosition = groupPosition;
        contactViewHolder.childPosition = childPosition;

        // return and stop further process if child contact may have been removed
        if (!(child instanceof MetaContact))
            return convertView;

        // Must init child tag here as reused convertView may not necessary contains the correct metaContact
        View contactView = convertView.findViewById(R.id.contact_view);
        if (isMainContactList) {
            contactView.setOnClickListener(this);
            contactView.setOnLongClickListener(this);
        }
        contactView.setTag(child);
        contactViewHolder.avatarView.setTag(child);

        UIContactRenderer renderer = getContactRenderer(groupPosition);
        if (renderer.isSelected(child)) {
            convertView.setBackgroundResource(R.drawable.color_blue_gradient);
        }
        else {
            convertView.setBackgroundResource(R.drawable.list_selector_state);
        }

        // Set display name and status message for contacts or phone book contacts
        String sDisplayName = renderer.getDisplayName(child);
        String statusMessage = renderer.getStatusMessage(child);

        MetaContact metaContact = (MetaContact) child;
        if (metaContact.getDefaultContact() != null) {
            mContactViewHolder.put(metaContact, contactViewHolder);
            updateUnreadCount(metaContact, metaContact.getUnreadCount());

            String sJid = sDisplayName;
            if (TextUtils.isEmpty(statusMessage)) {
                if (sJid.contains("@")) {
                    sDisplayName = sJid.split("@")[0];
                    statusMessage = sJid;
                }
                else
                    statusMessage = renderer.getDefaultAddress(child);
            }
        }
        contactViewHolder.displayName.setText(sDisplayName);
        contactViewHolder.statusMessage.setText(statusMessage);

        if (renderer.isDisplayBold(child)) {
            contactViewHolder.displayName.setTypeface(Typeface.DEFAULT_BOLD);
        }
        else {
            contactViewHolder.displayName.setTypeface(Typeface.DEFAULT);
        }

        // Set avatar.
        setAvatar(contactViewHolder.avatarView, renderer.getAvatarImage(child));
        contactViewHolder.statusView.setImageDrawable(renderer.getStatusImage(child));

        // Show both voice and video call buttons.
        boolean isShowVideoCall = renderer.isShowVideoCallBtn(child);
        boolean isShowCall = renderer.isShowCallBtn(child);

        if (isMainContactList && (isShowVideoCall || isShowCall)) {
            AndroidUtils.setOnTouchBackgroundEffect(contactViewHolder.callButtonLayout);

            contactViewHolder.callButtonLayout.setVisibility(View.VISIBLE);
            contactViewHolder.callButton.setVisibility(isShowCall ? View.VISIBLE : View.GONE);
            contactViewHolder.callVideoButton.setVisibility(isShowVideoCall ? View.VISIBLE : View.GONE);
        }
        else {
            contactViewHolder.callButtonLayout.setVisibility(View.INVISIBLE);
        }
        return convertView;
    }

    /**
     * Returns the group view for the given <tt>groupPosition</tt>.
     *
     * @param groupPosition the group position of the desired view
     * @param isExpanded indicates if the view is currently expanded
     * @param convertView the view to fill with data
     * @param parent the parent view group
     */
    @Override
    public View getGroupView(int groupPosition, boolean isExpanded, View convertView, ViewGroup parent)
    {
        // Keeps reference to avoid future findViewById()
        GroupViewHolder groupViewHolder;
        Object group = getGroup(groupPosition);

        if ((convertView == null) || !(convertView.getTag() instanceof GroupViewHolder)) {
            convertView = mInflater.inflate(R.layout.contact_list_group_row, parent, false);

            groupViewHolder = new GroupViewHolder();
            groupViewHolder.groupName = convertView.findViewById(R.id.groupName);
            groupViewHolder.groupName.setOnLongClickListener(this);

            groupViewHolder.indicator = convertView.findViewById(R.id.groupIndicatorView);
            convertView.setTag(groupViewHolder);
        }
        else {
            groupViewHolder = (GroupViewHolder) convertView.getTag();
        }

        if (group instanceof MetaContactGroup) {
            UIGroupRenderer groupRenderer = getGroupRenderer(groupPosition);
            groupViewHolder.groupName.setTag(group);
            groupViewHolder.groupName.setText(groupRenderer.getDisplayName(group));
        }

        // Group expand indicator
        int indicatorResId = isExpanded ? R.drawable.expanded_dark : R.drawable.collapsed_dark;
        groupViewHolder.indicator.setImageResource(indicatorResId);
        return convertView;
    }

    /**
     * Returns the identifier of the group given by <tt>groupPosition</tt>.
     *
     * @param groupPosition the index of the group, which identifier we're looking for
     */
    @Override
    public long getGroupId(int groupPosition)
    {
        return groupPosition;
    }

    /**
     *
     */
    @Override
    public boolean hasStableIds()
    {
        return true;
    }

    /**
     * Indicates that all children are selectable.
     */
    @Override
    public boolean isChildSelectable(int groupPosition, int childPosition)
    {
        return true;
    }

    /**
     * We keep one instance of view click listener to avoid unnecessary allocations.
     * Clicked positions are obtained from the view holder.
     */
    public void onClick(View view)
    {
        ContactViewHolder viewHolder = null;

        Object object = view.getTag();

        // Use by media call button activation
        if (object instanceof ContactViewHolder) {
            viewHolder = (ContactViewHolder) view.getTag();
            int groupPos = viewHolder.groupPosition;
            int childPos = viewHolder.childPosition;
            object = getChild(groupPos, childPos);
        }

        if (object instanceof MetaContact) {
            MetaContact metaContact = (MetaContact) object;
            Contact contact = metaContact.getDefaultContact();
            Boolean isAudioCall = null;

            if (view.getId() == R.id.contact_view) {
                contactListFragment.startChat(metaContact);
            }
            else if (contact != null) {
                Jid jid = contact.getJid();
                String JidAddress = contact.getAddress();

                switch (view.getId()) {
                    case R.id.contact_view:
                        contactListFragment.startChat(metaContact);
                        break;

                    case R.id.contactCallButton:
                        if (jid instanceof DomainBareJid) {
                            TelephonyFragment extPhone = TelephonyFragment.newInstance(JidAddress);
                            contactListFragment.getActivity().getSupportFragmentManager().beginTransaction()
                                    .replace(android.R.id.content, extPhone).commit();
                            break;
                        }
                        isAudioCall = true;

                    case R.id.contactCallVideoButton:
                        if (viewHolder != null) {
                            // AndroidCallUtil.createAndroidCall(aTalkApp.getGlobalContext(),
                            //        viewHolder.callVideoButton, JidAddress, (isAudioCall == null));
                            AndroidCallUtil.createCall(aTalkApp.getGlobalContext(), metaContact, (isAudioCall == null),
                                    viewHolder.callVideoButton);
                        }
                        break;

                    case R.id.avatarIcon:
                        aTalkApp.showToastMessage(JidAddress);
                        break;

                    default:
                        break;
                }
            }
        }
        else {
            Timber.w("Clicked item is not a valid MetaContact");
        }
    }

    /**
     * Retrieve the contact avatar from server when user longClick on the avatar in contact list.
     * Clicked position/contact is derived from the view holder group/child positions.
     */
    public boolean onLongClick(View view)
    {
        Object clicked = view.getTag();

        // proceed to retrieve avatar for the clicked contact
        if (clicked instanceof MetaContact) {
            MetaContact metaContact = (MetaContact) clicked;
            switch (view.getId()) {
                case R.id.contact_view:
                    contactListFragment.showPopupMenuContact(view, metaContact);
                    return true;

                case R.id.avatarIcon:
                    Contact contact = metaContact.getDefaultContact();
                    if (contact != null) {
                        Jid contactJid = contact.getJid();
                        if (!(contactJid instanceof DomainBareJid)) {
                            ((ContactJabberImpl) contact).getAvatar(true);
                            aTalkApp.showToastMessage(R.string.service_gui_AVATAR_RETRIEVING, contactJid);
                        }
                        else {
                            aTalkApp.showToastMessage(R.string.service_gui_CONTACT_INVALID, contactJid);
                        }
                    }
                    return true;
            }
        }
        else if (clicked instanceof MetaContactGroup) {
            if (view.getId() == R.id.groupName) {
                if (ContactGroup.ROOT_GROUP_UID.equals(((MetaContactGroup) clicked).getMetaUID())
                        || ContactGroup.VOLATILE_GROUP.equals(((MetaContactGroup) clicked).getGroupName())) {
                    Timber.w("No action allowed for Group Name: %s", ((MetaContactGroup) clicked).getGroupName());
                    aTalkApp.showToastMessage(R.string.service_gui_UNSUPPORTED_OPERATION);
                }
                else {
                    contactListFragment.showPopUpMenuGroup(view, (MetaContactGroup) clicked);
                }
                return true;
            }
        }
        return false;
    }

    /**
     * Sets the avatar icon of the action bar.
     *
     * @param avatarView the avatar image view
     */
    private void setAvatar(ImageView avatarView, Drawable avatarImage)
    {
        if (avatarImage == null) {
            avatarImage = aTalkApp.getAppResources().getDrawable(R.drawable.contact_avatar);
        }
        avatarView.setImageDrawable(avatarImage);
    }

    private static class ContactViewHolder
    {
        TextView displayName;
        TextView statusMessage;
        ImageView avatarView;
        ImageView statusView;
        ImageView callButton;
        ImageView callVideoButton;
        ImageView buttonSeparatorView;
        View callButtonLayout;
        UnreadCountCustomView unreadCount;
        int groupPosition;
        int childPosition;
    }

    private static class GroupViewHolder
    {
        ImageView indicator;
        TextView groupName;
    }
}