/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.xmpp.extensions.jibri;

import org.apache.commons.lang3.StringUtils;
import org.jivesoftware.smack.packet.XmlEnvironment;
import org.jivesoftware.smack.provider.IQProvider;
import org.jivesoftware.smack.xml.XmlPullParser;
import org.jivesoftware.smack.xml.XmlPullParserException;
import org.jxmpp.jid.EntityBareJid;
import org.jxmpp.jid.impl.JidCreate;

import java.io.IOException;

/**
 * Parses {@link JibriIq}.
 */
public class JibriIqProvider extends IQProvider<JibriIq>
{
    /**
     * {@inheritDoc}
     */
    @Override
    public JibriIq parse(XmlPullParser parser, int initialDepth, XmlEnvironment xmlEnvironment)
            throws XmlPullParserException, IOException
    {
        String namespace = parser.getNamespace();

        // Check the namespace
        if (!JibriIq.NAMESPACE.equals(namespace)) {
            return null;
        }

        String rootElement = parser.getName();
        JibriIq iq = null;

        if (JibriIq.ELEMENT.equals(rootElement)) {
            iq = new JibriIq();

            String action = parser.getAttributeValue("", JibriIq.ACTION_ATTR_NAME);
            iq.setAction(JibriIq.Action.parse(action));

            String status = parser.getAttributeValue("", JibriIq.STATUS_ATTR_NAME);
            iq.setStatus(JibriIq.Status.parse(status));

            String recordingMode = parser.getAttributeValue("", JibriIq.RECORDING_MODE_ATTR_NAME);
            if (StringUtils.isNotEmpty(recordingMode))
                iq.setRecordingMode(JibriIq.RecordingMode.parse(recordingMode));

            String room = parser.getAttributeValue("", JibriIq.ROOM_ATTR_NAME);
            if (StringUtils.isNotEmpty(room)) {
                EntityBareJid roomJid = JidCreate.entityBareFrom(room);
                iq.setRoom(roomJid);
            }

            String streamId = parser.getAttributeValue("", JibriIq.STREAM_ID_ATTR_NAME);
            if (StringUtils.isNotEmpty(streamId))
                iq.setStreamId(streamId);

            String youTubeBroadcastId = parser.getAttributeValue("", JibriIq.YOUTUBE_BROADCAST_ID_ATTR_NAME);
            if (StringUtils.isNotEmpty(youTubeBroadcastId))
                iq.setYouTubeBroadcastId(youTubeBroadcastId);

            String sessionId = parser.getAttributeValue("", JibriIq.SESSION_ID_ATTR_NAME);
            if (StringUtils.isNotEmpty(sessionId)) {
                iq.setSessionId(sessionId);
            }

            String appData = parser.getAttributeValue("", JibriIq.APP_DATA_ATTR_NAME);
            if (StringUtils.isNotEmpty(appData)) {
                iq.setAppData(appData);
            }

            String failureStr = parser.getAttributeValue("", JibriIq.FAILURE_REASON_ATTR_NAME);
            if (StringUtils.isNotEmpty(failureStr)) {
                iq.setFailureReason(JibriIq.FailureReason.parse(failureStr));
            }
            String shouldRetryStr = parser.getAttributeValue("", JibriIq.SHOULD_RETRY_ATTR_NAME);
            if (StringUtils.isNotEmpty(shouldRetryStr)) {
                iq.setShouldRetry(Boolean.valueOf(shouldRetryStr));
            }
            else if (iq.getFailureReason() != null
                    && iq.getFailureReason() != JibriIq.FailureReason.UNDEFINED) {
                throw new RuntimeException("shouldRetry must be set if a failure reason is given");
            }

            String displayName = parser.getAttributeValue("", JibriIq.DISPLAY_NAME_ATTR_NAME);
            if (StringUtils.isNotEmpty(displayName))
                iq.setDisplayName(displayName);

            String sipAddress = parser.getAttributeValue("", JibriIq.SIP_ADDRESS_ATTR_NAME);
            if (StringUtils.isNotEmpty(sipAddress))
                iq.setSipAddress(sipAddress);
        }
        return iq;
    }
}
