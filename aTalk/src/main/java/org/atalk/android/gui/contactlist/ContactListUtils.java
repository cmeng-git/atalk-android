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
import org.atalk.android.gui.AndroidGUIActivator;
import org.atalk.android.gui.dialogs.DialogActivity;

import timber.log.Timber;

/**
 * Class gathers utility methods for operations on contact list.
 */
public class ContactListUtils
{
    /**
     * Adds a new contact in separate <tt>Thread</tt>.
     *
     * @param protocolProvider parent protocol provider.
     * @param group contact group to which new contact will be added.
     * @param contactAddress new contact address.
     */
    public static void addContact(final ProtocolProviderService protocolProvider, final MetaContactGroup group,
            final String contactAddress)
    {
        new Thread()
        {
            @Override
            public void run()
            {
                try {
                    AndroidGUIActivator.getContactListService().createMetaContact(protocolProvider, group, contactAddress);
                } catch (MetaContactListException ex) {
                    Timber.e("Add Contact error: %s", ex.getMessage());
                    Context ctx = aTalkApp.getGlobalContext();
                    String title = ctx.getString(R.string.service_gui_ADD_CONTACT_ERROR_TITLE);

                    String msg;
                    int errorCode = ex.getErrorCode();
                    switch (errorCode) {
                        case MetaContactListException.CODE_CONTACT_ALREADY_EXISTS_ERROR:
                            msg = ctx.getString(R.string.service_gui_ADD_CONTACT_EXIST_ERROR, contactAddress);
                            break;
                        case MetaContactListException.CODE_NETWORK_ERROR:
                            msg = ctx.getString(R.string.service_gui_ADD_CONTACT_NETWORK_ERROR, contactAddress);
                            break;
                        case MetaContactListException.CODE_NOT_SUPPORTED_OPERATION:
                            msg = ctx.getString(R.string.service_gui_ADD_CONTACT_NOT_SUPPORTED, contactAddress);
                            break;
                        default:
                            msg = ctx.getString(R.string.service_gui_ADD_CONTACT_ERROR, contactAddress);
                            break;
                    }
                    DialogActivity.showDialog(ctx, title, msg);
                }
            }
        }.start();
    }
}
