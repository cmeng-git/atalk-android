/*
 * aTalk, android VoIP and Instant Messaging client
 * Copyright 2014~2024 Eng Chong Meng
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
package org.atalk.android.gui.contactlist;

import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.TextView;

import androidx.core.content.res.ResourcesCompat;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import net.java.sip.communicator.impl.protocol.jabber.OperationSetPersistentPresenceJabberImpl;
import net.java.sip.communicator.service.contactlist.MetaContactListService;
import net.java.sip.communicator.service.protocol.Contact;
import net.java.sip.communicator.service.protocol.OperationSetPresence;
import net.java.sip.communicator.service.protocol.PresenceStatus;
import net.java.sip.communicator.service.protocol.ProtocolProviderService;
import net.java.sip.communicator.service.protocol.event.ContactBlockingStatusListener;
import net.java.sip.communicator.util.StatusUtil;
import net.java.sip.communicator.util.account.AccountUtils;

import org.atalk.android.BaseActivity;
import org.atalk.android.R;
import org.atalk.android.gui.AppGUIActivator;
import org.atalk.android.gui.util.EntityListHelper;
import org.atalk.android.util.AppImageUtil;
import org.jivesoftware.smack.XMPPConnection;
import org.jivesoftware.smackx.avatar.AvatarManager;
import org.jivesoftware.smackx.blocking.BlockingCommandManager;
import org.jxmpp.jid.DomainBareJid;
import org.jxmpp.jid.Jid;

import timber.log.Timber;

/**
 * An activity to show all the blocked contacts; user may unblock any listed contact from this view.
 */
public class ContactBlockListActivity extends BaseActivity
        implements ContactBlockingStatusListener, View.OnLongClickListener {
    private final List<OperationSetPresence> presenceOpSets = new ArrayList<>();

    // A reference map between contact and its viewHolder
    private final Map<Contact, ContactViewHolder> mContactViews = new HashMap<>();

    private final List<Contact> volatileContacts = new ArrayList<>();

    /**
     * {@inheritDoc}
     */
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.list_layout);
        setMainTitle(R.string.block_list_title);

        ListView contactBlockList = findViewById(R.id.list);
        contactBlockList.setBackgroundResource(R.color.background_light);

        BlockListAdapter blockListAdapter = new BlockListAdapter();
        contactBlockList.setAdapter(blockListAdapter);
    }

    /**
     * Get a list of all known contacts being blocked
     *
     * @return the list of all known contacts being blocked.
     */
    private List<Contact> getContactBlockList() {
        List<Contact> blockContacts = new ArrayList<>();
        volatileContacts.clear();

        // Get all the registered protocolProviders
        Collection<ProtocolProviderService> providers = AccountUtils.getRegisteredProviders();
        for (ProtocolProviderService pps : providers) {
            XMPPConnection connection = pps.getConnection();
            if (connection == null)
                continue;

            OperationSetPresence presenceOpSet = pps.getOperationSet(OperationSetPresence.class);
            if (presenceOpSet != null) {
                presenceOpSet.addContactBlockStatusListener(this);
                presenceOpSets.add(presenceOpSet);

                BlockingCommandManager bcManager = BlockingCommandManager.getInstanceFor(connection);
                try {
                    List<Jid> blockList = bcManager.getBlockList();
                    for (Jid jid : blockList) {
                        Contact contact = presenceOpSet.findContactByJid(jid);
                        // create a volatile contact if not found
                        if (contact == null) {
                            contact = ((OperationSetPersistentPresenceJabberImpl) presenceOpSet).createVolatileContact(jid);
                            volatileContacts.add(contact);
                        }
                        blockContacts.add(contact);
                        contact.setContactBlock(true);
                    }
                } catch (Exception e) {
                    Timber.w("initContactBlockStatus: %s", e.getMessage());
                }
            }
        }
        return blockContacts;
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (presenceOpSets != null) {
            for (OperationSetPresence ops : presenceOpSets)
                ops.removeContactBlockStatusListener(this);
        }

        // Remove all volatile contacts on exit, else show up in contact list
        new Thread(() -> {
            MetaContactListService metaContactListService = AppGUIActivator.getContactListService();
            for (Contact contact : volatileContacts) {
                try {
                    metaContactListService.removeContact(contact);
                } catch (Exception ex) {
                    Timber.w("Remove contact %s error: %s", contact, ex.getMessage());
                }
            }
        }).start();
    }

    @Override
    public void contactBlockingStatusChanged(Contact contact, boolean blockState) {
        ContactViewHolder contactView = mContactViews.get(contact);
        if (contactView != null) {
            ImageView blockView = contactView.contactBlockState;
            if (blockView == null) {
                Timber.w("No contact blocking status view found for %s", contact);
                return;
            }

            runOnUiThread(() -> {
                if (contact.isContactBlock())
                    blockView.setImageResource(R.drawable.contact_block);
                else
                    blockView.setImageDrawable(null);
            });
        }
    }

    public Drawable getStatusIcon(Contact contact) {
        PresenceStatus presenceStatus = contact.getPresenceStatus();
        if (presenceStatus != null) {
            byte[] statusBlob = StatusUtil.getContactStatusIcon(presenceStatus);
            if (statusBlob != null)
                return AppImageUtil.drawableFromBytes(statusBlob);
        }
        return null;
    }

    /**
     * Retrieve the contact from viewHolder to take action on.
     */
    @Override
    public boolean onLongClick(View view) {
        Contact contact = ((ContactViewHolder) view.getTag()).contact;
        EntityListHelper.setEntityBlockState(getBaseContext(), contact, !contact.isContactBlock());
        return true;
    }

    /**
     * Adapter which displays block state for each contact in the list.
     */
    private class BlockListAdapter extends BaseAdapter {
        /**
         * The list of currently blocked contacts.
         */
        private final List<Contact> contactBlockList;

        /**
         * Creates new instance of <code>BlockListAdapter</code>.
         */
        BlockListAdapter() {
            contactBlockList = getContactBlockList();
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public int getCount() {
            return contactBlockList.size();
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public Contact getItem(int position) {
            return contactBlockList.get(position);
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public long getItemId(int position) {
            return position;
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            // Keeps reference to avoid future findViewById()
            ContactViewHolder contactViewHolder;
            if (convertView == null) {
                convertView = getLayoutInflater().inflate(R.layout.contact_block_list_row, parent, false);

                contactViewHolder = new ContactViewHolder();
                contactViewHolder.contactAvatar = convertView.findViewById(R.id.avatarIcon);
                contactViewHolder.contactBlockState = convertView.findViewById(R.id.contactBlockIcon);
                contactViewHolder.contactStatus = convertView.findViewById(R.id.contactStatusIcon);

                contactViewHolder.displayName = convertView.findViewById(R.id.displayName);
                contactViewHolder.statusMessage = convertView.findViewById(R.id.statusMessage);

                convertView.setTag(contactViewHolder);
            }
            else {
                contactViewHolder = (ContactViewHolder) convertView.getTag();
            }

            // update contact display info
            Contact contact = contactBlockList.get(position);
            contactViewHolder.contact = contact;

            contactViewHolder.displayName.setText(contact.getDisplayName());
            contactViewHolder.statusMessage.setText(contact.getStatusMessage());
            contactViewHolder.statusMessage.setSelected(true); // to start scroll the text.

            // Set avatar.
            byte[] byteAvatar = AvatarManager.getAvatarImageByJid(contact.getJid().asBareJid());
            Drawable avatar = AppImageUtil.roundedDrawableFromBytes(byteAvatar);
            if (avatar == null) {
                avatar = ResourcesCompat.getDrawable(getResources(),
                        contact.getJid() instanceof DomainBareJid ? R.drawable.domain_icon : R.drawable.contact_avatar, null);
            }

            contactViewHolder.contactAvatar.setImageDrawable(avatar);
            contactViewHolder.contactStatus.setImageDrawable(getStatusIcon(contact));

            if (contact.isContactBlock()) {
                contactViewHolder.contactBlockState.setImageResource(R.drawable.contact_block);
            }
            else {
                contactViewHolder.contactBlockState.setImageDrawable(null);
            }

            convertView.setOnLongClickListener(ContactBlockListActivity.this);
            mContactViews.put(contact, contactViewHolder);
            return convertView;
        }
    }

    private static class ContactViewHolder {
        TextView displayName;
        TextView statusMessage;
        ImageView contactAvatar;
        ImageView contactBlockState;
        ImageView contactStatus;
        Contact contact;
    }
}
