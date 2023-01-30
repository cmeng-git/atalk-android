/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license.
 * See terms of license at gnu.org.
 */
package net.java.sip.communicator.impl.msghistory;

import net.java.sip.communicator.service.contactlist.MetaContact;
import net.java.sip.communicator.service.contactsource.*;
import net.java.sip.communicator.service.msghistory.MessageSourceContactPresenceStatus;
import net.java.sip.communicator.service.muc.ChatRoomPresenceStatus;
import net.java.sip.communicator.service.protocol.*;
import net.java.sip.communicator.service.protocol.event.*;
import net.java.sip.communicator.service.protocol.globalstatus.GlobalStatusEnum;
import net.java.sip.communicator.util.DataObject;

import org.atalk.android.R;
import org.atalk.android.aTalkApp;

import java.text.SimpleDateFormat;
import java.util.*;

/**
 * Represents a contact source displaying a recent message for contact.
 *
 * @author Damian Minkov
 * @author Eng Chong Meng
 */
public class MessageSourceContact extends DataObject
        implements SourceContact, Comparable<MessageSourceContact>
{
    /**
     * Date format used to mark today messages.
     */
    public static final String TODAY_DATE_FORMAT = "HH:mm', '";
    /**
     * Date format used to mark past messages.
     */
    public static final String PAST_DATE_FORMAT = "MMM d', '";
    /**
     * The parent service.
     */
    private final MessageSourceService service;
    /**
     * A list of all contact details.
     */
    private final List<ContactDetail> contactDetails = new LinkedList<>();
    /**
     * The address.
     */
    private String address = null;
    /**
     * The display name.
     */
    private String displayName = null;
    /**
     * The protocol provider.
     */
    private ProtocolProviderService ppService = null;
    /**
     * The status. Will reuse global status offline.
     */
    private PresenceStatus status = GlobalStatusEnum.OFFLINE;
    /**
     * The image.
     */
    private byte[] image = null;
    /**
     * The message content.
     */
    private String messageContent = null;
    /**
     * The contact instance.
     */
    private Contact contact = null;
    /**
     * The room instance.
     */
    private ChatRoom room = null;
    /**
     * The timestamp.
     */
    private Date timestamp = null;

    /**
     * Constructs <code>MessageSourceContact</code>.
     *
     * @param source the source event.
     * @param service the message source service.
     */
    MessageSourceContact(EventObject source, MessageSourceService service)
    {
        this.service = service;
        update(source);
    }

    /**
     * The protocol provider.
     *
     * @return the protocol provider.
     */
    public ProtocolProviderService getProtocolProviderService()
    {
        return ppService;
    }

    /**
     * Make sure the content of the message is not too long, as it will fill up tooltips and ui
     * components.
     */
    private void updateMessageContent()
    {
        if (isToday(timestamp)) {
            // just hour
            this.messageContent = new SimpleDateFormat(TODAY_DATE_FORMAT).format(timestamp)
                    + this.messageContent;

        }
        else {
            // just date
            this.messageContent = new SimpleDateFormat(PAST_DATE_FORMAT).format(timestamp)
                    + this.messageContent;
        }

        if (this.messageContent.length() > 60) {
            // do not display too long texts
            this.messageContent = this.messageContent.substring(0, 60);
            this.messageContent += "...";
        }
    }

    /**
     * Checks whether <code>timestamp</code> is today.
     *
     * @param timestamp the date to check
     * @return whether <code>timestamp</code> is today.
     */
    private boolean isToday(Date timestamp)
    {
        Calendar today = Calendar.getInstance();
        Calendar tsCalendar = Calendar.getInstance();
        tsCalendar.setTime(timestamp);

        return today.get(Calendar.YEAR) == tsCalendar.get(Calendar.YEAR)
                && today.get(Calendar.DAY_OF_YEAR) == tsCalendar.get(Calendar.DAY_OF_YEAR);
    }

    /**
     * Updates fields.
     *
     * @param source the event object
     */
    void update(EventObject source)
    {
        if (source instanceof MessageDeliveredEvent) {
            MessageDeliveredEvent e = (MessageDeliveredEvent) source;

            this.contact = e.getContact();
            this.address = contact.getAddress();
            this.updateDisplayName();
            this.ppService = contact.getProtocolProvider();
            this.image = contact.getImage();
            this.status = contact.getPresenceStatus();
            this.messageContent = e.getSourceMessage().getContent();
            this.timestamp = e.getTimestamp();
        }
        else if (source instanceof MessageReceivedEvent) {
            MessageReceivedEvent e = (MessageReceivedEvent) source;

            this.contact = e.getSourceContact();
            this.address = contact.getAddress();
            this.updateDisplayName();
            this.ppService = contact.getProtocolProvider();
            this.image = contact.getImage();
            this.status = contact.getPresenceStatus();
            this.messageContent = e.getSourceMessage().getContent();
            this.timestamp = e.getTimestamp();
        }
        else if (source instanceof ChatRoomMessageDeliveredEvent) {
            ChatRoomMessageDeliveredEvent e = (ChatRoomMessageDeliveredEvent) source;

            this.room = e.getSourceChatRoom();
            this.address = room.getName();
            this.displayName = room.getName();
            this.ppService = room.getParentProvider();
            this.image = null;
            this.status = room.isJoined() ? ChatRoomPresenceStatus.CHAT_ROOM_ONLINE
                    : ChatRoomPresenceStatus.CHAT_ROOM_OFFLINE;
            this.messageContent = e.getMessage().getContent();
            this.timestamp = e.getTimestamp();
        }
        else if (source instanceof ChatRoomMessageReceivedEvent) {
            ChatRoomMessageReceivedEvent e = (ChatRoomMessageReceivedEvent) source;

            this.room = e.getSourceChatRoom();
            this.address = room.getName();
            this.displayName = room.getName();
            this.ppService = room.getParentProvider();
            this.image = null;
            this.status = room.isJoined() ? ChatRoomPresenceStatus.CHAT_ROOM_ONLINE
                    : ChatRoomPresenceStatus.CHAT_ROOM_OFFLINE;
            this.messageContent = e.getMessage().getContent();
            this.timestamp = e.getTimestamp();
        }

        if (service.isSMSEnabled()) {
            this.status = MessageSourceContactPresenceStatus.MSG_SRC_CONTACT_ONLINE;
        }
        updateMessageContent();
    }

    @Override
    public String toString()
    {
        return "MessageSourceContact{" + "address='" + address
                + '\'' + ", ppService=" + ppService + '}';
    }

    /**
     * Init details. Check contact capabilities.
     *
     * @param source the source event.
     */
    void initDetails(EventObject source)
    {
        if (source instanceof MessageDeliveredEvent) {
            initDetails(false, ((MessageDeliveredEvent) source).getContact());
        }
        else if (source instanceof MessageReceivedEvent) {
            initDetails(false, ((MessageReceivedEvent) source).getSourceContact());
        }
        else if (source instanceof ChatRoomMessageDeliveredEvent
                || source instanceof ChatRoomMessageReceivedEvent) {
            initDetails(true, null);
        }
    }

    /**
     * We will the details for this source contact. Will skip OperationSetBasicInstantMessaging
     * for chat rooms.
     *
     * @param isChatRoom is current source contact a chat room.
     */
    void initDetails(boolean isChatRoom, Contact contact)
    {
        if (!isChatRoom && contact != null)
            this.updateDisplayName();

        ContactDetail contactDetail = new ContactDetail(this.address, this.displayName);
        Map<Class<? extends OperationSet>, ProtocolProviderService> preferredProviders;
        ProtocolProviderService preferredProvider = this.ppService;

        OperationSetContactCapabilities capOpSet
                = preferredProvider.getOperationSet(OperationSetContactCapabilities.class);
        Map<String, OperationSet> opsetCapabilities = null;
        if (capOpSet != null && contact != null)
            opsetCapabilities = capOpSet.getSupportedOperationSets(contact);

        if (preferredProvider != null) {
            preferredProviders = new Hashtable<>();

            LinkedList<Class<? extends OperationSet>> supportedOpSets = new LinkedList<>();

            for (Class<? extends OperationSet> opset
                    : preferredProvider.getSupportedOperationSetClasses()) {
                // skip opset IM as we want explicitly muc support
                if (opset.equals(OperationSetPresence.class)
                        || opset.equals(OperationSetPersistentPresence.class)
                        || ((isChatRoom || service.isSMSEnabled())
                        && opset.equals(OperationSetBasicInstantMessaging.class))) {
                    continue;
                }

                if (!isChatRoom && opsetCapabilities != null
                        && !opsetCapabilities.containsKey(opset.getName()))
                    continue;

                preferredProviders.put(opset, preferredProvider);
                supportedOpSets.add(opset);
            }
            contactDetail.setPreferredProviders(preferredProviders);
            contactDetail.setSupportedOpSets(supportedOpSets);
        }
        contactDetails.clear();
        contactDetails.add(contactDetail);
    }

    @Override
    public String getDisplayName()
    {
        if (this.displayName != null)
            return this.displayName;
        else
            return aTalkApp.getResString(R.string.service_gui_UNKNOWN_USER);
    }

    /**
     * Changes display name.
     *
     * @param displayName
     */
    public void setDisplayName(String displayName)
    {
        this.displayName = displayName;
    }

    @Override
    public String getContactAddress()
    {
        if (this.address != null)
            return this.address;

        return null;
    }

    @Override
    public void setContactAddress(String contactAddress)
    {
    }

    @Override
    public ContactSourceService getContactSource()
    {
        return service;
    }

    @Override
    public String getDisplayDetails()
    {
        return messageContent;
    }

    /**
     * Returns a list of available contact details.
     *
     * @return a list of available contact details
     */
    @Override
    public List<ContactDetail> getContactDetails()
    {
        return new LinkedList<>(contactDetails);
    }

    /**
     * Returns a list of all <code>ContactDetail</code>s supporting the given <code>OperationSet</code>
     * class.
     *
     * @param operationSet the <code>OperationSet</code> class we're looking for
     * @return a list of all <code>ContactDetail</code>s supporting the given <code>OperationSet</code>
     * class
     */
    @Override
    public List<ContactDetail> getContactDetails(Class<? extends OperationSet> operationSet)
    {
        List<ContactDetail> res = new LinkedList<>();

        for (ContactDetail det : contactDetails) {
            if (det.getPreferredProtocolProvider(operationSet) != null)
                res.add(det);
        }

        return res;
    }

    /**
     * Returns a list of all <code>ContactDetail</code>s corresponding to the given category.
     *
     * @param category the <code>OperationSet</code> class we're looking for
     * @return a list of all <code>ContactDetail</code>s corresponding to the given category
     */
    @Override
    public List<ContactDetail> getContactDetails(ContactDetail.Category category)
            throws OperationNotSupportedException
    {
        // We don't support category for message source history details,
        // so we return null.
        throw new OperationNotSupportedException(
                "Categories are not supported for message source contact history.");
    }

    /**
     * Returns the preferred <code>ContactDetail</code> for a given <code>OperationSet</code> class.
     *
     * @param operationSet the <code>OperationSet</code> class, for which we would like to obtain a
     * <code>ContactDetail</code>
     * @return the preferred <code>ContactDetail</code> for a given <code>OperationSet</code> class
     */
    @Override
    public ContactDetail getPreferredContactDetail(Class<? extends OperationSet> operationSet)
    {
        return contactDetails.get(0);
    }

    @Override
    public byte[] getImage()
    {
        return image;
    }

    @Override
    public boolean isDefaultImage()
    {
        return image == null;
    }

    @Override
    public PresenceStatus getPresenceStatus()
    {
        return status;
    }

    /**
     * Sets current status.
     *
     * @param status
     */
    public void setStatus(PresenceStatus status)
    {
        this.status = status;
    }

    @Override
    public int getIndex()
    {
        return service.getIndex(this);
    }

    /**
     * The contact.
     *
     * @return the contact.
     */
    public Contact getContact()
    {
        return contact;
    }

    /**
     * The room.
     *
     * @return the room.
     */
    public ChatRoom getRoom()
    {
        return room;
    }

    /**
     * The timestamp of the message.
     *
     * @return the timestamp of the message.
     */
    public Date getTimestamp()
    {
        return timestamp;
    }

    /**
     * Updates display name if contact is not null.
     */
    private void updateDisplayName()
    {
        if (this.contact == null)
            return;

        MetaContact metaContact = MessageHistoryActivator.getContactListService()
                .findMetaContactByContact(contact);

        if (metaContact == null)
            return;

        this.displayName = metaContact.getDisplayName();
    }

    /**
     * Compares two MessageSourceContacts.
     *
     * @param o the object to compare with
     * @return 0, less than zero, greater than zero, if equals, less or greater.
     */
    @Override
    public int compareTo(MessageSourceContact o)
    {
        if (o == null || o.getTimestamp() == null)
            return 1;

        return o.getTimestamp().compareTo(getTimestamp());
    }

    @Override
    public boolean equals(Object o)
    {
        if (this == o)
            return true;
        if (o == null || getClass() != o.getClass())
            return false;

        MessageSourceContact that = (MessageSourceContact) o;

        if (!address.equals(that.address))
            return false;
        if (!ppService.equals(that.ppService))
            return false;

        return true;
    }

    @Override
    public int hashCode()
    {
        int result = address.hashCode();
        result = 31 * result + ppService.hashCode();
        return result;
    }
}
