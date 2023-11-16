/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license.
 * See terms of license at gnu.org.
 */
package net.java.sip.communicator.impl.msghistory;

import net.java.sip.communicator.service.contactsource.AsyncContactQuery;
import net.java.sip.communicator.service.contactsource.SourceContact;
import net.java.sip.communicator.service.protocol.Contact;
import net.java.sip.communicator.service.protocol.PresenceStatus;

import java.util.EventObject;
import java.util.regex.Pattern;

/**
 * The query which creates source contacts and uses the values stored in
 * <code>MessageSourceService</code>.
 *
 * @author Damian Minkov
 */
public class MessageSourceContactQuery extends AsyncContactQuery<MessageSourceService>
{
	/**
	 * Constructs.
	 *
	 * @param messageSourceService
	 */
	MessageSourceContactQuery(MessageSourceService messageSourceService)
	{
		super(messageSourceService,
				Pattern.compile("", Pattern.CASE_INSENSITIVE | Pattern.LITERAL), false);
	}

	/**
	 * Creates <code>MessageSourceContact</code> for all currently cached recent messages in the
	 * <code>MessageSourceService</code>.
	 */
	@Override
	public void run()
	{
		getContactSource().updateRecentMessages();
	}

	/**
	 * Updates capabilities from <code>EventObject</code> for the found <code>MessageSourceContact</code>
	 * equals to the <code>Object</code> supplied. Note that Object may not be
	 * <code>MessageSourceContact</code>, but  its equals method can return true for message source
	 * contact instances.
	 *
	 * @param srcObj
	 * 		used to search for <code>MessageSourceContact</code>
	 * @param eventObj
	 * 		the values used for the update
	 */
	public void updateCapabilities(Object srcObj, EventObject eventObj)
	{
		for (SourceContact msc : getQueryResults()) {
			if (srcObj.equals(msc) && msc instanceof MessageSourceContact) {
				((MessageSourceContact) msc).initDetails(eventObj);
				break;
			}
		}
	}

	/**
	 * Updates capabilities from <code>Contact</code> for the found <code>MessageSourceContact</code>
	 * equals to the <code>Object</code> supplied. Note that Object may not be
	 * <code>MessageSourceContact</code>, but its equals method can return true for message source
	 * contact instances.
	 *
	 * @param srcObj
	 * 		used to search for <code>MessageSourceContact</code>
	 * @param contact
	 * 		the values used for the update
	 */
	public void updateCapabilities(Object srcObj, Contact contact)
	{
		for (SourceContact msc : getQueryResults()) {
			if (srcObj.equals(msc) && msc instanceof MessageSourceContact) {
				((MessageSourceContact) msc).initDetails(false, contact);
				break;
			}
		}
	}

	/**
	 * Notifies the <code>ContactQueryListener</code>s registered with this <code>ContactQuery</code>
	 * that a <code>SourceContact</code> has been changed. Note that Object may not be
	 * <code>MessageSourceContact</code>, but its equals method can return true for message source
	 * contact instances.
	 *
	 * @param srcObj
	 * 		the <code>Object</code> representing a recent message which has been changed and
	 * 		corresponding <code>SourceContact</code> which the registered
	 * 		<code>ContactQueryListener</code>s are to be notified about
	 */
	public void updateContact(Object srcObj, EventObject eventObject)
	{
		for (SourceContact msc : getQueryResults()) {
			if (srcObj.equals(msc) && msc instanceof MessageSourceContact) {
				((MessageSourceContact) msc).update(eventObject);
				super.fireContactChanged(msc);
				break;
			}
		}
	}

	/**
	 * Notifies the <code>ContactQueryListener</code>s registered with this <code>ContactQuery</code>
	 * that a <code>SourceContact</code> has been changed. Note that Object may not be
	 * <code>MessageSourceContact</code>, but its equals method can return true for message source
	 * contact instances.
	 *
	 * @param srcObj
	 * 		the <code>Object</code> representing a recent message which has been changed and
	 * 		corresponding <code>SourceContact</code> which the registered
	 * 		<code>ContactQueryListener</code>s are to be notified about
	 */
	public void fireContactChanged(Object srcObj)
	{
		for (SourceContact msc : getQueryResults()) {
			if (srcObj.equals(msc) && msc instanceof MessageSourceContact) {
				super.fireContactChanged(msc);
				break;
			}
		}
	}

	/**
	 * Notifies the <code>ContactQueryListener</code>s registered with this <code>ContactQuery</code>
	 * that a <code>SourceContact</code> has been changed. Note that Object may not be
	 * <code>MessageSourceContact</code>, but its equals method can return true for message source
	 * contact instances.
	 *
	 * @param srcObj
	 * 		the <code>Object</code> representing a recent message which has been changed and
	 * 		corresponding <code>SourceContact</code> which the registered
	 * 		<code>ContactQueryListener</code>s are to be notified about
	 */
	public void updateContactStatus(Object srcObj, PresenceStatus status)
	{
		for (SourceContact msc : getQueryResults()) {
			if (srcObj.equals(msc) && msc instanceof MessageSourceContact) {
				((MessageSourceContact) msc).setStatus(status);
				super.fireContactChanged(msc);
				break;
			}
		}
	}

	/**
	 * Notifies the <code>ContactQueryListener</code>s registered with this <code>ContactQuery</code>
	 * that a <code>SourceContact</code> has been changed. Note that Object may not be
	 * <code>MessageSourceContact</code>, but its equals method can return true for message source
	 * contact instances.
	 *
	 * @param srcObj
	 * 		the <code>Object</code> representing a recent message which has been changed and
	 * 		corresponding <code>SourceContact</code> which the registered
	 * 		<code>ContactQueryListener</code>s are to be notified about
	 */
	public void updateContactDisplayName(Object srcObj, String newName)
	{
		for (SourceContact msc : getQueryResults()) {
			if (srcObj.equals(msc) && msc instanceof MessageSourceContact) {
				((MessageSourceContact) msc).setDisplayName(newName);
				super.fireContactChanged(msc);
				break;
			}
		}
	}

	/**
	 * Notifies the <code>ContactQueryListener</code>s registered with this <code>ContactQuery</code>
	 * that a <code>SourceContact</code> has been removed. Note that Object may not be
	 * <code>MessageSourceContact</code>, but its equals method can return true for message source
	 * contact instances.
	 *
	 * @param srcObj
	 * 		representing the message and its corresponding <code>SourceContact</code> which has been
	 * 		removed and which the registered <code>ContactQueryListener</code>s are to be notified about
	 */
	public void fireContactRemoved(Object srcObj)
	{
		for (SourceContact msc : getQueryResults()) {
			if (srcObj.equals(msc)) {
				super.fireContactRemoved(msc);
				break;
			}
		}
	}

	/**
	 * Adds a specific <code>SourceContact</code> to the list of <code>SourceContact</code>s to be
	 * returned by this <code>ContactQuery</code> in response to {@link #getQueryResults()}.
	 *
	 * @param sourceContact
	 * 		the <code>SourceContact</code> to be added to the <code>queryResults</code> of this
	 * 		<code>ContactQuery</code>
	 * @return <code>true</code> if the <code>queryResults</code> of this <code>ContactQuery</code> has
	 * changed in response to the call
	 */
	public boolean addQueryResult(SourceContact sourceContact)
	{
		return super.addQueryResult(sourceContact, false);
	}
}
