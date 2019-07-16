package com.akhgupta.easylocation;

import android.app.Activity;
import android.content.Intent;
import android.location.Location;
import android.os.Bundle;
import androidx.annotation.NonNull;


public abstract class EasyLocationActivity extends Activity implements EasyLocationListener {
    private EasyLocationDelegate easyLocationDelegate;

    protected Location getLastKnownLocation() {
        return easyLocationDelegate.getLastKnownLocation();
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        easyLocationDelegate = new EasyLocationDelegate(this,this);
        easyLocationDelegate.onCreate();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        easyLocationDelegate.onActivityResult(requestCode);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        easyLocationDelegate.onRequestPermissionsResult(requestCode, grantResults);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        easyLocationDelegate.onDestroy();
    }

    protected void requestLocationUpdates(EasyLocationRequest easyLocationRequest) {
        easyLocationDelegate.requestLocationUpdates(easyLocationRequest);
    }

    protected void requestSingleLocationFix(EasyLocationRequest easyLocationRequest) {
        easyLocationDelegate.requestSingleLocationFix(easyLocationRequest);
    }

    protected void stopLocationUpdates() {
        easyLocationDelegate.stopLocationUpdates();
    }
}