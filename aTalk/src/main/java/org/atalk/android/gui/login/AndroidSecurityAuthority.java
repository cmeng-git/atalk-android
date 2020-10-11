/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.atalk.android.gui.login;

import android.content.Context;
import android.os.Bundle;
import android.os.Looper;
import android.text.TextUtils;
import android.view.View;
import android.widget.Spinner;

import net.java.sip.communicator.impl.msghistory.MessageHistoryActivator;
import net.java.sip.communicator.impl.msghistory.MessageHistoryServiceImpl;
import net.java.sip.communicator.service.protocol.*;

import org.atalk.android.R;
import org.atalk.android.aTalkApp;
import org.atalk.android.gui.dialogs.DialogActivity;
import org.atalk.android.gui.util.ViewUtil;

import timber.log.Timber;

/**
 * Android <tt>SecurityAuthority</tt> implementation.
 *
 * The method checks for valid reason based on given reasonCode. Pending on the given reason, it
 * either launches a user login dialog or displays an error message.
 * When launching a login dialog, it will waits until the right activity is in view before
 * displaying the dialog to the user. Otherwise the dialog may be obscured by other activity display
 * windows. The login dialog menu allows user to change certain account settings before signing in.
 *
 * @author Pawel Domas
 * @author Eng Chong Meng
 */
public class AndroidSecurityAuthority implements SecurityAuthority
{
    /**
     * If user name should be editable when asked for credentials.
     */
    private boolean isUserNameEditable = true;

    /**
     * user last entered userName to check for anymore new changes in userName
     */
    private String userNameLastEdited;

    /**
     * Returns a UserCredentials object associated with the specified realm (accountID), by
     * specifying the reason of this operation. Or display an error message.
     *
     * @param accountID The realm (accountID) that the credentials are needed for.
     * @param defaultValues the values to propose the user by default
     * @param reasonCode indicates the reason for which we're obtaining the credentials.
     * @return The credentials associated with the specified realm or null if none could be obtained.
     */
    public UserCredentials obtainCredentials(AccountID accountID, UserCredentials defaultValues,
            int reasonCode, Boolean isShowServerOption)
    {
        if (reasonCode != SecurityAuthority.REASON_UNKNOWN) {
            return obtainCredentials(accountID, defaultValues, isShowServerOption);
        }

        Context ctx = aTalkApp.getGlobalContext();
        String errorMessage = aTalkApp.getResString(R.string.service_gui_CONNECTION_FAILED_MSG,
                defaultValues.getUserName(), defaultValues.getServerAddress());

        DialogActivity.showDialog(ctx, aTalkApp.getResString(R.string.service_gui_LOGIN_FAILED), errorMessage);
        return defaultValues;
    }

    /**
     * Returns a UserCredentials object associated with the specified realm (AccountID), by
     * specifying the reason of this operation.
     *
     * @param accountID The accountId / realm that the credentials are needed for.
     * @param credentials the values to propose the user by default
     * @return The credentials associated with the specified realm or null if none could be obtained.
     */
    public UserCredentials obtainCredentials(final AccountID accountID,
            final UserCredentials credentials, final Boolean isShowServerOption)
    {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            Timber.e("Cannot obtain credentials from the main thread!");
            return credentials;
        }

        // Insert DialogActivity arguments
        Bundle args = new Bundle();
        // Login userName and editable state
        String userName = credentials.getUserName();
        userNameLastEdited = userName;
        args.putString(CredentialsFragment.ARG_LOGIN, userName);
        args.putBoolean(CredentialsFragment.ARG_LOGIN_EDITABLE, isUserNameEditable);

        // Password argument
        char[] password = credentials.getPassword();
        if (password != null) {
            args.putString(CredentialsFragment.ARG_PASSWORD, credentials.getPasswordAsString());
        }

        String dnssecMode = accountID.getDnssMode();
        args.putString(CredentialsFragment.ARG_DNSSEC_MODE, dnssecMode);

        // Persistent password argument
        args.putBoolean(CredentialsFragment.ARG_STORE_PASSWORD, credentials.isPasswordPersistent());

        // InBand Registration argument
        args.putBoolean(CredentialsFragment.ARG_IB_REGISTRATION, accountID.isIbRegistration());
        args.putString(CredentialsFragment.ARG_CERT_ID, accountID.getTlsClientCertificate());

        args.putBoolean(CredentialsFragment.ARG_IS_SHOWN_SERVER_OPTION, isShowServerOption);
        if (isShowServerOption) {
            // Server overridden argument
            args.putBoolean(CredentialsFragment.ARG_IS_SERVER_OVERRIDDEN, accountID.isServerOverridden());
            args.putString(CredentialsFragment.ARG_SERVER_ADDRESS, accountID.getServerAddress());
            args.putString(CredentialsFragment.ARG_SERVER_PORT, accountID.getServerPort());
        }
        args.putString(CredentialsFragment.ARG_LOGIN_REASON, credentials.getLoginReason());
        aTalkApp.waitForFocus();

        // Obtain credentials lock
        final Object credentialsLock = new Object();

        // Displays the credentials dialog and waits for it to complete
        DialogActivity.showCustomDialog(aTalkApp.getGlobalContext(),
                aTalkApp.getResString(R.string.service_gui_LOGIN_CREDENTIAL), CredentialsFragment.class.getName(),
                args, aTalkApp.getResString(R.string.service_gui_SIGN_IN), new DialogActivity.DialogListener()
                {
                    public boolean onConfirmClicked(DialogActivity dialog)
                    {
                        View dialogContent = dialog.findViewById(R.id.alertContent);
                        String userNameEntered = ViewUtil.getTextViewValue(dialogContent, R.id.username);
                        String password = ViewUtil.getTextViewValue(dialogContent, R.id.password);

                        boolean storePassword = ViewUtil.isCompoundChecked(dialogContent, R.id.store_password);
                        boolean ibRegistration = ViewUtil.isCompoundChecked(dialogContent, R.id.ib_registration);

                        if (!userNameLastEdited.equals(userNameEntered)) {
                            int msgCount = checkPurgedMsgCount(accountID.getAccountUniqueID(), userNameEntered);
                            if (msgCount < 0) {
                                userNameLastEdited = userName;
                                ViewUtil.setTextViewValue(dialogContent, R.id.reason_field,
                                        aTalkApp.getResString(R.string.service_gui_USERNAME_NULL));
                                return false;
                            }
                            else if (msgCount > 0) {
                                String msgReason = aTalkApp.getResString(R.string.service_gui_USERNAME_CHANGE_WARN,
                                        userNameEntered, msgCount, userName);
                                ViewUtil.setTextViewValue(dialogContent, R.id.reason_field, msgReason);
                                ViewUtil.setTextViewColor(dialogContent, R.id.reason_field, R.color.red);
                                userNameLastEdited = userNameEntered;
                                return false;
                            }
                        }

                        credentials.setUserName(userNameEntered);
                        credentials.setPassword((password != null) ? password.toCharArray() : null);
                        credentials.setPasswordPersistent(storePassword);
                        credentials.setIbRegistration(ibRegistration);

                        // Translate dnssecMode label to dnssecMode value
                        Spinner spinnerDM = dialogContent.findViewById(R.id.dnssecModeSpinner);
                        String[] dnssecModeValues = aTalkApp.getGlobalContext().getResources()
                                .getStringArray(R.array.dnssec_Mode_value);
                        String selectedDnssecMode = dnssecModeValues[spinnerDM.getSelectedItemPosition()];
                        credentials.setDnssecMode(selectedDnssecMode);

                        if (isShowServerOption) {
                            boolean isServerOverridden = ViewUtil.isCompoundChecked(dialogContent, R.id.serverOverridden);
                            String serverAddress = ViewUtil.getTextViewValue(dialogContent, R.id.serverIpField);
                            String serverPort = ViewUtil.getTextViewValue(dialogContent, R.id.serverPortField);
                            if (serverAddress == null || serverPort == null) {
                                aTalkApp.showToastMessage(R.string.plugin_certconfig_INCOMPLETE);
                                return false;
                            }

                            credentials.setIsServerOverridden(isServerOverridden);
                            credentials.setServerAddress(serverAddress);
                            credentials.setServerPort(serverPort);
                            credentials.setUserCancel(false);
                        }
                        synchronized (credentialsLock) {
                            credentialsLock.notify();
                        }
                        return true;
                    }

                    public void onDialogCancelled(DialogActivity dialog)
                    {
                        credentials.setUserCancel(true);
                        synchronized (credentialsLock) {
                            credentialsLock.notify();
                        }
                    }
                }, null);
        try {
            synchronized (credentialsLock) {
                // Wait for the credentials
                credentialsLock.wait();
            }
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
        return credentials;
    }

    /**
     * Get the number of messages belong to editedAccUid
     *
     * @param editedAccUid current edited account Uid
     * @param userName new userName
     * @return count of old messages belong to editedAccUid which are to be purged or -1 if userName entered is invalid
     */
    private int checkPurgedMsgCount(String editedAccUid, String userName)
    {
        if (!TextUtils.isEmpty(userName) && userName.contains("@")) {
            if (!editedAccUid.split(":")[1].equals(userName)) {
                MessageHistoryServiceImpl mhs = MessageHistoryActivator.getMessageHistoryService();
                return mhs.getMessageCountForAccountUuid(editedAccUid);
            }
            else
                return 0;
        }
        else {
            return -1;
        }
    }

    /**
     * Sets the userNameEditable property, which should indicate to the implementations of
     * this interface if the user name could be changed by user or not.
     *
     * @param isUserNameEditable indicates if the user name could be changed by user in the
     * implementation of this interface.
     */
    public void setUserNameEditable(boolean isUserNameEditable)
    {
        this.isUserNameEditable = isUserNameEditable;
    }

    /**
     * Indicates if the user name is currently editable, i.e. could be changed by user or not.
     *
     * @return {@code true</code> if the user name could be changed, <code>false} - otherwise.
     */
    public boolean isUserNameEditable()
    {
        return isUserNameEditable;
    }
}
