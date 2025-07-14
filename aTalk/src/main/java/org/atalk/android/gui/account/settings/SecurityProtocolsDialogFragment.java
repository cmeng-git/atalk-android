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
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.CheckBox;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.os.BundleCompat;

import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;

import net.java.sip.communicator.service.protocol.SecurityAccountRegistration;

import org.atalk.android.R;
import org.atalk.android.gui.dialogs.BaseDialogFragment;
import org.atalk.android.gui.widgets.TouchInterceptor;

/**
 * The dialog that displays a list of security protocols in {@link SecurityActivity}.
 * It allows user to enable/disable each protocol and set their priority.
 */
public class SecurityProtocolsDialogFragment extends BaseDialogFragment {
    /**
     * The encryption protocols managed by this dialog.
     */
    public static final String ENCRYPTION_PRIORITY = "encryption_priority";

    public static final String ENCRYPTION_STATE = "encryption_state";

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
    public void onAttach(@NonNull Context context) {
        super.onAttach(context);
        mActivity = (AppCompatActivity) mFragmentActivity;
        mListener = (DialogClosedListener) context;
    }

    @NonNull
    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        Bundle bundle = (savedInstanceState == null) ? getArguments() : savedInstanceState;
        if (bundle != null) {
            mProtocolsAdapter= new ProtocolsAdapter(BundleCompat.getSerializable(bundle, ENCRYPTION_PRIORITY, HashMap.class),
                    BundleCompat.getSerializable(bundle, ENCRYPTION_STATE, HashMap.class));
        }

        // Get the layout inflater
        LayoutInflater inflater = requireActivity().getLayoutInflater();
        View contentView = inflater.inflate(R.layout.sec_protocols_dialog, null);

        // Builds the dialog
        AlertDialog.Builder builder = new AlertDialog.Builder(mActivity);
        builder = builder.setTitle(R.string.sec_protocols_title);
        builder.setView(contentView).setPositiveButton(R.string.save, (dialog, i) -> {
            hasChanges = true;
            dismiss();
        }).setNegativeButton(R.string.discard, (dialog, i) -> {
            hasChanges = false;
            dismiss();
        });

        TouchInterceptor lv = contentView.findViewById(android.R.id.list);
        lv.setAdapter(mProtocolsAdapter);
        lv.setDropListener(mProtocolsAdapter);

        return builder.create();
    }

    @Override
    public void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);

        outState.putSerializable(ENCRYPTION_PRIORITY, mProtocolsAdapter.mEncryption);
        outState.putSerializable(ENCRYPTION_STATE, (Serializable) mProtocolsAdapter.mEncryptionState);
    }

    /**
     * Commits the changes into given {@link SecurityAccountRegistration}
     *
     * @param securityReg the registration object that will hold new security preferences
     */
    public void commit(SecurityAccountRegistration securityReg) {
        Map<String, Integer> protocol = new HashMap<>();
        for (int i = 0; i < mProtocolsAdapter.mEncryption.length; i++) {
            protocol.put(mProtocolsAdapter.mEncryption[i], i);
        }
        securityReg.setEncryptionProtocol(protocol);
        securityReg.setEncryptionProtocolStatus(mProtocolsAdapter.mEncryptionState);
    }

    /**
     * The interface that will be notified when this dialog is closed
     */
    public interface DialogClosedListener {
        void onDialogClosed(SecurityProtocolsDialogFragment dialog);
    }

    @Override
    public void onDismiss(@NonNull DialogInterface dialog) {
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
    public boolean hasChanges() {
        return hasChanges;
    }

    /**
     * List model for security protocols and their priorities
     */
    class ProtocolsAdapter extends BaseAdapter implements TouchInterceptor.DropListener {
        /**
         * The array of encryption protocol names and their on/off status in mEncryptionStatus
         */
        protected String[] mEncryption;
        protected Map<String, Boolean> mEncryptionState;

        /**
         * Creates a new instance of {@link ProtocolsAdapter}
         *
         * @param encryption reference copy
         * @param encryptionStatus reference copy
         */
        ProtocolsAdapter(final Map<String, Integer> encryption, final Map<String, Boolean> encryptionStatus) {
            mEncryption = (String[]) SecurityAccountRegistration.loadEncryptionProtocol(encryption, encryptionStatus)[0];
            // Fill missing entries
            for (String enc : encryption.keySet()) {
                if (!encryptionStatus.containsKey(enc))
                    encryptionStatus.put(enc, false);
            }
            mEncryptionState = encryptionStatus;
        }

        /**
         * Creates new instance of {@link ProtocolsAdapter}
         *
         * @param encryption reference copy
         * @param encryptionStatus reference copy
         */
        ProtocolsAdapter(String[] encryption, Map<String, Boolean> encryptionStatus) {
            mEncryption = encryption;
            mEncryptionState = encryptionStatus;
        }

        public int getCount() {
            return mEncryption.length;
        }

        public Object getItem(int i) {
            return mEncryption[i];
        }

        public long getItemId(int i) {
            return i;
        }

        public View getView(int i, View view, ViewGroup viewGroup) {
            final String encryption = (String) getItem(i);

            LayoutInflater li = requireActivity().getLayoutInflater();
            View v = li.inflate(R.layout.encoding_item, viewGroup, false);

            TextView tv = v.findViewById(android.R.id.text1);
            tv.setText(encryption);

            CheckBox cb = v.findViewById(android.R.id.checkbox);
            cb.setChecked(mEncryptionState.containsKey(encryption) && Boolean.TRUE.equals(mEncryptionState.get(encryption)));
            cb.setOnCheckedChangeListener((cb1, state) -> {
                mEncryptionState.put(encryption, state);
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
        public void drop(int from, int to) {
            hasChanges = true;
            String swap = mEncryption[to];
            mEncryption[to] = mEncryption[from];
            mEncryption[from] = swap;

            runOnUiThread(this::notifyDataSetChanged);
        }
    }
}
