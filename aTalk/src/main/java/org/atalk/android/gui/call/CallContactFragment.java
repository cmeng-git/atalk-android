/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.atalk.android.gui.call;

import android.Manifest;
import android.accounts.Account;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.*;
import android.widget.*;

import net.java.sip.communicator.service.protocol.ProtocolProviderService;
import net.java.sip.communicator.util.account.AccountUtils;

import org.atalk.android.R;
import org.atalk.android.gui.util.AndroidUtils;
import org.atalk.android.gui.util.ViewUtil;
import org.atalk.service.osgi.OSGiFragment;
import org.jivesoftware.smack.XMPPConnection;
import org.jivesoftware.smack.roster.Roster;
import org.jxmpp.jid.Jid;
import org.jxmpp.jid.impl.JidCreate;
import org.jxmpp.stringprep.XmppStringprepException;
import org.osgi.framework.BundleContext;

import java.util.Collection;

import androidx.annotation.NonNull;
import androidx.core.app.ActivityCompat;
import timber.log.Timber;

/**
 * Tha <tt>CallContactFragment</tt> encapsulated GUI used to make a call.
 *
 * @author Yana Stamcheva
 * @author Pawel Domas
 * @author Eng Chong Meng
 */
public class CallContactFragment extends OSGiFragment
{
    /**
     * The bundle context.
     */
    private BundleContext bundleContext;

    /**
     * Optional phone number argument.
     */
    public static String ARG_PHONE_NUMBER = "arg.phone_number";

    private final int PERMISSION_GET_ACCOUNTS = 10;

    /**
     * {@inheritDoc}
     */
    @Override
    public synchronized void start(BundleContext bundleContext)
            throws Exception
    {
        super.start(bundleContext);
        /*
         * If there are unit tests to be run, do not run anything else and just perform
         * the unit tests.
         */
        if (System.getProperty("net.java.sip.communicator.slick.runner.TEST_LIST") != null)
            return;

        this.bundleContext = bundleContext;
        // initAndroidAccounts();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState)
    {
        final View content = inflater.inflate(R.layout.call_contact, container, false);

        final ImageView callButton = content.findViewById(R.id.callButtonFull);
        callButton.setOnClickListener(v -> {
            String contact = ViewUtil.toString(content.findViewById(R.id.callField));
            if (contact == null) {
                System.err.println("Contact is empty");
            }
            else {
                showCallViaMenu(callButton, contact);
            }
        });

        // Call intent handling
        Bundle arguments = getArguments();
        String phoneNumber = arguments.getString(ARG_PHONE_NUMBER);
        if (!TextUtils.isEmpty(phoneNumber)) {
            ViewUtil.setTextViewValue(content, R.id.callField, phoneNumber);
        }
        return content;
    }

    /**
     * Shows "call via" menu allowing user to selected from multiple providers if available.
     *
     * @param v the View that will contain the popup menu.
     * @param calleeAddress target callee name.
     */
    private void showCallViaMenu(View v, final String calleeAddress)
    {
        PopupMenu popup = new PopupMenu(getActivity(), v);
        Menu menu = popup.getMenu();
        ProtocolProviderService mProvider = null;

        Collection<ProtocolProviderService> onlineProviders = AccountUtils.getOnlineProviders();

        for (final ProtocolProviderService provider : onlineProviders) {
            XMPPConnection connection = provider.getConnection();
            try {
                if (Roster.getInstanceFor(connection).contains(JidCreate.bareFrom(calleeAddress))) {

                    String accountAddress = provider.getAccountID().getAccountJid();

                    MenuItem menuItem = menu.add(Menu.NONE, Menu.NONE, Menu.NONE, accountAddress);
                    menuItem.setOnMenuItemClickListener(item -> {
                        createCall(provider, calleeAddress);
                        return false;
                    });
                    mProvider = provider;
                }
            } catch (XmppStringprepException e) {
                e.printStackTrace();
            }
        }
        if (menu.size() > 1)
            popup.show();
        else
            createCall(mProvider, calleeAddress);
    }

    /**
     * Creates new call to given <tt>destination</tt> using selected <tt>provider</tt>.
     *
     * @param destination target callee name.
     * @param provider the provider that will be used to make a call.
     */
    private void createCall(final ProtocolProviderService provider, final String destination)
    {
        new Thread()
        {
            public void run()
            {
                try {
                    CallManager.createCall(provider, destination, false);
                } catch (Throwable t) {
                    Timber.e(t, "Error creating the call: %s", t.getMessage());
                    AndroidUtils.showAlertDialog(getActivity(), getString(R.string.service_gui_ERROR), t.getMessage());
                }
            }
        }.start();
    }

    /**
     * Loads Android accounts.
     */
    private void initAndroidAccounts()
    {
        if (ActivityCompat.checkSelfPermission(getActivity(),
                Manifest.permission.GET_ACCOUNTS) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(getActivity(),
                    new String[]{Manifest.permission.GET_ACCOUNTS}, PERMISSION_GET_ACCOUNTS);
        }
        else {
            android.accounts.AccountManager androidAccManager = android.accounts.AccountManager.get(getActivity());
            Account[] androidAccounts = androidAccManager.getAccountsByType(getString(R.string.ACCOUNT_TYPE));
            for (Account account : androidAccounts) {
                System.err.println("ACCOUNT======" + account);
            }
        }
    }

    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
            @NonNull int[] grantResults)
    {
        // If request is cancelled, the result arrays are empty.
        if (requestCode == PERMISSION_GET_ACCOUNTS) {
            if ((grantResults.length > 0)
                    && (grantResults[0] == PackageManager.PERMISSION_GRANTED)) {
                // permission granted, so proceed
                initAndroidAccounts();
            }
        }
    }

    /**
     * Creates new parametrized instance of <tt>CallContactFragment</tt>.
     *
     * @param phoneNumber optional phone number that will be filled.
     * @return new parameterized instance of <tt>CallContactFragment</tt>.
     */
    public static CallContactFragment newInstance(String phoneNumber)
    {
        CallContactFragment ccFragment = new CallContactFragment();
        Bundle args = new Bundle();
        args.putString(ARG_PHONE_NUMBER, phoneNumber);

        ccFragment.setArguments(args);
        return ccFragment;
    }
}