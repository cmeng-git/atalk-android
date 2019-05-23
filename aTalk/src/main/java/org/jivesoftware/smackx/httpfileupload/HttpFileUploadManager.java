/**
 *
 * Copyright © 2017 Grigory Fedorov
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
package org.jivesoftware.smackx.httpfileupload;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.AssertionError;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.WeakHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.crypto.Cipher;
import javax.crypto.CipherInputStream;
import javax.crypto.KeyGenerator;
import javax.crypto.NoSuchPaddingException;
import javax.crypto.SecretKey;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocketFactory;

import org.jivesoftware.smack.AbstractConnectionListener;
import org.jivesoftware.smack.ConnectionConfiguration;
import org.jivesoftware.smack.ConnectionCreationListener;
import org.jivesoftware.smack.Manager;
import org.jivesoftware.smack.SmackException;
import org.jivesoftware.smack.XMPPConnection;
import org.jivesoftware.smack.XMPPConnectionRegistry;
import org.jivesoftware.smack.XMPPException;
import org.jivesoftware.smack.util.Objects;
import org.jivesoftware.smack.util.StringUtils;
import org.jivesoftware.smackx.disco.ServiceDiscoveryManager;
import org.jivesoftware.smackx.disco.packet.DiscoverInfo;
import org.jivesoftware.smackx.httpfileupload.UploadService.Version;
import org.jivesoftware.smackx.httpfileupload.element.Slot;
import org.jivesoftware.smackx.httpfileupload.element.SlotRequest;
import org.jivesoftware.smackx.httpfileupload.element.SlotRequest_V0_2;
import org.jivesoftware.smackx.xdata.FormField;
import org.jivesoftware.smackx.xdata.packet.DataForm;

import org.jxmpp.jid.DomainBareJid;

/**
 * A manager for XEP-0363: HTTP File Upload.
 * This manager is also capable of XEP-XXXX: OMEMO Media Sharing.
 *
 * @author Grigory Fedorov
 * @author Florian Schmaus
 * @author Paul Schaub
 * @see <a href="http://xmpp.org/extensions/xep-0363.html">XEP-0363: HTTP File Upload</a>
 * @see <a href="https://xmpp.org/extensions/inbox/omemo-media-sharing.html">XEP-XXXX: OMEMO Media Sharing</a>
 */
public final class HttpFileUploadManager extends Manager {

    /**
     * Namespace of XEP-0363 v0.4 or higher. Constant value {@value #NAMESPACE}.
     *
     * @see <a href="https://xmpp.org/extensions/attic/xep-0363-0.4.0.html">XEP-0363 v0.4.0</a>
     */
    public static final String NAMESPACE = "urn:xmpp:http:upload:0";

    /**
     * Namespace of XEP-0363 v0.2 or lower. Constant value {@value #NAMESPACE_0_2}.
     *
     * @see <a href="https://xmpp.org/extensions/attic/xep-0363-0.2.5.html">XEP-0363 v0.2.5</a>
     */
    public static final String NAMESPACE_0_2 = "urn:xmpp:http:upload";

    private static final Logger LOGGER = Logger.getLogger(HttpFileUploadManager.class.getName());
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    static {
        XMPPConnectionRegistry.addConnectionCreationListener(new ConnectionCreationListener() {
            @Override
            public void connectionCreated(XMPPConnection connection) {
                getInstanceFor(connection);
            }
        });
    }

    private static final Map<XMPPConnection, HttpFileUploadManager> INSTANCES = new WeakHashMap<>();

    private UploadService defaultUploadService;

    private SSLSocketFactory tlsSocketFactory;

    /**
     * Obtain the HttpFileUploadManager responsible for a connection.
     *
     * @param connection the connection object.
     * @return a HttpFileUploadManager instance
     */
    public static synchronized HttpFileUploadManager getInstanceFor(XMPPConnection connection) {
        HttpFileUploadManager httpFileUploadManager = INSTANCES.get(connection);

        if (httpFileUploadManager == null) {
            httpFileUploadManager = new HttpFileUploadManager(connection);
            INSTANCES.put(connection, httpFileUploadManager);
        }

        return httpFileUploadManager;
    }

    private HttpFileUploadManager(XMPPConnection connection) {
        super(connection);

        connection.addConnectionListener(new AbstractConnectionListener() {
            @Override
            public void authenticated(XMPPConnection connection, boolean resumed) {
                // No need to reset the cache if the connection got resumed.
                if (resumed) {
                    return;
                }

                try {
                    discoverUploadService();
                } catch (XMPPException.XMPPErrorException | SmackException.NotConnectedException
                        | SmackException.NoResponseException | InterruptedException e) {
                    LOGGER.log(Level.WARNING, "Error during discovering HTTP File Upload service", e);
                }
            }
        });
    }

    private static UploadService uploadServiceFrom(DiscoverInfo discoverInfo) {
        assert (containsHttpFileUploadNamespace(discoverInfo));

        UploadService.Version version;
        if (discoverInfo.containsFeature(NAMESPACE)) {
            version = Version.v0_3;
        } else if (discoverInfo.containsFeature(NAMESPACE_0_2)) {
            version = Version.v0_2;
        } else {
            throw new AssertionError();
        }

        DomainBareJid address = discoverInfo.getFrom().asDomainBareJid();

        DataForm dataForm = DataForm.from(discoverInfo);
        if (dataForm == null) {
            return new UploadService(address, version);
        }

        FormField field = dataForm.getField("max-file-size");
        if (field == null) {
            return new UploadService(address, version);
        }

        String maxFileSizeValue = field.getFirstValue();
        if (maxFileSizeValue == null) {
            // This is likely an implementation error of the upload component, because the max-file-size form field is
            // there but has no value set.
            return new UploadService(address, version);

        }

        Long maxFileSize = Long.valueOf(maxFileSizeValue);
        return new UploadService(address, version, maxFileSize);
    }

    /**
     * Discover upload service.
     *
     * Called automatically when connection is authenticated.
     *
     * Note that this is a synchronous call -- Smack must wait for the server response.
     *
     * @return true if upload service was discovered

     * @throws XMPPException.XMPPErrorException
     * @throws SmackException.NotConnectedException
     * @throws InterruptedException
     * @throws SmackException.NoResponseException
     */
    public boolean discoverUploadService() throws XMPPException.XMPPErrorException, SmackException.NotConnectedException,
            InterruptedException, SmackException.NoResponseException {
        ServiceDiscoveryManager sdm = ServiceDiscoveryManager.getInstanceFor(connection());
        List<DiscoverInfo> servicesDiscoverInfo = sdm
                .findServicesDiscoverInfo(NAMESPACE, true, true);

        if (servicesDiscoverInfo.isEmpty()) {
            servicesDiscoverInfo = sdm.findServicesDiscoverInfo(NAMESPACE_0_2, true, true);
            if (servicesDiscoverInfo.isEmpty()) {
                return false;
            }
        }

        DiscoverInfo discoverInfo = servicesDiscoverInfo.get(0);

        defaultUploadService = uploadServiceFrom(discoverInfo);
        return true;
    }

    /**
     * Check if upload service was discovered.
     *
     * @return true if upload service was discovered
     */
    public boolean isUploadServiceDiscovered() {
        return defaultUploadService != null;
    }

    /**
     * Get default upload service if it was discovered.
     *
     * @return upload service JID or null if not available
     */
    public UploadService getDefaultUploadService() {
        return defaultUploadService;
    }

    /**
     * Request slot and uploaded file to HTTP file upload service.
     *
     * You don't need to request slot and upload file separately, this method will do both.
     * Note that this is a synchronous call -- Smack must wait for the server response.
     *
     * @param file file to be uploaded
     * @return public URL for sharing uploaded file
     * @throws InterruptedException
     * @throws XMPPException.XMPPErrorException
     * @throws SmackException
     * @throws IOException in case of HTTP upload errors
     */
    public URL uploadFile(File file) throws InterruptedException, XMPPException.XMPPErrorException,
            SmackException, IOException {
        return uploadFile(file, null);
    }

    /**
     * Request slot and uploaded file to HTTP file upload service with progress callback.
     *
     * You don't need to request slot and upload file separately, this method will do both.
     * Note that this is a synchronous call -- Smack must wait for the server response.
     *
     * @param file file to be uploaded
     * @param listener upload progress listener or null
     * @return public URL for sharing uploaded file
     *
     * @throws InterruptedException
     * @throws XMPPException.XMPPErrorException
     * @throws SmackException
     * @throws IOException
     */
    public URL uploadFile(File file, UploadProgressListener listener) throws InterruptedException,
            XMPPException.XMPPErrorException, SmackException, IOException {
        if (!file.isFile()) {
            throw new FileNotFoundException("The path " + file.getAbsolutePath() + " is not a file");
        }
        final Slot slot = requestSlot(file.getName(), file.length(), "application/octet-stream");

        uploadFile(file, slot, listener);

        return slot.getGetUrl();
    }

    /**
     * Upload a file encrypted using the scheme described in OMEMO Media Sharing.
     * The file is being encrypted using a random 256 bit AES key in Galois Counter Mode using a random 16 byte IV and
     * then uploaded to the server.
     * The URL that is returned has a modified scheme (aesgcm:// instead of https://) and has the IV and key attached
     * as ref part.
     *
     * Note: The URL contains the used key and IV in plain text. Keep in mind to only share this URL though a secured
     * channel (i.e. end-to-end encrypted message), as anybody who can read the URL can also decrypt the file.
     *
     * Note: This method uses a IV of length 16 instead of 12. Although not specified in the ProtoXEP, 16 byte IVs are
     * currently used by most implementations. This implementation also supports 12 byte IVs when decrypting.
     *
     * @param file file
     * @return AESGCM URL which contains the key and IV of the encrypted file.
     *
     * @see <a href="https://xmpp.org/extensions/inbox/omemo-media-sharing.html">XEP-XXXX: OMEMO Media Sharing</a>
     */
    public AesgcmUrl uploadFileEncrypted(File file) throws InterruptedException, IOException,
            XMPPException.XMPPErrorException, SmackException, InvalidAlgorithmParameterException,
            NoSuchAlgorithmException, InvalidKeyException, NoSuchPaddingException {
        return uploadFileEncrypted(file, null);
    }
    /**
     * Upload a file encrypted using the scheme described in OMEMO Media Sharing.
     * The file is being encrypted using a random 256 bit AES key in Galois Counter Mode using a random 16 byte IV and
     * then uploaded to the server.
     * The URL that is returned has a modified scheme (aesgcm:// instead of https://) and has the IV and key attached
     * as ref part.
     *
     * Note: The URL contains the used key and IV in plain text. Keep in mind to only share this URL though a secured
     * channel (i.e. end-to-end encrypted message), as anybody who can read the URL can also decrypt the file.
     *
     * Note: This method uses a IV of length 16 instead of 12. Although not specified in the ProtoXEP, 16 byte IVs are
     * currently used by most implementations. This implementation also supports 12 byte IVs when decrypting.
     *
     * @param file file
     * @param listener progress listener or null
     * @return AESGCM URL which contains the key and IV of the encrypted file.
     *
     * @see <a href="https://xmpp.org/extensions/inbox/omemo-media-sharing.html">XEP-XXXX: OMEMO Media Sharing</a>
     */
    public AesgcmUrl uploadFileEncrypted(File file, UploadProgressListener listener) throws IOException,
            InterruptedException, XMPPException.XMPPErrorException, SmackException, NoSuchPaddingException,
            NoSuchAlgorithmException, InvalidAlgorithmParameterException, InvalidKeyException {
        if (!file.isFile()) {
            throw new FileNotFoundException("The path " + file.getAbsolutePath() + " is not a file");
        }

        // The encrypted file will contain an extra block with the AEAD MAC.
        long cipherFileLength = file.length() + 16;

        final Slot slot = requestSlot(file.getName(), cipherFileLength, "application/octet-stream");
        URL slotUrl = slot.getGetUrl();

        // fresh AES key + iv
        byte[] key = OmemoMediaSharingUtils.generateRandomKey();
        byte[] iv = OmemoMediaSharingUtils.generateRandomIV();
        Cipher cipher = OmemoMediaSharingUtils.encryptionCipherFrom(key, iv);

        FileInputStream fis = new FileInputStream(file);
        // encrypt the file on the fly - encryption actually happens below in uploadFile()
        CipherInputStream cis = new CipherInputStream(fis, cipher);

        uploadFile(cis, cipherFileLength, slot, listener);

        return new AesgcmUrl(slotUrl, key, iv);
    }

    /**
     * Request a new upload slot from default upload service (if discovered). When you get slot you should upload file
     * to PUT URL and share GET URL. Note that this is a synchronous call -- Smack must wait for the server response.
     *
     * @param filename name of file to be uploaded
     * @param fileSize file size in bytes.
     * @return file upload Slot in case of success
     * @throws IllegalArgumentException if fileSize is less than or equal to zero or greater than the maximum size
     *         supported by the service.
     * @throws InterruptedException
     * @throws XMPPException.XMPPErrorException
     * @throws SmackException.NotConnectedException
     * @throws SmackException.NoResponseException
     */
    public Slot requestSlot(String filename, long fileSize) throws InterruptedException,
            XMPPException.XMPPErrorException, SmackException {
        return requestSlot(filename, fileSize, null, null);
    }

    /**
     * Request a new upload slot with optional content type from default upload service (if discovered).
     *
     * When you get slot you should upload file to PUT URL and share GET URL.
     * Note that this is a synchronous call -- Smack must wait for the server response.
     *
     * @param filename name of file to be uploaded
     * @param fileSize file size in bytes.
     * @param contentType file content-type or null
     * @return file upload Slot in case of success

     * @throws IllegalArgumentException if fileSize is less than or equal to zero or greater than the maximum size
     *         supported by the service.
     * @throws SmackException.NotConnectedException
     * @throws InterruptedException
     * @throws XMPPException.XMPPErrorException
     * @throws SmackException.NoResponseException
     */
    public Slot requestSlot(String filename, long fileSize, String contentType) throws SmackException,
            InterruptedException, XMPPException.XMPPErrorException {
        return requestSlot(filename, fileSize, contentType, null);
    }

    /**
     * Request a new upload slot with optional content type from custom upload service.
     *
     * When you get slot you should upload file to PUT URL and share GET URL.
     * Note that this is a synchronous call -- Smack must wait for the server response.
     *
     * @param filename name of file to be uploaded
     * @param fileSize file size in bytes.
     * @param contentType file content-type or null
     * @param uploadServiceAddress the address of the upload service to use or null for default one
     * @return file upload Slot in case of success
     * @throws IllegalArgumentException if fileSize is less than or equal to zero or greater than the maximum size
     *         supported by the service.
     * @throws SmackException
     * @throws InterruptedException
     * @throws XMPPException.XMPPErrorException
     */
    public Slot requestSlot(String filename, long fileSize, String contentType, DomainBareJid uploadServiceAddress)
            throws SmackException, InterruptedException, XMPPException.XMPPErrorException {
        final XMPPConnection connection = connection();
        final UploadService defaultUploadService = this.defaultUploadService;

        // The upload service we are going to use.
        UploadService uploadService;

        if (uploadServiceAddress == null) {
            uploadService = defaultUploadService;
        } else {
            if (defaultUploadService != null && defaultUploadService.getAddress().equals(uploadServiceAddress)) {
                // Avoid performing a service discovery if we already know about the given service.
                uploadService = defaultUploadService;
            } else {
                DiscoverInfo discoverInfo = ServiceDiscoveryManager.getInstanceFor(connection).discoverInfo(uploadServiceAddress);
                if (!containsHttpFileUploadNamespace(discoverInfo)) {
                    throw new IllegalArgumentException("There is no HTTP upload service running at the given address '"
                                    + uploadServiceAddress + '\'');
                }
                uploadService = uploadServiceFrom(discoverInfo);
            }
        }

        if (uploadService == null) {
            throw new SmackException.SmackMessageException("No upload service specified and also none discovered.");
        }

        if (!uploadService.acceptsFileOfSize(fileSize)) {
            throw new IllegalArgumentException(
                            "Requested file size " + fileSize + " is greater than max allowed size " + uploadService.getMaxFileSize());
        }

        SlotRequest slotRequest;
        switch (uploadService.getVersion()) {
        case v0_3:
            slotRequest = new SlotRequest(uploadService.getAddress(), filename, fileSize, contentType);
            break;
        case v0_2:
            slotRequest = new SlotRequest_V0_2(uploadService.getAddress(), filename, fileSize, contentType);
            break;
        default:
            throw new AssertionError();
        }

        return connection.createStanzaCollectorAndSend(slotRequest).nextResultOrThrow();
    }

    public void setTlsContext(SSLContext tlsContext) {
        if (tlsContext == null) {
            return;
        }
        this.tlsSocketFactory = tlsContext.getSocketFactory();
    }

    public void useTlsSettingsFrom(ConnectionConfiguration connectionConfiguration) {
        SSLContext sslContext = connectionConfiguration.getCustomSSLContext();
        setTlsContext(sslContext);
    }

    private void uploadFile(final File file, final Slot slot, UploadProgressListener listener) throws IOException {
        final long fileSize = file.length();
        // TODO Remove once Smack's minimum Android API level is 19 or higher. See also comment below.
        if (fileSize >= Integer.MAX_VALUE) {
            throw new IllegalArgumentException("File size " + fileSize + " must be less than " + Integer.MAX_VALUE);
        }

        // Construct the FileInputStream first to make sure we can actually read the file.
        final FileInputStream fis = new FileInputStream(file);
        uploadFile(fis, fileSize, slot, listener);
    }

    private void uploadFile(final InputStream fis, long fileSize, final Slot slot, UploadProgressListener listener) throws IOException {

        final URL putUrl = slot.getPutUrl();

        final HttpURLConnection urlConnection = (HttpURLConnection) putUrl.openConnection();

        urlConnection.setRequestMethod("PUT");
        urlConnection.setUseCaches(false);
        urlConnection.setDoOutput(true);
        // TODO Change to using fileSize once Smack's minimum Android API level is 19 or higher.
        urlConnection.setFixedLengthStreamingMode((int) fileSize);
        urlConnection.setRequestProperty("Content-Type", "application/octet-stream;");
        for (Entry<String, String> header : slot.getHeaders().entrySet()) {
            urlConnection.setRequestProperty(header.getKey(), header.getValue());
        }

        final SSLSocketFactory tlsSocketFactory = this.tlsSocketFactory;
        if (tlsSocketFactory != null && urlConnection instanceof HttpsURLConnection) {
            HttpsURLConnection httpsUrlConnection = (HttpsURLConnection) urlConnection;
            httpsUrlConnection.setSSLSocketFactory(tlsSocketFactory);
        }

        try {
            OutputStream outputStream = urlConnection.getOutputStream();

            long bytesSend = 0;

            if (listener != null) {
                listener.onUploadProgress(0, fileSize);
            }

            BufferedInputStream inputStream = new BufferedInputStream(fis);

            // TODO Factor in extra static method (and re-use e.g. in bytestream code).
            byte[] buffer = new byte[4096];
            int bytesRead;
            try {
                while ((bytesRead = inputStream.read(buffer)) != -1) {
                    outputStream.write(buffer, 0, bytesRead);
                    bytesSend += bytesRead;

                    if (listener != null) {
                        listener.onUploadProgress(bytesSend, fileSize);
                    }
                }
            }
            finally {
                try {
                    inputStream.close();
                }
                catch (IOException e) {
                    LOGGER.log(Level.WARNING, "Exception while closing input stream", e);
                }
                try {
                    outputStream.close();
                }
                catch (IOException e) {
                    LOGGER.log(Level.WARNING, "Exception while closing output stream", e);
                }
            }

            int status = urlConnection.getResponseCode();
            switch (status) {
            case HttpURLConnection.HTTP_OK:
            case HttpURLConnection.HTTP_CREATED:
            case HttpURLConnection.HTTP_NO_CONTENT:
                break;
            default:
                throw new IOException("Error response " + status + " from server during file upload: "
                                + urlConnection.getResponseMessage() + ", file size: " + fileSize + ", put URL: "
                                + putUrl);
            }
        }
        finally {
            urlConnection.disconnect();
        }
    }

    public static UploadService.Version namespaceToVersion(String namespace) {
        UploadService.Version version;
        switch (namespace) {
        case NAMESPACE:
            version = Version.v0_3;
            break;
        case NAMESPACE_0_2:
            version = Version.v0_2;
            break;
        default:
            version = null;
            break;
        }
        return version;
    }

    private static boolean containsHttpFileUploadNamespace(DiscoverInfo discoverInfo) {
        return discoverInfo.containsFeature(NAMESPACE) || discoverInfo.containsFeature(NAMESPACE_0_2);
    }

    /**
     * Generate a securely random byte array.
     *
     * @param len length of the byte array
     * @return byte array
     */
    private static byte[] secureRandomBytes(int len) {
        byte[] bytes = new byte[len];
        SECURE_RANDOM.nextBytes(bytes);
        return bytes;
    }

    /**
     * Utility code for XEP-XXXX: OMEMO Media Sharing.
     *
     * @see <a href="https://xmpp.org/extensions/inbox/omemo-media-sharing.html">XEP-XXXX: OMEMO Media Sharing</a>
     */
    static class OmemoMediaSharingUtils {

        private static final String KEYTYPE = "AES";
        private static final String CIPHERMODE = "AES/GCM/NoPadding";
        // 256 bit = 32 byte
        private static final int LEN_KEY = 32;
        private static final int LEN_KEY_BITS = LEN_KEY * 8;

        private static final int LEN_IV_12 = 12;
        private static final int LEN_IV_16 = 16;
        // Note: Contrary to what the ProtoXEP states, 16 byte IV length is used in the wild instead of 12.
        // At some point we should switch to 12 bytes though.
        private static final int LEN_IV = LEN_IV_16;

        static byte[] generateRandomIV() {
            return generateRandomIV(LEN_IV);
        }

        static byte[] generateRandomIV(int len) {
            return secureRandomBytes(len);
        }

        /**
         * Generate a random 256 bit AES key.
         *
         * @return encoded AES key
         * @throws NoSuchAlgorithmException if the JVM doesn't provide the given key type.
         */
        static byte[] generateRandomKey() throws NoSuchAlgorithmException {
            KeyGenerator generator = KeyGenerator.getInstance(KEYTYPE);
            generator.init(LEN_KEY_BITS);
            return generator.generateKey().getEncoded();
        }

        /**
         * Create a {@link Cipher} from a given key and iv which is in encryption mode.
         *
         * @param key aes encryption key
         * @param iv initialization vector
         *
         * @return cipher in encryption mode
         *
         * @throws NoSuchPaddingException if the JVM doesn't provide the padding specified in the ciphermode.
         * @throws NoSuchAlgorithmException if the JVM doesn't provide the encryption method specified in the ciphermode.
         * @throws InvalidAlgorithmParameterException if the cipher cannot be initiated.
         * @throws InvalidKeyException if the key is invalid.
         */
        private static Cipher encryptionCipherFrom(byte[] key, byte[] iv)
                throws NoSuchPaddingException, NoSuchAlgorithmException, InvalidAlgorithmParameterException,
                InvalidKeyException {
            SecretKey secretKey = new SecretKeySpec(key, KEYTYPE);
            IvParameterSpec ivSpec = new IvParameterSpec(iv);
            Cipher cipher = Cipher.getInstance(CIPHERMODE);
            cipher.init(Cipher.ENCRYPT_MODE, secretKey, ivSpec);
            return cipher;
        }

        /**
         * Create a {@link Cipher} from a given key and iv which is in decryption mode.
         *
         * @param key aes encryption key
         * @param iv initialization vector
         *
         * @return cipher in decryption mode
         *
         * @throws NoSuchPaddingException if the JVM doesn't provide the padding specified in the ciphermode.
         * @throws NoSuchAlgorithmException if the JVM doesn't provide the encryption method specified in the ciphermode.
         * @throws InvalidAlgorithmParameterException if the cipher cannot be initiated.
         * @throws InvalidKeyException if the key is invalid.
         */
        private static Cipher decryptionCipherFrom(byte[] key, byte[] iv)
                throws NoSuchPaddingException, NoSuchAlgorithmException, InvalidAlgorithmParameterException,
                InvalidKeyException {
            SecretKey secretKey = new SecretKeySpec(key, KEYTYPE);
            IvParameterSpec ivSpec = new IvParameterSpec(iv);
            Cipher cipher = Cipher.getInstance(CIPHERMODE);
            cipher.init(Cipher.ENCRYPT_MODE, secretKey, ivSpec);
            return cipher;
        }
    }

    /**
     * This class represents a aesgcm URL as described in XEP-XXXX: OMEMO Media Sharing.
     * As the builtin {@link URL} class cannot handle the aesgcm protocol identifier, this class
     * is used as a utility class that bundles together a {@link URL}, key and IV.
     *
     * @see <a href="https://xmpp.org/extensions/inbox/omemo-media-sharing.html">XEP-XXXX: OMEMO Media Sharing</a>
     */
    public static class AesgcmUrl {

        public static final String PROTOCOL = "aesgcm";

        private final URL httpsUrl;
        private final byte[] keyBytes;
        private final byte[] ivBytes;

        /**
         * Private constructor that constructs the {@link AesgcmUrl} from a normal https {@link URL}, a key and iv.
         *
         * @param httpsUrl normal https url as given by the {@link Slot}.
         * @param key byte array of an encoded 256 bit aes key
         * @param iv 16 or 12 byte initialization vector
         */
        AesgcmUrl(URL httpsUrl, byte[] key, byte[] iv) {
            this.httpsUrl = Objects.requireNonNull(httpsUrl);
            this.keyBytes = Objects.requireNonNull(key);
            this.ivBytes = Objects.requireNonNull(iv);
        }

        /**
         * Parse a {@link AesgcmUrl} from a {@link String}.
         * The parsed object will provide a normal {@link URL} under which the offered file can be downloaded,
         * as well as a {@link Cipher} that can be used to decrypt it.
         *
         * @param aesgcmUrlString aesgcm URL as a {@link String}
         */
        public AesgcmUrl(String aesgcmUrlString) {
            if (!aesgcmUrlString.startsWith(PROTOCOL)) {
                throw new IllegalArgumentException("Provided String does not resemble a aesgcm URL.");
            }

            // Convert aesgcm Url to https URL
            this.httpsUrl = extractHttpsUrl(aesgcmUrlString);

            // Extract IV and Key
            byte[][] ivAndKey = extractIVAndKey(aesgcmUrlString);
            this.ivBytes = ivAndKey[0];
            this.keyBytes = ivAndKey[1];
        }

        /**
         * Return a https {@link URL} under which the file can be downloaded.
         *
         * @return https URL
         */
        public URL getDownloadUrl() {
            return httpsUrl;
        }

        /**
         * Returns the {@link String} representation of this aesgcm URL.
         *
         * @return aesgcm URL with key and IV.
         */
        public String getAesgcmUrl() {
            String aesgcmUrl = httpsUrl.toString().replaceFirst(httpsUrl.getProtocol(), PROTOCOL);
            return aesgcmUrl + "#" + StringUtils.encodeHex(ivBytes) + StringUtils.encodeHex(keyBytes);
        }

        /**
         * Returns a {@link Cipher} in decryption mode, which can be used to decrypt the offered file.
         *
         * @return cipher
         *
         * @throws NoSuchPaddingException if the JVM cannot provide the specified cipher mode
         * @throws NoSuchAlgorithmException if the JVM cannot provide the specified cipher mode
         * @throws InvalidAlgorithmParameterException if the JVM cannot provide the specified cipher
         *                                            (eg. if no BC provider is added)
         * @throws InvalidKeyException if the provided key is invalid
         */
        public Cipher getDecryptionCipher() throws NoSuchPaddingException, NoSuchAlgorithmException,
                InvalidAlgorithmParameterException, InvalidKeyException {
            return OmemoMediaSharingUtils.decryptionCipherFrom(keyBytes, ivBytes);
        }

        private static URL extractHttpsUrl(String aesgcmUrlString) {
            // aesgcm -> https
            String httpsUrlString = aesgcmUrlString.replaceFirst(PROTOCOL, "https");
            // remove #ref
            httpsUrlString = httpsUrlString.substring(0, httpsUrlString.indexOf("#"));

            try {
                return new URL(httpsUrlString);
            } catch (MalformedURLException e) {
                throw new AssertionError("Failed to convert aesgcm URL to https URL: '" + aesgcmUrlString + "'", e);
            }
        }

        private static byte[][] extractIVAndKey(String aesgcmUrlString) {
            int startOfRef = aesgcmUrlString.lastIndexOf("#");
            if (startOfRef == -1) {
                throw new IllegalArgumentException("The provided aesgcm Url does not have a ref part which is " +
                        "supposed to contain the encryption key for file encryption.");
            }

            String ref = aesgcmUrlString.substring(startOfRef + 1);
            byte[] refBytes = StringUtils.hexStringToByteArray(ref);

            byte[] key = new byte[32];
            byte[] iv;
            int ivLen;
            // determine the length of the initialization vector part
            switch (refBytes.length) {
                // 32 bytes key + 16 bytes IV
                case 48:
                    ivLen = 16;
                    break;

                // 32 bytes key + 12 bytes IV
                case 44:
                    ivLen = 12;
                    break;
                default:
                    throw new IllegalArgumentException("Provided URL has an invalid ref tag (" + ref.length() + "): '" + ref + "'");
            }
            iv = new byte[ivLen];
            System.arraycopy(refBytes, 0, iv, 0, ivLen);
            System.arraycopy(refBytes, ivLen, key, 0, 32);

            return new byte[][] {iv,key};
        }
    }
}
