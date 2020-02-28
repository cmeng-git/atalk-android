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

import org.jetbrains.annotations.NotNull;

/**
 * Data object for client certificate configuration entries.
 *
 * @author Ingo Bauersachs
 * @author Eng Chong Meng
 */
public class CertificateConfigEntry
{
    /*
     * CERT_NONE for use on android device to denote no client TLS Certificate being selected.
     */
    public static final CertificateConfigEntry CERT_NONE = new CertificateConfigEntry("None");

    /**
     * Default construct with only display defined
     * @param displayName Certificate display name
     */
    public CertificateConfigEntry(String displayName)
    {
        this.displayName = displayName;
    }

    // ------------------------------------------------------------------------
    // Fields
    // ------------------------------------------------------------------------
    private KeyStoreType keyStoreType;
    private String keyStorePassword;
    private String displayName;
    private String alias;
    private String id;
    private String keyStore;
    private boolean savePassword;

    // ------------------------------------------------------------------------
    // Properties
    // ------------------------------------------------------------------------

    /**
     * Sets the key store type.
     *
     * @param keyStoreType the new key store type
     */
    public void setKeyStoreType(KeyStoreType keyStoreType)
    {
        this.keyStoreType = keyStoreType;
    }

    /**
     * Gets the key store type.
     *
     * @return the key store type
     */
    public KeyStoreType getKeyStoreType()
    {
        return keyStoreType;
    }

    /**
     * Sets the key store password.
     *
     * @param keyStorePassword the new key store password
     */
    public void setKeyStorePassword(String keyStorePassword)
    {
        this.keyStorePassword = keyStorePassword;
    }

    /**
     * Gets the key store password.
     *
     * @return the key store password
     */
    public String getKeyStorePassword()
    {
        return keyStorePassword;
    }


    public char[] getKSPassword()
    {
        return (keyStorePassword == null) ? null : keyStorePassword.toCharArray();
    }

    /**
     * Sets the display name.
     *
     * @param displayName the new display name
     */
    public void setDisplayName(String displayName)
    {
        this.displayName = displayName;
    }

    /**
     * Gets the display name.
     *
     * @return the display name
     */
    public String getDisplayName()
    {
        return displayName;
    }

    /**
     * Sets the alias.
     *
     * @param alias the new alias
     */
    public void setAlias(String alias)
    {
        this.alias = alias;
    }

    /**
     * Gets the alias.
     *
     * @return the alias
     */
    public String getAlias()
    {
        return alias;
    }

    /**
     * Sets the id.
     *
     * @param id the new id
     */
    public void setId(String id)
    {
        this.id = id;
    }

    /**
     * Gets the id.
     *
     * @return the id
     */
    public String getId()
    {
        return id;
    }

    /**
     * Sets the key store.
     *
     * @param keyStore the new key store
     */
    public void setKeyStore(String keyStore)
    {
        this.keyStore = keyStore;
    }

    /**
     * Gets the key store.
     *
     * @return the key store
     */
    public String getKeyStore()
    {
        return keyStore;
    }

    /**
     * Sets the save password.
     *
     * @param savePassword the new save password
     */
    public void setSavePassword(boolean savePassword)
    {
        this.savePassword = savePassword;
    }

    /**
     * Checks if is save password.
     *
     * @return true, if is save password
     */
    public boolean isSavePassword()
    {
        return savePassword;
    }

    /**
     * Human readable and uniquely identify the certificate
     * Min is the displayName e.g. 'None' certificate
     *
     * @return String representing the certificate
     */
    @NotNull
    @Override
    public String toString()
    {
        String certName = "";
        if (id != null)
            certName = id + "-";

        certName += displayName;

        if (keyStoreType != null)
            certName += " [" + keyStoreType.getName() + "]";

        return certName;
    }
}
