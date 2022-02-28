/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.atalk.android.gui.account.settings;

import android.app.ProgressDialog;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;

import androidx.annotation.NonNull;
import androidx.preference.ListPreference;

import net.java.sip.communicator.service.gui.AccountRegistrationWizard;
import net.java.sip.communicator.service.protocol.*;
import net.java.sip.communicator.util.account.AccountUtils;

import org.atalk.android.R;
import org.atalk.android.gui.AndroidGUIActivator;
import org.atalk.android.gui.settings.util.SummaryMapper;
import org.atalk.service.osgi.OSGiPreferenceFragment;
import org.osgi.framework.*;

import timber.log.Timber;

/**
 * The fragment shares common parts for all protocols settings. It handles security and encoding preferences.
 *
 * @author Pawel Domas
 * @author Eng Chong Meng
 * @author MilanKral
 */
public abstract class AccountPreferenceFragment extends OSGiPreferenceFragment
        implements SharedPreferences.OnSharedPreferenceChangeListener
{
    /**
     * Account unique ID extra key
     */
    public static final String EXTRA_ACCOUNT_ID = "accountID";

    /**
     * State key for "initialized" flag
     */
    private static final String STATE_INIT_FLAG = "initialized";

    /**
     * The key identifying edit encodings request
     */
    protected static final int EDIT_ENCODINGS = 1;

    /**
     * The key identifying edit security details request
     */
    protected static final int EDIT_SECURITY = 2;

    /**
     * The ID of protocol preferences xml file passed in constructor
     */
    private final int preferencesResourceId;

    /**
     * Utility that maps current preference value to summary
     */
    private final SummaryMapper summaryMapper = new SummaryMapper();

    /**
     * Flag indicating if there are uncommitted changes - need static to avoid clear by android OS
     */
    protected static boolean uncommittedChanges;

    /**
     * The progress dialog shown when changes are being committed
     */
    private ProgressDialog mProgressDialog;

    /**
     * The wizard used to edit accounts
     */
    private AccountRegistrationWizard wizard;
    /**
     * We load values only once into shared preferences to not reset values on screen rotated event.
     */
    private boolean initialized = false;

    /**
     * The {@link Thread} which runs the commit operation in background
     */
    private Thread commitThread;

    protected ListPreference dnssecModeLP;

    /**
     * Edited {@link AccountID}
     */
    private AccountID mAccountID;

    /**
     * Parent Activity of the Account Preference Fragment.
     * Initialize onCreate. Dynamic retrieve may sometimes return null;
     */
    protected AccountPreferenceActivity mActivity;
    protected SharedPreferences shPrefs;

    /**
     * Creates new instance of {@link AccountPreferenceFragment}
     *
     * @param preferencesResourceId the ID of preferences xml file for current protocol
     */
    public AccountPreferenceFragment(int preferencesResourceId)
    {
        this.preferencesResourceId = preferencesResourceId;
    }

    /**
     * Method should return <code>EncodingsRegistrationUtil</code> if it supported by impl fragment.
     * Preference categories with keys: <code>pref_cat_audio_encoding</code> and/or
     * <code>pref_cat_video_encoding</code> must be included in preferences xml to trigger encodings activities.
     *
     * @return impl fragments should return <code>EncodingsRegistrationUtil</code> if encodings are supported.
     */
    protected abstract EncodingsRegistrationUtil getEncodingsRegistration();

    /**
     * Method should return <code>SecurityAccountRegistration</code> if security details are supported
     * by impl fragment. Preference category with key <code>pref_key_enable_encryption</code> must be
     * present to trigger security edit activity.
     *
     * @return <code>SecurityAccountRegistration</code> if security details are supported by impl fragment.
     */
    protected abstract SecurityAccountRegistration getSecurityRegistration();

    /**
     * Returns currently used <code>AccountRegistrationWizard</code>.
     *
     * @return currently used <code>AccountRegistrationWizard</code>.
     */
    protected AccountRegistrationWizard getWizard()
    {
        return wizard;
    }

    /**
     * Returns currently edited {@link AccountID}.
     *
     * @return currently edited {@link AccountID}.
     */
    protected AccountID getAccountID()
    {
        return mAccountID;
    }

    /**
     * Returns <code>true</code> if preference views have been initialized with values from the registration object.
     *
     * @return <code>true</code> if preference views have been initialized with values from the registration object.
     */
    protected boolean isInitialized()
    {
        return initialized;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey)
    {
        // Load the preferences from the given resource
        super.onCreatePreferences(savedInstanceState, rootKey);
        setPreferencesFromResource(preferencesResourceId, rootKey);

        if (savedInstanceState != null) {
            initialized = savedInstanceState.getBoolean(STATE_INIT_FLAG);
        }

        mActivity = (AccountPreferenceActivity) getActivity();
        String accountID = getArguments().getString(EXTRA_ACCOUNT_ID);
        AccountID account = AccountUtils.getAccountIDForUID(accountID);

        ProtocolProviderService pps = AccountUtils.getRegisteredProviderForAccount(account);
        if (pps == null) {
            Timber.w("No protocol provider registered for %s", account);
            mActivity.finish();
            return;
        }

        shPrefs = getPreferenceManager().getSharedPreferences();
        shPrefs.registerOnSharedPreferenceChangeListener(this);
        shPrefs.registerOnSharedPreferenceChangeListener(summaryMapper);

        /*
         * Workaround for de-synchronization problem when account was created for the first time.
         * During account creation process another instance was returned by AccountManager and
         * another from corresponding ProtocolProvider. We should use that one from the provider.
         */
        account = pps.getAccountID();

        // Loads the account details
        loadAccount(account);

        // Preference View can be manipulated at this point
        onPreferencesCreated();

        // Preferences summaries mapping
        mapSummaries(summaryMapper);
    }

    /**
     * Unregisters preference listeners.
     */
    @Override
    public void onStop()
    {
        shPrefs.unregisterOnSharedPreferenceChangeListener(this);
        shPrefs.unregisterOnSharedPreferenceChangeListener(summaryMapper);
        dismissOperationInProgressDialog();
        super.onStop();
    }

    /**
     * Method fired when OSGI context is attached, but after the <code>View</code> is created.
     */
    @Override
    protected void onOSGiConnected()
    {
        super.onOSGiConnected();
    }

    /**
     * Fired when OSGI is started and the <code>bundleContext</code> is available.
     *
     * @param bundleContext the OSGI bundle context.
     */
    @Override
    public void start(BundleContext bundleContext)
            throws Exception
    {
        super.start(bundleContext);
    }

    /**
     * Load the <code>account</code> and its encoding and security properties if exist as reference for update
     * before merging with the original mAccountProperties in #doCommitChanges() in the sub-class
     *
     * @param account the {@link AccountID} that will be edited
     */
    public void loadAccount(AccountID account)
    {
        mAccountID = account;
        wizard = findRegistrationService(account.getProtocolName());
        if (wizard == null)
            throw new NullPointerException();

        if (initialized) {
            System.err.println("Initialized not loading account data");
            return;
        }

        ProtocolProviderService pps = AccountUtils.getRegisteredProviderForAccount(account);
        wizard.loadAccount(pps);
        onInitPreferences();
        initialized = true;
    }

    /**
     * Method is called before preference XML file is loaded. Subclasses should perform preference
     * views initialization here.
     */
    protected abstract void onInitPreferences();

    /**
     * Method is called after preference views have been created and can be found by using findPreference() method.
     */
    protected abstract void onPreferencesCreated();

    /**
     * Stores <code>initialized</code> flag.
     */
    @Override
    public void onSaveInstanceState(@NonNull Bundle outState)
    {
        super.onSaveInstanceState(outState);
        outState.putBoolean(STATE_INIT_FLAG, initialized);
    }

    /**
     * Finds the wizard for given protocol name
     *
     * @param protocolName the name of the protocol
     * @return {@link AccountRegistrationWizard} for given <code>protocolName</code>
     */
    AccountRegistrationWizard findRegistrationService(String protocolName)
    {
        ServiceReference[] accountWizardRefs;
        try {
            BundleContext context = AndroidGUIActivator.bundleContext;
            accountWizardRefs = context.getServiceReferences(AccountRegistrationWizard.class.getName(), null);

            for (ServiceReference accountWizardRef : accountWizardRefs) {
                AccountRegistrationWizard wizard = (AccountRegistrationWizard) context.getService(accountWizardRef);
                if (wizard.getProtocolName().equals(protocolName))
                    return wizard;
            }
        } catch (InvalidSyntaxException ex) {
            // this shouldn't happen since we're providing no parameter string but let's log just in case.
            Timber.e(ex, "Error while retrieving service refs");
        }
        throw new RuntimeException("No wizard found for protocol: " + protocolName);
    }

    /**
     * Method called after all preference Views are created and initialized. Subclasses can use
     * given <code>summaryMapper</code> to include it's preferences in summary mapping
     *
     * @param summaryMapper the {@link SummaryMapper} managed by this {@link AccountPreferenceFragment} that can
     * be used by subclasses to map preference's values into their summaries
     */
    protected abstract void mapSummaries(SummaryMapper summaryMapper);

    /**
     * Returns the string that should be used as preference summary when no value has been set.
     *
     * @return the string that should be used as preference summary when no value has been set.
     */
    protected String getEmptyPreferenceStr()
    {
        return getString(R.string.service_gui_SETTINGS_NOT_SET);
    }

    /**
     * Should be called by subclasses to indicate that some changes has been made to the account
     */
    protected static void setUncommittedChanges()
    {
        uncommittedChanges = true;
    }

    /**
     * {@inheritDoc}
     */
    public void onSharedPreferenceChanged(SharedPreferences shPrefs, String key)
    {
        uncommittedChanges = true;
    }

    /**
     * Subclasses should implement account changes commit in this method
     */
    protected abstract void doCommitChanges();

    /**
     * Commits the changes and shows "in progress" dialog
     */
    public void commitChanges()
    {
        if (!uncommittedChanges) {
            mActivity.finish();
            return;
        }
        try {
            if (commitThread != null)
                return;

            displayOperationInProgressDialog();
            commitThread = new Thread(() -> {
                doCommitChanges();
                mActivity.finish();
            });
            commitThread.start();
        } catch (Exception e) {
            Timber.e("Error occurred while trying to commit changes: %s", e.getMessage());
            mActivity.finish();
        }
    }

    /**
     * Shows the "in progress" dialog with a TOT of 5S if commit hangs
     */
    private void displayOperationInProgressDialog()
    {
        Context context = getView().getRootView().getContext();
        CharSequence title = getResources().getText(R.string.service_gui_COMMIT_PROGRESS_TITLE);
        CharSequence msg = getResources().getText(R.string.service_gui_COMMIT_PROGRESS_MSG);
        mProgressDialog = ProgressDialog.show(context, title, msg, true, false);

        new Handler().postDelayed(() -> {
            Timber.d("Timeout in saving");
            mActivity.finish();
        }, 5000);
    }

    /**
     * Hides the "in progress" dialog
     */
    private void dismissOperationInProgressDialog()
    {
        Timber.d("Dismiss mProgressDialog: %s", mProgressDialog);
        if (mProgressDialog != null && mProgressDialog.isShowing()) {
            mProgressDialog.dismiss();
            mProgressDialog = null;
        }
    }
}
