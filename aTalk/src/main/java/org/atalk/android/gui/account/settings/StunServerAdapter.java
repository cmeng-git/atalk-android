/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.atalk.android.gui.account.settings;

import android.app.Activity;
import android.view.*;
import android.widget.TextView;

import net.java.sip.communicator.service.protocol.StunServerDescriptor;
import net.java.sip.communicator.service.protocol.jabber.JabberAccountRegistration;

import org.atalk.android.R;
import org.atalk.android.aTalkApp;

import androidx.fragment.app.DialogFragment;

/**
 * List model for STUN servers. Is used to edit STUN servers preferences of Jabber account. It's also responsible for
 * creating list row <tt>View</tt>s and implements {@link ServerItemAdapter#createItemEditDialogFragment(int)} to
 * provide item edit dialog.
 *
 * @author Pawel Domas
 * @author Eng Chong Meng
 * See {@link ServerListActivity}
 */
public class StunServerAdapter extends ServerItemAdapter
{
    /**
     * The {@link JabberAccountRegistration} that contains the original list
     */
    protected final JabberAccountRegistration registration;

    /**
     * Creates new instance of {@link StunServerAdapter}
     *
     * @param parent the parent {@link android.app.Activity} used as a context
     * @param registration the registration object that holds the STUN server list
     */
    public StunServerAdapter(Activity parent, JabberAccountRegistration registration)
    {
        super(parent);
        this.registration = registration;
    }

    public int getCount()
    {
        return registration.getAdditionalStunServers().size();
    }

    public Object getItem(int i)
    {
        return registration.getAdditionalStunServers().get(i);
    }

    public View getView(int i, View view, ViewGroup viewGroup)
    {
        LayoutInflater li = parent.getLayoutInflater();
        View rowView = li.inflate(android.R.layout.simple_list_item_1, viewGroup, false);
        TextView tv = rowView.findViewById(android.R.id.text1);

        StunServerDescriptor server = (StunServerDescriptor) getItem(i);
        String descriptor = aTalkApp.getResString(R.string.service_gui_SERVERS_STUN_DESCRIPTOR,
                server.getAddress(), server.getPort(), (server.isTurnSupported() ? "(+TURN)" : ""));
        tv.setText(descriptor);

        return rowView;
    }

    /**
     * Removes the server from the list.
     *
     * @param descriptor the server descriptor to be removed
     */
    void removeServer(StunServerDescriptor descriptor)
    {
        registration.getAdditionalStunServers().remove(descriptor);
        refresh();
    }

    /**
     * Add new STUN server descriptor to the list
     *
     * @param descriptor the server descriptor
     */
    void addServer(StunServerDescriptor descriptor)
    {
        registration.addStunServer(descriptor);
        refresh();
    }

    /**
     * Updates given server description
     *
     * @param descriptor the server to be updated
     */
    void updateServer(StunServerDescriptor descriptor)
    {
        refresh();
    }

    DialogFragment createItemEditDialogFragment(int position)
    {
        DialogFragment dialogFragment;
        if (position < 0)
            dialogFragment = StunTurnDialogFragment.newInstance(this, null);
        else
            dialogFragment = StunTurnDialogFragment.newInstance(this, (StunServerDescriptor) getItem(position));
        return dialogFragment;
    }
}
