package org.atalk.entities;

import org.atalk.android.gui.account.Account;
import org.jxmpp.jid.Jid;

public interface Blockable
{
	boolean isBlocked();
	boolean isDomainBlocked();
	Jid getBlockedJid();
	Jid getJid();
	Account getAccount();
}
