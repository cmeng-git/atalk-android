/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.atalk.impl.neomedia.transform.srtp;

/**
 * SRTPPolicy holds the SRTP encryption / authentication policy of a SRTP session.
 *
 * @author Bing SU (nova.su@gmail.com)
 */
public class SrtpPolicy
{
    /**
     * Null Cipher, does not change the content of RTP payload
     */
    public final static int NULL_ENCRYPTION = 0;

    /**
     * Counter Mode AES Cipher, defined in Section 4.1.1, RFC3711
     */
    public final static int AESCM_ENCRYPTION = 1;

    /**
     * Counter Mode TwoFish Cipher
     */
    public final static int TWOFISH_ENCRYPTION = 3;

    /**
     * F8 mode AES Cipher, defined in Section 4.1.2, RFC 3711
     */
    public final static int AESF8_ENCRYPTION = 2;

    /**
     * F8 Mode TwoFish Cipher
     */
    public final static int TWOFISHF8_ENCRYPTION = 4;
    /**
     * Null Authentication, no authentication
     */
    public final static int NULL_AUTHENTICATION = 0;

    /**
     * HAMC SHA1 Authentication, defined in Section 4.2.1, RFC3711
     */
    public final static int HMACSHA1_AUTHENTICATION = 1;

    /**
     * Skein Authentication
     */
    public final static int SKEIN_AUTHENTICATION = 2;

    /**
     * SRTP encryption type
     */
    private int encType;

    /**
     * SRTP encryption key length
     */
    private int encKeyLength;

    /**
     * SRTP authentication type
     */
    private int authType;

    /**
     * SRTP authentication key length
     */
    private int authKeyLength;

    /**
     * SRTP authentication tag length
     */
    private int authTagLength;

    /**
     * SRTP salt key length
     */
    private int saltKeyLength;

    /**
     * Whether send-side replay protection is enabled
     */
    private boolean sendReplayEnabled = true;

    /**
     * Whether receive-side replay protection is enabled
     */
    private boolean receiveReplayEnabled = true;

    /**
     * Construct a SrtpPolicy object based on given parameters.
     * This class acts as a storage class, so all the parameters are passed in
     * through this constructor.
     *
     * @param encType SRTP encryption type
     * @param encKeyLength SRTP encryption key length
     * @param authType SRTP authentication type
     * @param authKeyLength SRTP authentication key length
     * @param authTagLength SRTP authentication tag length
     * @param saltKeyLength SRTP salt key length
     */
    public SrtpPolicy(int encType,
            int encKeyLength,
            int authType,
            int authKeyLength,
            int authTagLength,
            int saltKeyLength)
    {
        this.encType = encType;
        this.encKeyLength = encKeyLength;
        this.authType = authType;
        this.authKeyLength = authKeyLength;
        this.authTagLength = authTagLength;
        this.saltKeyLength = saltKeyLength;
    }

    /**
     * Get the authentication key length
     *
     * @return the authentication key length
     */
    public int getAuthKeyLength()
    {
        return this.authKeyLength;
    }

    /**
     * Set the authentication key length
     *
     * @param authKeyLength the authentication key length
     */
    public void setAuthKeyLength(int authKeyLength)
    {
        this.authKeyLength = authKeyLength;
    }

    /**
     * Get the authentication tag length
     *
     * @return the authentication tag length
     */
    public int getAuthTagLength()
    {
        return this.authTagLength;
    }

    /**
     * Set the authentication tag length
     *
     * @param authTagLength the authentication tag length
     */
    public void setAuthTagLength(int authTagLength)
    {
        this.authTagLength = authTagLength;
    }

    /**
     * Get the authentication type
     *
     * @return the authentication type
     */
    public int getAuthType()
    {
        return this.authType;
    }

    /**
     * Set the authentication type
     *
     * @param authType the authentication type
     */
    public void setAuthType(int authType)
    {
        this.authType = authType;
    }

    /**
     * Get the encryption key length
     *
     * @return the encryption key length
     */
    public int getEncKeyLength()
    {
        return this.encKeyLength;
    }

    /**
     * Set the encryption key length
     *
     * @param encKeyLength the encryption key length
     */
    public void setEncKeyLength(int encKeyLength)
    {
        this.encKeyLength = encKeyLength;
    }

    /**
     * Get the encryption type
     *
     * @return the encryption type
     */
    public int getEncType()
    {
        return this.encType;
    }

    /**
     * Set the encryption type
     *
     * @param encType encryption type
     */
    public void setEncType(int encType)
    {
        this.encType = encType;
    }

    /**
     * Get the salt key length
     *
     * @return the salt key length
     */
    public int getSaltKeyLength()
    {
        return this.saltKeyLength;
    }

    /**
     * Set the salt key length
     *
     * @param keyLength the salt key length
     */
    public void setSaltKeyLength(int keyLength)
    {
        this.saltKeyLength = keyLength;
    }

    /**
     * Set whether send-side RTP replay protection is to be enabled.
     *
     * Turn this off if you need to send identical packets more than once (e.g., retransmission to a peer that
     * does not support the rtx payload.)  <b>Note</b>: Never re-send a packet with a different payload!
     *
     * @param enabled <tt>true</tt> if send-side replay protection is to be enabled; <tt>false</tt> if not.
     */
    public void setSendReplayEnabled(boolean enabled)
    {
        sendReplayEnabled = enabled;
    }

    /**
     * Get whether send-side RTP replay protection is enabled.
     *
     * @see #isSendReplayDisabled
     */
    public boolean isSendReplayEnabled()
    {
        return sendReplayEnabled;
    }

    /**
     * Get whether send-side RTP replay protection is disabled.
     *
     * @see #isSendReplayEnabled
     */
    public boolean isSendReplayDisabled()
    {
        return !sendReplayEnabled;
    }

    /**
     * Set whether receive-side RTP replay protection is to be enabled.
     *
     * Turn this off if you need to be able to receive identical packets more than once (e.g., if you are
     * an RTP translator, with peers that are doing retransmission without using the rtx payload.)
     * <b>Note</b>: You must make sure your packet handling is idempotent!
     *
     * @param enabled <tt>true</tt> if receive-side replay protection is to be enabled; <tt>false</tt> if not.
     */
    public void setReceiveReplayEnabled(boolean enabled)
    {
        receiveReplayEnabled = enabled;
    }

    /**
     * Get whether receive-side RTP replay protection is enabled.
     *
     * @see #isReceiveReplayDisabled
     */
    public boolean isReceiveReplayEnabled()
    {
        return receiveReplayEnabled;
    }

    /**
     * Get whether receive-side RTP replay protection is enabled.
     *
     * @see #isReceiveReplayEnabled
     */
    public boolean isReceiveReplayDisabled()
    {
        return !receiveReplayEnabled;
    }
}
