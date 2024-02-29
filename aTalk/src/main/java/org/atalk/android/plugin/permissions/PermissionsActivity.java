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
package org.atalk.android.plugin.permissions;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.PowerManager;
import android.provider.Settings;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContract;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.karumi.dexter.Dexter;
import com.karumi.dexter.PermissionToken;
import com.karumi.dexter.listener.PermissionDeniedResponse;
import com.karumi.dexter.listener.PermissionGrantedResponse;
import com.karumi.dexter.listener.PermissionRequest;
import com.karumi.dexter.listener.PermissionRequestErrorListener;
import com.karumi.dexter.listener.multi.CompositeMultiplePermissionsListener;
import com.karumi.dexter.listener.multi.DialogOnAnyDeniedMultiplePermissionsListener;
import com.karumi.dexter.listener.multi.MultiplePermissionsListener;
import com.karumi.dexter.listener.multi.SnackbarOnAnyDeniedMultiplePermissionsListener;
import com.karumi.dexter.listener.single.CompositePermissionListener;
import com.karumi.dexter.listener.single.DialogOnDeniedPermissionListener;
import com.karumi.dexter.listener.single.PermissionListener;

import org.atalk.android.BaseActivity;
import org.atalk.android.R;
import org.atalk.android.aTalkApp;
import org.atalk.android.databinding.PermissionsActivityBinding;
import org.atalk.android.gui.Splash;
import org.atalk.service.SystemEventReceiver;

import java.util.LinkedList;
import java.util.List;

import timber.log.Timber;

/**
 * Sample activity showing the permission request process with Dexter.
 */
public class PermissionsActivity extends BaseActivity {
    private PermissionsActivityBinding mBinding;

    private MultiplePermissionsListener allPermissionsListener;
    private MultiplePermissionsListener dialogMultiplePermissionsListener;
    private PermissionListener cameraPermissionListener;
    private PermissionListener contactsPermissionListener;
    private PermissionListener locationPermissionListener;
    private PermissionListener audioPermissionListener;
    private PermissionListener phonePermissionListener;
    private PermissionListener storagePermissionListener;
    private PermissionListener notificationsPermissionListener;
    private PermissionRequestErrorListener errorListener;

    private ActivityResultLauncher<Void> mBatteryOptimization;

    protected static List<PermissionGrantedResponse> grantedPermissionResponses = new LinkedList<>();
    protected static List<PermissionDeniedResponse> deniedPermissionResponses = new LinkedList<>();

    protected static List<String> permissionList = new LinkedList<>();

    static {
        permissionList.add(Manifest.permission.ACCESS_FINE_LOCATION);
        permissionList.add(Manifest.permission.CAMERA);
        permissionList.add(Manifest.permission.READ_CONTACTS);
        permissionList.add(Manifest.permission.READ_PHONE_STATE);
        permissionList.add(Manifest.permission.RECORD_AUDIO);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissionList.add(Manifest.permission.POST_NOTIFICATIONS);
        }
        else if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            permissionList.add(Manifest.permission.WRITE_EXTERNAL_STORAGE);
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Always request permission on first apk launch for android.M
        if (aTalkApp.permissionFirstRequest) {

            // see if we should show the splash screen and wait for it to complete before continue
            if (Splash.isFirstRun()) {
                Intent intent = new Intent(this, Splash.class);
                startActivity(intent);
            }

            mBinding = PermissionsActivityBinding.inflate(getLayoutInflater());
            View view = mBinding.getRoot();
            setContentView(view);
            initView();

            Timber.i("Launching dynamic permission request for aTalk.");
            aTalkApp.permissionFirstRequest = false;

            // Request user to add aTalk to BatteryOptimization whitelist
            // Otherwise, aTalk will be put to sleep on system doze-standby
            mBatteryOptimization = requestBatteryOptimization();
            boolean showBatteryOptimizationDialog = openBatteryOptimizationDialogIfNeeded();

            createPermissionListeners();
            boolean permissionRequest = getPackagePermissionsStatus();
            permissionsStatusUpdate();

            if ((!permissionRequest) && !showBatteryOptimizationDialog) {
                startLauncher();
            }
        }
        else {
            startLauncher();
        }
    }

    private void startLauncher() {
        Class<?> activityClass = aTalkApp.getHomeScreenActivityClass();
        Intent i = new Intent(this, activityClass);
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        i.putExtra(SystemEventReceiver.AUTO_START_ONBOOT, false);
        startActivity(i);
        finish();
    }

    private void initView() {
        Button button_storage = mBinding.storagePermissionButton;
        // handler for WRITE_EXTERNAL_STORAGE pending android API
        if ((Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) ||
                ActivityCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED) {
            mBinding.storagePermissionFeedback.setText(R.string.permission_granted_feedback);
            button_storage.setEnabled(false);
        }
        else {
            button_storage.setEnabled(true);
            button_storage.setOnClickListener(v -> onStoragePermissionButtonClicked());
        }

        // POST_NOTIFICATIONS is only valid for API-33 (TIRAMISU)
        TextView notificationsPermissionFeedbackView = mBinding.notificationsPermissionFeedback;
        Button button_notifications = mBinding.notificationsPermissionButton;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            notificationsPermissionFeedbackView.setVisibility(View.VISIBLE);
            button_notifications.setVisibility(View.VISIBLE);
            button_notifications.setOnClickListener(v -> onNotificationsPermissionButtonClicked());
        }
        else {
            notificationsPermissionFeedbackView.setVisibility(View.GONE);
            button_notifications.setVisibility(View.GONE);
        }

        mBinding.cameraPermissionButton.setOnClickListener(v -> onCameraPermissionButtonClicked());
        mBinding.contactsPermissionButton.setOnClickListener(v -> onContactsPermissionButtonClicked());
        mBinding.locationPermissionButton.setOnClickListener(v -> onLocationPermissionButtonClicked());
        mBinding.audioPermissionButton.setOnClickListener(v -> onAudioPermissionButtonClicked());
        mBinding.phonePermissionButton.setOnClickListener(v -> onPhonePermissionButtonClicked());

        mBinding.allPermissionsButton.setOnClickListener(v -> onAllPermissionsButtonClicked());
        mBinding.appInfoPermissionsButton.setOnClickListener(v -> onInfoButtonClicked());
        mBinding.buttonDone.setOnClickListener(v -> onDoneButtonClicked());
    }

    public void onDoneButtonClicked() {
        startLauncher();
    }

    @Override
    public void onBackPressed() {
        super.onBackPressed();
        startLauncher();
    }

    public void onAllPermissionsCheck() {
        Dexter.withContext(this)
                .withPermissions(permissionList)
                .withListener(allPermissionsListener)
                .withErrorListener(errorListener)
                .check();
    }

    public void onAllPermissionsButtonClicked() {
        grantedPermissionResponses.clear();
        deniedPermissionResponses.clear();

        Dexter.withContext(this)
                .withPermissions(permissionList)
                .withListener(dialogMultiplePermissionsListener)
                .withErrorListener(errorListener)
                .check();
    }

    public void onAudioPermissionButtonClicked() {
        Dexter.withContext(this)
                .withPermission(Manifest.permission.RECORD_AUDIO)
                .withListener(audioPermissionListener)
                .withErrorListener(errorListener)
                .check();
    }

    public void onCameraPermissionButtonClicked() {
        Dexter.withContext(this)
                .withPermission(Manifest.permission.CAMERA)
                .withListener(cameraPermissionListener)
                .withErrorListener(errorListener)
                .check();
    }

    public void onContactsPermissionButtonClicked() {
        Dexter.withContext(this)
                .withPermission(Manifest.permission.READ_CONTACTS)
                .withListener(contactsPermissionListener)
                .withErrorListener(errorListener)
                .check();
    }

    public void onLocationPermissionButtonClicked() {
        Dexter.withContext(this)
                .withPermission(Manifest.permission.ACCESS_FINE_LOCATION)
                .withListener(locationPermissionListener)
                .withErrorListener(errorListener)
                .check();
    }

    @RequiresApi(api = Build.VERSION_CODES.TIRAMISU)
    public void onNotificationsPermissionButtonClicked() {
        Dexter.withContext(this)
                .withPermission(Manifest.permission.POST_NOTIFICATIONS)
                .withListener(notificationsPermissionListener)
                .withErrorListener(errorListener)
                .check();
    }

    public void onPhonePermissionButtonClicked() {
        Dexter.withContext(this)
                .withPermission(Manifest.permission.READ_PHONE_STATE)
                .withListener(phonePermissionListener)
                .withErrorListener(errorListener)
                .check();
    }

    public void onStoragePermissionButtonClicked() {
        Dexter.withContext(this)
                .withPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE)
                .withListener(storagePermissionListener)
                .withErrorListener(errorListener)
                .check();
    }

    public void onInfoButtonClicked() {
        Intent myAppSettings = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                Uri.parse("package:" + this.getPackageName()));
        myAppSettings.addCategory(Intent.CATEGORY_DEFAULT);
        myAppSettings.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(myAppSettings);
    }

    public void showPermissionRationale(final PermissionToken token) {
        new AlertDialog.Builder(this).setTitle(R.string.permission_rationale_title)
                .setMessage(R.string.permission_rationale_message)
                .setNegativeButton(android.R.string.cancel, (dialog, which) -> {
                    dialog.dismiss();
                    token.cancelPermissionRequest();
                })
                .setPositiveButton(android.R.string.ok, (dialog, which) -> {
                    dialog.dismiss();
                    token.continuePermissionRequest();
                })
                .setOnDismissListener(dialog -> token.cancelPermissionRequest())
                .show();
    }

    /**
     * Retrieve the package current default permissions status on create;
     * only if both the arrays are empty. Non-empty -> orientation change
     */
    private boolean getPackagePermissionsStatus() {
        if (grantedPermissionResponses.isEmpty() && deniedPermissionResponses.isEmpty()) {
            PackageManager pm = getPackageManager();
            try {
                PackageInfo packageInfo = pm.getPackageInfo(this.getPackageName(), PackageManager.GET_PERMISSIONS);

                // Get Permissions
                String[] requestedPermissions = packageInfo.requestedPermissions;
                if (requestedPermissions != null) {
                    for (String requestedPermission : requestedPermissions) {
                        if (getFeedbackViewForPermission(requestedPermission) == null)
                            continue;

                        PermissionRequest pr = new PermissionRequest(requestedPermission);
                        // denied
                        if (ActivityCompat.shouldShowRequestPermissionRationale(this, requestedPermission)) {
                            deniedPermissionResponses.add(new PermissionDeniedResponse(pr, false));
                        }
                        else {
                            // allowed
                            if (ActivityCompat.checkSelfPermission(this,
                                    requestedPermission) == PackageManager.PERMISSION_GRANTED) {
                                grantedPermissionResponses.add(new PermissionGrantedResponse(pr));
                            }
                            // set to never ask again
                            else {
                                deniedPermissionResponses.add(new PermissionDeniedResponse(pr, true));
                            }
                        }
                    }
                }
            } catch (PackageManager.NameNotFoundException e) {
                e.printStackTrace();
            }
        }
        // Proceed to request user for permissions if not all are permanently denied
        for (PermissionDeniedResponse response : deniedPermissionResponses) {
            if (!response.isPermanentlyDenied())
                return true;
        }
        /*
         * It seems that some android devices have init all requested permissions to permanently denied states
         * i.e. incorrect return value for: ActivityCompat.shouldShowRequestPermissionRationale == false
         * Must prompt user if < 3 permission has been granted to aTalk - will not work in almost cases;
         *
         * Do not disturb user, if he has chosen partially granted the permissions.
         */
        return grantedPermissionResponses.size() < 3;
    }

    /**
     * Update the permissions status with the default application permissions on entry
     */
    private void permissionsStatusUpdate() {
        // if (grantedPermissionResponses.isEmpty() && deniedPermissionResponses
        for (PermissionGrantedResponse response : grantedPermissionResponses) {
            showPermissionGranted(response.getPermissionName());
        }
        for (PermissionDeniedResponse response : deniedPermissionResponses) {
            showPermissionDenied(response.getPermissionName(), response.isPermanentlyDenied());
        }
    }

    /**
     * Update the granted permissions for the package
     *
     * @param permission permission view to be updated
     */
    public void showPermissionGranted(String permission) {
        TextView feedbackView = getFeedbackViewForPermission(permission);
        if (feedbackView != null) {
            feedbackView.setText(R.string.permission_granted_feedback);
            feedbackView.setTextColor(ContextCompat.getColor(this, R.color.permission_granted));
        }
    }

    /**
     * Update the denied permissions for the package
     *
     * @param permission permission view to be updated
     */
    public void showPermissionDenied(String permission, boolean isPermanentlyDenied) {
        TextView feedbackView = getFeedbackViewForPermission(permission);
        if (feedbackView != null) {
            feedbackView.setText(isPermanentlyDenied
                    ? R.string.permission_permanently_denied_feedback : R.string.permission_denied_feedback);
            feedbackView.setTextColor(ContextCompat.getColor(this, R.color.permission_denied));
        }
    }

    /**
     * Initialize all the permission listener required actions
     */
    private void createPermissionListeners() {
        PermissionListener dialogOnDeniedPermissionListener;
        PermissionListener feedbackViewPermissionListener = new AppPermissionListener(this);
        MultiplePermissionsListener feedbackViewMultiplePermissionListener = new MultiplePermissionListener(this);

        View contentView = findViewById(android.R.id.content);
        allPermissionsListener = new CompositeMultiplePermissionsListener(feedbackViewMultiplePermissionListener,
                SnackbarOnAnyDeniedMultiplePermissionsListener.Builder
                        .with(contentView, R.string.all_permissions_denied_feedback)
                        .withOpenSettingsButton(R.string.permission_rationale_settings_button_text)
                        .build());

        DialogOnAnyDeniedMultiplePermissionsListener dialogOnAnyDeniedPermissionListener
                = DialogOnAnyDeniedMultiplePermissionsListener.Builder
                .withContext(this)
                .withTitle(R.string.all_permission_denied_dialog_title)
                .withMessage(R.string.all_permissions_denied_feedback)
                .withButtonText(android.R.string.ok)
                .withIcon(R.drawable.ic_icon)
                .build();
        dialogMultiplePermissionsListener = new CompositeMultiplePermissionsListener(
                feedbackViewMultiplePermissionListener, dialogOnAnyDeniedPermissionListener);

        dialogOnDeniedPermissionListener = DialogOnDeniedPermissionListener.Builder
                .withContext(this)
                .withTitle(R.string.audio_permission_denied_dialog_title)
                .withMessage(R.string.audio_permission_denied_feedback)
                .withButtonText(android.R.string.ok)
                .withIcon(R.drawable.ic_icon)
                .build();
        audioPermissionListener = new CompositePermissionListener(feedbackViewPermissionListener,
                dialogOnDeniedPermissionListener);

        dialogOnDeniedPermissionListener = DialogOnDeniedPermissionListener.Builder
                .withContext(this)
                .withTitle(R.string.camera_permission_denied_dialog_title)
                .withMessage(R.string.camera_permission_denied_feedback)
                .withButtonText(android.R.string.ok)
                .withIcon(R.drawable.ic_icon)
                .build();
        cameraPermissionListener = new CompositePermissionListener(feedbackViewPermissionListener,
                dialogOnDeniedPermissionListener);

        dialogOnDeniedPermissionListener = DialogOnDeniedPermissionListener.Builder
                .withContext(this)
                .withTitle(R.string.contacts_permission_denied_dialog_title)
                .withMessage(R.string.contacts_permission_denied_feedback)
                .withButtonText(android.R.string.ok)
                .withIcon(R.drawable.ic_icon)
                .build();
        contactsPermissionListener = new CompositePermissionListener(feedbackViewPermissionListener,
                dialogOnDeniedPermissionListener);

        dialogOnDeniedPermissionListener = DialogOnDeniedPermissionListener.Builder
                .withContext(this)
                .withTitle(R.string.location_permission_denied_dialog_title)
                .withMessage(R.string.location_permission_denied_feedback)
                .withButtonText(android.R.string.ok)
                .withIcon(R.drawable.ic_icon)
                .build();
        locationPermissionListener = new CompositePermissionListener(feedbackViewPermissionListener,
                dialogOnDeniedPermissionListener);

        dialogOnDeniedPermissionListener = DialogOnDeniedPermissionListener.Builder
                .withContext(this)
                .withTitle(R.string.notifications_permission_denied_dialog_title)
                .withMessage(R.string.notifications_permission_denied_feedback)
                .withButtonText(android.R.string.ok)
                .withIcon(R.drawable.ic_icon)
                .build();
        notificationsPermissionListener = new CompositePermissionListener(feedbackViewPermissionListener,
                dialogOnDeniedPermissionListener);

        dialogOnDeniedPermissionListener = DialogOnDeniedPermissionListener.Builder
                .withContext(this)
                .withTitle(R.string.phone_permission_denied_dialog_title)
                .withMessage(R.string.phone_permission_denied_feedback)
                .withButtonText(android.R.string.ok)
                .withIcon(R.drawable.ic_icon)
                .build();
        phonePermissionListener = new CompositePermissionListener(feedbackViewPermissionListener,
                dialogOnDeniedPermissionListener);

        dialogOnDeniedPermissionListener = DialogOnDeniedPermissionListener.Builder
                .withContext(this)
                .withTitle(R.string.storage_permission_denied_dialog_title)
                .withMessage(R.string.storage_permission_denied_feedback)
                .withButtonText(android.R.string.ok)
                .withIcon(R.drawable.ic_icon)
                .build();
        storagePermissionListener = new CompositePermissionListener(feedbackViewPermissionListener,
                dialogOnDeniedPermissionListener);

        errorListener = new PermissionsErrorListener();
    }

    /**
     * Get the view of the permission for update, null if view does not exist
     *
     * @param name permission name
     *
     * @return the textView for the request permission
     */
    private TextView getFeedbackViewForPermission(String name) {
        TextView feedbackView;

        switch (name) {
            case Manifest.permission.ACCESS_FINE_LOCATION:
                feedbackView = mBinding.locationPermissionFeedback;
                break;
            case Manifest.permission.CAMERA:
                feedbackView = mBinding.cameraPermissionFeedback;
                break;
            case Manifest.permission.POST_NOTIFICATIONS:
                feedbackView = mBinding.notificationsPermissionFeedback;
                break;
            case Manifest.permission.READ_CONTACTS:
                feedbackView = mBinding.contactsPermissionFeedback;
                break;
            case Manifest.permission.READ_PHONE_STATE:
                feedbackView = mBinding.phonePermissionFeedback;
                break;
            case Manifest.permission.RECORD_AUDIO:
                feedbackView = mBinding.audioPermissionFeedback;
                break;
            case Manifest.permission.WRITE_EXTERNAL_STORAGE:
                feedbackView = mBinding.storagePermissionFeedback;
                break;
            default:
                feedbackView = null;
        }
        return feedbackView;
    }

    /**********************************************************************************************
     * Android Battery Usage Optimization Request; Will only be called if >= Build.VERSION_CODES.M
     ***********************************************************************************************/
    private boolean openBatteryOptimizationDialogIfNeeded() {
        // Will always request for battery optimization disable for aTalk on every aTalk new launch, if not disabled
        if (isOptimizingBattery()) {
            AlertDialog.Builder builder = new AlertDialog.Builder(this);
            builder.setTitle(R.string.battery_optimizations);
            builder.setMessage(R.string.battery_optimizations_dialog);

            builder.setPositiveButton(R.string.next, (dialog, which) -> {
                dialog.dismiss();
                mBatteryOptimization.launch(null);
            });

            AlertDialog dialog = builder.create();
            dialog.setCanceledOnTouchOutside(false);
            dialog.show();
            return true;
        }
        else {
            return false;
        }
    }

    protected boolean isOptimizingBattery() {
        final PowerManager pm = (PowerManager) getSystemService(POWER_SERVICE);
        return (pm != null) && !pm.isIgnoringBatteryOptimizations(getPackageName());
    }

    /**
     * GetBatteryOptimization class ActivityResultContract implementation.
     */
    @SuppressLint("BatteryLife")
    public class GetBatteryOptimization extends ActivityResultContract<Void, Boolean> {
        @NonNull
        @Override
        public Intent createIntent(@NonNull Context context, @Nullable Void input) {
            Intent intent = new Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS);
            Uri uri = Uri.parse("package:" + getPackageName());
            intent.setData(uri);
            return intent;
        }

        @Override
        public Boolean parseResult(int resultCode, @Nullable Intent result) {
            return (resultCode == AppCompatActivity.RESULT_OK);
        }
    }

    /**
     * Return success == true if disable battery optimization for aTalk is allowed
     */
    private ActivityResultLauncher<Void> requestBatteryOptimization() {
        return registerForActivityResult(new GetBatteryOptimization(), success -> {
            if (!success) {
                aTalkApp.showToastMessage(R.string.battery_optimization_on);
            }
        });
    }
}
