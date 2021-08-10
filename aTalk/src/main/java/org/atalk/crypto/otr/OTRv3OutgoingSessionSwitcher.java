/*
 * aTalk, android VoIP and Instant Messaging client
 * Copyright 2014 Eng Chong Meng
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.atalk.crypto.otr;

import android.content.Context;
import android.os.Bundle;
import android.view.*;
import android.widget.*;

import net.java.otr4j.session.Session;
import net.java.sip.communicator.plugin.otr.OtrActivator;
import net.java.sip.communicator.plugin.otr.OtrContactManager.OtrContact;

import org.atalk.android.R;
import org.atalk.service.osgi.OSGiDialogFragment;

import java.security.PublicKey;
import java.util.*;

import timber.log.Timber;

/**
 * A special {@link } that controls the switching of OTRv3 outgoing
 * sessions in case the remote party is logged in multiple times.
 *
 * @author Eng Chong Meng
 */
public class OTRv3OutgoingSessionSwitcher extends OSGiDialogFragment
        implements AdapterView.OnItemClickListener, View.OnClickListener
{
    /**
     * The <tt>Contact</tt> that belongs to OTR session handled by this instance.
     */
    private static OtrContact mOtrContact;

    private final ArrayList<String> otrSessionLabels = new ArrayList<>();

    // session label => session
    private HashMap<String, Session> outgoingSessions = new HashMap<>();

    // session label => icon
    private HashMap<String, Integer> sessionIcons = new HashMap<>();

    private Session mSelectedSession = null;

    public static OTRv3OutgoingSessionSwitcher newInstance(OtrContact contact)
    {
        mOtrContact = contact;
        Bundle args = new Bundle();
        OTRv3OutgoingSessionSwitcher dialog = new OTRv3OutgoingSessionSwitcher();
        dialog.setArguments(args);
        return dialog;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState)
    {
        getDialog().setTitle("OTR Sessions");
        View contentView = inflater.inflate(R.layout.otr_session_list, container, false);

        final Button buttonOk = contentView.findViewById(R.id.btn_OK);
        buttonOk.setOnClickListener(this);

        final Button buttonCancel = contentView.findViewById(R.id.btn_Cancel);
        buttonCancel.setOnClickListener(this);

        ListView listview = contentView.findViewById(R.id.otrSessionListView);
        final ArrayList<String> list = buildSessionList(mOtrContact);
        final SessionArrayAdapter adapter = new SessionArrayAdapter(getContext(), list);
        listview.setAdapter(adapter);

        listview.setOnItemClickListener(this);
        setCancelable(false);
        return contentView;

    }

    private class SessionArrayAdapter extends ArrayAdapter<String>
    {
        private final Context context;
        private final ArrayList<String> values;

        public SessionArrayAdapter(Context context, ArrayList<String> values)
        {
            super(context, -1, values);
            this.context = context;
            this.values = values;
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent)
        {
            LayoutInflater inflater = (LayoutInflater) context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
            if (convertView == null) {
                convertView = inflater.inflate(R.layout.otr_session_list_row, parent, false);
            }
            TextView textView = convertView.findViewById(R.id.otr_session);
            ImageView imageView = convertView.findViewById(R.id.otr_icon);

            String label = values.get(position);
            textView.setText(label);
            Integer icon = sessionIcons.get(label);
            if (icon != null)
                imageView.setImageResource(icon);

            return convertView;
        }

        @Override
        public boolean hasStableIds()
        {
            return true;
        }
    }

    /**
     * Builds the view list used for switching between outgoing OTRv3 Sessions in
     * case the remote party is logged in multiple locations
     *
     * @param otrContact the contact which is logged in multiple locations
     */
    private ArrayList<String> buildSessionList(OtrContact otrContact)
    {
        String label;
        List<Session> multipleInstances = OtrActivator.scOtrEngine.getSessionInstances(otrContact);

        int index = 0;
        for (Session session : multipleInstances) {
            index++;

            Integer imageIcon = null;
            switch (session.getSessionStatus(session.getReceiverInstanceTag())) {
                case ENCRYPTED:
                    PublicKey pubKey = session.getRemotePublicKey(session.getReceiverInstanceTag());
                    String fingerprint = OtrActivator.scOtrKeyManager.getFingerprintFromPublicKey(pubKey);
                    imageIcon = OtrActivator.scOtrKeyManager.isVerified(otrContact.contact, fingerprint)
                            ? R.drawable.crypto_otr_verified_grey : R.drawable.crypto_otr_unverified_grey;
                    break;
                case FINISHED:
                    imageIcon = R.drawable.crypto_otr_finished_grey;
                    break;
                case PLAINTEXT:
                    imageIcon = R.drawable.crypto_otr_unsecure_grey;
                    break;
                default:
                    Timber.d("Failed to load padlock image");
            }

            label = "Session " + index;
            otrSessionLabels.add(label);
            outgoingSessions.put(label, session);
            sessionIcons.put(label, imageIcon);
        }
        return otrSessionLabels;
    }

    @Override
    public void onItemClick(AdapterView<?> parent, View view, int position, long id)
    {
        if (view.isSelected()) {
            view.setBackgroundResource(R.drawable.color_blue_gradient);
        }
        else {
            view.setBackgroundResource(R.drawable.list_selector_state);
        }

        if (position < otrSessionLabels.size()) {
            mSelectedSession = outgoingSessions.get(otrSessionLabels.get(position));
        }
    }

    /**
     * Method fired when the ok button is clicked.
     *
     * @param v ok button's <tt>View</tt>.
     */
    @Override
    public void onClick(View v)
    {
        if (v.getId() == R.id.btn_OK) {
            if (mSelectedSession != null)
                OtrActivator.scOtrEngine.setOutgoingSession(mOtrContact, mSelectedSession.getReceiverInstanceTag());
        }
        dismiss();
    }
}
