/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.atalk.android.gui.account.settings;

import android.app.*;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.*;

import net.java.sip.communicator.service.protocol.StunServerDescriptor;

import org.atalk.android.R;
import org.atalk.android.aTalkApp;

import static net.java.sip.communicator.service.protocol.jabber.JabberAccountID.DEFAULT_STUN_PORT;

/**
 * The dialog fragment that allows user to edit the STUN server descriptor.
 *
 * @author Pawel Domas
 * @author Eng Chong Meng
 */
public class StunTurnDialogFragment extends DialogFragment
{
    /**
     * The edited descriptor
     */
    private StunServerDescriptor descriptor;

    /**
     * Parent adapter that will be notified about any changes to the descriptor
     */
    private StunServerAdapter parentAdapter;

    public StunTurnDialogFragment()
    {
    }

    /**
     * Creates new instance of {@link StunTurnDialogFragment}
     *
     * @param parentAdapter the parent adapter
     * @param descriptior the descriptor to edit or <tt>null</tt> if new one shall be created
     */

    public static StunTurnDialogFragment newInstance(StunServerAdapter parentAdapter, StunServerDescriptor descriptior)
    {
        if (parentAdapter == null)
            throw new NullPointerException();

        StunTurnDialogFragment dialogFragmentST = new StunTurnDialogFragment();
        dialogFragmentST.parentAdapter = parentAdapter;
        dialogFragmentST.descriptor = descriptior;
        return dialogFragmentST;
    }

    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState)
    {
        AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
        builder = builder.setTitle(R.string.service_gui_STUN_TURN_SERVER);

        // Get the layout inflater
        LayoutInflater inflater = getActivity().getLayoutInflater();
        View contentView = inflater.inflate(R.layout.stun_turn_dialog, null);

        builder = builder.setView(contentView).setPositiveButton(R.string.service_gui_SAVE, null)
                .setNeutralButton(R.string.service_gui_SERVERS_LIST_CANCEL, null);
        if (descriptor != null) {
            builder = builder.setNegativeButton(R.string.service_gui_SERVERS_LIST_REMOVE, null);
        }

        TextView portView = contentView.findViewById(R.id.serverPort);
        final TextView turnUserView = contentView.findViewById(R.id.usernameField);
        final TextView passwordView = contentView.findViewById(R.id.passwordField);

        CompoundButton useTurnButton = contentView.findViewById(R.id.useTurnCheckbox);
        useTurnButton.setOnCheckedChangeListener((cButton, b) -> {
            turnUserView.setEnabled(b);
            passwordView.setEnabled(b);
        });

        if (descriptor != null) {
            TextView ipAdrTextView = contentView.findViewById(R.id.ipAddress);
            ipAdrTextView.setText(descriptor.getAddress());

            portView.setText(String.valueOf(descriptor.getPort()));
            turnUserView.setText(new String(descriptor.getUsername()));
            passwordView.setText(new String(descriptor.getPassword()));
            useTurnButton.setChecked(descriptor.isTurnSupported());
        }
        else
            portView.setText(DEFAULT_STUN_PORT);

        boolean isTurnSupported = useTurnButton.isChecked();
        turnUserView.setEnabled(isTurnSupported);
        passwordView.setEnabled(isTurnSupported);
        useTurnButton.setChecked(isTurnSupported);

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
                    parentAdapter.removeServer(descriptor);
                    dismiss();
                });
            }
        });
        return dialog;
    }

    /**
     * Save the changes to the edited descriptor and notifies parent about the changes. Returns <tt>true</tt> if all
     * fields are correct.
     *
     * @return <tt>true</tt> if all field are correct and changes have been submitted to the parent adapter.
     */
    boolean saveChanges()
    {
        Dialog dialog = getDialog();
        boolean useTurn = ((CompoundButton) dialog.findViewById(R.id.useTurnCheckbox)).isChecked();
        String ipAddress = ((TextView) dialog.findViewById(R.id.ipAddress)).getText().toString();
        String turnUser = ((TextView) dialog.findViewById(R.id.usernameField)).getText().toString().trim();
        String password = ((TextView) dialog.findViewById(R.id.passwordField)).getText().toString().trim();

        String portStr = ((TextView) dialog.findViewById(R.id.serverPort)).getText().toString();
        if (portStr.isEmpty()) {
            aTalkApp.showToastMessage("The port can not be empty");
            return false;
        }
        int port = Integer.parseInt(portStr);

        // Create descriptor if new entry
        if (descriptor == null) {
            descriptor = new StunServerDescriptor(ipAddress, port, useTurn, turnUser, password);
            parentAdapter.addServer(descriptor);
        }
        else {
            descriptor.setAddress(ipAddress);
            descriptor.setPort(port);
            descriptor.setTurnSupported(useTurn);
            descriptor.setUsername(turnUser);
            descriptor.setPassword(password);
            parentAdapter.updateServer(descriptor);
        }
        return true;
    }
}
