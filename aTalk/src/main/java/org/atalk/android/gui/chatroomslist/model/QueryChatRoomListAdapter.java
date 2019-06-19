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

import android.os.Handler;

import net.java.sip.communicator.service.contactsource.*;
import net.java.sip.communicator.util.ServiceUtils;

import org.atalk.android.gui.AndroidGUIActivator;
import org.atalk.android.gui.chatroomslist.ChatRoomListFragment;
import org.atalk.android.gui.contactlist.model.UIGroupRenderer;
import org.atalk.service.osgi.OSGiActivity;
import org.osgi.framework.ServiceReference;

import java.util.ArrayList;
import java.util.List;

import timber.log.Timber;

/**
 * Class implements adapter that can be used to search chatRoomWrapper in the list.
 *
 * @author Eng Chong Meng
 */
public class QueryChatRoomListAdapter extends BaseChatRoomListAdapter
        implements UIGroupRenderer, ContactQueryListener
{
    /**
     * Handler used to execute stuff on UI thread.
     */
    private Handler uiHandler = OSGiActivity.uiHandler;

    /**
     * The meta contact list used as a base contact source. It is capable of filtering contacts
     * itself without queries.
     */
    private ChatRoomListAdapter chatRoomList;

    /**
     * List of contact sources of type {@link ContactSourceService#SEARCH_TYPE}.
     */
    private List<ContactSourceService> sources;

    /**
     * List of results groups. Each group corresponds to results from one contact source.
     */
    private List<ResultGroup> results = new ArrayList<ResultGroup>();

    /**
     * List of queries currently handled.
     */
    private List<ContactQuery> queries = new ArrayList<ContactQuery>();

    /**
     * Creates new instance of <tt>QueryContactListAdapter</tt>.
     *
     * @param fragment parent fragment.
     * @param chatRoomListModel meta contact list model used as a base data model
     */
    public QueryChatRoomListAdapter(ChatRoomListFragment fragment, ChatRoomListAdapter chatRoomListModel)
    {
        super(fragment);
        this.chatRoomList = chatRoomListModel;
    }

    /**
     * Returns a list of all registered contact sources.
     *
     * @return a list of all registered contact sources
     */
    private List<ContactSourceService> getSources()
    {
        ServiceReference<ContactSourceService>[] serRefs = ServiceUtils.getServiceReferences(
                AndroidGUIActivator.bundleContext, ContactSourceService.class);

        List<ContactSourceService> contactSources = new ArrayList<ContactSourceService>(
                serRefs.length);
        for (ServiceReference<ContactSourceService> serRef : serRefs) {
            ContactSourceService contactSource = AndroidGUIActivator.bundleContext.getService(
                    serRef);

            if (contactSource.getType() == ContactSourceService.SEARCH_TYPE) {
                contactSources.add(contactSource);
            }
        }
        return contactSources;
    }

    /**
     * {@inheritDoc}
     */
    public void initModelData()
    {
        this.sources = getSources();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void dispose()
    {
        super.dispose();
        cancelQueries();
    }

    @Override
    public int getGroupCount()
    {
        return chatRoomList.getGroupCount() + results.size();
    }

    @Override
    public Object getGroup(int position)
    {
        int metaGroupCount = chatRoomList.getGroupCount();
        if (position < metaGroupCount) {
            return chatRoomList.getGroup(position);
        }
        else {
            return results.get(position - metaGroupCount);
        }
    }

    @Override
    public UIGroupRenderer getGroupRenderer(int groupPosition)
    {
        if (groupPosition < chatRoomList.getGroupCount()) {
            return chatRoomList.getGroupRenderer(groupPosition);
        }
        else {
            return this;
        }
    }

    @Override
    public int getChildrenCount(int groupPosition)
    {
        int metaGroupCount = chatRoomList.getGroupCount();
        if (groupPosition < metaGroupCount) {
            return chatRoomList.getChildrenCount(groupPosition);
        }
        else {
            return results.get(groupPosition - metaGroupCount).getCount();
        }
    }

    @Override
    public Object getChild(int groupPosition, int childPosition)
    {
        int metaGroupCount = chatRoomList.getGroupCount();
        if (groupPosition < metaGroupCount) {
            return chatRoomList.getChild(groupPosition, childPosition);
        }
        else {
            return results.get(0).contacts.get(childPosition);
        }
    }

    @Override
    public UIChatRoomRenderer getChatRoomRenderer(int groupPosition)
    {
        if (groupPosition < chatRoomList.getGroupCount()) {
            return chatRoomList.getChatRoomRenderer(groupPosition);
        }
        else {
            return null;
            //			return SourceContactRenderer.instance;
        }
    }

    @Override
    public void filterData(String queryStr)
    {
        cancelQueries();

        for (ContactSourceService css : sources) {
            ContactQuery query = css.createContactQuery(queryStr);
            queries.add(query);
            query.addContactQueryListener(this);
            query.start();
        }
        chatRoomList.filterData(queryStr);

        results = new ArrayList<ResultGroup>();
        notifyDataSetChanged();
    }

    private void cancelQueries()
    {
        for (ContactQuery query : queries) {
            query.cancel();
        }
        queries.clear();
    }

    @Override
    public String getDisplayName(Object groupImpl)
    {
        return ((ResultGroup) groupImpl).source.getDisplayName();
    }

    @Override
    public void contactReceived(ContactReceivedEvent contactReceivedEvent)
    {
    }

    @Override
    public void queryStatusChanged(ContactQueryStatusEvent contactQueryStatusEvent)
    {
        if (contactQueryStatusEvent.getEventType() == ContactQuery.QUERY_COMPLETED) {
            final ContactQuery query = contactQueryStatusEvent.getQuerySource();
            final ResultGroup resultGroup = new ResultGroup(query.getContactSource(),
                    query.getQueryResults());

            if (resultGroup.getCount() == 0) {
                return;
            }

            uiHandler.post(() -> {
                if (!queries.contains(query)) {
                    Timber.w("Received event for cancelled query: %s", query);
                    return;
                }

                results.add(resultGroup);
                notifyDataSetChanged();
                expandAllGroups();
            });
        }
    }

    @Override
    public void contactRemoved(ContactRemovedEvent contactRemovedEvent)
    {
        Timber.e("CONTACT REMOVED NOT IMPLEMENTED");
    }

    @Override
    public void contactChanged(ContactChangedEvent contactChangedEvent)
    {
        Timber.e("CONTACT CHANGED NOT IMPLEMENTED");
    }

    private class ResultGroup
    {
        private final List<SourceContact> contacts;
        private final ContactSourceService source;

        public ResultGroup(ContactSourceService source, List<SourceContact> results)
        {
            this.source = source;
            this.contacts = results;
        }

        int getCount()
        {
            return contacts.size();
        }
    }
}
