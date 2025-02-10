/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.atalk.android.gui.account.settings;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.ListView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.DialogFragment;
import androidx.fragment.app.FragmentTransaction;
import androidx.fragment.app.ListFragment;

import net.java.sip.communicator.service.protocol.jabber.JabberAccountRegistration;

import org.atalk.android.BaseActivity;
import org.atalk.android.R;

/**
 * The activity allows user to edit STUN or Jingle Nodes list of the Jabber account.
 *
 * @author Pawel Domas
 * @author Eng Chong Meng
 */
public class ServerListActivity extends BaseActivity {
    /**
     * Request code when launched for STUN servers list edit
     */
    public static int RCODE_STUN_TURN = 1;

    /**
     * Request code used when launched for Jingle Nodes edit
     */
    public static int RCODE_JINGLE_NODES = 2;

    /**
     * Request code intent's extra key
     */
    public static String REQUEST_CODE_KEY = "requestCode";

    /**
     * Jabber account registration intent's extra key
     */
    public static String JABBER_REGISTRATION_KEY = "JabberReg";

    /**
     * The registration object storing edited properties
     */
    private JabberAccountRegistration registration;

    /**
     * The list model for currently edited items
     */
    private ServerItemAdapter mAdapter;

    @Override
    protected void onStart() {
        super.onStart();
        Intent intent = getIntent();
        this.registration = (JabberAccountRegistration) intent.getSerializableExtra(JABBER_REGISTRATION_KEY);
        int listType = intent.getIntExtra(REQUEST_CODE_KEY, -1);
        if (listType == RCODE_STUN_TURN) {
            mAdapter = new StunServerAdapter(this, registration);
            setMainTitle(R.string.stun_turn_server);
        }
        else if (listType == RCODE_JINGLE_NODES) {
            mAdapter = new JingleNodeAdapter(this, registration);
            setMainTitle(R.string.jbr_jingle_nodes);
        }
        else {
            throw new IllegalArgumentException();
        }

        ListFragment listFragment = new ServerListFragment();
        listFragment.setListAdapter(mAdapter);
        // Display the fragment as the main content.
        getSupportFragmentManager().beginTransaction()
                .replace(android.R.id.content, listFragment)
                .commit();

        findViewById(android.R.id.content).setOnClickListener(view -> showServerEditDialog(-1));
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        super.onCreateOptionsMenu(menu);

        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.server_list_menu, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == R.id.addItem) {
            showServerEditDialog(-1);
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    /**
     * Shows the item edit dialog, created with factory method of list model
     *
     * @param listPosition the position of selected item, -1 means "create new item"
     */
    void showServerEditDialog(int listPosition) {
        DialogFragment securityDialog = mAdapter.createItemEditDialogFragment(listPosition);

        FragmentTransaction ft = getSupportFragmentManager().beginTransaction();
        securityDialog.show(ft, "ServerItemDialogFragment");
    }

    @Override
    public boolean onKeyUp(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_BACK) {
            Intent result = new Intent();
            result.putExtra(JABBER_REGISTRATION_KEY, registration);
            setResult(Activity.RESULT_OK, result);
            finish();
            return true;
        }
        return super.onKeyUp(keyCode, event);
    }

    /**
     * The server list fragment. Required to catch events.
     */
    static public class ServerListFragment extends ListFragment {
        @Override
        public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
            super.onViewCreated(view, savedInstanceState);
            setEmptyText(getString(R.string.service_gui_SERVERS_LIST_EMPTY));
        }

        @Override
        public void onListItemClick(@NonNull ListView l, @NonNull View v, int position, long id) {
            super.onListItemClick(l, v, position, id);
            ((ServerListActivity) getActivity()).showServerEditDialog(position);
        }
    }
}
