/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.atalk.android.gui.account.settings;

import android.app.AlertDialog;
import android.app.Dialog;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.TextView;
import android.widget.Toast;

import net.java.sip.communicator.service.protocol.JingleNodeDescriptor;

import org.atalk.android.R;
import org.atalk.android.gui.util.ViewUtil;
import org.jxmpp.jid.Jid;
import org.jxmpp.jid.impl.JidCreate;
import org.jxmpp.stringprep.XmppStringprepException;

import androidx.fragment.app.DialogFragment;

/**
 * The Jingle Node edit dialog. It used to edit or create new {@link JingleNodeDescriptor}.
 * It serves as a "create new" dialog when <tt>null</tt> is passed as a descriptor argument.
 *
 * @author Pawel Domas
 * @author Eng Chong Meng
 */
public class JingleNodeDialogFragment extends DialogFragment
{
    /**
     * Edited Jingle Node descriptor
     */
    private JingleNodeDescriptor descriptor;

    /**
     * Parent {@link JingleNodeAdapter} that will be notified about any change to the Jingle Node
     */
    private JingleNodeAdapter listener;

    public JingleNodeDialogFragment()
    {
    }

    /**
     * Creates new instance of {@link JingleNodeDialogFragment}
     *
     * @param listener parent {@link JingleNodeAdapter}
     * @param descriptor the {@link JingleNodeDescriptor} to edit or <tt>null</tt> if a new node shall be created
     */
    public static JingleNodeDialogFragment newInstance(JingleNodeAdapter listener, JingleNodeDescriptor descriptor)
    {
        if (listener == null)
            throw new NullPointerException();

        JingleNodeDialogFragment fragmentJnd = new JingleNodeDialogFragment();
        fragmentJnd.listener = listener;
        fragmentJnd.descriptor = descriptor;
        return fragmentJnd;
    }

    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState)
    {
        // Builds the dialog
        AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
        builder = builder.setTitle(R.string.service_gui_SEC_PROTOCOLS_TITLE);

        // Get the layout inflater
        LayoutInflater inflater = getActivity().getLayoutInflater();
        View contentView = inflater.inflate(R.layout.jingle_node_dialog, null);

        builder = builder.setView(contentView)
                .setPositiveButton(R.string.service_gui_SAVE, null)
                .setNeutralButton(R.string.service_gui_SERVERS_LIST_CANCEL, null);
        if (descriptor != null) {
            // Add remove button if it''s not "create new" dialog
            builder = builder.setNegativeButton(R.string.service_gui_SERVERS_LIST_REMOVE, null);

            TextView jidAdrTextView = contentView.findViewById(R.id.jidAddress);
            jidAdrTextView.setText(descriptor.getJID());

            CompoundButton useRelayCb = (CompoundButton) contentView.findViewById(R.id.relaySupportCheckbox);
            useRelayCb.setChecked(descriptor.isRelaySupported());
        }

        final AlertDialog dialog = builder.create();
        dialog.setOnShowListener(dialogInterface -> {
            Button pos = dialog.getButton(AlertDialog.BUTTON_POSITIVE);
            pos.setOnClickListener(view -> {
                if (saveChanges())
                    dismiss();
            });
            Button neg = dialog.getButton(AlertDialog.BUTTON_NEGATIVE);
            if (neg != null) {
                neg.setOnClickListener(view -> {
                    listener.removeJingleNode(descriptor);
                    dismiss();
                });
            }
        });
        return dialog;
    }

    /**
     * Saves the changes if all data is correct
     *
     * @return <tt>true</tt> if all data is correct and changes have been stored in descriptor
     */
    boolean saveChanges()
    {
        Dialog dialog = getDialog();
        boolean relaySupport = ((CompoundButton) dialog.findViewById(R.id.relaySupportCheckbox)).isChecked();
        String jingleAddress = ViewUtil.toString(dialog.findViewById(R.id.jidAddress));

        if (jingleAddress == null) {
            Toast.makeText(getActivity(), "The Jid address can not be empty", Toast.LENGTH_LONG).show();
            return false;
        }

        Jid jidAddress = null;
        try {
            jidAddress = JidCreate.from(jingleAddress);
        } catch (XmppStringprepException | IllegalArgumentException e) {
            e.printStackTrace();
        }

        if (descriptor == null) {
            // Create new descriptor
            descriptor = new JingleNodeDescriptor(jidAddress, relaySupport);
            listener.addJingleNode(descriptor);
        }
        else {
            descriptor.setAddress(jidAddress);
            descriptor.setRelay(relaySupport);
            listener.updateJingleNode(descriptor);
        }
        return true;
    }
}
