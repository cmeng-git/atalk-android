/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 * 
pHideExtendedAwayStatus * Licensed under the Apache License, Version 2.0 (the "License"); you may
 not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 * 
 * http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */
package net.java.sip.communicator.impl.protocol.jabber;

import net.java.sip.communicator.service.protocol.OperationSetTLS;

import java.security.cert.Certificate;

import javax.net.ssl.SSLPeerUnverifiedException;
import javax.net.ssl.SSLSocket;

/**
 * An implementation of the OperationSetTLS for the Jabber protocol.
 *
 * @author Markus Kilas
 * @author Eng Chong Meng
 */
public class OperationSetTLSJabberImpl implements OperationSetTLS
{
    private final ProtocolProviderServiceJabberImpl mPPS;

    public OperationSetTLSJabberImpl(ProtocolProviderServiceJabberImpl pps)
    {
        this.mPPS = pps;
    }

    /**
     * @see OperationSetTLS#getCipherSuite()
     */
    @Override
    public String getCipherSuite()
    {
        final SSLSocket socket = mPPS.getSSLSocket();
        return (socket == null) ? null : socket.getSession().getCipherSuite();
    }

    /**
     * @see OperationSetTLS#getProtocol()
     */
    @Override
    public String getProtocol()
    {
        final SSLSocket socket = mPPS.getSSLSocket();
        return (socket == null) ? null : socket.getSession().getProtocol();
    }

    /**
     * @see OperationSetTLS#getServerCertificates()
     */
    @Override
    public Certificate[] getServerCertificates()
    {
        Certificate[] certChain = null;
        final SSLSocket socket = mPPS.getSSLSocket();
        if (socket != null) {
            try {
                certChain = socket.getSession().getPeerCertificates();
            } catch (SSLPeerUnverifiedException ex) {
                ex.printStackTrace();
            }
        }
        return certChain;
    }
}
