/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.atalk.android.gui.account.settings;

import android.app.Activity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.fragment.app.DialogFragment;

import net.java.sip.communicator.service.protocol.JingleNodeDescriptor;
import net.java.sip.communicator.service.protocol.jabber.JabberAccountRegistration;

/**
 * Implements list model for Jingle Nodes list of {@link JabberAccountRegistration}.
 *
 * @author Pawel Domas
 * @author Eng Chong Meng
 * @see ServerItemAdapter
 */
public class JingleNodeAdapter extends ServerItemAdapter {
    /**
     * The {@link JabberAccountRegistration} object that contains Jingle Nodes
     */
    private JabberAccountRegistration registration;

    /**
     * Creates new instance of {@link JingleNodeAdapter}
     *
     * @param parent the parent {@link android.app.Activity} used a a context
     * @param registration the registration object that contains Jingle Nodes
     */
    public JingleNodeAdapter(Activity parent, JabberAccountRegistration registration) {
        super(parent);
        this.registration = registration;
    }

    public int getCount() {
        return registration.getAdditionalJingleNodes().size();
    }

    public Object getItem(int i) {
        return registration.getAdditionalJingleNodes().get(i);
    }

    /**
     * Creates the dialog fragment that will allow user to edit Jingle Node
     *
     * @param position the position of item to edit
     *
     * @return the Jingle Node edit dialog
     */
    DialogFragment createItemEditDialogFragment(int position) {
        DialogFragment dialogFragment;

        if (position < 0)
            dialogFragment = JingleNodeDialogFragment.newInstance(this, null);
        else
            dialogFragment = JingleNodeDialogFragment.newInstance(this, (JingleNodeDescriptor) getItem(position));

        return dialogFragment;
    }

    public View getView(int i, View view, ViewGroup viewGroup) {
        LayoutInflater li = parent.getLayoutInflater();
        // View rowView = li.inflate(R.layout.server_list_row, viewGroup, false);
        View rowView = li.inflate(android.R.layout.simple_list_item_1, viewGroup, false);
        TextView tv = rowView.findViewById(android.R.id.text1);

        JingleNodeDescriptor node = (JingleNodeDescriptor) getItem(i);
        tv.setText(node.getJID() + (node.isRelaySupported() ? " (+Relay support)" : ""));

        return rowView;
    }

    /**
     * Removes the Jingle Node from the list
     *
     * @param descriptor Jingle Node that shall be removed
     */
    void removeJingleNode(JingleNodeDescriptor descriptor) {
        registration.getAdditionalJingleNodes().remove(descriptor);
        refresh();
    }

    /**
     * Adds new Jingle node to the list
     *
     * @param descriptor the {@link JingleNodeDescriptor} that will be included in this adapter
     */
    void addJingleNode(JingleNodeDescriptor descriptor) {
        registration.addJingleNodes(descriptor);
        refresh();
    }

    /**
     * Updates given Jingle Node
     *
     * @param descriptor the JingleNode that will be updated
     */
    void updateJingleNode(JingleNodeDescriptor descriptor) {
        refresh();
    }
}
