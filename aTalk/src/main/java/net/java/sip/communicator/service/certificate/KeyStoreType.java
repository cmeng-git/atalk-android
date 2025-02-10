/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package net.java.sip.communicator.service.certificate;

import androidx.annotation.NonNull;

/**
 * Data object for KeyStore configurations. Primarily used during adding/
 * editing client certificate configurations.
 *
 * @author Ingo Bauersachs
 */
public class KeyStoreType
{
    private final String name;
    private final String[] fileExtensions;
    private final boolean hasKeyStorePassword;

    /**
     * Creates a new instance of this class.
     * @param name the display name of the keystore type.
     * @param fileExtensions known file name extensions (including the dot)
     * @param hasKeyStorePassword KeyStore has password
     */
    public KeyStoreType(String name, String[] fileExtensions,
        boolean hasKeyStorePassword)
    {
        this.name = name;
        this.fileExtensions = fileExtensions;
        this.hasKeyStorePassword = hasKeyStorePassword;
    }

    @NonNull
    @Override
    public String toString()
    {
        return name;
    }

    /**
     * Gets the display name.
     * @return the display name.
     */
    public String getName()
    {
        return name;
    }

    /**
     * Gets the known file name extensions.
     * @return Known file name extensions (including the dot).
     */
    public String[] getFileExtensions()
    {
        return fileExtensions;
    }

    /**
     * Flag that indicates if the keystore supports passwords.
     * @return <code>true</code> if the keystore supports passwords, <code>false</code>
     *         otherwise.
     */
    public boolean hasKeyStorePassword()
    {
        return hasKeyStorePassword;
    }
}
