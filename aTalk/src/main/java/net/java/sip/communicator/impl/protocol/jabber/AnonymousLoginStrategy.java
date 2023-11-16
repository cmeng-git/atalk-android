/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */
package net.java.sip.communicator.impl.protocol.jabber;

import net.java.sip.communicator.service.certificate.CertificateService;
import net.java.sip.communicator.service.protocol.AccountID;
import net.java.sip.communicator.service.protocol.SecurityAuthority;
import net.java.sip.communicator.service.protocol.UserCredentials;

import org.jivesoftware.smack.AbstractXMPPConnection;
import org.jivesoftware.smack.ConnectionConfiguration;
import org.jivesoftware.smack.SmackException;
import org.jivesoftware.smack.XMPPException;
import org.jxmpp.jid.parts.Resourcepart;

import java.io.IOException;
import java.security.GeneralSecurityException;

import javax.net.ssl.SSLContext;
import javax.net.ssl.X509TrustManager;

/**
 * Implements anonymous login strategy for the purpose of some server side technologies. This makes
 * not much sense to be used with Jitsi directly.
 *
 * @author Pawel Domas
 * @author Eng Chong Meng
 * @see JabberAccountIDImpl#ANONYMOUS_AUTH
 */
public class AnonymousLoginStrategy implements JabberLoginStrategy
{
    /**
     * <code>UserCredentials</code> used by accompanying services.
     */
    private final UserCredentials credentials;
    private ConnectionConfiguration.Builder<?, ?>  ccBuilder;

    /**
     * Creates new anonymous login strategy instance.
     *
     * @param login user login only for the purpose of returning <code>UserCredentials</code> that are used by
     * accompanying services.
     */
    public AnonymousLoginStrategy(String login, ConnectionConfiguration.Builder<?, ?>  ccBuilder)
    {
        this.credentials = new UserCredentials();
        this.ccBuilder = ccBuilder;

        credentials.setUserName(login);

        // FIXME: consider including password for TURN authentication ?
        credentials.setPassword(new char[]{});
    }

    @Override
    public UserCredentials prepareLogin(SecurityAuthority authority, int reasonCode, String reason,
            Boolean isShowAlways)
    {
        return credentials;
    }

    @Override
    public boolean loginPreparationSuccessful()
    {
        ccBuilder.performSaslAnonymousAuthentication();
        return true;
    }

    @Override
    public boolean login(AbstractXMPPConnection connection, String userName, Resourcepart resource)
            throws XMPPException, InterruptedException, IOException, SmackException
    {
        connection.login();
        return true;
    }

    @Override
    public boolean registerAccount(ProtocolProviderServiceJabberImpl pps, AccountID accountId)
            throws XMPPException, SmackException
    {
        return true;
    }

    @Override
    public boolean isTlsRequired()
    {
        return false;
    }

    @Override
    public SSLContext createSslContext(CertificateService certificateService,
            X509TrustManager trustManager)
            throws GeneralSecurityException
    {
        return certificateService.getSSLContext(trustManager);
    }

    @Override
    public ConnectionConfiguration.Builder<?, ?>  getConnectionConfigurationBuilder()
    {
        return ccBuilder;
    }
}
