package com.akhgupta.easylocation;

import android.location.Location;

import androidx.annotation.NonNull;

import java.util.Objects;

class EasyLocation {
    private final Location location;

    public EasyLocation(Location location) {
        this.location = location;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        EasyLocation that = (EasyLocation) o;
        return Objects.equals(location, that.location);

    }

    @Override
    public int hashCode() {
        return location != null ? location.hashCode() : 0;
    }

    @NonNull
    @Override
    public String toString() {
         return  location.getLatitude() +","+location.getLongitude();
    }
}