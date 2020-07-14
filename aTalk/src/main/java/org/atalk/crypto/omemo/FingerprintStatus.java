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
package org.atalk.crypto.omemo;

import android.content.ContentValues;
import android.database.Cursor;

import org.apache.commons.lang3.StringUtils;
import org.jivesoftware.smackx.omemo.internal.OmemoDevice;
import org.jxmpp.jid.BareJid;
import org.jxmpp.jid.impl.JidCreate;
import org.jxmpp.stringprep.XmppStringprepException;

public class FingerprintStatus implements Comparable<FingerprintStatus>
{
    private static final long DO_NOT_OVERWRITE = -1;
    private Trust trust = Trust.UNTRUSTED;
    private OmemoDevice mDevice;
    private String mFingerPrint;
    private boolean active = false;
    private long lastActivation = DO_NOT_OVERWRITE;

    private FingerprintStatus() {
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        FingerprintStatus that = (FingerprintStatus) o;
        return active == that.active && trust == that.trust;
    }

    @Override
    public int hashCode() {
        int result = trust.hashCode();
        result = 31 * result + (active ? 1 : 0);
        return result;
    }

    public ContentValues toContentValues() {
        final ContentValues contentValues = new ContentValues();
        contentValues.put(SQLiteOmemoStore.TRUST, trust.toString());
        contentValues.put(SQLiteOmemoStore.ACTIVE, active ? 1 : 0);
        if (lastActivation != DO_NOT_OVERWRITE) {
            contentValues.put(SQLiteOmemoStore.LAST_ACTIVATION, lastActivation);
        }
        return contentValues;
    }

    public static FingerprintStatus fromCursor(Cursor cursor) {
        final FingerprintStatus status = new FingerprintStatus();
        try {
            BareJid bareJid = JidCreate.bareFrom(cursor.getString(cursor.getColumnIndex(SQLiteOmemoStore.BARE_JID)));
            int deviceId = cursor.getInt(cursor.getColumnIndex(SQLiteOmemoStore.DEVICE_ID));
            status.mDevice = new OmemoDevice(bareJid, deviceId);
        }
        catch (XmppStringprepException e) {
            e.printStackTrace();
        }
        status.mFingerPrint = cursor.getString(cursor.getColumnIndex(SQLiteOmemoStore.FINGERPRINT));
        if (StringUtils.isEmpty(status.mFingerPrint ))
            return null;

        try {
            String trust = cursor.getString(cursor.getColumnIndex(SQLiteOmemoStore.TRUST));
            if (StringUtils.isEmpty(trust))
                status.trust = Trust.UNDECIDED;
            else
                status.trust = Trust.valueOf(trust);
        } catch(IllegalArgumentException e) {
            status.trust = Trust.UNTRUSTED;
        }

        status.active = cursor.getInt(cursor.getColumnIndex(SQLiteOmemoStore.ACTIVE)) > 0;
        status.lastActivation = cursor.getLong(cursor.getColumnIndex(SQLiteOmemoStore.LAST_ACTIVATION));
        return status;
    }

    public static FingerprintStatus createActiveUndecided() {
        final FingerprintStatus status = new FingerprintStatus();
        status.trust = Trust.UNDECIDED;
        status.active = true;
        status.lastActivation = System.currentTimeMillis();
        return status;
    }

    public static FingerprintStatus createActiveTrusted() {
        final FingerprintStatus status = new FingerprintStatus();
        status.trust = Trust.TRUSTED;
        status.active = true;
        status.lastActivation = System.currentTimeMillis();
        return status;
    }

    public static FingerprintStatus createActiveVerified(boolean x509) {
        final FingerprintStatus status = new FingerprintStatus();
        status.trust = x509 ? Trust.VERIFIED_X509 : Trust.VERIFIED;
        status.active = true;
        return status;
    }

    public static FingerprintStatus createActive(boolean trusted) {
        final FingerprintStatus status = new FingerprintStatus();
        status.trust = trusted ? Trust.TRUSTED : Trust.UNTRUSTED;
        status.active = true;
        return status;
    }

    public boolean isTrustedAndActive() {
        return active && isTrusted();
    }

    public boolean isTrusted() {
        return trust == Trust.TRUSTED || isVerified();
    }

    public boolean isVerified() {
        return trust == Trust.VERIFIED || trust == Trust.VERIFIED_X509;
    }

    public boolean isCompromised() {
        return trust == Trust.COMPROMISED;
    }

    public boolean isActive() {
        return active;
    }

    public FingerprintStatus toActive() {
        FingerprintStatus status = new FingerprintStatus();
        status.trust = trust;
        if (!status.active) {
            status.lastActivation = System.currentTimeMillis();
        }
        status.active = true;
        return status;
    }

    public FingerprintStatus toInactive() {
        FingerprintStatus status = new FingerprintStatus();
        status.trust = trust;
        status.active = false;
        return status;
    }

    public Trust getTrust() {
        return trust;
    }

    public FingerprintStatus toVerified() {
        FingerprintStatus status = new FingerprintStatus();
        status.active = active;
        status.trust = Trust.VERIFIED;
        return status;
    }

    public FingerprintStatus toUntrusted() {
        FingerprintStatus status = new FingerprintStatus();
        status.active = active;
        status.trust = Trust.UNTRUSTED;
        // status.trust = Trust.UNDECIDED; // testing only
        return status;
    }

    public static FingerprintStatus createInactiveVerified() {
        final FingerprintStatus status = new FingerprintStatus();
        status.trust = Trust.VERIFIED;
        status.active = false;
        return status;
    }

    @Override
    public int compareTo(FingerprintStatus o) {
        if (active == o.active) {
            if (lastActivation > o.lastActivation) {
                return -1;
            } else if (lastActivation < o.lastActivation) {
                return 1;
            } else {
                return 0;
            }
        } else if (active){
            return -1;
        } else {
            return 1;
        }
    }

    public long getLastActivation() {
        return lastActivation;
    }

    public OmemoDevice getOmemoDevice() {
        return mDevice;
    }

    public String getFingerPrint() {
        return mFingerPrint;
    }

    public enum Trust {
        COMPROMISED,
        UNDECIDED,
        UNTRUSTED,
        TRUSTED,
        VERIFIED,
        VERIFIED_X509
    }
}
