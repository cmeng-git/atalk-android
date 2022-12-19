/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.atalk.android.gui.account.settings;

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.text.TextUtils;

import androidx.activity.result.*;
import androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult;
import androidx.preference.Preference;

import net.java.sip.communicator.impl.msghistory.MessageHistoryActivator;
import net.java.sip.communicator.impl.msghistory.MessageHistoryServiceImpl;
import net.java.sip.communicator.plugin.jabberaccregwizz.AccountRegistrationImpl;
import net.java.sip.communicator.service.protocol.*;
import net.java.sip.communicator.service.protocol.jabber.JabberAccountRegistration;

import org.atalk.android.R;
import org.atalk.android.aTalkApp;
import org.atalk.android.gui.aTalk;
import org.atalk.android.gui.dialogs.DialogActivity;
import org.atalk.android.gui.settings.util.SummaryMapper;
import org.atalk.util.MediaType;

import timber.log.Timber;

/**
 * Preferences fragment for Jabber settings. It maps Jabber specific properties to the
 * {@link Preference}s. Reads from and stores them inside {@link JabberAccountRegistration}.
 *
 * This is an instance of the accountID properties from Account Setting... preference editing. These changes
 * will be merged with the original mAccountProperties and saved to database in doCommitChanges()
 *
 * @author Pawel Domas
 * @author Eng Chong Meng
 * @author MilanKral
 */
public class JabberPreferenceFragment extends AccountPreferenceFragment
{
    // PreferenceScreen and PreferenceCategories for Account Settings...
    static private final String P_KEY_TELEPHONY = aTalkApp.getResString(R.string.pref_screen_jbr_tele);
    static private final String P_KEY_CALL_ENCRYPT = aTalkApp.getResString(R.string.pref_key_enable_encryption);
    static private final String P_KEY_AUDIO_ENC = aTalkApp.getResString(R.string.pref_cat_audio_encoding);
    static private final String P_KEY_VIDEO_ENC = aTalkApp.getResString(R.string.pref_cat_video_encoding);

    // Account Settings
    private static final String P_KEY_USER_ID = aTalkApp.getResString(R.string.pref_key_user_id);
    private static final String P_KEY_PASSWORD = aTalkApp.getResString(R.string.pref_key_password);
    private static final String P_KEY_STORE_PASSWORD = aTalkApp.getResString(R.string.pref_key_store_password);
    static private final String P_KEY_DNSSEC_MODE = aTalkApp.getResString(R.string.pref_key_dnssec_mode);

    // Proxy
    private static final String P_KEY_PROXY_CONFIG = aTalkApp.getResString(R.string.pref_key_bosh_configuration);

    /*
     * A new instance of AccountID and is not the same as accountID.
     * Defined as static, otherwise it may get clear onActivityResult - on some android devices
     */
    public static JabberAccountRegistration jbrReg;

    /**
     * Current user userName which is being edited.
     */
    private String userNameEdited;

    /**
     * user last entered userName to check for anymore new changes in userName
     */
    private String userNameLastEdited;

    /**
     * Creates new instance of <code>JabberPreferenceFragment</code>
     */
    public JabberPreferenceFragment()
    {
        super(R.xml.acc_jabber_preferences);
    }

    /**
     * Returns jabber registration wizard.
     *
     * @return jabber registration wizard.
     */
    private AccountRegistrationImpl getJbrWizard()
    {
        return (AccountRegistrationImpl) getWizard();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected EncodingsRegistrationUtil getEncodingsRegistration()
    {
        return jbrReg.getEncodingsRegistration();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected SecurityAccountRegistration getSecurityRegistration()
    {
        return jbrReg.getSecurityRegistration();
    }

    /**
     * {@inheritDoc}
     */
    protected void onInitPreferences()
    {
        AccountRegistrationImpl wizard = getJbrWizard();
        jbrReg = wizard.getAccountRegistration();

        SharedPreferences.Editor editor = shPrefs.edit();

        // User name and password
        userNameEdited = jbrReg.getUserID();
        userNameLastEdited = userNameEdited;

        editor.putString(P_KEY_USER_ID, userNameEdited);
        editor.putString(P_KEY_PASSWORD, jbrReg.getPassword());
        editor.putBoolean(P_KEY_STORE_PASSWORD, jbrReg.isRememberPassword());
        editor.putString(P_KEY_DNSSEC_MODE, jbrReg.getDnssMode());

        editor.apply();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void onPreferencesCreated()
    {
        dnssecModeLP = findPreference(P_KEY_DNSSEC_MODE);

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

        findPreference(P_KEY_PROXY_CONFIG).setOnPreferenceClickListener(pref -> {
            BoshProxyDialog boshProxy = new BoshProxyDialog(mActivity, jbrReg);
            boshProxy.setTitle(R.string.service_gui_JBR_ICE_SUMMARY);
            boshProxy.show();
            return true;
        });

//        findPreference(P_KEY_USER_ID).setOnPreferenceClickListener(preference -> {
//            startAccountEditor();
//            return true;
//        });
    }

//    private void startAccountEditor()
//    {
//        // Create AccountLoginFragment fragment
//        String login = "swordfish@atalk.sytes.net";
//        String password = "1234";
//
//        Intent intent = new Intent(mActivity, AccountLoginActivity.class);
//        intent.putExtra(AccountLoginFragment.ARG_USERNAME, login);
//        intent.putExtra(AccountLoginFragment.ARG_PASSWORD, password);
//        startActivity(intent);
//    }


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
        getSecurityRegistration.launch(intent);
    }

    /**
     * Handles {@link SecurityActivity} results
     */
    ActivityResultLauncher<Intent> getSecurityRegistration = registerForActivityResult(new StartActivityForResult(),
            result -> {
                if (result.getResultCode() == Activity.RESULT_OK) {
                    Intent data = result.getData();

                    boolean hasChanges = data.getBooleanExtra(SecurityActivity.EXTR_KEY_HAS_CHANGES, false);
                    if (!hasChanges)
                        return;

                    SecurityAccountRegistration secReg = (SecurityAccountRegistration)
                            data.getSerializableExtra(SecurityActivity.EXTR_KEY_SEC_REGISTRATION);

                    SecurityAccountRegistration myReg = getSecurityRegistration();
                    myReg.setCallEncryption(secReg.isCallEncryption());
                    myReg.setEncryptionProtocol(secReg.getEncryptionProtocol());
                    myReg.setEncryptionProtocolStatus(secReg.getEncryptionProtocolStatus());
                    myReg.setSipZrtpAttribute(secReg.isSipZrtpAttribute());
                    myReg.setZIDSalt(secReg.getZIDSalt());
                    myReg.setDtlsCertSa(secReg.getDtlsCertSa());
                    myReg.setSavpOption(secReg.getSavpOption());
                    myReg.setSDesCipherSuites(secReg.getSDesCipherSuites());
                    uncommittedChanges = true;
                }
            }
    );

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
        getEncodingRegistration.launch(intent);
    }

    /**
     * Handles {@link MediaEncodingActivity}results
     */
    ActivityResultLauncher<Intent> getEncodingRegistration = registerForActivityResult(new StartActivityForResult(),
            result -> {
                if (result.getResultCode() == Activity.RESULT_OK) {
                    Intent data = result.getData();

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
            }
    );

    /**
     * {@inheritDoc}
     */
    protected void mapSummaries(SummaryMapper summaryMapper)
    {
        String emptyStr = getEmptyPreferenceStr();

        // User name and password
        summaryMapper.includePreference(findPreference(P_KEY_USER_ID), emptyStr);
        summaryMapper.includePreference(findPreference(P_KEY_PASSWORD), emptyStr, new SummaryMapper.PasswordMask());
        summaryMapper.includePreference(findPreference(P_KEY_DNSSEC_MODE), emptyStr);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void onSharedPreferenceChanged(SharedPreferences shPrefs, String key)
    {
        // Check to ensure a valid key before proceed
        if (findPreference(key) == null)
            return;

        super.onSharedPreferenceChanged(shPrefs, key);
        if (key.equals(P_KEY_USER_ID)) {
            getUserConfirmation(shPrefs);
        }
        else if (key.equals(P_KEY_PASSWORD)) {
            // seems the encrypted password is not save to DB but work?- need further investigation in signin if have problem
            jbrReg.setPassword(shPrefs.getString(P_KEY_PASSWORD, null));
        }
        else if (key.equals(P_KEY_STORE_PASSWORD)) {
            jbrReg.setRememberPassword(shPrefs.getBoolean(P_KEY_STORE_PASSWORD, false));
        }
        else if (key.equals(P_KEY_DNSSEC_MODE)) {
            String dnssecMode = shPrefs.getString(P_KEY_DNSSEC_MODE,
                    getResources().getStringArray(R.array.dnssec_Mode_value)[0]);
            jbrReg.setDnssMode(dnssecMode);
        }
    }

    /**
     * Warn and get user confirmation if changes of userName will lead to removal of any old messages
     * of the old account. It also checks for valid userName entry.
     *
     * @param shPrefs SharedPreferences
     */
    private void getUserConfirmation(SharedPreferences shPrefs)
    {
        final String userName = shPrefs.getString(P_KEY_USER_ID, null);
        if (!TextUtils.isEmpty(userName) && userName.contains("@")) {
            String editedAccUid = jbrReg.getAccountUniqueID();
            if (userNameEdited.equals(userName)) {
                jbrReg.setUserID(userName);
                userNameLastEdited = userName;
            }
            else if (!userNameLastEdited.equals(userName)) {
                MessageHistoryServiceImpl mhs = MessageHistoryActivator.getMessageHistoryService();
                int msgCount = mhs.getMessageCountForAccountUuid(editedAccUid);
                if (msgCount > 0) {
                    String msgPrompt = aTalkApp.getResString(R.string.service_gui_USERNAME_CHANGE_WARN,
                            userName, msgCount, userNameEdited);
                    DialogActivity.showConfirmDialog(aTalkApp.getGlobalContext(),
                            aTalkApp.getResString(R.string.service_gui_WARNING), msgPrompt,
                            aTalkApp.getResString(R.string.service_gui_PROCEED), new DialogActivity.DialogListener()
                            {
                                @Override
                                public boolean onConfirmClicked(DialogActivity dialog)
                                {
                                    jbrReg.setUserID(userName);
                                    userNameLastEdited = userName;
                                    return true;
                                }

                                @Override
                                public void onDialogCancelled(DialogActivity dialog)
                                {
                                    jbrReg.setUserID(userNameEdited);
                                    userNameLastEdited = userNameEdited;
                                    SharedPreferences.Editor editor = shPrefs.edit();
                                    editor.putString(P_KEY_USER_ID, jbrReg.getUserID());
                                    editor.apply();
                                }
                            });

                }
                else {
                    jbrReg.setUserID(userName);
                    userNameLastEdited = userName;
                }
            }
        }
        else {
            userNameLastEdited = userNameEdited;
            aTalkApp.showToastMessage(R.string.service_gui_USERNAME_NULL);
        }
    }

    /**
     * This is executed when the user press BackKey. Signin with modification will merge the change properties
     * i.e jbrReg.getAccountProperties() with the accountID mAccountProperties before saving to SQL database
     */
    @Override
    protected void doCommitChanges()
    {
        try {
            AccountRegistrationImpl accWizard = getJbrWizard();
            accWizard.setModification(true);
            accWizard.signin(jbrReg.getUserID(), jbrReg.getPassword(), jbrReg.getAccountProperties());
        } catch (OperationFailedException e) {
            Timber.e("Failed to store account modifications: %s", e.getLocalizedMessage());
        }
    }
}
