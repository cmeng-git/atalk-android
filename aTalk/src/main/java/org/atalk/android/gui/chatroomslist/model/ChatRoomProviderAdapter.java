/*
 * aTalk, android VoIP and Instant Messaging client
 * Copyright 2014 Eng Chong Meng
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
package org.atalk.android.gui.chatroomslist.model;

import android.app.Activity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import net.java.sip.communicator.service.contactlist.MetaContactGroup;
import net.java.sip.communicator.service.contactlist.MetaContactListService;

import org.atalk.android.R;
import org.atalk.android.gui.AndroidGUIActivator;
import org.atalk.android.gui.contactlist.AddGroupDialog;
import org.atalk.android.gui.util.CollectionAdapter;

/**
 * This adapter displays all <code>chatRoom Group</code> items. If in the constructor
 * <code>AdapterView</code> id will be passed it will include "create new group" functionality. That
 * means extra item "create group.." will be appended on the last position and when selected
 * create group dialog will popup automatically. When new group is eventually created it is
 * implicitly included into this adapter.
 *
 * @author Eng Chong Meng
 */
public class ChatRoomProviderAdapter extends CollectionAdapter<Object> {
    /**
     * Object instance used to identify "Create group..." item.
     */
    private static final Object ADD_NEW_OBJECT = new Object();

    /**
     * Drop down item layout
     */
    private int dropDownLayout;

    /**
     * Item layout
     */
    private int itemLayout;

    /**
     * Instance of used <code>AdapterView</code>.
     */
    private AdapterView adapterView;

    /**
     * Creates new instance of <code>MetaContactGroupAdapter</code>. It will be filled with all
     * currently available <code>MetaContactGroup</code>.
     *
     * @param parent the parent <code>Activity</code>.
     * @param adapterViewId id of the <code>AdapterView</code>.
     * @param includeRoot <code>true</code> if "No group" item should be included
     * @param includeCreate <code>true</code> if "Create group" item should be included
     */
    public ChatRoomProviderAdapter(Activity parent, int adapterViewId, boolean includeRoot,
            boolean includeCreate) {
        super(parent, getAllContactGroups(includeRoot, includeCreate).iterator());

        if (adapterViewId != -1)
            init(adapterViewId);
    }

    /**
     * Creates new instance of <code>MetaContactGroupAdapter</code>. It will be filled with all
     * currently available <code>MetaContactGroup</code>.
     *
     * @param parent the parent <code>Activity</code>.
     * @param adapterView the <code>AdapterView</code> that will be used.
     * @param includeRoot <code>true</code> if "No group" item should be included
     * @param includeCreate <code>true</code> if "Create group" item should be included
     */
    public ChatRoomProviderAdapter(Activity parent, AdapterView adapterView, boolean includeRoot, boolean includeCreate) {
        super(parent, getAllContactGroups(includeRoot, includeCreate).iterator());
        init(adapterView);
    }

    private void init(int adapterViewId) {
        AdapterView aView = getParentActivity().findViewById(adapterViewId);
        init(aView);
    }

    private void init(AdapterView aView) {
        this.adapterView = aView;
        this.dropDownLayout = android.R.layout.simple_spinner_dropdown_item;
        this.itemLayout = android.R.layout.simple_spinner_item;

        // Handle add new group action
        aView.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                Object item = parent.getAdapter().getItem(position);
                if (item == ChatRoomProviderAdapter.ADD_NEW_OBJECT) {
                    AddGroupDialog.showCreateGroupDialog(getParentActivity(),
                            newGroup -> onNewGroupCreated(newGroup));
                }
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
            }

        });
    }

    /**
     * Returns the list of all currently available <code>MetaContactGroup</code>.
     *
     * @param includeRoot indicates whether "No group" item should be included in the list.
     * @param includeCreateNew indicates whether "create new group" item should be included in the list.
     *
     * @return the list of all currently available <code>MetaContactGroup</code>.
     */
    private static List<Object> getAllContactGroups(boolean includeRoot, boolean includeCreateNew) {
        MetaContactListService contactListService = AndroidGUIActivator.getContactListService();

        MetaContactGroup root = contactListService.getRoot();
        ArrayList<Object> merge = new ArrayList<>();
        if (includeRoot) {
            merge.add(root);
        }

        Iterator<MetaContactGroup> mcg = root.getSubgroups();
        while (mcg.hasNext()) {
            merge.add(mcg.next());
        }

        // Add new group item
        if (includeCreateNew)
            merge.add(ADD_NEW_OBJECT);

        return merge;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected View getView(boolean isDropDown, Object item, ViewGroup parent, LayoutInflater inflater) {
        int rowResId = isDropDown ? dropDownLayout : itemLayout;
        View rowView = inflater.inflate(rowResId, parent, false);
        TextView tv = rowView.findViewById(android.R.id.text1);

        if (item.equals(ADD_NEW_OBJECT)) {
            tv.setText(R.string.create_group);
        }
        else if (item.equals(AndroidGUIActivator.getContactListService().getRoot())) {
            // Root
            tv.setText(R.string.no_group);
        }
        else {
            tv.setText(((MetaContactGroup) item).getGroupName());
        }
        return rowView;
    }

    /**
     * Handles on new group created event by append item into the list and notifying about data
     * set change.
     *
     * @param newGroup new contact group if was created or <code>null</code> if user cancelled the dialog.
     */
    private void onNewGroupCreated(MetaContactGroup newGroup) {
        if (newGroup == null)
            return;

        int pos = getCount() - 1;
        insert(pos, newGroup);

        adapterView.setSelection(pos);
        notifyDataSetChanged();
    }

    /**
     * Sets drop down item layout resource id.
     *
     * @param dropDownLayout the drop down item layout resource id to set.
     */
    public void setDropDownLayout(int dropDownLayout) {
        this.dropDownLayout = dropDownLayout;
    }

    /**
     * Sets item layout resource id.
     *
     * @param itemLayout the item layout resource id to set.
     */
    public void setItemLayout(int itemLayout) {
        this.itemLayout = itemLayout;
    }
}
