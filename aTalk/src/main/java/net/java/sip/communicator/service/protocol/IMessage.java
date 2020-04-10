/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package net.java.sip.communicator.service.protocol;

/**
 * Used to access the content of instant messages that are sent or received via the instant
 * messaging operation set.
 * <p>
 * This class provides easy access to the content and key fields of an instant IMessage. Content
 * types are represented using MIME types. [IETF RFC 2045-2048].
 * <p>
 * Messages are created through the <tt>OperationSetBasicInstanceMessaging</tt> operation set.
 * <p>
 * All messages have message ids that allow the underlying implementation to notify the user of
 * their successful delivery.
 *
 * @author Emil Ivov
 * @author Eng Chong Meng
 */
public interface IMessage
{
    /*
     * ENC_TYPE type defined in DB; use by IMessage Local to define the required actions
     * Upper nibble (b7...b4) for body encryption Type i.e. OMEMO, OTR, NONE
     * Lower nibble (b3...b2) for special mode flag - may not be included in DB e.g FLAG_REMOTE_ONLY etc
     * Lower nibble (b1...b0) for body mimeType i.e. HTML or PLAIN
     */
    int ENCRYPTION_MASK = 0xF0;
    int FLAG_MODE_MASK = 0x0C;
    int ENCODE_MIME_MASK = 0x03;

    // Only the following three defined value are store in DB encType column
    int ENCRYPTION_OTR = 0x20;
    int ENCRYPTION_OMEMO = 0x10;
    int ENCRYPTION_NONE = 0x00;

    /*
     * The flag signifies that message is for remote sending - DO NOT store in DB or display in sender chat window
     * e.g. http file upload link for remote action
     */
    int FLAG_REMOTE_ONLY = 0x08;

    int ENCODE_HTML = 0x01;  // text/html
    int ENCODE_PLAIN = 0x00; // text/plain (UTF-8)

    /**
     * Returns the content of this message if representable in text form or null if this message
     * does not contain text data.
     *
     * @return a String containing the content of this message or null if the message does not
     * contain data representable in text form.
     */
    String getContent();

    /**
     * Returns the mime type for the message content.
     *
     * @return an integer for the mime type of the message content.
     */
    int getMimeType();

    /**
     * Returns the encryption type for the message content.
     *
     * @return an integer for the encryption type of the message content.
     */
    int getEncryptionType();

    /**
     * The Flag with EncryptionType | EncodeType | isRemoteFlag
     *
     * @return the message encType
     */
    int getEncType();

    /**
     * Returns the Http File Download status
     *
     * @return the file xfer status
     */
    int getXferStatus();

    // void setXferStatus(int status);

    /**
     * Returns the message delivery receipt status
     *
     * @return the receipt status
     */
    int getReceiptStatus();

    void setReceiptStatus(int status);

    /**
     * Returns the server message Id of the message sent - for tracking delivery receipt
     *
     * @return the server message Id of the message sent.
     */
    String getServerMsgId();

    void setServerMsgId(String msgId);

    /**
     * Returns the remote message Id of the message received - for tracking delivery receipt
     *
     * @return the remote message Id of the message received.
     */
    String getRemoteMsgId();

    void setRemoteMsgId(String msgId);

    /**
     * Returns true if the message is for remote consumption only; No local storage or Display is required.
     *
     * @return the remoteOnly flag status.
     */
    boolean isRemoteOnly();

    /**
     * Get the raw/binary content of an instant message.
     *
     * @return a byte[] array containing message bytes.
     */
    byte[] getRawData();

    /**
     * Returns the subject of this message or null if the message contains no subject.
     *
     * @return the subject of this message or null if the message contains no subject.
     */
    String getSubject();

    /**
     * Returns the size of the content stored in this message.
     *
     * @return an int indicating the number of bytes that this message contains.
     */
    int getSize();

    /**
     * Returns a unique identifier of this message.
     *
     * @return a String that uniquely represents this message in the scope of this protocol.
     */
    String getMessageUID();

    void setMessageUID(String msgUid);
}
