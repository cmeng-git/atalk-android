/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.atalk.android.gui.contactlist.model;

import android.os.Handler;

import net.java.sip.communicator.service.contactsource.*;
import net.java.sip.communicator.util.ServiceUtils;

import org.atalk.android.gui.AndroidGUIActivator;
import org.atalk.android.gui.contactlist.ContactListFragment;
import org.atalk.service.osgi.OSGiActivity;
import org.osgi.framework.ServiceReference;

import java.util.ArrayList;
import java.util.List;

import timber.log.Timber;

/**
 * Class implements adapter that can be used to search contact sources and the contact list. Meta contact list
 * is a base for this adapter and queries returned from contact sources are appended as next contact groups.
 *
 * @author Pawel Domas
 * @author Eng Chong Meng
 */
public class QueryContactListAdapter extends BaseContactListAdapter
        implements UIGroupRenderer, ContactQueryListener
{
    /**
     * Handler used to execute stuff on UI thread.
     */
    private Handler uiHandler = OSGiActivity.uiHandler;

    /**
     * The meta contact list used as a base contact source. It is capable of filtering contacts itself without queries.
     */
    private MetaContactListAdapter metaContactList;

    /**
     * List of contact sources of type {@link ContactSourceService#SEARCH_TYPE}.
     */
    private List<ContactSourceService> sources;

    /**
     * List of results groups. Each group corresponds to results from one contact source.
     */
    private List<ResultGroup> results = new ArrayList<>();

    /**
     * List of queries currently handled.
     */
    private List<ContactQuery> queries = new ArrayList<>();

    /**
     * Creates new instance of <tt>QueryContactListAdapter</tt>.
     *
     * @param fragment parent fragment.
     * @param contactListModel meta contact list model used as a base data model
     */
    public QueryContactListAdapter(ContactListFragment fragment, MetaContactListAdapter contactListModel)
    {
        super(fragment, true);
        this.metaContactList = contactListModel;
    }

    /**
     * Returns a list of all registered contact sources.
     *
     * @return a list of all registered contact sources
     */
    private List<ContactSourceService> getSources()
    {
        ServiceReference<ContactSourceService>[] serRefs
                = ServiceUtils.getServiceReferences(AndroidGUIActivator.bundleContext, ContactSourceService.class);

        List<ContactSourceService> contactSources = new ArrayList<>(serRefs.length);
        for (ServiceReference<ContactSourceService> serRef : serRefs) {
            ContactSourceService contactSource = AndroidGUIActivator.bundleContext.getService(serRef);
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
        return metaContactList.getGroupCount() + results.size();
    }

    @Override
    public Object getGroup(int position)
    {
        int metaGroupCount = metaContactList.getGroupCount();
        if ((position >= 0) &&  (position < metaGroupCount)) {
            return metaContactList.getGroup(position);
        }
        else {
            return null;
        }
    }

    @Override
    public UIGroupRenderer getGroupRenderer(int groupPosition)
    {
        if (groupPosition < metaContactList.getGroupCount()) {
            return metaContactList.getGroupRenderer(groupPosition);
        }
        else {
            return this;
        }
    }

    @Override
    public int getChildrenCount(int groupPosition)
    {
        int metaGroupCount = metaContactList.getGroupCount();
        if (groupPosition < metaGroupCount) {
            return metaContactList.getChildrenCount(groupPosition);
        }
        else {
            return results.get(groupPosition - metaGroupCount).getCount();
        }
    }

    @Override
    public Object getChild(int groupPosition, int childPosition)
    {
        int metaGroupCount = metaContactList.getGroupCount();
        if (groupPosition < metaGroupCount) {
            return metaContactList.getChild(groupPosition, childPosition);
        }
        else {
            return results.get(0).contacts.get(childPosition);
        }
    }

    @Override
    public UIContactRenderer getContactRenderer(int groupPosition)
    {
        if (groupPosition < metaContactList.getGroupCount()) {
            return metaContactList.getContactRenderer(groupPosition);
        }
        else {
            return SourceContactRenderer.instance;
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
        metaContactList.filterData(queryStr);

        results = new ArrayList<>();
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
            final ResultGroup resultGroup = new ResultGroup(query.getContactSource(), query.getQueryResults());
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
