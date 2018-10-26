package com.akhgupta.easylocation;


import com.google.android.gms.location.LocationRequest;

public class EasyLocationRequest {
     final LocationRequest locationRequest;
     final boolean addressRequest;
     final String locationSettingsDialogTitle;
     final String locationSettingsDialogMessage;
     final String locationSettingsDialogPositiveButtonText;
     final String locationSettingsDialogNegativeButtonText;
     final String locationPermissionDialogTitle;
     final String locationPermissionDialogMessage;
     final String locationPermissionDialogPositiveButtonText;
     final String locationPermissionDialogNegativeButtonText;
     final long fallBackToLastLocationTime;

    public EasyLocationRequest(
            LocationRequest locationRequest,
            boolean addressRequest,
            String locationSettingsDialogTitle,
            String locationSettingsDialogMessage,
            String locationSettingsDialogPositiveButtonText,
            String locationSettingsDialogNegativeButtonText,
            String locationPermissionDialogTitle,
            String locationPermissionDialogMessage,
            String locationPermissionDialogPositiveButtonText,
            String locationPermissionDialogNegativeButtonText,
            long fallBackToLastLocationTime) {
        this.locationRequest = locationRequest;
        this.addressRequest = addressRequest;
        this.locationSettingsDialogTitle = locationSettingsDialogTitle;
        this.locationSettingsDialogMessage = locationSettingsDialogMessage;
        this.locationSettingsDialogPositiveButtonText = locationSettingsDialogPositiveButtonText;
        this.locationSettingsDialogNegativeButtonText = locationSettingsDialogNegativeButtonText;
        this.locationPermissionDialogTitle = locationPermissionDialogTitle;
        this.locationPermissionDialogMessage = locationPermissionDialogMessage;
        this.locationPermissionDialogPositiveButtonText
                = locationPermissionDialogPositiveButtonText;
        this.locationPermissionDialogNegativeButtonText
                = locationPermissionDialogNegativeButtonText;
        this.fallBackToLastLocationTime = fallBackToLastLocationTime;
    }
}