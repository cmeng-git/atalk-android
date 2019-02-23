/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package net.java.sip.communicator.service.protocol;

import org.atalk.android.gui.chat.ChatMessage;

/**
 * Represents a default implementation of {@link Message} in order to make it easier for
 * implementers to provide complete solutions while focusing on implementation-specific details.
 *
 * @author Lubomir Marinov
 * @author Eng Chong Meng
 */
public abstract class AbstractMessage implements Message
{
    private String mContent;
    private final int mMimeType;
    private final int mEncryption;
    private final String mMessageUID;

    /**
     * The content of this message, in raw bytes according to the encoding.
     */
    private byte[] rawData;

    private final String mSubject;

    /**
     * @param content the text content of the message.
     * @param encType contains both mime and encryption types @see ChatMessage.ENC_TYPE definition
     * @param subject the subject of the message or null for empty.
     */
    protected AbstractMessage(String content, int encType, String subject)
    {
        mMimeType = encType & ChatMessage.MIME_MASK;
        mEncryption = encType & ChatMessage.ENCRYPTION_MASK;
        mSubject = subject;

        setContent(content);
        mMessageUID = createMessageUID();
    }

    /**
     * @param content the text content of the message.
     * @param encType contains both mime and encryption types @see ChatMessage.ENC_TYPE definition
     * @param subject the subject of the message or null for empty.
     * @param messageUID @see net.java.sip.communicator.service.protocol.Message#getMessageUID()
     */
    protected AbstractMessage(String content, int encType, String subject, String messageUID)
    {
        mMimeType = encType & ChatMessage.MIME_MASK;
        mEncryption = encType & ChatMessage.ENCRYPTION_MASK;
        mSubject = subject;

        setContent(content);
        mMessageUID = messageUID == null ? createMessageUID() : messageUID;
    }

    protected String createMessageUID()
    {
        return String.valueOf(System.currentTimeMillis()) + String.valueOf(hashCode());
    }

    /*
     * (non-Javadoc)
     *
     * @see net.java.sip.communicator.service.protocol.Message#getMimeType()
     */
    public int getMimeType()
    {
        return mMimeType;
    }

    /**
     * Returns the content of this message if representable in text form or null if this message
     * does not contain text data.
     * <p>
     * The implementation is final because it caches the raw data of the content.
     * </p>
     *
     * @return a String containing the content of this message or null if the message does not
     * contain data representable in text form.
     */
    public final String getContent()
    {
        return mContent;
    }

    /*
     * (non-Javadoc)
     *
     * @see net.java.sip.communicator.service.protocol.Message#getMimeType()
     */
    public int getEncryptionType()
    {
        return mEncryption;
    }

    /**
     * @return the encType of both Mime and Encryption combined
     */
    public int getEncType()
    {
        return mEncryption | mMimeType;
    }

    /*
     * (non-Javadoc)
     *
     * @see net.java.sip.communicator.service.protocol.Message#getMessageUID()
     */
    public String getMessageUID()
    {
        return mMessageUID;
    }

    /*
     * (non-Javadoc)
     *
     * @see net.java.sip.communicator.service.protocol.Message#getRawData()
     */
    public byte[] getRawData()
    {
        if (rawData == null) {
            String content = getContent();
            rawData = content.getBytes();
        }
        return rawData;
    }

    /*
     * (non-Javadoc)
     *
     * @see net.java.sip.communicator.service.protocol.Message#getSize()
     */
    public int getSize()
    {
        return getRawData().length;
    }

    /*
     * (non-Javadoc)
     *
     * @see net.java.sip.communicator.service.protocol.Message#getSubject()
     */
    public String getSubject()
    {
        return mSubject;
    }

    protected void setContent(String content)
    {
        if (!equals(mContent, content)) {
            mContent = content;
            rawData = null;
        }
    }

    private static boolean equals(String a, String b)
    {
        return (a == null) ? (b == null) : a.equals(b);
    }
}
