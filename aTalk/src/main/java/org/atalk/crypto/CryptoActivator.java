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
package org.atalk.crypto;

import org.atalk.crypto.omemo.SQLiteOmemoStore;
import org.jivesoftware.smackx.omemo.OmemoConfiguration;
import org.jivesoftware.smackx.omemo.OmemoStore;
import org.jivesoftware.smackx.omemo.signal.SignalOmemoService;
import org.osgi.framework.BundleActivator;
import org.osgi.framework.BundleContext;

/**
 * Android Crypto Activator which registers <code>Omemo ActionHandler</code> specific to this system.
 *
 * @author Eng Chong Meng
 */
public class CryptoActivator implements BundleActivator {
    @Override
    public void start(BundleContext bundleContext)
            throws Exception {
        setupOmemoConfigStore();
    }

    @Override
    public void stop(BundleContext bundleContext)
            throws Exception {
    }

    /**
     * Init OMEMO configuration setting and Store
     * - Acknowledge OMEMO licensing.
     * - Initialize the OMEMO configuration settings
     * - Setup OMEMO default data storage
     */
    private void setupOmemoConfigStore() {
        SignalOmemoService.acknowledgeLicense();
        SignalOmemoService.setup();

        // Omemo configuration settings
        OmemoConfiguration.setAddOmemoHintBody(true);
        OmemoConfiguration.setRenewOldSignedPreKeys(true);
        OmemoConfiguration.setDeleteStaleDevices(true);

        // IgnoreReadOnlyDevices: if read-only devices should get ignored after a certain amount of unanswered messages.
        // OmemoConfiguration.setIgnoreReadOnlyDevices(true);  // default

        // For testing only
        // OmemoConfiguration.setMaxReadOnlyMessageCount(5);

        // Uncomment to setup to use a file-based persistent storage for OMEMO
        // File omemoStoreDirectory = aTalkApp.getInstance().getFilesDir();
        // OmemoConfiguration.setFileBasedOmemoStoreDefaultPath(omemoStoreDirectory);

        // Alternatively initialize to use SQLiteOmemoStore backend database
        // OmemoStore<IdentityKeyPair, IdentityKey, PreKeyRecord, SignedPreKeyRecord, SessionRecord, SignalProtocolAddress,
        //        ECPublicKey, PreKeyBundle, SessionCipher> omemoStore = new SQLiteOmemoStore();
        // OmemoStore<?, ?, ?, ?, ?, ?, ?, ?, ?> omemoStore = new SQLiteOmemoStore();
        OmemoStore omemoStore = new SQLiteOmemoStore();
        SignalOmemoService.getInstance().setOmemoStoreBackend(omemoStore);
    }
}
