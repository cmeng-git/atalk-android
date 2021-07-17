/*
 * Copyright @ 2016 Atlassian Pty Ltd
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */
package org.atalk.impl.neomedia.transform.srtp.crypto;

import timber.log.Timber;

public class OpenSslWrapperLoader
{
    /**
     * The indicator which determines whether OpenSSL (Crypto) library wrapper was loaded.
     */
    private static boolean libraryLoaded = false;

    private static native boolean OpenSSL_Init();

    static {
        try {
            System.loadLibrary("jnopenssl");
            if (OpenSSL_Init()) {
                Timber.i("OpenSSL successfully loaded");
                libraryLoaded = true;
            }
            else {
                Timber.w("OpenSSL init failed");
            }
        } catch (Throwable t) {
            Timber.w("Unable to load jnopenssl: %s", t.getMessage());
        }
    }

    public static boolean isLoaded()
    {
        return libraryLoaded;
    }
}
