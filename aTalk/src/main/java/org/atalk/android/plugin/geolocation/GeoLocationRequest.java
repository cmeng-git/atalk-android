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
package org.atalk.android.plugin.geolocation;

import android.os.Parcel;
import android.os.Parcelable;

/**
 * Parcelable  geoLocation request with builder implementation.
 * Replace gms LocationRequest for both gms and fdroid support
 *
 * @author Eng Chong Meng
 */
public class GeoLocationRequest implements Parcelable
{
    final private int mLocationFetchMode;
    final boolean mAddressRequest;
    final long mUpdateMinTime;
    final float mUpdateMinDistance;
    final long mFallBackToLastLocationTime;

    public GeoLocationRequest(
            int locationFetchMode,
            boolean addressRequest,
            long updateMinTime,
            float updateMinDistance,
            long fallBackToLastLocationTime)
    {
        mLocationFetchMode = locationFetchMode;
        mAddressRequest = addressRequest;
        mUpdateMinTime = updateMinTime;
        mUpdateMinDistance = updateMinDistance;
        mFallBackToLastLocationTime = fallBackToLastLocationTime;
    }

    public int getLocationFetchMode()
    {
        return mLocationFetchMode;
    }

    public boolean getAddressRequest()
    {
        return mAddressRequest;
    }

    public Long getLocationUpdateMinTime()
    {
        return mUpdateMinTime;
    }

    public float getLocationUpdateMinDistance()
    {
        return mUpdateMinDistance;
    }

    public long getFallBackToLastLocationTime()
    {
        return mFallBackToLastLocationTime;
    }

    public static final Creator<GeoLocationRequest> CREATOR = new Creator<GeoLocationRequest>()
    {
        @Override
        public GeoLocationRequest createFromParcel(Parcel in)
        {
            return new GeoLocationRequest(
                    /* mLoctionFetchMode = */ in.readInt(),
                    /* mAddressRequest= */ in.readByte() != 0,
                    /* mUpdateMinTime= */ in.readLong(),
                    /* mUpdateMinDistance= */ in.readFloat(),
                    /* mFallBackToLastLocationTime= */ in.readLong()
            );
        }

        // @Override
        public GeoLocationRequest[] newArray(int size)
        {
            return new GeoLocationRequest[size];
        }
    };

    @Override
    public int describeContents()
    {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags)
    {
        dest.writeInt(mLocationFetchMode);
        dest.writeByte((byte) (mAddressRequest ? 1 : 0));
        dest.writeLong(mUpdateMinTime);
        dest.writeFloat(mUpdateMinDistance);
        dest.writeLong(mFallBackToLastLocationTime);
    }

    public static class GeoLocationRequestBuilder
    {
        private int mLocationFetchMode;
        private boolean mAddressRequest = false;
        private long mUpdateMinTime;
        private float mUpdateMinDistance;
        private long mFallBackToLastLocationTime;

        public GeoLocationRequestBuilder setLocationFetchMode(int locationFetchMode)
        {
            mLocationFetchMode = locationFetchMode;
            return this;
        }

        public GeoLocationRequestBuilder setAddressRequest(boolean addressRequest)
        {
            mAddressRequest = addressRequest;
            return this;
        }

        public GeoLocationRequestBuilder setLocationUpdateMinTime(Long minTime)
        {
            mUpdateMinTime = minTime;
            return this;
        }

        public GeoLocationRequestBuilder setLocationUpdateMinDistance(float minDistance)
        {
            mUpdateMinDistance = minDistance;
            return this;
        }

        public GeoLocationRequestBuilder setFallBackToLastLocationTime(long fallBackToLastLocationTime)
        {
            mFallBackToLastLocationTime = fallBackToLastLocationTime;
            return this;
        }

        public GeoLocationRequest build()
        {
            return new GeoLocationRequest(
                    mLocationFetchMode,
                    mAddressRequest,
                    mUpdateMinTime,
                    mUpdateMinDistance,
                    mFallBackToLastLocationTime);
        }
    }
}