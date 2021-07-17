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
package org.xmpp.extensions.jibri;

import org.atalk.android.util.ApiLib;
import org.jivesoftware.smack.packet.*;
import org.jivesoftware.smack.util.XmlStringBuilder;

/**
 * Wraps Smack's <tt>XMPPError</tt> into <tt>PacketExtension</tt>, so that it
 * can be easily inserted into {@link RecordingStatus}.
 *
 * * @author Eng Chong Meng
 */
public class XMPPErrorPE implements ExtensionElement
{
    /**
     * <tt>XMPPError</tt> wrapped into this <tt>XMPPErrorPE</tt>.
     */
    private StanzaError error;

    /**
     * Creates new instance of <tt>XMPPErrorPE</tt>.
     *
     * @param stanzaError the instance of <tt>XMPPError</tt> that will be wrapped
     * by the newly created <tt>XMPPErrorPE</tt>.
     */
    public XMPPErrorPE(StanzaError stanzaError)
    {
        setError(stanzaError);
    }

    /**
     * Returns the underlying instance of <tt>XMPPError</tt>.
     */
    public StanzaError getError()
    {
        return error;
    }

    /**
     * Sets new instance of <tt>XMPPError</tt> to be wrapped by this <tt>XMPPErrorPE</tt>.
     *
     * @param error <tt>XMPPError</tt> that will be wrapped by this <TT>XMPPErrorPE</TT>.
     */

    public void setError(StanzaError error)
    {
        ApiLib.requireNonNull(error, "error");
        this.error = error;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String getElementName()
    {
        return "error";
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String getNamespace()
    {
        return "";
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public XmlStringBuilder toXML(XmlEnvironment xmlEnvironment)
    {
        return error.toXML(XmlEnvironment.EMPTY);
    }
}
