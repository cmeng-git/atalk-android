/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.atalk.android.gui.account.settings;

import android.app.Activity;
import android.app.ProgressDialog;
import android.content.*;
import android.os.Bundle;
import android.preference.ListPreference;
import android.preference.Preference;

import net.java.sip.communicator.service.gui.AccountRegistrationWizard;
import net.java.sip.communicator.service.protocol.*;
import net.java.sip.communicator.util.account.AccountUtils;

import org.atalk.android.R;
import org.atalk.android.aTalkApp;
import org.atalk.android.gui.AndroidGUIActivator;
import org.atalk.android.gui.aTalk;
import org.atalk.android.gui.settings.util.SummaryMapper;
import org.atalk.service.osgi.OSGiPreferenceFragment;
import org.atalk.util.MediaType;
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
    // PreferenceScreen and PreferenceCategories for Account Settings...
    static private final String P_KEY_TELEPHONY = aTalkApp.getResString(R.string.pref_screen_jbr_tele);
    static private final String P_KEY_CALL_ENCRYPT = aTalkApp.getResString(R.string.pref_key_enable_encryption);
    static private final String P_KEY_AUDIO_ENC = aTalkApp.getResString(R.string.pref_cat_audio_encoding);
    static private final String P_KEY_VIDEO_ENC = aTalkApp.getResString(R.string.pref_cat_video_encoding);
    /**
     * Account unique ID extra key
     */
    public static final String EXTRA_ACCOUNT_ID = "accountID";

    /**
     * State key for "initialized" flag
     */
    private static final String STATE_INIT_FLAG = "initialized";

    // Account section
    private static final String P_KEY_DNSSEC_MODE = aTalkApp.getResString(R.string.pref_key_dnssec_mode);

    /**
     * The key identifying edit encodings request
     */
    protected static final int EDIT_ENCODINGS = 1;

    /**
     * The key identifying edit security details request
     */
    protected static final int EDIT_SECURITY = 2;

    /**
     * Edited {@link AccountID}
     */
    private AccountID mAccountID;

    /**
     * The ID of protocol preferences xml file passed in constructor
     */
    private final int preferencesResourceId;

    /**
     * Utility that maps current preference value to summary
     */
    private SummaryMapper summaryMapper = new SummaryMapper();

    /**
     * Flag indicating if there are uncommitted changes - need static to avoid clear by android OS
     */
    private static boolean uncommittedChanges;

    /**
     * The progress dialog shown when changes are being committed
     */
    private ProgressDialog progressDialog;

    /**
     * The wizard used to edit accounts
     */
    private AccountRegistrationWizard wizard;
    /**
     * We load values only once into shared preferences to not reset values on screen rotated event.
     */
    private boolean initialized = false;

    /**
     * Parent Activity of the Account Preference Fragment.
     * Initialize onCreate. Dynamic retrieve may sometimes return null;
     */
    private Activity mActivity;

    protected ListPreference dnssecModeLP;

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
     * Method should return <tt>EncodingsRegistrationUtil</tt> if it supported by impl fragment.
     * Preference categories with keys: <tt>pref_cat_audio_encoding</tt> and/or
     * <tt>pref_cat_video_encoding</tt> must be included in preferences xml to trigger encodings activities.
     *
     * @return impl fragments should return <tt>EncodingsRegistrationUtil</tt> if encodings are supported.
     */
    protected abstract EncodingsRegistrationUtil getEncodingsRegistration();

    /**
     * Method should return <tt>SecurityAccountRegistration</tt> if security details are supported
     * by impl fragment. Preference category with key <tt>pref_key_enable_encryption</tt> must be
     * present to trigger security edit activity.
     *
     * @return <tt>SecurityAccountRegistration</tt> if security details are supported by impl fragment.
     */
    protected abstract SecurityAccountRegistration getSecurityRegistration();

    /**
     * Returns currently used <tt>AccountRegistrationWizard</tt>.
     *
     * @return currently used <tt>AccountRegistrationWizard</tt>.
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
     * Returns <tt>true</tt> if preference views have been initialized with values from the registration object.
     *
     * @return <tt>true</tt> if preference views have been initialized with values from the registration object.
     */
    protected boolean isInitialized()
    {
        return initialized;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);
        if (savedInstanceState != null) {
            initialized = savedInstanceState.getBoolean(STATE_INIT_FLAG);
        }

        // Load the preferences from an XML resource
        // addPreferencesFromResource(preferencesResourceId);

        mActivity = getActivity();
        String accountID = getArguments().getString(EXTRA_ACCOUNT_ID);
        AccountID account = AccountUtils.getAccountIDForUID(accountID);

        ProtocolProviderService pps = AccountUtils.getRegisteredProviderForAccount(account);
        if (pps == null) {
            Timber.w("No protocol provider registered for %s", account);
            mActivity.finish();
            return;
        }

        /*
         * Workaround for de-synchronization problem when account was created for the first time.
         * During account creation process another instance was returned by AccountManager and
         * another from corresponding ProtocolProvider. We should use that one from the provider.
         */
        account = pps.getAccountID();

        // Loads the account details
        loadAccount(account);

        // Loads preference Views. They will be initialized with values loaded into SharedPreferences in loadAccount
        addPreferencesFromResource(preferencesResourceId);

        // Preference View can be manipulated at this point
        onPreferencesCreated();

        // Preferences summaries mapping
        mapSummaries(summaryMapper);
    }

    /**
     * Method fired when OSGI context is attached, but after the <tt>View</tt> is created.
     */
    @Override
    protected void onOSGiConnected()
    {
        super.onOSGiConnected();
    }

    /**
     * Fired when OSGI is started and the <tt>bundleContext</tt> is available.
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
     * Load the <tt>account</tt> and its encoding and security properties if exist as reference for update
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
     * Method is called after preference views have been created and can be found by using
     * <tt>this.findPreference</tt> method.
     */
    protected void onPreferencesCreated()
    {
        dnssecModeLP = (ListPreference) findPreference(P_KEY_DNSSEC_MODE);

        if (aTalk.disableMediaServiceOnFault) {
            findPreference(P_KEY_CALL_ENCRYPT).setEnabled(false);
            findPreference(P_KEY_TELEPHONY).setEnabled(false);
            findPreference(P_KEY_AUDIO_ENC).setEnabled(false);
            findPreference(P_KEY_VIDEO_ENC).setEnabled(false);
        }
        else {
            // Audio,video and security are optional and should be present in settings XML to be handled
            Preference audioEncPreference = findPreference(P_KEY_AUDIO_ENC);
            if (audioEncPreference != null) {
                audioEncPreference.setOnPreferenceClickListener(preference -> {
                    startEncodingActivity(MediaType.AUDIO);
                    return true;
                });
            }

            Preference videoEncPreference = findPreference(P_KEY_VIDEO_ENC);
            if (videoEncPreference != null) {
                videoEncPreference.setOnPreferenceClickListener(preference -> {
                    startEncodingActivity(MediaType.VIDEO);
                    return true;
                });
            }

            Preference encryptionOnOff = findPreference(P_KEY_CALL_ENCRYPT);
            if (encryptionOnOff != null) {
                encryptionOnOff.setOnPreferenceClickListener(preference -> {
                    startSecurityActivity();
                    return true;
                });
            }
        }
    }

    /**
     * Stores <tt>initialized</tt> flag.
     */
    @Override
    public void onSaveInstanceState(Bundle outState)
    {
        super.onSaveInstanceState(outState);
        outState.putBoolean(STATE_INIT_FLAG, initialized);
    }

    /**
     * Finds the wizard for given protocol name
     *
     * @param protocolName the name of the protocol
     * @return {@link AccountRegistrationWizard} for given <tt>protocolName</tt>
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
     * given <tt>summaryMapper</tt> to include it's preferences in summary mapping
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
        return getResources().getString(R.string.service_gui_SETTINGS_NOT_SET);
    }

    /**
     * Starts the {@link SecurityActivity} to edit account's security preferences
     */
    private void startSecurityActivity()
    {
        Intent intent = new Intent(mActivity, SecurityActivity.class);
        SecurityAccountRegistration securityRegistration = getSecurityRegistration();
        if (securityRegistration == null)
            throw new NullPointerException();

        intent.putExtra(SecurityActivity.EXTR_KEY_SEC_REGISTRATION, securityRegistration);
        startActivityForResult(intent, EDIT_SECURITY);
    }

    /**
     * Starts the {@link MediaEncodingActivity} in order to edit encoding properties.
     *
     * @param mediaType indicates if AUDIO or VIDEO encodings will be edited
     */
    private void startEncodingActivity(MediaType mediaType)
    {
        Intent intent = new Intent(mActivity, MediaEncodingActivity.class);
        intent.putExtra(MediaEncodingActivity.ENC_MEDIA_TYPE_KEY, mediaType);

        EncodingsRegistrationUtil encodingsRegistration = getEncodingsRegistration();
        if (encodingsRegistration == null)
            throw new NullPointerException();
        intent.putExtra(MediaEncodingActivity.EXTRA_KEY_ENC_REG, encodingsRegistration);
        startActivityForResult(intent, EDIT_ENCODINGS);
    }

    /**
     * Handles {@link MediaEncodingActivity} and {@link SecurityActivity} results
     */
    public void onActivityResult(int requestCode, int resultCode, Intent data)
    {
        if (requestCode == EDIT_ENCODINGS && resultCode == Activity.RESULT_OK) {
            boolean hasChanges = data.getBooleanExtra(MediaEncodingActivity.EXTRA_KEY_HAS_CHANGES, false);
            if (!hasChanges)
                return;

            EncodingsRegistrationUtil encReg = (EncodingsRegistrationUtil)
                    data.getSerializableExtra(MediaEncodingActivity.EXTRA_KEY_ENC_REG);

            EncodingsRegistrationUtil myReg = getEncodingsRegistration();
            myReg.setOverrideEncodings(encReg.isOverrideEncodings());
            myReg.setEncodingProperties(encReg.getEncodingProperties());
            uncommittedChanges = true;
        }
        else if (requestCode == EDIT_SECURITY && resultCode == Activity.RESULT_OK) {
            boolean hasChanges = data.getBooleanExtra(SecurityActivity.EXTR_KEY_HAS_CHANGES, false);
            if (!hasChanges)
                return;

            SecurityAccountRegistration secReg = (SecurityAccountRegistration)
                    data.getSerializableExtra(SecurityActivity.EXTR_KEY_SEC_REGISTRATION);

            SecurityAccountRegistration myReg = getSecurityRegistration();
            myReg.setDefaultEncryption(secReg.isDefaultEncryption());
            myReg.setEncryptionProtocols(secReg.getEncryptionProtocols());
            myReg.setEncryptionProtocolStatus(secReg.getEncryptionProtocolStatus());
            myReg.setSipZrtpAttribute(secReg.isSipZrtpAttribute());
            myReg.setSavpOption(secReg.getSavpOption());
            myReg.setSDesCipherSuites(secReg.getSDesCipherSuites());
            myReg.setZIDSalt(secReg.getZIDSalt());
            uncommittedChanges = true;
        }
    }

    /**
     * Registers preference listeners.
     */
    @Override
    public void onResume()
    {
        super.onResume();
        getPreferenceScreen().getSharedPreferences().registerOnSharedPreferenceChangeListener(this);
        getPreferenceScreen().getSharedPreferences().registerOnSharedPreferenceChangeListener(summaryMapper);
    }

    /**
     * Unregisters preference listeners.
     */
    @Override
    public void onPause()
    {
        getPreferenceScreen().getSharedPreferences().unregisterOnSharedPreferenceChangeListener(this);
        getPreferenceScreen().getSharedPreferences().unregisterOnSharedPreferenceChangeListener(summaryMapper);
        dismissOperationInProgressDialog();
        super.onPause();
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
        if (!uncommittedChanges)
            return;
        try {
            mActivity.runOnUiThread(this::displayOperationInProgressDialog);
            doCommitChanges();
            mActivity.runOnUiThread(this::dismissOperationInProgressDialog);
        } catch (Exception e) {
            Timber.e(e, "Error occurred while trying to commit changes");
        }
    }

    /**
     * Shows the "in progress" dialog
     */
    private void displayOperationInProgressDialog()
    {
        Context context = getView().getRootView().getContext();
        CharSequence title = getResources().getText(R.string.service_gui_COMMIT_PROGRESS_TITLE);
        CharSequence msg = getResources().getText(R.string.service_gui_COMMIT_PROGRESS_MSG);

        this.progressDialog = ProgressDialog.show(context, title, msg, true, false);
        // Display the progress dialog
        progressDialog.show();
    }

    /**
     * Hides the "in progress" dialog
     */
    private void dismissOperationInProgressDialog()
    {
        if (progressDialog != null && progressDialog.isShowing()) {
            progressDialog.dismiss();
            progressDialog = null;
        }
    }
}
