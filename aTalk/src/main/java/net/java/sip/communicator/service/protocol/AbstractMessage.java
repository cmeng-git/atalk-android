/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package net.java.sip.communicator.service.protocol;

import java.util.Objects;

import net.java.sip.communicator.service.filehistory.FileRecord;

import org.atalk.android.gui.chat.ChatMessage;

/**
 * Represents a default implementation of {@link IMessage} in order to make it easier for
 * implementers to provide complete solutions while focusing on implementation-specific details.
 *
 * @author Lubomir Marinov
 * @author Eng Chong Meng
 */
public abstract class AbstractMessage implements IMessage {
    private String mContent;
    private final int mEncType;
    private final int mEncryption;
    private final int mMimeType;
    private final boolean mRemoteOnly;
    private final boolean isCarbon;
    private final boolean isMessageOob;

    private final int mXferStatus;
    private int mReceiptStatus;
    private String mMessageUID;
    private String mServerMessageId;
    private String mRemoteMessageId;

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
    protected AbstractMessage(String content, int encType, String subject, String messageUID) {
        this(content, encType, subject, messageUID, FileRecord.STATUS_UNKNOWN,
                ChatMessage.MESSAGE_DELIVERY_NONE, null, null);
    }

    /**
     * @param content the text content of the message.
     * @param encType contains both flags, mime and encryption types @see ChatMessage.ENC_TYPE definition and other flags
     * @param subject the subject of the message or null for empty.
     * @param messageUID @see net.java.sip.communicator.service.protocol.IMessage#getMessageUID()
     */
    protected AbstractMessage(String content, int encType, String subject, String messageUID, int xferStatus,
            int receiptStatus, String serverMessageId, String remoteMessageId) {
        mEncType = encType;
        mEncryption = encType & ENCRYPTION_MASK;
        mMimeType = encType & ENCODE_MIME_MASK;
        mRemoteOnly = IMessage.FLAG_REMOTE_ONLY == (encType & FLAG_REMOTE_ONLY);
        isCarbon = IMessage.FLAG_IS_CARBON == (encType & FLAG_IS_CARBON);
        isMessageOob = IMessage.FLAG_MSG_OOB == (encType & FLAG_MSG_OOB);
        mSubject = subject;

        setContent(content);
        mMessageUID = messageUID == null ? createMessageUID() : messageUID;

        mXferStatus = xferStatus;
        mReceiptStatus = receiptStatus;
        mServerMessageId = serverMessageId;
        mRemoteMessageId = remoteMessageId;
    }

    private String createMessageUID() {
        return System.currentTimeMillis() + String.valueOf(hashCode());
    }

    /**
     * Returns the content of this message if representable in text form or null if this message
     * does not contain text data.
     * The implementation is final because it caches the raw data of the content.
     *
     * @return a String containing the content of this message or null if the message does not
     * contain data representable in text form.
     */
    @Override
    public final String getContent() {
        return mContent;
    }

    /**
     * @see net.java.sip.communicator.service.protocol.IMessage#getMimeType()
     */
    @Override
    public int getMimeType() {
        return mMimeType;
    }

    /*
     * return the Encryption Type for the message
     */
    @Override
    public int getEncryptionType() {
        return mEncryption;
    }

    /**
     * @return the encType info describing the message type
     */
    @Override
    public int getEncType() {
        return mEncType;
    }

    /**
     * @return the file transfer status for HTTP File Download message
     */
    @Override
    public int getXferStatus() {
        return mXferStatus;
    }

    /**
     * @return the Encryption Type for the message
     */
    @Override
    public int getReceiptStatus() {
        return mReceiptStatus;
    }

    @Override
    public void setReceiptStatus(int status) {
        mReceiptStatus = status;
    }

    /**
     * Returns the server message Id of the message sent - for tracking delivery receipt
     *
     * @return the server message Id of the message sent.
     */
    @Override
    public String getServerMsgId() {
        return mServerMessageId;
    }

    @Override
    public void setServerMsgId(String serverMsgId) {
        mServerMessageId = serverMsgId;
    }

    /**
     * Returns the remote message Id of the message received - for tracking delivery receipt
     *
     * @return the remote message Id of the message received.
     */
    @Override
    public String getRemoteMsgId() {
        return mRemoteMessageId;
    }

    @Override
    public void setRemoteMsgId(String remoteMsgId) {
        mRemoteMessageId = remoteMsgId;
    }

    @Override
    public boolean isRemoteOnly() {
        return mRemoteOnly;
    }

    @Override
    public boolean isCarbon() {
        return isCarbon;
    }

    @Override
    public boolean isMessageOob() {
        return isMessageOob;
    }

    /*
     * (non-Javadoc)
     *
     * @see net.java.sip.communicator.service.protocol.IMessage#getMessageUID()
     */
    public String getMessageUID() {
        return mMessageUID;
    }

    public void setMessageUID(String msgUid) {
        mMessageUID = msgUid;
    }

    /*
     * (non-Javadoc)
     *
     * @see net.java.sip.communicator.service.protocol.IMessage#getRawData()
     */
    public byte[] getRawData() {
        if (rawData == null) {
            String content = getContent();
            rawData = content.getBytes();
        }
        return rawData;
    }

    /*
     * (non-Javadoc)
     *
     * @see net.java.sip.communicator.service.protocol.IMessage#getSize()
     */
    public int getSize() {
        return rawData.length;
    }

    /*
     * (non-Javadoc)
     *
     * @see net.java.sip.communicator.service.protocol.IMessage#getSubject()
     */
    public String getSubject() {
        return mSubject;
    }

    protected void setContent(String content) {
        if (!equals(mContent, content)) {
            mContent = content;
            rawData = null;
        }
    }

    private static boolean equals(String a, String b) {
        return Objects.equals(a, b);
    }
}
