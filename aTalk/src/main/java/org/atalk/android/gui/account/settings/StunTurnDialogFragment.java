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
import org.atalk.android.gui.util.ViewUtil;
import androidx.fragment.app.DialogFragment;

import static net.java.sip.communicator.service.protocol.jabber.JabberAccountID.DEFAULT_STUN_PORT;

/**
 * The dialog fragment that allows user to edit the STUN server descriptor.
 *
 * @author Pawel Domas
 * @author Eng Chong Meng
 * @author MilanKral
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

    private CheckBox useTurnCbox;
    private EditText ipAddress;
    private EditText ipPort;
    private EditText turnUser;
    private EditText turnPassword;
    private Spinner turnProtocolSpinner;

    public StunTurnDialogFragment()
    {
    }

    /**
     * Creates new instance of {@link StunTurnDialogFragment}
     *
     * @param parentAdapter the parent adapter
     * @param descriptor the descriptor to edit or <tt>null</tt> if new one shall be created
     */

    public static StunTurnDialogFragment newInstance(StunServerAdapter parentAdapter, StunServerDescriptor descriptor)
    {
        if (parentAdapter == null)
            throw new NullPointerException();

        StunTurnDialogFragment dialogFragmentST = new StunTurnDialogFragment();
        dialogFragmentST.parentAdapter = parentAdapter;
        dialogFragmentST.descriptor = descriptor;
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

        builder = builder.setView(contentView)
                .setPositiveButton(R.string.service_gui_SAVE, null)
                .setNeutralButton(R.string.service_gui_SERVERS_LIST_CANCEL, null);
        if (descriptor != null) {
            builder = builder.setNegativeButton(R.string.service_gui_SERVERS_LIST_REMOVE, null);
        }

        ipAddress = contentView.findViewById(R.id.ipAddress);
        ipPort = contentView.findViewById(R.id.serverPort);
        turnUser = contentView.findViewById(R.id.usernameField);
        turnPassword = contentView.findViewById(R.id.passwordField);

        turnProtocolSpinner = contentView.findViewById(R.id.TURNProtocol);
        ArrayAdapter<CharSequence> adapterType = ArrayAdapter.createFromResource(getActivity(),
                R.array.TURN_protocol, R.layout.simple_spinner_item);
        adapterType.setDropDownViewResource(R.layout.simple_spinner_dropdown_item);
        turnProtocolSpinner.setAdapter(adapterType);

        View turnSetting = contentView.findViewById(R.id.turnSetting);
        useTurnCbox = contentView.findViewById(R.id.useTurnCheckbox);
        useTurnCbox.setOnCheckedChangeListener((cButton, b) -> {
            turnSetting.setVisibility(b ? View.VISIBLE : View.GONE);
        });

        CheckBox showPassword = contentView.findViewById(R.id.show_password);
        showPassword.setOnCheckedChangeListener((buttonView, isChecked)
                -> ViewUtil.showPassword(turnPassword, isChecked));

        if (descriptor != null) {
            ipAddress.setText(descriptor.getAddress());
            ipPort.setText(String.valueOf(descriptor.getPort()));

            useTurnCbox.setChecked(descriptor.isTurnSupported());
            turnUser.setText(new String(descriptor.getUsername()));
            turnPassword.setText(new String(descriptor.getPassword()));

            final String protocolText = convertTURNProtocolTypeToText(descriptor.getProtocol());
            final String[] protocolArray = getResources().getStringArray(R.array.TURN_protocol);
            for (int i = 0; i < protocolArray.length; i++) {
                if (protocolText.equals(protocolArray[i])) {
                    turnProtocolSpinner.setSelection(i);
                }
            }
        }
        else
            ipPort.setText(DEFAULT_STUN_PORT);

        turnSetting.setVisibility(useTurnCbox.isChecked() ? View.VISIBLE : View.GONE);

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
     * Save the changes to the edited descriptor and notifies parent about the changes.
     * Returns <tt>true</tt> if all fields are correct.
     *
     * @return <tt>true</tt> if all field are correct and changes have been submitted to the parent adapter.
     */
    boolean saveChanges()
    {
        Dialog dialog = getDialog();
        boolean useTurn = ((CheckBox) dialog.findViewById(R.id.useTurnCheckbox)).isChecked();
        String ipAddress = ViewUtil.toString(dialog.findViewById(R.id.ipAddress));
        String portStr = ViewUtil.toString(dialog.findViewById(R.id.serverPort));

        String turnUser = ViewUtil.toString(dialog.findViewById(R.id.usernameField));
        String password = ViewUtil.toString(dialog.findViewById(R.id.passwordField));

        final Spinner protocolSpinner = dialog.findViewById(R.id.TURNProtocol);
        final String protocol = convertTURNProtocolTextToType((String) protocolSpinner.getSelectedItem());

        if ((ipAddress == null) || (portStr == null)) {
            aTalkApp.showToastMessage("The ip and port can not be empty");
            return false;
        }

        int port = Integer.parseInt(portStr);

        // Create descriptor if new entry
        if (descriptor == null) {
            descriptor = new StunServerDescriptor(ipAddress, port, useTurn, turnUser, password, protocol);
            parentAdapter.addServer(descriptor);
        }
        else {
            descriptor.setAddress(ipAddress);
            descriptor.setPort(port);
            descriptor.setTurnSupported(useTurn);
            descriptor.setUsername(turnUser);
            descriptor.setPassword(password);
            descriptor.setProtocol(protocol);
            parentAdapter.updateServer(descriptor);
        }
        return true;
    }

    private static String convertTURNProtocolTypeToText(final String type)
    {
        switch (type) {
            case StunServerDescriptor.PROTOCOL_UDP:
                return "UDP";
            case StunServerDescriptor.PROTOCOL_TCP:
                return "TCP";
            case StunServerDescriptor.PROTOCOL_DTLS:
                return "DTLS";
            case StunServerDescriptor.PROTOCOL_TLS:
                return "TLS";
            default:
                throw new IllegalArgumentException("unknown TURN protocol");
        }
    }

    private static String convertTURNProtocolTextToType(final String protocolText)
    {
        switch (protocolText) {
            case "UDP":
                return StunServerDescriptor.PROTOCOL_UDP;
            case "TCP":
                return StunServerDescriptor.PROTOCOL_TCP;
            case "DTLS":
                return StunServerDescriptor.PROTOCOL_DTLS;
            case "TLS":
                return StunServerDescriptor.PROTOCOL_TLS;
            default:
                throw new IllegalArgumentException("unknown TURN protocol");
        }
    }
}
