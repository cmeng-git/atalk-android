/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.atalk.android.gui.chat;

import net.java.sip.communicator.service.contactlist.MetaContact;
import net.java.sip.communicator.service.contactlist.MetaContactListService;
import net.java.sip.communicator.service.contactlist.event.MetaContactAvatarUpdateEvent;
import net.java.sip.communicator.service.contactlist.event.MetaContactEvent;
import net.java.sip.communicator.service.contactlist.event.MetaContactGroupEvent;
import net.java.sip.communicator.service.contactlist.event.MetaContactListListener;
import net.java.sip.communicator.service.contactlist.event.MetaContactModifiedEvent;
import net.java.sip.communicator.service.contactlist.event.MetaContactMovedEvent;
import net.java.sip.communicator.service.contactlist.event.MetaContactRenamedEvent;
import net.java.sip.communicator.service.contactlist.event.ProtoContactEvent;
import net.java.sip.communicator.service.filehistory.FileRecord;
import net.java.sip.communicator.service.metahistory.MetaHistoryService;
import net.java.sip.communicator.service.protocol.ChatRoomMember;
import net.java.sip.communicator.service.protocol.Contact;
import net.java.sip.communicator.service.protocol.ContactGroup;
import net.java.sip.communicator.service.protocol.ContactResource;
import net.java.sip.communicator.service.protocol.OperationSetBasicInstantMessaging;
import net.java.sip.communicator.service.protocol.PresenceStatus;
import net.java.sip.communicator.service.protocol.event.ContactResourceEvent;
import net.java.sip.communicator.service.protocol.event.ContactResourceListener;
import net.java.sip.communicator.service.protocol.event.MessageDeliveredEvent;
import net.java.sip.communicator.service.protocol.event.MessageReceivedEvent;
import net.java.sip.communicator.util.ConfigurationUtils;

import org.apache.commons.lang3.StringUtils;
import org.atalk.android.R;
import org.atalk.android.aTalkApp;
import org.atalk.android.gui.AndroidGUIActivator;
import org.json.JSONArray;
import org.json.JSONException;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.Iterator;
import java.util.List;

/**
 * An implementation of the <code>ChatSession</code> interface that represents a user-to-user chat session.
 *
 * @author Yana Stamcheva
 * @author Lubomir Marinov
 * @author Eng Chong Meng
 */
public class MetaContactChatSession extends ChatSession
        implements MetaContactListListener, ContactResourceListener {

    private final MetaContact metaContact;
    private final MetaContactListService metaContactListService;

    /**
     * The current chat transport used for messaging.
     */
    private ChatTransport currentChatTransport;

    /**
     * The object used for rendering.
     */
    private final ChatPanel sessionRenderer;

    /**
     * Creates an instance of <code>MetaContactChatSession</code> by specifying the
     * renderer, which gives the connection with the UI, the meta contact
     * corresponding to the session and the protocol contact to be used as transport.
     *
     * @param chatPanel the renderer, which gives the connection with the UI.
     * @param metaContact the meta contact corresponding to the session and the protocol contact.
     * @param protocolContact the protocol contact to be used as transport.
     * @param contactResource the specific resource to be used as transport
     */
    public MetaContactChatSession(ChatPanel chatPanel, MetaContact metaContact,
            Contact protocolContact, ContactResource contactResource) {
        this.sessionRenderer = chatPanel;
        this.metaContact = metaContact;
        persistableAddress = protocolContact.getPersistableAddress();

        ChatContact<?> chatContact = new MetaContactChatContact(metaContact);
        chatParticipants.add(chatContact);

        this.initChatTransports(protocolContact, contactResource);

        // Obtain the MetaContactListService and add this class to it as a
        // listener of all events concerning the contact list.
        metaContactListService = AndroidGUIActivator.getContactListService();
        if (metaContactListService != null)
            metaContactListService.addMetaContactListListener(this);
    }

    /**
     * Returns the entityBareJid of the <code>MetaContact</code>
     *
     * @return the entityBareJid of this chat
     */
    @Override
    public String getChatEntity() {
        String entityJid = metaContact.getDefaultContact().getAddress();
        if (StringUtils.isEmpty(entityJid))
            entityJid = aTalkApp.getResString(R.string.service_gui_UNKNOWN);
        return entityJid;
    }

    /**
     * Returns a collection of the last N number of messages given by count.
     *
     * @param count The number of messages from history to return.
     *
     * @return a collection of the last N number of messages given by count.
     */
    @Override
    public Collection<Object> getHistory(int count) {
        final MetaHistoryService metaHistory = AndroidGUIActivator.getMetaHistoryService();

        // If the MetaHistoryService is not registered we have nothing to do here. The history
        // could be "disabled" from the user through one of the configuration forms.
        if (metaHistory == null)
            return null;

        return metaHistory.findLast(chatHistoryFilter, metaContact, ConfigurationUtils.getChatHistorySize());
    }

    /**
     * Returns a collection of the last N number of messages given by count.
     *
     * @param date The date up to which we're looking for messages.
     * @param count The number of messages from history to return.
     *
     * @return a collection of the last N number of messages given by count.
     */
    @Override
    public Collection<Object> getHistoryBeforeDate(Date date, int count) {
        final MetaHistoryService metaHistory = AndroidGUIActivator.getMetaHistoryService();

        // If the MetaHistoryService is not registered we have nothing to do here. The history
        // could be "disabled" from the user through one of the configuration forms.
        if (metaHistory == null)
            return null;

        return metaHistory.findLastMessagesBefore(chatHistoryFilter, metaContact, date,
                ConfigurationUtils.getChatHistorySize());
    }

    /**
     * Returns a collection of the last N number of messages given by count.
     *
     * @param date The date from which we're looking for messages.
     * @param count The number of messages from history to return.
     *
     * @return a collection of the last N number of messages given by count.
     */
    @Override
    public Collection<Object> getHistoryAfterDate(Date date, int count) {
        final MetaHistoryService metaHistory = AndroidGUIActivator.getMetaHistoryService();

        // If the MetaHistoryService is not registered we have nothing to do here. The history
        // could be "disabled" from the user through one of the configuration forms.
        if (metaHistory == null)
            return null;

        return metaHistory.findFirstMessagesAfter(chatHistoryFilter, metaContact, date,
                ConfigurationUtils.getChatHistorySize());
    }

    /**
     * Returns the start date of the history of this chat session.
     *
     * @return the start date of the history of this chat session.
     */
    @Override
    public Date getHistoryStartDate() {
        Date startHistoryDate = new Date(0);
        MetaHistoryService metaHistory = AndroidGUIActivator.getMetaHistoryService();

        // If the MetaHistoryService is not registered we have nothing to do here. The history
        // could be "disabled" from the user through one of the configuration forms.
        if (metaHistory == null)
            return startHistoryDate;

        Collection<Object> firstMessage
                = metaHistory.findFirstMessagesAfter(chatHistoryFilter, metaContact, new Date(0), 1);
        if (firstMessage.size() > 0) {
            Iterator<Object> i = firstMessage.iterator();

            Object o = i.next();
            if (o instanceof MessageDeliveredEvent) {
                MessageDeliveredEvent evt = (MessageDeliveredEvent) o;
                startHistoryDate = evt.getTimestamp();
            } else if (o instanceof MessageReceivedEvent) {
                MessageReceivedEvent evt = (MessageReceivedEvent) o;
                startHistoryDate = evt.getTimestamp();
            } else if (o instanceof FileRecord) {
                FileRecord fileRecord = (FileRecord) o;
                startHistoryDate = fileRecord.getDate();
            }
        }
        return startHistoryDate;
    }

    /**
     * Returns the end date of the history of this chat session.
     *
     * @return the end date of the history of this chat session.
     */
    @Override
    public Date getHistoryEndDate() {
        Date endHistoryDate = new Date(0);
        MetaHistoryService metaHistory = AndroidGUIActivator.getMetaHistoryService();

        // If the MetaHistoryService is not registered we have nothing to do here. The history
        // could be "disabled" from the user through one of the configuration forms.
        if (metaHistory == null)
            return endHistoryDate;

        Collection<Object> lastMessage = metaHistory.findLastMessagesBefore(
                chatHistoryFilter, metaContact, new Date(Long.MAX_VALUE), 1);
        if (lastMessage.size() > 0) {
            Iterator<Object> i1 = lastMessage.iterator();

            Object o1 = i1.next();
            if (o1 instanceof MessageDeliveredEvent) {
                MessageDeliveredEvent evt = (MessageDeliveredEvent) o1;
                endHistoryDate = evt.getTimestamp();
            } else if (o1 instanceof MessageReceivedEvent) {
                MessageReceivedEvent evt = (MessageReceivedEvent) o1;
                endHistoryDate = evt.getTimestamp();
            } else if (o1 instanceof FileRecord) {
                FileRecord fileRecord = (FileRecord) o1;
                endHistoryDate = fileRecord.getDate();
            }
        }
        return endHistoryDate;
    }

    /**
     * Returns the default mobile number used to send sms-es in this session.
     *
     * @return the default mobile number used to send sms-es in this session.
     */
    @Override
    public String getDefaultSmsNumber() {
        String smsNumber;
        JSONArray jsonArray = metaContact.getDetails("mobile");
        if (jsonArray != null && jsonArray.length() > 0) {
            try {
                smsNumber = jsonArray.getString(0);
                return smsNumber;
            } catch (JSONException e) {
                e.printStackTrace();
            }
        }
        return null;
    }

    /**
     * Sets the default mobile number used to send sms-es in this session.
     *
     * @param smsPhoneNumber The default mobile number used to send sms-es in this session.
     */
    @Override
    public void setDefaultSmsNumber(String smsPhoneNumber) {
        metaContact.addDetail("mobile", smsPhoneNumber);
    }

    /**
     * Initializes all chat transports for this chat session.
     *
     * @param protocolContact the <code>Contact</code> which is to be selected into this instance as the current
     * i.e. its <code>ChatTransport</code> is to be selected as <code>currentChatTransport</code>
     * @param contactResource the <code>ContactResource</code>, which is to be selected into this instance
     * as the current <code>ChatTransport</code> if indicated
     */
    private void initChatTransports(Contact protocolContact, ContactResource contactResource) {
        Iterator<Contact> protocolContacts = metaContact.getContacts();
        while (protocolContacts.hasNext()) {
            Contact contact = protocolContacts.next();

            addChatTransports(contact,
                    (contactResource != null) ? contactResource.getResourceName() : null,
                    contact.equals(protocolContact));
        }
    }

    /**
     * Returns the currently used transport for all operation within this chat session.
     * Note: currentChatTransport == null if pps.isRegistered is false
     *
     * @return the currently used transport for all operation within this chat session.
     */
    @Override
    public ChatTransport getCurrentChatTransport() {
        return currentChatTransport;
    }

    /**
     * Sets the transport that will be used for all operations within this chat session.
     *
     * @param chatTransport The transport to set as a default transport for this session.
     */
    @Override
    public void setCurrentChatTransport(ChatTransport chatTransport) {
        this.currentChatTransport = chatTransport;
        fireCurrentChatTransportChange();
    }

    public void childContactsReordered(MetaContactGroupEvent evt) {
    }

    public void metaContactAdded(MetaContactEvent evt) {
    }

    public void metaContactGroupAdded(MetaContactGroupEvent evt) {
    }

    public void metaContactGroupModified(MetaContactGroupEvent evt) {
    }

    public void metaContactGroupRemoved(MetaContactGroupEvent evt) {
    }

    public void metaContactModified(MetaContactModifiedEvent evt) {
    }

    public void metaContactMoved(MetaContactMovedEvent evt) {
    }

    public void metaContactRemoved(MetaContactEvent evt) {
    }

    public void metaContactAvatarUpdated(MetaContactAvatarUpdateEvent evt) {
    }

    /**
     * Implements <code>MetaContactListListener.metaContactRenamed</code> method.
     * When a meta contact is renamed, updates all related labels in this chat panel.
     * When a meta contact is renamed, updates all related labels in this chat panel.
     *
     * @param evt the <code>MetaContactRenamedEvent</code> that notified us
     */
    public void metaContactRenamed(MetaContactRenamedEvent evt) {
        String newName = evt.getNewDisplayName();

        if (evt.getSourceMetaContact().equals(metaContact)) {
            ChatContact<?> chatContact = findChatContactByMetaContact(evt.getSourceMetaContact());
            sessionRenderer.setContactName(chatContact, newName);
        }
    }

    /**
     * Implements <code>MetaContactListListener.protoContactAdded</code> method.
     * When a proto contact is added, updates the "send via" selector box.
     */
    public void protoContactAdded(ProtoContactEvent evt) {
        if (evt.getNewParent().equals(metaContact)) {
            addChatTransports(evt.getProtoContact(), null, false);
        }
    }

    /**
     * Implements <code>MetaContactListListener.protoContactMoved</code> method.
     * When a proto contact is moved, updates the "send via" selector box.
     *
     * @param evt the <code>ProtoContactEvent</code> that contains information about
     * the old and the new parent of the contact
     */
    public void protoContactMoved(ProtoContactEvent evt) {
        if (evt.getOldParent().equals(metaContact)) {
            protoContactRemoved(evt);
        } else if (evt.getNewParent().equals(metaContact)) {
            protoContactAdded(evt);
        }
    }

    /**
     * Implements <code>MetaContactListListener.protoContactRemoved</code> method.
     * When a proto contact is removed, updates the "send via" selector box.
     */
    public void protoContactRemoved(ProtoContactEvent evt) {
        if (evt.getOldParent().equals(metaContact)) {
            Contact protoContact = evt.getProtoContact();

            List<ChatTransport> transports;
            synchronized (chatTransports) {
                transports = new ArrayList<>(chatTransports);
            }

            for (ChatTransport chatTransport : transports) {
                if (((MetaContactChatTransport) chatTransport).getContact().equals(protoContact)) {
                    removeChatTransport(chatTransport);
                }
            }
        }
    }

    /**
     * Returns the <code>ChatContact</code> corresponding to the given <code>MetaContact</code>.
     *
     * @param metaContact the <code>MetaContact</code> to search for
     *
     * @return the <code>ChatContact</code> corresponding to the given <code>MetaContact</code>.
     */
    private ChatContact<?> findChatContactByMetaContact(MetaContact metaContact) {
        for (ChatContact<?> chatContact : chatParticipants) {
            Object chatSourceContact = chatContact.getDescriptor();
            if (chatSourceContact instanceof MetaContact) {
                MetaContact metaChatContact = (MetaContact) chatSourceContact;
                if (metaChatContact.equals(metaContact))
                    return chatContact;
            } else {
                ChatRoomMember metaChatContact = (ChatRoomMember) chatSourceContact;
                Contact contact = metaChatContact.getContact();
                MetaContact parentMetaContact
                        = AndroidGUIActivator.getContactListService().findMetaContactByContact(contact);
                if (parentMetaContact != null && parentMetaContact.equals(metaContact))
                    return chatContact;
            }
        }
        return null;
    }

    /**
     * Disposes this chat session.
     */
    @Override
    public void dispose() {
        if (metaContactListService != null)
            metaContactListService.removeMetaContactListListener(this);

        for (ChatTransport chatTransport : chatTransports) {
            ((Contact) chatTransport.getDescriptor()).removeResourceListener(this);
            chatTransport.dispose();
        }
    }

    /**
     * Returns the <code>ChatSessionRenderer</code> that provides the connection between
     * this chat session and its UI.
     *
     * @return The <code>ChatSessionRenderer</code>.
     */
    @Override
    public ChatPanel getChatSessionRenderer() {
        return sessionRenderer;
    }

    /**
     * Returns the descriptor of this chat session.
     *
     * @return the descriptor i.e. MetaContact of this chat session.
     */
    @Override
    public Object getDescriptor() {
        return metaContact;
    }

    /**
     * Returns the chat identifier.
     *
     * @return the chat identifier
     */
    public String getChatId() {
        return metaContact.getMetaUID();
    }

    /**
     * Returns {@code true} if this contact is persistent, otherwise returns {@code false}.
     *
     * @return {@code true} if this contact is persistent, otherwise returns {@code false}.
     */
    @Override
    public boolean isDescriptorPersistent() {
        if (metaContact == null)
            return false;

        Contact defaultContact = metaContact.getDefaultContact(OperationSetBasicInstantMessaging.class);
        if (defaultContact == null)
            return false;

        boolean isParentPersist = true;
        boolean isParentResolved = true;

        ContactGroup parent = defaultContact.getParentContactGroup();
        if (parent != null) {
            isParentPersist = parent.isPersistent();
            isParentResolved = parent.isResolved();
        }

        return (defaultContact.isPersistent()
                || defaultContact.isResolved()
                || isParentPersist
                || isParentResolved);
    }

    /**
     * Implements the <code>ChatPanel.getChatStatusIcon</code> method.
     *
     * @return the status icon corresponding to this chat room
     */
    @Override
    public byte[] getChatStatusIcon() {
        if (this.metaContact == null) {
            return null;
        }

        Contact c = this.metaContact.getDefaultContact();
        if (c == null) {
            return null;
        }

        PresenceStatus status = c.getPresenceStatus();
        if (status == null) {
            return null;
        }
        return status.getStatusIcon();
    }

    /**
     * Returns the avatar icon of this chat session.
     *
     * @return the avatar icon of this chat session.
     */
    @Override
    public byte[] getChatAvatar() {
        return metaContact.getAvatar();
    }

    public void protoContactModified(ProtoContactEvent evt) {
    }

    public void protoContactRenamed(ProtoContactEvent evt) {
    }

    /**
     * Implements ChatSession#isContactListSupported().
     */
    @Override
    public boolean isContactListSupported() {
        return false;
    }

    /**
     * Adds all chat transports for the given <code>contact</code>.
     *
     * @param contact the <code>Contact</code>, which transports to add
     * @param resourceName the resource to be pre-selected
     */
    private void addChatTransports(Contact contact, String resourceName, boolean isSelectedContact) {
        MetaContactChatTransport chatTransport = null;
        Collection<ContactResource> contactResources = contact.getResources();

        if (contact.supportResources() && (contactResources != null) && contactResources.size() > 0) {
            if (contactResources.size() > 1) {
                chatTransport = new MetaContactChatTransport(this, contact);
                addChatTransport(chatTransport);
            }

            for (ContactResource resource : contactResources) {
                MetaContactChatTransport resourceTransport = new MetaContactChatTransport(
                        this, contact, resource, (contact.getResources().size() > 1));

                addChatTransport(resourceTransport);
                if (resource.getResourceName().equals(resourceName)
                        || (contactResources.size() == 1)) {
                    chatTransport = resourceTransport;
                }
            }
        } else {
            chatTransport = new MetaContactChatTransport(this, contact);
            addChatTransport(chatTransport);
        }

        // If this is the selected contact we set it as a selected transport.
        if (isSelectedContact) {
            currentChatTransport = chatTransport;
            // sessionRenderer.setSelectedChatTransport(chatTransport, false);
        }

        // If no current transport is set we choose the first online from the list.
        if (currentChatTransport == null) {
            for (ChatTransport ct : chatTransports) {
                if (ct.getStatus() != null && ct.getStatus().isOnline()) {
                    currentChatTransport = ct;
                    break;
                }
            }

            // if still nothing selected, choose the first one
            if (currentChatTransport == null)
                currentChatTransport = chatTransports.get(0);
            // sessionRenderer.setSelectedChatTransport(currentChatTransport, false);
        }

        if (contact.supportResources()) {
            contact.addResourceListener(this);
        }
    }

    private void addChatTransport(ChatTransport chatTransport) {
        synchronized (chatTransports) {
            chatTransports.add(chatTransport);
        }
        // sessionRenderer.addChatTransport(chatTransport);
    }

    /**
     * Removes the given <code>ChatTransport</code>.
     *
     * @param chatTransport the <code>ChatTransport</code>.
     */
    private void removeChatTransport(ChatTransport chatTransport) {
        synchronized (chatTransports) {
            chatTransports.remove(chatTransport);
        }
        // sessionRenderer.removeChatTransport(chatTransport);
        chatTransport.dispose();
        if (chatTransport.equals(currentChatTransport))
            currentChatTransport = null;
    }

    /**
     * Removes the given <code>ChatTransport</code>.
     *
     * @param contact the <code>ChatTransport</code>.
     */
    private void removeChatTransports(Contact contact) {
        List<ChatTransport> transports;
        synchronized (chatTransports) {
            transports = new ArrayList<>(chatTransports);
        }

        for (ChatTransport transport : transports) {
            MetaContactChatTransport metaTransport = (MetaContactChatTransport) transport;

            if (metaTransport.getContact().equals(contact))
                removeChatTransport(metaTransport);
        }
        contact.removeResourceListener(this);
    }

    /**
     * Updates the chat transports for the given contact.
     *
     * @param contact the contact, which related transports to update
     */
    private void updateChatTransports(Contact contact) {
        if (currentChatTransport != null) {
            boolean isSelectedContact = ((MetaContactChatTransport) currentChatTransport).getContact().equals(contact);
            String resourceName = currentChatTransport.getResourceName();
            boolean isResourceSelected = isSelectedContact && (resourceName != null);

            removeChatTransports(contact);
            if (isResourceSelected)
                addChatTransports(contact, resourceName, true);
            else
                addChatTransports(contact, null, isSelectedContact);
        }
    }

    /**
     * Called when a new <code>ContactResource</code> has been added to the list of available <code>Contact</code> resources.
     *
     * @param event the <code>ContactResourceEvent</code> that notified us
     */
    public void contactResourceAdded(ContactResourceEvent event) {
        Contact contact = event.getContact();
        if (metaContact.containsContact(contact)) {
            updateChatTransports(contact);
        }
    }

    /**
     * Called when a <code>ContactResource</code> has been removed to the list of available <code>Contact</code> resources.
     *
     * @param event the <code>ContactResourceEvent</code> that notified us
     */
    public void contactResourceRemoved(ContactResourceEvent event) {
        Contact contact = event.getContact();
        if (metaContact.containsContact(contact)) {
            updateChatTransports(contact);
        }
    }

    /**
     * Called when a <code>ContactResource</code> in the list of available <code>Contact</code> resources has been modified.
     *
     * @param event the <code>ContactResourceEvent</code> that notified us
     */
    public void contactResourceModified(ContactResourceEvent event) {
        Contact contact = event.getContact();
        if (metaContact.containsContact(contact)) {
            ChatTransport transport = findChatTransportForResource(event.getContactResource());

            if (transport != null) {
                sessionRenderer.updateChatTransportStatus(transport);
            }
        }
    }

    /**
     * Finds the <code>ChatTransport</code> corresponding to the given contact <code>resource</code>.
     *
     * @param resource the <code>ContactResource</code>, which corresponding transport we're looking for
     *
     * @return the <code>ChatTransport</code> corresponding to the given contact <code>resource</code>
     */
    private ChatTransport findChatTransportForResource(ContactResource resource) {
        List<ChatTransport> transports;
        synchronized (chatTransports) {
            transports = new ArrayList<>(chatTransports);
        }

        for (ChatTransport chatTransport : transports) {
            if (chatTransport.getDescriptor().equals(resource.getContact())
                    && (chatTransport.getResourceName() != null)
                    && chatTransport.getResourceName().equals(resource.getResourceName()))
                return chatTransport;
        }
        return null;
    }
}
