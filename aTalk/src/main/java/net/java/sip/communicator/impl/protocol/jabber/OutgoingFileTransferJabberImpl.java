/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package net.java.sip.communicator.impl.protocol.jabber;

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

    // Message Uuid also used as file transfer id.
    private final String msgUuid;
    private final Contact mContact;
    private final File mFile;

    /**
     * The jabber outgoing file transfer.
     */
    private final OutgoingFileTransfer mJabberFileTransfer;
    private final ProtocolProviderServiceJabberImpl mPPS;
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
     * @param id the id that uniquely identifies this file transfer and saved DB record
     */
    public OutgoingFileTransferJabberImpl(Contact recipient, File file, OutgoingFileTransfer jabberTransfer,
            ProtocolProviderServiceJabberImpl protocolProvider, String id)
    {
        mContact = recipient;
        mFile = file;
        mJabberFileTransfer = jabberTransfer;
        mPPS = protocolProvider;

        // Create the identifier of this file transfer that is used from the history and the user
        // interface to track this transfer. Use pass in value if available (cmeng 20220206: true always)
        // this.id = (TextUtils.isEmpty(msgUuid)) ? String.valueOf(System.currentTimeMillis()) + hashCode() : msgUuid;
        // Timber.e("OutgoingFileTransferJabberImpl msgUid: %s", id);
        msgUuid = id;

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
        mJabberFileTransfer.cancel();
    }

    /**
     * Get the transfer error
     * @return FileTransfer.Error
     */
    public FileTransfer.Error getTransferError()
    {
        return mJabberFileTransfer.getError();
    }

    /**
     * Returns the number of bytes already sent to the recipient.
     *
     * @return the number of bytes already sent to the recipient.
     */
    @Override
    public long getTransferredBytes()
    {
        return mJabberFileTransfer.getBytesSent();
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
        return mFile;
    }

    /**
     * The contact we are sending the file.
     *
     * @return the receiver.
     */
    public Contact getContact()
    {
        return mContact;
    }

    /**
     * The unique id that uniquely identity the record and in DB.
     *
     * @return the id.
     */
    public String getID()
    {
        return msgUuid;
    }

    /**
     * Removes previously added thumbnail request listener.
     */
    public void removeThumbnailHandler()
    {
        if (bobInfo == null) {
            return;
        }

        BoBManager bobManager = BoBManager.getInstanceFor(mPPS.getConnection());
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
        if (!(mFile instanceof ThumbnailedFile))
            return;

        XMPPConnection connection = mPPS.getConnection();
        StreamInitiation fileTransferPacket = (StreamInitiation) stanza;
        ThumbnailedFile thumbnailedFile = (ThumbnailedFile) mFile;

        if (mJabberFileTransfer.getStreamID().equals(fileTransferPacket.getSessionID())) {
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

