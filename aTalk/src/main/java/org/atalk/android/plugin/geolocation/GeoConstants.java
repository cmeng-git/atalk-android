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

/**
 * GeoLocation application constant definitions.
 *
 * @author Eng Chong Meng
 */
public class GeoConstants
{
    public static final int ZERO_FIX = 0; // user defined location to show on map view (for fdroid release)
    public static final int SINGLE_FIX = 1; // Location provider updated location to show on map view
    public static final int FOLLOW_ME_FIX = 2; // Location providers updated location with user on motion
    public static final String ACTION_LOCATION_FETCH_START = "location.fetch.start";
    public static final String ACTION_LOCATION_FETCH_STOP = "location.fetch.stop";
    public static final String INTENT_LOCATION_RECEIVED = "intent.location.received";
    public static final String INTENT_NO_LOCATION_RECEIVED = "intent.no.location.received";
}
