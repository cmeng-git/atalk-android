/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.atalk.android.gui.account.settings;

import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.os.Bundle;
import android.view.*;
import android.widget.*;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.DialogFragment;

import net.java.sip.communicator.service.protocol.SecurityAccountRegistration;

import org.atalk.android.R;
import org.atalk.android.gui.widgets.TouchInterceptor;

import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;

/**
 * The dialog that displays a list of security protocols in {@link SecurityActivity}.
 * It allows user to enable/disable each protocol and set their priority.
 */
public class SecurityProtocolsDialogFragment extends DialogFragment
{
    /**
     * The encryption protocols managed by this dialog.
     */
   // public static final String[] encryptionProtocols = {"ZRTP", "SDES"};

    public static final String ARG_ENCRYPTION = "arg_encryption";

    public static final String ARG_ENCRYPTION_STATUS = "arg_encryption_status";

    public static final String STATE_ENCRYPTION = "state_encryption";

    public static final String STATE_ENCRYPTION_STATUS = "state_encryption_status";

    /**
     * The list model for the protocols
     */
    private ProtocolsAdapter mProtocolsAdapter;

    /**
     * The listener that will be notified when this dialog is closed
     */
    private DialogClosedListener mListener;
    private AppCompatActivity mActivity;

    /**
     * Flag indicating if there have been any changes made
     */
    private boolean hasChanges = false;

    @Override
    public void onAttach(@NonNull Context context)
    {
        super.onAttach(context);
        mActivity = (AppCompatActivity) context;
        mListener = (DialogClosedListener) context;
    }

    @NonNull
    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState)
    {
        if (savedInstanceState == null) {
            mProtocolsAdapter = new ProtocolsAdapter((Map<String, Integer>) getArguments().get(ARG_ENCRYPTION),
                    (Map<String, Boolean>) getArguments().get(ARG_ENCRYPTION_STATUS));
        }
        else {
            mProtocolsAdapter = new ProtocolsAdapter(savedInstanceState.getStringArray(STATE_ENCRYPTION),
                    (Map<String, Boolean>) savedInstanceState.get(STATE_ENCRYPTION_STATUS));
        }
        // Get the layout inflater
        LayoutInflater inflater = requireActivity().getLayoutInflater();
        View contentView = inflater.inflate(R.layout.sec_protocols_dialog, null);

        // Builds the dialog
        AlertDialog.Builder builder = new AlertDialog.Builder(mActivity);
        builder = builder.setTitle(R.string.service_gui_SEC_PROTOCOLS_TITLE);
        builder.setView(contentView).setPositiveButton(R.string.service_gui_SEC_PROTOCOLS_OK, (dialog, i) -> {
            hasChanges = true;
            dismiss();
        }).setNegativeButton(R.string.service_gui_SEC_PROTOCOLS_CANCEL, (dialog, i) -> {
            hasChanges = false;
            dismiss();
        });

        TouchInterceptor lv = contentView.findViewById(android.R.id.list);
        lv.setAdapter(mProtocolsAdapter);
        lv.setDropListener(mProtocolsAdapter);

        return builder.create();
    }

    @Override
    public void onSaveInstanceState(@NonNull Bundle outState)
    {
        super.onSaveInstanceState(outState);

        outState.putSerializable(STATE_ENCRYPTION, mProtocolsAdapter.mEncryption);
        outState.putSerializable(STATE_ENCRYPTION_STATUS, (Serializable) mProtocolsAdapter.mEncryptionStatus);
    }

    /**
     * Commits the changes into given {@link SecurityAccountRegistration}
     *
     * @param securityReg the registration object that will hold new security preferences
     */
    public void commit(SecurityAccountRegistration securityReg)
    {
        Map<String, Integer> protocol = new HashMap<>();
        for (int i = 0; i < mProtocolsAdapter.mEncryption.length; i++) {
            protocol.put(mProtocolsAdapter.mEncryption[i], i);
        }
        securityReg.setEncryptionProtocol(protocol);
        securityReg.setEncryptionProtocolStatus(mProtocolsAdapter.mEncryptionStatus);
    }

    /**
     * The interface that will be notified when this dialog is closed
     */
    public interface DialogClosedListener
    {
        void onDialogClosed(SecurityProtocolsDialogFragment dialog);
    }

    @Override
    public void onDismiss(@NonNull DialogInterface dialog)
    {
        super.onDismiss(dialog);
        if (mListener != null) {
            mListener.onDialogClosed(this);
        }
    }

    /**
     * Flag indicating whether any changes have been done to security config
     *
     * @return <code>true</code> if any changes have been made
     */
    public boolean hasChanges()
    {
        return hasChanges;
    }

    /**
     * List model for security protocols and their priorities
     */
    class ProtocolsAdapter extends BaseAdapter implements TouchInterceptor.DropListener
    {
        /**
         * The array of encryption protocol names and their on/off status in mEncryptionStatus
         */
        protected String[] mEncryption;
        protected Map<String, Boolean> mEncryptionStatus;

        /**
         * Creates a new instance of {@link ProtocolsAdapter}
         *
         * @param encryption reference copy
         * @param encryptionStatus reference copy
         */
        ProtocolsAdapter(final Map<String, Integer> encryption, final Map<String, Boolean> encryptionStatus)
        {
            mEncryption = (String[]) SecurityAccountRegistration.loadEncryptionProtocol(encryption, encryptionStatus)[0];
            // Fill missing entries
            for (String enc : encryption.keySet()) {
                if (!encryptionStatus.containsKey(enc))
                    encryptionStatus.put(enc, false);
            }
            this.mEncryptionStatus = encryptionStatus;
        }

        /**
         * Creates new instance of {@link ProtocolsAdapter}
         *
         * @param encryption reference copy
         * @param encryptionStatus reference copy
         */
        ProtocolsAdapter(String[] encryption, Map<String, Boolean> encryptionStatus)
        {
            this.mEncryption = encryption;
            this.mEncryptionStatus = encryptionStatus;
        }

        public int getCount()
        {
            return mEncryption.length;
        }

        public Object getItem(int i)
        {
            return mEncryption[i];
        }

        public long getItemId(int i)
        {
            return i;
        }

        public View getView(int i, View view, ViewGroup viewGroup)
        {
            final String encryption = (String) getItem(i);

            LayoutInflater li = requireActivity().getLayoutInflater();
            View v = li.inflate(R.layout.encoding_item, viewGroup, false);

            TextView tv = v.findViewById(android.R.id.text1);
            tv.setText(encryption);

            CheckBox cb = v.findViewById(android.R.id.checkbox);
            cb.setChecked(mEncryptionStatus.containsKey(encryption) && mEncryptionStatus.get(encryption));
            cb.setOnCheckedChangeListener((cb1, state) -> {
                mEncryptionStatus.put(encryption, state);
                hasChanges = true;
            });
            return v;
        }

        /**
         * Implements {@link TouchInterceptor.DropListener}. Method swaps protocols priorities.
         *
         * @param from source item index
         * @param to destination item index
         */
        public void drop(int from, int to)
        {
            hasChanges = true;
            String swap = mEncryption[to];
            mEncryption[to] = mEncryption[from];
            mEncryption[from] = swap;

            mActivity.runOnUiThread(this::notifyDataSetChanged);
        }
    }
}
