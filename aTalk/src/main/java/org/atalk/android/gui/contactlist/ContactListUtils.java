/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.atalk.android.gui.contactlist;

import android.content.Context;

import net.java.sip.communicator.service.contactlist.MetaContactGroup;
import net.java.sip.communicator.service.contactlist.MetaContactListException;
import net.java.sip.communicator.service.protocol.ProtocolProviderService;

import org.atalk.android.R;
import org.atalk.android.aTalkApp;
import org.atalk.android.gui.AppGUIActivator;
import org.atalk.android.gui.dialogs.DialogActivity;

import timber.log.Timber;

/**
 * Class gathers utility methods for operations on contact list.
 */
public class ContactListUtils {
    /**
     * Adds a new contact in separate <code>Thread</code>.
     *
     * @param protocolProvider parent protocol provider.
     * @param group contact group to which new contact will be added.
     * @param contactAddress new contact address.
     */
    public static void addContact(final ProtocolProviderService protocolProvider, final MetaContactGroup group,
            final String contactAddress) {
        new Thread() {
            @Override
            public void run() {
                try {
                    AppGUIActivator.getContactListService().createMetaContact(protocolProvider, group, contactAddress);
                } catch (MetaContactListException ex) {
                    Timber.e("Add Contact error: %s", ex.getMessage());
                    Context ctx = aTalkApp.getInstance();
                    String title = ctx.getString(R.string.add_contact_error);

                    String msg;
                    int errorCode = ex.getErrorCode();
                    switch (errorCode) {
                        case MetaContactListException.CODE_CONTACT_ALREADY_EXISTS_ERROR:
                            msg = ctx.getString(R.string.add_contact_error_exist, contactAddress);
                            break;
                        case MetaContactListException.CODE_NETWORK_ERROR:
                            msg = ctx.getString(R.string.add_contact_error_network, contactAddress);
                            break;
                        case MetaContactListException.CODE_NOT_SUPPORTED_OPERATION:
                            msg = ctx.getString(R.string.add_contact_error_not_supported, contactAddress);
                            break;
                        default:
                            msg = ctx.getString(R.string.add_contact_failed, contactAddress);
                            break;
                    }
                    DialogActivity.showDialog(ctx, title, msg);
                }
            }
        }.start();
    }
}
