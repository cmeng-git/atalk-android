package com.akhgupta.easylocation;

import com.google.android.gms.location.LocationRequest;

public class EasyLocationRequestBuilder {
    private LocationRequest locationRequest;
    private boolean addressRequest = false;
    private String locationSettingsDialogTitle;
    private String locationSettingsDialogMessage;
    private String locationSettingsDialogPositiveButtonText;
    private String locationSettingsDialogNegativeButtonText;
    private String locationPermissionDialogTitle;
    private String locationPermissionDialogMessage;
    private String locationPermissionDialogPositiveButtonText;
    private String locationPermissionDialogNegativeButtonText;
    private long fallBackToLastLocationTime;

    public EasyLocationRequestBuilder setLocationRequest(LocationRequest locationRequest) {
        this.locationRequest = locationRequest;
        return this;
    }

    public EasyLocationRequestBuilder setAddressRequest(boolean addressRequest) {
        this.addressRequest = addressRequest;
        return this;
    }

    public EasyLocationRequestBuilder
    setLocationSettingsDialogTitle(String locationSettingsDialogTitle) {
        this.locationSettingsDialogTitle = locationSettingsDialogTitle;
        return this;
    }

    public EasyLocationRequestBuilder
    setLocationSettingsDialogMessage(String locationSettingsDialogMessage) {
        this.locationSettingsDialogMessage = locationSettingsDialogMessage;
        return this;
    }

    public EasyLocationRequestBuilder
    setLocationSettingsDialogPositiveButtonText(String locationSettingsDialogPositiveButtonText) {
        this.locationSettingsDialogPositiveButtonText = locationSettingsDialogPositiveButtonText;
        return this;
    }

    public EasyLocationRequestBuilder
    setLocationSettingsDialogNegativeButtonText(String locationSettingsDialogNegativeButtonText) {
        this.locationSettingsDialogNegativeButtonText = locationSettingsDialogNegativeButtonText;
        return this;
    }

    public EasyLocationRequestBuilder
    setLocationPermissionDialogTitle(String locationPermissionDialogTitle) {
        this.locationPermissionDialogTitle = locationPermissionDialogTitle;
        return this;
    }

    public EasyLocationRequestBuilder setLocationPermissionDialogMessage(
            String locationPermissionDialogMessage) {
        this.locationPermissionDialogMessage = locationPermissionDialogMessage;
        return this;
    }

    public EasyLocationRequestBuilder setLocationPermissionDialogPositiveButtonText(
            String locationPermissionDialogPositiveButtonText) {
        this.locationPermissionDialogPositiveButtonText
                = locationPermissionDialogPositiveButtonText;
        return this;
    }

    public EasyLocationRequestBuilder setLocationPermissionDialogNegativeButtonText(
            String locationPermissionDialogNegativeButtonText) {
        this.locationPermissionDialogNegativeButtonText
                = locationPermissionDialogNegativeButtonText;
        return this;
    }

    public EasyLocationRequestBuilder
    setFallBackToLastLocationTime(long fallBackToLastLocationTime) {
        this.fallBackToLastLocationTime = fallBackToLastLocationTime;
        return this;
    }

    public EasyLocationRequest build() {
        return new EasyLocationRequest(
                locationRequest,
                addressRequest,
                locationSettingsDialogTitle,
                locationSettingsDialogMessage,
                locationSettingsDialogPositiveButtonText,
                locationSettingsDialogNegativeButtonText,
                locationPermissionDialogTitle,
                locationPermissionDialogMessage,
                locationPermissionDialogPositiveButtonText,
                locationPermissionDialogNegativeButtonText,
                fallBackToLastLocationTime);
    }
}