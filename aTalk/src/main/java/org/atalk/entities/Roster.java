package org.atalk.entities;

import net.java.sip.communicator.impl.protocol.jabber.ContactJabberImpl;
import net.java.sip.communicator.service.protocol.AccountID;
import net.java.sip.communicator.service.protocol.globalstatus.GlobalStatusEnum;

import org.jxmpp.jid.Jid;
import org.jxmpp.jid.impl.JidCreate;
import org.jxmpp.stringprep.XmppStringprepException;

import java.util.*;


public class Roster
{
	final AccountID accountId;
	final HashMap<Jid, ContactJabberImpl> contacts = new HashMap<>();
	private String version = null;

	public Roster(AccountID accountId) {
		this.accountId = accountId;
	}

	public ContactJabberImpl getContactFromRoster(Jid jid) {
		if (jid == null) {
			return null;
		}

		synchronized (this.contacts) {
			ContactJabberImpl contact = contacts.get(jid.asBareJid());
			if (contact != null && contact.isResolved()) {
				return contact;
			} else {
				return null;
			}
		}
	}

	public ContactJabberImpl getContact(final Jid jid) {
		synchronized (this.contacts) {
			if (!contacts.containsKey(jid.asBareJid())) {
				ContactJabberImpl contact = null; //new ContactJabberImpl(jid.asBareJid());
				// contact.setAccount(accountId);

				contacts.put(jid.asBareJid(), contact);
				return contact;
			}
			return contacts.get(jid.asBareJid());
		}
	}

	public void clearPresences() {
		for (ContactJabberImpl ContactJabberImpl : getContacts()) {
			ContactJabberImpl.updatePresenceStatus(GlobalStatusEnum.OFFLINE);
		}
	}

	public void markAllAsNotInRoster() {
		for (ContactJabberImpl contact : getContacts()) {
			// contact.resetOption(ContactJabberImpl.Options.IN_ROSTER);
		}
	}

	public List<ContactJabberImpl> getWithSystemAccounts() {
		List<ContactJabberImpl> with = getContacts();
		for(Iterator<ContactJabberImpl> iterator = with.iterator(); iterator.hasNext();) {
			ContactJabberImpl ContactJabberImpl = iterator.next();
			// if (ContactJabberImpl.getSystemAccount() == null) {
				iterator.remove();
			// }
		}
		return with;
	}

	public List<ContactJabberImpl> getContacts() {
		synchronized (this.contacts) {
			return new ArrayList<>(this.contacts.values());
		}
	}

	public void initContactJabberImpl(final ContactJabberImpl contact) {
		if (contact == null) {
			return;
		}
//		contact.setAccount(accountId);
//		contact.setOption(ContactJabberImpl.Options.IN_ROSTER);
		Jid jid = null;
		try {
			jid = JidCreate.from(contact.getAddress());
		}
		catch (XmppStringprepException e) {
			e.printStackTrace();
		}

		synchronized (this.contacts) {
			contacts.put(jid, contact);
		}
	}

	public void setVersion(String version) {
		this.version = version;
	}

	public String getVersion() {
		return this.version;
	}

	public AccountID getAccount() {
		return this.accountId;
	}
}
