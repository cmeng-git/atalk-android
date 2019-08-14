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

import net.java.sip.communicator.service.contactlist.MetaContact;

import org.atalk.android.R;
import org.atalk.android.aTalkApp;
import org.atalk.android.gui.contactlist.ContactListFragment;
import org.atalk.android.gui.util.*;
import org.atalk.service.osgi.OSGiActivity;

import timber.log.Timber;

/**
 * Base class for contact list adapter implementations.
 *
 * @author Yana Stamcheva
 * @author Pawel Domas
 * @author Eng Chong Meng
 */
public abstract class BaseContactListAdapter extends BaseExpandableListAdapter
        implements View.OnClickListener
{
    /**
     * UI thread handler used to call all operations that access data model. This guarantees that
     * it's accessed from the single thread.
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

    private final boolean isShownCallButton;

    private LayoutInflater mInflater;

    /**
     * Creates the contact list adapter.
     *
     * @param clFragment the parent <tt>ContactListFragment</tt>
     * @param isShowButton enable/disable call buttons option
     */
    public BaseContactListAdapter(ContactListFragment clFragment, boolean isShowButton)
    {
        // cmeng - must use this as clFragment may not always attached to FragmentManager e.g. muc invite dialog
        mInflater = LayoutInflater.from(aTalkApp.getGlobalContext());

        contactListFragment = clFragment;
        isShownCallButton = isShowButton;
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
     * Refreshes the list view.
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
    protected void updateAvatar(final int groupIndex, final int contactIndex,
            final Object contactImpl)
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

        if (convertView == null) {
            convertView = mInflater.inflate(R.layout.contact_list_row, parent, false);

            contactViewHolder = new ContactViewHolder();
            contactViewHolder.displayName = convertView.findViewById(R.id.displayName);
            contactViewHolder.statusMessage = convertView.findViewById(R.id.statusMessage);

            contactViewHolder.avatarView = convertView.findViewById(R.id.avatarIcon);
            contactViewHolder.avatarView.setOnClickListener(this);
            contactViewHolder.avatarView.setTag(contactViewHolder);
            contactViewHolder.statusView = convertView.findViewById(R.id.contactStatusIcon);

            // Create call button listener and add bind holder tag
            contactViewHolder.callButtonLayout = convertView.findViewById(R.id.callButtonLayout);
            contactViewHolder.callButton = convertView.findViewById(R.id.contactCallButton);
            contactViewHolder.callButton.setOnClickListener(this);
            contactViewHolder.callButton.setTag(contactViewHolder);

            contactViewHolder.callVideoButton = convertView.findViewById(R.id.contactCallVideoButton);
            contactViewHolder.callVideoButton.setOnClickListener(this);
            contactViewHolder.callVideoButton.setTag(contactViewHolder);

            contactViewHolder.selectedBgView = convertView.findViewById(R.id.selectedBackgroundIcon);
            contactViewHolder.buttonSeparatorView = convertView.findViewById(R.id.buttonSeparatorView);

            convertView.setTag(contactViewHolder);
        }
        else {
            contactViewHolder = (ContactViewHolder) convertView.getTag();
        }
        contactViewHolder.groupPosition = groupPosition;
        contactViewHolder.childPosition = childPosition;

        // return and stop further process if child contact may have been removed
        Object child = getChild(groupPosition, childPosition);
        if (child == null)
            return convertView;

        UIContactRenderer renderer = getContactRenderer(groupPosition);
        if (renderer.isSelected(child)) {
            convertView.setBackgroundResource(R.drawable.list_selection_gradient);
        }
        else {
            convertView.setBackgroundResource(R.drawable.array_list_selector);
        }

        // Set display name and status message for contacts or phone book contacts
        String sDisplayName = renderer.getDisplayName(child);
        String statusMessage = renderer.getStatusMessage(child);

        if ((child instanceof MetaContact)
                && (((MetaContact) child).getDefaultContact() != null)) {
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

        if (isShownCallButton && (isShowVideoCall || isShowCall)) {
            contactViewHolder.callButtonLayout.setVisibility(View.VISIBLE);
            AndroidUtils.setOnTouchBackgroundEffect(contactViewHolder.callButtonLayout);

            if (contactViewHolder.callButton.isSelected()) {
                contactViewHolder.callButton.setImageResource(R.drawable.contact_call_selected_dark);
            }
            else if (contactViewHolder.callVideoButton.isPressed()) {
                contactViewHolder.callVideoButton.setImageResource(R.drawable.contact_call_video_selected_dark);
            }
            else {
                if (!isShowCall) {
                    contactViewHolder.callButton.setVisibility(View.INVISIBLE);
                }
                if (!isShowVideoCall) {
                    contactViewHolder.callVideoButton.setVisibility(View.INVISIBLE);
                }
            }
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

        if (convertView == null) {
            convertView = mInflater.inflate(R.layout.contact_list_group_row, parent, false);
            groupViewHolder = new GroupViewHolder();
            groupViewHolder.displayName = convertView.findViewById(R.id.displayName);
            groupViewHolder.indicator = convertView.findViewById(R.id.groupIndicatorView);
            convertView.setTag(groupViewHolder);
        }
        else {
            groupViewHolder = (GroupViewHolder) convertView.getTag();
        }

        Object group = getGroup(groupPosition);
        if (group != null) {
            UIGroupRenderer groupRenderer = getGroupRenderer(groupPosition);
            groupViewHolder.displayName.setText(groupRenderer.getDisplayName(group));
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
        if (!(view.getTag() instanceof ContactViewHolder)) {
            return;
        }

        ContactViewHolder viewHolder = (ContactViewHolder) view.getTag();
        int groupPos = viewHolder.groupPosition;
        int childPos = viewHolder.childPosition;
        Object contact = getChild(groupPos, childPos);

        if (contact != null) {
            UIContactRenderer renderer = getContactRenderer(groupPos);
            String contactAddress = renderer.getDefaultAddress(contact);

            switch (view.getId()) {
                case R.id.contactCallButton:
                case R.id.contactCallVideoButton:
                    boolean isVideoCall = viewHolder.callVideoButton.isPressed();
                    AndroidCallUtil.createAndroidCall(aTalkApp.getGlobalContext(),
                            viewHolder.callVideoButton, contactAddress, isVideoCall);
                    break;

                case R.id.avatarIcon:
                    aTalkApp.showToastMessage(contactAddress);
                    break;

                default:
                    break;
            }
        }
        else {
            Timber.w("No valid contact found at this position: %s:%s", groupPos, childPos);
        }
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
        ImageView selectedBgView;
        ImageView buttonSeparatorView;
        View callButtonLayout;
        int groupPosition;
        int childPosition;
    }

    private static class GroupViewHolder
    {
        ImageView indicator;
        TextView displayName;
    }
}