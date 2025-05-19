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
package org.jivesoftware.smackx.jibri;

import java.util.List;

import javax.xml.namespace.QName;

import org.jivesoftware.smack.packet.ExtensionElement;
import org.jivesoftware.smack.packet.StanzaError;
import org.jivesoftware.smackx.AbstractExtensionElement;
import org.jxmpp.jid.Jid;

/**
 * The packet extension added to Jicofo MUC presence to broadcast current
 * recording status to all conference participants.
 *
 * Status meaning:
 * <code>{@link JibriIq.Status#UNDEFINED}</code> - recording not available
 * <code>{@link JibriIq.Status#OFF}</code> - recording stopped(available to start)
 * <code>{@link JibriIq.Status#PENDING}</code> - starting recording
 * <code>{@link JibriIq.Status#ON}</code> - recording in progress
 */
public class RecordingStatus extends AbstractExtensionElement {
    /**
     * The namespace of this packet extension.
     */
    public static final String NAMESPACE = JibriIq.NAMESPACE;

    /**
     * XML element name of this packet extension.
     */
    public static final String ELEMENT = "jibri-recording-status";

    public static final QName QNAME = new QName(NAMESPACE, ELEMENT);

    /**
     * The name of XML attribute which holds the recording status.
     */
    private static final String STATUS_ATTRIBUTE = "status";

    /**
     * The name of the argument that contains the "initiator" jid.
     */
    public static final String INITIATOR_ATTR_NAME = "initiator";

    /**
     * The full JID of the entity that has initiated the recording flow.
     */
    private Jid initiator;

    public RecordingStatus() {
        super(ELEMENT, NAMESPACE);
    }

    /**
     * Returns the value of current recording status stored in it's attribute.
     *
     * @return one of {@link JibriIq.Status}
     */
    public JibriIq.Status getStatus() {
        String statusAttr = getAttributeAsString(STATUS_ATTRIBUTE);
        return JibriIq.Status.parse(statusAttr);
    }

    /**
     * Sets new value for the recording status.
     *
     * @param status one of {@link JibriIq.Status}
     */
    public void setStatus(JibriIq.Status status) {
        setAttribute(STATUS_ATTRIBUTE, status);
    }

    /**
     * Returns the session ID stored in this element
     *
     * @return the session ID
     */
    public String getSessionId() {
        return getAttributeAsString(JibriIq.SESSION_ID_ATTR_NAME);
    }

    /**
     * Set the session ID for this recording status element
     *
     * @param sessionId the session ID
     */
    public void setSessionId(String sessionId) {
        setAttribute(JibriIq.SESSION_ID_ATTR_NAME, sessionId);
    }

    public JibriIq.RecordingMode getRecordingMode() {
        String recordingMode = getAttributeAsString(JibriIq.RECORDING_MODE_ATTR_NAME);
        return JibriIq.RecordingMode.parse(recordingMode);
    }

    public void setRecordingMode(JibriIq.RecordingMode recordingMode) {
        setAttribute(JibriIq.RECORDING_MODE_ATTR_NAME, recordingMode.toString());
    }

    /**
     * Get the failure reason in this status, or UNDEFINED if there isn't one
     *
     * @return the failure reason
     */
    public JibriIq.FailureReason getFailureReason() {
        String failureReasonStr = getAttributeAsString(JibriIq.FAILURE_REASON_ATTR_NAME);
        return JibriIq.FailureReason.parse(failureReasonStr);
    }

    /**
     * Set the failure reason in this status
     *
     * @param failureReason the failure reason
     */
    public void setFailureReason(JibriIq.FailureReason failureReason) {
        if (failureReason != null) {
            setAttribute(JibriIq.FAILURE_REASON_ATTR_NAME, failureReason.toString());
        }
    }

    /**
     * Returns <code>XMPPError</code> associated with current
     * {@link RecordingStatus}.
     */
    public StanzaError getError() {
        XMPPErrorPE errorPe = getErrorPE();
        return errorPe != null ? errorPe.getError() : null;
    }

    /**
     * Gets <code>{@link XMPPErrorPE}</code> from the list of child packet
     * extensions.
     *
     * @return {@link XMPPErrorPE} or <code>null</code> if not found.
     */
    private XMPPErrorPE getErrorPE() {
        List<? extends ExtensionElement> errorPe = getChildExtensionsOfType(XMPPErrorPE.class);
        return (XMPPErrorPE) (!errorPe.isEmpty() ? errorPe.get(0) : null);
    }

    /**
     * Sets <code>XMPPError</code> on this <code>RecordingStatus</code>.
     *
     * @param error <code>XMPPError</code> to add error details to this
     * <code>RecordingStatus</code> instance or <code>null</code> to have it removed.
     */
    public void setError(StanzaError error) {
        if (error != null) {
            // Wrap and add XMPPError as packet extension
            XMPPErrorPE errorPe = getErrorPE();
            if (errorPe == null) {
                errorPe = new XMPPErrorPE(error);
                addChildExtension(errorPe);
            }
            errorPe.setError(error);
        }
        else {
            // Remove error PE
            getChildExtensions().remove(getErrorPE());
        }
    }

    /**
     * Sets the full JID of the entity that has initiated the recording flow.
     *
     * @param initiator the full JID of the initiator.
     */
    public void setInitiator(Jid initiator) {
        setAttribute(INITIATOR_ATTR_NAME, initiator);
        this.initiator = initiator;
    }

    /**
     * Returns the full JID of the entity that has initiated the recording flow.
     *
     * @return the full JID of the initiator.
     */
    public Jid getInitiator() {
        return initiator;
    }
}
