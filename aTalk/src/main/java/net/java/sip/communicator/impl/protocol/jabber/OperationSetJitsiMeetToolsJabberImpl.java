/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */

package net.java.sip.communicator.impl.protocol.jabber;

import net.java.sip.communicator.service.protocol.*;

import org.jivesoftware.smack.packet.ExtensionElement;
import org.json.JSONObject;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

import timber.log.Timber;

/**
 * Jabber protocol provider implementation of {@link OperationSetJitsiMeetTools}
 *
 * @author Pawel Domas
 * @author Cristian Florin Ghita
 * @author Eng Chong Meng
 */
public class OperationSetJitsiMeetToolsJabberImpl implements OperationSetJitsiMeetTools
{
    private final ProtocolProviderServiceJabberImpl parentProvider;

    /**
     * The list of {@link JitsiMeetRequestListener}.
     */
    private final List<JitsiMeetRequestListener> requestHandlers = new CopyOnWriteArrayList<>();

    /**
     * Creates new instance of <tt>OperationSetJitsiMeetToolsJabberImpl</tt>.
     *
     * @param parentProvider parent Jabber protocol provider service instance.
     */
    public OperationSetJitsiMeetToolsJabberImpl(ProtocolProviderServiceJabberImpl parentProvider)
    {
        this.parentProvider = parentProvider;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void addSupportedFeature(String featureName)
    {
        parentProvider.getDiscoveryManager().addFeature(featureName);
    }

    /**
     * {@inheritDoc}
     */
    public void removeSupportedFeature(String featureName)
    {
        parentProvider.getDiscoveryManager().removeFeature(featureName);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void sendPresenceExtension(ChatRoom chatRoom, ExtensionElement extension)
    {
        ((ChatRoomJabberImpl) chatRoom).sendPresenceExtension(extension);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void removePresenceExtension(ChatRoom chatRoom, ExtensionElement extension)
    {
        ((ChatRoomJabberImpl) chatRoom).removePresenceExtension(extension);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void setPresenceStatus(ChatRoom chatRoom, String statusMessage)
    {
        ((ChatRoomJabberImpl) chatRoom).publishPresenceStatus(statusMessage);
    }

    @Override
    public void addRequestListener(JitsiMeetRequestListener requestHandler)
    {
        this.requestHandlers.add(requestHandler);
    }

    @Override
    public void removeRequestListener(JitsiMeetRequestListener requestHandler)
    {
        this.requestHandlers.remove(requestHandler);
    }

    /**
     * Event is fired after startmuted extension is received.
     *
     * @param startMutedFlags startMutedFlags[0] represents
     * the muted status of audio stream.
     * startMuted[1] represents the muted status of video stream.
     */
    public void notifySessionStartMuted(boolean[] startMuted)
    {
        boolean handled = false;
        for (JitsiMeetRequestListener l : requestHandlers) {
            l.onSessionStartMuted(startMuted);
            handled = true;
        }
        if (!handled) {
            Timber.w("Unhandled join onStartMuted Jitsi Meet request!");
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void sendJSON(CallPeer callPeer, JSONObject jsonObject, Map<String, Object> params)
            throws OperationFailedException
    {
        throw new OperationFailedException("Operation not supported for this protocol yet!",
                OperationFailedException.NOT_SUPPORTED_OPERATION);
    }
}
