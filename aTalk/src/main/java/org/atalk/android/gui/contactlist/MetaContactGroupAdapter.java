/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.atalk.android.gui.contactlist;

import android.app.Activity;
import android.view.*;
import android.widget.AdapterView;
import android.widget.TextView;

import net.java.sip.communicator.service.contactlist.MetaContactGroup;
import net.java.sip.communicator.service.contactlist.MetaContactListService;

import org.atalk.android.R;
import org.atalk.android.gui.AndroidGUIActivator;
import org.atalk.android.gui.util.CollectionAdapter;

import java.util.*;

/**
 * This adapter displays all <tt>MetaContactGroup</tt> items. If in the constructor <tt>AdapterView</tt> id
 * will be passed it will include "create new group" functionality. That means extra item "create group.."
 * will be appended on the last position and when selected create group dialog will popup automatically.
 * When a new group is created, it is implicitly included into this adapter.
 *
 * Use setItemLayout and setDropDownLayout to change the spinner style if need to.
 *
 * @author Pawel Domas
 * @author Eng Chong Meng
 */
public class MetaContactGroupAdapter extends CollectionAdapter<Object>
{
    /**
     * Object instance used to identify "Create group..." item.
     */
    private static final Object ADD_NEW_OBJECT = new Object();

    /**
     * Item layout
     */
    private int itemLayout;

    /**
     * Drop down item layout
     */
    private int dropDownLayout;

    /**
     * Instance of used <tt>AdapterView</tt>.
     */
    private AdapterView adapterView;

    /**
     * Creates a new instance of <tt>MetaContactGroupAdapter</tt>. It will be filled with all
     * currently available <tt>MetaContactGroup</tt>.
     *
     * @param parent the parent <tt>Activity</tt>.
     * @param adapterViewId id of the <tt>AdapterView</tt>.
     * @param includeRoot <tt>true</tt> if "No group" item should be included
     * @param includeCreate <tt>true</tt> if "Create group" item should be included
     */
    public MetaContactGroupAdapter(Activity parent, int adapterViewId, boolean includeRoot, boolean includeCreate)
    {
        super(parent, getAllContactGroups(includeRoot, includeCreate).iterator());

        if (adapterViewId != -1)
            init(adapterViewId);
    }

    /**
     * Creates a new instance of <tt>MetaContactGroupAdapter</tt>. It will be filled with all
     * currently available <tt>MetaContactGroup</tt>.
     *
     * @param parent the parent <tt>Activity</tt>.
     * @param adapterView the <tt>AdapterView</tt> that will be used.
     * @param includeRoot <tt>true</tt> if "No group" item should be included
     * @param includeCreate <tt>true</tt> if "Create group" item should be included
     */
    public MetaContactGroupAdapter(Activity parent, AdapterView adapterView, boolean includeRoot, boolean includeCreate)
    {
        super(parent, getAllContactGroups(includeRoot, includeCreate).iterator());
        init(adapterView);
    }

    private void init(int adapterViewId)
    {
        AdapterView aView = getParentActivity().findViewById(adapterViewId);
        init(aView);
    }

    private void init(AdapterView adapterView)
    {
        this.adapterView = adapterView;
        this.itemLayout = R.layout.simple_spinner_item;
        this.dropDownLayout = R.layout.simple_spinner_dropdown_item;

        // Handle add new group action
        adapterView.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener()
        {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id)
            {
                Object item = parent.getAdapter().getItem(position);
                if (item == MetaContactGroupAdapter.ADD_NEW_OBJECT) {
                    AddGroupDialog.showCreateGroupDialog(getParentActivity(), newGroup -> onNewGroupCreated(newGroup));
                }
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent)
            {
            }

        });
    }

    /**
     * Returns the list of all currently available <tt>MetaContactGroup</tt>.
     *
     * @param includeRoot indicates whether "No group" item should be included in the list.
     * @param includeCreateNew indicates whether "create new group" item should be included in the list.
     * @return the list of all currently available <tt>MetaContactGroup</tt>.
     */
    private static List<Object> getAllContactGroups(boolean includeRoot, boolean includeCreateNew)
    {
        MetaContactListService contactListService = AndroidGUIActivator.getContactListService();

        MetaContactGroup root = contactListService.getRoot();
        ArrayList<Object> groupList = new ArrayList<>();
        if (includeRoot) {
            groupList.add(root);
        }

        Iterator<MetaContactGroup> mcGroups = root.getSubgroups();
        while (mcGroups.hasNext()) {
            groupList.add(mcGroups.next());
        }

        // Add new group item
        if (includeCreateNew)
            groupList.add(ADD_NEW_OBJECT);

        return groupList;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected View getView(boolean isDropDown, Object item, ViewGroup parent, LayoutInflater
            inflater)
    {
        int rowResId = isDropDown ? dropDownLayout : itemLayout;
        View rowView = inflater.inflate(rowResId, parent, false);
        TextView tv = rowView.findViewById(android.R.id.text1);

        if (item.equals(ADD_NEW_OBJECT)) {
            tv.setText(R.string.service_gui_CREATE_GROUP);
        }
        else if (item.equals(AndroidGUIActivator.getContactListService().getRoot())) {
            // Root - Contacts
            tv.setText(R.string.service_gui_SELECT_NO_GROUP);
        }
        else {
            tv.setText(((MetaContactGroup) item).getGroupName());
        }
        return rowView;
    }

    /**
     * Handles on new group created event by append item into the list and notifying about data set change.
     *
     * @param newGroup new contact group if was created or <tt>null</tt> if user cancelled the dialog.
     */
    private void onNewGroupCreated(MetaContactGroup newGroup)
    {
        if (newGroup == null)
            return;

        int pos = getCount() - 1;
        insert(pos, newGroup);

        adapterView.setSelection(pos);
        notifyDataSetChanged();
    }

    /**
     * Sets to caller defined item layout resource id.
     *
     * @param itemLayout the item layout resource id to set.
     */
    public void setItemLayout(int itemLayout)
    {
        this.itemLayout = itemLayout;
    }

    /**
     * Set to caller defined drop down item layout resource id.
     *
     * @param dropDownLayout the drop down item layout resource id to set.
     */
    public void setDropDownLayout(int dropDownLayout)
    {
        this.dropDownLayout = dropDownLayout;
    }
}
