/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package net.java.sip.communicator.impl.protocol.jabber;

import android.text.TextUtils;

import net.java.sip.communicator.service.protocol.AbstractFileTransfer;
import net.java.sip.communicator.service.protocol.Contact;
import net.java.sip.communicator.util.ConfigurationUtils;

import org.jivesoftware.smack.StanzaListener;
import org.jivesoftware.smack.XMPPConnection;
import org.jivesoftware.smack.filter.AndFilter;
import org.jivesoftware.smack.filter.IQTypeFilter;
import org.jivesoftware.smack.filter.StanzaTypeFilter;
import org.jivesoftware.smack.packet.Stanza;
import org.jivesoftware.smackx.bob.BoBData;
import org.jivesoftware.smackx.bob.BoBInfo;
import org.jivesoftware.smackx.bob.BoBManager;
import org.jivesoftware.smackx.bob.ContentId;
import org.jivesoftware.smackx.bob.element.BoBIQ;
import org.jivesoftware.smackx.filetransfer.FileTransfer;
import org.jivesoftware.smackx.filetransfer.OutgoingFileTransfer;
import org.jivesoftware.smackx.si.packet.StreamInitiation;
import org.jivesoftware.smackx.thumbnail.Thumbnail;
import org.jivesoftware.smackx.thumbnail.ThumbnailFile;

import java.io.File;

import timber.log.Timber;

/**
 * The Jabber protocol extension of the <code>AbstractFileTransfer</code>.
 *
 * @author Yana Stamcheva
 * @author Eng Chong Meng
 */
public class OutgoingFileTransferJabberImpl extends AbstractFileTransfer implements StanzaListener
{
    // must include this attribute in bobData; else smack 4.4.0 throws NPE
    private static final int maxAge = 86400;

    private final String id;
    private final Contact recipient;
    private final File file;

    /**
     * The jabber outgoing file transfer.
     */
    private final OutgoingFileTransfer jabberTransfer;
    private final ProtocolProviderServiceJabberImpl protocolProvider;
    private BoBInfo bobInfo;

    /**
     * Creates an <code>OutgoingFileTransferJabberImpl</code> by specifying the <code>receiver</code>
     * contact, the <code>file</code> , the <code>jabberTransfer</code>, that would be used to send the file
     * through Jabber and the <code>protocolProvider</code>.
     *
     * @param recipient the destination contact
     * @param file the file to send
     * @param jabberTransfer the Jabber transfer object, containing all transfer information
     * @param protocolProvider the parent protocol provider
     * @param msgUuid the id that uniquely identifies this file transfer and saved DB record
     */
    public OutgoingFileTransferJabberImpl(Contact recipient, File file, OutgoingFileTransfer jabberTransfer,
            ProtocolProviderServiceJabberImpl protocolProvider, String msgUuid)
    {
        this.recipient = recipient;
        this.file = file;
        this.jabberTransfer = jabberTransfer;
        this.protocolProvider = protocolProvider;

        // Create the identifier of this file transfer that is used from the history and the user
        // interface to track this transfer. Use pass in value if available
        this.id = (TextUtils.isEmpty(msgUuid)) ? String.valueOf(System.currentTimeMillis()) + hashCode() : msgUuid;

        // jabberTransfer is null for http file upload
        if (jabberTransfer == null)
            return;

        // Add this outgoing transfer as a packet interceptor in order to manage thumbnails.
        if (ConfigurationUtils.isSendThumbnail() && (file instanceof ThumbnailedFile)
                && (((ThumbnailedFile) file).getThumbnailData() != null)
                && ((ThumbnailedFile) file).getThumbnailData().length > 0) {
            if (protocolProvider.isFeatureListSupported(protocolProvider.getFullJidIfPossible(recipient),
                    Thumbnail.NAMESPACE, BoBIQ.NAMESPACE)) {
                protocolProvider.getConnection().addStanzaInterceptor(this,
                        new AndFilter(IQTypeFilter.SET, new StanzaTypeFilter(StreamInitiation.class)));
            }
        }
    }

    /**
     * Cancels the file transfer.
     */
    @Override
    public void cancel()
    {
        this.jabberTransfer.cancel();
    }

    /**
     * Get the transfer error
     * @return FileTransfer.Error
     */
    public FileTransfer.Error getTransferError()
    {
        return jabberTransfer.getError();
    }

    /**
     * Returns the number of bytes already sent to the recipient.
     *
     * @return the number of bytes already sent to the recipient.
     */
    @Override
    public long getTransferredBytes()
    {
        return jabberTransfer.getBytesSent();
    }

    /**
     * The direction is outgoing.
     *
     * @return OUT.
     */
    public int getDirection()
    {
        return OUT;
    }

    /**
     * Returns the local file that is being transferred or to which we transfer.
     *
     * @return the file
     */
    public File getLocalFile()
    {
        return file;
    }

    /**
     * The contact we are sending the file.
     *
     * @return the receiver.
     */
    public Contact getContact()
    {
        return recipient;
    }

    /**
     * The unique id that uniquely identity the record and in DB.
     *
     * @return the id.
     */
    public String getID()
    {
        return id;
    }

    /**
     * Removes previously added thumbnail request listener.
     */
    public void removeThumbnailHandler()
    {
        if (bobInfo == null) {
            return;
        }

        BoBManager bobManager = BoBManager.getInstanceFor(protocolProvider.getConnection());
        for (ContentId hash : bobInfo.getHashes()) {
            bobManager.removeBoB(hash);
        }
    }

    /**
     * Listen for all <code>Si</code> stanzas and adds a thumbnail element to it if a thumbnail preview is enabled.
     *
     * @see StanzaListener#processStanza(Stanza)
     */
    @Override
    public void processStanza(Stanza stanza)
    {
        if (!ConfigurationUtils.isSendThumbnail() || !(stanza instanceof StreamInitiation))
            return;

        // If our file is not a thumbnail file we have nothing to do here.
        if (!(file instanceof ThumbnailedFile))
            return;

        XMPPConnection connection = protocolProvider.getConnection();
        StreamInitiation fileTransferPacket = (StreamInitiation) stanza;
        ThumbnailedFile thumbnailedFile = (ThumbnailedFile) file;

        if (jabberTransfer.getStreamID().equals(fileTransferPacket.getSessionID())) {
            StreamInitiation.File file = fileTransferPacket.getFile();

            BoBData bobData = new BoBData(
                    thumbnailedFile.getThumbnailMimeType(),
                    thumbnailedFile.getThumbnailData(),
                    maxAge);

            BoBManager bobManager = BoBManager.getInstanceFor(connection);
            bobInfo = bobManager.addBoB(bobData);
            Thumbnail thumbnail = new Thumbnail(
                    thumbnailedFile.getThumbnailData(),
                    thumbnailedFile.getThumbnailMimeType(),
                    thumbnailedFile.getThumbnailWidth(),
                    thumbnailedFile.getThumbnailHeight());

            ThumbnailFile fileElement = new ThumbnailFile(file, thumbnail);
            fileTransferPacket.setFile(fileElement);

            Timber.d("File transfer packet intercepted to add thumbnail element.");
            // Timber.d("The file transfer packet with thumbnail: %s", fileTransferPacket.toXML(XmlEnvironment.EMPTY));
        }

        // Remove this packet interceptor after we're done.
        if (connection != null)
            connection.removeStanzaInterceptor(this);
    }
}

